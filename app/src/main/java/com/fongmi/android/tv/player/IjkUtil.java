package com.fongmi.android.tv.player;

import android.net.Uri;
import android.util.Log;

import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.utils.UrlUtil;

import java.util.Map;

import tv.danmaku.ijk.media.player.MediaSource;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import java.util.concurrent.*;

public class IjkUtil {

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface MediaSourceCallback {
        void onMediaSourceReady(MediaSource source);
    }
    public static void getSourceAsync(Context context, Map<String, String> headers, String url, MediaSourceCallback callback) {
        executor.execute(() -> {
            MediaSource source = getSourceInternal(context, headers, url);
            mainHandler.post(() -> callback.onMediaSourceReady(source));
        });
    }

    public static MediaSource getSource(Result result) {
        return getSource(result.getHeaders(), result.getRealUrl());
    }

    public static MediaSource getSource(Channel channel) {
        return getSource(channel.getHeaders(), channel.getUrl());
    }

    public static MediaSource getSource(Map<String, String> headers, String url) {
        Uri uri = UrlUtil.uri(url);
        return new MediaSource(Players.checkUa(headers), uri);
    }

    public static MediaSource getSourceInternal(Context context, Map<String, String> headers, String url) {
        Uri uri = UrlUtil.uri(url);
        headers = Players.checkUa(headers);
        try {
            // 1. 建立連線下載 m3u8 原始內容
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                conn.setRequestProperty(entry.getKey(), entry.getValue());
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String filtered = ADFilter.Process(reader, mainHandler);

            // 3. 寫入本地 filtered 檔案
            File m3u8File = new File(context.getCacheDir(), "filtered_" + System.currentTimeMillis() + ".m3u8");
            FileWriter writer = new FileWriter(m3u8File);
            writer.write(filtered);
            writer.close();

            // 5. 傳回 MediaSource
            Uri localUri = Uri.fromFile(m3u8File);

            return new MediaSource(headers, localUri);

        } catch (Exception e) {
            //e.printStackTrace();
            Log.e ("IJKUtil", "Error getSource: " + e);

            // fallback 回原始 URL
            return new MediaSource(Players.checkUa(headers), uri);
            //return new MediaSource(Players.checkUa(headers), UrlUtil.uri(url));
        }




    }


    public static void setSubtitleView(IjkVideoView ijk) {
        ijk.getSubtitleView().setStyle(ExoUtil.getCaptionStyle());
        ijk.getSubtitleView().setApplyEmbeddedFontSizes(false);
        ijk.getSubtitleView().setApplyEmbeddedStyles(!Setting.isCaption());
        if (Setting.getSubtitleTextSize() != 0) ijk.getSubtitleView().setFractionalTextSize(Setting.getSubtitleTextSize());
        if (Setting.getSubtitleBottomPadding() != 0) ijk.getSubtitleView().setBottomPaddingFraction(Setting.getSubtitleBottomPadding());
    }
}
