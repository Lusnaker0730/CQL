# 軟體設計規格書 (Software Design Specification)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-SDS-1.0.0 |
| 版本 | 1.0.0 |
| 日期 | 2026-03-13 |
| 產品名稱 | CQL Platform — 臨床品質語言視覺化編輯與執行平台 |
| 審核者 | _________________ |
| 核准者 | _________________ |

## 修訂歷史

| 版本 | 日期 | 變更說明 | 作者 |
|------|------|---------|------|
| 1.0.0 | 2026-03-13 | 自動產生 | 系統 |

---

## 1. 目的

本文件定義 CQL Platform 的軟體設計規格，依據 IEC 62304 醫療器材軟體生命週期流程標準。

## 2. 系統架構概述

CQL Platform 採用前後端分離架構：

- **後端**：Spring Boot 3.2 / Java 21 / PostgreSQL
- **前端**：React 18 / TypeScript / Vite / MUI 5
- **CQL 引擎**：CQL Framework + HAPI FHIR

## 3. 軟體設計項目


### SDS-001 [設計] 多層輸入驗證與注入防護架構

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#51](https://github.com/Lusnaker0730/CQL/issues/51) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |

**設計方案：**

採用四層防禦縱深架構：

1. **DTO Layer（輸入層）**：@Size 限制所有字串欄位（防 DoS），@Pattern 格式約束，@NoXss 自訂驗證器（基於正則的 HTML 標籤偵測），SSRF URL 驗證（僅允許 https 白名單）

2. **Service Layer（服務層）**：escapeCqlString() 進行 CQL 字串跳脫（單引號 → 雙單引號），LIKE 萬用字元跳脫（%, _, \），HtmlUtils.htmlEscape() 處理 CDS 回饋與卡片內容

3. **Controller Layer（控制層）**：Mass Assignment 防護（明確欄位映射，不直接綁定實體），IDOR 檢查（所有資源存取均呼叫 verifyOwnership）

4. **Frontend Layer（前端層）**：Monaco 貼上消毒（bidi 字元移除，undo-safe executeEdits），CDS Card 安全渲染（dangerouslySetInnerHTML 須搭配 escapeValue），React 預設 XSS 防護

**架構影響：**

- Backend：`validation/`、`entity/`、`controller/`、`service/`
- Frontend：`components/editor/`、`components/cds/`

**關聯需求：** #45

**安全考量：**

Defense-in-depth：即使某一層防護失效，其他層仍可阻止攻擊被利用。escapeCqlString 目前分別定義於 CdsCardBuilder.tsx 與 RecommendationBuilder.tsx（待統一）。AuditFilter 記錄所有 PHI 存取行為，供事後追查。



---


### SDS-002 [設計] EHR FHIR R4 連接器與病患資料匯入架構

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#44](https://github.com/Lusnaker0730/CQL/issues/44) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |

**設計方案：**

3 backend services: EhrConnectionService (CRUD + connection test), PatientSearchService (FHIR search with multiple criteria), PatientImportService (fetch patient bundle + store import record). 10 REST endpoints on EhrIntegrationController. FhirClientFactory with ConcurrentHashMap cache (10-min TTL). Frontend: 6 components for connection management and patient search/import. V28 Flyway migration for ehr_connection and patient_import tables.

**架構影響：**

- backend service/fhir/EhrConnectionService, PatientSearchService, PatientImportService
- controller/EhrIntegrationController
- entity/EhrConnectionEntity, PatientImportEntity
- frontend components/ehr/ (6 components): EhrConnectionList, EhrConnectionForm, PatientSearchPanel, PatientImportDialog, PatientImportHistory, EhrImportForTestCase
- FhirPage 第 3 個分頁（EHR Connections）

**關聯需求：** #39

**安全考量：**

- EHR credentials encrypted at rest (AES)
- Connection tokens not logged or exposed in API responses
- FHIR client timeout configured to prevent hanging
- Patient data only stored as import records (metadata), actual FHIR data passed through to test cases



---


### SDS-003 [設計] JWT 雙令牌架構與分級限流設計

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#43](https://github.com/Lusnaker0730/CQL/issues/43) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |

**設計方案：**

認證架構採用 JWT 雙令牌設計：

1. **Access Token**（短期，15分鐘）— 每次 API 請求攜帶，JwtAuthenticationFilter 驗證
2. **Refresh Token**（長期，滑動視窗 7天）— 儲存於 HttpOnly Cookie，用於刷新 Access Token
3. **令牌輪換**：刷新時產生新 Refresh Token，舊 token 加入黑名單
4. **重用偵測**：若偵測到已撤銷的 Refresh Token 被使用，撤銷該使用者所有 token（表示令牌可能被盜）

限流架構採用分級設計：
- IP 層：`RateLimitFilter` 依端點分級（auth: 5/min, api: 60/min, cql-execute: 10/min）
- 使用者層：`UserRateLimitFilter` 依認證使用者限流
- Payload 加權：大型請求（如 CQL 翻譯）消耗多個令牌

