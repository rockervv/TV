package com.fongmi.android.tv.api;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import okhttp3.Call;
import okhttp3.Response;

public class SiteApi {

    public static final String PUSH = "push_agent";

    public static String call(@NonNull Site site, @NonNull ArrayMap<String, String> params) throws IOException {
        if (!site.getExt().isEmpty()) params.put("extend", site.getExt());
        if (site.getApi().isEmpty()) return "";
        Call call = site.getExt().length() <= 1000 ? OkHttp.newCall(site.getApi(), site.getHeader(), params) : OkHttp.newCall(site.getApi(), site.getHeader(), OkHttp.toBody(params));
        if (call == null) return "";
        try (Response response = call.execute()) {
            return response.body().string();
        }
    }

    private static boolean isSpider(@NonNull Site site) {
        String api = site.getApi();
        String key = site.getKey();
        if (site.getType() == 3) return true;
        if (api != null && (api.contains(".js") || api.contains(".py") || api.startsWith("csp_") || api.contains("drpy"))) return true;
        if (key != null && (key.startsWith("drpy_") || key.contains("js_") || key.startsWith("csp_"))) return true;
        return false;
    }

    private static String ac(int type) {
        return type == 0 ? "videolist" : "detail";
    }

    @NonNull
    public static Result homeContent(@NonNull Site site) {
        return homeContent(site, false);
    }

