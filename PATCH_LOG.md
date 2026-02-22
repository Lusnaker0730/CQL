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
| 007 | 2026-02-21 | eQCM | 測試案例批次匯入 + 日期平移 | Measures (Frontend + Backend) | [`76cf867`](../../commit/76cf867) |
| 008 | 2026-02-21 | eQCM | P1-7: 人類可讀文件匯出 | Measures (Backend + Frontend) | [`b106739`](../../commit/b106739) |
| 009 | 2026-02-21 | eQCM | P1-4: 審核者欄位 + 退回原因 UI | Measures (Backend + Frontend) | [`b106739`](../../commit/b106739) |
| 010 | 2026-02-21 | eQCM | P1-6: FHIR Bundle 檔案上傳匯入 | Measures (Frontend) | [`b106739`](../../commit/b106739) |
| 011 | 2026-02-21 | 通知 | P1-5: 持久化通知系統 + 工作流程推播 | Backend + Frontend (Header) | [`b106739`](../../commit/b106739) |
| 012 | 2026-02-21 | eQCM | P2-11: 衛福部指標代碼對照 | Backend + Frontend (Measures) | [`3dbf07a`](../../commit/3dbf07a) |
| 013 | 2026-02-21 | 跨模組 | P2-10: 科別多租戶隔離 | Backend + Frontend (Auth, Measures, Admin) | [`3dbf07a`](../../commit/3dbf07a) |
| 014 | 2026-02-21 | eQCM | P2-9: 指標儀表板增強（Recharts） | Backend + Frontend (Dashboard) | [`3dbf07a`](../../commit/3dbf07a) |
| 015 | 2026-02-21 | FHIR | P2-8: EHR/HIS 整合連接器 | Backend + Frontend (FHIR, Measures) | [`3dbf07a`](../../commit/3dbf07a) |
| 016 | 2026-02-21 | 安全性 | Okta SSO (OIDC) 整合 | Backend + Frontend (Auth, Admin) | [`7d48d6b`](../../commit/7d48d6b) |
| 017 | 2026-02-22 | eQCM | 補完科別分類功能（篩選 + 指派） | Backend + Frontend (Measures) | [`b205335`](../../commit/b205335) |
| 018 | 2026-02-22 | 文件 | API 參考文件 + OpenAPI 規格檔 | 專案根目錄（API.md, openapi.yaml） | [`b6681c0`](../../commit/b6681c0) |

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

## #007 — 測試案例批次匯入 + 日期平移

- **日期**: 2026-02-21
- **範圍**: eQCM — 測試案例管理增強
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心需要從 HIS 系統批次匯入真實病歷資料作為測試資料集，並將歷史病歷的日期調整到評估期間內。原有匯入功能僅在前端逐筆呼叫 API，無日期平移能力，不適合大量資料匯入場景。

### 修改內容

#### Feature A：後端日期平移服務

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/service/measure/DateShiftService.java` |

- 遞迴走訪 FHIR Bundle JSON，識別所有日期相關欄位（`effectiveDateTime`、`birthDate`、`start`、`end`、`issued`、`authored` 等 20+ 個欄位名）
- 支援 FHIR 日期格式：`YYYY-MM-DD`、`YYYY-MM-DDThh:mm:ss+zz:zz`、`YYYY-MM`
- `shiftDates(bundleJson, shiftDays)` — 平移所有日期
- `calculateAutoShift(bundleJson, targetPeriodEnd)` — 自動計算平移天數使最晚日期對齊目標期間

#### Feature B：後端批次匯入端點

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/BatchTestCaseImportRequest.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/BatchTestCaseImportResult.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/TestCaseService.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` |

- `BatchTestCaseImportRequest`：包含 `testCases` 清單和 `dateShiftDays` 參數
- `BatchTestCaseImportResult`：回傳 `successCount`、`failureCount`、`imported` 清單、`errors` 詳細訊息
- `TestCaseService.batchImport()` — 逐筆匯入，失敗不中斷，記錄錯誤
- `POST /api/measures/{measureId}/test-cases/batch-import` — 需擁有者或 ADMIN 權限

