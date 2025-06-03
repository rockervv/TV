package com.github.catvod.utils;

import android.net.Uri;

import com.github.catvod.net.OkHttp;

import java.io.File;

public class Github {

    public static final String URL = "https://rockercheng.pythonanywhere.com"; //https://my.t4tv.hz.cz";

    private static String getUrl(String path, String name) {
        return URL + "/" + path + "/" + name;
    }

    public static String getJson(boolean dev, String name) {
        return getUrl("tv/" + (dev ? "debug" : "release"), name + ".json");
    }

    public static String getApk(boolean dev, String name) {
        return getUrl("tv/" + (dev ? "debug" : "release"), name + (dev? "-debug.apk" : "-release.apk"));
    }

    public static String getSo(String url) {
        return "";
//        try {
//
//            File file = new File(Path.so(), Uri.parse(url).getLastPathSegment());
//            if (file.length() < 300) Path.write(file, OkHttp.newCall(url).execute().body().bytes());

//            return file.getAbsolutePath();
//        } catch (Exception e) {
//            e.printStackTrace();
//            return "";
//       }
    }
}
