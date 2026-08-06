package com.fongmi.android.tv.ui.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Page;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentVodBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.CollectActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.FilterPresenter;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Prefers;
import com.google.common.collect.Lists;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class VodFragment extends BaseFragment implements CustomScroller.Callback, VodPresenter.OnClickListener {

    private HashMap<String, String> mExtends;
    public FragmentVodBinding mBinding;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Filter> mFilters;
    private List<Page> mPages;
    private boolean mOpen;
    private Page mPage;

    public static VodFragment newInstance(String key, String typeId, Style style, HashMap<String, String> extend, boolean folder, boolean filter) {
        Bundle args = new Bundle();
        args.putString("key", key);
        args.putString("typeId", typeId);
        args.putBoolean("folder", folder);
        args.putBoolean("filter", filter);
        args.putParcelable("style", style);
        args.putSerializable("extend", extend);
        VodFragment fragment = new VodFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKey() {
        return getArguments().getString("key");
    }

    private String getTypeId() {
        return mPages.isEmpty() ? getArguments().getString("typeId") : getLastPage().getId();
    }

    private List<Filter> getFilter() {
        return Filter.arrayFrom(Prefers.getString("filter_" + getKey() + "_" + getTypeId()));
    }

    private HashMap<String, String> getExtend() {
        Serializable extend = getArguments().getSerializable("extend");
        return extend == null ? new HashMap<>() : (HashMap<String, String>) extend;
    }

    private boolean isFolder() {
        return getArguments().getBoolean("folder");
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private boolean isIndexs() {
        return getSite().isIndex();
    }

    private Page getLastPage() {
        return mPages.get(mPages.size() - 1);
    }

    private Style getStyle() {
        return isFolder() ? Style.list() : getSite().getStyle(mPages.isEmpty() ? getArguments().getParcelable("style") : getLastPage().getStyle());
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mPages = new ArrayList<>();
        mExtends = getExtend();
        mFilters = getFilter();
        mOpen = getArguments().getBoolean("filter");
        setRecyclerView();
        setViewModel();
        setFilters();
        if (mOpen) showFilter();
    }

    @Override
    protected void initData() {
        getVideo();
    }

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(Vod.class, new VodPresenter(this, Style.list()));
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(8, FocusHighlight.ZOOM_FACTOR_NONE, HorizontalGridView.FOCUS_SCROLL_ALIGNED), FilterPresenter.class);
        mBinding.recycler.addOnScrollListener(mScroller = new CustomScroller(this));
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setHeader(getActivity().findViewById(R.id.recycler));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(getActivity()).get(SiteViewModel.class);
        mViewModel.getFilter().observe(getViewLifecycleOwner(), typeId -> {
            if (typeId.equals(getTypeId())) toggleFilter(!mOpen);
        });
        mViewModel.getResult().observe(getViewLifecycleOwner(), result -> {
            if (!result.getTid().equals(getTypeId())) return;
            boolean first = mScroller.first();
            int size = result.getList().size();
            if (size > 0) addVideo(result);
            mScroller.endLoading(result);
            checkPosition(first);
            checkMore(size);
            hideProgress();
        });
    }

    private void setFilters() {
        for (Filter filter : mFilters) {
            if (mExtends.containsKey(filter.getKey())) {
                filter.setSelected(mExtends.get(filter.getKey()));
            }
        }
    }

    private void setClick(ArrayObjectAdapter adapter, String key, Value item) {
        for (int i = 0; i < adapter.size(); i++) ((Value) adapter.get(i)).setSelected(item);
        adapter.notifyArrayItemRangeChanged(0, adapter.size());
        if (item.isSelected()) mExtends.put(key, item.getV());
        else mExtends.remove(key);
        onRefresh();
    }

    private void getVideo() {
        mScroller.reset();
        getVideo(getTypeId(), "1", false);
    }

    private void getVideo(String typeId, String page, boolean refresh) {
        boolean first = "1".equals(page);
        if (first) mLast = null;
        if (first) showProgress();
        mScroller.setLoading(true);
        int filterSize = mOpen ? mFilters.size() : 0;
        boolean clear = first && mAdapter.size() > filterSize;
        if (clear) mAdapter.removeItems(filterSize, mAdapter.size() - filterSize);
        mViewModel.categoryContent(getKey(), typeId, page, true, mExtends, refresh);
    }

    private void addVideo(Result result) {
        Style style = result.getStyle(getStyle());
        if (style.isList()) mAdapter.addAll(mAdapter.size(), result.getList());
        else addGrid(result.getList(), style);
    }

    public boolean isFilterOpen() {
        return mOpen;
    }

    private void checkPosition(boolean first) {
        View focus = getActivity() != null ? getActivity().getCurrentFocus() : null;
        boolean sideMenuFocused = focus != null && focus.getId() == R.id.recycler;
        if (sideMenuFocused || mBinding.recycler.hasFocus() || mBinding.recycler.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
            android.util.Log.d("HomeFocus", "VodFragment.checkPosition() - BLOCKED: User is busy or focused on menu/content.");
            return; 
        }
        if (mPage != null && mPage.getPosition() > 0) mBinding.recycler.hideHeader();
        if (mPage != null && mPage.getPosition() < 1) mBinding.recycler.showHeader();
        if (mPage != null) {
            android.util.Log.d("HomeFocus", "VodFragment.checkPosition() - Restoring position: " + mPage.getPosition());
            mBinding.recycler.setSelectedPosition(mPage.getPosition());
        } else if (first && !mOpen) {
            android.util.Log.d("HomeFocus", "VodFragment.checkPosition() - First load, moving to top.");
            mBinding.recycler.moveToTop();
        }
        mPage = null;
    }

    private void checkMore(int count) {
        if (mScroller.isDisable() || count == 0 || mAdapter.size() >= 5) return;
        getVideo(getTypeId(), String.valueOf(mScroller.addPage()), false);
    }

    private boolean checkLastSize(List<Vod> items, Style style) {
        if (mLast == null || items.size() == 0) return false;
        int size = Product.getColumn(style) - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), new ArrayList<>(items.subList(0, size)));
        addGrid(new ArrayList<>(items.subList(size, items.size())), style);
        return true;
    }

    private void addGrid(List<Vod> items, Style style) {
        if (checkLastSize(items, style)) return;
        List<ListRow> rows = new ArrayList<>();
        for (List<Vod> part : Lists.partition(items, Product.getColumn(style))) {
            mLast = new ArrayObjectAdapter(new VodPresenter(this, style));
            mLast.setItems(part, null);
            rows.add(new ListRow(mLast));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private ListRow getRow(Filter filter) {
        FilterPresenter presenter = new FilterPresenter(filter.getKey());
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
        presenter.setOnClickListener((key, item) -> setClick(adapter, key, item));
        adapter.setItems(filter.getValue(), null);
        return new ListRow(adapter);
    }

    private void showProgress() {
        if (!mOpen) mBinding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void showFilter() {
        List<ListRow> rows = new ArrayList<>();
        for (Filter filter : mFilters) rows.add(getRow(filter));
        App.post(() -> mBinding.recycler.scrollToPosition(0), 48);
        mAdapter.addAll(0, rows);
        hideProgress();
    }

    private void hideFilter() {
        mAdapter.removeItems(0, mFilters.size());
    }

    public void toggleFilter(boolean open) {
        if (mOpen == open) return;
        if (open) showFilter();
        else hideFilter();
        mOpen = open;
    }

    private boolean isActivated(int index) {
        if (index < 0 || index >= mFilters.size()) return false;
        return mExtends.containsKey(mFilters.get(index).getKey());
    }

    public boolean isFilterFocused() {
        return mBinding.recycler.hasFocus() && mBinding.recycler.getSelectedPosition() < mFilters.size();
    }

    public boolean backFocusUp() {
        int filterSize = mOpen ? mFilters.size() : 0;
        int position = mBinding.recycler.getSelectedPosition();
        
        // 1. 如果焦點在影片區（position >= filterSize）
        if (position >= filterSize) {
            // 尋找最後一排有操作過的篩選器
            for (int i = filterSize - 1; i >= 0; i--) {
                if (isActivated(i)) {
                    mBinding.recycler.setSelectedPosition(i);
                    return true;
                }
            }
        } 
        // 2. 如果焦點在篩選區（position < filterSize）
        else {
            // 尋找上一排有操作過的篩選器
            for (int i = position - 1; i >= 0; i--) {
                if (isActivated(i)) {
                    mBinding.recycler.setSelectedPosition(i);
                    return true;
                }
            }
        }
        // 3. 沒找到任何操作過的篩選器，或已經在最上方，返回 false 交給 Activity 處理跳回分類選單
        return false;
    }

    public void scrollToTop() {
        if (mBinding == null) return;
        mBinding.recycler.setSelectedPosition(0);
        mBinding.recycler.showHeader();
    }

    public void focusFilter() {
        if (mBinding == null) return;
        mBinding.recycler.setSelectedPosition(0);
        mBinding.recycler.requestFocus();
    }

    public void onRefresh() {
        mScroller.reset();
        getVideo(getTypeId(), "1", true);
    }

    public boolean canBack() {
        return !mPages.isEmpty();
    }

    public void goBack() {
        if (mPages.size() == 1) mBinding.recycler.setMoveTop(true);
        mPages.remove(mPage = getLastPage());
        onRefresh();
    }

    public boolean goRoot() {
        if (mPages.isEmpty()) return false;
        mPages.clear();
        getVideo();
        return true;
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) {
            mViewModel.action(getKey(), item.getAction());
        } else if (item.isFolder()) {
            mPages.add(Page.get(item, mBinding.recycler.getSelectedPosition()));
            mBinding.recycler.setMoveTop(false);
            getVideo(item.getId(), "1", false);
        } else {
            if (isIndexs()) CollectActivity.start(getActivity(), item.getName());
            else if (!isFolder()) VideoActivity.start(getActivity(), getKey(), item.getId(), item.getName(), item.getPic());
            else VideoActivity.start(getActivity(), getKey(), item.getId(), item.getName(), item.getPic(), item.getName());
        }
    }

    @Override
    public boolean onLongClick(Vod item) {
        CollectActivity.start(getActivity(), item.getName());
        return true;
    }

    @Override
    public void onLoadMore(String page) {
        if (isIndexs()) return;
        if (Integer.parseInt(page) <= 1) return;
        mScroller.setLoading(true);
        getVideo(getTypeId(), page, false);
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (mBinding != null && !isVisibleToUser) mBinding.recycler.moveToTop();
    }
}
