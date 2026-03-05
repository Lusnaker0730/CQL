# Change Log

> 所有功能改善、重構、Bug 修復與安全修補的統一記錄。
> 由 BUGFIX_LOG.md（BUG-001 ~ BUG-082）與 PATCH_LOG.md（PAT-001 ~ PAT-026）合併而成。

---

## 總覽索引

| ID | 類型 | 日期 | 範圍 | 標題 | 備註 | Commit |
|-----|------|------|------|------|------|--------|
| PAT-028 | ✨ patch | 2026-03-05 | eCQM（前端） | eCQM 工作區存檔功能 — Save 按鈕 + Ctrl+S + 狀態指示器 + 未儲存變更防護 | Frontend (eCQM) | [`7019613`](../../commit/7019613) |
| PAT-027 | 🌐 i18n | 2026-03-05 | eCQM（前端） | eCQM 撰寫全模組 i18n 繁體中文翻譯 — 12 元件 + ecqm namespace + 懶載入 | Frontend (eCQM) | [`0fe60a8`](../../commit/0fe60a8) |
| BUG-083 | 🐛 bugfix | 2026-03-05 | CDS Authoring（前後端） | CQL Retrieve 使用 element_name 而非 code display — buildGenericResourceExpression 迴圈提前 return + save 驗證錯誤未顯示 | 邏輯錯誤 / UX | [`52fd78c`](../../commit/52fd78c) |
| PAT-026 | ✨ patch | 2026-03-05 | 安全性 | CQL 注入修復 + XSS 修復 — escapeCqlString 補齊 + dangerouslySetInnerHTML escapeValue | Backend (Authoring) + Frontend (全模組) | [`f1fe52e`](../../commit/f1fe52e) |
| PAT-025 | ✨ patch | 2026-03-05 | 重構 | FreeMarker 模板引擎遷移 — CQL 產生器從字串拼接重構為模板架構 + 表達式樹 conjunction 前端重構 | Backend (Authoring, eCQM) + Frontend (Authoring) | [`f1fe52e`](../../commit/f1fe52e) |
| PAT-024 | ✨ patch | 2026-03-04 | eCQM | eCQM 視覺化 CQL 產生引擎 — 全端實作 + Publish 至 MeasureDefinition | Backend + Frontend (eCQM, Authoring) | [`e356957`](../../commit/e356957) |
| PAT-023 | ✨ patch | 2026-03-04 | 安全性 | JWT Refresh Token 滑動視窗過期 — 雙令牌架構 + 令牌輪換 + 重用偵測 | Backend + Frontend (Auth) | [`256f5d1`](../../commit/256f5d1) |
| BUG-082 | 🐛 bugfix | 2026-03-04 | CDS Authoring（前後端） | 元素模板繼承未解析 + React Hooks 順序違規 — 缺少元素名稱 + CDS Hooks 頁面崩潰 | 邏輯錯誤 / 框架違規 | [`8e8d4c8`](../../commit/8e8d4c8) |
| BUG-081 | 🐛 bugfix | 2026-03-04 | CQL 執行引擎（後端） | CQL 批次執行崩潰 + FHIR Token 搜尋管道符號轉義 — FHIRHelpers 歧義 + 查詢回傳 0 筆 | 邏輯錯誤 / API 誤用 | [`649ac67`](../../commit/649ac67) |
| BUG-080 | 🐛 bugfix | 2026-03-04 | CDS Authoring（前後端） | 多分頁同時編輯 Artifact 導致靜默資料覆蓋 — JPA @Version 樂觀鎖 + 前端衝突對話框 | 併發/效能問題 | [`9b46017`](../../commit/9b46017) |
| BUG-079 | 🐛 bugfix | 2026-03-04 | 安全性（後端） | Rate Limiting 分層強化 — 端點分級 IP 限流 + 使用者限流 + 大型 Payload 加權 | 安全漏洞（DoS / 資源耗盡） | [`6b72fec`](../../commit/6b72fec) |
| BUG-078 | 🐛 bugfix | 2026-03-04 | 安全性（前後端） | CDS Card XSS 3 層防護 — 前端安全渲染 + 後端 HTML 跳脫 + 反序列化器強化 | 安全漏洞（XSS） | [`d7fc37f`](../../commit/d7fc37f) |
| BUG-077 | 🐛 bugfix | 2026-03-04 | 安全性（後端） | 停用使用者 API Key 未失效 — 認證繞過漏洞 + 雙重防護修復 | 安全漏洞（認證繞過） | [`51af336`](../../commit/51af336) |
| BUG-076 | 🐛 bugfix | 2026-03-04 | 安全性（後端） | AuditFilter $export 未標記 PHI 存取 + 欄位溢位導致稽核寫入失敗 | 安全漏洞（稽核遺漏） |  |
| BUG-075 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CqlArtifactBuilder 測試補強 — LookBack / AgeRange / 空排除 / 括號驗證 + Windows 換行修復 | 測試遺漏 |  |
| BUG-074 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CQL 產生器 AgeRange / ValueComparison 複合條件缺少括號 — OR 群組內可讀性差 | 邏輯錯誤 |  |
| BUG-073 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | verifyArtifactOwnership 使用 IllegalArgumentException(400) 而非 ResourceNotFoundException(404) | 邏輯錯誤 |  |
| BUG-072 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CdsArtifactEntity 反序列化失敗被靜默吞掉，無 log | 配置遺漏 |  |
| BUG-071 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CQL 產生器靜默降級 — 未知 element type 或 modifier 被忽略，使用者無感知 | 邏輯錯誤 |  |
| BUG-070 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CqlGenerationService.generateCql() 無 try-catch — 畸形 JSON 導致 generic 500 | 邏輯錯誤 |  |
| BUG-069 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CqlArtifactBuilder Singleton 可變 instance field — 並行請求互相覆蓋 | 併發/效能問題 |  |
| BUG-068 | 🐛 bugfix | 2026-03-03 | 安全性（後端） | AuditFilter PHI 稽核修復 — FHIR 三層路徑解析、顯式 phiAccess 旗標、查詢參數擷取 | 安全漏洞（稽核遺漏） | [`ec1a21c`](../../commit/ec1a21c) |
| BUG-067 | 🐛 bugfix | 2026-03-03 | 安全性（後端） | CDS Feedback 儲存型 XSS 修復 — @NoXss 驗證 + HtmlUtils.htmlEscape 雙層防護 | 安全漏洞（XSS） | [`e12e64b`](../../commit/e12e64b) |
| BUG-066 | 🐛 bugfix | 2026-03-03 | 安全性（後端） | CQL 執行逾時強化 — worker 中斷、AbortPolicy 防執行緒池耗盡、差異化 HTTP 狀態碼 | 安全漏洞（DoS / 資源耗盡） | [`d56c0a8`](../../commit/d56c0a8) |
| BUG-065 | 🐛 bugfix | 2026-03-03 | 安全性（後端） | CqlController IDOR 授權修復 + LIKE 萬用字元注入防護 | 安全漏洞（存取控制 / 注入） | [`57de58f`](../../commit/57de58f) |
| BUG-064 | 🐛 bugfix | 2026-03-02 | Monaco 編輯器（前端） | CqlEditor paste sanitization — Trojan Source bidi 防護、undo-safe executeEdits、Monaco 記憶體洩漏修復 | 安全漏洞 / 記憶體洩漏 | [`c84c8bd`](../../commit/c84c8bd) |
| BUG-063 | 🐛 bugfix | 2026-03-02 | 前端效能（前端） | useCqlEditor useCallback 優化 — translate/validate/execute 穩定引用 | 效能 |  |
| BUG-062 | 🐛 bugfix | 2026-03-02 | 安全性（後端） | FhirController 安全強化 — identifier 驗證、IG URL 驗證、RestTemplate 逾時、連線池耗盡防護 | 安全漏洞 / 配置遺漏 |  |
| BUG-061 | 🐛 bugfix | 2026-03-02 | 前端效能（前端） | Measure 元件效能 — useMemo、O(n²) 修復、搜尋防抖、console.error 替換 | 效能 / 程式碼品質 |  |
| BUG-060 | 🐛 bugfix | 2026-03-02 | 程式碼品質（前端） | Measure 元件重構 — scoreColors/downloadBlob/extractApiError 共用化 | 程式碼品質 |  |
| BUG-059 | 🐛 bugfix | 2026-03-02 | 程式碼品質（前後端） | Service 層安全強化 + Builder 元件去重 — 憑證洩漏、6 共用抽取、250 行刪減 | 安全漏洞 / 程式碼品質 |  |
| BUG-058 | 🐛 bugfix | 2026-03-02 | 安全性（後端） | Repository 層簡化 — 18 個死碼方法移除、LIKE 萬用字元注入修復、共用工具提取 | 程式碼品質 / 安全漏洞 |  |
| BUG-057 | 🐛 bugfix | 2026-03-02 | 安全性（後端） | Model DTO 驗證強化 — @Size 防 DoS、SSRF URL 驗證、@Pattern 約束、死碼清除 | 安全漏洞 / 程式碼品質 |  |
| BUG-056 | 🐛 bugfix | 2026-03-02 | 規則撰寫（前端） | CQL 預覽對話框程式碼文字在淺色模式下幾乎不可見 | UX 設計缺陷 |  |
| BUG-055 | 🐛 bugfix | 2026-03-01 | 安全性（後端） | Controller 輸入驗證強化 — require* helpers、Math.clamp、URI 安全、DigestUtils 抽取 | 安全漏洞 / 程式碼品質 |  |
| BUG-054 | 🐛 bugfix | 2026-03-01 | 安全性（後端） | Entity 安全強化 — mass assignment 防護、密碼/金鑰洩漏、API key SHA-256 雜湊、憑證加密 | 安全漏洞 |  |
| BUG-053 | 🐛 bugfix | 2026-03-01 | 認證系統（後端） | AuthController 安全強化 — SSO 錯誤訊息洩漏、JIT 競態條件、base URL header 信任 | 安全漏洞 |  |
| BUG-052 | 🐛 bugfix | 2026-03-01 | 規則撰寫（前端） | CDS 人工製品表格欄位錯位 — react-window 獨立 Table 未共享欄寬 | UX 設計缺陷 |  |
| BUG-051 | 🐛 bugfix | 2026-03-01 | Docker 基礎設施（後端） | 外部連線 CORS 被擋 + PNA header 未覆蓋動態 origin | 配置遺漏 / 邏輯錯誤 |  |
| PAT-022 | ✨ patch | 2026-02-28 | Authoring | TWCORE IG 範本支援（19 預設範本 + TW 代碼系統 + 目錄擴充） | Backend + Frontend (Authoring) | [`bf27974`](../../commit/bf27974) |
| PAT-021 | ✨ patch | 2026-02-27 | CDS | Hospital-Grade 改善：draftOrders + 預取解析 + 重大警示彈窗 | Backend + Frontend (CDS, i18n) | [`1b0a22a`](../../commit/1b0a22a) |
| BUG-050 | 🐛 bugfix | 2026-02-27 | CDS Hooks Sandbox（後端） | CDS 卡片 CodeableConcept 多 coding 只顯示第一個 | 邏輯錯誤 | [`aed0ecb`](../../commit/aed0ecb) |
| BUG-049 | 🐛 bugfix | 2026-02-27 | CDS Hooks Sandbox（後端） | CDS 卡片僅顯示資源參考而非過敏藥物名稱 | 邏輯錯誤 | [`8b67eb6`](../../commit/8b67eb6) |
| BUG-048 | 🐛 bugfix | 2026-02-27 | 術語查詢（後端） | RxNorm 代碼搜尋無結果 — FHIR 術語伺服器未載入 UMLS 授權碼表 | 外部服務限制 | [`fe50e2a`](../../commit/fe50e2a) |
| BUG-047 | 🐛 bugfix | 2026-02-27 | Monaco 編輯器（前端） | Fallback paste handler 非同步讀取 clipboardData 導致貼上失效 | 邏輯錯誤 | [`be3c6ce`](../../commit/be3c6ce) |
| BUG-046 | 🐛 bugfix | 2026-02-25 | Monaco 編輯器（Docker） | Docker 環境下 Monaco Editor 無法貼上（Ctrl+V 無效） | 配置遺漏 | [`ae9a0e3`](../../commit/ae9a0e3) |
| PAT-020 | ✨ patch | 2026-02-24 | Authoring | 分頁驗證錯誤明細（Tooltip + Alert） | Frontend (Authoring, i18n) | [`4efb3c8`](../../commit/4efb3c8) |
| BUG-045 | 🐛 bugfix | 2026-02-24 | 術語查詢（後端） | 代碼查詢本地 TWCORE IG 回傳 ValueSet 名稱且缺少 display name | 資料處理錯誤 | [`d5e150d`](../../commit/d5e150d) |
| BUG-044 | 🐛 bugfix | 2026-02-24 | CQL Builder（前端） | Retrieve Builder 依賴不存在的 C3F 外部函式庫致 CQL 無法解析 | 架構缺陷 | [`d5e150d`](../../commit/d5e150d) |
| BUG-043 | 🐛 bugfix | 2026-02-24 | Test Cases（前端） | TestCaseEditor expectedPopulations 被 React Query refetch 競態重置 | 架構缺陷 | [`5b09697`](../../commit/5b09697) |
| BUG-042 | 🐛 bugfix | 2026-02-23 | CQL Engine（後端） | ComparableR4FhirModelResolver 日期轉換破壞 FHIRHelpers 時態運算子 | 架構缺陷 | [`6534790`](../../commit/6534790) |
| BUG-041 | 🐛 bugfix | 2026-02-23 | CQL Engine（後端） | Encounter.class Java 保留字衝突致 CQL 路徑解析錯誤 | 邏輯錯誤 | [`6534790`](../../commit/6534790) |
| BUG-040 | 🐛 bugfix | 2026-02-23 | Test Cases（後端） | TestCaseService 缺少 Measurement Period 參數致時間過濾失效 | 配置遺漏 | [`6534790`](../../commit/6534790) |
| BUG-039 | 🐛 bugfix | 2026-02-23 | Test Cases（後端） | TestCaseService 查詢 FHIR Server 而非使用記憶體內測試 Bundle | 架構缺陷 | [`6534790`](../../commit/6534790) |
| BUG-038 | 🐛 bugfix | 2026-02-23 | 資料庫連線池（後端） | HikariCP 連線池耗盡導致所有 API 逾時、無法登入 | 配置遺漏 | [`ccdf3f2`](../../commit/ccdf3f2) |
| BUG-037 | 🐛 bugfix | 2026-02-23 | 術語查詢（前端） | Autocomplete freeSolo 以顯示標籤覆蓋系統 URL 致術語查詢 503 | 邏輯錯誤 | [`ccdf3f2`](../../commit/ccdf3f2) |
| BUG-036 | 🐛 bugfix | 2026-02-23 | 指標庫表格（前端） | MeasureLibrary 虛擬捲動表頭與內容欄位錯位擠壓 | UX 設計缺陷 | [`ccdf3f2`](../../commit/ccdf3f2) |
| PAT-019 | ✨ patch | 2026-02-22 | 跨模組 | 錯誤處理統一（Backend 例外層級 + Frontend 錯誤提取） | Backend (Controllers, Exceptions, Services) + Frontend (全模組) | [`645a775`](../../commit/645a775) |
| PAT-018 | ✨ patch | 2026-02-22 | 文件 | API 參考文件 + OpenAPI 規格檔 | 專案根目錄（API.md, openapi.yaml） | [`b6681c0`](../../commit/b6681c0) |
| PAT-017 | ✨ patch | 2026-02-22 | eQCM | 補完科別分類功能（篩選 + 指派） | Backend + Frontend (Measures) | [`b205335`](../../commit/b205335) |
| BUG-035 | 🐛 bugfix | 2026-02-22 | 品質指標儀表板（後端） | DashboardService 多處 NullPointerException 導致所有 Dashboard API 回傳 500 | 邏輯錯誤 | [`3f4c1c5`](../../commit/3f4c1c5) |
| BUG-034 | 🐛 bugfix | 2026-02-22 | 品質指標儀表板（前端） | Recharts ResponsiveContainer 初始化時計算 width/height 為 -1 | 配置遺漏 | [`3f4c1c5`](../../commit/3f4c1c5) |
| BUG-033 | 🐛 bugfix | 2026-02-22 | CQL 編輯器（前端） | 工具列 Undo/Redo 按鈕無效 — Redux 歷史與 Monaco 原生 undo 脫節 | 架構缺陷 | [`dca6617`](../../commit/dca6617) |
| PAT-016 | ✨ patch | 2026-02-21 | 安全性 | Okta SSO (OIDC) 整合 | Backend + Frontend (Auth, Admin) | [`7d48d6b`](../../commit/7d48d6b) |
| PAT-015 | ✨ patch | 2026-02-21 | FHIR | P2-8: EHR/HIS 整合連接器 | Backend + Frontend (FHIR, Measures) | [`3dbf07a`](../../commit/3dbf07a) |
| PAT-014 | ✨ patch | 2026-02-21 | eQCM | P2-9: 指標儀表板增強（Recharts） | Backend + Frontend (Dashboard) | [`3dbf07a`](../../commit/3dbf07a) |
| PAT-013 | ✨ patch | 2026-02-21 | 跨模組 | P2-10: 科別多租戶隔離 | Backend + Frontend (Auth, Measures, Admin) | [`3dbf07a`](../../commit/3dbf07a) |
| PAT-012 | ✨ patch | 2026-02-21 | eQCM | P2-11: 衛福部指標代碼對照 | Backend + Frontend (Measures) | [`3dbf07a`](../../commit/3dbf07a) |
| PAT-011 | ✨ patch | 2026-02-21 | 通知 | P1-5: 持久化通知系統 + 工作流程推播 | Backend + Frontend (Header) | [`b106739`](../../commit/b106739) |
| PAT-010 | ✨ patch | 2026-02-21 | eQCM | P1-6: FHIR Bundle 檔案上傳匯入 | Measures (Frontend) | [`b106739`](../../commit/b106739) |
| PAT-009 | ✨ patch | 2026-02-21 | eQCM | P1-4: 審核者欄位 + 退回原因 UI | Measures (Backend + Frontend) | [`b106739`](../../commit/b106739) |
| PAT-008 | ✨ patch | 2026-02-21 | eQCM | P1-7: 人類可讀文件匯出 | Measures (Backend + Frontend) | [`b106739`](../../commit/b106739) |
| PAT-007 | ✨ patch | 2026-02-21 | eQCM | 測試案例批次匯入 + 日期平移 | Measures (Frontend + Backend) | [`76cf867`](../../commit/76cf867) |
| PAT-006 | ✨ patch | 2026-02-21 | eQCM | Population Criteria 佈局優化 + Reporting 分頁 | Measures (Frontend + Backend) | [`3b66db3`](../../commit/3b66db3) |
| BUG-032 | 🐛 bugfix | 2026-02-21 | eCQM 資料需求（後端） | DataRequirements 未解析 ToConcept 包裝的 CodeRef 及 Case 包裝的日期屬性 | 資料處理錯誤 | [`b50d94a`](../../commit/b50d94a) |
| BUG-031 | 🐛 bugfix | 2026-02-21 | eCQM 種子資料（後端） | 種子 CQL 語法錯誤導致 DataRequirements 標籤頁空白 | 資料處理錯誤 | [`63a5781`](../../commit/63a5781) |
| BUG-030 | 🐛 bugfix | 2026-02-21 | API 客戶端（前端） | departmentApi/ehrApi/indicatorApi 使用原生 axios 無 JWT 攔截器致 401 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| BUG-029 | 🐛 bugfix | 2026-02-21 | 通知系統（前後端） | SSE EventSource 無法傳送 Authorization 標頭致 401 | 架構缺陷 | [`63a5781`](../../commit/63a5781) |
| BUG-028 | 🐛 bugfix | 2026-02-21 | Docker 基礎設施（後端） | DataInitializer 僅在 dev profile 啟用，Docker 環境無種子資料 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| BUG-027 | 🐛 bugfix | 2026-02-21 | Docker 基礎設施（後端） | PatientImportEntity @Lob 與 PostgreSQL schema validation 不相容 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| BUG-026 | 🐛 bugfix | 2026-02-21 | Docker 基礎設施（後端） | Flyway V24-V29 使用 H2 語法導致 PostgreSQL 部署失敗 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| BUG-025 | 🐛 bugfix | 2026-02-21 | CDS Authoring（後端） | CQL 產生器未對 list 型別表達式自動加 exists() 導致驗證失敗 | 邏輯錯誤 | [`0d418f1`](../../commit/0d418f1) |
| BUG-024 | 🐛 bugfix | 2026-02-21 | eCQM 資料需求（後端） | DataRequirements 未解析 Equal/Equivalent + CodeRef 模式（如 E.class ~ "AMB"） | 資料處理錯誤 | [`53b19ca`](../../commit/53b19ca) |
| BUG-023 | 🐛 bugfix | 2026-02-21 | Test Case Builder（後端） | Encounter.class 下拉選單顯示 1115 個代碼而非 11 個 | 資料處理錯誤 | [`3fc6de0`](../../commit/3fc6de0) |
| PAT-005 | ✨ patch | 2026-02-20 | 安全性 | MeasureController 授權與 IDOR 修復 | Backend — MeasureController, ScheduledMeasureEvaluationService | [`b8f57a6`](../../commit/b8f57a6) |
| PAT-004 | ✨ patch | 2026-02-20 | 跨模組 | 術語查詢 Drawer + 測試案例草稿自動儲存 | Header, Terminology, Test Case Builder, Measures | [`7429103`](../../commit/7429103) |
| PAT-003 | ✨ patch | 2026-02-20 | i18n | 全平台國際化完成（Phase 5-9） | CDS, FHIR, Terminology, Authoring, Admin | [`b2b7b07`](../../commit/b2b7b07) |
| BUG-022 | 🐛 bugfix | 2026-02-20 | Test Cases（前端） | 測試案例結果表格族群名稱未中文化 | i18n 遺漏 | [`0260852`](../../commit/0260852) |
| BUG-021 | 🐛 bugfix | 2026-02-20 | eCQM 資料需求（後端） | DataRequirements 未解析 FHIRHelpers.ToString 包裝的屬性比較 | 資料處理錯誤 | [`66a9ee2`](../../commit/66a9ee2) |
| BUG-020 | 🐛 bugfix | 2026-02-20 | eCQM 資料需求（前後端） | DataRequirements 標籤頁未顯示 Where 子句中的篩選條件 | 架構缺陷 | [`8efa589`](../../commit/8efa589) |
| BUG-019 | 🐛 bugfix | 2026-02-20 | CDS Hooks Sandbox（後端） | CDS Prefetch 執行清除 patientId 導致 Patient context 失效 | 邏輯錯誤 | [`c08372c`](../../commit/c08372c) |
| ~~BUG-018~~ | 🐛 bugfix | 2026-02-20 | CQL Engine（後端） | ~~FHIR Coding→Code 轉換~~ **已撤回**（CQL Engine 已透過 FHIRHelpers 處理） | 誤判 | [`878deef`](../../commit/878deef) → reverted |
| BUG-017 | 🐛 bugfix | 2026-02-20 | CDS Hooks Sandbox（後端） | PrefetchRetrieveProvider 未展開 ValueSet 導致代碼過濾失效 | 架構缺陷 | [`878deef`](../../commit/878deef) |
| BUG-016 | 🐛 bugfix | 2026-02-20 | Test Case Builder（前後端） | CodeableConcept dropdown 使用 ValueSet URL 而非 CodeSystem URL | 資料處理錯誤 | [`6ca7a86`](../../commit/6ca7a86) |
| BUG-015 | 🐛 bugfix | 2026-02-20 | CQL Engine（後端） | VSAC ValueSet 未連接 CQL Engine 導致 CDS 規則失效 | 架構缺陷 | [`69dd9a1`](../../commit/69dd9a1) |
| BUG-014 | 🐛 bugfix | 2026-02-20 | Test Case Builder（前端） | CodeableConcept boundCodes 未使用下拉選單 | UX 設計缺陷 | [`69dd9a1`](../../commit/69dd9a1) |
| BUG-013 | 🐛 bugfix | 2026-02-20 | CQL Builder（前端） | TWCORE 選碼導致 Monaco Editor 白屏 | 配置遺漏 | [`4c9ae86`](../../commit/4c9ae86) |
| BUG-012 | 🐛 bugfix | 2026-02-20 | 跨模組（前端） | Monaco Editor 夜間模式白屏 | 配置遺漏 | [`e375b1e`](../../commit/e375b1e) |
| BUG-011 | 🐛 bugfix | 2026-02-20 | 版面配置（前端） | Footer 位置異常：flexbox 佈局修正 | UX 設計缺陷 | [`d82710d`](../../commit/d82710d) |
| BUG-010 | 🐛 bugfix | 2026-02-20 | CDS Hooks Sandbox（前後端） | CDS Card 顯示所有表達式擠在一行 | UX 設計缺陷 | [`5e69d32`](../../commit/5e69d32) |
| BUG-009 | 🐛 bugfix | 2026-02-20 | Test Case Builder（前端） | FHIR Choice Type 序列化錯誤（value → valueQuantity） | 資料處理錯誤 | [`5e69d32`](../../commit/5e69d32) |
| PAT-002 | ✨ patch | 2026-02-19 | i18n | Measures 模組國際化（en / zh-TW） | Measures, Dashboard, Test Case Builder | [`37a9827`](../../commit/37a9827) |
| PAT-001 | ✨ patch | 2026-02-19 | 跨模組 | UCUM 單位下拉選單統一 | Test Case Builder, CQL Builder, eQCM, Authoring | [`300bf0f`](../../commit/300bf0f) |
| BUG-008 | 🐛 bugfix | 2026-02-19 | CDS Hooks Sandbox（後端） | CDS Sandbox Invoke 所有 CQL 表達式回傳 null | 資料處理錯誤 | [`fccd012`](../../commit/fccd012) |
| BUG-007 | 🐛 bugfix | 2026-02-19 | 版面配置（前端） | Footer fixed 定位仍遮擋操作按鈕 | UX 設計缺陷 | [`b570119`](../../commit/b570119) |
| BUG-006 | 🐛 bugfix | 2026-02-19 | Backend 基礎設施 | Backend OOM 導致所有 API 無回應 | 配置遺漏 | [`660347a`](../../commit/660347a) |
| BUG-005 | 🐛 bugfix | 2026-02-19 | 版面配置（前端） | Footer 覆蓋頁面內容 | UX 設計缺陷 | [`741b7dc`](../../commit/741b7dc) |
| BUG-004 | 🐛 bugfix | 2026-02-19 | CDS Hooks Sandbox（前端） | CDS Sandbox 修改資料後無法重新執行 | 邏輯錯誤 | [`741b7dc`](../../commit/741b7dc) |
| BUG-003 | 🐛 bugfix | 2026-02-19 | Test Case Builder（前端） | Observation status 欄位允許自由輸入導致無效值 | UX 設計缺陷 | [`741b7dc`](../../commit/741b7dc) |
| BUG-002 | 🐛 bugfix | 2026-02-19 | CQL Translation（後端＋前端） | CQL 翻譯失敗時 Builder 無法顯示已解析的部分結構 | 邏輯錯誤 | [`f9b3e33`](../../commit/f9b3e33) |
| BUG-001 | 🐛 bugfix | 2026-02-19 | CQL Builder（前端） | CQL Builder 解析 CQL 靜默失敗 | 邏輯錯誤 | [`3ee28f8`](../../commit/3ee28f8) |

