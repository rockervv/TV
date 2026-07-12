package com.fongmi.android.tv.player;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media3.common.C;
import com.fongmi.android.tv.bean.MediaChapter;
import com.fongmi.android.tv.bean.MediaEdition;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import android.view.View;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.ParseCallback;
import com.fongmi.android.tv.impl.SessionCallback;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.PlayerEngineFactory;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.player.parse.ParseJob;
import com.fongmi.android.tv.player.track.TrackUtil;
import com.fongmi.android.tv.player.util.PlayerHelper;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.common.net.HttpHeaders;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PlayerManager implements ParseCallback {

    private MediaSessionCompat session;
    private final Runnable runnable;
    private final Callback callback;
    private PlayerEngine engine;
    private VideoSize videoSize;
    private ParseJob parseJob;
    private PlaySpec spec;
    private Player player;
    private long pendingStartPositionMs;
    private boolean initTrack;
    private int retry;
    private int decode;

    public PlayerManager(Callback callback) {
        this.callback = callback;
        this.runnable = this::onPlayTimeout;
        this.decode = PlayerEngine.HARD;
        this.engine = PlayerEngineFactory.create(decode, listener);
        this.player = engine.getPlayer();
        this.pendingStartPositionMs = C.TIME_UNSET;
        this.session = new MediaSessionCompat(App.get(), "PlayerManager");
        this.session.setCallback(SessionCallback.create(this));
        this.session.setActive(true);
    }

    public static PlayerManager create(Callback callback) {
        return new PlayerManager(callback);
    }

    public static MediaMetadata buildMetadata(String title, String artist, String artUri) {
        Uri artwork = TextUtils.isEmpty(artUri) ? null : Uri.parse(artUri);
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(artwork).build();
    }

    public void release() {
        App.removeCallbacks(runnable);
        if (player != null) player.removeListener(listener);
        if (engine != null) engine.release();
        if (session != null) session.release();
        engine = null;
        player = null;
        session = null;
    }

    public MediaSessionCompat getSession() {
        return session;
    }

    public Player getPlayer() {
        return player;
    }

    public Tracks getCurrentTracks() {
        return player.getCurrentTracks();
    }

    public List<MediaChapter> getCurrentMediaChapters() {
        return new java.util.ArrayList<>();
    }

    public List<MediaEdition> getCurrentMediaEditions() {
        return new java.util.ArrayList<>();
    }

    public MediaItem getCurrentMediaItem() {
        return player.getCurrentMediaItem();
    }

    public int getPlaybackState() {
        return player.getPlaybackState();
    }

    public boolean isPlaying() {
        return player.isPlaying();
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
        if (getUrl() == null || !getUrl().startsWith(Server.get().getAddress("/m3u8?url="))) return "";
        try {
            String targetUrl = java.net.URLDecoder.decode(getUrl().split("url=")[1].split("&")[0], "UTF-8");
            String content = com.fongmi.android.tv.server.process.M3U8.getCache(targetUrl);
            if (content.contains("#EXT-X-STREAM-INF")) {
                StringBuilder sb = new StringBuilder(content);
                sb.append("\n\n--- Nested Playlists ---\n");
                String[] lines = content.split("\\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.startsWith(Server.get().getAddress("/m3u8?url="))) {
                        String subUrl = java.net.URLDecoder.decode(line.split("url=")[1].split("&")[0], "UTF-8");
                        String subContent = com.fongmi.android.tv.server.process.M3U8.getCache(subUrl);
                        if (!subContent.isEmpty()) {
                            sb.append("\nURL: ").append(subUrl).append("\n");
                            sb.append(subContent).append("\n");
                        }
                    }
                }
                return sb.toString();
            }
            return content;
        } catch (Exception e) {
            return "";
        }
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

    public void setMetadata(String title, String artist, String artUri, Drawable artwork) {
        setMetadata(buildMetadata(title, artist, artUri));
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
        return !getCurrentMediaEditions().isEmpty();
    }

    public boolean haveChapter() {
        return !getCurrentMediaChapters().isEmpty();
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

    public String getPlayerText() {
        return ResUtil.getStringArray(R.array.select_engine)[getEngine()];
    }

    public boolean isExo() {
        return getEngine() == PlayerSetting.ENGINE_EXO;
    }

    public boolean isMpv() {
        return getEngine() == PlayerSetting.ENGINE_MPV;
    }

    public boolean canAdjustSpeed() {
        return true;
    }

    public void nextPlayer() {
        setEngine(getEngine() == PlayerSetting.ENGINE_EXO ? PlayerSetting.ENGINE_MPV : PlayerSetting.ENGINE_EXO);
    }

    public void init(PlayerView view) {
        view.setPlayer(player);
    }

    public void setPlayer(int engine) {
        setEngine(engine);
    }

    public void setEngine(int targetEngine) {
        int oldEngine = getEngine();
        PlayerSetting.putEngine(targetEngine);
        if (oldEngine == targetEngine || isEmpty()) return;
        startCurrent();
    }

    public String getPositionTime(long delta) {
        return Util.timeMs(Math.max(0, Math.min(getPosition() + delta, getDuration())));
    }

    public long getBuffered() {
        return player.getBufferedPosition();
    }

    public String stringToTime(long time) {
        return Util.timeMs(time);
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

    public void selectChapter(MediaChapter chapter) {
    }

    public void selectEdition(MediaEdition edition) {
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
        return setSpeed(Math.max(0.25f, Math.min(getSpeed() + value, 5.0f)));
    }

    public String subSpeed(float value) {
        return setSpeed(Math.max(0.25f, Math.min(getSpeed() - value, 5.0f)));
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

    public void reset() {
        App.removeCallbacks(runnable);
        retry = 0;
    }

    public void clear() {
        spec = null;
    }

    public void resetTrack() {
        TrackUtil.reset(player);
    }

    public int addRetry() {
        return ++retry;
    }

    public void choose(Activity activity, CharSequence title) {
        PlayerHelper.choose(activity, getUrl(), getHeaders(), isVod(), getPosition(), title);
    }

    public void setMediaSource(String url) {
        start(PlaySpec.from(url, null, null, null), Constant.TIMEOUT_PLAY);
    }

    public void setMediaSource() {
        startCurrent();
    }

    public void toggleDecode() {
        decode = isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        boolean rebuild = engine.setDecode(decode);
        callback.onDecodeChanged();
        if (!rebuild) return;
        setPlayer(engine.rebuild());
        startCurrent(getPosition());
    }

    public void toggleDecode(boolean save) {
        if (save) toggleDecode();
        else {
            decode = isHard() ? PlayerEngine.SOFT : PlayerEngine.HARD;
            boolean rebuild = engine.setDecode(decode);
            callback.onDecodeChanged();
            if (!rebuild) return;
            setPlayer(engine.rebuild());
            startCurrent(getPosition());
        }
    }

    public boolean canToggleDecode() {
        return true;
    }

    private void handleDecodeError(PlaybackException e) {
        if (++retry > 1) {
            callback.onError(engine.getErrorMessage(e));
        } else {
            Notify.show(R.string.error_decode_fallback);
            toggleDecode();
        }
    }

    private boolean isHard() {
        return decode == PlayerEngine.HARD;
    }

    private void onPlayTimeout() {
        stop();
        callback.onError(ResUtil.getString(R.string.error_play_timeout));
    }

    private void ensureEngine(PlaySpec spec) {
        if (PlayerEngineFactory.matches(engine, spec)) return;
        PlayerEngine old = engine;
        player.removeListener(listener);
        engine = PlayerEngineFactory.create(decode, spec, listener);
        setPlayer(engine.getPlayer());
        old.release();
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
        ensureEngine(spec.checkUa().checkProxy());
        engine.start(spec, startPositionMs);
        App.post(runnable, timeout);
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
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY || state == Player.STATE_ENDED) App.removeCallbacks(runnable);
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

        //@Override
        public void onMediaChaptersChanged(@NonNull List<MediaChapter> chapters) {
            callback.onMediaOptionsChanged();
        }

        //@Override
        public void onMediaEditionsChanged(@NonNull List<MediaEdition> editions) {
            callback.onMediaOptionsChanged();
        }

        @Override
        public void onPlayerError(@NonNull PlaybackException e) {
            App.removeCallbacks(runnable);
            if (spec == null) return;
            switch (engine.handleError(e)) {
                case DECODE -> handleDecodeError(e);
                case RECOVERED -> {}
                case FATAL -> callback.onError(engine.getErrorMessage(e));
            }
        }
    };
}
