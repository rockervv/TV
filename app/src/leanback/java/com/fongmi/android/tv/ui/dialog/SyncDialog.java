package com.fongmi.android.tv.ui.dialog;

//import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
//import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.DialogSyncBinding;
import com.fongmi.android.tv.event.ServerEvent;
//import com.fongmi.android.tv.impl.SyncCallback;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

//import java.util.Locale;
//import android.content.Intent;
//import com.fongmi.android.tv.R;


public class SyncDialog{ // implements DialogInterface.OnDismissListener {

    private final DialogSyncBinding binding;
    private final FragmentActivity activity;
    private final AlertDialog dialog;
    private boolean append;


    public static SyncDialog create(FragmentActivity activity) {
        return new SyncDialog(activity);
    }

    public SyncDialog(FragmentActivity activity) {
        this.activity = activity;
        this.binding = DialogSyncBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.append = true;
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.90f);
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void initView() {
        binding.ftpServer.setText(Setting.getFtpUri());
        binding.ftpUsername.setText(Setting.getFtpUsername());
        binding.ftpPassword.setText(Setting.getFtpPassword());


        binding.syncUseFtpText.setText(getSwitch(Setting.isUseFtp()));
        binding.syncUseGistText.setText(getSwitch(Setting.isUseGist()));
        binding.syncGistUrl.setText(Setting.getGistUrl());
        binding.syncGistToken.setText(Setting.getGistToken());

    }

    private void initEvent() {
        EventBus.getDefault().register(this);
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.syncUseFtp.setOnClickListener(this::setUseFtp);
        binding.syncUseGist.setOnClickListener(this::setUseGist);
        binding.ftpServer.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());


            }
        });
    }


    private void onPositive(View view) {
        Setting.putFtpPassword(binding.ftpPassword.getText().toString().trim());
        Setting.putFtpUsername(binding.ftpUsername.getText().toString().trim());
        Setting.putFtpUri(binding.ftpServer.getText().toString().trim());

        Setting.putGistUrl(binding.syncGistUrl.getText().toString().trim());
        Setting.putGistToken(binding.syncGistToken.getText().toString().trim());
        dialog.dismiss();


    }

    private String getSwitch(boolean value) {
        String string = ResUtil.getString(value ? R.string.setting_on : R.string.setting_off);
        return string;
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
        dialog.dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.getType() != ServerEvent.Type.SETTING) return;
    }


    private void detect(String s) {
        //int l = s.length();
        if (append && "h".equalsIgnoreCase(s)) {
            //append = false;
            binding.ftpServer.append("ttp");

        } else if (append && "f".equalsIgnoreCase(s)) {
            //append = false;
            binding.ftpServer.append("tp");
        } else if (append && "https".equalsIgnoreCase(s)) {
            append = false;
            binding.ftpServer.append("://");
        } else if (append && "ftps".equalsIgnoreCase(s)) {
            append = false;
            binding.ftpServer.append("://");
        } else if (append && "http:".equalsIgnoreCase(s)) {
            append = false;
            binding.ftpServer.append("//");
        } else if (append && "ftp:".equalsIgnoreCase(s)) {
            append = false;
            binding.ftpServer.append("//");
        } else if (s.length() > 8) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }


}