# 軟體設計規格書 (Software Design Specification)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-SDS-1.0.0 |
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

本文件定義 CQL Platform 的軟體設計規格，依據 IEC 62304 醫療器材軟體生命週期流程標準。

## 2. 系統架構概述

CQL Platform 採用前後端分離架構：

- **後端**：Spring Boot 3.2 / Java 21 / PostgreSQL
- **前端**：React 18 / TypeScript / Vite / MUI 5
- **CQL 引擎**：CQL Framework + HAPI FHIR

## 3. 軟體設計項目


### SDS-001 [設計] Smoke contract-lock scenarios 17/20 + 附帶 backend 修復

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#338](https://github.com/Lusnaker0730/CQL/issues/338) |
| 建立日期 | 2026-04-22 |
| 狀態 | open |

**設計方案：**

**Harness 擴充（3 個 asserter + 1 個 fixture flag）**：
1. `assert-cds.sh` 加 `retrieveTracesCount` exact-match — 讀 `.debug.debugTrace.retrieveTraces | length`；fail 時把 actual rows 印出供 debug
2. `assert.sh` 加 `checkProvenance: true` flag — 接受 optional 3rd arg `measureId`，若 flag 開啟則 curl `GET /api/measures/{id}/reports` 並驗首筆 row 的 3 個 provenance 欄位
3. `assert-cql-debug.sh` 加 `expectedHttpStatus` + `errorInfoPhase` + `errorInfoRequiredFields` — 支援 500 error path 的 ErrorResponse 斷言
4. `execute-cql.sh` 改輸出為 `STATUS\n---HTTP_STATUS_BODY---\nBODY` 兩段格式，讓同一個 assert script 處理 2xx / 5xx 兩條路徑

**Scenario 17（BUG-116 regression lock）**：
- Service 含 4 個 defines（`AllObservations`, `HasAnyObservation`, `ObservationCount`, `Card`），`Card` 依次 reference 其他 3 個
- Invocation prefetch 含 2 個 Observation
- Expected: `cardCount: 1`, `retrieveTracesCount: 1`

**Scenario 20（PAT-098 editor errorInfo）**：
- Request 含 CQL 呼叫 `ThisFunctionDoesNotExist()` — 翻譯失敗
- Expected: `expectedHttpStatus: 500`, `errorInfoPhase: cql_translation`, `errorInfoRequiredFields: [phase, errorType, message]`

**架構影響：**

- 無 API / schema / migration 變動
- 測試 harness 新增約 80 行 shell + 2 個 scenario 目錄
- 1 個 Java class 擴展（TracingRetrieveProvider 從 66 行增至 ~130 行）
- 1 個 Java class 小修（EcqmPublishService 新增 1 行 setElmJson）

**關聯需求：** #337

**安全考量：**

（未填寫）



---


### SDS-002 [設計] Debug mode batch eval + per-expression trace post-build

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#333](https://github.com/Lusnaker0730/CQL/issues/333) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |

**設計方案：**

**Before**：`doExecute` 有兩條路徑
- Debug mode：for each expression → `engine.evaluate(Set.of(oneExpression))` → 取 timing → 建 trace
- Normal mode：`engine.evaluate(allExpressions)` 批次 → 取 results

**After**：兩條路徑統一
- 兩個 mode 都走 `engine.evaluate(allExpressions)`（batch-first）
- 批次失敗時 fallback 為 per-expression loop（與原有 normal-mode fallback 相同，用於處理 FHIRHelpers overload 歧義等情境）
- Debug mode 在 eval 結束後做一次 post-build：遍歷 `expressions` 從 `results` map 組裝 `ExpressionTrace`，填 `sourceLocator` + `dependencies` + `evaluationTimeMs`

**Timing 來源**：
- Batch 成功：`perExpressionTimings` map 空 → 所有 expression trace 的 `evaluationTimeMs=0`（註解明示 "not measured — see totalTimeMs"）
- Batch 失敗 fallback：per-expression loop 用 `try/finally` 記錄 `System.currentTimeMillis()` 差值到 `perExpressionTimings`

**架構影響：**

- 單檔變動：`CqlExecutionService.java` 的 `doExecute` 內部
- 無介面變動：`CqlExecutionResponse.DebugTrace` schema 不變、`ExpressionTrace.evaluationTimeMs` 仍是 long（0 合法）
- 無 DB / migration / API 路由變動
- `TracingRetrieveProvider` 完全不動——它仍然記錄每次實際 retrieve 呼叫，只是 debug mode 現在只呼叫一次

**關聯需求：** #332

**安全考量：**

1. **Batch timing 資訊損失**：per-expression wall-clock 0ms 可能讓 author 錯以為某 expression 很快。但 totalTimeMs 仍是準確的；若 author 真需要 per-expression timing，故意構造讓 batch fail 的情境（罕見）或未來可另做 engine-level instrumentation。
2. **Regression 風險**：原有 per-expression loop 有 error handling 分支（`runtimeErrors.add`、`CqlException` source locator 萃取）；新路徑靠既有 normal-mode fallback 處理，行為等價——現有 `CqlExecutionIntegrationTest` + smoke scenario 16 保證 regression 被抓到。
3. **Fallback 路徑仍然有 N× retrieve 問題**：若 batch fail 進 fallback，N× retrieve 回來了。但：(a) fallback 本來就是 rare path，(b) debug panel 上這時真的就是 N 個獨立 evaluate，retrieve 也是真實的 —— 不是假訊號。



---


### SDS-003 [設計] PHI 加密 Phase 1 架構：entity-level @Convert + V55 column widening

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#328](https://github.com/Lusnaker0730/CQL/issues/328) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |

**設計方案：**

**Entity 層透明加密**：依賴既有 `EncryptionConverter`（AES-GCM-256 + PBKDF2WithHmacSHA256 + per-row random IV + `ENC:` prefix）作為 JPA `AttributeConverter`。此 converter 已用於 `UserEntity.email` + 4 個 `EhrConnectionEntity` 欄位，是團隊熟悉的 idiom。

**三件 change scope**：
1. **Entity annotations**：4 個 entity class 加 `@Convert(converter = EncryptionConverter.class)` 到 8 個欄位（詳見需求 #327）
2. **Flyway V55**：`ALTER COLUMN ... TYPE TEXT` 把 4 個 VARCHAR 欄位擴成 TEXT，以容納 ciphertext 膨脹（~1.5× plaintext）
3. **Tests**：`EncryptionConverterTest` 新 2 個（100KB + UTF-8）、新 `PhiEncryptionIntegrationTest` 4 個（三 entity round-trip + 1 個 legacy-plaintext-fallback）

**為何選 TEXT 而非特定 VARCHAR 長度**：
- Ciphertext size 依 plaintext length 變動（base64 expansion ~1.33× + IV 12 bytes + GCM tag 16 bytes + `ENC:` prefix 4 bytes）
- PostgreSQL TEXT 無額外 overhead（同 VARCHAR 底層 storage）
- 未來 plaintext 成長不需再下 migration

**為何不做 bulk-encrypt backfill**：
- `EncryptionConverter.convertToEntityAttribute` 已有 legacy fallback：dbData 不含 `ENC:` prefix 時直接當明文回傳
- 既有 row 保持可讀、新寫入自動加密、漸進式 migrate 到 ciphertext
- Backfill SQL 留 Phase 2（需同時處理 audit log、backup encryption 等 ops 面）

**為何跳過 audit log + batch_import_job + failed_import**：
- Audit log 含 PHI 是 by design（forensic）；加密會影響稽核 UX，需先跟 compliance 確認 threat model
- `BatchImportJobEntity.patientIds` + `FailedImportEntity.patientFhirId` 是 operational metadata，曝露風險較低

**架構影響：**

- Application 層零變動（read/write 透明）
- FHIR browser (`/api/fhir/*`) 完全不受影響（HAPI proxy，不經 DB）
- Measure 報告 UI / CDS sandbox UI / PatientImport 歷史 UI 完全不受影響（經 JPA 自動解密）
- **唯一 operational impact**：運維直接下 `SELECT result_json FROM measure_report` 看到 `ENC:xxx`，需走 app 或解密 script

