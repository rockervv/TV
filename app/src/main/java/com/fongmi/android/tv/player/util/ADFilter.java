package com.fongmi.android.tv.player.util;

import androidx.media3.common.util.Log;

import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.fongmi.android.tv.utils.Notify;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ADFilter {
    private static M3U8ParseListener parseListener;
    private static final LruCache<String, M3U8AdFilterResult> cache = new LruCache<>(10);
    private static final Pattern PATTERN_SEGMENT = Pattern.compile("(\\d+)\\.(ts|jpe?g|m4s|mp4)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_URL_CLEAN = Pattern.compile("[\\s\\u200B\\u00A0]+");

    public static String Process(String url, BufferedReader reader) {
        M3U8AdFilterResult result = parseAndFilterM3U8(url, reader);
        notifyAdSegmentsFiltered(result.adSegmentCount, result.adDurationSeconds);
        return result.filteredContent;
    }

    public static String Process(String url, BufferedReader reader, Handler handler) {
        M3U8AdFilterResult result = parseAndFilterM3U8(url, reader);
        handler.post(() -> notifyAdSegmentsFiltered(result.adSegmentCount, result.adDurationSeconds));
        return result.filteredContent;
    }

    public static String Process(BufferedReader reader) {
        return Process("", reader);
    }

    public static String Process(BufferedReader reader, Handler handler) {
        return Process("", reader, handler);
    }

    private static final String[] AD_KEYWORDS = {
            "adsvideo", "gvt1.com", "doubleclick.net", "googleads", "analytics", "ads.ts", "ad-", "-ad", "ad_", "_ad", "ad/", "/ad", "pstatp", "toutiao", "byteimg", "adservice", "adsystem", "union.video", "volcengine", "vcloud", "m3u8-ad", "video-ads", "v-ad", "short.video", "video_ad", "marketing"
    };

    private static boolean isAdUrl(String url) {
        if (url == null) return false;
        String lowerUrl = url.toLowerCase();
        for (String keyword : AD_KEYWORDS) {
            if (lowerUrl.contains(keyword)) return true;
        }
        return false;
    }

    private static M3U8AdFilterResult parseAndFilterM3U8(String url, BufferedReader reader) {
        Log.d("M3U8Parser", "Analyzing M3U8 Content: " + url);
        List<String> lines = new ArrayList<>();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        } catch (IOException e) {
            Log.e("M3U8Parser", "IOException: " + e.getMessage());
        }

        String rawContent = String.join("\n", lines);
        if (rawContent.contains("#EXT-X-STREAM-INF")) {
            Log.d("M3U8Parser", "Master Playlist detected, skipping filter.");
            return new M3U8AdFilterResult(rawContent, 0, 0.0);
        }

        String cacheKey = url + "_" + rawContent.hashCode();
        M3U8AdFilterResult cached = cache.get(cacheKey);
        if (cached != null) {
            Log.d("M3U8Parser", "ADFilter Result Cache Hit for: " + url);
            return cached;
        }

        // 1. Calculate main path feature and main config feature
        Map<String, Integer> pathCountMap = new HashMap<>();
        Map<String, Integer> configCountMap = new HashMap<>();
        String activeConfig = "";

        for (String line : lines) {
            if (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")) {
                activeConfig = line;
            } else if (isMediaSegment(line)) {
                String feature = getUrlFeature(line);
                pathCountMap.put(feature, pathCountMap.getOrDefault(feature, 0) + 1);
                if (!activeConfig.isEmpty()) {
                    configCountMap.put(activeConfig, configCountMap.getOrDefault(activeConfig, 0) + 1);
                }
            }
        }

        String mainFeature = "";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : pathCountMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mainFeature = entry.getKey();
            }
        }

        String mainConfig = "";
        maxCount = 0;
        for (Map.Entry<String, Integer> entry : configCountMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mainConfig = entry.getKey();
            }
        }

        // 2. Group into blocks by #EXT-X-DISCONTINUITY, CUE tags, or CONFIG changes
        List<M3U8Block> blocks = new ArrayList<>();
        M3U8Block currentBlock = new M3U8Block();
        blocks.add(currentBlock);
        boolean inCueAd = false;
        activeConfig = "";

        for (String line : lines) {
            if (line.startsWith("#EXT-X-CUE-OUT") || line.startsWith("#EXT-X-CUT-OUT")) {
                if (currentBlock.segmentCount > 0) {
                    currentBlock = new M3U8Block();
                    blocks.add(currentBlock);
                }
                inCueAd = true;
                currentBlock.hasCueAd = true;
                currentBlock.configFeature = activeConfig;
                currentBlock.lines.add(line);
            } else if (line.startsWith("#EXT-X-CUE-IN") || line.startsWith("#EXT-X-CUT-IN")) {
                currentBlock.lines.add(line);
                inCueAd = false;
                currentBlock = new M3U8Block();
                blocks.add(currentBlock);
                currentBlock.configFeature = activeConfig;
            } else if (line.equals("#EXT-X-DISCONTINUITY")) {
                if (currentBlock.segmentCount > 0) {
                    currentBlock = new M3U8Block();
                    blocks.add(currentBlock);
                }
                currentBlock.hasStartDiscontinuity = true;
                currentBlock.hasCueAd = inCueAd;
                currentBlock.configFeature = activeConfig;
                currentBlock.lines.add(line);
            } else if (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")) {
                if (currentBlock.segmentCount > 0) {
                    currentBlock = new M3U8Block();
                    blocks.add(currentBlock);
                }
                activeConfig = line;
                currentBlock.configFeature = activeConfig;
                currentBlock.hasCueAd = inCueAd;
                currentBlock.lines.add(line);
            } else {
                currentBlock.lines.add(line);
                currentBlock.hasCueAd = inCueAd;
                currentBlock.configFeature = activeConfig;
                if (line.startsWith("#EXTINF:")) {
                    try {
                        String durationStr = line.substring(8).split(",")[0];
                        currentBlock.duration += Double.parseDouble(durationStr);
                    } catch (Exception ignored) {}
                } else if (isMediaSegment(line)) {
                    currentBlock.segmentCount++;
                    Long currentNum = extractSegmentNumber(line);
                    if (currentBlock.lastNum != null && currentNum != null && Math.abs(currentNum - currentBlock.lastNum) > 1) {
                        currentBlock.hasSequenceJump = true;
                    }
                    currentBlock.lastNum = currentNum;
                    if (isAdUrl(line) || (!mainFeature.isEmpty() && isAdUrlSmart(line, mainFeature))) {
                        currentBlock.hasAdUrl = true;
                    }
                } else if (line.equals("#EXT-X-ENDLIST")) {
                    currentBlock.hasEndList = true;
                }
            }
        }

        // 3. Evaluate each block
        StringBuilder output = new StringBuilder();
        int adCount = 0;
        double adDuration = 0.0;
        double totalDuration = 0.0;
        boolean processedFirstMediaBlock = false;

        for (M3U8Block block : blocks) {
            if (block.lines.isEmpty()) continue;
            
            totalDuration += block.duration;
            boolean isAd = false;

            if (block.segmentCount > 0) {
                // Determine if config mismatch
                boolean configMismatch = !mainConfig.isEmpty() && !block.configFeature.equals(mainConfig);

                // 🚀 核心修正：如果區塊時長 > 120秒 或分片數 > 30，除非有明確的 Cue 標籤或 AdUrl，否則視為正片
                boolean isLikelyLongVideo = block.duration > 120 || block.segmentCount > 30;

                if (block.hasCueAd) {
                    isAd = true;
                } else if (block.hasAdUrl) {
                    isAd = true;
                } else if (isLikelyLongVideo) {
                    isAd = false;
                } else if (block.hasSequenceJump || configMismatch) {
                    isAd = true;
                } else if (block.duration > 0 && block.duration < 60) {
                    if (block.hasStartDiscontinuity && processedFirstMediaBlock) {
                        isAd = true;
                    } else if (block.hasEndList && processedFirstMediaBlock) {
                        isAd = true;
                    }
                }
            }

            if (isAd) {
                adCount ++; //= block.segmentCount;
                adDuration += block.duration;
                Log.d("M3U8Parser", "Filtered Block (AD): Duration=" + block.duration + ", Segments=" + block.segmentCount + ", Cue=" + block.hasCueAd + ", ConfigMismatch=" + (!mainConfig.isEmpty() && !block.configFeature.equals(mainConfig)));
                if (block.hasEndList) {
                    output.append("#EXT-X-ENDLIST\n");
                }
            } else {
                for (String line : block.lines) {
                    output.append(line).append("\n");
                }
                if (block.segmentCount > 0) {
                    processedFirstMediaBlock = true;
                }
            }
        }

        adDuration = Math.round(adDuration * 10.0) / 10.0;
        double adRatio = adDuration / (totalDuration > 0 ? totalDuration : 1.0);
        Log.d("M3U8Parser", "=== Summary ===");
        Log.d("M3U8Parser", "Total: " + totalDuration + ", AD Total: " + adDuration + ", AD Count: " + adCount + ", AD Ratio: " + (Math.round(adRatio * 100.0) / 100.0));

        M3U8AdFilterResult result;
        if (adRatio > 0.5 && totalDuration > 0) {
             Log.w("M3U8Parser", "Warning: AD ratio > 50%, returning raw content to avoid false positive");
             result = new M3U8AdFilterResult(rawContent, 0, 0.0);
        } else if (adDuration > 0 && adRatio > 0.15 && totalDuration > 300) {
            Log.w("M3U8Parser", "Warning: AD ratio > 15% in long video, dropping video (-1)");
            result = new M3U8AdFilterResult(rawContent, -1, 0.0);
        } else {
            Log.d("M3U8Parser", "Successfully filtered M3U8");
            result = new M3U8AdFilterResult(output.toString(), adCount, adDuration);
        }

        cache.put(cacheKey, result);
        return result;
    }

    private static boolean isMediaSegment(String line) {
        if (line == null) return false;
        String lower = line.toLowerCase();
        return lower.endsWith(".ts") || lower.endsWith(".jpeg") || lower.endsWith(".jpg") || lower.endsWith(".m4s") || lower.endsWith(".mp4") ||
                lower.contains(".ts?") || lower.contains(".jpeg?") || lower.contains(".jpg?") || lower.contains(".m4s?") || lower.contains(".mp4?");
    }

    private static String getUrlFeature(String url) {
        if (url == null || url.isEmpty()) return "";
        String cleanUrl = PATTERN_URL_CLEAN.matcher(url).replaceAll("").trim();
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }
        int lastSlashIdx = cleanUrl.lastIndexOf('/');
        if (lastSlashIdx > 0) {
            String feature = cleanUrl.substring(0, lastSlashIdx);
            String checkStr = feature.replace("https://", "").replace("http://", "");
            if (checkStr.length() < 3) {
                return "[short_path]";
            }
            return feature;
        }
        return "[relative]";
    }

    private static boolean isAdUrlSmart(String url, String mainFeature) {
        if (mainFeature == null || mainFeature.isEmpty()) return false;
        String currentFeature = getUrlFeature(url);
        return !currentFeature.equals(mainFeature);
    }

    private static Long extractSegmentNumber(String tsFilename) {
        try {
            Matcher matcher = PATTERN_SEGMENT.matcher(tsFilename);
            if (matcher.find()) {
                return Long.parseLong(Objects.requireNonNull(matcher.group(1)));
            }
        } catch (Exception e) {
            // Log.e("M3U8Parser", "Failed to parse segment number from: " + tsFilename);
        }
        return null;
    }

    private static class M3U8Block {
        List<String> lines = new ArrayList<>();
        double duration = 0.0;
        int segmentCount = 0;
        boolean hasAdUrl = false;
        boolean hasStartDiscontinuity = false;
        boolean hasEndList = false;
        boolean hasCueAd = false;
        String configFeature = "";
        Long lastNum = null;
        boolean hasSequenceJump = false;
    }

    private static class M3U8AdFilterResult {
        public  String filteredContent;
        public  int adSegmentCount;
        public  double adDurationSeconds;

        public M3U8AdFilterResult(String filteredContent, int adSegmentCount, double adDurationSeconds) {
            this.filteredContent = filteredContent;
            this.adSegmentCount = adSegmentCount;
            this.adDurationSeconds = adDurationSeconds;
        }
    }

    public interface M3U8ParseListener {
        void onAdSegmentsFiltered(int adCount, double adSeconds);
    }

    public static void setM3U8ParseListener(M3U8ParseListener listener) {
        parseListener = listener;
    }
    public static void notifyAdSegmentsFiltered(int adCount, double adSeconds) {
        if (parseListener != null) {
            parseListener.onAdSegmentsFiltered(adCount, adSeconds);
        }
    }

    public static void initListener() {
        setM3U8ParseListener(new M3U8ParseListener() {
            private int lastCount = 0;
            private double lastSeconds = 0;
            private long lastTime = 0;

            @Override
            public void onAdSegmentsFiltered(int adCount, double adSeconds) {
                // 利用 Looper 確保吐回主線程彈出 UI 通知
                new Handler(Looper.getMainLooper()).post(() -> {
                    long currentTime = System.currentTimeMillis();
                    // 如果跟上次過濾的數量/時間一樣，或間隔小於 10 分鐘，就不重複提示
                    if (adCount == lastCount && Math.abs(adSeconds - lastSeconds) < 0.1 || (currentTime - lastTime) < 600000) {
                        return;
                    }

                    if (adCount > 0) {
                        Notify.showTop("過濾 " + adCount + " 段廣告，共 " + adSeconds + " 秒");
                        lastCount = adCount;
                        lastSeconds = adSeconds;
                        lastTime = currentTime;
                    } else if (adCount < 0 && (currentTime - lastTime) > 60000) {
                        Notify.showTop("廣告過濾失敗");
                        lastCount = adCount;
                        lastTime = currentTime;
                    }
                });
            }
        });
    }
}
