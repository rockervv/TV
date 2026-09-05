# SmartTube Spider 整合與 YouTube 直播流優化紀錄

本文件鉅細靡遺地紀錄了為了在 TV App 中實現穩定、無廣告且不報 403 錯誤的 YouTube 直播播放，針對各個模組所進行的修改。

---

## 1. 核心目標
解決 YouTube 直播在第三方播放器（ExoPlayer）上的三大難點：
- **403 Forbidden**: YouTube 對 HLS (.m3u8) 實施了嚴格的 UA + IP + Session 綁定。
- **格式誤判**: DASH (.mpd) 清單常被錯誤地當作文字 M3U8 代理處理，導致二進制損壞。
- **播放卡頓**: YouTube DASH 分段極碎（約 5 秒），預設緩衝策略會導致片段切換時頻繁緩衝。

---

## 2. 修改檔案清單與原因

### A. 爬蟲層 (Spider)
#### 檔案：`catvod/src/main/java/com/github/catvod/spider/SmartTube.java`
- **修改內容**：
    1. **強攻 DASH 策略**：實作循環切換 `YouTubeServiceManager` 客戶端的邏輯（最多 5 次），直到獲取到 `.mpd` 網址。因為 DASH 在電視端協議中比 HLS 穩定得多。
    2. **動態 UA 注入**：不再硬編碼 User-Agent，而是從 `formatInfo` 提取解析時真實使用的 UA。這是解決 403 的關鍵，確保「解析」與「播放」身份 100% 一致。
    3. **精準 Referer**：根據 UA 自動切換 `Referer`。電視端協議使用 `youtube.com/tv`，網頁端使用 `youtube.com/`。
    4. **補全標頭**：增加 `Origin` 與 `Accept-Encoding: gzip` 模擬官方行為。

---

### B. 提取層 (Extractor)
#### 檔案：`app/src/main/java/com/fongmi/android/tv/player/extractor/Youtube.java`
- **修改內容**：
    1. **橋接 SmartTube**：將原有的 API 解析邏輯攔截，優先調用 `SmartTube.playerContent()`。
    2. **明確標記 MimeType**：接收 SmartTube 返回的格式，若是 DASH 則強制設置 `MimeTypes.APPLICATION_MPD`。這防止了後續播放器將其誤判為常規影片網址。
    3. **標頭同步**：將 SmartTube 產生的 Headers 完整合併到 `Result` 物件中，傳遞給 ExoPlayer。

---

### C. 媒體規範層 (Media Spec)
#### 檔案：`app/src/main/java/com/fongmi/android/tv/player/media/PlaySpec.java`
- **修改內容**：
    1. **攔截代理陷阱**：在 `checkProxy()` 方法中，增加 `url.contains("googlevideo.com")` 的判斷。
    2. **原因**：YouTube 的 DASH 清單通常是 GZIP 壓縮的二進制數據。專案中的 `M3U8.java` 代理會嘗試將其作為「文字」讀取並進行廣告過濾，這會徹底損毀 DASH 數據，導致 `3002 (MANIFEST_MALFORMED)` 錯誤。
    3. **解決**：強制 YouTube 網址直連，不經過本地代理。

---

### D. 播放器核心層 (Player Engine)
#### 檔案：`app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java`
- **修改內容**：
    1. **自定義 LoadControl**：使用 `DefaultLoadControl.Builder` 重新定義緩衝參數。
        - `setBufferDurationsMs(20000, 60000, 2500, 5000)`：確保播放器始終嘗試預載至少 20 秒（約 4 個片段）的數據，避免分段切換時出現卡頓。
    2. **啟用 BackBuffer**：`setBackBuffer(15000, true)`。
    3. **原因**：YouTube 直播分段約 5 秒一跳。若緩衝深度不足，播放器會因追到直播邊緣（Edge）而頻繁進入 Loading 狀態。

#### 檔案：`app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java`
- **修改內容**：
    1. **手動設置直播偏移量**：設定 `defaultMediaSourceFactory.setLiveTargetOffsetMs(20000)`。
    2. **原因**：這是解決「每 5 秒卡頓一次」的核心方案。YouTube Live 的分段為 5s，如果播放點離直播頭太近（例如預設的幾秒），播放器手裡就沒有可預載的下一個片段。強迫播放器「後退」20 秒，使其手裡始終有 3-4 個緩衝片段，從而實現無感預載。

---

### E. YouTube API 核心 (Service)
#### 檔案：`MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/videoinfo/V2/VideoInfoService.java`
- **修改內容**：
    1. **重排客戶端優先權**：將 `ANDROID_VR` 和 `TV_DOWNGRADED` 移至 `VIDEO_INFO_TYPE_LIST` 的首位。
    2. **原因**：這兩個客戶端返回的 DASH 數據包含最完整的音視頻軌道資訊，且直播窗口（DVR）相對較長，最適合 ExoPlayer 進行長時間穩定播放。

---

## 3. 整合後的工作流
1. `LiveActivity` 發起請求 -> `Youtube.java` 攔截 videoId。
2. `SmartTube.java` 啟動 -> 循環嘗試不同客戶端 -> 獲取 DASH (.mpd)。
3. `SmartTube` 傳回正確 UA 與 DASH URL -> `Youtube.java` 標記為 MPD 格式。
4. `PlaySpec` 判斷為 YouTube 網址 -> 繞過本地代理 -> ExoPlayer 直連。
5. `ExoUtil` 配置的深度緩衝啟動 -> 平滑下載各個 5 秒分段。
6. **最終結果**：秒開、無 403 報錯、片段切換無感流暢。

---

**紀錄日期**：2026-09-02
**維護者**：AI Assistant / Expert Android Developer