### 類型說明

| 標記 | 說明 |
|------|------|
| 🐛 bugfix | Bug 修復（原 BUGFIX_LOG） |
| ✨ patch | 功能改善 / 重構 / 新功能（原 PATCH_LOG） |

### 根因類型說明（bugfix）

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
| 安全漏洞 | 認證、授權、注入、XSS 等安全缺陷 |
| 測試遺漏 | 測試案例不足或驗證不完整 |

---

## 詳細記錄 — 🌐 i18n / ✨ Patch（PAT-027+）

## PAT-028 — eCQM 工作區存檔功能

- **日期**: 2026-03-05
- **範圍**: eCQM（前端）
- **內容**: eCQM 撰寫工作區原先僅有隱藏的 1500ms auto-save（無 UI 回饋），使用者無法得知變更是否已儲存。
- **修復**:
  - `EcqmArtifactWorkspaceHeader` — 新增 Save 按鈕（Ctrl+S tooltip）+ 存檔狀態指示器（儲存中… / 已儲存 / 有未儲存的變更 / 儲存失敗）
  - `EcqmArtifactWorkspace` — 新增 `SaveStatus` 狀態機（idle → dirty → saving → saved/error）、Ctrl+S 鍵盤快捷鍵、`useUnsavedChangesGuard` 瀏覽器離開防護、返回列表前確認對話框（捨棄 / 儲存並離開）、發佈前自動 flush 未儲存變更
  - i18n 新增 6 個翻譯 key（en + zh-TW）

---

## PAT-027 — eCQM 撰寫全模組 i18n 繁體中文翻譯

- **日期**: 2026-03-05
- **範圍**: eCQM（前端）
- **內容**: 為 eCQM 撰寫模組全部 12 個元件加入 i18next 國際化支援，新增 `ecqm` namespace 並建立英文 / 繁體中文翻譯 JSON。
- **修改清單**:
  - 新增 `frontend/src/locales/en/ecqm.json`（英文翻譯 ~120 strings）
  - 新增 `frontend/src/locales/zh-TW/ecqm.json`（繁體中文翻譯，領域術語保留英文附中文括號）
  - `frontend/src/i18n.ts` — 註冊 ecqm namespace
  - 12 元件加入 `useTranslation('ecqm')` 並替換所有硬編碼字串：
    `EcqmArtifactList`, `EcqmArtifactModal`, `EcqmArtifactWorkspace`, `EcqmArtifactWorkspaceHeader`,
    `EcqmSummaryTab`, `EcqmPopulationGroupsTab`, `EcqmPopulationGroupEditor`, `EcqmObservationEditor`,
    `EcqmSdeTab`, `EcqmStratifiersTab`, `EcqmCqlPreviewTab`（`EcqmPopulationTreeEditor` 無硬編碼字串，跳過）
- **翻譯策略**: 領域專有名詞採「英文原文（中文）」格式（如 `Proportion（比例）`、`Initial Population`），一般 UI 文字全中文化

---

## 詳細記錄 — 🐛 Bugfix（BUG-083+）

## BUG-083 — CQL Retrieve 使用 element_name 而非 code display + save 驗證錯誤未顯示

- **日期**: 2026-03-05
- **範圍**: CDS Authoring（前後端）
- **根因**: `ExpressionCqlEngine.buildGenericResourceExpression()` 迴圈遍歷所有 fields 時，`element_name` field 排第一且有非空字串 value，觸發 `value instanceof String` fallback 提前 return，永遠走不到帶有 codes/valueSets 的 observation field。同時前端 `saveFirst()` 失敗時錯誤被吞掉，使用者看不到驗證錯誤訊息。
- **修復**:
  - Backend: `buildGenericResourceExpression` 迴圈中跳過 `element_name` 和 `comment` metadata fields
  - Frontend: `ArtifactWorkspaceHeader` 和 `CqlPreviewPanel` 的 `saveFirst()` 加入 catch，用 `extractApiError` + `extractApiErrorDetails` 顯示錯誤 Alert
  - 新增 `extractApiErrorDetails()` 提取後端 `ValidationException.details` 陣列
  - 修正 3 個測試的資料結構（valueSets 從 element_name field 移到正確的 observation field）
- **影響**: `[Observation: "LDL>130"]` → `[Observation: "LDL from lipid profile"]`（正確使用 code display）

---

## 詳細記錄 — ✨ Patch（功能 / 重構）

## PAT-026 — CQL 注入修復 + 儲存型 XSS 修復

- **日期**: 2026-03-05
- **範圍**: 安全性
- **分類**: 注入防護 / XSS 防護

### 問題描述

安全審查發現三類漏洞：

1. **CQL 注入**（MEDIUM）：多個 modifier 和 element 的使用者輸入值被直接嵌入 CQL 單引號字串字面值內，未呼叫 `escapeCqlString()`。攻擊者可透過包含單引號的值（如 `test' or true --`）注入任意 CQL 邏輯。
2. **CQL 注入**（MEDIUM）：Error Statement 的 `thenClause` / `elseClause` 同樣未逸出。
3. **儲存型 XSS**（MEDIUM）：多個 React 元件使用 `dangerouslySetInnerHTML` + i18next 插值，但全域 i18n 設定 `escapeValue: false`，導致使用者控制的名稱（如 base element name）可注入 HTML/JavaScript。

### 修復方案

#### Fix 1：CQL 字串逸出補齊

在 `ExpressionCqlEngine.applyModifier()` 中，所有嵌入 CQL `'...'` 字串字面值的使用者輸入均加上 `escapeCqlString()`：

| Modifier / Element | 欄位 | 檔案位置 |
|--------------------|------|----------|
| EqualsString | `value` | ExpressionCqlEngine.java |
| StartsWithString | `value` | ExpressionCqlEngine.java |
| EndsWithString | `value` | ExpressionCqlEngine.java |
| ConvertUnits | `unit` | ExpressionCqlEngine.java |
| WithUnit | `unit` | ExpressionCqlEngine.java |
| LookBackModifier | `unit` | ExpressionCqlEngine.java |
| ContainsQuantity | `unit` | ExpressionCqlEngine.java |
| ValueComparisonNumber/Observation | `unit` | ExpressionCqlEngine.java |
| Gender | `gender` | ExpressionCqlEngine.java |

#### Fix 2：Error Statement 逸出

在 `CqlArtifactBuilder.buildErrorStatement()` 中，`thenClause` 和 `elseClause` 加上 `engine.escapeCqlString()`。

#### Fix 3：XSS — 選擇性啟用 HTML 逸出

在所有使用 `dangerouslySetInnerHTML` + `t()` 且插值包含使用者控制值的位置，加上 `interpolation: { escapeValue: true }`：

| 元件 | 插值變數 |
|------|----------|
| `BaseElements.tsx` | `name` |
| `Parameters.tsx` | `name` |
| `Subpopulations.tsx` | `name` |
| `AdminUsersPage.tsx` | `username` |
| `ManageServicesPanel.tsx` | `name` |
| `ArtifactElementBody.tsx` | `input`, `output` |
| `PopulationCriteriaTab.tsx` | `scoringType` |

### 驗證

- Backend 全部 80 tests 通過
- Frontend TypeScript 編譯通過
- `escapeCqlString()` 已處理 `\` → `\\` 和 `'` → `\'` 兩種逸出
- i18next `interpolation.escapeValue: true` 會將 `<`、`>`、`&`、`"` 轉為 HTML entities

---

