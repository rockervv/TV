package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.C;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogOffsetBinding;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.slider.Slider;

import java.util.Locale;

public final class OffsetDialog {

    private PlayerManager player;
    private int type;

    public static OffsetDialog create() {
        return new OffsetDialog();
    }

    public OffsetDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public OffsetDialog type(int type) {
        this.type = type;
        return this;
    }

    public void show(FragmentActivity activity) {
        FragmentManager manager = activity.getSupportFragmentManager();
        for (Fragment f : manager.getFragments()) if (f instanceof BottomSheet || f instanceof SideSheet) return;
        if (Util.isFullscreenLand(activity) || Util.isLeanback()) new SideSheet(player, type).show(manager, null);
        else new BottomSheet(player, type).show(manager, null);
    }

    private static DialogOffsetBinding inflate(LayoutInflater inflater, ViewGroup container) {
        return DialogOffsetBinding.inflate(inflater, container, false);
    }

    public static final class BottomSheet extends BaseBottomSheetDialog {

        private DialogOffsetBinding binding;
        private final PlayerManager player;
        private final int type;

        BottomSheet(PlayerManager player, int type) {
            this.player = player;
            this.type = type;
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = OffsetDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            new OffsetPanel(binding, player, type).bind();
        }
    }

    public static final class SideSheet extends BaseSideSheetDialog {

        private DialogOffsetBinding binding;
        private final PlayerManager player;
        private final int type;

        SideSheet(PlayerManager player, int type) {
            this.player = player;
            this.type = type;
        }

        @Override
        protected int getWidth() {
            return Math.min(ResUtil.dp2px(320), ResUtil.getScreenWidth() / 2);
        }

        @Override
        protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
            return binding = OffsetDialog.inflate(inflater, container);
        }

        @Override
        protected void initView() {
            new OffsetPanel(binding, player, type).bind();
        }
    }

    private static class OffsetPanel {

        private final DialogOffsetBinding binding;
        private final PlayerManager player;
        private final int type;

        OffsetPanel(DialogOffsetBinding binding, PlayerManager player, int type) {
            this.binding = binding;
            this.player = player;
            this.type = type;
        }

        void bind() {
            initView();
            initEvent();
        }

        private void initView() {
            binding.audioSection.setVisibility(type == C.TRACK_TYPE_AUDIO ? View.VISIBLE : View.GONE);
            binding.textSection.setVisibility(type == C.TRACK_TYPE_TEXT ? View.VISIBLE : View.GONE);
            binding.audioSlider.setValue(player.getAudioOffsetMs());
            binding.textSlider.setValue(player.getTextOffsetMs());
            binding.audioValue.setText(String.format(Locale.getDefault(), "%d ms", (int) binding.audioSlider.getValue()));
            binding.textValue.setText(String.format(Locale.getDefault(), "%d ms", (int) binding.textSlider.getValue()));
        }

        private void initEvent() {
            binding.reset.setOnClickListener(v -> onReset());
            binding.audioSlider.addOnSliderTouchListener(getTouchListener());
            binding.textSlider.addOnSliderTouchListener(getTouchListener());
            binding.audioSlider.addOnChangeListener((slider, value, fromUser) -> onAudioChange(value));
            binding.textSlider.addOnChangeListener((slider, value, fromUser) -> onTextChange(value));
        }

        private void onReset() {
            binding.audioSlider.setValue(0);
            binding.textSlider.setValue(0);
            onAudioChange(0);
            onTextChange(0);
        }

        private void onAudioChange(float value) {
            player.setAudioOffsetMs((long) value);
            binding.audioValue.setText(String.format(Locale.getDefault(), "%d ms", (int) value));
        }

        private void onTextChange(float value) {
            player.setTextOffsetMs((long) value);
            binding.textValue.setText(String.format(Locale.getDefault(), "%d ms", (int) value));
        }

        private Slider.OnSliderTouchListener getTouchListener() {
            return new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(@NonNull Slider slider) {
                }

                @Override
                public void onStopTrackingTouch(@NonNull Slider slider) {
                }
            };
        }
    }
}
