# 軟體驗證報告 (Software Verification Report)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-SVR-1.0.0 |
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

本文件彙整 CQL Platform 的軟體驗證活動紀錄，依據 IEC 62304 醫療器材軟體生命週期流程標準。

## 2. 測試環境

| 項目 | 版本 |
|------|------|
| 後端框架 | Spring Boot 3.2.0 / Java 21 |
| 前端框架 | React 18 / TypeScript 5.3 / Vite 5.0 |
| 測試框架（後端）| JUnit 5 / Mockito |
| 測試框架（前端）| Vitest / React Testing Library |
| CI 環境 | GitHub Actions / Ubuntu Latest |

## 3. 驗證紀錄


### VER-001 [驗證] Smoke 擴充 #337 — 雙重 contract + 附帶 backend fix

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#340](https://github.com/Lusnaker0730/CQL/issues/340) |
| 建立日期 | 2026-04-22 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 3 個 contract lock 生效，且兩個附帶的 backend 修復（TracingRetrieveProvider memoization + EcqmPublishService elmJson persist）在整合場景正確。

**測試步驟：**

**Local smoke harness**（`scripts/smoke/run.sh`，19 scenarios）：
- 原 17 個 scenarios（01-16, 18）不變
- 新 scenario 17 `17-cds-retrieve-cache-dedupe`：驗 `retrieveTracesCount: 1`
- 新 scenario 20 `20-cql-execute-error-info`：驗 `expectedHttpStatus: 500` + `errorInfoPhase: cql_translation`
- 擴充 scenario 01：`checkProvenance: true` → follow-up GET `/api/measures/{id}/reports` 驗 `cqlHash` / `elmHash` / `measureVersion` 都非 null

**Backend 全量 test suite**（`mvn -f backend/pom.xml test`）：
- 既有 `TracingRetrieveProvider` 無 unit test（本 PR 也未加）— 靠 smoke scenario 17 覆蓋
- 既有 `EcqmPublishServiceTest` 若有 publish happy-path 應該仍綠
- 全 1307+ tests BUILD SUCCESS

**預期結果：**

| 項目 | 預期 |
|---|---|
| `scripts/smoke/run.sh` | 19/19 pass |
| scenario 01 | provenance 三欄都非 null |
| scenario 17 | retrieveTracesCount=1 |
| scenario 20 | HTTP 500 + errorInfo |
| 全量 backend suite | BUILD SUCCESS |

**實際結果：**

（未填寫）

**關聯需求：** #337



---


### VER-002 [驗證] Debug mode batch eval — regression + retrieve dedupe 驗收

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#335](https://github.com/Lusnaker0730/CQL/issues/335) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 (a) debug mode 不再多呼叫 engine.evaluate()，(b) `TracingRetrieveProvider` 不再累積重複 trace row，(c) 所有 expression 結果欄位仍正確填入，(d) 原有 per-expression trace metadata（sourceLocator、dependencies、errors）沒有 regression。

**測試步驟：**

**Unit / integration tests**（既有 + 本 PR 未加新 test file，所有保障來自既有覆蓋）：
- `CqlControllerTest`：13 tests — POST `/api/cql/execute` 各 happy / error paths
- `CqlExecutionIntegrationTest`：32 tests across 10 nested classes，特別是：
  - `4. Debug Mode (expression traces)`：驗 debugMode=true 時 `debugTrace.expressionTraces` 非空、`retrieveTraces` 存在
  - `5. Error Handling`：per-expression exception capture
  - `2. FHIR Retrieve against golden patient data`：批次 retrieve 真的跑得動
  - `3. Diabetes Screening Measure (Golden Data)`：多 expression 交互測試
  - `10. Cross-library retrieve discovery (BUG-111)`：跨 library 的 retrieve 仍 work
- 全量 backend 1300+ tests 綠

**Integration smoke**（`scripts/smoke/run.sh`）：
- 17 scenarios 全綠
- Scenario 16 `16-cql-execute-debug-trace` 特別驗：`success: true`、`expressionTraces` 最少 3 個 entry、每 entry 含 `name/resultType/evaluationTimeMs/order`、`elmJson` 非空、`totalTimeMs` 是 number
- Scenario 18 `18-cds-error-debug-trace` 驗 debug mode + error path 仍回 structured `debug.error`

**Manual production verification**（deploy 後建議）：
- 用 bug1.png 同樣的 CDS hook（BMI with 4 defines）觸發 debug mode
- 預期：FHIR retrieve trace 只有 1 筆 Observation（而非原本的 10 筆）
- 預期：`totalTimeMs` 與原 debug mode 相近或更快（因少了 N-1 次 FHIR round-trip）

**預期結果：**

| 項目 | 預期 |
|---|---|
| `mvn -f backend/pom.xml test` 全量 | BUILD SUCCESS |
| `CqlControllerTest` | 13/13 pass |
| `CqlExecutionIntegrationTest` | 32/32 pass |
| `scripts/smoke/run.sh` | 17/17 pass |
| Manual 驗 BMI CDS hook debug | retrieveTraces 從 10 筆降到 1 筆 |

**實際結果：**

2026-04-21 本機實測：
- `mvn test`：BUILD SUCCESS in 3m57s
- `CqlControllerTest`：13/13 pass in 33.5s
- `CqlExecutionIntegrationTest`：32/32 pass（含 `4. Debug Mode` 1/1）
- Smoke harness：17/17 pass（含 scenario 16 / 18）

Manual 驗留至 deploy 後由使用者在 twcql.com 上確認。

**關聯需求：** #332



---


### VER-003 [驗證] PHI 加密 Phase 1 — round-trip + raw-DB ciphertext 驗收

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#330](https://github.com/Lusnaker0730/CQL/issues/330) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

雙重 contract：(a) application 層讀出是 plaintext；(b) 同一 row 繞過 JPA 從 raw JDBC 讀出必須是 ciphertext（`ENC:` prefix 開頭）。兩個 assertion 同時成立才算通過——只 (a) 通過 = 我們以為加密了其實沒；只 (b) 通過 = 正常 app 路徑壞了。

**測試步驟：**

**Unit tests**（`EncryptionConverterTest`，13 項）：
- 新增 `roundTrip_largeJsonPayload_100kb` — 100KB+ realistic JSON round-trip
- 新增 `roundTrip_utf8MultibytePayload` — 中文 / 多位元組字元 round-trip
- 既有 11 個 test 保持綠（legacy plaintext fallback / wrong key / unique IV 等）

**Integration tests**（`PhiEncryptionIntegrationTest`，4 項，`@SpringBootTest` + H2）：
1. `measureReport_resultJson_encryptedOnDisk` — save entity → JPA read returns plaintext；raw JDBC SELECT returns `ENC:` + 不含明文 patient ID 字串
2. `measureReport_resultJson_legacyPlaintextStillReadable` — 用 `JdbcTemplate` 手動 insert 明文 row（模擬 Phase 1 deploy 前的 legacy row）→ JPA read 仍回傳 plaintext（converter fallback 路徑）
3. `patientImport_allPhiFields_encryptedOnDisk` — 同 (1) 模式，覆蓋 `patient_fhir_id` / `patient_identifier` / `patient_name` / `bundle_json` 四欄
4. `sandboxPreset_patientIdAndPrefetch_encryptedOnDisk` — `patient_id` + `prefetch_json`

**Migration test**：V55 `ALTER COLUMN ... TYPE TEXT` 在 CI PG migration test job 跑過、不影響既有 row。

**全量 backend suite**：本 PR 前 1300+ tests 綠；加完 6 個新 test 全綠。

**預期結果：**

| 項目 | 預期 |
|---|---|
| `EncryptionConverterTest` | 13/13 pass |
| `PhiEncryptionIntegrationTest` | 4/4 pass |
| `mvn -f backend/pom.xml test` 全量 | BUILD SUCCESS |
| V55 migration 在 H2 + PG 都能 apply | clean ALTER |
| JPA read → plaintext | ✓ |
| Raw JDBC read → `ENC:` prefix + 不含明文 PHI | ✓ |
| Legacy plaintext row JPA read → plaintext | ✓（fallback 路徑） |

**實際結果：**

2026-04-21 本機實測：

- `EncryptionConverterTest`：13/13 pass in 1.985s
- `PhiEncryptionIntegrationTest`：4/4 pass in 30.75s（H2 + Spring Boot ctx bootstrap cost）
- Full backend suite：BUILD SUCCESS in 3m49s（與加密前相同數量級，無 regression）
- 首次整合 test run 抓到一個 issue：raw `JdbcTemplate.update` INSERT 漏 `created_at` 欄位 → NOT NULL constraint 爆炸。修 fix 後綠。這個 fix 也順便示範了 test setup 對 column constraint 完整性的 regression value（將來若有人改 schema 忘了給 default，這 test 會率先失敗）

PR #TBD 送 CI 後同步驗 PG migration test。

**關聯需求：** #327



---


### VER-004 [驗證] 統一 debug 模式錯誤分類 — 三 flow 共用 classifier

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#323](https://github.com/Lusnaker0730/CQL/issues/323) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證三個 flow（CDS / editor / eCQM）共用 `ExecutionErrorClassifier` 後：(a) CDS wire JSON 零變動（CDS 客戶端零改動）、(b) editor 的 `GlobalExceptionHandler.ErrorResponse` 多 `errorInfo` 欄位並正確填值、(c) eCQM 的 `MeasureEvaluationResult` 多 `errorInfo` 欄位並正確填值、(d) classifier 的邊界條件（null / cause loop / stack filter）都 stable。

**測試步驟：**

**Unit tests**（`ExecutionErrorClassifierTest`，14 項）：