## PAT-025 — FreeMarker 模板引擎遷移 + Conjunction 前端重構

- **日期**: 2026-03-05
- **範圍**: 重構
- **分類**: 架構改善 / 可維護性

### 問題描述

CQL 產生器（`CqlArtifactBuilder`、`ExpressionCqlEngine`、`EcqmCqlBuilder`）原先使用大量 `String.format()` 和 `StringBuilder` 拼接 CQL 程式碼，switch-case 內嵌多行字串不易維護。前端 conjunction tree 元件也有重複的 conjunction kind 解析邏輯和顏色映射。

### 實作方案

#### Phase 1：FreeMarker 依賴 + 模板引擎

- `pom.xml` 加入 `spring-boot-starter-freemarker`
- 新增 `CqlTemplateEngine.java` — 封裝 FreeMarker Configuration，從 `classpath:templates/cql/` 載入模板

#### Phase 2：30 個 FreeMarker 模板

| 目錄 | 模板 | 數量 |
|------|------|------|
| `/` | `artifact.ftl`、`ecqm-artifact.ftl` | 2 |
| `modifiers/` | CheckExistence、BooleanNot、Count、AllTrue、AnyTrue、BooleanComparison、ConvertUnits、WithUnit、LookBackModifier、EqualsString、StartsWithString、EndsWithString、BeforeTime、AfterTime、ContainsValue、IsTrue、IsNotTrue、IsFalse、IsNotFalse | 19 |
| `elements/` | AgeRange、Gender、GenericResource | 3 |
| `fragments/` | cds-card、error-statement | 2 |
| `parameters/` | defaults | 1 |
| `ecqm/` | standard-sde | 1 |

#### Phase 3：Java Context Builder 重構

- `CqlArtifactBuilder.buildCql()` → 組裝 data model Map → `templateEngine.render("artifact.ftl", dataModel)`
- `EcqmCqlBuilder.buildEcqmCql()` → 組裝 data model Map → `templateEngine.render("ecqm-artifact.ftl", dataModel)`
- `ExpressionCqlEngine.applyModifier()` → 每個 modifier 呼叫 `renderModifier("XxxModifier.ftl", model)`
- 刪除死碼 `emitStandardSde()`

#### Phase 4：前端 Conjunction 共用化

- 新增 `conjunctionTreeUtils.ts` — 匯出 `resolveKind()`、`CONJ_CYCLE`、`nextConjunction()`、`conjColor()`、`changeConnectorAt()`、`addSubGroup()`、`simplifyTree()`
- `ConjunctionGroup.tsx` 和 `ConjunctionConnector.tsx` 改為從共用模組匯入，移除重複定義
- `EcqmArtifactWorkspace.tsx` — `localArtifact` 包 `useMemo` 避免不必要的子元件重繪
- `handleConnectorChange` 改用已計算的 `filteredChildren` memo

#### Phase 5：Code Review 修正

三重平行代碼審查（Reuse / Quality / Efficiency）後修正：

| 嚴重度 | 修正 |
|--------|------|
| HIGH | 死碼 `emitStandardSde()` 留在 `EcqmCqlBuilder` → 刪除 |
| MEDIUM | `resolveKind` 在 `ConjunctionGroup.tsx` 手寫 ternary chain → 改用 `conjunctionTreeUtils.resolveKind()` |
| MEDIUM | `CONJ_CYCLE` + `nextConjunction` + `conjColor` 在兩個元件重複 → 提取至共用模組 |
| MEDIUM | `localArtifact` 每次 render 建立新物件 → `useMemo` |
| LOW | `handleConnectorChange` 重複過濾 → 使用已有的 `filteredChildren` |

### 影響範圍

| 項目 | 數量 |
|------|------|
| 新增後端檔案 | 1（CqlTemplateEngine） + 30 模板 |
| 修改後端檔案 | 6（CqlArtifactBuilder、ExpressionCqlEngine、EcqmCqlBuilder、ModifierService、TemplateService、ExpressionTreeValidator） |
| 新增後端測試 | 33 snapshot tests |
| 新增前端檔案 | 3（ConjunctionConnector、conjunctionTreeUtils、modifierUtils） |
| 修改前端檔案 | 12 |

### 驗證

- Backend 全部 80 tests 通過（51 ExpressionCqlEngine + 11 CqlArtifactBuilder + 18 EcqmCqlBuilder）
- Frontend TypeScript 編譯通過（`npx tsc --noEmit`，零錯誤）
- CQL 產出與重構前完全一致（snapshot 比對）

---

## PAT-024 — eCQM 視覺化 CQL 產生引擎 — 全端實作 + Publish 至 MeasureDefinition

- **日期**: 2026-03-04
- **範圍**: eCQM — 全新視覺化 eCQM Authoring 模組
- **分類**: 新功能 / 跨模組整合
- **參考依據**: CMS 2026 eCQM Logic and Implementation Guidance Version 9.0

### 問題描述

平台既有 CDS Authoring Tool 可視覺化建構 expression tree 並產生 CDS 結構 CQL（Inclusion/Exclusion/Recommendation），Measure 模組可評估 eCQM，但 eCQM 的 CQL 需手寫。兩個系統之間缺乏橋接，無法從視覺化工具直接產出符合 eCQM 規範的 CQL（IP/Denom/Numer/SDE/Stratifier）並發布至 MeasureDefinition 評估管線。

**目標**：建立獨立的 eCQM Authoring 模組，複用 CDS expression tree 引擎，產生 eCQM 結構的 CQL，並透過「發布」功能橋接至 MeasureDefinition 評估管線。

### 設計決策

| 決策 | 選擇 | 理由 |
|------|------|------|
| CQL 引擎共用 | 抽取 `ExpressionCqlEngine` 共用類別 | CDS 和 eCQM 共用 expression → CQL 轉換邏輯，避免重複 |
| 資料模型 | 獨立 `ecqm_artifact` 表 | 與 CDS artifact 解耦，scoring type / population basis 等欄位為 eCQM 專有 |
| Population 結構 | ConjunctionGroup JSON（同 CDS） | 完全複用前端 ConjunctionGroup 元件 |
| Scoring Types | proportion / ratio / continuous-variable / cohort | 完整實作 CMS v9.0 §2 四種 scoring type |
| Ratio 雙 IP | `initialPopulationDenom` + `initialPopulationNumer` | CMS 允許 ratio 分子和分母有不同 Initial Population |
| Episode-based | `populationBasis` 欄位 | Patient-based (Boolean) 或 Encounter/Procedure 等 FHIR resource type |
| 前端複用 | 直接複用 ConjunctionGroup + ArtifactElement 元件 | 零修改既有元件，只包裝 eCQM 專用 tabs |
| 發布管線 | eCQM → MeasureDefinition → 既有評估管線 | 不新建評估引擎，複用現有 measure 評估流程 |

### 修改內容

#### Phase 1：共用 CQL 引擎抽取

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/authoring/ExpressionCqlEngine.java` |
| 修改 | `backend/.../service/authoring/CqlArtifactBuilder.java` — 委派至 ExpressionCqlEngine |

- 從 `CqlArtifactBuilder`（1056 行）抽取 expression → CQL 通用邏輯為獨立類別
- 搬移方法：`BuildContext`、`buildConjunctionExpression()`、`buildExpression()`、`applyModifier()`、`buildGenericResourceExpression()`、`collectDeclarations()`、emit helpers 等
- CDS `CqlArtifactBuilder` 改為注入 `ExpressionCqlEngine` 並委派
- **零行為變更** — 所有既有 CDS CQL 產生測試通過

#### Phase 2：EcqmArtifactEntity + Repository + Migration

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../entity/EcqmArtifactEntity.java` |
| 新增 | `backend/.../repository/EcqmArtifactRepository.java` |
| 新增 | `backend/.../resources/db/migration/V39__ecqm_artifacts.sql` |

- JPA 實體含 `scoringType`、`populationBasis`、`improvementNotation` 等 eCQM 專有欄位
- JSON 欄位（`populationGroups`、`supplementalData`、`stratifiers`、`baseElements`、`parameters`）使用 `@Transient` 反序列化 + `@PrePersist`/`@PreUpdate` 序列化
- 外鍵關聯 `measure_definition(id) ON DELETE SET NULL`

#### Phase 3：EcqmCqlBuilder

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/ecqm/EcqmCqlBuilder.java` |
| 新增 | `backend/.../model/ecqm/EcqmConstants.java` |

核心 CQL 產生邏輯：
- `buildEcqmCql()` — 主入口，接收 artifact 資料產出完整 CQL
- `emitPopulationDefine()` — 從 population tree 產生 define（Boolean / List return type）
- `emitDualInitialPopulations()` — Ratio 雙 IP 情境（`"Initial Population 1"` / `"Initial Population 2"`）
- `emitObservationFunction()` — Continuous Variable 的 measure observation function
- `emitSupplementalDataDefines()` — 標準 SDE defines（Ethnicity/Race/Sex/Payer）+ 自訂 expression tree
- `emitGroupStratifiers()` — per-group stratifier defines（ratio 雙 IP 時跳過並警告）
- `validateScoringPopulations()` — 依 scoring type 驗證必填 population
- 多 group 名稱後綴支援避免 define 名稱衝突

**Scoring Type → 必填 Population 對照（CMS v9.0 §2）：**

| Scoring | 必填 | 選填 |
|---------|------|------|
| proportion | IP, Denom, Numer | Denom Excl, Denom Except, Numer Excl |
| ratio | IP, Denom, Numer | Denom Excl, Numer Excl |
| continuous-variable | IP, Measure Pop, Measure Obs | Measure Pop Excl |
| cohort | IP | — |

#### Phase 4：Service 層 + Controller + DTOs

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/ecqm/EcqmArtifactService.java` — CRUD（list, get, create, update, delete, duplicate） |
| 新增 | `backend/.../service/ecqm/EcqmCqlGenerationService.java` — 載入 entity → 調用 EcqmCqlBuilder → CQL/ELM/validate |
| 新增 | `backend/.../service/ecqm/EcqmPublishService.java` — 產生 CQL + 建立/更新 MeasureDefinition（GroupDefinition mapping） |
| 新增 | `backend/.../service/ecqm/EcqmExpressionTreeValidator.java` — XSS 過濾（14 patterns）、template/modifier ID 驗證 |
| 新增 | `backend/.../controller/EcqmController.java` — REST API |
| 新增 | `backend/.../model/ecqm/EcqmArtifactRequest.java` |
| 新增 | `backend/.../model/ecqm/EcqmArtifactResponse.java` |
| 新增 | `backend/.../model/ecqm/EcqmArtifactSummary.java` |
| 新增 | `backend/.../model/ecqm/PublishResult.java` |

**API 端點（`/api/ecqm`）：**

| 端點 | 方法 | 說明 |
|------|------|------|
| `/artifacts` | GET | 列出使用者的 eCQM artifacts |
| `/artifacts/{id}` | GET/PUT/DELETE | 單一 artifact CRUD |
| `/artifacts` | POST | 建立 artifact |
| `/artifacts/{id}/duplicate` | POST | 複製 artifact |
| `/artifacts/{id}/cql` | POST | 產生 CQL |
| `/artifacts/{id}/elm` | POST | 產生 CQL + 翻譯為 ELM |
| `/artifacts/{id}/validate` | POST | 驗證 CQL |
| `/artifacts/{id}/publish` | POST | 發布至 MeasureDefinition |
| `/templates` | GET | 取得元素模板 |
| `/modifiers` | GET | 取得修飾器 |
| `/scoring-types` | GET | 取得 scoring type 設定 |

#### Phase 5：Frontend — 全新 eCQM 工作區

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/pages/EcqmPage.tsx` — 主頁面（列表 + 工作區切換） |
| 新增 | `frontend/src/api/ecqmApi.ts` — API client |
| 新增 | `frontend/src/types/ecqm.ts` — TypeScript 型別定義 |
| 新增 | `frontend/src/hooks/useEcqm.ts` — React Query hooks |
| 新增 | `frontend/src/constants/ecqmConstants.ts` — Scoring types、population types、SDE 模板 |
| 修改 | `frontend/src/App.tsx` — 加入 `/ecqm` lazy route |
| 修改 | `frontend/src/api/index.ts` — 加入 ecqmApi export |
| 修改 | `frontend/src/components/layout/Header.tsx` — 加入 eCQM 導航項目 |

**eCQM Workspace（8 tabs）：**

| Tab | 元件 | 複用 |
|-----|------|------|
| Summary | `EcqmSummaryTab.tsx` | 新建 |
| Population Groups | `EcqmPopulationGroupsTab.tsx` + `EcqmPopulationGroupEditor.tsx` + `EcqmPopulationTreeEditor.tsx` | 新建，包裝 ConjunctionGroup |
| Base Elements | placeholder | 複用 CDS 元件模型 |
| Parameters | placeholder | 複用 CDS 元件模型 |
| Supplemental Data | `EcqmSdeTab.tsx` | 新建 |
| Stratifiers | `EcqmStratifiersTab.tsx` | 新建 |
| External CQL | placeholder | 複用 CDS 元件模型 |
| Review CQL | `EcqmCqlPreviewTab.tsx` | 新建 |

其餘新建元件：
- `EcqmArtifactList.tsx` — artifact 列表
- `EcqmArtifactModal.tsx` — 建立 modal（scoring type + population basis 選擇）
- `EcqmArtifactWorkspace.tsx` — 主工作區（debounced auto-save + useRef 穩定回調）
- `EcqmArtifactWorkspaceHeader.tsx` — 標題列（名稱 / 版本 / 狀態 / 發布按鈕）
- `EcqmObservationEditor.tsx` — Continuous Variable observation 編輯（criteria tree + aggregateMethod 下拉）

#### Phase 6：Backend 測試

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/authoring/ExpressionCqlEngineTest.java` — 21 tests |
| 新增 | `backend/.../service/ecqm/EcqmCqlBuilderTest.java` — 18 tests |
| 新增 | `backend/.../service/ecqm/EcqmPublishServiceTest.java` — 4 tests |
| 新增 | `backend/.../controller/EcqmControllerTest.java` — 13 tests（@SpringBootTest + MockMvc） |

#### Phase 7：Code Review 修正

三重平行代碼審查（Reuse / Quality / Efficiency）後修正：

| 嚴重度 | 修正 |
|--------|------|
| HIGH | `validateCql()` 與 `generateAndTranslate()` 完全重複 → 改為委派 |
| HIGH | Entity `baseElementsList` 反序列化 bug（檢查 transient 欄位而非 JSON 欄位）→ 修正 |
| HIGH | XSS validator 僅 3 patterns 弱於 `NoXssValidator` 的 14 patterns → 對齊 |
| HIGH | `save` callback 依賴 `artifact` object ref 導致 debounce 重置 → `useRef` 穩定化 |
| MEDIUM | 手動 `refetchArtifact()` / `refetchList()` 與 React Query invalidation 重複 → 移除 |
| MEDIUM | `DEFAULT_CONJUNCTION_GROUP` spread 可能被意外 mutate → `Object.freeze()` + factory function |
| MEDIUM | 冗餘 `entity.serializeAll()` 呼叫（JPA `@PreUpdate` 已處理）→ 移除 |
| MEDIUM | `emitPopulationDefine()` 未使用的 `isEpisodeBased` / `populationBasis` 參數 → 清除 |
| LOW | SDE tab 使用 array index 作為 React key → 改用 SDE name |
| LOW | 死碼 `useGenerateEcqmElm` hook → 移除 |

### 影響範圍

| 項目 | 數量 |
|------|------|
| 新增後端檔案 | 14 |
| 修改後端檔案 | 3 |
| 新增後端測試 | 4（56 tests） |
| 新增前端檔案 | 17 |
| 修改前端檔案 | 3 |

### 驗證

- Backend 全部 64 新 tests 通過（21 ExpressionCqlEngine + 18 EcqmCqlBuilder + 4 EcqmPublish + 13 EcqmController + 8 CDS 迴歸）
- Frontend TypeScript 編譯通過（`npx tsc --noEmit`，零錯誤）
- CDS Authoring 迴歸測試通過（CqlArtifactBuilderTest 8/8）
- 四種 scoring type CQL 產出驗證（proportion / ratio / continuous-variable / cohort）
- Ratio 雙 IP 產生 `"Initial Population 1"` / `"Initial Population 2"`
- Continuous Variable 產生 function（非 define）含 aggregateMethod 註解
- Publish → MeasureDefinition 建立成功，GroupDefinition 正確映射

---

## PAT-023 — JWT Refresh Token 滑動視窗過期 — 雙令牌架構 + 令牌輪換 + 重用偵測

- **日期**: 2026-03-04
- **範圍**: 安全性 — 認證架構升級
- **分類**: 安全性 / 使用者體驗

### 問題描述

平台原本使用單一 JWT Access Token（24 小時過期）。Token 過期後使用者被強制重新登入，無法優雅恢復。這導致 UX 問題（工作進行中被中斷）及安全性二元取捨（長過期 = 不安全，短過期 = 體驗差）。

**目標**：實作雙令牌架構（短效 Access Token + 長效 Refresh Token），搭配滑動視窗過期與令牌輪換，同時達到強安全性與無縫使用者體驗。

### 設計決策

| 決策 | 選擇 | 理由 |
|------|------|------|
| Refresh Token 儲存 | httpOnly Cookie | XSS 防護（JavaScript 無法存取） |
| Access Token 有效期 | 15 分鐘 | 短暫曝露窗口 |
| Refresh Token 有效期 | 7 天（滑動視窗） | 每次使用後重設 |
| 絕對 Session 上限 | 30 天 | 硬性截止，無論滑動視窗如何延展 |
| 令牌輪換 | 每次 refresh 發放新 token | 舊 token 立即作廢 |
| 重用偵測 | 偵測到重用 → 撤銷整個 family | 令牌洩漏時全面防護 |

