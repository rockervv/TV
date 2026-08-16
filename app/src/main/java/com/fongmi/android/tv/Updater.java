package com.fongmi.android.tv;

import android.app.Activity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.databinding.DialogUpdateBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Download;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Github;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Updater implements Download.Callback {

    private DialogUpdateBinding binding;
    private AlertDialog dialog;
    private List<Apk> apks;
    private Apk selected;
    private WeakReference<Activity> activity;
    private Callback callback;
    private boolean dev;

    public interface Callback {
        void onEnd();
    }

    private static class Loader {
        static volatile Updater INSTANCE = new Updater();
    }

    public static Updater get() {
        return Loader.INSTANCE;
    }

    private File getFile() {
        File file = Path.externalCache("update.apk");
        if (file.exists()) file.delete();
        return file;
    }

    private String getJson() {
        return Github.getJson(dev, "leanback");
    }

    private String getApkUrl(String flavor) {
        return Github.getApk(dev, flavor);
    }

    private String getCurrentFlavor() {
        return "leanback-" + BuildConfig.FLAVOR_api + "-" + BuildConfig.FLAVOR_abi;
    }

    public Updater force() {
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public Updater release() {
        this.dev = false;
        return this;
    }

    public Updater dev() {
        this.dev = true;
        return this;
    }

    private Updater check() {
        dismiss();
        return this;
    }

    public void start(Activity activity) {
        start(activity, null);
    }

    public void start(Activity activity, Callback callback) {
        this.activity = new WeakReference<>(activity);
        this.callback = callback;
        Task.execute(() -> doInBackground(activity));
    }

    private boolean need(int code, String name) {
        Log.d("Updater", name + " code: " + code);
        return Setting.getUpdate() && (dev ? !name.equals(BuildConfig.VERSION_NAME) && code >= BuildConfig.VERSION_CODE : code > BuildConfig.VERSION_CODE);
    }

    private void doInBackground(Activity activity) {
        Github.URL = "https://rockervv.duckdns.org";
        String ip = com.github.catvod.utils.Util.getIp();
        if (ip.startsWith("192.168.68.")) {
            try (okhttp3.Response res = OkHttp.newCall(OkHttp.client(1000), "http://192.168.68.81").execute()) {
                if (res.isSuccessful()) Github.URL = "http://192.168.68.81";
            } catch (Exception ignored) {
            }
        }
        String url = getJson();
        try {
            String data = OkHttp.string(url);
            JSONObject object = Json.safeJSONObject(data);
            String name = object.optString("name");
            String desc = object.optString("desc");
            int code = object.optInt("code");
            this.apks = Apk.arrayFrom(object.optString("apks"));
            this.selected = findDefaultApk(object.optString("md5"));
            Log.d("Updater", "URL: " + Github.URL + " name:[" + name + "] code: " + code + " MD5: " + selected.md5 + ", desc:\n" + desc + "\n");
            if (need(code, name)) App.post(() -> show(activity, name, desc));
            else App.post(this::onEnd);
        } catch (Exception e) {
            Log.d("Updater", url + " error: " + e);
            App.post(this::onEnd);
        }
    }

    private Apk findDefaultApk(String defaultMd5) {
        String current = getCurrentFlavor();
        for (Apk apk : apks) {
            if (apk.flavor.equals(current)) return apk;
        }
        Apk apk = new Apk();
        apk.flavor = current;
        apk.md5 = defaultMd5;
        return apk;
    }

    private void show(Activity activity, String version, String desc) {
        binding = DialogUpdateBinding.inflate(LayoutInflater.from(activity));
        binding.version.setText(ResUtil.getString(R.string.update_version, version));
        binding.server.setText("Server: " + Github.URL);
        binding.server.setVisibility(View.VISIBLE);
        binding.more.setVisibility(apks.size() > 1 ? View.VISIBLE : View.GONE);
        binding.confirm.setOnClickListener(this::confirm);
        binding.more.setOnClickListener(v -> onMore(activity));
        binding.cancel.setOnClickListener(this::cancel);
        check().create(activity).show();
        binding.desc.setText(desc);
    }

    private void onMore(Activity activity) {
        String[] items = new String[apks.size()];
        for (int i = 0; i < apks.size(); i++) items[i] = apks.get(i).getName();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_more)
                .setItems(items, (d, which) -> {
                    selected = apks.get(which);
                    confirm(null);
                }).show();
    }

    private AlertDialog create(Activity activity) {
        return dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).setCancelable(false).create();
    }

    private void cancel(View view) {
        Setting.putUpdate(false);
        dismiss();
        onEnd();
    }

    private void confirm(View view) {
        File file = getFile();
        if (!TextUtils.isEmpty(selected.md5) && file.exists() && com.github.catvod.utils.Util.md5(file).equalsIgnoreCase(selected.md5)) {
            Log.d("Updater", "Local file MD5 match! Skipping download.");
            success(file);
            return;
        }
        binding.confirm.setEnabled(false);
        binding.more.setEnabled(false);
        binding.cancel.setVisibility(View.GONE);
        binding.speed.setVisibility(View.VISIBLE);
        Download.create(getApkUrl(selected.flavor), file, this).start();
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    private void onEnd() {
        if (callback != null) callback.onEnd();
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void progress(int progress, String speed) {
        binding.confirm.setText(progress >= 0 ? String.format(Locale.getDefault(), "%1$d%%", progress) : "下載中...");
        binding.speed.setText("Speed: " + speed);
    }

    @Override
    public void error(String msg) {
        Log.e("Updater", "Download error: " + msg);
        Notify.show(msg);
        dismiss();
        onEnd();
    }

    @Override
    public void success(File file) {
        Log.d("Updater", "Download success: " + file.getAbsolutePath());
        if (!TextUtils.isEmpty(selected.md5) && !com.github.catvod.utils.Util.md5(file).equalsIgnoreCase(selected.md5)) {
            Log.e("Updater", "MD5 mismatch! Downloaded: " + com.github.catvod.utils.Util.md5(file) + " Expected: " + selected.md5);
            Notify.show("檔案驗證失敗，請重試");
            file.delete();
            dismiss();
            onEnd();
            return;
        }
        App.post(() -> {
            Log.d("Updater", "Opening file for installation...");
            FileUtil.openFile(activity != null ? activity.get() : null, file);
            App.post(() -> {
                Log.d("Updater", "Dismissing dialog. Not calling onEnd to avoid auto-resume competition.");
                dismiss();
            }, 6000);
        }, 500);
    }

    private static class Apk {
        @SerializedName("name")
        private String name;
        @SerializedName("flavor")
        private String flavor;
        @SerializedName("md5")
        private String md5;

        public String getName() {
            return TextUtils.isEmpty(name) ? flavor : name;
        }

        static List<Apk> arrayFrom(String str) {
            List<Apk> items = App.gson().fromJson(str, new TypeToken<List<Apk>>() {}.getType());
            return items == null ? new ArrayList<>() : items;
        }
    }
}
