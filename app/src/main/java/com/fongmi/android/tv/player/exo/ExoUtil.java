package com.fongmi.android.tv.player.exo;

import android.content.Context;
import android.graphics.Color;
import android.media.audiofx.LoudnessEnhancer;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExoUtil {

    static {
        androidx.media3.common.util.Log.setLogger(new androidx.media3.common.util.Log.Logger() {
            @Override
            public void d(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
                Log.d(tag, message, throwable);
            }

            @Override
            public void i(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
                Log.i(tag, message, throwable);
            }

            @Override
            public void w(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
                Log.w(tag, message, throwable);
            }

            @Override
            public void e(@NonNull String tag, @NonNull String message, @Nullable Throwable throwable) {
                if (throwable instanceof PlaybackException pe) {
                    if (pe.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED || pe.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || pe.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || pe.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
                        return;
                    }
                }
                Log.e(tag, message, throwable);
            }
        });
    }

    @androidx.media3.common.util.UnstableApi
    @SuppressWarnings("RestrictedApi")
    public static ExoPlayer buildPlayer(int decode, Player.Listener listener) {
        Log.d("ExoUtil", "buildPlayer decode: " + decode);

        // 🛠️ 建立共享的 Allocator
        androidx.media3.exoplayer.upstream.DefaultAllocator allocator = new androidx.media3.exoplayer.upstream.DefaultAllocator(true, androidx.media3.common.C.DEFAULT_BUFFER_SEGMENT_SIZE);

        // 🛡️ 建立單一穩健的 LoadControl 實例，避免多實例導致的線程衝突
        androidx.media3.exoplayer.DefaultLoadControl internal = new androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setAllocator(allocator)
                .setBufferDurationsMs(60000, 120000, 1000, 3000) // 加大緩衝區上限至 120s
                .setBackBuffer(30000, true)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        // 🛠️ 優化解碼器性能
        RenderersFactory renderersFactory = buildPlaybackRenderersFactory(decode);
        if (renderersFactory instanceof DefaultRenderersFactory factory) {
            factory.setEnableDecoderFallback(true);
        }

        androidx.media3.exoplayer.LoadControl loadControl = new androidx.media3.exoplayer.LoadControl() {
            private volatile boolean isMpd;

            @Override public void onPrepared(@NonNull androidx.media3.exoplayer.analytics.PlayerId playerId) { internal.onPrepared(playerId); }

            @Override public void onTracksSelected(@NonNull androidx.media3.exoplayer.analytics.PlayerId playerId, @NonNull androidx.media3.common.Timeline timeline, @NonNull androidx.media3.exoplayer.source.MediaSource.MediaPeriodId mediaPeriodId, @NonNull androidx.media3.exoplayer.Renderer[] renderers, @NonNull androidx.media3.exoplayer.source.TrackGroupArray trackGroups, @NonNull androidx.media3.exoplayer.trackselection.ExoTrackSelection[] trackSelections) {
                if (!timeline.isEmpty()) {
                    androidx.media3.common.Timeline.Period period = new androidx.media3.common.Timeline.Period();
                    try {
                        int windowIndex = timeline.getPeriodByUid(mediaPeriodId.periodUid, period).windowIndex;
                        androidx.media3.common.Timeline.Window window = new androidx.media3.common.Timeline.Window();
                        timeline.getWindow(windowIndex, window);
                        isMpd = window.mediaItem != null && window.mediaItem.localConfiguration != null && MimeTypes.APPLICATION_MPD.equals(window.mediaItem.localConfiguration.mimeType);
                    } catch (Exception ignored) {}
                }
                internal.onTracksSelected(playerId, timeline, mediaPeriodId, renderers, trackGroups, trackSelections);
            }

            @Override public void onStopped(@NonNull androidx.media3.exoplayer.analytics.PlayerId playerId) { internal.onStopped(playerId); }
            @Override public void onReleased(@NonNull androidx.media3.exoplayer.analytics.PlayerId playerId) { internal.onReleased(playerId); }
            @Override public androidx.media3.exoplayer.upstream.Allocator getAllocator() { return internal.getAllocator(); }
            @Override public long getBackBufferDurationUs(@NonNull androidx.media3.exoplayer.analytics.PlayerId playerId) { return internal.getBackBufferDurationUs(playerId); }
            @Override public boolean retainBackBufferFromKeyframe(@NonNull androidx.media3.exoplayer.analytics.PlayerId playerId) { return internal.retainBackBufferFromKeyframe(playerId); }

            @Override
            public boolean shouldContinueLoading(@NonNull androidx.media3.exoplayer.LoadControl.Parameters parameters) {
                return internal.shouldContinueLoading(parameters);
            }

            @Override
            public boolean shouldStartPlayback(@NonNull androidx.media3.exoplayer.LoadControl.Parameters parameters) {
                // 🚀 核心優化：如果是 YouTube (DASH)，強制 0ms 起始，消除切換 Period 時的緩衝圈圈
                if (isMpd) return true;
                return internal.shouldStartPlayback(parameters);
            }
        };

        ExoPlayer player = new ExoPlayer.Builder(App.get())
                .setTrackSelector(buildTrackSelector())
                .setRenderersFactory(renderersFactory)
                .setMediaSourceFactory(buildMediaSourceFactory())
                .setLoadControl(loadControl)
                .setUseLazyPreparation(false)
                .build();
        if (BuildConfig.DEBUG) player.addAnalyticsListener(new EventLogger());
        player.addAnalyticsListener(new AnalyticsListener() {
            private LoudnessEnhancer loudnessEnhancer;

            @Override
            public void onTimelineChanged(@NonNull EventTime eventTime, int reason) {
                Log.d("ExoUtil", "onTimelineChanged - Periods: " + eventTime.timeline.getPeriodCount() + " | Reason: " + reason);
            }

            @Override
            public void onPositionDiscontinuity(@NonNull EventTime eventTime, @NonNull Player.PositionInfo oldPosition, @NonNull Player.PositionInfo newPosition, int reason) {
                Log.w("ExoUtil", "onPositionDiscontinuity - Reason: " + reason + " | From: " + oldPosition.positionMs + " To: " + newPosition.positionMs);
            }

            @Override
            public void onAudioSessionIdChanged(@NonNull EventTime eventTime, int audioSessionId) {
                Log.d("ExoUtil", "onAudioSessionIdChanged: " + audioSessionId);
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
            public void onPlaybackStateChanged(@NonNull EventTime eventTime, int state) {
                Log.d("ExoUtil", "onPlaybackStateChanged: " + state + " | VideoSize: " + player.getVideoSize().width + "x" + player.getVideoSize().height);
            }

            @Override
            public void onRenderedFirstFrame(@NonNull EventTime eventTime, @NonNull Object output, long renderTimeMs) {
                Log.d("ExoUtil", "onRenderedFirstFrame! Output: " + output);
            }

            @Override
            public void onVideoDecoderInitialized(@NonNull EventTime eventTime, @NonNull String decoderName, long initializedTimestampMs, long initializationDurationMs) {
                Log.d("ExoUtil", "Video Decoder Initialized: " + decoderName + " in " + initializationDurationMs + "ms");
            }

            @Override
            public void onAudioDecoderInitialized(@NonNull EventTime eventTime, @NonNull String decoderName, long initializedTimestampMs, long initializationDurationMs) {
                Log.d("ExoUtil", "Audio Decoder Initialized: " + decoderName + " in " + initializationDurationMs + "ms");
            }

            @Override
            public void onPlayerReleased(@NonNull EventTime eventTime) {
                Log.d("ExoUtil", "onPlayerReleased");
                if (loudnessEnhancer != null) {
                    loudnessEnhancer.release();
                    loudnessEnhancer = null;
                }
            }

            @Override
            public void onLoadStarted(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo, @NonNull MediaLoadData mediaLoadData) {
                Log.d("ExoUtil", "onLoadStarted: " + loadEventInfo.uri);
            }

            @Override
            public void onLoadCompleted(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo, @NonNull MediaLoadData mediaLoadData) {
                Log.d("ExoUtil", "onLoadCompleted: " + loadEventInfo.uri + " | Duration: " + loadEventInfo.loadDurationMs + "ms | Size: " + loadEventInfo.bytesLoaded);
            }

            @Override
            public void onLoadError(@NonNull EventTime eventTime, @NonNull LoadEventInfo loadEventInfo, @NonNull MediaLoadData mediaLoadData, @NonNull java.io.IOException error, boolean wasCanceled) {
                Log.e("ExoUtil", "onLoadError: " + loadEventInfo.uri + " | Error: " + error.getMessage());
            }

            @Override
            public void onPlayerError(@NonNull EventTime eventTime, @NonNull PlaybackException error) {
                String url = (player.getCurrentMediaItem() != null && player.getCurrentMediaItem().localConfiguration != null)
                        ? player.getCurrentMediaItem().localConfiguration.uri.toString() : "Unknown";
                Log.d("ExoUtil", "Playback Error: " + error.getErrorCodeName() + " (" + error.errorCode + ")");
                Log.d("ExoUtil", "Failed URL: " + url);
                Log.d("ExoUtil", "Error Cause: " + getConciseMsg(error));
            }

            @Override
            public void onDroppedVideoFrames(@NonNull EventTime eventTime, int droppedFrames, long elapsedMs) {
                if (droppedFrames > 10) Log.w("ExoUtil", "Dropped frames: " + droppedFrames + " in " + elapsedMs + "ms");
            }
        });
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setHandleAudioBecomingNoisy(true);
        player.setPlayWhenReady(true);
        player.addListener(listener);
        return player;
    }

    public static void setRender(ExoPlayer player, int mode, int intensity) {
        if (!PlayerSetting.isRenderEnhance()) return;
        Log.d("ExoUtil", "setRender mode: " + mode + " intensity: " + intensity);
        if (mode == 0) {
            Log.d("ExoUtil", "Standard mode, clearing effects");
            player.setVideoEffects(new ArrayList<>());
            return;
        }
        List<androidx.media3.common.Effect> effects = new ArrayList<>();
        switch (mode) {
            case 1: // Bright
                float b = 0.1f * (intensity + 1);
                Log.d("ExoUtil", "Applying Brightness: " + b);
                effects.add(new androidx.media3.effect.Brightness(b));
                break;
            case 2: // Cinema
                float c = 0.2f * (intensity + 1);
                float s = 15.0f * (intensity + 1);
                Log.d("ExoUtil", "Applying Contrast: " + c + " Saturation: " + s);
                effects.add(new androidx.media3.effect.Contrast(c));
                effects.add(new androidx.media3.effect.HslAdjustment.Builder().adjustSaturation(s).build());
                break;
            case 3: // Night
                float b3 = -0.1f * (intensity + 1);
                float c3 = -0.05f * (intensity + 1);
                Log.d("ExoUtil", "Applying Night - Brightness: " + b3 + " Contrast: " + c3);
                effects.add(new androidx.media3.effect.Brightness(b3));
                effects.add(new androidx.media3.effect.Contrast(c3));
                break;
            case 4: // Comfort
                Log.d("ExoUtil", "Applying Comfort (RgbAdjustment)");
                effects.add(new androidx.media3.effect.RgbAdjustment.Builder().setRedScale(1.1f).setBlueScale(0.8f).build());
                break;
        }
        try {
            player.setVideoEffects(effects);
            Log.d("ExoUtil", "setVideoEffects called successfully");
        } catch (Exception e) {
            Log.e("ExoUtil", "setVideoEffects error: ", e);
        }
    }

    public static String getMimeType(int errorCode) {
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED) return MimeTypes.APPLICATION_M3U8;
        if (errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED || errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED || errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) return "application/octet-stream";
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
        return decode == com.fongmi.android.tv.player.engine.PlayerEngine.HARD ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER;
    }

    private static TrackSelector buildTrackSelector() {
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(App.get());
        DefaultTrackSelector.Parameters.Builder builder = trackSelector.buildUponParameters();
        if (PlayerSetting.isPreferAAC()) builder.setPreferredAudioMimeType(MimeTypes.AUDIO_AAC);
        builder.setPreferredTextLanguages(LangUtil.getPreferredTextLanguages());
        // 🛠️ 穩定性優化：YouTube DASH 直播不建議開啟隧道模式
        builder.setTunnelingEnabled(false); 
        // 🛠️ 軌道銜接優化：防止因微小格式變動導致重置
        builder.setForceHighestSupportedBitrate(true);
        builder.setExceedVideoConstraintsIfNecessary(true);
        builder.setExceedRendererCapabilitiesIfNecessary(true);
        builder.setAllowVideoMixedMimeTypeAdaptiveness(true);
        builder.setAllowAudioMixedMimeTypeAdaptiveness(true);
        builder.setAllowVideoNonSeamlessAdaptiveness(true);
        // 🛠️ 增加解碼器適應性：允許在不同解碼能力間切換，減少 0 groups 情況
        builder.setAllowVideoMixedDecoderSupportAdaptiveness(true);
        builder.setAllowAudioMixedDecoderSupportAdaptiveness(true);
        // 🛠️ 核心：允許在 Period 切換時跨 MIME 類型適配，模擬 Period Unification
        builder.setAllowMultipleAdaptiveSelections(true);
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
        DefaultRenderersFactory factory = new DefaultRenderersFactory(App.get()) {
            @Override
            protected AudioSink buildAudioSink(@NonNull Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
                return ExoUtil.buildAudioSink(context, enableFloatOutput, enableAudioOutputPlaybackParams);
            }
        };
        factory.setExtensionRendererMode(renderMode);
        factory.setEnableDecoderFallback(true);
        // 🛠️ 銜接優化：允許視頻在切換 Period 時有更長的等待時間，避免因同步微調導致的重置
        factory.setAllowedVideoJoiningTimeMs(10000);
        return factory;
    }

    private static AudioSink buildAudioSink(Context context, boolean enableFloatOutput, boolean enableAudioOutputPlaybackParams) {
        AudioSink sink = new DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput).build();
        return (AudioSink) java.lang.reflect.Proxy.newProxyInstance(AudioSink.class.getClassLoader(), new Class[]{AudioSink.class}, (proxy, method, args) -> {
            if (method.getName().equals("setListener") && args[0] instanceof AudioSink.Listener) {
                return method.invoke(sink, new AudioSinkListener((AudioSink.Listener) args[0]));
            }
            return method.invoke(sink, args);
        });
    }

    private static class AudioSinkListener implements AudioSink.Listener {

        private final AudioSink.Listener listener;

        private AudioSinkListener(AudioSink.Listener listener) {
            this.listener = listener;
        }

        @Override
        public void onAudioSinkError(@NonNull Exception e) {
            Log.d("ExoUtil", "Audio Sink Error: " + e.getMessage());
        }

        @Override
        public void onPositionDiscontinuity() {
            if (listener != null) listener.onPositionDiscontinuity();
        }

        @Override
        public void onPositionAdvancing(long playoutServerTimestampNs) {
            if (listener != null) listener.onPositionAdvancing(playoutServerTimestampNs);
        }

        @Override
        public void onUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
            if (listener != null) listener.onUnderrun(bufferSize, bufferSizeMs, elapsedSinceLastFeedMs);
        }

        @Override
        public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
            if (listener != null) listener.onSkipSilenceEnabledChanged(skipSilenceEnabled);
        }

        @Override
        public void onOffloadBufferEmptying() {
            if (listener != null) listener.onOffloadBufferEmptying();
        }

        @Override
        public void onOffloadBufferFull() {
            if (listener != null) listener.onOffloadBufferFull();
        }
    }

    private static String getConciseMsg(Throwable e) {
        if (e == null) return "";
        String msg = e.getMessage() != null ? e.getMessage() : "";
        Throwable cause = e.getCause();
        while (cause != null) {
            String causeMsg = cause.getMessage();
            if (causeMsg != null && !causeMsg.isEmpty() && !causeMsg.contains(msg) && !msg.contains(causeMsg)) {
                msg += " (" + causeMsg + ")";
                break;
            }
            cause = cause.getCause();
        }
        return msg;
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
