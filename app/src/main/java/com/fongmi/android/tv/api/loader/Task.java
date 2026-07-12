package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Task {

    private final ExecutorService service;

    private static class Loader {
        static volatile Task INSTANCE = new Task();
    }

    public static Task get() {
        return Loader.INSTANCE;
    }

    public Task() {
        this.service = Executors.newFixedThreadPool(10);
    }

    public static void execute(Runnable runnable) {
        get().service.execute(runnable);
    }
}
