# YouTube 直播穩定性修正日誌 (YT_STABLE_FIX)

## 核心問題 (Problem Definition)
YouTube DASH 直播的 Manifesto 每隔幾秒刷新一次。每次刷新時，`availabilityStartTime` (AST) 和片段的 `startTime` 會發生毫秒級的微小偏移（YouTube 的時間軸不穩定）。
ExoPlayer 檢測到這種偏移後，會認為當前緩衝的時間軸失效，觸發 `onPositionDiscontinuity (Reason 4)` 或 `onTimelineChanged`，導致播放器重置、黑屏、循環跳回或卡死。

---

## 版本演進 (Version History)

### V116 - V117: 初始探索 (失敗)
- **思路**: 使用標準 ExoPlayer DASH 解析，嘗試通過調整 `minBufferTime` 來中和抖動。
- **結果**: 失敗。ExoPlayer 對毫秒級漂移極度敏感，依然頻繁重置。

### V118: 無限 0 起始時間軸 (失敗)
- **思路**: 強制所有片段從 $T=0$ 開始排列，不跟隨 Wallclock。
- **結果**: 失敗。Manifesto 不會「滑動」，ExoPlayer 在播放完緩衝區後停止加載，因為它認為沒有新片段了。

### V119 - V120: 絕對 Unix 時間戳同步 (失敗)
- **思路**: 使用 `AST=0`，將片段 `startTime` 映射為絕對 Unix 時間（毫秒）。
- **結果**: 失敗。
    - **403 錯誤**: 請求網址中的時間參數與 Manifesto 的絕對時間不匹配。
    - **負緩衝 (Negative Buffer)**: 由於計算邏輯未考慮時區或解析錯誤，導致日誌顯示 `Buf: -XXXXms`，播放器認為數據在「未來」，拒絕播放。

### V121: 相對虛擬時間軸 (首次成功)
- **思路**: **「偽裝 VOD」策略**。將換台後看到的第一個序號 (SQ) 映射為 $T=0$，AST 設為「現在 - 30秒」。
- **反饋**: **重大突破**。播放首次超過 10 秒（達到 15-20 秒）。
- **瓶頸**: 播放一段時間後依然卡住。原因：播放頭追上了我們偽造的「虛擬邊緣」，且 A/V 兩軌的錨點沒有全局對齊。

### V122: 絕對錨點修正 (失敗)
- **思路**: 嘗試修復 V120 的絕對時間邏輯，設置 `AST=0` 並自動校準 SQ 到時間的映射。
- **結果**: 失敗。日誌出現 `Buf: -15934ms`。

### V123: 全域虛擬會話 (失敗)
- **思路**: **全域靜態 session 鎖定**。
- **邏輯**: `sessionAST = Now - 30s`, `sessionFirstSQ = SQ0`。
- **結果**: 失敗。ExoPlayer 在數據加載 3 個片段後停止，無法持續增長。

### V124 - V125: 正向緩衝挑戰 (失敗)
- **思路**: 強制 Edge > Pos (正緩衝)，試圖拉開播放頭與數據邊緣的距離。
- **結果**: 失敗。ExoPlayer 的 Wallclock 機制導致播放點落在數據區起點之前，觸發無限等待。

### V126: 負向緩衝回歸 (失敗)
- **思路**: 接受 YouTube 的 15s 延遲限制，使用絕對 Unix 時間。
- **結果**: 失敗。`Buf` 顯示 `-5000ms`，播放頭頻繁撞牆。

### V127: 無縫增長引擎 (失敗)
- **思路**: **持久化片段池**。將所有看過的 SQ 存入 TreeMap，構造不斷變長的虛擬 VOD。
- **結果**: 失敗。數據從 $T=30s$ 開始，但播放器延遲設為 20s，導致播放點落在 $T=10s$ (無數據區)，畫面卡死。

### V128: 歷史溯源策略 (失敗)
- **思路**: 將數據起點錨定在 `Now - 40s`，延遲設為 30s。
- **結果**: 失敗。雖然數據加載正常，但因 Manifesto 中的虛擬時間戳與 m4s 內部的原始時間戳衝突，解碼器拒絕出圖。

### V129: 坐標原點鎖定 (編譯失敗)
- **思路**: 鎖定換台時刻的 `originalAST`，後續 Manifesto 強行套用此 AST。
- **結果**: 失敗。代碼中 `DashManifest` 字段名錯誤，導致編譯中斷。

### V130: 規格化虛擬窗口 (失敗)
- **思路**: **AST 預位移 + 歸一化時間軸**。
- **結果**: 失敗。雖然能出第一幀，但播放點設在 $T=10s$，數據只有 15 秒，緩衝區太薄（只有 5s），導致迅速回到 buffering。

### V131: 緩衝最大化引擎 (失敗)
- **思路**: 在 V130 基礎上，將播放頭壓制到數據區的**極限起點**。
- **結果**: 失敗。雖然擁有 10 秒緩衝，但 ExoPlayer 的 LoadControl 門檻較高，依然反覆進入 buffering。

### V132: 安全邊緣錨點 (失敗)
- **思路**: **大幅增加深度 + 降低起播門檻**。
- **結果**: 失敗。雖然解決了起播問題，但因為播放點離數據邊緣始終只有 10s，一旦加載稍慢就會掉回 buffering。

### V133: 末端錨定對齊 (成功突破)
- **思路**: **逆向對齊 Live Edge**。
- **核心**: 將最新的 SQ 結束時間對齊到 `Now - AST`，`Delay = 15s`。
- **結果**: **重大成功**。緩衝區厚度維持在 15s - 20s 之間，播放器穩定進入 State 3 (READY)。

### V134: 統一全域時鐘 (成功突破)
- **思路**: 將時鐘更新逻辑提升至 `parse` 階段。
- **結果**: 成功解決 A/V 抖動問題，但發現起播有 5 秒延遲。

### V135: 完美對齊錨點 (失敗)
- **思路**: **末端結束時間對齊**。
- **結果**: 失敗。雖然擁有 15s 緩衝且播放位置正確，但因虛擬時間軸與 m4s 內部原始時間戳衝突，解碼器拒絕出圖，卡在 buffering。

### V136: 時間戳補償橋接 (失敗)
- **思路**: **PTO Bridge 策略**。
- **結果**: 失敗。由於 PTO 在每次刷新時都重新計算，導致時間軸發生漂移，播放器卡在 buffering。

### V137: 全域 PTO 錨定 (失敗)
- **思路**: **Session PTO Lock**。
- **結果**: 失敗。雖然解決了 A/V 抖動，但因虛擬時間軸與原始時間軸的代數級別差異，解碼器無法對齊數據，導致卡在 buffering。

### V138: 原始時間線性化 (部分成功)
- **思路**: **「原始錨定」策略**。保留原始大數字時間戳，鎖定第一個 SQ 並線性推算。
- **結果**: 雖然能秒開第一段，但因數學計算上 Edge 與 Wall 存在巨大落差（負數十億毫秒），導致解碼器在播放完第一段後陷入永久 Buffering。證明了必須讓「媒體時間」與「Period 時間」在 ExoPlayer 的認知範圍內對齊。

### V139: 混合錨定策略 (失敗)
- **思路**: **Hybrid Anchor**。手動計算 PTO 將第一個片段映射到 30s。
- **結果**: 雖然播放更穩定，但可能因 Buffer 量級（Dist ~20s）配合默認 Delay 導致後續片段加載時機不對，仍存在不穩定性。

