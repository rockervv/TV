package com.fongmi.android.tv.ui.dialog;

import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.RemoteSyncManager;
import com.fongmi.android.tv.databinding.DialogSyncBinding;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class SyncDialog extends BaseAlertDialog {

    private DialogSyncBinding binding;
    private boolean append;

    public static SyncDialog create() {
        return new SyncDialog();
    }

    public static SyncDialog create(FragmentActivity activity) {
        return new SyncDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSyncBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.ftpServer.setText(Setting.getFtpUri());
        binding.ftpUsername.setText(Setting.getFtpUsername());
        binding.ftpPassword.setText(Setting.getFtpPassword());
        binding.syncUseFtpText.setText(getSwitch(Setting.isUseFtp()));
        binding.syncUseGistText.setText(getSwitch(Setting.isUseGist()));
        binding.syncGistUrl.setText(Setting.getGistUrl());
        binding.syncGistToken.setText(Setting.getGistToken());
        binding.ftpServer.requestFocus();
        com.fongmi.android.tv.utils.Util.showKeyboard(binding.ftpServer);
    }

    @Override
    protected void initEvent() {
        EventBus.getDefault().register(this);
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.syncUseFtp.setOnClickListener(this::setUseFtp);
        binding.syncUseGist.setOnClickListener(this::setUseGist);
        binding.ftpServer.setOnFocusChangeListener((view, hasFocus) -> { if (hasFocus) append = true; });
        binding.syncGistUrl.setOnFocusChangeListener((view, hasFocus) -> { if (hasFocus) append = true; });
        binding.ftpServer.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(binding.ftpServer, s.toString());
            }
        });
        binding.syncGistUrl.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(binding.syncGistUrl, s.toString());
            }
        });
    }

    private void onPositive(View view) {
        Setting.putFtpPassword(binding.ftpPassword.getText().toString().trim());
        Setting.putFtpUsername(binding.ftpUsername.getText().toString().trim());
        Setting.putFtpUri(binding.ftpServer.getText().toString().trim());
        Setting.putGistUrl(binding.syncGistUrl.getText().toString().trim());
        Setting.putGistToken(binding.syncGistToken.getText().toString().trim());
        RemoteSyncManager.init(Setting.getFtpUri(), Setting.getFtpUsername(), Setting.getFtpPassword(), Setting.isUseFtp());
        RemoteSyncManager.initGist(Setting.getGistUrl(), Setting.getGistToken(), Setting.isUseGist());
        dismiss();
    }

    private String getSwitch(boolean value) {
        return ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private void setUseFtp(View view) {
        Setting.putUseFtp(!Setting.isUseFtp());
        binding.syncUseFtpText.setText(getSwitch(Setting.isUseFtp()));
    }

    private void setUseGist(View view) {
        Setting.putUseGist(!Setting.isUseGist());
        binding.syncUseGistText.setText(getSwitch(Setting.isUseGist()));
    }

    private void onNegative(View view) {
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.getType() != ServerEvent.Type.SETTING) return;
    }

    private void detect(EditText view, String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            view.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            view.append("tp://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.90f);
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setDimAmount(0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }
}
