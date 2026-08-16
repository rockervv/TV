package com.fongmi.android.tv;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
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
    private boolean checking;

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
        return new File(Path.download(), "update.apk");
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
        if (isShowing() || checking) return;
        this.checking = true;
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
            App.post(() -> checking = false);
            if (need(code, name)) App.post(() -> show(activity, name, desc));
            else App.post(this::onEnd);
        } catch (Exception e) {
            Log.d("Updater", url + " error: " + e);
            App.post(() -> checking = false);
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
        binding.confirm.requestFocus();
        binding.desc.setText(desc);
    }

    private void onMore(Activity activity) {
        String[] names = new String[apks.size()];
        for (int idx = 0; idx < apks.size(); idx++) names[idx] = apks.get(idx).getName();
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_more)
                .setItems(names, (d, which) -> {
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
        binding.confirm.setEnabled(false);
        binding.more.setEnabled(false);
        binding.cancel.setVisibility(View.GONE);
        binding.speed.setVisibility(View.VISIBLE);
        binding.speed.setText("正在校驗本地檔案...");
        Task.execute(() -> {
            File file = getFile();
            String localMd5 = file.exists() ? com.github.catvod.utils.Util.md5(file) : "";
            App.post(() -> {
                Log.d("Updater", "Check Local MD5: " + localMd5 + " | Expected: " + selected.md5);
                if (!TextUtils.isEmpty(selected.md5) && file.exists() && localMd5.equalsIgnoreCase(selected.md5)) {
                    Log.d("Updater", "Local file MD5 match! Skipping download.");
                    binding.speed.setText("本地檔案校驗成功，準備安裝...");
                    success(file);
                } else {
                    if (file.exists()) file.delete();
                    binding.server.setText("正在下載: " + selected.getName());
                    Download.create(getApkUrl(selected.flavor), file, this).start();
                }
            });
        });
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
        binding.speed.setText("正在最終驗證檔案...");
        Task.execute(() -> {
            String downloadMd5 = com.github.catvod.utils.Util.md5(file);
            App.post(() -> {
                if (!TextUtils.isEmpty(selected.md5) && !downloadMd5.equalsIgnoreCase(selected.md5)) {
                    Log.e("Updater", "MD5 mismatch! Downloaded: " + downloadMd5 + " Expected: " + selected.md5);
                    binding.speed.setText("MD5 比對失敗，請重試");
                    Notify.show("檔案驗證失敗，請重試");
                    file.delete();
                    dismiss();
                    onEnd();
                } else {
                    binding.speed.setText("驗證成功，準備安裝...");
                    App.post(() -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !App.get().getPackageManager().canRequestPackageInstalls()) {
                            Notify.show("請開啟安裝未知應用程式權限");
                            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                            intent.setData(Uri.parse("package:" + App.get().getPackageName()));
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            App.get().startActivity(intent);
                            dismiss();
                        } else {
                            binding.speed.setText("正在啟動安裝器...");
                            Log.d("Updater", "Opening file for installation...");
                            FileUtil.openFile(activity != null ? activity.get() : App.activity(), file);
                            App.post(this::dismiss, 3000);
                        }
                    }, 800);
                }
            });
        });
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
