package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.Objects;

public class CustomEditText extends AppCompatEditText {

    public CustomEditText(@NonNull Context context) {
        super(context);
    }

    public CustomEditText(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        Configuration config = getResources().getConfiguration();
        if (config.keyboard != Configuration.KEYBOARD_NOKEYS) {
            config.keyboard = Configuration.KEYBOARD_NOKEYS;
            getContext().getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }
        return super.onCheckIsTextEditor();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused && getShowSoftInputOnFocus()) {
            setRawInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(this, InputMethodManager.SHOW_FORCED);
            }, 200);
        }
    }

    private View focusSearch(KeyEvent event) {
        if (KeyUtil.isUpKey(event)) return getParent().focusSearch(this, FOCUS_UP);
        if (KeyUtil.isDownKey(event)) return getParent().focusSearch(this, FOCUS_DOWN);
        if (KeyUtil.isLeftKey(event) && getSelectionStart() == 0) return getParent().focusSearch(this, FOCUS_LEFT);
        if (KeyUtil.isRightKey(event) && getSelectionStart() == Objects.requireNonNull(getText()).length()) return getParent().focusSearch(this, FOCUS_RIGHT);
        return null;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            Util.showKeyboard(this);
        }
        View v = focusSearch(event);
        if (v != null) return v.requestFocus();
        return super.onKeyDown(keyCode, event);
    }
}
