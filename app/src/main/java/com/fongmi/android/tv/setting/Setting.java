package com.fongmi.android.tv.setting;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Prefers;

public class Setting {

    private static final int MIN_WALL = 0;
    private static final int MAX_WALL = 4;
    private static final int MIN_WALL_TYPE = 0;
    private static final int MAX_WALL_TYPE = 2;
    private static final int MIN_SITE_MODE = 0;
    private static final int MAX_SITE_MODE = 1;
    private static final int MIN_SYNC_MODE = 0;
    private static final int MAX_SYNC_MODE = 2;

    public static String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static int getWall() {
        // 📥 改用 Java 17 巢狀 max/min 寫法
        int value = Prefers.getInt("wall", 1);
        return Math.max(MIN_WALL, Math.min(MAX_WALL, value));
    }

    public static void putWall(int wall) {
        // 📥 改用 Java 17 巢狀 max/min 寫法
        Prefers.put("wall", Math.max(MIN_WALL, Math.min(MAX_WALL, wall)));
    }

    public static int getWallType() {
        // 📥 改用 Java 17 巢狀 max/min 寫法
        int value = Prefers.getInt("wall_type", 0);
        return Math.max(MIN_WALL_TYPE, Math.min(MAX_WALL_TYPE, value));
    }

    public static void putWallType(int type) {
        // 📥 改用 Java 17 巢狀 max/min 寫法
        Prefers.put("wall_type", Math.max(MIN_WALL_TYPE, Math.min(MAX_WALL_TYPE, type)));
    }
    public static int getThemeColor() {
        return Prefers.getInt("theme_color", -1);
    }

    public static void putThemeColor(int color) {
        Prefers.put("theme_color", color);
    }

    public static int getWallColor() {
        return Prefers.getInt("wall_color", 0);
    }

    public static void putWallColor(int color) {
        Prefers.put("wall_color", color);
    }

    public static int getDynamicColor() {
        int color = getThemeColor();
        if (color == -1) return 0;
        return color != 0 ? color : getWallColor();
    }

    public static int getSiteMode() {
        // 📥 改用 Java 17 巢狀 max/min 寫法
        int value = Prefers.getInt("site_mode");
        return Math.max(MIN_SITE_MODE, Math.min(MAX_SITE_MODE, value));
    }

    public static void putSiteMode(int mode) {
        // 📥 改用 Java 17 巢狀 max/min 寫法
        Prefers.put("site_mode", Math.max(MIN_SITE_MODE, Math.min(MAX_SITE_MODE, mode)));
    }

    public static int getSyncMode() {
        // 📥 改用 Java 17 巢狀 max/min 寫法
        int value = Prefers.getInt("sync_mode");
        return Math.max(MIN_SYNC_MODE, Math.min(MAX_SYNC_MODE, value));
    }

    public static void putSyncMode(int mode) {
        // 📥 改用 Java 17 巢狀 max/min 寫法
        Prefers.put("sync_mode", Math.max(MIN_SYNC_MODE, Math.min(MAX_SYNC_MODE, mode)));
    }


    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }
}
