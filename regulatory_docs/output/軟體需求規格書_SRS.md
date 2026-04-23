# 軟體需求規格書 (Software Requirements Specification)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-SRS-1.0.0 |
| 版本 | 1.0.0 |
| 日期 | 2026-04-23 |
| 產品名稱 | CQL Platform — 臨床品質語言視覺化編輯與執行平台 |
| 審核者 | _________________ |
| 核准者 | _________________ |

## 修訂歷史

| 版本 | 日期 | 變更說明 | 作者 |
|------|------|---------|------|
| 1.0.0 | 2026-04-23 | 自動產生 | 系統 |

---

## 1. 目的

本文件定義 CQL Platform 的軟體需求規格，依據 IEC 62304 醫療器材軟體生命週期流程標準。

## 2. 範圍

本文件涵蓋所有標記為 `IEC62304:需求` 的 GitHub Issues，版本 1.0.0。

## 3. 軟體需求列表


### SRS-001 [需求] 首頁頁尾加上商業合作 / 錯誤回報聯絡 Email

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#350](https://github.com/Lusnaker0730/CQL/issues/350) |
| 建立日期 | 2026-04-23 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

在 `LandingPage.tsx` 頁尾（copyright 字樣之上）加入一行聯絡方式，供訪客回報 bug 或洽詢商業合作。Email 使用 `aluminum001@gmail.com`（專案負責人）。

**臨床情境：**

本變動純屬靜態文字與 mailto 連結，無臨床資料流影響。提供使用者回饋管道，有助於快速接到錯誤回報、加速修復。

**驗收條件：**

- [x] 頁尾出現 mailto 連結
- [x] i18n key `footer.contactPrompt` 於 `landing` namespace（en + zh-TW）
- [x] `npx tsc --noEmit` 零 error
- [x] 樣式與頁尾既有元素協調（淡白色、hover 轉 primary）



---


### SRS-002 [需求] LibraryDefinitionPicker 全面測試覆蓋（PAT-103 follow-up）

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#347](https://github.com/Lusnaker0730/CQL/issues/347) |
| 建立日期 | 2026-04-23 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

PAT-103 shipped 三個 follow-up 測試，本 PR 全部完成：

1. **RTL for ArtifactWorkspace picker integration** — 避免需要 mock 整個 ArtifactWorkspace 的做法是抽出 `useArtifactLibraryPicker` hook，以 isolation 方式單測 state + dispatch 邏輯。
2. **Smoke scenario for picker flow** — 新 scenario type `authoring-cql`、POST ArtifactRequest → 呼叫 generate-CQL endpoint → 斷言 CQL 內含 include 與 alias.defName。
3. **LibraryDefinitionPicker component tests** — `generateAlias` pure function（export 出來）+ dialog 的兩階段流程（library 選擇 / definition 選擇 / alias 驗證 / confirm / back）。

**臨床情境：**

本 PR 純增加測試，不改臨床行為。降低 picker 相關 regression 的可能性：
- Hook 測試鎖住「include / exclude 不交叉分派」的契約
- Smoke scenario 鎖住 Jackson → DTO → builder → CQL 的 end-to-end 路徑
- 元件測試確保 `generateAlias` 的 alias 生成規則穩定、dialog 互動不 regress

**驗收條件：**

- [x] 新 `useArtifactLibraryPicker` hook + 8 tests（isolation 測試 picker state/dispatch）
- [x] `ArtifactWorkspace.tsx` 重構使用 hook（減 inline state 15 行）
- [x] 新 smoke scenario 22 `22-authoring-library-reference`、新 type `authoring-cql`、新 helpers `generate-authoring-cql.sh` + `assert-authoring-cql.sh`
- [x] `generateAlias` 從 internal function 改 export
- [x] 新 `LibraryDefinitionPicker.test.tsx` 11 tests（5 pure + 6 dialog）
- [x] README 更新含 scenario 22 條目 + `authoring-cql` 檔案格式說明
- [x] TSC --noEmit 零 error
- [x] Hook tests 8/8 pass 本機
- [x] Full smoke 20/20 pass 本機
- [ ] Picker dialog tests 本機因 Windows EMFILE 執行不完，Linux CI 會跑（預期 11/11 pass）
- [x] 全量 backend test 不受影響（無 backend 改動）



---


### SRS-003 [需求] CDS authoring 加入 LibraryDefinitionPicker UI

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#344](https://github.com/Lusnaker0730/CQL/issues/344) |
| 建立日期 | 2026-04-23 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

PAT-102 整合測試已鎖住 CDS authoring 後端接受 `externalCqlElement` 樹節點並產出正確 include 陳述式。本需求補上前端缺口：將 eCQM 使用的 `LibraryDefinitionPicker` 引入 CDS `ArtifactWorkspace`，讓 author 可在 Inclusion / Exclusion 分頁插入 CQL 程式庫定義引用。

**設計決策**：
- 共用元素構造：新 `utils/libraryReference.ts` 集中 `LibraryDefinitionReference → ElementInstance` 的 factory，eCQM 同步重構使用（避免未來兩條路徑的元素 shape 漂移）
- 插入點：Inclusion / Exclusion 分頁右上角「引用程式庫定義」按鈕，點擊開 picker dialog；選定後以 `externalCqlElement` 節點追加到對應 tree 的 `childInstances`
- 重複使用 eCQM 的 picker 元件（不做 CDS 專屬版本）

**臨床情境：**

CDS hook 作者需要引用共用臨床定義（抽菸史、糖尿病診斷、BMI 計算）。目前 CDS authoring 只能透過 External CQL 上傳（ExternalCql.tsx）但無法在 builder tree 插入特定 def reference。eCQM 已有此功能，規則撰寫經驗不一致。

**驗收條件：**

- [x] 新 `utils/libraryReference.ts`：`libraryReferenceToElement(ref): ElementInstance` factory
- [x] `components/authoring/ArtifactWorkspace.tsx`：加 `libPickerTarget` state、Inclusion/Exclusion 分頁加「引用程式庫定義」按鈕、render `<LibraryDefinitionPicker>` dialog
- [x] `components/ecqm/EcqmPopulationTreeEditor.tsx` 改用共用 factory（原 inline 構造刪除）
- [x] i18n key `workspace.useLibraryDefinition`（en + zh-TW）
- [x] 新 `libraryReference.test.ts`：7 項單元測試（type=externalCqlElement / returnType=boolean / name 格式 / 四個 field id / static flag / value 保留 / uniqueId 唯一 / modifiers=[]）
- [x] `npx tsc --noEmit` 零 error
- [x] 新 vitest 7/7 pass
- [ ] 人工 UI 驗證：進 CDS workspace Inclusion 分頁 → 點按鈕 → picker 開啟 → 選定後 tree 出現新節點 → 按 Review CQL → 產出的 CQL 含 `include X version 'Y' called alias` 與 `"alias"."DefName"` 引用（留 PR 後使用者 e2e 驗）



---


### SRS-004 [需求] CDS authoring 支援 CQL 程式庫引用 — 整合測試

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#342](https://github.com/Lusnaker0730/CQL/issues/342) |
| 建立日期 | 2026-04-23 |
| 狀態 | open |
| 風險等級 | 低 (Low) — 測試新增不改 production 行為 |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

確認並驗證 CDS authoring 的後端管道支援 CQL 程式庫引用（`include Library version 'X.Y.Z' called alias`），並透過整合測試鎖住此 contract，作為後續前端 UI 啟用的基礎。

**發現**：後端其實**已支援**——`ExpressionCqlEngine.collectDeclarations()` 已處理 `externalCqlRef` 與 `externalCqlElement` 兩種樹節點，並動態產生 `include` 陳述式；`CqlArtifactBuilder` 在 `buildCql()` 中對 inclusion tree + exclusion tree + baseElements + subpopulations 全部呼叫 `collectDeclarations()`。`CqlArtifactBuilderEdgeCaseTest.externalCqlInBaseElements_shouldAddInclude` 已單元測試此行為。

**缺口**：
1. 從「save artifact → JPA 序列化/反序列化 → generateCql 產生 include」的整條 pipeline 沒有整合測試保險。若 `CdsArtifactEntity.serializeAll()` / `deserializeAll()` 未來回歸（filter unknown node type、drop nested fields、LinkedHashMap 順序漂移），單元測試不會紅但使用者會在前端看到 include 消失。
2. 前端：CDS `ArtifactWorkspace` 沒有 `LibraryDefinitionPicker`（eCQM 有），author 無法在 UI 上插入 library reference 節點。本需求**不含**此項，列為 follow-up PR。

**臨床情境：**

台灣臨床流程常需引用共用定義（抽菸史、糖尿病診斷、藥物過敏清單等）。如果每個 CDS hook 各自重複寫一遍，改動要散落多處 → 規則維運困難 + 版本不一致風險（hook A 更新定義但 hook B 沒跟）。程式庫引用是規則共用最小可行路徑。

**驗收條件：**

- [x] 新 `CqlGenerationServiceLibraryIntegrationTest`（@SpringBootTest + H2）
- [x] 5 個 test case 覆蓋：
  1. `externalCqlRef` 在 baseElements + 經 JPA save/load → `include` 正確出現
  2. `externalCqlElement` 在 inclusion tree + 經 JPA save/load → `include` + `"alias"."DefName"` 引用
  3. 多個 references 指向同一 library → 只產出一條 include（dedup）
  4. 同一 artifact 重複呼叫 `generateCql` → CQL byte-identical（deterministic）
  5. `generateCqlWithWarnings` 路徑（供 CQL Builder Export 用）也帶 include
- [x] 全量 backend test BUILD SUCCESS
- [x] 單元層既有測試（`CqlArtifactBuilderEdgeCaseTest`）未 regression
- [ ] 前端 `LibraryDefinitionPicker` port 到 CDS `ArtifactWorkspace` — **follow-up PR**（UI 設計 + i18n + 前端測試，scope 分離）



---


### SRS-005 [需求] Smoke 擴充：BUG-116 + PAT-095 + PAT-098 contract locks

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#337](https://github.com/Lusnaker0730/CQL/issues/337) |
| 建立日期 | 2026-04-22 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

為 3 個近期修改的 backend contract 加上 smoke harness 層級的 regression lock，確保未來重構時第一時間被抓到：

1. **BUG-116 retrieve dedupe**（新 scenario 17）：CDS hook debug mode 下、多個 expression 引用同一個 `[Observation]` retrieve，應該只產生 1 筆 retrieve trace 而非 N 筆
2. **PAT-095 provenance**（擴充 `assert.sh` + scenario 01）：eCQM 評估完成後，`measure_report` 應記錄 `cqlHash` / `elmHash` / `measureVersion` 三欄非空
3. **PAT-098 editor errorInfo**（新 scenario 20）：`/api/cql/execute` 壞 CQL → 500 + `ErrorResponse.errorInfo.phase = "cql_translation"`

建置過程發現兩個**真產品 bug**（非 smoke bug），附帶修復：
- **HAPI CQL engine 不 cache define 結果跨 expression references**：單次 `engine.evaluate()` call 內同一個 `[Observation]` 被 retrieve N 次（BUG-116 batch eval 先前修 engine.evaluate() 呼叫次數但這層沒解決）→ 修 `TracingRetrieveProvider` 加 per-instance 結果 memoization cache（key = 所有 retrieve 參數的穩定簽名）
- **EcqmPublishService 丟棄 translator 產出的 ELM**：`validateCql()` 回傳含 `elmJson` 的 translation response，但 publish 流程只讀 `cqlContent`、從未呼叫 `setElmJson()` → 所有 eCQM publish 都留 `elm_json = null` → PAT-095 provenance 的 `elmHash` 永遠空 → 修 publish 把 `validation.getElmJson()` 存到 `measureDef.setElmJson()`

**臨床情境：**

兩個附帶修復各自有 audit 影響：
- Retrieve dedupe：author 在 debug mode 看到 10 筆 Observation 以為 hook 邏輯有問題，實際是 HAPI + TracingProvider 沒 cache 重複呼叫。Debug UX 誤導 + 真實 production FHIR server N× 負載。
- ELM persist：PAT-095 設計本意是 TFDA audit 可用 `elmHash` 比較「兩份報告跑的是不是語意等價的 measure」；但 `elm_hash` 一直空等於這條路沒打通。

**驗收條件：**

- [x] Scenario 17 `17-cds-retrieve-cache-dedupe`：4 個 defines 引用 `[Observation]`、debugMode=true → `retrieveTracesCount==1`
- [x] `assert.sh` 新 `checkProvenance: true` flag，scenario 01 開啟；follow-up GET `/api/measures/{id}/reports` 驗 `cqlHash` / `elmHash` / `measureVersion` 都非 null
- [x] Scenario 20 `20-cql-execute-error-info`：壞 CQL → HTTP 500 + `errorInfo.phase == "cql_translation"` + `phase` / `errorType` / `message` 非 null
- [x] `TracingRetrieveProvider` 加 per-instance 結果 cache（同 request scope 安全）
- [x] `EcqmPublishService.publish` 儲存 `validation.getElmJson()` 到 measure def
- [x] Smoke 19/19 綠（原 17 + 新 2）
- [x] 全量 backend 1300+ tests 綠
- [x] README 更新



---


### SRS-006 [需求] Debug mode 統一為 batch eval — 消除重複 FHIR 回撈

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#332](https://github.com/Lusnaker0730/CQL/issues/332) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 風險等級 | 中 (Medium) — 影響 debug 診斷正確性，但不影響 production eval |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

現況：CQL debug mode（`POST /api/cql/execute` 帶 `debugMode=true` 與 CDS hook invocation 帶 `debugMode=true`）為了取得每個 expression 的個別 timing，對每個 expression 呼叫一次 `engine.evaluate()`。HAPI CQL engine 不會在獨立 `evaluate()` call 之間保留 retrieve cache → 每個引用到同一個 `[Observation: valueset]` 的 expression 都會重新發一次 FHIR search。

實測使用者影響：BMI CDS hook 4 個 expressions 都引用 `Body mass index Observations`，debug panel 出現 **10 筆 Observation retrieve row**、origin FHIR server 承受 **N× 於 production 的流量**。Author 看了以為 hook 真的在轟 FHIR server，同一個 retrieve 被列 N 次也無法快速找到真正的 retrieve 是哪個。

需求：debug mode 改走 batch eval（和 production 路徑一致），讓 retrieve cache 生效、debug 行為忠實反映 production 行為。

**臨床情境：**

1. Author 寫 CDS 規則時反覆用 debug mode 迭代 → 每次都把 FHIR server 多轟 N 次 → 測試環境假負載升高、origin server rate limit 可能誤觸發
2. Author 誤以為自己寫的 CQL 有 N 次 retrieve 要想辦法合併 → 去亂改 CQL 結構想 "優化"，反而把原本就正常的邏輯改壞
3. Production 的 retrieve 數量是正確的（normal mode batch eval 有 cache），但 debug-driven 調試決策誤導 production 設計

**驗收條件：**

- [x] `CqlExecutionService.doExecute` 移除 debug-mode 專用的 per-expression loop（L387~454）
- [x] Debug 跟 normal 走同一 batch eval + per-expression fallback 路徑
- [x] 沿用 fallback 路徑用 finally 區塊收集 per-expression timing，batch 成功時 timing=0 並記註解說明
- [x] 後置的 expression trace 建構由 `results` map + `sourceLocators` + `expressionDependencies` 組裝，批次與 fallback 共用
- [x] 全量 backend 1300+ tests BUILD SUCCESS（含 `CqlExecutionIntegrationTest.4. Debug Mode`）
- [x] Smoke harness 17/17 pass（含 scenario 16 `16-cql-execute-debug-trace`，驗 expressionTraces 欄位完整）



---


### SRS-007 [需求] PHI 欄位加密 Phase 1 — 4 entity、8 個高風險欄位

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#327](https://github.com/Lusnaker0730/CQL/issues/327) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 風險等級 | 中 (Medium) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

Pre-launch review Tier 1 blocker #4 Phase 1：把 8 個 PHI-carrying 欄位套 `@Convert(EncryptionConverter.class)`，讓 DB at-rest 不再儲存明文 PHI。Existing `EncryptionConverter` 已實作 AES-GCM + PBKDF2 + `ENC:` prefix + legacy plaintext 讀取 fallback，本需求僅是 widen 套用範圍。

覆蓋欄位：
1. `MeasureReportEntity.result_json` — 整個 measure 評估結果（含 subject IDs、populations、scores）
2. `MeasureReportPopulationEntity.subject_ids_json` — normalized populations 子表的 patient IDs JSON array
3. `PatientImportEntity.patient_fhir_id` / `patient_identifier` / `patient_name` / `bundle_json` — 匯入的 FHIR bundle + 三個病人識別欄位
4. `SandboxPresetEntity.patient_id` / `prefetch_json` — CDS sandbox 的 patient 測試 context

跳過（留 Phase 2）：`AuditLogEntity`（threat model 不同，需另討論）、`BatchImportJobEntity.patientIds`、`FailedImportEntity.patientFhirId`。

**臨床情境：**

