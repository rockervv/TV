package com.fongmi.android.tv.ui.base;

import static com.fongmi.android.tv.bean.History.getCurrentUTCTime;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.FlagScore;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.HistorySyncManager;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.dialog.PlayerDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import master.flame.danmaku.danmaku.model.android.DanmakuContext;

public abstract class BaseVideoActivity extends BaseActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, PlayerDialog.Listener, Clock.Callback {

    protected DanmakuContext mDanmakuContext;
    protected CustomKeyDownVod mKeyDown;
    protected ExecutorService mExecutor;
    protected SiteViewModel mViewModel;
    protected List<String> mBroken;
    protected History mHistory;
    protected Players mPlayers;
    protected boolean fullscreen;
    protected boolean initTrack;
    protected boolean initAuto;
    protected boolean autoMode;
    protected boolean useParse;
    protected int toggleCount;
    protected int errorCount;
    protected Runnable mR1;
    protected Runnable mR2;
    protected Runnable mR3;
    protected Runnable mR4;
    protected Clock mClock;

    protected abstract String getVodName();

    protected abstract String getVodPic();

    protected abstract void setTrackVisible(boolean visible);

    protected abstract void setQualityVisible(boolean visible);

    protected abstract void showProgress();

    protected abstract void hideProgress();

    protected abstract void showError(String text);

    protected abstract void showEmpty();

    protected abstract void hideControl();

    protected abstract void hidePreview();

    protected abstract void setPlayerView();

    protected abstract void setDecodeView();

    protected abstract void setScale(int scale);

    protected abstract Drawable getDefaultArtwork();

    protected abstract Flag getFlag();

    protected abstract Episode getEpisode();

    protected abstract int getFlagPosition();

    protected abstract int getParsePosition();

    protected abstract void onRefresh();

    protected abstract void checkDanmu(String danmu);

    protected abstract void setDetail(Vod item);

    protected abstract void setEmpty(boolean finish);

    protected abstract void onPlayerReady();

    protected abstract void checkEnded();

    protected abstract void onDecode(boolean save);

    protected abstract boolean onChoose();

    protected abstract void onTimeChangeDisplaySpeed();

    protected abstract void setOpening(long opening);

    protected abstract void setEnding(long ending);

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

    protected String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    protected Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    protected int getPlayer() {
        return mHistory != null && mHistory.getPlayer() != -1 ? mHistory.getPlayer() : getSite().getPlayerType() != -1 ? getSite().getPlayerType() : Setting.getPlayer();
    }

