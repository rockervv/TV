package com.fongmi.android.tv.utils;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Rule;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Sniffer {

    public static final Pattern CLICKER = Pattern.compile("\\[a=cr:(\\{.*?\\})\\/](.*?)\\[\\/a]");
    public static final Pattern AI_PUSH = Pattern.compile("(http|https|rtmp|rtsp|smb|ftp|thunder|magnet|ed2k|mitv|tvbox-xg|jianpian|video):[^\\s]+", Pattern.MULTILINE);
    public static final Pattern SNIFFER = Pattern.compile("http((?!http).){12,}?\\.(m3u8|mp4|mkv|flv|mp3|m4a|aac|mpd)\\?.*|http((?!http).){12,}\\.(m3u8|mp4|mkv|flv|mp3|m4a|aac|mpd)|http((?!http).)*?video/tos*|http((?!http).)*?obj/tos*|googlevideo\\.com/api/manifest/(hls_variant|dash)(?!.*adformat).*");

    public static String getUrl(String text) {
        if (Json.valid(text) || text.contains("$")) return text;
        Matcher m = AI_PUSH.matcher(text);
        if (m.find()) return m.group(0);
        return text;
    }

    public static boolean isVideoFormat(String url) {
        Rule rule = getRule(UrlUtil.uri(url));
        for (String exclude : rule.getExclude()) if (url.contains(exclude)) return false;
        for (String exclude : rule.getExclude()) if (Pattern.compile(exclude).matcher(url).find()) return false;
        for (String regex : rule.getRegex()) if (url.contains(regex)) return true;
        for (String regex : rule.getRegex()) if (Pattern.compile(regex).matcher(url).find()) return true;
        if (url.contains("url=http") || url.contains("v=http") || url.contains(".html") || url.contains(".js") || url.contains(".css")) return false;
        return SNIFFER.matcher(url).find();
    }

    public static Rule getRule(Uri uri) {
        if (uri == null || uri.getHost() == null) return Rule.empty();
        String hosts = TextUtils.join(",", Arrays.asList(UrlUtil.host(uri), UrlUtil.host(uri.getQueryParameter("url"))));
        for (Rule rule : VodConfig.get().getRules()) for (String host : rule.getHosts()) if (Util.containOrMatch(hosts, host)) return rule;
        for (Rule rule : LiveConfig.get().getRules()) for (String host : rule.getHosts()) if (Util.containOrMatch(hosts, host)) return rule;
        return Rule.empty();
    }

    public static List<String> getRegex(Uri uri) {
        return getRule(uri).getRegex();
    }

    public static List<String> getScript(Uri uri) {
        List<String> scripts = new ArrayList<>(getRule(uri).getScript());
        if (uri.getHost() != null && uri.getHost().contains("youtube.com")) {
            scripts.add("(function() {\n" +
                    "    let checkCount = 0;\n" +
                    "    let targetId = new URLSearchParams(window.location.search).get('v') || window.location.pathname.split('/').pop();\n" +
                    "    let timer = setInterval(() => {\n" +
                    "        checkCount++;\n" +
                    "        console.log('>>> [JS] Extraction Attempt #' + checkCount);\n" +
                    "        try {\n" +
                    "            let data = window.ytInitialPlayerResponse || window?.ytplayer?.config?.args?.raw_player_response;\n" +
                    "            if (typeof data === 'string') data = JSON.parse(data);\n" +
                    "            \n" +
                    "            let currentId = data?.videoDetails?.videoId;\n" +
                    "            let playability = data?.playabilityStatus?.status;\n" +
                    "            let isAd = data?.adPlacements || (currentId && currentId !== targetId);\n" +
                    "            \n" +
                    "            if (isAd || playability === 'UNPLAYABLE') {\n" +
                    "                console.log('>>> [JS] Waiting for Ad to finish or Video to load... Status: ' + playability);\n" +
                    "                // 自動跳過廣告\n" +
                    "                let video = document.querySelector('video');\n" +
                    "                if (video && isAd) video.currentTime = video.duration || 100;\n" +
                    "                let skipBtn = document.querySelector('.ytp-ad-skip-button') || document.querySelector('.ytp-skip-ad-button');\n" +
                    "                if (skipBtn) skipBtn.click();\n" +
                    "                return;\n" +
                    "            }\n" +
                    "            \n" +
                    "            let hls = data?.streamingData?.hlsManifestUrl;\n" +
                    "            if (hls && currentId === targetId) {\n" +
                    "                clearInterval(timer);\n" +
                    "                console.log('>>> [JS] REAL Content HLS found: ' + hls);\n" +
                    "                location.replace(hls);\n" +
                    "            }\n" +
                    "        } catch (e) {}\n" +
                    "        if (checkCount > 20) clearInterval(timer);\n" +
                    "    }, 1500);\n" +
                    "})();");
        }
        return scripts;
    }
}