**關聯需求：** #327

**安全考量：**

1. **Key 管理**：`ENCRYPTION_KEY` 仍是 single master key。遺失 = 資料不可復原。Key rotation 機制留 Phase 3。
2. **Ciphertext inflation**：100KB result_json → ~150KB ciphertext。PostgreSQL TEXT 硬上限 1GB，不是問題。CPU 開銷估 5-10ms/100KB，對 measure evaluation 寫入 latency 影響可忽略。
3. **Repository search**：經 grep 確認無 `WHERE phi_field = ?` 查詢（只有 non-PHI 欄位如 `connectionId`、`ownerUsername`、`measureDefinitionId`）。若未來需要 PHI search，需做 deterministic encryption + blind index（Phase 4 問題）。
4. **Encrypt-on-read fallback 的副作用**：一個 row 被讀出、app 修改、存回去時會從 plaintext 升級為 ciphertext。但若 raw SQL UPDATE 寫入 plaintext 則 bypass converter。這是已接受的 design（converter 層保護，不是 DB constraint 層保護）。



---


### SDS-004 [設計] 共用 ExecutionErrorInfo + Classifier 架構

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#321](https://github.com/Lusnaker0730/CQL/issues/321) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |

**設計方案：**

三層分離：**模型** / **分類器** / **消費者**。

**模型（`model/debug/`）**：
- `ErrorPhase` enum — 8 個 phase 涵蓋 CDS（原 6 個）+ 新 `FHIR_RETRIEVAL`（eCQM 資料抓取失敗）+ 新 `POPULATION_EVALUATION`（eCQM 族群聚合失敗）。`.wireName()` = 小寫 enum name（= CDS 原 wire format）。
- `ExecutionErrorInfo` — 結構同原 `CdsResponse.CdsErrorInfo`（phase/errorType/message/stackTraceSummary），`@JsonInclude(NON_NULL)`。

**分類器（`util/ExecutionErrorClassifier`）**：純 static util，無狀態。
- `classify(Throwable)` — 走 cause chain 最多 10 層（BUG-115 fix），match translation class markers {Translation, Parse, Syntax, Compiler, Lexer} 或 message markers {"CQL translation failed", "translation error", "parse error"} → `CQL_TRANSLATION`；否則 `CQL_EXECUTION`。
- `buildErrorInfo(Throwable)` — auto-classify + build。
- `buildErrorInfo(ErrorPhase, Throwable)` — caller 已知 phase 時用（e.g. CDS `CARD_GENERATION`）。堆疊過濾 `com.cqlplatform.*`，上限 5 frames。
- `fromCdsPhase(CdsInvocationException.Phase)` — legacy enum 對映，exhaustive switch（新加 value compile 就爆）。

**消費者**：
1. **CDS**（`CdsInvocationService`）：`executeCql` catch → delegate to classifier，丟原 `CdsInvocationException` 保留。原本 inline 的 `looksLikeTranslationError` 刪除。`buildErrorInfo(Phase, Throwable)` 改 delegate。
2. **Editor**（`CqlExecutionResponse` + `GlobalExceptionHandler`）：`ErrorResponse` 加 `errorInfo` 欄位；`handleCqlExecutionException` 丟 exception 時 classify 塞 ErrorInfo 到 response body。`CqlExecutionResponse` 也留新 `errorInfo` 欄位給未來 200-with-error 模式（目前未用）。
3. **eCQM**（`MeasureEvaluationResult` + `MeasureEvaluationService`）：`errorResult` overload 可選傳 `Throwable`，有 throwable 就經 classifier 填 errorInfo。

**架構影響：**

- 新檔：`ErrorPhase.java` + `ExecutionErrorInfo.java` + `ExecutionErrorClassifier.java` + `ExecutionErrorClassifierTest.java`（4 files）
- 改檔：`CdsInvocationService.java`（delegate）、`CdsResponse.java`（刪 inner class + 換型別）、`CqlExecutionResponse.java`（+ field）、`MeasureEvaluationResult.java`（+ field）、`MeasureEvaluationService.java`（overload）、`GlobalExceptionHandler.java`（+ field + handler 填 errorInfo）（6 files）
- 零 DB migration、零前端變動、zero-break JSON（所有新欄位 optional + NON_NULL serialization）
- 前端 `DebugPanel` / `CdsDebugPanel` 共用化列為 follow-up PR（不在此 scope）

**關聯需求：** #320

**安全考量：**

1. **JSON wire compat**：CDS 的 `debug.error.phase`/`errorType`/`message`/`stackTraceSummary` 原本是 camelCase JSON。`ExecutionErrorInfo` 欄位命名一致，`CdsDebugInfo.error` 欄位名不變，所以 JSON wire 零變動。CDS client 讀不到差異。
2. **Editor 新 errorInfo 欄位**：既有 editor client 不 parse 這欄也不會炸（JSON 新欄位是 forward-compat）。
3. **Stack trace exposure**：維持 BUG-115 原限制（`com.cqlplatform.*` only + top 5 frames），不洩漏 third-party lib 細節或完整堆疊。
4. **Classifier 只跑 heuristic、不副作用**：純 function、無 logging、無 mutation → 純讀 Throwable 內容。從 hot path（每個 CQL execution 都會過 catch）角度看 O(depth) 很廉價。



---


### SDS-005 [設計] looksLikeTranslationError walk cause chain + message scan

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#316](https://github.com/Lusnaker0730/CQL/issues/316) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |

**設計方案：**

修 `CdsInvocationService.looksLikeTranslationError(Throwable)` 的 classify heuristic。

**改動 scope**：私有 static method，只影響同類別 `executeCql(...)` 中的 catch 分派。不動 `CdsInvocationException.Phase` enum、不動 `CdsErrorInfo` response schema、不動前端。介面 zero change。

**演算法**：
1. 從傳入 throwable 開始走 cause chain（`getCause()`）
2. 每層檢查兩件事：
   - class simple name 含 `Translation` / `Parse` / `Syntax` / `Compiler` / `Lexer` 任一 substring
   - message 含 `CQL translation failed` / `translation error` / `parse error` 任一子字串（case-sensitive，這些是系統 log 既定 wording）
3. 任一命中回 true、走 `Phase.CQL_TRANSLATION`
4. 深度上限 10（防止故意 cause loop、rocket science 的 deeply nested library 不現實）
5. 全鏈無命中 → false、走 `Phase.CQL_EXECUTION`

**架構影響：**

- 單檔變動（`CdsInvocationService.java` 的 `looksLikeTranslationError` method）
- 測試檔擴 2 tests（`CdsInvocationServiceTest`）
- Smoke scenario 18 的 expected phase 從 workaround 的 `cql_execution` 改回正確的 `cql_translation`
- 無 DB migration、無 API 介面變動、無前端變動

**關聯需求：** #315

**安全考量：**

1. **不要 over-classify**：`RuntimeException("boom")` 這種純運行時錯誤 heuristic 不會命中（class 不含關鍵字、message 不含關鍵字）→ 保持 `cql_execution`。既有 test `invoke_debugModeTrue_executionFails_shouldAttachErrorInfoWithPhase` regression-lock 此行為。
2. **Cause loop 防護**：走鏈時計數，最多 10 層。超過就停。這比走無上限安全（惡意構造循環 cause 可讓 walker 無限遞迴）。
3. **Case sensitivity of message scan**：設計上保持 case-sensitive，因為 log wording 是工程端既定模版。若未來翻譯文字改了此 heuristic 需同步，test 會第一個警告。
4. **message 掃 vs. class 掃順序**：class simple name 先掃（快），miss 再掃 message。不是為了效能，是讓 class 匹配優先（更 deterministic）。



---


### SDS-006 [設計] TLS via Cloudflare Origin Certificate + nginx 反向代理

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#309](https://github.com/Lusnaker0730/CQL/issues/309) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |

**設計方案：**

採用 Cloudflare Origin Certificate + VM nginx 反向代理 TLS 終止方案，不在 Spring Boot 啟用 `server.ssl`。

