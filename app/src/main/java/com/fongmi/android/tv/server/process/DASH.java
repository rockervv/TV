package com.fongmi.android.tv.server.process;

import android.net.Uri;
import android.util.Log;

import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.Server;
import com.github.catvod.net.OkHttp;
import com.liskovsoft.mediaserviceinterfaces.data.MediaFormat;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.youtubeapi.app.AppService;
import com.liskovsoft.youtubeapi.service.YouTubeMediaItemService;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoHTTPD;
import okhttp3.Response;

public class DASH implements Process {
    private static final Map<String, Long> lastRotateMap = new ConcurrentHashMap<>();

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String url) {
        return url.startsWith("/dash");
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String url, Map<String, String> files) {
        List<String> idParams = session.getParameters().get("id");
        String videoId = idParams != null && !idParams.isEmpty() ? idParams.get(0) : null;
        if (videoId == null) return Nano.error(NanoHTTPD.Response.Status.BAD_REQUEST, "Missing Video ID");

        if (url.endsWith("/segment")) {
            return proxySegment(session, videoId);
        }

        // 🛠️ 強化 MPD 獲取邏輯：如果獲取失敗，嘗試清除快取並重試一次，確保 manifest 也是最新的
        for (int retry = 0; retry < 2; retry++) {
            try {
                MediaItemFormatInfo formatInfo = YouTubeMediaItemService.instance().getFormatInfo(videoId);
                if (formatInfo == null) throw new Exception("Video Info not found");

                try (InputStream mpdStream = formatInfo.createMpdStream()) {
                    if (mpdStream == null) throw new Exception("Failed to create MPD stream");

                    String mpdXml = readStream(mpdStream);
                    if (mpdXml.isEmpty()) throw new Exception("Empty MPD stream");

                    // 🛡️ 檢查是否為有效的 XML，避免回傳 M3U8 或錯誤訊息導致 3002 錯誤
                    if (!mpdXml.trim().startsWith("<")) throw new Exception("Manifest is not XML (possibly M3U8 or Error)");

                    String rewrittenMpd = rewriteMpd(mpdXml, videoId, formatInfo.getPoToken());
                    return NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/dash+xml", rewrittenMpd);
                }
            } catch (Exception e) {
                Log.w("DASH", ">>> [MPD Fetch] Attempt " + (retry + 1) + " failed for " + videoId + ": " + e.getMessage());
                YouTubeServiceManager.instance().invalidateCache();
                if (retry == 1) {
                    Log.e("DASH", ">>> [MPD Fetch] All attempts failed for " + videoId);
                    return Nano.error(NanoHTTPD.Response.Status.INTERNAL_ERROR, "DASH Process error: " + e.getMessage());
                }
            }
        }
        return Nano.error(NanoHTTPD.Response.Status.INTERNAL_ERROR, "DASH Process error: Unknown");
    }

    private String readStream(InputStream is) {
        try (java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String rewriteMpd(String xml, String videoId, String poToken) {
        String proxyBase = Server.get().getAddress("/dash/segment?id=" + videoId);
        // 🛠️ 獲取當前 Format 的 User-Agent，以便嵌入 URL 避免 Header 不匹配
        String ua = null;
        try {
            MediaItemFormatInfo formatInfo = YouTubeMediaItemService.instance().getFormatInfo(videoId);
            if (formatInfo != null && formatInfo.getClientInfo() != null) {
                ua = formatInfo.getClientInfo().getUserAgent();
            }
        } catch (Exception ignored) {}

        Pattern p = Pattern.compile("(https?://[^\"<>\\s]*googlevideo\\.com/videoplayback\\?[^\"<>\\s]*)");
        Matcher m = p.matcher(xml);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            try {
                String originalUrl = m.group(1);
                if (originalUrl == null) continue;
                
                String unescapedUrl = originalUrl.replace("&amp;", "&");
                
                if (poToken != null && !unescapedUrl.contains("pot=")) {
                    unescapedUrl += "&pot=" + poToken;
                }

                String itag = Uri.parse(unescapedUrl).getQueryParameter("itag");
                
                String rewritten = proxyBase + "&amp;itag=" + (itag != null ? itag : "") + "&amp;url=" + URLEncoder.encode(unescapedUrl, StandardCharsets.UTF_8.name());
                // 🛠️ 關鍵：將對應客戶端的 UA 傳遞給 Proxy
                if (ua != null) {
                    rewritten += "&amp;ua=" + URLEncoder.encode(ua, StandardCharsets.UTF_8.name());
                }
                rewritten = rewritten.replace("%24Number%24", "$Number$").replace("%24Time%24", "$Time$");

                m.appendReplacement(sb, Matcher.quoteReplacement(rewritten));
            } catch (Exception e) {
                String group = m.group(1);
                m.appendReplacement(sb, group != null ? Matcher.quoteReplacement(group) : "");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private NanoHTTPD.Response proxySegment(NanoHTTPD.IHTTPSession session, String videoId) {
        Map<String, List<String>> params = session.getParameters();
        List<String> urlParams = params.get("url");
        List<String> uaParams = params.get("ua");
        List<String> itagParams = params.get("itag");
        String url = urlParams != null && !urlParams.isEmpty() ? urlParams.get(0) : null;
        String embeddedUa = uaParams != null && !uaParams.isEmpty() ? uaParams.get(0) : null;
        String itag = itagParams != null && !itagParams.isEmpty() ? itagParams.get(0) : null;

        if (url == null) return Nano.error("Missing URL parameter");

        Uri.Builder builder = Uri.parse(url).buildUpon();
        for (Map.Entry<String, List<String>> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key.equals("id") || key.equals("itag") || key.equals("url") || key.equals("ua")) continue;
            List<String> values = entry.getValue();
            if (values == null || values.isEmpty()) continue;
            if (builder.build().getQueryParameter(key) == null) {
                builder.appendQueryParameter(key, values.get(0));
            }
        }
        String finalUrl = builder.build().toString();

        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, String> entry : session.getHeaders().entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.startsWith("x-goog-") || key.equals("range") || key.equals("referer") || key.equals("origin") || key.equals("accept")) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }
        
        // 🛠️ 優先使用 URL 帶過來的 User-Agent，確保與 c=XXXX 匹配
        if (embeddedUa != null) {
            headers.put("User-Agent", embeddedUa);
        } else if (session.getHeaders().containsKey("user-agent")) {
            headers.put("User-Agent", session.getHeaders().get("user-agent"));
        }

        // 🛡️ 關鍵：同步 Visitor ID 到片段請求中，防止 403 權限鎖死
        String visitorData = AppService.instance().getVisitorData();
        if (visitorData != null && !headers.containsKey("X-Goog-Visitor-Id")) {
            headers.put("X-Goog-Visitor-Id", visitorData);
        }

        return fetchWithRetry(finalUrl, videoId, itag, headers, 0);
    }

    private NanoHTTPD.Response fetchWithRetry(String url, String videoId, String itag, Map<String, String> headers, int depth) {
        if (depth > 2) return Nano.error(NanoHTTPD.Response.Status.GONE, "Max retry depth exceeded");
        try {
            Response res = OkHttp.newCall(url, headers).execute();
            if (res.code() == 403 || res.code() == 410) {
                Log.w("DASH", ">>> [Smart Proxy] " + res.code() + " detected on segment. Attempting to auto-heal for " + videoId + " (itag=" + itag + ")");
                res.close();
                
                // 🛠️ 防止 Rotation Storm：如果 5 秒內已經有人輪轉過，則不要再次觸發昂貴的輪轉
                long now = System.currentTimeMillis();
                Long lastRotate = lastRotateMap.get(videoId);
                if (lastRotate == null || (now - lastRotate) > 5000) {
                    lastRotateMap.put(videoId, now);
                    YouTubeServiceManager.instance().switchNextClient();
                } else {
                    YouTubeMediaItemService.instance().invalidateCache();
                }
                
                MediaItemFormatInfo freshInfo = YouTubeMediaItemService.instance().getFormatInfo(videoId);
                
                // 🛡️ 關鍵：同步更新 Visitor ID，防止指紋不匹配。優先使用 player 響應返回的最新指紋。
                String freshVisitorData = (freshInfo != null && freshInfo.getVisitorData() != null) ? freshInfo.getVisitorData() : AppService.instance().getVisitorData();
                if (freshVisitorData != null) {
                    headers.put("X-Goog-Visitor-Id", freshVisitorData);
                }

                String newUrl = findNewUrl(freshInfo, itag, url);
                
                if (newUrl != null) {
                    Log.d("DASH", ">>> [Smart Proxy] Auto-heal success at depth " + depth + ". Retrying with fresh URL.");
                    // 🛡️ 同步更新 User-Agent，防止客戶端切換後 UA 不匹配
                    if (freshInfo != null && freshInfo.getClientInfo() != null) {
                        headers.put("User-Agent", freshInfo.getClientInfo().getUserAgent());
                    }
                    return fetchWithRetry(newUrl, videoId, itag, headers, depth + 1);
                }

                // 🛡️ 如果 Auto-Heal 失敗（可能是 freshInfo 依然沒有 URL），強制最後一次輪轉
                if (depth == 0) {
                    Log.w("DASH", ">>> [Smart Proxy] First auto-heal yielded no URL. Forcing rotation and final retry...");
                    YouTubeServiceManager.instance().switchNextClient();
                    freshInfo = YouTubeMediaItemService.instance().getFormatInfo(videoId);
                    newUrl = findNewUrl(freshInfo, itag, url);
                    if (newUrl != null) {
                        return fetchWithRetry(newUrl, videoId, itag, headers, depth + 1);
                    }
                }

                return Nano.error(NanoHTTPD.Response.Status.GONE, "URL Expired");
            }

            NanoHTTPD.Response.IStatus status = NanoHTTPD.Response.Status.lookup(res.code());
            if (status == null) status = NanoHTTPD.Response.Status.OK;

            if (res.body() == null) return Nano.error("Empty response body");

            InputStream is = res.body().byteStream();
            String contentType = res.header("Content-Type", "video/mp4");
            long contentLength = res.body().contentLength();

            NanoHTTPD.Response nanoRes = NanoHTTPD.newFixedLengthResponse(status, contentType, is, contentLength);
            for (String headerName : res.headers().names()) {
                if (headerName.equalsIgnoreCase("Content-Type") || headerName.equalsIgnoreCase("Content-Length") || headerName.equalsIgnoreCase("Transfer-Encoding")) continue;
                nanoRes.addHeader(headerName, res.header(headerName));
            }
            return nanoRes;
        } catch (Exception e) {
            Log.e("DASH", "Proxy fetch failed: " + e.getMessage());
            return Nano.error("Segment proxy failed for itag " + itag + ": " + e.getMessage());
        }
    }

    private String findNewUrl(MediaItemFormatInfo info, String itag, String oldUrl) {
        if (info == null || info.getAdaptiveFormats() == null) {
            Log.e("DASH", "findNewUrl: info or formats is null for " + itag);
            return null;
        }
        for (MediaFormat f : info.getAdaptiveFormats()) {
            if (itag.equals(f.getITag())) {
                String newBase = f.getUrl();
                if (newBase == null || newBase.isEmpty()) {
                    Log.e("DASH", "findNewUrl: itag " + itag + " found but URL is empty! (Possibly signature/cipher failed)");
                    return null;
                }
                Uri oldUri = Uri.parse(oldUrl);
                String sq = oldUri.getQueryParameter("sq");
                String range = oldUri.getQueryParameter("range");

                Uri.Builder builder = Uri.parse(newBase).buildUpon();
                if (sq != null) builder.appendQueryParameter("sq", sq);
                if (range != null) builder.appendQueryParameter("range", range);

                return builder.build().toString();
            }
        }
        Log.w("DASH", "findNewUrl: itag " + itag + " not found in fresh formats for " + info.getAdaptiveFormats().size() + " items");
        return null;
    }
}
