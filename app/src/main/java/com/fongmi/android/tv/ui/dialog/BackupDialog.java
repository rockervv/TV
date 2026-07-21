package com.fongmi.android.tv.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogHistoryBinding;
import com.fongmi.android.tv.impl.BackupCallback;
import com.fongmi.android.tv.ui.adapter.BackupAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class BackupDialog extends BaseAlertDialog implements BackupAdapter.OnClickListener {

    private DialogHistoryBinding binding;
    private BackupCallback callback;
    private BackupAdapter adapter;

    public static BackupDialog create() {
        return new BackupDialog();
    }

    public static BackupDialog create(Fragment fragment) {
        return new BackupDialog();
    }

    public static BackupDialog create(FragmentActivity activity) {
        return new BackupDialog();
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
        if (context instanceof BackupCallback) {
            callback = (BackupCallback) context;
        } else if (getParentFragment() instanceof BackupCallback) {
            callback = (BackupCallback) getParentFragment();
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
        adapter = new BackupAdapter(this);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, ResUtil.isPad() ? 8 : 16));
        binding.recycler.setAdapter(adapter.addAll());
        binding.recycler.requestFocus();
    }

    @Override
    public void onTextClick(String item) {
        if (callback != null) callback.restore(new File(Path.tv(), item));
        dismiss();
    }

    @Override
    public void onDeleteClick(String item) {
        if (adapter.remove(item) == 0) dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else {
            setWidth(ResUtil.isPad() || ResUtil.isLand(requireContext()) ? 0.4f : 0.8f);
            if (getDialog() != null && getDialog().getWindow() != null) {
                getDialog().getWindow().setDimAmount(0);
            }
        }
    }
}
