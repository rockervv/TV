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
    1. **強攻 DASH 策略**：實作循環切換 `YouTubeServiceManager` 客戶端的邏輯（最多 5 次），直到獲取到 `.mpd` 網址。
    2. **動態 UA 注入**：不再硬編碼 User-Agent，而是從 `formatInfo` 提取解析時真實使用的 UA。
    3. **精準 Referer**：根據 UA 自動切換 `Referer`。

---

### B. 提取層 (Extractor)
#### 檔案：`app/src/main/java/com/fongmi/android/tv/player/extractor/Youtube.java`
- **修改內容**：
    1. **橋接 SmartTube**：將原有的 API 解析邏輯攔截，優先調用 `SmartTube.playerContent()`。
    2. **明確標記 MimeType**：接收 SmartTube 返回的格式，若是 DASH 則強制設置 `MimeTypes.APPLICATION_MPD`。

---

### C. 進階優化：解決 YouTube Live DASH 時間軸抖動 (Timeline Jitter)
YouTube DASH 直播每隔 5 秒刷新一次 Manifest，其 AvailabilityStartTime (AST) 與片段起始時間會發生毫秒級的隨機偏移。這會導致 ExoPlayer 認為時間軸「斷裂」，觸發回跳、卡死或意外進入 `STATE_ENDED` 狀態。

#### 檔案：`app/src/main/java/com/fongmi/android/tv/player/exo/MediaSourceFactory.java`

