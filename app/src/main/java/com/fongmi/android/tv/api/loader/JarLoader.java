package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
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
    private final ConcurrentHashMap<String, Throwable> failures;
    private volatile String recent;

    public JarLoader() {
        loaders = new ConcurrentHashMap<>();
        methods = new ConcurrentHashMap<>();
        spiders = new ConcurrentHashMap<>();
        failures = new ConcurrentHashMap<>();
    }

    public void clear() {
        for (Spider spider : spiders.values()) spider.destroy();
        loaders.clear();
        methods.clear();
        spiders.clear();
        failures.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    private void load(String key, File file) {
        try {
            if (!Path.exists(file) || !file.setReadOnly()) return;
            String cachePath = Path.jar().getAbsolutePath();
            DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
            invokeInit(loader);
            invokeProxy(key, loader);
            loaders.put(key, loader);
        } catch (Throwable e) {
            failures.put(key, e);
        }
    }

    private void invokeInit(DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            java.lang.reflect.Method getMethod = clz.getMethod("get");
            Object instance = getMethod.invoke(null);
            try {
                java.lang.reflect.Field f = clz.getDeclaredField("c");
                f.setAccessible(true);
                f.set(instance, App.get());
                return;
            } catch (Throwable ignored) {}
            
            for (java.lang.reflect.Field field : clz.getDeclaredFields()) {
                if (field.getType().isAssignableFrom(android.app.Application.class)) {
                    field.setAccessible(true);
                    field.set(instance, App.get());
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private void invokeProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable ignored) {}
    }

    public void parseJar(String key, String jar) {
        if (loaders.containsKey(key)) return;
        synchronized (key.intern()) {
            if (loaders.containsKey(key)) return;
            try {
                if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
                String[] texts = jar.split(";md5;");
                jar = texts[0];
                
                File file = Path.jar(jar);
                if (Path.exists(file)) {
                    load(key, file);
                } else if (jar.startsWith("http")) {
                    load(key, Download.create(jar, file).get());
                } else if (jar.startsWith("file")) {
                    load(key, Path.local(jar));
                }
            } catch (Throwable e) {
                SpiderDebug.log(e);
            }
        }
    }

    public DexClassLoader dex(String jar) {
        String key = Util.md5(jar);
        parseJar(key, jar);
        return loaders.get(key);
    }

    public void setFailure(String jar, Throwable e) {
        failures.put(Util.md5(jar), e);
    }

    public boolean isError(String jar) {
        return failures.containsKey(Util.md5(jar));
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        String jaKey = Util.md5(jar);
        String spKey = jaKey + key;
        
        // 🛠️ 使用雙重檢查鎖 + intern()，避免 ConcurrentHashMap 的桶鎖卡死 UI 執行緒
        Spider spider = spiders.get(spKey);
        if (spider != null) return spider;

        synchronized (spKey.intern()) {
            spider = spiders.get(spKey);
            if (spider != null) return spider;
            
            try {
                parseJar(jaKey, jar);
                DexClassLoader loader = loaders.get(jaKey);
                if (loader == null) return new SpiderNull();
                
                String apiName = api.split(";")[0].trim();
                if (apiName.startsWith("csp_")) apiName = apiName.substring(4);

                spider = (Spider) loader.loadClass("com.github.catvod.spider." + apiName).newInstance();
                spider.siteKey = key;
                spider.init(App.get(), ext);
                spiders.put(spKey, spider);
                return spider;
            } catch (Throwable e) {
                Spider error = new SpiderNull();
                spiders.put(spKey, error);
                return error;
            }
        }
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) return new JSONObject();
        Class<?> clz = loader.loadClass("com.github.catvod.parser.Json" + key);
        Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
        return (JSONObject) method.invoke(null, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        DexClassLoader loader = loaders.get(recent);
        if (loader == null) return new JSONObject();
        Class<?> clz = loader.loadClass("com.github.catvod.parser.Mix" + key);
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
            return null;
        }
    }
}
