package com.fongmi.android.tv.utils;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationManagerCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.databinding.ViewProgressBinding;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.view.Window;
import android.view.WindowManager;


public class Notify {
    private AlertDialog mTopDialog;
    private Runnable mShowRunnable;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mDismissRunnable = Notify::dismissTop;

    public static final String DEFAULT = "default";
    public static final int ID = 9527;
    private AlertDialog mDialog;
    private Toast mToast;

    private static class Loader {
        static volatile Notify INSTANCE = new Notify();
    }

    private static Notify get() {
        return Loader.INSTANCE;
    }

    public static void createChannel() {
        NotificationManagerCompat notifyMgr = NotificationManagerCompat.from(App.get());
        notifyMgr.createNotificationChannel(new NotificationChannelCompat.Builder(DEFAULT, NotificationManagerCompat.IMPORTANCE_LOW).setName("TV").build());
    }

    public static String getError(int resId, Throwable e) {
        if (TextUtils.isEmpty(e.getMessage())) return ResUtil.getString(resId);
        return ResUtil.getString(resId) + "\n" + e.getMessage();
    }

    public static void show(Notification notification) {
        if (ActivityCompat.checkSelfPermission(App.get(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManagerCompat.from(App.get()).notify(ID, notification);
    }

    public static void show(int resId) {
        if (resId != 0) show(ResUtil.getString(resId));
    }

    public static void show(String text) {
        get().makeText(text, Gravity.BOTTOM);
    }


    public static void showTop(Context context, String text) {
        dismissTop();
        get().showDialog(context, text);
    }

    private void showDialog(Context context, String message) {
        if (TextUtils.isEmpty(message) || !(context instanceof Activity)) return;
        mHandler.post(mShowRunnable = () -> {
            try {
                TextView tv = createView(message, 12, Color.parseColor("#66000000"));
                mTopDialog = new MaterialAlertDialogBuilder(context).setView(tv).create();
                Window window = mTopDialog.getWindow();
                if (window != null) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    window.setBackgroundDrawableResource(android.R.color.transparent);
                    WindowManager.LayoutParams lp = window.getAttributes();
                    lp.gravity = Gravity.TOP | Gravity.END;
                    lp.x = ResUtil.dp2px(16);
                    lp.y = ResUtil.dp2px(16);
                    window.setAttributes(lp);
                }
                mTopDialog.show();
                mHandler.postDelayed(mDismissRunnable, 3500);
            } catch (Exception ignored) {
            }
        });
    }

    public static void dismissTop() {
        try {
            get().mHandler.removeCallbacks(get().mShowRunnable);
            get().mHandler.removeCallbacks(get().mDismissRunnable);
            if (get().mTopDialog != null && get().mTopDialog.isShowing()) {
                get().mTopDialog.dismiss();
            }
        } catch (Exception ignored) {
        }
    }


    public static void progress(Context context) {
        dismiss();
        get().create(context);
    }

    public static void dismiss() {
        try {
            if (get().mDialog != null) get().mDialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    private void create(Context context) {
        ViewProgressBinding binding = ViewProgressBinding.inflate(LayoutInflater.from(context));
        mDialog = new MaterialAlertDialogBuilder(context).setView(binding.getRoot()).create();
        mDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        mDialog.show();
    }


    private void makeText(String message, int gravity) {
        makeText(message, gravity, 16, Color.parseColor("#CC000000"));
    }

    private void makeText(String message, int gravity, int size, int color) {
        if (mToast != null) mToast.cancel();
        if (TextUtils.isEmpty(message)) return;
        int xOffset = gravity == (Gravity.TOP | Gravity.END) ? 0 : ResUtil.dp2px(16);
        int yOffset = gravity == (Gravity.TOP | Gravity.END) ? 0 : ResUtil.dp2px(16);
        mToast = new Toast(App.get());
        mToast.setDuration(Toast.LENGTH_LONG);
        mToast.setView(createView(message, size, color));
        if (gravity != Gravity.BOTTOM) mToast.setGravity(gravity, xOffset, yOffset);
        mToast.show();
    }

    public static void showTop(String text) {
        get().makeText(text, Gravity.TOP | Gravity.END, 12, Color.parseColor("#66000000"));
    }

    private TextView createView(String message, int size, int color) {
        TextView tv = new TextView(App.get());
        tv.setText(message);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        tv.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(8), ResUtil.dp2px(12), ResUtil.dp2px(8));
        tv.setBackground(getBackground(color));
        return tv;
    }

    private GradientDrawable getBackground(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(ResUtil.dp2px(8));
        shape.setColor(color);
        return shape;
    }

}