    protected int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : Setting.getScale();
    }

    protected boolean isReplay() {
        return Setting.getReset() == 1;
    }

    protected boolean isFullscreen() {
        return fullscreen;
    }

    protected void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    protected boolean isInitTrack() {
        return initTrack;
    }

    protected void setInitTrack(boolean initTrack) {
        this.initTrack = initTrack;
    }

    protected boolean isInitAuto() {
        return initAuto;
    }

    protected void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    protected boolean isAutoMode() {
        return autoMode;
    }

    protected void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    protected boolean isUseParse() {
        return useParse;
    }

    protected void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    protected int getToggleCount() {
        return toggleCount;
    }

    protected void resetToggle() {
        this.toggleCount = 0;
    }

    protected int addErrorCount() {
        return ++errorCount;
    }

    protected void resetError() {
        this.errorCount = 0;
    }

    protected void getDetail() {
        mViewModel.detailContent(getKey(), getId());
    }

    protected void getDetail(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getVodPic());
        getIntent().putExtra("id", item.getVodId());
        mClock.setCallback(null);
        mPlayers.reset();
        mPlayers.stop();
        getDetail();
    }

    protected void getPlayer(Flag flag, Episode episode, boolean replay) {
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        updateHistory(episode, replay);
        showProgress();
        setMetadata();
        hidePreview();
    }

    protected void updateHistory(Episode item, boolean replay) {
        boolean switchEpisode = !item.equals(mHistory.getEpisode());
        replay = replay || switchEpisode;
        long position = replay ? 0 : mHistory.getPosition();
        long opening = mHistory.getOpening();
        android.util.Log.d("VideoActivity", "updateHistory: name=" + mHistory.getVodName() + " remark=" + item.getName() + " pos=" + position + " open=" + opening + " replay=" + replay);
        if (position > 0) {
            Notify.showTop(ResUtil.getString(R.string.play_resume, mPlayers.stringToTime(position)));
        } else if (opening > 0 && !replay) {
            Notify.showTop(ResUtil.getString(R.string.play_skip_op, mPlayers.stringToTime(opening)));
        }
        mHistory.setPosition(position);
        mHistory.setEpisodeUrl(item.getUrl());
        mHistory.setVodRemarks(item.getName());
        mHistory.setVodFlag(getFlag().getFlag());
        mHistory.setCreateTime(System.currentTimeMillis());
        if (replay && !switchEpisode) mPlayers.setPosition(0);
        else mPlayers.setPosition(Math.max(opening, position));
    }

    protected void setMetadata() {
        String title = mHistory.getVodName();
        String episode = getEpisode().getName();
        String artist = title.equals(episode) ? "" : getString(R.string.play_now, episode);
        mPlayers.setMetadata(title, artist, mHistory.getVodPic(), getDefaultArtwork());
    }

    protected void onSave() {
        onSave("");
    }

    protected void onSave(String prefix) {
        String url = mPlayers.getUrl();
        if (TextUtils.isEmpty(url)) return;
        App.execute(() -> {
            try {
                String realUrl = url;
                if (url.startsWith(Server.get().getAddress())) {
                    realUrl = java.net.URLDecoder.decode(url.split("url=")[1].split("&")[0], java.nio.charset.StandardCharsets.UTF_8.name());
                }
                long timestamp = System.currentTimeMillis();
                long dd = timestamp / (1000L * 60 * 60 * 24);
                LocalDateTime ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());
                int hh = ldt.getHour();
                int mm = ldt.getMinute();
                int ss = ldt.getSecond();

                String name = prefix + dd + hh + mm + ss + "_" + getVodName() + ".json";
                com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                json.addProperty("URL", realUrl);
                json.addProperty("m3u8", mPlayers.getM3u8Content());

                String content = App.gson().toJson(json);
                if (Setting.isUseFtp()) {
                    String ftpUrl = Setting.getFtpUri();
                    String username = Setting.getFtpUsername();
                    String password = Setting.getFtpPassword();
                    com.fongmi.android.tv.bean.FtpManager ftp = new com.fongmi.android.tv.bean.FtpManager(ftpUrl, username, password);
                    String remotePath = new File(new java.net.URI(ftpUrl).getPath()).getParent() + "/" + name;
                    ftp.uploadJsonString(content, remotePath);
                    App.post(() -> Notify.show("已上傳至 FTP: " + name));
                } else {
                    File file = new File(com.github.catvod.utils.Path.cache(), name);
                    com.github.catvod.utils.Path.write(file, content.getBytes());
                    App.post(() -> Notify.show("已儲存至暫存: " + name));
                }
            } catch (Exception e) {
                App.post(() -> Notify.show("儲存失敗: " + e.getMessage()));
                e.printStackTrace();
            }
        });
    }

    protected void initSearch(String keyword, boolean auto) {
        stopSearch();
        setAutoMode(auto);
        setInitAuto(auto);
        startSearch(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    protected void startSearch(String keyword) {
        List<Site> sites = new ArrayList<>();
        mExecutor = Executors.newFixedThreadPool(Constant.THREAD_POOL * 2);
        for (Site item : VodConfig.get().getSites()) if (isPass(item)) sites.add(item);
        for (Site site : sites) mExecutor.execute(() -> {
            try {
                mViewModel.searchContent(site, keyword, true);
            } catch (Throwable ignored) {
            }
        });
    }

    protected void stopSearch() {
        if (mExecutor == null) return;
        mExecutor.shutdownNow();
        mExecutor = null;
    }

    protected boolean mismatch(Vod item, String keyword) {
        if (getId().equals(item.getVodId())) return true;
        if (mBroken.contains(item.getVodId())) return true;
        if (isAutoMode()) return !item.getVodName().equals(keyword);
        else return !item.getVodName().contains(keyword);
    }

    protected void setDetail(Result result) {
        if (result.getList().isEmpty()) setEmpty(result.hasMsg());
        else setDetail(result.getList().get(0));
        Notify.show(result.getMsg());
    }

    protected void setPlayer(Result result) {
        setUseParse(VodConfig.hasParse() && ((result.getPlayUrl().isEmpty() && VodConfig.get().getFlags().contains(result.getFlag())) || result.getJx() == 1));
        mPlayers.start(result, isUseParse(), getSite().isChangeable() ? getSite().getTimeout() : -1);
        setQualityVisible(result.getUrl().isMulti());
        checkDanmu(result.getDanmaku());
    }

    protected void setSearch(Result result) {
        List<Vod> items = result.getList();
        String keyword = getSearchKeyword();
        Iterator<Vod> iterator = items.iterator();
        while (iterator.hasNext()) if (mismatch(iterator.next(), keyword)) iterator.remove();
        onSearch(items);
        if (isInitAuto()) nextSite();
        if (!items.isEmpty()) App.removeCallbacks(mR4);
    }

    protected abstract String getSearchKeyword();

    protected abstract void onSearch(List<Vod> items);

    protected void nextPlayer() {
        mPlayers.nextPlayer();
        setPlayerView();
        setDecodeView();
        onRefresh();
    }

    protected void onErrorEnd(ErrorEvent event) {
        onErrorPlayer(event);
        resetError();
    }

    protected void onErrorPlayer(ErrorEvent event) {
        String key = getKey();
        String flag = getFlag().getFlag();
        Track.delete(getHistoryKey());
        showError(event.getMsg());
        mClock.setCallback(null);
        mPlayers.reset();
        mPlayers.stop();
        App.execute(() -> FlagScore.find(key, flag).decrement());
    }

    protected void onError(ErrorEvent event) {
        onErrorPlayer(event);
        startFlow();
    }

    protected void startFlow() {
        if (!getSite().isChangeable()) return;
        if (isUseParse()) checkParse();
        else checkFlag();
    }

    protected abstract void checkParse();

    protected abstract void checkFlag();

    protected abstract void nextParse(int position);

    protected abstract void nextFlag(int position);

    protected abstract void nextSite();

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        super.onRefreshEvent(event);
        if (isBackground()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.DANMAKU) checkDanmu(event.getPath());
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) mPlayers.setSub(Sub.from(event.getPath()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onActionEvent(ActionEvent event) {
        if (isBackground()) return;
        if (ActionEvent.PLAY.equals(event.getAction()) || ActionEvent.PAUSE.equals(event.getAction())) {
            onKeyCenter();
        } else if (ActionEvent.NEXT.equals(event.getAction())) {
            checkNext();
        } else if (ActionEvent.PREV.equals(event.getAction())) {
            checkPrev();
        } else if (ActionEvent.STOP.equals(event.getAction())) {
            finish();
        }
    }

    protected abstract boolean isBackground();

    protected abstract void checkNext();

    protected abstract void checkPrev();

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerEvent(PlayerEvent event) {
        if (isBackground()) return;
        switch (event.getState()) {
            case 0:
                setInitTrack(true);
                setTrackVisible(false);
                mClock.setCallback(this);
                break;
            case Player.STATE_IDLE:
                break;
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                stopSearch();
                onPlayerReady();
                hideProgress();
                mPlayers.reset();
                setDefaultTrack();
                setTrackVisible(true);
                mHistory.setPlayer(mPlayers.getPlayer());
                mHistory.setLastUpdated(getCurrentUTCTime());
                mHistory.update();
                if (mPlayers.isLive()) {
                    Notify.showTop("使用直播模式播放");
                    onSave("live_");
                }
                String key = getKey();
                String flag = getFlag().getFlag();
                App.execute(() -> FlagScore.find(key, flag).increment());
                break;
            case Player.STATE_ENDED:
                checkEnded();
                break;
        }
    }

    protected void setDefaultTrack() {
        if (isInitTrack()) {
            setInitTrack(false);
            mPlayers.prepared();
            mPlayers.setTrack(Track.find(getHistoryKey()));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onErrorEvent(ErrorEvent event) {
        if (isBackground()) return;
        if (addErrorCount() > 20) onErrorEnd(event);
        else if (mPlayers.addRetry() > event.getRetry()) checkError(event);
        else if (event.isDecode() && mPlayers.canToggleDecode()) onDecode(false);
        else if (event.isExo() && mPlayers.isExo()) onExoCheck(event);
        else onRefresh();
    }

    protected void onExoCheck(ErrorEvent event) {
        if (event.getCode() == PlaybackException.ERROR_CODE_IO_UNSPECIFIED || event.getCode() >= PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED && event.getCode() <= PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED) mPlayers.setFormat(ExoUtil.getMimeType(event.getCode()));
        mPlayers.setMediaSource();
    }

    protected void checkError(ErrorEvent event) {
        if (getSite().getPlayerType() == -1 && event.isUrl() && event.getRetry() > 0 && getToggleCount() < 2 && mPlayers.getPlayer() != Players.SYS) {
            toggleCount++;
            nextPlayer();
        } else {
            resetToggle();
            onError(event);
        }
    }

    @Override
    public void onTimeChanged() {
        onTimeChangeDisplaySpeed();
        long position, duration;
        mHistory.setPosition(position = mPlayers.getPosition());
        mHistory.setDuration(duration = mPlayers.getDuration());
        if (position >= 0 && duration > 0 && !Setting.isIncognito()) App.execute(() -> mHistory.update());
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            mClock.setCallback(null);
            checkNext();
        }
    }

    @Override
    public void onTrackClick(Track item) {
        item.setKey(getHistoryKey());
        item.save();
    }

    @Override
    public void onPlayerClick(Integer item) {
        mPlayers.setPlayer(item);
        setPlayerView();
        setDecodeView();
        onRefresh();
    }

    @Override
    public void onPlayerShare(String title) {
        onChoose();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSearch();
        mClock.release();
        mPlayers.release();
        Source.get().stop();
        RefreshEvent.history();
        HistorySyncManager.SyncAll();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
    }
}