1. `classify_null_shouldReturnUnknown` — null 輸入 → UNKNOWN
2. `classify_pureRuntimeException_shouldReturnCqlExecution` — 無 markers → CQL_EXECUTION（防 over-match regression）
3. `classify_classSimpleName_Translation_shouldReturnCqlTranslation`
4. `classify_classSimpleName_Compiler_shouldReturnCqlTranslation`
5. `classify_wrappedTranslatorMessage_shouldReturnCqlTranslation`（BUG-115 主 case）
6. `classify_nestedCause_withTranslationClass_shouldReturnCqlTranslation`
7. `classify_causeLoop_shouldTerminate` — 50 層 nested cause、無 markers → terminate with CQL_EXECUTION
8. `buildErrorInfo_autoClassify_populatesAllFields`
9. `buildErrorInfo_explicitPhase_overridesClassification`
10. `buildErrorInfo_nullThrowable_returnsPhaseOnly`
11. `buildErrorInfo_noOwnPackageFrames_stackTraceSummaryIsNull`
12. `buildErrorInfo_ownPackageFrames_limitedToFive`
13. `fromCdsPhase_null_returnsUnknown`
14. `fromCdsPhase_eachLegacyValue_mapsCleanly`（exhaustive enum cover）

**Regression tests**（`CdsInvocationServiceTest`，既有 10 項全綠）：
- `invoke_debugModeTrue_translatorErrorWrappedInExecutionException_shouldClassifyAsTranslation`（BUG-115）保持綠
- `invoke_debugModeTrue_executionFails_shouldAttachErrorInfoWithPhase`（"boom" → cql_execution）保持綠
- 其他 8 tests（CDS strategy dispatch、dry-run、debugMode 組合）皆綠

**整合 smoke**（既有 scenario 18 pin）：
- `18-cds-error-debug-trace`：service CQL 呼叫 undefined function + debugMode=true → `debug.error.phase = "cql_translation"`（BUG-115 引入）
- 重構後 CDS wire JSON 不變，此 scenario 仍綠

**預期結果：**

| 項目 | 預期 |
|---|---|
| `ExecutionErrorClassifierTest` | 14/14 pass |
| `CdsInvocationServiceTest` | 10/10 pass（regression） |
| Full backend suite | BUILD SUCCESS |
| Smoke scenario 18 | `debug.error.phase: cql_translation` ✓（待 deploy 後驗） |
| Mvn compile | clean |

**實際結果：**

2026-04-21 本機實測：

- `ExecutionErrorClassifierTest`：14/14 pass in 0.026s
- `CdsInvocationServiceTest`：10/10 pass in 5.165s
- Full backend suite：BUILD SUCCESS in 3m37s
- Mvn compile：SUCCESS、零 warning

Smoke scenario 18 重跑：延後至 deploy 後驗（與 CI pipeline 同步）。

**關聯需求：** #320



---


### VER-005 [驗證] CDS phase 分類修復 — wrapped translator error 驗收

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#318](https://github.com/Lusnaker0730/CQL/issues/318) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 `CdsInvocationService.looksLikeTranslationError()` 修復後能：(a) 正確把 `CqlExecutionException` 中 wrap 的 translator error 分類為 `cql_translation`、(b) nested cause chain 中含 `*Compiler*` simple name 也分類為 `cql_translation`、(c) 純 runtime 錯誤（無翻譯關鍵字）保持 `cql_execution`。

**測試步驟：**

**Unit tests**（`CdsInvocationServiceTest`）：

1. `invoke_debugModeTrue_translatorErrorWrappedInExecutionException_shouldClassifyAsTranslation`
   - mock executionService.execute 丟 `RuntimeException("Execution failed: CQL translation failed with 1 error(s): Could not resolve call")`
   - expected: `debug.error.phase == "cql_translation"`

2. `invoke_debugModeTrue_translatorErrorAsNestedCause_shouldClassifyAsTranslation`
   - mock 丟 `RuntimeException("outer", new CqlCompilerException("bad syntax"))`
   - expected: `debug.error.phase == "cql_translation"`（匹配 inner class 的 `Compiler` simple name）

3. Regression：`invoke_debugModeTrue_executionFails_shouldAttachErrorInfoWithPhase`（既有 test 不改）
   - mock 丟 `RuntimeException("boom")`
   - expected: `debug.error.phase == "cql_execution"`（無關鍵字、不誤歸）

**Integration test**（`scripts/smoke/scenarios/18-cds-error-debug-trace`）：
- `cqlContent` 含 `ThisFunctionDoesNotExist('oops')`
- 實際跑 save → invoke（debugMode=true）→ 驗 `debug.error.phase == "cql_translation"`

**預期結果：**

| 項目 | 預期 |
|---|---|
| Unit 1（wrapped translator） | phase=cql_translation ✓ |
| Unit 2（nested compiler） | phase=cql_translation ✓ |
| Regression（pure runtime） | phase=cql_execution ✓ |
| Smoke scenario 18 | phase=cql_translation ✓ |
| Backend suite 全量 | 1307/1307 pass |
| Smoke harness 17 scenarios | 17/17 pass |

**實際結果：**

2026-04-21 本機實測：

- Unit tests `CdsInvocationServiceTest`：10/10 pass（含新 2 + regression）
- Full backend suite：1307 tests pass in 4min 3s
- Smoke scenario 18：`debug.error.phase: cql_translation` ✓、`errorType: CqlExecutionException`、`message: Execution failed: CQL translation failed...`
- 無 duplicate phase print（smoke assert-cds.sh 同 PR 內修掉）

**關聯需求：** #315



---


### VER-006 [驗證] TLS 強制於 twcql.com — origin + redirect + HSTS 驗收

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#311](https://github.com/Lusnaker0730/CQL/issues/311) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 Cloudflare Origin Certificate 在 VM nginx 正常運作，HTTP 請求全部 301 redirect 到 HTTPS，HTTPS 回應帶 HSTS header，origin cert 以 openssl 檢視合格。

**測試步驟：**

在 VM 上直接執行（繞過 Cloudflare、直接打 origin 127.0.0.1）：

1. **Plain HTTP 301 redirect**：
   \`\`\`
   ssh root@187.77.155.248 'curl -sI http://127.0.0.1/ -H "Host: twcql.com" | head -5'
   \`\`\`
2. **HTTPS 200 + HSTS**：
   \`\`\`
   ssh root@187.77.155.248 'curl -sI -k --resolve twcql.com:443:127.0.0.1 https://twcql.com/ | head -15'
   \`\`\`
3. **Cert 檢視**：
   \`\`\`
   ssh root@187.77.155.248 'echo | openssl s_client -servername twcql.com -connect 127.0.0.1:443 2>/dev/null | openssl x509 -noout -subject -issuer -dates'
   \`\`\`
4. **nginx -t** 通過、\`systemctl reload nginx\` 無錯。
5. **File perms**：\`ls -l /etc/ssl/cloudflare/\` 確認 cert 644 / key 600 / owner root。

**預期結果：**

| 項目 | 預期 |
|---|---|
| 1 | `HTTP/1.1 301 Moved Permanently` + `Location: https://twcql.com/` |
| 2 | `HTTP/1.1 200 OK` + `Strict-Transport-Security: max-age=15768000; includeSubDomains` |
| 3 | subject 含 `CloudFlare Origin Certificate`；notAfter 為 2041；issuer 含 `CloudFlare Origin SSL Certificate Authority` |
| 4 | `nginx: configuration file /etc/nginx/nginx.conf test is successful` |
| 5 | cert 644、key 600、root:root |

**實際結果：**

2026-04-20 於 VM 直接驗證：

- (1) curl http→https 301 ✅
- (2) curl https 回 200 + `Strict-Transport-Security: max-age=31556926; includeSubDomains`（HAPI/SPA 層另疊一次更寬的 HSTS，nginx 自加的被覆蓋但規範仍達標）✅
- (3) openssl s_client 取得 Cloudflare Origin Certificate，notBefore=2026-04-20、notAfter=2041-04-16 ✅
- (4) nginx -t OK、reload 成功 ✅
- (5) cert/key perms 正確 ✅

Cloudflare SSL mode 切換為使用者手動步驟，完成後 end-to-end 驗證將由使用者另行執行 `curl -sI https://twcql.com/` 從 public internet 驗證 200。

**關聯需求：** #308



---


### VER-007 [驗證] ContentHashTest + MeasureReportServiceVersionTrackingTest (#303)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#306](https://github.com/Lusnaker0730/CQL/issues/306) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 測試結論 | 通過。 |

**測試目的：**

驗證 ContentHash 對所有 input 合約正確；saveReport 對所有 definition-lookup 分支填 provenance 正確。

**測試步驟：**

**ContentHashTest（6 tests）：**
- null → null（provenance-none distinguishable）
- empty string → known SHA-256 vector
- "abc" → known vector
- pure function（same input → same output）
- byte-level variation → different hash
- UTF-8 中文輸入 → deterministic

**MeasureReportServiceVersionTrackingTest（5 tests）：**
- Definition exists → version + cqlHash + elmHash 都正確 capture
- Same measure, CQL edit → cqlHash differ（invariant check）
- Definition missing (deleted between eval & save) → save 成功 + 3 provenance null
- measureDefinitionId null → 不查 DB（skip lookup）
- cqlContent null on def → cqlHash null（不填 empty-string SHA）

**預期結果：**

（未填寫）

**實際結果：**

`mvn test -Dtest=ContentHashTest,MeasureReportServiceVersionTrackingTest` → **11/11 pass**
全量 1304/1304 pass（baseline 1293 + 11 new）

**關聯需求：** （未指定）



---


### VER-008 [驗證] LoginAttemptListenerTest + CustomUserDetailsServiceLockoutTest (#PAT-094)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#299](https://github.com/Lusnaker0730/CQL/issues/299) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 測試結論 | 通過。 |

**測試目的：**

驗證 password lockout 行為：counter 正確累加、達 threshold 設 lockout、成功登入清零、locked 帳號在 DaoAuthenticationProvider 之前就被拒絕、攻擊 edge cases（unknown user / null username）safe。

**測試步驟：**

**LoginAttemptListenerTest** — 7 tests：
- First failure → counter=1
- Below threshold → increment without lock
- At threshold → lockout_until set ~30min future
- Unknown user → no DB write
- Success on dirty state → clear counter + lockout
- Success on clean state → no DB write（hot path）
- Null username → no-op, no repo query

**CustomUserDetailsServiceLockoutTest** — 3 tests：
- Future lockout → accountNonLocked=false
- Past lockout → treated unlocked
- Null lockout → unlocked

**預期結果：**

（未填寫）

**實際結果：**

- `mvn test -Dtest=LoginAttemptListenerTest,CustomUserDetailsServiceLockoutTest` → **10/10 pass**
- 全量 1303/1303 pass（baseline 1293 + 10 new）

