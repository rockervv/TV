package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingSpiderBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class SettingSpiderActivity extends BaseActivity {

    private ActivitySettingSpiderBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingSpiderActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingSpiderBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.localSpider.requestFocus();
        setOtherText();
    }

    private void setOtherText() {
        mBinding.localSpiderText.setText(getLocalSpiderName());
        mBinding.quickjsText.setText(Setting.getSwitch(Setting.isQuickJS()));
        mBinding.chaquoText.setText(Setting.getSwitch(Setting.isChaquo()));
    }

    private String getLocalSpiderName() {
        String key = Setting.getLocalSpider();
        if (key.isEmpty()) return ResUtil.getString(R.string.setting_off);
        com.github.catvod.crawler.Spider spider = com.github.catvod.spider.SpiderFactory.get(key);
        return spider != null ? spider.getName() : ResUtil.getString(R.string.setting_off);
    }

    @Override
    protected void initEvent() {
        mBinding.localSpider.setOnClickListener(this::onLocalSpider);
        mBinding.quickjs.setOnClickListener(this::onQuickJS);
        mBinding.chaquo.setOnClickListener(this::onChaquo);
    }

    private void onLocalSpider(View view) {
        List<String> names = new ArrayList<>();
        List<String> values = new ArrayList<>();
        names.add(ResUtil.getString(R.string.setting_off));
        values.add("");
        for (String key : com.github.catvod.spider.SpiderFactory.getKeys()) {
            com.github.catvod.crawler.Spider spider = com.github.catvod.spider.SpiderFactory.get(key);
            if (spider != null) {
                names.add(spider.getName());
                values.add(key);
            }
        }
        int index = values.indexOf(Setting.getLocalSpider());
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_local_spider)
                .setSingleChoiceItems(names.toArray(new String[0]), index, (dialog, which) -> {
                    Setting.putLocalSpider(values.get(which));
                    mBinding.localSpiderText.setText(names.get(which));
                    dialog.dismiss();
                })
                .show();
    }

    private void onQuickJS(View view) {
        boolean current = Setting.isQuickJS();
        Setting.putQuickJS(!current);
        mBinding.quickjsText.setText(Setting.getSwitch(!current));
        com.fongmi.android.tv.api.loader.BaseLoader.get().clear();
        if (current) Notify.show("關閉引擎建議重啟應用程式以釋放記憶體");
    }

    private void onChaquo(View view) {
        boolean current = Setting.isChaquo();
        Setting.putChaquo(!current);
        mBinding.chaquoText.setText(Setting.getSwitch(!current));
        com.fongmi.android.tv.api.loader.BaseLoader.get().clear();
        if (current) Notify.show("關閉 Python 建議重啟應用程式以釋放記憶體");
    }
}
