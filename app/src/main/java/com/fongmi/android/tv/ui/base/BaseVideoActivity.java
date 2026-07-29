package com.fongmi.android.tv.ui.base;

import android.content.Intent;
import android.text.TextUtils;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.model.PlaybackViewModel;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.PlaybackActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.google.gson.JsonObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

public abstract class BaseVideoActivity extends PlaybackActivity implements VodPlaybackHost {

    protected Observer<Result> mObserveDetail;
    protected Observer<Result> mObservePlayer;
    protected Observer<Result> mObserveSearch;
    protected VodPlaybackController mVod;
    protected PlaybackViewModel mViewModel;
    protected History mHistory;
    protected boolean fullscreen;
    protected boolean useParse;

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    protected String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    protected String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    protected String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    protected String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    protected String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    @Override
    public String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    protected Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    @Override
    public String getVodKey() {
        return getKey();
    }

    @Override
    public String getVodId() {
        return getId();
    }

    @Override
    public String getVodName() {
        return getName();
    }

    @Override
    public String getVodPic() {
        return getPic();
    }

    @Override
    public String getVodMark() {
        return getMark();
    }

    @Override
    public boolean isSiteChangeable() {
        return getSite().isChangeable();
    }

    @Override
    public boolean isHostFinishing() {
        return isFinishing() || isDestroyed();
    }

    @Override
    public boolean isPlayerEmpty() {
        return player().isEmpty();
    }

    @Override
    public boolean isFullscreenForPlayback() {
        return isFullscreen();
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    protected void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    @Override
    public long getPlayerPosition() {
        return player().getPosition();
    }

    @Override
    public void usePushId(String id) {
        getIntent().putExtra("key", "push_agent").putExtra("id", id);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String oldId = getId();
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(oldId)) return;
        saveHistory(false);
        getIntent().putExtras(intent);
        if (mVod != null) mVod.reset();
        checkId();
    }

    @Override
    protected void initView() {
        super.initView();
        mObserveDetail = this::onDetailObserved;
        mObservePlayer = this::onPlayerObserved;
        mObserveSearch = this::onSearchObserved;
        setViewModel();
    }

