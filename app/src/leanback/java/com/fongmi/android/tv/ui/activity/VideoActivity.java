package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.playback.PlaybackAction;
import com.fongmi.android.tv.playback.PlaybackReset;
import com.fongmi.android.tv.playback.vod.VodPlayRequest;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.fongmi.android.tv.playback.vod.VodPlaybackMedia;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.PartAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.base.BaseVideoActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.PlayerSeekView;
import com.fongmi.android.tv.ui.dialog.ContentDialog;
import com.fongmi.android.tv.ui.dialog.ParseDialog;
import com.fongmi.android.tv.ui.dialog.PlayerEngineDialog;
import com.fongmi.android.tv.ui.dialog.SubTitleView;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PartUtil;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class VideoActivity extends BaseVideoActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, ParseDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, Clock.Callback {

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayAdapter mArrayAdapter;
    private QuickAdapter mQuickAdapter;
    private FlagAdapter mFlagAdapter;
    private PartAdapter mPartAdapter;
    private CustomKeyDownVod mKeyDown;
    private Runnable mDataTimer;
    private int mDataCountdown;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private long mBasePosition = -1;
    private boolean mSeeking;

    private final Runnable mHideCenter = this::hideCenter;
    private final Runnable mSeekReset = () -> {
        mBasePosition = -1;
        mSeeking = false;
    };
    

    public static void push(FragmentActivity activity, String text) {
        Uri uri = UrlUtil.uri(text);
        if (FileChooser.isValid(activity, uri)) file(activity, FileChooser.getPathFromUri(uri));
        else start(activity, text);
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true, false);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
    }

    public Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : PlayerSetting.getScale();
    }

    private void setScale(int scale) {
        if (mVod != null) mVod.setScale(scale);
        mBinding.control.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
        player().setScale(scale);
        mBinding.exo.setResizeMode(scale);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected PlayerView getPlayerView() {
        return mBinding.exo;
    }

    @Override
    protected PlayerSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onServiceConnected() {
        checkId();
    }

    @Override
    protected void initView() {
        super.initView();
        mFrameParams = mBinding.video.getLayoutParams();
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownVod.create(this);
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showEmpty;
        setRecyclerView();
        setVideoView();
        checkCast();
        // injectMockData();
    }

    private void injectMockData() {
        android.util.Log.d("VideoActivity", "injectMockData() START");
        Vod vod = new Vod();
        vod.setName("測試影片 (Mock)");
        vod.setId("mock_id");
        vod.setContent("這是一個注入的測試影片內容。播放這段影片以驗證播放器引擎。");
        vod.setPlayFrom("測試線路");
        vod.setPlayUrl("第1集$https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8");

        // Force initialize mHistory for mock data to avoid NPE
        if (mHistory == null) {
            mHistory = new History();
            mHistory.setKey("mock_id");
            mHistory.setVodName("測試影片 (Mock)");
        }

        mVod.onDetailResult(Result.vod(vod));
        android.util.Log.d("VideoActivity", "Mock data injected successfully");
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.control.text.setOnClickListener(this::onTrack);
        mBinding.control.audio.setOnClickListener(this::onTrack);
        mBinding.control.video.setOnClickListener(this::onTrack);
        mBinding.control.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.speed.setDownListener(this::onSpeedSub);
        mBinding.control.ending.setUpListener(this::onEndingAdd);
        mBinding.control.ending.setDownListener(this::onEndingSub);
        mBinding.control.opening.setUpListener(this::onOpeningAdd);
        mBinding.control.opening.setDownListener(this::onOpeningSub);
        mBinding.control.text.setUpListener(this::onSubtitleClick);
        mBinding.control.text.setDownListener(this::onSubtitleClick);
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.scale.setOnClickListener(view -> onScale());
        mBinding.control.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.speed.setOnLongClickListener(view -> onSpeedReset());
        mBinding.control.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.reset.setOnClickListener(view -> onReset());
        mBinding.control.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.parse.setOnClickListener(view -> onParse());
        mBinding.control.player.setOnClickListener(view -> onChoose());
        mBinding.control.decode.setOnClickListener(view -> onDecode());
        mBinding.control.ending.setOnClickListener(view -> onEnding());
        mBinding.control.loop.setOnClickListener(view -> onRepeat());
        mBinding.control.opening.setOnClickListener(view -> onOpening());
        mBinding.control.save.setOnClickListener(view -> onSave());
        mBinding.control.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mFlagAdapter.getItemCount() > 0) onItemClick(mFlagAdapter.get(position));
            }
        });
        mBinding.episode.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null && mBinding.video != mFocus1) mFocus1 = child.itemView;
            }
        });
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mEpisodeAdapter.getItemCount() > 20 && position > 1) mBinding.episode.setSelectedPosition((position - 2) * 20);
            }
        });
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this));
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(mArrayAdapter = new ArrayAdapter(this));
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(mPartAdapter = new PartAdapter(item -> mVod.search(item, false)));
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
    }

    private void setVideoView() {
        setSeekNextFocusDown(R.id.next);
        setActionFocusBoundary(mBinding.control.actionLayout);
        PlayerEngineDialog.setText(mBinding.control.player);
        mBinding.control.next.requestFocus();
        setResetText();
    }

    private void setResetText() {
        mBinding.control.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
    }

    private void setPlaybackMode() {
        PlaybackAction.setPlaybackMode(player(), mBinding.control.player, mBinding.control.decode);
    }

    private void onReset() {
        if (Setting.getReset() == 1) onReplay();
        else onRefresh();
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        setResetText();
        return true;
    }

    @Override
    public void requestPlayer(VodPlayRequest request) {
        android.util.Log.d("VideoActivity", "requestPlayer: " + request.getTitle());
        mBinding.widget.title.setText(getString(R.string.detail_title, mBinding.name.getText(), request.getTitle()));
        mViewModel.playerContent(request.getKey(), request.getFlag(), request.getId());
        mBinding.widget.title.setSelected(true);
        showProgress();
    }

    @Override
    public void requestSearch(List<Site> sites, String keyword) {
        mQuickAdapter.clear();
        mViewModel.searchContent(sites, keyword, true);
    }

    @Override
    public void prepareSource(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        player().reset();
        player().stop();
    }

    @Override
    public void stopPlaybackForRefresh() {
        player().stop();
        player().clear();
        mClock.setCallback(null);
    }

    @Override
    public void resetPlaybackForError(String msg) {
        PlaybackReset.afterError(player(), () -> mClock.setCallback(null));
        showError(msg);
    }

    @Override
    public void replay(long position) {
        player().replay(position);
    }

    @Override
    public void startPlayback(Result result, boolean useParse, long startPositionMs, History history, Episode episode) {
        android.util.Log.d("VideoActivity", "startPlayback: " + result.getUrl());
        startPlayer(getHistoryKey(), result, useParse, getSite().getTimeout(), startPositionMs, VodPlaybackMedia.metadata(history, episode));
    }

    @Override
    protected void onVodChanged(Vod item) {
        mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
        mBinding.widget.title.setText(item.getName());
        mViewModel.checkKeep(getHistoryKey());
        setArtwork(item.getPic());
        updateKeep(item);
        setText(item);
    }

    @Override
    protected void onHistoryChanged(History history) {
        if (history == null) return;
        History old = mHistory;
        super.onHistoryChanged(history);
        if (old != null && old.getOpening() == history.getOpening() && old.getEnding() == history.getEnding() && old.getSpeed() == history.getSpeed() && Objects.equals(old.getVodName(), history.getVodName()) && Objects.equals(old.getEpisodeUrl(), history.getEpisodeUrl())) {
            return;
        }
        android.util.Log.d("VideoActivity", "onHistoryChanged: Updating UI for " + history.getVodName());
        mBinding.control.opening.setText(history.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(history.getOpening()));
        mBinding.control.ending.setText(history.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(history.getEnding()));
        if (old == null || old.getSpeed() != history.getSpeed()) PlaybackAction.setSpeed(player(), mBinding.control.speed, history.getSpeed());
        setScale(getScale());
        if (mEpisodeAdapter.getItemCount() > 0) setArrayAdapter(mEpisodeAdapter.getItemCount());
        if (old == null || !Objects.equals(old.getVodName(), history.getVodName())) {
            setPartAdapter();
        }
    }

    @Override
    protected void onFlagsChanged(List<Flag> items) {
        if (items.isEmpty()) return;
        mFlagAdapter.addAll(items);
        mBinding.flag.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onEpisodesChanged(List<Episode> items) {
        if (items.isEmpty()) return;
        setEpisodeAdapter(items);
    }

    @Override
    protected void onFlagChanged(Flag item) {
        mBinding.flag.setSelectedPosition(mFlagAdapter.indexOf(item));
        mFlagAdapter.notifyItemChanged(mFlagAdapter.indexOf(item));
    }

    @Override
    public void onEpisodeChanged(Episode item) {
        mEpisodeAdapter.notifyItemChanged(mEpisodeAdapter.getPosition());
        mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
    }

    @Override
    public void onTimeoutCountdown(long ms) {
        if (mSeeking || isFullscreen()) {
            mBinding.widget.status.setVisibility(View.GONE);
            return;
        }
        if (mBinding.widget.status.getVisibility() != View.VISIBLE) mBinding.widget.status.setVisibility(View.VISIBLE);
        if (ms <= 0) {
            mBinding.widget.status.setText(R.string.play_timeout_error);
        } else {
            mBinding.widget.status.setText(ResUtil.getString(R.string.play_timeout_count, ms / 1000.0f));
        }
    }

    @Override
    protected void onQualityChanged(Result result) {
        mQualityAdapter.addAll(result);
    }

    @Override
    protected void onQualityVisibleChanged(boolean visible) {
        setQualityVisible(visible);
    }

    @Override
    protected void onUseParseChanged(boolean useParse) {
        super.onUseParseChanged(useParse);
        mBinding.control.parse.setVisibility(useParse ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onArtworkChanged(String url) {
        setArtwork(url);
    }

    @Override
    protected void onDescriptionChanged(String desc) {
        mBinding.content.setTag(desc);
    }

    @Override
    protected void onSourcesChanged(List<Vod> items) {
        App.post(() -> {
            if (mBinding == null) return;
            mQuickAdapter.addAll(items);
            mBinding.quick.setVisibility(mQuickAdapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        }, 2500);
    }

    @Override
    public void renderEmptyDetail() {
        showEmpty();
    }

    @Override
    public void renderFallbackName(String name) {
        mBinding.name.setText(name);
    }

    @Override
    public void renderReverseEpisodes(List<Episode> items, boolean scroll) {
        setEpisodeAdapter(items);
        if (scroll) mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
    }

    @Override
    public void onDetailFallbackScheduled() {
        App.post(mR4, 10000);
    }

    @Override
    public void onDetailFallbackCancelled() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void onSearchStarted(String keyword) {
        mBinding.part.setTag(keyword);
    }

    @Override
    public void onSearchResult() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void showDetailMessage(String msg) {
        Notify.show(msg);
    }

    @Override
    public void showSwitchLine(Flag flag) {
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
    }

    @Override
    public void showSwitchSource(Vod item) {
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
    }

    @Override
    public void showEpisodeReady(Episode item) {
        Notify.show(getString(R.string.play_ready, item.getName()));
    }

    @Override
    public void showNoNext(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_prev : R.string.error_play_next);
    }

    @Override
    public void showNoPrev(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_next : R.string.error_play_prev);
    }

    @Override
    public void finishVod() {
        setStop(true);
        finish();
    }

    private void checkCast() {
        if (isCast() && !isFullscreen()) enterFullscreen();
        else mBinding.progressLayout.showProgress();
    }

    private void showEmpty() {
        mBinding.progressLayout.showEmpty();
    }

    private void setText(Vod item) {
        mBinding.content.setTag(item.getContent());
        setText(mBinding.year, R.string.detail_year, item.getYear().isEmpty() ? "未知" : item.getYear());
        setText(mBinding.area, R.string.detail_area, item.getArea().isEmpty() ? "未知" : item.getArea());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.remark, 0, item.getRemarks());
        mBinding.flag.setVisibility(item.getFlags().isEmpty() ? View.GONE : View.VISIBLE);
        if (mBinding.scroll.getVisibility() != View.VISIBLE) mBinding.scroll.setVisibility(View.VISIBLE);
    }

    private void setText(TextView view, int resId, String text) {
        view.setText(resId > 0 ? getString(resId, text) : text);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
    }

    @Override
    public void onItemClick(Flag item) {
        mVod.selectFlag(item);
    }

    @Override
    public boolean onLongClick(Flag item) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(ResUtil.getString(R.string.site_blacklist_confirm, getSite().getName()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    getSite().setBlacklist();
                    onChange();
                }).show();
        return true;
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mEpisodeAdapter.addAll(items);
        setArrayAdapter(items.size());
        setR2Callback();
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mVod.selectEpisode(item);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        setR2Callback();
    }

    @Override
    public void onItemClick(Result result) {
        mVod.selectQuality(result);
    }

    private void setArrayAdapter(int size) {
        if (mHistory == null) {
            android.util.Log.w("VideoActivity", "setArrayAdapter: mHistory is null, skipping UI update");
            return;
        }
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) {
            for (int i = size; i > 0; i -= 20) items.add(i + "-" + Math.max(i - 19, 1));
        } else {
            for (int i = 0; i < size; i += 20) items.add((i + 1) + "-" + Math.min(i + 20, size));
        }
        mArrayAdapter.addAll(items);
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episode, R.id.array, R.id.part, R.id.quick);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episode, R.id.array, R.id.part, R.id.quick);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        mPartAdapter.setNextFocusUp(findFocusUp(4));
        mEpisodeAdapter.setNextFocusUp(findFocusUp(2));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(2));
        mEpisodeAdapter.notifyItemChanged(mEpisodeAdapter.getPosition());
        mPartAdapter.notifyDataSetChanged();
        mFlagAdapter.notifyItemChanged(mFlagAdapter.getPosition());
    }

    @Override
    public void onRevSort() {
        mVod.setRevSort(!mHistory.isRevSort());
        mVod.reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        mVod.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private boolean tuning;

    @Override
    public boolean isDebugViewVisible() {
        return tuning;
    }

    @Override
    public void toggleDebugView() {
        tuning = !tuning;
        updateTuningLayout();
    }

    @Override
    public void hideDebugView() {
        tuning = false;
        updateTuningLayout();
    }

    private void updateTuningLayout() {
        if (tuning && isFullscreen()) {
            int width = ResUtil.getScreenWidth() / 2;
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(width, RelativeLayout.LayoutParams.MATCH_PARENT));
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(width, RelativeLayout.LayoutParams.MATCH_PARENT);
            params.addRule(RelativeLayout.ALIGN_PARENT_END);
            mBinding.tuning.getRoot().setLayoutParams(params);
            mBinding.tuning.getRoot().setVisibility(View.VISIBLE);
            refreshTuningInfo();
        } else {
            if (isFullscreen()) {
                mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            }
            mBinding.tuning.getRoot().setVisibility(View.GONE);
        }
    }

    private void refreshTuningInfo() {
        if (!tuning || player() == null || isDestroyed()) return;
        Player p = player().getPlayer();
        if (p instanceof androidx.media3.mpvplayer.MpvPlayer mpv) {
            mBinding.tuning.tuningState.setText(getString(mpv.isPlaying() ? R.string.play_ready : R.string.play_buffering));
            mBinding.tuning.tuningHwdec.setText("hwdec: " + mpv.getHwdec());
            mBinding.tuning.tuningError.setText(mpv.getLastHwdecError());
            mBinding.tuning.tuningInfo.setText(String.format("VO: %s\nGPU API: %s\nThreads: %d\nFast: %b\nVideo Sync: %s",
                    mpv.getVo(),
                    ResUtil.getStringArray(R.array.select_mpv_gpu_api)[PlayerSetting.getMpvGpuApi()],
                    PlayerSetting.getMpvThreads(),
                    PlayerSetting.isMpvFast(),
                    PlayerSetting.getMpvVideoSync()
            ));
        }
        App.post(this::refreshTuningInfo, 1000);
    }

    @Override
    protected void onFullscreenChanged(boolean fullscreen) {
        if (this.fullscreen == fullscreen) return;
        android.util.Log.d("TV_UI", "onFullscreenChanged: " + fullscreen);
        if (fullscreen) enterFullscreen();
        else exitFullscreen();
        updateTuningLayout();
    }

    private void enterFullscreen() {
        mFocus1 = getCurrentFocus();
        mBinding.video.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        mBinding.video.requestFocus();
        mBinding.video.setForeground(null);
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.video.setTranslationZ(0f);
        mBinding.exo.setTranslationZ(0f);
        mBinding.flag.setSelectedPosition(mFlagAdapter.getPosition());
        mKeyDown.setFull(true);
        this.fullscreen = true;
        mFocus2 = null;
        mViewModel.setFullscreen(true);
    }

    private void exitFullscreen() {
        android.util.Log.d("TV_UI", "exitFullscreen() START");
        mBinding.video.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
        mBinding.video.setLayoutParams(mFrameParams);
        mKeyDown.setFull(false);
        this.fullscreen = false;
        mFocus2 = null;
        hideControl();
        hideInfo();
        mBinding.video.requestFocus();
        mViewModel.setFullscreen(false);
    }

    private void onContent() {
        if (mBinding.content.getTag() == null) return;
        ContentDialog.create().content(mBinding.content.getTag().toString()).show(this);
    }

    private void onKeep() {
        if (mHistory == null) return;
        mViewModel.toggleKeep(getHistoryKey(), mHistory, getSite().getName());
    }

    private void onVideo() {
        if (!isFullscreen()) mViewModel.toggleFullscreen();
    }

    private void onChange() {
        mVod.manualSwitchSource();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.loop.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.loop.setSelected(player().isRepeatOne());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        mVod.nextEpisode(notify);
    }

    private void checkPrev() {
        mVod.prevEpisode(true);
    }

    private void onNext(boolean notify) {
        Episode item = mEpisodeAdapter.getNext();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void onPrev(boolean notify) {
        Episode item = mEpisodeAdapter.getPrev();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void onScale() {
        mViewModel.nextScale(ResUtil.getStringArray(R.array.select_scale).length);
        setScale(mHistory.getScale());
    }

    private void onSpeed() {
        mViewModel.setSpeed(PlaybackAction.addSpeed(player(), mBinding.control.speed));
    }

    private boolean onSpeedReset() {
        mViewModel.setSpeed(PlaybackAction.setSpeed(player(), mBinding.control.speed, 1.0f));
        return true;
    }

    private void onSpeedAdd() {
        mViewModel.setSpeed(PlaybackAction.addSpeed(player(), mBinding.control.speed, 0.25f));
    }

    private void onSpeedSub() {
        mViewModel.setSpeed(PlaybackAction.subSpeed(player(), mBinding.control.speed, 0.25f));
    }

    @Override
    public void onParse(com.fongmi.android.tv.bean.Parse item) {
        mVod.selectParse(item);
    }

    private void onParse() {
        ParseDialog.create().show(this);
        hideControl();
    }

    private void onReplay() {
        mVod.replay();
    }

    private void onRefresh() {
        mVod.refresh();
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (position > 600000) {
            Notify.show(getString(R.string.error_play_op_limit));
            return;
        }
        if (player().canSetOpening(position, duration)) setOpening(position);
    }

    private void onOpeningAdd() {
        if (mHistory.getOpening() >= 600000) {
            Notify.show(getString(R.string.error_play_op_limit));
            return;
        }
        mViewModel.addOpening(1000);
        setOpening(mHistory.getOpening());
    }

    private void onOpeningSub() {
        mViewModel.addOpening(-1000);
        setOpening(mHistory.getOpening());
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mViewModel.setOpening(opening);
        mBinding.control.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        long ending = duration - position;
        if (ending > 600000) {
            Notify.show(getString(R.string.error_play_ed_limit));
            return;
        }
        if (player().canSetEnding(position, duration)) setEnding(ending);
    }

    private void onEndingAdd() {
        if (mHistory.getEnding() >= 600000) {
            Notify.show(getString(R.string.error_play_ed_limit));
            return;
        }
        mViewModel.addEnding(1000);
        setEnding(mHistory.getEnding());
    }

    private void onEndingSub() {
        mViewModel.addEnding(-1000);
        setEnding(mHistory.getEnding());
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mViewModel.setEnding(ending);
        mBinding.control.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onChoose() {
        PlayerEngineDialog.show(this, mBinding.control.player, player(), mBinding.widget.title.getText());
        hideControl();
    }

    private void onEpisodes() {
        if (isFullscreen()) exitFullscreen();
        hideControl();
        mBinding.episode.setSelectedPosition(mEpisodeAdapter.getPosition());
        mBinding.episode.requestFocus();
        mBinding.scroll.smoothScrollTo(0, mBinding.episode.getTop());
    }

    private void onDecode() {
        mClock.setCallback(null);
        PlaybackAction.toggleDecode(player());
    }

    public void onTrackClick(Track item) {
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).show(this);
        hideControl();
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl(getFocus2());
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showInfo() {
        if (mSeeking || player() == null || player().getPlayer() == null || !isFullscreen()) return;
        mBinding.video.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        mBinding.widget.getRoot().setVisibility(View.VISIBLE);
        mBinding.widget.getRoot().setTranslationZ(2000f);
        mBinding.widget.info.setVisibility(View.VISIBLE);
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.exo.setTranslationZ(-100f);
        mBinding.video.bringToFront();
        mBinding.widget.getRoot().bringToFront();
        mBinding.widget.getRoot().requestLayout();
        mBinding.widget.size.setText(player().getSizeText());
        mBinding.widget.exoDuration.setText(player().getDurationTime());
        mBinding.widget.exoPosition.setText(player().getPositionTime(0));
    }

    private void hideInfo() {
        android.util.Log.d("TV_UI", "hideInfo() called, isFullscreen=" + isFullscreen());
        mBinding.widget.info.setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showControl(View view) {
        if (player() == null || player().getPlayer() == null || !isFullscreen()) {
            android.util.Log.d("TV_UI", "showControl() ABORTED - isFullscreen=" + isFullscreen());
            return;
        }
        android.util.Log.d("TV_UI", "showControl() START");
        mBinding.video.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        mBinding.widget.getRoot().setVisibility(View.VISIBLE);
        mBinding.widget.info.setVisibility(View.VISIBLE);
        mBinding.control.getRoot().setTranslationZ(2000f);
        mBinding.widget.getRoot().setTranslationZ(2000f);
        mBinding.exo.setTranslationZ(0f);
        mBinding.control.getRoot().bringToFront();
        mBinding.widget.getRoot().bringToFront();
        mBinding.control.getRoot().requestLayout();
        mBinding.widget.getRoot().requestLayout();
        mBinding.widget.size.setText(player().getSizeText());
        if (view != null) App.post(view::requestFocus, 50);
        setR1Callback();
    }

    protected void hideControl() {
        if (mBinding == null) return;
        android.util.Log.d("TV_UI", "hideControl() START, isFullscreen=" + isFullscreen());
        mBinding.video.setDescendantFocusability(isFullscreen() ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS);
        mBinding.control.getRoot().setVisibility(View.GONE);
        mBinding.widget.getRoot().setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
        mBinding.widget.info.setVisibility(View.GONE);
        mBinding.control.getRoot().setTranslationZ(-1000f);
        mBinding.widget.getRoot().setTranslationZ(-1000f);
        mBinding.exo.setTranslationZ(0f);
        hideInfo();
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.center.setVisibility(View.GONE);
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        if (player().isPlaying()) hideInfo();
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        App.post(mR3, 1000);
    }

    private void setR1Callback() {
        if (isScrubbing()) return;
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback() {
        App.post(mR2, 500);
    }

    private void setArtwork(String url) {
        ImgUtil.loadVod("", url, mBinding.widget.preview);
    }

    private void setPartAdapter() {
        if (mHistory == null) return;
        mPartAdapter.addAll(PartUtil.split(mHistory.getVodName()));
        mBinding.part.setVisibility(View.VISIBLE);
        setR2Callback();
    }

    private void syncHistory() {
        if (mVod != null) mVod.syncHistory();
    }

    @Override
    protected void onKeepChanged() {
        checkKeepImg();
    }

    private void checkKeepImg() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(ResUtil.getDrawable(Boolean.TRUE.equals(mViewModel.getKeep().getValue()) ? R.drawable.ic_detail_keep_on : R.drawable.ic_detail_keep_off), null, null, null);
    }


    private void updateKeep(Vod item) {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(item.getName());
            keep.setVodPic(item.getPic());
            keep.save();
        }
    }

    private void updateVod(Vod item) {
        boolean id = !item.getId().isEmpty();
        boolean pic = !item.getPic().isEmpty();
        boolean name = !item.getName().isEmpty();
        if (id) getIntent().putExtra("id", item.getId());
        if (name && mHistory != null) mHistory.setVodName(item.getName());
        if (name) mBinding.name.setText(item.getName());
        if (name) mBinding.widget.title.setText(item.getName());
        mVod.mergeFlags(item.getFlags());
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        if (pic || name) syncHistory();
        if (pic || name) updateKeep(item);
        if (id) updateNavigationKey();
        if (name) setPartAdapter();
        setText(item);
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            if (isStop()) finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }
    };

    @Override
    protected void onPrepare() {
        setPlaybackMode();
    }

    @Override
    protected void onDecodeChanged() {
        setPlaybackMode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
    }

    @Override
    protected void onMediaOptionsChanged() {
        setMediaOptionVisible();
    }

    @Override
    protected void onError(String msg) {
        mVod.playbackError(msg);
    }

    @Override
    protected void onReclaim() {
        mVod.reclaim(player().getPosition());
    }

    @Override
    protected void onStateChanged(int state) {
        super.onStateChanged(state);
        switch (state) {
            case Player.STATE_BUFFERING:
                mClock.setCallback(null);
                if (mBinding.widget.status.getVisibility() == View.VISIBLE || mBinding.widget.progress.getVisibility() == View.VISIBLE) {
                    mBinding.widget.status.setVisibility(View.VISIBLE);
                    mBinding.widget.status.setText(R.string.play_buffering);
                }
                break;
            case Player.STATE_READY:
                mClock.setCallback(this);
                mBinding.video.setBackgroundResource(0);
                mBinding.exo.setBackgroundResource(0);
                mBinding.widget.status.setVisibility(View.GONE);
                if (!isFullscreen()) hideInfo();
                if (!isFullscreen()) hideCenter();
                break;
            case Player.STATE_ENDED:
                mClock.setCallback(null);
                break;
        }
    }

    private boolean isUpdatingInfo;

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isUpdatingInfo || mSeeking || !isFullscreen()) return;
        isUpdatingInfo = true;
        try {
            if (isPlaying) {
                hideCenter();
                hideInfo();
            } else {
                showInfo();
            }
        } finally {
            isUpdatingInfo = false;
        }
    }

    @Override
    public void onSubtitleClick() {
        SubTitleView.create().view(getPlayerView().getSubtitleView()).player(player()).show(this);
        App.post(this::hideControl, 100);
    }

    private long lastTimeUpdate;

    @Override
    public void onTimeChanged(long time) {
        if (!isOwner() || player() == null || isUpdatingInfo || isScrubbing() || mSeeking) return;
        if (System.currentTimeMillis() - lastTimeUpdate < 2000) return; // 🛠️ 降低進度更新頻率(2秒)，減輕主線程壓力
        lastTimeUpdate = System.currentTimeMillis();
        if (player().isPlaying()) mVod.onTimeChanged(time, player().getPosition(), player().getDuration());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) mVod.requestDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) mVod.refresh();
    }

    @Override
    protected long startPositionMs() {
        return mVod == null ? C.TIME_UNSET : mVod.startPositionMs();
    }

    private void setTrackVisible() {
        PlaybackAction.setTracks(player(), mBinding.control.text, mBinding.control.audio, mBinding.control.video);
    }

    private void setMediaOptionVisible() {
        // PlaybackAction.setMediaOptions(player(), mBinding.control.edition, mBinding.control.chapter);
    }

    private MediaMetadata buildMetadata() {
        return VodPlaybackMedia.metadata(mHistory, getEpisode());
    }

    private void setMetadata() {
        player().setMetadata(buildMetadata());
    }

    @Override
    public void onItemClick(Vod item) {
        mVod.selectSource(item);
    }

    @Override
    public void onItemLongClick(Vod item) {
        if (item.getSiteKey().equals(getKey())) return;
        new MaterialAlertDialogBuilder(this)
                .setMessage(ResUtil.getString(R.string.site_blacklist_confirm, item.getSiteName()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    Site site = Site.find(item.getSiteKey());
                    if (site == null) site = Site.get(item.getSiteKey(), item.getSiteName());
                    site.setBlacklist();
                    mQuickAdapter.removeBySiteKey(item.getSiteKey());
                }).show();
    }

    private void onPaused() {
        if (player() == null || player().getPlayer() == null) return;
        player().getPlayer().pause();
        showInfo();
    }

    private void onPlay() {
        if (player() == null || player().getPlayer() == null) return;
        if (mHistory != null && isEnded()) player().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) player().getPlayer().prepare();
        player().getPlayer().play();
        hideControl();
    }

    private boolean onSeekBack() {
        controller().seekBack();
        return true;
    }

    private boolean onSeekForward() {
        controller().seekForward();
        return true;
    }

    private View getFocus1() {
        return mFocus1 == null || mFocus1.getVisibility() != View.VISIBLE ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        List<View> list = Arrays.asList(mBinding.control.next, mBinding.control.reset, mBinding.control.player, mBinding.control.decode, mBinding.control.speed, mBinding.control.scale, mBinding.control.episodes);
        if (mFocus2 != null && mFocus2.getVisibility() == View.VISIBLE && mFocus2 != mBinding.control.opening && mFocus2 != mBinding.control.ending) return mFocus2;
        for (View view : list) if (view.getVisibility() == View.VISIBLE) return view;
        return mBinding.video;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mBinding == null) return false;
        if (isFullscreen() && KeyUtil.isBackKey(event)) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                if (isVisible(mBinding.control.getRoot())) hideControl();
                else if (isVisible(mBinding.widget.center)) hideCenter();
                else exitFullscreen();
            }
            return true;
        }
        if (!isFullscreen() && isVisible(mBinding.control.getRoot())) hideControl();
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();

        if (isFullscreen()) {
            if (KeyUtil.isMenuKey(event)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) onToggle();
                return true;
            }
            if (isGone(mBinding.control.getRoot())) {
                if (mKeyDown.hasEvent(event)) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && !KeyUtil.isEnterKey(event) && !KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) {
                        showControl(getFocus2());
                        return true;
                    }
                    return mKeyDown.onKeyDown(event);
                } else if (event.getAction() == KeyEvent.ACTION_DOWN && !KeyUtil.isVolumeKey(event)) {
                    showControl(getFocus2());
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onBrightEnd() {
        mBinding.widget.bright.setVisibility(View.GONE);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onVolumeEnd() {
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    @Override
    public void onSeeking(int time) {
        App.removeCallbacks(mHideCenter);
        App.removeCallbacks(mSeekReset);
        if (mBasePosition == -1) mBasePosition = player().getPosition();
        mSeeking = true;
        mBinding.video.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        mBinding.widget.getRoot().setVisibility(View.VISIBLE);
        mBinding.widget.getRoot().setTranslationZ(2000f);
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.video.bringToFront();
        mBinding.widget.getRoot().bringToFront();
        mBinding.widget.getRoot().requestLayout();
        mBinding.widget.exoDuration.setText(player().getDurationTime());
        mBinding.widget.exoPosition.setText(player().getPositionTime(mBasePosition, time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onSeekTo(int time) {
        if (mBasePosition == -1) return;
        long target = mBasePosition + time;
        long duration = player().getDuration();
        long finalPos = Math.max(0, Math.min(target, duration));
        android.util.Log.d("VideoActivity", "onSeekTo: finalPos=" + finalPos + " (base=" + mBasePosition + ", delta=" + time + ")");
        mKeyDown.resetTime();
        player().seekTo(finalPos);
        mBasePosition = finalPos;
        if (player().isPlaying()) App.post(mHideCenter, 500);
        App.post(mSeekReset, 2500);
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        mBinding.widget.speed.setVisibility(View.VISIBLE);
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        PlaybackAction.setSpeed(player(), mBinding.control.speed, PlayerSetting.getSpeed());
    }

    @Override
    public void onSpeedEnd() {
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        PlaybackAction.setSpeed(player(), mBinding.control.speed, mHistory.getSpeed());
    }

    @Override
    public void onKeyUp() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) {
            showControl(mBinding.control.opening);
        } else if (player().canSetEnding(position, duration)) {
            showControl(mBinding.control.ending);
        } else {
            showControl(getFocus2());
        }
    }

    @Override
    public void onKeyDown() {
        showControl(getFocus2());
    }

    @Override
    public void onKeyCenter() {
        if (!isFullscreen()) {
            enterFullscreen();
        } else if (player().isPlaying() || player().getPlayer().getPlayWhenReady()) {
            onPaused();
        } else if (player().isEmpty()) {
            onRefresh();
            hideControl();
        } else {
            onPlay();
        }
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        Source.get().exit();
        App.removeCallbacks(mR1, mR2, mR3, mR4, mDataTimer);
        super.onDestroy();
    }

    @Override
    protected void showProgress() {
        if (mBinding == null) return;
        if (mBinding.progressLayout.isContent() || mBinding.name.length() > 0) {
            if (mBinding.widget.progress.getVisibility() == View.VISIBLE) return;
            mBinding.widget.progress.setVisibility(View.VISIBLE);
            if (!mBinding.progressLayout.isContent()) mBinding.progressLayout.showContent();
        } else {
            mBinding.progressLayout.showProgress();
        }
    }

    @Override
    protected void hideProgress() {
        if (mBinding == null) return;
        if (mBinding.widget.status.getVisibility() == View.VISIBLE) {
            mBinding.widget.status.setText(R.string.play_timeout_success);
            App.post(() -> {
                if (mBinding != null && player() != null && player().getPlaybackState() == Player.STATE_BUFFERING) {
                    mBinding.widget.status.setText(R.string.play_buffering);
                } else if (mBinding != null) {
                    mBinding.widget.status.setVisibility(View.GONE);
                }
            }, 1000);
        }
        if (mBinding.widget.progress.getVisibility() == View.GONE && mBinding.progressLayout.isContent()) return;
        mBinding.widget.status.setVisibility(View.GONE);
        mBinding.widget.progress.setVisibility(View.GONE);
        mBinding.progressLayout.showContent();
    }

    @Override
    protected boolean handleBack() {
        if (isFullscreen()) {
            mViewModel.setFullscreen(false);
            return true;
        }
        return false;
    }

    @Override
    protected void onBackPress() {
        if (mBinding == null) return;
        if (isVisible(mBinding.control.getRoot())) {
            android.util.Log.d("VideoActivity", "onBackPress: hiding control");
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            android.util.Log.d("VideoActivity", "onBackPress: hiding center");
            hideCenter();
        } else if (isFullscreen()) {
            android.util.Log.d("VideoActivity", "onBackPress: exiting fullscreen");
            mViewModel.setFullscreen(false);
        } else {
            android.util.Log.d("VideoActivity", "onBackPress: finishing activity");
            mViewModel.stopSearch();
            finish();
        }
    }
}