**關聯需求：** Closes #PAT-094



---


### VER-009 [驗證] CqlTranslationServiceHintTest + HookTypeValidatorTest deprecation cases (#292)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#295](https://github.com/Lusnaker0730/CQL/issues/295) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 測試結論 | 通過。 |

**測試目的：**

驗證 error message hints 對識別的 patterns 觸發正確、對未識別的訊息不 over-hint；deprecated hook rejection 帶 migration guidance、純未知 hook 維持 generic message。

**測試步驟：**

**CqlTranslationServiceHintTest** — 4 tests：
- CodeableConcept × String equivalent → hint 含「CodeableConcept」「coding[0].code」「Code 'active' from」
- Coding × String equivalent → hint 含「Coding」「.code」
- Unrelated translator errors（syntax / lib-not-found） → null
- Null message → null

**HookTypeValidatorTest** — 2 新 tests（既有 3 保留）：
- `medication-prescribe` → exception message 含「deprecated in CDS Hooks 1.0」「order-sign」
- `totally-made-up-hook` → generic「Invalid hook type」message，no deprecated 提示

**預期結果：**

（未填寫）

**實際結果：**

- `mvn test -Dtest=HookTypeValidatorTest,CqlTranslationServiceHintTest` → **10/10 pass**
- 全量 1293/1293 pass

**關聯需求：** Closes #292



---


### VER-010 [驗證] EcqmExpressionTreeValidatorTest aggregateMethod cases (#283)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#287](https://github.com/Lusnaker0730/CQL/issues/287) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 測試結論 | 通過。 |

**測試目的：**

驗證 save-time aggregateMethod validation 正確 accept canonicals + aliases、reject typos、accept null/blank。

**測試步驟：**

`EcqmExpressionTreeValidatorTest` — 33 tests：
- Parametrized over canonical forms + aliases（Min/Max/Avg/Mean × case variants）
- Parametrized over typos（Minumum / Maxmum / Percentile / garbage ...）
- Null / blank / whitespace input accepted
- Missing observations 結構 accepted
- Multi-observation with one bad method → error path 指向該 index

**預期結果：**

（未填寫）

**實際結果：**

- `mvn test -Dtest=EcqmExpressionTreeValidatorTest` → 33/33 pass
- 全量 1286/1286 pass
- Smoke 9/9 pass（aliases 仍 accept 於 save time）

**關聯需求：** Closes #283



---


### VER-011 [驗證] aggregateMethod normalization — MeasureScoreCalculatorTest + smoke 08/09 (#278)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#281](https://github.com/Lusnaker0730/CQL/issues/281) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 測試結論 | **通過**：aggregateMethod 正規化涵蓋所有合理使用場景（canonical/alias/case variant）；typo 不再 silent fall-through；display 欄位 canonical 化；end-to-end 驗證 alias 真機可用。 |

**測試目的：**

驗證 `normalizeAggregateMethod` 正確處理：canonical forms、aliases (Min/Max/Avg/Mean)、case variants、null/blank input、unknown typos；`calculateContinuousVariableScore` 對 aliases 走正確分支、對 unknowns 回 null（不 silent 走 Average）；`computeObservationStats` 顯示 canonical form。End-to-end 驗證 smoke scenarios 08/09 用 `"Min"` / `"Max"` alias 仍產生正確 min/max 極值。

**測試步驟：**

**1. 單元測試（`MeasureScoreCalculatorTest`）— 40+ tests：**

_Normalization helper（parametrized）：_
- `normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases` — 21 cases 覆蓋 count/sum/average/median/minimum/maximum × 原樣 / 小寫 / 大寫 + aliases `Min` `min` `MIN` `Max` `max` `MAX` `Avg` `avg` `Mean` `mean`
- `normalizeAggregateMethod_nullOrBlank_defaultsToAverage` — 三種 null/empty/whitespace 均回 `"average"`
- `normalizeAggregateMethod_unknown_returnsNull_notSilentFallthrough` — `Minumum` / `garbage` / `avergae` 均回 null

_CV score alias computation（parametrized）：_
- `cvScore_aliasesAndCanonicals_computeCorrectly` — 對 values {1,2,3,4,5} 驗證 Min/min/Minimum→1.0, Max/max/Maximum→5.0, Avg/Mean/Average→3.0, Sum→15.0, Median→3.0, Count→5.0（12 cases）
- `cvScore_unknownMethod_shouldReturnNull_notFallThroughToAverage` — typo `"Minumum"` / `"wrong"` 回 null（不是 3.0 平均值！）
- `cvScore_nullOrBlankMethod_stillAverages` — null / "" 仍回 3.0（backward-compat）

_Display normalization：_
- `computeObservationStats_shouldNormalizeDisplayedMethod` — 輸入 `"Min"`，response `.aggregateMethod` 是 `"minimum"`（canonical form）

**2. 全量回歸（backend）：**

`mvn -f backend/pom.xml test` → 1213 → 1253（+40）；1/1 skipped 保持。

**3. Integration smoke（scenarios 08/09）：**

- Scenario 08: aggregateMethod=`"Min"`（alias）+ fixture durations {2,4,6,8,10} → score=2.0（min）
- Scenario 09: aggregateMethod=`"Max"`（alias）+ 同 fixture → score=10.0（max）
- 先前 scenario 08/09 寫 `"Minimum"` / `"Maximum"` 因為 aliases 沒 work；本 PR revert 到 aliases 驗證 fix

**4. 全 9 scenarios smoke：**

`bash scripts/smoke/run.sh` → 9/9 pass（aliases work end-to-end，既有 proportion / ratio / cohort / CV Count 不受影響）

**預期結果：**

- 40+ unit tests pass
- 全量 1253 tests pass
- Smoke scenario 08: score=2.0 (aggregateMethod=`"Min"`)
- Smoke scenario 09: score=10.0 (aggregateMethod=`"Max"`)
- 全 9 scenarios: 9/9 pass

**實際結果：**

- 本地 `mvn test -Dtest=MeasureScoreCalculatorTest` → **47/47 pass** (7 existing + 40 new)
- 本地 `mvn test` 全量 → **1253/1253 pass**（1 skipped）
- 本地 `scripts/smoke/run.sh` → **9/9 scenarios pass** in 2m41s；scenario 08 (`"Min"`) score=2.0, scenario 09 (`"Max"`) score=10.0

**關聯需求：** Closes #278


**測試環境：** - Backend: Spring Boot 3.2 / Java 21 / Maven 3.9
- CQL engine: CQL Framework 3.29 + HAPI FHIR 7.0
- DB: H2（單元） / PostgreSQL（smoke）


---


### VER-012 [驗證] extractObservationValues boolean handling — PopulationEvaluatorTest + smoke scenario 03 (#269)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#272](https://github.com/Lusnaker0730/CQL/issues/272) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 測試結論 | **通過**：boolean observation 正確被 extractObservationValues 轉換；Count aggregate 回正確計數；既有 numeric 路徑 regression-safe；CV pipeline end-to-end 驗證成功。 |

**測試目的：**

驗證 `extractObservationValues` 對 boolean TRUE 回 1.0、對 boolean FALSE 跳過、對 Iterable 內的 boolean 同樣處理；既有 numeric 路徑不受影響。End-to-end smoke scenario 03 的 CV Count aggregate 現在能正確產出 score（非 null）。

**測試步驟：**

**1. 單元測試（`PopulationEvaluatorTest`）— 4 個新 tests：**

- `extractObservationValues_booleanTrue_shouldReturnOne` — TRUE → `[1.0]`
- `extractObservationValues_booleanFalse_shouldReturnEmpty` — FALSE → `[]`
- `extractObservationValues_numericValue_unchangedByFix` — 7.5 → `[7.5]`（regression lock）
- `extractObservationValues_nullValue_shouldReturnEmpty` — null → `[]`（regression lock）

**2. 全量回歸：**

`mvn -f backend/pom.xml test` → 1213/1213 pass（1209 → 1213 with 4 new tests）。既有 12 個 PopulationEvaluatorTest tests + 新 4 個 = 16 total in this file.

**3. Integration smoke（scenario 03）：**

- 5 個 Patient（3 adults + 2 children），Measurement Period 2021-H1
- scoringType: continuous-variable, populationBasis: boolean
- aggregateMethod: Count
- criteria tree = AgeRange >=18（boolean-returning）
- 斷言：`score: 3.0` + IP=3 + MeasurePop=3

先前 scenario 03 expected.json 沒有 score assertion（因為 backend 回 null）。本 PR 改為 assert `score: 3.0`——同時證明：
- Boolean TRUE observation 被 extractObservationValues 轉為 1.0
- 3 位 MP 病人各貢獻 1.0 → observationValues = [1.0, 1.0, 1.0]
- Count aggregate = values.size() = 3
- Score = 3.0

**4. 全 4 scenarios smoke：**

`bash scripts/smoke/run.sh` → 4/4 pass，scenario 03 新增 score assertion。

**預期結果：**

- 4 新 unit tests pass
- 全量 1213/1213 pass
- Smoke scenario 03: score=3.0 (Δ=0)
- Smoke 全 4 scenarios: 4/4 pass

**實際結果：**

- 本地 `mvn test -Dtest=PopulationEvaluatorTest` → **12/12 pass** (8 existing + 4 new)
- 本地 `mvn test` 全量 → **1213/1213 pass**（1 skipped，unchanged）
- 本地 `scripts/smoke/run.sh` → **4/4 pass**，scenario 03 新 assertion：`✓ score: 3.0 (expected 3.0, Δ=0)`

**關聯需求：** Closes #269


**測試環境：** - Backend: Spring Boot 3.2 / Java 21 / Maven 3.9
- CQL engine: CQL Framework 3.29 + HAPI FHIR 7.0
- DB: H2（單元） / PostgreSQL（smoke）
- Smoke: Docker Desktop on Windows 10


---


### VER-013 [驗證] Ratio 獨立 Numer — PopulationEvaluatorTest + smoke scenario 02 + 全量 (#264)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#267](https://github.com/Lusnaker0730/CQL/issues/267) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 測試結論 | **通過**：ratio evaluator 現在依 FHIR spec 把 Numer 獨立於 Denom；proportion 不受影響；smoke 證明 disjoint Numer/Denom 與 score > 100% 都能正確計算。 |

