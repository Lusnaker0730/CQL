# CQL Platform — AI 開發指引

> 臨床品質語言 (CQL) 視覺化編輯 + 執行平台
> 本檔的版本與數量為 2026-09-16 實測快照；改動技術棧 / 新增目錄時請一併更新，別讓它再落後四個月。

## 技術棧

| 層 | 技術 | 版本 |
|----|------|------|
| Backend | Spring Boot / Java / Maven | 4.1.0 / 25 |
| Frontend | React / TypeScript / Vite | 19.2 / 5.9 / 7.3 |
| UI | Material-UI (MUI) | 9.1 |
| Editor | Monaco Editor (+ @monaco-editor/react) | 0.55 / 4.7 |
| State | Redux Toolkit + TanStack React Query | 2.11 / 5.100 |
| i18n | i18next + react-i18next | 25.8 / 16.5 |
| DB | PostgreSQL 16 (prod & dev) / H2 (test only) | — |
| Cache | Caffeine (in-process) | — |
| CQL Engine | CQL Framework (cql-to-elm / engine) + HAPI FHIR | 5.3.0 / 8.12.0 |
| Templates | FreeMarker (.ftl) | — |
| Test | JUnit 5 + Mockito / Vitest + React Testing Library | — / 4.1 |

## 目錄結構

```
backend/src/main/java/com/cqlplatform/
  config/          — Spring 配置 (Security, CORS, Cache, Async, Metrics)
  controller/      — REST API (26 controllers)
  entity/          — JPA 實體 (40 entities；18 個帶 tenant_id)
  exception/       — 自訂例外 + GlobalExceptionHandler
  fhir/            — FHIR 相關基礎元件
  model/           — DTO / Request / Response
  repository/      — Spring Data JPA (39 repositories)
  security/        — JWT 認證、API Key、Token Version 即時撤銷、TenantContext、Rate limit
  service/
    ai/            — AI 修 CQL 建議 (Ollama / OpenAI-compatible cloud)
    authoring/     — CQL 產生引擎 (★ 核心)
    cds/           — CDS Hooks
    cql/           — CQL 翻譯 / 執行 / 程式庫
    ecqm/          — eCQM 邏輯
    fhir/          — FHIR 伺服器互動
    measure/       — 品質量測 (最大的一包，~7.6k LOC)
  util/            — 工具類
  validation/      — 輸入驗證
backend/src/main/java/db/migration/ — Java-based Flyway migration (V56)

frontend/src/
  api/             — Axios API 模組 (24 modules)
  components/
    auth/          — 登入 / 密碼重設
    authoring/     — CDS Authoring 視覺化
    builder/       — CQL Builder 元件 (★ 核心, 27 元件)
    cds/           — CDS Hooks UI
    common/        — 共用元件
    cql-libraries/ — CQL 程式庫管理
    dashboard/     — 品質 dashboard
    debug/         — 執行除錯 / trace 檢視
    ecqm/          — eCQM 建構器
    editor/        — Monaco CQL 編輯器
    ehr/           — EHR 連線 (SMART Backend Services)
    execution/     — CQL 執行面板
    fhir/          — FHIR 瀏覽器
    landing/, layout/, learn/ — 公開頁 / 版面 / 教學中心
    measure/       — 品質量測
    patient-generator/ — TW Core IG 假病人產生器 (7 元件)
    terminology/   — 術語瀏覽器
    testcase-builder/ — 測試案例建構
  contexts/        — React Context (7 providers)
  config/
    twcore/        — TW Core IG 假病人產生器設定 (5 JSON + types)
  hooks/           — 自訂 Hooks (37 files)
  locales/{en,zh-TW}/ — i18n JSON (14 namespaces)
  constants/       — 集中常數 (24 modules: timing, layout, queryConstants 等)
  pages/           — 路由頁面 (26 pages, 全部 lazy-loaded)
  store/           — Redux slices (editor, execution, auth, artifact)
  types/           — 手寫 TS 型別 (index.ts 148 個 export；尚未從 OpenAPI 生成)
  utils/           — 共用工具 (25 modules)

backend/src/main/resources/
  templates/cql/   — FreeMarker 模板 (32 files)
    artifact.ftl, ecqm-artifact.ftl    — 主模板
    modifiers/     — 23 modifier templates
    elements/      — 3 element templates
    fragments/     — cds-card, error-statement
  db/migration/    — Flyway forward migrations (V1~V74；V56 為 Java migration)
  db/rollback/     — 手動 rollback SQL（每個 V__ 對應一份，非 Flyway 管理；CI 會檢查數量相符）
  application.yml  — 主配置

scripts/smoke/     — 本機 / CI 整合 smoke harness (38 scenarios)
```

