# TV 專案：多維度場景（Multi-Context）架構重構藍圖

## 1. 核心願景
將 App 從單一的點播/直播模式，轉變為支援多個獨立觀影場景（Persona/Context）的架構。每個場景（如：一般影視、體育、音樂、成人）擁有獨立的 Spider 列表、歷史紀錄、收藏、推薦與搜尋空間。

---

## 2. 邏輯架構：邏輯分區 (Logical Partitioning)
*   **數據層**：統一資料庫，透過 `context` 欄位進行物理隔離感官。
*   **配置層**：擴展 JSON 協定，支援按類別定義 Spider。
*   **UI 層**：增加場景切換器，根據當前 Context 渲染不同的介面風格與內容。

---

## 3. JSON 協定擴展規範 (JSON Protocol)
為了支援多維度場景，JSON 配置檔將遵循以下命名規範：

```json
{
  "spider": "...",
  "sites": [ ... ],             // 預設場景：一般影視 (VOD)
  "sites_sport": [ ... ],       // 擴展場景：體育
  "sites_music": [ ... ],       // 擴展場景：音樂
  "sites_x": [ ... ],           // 擴展場景：成人/隱私
  
  "contexts": [                 // 場景定義與 UI 注入
    { 
      "id": "vod", 
      "name": "一般影視", 
      "type": 0,
      "style": {
        "layout": "grid",
        "type": "rect",
        "ratio": 0.66,
        "theme": "#E50914"
      }
    },
    { 
      "id": "music", 
      "name": "音樂頻道", 
      "type": 2,
      "style": {
        "layout": "list",
        "type": "oval",
        "ratio": 1.0,
        "theme": "#1DB954"
      }
    }
  ]
}
```

**解析邏輯**：
1. **數據映射**：`VodConfig.java` 根據 `ContextId` 動態映射 Key：`Context == "vod" ? "sites" : "sites_" + Context`。
2. **UI 注入**：`HomeActivity` 載入場景時，讀取 `style` 物件並透過 `Presenter` 實作動態佈局與樣式渲染。

---

## 4. 階段規劃與修改清單

### 第一階段：資料與模型基礎 (The Foundation)
**目標**：讓資料庫具備區分場景的能力。

| 檔案路徑 | 修改重點 |
| :--- | :--- |
| `Site.java` | 新增 `context` 欄位（int 或 String），區分所屬分類。 |
| `History.java` | 新增 `context` 欄位。 |
| `Keep.java` | 新增 `context` 欄位。 |
| `AppDatabase.java` | 升級版本號（39），實作 Migration 新增 context 欄位並設預設值。 |
| `BaseDao.java` | 檢視是否需要根據 context 進行全域過濾。 |

### 第二階段：配置載入器改造 (Config Loader)
**目標**：支援從一個設定檔載入多組 Spider，或切換不同的設定。

| 檔案路徑 | 修改重點 |
| :--- | :--- |
| `VodConfig.java` | 從單一 `sites` 列表改為 `Map<Integer, List<Site>>`。 |
| `VodConfig.java` | 實作 `setContext(int context)` 方法，切換當前活耀的站台池。 |
| `Decoder.java` | 檢視 JSON 解析邏輯，是否支援 `sites_sport` 等新標籤。 |

### 第三階段：業務邏輯與 API 隔離 (Business Logic)
**目標**：確保搜尋、推薦不會跨場景「串味」。

| 檔案路徑 | 修改重點 |
| :--- | :--- |
| `SiteApi.java` | 所有請求強制帶入當前 context 參數，確保存取正確的資料表子集。 |
| `SiteViewModel.java` | 搜尋排序邏輯加入 context 過濾，僅搜尋當前場景的站台。 |
| `CacheManager.java` | 快取檔名加入 context 前綴，避免不同場景的同名站台衝突。 |

### 第四階段：UI/UX 切換器實作 (The Switcher)
**目標**：實作場景切換的體感與導航邏輯。

| 檔案路徑 | 修改重點 |
| :--- | :--- |
| `HomeActivity.java` | 在側邊欄或頂部標題處新增「場景切換器（Context Switcher）」。 |
| `HomeActivity.java` | 切換場景時觸發 `VodConfig.setContext()` 並刷新 `mAdapter` 與 `ViewPager`。 |
| `SettingActivity.java` | 增加各個場景的獨立設定入口。 |
| `activity_home.xml` | 調整版面以容納切換按鈕。 |

---

## 4. 關鍵技術挑戰與對策
1.  **隱私保護 (針對 X-Site)**：
    *   在資料庫儲存時，針對特定 context 的 `History` 標題進行簡單加密或隱藏。
    *   進入該場景前可選是否開啟密碼鎖。
2.  **效能維護 (Chromecast 調適)**：
    *   即便支援多場景，記憶體中仍僅保留當前場景的 Spider 實體，切換時即刻調用 `BaseLoader.clear()`。
3.  **JSON 格式相容性**：
    *   需相容舊版單一 `sites` 陣列的 JSON，將其預設歸類為 `VOD` 場景。

---

## 5. 執行順序建議
1.  **DB Migration** (確保資料結構就緒)。
2.  **Bean 修改** (Site/History/Keep)。
3.  **Config 載入邏輯** (讓程式能讀到不同組的 Spider)。
4.  **UI 基礎框架** (實作切換鈕，先求能切換站台列表)。
5.  **業務邏輯補全** (搜尋、歷史隔離)。