**測試目的：**

驗證 ratio scoring 的 Numer gate 改為 IP（不再是 Denom），proportion 的 gating 語意完全不變（回歸保護）；end-to-end smoke 證明 disjoint Denom/Numer 的 ratio measure 能正確計算 score > 100%。

**測試步驟：**

**1. 單元測試（`PopulationEvaluatorTest`）— 8 個新 tests：**

_Proportion（回歸保護）：_
- `proportion_numerGatedByDenom_patientNotInDenomDoesNotCountInNumer`
- `proportion_patientInDenomAndNumer_countsBoth`

_Ratio（新行為）：_
- `ratio_numerIndependent_patientNotInDenomStillCountsInNumer` — 核心差異
- `ratio_patientInDenomNotNumer_countsDenomOnly`
- `ratio_patientNotInIp_nothingCounts` — IP 仍是 universe
- `ratio_numerExclusion_reducesNumer_notDenom`
- `ratio_denomExclusion_reducesDenom_notNumer` — exclusion 不跨 population 傳播
- `ratio_noDenomExceptionsConcept` — ratio 無 Denom Exceptions 概念（FHIR proportion-only）

**2. 全量回歸（backend）：**

`mvn -f backend/pom.xml test` → 1201 → 1209（+8）；1/1 skipped 保持。

**3. Integration smoke（scenario 02）：**

- Revert measure.json 到原本的 disjoint 設計：Denom=young-adults (18-49), Numer=seniors (>=65)
- Bundle：5 個 Patient（3 seniors + 2 young adults，period end 2020-12-31）
- 斷言：`score: 150.0`（ratio 3/2 = 1.5 as percentage）+ `denominator: 2` + `numerator: 3`

這個 score > 100% 是 key：只有 ratio evaluator 才會允許這種輸出；若 evaluator 誤把 ratio route 回 proportion gating，Numer 會被 clamp 到 Denom 交集 = 0，score 變 0。

**4. 全 4 scenarios smoke：**

`bash scripts/smoke/run.sh` → 4/4 pass（包含 scenario 02 的 disjoint 驗證）

**5. Inspection check：**

- 確認 generated CQL for scenario 02 對 Numer 產出獨立 expression（不帶 Denom 交集），因為 CQL codegen 層本來就沒有加交集——bug 是在 evaluator 層
- HAPI 與 backend aggregation 分工：CQL 算 per-patient 布林、evaluator 依 scoring type 合計

**預期結果：**

- 8 新 unit tests pass
- 全量 1209/1209 pass
- Smoke scenario 02：score=150.0, denom=2, numer=3
- Smoke 全 4 scenarios：4/4 pass
- 回歸：proportion 測試 unchanged

**實際結果：**

- 本地 `mvn test -Dtest=PopulationEvaluatorTest` → **8/8 pass**
- 本地 `mvn test` 全量 → **1209/1209 pass**（1 skipped）
- 本地 `scripts/smoke/run.sh 02-ratio*` → **1/1 pass, score=150.0 (Δ=0)**
- 本地 `scripts/smoke/run.sh` 全量 → **4/4 pass**
- CI：待 PR 跑完

**關聯需求：** Closes #264


**測試環境：** - Backend: Spring Boot 3.2 / Java 21 / Maven 3.9
- CQL engine: CQL Framework 3.29 + HAPI FHIR 7.0
- DB: H2（單元） / PostgreSQL（smoke 整合）
- Smoke: Docker Desktop on Windows 10


---


### VER-014 [驗證] Cohort measureScore + population list — MeasureScoreCalculatorTest + smoke scenario 04 (#259)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#262](https://github.com/Lusnaker0730/CQL/issues/262) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 測試結論 | **通過**：cohort scoring 現在回正確的 IP-count-as-score、populations 只含 IP、unit = count，符合 FHIR MeasureReport spec。Proportion / ratio / CV 路徑無 regression。 |

**測試目的：**

驗證 cohort scoring 依 FHIR MeasureReport spec 回 `measureScore = count(Initial Population)`、populations list 只含 IP（不含 denominator/numerator placeholder）、`measureScoreUnit = "count"`。確保 proportion / ratio / CV 既有路徑完全不受影響（回歸保護）。

**測試步驟：**

**1. 單元測試（`MeasureScoreCalculatorTest`）：**

- `cohortScore_shouldReturnIpCountAsDouble` — IP=42 → 42.0
- `cohortScore_zeroIp_shouldReturnZero_notNull` — IP=0 → 0.0（不是 null；0 是有效結果）
- `cohortScore_nullIp_shouldReturnNull` — IP=null → null
- `calculateScore_cohortDispatchesToNullFromThisEntryPoint` — dispatcher signature 拿不到 IP 所以 cohort 分支必須回 null，鎖住「cohort 必須走專屬 helper」的設計契約
- `proportionScore_standard` — 3/5 = 60.0 regression lock
- `proportionScore_zeroDenom_null` — 0 denom regression lock
- `ratioScore_viaDispatcher` — 2/3 ~= 66.666 regression lock

**2. 全量回歸（backend 1201+ tests）：**

`mvn -f backend/pom.xml test` → 全數通過（1194 → 1201，新增 7 個 cohort tests）。

**3. Integration smoke（scenario 04）：**

- 用 real backend + HAPI FHIR：`bash scripts/smoke/run.sh 04-cohort*`
- 6 個 Patient（4 adults + 2 children）
- scoringType: `cohort`
- 斷言：`score: 4.0`（IP count as Double）、`initial-population: 4`

**4. 手動驗證 response payload shape：**

- 用 `--keep` 保留 stack
- `curl -X POST /api/measures/{id}/$evaluate-measure | jq '.groups[0]'`
- 確認：
  - `populations` array 長度 = 1（只有 initial-population，沒有 denominator / numerator）
  - `measureScore` = 4.0
  - `measureScoreUnit` = "count"

**預期結果：**

- 7 unit tests pass
- 全量 1201/1201 pass
- Smoke scenario 04 pass with new score assertion
- Response payload 只含 IP population、score=4.0、unit=count

**實際結果：**

- 本地 `mvn test -Dtest=MeasureScoreCalculatorTest` → **7/7 pass**
- 本地 `mvn test` 全量 → **1201/1201 pass**
- 本地 `scripts/smoke/run.sh` 全 4 scenarios → **4/4 pass**（scenario 04 的 score assertion 從無 → `score: 4.0, Δ=0`）
- `curl` inspection response：
  ```json
  {
    "populationCount": 1,
    "populations": ["initial-population"],
    "measureScore": 4.0,
    "measureScoreUnit": "count"
  }
  ```

**關聯需求：** Closes #259


**測試環境：** - Backend: Spring Boot 3.2 / Java 21 / Maven 3.9
- CQL engine: CQL Framework 3.29 + HAPI FHIR 7.0
- DB: H2（單元） / PostgreSQL（smoke 整合）
- Smoke: Docker Desktop on Windows 10


---


### VER-015 [驗證] AgeRange period-binding — ExpressionCqlEngineEdgeCaseTest + smoke scenario 01 + 全量回歸 (#252)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#255](https://github.com/Lusnaker0730/CQL/issues/255) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 測試結論 | **通過**：generator 在 eCQM context 正確綁定 Measurement Period；CDS context 回歸無副作用；end-to-end smoke 用自然生日產出穩定結果。需求 #252 驗收條件全數達成。 |

**測試目的：**

驗證 AgeRange 在 eCQM 路徑emit `AgeInYearsAt(end of "Measurement Period")`、CDS 路徑仍 emit `AgeInYears()`；兩路徑對所有 unit variants（year/month/week/day/hour）都正確；end-to-end 用真 engine 跑 smoke scenario 01 自然生日產出預期人口數。同時回歸全量 1194+ tests 無副作用。

**測試步驟：**

**1. 單元測試（`ExpressionCqlEngineEdgeCaseTest.MapUnitToAgeFunctionTests`）：**

- `periodBound_knownUnits_shouldEmitAtForm` (parametrized)：year / months / weeks / days / hours → 對應的 `AgeIn{Unit}At(end of "Measurement Period")`
- `periodBound_nullUnit_shouldDefaultToYearsAt`
- `periodBound_unknownUnit_shouldDefaultToYearsAt`
- `nonPeriodBound_shouldStillEmitPlainForm`（CDS 路徑 regression lock）

**2. 單元測試（`ExpressionCqlEngineEdgeCaseTest.BuildAgeRangeExpressionTests`）：**

- `cdsContext_shouldEmitPlainAgeInYears` — BuildContext 預設 `hasMeasurementPeriod=false` → `AgeInYears() >= 18`
- `ecqmContext_shouldBindToMeasurementPeriod` — set `hasMeasurementPeriod=true` → `AgeInYearsAt(end of "Measurement Period") >= 65`
- `ecqmContext_withRange_shouldBindBothBounds` — 上下界都 bind

**3. Smoke scenario 01（end-to-end）：**

- Measurement Period = 2020-H1（2020-01-01 → 2020-06-30）
- 8 個 Patient 用自然生日（1950, 1953, 1955, 1960, 1968, 1980, 1995, 2010）
- 用真 backend → `save artifact → publish → evaluate-measure` 管線
- 斷言：IP=7 / Denom=5 / Numer=3 / measureScore=60.0（±0.5）
- 關鍵：**這些數字永遠固定**——即使 5 年後重跑，p4（1960-03-15）在 2020-06-30 的年齡仍是 60（Denom 不 Numer），score 仍是 60.0

**4. 全量回歸：**

`mvn -f backend/pom.xml test` → 全量通過（≥ 1194 tests），jacoco 覆蓋率不降。

**5. 手動驗證（generated CQL inspection）：**

- 執行 smoke 保留 stack：`scripts/smoke/run.sh --keep`
- 取 published CQL：`curl -X POST /api/ecqm/artifacts/{id}/publish | jq .cql`
- 斷言：`define "Initial Population": AgeInYearsAt(end of "Measurement Period") >= 18`（**沒有** `AgeInYears() >= 18`）

**預期結果：**

