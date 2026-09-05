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
    private final Handler handler;
    private boolean detecting;

    private AdAudioDetector() {
        this.handler = new Handler(Looper.getMainLooper());
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
        Log.d(TAG, "Initialized for source: " + sourceId + ", cached ads: " + (adCache != null ? adCache.size() : 0));
    }

    private String lastUrl;
    private Map<String, String> lastHeaders;
    private List<String> recordingUrlPatterns;
    private int recordingTsCount;
    private long lastSkipTime;

    public void onLoadStarted(String url, Map<String, String> headers) {
        this.lastUrl = url;
        this.lastHeaders = headers;
        if (recordingAd != null) {
            recordingTsCount++;
            recordingUrlPatterns.add(ADFilter.extractUrlFeature(url));
        }
        
        if (url.contains("ad_type=static")) {
            if (System.currentTimeMillis() - lastSkipTime < 2000) return;
            long duration = 0;
            try {
                String durStr = url.split("ad_dur=")[1].split("&")[0];
                duration = Long.parseLong(durStr);
            } catch (Exception ignored) {}
            skipAd(duration);
        } else if (player != null && adCache != null) {
            // Fallback time-based check if M3U8 wasn't pre-tagged
            long currentPos = player.getCurrentPosition();
            for (Ad ad : adCache) {
                for (Long offset : ad.getTimeOffsetList()) {
                    if (Math.abs(offset - currentPos) < 5000) {
                        skipAd(ad);
                        return;
                    }
                }
            }
        }
    }

    public void onAdMarkClick(long currentPosition) {
        if (lastUrl == null) {
            Notify.show("找不到片段資訊，無法標記");
            return;
        }
        toggleRecord(lastUrl, lastHeaders, currentPosition);
    }

    public void detect(String url, Map<String, String> headers) {
        if (detecting) return;
        detecting = true;
        Log.d(TAG, "Starting detection for URL: " + url);
        
        AudioExtractor.extract(url, headers, new AudioExtractor.Callback() {
            @Override
            public void onSuccess(byte[] pcmData) {
                String fingerprint = AudioFingerprinter.generateFingerprint(pcmData);
                checkAd(fingerprint, url);
                detecting = false;
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Extraction error: " + e.getMessage());
                detecting = false;
            }
        });
    }

    private void checkAd(String fingerprint, String url) {
        if (adCache == null) return;
        for (Ad ad : adCache) {
            float similarity = AudioFingerprinter.compare(fingerprint, ad.getFingerprint());
            if (similarity > 0.9f) {
                Log.d(TAG, "Ad detected by fingerprint! Similarity: " + similarity);
                // Update pattern and hit count
                ad.addTimeOffset(player.getCurrentPosition());
                skipAd(ad);
                return;
            }
        }
    }

    private void skipAd(long durationMs) {
        handler.post(() -> {
            if (player == null) return;
            long currentPos = player.getCurrentPosition();
            long skip = durationMs > 0 ? durationMs : 15000;
            player.seekTo(currentPos + skip);
            Notify.show("正在跳過 " + Util.timeMs(skip) + " 廣告");
            lastSkipTime = System.currentTimeMillis();
        });
    }

    private void skipAd(Ad ad) {
        if (System.currentTimeMillis() - lastSkipTime < 2000) return;
        handler.post(() -> {
            if (player == null) return;
            long currentPos = player.getCurrentPosition();
            long skipDuration = ad.getDuration() > 0 ? ad.getDuration() : 15000; // Default 15s if unknown
            player.seekTo(currentPos + skipDuration);
            Notify.show("正在跳過 " + Util.timeMs(skipDuration) + " 廣告");
            lastSkipTime = System.currentTimeMillis();
            
            // Update hit count
            ad.setHitCount(ad.getHitCount() + 1);
            ad.setLastHitTime(System.currentTimeMillis());
            App.execute(ad::save);
        });
    }

    private Ad recordingAd;

    public boolean isRecording() {
        return recordingAd != null;
    }

    public void toggleRecord(String url, Map<String, String> headers, long startTimeOffset) {
        if (recordingAd == null) {
            startRecording(url, headers, startTimeOffset);
        } else {
            stopRecording(startTimeOffset);
        }
    }

    private void startRecording(String url, Map<String, String> headers, long startTimeOffset) {
        Log.d(TAG, "Start recording ad for URL: " + url);
        recordingTsCount = 1;
        recordingUrlPatterns = new ArrayList<>();
        recordingUrlPatterns.add(ADFilter.extractUrlFeature(url));
        AudioExtractor.extract(url, headers, new AudioExtractor.Callback() {
            @Override
            public void onSuccess(byte[] pcmData) {
                String fingerprint = AudioFingerprinter.generateFingerprint(pcmData);
                
                // Check if this ad already exists in cache
                Ad existing = null;
                if (adCache != null) {
                    for (Ad a : adCache) {
                        if (AudioFingerprinter.compare(fingerprint, a.getFingerprint()) > 0.95f) {
                            existing = a;
                            break;
                        }
                    }
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
                    handler.post(() -> Notify.show("已標記廣告開始，播放結束後請點擊「標記結尾」"));
                }
            }

            @Override
            public void onError(Exception e) {
                handler.post(() -> Notify.show("標記廣告失敗: " + e.getMessage()));
            }
        });
    }

    private void stopRecording(long endTimeOffset) {
        if (recordingAd == null) return;
        long startTime = recordingAd.getTimeOffsetList().isEmpty() ? 0 : recordingAd.getTimeOffsetList().get(recordingAd.getTimeOffsetList().size() - 1);
        long duration = endTimeOffset - startTime;
        if (duration <= 0 || duration > 60000) {
            Notify.show("廣告時長不合法 (" + (duration / 1000) + "秒)，已取消標記");
            recordingAd = null;
            return;
        }

        recordingAd.setDuration(duration);
        recordingAd.setTsCount(recordingTsCount);
        recordingAd.setLastHitTime(System.currentTimeMillis());
        
        StringBuilder patterns = new StringBuilder();
        for (int i = 0; i < recordingUrlPatterns.size(); i++) patterns.append(recordingUrlPatterns.get(i)).append(i == recordingUrlPatterns.size() - 1 ? "" : ",");
        recordingAd.setUrlPatterns(patterns.toString());
        
        final Ad finalAd = recordingAd;
        App.execute(() -> {
            finalAd.save();
            adCache = Ad.get(sourceId);
            int count = finalAd.getTimeOffsetList().size();
            handler.post(() -> Notify.show("已紀錄廣告特徵 (長度 " + (duration / 1000) + "秒, TS數 " + finalAd.getTsCount() + ")，該影片已出現 " + count + " 次"));
        });
        recordingAd = null;
    }
}
