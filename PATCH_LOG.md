# Patch Log

> 每次功能改善、重構或跨模組統一的詳細記錄。

---

## 總覽索引

| # | 日期 | 範圍 | 標題 | 影響模組 | Commit |
|---|------|------|------|----------|--------|
| 001 | 2026-02-19 | 跨模組 | UCUM 單位下拉選單統一 | Test Case Builder, CQL Builder, eQCM, Authoring | [`300bf0f`](../../commit/300bf0f) |
| 002 | 2026-02-19 | i18n | Measures 模組國際化（en / zh-TW） | Measures, Dashboard, Test Case Builder | [`37a9827`](../../commit/37a9827) |
| 003 | 2026-02-20 | i18n | 全平台國際化完成（Phase 5-9） | CDS, FHIR, Terminology, Authoring, Admin | [`b2b7b07`](../../commit/b2b7b07) |
| 004 | 2026-02-20 | 跨模組 | 術語查詢 Drawer + 測試案例草稿自動儲存 | Header, Terminology, Test Case Builder, Measures | [`7429103`](../../commit/7429103) |
| 005 | 2026-02-20 | 安全性 | MeasureController 授權與 IDOR 修復 | Backend — MeasureController, ScheduledMeasureEvaluationService | [`b8f57a6`](../../commit/b8f57a6) |
| 006 | 2026-02-21 | eQCM | Population Criteria 佈局優化 + Reporting 分頁 | Measures (Frontend + Backend) | [`3b66db3`](../../commit/3b66db3) |

---

## #001 — UCUM 單位下拉選單統一

- **日期**: 2026-02-19
- **範圍**: 跨模組 UX 改善
- **分類**: UX 一致性 / 輸入正確性

### 問題描述

平台中只有 Authoring 模組的 `UcumUnitField.tsx` 有完整的 UCUM 單位搜尋下拉選單（65 個單位、11 分類、`freeSolo` 支持自訂輸入）。其餘三個模組的單位輸入都是純文字 `TextField`，使用者需要手動輸入 `mm[Hg]`、`kg/m2` 等 UCUM 代碼，容易出錯。

**受影響位置：**
1. 虛擬病人建構器 — `QuantityField.tsx`（unit / code 都是純文字）
2. CQL Builder — `ParametersSection.tsx`（Quantity 類型的 default value 是純文字）
3. eQCM — `PopulationCriteriaTab.tsx`（Scoring Unit 是純文字）

### 修改內容

#### Step 1：搬移 UcumUnitField 到 common + 擴充單位清單

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/components/common/UcumUnitField.tsx` |
| 修改 | `frontend/src/components/authoring/fields/UcumUnitField.tsx` → re-export |

- 從 Authoring 模組複製 `UcumUnitField` 到 `common/`，成為平台共用元件
- 新增 2 個常用單位：`kg/m2`（BMI，分類 Ratio）、`{score}`（臨床評分量表，分類 Other）
- 新增 `size`、`fullWidth`、`helperText` props 以支援不同模組的排版需求
- 匯出 `UCUM_UNITS` 陣列供其他元件使用（如 QuantityField 需要反查）
- Authoring 原檔改為 re-export，既有引用零影響

#### Step 2：更新 QuantityField（虛擬病人建構器）

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/testcase-builder/QuantityField.tsx` |

- `unit` 欄位從純文字 `TextField` → `UcumUnitField` 搜尋下拉選單
- 選擇已知單位時，自動填入 `system: 'http://unitsofmeasure.org'` 和 `code: UCUM code`
- `system` / `code` 欄位保留但改為可收合的進階區（`Collapse` + `Link` 切換），減少視覺雜亂
- 自由輸入仍可正常運作（`freeSolo`）