#### Feature C：前端匯入對話框

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/components/measure/TestCaseImportDialog.tsx` |
| 修改 | `frontend/src/components/measure/TestCasesTab.tsx` |
| 修改 | `frontend/src/api/measureApi.ts` |
| 修改 | `frontend/src/types/index.ts` |

- `TestCaseImportDialog`：完整匯入對話框，包含：
  - 拖放上傳區（支援 .json / .ndjson）
  - 檔案解析預覽（顯示標題、系列、族群數量）
  - 日期平移開關 + 天數輸入
  - 匯入進度條 + 結果摘要（成功/失敗數、錯誤明細）
  - 支援：JSON 陣列、單一測試案例、MADiE 格式、原始 FHIR Bundle
- `TestCasesTab`：移除舊有 inline 匯入邏輯，改用 `TestCaseImportDialog`
- 匯出增強：新增 `sortOrder` 欄位至匯出格式，提取共用 `toExportShape` 和 `downloadBlob` 輔助函式

#### i18n 鍵新增

| 檔案 | 新增鍵 |
|------|--------|
| `locales/{en,zh-TW}/measures.json` | 18 個 `importDialog.*` 鍵 |

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 4（1 服務 + 2 模型 + 1 元件） |
| 修改檔案 | 6（2 後端 + 2 前端 + 2 locale） |
| 新增 i18n 鍵 | 18（EN + zh-TW） |

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## #008 — P1-7: 人類可讀文件匯出

- **日期**: 2026-02-21
- **範圍**: eQCM — 文件輸出
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心在指標發布前需要產出人類可讀的指標文件供臨床委員會審閱。需要完整的 HTML 文件，包含指標描述、族群準則表格、CQL 邏輯程式碼、以及中繼資料。

### 修改內容

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/service/measure/HumanReadableService.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` |
| 修改 | `frontend/src/api/measureApi.ts` |
| 修改 | `frontend/src/components/measure/MeasureEditor.tsx` |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` |

- `HumanReadableService`：產生完整 HTML 文件，包含：
  - CSS 樣式（teal/navy 配色，@media print 列印支援）
  - 標題區含狀態徽章
  - 目錄（TOC）
  - 描述、理論基礎、臨床指引
  - 每群組族群準則表格
  - 分層器表格
  - CQL 原始碼 `<pre>` 區塊
  - 補充資料、中繼資料表格、頁尾
- `GET /api/measures/{id}/export/human-readable` — 回傳 `text/html`
- 前端：匯出選單新增「Human Readable」選項，以 `Blob` 開啟新視窗

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤

---

## #009 — P1-4: 審核者欄位 + 退回原因 UI

- **日期**: 2026-02-21
- **範圍**: eQCM — 審核工作流程增強
- **分類**: 功能新增 / 工作流程

### 問題描述

審核工作流程缺少審核者追蹤欄位和退回原因 UI。當審核者退回指標時，擁有者無法看到退回原因，需重新溝通才能了解需要修改的內容。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V23__review_workflow_fields.sql` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureDefinitionEntity.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/model/measure/MeasureDefinition.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` |

- V23 遷移：新增 `reviewed_by`、`approved_by`、`review_comment`、`reviewed_at` 欄位
- `approveMeasure()`：設定 `approvedBy`、`reviewedBy`、`reviewedAt`，清除 `reviewComment`
- `rejectMeasure()`：設定 `reviewedBy`、`reviewedAt`、`reviewComment`，清除 `approvedBy`

#### 前端

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/types/index.ts` |
| 修改 | `frontend/src/components/measure/MeasureEditor.tsx` |
| 修改 | `frontend/src/hooks/useMeasures.ts` |
| 修改 | `frontend/src/api/measureApi.ts` |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` |

- MeasureEditor：退回時顯示對話框（含原因 TextField），狀態為 draft 且有 reviewComment 時顯示退回通知 Alert
- `useRejectMeasure` hook 改為接受 `{ id, reason }` 物件
- `rejectMeasure` API 新增 `reason` 選用參數

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤

---

## #010 — P1-6: FHIR Bundle 檔案上傳匯入

- **日期**: 2026-02-21
- **範圍**: eQCM — FHIR Measure Bundle 匯入增強
- **分類**: UX 改善

### 問題描述

MeasureLibrary 的 FHIR Bundle 匯入功能僅支援文字區域貼上 JSON，不支援檔案上傳。使用者需要從檔案系統複製內容到剪貼簿再貼上，不便操作。

### 修改內容

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/MeasureLibrary.tsx` |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` |

- 新增 Upload File 按鈕 + 隱藏 `<input type="file">`（.json, .xml）
- 選擇檔案後自動讀取內容填入文字區域
- 文字區域行數從 12 減少到 10 以容納按鈕

### 驗證

- `npx tsc --noEmit` — 無型別錯誤

---

## #011 — P1-5: 持久化通知系統 + 工作流程推播

- **日期**: 2026-02-21
- **範圍**: 通知系統
- **分類**: 功能新增 / 協作

### 問題描述

平台缺少通知系統。當指標被提交審核、核准或退回時，相關使用者不會收到任何通知，必須手動刷新頁面才能看到狀態變更。醫學中心需要即時通知機制以加速審核流程。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V24__notifications.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/NotificationEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/NotificationRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/NotificationService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/controller/NotificationController.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` |

