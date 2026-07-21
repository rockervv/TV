package com.fongmi.android.tv.ui.custom;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public abstract class FragmentStateManager {

    private final FragmentManager fm;
    private final ViewGroup container;

    public FragmentStateManager(ViewGroup container, FragmentManager fm) {
        this.container = container;
        this.fm = fm;
    }

    public abstract Fragment getItem(int position);

    public void change(int position) {
        FragmentTransaction ft = fm.beginTransaction();
        Fragment fragment = fm.findFragmentByTag(String.valueOf(position));
        if (fm.getFragments().size() > 0) {
            for (Fragment f : fm.getFragments()) ft.hide(f);
        }
        if (fragment == null) {
            ft.add(container.getId(), fragment = getItem(position), String.valueOf(position));
        } else {
            ft.show(fragment);
        }
        ft.commitAllowingStateLoss();
    }
}