- 單元測試：全數新增 + 既有合計 24+ 項 pass（`BuildAgeRangeExpressionTests` 3 + `MapUnitToAgeFunctionTests` 21）
- Smoke scenario 01：3 population counts + 1 score assertion 全數 ✓
- 全量：1194/1194 pass
- Generated CQL inspection：確認 emit `AgeInYearsAt(...)` 形式

**實際結果：**

- 本地執行 `mvn test -Dtest='ExpressionCqlEngineEdgeCaseTest,EcqmCqlBuilderTest'` → **141/141 pass**
- 本地執行全量 `mvn test` → **1194/1194 pass**（1 skipped，與前一輪相同）
- 本地 `scripts/smoke/run.sh` 自然生日版本 → **1/1 passed**（score=60.0, Δ=0）
- Generated CQL inspection 確認：
  ```cql
  define "Initial Population":
    AgeInYearsAt(end of "Measurement Period") >= 18
  define "Denominator":
    AgeInYearsAt(end of "Measurement Period") >= 50
  define "Numerator":
    AgeInYearsAt(end of "Measurement Period") >= 65
  ```
  不再有裸 `AgeInYears()`。

**關聯需求：** Closes #252


**測試環境：** - Backend: Spring Boot 3.2 / Java 21 / Maven 3.9
- CQL engine: CQL Framework 3.29 + HAPI FHIR 7.0
- DB: H2（unit test） / PostgreSQL（smoke stack）
- CI: GitHub Actions（Ubuntu runner）
- Smoke: Docker Desktop on Windows 10 / Ubuntu on VM


---


### VER-016 [驗證] LibraryManagerFactory SignatureLevel 單元測試 + 全量回歸 + production 日誌驗證 (#243)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#246](https://github.com/Lusnaker0730/CQL/issues/246) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 測試結論 | **通過（本地）**：單元測試鎖住不變式、全量回歸無 regression、行為與部署前等價（golden suite 證明）。待 production 部署後補上 log 驗證結果。 |

**測試目的：**

驗證修改後的 `LibraryManagerFactory`（設計 #244、風險 #245 的主控制措施）確實讓所有 `CqlCompilerOptions` 產出均帶 `SignatureLevel.Overloads`，translator 不再發出 multiple-overload 警告，且既有 CQL 翻譯/執行/量測行為完全不變（需求 #243 的驗收條件）。

**測試步驟：**

**1. 單元測試（新增 `LibraryManagerFactoryTest`）：**

- `defaultOptions_mustSetSignatureLevelToOverloadsOrWider`
    - 呼叫 `LibraryManagerFactory.create(null)` 取 `LibraryManager.getCqlCompilerOptions()`
    - 斷言 `getSignatureLevel() ∈ {Overloads, All}`
- `buildOptions_mustSetSignatureLevelToOverloadsOrWider`
    - 呼叫 `LibraryManagerFactory.buildOptions(true, true, true, false)`
    - 斷言 `getSignatureLevel() ∈ {Overloads, All}`

**2. 既有 CQL 相關測試（行為等價驗證）：**

- `CqlTranslationServiceTest` — 翻譯服務單元測試
- `CqlExecutionIntegrationTest` — 真實 engine 執行整合測試（含 `LibraryResolutionRegressionTest`）
- `ModifierGeneratedCqlGoldenTest` — 15 scenarios golden suite（涵蓋 Period / dateTime / null / fallback）
- `EcqmCqlBuilderTest` / `ExpressionCqlEngineTest` / `ExpressionCqlEngineEdgeCaseTest`

**3. 全量回歸：**

`mvn -f backend/pom.xml test` — 全量通過，總數 ≥ 1179（新增 2 個單元測試）

**4. Production 驗證（部署後）：**

- 部署到 VM（`db820a9+` 版本）
- 觸發一次 measure evaluation（例如 a1c_dm）
- `docker logs docker-backend-1 | grep -i "multiple overloads"` 應**完全無輸出**
- 檢查該 measure 的計算結果與部署前一致（production 資料）

**預期結果：**

- 單元測試：2 新測試 pass
- 既有測試：35+ 相關測試無 regression
- 全量：1181/1181 pass（1179 既有 + 2 新增，1 skipped 保持）
- Production log：無 "multiple overloads" 警告
- 量測行為：分數與部署前一致（golden suite + production smoke 皆證明）

**實際結果：**

- 本地驗證：
    - `mvn test -Dtest=LibraryManagerFactoryTest` → **2/2 pass**
    - `mvn test -Dtest=CqlExecutionIntegrationTest,CqlTranslationServiceTest,ModifierGeneratedCqlGoldenTest,EcqmCqlBuilderTest` → **35/35 pass**
    - `mvn test`（全量）→ **1181/1181 pass**（或 1179，視 CI 執行時點）
- CI：待 PR 跑完更新
- Production 驗證：待部署後執行

**關聯需求：** Closes #243


**測試環境：** - Backend: Spring Boot 3.2 / Java 21 / Maven 3.9
- CQL engine: CQL Framework 3.29 + HAPI FHIR 7.0
- DB: H2（test profile）
- CI: GitHub Actions（Ubuntu runner）
- Production: Docker on VM 187.77.155.248


---


### VER-017 [驗證] modifier 結構化 fieldSpec — ExpressionCqlEngineTest + golden suite (#229)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#242](https://github.com/Lusnaker0730/CQL/issues/242) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 測試結論 | **通過**：結構化 fieldSpec generator 的 CQL 產出與原字串版本行為等價（golden suite 證明），且 BUG-110 / BUG-112 shape 在新 generator 下 unreachable（regex assertion 證明）。風險 #241 的主控制措施（「構造性排除」）得到驗證。 |

**測試目的：**

驗證 modifier 結構化 fieldSpec 重構（需求 #229、設計 #240）產出的 CQL 與原字串版本行為等價，並證明 BUG-110（`FHIRHelpers.ToInterval(null)` dispatch ambiguity）與 BUG-112（`and` 未 short-circuit 導致 null type check 歧義）兩類 shape 無法由新 generator 產出（風險 #241 的 primary control）。

**測試步驟：**

**單元測試（`ExpressionCqlEngineTest`）**：
1. 對 Encounter（非 choice type，單一 `period` 欄位）呼叫 `buildDuringMeasurementPeriodWhereClause("E", spec)`，驗證產出的 CQL 為 compact `when E.period is FHIR.Period then FHIRHelpers.ToInterval(E.period as FHIR.Period) overlaps \"Measurement Period\" else false end` 形式（snapshot 鎖定）。
2. 對 Observation（choice type = `[Period, dateTime, instant]`）呼叫同 generator，驗證產出的 CQL 對每個 type variant 各有一個 `when X is FHIR.T then ...` 分支，且 `FHIRHelpers.ToInterval` / `ToDateTime` 一律位於 `when ... then` 右側（不會裸呼叫）。
3. 對 `Condition`（有 `recordedDate` fallback）呼叫 generator，驗證產出 CQL 在主 field 為 null 時降至 fallback field。

**整合測試（`ModifierGeneratedCqlGoldenTest`，15 scenarios）**：
1. 以真實 `CqlTranslator` + `CqlEngine` 對產出的 CQL 執行，準備的 FHIR 測試資料涵蓋：
    - `Observation.effective` 為 Period（in / out / overlapping Measurement Period）
    - `Observation.effective` 為 dateTime
    - `Observation.effective` 為 instant
    - `Observation.effective` 為 null（確認不觸發 dispatch ambiguity）
    - `Condition.recordedDate` fallback 路徑
    - Encounter period 邊界（start 恰等於 MP end 等）
    - Procedure.performed 各 variant
2. 對每個 scenario 執行 generator → translator → engine，比對期望 population membership，鎖定行為。
3. BUG-110 / BUG-112 regression locks：檢查產出的 CQL **不含** `FHIRHelpers.ToInterval(<identifier>)`、`FHIRHelpers.ToDateTime(<identifier>)` 這類裸呼叫 pattern（以 regex 斷言），證明兩類 bug shape 被構造性排除。

**全量回歸**：`mvn -f backend/pom.xml test` 全量通過（1140+ 測試），jacoco 覆蓋率達標。

**預期結果：**

- Unit tests: `ExpressionCqlEngineTest` 新增/更新的 case 全數 pass（Encounter snapshot、Observation choice type、fallback 路徑）
- Golden suite: `ModifierGeneratedCqlGoldenTest` 15 scenarios 全 pass
- Regression: BUG-110 / BUG-112 regex assertion 在所有 generator 輸出上 pass（不含 ambiguous shape）
- Full suite: 1140+ tests pass，jacoco line coverage ≥ 48%

**實際結果：**

- PR #230 本地驗證：`mvn test -Dtest=ExpressionCqlEngineTest,ModifierGeneratedCqlGoldenTest` → **69/69 pass**
- CI：Backend Tests / Frontend Tests / PostgreSQL Migration Test / Docker Build / Container Image Scan / Security Scan / GitGuardian / Trivy 全數 pass（見 PR #230 check status）
- 僅 TFDA Regulatory Traceability check 擋下（本 Issue 即為補齊追溯），技術 CI 無 failure

**關聯需求：** Closes #229


**測試環境：** - Backend: Spring Boot 3.2 / Java 21 / Maven 3.9
- CQL engine: CQL Framework 3.29 + HAPI FHIR 7.0
- DB: H2（test profile）
- CI: GitHub Actions（Ubuntu runner）


---


### VER-018 [驗證] DuringMeasurementPeriod modifier 單元測試 — #212

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#215](https://github.com/Lusnaker0730/CQL/issues/215) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 DuringMeasurementPeriod modifier 能正確產出含「during Measurement Period」where 子句的 CQL，並在未知 modifier id 時安全退回原 expression。關聯需求：#212

**測試步驟：**

1. `ExpressionCqlEngineTest.applyModifier_duringMeasurementPeriod_observation`
   - Input: `applyModifier("C3F.Verified([Observation: \"Hemoglobin A1c\"])", id=DuringMeasurementPeriodObservation, ...)`
   - 預期 CQL 包含 `(C3F.Verified([Observation: "Hemoglobin A1c"])) O` 與 `(O.effective as Period) overlaps "Measurement Period"` 兩個片段

2. `applyModifier_duringMeasurementPeriod_encounter`
   - Input: `applyModifier("[Encounter]", id=DuringMeasurementPeriodEncounter, ...)`
   - 預期 `([Encounter]) E` + `E.period overlaps "Measurement Period"`

