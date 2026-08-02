package com.fongmi.android.tv.player;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.server.process.M3U8;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.PlayerEngineFactory;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.player.mpv.MpvPlayerEngine;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.player.parse.ParseJob;
import com.fongmi.android.tv.player.track.TrackUtil;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.common.net.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerManager implements ParseCallback {

    private final Runnable runnable;
    private final Runnable countdownRunnable;
    private final Callback callback;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;
    private long timeoutRemaining;

    private long pendingStartPositionMs;
    private boolean initTrack;
    private int retry;
    private int decode;

    public PlayerManager(Callback callback) {
        this.callback = callback;
        this.runnable = this::onPlayTimeout;
        this.countdownRunnable = this::onCountdown;
        this.decode = PlayerEngine.HARD;
        this.engine = PlayerEngineFactory.create(decode, listener);
        this.player = engine.getPlayer();
        this.pendingStartPositionMs = C.TIME_UNSET;
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void release() {
        App.removeCallbacks(runnable);
        App.removeCallbacks(countdownRunnable);
        if (player != null) player.removeListener(listener);
        if (engine != null) engine.release();
        engine = null;
        player = null;
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return player.getCurrentTracks();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player == null ? Player.STATE_IDLE : player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public boolean isReleased() {
        return player == null;
    }

    public boolean isEnded() {
        return getPlaybackState() == Player.STATE_ENDED;
    }

    public String getUrl() {
        return spec != null ? spec.getUrl() : null;
    }

    public String getM3u8Content() {
        return getM3u8Content(false);
    }

    public String getM3u8Content(boolean fetch) {
        if (TextUtils.isEmpty(getUrl())) return "";
        String url = getUrl();
        try {
            if (url.startsWith(Server.get().getAddress())) {
                url = java.net.URLDecoder.decode(url.split("url=")[1].split("&")[0], StandardCharsets.UTF_8.name());
            }
        } catch (Exception ignored) {
        }
        if (fetch) return M3U8.fetch(url, getHeaders());
        return M3U8.getCache(url);
    }

    public String getKey() {
        return spec != null ? spec.getKey() : null;
    }

    public MediaMetadata getMetadata() {
        return spec != null ? spec.getMetadata() : null;
    }

    public void setMetadata(MediaMetadata data) {
        if (spec != null) spec.setMetadata(data);
        MediaItem current = player.getCurrentMediaItem();
        if (current != null) player.replaceMediaItem(player.getCurrentMediaItemIndex(), current.buildUpon().setMediaMetadata(data).build());
    }

    public Map<String, String> getHeaders() {
        return spec == null || spec.getHeaders() == null ? new HashMap<>() : spec.getHeaders();
    }

    public float getSpeed() {
        return player.getPlaybackParameters().speed;
    }

    public boolean isEmpty() {
        return spec == null || TextUtils.isEmpty(spec.getUrl());
    }

    public boolean isPortrait() {
        return getVideoHeight() > getVideoWidth();
    }

    public boolean isLandscape() {
        return getVideoWidth() > getVideoHeight();
    }

    public boolean isLive() {
        return engine.isLive();
    }

    public boolean isVod() {
        return engine.isVod();
    }

    public boolean haveTrack(int type) {
        return TrackUtil.count(getCurrentTracks(), type) > 0;
    }

    public boolean haveEdition() {
        return false;
    }

    public boolean haveChapter() {
        return false;
    }

    public boolean canSetOpening(long position, long duration) {
        return position > 0 && duration > 0 && position <= Constant.getOpEdLimit(duration);
    }

    public boolean canSetEnding(long position, long duration) {
        return position > 0 && duration > 0 && duration - position <= Constant.getOpEdLimit(duration);
    }

    public int getVideoWidth() {
        return videoSize == null ? 0 : videoSize.width;
    }

    public int getVideoHeight() {
        return videoSize == null ? 0 : videoSize.height;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public String getSizeText() {
        return (getVideoWidth() == 0 && getVideoHeight() == 0) ? "" : getVideoWidth() + " x " + getVideoHeight();
    }

    public String getSpeedText() {
        return String.format(Locale.getDefault(), "%.2f", getSpeed());
    }

    public String getDecodeText() {
        return ResUtil.getStringArray(R.array.select_decode)[decode];
    }

    public int getEngine() {
        return engine.getType() == PlayerEngine.Type.MPV ? PlayerSetting.ENGINE_MPV : PlayerSetting.ENGINE_EXO;
    }

    public void setEngine(int targetEngine) {
        int oldEngine = getEngine();
        PlayerSetting.putEngine(targetEngine);
        if (oldEngine == targetEngine || isEmpty()) return;
        startCurrent();
    }

    public String getPositionTime(long delta) {
        return getPositionTime(getPosition(), delta);
    }

    public String getPositionTime(long position, long delta) {
        position += delta;
        long duration = Math.max(0, getDuration());
        return Util.timeMs(Math.max(0, Math.min(position, duration)));
    }

    public long getDuration() {
        return player.getDuration();
    }

    public String getDurationTime() {
        return Util.timeMs(Math.max(0, getDuration()));
    }

    public void setSub(Sub sub) {
        if (spec != null) spec.setSub(sub);
        if (engine.addSubtitle(sub)) play();
        else startCurrent();
    }

    public void setFormat(String format) {
        if (spec != null) spec.setFormat(format);
        startCurrent();
    }

    public String setSpeed(float speed) {
        if (!player.isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH)) return getSpeedText();
        player.setPlaybackParameters(player.getPlaybackParameters().withSpeed(speed));
        return getSpeedText();
    }

    public String addSpeed() {
        float speed = getSpeed();
        float step = speed >= 2 ? 1f : 0.25f;
        return setSpeed(speed >= 5 ? 0.25f : Math.min(speed + step, 5.0f));
    }

    public String addSpeed(float value) {
        float speed = getSpeed() + value;
        return setSpeed(Math.max(0.25f, Math.min(speed, 5.0f)));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.min(Math.max(getSpeed() - value, 0.25f), 5.0f));
    }

    public String toggleSpeed() {
        return setSpeed(getSpeed() == 1 ? PlayerSetting.getSpeed() : 1);
    }

    public void setTrack(List<Track> tracks) {
        if (!tracks.isEmpty()) TrackUtil.setTrackSelection(player, tracks);
    }

    public void setSubtitleStyle() {
        if (engine != null) engine.setSubtitleStyle();
    }

    public void setStats(boolean stats) {
        if (engine != null) engine.setStats(stats);
    }

    public void play() {
        player.play();
    }

    public void pause() {
        player.pause();
    }

    public void stop() {
        engine.stop();
        stopParse();
    }

    public void clearMediaItems() {
        player.clearMediaItems();
    }

    public boolean isRepeatOne() {
        return player.getRepeatMode() == Player.REPEAT_MODE_ONE;
    }

    public void setRepeatOne(boolean repeat) {
        player.setRepeatMode(repeat ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void replay(long positionMs) {
        if (positionMs == C.TIME_UNSET) player.seekToDefaultPosition();
        else player.seekTo(positionMs);
        player.play();
    }

    public void seekTo(long time) {
        player.seekTo(time);
    }

    public long getTextOffsetMs() {
        return 0;
    }

    public void setTextOffsetMs(long offsetMs) {
    }

    public long getAudioOffsetMs() {
        return 0;
    }

    public void setAudioOffsetMs(long offsetMs) {
    }

    public void reset() {
        android.util.Log.d("PlayerManager", "reset: removing all timer callbacks");
        App.removeCallbacks(runnable);
        App.removeCallbacks(countdownRunnable);
        retry = 0;
    }

    public void clear() {
        android.util.Log.d("PlayerManager", "clear: spec cleared");
        spec = null;
    }

    public void resetTrack() {
        TrackUtil.reset(player);
    }

    public void toggleDecode() {
        decode = isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean rebuild = engine.setDecode(decode);
        callback.onDecodeChanged();
        if (!rebuild) return;
        setPlayer(engine.rebuild());
        startCurrent(getPosition());
    }

    private void handleFallback(PlaybackException e) {
        if (++retry > 2) {
            callback.onError(engine.getErrorMessage(e));
        } else if (retry == 2 && engine.getType() == PlayerEngine.Type.EXO && MpvPlayerEngine.isAvailable()) {
            setEngine(PlayerSetting.ENGINE_MPV);
            startCurrent(getPosition());
        } else {
            Notify.show(R.string.error_decode_fallback);
            toggleDecode();
        }
    }

    private void handleSeek() {
        player.seekToDefaultPosition();
        player.prepare();
        callback.onPrepare();
    }

    private void handleFormat(PlaybackException e) {
        spec.setFormat(ExoUtil.getMimeType(e.errorCode));
        startCurrent(getPosition());
    }

    private boolean isHard() {
        return decode == PlayerEngine.HARD;
    }

    private void onPlayTimeout() {
        stop();
        listener.onPlayerError(new PlaybackException(ResUtil.getString(R.string.error_play_timeout), null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED));
    }

    public void startTimeout(long timeout) {
        android.util.Log.d("PlayerManager", "startTimeout: " + timeout + "ms");
        App.removeCallbacks(runnable);
        App.removeCallbacks(countdownRunnable);
        this.timeoutRemaining = timeout;
        if (timeout > 0) {
            App.post(runnable, timeout);
            App.post(countdownRunnable, 0);
        }
    }

    private void onCountdown() {
        if (timeoutRemaining < 0) {
            android.util.Log.d("PlayerManager", "onCountdown: stop (remaining < 0)");
            callback.onTimeoutCountdown(-1);
            return;
        }
        callback.onTimeoutCountdown(timeoutRemaining);
        timeoutRemaining -= 100;
        App.post(countdownRunnable, 100);
    }

    private void ensureEngine(PlaySpec spec) {
        if (PlayerEngineFactory.matches(engine, spec)) return;
        player.removeListener(listener);
        engine.release();
        engine = PlayerEngineFactory.create(decode, spec, listener);
        setPlayer(engine.getPlayer());
    }

    private void setPlayer(Player player) {
        this.player = player;
        callback.onPlayerRebuild(player);
    }

    public void browse(PlaySpec spec, long startPositionMs) {
        reset();
        clear();
        stopParse();
        start(spec, Constant.TIMEOUT_PLAY, startPositionMs);
    }

    public void start(PlaySpec spec, long timeout) {
        start(spec, timeout, C.TIME_UNSET);
    }

    public void start(PlaySpec spec, long timeout, long startPositionMs) {
        this.spec = spec;
        setMediaItem(timeout, startPositionMs);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata) {
        parse(key, result, useParse, metadata, C.TIME_UNSET);
    }

    public void parse(String key, Result result, boolean useParse, MediaMetadata metadata, long startPositionMs) {
        stopParse();
        startTimeout(Constant.TIMEOUT_PLAY);
        pendingStartPositionMs = startPositionMs;
        spec = PlaySpec.fromParse(result, key, metadata);
        parseJob = ParseJob.create(this).start(result, useParse);
    }

    private void stopParse() {
        if (parseJob != null) parseJob.stop();
        parseJob = null;
        pendingStartPositionMs = C.TIME_UNSET;
    }

    private void setMediaItem(long timeout, long startPositionMs) {
        if (spec == null || spec.getUrl() == null) return;
        App.removeCallbacks(runnable);
        ensureEngine(spec.checkUa().checkProxy());
        engine.start(spec, startPositionMs);
        if (timeout > 0) App.post(runnable, timeout);
        callback.onPrepare();
        initTrack = false;
    }

    private void startCurrent() {
        startCurrent(getPosition());
    }

    private void startCurrent(long startPositionMs) {
        setMediaItem(Constant.TIMEOUT_PLAY, startPositionMs);
    }

    @Override
    public void onParseSuccess(Map<String, String> headers, String url, String from) {
        if (!TextUtils.isEmpty(from)) Notify.show(ResUtil.getString(R.string.parse_from, from));
        if (headers != null) headers.remove(HttpHeaders.RANGE);
        if (spec != null) spec.setHeaders(headers);
        if (spec != null) spec.setUrl(url);
        startCurrent(pendingStartPositionMs);
        pendingStartPositionMs = C.TIME_UNSET;
    }

    @Override
    public void onParseError() {
        pendingStartPositionMs = C.TIME_UNSET;
        callback.onError(ResUtil.getString(R.string.error_play_parse));
    }

    public interface Callback {

        void onPrepare();

        void onTracksChanged();

        void onDecodeChanged();

        void onMediaOptionsChanged();

        void onError(String msg);

        void onPlayerRebuild(Player newPlayer);

        default void onTimeoutCountdown(long ms) {
        }
    }

    private final Player.Listener listener = new Player.Listener() {

        @Override
        public void onPlaybackStateChanged(int state) {
            android.util.Log.d("PlayerManager", "onPlaybackStateChanged: " + state);
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
                android.util.Log.d("PlayerManager", "onPlaybackStateChanged: removing timers due to READY/ENDED");
                App.removeCallbacks(runnable);
                App.removeCallbacks(countdownRunnable);
                callback.onTimeoutCountdown(-1);
            }
        }

        @Override
        public void onVideoSizeChanged(@NonNull VideoSize size) {
            videoSize = size;
        }

        @Override
        public void onTracksChanged(@NonNull Tracks tracks) {
            if (tracks.isEmpty() || initTrack) return;
            setTrack(Track.find(getKey()));
            callback.onTracksChanged();
            initTrack = true;
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            App.removeCallbacks(runnable);
            if (spec == null) return;
            switch (engine.handleError(e)) {
                case SEEK -> handleSeek();
                case FORMAT -> handleFormat(e);
                case FALLBACK -> handleFallback(e);
                case RETRY -> startCurrent(getPosition());
                case FATAL -> callback.onError(engine.getErrorMessage(e));
            }
        }

        @Override
        public void onRenderedFirstFrame() {
            if (PlayerSetting.isMpvStats()) setStats(true);
        }
    };

}