#### Step 3：更新 ParametersSection（CQL Builder）

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/builder/ParametersSection.tsx` |

- 當 `paramType` 為 `Quantity` 或 `Interval<Quantity>` 時，default value 區拆成兩個欄位：數值 `TextField` + `UcumUnitField`
- 自動組合成 CQL Quantity literal：`42 'kg'`
- 編輯既有參數時，自動解析 `70 'kg'` 格式回填到兩個欄位
- 非 Quantity 類型時保持原樣

#### Step 4：更新 PopulationCriteriaTab（eQCM Scoring Unit）

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/PopulationCriteriaTab.tsx` |

- Scoring Unit 從 `<TextField>` → `<UcumUnitField>`
- 保留 `freeSolo` 支持自訂值（如 `per 1000`）
- 傳入 `fullWidth` 和 `helperText` 保持原有排版

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功
- Authoring 模組的 ModifierCard / Parameters 中 UcumUnitField 不受影響（re-export 透明）

---

## #002 — Measures 模組國際化（en / zh-TW）

- **日期**: 2026-02-19
- **範圍**: i18n — Measures 模組全面翻譯
- **分類**: 國際化 / 使用者體驗

### 問題描述

平台的 i18n 架構（i18next + react-i18next）已在 Editor、Builder、Common 模組完成（Phase 1-3），但 Measures 模組（含 MeasuresPage、MeasureDashboardPage、26 個 `components/measure/` 元件、19 個 `components/testcase-builder/` 元件）仍使用硬編碼英文字串，共約 400+ 個字串未國際化。

### 修改內容

#### Step 1：建立 measures 命名空間的 locale 檔案

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/measures.json` |
| 新增 | `frontend/src/locales/zh-TW/measures.json` |

- 約 400 個翻譯鍵，按元件分類組織
- 頂層鍵：`page`, `dashboard`, `library`, `editor`, `details`, `cql`, `populationCriteria`, `populationCard`, `dataRequirements`, `evaluation`, `testCases`, `testCaseEditor`, `testCaseResult`, `evaluationResult`, `comparison`, `share`, `validation`, `panel`, `batch`, `workflow`, `reports`, `schedules`, `riskAdjustment`, `supplementalData`, `observations`, `audit`, `dateCalculator`, `coverage`, `testCaseBuilder`

#### Step 2：註冊命名空間

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/i18n.ts` |

- 匯入 `measuresEn` / `measuresZhTW`
- 在 `resources` 中註冊 `measures` 命名空間

#### Step 3：更新頁面元件（2 檔）

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/pages/MeasuresPage.tsx` |
| 修改 | `frontend/src/pages/MeasureDashboardPage.tsx` |

- 加入 `useTranslation('measures')` hook
- 替換頁面標題、副標題、Tab 標籤等硬編碼字串

#### Step 4：更新 Measure 元件（26 檔）

| 動作 | 檔案 |
|------|------|
| 修改 | `components/measure/` 下全部 26 個元件 |

重構模式：
- 模組層級常數（如 `POPULATION_LABELS`、`STATUS_CONFIG`、`WORKFLOW_STEPS`、`AGGREGATE_METHODS`、`PRESET_CRONS`）改為儲存翻譯鍵，在元件內透過 `t()` 解析顯示文字
- 變數名避免遮蔽 `t`（例如 `.map((t) => ...)` → `.map((refType) => ...)`）
- 跨命名空間引用：`t('actions.cancel', { ns: 'common' })`
- 複數支援：使用 i18next `count` 參數（如 `t('key', { count: n })`）

#### Step 5：更新 Test Case Builder 元件（19 檔）

| 動作 | 檔案 |
|------|------|
| 修改 | `components/testcase-builder/constants.tsx` — 移除 `STRINGS` 常數 |
| 修改 | 其餘 18 個 testcase-builder 元件 |

- `constants.tsx` 的 `STRINGS` 物件完全移除，保留 `RESOURCE_ICONS`、`FHIR_UCUM_SYSTEM`、`FHIR_BUNDLE_TYPE`
- 所有 `STRINGS.xxx` 引用改為 `t('testCaseBuilder.xxx')`

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 2（locale JSON） |
| 修改檔案 | 47 |
| 翻譯鍵數 | ~400 |
| 新增行數 | ~867 |
| 刪除行數 | ~719 |

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功
- `grep` 確認 measure / testcase-builder 元件中無殘留硬編碼英文字串
- 語言切換（en ↔ zh-TW）正常運作

---

## #003 — 全平台國際化完成（Phase 5-9）

- **日期**: 2026-02-20
- **範圍**: i18n — 剩餘 5 個模組全面翻譯
- **分類**: 國際化 / 使用者體驗

### 問題描述

平台的 i18n 架構已在 Phase 1-4 完成 Core（common, validation, editor, builder）和 Measures 模組，但 CDS Hooks、FHIR Browser、Terminology、Authoring、Admin/Audit 等 5 個模組仍使用硬編碼英文字串，共約 860+ 個字串未國際化。

### 修改內容

#### Phase 5：CDS Hooks 模組（~120 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/cds.json` |
| 新增 | `frontend/src/locales/zh-TW/cds.json` |
| 修改 | `frontend/src/pages/CdsPage.tsx` |
| 修改 | `frontend/src/components/cds/CdsPanel.tsx` |
| 修改 | `frontend/src/components/cds/InvokeServicePanel.tsx` |
| 修改 | `frontend/src/components/cds/ManageServicesPanel.tsx` |
| 修改 | `frontend/src/components/cds/SandboxPanel.tsx` |
| 修改 | `frontend/src/components/cds/AnalyticsPanel.tsx` |
| 修改 | `frontend/src/components/cds/ApiKeyManager.tsx` |

