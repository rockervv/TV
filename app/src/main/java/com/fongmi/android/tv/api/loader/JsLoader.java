package com.fongmi.android.tv.api.loader;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.quickjs.crawler.Loader;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsLoader {

    private final ConcurrentHashMap<String, Spider> spiders;
    private final Loader loader;
    private volatile String recent;

    public JsLoader() {
        this.spiders = new ConcurrentHashMap<>();
        this.loader = new Loader();
    }

    public void clear() {
        for (Spider spider : spiders.values()) spider.destroy();
        Module.get().clear();
        spiders.clear();
        recent = null;
    }

    public void setRecent(String recent) {
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        if (api == null || api.isEmpty()) return new SpiderNull();
        
        // 🛠️ 使用雙重檢查鎖 + intern()，解凍 UI 執行緒
        Spider spider = spiders.get(key);
        if (spider != null) return spider;

        synchronized (key.intern()) {
            spider = spiders.get(key);
            if (spider != null) return spider;

            try {
                dalvik.system.DexClassLoader dexLoader = BaseLoader.get().dex(jar);
                spider = loader.spider(api, dexLoader);
                spider.siteKey = key;
                spider.siteName = VodConfig.get().getSite(key).getName();
                spider.init(App.get(), ext);
                spiders.put(key, spider);
                return spider;
            } catch (Throwable e) {
                Spider error = new SpiderNull();
                spiders.put(key, error);
                return error;
            }
        }
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (recent == null) return null;
        Spider spider = spiders.get(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
