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
        
        if (rawContent.contains("#EXT-X-STREAM-INF")) {
            return new M3U8AdFilterResult(rawContent, 0, 0.0);
        }

        String cacheKey = url + "_" + rawContent.hashCode();
        M3U8AdFilterResult cached = cache.get(cacheKey);
        if (cached != null) return cached;

        Map<String, Integer> pathCountMap = new HashMap<>();
        int totalTsCount = 0;

        for (String line : lines) {
            if (line.endsWith(".ts") || line.endsWith(".jpeg") || line.endsWith(".jpg") || line.contains(".ts?") || line.contains(".jpeg?") || line.contains(".jpg?")) {
                totalTsCount++;
                String feature = getUrlFeature(line);
                pathCountMap.put(feature, pathCountMap.getOrDefault(feature, 0) + 1);
            }
        }

        String mainFeature = "";
        int maxCount = 0;
        Log.d("M3U8Parser", "=== Start path feature statistics ===");
        for (Map.Entry<String, Integer> entry : pathCountMap.entrySet()) {
            Log.d("M3U8Parser", "Feature path: [" + entry.getKey() + "] Count: " + entry.getValue());
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mainFeature = entry.getKey();
            }
        }
        Log.d("M3U8Parser", "-> Selected main feature (video): [" + mainFeature + "]");
        Log.d("M3U8Parser", "=== End path feature statistics ===");

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
                    String durationStr = line.substring(8).split(",")[0];
                    double duration = Double.parseDouble(durationStr);
                    totalDuration += duration;
                    if (inAd) {
                        currentDuration += duration;
                        pendingExtInfLine = null;
                    }
                } catch (Exception e) {
                    Log.e("M3U8Parser", "Parse EXTINF error: " + line + " Cause: " + e.getMessage());
                }
                continue;
            }

            if (line.startsWith("#EXT-X-CUE-OUT")) {
                inAd = true;
                Log.d("M3U8Parser", "Trigger CUE-OUT, entering AD mode");
                continue;
            }
            if (line.equals("#EXT-X-CUE-IN")) {
                inAd = false;
                Log.d("M3U8Parser", "Trigger CUE-IN, leaving AD mode");
                continue;
            }

            if (line.equals("#EXT-X-DISCONTINUITY")) {
                if (!passFirstDiscontinuity) {
                    passFirstDiscontinuity = true;
                    output.append(line).append("\n");
                    continue;
                }
                
                String nextExtInf = null;
                String nextTsLine = null;
                int lookAhead = i + 1;

                while (lookAhead < lines.size()) {
                    String forwardLine = lines.get(lookAhead);
                    if (forwardLine.startsWith("#EXTINF:")) {
                        nextExtInf = forwardLine;
                        if (lookAhead + 1 < lines.size()) {
                            nextTsLine = lines.get(lookAhead + 1);
                        }
                        break;
                    }
                    if (forwardLine.equals("#EXT-X-DISCONTINUITY") || forwardLine.equals("#EXT-X-ENDLIST")) {
                        break;
                    }
                    lookAhead++;
                }

                Log.d("M3U8Parser", "Meet DISCONTINUITY, look ahead -> EXTINF: [" + nextExtInf + "], TS: [" + nextTsLine + "]");

                if (!inAd) {
                    if (nextExtInf != null && nextExtInf.startsWith("#EXTINF:") && nextTsLine != null) {
                        boolean isAdOld = isAdUrl(nextTsLine);
                        boolean isAdSmart = isAdUrlSmart(nextTsLine, mainFeature);

                        Log.d("M3U8Parser", "DISCONTINUITY AD judgment -> Old: " + isAdOld + ", Smart: " + isAdSmart);

                        if (isAdSmart || isAdOld) {
                            inAd = true;
                            Log.d("M3U8Parser", "-> DISCONTINUITY judged: Entering AD mode");
                        } else if (isMediaSegment(nextTsLine)) {
                            Long nextNumber = extractSegmentNumber(nextTsLine);
                            if (lastSegmentNumber != null && nextNumber != null && nextNumber == lastSegmentNumber + 1) {
                                output.append(line).append("\n");
                            } else {
                                if (!mainFeature.isEmpty()) {
                                    output.append(line).append("\n");
                                } else {
                                    inAd = true;
                                    Log.d("M3U8Parser", "-> Sequence mismatch and no main feature: Entering AD mode");
                                }
                            }
                        } else {
                            inAd = true;
                        }
                    } else {
                        output.append(line).append("\n");
                    }
                } else {
                    Log.d("M3U8Parser", "AD ended, duration: " + currentDuration);
                    adDuration += currentDuration;
                    adCount++;
                    currentDuration = 0.0;
                    inAd = false;
                }
                continue;
            }

            if (isMediaSegment(line)) {
                boolean isAdOld = isAdUrl(line);
                boolean isAdSmart = isAdUrlSmart(line, mainFeature);

                if (isAdSmart || isAdOld) {
                    if (!inAd) {
                        Log.d("M3U8Parser", "Detected AD URL, switching to inAd = true. URL: " + line);
                    }
                    inAd = true;
                }

                Long currentNumber = extractSegmentNumber(line);
                if (!inAd) {
                    if (pendingExtInfLine != null) output.append(pendingExtInfLine).append("\n");
                    output.append(line).append("\n");
                    lastSegmentNumber = currentNumber;
                } else {
                    Log.d("M3U8Parser", "Filtered AD segment: " + line);
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
            } else {
                if (!line.startsWith("#")) {
                    Log.d("M3U8Parser", "Filtered AD line: " + line);
                }
            }
        }

        if (inAd && currentDuration > 0.0) {
            adDuration += currentDuration;
            adCount++;
        }
        adDuration = Math.round(adDuration * 10.0) / 10.0;
        double adRatio = adDuration / (totalDuration > 0 ? totalDuration : 1.0);
        Log.d("M3U8Parser", "=== Summary ===");
        Log.d("M3U8Parser", "Total: " + totalDuration + ", AD Total: " + adDuration + ", AD Count: " + adCount + ", AD Ratio: " + adRatio);

        M3U8AdFilterResult result;
        if (adDuration / (totalDuration > 0 ? totalDuration : 1.0) > 0.1) {
            Log.w("M3U8Parser", "Warning: AD ratio > 10%, dropping video (-1)");
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
        return lower.endsWith(".ts") || lower.endsWith(".jpeg") || lower.endsWith(".jpg") || lower.contains(".ts?") || lower.contains(".jpeg?") || lower.contains(".jpg?");
    }

    private static String getUrlFeature(String url) {
        if (url == null || url.isEmpty()) return "";
        String cleanUrl = url.replaceAll("[\\s\\u200B\\u00A0]+", "").trim();
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
            Pattern pattern = Pattern.compile("(\\d+)\\.(ts|jpe?g)", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(tsFilename);
            if (matcher.find()) {
                return Long.parseLong(Objects.requireNonNull(matcher.group(1)));
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