1. **Cert 來源**：Cloudflare dashboard 產生 Origin CA cert（15 年有效、scoped to Cloudflare↔origin only），安裝於 VM `/etc/ssl/cloudflare/twcql.com.pem` (644) 與 `.key` (600)。瀏覽器不信任此 CA；這沒問題——最終用戶永遠透過 CF，CF 自己信任這張 cert。
2. **Nginx 拓撲**：單一 server file 兩個 server block。`:80` 無條件 301 redirect 到 `https://$host$request_uri`（apex + www）；`:443` 綁 origin cert、只允 TLS 1.2/1.3、強 cipher list、發 HSTS 6 個月、proxy_pass 到 127.0.0.1:8888 的 docker frontend。
3. **Backend 不變**：Spring Boot 仍監聽 `:8080`、僅對 127.0.0.1 開放、不走 TLS（TLS 由 nginx 終止）。
4. **Config 管理**：canonical config 以 `docker/nginx-vm/twcql.com.conf` 存回 repo + README 記錄部署 scp 指令；避免 config drift 在 VM 上發生。

**架構影響：**

- 加 `docker/nginx-vm/` 目錄（2 個新檔）
- 既有 Docker compose 不變（nginx 是 VM 層非 container）
- Spring Boot 程式碼零變動
- Cloudflare SSL mode 需由使用者手動改 Flexible → Full (strict)

**關聯需求：** #308

**安全考量：**

1. **`X-Forwarded-Proto https` header injection**：nginx 硬寫 `https`，不回信 client 傳入的版本，避免 downstream 把 http 偽造成 https。
2. **HSTS rollback cost**：`max-age=15768000` (6 個月) + includeSubDomains。不加 `preload`（若需回退 http 會卡很久），留給成熟期再加。
3. **Cert renewal**：15 年 cert 代表本世代不用續期；續期時需手動 regenerate；若遺失 Cloudflare access，cert 無法撤銷（但危害僅限 Cloudflare 能偽造我們的 origin）。
4. **Legacy `http2 on;` directive**：nginx 1.24 不識別 1.25+ 語法，改用 legacy `listen 443 ssl http2;` 形式。



---


### SDS-007 [設計] measure_report V54 provenance columns + ContentHash + MeasureReportService lookup (#303)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#304](https://github.com/Lusnaker0730/CQL/issues/304) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |

**設計方案：**

**Schema 變動（Flyway V54）：**
- `measure_report` 加 3 nullable column：`measure_version VARCHAR(50)` / `cql_hash VARCHAR(64)` / `elm_hash VARCHAR(64)`
- Index `idx_measure_report_definition_version` on `(measure_definition_id, measure_version)`——支援 audit 熱路徑

**Provenance 計算（ContentHash）：**
- 新 util `ContentHash.sha256Hex(String)`
- null input → null output（distinguishable from empty string）
- UTF-8 encoding（CQL 可能含中文註解）
- JCA SHA-256 builtin、無第三方依賴

**Save 流程（MeasureReportService）：**
- Constructor 加 `MeasureDefinitionRepository` 依賴
- `saveReport` 在 build entity 前 `findById(measureDefinitionId)` fetch definition
- Present → 填 version + cqlHash + elmHash
- Absent / id null → 全 null（definition 可能 save 前被刪；report evaluation 結果仍 canonical，不因 lookup miss 而 abort）
- cqlContent / elmJson 為 null 時 hash 也為 null（不要誤填 empty-string SHA 當真 hash）

**架構影響：**

- 現有 save path 多一次 DB lookup（hot path，但通常只每分鐘幾次 evaluation，impact 可忽略）
- 既有 report rows 保持 null provenance（historic；前端 UI 展示時 fallback 顯示 "legacy"）
- 新 report 一律帶 provenance

**關聯需求：** （未指定）

**安全考量：**

（未填寫）



---


### SDS-008 [設計] LoginAttemptListener via Spring Security events + lockout_until in CustomUserDetailsService (#PAT-094)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#300](https://github.com/Lusnaker0730/CQL/issues/300) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |

**設計方案：**

兩個 extension point，符合 Spring Security 建議做法。關聯需求：#PAT-094

**(1) `CustomUserDetailsService.loadUserByUsername` 讀 lockout_until：**
```java
boolean accountNonLocked = user.getLockoutUntil() == null
        || user.getLockoutUntil().isBefore(LocalDateTime.now());
return new User(username, password, enabled, true, true, accountNonLocked, authorities);
```
Spring Security 的 `DaoAuthenticationProvider` 自動對 accountNonLocked=false 丟 `LockedException`——**password 比對完全不執行**，連 timing attack 都避免。

**(2) `LoginAttemptListener` 聽事件：**
- `AbstractAuthenticationFailureEvent` → 累加 counter；達 threshold 設 lockout_until
- `AuthenticationSuccessEvent` → clear counter + lockout_until（僅當需要時寫）

**架構影響：**

（未填寫）

**關聯需求：** Closes #PAT-094

**安全考量：**

（未填寫）



---


### SDS-009 [設計] CqlTranslationService.hintForError + HookTypeValidator deprecated-alias map (#292)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#294](https://github.com/Lusnaker0730/CQL/issues/294) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |

**設計方案：**

兩處獨立小修改，純改訊息、不改拒絕行為。關聯需求：#292

**CqlTranslationService：**
- 新 static `hintForError(String errorMessage)`：narrow pattern-match，只認識「真的見過使用者踩到的 pattern」。匹配到回 hint string、否則回 null。
- `mapTranslatorException` 把 hint append 到 `CqlError.message` via `"\n\nHint: " + hint` pattern，保留原訊息完整性（除錯時仍能看到 translator 原話）。
- 目前蓋 2 個 pattern：`FHIR.CodeableConcept × System.String`（常見 — CDS scenario 11 踩到）、`FHIR.Coding × System.String`（類似）。

**HookTypeValidator：**
- 新 `DEPRECATED_HOOKS` Map<String, String>，currently 只有 `medication-prescribe → order-sign`（CDS Hooks 1.0 release notes 明列的 migration）
- `validate(hook)` 先查 VALID_HOOKS，沒命中時查 DEPRECATED_HOOKS，命中則拋更 helpful 訊息「Hook type 'X' was deprecated in CDS Hooks 1.0 — use 'Y' instead.」
- 完全未知的 hook 走原有 generic message（不瞎猜替代）

**架構影響：**

- 零 API/行為變動 — callers 對 rejection 的處理不需改
- Error message 多了 `\n\nHint: ...` 尾巴（對 UI 直接 render 有益、對 log 略長但可讀）
- DEPRECATED_HOOKS map 是 static final 常數，不隨 HookContextRequirements 變動 — 新增 deprecation 需手動加

**關聯需求：** Closes #292

**安全考量：**

- Hint 内容都不含 user-supplied 資料，純 static 建議文字 — 無 XSS / injection 風險
- Hint 避免誤導：只對極具體 pattern 給建議；其他訊息保留原樣 — 不造成「看到 hint 就覆蓋判斷」的誤導
- `DEPRECATED_HOOKS` 條目必須**真的有 migration path**（由 CDS Hooks spec release notes 背書），不隨意加 — 避免指向已廢棄但無替代品的 hook



---


### SDS-010 [設計] aggregateMethod save-time validation in EcqmExpressionTreeValidator (#283)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#286](https://github.com/Lusnaker0730/CQL/issues/286) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |

**設計方案：**

在 `EcqmExpressionTreeValidator.validate` 加 `validateAggregateMethods` 子檢查；iterate `populationGroups[*].observations[*].aggregateMethod`；透過 `MeasureScoreCalculator.normalizeAggregateMethod` 判斷（#PAT-088 已建立的 helper）。Unknown → 加 error；null/blank → 接受。關聯需求：#283

**架構影響：**

- 單一 validator 方法新增；validate() 呼叫鏈多一個 check
- 跟 PAT-088 evaluation-layer 的 null handling 形成兩層 defense in depth：save-time 擋 99% typo、evaluation 處理漏網的 legacy / 直接 DB write
- 既有合法 measure 不受影響（aliases 在兩處都接受）

**關聯需求：** Closes #283

