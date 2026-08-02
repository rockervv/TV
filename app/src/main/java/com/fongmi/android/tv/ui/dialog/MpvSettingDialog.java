package com.fongmi.android.tv.ui.dialog;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.databinding.DialogMpvSettingBinding;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.activity.PlaybackActivity;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.R;
import com.github.bassaer.library.MDColor;

import java.util.HashMap;
import java.util.Map;

public class MpvSettingDialog extends BaseBottomSheetDialog {

    private DialogMpvSettingBinding binding;
    private final String[] syncValues = {"audio", "display-resample", "desync"};
    private final String[] framedropValues = {"no", "vo", "decoder"};
    private final Map<TextView, Object> tempValues = new HashMap<>();

    public static void show(FragmentActivity activity) {
        new MpvSettingDialog().show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogMpvSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.hwdec.setText(ResUtil.getStringArray(R.array.select_mpv_hwdec)[PlayerSetting.getMpvHwdec()]);
        binding.vo.setText(PlayerSetting.isMpvGpuNext() ? "gpu-next" : "gpu");
        binding.gpuApi.setText(ResUtil.getStringArray(R.array.select_mpv_gpu_api)[PlayerSetting.getMpvGpuApi()]);
        binding.fboFormat.setText(ResUtil.getStringArray(R.array.select_mpv_fbo_format)[PlayerSetting.getMpvFboFormat()]);
        binding.stats.setText(ResUtil.getString(PlayerSetting.isMpvStats() ? R.string.setting_on : R.string.setting_off));
        binding.threads.setText(String.valueOf(PlayerSetting.getMpvThreads()));
        binding.fast.setText(ResUtil.getString(PlayerSetting.isMpvFast() ? R.string.setting_on : R.string.setting_off));
        binding.pbo.setText(ResUtil.getString(PlayerSetting.isMpvPbo() ? R.string.setting_on : R.string.setting_off));
        binding.videoSync.setText(PlayerSetting.getMpvVideoSync());
        binding.framedrop.setText(PlayerSetting.getMpvFramedrop());
        binding.skipLoop.setText(ResUtil.getString(PlayerSetting.isMpvSkipLoop() ? R.string.setting_on : R.string.setting_off));
        binding.audioBuffer.setText(PlayerSetting.getMpvAudioBuffer() + "ms");
    }

