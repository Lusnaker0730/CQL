# 風險管理報告 (Risk Management Report)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-RMR-1.0.0 |
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

本文件彙整 CQL Platform 的風險分析紀錄，依據 ISO 14971 醫療器材風險管理標準。

## 2. 風險評估矩陣

| | 1-可忽略 | 2-輕微 | 3-嚴重 | 4-危急 | 5-災難性 |
|---|---------|--------|--------|--------|---------|
| **5-經常** | 中 | 高 | 高 | 極高 | 極高 |
| **4-可能** | 低 | 中 | 高 | 高 | 極高 |
| **3-偶爾** | 低 | 中 | 中 | 高 | 高 |
| **2-很少** | 低 | 低 | 中 | 中 | 高 |
| **1-極不可能** | 低 | 低 | 低 | 中 | 中 |

## 3. 風險分析紀錄


### RISK-001 [風險] Retrieve cache memoization + ELM persist — scope 與安全分析

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#339](https://github.com/Lusnaker0730/CQL/issues/339) |
| 建立日期 | 2026-04-22 |
| 狀態 | open |
| 嚴重度 | - Cache key 漏參數：**中（Moderate）** — 跨 context 拿錯資料有 clinical impact
- Cache scope 跨 request：**高（High）** — 潛在 PHI leak（但目前設計是 per-instance，不會發生）
- Engine cache 重疊：**低** — 效能影響、無正確性問題
- ELM null 寫入：**低** — 雙重保險（validation 失敗早 throw）
- Scenario 鎖死：**低** — 文件化即可 |
| 發生機率 | 低（Low）：
- Cache key 簽名涵蓋全部 12 個 retrieve() 參數，unit test 可擴加
- Scope 設計 per-instance，經 review
- HAPI engine 短期不會加
- Validation fail 的 throw 路徑覆蓋在 `EcqmPublishServiceTest`（若存在） |

**危害情境：**

1. **Cache key signature 不完整 → 錯誤資料共用**：`TracingRetrieveProvider` 的 cache key 若漏掉某個 retrieve() 參數，兩個語意不同的 retrieve 會 collide → 第二次呼叫回錯誤的資料。例如漏了 `contextValue`（patient ID），同 request 內兩個 patient context 的資料會混淆。
2. **Cache scope 誤設跨 request**：若 cache 從 per-instance 改成 static，不同使用者 / 不同病人的 PHI 可能混淆。
3. **HAPI engine 將來自己加 cache**：若 engine 未來開啟自己的 retrieve cache（目前沒有），provider 層 cache 變多餘，但不會錯誤。
4. **`setElmJson(null)` 寫入**：若 translator 失敗但 `validateCql()` 回傳 success=false、elmJson=null，當前修 `measureDef.setElmJson(validation.getElmJson())` 會寫 null 進 DB。實際 publish 流程若 validation 失敗則早在 `throw new CqlGenerationException(...)` 就中斷，不會跑到 setElmJson — 但這個假設不明示。
5. **Smoke scenario 17 hardcodes 1 retrieve**：若未來有合理原因要拆多個 retrieve（e.g. 效能優化 split by code system），scenario 17 會紅但意圖不 regression。需 `_note` 清楚說明「這個數字是 cache contract，不是 count 邏輯」。

**風險控制措施：**

（未填寫）

**殘餘風險：**

可接受：最高風險（cache scope 跨 request）在當前設計下不會發生；其他風險有代碼 / 文件 / 測試三層保險。


**關聯項目：** #337 #338


---


### RISK-002 [風險] Debug mode per-expression timing 資訊損失

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#334](https://github.com/Lusnaker0730/CQL/issues/334) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 嚴重度 | 低（Low）— 只影響 debug 診斷精度、不影響 production eval 結果、不影響臨床決策 |
| 發生機率 | 中（Medium）— batch 成功是 common path，author 會立即注意到「所有 expression 都 0ms」看不出熱點。 |

**危害情境：**

1. **Per-expression timing 精度降低**：batch 成功時所有 `evaluationTimeMs=0`，author 看不到哪個 expression 慢。若有單一 expression 真的很慢（e.g. 巨量 retrieve 或巨量 boolean expansion），author 無法在 debug panel 即時診斷。
2. **Fallback 路徑仍保留舊行為**：batch fail 時進 per-expression loop，retrieve 可能重複 N 次。若 fallback 頻繁觸發（例如 FHIRHelpers overload ambiguity），authors 仍會看到舊的 10× 症狀。
3. **前端 UI 不知如何顯示 0ms**：`RetrieveTraceTable` 顯示 `0ms` 可能看起來像 bug。需要 i18n 提示「批次模式下 per-expression 未測」。
4. **Batch fail 後無 per-expression timing 累加到 totalTimeMs**：totalTimeMs 由 wall-clock (`System.currentTimeMillis() - startTime`) 算，fallback 後還是正確的 — 不是風險但值得記錄。

**風險控制措施：**

（未填寫）

**殘餘風險：**

可接受。Debug mode 精度損失換取 (a) production-accurate retrieve behavior、(b) N× 少的 FHIR server hits、(c) 消除 author 被假訊號誤導的 UX 陷阱。Phase 2 可補 engine-level timing。


**關聯項目：** #332 #333


---


### RISK-003 [風險] PHI 加密 Phase 1 — key 管理 / column widening / ciphertext inflation

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#329](https://github.com/Lusnaker0730/CQL/issues/329) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 嚴重度 | 中（Moderate）— 主要風險是 (1) key loss 這個對稱加密固有問題，blast radius 大；其他都是可偵測的 operational 問題。 |
| 發生機率 | 低（Low）— (1) key loss 需人為失誤 + backup 機制缺失同時發生；(2)(3) migration/column 問題在 smoke + CI 會先抓到；(4) 需有 raw SQL 寫入權限的攻擊者，threat model 已不同；(5) 新 dev 加 repo query 會被 code review 抓到（且測試會立即失敗）；(6) 環境設置文件可明列 `ENCRYPTION_KEY` 必要性。 |

**危害情境：**

1. **Master key 遺失 → 資料不可復原**：`ENCRYPTION_KEY` env var 一旦遺失，所有 `ENC:` 加密資料永久無法解密。Production DB 整包價值歸零（plaintext 既有 row 仍可讀，但新寫入的 ENC: row 死絕）。這是對稱加密不可避免的 trade-off，但 blast radius 對臨床系統是災難級。
2. **Column widening 失敗 → 寫入失敗 silent**：V55 在 `patient_import.{patient_fhir_id, patient_identifier, patient_name}` 和 `sandbox_preset.patient_id` 把 VARCHAR 擴成 TEXT。若 migration 執行時有 concurrent write → lock 爭奪 → 可能 fail。Flyway 單 tx 應該可以抓到，但 Postgres online-DDL 行為需驗。
3. **Ciphertext inflation > 原 column size**：8 個欄位中有 3 個原本是 VARCHAR(200/500)，plaintext 接近上限時 ciphertext (~1.5×) 超過 upper bound → write 失敗。V55 migration 擴成 TEXT 解決此風險，但 migration 延後或失敗會導致 production write 錯誤。
4. **Encrypt-on-read fallback 被誤用成 silent-downgrade**：converter 看到無 `ENC:` prefix 就當明文讀回。若攻擊者可以寫 raw SQL 塞明文 → converter 不會抱怨、照讀。這不是保護 integrity 的機制，只是 migration 平滑。
5. **Repository layer 新增 equality 搜尋會 silently 壞掉**：目前 grep 確認無 `WHERE result_json = ?` 等查詢，但未來有 dev 加上就會永遠找不到結果（同明文每次產生不同 ciphertext）。
6. **Backup 搬移環境沒帶 key → restore 失敗**：pg_dump 出來的 .sql 含 ENC: ciphertext；還原到 dev 環境忘了設 `ENCRYPTION_KEY` → app 一啟動就 decryption exception。

**風險控制措施：**

（未填寫）

**殘餘風險：**

可接受。最大殘餘是 key loss 的 catastrophic risk，由 Phase 3 的 key backup + rotation 程序補齊。其他 operational risk 都有 test + migration guard + fallback 三層保險。


**關聯項目：** #327 #328


---


### RISK-004 [風險] ExecutionErrorInfo 欄位擴散風險 + heuristic coupling

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#322](https://github.com/Lusnaker0730/CQL/issues/322) |
| 建立日期 | 2026-04-21 |
| 狀態 | open |
| 嚴重度 | 低（Low）— 都只影響 debug UX，不影響臨床決策、不影響 CQL 執行結果、不影響 card 產出。errorInfo 是 diagnostic aid，「判錯 phase」的最壞下場是 triage 方向錯誤浪費時間，非臨床 safety event。 |
| 發生機率 | 低（Low）— (1) wire drift 需同時 BE/FE 變動落後；(2) heuristic over-match 需第三方 lib 命中 markers（BUG-115 已列過這類 scenario，至今未踩）；(3) enum 擴充是 additive change，大多情況 CDS 暫不加新 phase；(4) 「同時兩類錯誤」情境罕見（CQL 執行要嘛 translation fail 要嘛 runtime fail，少同時）。 |

**危害情境：**

1. **JSON wire drift**：Editor / eCQM 新加 `errorInfo` 欄位，若未來 front-end 開始讀這個欄位，之後 backend 改 field name 或刪欄位就會 break 前端。目前 TypeScript types 沒同步更新（follow-up），增加未同步風險。
2. **Heuristic over-match 擴散**：BUG-115 的 heuristic 原本只在 CDS flow 用。現在三個 flow 共用 → 若 heuristic 誤判（例：第三方 library 丟 `JsonParseException` 含 "Parse"），editor + eCQM 也會一起誤標 `cql_translation`。原本只有 CDS 用戶受影響、現在擴大三倍。
3. **`ErrorPhase` enum 擴充不同步**：新增 `FHIR_RETRIEVAL` / `POPULATION_EVALUATION`，但 CDS legacy `CdsInvocationException.Phase` 沒對應 value。`fromCdsPhase()` 是單向 map，若未來 CDS 也要 FHIR_RETRIEVAL，需兩邊加。
4. **Back-compat 假象**：Editor `errors: List<String>` 保留沒刪，但 classify 之後新 errorInfo 是單一 phase；如果某個 CQL 執行同時產生 translation + execution 兩類錯誤，`errors[]` 看得到全部但 `errorInfo` 只蓋一個（最外層 throwable）。使用者可能只看 errorInfo → 漏判。