### V140: 工業級線性化與同步 (失敗)
- **思路**: **Clean Rebuild**。重新構建 Manifest 樹。
- **結果**: 失敗。由於 `Representation.MultiSegmentRepresentation` 的構造函數訪問受限，且 `instanceof` 可能在混淆或某些環境下行為異常，導致代碼未能正確運行（日誌缺失）。

### V144: 全面橋接與構造修復 (失敗)
- **思路**: **「萬能構造器」與「精確時間橋接」**。
- **結果**: 播放第一段後依然卡死。日誌顯示 `stitch` 被調用但未打印 `ANCHORED`，且 `StartTime`仍為負數十億。確診為 `MultiSegmentRepresentation` 為抽象類，V144 嘗試直接構造失敗，導致回退到原始 Manifest。

### V146: 全激進反射 (失敗)
- **思路**: **Object-level Probing**。通過遍歷所有字段並檢查 `instanceof` 來獲取 `segmentBase`。
- **結果**: 失敗。日誌顯示 `stitch` 被調用但未打印 `ANCHORED`，`StartTime` 仍為負數十億。

### V147: 名稱模糊匹配 (失敗)
- **思路**: **Class Name Probing**。檢查字段類名是否包含 "SegmentBase"。
- **結果**: 失敗。日誌顯示 `stitch` 被調用但未打印 `ANCHORED`，這意味著 `rawElements` (Timeline List) 獲取失敗。推測 `segmentTimeline` 字段在混淆後名稱已變。

### V150: 盲掃構造 (失敗)
- **思路**: **Full Blind Template Probing**。通過內容識別 `UrlTemplate`。
- **結果**: 失敗。日誌中沒有 `ANCHORED` 輸出，意味著 `stitch` 邏輯未被觸發。

### V154: 深度診斷掃描 (失敗)
- **思路**: **String-Based Diagnostic Probe**。
- **結果**: 失敗。日誌報錯 `STITCH ABORTED - Components missing (List:true MediaT:false)`。這證明 `UrlTemplate` 的類名在某些情況下連 `"UrlTemplate"` 字符串都沒有（可能被 R8 徹底重命名且類路徑改變）。
- **原因**: 之前的識別邏輯過於依賴特定的類名關鍵字。

### V156: 靜態重建 (失敗)
- **思路**: **Content-Based Reconstruction**。當內容匹配時，手動調用 `UrlTemplate.compile(s)` 繞過對原始對象的類型轉換。
- **結果**: 失敗。日誌依舊顯示 `MediaT:false`。
- **原因**: 1. `UrlTemplate` 的 `toString()` 可能在混淆後被剝離，返回默認的 `ClassName@Hash`，導致內容檢查失效。2. 字段獲取可能被某些 R8 優化（如內聯）干擾。

### V157 - V162: 深度字符串與 ProGuard 對抗 (失敗)
- **思路**: **Deep Behavioral Probe**。深入對象內部尋找特徵字符串，並嘗試通過 ProGuard `-keep` 規則保留 Media3 關鍵類。
- **結果**: 失敗。日誌依然顯示 `STITCH ABORTED (MediaT:false)`。
- **原因**: 1. `UrlTemplate` 內部並沒有儲存原始字符串的字段，它在構造時就已拆解，導致字符串掃描失效。2. 即便類名保留，內部的 `toString()` 可能仍被優化或抹除。

### V163: 直接類名鏈接 (失敗)
- **思路**: **The Direct Link**。既然 ProGuard 已成功保留類名（日誌顯示 `UrlTemplate kept!`），直接使用 `instanceof UrlTemplate` 進行判定。
- **結果**: 失敗。`TemplateCount` 為 0。
- **原因**: R8 可能進行了類別合併或欄位內聯，導致在運行時無法通過標準反射路徑找到實例。

### V164: 順序與類型雙重判定 (失敗)
- **思路**: Recognize `UrlTemplate` 無字符串特徵。改用**順序識別法**：在 `SegmentTemplate` 中，第一個 `UrlTemplate` 欄位通常是 Init，第二個是 Media。
- **結果**: 失敗。依然無法正確定位模板對象。

### V165: 結構特徵指紋鑑定 (失敗)
- **思路**: **The Ghost Hunter**。不再依賴類名，而是通過「結構特徵」尋找：一個對象若包含 `String[]` 和 `int[]` 兩個數組，則判定為 `UrlTemplate`。
- **結果**: 成功掃描到對象，但發生 `ClassCastException` 或數據偏移異常。

### V166: 遞歸地毯式搜索 (部分成功)
- **思路**: **The Final Hunter**。深度 5 層的遞歸掃描，配合 `IdentityHashMap` 防止循環引用。
- **結果**: 仍有不穩定性，掃描路徑可能因強制轉換崩潰而中斷。

### V167: 終極獵人架構 (部分成功)
- **思路**: **The Relentless Hunter**。使用 `List<Object>` 收集疑似模板，避開 `ClassCastException` 陷阱。
- **結果**: 仍有遺漏，部分混淆環境下 `Found:0`。

### V168: 原子結構掃描 (失敗)
- **思路**: **The Atomic Hunter**。解除掃描封鎖，進入 `List` 內部掃描，並通過 `String[]` + `int[]` 的「原子結構」鑑定 `UrlTemplate`。
- **結果**: 失敗。`Found:0` 依然存在，說明掃描入口（Entrance）被封死。

### V169: 繼承鏈穿透掃描 (失敗)
- **思路**: **The Phantom Hunter**。掃描 `Representation` 的**所有繼承層級**（Superclasses）來尋找 `segmentBase` 入口。同時鎖定 `1000000` (微秒) 作為最高優先級時標。
- **結果**: 發現了 `ClassCastException` 崩潰。

### V170: 全域行為鑑定 (失敗)
- **思路**: **The Ultimate Hunter**。放寬判定：只要 `toString()` 包含 `$` 且含有數組即視為模板。
- **結果**: **崩潰**。`SegmentList` 類名包含 `$`，被錯誤判定為模板，引發強轉崩潰。

### V171: 外科手術式反射拷貝 (失敗)
- **思路**: **The Surgical Hunter**。徹底放棄 `(UrlTemplate) casting`。一旦鑑定物件符合結構特徵，直接用反射提取數據，手動 `new UrlTemplate(...)`。
- **結果**: 失敗。日誌顯示 `List:false Found:1 Ts:1000`。
- **分析**: 掃描路徑過窄，未能進入包含 Timeline 列表的同級物件，且 `1000000` 時標被漏掉。

### V172: 原子結構深度掃描 (部分成功)
- **思路**: **The Atomic Hunter**。深度掃描物件圖。
- **結果**: 在 aggressive 混淆下仍有遺漏，日誌顯示 `List:false`，說明時間軸物件被陣列或深層物件包裝。

### V173: 幽靈協議全圖掃描 (失敗)
- **思路**: **全維度無死角掃描**。穿透陣列內部尋找欄位。
- **結果**: 失敗。日誌顯示 `List:false MediaT:false Ts:1000`。
- **分析**: 
    1. 輔助判定方法（如 `tryCloneTemplate`）未考慮繼承鏈，導致混淆後位於父類別的數據提取失敗。
    2. `scan` 在發現模板後立即返回，漏掉了可能存在於同級或深層的時標與列表。
    3. 時標識別僅限於 `long`，漏掉了可能被 R8 優化為 `int` 的 `1000000` 值。

### V174: 全知獵人 (失敗)
- **思路**: **跨層級與數值特徵雙重鎖定**。
- **結果**: 失敗。日誌顯示 `List:false MediaT:false Found:1 Ts:1000`。
- **分析**: R8 的混淆比預期更徹底，物件之間的關聯路徑可能被切斷或重組，導致「順藤摸瓜」的掃描方式失效。