- 命名空間 `cds`，頂層鍵：`page`, `panel`, `invoke`, `manage`, `sandbox`, `analytics`, `apiKey`
- 7 個元件 + 1 個頁面全部替換為 `t()` 呼叫

#### Phase 6：FHIR Browser 模組（~200 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/fhir.json` |
| 新增 | `frontend/src/locales/zh-TW/fhir.json` |
| 修改 | `frontend/src/pages/FhirPage.tsx` |
| 修改 | `components/fhir/` 下全部 12 個元件 |

- 命名空間 `fhir`，頂層鍵：`page`, `browser`, `search`, `searchParams`, `read`, `validate`, `terminology`, `transaction`, `bulkExport`, `detail`, `editor`, `history`, `ig`
- 13 個檔案全部替換，含 `ImplementationGuideBrowser` 內 5 個子元件

#### Phase 7：Terminology 模組（~70 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/terminology.json` |
| 新增 | `frontend/src/locales/zh-TW/terminology.json` |
| 修改 | `frontend/src/pages/TerminologyPage.tsx` |
| 修改 | `components/terminology/TerminologyBrowser.tsx` |
| 修改 | `components/terminology/ValueSetTab.tsx` |
| 修改 | `components/terminology/CodeLookupTab.tsx` |
| 修改 | `components/terminology/CodeValidationTab.tsx` |

- 命名空間 `terminology`，頂層鍵：`page`, `browser`, `valueSet`, `codeLookup`, `codeValidation`
- 複數支援：`resultCount` / `resultCount_other`

#### Phase 8：Authoring 模組（~450 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/authoring.json` |
| 新增 | `frontend/src/locales/zh-TW/authoring.json` |
| 修改 | `frontend/src/pages/AuthoringPage.tsx` |
| 修改 | `components/authoring/` 下全部 30 個元件 |

- 命名空間 `authoring`，20 個頂層鍵：`page`, `list`, `modal`, `workspace`, `header`, `cpg`, `conjunction`, `element`, `elementBody`, `expression`, `modifier`, `conjunctionType`, `customModifier`, `elementSelect`, `elementDescriptions`, `valueSetField`, `chooseCode`, `subpopulations`, `recommendations`, `errorStatement`, `baseElements`, `parameters`, `externalCql`, `cqlPreview`, `testing`, `summary`, `importCql`, `queryBuilder`
- 含 HTML 的翻譯鍵（如 `expression.ageIs`）使用 `dangerouslySetInnerHTML` 渲染
- 模組層級常數（`GRADES`, `CONDITION_OPTIONS`, `PARAMETER_TYPES`, `TAB_LABELS`）移入元件內部以存取 `t()`
- `ElementSelectDropdown` 使用動態鍵 `` t(`elementDescriptions.${id}`) `` 搭配靜態 fallback

