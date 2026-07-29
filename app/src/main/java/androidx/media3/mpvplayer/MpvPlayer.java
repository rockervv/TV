package androidx.media3.mpvplayer;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaItem.SubtitleConfiguration;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;

import is.xyz.mpv.MPVLib;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MpvPlayer extends ForwardingPlayer implements MPVLib.EventObserver {

    private final Context context;
    private MediaItem currentMediaItem;
    private int playbackState = STATE_IDLE;
    private boolean playWhenReady = false;
    private final List<Listener> listeners = new ArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SurfaceHolder currentHolder;
    private Surface lastSurface;
    private boolean surfaceAttached;
    private boolean released;
    private int decode;
    private String vo;
    private final MpvPlayerConfig config;

    private int videoWidth;
    private int videoHeight;
    private long manualDuration = -1;
    private long manualPosition = -1;
    private long seekPosition = -1;
    private long lastTimePosPost;

    public MpvPlayer(Context context) {
        this(context, null);
    }

    public MpvPlayer(Context context, @Nullable MpvPlayerConfig config) {
        super(new ExoPlayer.Builder(context).build());
        this.context = context;
        this.config = config;
        this.decode = 0;
        this.vo = (config != null && config.vo != null) ? config.vo : "gpu";
        MPVLib.create(context);
        MPVLib.addObserver(this);
        
        MPVLib.setOptionString("tls-verify", "no");
        MPVLib.setOptionString("tls-verify-peer", "no");
        MPVLib.setOptionString("cache", "yes");
        MPVLib.setOptionString("demuxer-lavf-o", "protocol_whitelist=file,http,https,tcp,tls,crypto,ffmpeg,rtp,udp");

        if (config != null) {
            if (config.fontConfigDir != null) {
                MPVLib.setOptionString("config-dir", config.fontConfigDir.getAbsolutePath());
            }
            applyConfig(config);
        }

        MPVLib.init();
        
        if (config != null && config.postInitOptions != null) {
            for (Map.Entry<String, String> entry : config.postInitOptions.entrySet()) {
                MPVLib.setPropertyString(entry.getKey(), entry.getValue());
            }
        }

        MPVLib.observeProperty("time-pos", 5);
        MPVLib.observeProperty("duration", 5);
        MPVLib.observeProperty("pause", 3);
        MPVLib.observeProperty("metadata", 1);
        MPVLib.observeProperty("video-out-params", 1);
        MPVLib.observeProperty("hwdec-current", 1);
        MPVLib.observeProperty("eof-reached", 3);
        MPVLib.observeProperty("speed", 5);
    }

    private void applyConfig(MpvPlayerConfig config) {
        if (config.preInitOptions != null) {
            for (Map.Entry<String, String> entry : config.preInitOptions.entrySet()) {
                MPVLib.setOptionString(entry.getKey(), entry.getValue());
            }
        }
        if (config.voCache != null) {
            MPVLib.setOptionString("gpu-shader-cache-dir", config.voCache.getAbsolutePath());
        }
        if (config.vo != null) MPVLib.setOptionString("vo", config.vo);
        if (config.defaultUserAgent != null) MPVLib.setOptionString("user-agent", config.defaultUserAgent);
        MPVLib.setOptionString("hls-http-persistent", config.hlsHttpPersistent ? "yes" : "no");

        if (config.diskCacheDir != null) {
            MPVLib.setOptionString("cache-dir", config.diskCacheDir.getAbsolutePath());
            MPVLib.setOptionString("cache-on-disk", "yes");
            if (config.diskCacheSize > 0) MPVLib.setOptionString("demuxer-max-disk-cache", config.diskCacheSize + "MiB");
        }
        setSubtitleOptions(config);
    }

    public void setSubtitleOptions(MpvPlayerConfig config) {
        if (released) return;
        MPVLib.setPropertyString("sub-visibility", config.isCaption ? "yes" : "no");
        MPVLib.setPropertyDouble("sub-pos", config.subPos);
        MPVLib.setPropertyDouble("sub-scale", config.subScale);
    }

    public static boolean isAvailable() {
        return MPVLib.isLoaded();
    }

    public void setDecode(int decode) {
        if (released) return;
        this.decode = decode;
        MPVLib.setPropertyString("hwdec", decode == 1 ? "mediacodec" : decode == 2 ? "mediacodec-copy" : "no");
    }

    public void addSubtitle(SubtitleConfiguration config) {
        if (released) return;
        MPVLib.command(new String[]{"sub-add", config.uri.toString()});
    }

    @Override
    public void eventProperty(String property) {
        if (released) return;
        if ("time-pos".equals(property)) {
            long now = System.currentTimeMillis();
            if (now - lastTimePosPost < 200) return;
            lastTimePosPost = now;
        }
        if ("metadata".equals(property) || "video-out-params".equals(property) || "time-pos".equals(property) || "duration".equals(property) || "pause".equals(property) || "speed".equals(property)) {
            mainHandler.post(() -> {
                if (released) return;
                if ("metadata".equals(property)) {
                    playbackState = STATE_READY;
                    for (Listener listener : listeners) {
                        listener.onPlaybackStateChanged(playbackState);
                        listener.onIsPlayingChanged(isPlaying());
                        listener.onTimelineChanged(getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
                    }
                } else if ("video-out-params".equals(property)) {
                    Double w = MPVLib.getPropertyDouble("video-out-params/w");
                    Double h = MPVLib.getPropertyDouble("video-out-params/h");
                    if (w != null && h != null) {
                        videoWidth = w.intValue();
                        videoHeight = h.intValue();
                        for (Listener listener : listeners) {
                            listener.onVideoSizeChanged(new VideoSize(videoWidth, videoHeight));
                            listener.onTracksChanged(getCurrentTracks());
                            listener.onTimelineChanged(getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
                        }
                    }
                } else if ("time-pos".equals(property)) {
                    Double value = MPVLib.getPropertyDouble("time-pos");
                    if (value != null) {
                        this.manualPosition = (long) (value * 1000);
                        if (seekPosition != -1 && Math.abs(manualPosition - seekPosition) < 2000) seekPosition = -1;
                        if (playbackState == STATE_BUFFERING) {
                            playbackState = STATE_READY;
                            for (Listener listener : listeners) {
                                listener.onPlaybackStateChanged(playbackState);
                                listener.onIsPlayingChanged(isPlaying());
                                listener.onRenderedFirstFrame();
                            }
                        }
                    }
                } else if ("duration".equals(property)) {
                    Double value = MPVLib.getPropertyDouble("duration");
                    if (value != null) {
                        this.manualDuration = (long) (value * 1000);
                        for (Listener listener : listeners) {
                            listener.onPlaybackStateChanged(playbackState);
                            listener.onTimelineChanged(getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
                        }
                    }
                } else if ("pause".equals(property)) {
                    Boolean value = MPVLib.getPropertyBoolean("pause");
                    if (value != null) {
                        playWhenReady = !value;
                        for (Listener listener : listeners) {
                            listener.onPlayWhenReadyChanged(playWhenReady, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
                            listener.onIsPlayingChanged(isPlaying());
                        }
                    }
                } else if ("speed".equals(property)) {
                    Double value = MPVLib.getPropertyDouble("speed");
                    if (value != null) {
                        for (Listener listener : listeners) listener.onPlaybackParametersChanged(new PlaybackParameters(value.floatValue()));
                    }
                }
            });
        }
    }

    @Override public void eventProperty(String property, long value) {}
    @Override public void eventProperty(String property, boolean value) { eventProperty(property); }
    @Override public void eventProperty(String property, String value) {
        if ("hwdec-current".equals(property)) {
            if ("no".equals(value) && "mediacodec".equals(MPVLib.getPropertyString("hwdec"))) {
                android.util.Log.w("MpvPlayer", "Hardware decoding failed.");
            }
        } else eventProperty(property);
    }
    @Override public void eventProperty(String property, double value) { eventProperty(property); }

    @Override public void event(int eventId) {
        final String reasonStr = eventId == 7 ? MPVLib.getPropertyString("finished-data/reason") : null;
        mainHandler.post(() -> {
            switch (eventId) {
                case 8: // FILE_LOADED
                    playbackState = STATE_READY;
                    seekPosition = -1;
                    for (Listener listener : listeners) listener.onTimelineChanged(getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
                    break;
                case 18: // SEEK
                    break;
                case 19: // PLAYBACK_RESTART (Seek finished)
                    seekPosition = -1;
                    break;
                case 7: // END_FILE
                    Boolean eofReached = MPVLib.getPropertyBoolean("eof-reached");
                    if (Boolean.TRUE.equals(eofReached) || "0".equals(reasonStr) || "eof".equals(reasonStr)) {
                        playbackState = STATE_ENDED;
                    } else playbackState = STATE_IDLE;
                    break;
                case 20: playbackState = STATE_BUFFERING; break;
            }
            for (Listener listener : listeners) listener.onPlaybackStateChanged(playbackState);
            for (Listener listener : listeners) listener.onIsPlayingChanged(isPlaying());
        });
    }

    public void setManualMetadata(long positionMs, long durationMs) {
        this.manualPosition = positionMs;
        this.manualDuration = durationMs;
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        if (released || mediaItems.isEmpty()) return;
        this.currentMediaItem = mediaItems.get(startIndex);
        this.manualPosition = startPositionMs != C.TIME_UNSET ? startPositionMs : 0;
        this.manualDuration = -1;
        this.seekPosition = -1;

        mainHandler.post(() -> {
            if (released) return;
            for (Listener listener : listeners) {
                listener.onMediaItemTransition(currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
                listener.onTimelineChanged(getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE);
            }
        });

        if (currentMediaItem.localConfiguration != null) {
            String url = currentMediaItem.localConfiguration.uri.toString();
            synchronized (this) {
                StringBuilder headerStr = new StringBuilder();
                if (currentMediaItem.localConfiguration.tag instanceof Map) {
                    Map<?, ?> headers = (Map<?, ?>) currentMediaItem.localConfiguration.tag;
                    for (Map.Entry<?, ?> entry : headers.entrySet()) {
                        headerStr.append(entry.getKey()).append(": ").append(entry.getValue()).append("\\r\\n");
                    }
                }
                if (headerStr.length() > 0) MPVLib.setOptionString("headers", headerStr.toString());
                if (startPositionMs > 0) MPVLib.setOptionString("start", String.valueOf(startPositionMs / 1000.0));
                MPVLib.command(new String[]{"loadfile", url, "replace"});
            }
        }
    }

    @Override public void setMediaItem(MediaItem mediaItem) { setMediaItems(java.util.Collections.singletonList(mediaItem), 0, C.TIME_UNSET); }
    @Override public void setMediaItem(MediaItem mediaItem, long startPositionMs) { setMediaItems(java.util.Collections.singletonList(mediaItem), 0, startPositionMs); }
    @Override public void setMediaItem(MediaItem mediaItem, boolean resetPosition) { setMediaItems(java.util.Collections.singletonList(mediaItem), 0, C.TIME_UNSET); }
    @Override public void setMediaItems(List<MediaItem> mediaItems) { setMediaItems(mediaItems, 0, C.TIME_UNSET); }
    @Override public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) { setMediaItems(mediaItems, 0, C.TIME_UNSET); }

    @Override public void prepare() {
        playbackState = STATE_BUFFERING;
        for (Listener listener : listeners) listener.onPlaybackStateChanged(playbackState);
    }

    @Override public void play() {
        if (released) return;
        playWhenReady = true;
        MPVLib.setPropertyBoolean("pause", false);
        for (Listener listener : listeners) listener.onPlayWhenReadyChanged(true, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    }

    @Override public void pause() {
        if (released) return;
        playWhenReady = false;
        MPVLib.setPropertyBoolean("pause", true);
        for (Listener listener : listeners) listener.onPlayWhenReadyChanged(false, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST);
    }

    @Override public void setPlayWhenReady(boolean playWhenReady) { if (playWhenReady) play(); else pause(); }
    @Override public boolean getPlayWhenReady() { return playWhenReady; }

    @Override public synchronized void release() {
        if (released) return;
        released = true;
        if (currentHolder != null) currentHolder.removeCallback(surfaceCallback);
        MPVLib.command(new String[]{"stop"});
        detachSurface();
        MPVLib.removeObserver(this);
        MPVLib.destroy();
    }

    @Override public int getPlaybackState() { return released ? STATE_IDLE : playbackState; }
    @Override public long getDuration() { return manualDuration > 0 ? manualDuration : 0; }
    @Override public synchronized long getCurrentPosition() {
        if (seekPosition != -1) return seekPosition;
        return manualPosition > 0 ? manualPosition : 0;
    }

    @Override public void seekTo(int windowIndex, long positionMs) {
        if (released) return;
        this.seekPosition = positionMs;
        this.manualPosition = positionMs;
        this.lastTimePosPost = 0;
        android.util.Log.d("MpvPlayer", "seekTo: " + positionMs + "ms (" + (positionMs / 1000.0) + "s)");
        MPVLib.setPropertyBoolean("pause", false);
        MPVLib.command(new String[]{"seek", String.valueOf(positionMs / 1000.0), "absolute+exact"});
        for (Listener listener : listeners) listener.onPositionDiscontinuity(Player.DISCONTINUITY_REASON_SEEK);
    }

    @Override public void seekTo(long positionMs) { seekTo(0, positionMs); }
    @Override public Looper getApplicationLooper() { return Looper.getMainLooper(); }
    @Override public void addListener(Listener listener) { listeners.add(listener); }
    @Override public void removeListener(Listener listener) { listeners.remove(listener); }
    @Override public boolean isPlaying() { return !released && playbackState == STATE_READY && playWhenReady; }
    @Override public void setPlaybackParameters(PlaybackParameters playbackParameters) {
        if (released) return;
        MPVLib.setPropertyDouble("speed", playbackParameters.speed);
    }
    @Override public PlaybackParameters getPlaybackParameters() {
        Double speed = MPVLib.getPropertyDouble("speed");
        return new PlaybackParameters(speed != null ? speed.floatValue() : 1.0f);
    }
    @Override public void stop() { if (!released) MPVLib.command(new String[]{"stop"}); }

    @Override public Timeline getCurrentTimeline() {
        if (currentMediaItem == null) return Timeline.EMPTY;
        return new Timeline() {
            @Override public int getWindowCount() { return 1; }
            @Override public Window getWindow(int windowIndex, Window window, long defaultPositionProjectionUs) {
                return window.set(new Object(), currentMediaItem, null, C.TIME_UNSET, C.TIME_UNSET, C.TIME_UNSET, true, false, null, 0, getDuration() * 1000, 0, 0, 0);
            }
            @Override public int getPeriodCount() { return 1; }
            @Override public Period getPeriod(int periodIndex, Period period, boolean setIds) {
                return period.set(new Object(), new Object(), 0, getDuration() * 1000, 0);
            }
            @Override public int getIndexOfPeriod(Object uid) { return 0; }
            @Override public Object getUidOfPeriod(int periodIndex) { return new Object(); }
        };
    }

    @Override public Tracks getCurrentTracks() {
        if (videoWidth > 0 && videoHeight > 0) {
            androidx.media3.common.Format videoFormat = new androidx.media3.common.Format.Builder()
                    .setSampleMimeType(androidx.media3.common.MimeTypes.VIDEO_RAW)
                    .setWidth(videoWidth).setHeight(videoHeight).build();
            androidx.media3.common.TrackGroup group = new androidx.media3.common.TrackGroup(videoFormat);
            return new Tracks(com.google.common.collect.ImmutableList.of(new Tracks.Group(group, false, new int[]{C.FORMAT_HANDLED}, new boolean[]{true})));
        }
        return Tracks.EMPTY;
    }

    @Override public MediaMetadata getMediaMetadata() { return currentMediaItem == null ? MediaMetadata.EMPTY : currentMediaItem.mediaMetadata; }
    @Override public VideoSize getVideoSize() { return new VideoSize(videoWidth, videoHeight); }
    @Override @Nullable public MediaItem getCurrentMediaItem() { return currentMediaItem; }
    @Override public int getCurrentMediaItemIndex() { return 0; }
    @Override public int getMediaItemCount() { return currentMediaItem == null ? 0 : 1; }
    @Override public MediaItem getMediaItemAt(int index) { return currentMediaItem; }
    @Override public long getBufferedPosition() { return 0; }
    @Override public int getBufferedPercentage() { return 0; }
    @Override public long getTotalBufferedDuration() { return 0; }
    @Override public boolean isCurrentWindowDynamic() { return false; }
    @Override public boolean isCurrentWindowLive() { return false; }
    @Override public long getCurrentLiveOffset() { return 0; }
    @Override public boolean isCurrentWindowSeekable() { return true; }
    @Override public boolean isCurrentMediaItemSeekable() { return true; }
    @Override public boolean isPlayingAd() { return false; }
    @Override public int getCurrentAdGroupIndex() { return -1; }
    @Override public int getCurrentAdIndexInAdGroup() { return -1; }
    @Override public long getContentDuration() { return getDuration(); }
    @Override public MediaMetadata getPlaylistMetadata() { return MediaMetadata.EMPTY; }
    @Override public void setPlaylistMetadata(MediaMetadata mediaMetadata) {}
    @Override public CueGroup getCurrentCues() { return CueGroup.EMPTY_TIME_ZERO; }
    @Override public DeviceInfo getDeviceInfo() { return DeviceInfo.UNKNOWN; }
    @Override public int getDeviceVolume() { return 0; }
    @Override public boolean isDeviceMuted() { return false; }
    @Override public void setDeviceVolume(int volume) {}
    @Override public void setDeviceMuted(boolean muted) {}
    @Override public void setDeviceVolume(int volume, int flags) {}
    @Override public void setDeviceMuted(boolean muted, int flags) {}
    @Override public AudioAttributes getAudioAttributes() { return AudioAttributes.DEFAULT; }
    @Override public void setVolume(float audioVolume) { if (!released) MPVLib.setPropertyDouble("volume", audioVolume * 100); }
    @Override public float getVolume() { if (released) return 1f; Double d = MPVLib.getPropertyDouble("volume"); return d != null ? d.floatValue() / 100f : 1f; }
    @Override public boolean isCommandAvailable(int command) { return getAvailableCommands().contains(command); }
    @Override public Commands getAvailableCommands() {
        return new Commands.Builder().add(COMMAND_PLAY_PAUSE).add(COMMAND_PREPARE).add(COMMAND_STOP).add(COMMAND_SEEK_TO_DEFAULT_POSITION).add(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM).add(COMMAND_SEEK_TO_NEXT).add(COMMAND_SEEK_TO_PREVIOUS).add(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).add(COMMAND_SEEK_BACK).add(COMMAND_SEEK_FORWARD).add(COMMAND_SET_SPEED_AND_PITCH).add(COMMAND_SET_SHUFFLE_MODE).add(COMMAND_SET_REPEAT_MODE).add(COMMAND_GET_CURRENT_MEDIA_ITEM).add(COMMAND_GET_METADATA).add(COMMAND_GET_TIMELINE).add(COMMAND_GET_TRACKS).add(COMMAND_SET_VIDEO_SURFACE).add(COMMAND_GET_AUDIO_ATTRIBUTES).add(COMMAND_GET_DEVICE_VOLUME).add(COMMAND_GET_VOLUME).add(COMMAND_SET_VOLUME).add(COMMAND_SET_MEDIA_ITEM).add(COMMAND_CHANGE_MEDIA_ITEMS).add(COMMAND_SET_TRACK_SELECTION_PARAMETERS).build();
    }

    @Override public void setVideoSurface(@Nullable Surface surface) { if (surface != null) attachSurface(surface); else detachSurface(); }
    @Override public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
        if (currentHolder != null) currentHolder.removeCallback(surfaceCallback);
        currentHolder = surfaceHolder;
        if (currentHolder != null) {
            currentHolder.setFormat(PixelFormat.RGBA_8888);
            currentHolder.addCallback(surfaceCallback);
            Surface surface = currentHolder.getSurface();
            if (surface != null && surface.isValid()) {
                android.graphics.Rect rect = currentHolder.getSurfaceFrame();
                attachSurface(surface, rect.width(), rect.height());
            }
        } else detachSurface();
    }

    private synchronized void attachSurface(Surface surface) { attachSurface(surface, -1, -1); }
    private synchronized void attachSurface(Surface surface, int width, int height) {
        if (released) return;
        if (surface != null && surface.isValid()) {
            if (surface == lastSurface && surfaceAttached) return;
            MPVLib.setPropertyString("vo", "null");
            MPVLib.detachSurface();
            MPVLib.attachSurface(surface);
            surfaceAttached = true;
            lastSurface = surface;
            MPVLib.setPropertyString("gpu-context", "android");
            MPVLib.setPropertyString("vo", this.vo);
            if (width > 0 && height > 0) MPVLib.setPropertyString("android-surface-size", width + "x" + height);
        }
    }

    private synchronized void detachSurface() {
        if (!surfaceAttached) return;
        MPVLib.detachSurface();
        surfaceAttached = false;
        lastSurface = null;
    }

    @Override public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
        if (surfaceView != null) {
            surfaceView.getHolder().setFormat(PixelFormat.RGBA_8888);
            surfaceView.setZOrderMediaOverlay(true);
            setVideoSurfaceHolder(surfaceView.getHolder());
        } else setVideoSurfaceHolder(null);
    }

    @Override public void setVideoTextureView(@Nullable TextureView textureView) {
        if (textureView != null) attachSurface(new Surface(textureView.getSurfaceTexture()), textureView.getWidth(), textureView.getHeight());
        else setVideoSurface(null);
    }
    @Override public void clearVideoSurface() { setVideoSurface(null); }
    @Override public void setPlaybackSpeed(float speed) { if (!released) MPVLib.setPropertyDouble("speed", speed); }

    private final SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        @Override public void surfaceCreated(@NonNull SurfaceHolder holder) { if (!released) attachSurface(holder.getSurface()); }
        @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            if (!released && width > 0 && height > 0) {
                synchronized (MpvPlayer.this) { MPVLib.setPropertyString("android-surface-size", width + "x" + height); }
            }
        }
        @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) { detachSurface(); }
    };

    public static class Builder {
        private final Context context;
        private int decode;
        private MpvPlayerConfig config;
        public Builder(Context context) { this.context = context; }
        public Builder setDecode(int decode) { this.decode = decode; return this; }
        public Builder setConfig(MpvPlayerConfig config) { this.config = config; return this; }
        public MpvPlayer build() {
            MpvPlayer player = new MpvPlayer(context, config);
            player.setDecode(decode);
            return player;
        }
    }
}
