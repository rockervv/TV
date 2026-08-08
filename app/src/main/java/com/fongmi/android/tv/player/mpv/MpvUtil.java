package com.fongmi.android.tv.player.mpv;

import android.text.TextUtils;

import androidx.media3.common.Player;
import androidx.media3.mpvplayer.MpvPlayer;
import androidx.media3.mpvplayer.MpvPlayerConfig;
import androidx.media3.ui.SubtitleView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.track.LangUtil;
import com.fongmi.android.tv.player.util.PlayerHelper;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;

import java.io.File;

public final class MpvUtil {

    private static final String ASSET_CA_FILE = "cacert.pem";
    private static final double DEFAULT_SUB_POS = 100.0;
    private static final double DEFAULT_SUB_SCALE = 1.0;
    private static final double MIN_SUB_SCALE = 0.5;
    private static final double MAX_SUB_SCALE = 3.0;
    private static final double MIN_SUB_POS = 0.0;
    private static final double MAX_SUB_POS = 150.0;
    private static final String OPT_SUB_LANG = "slang";

    public static boolean isAvailable() {
        try {
            return MpvPlayer.isAvailable();
        } catch (Throwable e) {
            return false;
        }
    }

    public static MpvPlayer buildPlayer(Player.Listener listener) {
        // 優先使用 MPV 專屬的硬解設定
        int mpvHwdec = PlayerSetting.getMpvHwdec();
        MpvPlayer player = new MpvPlayer.Builder(App.get()).setDecode(mpvHwdec).setConfig(buildConfig()).build();
        player.addListener(listener);
        return player;
    }

    public static void setSubtitleStyle(MpvPlayer player) {
        player.setSubtitleOptions(buildSubtitleConfig());
    }

    public static void setRender(MpvPlayer player, int mode, int intensity) {
        String vf = "";
        switch (mode) {
            case 1: // Bright
                double gamma = 1.0 + (intensity + 1) * 0.1;
                double bright = 0.02 * (intensity + 1);
                vf = String.format("eq=gamma=%.2f:brightness=%.2f", gamma, bright);
                break;
            case 2: // Cinema
                double contrast = 1.0 + (intensity + 1) * 0.05;
                double saturation = 1.0 + (intensity + 1) * 0.1;
                vf = String.format("eq=contrast=%.2f:saturation=%.2f", contrast, saturation);
                break;
            case 3: // Night
                double g = 1.0 - (intensity + 1) * 0.1;
                double b = -0.05 * (intensity + 1);
                vf = String.format("eq=gamma=%.2f:brightness=%.2f", g, b);
                break;
            case 4: // Comfort
                vf = "colorlevels=rim=0.1:gim=0.1:bim=0.3";
                break;
        }
        player.setOption("vf", vf);
    }

    private static MpvPlayerConfig buildConfig() {
        MpvPlayerConfig.Builder builder = newConfigBuilder();
        addAndroidOptions(builder);
        addTrackLanguageOptions(builder);
        addSubtitleStyleOptions(builder);
        return builder.build();
    }

    private static MpvPlayerConfig buildSubtitleConfig() {
        MpvPlayerConfig.Builder builder = new MpvPlayerConfig.Builder();
        addSubtitleStyleOptions(builder);
        return builder.build();
    }

    private static MpvPlayerConfig.Builder newConfigBuilder() {
        String ua = getDefaultUserAgent();
        MpvPlayerConfig.Builder builder = new MpvPlayerConfig.Builder()
                .setDefaultUserAgent(ua)
                .addPreInitStringOption("hwdec", getHwdec())
                .addPreInitStringOption("hwdec-codecs", "all")
                .addPreInitStringOption("gpu-context", "android")
                .addPreInitStringOption("vd-lavc-dr", "yes")
                .addPreInitStringOption("cache", "yes")
                .addPreInitStringOption("demuxer-max-bytes", "48MiB")
                .addPreInitStringOption("demuxer-readahead-secs", "5")
                .addPreInitStringOption("vd-lavc-threads", String.valueOf(PlayerSetting.getMpvThreads()))
                .addPreInitStringOption("vd-lavc-fast", PlayerSetting.isMpvFast() ? "yes" : "no")
                .addPreInitStringOption("framedrop", PlayerSetting.getMpvFramedrop())
                .addPreInitStringOption("video-sync", PlayerSetting.getMpvVideoSync())
                .addPreInitStringOption("autosync", "30")
                .addPreInitStringOption("opengl-pbo", PlayerSetting.isMpvPbo() ? "yes" : "no")
                .addPreInitStringOption("vd-lavc-skiploopfilter", PlayerSetting.isMpvSkipLoop() ? "all" : "default")
                .addPreInitStringOption("audio-buffer", String.valueOf(PlayerSetting.getMpvAudioBuffer() / 1000.0))
                .addPreInitStringOption("scale", "bilinear")
                .addPreInitStringOption("cscale", "bilinear")
                .addPreInitStringOption("dscale", "bilinear")
                .addPreInitStringOption("scale-antiring", "0")
                .addPreInitStringOption("hdr-compute-peak", "no")
                .addPreInitStringOption("ytdl", "no")
                .addPreInitStringOption("tls-verify", "no")
                .addPreInitStringOption("tls-verify-peer", "no")
                .addPreInitStringOption("network-timeout", "20")
                .addPreInitStringOption("hls-bitrate", "max")
                .addPreInitStringOption("dns-cache-timeout", "300")
                .addPreInitStringOption("osd-level", "0")
                .addPreInitStringOption("input-default-bindings", "no")
                .addPreInitStringOption("input-vo-keyboard", "no")
                .addPreInitStringOption("vd-lavc-fast", "yes")
                .addPreInitStringOption("vd-lavc-dr", "yes")
                .addPreInitStringOption("osd-level", "1")
                .addPreInitStringOption("demuxer-lavf-o", "protocol_whitelist=file,http,https,tcp,tls,crypto,ffmpeg,rtp,udp");

        if (PlayerSetting.isMpvGpuNext()) {
            builder.addPreInitStringOption("vo", "gpu-next");
        } else {
            builder.addPreInitStringOption("vo", "gpu");
        }

        String api = getGpuApi();
        String fbo = getFboFormat();
        if (!api.equals("auto")) builder.addPreInitStringOption("gpu-api", api);
        if (!fbo.equals("auto")) builder.addPreInitStringOption("fbo-format", fbo);

        return builder;
    }

