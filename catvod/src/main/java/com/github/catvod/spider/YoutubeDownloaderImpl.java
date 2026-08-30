package com.github.catvod.spider;

import com.github.catvod.net.OkHttp;
import com.github.kiulian.downloader.Config;
import com.github.kiulian.downloader.downloader.Downloader;
import com.github.kiulian.downloader.downloader.request.RequestVideoFileDownload;
import com.github.kiulian.downloader.downloader.request.RequestVideoStreamDownload;
import com.github.kiulian.downloader.downloader.request.RequestWebpage;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.downloader.response.ResponseImpl;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class YoutubeDownloaderImpl implements Downloader {

    private final Config config;

    public YoutubeDownloaderImpl(Config config) {
        this.config = config;
    }

    @Override
    public Response<String> downloadWebpage(RequestWebpage request) {
        try {
            String url = request.getDownloadUrl();
            Map<String, String> headers = request.getHeaders();
            String body = request.getBody();
            String method = request.getMethod();

            okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(url);
            if (headers != null) builder.headers(okhttp3.Headers.of(headers));

            if ("POST".equalsIgnoreCase(method)) {
                builder.post(okhttp3.RequestBody.create(body, okhttp3.MediaType.parse("application/json; charset=utf-8")));
            } else {
                builder.get();
            }

            try (okhttp3.Response response = OkHttp.client().newCall(builder.build()).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return ResponseImpl.from(response.body().string());
                } else {
                    return ResponseImpl.error(new IOException("HTTP " + response.code()));
                }
            }
        } catch (Exception e) {
            return ResponseImpl.error(e);
        }
    }

    @Override
    public Response<File> downloadVideoAsFile(RequestVideoFileDownload request) {
        return ResponseImpl.error(new UnsupportedOperationException());
    }

    @Override
    public Response<Void> downloadVideoAsStream(RequestVideoStreamDownload request) {
        return ResponseImpl.error(new UnsupportedOperationException());
    }
}
