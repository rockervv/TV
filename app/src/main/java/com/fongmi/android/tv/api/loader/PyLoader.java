package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.lang.reflect.Method;

public class PyLoader {

    private Method method;
    private Object loader;

    public PyLoader() {
        init();
    }

    public void clear() {
        this.loader = null;
        this.method = null;
    }

    private void init() {
        try {
            loader = Class.forName("com.fongmi.chaquo.Loader").newInstance();
            method = loader.getClass().getMethod("spider", String.class);
        } catch (Throwable e) {
            Log.e("PyLoader", "Failed to create Loader instance", e);
        }
    }

    public Spider getSpider(String key, String api, String ext) {
        try {
            if (loader == null) return new SpiderNull();
            Spider spider = (Spider) method.invoke(loader, api);
            if (android.text.TextUtils.isEmpty(ext) || ext.equals("{}")) {
                Log.d("PyLoader", "getSpider with empty set:" + api);
                spider.init(App.get());
            }
            else {
                spider.init(App.get(), ext);
                Log.d("PyLoader", "getSpider :[" + api + "] ext [" + ext + "]");
            }
            return spider;
        } catch (Throwable e) {
            Log.e("PyLoader", "getSpider failed for key: " + key + " api: " + api, e);
            return new SpiderNull();
        }
    }
}
