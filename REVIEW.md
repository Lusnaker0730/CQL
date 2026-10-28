# REVIEW.md — PR 審查清單

> 給自動 AI review（`.github/workflows/claude-review.yml`，PAT-272）與人工審查共用。
> CLAUDE.md 講「怎麼寫」，這份講「審查時要抓什麼」。CLAUDE.md 的規則有變時，這裡對應的條目要一起改。

## 啟用方式（擁有者）

1. 在 repo 安裝 Claude GitHub App：<https://github.com/apps/claude>（只有 repo admin 能做）。
2. 在本機**另開終端**執行 `claude setup-token`，產生訂閱用的 OAuth token；不要在 AI session 裡執行，token 值不該出現在對話紀錄。
3. `gh secret set CLAUDE_CODE_OAUTH_TOKEN`（互動式貼上值）。secret 不存在時 workflow 會直接略過並成功，PR 不會被卡。
4. 這個 workflow 不是 required check，額度用完或服務異常都不擋合併。

## 審查原則

- **先讀 PR 描述與關聯 issue**，知道作者想做什麼、宣稱測了什麼，再看 diff。PR 描述沒寫測試方式或回滾方式，以 Nit 指出。
- **查證再留言**。每一則都要附檔案與行號，引用你實際讀到的程式，不要靠印象或 pattern 推測。拿不準就說拿不準，不要把猜測寫成結論。
- **三級分類**，每則留言開頭標明：
  - `Important`：不修會出錯、洩漏、資料壞掉、或違反下面「必查」清單的任一條。
  - `Nit`：可以改善但不擋合併（命名、重複、可讀性、小效能）。
  - `Pre-existing`：這次 diff 旁邊的既有問題，不是這個 PR 造成的，只提醒不要求本 PR 修。
- **一輪講完**。把所有 Important 一次列出，不要分多輪慢慢補；沒有 Important 就明說「沒有阻擋項」。
- **不重複 CI 會抓的事**：ESLint / tsc / i18n key 同步 / Monarch token 漂移 / rollback 數量 / Trivy / GitGuardian 都有自動檢查，除非 diff 顯示作者刻意繞過它們。
- **只用 GitHub 留言輸出**（一則總結 + 只對 Important 留 inline comment），不改程式、不開 commit。
- 用**繁體中文**，程式識別字保留原文。

## 必查清單（命中任一即 `Important`）

### 多租戶隔離（CLAUDE.md「多租戶隔離」）
- 新增或修改的 repository 方法是否 tenant-scoped（`findByIdAndTenantId`、`existsByTenantIdAnd...`）；service 是否用自己的 `effectiveTenantId()`。沒有 Hibernate `@Filter`、沒有 RLS、沒有 AOP，隔離只靠每條 query。
- 跨租戶存取是否回 **404**（不可探測），不是 403。
- 新表是否有 `tenant_id`（NOT NULL + 回填）且 rollback 腳本對應。
- 平台操作員限定的操作是否經 `PlatformOperatorGuard.require()`；擁有者檢查是否經 `OwnershipVerifier`。
- 改到 CDS 服務的 DB 寫入路徑，是否同步更新 `CdsHooksService.serviceConfigs` in-memory registry（BUG-142）。
- 租戶範圍的術語資料是否被放進 `FhirTerminologyService` 以 URL 為 key 的 `@Cacheable` 方法（不可）。

### 安全
- 密碼、token、API key、連線字串是否以字面值出現在程式、測試 fixture 或文件（GitGuardian 會掃全部 commit；fixture 的 `"password":"literal"` 也會中）。
- 新端點是否有認證與授權；`DELETE /api/measures/**` 是 admin 限定，租戶層級的「取消」要用 POST。
- 使用者輸入是否進了 regex（ReDoS）、HTML（XSS）、SQL / JPQL 字串拼接、檔案路徑。
- 回應是否洩漏內部錯誤堆疊、其他租戶的 id、或可探測資源存在性。
- 前端是否把敏感資料寫進 `localStorage` / URL query。

