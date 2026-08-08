package com.fongmi.android.tv.api;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.bean.HistorySyncManager;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.HashMap;
import java.util.TreeMap;

public class CacheManager {

    private static final String TAG = "CacheManager";

    public static File getFile(String api) {
        return getFile(api, "home", "1", "");
    }

    public static String getExtHash(String extend) {
        if (TextUtils.isEmpty(extend)) return "";
        return com.github.catvod.utils.Util.md5(extend).substring(0, 8);
    }

    public static String getExtHash(HashMap<String, String> extend) {
        if (extend == null || extend.isEmpty()) return "";
        // 🛠️ 使用 TreeMap 確保 Key 排序，產生唯一的 JSON 與 Hash
        TreeMap<String, String> sorted = new TreeMap<>(extend);
        String json = com.fongmi.android.tv.App.gson().toJson(sorted);
        return getExtHash(json);
    }

    public static File getFile(String key, String tid, String page, String extHash) {
        String name = com.github.catvod.utils.Util.md5(key);
        if (TextUtils.isEmpty(extHash)) {
            return Path.files(String.format("cache_%s_%s_%s.json", name, tid, page));
        } else {
            return Path.files(String.format("cache_%s_%s_%s_%s.json", name, tid, page, extHash));
        }
    }

    public static Result get(Site site) {
        return get(site, "home", "1", getExtHash(site.getExt()));
    }

    public static Result get(Site site, String tid, String page, String extHash) {
        try {
            File local = getFile(site.getKey(), tid, page, extHash);
            if (local.exists()) {
                Log.d(TAG, "Loading local cache for " + site.getName() + " [" + tid + "] Hash: " + extHash);
                String data = Path.read(local);
                if (!data.isEmpty()) return Result.fromJson(data);
            }
            // 雲端同步僅針對第一頁與首頁
            if (page.equals("1")) {
                Log.d(TAG, "Local cache not found for " + site.getName() + ", checking cloud...");
                String cloudData = HistorySyncManager.downloadCache(local.getName());
                if (cloudData != null && !cloudData.isEmpty()) {
                    Log.d(TAG, "Loading cloud cache for " + site.getName());
                    Path.write(local, cloudData);
                    return Result.fromJson(cloudData);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Load cache error", e);
        }
        return null;
    }

    public static void put(Site site, Result result) {
        put(site, "home", "1", getExtHash(site.getExt()), result);
    }

    public static void put(Site site, String tid, String page, String extHash, Result result) {
        if (result == null || (result.getTypes().isEmpty() && result.getList().isEmpty())) return;
        try {
            File local = getFile(site.getKey(), tid, page, extHash);
            String json = result.toString();
            Path.write(local, json);
            // 僅同步第一頁與無篩選的首頁到雲端
            if (page.equals("1") && TextUtils.isEmpty(extHash)) {
                HistorySyncManager.uploadCache(local.getName(), json);
            }
        } catch (Exception e) {
            Log.e(TAG, "Save cache error", e);
        }
    }
}
