package com.fongmi.android.tv.player;

import androidx.media3.common.util.Log;

import android.os.Handler;
import android.util.LruCache;

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
            "adsvideo", "gvt1.com", "doubleclick.net", "googleads", "analytics", "ads.ts", "ad-", "-ad"
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
        StringBuilder output = new StringBuilder();
        List<String> lines = new ArrayList<>();
        try {
            String l;
            while ((l = reader.readLine()) != null) {
                lines.add(l.trim());
            }
        } catch (IOException e) {
            Log.e("M3U8Parser", "IOException: " + e.getMessage());
        }

        String rawContent = String.join("\n", lines);
        String cacheKey = url + "_" + rawContent.hashCode();
        M3U8AdFilterResult cached = cache.get(cacheKey);
        if (cached != null) return cached;

        // === 🧠 聰明廣告偵測：動態統計網址特徵 ===
        Map<String, Integer> pathCountMap = new HashMap<>();
        int totalTsCount = 0;

        for (String line : lines) {
            if (line.endsWith(".ts") || line.endsWith(".jpeg") || line.endsWith(".jpg")) {
                totalTsCount++;
                // 提取前兩層目錄作為特徵，例如 "/20240706/JYhxNTES" 或 "/20260621/EksfpUSn"
                String feature = getUrlFeature(line);
                pathCountMap.put(feature, pathCountMap.getOrDefault(feature, 0) + 1);
            }
        }

        // 找出出現次數最多、絕對是正片的「主流特徵」
        String mainFeature = "";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : pathCountMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mainFeature = entry.getKey();
            }
        }
        // === 聰明偵測結束 ===

        boolean inAd = false;
        boolean passFirstDiscontinuity = false;
        double adDuration = 0.0;
        int adCount = 0;
        double currentDuration = 0.0;
        double totalDuration = 0.0;
        Long lastSegmentNumber = null;
        String pendingExtInfLine = null;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("#EXTINF:")) {
                pendingExtInfLine = line;
                if (!passFirstDiscontinuity) passFirstDiscontinuity = true;
                try {
                    double duration = Double.parseDouble(line.substring(8).split(",")[0]);
                    totalDuration += duration;
                    if (inAd) {
                        currentDuration += duration;
                        pendingExtInfLine = null;
                    }
                } catch (Exception ignored) {}
                continue;
            }

            if (line.startsWith("#EXT-X-CUE-OUT")) {
                inAd = true;
                continue;
            }
            if (line.equals("#EXT-X-CUE-IN")) {
                inAd = false;
                continue;
            }

            if (line.equals("#EXT-X-DISCONTINUITY")) {
                if (!passFirstDiscontinuity) {
                    passFirstDiscontinuity = true;
                    output.append(line).append("\n");
                    continue;
                }
                //String nextExtInf = (i + 1 < lines.size()) ? lines.get(i + 1) : null;
                //String nextTsLine = (i + 2 < lines.size()) ? lines.get(i + 2) : null;
                // --- 擴充開始：動態向後尋找下一個 EXTINF 和 TS 網址，跳過夾帶的標籤 ---
                String nextExtInf = null;
                String nextTsLine = null;
                int lookAhead = i + 1;

                // 往下找第一個出現的 #EXTINF:
                while (lookAhead < lines.size()) {
                    String forwardLine = lines.get(lookAhead);
                    if (forwardLine.startsWith("#EXTINF:")) {
                        nextExtInf = forwardLine;
                        // 再下一行通常就是 TS 網址
                        if (lookAhead + 1 < lines.size()) {
                            nextTsLine = lines.get(lookAhead + 1);
                        }
                        break;
                    }
                    // 如果遇到下一個不連續標記或結束標籤，代表中間沒有媒體片段了
                    if (forwardLine.equals("#EXT-X-DISCONTINUITY") || forwardLine.equals("#EXT-X-ENDLIST")) {
                        break;
                    }
                    lookAhead++;
                }
                // --- 擴充結束 ---



                if (!inAd) {
                    if (nextExtInf != null && nextExtInf.startsWith("#EXTINF:") && nextTsLine != null) {
                        // 2. 修正：結合您原本的偵測與「聰明特徵比對」
                        if (isAdUrlSmart(nextTsLine, mainFeature) || isAdUrl(nextTsLine)) {
                            inAd = true;
                        } else if ((nextTsLine.endsWith(".ts") || nextTsLine.endsWith(".jpeg") || nextTsLine.endsWith(".jpg"))) {
                            Long nextNumber = extractSegmentNumber(nextTsLine);
                            if (lastSegmentNumber != null && nextNumber != null && nextNumber == lastSegmentNumber + 1) {
                                output.append(line).append("\n");
                            } else {
                                inAd = true;
                            }
                        } else {
                            inAd = true;
                        }
                    } else {
                        output.append(line).append("\n");
                    }
                } else {
                    adDuration += currentDuration;
                    adCount++;
                    currentDuration = 0.0;
                    inAd = false;
                }
                continue;
            }

            if (line.endsWith(".ts") || line.endsWith(".jpeg") || line.endsWith(".jpg")) {
                // 3. 修正：同時用舊邏輯與新特徵檢查
                if (isAdUrlSmart(line, mainFeature) || isAdUrl(line)) inAd = true;
                Long currentNumber = extractSegmentNumber(line);
                if (!inAd) {
                    if (pendingExtInfLine != null) output.append(pendingExtInfLine).append("\n");
                    output.append(line).append("\n");
                    lastSegmentNumber = currentNumber;
                }
                pendingExtInfLine = null;
                continue;
            }

            if (!inAd) {
                if (pendingExtInfLine != null) {
                    output.append(pendingExtInfLine).append("\n");
                    pendingExtInfLine = null;
                }
                output.append(line).append("\n");
            }
        }

        if (inAd && currentDuration > 0.0) {
            adDuration += currentDuration;
            adCount++;
        }
        adDuration = Math.round(adDuration * 10.0) / 10.0;

        M3U8AdFilterResult result;
        if (adDuration / (totalDuration > 0 ? totalDuration : 1.0) > 0.1) {
            result = new M3U8AdFilterResult(rawContent, -1, 0.0);
        } else {
            result = new M3U8AdFilterResult(output.toString(), adCount, adDuration);
        }

        cache.put(cacheKey, result);
        return result;
    }


    // === 💡 新增的兩個統計輔助方法 ===

    /**
     * 提取網址前置的目錄結構作為統計特徵
     * 例如 "/20240706/JYhxNTES/2000kb/hls/l2edmBGC.ts" -> "/20240706/JYhxNTES"
     */
    private static String getUrlFeature(String url) {
        if (url == null || url.isEmpty()) return "";
        String[] parts = url.split("/");
        StringBuilder feature = new StringBuilder();
        int count = 0;
        for (String part : parts) {
            if (!part.isEmpty()) {
                feature.append("/").append(part);
                count++;
                if (count >= 2) break; // 取前兩層目錄就足以分辨不同的影片來源
            }
        }
        return feature.toString();
    }

    /**
     * 聰明比對：如果這條網址的特徵和統計出來的主流特徵（正片）不同，它就是廣告
     */
    private static boolean isAdUrlSmart(String url, String mainFeature) {
        if (mainFeature == null || mainFeature.isEmpty()) return false;
        String currentFeature = getUrlFeature(url);
        // 當前網址的目錄特徵與主要正片不同，認定為廣告
        return !currentFeature.equals(mainFeature);
    }

    // Helper to extract numeric part of segment
    private static Long extractSegmentNumber(String tsFilename) {
        try {
            Pattern pattern = Pattern.compile("(\\d+)\\.(ts|jpe?g)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(tsFilename);
            if (matcher.find()) {
                return Long.parseLong(matcher.group(1));
            }
        } catch (Exception e) {
            Log.e("M3U8Parser", "Failed to parse segment number from: " + tsFilename);
        }
        return null;
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

    //private static M3U8ParseListener parseListener;
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
}