DB dump / backup 外洩 → PHI 外洩。目前 `measure_report.result_json` 和 `patient_import.bundle_json` 都是 TEXT 明文，包含 patient IDs、診斷、檢驗值等完整臨床資訊。真要接真 EHR 之前這條 at-rest 防線必須建立。FHIR browser / measure 報告 UI / sandbox 沙盒的 UX 完全不影響（JPA 邊界透明加解密，只有運維直連 DB 做 raw SQL 看到 ENC: 亂碼）。

**驗收條件：**

- [x] 8 個欄位在 entity 層加 `@Convert(converter = EncryptionConverter.class)`
- [x] Flyway V55 migration 把 3 個 VARCHAR 欄位（`patient_import.{patient_fhir_id, patient_identifier, patient_name}`、`sandbox_preset.patient_id`）擴充為 TEXT（ciphertext ~1.5× 原 size 超過原 VARCHAR 限制）
- [x] Rollback V55 對應
- [x] `EncryptionConverterTest` 新增 `roundTrip_largeJsonPayload_100kb`（result_json 實測可能 100KB+）+ `roundTrip_utf8MultibytePayload`（中文 patient name / CQL 註解），全 13 tests pass
- [x] 新 `PhiEncryptionIntegrationTest` 4 tests 雙重驗證 contract：(a) app 層讀 plaintext / (b) raw JDBC 讀 ciphertext。包含 legacy-plaintext-fallback 測試
- [x] 全量 backend suite BUILD SUCCESS（原 1000+ 個 test + 6 新 test = 全綠）
- [x] 既有 repo 查詢經 review 確認無 `WHERE phi_field = ?` 相等性搜尋（只有非 PHI 欄位如 connectionId、ownerUsername、measureDefinitionId）
- [x] Encrypt-on-read fallback 確保既有明文 row 保持可讀 → zero-downtime 部署
- [x] CHANGE_LOG 條目 + TFDA 追溯 issue



---


### SRS-008 [需求] 前端 debug panel 共用化對齊後端 PAT-098

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#325](https://github.com/Lusnaker0730/CQL/issues/325) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

PAT-098 已在後端抽出共用 `ExecutionErrorInfo` + 分類器。前端 `DebugPanel`（編輯器）和 `CdsDebugPanel`（CDS sandbox）仍重複實作 expression trace table、retrieve trace table、ELM JSON viewer、以及錯誤 alert（後者 CDS 專屬、編輯器完全沒有）。此需求把這 4 個共用元件抽出到 `components/debug/`，讓兩個 panel 變成薄 orchestrator；同時把 `CdsErrorInfo` type 重新命名為 `ExecutionErrorInfo` 與後端命名一致。

**臨床情境：**

UI UX 一致性：同一個錯誤在兩個 panel 顯示風格應該一樣；維護成本：trace table 邏輯兩處就錯不同步。

**驗收條件：**

- [x] 新 `components/debug/` 目錄含 4 個共用元件（`ExpressionTraceTable`、`RetrieveTraceTable`、`ElmJsonViewer`、`ExecutionErrorAlert`）
- [x] `DebugPanel.tsx` 從 247 行縮到 ~30 行，僅 orchestrate shared tables
- [x] `CdsDebugPanel.tsx` 仍保有 CDS 專屬 section（prefetch / FHIR diagnostics / invocation context / dryRun），錯誤 alert 改用共用 `ExecutionErrorAlert`
- [x] `CdsErrorInfo` type 改名為 `ExecutionErrorInfo`，留 `type CdsErrorInfo = ExecutionErrorInfo` 作為 deprecated alias
- [x] 共用 debug 文字從 `editor.debug.*` / `cds.debug.*` 搬到 `common.debug.*`（含兩新 phase：fhir_retrieval、population_evaluation）
- [x] Phase→severity 映射加入 `card_generation=error` + `fhir_retrieval=warning` + `population_evaluation=warning`，解決原 CDS 專用映射未覆蓋新 phase 的 gap
- [x] 新 `ExecutionErrorAlert.test.tsx` 驗 4 個 case（fields / no stack / with stack / unknown phase）
- [x] `npx tsc --noEmit` 零 error
- [x] 前端本機 vitest 因 Windows EMFILE (@mui/icons-material) 環境限制無法全綠執行；CI 在 Linux 上會跑



---


### SRS-009 [需求] 統一三個 debug 模式的錯誤分類模型

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#320](https://github.com/Lusnaker0730/CQL/issues/320) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

將 CDS invocation、CQL editor execute、eCQM measure evaluation 三個 debug 模式的錯誤表達方式統一為共用 `ExecutionErrorInfo` 結構（phase + errorType + message + stackTraceSummary）。

現狀：
- CDS（`/cds-services/{id}` debugMode）：有 `debug.error` 含 phase 分類（BUG-115 已修）
- Editor（`/api/cql/execute`）：只有 flat `errors: List<String>`，無 phase
- eCQM（`/$evaluate-measure`）：只有 `errorMessage: String`，無 phase

同一個 `CqlExecutionService` 錯誤在三個 flow 長三個樣，EHR 整合商 / measure author / CQL author 拿到完全不同的 debug 體驗，phase 分類 heuristic 也只有 CDS 會走到。

**臨床情境：**

TW CDI 對接案在 sandbox 時，同一個「CQL 呼叫 undefined function」的錯誤，對接商可能從三個 endpoint 各碰到一次（editor 試寫 → eCQM evaluate → CDS hook）。三個 endpoint 回三種格式 → 排障經驗不一致、每次都要重新理解 error shape。臨床直接 impact 小但對接 velocity 影響大、也會放大 BUG-115 類型的誤分類問題（只有 CDS 有 phase，editor/eCQM 連 wrong phase 都沒有）。

**驗收條件：**

- [x] 新 `com.cqlplatform.model.debug.ErrorPhase` enum（涵蓋 CDS 原有 6 個 phase + `FHIR_RETRIEVAL` + `POPULATION_EVALUATION`）
- [x] 新 `com.cqlplatform.model.debug.ExecutionErrorInfo`（phase/errorType/message/stackTraceSummary，wire JSON 與原 `CdsResponse.CdsErrorInfo` 相同 → CDS 客戶零變動）
- [x] 新 `com.cqlplatform.util.ExecutionErrorClassifier`：`classify()` / `buildErrorInfo()` / `fromCdsPhase()` — 所有三個 flow 共用
- [x] CDS flow：`CdsInvocationService` 刪掉 inline heuristic、delegate to classifier；`CdsResponse.CdsDebugInfo.error` 改為 `ExecutionErrorInfo`
- [x] Editor flow：`CqlExecutionResponse.errorInfo` 新欄位；`GlobalExceptionHandler.ErrorResponse.errorInfo` 新欄位，`handleCqlExecutionException` 用 classifier 填
- [x] eCQM flow：`MeasureEvaluationResult.errorInfo` 新欄位；`MeasureEvaluationService.errorResult` 可選傳 `Throwable` 建構
- [x] 全 back-compat：原 `errors`/`errorMessage` 欄位保留不動
- [x] 新 `ExecutionErrorClassifierTest` 至少 12 tests（null / 純 runtime / 類別名 / 包裝訊息 / nested cause / cause loop 防護 / buildErrorInfo 三種 signature / 堆疊過濾 / legacy phase 對映）
- [x] `CdsInvocationServiceTest` 既有 10 tests regression green
- [x] 全量 backend + smoke 全綠



---


### SRS-010 [需求] CDS error phase 分類要正確識別 wrapped translator errors

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#315](https://github.com/Lusnaker0730/CQL/issues/315) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

當 CDS hook 執行時 CQL 翻譯失敗，`/cds-services/{id}` 的 debug response 必須把 `debug.error.phase` 正確標記為 `cql_translation`（不是 `cql_execution`），讓 EHR 對接商能立即判斷這是 author 寫壞 CQL 語法、而不是運行時 FHIR 資料問題。

現狀（PAT-097 scenario 18 實測證實）：`CdsInvocationService.looksLikeTranslationError()` 只檢查最外層 exception 的 simple class name。`CqlExecutionService` 把翻譯錯誤 wrap 成 `CqlExecutionException`（message 長這樣：`Execution failed: CQL translation failed with N error(s): ...`），simple name 不含 Translation/Parse/Syntax → heuristic 漏判 → phase 錯標成 `cql_execution`。

**臨床情境：**

Author 在 CDS sandbox 試寫一個新 hook，不小心打錯函式名。前端 debug panel 顯示 `phase: cql_execution` → author 以為是 patient 資料有問題、去翻 prefetch 設定 / FHIR server log 浪費 20-60 分鐘。正確應該是 `phase: cql_translation` → author 立刻知道回到編輯器修語法即可。Non safety-critical 但是重要的 authoring UX。對 EHR 整合商做外部對接時 impact 更大（他們沒有 CQL 開發經驗，phase 標錯會被帶到錯誤方向跑。

**驗收條件：**

- [x] CQL 呼叫 undefined function 時，phase = `cql_translation`
- [x] 真的運行時錯誤（除以零、null 解引用）phase 仍 = `cql_execution`（不能 over-fix 把所有錯都歸 translation）
- [x] Heuristic walk cause chain（最多 10 層防迴圈）+ 檢查 message 內容含 `"CQL translation failed"` / `"translation error"` / `"parse error"`
- [x] class name 擴充辨識 `Compiler` / `Lexer`（除了原本 Translation/Parse/Syntax）
- [x] 新 unit tests 至少 2 個：(a) wrapped RuntimeException with translation message → cql_translation、(b) nested cause with `*Compiler*` simple name → cql_translation
- [x] scenario 18 的 `debugErrorPhase` expected value 從 `cql_execution` 改回正確的 `cql_translation`
- [x] 全量 backend test + smoke 通過、原有純 runtime error 的 test（`RuntimeException("boom")` → cql_execution）保持綠



---


### SRS-011 [需求] Smoke harness 補覆蓋 CQL execute + CDS 錯誤路徑 debug 模式

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#313](https://github.com/Lusnaker0730/CQL/issues/313) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

現有 smoke harness（PAT-082/087/091）只覆蓋 eCQM + CDS 正向路徑。三個尚未驗證的 debug endpoints：(a) `POST /api/cql/execute` 帶 `debugMode=true` → `debugTrace.expressionTraces[]` 欄位完整性、(b) CDS hook 錯誤路徑帶 `debugMode=true` → `debug.error` 結構化（phase/errorType/message）而非裸 5xx、(c) test-case run 的 populationTrace（延後）。

本需求聚焦 (a) + (b)。scenario 16 做 CQL execute debug、scenario 17 reserved for CDS positive debug（follow-up）、scenario 18 做 CDS error-path debug、scenario 19 reserved for test-case debug（follow-up）。

Assertion 原則：只驗欄位存在與型別（name/resultType 非空、evaluationTimeMs 是數字、elmJson 非空字串），不鎖具體值——debug trace 是 diagnostic UX，schema 會隨 authoring UX 成熟演進，過度 lock 會讓未來 UI 重構變成 smoke churn。

**臨床情境：**

Debug mode 輸出是 EHR 整合商 troubleshoot 工具。若 contract silent 壞掉（例如 `debugTrace` 或 `debug.error` 格式變動 / null 化）user-facing 功能還是綠燈，但對接廠商拿到 bare stack trace / 空 object 無法自助排障 → 工單暴量 / 對接時程延誤。CQL 翻譯錯誤在執行時被 rewrap 成 `CqlExecutionException` 而非 `CqlTranslationException` 的 phase 分類 quirk 也在這裡曝光，可追蹤為 follow-up。

**驗收條件：**

- [x] Scenario 16 `16-cql-execute-debug-trace`：POST `/api/cql/execute` 帶 debugMode=true，斷言 `debugTrace.expressionTraces|length >= 3` + 每 entry `name`/`resultType`/`evaluationTimeMs`/`order` 非 null + `debugTrace.elmJson` 非空 + `debugTrace.totalTimeMs` 為 number
- [x] Scenario 18 `18-cds-error-debug-trace`：CDS service CQL 故意呼叫 undefined function，invoke 帶 debugMode=true 後回 HTTP 200（非 5xx），斷言 `debug.error.phase` 等於 `cql_execution`（classification quirk — 註解中記錄）+ `phase`/`errorType`/`message` 全部非 null
- [x] 新 scenario type `cql-execute` 在 `run.sh` 被 dispatch，`request.json` 格式 fed 到 `/api/cql/execute`
- [x] 新 lib helper `execute-cql.sh` + `assert-cql-debug.sh` 依專案 bash style（`set -euo pipefail`、stdout response、stderr 錯誤）
- [x] 既有 `assert-cds.sh` 擴充 `debugErrorPhase` + `debugErrorRequiredFields` 雙斷言，不破壞 15 既有 scenarios
- [x] `scripts/smoke/README.md` 表格更新，包含 scenario 16/18 條目 + 新 scenario 類型說明（`cql-execute` 檔案格式）
- [x] 17 scenarios 冷 cache 一次跑完全綠



---


### SRS-012 [需求] TLS 強制於 twcql.com — Client↔Cloudflare↔Origin 全程 HTTPS

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#308](https://github.com/Lusnaker0730/CQL/issues/308) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 風險等級 | 中 (Medium) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

twcql.com 對外的所有 HTTP 流量必須強制走 HTTPS（TLS 1.2+）。具體範圍：(a) 瀏覽器 ↔ Cloudflare 段已由 Cloudflare 保障；(b) Cloudflare ↔ VM origin 段原本走 :80 明文，本需求要把這段也加密；(c) 用戶直接 curl / openssl 連 origin 時能取得有效 TLS handshake；(d) 任何 :80 的請求一律 301 redirect 到 https。瀏覽器必須收到 HSTS header，瀏覽器內部自動把 http URL rewrite 成 https。

**臨床情境：**

將來接真 EHR 時，Bearer token、病人識別碼、觀察值會在 Cloudflare↔origin 這段流動。如果 Cloudflare SSL mode 維持 Flexible（僅瀏覽器↔CF 加密），任何能坐在 CF 與 origin VM 之間的網路節點都可旁錄明文 PHI 與有效 auth token。TLS 強制是任何臨床系統外部接入前的硬性前提。

**驗收條件：**

- [ ] `curl -sI http://twcql.com/` 回 `301 Moved Permanently` + `Location: https://...`
- [ ] `curl -sI https://twcql.com/` 回 `200 OK` + `Strict-Transport-Security` header（至少 6 個月 max-age）
- [ ] origin 直接用 `openssl s_client -connect 127.0.0.1:443` 取得有效 Cloudflare Origin CA cert、有效期 >= 1 年
- [ ] nginx 只接受 TLS 1.2 / 1.3，不接受 TLS 1.0 / 1.1
- [ ] 靜態安全 header 齊備：`X-Content-Type-Options: nosniff`、`X-Frame-Options: SAMEORIGIN`
- [ ] canonical nginx config 以 source-controlled 檔案存在（`docker/nginx-vm/twcql.com.conf`）



---


### SRS-013 [需求] Measure report 綁 measure_version + CQL/ELM hash 以支援 forensic reproducibility (#PAT-095)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#303](https://github.com/Lusnaker0730/CQL/issues/303) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 風險等級 | Medium（schema 變動、save path 加依賴；migration 完全 additive、既有 row 不動） |
| 安全性等級 | B（影響臨床 measure 結果的 forensic reproducibility + TFDA compliance） |

**需求描述：**

目前 `MeasureReportEntity` 只記 `measure_definition_id` 不記版本、不記 CQL/ELM 內容 hash。Measure 改版或刪除後，歷史 report **無法回溯是用哪版 CQL 跑出來的**。TFDA / CMS 稽核的核心問題「2026-Q1 的 report X 基於哪個 version 的 measure Y、content 是 Z」答不出來。Pre-launch review 列為 🔴 Critical（Tier 1 blocker）。

**臨床情境：**

臨床品質 measure 一年會多次 refine（error fix、定義 tighten、scoring 調整）。當 regulator 回頭驗證既有 report 是否基於當時 approved version 計算時，平台必須能提供：
1. 產出該 report 時 measure 是哪個 version 字串（`1.2.0` 之類）
2. 當時實際執行的 CQL bytes 是什麼（forensic hash）
3. 當時 translator 產出的 ELM bytes 是什麼（語意版本）

三者合起來才能證明「這個 report 是 reproducible、tamper-evident、與當時 approved content 一致」。

**驗收條件：**

- [ ] Flyway V54 加 `measure_version VARCHAR(50)` / `cql_hash VARCHAR(64)` / `elm_hash VARCHAR(64)` 到 `measure_report`，rollback 對應；所有 nullable（既有 row 不能回填）
- [ ] Covering index `idx_measure_report_definition_version` 於 `(measure_definition_id, measure_version)` 支援「all reports for measure X at version v」audit 查詢
- [ ] `MeasureReportEntity` 對應 3 個 fields with javadoc 說明 provenance 語意
- [ ] `ContentHash.sha256Hex(String)` 工具：null→null、empty→well-defined constant、byte-level stable、UTF-8 safe
- [ ] `MeasureReportService.saveReport` 在 save 時 fetch definition、snapshot version + hash、best-effort（definition 已刪除時仍 save，provenance 欄位 null）
- [ ] 單元測試：ContentHash vectors + saveReport 的 happy path / missing definition / null cql_content / null measureDefinitionId
- [ ] 全量 regression pass



---


