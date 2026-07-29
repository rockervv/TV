package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogMpvConfBinding;
import com.fongmi.android.tv.player.mpv.MpvConfigFiles;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.QRCode;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class MpvConfDialog extends BaseAlertDialog {

    private DialogMpvConfBinding binding;

    public static void show(FragmentActivity activity) {
        new MpvConfDialog().show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogMpvConfBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        setText(MpvConfigFiles.read());
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(3), 200, 0));
    }

    @Override
    protected void initEvent() {
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private void setText(String text) {
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    private void onPositive(View view) {
        MpvConfigFiles.write(binding.text.getText().toString());
        dismiss();
    }

    private void onNegative(View view) {
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.55f);
    }
}
