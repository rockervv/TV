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
        Log.d("M3U8Parser", "=== 網址特徵統計開始 ===");
        for (Map.Entry<String, Integer> entry : pathCountMap.entrySet()) {
            if (entry.getValue() > maxCount) {
                //Log.d("M3U8Parser", "特徵路徑: [" + entry.getKey() + "] 出現次數: " + entry.getValue());

                maxCount = entry.getValue();
                mainFeature = entry.getKey();
                Log.d("M3U8Parser", "特徵路徑: [" + mainFeature + "] 出現次數: " + maxCount);

            }
        }
        Log.d("M3U8Parser", "➔ 最終選定的主流特徵 (正片): [" + mainFeature + "]");
        Log.d("M3U8Parser", "=== 網址特徵統計結束 ===");

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
                    String durationStr = line.substring(8).split(",")[0];
                    double duration = Double.parseDouble(durationStr);
                    totalDuration += duration;
                    if (inAd) {
                        currentDuration += duration;
                        pendingExtInfLine = null;
                    }
                } catch (Exception e) {
                    Log.e("M3U8Parser", "解析 EXTINF 時間出錯: " + line + " 原因: " + e.getMessage());
                }
                continue;
            }

            if (line.startsWith("#EXT-X-CUE-OUT")) {
                inAd = true;
                Log.d("M3U8Parser", "觸發 CUE-OUT，進入廣告模式");
                continue;
            }
            if (line.equals("#EXT-X-CUE-IN")) {
                inAd = false;
                Log.d("M3U8Parser", "觸發 CUE-IN，離開廣告模式");
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

                Log.d("M3U8Parser", "遇到 DISCONTINUITY，向後尋找結果 -> 找到的 EXTINF: [" + nextExtInf + "], TS網址: [" + nextTsLine + "]");


                if (!inAd) {
                    if (nextExtInf != null && nextExtInf.startsWith("#EXTINF:") && nextTsLine != null) {
                        boolean isAdOld = isAdUrl(nextTsLine);
                        boolean isAdSmart = isAdUrlSmart(nextTsLine, mainFeature);

                        Log.d("M3U8Parser", "DISCONTINUITY 廣告判定 -> 舊邏輯判斷: " + isAdOld + ", 聰明邏輯判斷: " + isAdSmart);


                        // 2. 修正：結合您原本的偵測與「聰明特徵比對」
                        if (isAdSmart || isAdOld) {
                            inAd = true;
                            Log.d("M3U8Parser", "➔ DISCONTINUITY 判定成功：進入廣告狀態");

                        } else if ((nextTsLine.endsWith(".ts") || nextTsLine.endsWith(".jpeg") || nextTsLine.endsWith(".jpg"))) {
                            Long nextNumber = extractSegmentNumber(nextTsLine);
                            if (lastSegmentNumber != null && nextNumber != null && nextNumber == lastSegmentNumber + 1) {
                                output.append(line).append("\n");
                            } else {
                                inAd = true;
                                Log.d("M3U8Parser", "➔ 序號不連續，判定進入廣告狀態");
                            }
                        } else {
                            inAd = true;
                        }
                    } else {
                        output.append(line).append("\n");
                    }
                } else {
                    Log.d("M3U8Parser", "廣告結束，統計此段廣告時間: " + currentDuration);
                    adDuration += currentDuration;
                    adCount++;
                    currentDuration = 0.0;
                    inAd = false;
                }
                continue;
            }

            if (line.endsWith(".ts") || line.endsWith(".jpeg") || line.endsWith(".jpg")) {
                // === 📊 LOG 點 3：TS 檔案處理階段 ===
                boolean isAdOld = isAdUrl(line);
                boolean isAdSmart = isAdUrlSmart(line, mainFeature);

                if (isAdSmart || isAdOld) {
                    if (!inAd) {
                        Log.d("M3U8Parser", "偵測到廣告網址，切換 inAd = true. 網址: " + line);
                    }
                    inAd = true;
                }


                Long currentNumber = extractSegmentNumber(line);
                if (!inAd) {
                    if (pendingExtInfLine != null) output.append(pendingExtInfLine).append("\n");
                    output.append(line).append("\n");
                    lastSegmentNumber = currentNumber;
                }
                pendingExtInfLine = null;
                continue;
            } else {
                // 如果在廣告狀態中，記錄過濾掉的網址
                Log.d("M3U8Parser", "成功過濾廣告片段: " + line);
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
        // 計算廣告佔比，看是否會觸發您的「直接丟棄整部影片 (-1)」邏輯
        double adRatio = adDuration / (totalDuration > 0 ? totalDuration : 1.0);
        Log.d("M3U8Parser", "=== 過濾總結 ===");
        Log.d("M3U8Parser", "總時長: " + totalDuration + ", 廣告總時長: " + adDuration + ", 廣告次數: " + adCount + ", 廣告佔比: " + adRatio);

        M3U8AdFilterResult result;
        if (adDuration / (totalDuration > 0 ? totalDuration : 1.0) > 0.1) {
            Log.w("M3U8Parser", "警告：廣告佔比超過 10%，回傳原內容並丟棄影片 (-1)");
            result = new M3U8AdFilterResult(rawContent, -1, 0.0);
        } else {
            Log.d("M3U8Parser", "成功輸出過濾後的 M3U8，剩餘行數估算: " + output.toString().split("\n").length);
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

        // 1. 清除所有可能的 Unicode 隱形空白、換行與前後空格
        String cleanUrl = url.replaceAll("[\\s\\u200B\\u00A0]+", "").trim();

        // 2. 去除末端可能干擾的斜線
        if (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }

        // 3. 尋找最後一個斜線的位置
        int lastSlashIdx = cleanUrl.lastIndexOf('/');
        if (lastSlashIdx > 0) {
            String feature = cleanUrl.substring(0, lastSlashIdx);

            // 防呆：如果去掉協定標頭後，特徵太短或根本沒有目錄層級，就回傳空（不參與統計）
            String checkStr = feature.replace("https://", "").replace("http://", "");
            if (checkStr.length() < 3 || !checkStr.contains("/")) {
                return "";
            }
            return feature;
        }
        return ""; // 沒有斜線代表只是單純的檔名，不參與統計
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