## 開發指令

```bash
# Dev DB (PostgreSQL via Docker — 首次需先啟動)
docker compose -f docker/docker-compose.dev-pg.yml up -d

# Backend（本機 Maven 在 C:\Users\alumi\apache-maven-3.9.12）
mvn=/c/Users/alumi/apache-maven-3.9.12/bin/mvn
$mvn -f backend/pom.xml test                       # 執行測試
$mvn -f backend/pom.xml compile                    # 編譯
$mvn -f backend/pom.xml test -Dtest=XxxTest        # 單一測試
# 改了 service / repository 簽名後，Maven incremental compile 不會重編相依的測試類：
#   rm -rf backend/target/classes backend/target/test-classes 再跑，才是真的 fresh verify

# Frontend
cd frontend && npm run dev          # 開發伺服器 (port 5173)
cd frontend && npm test             # Vitest 測試
cd frontend && npm run build        # 產出建置
cd frontend && npx tsc --noEmit     # 型別檢查
cd frontend && npm run lint         # ESLint（CI 用 --max-warnings 0，一個 warning 就紅）
cd frontend && python scripts/check-i18n-sync.py   # en / zh-TW key 同步檢查（CI 會跑）
```

## 開發慣例

### Commit 與 Changelog
- Commit 格式: `feat|fix|docs|refactor: 描述 (#PAT-NNN)`（或 `(#BUG-NNN)`）
- 每次 commit 後更新 `docs/CHANGE_LOG.md`（表格格式，繁體中文）；純文件同步的 `docs:` commit 慣例上不加列
- **commit 欄位留空**（結尾 `| |`）——PR merge 後 `changelog-backfill.yml` workflow 會自動填入 hash
- ID 格式: `PAT-###`（功能/修補）、`BUG-###`（修復）；目前最新 PAT-235 / BUG-145
- 本機手動回填：`scripts/changelog/fill-hash.sh --commit`（跑 `.github/scripts/changelog-backfill.py`）
- PR 是 **squash merge**；本機分支 merge 後會看起來永遠 1 ahead / 1 behind，別誤判

### i18n（必須遵守）
- 所有 UI 文字使用 `useTranslation('namespace')`，**禁止硬編碼字串**
- 新增/修改文字時，**必須同時更新** `locales/en/*.json` 和 `locales/zh-TW/*.json`
- 14 個 namespace: common, validation, editor, builder, measures, cds, fhir, terminology, authoring, admin, ecqm, patientGenerator, cqlLibraries, landing

### 多租戶隔離（必須遵守）
- 租戶來源：`security/TenantContext`（ThreadLocal），由 `JwtAuthenticationFilter` 從 JWT `tenant` claim 填入；API-key 認證由 key 擁有者的 user 紀錄解析。非 JWT 路徑可能為 `null`
- **沒有 Hibernate `@Filter`、沒有 Postgres RLS、沒有 AOP**——隔離完全靠每條 query 手寫。新增 repository 方法一律要 tenant-scoped（慣例：`findByIdAndTenantId(...)`、`existsByTenantIdAnd...`），service 用各自的 `effectiveTenantId()`（null → `default` 租戶）。BUG-131~139 就是一整串「忘了加條件」的跨租戶洩漏
- 跨租戶存取一律回 **404 not-found**（不可探測），不要回 403
- 平台操作員限定的操作（全平台使用者管理、稽核清除、IndicatorCatalog 全域表）用 `PlatformOperatorGuard.require()`；擁有者檢查用 `OwnershipVerifier`
- 新增含租戶資料的表：migration 加 `tenant_id`（NOT NULL + 回填），rollback 腳本對應
- CDS 服務有 in-memory registry（`CdsHooksService.serviceConfigs`），任何改 DB 的路徑都必須同步 put / remove，否則 `invokeService` 讀舊值到重啟為止（BUG-142）