    @Override
    protected void initEvent() {
        View.OnKeyListener listener = (view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            TextView target = getTextView(view);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    changeValue(target, keyCode == KeyEvent.KEYCODE_DPAD_RIGHT);
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    apply(target);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    apply(target);
                    return false;
            }
            return false;
        };
        setRowListener(binding.hwdec, listener);
        setRowListener(binding.vo, listener);
        setRowListener(binding.gpuApi, listener);
        setRowListener(binding.fboFormat, listener);
        setRowListener(binding.stats, listener);
        setRowListener(binding.threads, listener);
        setRowListener(binding.fast, listener);
        setRowListener(binding.pbo, listener);
        setRowListener(binding.videoSync, listener);
        setRowListener(binding.framedrop, listener);
        setRowListener(binding.skipLoop, listener);
        setRowListener(binding.audioBuffer, listener);
    }

    private void setRowListener(TextView view, View.OnKeyListener listener) {
        View row = (View) view.getParent();
        row.setOnKeyListener(listener);
        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) apply(getTextView(v)); // Auto apply when leaving? No, apply when moving.
        });
    }

    private TextView getTextView(View row) {
        if (row instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (group.getChildAt(i) instanceof TextView v && v.getId() != View.NO_ID) return v;
            }
        }
        return null;
    }

    private void changeValue(TextView view, boolean next) {
        if (view == null) return;
        if (view == binding.hwdec) {
            int current = PlayerSetting.getMpvHwdec();
            String[] labels = ResUtil.getStringArray(R.array.select_mpv_hwdec);
            int val = next ? (current + 1) % labels.length : (current - 1 + labels.length) % labels.length;
            PlayerSetting.putMpvHwdec(val);
            binding.hwdec.setText(labels[val]);
        } else if (view == binding.vo) {
            boolean current = PlayerSetting.isMpvGpuNext();
            PlayerSetting.putMpvGpuNext(!current);
            binding.vo.setText(!current ? "gpu-next" : "gpu");
        } else if (view == binding.gpuApi) {
            int current = PlayerSetting.getMpvGpuApi();
            String[] labels = ResUtil.getStringArray(R.array.select_mpv_gpu_api);
            int val = next ? (current + 1) % labels.length : (current - 1 + labels.length) % labels.length;
            PlayerSetting.putMpvGpuApi(val);
            binding.gpuApi.setText(labels[val]);
        } else if (view == binding.fboFormat) {
            int current = PlayerSetting.getMpvFboFormat();
            String[] labels = ResUtil.getStringArray(R.array.select_mpv_fbo_format);
            int val = next ? (current + 1) % labels.length : (current - 1 + labels.length) % labels.length;
            PlayerSetting.putMpvFboFormat(val);
            binding.fboFormat.setText(labels[val]);
        } else if (view == binding.stats) {
            boolean current = PlayerSetting.isMpvStats();
            PlayerSetting.putMpvStats(!current);
            binding.stats.setText(ResUtil.getString(!current ? R.string.setting_on : R.string.setting_off));
        } else if (view == binding.threads) {
            int current = PlayerSetting.getMpvThreads();
            int val = next ? current + 1 : current - 1;
            if (val < 0) val = 16;
            if (val > 16) val = 0;
            PlayerSetting.putMpvThreads(val);
            binding.threads.setText(String.valueOf(val));
        } else if (view == binding.fast) {
            boolean current = PlayerSetting.isMpvFast();
            PlayerSetting.putMpvFast(!current);
            binding.fast.setText(ResUtil.getString(!current ? R.string.setting_on : R.string.setting_off));
        } else if (view == binding.pbo) {
            boolean current = PlayerSetting.isMpvPbo();
            PlayerSetting.putMpvPbo(!current);
            binding.pbo.setText(ResUtil.getString(!current ? R.string.setting_on : R.string.setting_off));
        } else if (view == binding.skipLoop) {
            boolean current = PlayerSetting.isMpvSkipLoop();
            PlayerSetting.putMpvSkipLoop(!current);
            binding.skipLoop.setText(ResUtil.getString(!current ? R.string.setting_on : R.string.setting_off));
        } else if (view == binding.audioBuffer) {
            int current = PlayerSetting.getMpvAudioBuffer();
            int val = next ? (current >= 1000 ? 50 : current + 100) : (current <= 50 ? 1000 : current - 100);
            PlayerSetting.putMpvAudioBuffer(val);
            binding.audioBuffer.setText(val + "ms");
        } else if (view == binding.videoSync) {
            String current = PlayerSetting.getMpvVideoSync();
            int index = getIndex(syncValues, current);
            int nextIndex = next ? (index + 1) % syncValues.length : (index - 1 + syncValues.length) % syncValues.length;
            PlayerSetting.putMpvVideoSync(syncValues[nextIndex]);
            binding.videoSync.setText(syncValues[nextIndex]);
        } else if (view == binding.framedrop) {
            String current = PlayerSetting.getMpvFramedrop();
            int index = getIndex(framedropValues, current);
            int nextIndex = next ? (index + 1) % framedropValues.length : (index - 1 + framedropValues.length) % framedropValues.length;
            PlayerSetting.putMpvFramedrop(framedropValues[nextIndex]);
            binding.framedrop.setText(framedropValues[nextIndex]);
        }
        showMpvStats();
    }

    private void apply(TextView view) {
        // No longer needed as values apply immediately
    }

    private void showMpvStats() {
        FragmentActivity activity = getActivity();
        if (activity instanceof PlaybackActivity owner && owner.player() != null && owner.player().getEngine() == PlayerSetting.ENGINE_MPV) {
            owner.player().setStats(PlayerSetting.isMpvStats());
        }
    }

    private void updateBackground(TextView view) {
        View row = (View) view.getParent();
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
    }

    private int getIndex(String[] arr, String val) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(val)) return i;
        return 0;
    }
}