| 測試版本 | 核心策略 | 關鍵 Log 反饋 (實際測試) | 最終狀態 | 失敗/改進原因分析 |
| :--- | :--- | :--- | :--- | :--- |
| **V58.0** | 空間冷凍 (Space-Time Freeze) | `Buffer: -5002ms` | **失敗 (重載)** | Buffer 為負值導致一進入 READY 就判定 ENDED。 |
| **V59.0** | 邊緣精準對齊法 | `Buffer: 4999ms` | **失敗 (卡死)** | 物理緩衝低於 `minBufferMs (20s)`，永遠在 BUFFERING。 |
| **V61.0** | 無限 DVR 與主動填補 | `Pos: 600001` (Window=600000) | **失敗 (結束)** | Pos 超出 Window 1ms 觸發 STATE_ENDED。 |
| **V62.0** | 銀河級擴張 (24h視窗) | `PlaybackState: 3 -> 4` | **失敗 (秒關)** | `durationMs` 設為 -1 被解析為 1ms，起播即結束。 |
| **V63.0** | 穩定座標對齊 | `From: -7079990 To: 0` | **失敗 (黑屏)** | 虛擬座標與影片內部 PTS 脫節，解碼器抓不到幀。 |
| **V64.0** | 網格鎖定法 (Native Sync) | `TotalBuffer: -1204112ms` | **失敗 (停滯)** | 原始 AST 座標太舊，導致播放點領先數據 20 分鐘。 |
| **V65.0** | 自癒式動態錨點 | `TotalBuffer: 89ms` | **失敗 (卡死)** | 焊接點太緊，Pos 追上數據末端導致 Buffer 歸零。 |
| **V66.0** | 自適應動態焊接 | `TotalBuffer: 29999ms` | **失敗 (斷層)** | **真空斷層**：數據起始點與播放頭 (Now-30s) 有 40s 空隙，導致播完一段後卡死。 |
| **V69.0** | 獨立軌道焊接 | `TotalBuffer: 94ms` | **失敗 (水位枯竭)** | 緩衝水位線與 Manifest 更新頻率衝突。5s 數據在 5s 的更新間隙內被耗盡。 |
| **V70.0** | 雙向極限擴張 | `TotalBuffer: 60000ms` | **失敗 (銜接斷裂)** | 雖然虛擬緩衝大，但 `makeContinuous` 算法與真實 SQ 脫節，導致切換 Period 時報 Discontinuity。 |
| **V71.0** | 穩定持續映射 (Stable Continuous) | `Buffer: 34999ms` | **失敗 (水位枯竭)** | 錨點設在 60s 前，延遲 30s。播放一段時間後，因 YouTube 片段更新趕不上消耗速度而卡頓。 |
| **V72.0** | 深錨座標偏移 (Deep Anchor) | `PlaybackState: 2 (Buffering)` | **失敗 (卡死)** | 錨點設在 120s 前，延遲 60s。Playhead 落在 Window 起始點，導致啟播即卡在緩衝狀態。 |
| **V73.0** | 安全播放頭對齊 (Safe Alignment) | `PlaybackState: 2 (Buffering)` | **失敗 (不穩定)** | 雖然嘗試居中對齊，但 YouTube 片段抖動仍導致播放頭偶爾出界。 |
| **V74.0** | 自調適閉環網格 (Self-Adaptive) | `PlaybackState: 2 (Buffering)` | **失敗 (延遲漂移)** | Delay 不斷增加（20s->40s），把播放頭壓在緩衝區末尾，導致水位永遠不足。 |
| **V75.0** | 領先邊緣錨定 (Lead-Edge) | `PlaybackState: 2 (Buffering)` | **失敗 (座標脫節)** | AST 設為 10 分鐘前，但虛擬數據只填補了 90s，播放頭落在無數據區間 (500s+)。 |
| **V76.0** | 極簡週期統一 (Minimalist) | `PlaybackState: 2 (Buffering)` | **失敗 (播放頭落後)** | **原因**：YouTube 數據僅 15s，Delay 設為 30s，導致播放點落在數據區間之前。 |
| **V77.0** | 動態居中自適應 (Center-Align) | `PlaybackState: 2 (Buffering)` | **失敗 (時間軸抖動)** | 隨系統時間計算 PTO 導致座標每秒都在變動，ExoPlayer 無法維持緩衝水位。 |
| **V78.0** | 首段錨定鎖定 (First-Seg Lock) | `PlaybackState: 2 (Buffering)` | **失敗 (視窗太窄)** | 僅保留最後 Period (約 15s)，數據量不足以填滿 ExoPlayer 的水位線。 |
| **V79.0** | 持久化視窗縫合 (Persistent Window) | `Buf: 94ms` | **失敗 (水位枯竭)** | 緩衝水位線與 Manifest 更新頻率衝突。5s 數據在 5s 的更新間隙內被耗盡。 |
| **V80.0** | AST 回推對齊法 (Back-Calculated AST) | `Pos: 15016 Buf: -1ms` | **失敗 (啟播即卡)** | AST 設為 Now-15s 過於激進，導致播放頭在 Manifest 邊緣反覆橫跳。 |
| **V81.0** | 混合精準焊接 (Hybrid Precision) | `Buf: -4826ms` | **失敗 (解碼出錯)** | AST 錨定在 T-60s 但首段 VirtualT 過大且不從0開始，導致 PTS 脫節。 |
| **V82.0** | 歸零對齊法 (Zero-Base Align) | `Buf: 5015ms` | **失敗 (10秒後卡死)** | 強制 t=0 與片段內部 PTS 衝突，解碼器報 frameIndex not found。 |
| **V83.0** | 原始時戳透傳 (PTS-Sync) | `Buf: -4978ms` | **失敗 (立刻轉圈)** | 未設置 PTO 導致 Manifest 坐標與 AST 脫節，播放頭落在數據區外。 |
| **V84.0** | PTO 全局對齊 (PTO-Alignment) | `FirstT: 0` | **失敗 (畫質卡頓)** | getLongSafe 獲取 T 失敗（得到0），導致 PTO=0 與實際 PTS 衝突。 |
| **V85.0** | 反射強化與動態視窗 (Robust Window) | `FirstT: 0` | **失敗 (畫質卡頓)** | 反射路徑仍無法穿透 package-private，獲取 PTS 失敗導致轉圈。 |
| **V87.0** | 黑盒子深度探測 (PTS-Extraction) | `Buf: -4880ms` | **失敗 (畫面凍結)** | 視窗太窄且 AST 持續漂移。播完初始數據後 Pos 超出 Window，導致畫面卡死。 |
| **V88.0** | 長效 DVR 與固定錨點 (Static DVR) | `Win: 10000ms` | **失敗 (畫面凍結)** | 未處理隱式 T 值累加，導致視窗寬度永遠固定在 10s，播放器撞牆凍結。 |
| **V89.0** | 顯式時戳傳遞 (Timeline-Propagation) | `Win: 10000ms` | **失敗 (畫面凍結)** | SQ 序列映射在 Manifest 更新時重疊，視窗長不長。 |
| **V94.0** | 無縫序號映射 (Gapless-Mapping) | `Win: 0ms` | **失敗 (崩潰)** | `lastRelTms` 計算為 0，導致 AST 設在未來，ExoPlayer 探測索引越界。 |
| **V95.0** | 強健字段提取與 AST 水位修正 (Safety-Window) | 待測 | **進行中** | **核心創新**：1. 修正反射字段讀取確保 Win 不為 0；2. AST 鎖定數據末端 15s 前，確保 Pos 始終為正。 |