#### Phase 9：Admin & Audit 模組（~90 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/admin.json` |
| 新增 | `frontend/src/locales/zh-TW/admin.json` |
| 修改 | `frontend/src/pages/AdminUsersPage.tsx` |
| 修改 | `frontend/src/pages/AuditDashboardPage.tsx` |

- 命名空間 `admin`，頂層鍵：`users`, `audit`
- `AuditDashboardPage` 將 `t` 作為 prop 傳遞給同檔案內的子元件

#### 共同：註冊命名空間

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/i18n.ts` |

- 新增 5 個命名空間：`cds`, `fhir`, `terminology`, `authoring`, `admin`
- 平台現共有 10 個命名空間：common, validation, editor, builder, measures, cds, fhir, terminology, authoring, admin

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 10（5 組 locale JSON） |
| 修改檔案 | ~55 |
| 翻譯鍵數 | ~930 |
| 命名空間 | 5 個新增（平台共 10 個） |

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功
- 10 組 locale 檔案均存在於 `en/` 和 `zh-TW/` 目錄
- 語言切換（en ↔ zh-TW）涵蓋全平台所有頁面

---

## #004 — 術語查詢 Drawer + 測試案例草稿自動儲存

- **日期**: 2026-02-20
- **範圍**: 跨模組 UX 改善
- **分類**: 功能新增 / 使用者體驗

### 問題描述

使用者編輯 CQL 或建構測試案例病人時，經常需要查詢術語代碼（ICD-10、LOINC、SNOMED 等）。目前存在三個痛點：

1. **測試案例編輯器無草稿持久化** — 切換到其他 Tab（如 CQL）會銷毀所有未儲存的工作，無任何警告
2. **視覺化建構器的代碼欄位僅支援 TWCORE 瀏覽** — 無通用代碼搜尋（依系統 + 文字）
3. **無法在編輯中查詢術語** — 必須完全離開目前的工作環境

### 修改內容

#### Feature A：全域術語查詢 Drawer

右側 MUI Drawer（420px），透過 Header 工具列的 `ManageSearch` 圖示開啟，包含 3 個分頁：

| 分頁 | 重用 Hook | 用途 |
|------|-----------|------|
| Code Search | `useSearchCodes` | 在代碼系統中依文字搜尋代碼 |
| ValueSet Browse | `useSearchValueSets` + `useExpandValueSet` | 尋找值集，展開查看代碼 |
| Code Lookup | `useLookupCode` | 查詢特定系統 + 代碼的詳細資訊 |

每列結果有 **Copy**（剪貼簿）和 **Use**（透過 callback 插入欄位）按鈕。

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/contexts/TerminologyDrawerContext.tsx` |
| 新增 | `frontend/src/hooks/useTerminologyDrawer.ts` |
| 新增 | `frontend/src/components/terminology/TerminologyLookupDrawer.tsx` |
| 新增 | `frontend/src/components/terminology/DrawerCodeSearchPanel.tsx` |
| 新增 | `frontend/src/components/terminology/DrawerValueSetPanel.tsx` |
| 新增 | `frontend/src/components/terminology/DrawerCodeLookupPanel.tsx` |
| 修改 | `frontend/src/main.tsx` — 加入 `<TerminologyDrawerProvider>` |
| 修改 | `frontend/src/components/layout/Header.tsx` — 新增工具列按鈕 + 渲染 Drawer |

關鍵設計：`openDrawer(options?)` 支援 `tab`、`system`、`searchText`、`onSelect` callback，讓代碼欄位可以開啟 Drawer 並接收選取結果，無需緊密耦合。

#### Feature B：測試案例編輯器草稿自動儲存

