package com.fongmi.android.tv.service;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.Player;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.player.PlayerManager;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class PlaybackService extends MediaSessionService {

    public static final String LOCAL_BIND_ACTION = "com.fongmi.android.tv.service.PlaybackService.LocalBind";

    private final List<PlayerCallback> playerCallbacks = new ArrayList<>();
    private NavigationCallback navigationCallback;
    private MediaSession mediaSession;
    private String playbackKey;
    private PlayerManager player;

    public class LocalBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        player = PlayerManager.create(new PlayerManager.Callback() {
            @Override
            public void onPrepare() {
                for (PlayerCallback callback : playerCallbacks) callback.onPrepare();
            }

            @Override
            public void onTracksChanged() {
                for (PlayerCallback callback : playerCallbacks) callback.onTracksChanged();
            }

            @Override
            public void onDecodeChanged() {
                for (PlayerCallback callback : playerCallbacks) callback.onDecodeChanged();
            }

            @Override
            public void onMediaOptionsChanged() {
                for (PlayerCallback callback : playerCallbacks) callback.onMediaOptionsChanged();
            }

            @Override
            public void onError(String msg) {
                for (PlayerCallback callback : playerCallbacks) callback.onError(msg);
            }

            @Override
            public void onPlayerRebuild(Player player) {
                for (PlayerCallback callback : playerCallbacks) callback.onPlayerRebuild(player);
                if (mediaSession != null) mediaSession.setPlayer(player);
            }
        });
        mediaSession = new MediaSession.Builder(this, player.getPlayer()).build();
    }

    public PlayerManager player() {
        return player;
    }

    public void addPlayerCallback(PlayerCallback callback) {
        playerCallbacks.add(callback);
    }

    public void removePlayerCallback(PlayerCallback callback) {
        playerCallbacks.remove(callback);
    }

    public boolean hasPlayerCallback() {
        return !playerCallbacks.isEmpty();
    }

    public void setNavigationCallback(NavigationCallback callback, String key) {
        this.navigationCallback = callback;
        this.playbackKey = key;
    }

    public void replaceBinding(Runnable callback) {
    }

    public void setSessionActivity(PendingIntent intent) {
        if (mediaSession != null) mediaSession.setSessionActivity(intent);
    }

    public void resetSessionActivity() {
        if (mediaSession != null) mediaSession.setSessionActivity(null);
    }

    public boolean hasMediaClient() {
        return false;
    }

    public void suspend() {
    }

    public void shutdown() {
        stopSelf();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        EventBus.getDefault().unregister(this);
        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && LOCAL_BIND_ACTION.equals(intent.getAction())) return new LocalBinder();
        return super.onBind(intent);
    }

    public interface PlayerCallback {
        void onPrepare();

        void onTracksChanged();

        void onDecodeChanged();

        void onMediaOptionsChanged();

        void onError(String msg);

        void onPlayerRebuild(Player player);
    }

    public interface NavigationCallback {
        void onNext();

        void onPrev();

        void onStop();

        void onReplay();
    }
}
