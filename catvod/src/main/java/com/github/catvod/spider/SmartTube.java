package com.github.catvod.spider;

import android.content.Context;
import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.ServiceManager;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;
import io.reactivex.Observable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SmartTube extends Spider {
    private ContentService mContentService;
    private MediaItemService mItemService;

    @Override
    public void init(Context context, String extend) {
        ServiceManager service = YouTubeServiceManager.instance();
        mContentService = service.getContentService();
        mItemService = service.getMediaItemService();
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        classes.add(new Class("TYPE_HOME", "首页"));
        classes.add(new Class("TYPE_TRENDING", "趋势"));
        classes.add(new Class("TYPE_SUBSCRIPTIONS", "订阅"));
        classes.add(new Class("TYPE_HISTORY", "历史"));
        classes.add(new Class("TYPE_MUSIC", "音乐"));
        classes.add(new Class("TYPE_GAMING", "游戏"));
        classes.add(new Class("TYPE_NEWS", "新闻"));
        return Result.get().classes(classes).string();
    }

    @Override
    public String homeVideoContent() throws Exception {
        return categoryContent("TYPE_HOME", "1", false, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        Observable<List<MediaGroup>> rowObs = null;
        Observable<MediaGroup> gridObs = null;

        switch (tid) {
            case "TYPE_HOME": rowObs = mContentService.getHomeObserve(); break;
            case "TYPE_TRENDING": rowObs = mContentService.getTrendingObserve(); break;
            case "TYPE_MUSIC": rowObs = mContentService.getMusicObserve(); break;
            case "TYPE_GAMING": rowObs = mContentService.getGamingObserve(); break;
            case "TYPE_NEWS": rowObs = mContentService.getNewsObserve(); break;
            case "TYPE_SUBSCRIPTIONS": gridObs = mContentService.getSubscriptionsObserve(); break;
            case "TYPE_HISTORY": gridObs = mContentService.getHistoryObserve(); break;
        }

        List<MediaItem> allItems = new ArrayList<>();
        if (rowObs != null) {
            List<MediaGroup> groups = rowObs.blockingFirst();
            for (MediaGroup group : groups) {
                if (group.getMediaItems() != null) {
                    allItems.addAll(group.getMediaItems());
                }
            }
        } else if (gridObs != null) {
            MediaGroup group = gridObs.blockingFirst();
            if (group.getMediaItems() != null) {
                allItems.addAll(group.getMediaItems());
            }
        }

        return Result.string(toVods(allItems));
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String videoId = ids.get(0);
        MediaItemMetadata metadata = mItemService.getMetadataObserve(videoId).blockingFirst();

        Vod vod = new Vod();
        vod.setVodId(metadata.getVideoId());
        vod.setVodName(metadata.getTitle());
        vod.setVodPic("https://img.youtube.com/vi/" + metadata.getVideoId() + "/hqdefault.jpg");
        vod.setVodContent(metadata.getDescription());
        vod.setVodDirector(metadata.getAuthor());
        vod.setVodRemarks(metadata.getPublishedDate());
        vod.setVodPlayFrom("YouTube");
        vod.setVodPlayUrl(metadata.getTitle() + "$" + metadata.getVideoId());

        List<Vod> list = new ArrayList<>();
        list.add(vod);
        return Result.string(list);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        List<MediaGroup> groups = mContentService.getSearchObserve(key).blockingFirst();
        if (groups == null || groups.isEmpty()) return Result.get().vod(new ArrayList<>()).string();
        
        List<MediaItem> allItems = new ArrayList<>();
        for (MediaGroup group : groups) {
            if (group.getMediaItems() != null) {
                allItems.addAll(group.getMediaItems());
            }
        }
        return Result.string(toVods(allItems));
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        android.util.Log.d("SmartTube", ">>> [playerContent] videoId: " + id);
        
        MediaItemFormatInfo formatInfo = mItemService.getFormatInfoObserve(id).blockingFirst();
        
        // 🛠️ 強化策略：循環切換所有可用客戶端，直到獲取 DASH (.mpd)
        // DASH 格式在 YouTube 電視端協議中幾乎不會遇到 403 報錯
        int retry = 0;
        while (formatInfo.getDashManifestUrl() == null && retry < 5) {
            android.util.Log.w("SmartTube", ">>> DASH not found (Retry " + retry + "). Switching client to find MPD...");
            YouTubeServiceManager.instance().switchNextClient();
            formatInfo = mItemService.getFormatInfoObserve(id).blockingFirst();
            retry++;
        }

        String url = formatInfo.getDashManifestUrl();
        boolean isDash = true;
        
        if (url == null) {
            android.util.Log.e("SmartTube", ">>> Critical: All clients failed to provide DASH. Falling back to HLS...");
            url = formatInfo.getHlsManifestUrl();
            isDash = false;
        }
        
        if (url == null && formatInfo.getAdaptiveFormats() != null && !formatInfo.getAdaptiveFormats().isEmpty()) {
            url = formatInfo.getAdaptiveFormats().get(0).getUrl();
            isDash = false;
        }

        Result result = Result.get().url(url);
        if (isDash) {
            result.dash();
        } else if (url != null && url.contains(".m3u8")) {
            result.hls();
        }
        
        // 🛠️ 配合 DASH/HLS 的穩定標頭組合
        Map<String, String> headers = new HashMap<>();
        String realUA = formatInfo.getClientInfo() != null ? formatInfo.getClientInfo().getUserAgent() : "com.google.android.youtube.tv/2.17.005 Cobalt/19.lTS.3.213543-gold (unlike Gecko)";
        headers.put("User-Agent", realUA);
        
        // 重要：使用更精準的 Referer
        if (realUA.contains("Cobalt") || realUA.contains("tv")) {
            headers.put("Referer", "https://www.youtube.com/tv");
            headers.put("Origin", "https://www.youtube.com");
        } else {
            headers.put("Referer", "https://www.youtube.com/");
        }
        
        android.util.Log.d("SmartTube", ">>> Final Result - Format: " + (isDash ? "DASH" : "HLS"));
        android.util.Log.d("SmartTube", ">>> Using UA: " + realUA);
        android.util.Log.d("SmartTube", ">>> Final URL: " + url);

        return result.header(headers).string();
    }

    private List<Vod> toVods(List<MediaItem> items) {
        List<Vod> vods = new ArrayList<>();
        if (items == null) return vods;
        for (MediaItem item : items) {
            if (item.getVideoId() == null) continue;
            Vod vod = new Vod();
            vod.setVodId(item.getVideoId());
            vod.setVodName(item.getTitle());
            vod.setVodPic(item.getCardImageUrl());
            vod.setVodRemarks(item.getAuthor());
            vods.add(vod);
        }
        return vods;
    }
}
