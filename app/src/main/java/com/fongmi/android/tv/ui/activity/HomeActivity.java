package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.VerticalGridView;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.fongmi.android.tv.bean.HistorySyncManager;
import com.fongmi.android.tv.service.DLNARendererService;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Button;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.bean.Func;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.MenuDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.fragment.HomeFragment;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.ui.presenter.FuncPresenter;
import com.fongmi.android.tv.ui.presenter.HistoryPresenter;
import com.fongmi.android.tv.ui.presenter.TypePresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Monitor;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Trans;
import com.permissionx.guolindev.PermissionX;
import com.permissionx.guolindev.callback.RequestCallback;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, TypePresenter.OnClickListener, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HistoryPresenter.OnClickListener {

    private static final Map<String, Result> mCache = new HashMap<>();
    public ActivityHomeBinding mBinding;
    private PageAdapter mPageAdapter;
    private ArrayObjectAdapter mAdapter;
    private SiteViewModel mViewModel;
    public Result mResult;
    private View mOldView;
    private View mFocus;
    private Clock mClock;
    private int mScrollState;
    private boolean updating;
    private boolean loading;
    private boolean confirm;
    private boolean coolDown;

    private Site getHome() {
        try {
            Site home = VodConfig.get().getHome();
            return home == null ? new Site() : home;
        } catch (Exception e) {
            return new Site();
        }
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        checkAction(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Monitor.start("HomeActivity_onCreate");
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView() {
        android.util.Log.d("TV_FATAL", "HomeActivity.initView() START");
        Monitor.start("HomeActivity_initView");
        setViewModel();
        initConfig();
        mResult = Result.empty();
        mClock = Clock.create(mBinding.clock);
        PermissionUtil.requestFile(this, (allGranted, grantedList, deniedList) -> { });
        DLNARendererService.start(this);
        Updater.get().release().start(this);
        setRecyclerView();

        App.execute(() -> Server.get().start());
        checkStoragePermission();
        setTitleView();
        setHomeType();
        setPager();
        App.post(this::initRemote, 500);
        android.util.Log.d("TV_FATAL", "HomeActivity.initView() END");
        Monitor.end("HomeActivity_initView");
        Monitor.end("HomeActivity_onCreate");
    }

    private void setTitle(Config config) {
        if (config == null || mBinding == null) return;
        String homeName = getHome() != null ? getHome().getName() : "";
        List<String> items = Arrays.asList(homeName, config.getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
    }

    private void setLogo(Config config) {
        if (config == null || mBinding == null) return;
        ImgUtil.logo(mBinding.logo, config.getLogo());
    }

    private void setTitle() {
        if (VodConfig.get() != null) setTitle(VodConfig.get().getConfig());
    }


    private void initRemote() {
        if (com.fongmi.android.tv.utils.Util.isLeanback()) return;
        View remote = getLayoutInflater().inflate(R.layout.view_virtual_remote, mBinding.root, false);
        mBinding.root.addView(remote);
        Log.e("HomeActivity", "initRemote: remote view added to root");
        View panel = remote.findViewById(R.id.remote_panel);
        View toggle = remote.findViewById(R.id.remote_toggle);
        toggle.setOnClickListener(v -> {
            boolean show = panel.getVisibility() == View.GONE;
            panel.setVisibility(show ? View.VISIBLE : View.GONE);
            toggle.setVisibility(show ? View.GONE : View.VISIBLE);
        });
        remote.findViewById(R.id.btn_up).setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_DPAD_UP));
        remote.findViewById(R.id.btn_down).setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN));
        remote.findViewById(R.id.btn_left).setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT));
        remote.findViewById(R.id.btn_right).setOnClickListener(v -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT));
        remote.findViewById(R.id.btn_ok).setOnClickListener(v -> {
            sendKey(KeyEvent.KEYCODE_DPAD_CENTER);
            panel.setVisibility(View.GONE);
            toggle.setVisibility(View.VISIBLE);
        });
        remote.findViewById(R.id.btn_back).setOnClickListener(v -> {
            sendKey(KeyEvent.KEYCODE_BACK);
            panel.setVisibility(View.GONE);
            toggle.setVisibility(View.VISIBLE);
        });
    }

    private void sendKey(int keyCode) {
        int dir = -1;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) dir = View.FOCUS_UP;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) dir = View.FOCUS_DOWN;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) dir = View.FOCUS_LEFT;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) dir = View.FOCUS_RIGHT;

        View current = getCurrentFocus();
        if (current == null) {
            mBinding.recycler.requestFocus();
            current = mBinding.recycler;
        }

        if (dir != -1) {
            View next = FocusFinder.getInstance().findNextFocus((ViewGroup) mBinding.root, current, dir);
            if (next == null || next == current || next == mBinding.recycler || next == mBinding.pager) {
                if (dir == View.FOCUS_DOWN && viewAncestor(current, mBinding.recycler)) {
                    VerticalGridView gridView = getRecyclerView();
                    if (gridView != null) {
                        gridView.requestFocus();
                        return;
                    }
                } else if (dir == View.FOCUS_UP && viewAncestor(current, mBinding.pager)) {
                    VerticalGridView gridView = getRecyclerView();
                    if (gridView != null && !gridView.canScrollVertically(-1)) {
                        mBinding.recycler.requestFocus();
                        return;
                    }
                }
            }

            if (next != null && next != current) {
                next.requestFocus();
                return;
            }
        }

        dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private VerticalGridView getRecyclerView() {
        if (mPageAdapter == null) return null;
        Fragment fragment = (Fragment) mPageAdapter.instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
        if (fragment instanceof HomeFragment) return ((HomeFragment) fragment).mBinding.recycler;
        if (fragment instanceof VodFragment) return ((VodFragment) fragment).mBinding.recycler;
        return null;
    }

    private boolean viewAncestor(View view, View target) {
        if (view == null) return false;
        if (view == target) return true;
        if (view.getParent() instanceof View) return viewAncestor((View) view.getParent(), target);
        return false;
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                onChildSelected(child);
                mBinding.toolbar.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
                HomeFragment fragment = getHomeFragment();
                if (fragment != null && fragment.mPresenter != null && fragment.mPresenter.isDelete()) fragment.setHistoryDelete(false);
            }
        });
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus != null) {
                android.util.Log.d("HomeFocus", ">>> FOCUS_CHANGED: " + newFocus.getClass().getSimpleName() + " [ID: " + getResName(newFocus) + "]");
            }
        });
    }

    private String getResName(View v) {
        if (v == null) return "null";
        StringBuilder sb = new StringBuilder();
        try {
            int id = v.getId();
            if (id == View.NO_ID) {
                sb.append("NO_ID");
            } else if (id < 0x7f000000) {
                sb.append("DYNAMIC_ID_").append(id);
            } else {
                sb.append(getResources().getResourceEntryName(id));
            }
            View current = v;
            ViewParent parent = current.getParent();
            while (parent instanceof View) {
                if (parent instanceof RecyclerView) {
                    int pos = ((RecyclerView) parent).getChildAdapterPosition(current);
                    sb.append(" [").append(((RecyclerView) parent).getClass().getSimpleName()).append(" Pos: ").append(pos).append("]");
                }
                current = (View) parent;
                parent = current.getParent();
            }
        } catch (Exception e) {
            sb.append("UNKNOWN");
        }
        return sb.toString();
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, (allGranted, grantedList, deniedList) -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) CollectActivity.start(this, keyword, true);
        }
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || (intent.getData() != null && UrlUtil.path(intent.getData()).endsWith(".m3u"))) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else if (intent.getData() != null) {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    private void setTitleView() {
        mBinding.homeSiteLock.setVisibility(Setting.isHomeSiteLock() ? View.VISIBLE : View.GONE);
        if (Setting.getHomeUI() == 0) {
            mBinding.title.setTextSize(24);
            mBinding.clock.setTextSize(24);
        } else {
            mBinding.title.setTextSize(20);
            mBinding.clock.setTextSize(20);
        }
    }

    private void setRecyclerView() {
        setHomeUI();
        mBinding.recycler.setHorizontalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(new TypePresenter(this))));
    }

    private void setHomeUI() {
        if (Setting.getHomeUI() == 0) mBinding.recycler.setVisibility(View.GONE);
        else mBinding.recycler.setVisibility(View.VISIBLE);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            if (result != null && (!result.getTypes().isEmpty() || result.getList().size() > 0)) setTypes(result);
        });
    }

    private List<Class> getTypes(Result result) {
        List<String> categories = getHome().getCategories();
        if (categories.isEmpty() || (categories.size() == 1 && categories.get(0).trim().isEmpty())) return result.getTypes();
        List<Class> items = new ArrayList<>();
        for (String cate : categories) {
            if (cate.trim().isEmpty()) continue;
            for (Class item : result.getTypes()) {
                if (Trans.s2t(cate.trim()).equalsIgnoreCase(item.getTypeName().trim())) {
                    items.add(item);
                }
            }
        }
        android.util.Log.d("HomeActivity", "getTypes: input=" + result.getTypes().size() + " filtered=" + items.size());
        return items.isEmpty() ? result.getTypes() : items;
    }

    private String getKey() {
        return getHome().getKey();
    }

    private List<Filter> getFilter(String typeId) {
        return Filter.arrayFrom(Prefers.getString("filter_" + getKey() + "_" + typeId));
    }

    private void setHomeType() {
        for (int i = 0; i < mAdapter.size(); i++) if (((Class) mAdapter.get(i)).getTypeId().equals("home")) return;
        Class home = new Class();
        home.setTypeId("home");
        home.setTypeName(ResUtil.getString(R.string.home));
        mAdapter.add(home);
    }

    public void homeContent() {
        if (mBinding == null || getHome() == null) return;
        if (!getKey().equals(mResult.getKey())) mResult = Result.empty();
        String title = getHome().getName();
        mBinding.title.setText(title.isEmpty() ? ResUtil.getString(R.string.app_name) : title);
        if (getHome().getKey().isEmpty()) return;
        Result cached = mCache.get(getKey());
        if (cached != null) {
            setTypes(mResult = cached);
            return;
        }
        mFocus = getCurrentFocus();
        HomeFragment fragment = getHomeFragment();
        if (fragment != null && fragment.mBinding != null && !fragment.mBinding.progressLayout.isContent()) fragment.mBinding.progressLayout.showProgress();
        Notify.showTop(this, ResUtil.getString(R.string.home_loading, title));
        if (mViewModel != null) mViewModel.homeContent();
    }

    private int getChildPos(View v) {
        View current = v;
        ViewParent parent = current.getParent();
        while (parent instanceof View) {
            if (parent instanceof RecyclerView) {
                return ((RecyclerView) parent).getChildAdapterPosition(current);
            }
            current = (View) parent;
            parent = current.getParent();
        }
        return 0;
    }

    private RecyclerView findInnerRecycler(View view) {
        if (view instanceof RecyclerView && view != mBinding.recycler && view != mBinding.pager) return (RecyclerView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                RecyclerView recycler = findInnerRecycler(group.getChildAt(i));
                if (recycler != null) return recycler;
            }
        }
        return null;
    }

    public void setTypes(Result result) {
        if (result.getTypes().isEmpty() && result.getList().isEmpty()) {
            android.util.Log.w("HomeActivity", "setTypes() ABORTED - Result is EMPTY (likely a spider error)");
            return;
        }

        if (result.getKey().isEmpty()) result.setKey(getKey());

        List<Class> types = new ArrayList<>(getTypes(result));
        types.removeIf(item -> item.getTypeName().equals(ResUtil.getString(R.string.home)));

        View current = getCurrentFocus();
        int sideMenuPos = Math.max(0, mBinding.recycler.getSelectedPosition());
        int pagerPos = Math.max(0, mBinding.pager.getCurrentItem());
        int fragmentRowPos = getRecyclerView() != null ? Math.max(0, getRecyclerView().getSelectedPosition()) : 0;
        int itemPos = Math.max(0, getChildPos(current));
        boolean recyclerFocused = viewAncestor(current, mBinding.recycler);
        boolean pagerFocused = viewAncestor(current, mBinding.pager);

        android.util.Log.d("HomeFocus", "setTypes() START - Current: " + getResName(current) + " [SidePos: " + sideMenuPos + ", Pager: " + pagerPos + ", Row: " + fragmentRowPos + ", Item: " + itemPos + ", PagerFocused: " + pagerFocused + "]");

        boolean siteChanged = !getKey().equals(mResult.getKey());
        boolean listChanged = !result.isSameList(mResult);
        boolean typesChanged = false;

        if (!types.isEmpty()) {
            typesChanged = true;
            if (mAdapter.size() == types.size() + 1) {
                boolean same = true;
                for (int i = 0; i < types.size(); i++) {
                    Class oldType = (Class) mAdapter.get(i + 1);
                    Class newType = types.get(i);
                    if (!oldType.isSameItem(newType) || !oldType.isSameContent(newType)) {
                        same = false;
                        break;
                    }
                }
                if (same) typesChanged = false;
            }
        } else if (mAdapter.size() > 1 && siteChanged) {
            typesChanged = true;
        } else {
            android.util.Log.d("HomeActivity", "setTypes() - Result has NO types, skipping category update");
        }

        if (typesChanged) {
            updating = true;
            for (Map.Entry<String, List<Filter>> entry : result.getFilters().entrySet()) Prefers.put("filter_" + getKey() + "_" + entry.getKey(), App.gson().toJson(entry.getValue()));
            for (Class item : types) item.setFilters(getFilter(item.getTypeId()));
            if (mAdapter.size() > 1) mAdapter.removeItems(1, mAdapter.size() - 1);
            if (!types.isEmpty()) mAdapter.addAll(1, types);
            setPager();
            if (mPageAdapter != null) mPageAdapter.notifyDataSetChanged();
            mBinding.recycler.setSelectedPosition(sideMenuPos);
            mBinding.pager.setCurrentItem(pagerPos, false);
            App.post(() -> updating = false, 500);
        }

        HomeFragment homeFragment = getHomeFragment();
        if (homeFragment != null && listChanged) {
            homeFragment.addVideo(result);
            if (homeFragment.mBinding != null) homeFragment.mBinding.progressLayout.showContent();
        }

        if (result.getTypes().isEmpty() && !mResult.getTypes().isEmpty() && !siteChanged) {
            result.setTypes(mResult.getTypes());
        }

        mResult = result;
        mCache.put(getKey(), mResult);
        if (!typesChanged && !listChanged) return;

        if (recyclerFocused) {
            App.post(() -> {
                if (isFinishing() || mBinding == null) return;
                android.util.Log.d("HomeFocus", "Restoring Side Menu focus to Pos: " + sideMenuPos);
                mBinding.recycler.setSelectedPosition(sideMenuPos);
                mBinding.recycler.post(() -> {
                    if (isFinishing() || mBinding == null) return;
                    mBinding.recycler.requestFocus();
                });
            }, 400);
        } else if (pagerFocused) {
            App.post(() -> {
                if (isFinishing() || mBinding == null) return;
                View focus = getCurrentFocus();
                if (focus != null && focus != mBinding.recycler && viewAncestor(focus, mBinding.recycler)) {
                    int currentSidePos = mBinding.recycler.getSelectedPosition();
                    if (currentSidePos != sideMenuPos) return;
                }
                VerticalGridView gridView = getRecyclerView();
                if (gridView != null) {
                    gridView.setSelectedPosition(fragmentRowPos);
                    gridView.post(() -> {
                        if (isFinishing() || mBinding == null) return;
                        RecyclerView.ViewHolder holder = gridView.findViewHolderForAdapterPosition(fragmentRowPos);
                        if (holder != null) {
                            RecyclerView inner = findInnerRecycler(holder.itemView);
                            if (inner != null) {
                                if (inner instanceof HorizontalGridView) ((HorizontalGridView) inner).setSelectedPosition(itemPos);
                                else inner.scrollToPosition(itemPos);
                                inner.post(() -> {
                                    if (isFinishing()) return;
                                    inner.requestFocus();
                                });
                            } else {
                                holder.itemView.requestFocus();
                            }
                        }
                    });
                }
            }, 800);
        }
else {
            App.post(this::setFocus, 1000);
        }
        mResult = result;
    }


    private void setPager() {
        if (mBinding.pager.getAdapter() != null) return;
        mBinding.pager.setAdapter(mPageAdapter = new HomeActivity.PageAdapter(getSupportFragmentManager()));
        mBinding.pager.setOffscreenPageLimit(10);
        mBinding.pager.setNoScrollItem(0);
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child) {
        if (updating) return;
        if (mOldView != null) mOldView.setActivated(false);
        if (child == null) return;
        mOldView = child.itemView;
        mOldView.setActivated(true);
        App.post(mRunnable, 100);
    }

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if (updating || mScrollState != ViewPager.SCROLL_STATE_IDLE) return;
            int position = mBinding.recycler.getSelectedPosition();
            int currentPagerItem = mBinding.pager.getCurrentItem();
            if (position != -1 && currentPagerItem != position) mBinding.pager.setCurrentItem(position);
            if (position == 0) showToolBar();
            else hideToolBar();
        }
    };

    private void updateFilter(Class item) {
        mViewModel.setFilter(item.toggleFilter());
        mAdapter.notifyArrayItemRangeChanged(1, mAdapter.size() - 1);
    }

    public void hideToolBar() {
        mBinding.toolbar.setVisibility(View.GONE);
        if (mBinding.recycler.getVisibility() == View.VISIBLE) mBinding.blank.setVisibility(View.VISIBLE);
        else mBinding.blank.setVisibility(View.GONE);
    }

    public void showToolBar() {
        mBinding.toolbar.setVisibility(View.VISIBLE);
        mBinding.blank.setVisibility(View.GONE);
    }

    private HomeFragment getHomeFragment() {
        if (mPageAdapter == null) return null;
        return (HomeFragment) mPageAdapter.instantiateItem(mBinding.pager, 0);
    }

    private VodFragment getFragment() {
        if (mPageAdapter == null) return null;
        return (VodFragment) mPageAdapter.instantiateItem(mBinding.pager, mBinding.pager.getCurrentItem());
    }

    private void setCoolDown() {
        App.post(() -> coolDown = false, 2000);
        coolDown = true;
    }

    private boolean hasSettingButton() {
        return Setting.getHomeButtons(Button.getDefaultButtons()).contains("6");
    }

    @Override
    public void onItemClick(Class item) {
        if (item.getTypeId().equals("home")) {
            SiteDialog.create(this).show(this);
        } else {
            updateFilter(item);
        }
    }

    @Override
    public void onRefresh(Class item) {
        if (mBinding.pager.getCurrentItem() == 0) mBinding.title.requestFocus();
        else {
            VodFragment fragment = getFragment();
            if (fragment != null) fragment.onRefresh();
        }
    }

    @Override
    public boolean onItemLongClick(Class item) {
        if (!item.getTypeId().equals("home")) return true;
        onRefresh();
        return true;
    }

    @Override
    public void onItemClick(Vod item) {
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public void onItemClick(Func item) {
        HomeFragment fragment = getHomeFragment();
        if (fragment != null) fragment.onItemClick(item);
    }

    @Override
    public void onItemClick(History item) {
        HomeFragment fragment = getHomeFragment();
        if (fragment != null) fragment.onItemClick(item);
    }

    @Override
    public void onItemDelete(History item) {
        HomeFragment fragment = getHomeFragment();
        if (fragment != null) fragment.onItemDelete(item);
    }

    @Override
    public boolean onLongClick() {
        HomeFragment fragment = getHomeFragment();
        return fragment != null && fragment.onLongClick();
    }

    public void setConfig(Config config) {
        setConfig(config, "");
    }

    private void setConfig(Config config, String success) {
        if (config.getUrl().startsWith("file") && !PermissionX.isGranted(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            PermissionX.init(this).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request((allGranted, grantedList, deniedList) -> load(config, success));
        } else {
            load(config, success);
        }
    }

    public void initConfig() {
        String homeName = getHome().getName();
        Notify.showTop(this, ResUtil.getString(R.string.home_loading, homeName.isEmpty() ? ResUtil.getString(R.string.app_name) : homeName));
        android.util.Log.d("TV_FATAL", "HomeActivity.initConfig() START");
        App.execute(() -> {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
            try {
                android.util.Log.d("TV_FATAL", "Background thread started");
                Config vod = Config.vod();
                Config live = Config.live();
                if (vod != null && !vod.getJson().isEmpty()) {
                    android.util.Log.d("TV_FATAL", "initConfig [CACHE]: Loading local JSON cache...");
                    VodConfig.get().config(vod).cache();
                    android.util.Log.d("TV_FATAL", "initConfig [CACHE]: Loaded, isLoaded=" + VodConfig.get().isLoaded());
                    if (VodConfig.get().isLoaded()) {
                        App.post(() -> {
                            showContent();
                            RefreshEvent.history();
                            RefreshEvent.home();
                        });
                    }
                }
                App.post(() -> {
                    android.util.Log.d("TV_FATAL", "Posting to main thread for load()");
                    Monitor.start("Config_Load");
                    if (vod != null && !vod.isEmpty()) {
                        VodConfig.get().config(vod).load(getCallback());
                    } else {
                        android.util.Log.d("TV_FATAL", "Vod config is empty, skip load");
                        showContent();
                    }
                    if (live != null && !live.isEmpty() && (vod == null || !live.getUrl().equals(vod.getUrl()))) {
                        LiveConfig.get().config(live).load();
                    }
                });
            } catch (Throwable e) {
                android.util.Log.e("TV_FATAL", "initConfig Error: " + e.getMessage());
                e.printStackTrace();
                App.post(this::showContent);
            }
        });
    }

    private Callback getCallback() {
        return getCallback("");
    }

    private Callback getCallback(String success) {
        return new Callback() {
            @Override
            public void success() {
                Config config = VodConfig.get().getConfig();
                int siteCount = VodConfig.get().getSites() != null ? VodConfig.get().getSites().size() : 0;
                android.util.Log.d("TV_FATAL", "VodConfig load SUCCESS, Site count: " + siteCount);
                Monitor.end("Config_Load");
                if (!TextUtils.isEmpty(success)) Notify.show(success);
                Task.execute(HistorySyncManager::setup);
                setTitle(config);
                setLogo(config);
                showContent();
                App.post(() -> {
                    RefreshEvent.history();
                    RefreshEvent.home();
                }, 500);
            }

            @Override
            public void error(String msg) {
                android.util.Log.e("TV_DEBUG", "HomeActivity Callback ERROR: " + msg);
                Monitor.end("Config_Load");
                Notify.show(msg);
                showContent();
            }
        };
    }

    private void showContent() {
        android.util.Log.d("TV_FATAL", "HomeActivity.showContent() START");
        checkAction(getIntent());
        setFocus();
        RefreshEvent.config();
        Monitor.log("HomeActivity_ContentShown");
    }

    private void load(Config config, String success) {
        if (config.getType() == 0) {
            HomeFragment fragment = getHomeFragment();
            if (fragment != null && fragment.mBinding != null) fragment.mBinding.progressLayout.showProgress();
            VodConfig.load(config, getCallback(success));
        }
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, Config.LIVE), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        setLoading(false);
        mBinding.title.setSelected(true);
        App.post(() -> mBinding.title.setFocusable(true), 500);
        View current = getCurrentFocus();
        android.util.Log.d("HomeFocus", "setFocus() called - currentFocus: " + current + " mFocus (old): " + mFocus);
        if (current != null && (viewAncestor(current, mBinding.pager) || viewAncestor(current, mBinding.recycler) || viewAncestor(current, mBinding.toolbar) || current == mBinding.title)) {
            android.util.Log.d("HomeFocus", "setFocus() - SKIPPED: User is already interacting with " + current);
            return;
        }
        if (viewAncestor(mFocus, mBinding.recycler)) {
            android.util.Log.d("HomeFocus", "setFocus() - Re-focusing Recycler");
            mBinding.recycler.requestFocus();
        } else if (viewAncestor(mFocus, mBinding.pager)) {
            android.util.Log.d("HomeFocus", "setFocus() - Re-focusing Pager");
            HomeFragment fragment = getHomeFragment();
            if (fragment != null && fragment.mBinding != null) fragment.mBinding.recycler.requestFocus();
        } else if (mFocus == mBinding.title) {
            android.util.Log.d("HomeFocus", "setFocus() - Re-focusing Title");
            mBinding.title.requestFocus();
        } else {
            android.util.Log.d("HomeFocus", "setFocus() - Defaulting focus based on Setting UI: " + Setting.getHomeUI());
            HomeFragment fragment = getHomeFragment();
            if (Setting.getHomeUI() == 0 && fragment != null && fragment.mBinding != null) fragment.mBinding.recycler.requestFocus();
            else mBinding.recycler.requestFocus();
        }
    }

    private void setConfirm() {
        confirm = true;
        Notify.show(R.string.app_exit);
        App.post(() -> confirm = false, 5000);
    }


    @Override
    public void showDialog() {
        if (!hasSettingButton()) {
            MenuDialog.create(this).show();
            return;
        }
        if (Setting.isHomeSiteLock()) return;
        SiteDialog.create(this).show(this);
    }

    @Override
    public void onRefresh() {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                Config config = VodConfig.get().getConfig().json("").save();
                if (!config.isEmpty()) setConfig(config, ResUtil.getString(R.string.config_refreshed));
            }
        });
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
        homeContent();
    }

    @Override
    public void onChanged() {
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.history();
                RefreshEvent.home();
                setLogo();
                // 如果目前是空站點，或者是因為同步問題導致沒分類，才嘗試刷新
                if (mAdapter.size() == 0 || getHome().getApi().isEmpty()) {
                    setHomeType();
                    homeContent();
                }
                break;
            case WALL:
                RefreshEvent.wall();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        super.onRefreshEvent(event);
        if (mBinding == null || mAdapter == null) return;
        switch (event.getType()) {
            case HOME:
                homeContent();
                break;
            case HISTORY:
                HomeFragment fragment = getHomeFragment();
                if (fragment != null) fragment.getHistory();
                break;
            case SIZE:
                homeContent();
                break;
            default:
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.getType()) {
            case SEARCH:
                CollectActivity.start(this, event.getText(), true);
                break;
            case PUSH:
                VideoActivity.push(this, event.getText());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.getConfig())) {
            VideoActivity.cast(this, event.getHistory().cid(VodConfig.getCid()));
        } else {
            VodConfig.load(event.getConfig(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                RefreshEvent.history();
                RefreshEvent.home();
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    public SiteViewModel getViewModel() {
        return mViewModel;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    private void setLogo() {
        ImgUtil.logo(mBinding.logo);
    }

    private RequestListener<Drawable> getListener() {
        return new RequestListener<Drawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                mBinding.logo.setVisibility(View.GONE);
                return false;
            }

            @Override
            public boolean onResourceReady(@NonNull Drawable resource, @NonNull Object model, Target<Drawable> target, @NonNull DataSource dataSource, boolean isFirstResource) {
                mBinding.logo.setVisibility(View.VISIBLE);
                return false;
            }
        };
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mFocus = getCurrentFocus();
            android.util.Log.d("HomeFocus", "KEY_DOWN: " + KeyEvent.keyCodeToString(event.getKeyCode()) + " | currentFocus: " + (mFocus == null ? "null" : mFocus.getClass().getSimpleName() + " [" + getResName(mFocus) + "]"));
        }
        boolean isHomeFragment = mBinding.pager.getCurrentItem() == 0;
        if (isHomeFragment && KeyUtil.isMenuKey(event)) {
            if (Setting.getHomeMenuKey() == 0) MenuDialog.create(this).show();
            else if (Setting.getHomeMenuKey() == 1) SiteDialog.create(this).show(this);
            else if (Setting.getHomeMenuKey() == 2) HistoryDialog.create().vod().show(this);
            else if (Setting.getHomeMenuKey() == 3) LiveActivity.start(this);
            else if (Setting.getHomeMenuKey() == 4) HistoryActivity.start(this);
            else if (Setting.getHomeMenuKey() == 5) SearchActivity.start(this);
            else if (Setting.getHomeMenuKey() == 6) PushActivity.start(this);
            else if (Setting.getHomeMenuKey() == 7) KeepActivity.start(this);
            else if (Setting.getHomeMenuKey() == 8) SettingActivity.start(this);
        }
        if (!isHomeFragment && KeyUtil.isMenuKey(event)) updateFilter((Class) mAdapter.get(mBinding.pager.getCurrentItem()));
        if (!isHomeFragment && KeyUtil.isBackKey(event) && event.isLongPress()) {
            VodFragment fragment = getFragment();
            if (fragment != null && fragment.goRoot()) setCoolDown();
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
        setTitleView();
        setHomeUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && isTV()) com.fongmi.android.tv.utils.Util.hideSystemUI(this);
        if (hasFocus) Monitor.log("HomeActivity_WindowFocused");
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }

    @Override
    protected boolean handleBack() {
        return true;
    }

    @Override
    protected void onBackPress() {
        HomeFragment fragment = getHomeFragment();
        if (isVisible(mBinding.recycler) && mBinding.recycler.getSelectedPosition() != 0) {
            mBinding.recycler.scrollToPosition(0);
        } else if (fragment != null && fragment.inited && fragment.mBinding.progressLayout.isProgress()) {
            fragment.mBinding.progressLayout.showContent();
        } else if (fragment != null && fragment.inited && fragment.mPresenter != null && fragment.mPresenter.isDelete()) {
            fragment.setHistoryDelete(false);
        } else if (fragment != null && fragment.canBack()) {
            fragment.goBack();
        } else if (!confirm) {
            setConfirm();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        boolean isHomeFragment = mBinding.pager.getCurrentItem() == 0;
        if (isHomeFragment) {
            super.onBackPressed();
            return;
        }
        Class item = (Class) mAdapter.get(mBinding.pager.getCurrentItem());
        if (item.getFilter()) updateFilter(item);
        else {
            VodFragment fragment = getFragment();
            if (fragment != null && fragment.canBack()) fragment.goBack();
            else if (!coolDown) super.onBackPressed();
        }
    }

    private void checkStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!android.os.Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception e) {
                    try {
                        Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivity(intent);
                    } catch (Exception ignored) {
                    }
                }
            }
        } else {
            PermissionX.init(this)
                    .permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .request((allGranted, grantedList, deniedList) -> {
                        // handle results
                    });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LiveConfig.get().clear();
        VodConfig.get().clear();
        if (Setting.getBackupMode() == 0) AppDatabase.backup();
        OkHttp.get().clear();
        Server.get().stop();
        Source.get().exit();
    }

    class PageAdapter extends FragmentStatePagerAdapter {
        public PageAdapter(@NonNull FragmentManager fm) {
            super(fm);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            if (position == 0) return new HomeFragment();
            Class type = (Class) mAdapter.get(position);
            return VodFragment.newInstance(getHome().getKey(), type.getTypeId(), type.getStyle(), new HashMap<>(), "1".equals(type.getTypeFlag()));
        }

        @Override
        public int getCount() {
            return mAdapter.size();
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        }
    }
}