### 修改內容

#### 1. Flyway Migration V37 — refresh_token 表

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../db/migration/V37__refresh_tokens.sql` |

- `token_hash`（SHA-256）、`family_id`（UUID 輪換鏈）、`expires_at`（滑動）、`absolute_expires_at`（固定）
- 外鍵關聯 `app_user(id) ON DELETE CASCADE`
- 索引：token_hash（唯一）、user_id、family_id、expires_at

#### 2. 組態更新 — application.yml

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../resources/application.yml` — 拆分 `jwt.expiration-ms` 為 `access-expiration-ms` / `refresh-expiration-ms` / `absolute-session-ms`，新增 `refresh-token.cookie-secure` |
| 修改 | `backend/.../resources/application-dev.yml` — `cookie-secure: false`（HTTP 開發環境） |
| 修改 | `backend/.../test/resources/application-test.yml` — 同步更新 |

#### 3. Entity + Repository

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../entity/RefreshTokenEntity.java` — `isExpired()` 同時檢查滑動與絕對過期 |
| 新增 | `backend/.../repository/RefreshTokenRepository.java` — `revokeByFamilyId()`、`revokeByUserId()`、`deleteExpiredOrRevoked()` |

#### 4. JwtTokenProvider — 拆分過期配置

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../security/JwtTokenProvider.java` — 建構函式接受 4 參數，`generateToken()` 使用 `accessExpirationMs`，保留 `getExpirationMs()` 向後相容 |

#### 5. RefreshTokenCookieUtil — Cookie 管理

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../security/RefreshTokenCookieUtil.java` — `addRefreshTokenCookie()`（httpOnly + Secure + SameSite=Strict + Path=/api/auth）、`clearRefreshTokenCookie()`、`extractRefreshToken()` |

#### 6. RefreshTokenService — 核心商業邏輯

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/RefreshTokenService.java` |

| 方法 | 功能 |
|------|------|
| `createTokenPair(UserEntity)` | 登入時建立新 family UUID，儲存雜湊 refresh token，回傳 access + refresh |
| `refreshTokens(String)` | 輪換：驗證 → 撤銷舊 → 發放新（同 familyId + absoluteExpiresAt），滑動視窗受絕對上限約束 |
| `revokeByToken(String)` | 單裝置登出：撤銷整個 family |
| `revokeAllForUser(Long)` | 全裝置登出 |
| `cleanupExpiredTokens()` | `@Scheduled(cron = "0 0 3 * * *")` 排程清理 |

**重用偵測**：若呈示的 token 已是 `revoked=true`，記錄警告、撤銷整個 family、拋出 `RefreshTokenReuseException`。

#### 7. AuthController — 新端點 + 修改登入

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../controller/AuthController.java` |

| 端點 | 認證 | 變更 |
|------|------|------|
| `POST /login` | permitAll | 改用 `createTokenPair()`，設定 refresh cookie |
| `POST /register` | permitAll | 同上 |
| `POST /okta/callback` | permitAll | 同上 |
| `POST /api/auth/refresh` | permitAll（新增） | 讀取 cookie → `refreshTokens()` → 設定新 cookie + 回傳新 access token |
| `POST /api/auth/logout` | permitAll（新增） | 讀取 cookie → `revokeByToken()` → 清除 cookie |

#### 8. SecurityConfig — 放行新端點

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../config/SecurityConfig.java` — `/api/auth/refresh` 與 `/api/auth/logout` 加入 `permitAll()` |

#### 9. Frontend — Axios 攔截器改造

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/api/client.ts` |

- `withCredentials: true`（跨域傳送 cookie）
- 401 攔截改為靜默 refresh + 請求佇列：
  - `isRefreshing` 旗標防止並行 refresh
  - `failedQueue` 陣列暫存等待中的請求
  - Refresh 成功：更新 localStorage token，重試原始請求，處理佇列
  - Refresh 失敗：清除 localStorage，導向 /login，拒絕佇列
- 派發 `CustomEvent('token-refreshed')` 同步 Redux

#### 10. Frontend — authApi / authSlice / Header / App / types

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/api/authApi.ts` — 新增 `refresh()` 與 `logout()` 方法 |
| 修改 | `frontend/src/store/authSlice.ts` — 新增 `updateToken` reducer |
| 修改 | `frontend/src/components/layout/Header.tsx` — 登出時呼叫 `authApi.logout()` |
| 修改 | `frontend/src/App.tsx` — 監聽 `token-refreshed` 事件 → `dispatch(updateToken(...))` |
| 修改 | `frontend/src/types/index.ts` — 新增 `RefreshResponse` 型別 |

#### 11. 後端測試

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/RefreshTokenServiceTest.java` — 11 測試案例 |
| 修改 | `backend/.../controller/AuthControllerTest.java` — 新增 6 個 refresh/logout 測試 |
| 修改 | `backend/.../security/JwtTokenProviderTest.java` — 更新建構函式為 4 參數 |

**RefreshTokenServiceTest 涵蓋：**
1. `createTokenPair` — 有效配對、雜湊儲存
2. `refreshTokens` — 有效輪換、同 family 繼承
3. 重用偵測 — 已撤銷 token → 整個 family 撤銷
4. 過期 token → `InvalidRefreshTokenException`
5. 絕對過期超過 → `InvalidRefreshTokenException`
6. 停用使用者 → family 撤銷 + 例外
7. 滑動視窗受絕對上限約束
8. Token 未找到 → 例外
9. `revokeByToken` / `revokeAllForUser`
10. `cleanupExpiredTokens`

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 6 |
| 修改檔案 | 14 |
| 新增行數 | +1024 / -36 |

### 驗證

- Backend 編譯成功（`mvn compile`）
- RefreshTokenServiceTest — 11/11 通過
- AuthControllerTest — 14/15 通過（1 個預存失敗與本次無關）
- JwtTokenProviderTest — 全數通過
- Frontend TypeScript 型別檢查通過（零新增錯誤）
- 多分頁測試：2 個分頁同時 access token 過期，僅觸發 1 次 refresh（請求佇列機制）

---

## PAT-022 — TWCORE IG 範本支援（19 預設範本 + TW 代碼系統 + 目錄擴充）

- **日期**: 2026-02-28
- **範圍**: Authoring — 功能增強
- **分類**: 功能增強 / 台灣核心實作指引（TWCORE IG）

### 問題描述

Authoring 規則編寫功能僅使用通用 FHIR R4 範本（每個資源類型一個）。使用者每次都必須手動從 TWCORE 目錄分頁挑選代碼。目標是新增 **TWCORE 專用預設範本**（如「BMI 觀測」、「糖尿病」），讓使用者可直接針對台灣核心 IG Profile 撰寫規則，減少操作步驟。

### 修改內容

#### 1. 新增 19 個 TWCORE 範本至 formTemplates.json

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../resources/data/formTemplates.json` — 新增 19 個 TWCORE 範本，分布於 Observations(9)、Conditions(7)、Medications(2)、AllergyIntolerances(1)，各自帶有 `twcoreOnly: true` 旗標及預設 LOINC/SNOMED 代碼 |

**Observations (9)：** BMI 觀測、血壓觀測、體重觀測、身高觀測、體溫觀測、心率觀測、血糖觀測、糖化血色素觀測、檢驗結果(通用)
**Conditions (7)：** 糖尿病、高血壓、心臟衰竭、慢性腎臟病、氣喘、慢性阻塞性肺病、病情(通用)
**Medications (2)：** 藥品處方(TWCORE)、用藥紀錄(TWCORE)
**AllergyIntolerances (1)：** 過敏(TWCORE)

所有 TWCORE 範本重用與通用範本相同的 `template` 欄位（如 `GenericObservation`），CQL 生成流程無需任何修改。

#### 2. Backend — 解析 twcoreOnly 旗標

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../model/authoring/FormTemplate.java` — 新增 `private Boolean twcoreOnly` 欄位 |
| 修改 | `backend/.../service/authoring/TemplateService.java` — `parseTemplate()` 新增 `setTwcoreOnly()` |

#### 3. Backend — 新增台灣代碼系統

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../model/authoring/AuthoringConstants.java` — `CODE_SYSTEM_NAMES` 從 `Map.of()` 改為 `Map.ofEntries()`，新增 ICD-10-CM-TW、ICD-10-PCS-TW、ATC、FDA-TW 四個台灣代碼系統 |

#### 4. Frontend — TWCORE 模式切換與範本篩選

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/types/authoring.ts` — `FormTemplate` 介面新增 `twcoreOnly?: boolean` |
| 修改 | `frontend/src/components/authoring/ArtifactWorkspace.tsx` — 新增 TWCORE 模式切換開關（工具列右側），`twcoreMode` state 傳遞至 ConjunctionGroup / Subpopulations / BaseElements |
| 修改 | `frontend/src/components/authoring/element-select/ElementSelectDropdown.tsx` — 接受 `twcoreMode` prop，關閉時隱藏 `twcoreOnly` 範本，顯示 "TW" Chip 標記 |
| 修改 | `frontend/src/components/authoring/element-select/ElementSelect.tsx` — 傳遞 `twcoreMode`，建立元素時複製 `codes` / `valueSets` 至欄位 |
| 修改 | `frontend/src/components/authoring/builder/ConjunctionGroup.tsx` — 新增 `twcoreMode` prop 傳遞 |
| 修改 | `frontend/src/components/authoring/subpopulations/Subpopulations.tsx` — 新增 `twcoreMode` prop 傳遞 |
| 修改 | `frontend/src/components/authoring/base-elements/BaseElements.tsx` — 新增 `twcoreMode` prop 傳遞 |

#### 5. 擴充 TWCORE 目錄

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../resources/data/twcoreCatalog.json` — 新增 LOINC 2345-7（血漿葡萄糖）與 4548-4（HbA1c）至血糖分類；新增 MedicationRequest ATC 值集（21 碼 / 4 類別）；新增 Procedure ICD-10-PCS-TW 值集（10 碼）；新增藥物過敏分類（7 碼） |

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 12 |
| 新增檔案 | 0 |
| 新增範本 | 19（9 Observation + 7 Condition + 2 Medication + 1 AllergyIntolerance） |
| 新增代碼系統 | 4（ICD-10-CM-TW、ICD-10-PCS-TW、ATC、FDA-TW） |
| 新增行數 | +562 / -22 |

### 驗證

- JSON 格式驗證 — `formTemplates.json` 與 `twcoreCatalog.json` 均通過 Python json.load 驗證
- `tsc --noEmit` — 通過，零錯誤
- VS Code diagnostics — 所有修改檔案零警告
- 設計驗證：TWCORE 範本使用與通用範本相同的 `template` 值，CQL 生成流程（`collectFromTree()` → `codesystem`/`code` 宣告）不受影響
- TWCORE 模式關閉時，19 個新範本完全隱藏（回歸正常）

---

## PAT-021 — Hospital-Grade 改善：draftOrders + 預取解析 + 重大警示彈窗

- **日期**: 2026-02-27
- **範圍**: CDS Hooks — 功能增強
- **分類**: 功能增強 / 醫院級合規

### 問題描述

現有 CDS 實作在三個關鍵面向與醫院生產系統存在差距：
1. **draftOrders 未處理**：order-select / order-sign hooks 的 `context.draftOrders` 從未被解析或合併至 prefetch provider，沙盒 UI 也無法輸入草稿醫令
2. **重大警示無差異化**：所有卡片（info / warning / critical）呈現方式相同，醫院系統應以阻斷式彈窗處理 critical 卡片
3. **預取範本未動態解析**：當 prefetch 資料缺失時，系統直接回退至 FHIR server URL，未依 CDS Hooks 規範動態解析預取範本

### 修改內容

#### Feature 1：draftOrders 處理（order-select / order-sign）

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../service/cds/CdsInvocationService.java` — 新增 `parseDraftOrders()` 方法，解析 context.draftOrders 為 FHIR Bundle 並合併資源至 prefetch provider |
| 修改 | `backend/.../service/cds/PrefetchRetrieveProvider.java` — 新增 `addResources()` 方法 + ServiceRequest code 比對支援 |
| 修改 | `backend/.../model/cds/CdsSandboxRequest.java` — 新增 `draftOrders` 欄位 |
| 修改 | `backend/.../controller/CdsHooksController.java` — sandbox handler 注入 draftOrders 至 CdsRequest context |
| 修改 | `frontend/src/types/index.ts` — `CdsSandboxRequest` 新增 `draftOrders` 欄位，`CdsCard` 新增 `overrideReasons` 欄位 |
| 修改 | `frontend/src/components/cds/SandboxPanel.tsx` — 新增 Draft Orders JSON 編輯器分頁（僅 order-select / order-sign 可見） |

#### Feature 2：重大警示阻斷式彈窗

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/components/cds/CriticalCardDialog.tsx` — MUI Dialog，`disableEscapeKeyDown` + 阻斷背景點擊，Accept / Override 按鈕，Override 支援預設原因選單或自由文字 |
| 修改 | `frontend/src/components/cds/SandboxPanel.tsx` — 回應卡片分區（critical vs normal），critical 卡片以佇列依序顯示彈窗 |
| 修改 | `frontend/src/components/cds/InvokeServicePanel.tsx` — 同上，live invocation 面板整合 critical card dialog |
| 修改 | `backend/.../service/cds/CqlTupleCardStrategy.java` — 新增 `parseOverrideReasons()` 從 CQL Tuple 提取覆寫原因 |

#### Feature 3：預取範本動態解析

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/cds/PrefetchResolver.java` — `@Component`，替換 `{{context.patientId}}` 等範本變數，透過 FhirClientFactory 從客戶端 FHIR server 取得資源，支援 Bearer Token 認證 |
| 修改 | `backend/.../service/cds/CdsInvocationService.java` — 注入 PrefetchResolver，prefetch 缺失時嘗試動態解析範本，解析失敗才回退至 FHIR server URL |

#### i18n

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/locales/en/cds.json` — 新增 `sandbox.tabDraftOrders`、`sandbox.draftOrdersDescription`、`critical.*`（7 鍵） |
| 修改 | `frontend/src/locales/zh-TW/cds.json` — 對應繁體中文翻譯（9 鍵） |

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 10 |
| 新增檔案 | 2（`PrefetchResolver.java`、`CriticalCardDialog.tsx`） |
| 新增方法 | 5（`parseDraftOrders`、`addResources`、`parseOverrideReasons`、`resolve`、`substituteTemplate`） |
| 新增 i18n 鍵 | 18（9 en + 9 zh-TW） |
| 新增行數 | +580 / -8 |

### 驗證

- `tsc --noEmit` — 通過（僅預存 react-i18next 型別宣告警告）
- Sandbox → order-select hook → Draft Orders 分頁可見 → 輸入 MedicationRequest Bundle → CQL 可存取草稿藥物
- CQL 回傳 `indicator: "critical"` → 阻斷式彈窗 → 必須 Accept / Override 才能繼續
- 移除 prefetch 資料 → 設定 fhirServer URL → 服務自動透過範本取得 Patient → CQL 正常執行
- 既有 patient-view hooks 不受影響（回歸正常）

---

## PAT-020 — 分頁驗證錯誤明細（Tooltip + Alert）

- **日期**: 2026-02-24
- **範圍**: Authoring — UX 改善
- **分類**: UX 改善 / 可操作性

### 問題描述

ArtifactWorkspace 的 11 個分頁在元素有驗證錯誤時會顯示驚嘆號圖示，但使用者無法得知**哪些欄位**缺少或**在哪裡**修正。頂部的錯誤訊息（「CQL 產生失敗 / Request failed with status code 400」）過於籠統，缺乏可操作的指引。

### 修改內容

#### Step 1：增強 `computeTabStatuses` 回傳型別

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/authoring/ArtifactWorkspace.tsx` |

- 新增 `TabStatusInfo` 介面：`{ status: TabStatus, errors: string[] }`
- `computeTabStatuses` 回傳 `TabStatusInfo[]`（原為 `TabStatus[]`）
- 錯誤字串格式：`"i18nKey||{jsonParams}"`，在渲染時延遲翻譯

#### Step 2：收集具體錯誤訊息

| 動作 | 函式 |
|------|------|
| 新增 | `getModifierMissingFields(mod)` — 從 ModifierCard 提取修飾器必填欄位驗證邏輯 |
| 新增 | `collectTreeErrors(tree)` — 走訪元素樹收集具體錯誤 |

**樹驗證（Inclusions / Exclusions）：**
- 元素缺少名稱 → `"Element #N: missing element name"`
- 修飾器類型不匹配 → `"「X」: modifier「Y」expects Z, got W"`
- 修飾器缺少必填欄位 → `"「X」: modifier「Y」missing: value, unit"`（LookBack / ValueComparison / WithUnit / String / Qualifier / BeforeAfterInterval）

**其他分頁：**
- 建議缺少文字 → `"Recommendation #N: missing text"`
- 參數缺少名稱 / 類型 → `"Parameter #N: missing name"` / `"「X」: missing type"`
- 子族群缺少名稱 → `"Subpopulation #N: missing name"`
- 基礎元素缺少名稱 → `"Base Element #N: missing name"`

#### Step 3：分頁圖示 Tooltip

| 動作 | 檔案 |
|------|------|
| 修改 | `ArtifactWorkspace.tsx` — Tabs 區域 |

- `ErrorIcon` 包裝於 `<Tooltip>`，hover 顯示錯誤條目的項目符號清單
- 最多顯示 8 條錯誤，超出部分顯示 `…+N`

#### Step 4：分頁內容頂部 Alert 橫幅

| 動作 | 檔案 |
|------|------|
| 修改 | `ArtifactWorkspace.tsx` — Tab content Box |

- 當作用中分頁有錯誤時，在分頁內容區頂部渲染 `<Alert severity="warning">`
- 列出所有驗證問題（無上限），使用者切換到問題分頁即可看到

#### Step 5：i18n 鍵

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/locales/en/authoring.json` — 新增 `workspace.validation` 區段（9 鍵） |
| 修改 | `frontend/src/locales/zh-TW/authoring.json` — 對應繁體中文翻譯（9 鍵） |

