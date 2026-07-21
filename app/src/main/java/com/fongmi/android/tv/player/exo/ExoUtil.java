package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;
import androidx.media3.exoplayer.source.LoadEventInfo;
import androidx.media3.exoplayer.source.MediaLoadData;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.ui.CaptionStyleCompat;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.track.LangUtil;
import com.fongmi.android.tv.setting.PlayerSetting;

import java.util.HashMap;
import java.util.Map;

public class ExoUtil {

    private static LoudnessEnhancer loudnessEnhancer;

    public static ExoPlayer buildPlayer(int decode, Player.Listener listener) {
        ExoPlayer player = new ExoPlayer.Builder(App.get()).setTrackSelector(buildTrackSelector()).setRenderersFactory(buildPlaybackRenderersFactory(decode)).setMediaSourceFactory(buildMediaSourceFactory()).build();
        if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());
        player.addAnalyticsListener(new AnalyticsListener() {
            @Override
            public void onAudioSessionIdChanged(@NonNull EventTime eventTime, int audioSessionId) {
                if (Setting.isNormalize()) {
                    try {
                        if (loudnessEnhancer != null) loudnessEnhancer.release();
                        loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                        loudnessEnhancer.setTargetGain(3000);
                        loudnessEnhancer.setEnabled(true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onPlayerReleased(@NonNull EventTime eventTime) {
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                    loudnessEnhancer = null;
                }
            }

            @Override
            public void onLoadStarted(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo, @NonNull MediaLoadData mediaLoadData) {
                if (loadEventInfo.uri.toString().contains(".ts")) {
                    Log.d("ExoUtil", "TS Source: " + loadEventInfo.uri);
                }
            }

            @Override
            public void onPlayerError(@NonNull EventTime eventTime, @NonNull PlaybackException error) {
                String url = (player.getCurrentMediaItem() != null && player.getCurrentMediaItem().localConfiguration != null) 
                    ? player.getCurrentMediaItem().localConfiguration.uri.toString() : "Unknown";
                Log.e("ExoUtil", "Playback Error: " + error.getErrorCodeName() + " (" + error.errorCode + ")");
                Log.e("ExoUtil", "Failed URL: " + url);
                Log.e("ExoUtil", "Error Cause: " + error.getMessage());
            }
        });
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
    }

    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED) return "application/octet-stream";
        return null;
    }

    public static Map<String, String> extractHeaders(MediaItem item) {
        Bundle extras = item.requestMetadata.extras;
        if (extras == null) return new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        for (String key : extras.keySet()) {
            String value = extras.getString(key);
            if (value != null) headers.put(key, value);
        }
        return headers;
    }

    private static int getRenderMode(int decode) {
        return decode == PlayerEngine.HARD ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
    }

    private static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (PlayerSetting.isPreferAAC()) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguages(LangUtil.getPreferredTextLanguages());
        builder.setTunnelingEnabled(PlayerSetting.isTunnelingEnabled());
        builder.setForceHighestSupportedBitrate(true);
        trackSelector.setParameters(builder.build());
        return trackSelector;
    }

    private static RenderersFactory buildPlaybackRenderersFactory(int decode) {
        return buildRenderersFactory(getRenderMode(decode), PlayerSetting.isAudioPrefer(), PlayerSetting.isVideoPrefer());
    }

    static RenderersFactory buildRenderersFactory() {
        return buildRenderersFactory(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, PlayerSetting.isAudioPrefer(), PlayerSetting.isVideoPrefer());
    }

    private static RenderersFactory buildRenderersFactory(int renderMode, boolean audioPrefer, boolean videoPrefer) {
        return new DefaultRenderersFactory(App.get()) {
            @Override
            protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
            }
        }.setExtensionRendererMode(renderMode).setEnableDecoderFallback(true);
    }

    private static AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
        return new DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput).build();
    }

    public static void setSubtitleView(PlayerView playerView) {
        playerView.getSubtitleView().setStyle(getCaptionStyle());
        playerView.getSubtitleView().setApplyEmbeddedStyles(true);
        playerView.getSubtitleView().setApplyEmbeddedFontSizes(false);
        if (PlayerSetting.getSubtitlePosition() != 0) playerView.getSubtitleView().setBottomPaddingFraction(PlayerSetting.getSubtitlePosition());
        if (PlayerSetting.getSubtitleTextSize() != 0) playerView.getSubtitleView().setFractionalTextSize(PlayerSetting.getSubtitleTextSize());
    }

    private static CaptionStyleCompat getCaptionStyle() {
        CaptioningManager manager = (CaptioningManager) App.get().getSystemService(Context.CAPTIONING_SERVICE);
        if (PlayerSetting.isCaption() && manager != null) return CaptionStyleCompat.createFromCaptionStyle(manager.getUserStyle());
        return new CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_OUTLINE, Color.BLACK, null);
    }

    private static MediaSource.Factory buildMediaSourceFactory() {
        return new MediaSourceFactory();
    }
}
