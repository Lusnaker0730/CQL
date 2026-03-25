# 軟體驗證報告 (Software Verification Report)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-SVR-1.0.0 |
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


### VER-001 [驗證] CQL 注入防護機制驗證

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


### VER-002 [驗證] 品質指標儀表板資料正確性驗證

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


### VER-003 [驗證] 輸入驗證與注入防護安全性驗證

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


### VER-004 [驗證] 認證授權機制安全性驗證

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


### VER-005 [驗證] eCQM CQL 產生與發佈流程驗證

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


### VER-006 [驗證] CQL Builder 視覺化建構功能驗證

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


### VER-007 [驗證] 國際化雙語介面功能驗證

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


### VER-008 [驗證] EHR 整合連接器功能與安全性驗證

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


### VER-009 [驗證] 測試案例建構器功能與執行正確性驗證

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


### VER-010 [驗證] CDS Hooks 建議卡片產生正確性驗證

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


### VER-011 [驗證] 品質量測族群計算正確性驗證

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


### VER-012 [驗證] CQL 翻譯服務效能與正確性驗證

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
| 總驗證項目 | 12 |
| 通過 | 9 |
| 失敗 | 0 |
| 待執行 | 0 |

## 6. 驗證結論

<!-- 由品質管理人員填寫 -->

---

*本文件由 CQL Platform 法規文件產生器自動產生*
