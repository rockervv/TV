# SmartTube 模組導入與構建修復細節記錄

本文檔詳細記錄了將 SmartTube 核心模組（SharedModules 與 MediaServiceCore）導入 TV 項目時所做的所有構建腳本修改。

## 1. 項目級配置 (Root Settings)

### `settings.gradle`
**修改內容：** 將局部變量改為全局擴展屬性，並定義常數腳本路徑。
```gradle
// 修改前
def sharedModulesRoot = new File(settingsDir, 'SharedModules')
if (sharedModulesRoot.exists()) {
    apply from: new File(sharedModulesRoot, 'core_settings.gradle')
}

// 修改後
gradle.ext.sharedModulesRoot = new File(settingsDir, 'SharedModules')
gradle.ext.sharedModulesConstants = new File(gradle.ext.sharedModulesRoot, 'constants.gradle')
if (gradle.ext.sharedModulesRoot.exists()) {
    apply from: new File(gradle.ext.sharedModulesRoot, 'core_settings.gradle')
}
```

## 2. 子模組管理 (Core Settings)

### `SharedModules/core_settings.gradle`
**修改內容：** 移除無限遞歸邏輯。
```gradle
// 移除以下區塊
gradle.ext.sharedModulesRoot = new File(rootDir, '../SharedModules').exists() ? ...
apply from: new File(gradle.ext.sharedModulesRoot, 'core_settings.gradle')
```

### `MediaServiceCore/core_settings.gradle`
**修改內容：** 移除對 SharedModules 的重複調用與遞歸。
```gradle
// 移除以下區塊
gradle.ext.sharedModulesRoot = new File(rootDir, '../SharedModules').exists() ? ...
apply from: new File(gradle.ext.sharedModulesRoot, 'core_settings.gradle')
```

## 3. 兼容性修復 (AGP 9.2.1 & Kotlin)

### Kotlin 插件移除 (Built-in Kotlin Support)
**涉及文件：**
*   `SharedModules/sharedutils/build.gradle`
*   `MediaServiceCore/youtubeapi/build.gradle`

**修改內容：**
```gradle
// 移除
apply plugin: 'kotlin-android'
```

### Proguard 配置更新 (R8 最佳化)
**涉及文件：** `:appupdatechecker2`, `:sharedutils`, `:sharedtests`, `:j2v8`, `:commons-io-2.8.0`, `:youtubeapi`, `:mediaserviceinterfaces`。

**修改內容：**
```gradle
// 修改前
proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'

// 修改後
proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
```

### 命名空間 (Namespace) 指定
**修改內容：** 在 `android` 區塊頂部添加 `namespace`。
*   `:appupdatechecker2`: `namespace 'com.liskovsoft.appupdatechecker2'`
*   `:sharedutils`: `namespace 'com.liskovsoft.sharedutils'`
*   `:sharedtests`: `namespace 'com.liskovsoft.sharedtests'`
*   `:j2v8`: `namespace 'com.eclipsesource.v8'`
*   `:commons-io-2.8.0`: `namespace 'org.apache.commons.commonsio'`
*   `:youtubeapi`: `namespace 'com.liskovsoft.youtubeapi'`
*   `:mediaserviceinterfaces`: `namespace 'com.liskovsoft.mediaserviceinterfaces'`
*   `:YoutubeSpider`: `namespace 'com.github.catvod.spider'`

## 4. 依賴與變體修復

### `youtubeapi/build.gradle`
**修改內容：** 修正協程庫版本參考。
```gradle
// 修改前
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:' + kotlinVersion

// 修改後 (使用 constants.gradle 中定義的 1.7.3)
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:' + kotlinxVersion
```

### `app/build.gradle` 與 `catvod/build.gradle` 修改
**修改內容：** 解決 Flavor 缺失導致的依賴解析失敗。
由於 SmartTube 模組使用了名為 `default` 的 Flavor Dimension，而主項目沒有定義該維度。必須在所有直接或間接依賴這些模組的組件中指定策略。
```gradle
android {
    defaultConfig {
        ...
        // 新增：強制指定依賴模組的 Flavor (需在 app, catvod, chaquo, quickjs 模組中添加)
        missingDimensionStrategy 'default', 'ststable'
    }
}
```

## 5. 整合 YoutubeSpider 到 :catvod

**修改內容：** 將原 `:YoutubeSpider` 模組的源碼整合至 `:catvod` 模組，使其與原有的 `Youtube` 爬蟲並存以供對比測試。