### Backend 模式
- **量耗時用 `util/Stopwatch`（`System.nanoTime()`），不要 `System.currentTimeMillis()` 相減**（BUG-145）：牆上時鐘在執行期間會被 NTP / VM 校時往回調，相減會得到負值。`measure_report.evaluation_duration_ms` 有 CHECK `>= 0`，負值曾讓整份評估報表被資料庫拒絕，而評估 API 照樣回 200。會被**儲存**的耗時尤其要用單調時鐘
- **附屬資訊不得拖垮紀錄**：`MeasureReportService.saveReport` 對無法成立的耗時存 NULL（未知），不存 0、不讓 insert 失敗
- Controller → Service → Repository 分層架構
- Service 層**禁止使用** HTTP 概念 (`HttpServletRequest`, `@ResponseStatus`)
- 使用 `@RequiredArgsConstructor` + `final` 欄位做依賴注入，不用 `@Autowired`
- 多步驟變更必須加 `@Transactional`
- 拋出領域例外（`ResourceNotFoundException`, `ValidationException`, `MeasureNotEvaluableException` 等），GlobalExceptionHandler 統一處理（對照表見 `backend/CLAUDE.md`）
- CQL 產生：`CqlArtifactBuilder` 組裝 context Map → 呼叫 FreeMarker 模板
- **儲存指標的評估一律經 `MeasureEvaluationService.evaluateMeasure(request, id, def)`**（3-arg overload）：`MeasureStatusGuard` 在此只放行 `active`（PAT-219）。新增評估路徑不要繞過這個 overload，否則守門會漏

### CQL 執行（BUG-107 規範）
**翻譯 + 執行使用者 CQL 一律走 `seedCompiledLibrary(...)` helper**（`CqlExecutionService` 中）：
```java
translator = CqlTranslator.fromText(cql, libraryManager);
elmLibrary = translator.toELM();
seedCompiledLibrary(libraryManager, elmLibrary.getIdentifier(), translator.getTranslatedLibrary());
// ... 然後才呼叫 engine.evaluate()
```
- **禁止**直接 `CqlTranslator.fromText(cql, libraryManager)` 後就 `engine.evaluate()`，否則引擎會走 `DatabaseLibrarySourceProvider` 撈 `cql_library` 表同名同版本舊版 CQL 執行，而不是你剛翻譯的文字
- helper 內部同時 (1) `put` 入 `libraryManager.compiledLibraries` cache，(2) 對 `statements.def` 按 name 排序（engine 的 `Libraries.resolveExpressionRef` 用 binarySearch，不排會爆 `Could not resolve expression reference`）
- Regression 測試：`CqlExecutionIntegrationTest.LibraryResolutionRegressionTest` 鎖住此不變式

### 術語 / value set（PAT-230）
- 解析順序：**平台自有 value set**（`value_set` 表，租戶範圍）→ 內建 TW Core IG → VSAC（URL 含 `cts.nlm.nih.gov`）→ 遠端術語伺服器
- `FhirTerminologyService` 的 Caffeine 快取（`valueSets` / `codeValidation` / `codeLookup`…）以 **URL 為 key、全程序共用、不分租戶**。任何租戶範圍的術語資料都**不可**放進這些 `@Cacheable` 方法，要在 controller 或呼叫端先查（`FhirController` 的 `$expand` / 搜尋 / `$validate-code` 就是這樣接的）
- 引擎的 `TerminologyProvider` 一律由 `FhirTerminologyService.createTerminologyProvider()` 取得：它每次回傳一個綁定**當下租戶**的 `PlatformTerminologyProvider`，要在 request thread（或 `TenantContext.callWith` 內）呼叫，每次評估呼叫一次
- value set 啟用（active）後代碼即凍結，改代碼 = 建立新版本；CQL 可用 `version '…'` 釘選。Builder 產生的宣告是 `valueset "<name>": '<oid 欄位的 URL>'`——artifact JSON 的 `oid` 才是 URL，`name` 只是識別名稱

