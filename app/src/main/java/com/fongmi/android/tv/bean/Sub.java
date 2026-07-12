package com.fongmi.android.tv.bean;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.media3.common.C;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;

import java.io.File;

public class Sub {

    @SerializedName("url")
    private String url;
    @SerializedName("name")
    private String name;
    @SerializedName("lang")
    private String lang;
    @SerializedName("format")
    private String format;
    @SerializedName("flag")
    private int flag;

    public static Sub from(String path) {
        if (path.startsWith("http")) {
            return http(path);
        } else {
            return file(Path.local(path));
        }
    }

    public static Sub from(String name, String url, String lang, String format) {
        Sub sub = new Sub();
        sub.setName(name);
        sub.setUrl(url);
        sub.setLang(lang);
        sub.setFormat(format);
        return sub;
    }

    private static Sub http(String url) {
        Uri uri = Uri.parse(url);
        Sub sub = new Sub();
        sub.url = url;
        sub.name = uri.getLastPathSegment();
        sub.flag = C.SELECTION_FLAG_FORCED;
        sub.format = ExoUtil.getMimeType(0); // Adapted
        return sub;
    }

    private static Sub file(File file) {
        Sub sub = new Sub();
        sub.name = file.getName();
        sub.url = file.getAbsolutePath();
        sub.flag = C.SELECTION_FLAG_FORCED;
        sub.format = ExoUtil.getMimeType(0); // Adapted
        return sub;
    }

    public String getUrl() {
        return TextUtils.isEmpty(url) ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLang() {
        return TextUtils.isEmpty(lang) ? "" : lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getFormat() {
        return TextUtils.isEmpty(format) ? "" : format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getFlag() {
        return flag == 0 ? C.SELECTION_FLAG_DEFAULT : flag;
    }

    public int getRawFlag() {
        return flag;
    }

    public void setFlag(int flag) {
        this.flag = flag;
    }

    public boolean isForced() {
        return (flag & C.SELECTION_FLAG_FORCED) != 0;
    }

    public boolean isEmpty() {
        return getUrl().isEmpty();
    }

    public Uri getUri() {
        return isEmpty() ? null : UrlUtil.uri(getUrl());
    }

    public void trans() {
        if (Trans.pass()) return;
        this.name = Trans.s2t(name);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Sub)) return false;
        Sub it = (Sub) obj;
        return getUrl().equals(it.getUrl());
    }

    @Override
    public String toString() {
        return App.gson().toJson(this);
    }
}
