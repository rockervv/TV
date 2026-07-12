package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public class PreloadSetting {

    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 10;
    public static final int MIN_SIZE_MB = 128;
    public static final int MAX_SIZE_MB = 4096;
    public static final int STEP_SIZE_MB = 128;
    public static final int MIN_TIME_SECONDS = 20;
    public static final int MAX_TIME_SECONDS = 120;
    public static final int STEP_TIME_SECONDS = 10;

    public static boolean isPreload() {
        return Prefers.getBoolean("preload");
    }

    public static void putPreload(boolean preload) {
        Prefers.put("preload", preload);
    }

    public static int getPreloadThreads() {
        int value = Prefers.getInt("preload_threads", MIN_THREADS);
        return Math.max(MIN_THREADS, Math.min(MAX_THREADS, value));
    }

    public static void putPreloadThreads(int threads) {
        Prefers.put("preload_threads", Math.max(MIN_THREADS, Math.min(MAX_THREADS, threads)));
    }

    public static int getPreloadSizeMb() {
        int size = Prefers.getInt("preload_size", MIN_SIZE_MB);
        size = Math.max(MIN_SIZE_MB, Math.min(MAX_SIZE_MB, size));
        return Math.max(MIN_SIZE_MB, Math.min(MAX_SIZE_MB, MIN_SIZE_MB + Math.round((float) (size - MIN_SIZE_MB) / STEP_SIZE_MB) * STEP_SIZE_MB));
    }

    public static void putPreloadSizeMb(int size) {
        Prefers.put("preload_size", Math.max(MIN_SIZE_MB, Math.min(MAX_SIZE_MB, size)));
    }

    public static long getPreloadSizeBytes() {
        return getPreloadSizeMb() * 1024L * 1024L;
    }

    public static int getPreloadTimeSeconds() {
        int seconds = Prefers.getInt("preload_time", MAX_TIME_SECONDS);
        seconds = Math.max(MIN_TIME_SECONDS, Math.min(MAX_TIME_SECONDS, seconds));
        return Math.max(MIN_TIME_SECONDS, Math.min(MAX_TIME_SECONDS, MIN_TIME_SECONDS + Math.round((float) (seconds - MIN_TIME_SECONDS) / STEP_TIME_SECONDS) * STEP_TIME_SECONDS));
    }

    public static void putPreloadTimeSeconds(int seconds) {
        Prefers.put("preload_time", Math.max(MIN_TIME_SECONDS, Math.min(MAX_TIME_SECONDS, seconds)));
    }

    public static long getPreloadDurationMs() {
        return getPreloadTimeSeconds() * 1000L;
    }
}
