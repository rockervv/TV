package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogProxyBinding;
import com.fongmi.android.tv.impl.ProxyCallback;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ProxyDialog extends BaseAlertDialog {

    private DialogProxyBinding binding;
    private ProxyCallback callback;
    private boolean append;

    public static ProxyDialog create() {
        return new ProxyDialog();
    }

    public static ProxyDialog create(Fragment fragment) {
        return new ProxyDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogProxyBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder()
                .setTitle(R.string.setting_proxy)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.dialog_positive, this::onPositive)
                .setNegativeButton(R.string.dialog_negative, this::onNegative);
    }

    @Override
    protected void initView() {
        this.callback = (ProxyCallback) (getParentFragment() != null ? getParentFragment() : getActivity());
        this.append = true;
        String text = Setting.getProxy();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    @Override
    protected void initEvent() {
        binding.text.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) ((AlertDialog) getDialog()).getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            return true;
        });
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.text.append("ttp://");
        } else if (append && "s".equalsIgnoreCase(s)) {
            append = false;
            binding.text.append("ocks5://");
        } else if (append && s.length() == 1) {
            append = false;
            binding.text.getText().insert(0, "socks5://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void onPositive(DialogInterface dialog, int which) {
        callback.setProxy(binding.text.getText().toString().trim());
        dismiss();
    }

    private void onNegative(DialogInterface dialog, int which) {
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
