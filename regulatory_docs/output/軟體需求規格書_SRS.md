# 軟體需求規格書 (Software Requirements Specification)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-SRS-1.0.0 |
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

本文件定義 CQL Platform 的軟體需求規格，依據 IEC 62304 醫療器材軟體生命週期流程標準。

## 2. 範圍

本文件涵蓋所有標記為 `IEC62304:需求` 的 GitHub Issues，版本 1.0.0。

## 3. 軟體需求列表


### SRS-001 [需求] 品質指標儀表板應提供視覺化數據分析與閾值警示

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#60](https://github.com/Lusnaker0730/CQL/issues/60) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

系統應提供品質指標儀表板，以圖表方式呈現品質量測執行結果的趨勢分析。支援：Recharts 圖表（折線圖、長條圖、圓餅圖）、品質指標達成率趨勢、科別比較、閾值警示（低於標準時標紅提醒）、時間範圍篩選、品質報告匯出。

**臨床情境：**

品質管理部門需要從宏觀角度監控全院品質指標的達成狀態，快速識別低於閾值的指標並採取改善行動。視覺化儀表板取代傳統的數字報表，提高資訊可讀性。

**驗收條件：**

1. Recharts 圖表正確呈現量測趨勢
2. 科別分組篩選
3. 閾值警示（紅色標示）
4. 時間範圍選擇（月/季/年）
5. 報告下載功能



---


### SRS-002 [需求] 系統應支援繁體中文與英文雙語介面

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#50](https://github.com/Lusnaker0730/CQL/issues/50) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

系統應提供完整的國際化（i18n）支援，包含繁體中文（zh-TW）與英文（en）雙語介面。所有 UI 文字透過 i18n 機制載入，禁止硬編碼。使用者可在 Header 工具列即時切換語言，偏好設定儲存於 localStorage。MUI 元件庫亦隨語言切換。10 個 namespace 涵蓋所有模組。

**臨床情境：**

台灣醫療機構使用者以繁體中文為主要語言，但部分國際合作場景需要英文介面。雙語支援確保所有使用者都能正確理解系統功能與臨床資訊。

**驗收條件：**

1. 所有 UI 文字透過 useTranslation hook 載入
2. Header 語言切換器即時生效
3. MUI locale 同步切換
4. 語言偏好持久化（localStorage）
5. zh-TW 動態載入（bundle optimization）



---


### SRS-003 [需求] 系統應具備完整的輸入驗證與注入攻擊防護

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#45](https://github.com/Lusnaker0730/CQL/issues/45) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 高 (High) |
| 安全性等級 | C - 可能造成死亡或嚴重傷害 |

**需求描述：**

系統應對所有使用者輸入實施完整的安全驗證與防護，包含：XSS（跨站腳本攻擊）三層防護、CQL 注入防護（escapeCqlString）、SQL LIKE 萬用字元注入防護、SSRF URL 驗證、Trojan Source 雙向字元偵測、DTO @Size 限制防 DoS、Mass Assignment 防護、CDS 儲存型 XSS 防護、Monaco 編輯器貼上消毒。

**臨床情境：**

作為處理受保護健康資訊的醫療軟體，系統必須抵禦各類注入攻擊。XSS 攻擊可竊取使用者 session 或顯示誤導性臨床資訊。CQL 注入可能擷取未授權的病患資料。

**驗收條件：**

1. CDS Card XSS 三層防護（前端安全渲染 + 後端 HTML 跳脫 + 反序列化器）
2. CQL 字串跳脫（單引號、反斜線）
3. LIKE 查詢萬用字元跳脫（%, _）
4. DTO @Size 限制所有字串欄位
5. SSRF URL 驗證（僅允許 https）
6. Trojan Source bidi 字元偵測並移除
7. @NoXss 自訂驗證器
8. API Key SHA-256 雜湊儲存



---


### SRS-004 [需求] 系統應支援 EHR/HIS 系統連接與病患資料匯入

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#39](https://github.com/Lusnaker0730/CQL/issues/39) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 高 (High) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

系統應提供 EHR/HIS（電子病歷/醫院資訊系統）整合連接功能，支援：FHIR R4 連線管理（新增/編輯/刪除/測試連線）、病患搜尋（姓名/ID/日期範圍）、病患 FHIR 資料匯入、匯入歷史紀錄、直接匯入至測試案例建構器。連線支援多種認證方式（Bearer Token、Basic Auth、OAuth2）。

**臨床情境：**

