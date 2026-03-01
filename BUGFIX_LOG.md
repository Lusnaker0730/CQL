# Bug Fix Log

> 每次 Bug 修正的詳細記錄，包含時間、分類、根因分析、修正方式與測試驗證。

---

## 總覽索引

| # | 日期 | 嚴重程度 | 分類 | 標題 | 根因類型 | Commit |
|---|------|----------|------|------|----------|--------|
| 058 | 2026-03-02 | Medium | 安全性（後端） | Repository 層簡化 — 18 個死碼方法移除、LIKE 萬用字元注入修復、共用工具提取 | 程式碼品質 / 安全漏洞 | |
| 057 | 2026-03-02 | High | 安全性（後端） | Model DTO 驗證強化 — @Size 防 DoS、SSRF URL 驗證、@Pattern 約束、死碼清除 | 安全漏洞 / 程式碼品質 | |
| 056 | 2026-03-02 | Low | 規則撰寫（前端） | CQL 預覽對話框程式碼文字在淺色模式下幾乎不可見 | UX 設計缺陷 | |
| 055 | 2026-03-01 | High | 安全性（後端） | Controller 輸入驗證強化 — require* helpers、Math.clamp、URI 安全、DigestUtils 抽取 | 安全漏洞 / 程式碼品質 | |
| 054 | 2026-03-01 | Critical | 安全性（後端） | Entity 安全強化 — mass assignment 防護、密碼/金鑰洩漏、API key SHA-256 雜湊、憑證加密 | 安全漏洞 | |
| 053 | 2026-03-01 | High | 認證系統（後端） | AuthController 安全強化 — SSO 錯誤訊息洩漏、JIT 競態條件、base URL header 信任 | 安全漏洞 | |
| 052 | 2026-03-01 | Medium | 規則撰寫（前端） | CDS 人工製品表格欄位錯位 — react-window 獨立 Table 未共享欄寬 | UX 設計缺陷 | |
| 051 | 2026-03-01 | High | Docker 基礎設施（後端） | 外部連線 CORS 被擋 + PNA header 未覆蓋動態 origin | 配置遺漏 / 邏輯錯誤 | |
| 050 | 2026-02-27 | Medium | CDS Hooks Sandbox（後端） | CDS 卡片 CodeableConcept 多 coding 只顯示第一個 | 邏輯錯誤 | [`aed0ecb`](../../commit/aed0ecb) |
| 049 | 2026-02-27 | High | CDS Hooks Sandbox（後端） | CDS 卡片僅顯示資源參考而非過敏藥物名稱 | 邏輯錯誤 | [`8b67eb6`](../../commit/8b67eb6) |
| 048 | 2026-02-27 | High | 術語查詢（後端） | RxNorm 代碼搜尋無結果 — FHIR 術語伺服器未載入 UMLS 授權碼表 | 外部服務限制 | [`fe50e2a`](../../commit/fe50e2a) |
| 047 | 2026-02-27 | High | Monaco 編輯器（前端） | Fallback paste handler 非同步讀取 clipboardData 導致貼上失效 | 邏輯錯誤 | [`be3c6ce`](../../commit/be3c6ce) |
| 046 | 2026-02-25 | High | Monaco 編輯器（Docker） | Docker 環境下 Monaco Editor 無法貼上（Ctrl+V 無效） | 配置遺漏 | [`ae9a0e3`](../../commit/ae9a0e3) |
| 045 | 2026-02-24 | Medium | 術語查詢（後端） | 代碼查詢本地 TWCORE IG 回傳 ValueSet 名稱且缺少 display name | 資料處理錯誤 | [`d5e150d`](../../commit/d5e150d) |
| 044 | 2026-02-24 | High | CQL Builder（前端） | Retrieve Builder 依賴不存在的 C3F 外部函式庫致 CQL 無法解析 | 架構缺陷 | [`d5e150d`](../../commit/d5e150d) |
| 043 | 2026-02-24 | High | Test Cases（前端） | TestCaseEditor expectedPopulations 被 React Query refetch 競態重置 | 架構缺陷 | [`5b09697`](../../commit/5b09697) |
| 042 | 2026-02-23 | Critical | CQL Engine（後端） | ComparableR4FhirModelResolver 日期轉換破壞 FHIRHelpers 時態運算子 | 架構缺陷 | [`6534790`](../../commit/6534790) |
| 041 | 2026-02-23 | High | CQL Engine（後端） | Encounter.class Java 保留字衝突致 CQL 路徑解析錯誤 | 邏輯錯誤 | [`6534790`](../../commit/6534790) |
| 040 | 2026-02-23 | High | Test Cases（後端） | TestCaseService 缺少 Measurement Period 參數致時間過濾失效 | 配置遺漏 | [`6534790`](../../commit/6534790) |
| 039 | 2026-02-23 | Critical | Test Cases（後端） | TestCaseService 查詢 FHIR Server 而非使用記憶體內測試 Bundle | 架構缺陷 | [`6534790`](../../commit/6534790) |
| 038 | 2026-02-23 | Critical | 資料庫連線池（後端） | HikariCP 連線池耗盡導致所有 API 逾時、無法登入 | 配置遺漏 | [`ccdf3f2`](../../commit/ccdf3f2) |
| 037 | 2026-02-23 | High | 術語查詢（前端） | Autocomplete freeSolo 以顯示標籤覆蓋系統 URL 致術語查詢 503 | 邏輯錯誤 | [`ccdf3f2`](../../commit/ccdf3f2) |
| 036 | 2026-02-23 | Medium | 指標庫表格（前端） | MeasureLibrary 虛擬捲動表頭與內容欄位錯位擠壓 | UX 設計缺陷 | [`ccdf3f2`](../../commit/ccdf3f2) |
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
| 外部服務限制 | 第三方服務不支援所需功能或資料 |

