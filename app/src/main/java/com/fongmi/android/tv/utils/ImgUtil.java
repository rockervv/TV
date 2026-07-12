package com.fongmi.android.tv.utils;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.signature.ObjectKey;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;

import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.HashMap;
import java.util.Map;

import jahirfiquitiva.libs.textdrawable.TextDrawable;


public class ImgUtil {

    private static ObjectKey getSignature(String url) {
        return new ObjectKey(url + "_" + Setting.getQuality());
    }

    public static void load(String url, CustomTarget<Bitmap> target) {
        if (!TextUtils.isEmpty(url)) Glide.with(App.get()).asBitmap().load(getUrl(url)).diskCacheStrategy(DiskCacheStrategy.ALL).dontAnimate().signature(getSignature(url)).into(target);
    }

    public static void load(String url, int error, CustomTarget<Drawable> target) {
        if (TextUtils.isEmpty(url)) target.onLoadFailed(ResUtil.getDrawable(error));
        else Glide.with(App.get()).asDrawable().load(getUrl(url)).error(error).diskCacheStrategy(DiskCacheStrategy.ALL).dontAnimate().signature(getSignature(url)).into(target);
    }

    public static void rect(String text, String url, ImageView view) {
        load(text, url, view, ImageView.ScaleType.CENTER, true);
    }

    public static void oval(String text, String url, ImageView view) {
        load(text, url, view, ImageView.ScaleType.CENTER, false);
    }

    // 🚀 魔改優化：首頁海報牆加載核心 (移除 skipMemoryCache，追加全量硬碟快取)
    public static void load(String text, String url, ImageView view, ImageView.ScaleType scaleType, boolean rect) {
        view.setScaleType(scaleType);
        if (!TextUtils.isEmpty(url)) {
            Glide.with(App.get()).asBitmap()
                    .load(getUrl(url))
                    .placeholder(R.drawable.ic_img_loading)
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // 💥 核心：全量永久緩存到硬碟
                    .dontAnimate()
                    .sizeMultiplier(Setting.getThumbnail())
                    .signature(getSignature(url))
                    .listener(getListener(view, scaleType))
                    .into(view);
        } else if (text.length() > 0) {
            view.setImageDrawable(getTextDrawable(text.substring(0, 1), rect));
        } else {
            view.setImageResource(R.drawable.ic_img_error);
        }
    }

    // 🚀 魔改優化：歷史紀錄與點播詳情頁海報加載
    public static void loadVod(String text, String url, ImageView view) {
        //android.util.Log.d("ImgUtil", "loadVod: " + text + ", url: " + url);
        view.setScaleType(ImageView.ScaleType.CENTER);
        if (!TextUtils.isEmpty(url)) {
            Glide.with(App.get()).asBitmap()
                    .load(getUrl(url))
                    .placeholder(R.drawable.ic_img_loading)
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // 💥 核心：全量永久緩存到硬碟
                    .listener(getListener(view))
                    .into(view);
        } else if (text.length() > 0) {
            view.setImageDrawable(getTextDrawable(text.substring(0, 1), true));
        } else {
            view.setImageResource(R.drawable.ic_img_error);
        }
    }

    public static void loadLive(String url, ImageView view) {
        view.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(url)) view.setImageResource(R.drawable.ic_img_empty);
        else Glide.with(App.get()).asBitmap().load(url).error(R.drawable.ic_img_empty).diskCacheStrategy(DiskCacheStrategy.ALL).dontAnimate().signature(getSignature(url)).listener(getListener(view)).into(view);
    }

    private static Drawable getTextDrawable(String text, boolean rect) {
        TextDrawable.Builder builder = new TextDrawable.Builder().withBorder(ResUtil.dp2px(2), ColorGenerator.get700(text));
        if (rect) return builder.buildRoundRect(text, ColorGenerator.get500(text), ResUtil.dp2px(8));
        return builder.buildRound(text, ColorGenerator.get500(text));
    }

    // 🚀 魔改優化：全網防盜鏈通殺型破解
    public static Object getUrl(String url) {
        if (url == null) return "";
        url = UrlUtil.convert(url);
        if (url.startsWith("data:")) return url;

        // 1. 使用 Map 暫存，以便進行「覆蓋」而非「附加」
        Map<String, String> headersMap = new HashMap<>();
        headersMap.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        String param = null;
        if (url.contains("@Headers=")) {
            param = url.split("@Headers=")[1].split("@")[0];
            headersMap.putAll(Json.toMap(Json.parse(param)));
        }
        if (url.contains("@Cookie=")) {
            param = url.split("@Cookie=")[1].split("@")[0];
            headersMap.put(HttpHeaders.COOKIE, param);
        }
        if (url.contains("@Referer=")) {
            param = url.split("@Referer=")[1].split("@")[0];
            headersMap.put(HttpHeaders.REFERER, param);
        } else if (url.startsWith("http")) {
            headersMap.put(HttpHeaders.REFERER, getBaseUrl(url));
        }
        if (url.contains("@User-Agent=")) {
            param = url.split("@User-Agent=")[1].split("@")[0];
            headersMap.put(HttpHeaders.USER_AGENT, param);
        }

        // 💥 終極優先級：如果是 Douban 圖片，強制修正 Referer，覆蓋站源提供的任何錯誤標頭
        if (url.contains("doubanio.com")) {
            headersMap.put(HttpHeaders.REFERER, "https://douban.com");
        }

        // 2. 移除 URL 中的標籤
        url = (url.contains("@Headers=") || url.contains("@Cookie=") || url.contains("@Referer=") || url.contains("@User-Agent=")) 
              ? url.split("@Headers=|@Cookie=|@Referer=|@User-Agent=")[0] : url;

        // 3. 轉入 Glide 的 LazyHeaders
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        for (Map.Entry<String, String> entry : headersMap.entrySet()) {
            builder.setHeader(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
        }

        //android.util.Log.d("ImgUtil", "Final URL: " + url + " | Referer: " + headersMap.get(HttpHeaders.REFERER));
        return TextUtils.isEmpty(url) ? null : new GlideUrl(url, builder.build());
    }

    // 輔助工具方法：提取根域名
    private static String getBaseUrl(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getProtocol() + "://" + u.getHost() + "/";
        } catch (Exception e) {
            return url;
        }
    }

    private static void addHeader(LazyHeaders.Builder builder, String header) {
        Map<String, String> map = Json.toMap(Json.parse(header));
        if (map == null) return;
        for (Map.Entry<String, String> entry : map.entrySet()) builder.setHeader(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
    }

    private static RequestListener<Bitmap> getListener(ImageView view) {
        return getListener(view, ImageView.ScaleType.CENTER);
    }

    private static RequestListener<Bitmap> getListener(ImageView view, ImageView.ScaleType scaleType) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Bitmap> target, boolean isFirstResource) {
                Log.e("ImgUtil", "Glide load failed for model: " + model);
                if (e != null) {
                    for (Throwable t : e.getRootCauses()) {
                        Log.e("ImgUtil", "Root cause: " + t.getMessage());
                    }
                }
                view.setImageResource(R.drawable.ic_img_error);
                view.setScaleType(scaleType);
                return true;
            }

            @Override
            public boolean onResourceReady(@NonNull Bitmap resource, @NonNull Object model, Target<Bitmap> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                view.setScaleType(ImageView.ScaleType.CENTER_CROP);
                return false;
            }
        };
    }
}