### CQL 引擎值模型（PAT-231，cql-engine 5.x）
- 5.x 起引擎的每個值都是 `org.opencds.cqf.cql.engine.runtime.Value`（`runtime.Boolean` 不是 `java.lang.Boolean`；FHIR 資源是 `ClassInstance` 樹，不是 HAPI 物件）。`Object v = result.getValue()` 照樣**編得過**，但 `v instanceof Boolean` 會是 false——母群會靜默清空
- **平台自己的程式一律走純 Java**：引擎結果只能經 `service/cql/CqlValues.unwrap` 取出（`CqlExecutionService` 四個取值點已接）；給引擎的 FHIR 資源經 `CqlValues.fromFhir`、參數經 `CqlValues.wrap`。不要在別處 import `runtime.*` 型別做判斷；`CqlValues` 是唯一同時認識兩邊的地方
- `RetrieveProvider.retrieve` 回傳 `Iterable<Value>`、context 參數是 `String`；自寫的 provider 產生 HAPI 資源時要 `fromFhir`
- Maven 座標：`engine-fhir-jvm`（5.x 的 `engine-fhir` 是 0 class 空殼）
- 4.x 的 `ComparableR4FhirModelResolver` 兩個 override（Encounter.class、BUG-106 Enumeration 歧義）在 5.x 沒有掛點也不需要：`toCqlValue` 走 HAPI runtime definition 轉換。`CqlValuesTest` 與 golden 測試鎖住
- **分層（PAT-233）**：`StratifierEvaluator` 一向以 `String.valueOf(value)` 當 stratum key，所以 define 回什麼值就分什麼層——`kind=criteria` 是 `true`/`false`，`kind=value`（builder 的 `gender` / `ageBands` 來源）是值本身；key 規則在 `StratifierEvaluator.stratumKey`（Code map → `code (display)`、空/`null` → 不屬任何層）。`toSerializable` 把 FHIR primitive `ClassInstance`（只有 `value` 元素，含 `FHIR.AdministrativeGender` 這種綁定型）轉成它的值、`Code`/`Concept` 轉成小 map；**別讓 stratifier define 回整個資源**。分層分數依 scoring type（CV 分層沒有分數，observation 值沒有逐層收集）。eCQM workspace 的 artifact 層級 stratifier 在 publish 時套到每個 group
- **多元件分層（PAT-235）**：`StratifierDefinition.components[]`（每元件自己的 define `Stratifier <id> <code>`，stratifier 本身 `criteriaExpression` 為 null）。評估器把各元件值以 ASCII 分隔字元（`\u001E` code / `\u001F` 元件）編成自描述的內部 key 放進同一個累積 map，`buildStratifierResults` 解回 `StratifierResult.components` 並把 `strataValue` 顯示成 `female | 65+`；`ValueKeys.of` 會剝掉這兩個字元，值偽造不了元件邊界。任一元件無值 → 不屬任何層。報表 `measure_report_stratifier.component_values`（V74，JSON），FHIR MeasureReport `stratum.component[]`、CQFM `stratifier.component[]`（匯入會讀回）。測試案例期望值仍是字串（寫 `female | 65+`）；smoke `assert.sh` 也仍以 `strataValue` 比對，另可斷言 `components`
- **補充資料 / 風險校正因子（PAT-234）**：指標宣告的 `supplementalData` / `riskAdjustments` define 由 `SupplementalDataEvaluator` 逐病人以 `ValueKeys.of`（與分層同一條 key 規則）分桶成「值 → 病人數」分布（`MeasureEvaluationResult.supplementalDataResults`），持久化在 `measure_report_supplemental_data`（V73，一列一值、null 值列 = 無值病人數，RLS 經 `measure_report`），`NormalizedMeasureReportReader` 會重建；FHIR MeasureReport 匯出以平台 extension（`<canonical base>/StructureDefinition/measurereport-supplemental-data`）攜帶——summary 報表沒有標準元素放 SDE 分布。舊的 `supplementalData` 計數 map（`aggregateCustomExpressions`，字串會被丟掉）仍在，只是 UI 有分布時不再顯示它。eCQM workspace 的 SDE 元素在 publish 時依 `usage` 映進 `MeasureDefinition`（以前一個都沒映）；RAF define 名應以 `RAF` 開頭（QM IG 3.19，builder 只警告不擋）。**這不是風險模型**：IG 只帶變數，不算校正後的率
- **逐子句覆蓋率（PAT-232）**：`service/cql/ClauseCoverageCollector` 實作 5.x 的 `BreakpointHandler`，掛在 `engine.getState().setBreakpointHandler(...)`，引擎每個運算式節點都會呼叫——**只在** `CqlExecutionRequest.clauseCoverage=true` 時建立（`TestCaseService` 除錯模式與 `measureClauseCoverage`），`$evaluate-measure` 與一般測試案例執行不碰。分母是 `BaseElmLibraryVisitor` 靜態走訪 ELM（有 `localId` + `locator` 的節點），分子是 handler 的動態記錄，兩者必須來自同一次翻譯的 `Library`。引擎**不**對 `and` 短路、query 的 `where` 逐次迭代各記一次——測試鎖住的是引擎事實，別在前端套語言假設。覆蓋率不持久化（`persistRunResult` 抹掉）