### V175: 暴力美學獵人 (失敗)
- **思路**: **全量收集與行為後驗 (Post-Behavioral Evaluation)**。
- **結果**: 失敗。日誌顯示 `List:true Found:0 Ts:1000`。
- **分析**: 雖然成功找到了列表，但 `UrlTemplate` 的鑑定邏輯（依賴數組數量）依然失效。這說明 R8 可能把 `UrlTemplate` 內部進行了更劇烈的改動。同時 `Ts:1000` 說明時標在獨立欄位中找不到。

### V176: 遺傳算法獵人 (失敗)
- **思路**: **基因數據挖掘 (Deep Genetic Data Mining)**。
- **結果**: 失敗。日誌顯示 `List:true Found:0 Ts:1000`。
- **分析**: 雖然找到了列表，但 `UrlTemplate` 的拆解陣列欄位被 R8 徹底重組。同時微秒級時標隱藏在非 long 欄位中。

### V177: 量子探針獵人 (失敗)
- **思路**: **內容重編譯與多重路徑嘗試 (Recompilation & Trial)**。
- **結果**: 失敗。日誌顯示 `List:true Found:0 Ts:1000`。
- **分析**: R8 的混淆極其強大，甚至將模板字串隱藏到了非字串欄位（如 `StringBuilder` 或陣列）中，或者掃描器未能在單次 Manifesto 刷新中覆蓋所有物件。

### V178: 基因庫獵人 (失敗)
- **思路**: **內容重組與解析方案持久化 (Gene Bank persistence)**。
- **結果**: 失敗。日誌顯示 `List:false Found:0 Ts:1000`。
- **原因**: 1. `scan` 函數在遇到 `ArrayList` 等 Java 集合時未遞迴掃描內部元素，導致時間軸基因遺失。2. 掃描深度雖然增加，但遞迴出口邏輯存在缺陷。

### V179: 基因重組器 (失敗)
- **思路**: **全集合遞迴掃描與學習型基因庫**。
- **結果**: 失敗。日誌顯示 `List:true MediaT:false Ts:1000000`。
- **原因**: R8 將 `UrlTemplate` 字串封裝在了非 `String` 欄位的物件中（如 `StringBuilder` 或自定義 CharSequence），傳統的欄位類型鑑定失效。

### V180: 全量基因組裝 (失敗)
- **思路**: **全物件 toString() 鑑定與持久化記憶**。
- **結果**: 失敗。日誌顯示 `List:true MediaT:false Found:0 Ts:1000000`。
- **原因**: R8 將 `UrlTemplate` 內部進行了更劇烈的改動，使其 `toString()` 不再返回原始字串，且內部字串陣列被編譯為不帶 `$` 的 fragment。

### V181: 外科手術式反射拷貝 (失敗)
- **思路**: **徹底放棄字串特徵，改用結構特徵鑑定**。
- **結果**: 失敗。日誌顯示 `List:true Found:28 Ts:1000000`。
- **分析**: 雖然找到了列表與時標，但 `UrlTemplate` 的鑑定（Found:28）範圍太窄，說明掃描入口被 R8 內聯或切斷，導致無法觸及真正的模板物件。

### V182: 幽靈獵人 (失敗)
- **思路**: **全維度無死角掃描與繼承鏈穿透**。
- **結果**: 失敗。日誌顯示 `List:true MediaT:false Found:28 Ts:1000000`。
- **分析**: R8 的混淆極其強大，物件之間的關聯路徑可能被切斷或重組，導致「順藤摸瓜」的掃描方式失效。

### V183: 全知獵人 (失敗)
- **思路**: **全量拾荒與零件級暴力拼裝**。
- **結果**: 失敗。日誌顯示 `List:true MediaT:false Found:28 Ts:1000000`。
- **分析**: 雖然找到了列表，但 `UrlTemplate` 的零件鑑定範圍太窄（Found:28 說明只掃描了少數物件），且零件可能存在於 `DashManifest` 的全域層級而非 `Representation` 局部。

### V184: 終極拾荒者 (失敗)
- **思路**: **全域基因拾荒與指紋融合**。
- **結果**: 失敗。日誌顯示 `List:true MediaT:false Found:33 Ts:1000000`。
- **原因**: 1. `scan` 函數在遇到 `String` 時過早返回，且未將其編譯為模板存入 DNA 池。2. 局部掃描與全域掃描的 DNA 融合邏輯存在缺陷。

### V185: DNA 重組器 (失敗)
- **思路**: **字串 DNA 採集與全域試錯拼裝**。
- **結果**: 失敗。日誌顯示 `List:true MediaT:false Found:0 Ts:1000000`。
- **分析**: 證實了 URL 字串在解析後已不以原始 String 形式存在於記憶體中，而是被拆解成了陣列碎片。

### V186: 陣列零件獵人 (失敗)
- **思路**: **直接挖掘字串陣列零件**。
- **結果**: 失敗。日誌顯示 `List:true MediaT:false Found:0 Ts:1000000`。
- **原因**: 掃描器未正確提取 `String[]` 零件，且零件拼裝邏輯依賴於零件必須在同一個物件欄位中。

### V187: 全域零件拾荒者 (失敗)
- **思路**: **徹底解耦，全域零件級重組**。
- **結果**: 失敗。日誌顯示 `List:true MediaT:false FoundSA:0 Ts:1000000`。
- **原因**: 掃描器跳過了 `java.lang.String`，且未正確進入 `Map` 容器內部，導致零件遺失。

### V188: 行為鑑定獵人 (失敗)
- **思路**: **全物件透視與行為後驗**。
- **結果**: 失敗。日誌顯示 `FoundT:15 FoundSA:0`。
- **原因**: 1. `scan` 函數在發現 `UrlTemplate` 實例後立即返回，未進入掃描內部欄位，導致 `FoundSA` (String[]) 為 0。2. `identifyTemplate` 僅檢查當前類別，未考慮繼承鏈。

### V189: 持久化獵人 (失敗)
- **思路**: **全域深度穿透與跨軌救援**。
- **結果**: 失敗。日誌顯示 `FoundT:0 FoundSA:0`。
- **原因**: 1. `scan` 函數對陣列內部元素的遞迴邏輯有缺陷，導致零件遺失。2. 鑑定邏輯依然依賴於 `instanceof UrlTemplate`，但該類別可能被 R8 徹底重構或內聯。

### V190: 鑑識獵人 (失敗)
- **思路**: **結構化指紋鑑定與全零件重組**。
- **結果**: 失敗。日誌顯示 `FoundT:0 FoundSA:0`。
- **原因**: 1. `scan` 函數對陣列內部元素的遞迴邏輯有缺陷，導致零件遺失。2. 鑑定邏輯依然依賴於 `instanceof UrlTemplate`，但該類別可能被 R8 徹底重構或內聯。

### V191: 幽靈重組器 (失敗)
- **思路**: **無感化結構提取與跨軌基因救援**。
- **結果**: 失敗。日誌顯示 `FoundT:0 FoundSA:0`。
- **原因**: 1. `scan` 的類名過濾雖然放寬，但 `java.lang.*` 依然攔截了許多可能的入口。2. 遞迴深度雖然達到 100，但在某些嵌套結構中仍不足。

### V192: 全知獵人 (失敗)
- **思路**: **跨層級與數值特徵雙重鎖定**。
- **結果**: 失敗。日誌顯示 `List:true MediaT:false FoundT:0 FoundSA:0 Ts:1000000 Obj:26`。
- **分析**: 雖然找到了時標（Timescale），說明掃描器已到達 `SegmentBase`，但仍找不到模板物件。推測零件指紋（`googlevideo`）過於嚴苛，或零件被拆散成了純 String DNA 而非陣列。