---

## #058 — Repository 層簡化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 程式碼品質 / 安全漏洞 |
| **影響範圍** | 7 個 Repository、`AuditLogSpecification`、`InputValidator`、`MeasureDefinitionService` |

### BUG 描述

全面審計 25 個 Spring Data JPA Repository 後，發現三類問題：

1. **死碼**（18 個未使用方法）：多個 Repository 宣告了從未被任何 Service 或 Controller 呼叫的查詢方法。部分為無分頁的 `List<>` 查詢（如 `AuditLogRepository.findByCreatedAtAfterOrderByCreatedAtDesc`），若被誤用可載入數百萬筆記錄導致 OOM。`UserRecentRepository.deleteOldestBeyondLimit` 使用非標準 JPQL `LIMIT` 子查詢，存在跨資料庫相容性風險。
2. **LIKE 萬用字元注入**（MEDIUM）：`MeasureDefinitionRepository.findByDepartmentAndSearchTerm` 使用 `CONCAT('%', :search, '%')` 構建 LIKE 模式，使用者輸入 `%` 可匹配所有列、輸入 `_` 可匹配任意單字元。
3. **工具重複**：`AuditLogSpecification.escapeLikeWildcards()` 為 private 方法，其他需要 LIKE 跳脫的程式碼無法共用。

### 修正方式

1. **死碼移除**：從 7 個 Repository 移除 18 個未使用方法：
   - `AuditLogRepository`：3 個無分頁查詢（已被 Specification 方式取代）
   - `CdsServiceConfigRepository`：5 個（已被 `WithPrefetch` 版本取代）
   - `CdsArtifactRepository`：3 個
   - `CqlLibraryRepository`：1 個（`findByAccessLevel`，已被 `findSharedWithUser` 取代）
   - `MeasureDefinitionRepository`：2 個（`findByStatus`、`findByAccessLevel`）
   - `NotificationRepository`：1 個（無限制版，已被 `findTop50` 取代）
   - `CdsFeedbackRepository`：1 個（無排序版，已被排序版取代）
   - `UserRecentRepository`：1 個（`deleteOldestBeyondLimit`，未使用且 JPQL 非標準）

2. **LIKE 跳脫**：
   - `InputValidator` 新增 `escapeLikeWildcards(String)` 公用方法
   - `MeasureDefinitionService.search()` 在呼叫 `findByDepartmentAndSearchTerm` 前跳脫搜尋字串
   - `AuditLogSpecification` 改用共用的 `InputValidator.escapeLikeWildcards()`

### 驗證

- IDE 診斷 0 errors、0 warnings（所有修改檔案）
- 搜尋確認所有移除的方法在 Service、Controller、Test 中均無引用
- LIKE 跳脫確保 `%`、`_` 字元被正確轉義為 `\%`、`\_`

---

## #057 — Model DTO 驗證強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 / 程式碼品質 |
| **影響範圍** | ~35 個 model DTO 與 4 個 controller |

### BUG 描述

全面審計 78 個 Model DTO 後，發現多層安全與品質問題：

1. **SSRF 風險**（CRITICAL）：`CdsRequest.fhirServer`、`CqlExecutionRequest.fhirServerUrl` 無 URL 驗證，攻擊者可探測內部網路。`AuthoringController` 有獨立的 29 行 `validateFhirServerUrl()` 與 `InputValidator.requireValidUrl()` 行為不一致（Docker hostname 判斷邏輯分歧）。
2. **DoS — 無限字串**（HIGH）：CQL 內容（7 處）、FHIR JSON（4 處）、prefetch JSON 等大型文字欄位無 `@Size` 限制，攻擊者可傳入 GB 級 payload 造成 OOM。
3. **Auth DTO 驗證缺失**（HIGH）：`RegisterRequest.email` 無 `@NotBlank`/`@Email`、`LoginRequest` 欄位無 `@Size`、`AdminCreateUserRequest` 密碼最小長度僅 6（其他處為 8）、`OktaCallbackRequest.code` 無長度限制。
4. **CDS/Measure DTO 驗證缺失**（MEDIUM）：`CdsFeedbackRequest.outcome` 無 `@Pattern`、集合欄位無 `@Size` 上限、`BatchTestCaseImportRequest` 無 `@NotNull`/`@Valid`（且 controller 缺 `@Valid` 註解）。
5. **死碼 / 品質問題**（LOW）：4 個 request DTO 含未使用的 `currentUser` 欄位、`FormTemplate.extendsTemplate` 缺 `@JsonProperty("extends")` 導致模板繼承靜默失敗、`ValidationReport.finalize()` 遮蔽 `Object.finalize()`。
6. **正規表達式尾隨 `|`**（LOW）：3 處 `@Pattern` regex 以 `|` 結尾，允許空字串但 `@Pattern` 對 null 本就通過驗證，語意不清。

### 修正方式

1. **SSRF 防護**：
   - `CdsRequest.fhirServer` 加 `@Size(max=500)`、`CqlExecutionRequest.fhirServerUrl` 加 `@Size(max=500)`
   - `CdsHooksController`（2 處）、`CqlController`（1 處）加入 `InputValidator.requireValidUrl()` 呼叫
   - `AuthoringController.validateFhirServerUrl()` 29 行重複方法刪除，改用 `InputValidator.requireValidUrl()`

2. **@Size 補全 — CQL/JSON 欄位**：
   - CQL 內容欄位統一加 `@Size(max=512_000)`（7 處）
   - FHIR JSON/Bundle 欄位統一加 `@Size(max=2_097_152)`（4 處）
   - 其他 string/list 欄位依語意加上適當 `@Size`