### CQL 執行錯誤/警告曝露（PAT-066）
`CqlExecutionResponse` 除 `results` 外另含：
- `errors: List<String>` — per-define runtime exception 摘要（從 `CqlEngine` 的 DebugResult harvest，附帶 `SourceLocator` 前綴）
- `warnings: List<String>` — CQL 翻譯期 warnings
- `CqlEngine` 初始化時掛 `DebugMap(loggingEnabled=true)`，否則引擎內部例外會被 `shouldDebug()` 返回 NONE 而靜默吞掉
- 前端 `ExecutionPanel` 以 MUI Alert 分別以 `severity="error"` / `"warning"` 顯示

### Frontend 模式
- 純函數式元件 + Hooks（無 class components）
- MUI + `sx` prop 做樣式，遵循專案 theme
- API 呼叫用 `src/api/` 模組 + TanStack React Query hooks
- Redux 用於全局狀態（editor content, auth token）
- Context 用於功能性狀態（preferences, notifications, terminology drawer）
- 效能：善用 `useMemo` / `useCallback`，大列表用 `react-window`
- 測試 render 用 `src/test/test-utils.tsx`（含 Redux / Query / Router / Theme providers）；它**不**初始化 i18next，測試要 mock `react-i18next` 讓 `t` 回傳 key

### 安全機制
- JWT Token Version 即時撤銷：`TokenVersionService` + Caffeine cache (30s TTL)
- 登出/改密碼/改角色/停用帳號 → `bumpVersion()` → 舊 JWT 30 秒內失效
- JWT claim `"tv"` 攜帶 token version，`JwtAuthenticationFilter` 驗證時比對 DB 版本
- V41 migration: `app_user.token_version` 欄位
- Rate limit 三層：`RateLimitFilter`（per-IP tier）、`UserRateLimitFilter`、`TenantRateLimitFilter`（PAT-213 每租戶合計）
- 密碼鎖定：`LoginAttemptListener`（PAT-094）；PHI 欄位 `@Convert(EncryptionConverter.class)`（PAT-100）

### 前端常數集中管理
- `constants/timing.ts` — 防抖、自動儲存、通知延遲時間
- `constants/layout.ts` — 編輯器高度、圖表尺寸、列表最大高度
- `constants/queryConstants.ts` — React Query staleTime / refetchInterval
- 禁止在元件中硬編碼 magic number，統一從 `constants/` import

