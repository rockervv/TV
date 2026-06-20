package com.fongmi.android.tv.api.loader;

import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Util;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class JarLoader {

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final ConcurrentHashMap<String, Spider> spiders;
    private String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
    }

    public void clear() {
        for (Spider spider : spiders.values()) App.execute(spider::destroy);
        loaders.clear();
        methods.clear();
        spiders.clear();
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    private void load(String key, File file) {
        try {
            if (file.exists() && file.length() > 0) {
                loaders.put(key, new DexClassLoader(file.getAbsolutePath(), Path.jar().getAbsolutePath(), null, App.get().getClassLoader()));
                invokeInit(key);
                putProxy(key);
            } else {
                Log.e("JarLoader", "File not found or empty: " + file.getAbsolutePath());
            }
        } catch (Throwable e) {
            Log.e("JarLoader", "Failed to load jar: " + file.getAbsolutePath(), e);
            loaders.remove(key);
            if (file.exists()) file.delete();
        }
    }

    private void invokeInit(String key) {
        try {
            DexClassLoader loader = loaders.get(key);
            if (loader == null) return;
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", android.content.Context.class);
            method.invoke(clz, App.get());
        } catch (Throwable e) {
            Log.e("JarLoader", "invokeInit failed for key: " + key, e);
        }
    }

    private void putProxy(String key) {
        try {
            DexClassLoader loader = loaders.get(key);
            if (loader == null) return;
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            Log.e("JarLoader", "putProxy failed for key: " + key, e);
        }
    }

    private File download(String url, String md5) {
        try {
            File jar = Path.jar(url);
            if (md5.length() > 0 && Util.equals(url, md5)) return jar;
            if (md5.isEmpty() && jar.exists() && jar.length() > 0) return jar;
            Log.d("JarLoader", "Downloading jar from: " + url);
            okhttp3.Response response = OkHttp.newCall(url).execute();
            if (response.isSuccessful()) {
                byte[] bytes = response.body().bytes();
                if (bytes.length > 0) {
                    Log.d("JarLoader", "Download success: " + url + " size: " + bytes.length);
                    return Path.write(jar, bytes);
                }
            }
            Log.e("JarLoader", "Download failed: " + url + " code: " + response.code());
            return jar;
        } catch (Exception e) {
            Log.e("JarLoader", "Download exception: " + url, e);
            return Path.jar(url);
        }
    }

    public synchronized void parseJar(String key, String jar) {
        if (loaders.containsKey(key)) return;
        String[] texts = jar.split(";md5;");
        String md5 = texts.length > 1 ? texts[1].trim() : "";
        jar = texts[0];
        Log.d("JarLoader", "Parsing jar: " + jar + " md5: " + md5);
        if (md5.length() > 0 && Util.equals(jar, md5)) {
            load(key, Path.jar(jar));
        } else if (jar.startsWith("img+")) {
            load(key, Decoder.getSpider(jar));
        } else if (jar.startsWith("http")) {
            load(key, download(jar, md5));
        } else if (jar.startsWith("file")) {
            load(key, Path.local(jar));
        } else if (jar.startsWith("assets")) {
            parseJar(key, UrlUtil.convert(jar));
        } else if (!jar.isEmpty()) {
            parseJar(key, UrlUtil.convert(jar));
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        try {
            String jaKey = Util.md5(jar);
            String spKey = jaKey + key;
            if (spiders.containsKey(spKey)) return spiders.get(spKey);
            if (!loaders.containsKey(jaKey)) parseJar(jaKey, jar);
            DexClassLoader loader = loaders.get(jaKey);
            if (loader == null) {
                Log.e("JarLoader", "DexClassLoader is null for jaKey: " + jaKey);
                return new SpiderNull();
            }
            Spider spider = (Spider) loader.loadClass("com.github.catvod.spider." + api.split("csp_")[1]).newInstance();
            spider.init(App.get(), ext);
            spiders.put(spKey, spider);
            return spider;
        } catch (Throwable e) {
            Log.e("JarLoader", "getSpider failed: " + api, e);
            return new SpiderNull();
        }
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        try {
            Class<?> clz = loaders.get(recent).loadClass("com.github.catvod.parser.Json" + key);
            Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
            return (JSONObject) method.invoke(null, jxs, url);
        } catch (Throwable e) {
            Log.e("JarLoader", "jsonExt failed for key: " + key, e);
            throw e;
        }
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        try {
            Class<?> clz = loaders.get(recent).loadClass("com.github.catvod.parser.Mix" + key);
            Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
            return (JSONObject) method.invoke(null, jxs, name, flag, url);
        } catch (Throwable e) {
            Log.e("JarLoader", "jsonExtMix failed for key: " + key, e);
            throw e;
        }
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            Method method = methods.get(Util.md5(recent));
            if (method == null) return null;
            return (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            Log.e("JarLoader", "proxyInvoke failed", e);
            return null;
        }
    }
}
