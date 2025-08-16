package com.github.catvod;

import android.util.Log;

import com.github.catvod.utils.Util;

public class Proxy {

    private static int port = 9978;

    public static void set(int port) {
        Proxy.port = port;
    }

    public static int getPort() {
        return port;
    }

    public static String getUrl(boolean local) {
        String url ="http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + getPort() + "/proxy";
        Log.d ("Proxy", "proxy url:" + url);
        return url;
    }
}