3. **Auth DTO 驗證**：`RegisterRequest` 加 `@NotBlank @Email @Size @NoXss`、`LoginRequest` 加 `@Size`、`AdminCreateUserRequest` 密碼最小長度 6→8 + `@Email`、`OktaCallbackRequest`/`ForgotPasswordRequest`/`ResetPasswordRequest` 加 `@Size`

4. **CDS/Measure DTO 驗證**：`CdsFeedbackRequest.outcome` 加 `@Pattern(regexp="accepted|overridden")`、所有集合加 `@Size` 上限、`CdsSandboxRequest.context` 加 `@Valid` 級聯驗證、`BatchTestCaseImportRequest` 加 `@NotNull @Valid @Size` + `MeasureController` 加 `@Valid`

5. **品質修正**：
   - 4 個 DTO 移除死碼 `currentUser` 欄位
   - `FormTemplate` 加 `@JsonProperty("extends")`
   - `ValidationReport.finalize()` 更名為 `complete()`（含 2 處呼叫端更新）
   - `ApiKeyCreateRequest.name` 加 `@NotBlank`
   - 3 處 `@Pattern` regex 移除尾隨 `|`

### 驗證

- IDE 診斷 0 errors、0 warnings（所有修改檔案）
- 所有 `@Size` 限制在 DoS 防護（512KB CQL / 2MB JSON）與實際使用場景之間取得平衡
- `@Valid` 級聯確保巢狀 DTO 的驗證也被觸發
- `AuthoringController` SSRF 邏輯統一為 `InputValidator.requireValidUrl()`，消除行為分歧

---

## #056 — CQL 預覽對話框文字顏色修正

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 規則撰寫（前端） |
| **嚴重程度** | Low |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/authoring/ArtifactWorkspaceHeader.tsx` |

### BUG 描述

Authoring 功能的「產生的 CQL」預覽對話框中，程式碼文字使用硬編碼顏色 `#d4d4d4`（淺灰色），在深色模式的 `#1e1e1e` 背景下清晰可見，但在淺色模式的 `#f5f5f5` 背景下幾乎不可辨識（淺灰色文字在淺灰色背景上）。

### 修正方式

將 `ArtifactWorkspaceHeader.tsx` 第 353 行的 `color: '#d4d4d4'` 改為 theme-aware 條件式：
```tsx
color: (theme) => theme.palette.mode === 'dark' ? '#d4d4d4' : '#1e1e1e'
```

### 驗證

- 深色模式：文字 `#d4d4d4` 在 `#1e1e1e` 背景上清晰可見
- 淺色模式：文字 `#1e1e1e` 在 `#f5f5f5` 背景上清晰可見

---

## #055 — Controller 輸入驗證強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 / 程式碼品質 |
| **影響範圍** | `backend/.../controller/FhirController.java`, `backend/.../controller/MeasureController.java`, `backend/.../security/InputValidator.java`, `backend/.../util/DigestUtils.java`, `backend/.../service/PasswordResetService.java` |

### BUG 描述

FhirController 與 MeasureController 存在多項輸入驗證與程式碼品質問題：

1. **手動驗證散落各處**（HIGH）：9 處 `isValidUrl()` 檢查分散在 FhirController 中，每處都是 3 行 if-throw 樣板，容易遺漏且不一致。
2. **URI.create() 未捕獲異常**（MEDIUM）：`URI.create(url)` 拋出 unchecked `IllegalArgumentException`，繞過 VSAC 錯誤處理邏輯。
3. **DNS 查詢無快取**（MEDIUM）：`isValidUrl()` 中 `isLocalDevelopment()` 每次呼叫都重新檢查系統屬性，而該值在 JVM 生命週期中不變。
4. **SHA-256 重複實作**：`UserEntity.computeEmailHash()`、`UserApiKeyService.hashApiKey()`、`PasswordResetService.sha256()` 三處各自實作相同的 SHA-256 邏輯。
5. **分頁大小未設上限**（MEDIUM）：`MeasureController.listMeasures` 的 `size` 參數無上限，攻擊者可傳入極大值造成 OOM。

### 修正方式

1. **require* 一行呼叫**：在 `InputValidator` 新增 `requireValidUrl()`、`requireValidFhirResourceType()`、`requireValidResourceId()`、`requireValidCacheName()`、`requireValidSearchParams()` 五個 throwing helper，將 FhirController 中 ~60 行 if-throw 區塊替換為一行呼叫。
2. **URI 安全**：`URI.create(url)` 改為 `new URI(url)` + `catch URISyntaxException`，確保格式異常走正確的錯誤處理路徑。
3. **快取 isLocalDevelopment()**：將計算結果存入 `static final Boolean IS_LOCAL_DEV`，避免重複查詢系統屬性。
4. **DigestUtils 抽取**：新建 `com.cqlplatform.util.DigestUtils.sha256Hex()`，使用 Java 21 `HexFormat`，替換三處重複實作。
5. **Math.clamp (Java 21)**：FhirController 3 處、MeasureController 2 處 `Math.max(min, Math.min(val, max))` 改為 `Math.clamp(val, min, max)`。`listMeasures` 的 `size` 參數加上 `Math.clamp(size, 1, 200)` 上限。

### 驗證

- 所有 `require*` 呼叫在參數不合法時正確拋出 `IllegalArgumentException`
- 畸形 URI 不再導致 uncaught exception
- `listMeasures` size 參數被限制在 1–200
- IDE 零診斷（0 errors, 0 warnings）

---

