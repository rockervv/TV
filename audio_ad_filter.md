# 音訊特徵廣告過濾系統 (Audio Texture AD Filter) 技術規劃書

## 1. 核心目標
針對無法透過 M3U8 規則攔截的重複性廣告，利用音訊指紋 (Audio Fingerprinting) 技術進行動態識別與自動跳過。

## 2. 實作流程架構
系統分為三個主要階段：**特徵獲取與標記**、**背景分析比對**、**播放執行跳過**。

### A. 偵測點與觸發機制 (Optimized)
*   **觸發訊號**：
    1.  監聽 HLS 的 `#EXT-X-DISCONTINUITY`。
    2.  **時間點觸發**：若目前影集已有紀錄的廣告時間點 (e.g., 15:30)，當播放接近該時間時，主動觸發音訊比對。
*   **範圍判定**：
    *   若廣告夾在兩個 `#EXT-X-DISCONTINUITY` 之間且長度 `< 20s`：視為完整廣告區塊，匹配成功後直接跳轉至第二個 Discontinuity 後。
    *   **手動標記模式**：
        *   若僅標記「廣告開始」：默認廣告為該單一 TS。
        *   若標記「廣告結束」：計算開始至結束間的 TS 總數與總時長，並更新資料庫。
        *   **合法性檢查**：單段廣告時長上限設定為 1 分鐘，超過則視為無效標記。
*   **監控層級**：透過 `ExoPlayer.Listener.onPositionDiscontinuity` 結合 `HlsMediaSource` 的 `PlaylistTracker` 獲取下一個 Segment 的資訊。
*   **分析範圍**：僅針對觸發點後的第一個 TS 檔案的前 2~5 秒音訊數據。

### B. 資料流 (Data Flow)
1.  **上下文關聯**：比對時優先讀取該**影集/頻道**專屬的廣告庫 (Scoped Database)，減少全局搜尋負荷。
2.  **攔截器**：`AdAudioInterceptor` 獲取 TS 數據。
3.  **解碼器**：`AudioExtractor` 利用 `MediaExtractor` + `MediaCodec` 將 TS 轉為原始 PCM。
4.  **比對器**：`ADFilter` 將當前特徵與**關聯庫**進行相似度比對。

---

## 3. 模組設計 (Component Design)

### 3.1 `AudioFingerprinter` (核心算法)
*   **方法**：採用簡化版的音訊哈希 (Perceptual Audio Hashing)。
*   **步驟**：
    1.  將 PCM 數據進行 FFT。
    2.  提取頻帶能量特徵。
    3.  生成具備抗噪能力的 LSH 指紋。

### 3.2 `AdDatabase` (儲存層)
*   **實作**：使用 `Room` 儲存，並建立 `source_id` 或 `series_id` 索引。
*   **欄位**：
    *   `id`: 唯一識別碼。
    *   `fingerprint`: 音訊特徵指紋 (用於比對)。
    *   `source_id`: 影集或頻道識別碼 (Scoped Search)。
    *   `series_name`: 影集片名 (如：甄嬛傳)。
    *   `ad_name`: 廣告名稱 (自定義或自動生成，如：廣告 1)。
    *   `start_time_offset`: 廣告在該影集中發生的時間點 (毫秒)。
    *   `ts_count`: 該廣告區塊包含的 TS 檔案總數。
    *   `duration`: 該廣告區塊的總時長 (毫秒)。
    *   `hit_count`: 該廣告被識別並跳過的次數統計。
    *   `last_hit_time`: 最後一次偵測到的時間。

### 3.3 `AdSkipper` (執行層)
*   **動作**：
    1.  UI 提示：清晰顯示**「偵測到廣告，自動跳過 xx 秒」**。
    2.  執行：`player.seekTo(targetPosition)`。

---

## 4. UI/UX 整合 (使用者回饋循環)
*   **廣告管理介面**：提供專屬設定頁面，列出目前已標記的廣告（顯示片名、廣告名、發生時間），並支援**單條刪除**或**清空該片源廣告**。
*   **手動標記流程**：
    1.  播放中按下「標記為廣告」。
    2.  UI 切換狀態，提供「標記為結束」或「這是新廣告」選項。
    3.  **標記為結束**：自動計算時長，若 `< 60s` 則儲存。
    4.  **即時回饋**：顯示「已紀錄廣告特徵，該特徵已存在 xx 次」。

---

## 5. 效能優化策略 (Performance Blueprint)
*   **精確檢索**：比對時僅載入與當前播放內容相關的指紋集，避免比對過期或無關的廣告數據。
*   **非同步化**：所有解碼與比對操作必須在 `HandlerThread` 或 `Coroutine` 中執行。
*   **統計分析**：透過 `hit_count` 評估特徵的普及率，作為未來開啟「全局自動偵測」的依據。

---

## 6. 潛在風險與解決方案
*   **誤報 (False Positive)**：某些節目片頭可能與廣告音訊相似。
    *   *解決*：引入「確認機制」，只有在 Discontinuity 發生時才觸發，且比對相似度門檻設定在 92% 以上。
*   **CPU 負擔**：連續頻繁的 Discontinuity 可能導致解碼堆疊。
    *   *解決*：建立工作隊列，若前一個比對尚未完成，自動放棄下一個比對以保護系統穩定性。

---

## 7. 實作階段規劃 (Implementation Roadmap)

### 第一階段：核心基礎設施 (Data & Algorithm) - [DONE]
*   **AdDatabase (Room)**：實作 `AdEntity` 與 DAO，支援 `source_id` 檢索。
*   **AudioFingerprinter**：開發 PCM 轉特徵碼工具類，驗證相似度算法。

### 第二階段：音訊數據獲取 (Audio Extraction) - [DONE]
*   **AudioExtractor**：利用 `MediaCodec` 從 TS URL 提取前 3 秒 PCM。
*   **Async Worker**：建立專屬執行線程，確保解碼不阻塞主線程。

### 第三階段：偵測與觸發邏輯 (Detection & Trigger) - [DONE]
*   **ExoPlayer Listener**：實作 `onPositionDiscontinuity` 與時間偏移監聽。
*   **Trigger Flow**：連接獲取、提取、比對的完整後台流程。

### 第四階段：執行跳過與 UI 回饋 (Execution & Feedback) - [DONE]
*   **AdSkipper**：執行 `player.seekTo()` 並計算跳轉偏移。
*   **UI Overlay**：顯示「自動跳過 xx 秒」提示元件。

### 第五階段：手動標記與管理介面 (UX & Management) - [DONE]
*   **標記機制**：實作「標記開始/結束」兩段式 UI 與時長合法性檢查。
*   **管理頁面**：實作已標記廣告的清單檢視、刪除與統計數據更新。
