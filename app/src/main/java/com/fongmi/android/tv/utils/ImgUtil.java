package com.fongmi.android.tv.utils;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.FIT_CENTER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.impl.CustomTarget;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jahirfiquitiva.libs.textdrawable.TextDrawable;

public class ImgUtil {

    private static final Set<String> failed = Collections.synchronizedSet(new HashSet<>());

    public static void logo(ImageView view) {
        logo(view, VodConfig.get().getConfig().getLogo());
    }

    public static void logo(ImageView view, String url) {
        try {
            Glide.with(view).load(UrlUtil.convert(url)).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String url, CustomTarget<Bitmap> target) {
        try {
            Glide.with(App.get()).asBitmap().load(getUrl(url)).override(ResUtil.dp2px(96), ResUtil.dp2px(96)).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(Context context, String url, CustomTarget<Drawable> target) {
        try {
            Glide.with(context).load(getUrl(url)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String text, String url, ImageView view) {
        load(text, url, view, true);
    }

    public static void loadVod(String text, String url, ImageView view) {
        load(text, url, view, true);
    }

    public static void load(String text, String url, ImageView view, ImageView.ScaleType scaleType, boolean vod) {
        view.setScaleType(scaleType);
        if (!vod) view.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(url) || failed.contains(url)) view.setImageDrawable(getTextDrawable(text, vod));
        else try {
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url)).listener(getListener(text, url, view, vod));
            if (scaleType == CENTER_CROP) builder.centerCrop().into(view);
            else builder.fitCenter().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void oval(String text, String url, ImageView view) {
        load(text, url, view, CENTER_CROP, true);
    }

    public static void rect(String text, String url, ImageView view) {
        load(text, url, view, CENTER_CROP, true);
    }

    public static void load(String text, String url, ImageView view, boolean vod) {
        load(text, url, view, vod ? CENTER_CROP : FIT_CENTER, vod);
    }

    public static Object getUrl(String url) {
        String param = null;
        url = UrlUtil.convert(url);
        if (TextUtils.isEmpty(url)) return null;
        if (url.startsWith("data:")) return url;
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        if (url.contains("@Headers=")) addHeader(builder, param = url.split("@Headers=")[1].split("@")[0]);
        if (url.contains("@Cookie=")) builder.addHeader(HttpHeaders.COOKIE, param = url.split("@Cookie=")[1].split("@")[0]);
        if (url.contains("@Referer=")) builder.addHeader(HttpHeaders.REFERER, param = url.split("@Referer=")[1].split("@")[0]);
        if (url.contains("@User-Agent=")) builder.addHeader(HttpHeaders.USER_AGENT, param = url.split("@User-Agent=")[1].split("@")[0]);
        
        // 🛡️ 豆瓣特調：解決 403 Forbidden 歷史圖片問題
        if (url.contains("doubanio.com")) {
            builder.addHeader(HttpHeaders.REFERER, "https://www.douban.com/");
        }

        url = param == null ? url : url.split("@")[0];
        return new GlideUrl(url, builder.build());
    }

    private static void addHeader(LazyHeaders.Builder builder, String header) {
        Map<String, String> map = Json.toMap(Json.parse(header));
        for (Map.Entry<String, String> entry : map.entrySet()) builder.addHeader(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
    }

    private static Drawable getTextDrawable(String text, boolean vod) {
        TextDrawable.Builder builder = new TextDrawable.Builder();
        text = TextUtils.isEmpty(text) ? "！" : text.substring(0, 1);
        if (vod) builder.buildRect(text, ColorGenerator.get400(text));
        return builder.buildRoundRect(text, ColorGenerator.get400(text), ResUtil.dp2px(4));
    }

    private static RequestListener<Drawable> getListener(String text, String url, ImageView view, boolean vod) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                android.util.Log.e("TV_FATAL", "onLoadFailed: " + url + " error: " + (e != null ? e.getMessage() : "unknown"));
                view.setImageDrawable(getTextDrawable(text, vod));
                failed.add(url);
                return true;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                android.util.Log.d("TV_FATAL", "onResourceReady: " + url);
                return false;
            }
        };
    }
}
