package com.fongmi.android.tv.service;

import androidx.annotation.NonNull;
import androidx.media3.session.MediaSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class MediaClients {

    private final Map<String, Integer> controllers = new ConcurrentHashMap<>();
    private volatile boolean browserBound;

    void bind() {
        browserBound = true;
    }

    void unbind() {
        browserBound = false;
    }

    void connect(@NonNull MediaSession.ControllerInfo controller, @NonNull String packageName) {
        if (!isSelf(controller, packageName)) {
            Integer count = controllers.get(controller.getPackageName());
            controllers.put(controller.getPackageName(), count == null ? 1 : count + 1);
        }
    }

    void disconnect(@NonNull MediaSession.ControllerInfo controller) {
        Integer count = controllers.get(controller.getPackageName());
        if (count != null) {
            if (count > 1) controllers.put(controller.getPackageName(), count - 1);
            else controllers.remove(controller.getPackageName());
        }
    }

    boolean hasAny() {
        return browserBound || !controllers.isEmpty();
    }

    boolean isSelf(@NonNull MediaSession.ControllerInfo controller, @NonNull String packageName) {
        return packageName.equals(controller.getPackageName());
    }
}