仿照 `MeasureCqlTab.tsx` 的 localStorage 模式。

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/hooks/useTestCaseDraft.ts` |
| 修改 | `frontend/src/components/measure/TestCaseEditor.tsx` |
| 修改 | `frontend/src/components/measure/TestCasesTab.tsx` |

- `useTestCaseDraft` hook：debounced 5 秒存入 localStorage，鍵格式 `testcase-draft-{measureId}-{testCaseId|'new'}`，7 天過期
- `TestCaseEditor`：掛載時還原草稿，變更時自動儲存，儲存成功時清除；顯示 info Alert 附帶「捨棄草稿」按鈕
- `TestCasesTab`：`editing` 狀態持久化至 `sessionStorage`（`testcase-editing-{measureId}`），切換 Tab 時保留編輯中的測試案例

**流程：**
```
使用者編輯測試案例 → debounced 5 秒存入 localStorage
使用者切換 MeasureEditor Tab → TestCaseEditor 卸載（狀態遺失）
使用者切回 Test Cases Tab →
  TestCasesTab 讀取 sessionStorage → 重新開啟對應測試案例的 TestCaseEditor
  TestCaseEditor 讀取 localStorage → 還原草稿
  顯示 Alert：「已還原先前未儲存的草稿。」[捨棄草稿]
使用者儲存 → 清除 localStorage 草稿 + sessionStorage 編輯狀態
```

#### Feature C：視覺化建構器代碼欄位的術語搜尋按鈕

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/testcase-builder/CodeField.tsx` |
| 修改 | `frontend/src/components/testcase-builder/CodeableConceptField.tsx` |

- `CodeField`：在非 required binding 路徑新增 `Search` 圖示按鈕 → `openDrawer({ tab: 0, system: ..., onSelect: ... })`
- `CodeableConceptField`：每個 coding 列新增 `Search` 圖示按鈕 → `openDrawer({ tab: 0, onSelect: updateCoding })`

#### i18n 鍵新增

| 檔案 | 新增鍵 |
|------|--------|
| `locales/{en,zh-TW}/terminology.json` | 12 個 `drawer.*` 鍵 |
| `locales/{en,zh-TW}/common.json` | `toolbar.terminologyLookup` |
| `locales/{en,zh-TW}/measures.json` | `testCaseEditor.draftRestored`、`testCaseEditor.discardDraft`、`testCaseBuilder.fields.searchTerminology` |

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 6（Context、Hook、4 個 Drawer 元件） + 1（useTestCaseDraft Hook） |
| 修改檔案 | 8（main.tsx、Header.tsx、TestCaseEditor、TestCasesTab、CodeField、CodeableConceptField、6 個 locale JSON） |
| 新增 i18n 鍵 | 17（12 + 1 + 4） |

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功
- 無新增後端端點，全部重用既有 API 和 React Query hooks

---

## #005 — MeasureController 授權與 IDOR 修復

- **日期**: 2026-02-20
- **範圍**: 安全性 — 後端授權強化
- **分類**: 安全性 / 存取控制

### 問題描述

`MeasureController` 存在多項授權缺陷：部分端點缺少所有權檢查，允許任意已認證使用者修改或刪除他人的指標；測試案例端點存在 IDOR（不安全的直接物件參考）漏洞；排程端點完全無授權保護。

### 修改內容

#### 新增輔助方法

| 方法 | 用途 |
|------|------|
| `requireMeasure(Long id)` | 取得指標或拋出 404 |
| `requireOwnedMeasure(Long id)` | 取得指標並驗證當前使用者為擁有者（或 ADMIN） |
| `verifyTestCaseBelongsToMeasure(Long measureId, Long testCaseId)` | 驗證測試案例確實屬於指定指標，防止 IDOR |
| `requireOwnedSchedule(Long scheduleId)` | 透過排程的 `measureDefinitionId` 查詢父指標並驗證所有權 |

#### 高優先級修復

