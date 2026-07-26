package com.fongmi.android.tv.api;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.collection.ArrayMap;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
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
        return site.getType() == 3;
    }

    private static String ac(int type) {
        return type == 0 ? "videolist" : "detail";
    }

    @NonNull
    public static Result homeContent(@NonNull Site site) {
        android.util.Log.d("SiteApi", "homeContent [START] site: " + site.getName());
        try {
            if (isSpider(site)) {
                Spider spider = site.recent().spider();
                String home = spider.homeContent(true);
                android.util.Log.d("SiteApi", "homeContent [RAW]: " + (home == null ? "NULL" : home.length() > 500 ? home.substring(0, 500) + "..." : home));
                Result result = Result.fromJson(home);
                if (result.getList().isEmpty()) {
                    String video = spider.homeVideoContent();
                    android.util.Log.d("SiteApi", "homeVideoContent [RAW]: " + (video == null ? "NULL" : video.length() > 500 ? video.substring(0, 500) + "..." : video));
                    result.setList(Result.fromJson(video).getList());
                }
                if (result.getTypes().isEmpty() && !site.getCategories().isEmpty()) {
                    for (String name : site.getCategories()) {
                        Class type = new Class();
                        type.setTypeName(name);
                        type.setTypeId(name);
                        result.getTypes().add(type);
                    }
                }
                android.util.Log.d("SiteApi", "homeContent [RESULT]: list=" + result.getList().size() + ", types=" + result.getTypes().size());
                for (Vod vod : result.getList()) vod.setSite(site);
                setTypes(site, result);
                site.resetFailures();
                return result;
            } else if (site.getType() == 4) {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("filter", "true");
                String homeContent = call(site, params);
                SpiderDebug.log(homeContent);
                Result result = Result.fromJson(homeContent);
                setTypes(site, result);
                site.resetFailures();
                return result;
            } else {
                try (Response response = OkHttp.newCall(site.getApi(), site.getHeader()).execute()) {
                    String homeContent = response.body().string();
                    SpiderDebug.log(homeContent);
                    Result result = Result.fromType(site.getType(), homeContent);
                    fetchPic(site, result);
                    setTypes(site, result);
                    site.resetFailures();
                    return result;
                }
            }
        } catch (Throwable e) {
            SpiderDebug.log(site.getName());
            SpiderDebug.log(e);
            // site.setBlacklist();
            return Result.empty();
        }
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend) {
        android.util.Log.d("SiteApi", "categoryContent [START] key: " + key + ", tid: " + tid + ", page: " + page);
        Site site = VodConfig.get().getSite(key);
        try {
            // if (site.isBlacklist()) return Result.empty();
            if (isSpider(site)) {
                String categoryContent = site.recent().spider().categoryContent(tid, page, filter, extend);
                android.util.Log.d("SiteApi", "categoryContent [RAW]: " + (categoryContent == null ? "NULL" : categoryContent.length() > 500 ? categoryContent.substring(0, 500) + "..." : categoryContent));
                if (TextUtils.isEmpty(categoryContent)) return Result.empty();
                Result result = Result.fromJson(categoryContent);
                android.util.Log.d("SiteApi", "categoryContent [RESULT]: list=" + result.getList().size());
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                if (site.getType() == 1 && !extend.isEmpty()) params.put("f", App.gson().toJson(extend));
                if (site.getType() == 4) params.put("ext", Util.base64(App.gson().toJson(extend), Util.URL_SAFE));
                params.put("ac", ac(site.getType()));
                params.put("t", tid);
                params.put("pg", page);
                String categoryContent = call(site, params);
                SpiderDebug.log(categoryContent);
                return Result.fromType(site.getType(), categoryContent);
            }
        } catch (Throwable e) {
            SpiderDebug.log(key);
            SpiderDebug.log(e);
            // site.setBlacklist();
            return Result.empty();
        }
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id) {
        android.util.Log.d("SiteApi", "detailContent [START] key: " + key + ", id: " + id);
        Site site = VodConfig.get().getSite(key);
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
                String detailContent = site.recent().spider().detailContent(Arrays.asList(id));
                SpiderDebug.log(detailContent);
                Result result = Result.fromJson(detailContent);
                if (!result.getList().isEmpty()) result.getVod().setFlags();
                Source.get().parse(result.getVod());
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("ac", ac(site.getType()));
                params.put("ids", id);
                String detailContent = call(site, params);
                SpiderDebug.log(detailContent);
                Result result = Result.fromType(site.getType(), detailContent);
                if (!result.getList().isEmpty()) result.getVod().setFlags();
                Source.get().parse(result.getVod());
                return result;
            }
        } catch (Throwable e) {
            SpiderDebug.log(key);
            SpiderDebug.log(e);
            // site.setBlacklist();
            return Result.empty();
        }
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id) {
        Site site = VodConfig.get().getSite(key);
        try {
            // if (site.isBlacklist()) return Result.empty();
            Source.get().stop();
            if (site.getType() == 3) {
                String playerContent = site.recent().spider().playerContent(flag, id, VodConfig.get().getFlags());
                SpiderDebug.log(playerContent);
                Result result = Result.fromJson(playerContent);
                if (result.getFlag().isEmpty()) result.setFlag(flag);
                if (result.getHeader().isEmpty()) result.setHeader(site.getHeader());
                result.setUrl(Source.get().fetch(result));
                result.setKey(key);
                return result;
            } else if (site.getType() == 4) {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("play", id);
                params.put("flag", flag);
                String playerContent = call(site, params);
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
            SpiderDebug.log(key);
            SpiderDebug.log(e);
            // site.setBlacklist();
            return Result.empty();
        }
    }

    @NonNull
    public static Result searchContent(@NonNull Site site, @NonNull String keyword, boolean quick, @NonNull String page) {
        try {
            // if (site.isBlacklist()) return Result.empty();
            boolean hasPage = !page.equals("1");
            if (isSpider(site)) {
                String searchContent = hasPage ? site.spider().searchContent(keyword, quick, page) : site.spider().searchContent(keyword, quick);
                SpiderDebug.log(searchContent);
                Result result = Result.fromJson(searchContent);
                for (Vod vod : result.getList()) vod.setSite(site);
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("wd", keyword);
                params.put("quick", String.valueOf(quick));
                if (hasPage) params.put("pg", page);
                String searchContent = call(site, params);
                SpiderDebug.log(searchContent);
                Result result = fetchPic(site, Result.fromType(site.getType(), searchContent));
                for (Vod vod : result.getList()) vod.setSite(site);
                return result;
            }
        } catch (Throwable e) {
            SpiderDebug.log(site.getName());
            SpiderDebug.log(e);
            // site.setBlacklist();
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
