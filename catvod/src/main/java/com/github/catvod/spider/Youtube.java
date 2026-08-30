package com.github.catvod.spider;

import android.content.Context;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.client.ClientType;
import com.github.kiulian.downloader.downloader.request.RequestSearchResult;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.model.search.SearchResult;
import com.github.kiulian.downloader.model.search.SearchResultVideoDetails;
import com.github.kiulian.downloader.model.videos.VideoDetails;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.Format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Youtube extends Spider {

    private YoutubeDownloader downloader;

    @Override
    public void init(Context context, String extend) throws Exception {
        com.github.kiulian.downloader.Config config = new com.github.kiulian.downloader.Config.Builder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();
        this.downloader = new YoutubeDownloader(config, new YoutubeDownloaderImpl(config));
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("trending", "發燒影片"));
        classes.add(new Class("music", "音樂"));
        classes.add(new Class("gaming", "發燒遊戲"));
        classes.add(new Class("movies", "電影"));
        return Result.get().classes(classes).vod(new ArrayList<>()).string();
    }

    @Override
    public String homeVideoContent() throws Exception {
        return categoryContent("trending", "1", false, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        String query = tid;
        if (tid.equals("trending")) query = "trending";
        else if (tid.equals("music")) query = "music trending";
        else if (tid.equals("gaming")) query = "gaming trending";
        else if (tid.equals("movies")) query = "movies";

        SearchResult result = downloader.search(new RequestSearchResult(query)).data();
        if (result == null) return Result.get().vod(new ArrayList<>()).string();
        return Result.string(convertList(result.videos()));
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String videoId = ids.get(0);
        VideoInfo videoInfo = downloader.getVideoInfo(new RequestVideoInfo(videoId).clientType(ClientType.IOS)).data();
        if (videoInfo == null || videoInfo.details().title() == null) videoInfo = downloader.getVideoInfo(new RequestVideoInfo(videoId).clientType(ClientType.TVHTML5)).data();
        
        VideoDetails details = videoInfo.details();
        Vod vod = new Vod();
        vod.setVodId(details.videoId());
        vod.setVodName(details.title());
        if (details.thumbnails() != null && !details.thumbnails().isEmpty()) {
            vod.setVodPic(details.thumbnails().get(details.thumbnails().size() - 1));
        }
        vod.setVodRemarks(details.author());
        vod.setVodContent(details.description());
        vod.setVodDirector(details.author());
        
        String playUrl = "Play$" + details.videoId();
        vod.setVodPlayFrom("YouTube");
        vod.setVodPlayUrl(playUrl);
        
        return Result.string(Arrays.asList(vod));
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        SearchResult result = downloader.search(new RequestSearchResult(key)).data();
        if (result == null) return Result.get().vod(new ArrayList<>()).string();
        return Result.string(convertList(result.videos()));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        VideoInfo videoInfo = downloader.getVideoInfo(new RequestVideoInfo(id).clientType(ClientType.IOS)).data();
        if (videoInfo == null || (videoInfo.details().liveUrl() == null && videoInfo.formats().isEmpty())) {
            videoInfo = downloader.getVideoInfo(new RequestVideoInfo(id).clientType(ClientType.TVHTML5)).data();
        }

        String url = "";
        String liveUrl = videoInfo.details().liveUrl();
        if (liveUrl != null) {
            url = liveUrl;
        } else {
            Format format = videoInfo.bestVideoWithAudioFormat();
            if (format == null) format = videoInfo.bestAudioFormat();
            if (format != null) url = format.url();
        }

        return Result.get().url(url).header(getHeaders()).parse(0).string();
    }

    private List<Vod> convertList(List<SearchResultVideoDetails> videos) {
        List<Vod> list = new ArrayList<>();
        if (videos == null) return list;
        for (SearchResultVideoDetails item : videos) {
            Vod vod = new Vod();
            vod.setVodId(item.videoId());
            vod.setVodName(item.title());
            if (item.thumbnails() != null && !item.thumbnails().isEmpty()) {
                vod.setVodPic(item.thumbnails().get(item.thumbnails().size() - 1));
            }
            vod.setVodRemarks(item.author() + (item.isLive() ? " [直播]" : ""));
            list.add(vod);
        }
        return list;
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
        headers.put("Referer", "https://www.youtube.com/");
        return headers;
    }
}
