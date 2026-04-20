# Pre-Launch Production Readiness Review

| | |
|---|---|
| **Review 日期** | 2026-04-20 |
| **適用目標** | 接真 EHR + 真 PHI + 真臨床決策的 production 部署 |
| **Reviewer** | Claude Opus (code review mode) |
| **Commit 當下** | `fb62fc4` (main) |
| **Scope** | Backend (Spring Boot) + Frontend (React SPA) + Infrastructure (Docker) + Regulatory 文件 |
| **總覽** | 🔴 Critical 4 / 🟠 High 7 / 🟡 Medium 6 / 🟢 Good (既有保留) |

## 整體結論

**目前 NOT production-ready 接真 EHR / 真 PHI。**

- Backend 邏輯正確性在 smoke harness 驗證下屬於成熟（15 scenarios，3 輪 silent-failure 修復完成）
- 但**部署安全邊界（TLS / PHI 加密 / measure 生命週期 guard）、regulatory 合規文件、ops 成熟度**都還沒到位
- 以 TFDA 醫療器材軟體（IEC 62304）+ 風險管理（ISO 14971）要求看，不能在此狀態接觸真 PHI

## 上線前三階段計畫

| Phase | 時間預估 | 必辦項目 |
|---|---|---|
| **Tier 1 — 絕對 blocker** | 1-2 週 | `#1 TLS`、`#2 draft guard`、`#3 measure-version tracking`、`#4 PHI field encryption`、`#11 regulatory docs 填充` |
| **Tier 2 — 上線 30 天內** | 1 個月 | `#5 password lockout`、`#7 deserialization alert`、`#8 CDS rate limit`、`#9 pool sizing`、`#10 FHIR degradation UX` |
| **Tier 3 — 季度內** | 3 個月 | `#6 @SuppressWarnings` 註解、`#12 liveness/readiness` 拆分、`#13 PITR runbook`、`#14 metrics endpoint 保護`、`#15 eval rate limit`、`#16 entity audit`、`#17 frontend version push` |

---

## 🔴 Critical（上線 blocker）

### #1 TLS 沒強制

- **現況**：`backend/src/main/resources/application.yml:18-23` 整塊 `server.ssl` 被註解；`APP_BASE_URL` / SMART endpoints 預設 `http://localhost:8080`（`application.yml:123-125`）
- **風險**：真 EHR 透過 HTTP 傳 Bearer token + PHI → 中間人攻擊直接截獲 patient data 與憑證
- **修法**：
  1. Spring Boot 或前置 reverse proxy（nginx / Caddy）強制 HTTPS-only
  2. `APP_BASE_URL` 必須以 `https://` 開頭，否則 application 啟動期 fail-fast
  3. HSTS header 設 6+ 月
  4. CSP / X-Content-Type-Options / X-Frame-Options headers
- **驗證**：`curl -I http://twcql.com/` 應 redirect 到 https；SSL Labs 測試達 A 級
- **Status**: `[ ] TODO`

### #2 Draft measure 能跑真病人資料

- **現況**：`MeasureDefinition.status` 有 `draft|active|retired|in-review` 定義，但 `MeasureEvaluationService` 沒檢查 status
- **風險**：研擬中、未 review 完的 measure 被 CDS hook 呼叫 → 發錯 alert、算錯 rate、臨床決策基於未驗證 logic
- **修法**：
  1. `MeasureEvaluationService` entry point 加 `if (!"active".equals(measure.status)) throw ...`
  2. Draft / in-review 要走 sandbox endpoint 才能執行（不碰 production FHIR server）
  3. UI 顯示警告 badge 於非 active measure
- **驗證**：新 smoke scenario — draft measure evaluate 回 4xx；active 才回 200
- **Status**: `[ ] TODO`

### #3 Evaluation 結果沒綁 measure 版本

- **現況**：`MeasureReportEntity` 沒有 `measure_version` / `cql_hash` / `elm_hash` 欄位
- **風險**：稽核「2026-01 的 report 用的是哪版 CQL」答不出來；measure 改版後歷史 report 不可重現 → TFDA / CMS compliance 驗證卡住
- **修法**：
  1. Flyway migration 加 `measure_version VARCHAR`、`cql_hash VARCHAR(64)`、`elm_hash VARCHAR(64)` 欄位
  2. Publish measure 時 snapshot CQL + ELM、SHA-256 hash 後一併存進 `measure_report`
  3. Evaluation 前驗證 snapshot 是否還存在（unbound snapshot → reject）
  4. 歷史 report 回顧 UI 顯示 "evaluated with CQL version X (hash Y)"
