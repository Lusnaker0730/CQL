# Bug Fix Log

> 每次 Bug 修正的詳細記錄，包含時間、分類、問題描述、修正方式與測試驗證。

---

## #001 — CQL Builder 解析 CQL 靜默失敗

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CQL Builder（前端） |
| **嚴重程度** | High |
| **影響範圍** | `frontend/src/hooks/useCqlStructure.ts`、`frontend/src/components/builder/CqlBuilderPanel.tsx` |

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

## #002 — CQL 翻譯失敗時 Builder 無法顯示已解析的部分結構

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CQL Translation（後端）+ CQL Builder（前端） |
| **嚴重程度** | Medium |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cql/CqlTranslationService.java`、`frontend/src/hooks/useCqlStructure.ts` |

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

## #003 — Observation status 欄位允許自由輸入導致無效值

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | Test Case Builder / CDS Sandbox（前端） |
| **嚴重程度** | Medium |
| **影響範圍** | `frontend/src/components/testcase-builder/CodeField.tsx` |

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

## #004 — CDS Sandbox 修改資料後無法重新執行

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CDS Hooks Sandbox（前端） |
| **嚴重程度** | Medium |
| **影響範圍** | `frontend/src/components/cds/SandboxPanel.tsx` |

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

## #005 — Footer 覆蓋頁面內容

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | 版面配置（前端） |
| **嚴重程度** | Low |
| **影響範圍** | `frontend/src/components/layout/Footer.tsx`、`frontend/src/App.tsx` |

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

## #006 — Backend OOM 導致所有 API 無回應

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | Backend 基礎設施 |
| **嚴重程度** | Critical |
| **影響範圍** | `docker/Dockerfile.backend`、`backend/Dockerfile` |

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

<!--
## 範本

## #00X — 簡短標題

| 欄位 | 內容 |
|------|------|
| **日期** | YYYY-MM-DD |
| **功能分類** | 分類名稱（前端/後端/API） |
| **嚴重程度** | Critical / High / Medium / Low |
| **影響範圍** | 受影響的檔案路徑 |

### BUG 描述

問題的詳細描述。

### 修正方式

修正的具體做法。

### 測試驗證

- [ ] 測試項目 1
- [ ] 測試項目 2
-->
