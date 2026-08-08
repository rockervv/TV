package com.fongmi.android.tv.player.exo;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;

import java.util.concurrent.TimeUnit;

public class ExoPlayerEngine implements PlayerEngine {

    private final ErrorMsgProvider provider;
    private final Player.Listener listener;
    private final PreCache preCache;
    private ExoPlayer player;
    private PlaySpec spec;
    private int decode;

    public ExoPlayerEngine(int decode, Player.Listener listener) {
        this.player = ExoUtil.buildPlayer(decode, listener);
        this.provider = new ErrorMsgProvider();
        this.preCache = new PreCache();
        this.listener = listener;
        this.decode = decode;
    }

    @Override
    public Type getType() {
        return Type.EXO;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void release() {
        preCache.release();
        player.release();
    }

    @Override
    public Player rebuild() {
        preCache.stop();
        player.release();
        return player = ExoUtil.buildPlayer(decode, listener);
    }

    @Override
    public boolean setDecode(int decode) {
        this.decode = decode;
        return true;
    }

    @Override
    public void start(PlaySpec spec, long startPositionMs) {
        this.spec = spec;
        startInternal(startPositionMs);
    }

    @Override
    public void setSubtitleStyle() {
        // Handled by calling Activity
    }

    @Override
    public void setRender(int mode, int intensity) {
        ExoUtil.setRender(player, mode, intensity);
    }

    @Override
    public void stop() {
        preCache.stop();
        player.stop();
    }

    @Override
    public boolean isLive() {
        return player.getDuration() < TimeUnit.MINUTES.toMillis(1) || player.isCurrentMediaItemLive();
    }

    @Override
    public boolean isVod() {
        return player.getDuration() > TimeUnit.MINUTES.toMillis(1) && !player.isCurrentMediaItemLive();
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        if (isConnectionRefused(e)) return com.fongmi.android.tv.utils.ResUtil.getString(R.string.error_play_refused);
        return provider.get(e);
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        if (e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED || e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED) {
            if (e.getMessage() != null && (e.getMessage().contains("NO_EXCEEDS_CAPABILITIES") || e.getMessage().contains("avc1.6E"))) {
                return ErrorAction.FALLBACK;
            }
        }
        if (isConnectionRefused(e)) return ErrorAction.FATAL;
        return switch (e.errorCode) {
            case PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> ErrorAction.SEEK;
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED, PlaybackException.ERROR_CODE_DECODING_FAILED -> ErrorAction.FALLBACK;
            case PlaybackException.ERROR_CODE_IO_UNSPECIFIED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED, PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> ErrorAction.FORMAT;
            default -> ErrorAction.FATAL;
        };
    }

    private boolean isConnectionRefused(PlaybackException e) {
        String message = e.getMessage() != null ? e.getMessage() : "";
        if (message.contains("ECONNREFUSED") || (message.contains("127.0.0.1") && !message.contains(String.valueOf(com.github.catvod.Proxy.getPort())))) return true;
        Throwable t = e.getCause();
        while (t != null) {
            String cMsg = t.getMessage() != null ? t.getMessage() : "";
            if (cMsg.contains("ECONNREFUSED") || (cMsg.contains("127.0.0.1") && !cMsg.contains(String.valueOf(com.github.catvod.Proxy.getPort())))) return true;
            t = t.getCause();
        }
        return false;
    }

    private void startInternal(long position) {
        MediaItem item = MediaItemFactory.from(spec, decode);
        player.setMediaItem(item, position);
        preCache.start(player, item);
        player.prepare();
        player.play();
    }
}
