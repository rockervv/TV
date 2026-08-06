package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Monitor;
import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class JsLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final Loader loader;
    private volatile String recent;

    public JsLoader() {
        spiders = new ConcurrentHashMap<>();
        loader = new Loader();
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        Module.get().clear();
        spiders.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        return spiders.computeIfAbsent(key, k -> {
            if (api == null || (!api.startsWith("http") && !api.startsWith("assets") && !api.contains("/"))) {
                android.util.Log.w("JsLoader", "Invalid JS API path, skipping initialization: " + api);
                return new SpiderNull();
            }
            android.util.Log.d("JsLoader", "Initializing JS Spider: " + key + " (API: " + api + ")");
            Monitor.start("Spider_Init_JS_" + key);
            try {
                dalvik.system.DexClassLoader dexLoader = BaseLoader.get().dex(jar);
                Spider spider = loader.spider(api, dexLoader);
                spider.siteKey = key;
                spider.init(App.get(), ext);
                android.util.Log.d("JsLoader", "Successfully initialized JS Spider: " + key);
                return spider;
            } catch (Throwable e) {
                android.util.Log.e("JsLoader", "Failed to initialize JS Spider: " + key, e);
                SpiderDebug.log(e);
                return new SpiderNull();
            } finally {
                Monitor.end("Spider_Init_JS_" + key);
            }
        });
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
