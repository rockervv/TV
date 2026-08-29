package com.fongmi.android.tv.utils;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.fongmi.android.tv.R;

import java.util.HashMap;
import java.util.Map;

public class DescHelper {

    private final Map<Integer, String> descriptions;
    private final View card;
    private final TextView text;

    public static DescHelper create(View card, TextView text) {
        return new DescHelper(card, text);
    }

    private DescHelper(View card, TextView text) {
        this.descriptions = new HashMap<>();
        this.card = card;
        this.text = text;
    }

    public DescHelper put(int id, int resId) {
        return put(id, ResUtil.getString(resId));
    }

    public DescHelper put(int id, String desc) {
        descriptions.put(id, desc);
        return this;
    }

    public void bind(ViewGroup root) {
        bindInternal(root);
    }

    private void bindInternal(ViewGroup group) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            if (view instanceof ViewGroup) {
                bindInternal((ViewGroup) view);
            }
            if (descriptions.containsKey(view.getId())) {
                View.OnFocusChangeListener existing = view.getOnFocusChangeListener();
                view.setOnFocusChangeListener((v, hasFocus) -> {
                    if (existing != null) existing.onFocusChange(v, hasFocus);
                    onFocusChange(v, hasFocus);
                });
            }
        }
    }

    public void show(int resId) {
        show(ResUtil.getString(resId));
    }

    public void show(String desc) {
        text.setText(desc);
        card.animate().alpha(1f).setDuration(250).start();
    }

    public void hide() {
        card.animate().alpha(0f).setDuration(250).start();
    }

    private void onFocusChange(View v, boolean hasFocus) {
        if (hasFocus) {
            show(descriptions.get(v.getId()));
        } else {
            hide();
        }
    }
}
