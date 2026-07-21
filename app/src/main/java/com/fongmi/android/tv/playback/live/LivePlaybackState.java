package com.fongmi.android.tv.playback.live;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Result;

import java.util.ArrayList;
import java.util.List;

public class LivePlaybackState {

    private List<Group> groups;
    private LivePlayRequest pendingRequest;
    private Result result;
    private Channel channel;
    private Group group;

    public void reset() {
        pendingRequest = null;
        groups = new ArrayList<>();
        channel = null;
        result = null;
        group = null;
    }

    public List<Group> getGroups() {
        return groups == null ? new ArrayList<>() : groups;
    }

    public void setGroups(List<Group> groups) {
        this.groups = groups;
    }

    public int getGroupCount() {
        return getGroups().size();
    }

    public int getGroupPosition() {
        return getGroups().indexOf(getGroup());
    }

    public Group getGroup(int position) {
        return getGroups().get(position);
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
        this.group = channel != null ? channel.getGroup() : group;
    }

    @Nullable
    public Result getResult() {
        return result;
    }

    public void setResult(Result result) {
        this.result = result;
    }

    @Nullable
    public LivePlayRequest getPendingRequest() {
        return pendingRequest;
    }

    public void setPendingRequest(LivePlayRequest pendingRequest) {
        this.pendingRequest = pendingRequest;
    }

    public void clearPendingRequest() {
        this.pendingRequest = null;
    }
}
