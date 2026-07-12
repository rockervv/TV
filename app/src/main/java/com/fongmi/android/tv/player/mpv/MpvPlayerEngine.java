package com.fongmi.android.tv.player.mpv;

import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;
import java.util.concurrent.TimeUnit;

public class MpvPlayerEngine implements PlayerEngine {

    public MpvPlayerEngine(int decode, Player.Listener listener) {
    }

    public static boolean isAvailable() {
        return false;
    }

    @Override
    public Type getType() {
        return Type.MPV;
    }

    @Override
    public Player getPlayer() {
        return null;
    }

    @Override
    public void release() {
    }

    @Override
    public Player rebuild() {
        return null;
    }

    @Override
    public boolean setDecode(int decode) {
        return false;
    }

    @Override
    public void start(PlaySpec spec, long startPositionMs) {
    }

    @Override
    public boolean isLive() {
        return false;
    }

    @Override
    public boolean isVod() {
        return false;
    }

    @Override
    public String getErrorMessage(PlaybackException e) {
        return "";
    }

    @Override
    public ErrorAction handleError(PlaybackException e) {
        return ErrorAction.FATAL;
    }
}
