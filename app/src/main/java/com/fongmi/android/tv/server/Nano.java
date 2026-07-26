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

    private static final String INDEX = "index.html";

    private List<Process> process;

    public Nano(int port) {
        super(port);
        addProcess();
    }

    private void addProcess() {
        process = new ArrayList<>();
        process.add(new Action());
        process.add(new Cache());
        process.add(new M3U8());
        process.add(new TS());
        process.add(new Local());
        process.add(new Media());
        process.add(new Parse());
        process.add(new Proxy());
    }

    public static Response ok() {
        return ok("OK");
    }

    public static Response ok(String text) {
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
    protected boolean useGzipWhenAccepted(Response r) {
        return false;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String url = session.getUri().trim();
        String query = session.getQueryParameterString();
        String fullPath = url + (query != null ? "?" + query : "");
        
        Map<String, String> files = new HashMap<>();
        if (session.getMethod() == Method.POST) parse(session, files);
        if (url.contains("?")) url = url.substring(0, url.indexOf('?'));

        if (url.startsWith("/tvbus")) return ok(LiveConfig.getResp());
        if (url.startsWith("/device")) return ok(Device.get().toString());

        Response response = null;
        for (Process process : process) {
            if (process.isRequest(session, url)) {
                response = process.doResponse(session, url, files);
                break;
            }
        }

        if (response == null && url.startsWith("/index.html")) {
            response = getAssets(url.substring(1));
        }

        if (response == null) {
            // 只允許以 http 開頭的 URL 進入 doProxy，其他的視為無效請求並 Log
            if (url.startsWith("/http") || url.startsWith("http")) {
                android.util.Log.d("NanoHTTPD", "🌐 Fallback to Proxy: " + fullPath);
                response = doProxy(session);
            } else {
                android.util.Log.e("NanoHTTPD", "❌ Invalid/Malformed Request Blocked: " + fullPath);
                response = error(Response.Status.NOT_FOUND, "Invalid request path: " + url);
            }
        }

        // 強制關閉 GZIP，防止播放器在探測時因 Stream 關閉導致 Broken Pipe
        if (response != null) response.setGzipEncoding(false);
        return response;
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
        String urlPath = session.getUri();
        // 增加更嚴謹的截斷檢查，防止出現像 /3U 這樣的錯誤路徑
        String fullUrl = urlPath.startsWith("/") ? urlPath.substring(1) : urlPath; 
        
        if (!fullUrl.startsWith("http")) {
            android.util.Log.w("NanoHTTPD", "⚠️ Unhandled Request Type (Skipping Proxy): " + urlPath);
            return error(Response.Status.NOT_FOUND, "Not a proxyable path: " + urlPath);
        }

        try {
            android.util.Log.d("NanoHTTPD", "🌐 Proxying Remote URL: " + fullUrl);
            URL url = new URL(fullUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            InputStream is = conn.getInputStream();
            String mime = conn.getContentType();
            int length = conn.getContentLength();

            // 如果長度未知，NanoHTTPD 會自動處理成 Chunked 傳輸
            return NanoHTTPD.newFixedLengthResponse(Response.Status.OK, mime, is, length);
        } catch (Exception e) {
            android.util.Log.e("NanoHTTPD", "🔥 Proxy Backend Connection Failed: " + e.getMessage());
            return error("Proxy Error: " + e.getMessage());
        }
    }

    private Response getAssets(String path) {
        try {
            if (path.isEmpty()) path = INDEX;
            InputStream is = Asset.open(path);
            return newFixedLengthResponse(Response.Status.OK, getMimeTypeForFile(path), is, is.available());
        } catch (Exception e) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_HTML, null);
        }
    }

}
