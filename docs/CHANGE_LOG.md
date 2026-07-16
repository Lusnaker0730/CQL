# Change Log

> 所有功能改善、重構、Bug 修復與安全修補的統一記錄。
> 由 BUGFIX_LOG.md（BUG-001 ~ BUG-082）與 PATCH_LOG.md（PAT-001 ~ PAT-026）合併而成。

---

## 總覽索引

| ID | 類型 | 日期 | 範圍 | 標題 | 備註 | Commit |
|-----|------|------|------|------|------|--------|
| PAT-206 | ✨ feat | 2026-07-16 | 健康存摺 FHIR bundle 上傳匯入(`PatientImportService` + `EhrIntegrationController` + 前端 `FhirBundleUpload`)+ TW Core IG v1.0.0 版本回報修正 | **讓診所上傳健康存摺匯出的 FHIR bundle 檔案直接匯入,不需 EHR 連線** — roadmap「資料進來」階段。**經 web 查證調整定位**:原任務為對齊「健保快譯通」CSV 格式,但查證確認「健保快譯通」= 「全民健保行動快易通│健康存摺」App,其匯出是 **XML/JSON 且採 HL7 FHIR 標準**(衛福部有電子病歷 FHIR 推動專區),**根本不是 CSV**;平台已支援 FHIR 匯入(但僅限 EHR 連線 $everything)。故定位為上傳 FHIR bundle 檔案匯入 —— 台灣實際的「隨插即用」入口。**另修版本回報 bug**:磁碟上 package.tgz **已是 v1.0.0**(含 TWComposition/TWCondition 等電子交換資源),但 `FhirImplementationGuideService` 硬編碼回報 `0.3.2` —— 版本被低報,修正為 1.0.0。**實作**:(1) **V69** patient_import.connection_id 改 nullable(FK 保留)+ 加 `source`('ehr'/'fhir-upload');(2) entity connectionId 放寬 + source 欄位;(3) service 抽出共用 `persistBundleImport`(EHR 與上傳路徑共用落地),新增 `importUploadedBundle`(HAPI 解析、非法/空→ValidationException/400、取 Patient FHIR id、connectionId=null、source='fhir-upload');(4) `POST /api/ehr/import/fhir-bundle`(multipart + optional measureId,限 ADMIN/DEPARTMENT_ADMIN);(5) 前端 `ehrApi.importFhirBundle` + `FhirBundleUpload` 元件掛 FhirPage EHR 分頁 + i18n(en/zh-TW)。**沿用剛完成的租戶硬化**(patient_import.tenant_id NOT NULL、effectiveTenantId、每讀租戶範圍化),上傳 bundle 一律落呼叫者租戶。**不建 CSV parser**(健康存摺是 FHIR,CSV 對不到台灣標準)。本機 fresh **後端 1726 案綠**(PatientImportServiceTest 4:租戶隔離落地/source/connectionId null、帶 measure 建 test case、非法/空 bundle 拒絕不落資料)+ **前端 tsc 乾淨**、FhirPage/ehr 測試 9 綠。**過程記錄**:MUI v9 Stack/Typography `fontWeight`/`flexWrap`/`alignItems` 直接 prop 破壞 overload 解析(誤要求 component),對照專案慣例改為 direction+spacing 當 prop、其餘進 sx。**殘留風險**:未設上傳檔案大小上限、未做 TW Core profile 結構驗證(FhirValidationService 已存在可後續掛)。**TFDA 追溯**:#780(安全性等級 B)/設計 #781/驗證 #782/風險 #783。 | 資料入口;健康存摺 FHIR;非 CSV | |
| BUG-139 | 🔒 fix | 2026-07-16 | 統一分享語意:CDS shared 收斂為租戶內(`CdsServiceConfigRepository` + `CdsHooksService` + 前端 i18n) | **統一「分享」語意 — CDS shared 收斂為租戶內,收回 Option A 全平台匿名發現面** — BUG-135 已把 `cql_library.accessLevel='public'` 收斂為租戶內,但 `cds_service_config.shared` 仍是 Option A 的**發佈到全平台**(BUG-137 風險 #768 列為「分享語意雙重定義」殘留風險)—— 同產品「分享」兩種相反語意,使用者可能誤以為 CDS share 只給院內同事。**使用者決策:全部收斂成租戶內。** 收斂前的跨租戶暴露:`getServicesForUser`(列表 `OR c.shared=true` 未綁租戶)、`getService`(`findReadableByIdWithPrefetch` 可讀他院 shared 完整 config 含 cqlContent)、`getSharedServiceDefinitions`(匿名 `/cds-services` 枚舉**所有租戶** shared)、`invokeService`(匿名 `/cds-services/{id}` **未認證**呼叫任何租戶 shared)。**修 4 處**:(1) 列表 JPQL `OR c.shared=true` 綁進租戶 `tenantId=:t AND (owner=:u OR shared=true)`(**括號必要**,同 BUG-135:缺括號 shared 分支因優先序逃逸→半修);(2) `getService` 改嚴格 `findByIdAndTenantIdWithPrefetch`,移除跨租戶 shared 讀(收斂後讀寫 lookup 相同,他院服務無論 shared 皆 not-found);(3) 匿名發現 `getSharedServiceDefinitions` **收成回傳空清單**(無租戶脈絡→無可枚舉,標準回應 `{"services":[]}` 仍有效,端點保留不 404→不破壞 CDS Hooks 探索);(4) invoke gate 移除「shared→匿名可呼叫」旁路,**一律要認證**(shared 由同租戶 `config.tenantId==callerTenant` 呼叫、private 由 owner、legacy 寬容;未授權得同 not-found card 不可探測)。**外部整合改走既有已租戶隔離的 per-user 面** `/cds-services/u/{username}`(API-key 認證設 TenantContext,PAT-198),不失對外能力。移除 3 個死/跨租戶查詢方法。**前端** i18n 統一「院內共享」(shareService/unshare/shared badge)+ preset sharedLabel 明示院內,en/zh-TW 同步。**mutation 邊界不動**(BUG-137 toggleShared/rollback 嚴格租戶,收斂後仍正確)。**無 schema/migration/端點移除**。本機 fresh **全套 1722 案綠**(CdsHooksServiceTest 19)+ `tsc --noEmit` 乾淨。**過程誠實記錄**:5 個既有 invoke 下游測試改 gate 後轉紅(`Service not found` 取代預期 delegation)—— 因新 gate 要求認證而測試未設 SecurityContext,**正證明匿名呼叫已收掉**,補認證 caller 後全綠。**曝險已查證**:生產 `ehr_connection=0`、診所租戶僅測試帳號 → 收斂為預防性。**至此分享語意雙重定義殘留風險消除**。**TFDA 追溯**:#775(安全性等級 B)/設計 #776/驗證 #777/風險 #778。 | 統一分享語意;收回 Option A;無 migration | |
| BUG-138 | 🔒 fix | 2026-07-16 | IndicatorCatalog 平台 guard + EHR 連線租戶化(`IndicatorCatalogService` + `EhrConnectionService.testConnection` + `ConnectionHealthService.getHistory`) | **IndicatorCatalog 全域表寫入 + EHR 連線憑證跨租戶洩漏 — 5 例止血(掃描收尾)** — **IndicatorCatalog 3 例**:`indicator_catalog` 是平台級全域表(全國/TFDA 品質指標,無 tenant_id,所有診所共讀),GET 開放正確,但 PUT/DELETE/import 僅擋 `hasRole('ADMIN')` —— 自 #700 起 ADMIN 已非平台邊界(BUG-131/132 同根因),任一診所 ADMIN 可改/刪/匯入全平台共用的指標目錄。**EHR 2 例**:`POST /connections/{id}/test` 用 `repository.findById(id)`(非 tenant-scoped 的 getById),診所 ADMIN/DEPARTMENT_ADMIN 可**測試任何租戶的連線** —— (a) 變更其 status/lastTestedAt,且 (b) **回傳含憑證與 PEM 材料的完整 entity**(等同取得他院連接自家醫療系統的機密認證,可冒用身分);`GET /health/connections/{id}/history` **完全無 @PreAuthorize**,任何登入者枚舉 id 取他院連線健康歷史。**修**:(1) IndicatorCatalog 的 create/update/delete/bulkImport 首行 `platformOperatorGuard.require()`(service 層,比照 BUG-132 稽核清除;GET 不加 guard;掃描只點名 3 個但 create 同形狀一併加);(2) `testConnection` 改用既有 tenant-scoped `getById`(內部 `findByIdAndTenantId`,與 update/delete 一致),他院連線讀為 not-found → 憑證不外流、狀態不被 mutate;(3) `getHistory` 補 `connectionService.getById` 父連線 gate(`connection_health_check` 無 tenant_id,租戶即父連線的,與 test_case/BUG-133 同型;該端點無 @PreAuthorize 故此為唯一邊界)。本機 fresh **全套 1720 案綠**。**過程誠實記錄**:初次既有測試「全過」一度以為沒問題,但複查發現 `getHistory_shouldReturnRecentChecks` **未 stub 新加的 getById** —— Mockito 未 stub 的 mock 回 null 而非拋錯,`getById` 回 null 時 getHistory 不爆,故該測試對我新加的 gate **形同虛設、未實際驗證**。已補 stub 修正並另加 not-found 路徑 `never()` 斷言。此為「既有測試通過 ≠ 新邏輯被覆蓋」的實例。另 `IndicatorCatalogService` 先前**零覆蓋**(同 BUG-135),補全新測試類 6 案。**曝險已查證**:生產 `ehr_connection = 0`(目前無任何連線 → 憑證洩漏現階段無標的),診所租戶僅 65 次呼叫 → 修補為預防性。**至此掃描 49 例全數處理**:42 已修(BUG-132~138)+ 7 已降級(FhirController 共用 HAPI 沙盒)。FhirController 裸 fhirServer 網址改 tenant-scoped connectionId 屬 API 合約變更,另案。**TFDA 追溯**:#770(安全性等級 B)/設計 #771/驗證 #772/風險 #773。 | P0 存取控制;掃描收尾;測試形同虛設已修 | |
| BUG-137 | 🔒 fix | 2026-07-16 | CDS service 讀/寫分野租戶化(`CdsServiceConfigRepository` 拆讀寫兩查詢 + `CdsHooksService` + `CdsAnalyticsService` + `CdsServiceAnalyticsRepository` join) | **CDS service 查詢未租戶化 — 7 例止血,含診所 ADMIN 可發佈他院私有服務** — `cds_service_config` 已於 V64/V66 有 tenant_id、`getAllServices`/`getServicesForUser`/`verifyOwnership` 亦已於 #698 PR-C2 租戶化,但這 7 條被遺漏。**最嚴重**:`PATCH /{id}/share` —— `toggleShared` 無租戶條件且 controller 僅檢查 `isAdmin()`,**診所 ADMIN 可把他院私有 service 推上匿名發現面**(使他院未預期公開其臨床邏輯),反向 `shared=false` 則可讓他院線上服務停止被發現(可用性攻擊);`POST /{serviceName}/rollback/{version}` 可**改寫他院線上臨床邏輯**;`GET /{id}`、`/{serviceName}/versions` 任何登入者猜 id/名即取完整 config 含 `cqlContent`;`GET /services/analytics` **完全無 admin 檢查**,任何登入者見所有租戶呼叫量/錯誤率;`/{id}/analytics`、`/{id}/feedback` 可讀他院臨床人員的覆寫理由。**核心設計 — 讀/寫分野**(本案與前幾批最大不同):`shared` 按設計就是「發佈到全平台」(Option A #698),故**讀**可跨租戶(對 shared 服務)但**寫**絕不可。repository 拆兩個查詢:`findReadableByIdWithPrefetch`(`c.id=:id AND (c.tenantId=:t OR c.shared=true)`,刻意與既有列表 `findByTenantIdAndOwnerUsernameOrSharedTrue` 語意一致 —— 否則列表看得到、點進去 404)與 `findByIdAndTenantIdWithPrefetch`(嚴格,永不含 shared)。`rollbackService` 的邊界刻意放在**其自身查詢**而非 controller 的 owner 檢查 —— 後者用 `getService`(允許 shared),不能作為 mutation 的授權依據。`getServiceVersions` 租戶化(版本歷史含各版 `cqlContent` —— 發佈服務是讓人「呼叫」它,不是公開其編輯史)。**analytics/feedback 採父層 gate 不加欄位**(`service_id` 為 `NOT NULL REFERENCES cds_service_config(id) CASCADE`,與 test_case/BUG-133、cds_external_cql_library/BUG-134、measure_threshold/BUG-136 **四張表處理一致**);`getAllServiceAnalytics` 無父 id 可 gate 故用 join;per-service 兩條用**嚴格版** gate —— 發佈服務不等於公開其呼叫量與覆寫理由。**不採掃描 agent 建議**(它建議為 analytics/feedback 加 tenant_id + migration + 回填)。**採改名而非新增**:移除 `findByIdWithPrefetch` 後**編譯器抓出 5 個計畫外呼叫點**(update/delete/toggleEnabled 的 IfOwnedBy 三兄弟 + 兩個裸方法)——查證後確認**原本並非漏洞**(`verifyOwnership` 自 #698 PR-C2 起即租戶感知、admin bypass 已限縮自己診所,掃描未列入 49 例是正確的),但既為 mutation 一律改嚴格版作縱深。本機 fresh **全套 1712 案綠**(CdsHooksServiceTest 17 = 14+3)。**過程誠實記錄**:新 gate 讓 3 個既有測試以 `Service not found` 轉紅 —— 因未 stub 父 config,**正證明 gate 生效**,補 stub 後綠。**產品層不一致待決策(另案)**:`cds_service_config.shared` = 發佈全平台(Option A),而 BUG-135 剛把 `cql_library.accessLevel='public'` 收斂為租戶內 —— **同產品「分享」有兩種語意**,使用者可能誤以為 CDS 的 share 只給院內同事。**曝險已查證**:診所租戶僅 65 次呼叫 → 未有實際跨租戶存取或發佈狀態變更,修補為預防性。**餘 5 例另案**(IndicatorCatalog 3/EhrIntegration 2)。**TFDA 追溯**:#765(安全性等級 B)/設計 #766/驗證 #767/風險 #768。 | P0 存取控制;讀/寫分野;分享語意待決策 | |
| BUG-136 | 🔒 fix | 2026-07-16 | dashboard 租戶化(`DashboardService` + `MeasureReportRepository` + `MeasureThresholdRepository` + `MeasureController` thresholds gate) | **DashboardService 整個 service 無租戶概念 — 6 例止血(本批攻擊面最廣,完全無 gate)** — `MeasureController` 的 dashboard 家族(enhanced/trends/department/{code}/alerts/report + `{id}/thresholds`)**完全沒有 controller gate**(無 `@PreAuthorize`、無 ownership,僅 `/api/**` → `.authenticated()`),而底下 `DashboardService` 直接 `definitionRepository.findAll()`、`reportRepository.findTop10ByOrderByCreatedAtDesc()`、`thresholdRepository.findByActiveTrue()` —— **16 個查詢點全部無租戶條件**。故**任何登入使用者**(不需任何角色)即可取得所有租戶的 measure 清單、品質達成率、評估分數、門檻告警。**`measure_threshold` 從未有 tenant_id**(V27 建立,V64/V66 附屬表批次未涵蓋);其 `department` 為反正規化字串,而科別代碼自 V66 起僅**租戶內**唯一 → 兩家診所都用「內科」時 `findByDepartmentAndActiveTrue` 會撈到對方門檻(**被動混入,無需攻擊即發生,且顯示在告警畫面 → 臨床誤導風險**)。**修**:(1) `DashboardService` 加 `effectiveTenantId()`,16 處全帶 —— 因無 controller gate,此 helper 即為**完整邊界**,service 內不得殘留任何無租戶查詢;(2) report repo 新增 4 個租戶版,其中 `findLatestByMeasureDefinitionId` 的**子查詢 `MAX(r2.createdAt)` 內亦加租戶** —— 只加外層會讓「最新」被他院報告決定(表面已修實則半修);(3) `measure_threshold` 採 **join 父 measure 推導租戶不加欄位**(`measure_definition_id` NOT NULL FK CASCADE,與 test_case/BUG-133、cds_external_cql_library/BUG-134 **三張表處理一致**);`findByActiveTrue`/`findByDepartmentAndActiveTrue` 沒有父 id 可 gate(它們就是「撈全部」)故**必須** join,這是本案不能只靠 controller gate 的原因;(4) `GET /{id}/thresholds` 補 `requireMeasure(id)`(與 BUG-133 `listTestCases` 同型:有父 id 可 gate 時就 gate);(5) `findAllById` → `findByTenantIdAndIdIn` —— ids 源自已租戶化 reports 故今日安全,但改後不再依賴對呼叫端的推理。**其餘 dashboard 端點不加 gate**:租戶化後任何登入者只看得到自己租戶的儀表板,這正是預期行為。本機 fresh **全套 1709 案綠**(DashboardServiceTest 17 = 13+4)。**過程誠實記錄**:(a) `mvn test-compile` 回 EXIT=0 **是謊報** —— 未重編未變動的測試類,錯誤被烘進 class 檔(`Unresolved compilation problems`),簽章變更時必須 `rm -rf target/{classes,test-classes}`;(b) 一個新測試**真的紅了而且紅得對** —— `findByTenantId` 被呼叫 2 次(`getEnhancedDashboard` + `computeDepartmentScores`,即原 352 行的 `findAll()`),是預設 `times(1)` 過嚴,改 `atLeastOnce()`;此失敗反而**證實** computeDepartmentScores 確已租戶化。**曝險已查證**:診所租戶僅 65 次呼叫 → 修補為預防性。**餘 12 例另案**(CdsServiceConfig 7/IndicatorCatalog 3/EhrIntegration 2)。**TFDA 追溯**:#760(安全性等級 B)/設計 #761/驗證 #762/風險 #763。 | P0 存取控制;無 gate;科別代碼碰撞 | |
| BUG-135 | 🔒 fix | 2026-07-16 | measure/library 查詢租戶化(`MeasureDefinitionRepository`/`CqlLibraryRepository` + `MeasureDefinitionService`/`CqlLibraryService`) | **measure / CQL library 的 owner/shared/dependents 查詢從未加租戶條件 — 6 例止血** — 兩個 repository 是雙胞胎,問題完全相同:`findByOwnerUsername` 衍生查詢**無租戶條件**;`findSharedWithUser` 的 JPQL 為 `WHERE e.accessLevel='public' OR e.sharedWith LIKE :pattern` **無租戶條件**(`public` 分支單獨就把**所有租戶**的公開項目回傳給任何呼叫者);CQL 另有 `findByDependenciesContaining` 無租戶條件。**影響**:`/api/measures/owner|shared/{username}`(717-731)與 `/api/cql/libraries/owner|shared/{username}`(311-321)—— controller 僅 `isAdmin() OR username==self`,故 ROLE_ADMIN **可傳任意 username** 撈他院;`/api/measures/dashboard` 非 admin 路徑經 `getSharedMeasures` 洩漏他院 public measure;**`/api/cql/libraries/dependents/{name}`(339)連角色檢查都沒有** —— 任何登入者枚舉 library 名稱即取得完整模型含 `cqlContent`(本批攻擊面最大)。**語意錯誤**:分享(public/sharedWith)在多租戶下應為**租戶內**概念 —— 診所設 public 的原意是「給院內同事」,實際是「給平台上所有診所」。**修**:(1) repository 查詢租戶化 —— `findByTenantIdAndOwnerUsername`、`findByTenantIdAndDependenciesContaining`、`findSharedWithUser` 加 `WHERE e.tenantId = :tenantId AND (A OR B)`(**括號必要**:寫成 `tenantId AND A OR B` 會因運算子優先序讓 sharedWith 分支逃逸 → 表面已修實則半修);(2) **採改名而非新增** —— 無租戶版直接從 interface 移除,任何殘留呼叫點成為**編譯錯誤**,由編譯器而非人工審查保證無漏網(比測試更強的保證);(3) 5 個 service 呼叫點帶 `effectiveTenantId()`。**controller 不動**:租戶化後 ROLE_ADMIN 即使傳任意 username 也只拿到同租戶結果,admin 的「可查他人」語意自動侷限於自己租戶(與 BUG-134 不收斂 `OwnershipVerifier` 同理 —— 避免把跨租戶漏洞修成租戶內退化)。**根本教訓**:這 5 個 service 方法**先前測試覆蓋為零** —— 意即改名重構期間即使租戶條件完全沒加,全套測試也會是綠的,缺陷正是這樣上線的。本 PR 補 5 個回歸測試使租戶條件成為被鎖住的不變式。本機 fresh **全套 1705 案綠**(MeasureDefinitionServiceTest 30 = 28+2、CqlLibraryServiceTest 20 = 17+3)。**曝險已查證**:診所租戶僅 65 次呼叫 → 修補為預防性。**餘 18 例另案**(Dashboard 6/CdsServiceConfig 7/IndicatorCatalog 3/EhrIntegration 2);FhirController 7 例已降級(沙盒定位)。**TFDA 追溯**:#755(安全性等級 B)/設計 #756/驗證 #757/風險 #758。 | P0 存取控制;無租戶版方法已移除 | |
| BUG-134 | 🔒 fix | 2026-07-15 | artifact/preset 租戶隔離(V68 + `CdsArtifactEntity`/`SandboxPresetEntity` tenant_id + `ArtifactService`/`CqlGenerationService`/`SandboxPresetService` 查詢租戶化) | **診所 ADMIN 可跨租戶存取 CDS artifact / sandbox preset — 16 例止血(全面掃描最大一群)** — `OwnershipVerifier.verifyOwnership`(34)為 `!owner.equals(currentUser) && !isAdmin()`,`isAdmin()`(19-23)只檢查裸 `ROLE_ADMIN` 且**整個類別不碰 TenantContext**;#700 後每家診所第一位使用者即 ADMIN → 等同跨租戶通行證。**真正根因在資料層**:`cds_artifact`(V19)/`sandbox_preset`(V33)**從未有 tenant_id** —— V57~V63(measure/PHI)、V64/V66(附屬營運表)兩批都不涵蓋,故三個 service 全以 id 單獨查詢。**影響**:AuthoringController 13 例(get/summary/export-zip/cql/validate/elm/test/deploy-cds/save-library + external-cql 4)—— 其中 **`save-library` 可把他院 CQL 臨床邏輯複製進自己租戶**;SandboxPreset 3 例(`findAccessible` 的 `OR shared=true` 回傳所有租戶共享 preset、PUT/DELETE 可改刪他院)。id 連號可枚舉 → 可全平台掃描。**修**:(1) **V68** `cds_artifact`/`sandbox_preset` 加 tenant_id + 回填 `COALESCE(owner 的租戶, default)` + NOT NULL + FK + index(**與 V60→V61/V64→V66 不同,column 與 NOT NULL 同一支** —— 那兩批的 foundation 有「讀取尚未租戶化」的 inert 窗口,本 PR 讀取範圍化同批上線故無寫入 NULL 的窗口);(2) entity +tenantId(比照 `MeasureDefinitionEntity` 慣例);(3) repository `findByIdAndTenantId`/`findByOwnerUsernameAndTenantId`,`findAccessible` 加 `p.tenantId`(sharing 是租戶內概念),順手移除 2 個死方法;(4) 三個 service 全查詢帶 `effectiveTenantId()`,create/duplicate 寫入租戶(copy 沿用 original 的)。**關鍵設計判斷 — 不動 `OwnershipVerifier`**:掃描 agent 建議把 `isAdmin()` 收斂到 `PlatformOperatorGuard`,**該建議是錯的** —— 那會讓診所 ADMIN 連自己診所內其他使用者的 artifact 都管不了,把跨租戶漏洞修成租戶內退化。正確語意「ADMIN 可在自己租戶內繞過 ownership」在查詢租戶化後**自動成立**:`verifyArtifactOwnership` 先過 `artifactService.getById`,外部租戶即 404,`verifyOwnership` 根本碰不到外部資料(與 BUG-133 同思路:邊界放在租戶化查詢而非角色判斷)。`cds_external_cql_library` **刻意不加 tenant_id**(`artifact_id` NOT NULL FK CASCADE,租戶定義上即父 artifact 的)。本機 fresh **全套 1700 案綠**;過程誠實記錄:targeted 36 案先綠,**全套才抓出另 3 錯**(`PhiEncryptionIntegrationTest` preset fixture 缺 tenant_id 的 DataIntegrityViolation、`ArtifactServiceLibraryRefIntegrationTest` 2 案未設 TenantContext 而 fallback 查不到 default 租戶)—— targeted 綠 ≠ 全套綠。**曝險已查證**:生產 29 筆 artifact 全屬 default 租戶 legacy 帳號、preset 0 筆、診所租戶今日才建僅 65 次呼叫 → 未有實際跨租戶存取,修補為預防性。**餘 30 例 + FhirController 7 例另案**。**TFDA 追溯**:#750(安全性等級 B)/設計 #751/驗證 #752/風險 #753。 | P0 存取控制;不動 OwnershipVerifier 避免租戶內退化 | |
| BUG-133 | 🔒 fix | 2026-07-15 | test case 租戶邊界(`MeasureController.listTestCases` 補父層 gate) | **任何登入使用者可跨租戶讀取 test case 的真實病人 bundle — PHI 外洩止血(全面掃描 49 例中最嚴重)** — `GET /api/measures/{measureId}/test-cases`(`MeasureController.java:553-557`)**完全沒有任何 gate**(無 `@PreAuthorize`/`requireMeasure`/ownership,僅 SecurityConfig `/api/**` → `.authenticated()`);`TestCaseService.getTestCasesForMeasure`(45-46)直接 `findByMeasureDefinitionIdOrderByCreatedAtAsc(measureId)`,repo 衍生查詢(12)僅以 measureDefinitionId 過濾**無租戶條件**;`TestCaseEntity` 無 tenant 欄位;`test_case` 表在 V11/V13/V28 及全部 migration **從未加過 tenant_id**(V64/V66 附屬表硬化批次未涵蓋)。而 `TestCase.patientBundleJson`(35)裝的是**真實病人資料**:`PatientImportService.importAsTestCase`(48-112)以 `$everything` 從診所實際 EHR 拉完整 bundle 存入 `patient_import.bundle_json`(該表已正確隔離),指定 measure 時**複製一份**進 test case(105)——這份沒隔離。結果:**任何已登入使用者**(普通 USER 即可,**不需 ADMIN** —— 與 BUG-131/132 的關鍵差異,攻擊面大得多)枚舉連號 measureId 即可讀 B 診所的完整病人資料(識別碼/診斷/用藥/檢驗)。**同區塊其餘 8 個 test-case 端點皆有 gate**(`getTestCase` 用 `requireMeasure`;create/update/delete/run/batch-import/run-with-coverage 用 `requireOwnedMeasure`)—— 本項為**單純遺漏非設計缺陷**。**修**:`listTestCases` 補 `requireMeasure(measureId)`(單行,與同為讀取的 `getTestCase` 一致)——→ `definitionService.getById` → `findByIdAndTenantId(id, effectiveTenantId())`(V60/V61 已硬化),外部租戶解析為 empty → 404,且在 `testCaseService` 被呼叫**之前**中止,PHI 不會被取出。**不加 `test_case.tenant_id` migration**:`measure_definition_id` 為 `NOT NULL REFERENCES measure_definition(id) ON DELETE CASCADE`(V11:3)且父表已隔離 —— test case 的租戶**定義上就是**父 measure 的租戶,反正規化會引入漂移風險(兩處真相)卻無額外保證;此與 V64/V66 不同(那些是無租戶化父層的根層級表)。**用 `requireMeasure` 非 `requireOwnedMeasure`**:精準只關跨租戶、完全不動租戶內行為。**已查證無第二條 PHI 路徑**:`MeasureValidationService.validateTestCases`(495-497)雖用同一 service,但端點 `validateFull`/`validateQuick` 有 `requireReadableMeasure`(797/805)且報告只輸出計數不含 bundle。本機 fresh:`MeasureControllerTest` 5 案綠(3 既有 + 2 新;關鍵不變式:外部租戶時 `getTestCasesForMeasure` **從未被呼叫**)。**誠實記錄**:未實測「移除 gate → 測試轉紅」(該操作被開發環境安全防護擋下,判定為重新引入已修的洩漏),測試鑑別力基於程式碼路徑推論。**曝險範圍未查證**:尚未確認生產環境是否已有真實匯入資料落地/稽核是否顯示曾遭跨租戶存取(生產 DB 讀取需另行授權)——**查證前不得假設未造成實際外洩**。**餘 47 例另案**(`OwnershipVerifier` 裸 isAdmin 繞過 13+3、dashboard 家族整批無過濾、FhirController 共用 HAPI 無 partition 等)。**TFDA 追溯**:#745(安全性等級 B)/設計 #746/驗證 #747/風險 #748。 | P0 PHI;無需角色;租戶內收斂另案 | |
| BUG-132 | 🔒 fix | 2026-07-15 | 稽核清除租戶邊界(`AuditService.manualCleanup` + `PlatformOperatorGuard` + `AuditLogRepository` JavaDoc) | **診所租戶 ADMIN 可不可逆清除全平台稽核紀錄 — 跨租戶破壞性刪除止血(BUG-131 後續掃描抓到)** — 租戶存取控制全面掃描確認:`POST /api/admin/audit/cleanup` 僅由 `@PreAuthorize(hasRole('ADMIN'))` 守衛(`AuditController.java:161`),底層 `AuditLogRepository.deleteByCreatedAtBefore`(63-65)的 JPQL 為 `DELETE FROM AuditLogEntity a WHERE a.createdAt < :before` **無 tenantId 條件**,`AuditService.manualCleanup`(184-186)未帶租戶。該全域設計是刻意的(#698 起註記為 system retention policy),前提是「只有平台操作員能觸發」——#700 把每家診所第一位使用者建為 `role=ADMIN` 後此前提破裂:任何診所 ADMIN 可打 `?olderThanDays=0` **不可逆刪除全平台所有租戶的稽核紀錄**(含 PHI 存取軌跡 → 法規證據滅失 + 反鑑識)。與 BUG-131 同源:`hasRole('ADMIN')` 在多租戶下已非平台邊界。**修**:(1) `AuditService.manualCleanup` **首行**(cutoff 計算與 delete 之前)`platformOperatorGuard.require()`;(2) guard 置 **service 層**而非 controller —— 刪除邏輯在此、涵蓋未來任何呼叫者,與 `TenantService`/`ClinicApplicationService` 既有慣例一致(`AccessDeniedException` 屬 Spring Security 概念非 HTTP,不違反 service 層規範);(3) `@Scheduled cleanupOldLogs()` **不加 guard**(無 TenantContext 的排程執行緒,維持系統級全域保留政策);(4) repository JavaDoc 原註記「ADMIN-only manual cleanup」已不成立,改為明示跨租戶語意 + 請求執行緒可達的呼叫者須先過 guard。**不採租戶範圍化刪除**(`deleteByTenantIdAndCreatedAtBefore`):診所自清稽核軌跡本身違反稽核完整性原則且無此需求。本機 fresh:23 案綠(AuditServiceTest 16 —— 含 guard 被呼叫、**拒絕時 `deleteByCreatedAtBefore` 從未被呼叫**(關鍵不變式:guard 若被誤移到 delete 之後即失敗)、排程路徑不過 guard;+ PlatformOperatorGuardTest 7)。**同批掃描另發現 16 例**(`OwnershipVerifier` 裸 `isAdmin()` 繞過 11 例 —— `cds_artifact` 根本無 tenant_id 欄位;CDS service 查詢未租戶過濾 3 例等),均為讀取/複製類非不可逆破壞,需 migration,另案;**掃描尚未完成**(FhirController `$export`/`Bundle/$transaction`、MeasureController dashboard 家族、CqlController、SandboxPreset、IndicatorCatalog、EhrIntegration 未驗)。**TFDA 追溯**:#740(安全性等級 B)/設計 #741/驗證 #742/風險 #743。 | P0 存取控制;唯一破壞性項目先止血 | |
| BUG-131 | 🔒 fix | 2026-07-15 | 平台使用者管理存取控制(`AdminController` guard + `PlatformOperatorGuard.isPlatformOperator` + `AuthResponse.platformOperator` + 前端 authSlice/Header) | **診所租戶 ADMIN 可存取平台級使用者管理 API — 跨租戶洩漏止血(正式環境實測抓到)** — #700 給診所管理員 ADMIN 角色後,診所租戶 ADMIN(台安診所 alumi001,tenant_id=2)實測可 `GET /api/admin/users` 取得**全平台**使用者列表(audit log 實證 200)。`AdminController`(使用者 CRUD/角色/停用/重設密碼/解鎖/recent-invocations,7 端點)從未租戶範圍化也無 guard —— 不在 #698 批次(PHI/營運表),#699/#700 引入診所 ADMIN 後暴露。**修**:(1) `AdminController` **7 端點全首行** `platformOperatorGuard.require()`(class annotation 無法表達 tenant 條件;保留 `@PreAuthorize hasAnyRole` 為第一層);(2) `PlatformOperatorGuard` 抽 `isPlatformOperator(Long tenantId)`(null/default→true,診所→false),`require()` 改用之;(3) `AuthResponse` + `platformOperator` 旗標,login 以 `isPlatformOperator(user.tenantId)` 計算(login 路徑 TenantContext 未設,直接用 user.tenantId);(4) 前端 authSlice/localStorage/LoginForm/LoginPage 帶 platformOperator,`Header` nav 的 Users/租戶管理/診所申請三項改由 platformOperator 顯示(audit 因已 tenant-scoped 維持任何 ADMIN 可見)。**附帶釐清**:診所 ADMIN 打 tenants/clinic-applications 本就被 guard 擋 403(audit 實證),只是 nav 入口誤導 + Users 真漏 —— 本 PR 補齊。**診所自有使用者管理**(tenant-scoped)為另案,診所 ADMIN 暫無使用者管理面。本機 fresh:後端 49 案(**AdminControllerTest 12 維持綠**——@WithMockUser 下 TenantContext null → guard 對 null 放行、default 管理員不受影響;`PlatformOperatorGuardTest` 7 含 isPlatformOperator 三案)+ 前端 `Header.test` 5(診所 ADMIN 不見平台項/平台操作員全見)。**TFDA 追溯**:#735(安全性等級 B)/設計 #736/風險 #737/驗證 #738。 | P0 存取控制;診所使用者管理另案 | |
| PAT-204 | ✨ feat | 2026-07-15 | 診所申請前端(新 `ClinicApplyPage` + `ClinicApplicationsAdminPage` + `clinicApplicationApi` + landing CTA + route/nav + i18n) | **診所自助申請 + 審核 UI(#700 PR-2)** — **公開面**:`/apply`(公開路由,無需登入)申請表單(診所名/代碼 slug 即時驗證/管理員帳號/email),送出後顯示與後端一致的統一訊息(反列舉);landing hero 加第二顆 CTA「申請試用」→ /apply。**審核面**:`/admin/clinic-applications`(AdminRoute + nav 項)——狀態 filter(待審核/已核准/已婉拒/全部)、核准(**一次性 setup link 對話框 + 複製按鈕**,help 文案明示「只顯示這一次;未配置郵件時請透過可信管道轉交」)、婉拒(原因寫入審核軌跡)。i18n:`landing.json` apply 12 keys + hero.applyCta、`admin.json` applications 21 keys、`common.json` nav.clinicApplications(en/zh-TW 同步)。測試:`ClinicApplyPage.test`(2:有效申請送出+統一訊息、非法 slug 擋送出)+ `ClinicApplicationsAdminPage.test`(3:清單、核准秀 setup link、婉拒帶原因)全綠;`npx tsc --noEmit` 乾淨。**至此 #700 主流程(申請→審核→開通→設定密碼→登入進隔離租戶)端到端完成**;per-tenant rate limit 為後續 PR。**TFDA 追溯**:#700(B)/設計 #730/風險 #731/驗證 #732。 | UI;#700 主流程完成 | |
| PAT-203 | ✨ feat | 2026-07-15 | 診所申請開通後端(V67 + `ClinicApplicationService/Entity/Repo` + 公開/審核雙 controller + `PlatformOperatorGuard` 抽出 + login/refresh 租戶停用阻斷) | **診所自助申請 + 平台審核開通流程後端(#700 PR-1;Roadmap 開通閉環第二步)** — **公開面**:`POST /api/auth/clinic-applications`(permitAll + AUTH tier 10rpm/IP);**反列舉**:submit 不檢查 code/username 唯一性(匿名不得枚舉)、統一回應,衝突延後到審核時才對操作員揭露。**審核面**(`/api/admin/clinic-applications`,ADMIN + PlatformOperatorGuard 雙層):list/approve/reject。**approve = 一鍵開通**:username 檢查 → `TenantService.createTenant`(code 衝突在此擋)→ 建租戶 ADMIN(隨機拋棄式密碼 + `forcePasswordChange=true` + tenantId=新租戶)→ `PasswordResetService.generateSetupToken`(新方法,7 天效期,**對 H8 的窄範圍刻意偏離並 javadoc 記錄**:連結回傳給剛建立該帳號的操作員〔no-store〕,SMTP 缺席時線下轉交可開通;有 SMTP 則同連結 afterCommit best-effort 寄出,失敗僅 log.warn)。**登入/換發擋停用租戶**(補 #726 情境 3):login 於 authenticate 後查租戶 active(403);refresh 於 enabled 檢查後同款撤 family + InvalidRefreshTokenException;tenantId null(legacy/平台帳號)恆放行。**重構**:platform-operator 邊界抽出為 `PlatformOperatorGuard` 元件(TenantService 共用,行為不變)。**明確不做(設計 #730 記錄)**:SUPER_ADMIN 正式角色(過渡模型足夠,動每處 isAdmin 檢查風險過大)、email 驗證/自動開通(待 SMTP)、per-tenant rate limit(後續 PR)。本機 fresh **34 案全綠**:`ClinicApplicationServiceTest` 6(guard 全擋/開通快樂路徑含 ADMIN+tenantId+forcePasswordChange 斷言/username 衝突/已審核/reject)+ `PlatformOperatorGuardTest` 4 + `RefreshTokenServiceTest` 13(+1 停用租戶撤 family)+ `TenantServiceTest` 11(重構適配);修一個 `registerSynchronization` 無交易環境炸掉的問題(isSynchronizationActive 守門)。V67 交 CI verify-migrations。前端申請頁/審核 UI 為 PR-2。**TFDA 追溯**:#700(安全性等級 B)/設計 #730/風險 #731/驗證 #732。 | 後端;UI 下一條 | |
| PAT-202 | ✨ feat | 2026-07-15 | 租戶管理 UI(新 `TenantManagementPage` + `tenantApi` + route/nav + i18n) | **租戶管理前端(#699 PR-2/2)** — `/admin/tenants`(AdminRoute,ADMIN-only nav 項)提供:租戶列表(狀態 chip + 啟停 Switch,**default 租戶 Switch disabled + tooltip**)、建立對話框(code slug 即時驗證,與後端 `@Pattern` 一致)、成員對話框(成員表 + 指派下拉——排除已是成員者與**操作者自己**(後端禁自我指派)+ 指派會斷線的警語)。`tenantApi.ts` 5 個函數對應 PAT-201 API;React Query(查詢 + invalidate)。i18n:`admin.json` tenants 26 keys + `common.json` nav.tenants(en/zh-TW 同步)。**MUI 已是 v9**(CLAUDE.md 記載 5.14 過時):Switch 的 aria-label 需用 `slotProps.input`(inputProps 已移除)。測試:`TenantManagementPage.test.tsx` 4 案綠(列表渲染、default Switch disabled 而診所租戶不 disabled、slug 驗證擋非法 code、建立流程呼叫 API)——測試查詢用 i18n key(test-utils 無 I18nextProvider,t() 回傳 key,repo 慣例);`npx tsc --noEmit` 乾淨。**至此 #699 完成:租戶開通管理閉環(API + UI)全落地**。**TFDA 追溯**:#699(B)/設計 #725/風險 #726/驗證 #727。 | UI;#699 完成 | |
| PAT-201 | ✨ feat | 2026-07-15 | 租戶管理 API(新 `TenantAdminController` + `TenantService` 擴充 + 4 DTO + `UserRepository` +2) | **租戶(診所)管理 API + 使用者租戶指派(Roadmap「開通閉環」第一步;#699)** — `TenantService.createTenant` 自 V57 以來首次有呼叫端。**分權(過渡)**:不引入 SUPER_ADMIN(會破壞散落的手動 isAdmin authority 檢查),平台操作員 = **default 租戶的 ADMIN**——controller `@PreAuthorize("hasRole('ADMIN')")`(刻意排除 DEPARTMENT_ADMIN)+ service `requirePlatformOperator()`(TenantContext vs default id;null claim = legacy 平台帳號視同 default);診所租戶 ADMIN 全操作 403。**API `/api/admin/tenants`**:GET 全列表、POST 建立(code slug 驗證)、`PUT /{id}/active`(**default 租戶禁停用**)、`GET /{id}/users`(default 租戶合併 legacy null-tenant 使用者,與 effectiveTenantId 語意一致)、`PUT /{id}/users/{userId}` 指派——**同角色變更慣例立即斷 session**(`bumpVersion` + `revokeAllForUser`,JWT tenant claim 已烙進 token);禁自我指派、禁指派到停用租戶。正式 SUPER_ADMIN 角色與登入期 tenant-active 檢查明確留給 #700(自助註冊)的角色模型重整。本機 `TenantServiceTest` **12 案全綠**(guard 5 操作 403 + legacy 允許、default 禁停用、listUsers 合併語意、指派成功斷言 bump+revoke/停用租戶拒/自我拒)。前端管理 UI 為 PR-2。**TFDA 追溯**:#699(安全性等級 B)/設計 #725/風險 #726/驗證 #727。 | 後端;UI 下一條 | |
| PAT-200 | ✨ feat | 2026-07-15 | 附屬表租戶硬化(V66 + 9 entity nullable=false + `DepartmentEntity` 唯一鍵語意) | **附屬營運表硬化:9 表 tenant_id NOT NULL + department 唯一鍵 (tenant_id,code)(#698 批次收官)** — V64/V65 foundation + PAT-195..199 enforcement 全落地後的 schema 最後防線。**V66**:default 租戶冪等 INSERT → 9 表逐一防禦性回填(修補 enforcement 各 PR 部署間隙殘留)→ `SET NOT NULL`;**department 唯一鍵 swap**——V26 inline `UNIQUE(code)` 為 postgres auto-named,以 V61 的 DO block 欄位集動態查找 drop(含 PAT-190 的 `attname::text` cast 教訓)→ `UNIQUE(tenant_id, code)`,各診所可各自持有 V26 seed 的 INTERNAL/SURGERY 等 code;約束替換安全性:舊全域鍵保證 code 全域唯一 → 回填後 (tenant_id,code) 必唯一。9 個 entity `tenantId` 改 `nullable=false`(註解同步);`DepartmentEntity.code` 移除 `unique=true`(複合鍵僅存遷移,與 V61/V62 慣例一致)。測試資料補租戶:`PhiEncryptionIntegrationTest` patient_import、`CdsServicePersistenceIntegrationTest` 5 個 builder。**真 postgres:16 四項驗證全 PASS**:9 表回填 0 殘留 NULL、NOT NULL 強制(NULL 插入被拒)、跨租戶同 code 可存 + 同租戶重複被拒(舊全域鍵確認移除)、rollback 後可回插 NULL。本機 fresh 全量編譯 + 50 案綠(Department controller 6 + CDS 14 + subscription 10 + WS 8 + api-key 12)。**至此 #698 批次全部完成:V57–V66 十個 migration、PAT-178~200 二十三個工項,平台所有含使用者/病人關聯資料的表皆已 row-level 租戶隔離 + DB 層 NOT NULL 防線**。**TFDA 追溯**:#698(B;總綱)/設計 #716/風險 #717/驗證 #718。 | 批次收官;Phase 2 完成 | |
| PAT-199 | ✨ feat | 2026-07-15 | cds_service_config 租戶 enforcement(`CdsHooksService` + repo 3 scoped 查詢 + invoke 授權門) | **CDS 服務設定以租戶隔離 + 匿名面收斂為 shared-only(#698 批次 PR-C2;使用者定案 A 案)** — 承接 V64 + PAT-198(API-key 路徑 TenantContext 前置)。**A 案語意**:匿名 `GET /cds-services` discovery 由「全租戶所有 enabled」(跨租戶列舉洩漏)改為 **`findBySharedTrueAndEnabledTrue`**(原死方法轉正;shared=true 是診所明示發布的公開面,刻意 tenant-agnostic);**invoke 授權門**——`invokeService`(匿名+per-user+sandbox 共用)對非 shared 服務要求「已認證且(owner==caller 或 legacy 無 owner)」,未授權回與不存在**相同的 not-found card**(private id 不可探測),`CdsServiceConfig` 內部模型補 shared/ownerUsername/tenantId 三欄位使 cache 命中即可授權免 DB round-trip——**全域 serviceConfigs 快取因此可保留**(服務 NAME 命名空間維持全域版本化,shared 服務以名字為公開契約,cache 無跨租戶 key 碰撞;per-tenant 命名留待 shared 註冊機制重設計)。**管理面**:createService 指派租戶;`getAllServices`(admin)tenant-scoped、`getServicesForUser` 改「(同租戶 AND owner) OR shared(全域)」、per-user discovery 改 tenant+owner scoped;**verifyOwnership 的 admin bypass 加租戶邊界**(B 診所 admin 不能再管 A 診所服務;legacy null-tenant 維持舊行為)。本機 fresh 全量:`CdsHooksServiceTest` 14(12 既有 config 補 `.shared(true)` 對齊新契約 + **2 新授權門測試**:匿名打 private → not-found card 且 never invoke、owner 認證打自己 private → 正常委派)+ `CdsServiceVersioningTest` 6 + `CdsFeedbackTest` 6 全綠。**至此 #698 全部 enforcement 完成,剩最終 hardening migration**(9 表 NOT NULL + department UNIQUE(tenant_id,code))。**TFDA 追溯**:#698(B;總綱)/設計 #716/風險 #717/驗證 #718。 | PR-C2(A 案);hardening 收尾 | |
| PAT-198 | ✨ feat | 2026-07-15 | user 附屬表租戶 enforcement(`JwtAuthenticationFilter` API-key 分支 + `UserApiKeyService`·`NotificationService`·`NotificationWebSocketHandler`·`DepartmentService` + 3 repo) | **user_api_keys / notification / department 讀寫以租戶隔離 + API-key 認證補設 TenantContext(#698 批次 PR-D;#716 設計)** — 承接 V64。**關鍵修正(也是 CDS PR-C2 前置)**:`JwtAuthenticationFilter` API-key 分支(per-user CDS 端點)原本**從不設 TenantContext**,下游一律 fallback default 租戶形同無隔離;現認證成功後由 `UserRepository.findByUsername` 解析 key 擁有者的租戶(權威來源,不怕 key 帶舊租戶)寫入 TenantContext(return 在 try 內,finally-clear 涵蓋);`findByApiKeyAndActiveTrue` **刻意 unscoped**(它就是認證步驟本身、當下無租戶可用,key hash 全域唯一,repo 註解記錄)。**user_api_keys**:generate 設租戶、listKeys/revokeKey scoped。**notification**:createNotification 設租戶(工作流通知為租戶內事件,actor 租戶適用;四個呼叫點皆 request thread 不需 callWith)、清單/未讀數/markAllAsRead/markAsRead/delete 全 scoped(per-user 資料的 tenant 條件 = 防同名 username 跨租戶碰撞的縱深);`NotificationWebSocketHandler` 未讀數——WS 握手走 ticket 分支無 TenantContext,改由 username→UserEntity.tenantId(缺→default)顯式解析後走 scoped 查詢(手寫建構子補齊新依賴)。**department**:create 設租戶 + existsByTenantIdAndCode、getAll/getByCode/getChildren/update/delete 全 scoped;**全域 UNIQUE(code)→(tenant_id,code) 留給批次 hardening migration**(V26 seed 10 筆已回填 default)。測試:本機 fresh 全量編譯 + 35 案綠(`UserApiKeyServiceTest` 12〔scoped stub + TenantContext 7L〕、`NotificationWebSocketHandlerTest` 8〔建構子 + default 租戶 stub〕、`JwtAuthenticationFilterTest` 5〔建構子 5 參〕、回歸 subscription 10);Notification/Department controller 測試(@SpringBootTest)本機受 JaCoCo/Java25 限制交 CI。**環境備忘**:本機 mvn 增量編譯不會因相依變更重編未動測試檔(兩度假 BUILD SUCCESS),`clean` plugin 下載遭 AVG TLS 攔截(PKIX)——fresh 驗證用 `rm -rf target/classes target/test-classes`。**TFDA 追溯**:#698(B;總綱)/設計 #716/風險 #717/驗證 #718。 | PR-D;剩 CDS config + hardening | |
| PAT-197 | ✨ feat | 2026-07-15 | fhir_subscription 租戶 enforcement(`FhirSubscriptionService` + repo scoped 查詢) | **FHIR Subscription 讀寫以租戶隔離(#698 批次 PR-C1;#716 設計)** — 承接 V64 foundation。**寫入**:`createSubscription` 補 `entity.setTenantId(connection.getTenantId())`——**繼承 connection 租戶**(語意上 subscription 掛在 connection 之下;且 `connectionService.getById` 自 PAT-180 起 tenant-scoped,connection 必屬 caller 租戶,天然防止兩者租戶不一致)。**讀取**:`listSubscriptions` 兩分支改 `findByTenantIdAnd…`(**原 `findAllByOrderByCreatedAtDesc` 是跨租戶洩漏點**——任何登入者可列全租戶 subscription)、`getSubscription`/`deleteSubscription`/`syncStatus` 經 `findByIdAndTenantId`(跨租戶 → 404);刪除死方法 `findByStatusOrderByCreatedAtDesc` 與不再使用的 unscoped list 變體。**刻意 unscoped(repo javadoc 記錄)**:`findByFhirSubscriptionId`——匿名 webhook `POST /api/ehr/subscriptions/callback`(permitAll,外部 FHIR server 無法認證)以遠端 subscription id 反查,無 TenantContext 可用;租戶正確性由「寫入時已綁租戶」保證。service 注入 TenantRepository + canonical `effectiveTenantId()`。本機 `FhirSubscriptionServiceTest` 10 案全綠(+create 繼承租戶斷言、list/get scoped stub、webhook 路徑回歸不變)。**cds_service_config 另案(PR-C2)**:涉及匿名 discovery 語意與全域 serviceConfigs 快取無租戶維度的架構決策。**TFDA 追溯**:#698(B;總綱)/設計 #716/風險 #717/驗證 #718。 | PR-C1;CDS config 待架構定案 | |
| PAT-196 | ✨ feat | 2026-07-15 | audit_log 租戶 enforcement(`AuditFilter`·`AuditService`·`AuditLogRepository` 12 查詢·`AuditLogSpecification`) | **稽核紀錄(含 patient_fhir_id)讀寫以租戶隔離(#698 批次 PR-B;#716 設計)** — 承接 V64 foundation。**寫入**:`AuditFilter`(唯一寫入點,直接用 repository、不經 AuditService)builder 補 `.tenantId(effectiveTenantId())`——**盤點確認不需 callWith**:filter 同步跑在 request thread、巢狀在 `JwtAuthenticationFilter` 的 try/finally 之內(chain 順序 Tracing→RateLimit→XSS→JWT→UserRateLimit→Audit),save 當下 TenantContext 尚未被 finally 清除;唯一要處理的是 null tenant(匿名/失敗登入、SSE ticket 分支、per-user CDS API-key 分支、無 tenant claim 的 legacy JWT)→ default 回退(`orElse(null)` 不丟例外,audit 失敗不得擋業務,原 catch 記 ERROR 邏輯保留)。**讀取**:`AuditLogRepository` **12 個管理/儀表板查詢全部加 `a.tenantId = :tenantId`**(stats 6 count + 3 聚合、phi-access/login-activity/security-events 分頁、findByConnectionId/findByPatientFhirId/findEhrOperations);`countByCreatedAtAfter` 改 derived `countByTenantIdAndCreatedAtAfter`;`AuditLogSpecification.fromSearchRequest` 簽名加 `tenantId` 參數並**無條件** and 上 `tenantIdEquals`(search/export 皆生效,編譯器強制呼叫端帶租戶);`AuditService` 注入 TenantRepository + `effectiveTenantId()`,9 處呼叫帶租戶。**retention 刻意不動**:排程 `cleanupOldLogs` 與 ADMIN-only `manualCleanup` 維持全域刪除(系統一致政策,repo javadoc 記錄)。測試:**新增 `AuditFilterTest`(3,寫入路徑原本零覆蓋)**——TenantContext=42 → row=42 且不查 tenant 表、null → default 9、default 缺失 → null 不炸;`AuditServiceTest`(13)stub 全改 `eq(7L)` 精確斷言;`AuditLogSpecificationTest`(3)改雙參簽名。**TFDA 追溯**:#698(B;總綱)/設計 #716/風險 #717/驗證 #718。 | PR-B;剩 subscription/config + user 附屬 | |
| PAT-195 | ✨ feat | 2026-07-15 | 匯入網域租戶 enforcement(V65 + `PatientImportService`·`AsyncPatientImportService`·`ImportRetryService` + 3 repo scoped 查詢) | **病人匯入網域(patient_import/batch_import_job/failed_import)讀寫以租戶隔離 + async/排程租戶傳播(#698 批次 PR-A;#716 設計)** — 承接 V64 foundation。**盤點抓到兩個超出原範圍的問題**:(1) **正確性 bug**——`executeBatchImport` 為 `@Async("patientImportExecutor")`,TenantContext 不跨執行緒,async 內 `EhrConnectionService.getById`(PAT-180 起 tenant-scoped)會 fallback default 租戶 → **非 default 租戶的批次匯入會整批失敗**;修法 = submit 時(request thread)`job.setTenantId(effectiveTenantId())`,execute 以 unscoped findById 撈 job 後 `TenantContext.callWith(job.tenantId, …)` 包住全部邏輯(PAT-181/184/189 同模式),`ImportRetryService.processAutoRetries`(@Scheduled)同樣按 row 的租戶 callWith。(2) **V64 漏了 failed_import**——該表存**未加密** patient_fhir_id,洩漏面比 patient_import 大;V65 補 tenant 基礎(nullable + 回填 + FK + 索引 + rollback)。**寫入指派**:`importAsTestCase`(唯一 PatientImport 寫入點,單筆/批次/重試三路共用)、`submitBatchImport`、`recordFailure` 皆 `setTenantId(effectiveTenantId())`。**讀取範圍化**:匯入歷史 `listImports`、`getJob`/`listJobs`/`cancelJob`、失敗清單 `listFailedImports`/`getFailedImport`/`deleteFailedImport`/`retryImport`(手動重試跨租戶 → not-found)全改 tenant-scoped 查詢;`findDueForRetry` 刻意保持 unscoped(系統排程掃全租戶,逐 row callWith,註解記錄)。三 service 各注入 TenantRepository + `effectiveTenantId()`(canonical 模式)。本機 **32 案全綠**:`AsyncPatientImportServiceTest` 14(+1 新 async 租戶重入:清 TenantContext 模擬 executor,job.tenant=42 → import 時 TenantContext=42、結束還原 null;+scoped 斷言 7L/never unscoped)+ `ImportRetryServiceTest` 18(+1 新排程重入 42;+scoped 斷言)。V65 交 CI verify-migrations。**TFDA 追溯**:#698(B;總綱)/設計 #716/風險 #717/驗證 #718。 | PR-A;audit_log 等後續 | |
| PAT-194 | ✨ feat | 2026-07-14 | 附屬表租戶基礎(V64 + 8 entity `.tenantId`) | **附屬營運表多租戶資料基礎:8 表加 tenant_id + 回填(Roadmap Phase 2;#698 批次第一步)** — 沿用 V57/V59/V60 foundation 範本,為 `patient_import`/`batch_import_job`(PHI 關聯匯入)、`audit_log`(含 patient_fhir_id)、`fhir_subscription`/`cds_service_config`、`user_api_keys`/`notification`/`department` 各加 `tenant_id`(nullable)+ `INSERT…WHERE NOT EXISTS` default 租戶(冪等)+ `UPDATE` 回填既有列 + FK + 索引;8 個對應 entity 加 `tenantId`(JSON READ_ONLY)。**本 PR 純新增、inert、零行為變更**:讀取未租戶化、nullable 未收緊、寫入路徑未指派租戶。V64 交 CI verify-migrations(真 postgres V1–V64 全鏈)、entity↔schema 交 @SpringBootTest(與 PAT-185 同驗證策略);本機全模組編譯過。**後續 Enforcement PR(B,依 PHI 敏感度排序)**:patient_import/batch_import_job → audit_log(稽核儀表板跨租戶不可見)→ 其餘;最後 NOT NULL hardening。**TFDA 追溯**:#714(安全性等級 A — inert foundation;批次總綱 #698)。 | inert;enforcement 留後續 | |
| PAT-193 | ✨ feat | 2026-07-14 | 執行熱路徑 library 解析(`DatabaseLibrarySourceProvider` + `LibraryManagerFactory`) | **DatabaseLibrarySourceProvider null-tenant fallback 以 default 租戶範圍化(PAT-191 記錄的 follow-up;關閉執行熱路徑最後一個 unscoped 讀取)** — 問題:provider 由 static `LibraryManagerFactory.createContext()` 建構、無法注入 TenantRepository,TenantContext 為 null 時(legacy 呼叫者/無 tenant claim 的 JWT)走 unscoped 查詢,多租戶下可能解析到他診所同名 library。解法:利用 factory 本身是 `@Component` —— 建構子注入 `TenantRepository`,`@PostConstruct` 把 **memoized** default-tenant resolver(首次成功解析後快取,解析失敗 catch + WARN 不丟例外)存入 static volatile,`createContext` 傳給 provider 新增的雙參建構子(舊建構子 delegate null 保留給測試/非 Spring)。provider:TenantContext null → resolver 解析 default 租戶走 scoped 查詢;resolver 缺席或回 null 才降級 unscoped + WARN——與 services 的 `effectiveTenantId()` 語意一致。單租戶 pilot 所有 library 在 default 租戶 → 行為零變更。本機 17 案全綠:`DatabaseLibrarySourceProviderTest` 14(+4 新:null+resolver 有版本/無版本 scoped、resolver 回 null 降級、顯式 TenantContext 優先)+ `LibraryManagerFactoryTest` 3(+1 新:createContext 接線 + memoize——兩次解析只查一次 tenant 表)。**至此 #697 關閉,cql_library 端到端(管理+執行)隔離無 unscoped 殘留**。**TFDA 追溯**:#697(安全性等級 B)/設計 #710/風險 #711/驗證 #712。 | 熱路徑 fallback 收口 | |
| PAT-192 | ✨ feat | 2026-07-14 | measure_report 租戶硬化（V63 + `MeasureReportEntity.tenantId` nullable=false + `PhiEncryptionIntegrationTest` 測試資料補 tenant） | **measure_report 硬化:tenant_id NOT NULL（Roadmap Phase 2 schema 防線收尾;隨插即用診所 roadmap 第一步)** — 承接 V60 基礎 + #671 讀取隔離 + #681 排程租戶化。**前置插入審計**:grep 確認唯一 builder 寫入點 `MeasureReportService.saveReport`(#671 已設 `.tenantId(effectiveTenantId())`)、排程路徑經 #681 `callWith` 涵蓋 → 無遺漏、不需補寫入。**V63**:INSERT default 租戶(防禦性冪等) → `UPDATE null→default`(修補 V60 與 #671 部署間隙可能殘留列) → `SET NOT NULL`;**無唯一鍵 swap**(append-only 報告表、無 name/version 身分概念,比 V61/V62 單純)。`MeasureReportEntity.tenantId` 改 `nullable=false`。`PhiEncryptionIntegrationTest` 兩處插入補 tenant_id(builder `.tenantId(1L)` + legacy 模擬 raw JDBC INSERT 加欄——V63 後 legacy 列亦經回填持有租戶,測試資料與現實一致)。**真 postgres:16 四項驗證全 PASS**:tenant-less 列回填 0 殘留 NULL、既有指派列(tenant 2)不被改動、NOT NULL 強制(NULL 插入被拒)、rollback 後可回插 NULL(可逆性)。本機 Mockito 測試 35 案全綠(`MeasureReportServiceVersionTrackingTest` 6 + `ScheduledMeasureEvaluationServiceTest` 1 + `MeasureDefinitionServiceTest` 28)+ 全測試源編譯;`PhiEncryptionIntegrationTest`(@SpringBootTest)本機受 JaCoCo/Java25 環境限制,交 CI 為權威。**至此 measure_report schema 層防線補完,三大資源硬化全數完成**。**TFDA 追溯**:#696（安全性等級 B）/設計 #704/風險 #705/驗證 #706。 | 硬化;無唯一鍵變更 | |
| BUG-130 | 🔒 fix | 2026-07-14 | 相依 CVE(`pom.xml` 5 pin 6.9.7→6.9.9 + `.trivyignore` VEX + pom 註解) | **修補 org.hl7.fhir.core CVE-2026-49485(FHIRPath matches()/replaceMatches() ReDoS;修復版 6.9.9)— CI Security Scan blocker** — 新 CVE 命中 7 個 org.hl7.fhir.* 模組(全在 6.9.7),阻擋 #707 起所有 PR 的 Security Scan。沿用 BUG-125 準則「能獨立升的升、耦合的 VEX」:(1) 5 個獨立 pin(dstu2/dstu2016may/dstu3/dstu3.support/r4b)升 6.9.9;(2) r4/r5/validation 維持 HAPI 8.8.1 傳遞的 6.9.7 + `.trivyignore` VEX——**javap ABI 驗證(重演 BUG-124 方法)**:6.9.9 `IValidationPolicyAdvisor` 新增 abstract `isSuppressMessageId(String,String,Object...)`(varargs 取代兩參數版)與 `relativeDatePlaceHolder()`,HAPI 8.8.1 `FhirDefaultPolicyAdvisor` 皆未實作 → 硬升 runtime `AbstractMethodError`,**原本嘗試的 r4/r5/validation 6.9.9 override 據此撤回**。暴露評估(VEX):平台不運行 FHIR Validator HTTP endpoint;驗證路徑 FHIRPath regex 來自建置期打包信任 IG(TW Core)非使用者輸入;使用者 CQL 走 cqframework 引擎非 fhir-core FHIRPathEngine。Risk: LOW,HAPI 綁定 core ≥ 6.9.9 時移除。pom 註解改寫記錄可升/不可升邊界與 javap 驗證法。本機編譯過 + FHIR 相關單元測試綠;CI Security Scan/Backend Tests 為權威。**TFDA 追溯**:#708(安全性等級 A — 相依更新 + VEX,無行為變更)。 | 5 升 3 VEX;javap 驗證 | |
| PAT-191 | ✨ feat | 2026-07-09 | cql_library 租戶隔離硬化（V62 + `CqlLibraryEntity.tenantId` nullable=false + CDS 同步/相依分析讀寫租戶化） | **cql_library 硬化:NOT NULL + 唯一鍵 (tenant_id,name,version)（Roadmap Phase 2;#690 measure 後最後一項 Phase 2 硬化)** — 承接 V59 基礎。**前置全插入審計**:grep 確認 cql_library 僅 3 處 builder 寫入(`CqlLibraryService` x2 已設 tenant、`CdsHooksService.syncCqlLibrary` **本 PR 補上** `.tenantId(effectiveTenantId())` + 查詢改租戶範圍),無其他 new/save 遺漏。**讀取範圍化(皆 request thread)**:`DependencyAnalysisService` 根查詢 + 遞迴相依(含 fallback-to-latest)全改 `findByTenantIdAndNameAndVersion`/`findByTenantIdAndName`(tenantId 解析一次向下傳),避免硬化後跨租戶解析他診所 library;新增 `never()`-呼叫未範圍化變體的回歸測試。**V62**:INSERT default 租戶 → `UPDATE null→default`(修補 V59 後 CdsHooks 曾插入的 tenant-less 列) → `SET NOT NULL` → `DROP CONSTRAINT IF EXISTS uq_library_name_version`(V4 具名,直接 drop、**不需 V61 那種動態 DO block**) → add `UNIQUE(tenant_id,name,version)`。約束替換安全性:舊全域鍵保證 (name,version) 全域唯一 → 回填後 (tenant_id,name,version) 必唯一,新約束不可能在既有資料失敗。`CqlLibraryEntity.tenantId` 改 `nullable=false`(唯一鍵僅存遷移、與 #690 一致)。**真 postgres:16 驗證**(以真 V59→V62 檔 + 模擬 CdsHooks tenant-less 列):回填 0 筆殘留 NULL、NOT NULL 強制、跨租戶同名可存、同租戶重複被拒、舊鍵移除——五項全 PASS。本機單元測試全綠(`DependencyAnalysisServiceTest` 9/9〔+新隔離回歸〕、CdsHooks 三測 constructor 補 TenantRepository、`CqlLibraryServiceTest`/`DatabaseLibrarySourceProviderTest` 未受影響仍綠)。**執行熱路徑 follow-up**:`DatabaseLibrarySourceProvider` 的 null-tenant fallback(經 static `LibraryManagerFactory` 建構、拿不到 TenantRepository)之 effective-tenant 傳播另立小 PR、隔離熱路徑風險;pilot 單租戶下該 fallback 不會多列不 throw,延後不產生 active bug。**至此三大資源(ehr_connection/cql_library/measure)管理+寫入面隔離全硬化,Phase 2 租戶隔離實質完成**。**TFDA 追溯**:#691（安全性等級 B）/設計 #692/風險 #693/驗證 #694。 | 硬化;執行路徑 fallback follow-up | |
| PAT-190 | ✨ feat | 2026-07-09 | measure_definition 租戶硬化（V61 + `MeasureDefinitionEntity.tenantId` nullable=false） | **measure_definition 租戶硬化:NOT NULL + 唯一鍵 (tenant_id,name,version)（Roadmap Phase 2)** — #666 已把 measure_definition 所有讀取租戶化(**前置審計確認無殘留 unscoped `findByNameAndVersion`/`existsByNameAndVersion`**),故可安全硬化。V61:防禦性 `UPDATE null→default` → `tenant_id SET NOT NULL` → DO block 以**欄位集**({name,version})動態尋找舊 inline `UNIQUE`(對 postgres auto-name robust)並 drop → add `UNIQUE(tenant_id,name,version)`(支援多租戶同名 measure)。`MeasureDefinitionEntity.tenantId` 改 `nullable=false`。**smoke 真堆疊抓到並修正 V61 初版的 postgres 型別錯誤**(`operator does not exist: name[] = text[]`——DO block `array_agg(a.attname)` 需 `::text` 轉型;單元測試/編譯無法涵蓋 PL/pgSQL 型別)→ 修後 backend log "Successfully applied 61 migrations" + healthy。本機 `MeasureDefinitionServiceTest`(28)綠;CI verify-migrations(真 postgres V1–V61)為權威。**cql_library 硬化另案**(其有 4 處 unscoped findByNameAndVersion 需先修);ehr_connection 已在 V58 硬化。**TFDA 追溯**:#686（安全性等級 B）/設計 #687/風險 #688/驗證 #689。 | 硬化;cql_library 另案 | |
| PAT-189 | ✨ feat | 2026-07-09 | 排程 measure 評估在正確租戶下執行（`ScheduledMeasureEvaluationService` callWith） | **排程 measure 評估在 measure 的租戶下執行（Roadmap Phase 2;修 #666/#671 記錄的排程過渡）** — 排程評估於排程執行緒(`@Scheduled`,無 TenantContext)原本落 default 租戶,產生的報告(含 PHI)歸屬錯亂。修法:`runScheduledEvaluation` 先以 `measureDefinitionRepository.findById`(unscoped 系統查詢)解析 measure 的租戶,再把整段評估(scoped `getById` + 建 request + `evaluateMeasure` + 更新 schedule)包進 `TenantContext.callWith(measureTenant, …)` → getById 找得到、報告 saveReport 以 `effectiveTenantId` 正確歸屬、並行 fan-out 的 `callWith(callerTenantId)` 也捕捉到正確租戶。`triggerManually`(request thread + scoped getById)已租戶安全(跨租戶觸發 not-found),不需改。本機 `ScheduledMeasureEvaluationServiceTest`(新,1)驗證 evaluateMeasure 執行時 `TenantContext`=measure 租戶(42)、結束還原 null,全模組編譯。**至此 measure 管理面 + 排程隔離完成**。**後續**:MeasureSchedule 存取控制、`measure_definition` 唯一鍵 `(tenant_id,name,version)` + tenant_id NOT NULL 硬化。**TFDA 追溯**:#681（安全性等級 B）/設計 #682/風險 #683/驗證 #684。 | 排程租戶化;唯一鍵硬化後續 | |
| PAT-188 | ✨ feat | 2026-07-09 | ecqm_artifact + 發布路徑租戶隔離（`EcqmArtifactRepository` +2 + `EcqmArtifactService` + `EcqmPublishService`） | **ecqm_artifact + 發布路徑以租戶隔離（Roadmap Phase 2）** — 承接 #664/#666,把 eCQM authoring 工件與其發布(建 measure_definition)按租戶隔離。`EcqmArtifactRepository` 加 `findByIdAndTenantId`/`findByTenantIdAndOwnerUsername`;`EcqmArtifactService` 注入 `TenantRepository` + `effectiveTenantId()`,`listByOwner`→findByTenantIdAndOwnerUsername、getById/update/delete/duplicate→findByIdAndTenantId、create `setTenantId`、duplicate copy 繼承來源租戶;`EcqmPublishService.publish` 以 `findByIdAndTenantId` 載入 artifact 與既有 measure,`newMeasureDefinition` 繼承 artifact 租戶(不產生跨租戶 measure)。工件讀寫皆 request thread → 不需 async 傳播。本機 22 案全綠(`EcqmArtifactServiceTest` 17 + `EcqmPublishServiceTest` 5,mock 為確切租戶 7L)、全測試源編譯。**後續**:排程路徑租戶化、measure_definition 唯一鍵/NOT NULL 硬化。**TFDA 追溯**:#676（安全性等級 B）/設計 #677/風險 #678/驗證 #679。 | 工件+發布隔離;排程/硬化後續 | |
| PAT-187 | ✨ feat | 2026-07-09 | measure_report(含 PHI）管理讀取租戶隔離（`MeasureReportRepository` +10 tenant 查詢 + `MeasureReportService`） | **measure_report 管理讀取以租戶隔離（Roadmap Phase 2；含加密 PHI 的評估報告）** — 承接 #664/#666,把評估報告(`result_json` 為加密 PHI)的**管理讀取**真正按租戶隔離。repository 加 10 個 tenant-scoped 查詢(`findByIdAndTenantId`/`findTop50ByTenantIdOrderByCreatedAtDesc`/per-measureDefinitionId/per-measureName/per-period 各租戶變體,含 2 個 `@Query` overlap)。`MeasureReportService` 注入 `TenantRepository` + `effectiveTenantId()`;所有讀取(getRecentReports/getReportsForMeasure/getReportsByMeasureName(+Period)/getReportsForPeriod(+ById)/getReportsByMeasureIdOrderByPeriod/getReport)改租戶範圍——**dashboard 的 `findTop50` 由全域改租戶範圍(關掉跨租戶報告洩漏)**;`saveReport` builder 指派 `effectiveTenantId()`;`deleteReport` 先 `findByIdAndTenantId` 再刪。報告讀寫皆 request thread(寫在 fan-out 後)→ 不需 async 傳播。本機 48 案全綠(定義 28 + `MeasureReportServiceVersionTrackingTest` 6 含 `getRecentReports_scopesToCallerTenant` verify `findTop50ByTenantIdOrderByCreatedAtDesc(7L)` + never 全域 + normalizer/reader)、全測試源編譯。**排程過渡**:排程評估寫報告於排程執行緒落 default 租戶(pilot 正常,後續 PR)。**後續**:EcqmArtifact/EcqmPublish 隔離、排程租戶化、唯一鍵/NOT NULL 硬化。**TFDA 追溯**:#671（安全性等級 B;PHI 危害嚴重度 4）/設計 #672/風險 #673/驗證 #674。 | 報告(PHI）隔離;Ecqm/排程後續 | |
| PAT-186 | ✨ feat | 2026-07-09 | measure 定義管理租戶隔離（`MeasureDefinitionRepository` +8 tenant 查詢 + `MeasureDefinitionService`） | **measure_definition 管理操作以租戶隔離（Roadmap Phase 2；measure 隔離主體）** — 承接 #664 foundation,把 measure 定義的**管理操作**真正按租戶隔離。repository 加 8 個 tenant-scoped 查詢(`findByIdAndTenantId`/`findByTenantIdAndNameAndVersion`/`findByTenantId`/`findByTenantIdAndName`/`findByTenantIdAndDepartment`/`existsByTenantIdAndNameAndVersion`/`@Query searchByTenant`/`@Query findByTenantIdAndDepartmentAndSearchTerm`)。`MeasureDefinitionService` 注入 `TenantRepository` + `effectiveTenantId()`;所有讀取(getById/getByNameAndVersion/getAll/search 四分支/getHistory + update/sharing/locking/workflow 的 11 處 findById)改租戶範圍;create/createVersion `existsByTenantId…` guard;`modelToEntity` 指派 `effectiveTenantId()`、createVersion clone 繼承來源租戶。**收緊**:`MeasureController.evaluateMeasure` 經租戶範圍 getById → 原「任何使用者可評估任何 measure id」的跨租戶缺口關閉。**保留 `UNIQUE(name,version)` + nullable**(硬化另案)。measure 評估讀寫皆 request thread → 不需 async 傳播。本機 `MeasureDefinitionServiceTest`(28,含確切租戶斷言 `findByTenantId(7L)`)+ `MeasureValidationServiceTest`(9) 全綠、全測試源編譯。**排程過渡**:`ScheduledMeasureEvaluationService` getById 於排程執行緒落 default 租戶(pilot 正常,真實租戶排程需後續 PR)。**後續**:MeasureReport / EcqmArtifact 隔離、排程租戶化、唯一鍵/NOT NULL 硬化。**TFDA 追溯**:#666（安全性等級 B）/設計 #667/風險 #668/驗證 #669。 | 定義管理隔離;報告/排程後續 | |
| PAT-185 | ✨ feat | 2026-07-09 | measure 網域租戶基礎（V60 + `MeasureDefinitionEntity`·`EcqmArtifactEntity`·`MeasureReportEntity`.tenantId） | **measure 網域多租戶資料基礎（Roadmap Phase 2 foundation）** — 為 measure 三表(`measure_definition`/`ecqm_artifact`/`measure_report`)鋪租戶隔離資料基礎(承接 ehr_connection/cql_library 範本)。V60 為三表各加 `tenant_id`(nullable)+ `INSERT…WHERE NOT EXISTS` 確保 default 租戶 + `UPDATE` 回填既有列 + FK + 索引;**保留 `measure_definition` 既有 `UNIQUE(name,version)`**。三 entity 各加 `tenantId`(READ_ONLY)。**本 PR 純新增、inert、零讀取行為變更**:讀取尚未租戶化、constraint 未改、create 尚未指派租戶。**探勘結論(拆 PR 依據)**:measure 評估的讀寫皆在 request thread(measure 於 fan-out 前載入、report 於 fan-out 後寫入,async 區只跑 pre-translated CQL、不碰 measure 表)→ **measure 隔離不需 callWith async 傳播**(比 cql_library 輕);且無 measure 版 DatabaseLibrarySourceProvider。全模組編譯過;V60 交 CI verify-migrations、entity↔schema 交 @SpringBootTest。**後續 Enforcement PR(B)**:各 service 注入 TenantRepository + effectiveTenantId、create sites 指派租戶、讀取租戶化、唯一鍵 `(tenant_id,name,version)` + NOT NULL;**Scheduled path** `ScheduledMeasureEvaluationService` 包 callWith。**TFDA 追溯**:#664（安全性等級 A — inert foundation）。 | inert;強制留 enforcement PR | |
| PAT-184 | ✨ feat | 2026-07-08 | cql_library 執行路徑 library 解析租戶隔離 + async 傳播（`DatabaseLibrarySourceProvider` + `CqlTranslationService`/`CqlExecutionService` executor callWith） | **cql_library 執行/翻譯路徑 library 解析以租戶隔離 + async 傳播（Roadmap Phase 2；完成 cql_library 端到端隔離）** — 承接 #654(管理讀取),補上執行/翻譯時 `include` library 解析的租戶隔離。`DatabaseLibrarySourceProvider.getLibrarySource` 讀 `TenantContext`:有租戶→`findByTenantIdAndNameAndVersion`/`findByTenantIdAndName`;**null→維持 unscoped 原行為(legacy/單租戶零功能變更)**。翻譯/執行走 async executor(cql-translate/cql-exec-*),request-thread `TenantContext` 不傳播 → `CqlTranslationService`(`TRANSLATION_EXECUTOR.submit`)與 `CqlExecutionService`(`executorService.submit`)兩處以 `TenantContext.callWith(capturedTenant, …)` 包住 doTranslate/doExecute,把租戶傳播進 executor 執行緒。**刻意不改唯一鍵 `(name,version)`、不改 tenant_id NOT NULL**——那是「多租戶同名 library」的功能性硬化,pilot(default 單租戶)用不到,另案。本機 5 個測試類 **37 案全綠**(`DatabaseLibrarySourceProviderTest` 新增租戶/非租戶兩路 + 回歸);**smoke test 真 Docker 堆疊 29/30 通過**——含 `30-authoring-library-reference`(`include SharedLogic version '1.2.0'` 解析正確,exercise DB provider);唯一未過 `25-multi-population-group` 單獨重跑 PASS(flaky 計時、不用 include、與本變更無關)。**TFDA 追溯**:#659（安全性等級 B）/設計 #660/風險 #661/驗證 #662。 | 執行面隔離;唯一鍵硬化另案 | |
| PAT-183 | ✨ feat | 2026-07-08 | cql_library 管理讀取租戶隔離（`CqlLibraryRepository` +6 tenant 查詢 + `CqlLibraryService`） | **cql_library 管理讀取以租戶隔離（Roadmap Phase 2）** — 承接 #652 foundation,把 `cql_library` 的**管理讀取**真正按租戶隔離。repository 加 `findByTenantId`/`findByTenantIdAndNameAndVersion`/`findByTenantIdAndName`/`existsByTenantIdAndNameAndVersion`/`@Query searchByTenant`/`@Query findMetadataByTenantId`(既有 `findByNameAndVersion`/`findByName` 保留給執行路徑 DB provider)。`CqlLibraryService` 所有管理讀取(list/search/metadata/getById/getByNameAndVersion/latest/versions/createVersion/delete/update/sharing)+ **`saveLibrary` upsert lookup** 改走 `effectiveTenantId()`(TenantContext ?? default)的 tenant-scoped 變體——跨租戶讀不到、upsert 租戶範圍防跨租戶誤更新。**執行/翻譯路徑刻意不動**:`DatabaseLibrarySourceProvider` 續用 repo unscoped——它在 async executor(cql-translate/cql-exec-*)執行,需 `callWith` 傳播,留 PR-C。**已驗證**所有 scoped service 方法呼叫者(含 `FhirMeasureBundleImportService`/`Service`)皆 request-thread(controller),非 async measure 評估 → 無 async 洩漏。本機 `CqlLibraryServiceTest`(17,含確切租戶斷言 `findByTenantId(7L)` + 跨租戶/upsert)全綠、全模組編譯。**PR-C**:DB provider 租戶化 + 唯一鍵 `(tenant_id,name,version)` + translation/execution executor `callWith` + tenant_id NOT NULL。**TFDA 追溯**:#654（安全性等級 B）/設計 #655/風險 #656/驗證 #657。 | 管理讀取隔離;執行路徑 PR-C | |
| PAT-182 | ✨ feat | 2026-07-08 | cql_library 租戶基礎（V59 + `CqlLibraryEntity.tenantId` + `CqlLibraryService`） | **cql_library 多租戶資料基礎（Roadmap Phase 2 foundation）** — 為 `cql_library` 鋪租戶隔離的資料基礎(承接 #642/#647 的 ehr_connection 範本)。V59 加 `tenant_id`(nullable)+ `INSERT…WHERE NOT EXISTS` 確保 default 租戶 + `UPDATE` 回填既有 library + FK + 索引;**保留舊 `uq_library_name_version(name,version)`**。`CqlLibraryEntity.tenantId`(READ_ONLY);`CqlLibraryService` 加 `effectiveTenantId()`(TenantContext ?? default),`saveLibrary` 建立時指派租戶、`createVersion` 繼承 `latest` 租戶(版本鏈同租戶)。**本 PR 純新增、inert、零讀取行為變更**:讀取尚未租戶化、constraint 未改、執行路徑未動。**為何拆**:cql_library 讀取涉及執行/翻譯時 `DatabaseLibrarySourceProvider` 在 async executor 執行緒(cql-translate/cql-exec-*)按 name/version 撈取,強制隔離需同改唯一鍵為 `(tenant_id,name,version)` + 租戶化所有讀取 + 兩處 executor `callWith` 傳播——留給 enforcement PR。本機 `CqlLibraryServiceTest`(15,含 saveLibrary 指派租戶/fallback default)全綠、全模組編譯;V59 交 CI verify-migrations。**TFDA 追溯**:#652（安全性等級 A — inert foundation）。 | inert;強制留 enforcement PR | |
| PAT-181 | ✨ feat | 2026-07-08 | 執行路徑租戶隔離 + async 傳播（`TenantContext.callWith` + `MeasureEvaluationService` + `CqlExecutionService.resolveConnection`） | **CQL/eCQM 執行路徑對認證連線以租戶隔離 + measure 並行 async 的 tenant 傳播（Roadmap Phase 2 PR#4）** — 承接 #642（管理隔離),把真正的**執行路徑**也租戶範圍。`resolveConnection` 由 `getByIdUnscoped` 改回租戶範圍 `getById`（跨租戶 connectionId 讀不到）。關鍵:measure 評估為並行 async(`supplyAsync(…, cqlExecutionExecutor)`),request-thread `TenantContext` 不傳播到 executor 執行緒 → 新增 `TenantContext.callWith(tenantId, action)`(set→run→finally 還原前值,支援 nested),`MeasureEvaluationService` 在並行 stream 前 capture 租戶、於每個 async task 以 `callWith` 傳播 → sync + async 兩路都在正確租戶內解析。Phase 1 ADMIN/DEPT_ADMIN 守門保留為 defence-in-depth(鬆綁待租戶指派全面推行)。本機 `TenantContextTest`(3,含 callWith 傳播/nested 還原)+回歸 `CqlExecutionServiceConnectionTest`(5,resolveConnection 改 getById)+`EhrConnectionServiceTest`(5)+`MeasureEvaluationContextTest`(2) 全綠、全模組編譯。**後續**:ADMIN 守門以純租戶授權取代(租戶指派推行後);cql_library / measure 隔離。**TFDA 追溯**:#647（安全性等級 B）/設計 #648/風險 #649/驗證 #650。 | 執行面隔離;async 傳播 | |
| PAT-180 | ✨ feat | 2026-07-08 | 多租戶隔離（V58 + `EhrConnectionEntity`·`EhrConnectionRepository`·`EhrConnectionService`·`CqlExecutionService.resolveConnection`） | **EHR 連線管理操作以租戶隔離（Roadmap Phase 2 — 第一個實際強制隔離的資源）** — 承接 #638/#640,把 `ehr_connection` 的**管理**操作真正按租戶隔離。V58 加 `tenant_id` + `INSERT…WHERE NOT EXISTS` 建 default 租戶 + `UPDATE` 回填既有連線 + `SET NOT NULL` + FK + 索引(單一 Flyway tx);`EhrConnectionEntity.tenantId`(READ_ONLY,不接受 client 傳);repository 加 `findByTenantIdAndActiveTrue`/`findByIdAndTenantId`;service `effectiveTenantId()`(TenantContext ?? default 租戶)過濾 list/getById/update(跨租戶讀不到),create 伺服器端 `setTenantId`。**執行路徑刻意用新 `getByIdUnscoped`**——因 measure 評估為並行 async(`supplyAsync(…, cqlExecutionExecutor)`),request-thread `TenantContext` 不傳播,scoped 查詢會解析錯租戶/失敗;`resolveConnection` 改用之,並保留 Phase 1 ADMIN/DEPT_ADMIN 守門。既有連線回填 default → 現行單診所部署零中斷。本機 `EhrConnectionServiceTest`(5)+回歸 `CqlExecutionServiceConnectionTest`(5,mock 改 getByIdUnscoped)+`MeasureEvaluationContextTest`(2) 全綠、全模組編譯;V58 交 CI verify-migrations。**PR#4**:執行路徑租戶範圍 + measure async 的 tenant 傳播。**TFDA 追溯**:#642（安全性等級 B）/設計 #643/風險 #644/驗證 #645。 | 管理隔離;執行路徑 PR#4 | |
| PAT-179 | ✨ feat | 2026-07-08 | 多租戶 Security Context（新 `TenantContext` + `JwtTokenProvider`·`JwtAuthenticationFilter`·`RefreshTokenService`） | **把 tenant 帶進 Security Context（Roadmap Phase 2 enabler）** — 承接 #638 資料基礎,把「呼叫者屬哪租戶」帶進後端,作為所有 row-level 隔離的前置。JWT access token 攜帶 `tenant` claim(來自 `user.tenantId`);`JwtAuthenticationFilter` 於 JWT 路徑把 tenant 放進 per-request `TenantContext`(ThreadLocal)、請求結束 **finally 清除**(防 pooled thread 洩漏);`TenantContext.getCurrentTenantId()` 為「誰在呼叫」單一來源,無租戶(未指派舊使用者 / 非 JWT 路徑)回 null。**本 PR 純新增、inert、零行為變更**:tenant 只是可取用,不強制任何隔離、不改認證/授權決定;既有使用者 token 無 tenant claim 正常運作。本機 `JwtTokenProviderTest`(14,含 tenant round-trip)+ `TenantContextTest`(1)+ 回歸 `RefreshTokenServiceTest`(12,mock 更新)全綠、全模組編譯過。**後續 Phase 2 PR(安全性等級 B)**:以 `TenantContext` 強制各資源(先 `ehr_connection`)row-level 隔離 + 建立時伺服器端指派 + 既有資料回填。**TFDA 追溯**:#640（安全性等級 A — inert enabler,不強制隔離/不改認證授權決定）。 | inert;隔離強制留後續 | |
| PAT-178 | ✨ feat | 2026-07-08 | 多租戶基礎（`V57` migration + `TenantEntity`·`TenantRepository`·`TenantService`·`UserEntity.tenantId`） | **多租戶(多診所)資料基礎:Tenant 實體 + app_user.tenant_id（Roadmap Phase 2 foundation）** — 多診所共用 SaaS 的租戶隔離起點。租戶模型與使用者定調:**新增獨立 `Tenant`(診所)實體作為隔離邊界,`department`(V26)保留為租戶內子維度**(非把 department 當租戶)。V57 建 `tenant` 表(code 唯一/name/active)+ `app_user.tenant_id`(nullable FK + 索引)+ rollback;新增 `TenantEntity`/`TenantRepository`/`TenantService`(create 拒重複 code、list、getByCode、getById)。**本 PR 純新增、inert、零行為變更**:不強制任何隔離、未指派租戶的既有使用者 tenant_id 為 null。本機 `TenantServiceTest`(3) 綠、全模組編譯過;V57 由 CI verify-migrations（真 postgres）驗證、entity↔schema 由 @SpringBootTest 驗證。**後續 Phase 2 PR(安全性等級 B)**:各資源加 tenant_id + 回填、row-level 強制過濾、tenant 帶進 Security Context、建立時伺服器端指派、EHR 連線租戶強制、超管 vs 租戶管理員分權。**TFDA 追溯**:#638（安全性等級 A — inert scaffolding,不強制隔離/不改執行期）。 | inert;隔離強制留後續 | |
| PAT-177 | ✨ feat | 2026-07-08 | measure 評估路徑（`MeasureEvaluationRequest`·`MeasureEvaluationContext`·`MeasureEvaluationService`·`CqlExecutionService.doExecutePreTranslated`·`MeasureController`） | **measure/eCQM 評估支援以已認證 EhrConnection 對診所自有 FHIR 執行（Phase 1 PR#2）** — 延續 PAT-176（#628 只做 `/api/cql/execute`),把真正的 eCQM 評估路徑（`doExecutePreTranslated`）也接上認證連線。`MeasureEvaluationRequest` 加 `connectionId` → `MeasureEvaluationContext` delegating getter 透傳 → `executeForPatient` 建的 `CqlExecutionRequest` 帶入 → `doExecutePreTranslated` 沿用 #628 的 `resolveConnection`（fail-closed）+ `createDataProvider(url,tp,connection)` 走認證 RetrieveProvider;`$evaluate-measure` 與 `/evaluate` 兩端點對 connection 評估限 ADMIN/DEPT_ADMIN。**向後相容**:無 connectionId 評估行為完全不變。本機 `MeasureEvaluationContextTest`(2) + 回歸 #628 測試(8) 全綠、全模組編譯過。**Phase 1 後續仍 defer**:CDS prefetch、批次 auto-prefetch、terminology 認證;per-tenant 授權 Phase 2;SSRF 使用時重驗 Phase 3。**TFDA 追溯**:#633（安全性等級 B）/設計 #634/風險 #635/驗證 #636。 | 向後相容;沿用 #628 fail-closed 機制 | |
| PAT-176 | ✨ feat | 2026-07-08 | 執行路徑（`CqlExecutionRequest`·`FhirDataProviderService`·`CqlExecutionService`·`CqlController`） | **CQL 執行支援以已認證 EhrConnection 對診所自有 FHIR 執行（多診所 Roadmap Phase 1）** — 那套完整的 `EhrConnection`（加密憑證 / SMART Backend / mTLS）原本只接到病人匯入/搜尋,**CQL/eCQM 執行接不到**,只能打未認證原始 URL → 「診所連自己需認證的 FHIR 跑 eCQM」做不到。**本 PR（Phase 1 PR#1,`/api/cql/execute`）**:`CqlExecutionRequest` 加 `connectionId`;`FhirDataProviderService` 新增 `createDataProvider(url,tp,connection)` overload（有 connection → `createAuthenticatedClient`,否則 `createClient`,原 2-arg 委派、零行為變更）;`doExecute` 加 `resolveConnection`（**fail-closed**:給了 connectionId 卻找不到/停用即報錯,**絕不默默 fallback 到未認證預設伺服器**）+ 有 connection 走認證 RetrieveProvider;`EhrConnectionService` 以 `@Autowired(required=false)` field 注入（不動建構子、不破壞 4 個 integration test）;controller 對 connection 執行限 ADMIN/DEPT_ADMIN（過渡期,Phase 2 換 per-tenant）。**向後相容**:無 connectionId 完全等同變更前。本機 `CqlExecutionServiceConnectionTest`(5)+`FhirDataProviderServiceAuthTest`(3) 全綠、main 編譯過。**明確 defer**:measure 路徑/CDS prefetch/批次 auto-prefetch/terminology 認證（Phase 1 後續）、per-tenant 授權（Phase 2）、SSRF 使用時重驗/IP pinning（Phase 3）。**TFDA 追溯**:#628（安全性等級 B）/設計 #629/風險 #630/驗證 #631。 | 向後相容;fail-closed | |
| PAT-174 | 🔧 chore | 2026-07-07 | 版控衛生（`.gitignore` + 移除 `backend/data/*.db`） | **移除版控中的 H2 dev 資料庫二進位檔** — `backend/data/cqlplatform.mv.db` / `.trace.db` 被 git 追蹤且 `.gitignore` 未涵蓋（安全審查 NEW-3）。此為舊 H2 dev DB（現行 dev/prod 皆 PostgreSQL、H2 僅 test scope），會在每次 dev 執行時變動污染 diff，且若曾載入真實資料則可能含加密 email / admin 雜湊甚至 PHI（TFDA/HIPAA 資料落地疑慮）。**修補**：`git rm --cached`（停止追蹤、保留本機檔）+ `.gitignore` 加 `backend/data/`。**僅前向修補**；若確認歷史曾含真實資料，需另以 BFG/filter-repo 清歷史（force-push，待使用者決策，本 PR 不做）。**TFDA 追溯**：#624（安全性等級 A — 版控衛生，不影響執行期 / CQL 計算 / 臨床決策）。 | 保留本機檔，僅停止追蹤 | |
| PAT-175 | ⚡ perf | 2026-07-07 | 後端讀取路徑（`CqlLibraryRepository`·`CqlLibraryService`·`CqlController`·`LibraryMetadataDTO`） | **`/cql/libraries/metadata` 改用投影載入，跳過未使用的 `cql_content`** — 該端點原透過 `getAllLibraries()`（`findAll()`）載入完整實體（含 `cql_content`、`elm_json` 等 TEXT）再映射，卻只用到 elmJson（解析出 expressions/valueSets/codes/functions），白白載入 `cql_content`。**修補**：新增只選 (name, version, elmJson) 的 repository 投影查詢（`LibraryMetadataView` + `findAllMetadata`）；`LibraryMetadataDTO` 抽 `fromElm(name, version, elmJson)`、`fromLibrary` 委派之；service `getLibrariesMetadata()` 用投影；controller 改呼叫之。**回傳內容完全不變**（結果保留式優化）。本機 `LibraryMetadataDTOTest`(4) + `CqlLibraryServiceTest`(含新測，驗證用投影、不呼叫 findAll) 全綠。**誠實標記 defer**：主 `/cql/libraries` 列表瘦身**不安全**（`LibraryPicker.onSelect(library.cqlContent)` 前端實際讀列表項的 cqlContent）、分頁需前端改寫——列後續。**TFDA 追溯**：#626（安全性等級 A — 結果不變的讀取優化，不改 CQL 計算/臨床決策/患者資料）。 | 結果不變；主列表瘦身因前端耦合 defer | |
| PAT-173 | 🔧 chore | 2026-07-07 | 維運/設定（`docker-compose.yml` hapi·backend env + `application.yml` 池屬性；無 Java 變更） | **2 核 VM 部署硬化：HAPI 釘 digest + CQL 執行池 env 可調** — (1) hapi-fhir 由浮動 `:latest` 釘為明確 digest（`@sha256:34c86f…`，正式機當下運行映像）——避免 `docker compose pull` 靜默升級臨床 FHIR 資料儲存到帶破壞性 schema migration 的新版而難以回滾；附升級/讀 tag 註記。(2) hapi healthcheck 維持停用並**註記原因**（映像為 distroless、無 shell/curl/wget，`docker exec sh` 實測失敗，無法表達容器 healthcheck；backend 以 FHIR retry + circuit breaker 容忍）——防止未來誤「恢復」而卡住啟動。(3) `cql.execution.thread-pool-size/max-pool-size/queue-capacity` 改 `${CQL_EXECUTION_*:預設}` + compose backend passthrough，**預設不變（10/20/50）**、可經 `docker/.env` 右調而不需重建映像（`AsyncConfig` 既以 `@Value` 讀取，不改 Java）。刻意不改預設值/資源上限/無上限翻譯池（系統健康、避免臆測；後者 rate-limit 已緩解，bound 需負載測試）。`docker compose config` EXIT 0、hapi 渲染為 digest、application.yml 解析通過。**TFDA 追溯**：#619（安全性等級 B）/設計 #620/風險 #621/驗證 #622。 | 純設定、零預設行為變更 | |
| PAT-172 | ⚡ perf | 2026-07-07 | 前端執行路徑（`types`·`editorSlice`·`useCql`·`ExecutionPanel` + 新 `utils/executionElm.ts`） | **CQL 執行重用已翻譯 ELM，免每次重複 cql2elm 編譯** — `/api/cql/execute` 每次都重跑 CPU 密集的 cql2elm（複雜 artifact 數百 ms～1s 主成本），即使前端剛翻譯過、Redux 已有 ELM 也沒帶上。後端 `CqlExecutionService` 早支援「有 elmJson 就跳過翻譯、反序列化失敗則 fallback」，缺的是前端**安全地**帶入。**修補（正確性優先）**：`CqlExecutionRequest` 加 `elmJson?`；editorSlice 記錄 `elmSourceCql`（ELM 從哪段 CQL 翻來）；純函數 `freshPrecompiledElm(cql, elmJson, elmSourceCql)` **僅在來源與即將執行文字字串完全相等時**才送 ELM（含 `getLatestCql()` 的 Monaco 即時內容）——任何編輯即不送、後端重新翻譯，**杜絕執行與畫面不符的舊 ELM → 錯誤臨床結果**。以字串比對而非旗標，不受 Monaco/Redux 時序影響；三重防護（前端守門→不送→後端 fallback）。本機 `tsc` 0 error、守門/slice 測試 17 綠。**TFDA 追溯**：#614（安全性等級 B）/設計 #615/風險 #616/驗證 #617。 | 純前端 + 既有後端路徑 | |
| BUG-129 | 🐛 fix | 2026-07-07 | 後端安全（`security/` RateLimitFilter·AuditFilter·ClientIpResolver） | **速率限制改以真實 client IP 分桶 + reset-password 納 AUTH 層** — `RateLimitFilter` 以 `getRemoteAddr()` 分桶，在 Cloudflare→nginx 代理鏈後所有外部 client 共用**同一個桶** → 單一攻擊者每分鐘打數次 `/api/auth/login` 即可耗盡全站 AUTH 桶、**鎖住所有臨床人員登入**（可用性 DoS），per-IP 暴力破解節流亦失效；且 `/api/auth/reset-password` 落在 6 倍寬鬆的 DEFAULT 層。**修補**：抽出共用 `ClientIpResolver`（僅信任私網對端的 `X-Forwarded-For`、取最右側非私網 IP、非 IP 字面值不做 DNS 避免偽造與 SSRF 形狀副作用），`RateLimitFilter` 與 `AuditFilter` 共用之（單一真相來源）；`resolveTier` 將 reset-password 納入緊縮 AUTH 層（refresh/logout 刻意留 DEFAULT）。新增 `ClientIpResolverTest`(6) + `RateLimitFilterTest` 分桶/reset-password 測試，本機 24 測試全綠、既有未回歸。**TFDA 追溯**：#609（安全性等級 B）/設計 #610/風險 #611/驗證 #612。源自安全審查 NEW-2/NEW-4。 | 新增 ClientIpResolver | |
| BUG-128 | 🐛 fix | 2026-07-07 | 維運（`docker/` compose·prometheus·scripts·systemd） | **資料保護：修復 WAL 歸檔靜默失敗 + 每日自動備份 + 磁碟/備份告警** — archive_command 結尾的 `|| true` 搭配從未建立、且 root 擁有（postgres 無法寫入）的 `/backups/wal`，使 `cp` 每次失敗被吞掉、PostgreSQL 卻誤以為歸檔成功並回收 WAL → **44 個 WAL 段靜默丟失、`pg_stat_archiver.failed_count` 恆為 0**（已於正式 VM 實測確認）；且 VM 原本零備份檔、零備份排程。**修補**：(1) archive_command 改 `mkdir -p /backups/wal && ( test -f … || cp … )` — 自我修復目錄、冪等、**移除 `|| true`** 使失敗真實反映在 log 與 `failed_count`；(2) `docker/systemd/cql-db-backup.{service,timer}` 每日 02:00 自動 pg_dump + 30 天保留（`Persistent=true` 補跑漏排程）；(3) 新增 `node-exporter` + 告警 `DiskSpaceLow`(<15%)·`DiskSpaceCritical`(<5%)·`BackupStale`(>26h)·`BackupMetricMissing`·`NodeExporterDown`，封住「磁碟無聲耗盡→postgres 毀損」風險鏈；(4) `backup-db.sh` 輸出新鮮度指標供告警。一次性 `chown postgres /backups` 已於 VM 執行、WAL 歸檔實測恢復（16MB WAL 落地）。**TFDA 追溯**：#604（安全性等級 B）／設計 #605／風險 #606／驗證 #607。 | docker/ 維運層，不動應用/CQL 邏輯 | |
| BUG-127 | 🐛 fix | 2026-07-02 | 後端（`backend/Dockerfile` ENTRYPOINT：JVM GC SerialGC→G1） | **後端 JVM 改用 G1GC — 根治 SerialGC 全站凍結延遲（`HighLatency` 家族根因）** — backend JVM 未指定 GC 且 `-Xmx1024m` 低於 JVM「server 級機器」1792M 門檻 → ergonomics 自動選 **SerialGC**（單執行緒、全堆 stop-the-world）。**佐證（30 天 Prometheus 歷史）**：major GC `MarkSweepCompact` 單次暫停峰值 **209s**、minor `Copy` 24.6s；GC 一觸發即凍結所有 request 執行緒 → 同一瞬間多條不相干端點齊爆延遲（`/api/cql/translate` 205s、`/cds-services/{id}` 174s、`/api/cql/execute` 113s、`/api/auth/login` 66s、`/api/auth/refresh` 59s、libraries·user-prefs·register·`/actuator/prometheus` 20–27s）；CPU 30 天峰值 `system_cpu_usage` **0.71**、`process_cpu_usage` 0.71（2 核從未打滿、backend≈全系統負載）→ **GC-pause-bound 非 CPU-bound**，推翻「其他容器爭 CPU」假說。**修補**：ENTRYPOINT 加 `-XX:+UseG1GC -XX:MaxGCPauseMillis=200`（並發/並行、200ms 暫停目標、無全堆 STW 大凍結）。**堆積不動**（`-Xmx1024m -Xms512m`）— 30 天峰值堆*用*僅 553M/54%，問題在收集器非大小；G1 動態 region 取消 SerialGC 固定 ~680M 老年代上限，反而**降低** `HighHeapUsage`（per-pool used/max>0.85）誤報風險。**驗證**：本機 sandbox TLS 攔截無法解析相依，以 CI Linux + 部署後 Prometheus `jvm_gc_pause_seconds_*` GC 名稱轉為 `G1 Young/Old Generation`、無 >5s 暫停為權威。**TFDA 追溯**：#597（安全性等級 A — 純 JVM 執行期調校，不改 CQL 計算/臨床決策/患者資料）。 | backend/Dockerfile | |
| PAT-171 | ♻️ chore | 2026-07-01 | 前端框架升級（`frontend/package.json` react/react-dom/@types 18→19、`@mui/material`+`@mui/icons-material` 7→9、`@emotion` 11.14；~206 檔 codemod + 13 項手動修 + LibraryPicker 測試改寫） | **前端升級 React 19 + MUI 9（取代 dependabot #553/#557）** — #557（react-dom 18→19）與 #553（mui 7→9）單獨升皆壞:#557 react-dom 19 配 react 18 peer 衝突、#553 造成 **802 個 type error**。改為一次到位的 React 19 + MUI 9 遷移。**做法**:(1) 相依:react/react-dom/@types 18→19、@mui 7→9、@emotion 自動 11.14（全樹 dedupe React 19、無 peer blocker）。(2) **MUI codemods**:`v9.0.0/system-props`（系統屬性→`sx`,~206 檔）、`deprecations/all`（TextField/ListItemText/Dialog `*Props`→`slotProps`）、`*Outline` icon→`*Outlined` → 802→13。(3) **手動 13 項**:React 19 `useRef()` 需初始參數、移除全域 `JSX.Element` 標註、Dialog 移除已移除的 `disableEscapeKeyDown`（onClose guard 已擋關閉）、Typography 字型屬性→`sx`、`theme.ts` Alert `standard{Severity}` class→`standard` slot + `colorX` 選擇器 → **tsc 0 error**。(4) LibraryPicker 競態測試改寫:React 19 render timing 揭露 `disabled={loading}` 已擋並發第二次點擊（兩個 in-flight 請求模擬不可達）,測試改驗實際行為（載入中清單禁用→只發一次 getLibrary→onSelect 一次）;`requestTokenRef` 守衛保留為 defense-in-depth。**驗證**:`tsc --noEmit` 0（原 802）、`lint` clean、`vite build` 成功、測試 **661/662→全綠**（LibraryPicker 修好後 3/3）。**本機限制**:`@mui/icons-material` ~9k 個 `.mjs` 在 Windows 觸發 EMFILE FD 上限,23 個測試檔本機跑不了（非程式問題,CI Linux 不受影響,為權威驗證）。**未納入**:視覺回歸需人工 QA（200+ 檔觸及整個 UI 層）。**TFDA 追溯**:#595（安全性等級 A — 前端框架升版,不改 CQL 計算/臨床決策/患者資料;建議 merge 前人工視覺 QA）。 | frontend/package.json, frontend/package-lock.json, frontend/src/**（~207 檔） | |
| BUG-126 | 🐛 fix | 2026-06-29 | 監控（`docker/prometheus-alerts.yml` HighLatency 拆三層門檻 + 修 PromQL per-uri）+ 後端（`CqlTranslationService.translate` 加 `@Cacheable` + `CqlConfig` 新 `cqlTranslation` cache + `FhirTerminologyService.getCacheStats` 納入）+ 測試（`CqlTranslationServiceTest` +1） | **修 `HighLatency` 對 AI/CPU 密集 endpoint 的誤告警 + 替 `/translate` 加快取** — `HighLatency`（P95>5s）對每條 endpoint 套單一 5 秒門檻,對本質就慢的端點誤報:`/api/cql/fix-suggestion` 是 AI/LLM 呼叫(`CloudAiService` RestTemplate 打外部 API,readTimeout 60s)、`/api/cql/translate|validate` 是 CPU 密集 cql2elm、`/api/cql/execute` 跑 CQL(timeout 120s)。**修補(同 BUG-122「過敏告警」家族)**:(1) **告警分三層**:一般 endpoint 5s;CQL 編譯（translate/validate）15s;long-running（fix-suggestion/execute）30s;PromQL 同時修為 `histogram_quantile(0.95, sum by (le, uri) (rate(...bucket[5m])))` 正確的 per-uri 分位數（原本缺 `sum by (le,uri)`）。(2) **`/translate` 加 `@Cacheable("cqlTranslation")`**,key=正規化 CQL + 四個 compiler options（避免不同 options 撞 key）;新增有界 cache `cqlTranslation`（300 筆/30 分,比照 `cqlValidation`）;cql2elm 對相同輸入為決定性,安全（staleness 與既有 `validate` 快取同級、受 TTL 界限）;砍掉編輯器重渲染/undo-redo/多 client 的重複翻譯延遲。`getCacheStats` 納入新 cache。**驗證（本機實跑）**:`CqlTranslationServiceTest` 2/2 綠（含新 `testTranslateIsCached` 以 `assertSame` 證明 cache hit 回同一實例）;`prometheus-alerts.yml` 經 `yaml.safe_load` 驗證（16 條 alert、三層門檻齊備、PromQL 含 `sum by (le,uri)`）。本機建置改用 `-Djavax.net.ssl.trustStoreType=Windows-ROOT` 繞過 AVG TLS 攔截解析相依。**未納入**:`/execute` 本就不在原告警內,僅順帶歸入 long-running 層;promtool 的 PromQL 驗證待 Prometheus reload（本機無 promtool 映像）。**部署**:後端快取隨 backend 映像;`prometheus-alerts.yml` 為掛載檔,需 VM `git pull` + recreate/reload prometheus 才生效。**TFDA 追溯**:#593（安全性等級 A — 監控門檻調校 + 決定性效能快取,不改 CQL 計算結果/臨床決策/患者資料）。 | docker/prometheus-alerts.yml, backend/src/main/java/com/cqlplatform/service/cql/CqlTranslationService.java, backend/src/main/java/com/cqlplatform/config/CqlConfig.java, backend/src/main/java/com/cqlplatform/service/fhir/FhirTerminologyService.java, backend/src/test/java/com/cqlplatform/service/cql/CqlTranslationServiceTest.java | |
| BUG-124 | 🐛 fix | 2026-06-29 | 後端（`FhirTerminologyService.lookupCode` catch 例外分類 + `CodeLookupResult` 加 `found` 旗標/factory + `FhirController` `found=false`→404）+ 測試（`FhirTerminologyServiceTest` +4、`FhirControllerTest` +2） | **`$lookup` 查無代碼改回 404 + 負快取 — 根治 `/api/fhir/CodeSystem/$lookup` 5xx 連環噴與 `HighErrorRate` 誤告警** — production 收到 critical `HighErrorRate`（>5% 5xx on `/api/fhir/CodeSystem/$lookup`），使用者反映「網站常傳錯誤訊息」。**根因**：`FhirTerminologyService.lookupCode` 以無差別 `catch (Exception e)` 把所有遠端例外（含 tx.fhir.org 對未知代碼回的 **4xx**）一律包成 `FhirServerUnavailableException`→**503**。前端 `useTerminologyValidation` 每次翻譯後對 library 內**每個 code 自動 `$lookup`**，故任何公開術語伺服器未收錄的代碼（TWCORE 本地碼／拼錯／草稿碼）都產生一個 5xx → 觸發 `HighErrorRate` 並跳「FHIR Server Unavailable」降級橫幅；`@Cacheable` 不快取例外 → 同一壞碼每次重打遠端重複 503。附帶：resilience4j `ignoreExceptions`（`ResourceNotFoundException`/`InvalidRequestException`）因例外在方法內被吞換成 `FhirServerUnavailableException`、切面從未見到而失效。**修補（A 例外分類）**：catch 拆兩段 — `BaseServerResponseException` 且新 helper `isClientError`（status∈[400,500)）為 true → **回傳** `CodeLookupResult.notFound()`（查無，非故障）；5xx／status 0（連線/逾時）→ `FhirServerUnavailableException`(503，沿用 `Reason.classify`)。controller 對 `found=false` 丟 `ResourceNotFoundException`→**404**。**（B 負快取）**：`CodeLookupResult` 加顯式 `boolean found` + `found()/notFound()` factory；service 改回傳而非丟例外 → `@Cacheable("codeLookup")`（max 1000、TTL 2h 有界自癒）快取負結果，同一未知碼不再反覆打遠端。**安全**：僅快取明確 4xx；5xx/連線/逾時不快取（避免暫時性故障鎖成查無）；`found` 顯式布林避免「null display 誤判有效」；維持 PAT-110 降級橫幅僅給真正上游故障。**未在本 PR**：`validateCode`/`expandValueSet` 同款模式（後續另案）；resilience4j 對真實上游故障的 retry/breaker 行為不動；術語伺服器穩定性中長期方案（自架 HAPI 載入術語 / 台灣國家術語伺服器 `fhir.mohw.gov.tw/ts`）另案。**驗證**：`FhirTerminologyServiceTest` +4（`isClientError` 4xx→true/5xx+連線→false、`notFound` 旗標、local-IG 命中 found）、`FhirControllerTest` +2（not-found→404、found→200+display）；`mvn -Djacoco.skip=true test-compile` 通過，測試執行交由 CI Backend Tests（本機 sandbox TLS 攔截無法解析 surefire junit-bom POM）。**姊妹案**：PAT-166（`$expand` 對 bundled IG 的 503 連環噴）為同一家族的對稱修補。**TFDA 追溯**：需求 #586 / 設計 #587 / 風險 #588 / 驗證 #589（安全性等級 B；ISO 14971 殘餘風險評為**低** 嚴重度2×機率2=4 — 純錯誤回報語意 + 有界負快取，不改 CQL 計算/臨床決策/病人資料）。 | backend/src/main/java/com/cqlplatform/service/fhir/FhirTerminologyService.java, backend/src/main/java/com/cqlplatform/controller/FhirController.java, backend/src/test/java/com/cqlplatform/service/fhir/FhirTerminologyServiceTest.java, backend/src/test/java/com/cqlplatform/controller/FhirControllerTest.java | |
| BUG-125 | 🐛 fix | 2026-06-29 | 後端相依（`backend/pom.xml` `tools.jackson` 3.1.1→3.1.4 + `com.fasterxml` jackson-databind→2.21.4 + `org.hl7.fhir.convertors` 6.9.7→6.9.10）+ VEX（`backend/.trivyignore` 加 CVE-2026-55470/55471） | **修補相依新揭露的 CVE（backend: HAPI FHIR XXE/DSTU2 + jackson-databind；frontend: form-data）— 解 `Security Scan` 阻塞** — Trivy DB 更新後 `Security Scan` 對 backend image 回報 **8 個（7 HIGH / 1 CRITICAL）新 CVE**，全在既有相依、與程式碼無關，卡住所有對 main 的 PR（含 BUG-124，#590）。**處置（沿用 BUG-121/123「可升就升、耦合才 VEX」哲學）**：(1) **jackson**（CVE-2026-54512/54513 反序列化）— `jackson-bom` 3.1.1→3.1.4、`tools.jackson.core` databind/core→3.1.4、`com.fasterxml.jackson.core:jackson-databind` 在 dependencyManagement pin→2.21.4（蓋 Spring Boot 4.0.6 帶的 2.21.2）。(2) **CVE-2026-55470**（HAPI convertors/validation，DSTU2 不完整修補）— convertors 屬可獨立升群組 → 6.9.7→6.9.10；validation 與 core 8.8.1 耦合無獨立修補 → VEX。(3) **CVE-2026-55471 CRITICAL**（HAPI `org.hl7.fhir.utilities` `XsltUtilities.saxonTransform` XXE）— 與 core 耦合無修補 → VEX。**VEX 暴險評估（原始碼 grep 佐證）**：全 codebase **無任何** `XsltUtilities.saxonTransform`/`TransformerFactory`/`javax.xml.transform`/`VersionConvertor` 呼叫 → XXE sink 不可達；平台 R4-only，`CdsInvocationService` 偵測到 DSTU2 client server 會 fallback R4、不解析/轉換 DSTU2 資源；「DSTU2」他處僅為 CQL `using` 字串標籤 → CVE-2026-55470/55471 **NOT exposed**。**(4) 前端**：Trivy DB 同波也對 frontend 回報 `form-data` 4.0.5 → **CVE-2026-12143 HIGH**（經 axios 傳遞，fixed 4.0.6）→ `frontend/package.json` 加 `overrides: form-data ^4.0.6` 並重產 `package-lock.json`。**驗證**：`pom.xml` XML well-formed；jackson/convertors 升版的相依解析、ABI 相容與零回歸交由 CI（Backend Tests / Docker Build / Security Scan）—— 本機 sandbox TLS 攔截無法 resolve 新版相依。**TFDA 追溯**：#591（安全性等級 A — 純相依升版 + VEX，不改應用邏輯、不涉臨床決策或患者資料）。 | backend/pom.xml, backend/.trivyignore, frontend/package.json, frontend/package-lock.json | |
| PAT-170 | ✨ feat | 2026-06-02 | 前端 SEO（新 `frontend/public/og-image.png` + `frontend/index.html` og:image/twitter:image/JSON-LD + `frontend/src/i18n.ts` 動態 lang + 修 html lang） | **SEO 補強:og:image 社群縮圖 + JSON-LD 結構化資料 + 動態 html lang** — SEO 盤點補齊可在現有架構內處理的缺口。(1) **og:image**:以 PIL + 微軟正黑產 **1200×630 `og-image.png`**（品牌色 teal `#0D7377`／深藍 `#1B3A5C` 漸層 + 平台名 + tagline + 醫療十字浮水印），置 `frontend/public/`（Vite build 進 dist 根、nginx `try_files` 直接提供）;`index.html` 加 `og:image`(+`width`/`height`/`alt`)、`twitter:image`，`twitter:card` `summary`→`summary_large_image`（解決 LINE/FB 分享無縮圖）。(2) **JSON-LD 結構化資料**:`index.html` 加 `@graph`（`WebSite` + `Organization` + `SoftwareApplication`，含 applicationCategory=HealthApplication、featureList、inLanguage）→ rich results 資格;放靜態 head 故非 JS 爬蟲也讀得到。(3) **html lang**:`<html lang="en">` → `zh-Hant-TW`;並在 `i18n.ts` 加 `languageChanged` → 同步 `document.documentElement.lang`（切英文時變 `en`）。per-page Helmet 不需改（繼承 index.html og:image）。**未納入（架構限制／待產品定位）**:`/learn` prerender（需在 build 加 puppeteer headless render，重且本 app 僅 2 公開頁、Google 已能 render JS → ROI 偏低，另議）;**hreflang 不適用**（i18n 為 localStorage + 預設 zh-TW、無分語言 URL，硬加為錯誤 markup;若日後改 `/en/`、`/zh-TW/` 路由再加）。**TFDA 追溯**:#583（安全性等級 A — 前端 meta/靜態資產，不涉應用邏輯、臨床決策或患者資料）。 | frontend/index.html, frontend/public/og-image.png, frontend/src/i18n.ts | |
| BUG-123 | 🐛 fix | 2026-06-01 | CI（`.github/workflows/ci.yml` `security-scan` job 加 setup-java Maven 快取 + `dependency:go-offline`）+ 後端相依（`backend/pom.xml` tomcat `10.1.54→10.1.55`、thymeleaf `3.1.4→3.1.5`）+ VEX（`backend/.trivyignore` 加 HAPI r4 / libthrift） | **修 CI Security Scan 間歇性失敗 — 並補救它揭穿的 11 個被掩蓋的 backend CVE** — `security-scan` job 的 backend Trivy fs 掃描間歇性 `FATAL: remote Maven repository returned 429 Too Many Requests for oss.sonatype.org/.../cql-to-elm-jvm-4.5.0.pom`（PR #578 即中此 flake）。**根因（CI flake）**:Trivy 的 pom.xml 分析器要抓 POM 解析相依樹，`security-scan` job 沒有本地 Maven 快取 → 對 oss.sonatype.org（cqframework repo）發大量請求被 **429 封 30 分鐘** → 整 job FATAL。**修補（採 Trivy 建議:掃描前 populate `~/.m2`）**:`security-scan` 已 `needs: backend-test`（後者 `cache:'maven'` 跑過 `mvn test`、快取已存），backend Trivy 前加 (1) `setup-java cache:'maven'` 還原快取、(2) `mvn dependency:go-offline || true` → Trivy 改讀本地 `~/.m2`,不再打 Sonatype。**關鍵發現**:修好後 Trivy 首度跑完，揭穿這個 429 crash 一直在**掩蓋 backend image 11 個真實 CVE（4 CRITICAL / 7 HIGH）**——scan 之前連分析都沒開始就掛了。**補救**:(a) 升 `tomcat-embed-core 10.1.54→10.1.55`（清 **CVE-2026-41293 CRITICAL** + 43512/43515/41284/42498/43513）;(b) 升 `thymeleaf 3.1.4→3.1.5.RELEASE`（清 **CVE-2026-41901 CRITICAL**）;兩者皆 pom property 覆蓋 Spring Boot 4.0.6 帶的版本。(c) **VEX**（`backend/.trivyignore` + 理由）:`CVE-2026-45367`（HAPI `org.hl7.fhir.r4` 6.7.9 FHIRPath ReDoS — 屬不能單獨升的 group，AbstractMethodError;影響為 DoS 非資料外洩、且 FHIRPath 僅在已認證 + timeout 界限的 CQL 執行內跑）、`CVE-2026-43869`（`libthrift` 0.22.0 上游無修補;本服務不跑任何 Thrift RPC server、bypass 不可達）。**驗證**:`yaml.safe_load` 確認 job 步驟順序;Backend Tests 確認 tomcat/thymeleaf 升版相容;升版後 Trivy 應只剩 VEX 的 2 個 → Security Scan 綠。**TFDA 追溯**:#579（安全性等級 A — CI 修補 + 相依安全升版/VEX,不改應用邏輯、不涉臨床決策或患者資料）。 | .github/workflows/ci.yml, backend/pom.xml, backend/.trivyignore | |
| PAT-169 | 🔧 chore | 2026-06-01 | 部署（`docker/docker-compose.yml` backend/frontend 加 `image:` 指向 GHCR + `docker/.env.example` 加 image tag 變數）+ 文件（`DEPLOYMENT_GUIDE_zh-TW.md` §3.5 改為 pull 流程） | **VM 部署改為 pull GHCR 預建映像，停止在 2 核 VM 上 build** — 接續 BUG-122 排查發現 backend 啟動慢到 ~12 分鐘:根因是在 **2 核 VM** 上 `docker compose build`（Maven build 把雙核打滿）與 backend 自身啟動爭 CPU（佐證:`nproc=2`、啟動時段 15-min load `2.59`>2、cgroup throttle 極少 → 是排程器層級爭用非硬上限;慢在 `Root WebApplicationContext init 142s` 類載入/JIT，非 DB/Flyway）。**關鍵發現**:CI（`.github/workflows/deploy.yml`）其實**早已**在每次 main CI 成功時 build + push 映像到 `ghcr.io/lusnaker0730/cql/{backend,frontend}`（實測**公開、可匿名 pull**、tag 含 `latest` + 每 commit `sha-xxxxxxx` 共 25+ 個）——VM 卻一直用 `build:` 本地重建、完全沒用這些現成映像。**修補**:(1) compose backend/frontend 加 `image: ghcr.io/lusnaker0730/cql/{backend,frontend}:${BACKEND_IMAGE_TAG:-latest}`（保留 `build:` 不影響本地 dev）;(2) VM 部署改為 `docker compose pull && up -d --no-build`（~30s,不再 build,VM 不需 `docker login`——映像公開）;(3) `.env.example` 加 `BACKEND_IMAGE_TAG`/`FRONTEND_IMAGE_TAG` 可釘 sha 做重現/秒回滾;(4) DEPLOYMENT_GUIDE §3.5 改寫 pull 流程。**效益**:消除正式機 build 的 CPU 爭用→啟動不再被拖慢;部署可重現/可回滾。**未納入**:CI 自動 SSH 進 VM pull（需把 VM SSH key 放 CI secret,暴露面變大,待安全邊界確認再議,本次仍手動 pull）。**驗證**:`yaml.safe_load` 確認 compose image+build 並存;CI Docker Build。**TFDA 追溯**:#577（安全性等級 A — 部署流程改善，不改應用邏輯、不涉臨床決策或患者資料）。 | docker/docker-compose.yml, docker/.env.example, DEPLOYMENT_GUIDE_zh-TW.md | |
| BUG-122 | 🐛 fix | 2026-06-01 | 後端容器（`backend/Dockerfile` ENTRYPOINT `-Xmx716m -Xms256m`→`-Xmx1024m -Xms512m` + 修正過時註解） | **修補 backend `HighHeapUsage` 持續誤告警 — heap 按舊 1G 假設低估，調回容器 1.5G 的 70%** — 啟用 Telegram 告警（BUG-120）後，backend 持續每 4h 觸發 `HighHeapUsage` 轟炸使用者。**調查（皆查證、非洩漏）**:從 Prometheus 拉老年代（Tenured Gen）24h 趨勢 `414→417MB`，**幾乎不動、0 次 GC 回收 → 穩態 live set，不是記憶體洩漏**;老年代 `used/max = 417/477MB = 87.3%` → 觸發 per-pool `>85%` 門檻。根因:`backend/Dockerfile` 的 `-Xmx716m` 註解寫「70% of 1G」，但 `docker-compose.yml` 容器上限其實是 **1.5G**（註解 stale）→ heap 被按舊 1G 假設低估，老年代上限只有 477MB，live set 417MB 永遠卡 87%。容器實測 `1.06/1.5 GiB`、VM 總 7.8G 可用 4.0G → 有空間。**修補**:按原設計「heap ≈ 70% 容器上限」套到實際 1.5G → `-Xmx716m→1024m`、`-Xms256m→512m`。SerialGC 老年代 cap → ~680MB，417MB live = **~61%**，穩低於 85%，留 ~500MB 給 metaspace/threads/direct buffers（符合原設計，**不需動容器上限**）;附帶減少 full GC、提升 CQL 執行 headroom。**未過度修**:未改告警規則（per-pool old-gen 85% 雖偏敏感，但 heap 調整後已不誤報，留待需要時再議）;未動容器 memory limit（1024m 在 1.5G 內安全）。**驗證**:CI（Backend Tests / Docker Build）確認 image 可建;部署後於 VM 觀察老年代 % 應降至 ~61%、`HighHeapUsage` 自動 resolve。**部署待辦（使用者）**:PR merge 後 VM `git pull && docker compose build backend && docker compose up -d backend`（會短暫重啟 backend）。**TFDA 追溯**:#575（安全性等級 A — JVM 記憶體調校，不改應用邏輯、不涉臨床決策或患者資料）。 | backend/Dockerfile | |
| BUG-121 | 🐛 fix | 2026-06-01 | 前端相依（`frontend/package.json` axios `^1.15.0`→`^1.16.1`、`package-lock.json` 重新產生、`frontend/.trivyignore` 移除過時 suppression） | **修補 frontend axios HIGH CVE — 升 1.16.1 清掉 7 個 prototype-pollution / proxy-bypass CVE** — Trivy Security Scan 對 frontend 回報 axios 1.15.0 三個新 HIGH CVE:`CVE-2026-44492`（`shouldBypassProxy` 不認 IPv4-mapped IPv6 → NO_PROXY bypass，fixed 1.16.0）、`CVE-2026-44494`（`config.proxy` prototype pollution → full MITM，fixed 1.16.0）、`CVE-2026-44495`（prototype pollution → 憑證竊取 / response hijacking，fixed 1.15.2）;另 PAT-157 曾用 VEX 暫忽略的 4 個（`CVE-2026-42033/42035/42043/42264`）當時 1.15.1/1.15.2 尚未發佈到 npm。**查證**:VM 對 `registry.npmjs.org` 查得 axios latest=`1.16.1`（1.15.1/1.15.2/1.16.0/1.16.1 均已發佈）→ 升 `^1.16.1` 一次修掉全部 7 個。**作法**:本機 sandbox 無外網，於 VM 用 `node:26-alpine`（與 `frontend/Dockerfile` 一致、lockfileVersion 維持 3 不漂移）跑 `npm install axios@^1.16.1 --package-lock-only` 產生正確 lock，帶回本機;package.json 只保留 axios 一行改動（還原 npm 順手把 devDependencies 重排的噪音）;`.trivyignore` 移除已修的 4 個 suppression 僅留歷史註記。**曝險評估**:frontend 純 SPA、axios 僅跑在使用者瀏覽器、`api/client.ts` 用預建 instance + 硬編 interceptor 不接受使用者控制的 prototype mutation、NO_PROXY 未設 → 實際曝險 LOW，屬 default-secure 應修。**驗證**:交由 CI（Frontend Lint & Type Check / Frontend Tests / Docker Build / Trivy）確認升版不破壞 build 並清除告警。**TFDA 追溯**:#573（安全性等級 A — 前端相依升版，不改動應用邏輯、不涉臨床決策或患者資料）。 | frontend/package.json, frontend/package-lock.json, frontend/.trivyignore | |
| BUG-120 | 🐛 fix | 2026-05-31 | 監控設定（`docker/alertmanager.yml` receiver 改 Telegram、`docker-compose.yml` alertmanager service 加 entrypoint 注入 token/chat_id、`docker/.env.example` 加 `TELEGRAM_*`）+ 文件（`DEPLOYMENT_GUIDE_zh-TW.md` §8.3） | **修復 production VM「假 ServiceDown 告警 + 告警通道從未生效」並把 Alertmanager 接成可用的 Telegram 通知** — VM Docker log 巡檢:應用容器（backend/hapi/postgres）一週內 0 應用錯誤，唯一一堆錯誤是 alertmanager 今天 00:00–05:21 共 64 筆 `Notify for alerts failed ... dial tcp [::1]:9095: connect: connection refused`。**根因 A（假告警，VM 已即時處置、無 code 變更）**:Prometheus scrape `backend:8080/actuator/prometheus` 回 **401** → `up=0` → 觸發 `ServiceDown`，但 backend `RestartCount=0`、healthy、整段正常記 log，是假告警。401 來自 PAT-157 輪替 `METRICS_SCRAPE_PASSWORD`（backend 已吃新值 `PNGN…`）但 **prometheus 容器「Up 5 weeks」從未重啟仍持舊密碼 `ON1j…`**。已於 VM `docker compose up -d --no-deps --force-recreate prometheus` 修復並驗證 target `health=up`、active alerts `[]`。**根因 B（本 PR）**:alertmanager 三個 receiver（default/pager/slack）全寫死 `http://localhost:9095/*`，repo 內無任何服務在 9095（早期 `phase 4/5` scaffold placeholder，從未實作），即「就算告警是真的也送不出去」。**修補（完成而非移除）**:把 receiver 改為 Alertmanager 原生 `telegram_configs`（v0.27 支援、VM 對 `api.telegram.org` 連線實測可達、`amtool check-config` 驗證 SUCCESS）;保留 critical 1h / warning 4h 重複間隔與 inhibit 規則;訊息走 plain-text（避免 description 內 URI 的 `&`/`<`/`>` 破壞 HTML parse_mode）;`send_resolved: true`。**Secret 處理（比照 PAT-113 password_file 模式）**:bot token 不進 git，alertmanager 容器 entrypoint 從 `TELEGRAM_BOT_TOKEN` env 寫入 `/tmp/telegram_token`（`bot_token_file`），並用 sed 把 `TELEGRAM_CHAT_ID` 注入 `__TELEGRAM_CHAT_ID__` placeholder;兩者任一未設則 fail-fast 拒啟動（避免靜默無告警）。`.env` 已 gitignore，`.env.example` + DEPLOYMENT_GUIDE §8.3 補上 BotFather 建 bot / 取 chat_id / 驗投遞步驟。**部署待辦（使用者）**:於 VM `docker/.env` 填入真實 `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` 後 recreate alertmanager，再以 `amtool alert add` 驗證收到 Telegram。**TFDA 追溯**:#570（安全性等級 A — 純維運/監控設定，不改動應用回應、不涉臨床決策或患者資料）。 | docker/alertmanager.yml, docker/docker-compose.yml, docker/.env.example, DEPLOYMENT_GUIDE_zh-TW.md | |
| BUG-118 | 🐛 fix | 2026-05-24 | 後端（`GlobalExceptionHandler` 新增 `HttpRequestMethodNotSupportedException` + `HttpMediaTypeNotSupportedException` handlers）+ 測試（`GlobalExceptionHandlerTest` 加 2 tests） | **HTTP method/media-type 不支援被誤記為 ERROR-level — 移除 false alert 噪音** — VM log 過去 5 天觀察發現 backend 有 397 個 `ERROR` 條目全是 `HttpRequestMethodNotSupportedException: Request method 'GET' is not supported`（≈ 132/天），全部是 bot scanner 拿 GET 打 POST-only endpoint，根本不是應用錯誤。根因：`GlobalExceptionHandler` 沒對 Spring 內建的 4xx 例外類別寫明確 handler，全部 fallthrough 到 `handleGenericException(Exception)` → `log.error("Unhandled exception", ex)` + 嘗試對可能已關閉的 stream 寫 500 body（這又觸發二次 `Failure in @ExceptionHandler` WARN，每筆製造 2 條 WARN + 1 條 ERROR）。如果未來有 alert 觸發條件設 `ERROR rate > N/min`，會被這個 false alarm 蓋住真問題。**修補**：(a) 新 `handleMethodNotSupported(HttpRequestMethodNotSupportedException)` → 405 + `Allow` header (RFC 7231 §6.5.5)，log 降為 WARN 且不帶 stack trace（client 拿錯 method 不需要 stack）；(b) 新 `handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException)` → 415 + WARN（鄰近類型一起補，避免下次再被掃描器戳）；(c) Allow header 用 `ex.getSupportedHttpMethods()` 列出該 endpoint 真正支援的 method，方便正當 client（非 bot）debug。**為什麼不過度補**：只補觀察到的 false alert 來源（`HttpRequestMethodNotSupported`）+ 一個鄰近 4xx (`HttpMediaTypeNotSupported`)。`NoHandlerFoundException` 預設 Spring 自己處理回 404 不會落到 GlobalExceptionHandler，不必補。`MissingServletRequestParameterException` 之類 binding 例外目前沒在 log 觀察到，不過度抓。**驗證**：(a) `GlobalExceptionHandlerTest` 加 `handleMethodNotSupported_shouldReturn405WithAllowHeader` 驗 status / Allow header 含 supported methods / message 含 client 用的 method；(b) `handleMediaTypeNotSupported_shouldReturn415` 驗 status + message 含 unsupported Content-Type；(c) 既有 24 個 tests 全綠不回歸。**TFDA 追溯**：BUG（安全性等級 A — 純 observability hygiene，不改動回應結構、不影響 client behavior、不涉臨床決策）。 | backend/src/main/java/com/cqlplatform/exception/GlobalExceptionHandler.java, backend/src/test/java/com/cqlplatform/exception/GlobalExceptionHandlerTest.java |[`b0f1b4f`](../../commit/b0f1b4f) |
| PAT-167 | ✨ feat | 2026-05-21 | 後端（pom + 新 `WebSocketConfig` + 新 `NotificationWebSocketHandler` + `NotificationService` 移除 SSE + `NotificationController` 移除 `/subscribe` 端點 + `SseTicketService` JavaDoc 更新）+ 前端（`useNotifications.ts` `EventSource`→`WebSocket` + 重寫 mock 測試 + 新 message 解析）+ nginx 設定（`/api/notifications/ws` upgrade headers）+ 測試（新 `NotificationWebSocketHandlerTest` 7 tests + `WebSocketConfigHandshakeTest` 3 tests + `useNotifications.test.tsx` 重寫 9 tests）| **通知推送 SSE → WebSocket 根本性遷移 — 永久解決 Cloudflare 524 / `ERR_QUIC_PROTOCOL_ERROR`** — production VM 多位使用者持續觀察到 `ERR_QUIC_PROTOCOL_ERROR` 與 Cloudflare `524 Origin Timeout` 在 SSE `/api/notifications/subscribe` 連線上，**雖然後端已實作 25s `:keepalive` SSE comment ping**（VM 內 curl 直連 backend 證實 70s 內收到 3 次 ping，scheduler 完全正常），但過 Cloudflare 後仍 ~100-156s 被砍。根因：Cloudflare 對 **SSE comment-only frame** 的 idle-detection 不可靠（社群長期問題，無 official fix）。**修補**：(a) 後端引入 `spring-boot-starter-websocket`，新 `WebSocketConfig` 在 `/api/notifications/ws` 註冊 `NotificationWebSocketHandler` + `TicketHandshakeInterceptor`；(b) 既有 `SseTicketService` 與 `/api/auth/sse-ticket` URL 與 `JwtAuthenticationFilter` ticket-redemption flow **零修改**重用 — handshake interceptor 直接從 `SecurityContextHolder` 讀已 redeem 的 principal（避免 double-redeem 把單次 ticket 浪費）；(c) `NotificationWebSocketHandler` 以 `Map<String, List<WebSocketSession>>` 管理多 tab session，`afterConnectionEstablished` 自動送初始 unread-count（沿用 SSE 既有 UX），`pushToUser` 廣播 JSON message 給該 user 所有 session；(d) `@Scheduled(fixedRate=25_000)` 改送 **protocol-level `PingMessage`** 而非 application-layer SSE comment — Cloudflare WS idle 計時器對 protocol ping frame **必認**為 activity，且 WS 預設 idle window 300s（SSE 100s 的 3 倍 buffer）；(e) `NotificationService` 移除所有 `SseEmitter` 邏輯（`emitters` map、`subscribe()`、`sendSseKeepalives()`、`pushToUser()`）改 delegate 給 `NotificationWebSocketHandler`；(f) `NotificationController` 移除 `/subscribe` SSE 端點（clean cutover，無 shim）；(g) 前端 `useNotifications.ts` `EventSource` → `WebSocket`，URL 從 `import.meta.env.VITE_API_URL` + `window.location` 動態組合 `ws://` / `wss://`，message handler 解析 `{type:"unread-count",count:N}` / `{type:"notification",...}` JSON envelope，沿用 PAT-144 reconnect + unmount cleanup 邏輯；(h) nginx `/api/notifications/ws` location block 加 `proxy_set_header Upgrade $http_upgrade` + `Connection "upgrade"` + `proxy_read_timeout 86400s`。**安全性**：handshake interceptor 拒絕無 SecurityContext / anonymous principal 的 upgrade 請求（401 Unauthorized）；ticket 單次使用 + 30s TTL 保留；`/api/notifications/ws` 仍受 `SecurityConfig` `/api/**` authenticated 規則覆蓋。**驗證**：(a) 新 `NotificationWebSocketHandlerTest` 7 tests（register session + 初始 unread-count、缺 username attr 拒連、close 移除 session、pushToUser 多 session 廣播、無 session no-op、ping 對 open session、ping 跳過 closed、無 subscriber fast-path）；(b) 新 `WebSocketConfigHandshakeTest` 3 tests（authenticated 接受、anonymous 拒、空 context 拒）；(c) 前端 `useNotifications.test.tsx` 從 `MockEventSource` 重寫成 `MockWebSocket` 9 tests（mount 開連線、無 token 跳過、unmount 關閉、PAT-144 reconnect timer 清理、ticket race condition、close/error 重連、unread-count message 寫 cache、notification message invalidate cache、malformed JSON 不爆）。**為何不選 workaround**：A) `:comment` → `event: ping\ndata: ok` 名稱型 event（80% 機率）— 仍依賴 Cloudflare 未文件化行為；B) 縮 25s → 20s — 同樣是 workaround；C) Cloudflare Page Rule — 需 Enterprise plan；D) Bypass Cloudflare 子域名 — 暴露 origin IP + 失去 DDoS 防護。**E (WebSocket)** 是唯一觸及第 5 層根因（「在 CDN 後面跑長連線協議，但 SSE 不是為 CDN 設計的」）的選項，且 WS 是 protocol-native 解法，業界（Slack/Discord/GitHub/Linear）標準。**TFDA 追溯**：需求 #544（安全性等級 A — 純傳輸協議遷移、不涉臨床決策／CQL 計算／患者資料完整性，純改善可用性）。 | backend/pom.xml, backend/src/main/java/com/cqlplatform/config/WebSocketConfig.java, backend/src/main/java/com/cqlplatform/service/NotificationWebSocketHandler.java, backend/src/main/java/com/cqlplatform/service/NotificationService.java, backend/src/main/java/com/cqlplatform/controller/NotificationController.java, backend/src/main/java/com/cqlplatform/security/SseTicketService.java, backend/src/test/java/com/cqlplatform/service/NotificationWebSocketHandlerTest.java, backend/src/test/java/com/cqlplatform/config/WebSocketConfigHandshakeTest.java, frontend/src/hooks/useNotifications.ts, frontend/src/hooks/__tests__/useNotifications.test.tsx, docker/nginx.conf | |
| BUG-117 | 🐛 fix | 2026-05-20 | 前端（`api/client.ts` 移除「403 一律 logout」+ `EditorPage.tsx` / `PreferencesDialog.tsx` admin-role gate）+ 測試（新 `client.interceptor.test.ts` 2 tests） | **USER 角色登入後立即跳回 `/login` — axios response interceptor 把 403 誤判為 session 失效** — Production VM 開啟 self-registration 後使用者反映「按登入後直接跳回登入頁、console 沒錯誤」。根因鏈：(1) `frontend/src/api/client.ts:107-113` 把**所有 403**（含「已認證但角色不足」）一律當成 session 失效，清 localStorage + `window.location.href = '/login'`；(2) `EditorPage.tsx:168` 主頁掛載時無條件 `useQuery` 拉 `/api/settings/ai-status`；(3) `SettingsController` class-level `@PreAuthorize("hasRole('ADMIN')")`（PAT-145 故意設計）→ USER 取得 403 是預期；三者組合：USER 登入 → 跳 `/` → 拉 ai-status → 403 → interceptor 清 token 強制跳 `/login`。HTTP 401 ≠ 403：401 = 未認證 / 403 = 已認證但權限不足。**修補**：(a) `client.ts` interceptor 只在 `401 && isAuthEndpoint` 才清 token + redirect，403 純當權限錯誤 propagate（讓元件自己處理）；(b) `EditorPage.tsx` ai-status `useQuery` 加 `enabled: userRole === 'ADMIN'` 不再無謂打 admin endpoint；(c) `PreferencesDialog.tsx` 對非 admin 跳過 vsac/ai-status 呼叫；(d) 新建 `client.interceptor.test.ts` 2 tests 鎖住「403 不該觸發 logout」與「401 + auth endpoint 仍然 logout」兩個不變式。**安全性**：移除誤動作不影響 401 失效處理；admin endpoint 仍由後端 `@PreAuthorize` 把關（defense-in-depth 未弱化）。**TFDA 追溯**：需求 #541（安全性等級 B — 影響可用性無患者安全直接風險）。 | frontend/src/api/client.ts, frontend/src/api/__tests__/client.interceptor.test.ts, frontend/src/pages/EditorPage.tsx, frontend/src/components/common/PreferencesDialog.tsx | |
| PAT-166 | 🔧 fix | 2026-05-17 | 後端（`FhirTerminologyService.expandValueSet` 加 local IG fallback + 新 `tryLocalExpand` helper）+ 測試（新 `FhirTerminologyServiceTest` 9 tests） | **ValueSet `$expand` 對 bundled TW Core IG 加 local-first fallback — 修 VM log `/api/fhir/ValueSet/$expand` 503 連環噴** — Production VM log 反覆出現 503 on `https://twcore.mohw.gov.tw/ig/twcore/ValueSet/condition-code-sct-tw` 與 `.../ValueSet/category-code-tw`：根因是 `FhirTerminologyService.expandValueSet()` 不論 canonical URL 是否屬於本地 bundled IG，都直接 forward 到 `tx.fhir.org/r4/ValueSet/$expand`。curl 重現顯示 `tx.fhir.org` 對這兩個 TW canonical 回 422 `ValueSet not found`（不在 HL7 public tx server 索引內）+ `lookupCode()` 已實作 local-IG-first（line 175-182）但 `expandValueSet()` 缺對稱路徑。**修補**：(1) `expandValueSet()` 開頭加 Priority 1 — 透過 `igService.getValueSetByUrl(url)` 撈 bundled VS，若存在先呼叫 `tryLocalExpand(vs, filter)`，能完整列舉 → 回填 expansion；不能列舉（filter / chained / 外部 CS）→ 直接 return VS body（UI 收到 compose rules 而非 503）；只有 `igService.getValueSetByUrl()` 找不到才走 remote。(2) 新 package-private `tryLocalExpand(ValueSet vs, String filter)` helper：對每個 `compose.include`，若有 inline concepts 走純列舉、若無 concepts 但 system 在 `codeSystemsByUrl` 走 `addConceptsRecursive()` 把整棵 CodeSystem concept tree 攤平；任何 include 帶 `filter[]`（SNOMED `is-a` etc.）/ `valueSet[]`（chained VS reference）/ 外部 CS（不在 bundle） → return null，呼叫端走 VS-body 路徑。(3) `addConceptsRecursive` 走 `CodeSystem.concept[].concept[]` 樹狀；`matchesFilter` 在 display/code 做 case-insensitive substring。**邊界處理**：`condition-code-sct-tw`（SNOMED is-a filter）→ tryLocalExpand 回 null → return VS body；`category-code-tw`（include HL7 `observation-category` CS 不在 bundle）→ 同樣 return VS body；純列舉的 VS 完整 expand。**驗證**：(a) 新建 `FhirTerminologyServiceTest` 9 tests — pure concept enum / text filter / include whole local CS（含 recursive tree）/ filter-based bail-out / chained VS bail-out / external CS bail-out / no compose bail-out / expandValueSet local pure path 不打 remote / expandValueSet local filter path 回 definition body；(b) 鄰近 `FhirImplementationGuideServiceTest` 7/7 regression 全綠；(c) 合計 16/16 BUILD SUCCESS（`-Djacoco.skip=true` workaround JDK 25 JaCoCo `Unsupported class file major version 69`）；(d) 完整 dynamic 反證 — `curl -k https://tx.fhir.org/r4/ValueSet/$expand?url=...condition-code-sct-tw` 確認上游 422，本地 bundled IG `package/ValueSet-condition-code-sct-tw.json` 確實存在 → 證明 local fallback 是正確補洞而非繞過。**未在本 PR**：SNOMED `is-a` 在本地 IG 沒 SCT closure 表所以**永遠**無法純本地展開（需 SNOMED license + 完整 hierarchy），這由 UI 收 VS body 自行渲染 compose rules 處理；遠端 5xx/422 對 non-bundled URL 的 retry / circuit-breaker 行為不動。 | backend/src/main/java/com/cqlplatform/service/fhir/FhirTerminologyService.java, backend/src/test/java/com/cqlplatform/service/fhir/FhirTerminologyServiceTest.java |[`6c6e528`](../../commit/6c6e528) |
| PAT-164 | ✨ feat | 2026-05-15 | 前端（新 `arithmeticTypes.ts` shared module + `ArithmeticElement` / `ArithmeticUnaryElement` 型別感知過濾 UI）+ 後端（`ExpressionCqlEngine` `Round(x, precision)` 雙參數 emit）+ i18n | **Arithmetic Element 擴充 Phase 4 (最終) — 型別感知 operator/function filtering + Round(x, precision) 雙參數** — 全功能 Arithmetic 擴充計畫的第四 (最終) 階段；4-phase 完成後系統達到設計目標。**前端修補**：(1) 新檔 `arithmeticTypes.ts` shared module — `inferOperandType()` 把 operand mode + element returnType 推導成 5 種型別 (`integer` / `decimal` / `quantity` / `string` / `date` / `unknown`)，`allowedOperators(types[])` 依 CQL 1.5 spec 矩陣過濾 (`+` 含 string concat、`-/*/`/ 對 numeric+quantity、`mod`/`div` integer-only、`^` integer/decimal)，`allowedUnaryFunctions(type)` 同樣依矩陣 (`Floor`/`Ceiling`/`Round`/`Truncate` integer/decimal、`Abs`/`Negate` 含 quantity)；`ROUND_PRECISION_RE` `/^\d+$/` 驗證；(2) `ArithmeticElement.tsx` — 計算 `operandTypes` + `allowedOps`，operator dropdown 只顯示合法子集；**已選 operator 不在 allowed 時仍渲染並標 `(invalid for operand types)`**（不 silent 改 user 選擇）；任一 operand 為 unknown 時 fallback 顯示全 7 個 (conservative)；(3) `ArithmeticUnaryElement.tsx` — 同樣 function dropdown 過濾 + invalid marker；Round selected 時新增可選 `precision` `TextField`（regex 驗證、紅 helper text 提示）；preview emit `Round(x)` 或 `Round(x, N)`；(4) `BaseElement.fields[]` 新欄位 `precision` (optional string)，舊 artifact 缺值自動 fallback 到 1-arg form。**後端修補**：(1) `ExpressionCqlEngine` 新 `ROUND_PRECISION_PATTERN` `Pattern.compile("\\d+")`；(2) `arithmeticUnary` case 加 Round 分支：function=Round 且 precision 非空且通過 regex → emit `Round(x, N)`；否則 emit `Round(x)`；**對 tampered precision（如 `"; DROP TABLE"`、`"-1"`、`"abc"`）silent fallback 到 1-arg** (defense-in-depth)；其他 function 完全忽略 precision 欄位（不污染 emit）。**安全機制** (沿用 + 強化)：(a) 前端 UI 過濾，但**後端不 reject 型別不匹配** — CQL translator 是 source of truth；(b) Round precision 後端正則 hard-validate；(c) operator/function 已選但 invalid 時 UI 顯示警示，CQL translator dry-run 仍會跑；(d) 三層防護沿用 (UI preview + 後端 allow-list + Validator dry-run)。**i18n**：`authoring.arithmetic.invalidForTypes` + `authoring.arithmeticUnary.precision` + `authoring.arithmeticUnary.precisionInvalid`（en + zh-TW 同步）。**驗證**：(a) 新建 `arithmeticTypes.test.ts` 21 tests（`inferOperandType` 8 條 / `allowedOperators` 7 條 / `allowedUnaryFunctions` 6 條 — 涵蓋所有型別 + unknown conservative + 邊界）；(b) `ArithmeticElement.test.tsx` 加 3 個 PAT-164 測試（integer+integer 全 7、string+string 只 +、已選 mod 配 decimal flagged invalid），13→16 全綠；(c) `ArithmeticUnaryElement.test.tsx` 加 5 個 PAT-164 測試（quantity 過濾顯示 Abs+Negate、Floor with quantity flagged invalid、Round 無 precision、Round with 2、Round with bad precision fallback、non-Round 不顯示 precision input），7→12 全綠；(d) 後端 `ExpressionCqlEngineTest` 加 6 個新 Round precision 測試（兩參數 happy、無 precision、empty、bad string、negative、Floor 忽略 precision），100→106 全綠；(e) 後端全 regression：`CqlArtifactBuilderTest` + `ExpressionTreeValidatorTest` + `EcqmCqlBuilderTest` + `EcqmExpressionTreeValidatorTest` + V56MigrationTest 合計 **207/207 零回歸**；(f) 前端合計 **50/50 全綠**（21 type + 16 N-ary + 13 unary）；(g) `npx tsc --noEmit` + `npm run lint` clean。**4-phase 擴充計畫狀態**：PAT-161 (binary mod/div/^/Quantity) ✅ + PAT-162 (unary Abs/Ceiling/Floor/Negate/Round/Truncate) ✅ + PAT-163 (N-ary + precedence parens + Flyway V56) ✅ + PAT-164 (型別感知 + Round precision) ✅ — **全部達到設計目標**。**TFDA 追溯**：需求 #514 / 設計 #515 / 風險 #516 / 驗證 #517（安全性等級 B；ISO 14971 殘餘風險評為**低** — 純 UI 增強 + Round 額外參數，不改動既有 storage shape、不擴大後端 reject 邊界、既有 artifact 不受影響）。 | backend/src/main/java/com/cqlplatform/service/authoring/ExpressionCqlEngine.java, backend/src/test/java/com/cqlplatform/service/authoring/ExpressionCqlEngineTest.java, frontend/src/components/authoring/base-elements/{ArithmeticElement,ArithmeticUnaryElement}.tsx, frontend/src/components/authoring/base-elements/arithmeticTypes.ts, frontend/src/components/authoring/base-elements/__tests__/{arithmeticTypes,ArithmeticElement,ArithmeticUnaryElement}.test.{ts,tsx}, frontend/src/locales/{en,zh-TW}/authoring.json |[`79628b9`](../../commit/79628b9) |
| PAT-163 | ✨ feat | 2026-05-15 | 前端（`ArithmeticElement.tsx` 全新 N-ary UI + 新 `arithmeticEmit.ts` shared emit）+ 後端（`ExpressionCqlEngine` N-ary case + `emitNaryArithmeticCql` + precedence parens）+ Flyway V56 Java migration + rollback SQL + i18n | **Arithmetic Element 擴充 Phase 3 — N-ary expressions with operator precedence parens + Eager Flyway migration** — 全功能 Arithmetic 擴充計畫的第三階段（前接 PAT-161 binary + PAT-162 unary、後接 PAT-164 型別感知）。把 2-ary 升級成 N-ary（單一 expression 含 2-10 operand + N-1 operator），CQL preview 與後端 emit 都自動加括號顯示 precedence（`a + b * c` → `a + (b * c)`），既有 2-ary 資料一次性 Flyway V56 in-place migration 轉成 N-ary shape。**設計選擇**：使用者於 AskUserQuestion 三選一中明確選 (A) in-place 升級 + (b) 顯示括號 + (c) Eager Flyway 一次性轉換。**Storage shape 變更**：`fields[]` 內從 `left_*/right_*/operator` scalar 改為 `operands` (JSON array) + `operators` (JSON array)；每個 operand 物件有 `mode` (element/literal/quantity) + 對應 `operand_id` / `operand_literal` / `operand_literal_value` / `operand_literal_unit`。**Flyway V56 (Java migration)**：(1) 新檔 `backend/src/main/java/db/migration/V56__migrate_arithmetic_to_nary.java` — `BaseJavaMigration` 子類，用 Jackson parse `cds_artifact.base_elements` 與 `ecqm_artifact.base_elements` TEXT JSON，逐 row 找 `type=arithmeticExpression` 且**沒有 `operands` field** 的 element，把 `left_*/right_*/operator` 轉成 `operands[]` + `operators[]` 新欄位；單 row JSON parse 失敗只 log 不 abort；idempotent（已轉過的不重做）；(2) 對應 rollback SQL `backend/src/main/resources/db/rollback/rollback_V56__migrate_arithmetic_to_nary.sql` — 純 PL/pgSQL DO 區塊，N=2 完整可逆，N>2 keeps operand 0,1 + operator 0（with NOTICE）；(3) 修補注意點 — JSDoc 內不能寫 `*/`（會提早關閉 comment block），改寫成 `left_x / right_x`。**後端 (ExpressionCqlEngine)**：(1) `arithmeticExpression` case 重寫，先嘗試 N-ary path（讀 `operands` + `operators` raw value），fallback 到舊 2-ary path（讀 `left_*/right_*/operator` 走 `resolveArithmeticOperand("left"/"right")` —**永遠保留作為 defense-in-depth**）；(2) 新 `getFieldRawValue` helper 取 List/Array value 而非 stringify；(3) 新 `emitNaryArithmeticCql` helper：先 validate operand 數量 (2-10)、operator 數量 (operands.length-1)，再 resolve 每個 operand 走新 `resolveNaryOperand`（mode + 直接 key 不再用 side 前綴），對 operator allow-list 驗證 fail-safe +，最後呼叫 `groupByPrecedence` 遞迴 left-associative 分組產 CQL；(4) `groupByPrecedence` 演算法：找 rightmost lowest-precedence operator 為 split 點，左右遞迴，**任何非單一 operand 的 sub-group 一律加括號**（清晰勝過 minimal-parens）；(5) precedence 表：`^`=3 > `*//mod/div`=2 > `+/-`=1，per CQL 1.5 spec。**前端 (`ArithmeticElement.tsx`)**：(1) 全新 UI — vertical operand list，每 row 一個 OperandField + 「−」delete button（disabled 在 N=2 邊界），row 之間有 operator dropdown，最底加「+ Add operand」button（disabled 在 N=10 邊界）；(2) 新檔 `arithmeticEmit.ts` shared module（`NaryOperand` type、`resolveNaryOperand`、`groupByPrecedence`、`emitNaryArithmeticCql`、`convertLegacy2aryToNary`）— **與後端 emit logic 1:1 對應**確保 UI preview 與 server 輸出一致；(3) `readOperandsAndOperators` helper 處理向後相容 — element 沒有 `operands` field 時即時把 `left_*/right_*` 轉成 N=2 N-ary in memory；(4) `writeNary` 寫回時自動 strip 所有 legacy field，下次儲存 on-disk shape 就轉到 N-ary（lazy migration as defense-in-depth on top of Flyway eager）；(5) sub-path icon imports (DeleteIcon / RemoveCircleOutline / AddCircleOutline) 沿用 PR #501 修法。**前端 (`BaseElements.tsx`)**：`handleAddArithmetic` 初始 shape 直接走 N=2 N-ary（operands[2] + operators[1]），不再寫 legacy left_*/right_* 欄位。**i18n**：`authoring.arithmetic.*` 加 `addOperand` / `removeOperand` / `operandLabel`（en + zh-TW 同步）。**安全機制**（沿用 PAT-159/161/162 三層 + 一個新層）：(1) operator 後端 allow-list；(2) operand 重用 PAT-161 已驗證 regex（NUMERIC + UCUM）；(3) ExpressionTreeValidator dry-run；(4) **新增** N 範圍 hard limit (2-10) 與 operator count 一致性 check 防止畸形 input。**驗證**：(a) 後端 `ExpressionCqlEngineTest` 加 14 個新 N-ary 測試（2-operand baseline、3-operand same/mixed/descending precedence、power highest precedence、mod 與 multiply 同 precedence、5-operand 複雜公式 BMI/eGFR-like、legacy 2-ary fallback、invalid operator fallback、too few/too many operands、operator count mismatch、unresolved quantity operand、element operand 識別子 escape），86 → 100 全綠；(b) 新 `V56MigrationTest` 7 tests（legacy → nary 轉、already nary no-op、run twice idempotent、mixed base elements 只有 arithmetic 動、quantity operand 保留、empty array、non-array 防禦）；(c) 後端全 regression：`CqlArtifactBuilderTest` + `ExpressionTreeValidatorTest` + `EcqmCqlBuilderTest` + `EcqmExpressionTreeValidatorTest` + V56MigrationTest 合計 201/201 零回歸；(d) 前端 `ArithmeticElement.test.tsx` 全新重寫 13 tests（2-operand 預設、Add/Remove operand 行為、precedence parens 顯示、element/quantity preview、legacy 2-ary 向後相容讀+寫遷移、operator dropdown 7 個選項）；(e) 前端 `ArithmeticUnaryElement.test.tsx` PAT-162 baseline 7/7 regression 全綠；(f) `npx tsc --noEmit` + `npm run lint` clean。**未在本 PR — 留待後續 Phase**：型別感知 operator filtering（避免在 string 上選 `*` 等）、`Round(x, precision)` 雙參數變體、minimal-parens 算法（PAT-164）。**TFDA 追溯**：需求 #508 / 設計 #509 / 風險 #510 / 驗證 #511（安全性等級 B；ISO 14971 殘餘風險評為**中**，比 PAT-161/162 高一階 — 對 production DB 動土；建議 Flyway V56 上線前 DB snapshot + cut-over 視窗暫停寫流量）。 | backend/src/main/java/com/cqlplatform/service/authoring/ExpressionCqlEngine.java, backend/src/main/java/db/migration/V56__migrate_arithmetic_to_nary.java, backend/src/main/resources/db/rollback/rollback_V56__migrate_arithmetic_to_nary.sql, backend/src/test/java/com/cqlplatform/service/authoring/ExpressionCqlEngineTest.java, backend/src/test/java/db/migration/V56MigrationTest.java, frontend/src/components/authoring/base-elements/{ArithmeticElement,BaseElements}.tsx, frontend/src/components/authoring/base-elements/arithmeticEmit.ts, frontend/src/components/authoring/base-elements/__tests__/ArithmeticElement.test.tsx, frontend/src/locales/{en,zh-TW}/authoring.json |[`8719510`](../../commit/8719510) |
| PAT-162 | ✨ feat | 2026-05-15 | 前端（新 `ArithmeticUnaryElement.tsx` + shared `OperandField.tsx` 抽出 + `BaseElements` 註冊）+ 後端（`ExpressionCqlEngine` 加 `arithmeticUnary` case + `UNARY_FUNCTIONS` allow-list + `CqlArtifactBuilder` bucket + `TemplateService` 註冊）+ i18n | **Arithmetic Element 擴充 Phase 2 — 加入 6 個 unary functions（Abs/Ceiling/Floor/Negate/Round/Truncate）** — 全功能 Arithmetic 擴充計畫的第二階段（前接 PAT-161 binary 擴充、後接 PAT-163 N-ary、PAT-164 型別感知）。**設計選擇 — 為何新做 element type 而非擴充既有**：unary 只用一個 operand 與既有 2-ary 結構不同；CQL emit 是 `Fn(arg)` 函式呼叫不是 `a + b` 中綴；不同 allow-list；獨立可演進。**後端修補**：(1) `ExpressionCqlEngine` 加 `UNARY_FUNCTIONS = Set.of("Abs", "Ceiling", "Floor", "Negate", "Round", "Truncate")` 常數；(2) `buildExpression` 加 `case "arithmeticUnary"`：讀 `function` field → 若不在 allow-list fallback 到 `Abs`；走 `resolveArithmeticOperand(fields, "operand", ctx)` 重用 PAT-161 已驗證的 3-mode 邏輯；產出 `<Function>(<operand>)`；operand 無法解析時走 `null /* unresolved unary operand */` + `ctx.warn`；(3) `resolveArithmeticOperand` 微調 — `side="operand"` 走 `operand_id` (非 `operand_operand_id` 雙前綴難看)，二元 left/right 行為不變；(4) `CqlArtifactBuilder` 二分桶判斷加 `\|\| "arithmeticUnary".equals(beType)` 把 unary 歸到 arithmetic-like emit bucket（順序與既有 binary 一致）；(5) `TemplateService.BUILTIN_REFERENCE_TYPES` 加 `arithmeticUnary` — 讓 validator 認可。**前端修補**：(1) 抽出 `OperandField.tsx` 從 `ArithmeticElement.tsx` 為 shared module（含 `NUMERIC_LITERAL_RE` / `UCUM_UNIT_RE` / `quantityToCql()` / `OperandMode` 型別 export），兩個 element 共用；(2) 新建 `ArithmeticUnaryElement.tsx`（function 下拉 6 選項 alphabetical + 單一 OperandField + CQL preview `<Fn>(<operand>)`，視覺以 `info.main` 色區分 binary 的 `secondary.main`）；(3) `BaseElements.tsx` Add menu 加第三個選項「Unary Function」+ render 分支處理 `be.type === 'arithmeticUnary'`；(4) `BaseElements.tsx` 改用 sub-path icon imports（沿用 PAT-161 PR #501 修法，避免 vitest collection hang）；(5) i18n 加 `authoring.arithmeticUnary.*`（en + zh-TW 同步 5 個 key）+ `baseElements.unaryDefaultName`；(6) `BaseElement.fields[]` 新欄位：`function` / `operand_mode` / `operand_id` / `operand_literal` / `operand_literal_value` / `operand_literal_unit`，缺值自動 fallback 與 PAT-161 一致。**安全機制**（沿用 PAT-159/161 三層）：function name 後端 allow-list + fail-safe Abs；operand 重用 PAT-161 已驗證 regex（NUMERIC + UCUM 安全字元集）；ExpressionTreeValidator 走既有 dry-run translation。**驗證**：(a) 後端 `ExpressionCqlEngineTest` 加 12 個新測試（6 functions × 1 happy path、quantity operand、element ref operand、invalid function fallback、quantity 單引號注入擋下、missing operand + ctx.warn、default function when missing），74 → 86 全綠；(b) `CqlArtifactBuilderTest` + `ExpressionTreeValidatorTest` + `EcqmCqlBuilderTest` + `EcqmExpressionTreeValidatorTest` 合計 180/180 零回歸；(c) 前端新建 `ArithmeticUnaryElement.test.tsx` 7 tests（dropdown 6 functions、Abs/Floor/Round preview、quantity 單引號擋下、mode 切換、invalid function fallback）；(d) 前端 `ArithmeticElement.test.tsx` PAT-161 baseline 10/10 跑 regression（OperandField 抽出零回歸）；(e) `npx tsc --noEmit` clean。**未在本 PR — 留待後續 Phase**：N-ary 多 operand + 運算符優先順序（PAT-163）、型別感知 operator / function filtering 與 `Round(x, precision)` 雙參數變體（PAT-164）。**TFDA 追溯**：需求 #503 / 設計 #504 / 風險 #505 / 驗證 #506（安全性等級 B；ISO 14971 殘餘風險評為低，沿用 PAT-161 三層防護）。 | backend/src/main/java/com/cqlplatform/service/authoring/{ExpressionCqlEngine,CqlArtifactBuilder,TemplateService}.java, backend/src/test/java/com/cqlplatform/service/authoring/ExpressionCqlEngineTest.java, frontend/src/components/authoring/base-elements/{ArithmeticUnaryElement,OperandField,ArithmeticElement,BaseElements}.tsx, frontend/src/components/authoring/base-elements/__tests__/ArithmeticUnaryElement.test.tsx, frontend/src/locales/{en,zh-TW}/authoring.json |[`8cc9e99`](../../commit/8cc9e99) |
| PAT-161 | ✨ feat | 2026-05-14 | 前端（`ArithmeticElement.tsx` UI）+ 後端（`ExpressionCqlEngine` operator allow-list + Quantity operand）+ i18n | **Arithmetic Element 擴充 Phase 1 — 加入 `mod` / `div` / `^` 三個 binary operator + Quantity literal operand** — 全功能 Arithmetic 擴充計畫的第一階段（後續 PAT-162 unary functions、PAT-163 N-ary、PAT-164 型別感知）。原本 ArithmeticElement 只支援 4 個運算符（+, -, *, /）與 numeric literal / element reference 兩種 operand；BMI、eGFR、藥物分次劑量這類臨床公式無法表達。**前端修補**：(1) `OPERATORS` 常數從 4 項擴成 7 項（symbol 仍走 unicode display：`+` / `−` / `×` / `÷`；keyword 顯示原樣：`mod` / `div` / `^`），運算符欄位寬度從 80px 加到 96px 收容 keyword 標籤；(2) `OperandField` 從 2-mode 擴成 3-mode（`element` / `literal` / `quantity`），新增 `quantity` 模式 UI 為 numeric value text field（80px）+ 既有 `<UcumUnitField>`（Autocomplete + UCUM 標準清單）並排；(3) CQL preview 增 `quantityToCql()` 與 `UCUM_UNIT_RE` 驗證：value 必須匹配 `^-?\d+(\.\d+)?$`，unit 必須匹配 `^[A-Za-z0-9./*+\-()[\]{}%_]{1,32}$`（**禁止單引號** — 會關閉 CQL Quantity literal；**禁止空白與反斜線** — 防 CQL fragment 走私），任一驗證失敗該 operand 在 preview 顯示空白，整個 expression 不渲染；(4) i18n 加 `authoring.arithmetic.modeQuantity` / `quantityValue` / `quantityUnit` 三個 key（en + zh-TW 同步）；(5) `BaseElement.fields[]` 加 3 個新 key（`<side>_literal_value`、`<side>_literal_unit`、`<side>_mode='quantity'`）— 既有 artifact 缺新 key 自動 fallback 到 `element` mode 保持向後相容。**後端修補**：(1) `ExpressionCqlEngine` 加 `ARITHMETIC_OPERATORS` Set 常數（`{+, -, *, /, mod, div, ^}`）取代原本 4 個 `equals` 串列；任何不在 set 內的 operator 仍 fallback 到 `+`（保留 PAT-159 引入的 fail-safe 路徑）；(2) `resolveArithmeticOperand` 新增 `quantity` mode 分支：讀 `<side>_literal_value` 與 `<side>_literal_unit` 兩個 field，分別跑 `ARITHMETIC_NUMERIC_PATTERN` 與 `ARITHMETIC_UCUM_UNIT_PATTERN`（與前端 regex 一致），通過後組成 `<value> '<unit>'` 字串；任一驗證失敗 operand 為 null → 整體 expression 變成 `null /* unresolved arithmetic operands */` 並透過 `ctx.warn()` 浮到 UI。**安全機制**：operator 前後端雙重 allow-list；Quantity unit 嚴格 UCUM 安全字元集（單引號、反斜線、空白一律拒絕）；ExpressionTreeValidator 對 arithmeticExpression 走既有 dry-run translation 路徑，新 operator 與 quantity mode 自動覆蓋。**驗證**：(a) 後端 `ExpressionCqlEngineTest` 加 11 個新測試（mod / div / ^ 各一條 happy path；quantity literal happy path；4 條 quantity injection 測試覆蓋 單引號 / 反斜線 / 非數字 value / unit 含空白；regression：invalid operator fallback / 舊 artifact 無 mode 欄位仍走 element / BMI 公式 mixed mode），63 → 74 全綠；(b) `CqlArtifactBuilderTest` / `ExpressionTreeValidatorTest` / `EcqmCqlBuilderTest` / `EcqmExpressionTreeValidatorTest` 合計 94/94 零回歸；(c) 前端 `ArithmeticElement.test.tsx` 新建（9 tests：operator dropdown 7 選項、mod/^ preview、quantity mode 切換顯示 value + UCUM 欄位、quantity preview 含單引號 / 空白被擋、legacy artifact 無 mode 走 element）；(d) `npx tsc --noEmit` clean。**未在本 PR — 留待後續 Phase**：unary functions（Abs/Floor/Ceiling/Round/Truncate/Negate — PAT-162）、N-ary 多 operand 與運算符優先順序顯示（PAT-163）、型別感知 operator filtering（避免在 string 上選 `*` 等 — PAT-164）。**TFDA 追溯**：需求 #497 / 設計 #498 / 風險 #499 / 驗證 #500（安全性等級 B — 影響 CQL 計算結果正確性，可能對臨床決策建議造成非嚴重偏差；ISO 14971 殘餘風險評為低，三層防護 UI preview + 後端 allow-list + Validator dry-run）。 | backend/src/main/java/com/cqlplatform/service/authoring/ExpressionCqlEngine.java, backend/src/test/java/com/cqlplatform/service/authoring/ExpressionCqlEngineTest.java, frontend/src/components/authoring/base-elements/ArithmeticElement.tsx, frontend/src/components/authoring/base-elements/__tests__/ArithmeticElement.test.tsx, frontend/src/locales/{en,zh-TW}/authoring.json |[`cc50445`](../../commit/cc50445) |
| PAT-160 | 🔧 fix | 2026-05-13 | 後端（`GlobalExceptionHandler` 處理 client disconnect）+ Docker（nginx 拒絕 dotfile 路徑） | **GlobalExceptionHandler 二次爆 + nginx dotfile defense-in-depth** — VM log 看到 `Failure in @ExceptionHandler com.cqlplatform.exception.GlobalExceptionHandler#handleGenericException(Exception)` 連環 ERROR：client 端在 server 回 response 時斷線 → `ServletOutputStream` 抓到 `IOException: Broken pipe` → Spring 包成 `AsyncRequestNotUsableException` → fallthrough 到 `handleGenericException(Exception)` → 試圖寫 `ErrorResponse` JSON 到**已關閉的 stream** → `HttpMessageNotWritableException` → 被 ExceptionHandlerExceptionResolver 捕獲為「Failure in @ExceptionHandler」一次跑出 4 條疊起來的 stack（GlobalException + AsyncRequestNotUsable + LockOrRaise + ClientAbort），在 `/api/cql/libraries` 和 `/api/notifications/subscribe`（SSE）各觸發。**修補**：(1) 加 `@ExceptionHandler({AsyncRequestNotUsableException, ClientAbortException})` `handleClientDisconnect` 直接 `return void`（Spring 不寫任何 body），log level 從 ERROR 降到 INFO 並去 stacktrace；(2) `handleGenericException` 入口加 `isClientDisconnect(throwable)` 防禦——當 IOException 訊息含 `Broken pipe / Connection reset / Connection closed`（含 cause chain 8 層走訪）時 `return null`，避免 Tomcat 在拋 AsyncRequestNotUsable 前就把 broken-pipe IOException 直接送上來繞過 (1) 的命中。**Tier 3 順手收尾**：docker `nginx.conf` 加 `location ~ /\\.(git|svn|hg|bzr|env|aws|ssh)(/|$)` 永遠回 404（access_log off），擋外部 recon 掃描（log 看到過 `34.204.48.163` 從 AWS 掃 `/.git/config`，nginx 的 SPA fallback 回了 200 + index.html 1006B；雖然 `docker exec docker-frontend-1 ls -la /usr/share/nginx/html/.git` 確認容器內無 `.git`，但 200 OK 對掃描器看起來像 misconfiguration，改 404 切斷後續探測）。**驗證**：(a) 新增 4 個單元測試 — `handleClientDisconnect_asyncRequestNotUsable_shouldReturnVoidWithoutThrowing`、`handleGenericException_brokenPipeIOException_shouldReturnNullNotErrorBody`、`handleGenericException_brokenPipeCausedBy_shouldDetectViaCauseChain`（cause chain 走訪）、`handleGenericException_nonDisconnectIOException_stillReturns500`（防止 over-broadening — 普通 IOException 仍走 500 路徑）；(b) `GlobalExceptionHandlerTest` 20/20 全綠（從 16 → 20，零回歸）；(c) `mvn compile` clean，nginx config 改動本機 build verify。**Tier 2 — TWCore terminology fallback 經調查不是 bug**：`LocalTerminologyProvider.in()/expand()` 對 remote 例外不吞、會往上拋給 CQL engine 變 execution failure；`FhirTerminologyService.lookupCode()` 只被 `FhirController` user-facing endpoint 呼叫，不影響 measure evaluation 正確性；log 雜訊性質而非 silent 偏差，先不動。 | backend/src/main/java/com/cqlplatform/exception/GlobalExceptionHandler.java, backend/src/test/java/com/cqlplatform/exception/GlobalExceptionHandlerTest.java, docker/nginx.conf |[`cb57338`](../../commit/cb57338) |
| PAT-159 | 🔒 fix | 2026-05-13 | 後端（`CustomModifierCqlBuilder` 後端重建 + validator 放行 `custom_*`）+ 前端（`CustomModifierBuilder.tsx` rule 加 `fieldType`） | **自訂 modifier 後端重建 — 修 save-fail + CQL injection sink** — Production VM log 顯示 `artifact 10` (admin owner) 連續 15 次 PUT 都吃 400 `ValidationException` (`subpopulations: unknown modifier id 'custom_609073a1-...' / 'custom_d90f470d-...'`)。根因：PAT-127 把「自訂篩選」UI 上線後，使用者透過 `CustomModifierBuilder.tsx` 建 `id: 'custom_<uuid>'` + `cqlTemplate: '({expression}).where(<cqlWhere>)'` 的 modifier；但 PAT-088 (`ExpressionTreeValidator.java:255` / `EcqmExpressionTreeValidator.java:173`) 加 CQL injection prevention 時的 `modifierService.isValidModifierId(modId)` 白名單只認 `data/modifiers.json` + LEGACY 清單，**`custom_*` 一律拒絕** → 每次儲存 400。**並發現的安全漏洞**：`ExpressionCqlEngine.applyModifier:570` fallback 對 `cqlTemplate` 做 `replace("{expression}", expr)` 把 client 字串原樣 splice 進產出 CQL — 是 **未守護的 CQL injection sink**（PAT-088 hardening 只覆蓋 `values.*` whitelist，沒守 `cqlTemplate` 自身）。**修補（Option C — server-side rebuild，徹底切 client-trust）**：(1) 新增 `util/CqlEscapeUtil.java` 把 `escapeCqlString`/`escapeCqlIdentifier`/`stripNonAscii` 從 `ExpressionCqlEngine` 抽出為 static helpers（避免新 builder 跟 engine 環依賴）；engine 原方法 delegate 過去維持 API 相容。(2) 新增 `service/authoring/CustomModifierCqlBuilder.java` — `@Component`，13 個 operator 完整對應前端 `CustomModifierBuilder.tsx:534-602` 的 `buildRuleClause`：`equals/not_equals`（per fieldType 走 code `~`、decimal `=`、string `=`）、`gt/gte/lt/lte`、`starts_with/ends_with/contains` (`StartsWith/EndsWith/PositionOf`)、`in` (escapeIdentifier wrap)、`before/after` (ISO_DATE regex 後 `@<date>`)、`within_last` (`<num> <unit>`，DURATION_NUM + unit 白名單)、`is_null/is_not_null`。accessor 用 `IDENT_SEGMENT` regex 逐段檢查，不符就 wrap quoted identifier 並 escape 內部 `"`；MAX_DEPTH=10 防 stack overflow。(3) 新增 `CustomModifierBuildException` — domain runtime exception。(4) `ExpressionCqlEngine.applyModifier` 在 cqlLibFunc/cqlTemplate 取出後、`modifierAppliers` 查表前，先檢查 `modId.startsWith("custom_")` → 走 `customModifierCqlBuilder.build()` 並**完全忽略** client 給的 `cqlTemplate` 字串（exception 時降級為 warning + 跳過 modifier）。(5) `ExpressionTreeValidator.validateNode` / `EcqmExpressionTreeValidator.validate` 對 `custom_*` 跑 `customModifierCqlBuilder.validate()` (dry-run build)，`CustomModifierBuildException.message` 收集成 error 列表（一樣回 400 但訊息變成「invalid — <reason>」）。(6) 前端 `CustomModifierBuilder.tsx` — `ModifierRule` 加 `fieldType?: string`，欄位選擇時把 `availableFields.find().type` 寫入；後端用這個區分 `equals` 的 code (`~`) / decimal / string 三條路徑（沒 fieldType 走 string，向後保守）。**TFDA 追溯**：需求 #490 / 設計 #491 / 風險 #492 / 驗證 #493（安全性等級 B — 影響 PHI 機密性 + 臨床評估正確性；ISO 14971 H1 CQL injection / H2 使用者操作受阻）。**驗證**：(a) 新增單元測試 `CustomModifierCqlBuilderTest` 24 個（12 happy + 9 rejection + 3 escape — 鎖 `O'Brien` → `O\\'Brien`、`code.weird name` wrap → `code."weird name"`、`code.bad"name` → `code."bad\\"name"`、12-deep nesting → exception）；(b) 全套件 1500/1500 zero regression（時間 4:12，比上次無差異）；(c) frontend `tsc --noEmit` 0 errors。**未在本 PR**：production DB 既有 artifact 都不曾有成功儲存的 custom modifier（每次都被擋），所以無 legacy 資料相容問題；engine line 570 對非 custom_* 未知 cqlTemplate 的 fallback 字串 splice 路徑仍保留（其他 modifier kind 都跑 known applier，validator 對非 custom_* 未知 id 也會 400），不在本次處理。 | backend/src/main/java/com/cqlplatform/service/authoring/{CustomModifierCqlBuilder,CustomModifierBuildException,ExpressionCqlEngine,ExpressionTreeValidator}.java, backend/src/main/java/com/cqlplatform/service/ecqm/EcqmExpressionTreeValidator.java, backend/src/main/java/com/cqlplatform/util/CqlEscapeUtil.java, backend/src/test/java/com/cqlplatform/service/authoring/CustomModifierCqlBuilderTest.java, frontend/src/components/authoring/builder/CustomModifierBuilder.tsx |[`e0d8a27`](../../commit/e0d8a27) |
| PAT-158 | 🐛 fix | 2026-05-10 | 後端（DataRequirementExtractor 解 QueryLetRef）+ 前端（DataRequirementsTab filter-type chip 動態化） | **資料需求面板兩個 bug 同時修補** — production 上 HbA1c (id=3) 的 MedicationRequest 顯示「1 個需求」但只看到 authoredOn 的 date filter，medication 的 ATC A10\* code filter 完全缺失。動態檢查發現 production CQL 用了 CLAUDE.md 推薦的 choice type let 慣例 `let medCC: MR.medication as FHIR.CodeableConcept; ... from medCC.coding Coding ...`，ELM 翻成 `Property("coding") → QueryLetRef("medCC")` 鏈；`DataRequirementExtractor.extractCodePathFromPropertyChain` 只跟 `Property → Property → AliasRef`，碰到 `QueryLetRef` chain 就斷 → `codeProperty = null` → `deduplicateRequirements` line 1282 因 `codeProperty == null` 把整個 code filter 丟掉（即使 `extractCodeSystemFromInnerWhere` 已正確抓到 system URL + StartsWith prefix）。**Backend 修補**：(1) `handleQuery` 多收一個 `Map<String, JsonNode> letBindings`（從 outer Query 的 `let[]` 收集 identifier → expression），(2) 透過 `enhanceFromWhere` → `walkWhereClause` → `handleExistsPattern` → `extractCodePathFromPropertyChain` 一路傳遞，(3) chain walk 在 bottom 是 `QueryLetRef` 時用 letBindings 查 expression，呼叫既有 `unwrapToProperty` 解 `As/Convert/FunctionRef` 包裝後遞迴重走 chain。`MR.medication.coding`（直寫）+ `from medCC.coding`（let）兩種寫法現在都能解 codeProperty=`medication`。**Frontend 修補**：`DataRequirementsTab.tsx:230` 的「篩選類型」chip 過去 hardcode `t('dataRequirements.filterTypes.code')` —「代碼」，不管 codeFilter 裡裝的是 ValueSet（`[Condition: "Pneumonia"]`）、CodeSystem + 純 prefix（`StartsWith` 範圍比對）還是真的 code 列表都顯示「代碼」。改為依 codeFilter 內容動態切換：`cf.valueSet` → 「集值」(secondary purple)、純 prefix（`!hasCodes && hasPrefixes`）→「代碼前綴」(warning orange)、其他 → 「代碼」(info blue)。**i18n 新增** `dataRequirements.filterTypes.codePrefix`（en + zh-TW）。**驗證**：(a) 新增 unit test `extract_letPattern_resolvesQueryLetRefToOuterAliasProperty` 用真實 ELM 結構（從 production CQL translate API dump 出來再縮小）鎖回正確 `path="medication"` + `codeSystemUrl` + `codePrefixes=["A10"]`；(b) `DataRequirementExtractorTest` 34/34 全綠（從 33 → 34，零回歸）；(c) end-to-end 在本機 docker stack 把 measure id=3 CQL 換成 production 的 let 寫法，data-requirements API 回傳 `codeFilter: [{path: "medication", codeSystemUrl: "http://www.whocc.no/atc", codePrefixes: ["A10"]}]`（修補前 `codeFilter: null`）；(d) `tsc --noEmit` 0 errors。**未在本 PR**：`extractPropertyPath`（`InValueSet` / `handleCodeRefComparison` 走的單層 path）也可能遇到類似 QueryLetRef pattern（`let codeCC: C.code as ...; where codeCC in "VS"`），這次先不擴大；frontend `dangerouslySetInnerHTML` 風格的單元測試補充屬獨立 P3。TFDA 追溯：需求 #PENDING（安全性等級 A — UI 顯示正確性 + 資料需求解析正確性；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | backend/src/main/java/com/cqlplatform/service/cql/DataRequirementExtractor.java, backend/src/test/java/com/cqlplatform/service/cql/DataRequirementExtractorTest.java, frontend/src/components/measure/DataRequirementsTab.tsx, frontend/src/locales/{en,zh-TW}/measures.json |[`a69de64`](../../commit/a69de64) |
| PAT-157 | 🔒 fix | 2026-05-08 | 後端 + Docker config + 測試（內部滲透測試 follow-up — 修 4 Critical / 1 High / 4 Medium / 1 Low） | **內部滲透測試修補批次** — 對 2026-05-08 滲透測試報告 (`docs/security/PEN_TEST_REPORT_2026-05-08.md`) 列出的發現批次修補；admin/admin 預設帳號（CRITICAL-1）依使用者要求暫不動，其餘**皆修**或排到獨立 follow-up PR。**(1) CRITICAL-2 / -3 / HIGH-2 — 輪替 `docker/.env` secrets**：`JWT_SECRET` / `ENCRYPTION_KEY` / `POSTGRES_PASSWORD` / `DB_PASSWORD` / `GF_SECURITY_ADMIN_PASSWORD` / `METRICS_SCRAPE_PASSWORD` 全部用 `secrets.token_bytes()` urlsafe-b64 重新產生。本機 dev DB 整 volume 砍掉重建（demo data 可重生），舊 ENCRYPTION_KEY 加密的 PHI 欄位本機已不重要；production VM 須獨立輪替 + 設計 envelope encryption migration（不在本 PR 範圍）。動態驗證：用洩漏的舊 JWT_SECRET 偽造 admin token 打 `/api/admin/users` 由 200 改為 401。**(2) CRITICAL-4 — `InputValidator.computeIsLocalDevelopment()` 拿掉 `docker` 認定**：`profile.contains("dev") || profile.contains("test")` only。Production 部署用 `SPRING_PROFILES_ACTIVE=docker` 但不再被 SSRF allow-list 視為 local dev，loopback / link-local / site-local IP 在 production 一律封死。動態驗證：`?fhirServer=http://169.254.169.254/`（雲端 metadata endpoint）由 503（reachable）改為 400 `Invalid FHIR server URL`。**(3) MEDIUM-3 — 自助註冊 feature flag 預設關閉**：`auth.self-registration.enabled` 加到 `application.yml`，預設 `${AUTH_SELF_REGISTRATION_ENABLED:false}`。醫療軟體 / TFDA 規管下符合 IEC 62304 access control 預設 admin 控管帳號。`AuthController.register` 在 flag false 時無條件回 200 + 統一「Registration request received」訊息（不洩漏 endpoint 是否啟用）。**(4) MEDIUM-4 — register endpoint 統一回應**：username 已存在從 400 + `Username already exists` 改為 200 + 同一 pending 訊息（與 flag-off 路徑外觀完全相同），CWE-200 user enumeration 補完（forgot-password 已防 / login 已防 / register 補完）。**(5) MEDIUM-5 — H2 driver 改 test scope**：原 `<scope>runtime</scope>` 會把 H2 打進 production JAR（H2 console 史上有 CVE-2022-23221 RCE）；production 用 PostgreSQL，H2 僅測試需要。**(6) LOW-1 — nginx CSP 收緊**：移除 `'unsafe-inline'` / `'unsafe-eval'` from script-src，移除 `cdn.jsdelivr.net`（grep 確認 SPA 完全不用）；保留 `'unsafe-inline'` 給 style-src（MUI / Emotion runtime style tag）+ `worker-src 'self' blob:`（Monaco editor worker）。**(7) LOW-2 — 密碼複雜度 `@Pattern`**：4 個 DTO（`RegisterRequest` / `ResetPasswordRequest` / `ChangePasswordRequest` / `AdminCreateUserRequest`）的 password 欄位加 regex `^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$`，原本 `@Size(8,100)` 通過 `12345678` 等弱密碼。動態驗證：`alllowercase1` 從 200 改為 400 + 明確 `details: ["password: must contain at least one lowercase letter, one uppercase letter, and one digit"]`。**測試修補連動**：`AuthControllerTest` / `AuthIntegrationTest` 加類別層 `@TestPropertySource(properties = "auth.self-registration.enabled=true")`；3 個 test 檔的所有 `password123` literal 改 `Password123`（滿足新 regex）；`register_existingUser_shouldReturn400` 與 `duplicateRegistration_shouldFail` 改名 + 改斷言 expect 200 + uniform message。**已交付動態驗證（重啟 stack 後）**：admin/admin 仍可登入（per request kept）、舊 secret 偽造 token 401、169.254.169.254 SSRF 400、register flag-off 200 uniform、weak password 400、strong password 一樣 200 uniform（無 leak）。**未在本 PR — 排到獨立 follow-up PR**：HIGH-1 DNS rebinding（需設計 IP-locking outbound interceptor 驗 HAPI 8.6.6 相容性）、MEDIUM-1 DEPARTMENT_ADMIN 權限細粒度（須 product 決策 dept-scoped user mgmt）、MEDIUM-2 JWT in localStorage（架構級 BFF 改造）、MEDIUM-6 subscription callback HMAC 驗證（需 DB schema 加 per-subscription secret + HMAC verify middleware）、CRITICAL-1 預設 admin/admin（per user request 暫保留）。**TFDA 追溯**：需求 #PENDING（安全性等級 C — 影響 PHI 機密性 + 認證機制健全；ISO 14971 風險分析 + IEC 62304:5.2 access control + IEC 62304:5.3 cryptographic key management） | docker/.env, docker/nginx.conf, backend/pom.xml, backend/src/main/resources/application.yml, backend/src/main/java/com/cqlplatform/security/InputValidator.java, backend/src/main/java/com/cqlplatform/controller/AuthController.java, backend/src/main/java/com/cqlplatform/model/auth/{RegisterRequest,ResetPasswordRequest,ChangePasswordRequest,AdminCreateUserRequest}.java, backend/src/test/java/com/cqlplatform/{controller/{AuthControllerTest,AdminControllerTest},integration/AuthIntegrationTest}.java, docs/security/PEN_TEST_REPORT_2026-05-08.md |[`0ed38e9`](../../commit/0ed38e9) |
| PAT-156 | 🐛 fix | 2026-05-07 | 後端（multi-group + 每 group stratifier 評估）| **multi-group + 每 group stratifier 修補** — BUG #474 follow-up：multi-group eval 已修，但 stratifier 評估還用舊路徑——`StratifierEvaluator.evaluatePatientStratifiers` 內 `populationEvaluator.extractPopulationCount(results, popName)` 用 unsuffixed canonical 名查找，但 multi-group CQL 的 results 只有 suffixed 名 (`Initial Population 1` / `Initial Population 2`)，所以 multi-group + 任何 stratifier 的 strata 計數全為 0；此外 `stratificationData` 是單一 flat map，多 group 共用同 stratifier id（兩 group 都叫 `gender`）會 cross-contaminate。**修補**：(1) 加 `evaluatePatientStratifiers` 新 overload 接受 (raw, canonical, data) — raw 用於 stratifier expression lookup（suffixed name），canonical 用於 population count lookup；舊 single-arg 版本 delegate 到 new 版本當 raw=canonical；(2) `StratifierEvaluator.buildExpressionMap` 在 PopulationEvaluator 已 public（PAT-477 改的），這邊重用；(3) `MeasureEvaluationService.AggregationState.stratificationData` 從 `Map<String, Map<String, Map<String, Integer>>>` 改 `Map<String, Map<String, Map<String, Map<String, Integer>>>>` per groupId 一個 bucket；(4) per-patient 迴圈 per-group 呼叫 `evaluatePatientStratifiers(group.stratifiers, raw, canonical, perGroupStratData[gid])`；(5) `buildMultiGroupResult` per-group attach `stratifiers` 到 `GroupResult`；(6) 單 group builder 新加 `primaryGroupStratData(state)` helper 讀 first group's stratification data；(7) 加 legacy fallback：當 `groupDefs.isEmpty()` (ad-hoc CQL execution 走的路徑) 時用舊 flat global stratifier 處理。**新 scenario #27** — multi-group + 每 group stratifier 鎖回正確行為：working-age group 5 patients (M=3 / F=2)、stratifier-male true=3males/Numer=2/score=100、false=2females/Numer=1/score=50；seniors group 2 patients (M=1 / F=1)、stratifier-male true=1male/Numer=1/score=100、false=1female/Numer=0/score=0。**`assert.sh` 加 per-group `stratifiers[]` 知識** — `expected.json.groups[].stratifiers[]` 跟 top-level `stratifiers[]` 同 schema 但 scoped 到單一 group；用 jq 對 `groups[].stratifiers[]` 路徑查找。**驗證**：scenario 27 24 個斷言全綠 (4 group populations + 4 scores + 16 stratifier counts/scores)；scenario 25 multi-group 仍綠；scenario 19 single-group + stratifier 仍綠（含 SDE Sex / SDE Payer presence + budget 4585ms < 8000ms）；71 unit tests zero regression。**未在本 PR**：cohort / CV / ratio 的 multi-group 評估還沒做，獨立 PAT-157 follow-up；SDE / observation 在 multi-group 也還沒做（SDE 通常在 library top-level 沒 suffix，已 work；observation 留 follow-up）。TFDA 追溯：BUG-474 follow-up（安全性等級 A — multi-group + stratifier 影響臨床評估正確性） | backend/src/main/java/com/cqlplatform/service/measure/{MeasureEvaluationService,StratifierEvaluator}.java, scripts/smoke/scenarios/27-multi-group-stratifier/, scripts/smoke/lib/assert.sh, scripts/smoke/README.md |[`37da463`](../../commit/37da463) |
| PAT-155 | 🧪 test | 2026-05-07 | 整合（smoke 低優先補強：concurrent evaluation scenario + seed-fhir 5xx retry）| **smoke harness 低優先補強批次** — 完成兩項；其餘三項（workflow lifecycle / token version / scenario number gap）評估後不做（單元測試已覆蓋 / 與 60-120s smoke 預算衝突 / 已隨 PAT-152 補上）。**(1) Scenario #26 — 5 個並行 evaluation 一致性檢查**：建立新 `lib/evaluate-concurrent.sh` helper 用 background subprocess + `wait` 觸發 N 個 `evaluate.sh`，捕獲每個 response 到獨立 tmp file 後比對全部 score+populations 必須完全一致（容差為 0，並行 run 對同一 FHIR snapshot 必須產生相同數字）。`run.sh` 偵測 `expected.json.concurrentEvaluations` 旗標 ≥ 2 時切換到 concurrent helper。Scenario 26 用 5 並行 + score=2/3=66.67% 鎖：(a) HikariCP 連線池不可飢餓；(b) `cqlExecutionExecutor` thread pool 不可洩漏；(c) HAPI client connection pool 並行下不能 race；(d) 並行 `MeasureReportService.saveReport` insert 不能 collide。實測 7797ms（5 並行）含一致性比對 vs 單跑 ~3s — 留 4-8x headroom 在 15000ms budget。**(2) `lib/seed-fhir.sh` 加 transient 5xx retry**：HAPI 偶爾在 startup 後第一次 transaction 仍回 502/503（async cache warming），即使 `wait-health.sh` 已看 `/metadata` 200 過。改為 3 次嘗試 + linear backoff (1s, 3s)，總 retry budget 4s 遠低於 60s scenario 預算。**未做**（評估後跳過）：(11) Workflow lifecycle (submit-for-review / approve / retire) — 純 status field 變更、unit test 已覆蓋、整合層回報率低；(12) Token version 撤銷 — 30s 生效需要 sleep 30 與 smoke runtime budget 衝突；(13) Scenario 編號補洞 — 已在 PAT-152 補上 (19)；(14) reset-fhir.sh `--keep` 重跑 — 重新評估後發現 reset 在每個 scenario 啟動時跑、不是真問題。**驗證** — Scenario 26 全 5 個 assertion + budget 全綠（5 並行 score 完全相同 / IP=4 / Denom=3 / Numer=2 / 7797ms < 15000ms）。**未在本 PR**：concurrent eval 也對 stratifier / observation / multi-group 適用但目前未測（單一 proportion 已驗證並行 contract，其他 scoring type 加 concurrentEvaluations 旗標是 1 行改動）；EHR Integration smoke 仍待獨立 PR（需 mock SMART JWKS server）。TFDA 追溯：需求 #PENDING（安全性等級 A — 整合測試 coverage 補強 + 並行 evaluation contract lock + transient HAPI retry 抗 flake） | scripts/smoke/scenarios/26-concurrent-evaluation/, scripts/smoke/lib/{evaluate-concurrent.sh,seed-fhir.sh}, scripts/smoke/run.sh, scripts/smoke/README.md |[`dec0202`](../../commit/dec0202) |
| BUG-474 | 🐛 fix | 2026-05-07 | 後端（multi-population-group eCQM evaluation 架構修補）| **multi-group eCQM evaluation 修補** — `MeasureEvaluationService.aggregatePerPatient` 之前用單一 `populationCounts` map 餵所有 patients，且 `PopulationEvaluator.aggregatePatientResults` 硬寫查找 unsuffixed 名（`"Initial Population"` 等）。multi-group 的 CQL 產生 suffixed 名（`"Initial Population 1"` / `"Initial Population 2"`），所以查找永遠失敗 → counts 全為 0。`buildMultiGroupResult` 雖已寫好但 fed 不到資料。**修補**：(1) `AggregationState.populationCounts` 從 `Map<String, Integer>` 改為 `Map<String, Map<String, Integer>>` per groupId；(2) aggregation loop 對 measure 的每個 group 用 `PopulationEvaluator.buildExpressionMap` 把 raw results 的 suffixed 名映射回 canonical 名，再呼叫既有 `aggregatePatientResults`；(3) 把 `buildExpressionMap` 從 private 改 public（同 class 已有）；(4) `buildResult` / `buildCohortResult` / `buildCvResult` 改透過 `primaryGroupCounts(state)` helper 取 first group's counts（向後相容單 group）；(5) `buildMultiGroupResult` 改用 `state.populationCounts.get(groupId)` 加 `canonicalPopulationName` helper 查 canonical key。**重新加 scenario 25** (multi-population-group) 鎖回正確行為：`working-age` group IP=5/Denom=4/Numer=3/score=75.0、`seniors` group IP=2/Denom=2/Numer=1/score=50.0。**驗證**：scenario 25 全 8 個 assertion + budget pass；scenario 01 single-group regression（含 idempotent + provenance）全綠。`mvn test -Dtest=MeasureEvaluationServiceTest,PopulationEvaluatorTest,MeasureScoreCalculatorTest` 71 個 unit test 零回歸。**未在本 PR**：multi-group + stratifiers / SDE / observations 的端到端（純 proportion multi-group 已 land；ratio / CV / cohort multi-group + per-group stratifier 屬獨立 PR）。**影響**：production 用 multi-population-group eCQM 的 measures 在修補前 evaluation 結果都是錯的（只回單一 group 且 populations 全 0）。CMS 2026 v9.0 §4.3 明確支援 multi-group。TFDA 追溯：BUG #474（安全性等級 A — 影響臨床評估正確性）；scenario 25 之前在 PAT-154 揪出此 gap 但獨立追蹤 | backend/src/main/java/com/cqlplatform/service/measure/MeasureEvaluationService.java, backend/src/main/java/com/cqlplatform/service/measure/PopulationEvaluator.java, scripts/smoke/scenarios/25-multi-population-group/, scripts/smoke/README.md | |
| PAT-154 | 🧪 test | 2026-05-07 | 整合（smoke 中優先補強：performance budget + empty-IP 邊界 scenario + 揪出 multi-group 架構 gap） | **smoke harness 中優先補強批次** — 完成 3 項中優先中的 2 項，第三項撰寫過程揪出 backend 架構級 gap 獨立追蹤。**(1) Per-scenario performance budget** — `expected.json` 加 `maxEvaluationTimeMs` flag；`run.sh` 在 `evaluate.sh` 周圍 wall-clock 計時並 export `EVAL_ELAPSED_MS`；`assert.sh` 比對。01/19/21/22/23/24 全部加 8000ms budget（典型 evaluation 1-2s，留 4-8x 上限抓 N+1 query / bulk-fetch 退化）。本機驗證實測：01=2917ms、24=2502ms、25 (移除前)=2745ms 全部在預算內。**(2) Scenario #24 — empty IP / null score** — proportion measure IP 條件 `>=200` 無人通過，3 個正常病人但全被 IP 排除。鎖 `MeasureScoreCalculator.calculateProportionScore` 第 68-70 行「denom=0 → null」路徑，加 `expectScoreNull: true` assertion knob 補。Catches NaN / 0.0 / 5xx 的 silent 退化會 corrupt dashboard 聚合。**(3) Scenario #25 — multi-population-group 揪出架構 gap**：撰寫過程觸發後**發現 `MeasureEvaluationService.buildProportionResult` 第 480 行硬寫 `.groups(List.of(groupResult))`，無論 measure 定義幾個 group 都只回 1 個**；CQL 建構層 (EcqmCqlBuilder) 已正確處理 multi-group define name suffix，FHIR Measure resource 也對，但 evaluation pipeline 沒走 per-group 路徑（`PopulationEvaluator.aggregatePatientResults` 也硬寫查找 `"Initial Population"` 等無 suffix 名）。架構級修補（per-group 迭代 + per-group score + `List<GroupResult>` 收集）超出 smoke 補強 PR 範圍，獨立 BUG #474 追蹤。Scenario 25 從本 PR 移除，待 multi-group eval 支援上線後加回；assert.sh 的 `groups[]` knob 保留。**`assert.sh` 新增 3 個 knobs**：`expectScoreNull` / `groups[]` (預留) / `maxEvaluationTimeMs`。**測試** — 3/3 嘗試的 scenario 全綠：01 (idempotent + provenance + budget 2917ms)、24 (IP=0 / score=null / budget 2502ms)；scenario 25 暴露架構 gap 獨立追蹤。**未在本 PR**：multi-group eval 架構修補（BUG #474）；EHR Integration smoke（複雜的 SMART JWKS mock，獨立 PR）。TFDA 追溯：需求 #475（安全性等級 A — 整合測試 coverage 補強 + empty-IP 邊界 + 揪出 multi-group 架構 gap） | scripts/smoke/scenarios/24-empty-ip-null-score/, scripts/smoke/lib/assert.sh, scripts/smoke/run.sh, scripts/smoke/README.md, scripts/smoke/scenarios/{01-proportion-age-cohort,19-proportion-stratifier-sde,21-proportion-with-exclusions,22-external-cql-library,23-export-csv-injection}/expected.json |[`2468e70`](../../commit/2468e70) |
| PAT-153 | 🔒 fix | 2026-05-07 | 後端（pom.xml CVE override）| **postgresql JDBC CVE-2026-42198 修補** — Trivy security scan 在 PR #471 旗了 `org.postgresql:postgresql 42.7.10`（HIGH，client-side DoS in pgjdbc）。Spring Boot 3.5.12 BOM 帶來 42.7.10，沿既有 CVE override 模式（tomcat / thymeleaf / spring-security）加 `<postgresql.version>42.7.11</postgresql.version>`。`mvn dependency:tree -Dincludes="org.postgresql:postgresql"` 已驗證解析為 42.7.11。風險低（patch 級升級 42.7.x 線內、僅修 DoS bug、無 API 變動）；TFDA security review 看 Trivy output，清掉 HIGH CVE 維持 security posture 乾淨。TFDA 追溯：需求 #472（安全性等級 A — CVE 修補） | backend/pom.xml |[`6af3abf`](../../commit/6af3abf) |
| PAT-152 | 🧪 test | 2026-05-06 | 後端 + 整合（smoke 高優先補強：新增 4 個 scenario 19/21/22/23 + idempotent assertion + 修 4 個 backend pre-existing bug） | **smoke harness 高優先補強批次** — 對 `scripts/smoke/` 增 4 個 scenario 鎖住先前零覆蓋的 prod path，過程中**新 scenario 立刻揪出 4 個 backend pre-existing bug 並全部修復**。**Scenario 19 (proportion + Stratifier + SDE)** — 鎖 `StratifierEvaluator` 端到端 + 標準 SDE define 生成。7 個病人混合 gender，per-group `gender` stratifier 產出兩個 strata (`true` 男性 100%/`false` 女性 33.33%)。**Scenario 21 (6-population proportion)** — 鎖 IP/Denom/DenomExcl/Numer/NumerExcl 完整 6-pop pipeline + 三值邏輯 regression family。**Scenario 22 (external CQL library include)** — 鎖 `DatabaseLibrarySourceProvider` 整合路徑 (BUG-107 family)。先 POST `/api/cql/libraries` 上傳 lib，然後 measure tree 用 `externalCqlRef` 指向 `SmokeExternalUtil.IsAdultAtPeriodEnd` / `IsSeniorAtPeriodEnd`。**Scenario 23 (CSV export injection)** — 鎖 `CsvUtils.escapeCsv` 端到端。Measure 名 / desc / group desc / group ID 全部以 formula trigger 字元開頭 (`=`, `+`, `-`, `@`)，evaluate 後 export 為 CSV，斷言無一行以 trigger 字元開頭。**新增 `assert.sh` 5 個 assertion knobs**：`stratifiers[]` (per-stratum populations + score)、`supplementalDataDefinesPresent` (CQL grep)、`uploadLibrary` (run.sh 偵測)、`checkCsvExport` (format + forbiddenLineStarts)、`idempotent: true` (re-run 比對相同分數 + populations)。**新增 `lib/upload-library.sh`** — 包 `/api/cql/libraries` POST 邏輯，409 視為 ok（`--keep` 重跑場景）。Scenario 01 加 `idempotent: true` 鎖住 cache pollution / `MeasureReportBackfillService` 重複插入 regression。**4 個 backend pre-existing bug 修復**：(1) `FhirDataProviderService.bulkFetchAllPatients` for-loop 跳過 `Patient` — Patient 在 line 161-189 用 batch GET 獨立 fetch，但 for-loop 仍試 `Patient?subject=...` 觸發 HAPI-0524。Smoke 01 從一開始就被這 bug 擋住。(2) `FhirDataProviderService.PATIENT_BASED_RESOURCES` 加 `Coverage` — Coverage 用 `patient` search param 不是 `subject`，遇到 SDE Payer 的 `[Coverage: ...]` retrieve 會爆。(3) `EcqmCqlBuilder.collectAllDeclarations` 把標準 SDE 的 OID 提早加入 `valueSets`（之前在 line 264 加 OID 太晚 — `escapedValueSets` snapshot 已建好，產出 CQL 缺 `valueset "..."` 宣告，translator 報 `Could not resolve identifier urn:oid:...`）。(4) `EcqmConstants.DENOMINATOR_EXCLUSION` / `NUMERATOR_EXCLUSION` / `DENOMINATOR_EXCEPTION` 改用複數形（CQL define 命名要對齊 `PopulationEvaluator` 的字串查找 `"Denominator Exclusions"` 等 — 原 singular 形產出空 exclusion populations 而 score 無聲偏差）。**測試** — 5/5 scenarios 全綠：scenario 01 (60.0%, idempotent, provenance) + 19 (60.0% + 12 stratifier assertions) + 21 (40.0% + 5-pop) + 22 (66.67%, external lib resolved) + 23 (50.0% + CSV 13 行無一行 formula prefix)。Scenario 19 觸發 4 個 bug 中的 3 個（patient skip, Coverage, SDE OID timing）；scenario 21 觸發第 4 個（exclusion 命名）。**未在本 PR**：剩下中優先項目（multi-group, EHR integration smoke, perf budget, empty IP edge）留下次批次；smoke run.sh 一次只能單 glob 的小限制留下次。TFDA 追溯：需求 #470（安全性等級 A — 整合測試 coverage 補強 + 4 個臨床評估路徑 bug 修補；影響範圍：所有 SDE Payer measures / 6-pop measures / external library measures 在修補前評估結果偏差，已用新 smoke 鎖回正確行為） | scripts/smoke/scenarios/{19-proportion-stratifier-sde,21-proportion-with-exclusions,22-external-cql-library,23-export-csv-injection}/, scripts/smoke/lib/{assert,upload-library}.sh, scripts/smoke/run.sh, scripts/smoke/README.md, scripts/smoke/scenarios/01-proportion-age-cohort/expected.json, backend/src/main/java/com/cqlplatform/service/fhir/FhirDataProviderService.java, backend/src/main/java/com/cqlplatform/service/ecqm/EcqmCqlBuilder.java, backend/src/main/java/com/cqlplatform/model/ecqm/EcqmConstants.java |[`d7fd35d`](../../commit/d7fd35d) |
| PAT-151 | 🐛 fix | 2026-05-04 | 前端（components/dashboard 模組 deep review P1+P2 修補：抽 dashboardFormat helper + ThresholdAlertPanel/QualityReportPanel scoringType-aware formatting + 截斷提示 + stable key + ScoreTrendChart useMemo + 補 24 cases test） | **dashboard module review-driven 修復批次** — 對 `frontend/src/components/dashboard/` (7 檔、~797 LOC、原 1 test) 直接讀完不派 agent，識別 2 P1 + 4 P2 並全部修復。**P1 都是 PAT-124 漏修的延伸 — `ScoreTrendChart` 在 PAT-124 已正確處理 scoring families（proportion / continuousVariable / cohort），但 `ThresholdAlertPanel` 與 `QualityReportPanel` 仍寫死 `%`**。**P1 #1 抽共用 `dashboardFormat.ts` helper + `ThresholdAlertPanel` 修 `%` 寫死**：原 `ThresholdAlertPanel.tsx:52` `\`${alert.actualScore?.toFixed(1)}% ${op} ${alert.thresholdValue}%\`` — HbA1c < 7.0 mmol/L 的 continuous-variable threshold 顯示 `5.6% < 7%`（錯）；cohort threshold（病人數）也誤標 `%`；`actualScore` null 時渲染 `undefined.0%`。新建 `frontend/src/utils/dashboardFormat.ts` (~70 LOC) export `formatScoreValue` / `formatThresholdValue`，內部呼叫 `classifyScoring` (PAT-124) 走三族分支：proportion → `${value.toFixed(1)}%`、continuousVariable → `${formatRaw(value)} ${unit}`（formatRaw 沿用 ScoreTrendChart magnitude-aware 邏輯：≥100 → 0 decimals、≥10 → 1、其他 → 2）、cohort → `${Math.round(value)} ${unit}`。null/undefined/NaN 回 `naLabel`（預設 `-`）。`ThresholdAlert` type 加 `scoringType?: string` + `unit?: string`。`ThresholdAlertPanel` 改用 helper。**P1 #2 `QualityReportPanel.tsx` 用同一 helper**：line 84 `${ms.targetThreshold}%` 同 P1 #1 bug。改 `formatThresholdValue`；移除 local `formatScore` helper（被 `formatScoreValue` 取代且更完整）。**P2 #1 截斷提示**：`MEASURE_TABLE_PREVIEW_LIMIT = 10` constant + footer Typography「Showing N of M measures」（i18n key `dashboard.tablePreviewTruncated`，en + zh-TW 同步），`measureScores.length > LIMIT` 才顯示。**P2 #2 stable key**：原 `key={i}` → `${alert.measureId}-${alert.thresholdType}` 同 measure 多種 threshold 也能區分。**P2 #3 `ScoreTrendChart` `targetLines` / `warningLines` 加 useMemo（line 86-87）**：與其他 derived state 一致，避免 ReferenceLine map 不必要重建。**P2 #4 補 24 cases test**：(1) `scoringFamily.test.ts` 5 cases；(2) `dashboardFormat.test.ts` 9 cases (含 PAT-151 regression: continuous-variable 不加 % / cohort 整數)；(3) `ThresholdAlertPanel.test.tsx` 6 cases (含 3 個 PAT-151 regression)；(4) `QualityReportPanel.test.tsx` 7 cases (含 PAT-151 regression: 截斷 footer + threshold 不再 7%)。**測試** — dashboard module 從 1 → 18 cases + utils 多 9 cases = 27 cases；既有 `ScoreTrendChart` test 零回歸；`tsc --noEmit` 零錯。**未在本 PR**：`QualityReportPanel.tsx:53` `report.averageScore` 在混合 scoring type 時意義不明確（屬 backend 聚合語意問題）；`MeasureScoreSummary` 加 `unit` 欄位屬 minor enhancement。TFDA 追溯：需求 #467（安全性等級 A — UI 顯示正確性 + 共用 formatter 規範化 + 截斷 UX + 補 zero-coverage test；直接改善 dashboard 使用者看到的數值正確性 — continuous-variable measure 不會再誤標單位） | frontend/src/utils/dashboardFormat.ts（新）, frontend/src/types/index.ts, frontend/src/components/dashboard/{ThresholdAlertPanel,QualityReportPanel,ScoreTrendChart}.tsx, frontend/src/components/dashboard/\_\_tests\_\_/{scoringFamily,ThresholdAlertPanel,QualityReportPanel}.test.{ts,tsx}, frontend/src/utils/\_\_tests\_\_/dashboardFormat.test.ts, frontend/src/locales/{en,zh-TW}/measures.json |[`c24ca45`](../../commit/c24ca45) |
| PAT-150 | 🐛 fix | 2026-05-03 | 後端（config/ 模組 deep review P1+P2 修補：@ConfigurationProperties + @Validated 防 silent misconfig + AsyncConfig @PreDestroy graceful shutdown + EmailHashMigration log stack trace + SecurityConfig prometheus startup log + 補 16 cases test） | **config module review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/config/` (13 檔、~1128 LOC、原 2 test) 稽核識別 1 P0 + 2 P1 + 3 P2。**P0（DataInitializer 自動建 admin/admin 在 docker profile）和 P1 #2 (DataInitializer Flyway 移植) 留下次 PR**（需要 deployment policy 討論：拿掉 docker profile vs 加 forcePasswordChange vs 隨機密碼）。本批次完成 P1 #1 + 3 P2 + 補測試。**過濾掉 Explore agent 5 個誇大/錯誤指控**（CORS allowCredentials wildcard 已被 line 55-58 reject 守住、CORS maxAge 3600s 是業界標準、AsyncConfig threads 「forcefully interrupted」誤讀 shutdown 不 interrupt 且 setDaemon=false 防 JVM 提早退出、Prometheus auth fallback 已 intentionally fail-closed 在 line 92-105、EmailHashMigration 「data corruption risk」overstated — 實際 idempotent 設計）。**P1 #1 4 個 `@ConfigurationProperties` 加 @Validated + @PostConstruct 守門**：`OktaProperties` (issuer @Pattern http(s) URL + @PostConstruct 檢查 enabled=true 時 clientId/clientSecret/issuer 三者皆非空)；`AiProperties` (provider @Pattern none/ollama/cloud + timeout @Min(1) + @PostConstruct 檢查 provider=cloud 需要 cloudApiKey、provider=ollama 需要 ollamaUrl)；`OllamaProperties` (url/model @NotBlank + timeoutSeconds @Min(1))；`RateLimitProperties` (requestsPerMinute @Min(1))。Spring 在 ApplicationContext 載入時 fail-fast — admin 漏設 `OKTA_CLIENT_SECRET` 等 env var 在 startup 就拋 IllegalStateException 而非 runtime cryptic OIDC failure。**P2 #1 `EmailHashMigration` log 加 stack trace（line 42）**：原 `log.warn(..., e.getMessage())` 漏掉 line / cause chain，且訊息推測 "encryption key mismatch" 但其實不知道實際錯因。改 `log.warn(..., e)` 帶 stack；訊息改為「rerun on next startup will retry」說明 idempotent。**P2 #2 `AsyncConfig` 加 @PreDestroy graceful shutdown（line 51-94）**：Spring 自動呼叫 ExecutorService bean 的 `shutdown()` 但**不等 in-flight 任務完成**，長 CQL evaluation / patient import 在 JVM exit 時被切斷。新加 `registeredExecutors` list、`SHUTDOWN_AWAIT_SECONDS=20L` 常數、`@PreDestroy void shutdownExecutors()` 走「shutdown → awaitTermination(20s) → 超時走 shutdownNow」三步。**P2 #3 `SecurityConfig` prometheus auth startup log（line 56-93）**：原 line 92-105 fail-closed 行為正確，但 ops 看不到狀態。新加 `@PostConstruct void logPrometheusAuthState()` log 三種狀態：PUBLIC（warn）、ENABLED（info 含 username）、BROKEN（warn 提示 env var 沒設）。**補 16 cases test**：(1) `AsyncConfigTest` +2 cases — `shutdownDrainsBothPools` 鎖 graceful drain 行為 + `shutdownIdempotent` 鎖呼叫兩次安全；(2) `WebConfigTest` 9 cases 全新 — 含 2 個 PAT-150 regression: 純 wildcard `*` reject + partial wildcard `https://*.example.com` reject、profile-aware origin (dev / docker / prod)、allowCredentials=true 與 wildcard 互斥不變式；(3) `EmailHashMigrationTest` 7 cases 全新 — backfill / skip already-hashed / skip null/blank email / mixed batch / 含 2 個 PAT-150 regression: findAll 拋例外不傳播 + save 拋例外不傳播。**測試** — config module 從 12 → 28 cases；4 個觸碰過的 controller test (Auth/Authoring/Settings/CdsServiceConfig 共 49 cases) 零回歸；完整 backend test suite 跑 in-flight。**未在本 PR**：P0 DataInitializer admin/admin 屬 deployment policy 決策題（`@Profile({"dev"})` only / 加 forcePasswordChange / 隨機密碼三選一），分開另一個小 PR；P1 #2 DataInitializer 移 Flyway 屬架構重構，搭 P0 一起做；DataInitializer 本身的 dedicated test 同樣留 P0 PR 寫。TFDA 追溯：需求 #465（安全性等級 A — fail-fast 配置驗證 + executor graceful shutdown + 觀測性 surface + zero-coverage tests；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | backend/src/main/java/com/cqlplatform/config/{OktaProperties,AiProperties,OllamaProperties,RateLimitProperties,EmailHashMigration,AsyncConfig,SecurityConfig}.java, backend/src/test/java/com/cqlplatform/config/{AsyncConfigTest,WebConfigTest,EmailHashMigrationTest}.java |[`5c050b9`](../../commit/5c050b9) |
| PAT-149 | 🐛 fix | 2026-05-03 | 前端（store/ + contexts/ 模組 deep review P1+P2 修補：safeStorage utility + Safari Private mode 不再 quota crash + BundleBuilderContext malformed JSON 安全 + TerminologyDrawerContext setTimeout cleanup + NotificationContext useRef + 補 4 個 context test） | **store + contexts module review-driven 修復批次** — 對 `frontend/src/store/` (4 slice、~392 LOC、4 test) 與 `frontend/src/contexts/` (7 provider、~451 LOC、原 0 test) 直接讀完不派 agent，識別 2 P1 + 4 P2 並全部修復。**P1 #1 `authSlice.ts` localStorage 全部沒 try/catch（line 19-22, 49-50, 55, 60, 67-68）**：模組載入時跑的 `getItem` 讀 `token` / `user`、reducers 內的 `setItem` / `removeItem` 沒包 try/catch。Safari Private 模式 `setItem` quota=0 拋 `QuotaExceededError`，導致 `setCredentials` reducer throw → Redux 不更新 → 即使更新成功，下次 reload token 沒在 storage → `isAuthenticated=false` → 強制 redirect login。比 PAT-144 對 `useTestCaseDraft` 的修補更嚴重，因為 auth 在每個 page load 都會跑。新增 `frontend/src/utils/safeStorage.ts` (~50 LOC) export `safeLocalStorage` / `safeSessionStorage` — 包 `getItem` / `setItem` / `removeItem` 在 try/catch（Storage 被 disabled 也 safe），`setItem` 失敗回 `false` 而非拋。`authSlice.ts` 全部改 `safeLocalStorage.*`。**P1 #2 `PreferencesContext.tsx` write paths 沒 try/catch（line 52, 58）**：`updatePreferences` 與 `resetPreferences` 的 `setItem` / `removeItem` 沒包，Safari Private 使用者改 preference 會中斷 setState callback。同 P1 #1 fix，改 `safeLocalStorage`。原 `loadPreferences` 已有 try/catch（包 JSON.parse），保留並小調整為「儲存成功讀到值才 try parse」。**P2 #1 `BundleBuilderContext.parseFromBundle` JSON.parse 沒 try/catch（line 89）**：caller 上傳 FHIR Bundle JSON 時 malformed 直接拋 SyntaxError。改為 try/catch JSON.parse + console.warn + 回 `[]`；同時補強 type guard（`typeof parsed === 'object' && parsed !== null` 防 `null` 例外、entry filter 用 type predicate）。**P2 #2 `TerminologyDrawerContext.closeDrawer` setTimeout 沒 clearTimeout on unmount（line 44）**：跟 PAT-144 `useNotifications` 同 family — pending timer 在 unmount 後 fire，setState on unmounted provider。修補：`clearOptionsTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)`，每次 close 前清 prev、`useEffect` cleanup 也清；`openDrawer` 內也清避免 close + 立刻 reopen 的 race。**P2 #3 `NotificationContext` `let nextId = 0` 改 `useRef`（line 21）**：模組層 mutable counter 在 HMR 下會殘留（HMR 不重置 module 但重置 React state），極少數情況 ID 會撞。改 per-provider `nextIdRef = useRef(0)`。**P2 #4 補 4 個 context test (24 cases)**：`backend/src/test/java/com/cqlplatform/contexts/` 從 0 建立。(1) `BundleBuilderContext.test.tsx` 8 cases — `serializeToBundle` 2 個 + `parseFromBundle` 6 個（含 PAT-149 regression: malformed JSON 回 [] 不拋）；(2) `PreferencesContext.test.tsx` 6 cases — 含 PAT-149 regression: setItem 拋 quota error 時 in-memory state 仍更新；(3) `TerminologyDrawerContext.test.tsx` 4 cases — 含 PAT-149 regression: pending timer cleared on unmount + reopen 取消舊 close timer；(4) `EhrOutageContext.test.tsx` 5 cases — 聚合同 connection / 分開不同 connection / clearOutage / clearAll。順手抽 `FHIR_BUNDLE_TYPE` 到新 `frontend/src/constants/bundle.ts`（原本 in `components/testcase-builder/constants.tsx` 連帶 import 大量 MUI icons 觸發 Windows EMFILE 阻擋 vitest collect）。**測試** — 8 個 test file 共 44 cases 全綠（store 4 file + 4 個新 context file，從 12 → 44 cases）；`tsc --noEmit` 零錯。**未在本 PR**：剩下 3 個 contexts (`LibraryHistoryContext` / `ResourceTypeContext` / `EhrOutageContext` 部分 listener bridge 的整合) 補測試屬獨立 P3 polish；`authSlice` JWT 在 localStorage 的 XSS 風險屬業界 trade-off（refresh token 已在 httpOnly cookie），不在本 PR 範圍。TFDA 追溯：需求 #463（安全性等級 A — Safari Private mode 使用者體驗修補 + JSON.parse 邊界 safety + setTimeout cleanup + 補 zero-coverage context tests；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | frontend/src/utils/safeStorage.ts（新）, frontend/src/store/authSlice.ts, frontend/src/contexts/{PreferencesContext,BundleBuilderContext,TerminologyDrawerContext,NotificationContext}.tsx, frontend/src/contexts/\_\_tests\_\_/{BundleBuilderContext,PreferencesContext,TerminologyDrawerContext,EhrOutageContext}.test.tsx, frontend/src/constants/bundle.ts |[`0da775f`](../../commit/0da775f) |
| PAT-148 | 🐛 fix | 2026-04-30 | 前端（utils/ 模組 deep review — 補 twDemographics 測試 + 修補 NHI checksum bug） | **utils 模組補測試 + NHI checksum 修復** — 對 `frontend/src/utils/` (21 檔、~3476 LOC、12 個測試檔) 稽核後，**確認模組整體沒有 actionable P0/P1 bug**（過濾掉 Explore agent 的 5 個誇大/出範圍指控：CQL injection 在 components/ 不在 utils/、libraryReference uniqueId 純內部 Redux 使用 Math.random 沒問題、fhirPatientGenerator nextId 已加 idCounter 累加不會撞、cqlNames lazy regex 是 O(n²) 不是 catastrophic backtracking ReDoS、download.ts header injection 在 browser `<a download>` context 安全）。但**過程中補 `twDemographics.test.ts` 立刻揪出真實 NHI checksum bug**：`generateNhiId` 使用 9 個 weights `[1,9,8,7,6,5,4,3,2]` 對應 10 個 digits（letter pair + gender + 7 random），漏掉**第 7 個隨機數字（r7）的權重**，導致 check digit 只對前 9 位正確。Taiwan 官方 NHI 驗證演算法（戶籍法施行細則第 4 條）使用 11 個 weights `[1,9,8,7,6,5,4,3,2,1,1]` 對應 11 個 digits（letter pair + gender + 7 random + check）。生成的 NHI 通過外部 FHIR profile validator（含 Taiwan IG conformance）的機率約 10%（只有 r7=0 那 10% 才能滿足 `r7*1 ≡ 0 mod 10` 的隱含要求）。**修補**：weights 改 `[1,9,8,7,6,5,4,3,2,1]` 共 10 個，loop 涵蓋 allDigits[0..9] 包含 r7；javadoc 補充演算法 + PAT-148 註解說明歷史。**新加 `twDemographics.test.ts` 15 cases**：(1) `isValidTwNhi` test helper sanity check (拒非法格式 / 通過官方範例 A123456789)；(2) `generateNhiId — PAT-148 validity regression` — 200 樣本批次驗證 + male/female 性別碼正確 + 起始字母在 26 個合法字母內；(3) `generateName` / `generateAddress` / `generateMobile` / `generateLandline` / `generateBirthDate` / `randomDateInRange` / `randomGender` / `generateEmail` 各 1-2 cases 鎖住格式與範圍。test helper `isValidTwNhi` 是獨立實作官方演算法，避免「測同一個 buggy 算法」的循環。**測試** — 15 cases 全綠；既有 utils test suite 145 → 160 cases 零回歸；`tsc --noEmit` 零錯。**未在本 PR 範圍**：`fhirPatientGenerator.ts` (451 LOC) 補測試、`conjunctionTreeUtils.ts` (142 LOC) 補測試、`cqlNames.ts` 大檔 size guard — 都是 nice-to-have 不影響當前正確性，獨立 PR 處理。下次 review components/ 時可順手檢查 `escapeCqlIdentifier` 在 components 層是否一致使用（agent 出範圍指控的真實 follow-up）。TFDA 追溯：需求 #461（安全性等級 A — 修補真實但限定範圍的資料正確性 bug + 補測試守住；影響範圍：使用 fake patient generator 產出的 NHI ID 會通不過 Taiwan IG conformance，但 production 不依賴此 generator 進行臨床評估，影響限於 demo/testing 場景） | frontend/src/utils/twDemographics.ts, frontend/src/utils/\_\_tests\_\_/twDemographics.test.ts |[`0ecf082`](../../commit/0ecf082) |
| PAT-147 | 🐛 fix | 2026-04-30 | 前端（components/auth/ 模組 deep review P1+P2 修補：AdminRoute 改 parameterized allowedRoles + ForcePasswordChangeDialog 用 extractApiError + 提交成功清密碼 state + 補測試 12 cases） | **components/auth module review-driven 修復批次** — 對 `frontend/src/components/auth/` (3 檔、174 LOC、原 1 個 test) 直接讀完不派 agent 識別 1 P1 + 3 P2 並全部修復。**P1 #1 `AdminRoute` 角色檢查與 backend 不一致**：原 `AdminRoute.tsx:13` 寫死 `user?.role !== 'ADMIN'` redirect 到 `/`，但 backend 的 `SecurityConfig:191` 是 `/api/admin/** → hasAnyRole("ADMIN", "DEPARTMENT_ADMIN")`，且 PAT-145 為 `AuditController` / `AdminController` 加的 `@PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_ADMIN')")` 也明確允許 DEPARTMENT_ADMIN。導致 DEPARTMENT_ADMIN 登入後嘗試導航到 `/admin/audit` 被前端 redirect 走，雖然 backend 會授權該角色看 audit dashboard。修補：`AdminRoute` 加 `allowedRoles?: AdminRouteRole[]` prop（預設 `['ADMIN']`），`App.tsx:163` `/admin/audit` 路由改傳 `['ADMIN', 'DEPARTMENT_ADMIN']`；`/admin/users` 保留預設 `['ADMIN']`（user CRUD 含 grant role，仍應 ADMIN-only）。新加 `AdminRouteRole` 類型 + javadoc 提醒「保持與 backend SecurityConfig:191 同步」。**P2 #1 `ForcePasswordChangeDialog` 改用 `extractApiError`**：原 line 60-62 `axiosErr.response?.data?.error || t('auth.changePasswordFailed')` bare 解 axios reject — 漏接非-Axios 錯誤路徑（純 Error / 網路 timeout 等）會 fallback 到 generic 訊息。改用 `extractApiError(err)`（與 `useCql.ts` PAT-144 相同 utility），統一 unwrap AxiosError + 結構化 server errors。**P2 #2 提交成功清密碼 state**：原來 `currentPassword` / `newPassword` / `confirmPassword` 三個 React state 即使 changePassword 成功（line 59 `clearForcePasswordChange`）後也保留在 component 直到 unmount — plain-text 密碼 lingering in heap，React DevTools 仍能擷取。修補：`onSuccess` 後 `setCurrentPassword('') / setNewPassword('') / setConfirmPassword('') / setFieldErrors({})` 主動清除，inline 註解說明動機。**P2 #3 補 12 cases test**：原僅 `ProtectedRoute.test.tsx` (2 cases)。新加：(1) `AdminRoute.test.tsx` 6 cases — `redirects to /login when not authenticated` / `renders for ADMIN (default)` / `redirects USER role` / **`PAT-147 regression: DEPARTMENT_ADMIN redirected from default ADMIN-only`** / **`PAT-147 regression: DEPARTMENT_ADMIN allowed when allowedRoles includes it`** / `redirects when user.role missing`；(2) `ForcePasswordChangeDialog.test.tsx` 6 cases — `shows three password fields` / `blocks submit when fields empty` / `blocks submit when passwords differ` / `calls authApi.changePassword on valid submit` / **`PAT-147 regression: password fields cleared after success`** / `surfaces server error via extractApiError`。test-utils 不初始化 i18next，沿用 measure 模組 mock pattern (`vi.mock('react-i18next')` 讓 `t(key)` 回 key 本身) 直接 query i18n key (`auth.currentPassword` 等)。`extractApiError` test 用真正的 `new AxiosError(...)` 實例（pure object 不通過 `instanceof` 檢查）。**測試** — 14 個 `components/auth` tests 全綠（從 2 → 14, +12 PAT-147 cases）；`tsc --noEmit` 零錯。**未在本 PR 範圍**：force-change dialog 沒 logout escape hatch（user 忘了 temp password 死鎖）— 屬故意設計但可加 "Logout instead" 連結（P3 polish）；`ForcePasswordChangeDialog` 的 submit-only validation（沒 onBlur 即時提示）也是純 UX 偏好。TFDA 追溯：需求 #459（安全性等級 A — frontend/backend role 一致性 + extractApiError 規範化 + 密碼 state hygiene + 補測試；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | frontend/src/components/auth/{AdminRoute,ForcePasswordChangeDialog}.tsx, frontend/src/App.tsx, frontend/src/components/auth/\_\_tests\_\_/{AdminRoute,ForcePasswordChangeDialog}.test.tsx |[`65f99da`](../../commit/65f99da) |
| PAT-146 | 🐛 fix | 2026-04-29 | 後端（repository/ 模組 deep review P0+P1+P2 修補：AuditLogSpecification cb.like 加 escape char + MeasureReportRepository.findByMeasureDefinitionId 200 筆上限 + 兩個 @Modifying clearAutomatically + AuditLogSpecificationTest 新增） | **repository module review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/repository/` (36 檔、~868 LOC) 稽核識別 1 個 P0 + 1 個 P1 + 2 個 P2 並全部修復。**過濾掉 Explore agent 3 個誇大/錯誤指控**（`AuditLogRepository` hardcoded `'%login%'` / `'%auth%'` 沒 ESCAPE — 純常數 substring 不含 wildcard 字元無問題；`UserApiKeyRepository.findByUsername` unbounded — 一個 user 不會有上千 API keys 沒 DoS framing；N+1 / EntityGraph 泛論沒舉具體 case 不可 actionable）。**P0 #1 `AuditLogSpecification.cb.like()` 沒傳 escape char，`escapeLikeWildcards` 形同虛設（line 53, 61）**：`InputValidator.escapeLikeWildcards()` 用 `\` 跳脫 `%` `_` `\`，但 `cb.like(expression, pattern)` 用的是 2-arg overload 沒指定 escape char → Hibernate 直接把 raw pattern 送 PostgreSQL → PG 預設 `standard_conforming_strings=on`，**backslash 不是 LIKE 的 escape char**（只有 `%` 和 `_` 是 wildcard）→ 整個 escape 邏輯**完全沒生效**。攻擊場景：audit log search 帶 `username=admin%` 想搜 literal "admin%"，escape 後 `%admin\%%`，PG 解讀為 "any chars, admin, literal backslash, any chars" — `%` 還是 wildcard，匹配所有 admin 開頭的 username。修補：`usernameContains` (line 53) 與 `resourceTypeEquals` (line 61) 改用 3-arg `cb.like(expr, pattern, '\\')`；新加 `LIKE_ESCAPE = '\\'` private constant + javadoc 解釋為什麼。雖然 audit endpoints 已 ADMIN-only 守住，**LIKE wildcard injection 屬「修補一次就一勞永逸」的 paper cut**。**P1 #1 `MeasureReportRepository.findByMeasureDefinitionIdOrderByCreatedAtDesc` 加 200 筆硬上限（不破 API 契約）**：原方法 unbounded，daily-evaluated measure 跑 5 年 ≈ 1825 筆/measure，hourly = 43800 筆/measure。`MeasureController:392 GET /reports/{measureId}` 直接序列化整批 → 記憶體 / 頻寬 / JSON parse 壓力。修補：repository 加 `Pageable` overload 保留原 unpaged method 給 cleanup / cascade 使用；service `getReportsForMeasure` 改用 `PageRequest.of(0, 200)`；新增 `MAX_REPORTS_PER_MEASURE_RESPONSE = 200` constant + javadoc 說明選 200 因為「典型 UI 看最近 50–100 筆，留 headroom；需完整 history 應走 paged endpoint」。controller 簽名不變（API 契約穩定）。同檔的 `findTop50` / `findTop10` 已示範正確 pattern，line 17 / 19 / 21 / 27 / 32 / 38 / 43 / 56 / 61 仍 unbounded 但無 controller 直接 expose（部分為 service 內部用），留作獨立 PR 處理。**P2 #1 `@Modifying(clearAutomatically=true)`**：`AuditLogRepository.deleteByCreatedAtBefore` (line 48-50) 與 `UserApiKeyRepository.deactivateAllByUsername` (line 19-21) 兩個 bulk DELETE / UPDATE 缺 `clearAutomatically=true`。場景：retention task 跑完同 transaction 內若再讀 audit log，L1 cache 仍有被刪掉的 entity；UserApiKey 部分若 caller 同 transaction 重新讀 active=true 仍會看到舊狀態。實務上 retention 通常獨立 transaction 跑影響窄，但補上 annotation 是 best practice + 加 javadoc 說明。**P2 #2 新增 `AuditLogSpecificationTest`（3 cases）**：`backend/src/test/java/com/cqlplatform/repository/` 從零開始建立，鎖住 PAT-146 的 escape 行為。(1) `usernameContains_invokesCriteriaBuilderLikeWithEscapeChar` — 帶 `%` 的 input 經過 escape 後 cb.like 收到 `%admin\%test%` + `'\\'` escape；(2) `resourceTypeContains_invokesCriteriaBuilderLikeWithEscapeChar` — 帶 `_` 的 input 同樣驗證；(3) `noFilters_returnsConjunctionPredicate` — 空 request 不拋例外。Mockito 捕獲 cb.like 的 ArgumentCaptor 驗證 escape char 與 escaped pattern。**測試** — 新增 `AuditLogSpecificationTest` 3 cases 全綠；既有 `AuditServiceTest` 13 / `AuditControllerTest` 9 / `MeasureControllerTest` 3 / `UserApiKeyControllerTest` 6 共 34 cases 零回歸；完整 backend test suite 跑 in-flight。**未在本 PR 範圍**：`MeasureReportRepository` 其他 unbounded list method (findByMeasureName / findByDepartment / etc.) — 確認沒 controller 直接 expose 後屬獨立 P2 PR；其他 `@Modifying` 已查證大致都沒問題；零 repository test coverage 的補強屬獨立 PR（本批次先建立 `repository/` test 目錄 + 第一個 spec test）。TFDA 追溯：需求 #457（安全性等級 A — 修補 LIKE wildcard injection paper cut + DoS / OOM 防護 + L1 cache 一致性 best practice + 補測試；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | backend/src/main/java/com/cqlplatform/repository/{AuditLogSpecification,AuditLogRepository,MeasureReportRepository,UserApiKeyRepository}.java, backend/src/main/java/com/cqlplatform/service/measure/MeasureReportService.java, backend/src/test/java/com/cqlplatform/repository/AuditLogSpecificationTest.java |[`262b043`](../../commit/262b043) |
| PAT-145 | 🐛 fix | 2026-04-29 | 後端（controller/ 模組 deep review P1+P2 修補：SettingsController 鎖 ADMIN + Optional<T> constructor injection + AuthController.getBaseUrl prod fail-fast 防 X-Forwarded-Host phishing + Audit/Admin 類別層 @PreAuthorize defense-in-depth + 非-admin 403 regression test） | **controller layer review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/controller/` (20 controllers、~4612 LOC、22 test files) 稽核識別 3 個 P1 + 2 個 P2 並全部修復。**過濾掉 Explore agent 1 個誇大 framing**（「missing @PreAuthorize on AuditController/AdminController endpoints」被 framed 為 P0 — URL-path 保護**已在 SecurityConfig:191** `/api/admin/** → hasAnyRole("ADMIN", "DEPARTMENT_ADMIN")` 設好，現行有效。`@PreAuthorize` 是 defense-in-depth 加分而非當下 bug，降為 P2 #1）。**P1 #1 `SettingsController` GET endpoints 鎖 ADMIN**：原 `getVsacStatus()` 與 `getAiStatus()` 只走 `SecurityConfig:198` 的 `/api/** → authenticated()` catch-all，任何登入 user 都能讀內部設定（VSAC server URL、AI provider 與 model 名稱）。`SecurityConfig:192` 只保護 `PUT /api/settings/**` → ADMIN，但設定管理整體應為 operator-only。修補：類別層加 `@PreAuthorize("hasRole('ADMIN')")` — 完整覆蓋 GET / PUT 所有 method。**P1 #2 `SettingsController` field `@Autowired(required=false) CqlFixService` 改 Optional<T> constructor**（line 21–22）：跟 PAT-141 在 `CqlTranslationService`、PAT-143 在 `JwtAuthenticationFilter` 修過的同一反模式（CLAUDE.md「禁止 @Autowired 在欄位上」）。改 explicit constructor with `Optional<CqlFixService>` 對齊既有 pattern；移除 `@RequiredArgsConstructor`（手寫 constructor）。**P1 #3 `AuthController.getBaseUrl()` prod fail-fast 防 X-Forwarded-Host phishing**（line 353–365）：原 fallback 從 `X-Forwarded-Host` / `Host` header 組裝 base URL — header 無認證機制可被 spoof，若 prod `APP_BASE_URL` env var 漏設會導致 password-reset email 內 reset 連結指向攻擊者域名。code 已 log warn 但繼續 fallback 不夠強。修補：新增 `@Value("${app.allow-base-url-fallback:true}") boolean allowBaseUrlFallback`，預設 true 保留 dev / docker 方便；`getBaseUrl` 在 `configuredBaseUrl` 缺且 `allowBaseUrlFallback=false` 時 log.error + 拋 `IllegalStateException`（fail loud）。`application-docker.yml` 補 `app.allow-base-url-fallback: false` — production / docker profile 必須設 `APP_BASE_URL` 否則整個 password-reset 流程 reject，避免悄悄送 phishing email。**P2 #1 `AuditController` / `AdminController` 類別層 `@PreAuthorize` defense-in-depth**：URL-path 保護仍由 `SecurityConfig:191` 提供（`/api/admin/** → hasAnyRole("ADMIN", "DEPARTMENT_ADMIN")`），但 method-level annotation 是 second line of defense — 若未來 SecurityConfig refactor / `@RequestMapping` path 移動，此 annotation 仍會擋下 unauthorized request。`AuditController` 已有 1 個 endpoint (line 153) 加了 `@PreAuthorize`，可見 pattern 已局部存在；統一在類別層加 `@PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_ADMIN')")` 一次覆蓋全 controller。Inline 註解說明保持與 SecurityConfig 同步的維護義務。**P2 #2 非-admin 403 regression test**：既有測試覆蓋 unauthenticated (401) 與 admin (200)，但**沒有測試 USER 角色被拒**。`AdminControllerTest` / `AuditControllerTest` / `SettingsControllerTest` 各加一個 `@WithMockUser(username = "regular", roles = {"USER"})` 測試打 admin endpoint 應得 403，鎖住「未來改 SecurityConfig 不小心開洞」。`SettingsControllerTest` 的 2 個既有測試從 `@WithMockUser(username = "testuser")`（預設 USER role）改為 `@WithMockUser(roles = {"ADMIN"})` 對齊新 P1 #1 ADMIN-only 規範。**測試** — `SettingsControllerTest` 4 → 5 cases（新加 1 個 USER 403 + 改 2 個既有測試 role）；`AdminControllerTest` 12 cases（新加 1 個 USER 403）；`AuditControllerTest` 9 cases（新加 1 個 USER 403）；4 controller test files 共 41 tests 全綠；完整 backend test suite in-flight。**未在本 PR 範圍**：其他 controllers (Measure / FHIR / Authoring 等) 也可加類別層 `@PreAuthorize` 但對應 SecurityConfig 規則複雜（部分 endpoint 只 authenticated 即可），值得獨立 PR 審查每個 controller 的 role 需求。TFDA 追溯：需求 #455（安全性等級 A — 修補真實 info disclosure (settings GET) + 防 X-Forwarded-Host phishing 的 prod fail-fast + defense-in-depth role check + regression test；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | backend/src/main/java/com/cqlplatform/controller/{SettingsController,AuthController,AuditController,AdminController}.java, backend/src/main/resources/application-docker.yml, backend/src/test/java/com/cqlplatform/controller/{SettingsControllerTest,AdminControllerTest,AuditControllerTest}.java |[`8eb1329`](../../commit/8eb1329) |
| PAT-144 | 🐛 fix | 2026-04-29 | 前端（hooks/ 模組 deep review P1+P2 修補：useCql extractApiError + useNotifications SSE timer cleanup + 移除硬編碼 "Execution failed" + useTerminologyValidation surface ELM 解析錯誤 + useTestCaseDraft storage try/catch + useNotifications.test.tsx 新增） | **frontend hooks module review-driven 修復批次** — 對 `frontend/src/hooks/` (36 hook、~2168 LOC、25 測試檔) 稽核識別 3 個 P1 + 3 個 P2 並全部修復。**過濾掉 Explore agent 3 個誇大/錯誤指控**（"infinite SSE reconnect loop" — `connectSSE` deps `[queryClient]` 跨 render 穩定，useEffect 不會重跑，真正問題是 pending timer 沒清；"concurrent SSE tickets issued race" — 跟 timer 同根，已併入 P1 #2 修法；"useNotification vs useNotifications duplication" — 兩個是 distinct 概念 (toast context API vs server state)，命名混淆是 P3 polish 不在這批次）。**P1 #1 `useCql.ts` 三個 onError 從 bare `error.message` 改為 `extractApiError`**（line 47, 67, 97 = `useTranslate` / `useValidate` / `useExecute`）：axios reject 是 AxiosError 不是 Error，server 結構化 errors 會被吞成「Request failed with status 500」這種無資訊訊息。`extractApiError` 已在 `MeasureEditor` / `MeasureLibrary` / `MeasureReportHistory` 等元件用過，hook 層補上一致。**P1 #2 `useNotifications.ts` SSE retry timer + post-unmount EventSource cleanup**：原本兩個 `setTimeout(connectSSE, REFETCH_30S)`（line 82 onerror、line 87 catch）沒記 timeout id，`useEffect` cleanup 只 close EventSource 不清 pending timer。場景：使用者在 notification 頁面 → SSE 連線失敗 → 排定 30s 後 reconnect → 使用者離開頁面 → cleanup 跑只 close EventSource → 30s 後 timer fire → connectSSE 對 unmounted ref 寫 EventSource → 累積 phantom 連線。修補：加 `reconnectTimerRef: useRef<ReturnType<typeof setTimeout> | null>` 記 timeout id，新加 `scheduleReconnect` helper 每次先 clearTimeout(prev) 再 setTimeout；加 `isActiveRef: useRef<boolean>(true)` 在 `useEffect` cleanup 時設 false，`scheduleReconnect` 內 timer 觸發時先檢查 `isActiveRef.current`，`api.post.then()` callback 也先檢查 — slow ticket request 在 unmount 後解決時拒絕建 EventSource。**P1 #3 移除 `useCql.ts` 硬編碼 "Execution failed"**（line 90）：`dispatch(setExecutionErrors(data.errors || ['Execution failed']))` fallback string 違反 CLAUDE.md「所有 UI 文字用 i18n」。改為 dispatch 空 array `[]`，UI fallback 移到 `ExecutionPanel.tsx` line 213 走 `t('execution.failedGeneric', 'Execution failed')`，當 `errors.length === 0 && executeMutation.data?.success === false` 時 render Alert。新加 i18n key `editor.execution.failedGeneric`（en: "Execution failed" / zh-TW: "執行失敗"）。`onError` 也順手改用 `extractApiError(error)`。**P2 #1 `useTerminologyValidation.ts` silent catch 改 surface error**（line 128-130）：原 `} catch { /* ELM parse error — no validation possible */ }` 完全不通知 caller，UI 顯示空結果看起來像「全部通過」。改為 `items.push({ name: '_elm', type: 'parseError', status: 'error', detail: e.message })`，新加 `'parseError'` 進 `TerminologyValidationItem.type` union（`'valueset' | 'code' | 'codesystem' | 'parseError'`），UI 層可以據此 render「驗證跳過 — ELM 損毀」訊息而不是空結果誤導。**P2 #2 `useTestCaseDraft.ts` 4 個 storage helper 加 try/catch**（line 91-112 = `clearTestCaseDraft` / `saveEditingState` / `loadEditingState` / `clearEditingState`）：原本直接 `localStorage.removeItem` / `sessionStorage.setItem` 等，Safari Private 模式或 quota exceeded 會丟 `QuotaExceededError` / `SecurityError` 直達 caller crash 元件。修補：每個 helper 包 try/catch，failures 是 best-effort（漏存 = 下次不還原 draft，遠比 uncaught throw 好）。同檔 `loadDraft` 已有 try/catch，這 4 個對齊。**P2 #3 新增 `useNotifications.test.tsx` 7 cases**：(1) `mount with token → opens EventSource`；(2) `no token → skip connecting`；(3) `unmount → closes EventSource`；(4) **PAT-144 regression: pending reconnect setTimeout 在 unmount 時清掉**；(5) **PAT-144 regression: ticket request 在 unmount 後解決不建 EventSource**；(6) `SSE error → reconnect after 30s`；(7) `initial query data shape`。MockEventSource 類別替代 jsdom 沒提供的 EventSource；`vi.useFakeTimers` 控制 reconnect 時序。**測試** — `useNotifications.test.tsx` 7 cases 全綠（新檔）；既有 hook test suite 180 → 187 cases；`tsc --noEmit` 零錯；完整 frontend test suite 跑 in-flight。**未在本 PR 範圍**：`useEcqm.ts` (127 LOC) 與 `useCqlLibraries.ts` (151 LOC) 雖無 dedicated test，主要是 mutation + invalidation 的 thin wrapper，coverage 透過元件測試間接到位 — 補測試屬獨立 P2 PR；hook 命名一致性 (`useFoo` vs `useFooQuery`、`useNotification` vs `useNotifications`) 屬 polish。TFDA 追溯：需求 #453（安全性等級 A — 修補真實 SSE 連線洩漏 + 對齊 i18n + extractApiError 規範化 + storage 邊界處理；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | frontend/src/hooks/{useCql,useNotifications,useTerminologyValidation,useTestCaseDraft}.ts, frontend/src/hooks/\_\_tests\_\_/useNotifications.test.tsx, frontend/src/components/execution/ExecutionPanel.tsx, frontend/src/types/index.ts, frontend/src/locales/{en,zh-TW}/editor.json |[`69629b5`](../../commit/69629b5) |
| PAT-143 | 🐛 fix | 2026-04-29 | 後端（com.cqlplatform.security 模組 deep review P0+P1+P2 修補：TokenVersionService.loadFromDb sentinel 修正 hard-delete user JWT bypass + JwtAuthenticationFilter constructor injection + AuditFilter log.error）| **security module review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/security/` (17 檔案、~1639 LOC) 稽核識別 1 個 P0 + 1 個 P1 + 1 個 P2 並全部修復。**P0 是驗證 Explore agent 報告過程中我自己發現的、agent 漏掉的真實 auth bypass**；同時過濾掉 agent 兩個錯誤 P0 指控（"fail-open if TokenVersionService throws" — 實際 `getCurrentVersion` 拋例外會 propagate ServletException → Spring 回 500，是 fail-closed；"audit failure = request bypass" — `AuditFilter.doFilterInternal` line 70 是 `filterChain.doFilter` 在 audit save 之前，request 已被前面 auth filter 把關，audit 失敗只是 post-action 紀錄遺失，不是「動作沒被檢查就放行」），agent 自己也標了 1 個 false alarm（rate limit refill race — synchronized 已守住）。**P0 #1 `TokenVersionService.loadFromDb` 對未知 user 回 `-1` defeat token version check** — `loadFromDb` (line 72-74) 原 `userRepository.findTokenVersionByUsername(username).orElse(-1)`；對應 `JwtAuthenticationFilter.doFilterInternal` (line 80-87) 的 `if (claimVersion < currentVersion)` 在 user hard-delete 後 `currentVersion = -1`、token claim `>= 0` → `0 < -1 = false` → **不拒絕 → setAuthentication 走完 → 已刪除 user 的 JWT 繼續被當合法**直到 token expiry（access 預設 15min / refresh cookie 7 days）。即使刪除前 `bumpVersion` 把版本升到 1，刪除後 `loadFromDb` 還是回 `-1` sentinel，把版本檢查整個倒轉。PAT memory 列的 logout/password-change/role-change/disable-account 都會 bumpVersion，但 hard-delete user 沒列上 — 而正是 hard-delete 場景最需要立刻撤銷 token。`JwtAuthenticationFilterTest` 不存在，這條路徑沒被任何測試守住。**修補**：`loadFromDb` sentinel 改 `Integer.MAX_VALUE`（user 不存在 = 永遠 < 任何 claim 版本，filter 邏輯不變、檢查方向反過來變正確）。`bumpVersion` 內 line 60 重新 load 改用直接 `userRepository.findTokenVersionByUsername(...).orElse(-1)` 繞過新 sentinel，避免「Bumped to 2147483647」誤導 log（user 在 increment 與 load 之間被刪除的小窗）。**新增 `JwtAuthenticationFilterTest` 5 cases**：`freshToken_shouldSetAuthentication` / `staleToken_shouldRejectWithoutSettingAuthentication` / `deletedUser_shouldRejectToken_PAT143_regression` / `noAuthorizationHeader_shouldPassThroughWithoutAuth` / `apiKeyAuth_perUserCdsEndpoint_shouldUseApiKeyService`。**P1 #1 `JwtAuthenticationFilter` constructor injection** — 原 line 31-32 `@Autowired(required=false) private UserApiKeyService userApiKeyService`，跟 PAT-141 在 `CqlTranslationService` / PAT-138 在 `CdsHooksService` 修掉的同一反模式（CLAUDE.md「禁止 @Autowired 在欄位上」）。其他 3 個 dep 都用 constructor，只有這個是 field。改為 explicit constructor with `Optional<UserApiKeyService>` 對齊既有 pattern；移除 `@RequiredArgsConstructor` 因為手寫 constructor。**P2 #1 `AuditFilter.writeAuditLog` 失敗從 log.warn 升 log.error** — line 165-167 PHI access / EXPORT / BATCH_IMPORT 等高敏感操作的 audit 失敗時，request 已經處理（line 70 `filterChain.doFilter` 在 audit 之前），但 audit 紀錄遺失屬合規問題；WARN 在多數 SIEM 不會發 alert，改 ERROR + 帶 stack trace + inline 註解說明這是 post-action logging（auth 已在前面 filter 把關）讓 ops 看到。**測試** — 新增 `JwtAuthenticationFilterTest` 5 cases 全綠；既有 38 個 security/audit-related tests 零回歸（JwtTokenProviderTest 12 / AuditControllerTest 8 / AuditServiceTest 13 + 新加 5）；完整 backend test suite BUILD SUCCESS 零回歸。**未在本 PR 範圍**：把 hard-delete user 的 admin 路徑改為先 bumpVersion 再 delete 屬另一個 controller 層的修補（會搭配 user CRUD 設計）；TokenVersionService cache 失效時的退路（fall back to DB? 還是 fail-closed?）屬 SLO 設計題。TFDA 追溯：需求 #451（安全性等級 B — 修補真實 auth bypass + 對齊 constructor injection 規範 + audit 觀測性升級；直接影響 token revocation 正確性，但既有 token 仍會在 token expiry 內失效，影響窗口為 access TTL 15 分鐘） | backend/src/main/java/com/cqlplatform/service/TokenVersionService.java, backend/src/main/java/com/cqlplatform/security/{JwtAuthenticationFilter,AuditFilter}.java, backend/src/test/java/com/cqlplatform/security/JwtAuthenticationFilterTest.java |[`c77dac8`](../../commit/c77dac8) |
| PAT-142 | 🐛 fix | 2026-04-29 | 後端（service/fhir 模組 deep review P1+P2 修補：testConnection 加事務邊界 + TLS hostname verification 全域守門 + Local IG silent catch 區分 not-loaded vs broken） | **fhir module review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/service/fhir/` (19 檔案、~4935 LOC，FHIR 客戶端 / patient import / terminology / IG / structure definition / TLS / SMART OAuth / VSAC) 稽核識別 1 個 P1 + 2 個 P2 並全部修復。**過濾掉 Explore agent 4 個錯誤指控**（SSRF on user URL — `InputValidator.requireValidUrl` 已在 5 個 controller 都呼叫，擋 loopback/link-local/site-local/multicast/embedded credentials，dev/docker/test profile 才放行 localhost；`FhirTerminologyService` field injection — 該 file 已用 constructor injection，agent 把 `@Autowired(required=false)` on constructor parameter 誤認為 field-injection 風格；`VsacService` volatile API key race — String reference 賦值本身就 atomic (JLS §17.7)；`FhirDataProviderService` 加 `@Transactional(readOnly=true)` — 整個 service 沒 DB 存取，跟 PAT-140 `StratifierEvaluator` 同款 cargo-cult false claim）。**P1 #1 `EhrConnectionService.testConnection(Long id)` 缺 `@Transactional`（line 109-140）**：是 read-modify-save 多步驟 pattern (`findById` → `setStatus` / `setLastTestedAt` / `setLastTestMessage` → `save`)，success path 與 catch block 內都走「mutate + save」路徑但**沒事務邊界** — 違反 CLAUDE.md「多步驟變更必須加 `@Transactional`」。同檔案的 `create` / `update` / `delete` 都正確 annotate，只有 `testConnection` 漏。修補：補上 `@Transactional`，與兄弟 method 對齊。**P2 #1 `TlsContextFactory.createHostnameVerifier` 加全域守門（line 77-99）**：原本 `connection.hostnameVerification=false` 直接回 always-accept verifier，admin 在 DB 寫了該欄位即接受任何 cert — 雖有 `log.warn` 提示「test environments only」，但無 profile 守門，misconfigured prod EHR connection 會 silently 在 MITM 下接受偽造 cert。新加 `@Value("${security.tls.allow-disable-hostname-verification:false}")` 全域 kill-switch — 預設 false 即使 DB 寫了也回 strict 模式（log.warn 提示衝突），營運必須在非 prod yml 顯式 opt-in 才能 disable verification。`TlsContextFactoryTest` 12 cases（原 11，新加 1 個 globalGuardOff_appliesStrictAnyway 鎖住預設 strict 行為，並把既有 disabled-returnsPermissiveVerifier 改名為 globalGuardOn_returnsPermissiveVerifier 並用 `ReflectionTestUtils.setField` 顯式打開 flag）。**P2 #2 `FhirTerminologyService.lookupCodeFromLocalIg` / 對應 search method silent catch 區分 IG 狀態（line 259-269 / 377-385）**：原本 `catch (Exception e) { log.debug("Local IG lookup failed: ...", e.getMessage()); }` 把「IG 沒載」與「IG 載了但 resource 損毀」合在 DEBUG level — 無法區分兩種狀態（沒載是正常 fall-through，載了損毀是需立即調查的問題）。改為檢查 `igService.isLoaded()`：true → `log.warn("unexpectedly threw … IG is loaded — possible corrupt resource")` 升 WARN；false → 維持 DEBUG「IG not loaded」，operators 在 prod log 看到 WARN 知道要去檢查 IG package 完整性而不是被「沒載」訊息誤導。**測試** — 既有 128 個 fhir-related tests 零回歸（FhirBulkExportService 2 / FhirClientFactory 9 / FhirDataProviderService 5 / FhirFallback 4 / FhirImplementationGuide 7 / FhirSubscription 10 / FhirValidation 9 / TlsContextFactory 12 等）；TlsContextFactoryTest 11 → 12 cases；完整 backend test suite BUILD SUCCESS 零回歸。**未在本 PR 範圍**：`EhrConnectionService` / `FhirTerminologyService` 沒 dedicated test class（13 個 test 對 19 個 service 缺 6 個），補測試屬獨立 P2 PR；`FhirDataProviderService` 760 LOC / `FhirTerminologyService` 642 LOC / `FhirStructureDefinitionService` 530 LOC 拆分屬架構重構，獨立 PR 處理。TFDA 追溯：需求 #449（安全性等級 A — 補事務邊界 + 全域 TLS 守門 + 區分 IG 故障 vs 未載；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | backend/src/main/java/com/cqlplatform/service/fhir/{EhrConnectionService,TlsContextFactory,FhirTerminologyService}.java, backend/src/test/java/com/cqlplatform/service/fhir/TlsContextFactoryTest.java |[`cb1fddc`](../../commit/cb1fddc) |
| PAT-141 | 🐛 fix | 2026-04-29 | 後端（service/cql 模組 deep review P0+P1+P2 修補：doExecutePreTranslated DebugMap + 對齊 doExecute response shape + CqlTranslationService constructor injection + replaceFirst quoteReplacement + 註解 / log polish） | **cql module review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/service/cql/` (17 檔案、~4700 LOC) 稽核識別 1 個 P0 + 2 個 P1 + 2 個 P2 並全部修復。過濾掉 Explore agent 三個錯誤指控（unbounded `compiledLibraries` cache 增長 — 實際 LibraryManager 是 request-scoped 每次 `LibraryManagerFactory.createContext` 都建新 instance、`CqlTranslationService.compile()` 不 seed 會引入 BUG-107 — 該 method 全 codebase 零 production caller、library name regex injection 在 Pattern.quote 側 — 實際不安全的是 replacement string 那側，已併入 P1 #2）。**P0 #1 `CqlExecutionService.doExecutePreTranslated` 缺 DebugMap + 不曝露 errors/warnings**：對照 `doExecute` (line 375-379, 540-572 完整曝露) 標準作法，pre-translated path (line 871) `new CqlEngine(environment)` 後**沒** `engine.getState().setDebugMap(...)`，引擎 `shouldDebug()` 回 NONE，per-define 例外 silently 吞掉（PAT-066 invariant 在這條路徑沒守住）；response builder (line 942-948) 只帶 `.results(...)` `.metadata(...)`，沒 `.success(true)` `.errors(...)` `.warnings(...)` — 即使有 DebugMap，runtime errors 也不會傳到 caller。**`MeasureEvaluationService.executeAndAggregate` 用的就是 pre-translated path**（PAT-140 才強化的 errorCount，依賴 per-patient 的 evaluation result，但 per-define 內部錯誤完全不可見），所以這條觀測性破洞會吃掉整個 measure pipeline 的 debugability。修補：(1) 在 `new CqlEngine(...)` 後加 `DebugMap(loggingEnabled=true)` + `setDebugMap`；(2) 在 build response 前 harvest `engine.getState().getDebugResult()` 的 messages（mirror line 540-551）；(3) response builder 補 `.success(true).patientId(...).errors(...)`。**P1 #1 `CqlTranslationService` field @Autowired 違反 constructor injection 規範（line 30-40）**：原本 4 個 `@Autowired(required=false)` field（`CqlLibraryRepository` / `Timer` / 2 個 `Counter`）違反 CLAUDE.md 「禁止 @Autowired 在欄位上，用 `@RequiredArgsConstructor` + `final`」。改為 explicit constructor with `Optional<T>` 參數（4 個都是可選 bean），對齊 PAT-138 在 `CdsHooksService` 使用過的 pattern；2 個既有 test 檔（`CqlTranslationServiceUnitTest`、`CqlPipelineIntegrationTest`）改為傳 `Optional.empty()` 建構。**P1 #2 `CqlLibraryService.createVersion` `replaceFirst` replacement string 不安全（line 195-198）**：`replaceFirst(regex, "library " + name + " version '" + newVersion + "'")` — 第二個參數是 replacement string，`$` 與 `\` 有特殊意義。CQL 規範允許 quoted identifier (`library "MyLib$1" version ...`)，這時 `$1` 會被解讀為 backreference（regex 沒 group → 拋 `IllegalArgumentException`，使用者看到莫名 server 錯誤）。修補：包 `Matcher.quoteReplacement(...)` 把 `$` `\` 跳脫。regex 那側既有 `Pattern.quote(name)` 已正確。**P2 #1 `seedCompiledLibrary` (line 1132) sort 上方加 inline comment 解釋 binarySearch 不變式**：原本只有 javadoc 提到，但 sort 那一行是這個 BUG-107 守門員 method 最關鍵的 invariant — engine `Libraries.resolveExpressionRef` 用 binarySearch 找 expression，沒 pre-sort 就拋 `Could not resolve expression reference`。加 inline 註解讓未來 reviewer 在程式碼點擊看到。**P2 #2 batch fallback log 包含例外類型 + stack trace**：line 427、904 兩個 `log.warn("Batch CQL evaluation failed, falling back: {}", batchEx.getMessage())` 改為帶 `batchEx.getClass().getSimpleName()` + 把 batchEx 當第三個參數讓 logger 印 stack — debug 時看得到例外類型 / stack。**測試** — 新加 2 個 PAT-141 regression tests 在 `CqlExecutionIntegrationTest$PreTranslatedRuntimeErrorTests`：(1) `singleton from ({1, 2})` 在 pre-translated path 會 surface 在 `response.errors`（驗證 DebugMap + harvestErrors 兩條路徑都修對）；(2) response shape parity check (`success=true`、`patientId` 設好、`results` 完整) — 鎖住 doExecute / doExecutePreTranslated 模型一致性；BUG-107 既有 19 個 integration tests 零回歸（`LibraryResolutionRegressionTest` `BUG-111` `Diabetes Screening Golden` `Debug Mode` 等）；完整 backend test suite 全綠零回歸。**未在本 PR 範圍**：`CqlTranslationService.compile()` 零 production caller — 留作未來 cleanup；`DataRequirementExtractor` 1373 LOC 拆分屬架構重構，獨立 PR 處理；`CqlExecutionService` 1230 LOC 拆分同上。TFDA 追溯：需求 #447（安全性等級 A — surface BUG-110/111 家族隱形錯誤 + 對齊 constructor injection 規範 + 防 quoted-identifier replacement 字串特殊字元；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | backend/src/main/java/com/cqlplatform/service/cql/{CqlExecutionService,CqlLibraryService,CqlTranslationService}.java, backend/src/test/java/com/cqlplatform/service/cql/{CqlExecutionIntegrationTest,CqlPipelineIntegrationTest,CqlTranslationServiceUnitTest}.java |[`94ddea7`](../../commit/94ddea7) |
| PAT-140 | 🐛 fix | 2026-04-28 | 後端（service/measure 模組 deep review P0+P1+P2 修補：preCompileElm 拋例外 + partial-failure 閾值 + errorCount 曝露 + findFhirOutageCause 加深 + StratifierEvaluator 抽常數） | **measure module review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/service/measure/` (27 檔案、~7129 LOC) 稽核識別 1 個 P0 + 1 個 P1 + 2 個 P2 並全部修復；過程中過濾掉 Explore agent 兩個錯誤指控（`ModifierValueValidator` 套用到原始 CQL 文字 — 該 API 不存在、`StratifierEvaluator` 加 `@Transactional` — 純邏輯類別無 DB 存取，class javadoc 已聲明）。**P0 #1 `MeasureDefinitionService.preCompileElm()` 翻譯失敗靜默吞錯**：原本 `try { translate } catch { log.warn; return null; }` 把語法錯誤吞掉並把 null 寫進 `entity.elmJson`，使用者看到「儲存成功」但稍後跑評估會撞 cryptic「library not found / runtime translation」錯誤（BUG-110/111 家族）。改為翻譯失敗時拋 `CqlTranslationException`（既有的 GlobalExceptionHandler 映射到 HTTP 400 + 結構化 errors list），@Transactional 自然 roll back save。順手把 `create()` 也加上 preCompileElm（原本只有 `update()` 有，造成不一致），確保 CRUD 兩條路徑都在 save 時 surface CQL 錯誤。**P1 #1 部分失敗閾值 + errorCount 曝露**：PAT-112a 已對 FHIR outage 做了「partial scores are worse than no score」的 abort，但同樣論點對 non-FHIR 錯誤沒套用 — 50 個病人 5 個因 CQL runtime error 失敗 → 分母悄悄變 45 → 比例失真上報 P4P / QI。新加 `measure.error-threshold-ratio` 設定（預設 1.0 = 保留現行行為，營運可在 prod yml 調為例如 0.5），超過時 errorResult 取代 partial aggregate。同時把 `errorCount` + `evaluatedPatientCount` 加進 `MeasureEvaluationResult` 模型（含 4 個 buildResult* 路徑：proportion / cohort / CV / multi-group + 3 個 errorResult overload），讓 UI 能在 status=complete 時也警告「3/50 evaluation failed」。**P2 #1 `findFhirOutageCause` cause chain max depth 10 → 20**：CompletableFuture.supplyAsync + ExecutionException 包裝會多增 1–2 層深度，原 limit 10 在某些 wrapper 組合下會錯把 outage 當 generic per-patient error 處理；觸頂時新加 `log.warn` 讓 edge case 在 prod log surface（先前是靜默 return null）。**P2 #2 `StratifierEvaluator` 抽 population type 命名常數**：原本 `popEntry.getKey().toLowerCase().replace(" ", "-").replace("exclusions", "exclusion").replace("exceptions", "exception")` 是 ad-hoc 字串轉換 — magic 規則散在程式碼中，未來若新增 population type 容易漏改。新加 `PopulationTypeConstants.cqlNameToFhirCode(cqlName)` 顯式映射表（9 個 entry: Initial Population / Denominator / Denominator Exclusions / Denominator Exceptions / Numerator / Numerator Exclusions / Measure Population / Measure Population Exclusion / Measure Observation），fallback 保留 legacy `toLowerCase + " "→"-"` 對應 custom population names。**測試** — `MeasureDefinitionServiceTest` 22 → 27 cases（新加 5 個 PAT-140 cases：create invalid CQL throw / create valid CQL stores ELM / update changed CQL fail throws and rolls back / update unchanged CQL skips translation / preCompileElm wraps translator crash as CqlTranslationException）；`MeasureEvaluationServiceTest` 9 → 12 cases（新加 3 個 PAT-140 cases：partial failures surface errorCount / failures at threshold abort with structured error / zero failures errorCount=0）。完整 backend test suite 1435 → 1443 tests 全綠零回歸（1 既有 skip）。**未在本 PR 範圍**：`MeasureValidationService` 對原始 CQL 內容的額外驗證（既有 `translationService.translate()` 已是事實上的 syntax 驗證，再加 regex/whitelist 純 cargo-cult）。TFDA 追溯：需求 #445（安全性等級 A — surface 既有 BUG-110/111 家族 silent failure + 配置可調 partial-failure 閾值 + 移除 magic strings；不影響 CQL 翻譯/執行/measure evaluation 既有臨床行為） | backend/src/main/java/com/cqlplatform/service/measure/{MeasureDefinitionService,MeasureEvaluationService,StratifierEvaluator}.java, backend/src/main/java/com/cqlplatform/model/measure/{MeasureEvaluationResult,PopulationTypeConstants}.java, backend/src/test/java/com/cqlplatform/service/measure/{MeasureDefinitionServiceTest,MeasureEvaluationServiceTest}.java |[`50e2ad9`](../../commit/50e2ad9) |
| PAT-139 | 🐛 fix | 2026-04-28 | 後端（service/ecqm 模組 deep review P1+P2 修補：抽 ModifierValueValidator 共用 + readOnly Transactional + safeProperty validate-then-pass + EcqmArtifactService 補測試） | **eCQM module review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/service/ecqm/` (8 檔案、~75KB) 稽核識別 1 個 P1 + 3 個 P2 並全部修復。**P1 #1 抽 `ModifierValueValidator` 共用 utility + 套用到 ECQM**：PAT-137 在 `authoring.ExpressionTreeValidator` 加了 modifier `values` 白名單驗證（22 種 cqlTemplate 對應 7 種 FieldRule），但 `ecqm.EcqmExpressionTreeValidator` 是另一個 class（雖共用 templateService / modifierService，validation logic 沒共用），eCQM artifact 走 save → `EcqmCqlBuilder` 也使用相同的 `ExpressionCqlEngine.applyModifier` 把 modifier values 直接餵進 CQL，但 eCQM 的 validator **完全沒擋** modifier values 注入。把 PAT-137 的整套 logic 抽出到 `com.cqlplatform.validation.ModifierValueValidator`（public static `validate(modifier, path, errors)`），含 `FieldRule` enum、所有白名單與 regex、22 種 modifier kind 的 `MODIFIER_VALUE_RULES` 表。authoring 與 ecqm 兩個 validator 共用同一份 truth — 加新 modifier kind 只需改一處。**順手修 ecqm validator 的副作用**：原本 `validateNode` 對所有 entry 做 HTML escape 檢查，會把 `>=` / `<=` 之類的合法 operator 當 HTML markup 拒掉（之前沒踩到是因為 eCQM validator 本來沒有 modifier-values 測試）。把 `values` key 加進跳過 HTML 檢查的 set（同 `fields`）—  現在 `ModifierValueValidator` 的 whitelist+regex 已比 HTML escape 更嚴格，不會放鬆安全。**P2 #2 `EcqmExternalCqlLibraryService` 加 `@Transactional(readOnly=true)`**：原本 `listByArtifact` / `getByIdAndArtifactId` 沒有 `@Transactional` 標記，每次呼叫會在 lazy-loading 場景下產生自動 transaction 但無 readOnly 提示，HikariCP 拿到較重的 RW connection。對齊 PAT-137 留的 follow-up，補上明確 readOnly。**P2 #3 `EcqmCqlBuilder.safeProperty` 改 validate-then-pass**：原本 `property.replaceAll("[^a-zA-Z0-9.]", "")` 對任何 user 輸入直接 strip 非白名單字元，silently 把 `period.start` 變成 `periodstart` 或更糟、產出 `paramName.periodstart` 然後 CQL 翻譯失敗於遠處（看不出根因）。改為新加 `FHIR_PROPERTY_PATH = ^[A-Za-z][A-Za-z0-9]*(\\.[A-Za-z][A-Za-z0-9]*)*$` 嚴格 pattern，invalid 時 `ctx.warn(...)` 報出 user error 並 fall back 到 default (`period` / `value`) — 產出可解析的 CQL 同時把錯誤可見化（CLAUDE.md「validate at boundaries, surface errors」）。**P2 #4 `EcqmArtifactService` 補單元測試**：原本沒有 dedicated test class（只間接被 `EcqmControllerTest` 部分覆蓋）。新加 `EcqmArtifactServiceTest` 17 cases 覆蓋 `listByOwner` (mapped summary / empty) / `getById` (found / missing) / `create` (default 值 / custom 值) / `update` (partial fields 不覆蓋 sibling / list field 整批替換 / non-owner throw / not-found throw) / `delete` (owner OK / non-owner throw / not-found throw) / `duplicate` (Copy suffix + cmsMeasureId 清空 + status reset to draft / non-owner throw / list 是 copy 不是 reference / not-found throw)。Repository 走 Mockito mock，無 Spring context — 測試在 ~3 秒跑完。**測試** — `EcqmExpressionTreeValidatorTest` 從 23 cases 擴充到 39 cases（新加 6 個 PAT-139 cases：invalid comparison op / invalid unit / invalid datetime / valid canonical values / empty optional fields / unknown template & no values noop）；`EcqmArtifactServiceTest` 17 cases 全新；完整 backend test suite 1435 tests 全綠零回歸（1 既有 skip）。**未在本 PR 範圍**：`EcqmPublishService.toString()` 已查證所有 callsite 皆有 null guard（Explore agent 誤報，已驗證）；`EcqmCqlGenerationService` silent catch wrap-and-rethrow 為合理行為（不擋本 PR）。TFDA 追溯：需求 #443（安全性等級 A — defense-in-depth boundary 驗證 + 純 refactor + 補測試；不影響 CQL 翻譯/執行/measure evaluation 路徑） | backend/src/main/java/com/cqlplatform/validation/ModifierValueValidator.java（新）, backend/src/main/java/com/cqlplatform/service/authoring/ExpressionTreeValidator.java, backend/src/main/java/com/cqlplatform/service/ecqm/{EcqmExpressionTreeValidator,EcqmExternalCqlLibraryService,EcqmCqlBuilder}.java, backend/src/test/java/com/cqlplatform/service/ecqm/{EcqmExpressionTreeValidatorTest,EcqmArtifactServiceTest}.java |[`849f3b7`](../../commit/849f3b7) |
| PAT-138 | 🐛 fix | 2026-04-28 | 後端（service/cds 模組 deep review P0+P1 修補：field injection / ownership 守門 / sync DB 失敗 propagate / cache atomicity） | **backend service/cds review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/service/cds/` (13 檔案、~115KB) 稽核識別 4 個 P0/P1 並全部修復。**P0 #1 Field injection 8 處改建構子注入**：`CdsHooksService` 2 個 + `CdsInvocationService` 6 個 `@Autowired(required=false)` 違反 CLAUDE.md 「禁止 @Autowired 在欄位上」標準。改 `Optional<T>` final 欄位 + `@RequiredArgsConstructor` 自動產生建構子。Spring 對 `Optional<T>` bean 缺席時自動注入 `Optional.empty()`，達到 optional bean 語義同時保留靜態安全。`CdsInvocationService` 加 `stopTimerSample(Timer.Sample)` private helper 集中處理 timer 停止；`prefetchResolver.orElseThrow` 在 `resolvePrefetchTemplates`（caller 已 gate `isPresent`）；`feedbackRepository`/`cqlLibraryRepository` 改 `Optional` 後所有 `xxx == null` 改 `xxx.isEmpty()` 或 `xxx.ifPresent(...)` 鏈式。順手把 `CdsFeedbackTest` 的 reflection hack（`field.setAccessible(true); field.set(...)` 注入 mock）刪掉，直接用建構子傳 `Optional.of(mock)`。**P1 #2 Service-layer ownership 守門**：`CdsHooksService.updateService` / `deleteService` / `toggleServiceEnabled` 三個 mutator 沒有 service-layer ownership 檢查 — Controller 雖然有檢查（`!isAdmin() && !owner.equals(username) → 403`），但 service 自己暴露無守門 entry point 是 footgun，與 PAT-137 移除 `ExternalCqlLibraryService.deleteLibrary` 同 family。新加：`updateServiceIfOwnedBy(id, request, username, isAdmin)` / `deleteServiceIfOwnedBy(id, username, isAdmin)` / `toggleServiceEnabledIfOwnedBy(id, enabled, username, isAdmin)` + private helper `verifyOwnership(entity, username, isAdmin, action)` 違反時拋 `AccessDeniedException`（Spring Security 內建例外，已被 `GlobalExceptionHandler:136` 映射成 403）；legacy `ownerUsername == null` 視為系統擁有不擋（避免鎖死既有資料）。`CdsServiceConfigController` 的 update / delete / enable / disable handler 全改用新 IfOwnedBy 變體 — 移除原本 controller 自己重複的 `getService → checkOwner → action` 模板，每個 handler 從 ~15 行降到 ~7 行；非擁有者的 403 回應從 controller 自己組裝改為 service 拋 AccessDeniedException 由 GlobalExceptionHandler 統一處理。**P1 #3 syncCqlLibrary silent catch 改 propagate**：`CdsHooksService.syncCqlLibrary` 原本 `try { ... } catch (Exception e) { log.warn(...); }` 把所有失敗（DB 寫入失敗、constraint violation 等）吞掉。Service config 已寫入 cdsservice 表但 cql_library 表寫入失敗 → 後續使用該 service 時 library 找不到，使用者看不到根本原因。改：parse 失敗（CQL 沒有 `library X version 'Y'` 宣告）保留 log.warn + return（合法路徑：sandbox/test CQL 經常無 library 宣告），但 DB 操作失敗順著 `@Transactional` 自然回滾整個 createService/updateService — 使用者看到 service create 直接失敗，避免靜默不一致。Javadoc 詳細說明兩種失敗模式的不同處理。**P1 #4 serviceConfigs 多步 cache mutation 加 synchronized**：`createService` 的 `serviceConfigs.entrySet().removeIf(...)` 後接 `serviceConfigs.put(...)` 是兩步操作，雖 ConcurrentHashMap 個別操作 thread-safe 但組合不原子；並發 createService 對同 serviceName 可能在 cache 留 stale 條目（DB 是 source of truth 所以 server restart 後修正，但 in-flight 觀測仍會錯亂）。同樣 race 在 `updateService`（put-or-remove 二選一）/ `toggleServiceEnabled`（同上）/ `deleteService`（remove）。全部包進 `synchronized (serviceConfigs) { ... }`，確保 cache 狀態原子轉移。**測試** — `CdsServiceConfigControllerTest` 6 個測試改 mock 新 IfOwnedBy 方法（包括 `notOwner_shouldReturn403` 改成驗 service 拋 `AccessDeniedException` 經由 `GlobalExceptionHandler` 映射成 403，不是 controller 自己組裝）；`CdsHooksServiceTest` / `CdsServiceVersioningTest` / `CdsFeedbackTest` 建構子呼叫加 2 個 `Optional.empty()`；`CdsInvocationServiceTest` 加 6 個 `Optional.empty()` 並補 `java.util.Optional` import。完整 backend test suite 1412 tests 全綠零回歸（1 既有 skip）。**未在本 PR (P2 留待後續)**：`CdsInvocationService.invoke()` 152 行拆 helper / `CdsAnalyticsService.getAllServiceAnalytics` N+1 查詢批次化 / `CqlTupleCardStrategy` `getClass().getSimpleName().contains("Tuple")` 改 `CdsTupleAccessor.isTuple` / `PrefetchResolver` URL `UriComponentsBuilder` encode / `CdsRecentInvocationsService` 補測試 / `PrefetchResolver` 補測試 / `SandboxPresetService` ownership 測試 / 6 個 hardcoded English error messages 抽 constants — 都屬於品質改善，獨立 PR 處理。TFDA 追溯：需求 #441（安全性等級 A — P0 是規範一致性 + testability；P1 #2 是 defense-in-depth；P1 #3 把不一致變顯式錯誤；P1 #4 cache atomicity；都不影響 CQL 翻譯/執行/measure evaluation 路徑） | backend/src/main/java/com/cqlplatform/service/cds/{CdsHooksService,CdsInvocationService}.java, backend/src/main/java/com/cqlplatform/controller/CdsServiceConfigController.java, backend/src/test/java/com/cqlplatform/service/cds/{CdsHooksServiceTest,CdsInvocationServiceTest,CdsServiceVersioningTest,CdsFeedbackTest}.java, backend/src/test/java/com/cqlplatform/controller/CdsServiceConfigControllerTest.java |[`000e0c0`](../../commit/000e0c0) |
| PAT-137 | 🐛 fix | 2026-04-28 | 後端（service/authoring 模組 deep review P1 修補：modifier 白名單 + applyModifier polymorphic dispatch + 移除 unsafe overload） | **backend authoring service review-driven 修復批次** — 對 `backend/src/main/java/com/cqlplatform/service/authoring/` (14 檔案、~140KB) 稽核識別 3 個 P1 並全部修復。**P1 #1 `ExpressionTreeValidator` 補 modifier `values` 白名單驗證**：`ExpressionCqlEngine.applyModifier` 把 modifier `values` 直接餵進 `String.format("(%s) %s %s", ...)` 或 FreeMarker `${value}` 插值，無 escape；前端 dropdown 限制了 operator/numeric/qualifier，但繞過前端就能注入（CLAUDE.md 「validate at system boundaries」原則）。新加 `MODIFIER_VALUE_RULES` 表（cqlTemplate → field → FieldRule）含 7 種 rule（NUMERIC / COMPARISON_OP / UNIT / DATETIME / BOOLEAN_COMPARISON_VALUE / QUALIFIER_TYPE / FREE_TEXT_ESCAPED）；對應白名單 `COMPARISON_OPERATORS` (<, <=, >, >=, =, !=, <>) / `BOOLEAN_COMPARISON_VALUES` (is null, is not null, is true, ...) / `QUALIFIER_TYPES` (value set, code) / `NUMERIC_LITERAL_PATTERN` (`^-?\\d+(\\.\\d+)?$`) / `UNIT_PATTERN` (UCUM-ish, 拒 quote/backslash) / `DATETIME_PATTERN` (ISO date/datetime)。validator 對 22 種 modifier kind (BooleanComparison / ValueComparisonNumber / ValueComparisonObservation / ConvertUnits / WithUnit / LookBackModifier / EqualsString / StartsWithString / EndsWithString / BeforeTime[/Precise] / AfterTime[/Precise] / ContainsInteger / ContainsDecimal / ContainsQuantity / ContainsDateTime / Qualifier) 各自驗證對應的 value 欄位，empty optional 字串放行（engine 自然 skip），unknown cqlTemplate 不擋（engine 已有 warn 處理）。**P1 #2 `ExpressionCqlEngine.applyModifier` 改 polymorphic dispatch**：原本 224 行的 switch（25+ case branches）拆成 `Map<String, ModifierApplier>` registry + 12 個命名 private helper（`applyBooleanComparison` / `applyValueComparison` / `applyConvertUnits` / `applyWithUnit` / `applyLookBack` / `applyDuringMeasurementPeriod` / `applyStringMatch` / `applyTimeBound` / `applyContainsLiteral` / `applyContainsDateTime` / `applyIntervalBound` / `applyQualifier`）。功能等價 — 233 個既有測試（ExpressionCqlEngineTest 63 + EdgeCase 46 + CqlArtifactBuilderTest + EdgeCase）零回歸；`applyModifier` 主體從 224 行降到 ~25 行，加新 modifier 從「在 224 行 switch 找位置貼一段」變「在 buildModifierAppliers map.put 一筆 + 一個 private helper」，maintenance cost 大幅降低。BooleanExists 與 CheckExistence alias 仍正確處理（同一 lambda 註冊兩個 key）；BeforeTimePrecise/BeforeDateTimePrecise/AfterTimePrecise/AfterDateTimePrecise 共享 `applyTimeBound` (兩個 template file)；ValueComparisonNumber/Observation 共享 `applyValueComparison`；ContainsInteger/ContainsDecimal/ContainsQuantity 共享 `applyContainsLiteral` (withUnit 旗標切換)。Generic fallback (`{expression}` 替換 / `cqlLibFunc` 包 BaseModifier.ftl / 未知 template warn) 保留在 applyModifier 主體。**P1 #3 移除 `ExternalCqlLibraryService.deleteLibrary(Long id)` 裸 overload**：該 method 不檢查 ownership 直接 `repository.deleteById(id)`，雖無 controller 引用（所有 controller 都用 `deleteLibraryIfOwnedByArtifact`），但 `public` 仍是 footgun — 任何新加 caller 會繞過守門。直接刪除（不採 deprecate）並加 javadoc 說明只能用 ownership-scoped 變體。`grep` 確認 backend src 內零引用。**測試** — `ExpressionTreeValidatorTest` 從 16 cases 擴充到 26 cases（新加 10 個 PAT-137 cases：valid ValueComparisonNumber / invalid comparison op / non-numeric value / invalid unit with quote / invalid BooleanComparison / invalid qualifier / valid datetime / invalid datetime / empty optional fields pass / unknown template & no values map noop）。完整 backend test suite 1412 tests 全綠零回歸（1 既有 skip）。**未在本 PR 範圍**：`buildExpression` 110 行拆 helper、`formatParameterDefault` 122 行拆 helper、`ExternalCqlLibraryService` 的 `listByArtifact`/`getById` 加 `@Transactional(readOnly=true)`、5 個服務無單元測試（ArtifactTestingService / CqlImportService / CqlTemplateEngine / ExternalCqlLibraryService / TwcoreCatalogService）— 都是 P2 quality improvement，獨立 PR 處理。TFDA 追溯：需求 #439（安全性等級 A — defense-in-depth + 純 refactor + 移除無 caller method；不影響 CQL 翻譯/執行/measure evaluation 路徑） | backend/src/main/java/com/cqlplatform/service/authoring/{ExpressionTreeValidator,ExpressionCqlEngine,ExternalCqlLibraryService}.java, backend/src/test/java/com/cqlplatform/service/authoring/ExpressionTreeValidatorTest.java |[`e4f37bd`](../../commit/e4f37bd) |
| PAT-136 | 🐛 fix | 2026-04-28 | 前端（components/terminology 模組 deep review P1+P2 修補 + useCopyFeedback 抽出 + 測試） | **components/terminology 模組 review-driven 修復批次** — 對 `frontend/src/components/terminology/` (8 個元件、~52KB) 稽核識別 6 個 P1 + 1 個 P2。**P1 (1) CQL 輸出沒 escape — 拷貝出來的 CQL 會壞掉**：`ValueSetTab.handleCopyCql:91` 與 `CodeLookupTab.handleCopyCql:67-72`、line 195 inline copy button 都把 server 回傳的 `title`/`name`/`display`/`systemLabel` 直接黏到 `valueset "X": 'Y'` / `code "X": 'Y' from "Z"` 字串。Server 提供的 ValueSet 標題例如 `Diabetes "Type 2"` → 產出 `valueset "Diabetes "Type 2"": ...` 直接破解 CQL identifier；display/code 含撇號也會破 string literal。改為走 `escapeCqlIdentifier`（identifier 內處理 `\` + `"`）+ `escapeCqlString`（literal 內處理 `\` + `'`），與 PAT-126/127 builder/authoring 模組對齊。**(2) 5 處 `(error as Error).message` → `extractApiError`**：`ValueSetTab` (search + expand) / `CodeLookupTab` / `CodeValidationTab` / `DrawerCodeLookupPanel` 全部統一。axios 通常回 AxiosError 而非 Error，`.message` 訪問會 runtime crash 在某些 reject path。**(3) `DrawerValueSetPanel.handleToggle` 空 catch 顯示「沒概念」誤導**：原本 `try/await/catch {}` 完全吞 expansion 失敗，accordion 圖示已展開、Collapse 內容只顯示 "no codes"，使用者誤以為這個 ValueSet 真的沒概念。改為移除 try/catch（mutation 自帶 error state），在 Collapse 內加 `<Alert severity="error">` 顯示 `t('valueSet.expandFailed', { error })`。**(4) `expandMutation` 共用單一狀態造成 race**：`DrawerValueSetPanel` 與 `ValueSetTab` 都有同問題 — 快速點 ValueSet A → 還沒回 → 點 B：mutation.data 用 B 的請求覆蓋；若 A 比 B 先回（cache 命中）內容會閃過 A 然後變 B；若 B 失敗 A 已回，使用者看的是 A 的 codes 但 `expandedUrl` state 是 B → 渲染錯位。修補：用 `expandMutation.variables?.url === expandedUrl/selectedVs.url` 判斷 expansion 是否屬於目前選中的列；`showExpansion = false` 時不渲染 codes table 也不顯 error Alert，避免錯亂。**(5) `DrawerCodeLookupPanel.handleLookup` `system.includes(cs.url)` 啟發式錯誤**：若使用者 freeSolo 輸入的 URL **包含**已知 CS URL 為子字串，原 `find` 會錯選（例如 `https://my-extension/wraps/http://snomed.info/sct?v=2024` 被認成 SNOMED）。移除 heuristic — 直接傳 `system` verbatim；Autocomplete 已對 well-known systems 做精確 normalize（onChange 設為 `cs.url`）。**(6) 抽出 `useCopyFeedback` hook**：3 個 Drawer panel 都實作了相同的 `copiedCode` state + `copyTimerRef` + `setTimeout` 模式，**全部沒在 unmount 時 clearTimeout** — 使用者拷貝 → drawer 關閉 → 元件 unmount → timer 200ms 後 fire → setState on unmounted（React 18 warning + leak）。新 `hooks/useCopyFeedback.ts` 提供 `{ copiedKey, markCopied, isCopied }`，內部以 `useEffect` cleanup 清掉 pending timer。`DrawerCodeSearchPanel` / `DrawerValueSetPanel` / `DrawerCodeLookupPanel` 三個 panel 改用此 hook（移除 ~30 LOC 重複）。**P2 (7) `ValueSetTab` 漏接 local query error**：原 `useIgValueSets(...)` 只解構 `data, isLoading`、`searchError = remoteError` 只看 remote。`source === 'local'` / `'both'` 模式下 backend IG 服務失敗時使用者看到「沒結果」而非錯誤。改 `error: localError` + `searchError = remoteError ?? localError`。**附帶**：`DrawerValueSetPanel` `slice(0, 50)` 截斷無提示，加 `<Chip color="warning">` 顯示 "顯示前 50 / 共 N 筆"（i18n key `valueSet.previewTruncated`）。a11y: 3 個 Drawer panel 的 IconButton 補上 `aria-label`。**測試** — 1 個新 hook 測試 + 1 個新 utils 測試（共 15 cases，本機跑全綠）：`useCopyFeedback.test.tsx` (3 cases: timeout 後 clear / 第二次 markCopied 重設 timer / unmount 後不再 setState — 用 render-spy 計數驗證) / `cqlString.test.ts` (12 cases: escapeCqlString / escapeCqlIdentifier / formatFieldValue + ValueSetTab/CodeLookupTab CQL 輸出含 `"` 的 regression)。`tsc --noEmit` + `eslint src/components/terminology src/hooks/useCopyFeedback.ts` 零警告。**未在本 PR (品質改善留待後續)**：3 個 Drawer panel 的 useEffect/useState 仍可進一步抽 `useDrawerSearchState` 共用 hook、`CodeValidationTab` 沒驗證 valueSetUrl 為 URL、`DrawerCodeSearchPanel.useEffect [initialSystem, initialSearchText]` 只在 truthy 時 sync 的非對稱行為。TFDA 追溯：需求 #437（安全性等級 A — P1 屬純前端 UI 修補：CQL 輸出 escape 防止 user 拿到的 CQL 不能執行；race 修補避免顯示錯亂；不影響 CQL 翻譯/執行/measure evaluation 路徑） | frontend/src/components/terminology/{CodeLookupTab,CodeValidationTab,DrawerCodeLookupPanel,DrawerCodeSearchPanel,DrawerValueSetPanel,ValueSetTab}.tsx, frontend/src/hooks/useCopyFeedback.ts + \_\_tests\_\_/useCopyFeedback.test.tsx, frontend/src/utils/\_\_tests\_\_/cqlString.test.ts, frontend/src/locales/{en,zh-TW}/terminology.json |[`7b542c5`](../../commit/7b542c5) |
| PAT-135 | 🐛 fix | 2026-04-28 | 前端（components/patient-generator 模組 deep review P0+P1 修補 + 測試） | **components/patient-generator 模組 review-driven 修復批次** — 對 `frontend/src/components/patient-generator/` (7 個元件、~27KB) + `usePatientGenerator` hook + `PatientGeneratorPage` 稽核識別 1 個 P0（含 4 個子問題）+ 6 個 P1。**P0 (1) `GenerationResultPanel.handleUpload` 集合多項問題**：(a) **無 `isMountedRef`**：100 個病人序列上傳跑數十秒，使用者切回 Editor 後 await 後 setState 在 unmounted component；(b) **第一個病人失敗整批中斷且已上傳的不 rollback**：第 50 個病人卡住時前 49 個已寫入後端 DB；(c) **無進度顯示**：100 個病人時只看到 CircularProgress；(d) **沒 `fhirServer` 選擇**：硬性使用 backend 預設。修補：完整重寫 `handleUpload` — `isMountedRef` 守衛 setState；per-patient try/catch 收集 `failures: { patientId, error }[]` 不再整批中斷；新加 `<LinearProgress variant="determinate">` + 失敗清單 Alert；新加 `<FhirServerUrlField>` 讓使用者選上傳目標。最終結果用 `useNotification.showNotification` 三段式：全成功 / 部分失敗 / 全失敗。**P1 (2) `patientJsonCache` 主執行緒卡頓**：原 useMemo 對 N 個病人全部 stringify，100 個病人時 ~1MB 同步序列化 in render。改 lazy 序列化：Accordion 展開時才 ensureJson 該病人。**(3) `ResourceSelector.allCodes` selectAll 對 search 失效**：useMemo 用 `categories` 而非 `filteredCategories`，搜「diabetes」按 Select All 連高血壓也勾起來。改 `visibleCodes` 用 filtered；selectAll 改為**併集**（保留其他類別已選），deselectAll 改為**只清 visible**。**(4) `BatchGenerator.dateFrom > dateTo` 無驗證**：抽出 `isDateRangeReversed(from, to)` 到 `utils/dateDefaults.ts`（react-refresh 規範要求 helper 不從 .tsx 匯出）；`DateRangeFields` 反向時兩個 TextField 顯 error + helperText；`BatchGenerator` / `CustomGenerator` 的 Generate 按鈕 disabled 直到範圍正確。**(5) 上傳結果 local Alert → `useNotification`**：跟前面 PR 對齊，error 走 `extractApiError` 取代 `instanceof Error ? .message : String(err)`。**(6) 切 tab 清空已生成結果無警告**：在 `PatientGeneratorPage` 包一層 `confirmOverwrite` — 已有結果時 generate 之前 `window.confirm(t('overwriteConfirm', { count }))`，3 個 panel (Batch/Custom/Scenario) 的 onGenerate 都走這個 wrapper。**測試** — 1 個 hook 測試 + 1 個 util 擴充 + 1 個元件測試（共 11 cases，本機跑 15/15 綠）：`usePatientGenerator.test.ts` (4 cases: batch isGenerating 狀態切換 / custom numPatients=3 呼叫 3 次 / clearResults / download empty no-op) — **全綠**；`dateDefaults.test.ts` 擴充 `isDateRangeReversed` (3 cases) — **全綠**；`ResourceSelector.test.tsx` (3 cases: search 後 selectAll 只選 visible / search 後 deselectAll 只清 visible / 無 search selectAll 全選) — 本機 EMFILE 略過，CI Linux 會跑。`tsc --noEmit` + `eslint src/components/patient-generator src/pages/PatientGeneratorPage.tsx src/utils/dateDefaults.ts` 零警告。**未在本 PR (品質改善留待後續)**：BatchGenerator dispatch callback 每 render 重建、`SliderField` 空字串 input 直接 return UX 怪、`CustomGenerator` 沒「全部清空」按鈕、`ResourceSelector.handleToggle` 資料結構不一致 (Set vs Array filter)、上傳 cancellation token。TFDA 追溯：需求 #435（安全性等級 B — P0 (b) 部分失敗集合後端 DB 留下「殘骸」資料；P1 修補屬純前端 UX / race / 防禦式編程；不影響 CQL 翻譯/執行/measure evaluation 路徑） | frontend/src/components/patient-generator/{BatchGeneratorPanel,CustomGeneratorPanel,DateRangeFields,GenerationResultPanel,ResourceSelector}.tsx, frontend/src/components/patient-generator/\_\_tests\_\_/ResourceSelector.test.tsx, frontend/src/hooks/\_\_tests\_\_/usePatientGenerator.test.ts, frontend/src/pages/PatientGeneratorPage.tsx, frontend/src/utils/dateDefaults.ts + \_\_tests\_\_/dateDefaults.test.ts, frontend/src/locales/{en,zh-TW}/patientGenerator.json |[`4b50faa`](../../commit/4b50faa) |
| PAT-133 | 🐛 fix | 2026-04-28 | 前端（components/common 模組 deep review P0+P1 修補 + 測試） | **components/common 模組 review-driven 修復批次** — 對 `frontend/src/components/common/` (22 個元件、~85KB) 稽核識別 1 個 P0 + 6 個 P1。**P0 (1) PreferencesDialog API key state 在 dialog close 之間持久存在**：使用者在 VSAC / AI API key 欄位輸入後若沒儲存就關閉對話框，由於 `useState` 只在元件 unmount 才重置、而對話框是 `open` prop 控制的常駐元件，輸入的 key 字串會留在 React state 直到完全 unmount；下一個使用者開啟偏好設定時看到上一個人輸到一半的 key（共享工作站場景）。修補：`useEffect [open]` 中當 `open === false` 時清空 `vsacApiKey` / `aiApiKey` / `vsacMessage` / `aiMessage` / `showApiKey` / `showAiApiKey`。**P1 (2) PreferencesDialog post-await setState 缺 isMountedRef**：`handleSaveVsacKey` / `handleSaveAiKey` 在 `await settingsApi.update*ApiKey(...)` 後直接 setState，使用者中途關閉對話框會在 unmounted component 上 setState；初始 status fetch 也加 mount guard。**(3) LibraryPicker 連點 race + 無 isMountedRef + 空 catch**：`handleSelect` 沒 mount guard、catch 完全靜默（使用者點選不存在的 library 看不到任何錯誤）、連點不同 library 時若第一個比第二個慢回會用過時的 library 內容呼叫 `onSelect`。修補：`requestTokenRef` 自增式 token 過濾 stale 回應；catch 改 `showNotification('libraryPicker.loadFailed', 'error')`；加 `isMountedRef`。**(4) LibraryPicker search query 漏接 error**：原本只解構 `data` / `isFetching`，503 時使用者誤以為「無結果」。新加 `isError: searchError` + Alert (`libraryPicker.searchFailed`)。**(5) VersionCheckProvider invalidateQueries 範圍化**：偵測到後端版本變化時整 cache 一次失效，所有 active hooks 立刻重抓 → 對剛重啟還沒回穩的 backend 產生併發峰值。改為 `{ refetchType: 'none' }`：cache 仍標 stale，但只在 mount/focus 時才重抓。**(6) GlobalNotification 用 `index * 64` 個別定位 Snackbar**：陣列中段移除時下面通知 `index` 變化導致畫面跳動。整個 `<Snackbar autoHide>` 改為單一 `<Box position: fixed>` flex 容器 + `<NotificationItem>` 子元件（Slide transition + 自管 setTimeout 自動關閉）；items 自然 reflow 不再產生跳動。**(7) `editorTabSize` 型別安全**：`as number` 不安全的型別斷言改 `Number(e.target.value)`。**測試** — 3 個新檔：`PreferencesDialog.test.tsx` (2 cases: API key 關閉時清除 / save 在 unmount 後 resolve 不報錯) / `LibraryPicker.test.tsx` (3 cases: 連點 race / 失敗顯示 notification / search error 顯示 Alert) / `GlobalNotification.test.tsx` (1 case: 通知不再用 inline `top` style)；本機 vitest 遇 Windows EMFILE @mui/icons-material 已知問題，CI Linux 會跑。`tsc --noEmit` + `eslint src/components/common` 零警告。**未在本 PR**：抽出 `UCUM_UNITS` 到 `constants/ucumUnits.ts`、`PreferencesDialog` 350+ LOC 拆分子元件、`HelpTooltip.includes('.')` 啟發式判斷 i18n key、`DepartmentSelector` query error 顯示、`ErrorBoundary` telemetry hook、`resetPreferences` 加二次確認、其餘 10+ 元件補測試 — 屬於品質改善，獨立 PR 處理避免本批次膨脹。TFDA 追溯：需求 #431（安全性等級 B — P0 涉及共享工作站使用者 API key 隔離；其他改動屬純前端 race / a11y / UX 修補；不影響 CQL 翻譯/執行/measure evaluation 路徑） | frontend/src/components/common/{GlobalNotification,LibraryPicker,PreferencesDialog,VersionCheckProvider}.tsx, frontend/src/components/common/\_\_tests\_\_/{GlobalNotification,LibraryPicker,PreferencesDialog}.test.tsx, frontend/src/locales/{en,zh-TW}/editor.json |[`8bae252`](../../commit/8bae252) |
| PAT-132 | 🐛 fix | 2026-04-28 | 前端（CDS Hooks 模組 deep review P0+P1+P2 修補 + 測試） | **CDS Hooks 模組 review-driven 修復批次** — 對 `frontend/src/components/cds/` (9 個元件、~99KB) 稽核識別 1 個 P0 資料遺失 bug + 6 個 P1 + 4 個 P2。**P0 (1) Critical-card accept/override 從未送 feedback**：`InvokeServicePanel.handleAcceptCritical` / `handleOverrideCritical` 只是把 queue 推進，**不呼叫** `feedbackMutation`，而非 critical 卡片走的路徑會送（`handleAccept` / `handleOverrideSubmit`）。`overrideReason` 參數甚至已從 `CriticalCardDialog` 傳上來但用 `_reason` 直接丟掉。後果：CDS 分析報告對最高嚴重度的決策一律 0 計（feedbackAcceptedCount / feedbackOverriddenCount 系統性低估）。新增 `submitCriticalFeedback(uuid, outcome, reason?)` helper 走 `feedbackMutation.mutateAsync`，accept→`{ outcome: accepted }`、override→`{ outcome: overridden, overrideReason: { code: 'override', display: reason 或 default } }`。Sandbox 端的同名 handler 是 by-design（sandbox 是測試模式不送 feedback），加註解 explicit explain。**P1 (2) SandboxPanel JSON↔Builder 雙向同步 race**：原 `syncingRef` 在同一 effect body 內同步 set true→false，**對立的 effect 永遠看不到 true**（effects 在 render 之後才跑）。改用 `lastSyncSourceRef: 'builder' | 'json' | null` source-tag pattern：handleJsonChange/loadPrefetchIntoBuilder 寫前 tag 'json'，state.entries→JSON 的 effect 看到 'json' 就跳過一輪並清空 ref，避免使用者剛打的 JSON 被 builder 序列化覆蓋。**(3) localStorage key 加 user scope**：`cds-sandbox-draft` → `cds-sandbox-draft:${username}` (含 anonymous 後備)；保留 legacy key 第一次讀的後備並在 reset 時清掉，共享工作站不再洩漏前一個使用者的 test patient 草稿。**(4) ApiKeyManager 從 `window.confirm` 改 `ConfirmDeleteDialog`**：與其他模組一致，主題正確、a11y 正確（前端唯一還在用 native confirm 的位置），revoke 流程改 state-driven (`pendingRevokeId`)。**(5) `isMountedRef` post-await setState 守衛**：`InvokeServicePanel` (handleInvoke) / `SandboxPanel` (handleSandboxInvoke / handleSavePreset / handleDeletePreset) / `ApiKeyManager` (handleGenerate / handleConfirmRevoke) / `ManageServicesPanel` (handleSave / handleDelete / handleRollback / handleSaveCqlToService) — 配合 PAT-130 的 MeasureComparison 同 pattern；CDS 呼叫常 >5s（FHIR server 慢）切換 tab 中途 setState 不再洩漏。**(6) ManageServicesPanel.handleClose 清空表單狀態**：原本只 close + clear editingService，遺留的 `formData` / `formErrors` 在開下一個 dialog 第一輪 render 會閃舊資料。**(7) CQL editor overwrite 加 dirty-check**：`InvokeServicePanel` 用 `lastAutoLoadedCqlRef` 偵測未編輯狀態（empty / 等於上次 auto-load / 等於目標 service 的 cqlContent）才 overwrite，否則顯示 info Notification (`invoke.editorPreservedNotice`)；`ManageServicesPanel.handleSelectService` 切換不同 service 且 editor 有未存編輯時 `window.confirm(switchServiceConfirm)`，且 `handleSaveCqlToService` 成功後重設 `lastLoadedCqlRef` 避免下次切換誤觸發。**P2 (8) CriticalCardDialog 改 `onClose(_, reason)` reason 檢查**：移除 `slotProps={{ backdrop: { onClick: e => e.preventDefault() } }}` hack，改 MUI v5 慣用作法（`disableEscapeKeyDown` + `onClose` 對 `backdropClick` early-return）。**(9) ApiKeyManager `getStoredUsername()` 加 `useMemo`**：每次 render 重讀 localStorage + JSON.parse 浪費。**(10) CdsPanel 失去 admin role 時重置 tabValue**：admin 在 Recent Invocations tab (idx=5) 時 token 輪替剝掉 ADMIN，tab 隱藏但 tabValue 留在 5 → MUI Tabs warning + 空白區。`useEffect` 偵測 `!isAdmin && tabValue >= 5 → setTabValue(0)`。**(11) 雜項清理**：`InvokeServicePanel.services.filter(s=>s.id===id).map(...)` 改 `services.find()`；`link.url.startsWith('http')` 改 `new URL(raw).protocol === 'http:' \|\| 'https:'` 防 `javascript:` / `data:` 注入；`RecentInvocationsPanel.refetchInterval: 10_000` 改 `REFETCH_10S` (新加進 `constants/queryConstants.ts`)；`handleInvoke` 開頭 `setCdsResponse(null)` 跟 SandboxPanel 對齊清掉 stale 卡片。**測試** — 3 個新檔：`InvokeServicePanel.test.tsx` (3 cases: accept→送 accepted feedback / override→送 overridden + reason / blank reason 時 submit 按鈕 disabled) / `ApiKeyManager.test.tsx` (3 cases: revoke 開 ConfirmDeleteDialog / 未確認前不 mutate / 確認後 mutate(id)) / `CdsPanel.test.tsx` 改寫 (2 cases: ADMIN 顯示 tabRecent / USER 隱藏)；本機 vitest 遇 Windows EMFILE @mui/icons-material barrel 已知問題（CLAUDE.md 已記錄），CI Linux 會跑。`tsc --noEmit` + `eslint src/components/cds` 零 warning。**未在本 PR**：抽出 `<CdsCardList>` 共用元件（Invoke + Sandbox 重複 ~150 LOC card 渲染）— 屬於較大 refactor，獨立 PR 處理避免本批次膨脹。TFDA 追溯：需求 #429（安全性等級 B — P0 影響 CDS 分析統計準確度，與臨床決策追蹤審計相關；不影響 CQL 翻譯/執行/measure evaluation 路徑） | frontend/src/components/cds/{ApiKeyManager,CdsPanel,CriticalCardDialog,InvokeServicePanel,ManageServicesPanel,RecentInvocationsPanel,SandboxPanel}.tsx, frontend/src/components/cds/\_\_tests\_\_/{ApiKeyManager,CdsPanel,InvokeServicePanel}.test.tsx, frontend/src/constants/queryConstants.ts, frontend/src/locales/{en,zh-TW}/cds.json |[`94143ca`](../../commit/94143ca) |
| PAT-131 | 🐛 fix | 2026-04-28 | 前端（testcase-builder 視覺化建構器 deep review + 共用工具抽出 + 測試補齊） | **testcase-builder review-driven 修復批次** — 對 `frontend/src/components/testcase-builder/` (22 個元件、~85KB) 稽核識別 1 個 high-severity correctness bug + 5 類 high-severity 修補。**High (1) ResourceForm choice-type cleanup prefix-match 誤刪兄弟欄位**：原 `handleFieldChange` 對 `choiceFieldName` 切換時用 `key.startsWith(baseName) && /[A-Z]/.test(key[baseName.length])` 比對要刪的舊 variant，會把共享前綴但非 choice variant 的兄弟欄位（例如 `valueQuantity` + `valueSet`）一起誤刪。改為新工具 `listChoiceFieldNames(baseName, choiceTypes)` 精確列出 metadata 已知 variant 才刪，並用 `choiceVariantToBaseName` Map 反查確保切到的 variant 屬於正確的 base element（即使未來新增非 choice 的 sibling 也安全）。**(2) ChoiceTypeField 空 choiceTypes silent fallback**：metadata 漏 choiceTypes 時 selectedType 會初始化為空字串、ElementField 走 PrimitiveField 分支但 `type=''`，使用者輸入完全寫不回。早返回顯示 warning Alert (`testCaseBuilder.choiceType.noChoices`)。**(3) ElementField depth>=3 Monaco fallback silent JSON parse swallow**：原本 try/catch 完全吞掉 SyntaxError，使用者打字看不到錯誤就以為已存。新增 `parseError` state，error 時顯示 `severity='error'` Alert (`testCaseBuilder.fields.jsonParseError`)；空字串視為 undefined（清欄位）；成功時清掉 error。**(4) Code* 三處 React Query 漏接 error**：`CodeableConceptField`/`CodeField`/`TwcoreCodePicker` 原本只解構 `data`，丟棄 `error`/`isError`，後端搜 code/terminology 失敗時 UI 顯示空白看不出區別。三個都加上 Alert 顯示 `error.message`。**(5) 多處盲轉型 (value as X)**：`CodeableConceptField`/`IdentifierField`/`ReferenceField`/`HumanNameField`/`PeriodField`/`QuantityField`/`ContactPointField`/`GenericComplexField` 一律 `(value as X) || {}`，malformed bundle 會 runtime crash。新增 `utils/fhirGuards.ts` (`isPlainObject`/`asObject`/`asStringArray`)；HumanName 的 `given` 額外 `asStringArray` guard。**(6) ElementField line 184 strict-null TS 隱性 bug**：`element.children?.length > 0` 在 strict 模式下 `undefined > 0 = false` 雖正確但脆弱，改 `(element.children?.length ?? 0) > 0`。**架構抽出**：`utils/fhirChoice.ts` (`buildChoiceFieldName`/`detectChoiceType`/`listChoiceFieldNames` — 原本 5 個地方各自實作 `name + ct.charAt(0).toUpperCase() + ct.slice(1)`)；`utils/fhirDefaults.ts` (`getDefaultValue` — 從 `ElementField` 抽出)；`utils/fhirGuards.ts`；`constants/fhirTypes.ts` (`QUANTITY_TYPES` set: Quantity/SimpleQuantity/Age/Duration/Distance/Count)；`constants/fhirExtensions.ts` (`TW_ADDRESS_EXT_BASE` — 從 AddressField 寫死的 URL 抽出)；`config/twcore/identifierPresets.ts` (`TW_IDENTIFIER_PRESETS` — 4 個 TW Core 識別碼預設從 IdentifierField 抽出，含 hintKey 對應 i18n)。**i18n / a11y**：(7) ElementField hardcoded `(JSON)` → `testCaseBuilder.fields.jsonSuffix`；(8) `FieldWrapper` 必填 `*` 加 `aria-label={t('testCaseBuilder.requiredAria')}`；(9) `ResourceEntryList` 截斷的 resource ID 加 `<Tooltip>`；(10) `TwcoreCodePicker.use` 按鈕加 `aria-label={t('testCaseBuilder.twcore.useAria', { code })}`；(11) `ResourceForm` Add Attribute 按鈕加 `aria-label={t('testCaseBuilder.addAttributeAria', { count })}`；(12) `ResourceForm` 加 `isError` Alert (`testCaseBuilder.metadataLoadError`)。**效能**：`CodeableConceptField` hoisted 10 個 inline `sx` 物件到 module-level constants；`CodeField.renderOption` 加 `useCallback`；`ResourceForm` 用 `useMemo` 預建 choice variant→base name lookup map（每次切換型別不再重掃 elements）。**i18n keys** (en/zh-TW 同步新增 11 個): `addAttributeAria`/`metadataLoadError`/`requiredAria`/`twcore.{loadError,useAria}`/`fields.{selectCode,noCodings,codeSearchError,jsonSuffix,jsonParseError}`/`choiceType.noChoices`。**測試** — 3 個新 `utils/__tests__/`：`fhirChoice.test.ts` (10 cases: buildChoiceFieldName/detectChoiceType/listChoiceFieldNames + ResourceForm cleanup pattern regression 2 cases) / `fhirDefaults.test.ts` (16 cases, 含 strict-null regression) / `fhirGuards.test.ts` (7 cases)；2 個新 `testcase-builder/__tests__/`：`ChoiceTypeField.test.tsx` (5 cases: 預設 type / initialChoiceType prop / 空 choiceTypes Alert / undefined choiceTypes / 換 variant 清值) / `ElementField.test.tsx` (6 cases: depth 3 渲染 Monaco / 解析 JSON onChange / 空字串 → undefined / parseError surface / 修好後清 Alert / depth 2 不渲染 Monaco)。共 44 cases 全綠。`tsc --noEmit` 零 error。**未在本 PR**：ResourceForm 整合測試嘗試但 Windows EMFILE @mui/icons-material barrel + vi.mock 互動使 vitest 集合期 hang，已移除；對應 high-severity prefix-bug 改在 `fhirChoice.test.ts` 的 `choice-type cleanup pattern (regression for ResourceForm)` describe block 直接測 helper 行為（2 cases），covers 一致。Constants.tsx 35 entries `RESOURCE_ICONS` 字典之拆 lazy（未來改善項，本 PR 範圍）。TFDA 追溯：需求 #427（安全性等級 A — UI 一致性與資料保護；不影響 CQL 翻譯/執行/measure evaluation 臨床路徑） | frontend/src/components/testcase-builder/{AddressField,ChoiceTypeField,CodeField,CodeableConceptField,ContactPointField,ElementField,FieldWrapper,GenericComplexField,HumanNameField,IdentifierField,PeriodField,QuantityField,ReferenceField,ResourceEntryList,ResourceForm,TwcoreCodePicker}.tsx, frontend/src/components/testcase-builder/\_\_tests\_\_/{ChoiceTypeField,ElementField}.test.tsx, frontend/src/utils/{fhirChoice,fhirDefaults,fhirGuards}.ts, frontend/src/utils/\_\_tests\_\_/{fhirChoice,fhirDefaults,fhirGuards}.test.ts, frontend/src/constants/{fhirTypes,fhirExtensions}.ts, frontend/src/config/twcore/identifierPresets.ts, frontend/src/locales/{en,zh-TW}/measures.json |[`77bc80e`](../../commit/77bc80e) |
| PAT-130 | 🐛 fix | 2026-04-27 | 前端（measure 模組 deep review P1+P2 + 測試補齊） | **measure 模組 deep review 修復批次** — 對 \`frontend/src/components/measure/\` (~9000 LOC, 31 components) 稽核後修復 P1+P2 共 5 個問題並補 3 個測試檔。**P1 (1) MeasureComparison live-eval unmount cleanup**：原 `runLiveCompare` / `runLiveTrend` 跑完後直接 setState，使用者切走頁面會 setState on unmounted component。新加 `isMountedRef` + cleanup effect，每個 await 後檢查 mount 狀態，未掛載則 early return。順手修復 catch 區塊的 `slotFailed` 訊息原本用 stale `liveTrendStep` state（state 還沒 propagate），改成 local `lastSlotIdx` 變數。**(2) MeasureEditor 工作流按鈕邏輯統一**：原 lock/unlock 用 inline `mutate({ onSuccess, onError })`，submit/approve/retire 走 `handleWorkflowAction`，兩套各有 isPending 守門 + 錯誤處理（PAT-117 regression 風險的根源）。新加 `runWorkflowMutation(mutation, successMsg | null, errorFallback?)` 統一 helper，successMsg=null 表示 quiet success（lock/unlock）、successMsg 字串表示顯示 alert（submit/approve）；舊 `handleWorkflowAction` 改為 thin wrapper 委派給 `runWorkflowMutation`。lock/unlock onClick 改為一行呼叫 `runWorkflowMutation`。所有 6 個 workflow button 現在共用同一條錯誤處理 / invalidate / alert 路徑。**P2 (3) PopulationCriteriaTab `autoMapDoneRef` 永久 latch**：原 boolean ref 一旦 set true 就永遠不再嘗試 auto-map，使用者首次開啟時 CQL 還沒翻譯（expressionNames=[]）會跳過、後來增加新 CQL define 時 auto-map 也不再運作。改為 `autoMapAttemptedNamesRef` 存「已嘗試過的 expression 名稱集合 sorted-join key」，effect 在 key 改變時才重跑；anyFilled 時記錄 key 但不執行（尊重既有手動映射）。(4) **MeasureReportHistory 刪除確認**：原點 trash icon 直接 mutate，使用者誤點丟失歷史報告。新加 `pendingDelete` state + MUI Dialog with Cancel / Delete buttons；Cancel 不刪、Delete 才執行 mutation；i18n 雙語加 `reports.{deleteConfirmTitle, deleteConfirmBody, cancel, confirmDelete, deleting}` 5 個 key（其中 deleteConfirmBody 帶 measure name + period 動態插值）。(5) **MeasureComparison staleTime**：原 `staleTime: Infinity` 讓 measures list 永遠不刷新，user 同 session share/rename 後同 tab 看到舊版。改為 `5 * 60 * 1000` (5 分鐘)。**測試 3 個新 \`__tests__\`** — `MeasureEditor.test.tsx` (4 cases: Lock 觸發 mutation+update / Unlock quiet success 不顯示 alert / Submit-for-review 顯示 alert / Lock onError 顯示 API error message)；`MeasureComparison.test.tsx` (2 cases: unmount 期間 resolved promise 不觸發第二次 evaluate 或 comparePeriods / staleTime 不再 Infinity 的 sanity check)；`MeasureReportHistory.test.tsx` (3 cases: trash icon 開 dialog 不刪 / Cancel 不刪 / 確認才實際刪除)。順手修 \`testcase-builder/__tests__/ElementField.test.tsx\` 一個 unused import lint warning（pre-existing main 上的問題阻擋本 PR CI，未在審查範圍但必須清掉才能過 max-warnings 0）。Local vitest 遇 Windows EMFILE @mui/icons-material 已知問題，CI Linux 會跑。`tsc --noEmit` + `eslint` 零警告。**未在本 PR 範圍**：PopulationCriteriaTab 1055 行拆 4 子 component（架構重構，留另一 PR）；MeasureLibrary 70+ state vars 拆 MeasureTable / dialogs（同樣留另 PR）；MeasureValidationPanel 過期 groupedIssues。TFDA 追溯：需求 #425（安全性等級 A — UI 一致性與資料保護；不影響 CDS / measure evaluation 臨床路徑） | frontend/src/components/measure/{MeasureComparison,MeasureEditor,MeasureReportHistory,PopulationCriteriaTab}.tsx, frontend/src/components/measure/\_\_tests\_\_/{MeasureComparison,MeasureEditor,MeasureReportHistory}.test.tsx, frontend/src/components/testcase-builder/\_\_tests\_\_/ElementField.test.tsx, frontend/src/locales/{en,zh-TW}/measures.json |[`d0dce3f`](../../commit/d0dce3f) |
| PAT-129 | 🐛 fix | 2026-04-27 | 前端（eCQM 模組 deep review P1+P2 + 測試補齊） | **eCQM 模組 deep review 修復批次** — 對 `frontend/src/components/ecqm/` (13 components, ~1700 LOC) 稽核後修復 P1+P2 共 7 個問題並補 4 個測試檔。**P1 (1) Stratifier ↔ Ratio dual-IP 互鎖**：CMS 規則禁止 Ratio + dual-IP 時掛 stratifier，UI 完全沒擋 → 後端默默拒絕。`EcqmArtifactWorkspace` 加 `useMemo` 計算 `stratifierDisabledReason`（scoringType==='ratio' && 任一 group 同時有 initialPopulationDenom + initialPopulationNumer）→ 傳入新 prop。`EcqmStratifiersTab` 加 `disabledReason?` prop，set 時頂部 warning Alert + Add 停用 + 各 row 的 Delete / TextField / 內嵌 tree editor 全停（`pointerEvents: 'none'`），既存資料**保留**讓使用者關掉 dual-IP 後 stratifier 重現。**(2) CqlPreviewTab 快取失效**：原 cql 存 useState 從不 invalidate，編輯 artifact 後切回此分頁仍顯示舊 CQL。新 `artifactUpdatedAt?` prop + `generatedAt` state；不一致則 `isStale=true`，頂部 warning Alert + Validate / Publish 停用 + CQL 文字塊 `opacity: 0.7`。**(3) handleSaveAndLeave double-flush**：原邏輯不檢查 `updateMutation.isPending` → 快速操作雙開 mutation 互蓋。改為先檢查 isPending，in-flight 時設 `pendingLeaveRef = true` 並等 onSuccess 才 onBack；統一走 `save()` 的 onSuccess 處理導航。**(4) saveStatus race**：`save()` onSuccess 原本無條件 `setSaveStatus('saved')`，把 mutation 期間 user 新輸入觸發的 'dirty' 蓋掉。改為僅 `pendingRef === null` 才設 'saved'；onError 清空 pendingLeaveRef 避免錯誤狀態下還跳走。**P2 (5) ObservationEditor 驗證**：Percentile 缺百分位數值 / `populationRef` 為空可 silently 存。新加 conditional percentileValue TextField（type=number, 0-100），缺值或越界顯示 error helperText；populationRef 為空也顯示 required helperText。type 加 `percentileValue?: number`。**(6) IconButton aria-labels**：補上 4 處（Header BackIcon / Stratifiers Add+Delete / SDE Delete / Observation Delete），對應 i18n key 雙語同步。**(7) useUnsavedChangesGuard SPA nav**：原僅 hook beforeunload，漏接 SPA back/forward。新加第二層 useEffect 監聽 popstate，dirty 時 `window.confirm` 詢問，使用者選留下則 pushState 把當前 URL 重推回。`BrowserRouter` 不支援 useBlocker (data router only)，這是務實折衷。**測試** 4 個新 \`__tests__\` — `EcqmStratifiersTab.test.tsx` 3 cases / `EcqmCqlPreviewTab.test.tsx` 2 cases / `EcqmObservationEditor.test.tsx` 4 cases / `EcqmArtifactWorkspace.test.tsx` 5 cases，共 14 cases 涵蓋全部 P1+P2 行為。Local vitest 遇 Windows EMFILE @mui/icons-material 已知問題，CI Linux 會跑。`tsc --noEmit` + `eslint` 零警告。TFDA 追溯：需求 #423（安全性等級 A — UI 一致性與資料保護；不影響 CDS / measure evaluation 臨床路徑） | frontend/src/components/ecqm/{EcqmArtifactWorkspace,EcqmArtifactWorkspaceHeader,EcqmCqlPreviewTab,EcqmObservationEditor,EcqmSdeTab,EcqmStratifiersTab}.tsx, frontend/src/components/ecqm/\_\_tests\_\_/{EcqmArtifactWorkspace,EcqmCqlPreviewTab,EcqmObservationEditor,EcqmStratifiersTab}.test.tsx, frontend/src/hooks/useUnsavedChangesGuard.ts, frontend/src/locales/{en,zh-TW}/ecqm.json, frontend/src/types/ecqm.ts |[`b9d1cc4`](../../commit/b9d1cc4) |
| PAT-128 | 🐛 fix | 2026-04-27 | 前端（PAT-127 deferred 3 項 perf / race 修補） | **CDS Authoring deferred follow-ups** — 完成 PAT-127 review 中因 PR 範圍考量延後的 3 個 medium 項目。**(#26)** `ConjunctionGroup` 大型 tree 搜尋延遲：`localSearch` 套 `useDebouncedValue(_, SEARCH_DEBOUNCE_GENERAL_MS=300ms)`，遞迴 `elementMatchesFilter` 不再每 keystroke 對所有 descendant 跑一次。**(#24)** `CqlPreviewPanel` validate→generate race：`handleValidate`/`handleGenerate`/`handleFormat` 從 `mutate({ onSuccess })` 改 `mutateAsync` + `isPending` guard。原本：使用者連按 Validate 兩次會 race，第一次的 onSuccess 把 setValidation 寫進去，第二次又覆蓋；如果第一次的 chain (validate→generate) 還沒結束，使用者離開 tab 就會在 unmounted component 上 setState。改後：duplicate click 被 isPending 擋掉，`await` 的線性流程確保 setState 順序，且任一錯誤被吞進 catch 由 mutation.error UI 顯示。**(#25)** `ArtifactElement` `memo()` 失效：原本 `ConjunctionGroup` 對每個 child 傳 `() => onRemoveElement(child.uniqueId)` 等 inline arrow → 每次 render 都產生新的函式 ref → memo 永遠 fail → 整顆 tree 隨任何兄弟節點輸入重 render。改 `ArtifactElement` props 為 `onUpdate(uniqueId, updates)`/`onRemove(uniqueId)`/`onIndent(uniqueId)`/`onOutdent(element)`，內部 bind 到 `element.uniqueId`/`element`；`ConjunctionGroup` 直接傳 useCallback 化的 `handleIndent`/`onUpdateElement`/`onRemoveElement`/`onOutdentElement`（這些已經是 useCallback 來自 `ArtifactWorkspace`），終於 ref-stable，memo 擋下兄弟節點的 re-render。對大型 tree（>30 個 element）每 keystroke render 量從 O(n) 降到 O(1)。**驗證**：`npx tsc --noEmit` 零 error；`npx eslint src/components/authoring/builder/{ConjunctionGroup,ArtifactElement}.tsx src/components/authoring/cql-preview/CqlPreviewPanel.tsx` 零 warning。TFDA 追溯：需求 #421（安全性等級 A — 純前端 perf / UX 修補；不影響 CQL 翻譯、執行、measure evaluation） | frontend/src/components/authoring/builder/{ArtifactElement,ConjunctionGroup}.tsx, frontend/src/components/authoring/cql-preview/CqlPreviewPanel.tsx |[`f1b5076`](../../commit/f1b5076) |
| PAT-127 | 🐛 fix | 2026-04-27 | 前端（CDS Authoring 33 個元件深度 review 修補） | **CDS Authoring review-driven 修復批次** — ultrareview 對 `frontend/src/components/authoring/` 識別 30 個 bug（3 blocker / 16 high / 11 medium）。延續 PAT-126 方法論；共同主題是 sister UI 把同一批 CQL escape gap、Monaco theme 全域副作用、i18n 持久化污染原樣複製過來了。**Blocker (3)**：(1) `builder/CustomModifierBuilder.buildRuleClause` 12 個 operator 全部 user input 直拼 `'${rule.value}'` / `in "${rule.value}"` / `before @${rule.value}` 沒 escape — `Crohn's` 等含撇號值會破壞 CQL；改用 PAT-126 加好的 `escapeCqlString` / `escapeCqlIdentifier`；`accessor` 路徑分段檢查只准 `[A-Za-z_][A-Za-z0-9_]*`；`before/after` 加 ISO 日期 regex 拒絕非法日期；`within_last` 限定 `year/month/week/day/hour` 白名單。(2) `query-builder/QueryBuilder` 同型 escape gap 整套修補（valueSetName / c.value / c.property / in"X" / formatValue 數值布林型別額外驗證）。(3) `ArtifactWorkspaceHeader.handleSave` request **漏 `lockVersion`** — workspace 寫好的 409 conflict-dialog 從這顆主要 Save 按鈕**永遠觸發不了**，並發編輯會靜默互覆；補回。**High (16)**：(4) `ArtifactWorkspace` TabStatusInfo 從 `${key}||${json}` 字串改 `{ key, params }` 結構，移除 render-time `JSON.parse` 對 user 輸入崩潰的風險（含 `||`/`"` 的 element/parameter name 會讓 tab tooltip 直接爆）；(5) `cql-preview/CqlPreviewPanel` 加模組層級 `lastAppliedTheme` + `cqlLanguageRegistered` guard（同 PAT-126 `builder/CqlPreviewBox` 模式）防 main editor 每次 keystroke 重繪；(6) `base-elements/ArithmeticElement` 名稱用 `escapeCqlIdentifier`，literal 加 `NUMERIC_LITERAL_RE` 驗證；(7) Conflict-dialog Reload 加 try/catch + 把 localArtifact 寫入 `localStorage[authoring-recovery:${id}]` recovery key + 失敗顯示 Alert（refetch 失敗時不再靜默丟失 30 分鐘工作）；(8) Tab keyboard shortcut Ctrl+9 改對 Testing tab（新增 `TAB_INDEX_TESTING` 常數，更新 KEYBOARD_SHORTCUTS 文案使 Testing 不再 unreachable）；(9-15) **持久化 i18n 污染**家族——之前所有 zh-TW 用戶看到並寫進 DB 的英文預設值都改 i18n：`description: 'Imported from CQL: ${name}'` / `Subpopulation 1` / `Base Element 1` / `Calculated Value 1` / `Custom Filter` modifier name / `Library:`/`Version:`/`FHIR:` chips / `Grade A` chip / `if`/`else if`/`else` / `Recommendation` indicator label；(11-14) **顯示 i18n 漏洞**：Header `R4 (4.0.1)` 寫死 → 讀 `artifact.fhirVersion`；`ArtifactElementBody.DEMOGRAPHIC_SELECT_OPTIONS` 寫死 Year/Male/Female/... → 純 value table + `t(\`elementBody.demographics.${value}\`)`；`ModifierCard.BOOL_COMPARISON_OPTIONS` 寫死 `Is Null` / `Is True` 與 `'Op'` placeholder → i18n labelKey；`ChooseCodeDialog` `'Other'` / `'selected system'` fallback → i18n；(17) `ElementSelectDropdown` 35 entries `ELEMENT_DESCRIPTIONS` 從 JS module-level 字典移到 en + zh-TW locale 檔（補 22 個 TWCORE 翻譯），完全移除 JS fallback；(18) `builder/ExpressionPhrase` 寫死 `<span> and </span>` 連接詞 → `t('expression.andConnective')`；`phraseToString` 用 `ageBetween` 整段帶參數的 i18n key；(19) `ArtifactList` 新增 `compareSemver()` 取代 `version.localeCompare()`（修 `1.10.0 < 1.2.0` lexical bug）；virtualized row 從 `<Table><TableBody><TableRow>` 改 flex `<Box>` 解決每列獨立 layout root 的無效 HTML，匹配 header table 的 column widths。**Medium (10/11 完成)**：(20) `pushState(localArtifact)` 移到 `setLocalArtifact` updater 之外（StrictMode/concurrent rendering 下不再 push 重複 history）；(21) `handleSave` 加 `if (updateMutation.isPending) return` 防雙送與 response-overwrites-edits race；(22) `useTemplates`/`useModifiers`/`useExternalCqlList` 解構 `error`，workspace 顯示 `dataLoadFailed` Alert（不再靜默變空陣列讓主編輯面莫名為空）；(27) ImportCqlDialog 加 5MB size limit + 拒絕 alert（不再 readAsText 100MB 檔案）；(28) **驗證** `generateId()` 使用 `crypto.randomUUID()`（非 module counter，無 ID 衝突風險）；(29-30) `ArtifactSummaryView` 用 `ERROR_CONDITION_VALUE_TO_KEY` (新加進 `constants/authoringConstants`) 從 stable wire `value` 即時 lookup label，避免 i18n 切換後 summary 顯示舊翻譯。**Deferred / 不在本 PR**：#24 CqlPreviewPanel validate→generate 連環 mutation race（需 `mutateAsync` 重構）、#25 `ArtifactElement` memo 失效（需 `ConjunctionGroup` 級 callback 緩存）、#26 `ConjunctionGroup` search debounce（minor perf）。**驗證**：`npx tsc --noEmit` 零 error；`npx eslint src/components/authoring src/utils/cqlString.ts src/constants/authoringConstants.ts` 零 warning（清掉一個 react-refresh 違規透過把 `ERROR_CONDITION_VALUE_TO_KEY` 從 `.tsx` 移到 `constants/authoringConstants.ts` 解決）。**i18n**：en + zh-TW 兩邊新增約 50 keys 跨 `subpopulations.defaultName` / `baseElements.{defaultName,calculatedDefaultName}` / `customModifier.{defaultName,ops.*}` / `summary.{ifLabel,elseIfLabel,elseLabel,gradeLabel}` / `importCql.{descriptionDefault,libraryChip,versionChip,fhirChip,fileTooLarge}` / `recommendations.indicator{Info,Warning,Critical}` / `header.fhirVersionLine` / `elementBody.demographics.*` / `modifier.{operatorPlaceholder,boolIs*}` / `chooseCode.selectedSystemFallback` / `elementDescriptions.Twcore*` (22) / `expression.{andConnective,ageBetween}` / `workspace.{conflict.reloadFailed,dataLoadFailed}`。TFDA 追溯：需求 #419（安全性等級 A — 純前端 UI / CQL 生成器修補；不改 CQL 翻譯/執行/measure evaluation 路徑） | frontend/src/components/authoring/{ArtifactList,ArtifactWorkspace,ArtifactWorkspaceHeader,base-elements/{ArithmeticElement,BaseElements},builder/{ArtifactElementBody,CustomModifierBuilder,ExpressionPhrase,ModifierCard},cql-preview/CqlPreviewPanel,element-select/ElementSelectDropdown,fields/ChooseCodeDialog,import/ImportCqlDialog,query-builder/QueryBuilder,recommendations/Recommendations,subpopulations/Subpopulations,summary/ArtifactSummaryView}.tsx, frontend/src/constants/authoringConstants.ts, frontend/src/locales/{en,zh-TW}/authoring.json |[`8fbfc58`](../../commit/8fbfc58) |
| PAT-126 | 🐛 fix | 2026-04-27 | 前端（CQL Builder 27 個元件深度 review 修補） | **CQL Builder review-driven 修復批次** — ultrareview 對 `frontend/src/components/builder/` 識別 19 個 bug（5 blocker / 10 high / 9 medium-low）；共同主題是 CQL 生成正確性。**Blocker (5)**：(1) `CodesSection` 三處 `code "..."` 識別字 / display 未跑 `escapeCqlString` — 含 `'` 的醫學名（`Crohn's` / `Alzheimer's`）會破壞 CQL 解析；新加 `utils/cqlString.ts:escapeCqlIdentifier()` 處理識別字內 `"`/`\\` 逃逸。(2) `ModifierChainBuilder` qualifier 把整段 raw definition (`"HbA1c": '4548-4' from "LOINC"...`) 重新包雙引號 → 改用 `extractCqlName()` 取 bare name。(3) `mostRecent` modifier 用 `sort by FHIRHelpers.ToDateTime(effective as FHIR.dateTime)` 直踩 HAPI `DateTimeType` Comparable bug（同 BUG-110/111 家族）→ 改 `let date: (X.field as FHIR.dateTime).value sort by date desc` + `First()` 的安全模式（per CLAUDE.md TWCDI）。(4) `ParametersSection` DateTime literal 寫 `T00:00:00.0`（一位小數）非 CQL spec 接受 → `T00:00:00.000` 對齊 `.999`。(5) `RetrieveBuilder` 對 `code`-typed terminology 與 `valueSet` 用同樣 `[Resource: "X"]` 語法產 invalid CQL → 追蹤 termType；code 用 `[Resource] alias where alias.code ~ "X"` 的 where-form（`applyModifierChain` 加 `extraWhereClauses` 參數讓 modifier 沿用）。**High (10)**：(6) `CqlBuilderPanel` duplicate-name regex 不認 `define function` 與 `context Patient\\ndefine` → 改寬範圍 regex；(8) 5 個 builder 模組層級 `let nextId = 1` → `useRef(0)` per-instance counter（`ModifierChainBuilder` / `QueryBuilder` / `ExpressionBuilder` / `ConditionalBuilder` / `RecommendationBuilder`）；(11) `IncludesSection.handleSelectLibrary` 加 `versionRequestIdRef` 防 quick-switch race；(14) `QueryBuilder` 對 choice-typed date 欄位（`effective`/`onset`/`performed`/`occurrence`/`period`）emit cast + `.value`；(21) `ModifierChainBuilder` `valueComparison` 對 Observation 加 `(${alias}.value as Quantity)` cast（同 dispatcher ambiguity 防禦）；(25) `ElementRefBuilder.obsRecent` preset 套用同 (3) 的安全模式。**Medium / Low (9)**：(10/19) `ConceptsSection` + `DefinitionsSection` 編輯時加 i18n `editWarning` Alert（這兩個結構不存原 body 故編輯=重建）；(15) `RecommendationBuilder` grade/indicator/selectionBehavior/link.type/action.type/resourceType 全部跑 `escapeCqlString` 防禦未來 dropdown 改自由輸入時的 latent bug；(16) `CodesSection` + `ValueSetSection` 移除手寫 setTimeout 改用既有 `useDebouncedValue` hook；(17) `CqlPreviewBox` setTheme 加模組層級 `lastAppliedTheme` cache 防 main editor 每次 keystroke 重繪；(18) `ParametersSection` 加 `originalIntervalDefault` state，編輯時 round-trip 保留秒/毫秒精度（datetime-local input 只回 minute）；(22) `CqlPreviewBox` `wordBreak: 'break-all'` → `whiteSpace: 'pre'` + `overflow: 'auto'` 防 CQL 識別字被切半；(23) `ConditionalBuilder` case-when result / else 加防禦括號；(24) `RecommendationBuilder` 條件 tuple 用 `split('\\n').map(l => '    ' + l)` 取代 regex post-process，閉合 `}` 對齊開頭 `Tuple {`；ConceptsSection 順手把 concept 生成補上 `escapeCqlIdentifier` / `escapeCqlString`。**i18n**：en + zh-TW 同步加 `definitions.editWarning` / `concepts.editWarning` 兩 key。**驗證**：`npx tsc --noEmit` 零 error；`npx eslint src/components/builder src/utils/cqlString.ts src/hooks/useDebouncedValue.ts` 零 warning（清掉一個因 mostRecent 改寫後 dead 的 `wrapLast` 變數）。**不在本 PR 範圍**：#7 `allNames` deps 過粗（accordion `unmountOnExit` 已大幅緩解）、#12 `applyModifierChain` 從 .tsx 匯出破 HMR（搬到 `utils/modifierUtils.ts` 是結構性重構）、#20（review 自身 closer-reading 後撤銷）。TFDA 追溯：需求 #417（安全性等級 A — 純前端 CQL 生成器修補；不改 CQL 翻譯/執行/measure evaluation 路徑） | frontend/src/components/builder/{CodesSection,ConceptsSection,ConditionalBuilder,CqlBuilderPanel,CqlPreviewBox,DefinitionsSection,ElementRefBuilder,ExpressionBuilder,IncludesSection,ModifierChainBuilder,ParametersSection,QueryBuilder,RecommendationBuilder,RetrieveBuilder,ValueSetSection}.tsx, frontend/src/utils/cqlString.ts, frontend/src/locales/{en,zh-TW}/builder.json |[`2ff24bb`](../../commit/2ff24bb) |
| PAT-125 | 🧹 chore | 2026-04-25 | 前端（header GitHub 連結移除） | **移除主 header 的 GitHub 圖示按鈕** — 使用者要求移除。`Header.tsx` 的 `<IconButton href="https://github.com">` 段落整塊刪除，連帶移除未再使用的 `GitHub as GitHubIcon` import；`common.toolbar.github` i18n key 從 en + zh-TW 同步刪除。其餘 toolbar 按鈕（dark mode / settings / help / logout）位置不變。`tsc --noEmit` + `eslint` 零警告。TFDA 追溯：需求 #415（安全性等級 A — 純 UI 移除） | frontend/src/components/layout/Header.tsx, frontend/src/locales/{en,zh-TW}/common.json |[`2353bf3`](../../commit/2353bf3) |
| PAT-124 | 🐛 fix | 2026-04-25 | 全端（dashboard trend chart 按 scoring type 分類顯示 + DTO scoringType/unit） | **儀表板分數趨勢重構為 scoring-type aware** — 使用者回報儀表板「分數趨勢也無法正確顯示」、「幾乎無用」。稽核發現核心 bug 是「所有 scoring types 被當同一把尺」：(a) `ScoreTrendChart` overlay 模式寫死 `<YAxis domain={[0, 100]} />`，CV 指標（HbA1c=5.6 mmol/L）在 0–100 圖上幾乎看不見；(b) `DashboardService.computeDepartmentScores` 直接 `average()` 所有 scoring types 的分數 — 一個部門有 3 個 proportion (85, 92, 78) + 1 個 CV (5.6 mg/dL) → 平均成 42.6% 無意義；(c) `TrendDataPoint` DTO 沒有 `scoringType` 欄位前端無從分群；(d) `DashboardService.generateReport` 已正確過濾 CV 但**同一份 service 的另兩處 aggregation 沒套用**這個過濾，team 知道但沒完成。修法：**Backend** (1) `EnhancedDashboardData.TrendDataPoint` DTO 加 `scoringType` + `unit` 欄位（unit 從 `measure_report_group.measureScoreUnit` 第一個非 null 值取，proportion/ratio/cohort 留 null）。(2) 新 `DashboardService.isProportionScaleScoring(String)` static helper 統一判斷規則：proportion/ratio/composite/null=true，continuous-variable/cohort=false；`generateReport` 的 inline 規則改用 helper、`computeDepartmentScores` 套用同 helper（這是核心 fix）。(3) `getTrends` 注入 `MeasureReportGroupRepository` 撈 unit、把 scoringType + unit 寫進 DTO。**Frontend** (4) `ScoreTrendChart` 從 200 行單一 component 重構成 type-aware 三段式 layout：`classifyScoring(scoringType)` → 'proportion' | 'continuousVariable' | 'cohort' family；`<ProportionSection>` 沿用既有 multiples/overlay 切換、Y 軸固定 0–100% 加 % tickFormatter + 1 位小數 tooltip；`<PerMeasureSection>` 給 CV / cohort 用，每個 measure 獨立小圖 Y 軸 auto-scaled、標題附 unit 標籤、CV 顯示 `formatRawValue` (依量級 0/1/2 位小數)、cohort 用 `Math.round` 顯示整數人數；mode toggle 只在 proportion 家族 measure 數量 > 1 時顯示，CV / cohort 永遠 multiples。(5) `TrendSeriesPoint` type 加 `scoringType?` + `unit?`。(6) i18n 雙語同步 8 個 key（`dashboard.trendSections.{proportion,proportionHint,continuousVariable,continuousVariableHint,cohort,cohortHint,noTrendData,unknownUnit}`）。Tests：BE `DashboardServiceTest` +4 tests（`isProportionScaleScoring` 分類矩陣 / `getTrends_shouldPropagateScoringTypeAndUnit` 把 scoringType+unit 寫入 DTO / `getEnhancedDashboard_departmentAverages_shouldExcludeContinuousVariable` 確認 CV 不再混入部門平均、避免 45.3 假平均 / 既有 8 tests regression）13/13 pass；FE 新 `ScoreTrendChart.test.tsx` 4 tests 鎖 `classifyScoring` 五個 family 路徑 + safe-default。`tsc --noEmit` 零 error。**不在本 PR 範圍**：時區感知 period label（PAT-125 候選）、composite 指標的 sub-measure 細部展開 UI、CV measure 的 reference range threshold 線（需 backend 增加 normal-range 欄位）。TFDA 追溯：需求 #412（安全性等級 A — 純顯示層；不影響 CDS / measure evaluation 臨床路徑） | backend/src/main/java/com/cqlplatform/service/measure/DashboardService.java, backend/src/main/java/com/cqlplatform/model/measure/EnhancedDashboardData.java, backend/src/test/java/com/cqlplatform/service/measure/DashboardServiceTest.java, frontend/src/components/dashboard/ScoreTrendChart.tsx, frontend/src/components/dashboard/\_\_tests\_\_/ScoreTrendChart.test.tsx, frontend/src/types/index.ts, frontend/src/locales/{en,zh-TW}/measures.json |[`802d4c6`](../../commit/802d4c6) |
| PAT-123 | ✨ feat | 2026-04-25 | 前端（比較與趨勢即時評估） | **品質指標「比較與趨勢」補即時評估模式** — 使用者回報：原「比較與趨勢」只能查 DB 既有 measure_report，對應期間沒報告時直接回「未找到此指標的報告資料」死路一條，幾乎無法使用。修法（全前端 orchestration，零後端改動 — 後端既有 `$evaluate-measure` auto-save measure report，直接 reuse）：(1) **比較區**加第二顆「即時評估並比較」GradientButton 與原「比較（查詢已存報告）」outlined button 並列；前者依序呼叫 `measureApi.evaluateMeasure(id, undefined, p1Start, p1End)` → p2 同樣 → 最後 `comparePeriods(...)` 撈剛存的新報告；三步 LinearProgress + i18n 狀態文案（`evaluatingPeriod`、`comparingLive`）；錯誤中止時清空 stale comparison card。兩顆按鈕各附 `caption` 說明資料來源避免混淆。(2) **趨勢區**拆成兩個 subsection：**載入歷史趨勢**（既有行為）改名並加 hint；新增 **即時趨勢評估** 含「結束日期」date picker + 「期間長度」select（月/季/年）+ 沿用既有「期間數」。按「執行即時趨勢」觸發新 `computeTrendSlots(endDate, count, interval)` util 切 N 段相同長度的 calendar-aligned 期間（oldest-first），逐段呼叫 `$evaluate-measure`，client-side 組裝 `TrendDataPoint[]` 不走 `/measures/trend`（後者拿歷史 last-N 不對應所需時段）。(3) 新 `utils/dateDefaults.computeTrendSlots` 處理月/季/年切分，含 leap-year Feb、year wrap 等 edge case；對應 4 tests 鎖住切分結果（2026-04-30 回退 4 個月、2026-12-31 回退 4 季、3 年期區間、2024-03-31 回退 2 個月含 2/29）。(4) i18n 新增 18 個 key 於 `comparison.*` 雙語同步（`compare`/`compareLive`/`compareHint`/`compareLiveHint`/`evaluatingPeriod`/`comparingLive`/`loadTrendHint`/`trendLiveSection`/`trendLiveHint`/`trendEndDate`/`trendInterval`/`trendIntervals.{monthly,quarterly,yearly}`/`runLiveTrend`/`runningLiveTrend`/`liveTrendNote`/`slotFailed`）；原 `compare` / `loadTrend` 文案改為「比較（查詢已存報告）」/「載入歷史趨勢」讓兩種模式一目了然。(5) 期間數 clamp 1–12 避免使用者設 52 段暴打 FHIR 伺服器。既有快速路徑（stored report 查詢）保持 outlined 風格 + 不變 UX；新增 live 路徑以 GradientButton 突顯為主要 CTA + 明確 warning-color 文案「即時模式會依序評估 {{count}} 段期間，可能耗時數分鐘」。`npx tsc --noEmit` 零 error、新 4 tests + 既有 4 tests = 8/8 pass。TFDA 追溯：需求 #410（安全性等級 A — UX enhancement；即時評估呼叫的 `\$evaluate-measure` 已於 PAT-112 系列 patient-safety audit 鎖住 fail-loud 語意） | frontend/src/components/measure/MeasureComparison.tsx, frontend/src/utils/dateDefaults.ts, frontend/src/utils/\_\_tests\_\_/dateDefaults.test.ts, frontend/src/locales/{en,zh-TW}/measures.json |[`18397d4`](../../commit/18397d4) |
| PAT-122 | 🐛 fix | 2026-04-23 | 前端（measure Reports 分頁範圍） | **修復品質指標報告分頁顯示全系統所有報告** — 使用者回報：點選單一品質指標進入後，「報告」分頁卻顯示**全部品質指標**的歷史報告。`MeasureReportHistory.tsx` 無視 caller context，硬寫死呼叫 `measureApi.getReports()`（unscoped `/measures/reports`）；後端 API 早已備有 `getReportsForMeasure(measureId)` → `/measures/{id}/reports` 但 `MeasureEditor.tsx` 把 `<MeasureReportHistory />` 直接掛上去從未傳 `measureId`。Authors 看到自己 measure 報告夾在他人草稿之間 → 既洩露其他 author 的 evaluation 結果，又無法快速看到目前在編 measure 的歷史趨勢。修法：(1) `MeasureReportHistory.tsx` 加 optional `measureId?: number` prop；queryKey 條件分流為 `['measureReports', 'measure', measureId]`（scoped）vs `['measureReports']`（unscoped）讓 React Query cache 各自獨立、不互相污染；queryFn 對應呼叫 `getReportsForMeasure(measureId)` 或 `getReports()`；delete mutation 的 `invalidateQueries` 用 prefix `['measureReports']` 同時刷新 scoped + unscoped 任一開啟的 tab。(2) `MeasureEditor.tsx` line 441 改傳 `<MeasureReportHistory measureId={measure.id} />`。(3) prop optional 留下 standalone 用法（未來 admin 全域報告頁面），但實際 entry point 都已掛 measureId。`tsc --noEmit` 零 error。TFDA 追溯：需求 #408（安全性等級 A — 純 UI scope bug，不涉 patient safety；但有跨 author 資訊洩漏觀感問題） | frontend/src/components/measure/MeasureReportHistory.tsx, frontend/src/components/measure/MeasureEditor.tsx |[`8e2f291`](../../commit/8e2f291) |
| PAT-117 | 🐛 fix | 2026-04-24 | 全端（measure workflow/share/lock 按鈕全部 500） | **修復 eCQM 草稿 workflow 按鈕全部 500** — 使用者回報按「提交審核」或「鎖定」回 internal error。實測 production 6 個 workflow/lock endpoints + share 全部 500。Backend 日誌：`HttpMessageNotReadableException: JSON parse error: Unrecognized field "currentUser" (class WorkflowActionRequest), not marked as ignorable`。Frontend `measureApi.ts` 11 個 method 都在 body 塞 `currentUser: getStoredUsername()`，但 `WorkflowActionRequest` DTO 只有 `reason` 欄位、沒 `currentUser`；Jackson 預設 `FAIL_ON_UNKNOWN_PROPERTIES=true` 直接爆；`HttpMessageNotReadableException` 無專屬 handler → 走 `handleGenericException` → 500 「An internal error occurred」。更糟的是 backend 根本不 care FE 送的 `currentUser` — 所有 controller method 都用 `ownershipVerifier.getCurrentUsername()` 從 JWT 取使用者（JWT auth 上線前的 legacy code，backend 早忽略）。三管齊下修法：**(1) FE**（主修）— `measureApi.ts` 11 個 method（6 workflow + 4 share/unshare/transfer/access + reject 的 reason 保留）全部移除 `currentUser` body 欄位；刪除 unused `getStoredUsername` import。**(2) BE defense**：`CqlConfig.objectMapper()` `disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)` — 客戶端 / 舊版 app 送額外欄位未來不會再觸發 500；依舊靠 `@Valid` / `@NotNull` 擋必要欄位缺漏。**(3) BE handler**：`GlobalExceptionHandler` 加 `@ExceptionHandler(HttpMessageNotReadableException.class)` 回 400 BadRequest + 「Request body is not valid JSON or does not match the expected schema.」訊息。Malformed client request 是 4xx 不是 5xx — 未來類似 bug 使用者會看到合理的 400 而非嚇人的 500。三道防線任一生效都能修此 bug；合併後徹底 fix。新 `handleHttpMessageNotReadable_shouldReturn400_notGeneric500` test 鎖住 400 行為且訊息不洩漏 Jackson 類別名稱。Regression `GlobalExceptionHandlerTest` 16/16 + `MeasureDefinitionServiceTest` 22/22 pass。`tsc --noEmit` 零 error。TFDA 追溯：需求 #396（安全性等級 A — 純功能 bug，不涉 patient safety） | backend/src/main/java/com/cqlplatform/config/CqlConfig.java, backend/src/main/java/com/cqlplatform/exception/GlobalExceptionHandler.java, backend/src/test/java/com/cqlplatform/exception/GlobalExceptionHandlerTest.java, frontend/src/api/measureApi.ts |[`1bfa2ca`](../../commit/1bfa2ca) |
| PAT-116 | 🐛 fix | 2026-04-24 | 後端（data-requirements CodeRef 解析） | **`DataRequirementExtractor` 解析 CodeRef 為真實 code/system** — 使用者請求稽核「資料需求」分頁是否正確擷取，實測 production 6 個 measure 後發現系統性 bug。當 CQL 用 inline code 宣告（`code "X": 'CODE' from "System"`）而非 valueset 時，extractor 把 CQL 的 **參考名稱** 當成 `code.code` 字串存入、`system` 留 null，而不是解析成實際 code 值與 system URL。例如 measure 6 `[Condition: "Type 2 diabetes mellitus"]` 擷取出 `code: "Type 2 diabetes mellitus", system: null`，實際應得 `code: "E11", system: http://hl7.org/fhir/sid/icd-10-cm`。ValueSet 類型不受影響。衝擊：前端資料需求分頁對 inline-code measure 顯示**假 code 字串**；對接商若按此規格建 FHIR 查詢**撈不到病人資料**；measure portability 斷掉。Root cause：`DataRequirementExtractor.extractCodeRef()` 對 `CodeRef` 類型只 `codes.add(CodingInfo.builder().code(node.path("name").asText(null)).build())` — 不查解。Extractor 已經 build `codeDefMap`（從 `library.codes.def[]`）+ `codeSystemMap`（從 `library.codeSystems.def[]`）但 **沒傳進** `parseRetrieve` / `extractCodeRef`，純屬信號未接線。修法：(1) `parseRetrieve(retrieveNode, codeDefMap, codeSystemMap)` / `extractCodeRef(node, codes, codeDefMap, codeSystemMap)` 簽名加兩個 map 參數。(2) `CodeRef` 分支：`codeDefMap.get(refName)` 取 `CodeDefInfo(code, codeSystemName, display)`，再 `codeSystemMap.get(codeSystemName)` 取 URL；legacy fallback（ref name 查不到，如外部 library 引用）保留 ref name 作 code 維持向後相容。(3) `Code` 字面值分支：改用 `codeSystemMap` 把 system name 轉 URL（原本漏網、直接存 codeSystem 的 CQL name）。更新 1 個既有測試（`extract_retrieveWithCodeRef_shouldCaptureDirectCode` 原本斷言錯誤行為 "BloodPressure" 作為 code，改為提供完整 ELM 含 `library.codes.def` + `library.codeSystems.def` 並斷言 resolved `code: "85354-9"` + `system: "http://loinc.org"` + `display`），新增 2 個測試（unresolvable ref 保留 ref name 作 fallback、Code 字面值的 CodeSystemRef 正確解析 URL）。24/24 pass（22 既有 + 2 新 + 1 updated）。TFDA 追溯：需求 #394（安全性等級 A — 純資料擷取正確性 bug；不涉 clinical logic 但影響 measure 對接商 FHIR 查詢正確性） | backend/src/main/java/com/cqlplatform/service/cql/DataRequirementExtractor.java, backend/src/test/java/com/cqlplatform/service/cql/DataRequirementExtractorTest.java |[`2567e4e`](../../commit/2567e4e) |
| PAT-115 | 🐛 fix | 2026-04-24 | 前端（eCQM 補充資料頁可用性） | **修復 eCQM 補充資料（SDE）頁 3 個讓自訂 SDE 幾乎無法使用的 bug** — 使用者回報「品質指標」眾多 tab 中的「補充資料」分頁「幾乎完全無功能」。稽核 `EcqmSdeTab.tsx` 找到 3 個前端 bug，後端 pipeline（`EcqmArtifactEntity.supplementalDataJson` → `EcqmCqlBuilder` → `standard-sde.ftl` / 自訂 define → measure report）實際完整。修法：(1) **Bug #1（最嚴重）**：`<Paper key={sde.name}>` 用 name 作 React key。使用者在 TextField 輸入自訂 SDE 名稱時每按一鍵 name 變動 → key 變動 → Paper 及其 child TextField unmount+mount → **每按一鍵失焦**、無法連續輸入。修：`SupplementalDataElement` 加 `id?: string` + `custom?: boolean` optional 欄位，React key 改為 `sde.id ?? \`sde-idx-\${idx}\``；legacy row 在第一次編輯時 `updateCustom()` 自動 assign stable id（crypto.randomUUID 或 Date.now+random fallback）並標 `custom: true`，保留向後相容。(2) **Bug #2**：Filter 自訂列表用 `!STANDARD_SDE.some(name match)` 為條件。使用者若把自訂 SDE 命名為 "SDE Ethnicity"（同標準 SDE 名稱），該行從自訂列表靜默消失、同時標準 SDE checkbox 被誤觸勾選。修：新 `isCustomSde(sde)` helper 優先看 `custom` flag（true=自訂、false=標準 slot、undefined=legacy 時 fallback 名稱比對）；`toggleStandard()` 建立標準 slot 時明確標 `custom: false`、移除時只過濾 `!isCustomSde(s)` 的同名 row 不影響自訂。`standardCheckedNames` 也改只看 `!isCustomSde(s)` 的 slot。(3) **Bug #3**：自訂 SDE `criteria` 為 undefined 時 `{sde.criteria && (<EcqmPopulationTreeEditor ... />)}` 整個隱藏，使用者無法補條件。修：移除條件渲染，`tree={sde.criteria ?? (createEmptyConjunctionGroup() as ConjunctionGroupType)}` 始終提供 fallback 空樹；`onUpdateTree` 更新時 `updateCustom()` 把真正的 tree 寫回。新 `EcqmSdeTab.test.tsx` 6 tests 鎖住：keystroke 後 id 保留、legacy 自動升級為 PAT-115 格式、name 與標準 SDE 相同仍留在自訂列表、標準 checkbox 獨立、undefined criteria 仍渲染 editor、toggleStandard 標 `custom: false`。`npx tsc --noEmit` 零 error。本機 vitest 遇 Windows EMFILE 已知問題，CI Linux 會跑。TFDA 追溯：需求 #392（安全性等級 A — 純 authoring UX bug、不涉 patient safety） | frontend/src/components/ecqm/EcqmSdeTab.tsx, frontend/src/components/ecqm/\_\_tests\_\_/EcqmSdeTab.test.tsx, frontend/src/types/ecqm.ts |[`cc8e4e1`](../../commit/cc8e4e1) |
| PAT-112b+c | 🐛 fix | 2026-04-24 | 後端（CDS prefetch strict + FHIR Bundle OperationOutcome 檢查） | **完成 Phase 3 剩餘兩塊 patient-safety 路徑 — Tier 2 #10 Phase 3b+3c** — 延續 PAT-112a 的 fail-loud 原則，收尾 #10 Phase 3 剩下的兩處 silent failure。**PAT-112b（CDS prefetch strict mode）**：(1) `CdsInvocationService.resolvePrefetchTemplates` L260 重構，try/catch 縮小到只包 `prefetchResolver.resolveWithStatus()` call，exception 時 log.error + rethrow `RuntimeException`（由 `buildPrefetchProviderSafe` 包成 `CdsInvocationException(PREFETCH_PROVIDER_BUILD, e)` → 5xx）；happy path 的 empty ResolutionResult → null 保留（合法路徑「CDS service 沒 prefetch templates 可解」）。(2) `buildPrefetchProvider` L410 per-key parse fail：**保留** `diagnostics.prefetchStatus(status=failed)` 紀錄讓對接商能看到哪個 key 壞、為什麼，**再加** `throw new RuntimeException("Prefetch key '...' failed to parse")`，讓整個 CDS invocation fail loud。原行為「partial prefetch 繼續執行 CQL」會讓 AllergyIntolerance / MedicationRequest 等關鍵資源缺資料 → CQL `exists()` 回 false → CDS 渲染「無警示」= false negative。**PAT-112c（OperationOutcome error inspection）**：新 `com.cqlplatform.fhir.FhirErrorInspector` utility（final class, private constructor）提供 `assertNoErrors(Bundle, String contextHint)`：走 Bundle top-level entries + 每 entry 的 `response.outcome`（transaction response 用），若有 `severity=ERROR|FATAL` 的 OperationOutcome 則拋 `FhirServerUnavailableException`，訊息帶 contextHint + severity + code + diagnostics；WARNING / INFORMATION 嚴重度忽略（FHIR conformance 合法訊號如 "partial result truncated" 不該誤擋）。HAPI FHIR server 偶爾在 HTTP 200 回應裡把錯誤包成 Bundle entry 的 OperationOutcome 而不是 5xx，platform 目前沒人檢查、全當 valid data；PAT-112c 把這條 silent 路徑關上。整合到 3 個 Bundle-consume 點：`FhirDataProviderService.bulkFetchAllPatients` 的 resource-type search 與 Patient batch response、`CountingRetrieveProvider.trySearch` 的 fallback bundle。測試：新 `FhirErrorInspectorTest` 11 tests 鎖嚴重度分類（null/empty/only-data/info/warning 皆 no-op；ERROR/FATAL/nested/mixed/perEntryResponseOutcome 皆 throw；exception.reason = OTHER）；regression `CdsInvocationServiceTest` 10/10 + `FhirDataProviderServiceTest` 5/5 + `MeasureEvaluationFhirOutageTest` 5/5 = 31/31 focused sub-scope pass。Pre-launch review #10 Status 更新為 `[x] Done — PAT-110 + PAT-111 + PAT-112a/b/c`（整個 Phase 3 patient-safety 決策全面落地）。TFDA 追溯：需求 #386 / 設計 #387 / 風險 #388 / 驗證 #389（安全性等級 **C** — 延續 PAT-112a，同 patient-safety path） | backend/src/main/java/com/cqlplatform/fhir/FhirErrorInspector.java, backend/src/test/java/com/cqlplatform/fhir/FhirErrorInspectorTest.java, backend/src/main/java/com/cqlplatform/service/cds/CdsInvocationService.java, backend/src/main/java/com/cqlplatform/service/fhir/FhirDataProviderService.java, docs/pre-launch-production-readiness-review.md | |
| PAT-112a | 🐛 fix | 2026-04-24 | 後端（FHIR 失敗不得靜默偽裝無資料） | **Fail-loud retrieve + measure evaluation abort — Tier 2 #10 Phase 3a** — Pre-launch review #10 Phase 3 的第一塊，patient-safety 路徑修復。臨床決策為「CDS 遇 FHIR 掛回 error 不回空 card、measure 遇 FHIR 掛整個 fail 不 partial」，PAT-112 稽核（Phase A）發現 6 處 catch-and-return-empty 讓 upstream 失敗偽裝成「病人沒資料」：CDS 會渲染「無警示」→ 醫師判斷安全 → patient harm；measure 會 partial-aggregate → 分母 falsify → P4P/QI 誤用。修法：(1) **`FhirDataProviderService.bulkFetchAllPatients`** 兩處 catch（per-resourceType L142、Patient batch L167）改 `catch(FhirServerUnavailableException e) { throw e; } catch(Exception e) { throw new FhirServerUnavailableException(...); }` — 失敗時整個 bulk 中止，不再留空 slot 讓下游誤判「該 resource type 無資料」。(2) **`CountingRetrieveProvider.retrieve()` L545** 移除 `results = null` catch-all，讓 delegate 例外直接傳播 — CQL engine 見到例外會 fail evaluation（正確），不會誤判 `exists([X]) = false`。(3) **Patient fallback read L571** 特別區分 `ResourceNotFoundException`（HAPI 的 404，合法 true negative，保留 log.debug + 繼續）vs. 其他 exception（outage，rethrow） — 區分「病人不存在」vs「server 掛」。(4) **`trySearch` fallback L717** 從 `return empty list` 改 rethrow `FhirServerUnavailableException`；原 comment「do NOT fall back to unfiltered」語意保留。(5) **`MeasureEvaluationService` 混合失敗偵測**：新 `findFhirOutageCause(Throwable)` 靜態 helper 走 cause chain ≤10 hop（CQL engine 包 2-3 層、加 depth cap 防 pathological chain hang）；`AggregationState` record 加 `fhirOutageError` 欄位；aggregation loop 對每個 failed patient 呼叫 helper 記第一個 outage；主 `evaluate()` 若 `state.fhirOutageError != null` → `errorResult(...)` 中止整個 evaluation 回 `FHIR_UPSTREAM_UNAVAILABLE` envelope（PAT-110 handler 承接）；bulk fetch 層特別處理：`FhirServerUnavailableException` 直接終止（不試 per-patient fallback，同 server 也會失敗），其他 exception 保留原「per-patient fallback」行為。測試：新 `MeasureEvaluationFhirOutageTest` 5 tests（top-level / nested cause / absent / null / 100-hop depth cap < 100ms）鎖 helper 行為；regression focused sub-scope 31/31 pass（`MeasureEvaluationServiceTest` 9 + `FhirDataProviderServiceTest` 5 + `FhirFallbackTest` 4 + `CqlExecutionIntegrationTest` 18 含 FHIR Retrieve / Diabetes Screening / Debug Mode / Cross-library）。**不含**（Phase 3b 以後）：CDS prefetch strict mode（L260/L410，目前有 diagnostics 記錄，算 🟡 suspicious 可接受）、`OperationOutcome` severity=error 檢查（邊緣 case，PAT-112c 另做）。Pre-launch review #10 Status 翻為 `[x] Done — PAT-110 + PAT-111 + PAT-112a`。TFDA 追溯：需求 #381 / 設計 #382 / 風險 #383 / 驗證 #384（安全性等級 **C** — 可能直接影響患者安全，CDS 警示抑制為 patient harm path、measure score falsify 可能誤導臨床改善決策） | backend/src/main/java/com/cqlplatform/service/fhir/FhirDataProviderService.java, backend/src/main/java/com/cqlplatform/service/measure/MeasureEvaluationService.java, backend/src/test/java/com/cqlplatform/service/measure/MeasureEvaluationFhirOutageTest.java, docs/pre-launch-production-readiness-review.md | |
| PAT-113 | 🔒 security | 2026-04-24 | 後端 + Docker（`/actuator/prometheus` HTTP Basic） | **`/actuator/prometheus` 預設 HTTP Basic 認證 — Tier 3 #14** — Pre-launch review Tier 3 #14：`application-docker.yml` 設 `management.prometheus.public: true`，一旦 backend container 不慎暴露（docker-compose override 加 ports / 反向代理誤規則 / k8s LoadBalancer 誤掛），整份 JVM 與業務 metrics（heap、GC、endpoint request rate、P95 latency、circuit breaker 狀態、CQL queue depth、Hikari 連線池）匿名可讀，可被攻擊者用作指紋識別、使用模式推測、甚至時序 side-channel 分析。修法：(1) **Spring Security 新 `@Order(0) prometheusSecurityChain`** — `SecurityConfig.java` 新 SecurityFilterChain bean，`securityMatcher("/actuator/prometheus")` preempt 主 chain，掛 HTTP Basic + `hasRole("METRICS")`；從 `management.prometheus.auth.username/password` 讀 credentials 建 `InMemoryUserDetailsManager`（BCrypt 儲存），scope 到這條 chain 不污染主 chain 的 JWT-backed `CustomUserDetailsService`。(2) **Fail-loud unconfigured**：若任一 credential blank，private helper 回 always-throw-UsernameNotFoundException 的 UDS → 配合 `hasRole("METRICS")` 拒絕所有請求（包括匿名與帶 basic auth），**刻意不 fallback 匿名**——ops 漏設 env 時結果是 scrape 失敗觸發既有 `ServiceDown` critical alert page oncall，而非靜默匿名暴露。(3) **`application-docker.yml` 預設翻為 `public: false`** + 新 `management.prometheus.auth.username/password` 從 env 讀；legacy `public: true` 仍保留作為 dev / smoke 逃生艙。(4) **`docker/prometheus.yml` `basic_auth.password_file: /tmp/scrape_password`**——選 `password_file` 而非 inline env 因 prom container 沒 envsubst；(5) **Prometheus container entrypoint override** 從 `/bin/sh` 接受 env var `METRICS_SCRAPE_PASSWORD`，若未設 `FATAL exit 1`（CrashLoopBackOff 讓 ops 馬上發現）、若設則 `printf + chmod 600` 寫到 tmpfs 的 `/tmp/scrape_password` 再 `exec /bin/prometheus`——plaintext 不進 image layer、container 銷毀即清。(6) **`docker/docker-compose.yml` backend 容器** 新增 `METRICS_SCRAPE_USER`（default `prometheus`）+ `METRICS_SCRAPE_PASSWORD`（無 default、必設）env vars；prometheus 容器同樣注入 `METRICS_SCRAPE_PASSWORD`。(7) **`docker/.env.example`** 加兩個新變數附 `openssl rand -base64 24` 產生指令。(8) **測試**：新 3 個 `@SpringBootTest` 鎖三種 mode contract：`PrometheusEndpointAuthTest` (configured, 3 tests) / `PrometheusEndpointUnconfiguredTest` (2 tests, fail-loud) / `PrometheusEndpointPublicTest` (1 test, legacy public)；`application-test.yml` 補加 `prometheus` 到 `exposure.include` 讓 `@SpringBootTest` 能解析該路由。6/6 新 test pass + 21/21 regression（`HikariMetricsIntegrationTest` + `GlobalExceptionHandlerTest` + `AuthIntegrationTest`）pass。Pre-launch review #14 Status 翻為 `[x] Done — PAT-113`。TFDA 追溯：需求 #373 / 設計 #374 / 風險 #375 / 驗證 #376（安全性等級 B — 間接影響安全 posture） | backend/src/main/java/com/cqlplatform/config/SecurityConfig.java, backend/src/main/resources/application-docker.yml, backend/src/test/resources/application-test.yml, backend/src/test/java/com/cqlplatform/integration/PrometheusEndpoint{Auth,Unconfigured,Public}Test.java, docker/prometheus.yml, docker/docker-compose.yml, docker/.env.example, docs/pre-launch-production-readiness-review.md |[`a48f608`](../../commit/a48f608) |
| PAT-111 | ✨ feat | 2026-04-23 | 前端（EHR admin Live Health 欄位） | **EhrConnectionList 即時健康狀態欄位 — Tier 2 #10 Phase 2** — 延續 PAT-110。Admin 巡檢 `EhrConnectionList` table 原本只能看「上次手動 Test」的結果（可能數小時前跑的），無法主動發現某 EHR 已循環中斷。Phase 2 消費既有但未被 FE 使用的 `GET /api/ehr/health/overview`（`ConnectionHealthService` 提供，含 24h availability / avg response time / error count / last checked），**零 BE 改動**。做法：(1) `ehrApi.ts` 新 `getHealthOverview(): Promise<ConnectionHealthOverview[]>`，DTO 對應後端 Java record（`connectionId`、`currentStatus` 'healthy|degraded|down|unknown'、`lastResponseTimeMs`、`lastCheckedAt`、`avgResponseTimeMs24h`、`availability24h`、`totalChecks24h`、`errorCount24h`）。(2) `EhrConnectionList.tsx` 新 useQuery `ehr-health-overview` 鍵，`staleTime: STALE_30S / refetchInterval: REFETCH_30S` 後台 poll；失敗時前一次快照仍保留（**刻意不顯示 loading skeleton** 在 health column——admin table 在 BE 短暫 hiccup 時必須保持可用）。(3) 新 table 欄位夾在既有 Status 與 Actions 之間；新 `<HealthCell>` 元件 map `currentStatus → MUI Chip color`（healthy=success/degraded=warning/down=error/unknown=default）+ 顯示 `Healthy 99%` 格式 label；當 `healthOverview.get(connId)` 為 undefined（overview 未載入或該連線從未 health-checked），fallback 顯示「Unknown」chip 而非留空 —「空」本身是訊號但會讓 admin 誤以為沒問題。(4) Tooltip 多行內容：availability24h % / avgResponseMs / errorCount24h（> 0 才顯示）/ lastCheckedAt（本地時區格式化）；全部 null 時顯示 `noData`。(5) i18n `fhir.ehr.health.*` 新 11 個 key（column/columnTooltip、healthy/degraded/down/unknown、availability24h 等 4 個動態訊息、noData），zh-TW + en 同步。(6) `EhrConnectionList.test.tsx` 新 3 tests（正常 2 row 顯示可用率 / 無 health 資料 fallback Unknown / poll 進行中 fallback Unknown）。本機 vitest 遇 EMFILE 已知問題（`@mui/icons-material` 超出 Windows handle 上限），`npx tsc --noEmit` 零 error，CI Linux 會跑。**不含**：per-connection health history 時序圖（要另開 modal，scope 外）、circuit-breaker-instance 狀態顯示（後端 `/api/ehr/health/circuit-breakers` 回的是 breaker name 不是 connection id，需要 backend 加 mapping，留 Phase 3 或以後）、health check 間隔調整 UI（既有 `application.yml` 固定 15min，PAT-111 沒理由改）。Pre-launch review #10 Status 翻為 `[x] Phase 1+2 Done`。TFDA 追溯：需求 #371（安全性等級 A — admin 資訊顯示 UI） | frontend/src/api/ehrApi.ts, frontend/src/components/ehr/EhrConnectionList.tsx, frontend/src/components/ehr/\_\_tests\_\_/EhrConnectionList.test.tsx, frontend/src/locales/{en,zh-TW}/fhir.json, docs/pre-launch-production-readiness-review.md |[`0ede7cd`](../../commit/0ede7cd) |
| PAT-110 | ✨ feat | 2026-04-23 | 全端（FHIR upstream degradation UX Phase 1） | **`FHIR_UPSTREAM_UNAVAILABLE` 結構化 envelope + 黃色 banner — Tier 2 blocker #10 Phase 1** — Pre-launch review Tier 2 #10：真 EHR 的 FHIR server 偶爾 timeout / 5xx 是日常，但平台目前將所有 FHIR 錯誤打成紅色 `Internal Server Error` snackbar；使用者誤判為平台 bug，診間信心受損。修法分三階段，本 PR Phase 1 為 plumbing + UX baseline。**Backend**：(1) 新 `com.cqlplatform.fhir.FhirRequestContext`（ThreadLocal<Record>），entry-point service 於 `set(connectionId, connectionName)` / `finally clear()` 設定 EHR 身份；`PatientSearchService.searchPatients` 作為示範 wiring site。(2) `FhirServerUnavailableException` 擴充 `Reason` enum（TIMEOUT / CIRCUIT_BREAKER_OPEN / UPSTREAM_5XX / CONNECTION_REFUSED / OTHER）+ `connectionId` / `connectionName` 欄位。建構子 snapshot ThreadLocal 到 instance field（跨 thread 傳遞安全）；`Reason.classify(Throwable)` 靜態方法走 cause chain ≤10 hop 掃 class/message 關鍵字分類；`(message, cause)` 建構子 auto-classify 使既有 20+ throw site **完全不需改簽名**就得到 meaningful reason。(3) `GlobalExceptionHandler` 對 `FhirServerUnavailableException` + `CallNotPermittedException` 都回 503 + `Retry-After` header + envelope `{ errorType: "FHIR_UPSTREAM_UNAVAILABLE", upstream: { connectionId, connectionName, reason, retryAfterSeconds } }`；`ErrorResponse` DTO 加 `errorType` + `upstream: UpstreamInfo` 欄位，`@JsonInclude(NON_NULL)` 不破壞既有非 FHIR 錯誤 shape。**Frontend**：(4) 新 `src/api/ehrOutageBridge.ts`（模組作用域 listener pattern）橋接 Axios interceptor（plain JS）與 React Context；Axios response interceptor 對 503 + `errorType === "FHIR_UPSTREAM_UNAVAILABLE"` 呼叫 `notifyEhrOutage()` 並**同時** `Promise.reject(error)` — 不抑制既有錯誤處理流程，只是**額外**觸發 banner。(5) 新 `EhrOutageContext` 管理 `outages[]` state，dedup key `connectionId ?? "unknown"`（同 EHR 重複失敗只累加 failureCount 不開新 banner），`useEffect` 在 mount 時 `registerEhrOutageListener` unmount 時 unregister（防 StrictMode / HMR 泄漏）。(6) 新 `<EhrOutageBanner>` 用 MUI `<Alert severity="warning">`（黃色 palette）**persistent 不自動消失**（對比紅色 error snackbar 的 6 秒 auto-dismiss — 因為 EHR 離線是持續狀態需要一直看到），塞在 `App.tsx` `<Header />` 下方 always-visible。(7) i18n 完整 `fhir.degradation.*` namespace：`titleGeneric` / `titleWithName`（帶 `{{name}}` 插值）/ `dismiss` / `failureCount_one` / `failureCount_other` / `reason.{TIMEOUT,CIRCUIT_BREAKER_OPEN,UPSTREAM_5XX,CONNECTION_REFUSED,OTHER}`，zh-TW + en 同步。**刻意不做的事（留 Phase 2/3）**：(a) 20+ 個 throw site **全部**改寫塞 connectionId（`(message, cause)` auto-classify + `PatientSearchService` 示範 wiring 已涵蓋主要 user-facing 路徑；剩餘 background job / VSAC translation-time lookup 顯示 "ehrName: unknown" 的 generic banner 即可）、(b) EHR admin 即時 health UI（接現有 `/api/ehr/health/*` endpoint，PAT-111）、(c) per-hook fallback（CDS 空 card / measure skip population 的臨床語意，PAT-112 需產品/臨床討論）。Test sub-scope：BE `FhirRequestContextTest` 12 + `GlobalExceptionHandlerTest` 15 (12 既有 + 3 新) = 27/27 pass；FE `EhrOutageBanner.test.tsx` 5/5 pass + `npx tsc --noEmit` 零 error。Pre-launch review #10 Status 翻為 `[x] Phase 1 Done — PAT-110`。TFDA 追溯：需求 #366 / 設計 #367 / 風險 #368 / 驗證 #369（安全性等級 B — 間接影響臨床決策支援可用性與使用者信心） | backend/src/main/java/com/cqlplatform/fhir/FhirRequestContext.java, backend/src/main/java/com/cqlplatform/exception/FhirServerUnavailableException.java, backend/src/main/java/com/cqlplatform/exception/GlobalExceptionHandler.java, backend/src/main/java/com/cqlplatform/service/fhir/PatientSearchService.java, backend/src/test/java/com/cqlplatform/fhir/FhirRequestContextTest.java, backend/src/test/java/com/cqlplatform/exception/GlobalExceptionHandlerTest.java, frontend/src/api/client.ts, frontend/src/api/ehrOutageBridge.ts, frontend/src/contexts/EhrOutageContext.tsx, frontend/src/hooks/useEhrOutage.ts, frontend/src/components/common/EhrOutageBanner.tsx, frontend/src/components/common/\_\_tests\_\_/EhrOutageBanner.test.tsx, frontend/src/locales/{en,zh-TW}/fhir.json, frontend/src/App.tsx, frontend/src/main.tsx, docs/pre-launch-production-readiness-review.md |[`bd936f4`](../../commit/bd936f4) |
| PAT-109 | 📊 observability | 2026-04-23 | 後端（thread pool 隔離 + HikariCP 告警） | **Bulk import 獨立 pool + HikariCP / pool saturation observability — Tier 2 blocker #9** — Pre-launch review Tier 2 #9：`AsyncPatientImportService.executeBatchImport` 原 `@Async("cqlExecutionExecutor")` 跟 `/api/cql/execute` 共用同一 thread pool（core 10 / max 20 / queue 50）。一次 100 筆 EHR 匯入會把 worker 佔滿 3–5 分鐘，同時 CDS Hooks / 編輯器的 CQL 執行排隊或 504。HikariCP 連線池（max 20）和 CQL 執行池都沒告警，運維看不到飽和訊號。修法 3 塊：(1) **隔離** — `AsyncConfig` 新 `patientImportExecutor` bean（core 4 / max 8 / queue 100，**AbortPolicy**——故意不用 CallerRunsPolicy，pool 滿時 `/api/ehr/import` 直接 503 快速失敗，而非佔用 Tomcat thread 空轉幾分鐘）；`AsyncPatientImportService.executeBatchImport` 的 `@Async` qualifier 從 `"cqlExecutionExecutor"` 改為 `"patientImportExecutor"`；cql pool 維持 CallerRunsPolicy 做 backpressure。(2) **Observability** — `MetricsConfig` 新 3 個 gauge `patient.import.queue.size` / `.pool.active` / `.pool.size`（mirror 既有 `cql.execution.*` 命名慣例）；HikariCP 的 `hikaricp_connections_*` 本來 Spring Boot 3 就會 auto-register，本 PR 新 `HikariMetricsIntegrationTest` (`@SpringBootTest`) 明確鎖住 6 個 gauge name + pool tag 都有出現，防止未來 DataSource bean wiring 改動靜默關掉 metrics。(3) **Alerting** — `prometheus-alerts.yml` 新 3 條 rule：`HikariConnectionPoolPending` (warning, `hikaricp_connections_pending > 3 for 2m` — pool 飽和徵兆、未爆前先 page)、`HikariConnectionTimeout` (critical, `increase(hikaricp_connections_timeout_total[5m]) > 0` — 已經有 request 在吃 5xx)、`PatientImportQueueSaturation` (warning, `patient_import_queue_size > 80` — queue 100、超 80 表示 AbortPolicy 快要開始拒件)。(4) **Grafana** — 4 個新 panel：Hikari connection breakdown (active/idle/pending/max)、Hikari timeouts stat、patient import pool (queue/active/size)、CQL execution pool (queue/active/size)。**刻意不做的事**：本 PR 不調大 pool size 數字（沒 production load metrics 猜數字只是換個瓶頸；先做隔離 + 觀測，上線後看 metrics 再調）。新測試：`AsyncConfigTest` 4 tests（兩 executor 是不同 instance / `patientImportExecutor` 獨立 sizing / AbortPolicy saturation → `RejectedExecutionException` / cql executor 未變動保留 CallerRunsPolicy）；`HikariMetricsIntegrationTest` 2 tests（6 gauges 都 registered / pool tag 不為空）。Focused test sub-scope（AsyncConfig + MetricsConfig + HikariMetrics + 既有 EhrIntegration / AsyncPatientImport）29/29 pass。Pre-launch review #9 Status 翻為 `[x] Done — PAT-109`。TFDA 追溯：需求 #357（安全性等級 B — 間接影響臨床決策支援可用性） | backend/src/main/java/com/cqlplatform/config/AsyncConfig.java, backend/src/main/java/com/cqlplatform/config/MetricsConfig.java, backend/src/main/java/com/cqlplatform/service/fhir/AsyncPatientImportService.java, backend/src/main/resources/application.yml, backend/src/test/java/com/cqlplatform/config/AsyncConfigTest.java, backend/src/test/java/com/cqlplatform/integration/HikariMetricsIntegrationTest.java, docker/prometheus-alerts.yml, docker/grafana/provisioning/dashboards/cql-platform.json, docs/pre-launch-production-readiness-review.md |[`8514dbb`](../../commit/8514dbb) [`513603a`](../../commit/513603a) |
| PAT-108 | 📊 observability | 2026-04-20 | 後端（measure_report deserialization 告警） | **Deserialization 失敗從 WARN log 升級為 Prometheus critical alert — Tier 2 blocker #7** — Pre-launch review Tier 2 #7：BUG-114 在 `MeasureReportEntity.@PostLoad` 加了 `DESERIALIZATION_FAILURES` LongAdder，schema drift / DB 資料損壞時只會在 backend log 出現一行 WARN，運維沒有儀表板、沒有 alert、沒人看到 = silent data loss。修法：(1) `MetricsConfig` 新 Gauge bean `measure.report.deserialization.failures`，直接讀 `MeasureReportEntity.getDeserializationFailureCount()`（reusing 既有 LongAdder、無 concurrency 改動、不影響 hot path）。(2) `docker/prometheus-alerts.yml` 新 alert rule `MeasureReportDeserializationFailure`：`increase(measure_report_deserialization_failures[10m]) > 0`，for 1m，severity critical。任何一筆失敗就 page（對比其他 warning 等級的 rate-based rule — schema drift 不是 noise，是靜默丟資料）。(3) `docker/grafana/provisioning/dashboards/cql-platform.json` 新 stat panel「Measure Report Deserialization Failures」，thresholds 綠→紅於 >= 1，放在 dashboard 最下排 y=40 獨立一格避免誤會成正常計數器。(4) 新 `MetricsConfigTest`（`SimpleMeterRegistry` + 直接反射呼 `MeasureReportEntity.onLoad`）鎖住 gauge-reads-live-counter 不變式——未來若有人改 counter 實作（e.g. 換成 Micrometer Counter）、gauge 供給函式不再 hit LongAdder，此 test 立即 break。全量 7/7 test（MetricsConfigTest 1 + MeasureReportEntityTest 6）pass。Pre-launch review #7 Status 翻為 `[x] Done — PAT-108`。TFDA 追溯：需求 #355（安全性等級 B — PHI/measure report 完整性告警） | backend/src/main/java/com/cqlplatform/config/MetricsConfig.java, docker/prometheus-alerts.yml, docker/grafana/provisioning/dashboards/cql-platform.json, backend/src/test/java/com/cqlplatform/config/MetricsConfigTest.java, docs/pre-launch-production-readiness-review.md |[`8514dbb`](../../commit/8514dbb) |
| PAT-107 | 🔒 security | 2026-04-23 | 後端（CDS discovery rate-limit tier） | **CDS discovery endpoint 加獨立 rate-limit tier — Tier 2 #8 收尾** — Pre-launch review Tier 2 #8 把 `/cds-services` endpoint 的 rate limit 列為 TODO，但 survey 發現 BUG-087 penetration-test fix 早已把 `POST /cds-services/{id}` 納入 `CDS_INVOKE` tier（10 RPM/IP，CQL 引擎 DoS 防護）。Review 文件落後。剩餘真 gap：**`GET /cds-services` discovery 只走 DEFAULT tier 60 RPM/IP**，對於 unauth 且易被 enumerate 的 endpoint 偏鬆（攻擊者每小時可以掃 3600 次 list 規劃目標攻擊）。修法：(1) `RateTier` enum 加 `CDS_DISCOVERY`；(2) `RateLimitProperties.cdsDiscoveryRpm` 預設 20、env var `RATE_LIMIT_CDS_DISCOVERY_RPM`；(3) `application.yml` 新 key `cds-discovery-rpm`；(4) `resolveTier()` 把 GET `/cds-services` 與 `/cds-services/`（含尾斜線兩種寫法）都映射到新 tier，per-user path (`/cds-services/u/*`) 仍走 DEFAULT（合法 EHR bootstrap 走這條）；(5) `getRpmForTier()` 新 branch。既有 `RateLimitFilterTest.tier resolution` test line 270 從斷言 DEFAULT 改為 CDS_DISCOVERY（本 PR 不算 regression、是契約更新），新 2 tests：`shouldApplyCdsDiscoveryTierLimit`（超過回 429）+ `cdsDiscoveryAndInvokeTiersShouldHaveIndependentBuckets`（雙 bucket 隔離驗證 — 攻擊者打爆 discovery 不會連帶 block invocation、反向亦然）。Metric `rate_limit_exceeded{tier="CDS_DISCOVERY",layer="ip"}` 可接 Grafana alert。`RateLimitFilterTest` 17/17 pass、全量 backend 1312+ tests BUILD SUCCESS in 6m17s。TFDA 追溯：需求 #353（安全性等級 B — unauth endpoint 流量控制）。Pre-launch review #8 Status 翻為 `[x] Done — BUG-087 + PAT-107` | backend/src/main/java/com/cqlplatform/security/RateLimitFilter.java, backend/src/main/java/com/cqlplatform/config/RateLimitProperties.java, backend/src/main/resources/application.yml, backend/src/test/java/com/cqlplatform/security/RateLimitFilterTest.java, docs/pre-launch-production-readiness-review.md |[`84c74c3`](../../commit/84c74c3) |
| PAT-106 | 📋 docs | 2026-04-23 | 法規（TFDA regulatory artifacts v1.0.0） | **產出 Tier 1 #11 TFDA 法規文件 — 9 份 artifact + SHA-256 manifest** — Pre-launch review Tier 1 blocker #11：`regulatory_docs/templates/` 有 6 個 Jinja2 模板、`output/` 為空。IEC 62304 醫療器材軟體 + ISO 14971 風險管理要求實際 artifact 而非只有模板。修法：(1) 執行 `generate_regulatory_docs.py --repo Lusnaker0730/CQL --version 1.0.0`（需 GITHUB_TOKEN）產出 6 份核心文件：軟體需求規格書 SRS（135 KB）/ 軟體設計規格書 SDS（74 KB）/ 風險管理報告 RMR（77 KB）/ 軟體驗證報告 SVR（77 KB）/ 追溯矩陣 TM（24 KB）/ 變更管制紀錄 CCR（22 KB），全部中文、符合 TFDA 表頭格式，自動從 GitHub issues (`IEC62304:需求` / `IEC62304:設計` / `IEC62304:驗證` / `ISO14971:風險` labels) 彙整。(2) 執行 `generate_test_report.py --backend-reports backend/target/surefire-reports --output regulatory_docs/output --version 1.0.0` 產出 STR 軟體測試報告（markdown + html 兩種格式，自動統計 1341/1342 tests pass = 99.9% 通過率）。(3) 執行 `generate_manifest.py --dir regulatory_docs/output --version 1.0.0 --commit <sha>` 產出 `MANIFEST.sha256` + `MANIFEST.json` — 每份 artifact 的 SHA-256 雜湊、版本、commit、產生時間戳，supports TFDA audit 的完整性證據鏈。追溯矩陣自動揭露 ~50 個 pre-TFDA 時代的舊 requirement issues 缺 `設計 / 風險 / 驗證` 子 issue 的追溯連結（PAT-043 至 PAT-094 家族），屬歷史資料、不在本 PR backfill。Pre-launch review #11 Status 翻為 `[x] Partially Done — PAT-106`（修法步驟 1 + 4 完成；步驟 2 domain expert 填充 + 步驟 3 review/sign-off 流程為組織層工作）。TFDA 追溯：需求 #TBD（安全性等級 A — documentation artifact generation） | regulatory_docs/output/{軟體需求規格書_SRS,軟體設計規格書_SDS,風險管理報告_RMR,軟體驗證報告_SVR,追溯矩陣_TM,變更管制紀錄_CCR,軟體測試報告_STR}.{md,html}, regulatory_docs/output/MANIFEST.{sha256,json}, docs/pre-launch-production-readiness-review.md |[`db81f39`](../../commit/db81f39) |
| PAT-103 | ✨ feat | 2026-04-23 | 前端（CDS authoring 加入 LibraryDefinitionPicker） | **CDS 分頁加「引用程式庫定義」按鈕 + picker dialog** — PAT-102 已鎖住後端 contract（CDS authoring 的 `externalCqlElement` 樹節點正確產生 include 陳述式），本 PR 補前端：讓 author 真的能在 CDS Inclusion / Exclusion 分頁挑選已儲存的 CQL 程式庫定義。做法：(1) 新 `utils/libraryReference.ts` 集中 `LibraryDefinitionReference → ElementInstance` factory，eCQM 同時重構使用（DRY + 避免未來元素 shape 漂移）。(2) `ArtifactWorkspace.tsx` 加 `libPickerTarget` state（'include' / 'exclude' / null）、在 Inclusion / Exclusion 分頁右上角加「引用程式庫定義」按鈕（`LibraryBooks` icon）、render 共用 `<LibraryDefinitionPicker>` dialog；onSelect 時根據 target 呼叫 `handleAddIncludeElement` 或 `handleAddExcludeElement`。(3) `EcqmPopulationTreeEditor.tsx` 用共用 factory 取代原 inline 構造。(4) i18n key `workspace.useLibraryDefinition`（en `Use library definition` / zh-TW `引用程式庫定義`）。(5) 新 `libraryReference.test.ts` 7 項單元測試：type/returnType/name 格式 / 四個 field id（libraryName、libraryVersion、definitionName、alias 後端 ExpressionCqlEngine 需要的精確 ids）/ static flag / value 保留 / uniqueId 唯一（`libref-` 開頭）/ modifiers=[]。7/7 pass、`npx tsc --noEmit` 零 error。本機 vitest 受 Windows EMFILE 影響無法全綠執行，CI Linux 會跑。e2e 人工驗證留 PR 後使用者操作。TFDA 追溯：需求 #344（安全性等級 A — frontend UX） | frontend/src/utils/libraryReference.ts, frontend/src/utils/\_\_tests\_\_/libraryReference.test.ts, frontend/src/components/authoring/ArtifactWorkspace.tsx, frontend/src/components/ecqm/EcqmPopulationTreeEditor.tsx, frontend/src/locales/{en,zh-TW}/authoring.json |[`edfc2ed`](../../commit/edfc2ed) [`f6c8004`](../../commit/f6c8004) |
| PAT-102 | 🧪 tooling | 2026-04-23 | 後端（CDS authoring CQL library reference 整合測試） | **CDS authoring 支援 CQL 程式庫引用 — 整合測試鎖住 end-to-end contract** — 使用者觀察：「eCQM 可以引用 CQL 程式庫，CDS 不行」。Survey 後真相：**後端已支援**，只有前端 UI 缺 picker。`ExpressionCqlEngine.collectDeclarations()` 早已處理 `externalCqlRef` + `externalCqlElement` 兩種樹節點，`CqlArtifactBuilder.buildCql()` 在 inclusion tree / exclusion tree / baseElements / subpopulations 全部呼叫 `collectDeclarations()`；既有 unit test `CqlArtifactBuilderEdgeCaseTest.externalCqlInBaseElements_shouldAddInclude` 證實 builder 層 OK。缺的是：(a) save artifact → JPA 序列化/反序列化 → generateCql 的 end-to-end 整合測試保險，(b) 前端 `ArtifactWorkspace` 的 `LibraryDefinitionPicker`（eCQM 有）。本 PR 補 (a)，(b) 列 follow-up。新 `CqlGenerationServiceLibraryIntegrationTest`（@SpringBootTest + H2）5 個 tests：(1) `externalCqlRef` 於 baseElements 經 `saveAndFlush` 後 `generateCql` 仍帶 `include SharedLogic version '1.2.0' called SharedLogic` — 防 `serializeAll()/deserializeAll()` 過濾 unknown node type 的 silent regression；(2) `externalCqlElement` 於 inclusion tree 產出 include + `"alias"."DefName"` 引用；(3) 多個 reference 指向同 library → 只產出一條 include（`Set<String>` dedup 驗證）；(4) 同一 artifact 連呼叫 `generateCql` 兩次 → CQL byte-identical（防 @PostLoad 破壞 LinkedHashMap 順序）；(5) `generateCqlWithWarnings` 路徑也帶 include（Builder Export flow 用）。全部 5 tests pass in 49.23s、全量 backend 1312+ tests BUILD SUCCESS in 5m59s。Follow-up：前端 `LibraryDefinitionPicker` 從 `components/ecqm/EcqmPopulationTreeEditor.tsx` port 到 `components/authoring/ArtifactWorkspace.tsx`，scope 包含 i18n + UI design + 前端測試，另 PR 做。TFDA 追溯：需求 #342（安全性等級 A — testing infrastructure） | backend/src/test/java/com/cqlplatform/service/authoring/CqlGenerationServiceLibraryIntegrationTest.java |[`cf085ed`](../../commit/cf085ed) |
| PAT-101 | 🧪 tooling | 2026-04-22 | 開發流程（smoke regression locks + 2 附帶 backend fix） | **Smoke 擴充 3 個 contract lock + 順手修 2 個產品 bug** — 三個最近的 backend 改動只有 unit-level 覆蓋，整合路徑會 silent regress。新增：(1) Scenario 17 `17-cds-retrieve-cache-dedupe`：CDS service 4 個 defines 全引用 `[Observation]`、debugMode=true、斷言 `retrieveTracesCount==1`（BUG-116 batch eval 只消除 engine.evaluate() 重複呼叫，engine intra-eval 還是沒 dedupe）。(2) `assert.sh` 新 `checkProvenance: true` flag + scenario 01 開啟：follow-up GET `/api/measures/{id}/reports` 驗首 row `cqlHash` / `elmHash` / `measureVersion` 非 null（PAT-095 provenance contract）。(3) Scenario 20 `20-cql-execute-error-info`：壞 CQL 打 `/api/cql/execute`，斷言 HTTP 500 + `ErrorResponse.errorInfo.phase == "cql_translation"`（PAT-098 editor contract）。Harness 擴充：`assert-cds.sh` 新 `retrieveTracesCount`；`assert-cql-debug.sh` 新 `expectedHttpStatus` + `errorInfoPhase` + `errorInfoRequiredFields`，`execute-cql.sh` 改輸出 `STATUS\n---HTTP_STATUS_BODY---\nBODY` 兩段格式使同一 assert 處理 2xx / 5xx；`assert.sh` 接 optional 3rd arg measureId。**建置過程抓到 2 個真 backend bug**：(a) `TracingRetrieveProvider` 無結果 cache → HAPI engine 單次 evaluate 內同 `[Observation]` 被 retrieve N 次（5 或 10 筆）→ 修加 per-instance `resultCache`，key 是 12 個 retrieve 參數的 stable StringJoiner 簽名；命中 cache 時回傳 cached result **且不追加 trace row**；scope 嚴格 per-request 避免 PHI 跨請求洩漏。(b) `EcqmPublishService.publish` 丟棄 `validateCql()` 回傳的 `elmJson`，只存 CQL 從未呼叫 `setElmJson()` → 所有 eCQM 發布留 `elm_json=null` → PAT-095 `elmHash` 契約實際斷掉 → 修一行 `measureDef.setElmJson(validation.getElmJson())`。全 19 scenarios 冷 cache ~3-4min pass，全量 backend 1307+ tests BUILD SUCCESS in 6m16s。README 更新含 scenario 17/20 + 新斷言鍵。TFDA 追溯：需求 #337、設計 #338、風險 #339（安全性等級 B）、驗證 #340 | scripts/smoke/scenarios/{17-cds-retrieve-cache-dedupe, 20-cql-execute-error-info}, scripts/smoke/scenarios/01-proportion-age-cohort/expected.json, scripts/smoke/lib/{assert.sh, assert-cds.sh, assert-cql-debug.sh, execute-cql.sh}, scripts/smoke/run.sh, scripts/smoke/README.md, backend/service/cql/TracingRetrieveProvider.java, backend/service/ecqm/EcqmPublishService.java |[`0016244`](../../commit/0016244) |
| BUG-116 | 🐛 fix | 2026-04-21 | 後端（CQL debug mode — 消除重複 retrieve）| **Debug mode 統一為 batch eval 消除 N× Observation 假 retrieve** — 使用者回報 BMI CDS hook 的 debug panel 出現 10 筆 Observation retrieve row（每筆 count=1），但實際 production 只會呼叫 1 次。根因：`CqlExecutionService.doExecute` 的 debug-mode 分支（L387~454）為取得 per-expression wall-clock 將每個 expression 丟進獨立的 `engine.evaluate(Set.of(one))` call。HAPI CQL engine 不在獨立 `evaluate()` 之間保留 retrieve cache，N 個 expressions 引用同一個 `[Observation: valueset]` 就發 N 次真實 FHIR search。影響：(1) debug mode 下 origin FHIR server 被轟 N×；(2) `TracingRetrieveProvider` 誠實記錄 N 個 retrieve row，author 看了以為 CQL 有問題去亂優化；(3) debug 觀察到的行為跟 production 不一致，誤導設計決策。修法（Option C）：(1) 刪除 debug-mode 專用 per-expression loop（68 行）；(2) Debug 與 normal 都走同一條 batch-first-with-per-expression-fallback 路徑（批次 `evaluateWithEngine(allExpressions)`、失敗才 fallback）；(3) 新增 `perExpressionTimings: Map<String, Long>` 欄位，在 fallback 的 per-expression loop 用 `try/finally` 記錄 wall-clock；batch 成功時 map 空 → 所有 trace `evaluationTimeMs=0`（trade-off：batch 模式下 per-expression timing 不可測，totalTimeMs 仍準確）；(4) 新增 post-eval trace build：遍歷 `expressions` 從 `results` map 組 `ExpressionTrace`、填 sourceLocator/dependencies/timing，batch 成功與 fallback 共用此建構。Trade-off 記於 #334：per-expression timing 精度降低換取 production-accurate retrieve behavior + N× 少的 FHIR server hit + 消除 debug 假訊號。未來 Phase 2 可 engine-level DebugMap 做 per-expression 精確 timing。regression 保險：`CqlExecutionIntegrationTest.4. Debug Mode` + smoke scenario 16 鎖住 expression trace schema 完整性；scenario 18 鎖住 error path debug.error 仍結構化。全量 1307+ backend tests BUILD SUCCESS in 3m57s、smoke 17/17 pass。TFDA 追溯：需求 #332、設計 #333、風險 #334（安全性等級 B）、驗證 #335 | CqlExecutionService |[`69e1400`](../../commit/69e1400) |
| PAT-100 | 🔒 security | 2026-04-21 | 後端（PHI 欄位加密 Phase 1） | **PHI DB-at-rest 加密：4 entity、8 個高風險欄位** — Pre-launch review 🔴 Critical #4 Phase 1。現況：`EncryptionConverter` 已存在（AES-GCM-256 + PBKDF2WithHmacSHA256 + per-row IV + `ENC:` prefix + legacy plaintext fallback），只套了 `UserEntity.email` + 4 個 `EhrConnectionEntity` 欄位。真正的 PHI payload（measure 評估結果、匯入的 FHIR bundle、CDS sandbox prefetch 資料、patient identifiers/names）全部明文。修法 Phase 1：(1) `MeasureReportEntity.result_json` 套 `@Convert` — 整個 measure 評估 JSON 含 subject IDs、populations、scores。(2) `MeasureReportPopulationEntity.subject_ids_json` 套 `@Convert` — V52 normalized tables 的 patient IDs JSON array。(3) `PatientImportEntity.{patient_fhir_id, patient_identifier, patient_name, bundle_json}` 四欄全套 `@Convert` — 匯入 FHIR bundle 含完整臨床記錄 + 三個 patient identifier 欄位。(4) `SandboxPresetEntity.{patient_id, prefetch_json}` 套 `@Convert` — CDS sandbox 測試 context 的 patient 與預抓資源。(5) Flyway V55：`ALTER COLUMN ... TYPE TEXT` 把 `patient_import.{patient_fhir_id, patient_identifier, patient_name}`（原 VARCHAR 200/200/500）+ `sandbox_preset.patient_id`（原 VARCHAR 100）擴成 TEXT 以容納 ciphertext ~1.5× 膨脹；rollback V55 對應。(6) `EncryptionConverterTest` 新 2 tests：`roundTrip_largeJsonPayload_100kb`（模擬 result_json 大 payload、~100KB+ realistic JSON）+ `roundTrip_utf8MultibytePayload`（中文 patient 姓名 / CQL 中文註解），既有 11 test regression green，共 13/13 pass。(7) 新 `PhiEncryptionIntegrationTest` 4 tests（`@SpringBootTest` + H2 + `JdbcTemplate`）：雙重驗證 contract — app 層 JPA read 回 plaintext + raw JDBC SELECT 回 `ENC:` + PHI 字串不出現在 ciphertext；含 legacy-plaintext-fallback 測試（模擬 deploy 前 row 仍可讀）。全量 backend suite BUILD SUCCESS in 3m49s（加 6 test 無 regression）。**不影響使用者 / FHIR 調閱 / sandbox / measure 報告 UI**：all reads go through JPA converter = transparent decryption；FHIR browser 走 HAPI proxy 完全不經 DB；只有運維 raw SQL debug 會看到 `ENC:` 亂碼（排障需走 API 或寫 decrypt script）。Repository queries 經 grep 確認無 PHI-equality 搜尋（皆為 connectionId/ownerUsername/measureDefinitionId 等 non-PHI 欄位），未破壞任何現有查詢。**跳過 Phase 2**：`AuditLogEntity`（threat model 不同、需 compliance 討論）、`BatchImportJobEntity.patientIds`、`FailedImportEntity.patientFhirId`、批次 backfill migration、backup 加密與 key rotation（Phase 3）。TFDA 追溯：需求 #327、設計 #328、風險 #329（安全性等級 B）、驗證 #330 | backend/entity/{MeasureReportEntity, MeasureReportPopulationEntity, PatientImportEntity, SandboxPresetEntity}.java, db/migration/V55__phi_field_column_expansion.sql, db/rollback/rollback_V55*, EncryptionConverterTest, PhiEncryptionIntegrationTest |[`4c342e8`](../../commit/4c342e8) |
| PAT-099 | 🔧 refactor | 2026-04-21 | 前端（debug panel 共用化：對齊 PAT-098 後端） | **前端 debug panel 抽出共用元件** — PAT-098 後端已統一 `ExecutionErrorInfo`；前端 `DebugPanel` 和 `CdsDebugPanel` 仍各自重刻 expression trace table、retrieve trace table、ELM viewer、錯誤 alert（後者 CDS 專有、editor 完全沒有）。修法：(1) 新 `components/debug/` 目錄含 4 個共用元件：`ExpressionTraceTable.tsx`（extracted from DebugPanel，expression rows + dependency expansion）、`RetrieveTraceTable.tsx`（extracted from DebugPanel，FHIR retrieve trace with timing bars）、`ElmJsonViewer.tsx`（extracted from CdsDebugPanel，collapsible JSON pretty-print with parse-fail fallback）、`ExecutionErrorAlert.tsx`（extracted from CdsDebugPanel inline alert，phase→severity mapping + collapsible stack trace）。全部 `useTranslation('common')`。(2) `ExecutionErrorAlert` 的 phase→severity 映射新增 `card_generation=error` + `fhir_retrieval=warning` + `population_evaluation=warning`，解決原 CDS 專用映射未覆蓋新 phase 的 gap。(3) `DebugPanel.tsx` 從 247 行縮到 ~30 行，僅 orchestrate `<ExpressionTraceTable>` + `<RetrieveTraceTable>` + totalTime caption。(4) `CdsDebugPanel.tsx` 精簡：錯誤 alert 改 `<ExecutionErrorAlert>`、trace tables 直接用共用元件（原本 embed 整個 `<DebugPanel>` 顯得 awkward）、ELM viewer 改共用。保留 CDS 專屬 section：`PrefetchSection`、`FhirServerSection`、resourcesByType chips、contextWarnings、dryRun alert、invocationContext collapsible。(5) `types/index.ts`：新增 `ExecutionErrorInfo` interface 對齊後端 `com.cqlplatform.model.debug.ExecutionErrorInfo` JSON shape；保留 `type CdsErrorInfo = ExecutionErrorInfo` 作 deprecated alias 不破壞現有 import。(6) i18n 共用 debug 文字從 `editor.debug.*` + `cds.debug.*` 搬到 `common.debug.*`（en + zh-TW 同步），含 `phase.*` 8 個 value（原 CDS 6 個 + 新 fhir_retrieval / population_evaluation）。CDS 專屬文字（title / invocationContext / prefetchStatus / fhirStatus / fhirCategory / dryRun / resourcesByType 等）留在 `cds.debug.*`。(7) 新 `ExecutionErrorAlert.test.tsx` 4 tests：fields render / no-stack 隱藏 toggle / with-stack 顯示 toggle / unknown phase fallback。(8) `npx tsc --noEmit` 零 error。本機 vitest 因 Windows EMFILE 環境限制（@mui/icons-material 開檔數超過 OS 上限）無法全綠執行，CI 在 Linux 上會跑。TFDA 追溯：需求 #325（安全性等級 A — frontend UX / dev experience） | frontend/src/components/debug/{ExpressionTraceTable,RetrieveTraceTable,ElmJsonViewer,ExecutionErrorAlert}.tsx, debug/\_\_tests\_\_/ExecutionErrorAlert.test.tsx, components/execution/DebugPanel.tsx, components/cds/CdsDebugPanel.tsx, types/index.ts, locales/en/common.json, locales/zh-TW/common.json |[`45844d0`](../../commit/45844d0) [`b170c76`](../../commit/b170c76) |
| PAT-098 | 🔧 refactor | 2026-04-21 | 後端（debug 模式統一：三 flow 共用 ExecutionErrorInfo） | **統一 CDS / editor / eCQM 的 debug error 結構** — BUG-115 之後發現 editor 和 eCQM 根本沒做 phase 分類，分別只有 `errors: List<String>` 和 `errorMessage: String`，跟 CDS 的結構化 `debug.error` 三個 flow 長三個樣。EHR 整合商 / measure author / CQL author 拿到完全不同的 debug 體驗，BUG-115 的 heuristic 只有 CDS 走到、其他兩個 flow silent。修法：(1) 新 `model/debug/ErrorPhase` enum（8 個 phase：CDS 原 6 + 新 FHIR_RETRIEVAL + POPULATION_EVALUATION，`.wireName()` = 小寫 enum name 保持 wire compat）。(2) 新 `model/debug/ExecutionErrorInfo`（phase/errorType/message/stackTraceSummary，JSON shape 與原 `CdsResponse.CdsErrorInfo` 完全相同）。(3) 新 `util/ExecutionErrorClassifier`：static util 無狀態。`classify(Throwable)` 走 cause chain ≤10 層 + 掃 class simple name (Translation/Parse/Syntax/Compiler/Lexer) + 掃 message ("CQL translation failed"/"translation error"/"parse error")，全 miss 回 `CQL_EXECUTION`。`buildErrorInfo()` 兩個 overload：auto-classify + explicit phase。堆疊過濾 `com.cqlplatform.*` top 5 frames。`fromCdsPhase()` legacy enum exhaustive switch 對映。(4) CDS flow：`CdsInvocationService` delete inline `looksLikeTranslationError` + `buildErrorInfo`，delegate to classifier。`CdsResponse.CdsDebugInfo.error` 欄位型別從 inner `CdsErrorInfo` 改為 shared `ExecutionErrorInfo`，JSON wire 零變動（CDS client 看不到差異）。(5) Editor flow：`CqlExecutionResponse` 新增 `errorInfo: ExecutionErrorInfo` 欄位（back-compat 保留 `errors[]`）。`GlobalExceptionHandler.ErrorResponse` 也新增同欄位 + `@JsonInclude(NON_NULL)`。`handleCqlExecutionException` 經 classifier 填 errorInfo。編輯器 / 對接商 500 回應現在帶 `errorInfo.phase` 可直接判斷是翻譯錯還是執行錯。(6) eCQM flow：`MeasureEvaluationResult` 新增 `errorInfo` 欄位。`MeasureEvaluationService.errorResult` overload 可選傳 `Throwable`，有 throwable 就經 classifier 填 errorInfo。無 throwable（guard conditions like 空 patient list）保持 null。(7) 新 `ExecutionErrorClassifierTest` 14 tests：null 輸入 / 純 runtime / class name marker / message marker / nested cause / 50 層 cause loop 不死循環 / buildErrorInfo 三 signature / stackTraceSummary null-vs-list / legacy phase exhaustive map。(8) `CdsInvocationServiceTest` 既有 10 tests（含 BUG-115 regression）保持綠。全量 backend BUILD SUCCESS in 3m37s。前端 `DebugPanel` / `CdsDebugPanel` 抽共用元件列 follow-up（分 PR 做，保持此 PR backend-only scope）。TFDA 追溯：需求 #320、設計 #321、風險 #322（安全性等級 B）、驗證 #323 | model/debug/ErrorPhase, model/debug/ExecutionErrorInfo, util/ExecutionErrorClassifier, ExecutionErrorClassifierTest, CdsInvocationService, CdsResponse, CqlExecutionResponse, MeasureEvaluationResult, MeasureEvaluationService, GlobalExceptionHandler |[`d8e8f3e`](../../commit/d8e8f3e) |
| BUG-115 | 🐛 fix | 2026-04-21 | 後端（CDS invocation 錯誤分類 + smoke 18 assertion） | **CDS debug.error.phase 正確識別 wrapped translator errors** — PAT-097 smoke scenario 18 建立時實測發現的分類 bug：`CdsInvocationService.looksLikeTranslationError()` 只檢查最外層 exception 的 simple class name。CQL 翻譯失敗在 invocation time 被 `CqlExecutionService` wrap 成 `CqlExecutionException`（訊息含 `Execution failed: CQL translation failed with N error(s): ...`），外層 simple name 不含 Translation/Parse/Syntax → heuristic 漏判 → phase 錯標為 `cql_execution`。對 EHR 對接商衝擊：看到 `phase: cql_execution` 會往 runtime / FHIR 資料方向排查，實際根因是 author 寫壞 CQL 語法。排障方向完全錯誤，估 20-60 分鐘浪費。Non safety-critical 但 authoring/integration UX 重傷。修法：(1) Heuristic 改走 cause chain（最多 10 層防 loop）檢查每層的 class simple name 是否含 Translation/Parse/Syntax + 新增 Compiler/Lexer substring；(2) 同層也 scan message 內容含 `CQL translation failed` / `translation error` / `parse error` 任一子字串；(3) 純 runtime 錯誤（class 不含關鍵字、message 不含關鍵字）仍歸 `cql_execution`，既有 test `invoke_debugModeTrue_executionFails_shouldAttachErrorInfoWithPhase` regression-lock 此路徑；(4) 2 新 unit tests：wrapped RuntimeException with translation message + nested cause with `*Compiler*` simple name，都 expected `cql_translation`；(5) Smoke scenario 18 的 `debugErrorPhase` 從 workaround 的 `cql_execution` 改回正確的 `cql_translation`；(6) 順手修 smoke `assert-cds.sh`：當 `debugErrorPhase` 已 exact-match 時，`debugErrorRequiredFields` 迴圈跳過 `phase` 避免 duplicate print。Editor (`/api/cql/execute`) 和 eCQM (`/$evaluate-measure`) 路徑經 review 確認**沒有同 bug**——它們根本沒做 phase 分類，錯誤一律 flat `errors[]` / `errorMessage`，是另一個 diagnostic gap 另案追（未開 PAT）。全量 1307/1307 backend + smoke 17/17 pass。TFDA 追溯：需求 #315、設計 #316、風險 #317（安全性等級 B）、驗證 #318 | CdsInvocationService, CdsInvocationServiceTest, scripts/smoke/lib/assert-cds.sh, scripts/smoke/scenarios/18-cds-error-debug-trace/expected.json |[`8b90169`](../../commit/8b90169) |
| PAT-097 | 🧪 tooling | 2026-04-21 | 開發流程（smoke test 覆蓋：debug 模式） | **Smoke harness 加 2 個 debug-mode scenarios（16 CQL execute / 18 CDS error 路徑）** — 既有 harness（PAT-082/087/091）覆蓋 eCQM + CDS 正向路徑，三個 debug endpoints 完全沒 guard：`/api/cql/execute` debugMode、CDS hook 錯誤路徑 debugMode、test-case run debugMode。Silent regression 在 debug schema 上不會讓 user-facing 功能變紅，但 EHR 對接商拿到空 debug object 或 bare stack trace 無法排障。修法：(1) 新 scenario type `cql-execute`：`run.sh` 新 dispatch case 讀 `request.json` 直接 POST 到 `/api/cql/execute`，不做 seed-fhir / save-publish（debug contract test 跟 eCQM 語意無關）。(2) 新 lib helpers `execute-cql.sh`（POST 帶 JWT + 解析 HTTP status）與 `assert-cql-debug.sh`（斷言 `.success` + `debugTrace.expressionTraces|length >= N` + 每 entry 有 `name`/`resultType`/`evaluationTimeMs`/`order` + `debugTrace.elmJson` 非空字串 + `debugTrace.totalTimeMs` 為 number，型別/存在為主不鎖值）。(3) Scenario 16 `16-cql-execute-debug-trace`：CQL 含 3 個 defines（true / 'hello' / dependent），debugMode=true + contextType=Patient，最少 3 entries 可驗。(4) 擴充 `assert-cds.sh` 加 `debugErrorPhase`（exact match `.debug.error.phase`）+ `debugErrorRequiredFields`（list of non-null fields on `debug.error`）。(5) Scenario 18 `18-cds-error-debug-trace`：service CQL 呼叫 `ThisFunctionDoesNotExist('oops')`，debugMode=true 後回 HTTP 200（非 5xx）+ `debug.error.phase = "cql_execution"`（not cql_translation — 因 `CdsInvocationService.looksLikeTranslationError()` 靠 exception class simple name 做 heuristic，translator 錯誤被 wrap 成 `CqlExecutionException` 後就 miss 了；此 classification quirk 作 follow-up 記在 scenario note，不擋 debug contract smoke）+ `errorType` 與 `message` 非 null。(6) README scenarios 表格更新，`cql-execute` 檔案格式說明（`request.json` 直接 POST body）與 `assert-cds.sh` 可用鍵新增 `debugErrorPhase` / `debugErrorRequiredFields`。(7) 跳過 scenario 17（CDS positive debug）與 scenario 19（test-case debug）作為 follow-up，scenario 編號刻意斷續（16/18）標示哪些保留給後續擴充。全 17 scenarios 冷 cache 跑過，1 miss（18 phase 值初次 assumption 是 cql_translation 實測為 cql_execution，改動 expected.json 後 pass）。TFDA 追溯：需求 #313（安全性等級 A — dev tooling） | scripts/smoke/run.sh, scripts/smoke/lib/execute-cql.sh, scripts/smoke/lib/assert-cql-debug.sh, scripts/smoke/lib/assert-cds.sh, scripts/smoke/scenarios/16-cql-execute-debug-trace, scripts/smoke/scenarios/18-cds-error-debug-trace, scripts/smoke/README.md |[`225ab19`](../../commit/225ab19) |
| PAT-096 | 🔒 security | 2026-04-20 | 基礎設施（TLS / VM nginx）| **TLS 強制於 twcql.com — 上線前 Tier 1 blocker #1** — Pre-launch review 🔴 Critical #1：生產流量 Client→Cloudflare→VM 原本只走 HTTP，Cloudflare SSL mode 為 Flexible → Cloudflare↔origin 段以明文傳送 Bearer token 與 PHI。修法：(1) Cloudflare 發 Origin CA cert（15 年有效，2026-04-20 ~ 2041-04-16）安裝於 VM `/etc/ssl/cloudflare/twcql.com.pem`（644 root:root）與 `.key`（600 root:root）。(2) 改寫 `/etc/nginx/sites-enabled/twcql.com` 成雙 server block：:80 `return 301 https://$host$request_uri`（apex + www），:443 `ssl http2` 掛 origin cert + TLS 1.2/1.3 only + 強 cipher list + HSTS `max-age=15768000; includeSubDomains` + `X-Content-Type-Options: nosniff` + `X-Frame-Options: SAMEORIGIN` + proxy_pass 127.0.0.1:8888 保留 `X-Forwarded-Proto https`。(3) `nginx -t` 通過、`systemctl reload nginx`；local origin curl 回 HTTP/1.1 200 + HSTS header；origin cert openssl 驗 subject `CloudFlare Origin Certificate` / issuer CF Origin SSL CA / 有效期 2041。(4) Canonical config 以 `docker/nginx-vm/twcql.com.conf` + README.md 存回 repo 作 source of truth，含 Cloudflare Full strict 必要性說明、部署 scp 指令、verification curl。Nginx 1.24 不識別新 `http2 on;` directive，改用 legacy `listen 443 ssl http2;`。**使用者尚需做**：Cloudflare dashboard 把 SSL mode 從 Flexible 切成 Full (strict) 並開啟 Always Use HTTPS，否則 CF→origin 仍走 :80 觸發 redirect loop。`APP_BASE_URL` 啟動期 fail-fast + SMART endpoints 預設 https 留 Tier 2。Pre-launch review #1 Status 翻為 `[x] Done — PAT-096` | docker/nginx-vm/twcql.com.conf, docker/nginx-vm/README.md, docs/pre-launch-production-readiness-review.md |[`9c74349`](../../commit/9c74349) |
| PAT-095 | 🔐 audit | 2026-04-20 | 後端（measure report provenance）| **Measure report 綁 version + CQL/ELM hash — 上線前 Tier 1 blocker #3** — Pre-launch review 🔴 Critical #3：`measure_report` 只記 `measure_definition_id`，不記版本、不記 CQL/ELM 內容 hash。Measure 改版或刪除後，歷史 report 無法回溯用哪版 CQL 跑出 → TFDA/CMS audit「此 report 基於哪版 measure」答不出來。修法：(1) Flyway V54 加 `measure_version VARCHAR(50)` / `cql_hash VARCHAR(64)` / `elm_hash VARCHAR(64)` 於 `measure_report`，全 nullable（既有 row 不回填）；rollback_V54 對應；covering index `(measure_definition_id, measure_version)` 支援 audit 查詢熱路徑。(2) `MeasureReportEntity` 三個 fields + javadoc 說明 provenance 語意（measure_version 人類可讀、cql_hash 位元組級 forensic、elm_hash 語意等價判定）。(3) 新 `util/ContentHash.sha256Hex(String)`：null→null（與 empty 區分）、empty→well-defined SHA constant、UTF-8 encoding（CQL 可能含中文註解）、純 JCA builtin 無第三方依賴、JCA 不支援 SHA-256 時 fail-fast throw。(4) `MeasureReportService` 注入 `MeasureDefinitionRepository`，`saveReport` 在 build entity 前 findById、present 填 provenance、absent 或 id null 則 provenance 全 null（definition 被刪時 report save 仍成功、provenance gap 顯式可見而非偽造）。(5) 11 新 tests：ContentHashTest 6（null/empty-vector/known abc vector/pure fn/byte-change/UTF-8 Chinese）+ MeasureReportServiceVersionTrackingTest 5（exists happy path/CQL-change-differs-hash/definition missing/id null skip lookup/cqlContent null → hash null 不填 empty SHA）。全量 1304/1304 pass。TFDA 追溯：需求 #303、設計 #304、風險 #305（安全性等級 B）、驗證 #306。Pre-launch review #3 Status 翻為 `[x] Done — PAT-095` | db/migration/V54__measure_report_version_tracking.sql, db/rollback/rollback_V54*, MeasureReportEntity, MeasureReportService, util/ContentHash, ContentHashTest, MeasureReportServiceVersionTrackingTest |[`5a4ec6c`](../../commit/5a4ec6c) |
| PAT-094 | 🔒 security | 2026-04-20 | 後端（認證 — password lockout）| **Password lockout — 5 次失敗鎖 30 分鐘 + admin unlock endpoint** — Pre-launch review #5（Tier 2）：沒 failed-login 追蹤、沒 account lockout，攻擊者可自動化嘗試密碼無限制。修法：(1) Flyway V53 加 `failed_login_attempts INT` + `lockout_until TIMESTAMP` + partial index `WHERE lockout_until IS NOT NULL`（讀取熱路徑便宜）；rollback_V53 對應。(2) `UserEntity` 對應 fields、`@JsonProperty(READ_ONLY)` 避免外部改。(3) `CustomUserDetailsService.loadUserByUsername` 讀 lockout_until：未來時間 → accountNonLocked=false → Spring Security 的 `DaoAuthenticationProvider` 在 password compare 之前丟 `LockedException`（連 timing attack 都避免）。(4) 新 `LoginAttemptListener` 聽 Spring Security 的 `AbstractAuthenticationFailureEvent`（累加、達 threshold 設 lockout）+ `AuthenticationSuccessEvent`（清零；hot path 只在需要時寫）。Unknown user findByUsername 回 empty 時 no-op — 不讓 attacker 用隨機 username 建 ghost row。(5) `AuthController.login` catch `LockedException` → HTTP 423 + 明確訊息不洩漏解鎖時間。(6) `POST /api/admin/users/{id}/unlock` 新 admin endpoint 清 counter + lockout。(7) 可配置 `app.security.lockout.max-attempts`（env `APP_SECURITY_LOCKOUT_MAX_ATTEMPTS`，預設 5）+ `duration-minutes`（預設 30）。(8) 10 新 tests（LoginAttemptListener 7 + CustomUserDetailsServiceLockoutTest 3）覆蓋 counter increment / lockout trigger / clear on success / unknown user / null username / past & future & null lockout 所有分支。全量 1303/1303 pass。TFDA 追溯：需求 #298、設計 #299、風險 #300（安全性等級 B）、驗證 #301。Pre-launch review 文件 #5 狀態可從 `[ ] TODO` 改 `[x] Done (PR #TBD)` | db/migration/V53__user_password_lockout.sql, db/rollback/rollback_V53*, UserEntity, CustomUserDetailsService, LoginAttemptListener, AuthController, AdminController, application.yml, LoginAttemptListenerTest, CustomUserDetailsServiceLockoutTest |[`f5d12d9`](../../commit/f5d12d9) |
| PAT-093 | 📋 docs | 2026-04-20 | 文件（上線前 production-readiness review） | **Pre-launch production readiness review — snapshot + action plan** — 以 code reviewer 角度做的完整 production-readiness review。Goal：接真 EHR + 真 PHI + 真臨床決策。Scope：backend + frontend + infrastructure + regulatory docs。發現 4 🔴 Critical（TLS 沒強制、draft measure 可跑真病人、evaluation 結果沒綁 measure version、PHI plaintext 儲存）、7 🟠 High（password lockout、@SuppressWarnings、deserialization alert、CDS rate limit、pool size、FHIR degradation、regulatory docs 填充）、6 🟡 Medium（liveness/readiness 分、PITR runbook、metrics 保護、eval rate limit、entity audit、frontend version push）+ 12 項 good-as-is regression lock。文件放 `docs/pre-launch-production-readiness-review.md`，每項有 Status checkbox 與 verification 步驟，三階段計畫（Tier 1 blocker / Tier 2 30d / Tier 3 quarterly）。校正初次 survey 誤報一處（`docker/.env` 實際 gitignored）。此文件作為 future reviewer / TFDA 稽核 / 工程 roadmap 的 source of truth | docs/pre-launch-production-readiness-review.md |[`56961ad`](../../commit/56961ad) |
| PAT-092 | 🐛 fix | 2026-04-20 | 後端（CDS / CQL authoring UX） | **CDS authoring 錯誤訊息帶 actionable hint — deprecated hook + CodeableConcept compare** — PAT-091 smoke 建置時踩到的兩個真 UX 問題。修法：(1) `CqlTranslationService` 新 static `hintForError(String)`，narrow pattern match 兩個真見使用者踩到的情況：`FHIR.CodeableConcept × System.String` equivalent call（scenario 11 踩到，告訴 author 要 extract coding[0].code 或 compare against declared Code）+ `FHIR.Coding × System.String`。`mapTranslatorException` 把 hint append 到 `CqlError.message` via `\n\nHint: ...`，保留原 translator message 不覆蓋。其他錯誤 hint=null（不過度 hint）。(2) `HookTypeValidator` 新 `DEPRECATED_HOOKS` map（目前只 `medication-prescribe → order-sign`，CDS Hooks 1.0 release notes 明列的 migration）。validate 失敗時若 hook 在 deprecated map，訊息改為「Hook type 'medication-prescribe' was deprecated in CDS Hooks 1.0 — use 'order-sign' instead」；純未知 hook 維持 generic message（不瞎猜替代）。(3) 零行為變動 — 只改 error message，callers 對 rejection 的處理不需改。新增 `CqlTranslationServiceHintTest` 4 tests（CodeableConcept/Coding/unrelated/null）、`HookTypeValidatorTest` 加 2 tests（deprecated hint + unknown fallback）。全量 1293/1293 pass。TFDA 追溯：需求 #292、設計 #293、風險 #294（安全性等級 B — authoring UX，影響 CDS 邏輯正確性的 authoring 信心）、驗證 #295 | CqlTranslationService, CqlTranslationServiceHintTest, HookTypeValidator, HookTypeValidatorTest |[`34202a9`](../../commit/34202a9) |
| PAT-091 | 🧪 tooling | 2026-04-20 | 開發流程（smoke test 覆蓋：CDS Hooks） | **Smoke harness 加 6 個 CDS Hook scenarios（10~15）** — PAT-082/087 只蓋 eCQM（9 scenarios）；CDS Hook 整合路徑完全沒測，class-level silent bug（hook validator、Tuple→card、dryRun、disabled）單元測試看不到。修法：(1) `run.sh` 依 `expected.json.type` field 分派（`ecqm` default / `cds-hook` 走 CDS 流程），既有 9 eCQM scenarios 不動。(2) 新 lib helpers：`save-cds-service.sh`（POST /api/cds/services、parse HTTP status 顯示 body）、`invoke-cds.sh`（POST /cds-services/{id}、unauth 公開 endpoint）、`assert-cds.sh`（cardCount/cards[]/expectNoCards/debugPrefetchNonEmpty、source.label nested 特別處理）。(3) `CDS_BASE` env wire 到 SMOKE_BACKEND_PORT（CDS 在 `/cds-services` 非 `/api`）。(4) 6 個 CDS scenario：`10-cds-patient-view-basic` hardcoded info card、`11-cds-patient-view-conditional` exists([Condition]) 驗 prefetch-driven logic、`12-cds-order-sign` 非 patient-view hook + draftOrders、`13-cds-multi-card-indicators` 3 defines 各一張 info/warning/critical card、`14-cds-disabled-service-not-listed` enabled:false 返 200+「Service not found」info card（backend 選 graceful response 而非 404，assertion 改 assert card content）、`15-cds-dryrun-mode` dryRun 0 cards + prefetchStatus populated。(5) 建置過程順手抓 3 個真坑：`CDS_BASE` 沒 wire 預設 8080 連不到 smoke 18080、`~` operator 不支援 CodeableConcept 需簡化 CQL、`medication-prescribe` 不在 HookTypeValidator allowed 清單改用 `order-sign`。全 15 scenarios 冷 cache 3m6s pass。TFDA 追溯：需求 #290（安全性等級 A — dev tooling） | scripts/smoke/{run.sh,lib/save-cds-service.sh,lib/invoke-cds.sh,lib/assert-cds.sh,scenarios/10~15-cds-\*,README.md} |[`afb73f9`](../../commit/afb73f9) |
| PAT-090 | 🔍 SEO | 2026-04-20 | 前端（SEO / Search Console） | **Sitemap 清理 + robots.txt + /login noindex — 解 GSC「已找到 - 目前尚未建立索引」14 筆** — 背景：twcql.com 在 GSC 顯示 16 URL 中 14 個卡「Discovered - currently not indexed」（13× `/learn?tab=xxx` + `/login`）。用 Chrome 自動化直接 scrape GSC drilldown 頁抓到完整 URL 清單與原因分類。診斷：不是 canonical 問題，是 Google 決定不 crawl（query-param filter 變體 + 新網域無 backlinks + SPA 同 HTML 看不出各頁差異）。修法：(1) `public/sitemap.xml` 從 16 URL 縮到 2 URL（`/` + `/learn`），拿掉 13× `?tab=` 變體（UI state 不是資源，Google 本來就會 deprioritize）與 `/login`（auth gateway 不該被索引）。inline XML comment 說明 exclusions 的理由。(2) 新 `public/robots.txt` default-allow + Sitemap 宣告，crawler 不靠 GSC submit 就能找到。(3) `LandingPage.tsx`（路由 `/login` 使用）的 Helmet 加 `<meta name="robots" content="noindex, follow">`，保留 canonical 一致性同時明確告訴 crawler 不要索引 auth gateway。TFDA 追溯：需求 #288（安全性等級 A — SEO／dev experience） | public/sitemap.xml, public/robots.txt, src/pages/LandingPage.tsx |[`ea8261d`](../../commit/ea8261d) |
| PAT-089 | 🐛 fix | 2026-04-19 | 後端（eCQM save validation） | **Save-time 拒絕 unknown aggregateMethod** — 延續 PAT-088：PAT-088 在 evaluation layer 把 unknown method 處理為 null+log.warn（避免 silent average）；本 PR 把同樣邏輯前推到 save time。修法：`EcqmExpressionTreeValidator.validate` 新增 `validateAggregateMethods` 子檢查，iterate `populationGroups[*].observations[*].aggregateMethod`，透過 `MeasureScoreCalculator.normalizeAggregateMethod` 判斷；unknown → 加 error 訊息（帶路徑 `populationGroups[0].observations[1].aggregateMethod`、輸入值、支援 methods 列表），最終 throw ValidationException 於 POST/PUT /api/ecqm/artifacts 回 400。Null/blank aggregateMethod 仍接受（維持「未指定 → average」語意）。Author 馬上看到 validation error 而非 save 成功後跑評估看空白 score debug。新增 `EcqmExpressionTreeValidatorTest` 33 tests（parametrized 所有 canonical + aliases + typos + edge cases + multi-observation error path identification）。全量 1286/1286 pass，smoke 9/9 pass（aliases 仍 accept 於 save time）。TFDA 追溯：需求 #283（安全性等級 B — early catch 延續 PAT-088 家族） | EcqmExpressionTreeValidator, EcqmExpressionTreeValidatorTest |[`5f5b73e`](../../commit/5f5b73e) |
| PAT-088 | 🐛 fix | 2026-04-19 | 後端（CV aggregateMethod 正規化） | **CV aggregateMethod 支援 Min/Max/Avg aliases + unknown 不再 silent fall-through** — 根因：`MeasureScoreCalculator.calculateContinuousVariableScore` 對 `aggregateMethod` 做小寫 exact-match 配 `default -> average` branch。`"Min"`/`"Max"`/`"Avg"`/`"Mean"` 是自然寫法，typo 如 `"Minumum"` 也是——全部 silently 走 default 回平均值。臨床 safety-critical 極值指標（最長住院日、最高血壓、最差 HbA1c）被平均化 → outlier patient 消失、report 看似正常、**介入決策基於假訊號**。PAT-087 smoke scenario 08/09 建立時踩到（expected 2.0 收到 6.0），當時繞過用 `"Minimum"`/`"Maximum"` canonical form——本 PR 結構性修。修法：(1) 新 `normalizeAggregateMethod(String)` static helper，`CANONICAL_AGGREGATE_METHODS` set = {count,sum,average,median,minimum,maximum}；接受 aliases `min`→`minimum` / `max`→`maximum` / `avg`+`mean`→`average`（case-insensitive）；未知值回 null；null/blank 保持 `"average"` 預設（preserve 舊「未指定」語意）。(2) `calculateContinuousVariableScore` 走 normalizer；unknown 時 `log.warn` 包含完整支援 methods 清單 + 回 null（而非 silently average）。(3) `computeObservationStats` 的 `.aggregateMethod` display 欄位也走 normalizer，response 一律 canonical form（`"minimum"` 而非 `"Min"`），下游 consumer 詞彙統一。(4) Smoke scenarios 08/09 revert 到 `"Min"`/`"Max"` aliases，端到端驗證 alias support。(5) 新增 40+ parametrized tests 鎖 canonical forms / all aliases / case variants / null-blank / unknown typos；smoke README 更新 caveat（現在 aliases 支援、unknowns 不 silent 了）。全量 1253/1253 pass，smoke 9/9 pass in 2m41s。TFDA 追溯：需求 #278、設計 #279、風險 #280（安全性等級 B — safety-critical Min/Max 指標正確性）、驗證 #281 | MeasureScoreCalculator, MeasureScoreCalculatorTest, scripts/smoke/scenarios/08-cv-min-encounter-duration, 09-cv-max-encounter-duration, scripts/smoke/README.md |[`deb8d87`](../../commit/deb8d87) |
| PAT-087 | 🧪 tooling | 2026-04-19 | 開發流程（smoke test 覆蓋：CV aggregates） | **Smoke harness 補齊 CV aggregate methods + episode-based CV 路徑** — PAT-082 只覆蓋 CV Count（patient-based）；剩下 4 個 aggregate method（Average/Sum/Median/Minimum/Maximum）與 episode-based CV codegen path 完全沒測。Episode-based 的 `Measure Observation Values`（plural）走 `("Measure Population") MP return "Measure Observation"(MP)`，跟 patient-based `Measure Observation Value`（singular）不同分支，未覆蓋 = 沒 guard。修法：(1) 新增 5 個 scenario（05~09）共用同一 fixture design（5 patients + 5 encounters 各 duration 2/4/6/8/10 days），各自測一個 aggregate method。Episode-based CV 用 `populationBasis=Encounter` + `GenericEncounter_vsac` element 無 codes/valuesets（emit `[Encounter]` 全撈）+ observationType=duration / unit=days / property=period（emit `duration in days of Encounter.period`）。Expected: Average=6.0, Sum=30.0, Median=6.0, Minimum=2.0, Maximum=10.0。(2) 建立過程順手抓兩個 operational issue：`save-and-publish.sh` 用 `curl -sf` 在 4xx 時 silent fail，吞掉 HAPI validation error body（`description: Input contains potentially unsafe content` 這類），改為不帶 -f 並加 `-w %{http_code}` 解析 status code、surface body 到 stderr；`reset-fhir.sh` 的 DELETE order 把 Patient 放第一個，當 Encounter reference Patient 時 HAPI 因 referential integrity 拒絕（HTTP 409），改為 reference-dependent order（referring resources Encounter/Observation/Condition/Procedure/Med* 先、Patient 最後）。(3) README 更新表格列出 9 個 scenarios + aggregateMethod naming caveat（Min/Max 會 silently fall through 到 Average）。全 9 scenarios 冷 cache 2m48s pass。TFDA 追溯：需求 #276（安全性等級 A — dev tooling） | scripts/smoke/scenarios/05-cv-avg-encounter-duration, 06-cv-sum-encounter-duration, 07-cv-median-encounter-duration, 08-cv-min-encounter-duration, 09-cv-max-encounter-duration, scripts/smoke/lib/save-and-publish.sh, scripts/smoke/lib/reset-fhir.sh, scripts/smoke/README.md |[`3af723f`](../../commit/3af723f) |
| PAT-086 | 🧪 tooling | 2026-04-19 | 開發流程（CHANGE_LOG 自動化） | **自動回填 CHANGE_LOG commit hash — 取代 2-commit pattern** — 根因：CLAUDE.md 要求每個 PR 做兩個 commit（主 commit 留空 hash，第二 commit 回填 hash），實務上常忘記導致 main 累積空 commit 欄位，且 CHANGE_LOG row 都插 table 開頭使多 PR 同時開時 merge conflict 頻發（今天 #258 cascade 過程多次撞到）。修法：(1) 新 `.github/workflows/changelog-backfill.yml` — push 到 main 時觸發，呼叫 Python script 掃 CHANGE_LOG.md 找 `\| ... \| \|$` pattern 的空 row、`git log origin/main --grep="(#PAT-NNN)" --fixed-strings --no-merges --reverse` 找 commit hash、格式化塞回去、auto-commit + push。Bot commit 跳過 `github-actions[bot]` 避免 infinite loop。(2) 新 `.github/scripts/changelog-backfill.py` — idempotent、--no-merges 濾掉 PR merge commit（merge commit 內含 PR title 會重複 match）、oldest-first 排序多 hash（對 multi-commit PR 的閱讀順序自然）。(3) 本機 fallback `scripts/changelog/fill-hash.sh` — 同邏輯，`--commit` flag 自動 commit。(4) `CLAUDE.md` commit 慣例章節更新：主 commit 留空 hash，後續 workflow 處理；需要本機時跑 fill-hash.sh。(5) 順手回填 15 個 empty row（PAT-078 到 PAT-085, PAT-061/062/063/064/067/068, BUG-105），自動驗證 script 正確。TFDA 追溯：需求 #274（安全性等級 A — dev tooling） | .github/workflows/changelog-backfill.yml, .github/scripts/changelog-backfill.py, scripts/changelog/fill-hash.sh, CLAUDE.md |[`5d618f9`](../../commit/5d618f9) |
| PAT-085 | 🐛 fix | 2026-04-19 | 後端（CV measure：boolean observation handling） | **CV Count aggregate 對 boolean Measure Observation 回正確計數** — 根因：`PopulationEvaluator.extractObservationValues` 只收 `Number` / `Iterable<Number>`，boolean 直接丟。CQL 產生器對 boolean-returning criteria（`AgeRange`/`Gender`/其他 boolean demographic filter）產出 `Measure Observation Value: if MP then <boolean> else null`——整個 observationValues list 空掉，Count aggregate 回 0、measureScore 回 null。所有 "count of patients with X" style CV measure silently broken。PAT-082 scenario 03 建立時發現（IP=3 / MP=3 / score=null），當時沒 assert score 並 logged follow-up——本 PR 修掉。修法：`extractObservationValues` 加 `Boolean` 分支：TRUE → `1.0`、FALSE → skip（不貢獻 observation instance）；Iterable 情況同樣處理。Number 路徑完全不變（regression-safe）。Count aggregate 現在 `values.size() = count of non-null boolean TRUE observations`，語意與 FHIR spec 對齊。Smoke scenario 03 加 `score: 3.0` 斷言（先前跳過）。新增 4 個 PopulationEvaluatorTest tests 鎖 boolean TRUE/FALSE/numeric/null 四路徑。全量 1213/1213 pass，smoke 4/4 pass。TFDA 追溯：需求 #269、設計 #270、風險 #271（安全性等級 B — 臨床品質指標正確性）、驗證 #272 | PopulationEvaluator, PopulationEvaluatorTest, scripts/smoke/scenarios/03-cv-count-adults/expected.json |[`39f754d`](../../commit/39f754d) |
| PAT-084 | 🐛 fix | 2026-04-19 | 後端（Population Evaluator：ratio semantics） | **Ratio Numerator 改為獨立於 Denominator（FHIR spec 合規）** — 根因：`PopulationEvaluator.aggregatePatientResults` 對所有 scoring type 硬把 Numer gate 在 Denom 後 (`inNumer = effectiveDenom && <numer>`)。對 proportion 正確（Numer ⊆ Denom），對 **ratio class-level bug**——FHIR MeasureReport spec 對 ratio 定義 Numer 與 Denom 為兩個獨立計數（各自 gate by IP）。所有 rate-style measure（跌倒事件/千病人日、院內感染/千住院日、ADE/千處方等）都被系統性低估。PAT-082 smoke scenario 02 原設計 Denom=young-adults, Numer=seniors (disjoint) 回報 Numer=0、score=0，當時只能用 Numer⊆Denom 繞過、記 follow-up——本 PR 修掉。修法：(1) `PopulationEvaluator.aggregateRatioPatientResults(counts, results)` 新 method：Numer 只 gate 在 IP、不 gate 在 Denom；Denom Excl 正常處理；無 Denom Exception（proportion-only concept）。(2) `buildRatioEntries(results)` 新 debug trace method，比照 `buildProportionEntries` 但 Numer gating reason 換成 `numer_gatedByIpFalse`。(3) `buildGroupTrace` dispatch 拆分 `case "proportion" / case "ratio"`（原本合併 route 到 proportion entries）。(4) `MeasureEvaluationService.executeAndAggregate` 加 `isRatio` flag 路由到新 method；observation values 收集照舊（rate-based ratio 仍可能有 observations）。(5) Score 計算：fallback 從 `calculateProportionScore` 改為 `scoreCalculator.calculateScore(scoringType,...)` dispatcher，ratio → `calculateRatioScore`（不扣 denom-exclusion、允許 >100%）。(6) Smoke scenario 02 revert：Denom=young-adults, Numer=seniors，score=150.0（Numer > Denom，>100% 的 valid ratio）全棧真機驗證。新增 `PopulationEvaluatorTest` 8 個 tests 鎖雙路徑差異（同 input 不同 aggregation）。全量 1209/1209 pass，smoke 4/4 pass。TFDA 追溯：需求 #264、設計 #265、風險 #266（安全性等級 B — 臨床品質指標正確性）、驗證 #267 | PopulationEvaluator, PopulationEvaluatorTest, MeasureEvaluationService, scripts/smoke/scenarios/02-ratio-age-comparison/\* |[`c8085fc`](../../commit/c8085fc) |
| PAT-083 | 🐛 fix | 2026-04-19 | 後端（Measure Score Calculator + Evaluation Service） | **Cohort measureScore 改回 IP count + populations 只含 IP** — 根因：`MeasureScoreCalculator.calculateScore` cohort 分支硬回 null，註解誤導 "Cohort measures don't have a numeric score"；`MeasureEvaluationService.buildResult` 把 cohort 和 proportion / ratio 混走同一條路徑，populations 硬塞 `denominator: 0` / `numerator: 0` placeholder。兩者皆不符 FHIR R4 MeasureReport spec。PAT-082 smoke scenario 04 建立時發現 backend 對 cohort 回 score=null 無法 assert，logged as follow-up—本 PR 修掉。修法：(1) `MeasureScoreCalculator.calculateCohortScore(Integer ip)` 新 helper：IP=null → null、IP=0 → 0.0（0 是有效 cohort 結果）、IP>0 → ip.doubleValue()；(2) `calculateScore` 的 COHORT dispatcher case 保留回 null 並加註解：該 signature 拿不到 IP，呼叫端必須 dispatch 到專屬 helper，避免未來誤 route；(3) `MeasureEvaluationService.buildResult` 在 CV 分派之後、proportion/ratio 共用路徑之前，加新分派 `if (COHORT) return buildCohortResult`；(4) `buildCohortResult` 只輸出 populations=[IP]、measureScore=count、measureScoreUnit="count"，不含 Denom/Numer placeholder；(5) Smoke scenario 04 expected.json 新增 `score: 4.0` assertion（先前 `_note` 說 backend 回 null 所以跳過——現在回 4.0）。新增 `MeasureScoreCalculatorTest` 7 tests 鎖 cohort helper 三路徑（null / zero / positive）+ dispatcher contract + proportion/ratio regression lock。全量 1201/1201 pass，smoke 4/4 pass。TFDA 追溯：需求 #259、設計 #260、風險 #261（安全性等級 B — FHIR interop 與指標正確性）、驗證 #262 | MeasureScoreCalculator, MeasureScoreCalculatorTest, MeasureEvaluationService, scripts/smoke/scenarios/04-cohort-adult-count/expected.json |[`5e5f5cc`](../../commit/5e5f5cc) |
| PAT-082 | 🧪 tooling | 2026-04-19 | 開發流程（smoke test 覆蓋） | **Smoke harness 補齊 ratio / CV / cohort scenario + 跨 scenario 隔離** — PAT-080 只有 proportion scenario，漏掉另外 3 種 scoring type（ratio/CV/cohort）。這 3 種走完全不同的 codegen 路徑（CV 尤其經過 `RenderMode.CV_MEASURE_POPULATION` 從 #239 出來的），沒測就等於沒 guard。修法：(1) 新增 `02-ratio-age-comparison`（Denom=seniors >=65 / Numer=very-elderly >=72 / score=66.67，Numer 設為 Denom 子集因為 backend 現狀 Numer CQL 帶 Denom 交集）；(2) `03-cv-count-adults`（populationGroups[0].observations 帶 aggregateMethod=Count，覆蓋 IP + measure-population + observation triple）；(3) `04-cohort-adult-count`（IP-only，locked 在 IP count 而非 measureScore 因為 backend 對 cohort 回 null 分數——另案 follow-up）。(4) 跨 scenario 隔離 bug：原設計假設 disjoint period 夠，但 pure-demographic 條件不 filter by period，scenario N 看得到 N-1 patients（測試時親眼見到 IP=12/15/19 累積）。修法：新 `lib/reset-fhir.sh` 用 HAPI conditional DELETE by `_lastUpdated=gt2000-01-01` 對 8 種 resource type（Patient, Encounter, Observation, Condition, Procedure, MedicationRequest, MedicationStatement, AllergyIntolerance）清空；`compose.override.yml` 加 `hapi.fhir.allow_multiple_delete=true` 讓 conditional DELETE 生效。並試了 `$expunge?_expungeEverything=true` 但 HAPI 只會 purge 軟刪除的 resource 不會 purge live ones，所以改走 DELETE。(5) run.sh 每 scenario 前 call reset-fhir.sh。全 4 scenarios 冷 cache 2m7s，全數 pass。TFDA 追溯：需求 #257（安全性等級 A） | scripts/smoke/scenarios/02-ratio-age-comparison, 03-cv-count-adults, 04-cohort-adult-count, lib/reset-fhir.sh, compose.override.yml, run.sh, README.md |[`3db33ec`](../../commit/3db33ec) |
| PAT-081 | 🐛 fix | 2026-04-19 | 後端（CQL 產生器：AgeRange period-binding） | **eCQM AgeRange 綁 Measurement Period 端點** — 根因：`ExpressionCqlEngine.buildAgeRangeExpression` 產出 `AgeInYears()`（無參數），FHIR 模型下依系統時鐘算年齡。對 eCQM 是錯的——回溯 2020 report 會套 2026 年齡，同一 measure 不同時間跑結果不同（reproducibility 失敗），也是 #PAT-080 smoke test p4 從 mid 變 senior 的根因。修法：(1) `BuildContext` 加 public `hasMeasurementPeriod` field（預設 false）；(2) `EcqmCqlBuilder.buildEcqmCql` set 為 true（eCQM 總是宣告 MP parameter）；(3) `mapUnitToAgeFunction(unit, bindToMP)` 新 signature — true 時 emit `AgeIn{Unit}At(end of "Measurement Period")`，false 時 emit `AgeIn{Unit}()`，涵蓋 year/month/week/day/hour；(4) `buildAgeRangeExpression(fields, ctx)` 新 signature，舊 1-arg 版 `@Deprecated(forRemoval=true)`；(5) CDS 路徑（`CqlArtifactBuilder`）不改，預設 false → 繼續 emit `AgeInYears()`（CDS 沒 MP 可 bind）；(6) Smoke scenario 01 p4 生日還原 1960-03-15（自然值），Numer=3 / score=60.0 永遠穩定；(7) `scripts/smoke/README.md` 移除 age-bracket-stability caveat。單元測試新增 9 項 lock 雙路徑（CDS vs eCQM × 全 unit variants），生成 CQL 經過手動 inspection 確認 emit `AgeInYearsAt(end of "Measurement Period") >= 18` 不再有裸 `AgeInYears()`。全量 1194/1194 pass。TFDA 追溯：需求 #252、設計 #253、風險 #254（安全性等級 B — 影響指標正確性與回溯性）、驗證 #255 | ExpressionCqlEngine, ExpressionCqlEngineEdgeCaseTest, EcqmCqlBuilder, scripts/smoke/scenarios/01-proportion-age-cohort, scripts/smoke/README.md |[`2afe446`](../../commit/2afe446) |
| PAT-080 | 🧪 tooling | 2026-04-19 | 開發流程（整合 smoke test） | **本機整合 smoke test harness** — 位址：`scripts/smoke/`。背景：既有 1183 個 unit test 全綠仍漏接 BUG-110 / BUG-111 / #230 / #247 家族（`ToInterval(null)` dispatch ambiguity、cross-library retrieve、wire-shape 改動、SignatureLevel=None）——這些都要跑到「真翻譯 / 真 engine」才爆。Code review #11 指出「使用者就是 smoke test」的 ~15min merge-to-feedback loop。修法：(1) 新 `scripts/smoke/run.sh` 單一指令帶起 isolated Docker compose（project name `smoke`，host 用 18xxx port 避免碰 dev stack／local postgres），依序走 `auth → seed-fhir → save → publish → evaluate → assert` 管線；(2) Scenario 結構 `scenarios/<NN-name>/{measure.json,bundle.json,expected.json}`；assertions 用 jq 對 measureScore（帶 tolerance）與 populations[]（逐 population-type）斷言；(3) 首個 scenario：`01-proportion-age-cohort` — 8 個 Patient 用 `AgeRange` element 產出 IP=7 / Denom=5 / Numer=3 / measureScore=60.0。其他 3 scoring type（ratio / CV / cohort）follow-up。(4) 本次實作過程順手抓到兩個真問題：assert.sh 在 Windows git-bash 下 CRLF 導致 pop_key 比對兩端皆為 null 的 false-positive pass（已修：改用 `while IFS= read` + `tr -d '\r'` + expected-is-concrete guard）；以及 `AgeRange` element 產 `AgeInYears()`（用系統時鐘）而非 `AgeInYearsAt(end of "Measurement Period")`，導致 population 結果會隨時間漂移（已用 age-bracket-stable birthdate 繞過，留 follow-up）。(5) `CLAUDE.md` 加開發慣例條款「push 前跑 `scripts/smoke/run.sh`」；冷 cache 約 2 分鐘、熱 cache 約 60-90 秒。TFDA 追溯：需求 #250（安全性等級 A — dev tooling，不影響 clinical correctness） | scripts/smoke/{run.sh,compose.override.yml,lib/\*.sh,scenarios/01-proportion-age-cohort/\*,README.md}, CLAUDE.md |[`6ef0ac9`](../../commit/6ef0ac9) |
| PAT-079 | 🐛 fix | 2026-04-18 | 全端（部署一致性） | **前端 React Query cache 隨後端部署自動失效** — 根因：`useModifiers` 用 `staleTime: Infinity`，`useEcqmModifiers` 用 `STALE_1M`。#230 更動 `ModifierDefinition` wire shape（`resourceAlias/whereClause` → `during.{alias, dateFieldSpec}`）後，保留舊分頁的使用者會卡在 cached 舊 schema，UI 引用已消失欄位（靜默 undefined），直到 Cmd+Shift+R 才解。修法：(1) 後端新 `VersionController` 端點 `/api/version` 回 `{startupTime, commitSha, version}`（unauth、無 DB、永不快取），`SecurityConfig` 放行；`startupTime` 為 JVM 啟動時間，任何 restart（含 hot-jar swap）都會變，比 commitSha 更保守；(2) 前端 `VersionCheckProvider` 掛最外層，每 5 分鐘 poll 對比 mount 時 snapshot，變動時 `queryClient.invalidateQueries()` + i18n toast「系統已更新」；(3) `useModifiers` staleTime `Infinity` → `STALE_5M` 當 defense-in-depth；(4) `docker-compose.yml` passthrough `APP_COMMIT_SHA` 環境變數；(5) `application.yml` 新 `app.commit-sha` / `app.version` 設定；2 個單元測試鎖住 `/api/version` unauth-readable 與 startupTime per-process 穩定性。TFDA 追溯：需求 #248（安全性等級 A） | VersionController, VersionControllerTest, SecurityConfig, application.yml, versionApi, VersionCheckProvider, useModifiers, main.tsx, common.json (en/zh-TW), docker-compose.yml |[`561cb53`](../../commit/561cb53) |
| PAT-078 | 🐛 fix | 2026-04-18 | 後端（CQL 翻譯器設定） | **CQL Translator SignatureLevel 修正** — 根因：`LibraryManagerFactory.defaultOptions()` 與 `buildOptions()` 均未呼叫 `setSignatureLevel`，繼承預設值 `None`，導致 translator 對 `FHIRHelpers.ToString` / `ToDateTime` / `ToInterval` 等多 overload function 只在 ELM 內留名稱、不嵌參數型別簽章 → engine 執行期只能靠 runtime argument type 挑 overload，null / base-type argument 會觸發 `AmbiguousCall` 例外或挑錯 overload 做錯誤型別 coercion（與 BUG-110 / BUG-112 同家族 dispatch ambiguity）。修法：兩個 options 工廠均鏈式加 `.withSignatureLevel(LibraryBuilder.SignatureLevel.Overloads)`，讓 translator 對任何 >1 overload 的 function 在編譯期就把參數簽章寫入 ELM。新增 `LibraryManagerFactoryTest` 單元測試鎖住「signatureLevel ∈ {Overloads, All}」不變式，回歸時 CI 直接擋下。選 Overloads 而非 All：ELM 體積增加最小（只對多 overload function 加 metadata），單 overload function 零影響。TFDA 追溯：需求 #243、設計 #244、風險 #245、驗證 #246 | LibraryManagerFactory, LibraryManagerFactoryTest |[`3fc8711`](../../commit/3fc8711) |
| PAT-068 | ✨ feature | 2026-04-18 | 前端（Authoring UX） | **Modifier chain 中間插入 UI** — 原本 `ArtifactElementBody` 只能在 modifier chain 尾端 append 新 modifier（filter 用 `getEffectiveReturnType()` 回傳鏈尾 type），當使用者已加了 `MostRecent`（`list_of_observations` → `observation`）+ `QuantityValue`（`observation` → `system_quantity`）後想再加 `DuringMeasurementPeriod`（需要 `list_of_observations`）就不會出現在下拉，被迫拆掉尾端幾個重組。修法：(1) `modifierUtils.ts` 新增 `getReturnTypeAtPosition(baseType, modifiers, insertAtIndex)` 計算任一插入位置的類型；(2) `ArtifactElementBody` 於每個 modifier card 前渲染一個 `+` inline button（hover 時變明顯），點擊開啟對話框並帶入目標 index；(3) `SelectModifiersDialog` 新增 `insertAtIndex` prop，filter 改用該位置的類型；(4) 插入後呼叫既有 `validateModifierChain` 自動剪除下游型別不相容的 modifier（副作用警告：極端情境下使用者看到的 chain 會比預期短，此時 ExpressionPhrase 自然會顯示新的鏈）。新增 `modifierUtils.test.ts` 8 個單元測試覆蓋 base type / 中間位置 / incomplete modifier skip / empty chain 等情境 | ArtifactElementBody.tsx, modifierUtils.ts, authoring.json (en/zh-TW), modifierUtils.test.ts |[`cd39eb3`](../../commit/cd39eb3) [`c5e8b4a`](../../commit/c5e8b4a) |
| PAT-067 | ✨ feature | 2026-04-18 | 全端（eCQM 期間過濾） | **Measurement Period filter modifier** — 修正根因：原本 eCQM 傳入的 Measurement Period 參數被宣告 + 傳到 engine 但**沒有任何 CQL 子句使用它**（authoring UI 沒有「在 Measurement Period 期間內」選項），導致不同年度跑同一 eCQM 得到相同結果（如 a1c_dm 跑 2025/2026 數值相同）。修法：新增 `DuringMeasurementPeriod` modifier 系列（Observation/Condition/Encounter/Procedure/MedicationRequest/MedicationStatement 共 6 個 resource type），每個 modifier 在 `modifiers.json` 定義 `resourceAlias` + `whereClause`，後端 `ExpressionCqlEngine` 新增 `case "DuringMeasurementPeriod"` 渲染新模板 `DuringMeasurementPeriod.ftl` 產出 `(expr) alias where ... overlaps/during "Measurement Period"` 的 CQL 查詢。Observation/Procedure 用 `(X.effective/performed as Period) overlaps ... or (... as dateTime) during ...` 同時支援 Period 與 dateTime 型態。`ModifierService` 新增 `getById()` 供引擎解析 alias/whereClause。`ModifierDefinition` 新增 `resourceAlias` + `whereClause` 欄位。3 個新 unit test（observation/encounter/unknown-id fallback）。使用者在 eCQM builder 的 Observation/Condition 等元素上加這個 modifier 即可讓期間輸入真正生效 | modifiers.json, DuringMeasurementPeriod.ftl, ExpressionCqlEngine, ModifierService, ModifierDefinition, ExpressionCqlEngineTest |[`81694d5`](../../commit/81694d5) [`119ed01`](../../commit/119ed01) |
| BUG-109 | 🛡️ security | 2026-04-18 | 後端（依賴升級） | backend Trivy CVE fix — 升級 `spring-boot-starter-parent` 3.5.10→3.5.12 覆蓋 `spring-boot-starter-actuator` 的 CVE-2026-22731/22733；explicit override `tomcat.version=10.1.54`（CVE-2026-29145/24734/34483/34487）、`thymeleaf.version=3.1.4.RELEASE`（CVE-2026-40477/40478）、`spring-security.version=6.5.9`（CVE-2026-22732 CRITICAL）；`tools.jackson.core:jackson-databind` + 新增 `jackson-core` 3.1.0→3.1.1（GHSA-2m67-wjpj-xhg9）；frontend `axios` ^1.13.5→^1.15.0（CVE-2025-62718 SSRF CRITICAL + CVE-2026-40175 prototype-pollution RCE CRITICAL）；`.trivyignore` 追加 CVE-2026-34359/34361 + VEX exposure 說明 | backend/pom.xml, backend/.trivyignore, frontend/package.json | [`38f4e5d`](../../commit/38f4e5d) [`d5b6afc`](../../commit/d5b6afc) |
| BUG-108 | 🐛 fix | 2026-04-18 | 全端（eCQM 儀表板） | eCQM 儀表板分數趨勢多指標混軸難辨識 — 所有指標擠同一張 0-100 的折線圖、圖例用短 `name`（如 `2`/`5`/`6`）導致使用者無法辨識；修法：(1) `ScoreTrendChart` 新增 `ToggleButtonGroup` 切換「指標網格（sparkline grid）」與「疊圖比較（含 chip 多選）」兩種顯示模式，sparkline Y 軸自動縮放避免比率/數量混軸；(2) `DashboardService.getTrends` 改以 `MeasureDefinition.title`（fallback name）作為圖例 displayName 取代 report 的原始 `measureName`；i18n 新增 `dashboard.chartMode.{multiples,overlay,label}` | ScoreTrendChart, DashboardService, measures.json (en/zh-TW) | [`2a7e617`](../../commit/2a7e617) |
| BUG-107 | 🐛 fix | 2026-04-18 | 後端（CQL 執行正確性）+ 前端（TWCDI 範本） | 編輯器 Run CQL 改執行舊版 DB 儲存內容 — 根因：`CqlTranslator.fromText` 翻譯完的 `CompiledLibrary` **沒被 seed 進 `libraryManager.compiledLibraries`**，`engine.evaluate` 以 VersionedIdentifier 向 libraryManager 反查，cache miss → `DatabaseLibrarySourceProvider` 撈 `cql_library` 表舊版 → 引擎執行舊版（user 看到的 locator 不符 / 預期外的 sort by）。修法：新 `seedCompiledLibrary(...)` helper 為單一入口，put cache **並按 name 排序 `statements.def`**（engine `Libraries.resolveExpressionRef` 用 binarySearch，translator 不自動排序 → 不排會爆 `Could not resolve expression reference`）；適用 `doExecute` + `translateOnce` 兩條路徑；Regression 測試 `CqlExecutionIntegrationTest.LibraryResolutionRegressionTest` 保證 fresh CQL 永遠贏過 DB。TWCDI 8 個範本全面改用 `return { id, text, status, date }` tuple 結構顯示 human-readable text，choice type 改 `as Quantity` / `as CodeableConcept`，日期以 `.value` 避免 `ToDateTime(null)` dispatch ambiguity | CqlExecutionService (seedCompiledLibrary helper), CqlExecutionIntegrationTest, twcdiTemplates.json |[`45aee4d`](../../commit/45aee4d) |
| BUG-106 | 🐛 fix | 2026-04-17 | 後端（CQL 執行型別 dispatch） | FHIRHelpers ToString 重載歧義（partial） — `ComparableR4FhirModelResolver.resolveType(Object)` 新 override：對 HAPI `Enumeration<T>` 值回傳底層具體 Enum class（例如 `MedicationRequestStatus.class`）而非泛型 `Enumeration.class`，解決 CQL 引擎 dispatcher 在 FHIRHelpers 251 個 per-Enum ToString 重載間無法唯一匹配的 `Ambiguous call` 問題；同時於 `CqlExecutionService` harvest 階段把 engine DebugResult 的 CqlException 附上 `SourceLocator` 前綴以利定位。**剩餘**：`DateTimeType is not comparable` 另源（issue #206/#207 追蹤） | ComparableR4FhirModelResolver, CqlExecutionService |[`ec1bd03`](../../commit/ec1bd03) |
| PAT-066 | 🐛 fix | 2026-04-17 | 全端（病人上傳 + CQL 執行 UX） | Patient generator upload round-trip + CQL warnings/errors surfacing + TWCDI templates + Quick Access layout — (1) 病人產生器 Bundle 改用 `PUT Resource/id` 保留 client-side ID，移除非法雙 `medication[x]` 欄位；(2) `CqlExecutionResponse` 新增 `warnings`，`CqlExecutionService` 收集 translator warnings + per-expression runtime exceptions + 安裝 `DebugMap(loggingEnabled)` 讓引擎內部的 `CqlException` 進 `DebugResult`；前端 Redux + ExecutionPanel 顯示 warning/error Alert；(3) TWCDI 8 個範本改用 `.value` + declared code `~` workaround 繞過 HAPI CodeType dispatch 歧義（正式修法見 BUG-106）；(4) LibraryQuickAccess 三個無 `maxHeight` 的 List 加內捲防止視覺重疊 | fhirPatientGenerator, GenerationResultPanel, CqlExecutionResponse, CqlExecutionService, ExecutionPanel, executionSlice, useCql, twcdiTemplates.json, LibraryQuickAccess, editor.json (en/zh-TW) |[`7a9821e`](../../commit/7a9821e) |
| PAT-064 | ✨ feature | 2026-04-16 | 後端（AI 除錯知識庫） | AI Fix Suggestion Knowledge Base — 把前端教學內容（TroubleshootingGuide / CodeableConcept / FHIRHelpers / TWCORE / AdvancedTopics / eCQM populations）抽成 8 個 YAML 檔，新 CqlKnowledgeBase 服務根據錯誤訊息做 keyword 匹配選 top-3 條目注入 AI system prompt；CloudAiService + OllamaService 整合；BASE_SYSTEM_PROMPT + buildSystemPrompt(relevant) 拆分，原 SYSTEM_PROMPT 保留為 @Deprecated | CqlKnowledgeBase, KnowledgeEntry, CqlFixPromptHelper, 8 YAML files |[`1412ae7`](../../commit/1412ae7) [`5cfe104`](../../commit/5cfe104) [`7b2fd0f`](../../commit/7b2fd0f) |
| PAT-063 | ✨ feature | 2026-04-16 | 全端（eCQM 測試案例除錯） | eCQM Test Case Debug Mode + Population Membership Trace — 整合現有 Coverage 與新除錯功能為單一 Run 動作（Debug Mode switch 控制）；新 PopulationMembershipTrace 模型顯示每位患者在 IP→Denom→Numer 層級的 raw/effective 布林值與原因代碼（15 種 reason codes）；支援 proportion/ratio/CV/cohort 四種計分類型；PhaseError 帶 BUNDLE_PARSE/CQL_TRANSLATION/CQL_EXECUTION/POPULATION_EVAL 分類；舊 `/run-with-coverage` 端點保留 backward compat | PopulationEvaluator.buildTestCaseTrace, TestCaseService, TestCasesTab, PopulationTracePanel |[`de9964d`](../../commit/de9964d) [`3cefce6`](../../commit/3cefce6) [`1d4dbae`](../../commit/1d4dbae) |
| PAT-062 | ✨ feature | 2026-04-16 | 全端（CDS Hooks 除錯） | CDS Hook Debug Mode + 結構化診斷 — 8 合 1 除錯套件：Sandbox/Invoke 支援 debug mode（DebugPanel 顯示 expression/retrieve traces）、phase-wrapped 錯誤（phase + errorType + stack summary）、prefetch per-key 狀態、context 診斷警告、FHIR server 錯誤分類、compiled ELM viewer、admin 最近調用紀錄（ring buffer 100 筆）、dry-run 模式（跳過 CQL 執行） | CdsResponse.CdsDebugInfo, CdsInvocationService, CdsRecentInvocationsService, CdsDebugPanel, RecentInvocationsPanel |[`caf842d`](../../commit/caf842d) |
| PAT-061 | ✨ feature | 2026-04-13 | 全端（CDS Hooks） | CDS Hook Context Requirements — 依 CDS Hooks 規範為 6 種 hook 類型加入必要 context 欄位驗證（patient-view 需 userId+patientId、order-select 需 selections+draftOrders 等）；後端驗證+discovery 回應帶 contextFields；前端動態渲染 context 欄位與 object tabs；新增 appointment-book 的 appointments 支援 | HookContextRequirements、CdsHooksService、SandboxPanel、InvokeServicePanel、ManageServicesPanel |[`7af8e11`](../../commit/7af8e11) |
| BUG-105 | 🐛 fix | 2026-04-03 | 全端（品質指標儀表板） | 品質報告混合計分類型平均 + 趨勢圖難理解 — 品質報告把 proportion（百分比）和 continuous-variable（原始數值如 HbA1c 5.6）混在一起平均，改為只平均比例型指標；趨勢圖 X 軸標籤過長改為簡短格式，修正 measureName 儲存 ID 而非名稱 | DashboardService、MeasureEvaluationService、QualityReportPanel |[`be59cf6`](../../commit/be59cf6) |
| BUG-104 | 🐛 fix | 2026-04-02 | 後端（eCQM 驗證/CQL 產生） | eCQM 程式庫定義儲存失敗 — LibraryDefinitionPicker 建立 `externalCqlElement` 元素但後端驗證器不認識此型別，導致 ValidationException；新增 TemplateService BUILTIN_REFERENCE_TYPES + ExpressionCqlEngine CQL 產生/include 收集 | TemplateService、ExpressionCqlEngine | |
| PAT-065 | ✨ feature | 2026-03-29 | 前端（CQL 程式庫） | CQL Library Management UI — 完整複製 MADiE 程式庫系統：4-Tab 列表(All/Mine/Shared/Public)+搜尋排序、5-Tab 工作區(CQL Editor/Metadata/Dependencies/History/Sharing)、auto-save+Ctrl+S、版本管理(Major/Minor/Draft/Compare)、FHIR/CQL 匯入匯出、分享/轉移、依賴分析、eCQM LibraryDefinitionPicker 整合、i18n EN+zh-TW | 15 新檔案、+3234 行 | [`fbbb4a0`](../../commit/fbbb4a0) |
| BUG-103 | 🐛 fix | 2026-03-29 | 後端（CQL 執行） | CV 觀察值序列化遺失 — `toSerializable` 未處理 HAPI FHIR `DecimalType`，回傳 `String` 而非 `BigDecimal`，導致 `extractObservationValues` 無法識別為 `Number`，聚合結果為空；新增 `PrimitiveType<?>` 處理，解包為原始 Java 型別 | CqlExecutionService | [`de38f71`](../../commit/de38f71) |
| BUG-102 | 🐛 fix | 2026-03-29 | 後端（CQL 執行） | Bulk fetch 遺漏 FunctionRef 內的 Retrieve 類型 — `collectRetrieveTypes` 遇到 `FunctionRef`（繼承 `ExpressionRef`）時跳過，導致 `C3F.Verified([Observation])` 中的 Observation 未被提取，bulk fetch 不拉 Observation 資料，CV 量測 Measure Population 恆為 0 | CqlExecutionService | [`befa1d7`](../../commit/befa1d7) |
| BUG-101 | 🐛 fix | 2026-03-29 | 後端（CQL 產生） | Episode-based CV Measure Population 型別不匹配 — `preserveListReturn` 未阻止 MostRecent/QuantityValue 等修飾器，導致 Measure Population 回傳 `System.Quantity` 而非資源列表，與 Measure Observation 函數簽名不匹配；新增 `isListCollapsingOrValueExtractingModifier` 檢查，跳過折疊列表和提取標量的修飾器 | ExpressionCqlEngine | [`c2360c1`](../../commit/c2360c1) |
| PAT-064 | ✨ feature | 2026-03-26 | 全端（品質量測） | Continuous-variable 觀察值聚合 — CQL 產生觀察函數 wrapper define、PopulationEvaluator 收集觀察值、MeasureScoreCalculator 實作 Average/Sum/Median/Min/Max/Count 聚合、前端顯示聚合統計（取代百分比） | 9 檔案、+313 行 | [`1541323`](../../commit/1541323) |
| BUG-084 | 🐛 fix | 2026-03-26 | 前端（eCQM） | 驗證按鈕只檢查 HTTP 狀態未檢查 CQL 翻譯結果 — 後端回 200+errors，前端誤判為驗證通過；現正確檢查 `response.success` 並顯示錯誤明細 | EcqmCqlPreviewTab、ecqmApi | [`c02cbbd`](../../commit/c02cbbd) |
| BUG-083 | 🐛 fix | 2026-03-26 | 後端（CQL 產生） | Code system 名稱不一致 — `code` 宣告使用前端 display name 但 `codesystem` 宣告使用原始 URI，導致含 Qualifier code 的 eCQM 發布失敗；將 codeSystems 從 Set 改為 Map 追蹤 URI→displayName 對應 | ExpressionCqlEngine、CqlArtifactBuilder、EcqmCqlBuilder | [`45cf21c`](../../commit/45cf21c) |
| PAT-063 | ✨ feature | 2026-03-26 | 全端（eCQM） | eCQM 觀察計算類型 — Continuous-variable 觀察函數新增 Duration of Period（住院天數等）及 Quantity Value（檢驗數值等）兩種計算模式，附 CQL 即時預覽；後端依 observationType 產生對應 CQL，含輸入驗證防注入 | EcqmObservationEditor、EcqmCqlBuilder、ecqmConstants | [`41e4a8b`](../../commit/41e4a8b) |
| PAT-062 | ⚡ perf | 2026-03-24 | 後端（CQL Engine） | ELM 預編譯快取 — 儲存量測定義時自動翻譯 CQL→ELM JSON 並存入 DB，執行時反序列化 (~10ms) 取代即時翻譯 (~1.5s/次)，含 corrupt ELM fallback + metadata-only 更新跳過重編譯 | V44 migration、MeasureDefinitionService preCompileElm | [`9ef5a9f`](../../commit/9ef5a9f) [`b0d65fb`](../../commit/b0d65fb) |
| BUG-100 | 🐛 bugfix | 2026-03-24 | 前端（病人產生器+品質指標） | 情境模板產生正確病人數 + 期間比較指標 Autocomplete — 情境模板原本固定只產生 1 位病人，改為依 recommended_patient_count 迴圈產生；期間比較「指標名稱」從純文字改為自動載入現有指標的下拉選單 | CustomGenerationConfig.numPatients、MeasureComparison Autocomplete | [`c2858c2`](../../commit/c2858c2) |
| BUG-099 | ⚡ perf | 2026-03-24 | 後端（品質量測） | 量測評估平行化 + TerminologyProvider 快取 — 病人 CQL 評估從循序改為 CompletableFuture 平行執行（99s→~20s），TerminologyProvider 依 server URL 快取避免重複建立 FHIR 客戶端，修復 ForkJoinPool 死鎖 + timeout 取消 futures | MeasureEvaluationService、FhirTerminologyService | [`e98463d`](../../commit/e98463d) [`87c278d`](../../commit/87c278d) |
| BUG-098 | 🐛 bugfix | 2026-03-24 | 前端（通知） | SSE 通知 JWT 靜默刷新 — SSE ticket 請求從原生 fetch 改為 Axios client，使過期 JWT 自動刷新，消除無限 401 重試迴圈 | useNotifications.ts | [`7720c98`](../../commit/7720c98) |
| PAT-060 | ✨ feature | 2026-03-24 | 全端（EHR 連線） | SMART Backend Services 驗證 — EHR 連線新增 OAuth 2.0 client_credentials + JWT assertion (RS384) 驗證方式，含 token 自動快取/刷新、per-connection locking、SSRF 防護（tokenEndpoint 驗證）、權限修正（test/search/import 端點加 @PreAuthorize）| V43 migration、SmartBackendTokenService、nimbus-jose-jwt PEM 解析 | [`b7a5345`](../../commit/b7a5345) |
| PAT-059 | ✨ feature | 2026-03-24 | 前端（病人產生器） | TW Core IG 假病人產生器 — 批次/自訂/情境模板三分頁，純前端 config-driven 架構（5 JSON 設定檔驅動 60+ 臨床項目），FHIR 資源產生（Patient/Encounter/Condition/Observation/Medication/MedicationRequest/AllergyIntolerance）+ 下載 JSON + 上傳 FHIR Server | 20 新檔案、新增 patientGenerator i18n namespace | [`c8b7512`](../../commit/c8b7512) |
| BUG-095 | 🔧 refactor | 2026-03-16 | 前端（全模組） | 前端全面簡化 — 主題色彩/i18n/效能/佈局修復 | 136 檔、+1334/-1146 行 | [`525d244`](../../commit/525d244) |
| PAT-058 | ✨ feature | 2026-03-15 | 前端（學習中心） | CQL 官方規範缺漏補齊 — 語言參考（條件式/Null/型別/Date-Time/字串）+ FHIRPath & ELM 解讀 + eCQM 實戰教學（糖尿病 HbA1c 指標建構 + CMS146 範例）| 13 Tab、+1372 行 | [`67540f0`](../../commit/67540f0) |
| BUG-094 | 🐛 bugfix | 2026-03-15 | 前端（全頁面） | MUI v7 Grid container prop 修復 — Dependabot 升級 MUI v5→v7 後 Grid 缺少 container prop 導致版面垂直堆疊，修復 15 個檔案 | EditorPage/CdsPage/MeasuresPage 等 | [`c93e0ef`](../../commit/c93e0ef) |
| PAT-057 | ✨ feature | 2026-03-15 | 前端（學習中心） | CQL 學習中心全面優化 — Monaco 練習場 + 「在編輯器中開啟」按鈕 + 進階主題/排錯指南/速查表 3 新 Tab + 測驗弱項提示 + i18n 修復 + 速查表擴充（基本結構 12 項 + 資料存取 13 項）| 10 Tab、+1292 行 | [`3edac7b`](../../commit/3edac7b) [`acd9136`](../../commit/acd9136) |
| BUG-093 | 🔧 refactor | 2026-03-15 | CI/CD（Deploy） | 移除 K8s 部署步驟 — 保留 Build+Push GHCR + Verify Migrations + Notify，修復 Deploy workflow 失敗 | -149 行 | [`6af3f31`](../../commit/6af3f31) |
| BUG-092 | 🔒 security | 2026-03-14 | 後端（依賴安全） | Jackson CVE 修復 — jackson-bom 2.21.1 (GHSA-72hv-8253-57qq) + tools.jackson 3.1.0 (CVE-2026-29062) — Security Scan 全綠 | logstash-logback-encoder 傳遞依賴 | [`ca9ebf7`](../../commit/ca9ebf7) |
| PAT-056 | ✨ feature | 2026-03-14 | 前端（品質指標） | eCQM CQL Tab 從程式庫匯入 — 新增 ImportCqlFromLibraryDialog（搜尋/選擇/確認覆蓋）+ MeasureCqlTab 匯入按鈕，複用現有 CQL Libraries API | i18n EN+zh-TW | [`9a96f5f`](../../commit/9a96f5f) |
| PAT-055 | ✨ feature | 2026-03-14 | 後端（CQL Engine） | CQL Framework 3.29→4.5.0 升級 — Kotlin Multiplatform 架構、groupId 遷移 (info→org.cqframework)、LibrarySourceProvider kotlinx.io.Source 適配、EvaluationParams builder、RestFhirRetrieveProvider +ModelResolver | 821 tests pass、6 檔案修改 | [`0d0f28a`](../../commit/0d0f28a) |
| PAT-054 | 🔒 security | 2026-03-14 | 後端+CI（依賴升級） | Spring Boot 3.2→3.5.10 + HAPI FHIR 7.0→8.6.6 + Jackson 2.21.1 + ucum 1.0.9 + CI 密碼改 Secrets 參照 — 修復 Trivy CVE 掃描 + GitGuardian 告警 | 821 tests pass、Spring FW 6.2.x 對齊 | [`b532232`](../../commit/b532232) |
| PAT-053 | ✨ feature | 2026-03-14 | 前端（Landing/Learn） | CQL 學習中心與 Landing Page 改造 — 新首頁（Hero+功能卡片+TWCORE 範例展示）+ /learn 學習中心（CQL 入門/核心概念/TWCORE IG/互動範例/快速開始/練習場/自我測驗）+ landing i18n namespace (EN+zh-TW) | 16 新檔案、所有 CQL 範例符合 TWCORE IG | [`cec2f1f`](../../commit/cec2f1f) |
| PAT-052 | 🔧 refactor | 2026-03-13 | 前端（常數管理） | 硬編碼常數提取 — 新增 queryConstants.ts + 擴充 timing.ts / layout.ts，22 檔 magic number 改為集中常數 (debounce/staleTime/尺寸) | 消除重複、統一維護 | [`8307c67`](../../commit/8307c67) |
| BUG-091 | 🐛 bugfix | 2026-03-13 | 後端（測試） | 修復 6 個預存測試失敗 — GEH timeout 504 斷言、VSAC OID 驗證、ApiKey name 可選、TestCase mock 方法名 | 821 tests 全過 | [`b78de09`](../../commit/b78de09) |
| PAT-051 | ✨ patch | 2026-03-13 | 全端（Observability） | X-Request-ID 端對端追蹤 — 前端 UUID 產生 + RequestTracingFilter MDC + 結構化日誌 + 稽核紀錄 request_id + Error Response requestId | TFDA 稽核軌跡 + 除錯效率 | [`7fc2e86`](../../commit/7fc2e86) |
| PAT-050 | 🔒 security | 2026-03-13 | 後端（Auth/JWT） | JWT 即時撤銷 — token_version + Caffeine 快取 (30s TTL)，登出/停用/角色變更/密碼重設觸發 bump，撤銷窗口從 15 分鐘縮至 ~30 秒 | 醫療稽核必備：無 Redis 依賴 | [`16fa19c`](../../commit/16fa19c) |
| PAT-049 | ✨ patch | 2026-03-13 | 前端（表單驗證） | 前後端欄位約束對齊 — fieldConstraints.ts 共用常數 + 6 表單元件 maxLength 對齊後端 @Size + 長文字欄位字數計數 + StringField/TextAreaField maxLength prop | 防止前端超長輸入導致 400 | [`726085f`](../../commit/726085f) |
| PAT-048 | ✨ patch | 2026-03-13 | 法規自動化（CI/CD） | 法規文件完整性防護 — Issue 快照 + SHA-256 清單 + 每週排程備份 | 防 Issue 竄改破壞 TFDA 文件 | [`356430c`](../../commit/356430c) |
| BUG-090 | 🔒 security | 2026-03-13 | 後端（CQL Engine） | CQL 執行資源耗盡防護 — Translation timeout + Retrieve 結果上限 + 回應集合截斷 + Prefetch 資源上限 + Patient 分頁上限 + Fallback cache 有界 | 防 DoS / OOM | [`9fe7b27`](../../commit/9fe7b27) |
| BUG-089 | 🐛 bugfix | 2026-03-13 | 前端（TestCase Builder） | TWCORE 術語瀏覽器代碼欄位同步修復 — CodingField/CodeField 本地狀態未隨外部更新同步 | useState 初始化只執行一次 | [`fd879a3`](../../commit/fd879a3) |
| PAT-047 | 🔧 refactor | 2026-03-13 | 後端（DB） | Flyway 安全強化 — 41 份 rollback 腳本 + baseline-on-migrate 生產關閉 + lock-retry-count + CI validate 步驟 | 叢集環境 + 緊急回退能力 | [`85cb768`](../../commit/85cb768) |
| PAT-046 | 🔧 refactor | 2026-03-13 | 前端（Builder） | escapeCqlString 技術債清除 — 提取共用 utils/cqlString.ts，消除 CdsCardBuilder / RecommendationBuilder 重複定義 | 含 formatFieldValue + FieldState type | [`c3cec5a`](../../commit/c3cec5a) |
| PAT-044 | ✨ patch | 2026-03-13 | 前端（Editor） | CQL Monarch 語法自動產生 — 從官方 ANTLR grammar 提取關鍵字 + CI drift 檢查 | 修正 15 缺漏 + 14 多餘關鍵字 | [`8ac2917`](../../commit/8ac2917) |
| PAT-043 | ✨ patch | 2026-03-13 | CI/CD（法規） | TFDA 法規追溯 CI 強制檢查 — PR 必須引用 Issue + B/C 等級完整追溯鏈驗證 | 防止法規文件遺漏 | [`8a77dc2`](../../commit/8a77dc2) |
| PAT-042 | ⚡ perf | 2026-03-13 | 前端（Editor） | Monaco-Redux 解耦 — 移除每次按鍵 dispatch，改為 blur/save 同步 + editor ref 架構 | 消除 per-keystroke re-render | [`a1a1caf`](../../commit/a1a1caf) |
| PAT-041 | ✨ patch | 2026-03-13 | CI/CD（後端） | PostgreSQL Migration CI 防護 — Flyway + JPA validate 對 PG service container 驗證 | 防止 H2/PG schema drift | [`4edcd3b`](../../commit/4edcd3b) |
| BUG-088 | 🔒 security | 2026-03-13 | CQL 產生引擎（後端） | CQL 注入防護 — identifier 跳脫 + include 語句消毒 + 表達式樹字元驗證 | 6 CRITICAL injection points fixed | [`b7d7f08`](../../commit/b7d7f08) |
| BUG-087 | 🔒 security | 2026-03-12 | 安全性（全端） | 滲透測試修復 — Mass Assignment / SSRF / XSS / 授權 / Rate Limiting / 密碼洩漏 / XFF 欺騙 | 4 CRITICAL + 10 HIGH | [`5de093c`](../../commit/5de093c) |
| PAT-040 | ✨ patch | 2026-03-12 | 法規自動化（全端） | TFDA 法規文件自動化工作流 — Issue/PR Templates + 產生腳本 + CI 整合 + 40 個法規 Issues | GitHub Templates + Python Scripts + CI | [`3405f5d`](../../commit/3405f5d) |
| BUG-086 | 🐛 bugfix | 2026-03-12 | 後端（Auth） | 註冊 email 欄位改為選填 — 移除 @NotBlank 驗證 | Backend (Auth) | [`7dd2349`](../../commit/7dd2349) |
| BUG-085 | 🐛 bugfix | 2026-03-12 | 前端（全模組） | Dark mode 硬編碼色彩修正 — 13 檔案 + Monaco 貼上修復 | Frontend (Editor, FHIR, Measures, Builder, Authoring) | [`8379115`](../../commit/8379115) |
| PAT-039 | ✨ patch | 2026-03-09 | eCQM + CDS Authoring（全端） | eCQM 儲存修復（JPA @Transient + 部分更新）+ 參數動態預設值輸入 | Backend (eCQM) + Frontend (Authoring) | [`662f5af`](../../commit/662f5af) |
| PAT-038 | ✨ patch | 2026-03-09 | CQL Builder + eCQM（全端） | includes concept 運算子 + 參數 Interval 修復 + eCQM 驗證器修復 + code/display 修飾器 | Backend (eCQM) + Frontend (Builder) | [`9e82843`](../../commit/9e82843) |
| PAT-037 | ✨ patch | 2026-03-08 | CQL Builder（前端） | Query Builder 擴充 — with/without 關聯查詢 + let 區域變數 + distinct 去重 + where 值自動引號 | Frontend (Builder) | [`15c1877`](../../commit/15c1877) |
| PAT-036 | ✨ patch | 2026-03-07 | CQL Builder（全端） | FHIR 代碼瀏覽器 + 測試修復 + TWCOREDATA Dockerfile 修復 | Backend (Tests) + Frontend (Builder) + TWCOREDATA | [`a2eade1`](../../commit/a2eade1) |
| PAT-035 | ✨ patch | 2026-03-06 | CDS Authoring（全端） | 修飾器連續新增 + Element Name UX 改善 + Numeric Value 修飾器 | Backend + Frontend (Authoring) | [`c77e6f6`](../../commit/c77e6f6) |
| PAT-034 | ✨ patch | 2026-03-06 | CDS Authoring（全端） | 基礎元素四則運算 — 支援元素間 +−×÷ 計算（如 BMI = 體重÷身高²） | Backend + Frontend (Authoring) | [`080aec0`](../../commit/080aec0) |
| BUG-084 | 🐛 bugfix | 2026-03-06 | CDS Hooks（後端） | CDS 卡片中文亂碼修復 — 移除雙重 HTML 跳脫（`&#39;` 問題） | Backend (CDS) | [`f782273`](../../commit/f782273) |
| PAT-033 | ✨ patch | 2026-03-06 | CQL Builder（前端） | CQL Builder 面板增強 — 型別標籤、修飾鏈、依賴圖、基礎元素、驗證面板 + 程式碼品質/安全修復 | Frontend (Builder) | [`f782273`](../../commit/f782273) |
| PAT-032 | ✨ patch | 2026-03-06 | eCQM（全端） | eCQM 外部 CQL 程式庫支援 — 全端上傳/解析/管理 + 可複用 ExternalCqlView 元件 | Backend + Frontend (eCQM) | [`b88bd32`](../../commit/b88bd32) |
| PAT-031 | ✨ patch | 2026-03-06 | eCQM（前端） | eCQM 基礎元素 + 參數分頁功能啟用 — 複用 CDS Authoring 元件（BaseElements + Parameters） | Frontend (eCQM) | [`8c50113`](../../commit/8c50113) |
| PAT-030 | 🔒 security | 2026-03-05 | 後端配置 | 配置風險修復 — CallerRunsPolicy + CORS 萬用字元拒絕 + Prometheus 認證 + 移除 XSS 反序列化器 | Backend (Config) | [`11c0f84`](../../commit/11c0f84) |
| PAT-029 | 🔒 security | 2026-03-05 | eCQM（後端） | eCQM 風險修復 — XSS 偵測改用 HtmlUtils + 結構驗證（元素/修飾詞/名稱唯一性）+ 發佈前 CQL 驗證 | Backend (eCQM) | [`326e5fb`](../../commit/326e5fb) |
| PAT-028 | ✨ patch | 2026-03-05 | eCQM（前端） | eCQM 工作區存檔功能 — Save 按鈕 + Ctrl+S + 狀態指示器 + 未儲存變更防護 | Frontend (eCQM) | [`7019613`](../../commit/7019613) |
| PAT-027 | 🌐 i18n | 2026-03-05 | eCQM（前端） | eCQM 撰寫全模組 i18n 繁體中文翻譯 — 12 元件 + ecqm namespace + 懶載入 | Frontend (eCQM) | [`0fe60a8`](../../commit/0fe60a8) |
| BUG-083 | 🐛 bugfix | 2026-03-05 | CDS Authoring（前後端） | CQL Retrieve 使用 element_name 而非 code display — buildGenericResourceExpression 迴圈提前 return + save 驗證錯誤未顯示 | 邏輯錯誤 / UX | [`52fd78c`](../../commit/52fd78c) |
| PAT-026 | ✨ patch | 2026-03-05 | 安全性 | CQL 注入修復 + XSS 修復 — escapeCqlString 補齊 + dangerouslySetInnerHTML escapeValue | Backend (Authoring) + Frontend (全模組) | [`f1fe52e`](../../commit/f1fe52e) |
| PAT-025 | ✨ patch | 2026-03-05 | 重構 | FreeMarker 模板引擎遷移 — CQL 產生器從字串拼接重構為模板架構 + 表達式樹 conjunction 前端重構 | Backend (Authoring, eCQM) + Frontend (Authoring) | [`f1fe52e`](../../commit/f1fe52e) |
| PAT-024 | ✨ patch | 2026-03-04 | eCQM | eCQM 視覺化 CQL 產生引擎 — 全端實作 + Publish 至 MeasureDefinition | Backend + Frontend (eCQM, Authoring) | [`e356957`](../../commit/e356957) |
| PAT-023 | ✨ patch | 2026-03-04 | 安全性 | JWT Refresh Token 滑動視窗過期 — 雙令牌架構 + 令牌輪換 + 重用偵測 | Backend + Frontend (Auth) | [`256f5d1`](../../commit/256f5d1) |
| BUG-082 | 🐛 bugfix | 2026-03-04 | CDS Authoring（前後端） | 元素模板繼承未解析 + React Hooks 順序違規 — 缺少元素名稱 + CDS Hooks 頁面崩潰 | 邏輯錯誤 / 框架違規 | [`8e8d4c8`](../../commit/8e8d4c8) |
| BUG-081 | 🐛 bugfix | 2026-03-04 | CQL 執行引擎（後端） | CQL 批次執行崩潰 + FHIR Token 搜尋管道符號轉義 — FHIRHelpers 歧義 + 查詢回傳 0 筆 | 邏輯錯誤 / API 誤用 | [`649ac67`](../../commit/649ac67) |
| BUG-080 | 🐛 bugfix | 2026-03-04 | CDS Authoring（前後端） | 多分頁同時編輯 Artifact 導致靜默資料覆蓋 — JPA @Version 樂觀鎖 + 前端衝突對話框 | 併發/效能問題 | [`9b46017`](../../commit/9b46017) |
| BUG-079 | 🐛 bugfix | 2026-03-04 | 安全性（後端） | Rate Limiting 分層強化 — 端點分級 IP 限流 + 使用者限流 + 大型 Payload 加權 | 安全漏洞（DoS / 資源耗盡） | [`6b72fec`](../../commit/6b72fec) |
| BUG-078 | 🐛 bugfix | 2026-03-04 | 安全性（前後端） | CDS Card XSS 3 層防護 — 前端安全渲染 + 後端 HTML 跳脫 + 反序列化器強化 | 安全漏洞（XSS） | [`d7fc37f`](../../commit/d7fc37f) |
| BUG-077 | 🐛 bugfix | 2026-03-04 | 安全性（後端） | 停用使用者 API Key 未失效 — 認證繞過漏洞 + 雙重防護修復 | 安全漏洞（認證繞過） | [`51af336`](../../commit/51af336) |
| BUG-076 | 🐛 bugfix | 2026-03-04 | 安全性（後端） | AuditFilter $export 未標記 PHI 存取 + 欄位溢位導致稽核寫入失敗 | 安全漏洞（稽核遺漏） |  |
| BUG-075 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CqlArtifactBuilder 測試補強 — LookBack / AgeRange / 空排除 / 括號驗證 + Windows 換行修復 | 測試遺漏 |  |
| BUG-074 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CQL 產生器 AgeRange / ValueComparison 複合條件缺少括號 — OR 群組內可讀性差 | 邏輯錯誤 |  |
| BUG-073 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | verifyArtifactOwnership 使用 IllegalArgumentException(400) 而非 ResourceNotFoundException(404) | 邏輯錯誤 |  |
| BUG-072 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CdsArtifactEntity 反序列化失敗被靜默吞掉，無 log | 配置遺漏 |  |
| BUG-071 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CQL 產生器靜默降級 — 未知 element type 或 modifier 被忽略，使用者無感知 | 邏輯錯誤 |  |
| BUG-070 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CqlGenerationService.generateCql() 無 try-catch — 畸形 JSON 導致 generic 500 | 邏輯錯誤 |  |
| BUG-069 | 🐛 bugfix | 2026-03-03 | CDS Authoring（後端） | CqlArtifactBuilder Singleton 可變 instance field — 並行請求互相覆蓋 | 併發/效能問題 |  |
| BUG-068 | 🐛 bugfix | 2026-03-03 | 安全性（後端） | AuditFilter PHI 稽核修復 — FHIR 三層路徑解析、顯式 phiAccess 旗標、查詢參數擷取 | 安全漏洞（稽核遺漏） | [`ec1a21c`](../../commit/ec1a21c) |
| BUG-067 | 🐛 bugfix | 2026-03-03 | 安全性（後端） | CDS Feedback 儲存型 XSS 修復 — @NoXss 驗證 + HtmlUtils.htmlEscape 雙層防護 | 安全漏洞（XSS） | [`e12e64b`](../../commit/e12e64b) |
| BUG-066 | 🐛 bugfix | 2026-03-03 | 安全性（後端） | CQL 執行逾時強化 — worker 中斷、AbortPolicy 防執行緒池耗盡、差異化 HTTP 狀態碼 | 安全漏洞（DoS / 資源耗盡） | [`d56c0a8`](../../commit/d56c0a8) |
| BUG-065 | 🐛 bugfix | 2026-03-03 | 安全性（後端） | CqlController IDOR 授權修復 + LIKE 萬用字元注入防護 | 安全漏洞（存取控制 / 注入） | [`57de58f`](../../commit/57de58f) |
| BUG-064 | 🐛 bugfix | 2026-03-02 | Monaco 編輯器（前端） | CqlEditor paste sanitization — Trojan Source bidi 防護、undo-safe executeEdits、Monaco 記憶體洩漏修復 | 安全漏洞 / 記憶體洩漏 | [`c84c8bd`](../../commit/c84c8bd) |
| BUG-063 | 🐛 bugfix | 2026-03-02 | 前端效能（前端） | useCqlEditor useCallback 優化 — translate/validate/execute 穩定引用 | 效能 |  |
| BUG-062 | 🐛 bugfix | 2026-03-02 | 安全性（後端） | FhirController 安全強化 — identifier 驗證、IG URL 驗證、RestTemplate 逾時、連線池耗盡防護 | 安全漏洞 / 配置遺漏 |  |
| BUG-061 | 🐛 bugfix | 2026-03-02 | 前端效能（前端） | Measure 元件效能 — useMemo、O(n²) 修復、搜尋防抖、console.error 替換 | 效能 / 程式碼品質 |  |
| BUG-060 | 🐛 bugfix | 2026-03-02 | 程式碼品質（前端） | Measure 元件重構 — scoreColors/downloadBlob/extractApiError 共用化 | 程式碼品質 |  |
| BUG-059 | 🐛 bugfix | 2026-03-02 | 程式碼品質（前後端） | Service 層安全強化 + Builder 元件去重 — 憑證洩漏、6 共用抽取、250 行刪減 | 安全漏洞 / 程式碼品質 |  |
| BUG-058 | 🐛 bugfix | 2026-03-02 | 安全性（後端） | Repository 層簡化 — 18 個死碼方法移除、LIKE 萬用字元注入修復、共用工具提取 | 程式碼品質 / 安全漏洞 |  |
| BUG-057 | 🐛 bugfix | 2026-03-02 | 安全性（後端） | Model DTO 驗證強化 — @Size 防 DoS、SSRF URL 驗證、@Pattern 約束、死碼清除 | 安全漏洞 / 程式碼品質 |  |
| BUG-056 | 🐛 bugfix | 2026-03-02 | 規則撰寫（前端） | CQL 預覽對話框程式碼文字在淺色模式下幾乎不可見 | UX 設計缺陷 |  |
| BUG-055 | 🐛 bugfix | 2026-03-01 | 安全性（後端） | Controller 輸入驗證強化 — require* helpers、Math.clamp、URI 安全、DigestUtils 抽取 | 安全漏洞 / 程式碼品質 |  |
| BUG-054 | 🐛 bugfix | 2026-03-01 | 安全性（後端） | Entity 安全強化 — mass assignment 防護、密碼/金鑰洩漏、API key SHA-256 雜湊、憑證加密 | 安全漏洞 |  |
| BUG-053 | 🐛 bugfix | 2026-03-01 | 認證系統（後端） | AuthController 安全強化 — SSO 錯誤訊息洩漏、JIT 競態條件、base URL header 信任 | 安全漏洞 |  |
| BUG-052 | 🐛 bugfix | 2026-03-01 | 規則撰寫（前端） | CDS 人工製品表格欄位錯位 — react-window 獨立 Table 未共享欄寬 | UX 設計缺陷 |  |
| BUG-051 | 🐛 bugfix | 2026-03-01 | Docker 基礎設施（後端） | 外部連線 CORS 被擋 + PNA header 未覆蓋動態 origin | 配置遺漏 / 邏輯錯誤 |  |
| PAT-022 | ✨ patch | 2026-02-28 | Authoring | TWCORE IG 範本支援（19 預設範本 + TW 代碼系統 + 目錄擴充） | Backend + Frontend (Authoring) | [`bf27974`](../../commit/bf27974) |
| PAT-021 | ✨ patch | 2026-02-27 | CDS | Hospital-Grade 改善：draftOrders + 預取解析 + 重大警示彈窗 | Backend + Frontend (CDS, i18n) | [`1b0a22a`](../../commit/1b0a22a) |
| BUG-050 | 🐛 bugfix | 2026-02-27 | CDS Hooks Sandbox（後端） | CDS 卡片 CodeableConcept 多 coding 只顯示第一個 | 邏輯錯誤 | [`aed0ecb`](../../commit/aed0ecb) |
| BUG-049 | 🐛 bugfix | 2026-02-27 | CDS Hooks Sandbox（後端） | CDS 卡片僅顯示資源參考而非過敏藥物名稱 | 邏輯錯誤 | [`8b67eb6`](../../commit/8b67eb6) |
| BUG-048 | 🐛 bugfix | 2026-02-27 | 術語查詢（後端） | RxNorm 代碼搜尋無結果 — FHIR 術語伺服器未載入 UMLS 授權碼表 | 外部服務限制 | [`fe50e2a`](../../commit/fe50e2a) |
| BUG-047 | 🐛 bugfix | 2026-02-27 | Monaco 編輯器（前端） | Fallback paste handler 非同步讀取 clipboardData 導致貼上失效 | 邏輯錯誤 | [`be3c6ce`](../../commit/be3c6ce) |
| BUG-046 | 🐛 bugfix | 2026-02-25 | Monaco 編輯器（Docker） | Docker 環境下 Monaco Editor 無法貼上（Ctrl+V 無效） | 配置遺漏 | [`ae9a0e3`](../../commit/ae9a0e3) |
| PAT-020 | ✨ patch | 2026-02-24 | Authoring | 分頁驗證錯誤明細（Tooltip + Alert） | Frontend (Authoring, i18n) | [`4efb3c8`](../../commit/4efb3c8) |
| BUG-045 | 🐛 bugfix | 2026-02-24 | 術語查詢（後端） | 代碼查詢本地 TWCORE IG 回傳 ValueSet 名稱且缺少 display name | 資料處理錯誤 | [`d5e150d`](../../commit/d5e150d) |
| BUG-044 | 🐛 bugfix | 2026-02-24 | CQL Builder（前端） | Retrieve Builder 依賴不存在的 C3F 外部函式庫致 CQL 無法解析 | 架構缺陷 | [`d5e150d`](../../commit/d5e150d) |
| BUG-043 | 🐛 bugfix | 2026-02-24 | Test Cases（前端） | TestCaseEditor expectedPopulations 被 React Query refetch 競態重置 | 架構缺陷 | [`5b09697`](../../commit/5b09697) |
| BUG-042 | 🐛 bugfix | 2026-02-23 | CQL Engine（後端） | ComparableR4FhirModelResolver 日期轉換破壞 FHIRHelpers 時態運算子 | 架構缺陷 | [`6534790`](../../commit/6534790) |
| BUG-041 | 🐛 bugfix | 2026-02-23 | CQL Engine（後端） | Encounter.class Java 保留字衝突致 CQL 路徑解析錯誤 | 邏輯錯誤 | [`6534790`](../../commit/6534790) |
| BUG-040 | 🐛 bugfix | 2026-02-23 | Test Cases（後端） | TestCaseService 缺少 Measurement Period 參數致時間過濾失效 | 配置遺漏 | [`6534790`](../../commit/6534790) |
| BUG-039 | 🐛 bugfix | 2026-02-23 | Test Cases（後端） | TestCaseService 查詢 FHIR Server 而非使用記憶體內測試 Bundle | 架構缺陷 | [`6534790`](../../commit/6534790) |
| BUG-038 | 🐛 bugfix | 2026-02-23 | 資料庫連線池（後端） | HikariCP 連線池耗盡導致所有 API 逾時、無法登入 | 配置遺漏 | [`ccdf3f2`](../../commit/ccdf3f2) |
| BUG-037 | 🐛 bugfix | 2026-02-23 | 術語查詢（前端） | Autocomplete freeSolo 以顯示標籤覆蓋系統 URL 致術語查詢 503 | 邏輯錯誤 | [`ccdf3f2`](../../commit/ccdf3f2) |
| BUG-036 | 🐛 bugfix | 2026-02-23 | 指標庫表格（前端） | MeasureLibrary 虛擬捲動表頭與內容欄位錯位擠壓 | UX 設計缺陷 | [`ccdf3f2`](../../commit/ccdf3f2) |
| PAT-019 | ✨ patch | 2026-02-22 | 跨模組 | 錯誤處理統一（Backend 例外層級 + Frontend 錯誤提取） | Backend (Controllers, Exceptions, Services) + Frontend (全模組) | [`645a775`](../../commit/645a775) |
| PAT-018 | ✨ patch | 2026-02-22 | 文件 | API 參考文件 + OpenAPI 規格檔 | 專案根目錄（API.md, openapi.yaml） | [`b6681c0`](../../commit/b6681c0) |
| PAT-017 | ✨ patch | 2026-02-22 | eQCM | 補完科別分類功能（篩選 + 指派） | Backend + Frontend (Measures) | [`b205335`](../../commit/b205335) |
| BUG-035 | 🐛 bugfix | 2026-02-22 | 品質指標儀表板（後端） | DashboardService 多處 NullPointerException 導致所有 Dashboard API 回傳 500 | 邏輯錯誤 | [`3f4c1c5`](../../commit/3f4c1c5) |
| BUG-034 | 🐛 bugfix | 2026-02-22 | 品質指標儀表板（前端） | Recharts ResponsiveContainer 初始化時計算 width/height 為 -1 | 配置遺漏 | [`3f4c1c5`](../../commit/3f4c1c5) |
| BUG-033 | 🐛 bugfix | 2026-02-22 | CQL 編輯器（前端） | 工具列 Undo/Redo 按鈕無效 — Redux 歷史與 Monaco 原生 undo 脫節 | 架構缺陷 | [`dca6617`](../../commit/dca6617) |
| PAT-016 | ✨ patch | 2026-02-21 | 安全性 | Okta SSO (OIDC) 整合 | Backend + Frontend (Auth, Admin) | [`7d48d6b`](../../commit/7d48d6b) |
| PAT-015 | ✨ patch | 2026-02-21 | FHIR | P2-8: EHR/HIS 整合連接器 | Backend + Frontend (FHIR, Measures) | [`3dbf07a`](../../commit/3dbf07a) |
| PAT-014 | ✨ patch | 2026-02-21 | eQCM | P2-9: 指標儀表板增強（Recharts） | Backend + Frontend (Dashboard) | [`3dbf07a`](../../commit/3dbf07a) |
| PAT-013 | ✨ patch | 2026-02-21 | 跨模組 | P2-10: 科別多租戶隔離 | Backend + Frontend (Auth, Measures, Admin) | [`3dbf07a`](../../commit/3dbf07a) |
| PAT-012 | ✨ patch | 2026-02-21 | eQCM | P2-11: 衛福部指標代碼對照 | Backend + Frontend (Measures) | [`3dbf07a`](../../commit/3dbf07a) |
| PAT-011 | ✨ patch | 2026-02-21 | 通知 | P1-5: 持久化通知系統 + 工作流程推播 | Backend + Frontend (Header) | [`b106739`](../../commit/b106739) |
| PAT-010 | ✨ patch | 2026-02-21 | eQCM | P1-6: FHIR Bundle 檔案上傳匯入 | Measures (Frontend) | [`b106739`](../../commit/b106739) |
| PAT-009 | ✨ patch | 2026-02-21 | eQCM | P1-4: 審核者欄位 + 退回原因 UI | Measures (Backend + Frontend) | [`b106739`](../../commit/b106739) |
| PAT-008 | ✨ patch | 2026-02-21 | eQCM | P1-7: 人類可讀文件匯出 | Measures (Backend + Frontend) | [`b106739`](../../commit/b106739) |
| PAT-007 | ✨ patch | 2026-02-21 | eQCM | 測試案例批次匯入 + 日期平移 | Measures (Frontend + Backend) | [`76cf867`](../../commit/76cf867) |
| PAT-006 | ✨ patch | 2026-02-21 | eQCM | Population Criteria 佈局優化 + Reporting 分頁 | Measures (Frontend + Backend) | [`3b66db3`](../../commit/3b66db3) |
| BUG-032 | 🐛 bugfix | 2026-02-21 | eCQM 資料需求（後端） | DataRequirements 未解析 ToConcept 包裝的 CodeRef 及 Case 包裝的日期屬性 | 資料處理錯誤 | [`b50d94a`](../../commit/b50d94a) |
| BUG-031 | 🐛 bugfix | 2026-02-21 | eCQM 種子資料（後端） | 種子 CQL 語法錯誤導致 DataRequirements 標籤頁空白 | 資料處理錯誤 | [`63a5781`](../../commit/63a5781) |
| BUG-030 | 🐛 bugfix | 2026-02-21 | API 客戶端（前端） | departmentApi/ehrApi/indicatorApi 使用原生 axios 無 JWT 攔截器致 401 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| BUG-029 | 🐛 bugfix | 2026-02-21 | 通知系統（前後端） | SSE EventSource 無法傳送 Authorization 標頭致 401 | 架構缺陷 | [`63a5781`](../../commit/63a5781) |
| BUG-028 | 🐛 bugfix | 2026-02-21 | Docker 基礎設施（後端） | DataInitializer 僅在 dev profile 啟用，Docker 環境無種子資料 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| BUG-027 | 🐛 bugfix | 2026-02-21 | Docker 基礎設施（後端） | PatientImportEntity @Lob 與 PostgreSQL schema validation 不相容 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| BUG-026 | 🐛 bugfix | 2026-02-21 | Docker 基礎設施（後端） | Flyway V24-V29 使用 H2 語法導致 PostgreSQL 部署失敗 | 配置遺漏 | [`63a5781`](../../commit/63a5781) |
| BUG-025 | 🐛 bugfix | 2026-02-21 | CDS Authoring（後端） | CQL 產生器未對 list 型別表達式自動加 exists() 導致驗證失敗 | 邏輯錯誤 | [`0d418f1`](../../commit/0d418f1) |
| BUG-024 | 🐛 bugfix | 2026-02-21 | eCQM 資料需求（後端） | DataRequirements 未解析 Equal/Equivalent + CodeRef 模式（如 E.class ~ "AMB"） | 資料處理錯誤 | [`53b19ca`](../../commit/53b19ca) |
| BUG-023 | 🐛 bugfix | 2026-02-21 | Test Case Builder（後端） | Encounter.class 下拉選單顯示 1115 個代碼而非 11 個 | 資料處理錯誤 | [`3fc6de0`](../../commit/3fc6de0) |
| PAT-005 | ✨ patch | 2026-02-20 | 安全性 | MeasureController 授權與 IDOR 修復 | Backend — MeasureController, ScheduledMeasureEvaluationService | [`b8f57a6`](../../commit/b8f57a6) |
| PAT-004 | ✨ patch | 2026-02-20 | 跨模組 | 術語查詢 Drawer + 測試案例草稿自動儲存 | Header, Terminology, Test Case Builder, Measures | [`7429103`](../../commit/7429103) |
| PAT-003 | ✨ patch | 2026-02-20 | i18n | 全平台國際化完成（Phase 5-9） | CDS, FHIR, Terminology, Authoring, Admin | [`b2b7b07`](../../commit/b2b7b07) |
| BUG-022 | 🐛 bugfix | 2026-02-20 | Test Cases（前端） | 測試案例結果表格族群名稱未中文化 | i18n 遺漏 | [`0260852`](../../commit/0260852) |
| BUG-021 | 🐛 bugfix | 2026-02-20 | eCQM 資料需求（後端） | DataRequirements 未解析 FHIRHelpers.ToString 包裝的屬性比較 | 資料處理錯誤 | [`66a9ee2`](../../commit/66a9ee2) |
| BUG-020 | 🐛 bugfix | 2026-02-20 | eCQM 資料需求（前後端） | DataRequirements 標籤頁未顯示 Where 子句中的篩選條件 | 架構缺陷 | [`8efa589`](../../commit/8efa589) |
| BUG-019 | 🐛 bugfix | 2026-02-20 | CDS Hooks Sandbox（後端） | CDS Prefetch 執行清除 patientId 導致 Patient context 失效 | 邏輯錯誤 | [`c08372c`](../../commit/c08372c) |
| ~~BUG-018~~ | 🐛 bugfix | 2026-02-20 | CQL Engine（後端） | ~~FHIR Coding→Code 轉換~~ **已撤回**（CQL Engine 已透過 FHIRHelpers 處理） | 誤判 | [`878deef`](../../commit/878deef) → reverted |
| BUG-017 | 🐛 bugfix | 2026-02-20 | CDS Hooks Sandbox（後端） | PrefetchRetrieveProvider 未展開 ValueSet 導致代碼過濾失效 | 架構缺陷 | [`878deef`](../../commit/878deef) |
| BUG-016 | 🐛 bugfix | 2026-02-20 | Test Case Builder（前後端） | CodeableConcept dropdown 使用 ValueSet URL 而非 CodeSystem URL | 資料處理錯誤 | [`6ca7a86`](../../commit/6ca7a86) |
| BUG-015 | 🐛 bugfix | 2026-02-20 | CQL Engine（後端） | VSAC ValueSet 未連接 CQL Engine 導致 CDS 規則失效 | 架構缺陷 | [`69dd9a1`](../../commit/69dd9a1) |
| BUG-014 | 🐛 bugfix | 2026-02-20 | Test Case Builder（前端） | CodeableConcept boundCodes 未使用下拉選單 | UX 設計缺陷 | [`69dd9a1`](../../commit/69dd9a1) |
| BUG-013 | 🐛 bugfix | 2026-02-20 | CQL Builder（前端） | TWCORE 選碼導致 Monaco Editor 白屏 | 配置遺漏 | [`4c9ae86`](../../commit/4c9ae86) |
| BUG-012 | 🐛 bugfix | 2026-02-20 | 跨模組（前端） | Monaco Editor 夜間模式白屏 | 配置遺漏 | [`e375b1e`](../../commit/e375b1e) |
| BUG-011 | 🐛 bugfix | 2026-02-20 | 版面配置（前端） | Footer 位置異常：flexbox 佈局修正 | UX 設計缺陷 | [`d82710d`](../../commit/d82710d) |
| BUG-010 | 🐛 bugfix | 2026-02-20 | CDS Hooks Sandbox（前後端） | CDS Card 顯示所有表達式擠在一行 | UX 設計缺陷 | [`5e69d32`](../../commit/5e69d32) |
| BUG-009 | 🐛 bugfix | 2026-02-20 | Test Case Builder（前端） | FHIR Choice Type 序列化錯誤（value → valueQuantity） | 資料處理錯誤 | [`5e69d32`](../../commit/5e69d32) |
| PAT-002 | ✨ patch | 2026-02-19 | i18n | Measures 模組國際化（en / zh-TW） | Measures, Dashboard, Test Case Builder | [`37a9827`](../../commit/37a9827) |
| PAT-001 | ✨ patch | 2026-02-19 | 跨模組 | UCUM 單位下拉選單統一 | Test Case Builder, CQL Builder, eQCM, Authoring | [`300bf0f`](../../commit/300bf0f) |
| BUG-008 | 🐛 bugfix | 2026-02-19 | CDS Hooks Sandbox（後端） | CDS Sandbox Invoke 所有 CQL 表達式回傳 null | 資料處理錯誤 | [`fccd012`](../../commit/fccd012) |
| BUG-007 | 🐛 bugfix | 2026-02-19 | 版面配置（前端） | Footer fixed 定位仍遮擋操作按鈕 | UX 設計缺陷 | [`b570119`](../../commit/b570119) |
| BUG-006 | 🐛 bugfix | 2026-02-19 | Backend 基礎設施 | Backend OOM 導致所有 API 無回應 | 配置遺漏 | [`660347a`](../../commit/660347a) |
| BUG-005 | 🐛 bugfix | 2026-02-19 | 版面配置（前端） | Footer 覆蓋頁面內容 | UX 設計缺陷 | [`741b7dc`](../../commit/741b7dc) |
| BUG-004 | 🐛 bugfix | 2026-02-19 | CDS Hooks Sandbox（前端） | CDS Sandbox 修改資料後無法重新執行 | 邏輯錯誤 | [`741b7dc`](../../commit/741b7dc) |
| BUG-003 | 🐛 bugfix | 2026-02-19 | Test Case Builder（前端） | Observation status 欄位允許自由輸入導致無效值 | UX 設計缺陷 | [`741b7dc`](../../commit/741b7dc) |
| BUG-002 | 🐛 bugfix | 2026-02-19 | CQL Translation（後端＋前端） | CQL 翻譯失敗時 Builder 無法顯示已解析的部分結構 | 邏輯錯誤 | [`f9b3e33`](../../commit/f9b3e33) |
| BUG-001 | 🐛 bugfix | 2026-02-19 | CQL Builder（前端） | CQL Builder 解析 CQL 靜默失敗 | 邏輯錯誤 | [`3ee28f8`](../../commit/3ee28f8) |

### 類型說明

| 標記 | 說明 |
|------|------|
| 🐛 bugfix | Bug 修復（原 BUGFIX_LOG） |
| ✨ patch | 功能改善 / 重構 / 新功能（原 PATCH_LOG） |

### 根因類型說明（bugfix）

| 類型 | 說明 |
|------|------|
| 邏輯錯誤 | 程式邏輯或條件判斷有誤 |
| UX 設計缺陷 | 介面設計不符合使用者預期 |
| 配置遺漏 | 環境設定、參數未正確配置 |
| 資料處理錯誤 | 資料解析、轉換或驗證問題 |
| 併發/效能問題 | 記憶體、執行緒或效能相關 |
| 架構缺陷 | 元件間整合或資料流路徑設計不當 |
| i18n 遺漏 | 國際化翻譯未覆蓋或未正確套用 |
| 外部服務限制 | 第三方服務不支援所需功能或資料 |
| 安全漏洞 | 認證、授權、注入、XSS 等安全缺陷 |
| 測試遺漏 | 測試案例不足或驗證不完整 |

---

## 詳細記錄 — 🌐 i18n / ✨ Patch（PAT-027+）

## PAT-044 — CQL Monarch 語法從 ANTLR Grammar 自動產生

- **日期**: 2026-03-13
- **範圍**: 前端（Editor — Monaco CQL 語法高亮）

### 問題

`cqlSyntax.ts` 的 116 個 CQL 關鍵字是手動維護，與官方 CQL ANTLR grammar（v1.5）存在偏差：
- **15 個官方關鍵字缺漏**：`div`, `mod`, `implies`, `fluent`, `maximum`, `minimum`, `of`, `starting`, `timezoneoffset`, `codesystems`, `Code`, `Concept`, `Interval`, `List`, `Tuple`
- **14 個非標準關鍵字被誤加**：`combine`, `first`, `last`, `indexof`, `skip`, `take`, `tail`（這些是系統函式，不是語言關鍵字）、`datetime`, `interval`（小寫，語法用大寫 `DateTime`/`Interval`）、`returns`, `external`, `such`, `that`, `included`

### 修正方案

1. **Codegen 腳本**（`scripts/generate-monarch-tokens.py`）：從 `cql.g4` 的 `keyword`、`reservedWord`、`typeNameIdentifier` 規則自動提取 token 列表
2. **產出檔案**（`src/utils/cqlTokens.generated.ts`）：匯出 `CQL_KEYWORDS`（115）、`CQL_TYPE_KEYWORDS`（18）、`CQL_MULTI_WORD_KEYWORDS`（8）、`CQL_RESERVED_WORDS`（67）、`CQL_DATETIME_PRECISIONS`（16）
3. **`cqlSyntax.ts`**：import 產出檔案取代手寫陣列；TWCDI 片段、FHIR 屬性表、theme 等專案特有部分保留手寫
4. **CI drift 檢查**：`ci.yml` 的 frontend-lint job 新增 `--check` 步驟，grammar 更新但未重新產生時 CI 失敗

### 影響的檔案

| 檔案 | 變更 |
|------|------|
| `frontend/scripts/grammar/cql.g4` | 新增：官方 ANTLR grammar v1.5 |
| `frontend/scripts/grammar/fhirpath.g4` | 新增：FHIRPath grammar |
| `frontend/scripts/generate-monarch-tokens.py` | 新增：codegen 腳本 |
| `frontend/src/utils/cqlTokens.generated.ts` | 新增：自動產生的 token 列表 |
| `frontend/src/utils/cqlSyntax.ts` | 改用 import 產出檔案 |
| `.github/workflows/ci.yml` | 新增 grammar drift check 步驟 |

---

## PAT-043 — TFDA 法規追溯 CI 強制檢查

- **日期**: 2026-03-13
- **範圍**: CI/CD（法規合規）

### 問題

CLAUDE.md 寫了「必須遵守」、「必須填寫」等法規追溯要求，但沒有對應的 CI 防護。開發者遺漏 Issue 引用或漏建法規文件時，系統不會攔截，導致法規文件產生腳本在最後才發現缺漏。

### 修正方案

新增 GitHub Actions workflow `regulatory-check.yml`，在 PR 發起或編輯時自動檢查：

**強制阻擋（Block Merge）：**
1. PR 描述未包含任何 `#NNN` Issue 引用
2. 安全性等級 B/C 的需求 Issue 缺少對應的設計/風險/驗證 Issue（反查所有法規 Issue 的 body 是否引用該需求）

**警告（不阻擋）：**
3. 需求 Issue 缺少安全性等級標籤（`安全性等級-A/B/C`）
4. 法規 Issue 內容缺少 `### 標題` 格式（影響文件產生腳本解析）

**豁免：**
- `docs:` 開頭的 PR 標題自動跳過檢查

### 影響的檔案

| 檔案 | 變更 |
|------|------|
| `.github/workflows/regulatory-check.yml` | 新增 workflow |
| `.github/scripts/check-regulatory-traceability.py` | 新增檢查腳本 |
| `.github/pull_request_template.md` | 加入 CI 檢查提示 |
| `CLAUDE.md` | 記錄 CI 強制檢查規則 |

---

## PAT-042 — Monaco-Redux 解耦（效能優化）

- **日期**: 2026-03-13
- **範圍**: 前端（Editor — 7 檔案）

### 問題

Monaco Editor 的 `onChange` 事件在每次按鍵時 dispatch `setCqlContent` 到 Redux store，導致：
- 每次按鍵觸發全局 state 更新 → 所有依賴 `cqlContent` 的元件 re-render
- `handleTranslate`、`handleSaveLibrary`、`handleExport` 等 callback 因 `cqlContent` 依賴每次按鍵重建
- `libraryMatch` 正則在每次 render 執行
- `useCqlStructure` hook 透過 Redux selector 驅動，每次按鍵觸發 debounce 重設

### 修正方案

**架構變更：Monaco 內部管理編輯狀態，僅在 blur / save / execute 時同步到 Redux**

1. **`CqlEditor.tsx`** — `forwardRef` + `useImperativeHandle` 暴露 `CqlEditorHandle`（`getContent`、`getEditor`、`flushContent`）；`onChange` 僅呼叫 `onContentChanged` callback；blur / Ctrl+S / Ctrl+Enter 時同步 Redux
2. **`EditorPage.tsx`** — 使用 `cqlEditorRef` 按需讀取內容（`syncAndGetContent()`）；輕量 `localContent` state 處理 UI 需求（disabled 檢查、`libraryMatch`）
3. **`useCqlStructure.ts`** — 移除 Redux 依賴，改為 callback 驅動：暴露 `notifyContentChanged(content)` 取代 `useSelector`
4. **`CqlBuilderPanel.tsx`** — 接受 `editorContent` prop，透過 `useEffect` 驅動 `notifyContentChanged`
5. **`ExecutionPanel.tsx`** — 接受 `getLatestCql` prop，執行時從 editor ref 讀取最新內容
6. **`MeasureCqlTab.tsx`** — 同 EditorPage 模式：`cqlEditorRef` + `localContent` + `onContentChanged`
7. **`useCqlStructure.test.ts`** — 更新測試使用 `notifyContentChanged` 取代 Redux dispatch

### 影響的檔案

| 檔案 | 變更 |
|------|------|
| `frontend/src/components/editor/CqlEditor.tsx` | forwardRef + imperative handle + blur sync |
| `frontend/src/pages/EditorPage.tsx` | editor ref + localContent + syncAndGetContent |
| `frontend/src/hooks/useCqlStructure.ts` | 移除 Redux 依賴，callback-driven debounce |
| `frontend/src/components/builder/CqlBuilderPanel.tsx` | 新增 editorContent prop |
| `frontend/src/components/execution/ExecutionPanel.tsx` | 新增 getLatestCql prop |
| `frontend/src/components/measure/MeasureCqlTab.tsx` | editor ref + localContent |
| `frontend/src/hooks/__tests__/useCqlStructure.test.ts` | 適配新 API |

---

## BUG-087 — 滲透測試安全修復

- **日期**: 2026-03-12
- **範圍**: 安全性（全端 — 後端 22 檔 + 前端 10 檔）

### 變更內容

**CRITICAL 修復（4 項）：**

1. **C2: EHR Mass Assignment** — 新建 `EhrConnectionRequest` DTO 取代直接接收 Entity，加 `@Valid` + `@NotBlank` / `@Size` 驗證
2. **C3: Department/Indicator 缺少授權** — `DepartmentController` + `IndicatorCatalogController` 加 `@PreAuthorize("hasRole('ADMIN')")` + Entity 加驗證註解
3. **C5: dangerouslySetInnerHTML XSS** — 安裝 DOMPurify 清洗 Monaco colorized 輸出（2 檔），9 個 `dangerouslySetInnerHTML` 替換為 `Trans` 元件
4. **@EnableMethodSecurity** — `SecurityConfig` 啟用方法級安全，讓 `@PreAuthorize` 生效

**HIGH 修復（10 項）：**

5. **H1: SSRF 儲存型 URL** — EHR 建立/更新時呼叫 `InputValidator.requireValidUrl()` 驗證 FHIR URL
6. **H2: Null URL 繞過** — `InputValidator.isValidUrl(null)` 改回傳 `false`（原為 `true`）
7. **H3: Measure 所有權繞過** — `MeasureController` 新增 `requireReadableMeasure()` 套用於 10 個讀取/匯出端點
8. **H4: EHR 端點無授權** — 建立/更新/刪除 EHR 連線加 `@PreAuthorize("hasAnyRole('ADMIN','DEPARTMENT_ADMIN')")`
9. **H5-H7: Rate Limiting 缺口** — 新增 AUTH（10 rpm）+ CDS_INVOKE（10 rpm）限流層級
10. **H8: 暫時密碼 API 洩漏** — `adminResetPassword` 改為透過 Email 寄送暫時密碼，API 回應不含密碼
11. **H9: XFF 標頭欺騙** — `AuditFilter.getClientIp()` 僅信任來自私有地址的 X-Forwarded-For
12. **H10: CSRF 配置** — 於 SecurityConfig 加註 CSRF disabled 原因說明（Stateless JWT 架構）

### 檔案變更

| 檔案 | 說明 |
|------|------|
| `model/ehr/EhrConnectionRequest.java` | 新建 DTO |
| `controller/EhrIntegrationController.java` | DTO + @PreAuthorize + URL 驗證 |
| `service/fhir/EhrConnectionService.java` | 接收 DTO |
| `config/SecurityConfig.java` | @EnableMethodSecurity + CSRF 說明 |
| `controller/DepartmentController.java` | @PreAuthorize + @Valid |
| `controller/IndicatorCatalogController.java` | @PreAuthorize + @Valid + @Size(max=500) |
| `entity/DepartmentEntity.java` | @NotBlank / @Size |
| `entity/IndicatorCatalogEntity.java` | @NotBlank / @Size |
| `security/InputValidator.java` | null/blank → false |
| `controller/FhirController.java` | 7 處 null guard |
| `controller/CdsHooksController.java` | 2 處 null guard |
| `controller/CqlController.java` | 1 處 null guard |
| `controller/MeasureController.java` | requireReadableMeasure + 5 處 null guard |
| `controller/AuthoringController.java` | 1 處 null guard |
| `controller/AdminController.java` | Cache-Control: no-store |
| `security/RateLimitFilter.java` | AUTH + CDS_INVOKE tiers |
| `config/RateLimitProperties.java` | authRpm + cdsInvokeRpm |
| `service/PasswordResetService.java` | void return + email 寄送 |
| `service/EmailService.java` | sendTemporaryPasswordEmail() |
| `model/auth/AdminResetPasswordResponse.java` | 移除 temporaryPassword |
| `security/AuditFilter.java` | 私有地址判定 XFF 信任 |
| `service/measure/IndicatorCatalogService.java` | delete() 方法 |
| `frontend/package.json` | +dompurify +@types/dompurify |
| `components/builder/CqlPreviewBox.tsx` | DOMPurify.sanitize |
| `components/authoring/cql-preview/CqlPreviewPanel.tsx` | DOMPurify.sanitize |
| `components/authoring/builder/ExpressionPhrase.tsx` | Trans 元件 |
| `pages/AdminUsersPage.tsx` | Trans 元件 |
| `components/cds/ManageServicesPanel.tsx` | Trans 元件 |
| `components/authoring/subpopulations/Subpopulations.tsx` | Trans 元件 |
| `components/authoring/base-elements/BaseElements.tsx` | Trans 元件 |
| `components/authoring/parameters/Parameters.tsx` | Trans 元件 |
| `components/authoring/builder/ArtifactElementBody.tsx` | Trans 元件 |
| `components/measure/PopulationCriteriaTab.tsx` | Trans 元件 |

---

## PAT-040 — TFDA 法規文件自動化工作流

- **日期**: 2026-03-12
- **範圍**: 法規自動化（GitHub Templates + Python Scripts + CI）

### 變更內容

1. **GitHub Issue Templates（4 個 YAML 表單）**
   - `software_requirement.yml` — [需求] 軟體需求（需求描述、臨床情境、驗收條件、風險等級 dropdown、安全性等級 dropdown）
   - `design_specification.yml` — [設計] 軟體設計規格（設計方案、架構影響、關聯需求、安全考量）
   - `risk_analysis.yml` — [風險] 風險分析（危害情境、嚴重度/發生機率 5 級 dropdown、控制措施、殘餘風險）
   - `verification_record.yml` — [驗證] 軟體驗證紀錄（測試目的、步驟、預期/實際結果、測試結論 dropdown）
   - `config.yml` — 禁用空白 Issue

2. **PR Template**（`.github/pull_request_template.md`）
   - 中文欄位：變更說明、變更類型、關聯 Issue、測試紀錄、風險評估、安全性確認、設計審查備註、IEC/ISO 追溯表

3. **Labels 設定腳本**（`.github/setup-labels.sh`）
   - 8 個中英對照標籤：IEC62304:需求/設計/驗證、ISO14971:風險、變更管制、安全性等級-A/B/C

4. **法規文件產生腳本**（`regulatory_docs/scripts/`）
   - `generate_regulatory_docs.py` — GitHub API 擷取 Issues/PRs/tags → 解析 YAML 表單 body → 建構追溯矩陣 → Jinja2 渲染 6 份法規文件
   - `generate_test_report.py` — 解析 Surefire XML + Vitest JSON → 中文 Markdown + HTML 測試報告
   - 6 個 Jinja2 模板（含 TFDA 標準表頭）：SRS、SDS、風險報告、驗證報告、追溯矩陣、變更管制紀錄

5. **CI 整合**
   - `ci.yml` — 上傳 Surefire XML + Vitest JSON artifact
   - `regulatory-docs.yml` — 手動觸發或 release 時自動產出法規文件包

6. **CLAUDE.md 更新**
   - 新增「TFDA 法規文件工作流（必須遵守）」區段，定義觸發時機、Issue 建立規則、追溯連結規範

7. **測試 Issues 建立**
   - 40 個法規 Issues（11 需求 + 9 設計 + 9 風險 + 11 驗證），涵蓋 8 大功能群組
   - 追溯矩陣：9/11 需求完整追溯（需求↔設計↔驗證↔風險），2 項低風險僅需求+驗證

### 檔案變更

| 檔案 | 說明 |
|------|------|
| `.github/ISSUE_TEMPLATE/*.yml` | 4 個 YAML 表單 + config |
| `.github/pull_request_template.md` | 中文 PR 模板 |
| `.github/setup-labels.sh` | 8 個法規標籤建立腳本 |
| `.github/workflows/ci.yml` | 新增 Surefire/Vitest artifact 上傳 |
| `.github/workflows/regulatory-docs.yml` | 法規文件產生 workflow |
| `regulatory_docs/scripts/*.py` | 2 個 Python 產生腳本 |
| `regulatory_docs/templates/*.md` | 6 個 Jinja2 中文模板 |
| `regulatory_docs/README.md` | 使用說明 |
| `CLAUDE.md` | 新增 TFDA 法規工作流指引 |

---

## PAT-039 — eCQM 儲存修復 + 參數動態預設值輸入

- **日期**: 2026-03-09
- **範圍**: eCQM（全端）+ CDS Authoring（前端）

### 變更內容

1. **eCQM Population Groups 儲存失敗修復（後端）**
   - **根因**: JPA `@Transient` list 欄位不觸發 Hibernate dirty checking → `@PreUpdate` 不執行 → `serializeAll()` 未將 list 序列化為 JSON column
   - **修復**: `EcqmArtifactService.update()` 中在 `repository.save()` 前明確呼叫 `entity.serializeAll()`
   - **附帶修復**: 所有欄位加 null guard 支援部分更新，避免 race condition 覆蓋

2. **eCQM 前端部分更新（前端）**
   - `save()` 改為只送變更欄位（不再以 stale `artifactRef.current` 為基底）
   - Save 按鈕改為僅在 `saving` 時停用（原先 `idle`/`saved` 也被停用）
   - `flushSave()` 無 pending 時仍強制送出 `save({})`

3. **參數動態預設值輸入（前端）**
   - `datetime` → 日期時間選擇器（`type="datetime-local"`）
   - `time` → 時間選擇器（`type="time"`）
   - `interval<integer>` → 下限/上限數字輸入
   - `interval<datetime>` → 開始/結束日期時間選擇器
   - `code` → 新增搜尋按鈕，開啟 `ChooseCodeDialog` 代碼檢索

### 影響檔案

| 檔案 | 變更 |
|------|------|
| `backend/.../EcqmArtifactService.java` | serializeAll() 明確呼叫 + null guard 部分更新 |
| `frontend/.../EcqmArtifactWorkspace.tsx` | 部分更新 save + flushSave 強制送出 |
| `frontend/.../EcqmArtifactWorkspaceHeader.tsx` | Save 按鈕停用條件修正 |
| `frontend/.../parameters/Parameters.tsx` | 動態預設值輸入（5 種新型別 UI） |
| `frontend/src/locales/{en,zh-TW}/authoring.json` | 新增 5 個 i18n key |

---

## PAT-036 — FHIR 代碼瀏覽器 + 測試修復 + TWCOREDATA Dockerfile 修復

- **日期**: 2026-03-07
- **範圍**: CQL Builder（全端）+ 測試 + TWCOREDATA

### 變更內容

1. **FHIR 代碼瀏覽器**：Codes 區新增第三個 tab「FHIR 代碼」，以分組手風琴瀏覽標準 FHIR 管理用 CodeSystem（AllergyIntolerance / Condition / MedicationRequest），點選 Chip 直接生成 codesystem + code 預覽插入。
2. **CodeSystem 分組資料結構**：`codeSystems.ts` 新增 `CODE_SYSTEM_GROUPS` 型別與資料，包含 AllergyIntolerance（Clinical Status / Verification Status / Category / Type / Criticality）、Condition（Clinical Status / Verification Status）、MedicationRequest（Status）共 3 群組 8 子系統。
3. **Auth/Password 測試修復**：`AuthIntegrationTest`（3 tests）和 `AuthControllerTest`（1 test）因 `RegisterRequest` 新增 `@NotBlank email` 欄位而失敗，補上缺少的 email 欄位。`PasswordResetServiceTest`（1 test）因 `requestPasswordReset` 使用 `TransactionSynchronizationManager.registerSynchronization()` 但單元測試無交易環境而失敗，補上 `initSynchronization/clearSynchronization` 生命週期及手動觸發 `afterCommit` 回呼。
4. **TWCOREDATA Dockerfile 修復**：COPY 指令遺漏 `models.py`，導致容器啟動時 `No module named 'models'` 錯誤。

### 檔案變更

| 檔案 | 說明 |
|------|------|
| `frontend/.../CodesSection.tsx` | 新增 `fhir-codes` tab + Accordion 分組瀏覽器 + handleFhirCodeClick |
| `frontend/.../constants/codeSystems.ts` | 新增 `CodeSystemGroup` / `PredefinedCodeSystem` 型別 + `CODE_SYSTEM_GROUPS` 資料；Condition/Allergy 小型 CS 從 `COMMON_CODE_SYSTEMS` 移至 groups |
| `frontend/.../locales/en/builder.json` | 新增 `codes.fhirCodes` key |
| `frontend/.../locales/zh-TW/builder.json` | 新增 `codes.fhirCodes` 中文翻譯 |
| `backend/.../controller/AuthControllerTest.java` | register_existingUser 補 email 欄位 |
| `backend/.../integration/AuthIntegrationTest.java` | 3 個 register 測試補 email 欄位 |
| `backend/.../service/PasswordResetServiceTest.java` | 補 TransactionSynchronization 生命週期 + afterCommit 觸發 |
| `TWCOREDATA/Dockerfile` | COPY 指令補上 `models.py` |

---

## PAT-035 — 修飾器連續新增 + Element Name UX + Numeric Value 修飾器

- **日期**: 2026-03-06
- **範圍**: CDS Authoring（全端）

### 變更內容

1. **修飾器連續新增**：選擇修飾器 dialog 不再每次新增後關閉，改為保持開啟，顯示已加入的修飾器鏈（Chip + 箭頭），可繼續選擇下一個或移除已加入的。
2. **Base Elements 隱藏子元素 Element Name**：Base Elements 內的子元素不再顯示多餘的 Element Name 欄位，避免兩個名稱造成混淆。
3. **Element Name 自動帶入預設值**：新增元素時 `element_name` 自動填入模板名稱（如 Age Range、Gender），不再留空。
4. **驗證邏輯修正**：移除「缺少元素名稱」強制驗證，改為 fallback 到 `element.name`，與 helper text「選填」一致。
5. **Numeric Value 修飾器**：新增修飾器，從 `System.Quantity`（如 `119 'kg'`）提取純數值（`119`），避免算術運算後單位不匹配導致比較失敗。
6. **arithmeticExpression 類型白名單**：後端 `TemplateService.BUILTIN_REFERENCE_TYPES` 加入 `arithmeticExpression`，修復儲存時「unknown element type」錯誤。

### 檔案變更

| 檔案 | 說明 |
|------|------|
| `backend/.../TemplateService.java` | BUILTIN_REFERENCE_TYPES 加入 arithmeticExpression |
| `backend/.../data/modifiers.json` | 新增 NumericValue 修飾器 + system_decimal 加入 ValueComparison/BooleanComparison inputTypes |
| `backend/.../templates/cql/modifiers/NumericValue.ftl` | 新 FreeMarker 模板：`(expression).value` |
| `frontend/.../ArtifactElementBody.tsx` | 修飾器 dialog 改為連續新增模式 + hideElementName prop |
| `frontend/.../ArtifactElement.tsx` | 透傳 hideElementName |
| `frontend/.../ConjunctionGroup.tsx` | 透傳 hideElementName |
| `frontend/.../BaseElements.tsx` | 設定 hideElementName |
| `frontend/.../ElementSelect.tsx` | element_name 自動帶入模板名稱 |
| `frontend/.../ArtifactWorkspace.tsx` | 移除強制 element_name 驗證 |
| `frontend/.../authoringConstants.ts` | system_decimal 顏色映射 |
| `frontend/.../locales/en/authoring.json` | 新增 noMoreModifiers key |
| `frontend/.../locales/zh-TW/authoring.json` | 新增 noMoreModifiers 中文翻譯 |

---

## PAT-034 — 基礎元素四則運算

- **日期**: 2026-03-06
- **範圍**: CDS Authoring（全端）
- **類型**: ✨ patch

### 修復內容

CDS Authoring Tool 的 Base Elements 面板新增「四則運算元素」，支援在兩個基礎元素之間進行 +、−、×、÷ 運算，產生計算型 CQL 定義。

**前端**
- 新增 `ArithmeticElement` 元件：兩個下拉選單（左/右運算元）+ 運算符選擇 + CQL 即時預覽
- `BaseElements` 面板新增下拉選單：「邏輯元素」（原有）和「四則運算元素」（新增）
- `ExpressionPhrase` 新增算術元素的自然語言描述

**後端**
- `ExpressionCqlEngine.buildExpression()` 新增 `arithmeticExpression` case，解析左/右運算元 ID 並產生 CQL
- `CqlArtifactBuilder` 調整 base element 發射順序：非算術元素先輸出，算術元素後輸出（避免 CQL 引用順序問題）
- 運算符白名單驗證（僅允許 +−×÷），防止注入

### 變更檔案
- `frontend/.../base-elements/ArithmeticElement.tsx` — 新增：四則運算元素 UI
- `frontend/.../base-elements/BaseElements.tsx` — 新增下拉選單 + 算術元素渲染
- `frontend/.../builder/ExpressionPhrase.tsx` — 新增算術表達式描述
- `frontend/src/locales/en/authoring.json` — 新增 arithmetic.* 鍵值
- `frontend/src/locales/zh-TW/authoring.json` — 新增繁中翻譯
- `backend/.../authoring/ExpressionCqlEngine.java` — 新增 arithmeticExpression case
- `backend/.../authoring/CqlArtifactBuilder.java` — base element 拓撲排序

---

## PAT-033 — CQL Builder 面板增強

- **日期**: 2026-03-06
- **範圍**: CQL Builder（前端）
- **類型**: ✨ patch

### 修復內容

CQL Builder 右側面板從被動結構瀏覽器升級為主動輔助撰寫工具，借鑑 Authoring Tool 的功能設計。

**Phase 1 — 型別系統 + 驗證面板**
- `TypeChip` 元件：15+ CQL/FHIR 型別對應顏色標籤（Boolean=藍、Quantity=橙、List/Interval 巢狀解析）
- `ValidationPanel` 靜態分析：缺少 InPopulation/MeetsInclusionCriteria 模式偵測、System.Any 警告、混合 context 提示
- `ElementListItem` 整合 TypeChip 顯示回傳型別

**Phase 2 — 修飾鏈建構器 + 元素選擇器**
- `ModifierChainBuilder`：15 種修飾器跨 5 類（filter/aggregate/compare/string/unit），含 singleton 限制
- `ElementSelectField`：分組下拉選單（Definitions/Parameters/Functions/Built-in）+ 搜尋 + TypeChip
- `RetrieveBuilder` 重寫：舊 checkbox 修飾器替換為 ModifierChainBuilder

**Phase 3 — 依賴圖 + 基礎元素**
- `ExpressionTreeView`：regex 掃描 CQL define 區塊建立依賴圖，深度限制 4 層，點擊導航
- `BaseElementsPanel`：釘選/取消釘選定義為基礎元素（localStorage 持久化），自動建議引用 ≥2 次的定義
- `DefinitionsSection` 範本改為依據實際 CQL 中的 valueSets/codes 動態產生

**程式碼品質修復（/simplify）**
- 4 處 `extractCqlName` 重複改為使用 `utils/cqlNames.ts` 共用工具
- CQL define 區塊解析 regex 提取為 `parseCqlDefineBlocks()` + `findQuotedReferences()` 共用函式
- Accordion 加入 `unmountOnExit` 避免折疊面板仍執行昂貴計算
- 移除死碼（`void _valueSets`、`void totalUnused`、未使用的 `allFuncNames`）
- `context` 狀態修復：Population context 現在正確寫入產生的 CQL
- `ModifierDef` 新增 `singleton` 欄位取代硬編碼字串陣列
- `CATEGORY_COLORS` 型別從 `Record<string>` 改為 `Record<ModifierCategory>`

**安全修復（security-review）**
- `applyModifierChain()` 新增 `cqlEscapeString()` 防止使用者輸入含單引號時產生 CQL 注入

### 變更檔案
- `frontend/.../builder/TypeChip.tsx` — 新增：型別顏色標籤元件
- `frontend/.../builder/ValidationPanel.tsx` — 新增：靜態分析面板
- `frontend/.../builder/ModifierChainBuilder.tsx` — 新增：修飾鏈 UI + CQL 產生
- `frontend/.../builder/ElementSelectField.tsx` — 新增：分組搜尋下拉選單
- `frontend/.../builder/ExpressionTreeView.tsx` — 新增：依賴圖視覺化
- `frontend/.../builder/BaseElementsPanel.tsx` — 新增：基礎元素釘選面板
- `frontend/.../builder/CqlBuilderPanel.tsx` — 新增 3 個 Accordion 區段 + unmountOnExit
- `frontend/.../builder/DefinitionsSection.tsx` — 動態範本 + context 修復
- `frontend/.../builder/RetrieveBuilder.tsx` — 整合 ModifierChainBuilder
- `frontend/.../builder/ExpressionBuilder.tsx` — 整合 ElementSelectField
- `frontend/.../builder/OperatorPanel.tsx` — 移除未使用 valueSets prop
- `frontend/.../builder/ElementListItem.tsx` — 整合 TypeChip
- `frontend/src/utils/cqlNames.ts` — 新增 parseCqlDefineBlocks + findQuotedReferences
- `frontend/src/locales/en/builder.json` — 新增 i18n 鍵值
- `frontend/src/locales/zh-TW/builder.json` — 新增 i18n 繁中翻譯

---

## BUG-084 — CDS 卡片中文亂碼修復（`&#39;` 雙重跳脫）

- **日期**: 2026-03-06
- **範圍**: CDS Hooks（後端）
- **類型**: 🐛 bugfix

### 問題描述

CDS Hooks 推薦卡片顯示 `&#39;` 等 HTML entity 亂碼，而非正確的單引號或中文字元。

### 根本原因

後端 `CdsValueFormatter`、`CdsResourceFormatter`、`CqlTupleCardStrategy`、`PlanDefinitionCardStrategy` 四個類別使用 `HtmlUtils.htmlEscape()` 對輸出進行 HTML 跳脫。但前端 React JSX 自動跳脫文字內容，導致雙重編碼：
- 後端：`'` → `&#39;`
- React：`&#39;` → 原樣顯示為 `&#39;`（而非 `'`）

### 修復內容

- 移除 4 個 Java 類別中的 `esc()` 方法及所有 `HtmlUtils.htmlEscape()` 呼叫
- CDS card 值以原始內容回傳，由 React 在渲染時自動處理跳脫
- `CdsHooksService.escapeHtml()` 保留不動 — 該方法用於清理使用者回饋資料寫入資料庫，屬於正確用途
- 測試更新：移除 14 個 XSS 跳脫斷言，替換為 3 個原始內容保留測試 + 中文文字測試

### 變更檔案
- `backend/.../cds/CdsValueFormatter.java` — 移除 `esc()` 方法及 `HtmlUtils` 匯入
- `backend/.../cds/CdsResourceFormatter.java` — 移除 `esc()` 方法及 `HtmlUtils` 匯入
- `backend/.../cds/CqlTupleCardStrategy.java` — 移除 `esc()` 呼叫
- `backend/.../cds/PlanDefinitionCardStrategy.java` — 移除 `esc()` 呼叫
- `backend/.../cds/CdsValueFormatterTest.java` — 測試更新
- `backend/.../cds/CdsResourceFormatterTest.java` — 測試更新

---

## PAT-031 — eCQM 基礎元素 + 參數分頁功能啟用

- **日期**: 2026-03-06
- **範圍**: eCQM（前端）
- **類型**: ✨ patch

### 修復內容

eCQM 撰寫的「基礎元素」和「參數」分頁原為靜態佔位文字，無法新增或編輯。
直接複用 CDS Authoring 已完成的 `BaseElements` 和 `Parameters` 元件，連接至 eCQM 狀態管理。

- **基礎元素**：新增/刪除/重新命名、ConjunctionGroup 樹狀編輯器、元素模板選擇、修飾詞鏈、回傳類型自動計算
- **參數**：新增/刪除、11 種參數型別（boolean/integer/decimal/string/datetime/time/code/concept/quantity/interval）、UCUM 單位欄位、備註
- **i18n**：英文及繁中翻譯已存在於 `authoring` namespace，無需新增
- **後端**：`EcqmCqlBuilder` 已支援 baseElements 和 parameters 的 CQL 產生

### 變更檔案
- `frontend/.../ecqm/EcqmArtifactWorkspace.tsx` — 匯入 BaseElements + Parameters 元件取代佔位文字

---

## PAT-030 — 後端配置風險修復

- **日期**: 2026-03-05
- **範圍**: 後端配置
- **類型**: 🔒 security
- **風險評估**: 根據 `config_risk_assessment_plan.md` 的 4 項風險修復

### 修復內容

1. **Risk 2.1 — CQL 執行緒池 AbortPolicy (HIGH)** (`AsyncConfig.java`)
   - `AbortPolicy` 在佇列滿載時拋出 `RejectedExecutionException` → 未處理 500 錯誤
   - 改用 `CallerRunsPolicy`：佇列滿載時由呼叫端執行緒執行，自然形成反壓（backpressure）
   - 不會遺失任務，也不會產生未處理例外

2. **Risk 1.1 — CORS 萬用字元拒絕 (MEDIUM)** (`WebConfig.java`)
   - `cors.allowed-origins` 環境變數可被設為 `*` 或含萬用字元的 pattern
   - 新增驗證：若 origin 含 `*` 則啟動時直接拋出 `IllegalArgumentException`
   - 僅接受精確 origin URL（例如 `https://example.com`）

3. **Risk 1.2 — Prometheus 端點認證 (MEDIUM)** (`SecurityConfig.java`)
   - `/actuator/prometheus` 原為 `permitAll()` — 可被未認證使用者存取業務指標
   - 改為由 `management.prometheus.public` 屬性控制（預設 `false` = 需認證）
   - Docker profile (`application-docker.yml`) 設為 `true` 允許 Prometheus 容器抓取

4. **Risk 3.1 — 移除 XssStringDeserializer (MEDIUM)** (`CqlConfig.java`)
   - 全域 Jackson `XssStringDeserializer` 使用 regex 靜默刪除字串內容
   - 問題：(a) regex 可被繞過 (b) 會損壞合法臨床數據（含 `eval`、角括號的 CQL/FHIR 資料）
   - XSS 防護已由其他層處理：React 自動跳脫、`@NoXss` 欄位驗證、表達式樹驗證器、`XssFilter`

### 變更檔案
- `backend/.../config/AsyncConfig.java` — `AbortPolicy` → `CallerRunsPolicy`
- `backend/.../config/WebConfig.java` — CORS 萬用字元拒絕
- `backend/.../config/SecurityConfig.java` — Prometheus 端點條件認證
- `backend/.../config/CqlConfig.java` — 移除 `XssStringDeserializer`
- `backend/.../resources/application-docker.yml` — 新增 `management.prometheus.public: true`

---

## PAT-029 — eCQM 風險修復（XSS + 結構驗證 + 發佈前 CQL 驗證）

- **日期**: 2026-03-05
- **範圍**: eCQM（後端）
- **類型**: 🔒 security
- **風險評估**: 根據 `ecqm_risk_assessment_plan.md` 的 3 項風險修復

### 修復內容

1. **Risk 1.1 — XSS 偵測方式改進** (`EcqmExpressionTreeValidator`)
   - 移除可被繞過的 regex XSS 偵測（`XSS_PATTERNS`）
   - 改用 Spring `HtmlUtils.htmlEscape()` 比對法：若字串 HTML 跳脫後不同，即含有 HTML 標記
   - 額外檢查 `javascript:` / `vbscript:` / `data:text/html` URI scheme
   - 不可被編碼混淆或 HTML5 新標籤繞過

2. **Risk 2.1 — 結構驗證** (`EcqmExpressionTreeValidator`)
   - 驗證元素類型 (`type`) 對照 `TemplateService.isValidElementType()`
   - 驗證修飾詞 ID (`modifiers[].id`) 對照 `ModifierService.isValidModifierId()`
   - 驗證 define name 唯一性（base elements + parameters 不可重複）
   - 檢查是否與保留的 eCQM population define name 衝突
   - 深度限制 `MAX_DEPTH=50`、節點數限制 `MAX_NODES=10,000`

3. **Risk 2.2 — 發佈前 CQL 驗證** (`EcqmPublishService`)
   - `publish()` 方法在建立 MeasureDefinition 前先呼叫 `cqlGenerationService.validateCql()`
   - CQL-to-ELM 翻譯失敗時拋出 `CqlGenerationException`，附帶逐行錯誤訊息
   - 新增測試 `publish_withCqlErrors_shouldThrow`

### 變更檔案
- `backend/.../ecqm/EcqmExpressionTreeValidator.java` — 完全重寫
- `backend/.../ecqm/EcqmPublishService.java` — 加入 CQL 驗證閘門
- `backend/.../ecqm/EcqmPublishServiceTest.java` — 新增驗證失敗測試 + mock 修正

---

## PAT-028 — eCQM 工作區存檔功能

- **日期**: 2026-03-05
- **範圍**: eCQM（前端）
- **內容**: eCQM 撰寫工作區原先僅有隱藏的 1500ms auto-save（無 UI 回饋），使用者無法得知變更是否已儲存。
- **修復**:
  - `EcqmArtifactWorkspaceHeader` — 新增 Save 按鈕（Ctrl+S tooltip）+ 存檔狀態指示器（儲存中… / 已儲存 / 有未儲存的變更 / 儲存失敗）
  - `EcqmArtifactWorkspace` — 新增 `SaveStatus` 狀態機（idle → dirty → saving → saved/error）、Ctrl+S 鍵盤快捷鍵、`useUnsavedChangesGuard` 瀏覽器離開防護、返回列表前確認對話框（捨棄 / 儲存並離開）、發佈前自動 flush 未儲存變更
  - i18n 新增 6 個翻譯 key（en + zh-TW）

---

## PAT-027 — eCQM 撰寫全模組 i18n 繁體中文翻譯

- **日期**: 2026-03-05
- **範圍**: eCQM（前端）
- **內容**: 為 eCQM 撰寫模組全部 12 個元件加入 i18next 國際化支援，新增 `ecqm` namespace 並建立英文 / 繁體中文翻譯 JSON。
- **修改清單**:
  - 新增 `frontend/src/locales/en/ecqm.json`（英文翻譯 ~120 strings）
  - 新增 `frontend/src/locales/zh-TW/ecqm.json`（繁體中文翻譯，領域術語保留英文附中文括號）
  - `frontend/src/i18n.ts` — 註冊 ecqm namespace
  - 12 元件加入 `useTranslation('ecqm')` 並替換所有硬編碼字串：
    `EcqmArtifactList`, `EcqmArtifactModal`, `EcqmArtifactWorkspace`, `EcqmArtifactWorkspaceHeader`,
    `EcqmSummaryTab`, `EcqmPopulationGroupsTab`, `EcqmPopulationGroupEditor`, `EcqmObservationEditor`,
    `EcqmSdeTab`, `EcqmStratifiersTab`, `EcqmCqlPreviewTab`（`EcqmPopulationTreeEditor` 無硬編碼字串，跳過）
- **翻譯策略**: 領域專有名詞採「英文原文（中文）」格式（如 `Proportion（比例）`、`Initial Population`），一般 UI 文字全中文化

---

## 詳細記錄 — 🐛 Bugfix（BUG-083+）

## BUG-083 — CQL Retrieve 使用 element_name 而非 code display + save 驗證錯誤未顯示

- **日期**: 2026-03-05
- **範圍**: CDS Authoring（前後端）
- **根因**: `ExpressionCqlEngine.buildGenericResourceExpression()` 迴圈遍歷所有 fields 時，`element_name` field 排第一且有非空字串 value，觸發 `value instanceof String` fallback 提前 return，永遠走不到帶有 codes/valueSets 的 observation field。同時前端 `saveFirst()` 失敗時錯誤被吞掉，使用者看不到驗證錯誤訊息。
- **修復**:
  - Backend: `buildGenericResourceExpression` 迴圈中跳過 `element_name` 和 `comment` metadata fields
  - Frontend: `ArtifactWorkspaceHeader` 和 `CqlPreviewPanel` 的 `saveFirst()` 加入 catch，用 `extractApiError` + `extractApiErrorDetails` 顯示錯誤 Alert
  - 新增 `extractApiErrorDetails()` 提取後端 `ValidationException.details` 陣列
  - 修正 3 個測試的資料結構（valueSets 從 element_name field 移到正確的 observation field）
- **影響**: `[Observation: "LDL>130"]` → `[Observation: "LDL from lipid profile"]`（正確使用 code display）

---

## 詳細記錄 — ✨ Patch（功能 / 重構）

## PAT-026 — CQL 注入修復 + 儲存型 XSS 修復

- **日期**: 2026-03-05
- **範圍**: 安全性
- **分類**: 注入防護 / XSS 防護

### 問題描述

安全審查發現三類漏洞：

1. **CQL 注入**（MEDIUM）：多個 modifier 和 element 的使用者輸入值被直接嵌入 CQL 單引號字串字面值內，未呼叫 `escapeCqlString()`。攻擊者可透過包含單引號的值（如 `test' or true --`）注入任意 CQL 邏輯。
2. **CQL 注入**（MEDIUM）：Error Statement 的 `thenClause` / `elseClause` 同樣未逸出。
3. **儲存型 XSS**（MEDIUM）：多個 React 元件使用 `dangerouslySetInnerHTML` + i18next 插值，但全域 i18n 設定 `escapeValue: false`，導致使用者控制的名稱（如 base element name）可注入 HTML/JavaScript。

### 修復方案

#### Fix 1：CQL 字串逸出補齊

在 `ExpressionCqlEngine.applyModifier()` 中，所有嵌入 CQL `'...'` 字串字面值的使用者輸入均加上 `escapeCqlString()`：

| Modifier / Element | 欄位 | 檔案位置 |
|--------------------|------|----------|
| EqualsString | `value` | ExpressionCqlEngine.java |
| StartsWithString | `value` | ExpressionCqlEngine.java |
| EndsWithString | `value` | ExpressionCqlEngine.java |
| ConvertUnits | `unit` | ExpressionCqlEngine.java |
| WithUnit | `unit` | ExpressionCqlEngine.java |
| LookBackModifier | `unit` | ExpressionCqlEngine.java |
| ContainsQuantity | `unit` | ExpressionCqlEngine.java |
| ValueComparisonNumber/Observation | `unit` | ExpressionCqlEngine.java |
| Gender | `gender` | ExpressionCqlEngine.java |

#### Fix 2：Error Statement 逸出

在 `CqlArtifactBuilder.buildErrorStatement()` 中，`thenClause` 和 `elseClause` 加上 `engine.escapeCqlString()`。

#### Fix 3：XSS — 選擇性啟用 HTML 逸出

在所有使用 `dangerouslySetInnerHTML` + `t()` 且插值包含使用者控制值的位置，加上 `interpolation: { escapeValue: true }`：

| 元件 | 插值變數 |
|------|----------|
| `BaseElements.tsx` | `name` |
| `Parameters.tsx` | `name` |
| `Subpopulations.tsx` | `name` |
| `AdminUsersPage.tsx` | `username` |
| `ManageServicesPanel.tsx` | `name` |
| `ArtifactElementBody.tsx` | `input`, `output` |
| `PopulationCriteriaTab.tsx` | `scoringType` |

### 驗證

- Backend 全部 80 tests 通過
- Frontend TypeScript 編譯通過
- `escapeCqlString()` 已處理 `\` → `\\` 和 `'` → `\'` 兩種逸出
- i18next `interpolation.escapeValue: true` 會將 `<`、`>`、`&`、`"` 轉為 HTML entities

---

## PAT-025 — FreeMarker 模板引擎遷移 + Conjunction 前端重構

- **日期**: 2026-03-05
- **範圍**: 重構
- **分類**: 架構改善 / 可維護性

### 問題描述

CQL 產生器（`CqlArtifactBuilder`、`ExpressionCqlEngine`、`EcqmCqlBuilder`）原先使用大量 `String.format()` 和 `StringBuilder` 拼接 CQL 程式碼，switch-case 內嵌多行字串不易維護。前端 conjunction tree 元件也有重複的 conjunction kind 解析邏輯和顏色映射。

### 實作方案

#### Phase 1：FreeMarker 依賴 + 模板引擎

- `pom.xml` 加入 `spring-boot-starter-freemarker`
- 新增 `CqlTemplateEngine.java` — 封裝 FreeMarker Configuration，從 `classpath:templates/cql/` 載入模板

#### Phase 2：30 個 FreeMarker 模板

| 目錄 | 模板 | 數量 |
|------|------|------|
| `/` | `artifact.ftl`、`ecqm-artifact.ftl` | 2 |
| `modifiers/` | CheckExistence、BooleanNot、Count、AllTrue、AnyTrue、BooleanComparison、ConvertUnits、WithUnit、LookBackModifier、EqualsString、StartsWithString、EndsWithString、BeforeTime、AfterTime、ContainsValue、IsTrue、IsNotTrue、IsFalse、IsNotFalse | 19 |
| `elements/` | AgeRange、Gender、GenericResource | 3 |
| `fragments/` | cds-card、error-statement | 2 |
| `parameters/` | defaults | 1 |
| `ecqm/` | standard-sde | 1 |

#### Phase 3：Java Context Builder 重構

- `CqlArtifactBuilder.buildCql()` → 組裝 data model Map → `templateEngine.render("artifact.ftl", dataModel)`
- `EcqmCqlBuilder.buildEcqmCql()` → 組裝 data model Map → `templateEngine.render("ecqm-artifact.ftl", dataModel)`
- `ExpressionCqlEngine.applyModifier()` → 每個 modifier 呼叫 `renderModifier("XxxModifier.ftl", model)`
- 刪除死碼 `emitStandardSde()`

#### Phase 4：前端 Conjunction 共用化

- 新增 `conjunctionTreeUtils.ts` — 匯出 `resolveKind()`、`CONJ_CYCLE`、`nextConjunction()`、`conjColor()`、`changeConnectorAt()`、`addSubGroup()`、`simplifyTree()`
- `ConjunctionGroup.tsx` 和 `ConjunctionConnector.tsx` 改為從共用模組匯入，移除重複定義
- `EcqmArtifactWorkspace.tsx` — `localArtifact` 包 `useMemo` 避免不必要的子元件重繪
- `handleConnectorChange` 改用已計算的 `filteredChildren` memo

#### Phase 5：Code Review 修正

三重平行代碼審查（Reuse / Quality / Efficiency）後修正：

| 嚴重度 | 修正 |
|--------|------|
| HIGH | 死碼 `emitStandardSde()` 留在 `EcqmCqlBuilder` → 刪除 |
| MEDIUM | `resolveKind` 在 `ConjunctionGroup.tsx` 手寫 ternary chain → 改用 `conjunctionTreeUtils.resolveKind()` |
| MEDIUM | `CONJ_CYCLE` + `nextConjunction` + `conjColor` 在兩個元件重複 → 提取至共用模組 |
| MEDIUM | `localArtifact` 每次 render 建立新物件 → `useMemo` |
| LOW | `handleConnectorChange` 重複過濾 → 使用已有的 `filteredChildren` |

### 影響範圍

| 項目 | 數量 |
|------|------|
| 新增後端檔案 | 1（CqlTemplateEngine） + 30 模板 |
| 修改後端檔案 | 6（CqlArtifactBuilder、ExpressionCqlEngine、EcqmCqlBuilder、ModifierService、TemplateService、ExpressionTreeValidator） |
| 新增後端測試 | 33 snapshot tests |
| 新增前端檔案 | 3（ConjunctionConnector、conjunctionTreeUtils、modifierUtils） |
| 修改前端檔案 | 12 |

### 驗證

- Backend 全部 80 tests 通過（51 ExpressionCqlEngine + 11 CqlArtifactBuilder + 18 EcqmCqlBuilder）
- Frontend TypeScript 編譯通過（`npx tsc --noEmit`，零錯誤）
- CQL 產出與重構前完全一致（snapshot 比對）

---

## PAT-024 — eCQM 視覺化 CQL 產生引擎 — 全端實作 + Publish 至 MeasureDefinition

- **日期**: 2026-03-04
- **範圍**: eCQM — 全新視覺化 eCQM Authoring 模組
- **分類**: 新功能 / 跨模組整合
- **參考依據**: CMS 2026 eCQM Logic and Implementation Guidance Version 9.0

### 問題描述

平台既有 CDS Authoring Tool 可視覺化建構 expression tree 並產生 CDS 結構 CQL（Inclusion/Exclusion/Recommendation），Measure 模組可評估 eCQM，但 eCQM 的 CQL 需手寫。兩個系統之間缺乏橋接，無法從視覺化工具直接產出符合 eCQM 規範的 CQL（IP/Denom/Numer/SDE/Stratifier）並發布至 MeasureDefinition 評估管線。

**目標**：建立獨立的 eCQM Authoring 模組，複用 CDS expression tree 引擎，產生 eCQM 結構的 CQL，並透過「發布」功能橋接至 MeasureDefinition 評估管線。

### 設計決策

| 決策 | 選擇 | 理由 |
|------|------|------|
| CQL 引擎共用 | 抽取 `ExpressionCqlEngine` 共用類別 | CDS 和 eCQM 共用 expression → CQL 轉換邏輯，避免重複 |
| 資料模型 | 獨立 `ecqm_artifact` 表 | 與 CDS artifact 解耦，scoring type / population basis 等欄位為 eCQM 專有 |
| Population 結構 | ConjunctionGroup JSON（同 CDS） | 完全複用前端 ConjunctionGroup 元件 |
| Scoring Types | proportion / ratio / continuous-variable / cohort | 完整實作 CMS v9.0 §2 四種 scoring type |
| Ratio 雙 IP | `initialPopulationDenom` + `initialPopulationNumer` | CMS 允許 ratio 分子和分母有不同 Initial Population |
| Episode-based | `populationBasis` 欄位 | Patient-based (Boolean) 或 Encounter/Procedure 等 FHIR resource type |
| 前端複用 | 直接複用 ConjunctionGroup + ArtifactElement 元件 | 零修改既有元件，只包裝 eCQM 專用 tabs |
| 發布管線 | eCQM → MeasureDefinition → 既有評估管線 | 不新建評估引擎，複用現有 measure 評估流程 |

### 修改內容

#### Phase 1：共用 CQL 引擎抽取

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/authoring/ExpressionCqlEngine.java` |
| 修改 | `backend/.../service/authoring/CqlArtifactBuilder.java` — 委派至 ExpressionCqlEngine |

- 從 `CqlArtifactBuilder`（1056 行）抽取 expression → CQL 通用邏輯為獨立類別
- 搬移方法：`BuildContext`、`buildConjunctionExpression()`、`buildExpression()`、`applyModifier()`、`buildGenericResourceExpression()`、`collectDeclarations()`、emit helpers 等
- CDS `CqlArtifactBuilder` 改為注入 `ExpressionCqlEngine` 並委派
- **零行為變更** — 所有既有 CDS CQL 產生測試通過

#### Phase 2：EcqmArtifactEntity + Repository + Migration

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../entity/EcqmArtifactEntity.java` |
| 新增 | `backend/.../repository/EcqmArtifactRepository.java` |
| 新增 | `backend/.../resources/db/migration/V39__ecqm_artifacts.sql` |

- JPA 實體含 `scoringType`、`populationBasis`、`improvementNotation` 等 eCQM 專有欄位
- JSON 欄位（`populationGroups`、`supplementalData`、`stratifiers`、`baseElements`、`parameters`）使用 `@Transient` 反序列化 + `@PrePersist`/`@PreUpdate` 序列化
- 外鍵關聯 `measure_definition(id) ON DELETE SET NULL`

#### Phase 3：EcqmCqlBuilder

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/ecqm/EcqmCqlBuilder.java` |
| 新增 | `backend/.../model/ecqm/EcqmConstants.java` |

核心 CQL 產生邏輯：
- `buildEcqmCql()` — 主入口，接收 artifact 資料產出完整 CQL
- `emitPopulationDefine()` — 從 population tree 產生 define（Boolean / List return type）
- `emitDualInitialPopulations()` — Ratio 雙 IP 情境（`"Initial Population 1"` / `"Initial Population 2"`）
- `emitObservationFunction()` — Continuous Variable 的 measure observation function
- `emitSupplementalDataDefines()` — 標準 SDE defines（Ethnicity/Race/Sex/Payer）+ 自訂 expression tree
- `emitGroupStratifiers()` — per-group stratifier defines（ratio 雙 IP 時跳過並警告）
- `validateScoringPopulations()` — 依 scoring type 驗證必填 population
- 多 group 名稱後綴支援避免 define 名稱衝突

**Scoring Type → 必填 Population 對照（CMS v9.0 §2）：**

| Scoring | 必填 | 選填 |
|---------|------|------|
| proportion | IP, Denom, Numer | Denom Excl, Denom Except, Numer Excl |
| ratio | IP, Denom, Numer | Denom Excl, Numer Excl |
| continuous-variable | IP, Measure Pop, Measure Obs | Measure Pop Excl |
| cohort | IP | — |

#### Phase 4：Service 層 + Controller + DTOs

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/ecqm/EcqmArtifactService.java` — CRUD（list, get, create, update, delete, duplicate） |
| 新增 | `backend/.../service/ecqm/EcqmCqlGenerationService.java` — 載入 entity → 調用 EcqmCqlBuilder → CQL/ELM/validate |
| 新增 | `backend/.../service/ecqm/EcqmPublishService.java` — 產生 CQL + 建立/更新 MeasureDefinition（GroupDefinition mapping） |
| 新增 | `backend/.../service/ecqm/EcqmExpressionTreeValidator.java` — XSS 過濾（14 patterns）、template/modifier ID 驗證 |
| 新增 | `backend/.../controller/EcqmController.java` — REST API |
| 新增 | `backend/.../model/ecqm/EcqmArtifactRequest.java` |
| 新增 | `backend/.../model/ecqm/EcqmArtifactResponse.java` |
| 新增 | `backend/.../model/ecqm/EcqmArtifactSummary.java` |
| 新增 | `backend/.../model/ecqm/PublishResult.java` |

**API 端點（`/api/ecqm`）：**

| 端點 | 方法 | 說明 |
|------|------|------|
| `/artifacts` | GET | 列出使用者的 eCQM artifacts |
| `/artifacts/{id}` | GET/PUT/DELETE | 單一 artifact CRUD |
| `/artifacts` | POST | 建立 artifact |
| `/artifacts/{id}/duplicate` | POST | 複製 artifact |
| `/artifacts/{id}/cql` | POST | 產生 CQL |
| `/artifacts/{id}/elm` | POST | 產生 CQL + 翻譯為 ELM |
| `/artifacts/{id}/validate` | POST | 驗證 CQL |
| `/artifacts/{id}/publish` | POST | 發布至 MeasureDefinition |
| `/templates` | GET | 取得元素模板 |
| `/modifiers` | GET | 取得修飾器 |
| `/scoring-types` | GET | 取得 scoring type 設定 |

#### Phase 5：Frontend — 全新 eCQM 工作區

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/pages/EcqmPage.tsx` — 主頁面（列表 + 工作區切換） |
| 新增 | `frontend/src/api/ecqmApi.ts` — API client |
| 新增 | `frontend/src/types/ecqm.ts` — TypeScript 型別定義 |
| 新增 | `frontend/src/hooks/useEcqm.ts` — React Query hooks |
| 新增 | `frontend/src/constants/ecqmConstants.ts` — Scoring types、population types、SDE 模板 |
| 修改 | `frontend/src/App.tsx` — 加入 `/ecqm` lazy route |
| 修改 | `frontend/src/api/index.ts` — 加入 ecqmApi export |
| 修改 | `frontend/src/components/layout/Header.tsx` — 加入 eCQM 導航項目 |

**eCQM Workspace（8 tabs）：**

| Tab | 元件 | 複用 |
|-----|------|------|
| Summary | `EcqmSummaryTab.tsx` | 新建 |
| Population Groups | `EcqmPopulationGroupsTab.tsx` + `EcqmPopulationGroupEditor.tsx` + `EcqmPopulationTreeEditor.tsx` | 新建，包裝 ConjunctionGroup |
| Base Elements | placeholder | 複用 CDS 元件模型 |
| Parameters | placeholder | 複用 CDS 元件模型 |
| Supplemental Data | `EcqmSdeTab.tsx` | 新建 |
| Stratifiers | `EcqmStratifiersTab.tsx` | 新建 |
| External CQL | placeholder | 複用 CDS 元件模型 |
| Review CQL | `EcqmCqlPreviewTab.tsx` | 新建 |

其餘新建元件：
- `EcqmArtifactList.tsx` — artifact 列表
- `EcqmArtifactModal.tsx` — 建立 modal（scoring type + population basis 選擇）
- `EcqmArtifactWorkspace.tsx` — 主工作區（debounced auto-save + useRef 穩定回調）
- `EcqmArtifactWorkspaceHeader.tsx` — 標題列（名稱 / 版本 / 狀態 / 發布按鈕）
- `EcqmObservationEditor.tsx` — Continuous Variable observation 編輯（criteria tree + aggregateMethod 下拉）

#### Phase 6：Backend 測試

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/authoring/ExpressionCqlEngineTest.java` — 21 tests |
| 新增 | `backend/.../service/ecqm/EcqmCqlBuilderTest.java` — 18 tests |
| 新增 | `backend/.../service/ecqm/EcqmPublishServiceTest.java` — 4 tests |
| 新增 | `backend/.../controller/EcqmControllerTest.java` — 13 tests（@SpringBootTest + MockMvc） |

#### Phase 7：Code Review 修正

三重平行代碼審查（Reuse / Quality / Efficiency）後修正：

| 嚴重度 | 修正 |
|--------|------|
| HIGH | `validateCql()` 與 `generateAndTranslate()` 完全重複 → 改為委派 |
| HIGH | Entity `baseElementsList` 反序列化 bug（檢查 transient 欄位而非 JSON 欄位）→ 修正 |
| HIGH | XSS validator 僅 3 patterns 弱於 `NoXssValidator` 的 14 patterns → 對齊 |
| HIGH | `save` callback 依賴 `artifact` object ref 導致 debounce 重置 → `useRef` 穩定化 |
| MEDIUM | 手動 `refetchArtifact()` / `refetchList()` 與 React Query invalidation 重複 → 移除 |
| MEDIUM | `DEFAULT_CONJUNCTION_GROUP` spread 可能被意外 mutate → `Object.freeze()` + factory function |
| MEDIUM | 冗餘 `entity.serializeAll()` 呼叫（JPA `@PreUpdate` 已處理）→ 移除 |
| MEDIUM | `emitPopulationDefine()` 未使用的 `isEpisodeBased` / `populationBasis` 參數 → 清除 |
| LOW | SDE tab 使用 array index 作為 React key → 改用 SDE name |
| LOW | 死碼 `useGenerateEcqmElm` hook → 移除 |

### 影響範圍

| 項目 | 數量 |
|------|------|
| 新增後端檔案 | 14 |
| 修改後端檔案 | 3 |
| 新增後端測試 | 4（56 tests） |
| 新增前端檔案 | 17 |
| 修改前端檔案 | 3 |

### 驗證

- Backend 全部 64 新 tests 通過（21 ExpressionCqlEngine + 18 EcqmCqlBuilder + 4 EcqmPublish + 13 EcqmController + 8 CDS 迴歸）
- Frontend TypeScript 編譯通過（`npx tsc --noEmit`，零錯誤）
- CDS Authoring 迴歸測試通過（CqlArtifactBuilderTest 8/8）
- 四種 scoring type CQL 產出驗證（proportion / ratio / continuous-variable / cohort）
- Ratio 雙 IP 產生 `"Initial Population 1"` / `"Initial Population 2"`
- Continuous Variable 產生 function（非 define）含 aggregateMethod 註解
- Publish → MeasureDefinition 建立成功，GroupDefinition 正確映射

---

## PAT-023 — JWT Refresh Token 滑動視窗過期 — 雙令牌架構 + 令牌輪換 + 重用偵測

- **日期**: 2026-03-04
- **範圍**: 安全性 — 認證架構升級
- **分類**: 安全性 / 使用者體驗

### 問題描述

平台原本使用單一 JWT Access Token（24 小時過期）。Token 過期後使用者被強制重新登入，無法優雅恢復。這導致 UX 問題（工作進行中被中斷）及安全性二元取捨（長過期 = 不安全，短過期 = 體驗差）。

**目標**：實作雙令牌架構（短效 Access Token + 長效 Refresh Token），搭配滑動視窗過期與令牌輪換，同時達到強安全性與無縫使用者體驗。

### 設計決策

| 決策 | 選擇 | 理由 |
|------|------|------|
| Refresh Token 儲存 | httpOnly Cookie | XSS 防護（JavaScript 無法存取） |
| Access Token 有效期 | 15 分鐘 | 短暫曝露窗口 |
| Refresh Token 有效期 | 7 天（滑動視窗） | 每次使用後重設 |
| 絕對 Session 上限 | 30 天 | 硬性截止，無論滑動視窗如何延展 |
| 令牌輪換 | 每次 refresh 發放新 token | 舊 token 立即作廢 |
| 重用偵測 | 偵測到重用 → 撤銷整個 family | 令牌洩漏時全面防護 |

### 修改內容

#### 1. Flyway Migration V37 — refresh_token 表

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../db/migration/V37__refresh_tokens.sql` |

- `token_hash`（SHA-256）、`family_id`（UUID 輪換鏈）、`expires_at`（滑動）、`absolute_expires_at`（固定）
- 外鍵關聯 `app_user(id) ON DELETE CASCADE`
- 索引：token_hash（唯一）、user_id、family_id、expires_at

#### 2. 組態更新 — application.yml

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../resources/application.yml` — 拆分 `jwt.expiration-ms` 為 `access-expiration-ms` / `refresh-expiration-ms` / `absolute-session-ms`，新增 `refresh-token.cookie-secure` |
| 修改 | `backend/.../resources/application-dev.yml` — `cookie-secure: false`（HTTP 開發環境） |
| 修改 | `backend/.../test/resources/application-test.yml` — 同步更新 |

#### 3. Entity + Repository

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../entity/RefreshTokenEntity.java` — `isExpired()` 同時檢查滑動與絕對過期 |
| 新增 | `backend/.../repository/RefreshTokenRepository.java` — `revokeByFamilyId()`、`revokeByUserId()`、`deleteExpiredOrRevoked()` |

#### 4. JwtTokenProvider — 拆分過期配置

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../security/JwtTokenProvider.java` — 建構函式接受 4 參數，`generateToken()` 使用 `accessExpirationMs`，保留 `getExpirationMs()` 向後相容 |

#### 5. RefreshTokenCookieUtil — Cookie 管理

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../security/RefreshTokenCookieUtil.java` — `addRefreshTokenCookie()`（httpOnly + Secure + SameSite=Strict + Path=/api/auth）、`clearRefreshTokenCookie()`、`extractRefreshToken()` |

#### 6. RefreshTokenService — 核心商業邏輯

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/RefreshTokenService.java` |

| 方法 | 功能 |
|------|------|
| `createTokenPair(UserEntity)` | 登入時建立新 family UUID，儲存雜湊 refresh token，回傳 access + refresh |
| `refreshTokens(String)` | 輪換：驗證 → 撤銷舊 → 發放新（同 familyId + absoluteExpiresAt），滑動視窗受絕對上限約束 |
| `revokeByToken(String)` | 單裝置登出：撤銷整個 family |
| `revokeAllForUser(Long)` | 全裝置登出 |
| `cleanupExpiredTokens()` | `@Scheduled(cron = "0 0 3 * * *")` 排程清理 |

**重用偵測**：若呈示的 token 已是 `revoked=true`，記錄警告、撤銷整個 family、拋出 `RefreshTokenReuseException`。

#### 7. AuthController — 新端點 + 修改登入

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../controller/AuthController.java` |

| 端點 | 認證 | 變更 |
|------|------|------|
| `POST /login` | permitAll | 改用 `createTokenPair()`，設定 refresh cookie |
| `POST /register` | permitAll | 同上 |
| `POST /okta/callback` | permitAll | 同上 |
| `POST /api/auth/refresh` | permitAll（新增） | 讀取 cookie → `refreshTokens()` → 設定新 cookie + 回傳新 access token |
| `POST /api/auth/logout` | permitAll（新增） | 讀取 cookie → `revokeByToken()` → 清除 cookie |

#### 8. SecurityConfig — 放行新端點

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../config/SecurityConfig.java` — `/api/auth/refresh` 與 `/api/auth/logout` 加入 `permitAll()` |

#### 9. Frontend — Axios 攔截器改造

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/api/client.ts` |

- `withCredentials: true`（跨域傳送 cookie）
- 401 攔截改為靜默 refresh + 請求佇列：
  - `isRefreshing` 旗標防止並行 refresh
  - `failedQueue` 陣列暫存等待中的請求
  - Refresh 成功：更新 localStorage token，重試原始請求，處理佇列
  - Refresh 失敗：清除 localStorage，導向 /login，拒絕佇列
- 派發 `CustomEvent('token-refreshed')` 同步 Redux

#### 10. Frontend — authApi / authSlice / Header / App / types

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/api/authApi.ts` — 新增 `refresh()` 與 `logout()` 方法 |
| 修改 | `frontend/src/store/authSlice.ts` — 新增 `updateToken` reducer |
| 修改 | `frontend/src/components/layout/Header.tsx` — 登出時呼叫 `authApi.logout()` |
| 修改 | `frontend/src/App.tsx` — 監聽 `token-refreshed` 事件 → `dispatch(updateToken(...))` |
| 修改 | `frontend/src/types/index.ts` — 新增 `RefreshResponse` 型別 |

#### 11. 後端測試

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/RefreshTokenServiceTest.java` — 11 測試案例 |
| 修改 | `backend/.../controller/AuthControllerTest.java` — 新增 6 個 refresh/logout 測試 |
| 修改 | `backend/.../security/JwtTokenProviderTest.java` — 更新建構函式為 4 參數 |

**RefreshTokenServiceTest 涵蓋：**
1. `createTokenPair` — 有效配對、雜湊儲存
2. `refreshTokens` — 有效輪換、同 family 繼承
3. 重用偵測 — 已撤銷 token → 整個 family 撤銷
4. 過期 token → `InvalidRefreshTokenException`
5. 絕對過期超過 → `InvalidRefreshTokenException`
6. 停用使用者 → family 撤銷 + 例外
7. 滑動視窗受絕對上限約束
8. Token 未找到 → 例外
9. `revokeByToken` / `revokeAllForUser`
10. `cleanupExpiredTokens`

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 6 |
| 修改檔案 | 14 |
| 新增行數 | +1024 / -36 |

### 驗證

- Backend 編譯成功（`mvn compile`）
- RefreshTokenServiceTest — 11/11 通過
- AuthControllerTest — 14/15 通過（1 個預存失敗與本次無關）
- JwtTokenProviderTest — 全數通過
- Frontend TypeScript 型別檢查通過（零新增錯誤）
- 多分頁測試：2 個分頁同時 access token 過期，僅觸發 1 次 refresh（請求佇列機制）

---

## PAT-022 — TWCORE IG 範本支援（19 預設範本 + TW 代碼系統 + 目錄擴充）

- **日期**: 2026-02-28
- **範圍**: Authoring — 功能增強
- **分類**: 功能增強 / 台灣核心實作指引（TWCORE IG）

### 問題描述

Authoring 規則編寫功能僅使用通用 FHIR R4 範本（每個資源類型一個）。使用者每次都必須手動從 TWCORE 目錄分頁挑選代碼。目標是新增 **TWCORE 專用預設範本**（如「BMI 觀測」、「糖尿病」），讓使用者可直接針對台灣核心 IG Profile 撰寫規則，減少操作步驟。

### 修改內容

#### 1. 新增 19 個 TWCORE 範本至 formTemplates.json

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../resources/data/formTemplates.json` — 新增 19 個 TWCORE 範本，分布於 Observations(9)、Conditions(7)、Medications(2)、AllergyIntolerances(1)，各自帶有 `twcoreOnly: true` 旗標及預設 LOINC/SNOMED 代碼 |

**Observations (9)：** BMI 觀測、血壓觀測、體重觀測、身高觀測、體溫觀測、心率觀測、血糖觀測、糖化血色素觀測、檢驗結果(通用)
**Conditions (7)：** 糖尿病、高血壓、心臟衰竭、慢性腎臟病、氣喘、慢性阻塞性肺病、病情(通用)
**Medications (2)：** 藥品處方(TWCORE)、用藥紀錄(TWCORE)
**AllergyIntolerances (1)：** 過敏(TWCORE)

所有 TWCORE 範本重用與通用範本相同的 `template` 欄位（如 `GenericObservation`），CQL 生成流程無需任何修改。

#### 2. Backend — 解析 twcoreOnly 旗標

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../model/authoring/FormTemplate.java` — 新增 `private Boolean twcoreOnly` 欄位 |
| 修改 | `backend/.../service/authoring/TemplateService.java` — `parseTemplate()` 新增 `setTwcoreOnly()` |

#### 3. Backend — 新增台灣代碼系統

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../model/authoring/AuthoringConstants.java` — `CODE_SYSTEM_NAMES` 從 `Map.of()` 改為 `Map.ofEntries()`，新增 ICD-10-CM-TW、ICD-10-PCS-TW、ATC、FDA-TW 四個台灣代碼系統 |

#### 4. Frontend — TWCORE 模式切換與範本篩選

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/types/authoring.ts` — `FormTemplate` 介面新增 `twcoreOnly?: boolean` |
| 修改 | `frontend/src/components/authoring/ArtifactWorkspace.tsx` — 新增 TWCORE 模式切換開關（工具列右側），`twcoreMode` state 傳遞至 ConjunctionGroup / Subpopulations / BaseElements |
| 修改 | `frontend/src/components/authoring/element-select/ElementSelectDropdown.tsx` — 接受 `twcoreMode` prop，關閉時隱藏 `twcoreOnly` 範本，顯示 "TW" Chip 標記 |
| 修改 | `frontend/src/components/authoring/element-select/ElementSelect.tsx` — 傳遞 `twcoreMode`，建立元素時複製 `codes` / `valueSets` 至欄位 |
| 修改 | `frontend/src/components/authoring/builder/ConjunctionGroup.tsx` — 新增 `twcoreMode` prop 傳遞 |
| 修改 | `frontend/src/components/authoring/subpopulations/Subpopulations.tsx` — 新增 `twcoreMode` prop 傳遞 |
| 修改 | `frontend/src/components/authoring/base-elements/BaseElements.tsx` — 新增 `twcoreMode` prop 傳遞 |

#### 5. 擴充 TWCORE 目錄

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../resources/data/twcoreCatalog.json` — 新增 LOINC 2345-7（血漿葡萄糖）與 4548-4（HbA1c）至血糖分類；新增 MedicationRequest ATC 值集（21 碼 / 4 類別）；新增 Procedure ICD-10-PCS-TW 值集（10 碼）；新增藥物過敏分類（7 碼） |

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 12 |
| 新增檔案 | 0 |
| 新增範本 | 19（9 Observation + 7 Condition + 2 Medication + 1 AllergyIntolerance） |
| 新增代碼系統 | 4（ICD-10-CM-TW、ICD-10-PCS-TW、ATC、FDA-TW） |
| 新增行數 | +562 / -22 |

### 驗證

- JSON 格式驗證 — `formTemplates.json` 與 `twcoreCatalog.json` 均通過 Python json.load 驗證
- `tsc --noEmit` — 通過，零錯誤
- VS Code diagnostics — 所有修改檔案零警告
- 設計驗證：TWCORE 範本使用與通用範本相同的 `template` 值，CQL 生成流程（`collectFromTree()` → `codesystem`/`code` 宣告）不受影響
- TWCORE 模式關閉時，19 個新範本完全隱藏（回歸正常）

---

## PAT-021 — Hospital-Grade 改善：draftOrders + 預取解析 + 重大警示彈窗

- **日期**: 2026-02-27
- **範圍**: CDS Hooks — 功能增強
- **分類**: 功能增強 / 醫院級合規

### 問題描述

現有 CDS 實作在三個關鍵面向與醫院生產系統存在差距：
1. **draftOrders 未處理**：order-select / order-sign hooks 的 `context.draftOrders` 從未被解析或合併至 prefetch provider，沙盒 UI 也無法輸入草稿醫令
2. **重大警示無差異化**：所有卡片（info / warning / critical）呈現方式相同，醫院系統應以阻斷式彈窗處理 critical 卡片
3. **預取範本未動態解析**：當 prefetch 資料缺失時，系統直接回退至 FHIR server URL，未依 CDS Hooks 規範動態解析預取範本

### 修改內容

#### Feature 1：draftOrders 處理（order-select / order-sign）

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/.../service/cds/CdsInvocationService.java` — 新增 `parseDraftOrders()` 方法，解析 context.draftOrders 為 FHIR Bundle 並合併資源至 prefetch provider |
| 修改 | `backend/.../service/cds/PrefetchRetrieveProvider.java` — 新增 `addResources()` 方法 + ServiceRequest code 比對支援 |
| 修改 | `backend/.../model/cds/CdsSandboxRequest.java` — 新增 `draftOrders` 欄位 |
| 修改 | `backend/.../controller/CdsHooksController.java` — sandbox handler 注入 draftOrders 至 CdsRequest context |
| 修改 | `frontend/src/types/index.ts` — `CdsSandboxRequest` 新增 `draftOrders` 欄位，`CdsCard` 新增 `overrideReasons` 欄位 |
| 修改 | `frontend/src/components/cds/SandboxPanel.tsx` — 新增 Draft Orders JSON 編輯器分頁（僅 order-select / order-sign 可見） |

#### Feature 2：重大警示阻斷式彈窗

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/components/cds/CriticalCardDialog.tsx` — MUI Dialog，`disableEscapeKeyDown` + 阻斷背景點擊，Accept / Override 按鈕，Override 支援預設原因選單或自由文字 |
| 修改 | `frontend/src/components/cds/SandboxPanel.tsx` — 回應卡片分區（critical vs normal），critical 卡片以佇列依序顯示彈窗 |
| 修改 | `frontend/src/components/cds/InvokeServicePanel.tsx` — 同上，live invocation 面板整合 critical card dialog |
| 修改 | `backend/.../service/cds/CqlTupleCardStrategy.java` — 新增 `parseOverrideReasons()` 從 CQL Tuple 提取覆寫原因 |

#### Feature 3：預取範本動態解析

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/.../service/cds/PrefetchResolver.java` — `@Component`，替換 `{{context.patientId}}` 等範本變數，透過 FhirClientFactory 從客戶端 FHIR server 取得資源，支援 Bearer Token 認證 |
| 修改 | `backend/.../service/cds/CdsInvocationService.java` — 注入 PrefetchResolver，prefetch 缺失時嘗試動態解析範本，解析失敗才回退至 FHIR server URL |

#### i18n

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/locales/en/cds.json` — 新增 `sandbox.tabDraftOrders`、`sandbox.draftOrdersDescription`、`critical.*`（7 鍵） |
| 修改 | `frontend/src/locales/zh-TW/cds.json` — 對應繁體中文翻譯（9 鍵） |

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 10 |
| 新增檔案 | 2（`PrefetchResolver.java`、`CriticalCardDialog.tsx`） |
| 新增方法 | 5（`parseDraftOrders`、`addResources`、`parseOverrideReasons`、`resolve`、`substituteTemplate`） |
| 新增 i18n 鍵 | 18（9 en + 9 zh-TW） |
| 新增行數 | +580 / -8 |

### 驗證

- `tsc --noEmit` — 通過（僅預存 react-i18next 型別宣告警告）
- Sandbox → order-select hook → Draft Orders 分頁可見 → 輸入 MedicationRequest Bundle → CQL 可存取草稿藥物
- CQL 回傳 `indicator: "critical"` → 阻斷式彈窗 → 必須 Accept / Override 才能繼續
- 移除 prefetch 資料 → 設定 fhirServer URL → 服務自動透過範本取得 Patient → CQL 正常執行
- 既有 patient-view hooks 不受影響（回歸正常）

---

## PAT-020 — 分頁驗證錯誤明細（Tooltip + Alert）

- **日期**: 2026-02-24
- **範圍**: Authoring — UX 改善
- **分類**: UX 改善 / 可操作性

### 問題描述

ArtifactWorkspace 的 11 個分頁在元素有驗證錯誤時會顯示驚嘆號圖示，但使用者無法得知**哪些欄位**缺少或**在哪裡**修正。頂部的錯誤訊息（「CQL 產生失敗 / Request failed with status code 400」）過於籠統，缺乏可操作的指引。

### 修改內容

#### Step 1：增強 `computeTabStatuses` 回傳型別

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/authoring/ArtifactWorkspace.tsx` |

- 新增 `TabStatusInfo` 介面：`{ status: TabStatus, errors: string[] }`
- `computeTabStatuses` 回傳 `TabStatusInfo[]`（原為 `TabStatus[]`）
- 錯誤字串格式：`"i18nKey||{jsonParams}"`，在渲染時延遲翻譯

#### Step 2：收集具體錯誤訊息

| 動作 | 函式 |
|------|------|
| 新增 | `getModifierMissingFields(mod)` — 從 ModifierCard 提取修飾器必填欄位驗證邏輯 |
| 新增 | `collectTreeErrors(tree)` — 走訪元素樹收集具體錯誤 |

**樹驗證（Inclusions / Exclusions）：**
- 元素缺少名稱 → `"Element #N: missing element name"`
- 修飾器類型不匹配 → `"「X」: modifier「Y」expects Z, got W"`
- 修飾器缺少必填欄位 → `"「X」: modifier「Y」missing: value, unit"`（LookBack / ValueComparison / WithUnit / String / Qualifier / BeforeAfterInterval）

**其他分頁：**
- 建議缺少文字 → `"Recommendation #N: missing text"`
- 參數缺少名稱 / 類型 → `"Parameter #N: missing name"` / `"「X」: missing type"`
- 子族群缺少名稱 → `"Subpopulation #N: missing name"`
- 基礎元素缺少名稱 → `"Base Element #N: missing name"`

#### Step 3：分頁圖示 Tooltip

| 動作 | 檔案 |
|------|------|
| 修改 | `ArtifactWorkspace.tsx` — Tabs 區域 |

- `ErrorIcon` 包裝於 `<Tooltip>`，hover 顯示錯誤條目的項目符號清單
- 最多顯示 8 條錯誤，超出部分顯示 `…+N`

#### Step 4：分頁內容頂部 Alert 橫幅

| 動作 | 檔案 |
|------|------|
| 修改 | `ArtifactWorkspace.tsx` — Tab content Box |

- 當作用中分頁有錯誤時，在分頁內容區頂部渲染 `<Alert severity="warning">`
- 列出所有驗證問題（無上限），使用者切換到問題分頁即可看到

#### Step 5：i18n 鍵

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/locales/en/authoring.json` — 新增 `workspace.validation` 區段（9 鍵） |
| 修改 | `frontend/src/locales/zh-TW/authoring.json` — 對應繁體中文翻譯（9 鍵） |

新增鍵：`elementMissingName`、`elementModifierTypeMismatch`、`elementModifierMissingFields`、`recommendationMissingText`、`parameterMissingName`、`parameterMissingType`、`subpopulationMissingName`、`baseElementMissingName`、`errorsFound`

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 3 |
| 新增介面 | 2（`TabStatusInfo`、`ModifierLike`） |
| 新增函式 | 2（`getModifierMissingFields`、`collectTreeErrors`） |
| 新增 i18n 鍵 | 18（9 en + 9 zh-TW） |
| 新增 MUI 元件引用 | 2（`Tooltip`、`Alert`） |

### 驗證

- `tsc --noEmit` — 通過
- `vite build` — 通過（AuthoringPage chunk 153→154 KB）
- Hover 分頁錯誤圖示 → 顯示具體錯誤 Tooltip
- 切換到錯誤分頁 → 顯示 Alert 橫幅列出所有問題
- 修正缺少欄位 → 錯誤自動消失

---

## PAT-019 — 錯誤處理統一（Backend 例外層級 + Frontend 錯誤提取）

- **日期**: 2026-02-22
- **範圍**: 跨模組 — 錯誤處理改善
- **分類**: 健壯性 / UX 改善

### 問題描述

平台的錯誤處理碎片化：

1. **Backend**：4 種不同的錯誤模式（`RuntimeException`、`ResponseStatusException`、`ResponseEntity.badRequest().build()`、手工 JSON 字串 `"{\"error\":\"...\"}"`），導致 API 回應格式不一致
2. **Frontend**：30+ 個 mutation 缺乏 `onError` 處理，失敗時無使用者回饋（靜默失敗）
3. **Resilience4j 降級**：部分 Circuit Breaker fallback 回傳空結果而非拋出錯誤，FHIR 伺服器離線時前端顯示空白而無錯誤提示
4. **Frontend 錯誤擷取**：所有元件使用 `(err as Error).message`，無法正確解析 GlobalExceptionHandler 的結構化回應

### 修改內容

#### Phase 1：Backend 例外層級

| 動作 | 檔案 |
|------|------|
| 新增 | `exception/ResourceNotFoundException.java` — 404 Not Found |
| 新增 | `exception/DuplicateResourceException.java` — 409 Conflict |
| 新增 | `exception/ValidationException.java` — 400 Bad Request（含 details） |
| 修改 | `exception/GlobalExceptionHandler.java` — 新增 4 個 `@ExceptionHandler` |
| 修改 | `controller/AdminController.java` — 5 處例外替換 |
| 修改 | `controller/AuthController.java` — 2 處例外替換 |
| 修改 | `controller/MeasureController.java` — 3 處例外替換 |

- `RuntimeException("User not found")` → `ResourceNotFoundException("User", id)`
- `badRequest().build()` → `DuplicateResourceException` / `ValidationException`
- `ResponseStatusException(NOT_FOUND, ...)` → `ResourceNotFoundException`
- 新增 `DataIntegrityViolationException` → 409 handler

#### Phase 2：Backend Controller 錯誤標準化

| 動作 | 檔案 |
|------|------|
| 修改 | `controller/FhirController.java` — ~15 處 inline 回應替換為 `throw` |
| 修改 | `service/fhir/FhirDataProviderService.java` — 2 處 fallback 修復 |
| 修改 | `service/fhir/FhirTerminologyService.java` — 3 處 fallback 修復 |

- 所有 `badRequest().body("{\"error\":\"...\"}")` → `throw new IllegalArgumentException("...")`
- 所有 `badRequest().body(Map.of("error", ...))` → `throw new IllegalArgumentException("...")`
- 靜默降級 `return new Bundle()` / `return false` / `return new ArrayList<>()` → `throw new FhirServerUnavailableException(...)`

#### Phase 3：Frontend 錯誤工具

| 動作 | 檔案 |
|------|------|
| 新增 | `utils/errorUtils.ts` — `extractApiError()` 函式 |
| 修改 | `hooks/useInvalidatingMutation.ts` — 新增 `onError` 選項 |
| 修改 | `main.tsx` — 全域 mutation `onError` 安全網 |

`extractApiError` 依序嘗試：
1. `AxiosError.response.data.message`（GlobalExceptionHandler 格式）
2. `AxiosError.response.data.error`（舊版 Map 格式）
3. `AxiosError.message`（網路錯誤）
4. `Error.message`
5. Fallback: `"An unknown error occurred"`

#### Phase 4：Frontend Mutation 錯誤處理 + i18n

| 動作 | 檔案 |
|------|------|
| 修改 | `locales/en/common.json` — 新增 `mutationErrors` 區段（7 鍵） |
| 修改 | `locales/zh-TW/common.json` — 對應中文翻譯 |
| 修改 | `MeasureLibrary.tsx` — 5 個 mutation 加 `onError` |
| 修改 | `TestCasesTab.tsx` — 3 個 mutation 加 `onError` |
| 修改 | 13 個元件 — `(err as Error).message` → `extractApiError(err)` |

受影響元件：EditorPage, MeasureEditor, MeasureLibrary, MeasurePanel, DataRequirementsTab, TestCasesTab, TestCaseImportDialog, ManageServicesPanel, SandboxPanel, InvokeServicePanel, ImportCqlDialog, ImplementationGuideBrowser, useCqlStructure

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 4（3 Backend exceptions + 1 Frontend util） |
| 修改檔案 | 23 |
| Backend 錯誤模式統一 | 4 種 → 1 種（GlobalExceptionHandler ErrorResponse） |
| Frontend `(err as Error).message` 消除 | 25 處 → 0 處 |
| 靜默 Resilience4j fallback 修復 | 5 處 |
| 新增 i18n 鍵 | 14（7 en + 7 zh-TW） |

### 驗證

- `mvn compile -q` — 通過
- `tsc --noEmit` — 通過
- `grep "(err as Error).message" frontend/src/` — 0 結果
- `grep "badRequest().body(\"{" backend/src/main/java/com/cqlplatform/controller/` — 0 結果

---

## PAT-018 — API 參考文件 + OpenAPI 規格檔

- **日期**: 2026-02-22
- **範圍**: 文件 — API 參考手冊
- **分類**: 文件撰寫

### 問題描述

CQL Platform 後端有 17 個 REST Controller、222 個端點，涵蓋 CQL 編輯/執行、品質指標管理、FHIR 資源操作、CDS 決策支援、權限管理等完整功能，但缺乏統一的 API 參考文件。開發者和整合人員需要查閱原始碼才能了解端點用法。

### 修改內容

#### 新增 `API.md` — Markdown API 參考手冊（1,522 行）

| 動作 | 檔案 |
|------|------|
| 新增 | `API.md` |

- 繁體中文撰寫
- 13 個章節：概述、認證、CQL 操作、品質指標、FHIR 資源、CDS 決策支援、CDS 撰寫工具、EHR 整合、管理功能、指標目錄、通知、使用者設定、附錄
- 每個端點包含：HTTP 方法、路徑、說明、參數表格（名稱/位置/類型/必填/說明）、請求範例 JSON、回應範例 JSON
- 涵蓋全部 222 個端點、17 個 Controller

#### 新增 `openapi.yaml` — OpenAPI 3.0.3 規格檔（5,158 行）

| 動作 | 檔案 |
|------|------|
| 新增 | `openapi.yaml` |

- 標準 OpenAPI 3.0.3 格式
- 25 個 tags（依控制器分類）
- JWT Bearer security scheme
- 40+ component schemas（LoginRequest、AuthResponse、CqlLibrary、MeasureDefinition、TestCase、CdsRequest、CdsResponse 等）
- 所有 `description` 欄位使用繁體中文
- 可被 Swagger UI、Redoc、Postman 直接匯入使用

### 涵蓋的 Controller（17 個，222 端點）

| Controller | 端點數 |
|------------|--------|
| AuthController | 8 |
| CqlController | 27 |
| MeasureController | 65 |
| FhirController | 29 |
| CdsServiceConfigController | 13 |
| CdsHooksController | 6 |
| AuthoringController | 25 |
| EhrIntegrationController | 10 |
| AdminController | 5 |
| AuditController | 6 |
| DepartmentController | 5 |
| IndicatorCatalogController | 5 |
| NotificationController | 6 |
| SettingsController | 2 |
| UserApiKeyController | 3 |
| UserLibraryPrefsController | 6 |
| SmartConfigController | 1 |

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 2（API.md + openapi.yaml） |
| 總行數 | 6,680（1,522 + 5,158） |
| 涵蓋端點 | 222 |

### 驗證

- `npx yaml-lint openapi.yaml` — YAML 語法正確
- endpoint 計數：`grep -c 'operationId:' openapi.yaml` → 222

---

## PAT-017 — 補完科別分類功能（篩選 + 指派）

- **日期**: 2026-02-22
- **範圍**: eQCM — 科別分類補完
- **分類**: 功能補完 / 醫學中心適用性

### 問題描述

科別（Department）功能已有 CRUD 基礎設施（DepartmentEntity、DepartmentController、DepartmentSelector 元件），Dashboard 也有科別篩選，但指標管理頁面（MeasureLibrary + MeasureDetailsTab）缺少科別指派和篩選功能，導致科別分類無法在指標層級實際使用。

### 修改內容

#### Step 1：後端 — Repository 新增查詢方法

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/repository/MeasureDefinitionRepository.java` |

- 新增 `findByDepartment(String department)` — 依科別代碼查詢
- 新增 `findByDepartmentAndSearchTerm(String department, String search)` — `@Query` 結合科別 + 名稱/標題模糊搜尋

#### Step 2：後端 — Service 新增科別篩選邏輯

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` |

- 新增 `search(String searchTerm, String department)` 多載方法
- 4 種組合（search+dept / dept-only / search-only / all）各走不同 Repository 查詢
- 原有 `search(String)` 委派至新方法（department=null），無 breaking change

#### Step 3：後端 — Controller 加 department 參數

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` |

- `GET /api/measures` 新增 `@RequestParam(required = false) String department`
- 改用 `definitionService.search(search, department)` 統一入口

#### Step 4：前端 — measureApi 加 department 參數

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/api/measureApi.ts` |

- `getMeasures(search?, department?)` 新增第二選用參數
- 動態建構 `params` 物件，僅在有值時附加

#### Step 5：前端 — MeasureLibrary 加科別篩選器

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/MeasureLibrary.tsx` |

- 搜尋列旁新增 `DepartmentSelector`（showAll=true），與搜尋框水平排列
- `departmentFilter` 狀態 + 傳入 `measureApi.getMeasures(search, department)`
- React Query queryKey 包含 `departmentFilter`，切換科別自動重新查詢
- 表格 "Setting" 欄改為 "Department" 欄，顯示 `m.department` Chip

#### Step 6：前端 — MeasureDetailsTab 加科別欄位

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/MeasureDetailsTab.tsx` |

- General Information accordion 的 Setting 欄位旁新增 `DepartmentSelector`（showAll=false）
- 使用 `updateField('department', value)` 連動表單狀態和 dirty 追蹤

#### Step 7：i18n 翻譯鍵新增

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/locales/en/measures.json` |
| 修改 | `frontend/src/locales/zh-TW/measures.json` |

- `library.tableHeaders.department`: "Department" / "科別"
- `details.fields.department`: "Department" / "科別"
- `details.fields.departmentHelper`: helper text（EN + zh-TW）

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 9（3 後端 + 4 前端 + 2 locale） |
| 新增 i18n 鍵 | 3（EN + zh-TW 各一份） |
| 新增後端方法 | 3（2 Repository + 1 Service） |

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- 無 DB 遷移（`department` 欄位已在 V26 建立）
- 既有 `search(String)` 保持向下相容

---

## PAT-016 — Okta SSO (OIDC) 整合

- **日期**: 2026-02-21
- **範圍**: 安全性 — 企業 SSO 登入
- **分類**: 功能新增 / 企業適用性

### 問題描述

平台目前僅支援本地帳密（username/password）+ JWT 認證。企業用戶需要透過 Okta SSO 單一登入，以簡化帳號管理並符合企業安全政策。需要在保留現有本地登入的同時，加入 OIDC Authorization Code Flow。

### 架構決策

採用 **Backend-Mediated Authorization Code Flow**：保留現有 JWT 架構不變，不引入 Spring `oauth2Login`（避免與 stateless 設計衝突），由後端自行處理 OIDC token exchange。

```
用戶點擊 "使用 Okta 登入"
  → 前端 redirect 到 Okta 授權頁（帶 state/nonce）
  → Okta 驗證後 redirect 回 /auth/okta/callback?code=xxx&state=yyy
  → 前端 POST /api/auth/okta/callback { code, redirectUri, nonce }
  → 後端 exchange code → 驗證 ID token → JIT 建立/查找用戶 → 產生本地 JWT
  → 前端收到 JWT → dispatch setCredentials（與本地登入完全一致）
```

### 修改內容

#### Step 1：資料庫遷移

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V29__okta_sso.sql` |

- `app_user.password` 改為 nullable（SSO 用戶無密碼）
- 新增 `auth_provider`（VARCHAR(20), NOT NULL, DEFAULT 'LOCAL'）
- 新增 `external_id`（VARCHAR(255)）— Okta subject ID
- 新增 `display_name`（VARCHAR(200)）— Okta 顯示名稱
- 唯一索引 `idx_user_external_id` on (auth_provider, external_id)

#### Step 2：Maven 依賴

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/pom.xml` |

- 新增 `com.nimbusds:nimbus-jose-jwt:9.37.3` — OIDC ID token 簽章驗證（JWKS）

#### Step 3：Okta 設定

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/resources/application.yml` |
| 新增 | `backend/src/main/java/com/cqlplatform/config/OktaProperties.java` |

- `application.yml` 新增 `okta:` 區段（4 個環境變數：`OKTA_ENABLED`, `OKTA_CLIENT_ID`, `OKTA_CLIENT_SECRET`, `OKTA_ISSUER`）
- `OktaProperties`：`@ConfigurationProperties(prefix = "okta")`，衍生方法 `getTokenEndpoint()`、`getJwksUri()`、`getAuthorizationEndpoint()`

#### Step 4：OIDC 服務

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/service/OktaOidcService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/OktaUserInfo.java` |

- `OktaOidcService`（`@ConditionalOnProperty(name = "okta.enabled", havingValue = "true")`）
  - `exchangeCodeForUser(code, redirectUri, nonce)` → OktaUserInfo
  - POST Okta token endpoint（Basic Auth）
  - Nimbus JWKS processor 驗證 ID token 簽章
  - 驗證 iss、aud、nonce、exp claims
  - 擷取 sub、email、preferred_username、name
- `OktaUserInfo`：DTO（sub, email, preferredUsername, name）

#### Step 5：UserEntity + Repository + UserDetailsService

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/entity/UserEntity.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/repository/UserRepository.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/security/CustomUserDetailsService.java` |

- `UserEntity`：新增 `AuthProvider` enum（LOCAL, OKTA）、`authProvider`、`externalId`、`displayName` 欄位；`password` 改為 nullable
- `UserRepository`：新增 `findByAuthProviderAndExternalId(AuthProvider, String)`
- `CustomUserDetailsService`：null password → placeholder `"{noop}SSO_USER_NO_PASSWORD"`（SSO 用戶無法透過本地登入端點認證）

#### Step 6：AuthController 擴充

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/model/auth/OktaCallbackRequest.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/AuthController.java` |

- `OktaCallbackRequest`：code（NotBlank）、redirectUri（NotBlank）、nonce（optional）
- `GET /api/auth/okta/config`：回傳 Okta 設定（enabled, authorizationEndpoint, clientId, scopes）
- `POST /api/auth/okta/callback`：exchange code → JIT provisioning → JWT
  - JIT：`findByAuthProviderAndExternalId(OKTA, sub)` → 未找到則建立新用戶（role=USER, 無密碼）
  - Username 衍生順序：preferred_username > email prefix > okta_{sub}，唯一性保障
  - 更新 displayName/email（若 Okta 端已變更）
  - 檢查 enabled 旗標，拒絕已停用用戶
- `GET /api/auth/me`：回應新增 `authProvider`、`displayName`

#### Step 7：SecurityConfig + AdminController

| 動作 | 檔案 |
|------|------|
| 修改 | `backend/src/main/java/com/cqlplatform/config/SecurityConfig.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/AdminController.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/model/auth/UserSummary.java` |

- SecurityConfig：Okta 端點加入 public 白名單
- AdminController：`toUserSummary()` 包含 `authProvider`；`resetUserPassword()` 拒絕 OKTA 用戶
- UserSummary：新增 `authProvider` 欄位

#### Step 8：前端類型 + API

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/types/index.ts` |
| 修改 | `frontend/src/api/authApi.ts` |

- 新增 `OktaConfig`、`OktaCallbackRequest` 介面
- `User` 和 `UserSummary` 新增 `authProvider?`、`displayName?`
- `authApi` 新增 `getOktaConfig()`、`oktaCallback()`

#### Step 9：OktaCallbackPage

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/pages/OktaCallbackPage.tsx` |

- 從 URL 擷取 code + state
- 驗證 state 匹配 sessionStorage（CSRF 防護）
- 呼叫 `authApi.oktaCallback()` → dispatch `setCredentials` → navigate `/`
- 錯誤狀態：Alert + 「返回登入」按鈕

#### Step 10：LoginPage + App.tsx

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/pages/LoginPage.tsx` |
| 修改 | `frontend/src/App.tsx` |

- LoginPage：掛載時 `getOktaConfig()` → 啟用時顯示 Divider（"OR"）+ 「使用 Okta 登入」按鈕
- 按鈕點擊：生成 state/nonce → 存入 sessionStorage → redirect Okta 授權 URL
- App.tsx：lazy import `OktaCallbackPage`，新增 `/auth/okta/callback` 路由

#### Step 11：AdminUsersPage

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/pages/AdminUsersPage.tsx` |

- 使用者名稱旁顯示 "Okta SSO" Chip（`color="info"`）
- OKTA 用戶隱藏「重設密碼」按鈕

#### Step 12：i18n

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/locales/en/common.json` |
| 修改 | `frontend/src/locales/zh-TW/common.json` |

- 8 個新 `auth.*` 鍵（EN + zh-TW）：or、loginWithOkta、ssoProcessing、ssoFailed、ssoMissingCode、ssoStateMismatch、authProviderLocal、authProviderOkta

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 6（1 SQL + 1 Config + 2 Service + 1 Model + 1 Page） |
| 修改檔案 | 13（5 Backend + 5 Frontend + 2 locale + 1 pom.xml） |
| 新增 i18n 鍵 | 8（EN + zh-TW） |
| 新增 API 端點 | 2（GET /okta/config + POST /okta/callback） |

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- 預設行為（OKTA_ENABLED=false）：GET /api/auth/okta/config → `{"enabled":false}`，登入頁無 Okta 按鈕
- 本地登入流程不受影響
- 啟用 Okta 後：完整 OIDC Authorization Code Flow + JIT 用戶建立

---

## PAT-015 — P2-8: EHR/HIS 整合連接器

- **日期**: 2026-02-21
- **範圍**: FHIR — 院內系統整合
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心需要與院內 HIS 系統整合：預設連接 FHIR R4 Server、依身分證/病歷號搜尋病人、自動匯入病歷作為測試資料。目前平台的 FHIR 操作僅對手動設定的單一伺服器。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V28__ehr_integration.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/EhrConnectionEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/PatientImportEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/EhrConnectionRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/PatientImportRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/fhir/PatientSearchResult.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/fhir/PatientImportPreview.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/fhir/EhrConnectionService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/fhir/PatientSearchService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/fhir/PatientImportService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/controller/EhrIntegrationController.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/fhir/FhirClientFactory.java` — `createAuthenticatedClient` |

- V28 遷移：`ehr_connection`（連線管理）+ `patient_import`（匯入記錄）
- `EhrConnectionService`：CRUD + `testConnection`（metadata capability 檢查）
- `PatientSearchService`：依 identifier/name 搜尋病人 + 資源預覽（$everything / 逐類型查詢）
- `PatientImportService`：$everything → Bundle → TestCase 建立
- `FhirClientFactory.createAuthenticatedClient`：支援 basic（BasicAuthInterceptor）和 bearer（BearerTokenAuthInterceptor）驗證
- `EhrIntegrationController`：10 個端點（連線 CRUD、測試、病人搜尋、預覽、匯入、匯入記錄）

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/api/ehrApi.ts` |
| 新增 | `frontend/src/components/ehr/EhrConnectionList.tsx` — 連線表格 + 測試/編輯/刪除 |
| 新增 | `frontend/src/components/ehr/EhrConnectionForm.tsx` — 建立/編輯對話框（含驗證方式切換） |
| 新增 | `frontend/src/components/ehr/PatientSearchPanel.tsx` — 病人搜尋面板 |
| 新增 | `frontend/src/components/ehr/PatientImportDialog.tsx` — 匯入預覽 + 確認 |
| 新增 | `frontend/src/components/ehr/PatientImportHistory.tsx` — 匯入歷史表格 |
| 新增 | `frontend/src/components/ehr/EhrImportForTestCase.tsx` — 步驟式匯入對話框（選連線→搜病人→預覽→匯入） |
| 修改 | `frontend/src/api/index.ts` — 匯出 `ehrApi` |
| 修改 | `frontend/src/types/index.ts` — 4 個新介面 |
| 修改 | `frontend/src/pages/FhirPage.tsx` — 新增「EHR 連線」第三分頁 |
| 修改 | `frontend/src/components/measure/TestCaseEditor.tsx` — 「從 EHR 匯入」按鈕 |
| 修改 | `frontend/src/locales/{en,zh-TW}/fhir.json` — 50+ 個 `ehr.*` 鍵 |

- FhirPage 新增第三個分頁：EHR Connections（CloudSync 圖示）
- TestCaseEditor 工具列新增「Import from EHR」按鈕，開啟 EhrImportForTestCase 步驟式對話框
- 連線管理：狀態徽章（untested/success/failed）、驗證方式選擇（none/basic/bearer）、科別歸屬

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## PAT-014 — P2-9: 指標儀表板增強（Recharts）

- **日期**: 2026-02-21
- **範圍**: eQCM — 品質監控視覺化
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心需要完善的品質監控視覺化：趨勢圖（月/季/年）、科別維度下鑽分析、閾值告警、自動產生品質報告。原有儀表板僅有基本概覽卡片和近期評估表格。

### 修改內容

#### 依賴新增

- `recharts` v2.x — 宣告式 React 圖表庫，含 LineChart、BarChart、PieChart、ResponsiveContainer

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V27__dashboard_enhancements.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/MeasureThresholdEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/MeasureThresholdRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/ThresholdAlert.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/EnhancedDashboardData.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/QualityReport.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/measure/DashboardService.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` — 7 個新端點 |

- V27 遷移：`measure_threshold` 資料表（target/warning/critical 閾值）
- `DashboardService`：趨勢計算、科別下鑽、閾值告警偵測、品質報告產生
- 新端點：`/dashboard/enhanced`、`/dashboard/trends`、`/dashboard/department/{code}`、`/dashboard/alerts`、`/{id}/thresholds`（GET/POST）、`/dashboard/report`

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/components/dashboard/ScoreTrendChart.tsx` — Recharts LineChart（多指標疊加 + 閾值參考線） |
| 新增 | `frontend/src/components/dashboard/DepartmentDrilldownChart.tsx` — Recharts BarChart（依閾值著色） |
| 新增 | `frontend/src/components/dashboard/ScoreDistributionChart.tsx` — Recharts PieChart |
| 新增 | `frontend/src/components/dashboard/ThresholdAlertPanel.tsx` — 告警清單（嚴重/警告圖示） |
| 新增 | `frontend/src/components/dashboard/QualityReportPanel.tsx` — 品質報告摘要 + 指標分數表格 |
| 新增 | `frontend/src/components/dashboard/DashboardFilterBar.tsx` — 科別 + 期間篩選列 |
| 修改 | `frontend/src/api/measureApi.ts` — 7 個新 API 函式 |
| 修改 | `frontend/src/types/index.ts` — 7 個新介面 |
| 修改 | `frontend/src/pages/MeasureDashboardPage.tsx` — 重建佈局 |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` — 17 個 `dashboard.*` 鍵 |

儀表板佈局：
```
┌─────────────────────────────────────────────────┐
│ FilterBar: [科別 ▼] [月度 ▼]                    │
├──────────────────────┬──────────────────────────┤
│ 概覽卡片 (5)         │ 閾值告警面板             │
├──────────────────────┴──────────────────────────┤
│ 分數趨勢圖 (LineChart, 全寬)                    │
├────────────────────────┬────────────────────────┤
│ 科別下鑽 (BarChart)    │ 評分分佈 (PieChart)    │
├────────────────────────┴────────────────────────┤
│ 近期評估表格           │ 品質報告面板           │
└─────────────────────────────────────────────────┘
```

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## PAT-013 — P2-10: 科別多租戶隔離

- **日期**: 2026-02-21
- **範圍**: 跨模組 — 軟性多租戶
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心有多個科別，各科的指標和資料需要適當隔離，同時支援跨科共享。需要科別概念、科別層級的資料篩選、科別管理員角色。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V26__department_multi_tenancy.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/DepartmentEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/DepartmentRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/DepartmentService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/controller/DepartmentController.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/UserEntity.java` — 新增 `department`、`DEPARTMENT_ADMIN` 角色 |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureDefinitionEntity.java` — 新增 `department` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureReportEntity.java` — 新增 `department` |
| 修改 | `backend/src/main/java/com/cqlplatform/model/measure/MeasureDefinition.java` — 新增 `department` |
| 修改 | `backend/src/main/java/com/cqlplatform/security/OwnershipVerifier.java` — `isDepartmentAdmin`、`getCurrentDepartment`、`verifySameDepartment` |
| 修改 | `backend/src/main/java/com/cqlplatform/security/JwtTokenProvider.java` — department claim |
| 修改 | `backend/src/main/java/com/cqlplatform/config/SecurityConfig.java` — `DEPARTMENT_ADMIN` 授權 |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` — department 篩選 |

- V26 遷移：`department` 資料表（含 10 筆醫院科別種子資料）；`app_user`、`measure_definition`、`measure_report` 新增 `department` 欄位
- `DepartmentController`：5 個端點（列表、取得、子科別、建立、更新）
- JWT token 攜帶 department claim
- `DEPARTMENT_ADMIN` 角色可管理同科別的使用者和指標

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/api/departmentApi.ts` |
| 新增 | `frontend/src/components/common/DepartmentSelector.tsx` |
| 修改 | `frontend/src/types/index.ts` — `Department` 介面 |
| 修改 | `frontend/src/store/authSlice.ts` — department 狀態 |
| 修改 | `frontend/src/locales/{en,zh-TW}/common.json` — 5 個 `department.*` 鍵 |

- `DepartmentSelector`：可重用下拉選單，useQuery 載入科別清單，支援「全部」選項

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## PAT-012 — P2-11: 衛福部指標代碼對照

- **日期**: 2026-02-21
- **範圍**: eQCM — 指標代碼管理
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心需要將 eCQM 指標對應到衛福部、健保署 P4P/DRG、及 CMS 的官方指標代碼，以便在品質申報時進行代碼關聯。目前平台的指標定義缺少這些代碼欄位，也無法瀏覽官方指標清單。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V25__indicator_catalog.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/IndicatorCatalogEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/IndicatorCatalogRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/measure/IndicatorCatalogService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/controller/IndicatorCatalogController.java` |
| 新增 | `backend/src/main/resources/data/indicator_catalog_seed.json` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureDefinitionEntity.java` — 新增 4 欄位 |
| 修改 | `backend/src/main/java/com/cqlplatform/model/measure/MeasureDefinition.java` — 新增 4 欄位 |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` — entityToModel/modelToEntity/update |

- V25 遷移：`measure_definition` 新增 `moh_indicator_code`、`nhia_p4p_code`、`drg_indicator_code`、`indicator_category`；新增 `indicator_catalog` 資料表（code + source 唯一約束）
- `IndicatorCatalogController`：5 個端點（搜尋、取得、建立、更新、批次匯入）
- 種子資料：13 筆（5 MOH + 3 NHIA_P4P + 2 DRG + 3 CMS）

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/api/indicatorApi.ts` |
| 新增 | `frontend/src/components/measure/IndicatorCatalogDialog.tsx` |
| 新增 | `frontend/src/components/measure/IndicatorMappingSection.tsx` |
| 修改 | `frontend/src/types/index.ts` — `IndicatorCatalogEntry` 介面 |
| 修改 | `frontend/src/components/measure/MeasureDetailsTab.tsx` — 嵌入 IndicatorMappingSection |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` — 18 個 `indicators.*` 鍵 |

- `IndicatorMappingSection`：可收合手風琴，含 4 個代碼欄位（MOH、NHIA、DRG、類別），每欄位有搜尋按鈕開啟目錄瀏覽對話框
- `IndicatorCatalogDialog`：搜尋 + 來源篩選 + 表格選取

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## PAT-011 — P1-5: 持久化通知系統 + 工作流程推播

- **日期**: 2026-02-21
- **範圍**: 通知系統
- **分類**: 功能新增 / 協作

### 問題描述

平台缺少通知系統。當指標被提交審核、核准或退回時，相關使用者不會收到任何通知，必須手動刷新頁面才能看到狀態變更。醫學中心需要即時通知機制以加速審核流程。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V24__notifications.sql` |
| 新增 | `backend/src/main/java/com/cqlplatform/entity/NotificationEntity.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/repository/NotificationRepository.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/service/NotificationService.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/controller/NotificationController.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` |

- `notification` 資料表：recipient、type、title、message、link、is_read、created_at、read_at
- `NotificationService`：完整 CRUD + SSE（Server-Sent Events）即時推播
  - 工作流程通知：`notifyMeasureSubmitted`（通知所有審核者）、`notifyMeasureApproved`、`notifyMeasureRejected`、`notifyMeasureShared`
  - SSE emitter 管理：`ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>`
- `NotificationController`：6 個端點
  - `GET /api/notifications` — 最新 50 則通知
  - `GET /api/notifications/unread-count` — 未讀數量
  - `POST /api/notifications/{id}/read` — 標記已讀
  - `POST /api/notifications/read-all` — 全部標記已讀
  - `DELETE /api/notifications/{id}` — 刪除通知
  - `GET /api/notifications/subscribe` — SSE 訂閱
- `MeasureDefinitionService`：在 `submitForReview`、`approveMeasure`、`rejectMeasure`、`shareMeasure` 中注入通知觸發

#### 前端

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/api/notificationApi.ts` |
| 新增 | `frontend/src/hooks/useNotifications.ts` |
| 新增 | `frontend/src/components/layout/NotificationBell.tsx` |
| 修改 | `frontend/src/components/layout/Header.tsx` |
| 修改 | `frontend/src/locales/{en,zh-TW}/common.json` |

- `notificationApi`：封裝 5 個 API 呼叫（getNotifications、getUnreadCount、markAsRead、markAllAsRead、deleteNotification）
- `useNotifications` hook：React Query 查詢 + SSE 訂閱自動刷新
- `NotificationBell`：Header 工具列的通知鈴鐺按鈕
  - `Badge` 顯示未讀數量（max 99）
  - `Popover` 下拉面板：通知列表 + 時間戳 + 類型圖示
  - 每則通知可點擊導航到相關頁面、刪除、標記已讀
  - 全部標記已讀按鈕
  - 空狀態提示

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 8（4 後端 + 1 SQL + 3 前端） |
| 修改檔案 | 4（1 後端 + 1 前端 + 2 locale） |
| 新增 i18n 鍵 | 7（EN + zh-TW） |
| 新增 API 端點 | 6 |

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## PAT-010 — P1-6: FHIR Bundle 檔案上傳匯入

- **日期**: 2026-02-21
- **範圍**: eQCM — FHIR Measure Bundle 匯入增強
- **分類**: UX 改善

### 問題描述

MeasureLibrary 的 FHIR Bundle 匯入功能僅支援文字區域貼上 JSON，不支援檔案上傳。使用者需要從檔案系統複製內容到剪貼簿再貼上，不便操作。

### 修改內容

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/MeasureLibrary.tsx` |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` |

- 新增 Upload File 按鈕 + 隱藏 `<input type="file">`（.json, .xml）
- 選擇檔案後自動讀取內容填入文字區域
- 文字區域行數從 12 減少到 10 以容納按鈕

### 驗證

- `npx tsc --noEmit` — 無型別錯誤

---

## PAT-009 — P1-4: 審核者欄位 + 退回原因 UI

- **日期**: 2026-02-21
- **範圍**: eQCM — 審核工作流程增強
- **分類**: 功能新增 / 工作流程

### 問題描述

審核工作流程缺少審核者追蹤欄位和退回原因 UI。當審核者退回指標時，擁有者無法看到退回原因，需重新溝通才能了解需要修改的內容。

### 修改內容

#### 後端

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V23__review_workflow_fields.sql` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureDefinitionEntity.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/model/measure/MeasureDefinition.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` |

- V23 遷移：新增 `reviewed_by`、`approved_by`、`review_comment`、`reviewed_at` 欄位
- `approveMeasure()`：設定 `approvedBy`、`reviewedBy`、`reviewedAt`，清除 `reviewComment`
- `rejectMeasure()`：設定 `reviewedBy`、`reviewedAt`、`reviewComment`，清除 `approvedBy`

#### 前端

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/types/index.ts` |
| 修改 | `frontend/src/components/measure/MeasureEditor.tsx` |
| 修改 | `frontend/src/hooks/useMeasures.ts` |
| 修改 | `frontend/src/api/measureApi.ts` |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` |

- MeasureEditor：退回時顯示對話框（含原因 TextField），狀態為 draft 且有 reviewComment 時顯示退回通知 Alert
- `useRejectMeasure` hook 改為接受 `{ id, reason }` 物件
- `rejectMeasure` API 新增 `reason` 選用參數

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤

---

## PAT-008 — P1-7: 人類可讀文件匯出

- **日期**: 2026-02-21
- **範圍**: eQCM — 文件輸出
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心在指標發布前需要產出人類可讀的指標文件供臨床委員會審閱。需要完整的 HTML 文件，包含指標描述、族群準則表格、CQL 邏輯程式碼、以及中繼資料。

### 修改內容

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/service/measure/HumanReadableService.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` |
| 修改 | `frontend/src/api/measureApi.ts` |
| 修改 | `frontend/src/components/measure/MeasureEditor.tsx` |
| 修改 | `frontend/src/locales/{en,zh-TW}/measures.json` |

- `HumanReadableService`：產生完整 HTML 文件，包含：
  - CSS 樣式（teal/navy 配色，@media print 列印支援）
  - 標題區含狀態徽章
  - 目錄（TOC）
  - 描述、理論基礎、臨床指引
  - 每群組族群準則表格
  - 分層器表格
  - CQL 原始碼 `<pre>` 區塊
  - 補充資料、中繼資料表格、頁尾
- `GET /api/measures/{id}/export/human-readable` — 回傳 `text/html`
- 前端：匯出選單新增「Human Readable」選項，以 `Blob` 開啟新視窗

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤

---

## PAT-007 — 測試案例批次匯入 + 日期平移

- **日期**: 2026-02-21
- **範圍**: eQCM — 測試案例管理增強
- **分類**: 功能新增 / 醫學中心適用性

### 問題描述

醫學中心需要從 HIS 系統批次匯入真實病歷資料作為測試資料集，並將歷史病歷的日期調整到評估期間內。原有匯入功能僅在前端逐筆呼叫 API，無日期平移能力，不適合大量資料匯入場景。

### 修改內容

#### Feature A：後端日期平移服務

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/service/measure/DateShiftService.java` |

- 遞迴走訪 FHIR Bundle JSON，識別所有日期相關欄位（`effectiveDateTime`、`birthDate`、`start`、`end`、`issued`、`authored` 等 20+ 個欄位名）
- 支援 FHIR 日期格式：`YYYY-MM-DD`、`YYYY-MM-DDThh:mm:ss+zz:zz`、`YYYY-MM`
- `shiftDates(bundleJson, shiftDays)` — 平移所有日期
- `calculateAutoShift(bundleJson, targetPeriodEnd)` — 自動計算平移天數使最晚日期對齊目標期間

#### Feature B：後端批次匯入端點

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/BatchTestCaseImportRequest.java` |
| 新增 | `backend/src/main/java/com/cqlplatform/model/measure/BatchTestCaseImportResult.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/TestCaseService.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/controller/MeasureController.java` |

- `BatchTestCaseImportRequest`：包含 `testCases` 清單和 `dateShiftDays` 參數
- `BatchTestCaseImportResult`：回傳 `successCount`、`failureCount`、`imported` 清單、`errors` 詳細訊息
- `TestCaseService.batchImport()` — 逐筆匯入，失敗不中斷，記錄錯誤
- `POST /api/measures/{measureId}/test-cases/batch-import` — 需擁有者或 ADMIN 權限

#### Feature C：前端匯入對話框

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/components/measure/TestCaseImportDialog.tsx` |
| 修改 | `frontend/src/components/measure/TestCasesTab.tsx` |
| 修改 | `frontend/src/api/measureApi.ts` |
| 修改 | `frontend/src/types/index.ts` |

- `TestCaseImportDialog`：完整匯入對話框，包含：
  - 拖放上傳區（支援 .json / .ndjson）
  - 檔案解析預覽（顯示標題、系列、族群數量）
  - 日期平移開關 + 天數輸入
  - 匯入進度條 + 結果摘要（成功/失敗數、錯誤明細）
  - 支援：JSON 陣列、單一測試案例、MADiE 格式、原始 FHIR Bundle
- `TestCasesTab`：移除舊有 inline 匯入邏輯，改用 `TestCaseImportDialog`
- 匯出增強：新增 `sortOrder` 欄位至匯出格式，提取共用 `toExportShape` 和 `downloadBlob` 輔助函式

#### i18n 鍵新增

| 檔案 | 新增鍵 |
|------|--------|
| `locales/{en,zh-TW}/measures.json` | 18 個 `importDialog.*` 鍵 |

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 4（1 服務 + 2 模型 + 1 元件） |
| 修改檔案 | 6（2 後端 + 2 前端 + 2 locale） |
| 新增 i18n 鍵 | 18（EN + zh-TW） |

### 驗證

- `mvn compile -q` — 編譯成功
- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功

---

## PAT-006 — Population Criteria 佈局優化 + Reporting 分頁

- **日期**: 2026-02-21
- **範圍**: eQCM — Population Criteria Tab 重構
- **分類**: UX 改善 / 功能新增

### 問題描述

Population Criteria Tab 存在多項與 MADiE 的差異：所有內容擠在單一垂直捲動頁面、缺少 Reporting 分頁（Improvement Notation + Rate Aggregation）、無佈局分欄（標準族群 vs 排除族群）、無完成度指示器、無側邊欄導覽、分層缺少說明欄位。

### 修改內容

#### Feature A：子分頁結構

將單一頁面拆分為 4 個子分頁：

| 子分頁 | 索引 | 內容 |
|--------|------|------|
| Populations | 0 | 群組欄位 + 雙欄族群卡片 + 觀察條件 + 評分單位 |
| Stratifications | 1 | 每群組的分層管理（含新增的說明欄位） |
| Reporting | 2 | Improvement Notation + Rate Aggregation（新增） |
| Supplemental | 3 | 風險校正 + 補充資料 |

- `subTab` 狀態控制顯示，切換分頁不遺失未儲存的變更
- 儲存按鈕和驗證提示始終顯示於頂部

#### Feature B：雙欄族群佈局

在 Populations 子分頁中，將族群卡片分為左右兩欄：

| 左欄（標準族群） | 右欄（排除/例外） |
|------------------|-------------------|
| Initial Population | Denominator Exclusion |
| Denominator | Denominator Exception |
| Numerator | Numerator Exclusion |
| Measure Population | Measure Population Exclusion |

- 使用 MUI `Grid` 組件，`md={6}` 雙欄，`xs={12}` 小螢幕垂直堆疊
- 無排除族群時左欄自動展開為 `md={12}`
- `EXCLUSION_POPULATION_TYPES` 常數定義分類邏輯

#### Feature C：Reporting 分頁 + 後端支援

新增 Improvement Notation 和 Rate Aggregation 欄位：

**後端：**

| 動作 | 檔案 |
|------|------|
| 新增 | `backend/src/main/resources/db/migration/V22__improvement_notation_rate_aggregation.sql` |
| 修改 | `backend/src/main/java/com/cqlplatform/model/measure/MeasureDefinition.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/entity/MeasureDefinitionEntity.java` |
| 修改 | `backend/src/main/java/com/cqlplatform/service/measure/MeasureDefinitionService.java` |

- V22 遷移：新增 `improvement_notation VARCHAR(20)` 和 `rate_aggregation VARCHAR(2000)` 欄位
- Model：新增 `improvementNotation`（驗證 `increase|decrease|`）和 `rateAggregation`（@Size max=2000）
- Entity + Service：entityToModel / modelToEntity / update 三處同步更新

**前端：**

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/types/index.ts` — 新增 `improvementNotation` 和 `rateAggregation` 欄位 |
| 修改 | `frontend/src/components/measure/PopulationCriteriaTab.tsx` — Reporting 子分頁 UI |

- Improvement Notation：下拉選單（increase / decrease / 未設定）
- Rate Aggregation：多行文字輸入，說明如何彙總多個族群的計算結果

#### Feature D：完成度指示器

在標題列下方新增 `LinearProgress` 進度條，含 Tooltip 詳細清單：

| 檢查項目 | 條件 |
|----------|------|
| 已定義族群 | 任一群組有族群 |
| 已指定運算式 | 任一族群有 CQL 運算式 |
| 已設定改善標記 | improvementNotation 有值 |
| 已定義分層 | 任一群組有分層 |
| 已定義補充資料 | 有風險校正或補充資料元素 |

- 顯示 `x/5` 計數和百分比進度條
- 100% 完成時進度條變為綠色
- Hover Tooltip 顯示每項的 ✓/○ 狀態

#### Feature E：左側導覽列

新增可摺疊側邊欄（180px），使用 MUI `List` + `ListItemButton`：

| 區段 | 項目 | 點擊行為 |
|------|------|----------|
| Groups | 每個群組 | 切換到 Populations 子分頁 + 捲動到該群組 |
| — | Stratifiers | 切換到 Stratifications 子分頁 |
| — | Reporting | 切換到 Reporting 子分頁 |
| — | Supplemental | 切換到 Supplemental 子分頁 |

- 每個項目顯示 ✓（綠色）/ ○（灰色）完成狀態
- `ChevronLeft` / `ChevronRight` 按鈕切換收合
- 響應式：`md+` 顯示側邊欄，`xs/sm` 隱藏（回退使用水平 Tabs）
- 群組點擊使用 `scrollIntoView({ behavior: 'smooth' })` 平滑捲動

#### Feature F：分層說明欄位

每個 Stratifier 卡片新增多行 `TextField`（2-4 行）：

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/PopulationCriteriaTab.tsx` — Stratifications 子分頁 |

- 使用 `StratifierDefinition.description` 既有欄位（型別和後端已支援）
- 位於 CQL 運算式和 Population Associations 下方

#### i18n 鍵新增

| 檔案 | 新增鍵 |
|------|--------|
| `locales/{en,zh-TW}/measures.json` | `populationCriteria.subTabs.*`（4）、`exclusions`、`noStratifiers` |
| | `populationCriteria.reporting.*`（7）、`populationCriteria.completeness.*`（6） |
| | `populationCriteria.sidebar.*`（3）、`stratifierFields.description/descriptionPlaceholder`（2） |

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 1（V22 SQL 遷移） |
| 修改檔案 | 7（3 後端 + 3 前端 + 1 型別） |
| 新增 i18n 鍵 | ~22（EN + zh-TW 各一份） |
| PopulationCriteriaTab | +546 / -213 行 |

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `mvn compile -q` — 編譯成功
- 所有既有狀態（groups, riskAdjustments, supplementalData）在子分頁間切換時保持不變
- 儲存操作包含新欄位 improvementNotation 和 rateAggregation

---

## PAT-005 — MeasureController 授權與 IDOR 修復

- **日期**: 2026-02-20
- **範圍**: 安全性 — 後端授權強化
- **分類**: 安全性 / 存取控制

### 問題描述

`MeasureController` 存在多項授權缺陷：部分端點缺少所有權檢查，允許任意已認證使用者修改或刪除他人的指標；測試案例端點存在 IDOR（不安全的直接物件參考）漏洞；排程端點完全無授權保護。

### 修改內容

#### 新增輔助方法

| 方法 | 用途 |
|------|------|
| `requireMeasure(Long id)` | 取得指標或拋出 404 |
| `requireOwnedMeasure(Long id)` | 取得指標並驗證當前使用者為擁有者（或 ADMIN） |
| `verifyTestCaseBelongsToMeasure(Long measureId, Long testCaseId)` | 驗證測試案例確實屬於指定指標，防止 IDOR |
| `requireOwnedSchedule(Long scheduleId)` | 透過排程的 `measureDefinitionId` 查詢父指標並驗證所有權 |

#### 高優先級修復

| 問題 | 端點 | 修復 |
|------|------|------|
| `updateMeasure` 所有權繞過 | `PUT /{id}` | 改為 `requireOwnedMeasure(id)` |
| `deleteMeasure` 無授權 | `DELETE /{id}` | 新增 `requireOwnedMeasure(id)` |
| 測試案例 IDOR | 7 個 `/{measureId}/test-cases/**` 端點 | 新增 `requireMeasure` / `requireOwnedMeasure` + `verifyTestCaseBelongsToMeasure` |

#### 中優先級修復

| 問題 | 端點 | 修復 |
|------|------|------|
| `exportReport` 無所有權檢查 | `GET /reports/{reportId}/export` | 新增 `evaluatedBy` 所有權檢查（比照 `getReport` / `deleteReport`） |
| 排程端點無授權 | 5 個排程端點 | `getSchedules` / `createSchedule` 使用 `requireOwnedMeasure(measureId)`；`updateSchedule` / `deleteSchedule` / `triggerSchedule` 使用 `requireOwnedSchedule(scheduleId)` |
| `getMeasuresByOwner` 資訊洩漏 | `GET /owner/{username}` | 限制為本人或 ADMIN，否則回傳 403 |
| `getSharedMeasures` 資訊洩漏 | `GET /shared/{username}` | 限制為本人或 ADMIN，否則回傳 403 |

#### 服務層新增

| 動作 | 檔案 | 修改 |
|------|------|------|
| 修改 | `ScheduledMeasureEvaluationService.java` | 新增 `getScheduleById(Long)` 方法，回傳 `Optional<MeasureScheduleEntity>` |

### 影響統計

| 類別 | 數量 |
|------|------|
| 修改檔案 | 2（MeasureController.java, ScheduledMeasureEvaluationService.java） |
| 修復端點 | 15 個（3 高優先 + 12 中優先） |
| 新增方法 | 4 個輔助方法（Controller） + 1 個服務方法 |

### 驗證

- `mvn compile -q` — 編譯成功
- 所有變更遵循既有 `OwnershipVerifier` 模式（`verifyOwnership` / `isAdmin` / `getCurrentUsername`）
- 唯讀端點（`getTestCase`, `runTestCase`, `runAllTestCases`, `runWithCoverage`）使用 `requireMeasure`（驗證存在性但不限制擁有者）
- 變更端點（`createTestCase`, `updateTestCase`, `deleteTestCase`）使用 `requireOwnedMeasure`（需擁有者或 ADMIN）

---

## PAT-004 — 術語查詢 Drawer + 測試案例草稿自動儲存

- **日期**: 2026-02-20
- **範圍**: 跨模組 UX 改善
- **分類**: 功能新增 / 使用者體驗

### 問題描述

使用者編輯 CQL 或建構測試案例病人時，經常需要查詢術語代碼（ICD-10、LOINC、SNOMED 等）。目前存在三個痛點：

1. **測試案例編輯器無草稿持久化** — 切換到其他 Tab（如 CQL）會銷毀所有未儲存的工作，無任何警告
2. **視覺化建構器的代碼欄位僅支援 TWCORE 瀏覽** — 無通用代碼搜尋（依系統 + 文字）
3. **無法在編輯中查詢術語** — 必須完全離開目前的工作環境

### 修改內容

#### Feature A：全域術語查詢 Drawer

右側 MUI Drawer（420px），透過 Header 工具列的 `ManageSearch` 圖示開啟，包含 3 個分頁：

| 分頁 | 重用 Hook | 用途 |
|------|-----------|------|
| Code Search | `useSearchCodes` | 在代碼系統中依文字搜尋代碼 |
| ValueSet Browse | `useSearchValueSets` + `useExpandValueSet` | 尋找值集，展開查看代碼 |
| Code Lookup | `useLookupCode` | 查詢特定系統 + 代碼的詳細資訊 |

每列結果有 **Copy**（剪貼簿）和 **Use**（透過 callback 插入欄位）按鈕。

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/contexts/TerminologyDrawerContext.tsx` |
| 新增 | `frontend/src/hooks/useTerminologyDrawer.ts` |
| 新增 | `frontend/src/components/terminology/TerminologyLookupDrawer.tsx` |
| 新增 | `frontend/src/components/terminology/DrawerCodeSearchPanel.tsx` |
| 新增 | `frontend/src/components/terminology/DrawerValueSetPanel.tsx` |
| 新增 | `frontend/src/components/terminology/DrawerCodeLookupPanel.tsx` |
| 修改 | `frontend/src/main.tsx` — 加入 `<TerminologyDrawerProvider>` |
| 修改 | `frontend/src/components/layout/Header.tsx` — 新增工具列按鈕 + 渲染 Drawer |

關鍵設計：`openDrawer(options?)` 支援 `tab`、`system`、`searchText`、`onSelect` callback，讓代碼欄位可以開啟 Drawer 並接收選取結果，無需緊密耦合。

#### Feature B：測試案例編輯器草稿自動儲存

仿照 `MeasureCqlTab.tsx` 的 localStorage 模式。

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/hooks/useTestCaseDraft.ts` |
| 修改 | `frontend/src/components/measure/TestCaseEditor.tsx` |
| 修改 | `frontend/src/components/measure/TestCasesTab.tsx` |

- `useTestCaseDraft` hook：debounced 5 秒存入 localStorage，鍵格式 `testcase-draft-{measureId}-{testCaseId|'new'}`，7 天過期
- `TestCaseEditor`：掛載時還原草稿，變更時自動儲存，儲存成功時清除；顯示 info Alert 附帶「捨棄草稿」按鈕
- `TestCasesTab`：`editing` 狀態持久化至 `sessionStorage`（`testcase-editing-{measureId}`），切換 Tab 時保留編輯中的測試案例

**流程：**
```
使用者編輯測試案例 → debounced 5 秒存入 localStorage
使用者切換 MeasureEditor Tab → TestCaseEditor 卸載（狀態遺失）
使用者切回 Test Cases Tab →
  TestCasesTab 讀取 sessionStorage → 重新開啟對應測試案例的 TestCaseEditor
  TestCaseEditor 讀取 localStorage → 還原草稿
  顯示 Alert：「已還原先前未儲存的草稿。」[捨棄草稿]
使用者儲存 → 清除 localStorage 草稿 + sessionStorage 編輯狀態
```

#### Feature C：視覺化建構器代碼欄位的術語搜尋按鈕

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/testcase-builder/CodeField.tsx` |
| 修改 | `frontend/src/components/testcase-builder/CodeableConceptField.tsx` |

- `CodeField`：在非 required binding 路徑新增 `Search` 圖示按鈕 → `openDrawer({ tab: 0, system: ..., onSelect: ... })`
- `CodeableConceptField`：每個 coding 列新增 `Search` 圖示按鈕 → `openDrawer({ tab: 0, onSelect: updateCoding })`

#### i18n 鍵新增

| 檔案 | 新增鍵 |
|------|--------|
| `locales/{en,zh-TW}/terminology.json` | 12 個 `drawer.*` 鍵 |
| `locales/{en,zh-TW}/common.json` | `toolbar.terminologyLookup` |
| `locales/{en,zh-TW}/measures.json` | `testCaseEditor.draftRestored`、`testCaseEditor.discardDraft`、`testCaseBuilder.fields.searchTerminology` |

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 6（Context、Hook、4 個 Drawer 元件） + 1（useTestCaseDraft Hook） |
| 修改檔案 | 8（main.tsx、Header.tsx、TestCaseEditor、TestCasesTab、CodeField、CodeableConceptField、6 個 locale JSON） |
| 新增 i18n 鍵 | 17（12 + 1 + 4） |

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功
- 無新增後端端點，全部重用既有 API 和 React Query hooks

---

## PAT-003 — 全平台國際化完成（Phase 5-9）

- **日期**: 2026-02-20
- **範圍**: i18n — 剩餘 5 個模組全面翻譯
- **分類**: 國際化 / 使用者體驗

### 問題描述

平台的 i18n 架構已在 Phase 1-4 完成 Core（common, validation, editor, builder）和 Measures 模組，但 CDS Hooks、FHIR Browser、Terminology、Authoring、Admin/Audit 等 5 個模組仍使用硬編碼英文字串，共約 860+ 個字串未國際化。

### 修改內容

#### Phase 5：CDS Hooks 模組（~120 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/cds.json` |
| 新增 | `frontend/src/locales/zh-TW/cds.json` |
| 修改 | `frontend/src/pages/CdsPage.tsx` |
| 修改 | `frontend/src/components/cds/CdsPanel.tsx` |
| 修改 | `frontend/src/components/cds/InvokeServicePanel.tsx` |
| 修改 | `frontend/src/components/cds/ManageServicesPanel.tsx` |
| 修改 | `frontend/src/components/cds/SandboxPanel.tsx` |
| 修改 | `frontend/src/components/cds/AnalyticsPanel.tsx` |
| 修改 | `frontend/src/components/cds/ApiKeyManager.tsx` |

- 命名空間 `cds`，頂層鍵：`page`, `panel`, `invoke`, `manage`, `sandbox`, `analytics`, `apiKey`
- 7 個元件 + 1 個頁面全部替換為 `t()` 呼叫

#### Phase 6：FHIR Browser 模組（~200 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/fhir.json` |
| 新增 | `frontend/src/locales/zh-TW/fhir.json` |
| 修改 | `frontend/src/pages/FhirPage.tsx` |
| 修改 | `components/fhir/` 下全部 12 個元件 |

- 命名空間 `fhir`，頂層鍵：`page`, `browser`, `search`, `searchParams`, `read`, `validate`, `terminology`, `transaction`, `bulkExport`, `detail`, `editor`, `history`, `ig`
- 13 個檔案全部替換，含 `ImplementationGuideBrowser` 內 5 個子元件

#### Phase 7：Terminology 模組（~70 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/terminology.json` |
| 新增 | `frontend/src/locales/zh-TW/terminology.json` |
| 修改 | `frontend/src/pages/TerminologyPage.tsx` |
| 修改 | `components/terminology/TerminologyBrowser.tsx` |
| 修改 | `components/terminology/ValueSetTab.tsx` |
| 修改 | `components/terminology/CodeLookupTab.tsx` |
| 修改 | `components/terminology/CodeValidationTab.tsx` |

- 命名空間 `terminology`，頂層鍵：`page`, `browser`, `valueSet`, `codeLookup`, `codeValidation`
- 複數支援：`resultCount` / `resultCount_other`

#### Phase 8：Authoring 模組（~450 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/authoring.json` |
| 新增 | `frontend/src/locales/zh-TW/authoring.json` |
| 修改 | `frontend/src/pages/AuthoringPage.tsx` |
| 修改 | `components/authoring/` 下全部 30 個元件 |

- 命名空間 `authoring`，20 個頂層鍵：`page`, `list`, `modal`, `workspace`, `header`, `cpg`, `conjunction`, `element`, `elementBody`, `expression`, `modifier`, `conjunctionType`, `customModifier`, `elementSelect`, `elementDescriptions`, `valueSetField`, `chooseCode`, `subpopulations`, `recommendations`, `errorStatement`, `baseElements`, `parameters`, `externalCql`, `cqlPreview`, `testing`, `summary`, `importCql`, `queryBuilder`
- 含 HTML 的翻譯鍵（如 `expression.ageIs`）使用 `dangerouslySetInnerHTML` 渲染
- 模組層級常數（`GRADES`, `CONDITION_OPTIONS`, `PARAMETER_TYPES`, `TAB_LABELS`）移入元件內部以存取 `t()`
- `ElementSelectDropdown` 使用動態鍵 `` t(`elementDescriptions.${id}`) `` 搭配靜態 fallback

#### Phase 9：Admin & Audit 模組（~90 keys）

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/admin.json` |
| 新增 | `frontend/src/locales/zh-TW/admin.json` |
| 修改 | `frontend/src/pages/AdminUsersPage.tsx` |
| 修改 | `frontend/src/pages/AuditDashboardPage.tsx` |

- 命名空間 `admin`，頂層鍵：`users`, `audit`
- `AuditDashboardPage` 將 `t` 作為 prop 傳遞給同檔案內的子元件

#### 共同：註冊命名空間

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/i18n.ts` |

- 新增 5 個命名空間：`cds`, `fhir`, `terminology`, `authoring`, `admin`
- 平台現共有 10 個命名空間：common, validation, editor, builder, measures, cds, fhir, terminology, authoring, admin

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 10（5 組 locale JSON） |
| 修改檔案 | ~55 |
| 翻譯鍵數 | ~930 |
| 命名空間 | 5 個新增（平台共 10 個） |

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功
- 10 組 locale 檔案均存在於 `en/` 和 `zh-TW/` 目錄
- 語言切換（en ↔ zh-TW）涵蓋全平台所有頁面

---

## PAT-002 — Measures 模組國際化（en / zh-TW）

- **日期**: 2026-02-19
- **範圍**: i18n — Measures 模組全面翻譯
- **分類**: 國際化 / 使用者體驗

### 問題描述

平台的 i18n 架構（i18next + react-i18next）已在 Editor、Builder、Common 模組完成（Phase 1-3），但 Measures 模組（含 MeasuresPage、MeasureDashboardPage、26 個 `components/measure/` 元件、19 個 `components/testcase-builder/` 元件）仍使用硬編碼英文字串，共約 400+ 個字串未國際化。

### 修改內容

#### Step 1：建立 measures 命名空間的 locale 檔案

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/locales/en/measures.json` |
| 新增 | `frontend/src/locales/zh-TW/measures.json` |

- 約 400 個翻譯鍵，按元件分類組織
- 頂層鍵：`page`, `dashboard`, `library`, `editor`, `details`, `cql`, `populationCriteria`, `populationCard`, `dataRequirements`, `evaluation`, `testCases`, `testCaseEditor`, `testCaseResult`, `evaluationResult`, `comparison`, `share`, `validation`, `panel`, `batch`, `workflow`, `reports`, `schedules`, `riskAdjustment`, `supplementalData`, `observations`, `audit`, `dateCalculator`, `coverage`, `testCaseBuilder`

#### Step 2：註冊命名空間

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/i18n.ts` |

- 匯入 `measuresEn` / `measuresZhTW`
- 在 `resources` 中註冊 `measures` 命名空間

#### Step 3：更新頁面元件（2 檔）

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/pages/MeasuresPage.tsx` |
| 修改 | `frontend/src/pages/MeasureDashboardPage.tsx` |

- 加入 `useTranslation('measures')` hook
- 替換頁面標題、副標題、Tab 標籤等硬編碼字串

#### Step 4：更新 Measure 元件（26 檔）

| 動作 | 檔案 |
|------|------|
| 修改 | `components/measure/` 下全部 26 個元件 |

重構模式：
- 模組層級常數（如 `POPULATION_LABELS`、`STATUS_CONFIG`、`WORKFLOW_STEPS`、`AGGREGATE_METHODS`、`PRESET_CRONS`）改為儲存翻譯鍵，在元件內透過 `t()` 解析顯示文字
- 變數名避免遮蔽 `t`（例如 `.map((t) => ...)` → `.map((refType) => ...)`）
- 跨命名空間引用：`t('actions.cancel', { ns: 'common' })`
- 複數支援：使用 i18next `count` 參數（如 `t('key', { count: n })`）

#### Step 5：更新 Test Case Builder 元件（19 檔）

| 動作 | 檔案 |
|------|------|
| 修改 | `components/testcase-builder/constants.tsx` — 移除 `STRINGS` 常數 |
| 修改 | 其餘 18 個 testcase-builder 元件 |

- `constants.tsx` 的 `STRINGS` 物件完全移除，保留 `RESOURCE_ICONS`、`FHIR_UCUM_SYSTEM`、`FHIR_BUNDLE_TYPE`
- 所有 `STRINGS.xxx` 引用改為 `t('testCaseBuilder.xxx')`

### 影響統計

| 類別 | 數量 |
|------|------|
| 新增檔案 | 2（locale JSON） |
| 修改檔案 | 47 |
| 翻譯鍵數 | ~400 |
| 新增行數 | ~867 |
| 刪除行數 | ~719 |

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功
- `grep` 確認 measure / testcase-builder 元件中無殘留硬編碼英文字串
- 語言切換（en ↔ zh-TW）正常運作

---

## PAT-001 — UCUM 單位下拉選單統一

- **日期**: 2026-02-19
- **範圍**: 跨模組 UX 改善
- **分類**: UX 一致性 / 輸入正確性

### 問題描述

平台中只有 Authoring 模組的 `UcumUnitField.tsx` 有完整的 UCUM 單位搜尋下拉選單（65 個單位、11 分類、`freeSolo` 支持自訂輸入）。其餘三個模組的單位輸入都是純文字 `TextField`，使用者需要手動輸入 `mm[Hg]`、`kg/m2` 等 UCUM 代碼，容易出錯。

**受影響位置：**
1. 虛擬病人建構器 — `QuantityField.tsx`（unit / code 都是純文字）
2. CQL Builder — `ParametersSection.tsx`（Quantity 類型的 default value 是純文字）
3. eQCM — `PopulationCriteriaTab.tsx`（Scoring Unit 是純文字）

### 修改內容

#### Step 1：搬移 UcumUnitField 到 common + 擴充單位清單

| 動作 | 檔案 |
|------|------|
| 新增 | `frontend/src/components/common/UcumUnitField.tsx` |
| 修改 | `frontend/src/components/authoring/fields/UcumUnitField.tsx` → re-export |

- 從 Authoring 模組複製 `UcumUnitField` 到 `common/`，成為平台共用元件
- 新增 2 個常用單位：`kg/m2`（BMI，分類 Ratio）、`{score}`（臨床評分量表，分類 Other）
- 新增 `size`、`fullWidth`、`helperText` props 以支援不同模組的排版需求
- 匯出 `UCUM_UNITS` 陣列供其他元件使用（如 QuantityField 需要反查）
- Authoring 原檔改為 re-export，既有引用零影響

#### Step 2：更新 QuantityField（虛擬病人建構器）

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/testcase-builder/QuantityField.tsx` |

- `unit` 欄位從純文字 `TextField` → `UcumUnitField` 搜尋下拉選單
- 選擇已知單位時，自動填入 `system: 'http://unitsofmeasure.org'` 和 `code: UCUM code`
- `system` / `code` 欄位保留但改為可收合的進階區（`Collapse` + `Link` 切換），減少視覺雜亂
- 自由輸入仍可正常運作（`freeSolo`）

#### Step 3：更新 ParametersSection（CQL Builder）

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/builder/ParametersSection.tsx` |

- 當 `paramType` 為 `Quantity` 或 `Interval<Quantity>` 時，default value 區拆成兩個欄位：數值 `TextField` + `UcumUnitField`
- 自動組合成 CQL Quantity literal：`42 'kg'`
- 編輯既有參數時，自動解析 `70 'kg'` 格式回填到兩個欄位
- 非 Quantity 類型時保持原樣

#### Step 4：更新 PopulationCriteriaTab（eQCM Scoring Unit）

| 動作 | 檔案 |
|------|------|
| 修改 | `frontend/src/components/measure/PopulationCriteriaTab.tsx` |

- Scoring Unit 從 `<TextField>` → `<UcumUnitField>`
- 保留 `freeSolo` 支持自訂值（如 `per 1000`）
- 傳入 `fullWidth` 和 `helperText` 保持原有排版

### 驗證

- `npx tsc --noEmit` — 無型別錯誤
- `npm run build` — 建置成功
- Authoring 模組的 ModifierCard / Parameters 中 UcumUnitField 不受影響（re-export 透明）

---

## 詳細記錄 — 🐛 Bugfix

## BUG-082 — 元素模板繼承未解析 + React Hooks 順序違規 — 缺少元素名稱 + CDS Hooks 頁面崩潰

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | CDS Authoring（前後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 / 框架違規 |
| **影響範圍** | 所有使用 `extends` 繼承的元素模板（如 Encounter）缺少 `element_name` 欄位；CDS Hooks 頁面因 React Hooks 規則違反而崩潰 |
| **Commit** | [`8e8d4c8`](../../commit/8e8d4c8) |

### 問題描述

1. **模板繼承未解析**：`TemplateService` 讀取 `formTemplates.json` 時解析了 `"extends": "Base"` 欄位，但從未實際將 Base 模板的欄位（`element_name`、`comment`）合併到子模板中。導致 Encounter 等元素僅有自身欄位（如 `encounter`），使用者看到「缺少元素名稱」驗證錯誤但找不到輸入框。

2. **React Hooks 順序違規**：`ResourceForm.tsx` 中 `useMemo` 放在條件式 early return 之後，違反 React Hooks 必須在每次渲染以相同順序呼叫的規則。當 `activeEntry` 或 `isLoading` 狀態改變時觸發 React error #310「Rendered more hooks than during the previous render」，導致 CDS Hooks 頁面 ErrorBoundary 崩潰。

### 根因分析

1. `TemplateService.parseTemplate()` 正確讀取 `extends` 欄位至 `FormTemplate.extendsTemplate`，但 `init()` 方法中從未呼叫任何繼承解析邏輯。

2. `ResourceForm.tsx` 中 `useMemo`（計算 `visibleOptional`/`hiddenOptional`）位於 `if (!activeEntry) return ...` 和 `if (isLoading) return ...` 之後。當元件從有 `activeEntry` 狀態切換至無 `activeEntry` 狀態時，Hooks 數量改變。

### 修正方式

1. **TemplateService**：新增 `resolveInheritance()` 方法，在載入分類後、建立 `knownElementTypes` 前呼叫。建立 `id → FormTemplate` lookup map，遍歷所有模板，將 parent 欄位前置合併到 child 中（跳過已存在的欄位避免重複）。

2. **ResourceForm**：將 `useMemo` 移到所有 early return 之前，內部加入 `if (!activeEntry)` null guard 回傳空陣列。

### 修改檔案

| 檔案 | 變更 |
|------|------|
| `backend/.../authoring/TemplateService.java` | 新增 `resolveInheritance()` 方法，`init()` 中呼叫 |
| `frontend/.../testcase-builder/ResourceForm.tsx` | `useMemo` 移至 early return 前，加 null guard |

---

## BUG-081 — CQL 批次執行崩潰 + FHIR Token 搜尋管道符號轉義 — FHIRHelpers 歧義 + 查詢回傳 0 筆

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | CQL 執行引擎（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 / API 誤用 |
| **影響範圍** | 所有 CQL 執行（CDS Hooks 調用、手動執行）；當 FHIRHelpers 遇到 null 值時整個執行失敗；FHIR fallback 搜尋永遠回傳 0 筆結果 |
| **Commit** | [`649ac67`](../../commit/649ac67) |

### 問題描述

1. **CQL 批次執行崩潰**：CQL 引擎的 `FunctionRefEvaluator` 在 `null` 值傳入 `FHIRHelpers.ToString()` 時無法判斷應呼叫哪個多載版本（`ToString(FHIR.string)`、`ToString(FHIR.code)` 等），拋出 `CqlException: Ambiguous call to operator 'ToString(null)'`。在正常模式下，`engine.evaluate()` 一次評估所有表達式，任一表達式失敗會導致整個執行崩潰。

2. **FHIR Token 搜尋管道符號轉義**：`FhirDataProviderService` 的 fallback 搜尋使用 `TokenClientParam.exactly().code("http://loinc.org|29463-7")`，HAPI FHIR client 將 `|` 視為 token 搜尋的保留字元並轉義為 `\|`，實際發出的查詢為 `code=http://loinc.org\|29463-7`，FHIR server 找不到 system `http://loinc.org\` 因此回傳 0 筆結果。

### 根因分析

1. `CqlExecutionService.doExecute()` 的 debug 模式逐一評估表達式並以 try-catch 捕捉錯誤，但 normal 模式批次呼叫 `engine.evaluate()` 後用外層 catch 直接拋出 `CqlExecutionException`，沒有 per-expression 容錯。

2. `buildCodeFilter()` 組合出 `system|code` 字串，`trySearch()` 將整串傳入 `.exactly().code()`。HAPI FHIR 的 `.code()` 方法設計為僅接受 code 值（非 system|code），因此自動轉義 `|`。正確做法是使用 `.systemAndCode(system, code)` 分開傳入。

### 修正方式

1. **CqlExecutionService**：normal 模式下，先嘗試批次 `engine.evaluate()`；若失敗，改以 per-expression 逐一評估（與 debug 模式相同策略），僅將失敗的表達式標記為 Error，其餘正常回傳。

2. **FhirDataProviderService**：重構 `buildCodeFilter()` → `collectCodes()` 回傳 Code 物件列表；`trySearch()` 使用 `.systemAndCode(system, code)` 正確建構 FHIR token 搜尋參數。多碼時用 `whereMap()` 避免 HAPI 轉義。

### 修改檔案

| 檔案 | 變更 |
|------|------|
| `backend/.../cql/CqlExecutionService.java` | 批次失敗 → per-expression 回退邏輯 + circuit breaker 處理 |
| `backend/.../fhir/FhirDataProviderService.java` | `collectCodes()` + `systemAndCode()` 修復 + circuit breaker 包裝 |
| `backend/.../cql/CircuitBreakerRetrieveProvider.java` | 新增 FHIR retrieve circuit breaker 包裝器 |
| `backend/.../fhir/FhirDataProviderServiceTest.java` | 更新測試適配新建構參數 |

---

## BUG-080 — 多分頁同時編輯 Artifact 導致靜默資料覆蓋 — JPA @Version 樂觀鎖 + 前端衝突對話框

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | CDS Authoring（前後端） |
| **嚴重程度** | High |
| **根因類型** | 併發/效能問題 |
| **影響範圍** | `CdsArtifactEntity.java`、`ArtifactRequest.java`、`ArtifactResponse.java`、`ArtifactService.java`、`GlobalExceptionHandler.java`、`ArtifactWorkspace.tsx`、`authoring.ts`、`en/authoring.json`、`zh-TW/authoring.json`、`V38__cds_artifact_lock_version.sql` |
| **Commit** | [`9b46017`](../../commit/9b46017) |

### BUG 描述

同一帳號在多個瀏覽器分頁同時編輯同一個 CDS Artifact 時，`ArtifactService.update()` 執行盲目覆寫（last-write-wins），無任何衝突偵測機制。當 Tab A 與 Tab B 各自修改後依序儲存，Tab B 的儲存會靜默覆蓋 Tab A 的變更，使用者完全無感知資料遺失。

**重現步驟**：
1. 在分頁 A 開啟 Artifact，修改納入條件
2. 在分頁 B 開啟相同 Artifact，修改建議文字
3. 分頁 A 儲存成功
4. 分頁 B 儲存成功 — 分頁 A 的修改被靜默覆蓋

### 修正方式

**JPA @Version 樂觀鎖 + 前端衝突對話框**：

1. **DB Migration（V38）**：新增 `lock_version BIGINT NOT NULL DEFAULT 0` 欄位
2. **Entity（CdsArtifactEntity）**：加入 `@Version @Column("lock_version") Long lockVersion`，Hibernate 自動在 UPDATE 語句加入 `WHERE lock_version = ?` 條件
3. **DTO 傳遞**：`ArtifactRequest` 與 `ArtifactResponse` 新增 `lockVersion` 欄位，前後端完整 round-trip
4. **Service（ArtifactService.update）**：將 client 傳入的 `lockVersion` 設定至 entity，若版本過舊則 UPDATE 命中 0 行 → `ObjectOptimisticLockingFailureException`
5. **Exception Handler**：新增 `ObjectOptimisticLockingFailureException` → HTTP 409 Conflict 回應
6. **前端（ArtifactWorkspace）**：`handleSave` 攔截 409 → 顯示衝突對話框，提供「重新載入」（refetch 最新版本）與「繼續編輯」（關閉對話框保留本地修改）兩個選項
7. **i18n**：新增 `workspace.conflict.*` 翻譯鍵（英文 + 繁體中文）

### 測試驗證

- 單分頁正常儲存：`lockVersion` 透明 round-trip，行為不變
- 多分頁衝突：Tab A 儲存後 Tab B 儲存 → Tab B 收到 409 → 顯示衝突對話框
- 「重新載入」按鈕：重新取得最新資料，`lockVersion` 更新
- 「繼續編輯」按鈕：關閉對話框，使用者可手動合併後重試儲存
- TypeScript 編譯通過（`npx tsc --noEmit`）

---

## BUG-079 — Rate Limiting 分層強化 — 端點分級 IP 限流 + 使用者限流 + 大型 Payload 加權

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（DoS / 資源耗盡） |
| **影響範圍** | `RateLimitFilter.java`、`UserRateLimitFilter.java`、`RateLimitProperties.java`、`SecurityConfig.java`、`application.yml` |
| **Commit** | [`6b72fec`](../../commit/6b72fec) |

### BUG 描述

`POST /api/cql/translate` 與 `/api/cql/execute` 為 CPU 密集型端點，但僅受全域 60 RPM/IP 速率限制保護，所有端點共用同一配額。攻擊者可利用以下方式進行 DoS：

1. **配額佔用**：連續發送 60 個 translate 請求即耗盡全部配額，合法的輕量 API 呼叫（如 library 列表）同樣被阻擋
2. **多 IP 繞過**：IP 限流無法防禦分散式攻擊，認證後的使用者無獨立限額
3. **大型 Payload 放大**：200KB+ 的 CQL 翻譯請求與 1KB 請求消耗相同配額，但 CPU 成本差距數十倍
4. **Token refill 時間漂移**：`refill()` 方法在 `tokensToAdd == 0` 時仍更新 `lastRefillTime`，導致 token 補充速率逐漸偏移

### 修正方式

**雙層分級限流架構**：

1. **Layer 1 — IP 分級限流（RateLimitFilter 重寫）**
   - 5 級端點分類：TRANSLATE(20 RPM)、EXECUTE(10 RPM)、FIX_SUGGESTION(5 RPM)、LIBRARY_READ(120 RPM)、DEFAULT(60 RPM)
   - Bucket key 改為 `IP:tier`，各級獨立計數互不干擾
   - Payload 加權：TRANSLATE 端點依 Content-Length 消耗 1-5 tokens（>10KB=2, >50KB=3, >200KB=5）
   - 修復 token refill 時間漂移：僅按實際補充量推進 `lastRefillTime`
   - 429 回應加入 `Retry-After` header
   - Micrometer `rate_limit_exceeded` 計數器（tag: tier, layer=ip）

2. **Layer 2 — 使用者限流（UserRateLimitFilter 新增）**
   - 位於 JwtAuthenticationFilter 之後，使用 `authentication.getName()` 為 bucket key
   - 獨立 RPM 配額：TRANSLATE(15)、EXECUTE(8)、FIX_SUGGESTION(3)、DEFAULT(40)
   - 未認證/匿名請求自動放行（已由 IP 層保護）
   - `X-UserRateLimit-Limit` / `X-UserRateLimit-Remaining` / `Retry-After` headers
   - Micrometer 計數器（tag: layer=user）

3. **外部化配置（RateLimitProperties）**
   - 所有 RPM 值透過 `@ConfigurationProperties(prefix = "rate-limit")` 管理
   - 支援環境變數覆蓋（如 `RATE_LIMIT_TRANSLATE_RPM`）

**Filter chain 順序**：RateLimitFilter → XssFilter → JwtAuthenticationFilter → UserRateLimitFilter → AuditFilter

### 測試驗證

- `RateLimitFilterTest`（13 tests）：分級限額驗證（translate/execute/default/library-read）、Payload 加權消耗、Bucket 隔離、Retry-After header、停用/OPTIONS 跳過、metrics 計數
- `UserRateLimitFilterTest`（7 tests）：未認證跳過、匿名使用者跳過、per-user translate 限額、使用者隔離、header 驗證、停用跳過、metrics 計數
- 全部 20 個測試通過

---

## BUG-078 — CDS Card XSS 3 層防護 — 前端安全渲染 + 後端 HTML 跳脫 + 反序列化器強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | 安全性（前後端） |
| **嚴重程度** | Critical |
| **根因類型** | 安全漏洞（XSS） |
| **影響範圍** | `SandboxPanel.tsx`、`CdsValueFormatter.java`、`CdsResourceFormatter.java`、`CqlTupleCardStrategy.java`、`PlanDefinitionCardStrategy.java`、`XssStringDeserializer.java`、`NoXssValidator.java` |
| **Commit** | [`d7fc37f`](../../commit/d7fc37f) |

### BUG 描述

CDS card 內容（summary、detail、source label）將未經消毒的 FHIR/CQL 資料直接傳遞至前端，`SandboxPanel.tsx` 透過 `dangerouslySetInnerHTML` 渲染 `card.detail`，造成儲存型 XSS 漏洞。此外，`XssStringDeserializer` 使用的 regex 黑名單可被 `<svg>`、`<math>`、未閉合 `<script`、`data:text/html` 等向量繞過。

**攻擊路徑**：
1. 攻擊者在 FHIR Resource 欄位（如 Observation.code.text、Condition 狀態碼）中注入 `<svg onload=alert(document.cookie)>`
2. CQL 執行引擎讀取該資源，CDS card 格式化器將惡意內容原封不動寫入 card detail
3. 前端 `dangerouslySetInnerHTML` 直接渲染為 HTML，觸發 XSS
4. `XssStringDeserializer` 的 `<script>(.*?)</script>` 模式無法攔截 `<svg>`、`<math>`、未閉合 `<script src=evil>`

### 修正方式

**3 層防禦（Defense-in-depth）**：

1. **Layer 1 — 前端安全渲染（Critical）**
   - 移除 `dangerouslySetInnerHTML`，改用 `text.split(/\*\*(.+?)\*\*/g)` 將 markdown bold 拆分為交替的純文字與 `<strong>` React 元素
   - React 自動跳脫所有文字內容，從根本杜絕 XSS

2. **Layer 2 — 後端 HTML 跳脫（Defense-in-depth）**
   - `CdsValueFormatter`：新增 `esc()` 工具方法（`HtmlUtils.htmlEscape`），對 String、CodeableConcept、Coding、Quantity unit、PrimitiveType 等所有 FHIR/CQL 衍生值進行跳脫
   - `CdsResourceFormatter`：對 `formatDetail()`、`formatReference()`、`formatAllCodings()` 中所有 FHIR 欄位值（display、text、code、unit、id）進行跳脫
   - `CqlTupleCardStrategy`：對 Tuple 衍生的 `summary`、`detail`、`sourceLabel` 及 `errorMessage` 進行跳脫
   - `PlanDefinitionCardStrategy`：對 `action.getTitle()` 及 `action.getDescription()` 進行跳脫

3. **Layer 3 — 反序列化器模式強化**
   - `XssStringDeserializer` + `NoXssValidator`：新增 `<svg>`、`<math>`、`<object>`、`<embed>`、`<base>`、`<form>` 標籤攔截
   - 將 `<script>(.*?)</script>` 改為 `<script[^>]*>` + `</script>` 以攔截未閉合/帶屬性的 script 標籤
   - 新增 `data:text/html` 及 `vbscript:` URI 向量攔截

### 測試驗證

- `CdsValueFormatterTest`：新增 8 個 XSS 跳脫驗證測試（String、SVG、CodeableConcept、Coding、Quantity unit、list items）
- `CdsResourceFormatterTest`：新增 6 個 XSS 跳脫驗證測試（code text、coding display、quantity unit、resource ID、reference ID、condition status）
- `XssStringDeserializerTest`：新建 17 個測試覆蓋所有新增模式（svg、math、object、embed、base、form、data:text/html、vbscript、未閉合 script 等）
- 前端：`SandboxPanel` 以 React 元素安全渲染 bold 文字，無 `dangerouslySetInnerHTML`

---

## BUG-077 — 停用使用者 API Key 未失效 — 認證繞過漏洞 + 雙重防護修復

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 安全漏洞（認證繞過） |
| **影響範圍** | `AdminController.java`、`UserApiKeyService.java`、`UserApiKeyRepository.java`、`JwtAuthenticationFilter.java` |

### BUG 描述

當 Admin 透過 `PUT /api/admin/users/{id}/enabled` 停用使用者時，該使用者先前建立的所有 API Key 仍然有效，可繼續存取 `/cds-services/u/` 等受保護端點。

**攻擊路徑**：
1. 使用者 A 產生 API Key（`POST /api/user/api-keys`）
2. Admin 停用使用者 A（`PUT /api/admin/users/{id}/enabled { "enabled": false }`）
3. 使用者 A 仍可使用 API Key 透過 `Authorization: Bearer cql_...` 存取 CDS 服務
4. `JwtAuthenticationFilter` 將 API Key 驗證委派給 `UserApiKeyService.validateApiKey()`，該方法只檢查 `key.active = true`，**完全不檢查 `user.enabled`**

### 根因分析

- `AdminController.updateUserEnabled()` 只設定 `user.enabled = false`，不觸碰 `user_api_keys` 表
- `UserApiKeyService.validateApiKey()` 透過 `findByApiKeyAndActiveTrue()` 只檢查 key 本身的 `active` 欄位，無 JOIN 查詢使用者狀態
- `UserApiKeyEntity` 以 `username` 字串關聯，無 JPA `@ManyToOne` 外鍵約束

### 修正方式

**雙重防護（A + B）**：

1. **Fix A — 驗證時檢查使用者狀態（防禦縱深）**
   - `UserApiKeyService.validateApiKey()` 在確認 key 有效後，額外查詢 `UserRepository.findByUsername()` 檢查 `user.enabled`
   - 若使用者已停用或已刪除，回傳 `Optional.empty()` 拒絕認證

2. **Fix B — 停用時立即失效所有 Keys**
   - `UserApiKeyRepository` 新增 `deactivateAllByUsername()` JPQL 批次更新
   - `UserApiKeyService` 新增 `deactivateAllKeys(username)` 方法
   - `AdminController.updateUserEnabled(false)` 時呼叫 `deactivateAllKeys()` 立即停用所有 API Keys

### 測試驗證

- `UserApiKeyServiceTest`: 新增 4 個測試（enabled user 通過、disabled user 拒絕、deleted user 拒絕、批次停用）+ 更新 1 個既有測試
- `AdminControllerTest`: 新增 2 個測試（disable 觸發 key 停用驗證、enable 不觸發驗證）
- 全部 28 個相關測試通過（12 + 11 + 5）

---

## BUG-076 — AuditFilter $export 未標記 PHI 存取 + 欄位溢位導致稽核寫入失敗

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-04 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（稽核遺漏） |
| **影響範圍** | `AuditFilter.java` |

### BUG 描述

兩個獨立的稽核缺陷：

1. **`$export` Bulk Data 未標記為 PHI 存取**：`FHIR_RESOURCE_PATTERN` regex `\w+` 不匹配 `$` 字元，導致 `/api/fhir/$export` 的 `resourceType` 錯誤解析為 `fhir`，`phiAccess = false`，匯出範圍（`_type`、`_since`）未記錄。Bulk Data Export 是最高風險 PHI 操作，卻完全不在 PHI 稽核報表中。

2. **`path`/`resourceId`/`ipAddress` 未截斷**：當 URI > 500 字元、resource ID > 100 字元、或偽造 `X-Forwarded-For` > 45 字元時，JPA 寫入觸發 `DataTruncation` 異常，被 catch 吞掉後整筆稽核記錄遺失。

### 根因分析

- `\w+` 只匹配 `[a-zA-Z0-9_]`，`$export` 的 `$` 不在範圍內
- 無針對 `$export` 路徑的特殊處理
- `path`、`resourceId`、`ipAddress` 直接傳入 entity builder，未呼叫 `truncate()`

### 修正方式

1. **新增 `$export` 偵測**：`path.contains("/fhir/$export")` → `phiAccess=true`、`resourceType="BulkExport"`、`action="EXPORT"`、記錄完整 `queryParameters`
2. **欄位截斷對齊 DB schema**：`path` → `truncate(500)`、`resourceId` → `truncate(100)`、`ipAddress` → `truncate(45)`

### 測試驗證

- 既有 16 個稽核相關測試全部通過
- `$export` 的稽核記錄現在包含：`phiAccess=true`、`resourceType=BulkExport`、`action=EXPORT`、`queryParameters=fhirServer=...&exportType=system&_type=Patient,Observation`

---

## BUG-075 — CqlArtifactBuilder 測試補強 — LookBack / AgeRange / 空排除 / 括號驗證 + Windows 換行修復

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Low |
| **根因類型** | 測試遺漏 |
| **影響範圍** | `CqlArtifactBuilderTest.java` |

### BUG 描述

`CqlArtifactBuilderTest` 僅有 4 個測試，缺少對以下場景的覆蓋：

1. **LookBack 修飾符**：未驗證 `C3F.ObservationLookBack(expr, N unit)` 輸出格式
2. **LookBack 空值降級**：未驗證 value 為空時的 fallback 行為
3. **AgeRange 單邊界**：未驗證只設下限時不會產生 null reference
4. **AgeRange 雙邊界括號**：未驗證 #074 修正的括號行為
5. **空排除樹 Windows 相容性**：`buildCql_emptyExclusion_shouldProduceFalseNotNull` 使用 `\n` 比對，但 `String.format("%n")` 在 Windows 產生 `\r\n`，導致 CI/本地測試失敗

### 修正方式

1. 新增 4 個測試：
   - `buildCql_lookBackModifier_shouldGenerateC3FLookBackWith6Months`
   - `buildCql_lookBackModifier_emptyValue_shouldOmitQuantity`
   - `buildCql_ageRangeOnlyMin_shouldNotProduceNullReference`
   - `buildCql_ageRangeBothBounds_shouldWrapInParentheses`
2. 修復 `buildCql_emptyExclusion_shouldProduceFalseNotNull`：`contains("\n")` → `containsPattern("\\R")` 以相容所有平台換行符

### 測試驗證

- 全部 8 個測試通過（Windows + Maven Surefire）

---

## BUG-074 — CQL 產生器 AgeRange / ValueComparison 複合條件缺少括號

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Low |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `CqlArtifactBuilder.java` |

### BUG 描述

`buildAgeRangeExpression()` 和 `ValueComparisonNumber` / `ValueComparisonObservation` modifier 在同時具有上下限條件時，使用 `String.join(" and ", conditions)` 串接，未加括號。當此複合表達式作為 OR 群組的子節點時，生成的 CQL 可讀性差：

```cql
// 修正前
AgeInYears() >= 18 and AgeInYears() <= 65 or SomeCondition

// 修正後
(AgeInYears() >= 18 and AgeInYears() <= 65) or SomeCondition
```

CQL 運算子優先級 `and` > `or`，因此語義上兩者等價，**不影響正確性**，但缺少括號會降低可讀性且容易在人工審查時產生疑慮。

### 根因分析

- `buildAgeRangeExpression()` 直接 `String.join(" and ", conditions)` 回傳
- `ValueComparisonNumber` / `ValueComparisonObservation` 同理
- `buildConjunctionExpression()` 只對 `conjunction=true` 的子節點加括號，葉節點不處理

### 修正方式

1. **`buildAgeRangeExpression()`**：當 `conditions.size() > 1` 時以 `()` 包裹
2. **`ValueComparisonNumber` / `ValueComparisonObservation`**：同上處理

### 測試驗證

- 既有 `CqlArtifactBuilderTest` 通過，確認正常路徑不受影響
- 單一條件不加括號，雙條件加括號

---

## BUG-073 — verifyArtifactOwnership 使用 IllegalArgumentException(400) 而非 ResourceNotFoundException(404)

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Low |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `AuthoringController.java`、`CqlGenerationService.java` |

### BUG 描述

`verifyArtifactOwnership()`、`deployCdsService()`、`saveAsLibrary()` 以及 `CqlGenerationService.generateCql()` 中，artifact 不存在時拋出 `IllegalArgumentException("Artifact not found: " + id)`。`GlobalExceptionHandler` 將 `IllegalArgumentException` 映射為 400 Bad Request，但語義應為 404 Not Found。

### 根因分析

- 開發時直接使用 `IllegalArgumentException` 作為通用拋出異常
- 已有 `ResourceNotFoundException` 但未被使用

### 修正方式

- 4 處 `IllegalArgumentException("Artifact not found")` → `ResourceNotFoundException("Artifact", id)`
  - `AuthoringController.verifyArtifactOwnership()`
  - `AuthoringController.deployCdsService()`
  - `AuthoringController.saveAsLibrary()`
  - `CqlGenerationService.doBuildCql()`

### 測試驗證

- `CqlGenerationServiceTest.generateCql_notFound_shouldThrow` 更新為期望 `ResourceNotFoundException`

---

## BUG-072 — CdsArtifactEntity 反序列化失敗被靜默吞掉，無 log

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `CdsArtifactEntity.java` |

### BUG 描述

`CdsArtifactEntity` 的 4 個序列化/反序列化方法（`serializeList`、`deserializeList`、`serializeMap`、`deserializeMap`）在 `JsonProcessingException` 時直接回傳預設值（空 list 或 null），無任何日誌輸出。當資料庫中存在損壞的 JSON 時，entity 靜默載入空資料，下游行為異常但無法從 log 追溯根因。

### 根因分析

- catch block 中直接 `return` 預設值，未呼叫任何 logging
- Entity 類別原本缺少 `@Slf4j` 註解

### 修正方式

1. 加 `@Slf4j` 註解
2. 4 個 catch block 加 `log.warn("Failed to (de)serialize ... for entity id={}: {}", id, e.getMessage())`

### 測試驗證

- 既有測試通過，確認正常路徑不受影響

---

## BUG-071 — CQL 產生器靜默降級 — 未知 element type 或 modifier 被忽略，使用者無感知

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `CqlArtifactBuilder.java`、`CqlBuildResult.java`（新增）、`CqlGenerationService.java`、`AuthoringController.java`、`AuthoringControllerTest.java` |

### BUG 描述

CQL 產生器在遇到不認識的 element type 或 modifier template 時：
1. 未知 element type → 靜默替換為 `true /* elementName */`
2. 未知 modifier template → 靜默忽略（原樣回傳表達式）

使用者完全不知道產出的 CQL 包含 placeholder，可能部署語義不正確的 CDS 規則。

### 根因分析

- `buildExpression()` 的 default case 直接回傳 `true` placeholder
- `applyModifier()` 的 default 路徑僅有 `log.warn` 但未通知呼叫者
- 回傳型別為 `String`，無法攜帶結構性警告

### 修正方式

1. **`CqlBuildResult` record** 攜帶 `List<String> warnings`
2. **`BuildContext.warn()`** 在兩處 fallback 收集警告：
   - 未知 element type → `"Unknown element type '...' for element '...'; defaulting to 'true'"`
   - 未知 modifier template → `"Unknown modifier template '...' (id='...'); modifier skipped"`
3. **`CqlGenerationService.generateCqlWithWarnings()`** 回傳 `CqlBuildResult`
4. **`AuthoringController.generateCql()` 端點** 回傳 `{ cql, warnings? }`，僅有 warnings 時才包含

### 測試驗證

- `CqlArtifactBuilderTest.buildCql_unknownElementType_shouldProduceWarning`
- `generateCqlWithWarnings_shouldReturnWarnings`
- `generateCql_withWarnings_shouldIncludeWarningsInResponse`

---

## BUG-070 — CqlGenerationService.generateCql() 無 try-catch — 畸形 JSON 導致 generic 500

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `CqlGenerationService.java`、`CqlGenerationException.java`（新增）、`GlobalExceptionHandler.java`、`CqlGenerationServiceTest.java` |

### BUG 描述

`CqlGenerationService.generateCql()` 直接呼叫 `cqlBuilder.buildCql()` 無任何 try-catch。當使用者儲存的 artifact 含有畸形 expression tree JSON 時，builder 內部的 `ClassCastException` 或 `NullPointerException` 直接穿透至 Spring 的 generic exception handler，回傳 500 Internal Server Error，沒有任何有用的錯誤訊息。

### 根因分析

- `buildCql()` 內部大量 unchecked cast（`(List<Map<String, Object>>)` 等），畸形資料會觸發 `ClassCastException`
- 缺少欄位時觸發 `NullPointerException`
- Service 層未包裝這些非預期異常

### 修正方式

1. **新增 `CqlGenerationException`**：攜帶 `List<String> details`，遵循 `CqlTranslationException` 模式
2. **抽出 `doBuildCql()` 方法**：try-catch 包裝 `buildCql()` 呼叫，非 `CqlGenerationException` 一律包裝成 `CqlGenerationException`
3. **`GlobalExceptionHandler` 新增 handler**：`CqlGenerationException` → 422 UNPROCESSABLE_ENTITY

### 測試驗證

- `generateCql_builderThrowsClassCast_shouldWrapInCqlGenerationException`
- `generateCql_builderThrowsNPE_shouldWrapInCqlGenerationException`

---

## BUG-069 — CqlArtifactBuilder Singleton 可變 instance field — 並行請求互相覆蓋

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 併發/效能問題 |
| **影響範圍** | `CqlArtifactBuilder.java`、`CqlBuildResult.java`（新增）、`CqlArtifactBuilderTest.java`（新增） |

### BUG 描述

`CqlArtifactBuilder` 是 Spring `@Component`（Singleton），但持有可變 instance field `currentBaseElements`，在 `buildCql()` 入口處被賦值。當多個請求並行呼叫時，後到的請求會覆蓋前一個請求的 `currentBaseElements`，導致 `findBaseElementName()` 查找到錯誤的 base element，產出語義錯誤的 CQL。

### 根因分析

- `private List<Map<String, Object>> currentBaseElements` 是 instance field，Singleton bean 共享同一個實例
- `buildCql()` 開頭直接 `this.currentBaseElements = baseElements`，無同步保護
- `findBaseElementName()` 讀取 `this.currentBaseElements` 時可能已被另一執行緒覆蓋

### 修正方式

1. **移除 instance field** `currentBaseElements`
2. **新增 `static class BuildContext`**：包含 `baseElements` 和 `warnings` 收集器
3. 在 `buildCql()` 中建立本地 `BuildContext`，作為參數傳遞給 4 個 private 方法：`buildConjunctionExpression`、`buildExpression`、`applyModifier`、`findBaseElementName`
4. **回傳型別改為 `CqlBuildResult` record**（`String cql` + `List<String> warnings`）

### 測試驗證

- `CqlArtifactBuilderTest.currentBaseElements_instanceField_shouldNotExist` — 反射斷言確認 instance field 已移除
- `CqlArtifactBuilderTest.buildCql_emptyTree_shouldProduceValidLibraryHeader` — 空 tree 產出合法 CQL
- Docker build 零錯誤，既有測試全部通過

---

## BUG-068 — AuditFilter PHI 稽核修復 — FHIR 三層路徑解析、顯式 phiAccess 旗標、查詢參數擷取

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（稽核遺漏） |
| **影響範圍** | `AuditFilter.java`、`AuditLogEntity.java`、`AuditLogEntry.java`、`AuditLogRepository.java`、`AuditService.java`、`V36__audit_phi_access_columns.sql`、`frontend/types/index.ts` |

### BUG 描述

PHI（受保護健康資訊）存取稽核存在多項缺陷：

1. **FHIR 路徑解析錯誤**：`/api/fhir/Patient/123` 被正則 `/api/(\w+)(?:/([^/]+))?` 解析為 `resourceType=fhir, resourceId=Patient`，完全丟失真正的資源類型和 ID
2. **PHI 偵測使用脆弱的 LIKE 比對**：`LOWER(a.path) LIKE '%patient%'` 會誤判（如 `/api/patient-settings`）且遺漏其他 PHI 資源（Observation、Condition 等）
3. **搜尋參數未記錄**：無法事後審查「查了哪些條件」
4. **無顯式 PHI 旗標**：依賴 query-time 字串比對，效能差且不精確

### 根因分析

- 原始正則僅支援二層路徑 `/api/{module}/{id}`，FHIR 端點為三層 `/api/fhir/{resourceType}/{id}`
- PHI 偵測沒有明確的資源類型白名單，僅做模糊字串比對
- `AuditLogEntity` 缺少 `phiAccess` 和 `queryParameters` 欄位

### 修正方式

1. **新增 Flyway V36 migration**：
   - `phi_access BOOLEAN NOT NULL DEFAULT FALSE` + `query_parameters VARCHAR(2000)`
   - 部分索引 `idx_audit_phi_access ON audit_log(phi_access) WHERE phi_access = TRUE`

2. **重寫 `AuditFilter.java` 路徑解析**：
   - 新增 `FHIR_RESOURCE_PATTERN = /api/fhir/(\w+)(?:/([^/]+))?` 優先比對三層路徑
   - 定義 `PHI_RESOURCE_TYPES` 白名單（18 種 FHIR 臨床資源）
   - PHI 存取時擷取 `request.getQueryString()` 記錄查詢參數
   - 特殊處理 `Patient/$search-by-demographics`

3. **更新 Repository 查詢**：
   - `LIKE '%patient%'` → `a.phiAccess = true`（精確、高效、使用部分索引）

4. **更新 DTO + Service 映射**：
   - `AuditLogEntry` / `AuditService.toEntry()` / 前端 TypeScript 介面同步新增欄位

### 測試驗證

- Docker build 編譯通過
- 前端 TypeScript 型別檢查通過

---

## BUG-067 — CDS Feedback 儲存型 XSS 修復 — @NoXss 驗證 + HtmlUtils.htmlEscape 雙層防護

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（XSS） |
| **影響範圍** | `CdsFeedbackRequest.java`、`CdsHooksService.java`、`CdsFeedbackTest.java` |

### BUG 描述

CDS Hooks 回饋介面 (`POST /cds-services/{id}/feedback`) 的自由文字欄位未做 HTML Sanitization。攻擊者可在 `card`、`overrideReason.code`、`overrideReason.display` 中注入 `<img src=x onerror=alert(1)>` 等 XSS payload，儲存到資料庫後，當管理員在後台查看統計報表時觸發執行。

### 根因分析

- `XssStringDeserializer` 僅剝除 5 種 pattern（`script`、`javascript:`、`on\w+=`、`iframe`、`eval`），大量旁路向量可繞過：`<img>`、`<svg>`、`<details>`、`data:` URI
- `CdsFeedbackRequest` 的 `card`、`outcomeTimestamp`、`AcceptedSuggestion.id`、`OverrideReason.code/display` 均缺少 `@NoXss` 驗證
- 寫入資料庫前未做 HTML entity 編碼

### 修正方式

1. **第一層：輸入驗證**（拒絕惡意輸入）
   - `CdsFeedbackRequest.java`：所有自由文字欄位加上 `@NoXss`
   - 巢狀物件 `acceptedSuggestions`、`overrideReason` 加上 `@Valid` 確保驗證傳播

2. **第二層：輸出編碼**（縱深防禦）
   - `CdsHooksService.processFeedback()`：呼叫 `HtmlUtils.htmlEscape()` 對 `card`、`overrideReason.code`、`overrideReason.display` 做 HTML entity 編碼後才寫入 DB

### 測試驗證

- 新增測試 `processFeedback_withHtmlInDisplay_shouldEscapeBeforePersist`：
  - 輸入 `<img src=x onerror=alert(1)>` → 驗證輸出為 `&lt;img src=x onerror=alert(1)&gt;`
- 全部 6 個 CdsFeedbackTest 測試通過

---

## BUG-066 — CQL 執行逾時強化 — worker 中斷、AbortPolicy 防執行緒池耗盡、差異化 HTTP 狀態碼

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 安全漏洞（DoS / 資源耗盡） |
| **影響範圍** | `AsyncConfig.java`、`CqlExecutionService.java`、`MetricsConfig.java`、`GlobalExceptionHandler.java`、新增 `InterruptAwareRetrieveProvider.java` |

### BUG 描述

CQL 執行逾時機制 `future.get(120, SECONDS)` 僅讓**呼叫端**停止等待，worker 執行緒繼續無限消耗 CPU。攻擊者可提交含無窮迴圈的複雜 CQL，耗盡 20 執行緒的執行緒池；之後 `CallerRunsPolicy` 會使 CQL 直接在 Tomcat HTTP 執行緒上執行，**凍結整個 API**。

具體攻擊路徑：
1. 提交 20+ 個耗時 CQL 請求 → 填滿執行緒池 + 佇列
2. `CallerRunsPolicy` 使後續請求在 HTTP 執行緒上同步執行 → 所有 API 端點阻塞
3. 即使 `future.get()` 逾時返回，worker 執行緒仍繼續執行 → 永久佔用資源

### 根因分析

1. **`CompletableFuture.supplyAsync()` 不支援取消**：`cancel()` 不會中斷底層執行緒
2. **`CallerRunsPolicy`**：佇列滿時在呼叫者執行緒執行任務，本意防止拒絕，但在安全場景下成為 DoS 放大器
3. **CQL Engine 不檢查中斷旗標**：即使設定中斷，引擎內部不會自行停止
4. **HTTP 狀態碼無差異化**：逾時和池耗盡都回傳 `500`，無法區分

### 修正方式

1. **新增 `InterruptAwareRetrieveProvider.java`**：
   - 裝飾器模式包裝 `RetrieveProvider`
   - 每次 `retrieve()` 前檢查 `Thread.currentThread().isInterrupted()`
   - 中斷時拋出 `CqlExecutionException`，讓 worker 執行緒乾淨退出

2. **`AsyncConfig.java` — 執行器類型 + 拒絕策略**：
   - `ThreadPoolTaskExecutor` → 原生 `ThreadPoolExecutor`（回傳 `ExecutorService`）
   - `CallerRunsPolicy` → `AbortPolicy`（佇列滿時拋 `RejectedExecutionException` → 503）
   - 加入 `allowCoreThreadTimeOut(true)` 回收閒置執行緒

3. **`CqlExecutionService.java` — 4 項變更**：
   - `Executor` → `ExecutorService`
   - `CompletableFuture.supplyAsync()` → `executorService.submit()`（真正可取消的 `Future`）
   - `TimeoutException` catch 加入 `future.cancel(true)` 設定中斷旗標
   - 外層包裝 `InterruptAwareRetrieveProvider` 作為最外層 retrieve 裝飾器
   - 捕獲 `RejectedExecutionException` → 拋出含 "pool exhausted" 訊息的例外

4. **`MetricsConfig.java` — 3 個 Gauge bean 適配**：
   - 參數型別 `ThreadPoolTaskExecutor` → `ExecutorService`
   - Lambda 內轉型為 `ThreadPoolExecutor` 存取 queue/active/poolSize

5. **`GlobalExceptionHandler.java` — 差異化 HTTP 狀態碼**：
   - 訊息含 "timed out" → `504 GATEWAY_TIMEOUT`
   - 訊息含 "pool exhausted" → `503 SERVICE_UNAVAILABLE`
   - 其他 → `500 INTERNAL_SERVER_ERROR`

### 修正後執行流程

```
HTTP 請求 → CqlExecutionService.executeWithProvider()
  → executorService.submit(doExecute)        ← 真正的 Future
  → future.get(120s)
     ├─ 成功 → 回傳結果
     ├─ TimeoutException → future.cancel(true) → 設定中斷旗標
     │    └─ 下一次 retrieve() 在 InterruptAwareRetrieveProvider
     │         → 檢查 Thread.isInterrupted() → 拋出 CqlExecutionException
     │              → worker 執行緒乾淨退出
     └─ RejectedExecutionException → 503 Service Unavailable
```

### 已知限制

不含 FHIR retrieve 的純運算 CQL（臨床 CQL 中極少見）無法被中斷，因為沒有 `retrieve()` 檢查點。執行緒最終會透過 `allowCoreThreadTimeOut` 回收。

### 驗證

- `mvn compile` 零錯誤（266 source + 66 test 檔案）
- Docker 映像建置成功（`mvn package -DskipTests`）
- 所有型別匹配一致：`AsyncConfig` → `ExecutorService` → `CqlExecutionService` / `MetricsConfig`

---

## BUG-065 — CqlController IDOR 授權修復 + LIKE 萬用字元注入防護

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-03 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞（存取控制 / 注入） |
| **影響範圍** | `CqlController.java`、`CqlLibraryService.java`、`MeasureDefinitionService.java` |

### BUG 描述

安全審查發現 2 項問題：

1. **CqlController 缺少授權檢查（IDOR）**（HIGH）：`GET /api/cql/libraries/owner/{username}` 與 `GET /api/cql/libraries/shared/{username}` 允許任何已認證使用者查詢**任意其他使用者**的程式庫，未驗證請求者身份。對比 `MeasureController` 的相同端點已正確實作 `ownershipVerifier.isAdmin() || getCurrentUsername().equals(username)` 檢查。
2. **LIKE 萬用字元注入**（MEDIUM）：`CqlLibraryService.getSharedLibraries()` 與 `MeasureDefinitionService.getSharedMeasures()` 直接將 `username` 串接至 LIKE 模式（`%"username"%`），未跳脫 `%` 和 `_` 萬用字元。攻擊者可傳送 `%` 作為使用者名稱，使 LIKE 模式變為 `%"%"%`，匹配所有已分享記錄。程式碼中已有 `InputValidator.escapeLikeWildcards()` 工具但未被使用。

### 修正方式

1. **CqlController 授權檢查**：
   - `getLibrariesByOwner()` 與 `getSharedLibraries()` 加入 `ownershipVerifier` 檢查
   - 非本人且非管理員時回傳 `403 FORBIDDEN`
   - 與 `MeasureController` 保持一致的授權模式

2. **LIKE 萬用字元跳脫**：
   - `CqlLibraryService.getSharedLibraries()` 套用 `InputValidator.escapeLikeWildcards(username)`
   - `MeasureDefinitionService.getSharedMeasures()` 同步套用

### 驗證

- 後端 `mvn compile` 零錯誤
- 授權模式與 `MeasureController` 完全對齊
- `escapeLikeWildcards` 會跳脫 `%` → `\%`、`_` → `\_`、`\` → `\\`

---

## BUG-064 — CqlEditor paste sanitization 強化 + Monaco 記憶體洩漏修復

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | Monaco 編輯器（前端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 / 記憶體洩漏 |
| **影響範圍** | `frontend/src/components/editor/CqlEditor.tsx` |

### BUG 描述

CqlEditor 審查發現 3 類問題：

1. **不可見字元過濾不完整**（HIGH）：`sanitizePastedText` 僅過濾 4 個 zero-width 字元，遺漏 Bidi 控制字元（`\u202A–\u202E`、`\u2066–\u2069`、`\u061C`、`\u200E–\u200F`）、Soft Hyphen（`\u00AD`）、Line/Paragraph Separator（`\u2028–\u2029`）等。Bidi 字元可被用於 **Trojan Source 攻擊**，讓 CQL 程式碼的顯示與實際語義不同。
2. **onDidPaste 使用 `model.setValue()` 破壞 undo stack**（MEDIUM）：每次貼上都會掃描整份文件並用 `setValue` 替換，導致 undo/redo 歷程完全重建。
3. **Monaco 銷毀時記憶體洩漏**（MEDIUM）：DOM paste event listener 未在 unmount 時移除；`onDidPaste` 和 `onDidChangeCursorPosition` 的 `IDisposable` 未保存；`editorRef` / `monacoRef` 指向已銷毀的實例。

### 修正方式

1. **擴充 sanitizePastedText**：
   - 用單一 character class `[\u200B-\u200F\u202A-\u202E\u2060-\u2064\u2066-\u2069\u061C\u00AD\u180E\uFEFF\uFFF9-\uFFFB]` 取代零散的 4 字元匹配
   - 新增 `\u2028`/`\u2029` → `\n` 轉換（Line/Paragraph Separator）

2. **onDidPaste 改為範圍 sanitize + executeEdits**：
   - 使用 `IPasteEvent.range` 取得貼入範圍，只 sanitize 該範圍文字
   - 改用 `executeEdits('paste-sanitize', ...)` 取代 `model.setValue()`，保留完整 undo/redo 歷程

3. **新增 unmount 清理**：
   - `disposablesRef` 儲存 `onDidPaste` 和 `onDidChangeCursorPosition` 的 `IDisposable`
   - `pasteListenerRef` 追蹤 DOM paste event listener
   - cleanup `useEffect`：unmount 時 dispose 所有訂閱、`removeEventListener`、清空 editor/monaco ref

### 驗證

- TypeScript 編譯零錯誤
- 56 test files / 399 tests 全部通過
- 新增覆蓋的不可見字元包含 Trojan Source 攻擊最常用的 bidi override 序列

---

## BUG-063 — useCqlEditor useCallback 優化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 前端效能（前端） |
| **嚴重程度** | Low |
| **根因類型** | 效能 |
| **影響範圍** | `frontend/src/hooks/useCql.ts` |

### BUG 描述

`useCqlEditor()` hook 內的 `translate`、`validate`、`execute` 三個函式為裸 closure，每次 render 都重建新的函式引用。消費端 `EditorPage.tsx` 的 `handleTranslate` 雖有 `useCallback` 包裝，但依賴 `translateMutation` 物件（`useMutation` 每次 render 回傳新引用），導致 `useCallback` 被穿透，無法穩定引用。

### 修正方式

- `translate`、`validate`、`execute` 三個函式改用 `useCallback` 包裝
- 依賴使用 `.mutate`（TanStack Query 內部以 `useCallback` 包裝，referentially stable）而非整個 mutation 物件，避免因 `isPending`/`data` 等狀態變化導致不必要的重建

### 驗證

- TypeScript 編譯通過
- 消費端 `useCallback` 依賴鏈穩定：`handleTranslate` → `translate` → `translateMutation.mutate`

---

## BUG-062 — FhirController 安全強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 / 配置遺漏 |
| **影響範圍** | `InputValidator.java`、`FhirController.java`、`FhirTerminologyService.java`、`application.yml` |

### BUG 描述

FhirController 安全審計發現 4 項問題：

1. **identifier 參數未驗證**（HIGH）：`/Patient/$search-by-demographics` 的 `identifier` 參數（格式 `system|value`，如 `http://hospital.org/mrn|12345`）直接傳入 Service 層，無輸入驗證，可能被注入惡意字元。
2. **IG URL 路徑變數未驗證**（MEDIUM）：`getIgProfile`、`getIgValueSet`、`getIgCodeSystem` 三個端點的 `{url}` 路徑變數經 URL decode 後直接作為 Map 查詢鍵，未驗證為合法 URL 格式。
3. **RestTemplate 無逾時**（MEDIUM）：`FhirTerminologyService` 的 `RestTemplate` 使用 `new RestTemplate()` 建立，無連線/讀取逾時設定，外部 API（如 RxNav）無回應時執行緒將永久阻塞。
4. **FHIR 連線池耗盡風險**（MEDIUM）：HAPI FHIR 客戶端 socket timeout 30s × 預設 3 次重試 = 單一請求最差 90s，容易耗盡連線池。

### 修正方式

1. **identifier 驗證**：
   - `InputValidator` 新增 `IDENTIFIER_PATTERN = ^[a-zA-Z0-9:./_|\\-]{1,500}$`，覆蓋 `system|value` 格式
   - 新增 `isValidIdentifierParam()` + `requireValidIdentifierParam()`（null-safe）
   - `FhirController.searchPatientsByDemographics()` 加入 `requireValidIdentifierParam(identifier)`

2. **IG URL 驗證**：
   - `InputValidator` 新增 `isValidFhirCanonicalUrl()` — 驗證 http/https scheme、合法 host、無嵌入憑證（不做 SSRF 私有 IP 檢查，因為僅用於 Map 查詢）
   - 新增 `requireValidFhirCanonicalUrl()`
   - 三個 IG 端點在 URLDecode 後加入 `requireValidFhirCanonicalUrl(decodedUrl)`

3. **RestTemplate 逾時**：
   - `new RestTemplate()` 改為 `createRestTemplate()` 靜態工廠
   - 使用 `SimpleClientHttpRequestFactory`：5s 連線逾時、10s 讀取逾時

4. **連線池耗盡防護**：
   - `socket-timeout-ms`：30000 → 15000（15s）
   - `fhirDataProvider` retry `maxAttempts`：3（預設） → 2
   - 最差情境：15s × 2 = 30s（原本 30s × 3 = 90s）

### 驗證

- 所有新增驗證方法為 null-safe，既有測試不受影響
- `isValidIdentifierParam(null)` → true（optional 參數）
- `isValidFhirCanonicalUrl(null)` → true（defensive）
- RestTemplate 逾時確保外部 API 無回應時不會永久阻塞
- 重試預算從 90s 降至 30s，減少連線池壓力

---

## BUG-061 — Measure 元件效能最佳化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 前端效能（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 效能 / 程式碼品質 |
| **影響範圍** | `MeasureComparison`、`DataRequirementsTab`、`TestCasesTab`、`MeasureValidationPanel`、`MeasureReportHistory`、`IndicatorCatalogDialog` |

### BUG 描述

三管齊下審計（reuse / quality / efficiency）Measure 元件目錄後，發現 6 項效能問題：

1. **O(n²) `Math.max`**：`MeasureComparison` 的趨勢圖在 `.map()` 迴圈內反覆呼叫 `Math.max(...dataPoints)`，每次重繪 N 點需 O(N²) 比較。
2. **缺少 `useMemo`**（3 處）：`DataRequirementsTab`、`TestCasesTab`、`MeasureValidationPanel` 的分組/計數邏輯在每次 render 都重建物件，即使資料未變。
3. **無防抖搜尋**：`IndicatorCatalogDialog` 每次按鍵直接觸發 API 查詢（透過 React Query `queryKey` 變化），快速打字會產生大量無用請求。
4. **靜默 `console.error`**：`MeasureReportHistory.handleExport` 的 catch 僅 `console.error`，使用者看不到匯出失敗訊息。

### 修正方式

1. **O(n²) → O(n)**：將 `Math.max(...)` 提升至 `.map()` 之外，僅計算一次。
2. **`useMemo` 包裝**：
   - `DataRequirementsTab`：`grouped`/`resourceTypeCount`/`valueSetCount` 以 `useMemo([requirements])` 包裝
   - `TestCasesTab`：`passCount`/`failCount`/`totalCount` 以 `useMemo([testCases])` 包裝，改為單次迴圈
   - `MeasureValidationPanel`：`groupedIssues` 以 `useMemo([report])` 包裝
3. **300ms 防抖**：新增 `debouncedSearch` 狀態 + `useEffect` timer，React Query 改用 `debouncedSearch` 作為 queryKey。
4. **`showNotification`**：引入 `useNotification` + `extractApiError`，替換 `console.error`。

### 驗證

- TypeScript 編譯 0 errors
- 所有 useMemo 依賴陣列正確（`[requirements]`、`[testCases]`、`[report]`）
- 防抖搜尋：打字停止 300ms 後才送出查詢

---

## BUG-060 — Measure 元件共用化重構

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 程式碼品質（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 程式碼品質 |
| **影響範圍** | 17 個 measure/dashboard 元件、3 個新共用模組 |

### BUG 描述

Measure 元件目錄 29 個檔案中發現三類重複：

1. **`getScoreColor` 重複 7 處**：`EvaluationResultCard`、`BatchEvaluationDialog`、`MeasureReportHistory`、`MeasureComparison`、`MeasurePanel`、`MeasureDashboardPage`、`DepartmentDrilldownChart` 各自定義功能相同但回傳型別不一致的分數→顏色函式。
2. **`downloadBlob` 重複 4 處**：`TestCasesTab`、`MeasureEditor`、`MeasureLibrary`、`MeasureReportHistory` 各自內聯 6 行 `URL.createObjectURL` / `a.click()` / `revokeObjectURL` 下載邏輯。
3. **`(error as Error).message` 不安全轉型 10 處**：Axios 錯誤實際為 `AxiosError` 物件，直接轉型會漏掉後端回傳的 `response.data.message`，專案已有 `extractApiError` 工具函式卻未使用。

### 修正方式

1. **`scoreColors.ts`**：新增 `getScoreChipColor()`（MUI Chip color）、`getScoreThemeColor()`（theme path）、`getScoreHex()`（hex string），7 個檔案改用對應函式。
2. **`download.ts`**：新增 `downloadBlob(blob, filename)`，4 個檔案刪除內聯實作。
3. **`extractApiError` 統一**：10 個 catch block 改用 `extractApiError(err)`，8 個檔案補 import。

### 驗證

- TypeScript 編譯 0 errors
- 7 個 score color 使用點行為一致（null → error、<50 → error、<80 → warning、≥80 → success）
- downloadBlob 行為不變（createObjectURL → click → revokeObjectURL）

---

## BUG-059 — Service 層安全強化 + Builder 元件去重

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 程式碼品質（前後端） |
| **嚴重程度** | Medium |
| **根因類型** | 安全漏洞 / 程式碼品質 |
| **影響範圍** | 7 個後端 Service、10 個前端 Builder 元件、6 個新共用模組 |

### BUG 描述

**後端 Service 層**（7 項）：
1. `FhirClientFactory`：暫存的 `FhirContext` 在 credential 變更後未清除，且 `hapiFhirContext()` 被多執行緒同時呼叫無鎖保護。
2. `VsacService`：日誌中記錄 API key 值（`LOG.info("Using VSAC API key: {}")`）。
3. `TestCaseService`：每次呼叫 `new FhirContext(FhirVersionEnum.R4)` 而非注入共用實例。
4. `EmailService`：捕捉 `MessagingException` 後拋出 `RuntimeException(e.getMessage())`，丟失 stack trace。
5. `EhrConnectionService`：方法級 `@Transactional` 標註在僅讀取操作上，不必要地持有資料庫連線。
6. `PasswordResetService`：SMTP 寄信在 DB 交易內執行，失敗時回滾 token 但使用者已收到信。
7. `CqlTranslationService`：每次呼叫建立新的 `LibraryManager` 與 `ModelManager`，初始化開銷大。

**前端 Builder 元件**（5 項）：
1. `RESOURCE_TYPES` 常數在 `QueryBuilder` 和 `RetrieveBuilder` 中重複定義。
2. `extractName()`/`parseCodeName()` 名稱解析邏輯在 3 個元件中重複。
3. `ConditionalBuilder` 接收 `expressions`/`parameters` props 但從未使用。
4. 6 個元件各自實作 clipboard + notification 邏輯（`navigator.clipboard.writeText` + `showNotification`）。
5. `CodesSection` 和 `ValueSetSection` 各含 ~80 行完全相同的 TW Core 瀏覽 UI。

### 修正方式

**後端**：
1. `FhirClientFactory`：加入 `ReadWriteLock` + credential hash 變更偵測，credential 改變時自動清除快取。
2. `VsacService`：移除 API key 日誌輸出。
3. `TestCaseService`：改注入 Spring 管理的 `FhirContext` Bean。
4. `EmailService`：改 `throw new RuntimeException("Failed to send email", e)` 保留原因鏈。
5. `EhrConnectionService`：移除不必要的 `@Transactional`。
6. `PasswordResetService`：使用 `@TransactionalEventListener(AFTER_COMMIT)` 確保 DB 成功後再寄信。
7. `CqlTranslationService`：快取 `LibraryManager`，透過 `LibraryManagerFactory` 提供。

**前端**：
1. 新增 `fhirResources.ts`（`FHIR_RESOURCE_TYPES` 常數 + `FhirResourceType` 型別）。
2. 新增 `cqlNames.ts`（`extractCqlName()` 函式）。
3. 移除 `ConditionalBuilder` 未使用的 `expressions`/`parameters` props。
4. 新增 `useCopyToClipboard` hook，6 個元件改用。
5. 新增 `useFilteredTwcoreCatalog` hook + `TwcoreBrowser` 元件，刪減 ~160 行重複 JSX。

### 驗證

- TypeScript 編譯 0 errors
- 所有 Builder 元件功能不變
- Service 層注入與快取邏輯正確

---

## BUG-058 — Repository 層簡化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 程式碼品質 / 安全漏洞 |
| **影響範圍** | 7 個 Repository、`AuditLogSpecification`、`InputValidator`、`MeasureDefinitionService` |

### BUG 描述

全面審計 25 個 Spring Data JPA Repository 後，發現三類問題：

1. **死碼**（18 個未使用方法）：多個 Repository 宣告了從未被任何 Service 或 Controller 呼叫的查詢方法。部分為無分頁的 `List<>` 查詢（如 `AuditLogRepository.findByCreatedAtAfterOrderByCreatedAtDesc`），若被誤用可載入數百萬筆記錄導致 OOM。`UserRecentRepository.deleteOldestBeyondLimit` 使用非標準 JPQL `LIMIT` 子查詢，存在跨資料庫相容性風險。
2. **LIKE 萬用字元注入**（MEDIUM）：`MeasureDefinitionRepository.findByDepartmentAndSearchTerm` 使用 `CONCAT('%', :search, '%')` 構建 LIKE 模式，使用者輸入 `%` 可匹配所有列、輸入 `_` 可匹配任意單字元。
3. **工具重複**：`AuditLogSpecification.escapeLikeWildcards()` 為 private 方法，其他需要 LIKE 跳脫的程式碼無法共用。

### 修正方式

1. **死碼移除**：從 7 個 Repository 移除 18 個未使用方法：
   - `AuditLogRepository`：3 個無分頁查詢（已被 Specification 方式取代）
   - `CdsServiceConfigRepository`：5 個（已被 `WithPrefetch` 版本取代）
   - `CdsArtifactRepository`：3 個
   - `CqlLibraryRepository`：1 個（`findByAccessLevel`，已被 `findSharedWithUser` 取代）
   - `MeasureDefinitionRepository`：2 個（`findByStatus`、`findByAccessLevel`）
   - `NotificationRepository`：1 個（無限制版，已被 `findTop50` 取代）
   - `CdsFeedbackRepository`：1 個（無排序版，已被排序版取代）
   - `UserRecentRepository`：1 個（`deleteOldestBeyondLimit`，未使用且 JPQL 非標準）

2. **LIKE 跳脫**：
   - `InputValidator` 新增 `escapeLikeWildcards(String)` 公用方法
   - `MeasureDefinitionService.search()` 在呼叫 `findByDepartmentAndSearchTerm` 前跳脫搜尋字串
   - `AuditLogSpecification` 改用共用的 `InputValidator.escapeLikeWildcards()`

### 驗證

- IDE 診斷 0 errors、0 warnings（所有修改檔案）
- 搜尋確認所有移除的方法在 Service、Controller、Test 中均無引用
- LIKE 跳脫確保 `%`、`_` 字元被正確轉義為 `\%`、`\_`

---

## BUG-057 — Model DTO 驗證強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 / 程式碼品質 |
| **影響範圍** | ~35 個 model DTO 與 4 個 controller |

### BUG 描述

全面審計 78 個 Model DTO 後，發現多層安全與品質問題：

1. **SSRF 風險**（CRITICAL）：`CdsRequest.fhirServer`、`CqlExecutionRequest.fhirServerUrl` 無 URL 驗證，攻擊者可探測內部網路。`AuthoringController` 有獨立的 29 行 `validateFhirServerUrl()` 與 `InputValidator.requireValidUrl()` 行為不一致（Docker hostname 判斷邏輯分歧）。
2. **DoS — 無限字串**（HIGH）：CQL 內容（7 處）、FHIR JSON（4 處）、prefetch JSON 等大型文字欄位無 `@Size` 限制，攻擊者可傳入 GB 級 payload 造成 OOM。
3. **Auth DTO 驗證缺失**（HIGH）：`RegisterRequest.email` 無 `@NotBlank`/`@Email`、`LoginRequest` 欄位無 `@Size`、`AdminCreateUserRequest` 密碼最小長度僅 6（其他處為 8）、`OktaCallbackRequest.code` 無長度限制。
4. **CDS/Measure DTO 驗證缺失**（MEDIUM）：`CdsFeedbackRequest.outcome` 無 `@Pattern`、集合欄位無 `@Size` 上限、`BatchTestCaseImportRequest` 無 `@NotNull`/`@Valid`（且 controller 缺 `@Valid` 註解）。
5. **死碼 / 品質問題**（LOW）：4 個 request DTO 含未使用的 `currentUser` 欄位、`FormTemplate.extendsTemplate` 缺 `@JsonProperty("extends")` 導致模板繼承靜默失敗、`ValidationReport.finalize()` 遮蔽 `Object.finalize()`。
6. **正規表達式尾隨 `|`**（LOW）：3 處 `@Pattern` regex 以 `|` 結尾，允許空字串但 `@Pattern` 對 null 本就通過驗證，語意不清。

### 修正方式

1. **SSRF 防護**：
   - `CdsRequest.fhirServer` 加 `@Size(max=500)`、`CqlExecutionRequest.fhirServerUrl` 加 `@Size(max=500)`
   - `CdsHooksController`（2 處）、`CqlController`（1 處）加入 `InputValidator.requireValidUrl()` 呼叫
   - `AuthoringController.validateFhirServerUrl()` 29 行重複方法刪除，改用 `InputValidator.requireValidUrl()`

2. **@Size 補全 — CQL/JSON 欄位**：
   - CQL 內容欄位統一加 `@Size(max=512_000)`（7 處）
   - FHIR JSON/Bundle 欄位統一加 `@Size(max=2_097_152)`（4 處）
   - 其他 string/list 欄位依語意加上適當 `@Size`

3. **Auth DTO 驗證**：`RegisterRequest` 加 `@NotBlank @Email @Size @NoXss`、`LoginRequest` 加 `@Size`、`AdminCreateUserRequest` 密碼最小長度 6→8 + `@Email`、`OktaCallbackRequest`/`ForgotPasswordRequest`/`ResetPasswordRequest` 加 `@Size`

4. **CDS/Measure DTO 驗證**：`CdsFeedbackRequest.outcome` 加 `@Pattern(regexp="accepted|overridden")`、所有集合加 `@Size` 上限、`CdsSandboxRequest.context` 加 `@Valid` 級聯驗證、`BatchTestCaseImportRequest` 加 `@NotNull @Valid @Size` + `MeasureController` 加 `@Valid`

5. **品質修正**：
   - 4 個 DTO 移除死碼 `currentUser` 欄位
   - `FormTemplate` 加 `@JsonProperty("extends")`
   - `ValidationReport.finalize()` 更名為 `complete()`（含 2 處呼叫端更新）
   - `ApiKeyCreateRequest.name` 加 `@NotBlank`
   - 3 處 `@Pattern` regex 移除尾隨 `|`

### 驗證

- IDE 診斷 0 errors、0 warnings（所有修改檔案）
- 所有 `@Size` 限制在 DoS 防護（512KB CQL / 2MB JSON）與實際使用場景之間取得平衡
- `@Valid` 級聯確保巢狀 DTO 的驗證也被觸發
- `AuthoringController` SSRF 邏輯統一為 `InputValidator.requireValidUrl()`，消除行為分歧

---

## BUG-056 — CQL 預覽對話框文字顏色修正

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-02 |
| **功能分類** | 規則撰寫（前端） |
| **嚴重程度** | Low |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/authoring/ArtifactWorkspaceHeader.tsx` |

### BUG 描述

Authoring 功能的「產生的 CQL」預覽對話框中，程式碼文字使用硬編碼顏色 `#d4d4d4`（淺灰色），在深色模式的 `#1e1e1e` 背景下清晰可見，但在淺色模式的 `#f5f5f5` 背景下幾乎不可辨識（淺灰色文字在淺灰色背景上）。

### 修正方式

將 `ArtifactWorkspaceHeader.tsx` 第 353 行的 `color: '#d4d4d4'` 改為 theme-aware 條件式：
```tsx
color: (theme) => theme.palette.mode === 'dark' ? '#d4d4d4' : '#1e1e1e'
```

### 驗證

- 深色模式：文字 `#d4d4d4` 在 `#1e1e1e` 背景上清晰可見
- 淺色模式：文字 `#1e1e1e` 在 `#f5f5f5` 背景上清晰可見

---

## BUG-055 — Controller 輸入驗證強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 / 程式碼品質 |
| **影響範圍** | `backend/.../controller/FhirController.java`, `backend/.../controller/MeasureController.java`, `backend/.../security/InputValidator.java`, `backend/.../util/DigestUtils.java`, `backend/.../service/PasswordResetService.java` |

### BUG 描述

FhirController 與 MeasureController 存在多項輸入驗證與程式碼品質問題：

1. **手動驗證散落各處**（HIGH）：9 處 `isValidUrl()` 檢查分散在 FhirController 中，每處都是 3 行 if-throw 樣板，容易遺漏且不一致。
2. **URI.create() 未捕獲異常**（MEDIUM）：`URI.create(url)` 拋出 unchecked `IllegalArgumentException`，繞過 VSAC 錯誤處理邏輯。
3. **DNS 查詢無快取**（MEDIUM）：`isValidUrl()` 中 `isLocalDevelopment()` 每次呼叫都重新檢查系統屬性，而該值在 JVM 生命週期中不變。
4. **SHA-256 重複實作**：`UserEntity.computeEmailHash()`、`UserApiKeyService.hashApiKey()`、`PasswordResetService.sha256()` 三處各自實作相同的 SHA-256 邏輯。
5. **分頁大小未設上限**（MEDIUM）：`MeasureController.listMeasures` 的 `size` 參數無上限，攻擊者可傳入極大值造成 OOM。

### 修正方式

1. **require* 一行呼叫**：在 `InputValidator` 新增 `requireValidUrl()`、`requireValidFhirResourceType()`、`requireValidResourceId()`、`requireValidCacheName()`、`requireValidSearchParams()` 五個 throwing helper，將 FhirController 中 ~60 行 if-throw 區塊替換為一行呼叫。
2. **URI 安全**：`URI.create(url)` 改為 `new URI(url)` + `catch URISyntaxException`，確保格式異常走正確的錯誤處理路徑。
3. **快取 isLocalDevelopment()**：將計算結果存入 `static final Boolean IS_LOCAL_DEV`，避免重複查詢系統屬性。
4. **DigestUtils 抽取**：新建 `com.cqlplatform.util.DigestUtils.sha256Hex()`，使用 Java 21 `HexFormat`，替換三處重複實作。
5. **Math.clamp (Java 21)**：FhirController 3 處、MeasureController 2 處 `Math.max(min, Math.min(val, max))` 改為 `Math.clamp(val, min, max)`。`listMeasures` 的 `size` 參數加上 `Math.clamp(size, 1, 200)` 上限。

### 驗證

- 所有 `require*` 呼叫在參數不合法時正確拋出 `IllegalArgumentException`
- 畸形 URI 不再導致 uncaught exception
- `listMeasures` size 參數被限制在 1–200
- IDE 零診斷（0 errors, 0 warnings）

---

## BUG-054 — Entity 安全強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | 安全性（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 安全漏洞 |
| **影響範圍** | `backend/.../entity/UserEntity.java`, `backend/.../entity/UserApiKeyEntity.java`, `backend/.../entity/EhrConnectionEntity.java`, `backend/.../entity/DepartmentEntity.java`, `backend/.../entity/IndicatorCatalogEntity.java`, `backend/.../entity/MeasureDefinitionEntity.java`, `backend/.../entity/CqlLibraryEntity.java`, `backend/.../entity/MeasureScheduleEntity.java`, `backend/.../entity/MeasureThresholdEntity.java`, `backend/.../service/UserApiKeyService.java`, `backend/.../controller/UserApiKeyController.java`, `backend/.../resources/db/migration/V35__api_key_hashing.sql` |

### BUG 描述

全面審計 25 個 Entity 後，發現多項安全漏洞分佈在 9 個 Entity 中：

1. **密碼洩漏**（CRITICAL）：`UserEntity.password` 無 `@JsonIgnore`，任何回傳 `UserEntity` 的 API 都會將 BCrypt hash 序列化至 JSON response。
2. **API Key 明文儲存**（CRITICAL）：`UserApiKeyEntity.apiKey` 以明文存入資料庫，資料庫洩漏等同所有 API key 外洩。
3. **EHR 憑證明文儲存**（CRITICAL）：`EhrConnectionEntity.credentials`（含 FHIR server 密碼/token）以明文存入資料庫。
4. **Mass Assignment**（HIGH）：9 個 Entity 的伺服器控制欄位（`id`、`createdAt`、`updatedAt`、`ownerUsername`、`role`、`enabled` 等）未標記 `READ_ONLY`，攻擊者可透過 JSON request body 覆寫這些值。
5. **validateApiKey 早期返回 bug**（HIGH）：legacy key 升級路徑的 `return` 跳過了 `lastUsedAt` 更新，且 managed entity 被修改 plaintext 後可能被 JPA dirty-checking 寫回 DB。

### 修正方式

1. **密碼保護**：`UserEntity.password` 加上 `@JsonIgnore`；`role`、`enabled` 加上 `@JsonProperty(READ_ONLY)`。
2. **API Key SHA-256 雜湊**：
   - `UserApiKeyService.generateApiKey()` 改為儲存 SHA-256 hash，新增 `keyPrefix` 欄位（前 8 字元）供顯示用。
   - `validateApiKey()` 以 hash 查詢；fallback 明文查詢後自動升級為 hash（向後相容）。
   - 用 `entityManager.detach(entity)` 防止 JPA dirty-checking 將暫時設定的明文 key 寫回 DB。
   - 加入 15 分鐘 debounce 減少 `lastUsedAt` 的 DB 寫入。
   - 重構為 single exit path，修復 legacy key 跳過 `lastUsedAt` 的 bug。
   - Flyway `V35__api_key_hashing.sql`：新增 `key_prefix` 欄位，從現有明文 key 填充。
3. **EHR 憑證加密**：`EhrConnectionEntity.credentials` 加上 `@Convert(converter = EncryptionConverter.class)` 以 AES-GCM 加密儲存，並標記 `@JsonProperty(WRITE_ONLY)` 防止 API 回傳。
4. **Mass Assignment 防護**：9 個 Entity 的伺服器控制欄位加上 `@JsonProperty(access = READ_ONLY)`，使 Jackson 反序列化時忽略這些欄位。
5. **UserApiKeyController**：移除 `maskKey()` 方法，改用 `keyPrefix + "..."` 顯示。

### 驗證

- `UserEntity` JSON 序列化不含 `password` 欄位
- API key 存入 DB 為 64 字元 SHA-256 hex（非明文）
- Legacy 明文 key 首次使用後自動升級為 hash
- EHR 憑證在 DB 中為加密密文，API 回應不含 `credentials`
- 所有 Entity 的 `id`/`createdAt`/`updatedAt` 無法透過 JSON request body 覆寫
- IDE 零診斷（0 errors, 0 warnings）

---

## BUG-053 — AuthController 安全強化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | 認證系統（後端） |
| **嚴重程度** | High |
| **根因類型** | 安全漏洞 |
| **影響範圍** | `backend/.../controller/AuthController.java` |

### BUG 描述

AuthController 存在三項安全問題：

1. **SSO 錯誤訊息洩漏**（HIGH）：Okta SSO callback 失敗時回傳 `e.getMessage()`，可能暴露內部主機名稱、Okta 配置細節或堆疊追蹤片段。
2. **JIT provisioning 競態條件**（MEDIUM）：`deriveUsername()` 的 `existsByUsername` 檢查與 `save` 之間無同步，兩個並行的 Okta 登入請求可能通過相同的唯一性檢查，導致 DB unique constraint violation → 500 錯誤。
3. **getBaseUrl 信任代理 header**（MEDIUM）：當 `APP_BASE_URL` 未設定時，密碼重設連結的 base URL 直接從 `X-Forwarded-Host` / `X-Forwarded-Proto` header 產生，攻擊者可偽造 header 將密碼重設連結導向惡意網域。

### 修正方式

1. **SSO 錯誤訊息**：移除 `e.getMessage()`，僅回傳通用的 `"SSO authentication failed"` 訊息。內部錯誤仍透過 `log.error()` 記錄。
2. **JIT 競態條件**：將 Okta user 建立邏輯從 `orElseGet` lambda 改為明確的 `if (user == null)` 區塊，用 `try-catch(DataIntegrityViolationException)` 包裝，捕獲時重新查詢取得已建立的使用者。
3. **getBaseUrl fallback**：新增 `log.warn()` 警告，讓運維人員在日誌中看到未配置 `APP_BASE_URL` 的風險提示。

### 驗證

- SSO 失敗回應不含任何內部細節
- 並行 Okta JIT provisioning 不會產生 500 錯誤
- 未設定 `APP_BASE_URL` 時日誌會出現警告訊息

---

## BUG-052 — CDS 人工製品表格欄位錯位

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | 規則撰寫（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/authoring/ArtifactList.tsx` |

### BUG 描述

「規則撰寫」頁面的 CDS 人工製品列表表格，header 欄位（名稱 / 版本 / 狀態 / 更新時間 / 操作）與資料列欄位嚴重錯位。

根因：使用 `react-window` 的 `FixedSizeList` 虛擬捲動，每一資料列是獨立的 `<Table>` 元素，與 header 的 `<Table>` 沒有共享欄寬，各自由瀏覽器自動計算寬度，導致不對齊。（與 BUG-036 MeasureLibrary 相同模式）

### 修正方式

- 定義 `COL_WIDTHS` 常數統一五欄的百分比寬度（40% / 12% / 12% / 20% / 16%）
- Header table 和每列 body table 都套用 `tableLayout: 'fixed'` + 相同的 `width` 值
- 名稱欄加上 `overflow: hidden; textOverflow: ellipsis` 防止過長名稱撐破版面

### 驗證

- 表格 header 與資料列的五欄完全對齊
- 長名稱正確截斷顯示

---

## BUG-051 — 外部連線 CORS 被擋 + PNA header 未覆蓋動態 origin

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-03-01 |
| **功能分類** | Docker 基礎設施（後端） |
| **嚴重程度** | High |
| **根因類型** | 配置遺漏 / 邏輯錯誤 |
| **影響範圍** | `backend/.../WebConfig.java`, `application-docker.yml`, `docker-compose.yml`, `docker/.env` |

### BUG 描述

外部使用者透過 LAN IP 或 Cloudflare Tunnel 連線時，所有 API 請求因 CORS 被瀏覽器攔截。兩處根因：

1. **配置遺漏**：Docker profile 下 CORS 只允許 `localhost:8888` / `127.0.0.1:8888`，沒有機制加入外部 IP 或域名。
2. **邏輯錯誤**：`privateNetworkFilter` 的 `isAllowedOrigin()` 使用硬編碼的靜態清單，不含 `cors.allowed-origins` 設定的動態 origin，也不含 docker profile 的 `localhost:8888`。即使 CORS 正確，Private Network Access preflight 仍會失敗。

### 修正方式

- **WebConfig.java**：刪除硬編碼 `ALLOWED_ORIGINS` 靜態清單與 `isAllowedOrigin()` 靜態方法。新增 `getAllAllowedOrigins()` 實例方法作為唯一的 origin 來源，`corsFilter()` 和 `privateNetworkFilter()` 共用同一份邏輯。
- **application-docker.yml**：新增 `cors.allowed-origins: ${CORS_ALLOWED_ORIGINS:}` 映射。
- **docker-compose.yml**：backend environment 新增 `CORS_ALLOWED_ORIGINS` 傳遞。
- **docker/.env** / **.env.example**：新增 `CORS_ALLOWED_ORIGINS` 佔位與說明。

### 驗證

- 設定 `CORS_ALLOWED_ORIGINS=http://192.168.1.100:8888` 後，外部瀏覽器可正常存取
- CDS Hooks Sandbox 的 PNA preflight 正確回傳 `Access-Control-Allow-Private-Network: true`
- 未設定時行為不變（僅允許 localhost）

---

## BUG-050 — CDS 卡片 CodeableConcept 多 coding 只顯示第一個

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-27 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/.../CdsResourceFormatter.java` |
| **Commit** | [`aed0ecb`](../../commit/aed0ecb) |

### BUG 描述

CDS 卡片在 AllergyIntolerance（及其他資源）的 `code` 包含多個 coding 時，僅顯示第一個 coding 的 display。例如同時輸入 ZINC 和 MAGNESIUM 兩個過敏藥物，卡片只顯示 ZINC。

根因：所有 `appendXxx()` 方法皆使用 `getCodingFirstRep()` 取第一筆 coding，忽略後續 coding。

### 修正方式

提取 `formatAllCodings()` helper 方法：
- 有 `text` 時直接回傳 text
- 無 `text` 時遍歷所有 `coding[]`，以逗號連接各 `display`（無 display 則 fallback 至 `code`）
- 統一套用至 Observation、Condition、MedicationRequest、Procedure、AllergyIntolerance 五個 formatter

### 驗證

- AllergyIntolerance 填入兩個 coding（ZINC + MAGNESIUM）→ 卡片顯示 "Allergy: ZINC (AS ZINC OXIDE), MAGNESIUM (AS PHO...)"
- 單一 coding 行為不變；有 text 時優先顯示 text

---

## BUG-049 — CDS 卡片僅顯示資源參考而非過敏藥物名稱

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-27 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/.../CdsResourceFormatter.java`, `backend/.../CdsValueFormatter.java` |
| **Commit** | [`8b67eb6`](../../commit/8b67eb6) |

### BUG 描述

CDS Sandbox 測試過敏 CDS 服務時，卡片顯示 `AllergyIntolerance/allergyintolerance-46efbb69` 而非實際藥物名稱 "Aspi-Cor 81 MG Delayed Release Oral Tablet"。

兩處根因：
1. `CdsResourceFormatter.appendAllergyIntolerance()` 僅檢查 `code.text`，但**未 fallback** 至 `code.coding[0].display`（`appendCondition` 和 `appendObservation` 皆有此 fallback）。使用者透過 RxNorm 碼表填入 coding 但未填「文字」欄位時，藥名完全不顯示。
2. `CdsValueFormatter.formatValue()` 對 List 中的 Resource 呼叫 `formatReference()` 僅回傳 `Type/id`，而非 `formatDetail()` 的完整臨床資訊。

### 修正方式

- `appendAllergyIntolerance()`：新增 `else if (hasCoding())` fallback 取 `getCodingFirstRep().getDisplay()`
- `appendProcedure()`：同樣補上 coding fallback（相同缺陷）
- `CdsValueFormatter.formatValue()`：Resource 項目改用 `formatDetail()` 取代 `formatReference()`

### 驗證

- CDS Sandbox 新增 AllergyIntolerance（僅填 coding，不填 text）→ 卡片正確顯示藥名
- 同時填 text 與 coding → 優先顯示 text（行為不變）
- Procedure 資源同理修正，卡片正確顯示處置名稱

---

## BUG-048 — RxNorm 代碼搜尋無結果 — FHIR 術語伺服器未載入 UMLS 授權碼表

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-27 |
| **功能分類** | 術語查詢（後端） |
| **嚴重程度** | High |
| **根因類型** | 外部服務限制 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/fhir/FhirTerminologyService.java` |
| **Commit** | [`fe50e2a`](../../commit/fe50e2a) |

### BUG 描述

「術語查詢 > 代碼搜尋」使用 FHIR `$expand` 搜尋隱式 ValueSet，但 `tx.fhir.org` 和 `r4.ontoserver.csiro.au` 均未載入 RxNorm（需 UMLS 授權），因此搜尋 RxNorm 代碼時 `$expand` 回傳 "ValueSet not found"，使用者看到 0 筆結果。

### 修正方式

在 `searchCodes()` 中，當所有 FHIR 術語伺服器對 RxNorm 回傳空結果時，新增 NLM RxNav REST API 作為 fallback：

- 偵測 `system == http://www.nlm.nih.gov/research/umls/rxnorm` 且遠端結果為空
- 呼叫 `https://rxnav.nlm.nih.gov/REST/approximateTerm.json?term={text}&maxEntries={count}`
- 解析 JSON `approximateGroup.candidate[]`，以 `rxcui` 去重
- 回傳 `List<CodeSearchResult>`，system 設為 RxNorm URL

RxNav 為 NLM 提供的免費公開 API，無需驗證。

### 驗證

- 選擇 RxNorm 搜尋 "aspirin" → 回傳 aspirin (1191)、aspirin Oral Tablet 等結果
- LOINC / SNOMED 搜尋仍正常運作（無迴歸）

---

## BUG-047 — Fallback paste handler 非同步讀取 clipboardData 導致貼上失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-27 |
| **功能分類** | Monaco 編輯器（前端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `frontend/src/components/editor/CqlEditor.tsx` |
| **Commit** | [`be3c6ce`](../../commit/be3c6ce) |

### BUG 描述

當 Monaco Editor 原生 Clipboard API 貼上失敗時（例如瀏覽器未授予剪貼簿權限、非 HTTPS 環境），fallback paste handler 無法正確介入，導致 Ctrl+V 完全無效。

**根本原因**：`ClipboardEvent.clipboardData` 僅在 paste 事件處理函數的同步執行期間可存取。在 #046 修正中引入的 fallback handler 將 `e.clipboardData?.getData('text/plain')` 放在 `setTimeout` callback 中讀取，此時事件已結束，瀏覽器已銷毀 `clipboardData` 物件，永遠回傳 `null`。

```javascript
// BUG: clipboardData 在 setTimeout 中已不可用
domNode.addEventListener('paste', (e) => {
  const modelBefore = editor.getModel()?.getValue()
  setTimeout(() => {
    // e.clipboardData 已被瀏覽器回收 → null
    const clipboardData = e.clipboardData?.getData('text/plain')
  }, 0)
})
```

### 修正方式

將 `clipboardData.getData('text/plain')` 移至 paste 事件處理函數的同步區域，先暫存至區域變數 `clipText`，再於 `setTimeout` 中使用該變數進行 fallback 寫入：

```javascript
domNode.addEventListener('paste', (e) => {
  // 同步讀取 — 事件處理期間才有效
  const clipText = e.clipboardData?.getData('text/plain')
  if (!clipText) return

  const modelBefore = editor.getModel()?.getValue()
  setTimeout(() => {
    // 使用先前暫存的 clipText
    if (modelBefore === modelAfter) { ... }
  }, 0)
})
```

### 驗證

- 在 Monaco 原生貼上失效的環境中，Ctrl+V 可透過 fallback 正確貼入文字
- 在 Monaco 原生貼上正常的環境中，fallback 不介入（`modelBefore !== modelAfter`）
- 貼上含特殊字元的 LLM 輸出仍正確 sanitize

---

## BUG-046 — Docker 環境下 Monaco Editor 無法貼上（Ctrl+V 無效）

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-25 |
| **功能分類** | Monaco 編輯器（Docker） |
| **嚴重程度** | High |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `docker/nginx.conf`, `frontend/nginx.conf`, `CqlEditor.tsx` |
| **Commit** | [`ae9a0e3`](../../commit/ae9a0e3) |

### BUG 描述

在 Docker 環境中使用 CQL 編輯器時，Ctrl+V 貼上操作完全無效，無法將剪貼簿內容貼入 Monaco Editor。本機開發環境無此問題。

**根本原因**：兩個問題同時存在：

1. **Permissions-Policy 限制剪貼簿存取**：nginx 安全標頭設定 `clipboard-read=(self), clipboard-write=(self)`，在 Docker 環境中瀏覽器對 `self` origin 的解析可能與容器內 nginx 的 port mapping 不一致（容器內 8080 vs 外部映射 port），導致 Clipboard API 被瀏覽器拒絕。
2. **Fallback paste handler 與 Monaco 內建貼上衝突**：DOM 層級的 paste event listener 未檢查 Monaco 是否已處理貼上，直接呼叫 `editor.executeEdits()` 插入文字，可能與 Monaco 原生 paste 產生衝突。

### 修正方式

1. **移除 Permissions-Policy 的剪貼簿限制**：從 `docker/nginx.conf` 和 `frontend/nginx.conf` 移除 `clipboard-read=(self), clipboard-write=(self)`，讓剪貼簿操作回歸瀏覽器預設行為（同源允許）
2. **改寫 fallback paste handler**：使用 `setTimeout(0)` 延遲檢查，比較貼上前後 model 內容；僅在 Monaco 內建貼上未生效時才手動介入處理

### 驗證

- Docker 環境中開啟 CQL 編輯器，Ctrl+V 可正常貼上文字
- 貼上含有特殊字元的 LLM 輸出仍會被 sanitize（smart quotes、zero-width chars）

---

## BUG-045 — 代碼查詢本地 TWCORE IG 回傳 ValueSet 名稱且缺少 display name

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-24 |
| **功能分類** | 術語查詢（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `FhirTerminologyService.java` |
| **Commit** | [`d5e150d`](../../commit/d5e150d) |

### BUG 描述

在術語查詢頁面的「代碼查詢」標籤中，輸入 LOINC code `29463-7`（Body weight）查詢後：
- 「顯示名稱」欄位為空
- 「名稱」欄位錯誤顯示 "TWVitalSigns"（這是 ValueSet 名稱，非代碼名稱）

**根本原因**：`lookupCodeFromLocalIg()` 有兩個問題：

1. **名稱欄位錯誤**：在 ValueSet 中找到代碼時，使用 `vs.getName()`（ValueSet 名稱 "TWVitalSigns"）作為回傳的 `name` 欄位，而非代碼本身的名稱。
2. **空 display 不 fallthrough**：本地 TWCORE IG 的 ValueSet 內僅存放代碼參考（code），不一定有 `display` 屬性。當 `conceptRef.getDisplay()` 為空時仍立即回傳結果，不會繼續 fallthrough 到遠端術語伺服器（tx.fhir.org）取得完整的 display name 和 designations。

### 修正方式

1. ValueSet 查找時，若 `conceptRef.getDisplay()` 為空或 blank，跳過不回傳，讓流程繼續到遠端伺服器
2. `name` 欄位改傳 `null`，不再使用 ValueSet 名稱作為代碼名稱

### 驗證

- 術語查詢 LOINC `29463-7`，顯示名稱正確顯示 "Body weight"，不再出現 "TWVitalSigns"

---

## BUG-044 — Retrieve Builder 依賴不存在的 C3F 外部函式庫致 CQL 無法解析

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-24 |
| **功能分類** | CQL Builder（前端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `RetrieveBuilder.tsx`, `CqlBuilderPanel.tsx` |
| **Commit** | [`d5e150d`](../../commit/d5e150d) |

### BUG 描述

在 CQL Builder 的 Retrieve 模式中，勾選「最近一次」（Most Recent）、「啟用/已確認」（Active/Confirmed）或「回溯期間」（Look Back）修飾器後，產生的 CQL 程式碼使用 `C3F.MostRecent(...)`、`C3F.ActiveCondition(...)` 等函式，並自動插入 `include CDS_Connect_Commons_for_FHIRv401 version '1.1.1' called C3F`。

然而此函式庫在後端 repository 中不存在，CQL 翻譯器無法解析 `C3F` 識別符號，導致錯誤：「Could not resolve identifier C3F in the current library.」

**根本原因**：RetrieveBuilder 的 `generateCql()` 假設環境中存在 CDS Connect Commons 外部函式庫，但實際部署環境無此函式庫。

### 修正方式

1. **`RetrieveBuilder.tsx`**：重寫 `generateCql()` 產生內嵌 CQL 查詢，不再依賴任何外部函式庫：
   - `C3F.MostRecent(list)` → `Last(list sort by Coalesce(effective as dateTime, issued))`
   - `C3F.ActiveCondition(list)` → `list C where C.clinicalStatus.coding.code contains 'active'`
   - `C3F.LookBack(list, N units)` → `list O where O.effective >= Now() - N units`
   - 新增 `getDateExpression()` 依資源類型回傳正確的日期欄位
2. **`CqlBuilderPanel.tsx`**：移除已無用的 `handleAutoIncludeC3F` 和 `handleInsertWithCheckC3F`，definitions 改用 `handleInsertWithCheck`

### 驗證

- Retrieve Builder 勾選 Most Recent → CQL 預覽顯示 `Last([Observation: ...] O sort by ...)` 語法，翻譯無錯誤
- 勾選 Active/Confirmed → 產生 `where C.clinicalStatus.coding.code contains 'active'`，翻譯無錯誤

---

## BUG-043 — TestCaseEditor expectedPopulations 被 React Query refetch 競態重置

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-24 |
| **功能分類** | Test Cases（前端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `TestCaseEditor.tsx`, `TestCasesTab.tsx` |
| **Commit** | [`5b09697`](../../commit/5b09697) |

### BUG 描述

使用者在測試案例編輯器中修改 expectedPopulations（分母/分子的預期值），按儲存後變更未生效，數值恢復為修改前的狀態。多次嘗試修改皆無法持久化。

**根本原因**：兩層 `useEffect` 競態導致編輯中的 state 被覆蓋：

1. **`TestCasesTab.tsx`**：sessionStorage 恢復用的 `useEffect` 依賴 `[measure.id, isLoading, testCases]`，每次 React Query 重取 `testCases`（如背景 refetch、其他 mutation 的 `invalidateQueries`）都會重新執行，呼叫 `setEditingRaw(found)` 產生新的物件參考。
2. **`TestCaseEditor.tsx`**：`useEffect` 依賴 `[testCase, dispatch]`，當 `testCase` prop 的物件參考改變時，無條件重置所有表單 state（`setExpectedPops(testCase.expectedPopulations || {})`），覆蓋使用者正在編輯的修改。

使用者操作流程：修改 toggle → React Query 背景 refetch → `testCases` 更新 → TestCasesTab useEffect 設定新 `editing` 物件 → TestCaseEditor useEffect 重置 `expectedPops` 回伺服器舊值 → 使用者按儲存 → 存入的是被重置的舊值。

### 修正方式

1. **`TestCasesTab.tsx`**：加入 `restoredRef` 確保 sessionStorage 恢復邏輯僅在首次載入時執行一次，不因 `testCases` refetch 重複更新 `editing` state。
2. **`TestCaseEditor.tsx`**：加入 `prevTestCaseIdRef` 追蹤 test case ID，`useEffect` 僅在切換到不同 test case 時才重置表單 state，同一 test case 的物件參考變化不會覆蓋使用者修改。

### 驗證

- 開啟測試案例編輯器，修改 expectedPopulations toggle，等待數秒（確保 React Query refetch 有機會觸發），按儲存後重新開啟，修改已正確持久化

---

## BUG-042 — ComparableR4FhirModelResolver 日期轉換破壞 FHIRHelpers 時態運算子

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | CQL Engine（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `ComparableR4FhirModelResolver.java` |
| **Commit** | [`6534790`](../../commit/6534790) |

### BUG 描述

測試案例中 `MR.authoredOn during "Measurement Period"` 時態比較靜默失敗，導致 Denominator 永遠為 false。透過診斷 CQL 表達式逐一隔離 WHERE 子句條件，確認 `authoredOn during` 是唯一失敗的條件。

**根本原因**：`ComparableR4FhirModelResolver.resolvePath()` 在 Resource 層級將 FHIR `DateTimeType` 提前轉換為 CQL `DateTime`。後續 FHIRHelpers（CQL 翻譯器自動包含）的 `ToDateTime()` 函數嘗試對已轉換的 CQL DateTime 呼叫 `.value`，回傳 null，導致 `during` 等時態運算子靜默失敗。

### 修正方式

完全移除 `ComparableR4FhirModelResolver` 中的日期/時間轉換邏輯（`convertIfDateTimeType`、`toEngineDateTime` 方法及相關 imports）。該類別現僅處理 Encounter.class Java 保留字衝突。FHIRHelpers 已正確處理所有 FHIR→CQL 型別轉換。

### 驗證

- 測試案例 "65-year-old lady with DM" 執行結果：initial-population=true、denominator=true、numerator=false，全部符合預期

---

## BUG-041 — Encounter.class Java 保留字衝突致 CQL 路徑解析錯誤

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | CQL Engine（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `ComparableR4FhirModelResolver.java` |
| **Commit** | [`6534790`](../../commit/6534790) |

### BUG 描述

CQL 中 `E.class ~ "AMB"` 存取 Encounter.class FHIR 元素時，Java 反射呼叫 `Object.getClass()` 而非 HAPI FHIR 的 `getClass_()`，回傳 Java Class 物件而非 FHIR Coding，導致 Encounter 類型過濾永遠失敗。

### 修正方式

在 `ComparableR4FhirModelResolver.resolvePath()` 加入特殊處理：當 `path="class"` 且 `target instanceof Encounter` 時，顯式呼叫 `encounter.getClass_()`。

### 驗證

- Outpatient Encounters 定義正確回傳符合條件的 Encounter 資源

---

## BUG-040 — TestCaseService 缺少 Measurement Period 參數致時間過濾失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | Test Cases（後端） |
| **嚴重程度** | High |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `TestCaseService.java` |
| **Commit** | [`6534790`](../../commit/6534790) |

### BUG 描述

測試案例 CQL 中所有引用 `"Measurement Period"` 的時態過濾（如 `during "Measurement Period"`、`overlaps "Measurement Period"`）均失敗，因為 CQL 引擎執行時未提供 Measurement Period 參數，該參數值為 null。

### 修正方式

新增 `buildMeasurementPeriodParams()` 方法，建立當年度（1/1 – 12/31）的 CQL `Interval<DateTime>` 參數，透過 `execRequest.setParameters()` 傳入 CQL 引擎。

### 驗證

- CQL 時態過濾表達式正確評估

---

## BUG-039 — TestCaseService 查詢 FHIR Server 而非使用記憶體內測試 Bundle

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | Test Cases（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `TestCaseService.java` |
| **Commit** | [`6534790`](../../commit/6534790) |

### BUG 描述

`TestCaseService.executeTestCase()` 和 `runWithCoverage()` 使用 `cqlExecutionService.execute()` 透過 REST 客戶端查詢外部 HAPI FHIR 伺服器，但測試案例的患者資料存在於 `patientBundleJson` 欄位中，不在 FHIR 伺服器上，導致所有 `[Resource]` retrieve 回傳空集合。

### 修正方式

1. 新增 `parseBundleResources()` 方法，使用 `FhirContext.forR4()` 解析 Bundle JSON 為 `List<Resource>`
2. 建立 `PrefetchRetrieveProvider`（複用 CDS Hooks 模組的記憶體內資料提供者）
3. 改用 `cqlExecutionService.executeWithProvider()` 以記憶體內資料執行 CQL

### 驗證

- CQL `[Condition]`、`[MedicationRequest]`、`[Encounter]` 等 retrieve 正確從測試 Bundle 取得資料

---

## BUG-038 — HikariCP 連線池耗盡導致所有 API 逾時、無法登入

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | 資料庫連線池（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `application.yml`, `application-docker.yml`, `application-dev.yml`, `CdsHooksService.java` |
| **Commit** | [`ccdf3f2`](../../commit/ccdf3f2) |

### BUG 描述

後端運行一段時間後，所有 API 請求逾時返回 `Connection is not available, request timed out after 30000ms`。使用者無法登入（admin/admin），此問題反覆出現需重啟後端才能暫時恢復。

**根本原因（3 個配置問題）**：

1. **連線池大小不足**：HikariCP 未配置，使用預設值 `maximum-pool-size=10`，在排程任務 (`@Scheduled(fixedRate=60000)`) + API 請求 + FHIR 呼叫並行下不足
2. **OSIV 未關閉**：`spring.jpa.open-in-view` 預設為 `true`，HTTP 請求期間持有 DB 連線不釋放，長時間 FHIR 評估呼叫期間佔住連線
3. **無洩漏偵測**：無 `leak-detection-threshold`，洩漏連線無法被發現

### 修正方式

1. 新增 HikariCP 配置：`maximum-pool-size: 20`、`minimum-idle: 5`、`idle-timeout: 300000`、`max-lifetime: 600000`、`connection-timeout: 20000`、`leak-detection-threshold: 60000`
2. 關閉 OSIV：`spring.jpa.open-in-view: false`
3. CDS 發現方法加上 `@Transactional(readOnly=true)` 防止 OSIV 關閉後的 lazy-loading 問題

### 驗證

- 565 個後端測試全數通過
- Docker 重建後 backend 狀態 healthy，HikariCP 啟動日誌顯示 `CqlPlatformPool - Start completed`

---

## BUG-037 — Autocomplete freeSolo 以顯示標籤覆蓋系統 URL 致術語查詢 503

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | 術語查詢（前端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `DrawerCodeLookupPanel.tsx`, `DrawerCodeSearchPanel.tsx` |
| **Commit** | [`ccdf3f2`](../../commit/ccdf3f2) |

### BUG 描述

術語抽屜中選擇 LOINC 等代碼系統後查詢代碼，返回 HTTP 503 錯誤。

**根本原因**：MUI Autocomplete `freeSolo` 模式下，當使用者從下拉選單選擇一個選項時，`onInputChange` 會被觸發 3 次：`'input'` → `'reset'`。`reason='reset'` 時將 `inputValue` 設為選項的顯示標籤（如 `"LOINC — http://loinc.org"`），覆蓋了先前 `onChange` 設定的純 URL 值 `"http://loinc.org"`。後端收到帶有 `—` 的非法 URL，FHIR 伺服器回傳 503。

### 修正方式

1. `onInputChange` 僅在 `reason === 'input' || reason === 'clear'` 時更新 state
2. `handleLookup` 加入 fallback：`ALL_CODE_SYSTEMS.find(cs => system.includes(cs.url))?.url || system`

### 驗證

- Docker 重建後術語查詢正常返回代碼結果
- 支援下拉選擇與手動輸入 URL 兩種方式

---

## BUG-036 — MeasureLibrary 虛擬捲動表頭與內容欄位錯位擠壓

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-23 |
| **功能分類** | 指標庫表格（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `MeasureLibrary.tsx` |
| **Commit** | [`ccdf3f2`](../../commit/ccdf3f2) |

### BUG 描述

指標庫表格在較小螢幕上欄位完全擠壓在一起，表頭與資料列欄寬不一致。

**根本原因**：`react-window` `FixedSizeList` 將每一列渲染為獨立 `<Table>`，與表頭 `<Table>` 分離。兩個 `<Table>` 各自使用 `auto` 佈局計算欄寬，導致寬度不同步。

### 修正方式

1. 設定 `tableLayout: 'fixed'` 強制固定佈局
2. 欄位寬度改用百分比（`COL_W = { checkbox: '4%', name: '28%', ... }`）
3. 外層包裹 `Box sx={{ overflowX: 'auto' }}`，設定 `minWidth: 860`
4. 名稱欄加上 `overflow: hidden` 防止溢出
5. 頂部工具列加上 `flexWrap: 'wrap'` 響應式排版

### 驗證

- 小螢幕表頭與資料列欄位對齊
- 超長名稱自動截斷，水平捲動正常

---

## BUG-035 — DashboardService 多處 NullPointerException 導致 Dashboard API 回傳 500

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-22 |
| **功能分類** | 品質指標儀表板（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/measure/DashboardService.java` |
| **Commit** | [`3f4c1c5`](../../commit/3f4c1c5) |

### BUG 描述

品質指標儀表板頁面所有 API 端點回傳 HTTP 500：`/dashboard/enhanced`、`/dashboard/trends`、`/dashboard/alerts`、`/dashboard/report`。

**根本原因（多處 null 未防護）**：

1. **Comparator NPE**：`getEnhancedDashboard()` 與 `getTrends()` 使用 `Comparator.comparing(MeasureReportEntity::getCreatedAt)` 排序。若任何 `MeasureReportEntity.createdAt` 為 null，Comparator 拋出 `NullPointerException`。
2. **isAfter() NPE**：`getLatestScoresMap()` 與 `getDepartmentDrilldown()` 中直接呼叫 `r.getCreatedAt().isAfter(existing.getCreatedAt())`，若 `createdAt` 為 null 則 NPE。
3. **字串串接 null**：`getTrends()` 中 `r.getPeriodStart() + " to " + r.getPeriodEnd()` 在 `periodStart`/`periodEnd` 為 null 時產生 `"null to null"` 字串。

### 修正方式

- **Comparator 排序前**：增加 `.filter(r -> r.getCreatedAt() != null)` 過濾空值記錄
- **isAfter() 比較前**：外層增加 `r.getCreatedAt() != null` 條件，內層增加 `existing.getCreatedAt() == null` fallback
- **period 字串串接**：使用三元運算子防護 null（`r.getPeriodStart() != null ? ... : "?"`）

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [ ] `/api/measures/dashboard/enhanced` 回傳 200
- [ ] `/api/measures/dashboard/trends` 回傳 200
- [ ] `/api/measures/dashboard/alerts` 回傳 200
- [ ] `/api/measures/dashboard/report` 回傳 200

---

## BUG-034 — Recharts ResponsiveContainer 初始化時計算 width/height 為 -1

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-22 |
| **功能分類** | 品質指標儀表板（前端） |
| **嚴重程度** | Low |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `frontend/src/components/dashboard/ScoreTrendChart.tsx`、`DepartmentDrilldownChart.tsx`、`ScoreDistributionChart.tsx` |
| **Commit** | [`3f4c1c5`](../../commit/3f4c1c5) |

### BUG 描述

瀏覽器 console 持續出現 Recharts 警告：
```
The width(-1) and height(-1) of chart should be greater than 0
```

**根本原因**：`ResponsiveContainer` 使用 `ResizeObserver` 測量父元素尺寸。在元件初始掛載或父容器尚未完成 layout 時，測量結果可能為 -1。Recharts 官方建議設定 `minWidth={0}` 以防止負值。

### 修正方式

- 三個圖表元件的 `<ResponsiveContainer>` 均加上 `minWidth={0} minHeight={0}` 屬性

### 測試驗證

- [x] `npx tsc --noEmit` 編譯通過
- [ ] 瀏覽器 console 不再出現 width/height 警告

---

## BUG-033 — 工具列 Undo/Redo 按鈕無效 — Redux 歷史與 Monaco 原生 undo 脫節

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-22 |
| **功能分類** | CQL 編輯器（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `frontend/src/components/editor/CqlEditor.tsx`、`frontend/src/pages/EditorPage.tsx` |
| **Commit** | [`dca6617`](../../commit/dca6617) |

### BUG 描述

CQL 編輯器工具列的 Undo（←）/ Redo（→）按鈕永遠處於 disabled 狀態，點擊無反應。

**根本原因（雙重）**：

1. **Redux 歷史堆疊與 Monaco 原生 undo 脫節**：工具列按鈕使用 Redux `past[]`/`future[]` 陣列控制 `disabled` 狀態並執行撤銷。但使用者在 Monaco 編輯器中打字時，`handleChange` 僅呼叫 `setCqlContent()`（不記錄歷史），`past[]` 永遠為空 → `canUndo = past.length > 0` 始終為 `false` → 按鈕永遠 disabled。

2. **`setValue()` 清除 Monaco undo 堆疊**：外部載入內容（如切換 Library）時，`useEffect` 呼叫 `editorRef.current.setValue(cqlContent)` 同步 Redux 狀態至編輯器，但 Monaco 的 `setValue()` 會清除內建的 undo 堆疊，導致 Ctrl+Z 也無法回復到載入前的內容。

### 修正方式

- **`CqlEditor.tsx`**：
  - 新增 `onEditorRef` prop，讓父元件取得 Monaco `IStandaloneCodeEditor` 實例
  - 將外部內容同步從 `setValue()` 改為 `executeEdits('external', [...])`，保留 Monaco 的 undo 歷史堆疊
- **`EditorPage.tsx`**：
  - 工具列 Undo/Redo 按鈕改為直接觸發 Monaco 原生 `undo`/`redo` 指令（`editor.trigger('toolbar', 'undo/redo', null)`）
  - 點擊後自動 `focus()` 回編輯器，確保後續鍵盤操作正常
  - 移除對 Redux `past`/`future` 的依賴，移除 `disabled` 限制

### 測試驗證

- [x] `npx tsc --noEmit` 編譯通過
- [ ] 在編輯器中打字後，點擊工具列 Undo 按鈕可撤銷
- [ ] Ctrl+Z / Ctrl+Y 鍵盤快捷鍵正常運作
- [ ] 透過 Builder 插入/刪除程式碼片段後，Undo 可回復
- [ ] 載入 Library 後，Undo 可回復到載入前的內容

---

## BUG-032 — DataRequirements 未解析 ToConcept 包裝的 CodeRef 及 Case 包裝的日期屬性

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | eCQM 資料需求（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cql/DataRequirementExtractor.java` |
| **Commit** | [`b50d94a`](../../commit/b50d94a) |
| **關聯** | #024 修正的後續問題 |

### BUG 描述

DataRequirements 標籤頁中 Observation 資源顯示「無篩選條件（擷取所有 Observation 資源）」，未擷取 `O.code ~ "HbA1c LOINC"` 的代碼篩選及 `O.effective during "Measurement Period"` 的日期篩選。

**根本原因（雙重）**：

1. **CodeRef 包裝在 ToConcept 中**：`O.code ~ "HbA1c LOINC"` 中 `O.code` 為 `CodeableConcept`、`"HbA1c LOINC"` 為 `Code`，CQL-to-ELM 翻譯器將兩側都用 `ToConcept` 包裝以統一型別：
   ```json
   Equivalent(FunctionRef("ToConcept", Property("code")), ToConcept(CodeRef("HbA1c LOINC")))
   ```
   #024 新增的 `tryExtractCodeRefFilter()` 僅檢查 `codeRefNode.type == "CodeRef"`，但實際型別為 `"ToConcept"`，內嵌的 `CodeRef` 未被展開。

2. **日期屬性包裝在 Case 中**：`O.effective during "Measurement Period"` 中 `Observation.effective` 為 FHIR Choice Type（`effective[x]`），ELM 生成 `Case` 表達式進行型別分支：
   ```json
   In(Case(when Is(dateTime) then ToDateTime(As(Property("effective"))), ...), ParameterRef)
   ```
   `tryExtractDateFilter()` 僅處理直接 `Property` 和 `FunctionRef` 包裝，未處理 `Case` 表達式。

### 修正方式

- **`tryExtractCodeRefFilter()`**：新增 `unwrapToCodeRef()` 輔助方法，遞迴展開 `ToConcept`、`ToCode`、`FunctionRef`、`As`、`Convert` 節點以取得底層 `CodeRef`。在比對前先展開 codeRefNode 再檢查
- **`tryExtractDateFilter()`**：新增 `extractDatePropertyFromExpression()` 輔助方法，遞迴處理 `FunctionRef`、`As`、`Convert`、`Case` 節點。`Case` 處理邏輯遍歷 `caseItem[].then` 分支，從中提取 Property path

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 後端重建部署成功
- [x] Observation (HbA1c): `codeFilter: code → LOINC 4548-4`、`dateFilter: effective`
- [x] Observation (Glycated Albumin): `codeFilter: code → LOINC 13980-8`、`dateFilter: effective`
- [x] Encounter/Condition/MedicationRequest 資料需求未受影響

---

## BUG-031 — 種子 CQL 語法錯誤導致 DataRequirements 標籤頁空白

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | eCQM 種子資料（後端） |
| **嚴重程度** | High |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/config/DataInitializer.java` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

Docker 環境中 Measures 頁面的「資料需求」（Data Requirements）標籤頁對種子指標 DiabetesHbA1cRate 顯示「未找到資料需求」。`GET /api/measures/{id}/data-requirements` 回傳空陣列 `[]`。

**根本原因**：`DataInitializer.seedDemoMeasure()` 中嵌入的 CQL 內容含有多處語法錯誤，CQL 翻譯失敗（`success: false`、`elmJson: null`），導致 `DataRequirementExtractor` 無法從 ELM AST 擷取資料需求。CQL 錯誤包含三類：

1. **`exists` 語法錯誤**：`C.code.coding exists (coding where ...)` — `exists` 應在查詢表達式前方，正確語法為 `exists(C.code.coding Coding where ...)`
2. **`starts with` 非 CQL 運算子**：`coding.code starts with 'E08'` — `starts` 是區間運算子、`with` 是查詢關鍵字，字串前綴比對應使用 `StartsWith()` 函數
3. **FHIR 原始類型需 `.value`**：`Coding.code` 為 FHIR `code` 類型，字串操作需明確取值 `Coding.code.value`
4. **Encounter.class 比較錯誤**：`E.class.code in { 'AMB', 'IMP' }` — `Encounter.class` 為 `Coding` 型別，應定義 `code` 常數並使用等價比較 `E.class ~ "AMB"`
5. **日期區間語法**：`E.period starts during` 應為 `E.period overlaps`

### 修正方式

- **`DataInitializer.java`**：完整重寫種子 CQL 內容，參照已驗證的 `DM_HbA1c_GA_Rate.cql` 語法模式：
  - `exists(C.code.coding Coding where ...)` — 正確的 `exists` 位置
  - `StartsWith(Coding.code.value, 'E08')` — 使用 `StartsWith()` 函數 + `.value` 取值
  - 新增 `codesystem "ActCode"` 及 `code "AMB"/"IMP"` 定義，使用 `E.class ~ "AMB"` 等價比較
  - `E.period overlaps "Measurement Period"` — 正確的期間重疊語法

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 後端重建，CQL 翻譯成功（0 errors, 0 warnings）
- [x] `GET /api/measures/1/data-requirements` 回傳 5 種資源類型（Encounter, Condition, MedicationRequest, Observation ×2）
- [x] DataRequirements 標籤頁正確顯示代碼篩選和日期篩選

---

## BUG-030 — departmentApi/ehrApi/indicatorApi 使用原生 axios 無 JWT 攔截器致 401

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | API 客戶端（前端） |
| **嚴重程度** | High |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `frontend/src/api/departmentApi.ts`、`frontend/src/api/ehrApi.ts`、`frontend/src/api/indicatorApi.ts` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

登入後所有使用 Department、EHR、Indicator API 的頁面（如 Dashboard 部門選擇器、EHR 連線管理、指標目錄）回傳 401 Unauthorized。瀏覽器開發者工具顯示請求標頭中無 `Authorization: Bearer` JWT token。

**根本原因**：`departmentApi.ts`、`ehrApi.ts`、`indicatorApi.ts` 三個 API 模組使用 `import axios from 'axios'`（原生 axios 實例），而非 `import { api } from './client'`（已設定 JWT 攔截器的 axios 實例）。`client.ts` 中的 `api` 實例在 request interceptor 中自動附加 `Authorization: Bearer <token>` 標頭，原生 `axios` 不含此攔截器。

此外，三個模組的 URL 前綴為 `/api/departments`、`/api/ehr`、`/api/indicators`（硬編碼 `/api`），但 `client.ts` 的 `api` 實例已設定 `baseURL: '/api'`，改用 `api` 後需移除 `/api` 前綴以避免雙重前綴 `/api/api/...`。

### 修正方式

- **`departmentApi.ts`**：`import axios from 'axios'` → `import { api } from './client'`，URL 從 `/api/departments` → `/departments`，所有 `axios.get/post/put` → `api.get/post/put`
- **`ehrApi.ts`**：同上，`/api/ehr` → `/ehr`
- **`indicatorApi.ts`**：同上，`/api/indicators` → `/indicators`

### 測試驗證

- [x] `npx tsc --noEmit` TypeScript 編譯通過
- [x] Docker 前端重建部署成功
- [x] 登入後 Dashboard 部門選擇器正常載入
- [x] EHR 連線管理頁面正常載入
- [x] 指標目錄頁面正常載入

---

## BUG-029 — SSE EventSource 無法傳送 Authorization 標頭致 401

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | 通知系統（前後端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/security/JwtAuthenticationFilter.java`、`frontend/src/hooks/useNotifications.ts` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

登入後瀏覽器 console 出現 `/api/notifications/subscribe` 401 Unauthorized 錯誤。SSE（Server-Sent Events）通知訂閱端點無法通過 JWT 認證。

**根本原因**：瀏覽器原生 `EventSource` API 不支援自訂 HTTP 標頭。`useNotifications` hook 使用 `new EventSource(url)` 建立 SSE 連線，無法附加 `Authorization: Bearer <token>` 標頭。後端 `JwtAuthenticationFilter` 僅從 `Authorization` 標頭讀取 JWT token，SSE 請求因此被拒絕。

### 修正方式

- **`JwtAuthenticationFilter.java`**：新增 query parameter token fallback — 當 `Authorization` 標頭不存在且 `request.getParameter("token")` 有值時，合成 `"Bearer " + token` 作為認證標頭
- **`useNotifications.ts`**：建立 EventSource URL 時附加 `?token=${encodeURIComponent(token)}`，將 JWT token 以 query parameter 傳遞

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 前後端重建部署成功
- [x] SSE 連線建立成功，瀏覽器 console 無 401 錯誤
- [x] 通知即時推送功能正常

---

## BUG-028 — DataInitializer 僅在 dev profile 啟用，Docker 環境無種子資料

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | Docker 基礎設施（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/config/DataInitializer.java` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

Docker 環境啟動後資料庫為空，無法使用 `admin/admin` 登入。`DataInitializer` 建立預設管理員帳號和種子指標，但未在 Docker 環境中執行。

**根本原因**：`DataInitializer` 標註 `@Profile("dev")`，僅在 `dev` profile 啟用。Docker 環境使用 `SPRING_PROFILES_ACTIVE=docker` profile，`DataInitializer` 不會載入。

### 修正方式

- **`DataInitializer.java`**：`@Profile("dev")` → `@Profile({"dev", "docker"})`

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 啟動後 admin/admin 可正常登入
- [x] 種子指標 DiabetesHbA1cRate 正確建立

---

## BUG-027 — PatientImportEntity @Lob 與 PostgreSQL schema validation 不相容

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | Docker 基礎設施（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/entity/PatientImportEntity.java` |
| **Commit** | [`63a5781`](../../commit/63a5781) |

### BUG 描述

Docker 環境中後端啟動時 Hibernate schema validation 失敗。`PatientImportEntity.bundleJson` 欄位使用 `@Lob` 標註，Hibernate 在 PostgreSQL 中將其映射為 `oid`（大物件引用），但 Flyway migration 將 `bundle_json` 欄位定義為 `TEXT`。`oid` ≠ `TEXT` 導致 schema validation 不匹配。

**根本原因**：`@Lob` 在 H2 中映射為 `CLOB`（相容），但在 PostgreSQL 中映射為 `oid`。Flyway V28 migration 已將 `CLOB` 修正為 `TEXT`（PostgreSQL 相容），但 JPA entity 仍使用 `@Lob`。

### 修正方式

- **`PatientImportEntity.java`**：移除 `@Lob` 標註，改為 `@Column(name = "bundle_json", columnDefinition = "TEXT")`，明確指定 PostgreSQL 相容的欄位類型

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 後端啟動，Hibernate schema validation 通過
- [x] EHR 匯入功能正常存取 bundle_json 欄位

---

## BUG-026 — Flyway V24-V29 使用 H2 語法導致 PostgreSQL 部署失敗

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | Docker 基礎設施（後端） |
| **嚴重程度** | Critical |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `backend/src/main/resources/db/migration/V24__notifications.sql`、`V25__indicator_catalog.sql`、`V26__department_multi_tenancy.sql`、`V27__dashboard_enhancements.sql`、`V28__ehr_integration.sql`、`V29__okta_sso.sql` |
| **Commit** | [`63a5781`](../../commit/63a5781) |
| **關聯** | Docker 環境使用 PostgreSQL，開發環境使用 H2 |

### BUG 描述

Docker 環境啟動時後端 Flyway migration 失敗，PostgreSQL 拒絕 H2 專用的 SQL 語法。6 個 migration 檔案（V24-V29）在 P2 功能和 Okta SSO 開發期間使用 H2 語法撰寫，未考慮 Docker 環境的 PostgreSQL 相容性。

**錯誤 SQL 語法**：

| H2 語法 | PostgreSQL 語法 | 影響檔案 |
|---------|----------------|---------|
| `AUTO_INCREMENT` | `GENERATED ALWAYS AS IDENTITY` | V24, V25, V26, V27, V28 |
| `CLOB` | `TEXT` | V28 |
| `DOUBLE` | `DOUBLE PRECISION` | V27 |
| `ALTER COLUMN password VARCHAR(255) NULL` | `ALTER COLUMN password DROP NOT NULL` | V29 |

### 修正方式

- **V24**：`id BIGINT AUTO_INCREMENT PRIMARY KEY` → `id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY`
- **V25**：同上
- **V26**：同上
- **V27**：同上 + `DOUBLE` → `DOUBLE PRECISION`
- **V28**：同上 + `CLOB` → `TEXT`
- **V29**：`ALTER COLUMN password VARCHAR(255) NULL` → `ALTER COLUMN password DROP NOT NULL`

**附帶修正**（Docker 基礎設施）：
- **`docker/nginx.conf` + `frontend/nginx.conf`**：`/twcoredata/` location 新增 `resolver 127.0.0.11 valid=30s`，避免 nginx 啟動時因 taiwan-fhir-generator 容器不存在而失敗
- **`docker/docker-compose.yml`**：移除過時的 `version: '3.8'`，新增 Okta SSO 環境變數（`OKTA_ENABLED`、`OKTA_CLIENT_ID`、`OKTA_CLIENT_SECRET`、`OKTA_ISSUER`）
- **`docker/docker-compose.dev.yml`**：移除 `version: '3.8'`，frontend port 從 `5173:80` → `5173:8080`（配合 nginx 監聽 port 8080）

### 測試驗證

- [x] Docker Compose 全 8 服務啟動成功（postgres, backend, frontend, hapi-fhir, prometheus, grafana, alertmanager, taiwan-fhir-generator）
- [x] Flyway V24-V29 migration 全部通過
- [x] PostgreSQL schema 結構正確
- [x] nginx 啟動正常，taiwan-fhir-generator proxy 可用

---

## BUG-025 — CQL 產生器未對 list 型別表達式自動加 exists() 導致驗證失敗

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | CDS Authoring（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/authoring/CqlArtifactBuilder.java` |
| **Commit** | [`0d418f1`](../../commit/0d418f1) |

### BUG 描述

CDS Authoring Tool 產生的 CQL 在驗證時出現錯誤：`Could not resolve call to operator Not with signature (list<FHIR.Condition>)`。

當排除條件（Exclusion）包含 Generic 資源元素（如 `GenericCondition_vsac`）並套用 list-returning 修飾器（如 `ActiveCondition`）時，產生的 CQL 如下：

```cql
define "MeetsExclusionCriteria":
  C3F.ActiveCondition([Condition: "Disorders of lipoprotein metabolism..."])

define "InPopulation":
  "MeetsInclusionCriteria" and not "MeetsExclusionCriteria"
```

`C3F.ActiveCondition()` 回傳 `list<FHIR.Condition>`，但 `InPopulation` 中的 `not` 運算子需要布林值。CQL Engine 無法對清單執行否定運算，導致驗證失敗。

**根本原因**：`CqlArtifactBuilder.buildExpression()` 在套用所有修飾器後，未檢查最終回傳型別。當修飾器鏈的最終型別仍為清單（如 `list_of_conditions`、`list_of_observations` 等），表達式被直接嵌入 `and`/`or` 邏輯運算，而這些運算子僅接受布林運算元。

修飾器如 `CheckExistence`（`exists()`）可將清單轉為布林值，但使用者若未手動加入此修飾器，產生的 CQL 就會出錯。同類問題影響所有 Generic 資源類型（Condition、Observation、Procedure、MedicationRequest 等）搭配 list-returning 修飾器（Active、Confirmed、Completed、MostRecent 等）的情境。

### 修正方式

- **`CqlArtifactBuilder.java`**：
  - `buildExpression()` 末尾新增自動型別檢測：套用所有修飾器後，呼叫 `getFinalReturnType()` 取得最終回傳型別。若型別以 `list_of_` 開頭，自動用 `exists()` 包裝表達式
  - 新增 `getFinalReturnType()` 輔助方法：優先取最後一個修飾器的 `returnType`，fallback 為元素本身的 `returnType`

### 修正結果

修正前產生的 CQL：
```cql
define "MeetsExclusionCriteria":
  C3F.ActiveCondition([Condition: "..."])    ← list<Condition>，驗證失敗
```

修正後產生的 CQL：
```cql
define "MeetsExclusionCriteria":
  exists(C3F.ActiveCondition([Condition: "..."]))    ← boolean，驗證通過
```

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [ ] Authoring 建立含 Condition + Active 修飾器的排除條件 → 產生 CQL 包含 `exists()`
- [ ] CQL 驗證通過，無 `operator Not with signature (list<...>)` 錯誤
- [ ] 已有 `CheckExistence` 修飾器的元素不會重複包裝 `exists(exists(...))`
- [ ] 回傳布林值的修飾器（如 `ValueComparisonNumber`）不受影響

---

## BUG-024 — DataRequirements 未解析 Equal/Equivalent + CodeRef 模式

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | eCQM 資料需求（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cql/DataRequirementExtractor.java` |
| **Commit** | [`53b19ca`](../../commit/53b19ca) |

### 問題描述

CQL 中 `E.class ~ "AMB"` 語法在 ELM 中生成 `Equivalent(Property, CodeRef)` 模式。DataRequirementExtractor 的 `walkWhereClause` 方法僅處理 `Exists`（嵌套子查詢比較）和 `InValueSet` 模式，未處理直接的 `Equal`/`Equivalent` + `CodeRef` 比較。導致 Encounter 資料需求只顯示 `dateFilter: period`，未顯示 `codeFilter: class → ActCode/AMB`。

### 根因分析

ELM AST 中 `E.class ~ "AMB"` 轉譯為：
```json
{
  "type": "Equivalent",
  "operand": [
    { "type": "FunctionRef", "name": "ToCode", "operand": [{ "type": "Property", "path": "class", "source": { "type": "AliasRef", "name": "E" } }] },
    { "type": "CodeRef", "name": "AMB" }
  ]
}
```

`CodeRef` 引用 `library.codes.def[]` 中定義的命名代碼（如 `code "AMB": 'AMB' from "ActCode"`），需要：
1. 從 ELM 的 `library.codes.def[]` 建立代碼定義映射（code name → code system + value）
2. 在 `walkWhereClause` 中識別 `Equal`/`Equivalent` 節點，解開 `FunctionRef` 包裝取得 `Property`，並查詢 `CodeRef` 對應的代碼系統
3. 將解析結果合併到 `RetrieveInfo` 的 `codeProperty`、`codeSystemUrl`、`directCodes`

### 修正方式

1. **新增 `buildCodeDefMap()`**：遍歷 `library.codes.def[]`，建立 `Map<String, CodeDefInfo>`（名稱→代碼系統+值+顯示文字）
2. **新增 `CodeDefInfo` 內部類別**：存儲代碼定義的中間表示
3. **傳遞 `codeDefMap` 貫穿調用鏈**：`collectRetrieves` → `handleQuery` → `enhanceFromWhere` → `walkWhereClause`
4. **新增 `handleCodeRefComparison()`**：在 `walkWhereClause` 的 `Equal`/`Equivalent` 分支中，偵測 `Property`↔`CodeRef` 配對
5. **新增 `tryExtractCodeRefFilter()`**：解開 FunctionRef 包裝取得 Property path，查詢 CodeRef → CodeDefInfo → 解析代碼系統 URL

### 修正結果

修正前 Encounter 資料需求：
```json
{ "type": "Encounter", "codeFilter": null, "dateFilter": [{"path": "period"}] }
```

修正後：
```json
{
  "type": "Encounter",
  "codeFilter": [{ "path": "class", "codeSystemUrl": "http://terminology.hl7.org/CodeSystem/v3-ActCode", "codeSystemName": "ActCode", "code": [{ "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode", "code": "AMB", "display": "ambulatory" }] }],
  "dateFilter": [{"path": "period"}]
}
```

### 測試驗證

- [x] `mvn compile -q` 通過
- [x] Measure 1 (DM_HbA1c_GA_Rate) DataRequirements 正確顯示 Encounter class → ActCode/AMB
- [x] Measure 2 (DM_FastingLipid_Rate) DataRequirements 同樣正確
- [x] 其他資源類型（Condition、MedicationRequest、Procedure）資料需求未受影響

---

## BUG-023 — Encounter.class 下拉選單顯示 1115 個代碼而非 11 個

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-21 |
| **功能分類** | Test Case Builder（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/fhir/FhirStructureDefinitionService.java` |
| **Commit** | [`3fc6de0`](../../commit/3fc6de0) |

### BUG 描述

Visual Builder 中新增 Encounter 資源時，`class` 欄位的下拉選單顯示 1115 個代碼（`_ActControlVariable`、`PDNPSPELAT`、`INFREG` 等無關代碼），使用者無法有效選擇正確的門診/住院/急診類別。

**根本原因**：`Encounter.class` 的 binding ValueSet 為 `v3-ActEncounterCode`，其定義使用 `is-a` 層級篩選器：

```xml
<compose>
  <include>
    <system value="http://terminology.hl7.org/CodeSystem/v3-ActCode"/>
    <filter>
      <property value="concept"/>
      <op value="is-a"/>
      <value value="_ActEncounterCode"/>
    </filter>
  </include>
</compose>
```

HAPI 的 `InMemoryTerminologyServerValidationSupport` 無法處理 CodeSystem 層級篩選器（`is-a`），在 `expandValueSet()` 時回傳整個 `v3-ActCode` CodeSystem（1115 個代碼），而非 `_ActEncounterCode` 的 11 個子代碼（AMB、EMER、IMP 等）。`expandValueSetCodes()` 直接信任 expansion 結果，未驗證結果合理性。

### 修正方式

- **`expandValueSetCodes()`**：新增 `MAX_BOUND_CODES = 80` 閾值。當 expansion 結果超過閾值且 compose 使用 filter 時，判定 expansion 失敗，改用手動層級解析
- **`resolveFilteredCodes()`**：遍歷 compose 中的 `is-a` 篩選器，為每個篩選器呼叫 `findDescendantCodes()` 並排除 `compose.exclude` 中的代碼
- **`findDescendantCodes()`**：透過 `validationSupport.fetchCodeSystem()` 取得 CodeSystem 定義，呼叫 `findConceptInHierarchy()` 遞迴定位祖先節點，再由 `collectDescendantCodes()` 收集所有子代代碼

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 後端重建部署成功
- [x] Encounter.class boundCodes: 11 個代碼（AMB, EMER, FLD, HH, IMP, ACUTE, NONAC, OBSENC, PRENC, SS, VR）
- [x] Encounter.status boundCodes: 9 個代碼（未受影響）
- [x] Condition/Observation/MedicationRequest 各欄位 boundCodes 數量正常（無迴歸）

---

## BUG-022 — 測試案例結果表格族群名稱未中文化

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | Test Cases（前端） |
| **嚴重程度** | Low |
| **根因類型** | i18n 遺漏 |
| **影響範圍** | `TestCaseResult.tsx`、`TestCasesTab.tsx`、`en/measures.json`、`zh-TW/measures.json` |
| **Commit** | [`0260852`](../../commit/0260852) |

### BUG 描述

測試案例執行結果表格中的族群名稱（`initial-population`、`denominator`、`numerator` 等）直接顯示英文原始 key，未經 i18n 翻譯。切換至中文介面時仍顯示英文。同樣地，測試案例列表的縮寫標籤（Ini、Den、Num）也是透過截取英文前三字元產生，在中文介面下無意義。

**根本原因**：

1. **`TestCaseResult.tsx`**（第 85 行）：直接輸出 `comp.populationType` 原始字串，未使用 `t()` 翻譯函數。同元件的 `TestCaseEditor.tsx` 已正確使用 `t('testCaseEditor.populationTypes.${key}')`。
2. **`TestCasesTab.tsx`**（第 253 行）：縮寫標籤使用 `key.replace(/-/g, ' ').replace(/\b\w/g, c => c.toUpperCase()).substring(0, 3)` 硬編碼截取英文前三字元，無 i18n。

### 修正方式

- **`TestCaseResult.tsx`**：`comp.populationType` → `t('testCaseEditor.populationTypes.${comp.populationType}', comp.populationType)`
- **`TestCasesTab.tsx`**：截取英文前三字元 → `t('testCaseEditor.populationTypesShort.${key}', key.substring(0, 3))`
- **`en/measures.json`** 及 **`zh-TW/measures.json`**：新增 `populationTypesShort` i18n keys（EN: Ini/Den/Num/DEx/DXp/NEx；zh-TW: 初始/分母/分子/母除/母外/子除）

### 測試驗證

- [x] `npx tsc --noEmit` TypeScript 編譯通過
- [x] Docker 前端重建部署成功
- [ ] 中文介面：結果表格顯示「初始族群」「分母」「分子」
- [ ] 中文介面：縮寫標籤顯示「初始」「分母」「分子」
- [ ] 英文介面：結果表格顯示「Initial Population」「Denominator」「Numerator」

---

## BUG-021 — DataRequirements 未解析 FHIRHelpers.ToString 包裝的屬性比較

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | eCQM 資料需求（後端） |
| **嚴重程度** | Medium |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `DataRequirementExtractor.java` |
| **Commit** | [`66a9ee2`](../../commit/66a9ee2) |
| **關聯** | #020 修正的後續問題 |

### BUG 描述

#020 新增了 Where 子句分析功能，但在實際 CQL（如 DM_HbA1c_GA_Rate 指標）上測試時，Condition 和 MedicationRequest 的 code system 篩選仍顯示「無篩選條件」。

**根本原因**：CQL-to-ELM 翻譯器在處理 FHIR 原生類型（`uri`, `code`, `string` 等）時，會自動插入 `FHIRHelpers.ToString()` 函數呼叫進行型別轉換。例如：

```
CQL:   Coding.system = 'http://hl7.org/fhir/sid/icd-10-cm'
ELM:   Equal(FunctionRef("FHIRHelpers.ToString", Property("system")), Literal("http://..."))
```

`extractSystemComparison()` 預期左運算元直接為 `Property` 節點，但實際上是 `FunctionRef` 包裝了 `Property`，導致比對失敗。

同時，`extractCodePathFromPropertyChain()` 在處理 scope-based 屬性鏈（如 `M.medication.coding`）時，只檢查最外層 Property 的 `scope` 屬性，但 scope 實際位於最內層 Property（例如 `Property(path="medication", scope="M")`），導致 MedicationRequest 的 `medication` code path 無法擷取。

### 修正方式

- **`extractSystemComparison()`**：新增 `unwrapToProperty()` 輔助方法，遞迴展開 `FunctionRef`、`As`、`Convert` 節點以取得底層的 `Property` 節點。在比較前先展開再檢查 `path`。
- **`extractCodePathFromPropertyChain()`**：追蹤迴圈中最後一個 `Property` 節點（`lastProperty`），在 scope 檢查時同時檢查最外層與最內層 Property 的 scope 屬性。

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 重建 → 呼叫 `/api/measures/1/data-requirements` 確認：
  - Condition: `code` → CodeSystem `http://hl7.org/fhir/sid/icd-10-cm` (ICD10CM)
  - MedicationRequest: `medication` → CodeSystem `http://www.whocc.no/atc` (ATC)
  - Encounter: date filter `period`
  - Procedure: date filter `performed`

---

## BUG-020 — DataRequirements 標籤頁未顯示 Where 子句中的篩選條件

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | eCQM 資料需求（前後端） |
| **嚴重程度** | Medium |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `DataRequirementExtractor.java`、`DataRequirementInfo.java`、`DataRequirementExtractorTest.java`、`DataRequirementsTab.tsx`、`types/index.ts`、`measures.json` (en/zh-TW) |
| **Commit** | [`8efa589`](../../commit/8efa589) |

### BUG 描述

Measures 頁面的「Data Requirements」標籤頁對所有資源類型皆顯示「無篩選條件」（No filters），包括明確帶有代碼篩選和日期篩選的 CQL 查詢（如 DM_HbA1c_GA_Rate 指標）。

**根本原因**：`DataRequirementExtractor` 僅處理 ELM Retrieve 節點中的 inline code filter（`[Condition: "Diabetes"]` 語法產生的 `Retrieve.codes = ValueSetRef`）。然而實際 CQL 普遍使用 bare Retrieve + Where clause 模式（`[Condition] C where exists(C.code.coding Y where Y.system = "url" ...)`），此模式產生的 ELM Retrieve 節點**不含** `codes` 欄位，代碼/日期篩選資訊全部位於外層 Query 的 `where` 子句中。Extractor 遞迴遍歷時僅檢查 Retrieve 節點本身，完全忽略 Query Where 子句，導致所有 bare Retrieve 被視為「無篩選條件」。

### 修正方式

- **`DataRequirementInfo.java`**：`CodeFilterInfo` 新增 `codeSystemUrl`（代碼系統 URL）和 `codeSystemName`（代碼系統名稱）欄位
- **`DataRequirementExtractor.java`**：
  - 新增 `buildCodeSystemMap(root)` 擷取 `library.codeSystems.def[]` 的 name→URL 對應
  - `collectRetrieves()` 改為識別 Query 節點：從 `source[0].expression` 擷取 Retrieve、取得 alias，避免重複計算
  - 新增 `handleQuery()` 處理 Query 節點：解析 source 中的 Retrieve 並呼叫 Where 分析
  - 新增 `enhanceFromWhere()` / `walkWhereClause()` 遞迴 AST walker，處理以下 ELM 模式：
    - `And`/`Or`/`Not` 邏輯連接詞 → 遞迴
    - `Exists` → 內部 Query（`C.code.coding Y where Y.system = "url"`）→ 擷取 CodeSystem URL 及 code path
    - `Overlaps`/`During`/`IncludedIn` 等日期比較 + `ParameterRef`（Measurement Period）→ 擷取 date filter path
    - `FunctionRef` 包裝（如 `NormalizeInterval(P.performed)`）→ 展開後擷取 property path
    - `InValueSet` → 擷取 ValueSet 參照
  - Dedup key 納入 `codeSystemUrl`，避免不同 CodeSystem 的同類型 Retrieve 被錯誤合併
  - 使用 `handledRetrieves` Set 追蹤已處理的 Retrieve 節點，防止 Query source 中的 Retrieve 被遞迴重複擷取
- **`DataRequirementExtractorTest.java`**：新增 7 個測試案例（exists+CodeSystem、Overlaps date filter、FunctionRef 包裝、combined filters、mixed inline/Where、dedup、And wrapper）
- **`types/index.ts`**：前端 `CodeFilterInfo` 新增 `codeSystemUrl?` 和 `codeSystemName?`
- **`DataRequirementsTab.tsx`**：當 code filter 有 `codeSystemUrl`（無 valueSet）時顯示 CodeSystem chip + 名稱 + URL；summary chip 計數納入 codeSystem filters
- **i18n**：EN `"codeSystem": "Code System"`、zh-TW `"codeSystem": "代碼系統"`

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] `mvn test -Dtest=DataRequirementExtractorTest` 全部 17 個測試通過
- [x] `npx tsc --noEmit` TypeScript 編譯通過
- [x] Docker 重建 → DataRequirements 標籤頁顯示 Condition(code: ICD-10-CM)、MedicationRequest(medication: ATC)、Encounter(period: date filter) 等篩選條件（於 #021 修正後驗證通過）

---

## BUG-019 — CDS Prefetch 執行清除 patientId 導致 Patient context 失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cds/CdsInvocationService.java` |
| **Commit** | [`c08372c`](../../commit/c08372c) |

### BUG 描述

CDS Sandbox 中使用 prefetch 資料執行含有 Patient context 表達式的 CQL 規則時，所有 Patient-context 表達式（如 `[Condition: "Diabetes"]`）回傳空集合，導致 `Has Diabetes` 永遠為 false，CDS Card 顯示 "No recommendations at this time."。

**根本原因**：#008 的修正中，為繞過 CQL engine 的 post-retrieval context filtering（`Reference.equals(String)` 類型不匹配），在使用 prefetch provider 時設定 `execRequest.setPatientId(null)`。然而 CQL engine 3.29.0 的 `RetrieveEvaluator` 實際上**不會**對 retrieve 結果做 post-filtering——它直接將 contextPath/contextValue 傳遞給 `DataProvider.retrieve()`，由 RetrieveProvider 決定如何處理。設定 patientId 為 null 反而導致 CQL engine 無法建立 Patient context，使所有 Patient-context 表達式（define 語句中隱含的 `context Patient`）無法正確評估，retrieve 呼叫缺少 contextValue 而回傳空結果。

**與 #008 的關係**：#008 的修正 2（`setPatientId(null)`）基於錯誤假設——認為 CQL engine 會對 retrieve 結果做 `Reference.equals(String)` post-filtering。經 CQL engine v3.29.0 bytecode 分析確認 engine 不做此過濾，#008 修正 1（`ensureSubjectReference`）才是正確修正，修正 2 為多餘且有害的 workaround。

### 修正方式

- **`CdsInvocationService.java`**：移除 `execRequest.setPatientId(null)`，保留原始 patientId 以便 CQL engine 建立正確的 Patient context。新增註解說明保留 patientId 的原因

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Docker 重建並部署成功
- [x] CDS Sandbox DM 服務 → `Has Diabetes = true`、`Needs HbA1c Test = true`（CDS Card 正確顯示）
- [x] PrefetchRetrieveProvider 日誌確認 `contextValue=Patient/test-patient-1`（非 null）
- [x] Debug CQL 表達式驗證：`Diabetes Count = 1`、`All Conditions = [Condition]`

---

## ~~#018~~ — FHIR Coding→Code 轉換 **已撤回**

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CQL Engine（後端） |
| **嚴重程度** | — (已撤回) |
| **根因類型** | 誤判 |
| **影響範圍** | `ComparableR4FhirModelResolver.java` |
| **Commit** | [`878deef`](../../commit/878deef) → reverted |

### 撤回原因

原本認為 CQL Engine 的 `Contains` 評估器不會對 FHIR Coding 套用隱式轉換。實際測試後發現 CQL Translator 已在 ELM 中內嵌 `FHIRHelpers.ToCode(FHIR.Coding)` 呼叫。在 Model Resolver 中提前將 Coding→Code 反而導致執行時簽名不匹配：`Could not resolve call to operator 'ToCode(org.opencds.cqf.cql.engine.runtime.Code)' in library 'FHIRHelpers'`。

原始 `Has Diabetes = false` 的真正根因為 #016（CodeSystem URL 錯誤）和 #017（ValueSet 未展開），非型別轉換問題。

---

## BUG-017 — PrefetchRetrieveProvider 未展開 ValueSet 導致代碼過濾失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `PrefetchRetrieveProvider.java`、`CqlExecutionService.java` |
| **Commit** | [`878deef`](../../commit/878deef) |

### BUG 描述

CDS Sandbox 中 ValueSet 基礎的 CQL 檢索（如 `[Condition: "Diabetes"]`，其中 "Diabetes" 為 VSAC ValueSet）未進行代碼過濾。CQL Engine 將 ValueSet URL 傳遞給 `RetrieveProvider.retrieve()` 的 `valueSet` 參數（`codes` 為 null），但 `PrefetchRetrieveProvider` 完全忽略 `valueSet` 參數，回傳所有該類型的資源而不做代碼篩選。

**根本原因**：`PrefetchRetrieveProvider` 是為 CDS Hooks prefetch 設計的簡易記憶體 RetrieveProvider，僅處理 `codes` 參數（已展開的代碼清單），未處理 `valueSet` 參數（ValueSet URL）。CQL Engine 3.29.0 期望 DataProvider 自行處理 ValueSet 展開，而非由 Engine 預先展開後傳遞 codes。

### 修正方式

- **`PrefetchRetrieveProvider.java`**：
  - 新增 `TerminologyProvider terminologyProvider` 欄位及 `setTerminologyProvider()` 方法
  - `retrieve()` 方法：當 `codes=null` 且 `valueSet` 非空時，呼叫 `terminologyProvider.expand(new ValueSetInfo().withId(valueSet))` 展開 ValueSet 取得代碼清單
  - 日誌新增 `valueSet` 參數輸出
- **`CqlExecutionService.java`**：在 `doExecute()` 中，當使用 PrefetchRetrieveProvider 時，自動注入已建立的 TerminologyProvider

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] CDS Sandbox `[Condition: "Diabetes"]` retrieve → 正確展開 VSAC ValueSet 並過濾 Condition
- [x] 無 ValueSet 的 retrieve → 行為不變
- [x] TerminologyProvider 不可用時 → graceful fallback（回傳所有資源）

---

## BUG-016 — CodeableConcept dropdown 使用 ValueSet URL 而非 CodeSystem URL

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | Test Case Builder（前後端） |
| **嚴重程度** | High |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `ElementMetadata.java`、`FhirStructureDefinitionService.java`、`CodeableConceptField.tsx`、`ChoiceTypeField.tsx`、`types/index.ts` |
| **Commit** | [`6ca7a86`](../../commit/6ca7a86) |

### BUG 描述

#014 新增的 CodeableConcept dropdown 在使用者選擇代碼後，產生的 coding `system` 為 **ValueSet URL**（如 `http://hl7.org/fhir/ValueSet/condition-clinical`），而非正確的 **CodeSystem URL**（`http://terminology.hl7.org/CodeSystem/condition-clinical`）。

CQL 中 `C.clinicalStatus.coding contains "Active"`（其中 `"Active"` 的 system 為 CodeSystem URL）會比對 code + system，因 system 不匹配導致永遠回傳 false。即使使用者在 dropdown 選擇了 "active"，CQL 仍判定 `Has Diabetes` = false → CDS Card 顯示 "No recommendations at this time"。

**根本原因**：`ElementMetadata` 僅提供 `bindingValueSetUrl`（ValueSet URL），未提供 CodeSystem URL。`CodeableConceptField` 直接以 `bindingValueSetUrl` 作為 coding system，但 ValueSet URL ≠ CodeSystem URL。

### 修正方式

- **`ElementMetadata.java`**：新增 `bindingCodeSystemUrl` 欄位
- **`FhirStructureDefinitionService.java`**：`expandValueSetCodes()` 重構為回傳 `ExpandedCodes` record（含 codes + codeSystemUrl）。從 ValueSet 的 `compose.include[].system` 和 expansion `contains[].system` 提取 CodeSystem URL
- **`types/index.ts`**：前端 `ElementMetadata` 新增 `bindingCodeSystemUrl` 欄位
- **`CodeableConceptField.tsx`**：dropdown 產生 coding 時優先使用 `bindingCodeSystemUrl`，fallback 為 `bindingValueSetUrl`
- **`ChoiceTypeField.tsx`**：合成 ElementMetadata 物件新增 `bindingCodeSystemUrl: null`

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] `tsc --noEmit` 編譯通過
- [x] Docker 重建並部署成功
- [x] clinicalStatus 選 "active" → JSON coding system 為 `http://terminology.hl7.org/CodeSystem/condition-clinical`
- [x] CDS Sandbox DM CQL → `Has Diabetes` 正確判定為 true

---

## BUG-015 — VSAC ValueSet 未連接 CQL Engine 導致 CDS 規則失效

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CQL Engine（後端） |
| **嚴重程度** | High |
| **根因類型** | 架構缺陷 |
| **影響範圍** | `LocalTerminologyProvider.java`、`FhirTerminologyService.java`、`VsacService.java`、`SettingsController.java`、`SecurityConfig.java` |
| **Commit** | [`69dd9a1`](../../commit/69dd9a1) |

### BUG 描述

CDS Sandbox 測試含有 VSAC ValueSet 參照（如 `http://cts.nlm.nih.gov/fhir/ValueSet/2.16.840.1.113883.3.464.1003.103.12.1001`）的 CQL 規則時，CQL Engine 無法解析 ValueSet，導致 `in` 運算子永遠回傳 false，CDS Card 顯示「No recommendations at this time」。

**根本原因**：`VsacService` 已存在且 API Key 已設定，但僅被 `FhirController` 的 REST API 使用。CQL Engine 的執行路徑為 `CqlExecutionService` → `FhirTerminologyService.createTerminologyProvider()` → `LocalTerminologyProvider(igService, R4FhirTerminologyProvider(tx.fhir.org))`，完全未接入 `VsacService`。當 ValueSet URL 為 `cts.nlm.nih.gov` 時，`tx.fhir.org` 無法解析，導致 fallback 失敗。

### 修正方式

- **`LocalTerminologyProvider.java`**：新增 `VsacService` 為可選依賴。`in()` 和 `expand()` 方法新增 VSAC 層：當 ValueSet URL 包含 `cts.nlm.nih.gov` 時，提取 OID 並委派 `VsacService.expandValueSetByOid()` 解析
- **`FhirTerminologyService.java`**：注入 `VsacService`（`@Autowired(required = false)`），傳遞至 `LocalTerminologyProvider`。Terminology 解析鏈改為：Local IG → VSAC → Remote (tx.fhir.org)
- **`VsacService.java`**：新增 `isConfigured()`、`getApiUrl()`、`updateApiKey()` 方法，支援執行時更新 API Key
- **`SettingsController.java`**（新增）：`GET /api/settings/vsac-status` 查詢 VSAC 狀態；`PUT /api/settings/vsac-api-key` 更新 API Key（ADMIN 限定）
- **`SecurityConfig.java`**：`PUT /api/settings/**` 限制為 ADMIN 角色
- **前端 `PreferencesDialog.tsx`**：新增「術語服務」區段，顯示 VSAC 連線狀態（Chip 指示已設定/未設定）、伺服器 URL、API Key 輸入與更新按鈕
- **i18n**：en/zh-TW 各新增 14 個 `preferences.vsac*` 鍵值

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] `tsc --noEmit` 編譯通過
- [x] CDS Sandbox 含 VSAC ValueSet 的 CQL 規則 → CQL Engine 正確解析 ValueSet
- [x] PreferencesDialog 顯示 VSAC 狀態和 API Key 設定
- [x] 更新 API Key 後 VSAC 狀態切換為「已設定」

---

## BUG-014 — CodeableConcept boundCodes 未使用下拉選單

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | Test Case Builder（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/testcase-builder/CodeableConceptField.tsx` |
| **Commit** | [`69dd9a1`](../../commit/69dd9a1) |

### BUG 描述

Visual Builder 中 `clinicalStatus`（type `CodeableConcept`）等帶有 `boundCodes` 的欄位，仍顯示為完整的 coding 複合編輯器（system + code + display 三欄 + TWCORE 瀏覽按鈕 + 新增 coding 按鈕 + text 欄位）。一般使用者不知道需要填入完整的 coding 物件才能使 CQL 正確判斷。

**根本原因**：`CodeField.tsx`（primitive `code` type）已有 `boundCodes` 下拉邏輯（#003），但 `CodeableConceptField.tsx`（complex `CodeableConcept` type）從未實作此功能。後端 `FhirStructureDefinitionService` 已正確回傳 `boundCodes: ["active", "recurrence", "relapse", "inactive", "remission", "resolved"]`，但前端忽略此資訊。

### 修正方式

- **`CodeableConceptField.tsx`**：在元件頂部新增 `hasBoundCodes` 判斷分支。當 `element.boundCodes.length > 0` 時，渲染 MUI `Select` 下拉選單取代複合編輯器。選擇後自動生成完整的 `CodeableConcept` 結構（含 `coding[0].system`、`code`、`display`）
- 新增 `FormControl`、`InputLabel`、`Select`、`MenuItem` imports

### 測試驗證

- [x] `tsc --noEmit` 編譯通過
- [x] Condition clinicalStatus 顯示為下拉選單（active/inactive/resolved 等）
- [x] 選擇後 JSON 正確生成 `{ coding: [{ system: "...", code: "active", display: "Active" }] }`
- [x] 無 boundCodes 的 CodeableConcept 欄位仍顯示完整 coding 編輯器

---

## BUG-013 — TWCORE 選碼導致 Monaco Editor 白屏

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CQL Builder（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `frontend/src/components/builder/CqlPreviewBox.tsx` |
| **Commit** | [`4c9ae86`](../../commit/4c9ae86) |

### BUG 描述

在 CQL Builder 的「代碼」區段使用「瀏覽 TWCORE」功能選擇代碼（如糖尿病 SNOMED CT 代碼）後，左側 Monaco Editor 突然從深色主題（`cql-theme-dark`）變為白色背景。問題僅在首次觸發 TWCORE 選碼時發生，之後主題即永久被覆寫為 light。

**根本原因**：`CqlPreviewBox` 使用 `import * as monaco from 'monaco-editor'`（Vite 打包的本地實例），而主編輯器的 `CqlEditor` 使用 `@monaco-editor/react`（透過 `@monaco-editor/loader` 從 CDN 載入 Monaco）。兩者為**不同的 Monaco 實例**。當 `CqlPreviewBox` 首次渲染時，本地打包的 `monaco-editor` 模組初始化，將全域 Monaco 主題重設為預設的 `'vs'`（白色），覆寫了 CDN 實例已設定的 `'cql-theme-dark'`。

### 修正方式

- **移除直接 import**：`import * as monaco from 'monaco-editor'` → `import { useMonaco } from '@monaco-editor/react'`
- **使用 `useMonaco()` hook**：取得與 `CqlEditor` 相同的 CDN Monaco 實例，確保 CQL 語言和主題均已註冊
- **呼叫 `colorize()` 前設定主題**：根據 `preferences.themeMode` 明確設定 `cql-theme-dark` 或 `cql-theme`，確保語法著色使用正確的色彩
- **深色模式適配**：preview box 背景色和 `mtk1` 文字色根據主題模式切換

### 測試驗證

- [x] TypeScript 編譯通過
- [x] 深色模式下選擇 TWCORE 代碼 → Monaco Editor 維持深色背景
- [x] 淺色模式下選擇 TWCORE 代碼 → Monaco Editor 維持淺色背景
- [x] 代碼預覽框（SnippetPreview）語法著色正確

---

## BUG-012 — Monaco Editor 夜間模式白屏

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | 跨模組（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `SandboxPanel.tsx`、`ElementField.tsx`、`ResourceEditorDialog.tsx`、`TransactionTab.tsx`、`ValidateTab.tsx`、`TestCaseEditor.tsx` |
| **Commit** | [`e375b1e`](../../commit/e375b1e) |

### BUG 描述

切換至夜間模式（Dark Mode）後，除 CQL Editor 以外的所有 Monaco Editor 實例仍顯示白色背景（light theme），與深色介面形成強烈對比，影響視覺一致性及可讀性。CQL Editor 正確使用自訂的 `cql-theme-dark`，但其餘 6 個 JSON editor 未設定 `theme` prop，Monaco 預設使用 light theme。

**受影響位置**：
1. CDS Sandbox — JSON (Prefetch) 編輯器
2. Test Case Builder — 深層 JSON fallback 編輯器
3. FHIR Browser — Resource Editor Dialog
4. FHIR Browser — Transaction Tab
5. FHIR Browser — Validate Tab
6. Measures — Test Case Editor（JSON Advanced 分頁）

### 修正方式

- 6 個檔案各加入 `useTheme` hook，並在 `<Editor>` 元件上設定 `theme={theme.palette.mode === 'dark' ? 'vs-dark' : 'light'}`
- JSON editor 使用 Monaco 內建的 `vs-dark` / `light` theme（不需自訂 theme）

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Frontend Docker 重建並部署成功
- [x] 夜間模式下所有 Monaco Editor 顯示深色背景
- [x] 日間模式下維持淺色背景不變

---

## BUG-011 — Footer 位置異常：flexbox 佈局修正

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | 版面配置（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/App.tsx`、`frontend/src/constants/layout.ts` |
| **Commit** | [`d82710d`](../../commit/d82710d) |

### BUG 描述

Footer（CQL 規範、CDS Hooks、FHIR 連結）卡在畫面中間，無法固定在視窗底部。此問題在 #005 和 #007 中已嘗試修正，但 `PAGE_CONTENT_HEIGHT = calc(100vh - 48px)` 僅扣除 Header 高度（且 MUI Toolbar 預設高度為 64px 非 48px），未扣除 Footer 高度，導致 Footer 被推至 viewport 外或浮在內容中間。

### 修正方式

- **`App.tsx`**：外層 Box 改用 `display: flex`, `flexDirection: column`, `height: 100vh`，Main 區域設為 `flex: 1, overflow: auto`，Footer 自然固定在 viewport 底部
- **`layout.ts`**：`PAGE_CONTENT_HEIGHT` 從 `calc(100vh - 48px)` 改為 `100%`（相對於 flex 分配的 main 空間）

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Frontend Docker 重建並部署成功
- [x] Footer 固定在 viewport 底部，不隨內容滾動
- [x] 各頁面內容可正常捲動

---

## BUG-010 — CDS Card 顯示所有表達式擠在一行

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | CDS Hooks Sandbox（前後端） |
| **嚴重程度** | Low |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `backend/.../CqlTupleCardStrategy.java`、`frontend/.../SandboxPanel.tsx` |
| **Commit** | [`5e69d32`](../../commit/5e69d32) |

### BUG 描述

CDS Sandbox 的結果卡片將所有 CQL 表達式（LatestBMI、BMICalue、BMIClassification、CardSuggestion）全部串接在同一行顯示，包含冗長的 FHIR Resource 物件序列化文字。問題有二：

1. **後端**：`CqlTupleCardStrategy` 將所有非 null 表達式（包含 FHIR Resource 物件）統一格式化為 `**key**: value` 並換行串接，未區分主要訊息與中間計算值
2. **前端**：`SandboxPanel.tsx` 以純文字 `<Typography>` 渲染 card detail，不支援換行 (`\n`) 和 Markdown 粗體 (`**text**`)

### 修正方式

- **`CqlTupleCardStrategy.java`**：新增「主要訊息偵測」邏輯 — 表達式名稱含 Suggestion / Summary / Message / Recommendation 且為 String 時，作為 card 主要 detail；FHIR Resource 物件從合併卡片中排除（過於冗長）；其餘標量值作為補充資訊
- **`SandboxPanel.tsx`**：card detail 加入 `whiteSpace: 'pre-line'` 支援換行，並以 `dangerouslySetInnerHTML` + regex 將 `**text**` 轉為 `<strong>text</strong>`

### 測試驗證

- [x] TypeScript 編譯通過、`mvn compile -q` 通過
- [x] Docker 重建並部署成功
- [x] BMI CDS card 主要顯示 CardSuggestion 訊息，BMIClassification 等補充值分行顯示
- [x] FHIR Resource 物件不再出現在卡片文字中

---

## BUG-009 — FHIR Choice Type 序列化錯誤（value → valueQuantity）

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-20 |
| **功能分類** | Test Case Builder（前端） |
| **嚴重程度** | High |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `frontend/src/components/testcase-builder/ChoiceTypeField.tsx`、`ElementField.tsx`、`ResourceForm.tsx` |
| **Commit** | [`5e69d32`](../../commit/5e69d32) |

### BUG 描述

Visual Builder 中 FHIR Choice Type 欄位（如 `Observation.value[x]`）的值被序列化為基礎名稱 `"value"` 而非帶類型後綴的 `"valueQuantity"`。HAPI FHIR parser 以 lenient 模式忽略未知的 `"value"` 欄位（日誌警告 `Unknown element 'value' found while parsing`），導致 Observation 無值 → CQL `obs.value is not null` 過濾掉所有資源 → 表達式回傳 null。

**根本原因**：`ChoiceTypeField` 正確建立合成元素名稱 `"valueQuantity"`（第 26 行），但將原始 `onChange` 直接傳遞給子 `ElementField`。`ResourceForm.handleFieldChange` 從 path `"Observation.value"` 擷取欄位名稱 `"value"` 儲存至 `resourceData`。`serializeToBundle` 直接複製 `resourceData` → JSON 產生 `"value"` 而非 `"valueQuantity"`。

### 修正方式

- **`ChoiceTypeField.tsx`**：onChange 包裝為 `(val) => onChange(val, syntheticElement.name)`，傳遞正確的帶類型欄位名（如 `"valueQuantity"`）；新增 `initialChoiceType` prop 支援從 JSON 載入時偵測已選類型
- **`ElementField.tsx`**：onChange 簽名擴展為 `(value, choiceFieldName?) => void`；新增 `initialChoiceType` prop 傳遞至 ChoiceTypeField
- **`ResourceForm.tsx`**：
  - `handleFieldChange` 新增 `choiceFieldName` 參數：收到時先清除所有 choice type 變體（如 `valueQuantity`、`valueString` 等），再以正確 key 儲存
  - `getFieldValue` 支援 choice type 查詢：遍歷 `choiceTypes` 尋找帶類型後綴的 key
  - `getSelectedChoiceType` 偵測既有資料的 choice type（用於 JSON ↔ Visual Builder 雙向同步）
  - `filledOptionalNames` 支援 choice type 變體匹配（如 `"valueQuantity"` 對應元素 `"value"`）

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Frontend Docker 重建並部署成功
- [x] Visual Builder 設定 Observation value[x] = Quantity → JSON 序列化為 `"valueQuantity"`
- [x] HAPI parser 不再產生 `Unknown element 'value'` 警告
- [x] CDS Sandbox invoke → 所有 CQL 表達式正確回傳值

---

## BUG-008 — CDS Sandbox Invoke 所有 CQL 表達式回傳 null

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CDS Hooks Sandbox（後端） |
| **嚴重程度** | High |
| **根因類型** | 資料處理錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cds/CdsInvocationService.java`、`backend/src/main/java/com/cqlplatform/service/cds/PrefetchRetrieveProvider.java` |
| **Commit** | [`fccd012`](../../commit/fccd012)、[`5e69d32`](../../commit/5e69d32) |

### BUG 描述

在 CDS Sandbox 中使用 Visual Builder 建立 Observation（例如 BMI: LOINC 39156-5, value 27 kg/m2）並 Invoke 時，CDS card 顯示 "No recommendations at this time."，所有 CQL 表達式（`LatestBMI`、`BMICalue`、`BMIClassification`、`CardSuggestion`）皆回傳 null。

**根本原因（雙重）**：

1. **Subject 缺失**：Visual Builder 建立的 Observation 預設不帶 `subject` 引用，CQL engine 的 context filter 靜默排除無 subject 的資源
2. **CQL engine context filter 類型比對失敗**：CQL engine 3.29.0 的 `RetrieveEvaluator` 執行 post-retrieval context filtering 時，將 FHIR `Reference` 物件與 String `"Patient/test-patient-1"` 做 `equals()` 比較，`Reference.equals(String)` 永遠回傳 false，導致所有資源被靜默排除

### 修正方式

- **`CdsInvocationService.java`**（修正 1 — subject 自動填充）：在 `buildPrefetchProvider()` 解析 prefetch 資源後，`ensureSubjectReference()` 自動為缺少 `subject`/`patient` 引用的資源補上 `Patient/{patientId}`。覆蓋 12 種 FHIR 資源型別
- **`CdsInvocationService.java`**（~~修正 2 — 繞過 context filter~~）：~~當使用 prefetch provider 時，設定 `execRequest.setPatientId(null)` 繞過 CQL engine 的 post-retrieval context filtering~~ ⚠️ **此修正為 #019 的根因，已在 [`c08372c`](../../commit/c08372c) 中撤回**
- **`PrefetchRetrieveProvider.java`**：將 retrieve 日誌從 DEBUG 提升到 INFO，記錄完整的 retrieve 參數及過濾結果

### 測試驗證

- [x] `mvn compile -q` 編譯通過
- [x] Backend Docker 重建並部署成功
- [x] PrefetchRetrieveProvider 正確回傳 1 筆 Observation（code filter 正常）
- [x] CQL 表達式（BMIClassification=Overweight, CardSuggestion=BMI:25 'kg/m2'...）正確回傳值
- [x] 預設 sandbox 測試資料行為不變

---

## BUG-007 — Footer fixed 定位仍遮擋操作按鈕

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | 版面配置（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/layout/Footer.tsx`、`frontend/src/App.tsx`、`frontend/src/constants/layout.ts` |
| **Commit** | [`b570119`](../../commit/b570119) |

### BUG 描述

#005 將 Footer 改為 `position: fixed` 後，Footer 仍然浮動覆蓋在頁面內容上方。在 CDS Sandbox Visual Builder 等內容較長的頁面中，底部的操作按鈕（如 Invoke in Sandbox）被 Footer 的 CQL 規範、CDS Hooks、FHIR 連結遮擋，無法點擊。根本原因是 fixed 定位的 Footer 永遠浮在頁面最下方，與 `pb: 36px` 留白方案在捲動式面板中無法完全避免遮擋。

### 修正方式

- **Footer.tsx**：移除 `position: fixed`、`bottom: 0`、`left: 0`、`right: 0`、`zIndex`，改為 `flexShrink: 0` 讓 Footer 回到正常文件流
- **App.tsx**：移除 `pb: '36px'`（不再需要為 fixed footer 預留空間），改為 `minHeight: 0` 確保 flex 子元素正確收縮
- **layout.ts**：`PAGE_CONTENT_HEIGHT` 從 `calc(100vh - 120px)` 調整為 `calc(100vh - 156px)`，多扣除 Footer 的 36px 高度

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Footer 緊貼在頁面內容下方，不遮擋任何操作按鈕
- [x] CDS Sandbox Visual Builder 的 Invoke 按鈕可正常點擊
- [x] 各頁面（Editor、CDS、Measures、FHIR、Authoring）版面正常

---

## BUG-006 — Backend OOM 導致所有 API 無回應

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | Backend 基礎設施 |
| **嚴重程度** | Critical |
| **根因類型** | 配置遺漏 |
| **影響範圍** | `docker/Dockerfile.backend`、`backend/Dockerfile` |
| **Commit** | [`660347a`](../../commit/660347a) |

### BUG 描述

Backend 容器記憶體上限 1GB，JVM 未設定 heap 大小，預設僅使用 ~256MB。在 CQL 翻譯、FHIR 資源處理等記憶體密集操作後觸發 `java.lang.OutOfMemoryError: Java heap space`，導致所有 HTTP 請求（包括登入）完全無回應。

### 修正方式

- Dockerfile 的 ENTRYPOINT 加入 JVM 參數：`-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC`
- MaxRAMPercentage=75.0 讓 JVM 使用容器 75% 記憶體作為 heap（~768MB）
- G1GC 垃圾回收器更適合大 heap 場景

### 測試驗證

- [x] Backend Docker 重建並部署成功
- [x] 重啟後登入功能恢復正常
- [x] 容器穩定運行無 OOM

---

## BUG-005 — Footer 覆蓋頁面內容

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | 版面配置（前端） |
| **嚴重程度** | Low |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/layout/Footer.tsx`、`frontend/src/App.tsx` |
| **Commit** | [`741b7dc`](../../commit/741b7dc) |

### BUG 描述

Footer（CQL 規範、CDS Hooks、FHIR 連結）未固定在畫面底部，隨頁面滾動時會覆蓋到其他內容，特別是在 CDS Sandbox 等內容較多的頁面。

### 修正方式

- Footer 加入 `position: fixed; bottom: 0` 固定在畫面底部
- App.tsx 的 main content 加入 `pb: 36px` 底部留白，避免內容被 Footer 遮擋

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Footer 固定在畫面最下方，不隨頁面滾動
- [x] 頁面內容不被 Footer 遮擋
- [x] Frontend Docker 重建並部署成功

---

## BUG-004 — CDS Sandbox 修改資料後無法重新執行

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CDS Hooks Sandbox（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `frontend/src/components/cds/SandboxPanel.tsx` |
| **Commit** | [`741b7dc`](../../commit/741b7dc) |

### BUG 描述

在 CDS Sandbox 中修改 Visual Builder 的欄位值後，點擊「Invoke in Sandbox」按鈕，結果可能顯示舊資料。原因有二：

1. 舊的 `sandboxResponse` 未清除，使用者無法區分新舊結果
2. Visual Builder 的狀態變更可能未即時同步到 `testDataJson`，導致送出的是修改前的資料

### 修正方式

- 點擊 Invoke 時先 `setSandboxResponse(null)` 清除舊結果
- 若處於 Visual Builder 分頁，在送出前強制重新序列化 `state.entries` → prefetch JSON

### 測試驗證

- [x] TypeScript 編譯通過
- [x] 修改 Visual Builder 欄位後重新 Invoke → 結果正確更新
- [x] 連續多次 Invoke → 每次都能正常執行
- [x] Frontend Docker 重建並部署成功

---

## BUG-003 — Observation status 欄位允許自由輸入導致無效值

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | Test Case Builder / CDS Sandbox（前端） |
| **嚴重程度** | Medium |
| **根因類型** | UX 設計缺陷 |
| **影響範圍** | `frontend/src/components/testcase-builder/CodeField.tsx` |
| **Commit** | [`741b7dc`](../../commit/741b7dc) |

### BUG 描述

Visual Builder 中 Observation 的 `status` 欄位使用 Autocomplete（freeSolo），允許使用者自由輸入任意值。使用者容易誤填（如填入 LOINC code `39156-5` 而非 `final`），導致產生的 FHIR 資源不合法，CQL 執行時找不到 Observation。此外，status 旁的 TWCORE 術語瀏覽按鈕對標準 FHIR 值集無意義。

### 修正方式

- 當 `bindingStrength === "required"` 且 `boundCodes` 存在時，改用 MUI `Select` 下拉選單取代 freeSolo Autocomplete
- Required binding 情境下隱藏 TWCORE 按鈕
- 其他欄位（如 CodeableConcept 的 code）保留 TWCORE 按鈕不變

### 測試驗證

- [x] TypeScript 編譯通過
- [x] Observation status 顯示為下拉選單（final、preliminary、amended 等）
- [x] status 旁不再顯示 TWCORE 按鈕
- [x] code 欄位的 TWCORE 按鈕仍正常顯示
- [x] Frontend Docker 重建並部署成功

---

## BUG-002 — CQL 翻譯失敗時 Builder 無法顯示已解析的部分結構

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CQL Translation（後端）+ CQL Builder（前端） |
| **嚴重程度** | Medium |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `backend/src/main/java/com/cqlplatform/service/cql/CqlTranslationService.java`、`frontend/src/hooks/useCqlStructure.ts` |
| **Commit** | [`f9b3e33`](../../commit/f9b3e33) |

### BUG 描述

CQL 內容含有任何翻譯錯誤時，後端 `CqlTranslationService.translate()` 在 `errors` 非空時直接回傳 response，完全不包含 `metadata`。即使 CQL translator 已成功解析部分結構（如 valueset、code、define），這些資訊也不會回傳給前端。

導致 Builder 面板在 CQL 有任何錯誤時完全無法顯示已解析的結構，使用者只能看到錯誤訊息而無法利用已正確的部分。

### 修正方式

- **後端**：將 `extractMetadata(library)` 提前至錯誤檢查之前執行，即使有錯誤也回傳 partial metadata
- **前端**：`useCqlStructure` hook 將 `if...else if` 改為兩個獨立 `if`，允許同時更新 structure 和顯示 parseError

### 測試驗證

- [x] 後端 `mvn compile -q` 編譯通過
- [x] 前端 `tsc --noEmit` 編譯通過
- [x] CQL 有錯誤時 → Builder 同時顯示已解析的結構 + 錯誤訊息
- [x] CQL 無錯誤時 → 行為不變，完整顯示結構
- [x] Backend + Frontend Docker 重建並部署成功

---

## BUG-001 — CQL Builder 解析 CQL 靜默失敗

| 欄位 | 內容 |
|------|------|
| **日期** | 2026-02-19 |
| **功能分類** | CQL Builder（前端） |
| **嚴重程度** | High |
| **根因類型** | 邏輯錯誤 |
| **影響範圍** | `frontend/src/hooks/useCqlStructure.ts`、`frontend/src/components/builder/CqlBuilderPanel.tsx` |
| **Commit** | [`3ee28f8`](../../commit/3ee28f8) |

### BUG 描述

使用 CQL Builder 面板解析 CQL 時，若 CQL 內容含有語法錯誤，Builder 不會顯示任何錯誤訊息，結構面板維持空白狀態。具體問題有二：

1. **靜默失敗**：`/api/cql/translate` 回傳 `success: false` 及 `errors` 時，`useCqlStructure` hook 僅檢查 `result.metadata`（錯誤時為 `null`），未將翻譯錯誤設入 `parseError`，導致 Builder 面板無任何回饋。
2. **快取阻擋重試**：`lastParsedContent.current = cql` 在翻譯失敗時仍被設定，導致內容未變更時點擊「Parse CQL」按鈕無法重新解析。

### 修正方式

- `lastParsedContent` 僅在 metadata 成功取得時設定
- 當 `result.success === false` 時，取前 3 筆翻譯錯誤組成訊息，設入 `parseError` 狀態
- Builder 面板 Alert 加入 `whiteSpace: 'pre-line'` 支援多行錯誤顯示

### 測試驗證

- [x] TypeScript 編譯通過（`tsc --noEmit` 零錯誤）
- [x] 輸入有語法錯誤的 CQL → Builder 顯示錯誤訊息
- [x] 修正 CQL 語法後 → Builder 自動重新解析並顯示結構
- [x] 點擊「Parse CQL」按鈕可強制重新解析
- [x] Frontend Docker 重建並部署成功

---

<!--
## 範本

## #00X — 簡短標題

| 欄位 | 內容 |
|------|------|
| **日期** | YYYY-MM-DD |
| **功能分類** | 分類名稱（前端/後端/API） |
| **嚴重程度** | Critical / High / Medium / Low |
| **根因類型** | 邏輯錯誤 / UX 設計缺陷 / 配置遺漏 / 資料處理錯誤 / 併發/效能問題 |
| **影響範圍** | 受影響的檔案路徑 |
| **Commit** | [`xxxxxxx`](../../commit/xxxxxxx) |

### BUG 描述

問題的詳細描述。

### 修正方式

修正的具體做法。

### 測試驗證

- [ ] 測試項目 1
- [ ] 測試項目 2
-->

---