## #054 — Entity 安全強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 安全漏洞 |
| **影響範圍** | `backend/.../entity/UserEntity.java`, `backend/.../entity/UserApiKeyEntity.java`, `backend/.../entity/EhrConnectionEntity.java`, `backend/.../entity/DepartmentEntity.java`, `backend/.../entity/IndicatorCatalogEntity.java`, `backend/.../entity/MeasureDefinitionEntity.java`, `backend/.../entity/CqlLibraryEntity.java`, `backend/.../entity/MeasureScheduleEntity.java`, `backend/.../entity/MeasureThresholdEntity.java`, `backend/.../service/UserApiKeyService.java`, `backend/.../controller/UserApiKeyController.java`, `backend/.../resources/db/migration/V35__api_key_hashing.sql` |

### BUG 描述

全面審計 25 個 Entity 後，發現多項安全漏洞分佈在 9 個 Entity 中：

1. **密碼洩漏**（CRITICAL）：`UserEntity.password` 無 `@JsonIgnore`，任何回傳 `UserEntity` 的 API 都會將 BCrypt hash 序列化至 JSON response。
2. **API Key 明文儲存**（CRITICAL）：`UserApiKeyEntity.apiKey` 以明文存入資料庫，資料庫洩漏等同所有 API key 外洩。
3. **EHR 憑證明文儲存**（CRITICAL）：`EhrConnectionEntity.credentials`（含 FHIR server 密碼/token）以明文存入資料庫。
4. **Mass Assignment**（HIGH）：9 個 Entity 的伺服器控制欄位（`id`、`createdAt`、`updatedAt`、`ownerUsername`、`role`、`enabled` 等）未標記 `READ_ONLY`，攻擊者可透過 JSON request body 覆寫這些值。
5. **validateApiKey 早期返回 bug**（HIGH）：legacy key 升級路徑的 `return` 跳過了 `lastUsedAt` 更新，且 managed entity 被修改 plaintext 後可能被 JPA dirty-checking 寫回 DB。

### 修正方式

1. **密碼保護**：`UserEntity.password` 加上 `@JsonIgnore`；`role`、`enabled` 加上 `@JsonProperty(READ_ONLY)`。
2. **API Key SHA-256 雜湊**：
   - `UserApiKeyService.generateApiKey()` 改為儲存 SHA-256 hash，新增 `keyPrefix` 欄位（前 8 字元）供顯示用。
   - `validateApiKey()` 以 hash 查詢；fallback 明文查詢後自動升級為 hash（向後相容）。
   - 用 `entityManager.detach(entity)` 防止 JPA dirty-checking 將暫時設定的明文 key 寫回 DB。
   - 加入 15 分鐘 debounce 減少 `lastUsedAt` 的 DB 寫入。
   - 重構為 single exit path，修復 legacy key 跳過 `lastUsedAt` 的 bug。
   - Flyway `V35__api_key_hashing.sql`：新增 `key_prefix` 欄位，從現有明文 key 填充。
3. **EHR 憑證加密**：`EhrConnectionEntity.credentials` 加上 `@Convert(converter = EncryptionConverter.class)` 以 AES-GCM 加密儲存，並標記 `@JsonProperty(WRITE_ONLY)` 防止 API 回傳。
4. **Mass Assignment 防護**：9 個 Entity 的伺服器控制欄位加上 `@JsonProperty(access = READ_ONLY)`，使 Jackson 反序列化時忽略這些欄位。
5. **UserApiKeyController**：移除 `maskKey()` 方法，改用 `keyPrefix + "..."` 顯示。

### 驗證

- `UserEntity` JSON 序列化不含 `password` 欄位
- API key 存入 DB 為 64 字元 SHA-256 hex（非明文）
- Legacy 明文 key 首次使用後自動升級為 hash
- EHR 憑證在 DB 中為加密密文，API 回應不含 `credentials`
- 所有 Entity 的 `id`/`createdAt`/`updatedAt` 無法透過 JSON request body 覆寫
- IDE 零診斷（0 errors, 0 warnings）

---

## #053 — AuthController 安全強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | 認證系統（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 |
| **影響範圍** | `backend/.../controller/AuthController.java` |

### BUG 描述

AuthController 存在三項安全問題：

1. **SSO 錯誤訊息洩漏**（HIGH）：Okta SSO callback 失敗時回傳 `e.getMessage()`，可能暴露內部主機名稱、Okta 配置細節或堆疊追蹤片段。
2. **JIT provisioning 競態條件**（MEDIUM）：`deriveUsername()` 的 `existsByUsername` 檢查與 `save` 之間無同步，兩個並行的 Okta 登入請求可能通過相同的唯一性檢查，導致 DB unique constraint violation → 500 錯誤。
3. **getBaseUrl 信任代理 header**（MEDIUM）：當 `APP_BASE_URL` 未設定時，密碼重設連結的 base URL 直接從 `X-Forwarded-Host` / `X-Forwarded-Proto` header 產生，攻擊者可偽造 header 將密碼重設連結導向惡意網域。

### 修正方式

1. **SSO 錯誤訊息**：移除 `e.getMessage()`，僅回傳通用的 `"SSO authentication failed"` 訊息。內部錯誤仍透過 `log.error()` 記錄。
2. **JIT 競態條件**：將 Okta user 建立邏輯從 `orElseGet` lambda 改為明確的 `if (user == null)` 區塊，用 `try-catch(DataIntegrityViolationException)` 包裝，捕獲時重新查詢取得已建立的使用者。
3. **getBaseUrl fallback**：新增 `log.warn()` 警告，讓運維人員在日誌中看到未配置 `APP_BASE_URL` 的風險提示。

### 驗證

- SSO 失敗回應不含任何內部細節
- 並行 Okta JIT provisioning 不會產生 500 錯誤
- 未設定 `APP_BASE_URL` 時日誌會出現警告訊息

---