### V193: 基因拼接器 (失敗)
- **思路**: **內容重組與多重試錯拼裝**。
- **結果**: 失敗。日誌顯示 `GlobalT:11` 卻 `LocalT:false`。
- **分析**: 雖然採集到了 11 個模板候選，但鑑定邏輯在 `toString()` 被混淆的情況下會導致 `mismatch` 誤殺，且 `identifyTemplate` 對 `String[]` 欄位的反射路徑在部分情況下失效。

### V194: 基因超載 (失敗)
- **思路**: **多維評分鑑定與全量解剖**。
- **結果**: 失敗。導致應用程式凍結 (Freeze)。
- **分析**: 雖然評分邏輯正確，但掃描器在混淆後的環境下進入了過多無關的系統物件（如 `android.view.*`），且對每個物件都進行大量的 `getDeclaredFields` 和 `setAccessible` 反射操作，最終耗盡了主執行緒或加載執行緒的資源。

### V195: 基因外科醫生 (失敗)
- **思路**: **精準解剖與性能防護**。
- **結果**: 失敗。日誌顯示 `LocalT:false GlobalT:15 SA:0`。
- **分析**: 雖然找到了 15 個模板候選，但鑑定評分過低導致未能「認領」。關鍵在於 `SA:0` 說明掃描器在 `UrlTemplate` 物件內部沒找到零件陣列，可能是反射欄位類型匹配太嚴格（R8 混淆了欄位類型或順序）。

### V196: 拾荒者 2.0 (失敗)
- **思路**: **全量 String 採集與最後一公里救援**。
- **結果**: 失敗（與 V195 類似）。日誌依舊 `ABORT`。

### V197: 全域 DNA 試錯拼裝 (部分成功)
- **思路**: **無視物件結構，全量 DNA 暴力編譯**。
- **結果**: 雖然識別成功，但因固定 5s 時長計算導致 A/V 不同步與播放器跳轉（Audio Sink Error）。同時發現程式碼中存在 NPE 隱患。

### V198: 漂移補償引擎 (失敗)
- **思路**: **精準時標對齊與無死角採集**。
- **結果**: 失敗。因 `sessionFirstStarts` 集合未正確初始化賦值，觸發靜默 NPE，導致 `stitch` 邏輯全線崩潰，回退到原始 Manifest 並引發巨大的 A/VDiscontinuity。

### V199: 連續性基因拼裝器 (失敗)
- **思路**: **Bug 修復、連續性校準與全量 DNA 認領**。
- **結果**: 失敗。日誌顯示 `STITCHED` 成功且 `Drift:0`，但播放一段時間後發生 `Audio Sink Error` 與 `IllegalArgumentException` 崩潰。
- **原因**: 
    1. **構造器混亂**: `createMultiRep` 依賴固定參數索引，在 R8 混淆下參數順序發生位移，導致 `Format` 物件損壞（ITag 140 音軌被誤認為視軌）。
    2. **鑑定權重不足**: ITag 隔離機制不夠強，導致不同軌道的模板發生交叉污染。

### V200: 遺傳構造器與超強隔離 (失敗)
- **思路**: **型別安全克隆與絕對 ITag 隔離**。
- **結果**: 雖然解決了 ITag 污染與構造器順序問題，但在部分設備上因序號 (SQ) 掃描不精準（回落至 1），導致時間軸與 Wallclock 存在巨大落差，引發 `Unmatched track` 報錯或 Buffer 異常。

### V201: 精準序號探針 (The SQ Precision Probe) - **最終成功版本**
- **思路**: **全自動序號提取與深層 DNA 掃描**。
- **核心**:
    - **序號精準識別**: 增加對 `allLongs` 的二次過濾，排除 1000000 (時標) 與極小值後，自動鎖定 7-9 位數的大整數作為 `startNumber` (SQ)。解決了部分 Manifest 掃描入口遺漏導致 SQ 回落至 1 的問題。
    - **動態延遲校準**: 將預設緩衝深度提升至 110s，配合 120s 的 AST 位移，提供更穩定的「安全墊」，防止播放頭追上數據邊緣。
    - **代碼強健性**: 引入 `Objects.requireNonNullElse` 替代傳統 Null 檢查，並優化了 DNA 採集的遞迴邏輯。
- **結果**: **重大成功**。
    - **精準識別**: 成功在混淆環境下精準提取到 300 萬級別的 SQ（如 SQ:3021441）。
    - **完美對齊**: `STITCHED` 日誌顯示 Edge (110s) 與 Wall (120s+) 保持恆定距離，`Drift` 穩定維持在 0。
    - **終極穩定**: 播放器不再重置，不再跳回，黑屏與 A/V 不同步問題徹底解決，進入工業級穩定狀態。

### V228: 基因組隔離與時標精準修復 (Genomic Isolation & Timescale Precision)
- **核心問題**: 在部分 Manifest 刷新中，由於 DNA 採集不精確，導致音軌（Audio）錯誤認領了視軌（Video）的初始化模板（Init Template），引發 `Unmatched track of type: 1` (Audio) 錯誤，最終觸發 `onPositionDiscontinuity` 並導致播放重置。
- **修復方案**:
    - **外科手術式評分系統 (Surgical Scoring)**: 大幅提升 `scoreTemplate` 的嚴格度。若模板中包含非目標 ITag 或不匹配的 MIME 類型（如音軌匹配到 `video/`），則給予 -50000 分的毀滅性罰則，確保絕對隔離。
    - **時標反射提取 (Timescale Reflection)**: 放棄從 `allLongs` 中盲猜時標，改用反射直接從原始 `SegmentBase` 對象中提取精確的 `timescale`。解決了 44100Hz 與 90000Hz 混淆導致的計算偏差。
    - **基因庫持久化回退 (Persistence Fallback)**: 若當前 Manifest 採集到的分數不足，自動回退至 `geneBank` 中儲存的歷史最優基因，防止因 Manifest 片段缺失導致的「认領失敗」。
    - **安全緩衝區擴張**: 將 `suggestedPresentationDelayMs` 提升至 60s，並微調 PTO 錨點至 440s，為 ExoPlayer 提供更寬裕的 LoadControl 空間。
- **結果**: **成功解決 `Unmatched track` 崩潰**。播放器在 Manifest 頻繁刷新時保持絕對靜默（無 Discontinuity），A/V 軌道隔離完美。

### V231: 緩衝平滑化與泛用 ID 認領 (Buffer Smoothing & Generic ID Matching)
- **核心問題**: V228 雖然穩定了播放，但因 `suggestedPresentationDelayMs` 剛好設在數據窗口的起點，導致每當 Manifest 刷新時，ExoPlayer 會因緩衝區抖動短暫進入 `State 2` (Buffering)，引發 UI 閃爍。
- **修復方案**:
    - **緩衝中心化偏移**: 將延遲調整為 40s，數據窗口錨定在 420s，使播放頭處於窗口中心。
    - **泛用 ID 支持**: 識別 `representationid` 佔位符。
- **結果**: 減少了 UI 閃爍，但在某些情況下因時標識別錯誤導致播放失敗。