### 源碼遷移
*   將 `YoutubeSpider.java` 從 `YoutubeSpider` 模組遷移至 `catvod/src/main/java/com/github/catvod/spider/SmartTube.java` 並更名為 `SmartTube` 類。

### `catvod/build.gradle` 修改
*   添加 SmartTube 相關項目的依賴 (`:sharedutils`, `:mediaserviceinterfaces`, `:youtubeapi`)。
*   添加 `rxandroid` 與 `rxjava` 依賴。
*   添加 `missingDimensionStrategy 'default', 'ststable'` 以匹配 SmartTube 變體。

### `SpiderFactory.java` 修改
*   註冊 `youtube_st` 鍵值對應 `SmartTube.class`。

### 模組移除
*   從 `settings.gradle` 中移除 `include ':YoutubeSpider'`。
*   刪除根目錄下的 `YoutubeSpider` 目錄。

## 6. Cronet 命名空間衝突修復

**修改內容：** 解決 `org.chromium.net` 命名空間在多個 Cronet 庫中重複定義導致的 Manifest 合併失敗。

### 依賴統一
由於主項目使用了 Google Play Services 版本的 Cronet (`play-services-cronet`)，而 SmartTube 模組使用了獨立的 Chromium Cronet。這兩者共存時會產生命名空間衝突。已統一改為使用 Play Services 版本，並修正了相關代碼。

**涉及文件：**
*   `SharedModules/sharedutils/build.gradle`
*   `MediaServiceCore/youtubeapi/build.gradle`
*   `SharedModules/sharedutils/src/main/java/com/liskovsoft/sharedutils/cronet/CronetManager.kt`

**修改內容：**
1.  **Gradle**: 將 `org.chromium.net` 依賴替換為 `com.google.android.gms:play-services-cronet:18.0.1`。
2.  **代碼**: 在 `CronetManager.kt` 中，將 `NativeCronetProvider(context).createBuilder()` 替換為標準的 `CronetEngine.Builder(context)`，因為 GMS Cronet 不提供 `NativeCronetProvider` 內部類。

## 7. YouTube 直播與點播 ExoPlayer 解析優化與卡頓修復 (V517 & V518)

**修改內容：** 徹底修復 YouTube 直播與點播在 ExoPlayer 上的解析錯誤 (3002) 與播放中途卡頓問題，確保時間軸線性增長與音畫同步。

### 7.1. 3002 解析錯誤根源修復 (V517)
*   **問題原因：** 原有的 XML 正則清洗邏輯存在引號歧視，導致在特定情況下重複注入 `start="PT0S" start="PT0S"` 非法語法；且正則過於貪婪，會導致標籤結尾被破壞成 `//>`；此外，未妥善支持包含命名空間的標籤（如 `yt:AdaptationSet`），引發 ExoPlayer 3002 解析崩潰。
*   **修正方案：** 重新實作乾淨的 Period 標籤重構邏輯與層級式標籤切分（Hierarchical Sanitize），完全相容單雙引號，且支援 `\w+:AdaptationSet` 命名空間格式，消除語法非法引發的崩潰。

### 7.2. 直播播放中途卡頓與時間軸抖動優化 (V518)
*   **問題原因：** YouTube 直播分片長度不固定（如 5005ms vs 4938ms）。如果每輪 Manifest 刷新都重新計算時間投影，會導致同一個序列號 (SN) 的虛擬時間 `t` 前後不一致，產生「時間倒流」或「時間軸彈簧效應」，迫使 ExoPlayer 清空緩衝重新對齊，造成頻繁的卡頓（Buffering）與 `Audio Sink Discontinuity`。
*   **修正方案：** 實施 **V518 線性投影算法**。在 Session 開始時鎖定每個軌道的基準步長（Locked `baseStep`），強制執行線性時間投影公式：`t = AnchorT + (SN - AnchorSN) * baseStep`。配合 `injectTimelineTFixed` 算法，徹底消除 Manifest 刷新導致的時間抖動。

### 7.3. IDE Duplicate Class Ghost 定義修復
*   **問題原因：** 發現項目中意外存在一個帶有連續點號的錯誤目錄 `app/src/main/java/com.fongmi.android.tv/player/exo/`，其中殘留了 `MediaSourceFactory.java` 的舊版定義，導致 IDE 提示 "Duplicate class" 衝突且代碼版本出現混淆。
*   **修正方案：** 清理並清空該 Ghost 目錄中的類定義，將最新的 V518 鎖定步長核心邏輯統一整合至正確的 nested 目錄結構 `app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java` 中。

---
**狀態：** 所有模組已成功同步，核心解析器已進化至 V518，YouTube 解析與直播中途卡頓問題已獲得徹底修復。
