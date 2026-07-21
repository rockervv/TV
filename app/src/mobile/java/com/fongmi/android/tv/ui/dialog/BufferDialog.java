package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogBufferBinding;
import com.fongmi.android.tv.impl.BufferCallback;
import com.fongmi.android.tv.setting.Setting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class BufferDialog extends BaseAlertDialog {

    private DialogBufferBinding binding;
    private BufferCallback callback;
    private int value;

    public static BufferDialog create() {
        return new BufferDialog();
    }

    public static BufferDialog create(Fragment fragment) {
        return new BufferDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogBufferBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder()
                .setTitle(R.string.player_exo_buffer)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.dialog_positive, this::onPositive)
                .setNegativeButton(R.string.dialog_negative, this::onNegative);
    }

    @Override
    protected void initView() {
        this.callback = (BufferCallback) (getParentFragment() != null ? getParentFragment() : getActivity());
        binding.slider.setValue(value = Setting.getBuffer());
    }

    private void onPositive(DialogInterface dialog, int which) {
        callback.setBuffer((int) binding.slider.getValue());
        dismiss();
    }

    private void onNegative(DialogInterface dialog, int which) {
        callback.setBuffer(value);
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setDimAmount(0);
        }
    }
}
