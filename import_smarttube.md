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

---
**狀態：** 所有模組已成功同步 (Sync finished successfully)。
