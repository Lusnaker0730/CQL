# eCQM Authoring Tool — MADiE 對標開發計畫

> **目標**: 將 CQL Platform 打造為可獨立商用的 eCQM Authoring & Testing 工具
> **對標**: [MADiE (Measure Authoring Development Integrated Environment)](https://ecqi.healthit.gov/tool/madie)
> **定位**: 面向 Measure Developer、品質報告團隊、醫療機構品管部門
> **估算**: 6 人月 (1-2 Full Stack, 約 4 個月)

---

## 現狀盤點 (已完成 vs MADiE)

| MADiE 功能 | 我們的狀態 | 差距 |
|-----------|----------|------|
| Measure CRUD + metadata | ✅ 完成 | — |
| CQL 編輯器 (Monaco) | ✅ 完成 | — |
| CQL Builder (視覺化) | ✅ 完成 | — |
| Population Criteria 視覺編輯 | ✅ 完成 (含 auto-map、dual IP、stratifier) | — |
| 5 種 Scoring Type | ✅ proportion/ratio/cohort/CV/composite | — |
| Test Case CRUD + 執行 | ✅ 完成 | — |
| Test Coverage Analysis | ✅ 完成 | — |
| Measure Evaluation | ✅ 完成 | — |
| Report Export (FHIR/CSV/Excel) | ✅ 完成 | — |
| FHIR Measure Import/Export | ✅ 完成 | — |
| CQL Library 版本管理 | ✅ 完成 (含共享+依賴分析) | — |
| Risk Adjustment + Supplemental Data | ✅ 完成 | — |
| Observation (CV aggregation) | ✅ 完成 | — |
| Measure Versioning + History | ✅ 完成 | — |
| CQL Library 共享/權限 | ✅ 完成 | — |
| Test Case Import/Export | ✅ 完成 (含 MADiE 格式) | — |
| QDM 支持 | ❌ 不做 | CMS 已轉向 FHIR，QDM 是 legacy |
| QI-Core Profile 驗證 | ❌ 缺 | **Phase 3** |
| HQMF Export (XML) | ❌ 缺 | **Phase 5** |
| FHIR Measure Bundle 完整封裝 | ⚠️ 部分 | **Phase 4** |
| Measure 共享/所有權轉移 | ✅ 完成 (含審批工作流+審計) | — |
| Measure 匯入 (MADiE JSON) | ❌ 缺 | **Phase 4** |
| 完整 Validation Pipeline | ⚠️ 基本 | **Phase 3** |
| Multi-rate Measure | ❌ 缺 | **Phase 6** |

---

## Phase 0: 基礎強化 (1 週) ✅ COMPLETED

> 穩固現有基礎，清理技術債

### 0.1 MeasureDetailsTab 增強 metadata ✅
- [x] 新增 `rationale`, `clinicalGuidance`, `steward`, `developers[]`, `references[]` 編輯 UI
- [x] 新增 `disclaimer`, `copyright`, `measureSet` 欄位
- [x] 後端 MeasureDefinitionEntity 已有 JSON 欄位，無需 DB migration

### 0.2 CQL Editor 整合強化 ✅
- [x] CQL error markers 同步到 Monaco editor (紅色波浪線) — translate dispatch to Redux (setErrors/setWarnings/setElmJson)
- [x] Error/Warning count Chips in toolbar
- [x] Auto-save draft (debounced 5s, 僅存 localStorage, 不觸發 API) — with draft restoration + "Discard Draft" button
- [x] Invalidate cql-expressions query on successful save

### 0.3 Test Case 匯入/匯出 ✅
- [x] Export: 單一 test case → FHIR Bundle JSON 下載
- [x] Export: 全部 test cases → JSON array 下載
- [x] Import: 上傳 JSON → 自動建立 test case
- [x] Import: 支援 MADiE JSON test case 格式 (偵測 `json`, `groupName`, `groupPopulationValues` 欄位自動轉換)

**交付物**: 更完整的 measure metadata 編輯、更好的 CQL 編輯體驗、test case 可攜性

---

## Phase 1: CQL Library 管理強化 (2 週) ✅ COMPLETED

> MADiE 的 CQL Library Service 是獨立微服務，支援共享、版本鏈、影響分析

### 1.1 Library 共享與權限模型 ✅

**後端**:
- [x] `CqlLibraryEntity` 新增: `ownerUsername`, `sharedWith` (JSON array), `accessLevel` (private/shared/public)
- [x] V14 DB migration: `owner_username`, `shared_with`, `access_level` columns + indexes
- [x] `POST /api/cql/libraries/{id}/share` — 共享給指定 user
- [x] `POST /api/cql/libraries/{id}/unshare` — 撤銷共享
- [x] `POST /api/cql/libraries/{id}/transfer` — 所有權轉移
- [x] `PUT /api/cql/libraries/{id}/access` — 設定 access level
- [x] `GET /api/cql/libraries/owner/{username}` — 按 owner 查詢
- [x] `GET /api/cql/libraries/shared/{username}` — 取得共享/公開 libraries
- [x] 權限檢查: 只有 owner 可 share/unshare/transfer/setAccess

**前端**:
- [x] LibraryQuickAccess 增加 Browse section with filter tabs: All / My / Shared / Public
- [x] Access level icons (Lock/Group/Public) per library
- [x] LibraryShareDialog: access level toggle, share with username, unshare, transfer ownership
- [x] Share button in EditorPage toolbar
- [x] Frontend types: CqlLibrary + ownerUsername, sharedWith, accessLevel
- [x] API methods: 8 new sharing/dependency endpoints in cqlApi
- [x] React Query hooks: useShareLibrary, useUnshareLibrary, useTransferOwnership, useSetAccessLevel

### 1.2 Library 依賴圖與影響分析 ✅

**後端**:
- [x] `GET /api/cql/libraries/{id}/dependencies` — 此 library 引用了哪些 library (遞迴)
- [x] `GET /api/cql/libraries/dependents/{name}` — 哪些 library 引用了此 library
- [x] `CqlLibraryService.getDependencies()` — recursive dependency traversal
- [x] `CqlLibraryService.getDependents()` — via `findByDependenciesContaining()`

**前端**:
- [x] LibraryDependencyPanel: Dependencies list + Impact Analysis panel
- [x] Impact count alert: "N libraries will be affected by changes"
- [x] Click-to-load: click any dependency/dependent to load its CQL
- [x] New "Dependencies" tab in EditorPage right panel
- [x] React Query hooks: useLibraryDependencies, useLibraryDependents

### 1.3 Library Lifecycle (partial)

- [x] Status field exists (draft/active/retired) on CqlLibraryEntity
- [x] Version creation sets old version to active, new version to draft
- [ ] Active library 不可修改, 需建新版本 (enforcement)
- [ ] Retire 時檢查是否有活躍的 measure 依賴

**交付物**: Library 共享生態系、依賴追蹤、版本管理完善

---

## Phase 2: Measure 共享 + 多角色權限 (1.5 週) ✅ COMPLETED

> MADiE 要求透過 email 申請共享, 我們做自助式

### 2.1 Measure 共享模型 ✅

**後端**:
- [x] `MeasureDefinitionEntity` 新增: `ownerUsername`, `sharedWith` (JSON), `accessLevel`
- [x] V15 DB migration: `owner_username`, `shared_with`, `access_level` columns + `measure_audit` table
- [x] `POST /api/measures/{id}/share` — 共享給 user
- [x] `POST /api/measures/{id}/unshare` — 撤銷共享
- [x] `POST /api/measures/{id}/transfer` — 所有權轉移
- [x] `PUT /api/measures/{id}/access` — 設定 access level
- [x] `GET /api/measures/owner/{username}` — 按 owner 查詢
- [x] `GET /api/measures/shared/{username}` — 取得共享/公開 measures

**前端**:
- [x] MeasureLibrary filter tabs: All / My Measures / Shared with Me / Public
- [x] Access level icons (Lock/Group/Public) per measure + Owner column
- [x] MeasureShareDialog: access level toggle, share with username, unshare, transfer ownership
- [x] Share button in MeasureEditor header
- [x] Frontend types: MeasureDefinition + ownerUsername, sharedWith, accessLevel, MeasureAuditEntry
- [x] API methods: 11 new sharing/workflow/audit endpoints in measureApi
- [x] React Query hooks: 10 hooks in useMeasures.ts

### 2.2 Measure 審批工作流 (簡化版) ✅

- [x] Status: Draft → In Review → Active → Retired (VALID_TRANSITIONS map)
- [x] "Submit for Review" button: Draft → In Review (owner only)
- [x] "Approve" / "Reject" buttons: In Review → Active or back to Draft (reviewer only)
- [x] 只有 owner 可 submit, 只有 owner/sharedWith 可 approve/reject
- [x] StatusChip updated: "in-review" status with blue info color + RateReview icon
- [x] WorkflowIndicator updated: 5 steps (Details → CQL → Populations → Review → Active)
- [x] Contextual workflow buttons in MeasureEditor header with success/error alerts

### 2.3 Audit Trail ✅

**後端**:
- [x] `MeasureAuditEntity` + `MeasureAuditRepository` (NEW)
- [x] `GET /api/measures/{id}/audit` — 返回此 measure 的所有變更記錄
- [x] Audit recorded on all CRUD + sharing + workflow operations
- [x] Records: action, performedBy, details, oldValue, newValue, createdAt

**前端**:
- [x] AuditTrailDialog: timeline list with action icons + color coding, who/when/what
- [x] "Audit" button in MeasureEditor header
- [x] useMeasureAuditTrail hook with lazy loading (only fetches when dialog opens)

**交付物**: 團隊協作、品質管控流程、變更追蹤

---

## Phase 3: Validation Pipeline (2 週)

> MADiE 的核心價值: 確保 measure 在提交前完全合規

### 3.1 CQL Validation 強化

**後端** `MeasureValidationService.java` (新建):
- [ ] **CQL 語法驗證**: 翻譯無 error (已有, 封裝)
- [ ] **Library 依賴完整性**: 所有 include 的 library 存在且 Active
- [ ] **Expression 使用驗證**: Population criteria 引用的 expression 必須存在於 CQL
- [ ] **Return Type 驗證**: Population expression 返回 Boolean (basis=Boolean 時)
- [ ] **Unused Expression 警告**: CQL 中有 define 但未被任何 population/stratifier 引用
- [ ] **FHIR Profile 合規**: 引用的 resource type 必須在 QI-Core profile 內

### 3.2 Measure Completeness 驗證

- [ ] **Required Fields**: name, version, scoringType, status, ≥1 group, ≥1 population
- [ ] **Population Logic**: 依 scoring type 檢查必要 population 全部有 expression
- [ ] **Stratifier 完整性**: 有 stratifier 必須有 expression + associations
- [ ] **Risk Adjustment / Supplemental Data**: 如有定義, expression 必須存在
- [ ] **Observation (CV)**: continuous-variable/ratio 需有 observation + aggregate method
- [ ] **Test Case 充分性**: 至少 N 個 test case, 且 pass rate ≥ 80% (可配置)

### 3.3 QI-Core Profile 驗證

**後端**:
- [ ] 下載並快取 QI-Core 4.1.1 StructureDefinition
- [ ] 驗證 CQL 中引用的 FHIR resource path 合規 (如 `[Condition]` → QI-Core Condition profile)
- [ ] 驗證 retrieve 的 code path 在 profile 中存在

**前端** `MeasureValidationPanel.tsx` (新建):
- [ ] 「Validate」button in MeasureEditor header (或獨立 tab)
- [ ] Validation result 分級: Error / Warning / Info
- [ ] 按 category 分組: CQL / Populations / Metadata / Test Cases / QI-Core
- [ ] 「Fix」quick-action links: 點擊跳轉到相關 tab 的問題位置
- [ ] Pre-save validation: Save 前自動跑 lightweight check (不含 QI-Core)

### 3.4 REST API

- [ ] `POST /api/measures/{id}/validate` — 完整 validation, 返回 `ValidationReport`
- [ ] `POST /api/measures/{id}/validate/quick` — 輕量 check (CQL + populations)

**交付物**: 提交前品質閘門, 大幅減少 measure 開發迭代次數

---

## Phase 4: FHIR Measure Bundle 完整封裝 (2 週)

> CMS 2026 起接受 FHIR Bundle 格式, MADiE 的 QI-Core export 就是這個

### 4.1 Measure Bundle 生成

**後端** `FhirMeasureBundleService.java` (新建):

一個合規的 eCQM FHIR Bundle 包含:
```
Bundle (type: collection)
├── Measure resource (metadata + groups + populations)
├── Library resource (CQL 原文 + ELM JSON, base64 encoded)
├── Library resource (FHIRHelpers, 如需要)
├── Library resource (每個 included library)
├── ValueSet resource (每個引用的 value set, expanded)
└── CodeSystem resource (每個引用的 code system)
```

- [ ] `Measure` resource 生成 (強化現有 FhirMeasureService)
  - 完整 population coding (system: `http://terminology.hl7.org/CodeSystem/measure-population`)
  - Stratifier + supplemental data + risk adjustment
  - `relatedArtifact` for references
  - `effectivePeriod` for measure period
- [ ] `Library` resource 生成
  - `content[0]`: CQL 原文 (contentType: `text/cql`, base64)
  - `content[1]`: ELM JSON (contentType: `application/elm+json`, base64)
  - `relatedArtifact` for included libraries
  - `dataRequirement` for FHIR resource types used
- [ ] Dependency library 解析: 遞迴打包所有 include 的 library
- [ ] ValueSet 展開: 透過 VSAC 拉取所有引用的 value set, 包在 bundle 裡
- [ ] Bundle metadata: `identifier`, `timestamp`, `type: collection`

### 4.2 FHIR Bundle Import (解包)

- [ ] 上傳 FHIR Bundle JSON → 解析出 Measure + Library + ValueSets
- [ ] 自動建立 MeasureDefinition + CQL Library + populate groupDefinitions
- [ ] 衝突處理: 若 library name+version 已存在, 詢問 skip/overwrite

### 4.3 Export UI 強化

**前端**:
- [ ] MeasureEditor toolbar 新增 "Export" dropdown:
  - Export as FHIR Bundle (JSON)
  - Export as FHIR Bundle (XML)
  - Export CQL only
  - Export ELM only
  - Export Test Cases (ZIP)
- [ ] Import button: 接受 FHIR Bundle JSON/XML, 自動解包

### 4.4 REST API

- [ ] `GET /api/measures/{id}/export/bundle` — 完整 FHIR Bundle (JSON)
- [ ] `GET /api/measures/{id}/export/bundle?format=xml` — XML 版
- [ ] `POST /api/measures/import/bundle` — 匯入 FHIR Bundle

**交付物**: CMS FHIR-based 提交格式, 可與其他工具互通

---

## Phase 5: HQMF Export + QRDA (2 週)

> 2026 CMS 仍接受 QRDA III (XML/CDA), HQMF 是 measure spec 格式
> 這是 CMS 合規提交的最後一哩路

### 5.1 HQMF (Health Quality Measure Format) Export

**後端** `HqmfExportService.java` (新建):

HQMF 是 HL7 CDA-based XML 格式, 描述 measure 定義:
- [ ] HQMF R2.1 模板: measure metadata, data criteria, population criteria
- [ ] CQL 嵌入: `<cql-library>` element
- [ ] Population coding: IPP, DENOM, NUMER 等 HQMF 標準 code
- [ ] Scoring type mapping: proportion → HQMF scoring OID
- [ ] Value set OID 引用: 對應 VSAC OID
- [ ] 使用 Java XML DOM builder (不需外部 library)

### 5.2 QRDA Category III (Aggregate Report) Export

**後端** `QrdaExportService.java` (新建):

QRDA III 是 CDA-based 聚合品質報告:
- [ ] CDA Header: 機構資訊, 報告期間, custodian
- [ ] Measure Section: measure OID, scoring, populations
- [ ] Population 結果: IPP count, DENOM count, NUMER count 等
- [ ] Stratifier 結果: per-stratum counts
- [ ] Performance Rate 計算: (NUMER / (DENOM - DENEX)) * 100
- [ ] 符合 [2026 CMS QRDA III IG](https://ecqi.healthit.gov/sites/default/files/2026-CMS-QRDA-III-EC-IG-v1.1.pdf) 規範
- [ ] Schematron 驗證: 下載 CMS schematron, 用 Jing/SchematronValidator 驗 output

### 5.3 QRDA Category I (Patient-Level) Export

QRDA I 是個別病患的品質資料:
- [ ] CDA Header: patient demographics
- [ ] Entry templates: diagnosis, encounter, medication, procedure
- [ ] 嵌入 measure reference + population membership
- [ ] 較低優先: 主要用途是 EHR → registry, 我們先專注 QRDA III

### 5.4 REST API + UI

- [ ] `GET /api/measures/{id}/export/hqmf` — HQMF XML 下載
- [ ] `GET /api/measures/reports/{reportId}/export?format=qrda3` — QRDA III XML
- [ ] `GET /api/measures/reports/{reportId}/export?format=qrda1&patientId=X` — QRDA I XML
- [ ] MeasureReportHistory 的 Export 按鈕增加 QRDA III 選項
- [ ] Validation badge: 匯出前自動跑 schematron, 顯示 pass/fail

**交付物**: 完整 CMS 提交格式支持 (HQMF + QRDA I/III)

---

## Phase 6: 進階功能 + 競爭力 (2 週)

> 超越 MADiE 的差異化功能

### 6.1 Multi-rate Measure 支持

部分 CMS measure (如 CMS69v13) 一個 measure 包含多個 rate:
- [ ] GroupDefinition 增加 `rateIndex`, `rateDescription`
- [ ] PopulationCriteriaTab 支援多個 rate 在同一 group 內
- [ ] Evaluation 分開計算每個 rate 的 score
- [ ] Report 展示 per-rate score

### 6.2 Measure Diff 視覺化

- [ ] 兩個 measure 版本的完整 side-by-side diff
- [ ] CQL diff (已有) + metadata diff + population diff + test case diff
- [ ] Diff summary: 新增/修改/刪除 的項目計數

### 6.3 Measure Dashboard (組織層級)

**前端** `MeasureDashboardPage.tsx` (新建):
- [ ] 總覽卡片: 總 measures 數、Active/Draft/Retired 分布
- [ ] Pass Rate 分布: test case 全通過 / 部分通過 / 未測試
- [ ] 最近評估結果趨勢
- [ ] 待審核 measure 列表 (In Review 狀態)
- [ ] 按 steward/developer 篩選

### 6.4 CQL Library Repository (公共庫)

- [ ] 預建 CQL 常用函式庫:
  - `CQLHelpers` — 常用 utility functions
  - `QICoreCommon` — QI-Core 常用 retrieve patterns
  - `MATGlobalCommonFunctions` — 移植自 MADiE 的 global functions
- [ ] 一鍵 include 到當前 measure

### 6.5 Batch Measure Evaluation

- [ ] 選擇多個 measures → 批次評估
- [ ] 非同步執行 + 進度條 (WebSocket 或 polling)
- [ ] 批次 QRDA III 匯出 (一個 XML 包含多 measure 結果)

**交付物**: 差異化競爭力、組織級品管能力

---

## 進度總覽

| Phase | 狀態 | 完成日期 |
|-------|------|----------|
| **Phase 0**: 基礎強化 | ✅ COMPLETED | 2026-02 |
| **Phase 1**: Library 管理 | ✅ COMPLETED | 2026-02 |
| **Phase 2**: Measure 共享 | ✅ COMPLETED | 2026-02 |
| **Phase 3**: Validation | ⬜ TODO | — |
| **Phase 4**: FHIR Bundle | ⬜ TODO | — |
| **Phase 5**: HQMF + QRDA | ⬜ TODO | — |
| **Phase 6**: 進階功能 | ⬜ TODO | — |

### 實作細節 (Phase 0-2)

**Phase 0 新增/修改的檔案:**
- `frontend/src/components/measure/MeasureCqlTab.tsx` — error dispatch, auto-save, draft restore
- `frontend/src/components/measure/TestCasesTab.tsx` — import/export with MADiE format detection

**Phase 1 新增/修改的檔案:**
- `backend/src/main/resources/db/migration/V14__library_sharing.sql` (NEW)
- `backend/src/main/java/com/cqlplatform/entity/CqlLibraryEntity.java` — ownerUsername, sharedWith, accessLevel + serialization
- `backend/src/main/java/com/cqlplatform/model/CqlLibrary.java` — ownerUsername, sharedWith, accessLevel
- `backend/src/main/java/com/cqlplatform/repository/CqlLibraryRepository.java` — 3 new query methods
- `backend/src/main/java/com/cqlplatform/service/cql/CqlLibraryService.java` — 8 new sharing/dependency methods
- `backend/src/main/java/com/cqlplatform/controller/CqlController.java` — 8 new REST endpoints
- `frontend/src/types/index.ts` — CqlLibrary + ownerUsername, sharedWith, accessLevel
- `frontend/src/api/index.ts` — 8 new cqlApi methods
- `frontend/src/hooks/useCql.ts` — 6 new hooks (dependencies, dependents, share, unshare, transfer, accessLevel)
- `frontend/src/components/editor/LibraryDependencyPanel.tsx` (NEW) — dependency tree + impact analysis
- `frontend/src/components/editor/LibraryShareDialog.tsx` (NEW) — sharing dialog
- `frontend/src/components/editor/LibraryQuickAccess.tsx` — Browse section with All/My/Shared/Public tabs
- `frontend/src/pages/EditorPage.tsx` — Share button + Dependencies tab + ShareDialog integration

**Phase 2 新增/修改的檔案:**
- `backend/src/main/resources/db/migration/V15__measure_sharing_audit.sql` (NEW) — sharing columns + audit table
- `backend/src/main/java/com/cqlplatform/entity/MeasureDefinitionEntity.java` — ownerUsername, sharedWith, accessLevel + serialization
- `backend/src/main/java/com/cqlplatform/entity/MeasureAuditEntity.java` (NEW) — audit trail entity
- `backend/src/main/java/com/cqlplatform/model/measure/MeasureDefinition.java` — ownerUsername, sharedWith, accessLevel
- `backend/src/main/java/com/cqlplatform/repository/MeasureDefinitionRepository.java` — findByOwnerUsername, findByAccessLevel
- `backend/src/main/java/com/cqlplatform/repository/MeasureAuditRepository.java` (NEW) — audit queries
- `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` — sharing, workflow (VALID_TRANSITIONS), audit methods
- `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` — 11 new REST endpoints
- `frontend/src/types/index.ts` — MeasureDefinition sharing fields + MeasureAuditEntry interface
- `frontend/src/api/index.ts` — 11 new measureApi methods (sharing, workflow, audit)
- `frontend/src/hooks/useMeasures.ts` (NEW) — 10 hooks (share, unshare, transfer, accessLevel, workflow x4, audit)
- `frontend/src/components/measure/MeasureShareDialog.tsx` (NEW) — sharing dialog
- `frontend/src/components/measure/AuditTrailDialog.tsx` (NEW) — audit timeline dialog
- `frontend/src/components/measure/MeasureLibrary.tsx` — filter tabs (All/My/Shared/Public), owner column, access icons
- `frontend/src/components/measure/MeasureEditor.tsx` — Share, Audit, workflow buttons (Submit/Approve/Reject/Retire)
- `frontend/src/components/common/StatusChip.tsx` — "in-review" status support
- `frontend/src/components/measure/WorkflowIndicator.tsx` — "Review" step added (5-step workflow)

---

## 里程碑時程

```
Month 1          Month 2          Month 3          Month 4
|== Phase 0 ==|== Phase 1 ====|-- Phase 2 --|
              |               |-- Phase 3 ----|
                              |-- Phase 4 ----|
                                             |-- Phase 5 ----|
                                             |-- Phase 6 ----|
```

| Phase | 週數 | 人力 | 累計可交付 |
|-------|------|------|----------|
| **Phase 0**: 基礎強化 ✅ | 1 | 1 FE | 更完整的 metadata + test case 攜帶性 |
| **Phase 1**: Library 管理 ✅ | 2 | 1 FE + 1 BE | Library 共享 + 依賴分析 |
| **Phase 2**: Measure 共享 ✅ | 1.5 | 1 FE + 1 BE | 團隊協作 + 審批流程 |
| **Phase 3**: Validation | 2 | 1 FE + 1 BE | Pre-submission 品質閘門 |
| **Phase 4**: FHIR Bundle | 2 | 1 BE + 0.5 FE | CMS FHIR 格式提交 |
| **Phase 5**: HQMF + QRDA | 2 | 1 BE + 0.5 FE | CMS 傳統格式提交 |
| **Phase 6**: 進階功能 | 2 | 1 FE + 1 BE | 差異化 + Dashboard |
| **合計** | **~12.5 週** | | **MADiE 對等 + 超越** |

---

## 技術決策

### 不做的事 (節省工程量)

| 決策 | 理由 |
|------|------|
| **不做 QDM 支持** | CMS 已強制轉向 FHIR/QI-Core, QDM 是 legacy, MADiE 自己也在淘汰 |
| **不做 Micro-frontend** | MADiE 用 single-spa 但很複雜, 我們的 monolithic React 更快迭代 |
| **不做 Okta/SAML SSO** | 先維持 JWT, SSO 是 enterprise 部署議題, 需要時再加 |
| **不做 SMART on FHIR Launch** | 定位是獨立工具, 不嵌入 EHR |
| **不做 Real-time Collaborative Editing** | 複雜度極高, 用共享+鎖定機制替代 |

### 需要的新依賴

| 依賴 | 用途 | Phase |
|------|------|-------|
| 無 (Java XML DOM) | HQMF/QRDA XML 生成 | Phase 5 |
| `ph-schematron` 或 Jing | QRDA Schematron 驗證 | Phase 5 |
| QI-Core IG package (JSON) | Profile 驗證 | Phase 3 |

---

## 驗收標準

### MVP (Phase 0-4 完成後)
- [ ] 從零開始建一個 proportion measure: metadata → CQL → populations → test cases → evaluate → export FHIR Bundle
- [ ] FHIR Bundle 匯入另一個環境, measure 完整重建
- [x] Library 共享: User A 建 library, User B include 使用
- [ ] Validation: 缺少必要 population → 顯示 error, 缺 expression → warning
- [ ] Test cases 全通過 → 可設定 Active

### Full Release (Phase 0-5 完成後)
- [ ] 匯出 HQMF XML → 通過 CMS schematron 驗證
- [ ] 評估結果 → 匯出 QRDA III XML → 通過 CMS QRDA III schematron
- [ ] 完整流程: Author → Test → Validate → Approve → Export → Submit

### 差異化 (Phase 6)
- [ ] Dashboard 顯示組織內所有 measures 的健康狀態
- [ ] Batch evaluation + batch QRDA III export
- [ ] Multi-rate measure 支持

---

## 競爭分析

| 功能 | MADiE | Trifolia | 我們 (完成後) |
|------|-------|----------|------------|
| CQL 編輯器 | Ace editor | CodeMirror | **Monaco** (最佳) |
| CQL Builder (視覺化) | ✅ 基本 | ❌ | **✅ 完整** (6 section) |
| Population Auto-map | ❌ | ❌ | **✅** (獨有) |
| Ratio Dual IP | ✅ | ❌ | **✅** |
| Stratifier Associations | ✅ | ❌ | **✅** |
| Test Case Editor | ✅ JSON only | ❌ | **✅ JSON + import** |
| Test Coverage | ❌ | ❌ | **✅** (獨有) |
| FHIR Bundle Export | ✅ | ✅ | ✅ |
| HQMF Export | ✅ | ✅ | ✅ (Phase 5) |
| QRDA III Export | ❌ (外部工具) | ✅ | ✅ (Phase 5) |
| Library 依賴圖 | ❌ | ❌ | **✅** (獨有) |
| Library 共享/權限 | ✅ 基本 | ❌ | **✅ 完整** |
| Validation Pipeline | ✅ 基本 | ✅ | **✅ 完整** |
| Measure Dashboard | ❌ | ❌ | **✅** (獨有) |
| 部署方式 | SaaS (CMS) | SaaS | **Self-hosted + SaaS** |
| 開源 | ✅ (分散73 repos) | ✅ | **✅ (單一 repo)** |

**核心優勢**: Monaco 編輯器、CQL Builder、Auto-map、Test Coverage、Library 依賴圖、Measure 審批工作流、Self-hosted 部署

---

## References

- [MADiE — eCQI Resource Center](https://ecqi.healthit.gov/tool/madie)
- [MADiE GitHub Organization (73 repos)](https://github.com/MeasureAuthoringTool)
- [2026 CMS QRDA III IG](https://ecqi.healthit.gov/sites/default/files/2026-CMS-QRDA-III-EC-IG-v1.1.pdf)
- [eCQM Standards Summary](https://ecqi.healthit.gov/standards-summary)
- [Quality Measure IG (HL7 FHIR)](http://hl7.org/fhir/us/cqfmeasures/using-cql.html)
- [2026 eCQM Specifications](https://ecqi.healthit.gov/updated-ecqm-specifications-and-implementation-resources-2026-reporting/performance-period)
- [CQL Library Service (MADiE)](https://github.com/MeasureAuthoringTool/cql-library-service)
- [CMS eCQM Library](https://www.cms.gov/regulations-and-guidance/legislation/ehrincentiveprograms/ecqm_library)