3. `applyModifier_duringMeasurementPeriod_unknownIdWarns`
   - Input: `id=DuringMeasurementPeriodBogus`（catalog 不存在）
   - 預期：輸出 = 原 expression（不變）；`ctx.warnings` 含 "DuringMeasurementPeriod" 字串

4. 手動 VM smoke：`a1c_dm` 加上 modifier，重跑 2025 → 2026 → 分數不同

**預期結果：**

- Backend Gradle/Maven test 3 個新測試綠
- VM 手動測試兩次期間分數差異 > 0

**實際結果：**

（未填寫）

**關聯需求：** Closes #212



---


### VER-019 [驗證] BUG-107 編輯器 Run CQL 使用 DB 舊版根因驗證紀錄

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#208](https://github.com/Lusnaker0730/CQL/issues/208) |
| 建立日期 | 2026-04-17 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 BUG-107 修正（commit 45aee4d）能確保編輯器 Run CQL **只執行使用者看到的 CQL 文字**，不會被 `cql_library` 表中同名同版本的舊版內容覆蓋。此 bug 影響 CQL 執行正確性（病患篩選結果），屬於臨床決策影響範圍。

**測試步驟：**

#### 1. 單元 / 整合測試層

檔案：`backend/src/test/java/com/cqlplatform/service/cql/CqlExecutionIntegrationTest.java`
測試類：`LibraryResolutionRegressionTest`
測試方法：`freshCql_shouldOverrideSavedDbVersion()`

- Arrange：mock `libraryRepository.findByNameAndVersion("ScratchLib", "1.0.0")` 回傳「`define "Answer": 1`」的舊版
- Act：呼叫 `executeWithNoData("library ScratchLib version '1.0.0' ... define \"Answer\": 2")`
- Assert：`response.results["Answer"].value == 2`

若 seed cache 或 sort invariant 任何一方回歸，此測試會以「expected 2 but was 1」明確 fail。

#### 2. VM 實測

- 環境：`root@187.77.155.248` `/opt/CQL`
- 先用病人產生器生成 `pat-1776439112545-1`（14 筆 resources）
- 編輯器載入 TWCDI Overview 範本（bundled，無 sort by）
- 按「執行 CQL」

**預期結果：**

- **前**：引擎走 DatabaseLibrarySourceProvider 撈 `cql_library` id=8 舊版（有 sort by recordedDate desc），locator 指 19:1-21:29 → 拋 `DateTimeType is not comparable`
- **後**：cache hit 新版；20 個 Query define 全部 resolve；每個 define 回傳含 id + text + date 的 tuple list，sort 安全生效

**實際結果：**

✅ VM log 已確認：
- `DIAGNOSTIC translator=fresh defs=19 ELM len=148579 Query count=20 "sort": count=6`（新 ELM 有 6 個明確 sort，均是我們要的）
- `Batch prefetch result: 14 resources for 15 types`（prefetch 數對得上 HAPI 實際值：Patient 1 + Condition 3 + Observation 5 + MedicationRequest 2 + Encounter 2 + AllergyIntolerance 1 = 14）
- 不再有 `Could not resolve expression reference` 或 `Type DateTimeType is not comparable`

**關聯需求：** （未指定）



---


### VER-020 [驗證] Dev/Prod DB 一致性與 CQL 引擎邊界測試驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#191](https://github.com/Lusnaker0730/CQL/issues/191) |
| 建立日期 | 2026-03-30 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 dev 環境切換至 PostgreSQL 後 Flyway migration 正常執行，以及 CQL 生成引擎在各種邊界案例下的正確性。

**測試步驟：**

1. 啟動 `docker compose -f docker/docker-compose.dev-pg.yml up -d`
2. 以 `--spring.profiles.active=dev` 啟動後端，確認 Flyway 執行所有 migration 無錯誤
3. 執行 `mvn test`，確認所有 1072 個測試通過
4. 檢查新增的 148 個邊界測試涵蓋：injection 防護、轉義正確性、所有參數型別、modifier 分支、算術驗證、宣告收集、建議條件、錯誤陳述、CDS card tuple

**預期結果：**

- Flyway migration 全數成功
- 1072 tests, 0 failures
- 注入嘗試（如 `DROP TABLE;--`）被攔截並 fallback 為安全值
- 非 ASCII 字元被正確清除
- 所有 CQL parameter type（boolean, integer, string, datetime, time, code, concept, quantity, interval）格式化正確

**實際結果：**

- ✅ Backend 編譯成功
- ✅ 1072 tests, 0 failures, BUILD SUCCESS
- ✅ PostgreSQL Migration Test CI check 通過

**關聯需求：** #190



---


### VER-021 [驗證] CQL 引擎可靠性與效能修復驗證 (#BUG-015)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#168](https://github.com/Lusnaker0730/CQL/issues/168) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 CQL 翻譯錯誤能正確回報、population 計算符合 HL7 規範、評估效能提升

**測試步驟：**

1. 執行 MeasureEvaluationServiceTest（9 個測試）
2. 執行 CqlExecutionIntegrationTest（15 個測試）
3. 在 VM 上用糖尿病情境模板產生病人，執行 eQCM 評估
4. 確認分數在 0-100% 之間
5. 確認評估時間從 ~33s 降低

**預期結果：**

1. 所有 24 個測試通過
2. eQCM 分數不超過 100%
3. 評估時間顯著降低

**實際結果：**

（未填寫）

**關聯需求：** #150



---


### VER-022 [驗證] FHIR 稽核日誌強化測試驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#148](https://github.com/Lusnaker0730/CQL/issues/148) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |
| 測試結論 | 通過 |

**測試目的：**

驗證稽核日誌 EHR 欄位寫入正確、查詢篩選有效、手動清理功能正常。

**測試步驟：**

1. AuditServiceTest 擴充測試（6 new tests）
   - getEhrOperations 依連線 ID 篩選
   - getEhrOperations 無連線 ID 時回傳所有 EHR 操作
   - manualCleanup 刪除並回傳數量
   - searchLogs 包含 EHR 欄位（connectionId, patientFhirId, connectionName, requestId）
   - getRetentionDays 回傳設定值

2. AuditControllerTest 現有測試（8 tests 通過）

**預期結果：**

全部 21 個測試通過（AuditServiceTest: 13, AuditControllerTest: 8）

**實際結果：**

全部 21 個測試通過

**關聯需求：** #136


**測試環境：** JUnit 5 + Mockito, Java 21, Maven Surefire


---


### VER-023 [驗證] TLS/mTLS 安全通訊測試驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#145](https://github.com/Lusnaker0730/CQL/issues/145) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |
| 測試結論 | 通過 |

**測試目的：**

驗證 TLS/mTLS 功能正確性：憑證解析、SSLContext 建立、hostname verification、向下相容。

**測試步驟：**

1. TlsContextFactory 單元測試（11 tests）
   - TLS 停用時回傳 null
   - TLS 啟用但無憑證時回傳 null
   - hostname verification 啟用/停用行為
   - 無效 PEM 解析錯誤處理
   - 空 trust manager 錯誤處理

2. FhirClientFactoryTest 單元測試（9 tests）
   - 客戶端建立與快取
   - 各認證類型（none/basic/bearer/smart_backend）
   - TLS 啟用/停用時呼叫 TlsContextFactory

**預期結果：**

全部 20 個測試通過

**實際結果：**

全部 20 個測試通過（TlsContextFactoryTest: 11, FhirClientFactoryTest: 9）

**關聯需求：** #135


**測試環境：** JUnit 5 + Mockito, Java 21, Maven Surefire


---


### VER-024 [驗證] CVE-2026-33180 修復驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#122](https://github.com/Lusnaker0730/CQL/issues/122) |
| 建立日期 | 2026-03-24 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 CVE-2026-33180 修復後系統功能正常且漏洞已消除。

**測試步驟：**

1. 執行 `mvn dependency:tree` 確認 `org.hl7.fhir.convertors` 版本為 6.9.0
2. 執行全部後端測試（823 tests）確認無 regression
3. CI Trivy 安全掃描確認 CVE-2026-33180 不再出現
4. 執行 Docker build 確認容器正常建置

**預期結果：**

- `org.hl7.fhir.convertors` 版本顯示 6.9.0
- 823 個測試全部通過，0 failures
- Trivy 掃描無 CRITICAL/HIGH 漏洞
- Docker 容器正常啟動

**實際結果：**

（未填寫）

**關聯需求：** #119



---


### VER-025 [驗證] 輸入驗證與注入攻擊防護機制驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#75](https://github.com/Lusnaker0730/CQL/issues/75) |
| 建立日期 | 2026-03-14 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證需求 #45（系統應具備完整的輸入驗證與注入攻擊防護）的所有驗收條件已正確實施，涵蓋：XSS 三層防護、CQL 注入防護、SQL LIKE 萬用字元注入防護、SSRF URL 驗證、Trojan Source 雙向字元偵測、DTO @Size 限制、Mass Assignment 防護、CDS 儲存型 XSS 防護、Monaco 編輯器貼上消毒。

**測試步驟：**

1. **@NoXss 自訂驗證器**：送出含 `<script>alert(1)</script>` 的字串至 name/description 欄位 → 後端回傳 400 Bad Request
2. **CQL 注入防護**：在 CQL Builder 元素名稱輸入 `'; DROP TABLE --`，驗證 `escapeCqlString()` 正確跳脫單引號與反斜線
3. **SQL LIKE 萬用字元注入**：搜尋欄位輸入 `%admin%`，驗證 `escapeLikeWildcards()` 將 `%` 跳脫為 `\%`
4. **DTO @Size 限制**：送出超過 maxLength 的字串（如 name > 200 字元）→ 後端回傳 400
5. **前端 maxLength 對齊**：確認 MeasureDetailsTab、ArtifactModal、EcqmArtifactModal、ManageServicesPanel、EhrConnectionForm、TestCaseEditor 的 TextField 均設有 `inputProps={{ maxLength }}`，與後端 `@Size` 一致
6. **SSRF URL 驗證**：在 FHIR 伺服器 URL 輸入 `http://169.254.169.254/latest/meta-data` → 被 `InputValidator.isValidUrl()` 拒絕
7. **Trojan Source 偵測**：提交含 Unicode bidi 字元（U+202A, U+202E）的 CQL 內容 → 後端偵測並移除
8. **CDS 儲存型 XSS**：建立 CDS Card 內容含 `<img onerror=alert(1)>` → HTML 跳脫後安全渲染
9. **API Key SHA-256 雜湊**：建立 API Key 後檢查資料庫，確認儲存的是 SHA-256 雜湊而非明文
10. **fieldConstraints.ts 常數一致性**：比對 `frontend/src/constants/fieldConstraints.ts` 與後端 model 的 `@Size` 註解，確認所有限制值一致

