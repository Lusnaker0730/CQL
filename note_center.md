一、後端 — Critical 問題                                                                                                                                                                                                                                                                          1. DashboardService 全表載入 + 記憶體內過濾                                                                                                                                                                                                                                                     
  檔案: backend/src/main/java/com/cqlplatform/service/measure/DashboardService.java

  getEnhancedDashboard()、getTrends()、getDepartmentDrilldown() 全部呼叫 findAll() 載入所有 measures 和 reports，再用 Java Stream 過濾。若有 10K 
  measures + 100K reports，每次 Dashboard 請求都會載入所有資料到記憶體。

  // 現狀：載入全部再過濾
  List<MeasureDefinitionEntity> measures = definitionRepository.findAll();
  measures = measures.stream()
      .filter(m -> department.equals(m.getDepartment()))
      .collect(Collectors.toList());

  優化：新增 Repository 查詢方法，讓資料庫做過濾和聚合。

  2. CDS Service Eager Loading (N+1)

  檔案: backend/src/main/java/com/cqlplatform/entity/CdsServiceConfigEntity.java:71

  prefetchItems 使用 FetchType.EAGER，每次查詢 CDS Service 都會自動載入所有 prefetch 項目，產生笛卡兒積。

  優化：改為 FetchType.LAZY，需要時用 JOIN FETCH 明確載入。

  3. FHIR Client 每次請求重建

  檔案: backend/src/main/java/com/cqlplatform/service/fhir/FhirDataProviderService.java:66-86

  每次 FHIR 呼叫都 createClient(fhirServerUrl) 建立新連線，無法重用 TCP 連線。

  優化：用 ConcurrentHashMap 快取 client 實例，搭配 TTL 過期機制。

  ---
  二、後端 — High 問題

  4. 缺少複合索引

  多個常用查詢路徑缺少資料庫索引：
  - measure_definition 缺少 (owner_username)、(status, department) 複合索引
  - measure_report 缺少 (measure_definition_id, created_at DESC) 索引
  - cql_library 缺少 (owner_username)、(access_level) 索引

  5. 所有 List API 缺少分頁

  /api/measures、/api/libraries、/api/cds-services 等端點回傳完整結果集，無 Pageable 支援。

  6. getAllPatientIds() 載入全部病患

  檔案: FhirDataProviderService.java:172-204

  載入完整 Patient 資源只為取得 ID，且無批次大小控制。大型 FHIR Server (100K+ patients) 會導致 OOM。

  7. CQL Library 共享查詢效能差

  檔案: CqlLibraryService.java:102-106

  getSharedLibraries() 呼叫 findAll() 後在記憶體中解析每筆記錄的 JSON sharedWithList 來過濾。

  ---
  三、後端 — Medium 問題

  ┌─────┬────────────────────────────────┬──────────────────────────────────────┬───────────────────────────────────┐
  │  #  │              問題              │                 檔案                 │               說明                │
  ├─────┼────────────────────────────────┼──────────────────────────────────────┼───────────────────────────────────┤
  │ 8   │ CQL 驗證快取 key 用 hashCode() │ CqlTranslationService.java:101       │ 可能碰撞，空白差異導致 cache miss │
  ├─────┼────────────────────────────────┼──────────────────────────────────────┼───────────────────────────────────┤
  │ 9   │ ValueSet 快取太小 (500)        │ CqlConfig.java:65-75                 │ 大量術語操作會頻繁驅逐快取        │
  ├─────┼────────────────────────────────┼──────────────────────────────────────┼───────────────────────────────────┤
  │ 10  │ 缺少 Response 壓縮             │ application.yml                      │ 未啟用 server.compression.enabled │
  ├─────┼────────────────────────────────┼──────────────────────────────────────┼───────────────────────────────────┤
  │ 11  │ FHIR fallback 重複查詢         │ FhirDataProviderService.java:352-375 │ 空結果觸發二次查詢，無快取        │
  ├─────┼────────────────────────────────┼──────────────────────────────────────┼───────────────────────────────────┤
  │ 12  │ LIKE 搜尋無全文索引            │ MeasureDefinitionRepository.java     │ %keyword% 全表掃描                │
  └─────┴────────────────────────────────┴──────────────────────────────────────┴───────────────────────────────────┘

  ---
  四、前端 — High 問題

  13. EditorPage 頻繁重渲染

  檔案: frontend/src/pages/EditorPage.tsx:62

  const { cqlContent, isTranslating, errors, elmJson, cursorPosition } = useSelector(state => state.editor)

  物件解構每次 render 產生新參考，導致任何 editor state 變化都觸發完整重渲染 (582 行元件)。

  優化：拆分為獨立的 useSelector 呼叫。

  14. 搜尋欄缺少 Debounce

  檔案: frontend/src/components/measure/MeasureLibrary.tsx:100-103

  使用者打字時每個 keystroke 觸發一次 API 請求，無防抖。

  15. 長列表無虛擬化

  MeasureLibrary、ArtifactList 等表格渲染完整 DOM，1000+ 筆資料會產生大量 DOM 節點。

  優化：引入 react-virtuoso 或 react-window。

  16. i18n 兩語言全量載入

  檔案: frontend/src/i18n.ts

  20 個 JSON 檔 (~244KB) 在啟動時全部載入，使用者只需一種語言。

  優化：改用動態 import() 延遲載入未選擇的語言。

  ---
  五、前端 — Medium 問題

  ┌─────┬────────────────────────────┬────────────────────────┬──────────────────────────────────────────────────┐
  │  #  │            問題            │          檔案          │                       說明                       │
  ├─────┼────────────────────────────┼────────────────────────┼──────────────────────────────────────────────────┤
  │ 17  │ Monaco Editor 未 Lazy Load │ CqlEditor.tsx          │ 非編輯頁面也載入 885KB                           │
  ├─────┼────────────────────────────┼────────────────────────┼──────────────────────────────────────────────────┤
  │ 18  │ Recharts 未獨立 chunk      │ vite.config.ts         │ Dashboard 圖表庫混在主 bundle                    │
  ├─────┼────────────────────────────┼────────────────────────┼──────────────────────────────────────────────────┤
  │ 19  │ 缺少 useCallback           │ EditorPage.tsx:204-250 │ Event handler 每次 render 新建，觸發子元件重渲染 │
  ├─────┼────────────────────────────┼────────────────────────┼──────────────────────────────────────────────────┤
  │ 20  │ React Query 缺少 staleTime │ useMeasures.ts         │ 多數查詢未設定，視窗切換就重抓                   │
  ├─────┼────────────────────────────┼────────────────────────┼──────────────────────────────────────────────────┤
  │ 21  │ Waterfall API 請求         │ MeasuresPage.tsx:17-29 │ 選擇 measure 後同步等待完整資料載入              │
  └─────┴────────────────────────────┴────────────────────────┴──────────────────────────────────────────────────┘

  ---
  六、優化路線圖

  Phase 1 — Quick Wins（1-2 天，立即改善）
  ├── 啟用 server.compression.enabled
  ├── EditorPage selector 拆分 + useCallback
  ├── MeasureLibrary 搜尋加 debounce (300ms)
  ├── React Query defaultOptions 加 refetchOnWindowFocus: false
  └── CDS prefetchItems 改 FetchType.LAZY

  Phase 2 — 資料庫優化（3-5 天）
  ├── 新增 Flyway migration 補缺少的索引
  ├── DashboardService 改用 @Query 聚合查詢
  ├── CqlLibraryService 共享查詢改用 DB 過濾
  ├── List API 加入 Pageable 分頁
  └── FHIR Client 快取池

  Phase 3 — 前端 Bundle 優化（3-5 天）
  ├── Monaco Editor lazy load
  ├── Recharts 獨立 chunk
  ├── i18n 改為動態載入
  └── 長列表加 react-virtuoso 虛擬化

  Phase 4 — 進階優化（1-2 週）
  ├── getAllPatientIds 加分批控制
  ├── CQL 驗證快取 key 正規化
  ├── ValueSet 快取擴增至 2000
  ├── FHIR fallback 加結果快取
  └── PostgreSQL 全文索引 (GIN)

  ---
  預估效能提升

  ┌──────────────────────────┬───────────────────────────────────────────┐
  │         優化項目         │                 預期改善                  │
  ├──────────────────────────┼───────────────────────────────────────────┤
  │ DashboardService DB 查詢 │ API 回應時間 10-50x 提升（大量資料時）    │
  ├──────────────────────────┼───────────────────────────────────────────┤
  │ CDS Eager→Lazy           │ CDS 列表查詢 3-5x 提升                    │
  ├──────────────────────────┼───────────────────────────────────────────┤
  │ FHIR Client 快取         │ FHIR 操作 2-3x 提升（省去 TCP handshake） │
  ├──────────────────────────┼───────────────────────────────────────────┤
  │ EditorPage 重渲染修復    │ 編輯器打字流暢度 顯著提升                 │
  ├──────────────────────────┼───────────────────────────────────────────┤
  │ 搜尋 Debounce            │ API 呼叫數減少 ~80%                       │
  ├──────────────────────────┼───────────────────────────────────────────┤
  │ i18n 延遲載入            │ 首次載入省 ~120KB                         │
  ├──────────────────────────┼───────────────────────────────────────────┤
  │ Monaco Lazy Load         │ 非編輯頁省 ~885KB                         │
  ├──────────────────────────┼───────────────────────────────────────────┤
  │ Response 壓縮            │ 傳輸量減少 60-70%                         │
  └──────────────────────────┴───────────────────────────────────────────┘

  需要我開始實作哪個階段的優化？