### 測試
- Backend: JUnit 5 + Mockito，157 個測試檔案（36 個 `@SpringBootTest`，其餘純 Mockito）
- Frontend: Vitest + React Testing Library，122 個測試檔案
- 型別安全: 修改 tsx 後執行 `npx tsc --noEmit` 驗證
- 目前**沒有** coverage 門檻（jacoco 無 `check`、vitest 無 `thresholds`）、沒有 E2E；整合面靠下面的 smoke harness

### 本機整合 Smoke Test（push 前必跑）
```bash
scripts/smoke/run.sh          # 全部 scenarios，~60-120s（首次要 build image，較久）
scripts/smoke/run.sh 31-*     # 單一 scenario（glob）
scripts/smoke/run.sh --keep   # debug 時保留 stack
```
36 個 scenario：每個 scoring type 一個 canonical scenario（proportion/ratio/CV/cohort）+ CDS hooks + CQL execute debug/error 契約 + authoring CQL 生成 + measure 生命週期守門 + 測試案例結構化期望值 + 逐子句覆蓋率 + 值型分層 + 補充資料分布 + 多元件分層，走完整 save → publish → evaluate pipeline 打真 Docker 堆疊。單元測試全綠 ≠ 整合工作 — 這 harness 擋 BUG-110/111/#230 這類「翻譯後才爆」家族。詳情見 `scripts/smoke/README.md`。需要 Docker Desktop 在跑。Harness 在跑 scenario 之前有**開機檢查**（BUG-144）：後端容器只要重啟過一次就直接失敗——`restart: unless-stopped` 會讓「第一次開機崩潰」看起來完全正常，`DataInitializer` 的示範指標 insert 就這樣在空資料庫上崩潰而沒人發現（依程式碼推論自 2026-07 的 V61 起）；`DataInitializer` 只在 `dev` / `docker` profile 執行，H2 測試碰不到它。PAT-220 起 CI 也跑（`.github/workflows/smoke.yml`：backend / docker / smoke 變更的 PR 與 main push），本機跑不了時至少 PR 上會看到。

## 關鍵檔案速查

| 用途 | 檔案 |
|------|------|
| CQL 產生引擎 | `service/authoring/CqlArtifactBuilder.java` |
| 表達式引擎 | `service/authoring/ExpressionCqlEngine.java` |
| FreeMarker 引擎 | `service/authoring/CqlTemplateEngine.java` |
| CQL 翻譯 | `service/cql/CqlTranslationService.java` |
| CQL 執行 | `service/cql/CqlExecutionService.java` |
| 指標評估入口 | `service/measure/MeasureEvaluationService.java` + `MeasureStatusGuard.java` |
| 例外處理 | `exception/GlobalExceptionHandler.java` |
| 租戶上下文 / 平台操作員 | `security/TenantContext.java`, `security/PlatformOperatorGuard.java` |
| JWT Token Version | `service/TokenVersionService.java` |
| 平台自有 value set | `service/terminology/PlatformValueSetService.java` + `PlatformTerminologyProvider.java`；UI `components/terminology/PlatformValueSetTab.tsx` |
| 指標交換封裝 (CQFM) | `service/measure/CqfmMeasureBuilder.java`, `CqfmLibraryBuilder.java`, `FhirMeasureBundleService.java` |
| CQL Builder UI | `components/builder/` (27 元件) |
| CDS Authoring UI | `components/authoring/` |
| eCQM UI | `components/ecqm/` |
| 假病人產生器 UI | `components/patient-generator/` (7 元件) |
| 假病人產生邏輯 | `utils/fhirPatientGenerator.ts` + `twDemographics.ts` |
| TW Core 設定檔 | `config/twcore/` (conditions/observations/medications/allergies/scenarios JSON) |
| Monaco CQL 語法 | `utils/cqlSyntax.ts` (48KB) |
| API 客戶端 | `api/client.ts` (Axios instance) |
| 前端常數 | `constants/timing.ts`, `layout.ts`, `queryConstants.ts` |
| 路由 | `App.tsx` (26 routes, lazy-loaded) |
| 主配置 | `backend/src/main/resources/application.yml` |
| Flyway 遷移 | `backend/src/main/resources/db/migration/` |
| Docker | `docker/docker-compose.yml` |
| Smoke harness | `scripts/smoke/run.sh` + `scenarios/` |
| 上線前 review 清單 | `docs/pre-launch-production-readiness-review.md`（每項有 Status，還有幾項 TODO） |

