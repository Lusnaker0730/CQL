# Bug Fix Log

> 每次 Bug 修正的詳細記錄，包含時間、分類、根因分析、修正方式與測試驗證。

---

## 總覽索引

| # | 日期 | 嚴重程度 | 分類 | 標題 | 根因類型 | Commit |
|---|------|----------|------|------|----------|--------|
| 038 | 2026-02-23 | Critical | 資料庫連線池（後端） | HikariCP 連線池耗盡導致所有 API 逾時、無法登入 | 配置遺漏 | [`448c674`](../../commit/448c674) |
| 037 | 2026-02-23 | High | 術語查詢（前端） | Autocomplete freeSolo 以顯示標籤覆蓋系統 URL 致術語查詢 503 | 邏輯錯誤 | [`448c674`](../../commit/448c674) |
| 036 | 2026-02-23 | Medium | 指標庫表格（前端） | MeasureLibrary 虛擬捲動表頭與內容欄位錯位擠壓 | UX 設計缺陷 | [`448c674`](../../commit/448c674) |
| 035 | 2026-02-22 | High | 品質指標儀表板（後端） | DashboardService 多處 NullPointerException 導致所有 Dashboard API 回傳 500 | 邏輯錯誤 | [`3f4c1c5`](../../commit/3f4c1c5) |
| 034 | 2026-02-22 | Low | 品質指標儀表板（前端） | Recharts ResponsiveContainer 初始化時計算 width/height 為 -1 | 配置遺漏 | [`3f4c1c5`](../../commit/3f4c1c5) |
| 033 | 2026-02-22 | Medium | CQL 編輯器（前端） | 工具列 Undo/Redo 按鈕無效 — Redux 歷史與 Monaco 原生 undo 脫節 | 架構缺陷 | [`dca6617`](../../commit/dca6617) |
| 032 | 2026-02-21 | Medium | eCQM 資料需求（後端） | DataRequirements 未解析 ToConcept 包裝的 CodeRef 及 Case 包裝的日期屬性 | 資料處理錯誤 | [`b50d94a`](../../commit/b50d94a) |
| 031 | 2026-02-21 | High | eCQM 種子資料（後端） | 種子 CQL 語法錯誤導致 DataRequirements 標籤頁空白 | 資料處理錯誤 | [`63a5781`](../../commit/63a5781) |
| 030 | 2026-02-21 | High | API 客戶端（前端） | departmentApi/ehrApi/indicatorApi 使用原生 axios 無 JWT 攔截器致 401 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| 029 | 2026-02-21 | High | 通知系統（前後端） | SSE EventSource 無法傳送 Authorization 標頭致 401 | 架構缺陷 | [`63a5781`](../../commit/63a5781) |
| 028 | 2026-02-21 | Medium | Docker 基礎設施（後端） | DataInitializer 僅在 dev profile 啟用，Docker 環境無種子資料 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| 027 | 2026-02-21 | Medium | Docker 基礎設施（後端） | PatientImportEntity @Lob 與 PostgreSQL schema validation 不相容 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| 026 | 2026-02-21 | Critical | Docker 基礎設施（後端） | Flyway V24-V29 使用 H2 語法導致 PostgreSQL 部署失敗 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| 025 | 2026-02-21 | High | CDS Authoring（後端） | CQL 產生器未對 list 型別表達式自動加 exists() 導致驗證失敗 | 邏輯錯誤 | [`0d418f1`](../../commit/0d418f1) |
| 024 | 2026-02-21 | Medium | eCQM 資料需求（後端） | DataRequirements 未解析 Equal/Equivalent + CodeRef 模式（如 E.class ~ "AMB"） | 資料處理錯誤 | [`53b19ca`](../../commit/53b19ca) |
| 023 | 2026-02-21 | Medium | Test Case Builder（後端） | Encounter.class 下拉選單顯示 1115 個代碼而非 11 個 | 資料處理錯誤 | [`3fc6de0`](../../commit/3fc6de0) |
| 022 | 2026-02-20 | Low | Test Cases（前端） | 測試案例結果表格族群名稱未中文化 | i18n 遺漏 | [`0260852`](../../commit/0260852) |
| 021 | 2026-02-20 | Medium | eCQM 資料需求（後端） | DataRequirements 未解析 FHIRHelpers.ToString 包裝的屬性比較 | 資料處理錯誤 | [`66a9ee2`](../../commit/66a9ee2) |
| 020 | 2026-02-20 | Medium | eCQM 資料需求（前後端） | DataRequirements 標籤頁未顯示 Where 子句中的篩選條件 | 架構缺陷 | [`8efa589`](../../commit/8efa589) |
| 019 | 2026-02-20 | High | CDS Hooks Sandbox（後端） | CDS Prefetch 執行清除 patientId 導致 Patient context 失效 | 邏輯錯誤 | [`c08372c`](../../commit/c08372c) |
| ~~018~~ | 2026-02-20 | — | CQL Engine（後端） | ~~FHIR Coding→Code 轉換~~ **已撤回**（CQL Engine 已透過 FHIRHelpers 處理） | 誤判 | [`878deef`](../../commit/878deef) → reverted |
| 017 | 2026-02-20 | High | CDS Hooks Sandbox（後端） | PrefetchRetrieveProvider 未展開 ValueSet 導致代碼過濾失效 | 架構缺陷 | [`878deef`](../../commit/878deef) |
| 016 | 2026-02-20 | High | Test Case Builder（前後端） | CodeableConcept dropdown 使用 ValueSet URL 而非 CodeSystem URL | 資料處理錯誤 | [`6ca7a86`](../../commit/6ca7a86) |
| 015 | 2026-02-20 | High | CQL Engine（後端） | VSAC ValueSet 未連接 CQL Engine 導致 CDS 規則失效 | 架構缺陷 | [`69dd9a1`](../../commit/69dd9a1) |
| 014 | 2026-02-20 | Medium | Test Case Builder（前端） | CodeableConcept boundCodes 未使用下拉選單 | UX 設計缺陷 | [`69dd9a1`](../../commit/69dd9a1) |
| 013 | 2026-02-20 | Medium | CQL Builder（前端） | TWCORE 選碼導致 Monaco Editor 白屏 | 配置遺漏 | [`4c9ae86`](../../commit/4c9ae86) |
| 012 | 2026-02-20 | Medium | 跨模組（前端） | Monaco Editor 夜間模式白屏 | 配置遺漏 | [`e375b1e`](../../commit/e375b1e) |
| 011 | 2026-02-20 | Medium | 版面配置（前端） | Footer 位置異常：flexbox 佈局修正 | UX 設計缺陷 | [`d82710d`](../../commit/d82710d) |
| 010 | 2026-02-20 | Low | CDS Hooks Sandbox（前後端） | CDS Card 顯示所有表達式擠在一行 | UX 設計缺陷 | [`5e69d32`](../../commit/5e69d32) |
| 009 | 2026-02-20 | High | Test Case Builder（前端） | FHIR Choice Type 序列化錯誤（value → valueQuantity） | 資料處理錯誤 | [`5e69d32`](../../commit/5e69d32) |
| 008 | 2026-02-19 | High | CDS Hooks Sandbox（後端） | CDS Sandbox Invoke 所有 CQL 表達式回傳 null | 資料處理錯誤 | [`fccd012`](../../commit/fccd012) |
| 007 | 2026-02-19 | Medium | 版面配置（前端） | Footer fixed 定位仍遮擋操作按鈕 | UX 設計缺陷 | [`b570119`](../../commit/b570119) |
| 006 | 2026-02-19 | Critical | Backend 基礎設施 | Backend OOM 導致所有 API 無回應 | 配置遺漏 | [`660347a`](../../commit/660347a) |
| 005 | 2026-02-19 | Low | 版面配置（前端） | Footer 覆蓋頁面內容 | UX 設計缺陷 | [`741b7dc`](../../commit/741b7dc) |
| 004 | 2026-02-19 | Medium | CDS Hooks Sandbox（前端） | CDS Sandbox 修改資料後無法重新執行 | 邏輯錯誤 | [`741b7dc`](../../commit/741b7dc) |
| 003 | 2026-02-19 | Medium | Test Case Builder（前端） | Observation status 欄位允許自由輸入導致無效值 | UX 設計缺陷 | [`741b7dc`](../../commit/741b7dc) |
| 002 | 2026-02-19 | Medium | CQL Translation（後端＋前端） | CQL 翻譯失敗時 Builder 無法顯示已解析的部分結構 | 邏輯錯誤 | [`f9b3e33`](../../commit/f9b3e33) |
| 001 | 2026-02-19 | High | CQL Builder（前端） | CQL Builder 解析 CQL 靜默失敗 | 邏輯錯誤 | [`3ee28f8`](../../commit/3ee28f8) |

### 根因類型說明

| 類型 | 說明 |
|------|------|
| 邏輯錯誤 | 程式邏輯或條件判斷有誤 |
| UX 設計缺陷 | 介面設計不符合使用者預期 |
| 配置遺漏 | 環境設定、參數未正確配置 |
| 資料處理錯誤 | 資料解析、轉換或驗證問題 |
| 併發/效能問題 | 記憶體、執行緒或效能相關 |
| 架構缺陷 | 元件間整合或資料流路徑設計不當 |
| i18n 遺漏 | 國際化翻譯未覆蓋或未正確套用 |

---

## #038 — HikariCP 連線池耗盡導致所有 API 逾時、無法登入

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | 資料庫連線池（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `application.yml`, `application-docker.yml`, `application-dev.yml`, `CdsHooksService.java` |
| **Commit** | [`448c674`](../../commit/448c674) |

### BUG 描述

後端運行一段時間後，所有 API 請求逾時返回 `Connection is not available, request timed out after 30000ms`。使用者無法登入（admin/admin），此問題反覆出現需重啟後端才能暫時恢復。

**根本原因（3 個配置問題）**：

1. **連線池大小不足**：HikariCP 未配置，使用預設值 `maximum-pool-size=10`，在排程任務 (`@Scheduled(fixedRate=60000)`) + API 請求 + FHIR 呼叫並行下不足
2. **OSIV 未關閉**：`spring.jpa.open-in-view` 預設為 `true`，HTTP 請求期間持有 DB 連線不釋放，長時間 FHIR 評估呼叫期間佔住連線
3. **無洩漏偵測**：無 `leak-detection-threshold`，洩漏連線無法被發現

### 修正方式

