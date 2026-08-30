# 「我的頻道」虛擬倉源與直播側邊選單設計

## 1. 核心概念
透過「側邊設定選單」與「虛擬倉源」機制，大幅縮短使用者管理與切換直播源的路徑。
- **側邊選單導航**：在頻道列表最左側加入「功能導航區」，透過向左按鍵喚出深度設定。
- **我的頻道 (My Channels)**：由使用者集結、命名、分類的專屬虛擬倉源。
- **獨立 EPG 綁定**：每個自定義頻道保留其原始 EPG 資訊，確保跨倉源顯示正確。

---

## 2. 使用流程設計 (User Flow)

### 流程 A：喚出側邊設定選單
1. **呼叫列表**：播放中按「OK」或「返回」彈出「頻道選單」。
2. **進入設定**：焦點移動到列表最左側（顯示左箭頭處），按下「左鍵」或「確認鍵」。
3. **功能分區**：彈出側邊欄，包含三個主要入口：
   - **倉源**：點擊即列出所有可用訂閱源（如影視倉、訂閱源1等），選中後立即切換。
   - **設定我的頻道**：進入管理模式，可對虛擬倉源進行分類調整、排序、更名、刪除。
   - **偏好設定**：設定預設啟動倉源、UI 顯示時間、解碼模式等系統參數。
4. **返回列表**：在側邊欄按「右鍵」或「右箭頭」回到頻道選單。

### 流程 B：跨倉源集結 (Add to My Channels)
1. **一般倉源瀏覽**：在任意普通倉源的頻道列表上。
2. **觸發加入**：對目標頻道「長按確認鍵」。
3. **選擇動作**：點擊「加入我的頻道」。
4. **自定義存儲**：
   - 彈出分類清單（包含「新增分類」選項）。
   - 可修改頻道顯示名稱。
   - 系統自動紀錄原始 `origin_config` (原始配置 Key)、`epg_url` (或 `epg`)、`tvg-id`、`logo` 與 `urls`。

### 流程 C：管理「我的頻道」
1. **進入管理**：在側邊欄選擇「設定我的頻道」或在「我的頻道」倉源下對頻道長按。
2. **多維管理**：
   - **移動/排序**：選中頻道進入「排序模式」，上下移動位置。
   - **多類別並存**：同一個頻道可以加入不同的分類（例如：CCTV-5 可同時存在「央視」與「體育」類別）。
   - **分類管理**：建立多級分類目錄，調整目錄順序。

### 流程 D：返回與退出邏輯
1. **有 UI 顯示時**：按下「返回」鍵，優先隱藏 UI（如控制欄、列表、資訊欄），不提示退出。
2. **無 UI 顯示時**：按下「返回」鍵，若當前已無 UI 可隱藏，則彈出「再按一次返回鍵退出」提示。
3. **確認退出**：2 秒內再次按下「返回」鍵，執行 Activity 關閉邏輯。

---

## 3. 技術實作方案

### A. 數據結構 (`my_live.json`)
```json
{
  "lives": [
    {
      "name": "我的頻道",
      "type": "virtual",
      "groups": [
        {
          "name": "體育頻道",
          "groups": [],
          "channels": [
            {
              "name": "自定義足球台",
              "origin_config": "原始配置Key",
              "origin_group": "原始分組",
              "origin_name": "原始頻道名",
              "epg": "http://...", 
              "tvg_id": "cctv5",
              "logo": "http://...",
              "urls": ["URL1", "URL2"]
            }
          ]
        }
      ]
    }
  ]
}
```

### B. 交互邏輯與返回鍵實作
- **焦點循環**：頻道選單與側邊選單透過「左/右」鍵切換，分類列表最左側需處理 `onKeyLeft` 事件導向側邊欄。
- **雙擊退出 (LiveActivity.java)**：
  ```java
  protected void onBackPress() {
      if (isVisible(mBinding.control.getRoot())) {
          hideControl();
      } else if (isVisible(mBinding.widget.bottom)) {
          hideInfo();
      } else if (isGone(mBinding.recycler) && mGroupAdapter.getItemCount() > 0) {
          showUI(); // 帶出功能不提示
      } else if (System.currentTimeMillis() - mExitTime < 2000) {
          finish(); // 2秒內連按則退出
      } else {
          mExitTime = System.currentTimeMillis();
          Notify.show(R.string.app_exit);
      }
  }
  ```

### C. 虛擬源注入與 EPG
- **注入機制**：`LiveConfig.init()` 時，讀取本地 `my_live.json` 並將其作為首個 `Live` 對象插入全域列表。
- **EPG 優先級**：修改 `LiveViewModel.getEpg(Channel)` 與 `LiveApi.fetchEpgDay`，優先判斷 `channel.getEpg()` 是否有值，實現「跨倉源頻道保留原 EPG」或「自定義頻道綁定特定 EPG」。

---

## 4. 開發階段與順序 (Roadmap)

### 階段一：底層數據模型與本地存儲 (基礎建設)
- **目標**：讓系統支持「我的頻道」數據格式，並能從本地讀取/寫入。
- **工作內容**：
    1. 修改 `Channel.java`：新增 `originConfig`, `originGroup`, `originName` 等欄位。
    2. 建立 `LiveUtil` 或更新 `FileUtil`：實作 `my_live.json` 的讀取與寫入。
    3. 擴展 `LiveConfig.java`：在 `init()` 時注入虛擬 `Live` 對象。
    4. **資料庫處理**：若涉及 Room 資料庫變動，執行 Migration 升級。

### 階段二：EPG 優先級與虛擬源注入 (邏輯實作)
- **目標**：確保切換到「我的頻道」後，播放與 EPG 能正常運作。
- **工作內容**：
    1. 修改 `LiveViewModel.java`：優先讀取頻道層級 EPG。
    2. 優化 `LiveApi.java`：支持獨立 EPG URL 下載邏輯。
    3. 驗證播放流程與 EPG 顯示。

### 階段三：側邊選單與 UI 框架 (視覺實作)
- **目標**：實作側邊導航選單，處理焦點切換邏輯。
- **工作內容**：
    1. 修改 `activity_live.xml`：新增側邊導航選單元件。
    2. 更新 `LiveActivity.java` 焦點處理：實作向左呼叫側邊欄。
    3. 加入視覺引導（如左側箭頭圖示）。

### 階段四：頻道管理與跨倉源集結 (交互實作)
- **目標**：完成「長按加入」與「排序/分類」管理功能。
-工作內容**：
    1. 實作「加入我的頻道」Dialog 與分類選擇 UI。
    2. 修改 `ChannelAdapter.java`：新增長按菜單邏輯。
    3. 實作管理模式：支援排序、刪除與分類調整，並同步至本地 JSON。

---

## 5. Review 要點
1. **側邊欄 UI**：需在 `activity_live.xml` 新增窄版選單，並在分類列表最左側加入「<」視覺引導。
2. **資料完整性**：加入頻道時必須強制抓取 `origin_config`，避免主倉源切換後自定義頻道失效。
3. **效能優化**：虛擬源資料變動時，使用 `LiveConfig.postEvent()` 通知 `LiveActivity` 局部更新 `mGroupAdapter`，避免全量重整造成卡頓。