**風險控制措施：**

（未填寫）

**殘餘風險：**

可接受。主要殘餘是 heuristic over-match 在 editor + eCQM 擴大 3 倍影響面，但 worst case 仍是 phase 誤標，其他欄位（errorType / message）真實反映狀況，user 有充分資訊 override heuristic 判斷。


**關聯項目：** #320 #321


---


### RISK-005 [風險] CDS phase 分類 heuristic 可能 over-match 非 CQL translator 錯誤

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#317](https://github.com/Lusnaker0730/CQL/issues/317) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 嚴重度 | 低（Low）— 分類錯誤只影響 debug UX，不影響臨床決策、不影響 CDS 返回的 card 內容、不產生假陽性 alert。臨床流程正常運作。最壞情況是 EHR 對接商 triaging 錯誤類別浪費時間，但 phase 之外的 errorType / message 欄位仍完整正確，仍可自行判斷。 |
| 發生機率 | 低（Low）— (1) Over-classify 需要第三方 library 在 CQL 執行路徑上丟 class name 剛好含這 5 個 substring；(2) Under-classify 需 CQL engine 未來大改 exception 命名；(3) cause loop 在 1.5M 行的 Java 生態極罕見；(4) 運行時錯誤 message 剛好含 "parse error" 的機率低但不是零。 |

**危害情境：**

1. **Over-classify**：修法用 substring match 於 class name（Translation/Parse/Syntax/Compiler/Lexer）與 message（"CQL translation failed" 等）。若未來某非-CQL 相關 library 丟了一個 class name 含 "Parse"（如 Jackson 的 `JsonParseException`、某 HTTP client 的 `URIParseException`），或 message 含 "parse error"，會被誤分成 `cql_translation`。
2. **Under-classify（regression）**：若未來 CQL engine upgrade 改了 exception 結構或 message wording（如 `Could not translate`、`Compilation failed` 等新寫法），heuristic 沒跟進 → 翻譯錯誤仍被誤標為 `cql_execution`。
3. **Cause-chain loop**：若某第三方 library 構出循環 cause（罕見但 Throwable API 技術上允許），無限制 walker 會 stack overflow。
4. **Message-only match 誤判**：若運行時錯誤的 message 碰巧包含 "parse error"（e.g. 某 FHIR library 回 "Failed to parse error response from server"），會被誤歸 translation phase。

**風險控制措施：**

（未填寫）

**殘餘風險：**

可接受。worst case 是 future 加的 non-CQL exception 剛好 class name 含關鍵字 substring → phase 錯標成 translation。但 (a) 不影響臨床正確性，(b) errorType/message 欄位仍完整供 debug，(c) integration test 會在真實整合場景抓到。


**關聯項目：** #315 #316


---


### RISK-006 [風險] Cloudflare Flexible mode 造成 redirect loop + 私鑰外洩風險

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#310](https://github.com/Lusnaker0730/CQL/issues/310) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 嚴重度 | 中（Moderate）— 最壞情況是服務中斷或 auth token 竊取，但不直接導致誤診斷／誤治療；且發生需要同時滿足「接真 EHR」前置條件。 |
| 發生機率 | 低（Low）— 三個情境都是一次性配置錯誤，部署完成後不會在正常運作中自然發生。 |

**危害情境：**

1. **Cloudflare SSL mode 未切 Full strict → redirect loop**：我們在 origin :80 加了 301 redirect 到 https，但 Cloudflare Flexible mode 永遠以 http 向 origin 取資料。結果：curl https → CF 走 http 到 origin → origin 301 https → CF 轉回 client → client 再連 CF → 無限迴圈。網站功能完全中斷。
2. **Private key 權限誤設**：若 `/etc/ssl/cloudflare/twcql.com.key` 權限放寬到 644 或落到 non-root shell 使用者 read，有帳號的本機 user 即可竊取，並在 Cloudflare↔origin 段中間人偽造我們的 origin。
3. **TLS version 誤放寬**：若日後有 compat 需求打開 TLS 1.0/1.1，面對的 threat model 倒退到 2015 以前（BEAST/POODLE 等）。
4. **HSTS preload 提前加入**：若過早送 preload 申請，未來需要回滾到 http 的窗口被關閉 6 個月以上。

**風險控制措施：**

（未填寫）

**殘餘風險：**

可接受。主要殘餘風險是 Cloudflare SSL mode 切換的操作錯誤，由 release note + README 雙保險降低；配置錯誤會在第一次 smoke test 就暴露，不會 silent failure。


**關聯項目：** #308 #309


---


### RISK-007 [風險] Measure report 無法 bind 到 measure version/hash → audit reproducibility 失敗 (#303)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#305](https://github.com/Lusnaker0730/CQL/issues/305) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 嚴重度 | **高**：直接影響 TFDA / CMS / NHI 申報資料的稽核價值；若 regulator 質疑某報表是否正確、無法 reproduce = 該報表被否決 / 罰款 / 醫院 compliance 失分。 |
| 發生機率 | **中**：不上線前機率 0；但 measure 一定會改版（臨床品質指標 ongoing refinement），只要有改版 + 有歷史 report 就觸發（= 100% eventually）。 |

**危害情境：**

Measure 歷史 report 不綁版本 / CQL 內容 hash。Measure 改版後，原 report 在 `measure_report` 中看起來不變，但計算邏輯早已改過。Auditor 問「此 report 基於哪版 CQL」→ 答不出來 → compliance 失敗。更嚴重：惡意修改 measure definition + rerun 可 silently 產出看起來相同但實際不同的 report。

**風險控制措施：**

- Structural fix（PR #PAT-095）：save report 時 snapshot version + SHA-256 hash of CQL + ELM，可 tamper-evident 比對
- ELM hash 提供語意等價判定（CQL cosmetic edit → cql_hash 變、elm_hash 穩）
- 歷史 row 留 null provenance，UI 標示 'legacy'——提供後續 backfill 或 flag 的管道

**殘餘風險：**

**低**：
- Measure definition 刪除後 provenance 丟失——需要額外歸檔程序（Tier 2）
- 尚無 digital signature / timestamp authority——依靠 DB 完整性；若 DB 被 privileged user 篡改仍可偽造。真 production 需要 append-only audit table or external WORM storage



---


### RISK-008 [風險] 無 password lockout — 暴力破解可 takeover 帳號 (#PAT-094)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#301](https://github.com/Lusnaker0730/CQL/issues/301) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 嚴重度 | **中-高**：帳號 takeover 的 downstream 影響廣（PHI 讀取、CDS 建議偽造、身份偽裝做稽核留痕）。尤其 `/api/admin/**` 是 admin role，拿到 = 全平台存取。 |
| 發生機率 | **中-高**：
- Sandbox 公開在 Internet → 爬蟲每日掃登入 endpoint 是日常
- 真實醫療系統帳號通常密碼較弱（醫護不太換）
- Password spraying 攻擊對醫療業是 ongoing threat |

**危害情境：**

現況登入不限嘗試次數、不鎖定。攻擊者自動化腳本對 usernames（洩漏 / 猜測）嘗試常用密碼，一個中獎 → 醫療帳號 takeover → PHI 外洩、alert 偽造、audit log 污染。

**風險控制措施：**

PR #PAT-094：per-account counter + lockout（5 次 30 分鐘）+ admin unlock + Spring Security event-driven。補強 per-IP rate limit（既有）形成 defense in depth。

**殘餘風險：**

**低**：
- Sophisticated attacker 走分散 IP + 低頻率仍可能 evade — 需要 CAPTCHA / 2FA 終極防線（Tier 3）
- DoS attack (鎖受害者) → admin unlock + 未來 CAPTCHA 緩解
- 不擋 pre-existing 弱密碼 → 需要密碼 policy 強化（未在本 PR 範圍）


**關聯項目：** - 需求 #PAT-094
- 前置相關：Spring Security 既有 RateLimitFilter / JWT / token version


---


### RISK-009 [風險] CDS authoring 錯誤訊息不友善導致 mis-authoring 風險 (#292)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#293](https://github.com/Lusnaker0730/CQL/issues/293) |
| 建立日期 | 2026-04-20 |
| 狀態 | open |
| 嚴重度 | **中**：authoring-time catch 比 evaluation-time 錯誤嚴重度低（author 通常會 review，測試後才 ship）；但若 misauthored CDS 進 production，fire timing / 比對邏輯錯誤會讓 clinical alerts 在錯誤時機 fire 或完全不 fire。 |
| 發生機率 | **中**：`medication-prescribe` 是舊教材常見例子（Google 前幾筆 CDS Hooks tutorial 仍在用），新手必踩；CodeableConcept 比較是 CDS authoring 最常見需求之一。 |

**危害情境：**

兩類 authoring-time error message 對臨床 CDS author 不友善：

1. CQL translator 對 CodeableConcept vs String 比較給出 spec-precise 但 opaque 訊息：「Could not resolve call to operator Equivalent with signature (FHIR.CodeableConcept, System.String)」。使用者以為 CQL 寫法對、翻譯 engine 出 bug；實際上是 compare pattern 錯誤。若使用者放棄正確 pattern 改用錯誤 workaround（例如硬 cast 或跳過 clinicalStatus 篩選），CDS 邏輯會執行但 clinical 結果錯誤。
2. HookTypeValidator 對 `medication-prescribe` 等 deprecated hook 拒絕但只列 valid names。使用者可能誤以為 platform 不支援 medication-related hooks、改用 `patient-view` fallback，失去 order-sign hook 的 timing 語意（patient-view fires on page load；order-sign fires when order is signed — 用 patient-view 代替會 fire 太早）。

兩個都會讓 CDS 決策邏輯**執行得了但語意錯了** — 對臨床決策支援是高階風險。