    private static String getHwdec() {
        int index = PlayerSetting.getMpvHwdec();
        String[] values = ResUtil.getStringArray(R.array.select_mpv_hwdec_value);
        if (index >= values.length) return "no";
        return values[index];
    }

    private static String getGpuApi() {
        return ResUtil.getStringArray(R.array.select_mpv_gpu_api_value)[PlayerSetting.getMpvGpuApi()];
    }

    private static String getFboFormat() {
        String[] values = ResUtil.getStringArray(R.array.select_mpv_fbo_format_value);
        int index = PlayerSetting.getMpvFboFormat();
        String format = values[index];
        if (format.equals("auto")) {
            if (com.fongmi.android.tv.utils.Util.getTotalMem() <= 2048 * 1024 * 1024L) {
                return "rgba8";
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                return "rgb10_a2";
            }
        }
        return format;
    }

    private static void addAndroidOptions(MpvPlayerConfig.Builder builder) {
        if (com.fongmi.android.tv.utils.Util.getTotalMem() <= 2048 * 1024 * 1024L) {
            builder.addPreInitStringOption("gpu-dumb-mode", "yes");
        }
        addAndroidDefaultOptions(builder);
        addPreloadOptions(builder);
        addTlsCaFile(builder);
    }

    private static void addAndroidDefaultOptions(MpvPlayerConfig.Builder builder) {
        File configDir = Path.mpv();
        File cacheDir = Path.mpvCache();
        builder.addConfigDirectory(configDir).addAndroidFontConfig(configDir, cacheDir).addAndroidDefaults("gpu", cacheDir);
    }

    private static void addTlsCaFile(MpvPlayerConfig.Builder builder) {
        try {
            String[] assets = App.get().getAssets().list("");
            if (assets != null) for (String asset : assets) if (asset.equals(ASSET_CA_FILE)) builder.addTlsCaFileFromAsset(App.get(), ASSET_CA_FILE, Path.files(ASSET_CA_FILE));
        } catch (Exception ignored) {
        }
    }

    private static void addTrackLanguageOptions(MpvPlayerConfig.Builder builder) {
        builder.addPostInitStringOption(OPT_SUB_LANG, LangUtil.getPreferredTextLanguageList());
    }

    private static void addPreloadOptions(MpvPlayerConfig.Builder builder) {
        if (!PreloadSetting.isPreload()) return;
        builder.addDiskCacheOptions(Path.mpvCache(), PreloadSetting.getPreloadTimeSeconds(), PreloadSetting.getPreloadSizeMb());
    }

    private static void addSubtitleStyleOptions(MpvPlayerConfig.Builder builder) {
        builder.addAndroidSubtitleOptions(App.get(), PlayerSetting.isCaption(), getSubtitlePosition(), getSubtitleScale());
    }

    private static String getDefaultUserAgent() {
        String userAgent = Setting.getUa();
        return TextUtils.isEmpty(userAgent) ? PlayerHelper.getDefaultUa() : userAgent;
    }

    private static double getSubtitlePosition() {
        float position = PlayerSetting.getSubtitlePosition();
        if (position == 0) return DEFAULT_SUB_POS;
        return Math.max(MIN_SUB_POS, Math.min(MAX_SUB_POS, DEFAULT_SUB_POS - position * 100.0));
    }

    private static double getSubtitleScale() {
        float textSize = PlayerSetting.getSubtitleTextSize();
        if (textSize == 0) return DEFAULT_SUB_SCALE;
        return Math.max(MIN_SUB_SCALE, Math.min(MAX_SUB_SCALE, textSize / SubtitleView.DEFAULT_TEXT_SIZE_FRACTION));
    }
}