新增鍵：`elementMissingName`、`elementModifierTypeMismatch`、`elementModifierMissingFields`、`recommendationMissingText`、`parameterMissingName`、`parameterMissingType`、`subpopulationMissingName`、`baseElementMissingName`、`errorsFound`

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 3 |
| 新增介面 | 2（`TabStatusInfo`、`ModifierLike`） |
| 新增函式 | 2（`getModifierMissingFields`、`collectTreeErrors`） |
| 新增 i18n 鍵 | 18（9 en + 9 zh-TW） |
| 新增 MUI 元件引用 | 2（`Tooltip`、`Alert`） |

### 驗證

- `tsc --noEmit` — 通過
- `vite build` — 通過（AuthoringPage chunk 153→154 KB）
- Hover 分頁錯誤圖示 → 顯示具體錯誤 Tooltip
- 切換到錯誤分頁 → 顯示 Alert 橫幅列出所有問題
- 修正缺少欄位 → 錯誤自動消失

---

## PAT-019 — 錯誤處理統一（Backend 例外層級 + Frontend 錯誤提取）

- **日期**: 2026-02-22
- **範圍**: 跨模組 — 錯誤處理改善
- **分類**: 健壯性 / UX 改善

### 問題描述

平台的錯誤處理碎片化：

1. **Backend**：4 種不同的錯誤模式（`RuntimeException`、`ResponseStatusException`、`ResponseEntity.badRequest().build()`、手工 JSON 字串 `"{\"error\":\"...\"}"`），導致 API 回應格式不一致
2. **Frontend**：30+ 個 mutation 缺乏 `onError` 處理，失敗時無使用者回饋（靜默失敗）
3. **Resilience4j 降級**：部分 Circuit Breaker fallback 回傳空結果而非拋出錯誤，FHIR 伺服器離線時前端顯示空白而無錯誤提示
4. **Frontend 錯誤擷取**：所有元件使用 `(err as Error).message`，無法正確解析 GlobalExceptionHandler 的結構化回應

### 修改內容

#### Phase 1：Backend 例外層級

| 動作 | 檔案 |
|------|------|
| 新增 | `exception/ResourceNotFoundException.java` — 404 Not Found |
| 新增 | `exception/DuplicateResourceException.java` — 409 Conflict |
| 新增 | `exception/ValidationException.java` — 400 Bad Request（含 details） |
| 修改 | `exception/GlobalExceptionHandler.java` — 新增 4 個 `@ExceptionHandler` |
| 修改 | `controller/AdminController.java` — 5 處例外替換 |
| 修改 | `controller/AuthController.java` — 2 處例外替換 |
| 修改 | `controller/MeasureController.java` — 3 處例外替換 |

- `RuntimeException("User not found")` → `ResourceNotFoundException("User", id)`
- `badRequest().build()` → `DuplicateResourceException` / `ValidationException`
- `ResponseStatusException(NOT_FOUND, ...)` → `ResourceNotFoundException`
- 新增 `DataIntegrityViolationException` → 409 handler

#### Phase 2：Backend Controller 錯誤標準化

| 動作 | 檔案 |
|------|------|
| 修改 | `controller/FhirController.java` — ~15 處 inline 回應替換為 `throw` |
| 修改 | `service/fhir/FhirDataProviderService.java` — 2 處 fallback 修復 |
| 修改 | `service/fhir/FhirTerminologyService.java` — 3 處 fallback 修復 |

- 所有 `badRequest().body("{\"error\":\"...\"}")` → `throw new IllegalArgumentException("...")`
- 所有 `badRequest().body(Map.of("error", ...))` → `throw new IllegalArgumentException("...")`
- 靜默降級 `return new Bundle()` / `return false` / `return new ArrayList<>()` → `throw new FhirServerUnavailableException(...)`

#### Phase 3：Frontend 錯誤工具

| 動作 | 檔案 |
|------|------|
| 新增 | `utils/errorUtils.ts` — `extractApiError()` 函式 |
| 修改 | `hooks/useInvalidatingMutation.ts` — 新增 `onError` 選項 |
| 修改 | `main.tsx` — 全域 mutation `onError` 安全網 |

`extractApiError` 依序嘗試：
1. `AxiosError.response.data.message`（GlobalExceptionHandler 格式）
2. `AxiosError.response.data.error`（舊版 Map 格式）
3. `AxiosError.message`（網路錯誤）
4. `Error.message`
5. Fallback: `"An unknown error occurred"`

#### Phase 4：Frontend Mutation 錯誤處理 + i18n

| 動作 | 檔案 |
|------|------|
| 修改 | `locales/en/common.json` — 新增 `mutationErrors` 區段（7 鍵） |
| 修改 | `locales/zh-TW/common.json` — 對應中文翻譯 |
| 修改 | `MeasureLibrary.tsx` — 5 個 mutation 加 `onError` |
| 修改 | `TestCasesTab.tsx` — 3 個 mutation 加 `onError` |
| 修改 | 13 個元件 — `(err as Error).message` → `extractApiError(err)` |

受影響元件：EditorPage, MeasureEditor, MeasureLibrary, MeasurePanel, DataRequirementsTab, TestCasesTab, TestCaseImportDialog, ManageServicesPanel, SandboxPanel, InvokeServicePanel, ImportCqlDialog, ImplementationGuideBrowser, useCqlStructure

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 4（3 Backend exceptions + 1 Frontend util） |
| 修改檔案 | 23 |
| Backend 錯誤模式統一 | 4 種 → 1 種（GlobalExceptionHandler ErrorResponse） |
| Frontend `(err as Error).message` 消除 | 25 處 → 0 處 |
| 靜默 Resilience4j fallback 修復 | 5 處 |
| 新增 i18n 鍵 | 14（7 en + 7 zh-TW） |

### 驗證

- `mvn compile -q` — 通過
- `tsc --noEmit` — 通過
- `grep "(err as Error).message" frontend/src/` — 0 結果
- `grep "badRequest().body(\"{" backend/src/main/java/com/cqlplatform/controller/` — 0 結果

---

## PAT-018 — API 參考文件 + OpenAPI 規格檔

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

## PAT-017 — 補完科別分類功能（篩選 + 指派）

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

## PAT-016 — Okta SSO (OIDC) 整合

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

## PAT-015 — P2-8: EHR/HIS 整合連接器

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

## PAT-014 — P2-9: 指標儀表板增強（Recharts）

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

## PAT-013 — P2-10: 科別多租戶隔離

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

## PAT-012 — P2-11: 衛福部指標代碼對照

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

## PAT-011 — P1-5: 持久化通知系統 + 工作流程推播

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

## PAT-010 — P1-6: FHIR Bundle 檔案上傳匯入

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

## PAT-009 — P1-4: 審核者欄位 + 退回原因 UI

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

## PAT-008 — P1-7: 人類可讀文件匯出

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

## PAT-007 — 測試案例批次匯入 + 日期平移

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

## PAT-006 — Population Criteria 佈局優化 + Reporting 分頁

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

## PAT-005 — MeasureController 授權與 IDOR 修復

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

## PAT-004 — 術語查詢 Drawer + 測試案例草稿自動儲存

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

## PAT-003 — 全平台國際化完成（Phase 5-9）

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

## PAT-002 — Measures 模組國際化（en / zh-TW）

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

## PAT-001 — UCUM 單位下拉選單統一

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

## 詳細記錄 — 🐛 Bugfix

## BUG-082 — 元素模板繼承未解析 + React Hooks 順序違規 — 缺少元素名稱 + CDS Hooks 頁面崩潰

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | CDS Authoring（前後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 / 框架違規 |
| **影響範圍** | 所有使用 `extends` 繼承的元素模板（如 Encounter）缺少 `element_name` 欄位；CDS Hooks 頁面因 React Hooks 規則違反而崩潰 |
| **Commit** | [`8e8d4c8`](../../commit/8e8d4c8) |

### 問題描述

1. **模板繼承未解析**：`TemplateService` 讀取 `formTemplates.json` 時解析了 `"extends": "Base"` 欄位，但從未實際將 Base 模板的欄位（`element_name`、`comment`）合併到子模板中。導致 Encounter 等元素僅有自身欄位（如 `encounter`），使用者看到「缺少元素名稱」驗證錯誤但找不到輸入框。

2. **React Hooks 順序違規**：`ResourceForm.tsx` 中 `useMemo` 放在條件式 early return 之後，違反 React Hooks 必須在每次渲染以相同順序呼叫的規則。當 `activeEntry` 或 `isLoading` 狀態改變時觸發 React error #310「Rendered more hooks than during the previous render」，導致 CDS Hooks 頁面 ErrorBoundary 崩潰。

### 根因分析

1. `TemplateService.parseTemplate()` 正確讀取 `extends` 欄位至 `FormTemplate.extendsTemplate`，但 `init()` 方法中從未呼叫任何繼承解析邏輯。

2. `ResourceForm.tsx` 中 `useMemo`（計算 `visibleOptional`/`hiddenOptional`）位於 `if (!activeEntry) return ...` 和 `if (isLoading) return ...` 之後。當元件從有 `activeEntry` 狀態切換至無 `activeEntry` 狀態時，Hooks 數量改變。

### 修正方式

1. **TemplateService**：新增 `resolveInheritance()` 方法，在載入分類後、建立 `knownElementTypes` 前呼叫。建立 `id → FormTemplate` lookup map，遍歷所有模板，將 parent 欄位前置合併到 child 中（跳過已存在的欄位避免重複）。

2. **ResourceForm**：將 `useMemo` 移到所有 early return 之前，內部加入 `if (!activeEntry)` null guard 回傳空陣列。

### 修改檔案

| 檔案 | 變更 |
|------|------|
| `backend/.../authoring/TemplateService.java` | 新增 `resolveInheritance()` 方法，`init()` 中呼叫 |
| `frontend/.../testcase-builder/ResourceForm.tsx` | `useMemo` 移至 early return 前，加 null guard |

---

## BUG-081 — CQL 批次執行崩潰 + FHIR Token 搜尋管道符號轉義 — FHIRHelpers 歧義 + 查詢回傳 0 筆

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | CQL 執行引擎（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 / API 誤用 |
| **影響範圍** | 所有 CQL 執行（CDS Hooks 調用、手動執行）；當 FHIRHelpers 遇到 null 值時整個執行失敗；FHIR fallback 搜尋永遠回傳 0 筆結果 |
| **Commit** | [`649ac67`](../../commit/649ac67) |

### 問題描述

1. **CQL 批次執行崩潰**：CQL 引擎的 `FunctionRefEvaluator` 在 `null` 值傳入 `FHIRHelpers.ToString()` 時無法判斷應呼叫哪個多載版本（`ToString(FHIR.string)`、`ToString(FHIR.code)` 等），拋出 `CqlException: Ambiguous call to operator 'ToString(null)'`。在正常模式下，`engine.evaluate()` 一次評估所有表達式，任一表達式失敗會導致整個執行崩潰。

