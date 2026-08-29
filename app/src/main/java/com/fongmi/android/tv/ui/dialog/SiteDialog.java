package com.fongmi.android.tv.ui.dialog;

import android.view.View;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteBinding;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.DescHelper;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private RecyclerView.ItemDecoration decoration;
    private DialogSiteBinding binding;
    private SiteCallback callback;
    private SiteAdapter adapter;
    private int type;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public static SiteDialog create(FragmentActivity activity) {
        return new SiteDialog();
    }

    public SiteDialog search() {
        type = 1;
        return this;
    }

    public SiteDialog action() {
        type = 3; // Use a special type for action to show binding.action in initView
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    private DescHelper descHelper;

    @Override
    protected void initView() {
        this.callback = (SiteCallback) getActivity();
        this.descHelper = DescHelper.create(binding.descLayout.descCard, binding.descLayout.desc);
        this.adapter = new SiteAdapter(this);
        this.adapter.setDescHelper(descHelper);
        if (type == 3) {
            binding.action.setVisibility(View.VISIBLE);
            setType(0);
        } else {
            setType(type);
        }
        setRecyclerView();
        setMode();
        initDesc();
    }

    private void initDesc() {
        descHelper.put(R.id.search, R.string.desc_site_search)
                .put(R.id.change, R.string.desc_site_change)
                .put(R.id.select, R.string.desc_site_select)
                .put(R.id.cancel, R.string.desc_site_cancel)
                .put(R.id.mode, R.string.desc_site_mode)
                .bind(binding.getRoot());
    }

    @Override
    protected void initEvent() {
        binding.mode.setOnClickListener(this::setMode);
        binding.select.setOnClickListener(v -> adapter.selectAll());
        binding.cancel.setOnClickListener(v -> adapter.cancelAll());
        binding.search.setOnClickListener(v -> setType(v.isActivated() ? 0 : 1));
        binding.change.setOnClickListener(v -> setType(v.isActivated() ? 0 : 2));
    }

    private boolean list() {
        return Setting.getSiteMode() == 0 || adapter.getItemCount() < 10;
    }

    private int getCount() {
        return list() ? 1 : Math.max(1, Math.min((int) (Math.ceil(adapter.getItemCount() / 10.0f)), 3));
    }

    private int getIcon() {
        return list() ? R.drawable.ic_site_grid : R.drawable.ic_site_list;
    }

    private float getWidth() {
        return 0.4f + (getCount() - 1) * 0.2f;
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        if (decoration != null) binding.recycler.removeItemDecoration(decoration);
        binding.recycler.addItemDecoration(decoration = new SpaceItemDecoration(getCount(), 16));
        binding.recycler.setLayoutManager(new GridLayoutManager(getContext(), getCount()));
        if (!binding.mode.hasFocus()) binding.recycler.post(() -> binding.recycler.scrollToPosition(VodConfig.getHomeIndex()));
    }

    private void setMode() {
        if (adapter.getItemCount() < 20) Setting.putSiteMode(0);
        binding.mode.setEnabled(adapter.getItemCount() >= 20);
        binding.mode.setImageResource(getIcon());
    }

    private void setType(int type) {
        binding.search.setActivated(type == 1);
        binding.change.setActivated(type == 2);
        binding.select.setClickable(type > 0);
        binding.cancel.setClickable(type > 0);
        adapter.setType(this.type = type);
    }

    private void setMode(View view) {
        Setting.putSiteMode(Math.abs(Setting.getSiteMode() - 1));
        setRecyclerView();
        setMode();
        updateWidth();
    }

    private void updateWidth() {
        setWidth(getWidth());
    }

    @Override
    public void onItemClick(Site item) {
        callback.setSite(item);
        dismiss();
    }

    @Override
    public void onItemLongClick(Site item) {
        if (type != 0 || item.getKey().isEmpty()) return;
        new MaterialAlertDialogBuilder(getContext())
                .setMessage(ResUtil.getString(item.isBlacklist() ? R.string.site_blacklist_remove_confirm : R.string.site_blacklist_confirm, item.getName()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (d, which) -> {
                    if (item.isBlacklist()) item.resetFailures();
                    else item.setBlacklist();
                    adapter.notifyDataSetChanged();
                }).show();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else {
            updateWidth();
            if (getDialog() != null && getDialog().getWindow() != null) {
                getDialog().getWindow().setDimAmount(0);
            }
        }
    }
}