    protected void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        mViewModel.getKeep().observe(this, keep -> onKeepChanged());
        mViewModel.getLock().observe(this, this::onLockChanged);
        mViewModel.getFullscreen().observe(this, this::onFullscreenChanged);
        mViewModel.getState().observe(this, this::onStateChanged);
        mViewModel.getVod().observe(this, this::onVodChanged);
        mViewModel.getHistory().observe(this, this::onHistoryChanged);
        mViewModel.getFlags().observe(this, this::onFlagsChanged);
        mViewModel.getEpisodes().observe(this, this::onEpisodesChanged);
        mViewModel.getFlag().observe(this, this::onFlagChanged);
        mViewModel.getEpisode().observe(this, this::onEpisodeChanged);
        mViewModel.getQuality().observe(this, this::onQualityChanged);
        mViewModel.getQualityVisible().observe(this, this::onQualityVisibleChanged);
        mViewModel.getUseParse().observe(this, this::onUseParseChanged);
        mViewModel.getArtwork().observe(this, this::onArtworkChanged);
        mViewModel.getDescription().observe(this, this::onDescriptionChanged);
        mViewModel.getSources().observe(this, this::onSourcesChanged);
        observeForever(mViewModel.getResult(), mObserveDetail);
        observeForever(mViewModel.getPlayer(), mObservePlayer);
        observeForever(mViewModel.getSearch(), mObserveSearch);
        mVod = mViewModel.createPlaybackController(this);
    }

    protected void onVodChanged(Vod item) {
    }

    protected void onHistoryChanged(History history) {
        mHistory = history;
    }

    protected void onFlagsChanged(List<Flag> items) {
    }

    protected void onEpisodesChanged(List<Episode> items) {
    }

    protected void onFlagChanged(Flag item) {
    }

    protected void onEpisodeChanged(Episode item) {
    }

    @Override
    public void onTimeoutCountdown(long ms) {
    }

    protected void onQualityChanged(Result result) {
    }

    protected void onQualityVisibleChanged(boolean visible) {
    }

    protected void onUseParseChanged(boolean useParse) {
        this.useParse = useParse;
    }

    protected void onArtworkChanged(String url) {
    }

    protected void onDescriptionChanged(String desc) {
    }

    protected void onSourcesChanged(List<Vod> items) {
    }

    protected void onKeepChanged() {
    }

    protected void onLockChanged(boolean lock) {
    }

    protected void onFullscreenChanged(boolean fullscreen) {
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (isPlaying) hideProgress();
        super.onIsPlayingChanged(isPlaying);
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        android.util.Log.d("BaseVideoActivity", "onPlaybackStateChanged: " + state);
        mViewModel.onStateChanged(state);
        super.onPlaybackStateChanged(state);
    }

    protected void onPlayingChanged(boolean isPlaying) {
    }

    protected void onStateChanged(int state) {
        switch (state) {
            case androidx.media3.common.Player.STATE_BUFFERING:
                showProgress();
                break;
            case androidx.media3.common.Player.STATE_READY:
                hideProgress();
                break;
            case androidx.media3.common.Player.STATE_ENDED:
                hideProgress();
                break;
        }
    }

    protected abstract void showProgress();

    protected abstract void hideProgress();

    protected void onDetailObserved(Result result) {
        if (service() == null) return;
        mVod.onDetailResult(result);
    }

    protected void onPlayerObserved(Result result) {
        if (result == null) return;
        if (!result.getMsg().isEmpty()) {
            onError(result.getMsg());
            return;
        }
        if (service() == null) {
            android.util.Log.w("BaseVideoActivity", "onPlayerObserved: service is null!");
            return;
        }
        mVod.onPlayerResult(result);
    }

    protected void onSearchObserved(Result result) {
        if (service() == null) return;
        mVod.onSearchResult(result);
    }

    protected void checkId() {
        mVod.checkId();
    }

    protected void saveHistory(boolean finish) {
        if (service() != null && mVod != null) mVod.saveHistory(finish, System.currentTimeMillis(), player().getPosition(), player().getDuration());
    }

    protected void onSave() {
        String url = player().getUrl();
        if (TextUtils.isEmpty(url)) return;
        Notify.show(ResUtil.getString(R.string.play_save) + "...");
        String prefix = player().isLive() ? "Live_" : "Vod_";
        App.execute(() -> {
            try {
                String content = player().getM3u8Content(true);
                if (content.isEmpty()) {
                    App.post(() -> Notify.show(R.string.error_play_url));
                    return;
                }
                String realUrl = url;
                if (url.startsWith(Server.get().getAddress())) {
                    realUrl = java.net.URLDecoder.decode(url.split("url=")[1].split("&")[0], StandardCharsets.UTF_8.name());
                }
                long timestamp = System.currentTimeMillis();
                LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                String date = String.format(java.util.Locale.getDefault(), "%04d%02d%02d%02d%02d%02d", ldt.getYear(), ldt.getMonthValue(), ldt.getDayOfMonth(), ldt.getHour(), ldt.getMinute(), ldt.getSecond());
                String name = prefix + date + "_" + getVodName().replaceAll("[\\\\/:*?\"<>|]", "_") + ".json";
                JsonObject json = new JsonObject();
                json.addProperty("URL", realUrl);
                json.addProperty("m3u8", content);
                String content_json = App.gson().toJson(json);
                if (Setting.isUseFtp()) {
                    String ftpUrl = Setting.getFtpUri();
                    String username = Setting.getFtpUsername();
                    String password = Setting.getFtpPassword();
                    com.fongmi.android.tv.bean.FtpManager ftp = new com.fongmi.android.tv.bean.FtpManager(ftpUrl, username, password);
                    String remotePath = new File(new java.net.URI(ftpUrl).getPath()).getParent() + "/m3u8/" + name;
                    ftp.uploadJsonString(content_json, remotePath);
                    App.post(() -> Notify.show("已上傳至 FTP: " + name));
                } else {
                    File file = new File(Path.tv(), name);
                    Path.write(file, content_json.getBytes(StandardCharsets.UTF_8));
                    App.post(() -> Notify.show(ResUtil.getString(R.string.play_save_success, file.getAbsolutePath())));
                }
            } catch (Exception e) {
                App.post(() -> Notify.show("儲存失敗: " + e.getMessage()));
            }
        });
    }

    @Override
    public boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    @Override
    public void requestDetail(String key, String id) {
        mViewModel.detailContent(key, id);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveHistory(isFinishing());
    }

    @Override
    protected void onDestroy() {
        saveHistory(true);
        super.onDestroy();
    }
}
