package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Live;
import com.github.catvod.utils.Path;
import com.google.gson.JsonObject;

import java.io.File;

public class LiveUtil {

    public static Live getMyLive() {
        try {
            File file = new File(Path.tv(), "my_live.json");
            if (!file.exists()) return createMyLive();
            return Live.objectFrom(App.gson().fromJson(Path.read(file), JsonObject.class), "");
        } catch (Exception e) {
            return createMyLive();
        }
    }

    private static Live createMyLive() {
        Live live = new Live();
        live.setName("我的頻道");
        live.setType("virtual");
        return live;
    }

    public static void save(Live live) {
        try {
            File file = new File(Path.tv(), "my_live.json");
            Path.write(file, App.gson().toJson(live));
        } catch (Exception ignored) {
        }
    }
}