**風險控制措施：**

1. Structural fix（本 PR PAT-092 / 設計 #293）：translator error 帶 actionable hint；hook validator 帶 migration guidance
2. Hint 精準狙擊「確實見過使用者踩到的 pattern」，避免 over-hint noise
3. Unit tests 鎖 hint triggering patterns、不 triggering 時回 null
4. Deprecated hook map 限 CDS Hooks spec 明列的 migration（目前只有 1 筆），避免瞎猜替代

**殘餘風險：**

**低**：新 pattern 出現時需 extend hint map；但以 TDD pattern 擴充（新 hint → 新 test）控制得住。


**關聯項目：** - 需求：#292
- 相關 UX 家族：PAT-088 / PAT-089（aggregateMethod normalization + save-time reject）


---


### RISK-010 [風險] Save-time validation 缺失導致 typo'd aggregateMethod 進 DB (#283)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#285](https://github.com/Lusnaker0730/CQL/issues/285) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 嚴重度 | **低-中**：延續 PAT-088 家族；本風險只是「更早 catch」的時間差。PAT-088 已經擋住主要臨床風險（evaluation 回 null 不誤報）。 |
| 發生機率 | **中**：author typo 機率不低；新 authors 特別容易寫 `"Min"` / `"Max"` / 類似簡寫。 |

**危害情境：**

PAT-088 之前，unknown aggregateMethod silently → Average。PAT-088 修 evaluation 層。但若 typo'd measure 存進 DB（沒 save-time validation），仍需要 evaluation 時才發現。下游 consumer 系統可能 consume broken measure definition 做額外處理（例如同步到 EHR / 研究系統）。

**風險控制措施：**

Save-time validation（本 PR）於 POST/PUT /api/ecqm/artifacts 拒絕 unknown aggregateMethod；配合 PAT-088 的 evaluation-time 防護。

**殘餘風險：**

**非常低**。


**關聯項目：** - 需求：#283
- 相關：PAT-088 風險 #280


---


### RISK-011 [風險] aggregateMethod typo 靜默回平均值，safety-critical Min/Max 指標失真 (#278)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#280](https://github.com/Lusnaker0730/CQL/issues/280) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 嚴重度 | **高（High）**：

- 直接影響**病人安全監控與 outlier detection**，極值型指標是臨床 QI 的核心工具之一
- 失敗 silent：外顯為「合理的數字」而非「error / blank / warning」，臨床人員完全沒有觸發調查的線索
- **Miss critical deviations**：極值指標的本質就是識別極端狀況，被平均化等於監控失去意義
- 結合 PAT-084（ratio Numer 被低估）+ PAT-085（boolean observation 被丟棄）+ 本風險，整個 CV / ratio 家族的 silent 失真風險很顯著 |
| 發生機率 | **高（High）**：

- **Abbreviation 使用**：`Min` / `Max` 是極常見的自然寫法，幾乎任何 author 都可能這樣寫
- **Copy-paste typo**：手動輸入 `Minimum` / `Maximum` 比 `Min` / `Max` 慢且易錯
- Production 若已有 Min/Max 類型 measure，**高機率已經在 silently 回錯誤數字**
- PAT-087 smoke scenario 08/09 建立時立即踩到（expected 2.0 收到 6.0） |

**危害情境：**

`MeasureScoreCalculator.calculateContinuousVariableScore` 對 `aggregateMethod` 未知值 silently 走 `default -> ...average...` 分支。這涵蓋兩種誤用：

1. **常見 abbreviations 被視為 "unknown"**：`"Min"` / `"Max"` / `"Avg"` 對使用者是直覺寫法，對 backend 是 typo → 全部回平均值
2. **真 typos**：`"Minumum"` / `"Maxmum"` 等拼錯 → 也是回平均值

失敗是 silent：engine 不報錯、response 200、`observationStatistics.aggregateMethod` 欄位 echo 使用者輸入的 raw 字串、`aggregateValue` 卻是跟輸入 method 無關的平均值。

臨床安全面風險：

- **Safety-critical 極值指標失真**：最長住院日、最高血壓、最差 HbA1c、最低氧飽和度——這些**都是 Min/Max 類型指標**，用於安全事件識別與風險管理。作者寫 `"Max"`（自然寫法）實際得到平均值 → 最差個案被平均拉平 → 報表顯示「看似正常的中等數字」→ 臨床團隊**看不到 outlier 病人**。
- **TFDA / CMS 合規申報錯誤**：量測結果以錯誤 aggregate 計算並申報，稽核比對時數字對不上既有 benchmark，合規性存疑。
- **CQI（持續品質改善）決策基於假訊號**：品質管理委員會看到平均 blood pressure = 130，以為整體控制 ok。實際最高值 = 200，但沒人看到——高血壓急症案例被平均化。

**風險控制措施：**

1. **Structural fix（PR PAT-088 / 設計 #279）**：
    - `normalizeAggregateMethod(String)` 接受 Min / Max / Avg / Mean aliases
    - 未知值回 null 並 `log.warn`（不 silent 走 Average）
    - `observationStatistics.aggregateMethod` 顯示 canonical form，consumer 看一致詞彙
2. **Unit test lock**：`MeasureScoreCalculatorTest` 加 40+ tests（parametrized over canonical forms / aliases / typos / null-blank），鎖住所有正規化邏輯
3. **Integration smoke**：PAT-087 scenarios 08/09 revert 到 `"Min"` / `"Max"` aliases，真機驗證 alias 正確 route 到 Minimum/Maximum
4. **Logging audit trail**：unknown input 進 warn log，TFDA 合規稽核可從 log 查錯 authoring 歷史

**殘餘風險：**

**低**：

- **行為變動**：null return 給下游 consumer 一些衝擊（dashboard 顯示 blank 而非 wrong number），但 this is the point
- **未涵蓋 save-time validation**：理想還有 measure save endpoint 層 reject 未知 aggregateMethod — 另案 follow-up（不在本 PR 範圍）
- **aliases 集合固定**：若未來需要更多 aliases（e.g. "first"→"minimum"）需要 extend switch；但那是擴充不是 bug
- 殘餘風險層級：**Acceptable**


**關聯項目：** - 需求：#278
- 設計：#279
- 同「evaluator silent 失真」家族：PAT-083 (cohort null) / PAT-084 (ratio Numer gate) / PAT-085 (boolean obs) / PAT-088 (aggregate method) — 全部 class-level silent bugs，均透過 smoke harness 曝出


---


### RISK-012 [風險] CV Count 對 boolean 條件靜默回 null，所有「count of patients with X」指標失效 (#269)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#271](https://github.com/Lusnaker0730/CQL/issues/271) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 嚴重度 | **中（Moderate）**：

- 影響**品質監控指標的可見性**，不直接驅動臨床決策
- 失敗 silent：看報表的人看到空白，可能誤判「指標未計算」而非「指標 bug」
- 但：一旦使用者發現此類 CV 是空白，會去用 proportion 或 cohort 替代，behavior 改變可能不回頭——fix 後需 retrofit 既有 measure |
| 發生機率 | **高（High）**：

- **Class-level bug**：所有 boolean-criteria CV measures 百分百觸發
- CV measure 常見設計是 boolean criteria + Count aggregate；幾乎所有「count of patients with X」這類 measure 都踩到
- Production 若有此類 measure 已在 silently 報空白（PAT-082 scenario 03 建置時發現此 bug） |

**危害情境：**

`PopulationEvaluator.extractObservationValues` 只處理 Number / Iterable<Number>，boolean 直接跳過。CQL 產生器對 authoring tree 是 boolean-returning element（`AgeRange` / `Gender` / 其他 demographic / boolean filter）組成的 CV observation，會產出 boolean-valued `Measure Observation Value`。這兩邊湊起來 → CV measure 的 `measureScore` 回 null，`observationStatistics` 也 null。

完全 silent：engine 不報錯、translator 不警告、API 回 200，只是 `measureScore: null`。下游 UI / 儀表板顯示空白 → 使用者以為「還沒評估」或「0 個病人符合」。

典型受影響的指標：

- **Count of patients with completed assessment in period**
- **Count of patients with last visit in period**（有 recent visit follow-up）
- **Count of high-risk encounters**
- **Count of patients on specific medication**

這些全部是醫療品質監控的常見 CV use case，報表空白等於無法追蹤完成率、覆蓋率。

**風險控制措施：**

1. **Structural fix（PR PAT-085 / 設計 #270）**：`extractObservationValues` 加 Boolean 分支：TRUE → 1.0、FALSE → skip、Iterable 同步處理。一處修，所有 CV/ratio 流程繼承。
2. **Unit test lock**：`PopulationEvaluatorTest` 加 4 tests 覆蓋 boolean TRUE / FALSE / null / numeric（regression lock）。
3. **Integration smoke**：scenario 03 assert `score: 3.0`（先前 skipped），全棧真機驗證。
4. **scope isolation**：僅 `extractObservationValues` 一處改動，proportion / cohort 路徑完全不走此 method，不受影響。

**殘餘風險：**

**非常低**：

- **Aggregate method 語意退化**：boolean-only observation 輸入下 Sum = Count、Average/Median/Min/Max = 1.0。語意化約但不 crash、不 null。若作者意圖是「統計學意義上的 average」，用 boolean 本身就不對；這種情況現行 fix 也不對但至少有 non-null 結果給使用者看到能 debug
- **Downstream impact**：先前依賴「CV Count 對 boolean 回 null」的 consumer（極少有人會依賴）會看到 behavior 變化；但那是修正方向
- 殘餘風險層級：**Acceptable**


**關聯項目：** - 需求：#269
- 設計：#270
- 相關歷史 fix（同「evaluator 依 FHIR spec 合規」主線）：#252、#259、#264、PAT-081、PAT-083、PAT-084


---


### RISK-013 [風險] Ratio measure 的 Numerator 被錯誤 gate 在 Denominator，事件率系統性低估 (#264)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#266](https://github.com/Lusnaker0730/CQL/issues/266) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 嚴重度 | **高（High）**：

