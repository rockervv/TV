package com.fongmi.android.tv.player.util;

import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.media3.common.util.Log;

import com.fongmi.android.tv.bean.Ad;
import com.fongmi.android.tv.utils.Notify;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ADFilter {
    private static M3U8ParseListener parseListener;
    private static final LruCache<String, M3U8AdFilterResult> cache = new LruCache<>(10);
    private static final Pattern PATTERN_SEGMENT = Pattern.compile("(\\d+)\\.(ts|jpe?g|m4s|mp4)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_URL_CLEAN = Pattern.compile("[\\s\\u200B\\u00A0]+");

    public static String Process(String url, BufferedReader reader, String sourceId) {
        M3U8AdFilterResult result = parseAndFilterM3U8(url, readLines(reader), sourceId);
        notifyAdSegmentsFiltered(result.adSegmentCount, result.adDurationSeconds);
        return result.filteredContent;
    }

    public static String Process(String url, BufferedReader reader, Handler handler, String sourceId) {
        M3U8AdFilterResult result = parseAndFilterM3U8(url, readLines(reader), sourceId);
        handler.post(() -> notifyAdSegmentsFiltered(result.adSegmentCount, result.adDurationSeconds));
        return result.filteredContent;
    }

    public static String Process(String url, String content, String sourceId) {
        M3U8AdFilterResult result = parseAndFilterM3U8(url, content, sourceId);
        notifyAdSegmentsFiltered(result.adSegmentCount, result.adDurationSeconds);
        return result.filteredContent;
    }

    public static String Process(String url, String content, Handler handler, String sourceId) {
        M3U8AdFilterResult result = parseAndFilterM3U8(url, content, sourceId);
        handler.post(() -> notifyAdSegmentsFiltered(result.adSegmentCount, result.adDurationSeconds));
        return result.filteredContent;
    }

    public static String Process(BufferedReader reader) {
        return Process("", reader, "");
    }

    public static String Process(BufferedReader reader, Handler handler) {
        return Process("", reader, handler, "");
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

    private static List<String> readLines(BufferedReader reader) {
        List<String> lines = new ArrayList<>();
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line.trim());
            }
        } catch (IOException e) {
            Log.e("M3U8Parser", "IOException: " + e.getMessage());
        }
        return lines;
    }

    private static M3U8AdFilterResult parseAndFilterM3U8(String url, String content, String sourceId) {
        List<String> lines = new ArrayList<>();
        if (content != null) {
            for (String line : content.split("\n")) {
                lines.add(line.trim());
            }
        }
        return parseAndFilterM3U8(url, lines, sourceId);
    }

    private static M3U8AdFilterResult parseAndFilterM3U8(String url, List<String> lines, String sourceId) {
        Log.d("ADFilter", "Analyzing M3U8 Content: " + url + " | Source: " + sourceId);
        String rawContent = String.join("\n", lines);
        if (rawContent.contains("#EXT-X-STREAM-INF")) {
            Log.d("ADFilter", "Bypass ADFilter: Master Playlist detected.");
            return new M3U8AdFilterResult(rawContent, 0, 0.0);
        }

        String cacheKey = url + "_" + rawContent.hashCode() + "_" + sourceId;
        M3U8AdFilterResult cached = cache.get(cacheKey);
        if (cached != null) {
            Log.d("ADFilter", "ADFilter Result Cache Hit for: " + url);
            return cached;
        }

        // Fetch saved ads for this source
        List<Ad> savedAds = sourceId.isEmpty() ? new ArrayList<>() : Ad.get(sourceId);

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
                    if (currentBlock.firstNum == null) currentBlock.firstNum = currentNum;
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
        Long globalLastNum = null;
        String lastEmittedConfig = "";
        boolean needDiscontinuity = false;

        for (int i = 0; i < blocks.size(); i++) {
            M3U8Block block = blocks.get(i);
            if (block.lines.isEmpty()) continue;
            
            totalDuration += block.duration;
            boolean isAd = false;
            boolean isSandwichAd = false;
            boolean sequenceJump = false;

            if (block.segmentCount > 0) {
                // Time-based check (if current block start time matches a saved ad)
                long currentOffset = (long) (totalDuration - block.duration) * 1000;
                for (Ad ad : savedAds) {
                    // Check all occurrences (time offsets)
                    for (Long offset : ad.getTimeOffsetList()) {
                        if (Math.abs(offset - currentOffset) < 5000) { // 5s tolerance
                            isAd = true;
                            break;
                        }
                    }
                    if (isAd) break;

                    // Check URL patterns
                    if (ad.getUrlPatterns() != null && !ad.getUrlPatterns().isEmpty()) {
                        String[] patterns = ad.getUrlPatterns().split(",");
                        // Simple check: if any segment in block matches the first pattern
                        // (Improved check could match the whole sequence)
                        for (String line : block.lines) {
                            if (isMediaSegment(line)) {
                                String currentP = extractUrlFeature(line);
                                if (currentP != null && !currentP.isEmpty() && currentP.equals(patterns[0])) {
                                    isAd = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (isAd) break;
                }

                if (!isAd) {
                    boolean configMismatch = !mainConfig.isEmpty() && !block.configFeature.equals(mainConfig);
                    boolean isLikelyLongVideo = block.duration > 120 || block.segmentCount > 30;
                    boolean continuous = globalLastNum != null && block.firstNum != null && Math.abs(block.firstNum - globalLastNum) <= 1;
                    sequenceJump = globalLastNum != null && block.firstNum != null && !continuous;

                    // Sandwich Ad detection: if current block is a jump, but a future block is continuous with previous
                    if (sequenceJump) {
                        for (int j = i + 1; j < Math.min(i + 4, blocks.size()); j++) {
                            M3U8Block futureBlock = blocks.get(j);
                            if (futureBlock.firstNum != null && globalLastNum != null && Math.abs(futureBlock.firstNum - globalLastNum) <= 1) {
                                isSandwichAd = true;
                                break;
                            }
                        }
                    }

                    if (block.hasCueAd) {
                        isAd = true;
                    } else if (block.hasAdUrl) {
                        isAd = true;
                    } else if (isLikelyLongVideo) {
                        isAd = false;
                    } else if (isSandwichAd || block.hasSequenceJump || configMismatch || sequenceJump) {
                        isAd = true;
                    } else if (block.duration > 0 && block.duration < 25) {
                        if (block.hasStartDiscontinuity && processedFirstMediaBlock) {
                            if (continuous) {
                                isAd = false;
                            } else {
                                isAd = true;
                            }
                        } else if (block.hasEndList && processedFirstMediaBlock) {
                            isAd = true;
                        }
                    }
                }
            }

            if (isAd) {
                adCount++;
                adDuration += block.duration;
                needDiscontinuity = true; // 標記需要插入不連續標記，確保播放器能處理時間跳躍
                Log.d("ADFilter", "Physically Removed Ad Block: Time=" + com.fongmi.android.tv.utils.Util.timeMs((long)(totalDuration - block.duration) * 1000) + ", Duration=" + block.duration + "s");
                
                // 物理刪除：不將此區塊的媒體片段（TS/M4S）加入輸出內容
                // 但我們仍需掃描是否有配置資訊（如 KEY/MAP）在廣告塊中定義，以便後續片段使用
                for (String line : block.lines) {
                    if (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")) {
                        activeConfig = line;
                    }
                }
                
                if (block.segmentCount > 0) {
                    processedFirstMediaBlock = true;
                    if (block.lastNum != null) globalLastNum = block.lastNum;
                }
            } else {
                if (block.segmentCount > 0) {
                    boolean continuous = globalLastNum != null && block.firstNum != null && Math.abs(block.firstNum - globalLastNum) <= 1;
                    // 如果之前刪除了廣告，或者原本就有不連續標記，且序號不連續，則補上 DISCONTINUITY
                    if ((needDiscontinuity || !continuous) && !block.hasStartDiscontinuity && globalLastNum != null) {
                        output.append("#EXT-X-DISCONTINUITY\n");
                    }
                    needDiscontinuity = false;

                    // 確保加密金鑰 (KEY/MAP) 在刪除廣告後仍能正確銜接
                    if (!block.configFeature.isEmpty() && !block.configFeature.equals(lastEmittedConfig)) {
                        boolean alreadyHasConfig = false;
                        for (String line : block.lines) {
                            if (line.equals(block.configFeature)) {
                                alreadyHasConfig = true;
                                break;
                            }
                        }
                        if (!alreadyHasConfig) {
                            output.append(block.configFeature).append("\n");
                            lastEmittedConfig = block.configFeature;
                        }
                    }
                }

                boolean firstMediaInBlock = true;
                for (String line : block.lines) {
                    if (isMediaSegment(line)) {
                        if (block.segmentCount > 0 && (block.hasStartDiscontinuity || firstMediaInBlock)) {
                            // 針對非廣告但需要「背景偵測」的片段（每段開頭），標記 ad_check=1 觸發音訊偵測
                            String taggedLine = line.contains("?") ? line + "&ad_check=1" : line + "?ad_check=1";
                            output.append(taggedLine).append("\n");
                            firstMediaInBlock = false;
                        } else {
                            output.append(line).append("\n");
                        }
                    } else {
                        output.append(line).append("\n");
                    }
                    if (line.startsWith("#EXT-X-KEY") || line.startsWith("#EXT-X-MAP")) {
                        lastEmittedConfig = line;
                    }
                }

                if (block.segmentCount > 0) {
                    processedFirstMediaBlock = true;
                    if (block.lastNum != null) globalLastNum = block.lastNum;
                }
            }
        }

        adDuration = Math.round(adDuration * 10.0) / 10.0;
        double adRatio = adDuration / (totalDuration > 0 ? totalDuration : 1.0);
        Log.d("ADFilter", "=== Summary ===");
        Log.d("ADFilter", "Total: " + totalDuration + ", AD Total: " + adDuration + ", AD Count: " + adCount + ", AD Ratio: " + (Math.round(adRatio * 100.0) / 100.0));

        M3U8AdFilterResult result;
        if (adRatio > 0.5 && totalDuration > 0) {
             Log.w("ADFilter", "Bypass ADFilter: AD ratio > 50%, returning raw content to avoid false positive");
             result = new M3U8AdFilterResult(rawContent, 0, 0.0);
        } else if (adDuration > 0 && adRatio > 0.15 && totalDuration > 300) {
            Log.w("ADFilter", "Bypass ADFilter: AD ratio > 15% in long video, dropping video (-1)");
            result = new M3U8AdFilterResult(rawContent, -1, 0.0);
        } else {
            Log.d("ADFilter", "Successfully marked/filtered M3U8");
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

    public static String extractUrlFeature(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            int queryIdx = url.indexOf('?');
            String path = queryIdx > 0 ? url.substring(0, queryIdx) : url;
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0) {
                String filename = path.substring(lastSlash + 1);
                return filename.replaceAll("\\d+", "");
            }
        } catch (Exception ignored) {}
        return "";
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

    private static Long extractSegmentNumber(String url) {
        try {
            int queryIdx = url.indexOf('?');
            String path = queryIdx > 0 ? url.substring(0, queryIdx) : url;
            Matcher matcher = PATTERN_SEGMENT.matcher(path);
            String lastMatch = null;
            while (matcher.find()) {
                lastMatch = matcher.group(1);
            }
            if (lastMatch != null) {
                return Long.parseLong(lastMatch);
            }
        } catch (Exception ignored) {
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
        Long firstNum = null;
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
                new Handler(Looper.getMainLooper()).post(() -> {
                    long currentTime = System.currentTimeMillis();
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