**安全考量：**

- Save-time 拒絕比 evaluation-time null 對 author 更明確：400 + 完整路徑 + 支援 methods 列表
- DB 不再累積 typo'd measure
- 維持 null/blank acceptance 避免 break backward-compatible 「no aggregateMethod specified」使用場景



---


### SDS-011 [設計] normalizeAggregateMethod + null-on-unknown 取代 silent fall-through (#278)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#279](https://github.com/Lusnaker0730/CQL/issues/279) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |

**設計方案：**

單一 `MeasureScoreCalculator` 改動，不影響 caller 簽章。關聯需求：#278

**新 static helper `normalizeAggregateMethod(String input)`：**

```java
public static String normalizeAggregateMethod(String input) {
    if (input == null || input.isBlank()) return "average";  // preserve prior default
    String s = input.trim().toLowerCase();
    String canonical = switch (s) {
        case "min" -> "minimum";
        case "max" -> "maximum";
        case "avg", "mean" -> "average";
        default -> s;  // pass-through for canonicals; filter via set membership below
    };
    return CANONICAL_AGGREGATE_METHODS.contains(canonical) ? canonical : null;
}
```

Canonicals set: `{count, sum, average, median, minimum, maximum}`（就是 backend 本來支援的 6 種）。

**`calculateContinuousVariableScore` 使用 normalizer：**

```java
String method = normalizeAggregateMethod(aggregateMethod);
if (method == null) {
    log.warn("Unknown aggregate method '{}' — returning null score. " +
             "Supported methods: count, sum, average, median, minimum, maximum " +
             "(aliases: avg, mean, min, max).", aggregateMethod);
    return null;
}
return switch (method) { ... };
```

**`computeObservationStats` 的 display 欄位：**

```java
String canonical = normalizeAggregateMethod(aggregateMethod);
String displayMethod = canonical != null ? canonical
    : (aggregateMethod != null ? aggregateMethod.toLowerCase() : "average");
```

即便 input 無法正規化，display 欄位 fall back 到原始 lowercase（維持舊行為 for observability）。aggregateValue 仍透過 calculateContinuousVariableScore 拿（即 null）。

**架構影響：**

- **API 簽章不變**：caller 仍呼叫 `calculateContinuousVariableScore(values, methodString)`
- **行為變動**：
    - `"Min"` / `"Max"` / `"Avg"` 現在正確 map 到各自的 canonical（以前 silently → average）
    - 未知 method 現在回 null（以前 silently → average）
    - null / 空字串 不變（仍 average）
- **Response shape**：`observationStatistics.aggregateMethod` 欄位現在是 canonical form（`"minimum"` 而非 `"min"` 或 `"Min"`），consumer 看到一致詞彙
- **Logging**：unknown inputs 觸發 `log.warn`，有 debugging 痕跡

**關聯需求：** Closes #278

**安全考量：**

- 對 safety-critical Min/Max 指標（最長住院日、最高血壓、最差 HbA1c）的修正是 positive-correctness（先前 silently 平均化、現在正確取極值）
- Null-on-unknown 比 silent average 對臨床更安全：null = visible gap = 作者調查、average = invisible wrong-answer = 決策錯誤
- Logging 提供 audit trail 給 TFDA 合規稽核



---


### SDS-012 [設計] extractObservationValues: boolean TRUE → 1.0, FALSE → skip (#269)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#270](https://github.com/Lusnaker0730/CQL/issues/270) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |

**設計方案：**

單點修改 `extractObservationValues(results, expressionName)`。關聯需求：#269

**加 Boolean 分支（放在 Number 與 Iterable 之間）：**

```java
if (value instanceof Boolean bool) {
    return bool ? List.of(1.0) : List.of();
}
```

**Iterable 分支同步加上 Boolean 處理：**

```java
for (Object item : iterable) {
    if (item instanceof Number num) { values.add(num.doubleValue()); }
    else if (item instanceof Boolean bool && bool) { values.add(1.0); }
}
```

**語意解釋**：

- Boolean `TRUE` → 1.0 — 該 observation 有觀察到，貢獻一個「觀察實例」給 aggregate
- Boolean `FALSE` → skip（不加入 list）— 該 observation 未觀察到，不算一次；相當於 MP 內病人但 observation 條件沒滿足
- 其他非 Number 非 Boolean → skip（保持現行行為）

**為什麼選這個設計：**

- **Count aggregate 變得正確**：values.size() 就是 "count of observations observed"
- **Sum 退化為 Count**：對 boolean-only 輸入 Sum = 1.0 × count = Count；語意一致
- **Average 退化為 1.0**：boolean-only 輸入 Average always = 1.0；意義低但 non-null、不 crash
- **Min/Max 退化為 1.0**：同樣 non-null 且語意可接受
- **Median 退化為 1.0**：同上
- **對 Numeric observations 零影響**：regression-safe

替代方案 (拒絕)：

- **Boolean FALSE → 0.0** instead of skip：會讓 Sum 仍是 Count（0 不影響）但 Average 會變 (count_true / count_mp)，語意更複雜；且 MP 外病人已經不進 aggregation。統一用 skip 更乾淨
- **新加 `extractObservationCounts` 專為 Count**：API surface 膨脹、呼叫端要 know aggregate method 才能選對 extractor
- **aggregation 層改寫 Count 為「MP 內病人數」**：遺失「observation expression 可過濾 MP 病人」的能力，不合 CV 原意

**架構影響：**

- **變動範圍**：`PopulationEvaluator.extractObservationValues` 一個 method
- **Behavior change**：先前回 null score 的 CV measures 現在回正確計數——**修正非 regression**
- **Wire shape 不變**：MeasureReport response 結構相同，只是 `measureScore` 從 null → number
- **Dependent code**：`MeasureEvaluationService.buildCvResult`、`buildResult` 的 ratio-with-obs 分支、score calculator 的 Count aggregate—全都繼承新行為，無需改動

**關聯需求：** Closes #269

**安全考量：**

- **Proportion / cohort / ratio-without-obs 不受影響**：這些路徑不呼叫 extractObservationValues
- **Ratio-with-obs**：若有 ratio measure 用 boolean observation，會從 null score 變 counted rate — **修正非 regression**
- **Regression lock**：`PopulationEvaluatorTest` 加 4 tests 覆蓋 boolean TRUE / FALSE / null / numeric （numeric 確保 regression-safe）
- **Integration smoke**：PAT-082 scenario 03 用真棧驗證 score=3.0



---


### SDS-013 [設計] aggregateRatioPatientResults + buildRatioEntries — Numer gated by IP, not Denom (#264)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#265](https://github.com/Lusnaker0730/CQL/issues/265) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |

**設計方案：**

切離 ratio 與 proportion 的 evaluator 路徑。比照 CV（已有獨立 `aggregateCvPatientResults` / `buildCvEntries`）、cohort（#PAT-083 新加 `buildCohortResult`）的設計：每個 scoring type 一個專屬 per-patient aggregator。關聯需求：#264

**PopulationEvaluator 改動：**

1. `aggregateRatioPatientResults(counts, results)` 新 method：
   - IP gate：`inInitPop = isPopulationTrue(results, "Initial Population")`
   - Denom gate：`inDenom = inInitPop && isPopulationTrue(results, "Denominator")`
   - Denom Excl gate：`denomExcluded = inDenom && isPopulationTrue(results, "Denominator Exclusions")`
   - **Numer gate：`inNumer = inInitPop && isPopulationTrue(results, "Numerator")`**（key difference — gated by IP only）
   - Numer Excl gate：`numerExcluded = inNumer && isPopulationTrue(results, "Numerator Exclusions")`
   - **無 Denom Exception** 處理（FHIR proportion-only concept，ratio map 裡即便有也忽略）

2. `buildRatioEntries(results)` 新 trace method：比照 `buildProportionEntries` 但 Numer/Numer-Excl 的 gating 從 `effectiveDenom` 換成 `ip`；trace reason codes 改為 `numer_gatedByIpFalse` / `numer_exprFalse` / `numer_true`

