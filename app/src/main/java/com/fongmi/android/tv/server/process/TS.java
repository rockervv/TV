package com.fongmi.android.tv.server.process;
import com.fongmi.android.tv.server.Nano;

import fi.iki.elonen.NanoHTTPD;

import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Map;
import java.net.*;

public class TS implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String url) {
        return url.startsWith("/segment");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String url, Map<String, String> files) {
        String tsUrl = session.getParms().get("url");
        try {

            HttpURLConnection conn = (HttpURLConnection) new URL(tsUrl).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");


            //String tsPath = URLDecoder.decode(session.getParms().get("ts"), "UTF-8");
            //String base = URLDecoder.decode(session.getParms().get("base"), "UTF-8");

            // 拼接完整 ts URL
            //URL fullUrl = new URL(new URL(base), tsPath);
            //InputStream is = fullUrl.openStream();

            InputStream is = conn.getInputStream();
            int length = conn.getContentLength();
            return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "video/mp2t", is, length);



            //return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "video/mp2t", is, is.available());

        } catch (IOException e) {
            return Nano.error("Segment fetch failed: " + e.getMessage());
        }
    }
}