package com.fongmi.android.tv.api.loader;

import android.content.Context;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.Decoder;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class JarLoader {

    private final ConcurrentHashMap<String, DexClassLoader> loaders;
    private final ConcurrentHashMap<String, Method> methods;
    private final ConcurrentHashMap<String, Object> locks;
    private String recent;

    public JarLoader() {
        this.loaders = new ConcurrentHashMap<>();
        this.methods = new ConcurrentHashMap<>();
        this.locks = new ConcurrentHashMap<>();
    }

    public void clear() {
        this.loaders.clear();
        this.methods.clear();
        this.locks.clear();
        this.recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public DexClassLoader dex(String jar) {
        String key = Util.md5(jar);
        if (!loaders.containsKey(key)) parseJar(key, jar, null);
        return loaders.get(key);
    }

    private void load(String key, File file, String json) {
        if (Thread.interrupted()) return;
        try {
            if (file.exists() && file.length() > 0) {
                Log.d("JarLoader", "Loading JAR file: " + file.getAbsolutePath() + " (key: " + key + ")");
                if (file.canWrite()) file.setWritable(false, false);
                String cachePath = Path.jar().getAbsolutePath();
                DexClassLoader loader = new DexClassLoader(file.getAbsolutePath(), cachePath, cachePath, App.get().getClassLoader());
                invokeInit(loader, json);
                invokeProxy(key, loader);
                loaders.put(key, loader);
            }
        } catch (Throwable e) {
            Log.e("JarLoader", "Key: " + key + " Failed to load jar: " + file.getAbsolutePath(), e);
            loaders.remove(key);
            if (file.exists()) file.delete();
        }
    }

    private void invokeInit(DexClassLoader loader, String json) {
        try {
            // 🚀 1. 嘗試多個路徑將 JSON 注入到 SpiderDebug 全域單例中
            String[] debugClz = {"com.github.catvod.crawler.SpiderDebug", "com.github.catvod.spider.SpiderDebug", "com.github.catvod.utils.SpiderDebug"};
            for (String name : debugClz) {
                try {
                    Class<?> clzDebug = loader.loadClass(name);
                    Method methodDebug = clzDebug.getDeclaredMethod("init", String.class);
                    methodDebug.setAccessible(true);
                    methodDebug.invoke(null, json);
                    Log.d("JarLoader", "Successfully injected JSON into " + name + ".init()");
                    break;
                } catch (Throwable ignored) {}
            }

            Class<?> clz = loader.loadClass("com.github.catvod.spider.Init");
            Method[] methods = clz.getDeclaredMethods();

            // 🚀 2. 針對 Log 中出現的 i(String) 進行精準注入 (判斷是傳 URL 還是 JSON)
            try {
                Method method = clz.getDeclaredMethod("i", String.class);
                method.setAccessible(true);
                String url = VodConfig.getUrl();
                if (url.startsWith("http")) {
                    Log.d("JarLoader", "Invoking discovered JAR Init.i(String) with Config URL");
                    method.invoke(null, url);
                } else {
                    Log.d("JarLoader", "Invoking discovered JAR Init.i(String) with JSON");
                    method.invoke(null, json);
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable e) {
                Log.w("JarLoader", "Init.i(String) failed, likely mismatched expectation: " + e.getMessage());
            }

            // 🚀 3. 強制列出所有方法簽章，方便確認
            for (Method m : methods) {
                StringBuilder params = new StringBuilder();
                for (Class<?> p : m.getParameterTypes()) params.append(p.getSimpleName()).append(",");
                Log.d("JarLoader", "Init Method: " + m.getName() + "(" + params + ")");
            }

            // 1. 優先嘗試 init(Context, String)
            try {
                Method method = clz.getDeclaredMethod("init", Context.class, String.class);
                method.setAccessible(true);
                Log.d("JarLoader", "Invoking JAR init(Context, String)");
                method.invoke(null, App.get(), json);
                return;
            } catch (NoSuchMethodException ignored) {}

            // 2. 次要嘗試 init(Context, JSONObject)
            try {
                Method method = clz.getDeclaredMethod("init", Context.class, JSONObject.class);
                method.setAccessible(true);
                Log.d("JarLoader", "Invoking JAR init(Context, JSONObject)");
                method.invoke(null, App.get(), new JSONObject(json != null ? json : "{}"));
                return;
            } catch (NoSuchMethodException ignored) {}

            // 3. 最後嘗試標準 init(Context)
            try {
                Method method = clz.getDeclaredMethod("init", Context.class);
                method.setAccessible(true);
                Log.d("JarLoader", "Invoking JAR init(Context)");
                method.invoke(null, App.get());
            } catch (NoSuchMethodException e) {
                Log.e("JarLoader", "No valid init method found in JAR!");
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof org.json.JSONException) {
                Log.w("JarLoader", "JAR Init.init warned: JSON data is empty or invalid. This is usually safe to ignore.");
            } else {
                Log.e("JarLoader", "JAR Init.init target exception", cause);
            }
        } catch (Throwable e) {
            Log.e("JarLoader", "invokeInit failed", e);
        }
    }

    private void invokeProxy(String key, DexClassLoader loader) {
        try {
            Class<?> clz = loader.loadClass("com.github.catvod.spider.Proxy");
            Method method = clz.getMethod("proxy", Map.class);
            methods.put(key, method);
        } catch (Throwable e) {
            Log.e("JarLoader", "invokeProxy failed for key: " + key, e);
        }
    }

    private File download(String url, String md5) {
        try {
            File jar = Path.jar(url);
            if (jar.exists() && jar.length() > 0) {
                if (jar.canWrite()) jar.setWritable(false, false);
                return jar;
            }
            okhttp3.Response response = OkHttp.newCall(url).execute();
            if (response.isSuccessful()) {
                byte[] bytes = response.body().bytes();
                if (bytes.length > 0) {
                    if (jar.exists()) jar.delete();
                    File savedJar = Path.write(jar, bytes);
                    if (savedJar.exists()) savedJar.setWritable(false, false);
                    return savedJar;
                }
            }
            return jar;
        } catch (Exception e) {
            Log.e("JarLoader", "Download exception: " + url, e);
            return Path.jar(url);
        }
    }

    public synchronized void parseJar(String key, String jar, String json) {
        Log.d("JarLoader", "parseJar called with jar: " + jar + ", json length: " + (json != null ? json.length() : 0));
        if (loaders.containsKey(key)) return;
        if (jar.startsWith("assets")) jar = UrlUtil.convert(jar);
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            if (loaders.containsKey(key)) return;
            String[] texts = jar.split(";md5;");
            String md5 = texts.length > 1 ? texts[1].trim() : "";
            if (md5.startsWith("http")) md5 = OkHttp.string(md5).trim();
            jar = texts[0];
            if (!md5.isEmpty() && Util.equals(jar, md5)) {
                load(key, Path.jar(jar), json);
            } else if (jar.startsWith("img+")) {
                load(key, Decoder.getSpider(jar), json);
            } else if (jar.startsWith("http")) {
                load(key, download(jar, md5), json);
            } else if (jar.startsWith("file")) {
                load(key, Path.local(jar), json);
            } else if (!jar.isEmpty()) {
                parseJar(key, UrlUtil.convert(jar), json);
            }
        }
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        try {
            DexClassLoader loader = dex(jar);
            if (loader == null) return new SpiderNull();
            Spider spider = (Spider) loader.loadClass("com.github.catvod.spider." + api.split("csp_")[1]).newInstance();
            if (android.text.TextUtils.isEmpty(ext) || ext.equals("{}")) {
                Log.d("JarLoader", "getSpider with empty set:" + api);
                spider.init(App.get());
            } else {
                spider.init(App.get(), ext);
                Log.d("JarLoader", "getSpider :[" + api + "] ext [" + ext + "]");
            }

            return spider;
        } catch (Throwable e) {
            Log.e("JarLoader", "getSpider failed: " + api, e);
            return new SpiderNull();
        }
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        try {
            Class<?> clz = Objects.requireNonNull(loaders.get(recent)).loadClass("com.github.catvod.parser.Json" + key);
            Method method = clz.getMethod("parse", LinkedHashMap.class, String.class);
            return (JSONObject) method.invoke(null, jxs, url);
        } catch (Throwable e) {
            Log.e("JarLoader", "jsonExt failed for key: " + key, e);
            throw e;
        }
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        try {
            Class<?> clz = Objects.requireNonNull(loaders.get(recent)).loadClass("com.github.catvod.parser.Mix" + key);
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