1. 新增 HikariCP 配置：`maximum-pool-size: 20`、`minimum-idle: 5`、`idle-timeout: 300000`、`max-lifetime: 600000`、`connection-timeout: 20000`、`leak-detection-threshold: 60000`
2. 關閉 OSIV：`spring.jpa.open-in-view: false`
3. CDS 發現方法加上 `@Transactional(readOnly=true)` 防止 OSIV 關閉後的 lazy-loading 問題

### 驗證

- 565 個後端測試全數通過
- Docker 重建後 backend 狀態 healthy，HikariCP 啟動日誌顯示 `CqlPlatformPool - Start completed`

---

## #037 — Autocomplete freeSolo 以顯示標籤覆蓋系統 URL 致術語查詢 503

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | 術語查詢（前端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `DrawerCodeLookupPanel.tsx`, `DrawerCodeSearchPanel.tsx` |
| **Commit** | [`448c674`](../../commit/448c674) |

### BUG 描述

術語抽屜中選擇 LOINC 等代碼系統後查詢代碼，返回 HTTP 503 錯誤。

**根本原因**：MUI Autocomplete `freeSolo` 模式下，當使用者從下拉選單選擇一個選項時，`onInputChange` 會被觸發 3 次：`'input'` → `'reset'`。`reason='reset'` 時將 `inputValue` 設為選項的顯示標籤（如 `"LOINC — http://loinc.org"`），覆蓋了先前 `onChange` 設定的純 URL 值 `"http://loinc.org"`。後端收到帶有 `—` 的非法 URL，FHIR 伺服器回傳 503。

### 修正方式

1. `onInputChange` 僅在 `reason === 'input' || reason === 'clear'` 時更新 state
2. `handleLookup` 加入 fallback：`ALL_CODE_SYSTEMS.find(cs => system.includes(cs.url))?.url || system`

### 驗證

- Docker 重建後術語查詢正常返回代碼結果
- 支援下拉選擇與手動輸入 URL 兩種方式

---

## #036 — MeasureLibrary 虛擬捲動表頭與內容欄位錯位擠壓

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | 指標庫表格（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `MeasureLibrary.tsx` |
| **Commit** | [`448c674`](../../commit/448c674) |

### BUG 描述

指標庫表格在較小螢幕上欄位完全擠壓在一起，表頭與資料列欄寬不一致。

**根本原因**：`react-window` `FixedSizeList` 將每一列渲染為獨立 `<Table>`，與表頭 `<Table>` 分離。兩個 `<Table>` 各自使用 `auto` 佈局計算欄寬，導致寬度不同步。

### 修正方式

1. 設定 `tableLayout: 'fixed'` 強制固定佈局
2. 欄位寬度改用百分比（`COL_W = { checkbox: '4%', name: '28%', ... }`）
3. 外層包裹 `Box sx={{ overflowX: 'auto' }}`，設定 `minWidth: 860`
4. 名稱欄加上 `overflow: hidden` 防止溢出
5. 頂部工具列加上 `flexWrap: 'wrap'` 響應式排版

### 驗證

- 小螢幕表頭與資料列欄位對齊
- 超長名稱自動截斷，水平捲動正常

---

## #035 — DashboardService 多處 NullPointerException 導致 Dashboard API 回傳 500

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-22 |
| **功能分類** | 品質指標儀表板（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/measure/DashboardService.java` |
| **Commit** | [`3f4c1c5`](../../commit/3f4c1c5) |

### BUG 描述

品質指標儀表板頁面所有 API 端點回傳 HTTP 500：`/dashboard/enhanced`、`/dashboard/trends`、`/dashboard/alerts`、`/dashboard/report`。

**根本原因（多處 null 未防護）**：

1. **Comparator NPE**：`getEnhancedDashboard()` 與 `getTrends()` 使用 `Comparator.comparing(MeasureReportEntity::getCreatedAt)` 排序。若任何 `MeasureReportEntity.createdAt` 為 null，Comparator 拋出 `NullPointerException`。
2. **isAfter() NPE**：`getLatestScoresMap()` 與 `getDepartmentDrilldown()` 中直接呼叫 `r.getCreatedAt().isAfter(existing.getCreatedAt())`，若 `createdAt` 為 null 則 NPE。
3. **字串串接 null**：`getTrends()` 中 `r.getPeriodStart() + " to " + r.getPeriodEnd()` 在 `periodStart`/`periodEnd` 為 null 時產生 `"null to null"` 字串。

### 修正方式

- **Comparator 排序前**：增加 `.filter(r -> r.getCreatedAt() != null)` 過濾空值記錄
- **isAfter() 比較前**：外層增加 `r.getCreatedAt() != null` 條件，內層增加 `existing.getCreatedAt() == null` fallback
- **period 字串串接**：使用三元運算子防護 null（`r.getPeriodStart() != null ? ... : "?"`）

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [ ] `/api/measures/dashboard/enhanced` 回傳 200
- [ ] `/api/measures/dashboard/trends` 回傳 200
- [ ] `/api/measures/dashboard/alerts` 回傳 200
- [ ] `/api/measures/dashboard/report` 回傳 200

---

## #034 — Recharts ResponsiveContainer 初始化時計算 width/height 為 -1

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-22 |
| **功能分類** | 品質指標儀表板（前端） |
| **嚴重程度** | Low |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `frontend/src/components/dashboard/ScoreTrendChart.tsx`、`DepartmentDrilldownChart.tsx`、`ScoreDistributionChart.tsx` |
| **Commit** | [`3f4c1c5`](../../commit/3f4c1c5) |

### BUG 描述

瀏覽器 console 持續出現 Recharts 警告：
```
The width(-1) and height(-1) of chart should be greater than 0
```

**根本原因**：`ResponsiveContainer` 使用 `ResizeObserver` 測量父元素尺寸。在元件初始掛載或父容器尚未完成 layout 時，測量結果可能為 -1。Recharts 官方建議設定 `minWidth={0}` 以防止負值。

### 修正方式

- 三個圖表元件的 `<ResponsiveContainer>` 均加上 `minWidth={0} minHeight={0}` 屬性

### 測試驗證

- [x] `npx tsc --noEmit` 編譯通過
- [ ] 瀏覽器 console 不再出現 width/height 警告

---

## #033 — 工具列 Undo/Redo 按鈕無效 — Redux 歷史與 Monaco 原生 undo 脫節

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-22 |
| **功能分類** | CQL 編輯器（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `frontend/src/components/editor/CqlEditor.tsx`、`frontend/src/pages/EditorPage.tsx` |
| **Commit** | [`dca6617`](../../commit/dca6617) |

### BUG 描述

CQL 編輯器工具列的 Undo（←）/ Redo（→）按鈕永遠處於 disabled 狀態，點擊無反應。

**根本原因（雙重）**：

1. **Redux 歷史堆疊與 Monaco 原生 undo 脫節**：工具列按鈕使用 Redux `past[]`/`future[]` 陣列控制 `disabled` 狀態並執行撤銷。但使用者在 Monaco 編輯器中打字時，`handleChange` 僅呼叫 `setCqlContent()`（不記錄歷史），`past[]` 永遠為空 → `canUndo = past.length > 0` 始終為 `false` → 按鈕永遠 disabled。

2. **`setValue()` 清除 Monaco undo 堆疊**：外部載入內容（如切換 Library）時，`useEffect` 呼叫 `editorRef.current.setValue(cqlContent)` 同步 Redux 狀態至編輯器，但 Monaco 的 `setValue()` 會清除內建的 undo 堆疊，導致 Ctrl+Z 也無法回復到載入前的內容。

### 修正方式

- **`CqlEditor.tsx`**：
  - 新增 `onEditorRef` prop，讓父元件取得 Monaco `IStandaloneCodeEditor` 實例
  - 將外部內容同步從 `setValue()` 改為 `executeEdits('external', [...])`，保留 Monaco 的 undo 歷史堆疊
- **`EditorPage.tsx`**：
  - 工具列 Undo/Redo 按鈕改為直接觸發 Monaco 原生 `undo`/`redo` 指令（`editor.trigger('toolbar', 'undo/redo', null)`）
  - 點擊後自動 `focus()` 回編輯器，確保後續鍵盤操作正常
  - 移除對 Redux `past`/`future` 的依賴，移除 `disabled` 限制

### 測試驗證

- [x] `npx tsc --noEmit` 編譯通過
- [ ] 在編輯器中打字後，點擊工具列 Undo 按鈕可撤銷
- [ ] Ctrl+Z / Ctrl+Y 鍵盤快捷鍵正常運作
- [ ] 透過 Builder 插入/刪除程式碼片段後，Undo 可回復
- [ ] 載入 Library 後，Undo 可回復到載入前的內容

---

## #032 — DataRequirements 未解析 ToConcept 包裝的 CodeRef 及 Case 包裝的日期屬性

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | eCQM 資料需求（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cql/DataRequirementExtractor.java` |
| **Commit** | [`b50d94a`](../../commit/b50d94a) |
| **關聯** | #024 修正的後續問題 |

### BUG 描述

DataRequirements 標籤頁中 Observation 資源顯示「無篩選條件（擷取所有 Observation 資源）」，未擷取 `O.code ~ "HbA1c LOINC"` 的代碼篩選及 `O.effective during "Measurement Period"` 的日期篩選。

**根本原因（雙重）**：

1. **CodeRef 包裝在 ToConcept 中**：`O.code ~ "HbA1c LOINC"` 中 `O.code` 為 `CodeableConcept`、`"HbA1c LOINC"` 為 `Code`，CQL-to-ELM 翻譯器將兩側都用 `ToConcept` 包裝以統一型別：
   ```json
   Equivalent(FunctionRef("ToConcept", Property("code")), ToConcept(CodeRef("HbA1c LOINC")))
   ```
   #024 新增的 `tryExtractCodeRefFilter()` 僅檢查 `codeRefNode.type == "CodeRef"`，但實際型別為 `"ToConcept"`，內嵌的 `CodeRef` 未被展開。

2. **日期屬性包裝在 Case 中**：`O.effective during "Measurement Period"` 中 `Observation.effective` 為 FHIR Choice Type（`effective[x]`），ELM 生成 `Case` 表達式進行型別分支：
   ```json
   In(Case(when Is(dateTime) then ToDateTime(As(Property("effective"))), ...), ParameterRef)
   ```
   `tryExtractDateFilter()` 僅處理直接 `Property` 和 `FunctionRef` 包裝，未處理 `Case` 表達式。

### 修正方式

