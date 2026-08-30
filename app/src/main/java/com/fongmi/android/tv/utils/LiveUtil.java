package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.github.catvod.utils.Path;
import com.google.gson.JsonObject;

import java.io.File;

public class LiveUtil {

    public static Live getMyLive() {
        try {
            File file = new File(Path.tv(), "my_live.json");
            if (!file.exists()) return createMyLive();
            return Live.objectFrom(App.gson().fromJson(Path.read(file), JsonObject.class), "");
        } catch (Exception e) {
            return createMyLive();
        }
    }

    private static Live createMyLive() {
        Live live = new Live();
        live.setName("我的頻道");
        live.setType("virtual");
        return live;
    }

    public static void save(Live live) {
        try {
            File file = new File(Path.tv(), "my_live.json");
            Path.write(file, App.gson().toJson(live));
        } catch (Exception ignored) {
        }
    }

    public static void addChannel(String groupName, Channel channel) {
        Live myLive = getMyLive();
        Group targetGroup = null;
        for (Group g : myLive.getGroups()) {
            if (g.getName().equals(groupName)) {
                targetGroup = g;
                break;
            }
        }
        if (targetGroup == null) {
            targetGroup = Group.create(groupName, false);
            myLive.getGroups().add(targetGroup);
        }
        if (targetGroup.find(channel.getName()) == -1) {
            Channel copy = Channel.create(channel);
            copy.setOriginConfig(LiveConfig.getUrl());
            copy.setOriginGroup(channel.getGroup().getName());
            copy.setOriginName(channel.getName());
            targetGroup.getChannel().add(copy);
            save(myLive);
        }
    }

    public static void removeChannel(Channel channel) {
        Live myLive = getMyLive();
        for (Group g : myLive.getGroups()) {
            if (g.getName().equals(channel.getGroup().getName())) {
                g.getChannel().remove(channel);
                break;
            }
        }
        save(myLive);
    }

    public static void updateChannel(Channel old, Channel item) {
        Live myLive = getMyLive();
        for (Group g : myLive.getGroups()) {
            if (g.getName().equals(old.getGroup().getName())) {
                int index = g.getChannel().indexOf(old);
                if (index != -1) {
                    g.getChannel().set(index, item);
                    break;
                }
            }
        }
        save(myLive);
    }

    public static void moveChannel(Channel channel, boolean up) {
        Live myLive = getMyLive();
        for (Group g : myLive.getGroups()) {
            if (g.getName().equals(channel.getGroup().getName())) {
                int index = -1;
                for (int i = 0; i < g.getChannel().size(); i++) {
                    if (g.getChannel().get(i).getName().equals(channel.getName())) {
                        index = i;
                        break;
                    }
                }
                if (index != -1) {
                    int target = up ? index - 1 : index + 1;
                    if (target >= 0 && target < g.getChannel().size()) {
                        java.util.Collections.swap(g.getChannel(), index, target);
                    }
                }
                break;
            }
        }
        save(myLive);
    }
}
