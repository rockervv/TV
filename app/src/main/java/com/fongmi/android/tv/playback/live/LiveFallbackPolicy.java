package com.fongmi.android.tv.playback.live;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.setting.LiveSetting;

class LiveFallbackPolicy {

    private final LivePlaybackController controller;
    private final LivePlaybackState state;
    private final LivePlaybackHost host;

    LiveFallbackPolicy(LivePlaybackController controller, LivePlaybackState state, LivePlaybackHost host) {
        this.controller = controller;
        this.state = state;
        this.host = host;
    }

    void playbackError() {
        Channel channel = state.getChannel();
        android.util.Log.w("LiveDebug", ">>> [playbackError] Fallback Triggered. Auto-Change: " + LiveSetting.isChange());
        if (!LiveSetting.isChange() || channel == null || channel.isLast()) return;
        android.util.Log.i("LiveDebug", ">>> [playbackError] Attempting next line...");
        controller.nextLine(true);
    }

    private long lastEndedTime;

    void playbackEnded() {
        long now = System.currentTimeMillis();
        if (now - lastEndedTime < 3000) {
            android.util.Log.w("LiveDebug", ">>> [playbackEnded] Loop detected! Ignoring to prevent infinite refresh.");
            return;
        }
        lastEndedTime = now;
        if (host.isPlayerLive()) checkNext();
        else controller.nextChannel();
    }

    private void checkNext() {
        Channel channel = state.getChannel();
        if (channel == null) return;
        EpgData data = host.getNextEpgData(channel);
        if (data != null && controller.selectEpg(data)) return;
        data = host.getCurrentEpgData(channel);
        if (data != null) host.renderEpgSelection(channel, data);
        controller.refresh();
    }
}