| 問題 | 端點 | 修復 |
|------|------|------|
| `updateMeasure` 所有權繞過 | `PUT /{id}` | 改為 `requireOwnedMeasure(id)` |
| `deleteMeasure` 無授權 | `DELETE /{id}` | 新增 `requireOwnedMeasure(id)` |
| 測試案例 IDOR | 7 個 `/{measureId}/test-cases/**` 端點 | 新增 `requireMeasure` / `requireOwnedMeasure` + `verifyTestCaseBelongsToMeasure` |

#### 中優先級修復

| 問題 | 端點 | 修復 |
|------|------|------|
| `exportReport` 無所有權檢查 | `GET /reports/{reportId}/export` | 新增 `evaluatedBy` 所有權檢查（比照 `getReport` / `deleteReport`） |
| 排程端點無授權 | 5 個排程端點 | `getSchedules` / `createSchedule` 使用 `requireOwnedMeasure(measureId)`；`updateSchedule` / `deleteSchedule` / `triggerSchedule` 使用 `requireOwnedSchedule(scheduleId)` |
| `getMeasuresByOwner` 資訊洩漏 | `GET /owner/{username}` | 限制為本人或 ADMIN，否則回傳 403 |
| `getSharedMeasures` 資訊洩漏 | `GET /shared/{username}` | 限制為本人或 ADMIN，否則回傳 403 |

#### 服務層新增

| 動作 | 檔案 | 修改 |
|------|------|------|
| 修改 | `ScheduledMeasureEvaluationService.java` | 新增 `getScheduleById(Long)` 方法，回傳 `Optional<MeasureScheduleEntity>` |

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 2（MeasureController.java, ScheduledMeasureEvaluationService.java） |
| 修復端點 | 15 個（3 高優先 + 12 中優先） |
| 新增方法 | 4 個輔助方法（Controller） + 1 個服務方法 |

### 驗證

- `mvn compile -q` — 編譯成功
- 所有變更遵循既有 `OwnershipVerifier` 模式（`verifyOwnership` / `isAdmin` / `getCurrentUsername`）
- 唯讀端點（`getTestCase`, `runTestCase`, `runAllTestCases`, `runWithCoverage`）使用 `requireMeasure`（驗證存在性但不限制擁有者）
- 變更端點（`createTestCase`, `updateTestCase`, `deleteTestCase`）使用 `requireOwnedMeasure`（需擁有者或 ADMIN）

---

## #006 — Population Criteria 佈局優化 + Reporting 分頁

- **日期**: 2026-02-21
- **範圍**: eQCM — Population Criteria Tab 重構
- **分類**: UX 改善 / 功能新增

### 問題描述

Population Criteria Tab 存在多項與 MADiE 的差異：所有內容擠在單一垂直捲動頁面、缺少 Reporting 分頁（Improvement Notation + Rate Aggregation）、無佈局分欄（標準族群 vs 排除族群）、無完成度指示器、無側邊欄導覽、分層缺少說明欄位。

### 修改內容

#### Feature A：子分頁結構

將單一頁面拆分為 4 個子分頁：

| 子分頁 | 索引 | 內容 |
|--------|------|------|
| Populations | 0 | 群組欄位 + 雙欄族群卡片 + 觀察條件 + 評分單位 |
| Stratifications | 1 | 每群組的分層管理（含新增的說明欄位） |
| Reporting | 2 | Improvement Notation + Rate Aggregation（新增） |
| Supplemental | 3 | 風險校正 + 補充資料 |

- `subTab` 狀態控制顯示，切換分頁不遺失未儲存的變更
- 儲存按鈕和驗證提示始終顯示於頂部

#### Feature B：雙欄族群佈局

在 Populations 子分頁中，將族群卡片分為左右兩欄：

| 左欄（標準族群） | 右欄（排除/例外） |
|------------------|-------------------|
| Initial Population | Denominator Exclusion |
| Denominator | Denominator Exception |
| Numerator | Numerator Exclusion |
| Measure Population | Measure Population Exclusion |

- 使用 MUI `Grid` 組件，`md={6}` 雙欄，`xs={12}` 小螢幕垂直堆疊
- 無排除族群時左欄自動展開為 `md={12}`
- `EXCLUSION_POPULATION_TYPES` 常數定義分類邏輯