- **`tryExtractCodeRefFilter()`**：新增 `unwrapToCodeRef()` 輔助方法，遞迴展開 `ToConcept`、`ToCode`、`FunctionRef`、`As`、`Convert` 節點以取得底層 `CodeRef`。在比對前先展開 codeRefNode 再檢查
- **`tryExtractDateFilter()`**：新增 `extractDatePropertyFromExpression()` 輔助方法，遞迴處理 `FunctionRef`、`As`、`Convert`、`Case` 節點。`Case` 處理邏輯遍歷 `caseItem[].then` 分支，從中提取 Property path

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 後端重建部署成功
- [x] Observation (HbA1c): `codeFilter: code → LOINC 4548-4`、`dateFilter: effective`
- [x] Observation (Glycated Albumin): `codeFilter: code → LOINC 13980-8`、`dateFilter: effective`
- [x] Encounter/Condition/MedicationRequest 資料需求未受影響

---

## #031 — 種子 CQL 語法錯誤導致 DataRequirements 標籤頁空白

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | eCQM 種子資料（後端） |
| **嚴重程度** | High |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/config/DataInitializer.java` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

Docker 環境中 Measures 頁面的「資料需求」（Data Requirements）標籤頁對種子指標 DiabetesHbA1cRate 顯示「未找到資料需求」。`GET /api/measures/{id}/data-requirements` 回傳空陣列 `[]`。

**根本原因**：`DataInitializer.seedDemoMeasure()` 中嵌入的 CQL 內容含有多處語法錯誤，CQL 翻譯失敗（`success: false`、`elmJson: null`），導致 `DataRequirementExtractor` 無法從 ELM AST 擷取資料需求。CQL 錯誤包含三類：

1. **`exists` 語法錯誤**：`C.code.coding exists (coding where ...)` — `exists` 應在查詢表達式前方，正確語法為 `exists(C.code.coding Coding where ...)`
2. **`starts with` 非 CQL 運算子**：`coding.code starts with 'E08'` — `starts` 是區間運算子、`with` 是查詢關鍵字，字串前綴比對應使用 `StartsWith()` 函數
3. **FHIR 原始類型需 `.value`**：`Coding.code` 為 FHIR `code` 類型，字串操作需明確取值 `Coding.code.value`
4. **Encounter.class 比較錯誤**：`E.class.code in { 'AMB', 'IMP' }` — `Encounter.class` 為 `Coding` 型別，應定義 `code` 常數並使用等價比較 `E.class ~ "AMB"`
5. **日期區間語法**：`E.period starts during` 應為 `E.period overlaps`

### 修正方式

- **`DataInitializer.java`**：完整重寫種子 CQL 內容，參照已驗證的 `DM_HbA1c_GA_Rate.cql` 語法模式：
  - `exists(C.code.coding Coding where ...)` — 正確的 `exists` 位置
  - `StartsWith(Coding.code.value, 'E08')` — 使用 `StartsWith()` 函數 + `.value` 取值
  - 新增 `codesystem "ActCode"` 及 `code "AMB"/"IMP"` 定義，使用 `E.class ~ "AMB"` 等價比較
  - `E.period overlaps "Measurement Period"` — 正確的期間重疊語法

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 後端重建，CQL 翻譯成功（0 errors, 0 warnings）
- [x] `GET /api/measures/1/data-requirements` 回傳 5 種資源類型（Encounter, Condition, MedicationRequest, Observation ×2）
- [x] DataRequirements 標籤頁正確顯示代碼篩選和日期篩選

---

## #030 — departmentApi/ehrApi/indicatorApi 使用原生 axios 無 JWT 攔截器致 401

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | API 客戶端（前端） |
| **嚴重程度** | High |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `frontend/src/api/departmentApi.ts`、`frontend/src/api/ehrApi.ts`、`frontend/src/api/indicatorApi.ts` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

登入後所有使用 Department、EHR、Indicator API 的頁面（如 Dashboard 部門選擇器、EHR 連線管理、指標目錄）回傳 401 Unauthorized。瀏覽器開發者工具顯示請求標頭中無 `Authorization: Bearer` JWT token。

**根本原因**：`departmentApi.ts`、`ehrApi.ts`、`indicatorApi.ts` 三個 API 模組使用 `import axios from 'axios'`（原生 axios 實例），而非 `import { api } from './client'`（已設定 JWT 攔截器的 axios 實例）。`client.ts` 中的 `api` 實例在 request interceptor 中自動附加 `Authorization: Bearer <token>` 標頭，原生 `axios` 不含此攔截器。

此外，三個模組的 URL 前綴為 `/api/departments`、`/api/ehr`、`/api/indicators`（硬編碼 `/api`），但 `client.ts` 的 `api` 實例已設定 `baseURL: '/api'`，改用 `api` 後需移除 `/api` 前綴以避免雙重前綴 `/api/api/...`。

### 修正方式

- **`departmentApi.ts`**：`import axios from 'axios'` → `import { api } from './client'`，URL 從 `/api/departments` → `/departments`，所有 `axios.get/post/put` → `api.get/post/put`
- **`ehrApi.ts`**：同上，`/api/ehr` → `/ehr`
- **`indicatorApi.ts`**：同上，`/api/indicators` → `/indicators`

### 測試驗證

- [x] `npx tsc --noEmit` TypeScript 編譯通過
- [x] Docker 前端重建部署成功
- [x] 登入後 Dashboard 部門選擇器正常載入
- [x] EHR 連線管理頁面正常載入
- [x] 指標目錄頁面正常載入

---

## #029 — SSE EventSource 無法傳送 Authorization 標頭致 401

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | 通知系統（前後端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/security/JwtAuthenticationFilter.java`、`frontend/src/hooks/useNotifications.ts` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

登入後瀏覽器 console 出現 `/api/notifications/subscribe` 401 Unauthorized 錯誤。SSE（Server-Sent Events）通知訂閱端點無法通過 JWT 認證。

**根本原因**：瀏覽器原生 `EventSource` API 不支援自訂 HTTP 標頭。`useNotifications` hook 使用 `new EventSource(url)` 建立 SSE 連線，無法附加 `Authorization: Bearer <token>` 標頭。後端 `JwtAuthenticationFilter` 僅從 `Authorization` 標頭讀取 JWT token，SSE 請求因此被拒絕。

### 修正方式

- **`JwtAuthenticationFilter.java`**：新增 query parameter token fallback — 當 `Authorization` 標頭不存在且 `request.getParameter("token")` 有值時，合成 `"Bearer " + token` 作為認證標頭
- **`useNotifications.ts`**：建立 EventSource URL 時附加 `?token=${encodeURIComponent(token)}`，將 JWT token 以 query parameter 傳遞

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 前後端重建部署成功
- [x] SSE 連線建立成功，瀏覽器 console 無 401 錯誤
- [x] 通知即時推送功能正常

---

## #028 — DataInitializer 僅在 dev profile 啟用，Docker 環境無種子資料

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | Docker 基礎設施（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/config/DataInitializer.java` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

Docker 環境啟動後資料庫為空，無法使用 `admin/admin` 登入。`DataInitializer` 建立預設管理員帳號和種子指標，但未在 Docker 環境中執行。

**根本原因**：`DataInitializer` 標註 `@Profile("dev")`，僅在 `dev` profile 啟用。Docker 環境使用 `SPRING_PROFILES_ACTIVE=docker` profile，`DataInitializer` 不會載入。

### 修正方式

- **`DataInitializer.java`**：`@Profile("dev")` → `@Profile({"dev", "docker"})`

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 啟動後 admin/admin 可正常登入
- [x] 種子指標 DiabetesHbA1cRate 正確建立

---

## #027 — PatientImportEntity @Lob 與 PostgreSQL schema validation 不相容

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | Docker 基礎設施（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/entity/PatientImportEntity.java` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

Docker 環境中後端啟動時 Hibernate schema validation 失敗。`PatientImportEntity.bundleJson` 欄位使用 `@Lob` 標註，Hibernate 在 PostgreSQL 中將其映射為 `oid`（大物件引用），但 Flyway migration 將 `bundle_json` 欄位定義為 `TEXT`。`oid` ≠ `TEXT` 導致 schema validation 不匹配。

**根本原因**：`@Lob` 在 H2 中映射為 `CLOB`（相容），但在 PostgreSQL 中映射為 `oid`。Flyway V28 migration 已將 `CLOB` 修正為 `TEXT`（PostgreSQL 相容），但 JPA entity 仍使用 `@Lob`。

### 修正方式

- **`PatientImportEntity.java`**：移除 `@Lob` 標註，改為 `@Column(name = "bundle_json", columnDefinition = "TEXT")`，明確指定 PostgreSQL 相容的欄位類型

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 後端啟動，Hibernate schema validation 通過
- [x] EHR 匯入功能正常存取 bundle_json 欄位

---

## #026 — Flyway V24-V29 使用 H2 語法導致 PostgreSQL 部署失敗

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | Docker 基礎設施（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `backend/src/main/resources/db/migration/V24__notifications.sql`、`V25__indicator_catalog.sql`、`V26__department_multi_tenancy.sql`、`V27__dashboard_enhancements.sql`、`V28__ehr_integration.sql`、`V29__okta_sso.sql` |
| **Commit** | [`63a5781`](../../commit/63a5781) |
| **關聯** | Docker 環境使用 PostgreSQL，開發環境使用 H2 |

### BUG 描述

Docker 環境啟動時後端 Flyway migration 失敗，PostgreSQL 拒絕 H2 專用的 SQL 語法。6 個 migration 檔案（V24-V29）在 P2 功能和 Okta SSO 開發期間使用 H2 語法撰寫，未考慮 Docker 環境的 PostgreSQL 相容性。

**錯誤 SQL 語法**：

| H2 語法 | PostgreSQL 語法 | 影響檔案 |
|---------|----------------|---------|
| `AUTO_INCREMENT` | `GENERATED ALWAYS AS IDENTITY` | V24, V25, V26, V27, V28 |
| `CLOB` | `TEXT` | V28 |
| `DOUBLE` | `DOUBLE PRECISION` | V27 |
| `ALTER COLUMN password VARCHAR(255) NULL` | `ALTER COLUMN password DROP NOT NULL` | V29 |

### 修正方式

