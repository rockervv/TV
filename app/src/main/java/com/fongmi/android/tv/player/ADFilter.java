package com.fongmi.android.tv.player;

import androidx.media3.common.util.Log;

import android.os.Handler;
import android.util.LruCache;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
                String nextExtInf = (i + 1 < lines.size()) ? lines.get(i + 1) : null;
                String nextTsLine = (i + 2 < lines.size()) ? lines.get(i + 2) : null;
                if (!inAd) {
                    if (nextExtInf != null && nextExtInf.startsWith("#EXTINF:") && nextTsLine != null) {
                        if (isAdUrl(nextTsLine)) {
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
                if (isAdUrl(line)) inAd = true;
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
