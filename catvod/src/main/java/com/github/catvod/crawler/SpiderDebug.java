package com.github.catvod.crawler;

import android.util.Log;

import com.orhanobut.logger.Logger;

public class SpiderDebug {

    private static final String TAG = SpiderDebug.class.getSimpleName();

    public static void log(Throwable th) {
        if (th != null) Log.e(TAG, "Spider Error: ", th);
    }

    public static void log(String msg) {
        if (msg != null) Logger.t(TAG).d(msg);
    }
}
