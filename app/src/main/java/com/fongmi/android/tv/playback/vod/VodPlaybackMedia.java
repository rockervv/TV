package com.fongmi.android.tv.playback.vod;

import androidx.media3.common.MediaMetadata;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.player.PlayerManager;

public final class VodPlaybackMedia {

    public static MediaMetadata metadata(History history, Episode episode) {
        String title = history.getVodName();
        String name = episode.getName();
        boolean empty = name.isEmpty() || title.equals(name);
        String artist = empty ? "" : name;
        return PlayerManager.buildMetadata(title, artist, history.getVodPic());
    }
}
