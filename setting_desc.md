# 設定畫面說明功能修改計畫

預計在所有設定畫面的底部增加一個固定的說明文字欄位（`TextView`），當焦點移動到各個設定項或圖示時，該欄位會顯示對應的功能描述及操作方式。

## 修改範圍與內容

### 1. SettingActivity (主設定)
*   **影片源 (vod)**: 點擊切換配置 / 長按編輯配置網址
*   **首頁 (vodHome)**: 點擊選擇當前配置下的首頁站台
*   **歷史 (vodHistory)**: 點擊查看該配置的歷史切換記錄
*   **直播源 (live)**: 點擊切換直播配置 / 長按編輯配置網址
*   **直播首頁 (liveHome)**: 點擊選擇直播首頁
*   **直播歷史 (liveHistory)**: 點擊查看直播歷史記錄
*   **桌布源 (wall)**: 點擊切換桌布配置 / 長按編輯配置網址
*   **預設桌布 (wallDefault)**: 點擊切換至下一個預設內建桌布
*   **重新整理桌布 (wallRefresh)**: 點擊立即重新載入當前桌布
*   **快取 (cache)**: 點擊清理應用程式暫存快取 / 長按清理全部快取及重置配置
*   **類別快取 (category_cache)**: 開啟後會快取影片分類列表以加快載入速度
*   **代理伺服器 (proxy)**: 點擊設定全域網路代理
*   **DNS (doh)**: 點擊選擇 DNS over HTTPS 服務
*   **版本 (version)**: 點擊檢查更新 / 長按進入開發者模式
*   **自定義 (custom)**: 點擊進入介面與功能自定義設定 / 長按進入站台測試介面
*   **備份 (backup)**: 點擊立即執行資料庫備份 / 長按切換自動備份模式
*   **還原 (restore)**: 點擊從備份檔案還原資料
*   **播放器 (player)**: 點擊進入播放核心相關設定
*   **爬蟲引擎 (spider)**: 點擊進入爬蟲與指令碼引擎設定
*   **關於 (about)**: 顯示版本與硬體架構資訊
*   **同步設定 (sync_setting)**: 點擊設定遠端同步功能
*   **立即同步 (sync)**: 點擊立即執行所有裝置同步

### 2. SettingPlayerActivity (播放器設定)
*   **播放核心 (engine)**: 切換 ExoPlayer 或 mpv 播放引擎
*   **重置 (reset)**: 切換播放失敗時的處理方式（刷新或重播）
*   **字幕開關 (caption)**: 點擊切換外掛字幕 / 長按進入系統字幕設定
*   **播放速度 (speed)**: 點擊設定全域預設播放倍速
*   **mpv 配置 (mpvConf)**: 僅在 mpv 引擎下可用，自定義 mpv 指令參數
*   **渲染模式 (render)**: 切換影片渲染技術 (Surface/Texture 等)
*   **解碼設定 (decode)**: 進入硬體解碼與通道細項設定

### 3. SettingSpiderActivity (爬蟲設定)
*   **本地爬蟲 (localSpider)**: 選擇預設優先使用的本地爬蟲模組
*   **QuickJS**: 切換 JS 爬蟲引擎開關
*   **Chaquo**: 切換 Python 爬蟲引擎開關

### 4. SettingCustomActivity (自定義設定)
*   **播放倍速 (speed)**: 點擊增加倍速(0.1) / 長按恢復 1.0 倍速
*   **介面風格 (homeUI)**: 切換首頁顯示風格（列表、網格等）
*   **資料重置 (reset)**: 點擊清除所有應用程式數據（慎用）

### 5. 其他 (Decode/Preload)
*   **硬解通道 (tunnel)**: 開啟後在支援的裝置上提供更流暢的 4K 播放
*   **預載開關 (preload)**: 開啟後在播放時提前緩衝下一集數據

## 實作方式
1.  **Layout 修改**: 在 `activity_setting.xml` 等佈局的最下方加入一個 `TextView` (id: `desc`)。
2.  **Java 修改**: 
    *   在 `initView` 中初始化 `desc` TextView。
    *   為所有支援的 `View` 加上 `setOnFocusChangeListener`。
    *   當 `hasFocus` 為 `true` 時，根據 View 的 ID 設置對應的說明文字。
