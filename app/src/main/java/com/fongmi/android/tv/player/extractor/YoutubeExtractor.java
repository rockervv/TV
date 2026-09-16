package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import androidx.media3.common.MimeTypes;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.spider.SmartTube;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class YoutubeExtractor implements Source.Extractor {

    private SmartTube smartTube;

    public YoutubeExtractor() {
    }

    private SmartTube getSmartTube() {
        if (smartTube == null) {
            smartTube = new SmartTube();
            smartTube.init(App.get(), "");
        }
        return smartTube;
    }

    @Override
    public boolean match(Uri uri) {
        String scheme = UrlUtil.scheme(uri);
        String host = UrlUtil.host(uri);
        return scheme.equals("youtube") || host.contains("youtube.com") || host.contains("youtu.be");
    }

    @Override
    public String fetch(Result result) throws Exception {
        String url = result.getUrl().v();
        Uri uri = Uri.parse(url);
        String videoId = uri.getQueryParameter("v");
        if (videoId == null && url.contains("youtube.com/embed/")) videoId = url.split("embed/")[1].split("\\?")[0];
        if (videoId == null && url.contains("youtube.com/live/")) videoId = url.split("live/")[1].split("\\?")[0];
        if (videoId == null && uri.getHost() != null && uri.getHost().contains("youtu.be")) videoId = uri.getLastPathSegment();
        if (videoId == null && "youtube".equals(uri.getScheme())) videoId = uri.getHost();
        if (videoId == null) return "";

        android.util.Log.d("YoutubeExtractor", ">>> [fetch] videoId: " + videoId);

        try {
            String smartTubeResult = getSmartTube().playerContent("", videoId, null);
            Result temp = Result.objectFrom(smartTubeResult);
            // 🛠️ 強化成功判定：接受包含 googlevideo, manifest, videoplayback 或我們內部的 local dash proxy 網址
            if (temp != null && !temp.getRealUrl().isEmpty() && (temp.getRealUrl().contains("googlevideo.com") || temp.getRealUrl().contains("manifest") || temp.getRealUrl().contains("videoplayback") || temp.getRealUrl().contains("127.0.0.1:9978") || temp.getRealUrl().contains("dash?id="))) {
                android.util.Log.d("YoutubeExtractor", ">>> SmartTube Success! URL: " + temp.getRealUrl());
                
                if (temp.getFormat() != null) {
                    result.setFormat(temp.getFormat());
                } else if (temp.getRealUrl().contains("dash") || temp.getRealUrl().contains(".mpd")) {
                    result.setFormat(MimeTypes.APPLICATION_MPD);
                } else if (temp.getRealUrl().contains("hls") || temp.getRealUrl().contains(".m3u8")) {
                    result.setFormat(MimeTypes.APPLICATION_M3U8);
                }
                
                result.getHeader().putAll(temp.getHeader());
                return temp.getRealUrl();
            }
        } catch (Exception e) {
            android.util.Log.e("YoutubeExtractor", ">>> SmartTube Error: " + e.getMessage());
        }

        // --- THE RELIABLE SAFARI EXTRACTOR FALLBACK ---
        android.util.Log.w("YoutubeExtractor", ">>> SmartTube Failed or Blocked. Triggering Safari HLS Extraction for " + videoId);
        result.setParse(1); 
        // 使用 Safari UA。這會讓 YouTube 輸出最純淨的 HLS 清單，避開廣告片段
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15";
        result.getHeader().put("User-Agent", ua);
        result.getHeader().put("Referer", "https://www.youtube.com/");
        return "https://www.youtube.com/watch?v=" + videoId;
    }

    @Override public String fetch(String url) throws Exception {
        Result result = new Result();
        result.setUrl(url);
        return fetch(result);
    }

    @Override public void stop() {}
    @Override public void exit() {}

    public static class Parser implements Callable<List<Episode>> {
        private final String url;
        public Parser(String url) { this.url = url; }
        public static boolean match(String url) { return url.contains("/playlist") || url.contains("&list=") || url.contains("/channel/"); }
        public static Parser get(String url) { return new Parser(url); }
        @Override public List<Episode> call() {
            List<Episode> items = new ArrayList<>();
            // TODO: Implement playlist parsing via SmartTube if needed
            return items;
        }
    }
}