## #052 — CDS 人工製品表格欄位錯位

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | 規則撰寫（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/authoring/ArtifactList.tsx` |

### BUG 描述

「規則撰寫」頁面的 CDS 人工製品列表表格，header 欄位（名稱 / 版本 / 狀態 / 更新時間 / 操作）與資料列欄位嚴重錯位。

根因：使用 `react-window` 的 `FixedSizeList` 虛擬捲動，每一資料列是獨立的 `<Table>` 元素，與 header 的 `<Table>` 沒有共享欄寬，各自由瀏覽器自動計算寬度，導致不對齊。（與 BUG-036 MeasureLibrary 相同模式）

### 修正方式

- 定義 `COL_WIDTHS` 常數統一五欄的百分比寬度（40% / 12% / 12% / 20% / 16%）
- Header table 和每列 body table 都套用 `tableLayout: 'fixed'` + 相同的 `width` 值
- 名稱欄加上 `overflow: hidden; textOverflow: ellipsis` 防止過長名稱撐破版面

### 驗證

- 表格 header 與資料列的五欄完全對齊
- 長名稱正確截斷顯示

---

## #051 — 外部連線 CORS 被擋 + PNA header 未覆蓋動態 origin

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | Docker 基礎設施（後端） |
| **嚴重程度** | High |
| **根因類型** | 配置遺漏 / 邏輯錯誤 |
| **影響範圍** | `backend/.../WebConfig.java`, `application-docker.yml`, `docker-compose.yml`, `docker/.env` |

### BUG 描述

外部使用者透過 LAN IP 或 Cloudflare Tunnel 連線時，所有 API 請求因 CORS 被瀏覽器攔截。兩處根因：

1. **配置遺漏**：Docker profile 下 CORS 只允許 `localhost:8888` / `127.0.0.1:8888`，沒有機制加入外部 IP 或域名。
2. **邏輯錯誤**：`privateNetworkFilter` 的 `isAllowedOrigin()` 使用硬編碼的靜態清單，不含 `cors.allowed-origins` 設定的動態 origin，也不含 docker profile 的 `localhost:8888`。即使 CORS 正確，Private Network Access preflight 仍會失敗。

### 修正方式

- **WebConfig.java**：刪除硬編碼 `ALLOWED_ORIGINS` 靜態清單與 `isAllowedOrigin()` 靜態方法。新增 `getAllAllowedOrigins()` 實例方法作為唯一的 origin 來源，`corsFilter()` 和 `privateNetworkFilter()` 共用同一份邏輯。
- **application-docker.yml**：新增 `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:}` 映射。
- **docker-compose.yml**：backend environment 新增 `CORS_ALLOWED_ORIGINS` 傳遞。
- **docker/.env** / **.env.example**：新增 `CORS_ALLOWED_ORIGINS` 佔位與說明。

### 驗證

- 設定 `CORS_ALLOWED_ORIGINS=http://192.168.1.100:8888` 後，外部瀏覽器可正常存取
- CDS Hooks Sandbox 的 PNA preflight 正確回傳 `Access-Control-Allow-Private-Network: true`
- 未設定時行為不變（僅允許 localhost）

---

## #050 — CDS 卡片 CodeableConcept 多 coding 只顯示第一個

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-27 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/.../CdsResourceFormatter.java` |
| **Commit** | [`aed0ecb`](../../commit/aed0ecb) |

### BUG 描述

CDS 卡片在 AllergyIntolerance（及其他資源）的 `code` 包含多個 coding 時，僅顯示第一個 coding 的 display。例如同時輸入 ZINC 和 MAGNESIUM 兩個過敏藥物，卡片只顯示 ZINC。

根因：所有 `appendXxx()` 方法皆使用 `getCodingFirstRep()` 取第一筆 coding，忽略後續 coding。

### 修正方式

提取 `formatAllCodings()` helper 方法：
- 有 `text` 時直接回傳 text
- 無 `text` 時遍歷所有 `coding[]`，以逗號連接各 `display`（無 display 則 fallback 至 `code`）
- 統一套用至 Observation、Condition、MedicationRequest、Procedure、AllergyIntolerance 五個 formatter

### 驗證

- AllergyIntolerance 填入兩個 coding（ZINC + MAGNESIUM）→ 卡片顯示 "Allergy: ZINC (AS ZINC OXIDE), MAGNESIUM (AS PHO...)"
- 單一 coding 行為不變；有 text 時優先顯示 text

---

## #049 — CDS 卡片僅顯示資源參考而非過敏藥物名稱

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-27 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/.../CdsResourceFormatter.java`, `backend/.../CdsValueFormatter.java` |
| **Commit** | [`8b67eb6`](../../commit/8b67eb6) |

### BUG 描述

CDS Sandbox 測試過敏 CDS 服務時，卡片顯示 `AllergyIntolerance/allergyintolerance-46efbb69` 而非實際藥物名稱 "Aspi-Cor 81 MG Delayed Release Oral Tablet"。

兩處根因：
1. `CdsResourceFormatter.appendAllergyIntolerance()` 僅檢查 `code.text`，但**未 fallback** 至 `code.coding[0].display`（`appendCondition` 和 `appendObservation` 皆有此 fallback）。使用者透過 RxNorm 碼表填入 coding 但未填「文字」欄位時，藥名完全不顯示。
2. `CdsValueFormatter.formatValue()` 對 List 中的 Resource 呼叫 `formatReference()` 僅回傳 `Type/id`，而非 `formatDetail()` 的完整臨床資訊。

### 修正方式

- `appendAllergyIntolerance()`：新增 `else if (hasCoding())` fallback 取 `getCodingFirstRep().getDisplay()`
- `appendProcedure()`：同樣補上 coding fallback（相同缺陷）
- `CdsValueFormatter.formatValue()`：Resource 項目改用 `formatDetail()` 取代 `formatReference()`