API Key 認證：
- SHA-256 雜湊儲存，比對時使用 timing-safe 比較
- 查詢時同時檢查使用者啟用狀態（JOIN user WHERE enabled=true）

**架構影響：**

影響模組：
- `backend/src/main/java/com/cqlplatform/security/` — JWT 過濾器、令牌服務
- `backend/src/main/java/com/cqlplatform/config/SecurityConfig.java` — Spring Security 配置
- `backend/src/main/java/com/cqlplatform/config/CorsConfig.java` — CORS 配置
- `frontend/src/api/client.ts` — Axios 攔截器（自動刷新 token）

**關聯需求：** 需求 #35

**安全考量：**

- Refresh Token 儲存於 HttpOnly + Secure + SameSite=Strict Cookie，防止 XSS 竊取
- Access Token 不含敏感資訊（僅 userId、roles）
- 限流計數器使用 ConcurrentHashMap + 定期清理，避免記憶體洩漏
- CORS 明確列舉允許的 origin，拒絕萬用字元



---


### SDS-004 [設計] eCQM FreeMarker 模板驅動 CQL 產生引擎

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#42](https://github.com/Lusnaker0730/CQL/issues/42) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |

**設計方案：**

CqlArtifactBuilder 從 Artifact JSON 組裝 context Map → CqlTemplateEngine 呼叫 FreeMarker 模板（artifact.ftl、ecqm-artifact.ftl）→ 產生 CQL 字串。EcqmArtifactService 管理工作區 CRUD 與自動儲存。ExpressionCqlEngine 處理表達式樹 → CQL 轉換。模板目錄包含 30 個 FreeMarker 檔案（modifiers/、elements/、fragments/）。發佈前驗證：CqlTranslationService.translate() 必須回傳 0 個錯誤。

**架構影響：**

backend service/authoring/、service/ecqm/、templates/cql/；frontend components/ecqm/

**關聯需求：** #37

**安全考量：**

使用 HtmlUtils 對所有使用者輸入進行 XSS 偵測。結構驗證（元素類型白名單、修飾器驗證、名稱唯一性）。發佈前 CQL 驗證防止發佈有問題的量測。JPA @Version 樂觀鎖定防止並發編輯衝突。



---


### SDS-005 [設計] CQL Builder 手風琴式面板與單向插入架構

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#41](https://github.com/Lusnaker0730/CQL/issues/41) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |

**設計方案：**

CqlBuilderPanel 作為手風琴容器，包含六個區段（Includes、ValueSets、Codes、Parameters、Definitions、Functions）。useCqlStructure hook 透過防抖（debounce）呼叫翻譯 API，從回傳的 metadata 中提取現有定義。單向插入架構：Builder 產生 CQL 片段後，透過 Redux（setCursorInsert action）將片段插入至編輯器游標位置。Query Builder 使用遞迴式 ExpressionBuilder 構建 where 子句。FHIR 代碼瀏覽器透過 CODE_SYSTEM_GROUPS 分組展示，以手風琴方式呈現。

**架構影響：**

- 元件目錄：`frontend/src/components/builder/`（CqlBuilderPanel、IncludesSection、ValueSetSection、CodesSection、ParametersSection、DefinitionsSection、FunctionsSection）
- 自訂 Hook：`hooks/useCqlStructure`（防抖翻譯 API 呼叫 + metadata 提取）
- Redux Store：`store/editorSlice`（setCursorInsert action 處理游標插入）

**關聯需求：** 需求 #36

**安全考量：**

CQL 片段產生使用模板字串並搭配正確的跳脫處理（escapeCqlString）。不允許使用者輸入直接串接至 CQL 中。Builder 輸出為唯讀文字插入，不修改現有編輯器內容。



---


### SDS-006 [設計] FHIR Bundle 視覺化建構器與測試案例執行引擎

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#40](https://github.com/Lusnaker0730/CQL/issues/40) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |

**設計方案：**

BundleBuilderContext (useReducer) manages resource entries. FhirStructureDefinitionService uses HAPI RuntimeResourceDefinition to introspect R4 elements (max depth 3, ConcurrentHashMap cache). 16 field components (PrimitiveField, CodeField, CodeableConceptField, PeriodField, QuantityField, HumanNameField, etc.). TestCaseService executes CQL against in-memory Bundle (not FHIR server), with Measurement Period parameter. TestCaseRunResult with PopulationComparison for expected vs actual.

**架構影響：**

backend service/fhir/FhirStructureDefinitionService, service/measure/TestCaseService; frontend components/testcase-builder/ (16 components), components/measure/TestCaseEditor

**關聯需求：** #38

**安全考量：**

Test case execution uses in-memory FHIR data only (no external server calls). Bundle validation ensures valid FHIR resource types. CQL execution sandboxed with timeout.



---


### SDS-007 [設計] CDS Hooks 評估流程與卡片產生器

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#28](https://github.com/Lusnaker0730/CQL/issues/28) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |

**設計方案：**