2. **FHIR Token 搜尋管道符號轉義**：`FhirDataProviderService` 的 fallback 搜尋使用 `TokenClientParam.exactly().code("http://loinc.org|29463-7")`，HAPI FHIR client 將 `|` 視為 token 搜尋的保留字元並轉義為 `\|`，實際發出的查詢為 `code=http://loinc.org\|29463-7`，FHIR server 找不到 system `http://loinc.org\` 因此回傳 0 筆結果。

### 根因分析

1. `CqlExecutionService.doExecute()` 的 debug 模式逐一評估表達式並以 try-catch 捕捉錯誤，但 normal 模式批次呼叫 `engine.evaluate()` 後用外層 catch 直接拋出 `CqlExecutionException`，沒有 per-expression 容錯。

2. `buildCodeFilter()` 組合出 `system|code` 字串，`trySearch()` 將整串傳入 `.exactly().code()`。HAPI FHIR 的 `.code()` 方法設計為僅接受 code 值（非 system|code），因此自動轉義 `|`。正確做法是使用 `.systemAndCode(system, code)` 分開傳入。

### 修正方式

1. **CqlExecutionService**：normal 模式下，先嘗試批次 `engine.evaluate()`；若失敗，改以 per-expression 逐一評估（與 debug 模式相同策略），僅將失敗的表達式標記為 Error，其餘正常回傳。

2. **FhirDataProviderService**：重構 `buildCodeFilter()` → `collectCodes()` 回傳 Code 物件列表；`trySearch()` 使用 `.systemAndCode(system, code)` 正確建構 FHIR token 搜尋參數。多碼時用 `whereMap()` 避免 HAPI 轉義。

### 修改檔案

| 檔案 | 變更 |
|------|------|
| `backend/.../cql/CqlExecutionService.java` | 批次失敗 → per-expression 回退邏輯 + circuit breaker 處理 |
| `backend/.../fhir/FhirDataProviderService.java` | `collectCodes()` + `systemAndCode()` 修復 + circuit breaker 包裝 |
| `backend/.../cql/CircuitBreakerRetrieveProvider.java` | 新增 FHIR retrieve circuit breaker 包裝器 |
| `backend/.../fhir/FhirDataProviderServiceTest.java` | 更新測試適配新建構參數 |

---

## BUG-080 — 多分頁同時編輯 Artifact 導致靜默資料覆蓋 — JPA @Version 樂觀鎖 + 前端衝突對話框

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | CDS Authoring（前後端） |
| **嚴重程度** | High |
| **根因類型** | 併發/效能問題 |
| **影響範圍** | `CdsArtifactEntity.java`、`ArtifactRequest.java`、`ArtifactResponse.java`、`ArtifactService.java`、`GlobalExceptionHandler.java`、`ArtifactWorkspace.tsx`、`authoring.ts`、`en/authoring.json`、`zh-TW/authoring.json`、`V38__cds_artifact_lock_version.sql` |
| **Commit** | [`9b46017`](../../commit/9b46017) |

### BUG 描述

同一帳號在多個瀏覽器分頁同時編輯同一個 CDS Artifact 時，`ArtifactService.update()` 執行盲目覆寫（last-write-wins），無任何衝突偵測機制。當 Tab A 與 Tab B 各自修改後依序儲存，Tab B 的儲存會靜默覆蓋 Tab A 的變更，使用者完全無感知資料遺失。

**重現步驟**：
1. 在分頁 A 開啟 Artifact，修改納入條件
2. 在分頁 B 開啟相同 Artifact，修改建議文字
3. 分頁 A 儲存成功
4. 分頁 B 儲存成功 — 分頁 A 的修改被靜默覆蓋

### 修正方式

**JPA @Version 樂觀鎖 + 前端衝突對話框**：

1. **DB Migration（V38）**：新增 `lock_version BIGINT NOT NULL DEFAULT 0` 欄位
2. **Entity（CdsArtifactEntity）**：加入 `@Version @Column("lock_version") Long lockVersion`，Hibernate 自動在 UPDATE 語句加入 `WHERE lock_version = ?` 條件
3. **DTO 傳遞**：`ArtifactRequest` 與 `ArtifactResponse` 新增 `lockVersion` 欄位，前後端完整 round-trip
4. **Service（ArtifactService.update）**：將 client 傳入的 `lockVersion` 設定至 entity，若版本過舊則 UPDATE 命中 0 行 → `ObjectOptimisticLockingFailureException`
5. **Exception Handler**：新增 `ObjectOptimisticLockingFailureException` → HTTP 409 Conflict 回應
6. **前端（ArtifactWorkspace）**：`handleSave` 攔截 409 → 顯示衝突對話框，提供「重新載入」（refetch 最新版本）與「繼續編輯」（關閉對話框保留本地修改）兩個選項
7. **i18n**：新增 `workspace.conflict.*` 翻譯鍵（英文 + 繁體中文）

### 測試驗證

- 單分頁正常儲存：`lockVersion` 透明 round-trip，行為不變
- 多分頁衝突：Tab A 儲存後 Tab B 儲存 → Tab B 收到 409 → 顯示衝突對話框
- 「重新載入」按鈕：重新取得最新資料，`lockVersion` 更新
- 「繼續編輯」按鈕：關閉對話框，使用者可手動合併後重試儲存
- TypeScript 編譯通過（`npx tsc --noEmit`）

---

## BUG-079 — Rate Limiting 分層強化 — 端點分級 IP 限流 + 使用者限流 + 大型 Payload 加權

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（DoS / 資源耗盡） |
| **影響範圍** | `RateLimitFilter.java`、`UserRateLimitFilter.java`、`RateLimitProperties.java`、`SecurityConfig.java`、`application.yml` |
| **Commit** | [`6b72fec`](../../commit/6b72fec) |

### BUG 描述

`POST /api/cql/translate` 與 `/api/cql/execute` 為 CPU 密集型端點，但僅受全域 60 RPM/IP 速率限制保護，所有端點共用同一配額。攻擊者可利用以下方式進行 DoS：

1. **配額佔用**：連續發送 60 個 translate 請求即耗盡全部配額，合法的輕量 API 呼叫（如 library 列表）同樣被阻擋
2. **多 IP 繞過**：IP 限流無法防禦分散式攻擊，認證後的使用者無獨立限額
3. **大型 Payload 放大**：200KB+ 的 CQL 翻譯請求與 1KB 請求消耗相同配額，但 CPU 成本差距數十倍
4. **Token refill 時間漂移**：`refill()` 方法在 `tokensToAdd == 0` 時仍更新 `lastRefillTime`，導致 token 補充速率逐漸偏移

### 修正方式

**雙層分級限流架構**：

1. **Layer 1 — IP 分級限流（RateLimitFilter 重寫）**
   - 5 級端點分類：TRANSLATE(20 RPM)、EXECUTE(10 RPM)、FIX_SUGGESTION(5 RPM)、LIBRARY_READ(120 RPM)、DEFAULT(60 RPM)
   - Bucket key 改為 `IP:tier`，各級獨立計數互不干擾
   - Payload 加權：TRANSLATE 端點依 Content-Length 消耗 1-5 tokens（>10KB=2, >50KB=3, >200KB=5）
   - 修復 token refill 時間漂移：僅按實際補充量推進 `lastRefillTime`
   - 429 回應加入 `Retry-After` header
   - Micrometer `rate_limit_exceeded` 計數器（tag: tier, layer=ip）

2. **Layer 2 — 使用者限流（UserRateLimitFilter 新增）**
   - 位於 JwtAuthenticationFilter 之後，使用 `authentication.getName()` 為 bucket key
   - 獨立 RPM 配額：TRANSLATE(15)、EXECUTE(8)、FIX_SUGGESTION(3)、DEFAULT(40)
   - 未認證/匿名請求自動放行（已由 IP 層保護）
   - `X-UserRateLimit-Limit` / `X-UserRateLimit-Remaining` / `Retry-After` headers
   - Micrometer 計數器（tag: layer=user）

3. **外部化配置（RateLimitProperties）**
   - 所有 RPM 值透過 `@ConfigurationProperties(prefix = "rate-limit")` 管理
   - 支援環境變數覆蓋（如 `RATE_LIMIT_TRANSLATE_RPM`）

**Filter chain 順序**：RateLimitFilter → XssFilter → JwtAuthenticationFilter → UserRateLimitFilter → AuditFilter

### 測試驗證

- `RateLimitFilterTest`（13 tests）：分級限額驗證（translate/execute/default/library-read）、Payload 加權消耗、Bucket 隔離、Retry-After header、停用/OPTIONS 跳過、metrics 計數
- `UserRateLimitFilterTest`（7 tests）：未認證跳過、匿名使用者跳過、per-user translate 限額、使用者隔離、header 驗證、停用跳過、metrics 計數
- 全部 20 個測試通過

---

## BUG-078 — CDS Card XSS 3 層防護 — 前端安全渲染 + 後端 HTML 跳脫 + 反序列化器強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | 安全性（前後端） |
| **嚴重程度** | Critical |
| **根因類型** | 安全漏洞（XSS） |
| **影響範圍** | `SandboxPanel.tsx`、`CdsValueFormatter.java`、`CdsResourceFormatter.java`、`CqlTupleCardStrategy.java`、`PlanDefinitionCardStrategy.java`、`XssStringDeserializer.java`、`NoXssValidator.java` |
| **Commit** | [`d7fc37f`](../../commit/d7fc37f) |

### BUG 描述

CDS card 內容（summary、detail、source label）將未經消毒的 FHIR/CQL 資料直接傳遞至前端，`SandboxPanel.tsx` 透過 `dangerouslySetInnerHTML` 渲染 `card.detail`，造成儲存型 XSS 漏洞。此外，`XssStringDeserializer` 使用的 regex 黑名單可被 `<svg>`、`<math>`、未閉合 `<script`、`data:text/html` 等向量繞過。

**攻擊路徑**：
1. 攻擊者在 FHIR Resource 欄位（如 Observation.code.text、Condition 狀態碼）中注入 `<svg onload=alert(document.cookie)>`
2. CQL 執行引擎讀取該資源，CDS card 格式化器將惡意內容原封不動寫入 card detail
3. 前端 `dangerouslySetInnerHTML` 直接渲染為 HTML，觸發 XSS
4. `XssStringDeserializer` 的 `<script>(.*?)</script>` 模式無法攔截 `<svg>`、`<math>`、未閉合 `<script src=evil>`

### 修正方式

**3 層防禦（Defense-in-depth）**：

1. **Layer 1 — 前端安全渲染（Critical）**
   - 移除 `dangerouslySetInnerHTML`，改用 `text.split(/\*\*(.+?)\*\*/g)` 將 markdown bold 拆分為交替的純文字與 `<strong>` React 元素
   - React 自動跳脫所有文字內容，從根本杜絕 XSS

2. **Layer 2 — 後端 HTML 跳脫（Defense-in-depth）**
   - `CdsValueFormatter`：新增 `esc()` 工具方法（`HtmlUtils.htmlEscape`），對 String、CodeableConcept、Coding、Quantity unit、PrimitiveType 等所有 FHIR/CQL 衍生值進行跳脫
   - `CdsResourceFormatter`：對 `formatDetail()`、`formatReference()`、`formatAllCodings()` 中所有 FHIR 欄位值（display、text、code、unit、id）進行跳脫
   - `CqlTupleCardStrategy`：對 Tuple 衍生的 `summary`、`detail`、`sourceLabel` 及 `errorMessage` 進行跳脫
   - `PlanDefinitionCardStrategy`：對 `action.getTitle()` 及 `action.getDescription()` 進行跳脫

3. **Layer 3 — 反序列化器模式強化**
   - `XssStringDeserializer` + `NoXssValidator`：新增 `<svg>`、`<math>`、`<object>`、`<embed>`、`<base>`、`<form>` 標籤攔截
   - 將 `<script>(.*?)</script>` 改為 `<script[^>]*>` + `</script>` 以攔截未閉合/帶屬性的 script 標籤
   - 新增 `data:text/html` 及 `vbscript:` URI 向量攔截

### 測試驗證

- `CdsValueFormatterTest`：新增 8 個 XSS 跳脫驗證測試（String、SVG、CodeableConcept、Coding、Quantity unit、list items）
- `CdsResourceFormatterTest`：新增 6 個 XSS 跳脫驗證測試（code text、coding display、quantity unit、resource ID、reference ID、condition status）
- `XssStringDeserializerTest`：新建 17 個測試覆蓋所有新增模式（svg、math、object、embed、base、form、data:text/html、vbscript、未閉合 script 等）
- 前端：`SandboxPanel` 以 React 元素安全渲染 bold 文字，無 `dangerouslySetInnerHTML`

---

## BUG-077 — 停用使用者 API Key 未失效 — 認證繞過漏洞 + 雙重防護修復

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 安全漏洞（認證繞過） |
| **影響範圍** | `AdminController.java`、`UserApiKeyService.java`、`UserApiKeyRepository.java`、`JwtAuthenticationFilter.java` |

### BUG 描述

當 Admin 透過 `PUT /api/admin/users/{id}/enabled` 停用使用者時，該使用者先前建立的所有 API Key 仍然有效，可繼續存取 `/cds-services/u/` 等受保護端點。

**攻擊路徑**：
1. 使用者 A 產生 API Key（`POST /api/user/api-keys`）
2. Admin 停用使用者 A（`PUT /api/admin/users/{id}/enabled { "enabled": false }`）
3. 使用者 A 仍可使用 API Key 透過 `Authorization: Bearer cql_...` 存取 CDS 服務
4. `JwtAuthenticationFilter` 將 API Key 驗證委派給 `UserApiKeyService.validateApiKey()`，該方法只檢查 `key.active = true`，**完全不檢查 `user.enabled`**

### 根因分析

- `AdminController.updateUserEnabled()` 只設定 `user.enabled = false`，不觸碰 `user_api_keys` 表
- `UserApiKeyService.validateApiKey()` 透過 `findByApiKeyAndActiveTrue()` 只檢查 key 本身的 `active` 欄位，無 JOIN 查詢使用者狀態
- `UserApiKeyEntity` 以 `username` 字串關聯，無 JPA `@ManyToOne` 外鍵約束

### 修正方式

**雙重防護（A + B）**：

1. **Fix A — 驗證時檢查使用者狀態（防禦縱深）**
   - `UserApiKeyService.validateApiKey()` 在確認 key 有效後，額外查詢 `UserRepository.findByUsername()` 檢查 `user.enabled`
   - 若使用者已停用或已刪除，回傳 `Optional.empty()` 拒絕認證

2. **Fix B — 停用時立即失效所有 Keys**
   - `UserApiKeyRepository` 新增 `deactivateAllByUsername()` JPQL 批次更新
   - `UserApiKeyService` 新增 `deactivateAllKeys(username)` 方法
   - `AdminController.updateUserEnabled(false)` 時呼叫 `deactivateAllKeys()` 立即停用所有 API Keys

### 測試驗證

- `UserApiKeyServiceTest`: 新增 4 個測試（enabled user 通過、disabled user 拒絕、deleted user 拒絕、批次停用）+ 更新 1 個既有測試
- `AdminControllerTest`: 新增 2 個測試（disable 觸發 key 停用驗證、enable 不觸發驗證）
- 全部 28 個相關測試通過（12 + 11 + 5）

---

## BUG-076 — AuditFilter $export 未標記 PHI 存取 + 欄位溢位導致稽核寫入失敗

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（稽核遺漏） |
| **影響範圍** | `AuditFilter.java` |

### BUG 描述

兩個獨立的稽核缺陷：

1. **`$export` Bulk Data 未標記為 PHI 存取**：`FHIR_RESOURCE_PATTERN` regex `\w+` 不匹配 `$` 字元，導致 `/api/fhir/$export` 的 `resourceType` 錯誤解析為 `fhir`，`phiAccess = false`，匯出範圍（`_type`、`_since`）未記錄。Bulk Data Export 是最高風險 PHI 操作，卻完全不在 PHI 稽核報表中。

2. **`path`/`resourceId`/`ipAddress` 未截斷**：當 URI > 500 字元、resource ID > 100 字元、或偽造 `X-Forwarded-For` > 45 字元時，JPA 寫入觸發 `DataTruncation` 異常，被 catch 吞掉後整筆稽核記錄遺失。

### 根因分析

- `\w+` 只匹配 `[a-zA-Z0-9_]`，`$export` 的 `$` 不在範圍內
- 無針對 `$export` 路徑的特殊處理
- `path`、`resourceId`、`ipAddress` 直接傳入 entity builder，未呼叫 `truncate()`

### 修正方式

1. **新增 `$export` 偵測**：`path.contains("/fhir/$export")` → `phiAccess=true`、`resourceType="BulkExport"`、`action="EXPORT"`、記錄完整 `queryParameters`
2. **欄位截斷對齊 DB schema**：`path` → `truncate(500)`、`resourceId` → `truncate(100)`、`ipAddress` → `truncate(45)`

### 測試驗證

- 既有 16 個稽核相關測試全部通過
- `$export` 的稽核記錄現在包含：`phiAccess=true`、`resourceType=BulkExport`、`action=EXPORT`、`queryParameters=fhirServer=...&exportType=system&_type=Patient,Observation`

---

## BUG-075 — CqlArtifactBuilder 測試補強 — LookBack / AgeRange / 空排除 / 括號驗證 + Windows 換行修復

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Low |
| **根因類型** | 測試遺漏 |
| **影響範圍** | `CqlArtifactBuilderTest.java` |

### BUG 描述

`CqlArtifactBuilderTest` 僅有 4 個測試，缺少對以下場景的覆蓋：

1. **LookBack 修飾符**：未驗證 `C3F.ObservationLookBack(expr, N unit)` 輸出格式
2. **LookBack 空值降級**：未驗證 value 為空時的 fallback 行為
3. **AgeRange 單邊界**：未驗證只設下限時不會產生 null reference
4. **AgeRange 雙邊界括號**：未驗證 #074 修正的括號行為
5. **空排除樹 Windows 相容性**：`buildCql_emptyExclusion_shouldProduceFalseNotNull` 使用 `\n` 比對，但 `String.format("%n")` 在 Windows 產生 `\r\n`，導致 CI/本地測試失敗

### 修正方式

1. 新增 4 個測試：
   - `buildCql_lookBackModifier_shouldGenerateC3FLookBackWith6Months`
   - `buildCql_lookBackModifier_emptyValue_shouldOmitQuantity`
   - `buildCql_ageRangeOnlyMin_shouldNotProduceNullReference`
   - `buildCql_ageRangeBothBounds_shouldWrapInParentheses`
2. 修復 `buildCql_emptyExclusion_shouldProduceFalseNotNull`：`contains("\n")` → `containsPattern("\\R")` 以相容所有平台換行符

### 測試驗證

- 全部 8 個測試通過（Windows + Maven Surefire）

---

## BUG-074 — CQL 產生器 AgeRange / ValueComparison 複合條件缺少括號

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Low |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `CqlArtifactBuilder.java` |

### BUG 描述

`buildAgeRangeExpression()` 和 `ValueComparisonNumber` / `ValueComparisonObservation` modifier 在同時具有上下限條件時，使用 `String.join(" and ", conditions)` 串接，未加括號。當此複合表達式作為 OR 群組的子節點時，生成的 CQL 可讀性差：

```cql
// 修正前
AgeInYears() >= 18 and AgeInYears() <= 65 or SomeCondition