- `notification` 資料表：recipient、type、title、message、link、is_read、created_at、read_at
- `NotificationService`：完整 CRUD + SSE（Server-Sent Events）即時推播
  - 工作流程通知：`notifyMeasureSubmitted`（通知所有審核者）、`notifyMeasureApproved`、`notifyMeasureRejected`、`notifyMeasureShared`
  - SSE emitter 管理：`ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>`
- `NotificationController`：6 個端點
  - `GET /api/notifications` — 最新 50 則通知
  - `GET /api/notifications/unread-count` — 未讀數量
  - `POST /api/notifications/{id}/read` — 標記已讀
  - `POST /api/notifications/read-all` — 全部標記已讀
  - `DELETE /api/notifications/{id}` — 刪除通知
  - `GET /api/notifications/subscribe` — SSE 訂閱
- `MeasureDefinitionService`：在 `submitForReview`、`approveMeasure`、`rejectMeasure`、`shareMeasure` 中注入通知觸發

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/api/notificationApi.ts` |
| 新增 | `frontend/src/hooks/useNotifications.ts` |
| 新增 | `frontend/src/components/layout/NotificationBell.tsx` |
| 修改 | `frontend/src/components/layout/Header.tsx` |
| 修改 | `frontend/src/locales/{en,zh-TW}/common.json` |

- `notificationApi`：封裝 5 個 API 呼叫（getNotifications、getUnreadCount、markAsRead、markAllAsRead、deleteNotification）
- `useNotifications` hook：React Query 查詢 + SSE 訂閱自動刷新
- `NotificationBell`：Header 工具列的通知鈴鐺按鈕
  - `Badge` 顯示未讀數量（max 99）
  - `Popover` 下拉面板：通知列表 + 時間戳 + 類型圖示
  - 每則通知可點擊導航到相關頁面、刪除、標記已讀
  - 全部標記已讀按鈕
  - 空狀態提示

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 8（4 後端 + 1 SQL + 3 前端） |
| 修改檔案 | 4（1 後端 + 1 前端 + 2 locale） |
| 新增 i18n 鍵 | 7（EN + zh-TW） |
| 新增 API 端點 | 6 |

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## #012 — P2-11: 衛福部指標代碼對照

- **日期**: 2026-02-21
- **範圍**: eQCM — 指標代碼管理
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心需要將 eCQM 指標對應到衛福部、健保署 P4P/DRG、及 CMS 的官方指標代碼，以便在品質申報時進行代碼關聯。目前平台的指標定義缺少這些代碼欄位，也無法瀏覽官方指標清單。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V25__indicator_catalog.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/IndicatorCatalogEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/IndicatorCatalogRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/measure/IndicatorCatalogService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/controller/IndicatorCatalogController.java` |
| 新增 | `backend/src/main/resources/data/indicator_catalog_seed.json` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureDefinitionEntity.java` — 新增 4 欄位 |
| 修改 | `backend/src/main/java/com/cqlplatform/model/measure/MeasureDefinition.java` — 新增 4 欄位 |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` — entityToModel/modelToEntity/update |

- V25 遷移：`measure_definition` 新增 `moh_indicator_code`、`nhia_p4p_code`、`drg_indicator_code`、`indicator_category`；新增 `indicator_catalog` 資料表（code + source 唯一約束）
- `IndicatorCatalogController`：5 個端點（搜尋、取得、建立、更新、批次匯入）
- 種子資料：13 筆（5 MOH + 3 NHIA_P4P + 2 DRG + 3 CMS）

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/api/indicatorApi.ts` |
| 新增 | `frontend/src/components/measure/IndicatorCatalogDialog.tsx` |
| 新增 | `frontend/src/components/measure/IndicatorMappingSection.tsx` |
| 修改 | `frontend/src/types/index.ts` — `IndicatorCatalogEntry` 介面 |
| 修改 | `frontend/src/components/measure/MeasureDetailsTab.tsx` — 嵌入 IndicatorMappingSection |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` — 18 個 `indicators.*` 鍵 |

