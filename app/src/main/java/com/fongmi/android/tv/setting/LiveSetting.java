package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class LiveSetting {

    public static boolean isBoot() {
        return Prefers.getBoolean("boot_live");
    }

    public static void putBoot(boolean boot) {
        Prefers.put("boot_live", boot);
    }

    public static boolean isAcross() {
        return Prefers.getBoolean("across", true);
    }

    public static void putAcross(boolean across) {
        Prefers.put("across", across);
    }

    public static boolean isChange() {
        return Prefers.getBoolean("change", true);
    }

    public static void putChange(boolean change) {
        Prefers.put("change", change);
    }

    public static boolean isInvert() {
        return Prefers.getBoolean("invert");
    }

    public static void putInvert(boolean invert) {
        Prefers.put("invert", invert);
    }

    public static int getScale() {
        // 📥 改用 Java 17 及舊版本 Android 盒完美相容的 max/min 巢狀組合
        int currentValue = Prefers.getInt("scale_live", PlayerSetting.getScale());
        return Math.max(PlayerSetting.MIN_SCALE, Math.min(PlayerSetting.MAX_SCALE, currentValue));

        //return Math.clamp(Prefers.getInt("scale_live", PlayerSetting.getScale()), PlayerSetting.MIN_SCALE, PlayerSetting.MAX_SCALE);
    }

    public static void putScale(int scale) {
        // 📥 改用 Java 17 完美相容的上下限限制寫法
        Prefers.put("scale_live", Math.max(PlayerSetting.MIN_SCALE, Math.min(PlayerSetting.MAX_SCALE, scale)));
    }

}
