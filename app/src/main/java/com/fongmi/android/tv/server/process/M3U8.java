package com.fongmi.android.tv.server.process;
import androidx.media3.common.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.ADFilter;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.utils.Asset;

import java.io.*;
import java.net.*;
import java.util.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;


import fi.iki.elonen.NanoHTTPD;

public class M3U8 implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String url) {
        return url.startsWith("/m3u8");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String url, Map<String, String> files) {
        //String proxyurl = "http://127.0.0.1:9978/segment?url=";
        String proxyurl = "http://127.0.0.1:9978/m3u8?url=";
        String targetUrl = session.getParms().get("url");
        String baseUri = targetUrl.substring(0, targetUrl.lastIndexOf("/") + 1);
        if (targetUrl == null) return Nano.error("Missing URL");

        try {
            // 下載 m3u8 原始內容
            URL u = new URL(targetUrl);
            BufferedReader reader = new BufferedReader(new InputStreamReader(u.openStream()));

            String filtered = ADFilter.Process(reader);
            StringBuilder result = new StringBuilder();
            Scanner scanner = new Scanner (filtered);

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    result.append(line).append("\n");
                } else {
                    // 是 ts 段 → 包裝成 /segment 代理
                    if (line.startsWith("https://") || line.startsWith("http://")){
                        result.append(line).append("\n");
                    }
                    else {
                        String fullTsUrl;
                        if (line.endsWith(".m3u8") && !line.startsWith(proxyurl)) {
                            fullTsUrl = proxyurl + URLEncoder.encode(baseUri + line, "UTF-8");
                        }
                        else fullTsUrl = baseUri + line;

                        result.append(fullTsUrl).append("\n");
                        //result.append(proxyTsUrl).append("\n");
                    }

                }
            }

            //return Nano.success(result.toString());
            Log.d("M3U8", targetUrl + "\n" + result.toString().substring(0, result.length() > 1024 ? 1024 : result.length()));


            // 回傳修改後的 m3u8 內容
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/vnd.apple.mpegurl", result.toString());

        } catch (IOException e) {
            return Nano.error("Parse failed: " + e.getMessage());
        }
    }
}