- `IndicatorMappingSection`：可收合手風琴，含 4 個代碼欄位（MOH、NHIA、DRG、類別），每欄位有搜尋按鈕開啟目錄瀏覽對話框
- `IndicatorCatalogDialog`：搜尋 + 來源篩選 + 表格選取

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## #013 — P2-10: 科別多租戶隔離

- **日期**: 2026-02-21
- **範圍**: 跨模組 — 軟性多租戶
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心有多個科別，各科的指標和資料需要適當隔離，同時支援跨科共享。需要科別概念、科別層級的資料篩選、科別管理員角色。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V26__department_multi_tenancy.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/DepartmentEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/DepartmentRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/DepartmentService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/controller/DepartmentController.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/UserEntity.java` — 新增 `department`、`DEPARTMENT_ADMIN` 角色 |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureDefinitionEntity.java` — 新增 `department` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureReportEntity.java` — 新增 `department` |
| 修改 | `backend/src/main/java/com/cqlplatform/model/measure/MeasureDefinition.java` — 新增 `department` |
| 修改 | `backend/src/main/java/com/cqlplatform/security/OwnershipVerifier.java` — `isDepartmentAdmin`、`getCurrentDepartment`、`verifySameDepartment` |
| 修改 | `backend/src/main/java/com/cqlplatform/security/JwtTokenProvider.java` — department claim |
| 修改 | `backend/src/main/java/com/cqlplatform/config/SecurityConfig.java` — `DEPARTMENT_ADMIN` 授權 |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` — department 篩選 |

- V26 遷移：`department` 資料表（含 10 筆醫院科別種子資料）；`app_user`、`measure_definition`、`measure_report` 新增 `department` 欄位
- `DepartmentController`：5 個端點（列表、取得、子科別、建立、更新）
- JWT token 攜帶 department claim
- `DEPARTMENT_ADMIN` 角色可管理同科別的使用者和指標

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/api/departmentApi.ts` |
| 新增 | `frontend/src/components/common/DepartmentSelector.tsx` |
| 修改 | `frontend/src/types/index.ts` — `Department` 介面 |
| 修改 | `frontend/src/store/authSlice.ts` — department 狀態 |
| 修改 | `frontend/src/locales/{en,zh-TW}/common.json` — 5 個 `department.*` 鍵 |

- `DepartmentSelector`：可重用下拉選單，useQuery 載入科別清單，支援「全部」選項

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## #014 — P2-9: 指標儀表板增強（Recharts）

- **日期**: 2026-02-21
- **範圍**: eQCM — 品質監控視覺化
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心需要完善的品質監控視覺化：趨勢圖（月/季/年）、科別維度下鑽分析、閾值告警、自動產生品質報告。原有儀表板僅有基本概覽卡片和近期評估表格。

### 修改內容

#### 依賴新增

- `recharts` v2.x — 宣告式 React 圖表庫，含 LineChart、BarChart、PieChart、ResponsiveContainer

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V27__dashboard_enhancements.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/MeasureThresholdEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/MeasureThresholdRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/ThresholdAlert.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/EnhancedDashboardData.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/QualityReport.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/measure/DashboardService.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` — 7 個新端點 |

- V27 遷移：`measure_threshold` 資料表（target/warning/critical 閾值）
- `DashboardService`：趨勢計算、科別下鑽、閾值告警偵測、品質報告產生
- 新端點：`/dashboard/enhanced`、`/dashboard/trends`、`/dashboard/department/{code}`、`/dashboard/alerts`、`/{id}/thresholds`（GET/POST）、`/dashboard/report`

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/components/dashboard/ScoreTrendChart.tsx` — Recharts LineChart（多指標疊加 + 閾值參考線） |
| 新增 | `frontend/src/components/dashboard/DepartmentDrilldownChart.tsx` — Recharts BarChart（依閾值著色） |
| 新增 | `frontend/src/components/dashboard/ScoreDistributionChart.tsx` — Recharts PieChart |
| 新增 | `frontend/src/components/dashboard/ThresholdAlertPanel.tsx` — 告警清單（嚴重/警告圖示） |
| 新增 | `frontend/src/components/dashboard/QualityReportPanel.tsx` — 品質報告摘要 + 指標分數表格 |
| 新增 | `frontend/src/components/dashboard/DashboardFilterBar.tsx` — 科別 + 期間篩選列 |
| 修改 | `frontend/src/api/measureApi.ts` — 7 個新 API 函式 |
| 修改 | `frontend/src/types/index.ts` — 7 個新介面 |
| 修改 | `frontend/src/pages/MeasureDashboardPage.tsx` — 重建佈局 |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` — 17 個 `dashboard.*` 鍵 |

