package com.fongmi.android.tv.utils;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;

public class Monitor {

    private static final String TAG = "PerformanceMonitor";
    private static final Map<String, Long> startTimes = new HashMap<>();

    public static void start(String key) {
        startTimes.put(key, System.currentTimeMillis());
        logMemory(key + " [START]");
    }

    public static void end(String key) {
        Long startTime = startTimes.remove(key);
        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            Log.d(TAG, key + " [END] Duration: " + duration + "ms");
            logMemory(key + " [END]");
        }
    }

    public static void log(String message) {
        Log.d(TAG, message);
        logMemory(message);
    }

    private static void logMemory(String prefix) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        Log.d(TAG, prefix + " Memory: " + usedMemory + "MB / " + totalMemory + "MB (Max: " + maxMemory + "MB)");
    }
}
