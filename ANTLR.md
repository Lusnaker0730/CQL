手刻的肥大語法定義檔
我的提問：utils/cqlSyntax.ts (48KB) 這個 Monaco CQL 語法檔是如何維護的？CQL 是一個龐大且會更新的標準，48KB 的規則如果是工程師手刻 Regex，遇到邊界條件（Edge cases）時非常容易崩潰。
建議：這類檔案應該從官方的 ANTLR grammar 自動生成（轉譯成 Monaco 的 Monarch 語法），而不是手動維護。

  已完成的基礎建設                                                    
  你們目前的底子其實不錯：                                         
  
  ┌───────────┬────────────────────────────────────────────────┐
  │   能力    │                      現狀                      │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ FHIR R4   │ 完整（search/read/create/update/delete）       │   
  │ CRUD      │                                                │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ 認證方式  │ None / Basic / Bearer / SMART Backend Services │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ 憑證安全  │ 欄位級加密儲存，API 不外洩                     │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ 容錯機制  │ Resilience4j circuit breaker + retry（3        │   
  │           │ 個實例）                                       │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ 病人匯入  │ $everything + 手動 fallback 搜尋               │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ 術語服務  │ 本地 IG → VSAC → 公開 FHIR tx server 串接鏈    │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ 批次操作  │ Bundle batch/transaction 支援                  │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ Bulk      │ $export kick-off + polling                     │   
  │ Export    │                                                │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ 連線管理  │ EHR Connection CRUD +                          │   
  │           │ 連線測試（CapabilityStatement）                │   
  ├───────────┼────────────────────────────────────────────────┤   
  │ 權限控制  │ RBAC（ADMIN / DEPARTMENT_ADMIN）+ 部門級過濾   │   
  └───────────┴────────────────────────────────────────────────┘   

  ---
  缺口分析：需要完成的工程

  一、必做（P0）— 沒有就無法上線

  1. TLS / mTLS 安全通訊

  - 目前 SSL 設定在 application.yml 是被註解掉的
  - 醫院內網多要求 mutual TLS（雙向憑證驗證）
  - 需在 FhirClientFactory 加入自訂 TrustStore / KeyStore 載入邏輯 
  - 工作量：~3-5 天

  2. 稽核日誌（Audit Trail）

  - 目前只有 timestamp 級別的軟稽核，不符合醫療合規要求
  - 需要：誰、何時、對哪個病人、做了什麼 FHIR 操作
  - 建議產生 FHIR AuditEvent resource 或寫入獨立稽核表
  - 台灣個資法 + 醫院評鑑需要完整存取日誌
  - 工作量：~5-7 天

  3. 病人同意權（Consent）檢查

  - 匯入病人資料前需檢查是否有同意書
  - 需查詢 FHIR Consent resource 或對接醫院同意管理系統
  - 工作量：~3-5 天

  4. 非同步批次匯入

  - 現在所有匯入都是同步的，大量病人匯入會 timeout
  - 需要：背景排程匯入 + 進度追蹤 + 失敗重試
  - 建議用 Spring Async + 資料庫 job queue
  - 工作量：~5-7 天

  5. 錯誤恢復機制

  - 沒有 dead-letter queue，匯入失敗就消失了
  - 需要失敗紀錄表 + 重試機制 + 管理介面
  - 工作量：~3-5 天

  ---
  二、強烈建議（P1）— 大多數醫院會要求

  6. FHIR Subscription（即時推送）

  - 目前只有 pull 模式（手動搜尋/匯入），沒有 push
  - 醫院端新增/更新病人資料時，平台無法即時收到通知
  - 需實作 FHIR R4 Subscription（REST-hook 或 WebSocket）
  - 工作量：~7-10 天

  7. HL7 v2.x 轉接層

  - 台灣多數醫院 HIS 仍以 HL7 v2 ADT/ORU 為主要訊息格式
  - 需要 v2 → FHIR 轉換器（可用 HAPI v2 library 或 LinuxForHealth  
  hl7-to-fhir）
  - 至少支援 ADT（入出院）、ORU（檢驗報告）、ORM（醫囑）
  - 工作量：~10-15 天

  8. 病人身份比對（MPI）

  - 跨系統匯入時需要病人去重 / 身份比對
  - 台灣可用身分證字號 + 生日做 deterministic matching
  - 需要 Patient $match 操作或自建比對邏輯
  - 工作量：~5-7 天

  9. 台灣在地化 Profile 驗證

  - 需要驗證資料符合 TW Core IG profile
  - 目前 FhirValidationService 存在但未針對 TW Core 做預載入       
  - 需下載 TW Core IG package 並預載入 StructureDefinition
  - 工作量：~3-5 天

  10. 連線健康監控儀表板

  - 目前只有基本的 Counter/Timer metrics
  - 需要：每個 EHR 連線的延遲、錯誤率、可用性歷史圖表
  - Circuit breaker 狀態即時顯示
  - 工作量：~5-7 天

  ---
  三、建議做（P2）— 規模化後需要

  11. 大量資料分頁優化

  - 目前硬編碼 10,000 筆上限，cursor-based pagination 未實作       
  - 百萬級病人的醫院需要 scroll / continuation token
  - 工作量：~3-5 天

  12. 多 FHIR Server 連線池

  - 目前全域 50 個 client 上限，大型醫院系統對接可能不夠
  - 需要 per-connection 的連線池管理
  - 工作量：~3-5 天

  13. 資料保留策略

  - 匯入的 bundle_json 無自動清理機制
  - 長期累積會造成 DB 膨脹
  - 需要 TTL 策略 + 定期清理排程
  - 工作量：~2-3 天

  14. FHIR Messaging ($process-message)

  - 部分醫院使用 FHIR Message 模式交換資料
  - 工作量：~5-7 天

  15. OAuth2 Authorization Code Flow

  - 目前只有 Backend Services（machine-to-machine）
  - 如需用戶級別的 FHIR 授權（例如護理師代表病人查詢），需要       
  Authorization Code + PKCE
  - 工作量：~5-7 天

  ---
  工作量總覽

  ┌─────────────┬────────┬────────────┐
  │   優先級    │ 項目數 │  預估工時  │
  ├─────────────┼────────┼────────────┤
  │ P0 必做     │ 5 項   │ ~19-29 天  │
  ├─────────────┼────────┼────────────┤
  │ P1 強烈建議 │ 5 項   │ ~30-44 天  │
  ├─────────────┼────────┼────────────┤
  │ P2 規模化   │ 5 項   │ ~18-27 天  │
  ├─────────────┼────────┼────────────┤
  │ 合計        │ 15 項  │ ~67-100 天 │