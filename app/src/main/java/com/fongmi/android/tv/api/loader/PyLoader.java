package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private Object loader;
    private String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
        init();
    }

    public void clear() {
        for (Spider spider : spiders.values()) App.execute(spider::destroy);
        spiders.clear();
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    private void init() {
        try {
            loader = Class.forName("com.undcover.freedom.pyramid.Loader").newInstance();
            Log.d("PyLoader", "Loader instance created: " + loader.getClass().getName());
        } catch (Throwable e) {
            Log.e("PyLoader", "Failed to create Loader instance", e);
            loader = null;

        }
    }

    public Spider getSpider(String key, String api, String ext) {
        try {
            if (loader == null) {
                Log.e("PyLoader", "Loader is null, cannot get spider: " + api);
                return new SpiderNull();
            }

            if (spiders.containsKey(key)) return spiders.get(key);
            Method method = loader.getClass().getMethod("spider", Context.class, String.class);
            Spider spider = (Spider) method.invoke(loader, App.get(), api);
            Objects.requireNonNull(spider).init(App.get(), ext);
            spiders.put(key, spider);
            Site site = Site.find(key);
            if (site != null) site.resetFailures();
            return spider;
        } catch (Throwable e) {
            Log.e("PyLoader", "getSpider failed for key: " + key + " api: " + api, e);
            Site site = Site.find(key);
            if (site != null) site.setBlacklist();
            return new SpiderNull();
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (!params.containsKey("siteKey")) return Objects.requireNonNull(spiders.get(recent)).proxyLocal(params);
            return BaseLoader.get().getSpider(params).proxyLocal(params);
        } catch (Throwable e) {
            Log.e("PyLoader", "proxyInvoke failed", e);
            return null;
        }
    }
}
