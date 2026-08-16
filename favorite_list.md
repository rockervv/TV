# 快速切換功能修改計畫 (Favorite Switch Feature Plan)

## 1. 數據層 (Database Layer)
- **新增 Entity: `Favorite.java`**
    - 欄位: `key` (siteKey + symbol + vodId), `vodName`, `vodPic`, `vodRemarks`, `createTime`, `order`.
    - `vodRemarks` 用於紀錄「最後觀看集數」。
- **新增 DAO: `FavoriteDao.java`**
    - 提供 `insert`, `delete`, `findAll`, `findByKey` 等方法，按 `order` 排序。
- **`AppDatabase.java` 升級**
    - 資料庫版本升級至 40，新增 `Favorite` 表與 `MIGRATION_39_40`。
- **資料同步機制**
    - **進度同步**: 當 `History.save()` 被觸發時，自動更新對應 `Favorite` 項目中的 `vodRemarks`。
    - **遠端備份**: 整合進 `Backup.java`，支援 FTP/WebDAV/Gist 等雲端同步。

## 2. UI 元件 (UI Components)
- **播放控制欄 (`view_control_vod.xml`)**
    - 在 `actionLayout` 最前面插入 `TextView` (id: `favorite`)。
- **彈出視窗佈局 (`dialog_favorite_switch.xml`)**
    - 使用 `RecyclerView` 顯示清單，設有 `maxHeight` 避免過長。
- **影片項目佈局 (`adapter_favorite_switch.xml`)**
    - **視覺反饋規則**:
        - **正常**: 文字顯示為白色。
        - **有更新**: 當雲端集數 > 本地紀錄時，`vodRemarks` 顯示為**黃色**。
        - **失效**: 當站點無法取得資料或資源已下架，片名與備註變更為**灰色**，且備註顯示「沒有播放資料」。

## 3. 邏輯處理 (Logic)
- **`FavoriteSwitchDialog.java`**
    - **數據組合**: 
        - 頂層 (Favorite): 使用者自定義，上限 10 筆。
        - 歷史 (History): 最近觀看 5 筆。
        - 收藏 (Keep): 最近收藏 5 筆。
    - **更新與錯誤檢測**: 
        - 僅針對頂層項目，在對話框打開時異步請求 `SiteApi.detailContent`。
        - 根據回傳結果更新 `mUpdates` (黃色提醒) 或 `mErrors` (灰色失效提醒) 狀態。
    - **交互行為**:
        - 短按: 呼叫 `VideoActivity.start` (或 `push`) 切換播放。
        - 長按 (頂層): 選單包含「上移」、「下移」、「刪除」。
        - 長按 (歷史/收藏): 選單顯示「加入」。若已達 10 筆上限，提示「清單已滿」。

## 4. `VideoActivity.java` 整合
- `initEvent`: 綁定 `mBinding.control.favorite` 點擊事件。
- `onFavorite`: 實例化 `FavoriteSwitchDialog` 並傳入當前 `mHistory` 以供快速加入。

## 5. 規則確認
- **容量上限**: 10 個頂層項目。
- **效能平衡**: 僅頂層項目進行 API 輪詢以節省網路資源。
- **備份機制**: 保證使用者更換裝置後，快速切換清單與排序能完整還原。