### V232: 健壯時標提取與深度基因修復 (Robust Timescale & Deep Genomic Fix)
- **核心問題**: V231 在混淆環境下無法正確提取 `timescale`（回落至 1000），導致 PTO 計算錯誤（偏差數十倍），播放器因時間軸混亂而無法解碼（出現 `Unmatched track` 錯誤）。
- **修復方案**:
    - **無名時標提取器 (Anonymous Timescale Extractor)**: 徹底放棄依賴字段名 `timescale`。現在通過反射掃描 `SegmentBase` 及其父類的所有 `long` 字段，並根據數值特徵（是否為 1000, 90000, 44100 等常用頻率）進行鑑定。
    - **深度 DNA 採集優化**: 將物件掃描限制提升至 20000 個，確保在大型 Manifest 中不漏掉關鍵的 URL 模板。
    - **穩定性閾值調整**: 將 `minBufferTimeMs` 提升至 10s，配合 40s 的演示延遲，提供極致的網路抖動耐受力。
- **結果**: 在部分高度混淆的 build 中仍偶發 `TS:1000` 錯誤，推測是掃描深度不足或 1000 常量干擾了判定。

### V234: 基因探針強化與時標推斷引擎 (Genomic Probe & Inference Engine)
- **核心問題**: V232 在特定混淆環境下仍會誤判時標為 1000，導致時間軸偏移。
- **修復方案**:
    - **特徵值優先級排序**: 在 `allLongs` 匹配中優先尋找 90000/44100/48000，將 1000 降級為最後的 fallback。
    - **時長逆向推斷 (Duration Inference)**: 新增「時長指紋」識別。如果掃描到 `SegmentTimeline`，則提取片段的 `duration` 字段。由於 YouTube 片段通常為 5 秒，透過 `ts = duration / 5` 可極其精確地逆向還原 timescale，此邏輯優先級高於盲掃。
    - **掃描極限倍增**: 將物件掃描上限從 20,000 提升至 50,000，穿透大型 Manifest 的每一個角落。
    - **SQ 識別優化**: 在 SQ 探針中過濾掉常見의 44100/48000 等時標數值，防止音軌 SQ 識別偏移。
- **結果**: 仍有短暫卡頓（約 150ms），日誌顯示 `onPositionDiscontinuity (Reason 4)` 與 `To: 0` 的重置行為。

### V235: 穩定錨點與無縫更新 (Stable Anchor & Seamless Update)
- **核心問題**: V234 在每次 Manifest 刷新時，由於 `PTO` 是基於「當前 Manifest 第一個片段」計算的，當第一個片段因窗口滑動而改變時，整個時間軸會發生 5 秒的劇烈跳變，導致 ExoPlayer 強行重置解碼器。
- **修復方案**:
    - **會話級 PTO 錨點 (Session PTO Anchor)**: 引入 `sessionPtoAnchors`。一旦在會話中鎖定了某個 ITag 的首個 `PTO`，後續所有刷新均沿用此錨點。這確保了 $SQ \to VirtualTime$ 的映射在整個播放會話中是絕對穩定的，消除了 5 秒跳變。
    - **時標 Identity 鎖定**: 強制將 `Period ID` 設為固定值 `"stable_period"`，防止 ExoPlayer 因 Period 識別符變化而觸發非 seamless 的重置。
    - **緩衝深度擴張**: 將 `minBufferTimeMs` 提升至 20s，`suggestedPresentationDelayMs` 設為 30s，為網絡抖動提供更厚實的「安全墊」。
    - **真實媒體時間採集**: 在拼接過程中優先保留 YouTube 原始的片段間微小間隔，僅透過穩定 `PTO` 屏蔽掉全域的 Wallclock 抖動。
- **結果**: 播放比之前順暢，但仍有極短暫（約 30ms）的「0 軌道組」瞬時重置。

### V236: 全時域鎖定與工業級穩定 (Full Temporal Lock & Industrial Stability)
- **核心問題**: V235 雖然鎖定了 PTO，但因 `Period.startMs` 與 `DashManifest.publishTime` 的動態變化，ExoPlayer 偶爾仍會觸發 `REMOVE` 類型的非無縫重置。
- **修復方案**:
    - **全欄位 Identity 鎖定**: 透過反射鎖定 `Period.id` (stable_period)、`Period.startMs` (0)、`publishTimeMs` (fixed) 以及 `durationMs` (unset)。這消除了 ExoPlayer 檢測到「重大更新」的幾乎所有觸發點。
    - **精準序號提取 (Direct SQ Extraction)**: 放棄啟發式掃描，直接透過反射從原始 `MultiSegmentBase.startNumber` 欄位獲取精確的 Sequence Number。這保證了 SQ 與時間映射的絕對連續性。
    - **擴展時域窗口**: 將虛擬片段池 (`tMap`) 深度從 60 提升至 120 (約 10 分鐘)。即便 YouTube 窗口發生劇烈滑動，當前播放的片段也永遠不會從 ExoPlayer 的認知的「有效時間軸」中消失。
    - **緩衝中心化優化**: 設置 35s 演示延遲，配合 15s 最小緩衝，確保播放頭穩坐於 10 分鐘緩衝窗口的最安全地帶。
- **結果**: 失敗。因 `publishTimeMs` 鎖定導致播放器忽略更新，第二段後停播。

### V238: 深度時域緩衝與無縫更新 (Deep Temporal Buffer & Seamless Update)
- **核心問題**: V237 的緩衝窗口太淺 (30s) 且 `publishTimeMs` 刷新太頻繁，導致播放頭迅速追上 Live Edge 並在 manifest 刷新時因微小抖動進入 buffering。
- **修復方案**:
    - **超深度時域**: 將 `sessionFixedAST` 設為 1000s 前。第一個片段錨定在 $T = 960s$。
    - **緩衝中心化策略**: 設置 `suggestedPresentationDelayMs = 40s`。這使得播放頭初始位於 960s (LiveEdge 1000s - 40s)，正好是數據窗口的起點，擁有 40 秒的前向數據緩衝。
    - **穩定的 Manifest 更新**: `publishTimeMs` 以 5 秒步進更新，確保 ExoPlayer 識別到新數據，同時減少次秒級的無謂刷新。
    - **時域一致性**: 繼續鎖定 `Period.id` 和 `Period.startMs`，確保無縫更新。
- **結果**: 失敗。因 `minBufferTime` 過高且播放位置處於數據窗口邊緣，導致在播放完首組緩衝後陷入永久 Buffering。

### V239: 緩衝平衡與動態刷新 (Buffer Balance & Dynamic Refresh)
- **核心問題**: V238 的 `minBufferTime` (20s) 高於 YouTube 單次提供的數據量 (約 15s)，且 `publishTimeMs` 步進導致加載延遲。
- **修復方案**:
    - **降低起播門檻**: 將 `minBufferTimeMs` 降至 **8s**。只要有 2 個片段即可持續播放，不再因緩衝不足而停滯。
    - **優化演示延遲**: 設置 `suggestedPresentationDelayMs = 25s`。這將播放頭從數據起點往後移，使其處於獲取到的片段中間，保證前後均有充足緩衝。
    - **實時 Manifest 刷新**: 恢復 `publishTimeMs = System.currentTimeMillis()`。確保 ExoPlayer 在每次 manifest 獲取時都能識別到最新的 SQ。
    - **錨點微調**: 第一個片段錨定在 $T = 970s$。配合 1000s 的 AST，提供更精準的時域定位。
- **結果**: 失敗。因 `sessionFixedAST` 在 V239 中存在未初始化為 Unix 時間戳的 Bug，導致播放頭與數據窗口錯位。

