package com.fongmi.android.tv.model;

import androidx.lifecycle.MutableLiveData;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.FlagScore;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.playback.vod.VodPlaybackState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlaybackViewModel extends SiteViewModel {

    private final MutableLiveData<Boolean> fullscreen;
    private final MutableLiveData<Boolean> lock;
    private final MutableLiveData<Boolean> keep;
    private final MutableLiveData<Boolean> playing;
    private final MutableLiveData<Integer> state;
    private final MutableLiveData<Vod> vod;
    private final MutableLiveData<History> history;
    private final MutableLiveData<List<Flag>> flags;
    private final MutableLiveData<List<Episode>> episodes;
    private final MutableLiveData<Flag> flag;
    private final MutableLiveData<Episode> episode;
    private final MutableLiveData<Result> quality;
    private final MutableLiveData<Boolean> qualityVisible;
    private final MutableLiveData<Boolean> useParse;
    private final MutableLiveData<String> artwork;
    private final MutableLiveData<String> description;
    private final MutableLiveData<List<Vod>> sources;
    private final VodPlaybackState playbackState;
    private VodPlaybackController playbackController;

    public PlaybackViewModel() {
        this.fullscreen = new MutableLiveData<>(false);
        this.lock = new MutableLiveData<>(false);
        this.keep = new MutableLiveData<>(false);
        this.playing = new MutableLiveData<>(false);
        this.state = new MutableLiveData<>(androidx.media3.common.Player.STATE_IDLE);
        this.vod = new MutableLiveData<>();
        this.history = new MutableLiveData<>();
        this.flags = new MutableLiveData<>();
        this.episodes = new MutableLiveData<>();
        this.flag = new MutableLiveData<>();
        this.episode = new MutableLiveData<>();
        this.quality = new MutableLiveData<>();
        this.qualityVisible = new MutableLiveData<>(false);
        this.useParse = new MutableLiveData<>(false);
        this.artwork = new MutableLiveData<>();
        this.description = new MutableLiveData<>();
        this.sources = new MutableLiveData<>();
        this.playbackState = new VodPlaybackState();
    }

    public void setPlaying(boolean playing) {
        this.playing.setValue(playing);
    }

    public void onPlayingChanged(boolean playing) {
        setPlaying(playing);
    }

    public void setState(int state) {
        this.state.setValue(state);
    }

    public void onStateChanged(int state) {
        setState(state);
        if (state == androidx.media3.common.Player.STATE_ENDED && playbackController != null) {
            playbackController.playbackEnded();
        }
    }

    public MutableLiveData<Boolean> getFullscreen() {
        return fullscreen;
    }

    public MutableLiveData<Boolean> getLock() {
        return lock;
    }

    public MutableLiveData<Boolean> getKeep() {
        return keep;
    }

    public MutableLiveData<Boolean> getPlaying() {
        return playing;
    }

    public MutableLiveData<Integer> getState() {
        return state;
    }

    public MutableLiveData<Vod> getVod() {
        return vod;
    }

    public MutableLiveData<History> getHistory() {
        return history;
    }

    public MutableLiveData<List<Flag>> getFlags() {
        return flags;
    }

    public MutableLiveData<List<Episode>> getEpisodes() {
        return episodes;
    }

    public MutableLiveData<Flag> getFlag() {
        return flag;
    }

    public MutableLiveData<Episode> getEpisode() {
        return episode;
    }

    public MutableLiveData<Result> getQuality() {
        return quality;
    }

    public MutableLiveData<Boolean> getQualityVisible() {
        return qualityVisible;
    }

    public MutableLiveData<Boolean> getUseParse() {
        return useParse;
    }

    public MutableLiveData<String> getArtwork() {
        return artwork;
    }

    public MutableLiveData<String> getDescription() {
        return description;
    }

    public MutableLiveData<List<Vod>> getSources() {
        return sources;
    }

    public VodPlaybackState getPlaybackState() {
        return playbackState;
    }

    public VodPlaybackController getPlaybackController() {
        return playbackController;
    }

    public VodPlaybackController createPlaybackController(VodPlaybackHost host) {
        playbackController = new VodPlaybackController(host, playbackState);
        playbackController.setViewModel(this);
        return playbackController;
    }

    public void setVod(Vod item) {
        android.util.Log.d("TV_FATAL", "PlaybackViewModel.setVod: " + (item != null ? item.getVodName() : "null"));
        this.vod.setValue(item);
    }

    public void setHistory(History history) {
        android.util.Log.d("TV_FATAL", "PlaybackViewModel.setHistory: " + (history != null ? history.getVodName() : "null"));
        this.history.setValue(history);
    }

    public void setFlags(List<Flag> items) {
        android.util.Log.d("TV_FATAL", "PlaybackViewModel.setFlags: " + (items != null ? items.size() : "0") + " flags");
        this.flags.setValue(items);
    }

    public void setEpisodes(List<Episode> items) {
        android.util.Log.d("TV_FATAL", "PlaybackViewModel.setEpisodes: " + (items != null ? items.size() : "0") + " episodes");
        this.episodes.setValue(items);
    }

    public void setFlag(Flag item) {
        this.flag.setValue(item);
    }

    public void setEpisode(Episode item) {
        this.episode.setValue(item);
    }

    public void setQuality(Result result) {
        this.quality.setValue(result);
    }

    public void setQualityVisible(boolean visible) {
        this.qualityVisible.setValue(visible);
    }

    public void setUseParse(boolean useParse) {
        this.useParse.setValue(useParse);
    }

    public void setArtwork(String url) {
        this.artwork.setValue(url);
    }

    public void setDescription(String desc) {
        this.description.setValue(desc);
    }

    public void setSources(List<Vod> items) {
        this.sources.setValue(items);
    }

    public void setResult(Result result) {
        this.result.setValue(result);
    }

    public void setPlayer(Result result) {
        this.player.setValue(result);
    }

    public void nextScale(int max) {
        History history = playbackState.getHistory();
        if (history == null) return;
        int index = history.getScale();
        if (index == -1) index = com.fongmi.android.tv.setting.PlayerSetting.getScale();
        history.setScale(index == max - 1 ? 0 : ++index);
    }

    public void setOpening(long opening) {
        History history = playbackState.getHistory();
        if (history != null) history.setOpening(opening);
    }

    public void addOpening(long value) {
        History history = playbackState.getHistory();
        if (history != null) history.setOpening(Math.max(0, history.getOpening() + value));
    }

    public void setEnding(long ending) {
        History history = playbackState.getHistory();
        if (history != null) history.setEnding(ending);
    }

    public void addEnding(long value) {
        History history = playbackState.getHistory();
        if (history != null) history.setEnding(Math.max(0, history.getEnding() + value));
    }

    public void setSpeed(float speed) {
        History history = playbackState.getHistory();
        if (history != null) history.setSpeed(speed);
    }

    public void setFullscreen(boolean fullscreen) {
        if (Boolean.valueOf(fullscreen).equals(this.fullscreen.getValue())) return;
        this.fullscreen.setValue(fullscreen);
    }

    public void toggleFullscreen() {
        setFullscreen(!Boolean.TRUE.equals(fullscreen.getValue()));
    }

    public void setLock(boolean lock) {
        this.lock.setValue(lock);
    }

    public void toggleLock() {
        setLock(!Boolean.TRUE.equals(lock.getValue()));
    }

    public void checkKeep(String key) {
        keep.setValue(Keep.find(key) != null);
    }

    public void toggleKeep(String key, History history, String siteName) {
        Keep item = Keep.find(key);
        if (item != null) {
            item.delete();
            keep.setValue(false);
        } else {
            createKeep(key, history, siteName);
            keep.setValue(true);
        }
    }

    private void createKeep(String key, History history, String siteName) {
        Keep item = new Keep();
        item.setKey(key);
        item.setSiteName(siteName);
        item.setVodPic(history.getVodPic());
        item.setVodName(history.getVodName());
        item.setCreateTime(System.currentTimeMillis());
        item.save();
    }

    @Override
    public void detailContent(String key, String id) {
        execute(TaskType.RESULT, result, () -> {
            Result result = SiteApi.detailContent(key, id);
            if (!result.getList().isEmpty()) sortFlags(key, result.getVod().getFlags());
            return result;
        });
    }

    private void sortFlags(String key, List<Flag> flags) {
        if (flags.size() <= 1) return;
        List<FlagScore> scores = AppDatabase.get().getFlagScoreDao().findBySite(key);
        Map<String, Integer> scoreMap = new HashMap<>();
        for (FlagScore score : scores) scoreMap.put(score.getFlagName(), score.getScore());
        flags.sort((o1, o2) -> {
            Integer s1 = scoreMap.get(o1.getFlag());
            Integer s2 = scoreMap.get(o2.getFlag());
            return Integer.compare(s2 == null ? 0 : s2, s1 == null ? 0 : s1);
        });
    }

    @Override
    protected void onCleared() {
        playbackState.reset();
        super.onCleared();
    }
}