3. `buildGroupTrace` dispatch：原 `case "proportion", "ratio" -> buildProportionEntries` 拆成 `case "proportion" -> buildProportionEntries; case "ratio" -> buildRatioEntries`

**MeasureEvaluationService 改動：**

4. `executeAndAggregate` 加 `isRatio` 旗標，在 aggregation loop 內：
   ```java
   if (isCv) { aggregateCvPatientResults(...); }
   else if (isRatio) { aggregateRatioPatientResults(...); /* + observation collection */ }
   else { aggregatePatientResults(...); /* proportion */ }
   ```

5. Score 計算：原本 fallback 統一走 `calculateProportionScore`。改為 `scoreCalculator.calculateScore(scoringType, denom, excl, numer)` dispatcher：ratio → `calculateRatioScore`（不扣 denom-exclusion，允許 >100%）；proportion → `calculateProportionScore`。`calculateScore` 本已有 ratio / cohort / CV 分派，只是先前 callers 不用它。

**架構影響：**

- **Behavior 改動**：ratio measures Numer 計數可能**改變**。舊行為下 Numer 永遠 ≤ Denom；新行為下 Numer 可 > Denom，score 可 > 100%。下游 reporting 需理解這是正確修正（先前低估）而非 regression。
- **Wire shape 不變**：MeasureReport response payload 結構不變；只是數字變正確。
- **Test coverage**：既有 proportion tests 不受影響（route 不變）；新 `PopulationEvaluatorTest` 8 個 tests 鎖住雙路徑差異。

**關聯需求：** Closes #264

**安全考量：**

- **降低誤判風險**：ratio measures 先前全面低估事件數（Numer 被擷取到 Denom 子集），臨床品質決策基於低估數字可能導致「看不到問題」。修正後報告反映真實情況。
- **不影響 proportion**：設計明確 route by scoringType，proportion 路徑完全不變；ratio 路徑的所有改動都在新 method 裡，回退只需把 `if (isRatio)` 分支移除
- **Trace 一致性**：debug mode 下 ratio trace 顯示 "numer_gatedByIpFalse" 而非 "numer_gatedByDenomFalse"，使用者看到的 reason code 反映實際計算邏輯，避免誤解



---


### SDS-014 [設計] Cohort 專屬 buildCohortResult + calculateCohortScore — 切離 proportion/ratio 路徑 (#259)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#260](https://github.com/Lusnaker0730/CQL/issues/260) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |

**設計方案：**

兩層變動：(1) `MeasureScoreCalculator` 加 IP-input cohort helper，(2) `MeasureEvaluationService` 為 cohort 新增獨立 branch，比照既有的 CV branch。關聯需求：#259

**MeasureScoreCalculator 改動：**

1. 新 `calculateCohortScore(Integer ip)` public method：
   - IP == null → return null（無法計算）
   - IP == 0 → return 0.0（有效結果，不 null）
   - IP > 0 → return ip.doubleValue()

2. 現有 dispatcher `calculateScore(scoringType, denom, excl, numer)`：cohort case 顯式回 null 並加註解說明 contract（該 signature 沒有 IP，呼叫端必須 dispatch 到專屬 helper）。避免未來有人誤改成「cohort 走 calculateProportionScore(denom=0) → 回 null」— 那種錯誤路徑會讓 cohort 看起來「剛好修好」但其實走錯分支。

**MeasureEvaluationService 改動：**

比照既有的 `buildCvResult` 模式，新增 `buildCohortResult(context, state, totalPatients)`：
- populations list 只含 `populationResult(INITIAL_POPULATION, ip)` 一筆
- measureScore = `scoreCalculator.calculateCohortScore(ip)`
- measureScoreUnit = `"count"`（對 proportion 是 "percentage"，對 ratio 是 rate unit — cohort 語意上是 count）
- 不含 denominator/numerator rows

`buildResult` 分派：在既有 CV 分派之後、proportion/ratio 共用程式之前，加 cohort 分派：
```java
if (ScoringTypeConstants.CONTINUOUS_VARIABLE.equals(scoringType)) return buildCvResult(...);
if (ScoringTypeConstants.COHORT.equals(scoringType)) return buildCohortResult(...);
// ... proportion / ratio 繼續走共用 code
```

**架構影響：**

- **Wire shape 改動**：cohort measure 的 response payload 不再含 `denominator: 0` / `numerator: 0` rows。舊 consumer 若硬依賴這些 placeholder 要調整——但 FHIR spec 本來就沒規定必填 placeholder，預期看到 0 值本身就是 bug。前端 dashboard 已正確處理 null populations，所以實務影響趨近零。
- **Behavior 改動**：cohort measure 的 `measureScore` 從 null → 實際 IP count。下游 reporting / 儀表板的分數欄位從空白變成數字，這是修正而非 regression。
- **Backward compat**：測試多組既有 measure report 的行為：proportion / ratio / CV 路徑完全不變（分派邏輯僅在 `scoringType == "cohort"` 時才進入新 branch）。

**關聯需求：** Closes #259

**安全考量：**

- **FHIR compliance**：與 MeasureReport spec 對齊，避免把不合 spec 的 payload 送給 external consumer（EHR 匯出、CMS 報告、研究協作）
- **語意正確性**：cohort 的 score 現在語意正確，儀表板「0 位符合 cohort 的病人」vs「尚未計算」兩種狀態不再混淆（先前 0.0 vs null 都顯示空白）
- **Regression lock**：`MeasureScoreCalculatorTest` 新增 7 個測試鎖住 dispatcher 與 cohort helper 的 contract；`calculateScore(COHORT, ...)` 回 null 的 test 特別鎖住「cohort 路徑必須走專屬 helper」這個設計選擇，避免未來 refactor 誤 route



---


### SDS-015 [設計] AgeRange: BuildContext.hasMeasurementPeriod flag + period-bound age function mapping (#252)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#253](https://github.com/Lusnaker0730/CQL/issues/253) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |

**設計方案：**

兩層變動：(1) context 裡加一個 flag，(2) generator 依 flag 切輸出形式。關聯需求：#252

**資料模型（BuildContext）：**

新增 public boolean field `hasMeasurementPeriod`（預設 false）。設計選擇：

- 用 **field** 不用 enum 或 `RenderMode` 延伸：這是正交於 RenderMode 的軸（eCQM 可能是任一 RenderMode；CDS 永遠是 STANDARD 但 hasMeasurementPeriod=false）
- 用 **public mutable field** 不用 setter/scoped helper（對照 #239 的 `withRenderMode`）：MP 的存在跟 library boundary 綁定、不是 per-subtree；只在 buildEcqmCql 入口 set 一次即可，沒有 nested scope restoration 需求

**CQL 產生（ExpressionCqlEngine）：**

- `mapUnitToAgeFunction(unit, bindToMeasurementPeriod)`：flag=true 時回傳 `"AgeIn{Unit}At(end of \"Measurement Period\")"`；false 時回傳 `"AgeIn{Unit}()"`。支援的 units: year/month/week/day/hour，unknown 退回 year
- `buildAgeRangeExpression(fields, ctx)`：讀 `ctx.hasMeasurementPeriod` 決定呼叫哪一版
- **舊 1-arg overload 保留但加 `@Deprecated(forRemoval=true)`**：避免破壞現有 callers；新行為只有新 signature 有。過渡期結束後刪除（參考 #5 DB library guard 的 deprecation pattern）

**設定點（EcqmCqlBuilder）：**

```java
BuildContext ctx = new BuildContext(baseElements, parameters);
ctx.hasMeasurementPeriod = true;  // ← here, before any buildExpression call
```

單點設定、單點失敗、容易 review。`CqlArtifactBuilder`（CDS 路徑）不做改動，繼承 default false。

**架構影響：**

- **Public API 破壞**：0 — `buildAgeRangeExpression(fields)`, `mapUnitToAgeFunction(unit)` 仍可呼叫，只是 Deprecated warning
- **Wire shape 改變**：0 — 只改 CQL 輸出文字，artifact JSON / ELM 結構不變
- **儲存相容性**：已儲存的 eCQM artifact 下次 save-and-publish 時會重新產 CQL 帶新函式；舊的 measure reports 不受影響
- **Performance**：0 — 單一 string concat 差異