#### 各版本詳細修正紀錄：

- **Version 95.0**：**強健字段提取與 AST 水位修正**。
    1. **Win 為 0 修復**：在 V94 中，日誌顯示 `Win: 0ms`，這說明 `lastData[1]` (duration) 讀取失敗。V95 加入了 5s 的保底 Duration 並優化了反射讀取。
    2. **正向 Pos 導航**：確保 `sessionBaseAST` 始終是一個過去的時間點。啟播時 `Pos` 會落在數據末端的 15 秒前（例如數據有 20s，Pos 就是 5s）。這能引導 ExoPlayer 正確進入 Live 狀態。
    3. **物理同步**：嚴格維持 `startNumber` 與 `stitchedElements` 列表長度的一致性，徹底消滅 `IndexOutOfBoundsException`。

- **Version 94.0**：**無縫序號映射**。
    1. **Key 改為 PTS**：不再信任 SQ 序列。`TreeMap<Long, Long>` 紀錄 `startTime -> duration`。這保證了 Manifest 更新時，新片段會因為 PTS 不同而被正確 append 到視窗末尾。
    2. **雙軌獨立 PTO**：`sessionFirstTs` Map 紀錄每個 itag 的第一個 PTS。Audio 和 Video 各自對齊到 0 點。這解決了「畫面卡在最後一幀但聲音繼續」的問題，因為之前共享 PTO 導致 Video 的虛擬 PTS 可能落後於播放器進度。
    3. **水位監控強化**：日誌包含 `Sz:`（緩存段數），方便觀察視窗是否正常累積。

- **Version 89.0**：**顯式時戳傳遞**。
    1. **T 值累加修復**：YouTube 的 `SegmentTimeline` 通常省略後續片段的 `t`。V89 改為手動紀錄 `currentT = prevT + prevD`。這將使 Manifest 視窗隨時間穩定增長（10s -> 15s -> ... -> 40min），徹底解決「數據撞牆」導致的畫面凍結。
    2. **初始化優化**：AST 設置從 `stitch` 的第一次執行中計算，確保它與第一批加載的真實數據精確同步。
    3. **深層穩定性**：`tMap` 保留 500 段紀錄，確保播放器有足夠的歷史回溯空間，即便 Manifest 更新慢了幾秒也不會影響播放。

- **Version 88.0**：**長效 DVR 與固定錨點**。
    1. **固定坐標系**：`sessionBaseAST` 僅在 Session 開始時計算一次。這模仿了標準 DASH 的行為，讓 `Now - AST` 成為一個絕對穩定的時間坐標。
    2. **大容量緩存視窗**：將 `stitchedElements` 擴充為包含所有歷史看過的片段（最多 500 段）。這解決了「數據供應不上播放速度」的問題。即使 YouTube 提供的當前 Manifest 只有 10s，ExoPlayer 也能看到過去 40 分鐘的完整數據，水位會隨著播放持續增長。
    3. **邊緣安全啟播**：初始起播點設在最新片段結束的 10 秒前。這兼顧了「直播實時性」與「緩衝抗抖動」。

