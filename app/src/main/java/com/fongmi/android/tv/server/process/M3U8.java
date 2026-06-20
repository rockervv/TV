package com.fongmi.android.tv.server.process;

import androidx.media3.common.util.Log;

import com.fongmi.android.tv.player.ADFilter;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.net.OkHttp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Headers;
import okhttp3.Response;

public class M3U8 implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String url) {
        return url.startsWith("/m3u8");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String url, Map<String, String> files) {
        String targetUrl = session.getParms().get("url");
        if (targetUrl == null) return Nano.error("Missing URL");

        String proxyUrlPrefix = Server.get().getAddress("/m3u8?url=");

        try {
            Headers.Builder headersBuilder = new Headers.Builder();
            for (Map.Entry<String, String> entry : session.getHeaders().entrySet()) {
                String key = entry.getKey();
                if (key.equalsIgnoreCase("host") || key.equalsIgnoreCase("connection") || key.equalsIgnoreCase("remote-addr")) continue;
                headersBuilder.add(key, entry.getValue());
            }

            try (Response response = OkHttp.newCall(targetUrl, headersBuilder.build()).execute()) {
                if (!response.isSuccessful()) return Nano.error("HTTP " + response.code());

                BufferedReader reader = new BufferedReader(new InputStreamReader(response.body().byteStream()));
                String filtered = ADFilter.Process(reader);

                StringBuilder result = new StringBuilder();
                URL baseUrl = new URL(response.request().url().toString());
                String[] lines = filtered.split("\\n");

                for (String content : lines) {
                    content = content.trim();
                    if (content.isEmpty()) {
                        result.append("\n");
                        continue;
                    }

                    if (content.startsWith("#")) {
                        if (content.contains("URI=\"")) {
                            content = resolveTagUri(content, baseUrl);
                        }
                        result.append(content).append("\n");
                    } else {
                        String resolvedUrl = new URL(baseUrl, content).toString();
                        if (resolvedUrl.toLowerCase().contains(".m3u8") && !resolvedUrl.startsWith(proxyUrlPrefix)) {
                            result.append(proxyUrlPrefix).append(URLEncoder.encode(resolvedUrl, "UTF-8")).append("&.m3u8\n");
                        } else {
                            result.append(resolvedUrl).append("\n");
                        }
                    }
                }

                if (result.length() == 0) return Nano.error("Filtered m3u8 is empty");
                return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/x-mpegURL", result.toString());
            }

        } catch (Exception e) {
            Log.e("M3U8Proxy", "Error: " + e.getMessage());
            return Nano.error("Proxy failed: " + e.getMessage());
        }
    }

    private String resolveTagUri(String line, URL baseUrl) {
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
}