**關聯需求：** Closes #252

**安全考量：**

- **語意正確性**：新輸出的 CQL 對同一份輸入資料在任何執行時間都產生同一結果（reproducibility）——這是 eCQM 驗證、稽核、合規報表的必要條件
- **退場機制 & rollback**：若新輸出在某些 edge case 產生問題，可把 `EcqmCqlBuilder` 那行 `ctx.hasMeasurementPeriod = true` 改回 false 即 revert（或移除該行）
- **CDS 路徑保護**：`CqlArtifactBuilder` 產出的 library 沒有 `"Measurement Period"` parameter 宣告，如果誤 bind 到它會爆 translator error。此設計透過「flag 預設 false」+「只有 EcqmCqlBuilder set true」確保 CDS 路徑絕對不會 emit 該 reference
- **Deprecated path 安全**：舊 1-arg overload 繼續走 non-binding 版本，跟現狀一致，不會引入新行為



---


### SDS-016 [設計] LibraryManagerFactory 的 options 工廠一律內建 SignatureLevel.Overloads (#243)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#244](https://github.com/Lusnaker0730/CQL/issues/244) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |

**設計方案：**

在 `LibraryManagerFactory` 的兩個 `CqlCompilerOptions` 工廠方法都固定套用 `SignatureLevel.Overloads`。這是 **structural** 的修法——不讓任何 caller 有機會忘記傳、也不暴露給外部控制——與 #230（結構化 fieldSpec 取代 CQL 字串）同樣採「讓錯誤形狀在構造上不可能」的設計原則。關聯需求：#243

**兩個修改點：**

1. `defaultOptions()` — 既有 `new CqlCompilerOptions(EnableLocators, EnableAnnotations, EnableResultTypes)` 鏈式加 `.withSignatureLevel(LibraryBuilder.SignatureLevel.Overloads)`
2. `buildOptions(boolean, boolean, boolean, boolean)` — 可選 options 工廠，同樣在 return 前呼叫 `.withSignatureLevel(LibraryBuilder.SignatureLevel.Overloads)`

**為什麼選 `Overloads` 而非 `All`：**

| Level | 行為 | 取捨 |
|-------|------|------|
| `None` | 不嵌（現況） | Runtime dispatch 歧義 |
| `Differing` | 只對 translator 判定「signature differing」的嵌 | 判定規則 subtle，漏抓風險高 |
| `Overloads` | **任何有 >1 overload 的 function 都嵌** | ✅ 精準消除歧義、ELM 體積增加最小 |
| `All` | 全部 function 都嵌 | ELM 肥、但對單 overload function 毫無效益 |

`Overloads` 是同時滿足「消除歧義」與「ELM 大小可接受」的最窄設定。

**架構影響：**

- 影響路徑：所有 `CqlTranslator.fromText(cql, libraryManager)` 的呼叫位置——`CqlExecutionService`（3 個）、`CqlTranslationService`（2 個），共 5 處。這些 caller 不需改動，因為 options 是從 `LibraryManager` 取得。
- ELM 體積變化：每個多 overload function call 多 ~20 bytes 的 signature metadata。整體 artifact 約增 1-3%，可忽略。
- Backward compatibility：現有儲存的 artifact tree 不受影響——下次 save / translate 時自動用新 options。已快取的 ELM 會過期並重新生成。
- Performance：翻譯期相同（translator 本來就 resolve overload，只是決定要不要寫入 ELM）。Engine 執行期略快（不需做 late dispatch）。

**關聯需求：** Closes #243

**安全考量：**

- **消除 runtime dispatch ambiguity**：已知 `FHIRHelpers.ToString` / `ToDateTime` / `ToInterval` / `ToDate` / `ToCode` / `ToConcept` 均有 >1 overload。此修法讓 engine **編譯期就決定走哪條**，杜絕 argument-null / base-type 的 late-dispatch failure mode。
- **不改變語意**：SignatureLevel 只影響 ELM 內嵌 metadata，不改變表達式產生邏輯，golden suite 可證明行為等價。
- **Regression lock**：新增 `LibraryManagerFactoryTest` 在單元測試層鎖住「signatureLevel ∈ {Overloads, All}」的不變式。如果將來有人改動 options 工廠不慎把 SignatureLevel 弄回 None，CI 會直接擋下。



---


### SDS-017 [設計] modifier 結構化 fieldSpec — 以 typed block 取代 CQL 字串 (#229)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#240](https://github.com/Lusnaker0730/CQL/issues/240) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |

**設計方案：**

重構 `ModifierDefinition` 與 `modifiers.json`，把 `DuringMeasurementPeriod` 這類 subtype-only 欄位抽進 typed nested block，並以結構化 `DateFieldSpec` 取代原本以逃脫字串塞在 JSON 裡的 whereClause。關聯需求：#229

**資料模型變更**：

- `ModifierDefinition` 移除 `resourceAlias`、`whereClause`（subtype 污染）
- 新增 `DuringMeasurementPeriodConfig`（`alias` + `dateFieldSpec`）
- 新增 `DateFieldSpec`（`field` + `types: List<String>` + 可選 `fallbacks: List<FieldRef>`）
- 新增 `FieldRef`（`resource` + `field`），作為選擇性 fallback 表達（例：`Condition.recordedDate`）

**JSON 結構變更**：6 個 `DuringMeasurementPeriod` entry 的 `resourceAlias` / `whereClause` 收斂進 `during` 物件，whereClause 的 case 表達式 boilerplate 完全消失（約縮短 30%），JSON 只描述 spec。

**CQL 產生**：`ExpressionCqlEngine` 新增 `buildDuringMeasurementPeriodWhereClause(alias, DateFieldSpec)` 與 helper（`buildPeriodCheck` / `buildTypeComparison`）。每個 `FHIRHelpers.ToInterval(x)` / `FHIRHelpers.ToDateTime(x)` 呼叫一律 gated 在 `when X is FHIR.T then` 判斷之後——**dispatch-ambiguity 在構造上不可能出現**。

Dispatch 一律透過 `def.getDuring()` 解析 alias + spec，而不再信任 raw string。

**架構影響：**

- 後端：
    - `ModifierDefinition` DTO 移除 2 個欄位、新增 1 個 nested config 欄位
    - `ExpressionCqlEngine` 新增 whereClause generator 與兩個 helper；既有 BaseModifier flow 不受影響
    - `ModifierService` 讀取 `modifiers.json` 的 schema 變更由 Jackson 自動吸收（欄位非必填）
- 前端：無需改動——artifact tree 只引用 modifier `id`，subtype fields 不 round-trip 到前端
- DB：無 schema 變更
- Artifact 儲存：不受影響（舊 artifact 下次 save 重新產 CQL 時走新 generator，golden suite 證明行為等價）

**關聯需求：** Closes #229

**安全考量：**

- whereClause 不再以字串形式存在於 catalog——移除了「JSON 裡的 CQL 在 review / lint / translator 階段都不會被檢查」的風險面
- Generator 的輸出空間被 spec 形狀限制，只能產出 null-safe case 表達式，產不出 BUG-110（`FHIRHelpers.ToInterval(null)` ambiguous）與 BUG-112（`and` 未 short-circuit 導致 null type check）那類 shape
- FHIR choice type（Observation.effective / Procedure.performed）以 `types: [\"Period\", \"dateTime\", \"instant\"]` 明確宣告，每種 type 都各自 gated，避免 SqlSelectionResolver 在 null 時於多個 overload 間歧義
- 向後相容：未用到 DuringMeasurementPeriod 的 artifact 產出 CQL 完全不變；有用到的則產出 semantically equivalent CQL（golden suite 鎖住）



---


### SDS-018 [設計] DuringMeasurementPeriod modifier 實作方案 — #212

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#213](https://github.com/Lusnaker0730/CQL/issues/213) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |

**設計方案：**

新增 `DuringMeasurementPeriod` modifier 類型，解決 authoring UI 無法讓 population criteria 依 Measurement Period 過濾的問題。關聯需求：#212

