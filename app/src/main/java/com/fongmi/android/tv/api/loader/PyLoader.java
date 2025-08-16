package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.lang.reflect.Method;
import java.util.Map;
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
            if (loader == null) {
                Log.d("PyLoader", "Loader instance is null after initialization!");
            } else {
                Log.d("PyLoader", "Loader instance created: " + loader.getClass().getName());
            }
        } catch (Throwable e) {
            Log.e("PyLoader", "Failed to create Loader instance", e);
            loader = null;

        }
    }

    public Spider getSpider(String key, String api, String ext) {
        try {
            if (loader == null) {
                Log.d("PyLoader", "Loader is null, cannot get spider: " + api);
                return new SpiderNull();
            }

            if (spiders.containsKey(key)) return spiders.get(key);
            Method method = loader.getClass().getMethod("spider", Context.class, String.class);
            Spider spider = (Spider) method.invoke(loader, App.get(), api);
            spider.init(App.get(), ext);
            spiders.put(key, spider);
            return spider;
        } catch (Throwable e) {
            e.printStackTrace();
            return new SpiderNull();
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (!params.containsKey("siteKey")) return spiders.get(recent).proxyLocal(params);
            return BaseLoader.get().getSpider(params).proxyLocal(params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