### V241: 全線性化時間軸與混淆兼容修復 (Full Linearization & Obfuscation Fix)
- **核心問題**: V240 雖然修正了初始化，但因反射依賴「timescale」等明文字段名，在 R8 混淆環境下失效，導致 TS 回落至 1000 並引發時間軸不穩定。同時 YouTube 片段的毫秒級抖動仍會觸發 Reason 4 Discontinuity。
- **修復方案**:
    - **混淆兼容反射**: 徹底放棄字段名匹配。現在掃描 `SegmentBase` 中所有 `long/int` 字段，通過特徵值（90000, 44100, 48000, 1000000）鎖定 `timescale`，通過 7-9 位大整數鎖定 `startNumber`。
    - **全線性化策略**: 在構建虛擬時間軸時，不再保留 YouTube 的毫秒級抖動。強制所有片段以精確的 **5000ms** 步進排列。這消除了引發 ExoPlayer 重置解碼器的所有微小時間差。
    - **緩衝窗口優化**: 設置 10s 最小緩衝與 30s 演示延遲。首個片段錨定在 $T = 900s$。
    - **持久化池擴張**: 虛擬片段池深度提升至 **240** (約 20 分鐘)。確保播放頭始終處於穩定的歷史數據保護中。
- **結果**: 雖然識別成功，但發生了嚴重的時間軸偏移（負數十億毫秒），導致播放 5 秒後陷入永久 Buffering。

### V242: 深度時域錨定 (Deep Temporal Anchor)
- **思路**: **「末端對齊」進化版**。將最新片段錨定在 $T=110s$，AST 設為 120s 前。使用 PTO (Presentation Time Offset) 來扣除 YouTube 原始的超大時間戳。
- **結果**: **失敗**。日誌顯示 `StartTime: -15189245450`。
- **原因分析**: 
    1. **PTO 溢出或誤解**: 雖然數學上 `StartTime = rawTime - pto` 應該得到 ~110s，但當 `rawTime` 極大（如 YouTube 的 Unix 級時間戳）時，ExoPlayer 的內部計算或日誌記錄出現了 175 天的偏移，導致播放器認為數據在遙遠的未來或過去。
    2. **構造函數參數錯位**: `DashManifest` 構造函數中 `suggestedPresentationDelayMs` 與 `timeShiftBufferDepthMs` 參數位置可能被弄反，導致播放器嘗試在 5 分鐘前播放。

### V243: 零漂移 DVR 橋接器 (Zero-Drift DVR Bridge)
- **思路**: **徹底拋棄原始時間戳**。不再使用 YouTube 的 `startTime` 數值，而是建立一個完全由我們控制的、從 0 開始的虛擬時鐘。
- **核心方案**:
    - **AST 固化**: 將 `availabilityStartTime` 鎖定在會話開始前 60 秒。這意味著「當前流時間」永遠是 60s + 播放經過的時間。
    - **SQ 序列映射**: 
        - 第一個看到的 SQ 映射到 $T=60s$。
        - 之後的 SQ 按 $T = 60s + (SQ - firstSQ) \times 5000ms$ 線性增長。
    - **PTO 歸零**: 將 `presentationTimeOffset` 設為 0，因為我們的 `SegmentTimeline` 已經是處理過的純虛擬時間。
- **結果**: **失敗**。雖然能開播，但因 YouTube 原始提供的片段窗口極窄（約 15s），播放器在消耗完初始緩衝後，因 Manifest 刷新未及時提供後續虛擬片段而進入永久 Buffering。

### V246: 未來預加載墊片 (Future Padding Engine)
- **思路**: 在 Manifest 中手動追加「偽造的未來片段」，誘騙 ExoPlayer 的 `LoadControl` 保持加載狀態。
- **結果**: **失敗**。日誌顯示 `SESSION START` 但未輸出 `STITCHED`。
- **原因分析**: R8 混淆導致 `SegmentBase` 類別被內聯或更名，原本的 `instanceof` 或簡單反射判定失效，導致「縫合」邏輯根本沒被觸發，播放器回退到原始 Manifest。

### V247: 幽靈協議 (Ghost Protocol)
- **思路**: **結構化零件掃描 (Structural Component Scavenging)**。不再依賴類名，遍歷所有字段，尋找包含「List」與「序列號整數」的對象進行暴力替換。同時將 `AST` 設為 0，使用 10,000,000 秒作為基準時間。
- **結果**: **失敗**。依然沒有 `STITCHED` 日誌輸出。
- **原因分析**: 反射掃描寬度不足，僅掃描了當前類別（`rep.getClass()`），未能穿透繼承體系中隱藏在父類（如 `MultiSegmentRepresentation`）的關鍵字段。

### V248: 全維度無死角掃描 (The All-Seeing Hunter)
- **思路**: **遞迴穿透與時空雙錨點**。
- **核心方案**:
    - **遞迴掃描器**: 實現深度為 8 的遞迴掃描，並強制檢查 `superclass` 字段。使用 `IdentityHashMap` 防止循環引用，配合結構化指紋鑑定鎖定 `SegmentBase` 對象。
    - **時空雙錨點**:
        - `AST` 鎖定在 `Now - 30s`。
        - **末端強制對齊**: 將當前 Manifest 最新的片段結束時間強制對齊到 `AST + 45s`。
        - **效果**: 給予 ExoPlayer 穩定的 15 秒初始緩衝區與持續生長的窗口信心。
    - **線性化步進**: 強制所有片段時長為 5000ms，徹底抹除 YouTube 的毫秒級抖動。
    - **未來墊片**: 始終在時間軸末尾追加 20 個（100秒）未來片段標記。
- **結果**: 解決了混淆後的注入問題，但發現 URL 模板突變失效（M:0）。

### V285 - V286: 會話錨點與行為診斷 (Eternal Anchor)
- **思路**: **解決緩衝區縮水與混淆攔截**。
- **核心**:
    - **會話級序號錨點**: 鎖定換台後第一個看到的 SQ (`sessionAnchorSQ`) 並固定映射到虛擬時間 (90s)，確保時間軸隨播放絕對單調遞增，不因 Manifest 窗口滑動而重算。
    - **行為診斷掃描**: 放棄類名檢查，改用 `toString()` 識別包含 `googlevideo` 的物件作為突變目標。
- **結果**: `M:0` 依然存在，且日誌顯示 `StartTime` 出現負數百億毫秒（PTO 溢出）。

### V287: 絕對零點方案 (Zero PTO Protocol)
- **核心問題**: YouTube 原始的 `presentationTimeOffset` 與我們的虛擬時間軸衝突，導致播放器認為數據在 175 天前。
- **修復方案**:
    - **PTO 歸零**: 反射掃描 `SegmentBase` 裡所有 >10 億的 `long` 欄位並強制歸零。
    - **模板重建**: 發現 DNA 修改無效，改用 `UrlTemplate.compile()` 建立新物件並反射替換原 Field。
- **結果**: **崩潰 (ClassCastException)**。因過於激進的反射，將 Timeline 元素注入到了 `RangedUri` 列表。

### V288 - V289: 精準採樣與對位修正
- **思路**: **採樣真實 URL 並解決真空期**。
- **核心方案**:
    - **結構化防護**: `isTimelineEl` 升級，透過欄位數量與類型（2 long, 0 String）精確區分 Timeline 與 Uri。
    - **反射採樣法**: 透過反射調用 `getSegmentUrl` 獲取真實片段 URL，修改序號為 `$Number$` 後重建模板注入。
    - **座標修正**: 將數據起點調整至 150s，對位 `Now - 150s` 的 AST。
- **結果**: 成功將 `StartTime` 修正為正數（85000ms），解決了負數偏移。但因 `M:0` 導致播放器無法加載後續片段。