儀表板佈局：
```
┌─────────────────────────────────────────────────┐
│ FilterBar: [科別 ▼] [月度 ▼]                    │
├──────────────────────┬──────────────────────────┤
│ 概覽卡片 (5)         │ 閾值告警面板             │
├──────────────────────┴──────────────────────────┤
│ 分數趨勢圖 (LineChart, 全寬)                    │
├────────────────────────┬────────────────────────┤
│ 科別下鑽 (BarChart)    │ 評分分佈 (PieChart)    │
├────────────────────────┴────────────────────────┤
│ 近期評估表格           │ 品質報告面板           │
└─────────────────────────────────────────────────┘
```

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## #015 — P2-8: EHR/HIS 整合連接器

- **日期**: 2026-02-21
- **範圍**: FHIR — 院內系統整合
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心需要與院內 HIS 系統整合：預設連接 FHIR R4 Server、依身分證/病歷號搜尋病人、自動匯入病歷作為測試資料。目前平台的 FHIR 操作僅對手動設定的單一伺服器。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V28__ehr_integration.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/EhrConnectionEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/PatientImportEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/EhrConnectionRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/PatientImportRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/fhir/PatientSearchResult.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/fhir/PatientImportPreview.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/fhir/EhrConnectionService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/fhir/PatientSearchService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/fhir/PatientImportService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/controller/EhrIntegrationController.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/fhir/FhirClientFactory.java` — `createAuthenticatedClient` |

- V28 遷移：`ehr_connection`（連線管理）+ `patient_import`（匯入記錄）
- `EhrConnectionService`：CRUD + `testConnection`（metadata capability 檢查）
- `PatientSearchService`：依 identifier/name 搜尋病人 + 資源預覽（$everything / 逐類型查詢）
- `PatientImportService`：$everything → Bundle → TestCase 建立
- `FhirClientFactory.createAuthenticatedClient`：支援 basic（BasicAuthInterceptor）和 bearer（BearerTokenAuthInterceptor）驗證
- `EhrIntegrationController`：10 個端點（連線 CRUD、測試、病人搜尋、預覽、匯入、匯入記錄）

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/api/ehrApi.ts` |
| 新增 | `frontend/src/components/ehr/EhrConnectionList.tsx` — 連線表格 + 測試/編輯/刪除 |
| 新增 | `frontend/src/components/ehr/EhrConnectionForm.tsx` — 建立/編輯對話框（含驗證方式切換） |
| 新增 | `frontend/src/components/ehr/PatientSearchPanel.tsx` — 病人搜尋面板 |
| 新增 | `frontend/src/components/ehr/PatientImportDialog.tsx` — 匯入預覽 + 確認 |
| 新增 | `frontend/src/components/ehr/PatientImportHistory.tsx` — 匯入歷史表格 |
| 新增 | `frontend/src/components/ehr/EhrImportForTestCase.tsx` — 步驟式匯入對話框（選連線→搜病人→預覽→匯入） |
| 修改 | `frontend/src/api/index.ts` — 匯出 `ehrApi` |
| 修改 | `frontend/src/types/index.ts` — 4 個新介面 |
| 修改 | `frontend/src/pages/FhirPage.tsx` — 新增「EHR 連線」第三分頁 |
| 修改 | `frontend/src/components/measure/TestCaseEditor.tsx` — 「從 EHR 匯入」按鈕 |
| 修改 | `frontend/src/locales/{en,zh-TW}/fhir.json` — 50+ 個 `ehr.*` 鍵 |

- FhirPage 新增第三個分頁：EHR Connections（CloudSync 圖示）
- TestCaseEditor 工具列新增「Import from EHR」按鈕，開啟 EhrImportForTestCase 步驟式對話框
- 連線管理：狀態徽章（untested/success/failed）、驗證方式選擇（none/basic/bearer）、科別歸屬

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## #016 — Okta SSO (OIDC) 整合

