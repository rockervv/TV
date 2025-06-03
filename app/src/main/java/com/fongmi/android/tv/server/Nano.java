package com.fongmi.android.tv.server;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.server.process.TS;
import com.fongmi.android.tv.server.process.M3U8;
import com.fongmi.android.tv.server.process.Action;
import com.fongmi.android.tv.server.process.Cache;
import com.fongmi.android.tv.server.process.Local;
import com.fongmi.android.tv.server.process.Media;
import com.fongmi.android.tv.server.process.Parse;
import com.fongmi.android.tv.server.process.Process;
import com.fongmi.android.tv.server.process.Proxy;
import com.github.catvod.utils.Asset;
import com.google.common.net.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.*;

import fi.iki.elonen.NanoHTTPD;

public class Nano extends NanoHTTPD {

    private List<Process> process;

    public Nano(int port) {
        super(port);
        addProcess();
    }

    private void addProcess() {
        process = new ArrayList<Process>();
        process.add(new Action());
        process.add(new Cache());
        process.add(new M3U8());
        process.add(new TS());
        process.add(new Cache());
        process.add(new Local());
        process.add(new Media());
        process.add(new Parse());
        process.add(new Proxy());
    }

    public static Response success() {
        return success("OK");
    }

    public static Response success(String text) {
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, text);
    }

    public static Response error(String text) {
        return error(Response.Status.INTERNAL_ERROR, text);
    }

    public static Response error(Response.IStatus status, String text) {
        return newFixedLengthResponse(status, MIME_PLAINTEXT, text);
    }

    public static Response redirect(String url, Map<String, String> headers) {
        Response response = newFixedLengthResponse(Response.Status.REDIRECT, MIME_HTML, "");
        for (Map.Entry<String, String> entry : headers.entrySet()) response.addHeader(entry.getKey(), entry.getValue());
        response.addHeader(HttpHeaders.LOCATION, url);
        return response;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String url = session.getUri().trim();
        Map<String, String> files = new HashMap<>();
        if (session.getMethod() == Method.POST) parse(session, files);
        if (url.contains("?")) url = url.substring(0, url.indexOf('?'));
        if (url.startsWith("/go")) return go();
        if (url.startsWith("/tvbus")) return success(LiveConfig.getResp());
        if (url.startsWith("/device")) return success(Device.get().toString());
        for (Process process : process) if (process.isRequest(session, url)) return process.doResponse(session, url, files);
        if (url.startsWith("/index.html"))
            return getAssets(url.substring(1));
        return doProxy(session);  // fallback 成為普通 HTTP proxy
    }

    private void parse(IHTTPSession session, Map<String, String> files) {
        String ct = session.getHeaders().get("content-type");
        if (ct != null && ct.toLowerCase().contains("multipart/form-data") && !ct.toLowerCase().contains("charset=")) {
            Matcher matcher = Pattern.compile("[ |\t]*(boundary[ |\t]*=[ |\t]*['|\"]?[^\"^'^;^,]*['|\"]?)", Pattern.CASE_INSENSITIVE).matcher(ct);
            String boundary = matcher.find() ? matcher.group(1) : null;
            if (boundary != null) session.getHeaders().put("content-type", "multipart/form-data; charset=utf-8; " + boundary);
        }
        try {
            session.parseBody(files);
        } catch (Exception ignored) {
        }
    }

    private Response doProxy(IHTTPSession session) {
        String fullUrl = session.getUri().substring(1); // 把 `/https://xxx` 拿掉開頭的 `/`
        if (!fullUrl.startsWith("http")) {
            return error("Invalid proxy URL: " + fullUrl);
        }

        try {
            URL url = new URL(fullUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            InputStream is = conn.getInputStream();
            String mime = conn.getContentType();
            int length = conn.getContentLength();

            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime, is, length);
        } catch (Exception e) {
            return error("Proxy Error: " + e.getMessage());
        }
    }


    private Response go() {
        Go.start();
        return success();
    }

    private Response getAssets(String path) {
        try {
            if (path.isEmpty()) path = "index.html";
            InputStream is = Asset.open(path);
            return newFixedLengthResponse(Response.Status.OK, getMimeTypeForFile(path), is, is.available());
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, null);
        }
    }

    @Override
    public void start() throws IOException {
        super.start();
        Go.start();
    }

    @Override
    public void stop() {
        super.stop();
        Go.stop();
    }
}