### 後端模式（CLAUDE.md「Backend 模式」）
- 耗時量測是否用 `util/Stopwatch`（單調時鐘），不是 `System.currentTimeMillis()` 相減（BUG-145）。
- Service 層是否出現 HTTP 概念（`HttpServletRequest`、`@ResponseStatus`）。
- 多步驟變更是否有 `@Transactional`；是否拋領域例外交給 `GlobalExceptionHandler`。
- JPA 實體的 `@Transient` list / map 只在 `@PrePersist` / `@PreUpdate` 序列化：只改 transient 欄位的 save 會什麼都不寫，要用 `applyValueSetVersions` / `applyComponentWeights` 這類同時寫欄位的方法（PAT-260 的坑）。
- 「載入 → 慢工作 → 寫回」的路徑是否 save 了 detached entity（會把舊快照整個蓋回去，BUG：編輯鎖消失）；要走針對性 UPDATE 或 `@DynamicUpdate`。
- 對 FHIR 的搜尋是否跟 `next` link 讀到最後一頁、id 是否分批（BUG-151）。
- 在 `cqlExecutionExecutor` 的執行緒上是否呼叫了 `CqlExecutionService.execute()`（池飢餓，BUG-152）；池內要 `executeInline`。
- 儲存指標的評估是否經 `MeasureEvaluationService.evaluateMeasure(request, id, def)` 三參數 overload（`MeasureStatusGuard` 在這裡）。
- 指標邏輯是否只在 draft 可改；非 draft 的邏輯變更要 409 `Measure Logic Locked`（BUG-147）。

### CQL 引擎（CLAUDE.md「CQL 執行」「CQL 引擎值模型」）
- 翻譯 + 執行使用者 CQL 是否走 `seedCompiledLibrary(...)`（BUG-107）；`CqlTranslator.fromText` 前是否 `LibraryManagerFactory.ensureModelsLoaded(cql)`。
- 引擎回傳值是否只經 `service/cql/CqlValues.unwrap` 取出、FHIR 資源經 `CqlValues.fromFhir`、參數經 `CqlValues.wrap`；其他地方 import `org.opencds.cqf.cql.engine.runtime.*` 做 `instanceof` 判斷是錯的（PAT-231：`v instanceof Boolean` 永遠 false，母群靜默清空）。
- 新的不變量是否優先寫成 property 測試（`testsupport/Fuzz`），固定答案留給 golden 測試。

### 資料庫
- 每個新 `V__` migration 是否有對應的 `db/rollback/` 腳本；migration 是否可在既有資料上執行（回填、NOT NULL 順序）。
- 含 CHECK 約束的欄位（如 `evaluation_duration_ms >= 0`）寫入前是否處理了不可能的值（存 NULL，不讓 insert 失敗）。

### 前端（CLAUDE.md「i18n」「Frontend 模式」）
- 所有 UI 文字是否走 `useTranslation('namespace')`，**en 與 zh-TW 兩份 JSON 是否都改了**。
- 元件內是否出現 magic number（時間、尺寸、staleTime 要從 `constants/` 來）。
- 隨機來源是否走 `utils/random.ts`（假病人產生器的可重現性靠它；smoke 43 的 fixture 會漂）。
- 漸層背景上的 heading 是否明寫顏色（BUG-149）。
- 測試是否用 `src/test/test-utils.tsx` render，並 mock `react-i18next`。

### 流程與文件
- `docs/CHANGE_LOG.md` 是否在表頂加列、commit 欄是否留空為 `| |`（兩個空格 `|  |` 會讓 backfill 永遠對不上）；純 `docs:` 同步可不加列。
- PR 描述是否有 `#NNN` 需求 issue 引用（`docs:` 開頭除外）；是否寫了無關的 `#數字`（會被拉進 TFDA 追溯；順帶提到要寫「PR 1083」「issue 700」不加 `#`）。
- B / C 級需求是否四件套齊全（需求 / 設計 / 風險 / 驗證）。
- 改了技術棧或目錄結構，CLAUDE.md 的版本表 / 目錄樹是否一併更新。
- 改了 workflow、ruleset、部署流程，`DEPLOYMENT_GUIDE_zh-TW.md` 或相關 README 是否同步。

## 輸出格式

總結留言（一則，sticky）：

```
## AI review（PAT-272）

**結論**：有 N 個 Important / 沒有阻擋項
**測試與回滾**：PR 描述有 / 沒有說明

### Important
- `path/File.java:123` — 一句話說問題、一句話說後果、一句話說怎麼修

### Nit
- ...

### Pre-existing
- ...
```

inline comment **只給有明確行號的 Important**，開頭同樣標級別；Nit 與 Pre-existing 只寫在總結留言，**不留 inline**。沒有 Important 時仍要留總結，讓合併者知道這輪看過了。

為什麼 Nit 不留 inline：main 的 ruleset 開了 review thread resolution，每一則 inline comment 都是一個要 resolve 的 thread，而每次 push 都會重跑 review——Nit 留成 inline 等於每一輪都用「不擋合併」的意見擋住合併（PR 1112、PR 1114 各被擋了兩輪）。Important 留 inline 正是要這個擋門效果。