- **V24**：`id BIGINT AUTO_INCREMENT PRIMARY KEY` → `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- **V25**：同上
- **V26**：同上
- **V27**：同上 + `DOUBLE` → `DOUBLE PRECISION`
- **V28**：同上 + `CLOB` → `TEXT`
- **V29**：`ALTER COLUMN password VARCHAR(255) NULL` → `ALTER COLUMN password DROP NOT NULL`

**附帶修正**（Docker 基礎設施）：
- **`docker/nginx.conf` + `frontend/nginx.conf`**：`/twcoredata/` location 新增 `resolver 127.0.0.11 valid=30s`，避免 nginx 啟動時因 taiwan-fhir-generator 容器不存在而失敗
- **`docker/docker-compose.yml`**：移除過時的 `version: '3.8'`，新增 Okta SSO 環境變數（`OKTA_ENABLED`、`OKTA_CLIENT_ID`、`OKTA_CLIENT_SECRET`、`OKTA_ISSUER`）
- **`docker/docker-compose.dev.yml`**：移除 `version: '3.8'`，frontend port 從 `5173:80` → `5173:8080`（配合 nginx 監聽 port 8080）

### 測試驗證

- [x] Docker Compose 全 8 服務啟動成功（postgres, backend, frontend, hapi-fhir, prometheus, grafana, alertmanager, taiwan-fhir-generator）
- [x] Flyway V24-V29 migration 全部通過
- [x] PostgreSQL schema 結構正確
- [x] nginx 啟動正常，taiwan-fhir-generator proxy 可用

---

## #025 — CQL 產生器未對 list 型別表達式自動加 exists() 導致驗證失敗

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/authoring/CqlArtifactBuilder.java` |
| **Commit** | [`0d418f1`](../../commit/0d418f1) |

### BUG 描述

CDS Authoring Tool 產生的 CQL 在驗證時出現錯誤：`Could not resolve call to operator Not with signature (list<FHIR.Condition>)`。

當排除條件（Exclusion）包含 Generic 資源元素（如 `GenericCondition_vsac`）並套用 list-returning 修飾器（如 `ActiveCondition`）時，產生的 CQL 如下：

```cql
define "MeetsExclusionCriteria":
  C3F.ActiveCondition([Condition: "Disorders of lipoprotein metabolism..."])

define "InPopulation":
  "MeetsInclusionCriteria" and not "MeetsExclusionCriteria"
```

`C3F.ActiveCondition()` 回傳 `list<FHIR.Condition>`，但 `InPopulation` 中的 `not` 運算子需要布林值。CQL Engine 無法對清單執行否定運算，導致驗證失敗。

**根本原因**：`CqlArtifactBuilder.buildExpression()` 在套用所有修飾器後，未檢查最終回傳型別。當修飾器鏈的最終型別仍為清單（如 `list_of_conditions`、`list_of_observations` 等），表達式被直接嵌入 `and`/`or` 邏輯運算，而這些運算子僅接受布林運算元。

修飾器如 `CheckExistence`（`exists()`）可將清單轉為布林值，但使用者若未手動加入此修飾器，產生的 CQL 就會出錯。同類問題影響所有 Generic 資源類型（Condition、Observation、Procedure、MedicationRequest 等）搭配 list-returning 修飾器（Active、Confirmed、Completed、MostRecent 等）的情境。

### 修正方式

- **`CqlArtifactBuilder.java`**：
  - `buildExpression()` 末尾新增自動型別檢測：套用所有修飾器後，呼叫 `getFinalReturnType()` 取得最終回傳型別。若型別以 `list_of_` 開頭，自動用 `exists()` 包裝表達式
  - 新增 `getFinalReturnType()` 輔助方法：優先取最後一個修飾器的 `returnType`，fallback 為元素本身的 `returnType`

### 修正結果

修正前產生的 CQL：
```cql
define "MeetsExclusionCriteria":
  C3F.ActiveCondition([Condition: "..."])    ← list<Condition>，驗證失敗
```

修正後產生的 CQL：
```cql
define "MeetsExclusionCriteria":
  exists(C3F.ActiveCondition([Condition: "..."]))    ← boolean，驗證通過
```

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [ ] Authoring 建立含 Condition + Active 修飾器的排除條件 → 產生 CQL 包含 `exists()`
- [ ] CQL 驗證通過，無 `operator Not with signature (list<...>)` 錯誤
- [ ] 已有 `CheckExistence` 修飾器的元素不會重複包裝 `exists(exists(...))`
- [ ] 回傳布林值的修飾器（如 `ValueComparisonNumber`）不受影響

---

## #024 — DataRequirements 未解析 Equal/Equivalent + CodeRef 模式

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | eCQM 資料需求（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cql/DataRequirementExtractor.java` |
| **Commit** | [`53b19ca`](../../commit/53b19ca) |

### 問題描述

CQL 中 `E.class ~ "AMB"` 語法在 ELM 中生成 `Equivalent(Property, CodeRef)` 模式。DataRequirementExtractor 的 `walkWhereClause` 方法僅處理 `Exists`（嵌套子查詢比較）和 `InValueSet` 模式，未處理直接的 `Equal`/`Equivalent` + `CodeRef` 比較。導致 Encounter 資料需求只顯示 `dateFilter: period`，未顯示 `codeFilter: class → ActCode/AMB`。

### 根因分析

ELM AST 中 `E.class ~ "AMB"` 轉譯為：
```json
{
  "type": "Equivalent",
  "operand": [
    { "type": "FunctionRef", "name": "ToCode", "operand": [{ "type": "Property", "path": "class", "source": { "type": "AliasRef", "name": "E" } }] },
    { "type": "CodeRef", "name": "AMB" }
  ]
}
```

`CodeRef` 引用 `library.codes.def[]` 中定義的命名代碼（如 `code "AMB": 'AMB' from "ActCode"`），需要：
1. 從 ELM 的 `library.codes.def[]` 建立代碼定義映射（code name → code system + value）
2. 在 `walkWhereClause` 中識別 `Equal`/`Equivalent` 節點，解開 `FunctionRef` 包裝取得 `Property`，並查詢 `CodeRef` 對應的代碼系統
3. 將解析結果合併到 `RetrieveInfo` 的 `codeProperty`、`codeSystemUrl`、`directCodes`

### 修正方式

1. **新增 `buildCodeDefMap()`**：遍歷 `library.codes.def[]`，建立 `Map<String, CodeDefInfo>`（名稱→代碼系統+值+顯示文字）
2. **新增 `CodeDefInfo` 內部類別**：存儲代碼定義的中間表示
3. **傳遞 `codeDefMap` 貫穿調用鏈**：`collectRetrieves` → `handleQuery` → `enhanceFromWhere` → `walkWhereClause`
4. **新增 `handleCodeRefComparison()`**：在 `walkWhereClause` 的 `Equal`/`Equivalent` 分支中，偵測 `Property`↔`CodeRef` 配對
5. **新增 `tryExtractCodeRefFilter()`**：解開 FunctionRef 包裝取得 Property path，查詢 CodeRef → CodeDefInfo → 解析代碼系統 URL

### 修正結果

修正前 Encounter 資料需求：
```json
{ "type": "Encounter", "codeFilter": null, "dateFilter": [{"path": "period"}] }
```

修正後：
```json
{
  "type": "Encounter",
  "codeFilter": [{ "path": "class", "codeSystemUrl": "http://terminology.hl7.org/CodeSystem/v3-ActCode", "codeSystemName": "ActCode", "code": [{ "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode", "code": "AMB", "display": "ambulatory" }] }],
  "dateFilter": [{"path": "period"}]
}
```

### 測試驗證

- [x] `mvn compile -q` 通過
- [x] Measure 1 (DM_HbA1c_GA_Rate) DataRequirements 正確顯示 Encounter class → ActCode/AMB
- [x] Measure 2 (DM_FastingLipid_Rate) DataRequirements 同樣正確
- [x] 其他資源類型（Condition、MedicationRequest、Procedure）資料需求未受影響

---

## #023 — Encounter.class 下拉選單顯示 1115 個代碼而非 11 個

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | Test Case Builder（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/fhir/FhirStructureDefinitionService.java` |
| **Commit** | [`3fc6de0`](../../commit/3fc6de0) |

### BUG 描述

Visual Builder 中新增 Encounter 資源時，`class` 欄位的下拉選單顯示 1115 個代碼（`_ActControlVariable`、`PDNPSPELAT`、`INFREG` 等無關代碼），使用者無法有效選擇正確的門診/住院/急診類別。

**根本原因**：`Encounter.class` 的 binding ValueSet 為 `v3-ActEncounterCode`，其定義使用 `is-a` 層級篩選器：

```xml
<compose>
  <include>
    <system value="http://terminology.hl7.org/CodeSystem/v3-ActCode"/>
    <filter>
      <property value="concept"/>
      <op value="is-a"/>
      <value value="_ActEncounterCode"/>
    </filter>
  </include>
</compose>
```

HAPI 的 `InMemoryTerminologyServerValidationSupport` 無法處理 CodeSystem 層級篩選器（`is-a`），在 `expandValueSet()` 時回傳整個 `v3-ActCode` CodeSystem（1115 個代碼），而非 `_ActEncounterCode` 的 11 個子代碼（AMB、EMER、IMP 等）。`expandValueSetCodes()` 直接信任 expansion 結果，未驗證結果合理性。

### 修正方式

- **`expandValueSetCodes()`**：新增 `MAX_BOUND_CODES = 80` 閾值。當 expansion 結果超過閾值且 compose 使用 filter 時，判定 expansion 失敗，改用手動層級解析
- **`resolveFilteredCodes()`**：遍歷 compose 中的 `is-a` 篩選器，為每個篩選器呼叫 `findDescendantCodes()` 並排除 `compose.exclude` 中的代碼
- **`findDescendantCodes()`**：透過 `validationSupport.fetchCodeSystem()` 取得 CodeSystem 定義，呼叫 `findConceptInHierarchy()` 遞迴定位祖先節點，再由 `collectDescendantCodes()` 收集所有子代代碼

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 後端重建部署成功
- [x] Encounter.class boundCodes: 11 個代碼（AMB, EMER, FLD, HH, IMP, ACUTE, NONAC, OBSENC, PRENC, SS, VR）
- [x] Encounter.status boundCodes: 9 個代碼（未受影響）
- [x] Condition/Observation/MedicationRequest 各欄位 boundCodes 數量正常（無迴歸）

---

## #022 — 測試案例結果表格族群名稱未中文化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | Test Cases（前端） |
| **嚴重程度** | Low |
| **根因類型** | i18n 遺漏 |
| **影響範圍** | `TestCaseResult.tsx`、`TestCasesTab.tsx`、`en/measures.json`、`zh-TW/measures.json` |
| **Commit** | [`0260852`](../../commit/0260852) |

### BUG 描述

測試案例執行結果表格中的族群名稱（`initial-population`、`denominator`、`numerator` 等）直接顯示英文原始 key，未經 i18n 翻譯。切換至中文介面時仍顯示英文。同樣地，測試案例列表的縮寫標籤（Ini、Den、Num）也是透過截取英文前三字元產生，在中文介面下無意義。