// 修正後
(AgeInYears() >= 18 and AgeInYears() <= 65) or SomeCondition
```

CQL 運算子優先級 `and` > `or`，因此語義上兩者等價，**不影響正確性**，但缺少括號會降低可讀性且容易在人工審查時產生疑慮。

### 根因分析

- `buildAgeRangeExpression()` 直接 `String.join(" and ", conditions)` 回傳
- `ValueComparisonNumber` / `ValueComparisonObservation` 同理
- `buildConjunctionExpression()` 只對 `conjunction=true` 的子節點加括號，葉節點不處理

### 修正方式

1. **`buildAgeRangeExpression()`**：當 `conditions.size() > 1` 時以 `()` 包裹
2. **`ValueComparisonNumber` / `ValueComparisonObservation`**：同上處理

### 測試驗證

- 既有 `CqlArtifactBuilderTest` 通過，確認正常路徑不受影響
- 單一條件不加括號，雙條件加括號

---

## BUG-073 — verifyArtifactOwnership 使用 IllegalArgumentException(400) 而非 ResourceNotFoundException(404)

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Low |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `AuthoringController.java`、`CqlGenerationService.java` |

### BUG 描述

`verifyArtifactOwnership()`、`deployCdsService()`、`saveAsLibrary()` 以及 `CqlGenerationService.generateCql()` 中，artifact 不存在時拋出 `IllegalArgumentException("Artifact not found: " + id)`。`GlobalExceptionHandler` 將 `IllegalArgumentException` 映射為 400 Bad Request，但語義應為 404 Not Found。

### 根因分析

- 開發時直接使用 `IllegalArgumentException` 作為通用拋出異常
- 已有 `ResourceNotFoundException` 但未被使用

### 修正方式

- 4 處 `IllegalArgumentException("Artifact not found")` → `ResourceNotFoundException("Artifact", id)`
  - `AuthoringController.verifyArtifactOwnership()`
  - `AuthoringController.deployCdsService()`
  - `AuthoringController.saveAsLibrary()`
  - `CqlGenerationService.doBuildCql()`

### 測試驗證

- `CqlGenerationServiceTest.generateCql_notFound_shouldThrow` 更新為期望 `ResourceNotFoundException`

---

## BUG-072 — CdsArtifactEntity 反序列化失敗被靜默吞掉，無 log

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `CdsArtifactEntity.java` |

### BUG 描述

`CdsArtifactEntity` 的 4 個序列化/反序列化方法（`serializeList`、`deserializeList`、`serializeMap`、`deserializeMap`）在 `JsonProcessingException` 時直接回傳預設值（空 list 或 null），無任何日誌輸出。當資料庫中存在損壞的 JSON 時，entity 靜默載入空資料，下游行為異常但無法從 log 追溯根因。

### 根因分析

- catch block 中直接 `return` 預設值，未呼叫任何 logging
- Entity 類別原本缺少 `@Slf4j` 註解

### 修正方式

1. 加 `@Slf4j` 註解
2. 4 個 catch block 加 `log.warn("Failed to (de)serialize ... for entity id={}: {}", id, e.getMessage())`

### 測試驗證

- 既有測試通過，確認正常路徑不受影響

---

## BUG-071 — CQL 產生器靜默降級 — 未知 element type 或 modifier 被忽略，使用者無感知

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `CqlArtifactBuilder.java`、`CqlBuildResult.java`（新增）、`CqlGenerationService.java`、`AuthoringController.java`、`AuthoringControllerTest.java` |

### BUG 描述

CQL 產生器在遇到不認識的 element type 或 modifier template 時：
1. 未知 element type → 靜默替換為 `true /* elementName */`
2. 未知 modifier template → 靜默忽略（原樣回傳表達式）

使用者完全不知道產出的 CQL 包含 placeholder，可能部署語義不正確的 CDS 規則。

### 根因分析

- `buildExpression()` 的 default case 直接回傳 `true` placeholder
- `applyModifier()` 的 default 路徑僅有 `log.warn` 但未通知呼叫者
- 回傳型別為 `String`，無法攜帶結構性警告

### 修正方式

1. **`CqlBuildResult` record** 攜帶 `List<String> warnings`
2. **`BuildContext.warn()`** 在兩處 fallback 收集警告：
   - 未知 element type → `"Unknown element type '...' for element '...'; defaulting to 'true'"`
   - 未知 modifier template → `"Unknown modifier template '...' (id='...'); modifier skipped"`
3. **`CqlGenerationService.generateCqlWithWarnings()`** 回傳 `CqlBuildResult`
4. **`AuthoringController.generateCql()` 端點** 回傳 `{ cql, warnings? }`，僅有 warnings 時才包含

### 測試驗證

- `CqlArtifactBuilderTest.buildCql_unknownElementType_shouldProduceWarning`
- `generateCqlWithWarnings_shouldReturnWarnings`
- `generateCql_withWarnings_shouldIncludeWarningsInResponse`

---

## BUG-070 — CqlGenerationService.generateCql() 無 try-catch — 畸形 JSON 導致 generic 500

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `CqlGenerationService.java`、`CqlGenerationException.java`（新增）、`GlobalExceptionHandler.java`、`CqlGenerationServiceTest.java` |

### BUG 描述

`CqlGenerationService.generateCql()` 直接呼叫 `cqlBuilder.buildCql()` 無任何 try-catch。當使用者儲存的 artifact 含有畸形 expression tree JSON 時，builder 內部的 `ClassCastException` 或 `NullPointerException` 直接穿透至 Spring 的 generic exception handler，回傳 500 Internal Server Error，沒有任何有用的錯誤訊息。

### 根因分析

- `buildCql()` 內部大量 unchecked cast（`(List<Map<String, Object>>)` 等），畸形資料會觸發 `ClassCastException`
- 缺少欄位時觸發 `NullPointerException`
- Service 層未包裝這些非預期異常

### 修正方式

1. **新增 `CqlGenerationException`**：攜帶 `List<String> details`，遵循 `CqlTranslationException` 模式
2. **抽出 `doBuildCql()` 方法**：try-catch 包裝 `buildCql()` 呼叫，非 `CqlGenerationException` 一律包裝成 `CqlGenerationException`
3. **`GlobalExceptionHandler` 新增 handler**：`CqlGenerationException` → 422 UNPROCESSABLE_ENTITY

### 測試驗證

- `generateCql_builderThrowsClassCast_shouldWrapInCqlGenerationException`
- `generateCql_builderThrowsNPE_shouldWrapInCqlGenerationException`

---

## BUG-069 — CqlArtifactBuilder Singleton 可變 instance field — 並行請求互相覆蓋

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 併發/效能問題 |
| **影響範圍** | `CqlArtifactBuilder.java`、`CqlBuildResult.java`（新增）、`CqlArtifactBuilderTest.java`（新增） |

### BUG 描述

`CqlArtifactBuilder` 是 Spring `@Component`（Singleton），但持有可變 instance field `currentBaseElements`，在 `buildCql()` 入口處被賦值。當多個請求並行呼叫時，後到的請求會覆蓋前一個請求的 `currentBaseElements`，導致 `findBaseElementName()` 查找到錯誤的 base element，產出語義錯誤的 CQL。

### 根因分析

- `private List<Map<String, Object>> currentBaseElements` 是 instance field，Singleton bean 共享同一個實例
- `buildCql()` 開頭直接 `this.currentBaseElements = baseElements`，無同步保護
- `findBaseElementName()` 讀取 `this.currentBaseElements` 時可能已被另一執行緒覆蓋

### 修正方式

1. **移除 instance field** `currentBaseElements`
2. **新增 `static class BuildContext`**：包含 `baseElements` 和 `warnings` 收集器
3. 在 `buildCql()` 中建立本地 `BuildContext`，作為參數傳遞給 4 個 private 方法：`buildConjunctionExpression`、`buildExpression`、`applyModifier`、`findBaseElementName`
4. **回傳型別改為 `CqlBuildResult` record**（`String cql` + `List<String> warnings`）

### 測試驗證

- `CqlArtifactBuilderTest.currentBaseElements_instanceField_shouldNotExist` — 反射斷言確認 instance field 已移除
- `CqlArtifactBuilderTest.buildCql_emptyTree_shouldProduceValidLibraryHeader` — 空 tree 產出合法 CQL
- Docker build 零錯誤，既有測試全部通過

---

## BUG-068 — AuditFilter PHI 稽核修復 — FHIR 三層路徑解析、顯式 phiAccess 旗標、查詢參數擷取

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（稽核遺漏） |
| **影響範圍** | `AuditFilter.java`、`AuditLogEntity.java`、`AuditLogEntry.java`、`AuditLogRepository.java`、`AuditService.java`、`V36__audit_phi_access_columns.sql`、`frontend/types/index.ts` |

### BUG 描述

PHI（受保護健康資訊）存取稽核存在多項缺陷：

1. **FHIR 路徑解析錯誤**：`/api/fhir/Patient/123` 被正則 `/api/(\w+)(?:/([^/]+))?` 解析為 `resourceType=fhir, resourceId=Patient`，完全丟失真正的資源類型和 ID
2. **PHI 偵測使用脆弱的 LIKE 比對**：`LOWER(a.path) LIKE '%patient%'` 會誤判（如 `/api/patient-settings`）且遺漏其他 PHI 資源（Observation、Condition 等）
3. **搜尋參數未記錄**：無法事後審查「查了哪些條件」
4. **無顯式 PHI 旗標**：依賴 query-time 字串比對，效能差且不精確

### 根因分析

- 原始正則僅支援二層路徑 `/api/{module}/{id}`，FHIR 端點為三層 `/api/fhir/{resourceType}/{id}`
- PHI 偵測沒有明確的資源類型白名單，僅做模糊字串比對
- `AuditLogEntity` 缺少 `phiAccess` 和 `queryParameters` 欄位

### 修正方式

1. **新增 Flyway V36 migration**：
   - `phi_access BOOLEAN NOT NULL DEFAULT FALSE` + `query_parameters VARCHAR(2000)`
   - 部分索引 `idx_audit_phi_access ON audit_log(phi_access) WHERE phi_access = TRUE`

2. **重寫 `AuditFilter.java` 路徑解析**：
   - 新增 `FHIR_RESOURCE_PATTERN = /api/fhir/(\w+)(?:/([^/]+))?` 優先比對三層路徑
   - 定義 `PHI_RESOURCE_TYPES` 白名單（18 種 FHIR 臨床資源）
   - PHI 存取時擷取 `request.getQueryString()` 記錄查詢參數
   - 特殊處理 `Patient/$search-by-demographics`

3. **更新 Repository 查詢**：
   - `LIKE '%patient%'` → `a.phiAccess = true`（精確、高效、使用部分索引）

4. **更新 DTO + Service 映射**：
   - `AuditLogEntry` / `AuditService.toEntry()` / 前端 TypeScript 介面同步新增欄位

### 測試驗證

- Docker build 編譯通過
- 前端 TypeScript 型別檢查通過

---

## BUG-067 — CDS Feedback 儲存型 XSS 修復 — @NoXss 驗證 + HtmlUtils.htmlEscape 雙層防護

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（XSS） |
| **影響範圍** | `CdsFeedbackRequest.java`、`CdsHooksService.java`、`CdsFeedbackTest.java` |

### BUG 描述

CDS Hooks 回饋介面 (`POST /cds-services/{id}/feedback`) 的自由文字欄位未做 HTML Sanitization。攻擊者可在 `card`、`overrideReason.code`、`overrideReason.display` 中注入 `<img src=x onerror=alert(1)>` 等 XSS payload，儲存到資料庫後，當管理員在後台查看統計報表時觸發執行。

### 根因分析

- `XssStringDeserializer` 僅剝除 5 種 pattern（`script`、`javascript:`、`on\w+=`、`iframe`、`eval`），大量旁路向量可繞過：`<img>`、`<svg>`、`<details>`、`data:` URI
- `CdsFeedbackRequest` 的 `card`、`outcomeTimestamp`、`AcceptedSuggestion.id`、`OverrideReason.code/display` 均缺少 `@NoXss` 驗證
- 寫入資料庫前未做 HTML entity 編碼

### 修正方式

1. **第一層：輸入驗證**（拒絕惡意輸入）
   - `CdsFeedbackRequest.java`：所有自由文字欄位加上 `@NoXss`
   - 巢狀物件 `acceptedSuggestions`、`overrideReason` 加上 `@Valid` 確保驗證傳播

2. **第二層：輸出編碼**（縱深防禦）
   - `CdsHooksService.processFeedback()`：呼叫 `HtmlUtils.htmlEscape()` 對 `card`、`overrideReason.code`、`overrideReason.display` 做 HTML entity 編碼後才寫入 DB

### 測試驗證

- 新增測試 `processFeedback_withHtmlInDisplay_shouldEscapeBeforePersist`：
  - 輸入 `<img src=x onerror=alert(1)>` → 驗證輸出為 `&lt;img src=x onerror=alert(1)&gt;`
- 全部 6 個 CdsFeedbackTest 測試通過

---

## BUG-066 — CQL 執行逾時強化 — worker 中斷、AbortPolicy 防執行緒池耗盡、差異化 HTTP 狀態碼

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 安全漏洞（DoS / 資源耗盡） |
| **影響範圍** | `AsyncConfig.java`、`CqlExecutionService.java`、`MetricsConfig.java`、`GlobalExceptionHandler.java`、新增 `InterruptAwareRetrieveProvider.java` |

### BUG 描述

CQL 執行逾時機制 `future.get(120, SECONDS)` 僅讓**呼叫端**停止等待，worker 執行緒繼續無限消耗 CPU。攻擊者可提交含無窮迴圈的複雜 CQL，耗盡 20 執行緒的執行緒池；之後 `CallerRunsPolicy` 會使 CQL 直接在 Tomcat HTTP 執行緒上執行，**凍結整個 API**。

具體攻擊路徑：
1. 提交 20+ 個耗時 CQL 請求 → 填滿執行緒池 + 佇列
2. `CallerRunsPolicy` 使後續請求在 HTTP 執行緒上同步執行 → 所有 API 端點阻塞
3. 即使 `future.get()` 逾時返回，worker 執行緒仍繼續執行 → 永久佔用資源

### 根因分析

1. **`CompletableFuture.supplyAsync()` 不支援取消**：`cancel()` 不會中斷底層執行緒
2. **`CallerRunsPolicy`**：佇列滿時在呼叫者執行緒執行任務，本意防止拒絕，但在安全場景下成為 DoS 放大器
3. **CQL Engine 不檢查中斷旗標**：即使設定中斷，引擎內部不會自行停止
4. **HTTP 狀態碼無差異化**：逾時和池耗盡都回傳 `500`，無法區分

### 修正方式

1. **新增 `InterruptAwareRetrieveProvider.java`**：
   - 裝飾器模式包裝 `RetrieveProvider`
   - 每次 `retrieve()` 前檢查 `Thread.currentThread().isInterrupted()`
   - 中斷時拋出 `CqlExecutionException`，讓 worker 執行緒乾淨退出

2. **`AsyncConfig.java` — 執行器類型 + 拒絕策略**：
   - `ThreadPoolTaskExecutor` → 原生 `ThreadPoolExecutor`（回傳 `ExecutorService`）
   - `CallerRunsPolicy` → `AbortPolicy`（佇列滿時拋 `RejectedExecutionException` → 503）
   - 加入 `allowCoreThreadTimeOut(true)` 回收閒置執行緒

3. **`CqlExecutionService.java` — 4 項變更**：
   - `Executor` → `ExecutorService`
   - `CompletableFuture.supplyAsync()` → `executorService.submit()`（真正可取消的 `Future`）
   - `TimeoutException` catch 加入 `future.cancel(true)` 設定中斷旗標
   - 外層包裝 `InterruptAwareRetrieveProvider` 作為最外層 retrieve 裝飾器
   - 捕獲 `RejectedExecutionException` → 拋出含 "pool exhausted" 訊息的例外

4. **`MetricsConfig.java` — 3 個 Gauge bean 適配**：
   - 參數型別 `ThreadPoolTaskExecutor` → `ExecutorService`
   - Lambda 內轉型為 `ThreadPoolExecutor` 存取 queue/active/poolSize

5. **`GlobalExceptionHandler.java` — 差異化 HTTP 狀態碼**：
   - 訊息含 "timed out" → `504 GATEWAY_TIMEOUT`
   - 訊息含 "pool exhausted" → `503 SERVICE_UNAVAILABLE`
   - 其他 → `500 INTERNAL_SERVER_ERROR`

### 修正後執行流程

```
HTTP 請求 → CqlExecutionService.executeWithProvider()
  → executorService.submit(doExecute)        ← 真正的 Future
  → future.get(120s)
     ├─ 成功 → 回傳結果
     ├─ TimeoutException → future.cancel(true) → 設定中斷旗標
     │    └─ 下一次 retrieve() 在 InterruptAwareRetrieveProvider
     │         → 檢查 Thread.isInterrupted() → 拋出 CqlExecutionException
     │              → worker 執行緒乾淨退出
     └─ RejectedExecutionException → 503 Service Unavailable
```

### 已知限制

不含 FHIR retrieve 的純運算 CQL（臨床 CQL 中極少見）無法被中斷，因為沒有 `retrieve()` 檢查點。執行緒最終會透過 `allowCoreThreadTimeOut` 回收。

### 驗證

- `mvn compile` 零錯誤（266 source + 66 test 檔案）
- Docker 映像建置成功（`mvn package -DskipTests`）
- 所有型別匹配一致：`AsyncConfig` → `ExecutorService` → `CqlExecutionService` / `MetricsConfig`

---

## BUG-065 — CqlController IDOR 授權修復 + LIKE 萬用字元注入防護

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（存取控制 / 注入） |
| **影響範圍** | `CqlController.java`、`CqlLibraryService.java`、`MeasureDefinitionService.java` |

### BUG 描述

安全審查發現 2 項問題：

1. **CqlController 缺少授權檢查（IDOR）**（HIGH）：`GET /api/cql/libraries/owner/{username}` 與 `GET /api/cql/libraries/shared/{username}` 允許任何已認證使用者查詢**任意其他使用者**的程式庫，未驗證請求者身份。對比 `MeasureController` 的相同端點已正確實作 `ownershipVerifier.isAdmin() || getCurrentUsername().equals(username)` 檢查。
2. **LIKE 萬用字元注入**（MEDIUM）：`CqlLibraryService.getSharedLibraries()` 與 `MeasureDefinitionService.getSharedMeasures()` 直接將 `username` 串接至 LIKE 模式（`%"username"%`），未跳脫 `%` 和 `_` 萬用字元。攻擊者可傳送 `%` 作為使用者名稱，使 LIKE 模式變為 `%"%"%`，匹配所有已分享記錄。程式碼中已有 `InputValidator.escapeLikeWildcards()` 工具但未被使用。

### 修正方式

1. **CqlController 授權檢查**：
   - `getLibrariesByOwner()` 與 `getSharedLibraries()` 加入 `ownershipVerifier` 檢查
   - 非本人且非管理員時回傳 `403 FORBIDDEN`
   - 與 `MeasureController` 保持一致的授權模式

2. **LIKE 萬用字元跳脫**：
   - `CqlLibraryService.getSharedLibraries()` 套用 `InputValidator.escapeLikeWildcards(username)`
   - `MeasureDefinitionService.getSharedMeasures()` 同步套用

### 驗證

- 後端 `mvn compile` 零錯誤
- 授權模式與 `MeasureController` 完全對齊
- `escapeLikeWildcards` 會跳脫 `%` → `\%`、`_` → `\_`、`\` → `\\`

---

## BUG-064 — CqlEditor paste sanitization 強化 + Monaco 記憶體洩漏修復

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | Monaco 編輯器（前端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 / 記憶體洩漏 |
| **影響範圍** | `frontend/src/components/editor/CqlEditor.tsx` |

### BUG 描述

CqlEditor 審查發現 3 類問題：

1. **不可見字元過濾不完整**（HIGH）：`sanitizePastedText` 僅過濾 4 個 zero-width 字元，遺漏 Bidi 控制字元（`\u202A–\u202E`、`\u2066–\u2069`、`\u061C`、`\u200E–\u200F`）、Soft Hyphen（`\u00AD`）、Line/Paragraph Separator（`\u2028–\u2029`）等。Bidi 字元可被用於 **Trojan Source 攻擊**，讓 CQL 程式碼的顯示與實際語義不同。
2. **onDidPaste 使用 `model.setValue()` 破壞 undo stack**（MEDIUM）：每次貼上都會掃描整份文件並用 `setValue` 替換，導致 undo/redo 歷程完全重建。
3. **Monaco 銷毀時記憶體洩漏**（MEDIUM）：DOM paste event listener 未在 unmount 時移除；`onDidPaste` 和 `onDidChangeCursorPosition` 的 `IDisposable` 未保存；`editorRef` / `monacoRef` 指向已銷毀的實例。

### 修正方式

1. **擴充 sanitizePastedText**：
   - 用單一 character class `[\u200B-\u200F\u202A-\u202E\u2060-\u2064\u2066-\u2069\u061C\u00AD\u180E\uFEFF\uFFF9-\uFFFB]` 取代零散的 4 字元匹配
   - 新增 `\u2028`/`\u2029` → `\n` 轉換（Line/Paragraph Separator）

2. **onDidPaste 改為範圍 sanitize + executeEdits**：
   - 使用 `IPasteEvent.range` 取得貼入範圍，只 sanitize 該範圍文字
   - 改用 `executeEdits('paste-sanitize', ...)` 取代 `model.setValue()`，保留完整 undo/redo 歷程

3. **新增 unmount 清理**：
   - `disposablesRef` 儲存 `onDidPaste` 和 `onDidChangeCursorPosition` 的 `IDisposable`
   - `pasteListenerRef` 追蹤 DOM paste event listener
   - cleanup `useEffect`：unmount 時 dispose 所有訂閱、`removeEventListener`、清空 editor/monaco ref

### 驗證

- TypeScript 編譯零錯誤
- 56 test files / 399 tests 全部通過
- 新增覆蓋的不可見字元包含 Trojan Source 攻擊最常用的 bidi override 序列

---

## BUG-063 — useCqlEditor useCallback 優化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 前端效能（前端） |
| **嚴重程度** | Low |
| **根因類型** | 效能 |
| **影響範圍** | `frontend/src/hooks/useCql.ts` |

### BUG 描述

`useCqlEditor()` hook 內的 `translate`、`validate`、`execute` 三個函式為裸 closure，每次 render 都重建新的函式引用。消費端 `EditorPage.tsx` 的 `handleTranslate` 雖有 `useCallback` 包裝，但依賴 `translateMutation` 物件（`useMutation` 每次 render 回傳新引用），導致 `useCallback` 被穿透，無法穩定引用。

### 修正方式

- `translate`、`validate`、`execute` 三個函式改用 `useCallback` 包裝
- 依賴使用 `.mutate`（TanStack Query 內部以 `useCallback` 包裝，referentially stable）而非整個 mutation 物件，避免因 `isPending`/`data` 等狀態變化導致不必要的重建

### 驗證

- TypeScript 編譯通過
- 消費端 `useCallback` 依賴鏈穩定：`handleTranslate` → `translate` → `translateMutation.mutate`

---

## BUG-062 — FhirController 安全強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 / 配置遺漏 |
| **影響範圍** | `InputValidator.java`、`FhirController.java`、`FhirTerminologyService.java`、`application.yml` |

### BUG 描述

FhirController 安全審計發現 4 項問題：

1. **identifier 參數未驗證**（HIGH）：`/Patient/$search-by-demographics` 的 `identifier` 參數（格式 `system|value`，如 `http://hospital.org/mrn|12345`）直接傳入 Service 層，無輸入驗證，可能被注入惡意字元。
2. **IG URL 路徑變數未驗證**（MEDIUM）：`getIgProfile`、`getIgValueSet`、`getIgCodeSystem` 三個端點的 `{url}` 路徑變數經 URL decode 後直接作為 Map 查詢鍵，未驗證為合法 URL 格式。
3. **RestTemplate 無逾時**（MEDIUM）：`FhirTerminologyService` 的 `RestTemplate` 使用 `new RestTemplate()` 建立，無連線/讀取逾時設定，外部 API（如 RxNav）無回應時執行緒將永久阻塞。
4. **FHIR 連線池耗盡風險**（MEDIUM）：HAPI FHIR 客戶端 socket timeout 30s × 預設 3 次重試 = 單一請求最差 90s，容易耗盡連線池。

