package com.fongmi.android.tv.api.config;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Monitor;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.InterruptedIOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class BaseConfig {
    public static final int VOD = 0;
    public static final int LIVE = 1;
    public static final int WALL = 2;

    private final AtomicInteger taskId = new AtomicInteger(0);

    protected List<Header> headers;
    protected List<Proxy> proxy;
    protected List<String> hosts;
    protected volatile Config config;
    protected boolean sync;
    private volatile Future<?> future;

    protected abstract String getTag();

    protected abstract Config defaultConfig();

    protected abstract void load(Config config) throws Throwable;

    protected abstract boolean isLoaded();

    public synchronized void ensureLoaded() {
        if (isLoaded()) return;
        if (future != null && !future.isDone()) {
            try {
                future.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Throwable ignored) {
            }
        }
        if (isLoaded()) return;
        try {
            if (config == null) config = defaultConfig();
            Server.get().start();
            load(config);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    protected void postEvent() {
        ConfigEvent.common();
    }

    public boolean needSync(String url) {
        Config c = getConfig();
        if (c == null || c.isEmpty() || TextUtils.isEmpty(url)) return true;
        return sync || url.equals(c.getUrl());
    }

    public Config getConfig() {
        return config == null ? defaultConfig() : config;
    }

    protected void setHeaders(List<Header> headers) {
        OkHttp.responseInterceptor().addAll(headers);
    }

    protected void setProxy(List<Proxy> proxy) {
        OkHttp.selector().addAll(proxy);
    }

    protected void setHosts(List<String> hosts) {
        OkHttp.dns().addAll(hosts);
    }

    public void load(Callback callback) {
        int id = taskId.incrementAndGet();
        if (future != null && !future.isDone()) future.cancel(true);
        future = Task.submit(() -> loadConfig(id, config, callback));
        callback.start();
    }

    protected void loadConfig(int id, Config config, Callback callback) {
        final Config localConfig = config;
        if (localConfig == null || localConfig.isEmpty()) {
            android.util.Log.d("TV_FATAL", "loadConfig() ABORTED - config is NULL or EMPTY for Tag: " + getTag());
            App.post(callback::success);
            return;
        }
        String monitorKey = "LoadConfig_" + getTag();
        android.util.Log.d("TV_FATAL", "loadConfig() START - Tag: " + getTag() + " URL: " + localConfig.getUrl());
        Monitor.start(monitorKey);
        try {
            android.util.Log.d("TV_FATAL", " loadConfig() calling Server.get().start()");
            Server.get().start();
            OkHttp.cancel(getTag());
            android.util.Log.d("TV_FATAL", " loadConfig() calling load(localConfig)");
            load(localConfig);
            if (taskId.get() != id) return;
            if (localConfig.equals(this.config)) localConfig.update();
            App.post(() -> Notify.show(localConfig.getNotice()));
            android.util.Log.d("TV_FATAL", "loadConfig() SUCCESS for Tag: " + getTag());
            App.post(callback::success);
        } catch (Throwable e) {
            android.util.Log.e("TV_FATAL", "loadConfig() ERROR - Tag: " + getTag() + ", Msg: " + e.getMessage(), e);
            if (isCanceled(e)) return;
            if (taskId.get() != id) return;
            if (TextUtils.isEmpty(localConfig.getUrl())) App.post(() -> callback.error(""));
            else App.post(() -> callback.error(Notify.getError(R.string.error_config_get, e)));
        } finally {
            Monitor.end(monitorKey);
            if (taskId.get() == id) postEvent();
            android.util.Log.d("TV_FATAL", "loadConfig() END - Tag: " + getTag());
        }
    }

    protected boolean isCanceled(Throwable e) {
        if ("Canceled".equals(e.getMessage())) return true;
        if (e instanceof InterruptedException) return true;
        if (e instanceof InterruptedIOException) return true;
        return e.getCause() instanceof InterruptedIOException;
    }

    protected JsonArray fetchArray(JsonObject object, String key) {
        if (!object.has(key)) return new JsonArray();
        JsonElement element = object.get(key);
        if (element.isJsonObject()) return new JsonArray();
        if (element.isJsonPrimitive()) element = fetch(element.getAsString());
        JsonArray result = new JsonArray();
        if (element.isJsonArray()) {
            for (JsonElement item : element.getAsJsonArray()) {
                if (item.isJsonPrimitive()) result.addAll(fetch(item.getAsString()));
                else if (item.isJsonObject()) result.add(item);
            }
        }
        return result;
    }

    public List<Header> getHeaders() {
        return headers == null ? Collections.emptyList() : headers;
    }

    public List<Proxy> getProxy() {
        return proxy == null ? Collections.emptyList() : proxy;
    }

    public List<String> getHosts() {
        return hosts == null ? Collections.emptyList() : hosts;
    }

    private JsonArray fetch(String url) {
        try {
            JsonElement parsed = Json.parse(OkHttp.string(UrlUtil.convert(url)));
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        } catch (Exception e) {
            return new JsonArray();
        }
    }
}
