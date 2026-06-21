package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.BaseGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.FlagScore;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Part;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.IjkUtil;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.player.danmu.Parser;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.base.BaseVideoActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.dialog.DescDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeDialog;
import com.fongmi.android.tv.ui.dialog.FileChooserDialog;
import com.fongmi.android.tv.ui.dialog.PlayerDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.presenter.ArrayPresenter;
import com.fongmi.android.tv.ui.presenter.EpisodePresenter;
import com.fongmi.android.tv.ui.presenter.FlagPresenter;
import com.fongmi.android.tv.ui.presenter.ParsePresenter;
import com.fongmi.android.tv.ui.presenter.PartPresenter;
import com.fongmi.android.tv.ui.presenter.QuickPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;
import com.github.bassaer.library.MDColor;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;

import master.flame.danmaku.danmaku.model.BaseDanmaku;
import master.flame.danmaku.danmaku.model.IDisplayer;
import okhttp3.Call;
import okhttp3.Response;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class VideoActivity extends BaseVideoActivity implements ArrayPresenter.OnClickListener, TrackDialog.ChooserListener {

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private EpisodePresenter mEpisodePresenter;
    private ArrayObjectAdapter mEpisodeAdapter;
    private ArrayObjectAdapter mArrayAdapter;
    private ArrayObjectAdapter mParseAdapter;
    private ArrayObjectAdapter mQuickAdapter;
    private ArrayObjectAdapter mFlagAdapter;
    private ArrayObjectAdapter mPartAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayPresenter mArrayPresenter;
    private FlagPresenter mFlagPresenter;
    private PartPresenter mPartPresenter;
    private boolean background;
    private int groupSize;
    private View mFocus1;
    private View mFocus2;
    private boolean hasKeyEvent;

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean clear, boolean cast, boolean collect) {
        Intent intent = new Intent(activity, VideoActivity.class);
        if (clear) intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivityForResult(intent, 1000);
    }

    private BaseGridView getEpisodeView() {
        return Setting.getEpisode() == 0 ? mBinding.episodeHori : mBinding.episodeVert;
    }

    private void setEpisodeSelectedPosition(int position) {
        getEpisodeView().setSelectedPosition(position);
        if (hasKeyEvent) return;
        if (isFullscreen()) return;
        getEpisodeView().postDelayed(() -> {
            View selectedItem = getEpisodeView().getLayoutManager().findViewByPosition(position);
            View focusedView = getCurrentFocus();
            if (selectedItem != null) selectedItem.requestFocus();
            if (focusedView == mBinding.video) mBinding.video.requestFocus();
        }, 300);
    }

    private PlayerView getExo() {
        return mBinding.exo;
    }

    private IjkVideoView getIjk() {
        return mBinding.ijk;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        mKeyDown = CustomKeyDownVod.create(this, mBinding.video);
        mFrameParams = mBinding.video.getLayoutParams();
        mClock = Clock.create(mBinding.display.clock);
        mDanmakuContext = master.flame.danmaku.danmaku.model.android.DanmakuContext.create();
        mPlayers = Players.create(this);
        mBroken = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showEmpty;
        setBackground(false);
        setRecyclerView();
        setEpisodeView();
        setVideoView();
        setDisplayView();
        setDanmuView();
        setViewModel();
        checkCast();
        checkId();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.control.seek.setListener(mPlayers);
        mBinding.desc.setOnClickListener(view -> onDesc());
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
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
        mBinding.control.loop.setOnClickListener(view -> onLoop());
        mBinding.control.danmu.setOnClickListener(view -> onDanmu());
        mBinding.control.danmu.setUpListener(this::onDanmuAdd);
        mBinding.control.danmu.setDownListener(this::onDanmuSub);
        mBinding.control.save.setOnClickListener(view -> onSave());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.scale.setOnClickListener(view -> onScale());
        mBinding.control.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.reset.setOnClickListener(view -> onReset());
        mBinding.control.player.setOnClickListener(view -> onPlayer());
        mBinding.control.decode.setOnClickListener(view -> onDecode());
        mBinding.control.ending.setOnClickListener(view -> onEnding());
        mBinding.control.opening.setOnClickListener(view -> onOpening());
        mBinding.control.player.setOnLongClickListener(view -> { onChoose(); return true; });
        mBinding.control.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mFlagAdapter.size() > 0) setFlagActivated((Flag) mFlagAdapter.get(position));
            }
        });
        getEpisodeView().addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null) mFocus1 = child.itemView;
                setEpisodeChildKeyListener(child, position);
            }
        });
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mEpisodeAdapter.size() > getGroupSize() && position > 1 && hasKeyEvent) setEpisodeSelectedPosition((position - 2) * getGroupSize());
            }
        });
    }

    private void setEpisodeChildKeyListener(RecyclerView.ViewHolder child, int position) {
        if (getEpisodeView() != mBinding.episodeVert) return;
        int itemCount = getEpisodeView().getAdapter().getItemCount();
        if (itemCount <= 0) return;
        int columns = mEpisodePresenter.getNumColumns();
        if ((position + columns >= itemCount) && ((position % columns) + 1 > (itemCount % columns))) {
            child.itemView.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
                        View lastItem =  getEpisodeView().getLayoutManager().findViewByPosition(itemCount - 1);
                        if (lastItem != null) lastItem.requestFocus();
                    }
                    return false;
                }
            });
        }
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(new ItemBridgeAdapter(mFlagAdapter = new ArrayObjectAdapter(mFlagPresenter = new FlagPresenter(this::setFlagActivated))));
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this::setQualityActivated));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(new ItemBridgeAdapter(mArrayAdapter = new ArrayObjectAdapter(mArrayPresenter = new ArrayPresenter(this))));
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(new ItemBridgeAdapter(mPartAdapter = new ArrayObjectAdapter(mPartPresenter = new PartPresenter(item -> initSearch(item, false)))));
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(new ItemBridgeAdapter(mQuickAdapter = new ArrayObjectAdapter(new QuickPresenter(this::setSearch))));
        mBinding.control.parse.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.control.parse.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.control.parse.setAdapter(new ItemBridgeAdapter(mParseAdapter = new ArrayObjectAdapter(new ParsePresenter(this::setParseActivated))));
        mParseAdapter.setItems(VodConfig.get().getParses(), null);
    }

    private void setEpisodeView() {
        mBinding.episodeVert.setVerticalSpacing(ResUtil.dp2px(8));
        mBinding.episodeHori.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episodeVert.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episodeHori.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        getEpisodeView().setAdapter(new ItemBridgeAdapter(mEpisodeAdapter = new ArrayObjectAdapter(mEpisodePresenter = new EpisodePresenter(this::setEpisodeActivated))));
    }

    private void setVideoView() {
        mPlayers.init(getExo(), getIjk());
        ExoUtil.setSubtitleView(mBinding.exo);
        IjkUtil.setSubtitleView(mBinding.ijk);
        mBinding.control.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
    }

    private void setDanmuViewSettings() {
        float[] range = {2.4f, 1.8f, 1.2f, 0.8f};
        float speed = range[Setting.getDanmuSpeed()];
        float alpha = Setting.getDanmuAlpha() / 100.0f;
        float sizeScale = isFullscreen() ? 1.2f * Setting.getDanmuSize() : 0.8f * Setting.getDanmuSize();
        int maxLine = Setting.getDanmuLine(3);
        HashMap<Integer, Integer> maxLines = new HashMap<>();
        maxLines.put(BaseDanmaku.TYPE_FIX_TOP, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_RL, maxLine);
        maxLines.put(BaseDanmaku.TYPE_SCROLL_LR, maxLine);
        maxLines.put(BaseDanmaku.TYPE_FIX_BOTTOM, maxLine);
        mDanmakuContext.setMaximumLines(maxLines).setScrollSpeedFactor(speed).setDanmakuTransparency(alpha).setScaleTextSize(sizeScale);
    }

    private void setDanmuView() {
        mPlayers.setDanmuView(mBinding.danmaku);
        setDanmuViewSettings();
        mDanmakuContext.setDanmakuStyle(IDisplayer.DANMAKU_STYLE_STROKEN, 3).setDanmakuMargin(8);
        mBinding.control.danmu.setActivated(Setting.isDanmu());
    }

    private void setDisplayView() {
        mBinding.display.getRoot().setVisibility(View.VISIBLE);
        showDisplayInfo();
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(this, this::setDetail);
        mViewModel.player.observe(this, this::setPlayer);
        mViewModel.search.observe(this, this::setSearch);
    }

    private void checkCast() {
        if (getIntent().getBooleanExtra("cast", false)) onVideo();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", "push_agent").putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else getDetail();
    }

    @Override
    protected void setPlayerView() {
        getIjk().setPlayer(mPlayers.getPlayer());
        mBinding.control.player.setText(mPlayers.getPlayerText());
        mBinding.control.speed.setEnabled(mPlayers.canAdjustSpeed());
        getExo().setVisibility(mPlayers.isExo() ? View.VISIBLE : View.GONE);
        getIjk().setVisibility(mPlayers.isIjk() ? View.VISIBLE : View.GONE);
        mBinding.control.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
    }

    @Override
    protected void setDecodeView() {
        mBinding.control.decode.setText(mPlayers.getDecodeText());
    }

    @Override
    protected void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        mBinding.control.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    @Override
    protected void setPlayer(Result result) {
        result.getUrl().set(mQualityAdapter.getPosition());
        super.setPlayer(result);
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        mQualityAdapter.addAll(result);
    }

    @Override
    protected void checkDanmu(String danmu) {
        mBinding.danmaku.release();
        if (!Setting.isDanmuLoad()) return;
        mBinding.danmaku.setVisibility(danmu.isEmpty() ? View.GONE : View.VISIBLE);
        if (danmu.length() > 0) App.execute(() -> mBinding.danmaku.prepare(new Parser(danmu), mDanmakuContext));
    }

    @Override
    protected void setEmpty(boolean finish) {
        if (getIntent().getBooleanExtra("collect", false) || finish) {
            finish();
        } else if (getName().isEmpty()) {
            showEmpty();
        } else {
            mBinding.name.setText(getName());
            App.post(mR4, 10000);
            checkSearch(false);
        }
    }

    @Override
    protected void showEmpty() {
        mBinding.progressLayout.showEmpty();
        stopSearch();
    }

    @Override
    protected void setDetail(Vod item) {
        mBinding.progressLayout.showContent();
        mBinding.video.setTag(item.getVodPic(getPic()));
        mBinding.name.setText(item.getVodName(getName()));
        setText(mBinding.remark, 0, item.getVodRemarks());
        setText(mBinding.year, R.string.detail_year, item.getVodYear());
        setText(mBinding.area, R.string.detail_area, item.getVodArea());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.actor, R.string.detail_actor, Html.fromHtml(item.getVodActor()).toString());
        setText(mBinding.content, R.string.detail_content, Html.fromHtml(item.getVodContent()).toString());
        setText(mBinding.director, R.string.detail_director, Html.fromHtml(item.getVodDirector()).toString());
        sortFlags(item.getVodFlags());
        mFlagAdapter.setItems(item.getVodFlags(), null);
        mBinding.content.setMaxLines(getMaxLines());
        mBinding.video.requestFocus();
        setArtwork(item.getVodPic());
        getPart(item.getVodName());
        App.removeCallbacks(mR4);
        checkHistory(item);
        checkFlag(item);
        checkKeep();
    }

    private void sortFlags(List<Flag> flags) {
        if (flags.size() <= 1) return;
        List<FlagScore> scores = AppDatabase.get().getFlagScoreDao().findBySite(getKey());
        Map<String, Integer> scoreMap = new HashMap<>();
        for (FlagScore score : scores) scoreMap.put(score.getFlagName(), score.getScore());
        Collections.sort(flags, (o1, o2) -> {
            Integer s1 = scoreMap.get(o1.getFlag());
            Integer s2 = scoreMap.get(o2.getFlag());
            return Integer.compare(s2 == null ? 0 : s2, s1 == null ? 0 : s1);
        });
    }

    private int getMaxLines() {
        return 1 + (isGone(mBinding.actor) ? 1 : 0) + (isGone(mBinding.remark) ? 1 : 0) + (isGone(mBinding.director) ? 1 : 0);
    }

    private void setText(TextView view, int resId, String text) {
        view.setText(getSpan(resId, text), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
        view.setTag(text);
    }

    private SpannableStringBuilder getSpan(int resId, String text) {
        String content = resId > 0 ? getString(resId, text) : text;
        Map<String, String> map = new HashMap<>();
        Matcher m = com.fongmi.android.tv.utils.Sniffer.CLICKER.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = Trans.s2t(m.group(2)).trim();
            m.appendReplacement(sb, key);
            map.put(key, m.group(1));
        }
        m.appendTail(sb);
        String finalContent = sb.toString();
        SpannableStringBuilder span = SpannableStringBuilder.valueOf(finalContent);
        for (String s : map.keySet()) {
            int index = finalContent.indexOf(s);
            if (index != -1) span.setSpan(getClickSpan(Result.type(map.get(s))), index, index + s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private ClickableSpan getClickSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                VodActivity.start(getActivity(), getKey(), result);
            }
        };
    }

    private void setFlagActivated(Flag item) {
        if (mFlagAdapter.size() == 0 || item.isActivated()) return;
        if (mFlagAdapter.indexOf(item) == -1) item.setFlag(((Flag) mFlagAdapter.get(0)).getFlag());
        for (int i = 0; i < mFlagAdapter.size(); i++) ((Flag) mFlagAdapter.get(i)).setActivated(item);
        mBinding.flag.setSelectedPosition(mFlagAdapter.indexOf(item));
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
        seamless(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        getEpisodeView().setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        if (isVisible(mBinding.episodeVert)) setEpisodeView(items);
        mEpisodeAdapter.setItems(items, null);
        setArrayAdapter(items.size());
        setR2Callback(50);
    }

    private void setEpisodeView(List<Episode> items) {
        int size = items.size();
        int episodeNameLength = items.isEmpty() ? 0 : items.get(0).getName().length();
        for (int i = 0; i < size; i++) {
            items.get(i).setIndex(i);
            int length = items.get(i).getName() == null ? 0 : items.get(i).getName().length();
            if (length > episodeNameLength) episodeNameLength = length;
        }
        int numColumns = 10;
        if (episodeNameLength > 40) numColumns = 1;
        if (episodeNameLength > 30) numColumns = 2;
        else if (episodeNameLength > 15) numColumns = 3;
        else if (episodeNameLength > 10) numColumns = 4;
        else if (episodeNameLength > 6) numColumns = 6;
        else if (episodeNameLength > 4) numColumns = 8;
        int rowNum = (int) Math.ceil((double) size / (double) numColumns);
        int width = ResUtil.getScreenWidth() - ResUtil.dp2px(48);
        ViewGroup.LayoutParams params = mBinding.episodeVert.getLayoutParams();
        params.width = ResUtil.getScreenWidth();
        params.height = rowNum > 6 ? ResUtil.dp2px(300) : ResUtil.dp2px(rowNum * 44);
        mBinding.episodeVert.setNumColumns(numColumns);
        mBinding.episodeVert.setColumnWidth((width - ((numColumns - 1) * ResUtil.dp2px(8))) / numColumns);
        mBinding.episodeVert.setLayoutParams(params);
        mBinding.episodeVert.setWindowAlignmentOffsetPercent(10f);
        mEpisodePresenter.setNumColumns(numColumns);
        mEpisodePresenter.setNumRows(rowNum);
    }

    private void seamless(Flag flag) {
        Episode episode = flag.find(mHistory.getVodRemarks(), getMark().isEmpty());
        setQualityVisible(episode != null && episode.isActivated() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isActivated()) return;
        if (Setting.getFlag() == 1) {
            episode.setActivated(true);
            if (!isFullscreen()) getEpisodeView().requestFocus();
            setEpisodeSelectedPosition(getEpisodePosition());
            episode.setActivated(false);
        } else {
            mHistory.setVodRemarks(episode.getName());
            setEpisodeActivated(episode);
            hidePreview();
        }
    }

    public void setEpisodeActivated(Episode item) {
        int flagPosition = getFlagPosition();
        if (shouldEnterFullscreen(item)) return;
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        for (int i = 0; i < mFlagAdapter.size(); i++) ((Flag) mFlagAdapter.get(i)).toggle(flagPosition == i, item);
        setEpisodeSelectedPosition(getEpisodePosition());
        notifyItemChanged(getEpisodeView(), mEpisodeAdapter);
        onRefresh();
    }

    @Override
    protected void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        setR2Callback(100);
    }

    private void setQualityActivated(Result result) {
        try {
            mPlayers.start(result, isUseParse(), getSite().isChangeable() ? getSite().getTimeout() : -1);
            mBinding.danmaku.hide();
        } catch (Exception e) {
            ErrorEvent.extract(e.getMessage());
            e.printStackTrace();
        }
    }

    private void reverseEpisode(boolean scroll) {
        for (int i = 0; i < mFlagAdapter.size(); i++) Collections.reverse(((Flag) mFlagAdapter.get(i)).getEpisodes());
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) setEpisodeSelectedPosition(getEpisodePosition());
    }

    private void setParseActivated(Parse item) {
        VodConfig.get().setParse(item);
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
        onRefresh();
    }

    private void setArrayAdapter(int size) {
        if (size > 200) setGroupSize(100);
        else if (size > 100) setGroupSize(40);
        else setGroupSize(20);
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) for (int i = size; i > 0; i -= getGroupSize()) items.add(i + "-" + Math.max(i - (getGroupSize() - 1), 1));
        else for (int i = 0; i < size; i += getGroupSize()) items.add((i + 1) + "-" + Math.min(i + getGroupSize(), size));
        mArrayAdapter.setItems(items, null);
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episodeHori, R.id.array, R.id.episodeVert, R.id.part, R.id.quick);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.episodeHori, R.id.array, R.id.episodeVert, R.id.part, R.id.quick);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        hasKeyEvent = false;
        mEpisodePresenter.setNextFocusDown(findFocusDown(Setting.getEpisode() == 0 ? 2 : 4));
        mEpisodePresenter.setNextFocusUp(findFocusUp(Setting.getEpisode() == 0 ? 2 : 4));
        mQualityAdapter.setNextFocusDown(findFocusDown(1));
        mArrayPresenter.setNextFocusDown(findFocusDown(3));
        mFlagPresenter.setNextFocusDown(findFocusDown(0));
        mArrayPresenter.setNextFocusUp(findFocusUp(3));
        mPartPresenter.setNextFocusUp(findFocusUp(5));
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        notifyItemChanged(mBinding.quality, mQualityAdapter);
        notifyItemChanged(mBinding.array, mArrayAdapter);
        notifyItemChanged(getEpisodeView(), mEpisodeAdapter);
        notifyItemChanged(mBinding.part, mPartAdapter);
    }

    private void showDisplayInfo() {
        boolean hasDialog = false;
        for (Fragment f : getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) hasDialog = true;
        mBinding.display.clock.setVisibility(Setting.isDisplayTime() || isVisible(mBinding.widget.info)  ? View.VISIBLE : View.GONE);
        mBinding.display.titleLayout.setVisibility(Setting.isDisplayVideoTitle() && !isVisible(mBinding.control.getRoot()) ? View.VISIBLE : View.GONE);
        mBinding.display.netspeed.setVisibility(Setting.isDisplaySpeed() && !isVisible(mBinding.control.getRoot()) && !hasDialog ? View.VISIBLE : View.GONE);
        mBinding.display.duration.setVisibility(Setting.isDisplayDuration() && !isVisible(mBinding.control.getRoot()) && (mPlayers.isVod()) && !hasDialog ? View.VISIBLE : View.GONE);
        mBinding.display.progress.setVisibility(Setting.isDisplayMiniProgress() && !isVisible(mBinding.control.getRoot()) && (mPlayers.isVod()) && !hasDialog ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onTimeChangeDisplaySpeed() {
        boolean visible = !isVisible(mBinding.control.getRoot());
        long position = mPlayers.getPosition();
        if (Setting.isDisplaySpeed() && visible) Traffic.setSpeed(mBinding.display.netspeed);
        if (Setting.isDisplayDuration() && visible && position > 0) mBinding.display.duration.setText(mPlayers.getPositionTime(0) + "/" + mPlayers.getDurationTime());
        if (Setting.isDisplayMiniProgress() && visible && position > 0 && (mPlayers.isVod())) mBinding.display.progress.setProgress((int)(position * 100 / mPlayers.getDuration()));
        showDisplayInfo();
    }

    @Override
    public boolean onArrayItemTouch() {
        hasKeyEvent = true;
        return false;
    }

    @Override
    public void onRevSort() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        mHistory.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isActivated();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        mFocus1 = getCurrentFocus();
        mBinding.video.requestFocus();
        mBinding.video.setForeground(null);
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.flag.setSelectedPosition(getFlagPosition());
        mDanmakuContext.setScaleTextSize(1.2f * Setting.getDanmuSize());
        mKeyDown.setFull(true);
        setFullscreen(true);
        mFocus2 = null;
        onPlay();
    }

    private void exitFullscreen() {
        mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
        mBinding.video.setLayoutParams(mFrameParams);
        mDanmakuContext.setScaleTextSize(0.8f * Setting.getDanmuSize());
        getFocus1().requestFocus();
        mKeyDown.setFull(false);
        setFullscreen(false);
        mFocus2 = null;
        hideInfo();
    }

    private void onDesc() {
        CharSequence desc = mBinding.content.getText();
        if (desc.length() > 3) DescDialog.show(this, desc.subSequence(3, desc.length()));
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        RefreshEvent.keep();
        checkKeep();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        checkSearch(true);
    }

    private void onLoop() {
        mBinding.control.loop.setActivated(!mBinding.control.loop.isActivated());
    }

    private void onDanmu() {
        Setting.putDanmu(!Setting.isDanmu());
        mBinding.control.danmu.setActivated(Setting.isDanmu());
        showDanmu();
    }

    private void showDanmu() {
        if (Setting.isDanmu()) mBinding.danmaku.show();
        else mBinding.danmaku.hide();
    }

    private void onDanmuAdd() {
        int line = Setting.getDanmuLine(3);
        line = Math.min(line + 1, 15);
        Setting.putDanmuLine(line);
        mBinding.control.danmu.setText(line + ResUtil.getString(R.string.lines));
        setDanmuViewSettings();
    }

    private void onDanmuSub() {
        int line = Setting.getDanmuLine(3);
        line = Math.max(line - 1, 1);
        Setting.putDanmuLine(line);
        mBinding.control.danmu.setText(line + ResUtil.getString(R.string.lines));
        setDanmuViewSettings();
    }

    private void onEpisodes() {
        EpisodeDialog.create().episodes(getFlag().getEpisodes()).show(this);
        hideControl();
    }

    @Override
    protected void checkNext() {
        if (mHistory.isRevPlay()) onPrev();
        else onNext();
    }

    @Override
    protected void checkPrev() {
        if (mHistory.isRevPlay()) onNext();
        else onPrev();
    }

    private void onNext() {
        int current = getEpisodePosition();
        int max = mEpisodeAdapter.size() - 1;
        current = ++current > max ? max : current;
        Episode item = (Episode) mEpisodeAdapter.get(current);
        if (item.isActivated()) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
        else setEpisodeActivated(item);
    }

    private void onPrev() {
        int current = getEpisodePosition();
        current = --current < 0 ? 0 : current;
        Episode item = (Episode) mEpisodeAdapter.get(current);
        if (item.isActivated()) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
        else setEpisodeActivated(item);
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        mHistory.setScale(index = index == array.length - 1 ? 0 : ++index);
        setScale(index);
    }

    private void onSpeed() {
        mBinding.control.speed.setText(mPlayers.addSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
    }

    private void onSpeedAdd() {
        mBinding.control.speed.setText(mPlayers.addSpeed(0.25f));
        mHistory.setSpeed(mPlayers.getSpeed());
    }

    private void onSpeedSub() {
        mBinding.control.speed.setText(mPlayers.subSpeed(0.25f));
        mHistory.setSpeed(mPlayers.getSpeed());
    }

    private boolean onSpeedLong() {
        mBinding.control.speed.setText(mPlayers.toggleSpeed());
        mHistory.setSpeed(mPlayers.getSpeed());
        return true;
    }

    @Override
    protected void onRefresh() {
        onReset(false);
    }

    private void onReset() {
        onReset(isReplay());
    }

    private void onReset(boolean replay) {
        mPlayers.clear();
        mPlayers.stop();
        mClock.setCallback(null);
        if (mFlagAdapter.size() == 0) return;
        if (mEpisodeAdapter.size() == 0) return;
        getPlayer(getFlag(), getEpisode(), replay);
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onOpening() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || current > duration / 2) return;
        setOpening(current);
    }

    private void onOpeningAdd() {
        setOpening(Math.min(mHistory.getOpening() + 1000, mPlayers.getDuration() / 2));
    }

    private void onOpeningSub() {
        setOpening(Math.max(0, mHistory.getOpening() - 1000));
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    @Override
    protected void setOpening(long opening) {
        long time = Math.max(0, Math.min(opening, 10 * 60 * 1000));
        mHistory.setOpening(time);
        mBinding.control.opening.setText(time == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(time));
    }

    private void onEnding() {
        long current = mPlayers.getPosition();
        long duration = mPlayers.getDuration();
        if (current < 0 || current < duration / 2) return;
        setEnding(duration - current);
    }

    private void onEndingAdd() {
        setEnding(Math.min(mPlayers.getDuration() / 2, mHistory.getEnding() + 1000));
    }

    private void onEndingSub() {
        setEnding(Math.max(0, mHistory.getEnding() - 1000));
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    @Override
    protected void setEnding(long ending) {
        long time = Math.max(0, Math.min(ending, 10 * 60 * 1000));
        mHistory.setEnding(time);
        mBinding.control.ending.setText(time == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(time));
    }

    @Override
    protected boolean onChoose() {
        if (mPlayers.isEmpty()) return false;
        mPlayers.choose(this, mBinding.widget.title.getText());
        return true;
    }

    private void onPlayer() {
        PlayerDialog.create().select(mPlayers.getPlayer()).title(mBinding.widget.title.getText().toString()).show(this);
        hideControl();
    }

    private void onDecode() {
        onDecode(true);
    }

    @Override
    protected void onDecode(boolean save) {
        mPlayers.toggleDecode(save);
        mPlayers.init(getExo(), getIjk());
        mPlayers.setMediaSource();
        setDecodeView();
    }

    private void onTrack(View view) {
        TrackDialog.create().player(mPlayers).chooser(this).vod(true).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl(getFocus2());
    }

    @Override
    protected void showProgress() {
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        App.post(mR3, 0);
        hideError();
    }

    @Override
    protected void hideProgress() {
        mBinding.widget.progress.setVisibility(View.GONE);
        App.removeCallbacks(mR3);
        Traffic.reset();
    }

    @Override
    protected void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showInfo() {
        mBinding.widget.info.setVisibility(View.VISIBLE);
        showDisplayInfo();
    }

    private void hideInfo() {
        mBinding.widget.info.setVisibility(View.GONE);
        showDisplayInfo();
    }

    private void showInfoAndCenter() {
        showInfo();
        mBinding.widget.center.setVisibility(View.VISIBLE);
    }

    private void hideInfoAndCenter() {
        hideInfo();
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void setControlNextFocus() {
        int count = mBinding.control.actionLayout.getChildCount();
        for(int i=0; i<count-1; i++) {
            View btn = mBinding.control.actionLayout.getChildAt(i);
            if (btn == null || !isVisible(btn) || !btn.isEnabled()) continue;
            for(int j=i+1; j<count; j++) {
                View next = mBinding.control.actionLayout.getChildAt(j);
                if (next == null || !isVisible(next) || !next.isEnabled()) continue;
                btn.setNextFocusRightId(next.getId());
                next.setNextFocusLeftId(btn.getId());
                break;
            }
        }
    }

    private void showControl(View view) {
        mBinding.control.danmu.setVisibility(mBinding.danmaku.isPrepared() ? View.VISIBLE : View.GONE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        mBinding.control.episodes.setVisibility(Setting.getFullscreenMenuKey() == 0 ? View.VISIBLE : View.GONE);
        view.requestFocus();
        setControlNextFocus();
        setR1Callback();
    }

    @Override
    protected void hideControl() {
        hideControl(true);
    }

    private void hideControl(boolean hideInfo) {
        if (hideInfo) hideInfo();
        mBinding.control.text.setText(R.string.play_track_text);
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showPreview(Drawable preview) {
        if (Setting.getFlag() == 0 || isGone(mBinding.widget.preview)) return;
        mBinding.widget.preview.setVisibility(View.VISIBLE);
        mBinding.widget.preview.setImageDrawable(preview);
    }

    @Override
    protected void hidePreview() {
        mBinding.widget.preview.setVisibility(View.GONE);
        mBinding.widget.preview.setImageDrawable(null);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        App.post(mR3, Constant.INTERVAL_TRAFFIC);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback(long delayMillis) {
        App.post(mR2, delayMillis);
    }

    private void setArtwork(String url) {
        ImgUtil.load(url, R.drawable.radio, new com.bumptech.glide.request.target.CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable com.bumptech.glide.request.transition.Transition<? super Drawable> transition) {
                getExo().setDefaultArtwork(resource);
                getIjk().setDefaultArtwork(resource);
                showPreview(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable error) {
                getExo().setDefaultArtwork(error);
                getIjk().setDefaultArtwork(error);
                hidePreview();
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        });
    }

    private void getPart(String source) {
        OkHttp.newCall("https://api.yesapi.cn/?service=App.Scws.GetWords&app_key=CEE4B8A091578B252AC4C92FB4E893C3&text=" + URLEncoder.encode(source.trim())).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<String> items = Part.get(response.body().string());
                if (!items.contains(source)) items.add(0, source);
                App.post(() -> setPartAdapter(items), 1000);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                List<String> items = Arrays.asList(source);
                App.post(() -> setPartAdapter(items), 1000);
            }
        });
    }

    private void setPartAdapter(List<String> items) {
        mBinding.part.setVisibility(View.VISIBLE);
        mPartAdapter.setItems(items, null);
        setR2Callback(1000);
    }

    private void checkFlag(Vod item) {
        boolean empty = item.getVodFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            ErrorEvent.flag();
        } else {
            setFlagActivated(mHistory.getFlag());
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
    }

    private void checkHistory(Vod item) {
        mHistory = History.find(getHistoryKey());
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        if (Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) mHistory.delete();
        mBinding.control.opening.setText(mHistory.getOpening() == 0 ? getString(R.string.play_op) : mPlayers.stringToTime(mHistory.getOpening()));
        mBinding.control.ending.setText(mHistory.getEnding() == 0 ? getString(R.string.play_ed) : mPlayers.stringToTime(mHistory.getEnding()));
        mHistory.setVodPic(item.getVodPic());
        mPlayers.setPlayer(getPlayer());
        setScale(getScale());
        setPlayerView();
        setDecodeView();
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getVodName());
        history.findEpisode(item.getVodFlags());
        history.setSpeed(Setting.getPlaySpeed());
        return history;
    }

    private void checkKeep() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setSiteName(getSite().getName());
        keep.setVodPic(mBinding.video.getTag().toString());
        keep.setVodName(mBinding.name.getText().toString());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    @Override
    public void showChooser(TrackDialog dialog) {
        FileChooserDialog.create().player(mPlayers).trackDialog(dialog).show(this);
    }

    @Override
    protected void onPlayerReady() {
        mBinding.widget.size.setText(mPlayers.getSizeText());
        mBinding.display.size.setText(mPlayers.getSizeText());
    }

    @Override
    protected void checkEnded() {
        if (mBinding.control.loop.isActivated()) {
            onReset(true);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            checkNext();
        }
    }

    @Override
    protected void setTrackVisible(boolean visible) {
        mBinding.control.text.setVisibility(visible && (mPlayers.haveTrack(C.TRACK_TYPE_TEXT) || mPlayers.isExo()) ? View.VISIBLE : View.GONE);
        mBinding.control.audio.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.video.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    @Override
    protected String getVodName() {
        return mBinding.name.getText().toString();
    }

    @Override
    protected String getVodPic() {
        return mBinding.video.getTag().toString();
    }

    @Override
    public void onSubtitleClick() {
        App.post(this::hideControl, 200);
        SubtitleView subtitleView = mPlayers.isIjk() ? getIjk().getSubtitleView() : getExo().getSubtitleView();
        App.post(() -> SubtitleDialog.create().view(subtitleView).full(isFullscreen()).show(this), 200);
    }

    @Override
    protected void checkParse() {
        int position = getParsePosition();
        boolean last = position == mParseAdapter.size() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    @Override
    protected void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : getFlagPosition();
        if (position >= mFlagAdapter.size() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (mQuickAdapter.size() == 0) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    @Override
    protected void initSearch(String keyword, boolean auto) {
        mBinding.part.setTag(keyword);
        super.initSearch(keyword, auto);
    }

    @Override
    protected void nextParse(int position) {
        Parse parse = (Parse) mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        setParseActivated(parse);
    }

    @Override
    protected void nextFlag(int position) {
        Flag flag = (Flag) mFlagAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
        setFlagActivated(flag);
    }

    @Override
    protected void nextSite() {
        if (mQuickAdapter.size() == 0) return;
        Vod item = (Vod) mQuickAdapter.get(0);
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mQuickAdapter.removeItems(0, 1);
        mBroken.add(getId());
        setInitAuto(false);
        getDetail(item);
    }

    private void initParse() {
        if (mParseAdapter.size() == 0) return;
        VodConfig.get().setParse((Parse) mParseAdapter.get(0));
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
    }

    @Override
    protected String getSearchKeyword() {
        return Objects.toString(mBinding.part.getTag(), "");
    }

    @Override
    protected void onSearch(List<Vod> items) {
        mQuickAdapter.addAll(mQuickAdapter.size(), items);
        mBinding.quick.setVisibility(View.VISIBLE);
    }

    private void setSearch(Vod item) {
        setAutoMode(false);
        getDetail(item);
    }

    private void onPaused() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mBinding.widget.exoDuration.setText(mPlayers.getDurationTime());
        mBinding.widget.exoPosition.setText(mPlayers.getPositionTime(0));
        if (isFullscreen()) showInfoAndCenter();
        else hideInfoAndCenter();
        mPlayers.pause();
    }

    private void onPlay() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mPlayers.play();
        hideCenter();
    }

    @Override
    protected boolean isBackground() {
        return background;
    }

    public void setBackground(boolean background) {
        this.background = background;
    }

    public int getGroupSize() {
        return groupSize;
    }

    public void setGroupSize(int size) {
        groupSize = size;
    }

    private View getFocus1() {
        return mFocus1 == null ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2 == mBinding.control.opening || mFocus2 == mBinding.control.ending ? mBinding.control.next : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        hasKeyEvent = true;
        if (mBinding.progressLayout.isContent() && !isFullscreen() && KeyUtil.isBackKey(event) && Setting.getSmallWindowBackKey() == 1 && getCurrentFocus() != mBinding.video) {
            mFocus1 = mBinding.video;
            getFocus1().requestFocus();
            return true;
        }
        if (isFullscreen() && KeyUtil.isMenuKey(event) && Setting.getFullscreenMenuKey() == 0) onToggle();
        if (isFullscreen() && KeyUtil.isMenuKey(event) && Setting.getFullscreenMenuKey() == 1) onEpisodes();
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event)) return mKeyDown.onKeyDown(event);
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
        mBinding.widget.exoDuration.setText(mPlayers.getDurationTime());
        mBinding.widget.exoPosition.setText(mPlayers.getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.center.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekTo(int time) {
        mPlayers.seekTo(time);
        mKeyDown.resetTime();
        showProgress();
        onPlay();
    }

    @Override
    public void onSpeedUp() {
        if (!mPlayers.isPlaying() || !mPlayers.canAdjustSpeed()) return;
        mBinding.control.speed.setText(mPlayers.setSpeed(mPlayers.getSpeed() < 3 ? 3 : 5));
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.widget.speed.setVisibility(View.VISIBLE);
    }

    @Override
    public void onSpeedEnd() {
        mBinding.control.speed.setText(mPlayers.setSpeed(mHistory.getSpeed()));
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
    }

    @Override
    public void onKeyUp() {
        long current = mPlayers.getPosition();
        long half = mPlayers.getDuration() / 2;
        showInfo();
        showControl(current < half ? mBinding.control.opening : mBinding.control.ending);
    }

    @Override
    public void onKeyDown() {
        showInfo();
        showControl(getFocus2());
    }

    @Override
    public void onKeyCenter() {
        if (mPlayers.isPlaying()) {
            onPaused();
            hideControl(false);
        } else {
            onPlay();
            hideControl(true);
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
        if (resultCode != RESULT_OK) return;
        switch (requestCode) {
            case 1000:
                setResult(RESULT_OK);
                finish();
                break;
            case 1001:
                mPlayers.checkData(data);
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setBackground(false);
        mClock.start();
        onPlay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        setBackground(true);
        mPlayers.pause();
        mClock.stop();
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else if (isFullscreen()) {
            exitFullscreen();
        } else {
            stopSearch();
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected int getFlagPosition() {
        for (int i = 0; i < mFlagAdapter.size(); i++) if (((Flag) mFlagAdapter.get(i)).isActivated()) return i;
        return 0;
    }

    private int getEpisodePosition() {
        for (int i = 0; i < mEpisodeAdapter.size(); i++) if (((Episode) mEpisodeAdapter.get(i)).isActivated()) return i;
        return 0;
    }

    @Override
    protected int getParsePosition() {
        for (int i = 0; i < mParseAdapter.size(); i++) if (((Parse) mParseAdapter.get(i)).isActivated()) return i;
        return 0;
    }

    @Override
    protected Flag getFlag() {
        if (mFlagAdapter.size() == 0) return new Flag();
        return (Flag) mFlagAdapter.get(getFlagPosition());
    }

    @Override
    protected Episode getEpisode() {
        if (mEpisodeAdapter.size() == 0) return new Episode();
        return (Episode) mEpisodeAdapter.get(getEpisodePosition());
    }

    @Override
    protected Drawable getDefaultArtwork() {
        if (mPlayers.isExo()) return getExo().getDefaultArtwork();
        return getIjk().getDefaultArtwork();
    }
}
