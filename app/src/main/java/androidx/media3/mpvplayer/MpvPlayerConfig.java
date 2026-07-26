package androidx.media3.mpvplayer;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MpvPlayerConfig {

    public static final String VIDEO_OUTPUT_GPU_NEXT = "gpu-next";

    public final String defaultUserAgent;
    public final boolean hlsHttpPersistent;
    public final List<File> configDirectories;
    public final Map<String, String> preInitOptions;
    public final Map<String, String> postInitOptions;
    public final File fontConfigDir;
    public final File fontConfigCache;
    public final String vo;
    public final File voCache;
    public final File tlsCaFile;
    public final boolean isCaption;
    public final double subPos;
    public final double subScale;
    public final File diskCacheDir;
    public final int diskCacheTime;
    public final int diskCacheSize;

    private MpvPlayerConfig(Builder builder) {
        this.defaultUserAgent = builder.defaultUserAgent;
        this.hlsHttpPersistent = builder.hlsHttpPersistent;
        this.configDirectories = builder.configDirectories;
        this.preInitOptions = builder.preInitOptions;
        this.postInitOptions = builder.postInitOptions;
        this.fontConfigDir = builder.fontConfigDir;
        this.fontConfigCache = builder.fontConfigCache;
        this.vo = builder.vo;
        this.voCache = builder.voCache;
        this.tlsCaFile = builder.tlsCaFile;
        this.isCaption = builder.isCaption;
        this.subPos = builder.subPos;
        this.subScale = builder.subScale;
        this.diskCacheDir = builder.diskCacheDir;
        this.diskCacheTime = builder.diskCacheTime;
        this.diskCacheSize = builder.diskCacheSize;
    }

    public static final class Builder {
        private String defaultUserAgent;
        private boolean hlsHttpPersistent;
        private final List<File> configDirectories = new ArrayList<>();
        private final Map<String, String> preInitOptions = new HashMap<>();
        private final Map<String, String> postInitOptions = new HashMap<>();
        private File fontConfigDir;
        private File fontConfigCache;
        private String vo;
        private File voCache;
        private File tlsCaFile;
        private boolean isCaption;
        private double subPos;
        private double subScale;
        private File diskCacheDir;
        private int diskCacheTime;
        private int diskCacheSize;

        public Builder setDefaultUserAgent(String userAgent) {
            this.defaultUserAgent = userAgent;
            return this;
        }

        public Builder setHlsHttpPersistent(boolean persistent) {
            this.hlsHttpPersistent = persistent;
            return this;
        }

        public Builder addConfigDirectory(File directory) {
            this.configDirectories.add(directory);
            return this;
        }

        public Builder addPreInitStringOption(String key, String value) {
            this.preInitOptions.put(key, value);
            return this;
        }

        public Builder addPostInitStringOption(String key, String value) {
            this.postInitOptions.put(key, value);
            return this;
        }

        public Builder addAndroidFontConfig(File configDir, File cacheDir) {
            this.fontConfigDir = configDir;
            this.fontConfigCache = cacheDir;
            return this;
        }

        public Builder addAndroidDefaults(String vo, File cacheDir) {
            this.vo = vo;
            this.voCache = cacheDir;
            return this;
        }

        public Builder addTlsCaFileFromAsset(Context context, String assetName, File targetFile) {
            try {
                java.io.InputStream is = context.getAssets().open(assetName);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
                fos.close();
                is.close();
                this.tlsCaFile = targetFile;
            } catch (Exception e) {
                android.util.Log.e("MpvPlayerConfig", "Failed to copy CA file from assets", e);
            }
            return this;
        }

        public Builder addAndroidSubtitleOptions(Context context, boolean isCaption, double subPos, double subScale) {
            this.isCaption = isCaption;
            this.subPos = subPos;
            this.subScale = subScale;
            return this;
        }

        public Builder addDiskCacheOptions(File cacheDir, int time, int size) {
            this.diskCacheDir = cacheDir;
            this.diskCacheTime = time;
            this.diskCacheSize = size;
            return this;
        }

        public MpvPlayerConfig build() {
            return new MpvPlayerConfig(this);
        }
    }
}