**資料模型**：`ModifierDefinition` 新增兩個欄位：
- `resourceAlias` — CQL 查詢別名（O/C/E/P/M/MS）
- `whereClause` — 預先組好的 where 子句字串（包含別名）

**Catalog**（`modifiers.json`）為 6 種 resource type 各加一筆 modifier 定義，共用 `cqlTemplate: "DuringMeasurementPeriod"`，每筆帶各自的 alias + whereClause。

**CQL 產生**（`ExpressionCqlEngine`）：新增 `case "DuringMeasurementPeriod"`，透過 `ModifierService.getById(modId)` 解析 alias/whereClause（這兩個欄位不在前端儲存的 artifact tree 中，只存 id），渲染 `DuringMeasurementPeriod.ftl` 模板輸出 `(${expression}) ${alias} where ${whereClause}` 形式的 CQL query。

**FHIR choice type 處理**：Observation.effective / Procedure.performed 可能為 Period 或 dateTime。whereClause 使用 `(X.effective as Period) overlaps "Measurement Period" or (X.effective as dateTime) during "Measurement Period"` 同時涵蓋兩種情形，null coercion 由 CQL 三值邏輯處理。

**架構影響：**

- 前端：無需改動 — 現有 `ModifierCard` 的 no-values 分支直接顯示 `modifier.name`
- 後端：ExpressionCqlEngine 新增 ModifierService 依賴（constructor injection）；6 個既有 test 建構子新增 `null` 參數
- DB：無 schema 變更

**關聯需求：** Closes #212

**安全考量：**

- whereClause 源自後端 catalog（非使用者輸入），無 CQL injection 風險
- 未知的 DuringMeasurementPeriod id（catalog 不認識）時退回原 expression 並記錄 warning，避免產出不完整 CQL
- 向後相容：既有未使用此 modifier 的 artifact 產出的 CQL 完全不變



---


### SDS-019 [設計] ComparableR4FhirModelResolver enum/primitive 型別正規化以消除 CodeType dispatch 歧義 (BUG-106)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#207](https://github.com/Lusnaker0730/CQL/issues/207) |
| 建立日期 | 2026-04-17 |
| 狀態 | open |

**設計方案：**

擴充 `ComparableR4FhirModelResolver`（繼承 cql-engine 的 `R4FhirModelResolver`）攔截 primitive 欄位存取：

```java
@Override
public Object resolvePath(Object target, String path) {
    // 既有 Encounter.class 處理保留
    if (\"class\".equals(path) && target instanceof Encounter e) {
        return e.getClass_();
    }
    Object resolved = super.resolvePath(target, path);
    // 若 HAPI 回傳的是泛型 CodeType（但 reflect 得到的宣告型別是特定 Enumeration<X>），
    // 透過欄位宣告找回具體 enum 並重包為 Enumeration<X>，讓 CQL engine 的 dispatcher
    // 能匹配 FHIRHelpers.ToString(SpecificEnum) 而不是 ToString(code)。
    return normalizeEnumType(target, path, resolved);
}
```

`normalizeEnumType` 策略：
1. 用 target class + path 名查 HAPI 的欄位宣告（`target.getClass().getDeclaredField(path + \"Element\")` 或 `getStatusElement()`）
2. 從欄位 generic type 取出 `Enumeration<T>` 的具體 T
3. 若 resolved 是 raw `CodeType`，`new Enumeration<T>(factory)` 重包並複製 value

另一選項：在 `PrefetchRetrieveProvider.getResources()` 回傳前遞迴掃描 resource 樹，對每個 primitive 欄位呼叫對應 typed setter，觸發 HAPI 內部型別恢復。

**架構影響：**

- `ComparableR4FhirModelResolver.resolvePath` 是 CQL 執行的熱路徑，每次欄位存取都會進，需避免反射開銷（cache Field 查找）
- 影響**所有 CQL 執行**：eCQM、CDS Hooks、quality measures、test case 模擬——必須跑完整 library 回歸（QICoreCommon、MATGlobalCommonFunctions、FHIRHelpers）
- `DateTimeType is not comparable` 是同源問題但走不同路徑（comparison operator 而非 ToString dispatch），同一個 normalize 邏輯需覆蓋 `DateTimeType` → `System.DateTime` 轉換（或確保 `ToDateTime(dateTime)` 可正確呼叫）

**關聯需求：** - 關聯風險：#206（BUG-106 風險分析）
- 先前 workaround：PAT-066 commit 7a9821e

**安全考量：**

修改 ModelResolver 是 CQL 執行語意的核心，若正規化邏輯誤判會讓 production quality measures 失準。緩解：
- 只對「宣告為 enum 但 runtime 是 raw CodeType」的情境 normalize
- 其他情境（已是 typed Enumeration、非 primitive、Extension）一律走 super.resolvePath
- 加 feature flag `cql.engine.normalize-enum-types`（預設 true，可緊急關閉回到舊行為）


**介面描述：**

對 CQL 作者**完全透明**——修正後 `where M.status = 'active'`、`sort by recordedDate desc` 等自然 CQL idiom 直接運作，無需 `.value` workaround。TWCDI 範本可反向清理（另行追蹤）。


---


### SDS-020 [設計] CQL 引擎可靠性與效能修復設計方案 (#BUG-015)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#166](https://github.com/Lusnaker0730/CQL/issues/166) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |

**設計方案：**

1. **翻譯錯誤回報**：CqlExecutionService 翻譯後檢查 translator.getExceptions()，Error 等級拋出 CqlExecutionException
2. **Class Loader 修復**：LibraryManagerFactory 在 Spring @PostConstruct 建立共用 ModelManager，避免 worker 執行緒 ServiceLoader 失敗
3. **Population 階層**：PopulationEvaluator 強制 IP ⊇ Denom ⊇ Numer 從屬關係
4. **預翻譯 CQL**：MeasureEvaluationService 在病人迴圈前翻譯一次 CQL→ELM，所有病人共用

**架構影響：**

僅修改 service 層邏輯，不影響 API 介面或資料庫 schema

**關聯需求：** #150

**安全考量：**

修正 population 計算邏輯確保品質量測分數正確（0-100%），避免誤導臨床決策



---


### SDS-021 [設計] FHIR 操作稽核日誌強化技術方案

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#146](https://github.com/Lusnaker0730/CQL/issues/146) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |

**設計方案：**

1. **AuditLogEntity 擴充**：新增 connection_id、patient_fhir_id、connection_name 欄位
2. **AuditFilter 增強**：解析 EHR 路徑模式，提取連線 ID 和病人 ID
   - `/api/ehr/connections/{connId}/patients/{patientId}/*` → 標記 PHI access
   - `/api/ehr/connections/{connId}/*` → 記錄連線 ID
3. **AuditLogSearchRequest 擴充**：新增 connectionId、patientFhirId、phiAccess 篩選
4. **AuditController 新端點**：GET /ehr-operations、GET /retention、POST /cleanup
5. **CSV 匯出擴充**：包含 EHR 欄位

**架構影響：**

- 既有 AuditFilter/AuditService/AuditController 擴充，不新增 Service
- V45 migration 新增 3 個欄位 + 3 個索引

**關聯需求：** #136

**安全考量：**

- 稽核日誌不可竄改（INSERT only，無 UPDATE）
- 清理功能僅 ADMIN 角色可執行
- CSV 匯出需認證


**介面描述：**

新增 3 個 REST 端點於 /api/admin/audit/ 路徑下


---


### SDS-022 [設計] TLS/mTLS 安全通訊技術方案

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#143](https://github.com/Lusnaker0730/CQL/issues/143) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |

**設計方案：**

1. **TlsContextFactory**：新增 `@Component`，負責從 PEM 憑證建立 `SSLContext`
   - 支援自訂 CA Trust Store（多憑證串接）
   - 支援 mTLS Client Certificate + Private Key（PKCS#8 格式，RSA/EC）
   - 支援停用 hostname verification（僅限測試環境，記錄警告日誌）

2. **FhirClientFactory**：注入 TlsContextFactory
   - TLS 連線使用獨立 `FhirContext` 避免全域副作用
   - 非 TLS 連線行為不變（向下相容）

