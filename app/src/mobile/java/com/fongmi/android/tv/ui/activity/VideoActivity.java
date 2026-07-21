package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.event.ActionEvent;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.model.PlaybackViewModel;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.base.BaseVideoActivity;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.PlayerSeekView;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SubTitleView;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.IDMUtil;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.bassaer.library.MDColor;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Trans;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.permissionx.guolindev.PermissionX;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

public class VideoActivity extends BaseVideoActivity implements ControlDialog.Listener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private Observer<Result> mObserveDownload;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ControlDialog mControlDialog;
    private QuickAdapter mQuickAdapter;
    private ParseAdapter mParseAdapter;
    private List<Dialog> mDialogs;
    private boolean foreground;
    private boolean rotate;
    private Runnable mR0;
    private PiP mPiP;

    public static void push(FragmentActivity activity, String text) {
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(activity, Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> start(activity, "push_agent", "file://" + path, name));
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void download(Activity activity, String id, String name, String pic) {
        start(activity, "push_agent", id, name, pic, null, false, true);
    }

    public static void start(Activity activity, String url) {
        start(activity, "push_agent", url, url, null);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null, null, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        start(activity, key, id, name, pic, mark, collect, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean download) {
        Intent intent = new Intent(activity, VideoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("download", download);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    private PlayerView getExo() {
        return mBinding.exo;
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return null;
    }

    @Override
    protected PlayerView getPlayerView() {
        return getExo();
    }

    @Override
    protected PlayerSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    private boolean isFromDownload() {
        return getIntent().getBooleanExtra("download", false);
    }

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return mBinding.getRoot().getTag().equals("land");
    }

    private boolean isPort() {
        return mBinding.getRoot().getTag().equals("port");
    }

    @Override
    protected boolean transparent() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        mKeyDown = CustomKeyDownVod.create(this, mBinding.video);
        mFrameParams = mBinding.video.getLayoutParams();
        mBinding.progressLayout.showProgress();
        mBinding.swipeLayout.setEnabled(false);
        mObserveDetail = this::setDetailResult;
        mObservePlayer = this::setPlayerResult;
        mObserveDownload = this::setDownload;
        mObserveSearch = this::setSearchResult;
        mPlayers = Players.create(this);
        mDialogs = new ArrayList<>();
        mBroken = new ArrayList<>();
        mClock = Clock.create(Arrays.asList(mBinding.display.clock, mBinding.control.time));
        mR0 = this::stopService;
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mPiP = new PiP();
        setForeground(true);
        setRecyclerView();
        setVideoView();
        setDisplayView();
        setViewModel();
        showProgress();
        checkId();
    }

    private void setDetailResult(Result result) {
        if (result.getList().isEmpty()) setEmpty(result.hasMsg());
        else setDetail(result.getVod());
    }

    private void setPlayerResult(Result result) {
        setPlayer(result);
    }

    private void setSearchResult(Result result) {
        setSearch(result);
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.actor.setOnClickListener(view -> onActor());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.download.setOnClickListener(view -> onDownload());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.full.setOnClickListener(view -> onFull());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.right.back.setOnClickListener(view -> onFull());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.loop.setOnClickListener(view -> onLoop());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.save.setOnClickListener(view -> onSave());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.player.setOnLongClickListener(view -> onChoose());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
        mBinding.control.seek.setListener(mPlayers);
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.episode.setHasFixedSize(true);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.HORI));
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.control.parse.setHasFixedSize(true);
        mBinding.control.parse.setItemAnimator(null);
        mBinding.control.parse.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this, ViewType.DARK));
    }

    @Override
    protected void setPlayerView() {
        mBinding.control.action.player.setText(mPlayers.getPlayerText());
        mBinding.control.action.speed.setEnabled(mPlayers.canAdjustSpeed());
        mBinding.control.action.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
        getExo().setVisibility(View.VISIBLE);
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.updatePlayer();
    }

    @Override
    protected void setDecodeView() {
        mBinding.control.action.decode.setText(mPlayers.getDecodeText());
        if (mControlDialog != null && mControlDialog.isVisible()) mControlDialog.updateDecode();
    }

    private void setVideoView() {
        mPlayers.init(getExo());
        ExoUtil.setSubtitleView(mBinding.exo);
        if (isPort() && ResUtil.isLand(this)) enterFullscreen();
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(getActivity(), view));
    }

    private void setDisplayView() {
        mBinding.display.getRoot().setVisibility(View.VISIBLE);
        showDisplayInfo();
    }

    @Override
    protected void setScale(int scale) {
        getExo().setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    protected void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(PlaybackViewModel.class);
        mViewModel.getKeep().observe(this, keep -> onKeepChanged());
        mViewModel.result.observeForever(mObserveDetail);
        mViewModel.player.observeForever(mObservePlayer);
        mViewModel.search.observeForever(mObserveSearch);
        mViewModel.download.observeForever(mObserveDownload);
        mViewModel.episode.observe(this, episode -> {
            onItemClick(episode);
            hideSheet();
        });
        mViewModel.ep.observe(this, episode -> {
            Notify.progress(this);
            Downloader.get().title(mBinding.name.getText() + "-" + episode.getName());
            mViewModel.download(getKey(), getFlag().getFlag(), episode.getUrl());
        });
        mVod = mViewModel.createPlaybackController(this);
    }

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", "push_agent").putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    @Override
    protected void setPlayer(Result result) {
    }

    @Override
    protected void onVodChanged(Vod item) {
        mBinding.progressLayout.showContent();
        if (isFromDownload()) item.setVodName("");
        if (isFromDownload()) item.setVodPic("");
        mBinding.video.setTag(item.getVodPic(getPic()));
        mBinding.name.setText(item.getVodName(getName()));
        Downloader.get().image(item.getVodPic());
        setText(mBinding.remark, 0, item.getVodRemarks());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.content, 0, Html.fromHtml(item.getVodContent()).toString());
        setText(mBinding.actor, R.string.detail_actor, Html.fromHtml(item.getVodActor()).toString());
        setText(mBinding.director, R.string.detail_director, Html.fromHtml(item.getVodDirector()).toString());
        mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
        setOther(mBinding.other, item);
        setArtwork(item.getVodPic());
        App.removeCallbacks(mR4);
        mViewModel.checkKeep(getHistoryKey());
    }

    @Override
    protected void onHistoryChanged(History history) {
        super.onHistoryChanged(history);
        if (!TextUtils.isEmpty(getMark())) history.setVodRemarks(getMark());
        if (Setting.isIncognito() && history.getKey().equals(getHistoryKey())) history.delete();
        mBinding.control.action.opening.setText(history.getOpening() == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(history.getOpening()));
        mBinding.control.action.ending.setText(history.getEnding() == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(history.getEnding()));
        mPlayers.setPlayer(getPlayer());
        setScale(getScale());
        setPlayerView();
        setDecodeView();
    }

    @Override
    protected void onFlagsChanged(List<Flag> items) {
        boolean empty = items.isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) ErrorEvent.flag();
        else mFlagAdapter.addAll(items);
    }

    @Override
    protected void onEpisodesChanged(List<Episode> items) {
        setEpisodeAdapter(items);
    }

    @Override
    protected void onFlagChanged(Flag item) {
        mFlagAdapter.setActivated(item);
        mBinding.flag.scrollToPosition(mFlagAdapter.getPosition());
        setQualityVisible(false);
    }

    @Override
    protected void onEpisodeChanged(Episode item) {
        mFlagAdapter.toggle(item);
        notifyItemChanged(mEpisodeAdapter);
        mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
    }

    @Override
    protected void onQualityChanged(Result result) {
        mQualityAdapter.addAll(result);
    }

    @Override
    protected void onArtworkChanged(String url) {
        setArtwork(url);
    }

    @Override
    protected void onSourcesChanged(List<Vod> items) {
        mBinding.quick.setVisibility(View.VISIBLE);
        mQuickAdapter.addAll(items);
    }

    @Override
    protected void onQualityVisibleChanged(boolean visible) {
        setQualityVisible(visible);
    }

    @Override
    protected void onUseParseChanged(boolean useParse) {
        setUseParse(useParse);
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
                    finish();
                }).show();
        return true;
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mVod.selectEpisode(item);
    }

    @Override
    public void onItemClick(Result result) {
        mVod.selectQuality(result);
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

    @Override
    public void onItemClick(Parse item) {
        mVod.selectParse(item);
    }

    @Override
    public void onScale(int tag) {
        mHistory.setScale(tag);
        setScale(tag);
    }

    @Override
    public void onParse(Parse item) {
        onItemClick(item);
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
        if (scroll) mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition());
    }

    @Override
    protected void showProgress() {
        if (mBinding != null) mBinding.progressLayout.showProgress();
    }

    @Override
    protected void hideProgress() {
        if (mBinding != null) mBinding.progressLayout.showContent();
    }
}