- **Version 87.0**：**黑盒子深度探測**。
    1. **PTS 強力提取**：不再依賴字段名稱（如 `startTime`），改為掃描 `SegmentTimelineElement` 類中所有的 `long` 字段。YouTube 的第一個 `long` 永遠是 `t`，第二個是 `d`。這確保我們能從黑盒子裡挖出真實的 PTS。
    2. **PTO 坐標映射**：將第一個看到的真實 `t` 鎖定為 `sessionFirstT` 並賦值給 `presentationTimeOffset`。這在硬體層面實現了「絕對 PTS」與「ExoPlayer 虛擬坐標」的完美同步。
    3. **自適應直播邊緣**：自動計算數據末端相對於 `sessionFirstT` 的位置，並將 AST 錨定在末端 15 秒前。這保證了極致的直播延遲與穩定的緩衝水位。

- **Version 85.0**：**反射強化與動態視窗**。
    1. **真實 T 獲取**：修復了 `SegmentTimelineElement` 的反射路徑。V84 中獲取到 `FirstT:0` 是失敗的標誌，V85 將恢復獲取 YouTube 原始的大數 PTS。
    2. **自適應啟播點**：`sessionBaseAST = Now - (Manifest_End - 5s)`。這確保不論 YouTube 的 `t` 是多少，播放器都會從當前最新片段的倒數 5 秒處開始播，實現真正的「Live」且有緩衝。
    3. **長效縫合視窗**：緩存增加到 300 個 SQ，提供約 25 分鐘的回看能力，並增加穩定性。

- **Version 84.0**：**PTO 全局對齊**。
    1. **PTS 連致性**：不再修改 `startTime`，而是使用 `presentationTimeOffset = sessionFirstT`。這在不破壞影片內部 PTS 的前提下，將 Period 虛擬座標歸零。
    2. **穩健緩衝**：AST 固定為 `Now - 10s`。結合歸零的虛擬座標，播放器一啟動就擁有 10 秒的預加載數據。
    3. **隱式時戳處理**：修復了 `SegmentTimeline` 中隱式 `t` 值的計算，防止 `tMap` 中出現斷層。

- **Version 83.0**：**原始時戳透傳**。
    1. **解碼座標鎖定**：將 Session 第一個 SQs 強制映射到 `t=0`。這能確保 ExoPlayer 傳遞給硬解碼器的第一個 PTS 是穩定的 0 座標，消除 `frameIndex not found`。
    2. **預熱緩衝 (Hydration)**：AST 固定為 `Now - 10s`。因為我們從 `t=0` 開始提供約 15s 的數據，這讓播放器一啟動就擁有 10s 的預加載空間。
    3. **毫秒級連續性**：`startTime` 計算改為 `previous_start + previous_duration`，確保縫合點完美無縫。

- **Version 81.0**：**混合精準焊接**。
    1. **全局座標鎖定**：AST 固定設為 60 秒前，不再隨 Manifest 片段數量變動。
    2. **初始水位錨定**：計算 `itagFirstSQ` 時，主動將首批數據映射到 (Now-5s) 的位置，確保啟播時就有 5s+ 的緩衝。
    3. **精準時長追蹤**：`TreeMap` 改為儲存 `[startTime, duration]`，保留 YouTube 片段毫秒級的差異（如 5005ms），解決長時間播放後的「frame index not found」累積誤差。

- **Version 80.0**：**AST 回推對齊**。
    1. **邏輯**：`AST = Now - (Segs * Dur)`。
    2. **結果**：失敗。Buffer 始終在 0ms 附近，導致稍有網絡波動就轉圈。