    @NonNull
    public static Result homeContent(@NonNull Site site, boolean refresh) {
        if (!VodConfig.get().isLoaded()) {
            android.util.Log.w("SiteApi", "homeContent [ABORTED]: VodConfig not loaded yet!");
            return Result.empty().setTid("");
        }
        if (BaseLoader.get().getJarLoader().isError(site.getJar())) return Result.empty();
        if (site.getApi().isEmpty() || site.getType() == 0) {
            Site recovery = VodConfig.get().getSite(site.getKey());
            if (recovery.getApi().isEmpty()) {
                android.util.Log.e("SiteApi", "homeContent [ERROR]: Incomplete site info for " + site.getName());
                return Result.empty();
            }
            site = recovery;
        }
        android.util.Log.d("SiteApi", "homeContent [START] site: " + site.getName() + " (Key: " + site.getKey() + ", Type: " + site.getType() + ", API: " + site.getApi() + ")");
        try {
            if (isSpider(site)) {
                if (!refresh) {
                    Result cache = CacheManager.get(site);
                    if (cache != null) return cache;
                }
                Spider spider = site.recent().spider();
                android.util.Log.d("SiteApi", "homeContent [Spider]: Initializing " + site.getApi() + " for site: " + site.getName());
                String home = spider.homeContent(true);
                android.util.Log.d("SiteApi", "homeContent [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (home == null ? "NULL" : home.isEmpty() ? "EMPTY" : home.length() > 500 ? home.substring(0, 500) + "..." : home));
                Result result = Result.fromJson(home);
                result.setKey(site.getKey());
                if (result.getList().isEmpty()) {
                    String video = spider.homeVideoContent();
                    android.util.Log.d("SiteApi", "homeVideoContent [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (video == null ? "NULL" : video.isEmpty() ? "EMPTY" : video.length() > 500 ? video.substring(0, 500) + "..." : video));
                    result.setList(Result.fromJson(video).getList());
                }
                if (result.getTypes().isEmpty() && !site.getCategories().isEmpty()) {
                    android.util.Log.d("SiteApi", "homeContent [INFO] site: " + site.getName() + ": Types empty, using site categories: " + site.getCategories());
                    for (String name : site.getCategories()) {
                        Class type = new Class();
                        type.setTypeName(name);
                        type.setTypeId(name);
                        result.getTypes().add(type);
                    }
                }
                android.util.Log.d("SiteApi", "homeContent [RESULT] site: " + site.getName() + ": list=" + result.getList().size() + ", types=" + result.getTypes().size());
                for (Vod vod : result.getList()) vod.setSite(site);
                setTypes(site, result);
                site.resetFailures();
                site.save();
                CacheManager.put(site, result);
                return result;
            } else if (site.getType() == 4) {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("filter", "true");
                String homeContent = call(site, params);
                android.util.Log.d("SiteApi", "homeContent Type4 [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (homeContent == null ? "NULL" : homeContent.isEmpty() ? "EMPTY" : homeContent.length() > 500 ? homeContent.substring(0, 500) + "..." : homeContent));
                SpiderDebug.log(homeContent);
                Result result = Result.fromJson(homeContent);
                result.setKey(site.getKey());
                result.setTid("");
                setTypes(site, result);
                site.resetFailures();
                site.save();
                return result;
            } else {
                try (Response response = OkHttp.newCall(site.getApi(), site.getHeader()).execute()) {
                    String homeContent = response.body().string();
                    SpiderDebug.log(homeContent);
                    Result result = Result.fromType(site.getType(), homeContent);
                    result.setKey(site.getKey());
                    result.setTid("");
                    fetchPic(site, result);
                    setTypes(site, result);
                    site.resetFailures();
                    site.save();
                    return result;
                }
            }
        } catch (Throwable e) {
            BaseLoader.get().setFailure(site.getJar(), e);
            SpiderDebug.log(site.getName());
            SpiderDebug.log(e);
            return Result.empty().setTid("");
        }
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend) {
        return categoryContent(key, tid, page, filter, extend, false);
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend, boolean refresh) {
        if (!VodConfig.get().isLoaded()) {
            android.util.Log.w("SiteApi", "categoryContent [ABORTED]: VodConfig not loaded yet! (Key: " + key + ")");
            return Result.empty().setTid(tid);
        }
        Site site = VodConfig.get().getSite(key);
        if (BaseLoader.get().getJarLoader().isError(site.getJar())) return Result.empty().setTid(tid);
        if (site.getApi().isEmpty()) {
            android.util.Log.e("SiteApi", "categoryContent [ERROR]: Site not found or API empty for key: " + key);
            return Result.empty().setTid(tid);
        }
        android.util.Log.d("SiteApi", "categoryContent [START] site: " + site.getName() + " (Key: " + key + "), tid: " + tid + ", page: " + page + ", type: " + site.getType() + ", API: " + site.getApi());
        try {
            if (isSpider(site)) {
                if (!refresh) {
                    Result cache = CacheManager.get(site, tid, page);
                    if (cache != null) return cache.setTid(tid);
                }
                android.util.Log.d("SiteApi", "categoryContent [Spider] site: " + site.getName() + " plugin: " + site.getApi() + ": tid=" + tid + " key=" + key);
                String categoryContent = site.recent().spider().categoryContent(tid, page, filter, extend);
                if (TextUtils.isEmpty(categoryContent)) return Result.empty().setTid(tid);
                Result result = Result.fromJson(categoryContent);
                result.setKey(key);
                result.setTid(tid);
                CacheManager.put(site, tid, page, result);
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                if (site.getType() == 1 && !extend.isEmpty()) params.put("f", App.gson().toJson(extend));
                if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(extend), Util.URL_SAFE));
                params.put("ac", ac(site.getType()));
                params.put("t", tid);
                params.put("pg", page);
                String categoryContent = call(site, params);
                android.util.Log.d("SiteApi", "categoryContent Non-Spider [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (categoryContent == null ? "NULL" : categoryContent.isEmpty() ? "EMPTY" : categoryContent.length() > 500 ? categoryContent.substring(0, 500) + "..." : categoryContent));
                SpiderDebug.log(categoryContent);
                Result result = Result.fromType(site.getType(), categoryContent);
                result.setKey(key);
                result.setTid(tid);
                return result;
            }
        } catch (Throwable e) {
            android.util.Log.e("SiteApi", "categoryContent [ERROR] site: " + site.getName() + " (Key: " + key + "): " + e.getMessage(), e);
            SpiderDebug.log(key);
            SpiderDebug.log(e);
            return Result.empty().setTid(tid);
        }
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id) {
        if (!VodConfig.get().isLoaded()) {
            android.util.Log.w("SiteApi", "detailContent [ABORTED]: VodConfig not loaded yet! (Key: " + key + ")");
            return Result.empty();
        }
        Site site = VodConfig.get().getSite(key);
        if (BaseLoader.get().getJarLoader().isError(site.getJar())) return Result.empty();
        if (site.getApi().isEmpty() && !PUSH.equals(key)) {
            android.util.Log.e("SiteApi", "detailContent [ERROR]: Site not found or API empty for key: " + key);
            return Result.empty();
        }
        android.util.Log.d("SiteApi", "detailContent [START] site: " + site.getName() + " (Key: " + key + "), id: " + id + ", type: " + site.getType() + ", API: " + site.getApi());
        try {
            // if (site.isBlacklist()) return Result.empty();
            if (site.isEmpty() && PUSH.equals(key)) {
                Vod vod = new Vod();
                vod.setId(id);
                vod.setName(id);
                vod.setPlayUrl(id);
                vod.setPlayFrom(ResUtil.getString(R.string.push));
                vod.setPic(ResUtil.getString(R.string.push_image));
                vod.setFlags();
                Source.get().parse(vod);
                return Result.vod(vod);
            } else if (isSpider(site)) {
                android.util.Log.d("SiteApi", "detailContent [Spider] site: " + site.getName() + " plugin: " + site.getApi() + ": Calling JS/CSP fetch...");
                String detailContent = site.recent().spider().detailContent(Arrays.asList(id));
                android.util.Log.d("SiteApi", "detailContent [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (detailContent == null ? "NULL" : detailContent.isEmpty() ? "EMPTY" : detailContent.length() > 500 ? detailContent.substring(0, 500) + "..." : detailContent));
                SpiderDebug.log(detailContent);
                Result result = Result.fromJson(detailContent);
                result.setKey(key);
                if (!result.getList().isEmpty()) result.getVod().setFlags();
                Source.get().parse(result.getVod());
                android.util.Log.d("SiteApi", "detailContent [RESULT] site: " + site.getName() + ": name=" + (result.getList().isEmpty() ? "EMPTY" : result.getVod().getName()));
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("ac", ac(site.getType()));
                params.put("ids", id);
                android.util.Log.d("SiteApi", "detailContent [Non-Spider] site: " + site.getName() + ": Calling external API: " + site.getApi() + " with params: " + params);
                String detailContent = call(site, params);
                android.util.Log.d("SiteApi", "detailContent Non-Spider [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (detailContent == null ? "NULL" : detailContent.isEmpty() ? "EMPTY" : detailContent.length() > 500 ? detailContent.substring(0, 500) + "..." : detailContent));
                SpiderDebug.log(detailContent);
                Result result = Result.fromType(site.getType(), detailContent);
                result.setKey(key);
                if (!result.getList().isEmpty()) result.getVod().setFlags();
                Source.get().parse(result.getVod());
                return result;
            }
        } catch (Throwable e) {
            android.util.Log.e("SiteApi", "detailContent [ERROR] site: " + site.getName() + " (Key: " + key + "): " + e.getMessage(), e);
            SpiderDebug.log(key);
            SpiderDebug.log(e);
            // site.setBlacklist();
            return Result.empty();
        }
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id) {
        Site site = VodConfig.get().getSite(key);
        android.util.Log.d("SiteApi", "playerContent [START] site: " + site.getName() + " (Key: " + key + "), flag: " + flag + ", id: " + id + ", API: " + site.getApi());
        try {
            // if (site.isBlacklist()) return Result.empty();
            Source.get().stop();
            if (site.getType() == 3) {
                String playerContent = site.recent().spider().playerContent(flag, id, VodConfig.get().getFlags());
                android.util.Log.d("SiteApi", "playerContent [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (playerContent == null ? "NULL" : playerContent.isEmpty() ? "EMPTY" : playerContent.length() > 500 ? playerContent.substring(0, 500) + "..." : playerContent));
                SpiderDebug.log(playerContent);
                Result result = Result.fromJson(playerContent);
                if (result.getFlag().isEmpty()) result.setFlag(flag);
                if (result.getHeader().isEmpty()) result.setHeader(site.getHeader());
                result.setUrl(Source.get().fetch(result));
                result.setKey(key);
                android.util.Log.d("SiteApi", "playerContent [RESULT] site: " + site.getName() + ": url=" + result.getUrl().v());
                return result;
            } else if (site.getType() == 4) {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("play", id);
                params.put("flag", flag);
                String playerContent = call(site, params);
                android.util.Log.d("SiteApi", "playerContent Type4 [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (playerContent == null ? "NULL" : playerContent.isEmpty() ? "EMPTY" : playerContent.length() > 500 ? playerContent.substring(0, 500) + "..." : playerContent));
                SpiderDebug.log(playerContent);
                Result result = Result.fromJson(playerContent);
                if (result.getFlag().isEmpty()) result.setFlag(flag);
                result.setUrl(Source.get().fetch(result));
                result.setHeader(site.getHeader());
                return result;
            } else if (site.isEmpty() && PUSH.equals(key)) {
                Result result = new Result();
                result.setUrl(id);
                result.setParse(0);
                result.setFlag(flag);
                result.setUrl(Source.get().fetch(result));
                SpiderDebug.log(result.toString());
                return result;
            } else {
                Result result = new Result();
                result.setUrl(id);
                result.setFlag(flag);
                result.setHeader(site.getHeader());
                result.setPlayUrl(site.getPlayUrl());
                result.setParse(Sniffer.isVideoFormat(id) && result.getPlayUrl().isEmpty() ? 0 : 1);
                result.setUrl(Source.get().fetch(result));
                SpiderDebug.log(result.toString());
                return result;
            }
        } catch (Throwable e) {
            BaseLoader.get().setFailure(VodConfig.get().getSite(key).getJar(), e);
            SpiderDebug.log(key);
            SpiderDebug.log(e);
            // site.setBlacklist();
            return Result.empty();
        }
    }

    @NonNull
    public static Result searchContent(@NonNull Site site, @NonNull String keyword, boolean quick, @NonNull String page) {
        if (BaseLoader.get().getJarLoader().isError(site.getJar())) return Result.empty();
        android.util.Log.d("SiteApi", "searchContent [START] site: " + site.getName() + " (Key: " + site.getKey() + "), keyword: " + keyword + ", API: " + site.getApi());
        try {
            boolean hasPage = !page.equals("1");
            if (isSpider(site)) {
                String searchContent = hasPage ? site.spider().searchContent(keyword, quick, page) : site.spider().searchContent(keyword, quick);
                android.util.Log.d("SiteApi", "searchContent [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (searchContent == null ? "NULL" : searchContent.isEmpty() ? "EMPTY" : searchContent.length() > 500 ? searchContent.substring(0, 500) + "..." : searchContent));
                if (TextUtils.isEmpty(searchContent) || searchContent.trim().equals("{}")) {
                    site.decrementScore();
                    return Result.empty();
                }
                if (searchContent.contains("500") || searchContent.contains("503") || searchContent.contains("3003") || searchContent.toLowerCase().contains("authentication")) {
                    site.setErrorScore();
                    return Result.empty();
                }
                Result result = Result.fromJson(searchContent);
                result.setKey(site.getKey());
                for (Vod vod : result.getList()) vod.setSite(site);
                android.util.Log.d("SiteApi", "searchContent [RESULT] site: " + site.getName() + ": list=" + result.getList().size());
                site.incrementScore();
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("wd", keyword);
                params.put("quick", String.valueOf(quick));
                if (hasPage) params.put("pg", page);
                String searchContent = call(site, params);
                android.util.Log.d("SiteApi", "searchContent Non-Spider [RAW] site: " + site.getName() + " plugin: " + site.getApi() + ": " + (searchContent == null ? "NULL" : searchContent.isEmpty() ? "EMPTY" : searchContent.length() > 500 ? searchContent.substring(0, 500) + "..." : searchContent));
                if (TextUtils.isEmpty(searchContent) || searchContent.trim().equals("{}")) {
                    site.decrementScore();
                    return Result.empty();
                }
                if (searchContent.contains("500") || searchContent.contains("503") || searchContent.contains("3003") || searchContent.toLowerCase().contains("authentication")) {
                    site.setErrorScore();
                    return Result.empty();
                }
                Result result = Result.fromType(site.getType(), searchContent);
                result.setKey(site.getKey());
                result = fetchPic(site, result);
                for (Vod vod : result.getList()) vod.setSite(site);
                site.incrementScore();
                return result;
            }
        } catch (Throwable e) {
            String trace = android.util.Log.getStackTraceString(e).toLowerCase();
            if (trace.contains("500") || trace.contains("503") || trace.contains("3003") || trace.contains("authentication") || trace.contains("code: 50") || trace.contains("code: 300")) {
                site.setErrorScore();
            } else {
                site.decrementScore();
            }
            BaseLoader.get().setFailure(site.getJar(), e);
            android.util.Log.e("SiteApi", "searchContent [ERROR] site: " + site.getName() + " (Key: " + site.getKey() + "): " + e.getMessage(), e);
            SpiderDebug.log(site.getName());
            SpiderDebug.log(e);
            return Result.empty();
        }
    }

    @NonNull
    public static Result action(@NonNull String key, @NonNull String action) {
        try {
            Site site = VodConfig.get().getSite(key);
            if (site.getType() == 3) return Result.fromJson(site.recent().spider().action(action));
            if (site.getType() == 4) return Result.fromJson(OkHttp.string(action));
            return Result.empty();
        } catch (Throwable e) {
            SpiderDebug.log(key);
            SpiderDebug.log(e);
            return Result.empty();
        }
    }

    @NonNull
    public static Result fetchPic(@NonNull Site site, @NonNull Result result) {
        try {
            if (site.getType() > 2 || result.getList().isEmpty() || !result.getVod().getPic().isEmpty()) return result;
            ArrayList<String> ids = new ArrayList<>();
            boolean empty = site.getCategories().isEmpty();
            for (Vod item : result.getList()) if (empty || site.getCategories().contains(item.getTypeName())) ids.add(item.getId());
            if (ids.isEmpty()) return result.clear();
            ArrayMap<String, String> params = new ArrayMap<>();
            params.put("ac", ac(site.getType()));
            params.put("ids", TextUtils.join(",", ids));
            try (Response response = OkHttp.newCall(site.getApi(), site.getHeader(), params).execute()) {
                result.setList(Result.fromType(site.getType(), response.body().string()).getList());
                return result;
            }
        } catch (Throwable e) {
            SpiderDebug.log(site.getName());
            SpiderDebug.log(e);
            return result;
        }
    }

    private static void setTypes(@NonNull Site site, @NonNull Result result) {
        for (Class type : result.getTypes()) {
            if (result.getFilters().containsKey(type.getTypeId())) {
                type.setFilters(result.getFilters().get(type.getTypeId()));
            }
        }
    }
}
