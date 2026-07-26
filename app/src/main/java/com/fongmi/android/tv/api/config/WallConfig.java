package com.fongmi.android.tv.api.config;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class WallConfig  extends BaseConfig{

    private static final String TAG = WallConfig.class.getSimpleName();

    private static class Loader {
        static volatile WallConfig INSTANCE = new WallConfig();
    }

    public static WallConfig get() {
        return Loader.INSTANCE;
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static void load(Config config, Callback callback) {
        get().config(config).load(callback);
    }


    public void load() {
        if (sync) return;
        load(new Callback());
    }

    public WallConfig init() {
        return config(Config.wall());
    }

    public WallConfig config(Config config) {
        this.config = config;
        if (config.getUrl() == null) return this;
        this.sync = config.getUrl().equals(VodConfig.get().getWall());
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.wall();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.wall();
        RefreshEvent.wall();
        RefreshEvent.video();
        RefreshEvent.image();
        RefreshEvent.config();
    }

    @Override
    protected void load(Config config) throws Throwable {
        File file = FileUtil.getWall(0);
        checkUrl(config.getUrl(), file);
        setWallType(file);
        setSnapshot(file);
        Setting.putWall(0);
    }

    @Override
    protected boolean isLoaded() {
        return false;
    }

    private void checkUrl(String url, File file) throws Throwable {
        if (url.startsWith("file")) Path.copy(Path.local(url), file);
        else Download.create(UrlUtil.convert(url), file).tag(TAG).get();
        if (!Path.exists(file)) throw new FileNotFoundException();
    }

    private void setWallType(File file) {
        Setting.putWallType(0);
        if (isGif(file)) Setting.putWallType(1);
        else if (isVideo(file)) Setting.putWallType(2);
    }

    private void setSnapshot(File file) throws Throwable {
        Bitmap bitmap = Glide.with(App.get()).asBitmap().load(file).signature(new ObjectKey(file.length() + "@" + file.lastModified())).frame(0).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE).submit().get();
        try (FileOutputStream fos = new FileOutputStream(FileUtil.getWallCache())) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
    }

    private boolean isVideo(File file) {
        try (MediaMetadataRetriever retriever = new MediaMetadataRetriever(); FileInputStream is = new FileInputStream(file)) {
            retriever.setDataSource(is.getFD());
            return "yes".equalsIgnoreCase(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isGif(File file) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            return "image/gif".equals(options.outMimeType);
        } catch (Exception e) {
            return false;
        }
    }

}