3. **EhrConnectionEntity**：新增 6 個欄位（tlsEnabled, caCertPem, clientCertPem, clientKeyPem, tlsMinVersion, hostnameVerification）
   - PEM 欄位使用 `EncryptionConverter` 加密儲存
   - JSON 回應中隱藏憑證欄位（`@JsonProperty(WRITE_ONLY)`）

**架構影響：**

- 新增一個 Service 類別（TlsContextFactory），不影響現有架構
- FhirClientFactory 新增一個建構子參數
- V45 Flyway migration 新增欄位

**關聯需求：** #135

**安全考量：**

- 憑證以 AES-256-GCM 加密儲存
- 私鑰不透過 API 回傳
- 停用 hostname verification 時記錄 WARN 日誌
- TLS 最低版本預設 TLSv1.2


**介面描述：**

EhrConnectionRequest 新增 TLS 欄位，REST API 不變


---


### SDS-023 [設計] CVE-2026-33180 修復方案 — dependencyManagement 覆蓋

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#120](https://github.com/Lusnaker0730/CQL/issues/120) |
| 建立日期 | 2026-03-24 |
| 狀態 | open |

**設計方案：**

在 `backend/pom.xml` 新增 `<dependencyManagement>` 區段，覆蓋 HAPI FHIR 的 transitive dependency `org.hl7.fhir.convertors` 版本從 6.7.9 升級到 6.9.0。同時覆蓋 `org.hl7.fhir.r4b`（convertors 的直接依賴）確保版本一致。

**架構影響：**

- 僅變更 Maven 依賴管理，不影響應用程式碼
- `dependencyManagement` 只覆蓋 transitive dependency 版本，不新增直接依賴
- 6.9.0 為 patch release，API 向後相容

**關聯需求：** #119

**安全考量：**

- CVE-2026-33180：HAPI FHIR HTTP Client 在收到 3xx 重導向時，會將 Authorization header 轉發到重導向目標
- 修復後 HAPI FHIR Client 不再在重導向時轉發認證標頭



---


### SDS-024 [設計] PAT-044: CQL ANTLR Grammar Codegen 架構

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#73](https://github.com/Lusnaker0730/CQL/issues/73) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |

**設計方案：**

```
cql.g4 (官方 ANTLR grammar v1.5)
    ↓  generate-monarch-tokens.py
    ↓  正則提取 keyword / reservedWord / typeNameIdentifier 規則
    ↓
cqlTokens.generated.ts   ← CQL_KEYWORDS, CQL_TYPE_KEYWORDS, etc.
    ↓
cqlSyntax.ts  ← import 產出列表，其餘（TWCDI、FHIR 屬性、theme）手寫不動
```

- 腳本用 Python 正則解析 .g4 文件，不需要 ANTLR runtime
- 產出是純 TypeScript 陣列（readonly string[]），零 runtime 依賴
- `--check` 模式比對現有檔案與重新產出的內容（忽略 timestamp）

**架構影響：**

無 runtime 影響。僅開發/CI 工具鏈變更。

**關聯需求：** #72

**安全考量：**

無安全影響。


**介面描述：**

無 UI 變更。語法高亮顏色分配不變。


---


### SDS-025 [設計] PAT-043: 法規追溯 CI 檢查架構設計

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#69](https://github.com/Lusnaker0730/CQL/issues/69) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |

**設計方案：**

#### 架構

```
.github/
├── workflows/regulatory-check.yml    — GitHub Actions workflow 定義
├── scripts/check-regulatory-traceability.py  — Python 檢查腳本
└── pull_request_template.md          — 加入 CI 提示
```

#### Workflow 觸發

- 事件：`pull_request` (opened, edited, synchronize, labeled, unlabeled)
- 目標分支：`main`
- 權限：`issues: read`, `pull-requests: read`

#### 檢查邏輯

1. **Issue 引用檢查**：用正則 `#(\d+)` 掃描 PR body，無引用則 Block
2. **安全性等級判斷**：讀取需求 Issue 的 labels，判斷 `安全性等級-A/B/C`
3. **追溯鏈驗證**（B/C 等級）：
   - 取得所有 `IEC62304:設計`、`ISO14971:風險`、`IEC62304:驗證` label 的 Issues
   - 搜尋 body 中是否包含 `#需求編號` 引用
   - 任一類型缺失則 Block
4. **Issue body 格式檢查**：驗證法規 Issue 含 `### 標題` 格式
5. **豁免規則**：`docs:` 開頭的 PR 標題跳過檢查

#### 輸出

- `::error::` — GitHub Actions annotation，在 PR 頁面顯示錯誤
- `::warning::` — 警告標記，不阻擋合併
- exit code 0/1 控制 CI status check

**架構影響：**

無應用程式碼影響，僅新增 CI 流程。

**關聯需求：** #68

**安全考量：**

- 腳本使用 `GH_TOKEN` 環境變數（GitHub Actions 自動提供的 `GITHUB_TOKEN`），僅有 read 權限
- 不執行任何寫入操作
- 不存取外部服務


**介面描述：**

無 UI 介面。開發者透過 GitHub PR 頁面的 CI status check 查看結果。


---


### SDS-026 [設計] PAT-042: Monaco-Redux 解耦架構設計

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#66](https://github.com/Lusnaker0730/CQL/issues/66) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |

**設計方案：**

**核心架構變更：Monaco 內部管理編輯狀態，僅在 blur / save / execute 時同步到 Redux**

#### 1. CqlEditor — forwardRef + useImperativeHandle

```typescript
export interface CqlEditorHandle {
  getContent: () => string
  getEditor: () => editor.IStandaloneCodeEditor | null
  flushContent: () => void  // 同步到 Redux
}
```

- onChange 僅呼叫 `onContentChanged` callback（輕量通知）
- onDidBlurEditorText 事件同步到 Redux
- Ctrl+S / Ctrl+Enter 先 flush 再觸發 action

#### 2. EditorPage — syncAndGetContent 模式

- `cqlEditorRef` 按需讀取內容
- `localContent` state 處理 UI 需求（disabled check、libraryMatch regex）
- 所有 action handler 透過 `syncAndGetContent()` 取得最新內容

#### 3. useCqlStructure — callback-driven debounce

- 移除 `useSelector(state.editor.cqlContent)`
- 暴露 `notifyContentChanged(content)` 函式
- CqlBuilderPanel 透過 `editorContent` prop 驅動

#### 4. ExecutionPanel — getLatestCql prop

- 接受 `getLatestCql?: () => string` prop
- 執行時從 prop 讀取最新內容，避免讀取 stale Redux state

**架構影響：**

| 層 | 影響 |
|----|------|
| CqlEditor | 新增 forwardRef + imperative API |
| EditorPage | 從 Redux-driven 改為 ref-driven |
| useCqlStructure | 從 Redux selector 改為 callback |
| CqlBuilderPanel | 新增 editorContent prop |
| ExecutionPanel | 新增 getLatestCql prop |
| MeasureCqlTab | 同 EditorPage 模式 |

**關聯需求：** #65

**安全考量：**

無安全影響 — 純前端效能優化，不改變資料流或 API 呼叫行為。



---


### SDS-027 [設計] 多層輸入驗證與注入防護架構

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


### SDS-028 [設計] EHR FHIR R4 連接器與病患資料匯入架構

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


### SDS-029 [設計] JWT 雙令牌架構與分級限流設計

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


### SDS-030 [設計] eCQM FreeMarker 模板驅動 CQL 產生引擎

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


### SDS-031 [設計] CQL Builder 手風琴式面板與單向插入架構

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


### SDS-032 [設計] FHIR Bundle 視覺化建構器與測試案例執行引擎

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


### SDS-033 [設計] CDS Hooks 評估流程與卡片產生器

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


### SDS-034 [設計] 品質量測評估引擎 Orchestrator 模式

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


### SDS-035 [設計] CQL 翻譯服務非同步處理架構

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
| 總設計項目 | 35 |
| 開放中 | 35 |
| 已關閉 | 0 |

---

*本文件由 CQL Platform 法規文件產生器自動產生*
