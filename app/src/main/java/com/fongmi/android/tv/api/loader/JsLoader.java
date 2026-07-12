package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

public class JsLoader {

    public void clear() {
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        try {
            Spider spider = new com.fongmi.quickjs.crawler.Spider(api, BaseLoader.get().dex(jar));
            spider.init(App.get(), ext);
            return spider;
        } catch (Throwable e) {
            return new SpiderNull();
        }
    }
}
