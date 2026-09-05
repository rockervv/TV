package com.fongmi.android.tv.player.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Ad;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdAudioDetector {

    private static final String TAG = "AdAudioDetector";
    private static volatile AdAudioDetector instance;

    private ExoPlayer player;
    private String sourceId;
    private String seriesName;
    private List<Ad> adCache;
    private final List<AdRange> dynamicRanges;
    private final Handler handler;
    private boolean detecting;

    private AdAudioDetector() {
        this.handler = new Handler(Looper.getMainLooper());
        this.dynamicRanges = new ArrayList<>();
    }

    public static AdAudioDetector get() {
        if (instance == null) {
            synchronized (AdAudioDetector.class) {
                if (instance == null) instance = new AdAudioDetector();
            }
        }
        return instance;
    }

    public void init(ExoPlayer player, String sourceId, String seriesName) {
        this.player = player;
        this.sourceId = sourceId;
        this.seriesName = seriesName;
        this.adCache = Ad.get(sourceId);
        this.dynamicRanges.clear();
        Log.d(TAG, "Initialized for source: " + sourceId + ", cached ads: " + (adCache != null ? adCache.size() : 0));
    }

    private String lastUrl;
    private Map<String, String> lastHeaders;
    private List<String> recordingUrlPatterns;
    private int recordingTsCount;
    private long lastSkipTime;

    public void onLoadStarted(String url, Map<String, String> headers, long segmentStartTimeMs) {
        this.lastUrl = url;
        this.lastHeaders = headers;
        if (recordingAd != null) {
            recordingTsCount++;
            recordingUrlPatterns.add(ADFilter.extractUrlFeature(url));
        }
        
        if (url.contains("ad_type=static")) {
            try {
                long start = -1;
                long target = -1;
                if (url.contains("ad_start=")) start = Long.parseLong(url.split("ad_start=")[1].split("&")[0]);
                if (url.contains("ad_target=")) target = Long.parseLong(url.split("ad_target=")[1].split("&")[0]);
                
                if (start != -1 && target != -1) {
                    registerAdRange(start, target);
                    checkImmediateJump(start, target);
                }
            } catch (Exception ignored) {}
        }
    }

    private void registerAdRange(long start, long target) {
        for (AdRange range : dynamicRanges) {
            if (range.start == start && range.target == target) return;
        }
        Log.d(TAG, "Registered Dynamic Ad Range: " + Util.timeMs(start) + " -> " + Util.timeMs(target));
        dynamicRanges.add(new AdRange(start, target));
        
        // Use ExoPlayer's Message system for sub-millisecond precision
        if (player != null) {
            player.createMessage((messageType, payload) -> {
                Log.d(TAG, "Precise Message Jump triggered at " + Util.timeMs(start));
                jumpTo(target);
            })
            .setPosition(start)
            .setDeleteAfterDelivery(true)
            .send();
        }
    }

    private void checkImmediateJump(long start, long target) {
        if (player == null) return;
        long currentPos = player.getCurrentPosition();
        long bufferedPos = player.getBufferedPosition();
        
        // If we are currently loading an ad segment and our current playback position 
        // is within 5 seconds of it, we should jump NOW to target to avoid downloading and decoding the ad.
        if (bufferedPos >= start - 1000 && currentPos < target) {
            Log.d(TAG, "checkImmediateJump: Buffer is at ad start. Jumping to target to block preload.");
            jumpTo(target);
        }
    }

    /**
     * Called whenever playback position changes.
     */
    public void onPositionChanged(long currentPos) {
        if (player == null || System.currentTimeMillis() - lastSkipTime < 2000) return;

        // 1. Check Dynamic Ranges (from M3U8 tags)
        for (AdRange range : dynamicRanges) {
            if (currentPos >= range.start - 300 && currentPos < range.target) {
                jumpTo(range.target);
                return;
            }
        }

        // 2. Check Static Cache (from Fingerprinting database)
        if (adCache != null) {
            for (Ad ad : adCache) {
                for (Long offset : ad.getTimeOffsetList()) {
                    if (Math.abs(offset - currentPos) < 1000) {
                        jumpTo(currentPos + (ad.getDuration() > 0 ? ad.getDuration() : 15000));
                        ad.setHitCount(ad.getHitCount() + 1);
                        ad.setLastHitTime(System.currentTimeMillis());
                        App.execute(ad::save);
                        return;
                    }
                }
            }
        }
    }

    private void jumpTo(long targetPosMs) {
        handler.post(() -> {
            if (player == null) return;
            long currentPos = player.getCurrentPosition();
            if (targetPosMs <= currentPos + 200) return;
            
            Log.d(TAG, "Executing jumpTo: currentPos=" + currentPos + ", target=" + targetPosMs);
            player.seekTo(targetPosMs);
            Notify.show("正在跳過廣告，跳轉至 " + Util.timeMs(targetPosMs));
            lastSkipTime = System.currentTimeMillis();
        });
    }

    // --- Fingerprint Detection ---

    public void detect(String url, Map<String, String> headers) {
        if (detecting) return;
        detecting = true;
        AudioExtractor.extract(url, headers, new AudioExtractor.Callback() {
            @Override
            public void onSuccess(byte[] pcmData) {
                String fingerprint = AudioFingerprinter.generateFingerprint(pcmData);
                checkAd(fingerprint);
                detecting = false;
            }
            @Override
            public void onError(Exception e) {
                detecting = false;
            }
        });
    }

    private void checkAd(String fingerprint) {
        if (adCache == null) return;
        for (Ad ad : adCache) {
            float similarity = AudioFingerprinter.compare(fingerprint, ad.getFingerprint());
            if (similarity > 0.9f) {
                long pos = player.getCurrentPosition();
                ad.addTimeOffset(pos);
                jumpTo(pos + (ad.getDuration() > 0 ? ad.getDuration() : 15000));
                return;
            }
        }
    }

    // --- Recording Logic ---

    private Ad recordingAd;

    public boolean isRecording() {
        return recordingAd != null;
    }

    public void onAdMarkClick(long currentPosition) {
        if (lastUrl == null) {
            Notify.show("找不到片段資訊，無法標記");
            return;
        }
        if (recordingAd == null) {
            startRecording(lastUrl, lastHeaders, currentPosition);
        } else {
            stopRecording(currentPosition);
        }
    }

    private void startRecording(String url, Map<String, String> headers, long startTimeOffset) {
        recordingTsCount = 1;
        recordingUrlPatterns = new ArrayList<>();
        recordingUrlPatterns.add(ADFilter.extractUrlFeature(url));
        AudioExtractor.extract(url, headers, new AudioExtractor.Callback() {
            @Override
            public void onSuccess(byte[] pcmData) {
                String fingerprint = AudioFingerprinter.generateFingerprint(pcmData);
                Ad existing = null;
                if (adCache != null) {
                    for (Ad a : adCache) if (AudioFingerprinter.compare(fingerprint, a.getFingerprint()) > 0.95f) { existing = a; break; }
                }
                if (existing != null) {
                    recordingAd = existing;
                    recordingAd.addTimeOffset(startTimeOffset);
                    handler.post(() -> Notify.show("偵測到重複廣告，已更新出現時間點"));
                } else {
                    recordingAd = new Ad();
                    recordingAd.setFingerprint(fingerprint);
                    recordingAd.setSourceId(sourceId);
                    recordingAd.setSeriesName(seriesName);
                    recordingAd.addTimeOffset(startTimeOffset);
                    recordingAd.setAdName("廣告 " + (adCache != null ? adCache.size() + 1 : 1));
                    handler.post(() -> Notify.show("已標記廣告開始"));
                }
            }
            @Override
            public void onError(Exception e) {
                handler.post(() -> Notify.show("標記失敗: " + e.getMessage()));
            }
        });
    }

    private void stopRecording(long endTimeOffset) {
        if (recordingAd == null) return;
        long startTime = recordingAd.getTimeOffsetList().isEmpty() ? 0 : recordingAd.getTimeOffsetList().get(recordingAd.getTimeOffsetList().size() - 1);
        long duration = endTimeOffset - startTime;
        if (duration <= 0 || duration > 60000) {
            Notify.show("廣告時長不合法，已取消");
            recordingAd = null;
            return;
        }
        recordingAd.setDuration(duration);
        recordingAd.setTsCount(recordingTsCount);
        StringBuilder patterns = new StringBuilder();
        for (int i = 0; i < recordingUrlPatterns.size(); i++) patterns.append(recordingUrlPatterns.get(i)).append(i == recordingUrlPatterns.size() - 1 ? "" : ",");
        recordingAd.setUrlPatterns(patterns.toString());
        final Ad finalAd = recordingAd;
        App.execute(() -> {
            finalAd.save();
            adCache = Ad.get(sourceId);
            handler.post(() -> Notify.show("廣告特徵已紀錄 (" + (duration / 1000) + "秒)"));
        });
        recordingAd = null;
    }

    private static class AdRange {
        long start;
        long target;
        AdRange(long start, long target) { this.start = start; this.target = target; }
    }
}
