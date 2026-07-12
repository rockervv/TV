package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import com.fongmi.android.tv.utils.UrlUtil;
import java.util.List;
import java.util.concurrent.Callable;

public class Youtube implements Source.Extractor {

    @Override
    public boolean match(Uri uri) {
        String host = UrlUtil.host(uri);
        return host.contains("youtube.com") || host.contains("youtu.be");
    }

    @Override
    public String fetch(String url) throws Exception {
        return url;
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }

    public static class Parser implements Callable<List<com.fongmi.android.tv.bean.Episode>> {
        public Parser(String url) {
        }
        public static boolean match(String url) {
            return false;
        }
        public static Parser get(String url) {
            return new Parser(url);
        }
        @Override
        public List<com.fongmi.android.tv.bean.Episode> call() {
            return java.util.Collections.emptyList();
        }
    }
}