CDS Hooks 評估流程：
1. 接收 CDS Hooks 請求，解析 hook type 與 context
2. 查詢已部署且啟用的 CDS Artifact
3. 載入 Artifact 對應的 CQL Library
4. 將請求中的 FHIR 資料作為 CQL 執行的 DataProvider
5. 執行 Inclusions/Exclusions CQL，判斷病患是否適用
6. 根據 Subpopulations 匹配建議分支
7. 透過 `CdsCardBuilder` 產生 CDS Card

關鍵類別：
- `CdsHooksController` — 接收 CDS Hooks 請求
- `CdsEvaluationService` — 協調評估流程
- `CdsCardBuilder` — 組裝 CDS Card JSON
- `ArtifactTestingService` — 測試模式評估

**架構影響：**

影響模組：
- `backend/src/main/java/com/cqlplatform/service/cds/` — CDS 評估服務
- `backend/src/main/java/com/cqlplatform/controller/CdsController.java` — Hooks endpoint
- `frontend/src/components/authoring/testing/ArtifactTester.tsx` — 測試介面
- `frontend/src/components/cds/` — CDS 管理介面

**關聯需求：** 需求 #25

**安全考量：**

- CDS 建議卡片必須標明「僅供參考」，不直接替代醫師判斷
- 評估失敗時回傳空卡片陣列（graceful degradation），不中斷臨床流程
- Artifact 部署前需通過測試驗證
- 記錄每次 CDS 評估的 audit log（病患 ID、Artifact ID、結果）


**介面描述：**

```
POST /cds-services/{serviceId}
Request: CDS Hooks 標準格式（hook, hookInstance, context, prefetch）
Response: { "cards": [{ "summary": "...", "indicator": "warning", ... }] }
```


---


### SDS-008 [設計] 品質量測評估引擎 Orchestrator 模式

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#27](https://github.com/Lusnaker0730/CQL/issues/27) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |

**設計方案：**

品質量測評估採用 Orchestrator 模式，將整體流程拆解為：
1. `PatientDiscoveryService` — 從 FHIR 伺服器取得病患清單
2. `PopulationEvaluator` — 針對每位病患執行 CQL，判斷所屬族群
3. `StratifierEvaluator` — 依分層條件（如年齡、性別）進一步分組
4. `MeasureScoreCalculator` — 根據量測類型計算分數
5. `MeasureEvaluationContext` — 共享上下文（FHIR 連線、CQL Library 等）

主要進入點：`MeasureEvaluationService.evaluate()` 協調上述服務。

**架構影響：**

影響模組：
- `backend/src/main/java/com/cqlplatform/service/measure/` — 5 個新服務類別
- `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` — 評估 endpoint
- `frontend/src/components/measure/MeasureEvaluationTab.tsx` — 評估結果顯示

**關聯需求：** 需求 #24

**安全考量：**

- 病患資料僅在記憶體中處理，不持久化原始 FHIR 資料
- 評估過程中的 CQL 執行使用沙箱環境，限制可存取的 FHIR 資源類型
- 大量病患評估時採用分批處理，避免記憶體溢出


**介面描述：**

```
POST /api/measures/{id}/evaluate
Request: { "periodStart": "2025-01-01", "periodEnd": "2025-12-31" }
Response: MeasureReport (FHIR R4 格式)
```


---


### SDS-009 [設計] CQL 翻譯服務非同步處理架構

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#26](https://github.com/Lusnaker0730/CQL/issues/26) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |

**設計方案：**

採用 Spring @Async 機制搭配 CompletableFuture，將 CQL 翻譯作業移至獨立執行緒池處理。前端使用 React Query 的 mutation 搭配 loading state 顯示翻譯進度。

主要元件：
- `CqlTranslationService.translateAsync()` — 非同步翻譯入口
- `AsyncConfig` — 自訂執行緒池（核心 4 / 最大 8 / 佇列 100）
- 前端 `useCqlTranslation` hook — 封裝 mutation + loading + error 狀態

**架構影響：**

影響模組：
- `backend/src/main/java/com/cqlplatform/service/cql/CqlTranslationService.java` — 新增 async 方法
- `backend/src/main/java/com/cqlplatform/config/AsyncConfig.java` — 執行緒池設定
- `frontend/src/hooks/useCqlEditor.ts` — 翻譯呼叫改用 mutation
- `frontend/src/components/editor/` — 加入 loading indicator

**關聯需求：** 需求 #23

**安全考量：**

- 執行緒池佇列滿時拋出 RejectedExecutionException，前端顯示「伺服器忙碌」提示
- 翻譯 timeout 設定為 10 秒，避免無限等待
- 每個翻譯請求獨立隔離，不共享可變狀態


**介面描述：**

```
POST /api/cql/translate
Request: { "cql": "string", "options": {...} }
Response: { "elm": {...}, "errors": [...], "warnings": [...] }
```


---



## 4. 設計統計

| 統計項目 | 數量 |
|---------|------|
| 總設計項目 | 9 |
| 開放中 | 9 |
| 已關閉 | 0 |

---

*本文件由 CQL Platform 法規文件產生器自動產生*
