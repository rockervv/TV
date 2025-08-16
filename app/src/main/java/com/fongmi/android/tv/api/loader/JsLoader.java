package com.fongmi.android.tv.api.loader;

import android.util.Log;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private String recent;

    public JsLoader() {
        spiders = new ConcurrentHashMap<>();
    }

    public void clear() {
        for (Spider spider : spiders.values()) App.execute(spider::destroy);
        spiders.clear();
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        try {
            Log.d ("GetSpider", "Key: " + key + " Load:" + api + " ext: " + ext);
            if (spiders.containsKey(key)) return spiders.get(key);
            Spider spider = new com.fongmi.quickjs.crawler.Spider(key, api);
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
            Log.d("JsLoader", "proxyInvoke called with params: " + params);

            if (!params.containsKey("siteKey")) {
                Log.d("JsLoader", "No siteKey, use recent spider: " + recent);
                Object[] result = spiders.get(recent).proxyLocal(params);
                Log.d("JsLoader", "proxyLocal result: " + (result != null ? result.length + " items" : "null"));
                return result;
            }
            Log.d("JsLoader", "Has siteKey, use BaseLoader.get().getSpider(params)");
            Object[] result = BaseLoader.get().getSpider(params).proxyLocal(params);
            Log.d("JsLoader", "proxyLocal result: " + (result != null ? result.length + " items" : "null"));
            return result;
/**
            if (!params.containsKey("siteKey")) return spiders.get(recent).proxyLocal(params);
            return BaseLoader.get().getSpider(params).proxyLocal(params);
 */
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