## TFDA 法規文件工作流（必須遵守）

本專案受 TFDA 法規管理，開發過程中的需求、設計、風險、驗證紀錄直接用中文寫在 GitHub Issues/PRs，最後自動彙整成 IEC 62304 / ISO 14971 格式的法規文件。

### 觸發時機與對應動作

| 開發情境 | 你必須做的事 |
|---------|------------|
| 新增功能或使用者需求 | 用 `gh issue create` 建立 **[需求]** Issue（模板：`software_requirement.yml`），填寫需求描述、臨床情境、驗收條件、風險等級、安全性等級 |
| 設計技術方案 | 用 `gh issue create` 建立 **[設計]** Issue（模板：`design_specification.yml`），填寫設計方案、架構影響、安全考量，**必須在「關聯需求」填入對應的需求 Issue 編號** |
| 識別到潛在風險 | 用 `gh issue create` 建立 **[風險]** Issue（模板：`risk_analysis.yml`），填寫危害情境、嚴重度、發生機率、控制措施、殘餘風險，**必須在「關聯項目」填入對應的需求/設計 Issue 編號** |
| 完成測試驗證 | 用 `gh issue create` 建立 **[驗證]** Issue（模板：`verification_record.yml`），填寫測試目的、步驟、預期結果，**必須在「關聯需求」填入對應的需求 Issue 編號** |
| 安全性等級 B/C 的功能 | **必須同時建立**需求 + 設計 + 風險 + 驗證四個 Issue，確保追溯完整 |

### Issue 建立規則

1. **標題前綴**：`[需求]`、`[設計]`、`[風險]`、`[驗證]` — 必須使用
2. **Labels**：用 `gh issue create` 時模板不會自動套標籤，**要自己加** `--label`（`IEC62304:需求`、`IEC62304:設計`、`ISO14971:風險`、`IEC62304:驗證`）
3. **追溯連結**：設計/風險/驗證 Issue 中**必須用 `#編號` 引用對應的需求 Issue**，這是追溯矩陣自動建構的依據
4. **內容語言**：中文（直接匯出為 TFDA 文件，不需翻譯）
5. **安全性等級標籤**：需求 Issue 需額外加上 `安全性等級-A`、`安全性等級-B` 或 `安全性等級-C`
6. **別在 issue / PR 內文寫無關的 `#數字`**（例如「readiness review #2」）——會被當成 issue 連結拉進追溯；寫「第 2 項」
7. dropdown 欄位（風險等級、安全性等級、嚴重度、發生機率、測試結論）要用模板裡的選項字串（如 `3 - 嚴重 (Serious)`、`通過 (Pass)`）

### Issue Body 格式（YAML 表單解析用）

腳本透過 `### 標題` + 換行後的內容來解析欄位，建立 Issue 時使用以下格式：

```
### 需求描述

（內容）

### 臨床情境

（內容）
```

使用 `gh issue create` 搭配 `--body-file` 時，須遵守此 `### heading\n\nvalue` 格式（Windows 上內文含 emoji 別走 stdin，走檔案）。

### PR 法規追溯（CI 強制檢查）

PR 描述自動載入中文模板（`.github/pull_request_template.md`），**必須填寫**：
- 變更說明、關聯 Issue、測試紀錄、風險評估、IEC 62304 / ISO 14971 追溯表

