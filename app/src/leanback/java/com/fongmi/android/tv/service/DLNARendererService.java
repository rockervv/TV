package com.fongmi.android.tv.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.media3.common.Player;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.dlna.DLNAAvTransportImpl;
import com.fongmi.android.tv.dlna.DLNARenderingControlImpl;
import com.fongmi.android.tv.dlna.RenderState;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.server.Server;

import com.fongmi.android.tv.utils.ResUtil;

import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.LocalService;
import org.jupnp.model.types.DeviceType;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDN;
import org.jupnp.support.connectionmanager.ConnectionManagerService;

public class DLNARendererService extends AndroidUpnpServiceImpl {

    private final String CHANNEL_ID = "com.fongmi.android.tv.DLNA";
    private final String DEVICE_TYPE = "MediaRenderer";
    private final int NOTIFICATION_ID = 1000;

    private DLNAAvTransportImpl avTransportImpl;
    private DLNARenderingControlImpl renderingControlImpl;
    private Runnable positionUpdater;
    private boolean dlnaActive;

    public static void start(Context context) {
        if (!com.fongmi.android.tv.setting.Setting.isDlna()) return;
        try {
            Intent intent = new Intent(context, DLNARendererService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class LocalBinder extends android.os.Binder {
        public DLNARendererService getService() {
            return DLNARendererService.this;
        }
    }

    @Override
    public android.os.IBinder onBind(android.content.Intent intent) {
        return new LocalBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, getNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, getNotification());
        }
        avTransportImpl = new DLNAAvTransportImpl(this);
        renderingControlImpl = new DLNARenderingControlImpl(this);
        positionUpdater = this::updatePosition;
        registerLocalDevice();
    }

    private Notification getNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_LOW);
            channel.enableLights(false);
            channel.enableVibration(false);
            channel.setShowBadge(false);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_banner).setPriority(NotificationCompat.PRIORITY_LOW).build();
    }

    private void registerLocalDevice() {
        try {
            LocalService avTransportService = new AnnotationLocalServiceBinder().read(DLNAAvTransportImpl.class);
            LocalService renderingControlService = new AnnotationLocalServiceBinder().read(DLNARenderingControlImpl.class);
            LocalService connectionManagerService = new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
            avTransportService.setManager(new org.jupnp.model.DefaultServiceManager<>(avTransportService, DLNAAvTransportImpl.class) {
                @Override
                protected DLNAAvTransportImpl createServiceInstance() {
                    return avTransportImpl;
                }
            });
            renderingControlService.setManager(new org.jupnp.model.DefaultServiceManager<>(renderingControlService, DLNARenderingControlImpl.class) {
                @Override
                protected DLNARenderingControlImpl createServiceInstance() {
                    return renderingControlImpl;
                }
            });
            connectionManagerService.setManager(new org.jupnp.model.DefaultServiceManager<>(connectionManagerService, ConnectionManagerService.class));
            DeviceType type = new UDADeviceType(DEVICE_TYPE, 1);
            LocalDevice localDevice = new LocalDevice(new org.jupnp.model.meta.DeviceIdentity(UDN.uniqueSystemIdentifier(DEVICE_TYPE)), type, new org.jupnp.model.meta.DeviceDetails(ResUtil.getString(R.string.app_name), new org.jupnp.model.meta.ManufacturerDetails(Build.MANUFACTURER), new org.jupnp.model.meta.ModelDetails(Build.MODEL, ResUtil.getString(R.string.app_name), "1.0")), new LocalService[]{avTransportService, renderingControlService, connectionManagerService});
            upnpService.getRegistry().addDevice(localDevice);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setDlnaActive(boolean active) {
        this.dlnaActive = active;
        this.avTransportImpl.setDlnaActive(active);
        if (active) {
            updatePlayer();
        } else {
            avTransportImpl.setPlayer(null);
            App.removeCallbacks(positionUpdater);
        }
    }

    private void updatePlayer() {
        PlayerManager player = Server.get().getPlayer();
        avTransportImpl.setPlayer(player);
        if (player != null) {
            App.post(positionUpdater, 1000);
        }
    }

    private void updatePosition() {
        PlayerManager player = Server.get().getPlayer();
        if (player != null && dlnaActive) {
            avTransportImpl.updatePositionCache(player.getPosition(), player.getDuration());
            notifyState(player);
        }
        App.post(positionUpdater, 1000);
    }

    public void notifyState(PlayerManager player) {
        RenderState state = RenderState.IDLE;
        if (player.isReleased()) {
            state = RenderState.IDLE;
        } else if (player.isPlaying()) {
            state = RenderState.PLAYING;
        } else if (player.isEnded()) {
            state = avTransportImpl.hasNext() ? RenderState.PREPARING : RenderState.STOPPED;
        } else {
            state = RenderState.PAUSED;
        }
        avTransportImpl.fireStateChange(state);
    }

    public long consumePendingSeekMs() {
        return avTransportImpl.consumePendingSeekMs();
    }

    @Override
    public void onDestroy() {
        App.removeCallbacks(positionUpdater);
        super.onDestroy();
    }
}
