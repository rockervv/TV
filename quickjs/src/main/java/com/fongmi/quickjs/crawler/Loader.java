package com.fongmi.quickjs.crawler;

import dalvik.system.DexClassLoader;

public class Loader {

    public Spider spider(String api, DexClassLoader dex) {
        return new Spider(api, dex);
    }
}
