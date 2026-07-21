package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.text.Editable;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteBinding;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private SiteCallback callback;
    private DialogSiteBinding binding;
    private SiteAdapter adapter;
    private boolean search;
    private boolean change;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public static SiteDialog create(FragmentActivity activity) {
        return new SiteDialog();
    }

    public static SiteDialog create(Fragment fragment) {
        return new SiteDialog();
    }

    public SiteDialog search() {
        this.search = true;
        return this;
    }

    public SiteDialog change() {
        this.change = true;
        return this;
    }

    public SiteDialog all() {
        this.search = true;
        this.change = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    private boolean isFull() {
        return getParentFragment() == null;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        callback = isFull() ? (SiteCallback) context : (SiteCallback) getParentFragment();
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        this.adapter = new SiteAdapter(this);
        this.adapter.search(search);
        this.adapter.change(change);
        setRecyclerView();
        setSearchView();
    }

    @Override
    protected void initEvent() {
        binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) searchSite();
            return true;
        });
        binding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable s) {
                searchSite();
            }
        });
        binding.search.setOnClickListener(v -> searchSite());
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(VodConfig.getHomeIndex()));
    }

    private void setSearchView() {
        if (adapter.getItemCount() < 10 || !Setting.isSiteSearch()) binding.searchInput.setVisibility(View.GONE);
    }

    private void searchSite() {
        String keyword = binding.keyword.getText().toString().trim();
        adapter.keyword(keyword);
    }

    @Override
    public void onTextClick(Site item) {
        if (callback == null) return;
        callback.setSite(item);
        dismiss();
    }

    @Override
    public void onSearchClick(int position, Site item) {
        item.setSearchable(!item.isSearchable()).save();
        adapter.notifyItemChanged(position);
        callback.onChanged();
    }

    @Override
    public void onChangeClick(int position, Site item) {
        item.setChangeable(!item.isChangeable()).save();
        adapter.notifyItemChanged(position);
    }

    @Override
    public boolean onTextLongClick(Site item) {
        if (item.getKey().isEmpty()) return true;
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(ResUtil.getString(item.isBlacklist() ? R.string.site_blacklist_remove_confirm : R.string.site_blacklist_confirm, item.getName()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    if (item.isBlacklist()) item.resetFailures();
                    else item.setBlacklist();
                    adapter.notifyDataSetChanged();
                }).show();
        return true;
    }

    @Override
    public boolean onSearchLongClick(Site item) {
        boolean result = !item.isSearchable();
        for (Site site : VodConfig.get().getSites()) site.setSearchable(result).save();
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        callback.onChanged();
        return true;
    }

    @Override
    public boolean onChangeLongClick(Site item) {
        boolean result = !item.isChangeable();
        for (Site site : VodConfig.get().getSites()) site.setChangeable(result).save();
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        return true;
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else {
            if (ResUtil.isLand(requireContext())) setWidth(0.5f);
            if (getDialog() != null && getDialog().getWindow() != null) {
                getDialog().getWindow().setDimAmount(0);
            }
        }
    }
}
