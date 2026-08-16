package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.ResUtil;

public class CustomUpDownView extends AppCompatTextView {

    private UpListener upListener;
    private DownListener downListener;
    private String label;
    private Paint paint;

    public CustomUpDownView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public CustomUpDownView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CustomUpDownView);
        label = a.getString(R.styleable.CustomUpDownView_label);
        a.recycle();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(ResUtil.sp2px(10));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!TextUtils.isEmpty(label)) {
            paint.setTextSize(ResUtil.sp2px(7));
            paint.setColor(Color.WHITE);
            paint.setAlpha(80);
            canvas.drawText(label, ResUtil.dp2px(4), paint.getTextSize() + ResUtil.dp2px(2), paint);
        }
        super.onDraw(canvas);
    }

    public void setUpListener(UpListener upListener) {
        this.upListener = upListener;
    }

    public void setDownListener(DownListener downListener) {
        this.downListener = downListener;
    }

    private boolean hasEvent(KeyEvent event) {
        return event.getAction() == KeyEvent.ACTION_DOWN && ((upListener != null && KeyUtil.isUpKey(event)) || (downListener != null && KeyUtil.isDownKey(event)));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (hasEvent(event)) return onKeyDown(event);
        else return super.dispatchKeyEvent(event);
    }

    private boolean onKeyDown(KeyEvent event) {
        if (upListener != null && KeyUtil.isUpKey(event)) upListener.onUp();
        if (downListener != null && KeyUtil.isDownKey(event)) downListener.onDown();
        return true;
    }

    public interface UpListener {

        void onUp();
    }

    public interface DownListener {

        void onDown();
    }
}
