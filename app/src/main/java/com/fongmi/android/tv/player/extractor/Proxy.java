package com.fongmi.android.tv.player.extractor;

import android.net.Uri;
import com.fongmi.android.tv.server.Server;

public class Proxy implements Source.Extractor {

    @Override
    public boolean match(Uri uri) {
        return "proxy".equals(uri.getScheme());
    }

    @Override
    public String fetch(String url) throws Exception {
        return url.replace("proxy://", Server.get().getAddress("/proxy?"));
    }

    @Override
    public void stop() {
    }

    @Override
    public void exit() {
    }
}
