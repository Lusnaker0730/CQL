# Patch Log

> 每次功能改善、重構或跨模組統一的詳細記錄。

---

## 總覽索引

| # | 日期 | 範圍 | 標題 | 影響模組 |
|---|------|------|------|----------|
| 001 | 2026-02-19 | 跨模組 | UCUM 單位下拉選單統一 | Test Case Builder, CQL Builder, eQCM, Authoring |
| 002 | 2026-02-19 | i18n | Measures 模組國際化（en / zh-TW） | Measures, Dashboard, Test Case Builder |

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
