package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import androidx.leanback.widget.VerticalGridView;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;
import androidx.viewpager.widget.ViewPager;

import com.android.cast.dlna.dmr.DLNARendererService;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
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
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigCallback;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomTitleView;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.MenuDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.fragment.HomeFragment;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.ui.presenter.TypePresenter;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Tbs;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Trans;
import com.permissionx.guolindev.PermissionX;



import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, TypePresenter.OnClickListener, ConfigCallback {

    public ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private HomeActivity.PageAdapter mPageAdapter;
    private SiteViewModel mViewModel;
    public Result mResult;
    private boolean loading;
    private boolean coolDown;
    private View mOldView;
    private boolean confirm;
    private Clock mClock;
    private View mFocus;
    private boolean updating;
    private int mScrollState = ViewPager.SCROLL_STATE_IDLE;

    private Site getHome() {
        return VodConfig.get().getHome();
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
    protected void initView() {
        DLNARendererService.Companion.start(this, R.drawable.ic_logo);
        mClock = Clock.create(mBinding.clock).format("MM/dd HH:mm:ss");
        Updater.get().release().start(this);
        Server.get().start();
        Tbs.init();
        setTitleView();
        setRecyclerView();
        setViewModel();
        setHomeType();
        setPager();
        initConfig();
        initRemote();
    }

    private void initRemote() {
        android.util.Log.e("HomeActivity", "initRemote: isTvBox = " + com.fongmi.android.tv.utils.Util.isTvBox());
        if (com.fongmi.android.tv.utils.Util.isTvBox()) return;
        View remote = getLayoutInflater().inflate(R.layout.view_virtual_remote, mBinding.root, false);
        mBinding.root.addView(remote);
        android.util.Log.e("HomeActivity", "initRemote: remote view added to root");
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
        android.util.Log.e("HomeActivity", "sendKey: " + keyCode);
        int direction = -1;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) direction = View.FOCUS_UP;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) direction = View.FOCUS_DOWN;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) direction = View.FOCUS_LEFT;
        else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) direction = View.FOCUS_RIGHT;

        View current = getCurrentFocus();
        if (current == null) {
            mBinding.recycler.requestFocus();
            current = mBinding.recycler;
        }

        if (direction != -1) {
            // 1. 使用 FocusFinder 尋找下一個邏輯焦點
            View next = FocusFinder.getInstance().findNextFocus((ViewGroup) mBinding.root, current, direction);

            // 2. 特殊情況處理：從類別列向下進入內容區，或從內容區向上回到類別列
            if (next == null || next == current || next == mBinding.recycler || next == mBinding.pager) {
                if (direction == View.FOCUS_DOWN && viewAncestor(current, mBinding.recycler)) {
                    VerticalGridView gridView = getRecyclerView();
                    if (gridView != null) {
                        gridView.requestFocus();
                        return;
                    }
                } else if (direction == View.FOCUS_UP && viewAncestor(current, mBinding.pager)) {
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

        // 3. OK, BACK 或 FocusFinder 失敗時的回退方案
        dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private VerticalGridView getRecyclerView() {
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
        mBinding.pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrollStateChanged(int state) {
                mScrollState = state;
            }

            @Override
            public void onPageSelected(int position) {
                android.util.Log.e("HomeActivity", "onPageSelected: " + position + ", currentRecycler: " + mBinding.recycler.getSelectedPosition() + ", updating: " + updating);
                if (updating) return;
                mBinding.recycler.setSelectedPosition(position);
            }
        });
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (updating || mScrollState != ViewPager.SCROLL_STATE_IDLE) return;
                android.util.Log.e("HomeActivity", "onChildViewHolderSelected position: " + position + ", currentPager: " + mBinding.pager.getCurrentItem() + ", updating: " + updating);
                onChildSelected(child);
            }
        });
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
                loadLive("file:/" + FileChooser.getPathFromUri(this, intent.getData()));
            } else {
                VideoActivity.push(this, intent.getData().toString());
            }
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

        // 不設選中監聽，不用處理高亮
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(
                mAdapter = new ArrayObjectAdapter(new TypePresenter(this))
        ));


    }

    private void setHomeUI() {
        if (Setting.getHomeUI() == 0) mBinding.recycler.setVisibility(View.GONE);
        else mBinding.recycler.setVisibility(View.VISIBLE);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.result.observe(this, result -> {
            setTypes(mResult = result);
        });
    }

    private List<Class> getTypes(Result result) {
        List<Class> items = new ArrayList<>();
        for (String cate : getHome().getCategories()) for (Class item : result.getTypes()) if (Trans.s2t(cate).equals(item.getTypeName())) items.add(item);
        return items;
    }

    private String getKey() {
        return getHome().getKey();
    }

    private List<Filter> getFilter(String typeId) {
        return Filter.arrayFrom(Prefers.getString("filter_" + getKey() + "_" + typeId));
    }

    private void setHomeType() {
        Class home = new Class();
        home.setTypeId("home");
        home.setTypeName(ResUtil.getString(R.string.home));
        mAdapter.add(home);
    }

    public void homeContent() {
        mResult = Result.empty();
        String title = getHome().getName();
        mBinding.title.setText(title.isEmpty() ? ResUtil.getString(R.string.app_name) : title);
        if (getHome().getKey().isEmpty()) return;
        mFocus = getCurrentFocus();
        getHomeFragment().mBinding.progressLayout.showProgress();
        mViewModel.homeContent();
    }

    public void setTypes(Result result) {
        int position = mBinding.recycler.getSelectedPosition();
        updating = true;
        android.util.Log.e("HomeActivity", "setTypes start, current position: " + position);
        result.setTypes(getTypes(result));
        for (Map.Entry<String, List<Filter>> entry : result.getFilters().entrySet()) Prefers.put("filter_" + getKey() + "_" + entry.getKey(), App.gson().toJson(entry.getValue()));
        for (Class item : result.getTypes()) item.setFilters(getFilter(item.getTypeId()));
        if (mAdapter.size() > 1) mAdapter.removeItems(1, mAdapter.size() - 1);
        if (result.getTypes().size() > 0) mAdapter.addAll(1, result.getTypes());
        setPager();
        if (mPageAdapter != null) {
            mPageAdapter.notifyDataSetChanged();
        }
        int targetPos = Math.min(position, mAdapter.size() - 1);
        android.util.Log.e("HomeActivity", "Restoring recycler position to: " + targetPos);
        mBinding.recycler.setSelectedPosition(targetPos);
        App.post(() -> {
            android.util.Log.e("HomeActivity", "Updating flag set to false");
            updating = false;
        }, 500);
        getHomeFragment().addVideo(result);
        getHomeFragment().mBinding.progressLayout.showContent();
        App.post(() -> setFocus(), 200);
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
            android.util.Log.e("HomeActivity", "mRunnable run, recycler pos: " + position + ", pager item: " + currentPagerItem);
            if (position != -1 && currentPagerItem != position) {
                android.util.Log.e("HomeActivity", "Setting pager item to: " + position);
                mBinding.pager.setCurrentItem(position);
            }
            if (position == 0) showToolBar();
            else hideToolBar();
        }
    };

    private void updateFilter(Class item) {
        if (item.getFilter() == null) return;
        getFragment().toggleFilter(item.toggleFilter());
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
        return (HomeFragment) mPageAdapter.instantiateItem(mBinding.pager, 0);
    }

    private VodFragment getFragment() {
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
        if (mBinding.pager.getCurrentItem() == 0) {
            SiteDialog.create(this).show();
        } else {
            updateFilter(item);
        }
    }

    @Override
    public void onRefresh(Class item) {
        if (mBinding.pager.getCurrentItem() == 0) mBinding.title.requestFocus();
        else getFragment().onRefresh();
    }

    @Override
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
        if (isLoading()) return;
        WallConfig.get().init();
        LiveConfig.get().init().load();
        VodConfig.get().init().load(getCallback(""), true);
        setLoading(true);
    }

    private Callback getCallback(String success) {
        return new Callback() {
            @Override
            public void success(String result) {
                Notify.show(result);
            }

            @Override
            public void success() {
                checkAction(getIntent());
                RefreshEvent.video();
                setLogo();
                if (!TextUtils.isEmpty(success)) Notify.show(success);
            }

            @Override
            public void error(String msg) {
                if (getHomeFragment().inited) getHomeFragment().mBinding.progressLayout.showContent();
                else App.post(() -> getHomeFragment().mBinding.progressLayout.showContent(), 1000);
                mResult = Result.empty();
                Notify.show(msg);
                setLoading(false);
            }
        };
    }

    private void load(Config config, String success) {
        switch (config.getType()) {
            case 0:
                getHomeFragment().mBinding.progressLayout.showProgress();
                VodConfig.load(config, getCallback(success));
                break;
        }
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
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
        SiteDialog.create(this).show();
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
    public boolean onItemLongClick(Class item) {
        if (mBinding.pager.getCurrentItem() != 0) return true;
        onRefresh();
        return true;
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
    public void onRefreshEvent(RefreshEvent event) {
        super.onRefreshEvent(event);
        switch (event.getType()) {
            case CONFIG:
                setLogo();
                break;
            case VIDEO:
                homeContent();
                break;
            case IMAGE:
                getHomeFragment().refreshRecommond();
                break;
            case HISTORY:
                getHomeFragment().getHistory();
                break;
            case SIZE:
                homeContent();
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
            VideoActivity.cast(this, event.getHistory().update(VodConfig.getCid()));
        } else {
            VodConfig.load(event.getConfig(), getCallback(event));
        }
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                RefreshEvent.history();
                RefreshEvent.config();
                RefreshEvent.video();
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    private void setLogo() {
        //Glide.with(App.get()).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).listener(getListener()).into(mBinding.logo);
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

    private void setFocus() {
        setLoading(false);
        if (!mBinding.title.isFocusable()) App.post(() -> mBinding.title.setFocusable(true), 500);
        if (mFocus != mBinding.title) {
            if (Setting.getHomeUI() == 0) getHomeFragment().mBinding.recycler.requestFocus();
            else mBinding.recycler.requestFocus();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        boolean isHomeFragment = mBinding.pager.getCurrentItem() == 0;
        if (isHomeFragment && KeyUtil.isMenuKey(event)) {
            if (Setting.getHomeMenuKey() == 0) MenuDialog.create(this).show();
            else if (Setting.getHomeMenuKey() == 1) SiteDialog.create(this).show();
            else if (Setting.getHomeMenuKey() == 2) HistoryDialog.create(this).type(0).show();
            else if (Setting.getHomeMenuKey() == 3) LiveActivity.start(this);
            else if (Setting.getHomeMenuKey() == 4) HistoryActivity.start(this);
            else if (Setting.getHomeMenuKey() == 5) SearchActivity.start(this);
            else if (Setting.getHomeMenuKey() == 6) PushActivity.start(this);
            else if (Setting.getHomeMenuKey() == 7) KeepActivity.start(this);
            else if (Setting.getHomeMenuKey() == 8) SettingActivity.start(this);
        }
        if (!isHomeFragment && KeyUtil.isMenuKey(event)) updateFilter((Class) mAdapter.get(mBinding.pager.getCurrentItem()));
        if (!isHomeFragment && KeyUtil.isBackKey(event) && event.isLongPress() && getFragment().goRoot()) setCoolDown();
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
        if (isVisible(mBinding.recycler) && mBinding.recycler.getSelectedPosition() != 0) {
            mBinding.recycler.scrollToPosition(0);
        } else if (mPageAdapter != null && getHomeFragment().inited && getHomeFragment().mBinding.progressLayout.isProgress()) {
            getHomeFragment().mBinding.progressLayout.showContent();
        } else if (mPageAdapter != null && getHomeFragment().inited && getHomeFragment().mPresenter != null && getHomeFragment().mPresenter.isDelete()) {
            getHomeFragment().setHistoryDelete(false);
        } else if (getHomeFragment().canBack()) {
            getHomeFragment().goBack();
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
        if (item.getFilter() != null && item.getFilter()) updateFilter(item);
        else if (getFragment().canBack()) getFragment().goBack();
        else if (!coolDown) super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        WallConfig.get().clear();
        LiveConfig.get().clear();
        VodConfig.get().clear();
        AppDatabase.backup();
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
            return VodFragment.newInstance(getHome().getKey(), type.getTypeId(), type.getStyle(), type.getExtend(false), "1".equals(type.getTypeFlag()));
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
