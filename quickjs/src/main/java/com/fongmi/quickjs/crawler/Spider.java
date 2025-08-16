package com.fongmi.quickjs.crawler;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.fongmi.quickjs.bean.Res;
import com.fongmi.quickjs.method.Async;
import com.fongmi.quickjs.method.Console;
import com.fongmi.quickjs.method.Global;
import com.fongmi.quickjs.method.Local;
import com.fongmi.quickjs.utils.JSUtil;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.UriUtil;
import com.github.catvod.utils.Util;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;



public class Spider extends com.github.catvod.crawler.Spider {

    private static final ExecutorService JS_EXECUTOR = Executors.newSingleThreadExecutor();

    private QuickJSContext ctx;
    private JSObject jsObject;
    private final String key;
    private final String api;
    private boolean cat;

    public Spider(String key, String api) throws Exception {
        this.key = key;
        this.api = api;
        initializeJS();
    }

    private void runSync(Runnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        JS_EXECUTOR.submit(() -> {
            try {
                task.run();
            } finally {
                latch.countDown();
            }
        });
        latch.await();
    }

    private <T> T runSync(Callable<T> task) throws Exception {
        Future<T> future = JS_EXECUTOR.submit(task);
        return future.get();
    }

    private Object call(String func, Object... args) throws Exception {
        return runSync(() -> Async.run(jsObject, func, args)).get();
    }

    @Override
    public void init(Context context, String extend) throws Exception {
        if (cat) call("init", runSync(() -> cfg(extend)));
        else call("init", Json.valid(extend) ? runSync(() -> ctx.parse(extend)) : extend);
    }

    @Override public String homeContent(boolean filter) throws Exception { return (String) call("home", filter); }
    @Override public String homeVideoContent() throws Exception { return (String) call("homeVod"); }
    @Override public String detailContent(List<String> ids) throws Exception { return (String) call("detail", ids.get(0)); }
    @Override public String searchContent(String key, boolean quick) throws Exception { return (String) call("search", key, quick); }
    @Override public String searchContent(String key, boolean quick, String pg) throws Exception { return (String) call("search", key, quick, pg); }
    @Override public String liveContent() throws Exception { return (String) call("live"); }
    @Override public boolean manualVideoCheck() throws Exception { return (Boolean) call("sniffer"); }
    @Override public boolean isVideoFormat(String url) throws Exception { return (Boolean) call("isVideo", url); }
    @Override public String action(String action) throws Exception { return (String) call("action", action); }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        JSObject obj = runSync(() -> JSUtil.toObj(ctx, extend));
        return (String) call("category", tid, pg, filter, obj);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        JSArray array = runSync(() -> JSUtil.toArray(ctx, vipFlags));
        return (String) call("play", flag, id, array);
    }

    @Override
    public Object[] proxyLocal(Map<String, String> params) throws Exception {
        if ("catvod".equals(params.get("from"))) return proxy2(params);
        return runSync(() -> proxy1(params));
    }

    @Override
    public void destroy() {
        try {
            call("destroy");
        } catch (Throwable e) {
            e.printStackTrace();
        }
        try {
            runSync(() -> {
                    if (jsObject != null) jsObject.release();
                    if (ctx != null) ctx.destroy();
            });
        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    private void initializeJS() throws Exception {
        runSync(() -> {
            try {
                createCtx();
                createObj();
            } catch (Exception e) {
                throw new RuntimeException(e);  // 或 Log.e(...) 根據你的需求處理
            }
        });
    }

    private void createCtx() {
        ctx = QuickJSContext.create();
        ctx.setConsole(new Console());

        Global.create(ctx, JS_EXECUTOR).setProperty();
        ctx.getGlobalObject().setProperty("local", Local.class);




        ctx.evaluate(Asset.read("js/lib/http.js"));
        Global.create(ctx, JS_EXECUTOR).setProperty();
        ctx.getGlobalObject().setProperty("local", Local.class);
        ctx.setModuleLoader(new QuickJSContext.BytecodeModuleLoader() {
            @Override
            public String moduleNormalizeName(String baseModuleName, String moduleName) {
                return UriUtil.resolve(baseModuleName, moduleName);
            }
            @Override
            public byte[] getModuleBytecode(String moduleName) {
                String content = Module.get().fetch(moduleName);
                return content.startsWith("//bb") ? Module.get().bb(content) : ctx.compileModule(content, moduleName);
            }
        });
    }

    private void createObj() {
        String spider = "__JS_SPIDER__";
        String global = "globalThis." + spider;
        String content = Module.get().fetch(api);

        //Log.d("SpiderDebug", "API: " + api + "\nFetched content (head): " + content.substring(0, Math.min(content.length(), 200)));

        boolean bb = content.startsWith("//bb");
        cat = bb || content.contains("__jsEvalReturn");

        if (!bb) ctx.evaluateModule(content.replace(spider, global), api);
        ctx.evaluateModule(String.format(Asset.read("js/lib/spider.js"), api));
        jsObject = (JSObject) ctx.getProperty(ctx.getGlobalObject(), spider);
    }

    private JSObject cfg(String ext) {
        JSObject cfg = ctx.createNewJSObject();
        cfg.setProperty("stype", 3);
        cfg.setProperty("skey", key);
        if (Json.invalid(ext)) cfg.setProperty("ext", ext);
        else cfg.setProperty("ext", (JSObject) ctx.parse(ext));
        return cfg;
    }

    private Object[] proxy1(Map<String, String> params) throws Exception {
        JSObject object = JSUtil.toObj(ctx, params);
        JSONArray array = new JSONArray(((JSArray) jsObject.getJSFunction("proxy").call(object)).stringify());
        Map<String, String> headers = array.length() > 3 ? Json.toMap(array.optString(3)) : null;
        boolean base64 = array.length() > 4 && array.optInt(4) == 1;
        Object[] result = new Object[4];
        result[0] = array.optInt(0);
        result[1] = array.optString(1);
        result[2] = getStream(array.opt(2), base64);
        result[3] = headers;
        return result;
    }

    private Object[] proxy2(Map<String, String> params) throws Exception {
        String url = params.get("url");
        String header = params.get("header");
        JSArray array = runSync(() -> JSUtil.toArray(ctx, Arrays.asList(url.split("/"))));
        Object object = runSync(() -> ctx.parse(header));
        String json = (String) call("proxy", array, object);
        Res res = Res.objectFrom(json);
        return new Object[]{ res.getCode(), res.getContentType(), res.getStream() };
    }

    private ByteArrayInputStream getStream(Object o, boolean base64) {
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            byte[] bytes = new byte[a.length()];
            for (int i = 0; i < a.length(); i++) bytes[i] = (byte) a.optInt(i);
            return new ByteArrayInputStream(bytes);
        } else {
            String content = o.toString();
            if (base64 && content.contains("base64,")) content = content.split("base64,")[1];
            return new ByteArrayInputStream(base64 ? Util.decode(content) : content.getBytes());
        }
    }
}
