package com.github.catvod.spider;

import com.github.catvod.crawler.Spider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class SpiderFactory {

    private static final Map<String, Class<? extends Spider>> spiders = new HashMap<>();

    static {
        spiders.put("loc_Douban", LocalDouban.class);
    }

    public static Set<String> getKeys() {
        return spiders.keySet();
    }

    public static Spider get(String key) {
        try {
            Class<? extends Spider> clz = spiders.get(key);
            return clz != null ? clz.newInstance() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