### 驗證

- CDS Sandbox 新增 AllergyIntolerance（僅填 coding，不填 text）→ 卡片正確顯示藥名
- 同時填 text 與 coding → 優先顯示 text（行為不變）
- Procedure 資源同理修正，卡片正確顯示處置名稱

---

## #048 — RxNorm 代碼搜尋無結果 — FHIR 術語伺服器未載入 UMLS 授權碼表

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-27 |
| **功能分類** | 術語查詢（後端） |
| **嚴重程度** | High |
| **根因類型** | 外部服務限制 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/fhir/FhirTerminologyService.java` |
| **Commit** | [`fe50e2a`](../../commit/fe50e2a) |

### BUG 描述

「術語查詢 > 代碼搜尋」使用 FHIR `$expand` 搜尋隱式 ValueSet，但 `tx.fhir.org` 和 `r4.ontoserver.csiro.au` 均未載入 RxNorm（需 UMLS 授權），因此搜尋 RxNorm 代碼時 `$expand` 回傳 "ValueSet not found"，使用者看到 0 筆結果。

### 修正方式

在 `searchCodes()` 中，當所有 FHIR 術語伺服器對 RxNorm 回傳空結果時，新增 NLM RxNav REST API 作為 fallback：

- 偵測 `system == http://www.nlm.nih.gov/research/umls/rxnorm` 且遠端結果為空
- 呼叫 `https://rxnav.nlm.nih.gov/REST/approximateTerm.json?term={text}&maxEntries={count}`
- 解析 JSON `approximateGroup.candidate[]`，以 `rxcui` 去重
- 回傳 `List<CodeSearchResult>`，system 設為 RxNorm URL

RxNav 為 NLM 提供的免費公開 API，無需驗證。

### 驗證

- 選擇 RxNorm 搜尋 "aspirin" → 回傳 aspirin (1191)、aspirin Oral Tablet 等結果
- LOINC / SNOMED 搜尋仍正常運作（無迴歸）

---

## #047 — Fallback paste handler 非同步讀取 clipboardData 導致貼上失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-27 |
| **功能分類** | Monaco 編輯器（前端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `frontend/src/components/editor/CqlEditor.tsx` |
| **Commit** | [`be3c6ce`](../../commit/be3c6ce) |

### BUG 描述

當 Monaco Editor 原生 Clipboard API 貼上失敗時（例如瀏覽器未授予剪貼簿權限、非 HTTPS 環境），fallback paste handler 無法正確介入，導致 Ctrl+V 完全無效。

**根本原因**：`ClipboardEvent.clipboardData` 僅在 paste 事件處理函數的同步執行期間可存取。在 #046 修正中引入的 fallback handler 將 `e.clipboardData?.getData('text/plain')` 放在 `setTimeout` callback 中讀取，此時事件已結束，瀏覽器已銷毀 `clipboardData` 物件，永遠回傳 `null`。

```javascript
// BUG: clipboardData 在 setTimeout 中已不可用
domNode.addEventListener('paste', (e) => {
  const modelBefore = editor.getModel()?.getValue()
  setTimeout(() => {
    // e.clipboardData 已被瀏覽器回收 → null
    const clipboardData = e.clipboardData?.getData('text/plain')
  }, 0)
})
```

### 修正方式

將 `clipboardData.getData('text/plain')` 移至 paste 事件處理函數的同步區域，先暫存至區域變數 `clipText`，再於 `setTimeout` 中使用該變數進行 fallback 寫入：

```javascript
domNode.addEventListener('paste', (e) => {
  // 同步讀取 — 事件處理期間才有效
  const clipText = e.clipboardData?.getData('text/plain')
  if (!clipText) return

  const modelBefore = editor.getModel()?.getValue()
  setTimeout(() => {
    // 使用先前暫存的 clipText
    if (modelBefore === modelAfter) { ... }
  }, 0)
})
```

### 驗證

- 在 Monaco 原生貼上失效的環境中，Ctrl+V 可透過 fallback 正確貼入文字
- 在 Monaco 原生貼上正常的環境中，fallback 不介入（`modelBefore !== modelAfter`）
- 貼上含特殊字元的 LLM 輸出仍正確 sanitize

---

## #046 — Docker 環境下 Monaco Editor 無法貼上（Ctrl+V 無效）

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-25 |
| **功能分類** | Monaco 編輯器（Docker） |
| **嚴重程度** | High |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `docker/nginx.conf`, `frontend/nginx.conf`, `CqlEditor.tsx` |
| **Commit** | [`ae9a0e3`](../../commit/ae9a0e3) |

### BUG 描述

在 Docker 環境中使用 CQL 編輯器時，Ctrl+V 貼上操作完全無效，無法將剪貼簿內容貼入 Monaco Editor。本機開發環境無此問題。

**根本原因**：兩個問題同時存在：

1. **Permissions-Policy 限制剪貼簿存取**：nginx 安全標頭設定 `clipboard-read=(self), clipboard-write=(self)`，在 Docker 環境中瀏覽器對 `self` origin 的解析可能與容器內 nginx 的 port mapping 不一致（容器內 8080 vs 外部映射 port），導致 Clipboard API 被瀏覽器拒絕。
2. **Fallback paste handler 與 Monaco 內建貼上衝突**：DOM 層級的 paste event listener 未檢查 Monaco 是否已處理貼上，直接呼叫 `editor.executeEdits()` 插入文字，可能與 Monaco 原生 paste 產生衝突。

### 修正方式