### SRS-014 [需求] Password lockout — 暴力破解保護 (#PAT-094)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#298](https://github.com/Lusnaker0730/CQL/issues/298) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 風險等級 | Low-Medium（新增行為、既有使用者零影響；logic error 最糟 → false lockout、admin 可手動 unlock 化解） |
| 安全性等級 | B（認證路徑 — 影響存取控制 + 病人資料保護） |

**需求描述：**

現況 `app_user` 沒任何 failed-login 追蹤，也沒帳戶鎖定機制。`AuthController.login` 只 catch `BadCredentialsException` 回 401，攻擊者可以對 username 清單無限制嘗試密碼（自動化每秒數百次）。這是基本的 security baseline 缺口，pre-launch review 列為 🟠 High（Tier 2 內必修）。

**臨床情境：**

醫療系統登入帳號是高價值目標（可看病人資料、下診療建議）。暴力破解 / password spraying 攻擊會：
- 接管 account → 看 PHI
- 偽造 measure definition → 影響 CDS alerts
- 在 audit log 留下「合法使用者操作」但其實是攻擊者

**驗收條件：**

- [ ] `app_user` 新 `failed_login_attempts INTEGER` + `lockout_until TIMESTAMP` 欄位（Flyway V53 + rollback）
- [ ] `UserEntity` 對應 fields
- [ ] `CustomUserDetailsService` 讀 `lockout_until`：未來時間 → `accountNonLocked=false` → Spring Security 丟 `LockedException`（password 比對前短路）
- [ ] `LoginAttemptListener` 聽 Spring Security 的 `AuthenticationSuccessEvent` / `AbstractAuthenticationFailureEvent`：失敗累加、成功清零、達 threshold 設 lockout
- [ ] `AuthController.login` catch `LockedException` → HTTP 423 + 明確訊息
- [ ] `POST /api/admin/users/{id}/unlock` admin 端點清 counter + lockout
- [ ] 可配置 threshold（`app.security.lockout.max-attempts`，預設 5）+ duration（`duration-minutes`，預設 30）
- [ ] 單元測試：counter increment / lockout trigger / clear on success / unknown user no-op / null username safe / locked user loadByUsername 回 accountNonLocked=false / 過期 lockout 視為 unlocked
- [ ] 全量 backend regression pass



---


### SRS-015 [需求] CDS / CQL authoring: deprecated-hook hint + CodeableConcept compare hint (#PAT-092)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#292](https://github.com/Lusnaker0730/CQL/issues/292) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 風險等級 | Low（錯誤訊息改動、不改拒絕行為） |
| 安全性等級 | B（影響 CDS authoring 使用者能否正確建立 measure；同 PAT-088/089 family 的 UX 主線 — 錯誤訊息的 discoverability） |

**需求描述：**

兩個 authoring UX 問題，PAT-091 smoke 建置時親身踩到：

1. **CQL translator 對 `CodeableConcept ~ 'string'` 的錯誤訊息不友善**：技術正確但使用者看不懂
   ```
   Could not resolve call to operator Equivalent with signature (FHIR.CodeableConcept, System.String).
   ```
   使用者真正想做的是比較 clinicalStatus 之類的 coded field、不知道要 extract coding[0].code。同樣問題也發生在 Coding vs String。

2. **`HookTypeValidator` 對 CDS Hooks 1.0 deprecated hooks 拒絕但沒提示替代**：舊教材 / 舊系統匯入時常寫 `medication-prescribe`，被擋下但訊息只列正確 hooks，不說 `medication-prescribe` 已被 `order-sign` 取代。

兩個都屬於「backend 技術正確但 UX 差」。修 error message 而非 behavior，最小侵入。

**臨床情境：**

（未填寫）

**驗收條件：**

- [ ] `CqlTranslationService.hintForError(String)` 新 static helper：偵測 `CodeableConcept vs String` / `Coding vs String` equivalent-call 錯誤，返回 human-readable hint；其他錯誤回 null（不過度 hint）
- [ ] `CqlError.message` append `"\n\nHint: ..."` 當 hint 非 null；原始 translator 訊息保留
- [ ] `HookTypeValidator` 新 `DEPRECATED_HOOKS` map（`medication-prescribe → order-sign`），validate 失敗時偵測是否為 deprecated，提示替代；純未知 hook 保持原 generic message
- [ ] 單元測試：CqlTranslationServiceHintTest（4 tests）、HookTypeValidatorTest 加 2 tests（deprecated hint + unknown fallback）
- [ ] 既有 hook validation / CQL translation 行為不變（只是 error message 更 helpful）



---


### SRS-016 [需求] Smoke harness 補齊 CDS Hook 整合覆蓋 — 6 scenarios (#PAT-091)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#290](https://github.com/Lusnaker0730/CQL/issues/290) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 風險等級 | Low（dev tooling 覆蓋擴充） |
| 安全性等級 | A（測試覆蓋改善；不影響 clinical correctness） |

**需求描述：**

Smoke harness 只蓋 eCQM 的 4 種 scoring type + 5 個 CV aggregate（共 9 scenarios），**CDS Hook 整合路徑完全沒測**。CDS 跟 eCQM 是完全不同的執行路徑：不需要 publish、invocation endpoint 不同、response 是 cards 不是 score。類別層級的 silent bug（hook context validation、card emission、dryRun 短路、disabled handling）在單元測試看不到，要走真 API 才能暴露。

**臨床情境：**

（未填寫）

**驗收條件：**

- [ ] Run.sh 加 scenario-type dispatch（`ecqm` 預設 / `cds-hook` 走 CDS 流程），既有 9 個 eCQM scenario 完全不動
- [ ] 新 lib helpers: `save-cds-service.sh`, `invoke-cds.sh`, `assert-cds.sh`
- [ ] `CDS_BASE` 環境變數 wire 到 SMOKE_BACKEND_PORT（CDS 在 `/cds-services` 非 `/api`）
- [ ] 6 個 CDS scenario 覆蓋：
    - `10-cds-patient-view-basic` — hardcoded Tuple → info card
    - `11-cds-patient-view-conditional` — exists([Condition]) 驗 prefetch-driven logic
    - `12-cds-order-sign` — 非 patient-view hook + draftOrders context
    - `13-cds-multi-card-indicators` — 3 defines 各自 info/warning/critical card
    - `14-cds-disabled-service-not-listed` — enabled:false 返 200 + "Service not found" card
    - `15-cds-dryrun-mode` — dryRun + debugMode、0 cards + prefetchStatus populated
- [ ] 全 15 scenarios 冷 cache 跑完 < 4 min
- [ ] README 更新 scenarios 表格 + CDS 檔案結構說明



---


### SRS-017 [需求] 修正 Google Search Console 14 個「已找到 - 目前尚未建立索引」(#PAT-090)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#288](https://github.com/Lusnaker0730/CQL/issues/288) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Very Low（純 SEO 優化，無 product behavior 改動） |
| 安全性等級 | A（SEO / dev experience；不影響 clinical correctness） |

**需求描述：**

Google Search Console 顯示 twcql.com 有 16 個 URLs 在 sitemap 中，僅 2 個被索引（`/` 和 `/learn`），**14 個卡在「已找到 - 目前尚未建立索引」(Discovered – currently not indexed)**：

- 13 × `/learn?tab=xxx` (advanced / cheatSheet / concepts / ecqmTutorial / examples / fhirpathElm / introduction / languageRef / playground / quickStart / quiz / troubleshooting / twcore)
- 1 × `/login`

「Discovered but not crawled」不是 canonical/duplicate 問題，是 Google 看到 sitemap URL 但**決定不抓**。原因組合：

1. **Query-param `?tab=xxx` URLs** — Google 視為 filter/sort 變體，非獨立資源，預設 deprioritize
2. **新網域、無 backlinks** — Google 保守分配 crawl budget
3. **SPA serve 同一 HTML** — 初步 fetch 看不出這些是「不同 page」
4. **`/login` 本來就不該被索引**（auth gateway）

**臨床情境：**

（未填寫）

**驗收條件：**

- [ ] `public/sitemap.xml` 縮到只剩 `/` 和 `/learn` 兩個真正獨立的 indexable page；拿掉 13 × `?tab=` 與 `/login`
- [ ] 新 `public/robots.txt` 宣告 sitemap 位置、default-allow（讓 crawlers 不需靠 GSC submit 就找得到 sitemap）
- [ ] `LandingPage` (路由 `/login` 用)加 `<meta name="robots" content="noindex, follow" />`，auth page 明確告訴 crawler 不要 index
- [ ] `npx tsc --noEmit` 通過、build 通過
- [ ] 部署後 1-2 週 GSC 的 "Discovered - not indexed" 降到 0



---


### SRS-018 [需求] aggregateMethod 於 save-time validation：早期拒絕 typo (#PAT-089)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#283](https://github.com/Lusnaker0730/CQL/issues/283) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Low（新增 save-time 驗證；既有合法 measure 不受影響；不合法的會更早被擋下） |
| 安全性等級 | B（延續 PAT-088 的 safety-critical 家族 — 早期擋下錯 aggregate 比 evaluation 才發現更安全） |

**需求描述：**

#PAT-088 把 unknown `aggregateMethod` 在 evaluation time 處理為 null + log warning（避免 silent fall-through 到 Average）。這是正確的 evaluation-layer 行為——不拒絕整個 measure evaluation，只對有問題的 aggregate 回 null。

但有更早的抓點：**save time**。當 author 在 /api/ecqm/artifacts POST 或 PUT 時，如果 `aggregateMethod` 是 typo（或不支援的值），應該**立刻拒絕**而非存進 DB 等 evaluation 時才發現。

好處：

- Author 馬上看到 400 validation error，修正前無法存檔
- DB 不累積「lookup invalid」的 measure
- 避免 author 存完、跑評估、看空白 score、問 backend team、查 log 才發現 typo 的 long debugging loop
- 對 TFDA 合規稽核友善（invalid measure 不能存表示 governance 嚴格）

**臨床情境：**

延續 #PAT-088 的風險：Min/Max 類型指標是 safety-critical。Author 以為寫 `"Max"` 成功存了，實務上跑 evaluation 會看到 null score，debug 半天才發現。Save-time validation 在 author 按下「儲存」的那一刻就回「aggregateMethod 'Max' ✗ typo check fail」（實際上 Max 是合法 alias，但如果打成 "Maxmum" 就立刻擋下）。

**驗收條件：**

- [ ] `EcqmExpressionTreeValidator.validate(request)` 新增 `validateAggregateMethods` 子檢查：iterate `populationGroups[*].observations[*].aggregateMethod`，透過 `MeasureScoreCalculator.normalizeAggregateMethod` 判斷
- [ ] Unknown methods → 加到 errors list、最終 throw ValidationException（跟既有其他 errors 合併）
- [ ] Null / blank aggregateMethod → 接受（維持「未指定 → average」語意，跟 evaluation layer 一致）
- [ ] 錯誤訊息包含路徑（`populationGroups[0].observations[1].aggregateMethod`）+ 輸入值 + 支援 methods 列表
- [ ] 支援所有 aliases（`Min`/`Max`/`Avg`/`Mean` 等）save time 接受
- [ ] 單元測試鎖住 canonical / aliases / unknowns / null-blank / missing observations / multi-observation error identification
- [ ] 全量 backend test + smoke 9/9 不受影響



---


### SRS-019 [需求] CV aggregateMethod 支援 Min/Max/Avg aliases + unknown 不再 silent fall-through (#PAT-088)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#278](https://github.com/Lusnaker0730/CQL/issues/278) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Medium（行為變動：typo 從「silent 回 Average」變成「null + 警告」。既有 consumer 若吞掉 null 會看到 blank score；但那就是目的——讓錯誤可見） |
| 安全性等級 | B（影響 clinical CV 指標 correctness，特別是 Min/Max 極值型安全監控。同 PAT-083/084/085 FHIR 合規家族） |

**需求描述：**

`MeasureScoreCalculator.calculateContinuousVariableScore` 對 `aggregateMethod` 做小寫 exact-match：

```java
return switch (aggregateMethod != null ? aggregateMethod.toLowerCase() : "average") {
    case "sum" -> ...
    case "median" -> ...
    case "minimum" -> ...
    case "maximum" -> ...
    case "count" -> ...
    default -> values.stream().mapToDouble(...).average()...  // ← silent fall-through
};
```

兩個問題：

1. **Abbreviation 不支援**：`aggregateMethod: "Min"` / `"Max"` / `"Avg"` 都合理的寫法，但 backend 靜默走 default 分支回傳**平均值**。使用者看到合理數字但其實是錯的 aggregate。這是 #PAT-087 smoke scenario 08/09 建立時親身踩到的 bug（expected 2.0 收到 6.0）。
2. **Typo 不可見**：`aggregateMethod: "Minumum"` 或任何打錯都 silently 回傳 Average。量測作者沒有任何信號知道 aggregate 被 miss-route。

Aggregate method 的 silent fall-through 對臨床指標尤其危險：同一份資料用 Max 應得極值但實際得到平均值，**臨床風險監控報表（最差血糖、最高心率、最長住院日）完全失真**。

**臨床情境：**

典型 CV Max use case：「本月最長住院日」、「最高血壓讀數」、「最差 HbA1c」。這些都是 safety-critical 極值指標。若 author 寫 `"Max"` 得到平均值：

- 報表顯示看似合理的中等數字
- 臨床團隊以為「狀況還 OK」
- 實際最差值被平均拉低、看不到個案
- 介入決策基於假訊號

**驗收條件：**

- [ ] 新 `normalizeAggregateMethod(String)` static helper：接受 aliases `min`→`minimum`, `max`→`maximum`, `avg`/`mean`→`average`（case-insensitive）
- [ ] 未知值回 `null`（不是 fallback 到 average）；null/blank 輸入保持預設為 `average`（維持「未指定」語意）
- [ ] `calculateContinuousVariableScore` 使用 normalizer；unknown → null score + `log.warn` 包含支援的 method 列表
- [ ] `computeObservationStats` 的 `.aggregateMethod` 顯示欄位也走 normalizer，response 一律用 canonical form
- [ ] Parametrized unit tests 鎖住：canonical forms、all aliases、case variants、null/blank、unknown
- [ ] Smoke scenarios 08/09 改用 `"Min"` / `"Max"` aliases 端到端驗證
- [ ] README 更新 aggregateMethod naming caveat（現在 aliases 支援、unknowns 不 silent 了）



---


### SRS-020 [需求] Smoke harness 補齊 CV aggregate methods 覆蓋 (Average/Sum/Median/Min/Max) + episode-based CV (#PAT-087)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#276](https://github.com/Lusnaker0730/CQL/issues/276) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Low（dev tooling 增補覆蓋） |
| 安全性等級 | A（測試覆蓋改善；不影響 clinical decision correctness） |

**需求描述：**

PAT-082 新增的 CV scenario 03 只覆蓋 `aggregateMethod=Count`，且是 patient-based CV（populationBasis=boolean）。剩下的 4 個 CV aggregate methods（Average / Sum / Median / Minimum / Maximum）與 episode-based CV（populationBasis=Encounter 等）完全沒測——這是主要 CV use case。

Backend 的 CV aggregate 實作在 `MeasureScoreCalculator.calculateContinuousVariableScore`，包含 5 個 switch case。只測 Count = 20% 覆蓋率。Episode-based CV 走不同的 `Measure Observation Values`（plural）codegen path（`("Measure Population") MP return "Measure Observation"(MP)`），跟 patient-based 的 `Measure Observation Value`（singular）不同分支。沒測 = 沒 guard。

同時在建立過程中發現兩個 operational 問題要一起修：
1. `save-and-publish.sh` 的 `curl -sf` 在 4xx / 5xx 時 silent fail，沒印 body；我吃了一個 `description: Input contains potentially unsafe content` validation error debug 了 5 分鐘才看到——改為不帶 -f、顯示 body
2. `reset-fhir.sh` 的 DELETE order 把 Patient 放第一個，當有 Encounter reference Patient 時 HAPI 因 referential integrity 拒絕（HTTP 409）——改為 reference-dependent order（Patient 最後）

**臨床情境：**

（未填寫）

**驗收條件：**

- [ ] `05-cv-avg-encounter-duration` scenario：episode-based CV with Average aggregate，score=6.0
- [ ] `06-cv-sum-encounter-duration` scenario：Sum，score=30.0
- [ ] `07-cv-median-encounter-duration` scenario：Median，score=6.0
- [ ] `08-cv-min-encounter-duration` scenario：Minimum（不是 Min），score=2.0
- [ ] `09-cv-max-encounter-duration` scenario：Maximum（不是 Max），score=10.0
- [ ] 所有 5 個 CV aggregate scenarios 共用同一 fixture design（5 encounters with durations 2/4/6/8/10 days）
- [ ] `save-and-publish.sh` 失敗時 surface HAPI response body（改 curl flag）
- [ ] `reset-fhir.sh` DELETE order referring resources first
- [ ] README 更新 scenarios 表格 + aggregateMethod naming 注意事項
- [ ] 全 9 scenarios 冷 cache 跑完 < 3 分鐘



---


### SRS-021 [需求] 自動回填 CHANGE_LOG commit hash（取代 2-commit pattern）(#PAT-086)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#274](https://github.com/Lusnaker0730/CQL/issues/274) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Very Low（dev tooling，純 workflow 改善） |
| 安全性等級 | A（開發流程改善，不影響 product behavior） |

