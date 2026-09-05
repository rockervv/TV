package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import androidx.media3.common.MimeTypes;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.spider.SmartTube;
import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.request.RequestPlaylistInfo;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.playlist.PlaylistInfo;
import com.github.kiulian.downloader.model.playlist.PlaylistVideoDetails;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.Format;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

public class Youtube implements Source.Extractor {

    private final YoutubeDownloader downloader;
    private SmartTube smartTube;

    public Youtube() {
        this.downloader = new YoutubeDownloader();
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

        android.util.Log.d("Youtube", ">>> [fetch] videoId: " + videoId);

        try {
            String smartTubeResult = getSmartTube().playerContent("", videoId, null);
            Result temp = Result.objectFrom(smartTubeResult);
            if (temp != null && !temp.getRealUrl().isEmpty()) {
                android.util.Log.d("Youtube", ">>> SmartTube Success! URL: " + temp.getRealUrl());
                android.util.Log.d("Youtube", ">>> SmartTube Headers: " + temp.getHeader());
                android.util.Log.d("Youtube", ">>> SmartTube Format: " + temp.getFormat());
                
                // 🛠️ 關鍵修正：確保 format 被正確設置
                // 如果是 DASH，必須設置對應的 MimeType，否則會被內部的 M3U8 代理攔截
                if (temp.getFormat() != null) {
                    result.setFormat(temp.getFormat());
                } else if (temp.getRealUrl().contains("dash")) {
                    result.setFormat(MimeTypes.APPLICATION_MPD);
                }
                
                result.getHeader().putAll(temp.getHeader());
                return temp.getRealUrl();
            }
        } catch (Exception e) {
            android.util.Log.e("Youtube", ">>> SmartTube Error: " + e.getMessage());
        }

        VideoInfo video = null;
        try {
            // Step 1: Try IOS (Most stable for m3u8)
            Response<VideoInfo> response = downloader.getVideoInfo(new RequestVideoInfo(videoId).clientType(com.github.kiulian.downloader.downloader.client.ClientType.IOS));
            video = response.data();

            // Step 2: Try TVHTML5
            if (video == null || video.details().liveUrl() == null) {
                response = downloader.getVideoInfo(new RequestVideoInfo(videoId).clientType(com.github.kiulian.downloader.downloader.client.ClientType.TVHTML5));
                video = response.data();
            }
        } catch (Exception e) {
            android.util.Log.e("Youtube", ">>> API Error: " + e.getMessage());
        }

        // --- THE RELIABLE SAFARI EXTRACTOR ---
        if (video == null || (video.details().liveUrl() == null && video.formats().isEmpty())) {
            android.util.Log.w("Youtube", ">>> API Blocked. Triggering Safari HLS Extraction for " + videoId);
            result.setParse(1); 
            // 使用 Safari UA。這會讓 YouTube 輸出最純淨的 HLS 清單，避開廣告片段
            String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.6 Safari/605.1.15";
            result.getHeader().put("User-Agent", ua);
            result.getHeader().put("Referer", "https://www.youtube.com/");
            return "https://www.youtube.com/watch?v=" + videoId;
        }

        String liveUrl = video.details().liveUrl();
        if (liveUrl != null) {
            android.util.Log.d("Youtube", ">>> Success! Found Live URL via API: " + (liveUrl.contains("dash") ? "DASH" : "HLS"));
            if (liveUrl.contains("api/manifest/dash") || liveUrl.contains(".mpd")) {
                result.setFormat(MimeTypes.APPLICATION_MPD);
            } else {
                result.setFormat(MimeTypes.APPLICATION_M3U8);
            }
            result.getHeader().put("User-Agent", getUA("IOS"));
            return liveUrl;
        }

        Format format = video.bestVideoWithAudioFormat();
        if (format == null) format = video.bestAudioFormat();
        if (format != null) {
            result.getHeader().put("User-Agent", getUA("WEB"));
            return format.url();
        }

        return "";
    }

    private String getUA(String name) {
        if (name.equals("TVHTML5")) return "Mozilla/5.0 (Web0S; Linux/SmartTV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.5735.202 Safari/537.36";
        if (name.equals("IOS")) return "com.google.ios.youtube/19.29.1 (iPhone15,3; U; CPU iOS 17_6_1 like Mac OS X; en_US)";
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36";
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
            try {
                String playlistId = Uri.parse(url).getQueryParameter("list");
                if (playlistId != null) {
                    PlaylistInfo playlist = new YoutubeDownloader().getPlaylistInfo(new RequestPlaylistInfo(playlistId)).data();
                    if (playlist != null) {
                        for (PlaylistVideoDetails video : playlist.videos()) {
                            items.add(Episode.create(video.title(), "https://www.youtube.com/watch?v=" + video.videoId()));
                        }
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
            return items;
        }
    }
}
