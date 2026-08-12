package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.common.net.HttpHeaders;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Future;

import okhttp3.Response;

public class Download {

    private final File file;
    private final String url;
    private Callback callback;
    private Future<?> future;
    private String tag;


    public static Download create(String url, File file) {
        return create(url, file, null);
    }

    public static Download create(String url, File file, Callback callback) {
        return new Download(url, file, callback);
    }


    public Download(String url, File file, Callback callback) {
        this.url = url;
        this.file = file;
        this.callback = callback;
    }

    public Download(String url, File file) {
        this.tag = url;
        this.url = url;
        this.file = file;
    }

    public Download tag(String tag) {
        this.tag = tag;
        return this;
    }

    public File get() {
        doInBackground();
        return file;
    }

    public void start() {
        start(callback);
    }

    public void start(Callback callback) {
        this.callback = callback;
        future = Task.submit(this::doInBackground);
    }


    private void doInBackground() {
        try (Response res = OkHttp.newCall(url, tag).execute()) {
            download(res.body().byteStream(), getLength(res));
            if (callback != null) App.post(() -> callback.success(file));
        } catch (Exception e) {
            Path.clear(file);
            if (callback != null) App.post(() -> callback.error(e.getMessage()));
            else throw new RuntimeException(e.getMessage(), e);
        }

    }

    private void download(InputStream is, double length) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(is); FileOutputStream os = new FileOutputStream(Path.create(file))) {
            byte[] buffer = new byte[16384];
            int readBytes;
            long totalBytes = 0;
            long lastTime = System.currentTimeMillis();
            long lastBytes = 0;
            while ((readBytes = input.read(buffer)) != -1) {
                if (Thread.interrupted()) return;
                totalBytes += readBytes;
                os.write(buffer, 0, readBytes);
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastTime >= 500) {
                    long bytesRead = totalBytes - lastBytes;
                    double speed = (bytesRead / 1024.0) / ((currentTime - lastTime) / 1000.0);
                    int progress = length > 0 ? (int) (totalBytes / length * 100.0) : -1;
                    if (callback != null) App.post(() -> callback.progress(progress, String.format(java.util.Locale.getDefault(), "%.2f KB/s", speed)));
                    lastTime = currentTime;
                    lastBytes = totalBytes;
                }
            }
        }
    }

    private double getLength(Response res) {
        try {
            String header = res.header(HttpHeaders.CONTENT_LENGTH);
            return header != null ? Double.parseDouble(header) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public interface Callback {

        void progress(int progress, String speed);

        void error(String msg);

        void success(File file);
    }
}