- **日期**: 2026-02-21
- **範圍**: 安全性 — 企業 SSO 登入
- **分類**: 功能新增 / 企業適用性

### 問題描述

平台目前僅支援本地帳密（username/password）+ JWT 認證。企業用戶需要透過 Okta SSO 單一登入，以簡化帳號管理並符合企業安全政策。需要在保留現有本地登入的同時，加入 OIDC Authorization Code Flow。

### 架構決策

採用 **Backend-Mediated Authorization Code Flow**：保留現有 JWT 架構不變，不引入 Spring `oauth2Login`（避免與 stateless 設計衝突），由後端自行處理 OIDC token exchange。

```
用戶點擊 "使用 Okta 登入"
  → 前端 redirect 到 Okta 授權頁（帶 state/nonce）
  → Okta 驗證後 redirect 回 /auth/okta/callback?code=xxx&state=yyy
  → 前端 POST /api/auth/okta/callback { code, redirectUri, nonce }
  → 後端 exchange code → 驗證 ID token → JIT 建立/查找用戶 → 產生本地 JWT
  → 前端收到 JWT → dispatch setCredentials（與本地登入完全一致）
```

### 修改內容

#### Step 1：資料庫遷移

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V29__okta_sso.sql` |

- `app_user.password` 改為 nullable（SSO 用戶無密碼）
- 新增 `auth_provider`（VARCHAR(20), NOT NULL, DEFAULT 'LOCAL'）
- 新增 `external_id`（VARCHAR(255)）— Okta subject ID
- 新增 `display_name`（VARCHAR(200)）— Okta 顯示名稱
- 唯一索引 `idx_user_external_id` on (auth_provider, external_id)

#### Step 2：Maven 依賴

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/pom.xml` |

- 新增 `com.nimbusds:nimbus-jose-jwt:9.37.3` — OIDC ID token 簽章驗證（JWKS）

#### Step 3：Okta 設定

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/resources/application.yml` |
| 新增 | `backend/src/main/java/com/cqlplatform/config/OktaProperties.java` |

- `application.yml` 新增 `okta:` 區段（4 個環境變數：`OKTA_ENABLED`, `OKTA_CLIENT_ID`, `OKTA_CLIENT_SECRET`, `OKTA_ISSUER`）
- `OktaProperties`：`@ConfigurationProperties(prefix = "okta")`，衍生方法 `getTokenEndpoint()`、`getJwksUri()`、`getAuthorizationEndpoint()`

#### Step 4：OIDC 服務

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/service/OktaOidcService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/OktaUserInfo.java` |

- `OktaOidcService`（`@ConditionalOnProperty(name = "okta.enabled", havingValue = "true")`）
  - `exchangeCodeForUser(code, redirectUri, nonce)` → OktaUserInfo
  - POST Okta token endpoint（Basic Auth）
  - Nimbus JWKS processor 驗證 ID token 簽章
  - 驗證 iss、aud、nonce、exp claims
  - 擷取 sub、email、preferred_username、name
- `OktaUserInfo`：DTO（sub, email, preferredUsername, name）

#### Step 5：UserEntity + Repository + UserDetailsService

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/entity/UserEntity.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/repository/UserRepository.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/security/CustomUserDetailsService.java` |

- `UserEntity`：新增 `AuthProvider` enum（LOCAL, OKTA）、`authProvider`、`externalId`、`displayName` 欄位；`password` 改為 nullable
- `UserRepository`：新增 `findByAuthProviderAndExternalId(AuthProvider, String)`
- `CustomUserDetailsService`：null password → placeholder `"{noop}SSO_USER_NO_PASSWORD"`（SSO 用戶無法透過本地登入端點認證）

#### Step 6：AuthController 擴充

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/model/auth/OktaCallbackRequest.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/AuthController.java` |

- `OktaCallbackRequest`：code（NotBlank）、redirectUri（NotBlank）、nonce（optional）
- `GET /api/auth/okta/config`：回傳 Okta 設定（enabled, authorizationEndpoint, clientId, scopes）
- `POST /api/auth/okta/callback`：exchange code → JIT provisioning → JWT
  - JIT：`findByAuthProviderAndExternalId(OKTA, sub)` → 未找到則建立新用戶（role=USER, 無密碼）
  - Username 衍生順序：preferred_username > email prefix > okta_{sub}，唯一性保障
  - 更新 displayName/email（若 Okta 端已變更）
  - 檢查 enabled 旗標，拒絕已停用用戶