**需求描述：**

`CLAUDE.md` 現行 commit 慣例要求每個 PR 做兩次 commit：

1. 主 commit：更新程式碼 + CHANGE_LOG.md（commit 欄位留空 `| |`）
2. 第二 commit：`docs: add commit hash to CHANGE_LOG #NNN` — push 完第一 commit 後把 hash 填回 CHANGE_LOG

實務痛點：

1. **忘記做第二 commit**：PR 常只 merge 第一 commit，main 的 CHANGE_LOG 累積空 commit 欄位（今天有 15+ 個 empty row）
2. **Merge conflict**：CHANGE_LOG 每筆新 row 都加在 table 開頭，多 PR 同時開時 conflict 爆炸（昨天 #258 跟其他 PR 合併時多次撞到這個）
3. **雜訊**：一個 PR 兩個 commit，git log 多出一半的 `docs: add commit hash` 無實質內容的 commit
4. **Hash stability**：如果用 squash-merge，hash 在 merge 後改變，第二 commit 填的 hash 無效（目前用 merge commit 所以還好，但不 robust）

**臨床情境：**

（未填寫）

**驗收條件：**

- [ ] 新 GitHub Action `.github/workflows/changelog-backfill.yml`：push 到 main 時觸發，自動把空 commit 欄位回填
- [ ] 新 Python script `.github/scripts/changelog-backfill.py`：parse CHANGE_LOG.md、grep `git log --grep="(#PAT-NNN)"` 找 commit、填入
- [ ] 本機 fallback script `scripts/changelog/fill-hash.sh`：同邏輯、可手動執行（debug 用）
- [ ] `CLAUDE.md` commit 慣例章節更新：不再要求第二 commit、留空 commit 欄位即可
- [ ] Bot commit 避免 infinite loop（action 跳過 `github-actions[bot]` 發的 commit）
- [ ] Idempotent（已填的 row 不會重複處理）
- [ ] 既有 15 個 empty row 用同一 PR 順手 backfill 驗證 script 正確



---


### SRS-022 [需求] CV Count aggregate 對 boolean Measure Observation 須回正確計數（非 null）(#PAT-085)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#269](https://github.com/Lusnaker0730/CQL/issues/269) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Low-Medium（行為變動：既有的 boolean-valued CV measure 的 score 從 null 變 number，算是修正而非 regression；但既有 consumer 若硬依賴 null 會看到變化） |
| 安全性等級 | B（影響 CV 指標計算正確性 + FHIR 合規，同 PAT-081/083/084 家族） |

**需求描述：**

`PopulationEvaluator.extractObservationValues` 只接受 `Number` / `Iterable<Number>` 作為 observation value — **boolean 直接被丟棄**。對產出 boolean Measure Observation 的 CQL（authoring tree 用 boolean-returning element 如 `AgeRange` / `Gender` 組 observation criteria 時）這等於任何 CV measure 都拿不到 observation value → score=null、observationStatistics=null。

CQL 產生器對 `aggregateMethod=Count` + boolean criteria 現在產的 CQL：

```cql
define function "Measure Observation"(Patient "Patient"):
  AgeInYearsAt(end of "Measurement Period") >= 18

define "Measure Observation Value":
  if "Measure Population" then "Measure Observation"(Patient) else null
```

`Measure Observation Value` 對每個 MP 內病人回 true/false，對 MP 外病人回 null。`extractObservationValues` 拿到 true 後當非 Number 跳過，結果 empty list → Count aggregate=0、measureScore=null。

Count aggregate 的語意是「有多少個 non-null observation values」，boolean true 就是一個有效的 observation value。現行行為 silently broken 所有 boolean-criteria CV Count measures。

PAT-082 smoke scenario 03 設計時發現：3 個 adults、MP=3、expected score=3（count of patients with observation），但 backend 回 score=null。當時 scenario 沒 assert score（只 lock populations），follow-up 記 ticket——本 PR 修掉。

**臨床情境：**

CV Count aggregate 最自然的用法是「count of patients with X」或「count of events」：

- **Count of patients with completed assessment** — boolean observation "has completed assessment"
- **Count of patients with recent visit** — boolean "last visit in period"
- **Count of high-risk encounters** — boolean "encounter meets high-risk criteria"

這些都是 boolean Measure Observation 場景。所有這類指標在目前 system 下 score 都是 null，UI / 報表顯示空白 → 臨床管理無法判斷指標完成度。

**驗收條件：**

- [ ] `extractObservationValues` 對 `Boolean` 輸入：TRUE → `1.0`，FALSE → 跳過（empty）
- [ ] Iterable 情況下同樣：list 內 boolean TRUE 收進 1.0、FALSE 跳過
- [ ] 既有 Number / Iterable<Number> 處理**完全不變**（regression lock）
- [ ] Null value 維持回 empty
- [ ] Smoke scenario 03 assert `score: 3.0`（先前 skip）
- [ ] 全量回歸 1209+ tests



---


### SRS-023 [需求] Ratio 的 Numerator 必須獨立於 Denominator (#PAT-084)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#264](https://github.com/Lusnaker0730/CQL/issues/264) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Medium-High（影響所有 ratio measure 的正確性，屬於 class-level bug；但 production 已知 ratio measure 的 consumer 不多——主要是未來新增的 rate-based measure） |
| 安全性等級 | B（影響指標計算正確性 + FHIR interop。同 PAT-081 / PAT-083 家族：generator / evaluator 依 FHIR spec 合規） |

**需求描述：**

`PopulationEvaluator.aggregatePatientResults` 對**所有** scoring type 硬把 Numerator gate 在 Denominator 之後：

```java
boolean inNumer = effectiveDenom && isPopulationTrue(results, "Numerator");
```

這對 proportion 是對的（proportion spec 要求 Numer ⊆ Denom）；但對 **ratio 是錯的**——FHIR MeasureReport R4 spec 對 ratio 定義 Numer 與 Denom 為 **兩個獨立的計數**，都只被 Initial Population 這個 universe 限制。

實際影響：任何 Numer 與 Denom 交集為空（或交集比 Numer 本身小）的 ratio measure 都會回報錯誤數字。這是 **class-level bug**，影響所有 ratio measure，而不是邊界條件。

Ratio 最常用的場景就是 Numer/Denom 獨立的：

- **Encounters per patient**：Denom = 病人數、Numer = encounter 數。Numer 很可能 > Denom（每位病人可有多次 encounter）
- **Falls per 1000 patient-days**：Denom = 病人天數、Numer = fall 事件數
- **Hospital-Acquired Infections per 1000 admissions**：Denom = 入院數、Numer = HAI 事件數

PAT-082 smoke scenario 02 建立過程中發現：原設計 Denom=young-adults, Numer=seniors（disjoint），回報 Numer=0（實際上有 3 個 seniors）、score=0。當時只能用 Numer ⊆ Denom 繞過、並記 follow-up——本 PR 修掉。

**臨床情境：**

醫療品質監控裡 ratio measure 極常見：每千病人日跌倒次數、每次住院感染次數、每千處方 ADE 次數……等。背後 evaluator 若把 Numer 硬 gate 在 Denom，**所有這類指標都算不對**：報告出來的事件率會比實際低（甚至 0），醫院管理決策建立在錯誤基準上，可能**低估風險**。

**驗收條件：**

- [ ] `PopulationEvaluator.aggregateRatioPatientResults(counts, results)` 新 method：Numer 只被 IP gate，不被 Denom gate
- [ ] Ratio 不再計算 Denominator Exceptions（FHIR spec proportion-only 概念）
- [ ] `buildRatioEntries(results)` 新 trace method for debug mode，對應上述語意
- [ ] `buildGroupTrace` dispatch 區分 proportion / ratio（原 `case "proportion", "ratio" -> buildProportionEntries`）
- [ ] `MeasureEvaluationService.executeAndAggregate` 加 `isRatio` flag 路由到新 method
- [ ] Score 計算：`MeasureEvaluationService` 對 ratio 路由到 `calculateRatioScore`（略過 denom-exclusion 扣減、允許 >100%），proportion 維持呼叫 `calculateProportionScore`
- [ ] Smoke scenario 02 revert 到 Disjoint Denom/Numer 設計（Denom=young-adults, Numer=seniors，score=150.0 證明 >100% 可行）
- [ ] Unit tests 鎖住 proportion 與 ratio 兩路徑的差異（同 input shape、不同 aggregation）



---


### SRS-024 [需求] Cohort scoring 須依 FHIR spec 回 measureScore = count(IP) + 僅列 IP population (#PAT-083)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#259](https://github.com/Lusnaker0730/CQL/issues/259) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Medium（wire shape 變動：cohort response payload 不再含 denominator/numerator rows。舊 consumer 如果預期看到 placeholder 0 值可能需要調整，但那本來就不該預期） |
| 安全性等級 | B（影響指標計算正確性與 FHIR interop correctness，同 PAT-081 家族） |

**需求描述：**

`MeasureScoreCalculator` 對 cohort scoring type 回 `null`（程式註解："Cohort measures don't have a numeric score"）——**與 FHIR MeasureReport spec 不符**。FHIR R4 的 `MeasureReport.group.measureScore` 對 cohort scoring 的語意定義是「Initial Population 的 count（Quantity / Decimal）」。

同時 `MeasureEvaluationService.buildResult` 把 cohort measure 跟 proportion / ratio 混用同一路徑，結果 populations 裡硬塞了 `denominator: 0` 與 `numerator: 0` 兩筆——這兩個 population type 在 cohort 定義下不存在（FHIR spec 明定 cohort 只有 `initial-population`）。

這兩個問題是 PAT-082 smoke scenario 04 建立過程中發現的：
- Score assertion 被迫用 `score: null`（or 直接跳過 score 欄位）
- Response payload 含 meaningless `denominator: 0` / `numerator: 0`

影響：
- **下游儀表板 / 報表**：cohort 指標在 UI 顯示空白分數，審查者看不到「有幾位 patient 符合」
- **FHIR interop**：把 MeasureReport JSON 送到 external consumer（EHR / CMS / 研究機構）時，payload 不符合 spec
- **Test harness**：smoke 無法完整 lock cohort 行為（只能 assert IP count）

**臨床情境：**

（未填寫）

**驗收條件：**

- [ ] `MeasureScoreCalculator.calculateCohortScore(Integer ip)` 新 helper：回 IP count as Double（0.0 when IP=0；null when IP=null）
- [ ] `MeasureEvaluationService` 對 cohort 走專屬 branch `buildCohortResult`（比照既有的 `buildCvResult`）：populations 只含 Initial Population、measureScore = IP count、measureScoreUnit = "count"
- [ ] `MeasureScoreCalculator.calculateScore(scoringType, ...)` dispatcher：cohort 條件回 null（因為該 signature 拿不到 IP count），讓所有 cohort score 計算走專屬路徑
- [ ] 單元測試鎖定 cohort score 0 / null / positive 三種情況
- [ ] Smoke scenario 04 expected.json assert `score: 4.0`
- [ ] 全量回歸 1201+ tests



---


### SRS-025 [需求] Smoke test harness — 補齊 ratio / CV / cohort scenario + 跨 scenario 隔離 (#PAT-082)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#257](https://github.com/Lusnaker0730/CQL/issues/257) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Low |
| 安全性等級 | A（dev tooling；不影響 clinical decision correctness；但消除 ratio/CV/cohort scoring 路徑的回歸風險是很實用的 defense-in-depth） |

**需求描述：**

PAT-080 建立了 smoke test harness 與第一個 scenario（proportion）。原始設計目標是每個 eCQM scoring type 至少一個 canonical scenario——因為 4 種 scoring type 的 CQL 產生路徑與結果計算幾乎完全不同（proportion 做 N/D 比例、ratio 容許 N 與 D 獨立、CV 產 MeasureObservation wrapper + 走 `RenderMode.CV_MEASURE_POPULATION` 路徑、cohort 只回 IP count）——只有 proportion 的覆蓋等於忽略了其他 3 種 scoring type 的整合 bug 機會。

同時發現 harness 的原設計假設「disjoint measurement period 就能隔離 scenario」，但對純 demographic 條件（AgeRange / Gender）無效——這類條件不 filter by period，scenario N 的 measure evaluation 會看到 scenario N-1 的 patients。必須在 scenario 之間實際重置 FHIR 狀態。

**臨床情境：**

（未填寫）

**驗收條件：**

- [ ] Scenario 02（ratio）：scoring type=ratio，hit ratio 專屬 score 計算路徑
- [ ] Scenario 03（CV）：scoring type=continuous-variable，使用 populationGroup.observations 帶 aggregateMethod，hit #239 的 `RenderMode.CV_MEASURE_POPULATION` codegen 路徑
- [ ] Scenario 04（cohort）：scoring type=cohort，IP-only 結構
- [ ] Cross-scenario 隔離：scenarios 之間 HAPI 被 reset 乾淨，scenario N 看不到 N-1 的 patients
- [ ] 全 4 scenarios 冷 cache 跑完 < 3 分鐘
- [ ] 每 scenario 的 measure.json + bundle.json + expected.json 三件式結構維持
- [ ] README 更新 scenarios 表格，列出每個 scenario 覆蓋的 code path



---


### SRS-026 [需求] AgeRange element 在 eCQM 環境須綁 Measurement Period (#PAT-081)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#252](https://github.com/Lusnaker0730/CQL/issues/252) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 風險等級 | Medium（產出 CQL 語意變動；但兩個 BuildContext 的使用路徑（CDS vs eCQM）清楚分離，不會互相影響） |
| 安全性等級 | B（影響 eCQM 指標計算正確性 + 回溯性。同 #229 家族——「generator 產出應語意正確且 reproducible」） |

**需求描述：**

目前 `ExpressionCqlEngine.buildAgeRangeExpression` 產出的 CQL 使用 `AgeInYears()`（無參數形式），此函數依 CQL 規範對 FHIR 模型是使用**系統當下時間**計算年齡。這對 eCQM 是錯的——eCQM 的年齡判斷定義上應該 bind 到 Measurement Period 端點（通常是 `end of "Measurement Period"`），否則：

1. **同一份 FHIR 資料 + 同一個 measure 定義 + 同一個 measurement period**，在 2026 跑出的人口數跟 2027 跑會不一樣（有人跨過了年齡閾值）
2. **回溯評估**（評估 2020 年的 measure）會用 2026 年齡而非 2020 年齡，結論完全錯
3. **連 scoring 本身都不 reproducible**：同一個 CQL library cache 在不同機器（時區不同）或不同時間跑，結果可能不同

這在開發 smoke test harness（#250）時意外撞到——期望 p4（生於 1960-03-15）在 2020-06-30 時 60 歲（屬 Denom 不屬 Numer），但實際被算為 66 歲（2026 系統時間）歸入 Numer，導致 Numer 多 1、score 從 60.0 變 80.0。

修法：`BuildContext` 加 `hasMeasurementPeriod` flag；`EcqmCqlBuilder.buildEcqmCql` 設為 true；`buildAgeRangeExpression`/`mapUnitToAgeFunction` 依 flag 切換 `AgeInYears()` 或 `AgeInYearsAt(end of "Measurement Period")`（同樣套用到 Months/Weeks/Days/Hours）。CDS 環境（`CqlArtifactBuilder`）維持 `AgeInYears()`，因為沒有 Measurement Period 可 bind。

**臨床情境：**

臨床品質量測（eCQM）是**追溯性**的：2026 年可能要跑 2020 的 measure 回顧歷史表現。若年齡用系統時鐘，報表會反映「**這些病人在 2026 的年齡是幾歲**」而非「**他們在 2020 measurement period 時的年齡**」，造成 stratification／segmentation 錯誤。

例：a1c_dm 指標的 Numerator 經常有年齡上限（例如 <75 歲）以排除療護目標不同的 frail elderly。用系統時鐘會把 2020 時 70 歲但 2026 時已 76 歲的病人錯誤排除於 Numerator，造成該報表週期的 quality score 人為降低。

**驗收條件：**

- [ ] `BuildContext` 加 `hasMeasurementPeriod` public field，預設 false
- [ ] `EcqmCqlBuilder` 在 buildEcqmCql 開頭 set 為 true
- [ ] `buildAgeRangeExpression(fields, ctx)` 新 signature；舊 1-arg 版 `@Deprecated(forRemoval=true)` 繼續工作
- [ ] `mapUnitToAgeFunction(unit, bindToMeasurementPeriod)` 新 signature；舊 1-arg 版 `@Deprecated(forRemoval=true)`
- [ ] 單元測試覆蓋兩邊模式（CDS vs eCQM）+ 所有 unit variants（year/month/week/day/hour）
- [ ] Smoke scenario 01 可用**自然生日**（不需 age-bracket-stable 繞過）並 pass
- [ ] 全量回歸 1194+ tests



---


### SRS-027 [需求] 本機整合 smoke test harness — 讓 scoring-type pipeline 在 push 前可驗證 (#PAT-080)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#250](https://github.com/Lusnaker0730/CQL/issues/250) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 風險等級 | Low（dev tooling，不出 product） |
| 安全性等級 | A（開發流程改善；不影響 clinical decision correctness。Generator + translator gate 依然由 unit test 把關，smoke 是 defense-in-depth） |

**需求描述：**

