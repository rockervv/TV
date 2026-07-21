package com.fongmi.android.tv.utils;

import android.Manifest;

import androidx.fragment.app.FragmentActivity;

import com.permissionx.guolindev.PermissionX;
import com.permissionx.guolindev.callback.RequestCallback;

public class PermissionUtil {

    public static void requestFile(FragmentActivity activity, RequestCallback callback) {
        PermissionX.init(activity).permissions(Manifest.permission.WRITE_EXTERNAL_STORAGE).request(callback);
    }
}