- 直接影響**病人安全監控指標**的正確性（跌倒、感染、ADE 等是 high-harm 類別）
- 失敗是**靜默的**（事件率數字是合理範圍、不會 alarm）
- 系統性低估：管理層更難看出問題存在
- 主要 downstream 是 reporting 層（不直接驅動自動化 CDS），但 reporting 是醫院品質 governance 的核心輸入 |
| 發生機率 | **中（Medium）**：

- **Class-level bug**：所有 ratio scoring measure 都受影響
- Production 現有 ratio measure 目前不多（主要是 proportion-heavy 的 eCQM），但：
- 真實 ratio use case 一旦上線（跌倒/感染/ADE 指標），**100% 觸發**
- 未來 eCQM pipeline 擴展到 rate-based measures 時會立即爆發 |

**危害情境：**

`PopulationEvaluator.aggregatePatientResults` 對所有 scoring type 採用 proportion 的 gating 語意：`inNumer = effectiveDenom && isPopulationTrue(results, "Numerator")`。對 proportion 正確（Numer ⊆ Denom 本就是 spec），但對 **ratio** 是 class-level bug。

Ratio 的 Numer 語意是 **在 IP universe 內獨立計算的事件數/指標數**，典型包含：

- 每千病人日跌倒次數（Denom = 病人天數，Numer = 跌倒事件）
- 每千住院日院內感染（Denom = 住院日，Numer = HAI 事件）
- 每千 ED 訪視住院率（Denom = ED 訪視數，Numer = 住院事件）
- 每百處方不良事件（Denom = 處方數，Numer = ADE）

這些場景裡 Numer 本質是事件 counts，可以 > Denom（每日多次事件）、可以與 Denom 的 subset 不重疊（事件不一定發生在 Denom 樣本上）。Evaluator 把 Numer gate 在 Denom 之後意味著：

1. **事件率被系統性低估**：實際有 10 次跌倒但只有 3 個 Denom 人被 counted 就會只顯示 3 ✗
2. **Disjoint cohort 完全 0 分**：如 scenario 02 原設計（Denom=young, Numer=senior）Numer 會是 0，score=0
3. **分母交集縮水**：即使 Numer 落在 Denom 內，若 Denom 被 exclusions 砍掉、或 Denom 本身過濾比 Numer 嚴格，Numer 被低估
4. **所有 ratio measure 都受影響**：不是 edge case 而是普遍性 bug

對臨床/病人的潛在影響：

- **品質風險被低估**：醫院內跌倒、院內感染、ADE 等安全事件都是 ratio-style measure，報表看到的 rate 比實際低，管理層可能沒採取必要干預
- **回溯申報錯誤**：TFDA / CMS / NHI 申報的 ratio-based 指標數字不正確，合規稽核可能回來要求重驗
- **Research analytics**：用平台跑臨床研究的團隊若用 ratio measure 得到的事件率做 outcome analysis，結論會偏向「事件率比實際低」

**風險控制措施：**

1. **Structural fix（PR PAT-084 / 設計 #265）**：`aggregateRatioPatientResults` 新 method 依 FHIR spec 把 Numer 只 gate 在 IP，不 gate 在 Denom。`buildRatioEntries` 同步鏡射 trace debug 邏輯。`MeasureEvaluationService` 依 scoringType 路由。
2. **Unit test lock**：`PopulationEvaluatorTest` 8 個 tests 直接鎖住「同一 input、proportion vs ratio 結果不同」的 key invariant。任何 future refactor 誤把 ratio route 回 proportion 會立刻被 CI 擋下。
3. **Smoke integration verify**：scenario 02 revert 到 disjoint Denom/Numer 設計，score=150.0（Numer > Denom，>100% 的 valid ratio），全棧真機驗證。
4. **Score dispatcher 強化**：`MeasureEvaluationService` 原本 fallback 硬呼叫 `calculateProportionScore`——本 PR 改用 `calculateScore(scoringType, ...)` dispatcher，ratio → `calculateRatioScore`（不扣 denom-exclusion），proportion 維持。

**殘餘風險：**

**低**：

- Ratio / proportion 邏輯現在徹底分離；未來加 rate-style scoring（未列入 FHIR 4 spec 但預期可能出現）需另設計分派，現在 evaluator 結構已易於擴充
- 尚無 **Denominator Exclusion 對 ratio score 影響** 的特殊處理（`calculateRatioScore` 目前忽略 exclusion 不從 denom 扣除）——FHIR spec 對此場景本就模糊，現行設計採取保守路徑（raw Denom 當分母）
- 殘餘風險層級：**Acceptable**


**關聯項目：** - 需求：#264
- 設計：#265
- 相關歷史 fix（FHIR / CQL spec 合規主線）：#252（AgeRange period binding）、#259（cohort score）、PAT-081、PAT-083


---


### RISK-014 [風險] Cohort measure 回 measureScore=null + 空 Denom/Numer rows 不符 FHIR spec (#259)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#261](https://github.com/Lusnaker0730/CQL/issues/261) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 嚴重度 | **中（Moderate）**：

- 影響**報表正確性與 FHIR 互操作**，不影響即時臨床治療
- 失敗是**靜默的**（外顯為「空白」或 null），臨床人員看不到異常但也看不到正確答案
- FHIR consumer 系統可能被破壞（視 consumer 實作嚴格程度）
- 不直接驅動 CDS / treatment-recommendation 邏輯 |
| 發生機率 | **高（High）**：

- 任何 cohort scoring type 的 measure 都會觸發（這是全 class 的問題，不是邊界條件）
- 目前有 cohort scoring 的 measure 已在系統內（PAT-082 smoke scenario 04 就是一個）
- 使用者每次評估 cohort measure 都會遇到 |

**危害情境：**

`MeasureScoreCalculator` 對 cohort scoring 類型硬回 null（程式註解誤導："Cohort measures don't have a numeric score"），不符合 FHIR MeasureReport R4 spec。同時 `MeasureEvaluationService` 對 cohort 走 proportion/ratio 共用路徑，populations list 內硬塞 denominator=0 / numerator=0，這兩個 population 在 cohort 定義下並不存在。

臨床安全面風險：

1. **Cohort 指標數字在 UI 不顯示**：臨床品質監控儀表板的「符合此 cohort 的病人數」欄位留空或顯示 null，醫療管理者無法判讀「這個 cohort 有幾位 patient 符合」，也無法做歷史趨勢比較。實務上這會讓某些 cohort measure（例：「具備特定 risk factor 的病人」、「使用特定藥物的 cohort」）變成瞎報。
2. **FHIR 互操作失敗**：eCQM 平台若把 MeasureReport 送出給 external consumer（EHR 匯入、CMS 報表系統、研究協作機構），不合 spec 的 payload 可能被拒收、或被誤判為「計算失敗」；TFDA 法規稽核把 null 分數視為「評估未完成」而要求重驗。
3. **資料雜訊**：response payload 帶 denominator=0/numerator=0 placeholder 讓下游誤以為「這個 cohort 其實有 N/D 結構但 count 為 0」，混淆語意，debug 困難。

沒直接驅動自動化治療決策；但 cohort measure 常用於**醫療品質監控與法規申報**，正確性在臨床場域是合規問題。

**風險控制措施：**

1. **Structural fix（PR PAT-083 / 設計 #260）**：
   - `calculateCohortScore(Integer ip)` 新 helper 回傳正確 IP count（Double）
   - `buildCohortResult` 專屬 branch 只輸出 IP population、measureScore = count、unit = "count"
   - 切離 proportion/ratio 共用路徑，cohort 不再帶 denominator/numerator 0 值
2. **Unit-test invariant lock**：`MeasureScoreCalculatorTest` 覆蓋 calculateCohortScore 的 zero / null / positive 三路徑；dispatcher 對 COHORT 回 null 的 contract（避免 future refactor 誤 route）
3. **Integration smoke**：smoke scenario 04 assert `score: 4.0` 與只含 IP 的 populations shape，全棧回歸
4. **FHIR spec 依據明確**：fix 與 FHIR R4 MeasureReport spec 完全對齊；當 consumer 系統升級到 spec 更嚴格的驗證時不會被擋

**殘餘風險：**

**低**：

- **Wire shape 變動**：cohort response 不再帶 denominator/numerator rows；若有 consumer 硬依賴這些 placeholder 0 值需要調整（但那是下游 bug 不是本修正的）
- 沒有 fallback / feature-flag：因為舊行為不合 spec，沒理由保留
- 殘餘風險層級：**Acceptable**


**關聯項目：** - 需求：#259
- 設計：#260
- 同家族歷史 fix（FHIR / CQL spec 合規主線）：PAT-081（AgeInYearsAt(end of MP)）、#252、#254、PAT-082（smoke test 覆蓋）


---


### RISK-015 [風險] AgeRange 使用系統時鐘計算年齡 → eCQM 結果非 reproducible (#252)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#254](https://github.com/Lusnaker0730/CQL/issues/254) |
| 建立日期 | 2026-04-19 |
| 狀態 | open |
| 嚴重度 | **中高（Moderate）**：

- 直接影響臨床品質量測報告的**數字正確性**，這些報告用於：醫院內績效評估、對政府（NHI/eCQM/HEDIS）申報、研究分析
- 失敗是**靜默的**——engine 不會報錯，只是數字跟預期不同，容易流入下游決策
- 但：不直接驅動自動化臨床治療決策（eCQM 是 reporting 層，不是 CDS 決策層），CDS 路徑不受影響（因其本來就沒有 MP 概念、用 `AgeInYears()` 相對於決策當下是正確的） |
| 發生機率 | **高（High）**：

- **結構性存在**：任何用到 AgeRange element 的 eCQM measure 都會受影響（眾多 HEDIS/eCQM 指標都有年齡 stratification）
- **只有時間才會放大影響**：部署當年症狀輕（結果接近正確）；部署 3 年後同一份 measure 的數字才明顯失準
- 今天 smoke test 建立過程中就觸發（p4 在 6 年後被錯誤分類），說明已在「會發生」的時間範圍內 |

**危害情境：**

