package com.fongmi.android.tv.ui.base;

import android.content.Intent;
import android.text.TextUtils;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.model.VideoViewModel;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.ui.activity.PlaybackActivity;

import java.util.Objects;

public abstract class BaseVideoActivity extends PlaybackActivity implements VodPlaybackHost {

    protected Observer<Result> mObserveDetail;
    protected Observer<Result> mObservePlayer;
    protected Observer<Result> mObserveSearch;
    protected VodPlaybackController mVod;
    protected VideoViewModel mViewModel;
    protected History mHistory;
    protected boolean fullscreen;

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
        mVod.reset();
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
        mViewModel = new ViewModelProvider(this).get(VideoViewModel.class);
        observeForever(mViewModel.getResult(), mObserveDetail);
        observeForever(mViewModel.getPlayer(), mObservePlayer);
        observeForever(mViewModel.getSearch(), mObserveSearch);
        mVod = mViewModel.createPlaybackController(this);
    }

    protected void onDetailObserved(Result result) {
        if (service() == null) return;
        mVod.onDetailResult(result);
    }

    protected void onPlayerObserved(Result result) {
        if (service() == null) return;
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
        if (mVod != null && player() != null) mVod.saveHistory(finish, System.currentTimeMillis(), player().getPosition(), player().getDuration());
    }

    @Override
    public void requestDetail(String key, String id) {
        mViewModel.detailContent(key, id);
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveHistory(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        saveHistory(true);
    }
}
