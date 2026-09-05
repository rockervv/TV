package com.fongmi.android.tv.server.process;

import android.util.LruCache;

import androidx.media3.common.util.Log;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.util.ADFilter;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.net.OkHttp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Headers;
import okhttp3.Response;

public class M3U8 implements Process {

    private static final LruCache<String, CacheItem> urlCache = new LruCache<>(10);
    private static final long CACHE_TIME = 10 * 60 * 1000; // 10 分鐘快取

    private static class CacheItem {
        String content;
        long time;

        CacheItem(String content) {
            this.content = content;
            this.time = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - time > CACHE_TIME;
        }
    }

    public static String getCache(String url) {
        CacheItem item = urlCache.get(url);
        return item != null ? item.content : "";
    }

    public static String fetch(String targetUrl, Map<String, String> headers) {
        CacheItem cached = urlCache.get(targetUrl);
        if (cached != null && !cached.isExpired()) {
            Log.d("M3U8", "Cache Hit: " + targetUrl);
            return cached.content;
        }
        Log.d("M3U8", "Fetch M3U8: " + targetUrl);
        try {
            Headers.Builder headersBuilder = new Headers.Builder();
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    headersBuilder.add(entry.getKey(), entry.getValue());
                }
            }
            if (headersBuilder.build().get("User-Agent") == null) {
                headersBuilder.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            }
            try (Response response = OkHttp.newCall(targetUrl, headersBuilder.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) return "";
                BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line).append("\n");
                String raw = sb.toString().trim();

                String filtered;
                String sourceId = Server.get().getPlayer() != null ? Server.get().getPlayer().getKey() : "";
                boolean isVod = isVod(targetUrl, raw);
                if (Setting.isAdblockLive() || isVod) {
                    filtered = ADFilter.Process(targetUrl, raw, sourceId).trim();
                } else {
                    Log.d("ADFilter", "Bypass ADFilter: Not VOD and Live Adblock is OFF");
                    filtered = raw;
                }

                if (filtered.startsWith("\uFEFF")) filtered = filtered.substring(1);

                // Add ENDLIST back if it was in the original and lost or if it's a VOD
                if (!filtered.contains("#EXT-X-ENDLIST") && raw.contains("#EXT-X-ENDLIST")) {
                    filtered = filtered + "\n#EXT-X-ENDLIST\n";
                }

                StringBuilder result = new StringBuilder();
                URL baseUrl = new URL(response.request().url().toString());
                String[] filteredLines = filtered.split("\\n");
                String proxyUrlPrefix = Server.get().getAddress("/m3u8?url=");
                for (String fLine : filteredLines) {
                    fLine = fLine.trim();
                    if (fLine.isEmpty()) {
                        result.append("\n");
                        continue;
                    }
                    if (fLine.startsWith("#")) {
                        if (fLine.contains("URI=\"")) {
                            fLine = resolveTagUri(fLine, baseUrl);
                        }
                        result.append(fLine).append("\n");
                    } else {
                        String resolvedUrl = new URL(baseUrl, fLine).toString();
                        if (resolvedUrl.toLowerCase().contains(".m3u8") && !resolvedUrl.startsWith(proxyUrlPrefix)) {
                            result.append(proxyUrlPrefix).append(URLEncoder.encode(resolvedUrl, "UTF-8")).append("&.m3u8\n");
                        } else {
                            result.append(resolvedUrl).append("\n");
                        }
                    }
                }
                String finalM3u8 = result.toString().trim();
                if (!finalM3u8.startsWith("#EXTM3U")) {
                    if (finalM3u8.contains("#EXTM3U")) {
                        finalM3u8 = finalM3u8.substring(finalM3u8.indexOf("#EXTM3U"));
                    } else {
                        finalM3u8 = "#EXTM3U\n" + finalM3u8;
                    }
                }
                // Only cache if it's a complete VOD
                if (raw.contains("#EXT-X-ENDLIST")) {
                    urlCache.put(targetUrl, new CacheItem(finalM3u8));
                }
                return finalM3u8;
            }
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String url) {
        return url.startsWith("/m3u8");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String url, Map<String, String> files) {
        String targetUrl = session.getParms().get("url");
        if (targetUrl == null) return Nano.error("Missing URL");

        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : session.getHeaders().entrySet()) {
            String key = entry.getKey();
            if (key.equalsIgnoreCase("host") || key.equalsIgnoreCase("connection") || key.equalsIgnoreCase("remote-addr")) continue;
            headers.put(key, entry.getValue());
        }

        PlayerManager player = Server.get().getPlayer();
        Map<String, String> spiderHeaders = player != null ? player.getHeaders() : null;
        if (spiderHeaders != null) headers.putAll(spiderHeaders);

        String finalM3u8 = fetch(targetUrl, headers);
        if (finalM3u8.isEmpty()) return Nano.error("Fetch failed");

        return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/vnd.apple.mpegurl", finalM3u8);
    }

    private static String resolveTagUri(String line, URL baseUrl) {
        try {
            int start = line.indexOf("URI=\"") + 5;
            int end = line.indexOf("\"", start);
            String uri = line.substring(start, end);
            String resolved = new URL(baseUrl, uri).toString();
            return line.substring(0, start) + resolved + line.substring(end);
        } catch (Exception e) {
            return line;
        }
    }

    private static boolean isVod(String url, String content) {
        String lowerUrl = url.toLowerCase();
        if (lowerUrl.contains("live")) return false; // 排除明確的直播
        if (content.contains("#EXT-X-PLAYLIST-TYPE:VOD")) return true;
        if (lowerUrl.contains("vod") || lowerUrl.contains("video") || lowerUrl.contains("movie")) return true;

        // 啟發式判定：如果分片數量 > 10，極大機率是點播影片而非直播視窗
        int count = 0;
        int index = 0;
        while ((index = content.indexOf("#EXTINF:", index)) != -1) {
            count++;
            index += 8;
            if (count > 10) return true;
        }
        return false;
    }
}
