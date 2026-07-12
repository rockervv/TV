package com.fongmi.android.tv.api.loader;

import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class BaseLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> locks;
    private final JarLoader jarLoader;
    private final PyLoader pyLoader;
    private final JsLoader jsLoader;
    private String recent;

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    private BaseLoader() {
        this.spiders = new ConcurrentHashMap<>();
        this.locks = new ConcurrentHashMap<>();
        this.jarLoader = new JarLoader();
        this.pyLoader = new PyLoader();
        this.jsLoader = new JsLoader();
    }

    public void clear() {
        Task.execute(() -> {
            for (Spider spider : spiders.values()) App.execute(spider::destroy);
            jarLoader.clear();
            pyLoader.clear();
            jsLoader.clear();
            spiders.clear();
            locks.clear();
        });
    }

    public Spider getSpider(String key) {
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        if (!site.isEmpty()) return site.spider();
        if (!live.isEmpty()) return live.spider();
        if (spiders.containsKey(key)) return spiders.get(key);
        return new SpiderNull();
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        if (api == null || api.isEmpty()) return new SpiderNull();
        Site site = Site.find(key);
        if (site != null && site.isBlacklist()) return new SpiderNull();
        if (spiders.containsKey(key)) return spiders.get(key);
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (spiders.containsKey(key)) return spiders.get(key);
            Spider spider = new SpiderNull();
            if (api.startsWith("csp_")) spider = jarLoader.getSpider(key, api, ext, jar);
            else if (api.contains(".py")) spider = pyLoader.getSpider(key, api, ext);
            else if (api.contains(".js")) spider = jsLoader.getSpider(key, api, ext, jar);
            if (!(spider instanceof SpiderNull)) spiders.put(key, spider);
            return spider;
        }
    }

    public String getRecent() {
        return recent;
    }

    public void setRecent(String key, String api, String jar) {
        this.recent = key;
        if (api.startsWith("csp_")) jarLoader.setRecent(jar);
    }

    public Object[] proxyLocal(Map<String, String> params) {
        return Proxy.local(params);
    }

    public void parseJar(String jar, String json) {
        Log.d("BaseLoader", "Relaying parseJar to JarLoader for: " + jar);
        jarLoader.parseJar(Util.md5(jar), jar, json);
    }

    public DexClassLoader dex(String jar) {
        return jarLoader.dex(jar);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }
}