- `GET /api/auth/me`：回應新增 `authProvider`、`displayName`

#### Step 7：SecurityConfig + AdminController

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/config/SecurityConfig.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/AdminController.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/model/auth/UserSummary.java` |

- SecurityConfig：Okta 端點加入 public 白名單
- AdminController：`toUserSummary()` 包含 `authProvider`；`resetUserPassword()` 拒絕 OKTA 用戶
- UserSummary：新增 `authProvider` 欄位

#### Step 8：前端類型 + API

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/types/index.ts` |
| 修改 | `frontend/src/api/authApi.ts` |

- 新增 `OktaConfig`、`OktaCallbackRequest` 介面
- `User` 和 `UserSummary` 新增 `authProvider?`、`displayName?`
- `authApi` 新增 `getOktaConfig()`、`oktaCallback()`

#### Step 9：OktaCallbackPage

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/pages/OktaCallbackPage.tsx` |

- 從 URL 擷取 code + state
- 驗證 state 匹配 sessionStorage（CSRF 防護）
- 呼叫 `authApi.oktaCallback()` → dispatch `setCredentials` → navigate `/`
- 錯誤狀態：Alert + 「返回登入」按鈕

#### Step 10：LoginPage + App.tsx

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/pages/LoginPage.tsx` |
| 修改 | `frontend/src/App.tsx` |

- LoginPage：掛載時 `getOktaConfig()` → 啟用時顯示 Divider（"OR"）+ 「使用 Okta 登入」按鈕
- 按鈕點擊：生成 state/nonce → 存入 sessionStorage → redirect Okta 授權 URL
- App.tsx：lazy import `OktaCallbackPage`，新增 `/auth/okta/callback` 路由

#### Step 11：AdminUsersPage

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/pages/AdminUsersPage.tsx` |

- 使用者名稱旁顯示 "Okta SSO" Chip（`color="info"`）
- OKTA 用戶隱藏「重設密碼」按鈕

#### Step 12：i18n

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/locales/en/common.json` |
| 修改 | `frontend/src/locales/zh-TW/common.json` |

- 8 個新 `auth.*` 鍵（EN + zh-TW）：or、loginWithOkta、ssoProcessing、ssoFailed、ssoMissingCode、ssoStateMismatch、authProviderLocal、authProviderOkta

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 6（1 SQL + 1 Config + 2 Service + 1 Model + 1 Page） |
| 修改檔案 | 13（5 Backend + 5 Frontend + 2 locale + 1 pom.xml） |
| 新增 i18n 鍵 | 8（EN + zh-TW） |
| 新增 API 端點 | 2（GET /okta/config + POST /okta/callback） |

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- 預設行為（OKTA_ENABLED=false）：GET /api/auth/okta/config → `{"enabled":false}`，登入頁無 Okta 按鈕
- 本地登入流程不受影響
- 啟用 Okta 後：完整 OIDC Authorization Code Flow + JIT 用戶建立

---

## #017 — 補完科別分類功能（篩選 + 指派）

- **日期**: 2026-02-22
- **範圍**: eQCM — 科別分類補完
- **分類**: 功能補完 / 醫學中心適用性

### 問題描述

科別（Department）功能已有 CRUD 基礎設施（DepartmentEntity、DepartmentController、DepartmentSelector 元件），Dashboard 也有科別篩選，但指標管理頁面（MeasureLibrary + MeasureDetailsTab）缺少科別指派和篩選功能，導致科別分類無法在指標層級實際使用。

### 修改內容

#### Step 1：後端 — Repository 新增查詢方法

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/repository/MeasureDefinitionRepository.java` |

- 新增 `findByDepartment(String department)` — 依科別代碼查詢
- 新增 `findByDepartmentAndSearchTerm(String department, String search)` — `@Query` 結合科別 + 名稱/標題模糊搜尋

#### Step 2：後端 — Service 新增科別篩選邏輯

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` |

- 新增 `search(String searchTerm, String department)` 多載方法
- 4 種組合（search+dept / dept-only / search-only / all）各走不同 Repository 查詢
- 原有 `search(String)` 委派至新方法（department=null），無 breaking change

#### Step 3：後端 — Controller 加 department 參數

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` |

- `GET /api/measures` 新增 `@RequestParam(required = false) String department`
- 改用 `definitionService.search(search, department)` 統一入口

#### Step 4：前端 — measureApi 加 department 參數

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/api/measureApi.ts` |

