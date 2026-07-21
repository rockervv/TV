package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.media3.common.C;

import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.api.LiveApi;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.exception.ExtractException;
import com.fongmi.android.tv.playback.live.LivePlaybackController;
import com.fongmi.android.tv.playback.live.LivePlaybackHost;
import com.fongmi.android.tv.playback.live.LivePlaybackState;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class LiveViewModel extends ViewModel {

    private final MutableLiveData<Boolean> xml;
    private final MutableLiveData<Result> url;
    private final MutableLiveData<Live> live;
    private final MutableLiveData<Epg> epg;
    private final MutableLiveData<String> epgInfo;
    private final MutableLiveData<List<Group>> groups;
    private final MutableLiveData<Group> group;
    private final MutableLiveData<Channel> channel;

    private final ViewModelTaskRunner<TaskType> tasks;
    private final LivePlaybackState playbackState;
    private LivePlaybackController playbackController;
    private List<Group> hides;
    private volatile ZoneId zoneId;

    public LiveViewModel() {
        this.epg = new MutableLiveData<>();
        this.xml = new MutableLiveData<>();
        this.url = new MutableLiveData<>();
        this.live = new MutableLiveData<>();
        this.groups = new MutableLiveData<>();
        this.group = new MutableLiveData<>();
        this.channel = new MutableLiveData<>();
        this.epgInfo = new MutableLiveData<>();
        this.zoneId = ZoneId.systemDefault();
        this.hides = new ArrayList<>();
        this.playbackState = new LivePlaybackState();
        this.tasks = new ViewModelTaskRunner<>(TaskType.class);
    }

    public LiveData<Result> url() {
        return url;
    }

    public LiveData<Boolean> xml() {
        return xml;
    }

    public LiveData<Epg> epg() {
        return epg;
    }

    public LiveData<String> epgInfo() {
        return epgInfo;
    }

    public LiveData<Live> live() {
        return live;
    }

    public LiveData<List<Group>> getGroups() {
        return groups;
    }

    public List<Group> getHides() {
        return hides;
    }

    public LiveData<Group> getGroup() {
        return group;
    }

    public LiveData<Channel> getChannel() {
        return channel;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public LivePlaybackController getPlaybackController() {
        return playbackController;
    }

    public LivePlaybackController createPlaybackController(LivePlaybackHost host) {
        return playbackController = new LivePlaybackController(host, playbackState);
    }

    public void setGroup(Group group) {
        this.group.setValue(group);
    }

    public void setGroups(List<Group> items) {
        this.groups.setValue(items);
    }

    public void setChannel(Channel channel) {
        this.channel.setValue(channel);
    }

    public void nextChannel() {
        if (playbackController != null) playbackController.nextChannel();
    }

    public void prevChannel() {
        if (playbackController != null) playbackController.prevChannel();
    }

    public void nextLine(boolean show) {
        if (playbackController != null) playbackController.nextLine(show);
    }

    public void prevLine() {
        if (playbackController != null) playbackController.prevLine();
    }

    public void selectGroup(Group group) {
        if (playbackController != null) playbackController.selectGroup(group);
    }

    public void selectChannel(Channel channel) {
        if (playbackController != null) playbackController.selectChannel(channel);
    }

    public void selectEpg(EpgData data, long position) {
        if (playbackController != null) playbackController.selectEpg(data, position);
    }

    public void refresh(long position) {
        if (playbackController != null) playbackController.refresh(position);
    }

    public void parse(Live item) {
        execute(TaskType.LIVE, () -> {
            LiveApi.parse(item);
            return item;
        }, result -> {
            setTimeZone(result);
            processGroup(result);
            live.postValue(result);
        }, this::handleParseError);
    }

    private void processGroup(Live live) {
        List<Group> items = new ArrayList<>();
        this.hides = new ArrayList<>();
        for (Group group : live.getGroups()) (group.isHidden() ? hides : items).add(group);
        this.playbackState.setGroups(items);
        this.groups.postValue(items);
    }

    public void parseXml(Live item) {
        execute(TaskType.XML, () -> LiveApi.parseXml(item), xml::postValue, error -> xml.postValue(false));
    }

    public void getEpg(Channel item) {
        if (item == null) return;
        execute(TaskType.EPG, () -> LiveApi.getEpg(item, zoneId), result -> {
            epg.postValue(result);
            processEpg(item, result);
        }, error -> epg.postValue(new Epg()));
    }

    private void processEpg(Channel channel, Epg epg) {
        EpgData data = epg.getEpgData();
        if (data.getTitle().isEmpty()) epgInfo.postValue(channel.getShow());
        else epgInfo.postValue(com.fongmi.android.tv.utils.ResUtil.getString(com.fongmi.android.tv.R.string.detail_title, channel.getShow(), data.getTitle()));
    }

    public void getUrl(Channel item) {
        getUrl(item, C.TIME_UNSET);
    }

    public void getUrl(Channel item, long startPositionMs) {
        requestUrl(() -> LiveApi.getUrl(item), startPositionMs);
    }

    public void getUrl(Channel item, EpgData data) {
        getUrl(item, data, C.TIME_UNSET);
    }

    public void getUrl(Channel item, EpgData data, long startPositionMs) {
        requestUrl(() -> LiveApi.getUrl(item, data), startPositionMs);
    }

    private void requestUrl(Callable<Result> callable, long startPositionMs) {
        execute(TaskType.URL, callable, result -> postUrl(result, startPositionMs), error -> handleUrlError(error, startPositionMs));
    }

    private void postUrl(Result result, long startPositionMs) {
        if (startPositionMs != C.TIME_UNSET) result.setPosition(startPositionMs);
        url.postValue(result);
    }

    private void handleParseError(Throwable t) {
        if (t instanceof ExtractException) postUrl(Result.error(t.getMessage()), C.TIME_UNSET);
        else live.postValue(new Live());
    }

    private void handleUrlError(Throwable t, long startPositionMs) {
        if (t instanceof ExtractException) postUrl(Result.error(t.getMessage()), startPositionMs);
        else postUrl(new Result(), startPositionMs);
    }

    private void setTimeZone(Live live) {
        this.zoneId = live.getZoneId();
    }

    public void setPass(String pass) {
        boolean first = true;
        List<Group> items = new ArrayList<>(groups.getValue());
        Iterator<Group> iterator = hides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            items.add(item);
            if (first) selectGroup(item);
            iterator.remove();
            first = false;
        }
        groups.postValue(items);
    }

    private <T> void execute(TaskType type, Callable<T> callable, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        tasks.execute(type, type.timeout, callable, onSuccess, onError);
    }

    @Override
    protected void onCleared() {
        tasks.cancelAll();
        playbackState.reset();
    }

    private enum TaskType {

        LIVE(Constant.TIMEOUT_LIVE),
        EPG(Constant.TIMEOUT_EPG),
        XML(Constant.TIMEOUT_XML),
        URL(Constant.TIMEOUT_PARSE_LIVE);

        final long timeout;

        TaskType(long timeout) {
            this.timeout = timeout;
        }
    }
}
