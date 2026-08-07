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
        if (site.getType() == 3) return true;
        if (TextUtils.isEmpty(api)) return false;
        api = api.toLowerCase();
        if (api.startsWith("csp_") || api.contains(".js") || api.contains(".py") || api.contains("drpy") || api.contains("js_") || api.contains("proxy://")) return true;
        return site.getJar() != null && !site.getJar().isEmpty();
    }

    private static boolean isHtml(String content) {
        if (TextUtils.isEmpty(content)) return false;
        String check = content.toUpperCase();
        return check.contains("<!DOCTYPE") || check.contains("<HTML") || check.contains("ACCESS DENIED");
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
        if (!VodConfig.get().isLoaded() || BaseLoader.get().getJarLoader().isError(site.getJar())) return Result.empty().setTid("");
        if (site.getApi().isEmpty() || site.getType() == 0) {
            Site recovery = VodConfig.get().getSite(site.getKey());
            if (recovery.getApi().isEmpty()) return Result.empty();
            site = recovery;
        }
        try {
            if (isSpider(site)) {
                if (!refresh) {
                    Result cache = CacheManager.get(site);
                    if (cache != null) return cache;
                }
                Spider spider = site.recent().spider();
                String home = spider.homeContent(true);
                if (isHtml(home)) {
                    site.setBlacklist();
                    return Result.empty();
                }
                Result result = Result.fromJson(home);
                result.setKey(site.getKey());
                if (result.getList().isEmpty()) {
                    String video = spider.homeVideoContent();
                    if (video != null && !video.isEmpty()) result.setList(Result.fromJson(video).getList());
                }
                if (result.getTypes().isEmpty() && !site.getCategories().isEmpty()) {
                    for (String name : site.getCategories()) {
                        Class type = new Class();
                        type.setTypeName(name);
                        type.setTypeId(name);
                        result.getTypes().add(type);
                    }
                }
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
            return Result.empty().setTid("");
        }
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend) {
        return categoryContent(key, tid, page, filter, extend, false);
    }

    @NonNull
    public static Result categoryContent(@NonNull String key, @NonNull String tid, @NonNull String page, boolean filter, @NonNull HashMap<String, String> extend, boolean refresh) {
        if (!VodConfig.get().isLoaded()) return Result.empty().setTid(tid);
        Site site = VodConfig.get().getSite(key);
        if (BaseLoader.get().getJarLoader().isError(site.getJar()) || site.getApi().isEmpty()) return Result.empty().setTid(tid);
        
        // 🛠️ 核心修復：發送前將所有參數轉化為簡體，確保 Mainland 站點能正確識別
        HashMap<String, String> params = new HashMap<>();
        for (String k : extend.keySet()) params.put(k, com.github.catvod.utils.Trans.z2p(extend.get(k)));
        
        String extHash = CacheManager.getExtHash(params);
        android.util.Log.d("FILTER_DEBUG", ">>> [STEP 1: Request Start] Site: " + site.getName() + " | TID: " + tid + " | Page: " + page);
        android.util.Log.d("FILTER_DEBUG", ">>> [STEP 2: Params Sent (Simplified)] Hash: " + extHash + " Content: " + App.gson().toJson(params));

        try {
            if (isSpider(site)) {
                if (!refresh && com.fongmi.android.tv.setting.Setting.isCategoryCache()) {
                    Result cache = CacheManager.get(site, tid, page, extHash);
                    if (cache != null) {
                        android.util.Log.d("FILTER_DEBUG", ">>> [STEP 3: Cache Hit] Returning cached data.");
                        return cache.setTid(tid);
                    }
                }
                
                android.util.Log.d("FILTER_DEBUG", ">>> [STEP 4: API Fetch] Calling spider.categoryContent()...");
                long startTime = System.currentTimeMillis();
                String categoryContent = site.recent().spider().categoryContent(tid, page, filter, params);
                long cost = System.currentTimeMillis() - startTime;
                
                if (TextUtils.isEmpty(categoryContent)) {
                    android.util.Log.e("FILTER_DEBUG", ">>> [STEP 5: API Error] Spider returned EMPTY string! (Cost: " + cost + "ms)");
                    return Result.empty().setTid(tid);
                }

                if (isHtml(categoryContent)) {
                    android.util.Log.e("FILTER_DEBUG", ">>> [STEP 5: API Error] Spider returned HTML (Access Denied)! (Cost: " + cost + "ms)");
                    site.setBlacklist();
                    return Result.empty().setTid(tid);
                }

                android.util.Log.d("FILTER_DEBUG", ">>> [STEP 5: API Success] Response Length: " + categoryContent.length() + " (Cost: " + cost + "ms)");
                
                Result result = Result.fromJson(categoryContent);
                result.setKey(key);
                result.setTid(tid);

                if (com.fongmi.android.tv.setting.Setting.isCategoryCache()) {
                    CacheManager.put(site, tid, page, extHash, result);
                }
                
                if (!result.getList().isEmpty()) {
                    android.util.Log.d("FILTER_DEBUG", ">>> [STEP 6: Content Sample] First Item: " + result.getList().get(0).getVodName());
                }

                return result;
            } else {
                ArrayMap<String, String> legacyParams = new ArrayMap<>();
                if (site.getType() == 1 && !params.isEmpty()) legacyParams.put("f", App.gson().toJson(params));
                if (site.getType() == 4) legacyParams.put("ext", Util.base64(App.gson().toJson(params), Util.URL_SAFE));
                legacyParams.put("ac", ac(site.getType()));
                legacyParams.put("t", tid);
                legacyParams.put("pg", page);
                String raw = call(site, legacyParams);
                android.util.Log.d("FILTER_DEBUG", ">>> [API Fetch] Raw API Response Sample: " + (raw.length() > 200 ? raw.substring(0, 200) : raw));
                Result result = Result.fromType(site.getType(), raw);
                result.setKey(key);
                result.setTid(tid);
                return result;
            }
        } catch (Throwable e) {
            android.util.Log.e("FILTER_DEBUG", ">>> [CRITICAL ERROR] categoryContent failed", e);
            return Result.empty().setTid(tid);
        }
    }

    @NonNull
    public static Result detailContent(@NonNull String key, @NonNull String id) {
        if (!VodConfig.get().isLoaded()) return Result.empty();
        Site site = VodConfig.get().getSite(key);
        if (BaseLoader.get().getJarLoader().isError(site.getJar())) return Result.empty();
        if (site.getApi().isEmpty() && !PUSH.equals(key)) return Result.empty();
        try {
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
                if (isHtml(detailContent)) {
                    site.setBlacklist();
                    return Result.empty();
                }
                Result result = Result.fromJson(detailContent);
                result.setKey(key);
                if (!result.getList().isEmpty()) result.getVod().setFlags();
                Source.get().parse(result.getVod());
                return result;
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("ac", ac(site.getType()));
                params.put("ids", id);
                String detailContent = call(site, params);
                Result result = Result.fromType(site.getType(), detailContent);
                result.setKey(key);
                if (!result.getList().isEmpty()) result.getVod().setFlags();
                Source.get().parse(result.getVod());
                return result;
            }
        } catch (Throwable e) {
            return Result.empty();
        }
    }

    @NonNull
    public static Result playerContent(@NonNull String key, @NonNull String flag, @NonNull String id) {
        Site site = VodConfig.get().getSite(key);
        try {
            Source.get().stop();
            if (isSpider(site)) {
                String playerContent = site.recent().spider().playerContent(flag, id, VodConfig.get().getFlags());
                if (isHtml(playerContent)) {
                    site.setBlacklist();
                    return Result.empty();
                }
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
                return result;
            } else {
                Result result = new Result();
                result.setUrl(id);
                result.setFlag(flag);
                result.setHeader(site.getHeader());
                result.setPlayUrl(site.getPlayUrl());
                result.setParse(Sniffer.isVideoFormat(id) && result.getPlayUrl().isEmpty() ? 0 : 1);
                result.setUrl(Source.get().fetch(result));
                return result;
            }
        } catch (Throwable e) {
            return Result.empty();
        }
    }

    @NonNull
    public static Result searchContent(@NonNull Site site, @NonNull String keyword, boolean quick, @NonNull String page) {
        if (BaseLoader.get().getJarLoader().isError(site.getJar())) return Result.empty();
        try {
            boolean hasPage = !page.equals("1");
            if (isSpider(site)) {
                String searchContent = hasPage ? site.spider().searchContent(keyword, quick, page) : site.spider().searchContent(keyword, quick);
                if (TextUtils.isEmpty(searchContent) || searchContent.trim().equals("{}") || isHtml(searchContent)) {
                    if (isHtml(searchContent)) site.setBlacklist();
                    else site.decrementScore();
                    return Result.empty();
                }
                try {
                    Result result = Result.fromJson(searchContent);
                    result.setKey(site.getKey());
                    for (Vod vod : result.getList()) vod.setSite(site);
                    site.incrementScore();
                    return result;
                } catch (Throwable e) {
                    android.util.Log.e("SiteApi", "JSON Parse Error from site: " + site.getName());
                    site.decrementScore();
                    return Result.empty();
                }
            } else {
                ArrayMap<String, String> params = new ArrayMap<>();
                params.put("wd", keyword);
                params.put("quick", String.valueOf(quick));
                if (hasPage) params.put("pg", page);
                String searchContent = call(site, params);
                if (searchContent != null && (searchContent.contains("<!DOCTYPE") || searchContent.contains("<HTML") || searchContent.contains("Access Denied"))) {
                    site.setBlacklist();
                    return Result.empty();
                }
                if (TextUtils.isEmpty(searchContent) || searchContent.trim().equals("{}")) {
                    site.decrementScore();
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
            site.decrementScore();
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
