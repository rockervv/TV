package com.fongmi.android.tv.server;

import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.service.PlaybackService;
import com.github.catvod.utils.Util;

public class Server {

    private PlayerManager player;
    private PlaybackService service;
    private Nano nano;
    private int port;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public Server() {
        this.port = 9978;
    }

    public int getPort() {
        return port;
    }

    public PlayerManager getPlayer() {
        return player;
    }

    public void setPlayer(PlayerManager player) {
        this.player = player;
    }

    public PlaybackService getService() {
        return service;
    }

    public void setService(PlaybackService service) {
        this.service = service;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getAddress(int tab) {
        return getAddress(false) + "?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + getPort();
    }

    public void start() {
        if (nano != null) return;
        android.util.Log.d("TV_FATAL", "Server.start() loop BEGIN");
        do {
            try {
                nano = new Nano(port);
                com.github.catvod.Proxy.set(port);
                nano.start();
                android.util.Log.d("TV_FATAL", "Server.start() success on port: " + port);
                break;
            } catch (Exception e) {
                android.util.Log.d("TV_FATAL", "Server.start() failed on port: " + port + ", trying next...");
                ++port;
                if (nano != null) nano.stop();
                nano = null;
            }
        } while (port < 9999);
        android.util.Log.d("TV_FATAL", "Server.start() loop END");
    }

    public void stop() {
        if (nano != null) nano.stop();
        nano = null;
    }
}