#### Feature C：Reporting 分頁 + 後端支援

新增 Improvement Notation 和 Rate Aggregation 欄位：

**後端：**

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V22__improvement_notation_rate_aggregation.sql` |
| 修改 | `backend/src/main/java/com/cqlplatform/model/measure/MeasureDefinition.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureDefinitionEntity.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` |

- V22 遷移：新增 `improvement_notation VARCHAR(20)` 和 `rate_aggregation VARCHAR(2000)` 欄位
- Model：新增 `improvementNotation`（驗證 `increase|decrease|`）和 `rateAggregation`（@Size max=2000）
- Entity + Service：entityToModel / modelToEntity / update 三處同步更新

**前端：**

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/types/index.ts` — 新增 `improvementNotation` 和 `rateAggregation` 欄位 |
| 修改 | `frontend/src/components/measure/PopulationCriteriaTab.tsx` — Reporting 子分頁 UI |

- Improvement Notation：下拉選單（increase / decrease / 未設定）
- Rate Aggregation：多行文字輸入，說明如何彙總多個族群的計算結果

#### Feature D：完成度指示器

在標題列下方新增 `LinearProgress` 進度條，含 Tooltip 詳細清單：

| 檢查項目 | 條件 |
|----------|------|
| 已定義族群 | 任一群組有族群 |
| 已指定運算式 | 任一族群有 CQL 運算式 |
| 已設定改善標記 | improvementNotation 有值 |
| 已定義分層 | 任一群組有分層 |
| 已定義補充資料 | 有風險校正或補充資料元素 |

- 顯示 `x/5` 計數和百分比進度條
- 100% 完成時進度條變為綠色
- Hover Tooltip 顯示每項的 ✓/○ 狀態

#### Feature E：左側導覽列

新增可摺疊側邊欄（180px），使用 MUI `List` + `ListItemButton`：

| 區段 | 項目 | 點擊行為 |
|------|------|----------|
| Groups | 每個群組 | 切換到 Populations 子分頁 + 捲動到該群組 |
| — | Stratifiers | 切換到 Stratifications 子分頁 |
| — | Reporting | 切換到 Reporting 子分頁 |
| — | Supplemental | 切換到 Supplemental 子分頁 |

- 每個項目顯示 ✓（綠色）/ ○（灰色）完成狀態
- `ChevronLeft` / `ChevronRight` 按鈕切換收合
- 響應式：`md+` 顯示側邊欄，`xs/sm` 隱藏（回退使用水平 Tabs）
- 群組點擊使用 `scrollIntoView({ behavior: 'smooth' })` 平滑捲動

#### Feature F：分層說明欄位

每個 Stratifier 卡片新增多行 `TextField`（2-4 行）：

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/PopulationCriteriaTab.tsx` — Stratifications 子分頁 |

- 使用 `StratifierDefinition.description` 既有欄位（型別和後端已支援）
- 位於 CQL 運算式和 Population Associations 下方

#### i18n 鍵新增

| 檔案 | 新增鍵 |
|------|--------|
| `locales/{en,zh-TW}/measures.json` | `populationCriteria.subTabs.*`（4）、`exclusions`、`noStratifiers` |
| | `populationCriteria.reporting.*`（7）、`populationCriteria.completeness.*`（6） |
| | `populationCriteria.sidebar.*`（3）、`stratifierFields.description/descriptionPlaceholder`（2） |

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 1（V22 SQL 遷移） |
| 修改檔案 | 7（3 後端 + 3 前端 + 1 型別） |
| 新增 i18n 鍵 | ~22（EN + zh-TW 各一份） |
| PopulationCriteriaTab | +546 / -213 行 |

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `mvn compile -q` — 編譯成功
- 所有既有狀態（groups, riskAdjustments, supplementalData）在子分頁間切換時保持不變
- 儲存操作包含新欄位 improvementNotation 和 rateAggregation

---