`ExpressionCqlEngine.buildAgeRangeExpression` 對所有 callers 輸出 `AgeInYears()`（無參數）。在 CQL 對 FHIR 模型的語意下，這個函式使用**評估機器的系統時鐘**。eCQM 指標本應對「在 Measurement Period 端點時的病人年齡」做判定，結果實際上是對「評估當下的病人年齡」做判定。

臨床安全面風險：

1. **回溯報表錯誤**：2026 跑 2020 的 measure 回顧報表，Numerator/Denominator 閾值套到 2026 的年齡，人口數完全不對。臨床 QI 團隊用這種報表做「歷史趨勢 vs 介入後趨勢」的比較會得到假改善或假惡化的結論。
2. **Reproducibility 失敗**：稽核單位回來驗證同一份 measure 報告，得到跟上次不一樣的數字——TFDA/CMS 合規審查過不了。
3. **時區/伺服器漂移**：同一個 CQL library cache 在兩台不同時區或不同時鐘校準的機器上執行，會產出不同 population counts；cluster deployment / blue-green deployment 會放大此漂移。
4. **測試不穩**：任何自動化測試、smoke test、integration test 的斷言值都會隨著時間流逝漸漸失效（就是 #PAT-080 smoke harness 今天踩到的 bug——p4 從 mid 變 senior）。

**風險控制措施：**

1. **Structural fix（本 PR PAT-081 / 設計 #253）**：generator 在 eCQM context 自動 emit `AgeInYearsAt(end of "Measurement Period")`。輸出形狀由 code 決定，catalog / JSON 無法選錯。
2. **Unit tests lock**：`BuildAgeRangeExpressionTests` 覆蓋 eCQM vs CDS 兩路徑；`MapUnitToAgeFunctionTests` 覆蓋所有 unit variants。generator 回歸時 CI 擋。
3. **Smoke test verification**：`scripts/smoke/scenarios/01-proportion-age-cohort` 用自然生日（1950~2010）對 2020-H1 measurement period 跑，預期 score=60.0 stable。如果未來有人改壞，smoke 立即抓。
4. **Deploy-time invalidation**：眼前已部署的 eCQM artifact 在下次 save & publish 時自動帶新 CQL，不需要手動遷移 DB / rebuild ELM cache。

**殘餘風險：**

**低**：

- 仍依賴開發者把新 eCQM element（假設未來新增 GestationalAge 之類）正確標注「依賴 MP」。mitigate via code review + 新元素的 integration scenario test
- `CqlArtifactBuilder` 路徑（CDS hooks）保持 `AgeInYears()`——若未來 CDS 也需要 MP 綁定邏輯，需另開設計
- 殘餘風險層級：**Acceptable**


**關聯項目：** - 需求：#252
- 設計：#253
- 相關歷史 bug（同「結構性排除非 reproducible 輸出」主線）：BUG-110、BUG-112、#225、#241、#245


---


### RISK-016 [風險] SignatureLevel=None 讓 FHIRHelpers multi-overload function 在 runtime 歧義 (#243)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#245](https://github.com/Lusnaker0730/CQL/issues/245) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 嚴重度 | **中（Minor）**：

- 失敗模式既有「engine 例外 → 指標為 null」，也有「挑錯 overload → 數字靜默偏差」。後者在臨床 QI 應用上較嚴重，因為不會觸發任何 alarm。
- 但：eCQM 結果展示在前端 UI，由臨床工作者覆核才送出，不直接驅動自動化治療。
- 未來若 CDS hook 使用 `FHIRHelpers.ToString` 做決策（例如把 FHIR Code 轉字串比對 valueset），失敗時 hook 無法觸發 → 可能遺漏提醒，但非主動錯誤建議。 |
| 發生機率 | **中低（Low-Medium）**：

- Translator 警告每次翻譯必發，代表**結構性存在**，不是邊界條件
- 實際臨床資料觸發失敗的機率取決於「多少病人的該欄位為 null / base type」，經驗上 1-5% 常見
- 一旦 ship 影響所有用到該 overload 的指標/hook |

**危害情境：**

`LibraryManagerFactory` 建 `CqlCompilerOptions` 時完全未設 `signatureLevel`，預設值 `None` 令 Translator 產出的 ELM 對**多 overload function**（`FHIRHelpers.ToString` / `ToDateTime` / `ToInterval` / `ToDate` 等）只留名稱、不嵌參數型別簽章。

對臨床/病人的潛在影響：

1. **量測分數靜默錯誤**：若 engine 在 null 或 base-type argument 下挑到非預期 overload，會做錯誤的型別 coercion（例如把 `FHIR.dateTime` 當 `FHIR.date` 處理、時區資訊遺失），產生錯誤的 population 計算結果，且**不會拋例外**——指標報告顯示「看似正常但數字錯」。
2. **量測直接失敗**：另一種情況是 engine 根本找不到 unambiguous match，拋 `AmbiguousCall`，整個 group evaluation 失敗，指標在 dashboard 上顯示為 null 或 0。
3. **曝險面廣**：FHIR choice type（Observation.effective、Procedure.performed、Condition.onset...）+ 可能 null 的 FHIR 欄位，只要 CQL 指標用到 `ToString` / `ToDateTime` 就有風險；既有 eCQM 指標幾乎都觸及。

這是與 BUG-110 (`ToInterval(null)` ambiguous) / BUG-112 (`and` 未 short-circuit 導致 null type check 歧義) **同一家族**的 dispatch failure mode——目前尚未在 production log 觀察到事故，但 translator 每次翻譯都在警告，等於「炸彈已埋」。

**風險控制措施：**

1. **Structural fix（本 PR #PAT-078 / 設計 #244）**：`LibraryManagerFactory.defaultOptions()` 與 `buildOptions()` 固定套 `SignatureLevel.Overloads`。之後任何 translator 產出都會在 ELM 內嵌簽章，engine 編譯期就完成 overload 選擇。
2. **Unit-test invariant lock**：新增 `LibraryManagerFactoryTest` 對 `getSignatureLevel()` 斷言 ∈ {Overloads, All}；任何將來誤改回 None 的 regression 會在 CI 單元測試層級擋下。
3. **Production 驗證**：部署後檢查 backend 日誌，確認不再有 "multiple overloads ... SignatureLevel setting (None)" 警告。
4. **Golden suite**（#230 已建立）：`ModifierGeneratedCqlGoldenTest` 的 15 scenarios 對真實 engine 跑 CQL，確保 behavior 等價，SignatureLevel 改動不破壞既有指標。

**殘餘風險：**

**非常低**：

- `Overloads` 涵蓋所有多 overload function；若將來 FHIRHelpers 擴增新 overload，此修法自動涵蓋（不需更新 catalog）
- 殘餘面：**single-definition** function 如果未來被某 library 以同名 overload shadow，仍可能歧義；但這屬於作者寫 CQL 時的主動行為，可由 translator 的其他 diagnostic 抓到
- 殘餘風險層級：**Acceptable**


**關聯項目：** - 需求：#243
- 設計：#244
- 同家族歷史 bug：BUG-110、BUG-112、#225、#241


---


### RISK-017 [風險] modifier catalog 的 CQL 字串可能藏 dispatch 歧義與未 short-circuit bug (#229)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#241](https://github.com/Lusnaker0730/CQL/issues/241) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 嚴重度 | **中（Minor）**：指標計算錯誤會產生錯誤的量測報告數字，但：
- 所有錯誤模式都是 translator-level 或 engine exception（非靜默偏差），CQL 執行會回報 error，不會在外觀上「看起來正常但答案錯誤」
- eCQM 結果展示在前端 UI，臨床工作者核閱過才會送出
- 不直接驅動自動化治療決策 |
| 發生機率 | **低（Low）**：需要 catalog 作者或修改者引入新 whereClause 且 reviewer 未抓出；但一旦 ship 到 production，所有使用該 modifier 的量測都受影響（橫向爆炸）。 |

**危害情境：**

Modifier catalog（`modifiers.json`）把 CQL whereClause 以逃脫過的字串塞在 JSON 欄位內（例：`(case when O.effective is FHIR.Period then FHIRHelpers.ToInterval(O.effective as FHIR.Period) overlaps \"Measurement Period\" ... else false end)`）。此形式帶來以下臨床安全風險：

1. **Runtime-only 錯誤**：JSON 裡的 CQL 沒有 syntax highlighting、沒有 IDE / translator 檢查，錯誤只能在執行期才暴露。BUG-110（`ToInterval(null)` ambiguous call）與 BUG-112（`and` 未 short-circuit 導致 null type check 歧義）正是此模式下的實際事故——生成的量測 CQL 在 engine 端爆錯，指標計算被靜默停用或 population count 為 0。
2. **審查不可行**：6 個 `DuringMeasurementPeriod` entry 的 whereClause 重複了 ~90% 的 case-expression boilerplate；人類 reviewer 在比對這些近似字串時，極易放過邊界條件的差異（例如 Encounter 是單一 Period、Observation 是 choice、Condition 有 `recordedDate` fallback 而其他沒有）。
3. **橫向擴散**：新增 resource type 時（R5 新增 Consent / DocumentReference 等），複製-貼上一段 whereClause 就能產生同類 bug，且 CI 無法擋下。

對病人/臨床的潛在影響：品質量測報告數字錯誤 → 影響績效評估、醫療決策支援（CDS）誤用。

**風險控制措施：**

1. **結構化 fieldSpec**（本 PR #230 實作）：CQL 在 JSON 內不再以字串存在；catalog 只描述 `{alias, field, types, fallbacks}`，generator 以 code 組合 null-safe case 表達式。dispatch-ambiguity shape **在構造上不可能被產出**。
2. **Golden integration suite**（`ModifierGeneratedCqlGoldenTest`，15 scenarios）：對 Period / dateTime / null / fallback 各分支以真實 engine 執行，鎖住 BUG-110 / BUG-112 regression。
3. **CI gate**：`jacoco:check` 48% line floor 確保 generator helper 被測試覆蓋；golden suite 隨 PR 執行，catalog 任何變動都會重跑 end-to-end。

**殘餘風險：**

