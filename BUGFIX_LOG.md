# Bug Fix Log

> 每次 Bug 修正的詳細記錄，包含時間、分類、根因分析、修正方式與測試驗證。

---

## 總覽索引

| # | 日期 | 嚴重程度 | 分類 | 標題 | 根因類型 | Commit |
|---|------|----------|------|------|----------|--------|
| 013 | 2026-02-20 | Medium | CQL Builder（前端） | TWCORE 選碼導致 Monaco Editor 白屏 | 配置遺漏 | [`4c9ae86`](../../commit/4c9ae86) |
| 012 | 2026-02-20 | Medium | 跨模組（前端） | Monaco Editor 夜間模式白屏 | 配置遺漏 | [`e375b1e`](../../commit/e375b1e) |
| 011 | 2026-02-20 | Medium | 版面配置（前端） | Footer 位置異常：flexbox 佈局修正 | UX 設計缺陷 | [`5e69d32`](../../commit/5e69d32) |
| 010 | 2026-02-20 | Low | CDS Hooks Sandbox（前後端） | CDS Card 顯示所有表達式擠在一行 | UX 設計缺陷 | [`5e69d32`](../../commit/5e69d32) |
| 009 | 2026-02-20 | High | Test Case Builder（前端） | FHIR Choice Type 序列化錯誤（value → valueQuantity） | 資料處理錯誤 | [`5e69d32`](../../commit/5e69d32) |
| 008 | 2026-02-19 | High | CDS Hooks Sandbox（後端） | CDS Sandbox Invoke 所有 CQL 表達式回傳 null | 資料處理錯誤 | [`5e69d32`](../../commit/5e69d32) |
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
- [ ] 深色模式下選擇 TWCORE 代碼 → Monaco Editor 維持深色背景
- [ ] 淺色模式下選擇 TWCORE 代碼 → Monaco Editor 維持淺色背景
- [ ] 代碼預覽框（SnippetPreview）語法著色正確

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
| **Commit** | — |

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
| **Commit** | — |

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
| **Commit** | — |

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
| **Commit** | — |

### BUG 描述

在 CDS Sandbox 中使用 Visual Builder 建立 Observation（例如 BMI: LOINC 39156-5, value 27 kg/m2）並 Invoke 時，CDS card 顯示 "No recommendations at this time."，所有 CQL 表達式（`LatestBMI`、`BMICalue`、`BMIClassification`、`CardSuggestion`）皆回傳 null。

**根本原因（雙重）**：

1. **Subject 缺失**：Visual Builder 建立的 Observation 預設不帶 `subject` 引用，CQL engine 的 context filter 靜默排除無 subject 的資源
2. **CQL engine context filter 類型比對失敗**：CQL engine 3.29.0 的 `RetrieveEvaluator` 執行 post-retrieval context filtering 時，將 FHIR `Reference` 物件與 String `"Patient/test-patient-1"` 做 `equals()` 比較，`Reference.equals(String)` 永遠回傳 false，導致所有資源被靜默排除

### 修正方式

- **`CdsInvocationService.java`**（修正 1 — subject 自動填充）：在 `buildPrefetchProvider()` 解析 prefetch 資源後，`ensureSubjectReference()` 自動為缺少 `subject`/`patient` 引用的資源補上 `Patient/{patientId}`。覆蓋 12 種 FHIR 資源型別
- **`CdsInvocationService.java`**（修正 2 — 繞過 context filter）：當使用 prefetch provider 時，設定 `execRequest.setPatientId(null)` 繞過 CQL engine 的 post-retrieval context filtering。Prefetch 資料已為特定病患篩選，無需二次過濾
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
- [ ] Footer 緊貼在頁面內容下方，不遮擋任何操作按鈕
- [ ] CDS Sandbox Visual Builder 的 Invoke 按鈕可正常點擊
- [ ] 各頁面（Editor、CDS、Measures、FHIR、Authoring）版面正常

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