1. **移除 Permissions-Policy 的剪貼簿限制**：從 `docker/nginx.conf` 和 `frontend/nginx.conf` 移除 `clipboard-read=(self), clipboard-write=(self)`，讓剪貼簿操作回歸瀏覽器預設行為（同源允許）
2. **改寫 fallback paste handler**：使用 `setTimeout(0)` 延遲檢查，比較貼上前後 model 內容；僅在 Monaco 內建貼上未生效時才手動介入處理

### 驗證

- Docker 環境中開啟 CQL 編輯器，Ctrl+V 可正常貼上文字
- 貼上含有特殊字元的 LLM 輸出仍會被 sanitize（smart quotes、zero-width chars）

---

## #045 — 代碼查詢本地 TWCORE IG 回傳 ValueSet 名稱且缺少 display name

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-24 |
| **功能分類** | 術語查詢（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `FhirTerminologyService.java` |
| **Commit** | [`d5e150d`](../../commit/d5e150d) |

### BUG 描述

在術語查詢頁面的「代碼查詢」標籤中，輸入 LOINC code `29463-7`（Body weight）查詢後：
- 「顯示名稱」欄位為空
- 「名稱」欄位錯誤顯示 "TWVitalSigns"（這是 ValueSet 名稱，非代碼名稱）

**根本原因**：`lookupCodeFromLocalIg()` 有兩個問題：

1. **名稱欄位錯誤**：在 ValueSet 中找到代碼時，使用 `vs.getName()`（ValueSet 名稱 "TWVitalSigns"）作為回傳的 `name` 欄位，而非代碼本身的名稱。
2. **空 display 不 fallthrough**：本地 TWCORE IG 的 ValueSet 內僅存放代碼參考（code），不一定有 `display` 屬性。當 `conceptRef.getDisplay()` 為空時仍立即回傳結果，不會繼續 fallthrough 到遠端術語伺服器（tx.fhir.org）取得完整的 display name 和 designations。

### 修正方式

1. ValueSet 查找時，若 `conceptRef.getDisplay()` 為空或 blank，跳過不回傳，讓流程繼續到遠端伺服器
2. `name` 欄位改傳 `null`，不再使用 ValueSet 名稱作為代碼名稱

### 驗證

- 術語查詢 LOINC `29463-7`，顯示名稱正確顯示 "Body weight"，不再出現 "TWVitalSigns"

---

## #044 — Retrieve Builder 依賴不存在的 C3F 外部函式庫致 CQL 無法解析

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-24 |
| **功能分類** | CQL Builder（前端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `RetrieveBuilder.tsx`, `CqlBuilderPanel.tsx` |
| **Commit** | [`d5e150d`](../../commit/d5e150d) |

### BUG 描述

在 CQL Builder 的 Retrieve 模式中，勾選「最近一次」（Most Recent）、「啟用/已確認」（Active/Confirmed）或「回溯期間」（Look Back）修飾器後，產生的 CQL 程式碼使用 `C3F.MostRecent(...)`、`C3F.ActiveCondition(...)` 等函式，並自動插入 `include CDS_Connect_Commons_for_FHIRv401 version '1.1.1' called C3F`。

然而此函式庫在後端 repository 中不存在，CQL 翻譯器無法解析 `C3F` 識別符號，導致錯誤：「Could not resolve identifier C3F in the current library.」

**根本原因**：RetrieveBuilder 的 `generateCql()` 假設環境中存在 CDS Connect Commons 外部函式庫，但實際部署環境無此函式庫。

### 修正方式

1. **`RetrieveBuilder.tsx`**：重寫 `generateCql()` 產生內嵌 CQL 查詢，不再依賴任何外部函式庫：
   - `C3F.MostRecent(list)` → `Last(list sort by Coalesce(effective as dateTime, issued))`
   - `C3F.ActiveCondition(list)` → `list C where C.clinicalStatus.coding.code contains 'active'`
   - `C3F.LookBack(list, N units)` → `list O where O.effective >= Now() - N units`
   - 新增 `getDateExpression()` 依資源類型回傳正確的日期欄位
2. **`CqlBuilderPanel.tsx`**：移除已無用的 `handleAutoIncludeC3F` 和 `handleInsertWithCheckC3F`，definitions 改用 `handleInsertWithCheck`

### 驗證

- Retrieve Builder 勾選 Most Recent → CQL 預覽顯示 `Last([Observation: ...] O sort by ...)` 語法，翻譯無錯誤
- 勾選 Active/Confirmed → 產生 `where C.clinicalStatus.coding.code contains 'active'`，翻譯無錯誤

---

## #043 — TestCaseEditor expectedPopulations 被 React Query refetch 競態重置

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-24 |
| **功能分類** | Test Cases（前端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `TestCaseEditor.tsx`, `TestCasesTab.tsx` |
| **Commit** | [`5b09697`](../../commit/5b09697) |

### BUG 描述

使用者在測試案例編輯器中修改 expectedPopulations（分母/分子的預期值），按儲存後變更未生效，數值恢復為修改前的狀態。多次嘗試修改皆無法持久化。

**根本原因**：兩層 `useEffect` 競態導致編輯中的 state 被覆蓋：

1. **`TestCasesTab.tsx`**：sessionStorage 恢復用的 `useEffect` 依賴 `[measure.id, isLoading, testCases]`，每次 React Query 重取 `testCases`（如背景 refetch、其他 mutation 的 `invalidateQueries`）都會重新執行，呼叫 `setEditingRaw(found)` 產生新的物件參考。
2. **`TestCaseEditor.tsx`**：`useEffect` 依賴 `[testCase, dispatch]`，當 `testCase` prop 的物件參考改變時，無條件重置所有表單 state（`setExpectedPops(testCase.expectedPopulations || {})`），覆蓋使用者正在編輯的修改。