**CI 自動檢查（`.github/workflows/regulatory-check.yml`）：**
- ❌ Block：PR 描述未包含任何 `#NNN` Issue 引用
- ❌ Block：安全性等級 B/C 的需求 Issue 缺少對應的設計/風險/驗證 Issue（**PR 內文每一個 `#NNN` 都會被追溯**，順帶提到的 B 級 issue 也算——前瞻引用請寫「issue 700」不加 `#`）
- ⚠️ Warn：需求 Issue 缺少安全性等級標籤
- ⚠️ Warn：法規 Issue 內容缺少 `### 標題` 格式（腳本無法解析）
- ✅ Skip：`docs:` 開頭的 PR 標題不觸發檢查

其他 PR gate：GitGuardian 掃 PR **全部 commit**（測試 fixture 裡 `"password":"literal"` 也會中，改用 Map 組 JSON；中了要 squash 重寫歷史）、Security Scan（Trivy backend + frontend，一次只報第一個發現）。

### 法規文件產生

```bash
# 產出 6 份 TFDA 法規文件（需要 GITHUB_TOKEN）
GITHUB_TOKEN=$(gh auth token) python regulatory_docs/scripts/generate_regulatory_docs.py \
    --repo Lusnaker0730/CQL --version 1.0.0

# 產出測試報告（需先執行後端測試）
python regulatory_docs/scripts/generate_test_report.py \
    --backend-reports backend/target/surefire-reports \
    --output regulatory_docs/output --version 1.0.0
```

產出文件放在 `regulatory_docs/output/`：SRS、SDS、風險報告、驗證報告、追溯矩陣、變更管制紀錄。

### 法規文件目錄

```
regulatory_docs/
├── scripts/          — generate_regulatory_docs.py, generate_test_report.py
├── templates/        — 6 個 Jinja2 中文模板（含 TFDA 表頭）
└── output/           — 產出檔案
```

## 部署（重點）

- merge 到 main → CI 綠 → `deploy.yml` 只把 image 推到 GHCR（`:latest` + `sha-xxxxxxx`），**不會**部署到 VM
- 上線是手動 SSH 到 VM `docker compose pull backend frontend && up -d --no-build`（見 `DEPLOYMENT_GUIDE_zh-TW.md` §3.5）；**絕不在 VM 上 `docker compose build`**（2 核，會把跑中的 backend 餓死）

## 注意事項

- FHIR resource properties 定義在 `frontend/src/utils/cqlSyntax.ts` 的 `fhirResourceProperties`
- CQL 字面值正則: `CQL_LITERAL_RE` 在 `QueryBuilder.tsx`，用於自動引號判斷
- `escapeCqlString()` / `formatFieldValue()` 統一定義在 `utils/cqlString.ts`
- Monaco Editor 整合: `useCqlEditor` hook 管理編輯器生命週期
- 前端 dev server proxy: `/api/*` → `localhost:8080`
- 前端時間/尺寸常數統一在 `constants/` 目錄，禁止在元件中寫 magic number
- 假病人產生器: 純前端實作，臨床資料由 `config/twcore/*.json` 驅動，新增/修改病症只需改 JSON 不需改程式碼
- `utils/random.ts` 提供共用隨機函數（`randomInt`, `randomElement`, `pickRandom`），禁止在其他檔案重複定義
- Docker 部署: `docker/docker-compose.yml`（postgres, backend, frontend, hapi-fhir, monitoring stack）
- 病人產生器上傳 Bundle 用 `PUT ResourceType/id` 保留 client-side ID（`GenerationResultPanel.tsx`）；不要改回 `POST ResourceType`，否則 HAPI 重配 ID 而前端搜尋原 id 會找不到
- TWCDI CQL 範本語法慣例（`constants/twcdiTemplates.json`）：choice type 用 `(X.value as Quantity)` / `(X.medication as CodeableConcept)` 存取；日期排序用 `return { ..., date: X.dateField.value } sort by date desc` 而不要 `sort by X.dateField desc`（HAPI DateTimeType 不實作 Comparable 會爆）；避免 `FHIRHelpers.ToDateTime(X)` 直接當欄位值（null 時 dispatcher 在 dateTime/instant 重載間歧義）
- `*.sh` 與 Docker 相關檔案 `.gitattributes` 強制 LF；Windows 工作區可能是 CRLF，commit 時 git 會正規化