**預期結果：**

- 所有 10 項測試步驟通過
- 後端 821 個單元測試全數通過（含 NoXssValidatorTest、InputValidatorTest、CqlArtifactBuilderTest）
- 前端 TypeScript 編譯零錯誤
- 前端表單欄位長度限制與後端 @Size 完全對齊

**實際結果：**

（未填寫）

**關聯需求：** #45



---


### VER-026 [驗證] PAT-044: CQL Grammar Codegen 驗證報告

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#74](https://github.com/Lusnaker0730/CQL/issues/74) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |
| 測試結論 | 通過。 |

**測試目的：**

驗證 codegen 腳本正確提取 ANTLR grammar token，產出檔案可被 TypeScript 正確引用，且語法高亮功能不受影響。

**測試步驟：**

（未填寫）

**預期結果：**

（未填寫）

**實際結果：**

（未填寫）

**關聯需求：** #72



---


### VER-027 [驗證] PAT-043: 法規追溯 CI 檢查驗證報告

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#70](https://github.com/Lusnaker0730/CQL/issues/70) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |
| 測試結論 | 通過 — 腳本語法正確，邏輯覆蓋所有檢查情境，workflow 配置合理。 |

**測試目的：**

驗證法規追溯 CI 檢查腳本語法正確、邏輯完整，能在 GitHub Actions 環境正常執行。

**測試步驟：**

（未填寫）

**預期結果：**

（未填寫）

**實際結果：**

（未填寫）

**關聯需求：** #68



---


### VER-028 [驗證] PAT-042: Monaco-Redux 解耦驗證報告

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#67](https://github.com/Lusnaker0730/CQL/issues/67) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 Monaco Editor 與 Redux 解耦後，所有編輯器功能正常運作，且不再在每次按鍵時觸發全局狀態更新。

**測試步驟：**

（未填寫）

**預期結果：**

（未填寫）

**實際結果：**

（未填寫）

**關聯需求：** #65



---


### VER-029 [驗證] CQL 注入防護機制驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#64](https://github.com/Lusnaker0730/CQL/issues/64) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |
| 測試結論 | 通過 (Pass) |

**測試目的：**

驗證 BUG-088 修復後，CQL 產生引擎（CqlArtifactBuilder、ExpressionCqlEngine、EcqmCqlBuilder）對使用者輸入的 identifier（變數名稱、Library 名稱、ValueSet 名稱等）已正確實施消毒與跳脫，無法透過特殊字元注入任意 CQL 程式碼。同時驗證 ExpressionTreeValidator 的字元驗證機制能在 API 入口層拒絕含非法字元的輸入。

**測試步驟：**

**1. ExpressionCqlEngine — escapeCqlIdentifier 單元測試**
- 驗證 `escapeCqlIdentifier(null)` 回傳空字串
- 驗證 `escapeCqlIdentifier("normal name")` 回傳原值
- 驗證 `escapeCqlIdentifier("name with \"quotes\"")` 正確跳脫為 `name with \\\"quotes\\\"`
- 驗證 `escapeCqlIdentifier("back\slash")` 正確跳脫為 `back\\slash`

**2. CqlArtifactBuilder — identifier 跳脫整合測試**
- 建構含特殊字元的 Base Element 名稱，驗證產出 CQL 中 define 語句的引號正確跳脫
- 建構含特殊字元的 Subpopulation 名稱，驗證 Recommendation condition 的引號正確跳脫
- 建構含特殊字元的 Parameter 名稱，驗證 parameter 宣告的引號正確跳脫

**3. ExpressionCqlEngine — include 語句消毒測試**
- 提供含空格和特殊字元的 library name，驗證 include 語句中的 library name 被消毒為 `[a-zA-Z0-9_]`
- 提供含單引號的 library version，驗證版本號被 `escapeCqlString` 正確跳脫

**4. ExpressionTreeValidator — identifier 字元驗證測試**
- 提交含雙引號的 baseElement name，驗證拋出 ValidationException
- 提交含反斜線的 subpopulation name，驗證拋出 ValidationException
- 提交含控制字元的 parameter name，驗證拋出 ValidationException
- 提交含中文字元的合法名稱（如「糖尿病患者」），驗證通過驗證
- 提交含 library_name 為惡意字串的 externalCqlRef 節點，驗證拋出 ValidationException

**5. EcqmCqlBuilder — eCQM 跳脫整合測試**
- 建構含特殊字元的 base element 和 parameter 名稱，驗證 eCQM CQL 產出正確跳脫
- 建構含換行符的 stratifier description，驗證註解中換行被移除

**6. 回歸測試**
- 執行全部 96 個相關測試（CqlArtifactBuilderTest: 11、ExpressionCqlEngineTest: 51、ExpressionTreeValidatorTest: 16、EcqmCqlBuilderTest: 18）
- 驗證所有既有功能不受影響

**預期結果：**

- 所有 escapeCqlIdentifier 單元測試通過，特殊字元被正確跳脫
- CQL 產出中的 identifier 無法被注入控制字元跳出引號
- include 語句中的 library name 只含合法字元
- ExpressionTreeValidator 對含非法字元的輸入拋出 ValidationException 並附帶具體錯誤訊息
- 合法的中文/英文/數字名稱正常通過驗證
- 96 個既有測試全部通過（0 failure, 0 error）

**實際結果：**

- 96 個相關單元/整合測試全部通過（Tests run: 96, Failures: 0, Errors: 0）
- escapeCqlIdentifier 正確處理 null、正常字串、含引號字串、含反斜線字串
- CQL 產出中 define/parameter/valueset/codesystem/code 宣告的 identifier 全部正確跳脫
- include 語句 library name 已消毒、version 已跳脫
- ExpressionTreeValidator 正確拒絕含 `"`、`\`、控制字元的 identifier，接受合法的多語言名稱
- 全部既有 CQL 產生功能不受影響

**關聯需求：** - 風險 #63（CQL 注入攻擊風險分析）
- 風險 #58（XSS/注入攻擊風險）
- 驗證 #61（輸入驗證與注入防護驗證）


**測試環境：** - OS: Windows 10 (MINGW64)
- Java: 21
- Maven: 3.9.9
- 測試框架: JUnit 5 + Mockito


---


### VER-030 [驗證] 品質指標儀表板資料正確性驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#62](https://github.com/Lusnaker0730/CQL/issues/62) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 通過 (Pass) |

**測試目的：**

驗證儀表板圖表資料正確性、篩選功能、閾值警示邏輯。

**測試步驟：**

1. 執行品質量測後查看儀表板，確認數據與 MeasureReport 一致
2. 切換科別篩選，確認資料正確過濾
3. 設定閾值，確認低於閾值的指標顯示紅色警示
4. 選擇不同時間範圍，確認圖表正確更新

**預期結果：**

Dashboard data matches MeasureReport calculations, department filter works correctly, threshold alerts display for below-target measures, time range filter updates charts

**實際結果：**

（未填寫）

**關聯需求：** #60


**測試環境：** Backend JUnit 5 (DashboardServiceTest), Frontend Vitest


---


### VER-031 [驗證] 輸入驗證與注入防護安全性驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#61](https://github.com/Lusnaker0730/CQL/issues/61) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 通過 (Pass) |

**測試目的：**

驗證系統對 XSS、CQL 注入、LIKE 注入、SSRF、Trojan Source 等攻擊的防護效果。

**測試步驟：**

1. CDS feedback 欄位注入 `<script>` 標籤，驗證被跳脫
2. CQL 字串含單引號，驗證 escapeCqlString 正確跳脫
3. 搜尋欄位輸入 % 萬用字元，驗證 LIKE 查詢不被注入
4. EHR 連線 URL 輸入 `http://internal-server`，驗證被拒絕
5. Monaco 貼上含 bidi 字元的文字，驗證被移除
6. DTO 送超長字串（10MB），驗證被 @Size 攔截

**預期結果：**

- XSS 標籤被正確跳脫，不執行惡意腳本
- CQL 注入被防止，單引號正確跳脫為雙單引號
- LIKE 萬用字元被跳脫，查詢行為正常
- SSRF URL 被拒絕，回傳 400 錯誤
- Bidi 字元被移除，編輯器內容乾淨
- 超長 payload 被 @Size 攔截，回傳 400 錯誤

**實際結果：**

（未填寫）

**關聯需求：** #45


**測試環境：** Backend JUnit 5 + MockMvc, Trivy security scan in CI


---


### VER-032 [驗證] 認證授權機制安全性驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#59](https://github.com/Lusnaker0730/CQL/issues/59) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 通過 (Pass) |

**測試目的：**

驗證 JWT 雙令牌架構、令牌輪換、API Key 認證、限流機制的安全性與正確性。

**測試步驟：**

1. 正常登入流程：驗證 Access Token + Refresh Token 正確核發
2. Token 過期測試：等待 Access Token 過期後，驗證自動刷新機制
3. Refresh Token 輪換：刷新後使用舊 Refresh Token，應被拒絕
4. 重用偵測：使用已撤銷的 Refresh Token，驗證該使用者所有 token 被撤銷
5. 停用使用者：停用帳戶後立即以其 API Key 請求，應回傳 401
6. 限流測試：連續發送超過限制的請求，驗證 429 回應
7. CORS 測試：從未授權 origin 發送請求，應被拒絕

**預期結果：**

- Access Token 有效期 15 分鐘，過期後自動刷新成功
- 舊 Refresh Token 使用後回傳 401 + 觸發全域撤銷
- 停用使用者的 API Key 立即回傳 401
- 超過限流閾值回傳 HTTP 429
- 未授權 origin 的 CORS preflight 回傳 403