- **Version 79.0**：**持久化緩存縫合**。
    1. **邏輯**：使用 `TreeMap` 緩存 SQ。每次更新只增不減（保留前 60 個片段）。
    2. **對齊**：`VirtualT = (SQ - itagFirstSQ) * 5000ms`。
    3. **預期**：提供長達 5 分鐘的虛擬視窗，解決 YouTube 片段太少導致的卡圈。

- **Version 78.0**：**基準座標鎖定**。
    1. **策略**：鎖定首個 $T$ 值計算 PTO。
    2. **結果**：失敗。雖然座標穩定了，但數據帶寬太窄（僅 3 個片段 15s），無法支撐 ExoPlayer 的緩衝需求。

- **Version 76.0**：**極簡座標平移**。
    1. **策略**：AST = 60s，Delay = 30s。
    2. **分析回饋**：
       `ExoUtil: >>> YT_STABLE_V76: [137] Segs:3 Dur:15015ms PTO_Shifted`
       `ExoUtil: onLoadStarted: ... StartTime: 44993`
       數據在 45s~60s，但播放點在 30s，導致卡死。


- **Version 71.0**：**穩定性測試 1.0**。
    1. **策略**：錨點 60s，延遲 30s。
    2. **結果**：起播快，但播了幾分鐘後會卡住。
    3. **分析**：緩衝區 (30s) 足夠，但 YouTube Manifest 刷新間隔太大，數據補充不夠及時。

- **Version 70.0**：**雙向極限擴張**。
    1. **調降啟播門檻**：在 `ExoUtil.java` 將 `minBufferMs` 從 20s 降至 5s。
    2. **雙向填補**：在 `sessionTimeline` 中往回補 60s 歷史，往後補 30s 未來。
    3. **結果**：起播成功，但切換片段時有明顯卡頓感。

- **Version 69.0**：解決了音畫同步，但忽略了 YouTube Manifest 刷新率的問題。緩衝區太薄，導致播放器在 Manifest 到達前就因水位過低停擺。
- **Version 66.0**：嘗試領先焊接，但未考慮到 YouTube 提供的片段數量極少。雖然緩衝「總量」足夠，但「位置」不對，導致播放頭落在無數據區間。
- **Version 65.0**：嘗試動態焊接將 `lastSQ` 對齊 `Pos`。雖然座標同步了，但因為沒有預留下載空間，播放器瞬間追上 Live Edge 觸發卡死。
- **Version 64.0**：回歸原始 AST。發現原始 AST 座標系與相對映射值有 20 分鐘落差，導致播放完第一段後卡死。
- **Version 63.0**：修正 `durationMs = C.TIME_UNSET`。嘗試 10 分鐘虛擬座標，造成 PTS 脫節黑屏。
- **Version 62.0**：`window` 設為 24 小時。解決邊界問題，但因 `durationMs = -1` 引起 1ms 結束 Bug。
- **Version 61.0**：填補 2 分鐘歷史。將 `AST` 擴至 10 分鐘前。因 Pos 邊界判斷 Bug 失敗。
- **Version 59.0**：修正 `sessionFirstSQ` 確保 Live Edge 對齊。將延遲設為 20s。但 YouTube 給的片段太少，緩衝達不到門檻。
- **Version 58.0**：固定 `AST = Now - 60s`，忽略 Manifest 起始時間，強制 SQ 映射。解決了頻繁回跳，但因 Buffer 算法不精準導致負值循環。

---

### E. YouTube API 核心 (Service)
#### 檔案：`MediaServiceCore/youtubeapi/src/main/java/com/liskovsoft/youtubeapi/videoinfo/V2/VideoInfoService.java`
- **修改內容**：
    1. **重排客戶端優先權**：將 `ANDROID_VR` 移至 `VIDEO_INFO_TYPE_LIST` 的第一位。
    2. **原因**：`TVHTML5` (TV_DOWNGRADED) 在某些地區獲取 DASH (.mpd) 失敗率較高。`ANDROID_VR` 提供更穩定的 DASH 流與更長的 DVR 窗口。

---

**紀錄日期**：2026-09-06
**維護者**：AI Assistant / Expert Android Developer