- **驗證**：更新一個 measure 後跑 evaluation，check report.measure_version 不同於修改前
- **Status**: `[ ] TODO`

### #4 PHI 在 DB 以 plain text 儲存

- **現況**：`MeasureReportEntity.result_json` 是 `TEXT` 欄、serialize 整包 evaluation 結果（含 patient IDs、可能 observation 值）。只有 `UserEntity.email` 套 `@Convert(EncryptionConverter.class)`
- **風險**：DB dump / backup 檔外洩 = PHI 外洩
- **修法**：
  1. 盤點哪些欄位含 PHI：`MeasureReportEntity.result_json`、normalized population 子表的 `subject_id` 列、audit log 的 query_params 等
  2. 套 `EncryptionConverter` 到每個 PHI 欄位
  3. Flyway migration 加密既有資料（離線 batch、分 chunk）
  4. Backup 也要用加密（pg_dump 加 `-Z` + encryption at rest）
  5. 輪替 encryption key 的程序建立
- **驗證**：query DB 看不到明文 patient ID；application 透過 converter 正常 read
- **Status**: `[ ] TODO`

---

## 🟠 High（上線 30 天內）

### #5 沒 password lockout
- **現況**：`UserEntity` 沒 `failed_login_attempts` / `lockout_until` column
- **風險**：brute-force；醫療系統帳號是高價值目標
- **修法**：column + filter 計數 + 暫鎖（如 5 次失敗 30 分鐘）+ email alert；Admin 可手動解鎖
- **Status**: `[ ] TODO`

### #6 `@SuppressWarnings("unchecked")` 15 處沒說明
- **現況**：`EcqmPublishService`、`EcqmExpressionTreeValidator` 等 15 處，多半是 `Map<String, Object>` cast
- **風險**：型別安全盲點，升 Java / 依賴時可能爆 ClassCastException
- **修法**：每個加 inline comment 說明 cast 為何 safe、或改 DTO
- **Status**: `[ ] TODO`（列 Tier 3 實際時間看）

### #7 Deserialization 失敗靜默吃掉
- **現況**：`MeasureReportEntity` 有 `DESERIALIZATION_FAILURES` counter（BUG-114 加的），production 只 log warning
- **風險**：DB 資料損壞 / schema drift 沒人發現，report 讀出來少欄位
- **修法**：counter 接 Prometheus → Grafana alert；或 healthcheck degrade
- **Status**: `[ ] TODO`

### #8 CDS Hooks discovery public 但沒 rate limit
- **現況**：`GET /cds-services` unauth（spec 允許）+ `POST /cds-services/{id}` 無 rate limit
- **風險**：惡意使用者 enumerate 所有 service + 密集 POST = DoS
- **修法**：Bucket4j per-IP rate limit（e.g. 60 req/min per IP on invocation）；discovery 低 limit
- **Status**: `[ ] TODO`

### #9 HikariCP pool = 20 對真實 load 太小
- **現況**：`application.yml:48` `maximum-pool-size: 20`；measure pool 也 20
- **風險**：100 concurrent EHR users + bulk patient import = 連線池爆、API 超時
- **修法**：依實際 concurrent user 估算（一般建議 2× CPU + spindles）；bulk 操作走 background queue 不占 API pool
- **Status**: `[ ] TODO`

### #10 FHIR server outage 沒 graceful degradation
- **現況**：Circuit breaker 有設（Resilience4j `application.yml:283-287`），但 UX 沒定義
- **風險**：真 EHR 的 FHIR server 偶爾 timeout / 5xx 是日常；使用者看到 raw error 會誤以為 platform bug
- **修法**：統一 fallback response（如 "EHR 暫時離線、已自動重試 N 次"）；per-EHR connection health status UI
- **Status**: `[ ] TODO`

### #11 Regulatory docs 只有模板、沒實際 artifacts
- **現況**：`regulatory_docs/templates/` 有 6 個 `.md` 模板；`regulatory_docs/output/` 空的
- **風險**：TFDA 醫療器材軟體（IEC 62304）+ 風險管理（ISO 14971）要有實際產出、不能只有模板
- **修法**：
  1. 跑 `regulatory_docs/scripts/generate_regulatory_docs.py`
  2. 由 domain expert 填充每份文件
  3. Review + sign-off 流程定義
  4. 版本控制與 revision tracking
- **Status**: `[ ] TODO`