目前我們的自動化測試層只有 unit tests（1183 個）。「unit tests 全綠」卻在 production 爆的 bug 本季已有多筆：BUG-110（`ToInterval(null)` dispatch ambiguity）、BUG-111（cross-library retrieve 缺 Encounter）、#230 modifier catalog wire shape 改動、#247 SignatureLevel=None。每一次都是「使用者部署到 VM → 手動建 eCQM → 執行 → 回報沒資料」的 ~15 分鐘 feedback loop，開發的 signal 其實是使用者的挫折。

這些 bug 的共通點：**要到真翻譯/真執行 engine 才會爆**，unit tests 看不到。既有 `ModifierGeneratedCqlGoldenTest`（in-process golden suite）蓋住了 element / modifier 層的 CQL 產生，但**沒有任何測試覆蓋到 scoring-type pipeline 的 end-to-end**（save → publish → evaluate 的 API 表面、Spring Boot context、Flyway migrations、HAPI 互動）。

**臨床情境：**

直接影響是開發流程，不直接影響臨床資料正確性——但間接影響很大：當整合 bug 只能靠 production 驗證時，會多出額外的 「推一次 → 壞一次 → 再修一次」PR 連環（BUG-108 → PAT-067 → BUG-110 → BUG-111 是典型），每一次都帶著些許 production 使用者體驗的退化。有 local smoke 之後，開發者在 push 前就能抓到 90% 的整合 bug，大幅縮短 clinical-facing regression 的暴露時間。

**驗收條件：**

- [ ] `scripts/smoke/run.sh` 單一指令帶起 isolated Docker compose 堆疊並執行所有 scenarios
- [ ] 每個 eCQM scoring type 至少一個 canonical scenario（4 種：proportion / ratio / continuous-variable / cohort，本 PR 先做 proportion，其他 3 種跟進）
- [ ] 每個 scenario 有 measure.json + bundle.json + expected.json 三件式 fixture
- [ ] 全量跑完 < 3 分鐘（冷 cache），< 90 秒（熱 cache）
- [ ] 自動 teardown；可用 `--keep` debug
- [ ] 使用 1 萬以上的 host port 避免衝突 dev stack 或 local postgres
- [ ] `scripts/smoke/README.md` 說明 scenarios 結構 + 新增 scenario 的方式
- [ ] `CLAUDE.md` 加入「push 前跑 smoke」的開發慣例條款



---


### SRS-028 [需求] 後端部署時前端 React Query cache 應自動失效 (#PAT-079)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#248](https://github.com/Lusnaker0730/CQL/issues/248) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 風險等級 | Low |
| 安全性等級 | A（不影響指標計算／CDS 決策，只影響 UI 陳列。Generator 與 translator 對 CQL 的 gate 不因前端 cache 陳舊而被繞過） |

**需求描述：**

前端 `useModifiers` hook 設 `staleTime: Infinity`（`useEcqmModifiers` 也用 1 分鐘 TTL）。後端部署改動 modifier catalog 的 wire shape（#230 把 `resourceAlias`/`whereClause` 換成 `during.{alias, dateFieldSpec}`）後，**保留舊分頁的使用者**會繼續看到 cached 舊 schema：

- Modifier 編輯 dialog 會引用已消失的欄位，產出 undefined（靜默失敗、UI 不報錯）
- 除非 user 手動 Cmd+Shift+R 或完全關閉重開，否則永遠不會 refetch

今天就已踩到：#230 merge + 部署後短時間內，任何「部署前已開著 authoring 或 ecqm 頁」的 user 都處於這個狀態。

**臨床情境：**

（未填寫）

**驗收條件：**

- [ ] 後端提供 `/api/version` 端點，回 `{startupTime, commitSha, version}`，未認證可讀
- [ ] 前端 `VersionCheckProvider` 掛在最外層，每 5 分鐘 poll，對比 mount 時 snapshot
- [ ] 偵測到 version 變動時自動 `queryClient.invalidateQueries()` + 顯示 toast「系統已更新」
- [ ] `useModifiers` 的 `staleTime: Infinity` 改為 `STALE_5M`（defense-in-depth）
- [ ] 單元測試鎖住 `/api/version` 為 unauth-readable、同一 boot 內 `startupTime` 穩定



---


### SRS-029 [需求] CQL 翻譯器 SignatureLevel 必須設為 Overloads 以消除多 overload function 的 dispatch ambiguity (#PAT-078)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#243](https://github.com/Lusnaker0730/CQL/issues/243) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 風險等級 | Medium（同 BUG-110 / BUG-112 風險家族，但尚未觀察到線上事故） |
| 安全性等級 | B（影響指標計算正確性、CDS hook 觸發邏輯） |

**需求描述：**

CQL Translator 目前以 `SignatureLevel.None`（預設值）產生 ELM，導致：

1. `FHIRHelpers.ToString` / `ToDateTime` / `ToInterval` 等**多 overload** function 在 ELM 內只留 function 名稱、**不嵌參數型別簽章**
2. Engine 執行期只能靠 argument 的 runtime type 猜 overload
3. 當 argument 為 null 或基底型別（FHIR choice type 未被 type-checked）時，dispatch 變成歧義——**與 BUG-110 / BUG-112 同家族的 runtime failure**
4. Translator 每次翻譯都會 emit 警告：`The function FHIRHelpers.ToString has multiple overloads and due to the SignatureLevel setting (None), the overload signature is not being included in the output. This may result in ambiguous function resolution at runtime...`

根因位於 `LibraryManagerFactory.defaultOptions()` 與 `buildOptions()`——兩個 options 工廠方法都**完全未呼叫 `setSignatureLevel` / `withSignatureLevel`**，全專案 translate 路徑一律繼承此預設。

**臨床情境：**

品質量測（eCQM）與 CDS hook 的 CQL 若使用 `FHIRHelpers.ToString(某 FHIR 欄位)` 於指標定義中，當該欄位在某些患者資料上為 null、或為 base type（未被 `as T` 窄化），engine 會：

- 拋 `AmbiguousCall` 例外 → 指標計算整個 group 失敗，回報錯誤給使用者
- 或（更糟）挑到非預期的 overload 產出 wrong-type coercion → 靜默錯誤數字

實務上，Observation / Condition / Procedure 的日期欄位為 choice type 是家常便飯，曝險面很廣。

**驗收條件：**

- [ ] `LibraryManagerFactory.defaultOptions()` 與 `buildOptions()` 產出的 `CqlCompilerOptions` 之 `signatureLevel` 為 `Overloads`（或更寬）
- [ ] 新增單元測試 `LibraryManagerFactoryTest` 鎖住此不變式，回歸時會 fail
- [ ] 全量回歸（1179+ 測試）通過
- [ ] 部署後 translator 警告訊息消失（驗 production log）



---


### SRS-030 [需求] 把 modifier 的 CQL 字串改為結構化 fieldSpec

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#229](https://github.com/Lusnaker0730/CQL/issues/229) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 風險等級 | Low |
| 安全性等級 | B（影響指標計算正確性） |

**需求描述：**

