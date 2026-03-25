# 風險管理報告 (Risk Management Report)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-RMR-1.0.0 |
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


### RISK-001 [風險] CQL 注入攻擊導致任意 CQL 程式碼執行

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


### RISK-002 [風險] XSS 或注入攻擊導致病患資料外洩或臨床資訊竄改

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


### RISK-003 [風險] 認證繞過或令牌竊取導致未授權存取 PHI

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


### RISK-004 [風險] eCQM 產生錯誤的 CQL 導致品質量測定義不正確

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


### RISK-005 [風險] EHR 連線憑證外洩或匯入資料包含 PHI 未妥善保護

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


### RISK-006 [風險] CQL Builder 產生語法錯誤的 CQL 片段

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


### RISK-007 [風險] 測試案例執行結果不正確導致錯誤的品質量測驗證

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


### RISK-008 [風險] CDS 建議錯誤導致不當臨床決策

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


### RISK-009 [風險] 品質量測計算錯誤導致不準確的品質報告

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


### RISK-010 [風險] CQL 翻譯錯誤導致無效的 ELM 輸出

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
| 總風險項目 | 10 |
| 開放中 | 10 |
| 已關閉 | 0 |

## 5. 整體殘餘風險評估

<!-- 由品質管理人員填寫 -->

---

*本文件由 CQL Platform 法規文件產生器自動產生*