---

## 🟡 Medium（上線後持續強化）

### #12 `/actuator/health` 同時當 liveness + readiness
- **現況**：單一 endpoint，Docker health check 不區分
- **風險**：CQL translation 卡住時 process 還活但不能服務，Kubernetes 不會 restart
- **修法**：`/actuator/health/liveness`（process 活）+ `/actuator/health/readiness`（可服務）分開，container orchestrator 接對應的 probe
- **Status**: `[ ] TODO`

### #13 沒 PITR runbook
- **現況**：`docker-compose.yml` 有 `archive_mode=on` 暗示 WAL shipping，但 restore 程序沒文件
- **風險**：DB 事故時 RTO / RPO 不可預期
- **修法**：寫 DR playbook（full restore / PITR / schema-only restore）；季度演練並記錄時間
- **Status**: `[ ] TODO`

### #14 Metrics endpoint 在 docker 設 public
- **現況**：`application-docker.yml` `management.prometheus.public: true`
- **風險**：若 backend container 意外暴露到 internet → latencies / error rates / JVM 內部訊息全露
- **修法**：維持 `false` + 走內網 Prometheus；或加 basic auth / IP allowlist
- **Status**: `[ ] TODO`

### #15 Expensive endpoints 無 rate limit
- **現況**：`/api/measures/{id}/$evaluate-measure` 沒 per-user rate limit
- **風險**：惡意 / 無心 batch evaluation 造成 DB + CPU burn
- **修法**：per-user per-measure rate limit（e.g. 10 evaluations / hour / user / measure）
- **Status**: `[ ] TODO`

### #16 Measure definition 修改沒 entity audit
- **現況**：`AuditLog` 蓋 access；不蓋 modification
- **風險**：「誰何時改了 measure X」無法回溯；合規稽核缺資料
- **修法**：JPA Envers 或自建 entity-version table；measure + service config update/delete 強制 audit
- **Status**: `[ ] TODO`

### #17 Frontend VersionCheckProvider 只 poll，沒 push
- **現況**：PAT-079 加的 poll 間隔 5 分鐘
- **風險**：部署窗口內使用者最多 5 分鐘拿舊 schema；醫療場景可能 unacceptable
- **修法**：SSE push / WebSocket；或縮短 poll（30s）；或 deploy 做 canary drain
- **Status**: `[ ] TODO`

---

## 🟢 Good（不用動，review 時確認的 solid 部分）

| 項目 | 位置 |
|------|------|
| JWT 32-byte secret enforce + token version 即時撤銷 | `JwtTokenProvider`, `TokenVersionService` |
| Refresh token HttpOnly + SameSite=Strict + Secure | `application.yml:95`, `SecurityConfig.java:60-62` |
| BCryptPasswordEncoder | `SecurityConfig.java:150` |
| Audit log PHI access flag + 365 day retention | `AuditLogEntity`, `AuditFilter`, `application.yml:221` |
| Flyway migrations + rollback scripts + lock-retry | `db/migration/*` + `db/rollback/*` |
| Dockerfile multi-stage + 非 root | `docker/Dockerfile.backend:18` |
| Resilience4j circuit breakers | `application.yml:283-287` |
| Smoke harness 15 scenarios（eCQM + CDS）| `scripts/smoke/` |
| i18n + authoring error hints | `PAT-088/089/092` |
| Jakarta @Valid on DTOs | 各 Controller `@Valid @RequestBody` |
| `docker/.env` **gitignored**（初次 survey 誤報修正）| `.gitignore` |
| CQL engine SignatureLevel=Overloads 消除 dispatch ambiguity | `LibraryManagerFactory` (`PAT-078`) |
| Structural fixes against silent failures（cohort score / ratio Numer / CV boolean / aggregate aliases）| `PAT-083/084/085/088/089` |

---

## Review 過程記錄

此 review 由 Claude Opus 於 2026-04-20 以 code reviewer 角色執行，涵蓋：
- 靜態 code survey（Explore agent 針對 8 個領域）
- 關鍵項目現場驗證（`grep`，`git ls-files`，真實檔案內容）
- 跟專案歷史（CHANGE_LOG.md）對照，避免重複建議已修項目
- 誠實校正初次 survey 的一處誤報（`docker/.env` 實際 gitignored，非 committed）

## 下次 Review 建議

- Tier 1 全數完成後
- 或重大架構改動後（新外部 EHR 整合 / 新 scoring type / 新 regulatory scope）
- 至少每 6 個月