\`modifiers.json\` 目前把 CQL 以逃脫過的字串 (\`\\\"Measurement Period\\\"\`) 直接塞在 JSON 的 \`whereClause\` 欄位裡。這造成：

1. JSON 裡的 CQL 沒有 syntax highlighting、沒有 IDE 檢查、也沒有 translator 保護，錯誤只能在執行期發現（BUG-110 / BUG-112 正是如此）
2. 6 個 DuringMeasurementPeriod entry 的 whereClause 重複了 90% 的 case-expression 結構
3. \`ModifierDefinition\` POJO 被這類 subtype-only 欄位污染

改成結構化 fieldSpec 後，JSON 只描述「field name + 允許的 FHIR 型別 + 可選 fallback」，後端的 generator 產出 null-safe 的 CQL case 表達式。dispatch ambiguity 由 code 構造上杜絕，不需 review CQL 字面值。

**臨床情境：**

（未填寫）

**驗收條件：**

- [ ] modifiers.json 的 6 個 DuringMeasurementPeriod entry 使用 \`dateFieldSpec\` 結構
- [ ] \`ExpressionCqlEngine\` 有單元與整合測試鎖住產出 CQL
- [ ] Golden integration suite 仍 100% 通過（行為等價）
- [ ] BUG-110 / BUG-112 無法以 generator 產出（測試反證）



---


### SRS-031 [需求] 後端啟用 JaCoCo 測試覆蓋率量測

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#227](https://github.com/Lusnaker0730/CQL/issues/227) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 風險等級 | Low（純建置設定，不改產品碼） |
| 安全性等級 | A |

**需求描述：**

後端 Java 側目前沒有任何測試覆蓋率量測。討論「應該多寫測試」時沒有基準數字，無法判斷優先順序、進度、或回歸狀況。需啟用 JaCoCo Maven plugin 產生覆蓋率報告，並取得 baseline 以做後續規劃。

**臨床情境：**

不直接涉及臨床，屬於軟體品質保證 / 監管要求（IEC 62304 要求說明各安全性等級的軟體單元測試覆蓋目標）。

**驗收條件：**

- [ ] \`mvn test\` 每次執行後在 \`target/site/jacoco/\` 產出 HTML + CSV 報告
- [ ] 排除 DTO / entity / config / Main class 不計入 %
- [ ] 量測結果記錄在 PR 描述中供 baseline 使用
- [ ] 為後續「每包覆蓋率門檻」奠定基礎



---


### SRS-032 [需求] CQL 產生器 golden integration test suite

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#224](https://github.com/Lusnaker0730/CQL/issues/224) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 風險等級 | Low（純新增測試 + 改 JSON 字串） |
| 安全性等級 | B（直接影響臨床指標計算正確性） |

**需求描述：**

目前 CQL 產生器（ExpressionCqlEngine + modifier catalog）只有 translator-level 單元測試（檢查產出字串），沒有跑過真的 CQL engine。這讓 dispatch-time-only 的錯誤（如 ToInterval(null) 歧義）能 silent 通過 unit test、translate phase，只在 runtime 對真資料才爆炸。

實際傷害：BUG-110 正是這類問題 — 產出 CQL 字串正確、translator 通過，但使用者一用就爆。修完 BUG-110 寫 golden suite 時又立刻抓到同類型的 BUG-112（Encounter / MedicationRequest 的 \`and\` 不短路）。

**臨床情境：**

品管人員建構的每個 eCQM 都會經過 modifier catalog 產生 CQL；若 generator 有 dispatch bug，所有相關 measure 評估會回 null / 出錯，影響跨年度指標準確性。

**驗收條件：**

- [ ] 新 integration suite 針對每個 modifier 產出的 CQL 跑過真 CQL engine
- [ ] 覆蓋 FHIR choice type（Period / dateTime / instant / null）至少四種
- [ ] BUG-110 / BUG-112 regression lock 明確存在
- [ ] suite 不使用 Mockito（整合測試不該依賴 mockito-inline + JDK 相容性）
- [ ] CI 每次 backend push 都跑



---


### SRS-033 [需求] 前端工具函式測試覆蓋率補強

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#222](https://github.com/Lusnaker0730/CQL/issues/222) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 風險等級 | Low（純新增測試，不改產品碼） |
| 安全性等級 | A |

**需求描述：**

目前前端測試覆蓋率約 24% statements，多個純函式工具模組完全沒有測試（cqlString / cqlNames / scoreColors / random / twDemographics / download 等）。這些模組是 CQL 產生、假病人產生、品質量測顯示等關鍵流程的基礎建設。缺少測試使得未來改動無回歸保護。

**臨床情境：**

不直接涉及臨床情境，屬於非功能性需求（軟體品質保證）。

**驗收條件：**

- [ ] `src/utils/cqlString.ts` 測試涵蓋 escape 正確順序（backslash 在 quote 之前）
- [ ] `src/utils/cqlNames.ts` 測試涵蓋 extract/parseDefineBlocks/findQuotedReferences
- [ ] `src/utils/scoreColors.ts` 測試涵蓋 80/60 閾值邊界
- [ ] `src/utils/random.ts` 測試用 mocked Math.random 驗證邊界行為
- [ ] `src/utils/twDemographics.ts` 測試含 NHI ID checksum 獨立驗證
- [ ] `src/utils/download.ts` 測試 stub URL.createObjectURL 驗證 createObjectURL + click + revokeObjectURL 呼叫序
- [ ] 全部新測試在 CI 通過



---


### SRS-034 [需求] Modifier chain 中間插入 UI

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#216](https://github.com/Lusnaker0730/CQL/issues/216) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 風險等級 | Low |
| 安全性等級 | A |

**需求描述：**

Authoring UI 的 modifier chain 原本只能在尾端 append 新 modifier，造成新 modifier 若類型與鏈尾不相容時使用者必須拆掉尾端重組（例如想在 `Verified → MostRecent → QuantityValue` 之間插入 `DuringMeasurementPeriod` 時，因尾端 `system_quantity` 與新 modifier 所需的 `list_of_observations` 不匹配，下拉選單不會出現新選項）。需改為允許於 chain 任一位置插入 modifier，並自動處理下游相容性。

**臨床情境：**

品管人員已建立含三個 modifier 的 Observation 元素來取每個病人最近一筆 HbA1c 的數值，現在依 #212 需要在中間加入「During Measurement Period」過濾；不應要求使用者手動拆開現有 chain。

**驗收條件：**

- [ ] Modifier chain 中每個 modifier card 前顯示可點擊的「+」按鈕（插入在該位置）
- [ ] 末端仍保留「Select Modifiers」主按鈕（append 行為不變）
- [ ] 插入後若下游 modifier 型別不相容，自動由 validateModifierChain 剪除
- [ ] 單元測試覆蓋 getReturnTypeAtPosition 各種情境



---


### SRS-035 [需求] eCQM Measurement Period 期間過濾 modifier

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#212](https://github.com/Lusnaker0730/CQL/issues/212) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 風險等級 | Medium — 影響品質指標結果正確性，若使用者未意識到需加此 modifier，會誤判跨年度趨勢。 |
| 安全性等級 | B |

**需求描述：**

eCQM 目前宣告 `"Measurement Period"` Interval 參數並由後端傳遞到 CQL engine，但 authoring UI 的 modifier 清單缺少「在 Measurement Period 期間內」的選項，導致使用者建構的 population criteria 只能產生如 `[Observation: "Hemoglobin A1c"]` 這種無條件 retrieve，無法用 Measurement Period 過濾，造成跨年度比較（例如 2025 vs 2026）得到相同分數。需要新增 DuringMeasurementPeriod modifier 讓使用者能在 Observation / Condition / Encounter / Procedure / MedicationRequest / MedicationStatement 元素上套用「during Measurement Period」過濾。

**臨床情境：**

1. 品管人員在儀表板選擇 2026 年度期間評估 a1c_dm 指標
2. 評估結果應只反映 2026 年內的 HbA1c 觀察值
3. 若使用者改為 2025 年度，應得到 2025 年的觀察值平均，結果與 2026 不同
4. 本需求修正後，使用者只需在 eCQM builder 的 Observation 元素上加「During Measurement Period」modifier 即可達成

**驗收條件：**

- [ ] 6 種 resource type（Observation / Condition / Encounter / Procedure / MedicationRequest / MedicationStatement）各有對應 DuringMeasurementPeriod modifier
- [ ] Observation / Procedure 的 modifier 能處理 FHIR choice type（`effective` / `performed` 同時為 Period 或 dateTime 的情形）
- [ ] a1c_dm 套用 modifier 後，跑 2025 與 2026 兩次得到不同分數
- [ ] Backend unit test 覆蓋 observation / encounter / unknown-id fallback 三種情境



---


### SRS-036 [需求] AI Fix Suggestion 知識庫增強

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#203](https://github.com/Lusnaker0730/CQL/issues/203) |
| 建立日期 | 2026-04-16 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

目前 AI Fix Suggestion（`POST /api/cql/fix-suggestion`）使用單一硬編碼 ~70 行 system prompt，只涵蓋基本 CQL 規則 + 3 種 CodeableConcept 模式。前端有 2700+ 行教學內容（TroubleshootingGuide、TwcoreGuide、EcqmTutorial、AdvancedTopics）但 AI 完全拿不到。常見失敗類型：CodeableConcept 深層匹配錯誤、TWCORE 本地 code systems、eCQM population 邏輯、FHIRHelpers 虛構函式。

需要把教學內容抽成結構化知識庫，根據錯誤訊息動態選相關條目注入 AI prompt。

**臨床情境：**

臨床決策支援（CDS Hooks）、品質量測（eCQM）等場景，使用者遇到 CQL 編譯錯誤時依賴 AI 給出修正建議。若 AI 給錯建議可能誤導開發，降低使用者信任度。強化 AI 對 CQL 的理解能顯著提升修正建議品質。

**驗收條件：**

- 至少 8 個 YAML 知識檔（troubleshooting / codeable-concept / fhirhelpers / retrieve-filtering / valueset-binding / twcore / advanced-patterns / ecqm-populations）
- YAML 載入失敗不擋 app 啟動（best-effort）
- CqlKnowledgeBase 提供 `findRelevant(errorMsg, cql, topK)` keyword 匹配
- CloudAiService 和 OllamaService 都整合知識庫
- System prompt 動態附加 top-3 相關條目（含 before/after 範例）
- 空知識庫時行為 = 原本 SYSTEM_PROMPT（向下相容）
- 單元測試覆蓋 YAML 載入、keyword 匹配、prompt 構建



---


### SRS-037 [需求] eCQM 測試案例除錯模式（整合 Coverage + Population Membership Trace）

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#200](https://github.com/Lusnaker0730/CQL/issues/200) |
| 建立日期 | 2026-04-16 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

目前 eCQM 測試案例有獨立的 Coverage 按鈕和一般 Run 按鈕，使用者需要分兩次執行才能同時看到 pass/fail 與 coverage。而且測試失敗時無法得知：
1. 每個 CQL 表達式的詳細結果與耗時
2. 為什麼這位病患（沒）進入分子？（IP → Denom → Numer 層級哪一關擋下來）
3. 錯誤發生在哪個階段（Bundle 解析、CQL 翻譯、CQL 執行、族群評估）

需整合 Coverage 和新 Debug 功能為單一「Debug Mode」開關；新增 Population Membership Trace 直接顯示每個族群的 raw CQL 布林、經 hierarchy gating 後的 effective 布林、以及人類可讀的原因。

**臨床情境：**

臨床品質量測（eCQM）的設計需經嚴格驗證。當測試案例結果不如預期時，目前只能逐一檢查 CQL 表達式或用 Coverage 表查看。Population Membership Trace 直接回答「這位病患為什麼沒進分子」，大幅降低除錯成本，特別是對非後端開發者友善。

**驗收條件：**

- Test case 工具列有 Debug Mode Switch；關閉時行為不變（輕量 pass/fail）
- 開啟時，單次 Run 回傳：
  - Pass/Fail + expected/actual populations（原有）
  - Population Membership Trace（預設展開）
  - Expression Coverage（可展開，原功能）
  - CQL Execution Trace（expression + retrieve + ELM）
- 支援 proportion / ratio / continuous-variable / cohort 四種計分類型
- 錯誤時顯示 phase chip（BUNDLE_PARSE / CQL_TRANSLATION / CQL_EXECUTION / POPULATION_EVAL）
- 舊 `/run-with-coverage` endpoint 向下相容（回傳格式不變）
- Debug 資訊不寫入 DB（避免 row 膨脹），僅透過 HTTP response 回傳



---


### SRS-038 [需求] CDS Hook 除錯模式與結構化診斷

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#198](https://github.com/Lusnaker0730/CQL/issues/198) |
| 建立日期 | 2026-04-15 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

當使用者寫的 CQL 在 Editor 翻譯/執行成功，但在 CDS Sandbox 或 Invoke 面板呼叫失敗，目前只能看到「An error occurred: 單行訊息」，其他資訊全在 server log。開發者無從判斷是哪個階段失敗（prefetch 解析、CQL 翻譯、CQL 執行、FHIR 呼叫、卡片生成），也看不到 prefetch 是否真的拿到資源、context patientId 是否正確綁定。

需要一套完整的除錯工具讓使用者在 UI 直接看到：
- 每個表達式的執行結果與耗時
- 每個 prefetch key 的解析狀態
- Context 綁定警告（如病患不在 prefetch 中）
- FHIR server 錯誤分類（timeout / 404 / 網路等）
- 編譯後 ELM（進階）
- Admin 可看到最近的調用紀錄
- Dry Run 模式（解析 prefetch 但不執行 CQL，快速驗證環境設定）

**臨床情境：**

臨床決策支援服務在正式部署前必須經過完整測試。當 EHR 整合商送進來的 context/prefetch 與 CQL 作者預期不符時，目前只能透過 SSH 撈 server log 診斷，效率低且對非後端開發者不友善。完整的 UI 除錯工具能大幅縮短 CDS 服務開發與除錯時間。

**驗收條件：**

- Sandbox/Invoke 面板有 Debug Mode switch，開啟後顯示 expression + retrieve traces
- 失敗時顯示結構化錯誤：phase + errorType + 錯誤訊息 + top-5 stack frames
- Response 包含每個 prefetch key 的 status/count/elapsedMs/error/resolvedUrl
- 缺少 patientId 或 Patient 資源不在 prefetch 時顯示警告
- FHIR server 錯誤分類顯示：timeout/network/not_found/unauthorized/forbidden/server_error/unsupported_version/other
- Debug mode 下可展開查看編譯後 ELM JSON
- Admin 可查看最近 100 筆 CDS 呼叫紀錄（時間/服務/hook/病患/狀態/耗時）
- Dry Run switch：只解析 prefetch + 驗證 context，不執行 CQL，回傳資源清單
- EHR 客戶端（不送 debugMode）不受影響 — debug 欄位 `@JsonInclude(NON_NULL)`



---


### SRS-039 [需求] CDS Hook Context 欄位驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#197](https://github.com/Lusnaker0730/CQL/issues/197) |
| 建立日期 | 2026-04-15 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A

---

CDS Hooks 規範各 hook 類型必要 context：
| Hook | Required Context |
|------|-----------------|
| patient-view | userId, patientId |
| order-select | userId, patientId, selections, draftOrders |
| order-sign | userId, patientId, draftOrders |
| encounter-start | userId, patientId, encounterId |
| encounter-discharge | userId, patientId, encounterId |
| appointment-book | userId, patientId, appointments | |

**需求描述：**

依 CDS Hooks 規範，不同 hook 類型需要不同的必要 context 欄位。目前實作對所有 hook 統一處理，所有 context 欄位皆為 optional，無法強制 EHR client 提供正確的 context。需加入 hook-type-specific 的 context 驗證機制。

**臨床情境：**

臨床決策支援服務（CDS Hooks）在 EHR 系統的關鍵決策點觸發。不同觸發點（如開啟病歷、選擇醫令、預約預約）需要不同的上下文資訊才能正確執行 CQL 邏輯。若缺少必要資訊，決策結果可能不完整或錯誤。

**驗收條件：**

- 後端接收到 CDS 調用請求時，若缺少該 hook 類型所需的 context 欄位，回傳 HTTP 400 並列出缺少的欄位
- Discovery 端點（/cds-services）回應包含每個 service 所需的 context 欄位定義
- 前端 Sandbox / Invoke 面板根據所選 hook 類型動態顯示 context 輸入欄位
- 前端 ManageServices 面板選擇 hook 類型後顯示該 hook 所需的 context 欄位提示
- 支援 6 種 CDS Hook 類型的 context 要求：patient-view、order-select、order-sign、encounter-start、encounter-discharge、appointment-book
- 新增 appointment-book hook 的 appointments 欄位支援



---


### SRS-040 [需求] 品質指標儀表板分數分類型顯示

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#194](https://github.com/Lusnaker0730/CQL/issues/194) |
| 建立日期 | 2026-04-03 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A — 儀表板顯示修正，不影響臨床決策邏輯 |

**需求描述：**

品質指標儀表板的品質報告區塊將不同計分類型（proportion vs continuous-variable）的分數混在一起計算平均值，導致語意錯誤。同時分數趨勢圖的 X 軸標籤過長且圖例顯示 ID 而非指標名稱。

**臨床情境：**

管理者在儀表板查看品質指標整體表現時，需要正確區分比例型指標（如採檢率 100%）和連續變數型指標（如平均 HbA1c 5.6）。混合平均會產生誤導性數據。

**驗收條件：**

1. 品質報告的平均分數僅計算 proportion/ratio/cohort 類型指標
2. Continuous-variable 類型指標以原始數值顯示（不加 % 後綴）
3. 趨勢圖 X 軸標籤簡短易讀
4. 近期評估表格和趨勢圖顯示指標名稱而非 ID



---


### SRS-041 [需求] eCQM 程式庫定義 externalCqlElement 後端支援

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#193](https://github.com/Lusnaker0730/CQL/issues/193) |
| 建立日期 | 2026-04-02 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A — 顯示修正，不影響臨床決策邏輯 |

**需求描述：**

eCQM 撰寫頁面使用 `LibraryDefinitionPicker` 載入外部 CQL 程式庫定義時，後端驗證器 (`EcqmExpressionTreeValidator`) 不認識 `externalCqlElement` 元素類型，導致儲存時拋出 `ValidationException: Invalid eCQM artifact data`。

**臨床情境：**

使用者在 eCQM 撰寫流程中，需要引用已建立的 CQL 程式庫定義（如 ASCVD.MeetsInclusionCriteria），作為族群篩選條件。此為品質指標建構的標準工作流程。

**驗收條件：**

1. eCQM 族群群組中可新增 `externalCqlElement` 類型的程式庫定義參考
2. 儲存成功（不再拋出驗證錯誤）
3. CQL 產生正確的 include 宣告（如 `include ASCVD version '1.0.0' called A`）
4. CQL 產生正確的表達式引用（如 `"A"."MeetsInclusionCriteria"`）



---


### SRS-042 [需求] Dev/Prod 資料庫一致性與 CQL 引擎測試覆蓋強化

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#190](https://github.com/Lusnaker0730/CQL/issues/190) |
| 建立日期 | 2026-03-30 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

本地開發環境須與生產環境使用相同的資料庫系統（PostgreSQL），消除 H2/PostgreSQL 方言差異導致的 Flyway migration 不一致風險。同時，CQL 生成引擎（ExpressionCqlEngine、CqlArtifactBuilder）作為臨床決策支援的核心路徑，須強化邊界案例測試覆蓋率以符合 IEC 62304 軟體單元驗證要求。

**臨床情境：**

CQL 生成引擎產出的 CQL 程式碼直接影響臨床決策支援系統的建議結果。若轉義不當（如注入）、參數格式錯誤、或修飾器邏輯缺陷，可能導致錯誤的臨床建議。

**驗收條件：**

1. 開發環境改用 PostgreSQL，Flyway 啟用並與生產環境行為一致
2. Migration 腳本統一使用標準 SQL 語法（消除 BIGSERIAL、::、partial index 等 PostgreSQL 專屬語法）
3. ExpressionCqlEngine 與 CqlArtifactBuilder 新增邊界案例測試，覆蓋所有 expression type、modifier、parameter type、escaping 路徑
4. 所有測試通過（0 failures）



---


### SRS-043 [需求] BUG-101: Episode-based CV Measure Population 型別不匹配修復

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#187](https://github.com/Lusnaker0730/CQL/issues/187) |
| 建立日期 | 2026-03-29 |
| 狀態 | open |
| 風險等級 | 低 — 僅影響 CQL 產生邏輯，不影響病人安全 |
| 安全性等級 | A — 輔助功能，不直接影響臨床決策 |

**需求描述：**

修復 episode-based continuous-variable 量測在 CQL 產生時的型別不匹配驗證錯誤。`preserveListReturn` 旗標未阻止 `MostRecent`/`QuantityValue` 等修飾器，導致 Measure Population 回傳 `System.Quantity` 而非資源列表。

**臨床情境：**

使用者建立 A1C 糖尿病 continuous-variable 量測時，系統產生的 CQL 無法通過驗證，阻擋使用者完成品質指標建構。

**驗收條件：**

- Episode-based CV 量測的 Measure Population 回傳資源列表（如 `List<Observation>`）
- Measure Observation 函數簽名與 Measure Population 回傳型別匹配
- CQL 驗證通過，無型別不匹配錯誤



---


### SRS-044 [需求] CQL 引擎可靠性與效能改善

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#185](https://github.com/Lusnaker0730/CQL/issues/185) |
| 建立日期 | 2026-03-28 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

改善 CQL 引擎在多種量測情境下的可靠性與正確性，包含：
1. Ratio measures 觀察型評分支援
2. eCQM 連續變數量測評估
3. CQL engine 4.5+ Map-based tuple 相容性
4. TW Core 代碼選擇器使用正確的 code reference
5. 非 ASCII 字元過濾

**臨床情境：**

醫療品質量測人員使用平台建立與執行 CQL 品質指標時，需確保各類量測類型（ratio、continuous-variable）能正確產生 CQL 並執行，且 CDS Hooks 在新版 CQL 引擎上能穩定運作。

**驗收條件：**

- Ratio measure 能正確評估並產生分數
- eCQM 連續變數量測能產生有效 CQL
- CDS Hooks 在 CQL engine 4.5+ 上無 tuple accessor 錯誤
- TW Core 代碼選擇器以 code（非 value set）方式加入
- 所有後端測試通過



---


### SRS-045 [需求] 修復 eCQM 期間比較顯示 N/A

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#163](https://github.com/Lusnaker0730/CQL/issues/163) |
| 建立日期 | 2026-03-25 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

eCQM 期間比較與趨勢功能在選擇兩個比較期間後，結果顯示 N/A。原因是查詢報告時使用精確日期匹配，只要選擇的日期與已儲存報告的期間不完全一致就找不到資料。

需修正為：
1. 先嘗試精確日期匹配
2. 找不到時 fallback 到範圍查詢（報告期間落在選擇範圍內）
3. 多筆報告時取最佳結果
4. 兩個期間都無資料時顯示提示訊息

**臨床情境：**

品質管理人員需要比較不同時期的品質指標表現，目前功能無法正常使用。

**驗收條件：**

- [ ] 範圍查詢能找到期間內的報告
- [ ] 多筆報告取最佳分數
- [ ] 無資料時顯示友善提示
- [ ] 不影響精確匹配的既有行為



---


### SRS-046 [需求] EHR 連線健康監控儀表板

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#154](https://github.com/Lusnaker0730/CQL/issues/154) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

建立 EHR 連線健康監控系統：
1. **連線健康指標**：每個 EHR 連線的延遲、錯誤率、可用性
2. **Circuit Breaker 狀態**：即時顯示各斷路器的 CLOSED/OPEN/HALF_OPEN 狀態
3. **歷史趨勢**：最近 24h/7d 的連線健康歷史
4. **健康檢查排程**：定期自動測試每個連線的可用性

**臨床情境：**

管理員需要即時掌握各醫院 FHIR 伺服器的連線狀況，及時發現和處理連線問題，確保資料匯入的可靠性。

**驗收條件：**

- [ ] ConnectionHealthEntity 記錄健康指標（V50 migration）
- [ ] ConnectionHealthService：收集/查詢健康指標
- [ ] 排程健康檢查：定期 ping 各連線
- [ ] GET /api/ehr/health/overview 總覽端點
- [ ] GET /api/ehr/health/connections/{id}/history 歷史端點
- [ ] GET /api/ehr/health/circuit-breakers 斷路器狀態
- [ ] 單元測試涵蓋健康收集、歷史查詢、排程邏輯


**關聯項目：** P1 醫院 FHIR 對接工程


---


### SRS-047 [需求] TW Core IG Profile 驗證強化

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#153](https://github.com/Lusnaker0730/CQL/issues/153) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

強化 TW Core IG Profile 驗證功能：
1. **外部 IG 套件載入**：支援從檔案系統載入 IG package（不限 classpath）
2. **Profile 驗證 API 強化**：批次驗證 Bundle 中所有資源
3. **TW Core 合規報告**：產出 TW Core IG 合規摘要
4. **Profile 推薦**：依資源類型自動推薦適用的 TW Core Profile

**臨床情境：**

從醫院匯入的 FHIR 資料需要驗證是否符合台灣 TW Core IG 規範。不合規的資料可能導致品質量測結果不正確。

**驗收條件：**

- [ ] 支援 file:// 路徑載入外部 IG package
- [ ] POST /api/fhir/validate/bundle 批次驗證端點
- [ ] GET /api/fhir/ig/profiles/{resourceType}/recommended 推薦 Profile
- [ ] 驗證結果含 TW Core Profile 合規摘要
- [ ] 單元測試涵蓋 Profile 載入、驗證、推薦邏輯


**關聯項目：** P1 醫院 FHIR 對接工程


---


### SRS-048 [需求] 病人身份比對服務（MPI）

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#152](https://github.com/Lusnaker0730/CQL/issues/152) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

建立病人身份比對（Master Patient Index）功能：
1. **確定性比對**：以身分證字號 + 生日做精確比對
2. **模糊比對**：姓名相似度比對（支援中文姓名）
3. **信心分數**：每筆比對結果附帶信心分數（0-100）
4. **跨連線去重**：識別不同 EHR 連線中的同一病人

**臨床情境：**

品質量測需要整合多家醫院的病人資料。同一病人可能在不同系統有不同 ID，需要 MPI 服務來正確關聯。

**驗收條件：**

- [ ] PatientMatchService：deterministic + probabilistic 比對邏輯
- [ ] POST /api/ehr/patients/match 端點（接受比對參數）
- [ ] PatientMatchResult DTO（含 confidence score、match type）
- [ ] 支援 TW 身分證字號格式驗證
- [ ] 跨連線批次比對功能
- [ ] 單元測試涵蓋各比對情境


**關聯項目：** P1 醫院 FHIR 對接工程


---


### SRS-049 [需求] FHIR Subscription 即時推送通知

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#151](https://github.com/Lusnaker0730/CQL/issues/151) |
| 建立日期 | 2026-03-25 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

支援 FHIR R4 Subscription 資源，讓平台能即時接收醫院 FHIR 伺服器的資料變更通知：
1. **Subscription 管理**：建立/查詢/刪除對遠端 FHIR 伺服器的 Subscription
2. **REST-hook 回呼**：接收 FHIR 伺服器推送的資源變更通知
3. **通知處理**：收到通知後自動同步受影響的病人資料
4. **Subscription 狀態監控**：追蹤每個 Subscription 的活躍/錯誤狀態

**臨床情境：**

醫院 FHIR 伺服器新增或更新病人資料（如新檢驗報告、新醫囑）時，平台需即時得知，而非依賴使用者手動搜尋。這對臨床品質量測的即時性至關重要。

**驗收條件：**

- [ ] SubscriptionEntity 記錄訂閱狀態（V49 migration）
- [ ] SubscriptionService：建立/取消 FHIR Subscription + 狀態同步
- [ ] SubscriptionCallbackController：REST-hook 端點接收通知
- [ ] EhrIntegrationController 新增 Subscription CRUD 端點
- [ ] 通知處理：收到資源變更時記錄並可選觸發同步
- [ ] 單元測試涵蓋訂閱建立、回呼處理、狀態管理


**關聯項目：** P1 醫院 FHIR 對接工程


---


### SRS-050 [需求] 修正 CQL 執行引擎 NullPointerException (#BUG-015)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#150](https://github.com/Lusnaker0730/CQL/issues/150) |
| 建立日期 | 2026-03-25 |
| 狀態 | closed |
| 風險等級 | 中 — 影響 CQL 執行結果的正確性 |
| 安全性等級 | B — CQL 執行結果可能影響臨床品質量測 |

**需求描述：**

CqlExecutionService.doExecute() 在 CQL 引擎回傳 null EvaluationResult 時拋出 NullPointerException，導致 CQL 執行結果遺失。需加入 null 防護，使其優雅降級為 per-expression 逐個評估。

**臨床情境：**

當 CQL library 翻譯失敗或 FHIR 伺服器的 ValueSet 不可用時，CQL 引擎可能回傳 null 結果。目前系統會拋出 NPE 並在日誌中產生大量 WARN，影響品質量測的正確執行。

**驗收條件：**

1. evaluationResult 為 null 時自動降級為 per-expression 評估模式
2. per-expression 評估也回傳 null 時，標記為 Error 而非靜默回傳 null 值
3. Debug 模式路徑同樣加入 null 防護
4. 所有 848 個後端測試通過



---


### SRS-051 [需求] 病人產生器診斷代碼改用 ICD-10-CM 並修正情境模板資料對應 (#BUG-014)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#149](https://github.com/Lusnaker0730/CQL/issues/149) |
| 建立日期 | 2026-03-25 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A — 測試資料產生工具，不影響臨床決策 |

**需求描述：**

病人產生器的診斷（Condition）資料庫需從 SNOMED CT 改為 ICD-10-CM 代碼系統，以符合台灣醫療實務。同時修正情境模板（scenarios.json）中 17+ 個無效代碼引用，導致產生的病人資料與模板設定嚴重不符。

**臨床情境：**

臨床使用者選擇情境模板（如糖尿病、高血壓）產生測試病人時，期望產生的 FHIR 資源包含模板所列的所有診斷、檢驗和用藥。目前因代碼不匹配，大量資源被靜默過濾，產生的病人資料不完整。

**驗收條件：**

1. conditions.json 全部改用 ICD-10-CM 代碼（system: http://hl7.org/fhir/sid/icd-10-cm）
2. scenarios.json 所有 11 個情境模板的 condition/observation/medication 代碼均能在對應資料檔中找到
3. medications.json 補齊情境模板所需的缺失藥物（Insulin, Furosemide, Tiotropium, Escitalopram）
4. 修正錯誤的 LOINC 代碼（HbA1c: 4548-4→17856-6, 血氧: 20564-1→2710-2）
5. TypeScript 編譯通過



---


### SRS-052 [需求] 匯入失敗錯誤恢復機制

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#138](https://github.com/Lusnaker0730/CQL/issues/138) |
| 建立日期 | 2026-03-25 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

為病人匯入流程建立錯誤恢復機制：
1. **失敗紀錄**：匯入失敗時記錄失敗原因、原始請求參數，不丟失資訊
2. **手動重試**：管理員可對失敗的匯入執行重試
3. **自動重試**：可配置自動重試次數和延遲，使用指數退避策略
4. **失敗管理 API**：查詢失敗清單、重試、刪除過期失敗紀錄

**臨床情境：**

醫院 FHIR 伺服器可能因網路不穩、伺服器負載過高等原因暫時無法回應。失敗的匯入需要能夠在問題解決後重新執行，而非要求使用者手動重新操作。

**驗收條件：**

- [ ] 新增 FailedImportEntity（失敗原因、重試次數、下次重試時間、原始參數）
- [ ] V48 Flyway migration
- [ ] PatientImportService 失敗時自動建立 FailedImport 紀錄
- [ ] 新增 ImportRetryService：手動重試 + 自動重試排程（指數退避）
- [ ] GET /api/ehr/failed-imports（查詢失敗清單）
- [ ] POST /api/ehr/failed-imports/{id}/retry（手動重試）
- [ ] DELETE /api/ehr/failed-imports/{id}（刪除失敗紀錄）
- [ ] 配置項：max-retry-attempts、retry-initial-delay-seconds
- [ ] 單元測試涵蓋失敗紀錄建立、重試邏輯、指數退避計算


**關聯項目：** P0 醫院 FHIR 對接工程


---


### SRS-053 [需求] 非同步批次病人匯入（含進度追蹤）

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#137](https://github.com/Lusnaker0730/CQL/issues/137) |
| 建立日期 | 2026-03-25 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

支援批次匯入多位病人資料，以非同步方式執行並提供進度追蹤：
1. **批次匯入 API**：一次提交多位病人 ID，系統在背景逐一匯入
2. **進度追蹤**：每個批次任務有獨立 ID，可查詢進度（已完成/總數/失敗數）
3. **批次任務管理**：可查詢歷史任務列表、取消進行中的任務
4. **完成通知**：任務完成時記錄結果摘要

**臨床情境：**

品質量測評估需要匯入大量病人資料作為測試案例。目前同步匯入在病人數量較多時會 timeout，需要非同步批次處理。

**驗收條件：**

- [ ] 新增 BatchImportJobEntity（job ID、狀態、進度、結果摘要）
- [ ] V47 Flyway migration
- [ ] 新增 AsyncPatientImportService：非同步批次匯入邏輯
- [ ] POST /api/ehr/connections/{id}/patients/batch-import（提交批次匯入）
- [ ] GET /api/ehr/batch-imports/{jobId}（查詢單一任務進度）
- [ ] GET /api/ehr/batch-imports（查詢任務列表）
- [ ] POST /api/ehr/batch-imports/{jobId}/cancel（取消任務）
- [ ] 單元測試涵蓋批次匯入邏輯、進度追蹤、取消機制


**關聯項目：** P0 醫院 FHIR 對接工程


---


### SRS-054 [需求] FHIR 操作稽核日誌強化（醫療合規）

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#136](https://github.com/Lusnaker0730/CQL/issues/136) |
| 建立日期 | 2026-03-25 |
| 狀態 | closed |
| 風險等級 | 中 |
| 安全性等級 | B — 涉及醫療資料存取追蹤合規 |

**需求描述：**

強化現有稽核日誌系統，增加 FHIR 操作級別的稽核追蹤：
1. **EHR 連線操作稽核**：記錄每次 EHR FHIR 操作的連線 ID、目標病人 ID、操作類型
2. **稽核日誌保留策略**：定時清理超過保留期限的舊日誌（依 `audit.retention-days` 設定）
3. **稽核查詢 API**：管理員可查詢/匯出稽核日誌，支援依時間、使用者、操作類型篩選
4. **EHR 連線變更稽核**：記錄連線建立/修改/刪除/測試操作

**臨床情境：**

醫院評鑑及台灣個資法要求完整的病人資料存取紀錄。管理員需能回答「誰在什麼時候存取了哪位病人的資料」、「從哪台 FHIR 伺服器取得」等問題。

**驗收條件：**

- [ ] audit_log 表新增 connection_id、patient_fhir_id、connection_name 欄位（V46 migration）
- [ ] AuditFilter 在 EHR 相關操作時填入連線 ID 和病人 ID
- [ ] 新增 AuditRetentionService：排程清理超過保留天數的日誌
- [ ] 新增 AuditController：GET /api/admin/audit/logs（分頁+篩選）、GET /api/admin/audit/export（CSV 匯出）
- [ ] 單元測試涵蓋稽核寫入、保留清理、查詢 API
- [ ] 現有稽核功能不受影響（向下相容）


**關聯項目：** P0 醫院 FHIR 對接工程


---


### SRS-055 [需求] TLS/mTLS 安全通訊支援（醫院 FHIR 對接）

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#135](https://github.com/Lusnaker0730/CQL/issues/135) |
| 建立日期 | 2026-03-25 |
| 狀態 | closed |
| 風險等級 | 中 |
| 安全性等級 | B — 涉及醫療資料傳輸安全 |

**需求描述：**

EHR 連線（FHIR Client）需支援 TLS 及 mTLS（雙向憑證驗證），以滿足醫院內網安全通訊要求。每個 EHR 連線可獨立設定：
1. **自訂 Trust Store**：上傳醫院 CA 憑證（PEM 格式），信任醫院自簽憑證
2. **Client Certificate（mTLS）**：上傳客戶端憑證 + 私鑰（PEM 格式），供醫院伺服器驗證本平台身份
3. **TLS 版本控制**：可設定最低 TLS 版本（預設 TLSv1.2）
4. **主機名稱驗證**：預設啟用，可關閉（供測試環境使用）

**臨床情境：**

醫院 FHIR 伺服器通常部署在內部網路，使用自簽 CA 憑證，且要求客戶端提供憑證才能連線。不支援 TLS/mTLS 將無法與絕大多數醫院 FHIR 伺服器建立安全連線。

**驗收條件：**

- [ ] EhrConnectionEntity 新增 TLS 設定欄位（tlsEnabled, caCertPem, clientCertPem, clientKeyPem, tlsMinVersion, hostnameVerification）
- [ ] FhirClientFactory 支援根據 EHR 連線的 TLS 設定建立 SSL Context
- [ ] 憑證以加密形式儲存（EncryptionConverter）
- [ ] 連線測試時使用完整 TLS 設定
- [ ] 支援 PEM 格式憑證解析
- [ ] V45 Flyway migration 新增欄位
- [ ] 單元測試涵蓋 TLS 客戶端建立邏輯
- [ ] 無 TLS 設定時行為不變（向下相容）


**關聯項目：** P0 醫院 FHIR 對接工程


---


### SRS-056 [需求] CQL 預編譯 ELM 快取 — 儲存時翻譯，執行時跳過

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#132](https://github.com/Lusnaker0730/CQL/issues/132) |
| 建立日期 | 2026-03-24 |
| 狀態 | closed |
| 風險等級 | 未指定 |
| 安全性等級 | A |

**需求描述：**

量測評估時 CQL 每位病人都重新翻譯為 ELM（~1.5s/次），造成不必要的效能消耗。應在儲存量測定義時預先翻譯 CQL 為 ELM JSON 並存入資料庫，執行時直接使用預編譯的 ELM。

**臨床情境：**

（未填寫）

**驗收條件：**

- 量測定義儲存時自動翻譯 CQL 為 ELM JSON
- 執行 CQL 時優先使用預編譯 ELM（反序列化 ~10ms），無 ELM 時 fallback 到即時翻譯
- 全部 823 個後端測試通過



---


### SRS-057 [需求] 假病人產生器情境模板產生正確數量 + 期間比較指標自動載入

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#130](https://github.com/Lusnaker0730/CQL/issues/130) |
| 建立日期 | 2026-03-24 |
| 狀態 | closed |
| 風險等級 | 未指定 |
| 安全性等級 | A |

**需求描述：**

1. 假病人產生器的情境模板（糖尿病、高血壓、氣喘）點擊「使用此情境」後僅產生 1 位病人，應依 recommended_patient_count 產生對應數量
2. 品質指標的「期間比較與趨勢」頁面中「指標名稱」為純文字輸入，使用者必須記住指標名稱才能比較。應改為自動載入現有指標的下拉選單

**臨床情境：**

（未填寫）

**驗收條件：**

- 情境模板按下後產生 recommended_patient_count 位病人（例如糖尿病 5 位）
- 期間比較的指標名稱改為 Autocomplete，自動載入所有 MeasureDefinition 的 title



---


### SRS-058 [需求] 量測評估效能優化 — 平行化病人評估

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#127](https://github.com/Lusnaker0730/CQL/issues/127) |
| 建立日期 | 2026-03-24 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

量測評估（evaluate-measure）對病人進行 CQL 評估時為逐一循序執行，11 位病人耗時約 99 秒，超過 Cloudflare 代理的 ~100 秒逾時限制，導致前端收到 504 Gateway Timeout。

**臨床情境：**

使用者在量測頁面執行品質量測評估時，因逾時而無法取得結果。

**驗收條件：**

- 病人 CQL 評估改為平行執行（使用既有的 cqlExecutionExecutor 執行緒池）
- TerminologyProvider 依 server URL 快取，避免每位病人重複建立 FHIR 客戶端
- 整體評估時間降至原先的 1/N（N = 平行度），低於 Cloudflare 逾時限制



---


### SRS-059 [需求] SSE 通知連線 JWT 過期後無法自動刷新

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#126](https://github.com/Lusnaker0730/CQL/issues/126) |
| 建立日期 | 2026-03-24 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A — 通知功能，非臨床關鍵 |

**需求描述：**

SSE 通知連線的 ticket 請求使用原生 `fetch()` 而非 Axios 客戶端，導致 JWT 過期時無法觸發靜默刷新機制，造成每 30 秒重試一次的無限 401 迴圈。

**臨床情境：**

使用者長時間開啟量測頁面時，JWT 過期後 SSE 通知連線中斷且無法自動恢復，瀏覽器控制台持續出現 ERR_QUIC_PROTOCOL_ERROR 錯誤。

**驗收條件：**

- SSE ticket 請求透過 Axios 客戶端發送，享有靜默 JWT 刷新機制
- JWT 過期時自動刷新後重新建立 SSE 連線
- 不再出現無限 401 重試迴圈



---


### SRS-060 [需求] 修復 CVE-2026-33180 HAPI FHIR HTTP 認證洩漏 (BUG-097)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#119](https://github.com/Lusnaker0730/CQL/issues/119) |
| 建立日期 | 2026-03-24 |
| 狀態 | open |
| 風險等級 | 高（CRITICAL CVE） |
| 安全性等級 | B |

**需求描述：**

升級 `org.hl7.fhir.convertors` 從 6.7.9 到 6.9.0，修復 CVE-2026-33180（CRITICAL）：HAPI FHIR 在 HTTP 重導向時洩漏認證資訊。

**臨床情境：**

當後端透過 HAPI FHIR Client 連接外部 FHIR 伺服器時，如果伺服器發出重導向，認證標頭可能洩漏到非預期的目標伺服器。此漏洞影響 EHR 整合功能中的 FHIR 資料提取。

**驗收條件：**

- Trivy 安全掃描不再報告 CVE-2026-33180
- `org.hl7.fhir.convertors` 版本 >= 6.9.0
- 所有後端測試通過



---


### SRS-061 [需求] 修復 CSP 違規與假病人產生器直連 localhost 問題 (BUG-096)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#117](https://github.com/Lusnaker0730/CQL/issues/117) |
| 建立日期 | 2026-03-24 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

修復生產環境 www.twcql.com 上的三個 console 錯誤：
1. CSP 阻擋 Cloudflare Analytics beacon 載入
2. 假病人產生器上傳功能直連 localhost:8090 導致連線失敗
3. CSP connect-src 包含無法從瀏覽器存取的內部網路位址

**臨床情境：**

不影響臨床功能，但 CSP 錯誤會阻擋 Cloudflare 分析，假病人上傳功能在生產環境完全無法使用。

**驗收條件：**

- Cloudflare beacon 正常載入無 CSP 錯誤
- 假病人產生器上傳透過後端 API proxy 正常運作
- CSP connect-src 不包含 localhost 或 Docker 內部位址



---


### SRS-062 [需求] SEO 優化與 FHIR 瀏覽器分頁修復

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#98](https://github.com/Lusnaker0730/CQL/issues/98) |
| 建立日期 | 2026-03-16 |
| 狀態 | closed |
| 風險等級 | 低 |
| 安全性等級 | A — 不影響臨床決策或病人安全 |

**需求描述：**

1. **SEO 優化**：為公開頁面（LandingPage、LearnPage 13 個教學分頁）加入動態 SEO meta tags（title、description、Open Graph、Twitter Card、canonical），並提供 sitemap.xml 讓搜尋引擎正確索引。
2. **FHIR 瀏覽器分頁修復**：修復 FHIR 瀏覽器搜尋結果超過 20 筆時無法翻頁的問題，改為後端自動收集所有分頁結果、前端 client-side 分頁。

**臨床情境：**

不涉及臨床功能。SEO 改善讓教學資源更容易被搜尋引擎發現；FHIR 瀏覽器分頁修復讓使用者能正確瀏覽所有 FHIR 資源。

**驗收條件：**

- [ ] LandingPage 和 LearnPage 各分頁有獨立的 `<title>` 和 `<meta description>`
- [ ] `sitemap.xml` 包含所有公開頁面，Google Search Console 顯示成功
- [ ] FHIR 瀏覽器搜尋超過 20 筆資源時，能正確翻頁顯示所有結果
- [ ] 顯示頁碼指示器（如 1/2）
- [ ] 搜尋結果上限 10,000 筆，超過時截斷並記錄警告日誌



---


### SRS-063 [需求] PAT-044: CQL Monaco 語法從 ANTLR Grammar 自動產生

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#72](https://github.com/Lusnaker0730/CQL/issues/72) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |
| 風險等級 | 低 |
| 安全性等級 | A |

**需求描述：**

Monaco Editor 的 CQL 語法高亮使用手寫的 116 個關鍵字清單（cqlSyntax.ts），與官方 CQL ANTLR grammar (v1.5) 存在偏差：15 個官方關鍵字缺漏、14 個非標準關鍵字被誤加。CQL 是持續更新的標準，手動維護 keyword 清單會在標準更新時產生 drift。

需要建立從官方 ANTLR grammar 自動產生 Monarch token 列表的機制，並在 CI 中檢查 drift。

**臨床情境：**

臨床使用者在編輯 CQL 規則時，缺漏的關鍵字（如 div, mod, implies）不會被語法高亮標記，可能導致混淆或錯誤使用。

**驗收條件：**

1. Codegen 腳本從 cql.g4 的 keyword/reservedWord/typeNameIdentifier 規則提取 token
2. 產出 TypeScript 檔案匯出 CQL_KEYWORDS、CQL_TYPE_KEYWORDS 等陣列
3. cqlSyntax.ts 使用產出檔案取代手寫陣列
4. CI 包含 --check 模式，grammar 更新但未重新產生時失敗
5. TypeScript 編譯通過、所有前端測試通過



---


### SRS-064 [需求] PAT-043: TFDA 法規追溯 CI 強制檢查

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#68](https://github.com/Lusnaker0730/CQL/issues/68) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |
| 風險等級 | 低 — 純 CI/CD 流程改善，不影響應用程式功能 |
| 安全性等級 | A — 不涉及病患安全（開發工具鏈改善） |

**需求描述：**

現有 TFDA 法規追溯工作流僅依賴開發者自律遵守（CLAUDE.md 中「必須遵守」規則），缺乏 CI 自動化防護。當開發者遺漏 PR 中的 Issue 引用或漏建法規文件時，系統不會攔截，法規合規缺漏直到文件產生階段才被發現。

需要建立 GitHub Actions CI 檢查，在 PR 發起時自動驗證法規追溯完整性，不通過則阻擋合併。

**臨床情境：**

TFDA 要求醫療軟體開發過程留下完整的需求→設計→風險→驗證追溯紀錄。若追溯鏈斷裂（如安全性等級 B/C 的功能缺少風險分析），將導致法規審查不通過。CI 強制檢查確保每次程式碼變更都有對應的法規文件。

**驗收條件：**

1. PR 描述未包含 #NNN Issue 引用時，CI 檢查失敗（Block Merge）
2. 安全性等級 B/C 需求缺少設計/風險/驗證 Issue 時，CI 檢查失敗
3. docs: 開頭的 PR 標題自動豁免
4. 需求 Issue 缺少安全性等級標籤時發出警告（不阻擋）
5. 法規 Issue body 缺少 ### 標題格式時發出警告
6. 檢查腳本在 GitHub Actions 環境正常執行



---


### SRS-065 [需求] PAT-042: Monaco Editor 效能優化 — 移除每次按鍵 Redux dispatch

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#65](https://github.com/Lusnaker0730/CQL/issues/65) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |
| 風險等級 | 低 — 純前端效能優化，不影響 CQL 翻譯/執行結果 |
| 安全性等級 | A — 不涉及病患安全 |

**需求描述：**

Monaco Editor 的 onChange 事件在每次按鍵時 dispatch setCqlContent 到 Redux store，導致所有依賴 cqlContent 的元件不必要地 re-render。需要將 Monaco 內部編輯狀態與全局 Redux 狀態解耦，僅在必要時同步（blur、save、execute）。

**臨床情境：**

臨床使用者在編輯 CQL 規則時，每次按鍵觸發全局狀態更新導致 UI 卡頓，影響編輯效率和使用體驗。尤其在複雜 CQL 程式碼（數百行）的情境下，per-keystroke dispatch 造成明顯的輸入延遲。

**驗收條件：**

1. Monaco Editor onChange 不再直接 dispatch setCqlContent 到 Redux
2. 內容同步改為 blur / Ctrl+S / Ctrl+Enter 觸發
3. CqlEditor 暴露 forwardRef handle（getContent / flushContent）供父元件按需讀取
4. useCqlStructure 改為 callback-driven debounce，不再依賴 Redux selector
5. 所有原有功能不受影響（翻譯、儲存、匯出、Builder、執行面板）
6. TypeScript 編譯通過、所有前端測試通過



---


### SRS-066 [需求] 品質指標儀表板應提供視覺化數據分析與閾值警示

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#60](https://github.com/Lusnaker0730/CQL/issues/60) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

系統應提供品質指標儀表板，以圖表方式呈現品質量測執行結果的趨勢分析。支援：Recharts 圖表（折線圖、長條圖、圓餅圖）、品質指標達成率趨勢、科別比較、閾值警示（低於標準時標紅提醒）、時間範圍篩選、品質報告匯出。

**臨床情境：**

品質管理部門需要從宏觀角度監控全院品質指標的達成狀態，快速識別低於閾值的指標並採取改善行動。視覺化儀表板取代傳統的數字報表，提高資訊可讀性。

**驗收條件：**

1. Recharts 圖表正確呈現量測趨勢
2. 科別分組篩選
3. 閾值警示（紅色標示）
4. 時間範圍選擇（月/季/年）
5. 報告下載功能



---


### SRS-067 [需求] 系統應支援繁體中文與英文雙語介面

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#50](https://github.com/Lusnaker0730/CQL/issues/50) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

系統應提供完整的國際化（i18n）支援，包含繁體中文（zh-TW）與英文（en）雙語介面。所有 UI 文字透過 i18n 機制載入，禁止硬編碼。使用者可在 Header 工具列即時切換語言，偏好設定儲存於 localStorage。MUI 元件庫亦隨語言切換。10 個 namespace 涵蓋所有模組。

**臨床情境：**

台灣醫療機構使用者以繁體中文為主要語言，但部分國際合作場景需要英文介面。雙語支援確保所有使用者都能正確理解系統功能與臨床資訊。

**驗收條件：**

1. 所有 UI 文字透過 useTranslation hook 載入
2. Header 語言切換器即時生效
3. MUI locale 同步切換
4. 語言偏好持久化（localStorage）
5. zh-TW 動態載入（bundle optimization）



---


### SRS-068 [需求] 系統應具備完整的輸入驗證與注入攻擊防護

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#45](https://github.com/Lusnaker0730/CQL/issues/45) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 高 (High) |
| 安全性等級 | C - 可能造成死亡或嚴重傷害 |

**需求描述：**

系統應對所有使用者輸入實施完整的安全驗證與防護，包含：XSS（跨站腳本攻擊）三層防護、CQL 注入防護（escapeCqlString）、SQL LIKE 萬用字元注入防護、SSRF URL 驗證、Trojan Source 雙向字元偵測、DTO @Size 限制防 DoS、Mass Assignment 防護、CDS 儲存型 XSS 防護、Monaco 編輯器貼上消毒。

**臨床情境：**

作為處理受保護健康資訊的醫療軟體，系統必須抵禦各類注入攻擊。XSS 攻擊可竊取使用者 session 或顯示誤導性臨床資訊。CQL 注入可能擷取未授權的病患資料。

**驗收條件：**

1. CDS Card XSS 三層防護（前端安全渲染 + 後端 HTML 跳脫 + 反序列化器）
2. CQL 字串跳脫（單引號、反斜線）
3. LIKE 查詢萬用字元跳脫（%, _）
4. DTO @Size 限制所有字串欄位
5. SSRF URL 驗證（僅允許 https）
6. Trojan Source bidi 字元偵測並移除
7. @NoXss 自訂驗證器
8. API Key SHA-256 雜湊儲存



---


### SRS-069 [需求] 系統應支援 EHR/HIS 系統連接與病患資料匯入

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#39](https://github.com/Lusnaker0730/CQL/issues/39) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 高 (High) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

系統應提供 EHR/HIS（電子病歷/醫院資訊系統）整合連接功能，支援：FHIR R4 連線管理（新增/編輯/刪除/測試連線）、病患搜尋（姓名/ID/日期範圍）、病患 FHIR 資料匯入、匯入歷史紀錄、直接匯入至測試案例建構器。連線支援多種認證方式（Bearer Token、Basic Auth、OAuth2）。

**臨床情境：**

品質管理人員需要從醫院 HIS 系統匯入真實病患資料來驗證品質量測邏輯。手動建立測試資料無法反映真實臨床資料的複雜性。EHR 連接器讓使用者直接搜尋並匯入病患的 FHIR 資料，提高測試的真實性。

**驗收條件：**

1. FHIR 連線 CRUD + 連線測試
2. 病患搜尋（多條件組合）
3. 病患 FHIR Bundle 匯入
4. 匯入歷史紀錄查詢
5. 「Import from EHR」一鍵匯入至測試案例
6. 支援 Bearer/Basic/OAuth2 認證



---


### SRS-070 [需求] 測試案例建構器應提供視覺化 FHIR Bundle 編輯與族群驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#38](https://github.com/Lusnaker0730/CQL/issues/38) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 中 (Medium) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

系統應提供測試案例視覺化建構器，讓使用者透過表單介面建立測試用 FHIR Bundle（無需手寫 JSON），設定預期族群結果，並與實際執行結果自動比對。支援：16 種 FHIR 資源表單、必填/選填屬性分組、Reference 自動連結、Visual Builder ↔ JSON 雙向同步、批次匯入與日期平移、預期 vs 實際族群比對。

**臨床情境：**

品質量測驗證需要建立大量測試病患資料。手寫 FHIR Bundle JSON 容易出錯且耗時。視覺化建構器讓品質管理師快速建立測試資料，驗證量測邏輯正確性。

**驗收條件：**

- Visual Builder 表單建立 FHIR 資源
- JSON ↔ Visual 雙向同步
- Reference auto-linking
- 預期族群設定（IP/Denom/Numer/Excl）
- 批次執行比對 expected vs actual
- 測試結果詳細報告（PopulationComparison）



---


### SRS-071 [需求] eCQM 撰寫工具應提供視覺化品質量測定義與 CQL 自動產生

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#37](https://github.com/Lusnaker0730/CQL/issues/37) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 中 (Medium) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

系統應提供 eCQM（Electronic Clinical Quality Measures）視覺化撰寫工具，讓品質管理人員透過表單介面定義品質量測的 Population Criteria（Initial Population、Denominator、Numerator、Exclusion 等），系統自動產生對應的 CQL 語句。支援：外部 CQL 程式庫引用、基礎元素複用、參數定義、FreeMarker 模板驅動的 CQL 產生、發佈前 CQL 驗證、直接發佈至 MeasureDefinition。

**臨床情境：**

品質管理部門需要建立醫療品質量測指標（如：糖尿病 HbA1c 控制率），但直接撰寫 CQL 需要程式技能。視覺化撰寫工具讓品質管理師專注於臨床邏輯，系統自動產生標準 CQL 並發佈為可執行的品質量測。

**驗收條件：**

1. 視覺化定義 Population Criteria
2. 自動產生 CQL（FreeMarker 模板）
3. CQL 預覽面板即時更新
4. 外部 CQL 程式庫上傳/解析/引用
5. 發佈前 CQL 翻譯驗證通過才允許發佈
6. 發佈後自動建立 MeasureDefinition + CQL Library
7. 工作區自動儲存 + Ctrl+S



---


### SRS-072 [需求] CQL Builder 應提供視覺化元件輔助使用者建構 CQL 語句

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#36](https://github.com/Lusnaker0730/CQL/issues/36) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

系統應提供 CQL Builder 視覺化面板，讓不熟悉 CQL 語法的使用者透過表單式介面建構 CQL 語句。包含：Includes（引用庫）、ValueSets（值集）、Codes（代碼，含 FHIR 代碼瀏覽器）、Parameters（參數）、Definitions（定義，含 Query Builder）、Functions（函數）六大區段。一鍵插入產生的 CQL 片段至 Monaco 編輯器。

**臨床情境：**

臨床品質管理師需要建構 CQL 查詢語句，但多數使用者不熟悉 CQL 語法。視覺化建構器降低使用門檻，讓使用者專注於臨床邏輯而非語法細節。

**驗收條件：**

- [ ] 六個區段可展開/收合
- [ ] ValueSet/Code 搜尋與選取
- [ ] Query Builder 支援 with/without/let/distinct
- [ ] 產生的 CQL 片段語法正確
- [ ] 一鍵插入至編輯器游標位置
- [ ] FHIR 代碼瀏覽器可分組瀏覽



---


### SRS-073 [需求] 系統應提供安全的認證授權機制防止未授權存取

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#35](https://github.com/Lusnaker0730/CQL/issues/35) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 高 (High) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

系統應實作完整的認證授權機制，包含：JWT 雙令牌架構（Access Token + Refresh Token）、令牌滑動視窗過期、令牌輪換與重用偵測、API Key 認證、停用使用者即時失效、IP 分級限流、大型 Payload 加權限流。

**臨床情境：**

醫療資訊系統存取受保護健康資訊（PHI），必須確保只有經授權的使用者才能存取系統功能。未授權存取可能導致病患隱私外洩或資料竄改。

**驗收條件：**

- [ ] JWT Access Token 有效期 15 分鐘，Refresh Token 滑動視窗 7 天
- [ ] Refresh Token 輪換機制：每次刷新產生新 token，舊 token 立即失效
- [ ] 偵測 Refresh Token 重用時撤銷該使用者所有 token
- [ ] 停用使用者的 API Key 應立即失效，無法繼續存取
- [ ] API 限流：登入 5次/分鐘、一般 API 60次/分鐘、CQL 執行 10次/分鐘
- [ ] CORS 配置拒絕萬用字元 origin



---


### SRS-074 [需求] CDS Hooks 應根據臨床決策規則產生建議卡片

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#25](https://github.com/Lusnaker0730/CQL/issues/25) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 高 (High) |
| 安全性等級 | C - 可能造成死亡或嚴重傷害 |

**需求描述：**

CDS Hooks 服務應能根據已部署的臨床決策支援（CDS）規則，在收到 FHIR 資料時自動評估並產生建議卡片（CDS Cards）。卡片內容應包含建議文字、指標來源、嚴重等級。

**臨床情境：**

臨床醫師在開立醫囑時，系統即時觸發 CDS Hooks，根據病患的 FHIR 資料（藥物、檢驗、診斷等）評估潛在風險並提供臨床建議。例如：藥物交互作用警示、高出血風險提醒。

**驗收條件：**

- [ ] 正確接收 CDS Hooks 請求（patient-view、order-sign 等）
- [ ] 根據 Artifact 定義的 Inclusions/Exclusions 評估病患
- [ ] 產生正確格式的 CDS Card（符合 CDS Hooks 規範）
- [ ] 建議卡片包含 summary、detail、indicator、source
- [ ] 支援多條件 Subpopulation 分支建議



---


### SRS-075 [需求] 品質量測執行結果應正確計算病患族群

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#24](https://github.com/Lusnaker0730/CQL/issues/24) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 中 (Medium) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

品質量測（Measure）執行引擎應根據 CQL 定義的 Population Criteria 正確識別並分類病患，包括 Initial Population、Denominator、Numerator、Exclusion 等族群。計算結果應與手動驗算一致。

**臨床情境：**

品質管理部門執行醫療品質指標評估時，系統需要正確計算各族群人數。若計算錯誤，可能導致品質指標報告不準確，影響醫療機構的品質改善決策。

**驗收條件：**

- [ ] Initial Population 正確識別符合條件的病患
- [ ] Denominator/Numerator 正確分類
- [ ] Exclusion 條件正確排除不符合的病患
- [ ] 計算結果與測試案例預期值一致
- [ ] 支援 proportion、continuous-variable、cohort 三種量測類型



---


### SRS-076 [需求] CQL 翻譯服務應在 3 秒內完成回應

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#23](https://github.com/Lusnaker0730/CQL/issues/23) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

系統應能在 3 秒內完成 CQL 語句的翻譯（translate）並回傳 ELM JSON 結果。當翻譯時間超過 3 秒時，應顯示進度指示器。

**臨床情境：**

臨床品質管理師在編輯 CQL 語句時，需要即時翻譯回饋來確認語法正確性。過長的等待時間會影響工作效率，導致使用者放棄使用系統而改用手動方式。

**驗收條件：**

- [ ] 一般 CQL 語句（< 100 行）翻譯時間 < 3 秒
- [ ] 翻譯過程中顯示 loading 指示器
- [ ] 翻譯失敗時顯示明確的錯誤訊息
- [ ] API 回傳包含完整的 ELM JSON 結構



---



## 4. 需求統計

| 統計項目 | 數量 |
|---------|------|
| 總需求數 | 76 |
| 開放中 | 61 |
| 已關閉 | 15 |

---

*本文件由 CQL Platform 法規文件產生器自動產生*
