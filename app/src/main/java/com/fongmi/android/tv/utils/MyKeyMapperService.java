package com.fongmi.android.tv.utils;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.util.Log;

public class MyKeyMapperService extends AccessibilityService {

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        // 🚀 核心物理攔截：當使用者放開按鍵時 (ACTION_UP)
        if (event.getAction() == KeyEvent.ACTION_UP) {
            // 您可以先按紅色按鍵，在 Logcat 裡觀察您這支遙控器回傳的真實數字（例如 261 或是其他代碼）
            Log.d("KeyMapper", "🛡️ [無障礙監聽] 遙控器按下 KeyCode: " + keyCode);

            // 💥 精準判定：如果是 Netflix 實體鍵碼 (Chromecast 常見硬體映射通常為特定的媒體鍵)
            if (keyCode == KeyEvent.KEYCODE_BUTTON_3 || keyCode == KeyEvent.KEYCODE_MUSIC) {
                Log.e("KeyMapper", "🛡️ [無障礙攔截] 成功在物理層生吞 Netflix 紅鍵！正在繞過 Android 14 盾牌秒開主程式！");

                // 執行最高優先級的前台 Activity 自我喚醒
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(launchIntent);
                }

                return true; // 💥 關鍵核心：回傳 true，代表這顆按鍵已被我肉體吃掉，禁止系統再跳轉 Google Play！
            }
        }
        return false;
        //return super.onKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}
}
