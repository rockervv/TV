package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.HorizontalGridView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

public class CustomHorizontalGridView extends HorizontalGridView {

    private Animation shake;

    public CustomHorizontalGridView(@NonNull Context context) {
        super(context);
    }

    public CustomHorizontalGridView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomHorizontalGridView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void initAttributes(@NonNull Context context, @Nullable AttributeSet attrs) {
        super.initAttributes(context, attrs);
        this.shake = isInEditMode() ? null : ResUtil.getAnim(R.anim.shake);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        if (focused != null) {
            View found = FocusFinder.getInstance().findNextFocus(this, focused, direction);
            if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
                if (found == null || found.getId() != R.id.text) {
                    // 💡 如果是向左且沒找到下一個項目，嘗試找場景按鈕
                    if (direction == View.FOCUS_LEFT) {
                        View scenario = getRootView().findViewById(R.id.scenario);
                        if (scenario != null && scenario.getVisibility() == View.VISIBLE) return scenario;
                    }
                    // 💡 如果沒找到且沒場景按鈕，才播放抖動動畫
                    if (getScrollState() == SCROLL_STATE_IDLE) {
                        focused.clearAnimation();
                        focused.startAnimation(shake);
                    }
                    return null;
                }
            }
        }
        return super.focusSearch(focused, direction);
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }
}