**根本原因**：

1. **`TestCaseResult.tsx`**（第 85 行）：直接輸出 `comp.populationType` 原始字串，未使用 `t()` 翻譯函數。同元件的 `TestCaseEditor.tsx` 已正確使用 `t('testCaseEditor.populationTypes.${key}')`。
2. **`TestCasesTab.tsx`**（第 253 行）：縮寫標籤使用 `key.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase()).substring(0, 3)` 硬編碼截取英文前三字元，無 i18n。

### 修正方式

- **`TestCaseResult.tsx`**：`comp.populationType` → `t('testCaseEditor.populationTypes.${comp.populationType}', comp.populationType)`
- **`TestCasesTab.tsx`**：截取英文前三字元 → `t('testCaseEditor.populationTypesShort.${key}', key.substring(0, 3))`
- **`en/measures.json`** 及 **`zh-TW/measures.json`**：新增 `populationTypesShort` i18n keys（EN: Ini/Den/Num/DEx/DXp/NEx；zh-TW: 初始/分母/分子/母除/母外/子除）

### 測試驗證

- [x] `npx tsc --noEmit` TypeScript 編譯通過
- [x] Docker 前端重建部署成功
- [ ] 中文介面：結果表格顯示「初始族群」「分母」「分子」
- [ ] 中文介面：縮寫標籤顯示「初始」「分母」「分子」
- [ ] 英文介面：結果表格顯示「Initial Population」「Denominator」「Numerator」

---

## #021 — DataRequirements 未解析 FHIRHelpers.ToString 包裝的屬性比較

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | eCQM 資料需求（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `DataRequirementExtractor.java` |
| **Commit** | [`66a9ee2`](../../commit/66a9ee2) |
| **關聯** | #020 修正的後續問題 |

### BUG 描述

#020 新增了 Where 子句分析功能，但在實際 CQL（如 DM_HbA1c_GA_Rate 指標）上測試時，Condition 和 MedicationRequest 的 code system 篩選仍顯示「無篩選條件」。

**根本原因**：CQL-to-ELM 翻譯器在處理 FHIR 原生類型（`uri`, `code`, `string` 等）時，會自動插入 `FHIRHelpers.ToString()` 函數呼叫進行型別轉換。例如：

```
CQL:   Coding.system = 'http://hl7.org/fhir/sid/icd-10-cm'
ELM:   Equal(FunctionRef("FHIRHelpers.ToString", Property("system")), Literal("http://..."))
```

`extractSystemComparison()` 預期左運算元直接為 `Property` 節點，但實際上是 `FunctionRef` 包裝了 `Property`，導致比對失敗。

同時，`extractCodePathFromPropertyChain()` 在處理 scope-based 屬性鏈（如 `M.medication.coding`）時，只檢查最外層 Property 的 `scope` 屬性，但 scope 實際位於最內層 Property（例如 `Property(path="medication", scope="M")`），導致 MedicationRequest 的 `medication` code path 無法擷取。

### 修正方式

- **`extractSystemComparison()`**：新增 `unwrapToProperty()` 輔助方法，遞迴展開 `FunctionRef`、`As`、`Convert` 節點以取得底層的 `Property` 節點。在比較前先展開再檢查 `path`。
- **`extractCodePathFromPropertyChain()`**：追蹤迴圈中最後一個 `Property` 節點（`lastProperty`），在 scope 檢查時同時檢查最外層與最內層 Property 的 scope 屬性。

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 重建 → 呼叫 `/api/measures/1/data-requirements` 確認：
  - Condition: `code` → CodeSystem `http://hl7.org/fhir/sid/icd-10-cm` (ICD10CM)
  - MedicationRequest: `medication` → CodeSystem `http://www.whocc.no/atc` (ATC)
  - Encounter: date filter `period`
  - Procedure: date filter `performed`

---

## #020 — DataRequirements 標籤頁未顯示 Where 子句中的篩選條件

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | eCQM 資料需求（前後端） |
| **嚴重程度** | Medium |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `DataRequirementExtractor.java`、`DataRequirementInfo.java`、`DataRequirementExtractorTest.java`、`DataRequirementsTab.tsx`、`types/index.ts`、`measures.json` (en/zh-TW) |
| **Commit** | [`8efa589`](../../commit/8efa589) |

### BUG 描述

Measures 頁面的「Data Requirements」標籤頁對所有資源類型皆顯示「無篩選條件」（No filters），包括明確帶有代碼篩選和日期篩選的 CQL 查詢（如 DM_HbA1c_GA_Rate 指標）。

**根本原因**：`DataRequirementExtractor` 僅處理 ELM Retrieve 節點中的 inline code filter（`[Condition: "Diabetes"]` 語法產生的 `Retrieve.codes = ValueSetRef`）。然而實際 CQL 普遍使用 bare Retrieve + Where clause 模式（`[Condition] C where exists(C.code.coding Y where Y.system = "url" ...)`），此模式產生的 ELM Retrieve 節點**不含** `codes` 欄位，代碼/日期篩選資訊全部位於外層 Query 的 `where` 子句中。Extractor 遞迴遍歷時僅檢查 Retrieve 節點本身，完全忽略 Query Where 子句，導致所有 bare Retrieve 被視為「無篩選條件」。

### 修正方式

- **`DataRequirementInfo.java`**：`CodeFilterInfo` 新增 `codeSystemUrl`（代碼系統 URL）和 `codeSystemName`（代碼系統名稱）欄位
- **`DataRequirementExtractor.java`**：
  - 新增 `buildCodeSystemMap(root)` 擷取 `library.codeSystems.def[]` 的 name→URL 對應
  - `collectRetrieves()` 改為識別 Query 節點：從 `source[0].expression` 擷取 Retrieve、取得 alias，避免重複計算
  - 新增 `handleQuery()` 處理 Query 節點：解析 source 中的 Retrieve 並呼叫 Where 分析
  - 新增 `enhanceFromWhere()` / `walkWhereClause()` 遞迴 AST walker，處理以下 ELM 模式：
    - `And`/`Or`/`Not` 邏輯連接詞 → 遞迴
    - `Exists` → 內部 Query（`C.code.coding Y where Y.system = "url"`）→ 擷取 CodeSystem URL 及 code path
    - `Overlaps`/`During`/`IncludedIn` 等日期比較 + `ParameterRef`（Measurement Period）→ 擷取 date filter path
    - `FunctionRef` 包裝（如 `NormalizeInterval(P.performed)`）→ 展開後擷取 property path
    - `InValueSet` → 擷取 ValueSet 參照
  - Dedup key 納入 `codeSystemUrl`，避免不同 CodeSystem 的同類型 Retrieve 被錯誤合併
  - 使用 `handledRetrieves` Set 追蹤已處理的 Retrieve 節點，防止 Query source 中的 Retrieve 被遞迴重複擷取
- **`DataRequirementExtractorTest.java`**：新增 7 個測試案例（exists+CodeSystem、Overlaps date filter、FunctionRef 包裝、combined filters、mixed inline/Where、dedup、And wrapper）
- **`types/index.ts`**：前端 `CodeFilterInfo` 新增 `codeSystemUrl?` 和 `codeSystemName?`
- **`DataRequirementsTab.tsx`**：當 code filter 有 `codeSystemUrl`（無 valueSet）時顯示 CodeSystem chip + 名稱 + URL；summary chip 計數納入 codeSystem filters
- **i18n**：EN `"codeSystem": "Code System"`、zh-TW `"codeSystem": "代碼系統"`

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] `mvn test -Dtest=DataRequirementExtractorTest` 全部 17 個測試通過
- [x] `npx tsc --noEmit` TypeScript 編譯通過
- [x] Docker 重建 → DataRequirements 標籤頁顯示 Condition(code: ICD-10-CM)、MedicationRequest(medication: ATC)、Encounter(period: date filter) 等篩選條件（於 #021 修正後驗證通過）

---

## #019 — CDS Prefetch 執行清除 patientId 導致 Patient context 失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cds/CdsInvocationService.java` |
| **Commit** | [`c08372c`](../../commit/c08372c) |

### BUG 描述

CDS Sandbox 中使用 prefetch 資料執行含有 Patient context 表達式的 CQL 規則時，所有 Patient-context 表達式（如 `[Condition: "Diabetes"]`）回傳空集合，導致 `Has Diabetes` 永遠為 false，CDS Card 顯示 "No recommendations at this time."。

**根本原因**：#008 的修正中，為繞過 CQL engine 的 post-retrieval context filtering（`Reference.equals(String)` 類型不匹配），在使用 prefetch provider 時設定 `execRequest.setPatientId(null)`。然而 CQL engine 3.29.0 的 `RetrieveEvaluator` 實際上**不會**對 retrieve 結果做 post-filtering——它直接將 contextPath/contextValue 傳遞給 `DataProvider.retrieve()`，由 RetrieveProvider 決定如何處理。設定 patientId 為 null 反而導致 CQL engine 無法建立 Patient context，使所有 Patient-context 表達式（define 語句中隱含的 `context Patient`）無法正確評估，retrieve 呼叫缺少 contextValue 而回傳空結果。

**與 #008 的關係**：#008 的修正 2（`setPatientId(null)`）基於錯誤假設——認為 CQL engine 會對 retrieve 結果做 `Reference.equals(String)` post-filtering。經 CQL engine v3.29.0 bytecode 分析確認 engine 不做此過濾，#008 修正 1（`ensureSubjectReference`）才是正確修正，修正 2 為多餘且有害的 workaround。

### 修正方式

- **`CdsInvocationService.java`**：移除 `execRequest.setPatientId(null)`，保留原始 patientId 以便 CQL engine 建立正確的 Patient context。新增註解說明保留 patientId 的原因

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 重建並部署成功
- [x] CDS Sandbox DM 服務 → `Has Diabetes = true`、`Needs HbA1c Test = true`（CDS Card 正確顯示）
- [x] PrefetchRetrieveProvider 日誌確認 `contextValue=Patient/test-patient-1`（非 null）
- [x] Debug CQL 表達式驗證：`Diabetes Count = 1`、`All Conditions = [Condition]`

---

## ~~#018~~ — FHIR Coding→Code 轉換 **已撤回**

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CQL Engine（後端） |
| **嚴重程度** | — (已撤回) |
| **根因類型** | 誤判 |
| **影響範圍** | `ComparableR4FhirModelResolver.java` |
| **Commit** | [`878deef`](../../commit/878deef) → reverted |

