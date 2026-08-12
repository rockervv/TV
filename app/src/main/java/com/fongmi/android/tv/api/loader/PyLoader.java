package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Monitor;
import com.fongmi.chaquo.Loader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private Loader loader;
    private volatile String recent;

    public PyLoader() {
        spiders = new ConcurrentHashMap<>();
    }

    private Loader getLoader() {
        if (loader == null) loader = new Loader();
        return loader;
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        spiders.clear();
        loader = null;
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        if (!com.fongmi.android.tv.setting.Setting.isChaquo()) {
            android.util.Log.w("PyLoader", "Python is disabled in settings. Skipping: " + key);
            return new SpiderNull("Python 引擎已關閉");
        }
        
        Spider spider = spiders.get(key);
        if (spider != null) return spider;

        synchronized (key.intern()) {
            spider = spiders.get(key);
            if (spider != null) return spider;
            
            Monitor.start("Spider_Init_PY_" + key);
            try {
                spider = getLoader().spider(api);
                spider.siteKey = key;
                spider.siteName = com.fongmi.android.tv.api.config.VodConfig.get().getSite(key).getName();
                spider.init(App.get(), ext);
                spiders.put(key, spider);
                return spider;
            } catch (Throwable e) {
                SpiderDebug.log(e);
                Spider error = new SpiderNull();
                spiders.put(key, error);
                return error;
            } finally {
                Monitor.end("Spider_Init_PY_" + key);
            }
        }
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