- **非常低**：新增不同形狀的 modifier（非 `DuringMeasurementPeriod` 類型）時，若再引入新的 subtype-only nested block，需為該 block 同樣以 code-generator 方式產生 CQL，而非塞字串。此規範已記錄在 `ModifierDefinition.java` 的 Javadoc 與 PR #230 的設計說明 (#240) 內。
- 殘餘風險層級：**Acceptable**（受 policy + code review 共同防線覆蓋）


**關聯項目：** - 需求：#229
- 設計：#240
- 歷史 bug（本次改動讓它們 unreachable）：BUG-110、BUG-112、#225


---


### RISK-018 [風險] CQL library 解析鏈的 silent DB fallback 造成 stale CQL 執行

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#231](https://github.com/Lusnaker0730/CQL/issues/231) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 嚴重度 | High — 計算正確性錯誤且無線索。 |
| 發生機率 | Medium — 需要 (a) 編輯器 Run CQL 路徑沒 seed 到 compiledLibrary cache、(b) DB 有同名同版本的 stale 紀錄。兩個條件在 BUG-107 那次同時發生。 |

**危害情境：**

\`DatabaseLibrarySourceProvider\` 沒有任何警示地在 library 解析鏈中返回資料庫中儲存的 CQL。當使用者編輯器裡有新版 CQL、但資料庫中已存在相同 \`name+version\` 的舊副本時，解析鏈會先命中 DB 並跑舊版本（BUG-107 的發生路徑）。

使用者沒有錯誤訊息可看 — CQL 翻譯成功、評估成功，但分數對應的是舊 CQL 的邏輯。可能導致：
- 品管人員看不出新 measure 編輯沒生效
- 評估結果對應的實際邏輯與 UI 顯示的 CQL 文字不一致
- 上線前測試通過、上線後 silently 用到舊規則

**風險控制措施：**

（未填寫）

**殘餘風險：**

Low。單元測試鎖住 exclusion 行為（8 個場景），BUG-107 的 integration 測試仍通過。其他 library（DFLR / FHIRHelpers 等 include）的 DB 解析不受影響。


**關聯項目：** 修正於 #BUG-107（原 fix）基礎上加強；新 issue #PAT-073 / #BUG-113。


---


### RISK-019 [風險] Encounter/MedicationRequest whereClause 的 `is not null and ToInterval` 仍會觸發 dispatch 歧義

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#225](https://github.com/Lusnaker0730/CQL/issues/225) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 嚴重度 | High — 所有使用 DuringMeasurementPeriod Encounter/MedicationRequest modifier 的 measure 回傳不正確結果。 |
| 發生機率 | High — 該 modifier 一使用就觸發（Encounter.period 若 null 或 MedicationRequest 沒 authoredOn）。 |

**危害情境：**

BUG-110 修復時對 Observation/Condition/Procedure 改用 case 表達式，但 Encounter 與 MedicationRequest 走了較簡單的 \`is not null and ToInterval/ToDateTime\` 捷徑。實測發現 CQL 的 \`and\` 並不會短路 — 即使左側是 \`false\`，右側仍會被評估，null 值因此流入 ToInterval / ToDateTime，和 BUG-110 一樣在 FHIRHelpers 多載間無法 dispatch，整個 measure 評估回 null。

使用者沒有明顯線索（前端不會顯示「某個 define 執行期拋例外」），只會看到 IP/denominator 為 0，和 BUG-111 的表象難以區分。

**風險控制措施：**

（未填寫）

**殘餘風險：**

Low — golden suite 日後會跑所有 modifier × choice-type × null 組合。CI 強制此 suite 通過才能 merge。


**關聯項目：** 修正 #212 / 延伸自 BUG-110 (#218) / 由 #224 (golden suite) 發現


---


### RISK-020 [風險] Cross-library retrieve 未被 bulk fetch 發現造成評估結果錯誤

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#220](https://github.com/Lusnaker0730/CQL/issues/220) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 嚴重度 | High — 所有依賴跨 library 邏輯的 eCQM（包含一般 MAT 發布的 measure、以及使用者自建的 CDS Connect Commons / TWCDI 模組化 measure）評估結果都會錯誤。 |
| 發生機率 | High — 任何採用 libref 拆解 population criteria 的 eCQM 都會觸發。 |

**危害情境：**

當 eCQM 的 population criteria 透過 libref 參照到另一個 CQL library 的 define（例如 \`define "Initial Population": "DFLR"."Initial Population"\`，而 DFLR 內部使用 \`[Encounter]\` 過濾），bulk fetch 層只會掃描主 library 的 retrieves，不會遞迴進 included library 發掘需要抓的 FHIR 資源型別。結果：每個病人拿到 0 筆相關資源、DFLR 的 population 永遠評估為 0、最終 measure score 為 null 或 0，使用者卻收到「評估成功」的回應，誤判為該期間無符合條件病人。

**風險控制措施：**

（未填寫）

**殘餘風險：**

Low — Integration test \`CqlExecutionIntegrationTest.CrossLibraryRetrieveDiscovery\` 鎖住 invariant（不傳 libMgr 找不到 Encounter；傳了就會找到）。legacy 單參數 overload 保留 null 行為避免破壞既有 callers。


**關聯項目：** 修正 #212 評估路徑；由 PAT-067 + DFLR libref 組合暴露。


---


### RISK-021 [風險] DuringMeasurementPeriod 執行期 ToInterval(null) 歧義造成評估失敗

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#218](https://github.com/Lusnaker0730/CQL/issues/218) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 嚴重度 | High — 功能部署即失效，任何套用 modifier 的 eCQM 都無法取得正確分數。 |
| 發生機率 | High — 只要使用者加了 modifier 且資料中存在任何 effective/onset/performed 為 null 或與 cast target 型別不符的 resource，就會觸發。 |

**危害情境：**

PAT-067 新增的 DuringMeasurementPeriod modifier 實際部署到 VM 並由使用者加入 a1c_dm 的 Observation 元素後，所有病人評估皆以 \`CqlException: Ambiguous call to operator 'ToInterval(null)' in library 'FHIRHelpers'\` 失敗，導致 Measure Report 產出分數為 null / 病人數為 0。若使用者未察覺這是執行期錯誤（前端未明顯警示），會誤以為該期間沒有符合條件的病人，造成低報達標率 / 誤判臨床品質。

**風險控制措施：**

（未填寫）

**殘餘風險：**

Low — case 表達式內每個分支獨立處理型別，CQL engine 在執行期不會對 null 呼叫 ToInterval。已有 ExpressionCqlEngineTest 單元測試 snapshot 確保 whereClause 產出正確字串。


**關聯項目：** 修正 #212, 由 PAT-067 引入。


---


### RISK-022 [風險] 品管人員誤信未過濾期間的 eCQM 分數 — #212

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#214](https://github.com/Lusnaker0730/CQL/issues/214) |
| 建立日期 | 2026-04-18 |
| 狀態 | open |
| 嚴重度 | **中等 (S2)** — 非直接造成個別病人傷害，但可能導致機構層級的錯誤 QI 決策或虛報主管機關指標。 |
| 發生機率 | **中等 (P2)** — 沒有此 modifier 時，多數使用者會直觀認為「期間選擇器當然會過濾」，很容易忽略。 |

**危害情境：**

若使用者在 eCQM authoring 階段未察覺需要套用 DuringMeasurementPeriod modifier（或忘記套用），儀表板上的期間選擇器仍會呈現可選，但評估結果會忽略期間輸入並回傳相同的歷史全資料平均。品管人員可能依此錯誤分數做跨年度趨勢判讀、向主管單位呈報錯誤的達標率，或據此下達錯誤的臨床改善決策。

**風險控制措施：**

（未填寫）

**殘餘風險：**

LOW — 在控制措施 (1) 實作後，使用者仍有可能建構出錯誤配置的 measure，但需配合文件（2）與預期後續的 UI 提示來降低此風險。


**關聯項目：** Closes #212


---


### RISK-023 [風險] CQL 引擎 CodeType/DateTimeType dispatch 歧義可能回傳錯誤的病患篩選結果 (BUG-106)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#206](https://github.com/Lusnaker0730/CQL/issues/206) |
| 建立日期 | 2026-04-17 |
| 狀態 | closed |
| 嚴重度 | 3 - 嚴重 (Serious) |
| 發生機率 | 4 - 可能發生 (Probable) |

**危害情境：**

CQL 執行引擎處理 FHIR R4 資源時，HAPI FHIR 以泛型 `CodeType` 封裝具體 enum 值（如 `MedicationRequestStatus`、`ObservationStatus`）、以 `DateTimeType` 封裝 FHIR `dateTime` 欄位。`FHIRHelpers-4.0.1.cql` 有 251 個特定 enum 的 `ToString` 重載，但執行期 dispatcher 看到 `CodeType` 時有 252 個候選同時相容（每個 enum IS-A code），無法決定唯一 overload → 拋 `Ambiguous call to operator 'ToString(CodeType)'`。`sort by` 原生 `DateTimeType` 也因缺少 CQL System.DateTime 轉換層拋 `Type DateTimeType is not comparable`。

**臨床影響**：撰寫常見 idiom（例如 `where M.status = 'active'`、`sort by recordedDate desc`）的 CQL 在執行期 silently 傳回 null 或拋例外，導致病患篩選（quality measure 分子/分母）結果不正確或缺失。PAT-066 已加 UI 錯誤曝露 + TWCDI 範本 `.value` workaround，但**使用者自行撰寫的 CQL 仍會踩到這個 bug**。

**風險控制措施：**

- **設計控制**：擴充 `ComparableR4FhirModelResolver.resolvePath()`，攔截 primitive enum 欄位存取，回傳具體 `Enumeration<T>` 而非泛型 `CodeType`；或建立 `TypeNormalizingRetrieveProvider` wrapper 在 resource 交給 engine 前正規化型別
- **替代方案**：引入 cql-evaluator 社群的 `FhirTypeConverter` 或升級 CQL Framework / FHIRHelpers 到有此修補的版本
- **防護措施**：整合測試覆蓋 `QICoreCommon`、`MATGlobalCommonFunctions` 等標準 library，確保修正不回歸
- **使用者警告**：PAT-066 已部署錯誤曝露（translator warnings + per-define runtime exception），執行失敗時 UI 明確顯示

**殘餘風險：**

實施 ModelResolver 正規化 + 標準 library 回歸測試後，殘餘風險降至可接受範圍（嚴重度 3 × 機率 1 = 3）。若 FHIRHelpers 未來版本完全解決 dispatch 歧義，可移除 workaround。


**關聯項目：** - PAT-066 commit 7a9821e（錯誤曝露 + TWCDI 範本 workaround + 病人上傳修正）
- 依關聯設計 Issue（待建立）


---


### RISK-024 [風險] CQL 引擎翻譯錯誤靜默吞掉可能導致量測結果不正確 (#BUG-015)

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#167](https://github.com/Lusnaker0730/CQL/issues/167) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |
| 嚴重度 | 中 |
| 發生機率 | 高（任何 CQL 語法不相容都會觸發） |

**危害情境：**

CQL 翻譯產生錯誤但被靜默吞掉，所有 expression 回傳 null/0，品質量測分數顯示為 0% 或異常值（如 220%），可能誤導臨床品質決策

**風險控制措施：**

（未填寫）

**殘餘風險：**

低 — 錯誤會被明確回報，不會靜默產生錯誤結果


**關聯項目：** #150


---


### RISK-025 [風險] FHIR 稽核日誌不完整風險

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#147](https://github.com/Lusnaker0730/CQL/issues/147) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |
| 嚴重度 | 3（合規失敗可能影響醫院營運） |
| 發生機率 | 2（系統穩定運行時機率低） |

**危害情境：**

1. **稽核紀錄遺漏**：EHR 操作未記錄連線 ID/病人 ID，無法追溯資料存取來源
2. **日誌膨脹**：長期累積導致資料庫容量不足
3. **合規失敗**：醫院評鑑要求完整存取日誌，不足可能導致不合規

**風險控制措施：**

- AuditFilter 自動提取 EHR 連線和病人資訊
- 排程清理（每日 2:00 AM）遵守保留天數設定
- 管理員可手動觸發清理
- 稽核寫入失敗不影響主要操作（try-catch 包裝）

**殘餘風險：**

低 — 自動機制覆蓋所有 /api/ 路徑


**關聯項目：** #136


---


### RISK-026 [風險] TLS/mTLS 憑證管理風險

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#144](https://github.com/Lusnaker0730/CQL/issues/144) |
| 建立日期 | 2026-03-25 |
| 狀態 | open |
| 嚴重度 | 3（可能導致醫療資料在傳輸中被竊取） |
| 發生機率 | 2（需要特定配置錯誤才會發生） |

**危害情境：**

1. **憑證洩漏**：CA 憑證或客戶端私鑰透過 API 回傳或日誌洩漏
2. **弱 TLS 版本**：使用 TLSv1.0/1.1 導致中間人攻擊
3. **hostname verification 停用**：被誤用於生產環境導致 MITM 攻擊

**風險控制措施：**

- PEM 欄位使用 `@JsonProperty(WRITE_ONLY)` 禁止 API 回傳
- PEM 欄位使用 AES-256-GCM 加密儲存
- TLS 最低版本預設 TLSv1.2
- 停用 hostname verification 時記錄 WARN 日誌
- 測試覆蓋所有 TLS 路徑

**殘餘風險：**

低 — 配置錯誤需管理員權限，且有日誌警告


**關聯項目：** #135


---


### RISK-027 [風險] CVE-2026-33180 HAPI FHIR 認證洩漏風險分析

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#121](https://github.com/Lusnaker0730/CQL/issues/121) |
| 建立日期 | 2026-03-24 |
| 狀態 | open |
| 嚴重度 | 嚴重（認證憑證外洩可導致未授權存取病患資料） |
| 發生機率 | 低（需要目標 FHIR 伺服器發出重導向至惡意伺服器） |

**危害情境：**

後端透過 HAPI FHIR Client 連接外部 FHIR 伺服器（如 EHR 整合）時，若目標伺服器回應 HTTP 3xx 重導向，認證標頭（Bearer Token / Basic Auth）可能被轉發至非預期的第三方伺服器，導致認證資訊洩漏。

**風險控制措施：**

（未填寫）

**殘餘風險：**

極低 — 升級後 HAPI FHIR Client 遵循 RFC 7235，不再在跨域重導向時傳遞認證資訊。


**關聯項目：** #119


---


### RISK-028 [風險] CQL 注入攻擊導致任意 CQL 程式碼執行

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#63](https://github.com/Lusnaker0730/CQL/issues/63) |
| 建立日期 | 2026-03-13 |
| 狀態 | open |
| 嚴重度 | 4 - 重大 (Critical) |
| 發生機率 | 2 - 很少發生 (Remote) |

**危害情境：**

攻擊者透過 CDS Authoring 或 eCQM 視覺化編輯介面，在 Builder 欄位（如 Base Element 名稱、Subpopulation 名稱、Parameter 名稱、外部 CQL Library 名稱/版本號、ValueSet 名稱）中注入含有 CQL 控制字元（雙引號 `"`、反斜線 `\`、換行符）的惡意字串。這些字串經由 CqlArtifactBuilder / EcqmCqlBuilder 傳入 FreeMarker 模板渲染時，未經跳脫直接嵌入 CQL 原始碼，導致：