### 撤回原因

原本認為 CQL Engine 的 `Contains` 評估器不會對 FHIR Coding 套用隱式轉換。實際測試後發現 CQL Translator 已在 ELM 中內嵌 `FHIRHelpers.ToCode(FHIR.Coding)` 呼叫。在 Model Resolver 中提前將 Coding→Code 反而導致執行時簽名不匹配：`Could not resolve call to operator 'ToCode(org.opencds.cqf.cql.engine.runtime.Code)' in library 'FHIRHelpers'`。

原始 `Has Diabetes = false` 的真正根因為 #016（CodeSystem URL 錯誤）和 #017（ValueSet 未展開），非型別轉換問題。

---

## #017 — PrefetchRetrieveProvider 未展開 ValueSet 導致代碼過濾失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `PrefetchRetrieveProvider.java`、`CqlExecutionService.java` |
| **Commit** | [`878deef`](../../commit/878deef) |

### BUG 描述

CDS Sandbox 中 ValueSet 基礎的 CQL 檢索（如 `[Condition: "Diabetes"]`，其中 "Diabetes" 為 VSAC ValueSet）未進行代碼過濾。CQL Engine 將 ValueSet URL 傳遞給 `RetrieveProvider.retrieve()` 的 `valueSet` 參數（`codes` 為 null），但 `PrefetchRetrieveProvider` 完全忽略 `valueSet` 參數，回傳所有該類型的資源而不做代碼篩選。

**根本原因**：`PrefetchRetrieveProvider` 是為 CDS Hooks prefetch 設計的簡易記憶體 RetrieveProvider，僅處理 `codes` 參數（已展開的代碼清單），未處理 `valueSet` 參數（ValueSet URL）。CQL Engine 3.29.0 期望 DataProvider 自行處理 ValueSet 展開，而非由 Engine 預先展開後傳遞 codes。

### 修正方式

- **`PrefetchRetrieveProvider.java`**：
  - 新增 `TerminologyProvider terminologyProvider` 欄位及 `setTerminologyProvider()` 方法
  - `retrieve()` 方法：當 `codes=null` 且 `valueSet` 非空時，呼叫 `terminologyProvider.expand(new ValueSetInfo().withId(valueSet))` 展開 ValueSet 取得代碼清單
  - 日誌新增 `valueSet` 參數輸出
- **`CqlExecutionService.java`**：在 `doExecute()` 中，當使用 PrefetchRetrieveProvider 時，自動注入已建立的 TerminologyProvider

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] CDS Sandbox `[Condition: "Diabetes"]` retrieve → 正確展開 VSAC ValueSet 並過濾 Condition
- [x] 無 ValueSet 的 retrieve → 行為不變
- [x] TerminologyProvider 不可用時 → graceful fallback（回傳所有資源）

---

## #016 — CodeableConcept dropdown 使用 ValueSet URL 而非 CodeSystem URL

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | Test Case Builder（前後端） |
| **嚴重程度** | High |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `ElementMetadata.java`、`FhirStructureDefinitionService.java`、`CodeableConceptField.tsx`、`ChoiceTypeField.tsx`、`types/index.ts` |
| **Commit** | [`6ca7a86`](../../commit/6ca7a86) |

### BUG 描述

#014 新增的 CodeableConcept dropdown 在使用者選擇代碼後，產生的 coding `system` 為 **ValueSet URL**（如 `http://hl7.org/fhir/ValueSet/condition-clinical`），而非正確的 **CodeSystem URL**（`http://terminology.hl7.org/CodeSystem/condition-clinical`）。

CQL 中 `C.clinicalStatus.coding contains "Active"`（其中 `"Active"` 的 system 為 CodeSystem URL）會比對 code + system，因 system 不匹配導致永遠回傳 false。即使使用者在 dropdown 選擇了 "active"，CQL 仍判定 `Has Diabetes` = false → CDS Card 顯示 "No recommendations at this time"。

**根本原因**：`ElementMetadata` 僅提供 `bindingValueSetUrl`（ValueSet URL），未提供 CodeSystem URL。`CodeableConceptField` 直接以 `bindingValueSetUrl` 作為 coding system，但 ValueSet URL ≠ CodeSystem URL。

### 修正方式

- **`ElementMetadata.java`**：新增 `bindingCodeSystemUrl` 欄位
- **`FhirStructureDefinitionService.java`**：`expandValueSetCodes()` 重構為回傳 `ExpandedCodes` record（含 codes + codeSystemUrl）。從 ValueSet 的 `compose.include[].system` 和 expansion `contains[].system` 提取 CodeSystem URL
- **`types/index.ts`**：前端 `ElementMetadata` 新增 `bindingCodeSystemUrl` 欄位
- **`CodeableConceptField.tsx`**：dropdown 產生 coding 時優先使用 `bindingCodeSystemUrl`，fallback 為 `bindingValueSetUrl`
- **`ChoiceTypeField.tsx`**：合成 ElementMetadata 物件新增 `bindingCodeSystemUrl: null`

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] `tsc --noEmit` 編譯通過
- [x] Docker 重建並部署成功
- [x] clinicalStatus 選 "active" → JSON coding system 為 `http://terminology.hl7.org/CodeSystem/condition-clinical`
- [x] CDS Sandbox DM CQL → `Has Diabetes` 正確判定為 true

---

## #015 — VSAC ValueSet 未連接 CQL Engine 導致 CDS 規則失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CQL Engine（後端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `LocalTerminologyProvider.java`、`FhirTerminologyService.java`、`VsacService.java`、`SettingsController.java`、`SecurityConfig.java` |
| **Commit** | [`69dd9a1`](../../commit/69dd9a1) |

### BUG 描述

CDS Sandbox 測試含有 VSAC ValueSet 參照（如 `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001`）的 CQL 規則時，CQL Engine 無法解析 ValueSet，導致 `in` 運算子永遠回傳 false，CDS Card 顯示「No recommendations at this time」。

**根本原因**：`VsacService` 已存在且 API Key 已設定，但僅被 `FhirController` 的 REST API 使用。CQL Engine 的執行路徑為 `CqlExecutionService` → `FhirTerminologyService.createTerminologyProvider()` → `LocalTerminologyProvider(igService, R4FhirTerminologyProvider(tx.fhir.org))`，完全未接入 `VsacService`。當 ValueSet URL 為 `cts.nlm.nih.gov` 時，`tx.fhir.org` 無法解析，導致 fallback 失敗。

### 修正方式

- **`LocalTerminologyProvider.java`**：新增 `VsacService` 為可選依賴。`in()` 和 `expand()` 方法新增 VSAC 層：當 ValueSet URL 包含 `cts.nlm.nih.gov` 時，提取 OID 並委派 `VsacService.expandValueSetByOid()` 解析
- **`FhirTerminologyService.java`**：注入 `VsacService`（`@Autowired(required = false)`），傳遞至 `LocalTerminologyProvider`。Terminology 解析鏈改為：Local IG → VSAC → Remote (tx.fhir.org)
- **`VsacService.java`**：新增 `isConfigured()`、`getApiUrl()`、`updateApiKey()` 方法，支援執行時更新 API Key
- **`SettingsController.java`**（新增）：`GET /api/settings/vsac-status` 查詢 VSAC 狀態；`PUT /api/settings/vsac-api-key` 更新 API Key（ADMIN 限定）
- **`SecurityConfig.java`**：`PUT /api/settings/**` 限制為 ADMIN 角色
- **前端 `PreferencesDialog.tsx`**：新增「術語服務」區段，顯示 VSAC 連線狀態（Chip 指示已設定/未設定）、伺服器 URL、API Key 輸入與更新按鈕
- **i18n**：en/zh-TW 各新增 14 個 `preferences.vsac*` 鍵值

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] `tsc --noEmit` 編譯通過
- [x] CDS Sandbox 含 VSAC ValueSet 的 CQL 規則 → CQL Engine 正確解析 ValueSet
- [x] PreferencesDialog 顯示 VSAC 狀態和 API Key 設定
- [x] 更新 API Key 後 VSAC 狀態切換為「已設定」

---