### 修正方式

1. **identifier 驗證**：
   - `InputValidator` 新增 `IDENTIFIER_PATTERN = ^[a-zA-Z0-9:./_|\\-]{1,500}$`，覆蓋 `system|value` 格式
   - 新增 `isValidIdentifierParam()` + `requireValidIdentifierParam()`（null-safe）
   - `FhirController.searchPatientsByDemographics()` 加入 `requireValidIdentifierParam(identifier)`

2. **IG URL 驗證**：
   - `InputValidator` 新增 `isValidFhirCanonicalUrl()` — 驗證 http/https scheme、合法 host、無嵌入憑證（不做 SSRF 私有 IP 檢查，因為僅用於 Map 查詢）
   - 新增 `requireValidFhirCanonicalUrl()`
   - 三個 IG 端點在 URLDecode 後加入 `requireValidFhirCanonicalUrl(decodedUrl)`

3. **RestTemplate 逾時**：
   - `new RestTemplate()` 改為 `createRestTemplate()` 靜態工廠
   - 使用 `SimpleClientHttpRequestFactory`：5s 連線逾時、10s 讀取逾時

4. **連線池耗盡防護**：
   - `socket-timeout-ms`：30000 → 15000（15s）
   - `fhirDataProvider` retry `maxAttempts`：3（預設） → 2
   - 最差情境：15s × 2 = 30s（原本 30s × 3 = 90s）

### 驗證

- 所有新增驗證方法為 null-safe，既有測試不受影響
- `isValidIdentifierParam(null)` → true（optional 參數）
- `isValidFhirCanonicalUrl(null)` → true（defensive）
- RestTemplate 逾時確保外部 API 無回應時不會永久阻塞
- 重試預算從 90s 降至 30s，減少連線池壓力

---

## BUG-061 — Measure 元件效能最佳化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 前端效能（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 效能 / 程式碼品質 |
| **影響範圍** | `MeasureComparison`、`DataRequirementsTab`、`TestCasesTab`、`MeasureValidationPanel`、`MeasureReportHistory`、`IndicatorCatalogDialog` |

### BUG 描述

三管齊下審計（reuse / quality / efficiency）Measure 元件目錄後，發現 6 項效能問題：

1. **O(n²) `Math.max`**：`MeasureComparison` 的趨勢圖在 `.map()` 迴圈內反覆呼叫 `Math.max(...dataPoints)`，每次重繪 N 點需 O(N²) 比較。
2. **缺少 `useMemo`**（3 處）：`DataRequirementsTab`、`TestCasesTab`、`MeasureValidationPanel` 的分組/計數邏輯在每次 render 都重建物件，即使資料未變。
3. **無防抖搜尋**：`IndicatorCatalogDialog` 每次按鍵直接觸發 API 查詢（透過 React Query `queryKey` 變化），快速打字會產生大量無用請求。
4. **靜默 `console.error`**：`MeasureReportHistory.handleExport` 的 catch 僅 `console.error`，使用者看不到匯出失敗訊息。

### 修正方式

1. **O(n²) → O(n)**：將 `Math.max(...)` 提升至 `.map()` 之外，僅計算一次。
2. **`useMemo` 包裝**：
   - `DataRequirementsTab`：`grouped`/`resourceTypeCount`/`valueSetCount` 以 `useMemo([requirements])` 包裝
   - `TestCasesTab`：`passCount`/`failCount`/`totalCount` 以 `useMemo([testCases])` 包裝，改為單次迴圈
   - `MeasureValidationPanel`：`groupedIssues` 以 `useMemo([report])` 包裝
3. **300ms 防抖**：新增 `debouncedSearch` 狀態 + `useEffect` timer，React Query 改用 `debouncedSearch` 作為 queryKey。
4. **`showNotification`**：引入 `useNotification` + `extractApiError`，替換 `console.error`。

### 驗證

- TypeScript 編譯 0 errors
- 所有 useMemo 依賴陣列正確（`[requirements]`、`[testCases]`、`[report]`）
- 防抖搜尋：打字停止 300ms 後才送出查詢

---

## BUG-060 — Measure 元件共用化重構

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 程式碼品質（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 程式碼品質 |
| **影響範圍** | 17 個 measure/dashboard 元件、3 個新共用模組 |

### BUG 描述

Measure 元件目錄 29 個檔案中發現三類重複：

1. **`getScoreColor` 重複 7 處**：`EvaluationResultCard`、`BatchEvaluationDialog`、`MeasureReportHistory`、`MeasureComparison`、`MeasurePanel`、`MeasureDashboardPage`、`DepartmentDrilldownChart` 各自定義功能相同但回傳型別不一致的分數→顏色函式。
2. **`downloadBlob` 重複 4 處**：`TestCasesTab`、`MeasureEditor`、`MeasureLibrary`、`MeasureReportHistory` 各自內聯 6 行 `URL.createObjectURL` / `a.click()` / `revokeObjectURL` 下載邏輯。
3. **`(error as Error).message` 不安全轉型 10 處**：Axios 錯誤實際為 `AxiosError` 物件，直接轉型會漏掉後端回傳的 `response.data.message`，專案已有 `extractApiError` 工具函式卻未使用。

### 修正方式

1. **`scoreColors.ts`**：新增 `getScoreChipColor()`（MUI Chip color）、`getScoreThemeColor()`（theme path）、`getScoreHex()`（hex string），7 個檔案改用對應函式。
2. **`download.ts`**：新增 `downloadBlob(blob, filename)`，4 個檔案刪除內聯實作。
3. **`extractApiError` 統一**：10 個 catch block 改用 `extractApiError(err)`，8 個檔案補 import。

### 驗證

- TypeScript 編譯 0 errors
- 7 個 score color 使用點行為一致（null → error、<50 → error、<80 → warning、≥80 → success）
- downloadBlob 行為不變（createObjectURL → click → revokeObjectURL）

---

## BUG-059 — Service 層安全強化 + Builder 元件去重

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 程式碼品質（前後端） |
| **嚴重程度** | Medium |
| **根因類型** | 安全漏洞 / 程式碼品質 |
| **影響範圍** | 7 個後端 Service、10 個前端 Builder 元件、6 個新共用模組 |

### BUG 描述

**後端 Service 層**（7 項）：
1. `FhirClientFactory`：暫存的 `FhirContext` 在 credential 變更後未清除，且 `hapiFhirContext()` 被多執行緒同時呼叫無鎖保護。
2. `VsacService`：日誌中記錄 API key 值（`LOG.info("Using VSAC API key: {}")`）。
3. `TestCaseService`：每次呼叫 `new FhirContext(FhirVersionEnum.R4)` 而非注入共用實例。
4. `EmailService`：捕捉 `MessagingException` 後拋出 `RuntimeException(e.getMessage())`，丟失 stack trace。
5. `EhrConnectionService`：方法級 `@Transactional` 標註在僅讀取操作上，不必要地持有資料庫連線。
6. `PasswordResetService`：SMTP 寄信在 DB 交易內執行，失敗時回滾 token 但使用者已收到信。
7. `CqlTranslationService`：每次呼叫建立新的 `LibraryManager` 與 `ModelManager`，初始化開銷大。

**前端 Builder 元件**（5 項）：
1. `RESOURCE_TYPES` 常數在 `QueryBuilder` 和 `RetrieveBuilder` 中重複定義。
2. `extractName()`/`parseCodeName()` 名稱解析邏輯在 3 個元件中重複。
3. `ConditionalBuilder` 接收 `expressions`/`parameters` props 但從未使用。
4. 6 個元件各自實作 clipboard + notification 邏輯（`navigator.clipboard.writeText` + `showNotification`）。
5. `CodesSection` 和 `ValueSetSection` 各含 ~80 行完全相同的 TW Core 瀏覽 UI。

### 修正方式

**後端**：
1. `FhirClientFactory`：加入 `ReadWriteLock` + credential hash 變更偵測，credential 改變時自動清除快取。
2. `VsacService`：移除 API key 日誌輸出。
3. `TestCaseService`：改注入 Spring 管理的 `FhirContext` Bean。
4. `EmailService`：改 `throw new RuntimeException("Failed to send email", e)` 保留原因鏈。
5. `EhrConnectionService`：移除不必要的 `@Transactional`。
6. `PasswordResetService`：使用 `@TransactionalEventListener(AFTER_COMMIT)` 確保 DB 成功後再寄信。
7. `CqlTranslationService`：快取 `LibraryManager`，透過 `LibraryManagerFactory` 提供。

**前端**：
1. 新增 `fhirResources.ts`（`FHIR_RESOURCE_TYPES` 常數 + `FhirResourceType` 型別）。
2. 新增 `cqlNames.ts`（`extractCqlName()` 函式）。
3. 移除 `ConditionalBuilder` 未使用的 `expressions`/`parameters` props。
4. 新增 `useCopyToClipboard` hook，6 個元件改用。
5. 新增 `useFilteredTwcoreCatalog` hook + `TwcoreBrowser` 元件，刪減 ~160 行重複 JSX。

### 驗證

- TypeScript 編譯 0 errors
- 所有 Builder 元件功能不變
- Service 層注入與快取邏輯正確

---

## BUG-058 — Repository 層簡化

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

## BUG-057 — Model DTO 驗證強化

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

## BUG-056 — CQL 預覽對話框文字顏色修正

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

## BUG-055 — Controller 輸入驗證強化

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

## BUG-054 — Entity 安全強化

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

## BUG-053 — AuthController 安全強化

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

## BUG-052 — CDS 人工製品表格欄位錯位

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

## BUG-051 — 外部連線 CORS 被擋 + PNA header 未覆蓋動態 origin

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

## BUG-050 — CDS 卡片 CodeableConcept 多 coding 只顯示第一個

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

## BUG-049 — CDS 卡片僅顯示資源參考而非過敏藥物名稱

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

## BUG-048 — RxNorm 代碼搜尋無結果 — FHIR 術語伺服器未載入 UMLS 授權碼表

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

## BUG-047 — Fallback paste handler 非同步讀取 clipboardData 導致貼上失效

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

## BUG-046 — Docker 環境下 Monaco Editor 無法貼上（Ctrl+V 無效）

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

## BUG-045 — 代碼查詢本地 TWCORE IG 回傳 ValueSet 名稱且缺少 display name

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

## BUG-044 — Retrieve Builder 依賴不存在的 C3F 外部函式庫致 CQL 無法解析

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

## BUG-043 — TestCaseEditor expectedPopulations 被 React Query refetch 競態重置

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

## BUG-042 — ComparableR4FhirModelResolver 日期轉換破壞 FHIRHelpers 時態運算子

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

## BUG-041 — Encounter.class Java 保留字衝突致 CQL 路徑解析錯誤

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

## BUG-040 — TestCaseService 缺少 Measurement Period 參數致時間過濾失效

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

## BUG-039 — TestCaseService 查詢 FHIR Server 而非使用記憶體內測試 Bundle

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

## BUG-038 — HikariCP 連線池耗盡導致所有 API 逾時、無法登入

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

## BUG-037 — Autocomplete freeSolo 以顯示標籤覆蓋系統 URL 致術語查詢 503

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

## BUG-036 — MeasureLibrary 虛擬捲動表頭與內容欄位錯位擠壓

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

## BUG-035 — DashboardService 多處 NullPointerException 導致 Dashboard API 回傳 500

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

## BUG-034 — Recharts ResponsiveContainer 初始化時計算 width/height 為 -1

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

## BUG-033 — 工具列 Undo/Redo 按鈕無效 — Redux 歷史與 Monaco 原生 undo 脫節

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

## BUG-032 — DataRequirements 未解析 ToConcept 包裝的 CodeRef 及 Case 包裝的日期屬性

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

## BUG-031 — 種子 CQL 語法錯誤導致 DataRequirements 標籤頁空白

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

## BUG-030 — departmentApi/ehrApi/indicatorApi 使用原生 axios 無 JWT 攔截器致 401

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

## BUG-029 — SSE EventSource 無法傳送 Authorization 標頭致 401

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

## BUG-028 — DataInitializer 僅在 dev profile 啟用，Docker 環境無種子資料

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

## BUG-027 — PatientImportEntity @Lob 與 PostgreSQL schema validation 不相容

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

## BUG-026 — Flyway V24-V29 使用 H2 語法導致 PostgreSQL 部署失敗

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

## BUG-025 — CQL 產生器未對 list 型別表達式自動加 exists() 導致驗證失敗

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

## BUG-024 — DataRequirements 未解析 Equal/Equivalent + CodeRef 模式

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

## BUG-023 — Encounter.class 下拉選單顯示 1115 個代碼而非 11 個

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

## BUG-022 — 測試案例結果表格族群名稱未中文化

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

## BUG-021 — DataRequirements 未解析 FHIRHelpers.ToString 包裝的屬性比較

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

## BUG-020 — DataRequirements 標籤頁未顯示 Where 子句中的篩選條件

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

## BUG-019 — CDS Prefetch 執行清除 patientId 導致 Patient context 失效

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

## BUG-017 — PrefetchRetrieveProvider 未展開 ValueSet 導致代碼過濾失效

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

## BUG-016 — CodeableConcept dropdown 使用 ValueSet URL 而非 CodeSystem URL

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

## BUG-015 — VSAC ValueSet 未連接 CQL Engine 導致 CDS 規則失效

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

## BUG-014 — CodeableConcept boundCodes 未使用下拉選單

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

## BUG-013 — TWCORE 選碼導致 Monaco Editor 白屏

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

## BUG-012 — Monaco Editor 夜間模式白屏

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

## BUG-011 — Footer 位置異常：flexbox 佈局修正

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

## BUG-010 — CDS Card 顯示所有表達式擠在一行

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

## BUG-009 — FHIR Choice Type 序列化錯誤（value → valueQuantity）

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

## BUG-008 — CDS Sandbox Invoke 所有 CQL 表達式回傳 null

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

## BUG-007 — Footer fixed 定位仍遮擋操作按鈕

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

## BUG-006 — Backend OOM 導致所有 API 無回應

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

## BUG-005 — Footer 覆蓋頁面內容

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

## BUG-004 — CDS Sandbox 修改資料後無法重新執行

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

## BUG-003 — Observation status 欄位允許自由輸入導致無效值

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

## BUG-002 — CQL 翻譯失敗時 Builder 無法顯示已解析的部分結構

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

## BUG-001 — CQL Builder 解析 CQL 靜默失敗

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

---