品質管理人員需要從醫院 HIS 系統匯入真實病患資料來驗證品質量測邏輯。手動建立測試資料無法反映真實臨床資料的複雜性。EHR 連接器讓使用者直接搜尋並匯入病患的 FHIR 資料，提高測試的真實性。

**驗收條件：**

1. FHIR 連線 CRUD + 連線測試
2. 病患搜尋（多條件組合）
3. 病患 FHIR Bundle 匯入
4. 匯入歷史紀錄查詢
5. 「Import from EHR」一鍵匯入至測試案例
6. 支援 Bearer/Basic/OAuth2 認證



---


### SRS-005 [需求] 測試案例建構器應提供視覺化 FHIR Bundle 編輯與族群驗證

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#38](https://github.com/Lusnaker0730/CQL/issues/38) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 中 (Medium) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

系統應提供測試案例視覺化建構器，讓使用者透過表單介面建立測試用 FHIR Bundle（無需手寫 JSON），設定預期族群結果，並與實際執行結果自動比對。支援：16 種 FHIR 資源表單、必填/選填屬性分組、Reference 自動連結、Visual Builder ↔ JSON 雙向同步、批次匯入與日期平移、預期 vs 實際族群比對。

**臨床情境：**

品質量測驗證需要建立大量測試病患資料。手寫 FHIR Bundle JSON 容易出錯且耗時。視覺化建構器讓品質管理師快速建立測試資料，驗證量測邏輯正確性。

**驗收條件：**

- Visual Builder 表單建立 FHIR 資源
- JSON ↔ Visual 雙向同步
- Reference auto-linking
- 預期族群設定（IP/Denom/Numer/Excl）
- 批次執行比對 expected vs actual
- 測試結果詳細報告（PopulationComparison）



---


### SRS-006 [需求] eCQM 撰寫工具應提供視覺化品質量測定義與 CQL 自動產生

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#37](https://github.com/Lusnaker0730/CQL/issues/37) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 中 (Medium) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

系統應提供 eCQM（Electronic Clinical Quality Measures）視覺化撰寫工具，讓品質管理人員透過表單介面定義品質量測的 Population Criteria（Initial Population、Denominator、Numerator、Exclusion 等），系統自動產生對應的 CQL 語句。支援：外部 CQL 程式庫引用、基礎元素複用、參數定義、FreeMarker 模板驅動的 CQL 產生、發佈前 CQL 驗證、直接發佈至 MeasureDefinition。

**臨床情境：**

品質管理部門需要建立醫療品質量測指標（如：糖尿病 HbA1c 控制率），但直接撰寫 CQL 需要程式技能。視覺化撰寫工具讓品質管理師專注於臨床邏輯，系統自動產生標準 CQL 並發佈為可執行的品質量測。

**驗收條件：**

1. 視覺化定義 Population Criteria
2. 自動產生 CQL（FreeMarker 模板）
3. CQL 預覽面板即時更新
4. 外部 CQL 程式庫上傳/解析/引用
5. 發佈前 CQL 翻譯驗證通過才允許發佈
6. 發佈後自動建立 MeasureDefinition + CQL Library
7. 工作區自動儲存 + Ctrl+S



---


### SRS-007 [需求] CQL Builder 應提供視覺化元件輔助使用者建構 CQL 語句

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#36](https://github.com/Lusnaker0730/CQL/issues/36) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

系統應提供 CQL Builder 視覺化面板，讓不熟悉 CQL 語法的使用者透過表單式介面建構 CQL 語句。包含：Includes（引用庫）、ValueSets（值集）、Codes（代碼，含 FHIR 代碼瀏覽器）、Parameters（參數）、Definitions（定義，含 Query Builder）、Functions（函數）六大區段。一鍵插入產生的 CQL 片段至 Monaco 編輯器。

**臨床情境：**

臨床品質管理師需要建構 CQL 查詢語句，但多數使用者不熟悉 CQL 語法。視覺化建構器降低使用門檻，讓使用者專注於臨床邏輯而非語法細節。

**驗收條件：**

- [ ] 六個區段可展開/收合
- [ ] ValueSet/Code 搜尋與選取
- [ ] Query Builder 支援 with/without/let/distinct
- [ ] 產生的 CQL 片段語法正確
- [ ] 一鍵插入至編輯器游標位置
- [ ] FHIR 代碼瀏覽器可分組瀏覽



---


### SRS-008 [需求] 系統應提供安全的認證授權機制防止未授權存取

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#35](https://github.com/Lusnaker0730/CQL/issues/35) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 高 (High) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