## #014 — CodeableConcept boundCodes 未使用下拉選單

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | Test Case Builder（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/testcase-builder/CodeableConceptField.tsx` |
| **Commit** | [`69dd9a1`](../../commit/69dd9a1) |

### BUG 描述

Visual Builder 中 `clinicalStatus`（type `CodeableConcept`）等帶有 `boundCodes` 的欄位，仍顯示為完整的 coding 複合編輯器（system + code + display 三欄 + TWCORE 瀏覽按鈕 + 新增 coding 按鈕 + text 欄位）。一般使用者不知道需要填入完整的 coding 物件才能使 CQL 正確判斷。

**根本原因**：`CodeField.tsx`（primitive `code` type）已有 `boundCodes` 下拉邏輯（#003），但 `CodeableConceptField.tsx`（complex `CodeableConcept` type）從未實作此功能。後端 `FhirStructureDefinitionService` 已正確回傳 `boundCodes: ["active", "recurrence", "relapse", "inactive", "remission", "resolved"]`，但前端忽略此資訊。

### 修正方式

- **`CodeableConceptField.tsx`**：在元件頂部新增 `hasBoundCodes` 判斷分支。當 `element.boundCodes.length > 0` 時，渲染 MUI `Select` 下拉選單取代複合編輯器。選擇後自動生成完整的 `CodeableConcept` 結構（含 `coding[0].system`、`code`、`display`）
- 新增 `FormControl`、`InputLabel`、`Select`、`MenuItem` imports

### 測試驗證

- [x] `tsc --noEmit` 編譯通過
- [x] Condition clinicalStatus 顯示為下拉選單（active/inactive/resolved 等）
- [x] 選擇後 JSON 正確生成 `{ coding: [{ system: "...", code: "active", display: "Active" }] }`
- [x] 無 boundCodes 的 CodeableConcept 欄位仍顯示完整 coding 編輯器

---

## #013 — TWCORE 選碼導致 Monaco Editor 白屏

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CQL Builder（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `frontend/src/components/builder/CqlPreviewBox.tsx` |
| **Commit** | [`4c9ae86`](../../commit/4c9ae86) |

### BUG 描述

在 CQL Builder 的「代碼」區段使用「瀏覽 TWCORE」功能選擇代碼（如糖尿病 SNOMED CT 代碼）後，左側 Monaco Editor 突然從深色主題（`cql-theme-dark`）變為白色背景。問題僅在首次觸發 TWCORE 選碼時發生，之後主題即永久被覆寫為 light。

**根本原因**：`CqlPreviewBox` 使用 `import * as monaco from 'monaco-editor'`（Vite 打包的本地實例），而主編輯器的 `CqlEditor` 使用 `@monaco-editor/react`（透過 `@monaco-editor/loader` 從 CDN 載入 Monaco）。兩者為**不同的 Monaco 實例**。當 `CqlPreviewBox` 首次渲染時，本地打包的 `monaco-editor` 模組初始化，將全域 Monaco 主題重設為預設的 `'vs'`（白色），覆寫了 CDN 實例已設定的 `'cql-theme-dark'`。

### 修正方式

- **移除直接 import**：`import * as monaco from 'monaco-editor'` → `import { useMonaco } from '@monaco-editor/react'`
- **使用 `useMonaco()` hook**：取得與 `CqlEditor` 相同的 CDN Monaco 實例，確保 CQL 語言和主題均已註冊
- **呼叫 `colorize()` 前設定主題**：根據 `preferences.themeMode` 明確設定 `cql-theme-dark` 或 `cql-theme`，確保語法著色使用正確的色彩
- **深色模式適配**：preview box 背景色和 `mtk1` 文字色根據主題模式切換

### 測試驗證

- [x] TypeScript 編譯通過
- [x] 深色模式下選擇 TWCORE 代碼 → Monaco Editor 維持深色背景
- [x] 淺色模式下選擇 TWCORE 代碼 → Monaco Editor 維持淺色背景
- [x] 代碼預覽框（SnippetPreview）語法著色正確

---

## #012 — Monaco Editor 夜間模式白屏

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | 跨模組（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `SandboxPanel.tsx`、`ElementField.tsx`、`ResourceEditorDialog.tsx`、`TransactionTab.tsx`、`ValidateTab.tsx`、`TestCaseEditor.tsx` |
| **Commit** | [`e375b1e`](../../commit/e375b1e) |

### BUG 描述

切換至夜間模式（Dark Mode）後，除 CQL Editor 以外的所有 Monaco Editor 實例仍顯示白色背景（light theme），與深色介面形成強烈對比，影響視覺一致性及可讀性。CQL Editor 正確使用自訂的 `cql-theme-dark`，但其餘 6 個 JSON editor 未設定 `theme` prop，Monaco 預設使用 light theme。

**受影響位置**：
1. CDS Sandbox — JSON (Prefetch) 編輯器
2. Test Case Builder — 深層 JSON fallback 編輯器
3. FHIR Browser — Resource Editor Dialog
4. FHIR Browser — Transaction Tab
5. FHIR Browser — Validate Tab
6. Measures — Test Case Editor（JSON Advanced 分頁）

### 修正方式

- 6 個檔案各加入 `useTheme` hook，並在 `<Editor>` 元件上設定 `theme={theme.palette.mode === 'dark' ? 'vs-dark' : 'light'}`
- JSON editor 使用 Monaco 內建的 `vs-dark` / `light` theme（不需自訂 theme）

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Frontend Docker 重建並部署成功
- [x] 夜間模式下所有 Monaco Editor 顯示深色背景
- [x] 日間模式下維持淺色背景不變

---

## #011 — Footer 位置異常：flexbox 佈局修正

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | 版面配置（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/App.tsx`、`frontend/src/constants/layout.ts` |
| **Commit** | [`d82710d`](../../commit/d82710d) |

### BUG 描述

Footer（CQL 規範、CDS Hooks、FHIR 連結）卡在畫面中間，無法固定在視窗底部。此問題在 #005 和 #007 中已嘗試修正，但 `PAGE_CONTENT_HEIGHT = calc(100vh - 48px)` 僅扣除 Header 高度（且 MUI Toolbar 預設高度為 64px 非 48px），未扣除 Footer 高度，導致 Footer 被推至 viewport 外或浮在內容中間。

### 修正方式

- **`App.tsx`**：外層 Box 改用 `display: flex`, `flexDirection: column`, `height: 100vh`，Main 區域設為 `flex: 1, overflow: auto`，Footer 自然固定在 viewport 底部
- **`layout.ts`**：`PAGE_CONTENT_HEIGHT` 從 `calc(100vh - 48px)` 改為 `100%`（相對於 flex 分配的 main 空間）

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Frontend Docker 重建並部署成功
- [x] Footer 固定在 viewport 底部，不隨內容滾動
- [x] 各頁面內容可正常捲動

---

## #010 — CDS Card 顯示所有表達式擠在一行

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CDS Hooks Sandbox（前後端） |
| **嚴重程度** | Low |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `backend/.../CqlTupleCardStrategy.java`、`frontend/.../SandboxPanel.tsx` |
| **Commit** | [`5e69d32`](../../commit/5e69d32) |

### BUG 描述

CDS Sandbox 的結果卡片將所有 CQL 表達式（LatestBMI、BMICalue、BMIClassification、CardSuggestion）全部串接在同一行顯示，包含冗長的 FHIR Resource 物件序列化文字。問題有二：

1. **後端**：`CqlTupleCardStrategy` 將所有非 null 表達式（包含 FHIR Resource 物件）統一格式化為 `**key**: value` 並換行串接，未區分主要訊息與中間計算值
2. **前端**：`SandboxPanel.tsx` 以純文字 `<Typography>` 渲染 card detail，不支援換行 (`\n`) 和 Markdown 粗體 (`**text**`)

### 修正方式

- **`CqlTupleCardStrategy.java`**：新增「主要訊息偵測」邏輯 — 表達式名稱含 Suggestion / Summary / Message / Recommendation 且為 String 時，作為 card 主要 detail；FHIR Resource 物件從合併卡片中排除（過於冗長）；其餘標量值作為補充資訊
- **`SandboxPanel.tsx`**：card detail 加入 `whiteSpace: 'pre-line'` 支援換行，並以 `dangerouslySetInnerHTML` + regex 將 `**text**` 轉為 `<strong>text</strong>`

### 測試驗證

- [x] TypeScript 編譯通過、`mvn compile -q` 通過
- [x] Docker 重建並部署成功
- [x] BMI CDS card 主要顯示 CardSuggestion 訊息，BMIClassification 等補充值分行顯示
- [x] FHIR Resource 物件不再出現在卡片文字中

---

## #009 — FHIR Choice Type 序列化錯誤（value → valueQuantity）

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | Test Case Builder（前端） |
| **嚴重程度** | High |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `frontend/src/components/testcase-builder/ChoiceTypeField.tsx`、`ElementField.tsx`、`ResourceForm.tsx` |
| **Commit** | [`5e69d32`](../../commit/5e69d32) |

### BUG 描述

Visual Builder 中 FHIR Choice Type 欄位（如 `Observation.value[x]`）的值被序列化為基礎名稱 `"value"` 而非帶類型後綴的 `"valueQuantity"`。HAPI FHIR parser 以 lenient 模式忽略未知的 `"value"` 欄位（日誌警告 `Unknown element 'value' found while parsing`），導致 Observation 無值 → CQL `obs.value is not null` 過濾掉所有資源 → 表達式回傳 null。

**根本原因**：`ChoiceTypeField` 正確建立合成元素名稱 `"valueQuantity"`（第 26 行），但將原始 `onChange` 直接傳遞給子 `ElementField`。`ResourceForm.handleFieldChange` 從 path `"Observation.value"` 擷取欄位名稱 `"value"` 儲存至 `resourceData`。`serializeToBundle` 直接複製 `resourceData` → JSON 產生 `"value"` 而非 `"valueQuantity"`。

### 修正方式

- **`ChoiceTypeField.tsx`**：onChange 包裝為 `(val) => onChange(val, syntheticElement.name)`，傳遞正確的帶類型欄位名（如 `"valueQuantity"`）；新增 `initialChoiceType` prop 支援從 JSON 載入時偵測已選類型
- **`ElementField.tsx`**：onChange 簽名擴展為 `(value, choiceFieldName?) => void`；新增 `initialChoiceType` prop 傳遞至 ChoiceTypeField
- **`ResourceForm.tsx`**：
  - `handleFieldChange` 新增 `choiceFieldName` 參數：收到時先清除所有 choice type 變體（如 `valueQuantity`、`valueString` 等），再以正確 key 儲存
  - `getFieldValue` 支援 choice type 查詢：遍歷 `choiceTypes` 尋找帶類型後綴的 key
  - `getSelectedChoiceType` 偵測既有資料的 choice type（用於 JSON ↔ Visual Builder 雙向同步）
  - `filledOptionalNames` 支援 choice type 變體匹配（如 `"valueQuantity"` 對應元素 `"value"`）

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Frontend Docker 重建並部署成功
- [x] Visual Builder 設定 Observation value[x] = Quantity → JSON 序列化為 `"valueQuantity"`
- [x] HAPI parser 不再產生 `Unknown element 'value'` 警告
- [x] CDS Sandbox invoke → 所有 CQL 表達式正確回傳值

---

## #008 — CDS Sandbox Invoke 所有 CQL 表達式回傳 null

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | High |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cds/CdsInvocationService.java`、`backend/src/main/java/com/cqlplatform/service/cds/PrefetchRetrieveProvider.java` |
| **Commit** | [`fccd012`](../../commit/fccd012)、[`5e69d32`](../../commit/5e69d32) |

### BUG 描述

在 CDS Sandbox 中使用 Visual Builder 建立 Observation（例如 BMI: LOINC 39156-5, value 27 kg/m2）並 Invoke 時，CDS card 顯示 "No recommendations at this time."，所有 CQL 表達式（`LatestBMI`、`BMICalue`、`BMIClassification`、`CardSuggestion`）皆回傳 null。

**根本原因（雙重）**：

1. **Subject 缺失**：Visual Builder 建立的 Observation 預設不帶 `subject` 引用，CQL engine 的 context filter 靜默排除無 subject 的資源
2. **CQL engine context filter 類型比對失敗**：CQL engine 3.29.0 的 `RetrieveEvaluator` 執行 post-retrieval context filtering 時，將 FHIR `Reference` 物件與 String `"Patient/test-patient-1"` 做 `equals()` 比較，`Reference.equals(String)` 永遠回傳 false，導致所有資源被靜默排除

### 修正方式

- **`CdsInvocationService.java`**（修正 1 — subject 自動填充）：在 `buildPrefetchProvider()` 解析 prefetch 資源後，`ensureSubjectReference()` 自動為缺少 `subject`/`patient` 引用的資源補上 `Patient/{patientId}`。覆蓋 12 種 FHIR 資源型別
- **`CdsInvocationService.java`**（~~修正 2 — 繞過 context filter~~）：~~當使用 prefetch provider 時，設定 `execRequest.setPatientId(null)` 繞過 CQL engine 的 post-retrieval context filtering~~ ⚠️ **此修正為 #019 的根因，已在 [`c08372c`](../../commit/c08372c) 中撤回**
- **`PrefetchRetrieveProvider.java`**：將 retrieve 日誌從 DEBUG 提升到 INFO，記錄完整的 retrieve 參數及過濾結果

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Backend Docker 重建並部署成功
- [x] PrefetchRetrieveProvider 正確回傳 1 筆 Observation（code filter 正常）
- [x] CQL 表達式（BMIClassification=Overweight, CardSuggestion=BMI:25 'kg/m2'...）正確回傳值
- [x] 預設 sandbox 測試資料行為不變

---

## #007 — Footer fixed 定位仍遮擋操作按鈕

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | 版面配置（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/layout/Footer.tsx`、`frontend/src/App.tsx`、`frontend/src/constants/layout.ts` |
| **Commit** | [`b570119`](../../commit/b570119) |

