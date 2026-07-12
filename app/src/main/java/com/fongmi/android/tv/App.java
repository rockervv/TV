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

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.player.util.ADFilter;
import com.fongmi.android.tv.ui.activity.CrashActivity;
import com.fongmi.android.tv.bean.HistorySyncManager;
import com.fongmi.android.tv.utils.LanguageUtil;
import com.fongmi.android.tv.utils.Notify;
import com.github.catvod.Init;
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

public class App extends Application {

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
        System.setProperty("net.java.preferIPv4Stack", "true");
        System.setProperty("net.java.preferIPv6Addresses", "false");
        System.setProperty("org.jupnp.network.useIPv4Names", "true");
        System.setProperty("org.jupnp.network.useIPv6Names", "false");
    }

    private final ExecutorService executor;
    private final Handler handler;
    @SuppressLint("StaticFieldLeak")
    private static App instance;
    private Activity activity;
    private final Gson gson;
    private boolean hook;

    public App() {
        instance = this;
        executor = Executors.newFixedThreadPool(Constant.THREAD_POOL);
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
        gson = new Gson();
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
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

    public void setHook(boolean hook) {
        this.hook = hook;
    }

    private void setActivity(Activity activity) {
        this.activity = activity;
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
        setupExceptionHandler();
        Notify.createChannel();
        LanguageUtil.init(this);
        Logger.addLogAdapter(getLogAdapter());
        OkHttp.get().setProxy(Setting.getProxy());
        OkHttp.get().setDoh(Doh.objectFrom(Setting.getDoh()));
        System.setProperty("sun.net.client.defaultConnectTimeout", "5000");
        System.setProperty("sun.net.client.defaultReadTimeout", "5000");

        //HistorySyncManager.init(Setting.getFtpUri(), Setting.getFtpUsername(), Setting.getFtpPassword());
        HistorySyncManager.init(Setting.getFtpUri(), Setting.getFtpUsername(), Setting.getFtpPassword(), Setting.isUseFtp());
        HistorySyncManager.initGist( Setting.getGistUrl(),  Setting.getGistToken(), Setting.isUseGist());
        HistorySyncManager.SyncAll();
        //new SyncTask().execute();

        CaocConfig.Builder.create().backgroundMode(CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM).errorActivity(CrashActivity.class).apply();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                if (activity != activity()) setActivity(activity);
            }

            @Override
            public void onActivityPaused(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                if (activity == activity()) setActivity(null);
            }

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
            }
        });

        ADFilter.initListener();

        // 🚀 終極魔改大招：App 自我迴環網路廣播喚醒 (繞過 Android 14 背景 CEC 限制)
        execute(() -> {
            try {
                // 延遲 1 秒執行，確保 App 網路元件已完全就緒
                Thread.sleep(1000);

                // 1. 建立標準的 SSDP (UPnP/DLNA 喚醒) M-SEARCH 廣播字串
                String ssdpPacket = "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 1\r\n" +
                        "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n";

                byte[] sendData = ssdpPacket.getBytes();

                // 2. 對本地迴環地址 (127.0.0.1) 與 標準 UPnP 廣播地址發射魔術包
                java.net.DatagramSocket socket = new java.net.DatagramSocket();
                socket.setBroadcast(true);

                // 向自己發射，強迫系統網路層喚醒硬體
                java.net.DatagramPacket localPacket = new java.net.DatagramPacket(sendData, sendData.length, java.net.InetAddress.getByName("127.0.0.1"), 1900);
                socket.send(localPacket);

                // 同步向同網域群播發射，雙重保險
                java.net.DatagramPacket multicastPacket = new java.net.DatagramPacket(sendData, sendData.length, java.net.InetAddress.getByName("239.255.255.250"), 1900);
                socket.send(multicastPacket);

                socket.close();
                android.util.Log.e("App", "🛡️ [Self-Loopback] App 已成功向自己發射網路喚醒魔術包，強制逼迫系統發動實體 CEC！");
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });

/**
        ADFilter.setM3U8ParseListener(new ADFilter.M3U8ParseListener() {
            private int lastCount = 0;
            private double lastSeconds = 0;
            private long lastTime = 0;

            @Override
            public void onAdSegmentsFiltered(int adCount, double adSeconds) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    long currentTime = System.currentTimeMillis();
                    // 如果跟上次過濾的數量/時間一樣，或間隔小於 10 分鐘，就不重複提示
                    if (adCount == lastCount && Math.abs(adSeconds - lastSeconds) < 0.1 || (currentTime - lastTime) < 600000) {
                        return;
                    }
                    
                    if (adCount > 0) {
                        Notify.showTop("過濾 " + adCount + " 段廣告，共 " + adSeconds + " 秒");
                        lastCount = adCount;
                        lastSeconds = adSeconds;
                        lastTime = currentTime;
                    } else if (adCount < 0 && (currentTime - lastTime) > 60000) {
                        Notify.showTop("廣告過濾失敗");
                        lastCount = adCount;
                        lastTime = currentTime;
                    }
                });
            }
        });
 **/
    }


    private void setupExceptionHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            if (isSpiderError(e)) {
                Log.e("SpiderWatcher", "Intercepted external spider error on thread [" + t.getName() + "]: " + e.getMessage());
            } else if (defaultHandler != null && !defaultHandler.getClass().getName().startsWith("cat.ereza.customactivityoncrash")) {
                // 如果是 spider error，我們自己吞掉不崩潰
                // 如果不是，交給系統或是 Caoc 處理
                defaultHandler.uncaughtException(t, e);
            }
        });
    }

    private boolean isSpiderError(Throwable e) {
        if (e == null) return false;
        String msg = String.valueOf(e.getMessage());
        if (e instanceof org.json.JSONException && (msg.contains("<html>") || msg.contains("<HTML>") || msg.contains("<!DOCTYPE"))) return true;
        for (StackTraceElement element : e.getStackTrace()) {
            if (element.getClassName().contains("com.github.catvod.spider")) return true;
            if (element.getClassName().contains("com.fongmi.quickjs.crawler")) return true;
        }
        Throwable cause = e.getCause();
        return isSpiderError(cause);
    }

    @Override
    public PackageManager getPackageManager() {
        if (com.fongmi.hook.Chromium.find()) return getBaseContext().getPackageManager();
        if (!hook) return getBaseContext().getPackageManager();
        return LiveConfig.get().getHome().getCore().getHook();
    }

    @Override
    public String getPackageName() {
        if (com.fongmi.hook.Chromium.find()) return getBaseContext().getPackageName();
        if (!hook) return getBaseContext().getPackageName();
        return LiveConfig.get().getHome().getCore().getPkg();
    }
}