使用者操作流程：修改 toggle → React Query 背景 refetch → `testCases` 更新 → TestCasesTab useEffect 設定新 `editing` 物件 → TestCaseEditor useEffect 重置 `expectedPops` 回伺服器舊值 → 使用者按儲存 → 存入的是被重置的舊值。

### 修正方式

1. **`TestCasesTab.tsx`**：加入 `restoredRef` 確保 sessionStorage 恢復邏輯僅在首次載入時執行一次，不因 `testCases` refetch 重複更新 `editing` state。
2. **`TestCaseEditor.tsx`**：加入 `prevTestCaseIdRef` 追蹤 test case ID，`useEffect` 僅在切換到不同 test case 時才重置表單 state，同一 test case 的物件參考變化不會覆蓋使用者修改。

### 驗證

- 開啟測試案例編輯器，修改 expectedPopulations toggle，等待數秒（確保 React Query refetch 有機會觸發），按儲存後重新開啟，修改已正確持久化

---

## #042 — ComparableR4FhirModelResolver 日期轉換破壞 FHIRHelpers 時態運算子

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | CQL Engine（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `ComparableR4FhirModelResolver.java` |
| **Commit** | [`6534790`](../../commit/6534790) |

### BUG 描述

測試案例中 `MR.authoredOn during "Measurement Period"` 時態比較靜默失敗，導致 Denominator 永遠為 false。透過診斷 CQL 表達式逐一隔離 WHERE 子句條件，確認 `authoredOn during` 是唯一失敗的條件。

**根本原因**：`ComparableR4FhirModelResolver.resolvePath()` 在 Resource 層級將 FHIR `DateTimeType` 提前轉換為 CQL `DateTime`。後續 FHIRHelpers（CQL 翻譯器自動包含）的 `ToDateTime()` 函數嘗試對已轉換的 CQL DateTime 呼叫 `.value`，回傳 null，導致 `during` 等時態運算子靜默失敗。

### 修正方式

完全移除 `ComparableR4FhirModelResolver` 中的日期/時間轉換邏輯（`convertIfDateTimeType`、`toEngineDateTime` 方法及相關 imports）。該類別現僅處理 Encounter.class Java 保留字衝突。FHIRHelpers 已正確處理所有 FHIR→CQL 型別轉換。

### 驗證

- 測試案例 "65-year-old lady with DM" 執行結果：initial-population=true、denominator=true、numerator=false，全部符合預期

---

## #041 — Encounter.class Java 保留字衝突致 CQL 路徑解析錯誤

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | CQL Engine（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `ComparableR4FhirModelResolver.java` |
| **Commit** | [`6534790`](../../commit/6534790) |

### BUG 描述

CQL 中 `E.class ~ "AMB"` 存取 Encounter.class FHIR 元素時，Java 反射呼叫 `Object.getClass()` 而非 HAPI FHIR 的 `getClass_()`，回傳 Java Class 物件而非 FHIR Coding，導致 Encounter 類型過濾永遠失敗。

### 修正方式

在 `ComparableR4FhirModelResolver.resolvePath()` 加入特殊處理：當 `path="class"` 且 `target instanceof Encounter` 時，顯式呼叫 `encounter.getClass_()`。

### 驗證

- Outpatient Encounters 定義正確回傳符合條件的 Encounter 資源

---

## #040 — TestCaseService 缺少 Measurement Period 參數致時間過濾失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | Test Cases（後端） |
| **嚴重程度** | High |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `TestCaseService.java` |
| **Commit** | [`6534790`](../../commit/6534790) |

### BUG 描述

測試案例 CQL 中所有引用 `"Measurement Period"` 的時態過濾（如 `during "Measurement Period"`、`overlaps "Measurement Period"`）均失敗，因為 CQL 引擎執行時未提供 Measurement Period 參數，該參數值為 null。

### 修正方式

新增 `buildMeasurementPeriodParams()` 方法，建立當年度（1/1 – 12/31）的 CQL `Interval<DateTime>` 參數，透過 `execRequest.setParameters()` 傳入 CQL 引擎。

### 驗證

- CQL 時態過濾表達式正確評估

---

## #039 — TestCaseService 查詢 FHIR Server 而非使用記憶體內測試 Bundle

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | Test Cases（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `TestCaseService.java` |
| **Commit** | [`6534790`](../../commit/6534790) |

### BUG 描述

`TestCaseService.executeTestCase()` 和 `runWithCoverage()` 使用 `cqlExecutionService.execute()` 透過 REST 客戶端查詢外部 HAPI FHIR 伺服器，但測試案例的患者資料存在於 `patientBundleJson` 欄位中，不在 FHIR 伺服器上，導致所有 `[Resource]` retrieve 回傳空集合。

### 修正方式

1. 新增 `parseBundleResources()` 方法，使用 `FhirContext.forR4()` 解析 Bundle JSON 為 `List<Resource>`
2. 建立 `PrefetchRetrieveProvider`（複用 CDS Hooks 模組的記憶體內資料提供者）
3. 改用 `cqlExecutionService.executeWithProvider()` 以記憶體內資料執行 CQL

### 驗證

- CQL `[Condition]`、`[MedicationRequest]`、`[Encounter]` 等 retrieve 正確從測試 Bundle 取得資料

---

## #038 — HikariCP 連線池耗盡導致所有 API 逾時、無法登入

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | 資料庫連線池（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `application.yml`, `application-docker.yml`, `application-dev.yml`, `CdsHooksService.java` |
| **Commit** | [`ccdf3f2`](../../commit/ccdf3f2) |

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
| **Commit** | [`ccdf3f2`](../../commit/ccdf3f2) |

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
| **Commit** | [`ccdf3f2`](../../commit/ccdf3f2) |

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