1. **CQL 定義注入**：攻擊者可在 quoted identifier 中插入 `"` 跳出引號，注入任意 `define` 語句
2. **Include 語句注入**：外部 Library 名稱未加引號且未消毒，可注入完整的 CQL 語句
3. **品質量測邏輯竄改**：注入的 CQL 可改變 eCQM 量測定義，導致品質報告資料不正確
4. **臨床決策偏差**：CDS Artifact 的 Recommendation 邏輯被竄改，可能產生錯誤的臨床建議

影響範圍：6 個 CRITICAL 注入點（base element name、subpopulation name、parameter name、library name、library version、valueset name），涉及 CqlArtifactBuilder、ExpressionCqlEngine、EcqmCqlBuilder 三個核心元件。

**風險控制措施：**

**第一層：輸入驗證（ExpressionTreeValidator）**
- 新增 `CQL_IDENTIFIER_PATTERN`（`[\p{L}\p{N}_\-. ]{1,255}`），在 API 入口層拒絕含有引號、反斜線、控制字元的 identifier
- 驗證範圍：baseElement name、subpopulation name、parameter name、external CQL library_name / library_version
- 驗證失敗時拋出 `ValidationException`，不進入 CQL 產生流程

**第二層：識別符跳脫（ExpressionCqlEngine.escapeCqlIdentifier）**
- 新增 `escapeCqlIdentifier()` 方法，將 `"` 跳脫為 `\"`、`\` 跳脫為 `\`
- 套用於所有 quoted identifier 插入點：baseElementRef、parameterRef、externalCqlRef、arithmetic operand、generic resource query、code declaration、valueset declaration、codesystem declaration
- 即使繞過第一層驗證，注入字元也會被安全跳脫

**第三層：Library 名稱消毒**
- `include` 語句中的 library name 改用白名單 regex `[^a-zA-Z0-9_]` → `_` 消毒
- Library version 套用 `escapeCqlString()` 跳脫單引號
- FreeMarker 模板中 valueset 改為結構化 `{identifier, uri}` 分別套用對應跳脫函式

**第四層：註解注入防護（EcqmCqlBuilder）**
- CQL 註解中的 description 文字移除換行符，防止透過 `\n` 跳出註解注入程式碼

**殘餘風險：**

三層防護機制（驗證 → 跳脫 → 消毒）使 CQL 注入成功的可能性極低。第一層在 API 入口即阻擋惡意輸入；即使繞過驗證，第二層的 `escapeCqlIdentifier` 確保引號被正確跳脫。殘餘風險等級：嚴重度 4 × 機率 1 = 4（低）。建議定期進行 CQL 產生引擎的模糊測試（fuzz testing）以發現潛在邊界情況。


**關聯項目：** - 風險 #58（XSS/注入攻擊）— 本次修復為該風險項的深化控制措施
- 驗證 #61（輸入驗證與注入防護）— 既有驗證覆蓋範圍擴展


---


### RISK-029 [風險] XSS 或注入攻擊導致病患資料外洩或臨床資訊竄改

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#58](https://github.com/Lusnaker0730/CQL/issues/58) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 嚴重度 | 5 - 災難性 (Catastrophic) |
| 發生機率 | 2 - 很少發生 (Remote) |

**危害情境：**

攻擊者透過 CDS 回饋欄位注入惡意 JavaScript（儲存型 XSS），當其他使用者瀏覽 CDS 卡片時執行惡意腳本，竊取 JWT token 或顯示偽造的臨床建議。或透過 CQL 注入構造惡意查詢，擷取未授權的病患資料。Trojan Source 攻擊可使 CQL 程式碼顯示與實際執行的邏輯不同。

**風險控制措施：**

- XSS 三層防護（前端安全渲染 + 後端 HTML 跳脫 + 反序列化器硬化）
- escapeCqlString 防止 CQL 注入
- @NoXss 驗證器套用於所有使用者文字輸入
- Monaco bidi 字元消毒
- DTO @Size 限制防止 DoS payload
- AuditFilter 用於偵測與事後追查

**殘餘風險：**

多層防護機制使 XSS/注入攻擊成功的可能性極低。即使發現新的攻擊向量，audit log 可用於事後追查。殘餘風險等級：嚴重度 5 × 機率 1 = 5（中），需持續進行安全掃描（Trivy）。


**關聯項目：** - 需求：#45
- 設計：#51


---


### RISK-030 [風險] 認證繞過或令牌竊取導致未授權存取 PHI

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#52](https://github.com/Lusnaker0730/CQL/issues/52) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 嚴重度 | 4 - 危急 (Critical) |
| 發生機率 | 2 - 很少發生 (Remote) |

**危害情境：**

攻擊者透過 JWT 令牌竊取（XSS）、Refresh Token 重用攻擊、停用使用者 API Key 未即時失效、或暴力破解等方式繞過認證機制，未經授權存取系統中的受保護健康資訊（PHI），可能導致病患隱私外洩或資料竄改。

**風險控制措施：**

- 設計控制：JWT 雙令牌架構，Access Token 短期有效（15分鐘）
- 設計控制：Refresh Token 輪換 + 重用偵測，偵測到攻擊時撤銷所有 token
- 設計控制：API Key SHA-256 雜湊儲存 + 停用使用者即時檢查
- 防護措施：IP 分級限流防止暴力破解（登入 5次/分鐘）
- 防護措施：CORS 拒絕萬用字元，僅允許明確 origin
- 偵測措施：AuditFilter 記錄所有 PHI 存取的稽核日誌

**殘餘風險：**

實施所有控制措施後，認證繞過的可能性極低。Refresh Token 重用偵測為最後防線，可在令牌被盜時自動切斷攻擊者存取。殘餘風險等級：嚴重度 4 × 機率 1 = 4（中），在可接受範圍。


**關聯項目：** 需求 #35、設計 #43


---


### RISK-031 [風險] eCQM 產生錯誤的 CQL 導致品質量測定義不正確

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#49](https://github.com/Lusnaker0730/CQL/issues/49) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 嚴重度 | 3 - 嚴重 (Serious) |
| 發生機率 | 2 - 很少發生 (Remote) |

**危害情境：**

eCQM 撰寫工具因 FreeMarker 模板邏輯錯誤、元素類型處理不完整、或修飾器組合未預期，產生語義錯誤的 CQL，導致品質量測的 Population Criteria 定義不正確。發佈後執行的量測結果偏離預期，可能誤導品質改善決策。

**風險控制措施：**

- 發佈前 CQL 驗證（translate 必須回傳 0 個錯誤）
- 上線前測試案例驗證
- CQL 預覽面板提供即時審查
- 結構驗證攔截格式錯誤的元素

**殘餘風險：**

發佈前強制 CQL 翻譯驗證 + 測試案例機制可攔截大部分錯誤。語義層級的邏輯錯誤需人工審查。殘餘風險等級：嚴重度 3 × 機率 1 = 3（低）。


**關聯項目：** - 需求：#37
- 設計：#42


---


### RISK-032 [風險] EHR 連線憑證外洩或匯入資料包含 PHI 未妥善保護

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#48](https://github.com/Lusnaker0730/CQL/issues/48) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 嚴重度 | 4 - 危急 (Critical) |
| 發生機率 | 2 - 很少發生 (Remote) |

**危害情境：**

EHR 連線設定中的認證憑證（Bearer Token、密碼、OAuth2 Client Secret）因儲存不當或 API 回傳未遮蔽而外洩。或匯入的病患 FHIR 資料包含真實 PHI，在測試環境中未妥善保護，可能被未授權人員存取。

**風險控制措施：**

- Credentials AES encrypted in database
- API responses mask sensitive fields
- FHIR client uses HTTPS only
- Import records store metadata only (not full FHIR data)
- Date shift feature for de-identification (PAT-007)
- Role-based access control on EHR endpoints

**殘餘風險：**

憑證加密儲存 + API 遮蔽降低外洩風險。匯入的 PHI 資料保護依賴機構的存取控制政策。殘餘風險等級：嚴重度 4 × 機率 1 = 4（中），需搭配機構層級的資料保護措施。


**關聯項目：** - 需求：#39
- 設計：#44


---


### RISK-033 [風險] CQL Builder 產生語法錯誤的 CQL 片段

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#47](https://github.com/Lusnaker0730/CQL/issues/47) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 嚴重度 | 2 - 輕微 (Minor) |
| 發生機率 | 3 - 偶爾發生 (Occasional) |

**危害情境：**

CQL Builder 產生的 CQL 片段因模板邏輯錯誤或特殊字元未正確跳脫，導致語法錯誤或語義不正確的 CQL 被插入編輯器。使用者可能未發現錯誤而直接執行，產生不正確的查詢結果。

**風險控制措施：**

- snippet 插入後自動觸發 CQL 翻譯驗證
- Builder 使用 escapeCqlString 跳脫特殊字元
- Query Builder where 子句自動判斷引號需求 (CQL_LITERAL_RE)

**殘餘風險：**

Builder 產生的 CQL 片段會立即經過翻譯引擎驗證，語法錯誤會即時顯示在編輯器中。殘餘風險等級：嚴重度 2 × 機率 2 = 4（低）。


**關聯項目：** - 需求 #36
- 設計 #41


---


### RISK-034 [風險] 測試案例執行結果不正確導致錯誤的品質量測驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#46](https://github.com/Lusnaker0730/CQL/issues/46) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 嚴重度 | 3 - 嚴重 (Serious) |
| 發生機率 | 2 - 很少發生 (Remote) |

**危害情境：**

測試案例建構器因 FHIR Bundle 序列化錯誤（如 Choice Type 格式不正確）、CQL 執行環境缺少 Measurement Period 參數、或 expectedPopulations 被 React Query refetch 競態重置，導致測試結果不可靠。使用者可能基於錯誤的測試結果認為量測邏輯正確，而實際上存在計算錯誤。

**風險控制措施：**

- Visual ↔ JSON bidirectional sync for data integrity verification
- In-memory execution avoids FHIR server inconsistency (BUG-039 fix)
- Measurement Period parameter always injected (BUG-040 fix)
- expectedPopulations managed by useRef to avoid race conditions (BUG-043 fix)

**殘餘風險：**

修復已知問題後（BUG-039~043），測試案例執行環境穩定。殘餘風險在於使用者設定的預期值可能本身有誤，需人工審查。殘餘風險等級：嚴重度 3 × 機率 1 = 3（低）。


**關聯項目：** - 需求：#38
- 設計：#40


---


### RISK-035 [風險] CDS 建議錯誤導致不當臨床決策

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#31](https://github.com/Lusnaker0730/CQL/issues/31) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 嚴重度 | 5 - 災難性 (Catastrophic) |
| 發生機率 | 2 - 很少發生 (Remote) |

**危害情境：**

CDS Hooks 服務因 Artifact 規則定義錯誤、FHIR 資料不完整、或 CQL 執行異常，產生不正確的臨床建議卡片（例如：未偵測到藥物交互作用、錯誤建議停藥），臨床醫師據此做出不當決策，可能導致病患傷害。

**風險控制措施：**

- 設計控制：CDS Card 強制標示「建議僅供參考，請依臨床專業判斷」
- 設計控制：Artifact 部署前必須通過完整測試案例驗證
- 設計控制：CDS Card indicator 分為 info/warning/critical，讓醫師瞭解建議嚴重度
- 防護措施：CDS 評估失敗時回傳空卡片（graceful degradation），不產生錯誤建議
- 偵測措施：完整 audit log 記錄每次評估的輸入、規則、結果
- 管理控制：Artifact 上線需經臨床專家與資訊人員雙重審核

**殘餘風險：**

即使實施所有控制措施，CDS 系統仍可能因未預見的資料組合產生不適當建議。但透過「僅供參考」標示 + 醫師專業判斷的雙重防線，直接導致嚴重傷害的機率極低。殘餘風險等級：嚴重度 5 × 機率 1 = 5（中），需持續監控。


**關聯項目：** 需求 #25、設計 #28


---


### RISK-036 [風險] 品質量測計算錯誤導致不準確的品質報告

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#30](https://github.com/Lusnaker0730/CQL/issues/30) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 嚴重度 | 3 - 嚴重 (Serious) |
| 發生機率 | 3 - 偶爾發生 (Occasional) |

**危害情境：**

品質量測評估引擎因 Population Criteria CQL 邏輯錯誤或 FHIR 資料解析不正確，導致病患族群分類錯誤（如應納入 Numerator 的病患被排除），產生不準確的品質指標報告，可能誤導品質改善方向。

**風險控制措施：**

- 設計控制：提供測試案例功能，可設定預期族群結果並自動比對
- 設計控制：評估結果顯示每位病患的詳細族群歸屬，方便人工抽檢
- 防護措施：品質報告標示「系統計算結果，需經品質管理人員審核」
- 偵測措施：新舊版本量測結果比對，差異超過閾值時發出警告

**殘餘風險：**

測試案例機制可在部署前驗證計算邏輯，但無法涵蓋所有邊界情況。人工審核機制為最後防線。殘餘風險等級：嚴重度 3 × 機率 2 = 6（中），在可接受範圍。


**關聯項目：** 需求 #24、設計 #27


---


### RISK-037 [風險] CQL 翻譯錯誤導致無效的 ELM 輸出

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#29](https://github.com/Lusnaker0730/CQL/issues/29) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 嚴重度 | 3 - 嚴重 (Serious) |
| 發生機率 | 2 - 很少發生 (Remote) |

**危害情境：**

CQL 翻譯引擎因版本不相容或輸入異常，產生語法正確但語義錯誤的 ELM JSON，使得後續 CQL 執行引擎基於錯誤的邏輯進行病患評估，導致不正確的品質指標計算或臨床建議。

**風險控制措施：**

- 設計控制：翻譯後自動執行 ELM 結構驗證（schema validation）
- 設計控制：翻譯結果中的 annotation/error/warning 全部回傳前端顯示
- 防護措施：提供「測試案例」功能，讓使用者可驗證 CQL 邏輯正確性
- 偵測措施：記錄所有翻譯請求與結果的 audit log

**殘餘風險：**

實施控制措施後，殘餘風險降至可接受範圍。ELM 結構驗證可攔截大部分語法層級錯誤，測試案例機制讓使用者可在部署前驗證語義正確性。殘餘風險等級：嚴重度 3 × 機率 1 = 3（低）。


**關聯項目：** 需求 #23、設計 #26


---



## 4. 風險統計

| 統計項目 | 數量 |
|---------|------|
| 總風險項目 | 37 |
| 開放中 | 36 |
| 已關閉 | 1 |

## 5. 整體殘餘風險評估

<!-- 由品質管理人員填寫 -->

---

*本文件由 CQL Platform 法規文件產生器自動產生*
