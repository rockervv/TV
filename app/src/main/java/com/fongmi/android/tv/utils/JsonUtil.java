package com.fongmi.android.tv.utils;

import com.google.gson.Gson;

public class JsonUtil {

    private static class GsonHolder {
        private static final Gson INSTANCE = new Gson();
    }

    public static Gson gson() {
        return GsonHolder.INSTANCE;
    }
}