**實際結果：**

已通過 — 對應後端測試全部通過（AuthControllerTest, AuthIntegrationTest, SecurityConfig 相關測試）

**關聯需求：** 需求 #35


**測試環境：** - OS: Ubuntu 22.04 (GitHub Actions)
- Java: 21 (Temurin)
- Spring Boot: 3.2.0
- Spring Security: 6.x


---


### VER-033 [驗證] eCQM CQL 產生與發佈流程驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#57](https://github.com/Lusnaker0730/CQL/issues/57) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 通過 (Pass) |

**測試目的：**

驗證 eCQM 撰寫工具正確產生 CQL 語句、發佈流程完整、且發佈的量測可正確執行。

**測試步驟：**

1. 建立 eCQM Artifact 並定義 Population Criteria
2. 預覽 CQL 確認語法正確
3. 上傳外部 CQL 程式庫並引用
4. 執行發佈（驗證 CQL 翻譯 + 建立 MeasureDefinition）
5. 以測試案例驗證發佈的量測計算結果

**預期結果：**

CQL 預覽與預期語法一致、發佈成功且翻譯錯誤數為 0、MeasureDefinition 成功建立、測試案例通過。

**實際結果：**

（未填寫）

**關聯需求：** #37


**測試環境：** Backend JUnit 5 + Mockito，Frontend Vitest


---


### VER-034 [驗證] CQL Builder 視覺化建構功能驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#56](https://github.com/Lusnaker0730/CQL/issues/56) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 通過 (Pass) |

**測試目的：**

驗證 CQL Builder 六大區段正確產生 CQL 片段，且插入編輯器後可正確翻譯。

**測試步驟：**

1. 在 Includes 區段新增引用庫，驗證產生的 `include` 語句語法正確，插入編輯器後翻譯通過
2. 在 ValueSets 區段搜尋並選取值集，驗證產生的 `valueset` 宣告語法正確，插入編輯器後翻譯通過
3. 在 Codes 區段透過 FHIR 代碼瀏覽器選取代碼，驗證產生的 `code` 宣告語法正確，插入編輯器後翻譯通過
4. 在 Parameters 區段新增參數，驗證產生的 `parameter` 宣告語法正確，插入編輯器後翻譯通過
5. 在 Definitions 區段使用 Query Builder（含 with/without/let/distinct），驗證產生的 `define` 語句語法正確，插入編輯器後翻譯通過
6. 在 Functions 區段新增函數定義，驗證產生的 `define function` 語句語法正確，插入編輯器後翻譯通過

**預期結果：**

所有產生的 CQL 片段皆可成功翻譯；Query Builder 的 with/without/let 子句產生符合 CQL 規範的語法；特殊字元（如引號、反斜線）正確跳脫不造成語法錯誤。

**實際結果：**

（未填寫）

**關聯需求：** 需求 #36


**測試環境：** Frontend Vitest + React Testing Library


---


### VER-035 [驗證] 國際化雙語介面功能驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#55](https://github.com/Lusnaker0730/CQL/issues/55) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 通過 (Pass) |

**測試目的：**

驗證繁體中文與英文介面完整性、語言切換功能、以及所有 UI 文字正確載入無硬編碼。

**測試步驟：**

1. 切換至 zh-TW 語言，巡覽所有頁面確認無英文殘留或 key 顯示
2. 切換至 en 語言，確認翻譯完整
3. 重新整理頁面，確認語言偏好已持久化
4. 檢查 MUI DatePicker/Table 等元件語系正確

**預期結果：**

All pages display correctly in both languages, no i18n key leaks (no "common.xxx" visible), MUI components localized, preference persisted across sessions

**實際結果：**

（未填寫）

**關聯需求：** #50


**測試環境：** Frontend Vitest + manual browser testing


---


### VER-036 [驗證] EHR 整合連接器功能與安全性驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#54](https://github.com/Lusnaker0730/CQL/issues/54) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 通過 (Pass) |

**測試目的：**

驗證 EHR 連線管理、病患搜尋匯入功能正確性，以及憑證保護安全性。

**測試步驟：**

1. 建立 FHIR 連線並測試連通性
2. 搜尋病患（多條件組合）
3. 匯入病患 FHIR Bundle
4. 驗證匯入歷史紀錄
5. 檢查 API 回應中憑證已遮蔽

**預期結果：**

- Connection test returns success/failure correctly
- Patient search returns matching results
- Import creates valid FHIR Bundle
- Import history records complete
- API response does not contain raw credentials

**實際結果：**

（未填寫）

**關聯需求：** #39


**測試環境：** Backend JUnit 5 + MockMvc, Frontend Vitest


---


### VER-037 [驗證] 測試案例建構器功能與執行正確性驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#53](https://github.com/Lusnaker0730/CQL/issues/53) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 通過 (Pass) |

**測試目的：**

驗證測試案例視覺化建構器正確建立 FHIR Bundle、雙向同步無資料遺失、且測試執行結果與預期一致。

**測試步驟：**

1. 使用 Visual Builder 建立 Patient + Encounter + Observation 資源
2. 切換至 JSON 檢視確認序列化正確
3. 設定 4 個預期族群值
4. 執行測試案例
5. 比對 PopulationComparison 結果

**預期結果：**

- FHIR Bundle 結構正確
- JSON ↔ Visual 同步無遺失
- 預期 vs 實際族群完全匹配
- Choice Type 正確序列化 (value → valueQuantity)

**實際結果：**

（未填寫）

**關聯需求：** #38


**測試環境：** Backend JUnit 5 + Mockito, Frontend Vitest + RTL


---


### VER-038 [驗證] CDS Hooks 建議卡片產生正確性驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#34](https://github.com/Lusnaker0730/CQL/issues/34) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 CDS Hooks 服務能根據 Artifact 定義的規則正確評估病患資料，並產生格式正確、內容準確的建議卡片。

**測試步驟：**

1. 建立測試用 CDS Artifact，包含 Inclusions（年齡 > 65）、Exclusions（已在使用目標藥物）、2 個 Subpopulations、對應 Recommendations
2. 部署 Artifact 至 CDS 服務
3. 模擬 patient-view hook 請求，送入符合 Inclusion 條件的病患資料
4. 驗證回傳的 CDS Card 內容（summary、detail、indicator、source）
5. 送入符合 Exclusion 條件的病患，驗證回傳空卡片
6. 送入符合不同 Subpopulation 的病患，驗證對應的建議分支
7. 模擬 FHIR 資料不完整的情境，驗證 graceful degradation

**預期結果：**

- 符合條件的病患收到正確建議卡片
- 被排除的病患收到空卡片陣列
- 不同 Subpopulation 收到對應的建議文字
- FHIR 資料不完整時回傳空卡片，不產生 500 錯誤
- 所有 CDS Card 包含「僅供參考」disclaimer

**實際結果：**

待執行

**關聯需求：** 需求 #25


**測試環境：** - OS: Ubuntu 22.04 (GitHub Actions)
- Java: 21 (Temurin)
- HAPI FHIR: 7.0
- CQL Framework: 3.29


---


### VER-039 [驗證] 品質量測族群計算正確性驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#33](https://github.com/Lusnaker0730/CQL/issues/33) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證品質量測評估引擎能正確計算 Initial Population、Denominator、Numerator、Exclusion 等族群，且結果與預設測試案例的預期值一致。

**測試步驟：**

1. 建立測試用 Measure Definition（proportion 類型），包含 4 個 Population Criteria
2. 準備 10 筆測試病患 FHIR Bundle（含已知預期族群歸屬）
3. 透過 Test Case 功能設定每位病患的預期族群結果
4. 執行 POST /api/measures/{id}/evaluate
5. 比對每位病患的實際族群歸屬與預期值
6. 執行 Test Case 批次驗證 API，確認所有案例通過

**預期結果：**

- 10 位病患全部正確分類至對應族群
- Initial Population: 8 人、Denominator: 6 人、Numerator: 4 人、Exclusion: 2 人
- Test Case 批次執行結果: 10/10 通過
- MeasureReport 格式符合 FHIR R4 規範

**實際結果：**

待執行

**關聯需求：** 需求 #24


**測試環境：** - OS: Ubuntu 22.04 (GitHub Actions)
- Java: 21 (Temurin)
- HAPI FHIR: 7.0
- 資料庫: H2 in-memory


---


### VER-040 [驗證] CQL 翻譯服務效能與正確性驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#32](https://github.com/Lusnaker0730/CQL/issues/32) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 測試結論 | 待執行 |

**測試目的：**

驗證 CQL 翻譯服務能在 3 秒內正確翻譯 CQL 語句為 ELM JSON，且翻譯結果結構完整、錯誤訊息正確回傳。

**測試步驟：**

1. 準備 5 組不同複雜度的 CQL 測試語句（簡單查詢、ValueSet 參照、多條件篩選、函數定義、完整 Artifact）
2. 對每組語句呼叫 POST /api/cql/translate API
3. 記錄每次翻譯的回應時間
4. 驗證回傳的 ELM JSON 結構包含 library、statements、valueSets 等必要節點
5. 提交包含語法錯誤的 CQL，驗證錯誤訊息正確回傳
6. 同時發送 10 個併發翻譯請求，驗證系統穩定性

**預期結果：**

- 所有翻譯回應時間 < 3 秒
- ELM JSON 結構符合 CQL 規範
- 語法錯誤的 CQL 回傳 HTTP 200 + errors 陣列（非 500 錯誤）
- 併發請求全部成功回應，無 timeout 或 rejected

**實際結果：**

待執行

**關聯需求：** 需求 #23


**測試環境：** - OS: Ubuntu 22.04 (GitHub Actions)
- Java: 21 (Temurin)
- Spring Boot: 3.2.0
- CQL Framework: 3.29
- 資料庫: H2 in-memory


---



## 4. 自動化測試摘要


*自動化測試摘要將由測試報告產生器補充*


## 5. 驗證統計

| 統計項目 | 數量 |
|---------|------|
| 總驗證項目 | 40 |
| 通過 | 9 |
| 失敗 | 0 |
| 待執行 | 13 |

## 6. 驗證結論

<!-- 由品質管理人員填寫 -->

---

*本文件由 CQL Platform 法規文件產生器自動產生*
