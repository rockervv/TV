package com.github.catvod.crawler;

import android.util.Log;

import com.orhanobut.logger.Logger;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();

    public static void log(Throwable th) {
        if (th == null) return;
        String msg = th.getMessage();
        if (msg != null && msg.length() > 1000) {
            msg = msg.substring(0, 1000) + "... [TRUNCATED]";
            // 🛠️ 只有建立一個新的 Exception 實體，保留 StackTrace 但截斷訊息，防止 Logcat 鎖死
            th = new RuntimeException(msg, th.getCause());
            th.setStackTrace(th.getStackTrace());
        }
        Log.e(TAG, "Spider Error: ", th);
    }

    public static void log(String msg) {
        if (msg == null) return;
        if (msg.length() > 1000) msg = msg.substring(0, 1000) + "... [TRUNCATED]";
        Logger.t(TAG).d(msg);
    }
}
