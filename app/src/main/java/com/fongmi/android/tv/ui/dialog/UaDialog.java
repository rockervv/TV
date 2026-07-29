package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogUaBinding;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.impl.UaCallback;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.QRCode;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class UaDialog extends BaseAlertDialog {

    private DialogUaBinding binding;
    private UaCallback callback;
    private boolean append;

    public static UaDialog create() {
        return new UaDialog();
    }

    public static UaDialog create(FragmentActivity activity) {
        return new UaDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogUaBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        this.callback = (UaCallback) getActivity();
        this.append = true;
        String text = Setting.getUa();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
        binding.text.requestFocus();
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(3), 200, 0));
        binding.info.setText(ResUtil.getString(R.string.push_info, Server.get().getAddress()).replace("，", "\n"));
    }

    @Override
    protected void initEvent() {
        EventBus.getDefault().register(this);
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.text.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private void detect(String s) {
        if (append && "c".equalsIgnoreCase(s)) {
            append = false;
            binding.text.setText(Util.CHROME);
        } else if (append && "o".equalsIgnoreCase(s)) {
            append = false;
            binding.text.setText(Util.OKHTTP);
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void onPositive(View view) {
        callback.setUa(binding.text.getText().toString().trim());
        dismiss();
    }

    private void onNegative(View view) {
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.getType() != ServerEvent.Type.SETTING) return;
        binding.text.setText(event.getText());
        binding.positive.performClick();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.55f);
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setDimAmount(0);
            getDialog().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        }
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        EventBus.getDefault().unregister(this);
    }
}