- `getMeasures(search?, department?)` 新增第二選用參數
- 動態建構 `params` 物件，僅在有值時附加

#### Step 5：前端 — MeasureLibrary 加科別篩選器

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/MeasureLibrary.tsx` |

- 搜尋列旁新增 `DepartmentSelector`（showAll=true），與搜尋框水平排列
- `departmentFilter` 狀態 + 傳入 `measureApi.getMeasures(search, department)`
- React Query queryKey 包含 `departmentFilter`，切換科別自動重新查詢
- 表格 "Setting" 欄改為 "Department" 欄，顯示 `m.department` Chip

#### Step 6：前端 — MeasureDetailsTab 加科別欄位

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/MeasureDetailsTab.tsx` |

- General Information accordion 的 Setting 欄位旁新增 `DepartmentSelector`（showAll=false）
- 使用 `updateField('department', value)` 連動表單狀態和 dirty 追蹤

#### Step 7：i18n 翻譯鍵新增

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/locales/en/measures.json` |
| 修改 | `frontend/src/locales/zh-TW/measures.json` |

- `library.tableHeaders.department`: "Department" / "科別"
- `details.fields.department`: "Department" / "科別"
- `details.fields.departmentHelper`: helper text（EN + zh-TW）

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 9（3 後端 + 4 前端 + 2 locale） |
| 新增 i18n 鍵 | 3（EN + zh-TW 各一份） |
| 新增後端方法 | 3（2 Repository + 1 Service） |

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- 無 DB 遷移（`department` 欄位已在 V26 建立）
- 既有 `search(String)` 保持向下相容

---

## #018 — API 參考文件 + OpenAPI 規格檔

- **日期**: 2026-02-22
- **範圍**: 文件 — API 參考手冊
- **分類**: 文件撰寫

### 問題描述

CQL Platform 後端有 17 個 REST Controller、222 個端點，涵蓋 CQL 編輯/執行、品質指標管理、FHIR 資源操作、CDS 決策支援、權限管理等完整功能，但缺乏統一的 API 參考文件。開發者和整合人員需要查閱原始碼才能了解端點用法。

### 修改內容

#### 新增 `API.md` — Markdown API 參考手冊（1,522 行）

| 動作 | 檔案 |
|------|------|
| 新增 | `API.md` |

- 繁體中文撰寫
- 13 個章節：概述、認證、CQL 操作、品質指標、FHIR 資源、CDS 決策支援、CDS 撰寫工具、EHR 整合、管理功能、指標目錄、通知、使用者設定、附錄
- 每個端點包含：HTTP 方法、路徑、說明、參數表格（名稱/位置/類型/必填/說明）、請求範例 JSON、回應範例 JSON
- 涵蓋全部 222 個端點、17 個 Controller

#### 新增 `openapi.yaml` — OpenAPI 3.0.3 規格檔（5,158 行）

| 動作 | 檔案 |
|------|------|
| 新增 | `openapi.yaml` |

- 標準 OpenAPI 3.0.3 格式
- 25 個 tags（依控制器分類）
- JWT Bearer security scheme
- 40+ component schemas（LoginRequest、AuthResponse、CqlLibrary、MeasureDefinition、TestCase、CdsRequest、CdsResponse 等）
- 所有 `description` 欄位使用繁體中文
- 可被 Swagger UI、Redoc、Postman 直接匯入使用

### 涵蓋的 Controller（17 個，222 端點）

| Controller | 端點數 |
|------------|--------|
| AuthController | 8 |
| CqlController | 27 |
| MeasureController | 65 |
| FhirController | 29 |
| CdsServiceConfigController | 13 |
| CdsHooksController | 6 |
| AuthoringController | 25 |
| EhrIntegrationController | 10 |
| AdminController | 5 |
| AuditController | 6 |
| DepartmentController | 5 |
| IndicatorCatalogController | 5 |
| NotificationController | 6 |
| SettingsController | 2 |
| UserApiKeyController | 3 |
| UserLibraryPrefsController | 6 |
| SmartConfigController | 1 |

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 2（API.md + openapi.yaml） |
| 總行數 | 6,680（1,522 + 5,158） |
| 涵蓋端點 | 222 |

### 驗證

- `npx yaml-lint openapi.yaml` — YAML 語法正確
- endpoint 計數：`grep -c 'operationId:' openapi.yaml` → 222

---
