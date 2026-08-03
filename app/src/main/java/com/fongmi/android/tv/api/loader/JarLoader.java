package com.fongmi.android.tv.api.loader;

import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.Monitor;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderDebug;
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class JarLoader {

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final ConcurrentHashMap<String, Spider> spiders;
    private final ConcurrentHashMap<String, Object> locks;
    private final ConcurrentHashMap<String, Throwable> failures;
    private volatile String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
        locks = new ConcurrentHashMap<>();
        failures = new ConcurrentHashMap<>();
    }

    public void clear() {
        spiders.values().forEach(Spider::destroy);
        loaders.clear();
        methods.clear();
        spiders.clear();
        locks.clear();
        failures.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    private void load(String key, File file) {
        try {
            if (Thread.interrupted()) return;
            if (!Path.exists(file) || !file.setReadOnly()) return;
            String cachePath = Path.jar().getAbsolutePath();
            DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
            invokeInit(loader);
            invokeProxy(key, loader);
            loaders.put(key, loader);
        } catch (Throwable e) {
            failures.put(key, e);
            SpiderDebug.log(e);
        }
    }

    private void invokeInit(DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method method = clz.getMethod("init", Context.class);
            method.invoke(clz, App.get());
        } catch (Throwable e) {
            SpiderDebug.log(e);
        }
    }

    private void invokeProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            SpiderDebug.log(e);
        }
    }

    public void parseJar(String key, String jar) {
        if (loaders.containsKey(key) || failures.containsKey(key)) return;
        Monitor.start("Spider_ParseJar_" + key);
        try {
            if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
            Object lock = locks.computeIfAbsent(key, k -> new Object());
            synchronized (lock) {
                if (loaders.containsKey(key) || failures.containsKey(key)) return;
                String[] texts = jar.split(";md5;");
                String md5 = texts.length > 1 ? texts[1].trim() : "";
                if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
                jar = texts[0];
                if (!md5.isEmpty() && Util.equals(jar, md5)) {
                    load(key, Path.jar(jar));
                } else if (jar.startsWith("http")) {
                    File file = Path.jar(jar);
                    if (Path.exists(file)) load(key, file);
                    else load(key, Download.create(jar, file).get());
                } else if (jar.startsWith("file")) {
                    load(key, Path.local(jar));
                }
            }
        } catch (Throwable e) {
            failures.put(key, e);
            SpiderDebug.log(e);
            if (e instanceof VerifyError) {
                File file = Path.jar(jar);
                if (file.exists()) file.delete();
            }
        } finally {
            Monitor.end("Spider_ParseJar_" + key);
        }
    }

    public DexClassLoader dex(String jar) {
        try {
            String jaKey = Util.md5(jar);
            parseJar(jaKey, jar);
            return loaders.get(jaKey);
        } catch (Throwable e) {
            SpiderDebug.log(e);
            return null;
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Util.md5(jar);
        if (failures.get(jaKey) instanceof VerifyError) {
            android.util.Log.e("JarLoader", "VerifyError detected for JAR, skipping load. [Key: " + key + ", API: " + api + ", EXT: " + ext + ", JAR: " + jar + "]");
            return new SpiderNull();
        }
        String spKey = jaKey + key;
        return spiders.computeIfAbsent(spKey, k -> {
            Monitor.start("Spider_Init_CSP_" + key);
            try {
                parseJar(jaKey, jar);
                if (failures.get(jaKey) instanceof VerifyError) {
                    android.util.Log.e("JarLoader", "VerifyError detected during parseJar, skipping initialization. [Key: " + key + ", API: " + api + ", EXT: " + ext + ", JAR: " + jar + "]");
                    return new SpiderNull();
                }
                DexClassLoader loader = loaders.get(jaKey);
                if (loader == null) return new SpiderNull();
                String apiName = api.split("csp_").length > 1 ? api.split("csp_")[1] : api;
                Spider spider = (Spider) loader.loadClass("com.github.catvod.spider." + apiName).newInstance();
                spider.siteKey = key;
                spider.init(App.get(), ext);
                return spider;
            } catch (Throwable e) {
                if (e instanceof VerifyError) {
                    android.util.Log.e("JarLoader", "VerifyError caught during class load/init. [Key: " + key + ", API: " + api + ", EXT: " + ext + ", JAR: " + jar + "]", e);
                }
                failures.put(jaKey, e);
                SpiderDebug.log(e);
                return new SpiderNull();
            } finally {
                Monitor.end("Spider_Init_CSP_" + key);
            }
        });
    }

    private DexClassLoader requireRecentLoader() {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) throw new IllegalStateException("No jar loaded for recent key: " + recent);
        return loader;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        return (JSONObject) method.invoke(null, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        Class<?> clz = requireRecentLoader().loadClass("com.github.catvod.parser.Mix" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
        return (JSONObject) method.invoke(null, jxs, name, flag, url);
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Method method = recent != null ? methods.get(recent) : null;
        Object[] result = proxyInvoke(method, params);
        if (result != null) return result;
        return tryOthers(params);
    }

    private Object[] tryOthers(Map<String, String> p) {
        return methods.entrySet().stream().filter(e -> !e.getKey().equals(recent)).map(e -> proxyInvoke(e.getValue(), p)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Object[] proxyInvoke(Method method, Map<String, String> params) {
        try {
            return method == null ? null : (Object[]) method.invoke(null, params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
