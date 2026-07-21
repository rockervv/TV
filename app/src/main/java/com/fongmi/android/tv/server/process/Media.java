package com.fongmi.android.tv.server.process;

import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import android.text.TextUtils;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.server.Nano;
import com.fongmi.android.tv.server.Server;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.Objects;

import fi.iki.elonen.NanoHTTPD;

public class Media implements Process {

    @Override
    public boolean isRequest(NanoHTTPD.IHTTPSession session, String path) {
        return "/media".equals(path);
    }

    @Override
    public NanoHTTPD.Response doResponse(NanoHTTPD.IHTTPSession session, String path, Map<String, String> files) {
        if (isNull()) return Nano.ok("{}");
        JsonObject result = new JsonObject();
        result.addProperty("url", getUrl());
        result.addProperty("state", getState());
        result.addProperty("speed", getSpeed());
        result.addProperty("title", getTitle());
        result.addProperty("artist", getArtist());
        result.addProperty("artwork", getArtUri());
        result.addProperty("duration", getDuration());
        result.addProperty("position", getPosition());
        return Nano.ok(result.toString());
    }

    private PlayerManager getPlayer() {
        return Server.get().getPlayer();
    }

    private boolean isNull() {
        return Objects.isNull(getPlayer()) || getPlayer().isReleased();
    }

    private String getUrl() {
        return TextUtils.isEmpty(getPlayer().getUrl()) ? "" : getPlayer().getUrl();
    }

    private String getTitle() {
        MediaMetadata metadata = getPlayer().getMetadata();
        return metadata == null || TextUtils.isEmpty(metadata.title) ? "" : metadata.title.toString();
    }

    private String getArtist() {
        MediaMetadata metadata = getPlayer().getMetadata();
        return metadata == null || TextUtils.isEmpty(metadata.artist) ? "" : metadata.artist.toString();
    }

    private String getArtUri() {
        MediaMetadata metadata = getPlayer().getMetadata();
        return metadata == null || metadata.artworkUri == null ? "" : metadata.artworkUri.toString();
    }

    private long getDuration() {
        return getPlayer().getDuration();
    }

    private int getState() {
        if (getPlayer().isPlaying()) return 3;
        if (getPlayer().getPlaybackState() == Player.STATE_BUFFERING) return 6;
        if (getPlayer().getPlaybackState() == Player.STATE_ENDED) return 1;
        return 2;
    }

    private long getPosition() {
        return getPlayer().getPosition();
    }

    private float getSpeed() {
        return getPlayer().getSpeed();
    }
}
