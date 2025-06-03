package com.fongmi.android.tv.player;

import androidx.media3.common.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import android.os.Handler;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;




public class ADFilter {
    private static M3U8ParseListener parseListener;

    public static String Process (BufferedReader reader) {

        M3U8AdFilterResult result = parseAndFilterM3U8(reader);
        notifyAdSegmentsFiltered(result.adSegmentCount, result.adDurationSeconds);

        return result.filteredContent;

    }

    public static String Process (BufferedReader reader, Handler handler) {

        M3U8AdFilterResult result = parseAndFilterM3U8(reader);
        handler.post(() -> //parseListener.onAdSegmentsFiltered(finalAdCount, finalAdSeconds));
            notifyAdSegmentsFiltered(result.adSegmentCount, result.adDurationSeconds));

        return result.filteredContent;

    }

    private static M3U8AdFilterResult parseAndFilterM3U8(BufferedReader reader) {

        StringBuilder output = new StringBuilder(), output1 = new StringBuilder();
        String line;
        boolean inAd = false;
        boolean passFirstDiscontinuity = false;
        double adDuration = 0.0;
        int adCount = 0;

        double currentDuration = 0.0;
        double totalDuration = 0.0;
        Long lastSegmentNumber = null;
        String pendingExtInfLine = null;

        try {
            List<String> lines = new ArrayList<>();
            String l;
            while ((l = reader.readLine()) != null) {
                lines.add(l.trim());
            }

            for (int i = 0; i < lines.size(); i++) {
                line = lines.get(i);
                output1.append(line).append("\n");

                if (line.startsWith("#EXTINF:")) {
                    pendingExtInfLine = line;

                    if (!passFirstDiscontinuity) passFirstDiscontinuity = true;

                    try {
                        double duration = Double.parseDouble(line.substring(8).split(",")[0]);
                        totalDuration += duration;
                        if (inAd) {
                            currentDuration += duration;
                            pendingExtInfLine = null;
                            //output.append(line).append("\n");
                        }

                    } catch (NumberFormatException e) {
                        //currentDuration = 0.0;
                    }
                    continue;
                }

                if (line.startsWith("#EXT-X-CUE-OUT")) {
                    inAd = true;
                    continue;
                }
                if (line.equals("#EXT-X-CUE-IN")){
                    inAd = false;
                    continue;
                }

                if (line.equals("#EXT-X-DISCONTINUITY")) {
                    if (!passFirstDiscontinuity) {
                        passFirstDiscontinuity = true;
                        output.append(line).append("\n");
                        continue;
                    }

                    // 檢查下一段 ts 檔名是否為連號
                    String nextExtInf = (i + 1 < lines.size()) ? lines.get(i + 1) : null;
                    String nextTsLine = (i + 2 < lines.size()) ? lines.get(i + 2) : null;
                    if (!inAd) {
                        if (nextExtInf != null && nextExtInf.startsWith("#EXTINF:")
                                && nextTsLine != null) {

                            if ((nextTsLine.endsWith(".ts") || nextTsLine.endsWith(".jpeg") || nextTsLine.endsWith(".jpg"))) {

                                Long nextNumber = extractSegmentNumber(nextTsLine);
                                if (lastSegmentNumber != null && nextNumber != null && nextNumber == lastSegmentNumber + 1) {
                                    // 是連號，保留
                                    output.append(line).append("\n");
                                } else {
                                    // 不連號，視為廣告開始
                                    inAd = true;
                                }
                            }
                            else {
                                // 非正規副檔名，視為廣告開始
                                inAd = true;
                            }
                        } else {
                            // 無法解析，保守保留
                            output.append(line).append("\n");
                        }
                    } else {
                        // 廣告結束
                        adDuration += currentDuration;
                        adCount++;
                        currentDuration = 0.0;
                        inAd = false;
                    }
                    continue;
                }

                if (line.endsWith(".ts") || line.endsWith(".jpeg") || line.endsWith(".jpg")) {
                    Long currentNumber = extractSegmentNumber(line);

                    if (!inAd) {
                        if (pendingExtInfLine != null) {
                            output.append(pendingExtInfLine).append("\n");
                        }
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

            // 處理最後可能遺漏的廣告段
            if (inAd && currentDuration > 0.0) {
                adDuration += currentDuration;
                adCount++;
            }
            adDuration = Math.round(adDuration * 10.0) / 10.0;

        } catch (IOException e){
            Log.e("M3U8Parser", "IOException while reading playlist: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    Log.e("M3U8Parser", "IOException while closing reader: " + e.getMessage(), e);
                }
            }
        }
        Log.d("ADFilter", "Filtered out " + adCount + " ad sections, total " + adDuration + " seconds");
        if (adDuration / totalDuration > 0.1) {
            //Wrong analyze
            return new M3U8AdFilterResult(output1.toString(), -1, 0.0);
        }
        return new M3U8AdFilterResult(output.toString(), adCount, adDuration);
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
