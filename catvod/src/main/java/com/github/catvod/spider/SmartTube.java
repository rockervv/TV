package com.github.catvod.spider;

import android.content.Context;
import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.liskovsoft.mediaserviceinterfaces.ContentService;
import com.liskovsoft.mediaserviceinterfaces.MediaItemService;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItem;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemFormatInfo;
import com.liskovsoft.mediaserviceinterfaces.data.MediaItemMetadata;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;

public class SmartTube extends Spider {
    private static final String VERSION = "v2026.09.15.2";
    private ContentService mContentService;
    private MediaItemService mItemService;
    private ExecutorService mExecutor;
    private final CountDownLatch mInitLatch = new CountDownLatch(1);

    private void execute(Runnable runnable) {
        if (mExecutor == null) mExecutor = Executors.newFixedThreadPool(5);
        mExecutor.execute(runnable);
    }

    private void waitInit() {
        try {
            if (!mInitLatch.await(10, TimeUnit.SECONDS)) {
                android.util.Log.w("SmartTube", ">>> [" + VERSION + "] [waitInit] Initialization timed out!");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String downloadCache(String key) {
        try {
            java.lang.Class<?> manager = java.lang.Class.forName("com.fongmi.android.tv.bean.RemoteSyncManager");
            Method method = manager.getMethod("downloadCache", String.class);
            return (String) method.invoke(null, key);
        } catch (Exception e) {
            return null;
        }
    }

    private void uploadCache(String key, String value) {
        try {
            java.lang.Class<?> manager = java.lang.Class.forName("com.fongmi.android.tv.bean.RemoteSyncManager");
            Method method = manager.getMethod("uploadCache", String.class, String.class);
            method.invoke(null, key, value);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public void init(Context context, String extend) {
        YouTubeServiceManager service = (YouTubeServiceManager) YouTubeServiceManager.instance();
        mContentService = service.getContentService();
        mItemService = service.getMediaItemService();

        if (!service.getSignInService().isSigned()) {
            execute(() -> {
                try {
                    Thread.interrupted(); 
                    android.util.Log.d("SmartTube", ">>> [" + VERSION + "] [init] Starting prioritized initialization...");

                    String remoteCookie = downloadCache("yt_visitor_cookie.txt");
                    if (android.text.TextUtils.isEmpty(remoteCookie)) {
                        String localCookie = com.liskovsoft.youtubeapi.service.internal.MediaServiceData.instance().getVisitorCookie();
                        if (!android.text.TextUtils.isEmpty(localCookie)) {
                            uploadCache("yt_visitor_cookie.txt", localCookie);
                        }
                    } else {
                        com.liskovsoft.youtubeapi.service.internal.MediaServiceData.instance().setVisitorCookie(remoteCookie);
                    }

                    mContentService.getRecommended();
                    android.util.Log.d("SmartTube", ">>> [" + VERSION + "] [init] Visitor data pre-warmed.");
                } catch (Exception e) {
                    android.util.Log.w("SmartTube", ">>> [init] Failed: " + e.getMessage());
                } finally {
                    mInitLatch.countDown();
                }
            });
        } else {
            mInitLatch.countDown();
        }
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        waitInit();
        List<Class> classes = new ArrayList<>();
        boolean signed = YouTubeServiceManager.instance().getSignInService().isSigned();

        if (signed) {
            classes.add(new Class("TYPE_SUBSCRIPTIONS", "訂閱內容"));
            classes.add(new Class("TYPE_HOME", "首頁"));
            classes.add(new Class("TYPE_HISTORY", "觀看紀錄"));
        } else {
            classes.add(new Class("TYPE_RECOMMENDED", "推薦影片"));
            classes.add(new Class("TYPE_HOME", "首頁"));
            classes.add(new Class("TYPE_SHORTS", "Shorts"));
            classes.add(new Class("TYPE_SPORTS", "體育"));
            classes.add(new Class("TYPE_LIVE", "直播"));
            classes.add(new Class("TYPE_NEWS", "新聞"));
            classes.add(new Class("TYPE_TRENDING", "趨勢"));
        }

        return Result.get().classes(classes).string();
    }

    @Override
    public String homeVideoContent() throws Exception {
        waitInit();
        boolean signed = YouTubeServiceManager.instance().getSignInService().isSigned();
        return categoryContent(signed ? "TYPE_HOME" : "TYPE_RECOMMENDED", "1", false, null);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        waitInit();
        List<MediaItem> allItems = new ArrayList<>();

        try {
            Observable<List<MediaGroup>> rowObs = null;
            Observable<MediaGroup> gridObs = null;

            switch (tid) {
                case "TYPE_HOME": rowObs = mContentService.getHomeObserve(); break;
                case "TYPE_RECOMMENDED": gridObs = mContentService.getRecommendedObserve(); break;
                case "TYPE_SHORTS": gridObs = mContentService.getShortsObserve(); break;
                case "TYPE_SPORTS": rowObs = mContentService.getSportsObserve(); break;
                case "TYPE_LIVE": rowObs = mContentService.getLiveObserve(); break;
                case "TYPE_TRENDING": rowObs = mContentService.getTrendingObserve(); break;
                case "TYPE_MUSIC": rowObs = mContentService.getMusicObserve(); break;
                case "TYPE_GAMING": rowObs = mContentService.getGamingObserve(); break;
                case "TYPE_NEWS": rowObs = mContentService.getNewsObserve(); break;
                case "TYPE_SUBSCRIPTIONS": gridObs = mContentService.getSubscriptionsObserve(); break;
                case "TYPE_HISTORY": gridObs = mContentService.getHistoryObserve(); break;
            }

            if (rowObs != null) {
                List<MediaGroup> groups = rowObs.observeOn(io.reactivex.schedulers.Schedulers.io())
                        .onErrorReturnItem(new ArrayList<>())
                        .blockingFirst(new ArrayList<>());
                for (MediaGroup group : groups) if (group != null && !group.isEmpty() && group.getMediaItems() != null) allItems.addAll(group.getMediaItems());
            } else if (gridObs != null) {
                MediaGroup group = gridObs.observeOn(io.reactivex.schedulers.Schedulers.io())
                        .blockingFirst(null);
                if (group != null && !group.isEmpty() && group.getMediaItems() != null) allItems.addAll(group.getMediaItems());
            }
        } catch (Exception e) {
            android.util.Log.w("SmartTube", ">>> [" + VERSION + "] [categoryContent] Fetch failed: " + e.getMessage());
        }

        android.util.Log.d("SmartTube", ">>> [" + VERSION + "] [categoryContent] Total Items: " + allItems.size());
        return Result.string(toVods(allItems));
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        waitInit();
        String videoId = ids.get(0);
        MediaItemMetadata metadata = mItemService.getMetadataObserve(videoId).blockingFirst();

        Vod vod = new Vod();
        String name = metadata.getTitle().replaceAll("[#$]", " ");
        vod.setVodId(metadata.getVideoId());
        vod.setVodName(name);
        vod.setVodPic("https://img.youtube.com/vi/" + metadata.getVideoId() + "/hqdefault.jpg");
        vod.setVodContent(metadata.getDescription());
        vod.setVodDirector(metadata.getAuthor());
        vod.setVodRemarks(metadata.getPublishedDate());
        vod.setVodPlayFrom("YouTube");
        vod.setVodPlayUrl(name + "$" + metadata.getVideoId());

        return Result.string(Collections.singletonList(vod));
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return searchContent(key, quick, "1");
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) throws Exception {
        waitInit();
        List<MediaItem> allItems = new ArrayList<>();
        List<MediaGroup> groups = mContentService.getSearchObserve(key).blockingFirst();
        if (groups != null) {
            for (MediaGroup g : groups) {
                if (g != null && g.getMediaItems() != null) allItems.addAll(g.getMediaItems());
            }
        }

        return Result.string(toVods(allItems));
    }

    @Override
    public String action(String action) throws Exception {
        YouTubeServiceManager service = (YouTubeServiceManager) YouTubeServiceManager.instance();
        if ("signIn".equals(action)) {
            return service.getSignInService().signInObserve().map(code -> {
                Vod vod = new Vod();
                vod.setVodId(code);
                vod.setVodName("YouTube 登入");
                vod.setVodRemarks("代碼：" + code);
                vod.setVodContent("請在手機或電腦瀏覽器打開 https://youtube.com/activate 並輸入代碼 " + code + " 完成登入。完成後請重啟 App 或等待同步。");
                vod.setVodPic("https://www.gstatic.com/youtube/img/branding/youtubelogo/2x/youtube_logo_dark_v2.png");
                return Result.string(Collections.singletonList(vod));
            }).blockingFirst();
        } else if ("signOut".equals(action)) {
            Account account = service.getSignInService().getSelectedAccount();
            if (account != null) {
                service.getSignInService().removeAccount(account);
                return Result.get().msg("已登出帳號: " + account.getName()).string();
            }
            return Result.get().msg("尚未登入").string();
        }
        return null;
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        waitInit();
        try {
            MediaItemFormatInfo formatInfo = mItemService.getFormatInfo(id);
            if (formatInfo != null) {
                boolean isLive = formatInfo.isLive() || formatInfo.isLiveContent();
                String url = formatInfo.getDashManifestUrl();
                if (TextUtils.isEmpty(url)) url = formatInfo.getHlsManifestUrl();

                if (TextUtils.isEmpty(url) && formatInfo.containsDashFormats()) {
                    url = "http://127.0.0.1:9978/dash?id=" + id + (isLive ? "&live=true" : "") + "&.mpd";
                    Map<String, String> headers = getHeaders();
                    String ua = formatInfo.getClientInfo() != null ? formatInfo.getClientInfo().getUserAgent() : null;
                    if (!TextUtils.isEmpty(ua)) headers.put("User-Agent", ua);
                    android.util.Log.d("SmartTube", ">>> [" + VERSION + "] [playerContent] Building Local MPD Proxy for " + id + " (Live=" + isLive + ")");
                    return Result.get().url(url).header(headers).string();
                }

                if (!TextUtils.isEmpty(url)) {
                    Map<String, String> headers = getHeaders();
                    String ua = formatInfo.getClientInfo() != null ? formatInfo.getClientInfo().getUserAgent() : null;
                    if (!TextUtils.isEmpty(ua)) headers.put("User-Agent", ua);
                    android.util.Log.d("SmartTube", ">>> [" + VERSION + "] [playerContent] Found " + (isLive ? "Live" : "VOD") + " Manifest: " + url + " | UA: " + ua);
                    return Result.get().url(url).header(headers).string();
                }

                // 🛡️ 備案：如果是點播 (VOD) 且獲取不到 Manifest，則不要直接返回 adaptive URL (容易 403 且可能無聲)
                // 強制回退到 watch URL 讓 App 的 Youtube 解析器 (WebView/Extractor) 處理，通常更穩定
                if (!isLive) {
                    android.util.Log.d("SmartTube", ">>> [" + VERSION + "] [playerContent] No Manifest found for VOD, falling back to app parser.");
                    return Result.get().url("https://www.youtube.com/watch?v=" + id).parse(1).header(getHeaders()).string();
                }

                // 🛡️ 最後的最後：如果是直播且真的沒 Manifest (極罕見)，才嘗試 adaptive 第一個格式
                if (formatInfo.getAdaptiveFormats() != null && !formatInfo.getAdaptiveFormats().isEmpty()) {
                    url = formatInfo.getAdaptiveFormats().get(0).getUrl();
                }

                if (!TextUtils.isEmpty(url)) {
                    Map<String, String> headers = getHeaders();
                    String ua = formatInfo.getClientInfo() != null ? formatInfo.getClientInfo().getUserAgent() : null;
                    if (!TextUtils.isEmpty(ua)) headers.put("User-Agent", ua);
                    android.util.Log.d("SmartTube", ">>> [" + VERSION + "] [playerContent] Found Legacy URL: " + url + " | UA: " + ua);
                    return Result.get().url(url).header(headers).string();
                }
            }
        } catch (Exception e) {
            android.util.Log.w("SmartTube", ">>> [" + VERSION + "] [playerContent] Format check failed: " + e.getMessage());
        }
        return Result.get().url("https://www.youtube.com/watch?v=" + id).parse(1).header(getHeaders()).string();
    }

    private Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://www.youtube.com/");
        headers.put("Origin", "https://www.youtube.com");
        headers.put("X-Goog-Api-Format-Version", "2");
        headers.put("Accept", "*/*");
        return headers;
    }

    private List<Vod> toVods(List<MediaItem> items) {
        List<Vod> vods = new ArrayList<>();
        if (items == null) return vods;
        for (MediaItem item : items) {
            if (item == null || TextUtils.isEmpty(item.getVideoId())) continue;
            Vod vod = new Vod();
            vod.setVodId(item.getVideoId());
            vod.setVodName(item.getTitle());
            vod.setVodPic(item.getCardImageUrl());
            vod.setVodRemarks(item.getBadgeText());
            vods.add(vod);
        }
        return vods;
    }
}
