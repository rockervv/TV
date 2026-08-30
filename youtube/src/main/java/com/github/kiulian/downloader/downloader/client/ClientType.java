// FINAL_FIX_VERSION_V30
package com.github.kiulian.downloader.downloader.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

public class ClientType {
    public static final ClientType WEB = new ClientType("WEB", "2.20241210.01.00", baseJson());
    public static final ClientType MWEB = new ClientType("MWEB", "2.20241210.01.00", baseJson());
    public static final ClientType ANDROID = new ClientType("ANDROID", "19.45.42", baseJson());
    public static final ClientType IOS = new ClientType("IOS", "19.29.1", baseJson());
    public static final ClientType TVHTML5 = new ClientType("TVHTML5", "7.20241025.01.00", baseJson());
    public static final ClientType ANDROID_TV = new ClientType("ANDROID_TV", "2.17.085", baseJson());
    public static final ClientType ANDROID_VR = new ClientType("ANDROID_VR", "1.37.100", baseJson());
    public static final ClientType ANDROID_TESTSUITE = new ClientType("ANDROID_TESTSUITE", "1.9", baseJson());

    // Compatibility Constants for Clients.java
    public static final ClientType WEB_HEROES = WEB;
    public static final ClientType TVHTML5_VR = TVHTML5;
    public static final ClientType WEB_MUSIC_ANALYTICS = WEB;
    public static final ClientType WEB_MUSIC = WEB;
    public static final ClientType TVHTML5_SIMPLY = TVHTML5;
    public static final ClientType WEB_REMIX = WEB;
    public static final ClientType TVHTML5_CAST = TVHTML5;
    public static final ClientType GOOGLE_LIST_RECS = WEB;
    public static final ClientType IOS_EMBEDDED_PLAYER = IOS;
    public static final ClientType IOS_MESSAGES_EXTENSION = IOS;
    public static final ClientType ANDROID_EMBEDDED_PLAYER = ANDROID;
    public static final ClientType IOS_LIVE_CREATION_EXTENSION = IOS;
    public static final ClientType WEB_PHONE_VERIFICATION = WEB;
    public static final ClientType IOS_PRODUCER = IOS;
    public static final ClientType WEB_EXPERIMENTS = WEB;
    public static final ClientType TVANDROID = ANDROID_TV;
    public static final ClientType MWEB_TIER_2 = MWEB;
    public static final ClientType MUSIC_INTEGRATIONS = WEB;
    public static final ClientType MEDIA_CONNECT_FRONTEND = WEB;
    public static final ClientType TVHTML5_YONGLE = TVHTML5;
    public static final ClientType GOOGLE_ASSISTANT = WEB;
    public static final ClientType XBOXONEGUIDE = TVHTML5;
    public static final ClientType WEB_INTERNAL_ANALYTICS = WEB;
    public static final ClientType GOOGLE_MEDIA_ACTIONS = WEB;
    public static final ClientType WEB_PARENT_TOOLS = WEB;
    public static final ClientType IOS_MUSIC = IOS;
    public static final ClientType ANDROID_MUSIC = ANDROID;
    public static final ClientType WEB_CREATOR = WEB;
    public static final ClientType IOS_CREATOR = IOS;
    public static final ClientType ANDROID_CREATOR = ANDROID;
    public static final ClientType ANDROID_LITE = ANDROID;
    public static final ClientType TVAPPLE = IOS;
    public static final ClientType TVLITE = TVHTML5;
    public static final ClientType WEB_EMBEDDED_PLAYER = WEB;
    public static final ClientType TVHTML5_SIMPLY_EMBEDDED_PLAYER = TVHTML5;
    public static final ClientType WEB_UNPLUGGED_OPS = WEB;
    public static final ClientType WEB_UNPLUGGED = WEB;
    public static final ClientType WEB_UNPLUGGED_ONBOARDING = WEB;
    public static final ClientType TV_UNPLUGGED_CAST = TVHTML5;
    public static final ClientType TVHTML5_UNPLUGGED = TVHTML5;
    public static final ClientType ANDROID_UNPLUGGED = ANDROID;
    public static final ClientType TV_UNPLUGGED_ANDROID = ANDROID_TV;
    public static final ClientType WEB_UNPLUGGED_PUBLIC = WEB;
    public static final ClientType IOS_UNPLUGGED = IOS;
    public static final ClientType IOS_UPTIME = IOS;
    public static final ClientType IOS_KIDS = IOS;
    public static final ClientType ANDROID_TV_KIDS = ANDROID_TV;
    public static final ClientType TVHTML5_AUDIO = TVHTML5;
    public static final ClientType TVHTML5_FOR_KIDS = TVHTML5;
    public static final ClientType ANDROID_KIDS = ANDROID;
    public static final ClientType TVHTML5_KIDS = TVHTML5;
    public static final ClientType WEB_KIDS = WEB;

    private final String body;
    private final String version;
    private final String name;

    public ClientType(String name, String version, JSONObject body, QueryParameter... parameters) {
        this.name = name;
        this.version = version;
        JSONObject client = body.getJSONObject("context").getJSONObject("client");
        client.fluentPut("clientName", name);
        client.fluentPut("clientVersion", version);
        
        if (name.contains("TV") || name.contains("TVHTML5")) {
            client.fluentPut("platform", "TV").fluentPut("osName", "Android").fluentPut("osVersion", "11").fluentPut("androidSdkVersion", "30");
        } else if (name.contains("IOS") || name.contains("MWEB")) {
            client.fluentPut("osName", "iOS").fluentPut("osVersion", "17.6").fluentPut("platform", "MOBILE");
            if (name.equals("IOS")) client.fluentPut("appBundleId", "com.google.ios.youtube");
        } else if (name.contains("ANDROID")) {
            client.fluentPut("osName", "Android").fluentPut("osVersion", "14").fluentPut("platform", "MOBILE").fluentPut("androidSdkVersion", "34");
        }

        for (QueryParameter param : parameters) {
            JSONObject cur = body;
            for (String p : param.path) { if (!p.isEmpty()) cur = cur.getJSONObject(p); }
            cur.fluentPut(param.key, param.value);
        }
        this.body = body.toJSONString();
    }

    public ClientType(String name, String version, JSONObject body) { this(name, version, body, new QueryParameter[0]); }
    public String getVersion() { return version; }
    public String getName() { return name; }
    public JSONObject getBody() { return JSON.parseObject(body); }

    private static JSONObject baseJson() {
        JSONObject client = new JSONObject().fluentPut("hl", "zh-TW").fluentPut("gl", "TW").fluentPut("utcOffsetMinutes", 480).fluentPut("userInterfaceTheme", "USER_INTERFACE_THEME_DARK");
        JSONObject context = new JSONObject().fluentPut("client", client).fluentPut("user", new JSONObject().fluentPut("lockedSafetyMode", false)).fluentPut("request", new JSONObject().fluentPut("useSsl", true));
        return new JSONObject().fluentPut("context", context);
    }

    public static QueryParameter queryParam(String path, String key, String value) { return new QueryParameter(path, key, value); }

    public static class QueryParameter {
        final String[] path;
        final String value;
        final String key;
        QueryParameter(String path, String key, String value) { this.path = path.split("/"); this.value = value; this.key = key; }
    }
}
