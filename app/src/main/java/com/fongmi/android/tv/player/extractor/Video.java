package com.fongmi.android.tv.player.extractor;

import android.net.Uri;

public class Video implements Source.Extractor {

    @Override
    public boolean match(Uri uri) {
        return "video".equals(uri.getScheme());
    }

    @Override
    public String fetch(String url) throws Exception {
        return url.substring(8);
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
