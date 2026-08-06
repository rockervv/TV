package com.fongmi.android.tv.api;

import android.util.Log;

import com.fongmi.android.tv.bean.HistorySyncManager;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Path;

import java.io.File;

public class CacheManager {

    private static final String TAG = "CacheManager";

    public static File getFile(String api) {
        return getFile(api, "home", "1");
    }

    public static File getFile(String api, String tid, String page) {
        return Path.files(String.format("cache_%s_%s_%s.json", api, tid, page));
    }

    public static Result get(Site site) {
        return get(site, "home", "1");
    }

    public static Result get(Site site, String tid, String page) {
        try {
            File local = getFile(site.getApi(), tid, page);
            if (local.exists()) {
                Log.d(TAG, "Loading local cache for " + site.getName() + " [" + tid + "]");
                String data = Path.read(local);
                if (!data.isEmpty()) return Result.fromJson(data);
            }
            // 雲端同步僅針對第一頁與首頁，避免流量過大
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
            Log.e(TAG, "Load cache error: " + e.getMessage());
        }
        return null;
    }

    public static void put(Site site, Result result) {
        put(site, "home", "1", result);
    }

    public static void put(Site site, String tid, String page, Result result) {
        if (result == null || (result.getTypes().isEmpty() && result.getList().isEmpty())) return;
        try {
            File local = getFile(site.getApi(), tid, page);
            String json = result.toString();
            Path.write(local, json);
            // 僅同步第一頁到雲端
            if (page.equals("1")) {
                HistorySyncManager.uploadCache(local.getName(), json);
            }
        } catch (Exception e) {
            Log.e(TAG, "Save cache error: " + e.getMessage());
        }
    }
}
