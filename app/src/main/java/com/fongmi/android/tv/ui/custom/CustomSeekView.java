package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.ui.DefaultTimeBar;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.utils.Util;

import java.util.concurrent.TimeUnit;

public class CustomSeekView extends FrameLayout implements TimeBar.OnScrubListener {

    private static final int MAX_UPDATE_INTERVAL_MS = 1000;
    private static final int MIN_UPDATE_INTERVAL_MS = 200;

    private TextView positionView;
    private TextView durationView;
    private DefaultTimeBar timeBar;

    private Runnable refresh;
    private Player exoPlayer;
    private PlayerManager player;

    private long currentDuration;
    private long currentPosition;
    private long currentBuffered;
    private boolean scrubbing;

    public CustomSeekView(Context context) {
        this(context, null);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomSeekView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.view_control_seek, this);
        init();
        start();
    }

    private void init() {
        positionView = findViewById(R.id.position);
        durationView = findViewById(R.id.duration);
        timeBar = findViewById(R.id.timeBar);
        timeBar.addListener(this);
        refresh = this::refresh;
    }

    public void setPlayer(Player exoPlayer) {
        this.exoPlayer = exoPlayer;
    }

    public void setListener(PlayerManager player) {
        this.player = player;
    }

    public DefaultTimeBar getTimeBar() {
        return timeBar;
    }

    private void start() {
        removeCallbacks(refresh);
        post(refresh);
    }

    private void refresh() {
        if (player == null && exoPlayer == null) return;
        if (player != null && player.isReleased()) return;
        long duration = player != null ? player.getDuration() : exoPlayer.getDuration();
        long position = player != null ? player.getPosition() : exoPlayer.getCurrentPosition();
        long buffered = player != null ? player.getBuffered() : exoPlayer.getBufferedPosition();
        boolean positionChanged = position != currentPosition;
        boolean durationChanged = duration != currentDuration;
        boolean bufferedChanged = buffered != currentBuffered;
        currentDuration = duration;
        currentPosition = position;
        currentBuffered = buffered;
        if (durationChanged) {
            setKeyTimeIncrement(duration);
            timeBar.setDuration(duration);
            durationView.setText(Util.timeMs(duration < 0 ? 0 : duration));
        }
        if (positionChanged && !scrubbing) {
            timeBar.setPosition(position);
            positionView.setText(Util.timeMs(position < 0 ? 0 : position));
        }
        if (bufferedChanged) {
            timeBar.setBufferedPosition(buffered);
        }
        if ((player != null && player.isEmpty()) || (exoPlayer != null && exoPlayer.getPlaybackState() == Player.STATE_IDLE)) {
            positionView.setText("00:00");
            durationView.setText("00:00");
            timeBar.setPosition(currentDuration = 0);
            timeBar.setDuration(currentDuration = 0);
        }
        removeCallbacks(refresh);
        if ((player != null && player.isPlaying()) || (exoPlayer != null && exoPlayer.isPlaying())) {
            postDelayed(refresh, delayMs(position));
        } else {
            postDelayed(refresh, MAX_UPDATE_INTERVAL_MS);
        }
    }

    private void setKeyTimeIncrement(long duration) {
        if (duration > TimeUnit.HOURS.toMillis(2)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(5));
        } else if (duration > TimeUnit.HOURS.toMillis(1)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(3));
        } else if (duration > TimeUnit.MINUTES.toMillis(30)) {
            timeBar.setKeyTimeIncrement(TimeUnit.MINUTES.toMillis(1));
        } else if (duration > TimeUnit.MINUTES.toMillis(15)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(30));
        } else if (duration > TimeUnit.MINUTES.toMillis(10)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(15));
        } else if (duration > TimeUnit.MINUTES.toMillis(5)) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(10));
        } else if (duration > 0) {
            timeBar.setKeyTimeIncrement(TimeUnit.SECONDS.toMillis(5));
        }
    }

    private long delayMs(long position) {
        long mediaTimeUntilNextFullSecondMs = 1000 - position % 1000;
        long mediaTimeDelayMs = Math.min(timeBar.getPreferredUpdateDelay(), mediaTimeUntilNextFullSecondMs);
        float speed = player != null ? player.getSpeed() : (exoPlayer != null ? exoPlayer.getPlaybackParameters().speed : 1.0f);
        long delayMs = (long) (mediaTimeDelayMs / speed);
        return androidx.media3.common.util.Util.constrainValue(delayMs, MIN_UPDATE_INTERVAL_MS, MAX_UPDATE_INTERVAL_MS);
    }

    private void seekToTimeBarPosition(long positionMs) {
        if (player != null) player.seekTo(positionMs);
        else if (exoPlayer != null) exoPlayer.seekTo(positionMs);
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(refresh);
    }

    @Override
    public void onScrubStart(@NonNull TimeBar timeBar, long position) {
        scrubbing = true;
        positionView.setText(Util.timeMs(position));
    }

    @Override
    public void onScrubMove(@NonNull TimeBar timeBar, long position) {
        positionView.setText(Util.timeMs(position));
    }

    @Override
    public void onScrubStop(@NonNull TimeBar timeBar, long position, boolean canceled) {
        scrubbing = false;
        if (!canceled) seekToTimeBarPosition(position);
    }
}
