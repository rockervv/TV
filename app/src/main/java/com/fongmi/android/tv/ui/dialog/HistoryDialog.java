package com.fongmi.android.tv.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogHistoryBinding;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.ui.adapter.ConfigAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HistoryDialog extends BaseAlertDialog implements ConfigAdapter.OnClickListener {

    private DialogHistoryBinding binding;
    private ConfigListener listener;
    private ConfigAdapter adapter;
    private boolean readOnly;
    private int type;

    public static HistoryDialog create() {
        return new HistoryDialog();
    }

    public static HistoryDialog create(Fragment fragment) {
        return new HistoryDialog();
    }

    public static HistoryDialog create(FragmentActivity activity) {
        return new HistoryDialog();
    }

    public HistoryDialog type(int type) {
        this.type = type;
        return this;
    }

    public HistoryDialog vod() {
        return type(0);
    }

    public HistoryDialog live() {
        return type(1);
    }

    public HistoryDialog wall() {
        return type(2);
    }

    public HistoryDialog readOnly() {
        readOnly = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof ConfigListener) {
            listener = (ConfigListener) context;
        } else if (getParentFragment() instanceof ConfigListener) {
            listener = (ConfigListener) getParentFragment();
        }
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHistoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new ConfigAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        if (ResUtil.isPad()) binding.recycler.setMaxHeight(ResUtil.dp2px(264));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, ResUtil.isLand(requireContext()) ? 16 : 8));
        binding.recycler.setAdapter(adapter.readOnly(readOnly).addAll(type));
    }

    @Override
    public void onTextClick(Config item) {
        if (listener != null) listener.setConfig(item);
        dismiss();
    }

    @Override
    public void onDeleteClick(Config item) {
        if (adapter.remove(item) == 0) dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else setWidth(ResUtil.isPad() || ResUtil.isLand(requireContext()) ? 0.4f : 0.8f);
    }
}
