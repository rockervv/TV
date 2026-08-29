package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.databinding.ActivitySettingPlayerBinding;
import com.fongmi.android.tv.impl.SpeedListener;
import com.fongmi.android.tv.impl.UaCallback;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.MpvConfDialog;
import com.fongmi.android.tv.ui.dialog.SpeedDialog;
import com.fongmi.android.tv.ui.dialog.UaDialog;
import com.fongmi.android.tv.utils.DescHelper;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.text.DecimalFormat;

public class SettingPlayerActivity extends BaseActivity implements UaCallback, SpeedListener {

    private ActivitySettingPlayerBinding mBinding;
    private DecimalFormat format;
    private String[] caption;
    private String[] render;
    private String[] scale;
    private String[] reset;
    private String[] engine;
    private String[] mpvHwdec;
    private String[] mpvGpuApi;
    private String[] mpvFboFormat;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPlayerActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPlayerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setVisible();
        setPlaybackModeText();
        mBinding.engine.requestFocus();
        format = new DecimalFormat("0.#");
        mBinding.speedText.setText(format.format(PlayerSetting.getSpeed()));
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
        mBinding.mpvHwdecText.setText(mpvHwdec[PlayerSetting.getMpvHwdec()]);
        mBinding.mpvGpuApiText.setText(mpvGpuApi[PlayerSetting.getMpvGpuApi()]);
        mBinding.mpvFboFormatText.setText(mpvFboFormat[PlayerSetting.getMpvFboFormat()]);
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext()));
        mBinding.backgroundText.setText(Setting.getSwitch(PlayerSetting.isBackgroundOn()));
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
        mBinding.resetText.setText((reset = ResUtil.getStringArray(R.array.select_reset))[Setting.getReset()]);
        mBinding.captionText.setText((caption = ResUtil.getStringArray(R.array.select_caption))[PlayerSetting.isCaption() ? 1 : 0]);
        mBinding.normalizeText.setText(Setting.getSwitch(Setting.isNormalize()));
        mBinding.renderEnhanceText.setText(Setting.getSwitch(PlayerSetting.isRenderEnhance()));
        initDesc();
    }

    private void initDesc() {
        DescHelper.create(mBinding.descLayout.descCard, mBinding.descLayout.desc)
                .put(R.id.engine, R.string.desc_player_engine)
                .put(R.id.reset, R.string.desc_player_reset)
                .put(R.id.caption, R.string.desc_player_caption)
                .put(R.id.speed, R.string.desc_player_speed)
                .put(R.id.mpvConf, R.string.desc_player_mpv_conf)
                .put(R.id.render, R.string.desc_player_render)
                .put(R.id.decode, R.string.desc_player_decode)
                .bind(mBinding.getRoot());
    }

    @Override
    protected void initEvent() {
        mBinding.engine.setOnClickListener(this::setEngine);
        mBinding.reset.setOnClickListener(this::setReset);
        mBinding.mpvConf.setOnClickListener(this::onMpvConf);
        mBinding.mpvHwdec.setOnClickListener(this::onMpvHwdec);
        mBinding.mpvGpuApi.setOnClickListener(this::onMpvGpuApi);
        mBinding.mpvFboFormat.setOnClickListener(this::onMpvFboFormat);
        mBinding.mpvGpuNext.setOnClickListener(this::setMpvGpuNext);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.caption.setOnClickListener(this::setCaption);
        mBinding.caption.setOnLongClickListener(this::onCaption);
        mBinding.speed.setOnClickListener(this::onSpeed);
        mBinding.normalize.setOnClickListener(this::setNormalize);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.adblock.setOnClickListener(this::setAdblock);
        mBinding.renderEnhance.setOnClickListener(this::setRenderEnhance);
        mBinding.preload.setOnClickListener(this::onPreloadSetting);
        mBinding.decode.setOnClickListener(this::onDecodeSetting);
        mBinding.ua.setOnClickListener(this::onUa);
    }

    private void setVisible() {
        boolean exo = !PlayerSetting.isMpv();
        if (PlayerSetting.isBackgroundPiP()) PlayerSetting.putBackground(1);
        mBinding.mpvConf.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.mpvHwdec.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.mpvGpuApi.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.mpvFboFormat.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.mpvGpuNext.setVisibility(exo ? View.GONE : View.VISIBLE);
        mBinding.decode.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.adblock.setVisibility(exo ? View.VISIBLE : View.GONE);
        mBinding.caption.setVisibility(PlayerSetting.hasCaption() ? View.VISIBLE : View.GONE);
    }

    private void setEngine(View view) {
        int index = (PlayerSetting.getEngine() + 1) % engine.length;
        PlayerSetting.putEngine(index);
        setPlaybackModeText();
        setVisible();
    }

    private void setReset(View view) {
        int index = (Setting.getReset() + 1) % reset.length;
        Setting.putReset(index);
        mBinding.resetText.setText(reset[index]);
    }

    private void onMpvConf(View view) {
        MpvConfDialog.show(this);
    }

    private void onMpvHwdec(View view) {
        int index = PlayerSetting.getMpvHwdec();
        PlayerSetting.putMpvHwdec(index = index == mpvHwdec.length - 1 ? 0 : ++index);
        mBinding.mpvHwdecText.setText(mpvHwdec[index]);
    }

    private void onMpvGpuApi(View view) {
        int index = PlayerSetting.getMpvGpuApi();
        PlayerSetting.putMpvGpuApi(index = index == mpvGpuApi.length - 1 ? 0 : ++index);
        mBinding.mpvGpuApiText.setText(mpvGpuApi[index]);
    }

    private void onMpvFboFormat(View view) {
        int index = PlayerSetting.getMpvFboFormat();
        PlayerSetting.putMpvFboFormat(index = index == mpvFboFormat.length - 1 ? 0 : ++index);
        mBinding.mpvFboFormatText.setText(mpvFboFormat[index]);
        if (index == 0) {
            int recommendedIndex = Util.getTotalMem() <= 2048 * 1024 * 1024L ? 1 : 2;
            Notify.show(ResUtil.getString(R.string.play_fbo_auto, mpvFboFormat[recommendedIndex]));
        }
    }

    private void setMpvGpuNext(View view) {
        PlayerSetting.putMpvGpuNext(!PlayerSetting.isMpvGpuNext());
        mBinding.mpvGpuNextText.setText(Setting.getSwitch(PlayerSetting.isMpvGpuNext()));
    }

    private void setRender(View view) {
        int index = (PlayerSetting.getRender() + 1) % render.length;
        PlayerSetting.putRender(index);
        setPlaybackModeText();
    }

    private void setPlaybackModeText() {
        engine = ResUtil.getStringArray(R.array.select_engine);
        mpvHwdec = ResUtil.getStringArray(R.array.select_mpv_hwdec);
        mpvGpuApi = ResUtil.getStringArray(R.array.select_mpv_gpu_api);
        mpvFboFormat = ResUtil.getStringArray(R.array.select_mpv_fbo_format);
        render = ResUtil.getStringArray(R.array.select_render);
        mBinding.engineText.setText(engine[PlayerSetting.getEngine()]);
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
    }

    private void setScale(View view) {
        int index = (PlayerSetting.getScale() + 1) % scale.length;
        mBinding.scaleText.setText(scale[index]);
        PlayerSetting.putScale(index);
    }

    private void setCaption(View view) {
        PlayerSetting.putCaption(!PlayerSetting.isCaption());
        mBinding.captionText.setText(caption[PlayerSetting.isCaption() ? 1 : 0]);
    }

    private boolean onCaption(View view) {
        if (PlayerSetting.isCaption()) startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
        return PlayerSetting.isCaption();
    }

    private void onSpeed(View view) {
        SpeedDialog.show(this);
    }

    @Override
    public void setSpeed(float speed) {
        mBinding.speedText.setText(format.format(speed));
        PlayerSetting.putSpeed(speed);
    }

    private void onBackground(View view) {
        PlayerSetting.putBackground(PlayerSetting.isBackgroundOn() ? 0 : 1);
        mBinding.backgroundText.setText(Setting.getSwitch(PlayerSetting.isBackgroundOn()));
    }

    private void setNormalize(View view) {
        Setting.putNormalize(!Setting.isNormalize());
        mBinding.normalizeText.setText(Setting.getSwitch(Setting.isNormalize()));
    }

    private void setAdblock(View view) {
        Setting.putAdblock(!Setting.isAdblock());
        mBinding.adblockText.setText(Setting.getSwitch(Setting.isAdblock()));
    }

    private void setRenderEnhance(View view) {
        PlayerSetting.putRenderEnhance(!PlayerSetting.isRenderEnhance());
        mBinding.renderEnhanceText.setText(Setting.getSwitch(PlayerSetting.isRenderEnhance()));
    }

    private void onPreloadSetting(View view) {
        SettingPreloadActivity.start(this);
    }

    private void onDecodeSetting(View view) {
        SettingDecodeActivity.start(this);
    }

    private void onUa(View view) {
        UaDialog.create(this).show(this);
    }

    @Override
    public void setUa(String ua) {
        Setting.putUa(ua);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBinding != null) setPlaybackModeText();
    }
}