### BUG 描述

#005 將 Footer 改為 `position: fixed` 後，Footer 仍然浮動覆蓋在頁面內容上方。在 CDS Sandbox Visual Builder 等內容較長的頁面中，底部的操作按鈕（如 Invoke in Sandbox）被 Footer 的 CQL 規範、CDS Hooks、FHIR 連結遮擋，無法點擊。根本原因是 fixed 定位的 Footer 永遠浮在頁面最下方，與 `pb: 36px` 留白方案在捲動式面板中無法完全避免遮擋。

### 修正方式

- **Footer.tsx**：移除 `position: fixed`、`bottom: 0`、`left: 0`、`right: 0`、`zIndex`，改為 `flexShrink: 0` 讓 Footer 回到正常文件流
- **App.tsx**：移除 `pb: '36px'`（不再需要為 fixed footer 預留空間），改為 `minHeight: 0` 確保 flex 子元素正確收縮
- **layout.ts**：`PAGE_CONTENT_HEIGHT` 從 `calc(100vh - 120px)` 調整為 `calc(100vh - 156px)`，多扣除 Footer 的 36px 高度

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Footer 緊貼在頁面內容下方，不遮擋任何操作按鈕
- [x] CDS Sandbox Visual Builder 的 Invoke 按鈕可正常點擊
- [x] 各頁面（Editor、CDS、Measures、FHIR、Authoring）版面正常

---

## #006 — Backend OOM 導致所有 API 無回應

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | Backend 基礎設施 |
| **嚴重程度** | Critical |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `docker/Dockerfile.backend`、`backend/Dockerfile` |
| **Commit** | [`660347a`](../../commit/660347a) |

### BUG 描述

Backend 容器記憶體上限 1GB，JVM 未設定 heap 大小，預設僅使用 ~256MB。在 CQL 翻譯、FHIR 資源處理等記憶體密集操作後觸發 `java.lang.OutOfMemoryError: Java heap space`，導致所有 HTTP 請求（包括登入）完全無回應。

### 修正方式

- Dockerfile 的 ENTRYPOINT 加入 JVM 參數：`-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC`
- MaxRAMPercentage=75.0 讓 JVM 使用容器 75% 記憶體作為 heap（~768MB）
- G1GC 垃圾回收器更適合大 heap 場景

### 測試驗證

- [x] Backend Docker 重建並部署成功
- [x] 重啟後登入功能恢復正常
- [x] 容器穩定運行無 OOM

---

## #005 — Footer 覆蓋頁面內容

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | 版面配置（前端） |
| **嚴重程度** | Low |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/layout/Footer.tsx`、`frontend/src/App.tsx` |
| **Commit** | [`741b7dc`](../../commit/741b7dc) |

### BUG 描述

Footer（CQL 規範、CDS Hooks、FHIR 連結）未固定在畫面底部，隨頁面滾動時會覆蓋到其他內容，特別是在 CDS Sandbox 等內容較多的頁面。

### 修正方式

- Footer 加入 `position: fixed; bottom: 0` 固定在畫面底部
- App.tsx 的 main content 加入 `pb: 36px` 底部留白，避免內容被 Footer 遮擋

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Footer 固定在畫面最下方，不隨頁面滾動
- [x] 頁面內容不被 Footer 遮擋
- [x] Frontend Docker 重建並部署成功

---

## #004 — CDS Sandbox 修改資料後無法重新執行

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CDS Hooks Sandbox（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `frontend/src/components/cds/SandboxPanel.tsx` |
| **Commit** | [`741b7dc`](../../commit/741b7dc) |

### BUG 描述

在 CDS Sandbox 中修改 Visual Builder 的欄位值後，點擊「Invoke in Sandbox」按鈕，結果可能顯示舊資料。原因有二：

1. 舊的 `sandboxResponse` 未清除，使用者無法區分新舊結果
2. Visual Builder 的狀態變更可能未即時同步到 `testDataJson`，導致送出的是修改前的資料

### 修正方式

- 點擊 Invoke 時先 `setSandboxResponse(null)` 清除舊結果
- 若處於 Visual Builder 分頁，在送出前強制重新序列化 `state.entries` → prefetch JSON

### 測試驗證

- [x] TypeScript 編譯通過
- [x] 修改 Visual Builder 欄位後重新 Invoke → 結果正確更新
- [x] 連續多次 Invoke → 每次都能正常執行
- [x] Frontend Docker 重建並部署成功

---

## #003 — Observation status 欄位允許自由輸入導致無效值

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | Test Case Builder / CDS Sandbox（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/testcase-builder/CodeField.tsx` |
| **Commit** | [`741b7dc`](../../commit/741b7dc) |

### BUG 描述

Visual Builder 中 Observation 的 `status` 欄位使用 Autocomplete（freeSolo），允許使用者自由輸入任意值。使用者容易誤填（如填入 LOINC code `39156-5` 而非 `final`），導致產生的 FHIR 資源不合法，CQL 執行時找不到 Observation。此外，status 旁的 TWCORE 術語瀏覽按鈕對標準 FHIR 值集無意義。

### 修正方式

- 當 `bindingStrength === "required"` 且 `boundCodes` 存在時，改用 MUI `Select` 下拉選單取代 freeSolo Autocomplete
- Required binding 情境下隱藏 TWCORE 按鈕
- 其他欄位（如 CodeableConcept 的 code）保留 TWCORE 按鈕不變

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Observation status 顯示為下拉選單（final、preliminary、amended 等）
- [x] status 旁不再顯示 TWCORE 按鈕
- [x] code 欄位的 TWCORE 按鈕仍正常顯示
- [x] Frontend Docker 重建並部署成功

---

## #002 — CQL 翻譯失敗時 Builder 無法顯示已解析的部分結構

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CQL Translation（後端）+ CQL Builder（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cql/CqlTranslationService.java`、`frontend/src/hooks/useCqlStructure.ts` |
| **Commit** | [`f9b3e33`](../../commit/f9b3e33) |

### BUG 描述

CQL 內容含有任何翻譯錯誤時，後端 `CqlTranslationService.translate()` 在 `errors` 非空時直接回傳 response，完全不包含 `metadata`。即使 CQL translator 已成功解析部分結構（如 valueset、code、define），這些資訊也不會回傳給前端。

導致 Builder 面板在 CQL 有任何錯誤時完全無法顯示已解析的結構，使用者只能看到錯誤訊息而無法利用已正確的部分。

### 修正方式

- **後端**：將 `extractMetadata(library)` 提前至錯誤檢查之前執行，即使有錯誤也回傳 partial metadata
- **前端**：`useCqlStructure` hook 將 `if...else if` 改為兩個獨立 `if`，允許同時更新 structure 和顯示 parseError

### 測試驗證

- [x] 後端 `mvn compile -q` 編譯通過
- [x] 前端 `tsc --noEmit` 編譯通過
- [x] CQL 有錯誤時 → Builder 同時顯示已解析的結構 + 錯誤訊息
- [x] CQL 無錯誤時 → 行為不變，完整顯示結構
- [x] Backend + Frontend Docker 重建並部署成功

---

## #001 — CQL Builder 解析 CQL 靜默失敗

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CQL Builder（前端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `frontend/src/hooks/useCqlStructure.ts`、`frontend/src/components/builder/CqlBuilderPanel.tsx` |
| **Commit** | [`3ee28f8`](../../commit/3ee28f8) |

### BUG 描述

使用 CQL Builder 面板解析 CQL 時，若 CQL 內容含有語法錯誤，Builder 不會顯示任何錯誤訊息，結構面板維持空白狀態。具體問題有二：

1. **靜默失敗**：`/api/cql/translate` 回傳 `success: false` 及 `errors` 時，`useCqlStructure` hook 僅檢查 `result.metadata`（錯誤時為 `null`），未將翻譯錯誤設入 `parseError`，導致 Builder 面板無任何回饋。
2. **快取阻擋重試**：`lastParsedContent.current = cql` 在翻譯失敗時仍被設定，導致內容未變更時點擊「Parse CQL」按鈕無法重新解析。

### 修正方式

- `lastParsedContent` 僅在 metadata 成功取得時設定
- 當 `result.success === false` 時，取前 3 筆翻譯錯誤組成訊息，設入 `parseError` 狀態
- Builder 面板 Alert 加入 `whiteSpace: 'pre-line'` 支援多行錯誤顯示

### 測試驗證

- [x] TypeScript 編譯通過（`tsc --noEmit` 零錯誤）
- [x] 輸入有語法錯誤的 CQL → Builder 顯示錯誤訊息
- [x] 修正 CQL 語法後 → Builder 自動重新解析並顯示結構
- [x] 點擊「Parse CQL」按鈕可強制重新解析
- [x] Frontend Docker 重建並部署成功

---

<!--
## 範本

## #00X — 簡短標題

| 欄位 | 內容 |
|------|------|
| **日期** | YYYY-MM-DD |
| **功能分類** | 分類名稱（前端/後端/API） |
| **嚴重程度** | Critical / High / Medium / Low |
| **根因類型** | 邏輯錯誤 / UX 設計缺陷 / 配置遺漏 / 資料處理錯誤 / 併發/效能問題 |
| **影響範圍** | 受影響的檔案路徑 |
| **Commit** | [`xxxxxxx`](../../commit/xxxxxxx) |

### BUG 描述

問題的詳細描述。

### 修正方式

修正的具體做法。

### 測試驗證

- [ ] 測試項目 1
- [ ] 測試項目 2
-->
