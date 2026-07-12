package com.fongmi.android.tv.utils;

import static android.provider.Settings.*;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.github.catvod.Init;
import com.github.catvod.utils.Shell;

import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {
    private static final Pattern EPISODE = Pattern.compile("(?i)(?:ep|第|e|[\\-\\.\\s])\\s?(\\d{1,4})");

    public static void toggleFullscreen(Activity activity, boolean fullscreen) {
        if (fullscreen) hideSystemUI(activity);
        else showSystemUI(activity);
    }

    public static void showSystemUI(Activity activity) {
        showSystemUI(activity.getWindow());
    }

    public static void showSystemUI(Window window) {
        // 1. 獲取現代的 Insets 控制器
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        if (controller != null) {
            // 2. 顯示系統狀態列與導航列（System Bars）
            controller.show(WindowInsetsCompat.Type.systemBars());
        }

        // 3. 針對 Android 9 (API 28) 以下的舊設備，改用不被棄用的全螢幕系統旗標作為相容方案
        //@SuppressWarnings("deprecation")
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        }
    }

    public static void hideSystemUI(Activity activity) {
        hideSystemUI(activity.getWindow());
    }


    public static void hideSystemUI(Window window) {
        // 1. 獲取現代的 Insets 控制器
        WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(window, window.getDecorView());

        if (insets != null) {
            // 2. 設定隱藏模式：設定為滑動時才短暫顯示狀態列（適合影音播放或 TV 介面）
            insets.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            // 3. 隱藏系統狀態列與導航列（System Bars）
            insets.hide(WindowInsetsCompat.Type.systemBars());
        }

        // 4. 針對 Android 9 (API 28) 以下的舊設備，改用不被棄用的全螢幕系統旗標作為相容方案
        //@SuppressWarnings("deprecation")
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LOW_PROFILE
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) App.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        IBinder windowToken = view.getWindowToken();
        if (imm == null || windowToken == null) return;
        imm.hideSoftInputFromWindow(windowToken, 0);
    }

    public static void showKeyboard(View view) {
        if (view == null) return;

        // 1. 先讓元件在畫面上取得焦點
        view.requestFocus();
        if (view.isInTouchMode()) {
            view.requestFocusFromTouch();
        }

        // 💡 稍微延遲，確保主執行緒完成焦點轉移後再處理鍵盤彈出
        view.postDelayed(() -> {
            Context context = view.getContext();
            if (context instanceof Activity) {
                Window window = ((Activity) context).getWindow();

                // 2. 獲取現代的 Insets 控制器
                WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, view);
                if (controller != null) {
                    // 3. 直接呼叫顯示 IME (軟體鍵盤)，這完全取代了舊版的 showSoftInput 和 toggleSoftInput
                    controller.show(WindowInsetsCompat.Type.ime());
                    return; // 成功喚起最新 API，直接結束
                }
            }

            // 4. 降級相容方案：如果拿不到 Window 或是舊版系統，回退到基礎的 InputMethodManager
            // 僅使用未被棄用的 SHOW_IMPLICIT 旗標
            @SuppressWarnings("deprecation")
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
    }


    public static float getBrightness(Activity activity) {
        try {
            float value = activity.getWindow().getAttributes().screenBrightness;
            if (WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL >= value && value >= WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF) return value;
            return Settings.System.getFloat(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 128;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    public static CharSequence getClipText() {
        ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = manager == null ? null : manager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) return "";
        return clipData.getItemAt(0).getText();
    }

    public static void copy(String text) {
        try {
            ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("", text));
            Notify.show(R.string.copied);
        } catch (Exception e) {
            Log.d ("Util", "Copy error:" + e);
        }
    }

    public static int getDigit(String text) {
        try {
            if (text.startsWith("上") || text.startsWith("下")) return -1;
            return Integer.parseInt(text.replaceAll("(?i)(mp4|H264|H265|720p|1080p|2160p|4K)", "").replaceAll("\\D+", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    public static int getNumber(String text) {
        try {
            text = text.replaceAll("\\[.*?\\]|\\(.*?\\)", "");
            text = text.replaceAll("\\b(19|20)\\d{2}\\b", "");
            text = text.toLowerCase().replaceAll("2160p|1080p|720p|480p|4k|h26[45]|x26[45]|mp4", "");
            Matcher matcher = EPISODE.matcher(text);
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
            String number = text.replaceAll("\\D+", "");
            return number.isEmpty() ? -1 : Integer.parseInt(number);
        } catch (Exception e) {
            return -1;
        }
    }

    public static String clean(String text) {
        if (!text.contains("<")) return text;
        StringBuilder sb = new StringBuilder();
        text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().replace("\u00A0", " ").replace("\u3000", " ");
        for (String line : text.split("\\r?\\n")) sb.append(line.trim()).append("\n");
        return substring(sb.toString()).trim();
    }

    public static String getAndroidId() {
        try {
            String id = Secure.getString(Init.context().getContentResolver(), Secure.ANDROID_ID);
            if (TextUtils.isEmpty(id)) throw new NullPointerException();
            return id;
        } catch (Exception e) {
            return "0000000000000000";
        }
    }


    public static String getMac(String name) {
        try {
            StringBuilder sb = new StringBuilder();
            NetworkInterface nif = NetworkInterface.getByName(name);
            if (nif.getHardwareAddress() == null) return "";
            for (byte b : nif.getHardwareAddress()) sb.append(String.format("%02X:", b));
            return substring(sb.toString());
        } catch (Exception e) {
            return "";
        }
    }
    public static String getDeviceName() {
        String model = Build.MODEL;
        String manufacturer = Build.MANUFACTURER;
        return model.startsWith(manufacturer) ? model : manufacturer + " " + model;
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) return text.substring(0, text.length() - num);
        return text;
    }

    public static long format(SimpleDateFormat format, String src) {
        try {
            return Objects.requireNonNull(format.parse(src)).getTime();
        } catch (Exception e) {
            return 0;
        }
    }


    public static boolean isLeanback() {
        return "leanback".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isMobile() {
        return "mobile".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isFullscreen(Activity activity) {
        if (activity == null || activity.getWindow() == null) return false;

        // 如果是 Android TV (Leanback)，依據您的專案邏輯直接判定為全螢幕
        if (isLeanback()) return true;

        // 1. 現代寫法：針對 Android 11 (API 30) 以上，檢查系統狀態列（Status Bars）是否被隱藏
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            View decorView = activity.getWindow().getDecorView();
            if (decorView.getRootWindowInsets() != null) {
                // 如果狀態列（statusBars）是隱藏（!isVisible）的，即代表處於全螢幕狀態
                return !decorView.getRootWindowInsets().isVisible(android.view.WindowInsets.Type.statusBars());
            }
        }

        // 2. 降級相容：針對 Android 10 以下的舊設備，繼續維持原有的位元運算檢查
        // 這裡加上 @SuppressWarnings("deprecation") 讓編譯器知道我們是在為舊系統做向下相容，從而消除全案警告
        @SuppressWarnings("deprecation")
        int flags = activity.getWindow().getAttributes().flags;
        return (flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }


    public static boolean isFullscreenLand(Activity activity) {
        return isFullscreen(activity) && !isLeanback() && ResUtil.isLand(activity);
    }

    public static String format(SimpleDateFormat format, long time) {
        try {
            return format.format(time);
        } catch (Exception e) {
            return "";
        }
    }

    public static String format(StringBuilder builder, Formatter formatter, long timeMs) {
        try {
            return androidx.media3.common.util.Util.getStringForTime(builder, formatter, timeMs);
        } catch (Exception e) {
            return "";
        }
    }

    public static String timeMs(long timeMs) {
        StringBuilder sb = new StringBuilder();
        return format(sb, new Formatter(sb, Locale.getDefault()), timeMs);
    }

    public static Intent getChooser(Intent intent) {
        List<ComponentName> components = new ArrayList<>();
        for (ResolveInfo resolveInfo : App.get().getPackageManager().queryIntentActivities(intent, 0)) {
            String pkgName = resolveInfo.activityInfo.packageName;
            if (pkgName.equals(App.get().getPackageName())) {
                components.add(new ComponentName(pkgName, resolveInfo.activityInfo.name));
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Intent.createChooser(intent, null).putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, components.toArray(new Parcelable[]{}));
        } else {
            return Intent.createChooser(intent, null);
        }
    }

    public static boolean hasSAFChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        return intent.resolveActivity(App.get().getPackageManager()) != null;
    }

    public static boolean isTvBox() {
        PackageManager pm = App.get().getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN) && !pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            return true;
        }
        if (pm.hasSystemFeature("amazon.hardware.fire_tv")) {
            return true;
        }
        if (!hasSAFChooser()) {
            return true;
        }
        if (Build.VERSION.SDK_INT < 30) {
            if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                return true;
            }
            if (pm.hasSystemFeature("android.hardware.hdmi.cec")) {
                return true;
            }
            return Build.MANUFACTURER.equalsIgnoreCase("zidoo");
        }
        return false;
    }

    public static int batteryLevel() {
        BatteryManager batteryManager = (BatteryManager) App.get().getSystemService(Context.BATTERY_SERVICE);
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    public static void restartApp(Activity activity) {
        Intent intent = activity.getBaseContext().getPackageManager().getLaunchIntentForPackage(activity.getBaseContext().getPackageName());
        ComponentName componentName = Objects.requireNonNull(intent).getComponent();
        Intent mainIntent = Intent.makeRestartActivityTask(componentName);
        activity.startActivity(mainIntent);
        Runtime.getRuntime().exit(0);
    }

}
