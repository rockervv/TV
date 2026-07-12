package com.fongmi.android.tv.bean;

import android.os.Build;
import android.view.View;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;

public class Func {

    private final int resId;
    private final int id;
    private int drawable;
    private int nextFocusLeft;
    private int nextFocusRight;

    public static Func create(int resId) {
        return new Func(resId);
    }

    public Func(int resId) {
        this.resId = resId;
        this.id = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 ? View.generateViewId() : -1;
        this.setDrawable();
    }

    public int getResId() {
        return resId;
    }

    public int getId() {
        return id;
    }

    public int getDrawable() {
        return drawable;
    }

    public int getNextFocusLeft() {
        return nextFocusLeft;
    }

    public void setNextFocusLeft(int nextFocusLeft) {
        this.nextFocusLeft = nextFocusLeft;
    }

    public int getNextFocusRight() {
        return nextFocusRight;
    }

    public void setNextFocusRight(int nextFocusRight) {
        this.nextFocusRight = nextFocusRight;
    }

    public String getText() {
        return ResUtil.getString(resId);
    }

    public void setDrawable() {
        if (resId == R.string.home_history_short) {
            this.drawable = R.drawable.ic_home_history;
        } else if (resId == R.string.home_vod) {
            this.drawable = R.drawable.ic_home_vod;
        } else if (resId == R.string.home_live) {
            this.drawable = R.drawable.ic_home_live;
        } else if (resId == R.string.home_keep) {
            this.drawable = R.drawable.ic_home_keep;
        } else if (resId == R.string.home_push) {
            this.drawable = R.drawable.ic_home_push;
        } else if (resId == R.string.home_search) {
            this.drawable = R.drawable.ic_home_search;
        } else if (resId == R.string.home_setting) {
            this.drawable = R.drawable.ic_home_setting;
        }
    }
}
