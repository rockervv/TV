package com.fongmi.android.tv.api.loader;

import com.github.catvod.crawler.Spider;

import java.util.Map;

public class Proxy {

    public static Object[] local(Map<String, String> params) {
        try {
            String siteKey = params.containsKey("siteKey") ? params.get("siteKey") : BaseLoader.get().getRecent();
            Spider spider = BaseLoader.get().getSpider(siteKey);
            return spider.proxyLocal(params);
        } catch (Throwable e) {
            return null;
        }
    }
}
