package com.fongmi.quickjs.utils;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import androidx.collection.LruCache;

import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.Headers;

public class Module {
    private static final int MAX_SIZE = 50;
    private final LruCache<String, String> cache;

    private static class Loader {
        static volatile Module INSTANCE = new Module();
    }

    public static Module get() {
        return Loader.INSTANCE;
    }

    public Module() {
        cache = new LruCache<>(MAX_SIZE);
    }


    public String fetch(String name) {
        String content = cache.get(name);
        if (!TextUtils.isEmpty(content)) return content;
        android.util.Log.d("Module", "Fetching JS from: " + name);
        if (name.startsWith("http")) {
            content = OkHttp.string(name);
            if (TextUtils.isEmpty(content)) {
                android.util.Log.e("Module", "FAILED to fetch JS from URL (EMPTY response): " + name);
            } else {
                android.util.Log.d("Module", "Successfully fetched JS from URL, length: " + content.length());
                cache.put(name, content);
            }
        } else if (name.startsWith("assets")) {
            cache.put(name, content = Asset.read(name));
        } else if (name.startsWith("lib/")) {
            cache.put(name, content = Asset.read("js/" + name));
        }
        return content;
    }

    public void clear() {
        cache.evictAll();
    }


    private String cache(String url) {
        try {
            Uri uri = Uri.parse(url);
            File file = Path.js(uri.getLastPathSegment());
            return file.exists() ? Path.read(file) : "";
        } catch (Exception e) {
            return "";
        }
    }

    public byte[] bb(String content) {
        byte[] bytes = Base64.decode(content.substring(4), Base64.DEFAULT);
        byte[] newBytes = new byte[bytes.length - 4];
        newBytes[0] = 1;
        System.arraycopy(bytes, 5, newBytes, 1, bytes.length - 5);
        return newBytes;
    }
}