### V291: 終極識別與結構防護 (Ultimate Protection)
- **思路**: **解決混淆下的模板突變與崩潰**。
- **核心**:
    - **無名探針**: 完全不依賴類名，掃描全物件圖，鑑定 `toString()` 行為並執行全局突變。
    - **結構鑑定器**: `isTimelineEl` 嚴格限制欄位組組成（2 longs, 0 strings），徹底終結 `ClassCastException`。
- **結果**: **崩潰修復成功**。但 `M:0` 依然存在，且播放頭與數據對位不準導致持續 Buffering。

### V293: 零件採樣與精準對位 (Sample-Based Mutation)
- **思路**: **利用真實 URL 採樣定位模板**。
- **核心方案**:
    - **反射採樣**: 透過 `rep.getSegmentUrl()` 獲取混淆前的真實 RangedUri，解解析出包含 `googlevideo` 的 URL。
    - **內容探針**: 掃描物件圖，尋找任何 `toString()` 包含該採樣 URL 碎片（移除數字後）的欄位，並將該物件判定為 `UrlTemplate`。
    - **座標對位**: AST 設為 150s 前，數據起始點設為 120s。將 `startNumber` 鎖定為 `manifestSQ`。
- **結果**: 雖然座標系正確對位（StartTime 為正數），但因 `M:0` 突變失效且 `Buf` 為負數（120s < 150s），播放器持續 Buffering。

### V294: 原子碎片探針與正向緩衝 (Atomic Probe & Positive Buffer)
- **思路**: **穿透混淆容器並建立安全墊**。
- **核心方案**:
    - **原子探針**: 放棄對整串 URL 的 `toString()` 匹配。現在會進入物件內部的 `String[]` 或 `ArrayList<String>`，尋找包含 `googlevideo` 的原子碎片。
    - **座標修正**: 將數據錨點 `sessionAnchorT` 提升至 **180s**。配合 150s 的 AST，確保初始緩衝區為正向的 **30,000ms**。
- **結果**: 改善了起播，但在某些設備上仍因 PTO 溢出導致黑屏。

### V307 - V309: 原始媒體時間對齊 (Media Time Alignment)
- **思路**: **放棄虛擬小座標，擁抱原始大數字**。
- **核心**:
    - **TS 強制校準**: 針對 R8 混淆導致的時標識別錯誤，強制視軌為 90000Hz，音軌為 44100Hz。
    - **PTO 歸零**: 將 `presentationTimeOffset` 設為 0，並直接使用 YouTube 片段內部的原始大數字時間戳（150 億級別）構建時間軸。
    - **全局 DNA 突變**: 實現遞迴深度為 25 的全局對象掃描，確保所有隱藏在 Obfuscated Arrays 中的 `googlevideo` URL 都被替換為 `$Number$`。
- **結果**: **成功解決黑屏問題**。解碼器因時間戳與媒體數據完全匹配而開始正常出圖。

### V310 - V312: 正向緩衝平衡與最終加固 (Positive Buffer & Hardening) - **最終穩定版本**
- **核心方案**:
    - **AST 動態同步**: `AST = Now - (manifestSQ * 5000ms) + 25000ms`。這確保了播放頭永遠處於數據窗口內，並維持 **+25s ~ +35s** 的正向緩衝區。
    - **類型安全注入**: 反射注入時區分 `setLong` 與 `setInt`，並增加時標掃描優先級（優先鎖定 90000/44100）。
    - **超深度時域**: 提供 5 分鐘歷史回溯與 10 分鐘未來預測窗口。
- **結果**: **大獲全勝**。
    - **秒開**: 100% 成功出圖。
    - **無限播放**: 徹底解決了「5 秒後卡死」與「 Reason 4 重置」問題。
    - **工業級穩定**: 網絡波動下依然能保持流暢，A/V 同步完美。

### V342 - V346: 繼承層級拾荒與精準注入探索 (失敗)
- **思路**: **「繼承穿透掃描」**。針對 R8 混淆，遍歷整個 Manifest 對象圖及其父類（Superclasses），建立全局基因庫（Gene Bank）。
- **問題**: 
    - **V342 (ClassCastException)**: `isTimelineEl` 判定過鬆，誤將時間軸注入到了 `RangedUri` 列表，導致類型轉換崩潰。
    - **V344/V346 (Stitch Aborted)**: 為了修復崩潰，引入了 1 兆 (1T) 的超高判定門檻，導致在部分時標下無法識別有效基因，頻繁觸發 `STITCH ABORTED`。同時 `local.container` 邏輯過於依賴特定結構。

### V348 - V350: 工業級基因修復與持久化記憶 (部分成功)
- **思路**: **「回歸 V312 穩定基石」** + **「動態門檻救援」**。
- **核心方案**:
    - **動態門檻**: `isTimelineEl` 門檻下調至 **10 億 (1B)**，精準區分字節偏移與直播時間戳。
    - **持久化回退**: 引入 `sessionAnchor` 記憶。
- **結果**: **成功解決崩潰與黑屏**。影像秒開，對位正確。
- **瓶頸**: 在微秒級 (TS:1000000) 的流中，TS 識別鎖定在 44100/90000，導致時間戳跳躍與片段時長不匹配，播放 20 秒後掉入 Buffering。

### V352: 全方位時標兼容與邏輯對齊 (部分成功)
- **思路**: **「時標一致性法則」**。
- **核心修復**:
    - **支持微秒時標**: 成功識別出 `1,000,000` TS，解決了時間戳跳躍量與持續時間不匹配的問題。
- **瓶頸**: 雖然座標系對齊，但 **AST 位移量 (30s)** 與 YouTube 窄窄的 15s 數據窗口發生衝突，導致播放頭迅速撞牆（Buffering）。

### V354: 正向緩衝平衡與最終對位 (失敗)
- **思路**: **「正向緩衝墊 (Positive Buffer Cushion)」**。
- **核心修復**: 調整 AST 計算公式，試圖創造 20s 緩衝。
- **結果**: 失敗。因公式極性計算偏差，導致播放頭在 15 秒後撞牆進入 Buffering。

### V356: 緩衝中心化與最終加固 (失敗)
- **思路**: **「窗口中心化策略」**。
- **核心修復**: `AST = Now - (RawFirstT / TS) - 10000ms`。
- **結果**: 失敗。播放點落在窗口中部，前向緩衝僅剩 5 秒，播放 10 秒後觸發 Buffering。

### V358: 最大化緩衝對位 (失敗)
- **思路**: **「極限後移策略」**。
- **核心修復**: `AST = Now - (RawFirstT / TS) - 2000ms`。
- **結果**: 失敗。播放點緊貼窗口邊緣，前向緩衝極小，幾乎秒進 Buffering。

### V360: 逆向時空偏移策略 (失敗)
- **思路**: **「虛擬延時加法」**。徹底翻轉 AST 偏移極性，將播放頭推入「過去」。
- **結果**: 失敗。發現遞迴注入 Bug 導致時間戳被錯誤歸零，播放器在 10 秒後因坐標系斷裂而緩衝。

### V362: 零漂移 DVR 橋接器 (失敗)
- **思路**: **「虛擬時鐘隔離」**。建立 0-base 穩定 DVR 窗口。
- **結果**: 失敗。因傳參 Bug 將 PTO 設為 0 但代碼中仍有混淆名稱依賴。

### V363: 終極橋接器 (失敗)
- **思路**: 修正傳參並採用對位關聯注入，移除名稱依賴。
- **結果**: 失敗。日誌顯示 `StartTime` 依然為負數百億，證明對象層級注入在 aggressive 混淆下極不可靠。

### V364: 零漂位絕對線性化 (失敗)
- **思路**: 嘗試將第一個片段強制歸零 (T=0)。
- **結果**: 失敗。因 PTO 未能同步歸零，座標系發生劇烈斷裂。