系統應實作完整的認證授權機制，包含：JWT 雙令牌架構（Access Token + Refresh Token）、令牌滑動視窗過期、令牌輪換與重用偵測、API Key 認證、停用使用者即時失效、IP 分級限流、大型 Payload 加權限流。

**臨床情境：**

醫療資訊系統存取受保護健康資訊（PHI），必須確保只有經授權的使用者才能存取系統功能。未授權存取可能導致病患隱私外洩或資料竄改。

**驗收條件：**

- [ ] JWT Access Token 有效期 15 分鐘，Refresh Token 滑動視窗 7 天
- [ ] Refresh Token 輪換機制：每次刷新產生新 token，舊 token 立即失效
- [ ] 偵測 Refresh Token 重用時撤銷該使用者所有 token
- [ ] 停用使用者的 API Key 應立即失效，無法繼續存取
- [ ] API 限流：登入 5次/分鐘、一般 API 60次/分鐘、CQL 執行 10次/分鐘
- [ ] CORS 配置拒絕萬用字元 origin



---


### SRS-009 [需求] CDS Hooks 應根據臨床決策規則產生建議卡片

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#25](https://github.com/Lusnaker0730/CQL/issues/25) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 高 (High) |
| 安全性等級 | C - 可能造成死亡或嚴重傷害 |

**需求描述：**

CDS Hooks 服務應能根據已部署的臨床決策支援（CDS）規則，在收到 FHIR 資料時自動評估並產生建議卡片（CDS Cards）。卡片內容應包含建議文字、指標來源、嚴重等級。

**臨床情境：**

臨床醫師在開立醫囑時，系統即時觸發 CDS Hooks，根據病患的 FHIR 資料（藥物、檢驗、診斷等）評估潛在風險並提供臨床建議。例如：藥物交互作用警示、高出血風險提醒。

**驗收條件：**

- [ ] 正確接收 CDS Hooks 請求（patient-view、order-sign 等）
- [ ] 根據 Artifact 定義的 Inclusions/Exclusions 評估病患
- [ ] 產生正確格式的 CDS Card（符合 CDS Hooks 規範）
- [ ] 建議卡片包含 summary、detail、indicator、source
- [ ] 支援多條件 Subpopulation 分支建議



---


### SRS-010 [需求] 品質量測執行結果應正確計算病患族群

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#24](https://github.com/Lusnaker0730/CQL/issues/24) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 中 (Medium) |
| 安全性等級 | B - 可能造成非嚴重傷害 |

**需求描述：**

品質量測（Measure）執行引擎應根據 CQL 定義的 Population Criteria 正確識別並分類病患，包括 Initial Population、Denominator、Numerator、Exclusion 等族群。計算結果應與手動驗算一致。

**臨床情境：**

品質管理部門執行醫療品質指標評估時，系統需要正確計算各族群人數。若計算錯誤，可能導致品質指標報告不準確，影響醫療機構的品質改善決策。

**驗收條件：**

- [ ] Initial Population 正確識別符合條件的病患
- [ ] Denominator/Numerator 正確分類
- [ ] Exclusion 條件正確排除不符合的病患
- [ ] 計算結果與測試案例預期值一致
- [ ] 支援 proportion、continuous-variable、cohort 三種量測類型



---


### SRS-011 [需求] CQL 翻譯服務應在 3 秒內完成回應

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#23](https://github.com/Lusnaker0730/CQL/issues/23) |
| 建立日期 | 2026-03-12 |
| 狀態 | open |
| 風險等級 | 低 (Low) |
| 安全性等級 | A - 不會造成傷害 |

**需求描述：**

系統應能在 3 秒內完成 CQL 語句的翻譯（translate）並回傳 ELM JSON 結果。當翻譯時間超過 3 秒時，應顯示進度指示器。

**臨床情境：**

臨床品質管理師在編輯 CQL 語句時，需要即時翻譯回饋來確認語法正確性。過長的等待時間會影響工作效率，導致使用者放棄使用系統而改用手動方式。

**驗收條件：**

- [ ] 一般 CQL 語句（< 100 行）翻譯時間 < 3 秒
- [ ] 翻譯過程中顯示 loading 指示器
- [ ] 翻譯失敗時顯示明確的錯誤訊息
- [ ] API 回傳包含完整的 ELM JSON 結構



---



## 4. 需求統計

| 統計項目 | 數量 |
|---------|------|
| 總需求數 | 11 |
| 開放中 | 11 |
| 已關閉 | 0 |

---

*本文件由 CQL Platform 法規文件產生器自動產生*
