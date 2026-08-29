package com.fongmi.android.tv;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;

import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.player.util.ADFilter;
import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.bean.RemoteSyncManager;
import com.fongmi.android.tv.utils.LanguageUtil;
import com.fongmi.android.tv.utils.Monitor;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.hook.Hook;
import com.github.catvod.Init;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.google.gson.Gson;
import com.orhanobut.logger.AndroidLogAdapter;
import com.orhanobut.logger.LogAdapter;
import com.orhanobut.logger.Logger;
import com.orhanobut.logger.PrettyFormatStrategy;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
        System.setProperty("net.java.preferIPv4Stack", "true");
        System.setProperty("net.java.preferIPv6Addresses", "false");
        System.setProperty("org.jupnp.network.useIPv4Names", "true");
        System.setProperty("org.jupnp.network.useIPv6Names", "false");
    }

    private final Handler handler;
    private final ExecutorService executor;
    @SuppressLint("StaticFieldLeak")
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private Hook hook;
    private final long time;

    public App() {
        instance = this;
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
        executor = Executors.newFixedThreadPool(Constant.THREAD_POOL);
        time = System.currentTimeMillis();
        gson = new Gson();
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static void execute(Runnable runnable) {
        get().executor.execute(runnable);
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    private LogAdapter getLogAdapter() {
        return new AndroidLogAdapter(PrettyFormatStrategy.newBuilder().methodCount(0).showThreadInfo(false).tag("").build()) {
            @Override
            public boolean isLoggable(int priority, String tag) {
                return true;
            }
        };
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Init.set(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 🛡️ 追蹤 Hidden API 與反射調用的神器
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setVmPolicy(new android.os.StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectUntaggedSockets()
                    .penaltyLog()
                    .build());
        }

        try {
            java.lang.reflect.Field field = android.database.CursorWindow.class.getDeclaredField("sCursorWindowSize");
            field.setAccessible(true);
            field.set(null, 5 * 1024 * 1024);
        } catch (Exception ignored) {
        }
        Monitor.start("App_onCreate");
        setupExceptionHandler();
        Notify.createChannel();
        LanguageUtil.init(this);
        registerActivityLifecycleCallbacks(this);
        try {
            initTools();
        } catch (Throwable e) {
            Log.d("TV_FATAL", "initTools Error: " + e.getMessage());
        }
        Monitor.end("App_onCreate");
    }

    private void setupExceptionHandler() {
        CaocConfig.Builder.create()
                .backgroundMode(CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM)
                .errorActivity(CrashActivity.class)
                .apply();

        Thread.UncaughtExceptionHandler caocHandler = Thread.getDefaultUncaughtExceptionHandler();
        
        // 🛠️ 確保 UI 執行緒崩潰時也能精確導向 CrashActivity，防止黑屏掛起
        new Handler(Looper.getMainLooper()).post(() -> {
            while (true) {
                try {
                    Looper.loop();
                } catch (Throwable e) {
                    if (isSpiderError(e)) {
                        Log.e("SpiderWatcher", "Intercepted UI thread spider error: " + e.getMessage());
                    } else if (caocHandler != null) {
                        caocHandler.uncaughtException(Thread.currentThread(), e);
                    }
                }
            }
        });

        // 🛠️ 其他執行緒的崩潰攔截
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (isSpiderError(e)) {
                Log.e("SpiderWatcher", "Intercepted background thread spider error [" + t.getName() + "]: " + e.getMessage());
            } else if (caocHandler != null) {
                caocHandler.uncaughtException(t, e);
            }
        });
    }

    private void initTools() {
        Logger.addLogAdapter(getLogAdapter());
        SpiderDebug.init();
        OkHttp.get().setProxy(Setting.getProxy());
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()));
        ADFilter.initListener();
    }

    private boolean isSpiderError(Throwable e) {
        if (e == null) return false;
        String msg = String.valueOf(e.getMessage());
        if (e instanceof org.json.JSONException && (msg.contains("<html>") || msg.contains("<HTML>") || msg.contains("<!DOCTYPE"))) return true;
        
        // 🛠️ 強化檢查：如果是 NPE 且發生在 App Core 邏輯中，優先視為 App 錯誤而非 Spider 錯誤
        if (e instanceof NullPointerException) {
            for (StackTraceElement element : e.getStackTrace()) {
                if (element.getClassName().startsWith("com.fongmi.android.tv.ui") || 
                    element.getClassName().startsWith("com.fongmi.android.tv.player")) return false;
            }
        }

        for (StackTraceElement element : e.getStackTrace()) {
            if (element.getClassName().contains("com.github.catvod.spider")) return true;
            if (element.getClassName().contains("com.fongmi.quickjs.crawler")) return true;
            if (element.getClassName().contains("com.github.catvod.parser")) return true;
        }
        Throwable cause = e.getCause();
        return isSpiderError(cause);
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }
}
