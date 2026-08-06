package com.github.catvod.crawler;

import com.github.catvod.bean.Result;
import java.util.HashMap;

public class SpiderNull extends Spider {

    private String message;

    public SpiderNull() {
        this("");
    }

    public SpiderNull(String message) {
        this.message = message;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        return message.isEmpty() ? "" : Result.error(message);
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        return message.isEmpty() ? "" : Result.error(message);
    }

    @Override
    public String detailContent(java.util.List<String> ids) throws Exception {
        return message.isEmpty() ? "" : Result.error(message);
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        return message.isEmpty() ? "" : Result.error(message);
    }
}