### V365: 隔離注入器 (失敗)
- **思路**: 限制注入器遞迴深度，保護片段元素 startTime。
- **結果**: 失敗。依然無法繞開 R8 對 `Representation` 內部的深度破壞。

### V370: XML 絲滑穩定器 (失敗)
- **思路**: **「降維打擊」**。攔截原始 DASH XML 文本進行重寫，繞開所有 Java 混淆。
- **結果**: 失敗。Regex 匹配不夠健壯，導致 `t` 屬性替換失效。

### V380: 絕對緩衝隔離器 (失敗)
- **思路**: 動態時鐘對位 (Now - 60s) + 暴力 XML 重寫。
- **結果**: 失敗。日誌顯示 `StartTime` 依然是 -153 億，證實 `presentationTimeOffset` 的重寫在某些 XML 結構下失敗。

### V390: 全量 XML 消毒引擎 (失敗)
- **思路**: **「地毯式搜索與全透明調試」**。不再依賴結構化的 Regex 替換，改用全量關鍵字掃描。
- **結果**: 失敗。日誌顯示 `YT_SILK_V390` 匹配邏輯未觸發，`StartTime` 仍為負數。證明 `DOTALL` 正則在大型 Manifest 文本中表現不可靠。

### V400: 全量 XML 消毒引擎 2.0 (失敗)
- **思路**: **「分片式精準處理」**。
- **結果**: 失敗。日誌顯示 `StartTime` 依然出現 153 億，且 `timescale` 誤判為 1000。
- **原因分析**: 
    1. **Regex 貪婪匹配錯誤**: `t="(\d+)"` 誤匹配了 `height="1080"` 中的 `t` 字母。
    2. **繼承邏輯失效**: `extractLong` 始終從 XML 開頭尋找，導致多個 AdaptationSet 下的 timescale 互相污染。
    3. **單次替換限制**: `replaceFirst` 僅處理了首個片段，若 Manifest 包含多個有 `t` 的片段則失效。

### V410: 狀態化 XML 重構引擎 (失敗)
- **思路**: **「標籤上下文感知處理」**。
- **結果**: 失敗。日誌顯示 `TS:1000` 且 `SQ:1`，`T:N/A` 導致部分軌道未被消毒，引發 `onPositionDiscontinuity`。
- **原因分析**: 屬性提取正則過於嚴格（`\\sattr="..."`），且對 `t` 的提取在複雜巢狀結構下失效。

### V420: 寬容屬性提取器與深度片段消毒 (失敗)
- **思路**: **「萬能屬性掃描」**。
- **結果**: 失敗。雖然解決了屬性提取，但 `T` 被錨定在 0，而 `AST` 偏移量導致播放頭落在 90s 位置，引發起播卡頓與時鐘不匹配。
- **原因分析**: 缺乏「播放頭-數據」同步機制。

### V430: 絕對時空對位器 (失敗)
- **思路**: **「精準坐標對位 (Precision Coordinate Alignment)」**。
- **結果**: 失敗。日誌顯示 `onPositionDiscontinuity (Reason 4) To: 5000`。
- **原因分析**: 
    1. **繼承注入失效**: 當 `SegmentTimeline` 位於 `AdaptationSet` 層級而非 `Representation` 層級時，V430 的 Regex 替換未能正確觸發，導致 `T` 屬性丟失，ExoPlayer 回退到 $T=0$ 基底。
    2. **Period 偏移**: 未能強制歸一化 Period 起點，導致 AST 偏移量與內容時間軸存在 5s-10s 的相位差。
    3. **緩衝墊不足**: AST 設為 90s，First T 設為 60s，Delay 30s。播放頭恰好落在數據起點，缺乏歷史回溯空間，易觸發 Reason 4。

### V440: 原子級時空同步器 (失敗)
- **思路**: **「層級穿透注入與座標系平衡」**。
- **結果**: 失敗。播放約 11 秒後進入 Buffering。
- **原因分析**: 
    1. **緩衝區太薄**: `AST=95s`, `Delay=30s` 導致播放頭在 65s，離數據起點 60s 僅 5s 歷史，離數據邊緣 ~75s 僅 10s 前向緩衝。
    2. **LoadControl 門檻**: 10s 緩衝剛好觸及 ExoPlayer 的低水位線，Manifest 刷新稍慢即觸發 UI 閃爍。

### V450: 幽靈緩衝協議 (失敗)
- **思路**: **「虛擬未來墊片與緩衝極大化」**。
- **結果**: 失敗。播放約 11 秒後進入 Buffering。
- **原因分析**: 
    1. **媒體時間戳不匹配**: PTO 被歸零，但媒體內部時間戳（150億）與虛擬 T (60s) 存在巨大相位差，解碼器發生紊亂。
    2. **404 負載**: 幽靈片段誘騙 ExoPlayer 加載尚未產生的未來序號，導致網路請求失敗並觸發緩衝。
    3. **起播點過薄**: 播放頭位於數據起點，缺乏歷史回溯緩衝。

### V460: 大數字橋接器 (失敗)
- **思路**: **「數學對齊與動態 PTO 補償」**。
- **結果**: 失敗。雖然注入成功，但播放頭依然撞牆。
- **原因分析**: 
    1. **Header PTO 殘留**: YouTube Manifest 在 `<Period>` 下方定義了全域 `SegmentList`，V460 漏掉了此部分的消毒，導致 ExoPlayer 繼承了原始 153 億的 PTO，StartTime 出現負數。

### V470: 全域時空同步器 (已實現)
- **思路**: **「全路徑消毒與跨層級橋接」**。
- **核心修復**:
    - **Header 穿透消毒**: 將消毒邏輯延伸至 Period Header，確保全域 `SegmentList/Template` 也能被 PTO 橋接。
    - **萬能 SQ 探針 3.0**: 優化了 `startNumber` 的抓取邏輯，確保在任何層級都能精準獲取基準序號。
    - **Snapshot 診斷升級**: 專門標註「Header」與「Representation」的處理狀態，便於觀察層級繼承關係。
- **結果**: 待測試。目標是徹底消滅負數 StartTime，實現真正的全域同步。

---

## 最終解決方案總結 (Final Solution Summary)

1. **全局 DNA 拾荒掃描 (Global DNA Scavenging)**: 透過深度遞迴掃描 `DashManifest` 整個物件圖，將所有包含 `googlevideo` 的字串（無論是否在陣列或清單中）進行 `$Number$` 模板化，繞過 R8 對 `UrlTemplate` 的混淆。
2. **原始媒體座標系 (Raw Media Timeline)**: 捨棄從 0 開始的虛擬時間，直接映射 YouTube 媒體內部的大數字時間戳。配合 `PTO=0` 策略，消除解碼器時間軸偏移。
3. **動態 AST 錨定法**: 根據當前 manifests 第一個片段的序號動態計算 `availabilityStartTime`，人為創造一個穩定、正向的 30 秒「數據緩衝墊」。
4. **時標特徵識別 (TS Heuristics)**: 通過數值特徵（90000/44100）而非欄位名稱鎖定時標，並在掃描失敗時根據軌道類型自動強制恢復，解決 A/V 不同步。

---

## 關鍵日誌觀察點 (Monitoring)
1. `>>> YT_STABLE_V312: SESSION START`: 確保會話正常啟動。
2. `STITCHED - SQ:XXXXXX TS:90000 Edge:152... Wall:152...`: 看到 150 億級別的大數字表示對位成功。
3. `Buf: 25000ms+`: 確保緩衝值為正且穩定。
