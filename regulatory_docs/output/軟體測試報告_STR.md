# 軟體測試報告 (Software Test Report)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-STR-1.0.0 |
| 版本 | 1.0.0 |
| 日期 | 2026-04-23 |
| 產品名稱 | CQL Platform — 臨床品質語言視覺化編輯與執行平台 |
| 測試環境 | GitHub Actions / Ubuntu Latest / Java 21 / Node 20 |
| 執行者 | CI/CD 自動化 |
| 審核者 | _________________ |
| 核准者 | _________________ |

## 修訂歷史

| 版本 | 日期 | 變更說明 | 作者 |
|------|------|---------|------|
| 1.0.0 | 2026-04-23 | 自動產生 | 系統 |

---

## 1. 測試摘要

| 項目 | 後端 (JUnit) | 前端 (Vitest) | 合計 |
|------|-------------|--------------|------|
| 測試套件數 | 115 | 0 | 115 |
| 測試案例數 | 1342 | 0 | 1342 |
| 通過 | 1341 | 0 | 1341 |
| 失敗 | 0 | 0 | 0 |
| 錯誤 | 0 | 0 | 0 |
| 略過 | 1 | 0 | 1 |
| 總執行時間 | 392.20s | 0.00s | 392.20s |

**通過率：99.9%**

---

## 2. 後端測試結果 (JUnit / Surefire)


### com.cqlplatform.controller.AdminControllerErrorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 44.522s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| updateUserRole_notFound_shouldReturn404 | 通過 | 0.781s |
| updateUserEnabled_notFound_shouldReturn404 | 通過 | 0.055s |
| updateUserEnabled_selfDisable_shouldReturn400 | 通過 | 0.032s |
| updateUserRole_selfModify_shouldReturn400 | 通過 | 0.043s |
| createUser_duplicateUsername_shouldReturn409 | 通過 | 0.065s |




### com.cqlplatform.controller.AdminControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 11 |
| 通過 | 11 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.562s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| updateUserEnabled_shouldToggleEnabled | 通過 | 0.072s |
| updateUserRole_differentUser_shouldUpdateRole | 通過 | 0.055s |
| listUsers_unauthenticated_shouldReturn401 | 通過 | 0.061s |
| updateUserRole_selfUpdate_shouldReturn400 | 通過 | 0.033s |
| resetUserPassword_shouldReturnSuccessWithoutPassword | 通過 | 0.066s |
| updateUserEnabled_enable_shouldNotDeactivateApiKeys | 通過 | 0.048s |
| createUser_validRequest_shouldReturnUser | 通過 | 0.031s |
| updateUserEnabled_disable_shouldDeactivateApiKeys | 通過 | 0.032s |
| listUsers_authenticated_shouldReturnUsers | 通過 | 0.046s |
| updateUserEnabled_selfDisable_shouldReturn400 | 通過 | 0.027s |
| createUser_duplicateUsername_shouldReturn409 | 通過 | 0.028s |




### com.cqlplatform.controller.AuditControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 8 |
| 通過 | 8 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 12.259s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getLogs_unauthenticated_shouldReturn401 | 通過 | 0.027s |
| getLogs_withFilters_shouldPassFilters | 通過 | 0.049s |
| getLoginActivity_shouldReturnLogs | 通過 | 0.025s |
| getStats_shouldReturnStats | 通過 | 0.050s |
| getPhiAccess_shouldReturnLogs | 通過 | 0.020s |
| getSecurityEvents_shouldReturnLogs | 通過 | 0.018s |
| exportLogs_shouldReturnCsv | 通過 | 0.027s |
| getLogs_shouldReturnAuditLogs | 通過 | 0.020s |




### com.cqlplatform.controller.AuthControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 15 |
| 通過 | 15 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 12.319s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| logout_withCookie_shouldRevokeAndClear | 通過 | 0.076s |
| refresh_reuseDetected_shouldReturn401AndClearCookie | 通過 | 0.019s |
| login_validCredentials_shouldReturnToken | 通過 | 0.034s |
| refresh_validCookie_shouldReturnNewToken | 通過 | 0.021s |
| register_shortUsername_shouldReturn400 | 通過 | 0.088s |
| register_existingUser_shouldReturn400 | 通過 | 0.040s |
| me_authenticated_shouldReturnUserInfo | 通過 | 0.029s |
| me_unauthenticated_shouldReturn401 | 通過 | 0.023s |
| register_shortPassword_shouldReturn400 | 通過 | 0.023s |
| login_invalidCredentials_shouldReturn401 | 通過 | 0.023s |
| logout_noCookie_shouldStillSucceed | 通過 | 0.018s |
| register_newUser_shouldReturnToken | 通過 | 0.115s |
| login_missingFields_shouldReturn400 | 通過 | 0.021s |
| refresh_noCookie_shouldReturn401 | 通過 | 0.017s |
| refresh_expiredToken_shouldReturn401 | 通過 | 0.020s |




### com.cqlplatform.controller.AuthoringControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 11.316s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| createArtifact_shouldReturn201 | 通過 | 0.176s |
| updateArtifact_shouldReturnUpdated | 通過 | 0.026s |
| listArtifacts_shouldReturnUserArtifacts | 通過 | 0.026s |
| getModifiers_noFilter_shouldReturnAll | 通過 | 0.021s |
| getQueryBuilderResources_shouldReturnResources | 通過 | 0.017s |
| deleteArtifact_shouldReturn204 | 通過 | 0.020s |
| generateCql_shouldReturnCql | 通過 | 0.018s |
| generateCql_withWarnings_shouldIncludeWarningsInResponse | 通過 | 0.014s |
| listArtifacts_unauthenticated_shouldReturn401 | 通過 | 0.017s |
| getModifiers_withInputType_shouldFilter | 通過 | 0.020s |
| getArtifact_shouldReturnArtifact | 通過 | 0.018s |
| getTemplates_shouldReturnCategories | 通過 | 0.014s |




### com.cqlplatform.controller.CdsHooksControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 13.575s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| invoke_shouldCallServiceWithoutAuth | 通過 | 0.094s |
| sandbox_withoutAuth_shouldReturn401 | 通過 | 0.012s |
| invoke_hookMismatch_shouldReturn400 | 通過 | 0.019s |
| invoke_missingRequiredContextFields_shouldReturn400WithDetails | 通過 | 0.031s |
| feedback_withOverride_shouldReturn200 | 通過 | 0.105s |
| discovery_shouldReturnServicesWithoutAuth | 通過 | 0.051s |
| feedback_shouldReturn200WithoutAuth | 通過 | 0.017s |
| sandbox_withAuth_shouldReturn200 | 通過 | 0.046s |
| discovery_trailingSlash_shouldAlsoWork | 通過 | 0.019s |




### com.cqlplatform.controller.CdsServiceConfigControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 15 |
| 通過 | 15 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 10.714s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getService_existing_shouldReturn200 | 通過 | 0.125s |
| createService_invalidHookType_shouldReturn400 | 通過 | 0.040s |
| updateService_shouldReturn200 | 通過 | 0.028s |
| getAnalytics_shouldReturnData | 通過 | 0.032s |
| deleteService_owner_shouldReturn204 | 通過 | 0.033s |
| createService_shouldReturn201 | 通過 | 0.025s |
| deleteService_admin_shouldReturn204 | 通過 | 0.019s |
| getAllServices_shouldReturnList | 通過 | 0.021s |
| createService_duplicate_shouldReturn400 | 通過 | 0.020s |
| disableService_shouldReturn200 | 通過 | 0.017s |
| deleteService_notOwner_shouldReturn403 | 通過 | 0.018s |
| updateService_notOwner_shouldReturn403 | 通過 | 0.020s |
| enableService_shouldReturn200 | 通過 | 0.018s |
| getAllServices_admin_shouldReturnAll | 通過 | 0.019s |
| getService_notFound_shouldReturn404 | 通過 | 0.022s |




### com.cqlplatform.controller.CqlControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 13 |
| 通過 | 13 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 10.540s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| listLibraries_unauthenticated_shouldReturn401 | 通過 | 0.019s |
| listLibraries_shouldReturnList | 通過 | 0.029s |
| translate_validCql_shouldReturn200 | 通過 | 0.037s |
| updateLibrary_shouldReturn200 | 通過 | 0.046s |
| listLibraries_withSearch_shouldFilter | 通過 | 0.017s |
| execute_validRequest_shouldReturn200 | 通過 | 0.032s |
| createLibrary_shouldReturn200 | 通過 | 0.013s |
| translate_unauthenticated_shouldReturn401 | 通過 | 0.013s |
| translate_emptyCql_shouldReturn400 | 通過 | 0.025s |
| getLibrary_notFound_shouldReturn404 | 通過 | 0.017s |
| deleteLibrary_shouldReturn204 | 通過 | 0.019s |
| getLibrary_existing_shouldReturn200 | 通過 | 0.018s |
| validate_shouldCallService | 通過 | 0.015s |




### com.cqlplatform.controller.DepartmentControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 6 |
| 通過 | 6 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 10.392s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| update_shouldReturn200 | 通過 | 0.082s |
| getByCode_notFound_shouldReturn404 | 通過 | 0.017s |
| unauthenticated_shouldReturn401 | 通過 | 0.018s |
| getAll_shouldReturnDepartmentList | 通過 | 0.034s |
| getByCode_shouldReturnDepartment | 通過 | 0.017s |
| create_shouldReturn201 | 通過 | 0.022s |




### com.cqlplatform.controller.EcqmControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 13 |
| 通過 | 13 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 10.568s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| createArtifact_shouldReturn201 | 通過 | 0.083s |
| getTemplates_shouldDelegate | 通過 | 0.012s |
| duplicateArtifact_shouldReturnDuplicated | 通過 | 0.014s |
| updateArtifact_shouldReturnUpdated | 通過 | 0.017s |
| listArtifacts_shouldReturnUserArtifacts | 通過 | 0.020s |
| publish_shouldReturnResult | 通過 | 0.018s |
| generateCql_withWarnings_shouldReturnWarnings | 通過 | 0.015s |
| deleteArtifact_shouldReturn204 | 通過 | 0.014s |
| generateCql_shouldReturnCql | 通過 | 0.013s |
| getModifiers_shouldDelegate | 通過 | 0.013s |
| listArtifacts_unauthenticated_shouldReturn401 | 通過 | 0.017s |
| getScoringTypes_shouldReturnConfig | 通過 | 0.025s |
| getArtifact_shouldReturnFull | 通過 | 0.017s |




### com.cqlplatform.controller.EhrIntegrationControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 10.778s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| listImports_shouldReturnImportHistory | 通過 | 0.081s |
| unauthenticated_shouldReturn401 | 通過 | 0.010s |
| testConnection_shouldReturnUpdatedConnection | 通過 | 0.019s |
| getConnection_shouldReturnConnection | 通過 | 0.012s |
| listConnections_shouldReturnList | 通過 | 0.013s |
| createConnection_withUserRole_shouldReturn403 | 通過 | 0.030s |
| createConnection_shouldReturn201 | 通過 | 0.026s |
| deleteConnection_withUserRole_shouldReturn403 | 通過 | 0.012s |
| deleteConnection_shouldReturn204 | 通過 | 0.011s |
| searchPatients_shouldReturnResults | 通過 | 0.020s |
| searchPatients_withUserRole_shouldReturn403 | 通過 | 0.015s |
| testConnection_withUserRole_shouldReturn403 | 通過 | 0.013s |




### com.cqlplatform.controller.FhirControllerErrorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 9.970s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getVsacValueSet_invalidOid_shouldReturn400 | 通過 | 0.053s |
| readResource_invalidResourceId_shouldReturn400 | 通過 | 0.015s |
| kickOffExport_invalidFhirServerUrl_shouldReturn400 | 通過 | 0.015s |
| readResource_invalidResourceType_shouldReturn400 | 通過 | 0.013s |
| searchResources_invalidResourceType_shouldReturn400 | 通過 | 0.015s |




### com.cqlplatform.controller.FhirControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 25 |
| 通過 | 25 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 11.527s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| updateResource_shouldReturn200 | 通過 | 0.092s |
| executeTransaction_shouldReturn200 | 通過 | 0.048s |
| searchResources_invalidUrl_shouldReturn400 | 通過 | 0.019s |
| kickOffExport_shouldReturn202 | 通過 | 0.220s |
| createResource_shouldReturn200 | 通過 | 0.013s |
| searchPatientsByDemographics_invalidDate_shouldReturn400 | 通過 | 0.014s |
| expandValueSet_shouldReturn200 | 通過 | 0.012s |
| validateCode_shouldReturn200 | 通過 | 0.019s |
| getVsacValueSet_invalidOid_shouldReturn400 | 通過 | 0.017s |
| searchResources_validType_shouldReturn200 | 通過 | 0.014s |
| createSubscription_shouldReturn200 | 通過 | 0.017s |
| searchResources_invalidType_shouldReturn400 | 通過 | 0.010s |
| searchPatientsByDemographics_shouldReturn200 | 通過 | 0.014s |
| readResource_invalidId_shouldReturn400 | 通過 | 0.009s |
| evictCache_withoutAdmin_shouldReturn403 | 通過 | 0.018s |
| deleteResource_shouldReturn204 | 通過 | 0.013s |
| searchResources_unauthenticated_shouldReturn401 | 通過 | 0.012s |
| getVsacValueSet_validOid_shouldReturn200 | 通過 | 0.014s |
| searchResources_invalidParams_shouldReturn400 | 通過 | 0.016s |
| validateResource_invalidResource_shouldReturnIssues | 通過 | 0.046s |
| readResource_invalidType_shouldReturn400 | 通過 | 0.017s |
| getCacheStats_shouldReturn200 | 通過 | 0.017s |
| evictCache_withAdmin_shouldReturn200 | 通過 | 0.059s |
| validateResource_validResource_shouldReturn200 | 通過 | 0.013s |
| readResource_validIdAndType_shouldReturn200 | 通過 | 0.011s |




### com.cqlplatform.controller.IndicatorCatalogControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 19.170s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getByCode_notFound_shouldReturn404 | 通過 | 0.348s |
| unauthenticated_shouldReturn401 | 通過 | 0.016s |
| create_shouldReturn201 | 通過 | 0.047s |
| search_shouldReturnIndicators | 通過 | 0.023s |
| getByCode_found_shouldReturnIndicator | 通過 | 0.012s |




### com.cqlplatform.controller.MeasureControllerErrorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 20.022s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| runTestCase_mismatchedTestCase_shouldReturn400 | 通過 | 0.096s |
| getAuditTrail_measureNotFound_shouldReturn404 | 通過 | 0.013s |
| exportHumanReadable_measureNotFound_shouldReturn404 | 通過 | 0.013s |
| getSchedules_measureNotFound_shouldReturn404 | 通過 | 0.011s |
| getMeasure_notFound_shouldReturn404 | 通過 | 0.012s |




### com.cqlplatform.controller.MeasureControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 3 |
| 通過 | 3 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 13.174s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| evaluateMeasure_withParams_shouldPassThrough | 通過 | 0.092s |
| evaluateCustomMeasure_shouldReturn200 | 通過 | 0.012s |
| evaluateMeasure_shouldReturn200 | 通過 | 0.021s |




### com.cqlplatform.controller.NotificationControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 6 |
| 通過 | 6 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 9.782s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| markAllAsRead_shouldReturnUpdatedCount | 通過 | 0.058s |
| unauthenticated_shouldReturn401 | 通過 | 0.011s |
| markAsRead_notFound_shouldReturn400 | 通過 | 0.013s |
| getUnreadCount_shouldReturnCount | 通過 | 0.013s |
| getNotifications_shouldReturnList | 通過 | 0.017s |
| markAsRead_shouldReturnUpdatedNotification | 通過 | 0.014s |




### com.cqlplatform.controller.SettingsControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 13.341s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getVsacStatus_notConfigured_shouldReturnFalse | 通過 | 0.045s |
| getVsacStatus_configured_shouldReturnStatus | 通過 | 0.012s |
| getVsacStatus_unauthenticated_shouldReturn401 | 通過 | 0.011s |
| updateVsacApiKey_shouldUpdateAndReturnStatus | 通過 | 0.063s |




### com.cqlplatform.controller.SmartConfigControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 2 |
| 通過 | 2 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 11.781s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| smartConfiguration_shouldContainRequiredFields | 通過 | 0.025s |
| smartConfiguration_shouldReturnConfigWithoutAuth | 通過 | 0.014s |




### com.cqlplatform.controller.UserApiKeyControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 6 |
| 通過 | 6 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 11.242s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| listKeys_shouldReturnMaskedKeys | 通過 | 0.050s |
| generateKey_noName_shouldUseDefault | 通過 | 0.024s |
| revokeKey_success_shouldReturn200 | 通過 | 0.013s |
| revokeKey_notFound_shouldReturn404 | 通過 | 0.013s |
| listKeys_unauthenticated_shouldReturn401 | 通過 | 0.011s |
| generateKey_shouldReturn201WithFullKey | 通過 | 0.014s |




### com.cqlplatform.controller.UserLibraryPrefsControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 9.438s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getRecent_shouldReturnRecentLibraries | 通過 | 0.031s |
| getFavorites_shouldReturnParsedLibraryIds | 通過 | 0.014s |
| clearRecent_shouldReturn204 | 通過 | 0.013s |
| addRecent_new_shouldReturn201 | 通過 | 0.017s |
| addRecent_existing_shouldUpdateAccessedAt | 通過 | 0.013s |
| removeFavorite_shouldReturn204 | 通過 | 0.011s |
| addFavorite_new_shouldReturn201 | 通過 | 0.012s |
| addFavorite_alreadyExists_shouldReturn200 | 通過 | 0.013s |
| getFavorites_unauthenticated_shouldReturn401 | 通過 | 0.009s |




### com.cqlplatform.controller.VersionControllerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 2 |
| 通過 | 2 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.241s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getVersion_shouldReturnStartupFields_withoutAuth | 通過 | 0.017s |
| getVersion_startupTimeShouldBeStableWithinSameBootedInstance | 通過 | 0.213s |




### com.cqlplatform.CqlPlatformApplicationTest

| 項目 | 數值 |
|------|------|
| 測試數 | 1 |
| 通過 | 0 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 1 |
| 執行時間 | 0.001s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| contextLoads | 略過 | 0.000s |




### com.cqlplatform.entity.MeasureReportEntityTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.077s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| blankJson_isNoop | 通過 | 0.018s |
| multipleFailures_accumulate | 通過 | 0.016s |
| validJson_populatesResult | 通過 | 0.024s |
| unknownField_isTolerated | 通過 | 0.012s |
| malformedJson_incrementsCounter | 通過 | 0.001s |




### com.cqlplatform.entity.PhiEncryptionIntegrationTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 13.578s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| sandboxPreset_patientIdAndPrefetch_encryptedOnDisk | 通過 | 0.127s |
| measureReport_resultJson_legacyPlaintextStillReadable | 通過 | 0.019s |
| measureReport_resultJson_encryptedOnDisk | 通過 | 0.013s |
| patientImport_allPhiFields_encryptedOnDisk | 通過 | 0.011s |




### com.cqlplatform.exception.GlobalExceptionHandlerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.097s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| handleCqlTranslationException_shouldReturn400WithErrorMessages | 通過 | 0.018s |
| handleCqlExecutionException_shouldReturn500 | 通過 | 0.007s |
| handleIllegalArgumentException_shouldReturn400 | 通過 | 0.001s |
| handleDuplicateResourceException_shouldReturn409WithFieldAndValue | 通過 | 0.000s |
| handleCircuitBreakerOpenException_shouldReturn503 | 通過 | 0.044s |
| handleValidationException_shouldReturn400WithDetails | 通過 | 0.002s |
| handleCqlExecutionException_timedOut_shouldReturn504 | 通過 | 0.001s |
| handleAccessDeniedException_shouldReturn403WithMessage | 通過 | 0.001s |
| handleResourceNotFoundException_shouldReturn404WithMessage | 通過 | 0.005s |
| handleDataIntegrityViolationException_shouldReturn409 | 通過 | 0.001s |
| handleFhirServerUnavailableException_shouldReturn503 | 通過 | 0.002s |
| handleGenericException_shouldReturn500WithGenericMessage | 通過 | 0.008s |




### com.cqlplatform.integration.AuthIntegrationTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 1.002s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| tokenValidation_registeredUserTokenShouldBeValid | 通過 | 0.437s |
| accessProtectedEndpoint_withValidToken | 通過 | 0.140s |
| registerAndLogin_fullFlow | 通過 | 0.254s |
| duplicateRegistration_shouldFail | 通過 | 0.112s |




### com.cqlplatform.integration.CdsServicePersistenceIntegrationTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.193s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| createAndRetrieve_shouldPersist | 通過 | 0.079s |
| findAllEnabled_shouldFilterCorrectly | 通過 | 0.026s |
| updateEntity_shouldPersistChanges | 通過 | 0.040s |
| deleteEntity_shouldRemove | 通過 | 0.038s |




### com.cqlplatform.security.CustomUserDetailsServiceLockoutTest

| 項目 | 數值 |
|------|------|
| 測試數 | 3 |
| 通過 | 3 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.057s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| nullLockout_shouldBeUnlocked | 通過 | 0.053s |
| lockoutInFuture_shouldReturnAccountNonLockedFalse | 通過 | 0.002s |
| lockoutInPast_shouldTreatAsUnlocked | 通過 | 0.001s |




### com.cqlplatform.security.EncryptionConverterTest

| 項目 | 數值 |
|------|------|
| 測試數 | 13 |
| 通過 | 13 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 1.556s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| convertToDatabaseColumn_shouldReturnNullForNull | 通過 | 0.208s |
| convertToEntityAttribute_shouldReturnNullForNull | 通過 | 0.080s |
| roundTrip_specialCharacters | 通過 | 0.073s |
| shortKey_shouldPad | 通過 | 0.168s |
| uniqueIVs_sameInputDifferentCiphertext | 通過 | 0.085s |
| convertToEntityAttribute_shouldReturnLegacyDataAsIs | 通過 | 0.081s |
| roundTrip_largeJsonPayload_100kb | 通過 | 0.133s |
| roundTrip_emptyString | 通過 | 0.092s |
| roundTrip_shouldEncryptAndDecrypt | 通過 | 0.068s |
| roundTrip_longString | 通過 | 0.113s |
| convertToDatabaseColumn_shouldProduceEncPrefix | 通過 | 0.112s |
| wrongKey_shouldFailToDecrypt | 通過 | 0.228s |
| roundTrip_utf8MultibytePayload | 通過 | 0.106s |




### com.cqlplatform.security.InputValidatorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 41 |
| 通過 | 41 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.273s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| isValidSearchParams_shouldAcceptNull | 通過 | 0.076s |
| isValidResourceId_shouldAcceptMaxLengthId | 通過 | 0.001s |
| isValidFhirResourceType_shouldRejectNull | 通過 | 0.000s |
| isValidResourceId_shouldRejectOverlongId | 通過 | 0.000s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[1] | 通過 | 0.015s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[2] | 通過 | 0.001s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[3] | 通過 | 0.000s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[4] | 通過 | 0.001s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[5] | 通過 | 0.003s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[6] | 通過 | 0.001s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[7] | 通過 | 0.001s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[8] | 通過 | 0.001s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[9] | 通過 | 0.001s |
| isValidFhirResourceType_shouldAcceptAllowedTypes(String)[10] | 通過 | 0.003s |
| isValidResourceId_shouldRejectInvalidIds(String)[1] | 通過 | 0.002s |
| isValidResourceId_shouldRejectInvalidIds(String)[2] | 通過 | 0.001s |
| isValidResourceId_shouldRejectInvalidIds(String)[3] | 通過 | 0.006s |
| isValidResourceId_shouldRejectInvalidIds(String)[4] | 通過 | 0.002s |
| isValidResourceId_shouldRejectInvalidIds(String)[5] | 通過 | 0.001s |
| isValidUrl_shouldRejectBlank | 通過 | 0.000s |
| isValidUrl_shouldAcceptHttpsUrl | 通過 | 0.013s |
| isValidSearchParams_shouldRejectScriptInjection | 通過 | 0.000s |
| isValidSearchParams_shouldAcceptEmptyString | 通過 | 0.001s |
| isValidResourceId_shouldRejectNull | 通過 | 0.001s |
| isValidUrl_shouldAcceptHttpUrl | 通過 | 0.001s |
| isValidResourceId_shouldAcceptValidIds(String)[1] | 通過 | 0.000s |
| isValidResourceId_shouldAcceptValidIds(String)[2] | 通過 | 0.000s |
| isValidResourceId_shouldAcceptValidIds(String)[3] | 通過 | 0.000s |
| isValidResourceId_shouldAcceptValidIds(String)[4] | 通過 | 0.000s |
| isValidResourceId_shouldAcceptValidIds(String)[5] | 通過 | 0.000s |
| isValidSearchParams_shouldAcceptValidParams | 通過 | 0.000s |
| isValidUrl_shouldRejectFtpUrl | 通過 | 0.001s |
| isValidSearchParams_shouldRejectSqlInjection | 通過 | 0.000s |
| isValidUrl_shouldRejectJavascriptUrl | 通過 | 0.001s |
| isValidFhirResourceType_shouldRejectInvalidTypes(String)[1] | 通過 | 0.000s |
| isValidFhirResourceType_shouldRejectInvalidTypes(String)[2] | 通過 | 0.000s |
| isValidFhirResourceType_shouldRejectInvalidTypes(String)[3] | 通過 | 0.000s |
| isValidFhirResourceType_shouldRejectInvalidTypes(String)[4] | 通過 | 0.000s |
| isValidFhirResourceType_shouldRejectInvalidTypes(String)[5] | 通過 | 0.001s |
| isValidFhirResourceType_shouldRejectInvalidTypes(String)[6] | 通過 | 0.001s |
| isValidUrl_shouldRejectNull | 通過 | 0.001s |




### com.cqlplatform.security.JwtTokenProviderTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.047s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getExpirationMs_shouldReturnConfiguredValue | 通過 | 0.000s |
| getRole_shouldExtractCorrectRole | 通過 | 0.002s |
| validateToken_shouldReturnTrueForValidToken | 通過 | 0.004s |
| generateToken_shouldReturnNonNullToken | 通過 | 0.003s |
| validateToken_shouldReturnFalseForTamperedToken | 通過 | 0.004s |
| differentSecrets_shouldNotValidateCrossTokens | 通過 | 0.003s |
| validateToken_shouldReturnFalseForEmptyToken | 通過 | 0.001s |
| getUsername_shouldExtractCorrectUsername | 通過 | 0.004s |
| generateToken_shouldContainIssuerAndAudience | 通過 | 0.006s |
| validateToken_shouldRejectTokenWithWrongIssuer | 通過 | 0.002s |
| validateToken_shouldReturnFalseForExpiredToken | 通過 | 0.007s |
| validateToken_shouldReturnFalseForNullToken | 通過 | 0.001s |




### com.cqlplatform.security.LoginAttemptListenerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 7 |
| 通過 | 7 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.035s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| nullUsername_shouldBeNoop | 通過 | 0.008s |
| successfulLogin_clearsPriorFailureState | 通過 | 0.003s |
| firstFailure_shouldSetCounterToOne_noLockout | 通過 | 0.005s |
| failureOnUnknownUser_shouldNotTouchRepository | 通過 | 0.002s |
| failureBelowThreshold_incrementsWithoutLock | 通過 | 0.003s |
| failureAtThreshold_setsLockoutUntil | 通過 | 0.004s |
| successfulLoginOnCleanRecord_shouldNotWriteAgain | 通過 | 0.003s |




### com.cqlplatform.security.OwnershipVerifierTest

| 項目 | 數值 |
|------|------|
| 測試數 | 11 |
| 通過 | 11 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.019s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| isDepartmentAdmin_withDeptAdminRole_shouldReturnTrue | 通過 | 0.003s |
| isAdmin_withUserRole_shouldReturnFalse | 通過 | 0.001s |
| verifyOwnership_otherUser_shouldThrowAccessDenied | 通過 | 0.002s |
| verifyOwnership_ownResource_shouldPass | 通過 | 0.000s |
| getCurrentUsername_shouldReturnAuthenticatedUsername | 通過 | 0.001s |
| verifyOwnership_admin_shouldBypass | 通過 | 0.002s |
| getCurrentUsername_notAuthenticated_shouldThrowAccessDenied | 通過 | 0.001s |
| verifySameDepartment_matchingDepartment_shouldPass | 通過 | 0.002s |
| verifySameDepartment_admin_shouldBypass | 通過 | 0.001s |
| verifySameDepartment_mismatchedDepartment_shouldThrow | 通過 | 0.001s |
| isAdmin_withAdminRole_shouldReturnTrue | 通過 | 0.000s |




### com.cqlplatform.security.RateLimitFilterTest

| 項目 | 數值 |
|------|------|
| 測試數 | 15 |
| 通過 | 15 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.188s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| shouldConsumeMultipleTokensForLargePayload | 通過 | 0.118s |
| shouldComputeTokenCostByPayloadSize | 通過 | 0.002s |
| shouldTrackPerClientBuckets | 通過 | 0.004s |
| shouldSkipOptionsRequests | 通過 | 0.002s |
| shouldApplyTranslateTierLimit | 通過 | 0.009s |
| shouldApplyDefaultTierForOtherPaths | 通過 | 0.005s |
| shouldIsolateBucketsPerTier | 通過 | 0.007s |
| shouldApplyAuthTierLimit | 通過 | 0.003s |
| shouldResolveTierCorrectly | 通過 | 0.004s |
| shouldApplyExecuteTierLimit | 通過 | 0.004s |
| shouldApplyCdsInvokeTierLimit | 通過 | 0.002s |
| shouldApplyLibraryReadTierLimit | 通過 | 0.003s |
| shouldIncrementMetricOnRejection | 通過 | 0.009s |
| shouldSkipWhenDisabled | 通過 | 0.002s |
| shouldReturnRetryAfterHeader | 通過 | 0.004s |




### com.cqlplatform.security.SseTicketServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 6 |
| 通過 | 6 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.007s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| issueAndRedeem_shouldReturnPrincipal | 通過 | 0.002s |
| multipleTickets_shouldBeIndependent | 通過 | 0.001s |
| redeem_unknownTicket_shouldReturnEmpty | 通過 | 0.001s |
| purgeExpired_shouldNotAffectValidTickets | 通過 | 0.001s |
| redeem_nullOrBlank_shouldReturnEmpty | 通過 | 0.000s |
| redeem_sameTicketTwice_shouldFailSecondTime | 通過 | 0.000s |




### com.cqlplatform.security.UserRateLimitFilterTest

| 項目 | 數值 |
|------|------|
| 測試數 | 7 |
| 通過 | 7 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.020s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| shouldIsolatePerUser | 通過 | 0.003s |
| shouldSkipWhenUnauthenticated | 通過 | 0.001s |
| shouldSkipWhenAnonymousUser | 通過 | 0.001s |
| shouldApplyPerUserTranslateLimit | 通過 | 0.004s |
| shouldIncrementMetricOnRejection | 通過 | 0.003s |
| shouldReturnUserRateLimitHeaders | 通過 | 0.003s |
| shouldSkipWhenDisabled | 通過 | 0.002s |




### com.cqlplatform.security.XssStringDeserializerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 24 |
| 通過 | 24 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.035s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| shouldStripVbscriptProtocol | 通過 | 0.001s |
| shouldStripObjectTags(String)[1] | 通過 | 0.001s |
| shouldStripObjectTags(String)[2] | 通過 | 0.000s |
| shouldStripEventHandlers | 通過 | 0.000s |
| shouldStripIframeTags | 通過 | 0.001s |
| shouldStripMathTags(String)[1] | 通過 | 0.001s |
| shouldStripMathTags(String)[2] | 通過 | 0.001s |
| shouldStripFormTags(String)[1] | 通過 | 0.002s |
| shouldStripFormTags(String)[2] | 通過 | 0.001s |
| shouldReturnNullForNull | 通過 | 0.001s |
| shouldStripEmbedTags(String)[1] | 通過 | 0.001s |
| shouldStripEmbedTags(String)[2] | 通過 | 0.001s |
| shouldStripDataTextHtmlUri | 通過 | 0.001s |
| shouldStripScriptTagWithAttributes | 通過 | 0.001s |
| shouldPassSafeStrings | 通過 | 0.000s |
| shouldStripUnclosedScriptTag | 通過 | 0.000s |
| shouldStripBaseTags(String)[1] | 通過 | 0.001s |
| shouldStripBaseTags(String)[2] | 通過 | 0.001s |
| shouldStripEvalCalls | 通過 | 0.001s |
| shouldStripJavascriptProtocol | 通過 | 0.000s |
| shouldStripClosingScriptTag | 通過 | 0.000s |
| shouldStripSvgTags(String)[1] | 通過 | 0.001s |
| shouldStripSvgTags(String)[2] | 通過 | 0.001s |
| shouldStripSvgTags(String)[3] | 通過 | 0.001s |




### com.cqlplatform.service.ai.CqlFixPromptHelperTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.009s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| buildSystemPrompt_emptyList_returnsBasePrompt | 通過 | 0.003s |
| basePrompt_preservesCoreRules | 通過 | 0.001s |
| buildSystemPrompt_entryWithoutExamples_stillIncludesTopicAndExplanation | 通過 | 0.000s |
| buildSystemPrompt_withEntries_appendsPatterns | 通過 | 0.001s |
| buildSystemPrompt_nullList_returnsBasePrompt | 通過 | 0.000s |




### com.cqlplatform.service.ai.CqlKnowledgeBaseTest

| 項目 | 數值 |
|------|------|
| 測試數 | 10 |
| 通過 | 10 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.094s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| allEntries_haveRequiredFields | 通過 | 0.014s |
| findRelevant_codeableConceptError_shouldMatchCodeableConceptEntry | 通過 | 0.010s |
| findRelevant_resolveTypeError_shouldMatchResolveTypeEntry | 通過 | 0.009s |
| findRelevant_unrelatedQuery_shouldReturnEmpty | 通過 | 0.008s |
| findRelevant_zeroTopK_shouldReturnEmpty | 通過 | 0.007s |
| findRelevant_twcoreKeywords_shouldMatchTwcoreEntry | 通過 | 0.008s |
| load_shouldIngestAllYamlFiles | 通過 | 0.007s |
| findRelevant_nullInputs_shouldNotThrow | 通過 | 0.007s |
| findRelevant_respectsTopK | 通過 | 0.009s |
| allEntryIds_areUnique | 通過 | 0.010s |




### com.cqlplatform.service.AuditServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 13 |
| 通過 | 13 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.088s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getPhiAccessLog_withValidDate_shouldParse | 通過 | 0.059s |
| getPhiAccessLog_withNullStartDate_shouldDefault30DaysAgo | 通過 | 0.002s |
| getLoginActivity_shouldReturnResponse | 通過 | 0.002s |
| getEhrOperations_withConnectionId_shouldFilterByConnection | 通過 | 0.003s |
| getStats_shouldReturnAggregatedStats | 通過 | 0.004s |
| exportLogs_shouldOverridePagination | 通過 | 0.005s |
| cleanupOldLogs_shouldDeleteOldRecords | 通過 | 0.002s |
| getSecurityEvents_shouldReturnResponse | 通過 | 0.001s |
| searchLogs_shouldReturnPaginatedResponse | 通過 | 0.001s |
| manualCleanup_shouldDeleteAndReturnCount | 通過 | 0.001s |
| searchLogs_shouldIncludeEhrFieldsInResponse | 通過 | 0.002s |
| getRetentionDays_shouldReturnConfiguredValue | 通過 | 0.001s |
| getEhrOperations_withoutConnectionId_shouldReturnAllEhrOps | 通過 | 0.002s |




### com.cqlplatform.service.authoring.ArtifactExportServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 3 |
| 通過 | 3 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.033s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| exportAsZip_translationFails_shouldThrow | 通過 | 0.004s |
| exportAsZip_libraryJsonContainsCpgMetadata | 通過 | 0.018s |
| exportAsZip_shouldContainAllFiles | 通過 | 0.008s |




### com.cqlplatform.service.authoring.ArtifactServiceLibraryRefIntegrationTest

| 項目 | 數值 |
|------|------|
| 測試數 | 2 |
| 通過 | 2 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 47.759s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| emptyTree_regression_stillBuildsCqlWithoutLibraryIncludes | 通過 | 1.197s |
| frontendPickerJson_deserializesAndGeneratesCqlWithInclude | 通過 | 0.029s |




### com.cqlplatform.service.authoring.ArtifactServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 10 |
| 通過 | 10 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.127s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| create_shouldSetOwnerAndSave | 通過 | 0.101s |
| getById_notFound_shouldReturnEmpty | 通過 | 0.002s |
| update_asOwner_shouldSucceed | 通過 | 0.003s |
| duplicate_shouldCreateCopyWithSuffix | 通過 | 0.003s |
| delete_asNonOwner_shouldThrow | 通過 | 0.002s |
| duplicate_asNonOwner_shouldThrow | 通過 | 0.002s |
| listByOwner_shouldReturnSummaries | 通過 | 0.002s |
| delete_asOwner_shouldCascadeExternalCql | 通過 | 0.002s |
| update_asNonOwner_shouldThrow | 通過 | 0.001s |
| getById_found_shouldReturnResponse | 通過 | 0.001s |




### com.cqlplatform.service.authoring.CqlArtifactBuilderEdgeCaseTest

| 項目 | 數值 |
|------|------|
| 測試數 | 36 |
| 通過 | 36 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.852s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| warningsFromMultipleSources_shouldBeCollected | 通過 | 0.323s |
| noWarnings_shouldReturnEmptyList | 通過 | 0.030s |
| emptyInclusion_shouldReturnNull | 通過 | 0.017s |
| nonEmptyExclusion_shouldNotUseFalse | 通過 | 0.014s |
| defaultR4_shouldIncludeFhir401 | 通過 | 0.015s |
| codeSystemsInTree_shouldAppearInHeader | 通過 | 0.025s |
| externalCqlInBaseElements_shouldAddInclude | 通過 | 0.009s |
| valueSetsInTree_shouldAppearInHeader | 通過 | 0.014s |
| customCondition_shouldPassThrough | 通過 | 0.014s |
| nullErrorStatement_shouldNotProduceErrorsDefine | 通過 | 0.008s |
| nullCondition_shouldMapToRecommendationIsNull | 通過 | 0.011s |
| emptyErrorStatement_shouldNotProduceErrorsDefine | 通過 | 0.009s |
| errorsCondition_shouldMapToErrorsNotNull | 通過 | 0.013s |
| multipleClauses_shouldChainIfElseIf | 通過 | 0.011s |
| withSelectionBehavior_shouldInclude | 通過 | 0.018s |
| withSourceLabel_shouldIncludeSource | 通過 | 0.010s |
| withSuggestions_shouldProduceTupleWithSuggestions | 通過 | 0.009s |
| cardText_withSpecialChars_shouldBeEscaped | 通過 | 0.010s |
| multipleRecommendations_shouldBeNumbered | 通過 | 0.006s |
| singleRecommendation_shouldNotBeNumbered | 通過 | 0.005s |
| recommendation_noSubpopulations_shouldUseInPopulation | 通過 | 0.006s |
| recommendation_withDoesntMeetInclusionSubpop_shouldNegateInclusion | 通過 | 0.007s |
| recommendation_withMeetsExclusionSubpop_shouldReferenceExclusion | 通過 | 0.008s |
| recommendation_withCustomSubpop_shouldCombineInPopulationAndSubpop | 通過 | 0.005s |
| specialSubpopulation_shouldBeSkipped | 通過 | 0.005s |
| shouldGenerateSubpopulationDefine | 通過 | 0.006s |
| nonArithmetic_shouldAppearBeforeArithmetic | 通過 | 0.012s |
| conjunctionBaseElement_shouldBuildAsConjunction | 通過 | 0.007s |
| stringParameter_shouldDeclare | 通過 | 0.090s |
| parameterWithUniqueId_shouldBeResolvableByRef | 通過 | 0.017s |
| parameterWithoutValue_shouldDeclareWithoutDefault | 通過 | 0.008s |
| booleanParameter_shouldDeclare | 通過 | 0.011s |
| integerParameter_shouldDeclare | 通過 | 0.010s |
| multipleParameters_shouldAllAppear | 通過 | 0.019s |
| nullVersion_shouldUseDefault | 通過 | 0.010s |
| specialCharacters_shouldBeReplacedWithUnderscore | 通過 | 0.008s |




### com.cqlplatform.service.authoring.CqlArtifactBuilderTest

| 項目 | 數值 |
|------|------|
| 測試數 | 11 |
| 通過 | 11 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.109s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| buildCql_unknownElementType_shouldProduceWarning | 通過 | 0.008s |
| buildCql_emptyExclusion_shouldProduceFalseNotNull | 通過 | 0.007s |
| currentBaseElements_instanceField_shouldNotExist | 通過 | 0.002s |
| buildCql_ageRangeBothBounds_shouldWrapInParentheses | 通過 | 0.012s |
| buildCql_withSimpleRecommendation_shouldProduceDefine | 通過 | 0.008s |
| buildCql_withCdsCardRecommendation_shouldProduceTuple | 通過 | 0.012s |
| buildCql_emptyTree_shouldProduceValidLibraryHeader | 通過 | 0.007s |
| buildCql_ageRangeOnlyMin_shouldNotProduceNullReference | 通過 | 0.011s |
| buildCql_lookBackModifier_emptyValue_shouldOmitQuantity | 通過 | 0.013s |
| buildCql_lookBackModifier_shouldGenerateC3FLookBackWith6Months | 通過 | 0.012s |
| buildCql_withErrorStatement_shouldProduceIfThenElse | 通過 | 0.009s |




### com.cqlplatform.service.authoring.CqlGenerationServiceLibraryIntegrationTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.178s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| generateCqlWithWarnings_exposesLibraryIncludes | 通過 | 0.028s |
| externalCqlRefInBaseElements_survivesPersistenceAndEmitsInclude | 通過 | 0.010s |
| multipleLibraryReferences_emitOnlyUniqueIncludeStatements | 通過 | 0.011s |
| externalCqlElementInInclusionTree_emitsIncludeAndReference | 通過 | 0.010s |
| regenerateWithoutReSave_producesIdenticalCql | 通過 | 0.009s |




### com.cqlplatform.service.authoring.CqlGenerationServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.042s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| generateCql_nullFhirVersion_shouldUseR4 | 通過 | 0.017s |
| generateAndTranslate_shouldReturnTranslationResponse | 通過 | 0.002s |
| generateCqlWithWarnings_shouldReturnWarnings | 通過 | 0.002s |
| generateCql_found_shouldReturnBuiltCql | 通過 | 0.002s |
| generateCql_builderThrowsClassCast_shouldWrapInCqlGenerationException | 通過 | 0.004s |
| generateCql_builderThrowsNPE_shouldWrapInCqlGenerationException | 通過 | 0.003s |
| validateArtifactCql_shouldReturnValidationResult | 通過 | 0.002s |
| generateCql_notFound_shouldThrow | 通過 | 0.003s |
| generateCql_explicitFhirVersion_shouldAlwaysUseR4 | 通過 | 0.002s |




### com.cqlplatform.service.authoring.ExpressionCqlEngineEdgeCaseTest

| 項目 | 數值 |
|------|------|
| 測試數 | 122 |
| 通過 | 122 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.580s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| emptyModifiers_shouldReturnElementType | 通過 | 0.001s |
| withModifiers_shouldReturnLastModifierReturnType | 通過 | 0.001s |
| modifiersWithNoReturnType_shouldFallBackToElement | 通過 | 0.000s |
| nullModifiers_shouldReturnElementType | 通過 | 0.001s |
| nullValue_shouldReturnDefault | 通過 | 0.001s |
| nullFields_shouldReturnDefault | 通過 | 0.001s |
| presentValue_shouldReturnValue | 通過 | 0.001s |
| missingField_shouldReturnDefault | 通過 | 0.001s |
| parameterNameIndex_shouldResolveByUniqueId | 通過 | 0.000s |
| findParameterName_null_shouldReturnNull | 通過 | 0.001s |
| findBaseElementName_null_shouldReturnNull | 通過 | 0.001s |
| parameterNameIndex_unknownId_shouldReturnNull | 通過 | 0.001s |
| emitCodes_empty_shouldEmitNothing | 通過 | 0.001s |
| emitCodeSystems_empty_shouldEmitNothing | 通過 | 0.001s |
| emitCodes_shouldEmitDeclarations | 通過 | 0.001s |
| emitCodeSystems_shouldEmitDeclarations | 通過 | 0.004s |
| shouldCollectExternalCqlIncludes_withoutVersion | 通過 | 0.001s |
| shouldCollectCodesAndCodeSystems | 通過 | 0.035s |
| shouldCollectValueSetFromFieldValueMap | 通過 | 0.001s |
| shouldRecurseIntoChildren | 通過 | 0.005s |
| nullNode_shouldNotThrow | 通過 | 0.001s |
| shouldCollectExternalCqlIncludes | 通過 | 0.001s |
| shouldCollectValueSetsFromFieldValueSets | 通過 | 0.001s |
| nullType_shouldReturnToString | 通過 | 0.036s |
| unknownType_shouldUseDefault | 通過 | 0.008s |
| quantity_withNonMap_shouldReturnNull | 通過 | 0.000s |
| quantity_withMap_shouldFormat | 通過 | 0.006s |
| time_withAtT_shouldKeepAsIs | 通過 | 0.006s |
| datetime_withoutAt_shouldPrefixAt | 通過 | 0.013s |
| code_withMap_shouldFormat | 通過 | 0.006s |
| datetime_withT_shouldNotDuplicateT | 通過 | 0.007s |
| intervalDatetime_withMap_shouldFormat | 通過 | 0.009s |
| time_shouldPrefixAtT | 通過 | 0.007s |
| decimal_shouldFormatAsIs | 通過 | 0.007s |
| intervalInteger_withNonMap_shouldUseFallback | 通過 | 0.007s |
| string_withSingleQuote_shouldEscape | 通過 | 0.006s |
| time_withAtPrefix_shouldConvertToAtT | 通過 | 0.006s |
| concept_withMap_shouldFormat | 通過 | 0.006s |
| concept_withNonMap_shouldReturnNull | 通過 | 0.001s |
| code_withNonMap_shouldReturnNull | 通過 | 0.000s |
| intervalDatetime_withEmptyBounds_shouldUseNull | 通過 | 0.006s |
| quantity_withEmptyValue_shouldReturnNull | 通過 | 0.000s |
| datetime_withAt_shouldKeepAt | 通過 | 0.004s |
| code_withEmptyCode_shouldReturnNull | 通過 | 0.000s |
| string_shouldWrapInQuotes | 通過 | 0.005s |
| intervalInteger_withMap_shouldFormat | 通過 | 0.005s |
| afterInterval_emptyValue_shouldReturnExprUnchanged | 通過 | 0.001s |
| containsDecimal_shouldFormatValue | 通過 | 0.006s |
| beforeInterval_emptyValue_shouldReturnExprUnchanged | 通過 | 0.001s |
| cqlTemplateWithPlaceholder_shouldInterpolate | 通過 | 0.001s |
| unknownModifier_shouldWarnAndSkip | 通過 | 0.002s |
| valueComparisonNumber_emptyValues_shouldReturnExprUnchanged | 通過 | 0.001s |
| valueComparisonNumber_nullValues_shouldReturnExprUnchanged | 通過 | 0.001s |
| withUnit_nullCqlLibFunc_shouldReturnExprUnchanged | 通過 | 0.001s |
| qualifier_unknownQualifierType_shouldStillRender | 通過 | 0.004s |
| convertUnits_emptyUnit_shouldReturnExprUnchanged | 通過 | 0.001s |
| containsDateTime_shouldFormatValue | 通過 | 0.004s |
| valueComparisonNumber_onlyMax_shouldProduceSingleCondition | 通過 | 0.000s |
| valueComparisonNumber_withUnit_shouldIncludeUnit | 通過 | 0.000s |
| listCollapsingModifier_shouldBeSkippedWhenPreserveListReturn | 通過 | 0.008s |
| valueComparisonNumber_withRange_shouldProduceCompoundCondition | 通過 | 0.002s |
| valueComparisonObservation_shouldWorkSameAsNumber | 通過 | 0.002s |
| booleanComparison_nullValues_shouldReturnExprUnchanged | 通過 | 0.001s |
| resourceTypeExtraction_shouldStripPrefixAndSuffix | 通過 | 0.005s |
| withCodeReferences_shouldUseCodeDisplay | 通過 | 0.006s |
| multipleValueSets_shouldProduceUnion | 通過 | 0.005s |
| nullFields_shouldReturnBareResource | 通過 | 0.005s |
| withValueAsString_shouldUseStringValue | 通過 | 0.007s |
| withValueAsMap_shouldExtractName | 通過 | 0.005s |
| orConjunction_shouldUseOrOperator | 通過 | 0.017s |
| nullGroup_shouldReturnNull | 通過 | 0.000s |
| nestedConjunction_shouldWrapInParentheses | 通過 | 0.004s |
| listReturnType_withoutPreserve_shouldWrapInExists | 通過 | 0.005s |
| arithmeticExpression_validQuantityLiteral_shouldPass | 通過 | 0.001s |
| baseElementRef_unresolvedRef_shouldUseFallbackName | 通過 | 0.000s |
| arithmeticExpression_literalOperands_shouldProduceFormula | 通過 | 0.001s |
| arithmeticExpression_invalidOperator_shouldDefaultToPlus | 通過 | 0.000s |
| unknownType_shouldDefaultToTrueWithWarning | 通過 | 0.000s |
| listReturnType_withPreserve_shouldNotWrapInExists | 通過 | 0.006s |
| arithmeticExpression_unresolvedOperands_shouldWarn | 通過 | 0.001s |
| externalCqlRef_withoutLibraryName_shouldNotQualify | 通過 | 0.001s |
| parameterRef_shouldResolveByUniqueId | 通過 | 0.002s |
| baseElementRef_shouldResolveByUniqueId | 通過 | 0.001s |
| arithmeticExpression_invalidLiteral_shouldRejectNonNumeric | 通過 | 0.001s |
| externalCqlRef_withLibraryName_shouldQualify | 通過 | 0.001s |
| cdsContext_shouldEmitPlainAgeInYears | 通過 | 0.004s |
| ecqmContext_shouldBindToMeasurementPeriod | 通過 | 0.004s |
| ecqmContext_withRange_shouldBindBothBounds | 通過 | 0.005s |
| null_shouldDefaultToYears | 通過 | 0.000s |
| periodBound_unknownUnit_shouldDefaultToYearsAt | 通過 | 0.003s |
| periodBound_knownUnits_shouldEmitAtForm(String, String)[1] | 通過 | 0.001s |
| periodBound_knownUnits_shouldEmitAtForm(String, String)[2] | 通過 | 0.001s |
| periodBound_knownUnits_shouldEmitAtForm(String, String)[3] | 通過 | 0.000s |
| periodBound_knownUnits_shouldEmitAtForm(String, String)[4] | 通過 | 0.000s |
| periodBound_knownUnits_shouldEmitAtForm(String, String)[5] | 通過 | 0.001s |
| periodBound_nullUnit_shouldDefaultToYearsAt | 通過 | 0.000s |
| mixedCase_shouldMatch | 通過 | 0.000s |
| nonPeriodBound_shouldStillEmitPlainForm | 通過 | 0.000s |
| knownUnits_shouldMapCorrectly(String, String)[1] | 通過 | 0.001s |
| knownUnits_shouldMapCorrectly(String, String)[2] | 通過 | 0.001s |
| knownUnits_shouldMapCorrectly(String, String)[3] | 通過 | 0.001s |
| knownUnits_shouldMapCorrectly(String, String)[4] | 通過 | 0.001s |
| knownUnits_shouldMapCorrectly(String, String)[5] | 通過 | 0.001s |
| knownUnits_shouldMapCorrectly(String, String)[6] | 通過 | 0.000s |
| knownUnits_shouldMapCorrectly(String, String)[7] | 通過 | 0.001s |
| knownUnits_shouldMapCorrectly(String, String)[8] | 通過 | 0.001s |
| knownUnits_shouldMapCorrectly(String, String)[9] | 通過 | 0.000s |
| knownUnits_shouldMapCorrectly(String, String)[10] | 通過 | 0.001s |
| unknownUnit_shouldDefaultToYears | 通過 | 0.001s |
| empty_shouldReturnNull | 通過 | 0.001s |
| null_shouldReturnNull | 通過 | 0.002s |
| dateTime_shouldPrefixAt | 通過 | 0.001s |
| dateOnly_shouldPrefixAt | 通過 | 0.001s |
| emptyString_shouldReturnEmpty | 通過 | 0.001s |
| withNonAscii_shouldStrip | 通過 | 0.000s |
| withNonAscii_shouldStrip | 通過 | 0.000s |
| withMixedEscapeChars_shouldEscapeCorrectly | 通過 | 0.001s |
| null_shouldReturnEmpty | 通過 | 0.000s |
| withEmptyParentheses_shouldStrip | 通過 | 0.001s |
| withMultipleSpaces_shouldCollapse | 通過 | 0.001s |
| withBackslashAndQuotes_shouldEscapeBothCorrectOrder | 通過 | 0.002s |
| withDoubleQuotes_shouldEscape | 通過 | 0.002s |
| withBackslash_shouldEscape | 通過 | 0.001s |




### com.cqlplatform.service.authoring.ExpressionCqlEngineTest

| 項目 | 數值 |
|------|------|
| 測試數 | 63 |
| 通過 | 63 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.217s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| applyModifier_containsQuantity_withUnit | 通過 | 0.012s |
| formatParameterDefault_null_shouldReturnNull | 通過 | 0.001s |
| applyModifier_duringMeasurementPeriod_observation | 通過 | 0.008s |
| applyModifier_qualifier_valueSet | 通過 | 0.013s |
| applyModifier_isFalse | 通過 | 0.004s |
| applyModifier_booleanComparison | 通過 | 0.006s |
| classifyListBehavior_singleResourceReturnType_isCollapsesList | 通過 | 0.001s |
| applyModifier_count | 通過 | 0.004s |
| emitValueSets_empty_shouldEmitNothing | 通過 | 0.000s |
| buildConjunctionExpression_emptyTree_shouldReturnNull | 通過 | 0.000s |
| applyModifier_isNotTrue | 通過 | 0.004s |
| classifyListBehavior_systemReturnType_isExtractsValue | 通過 | 0.001s |
| resolveFhirVersion_r4_shouldReturn401 | 通過 | 0.001s |
| escapeCqlString_shouldEscapeBackslashes | 通過 | 0.000s |
| buildExpression_ageRange_bothBounds | 通過 | 0.003s |
| applyModifier_qualifier_code | 通過 | 0.008s |
| applyModifier_beforeTimePrecise | 通過 | 0.005s |
| applyModifier_startsWithString | 通過 | 0.006s |
| classifyListBehavior_listReturnType_isPreservesList | 通過 | 0.001s |
| applyModifier_booleanNot | 通過 | 0.005s |
| applyModifier_equalsString | 通過 | 0.004s |
| formatParameterDefault_integer_shouldFormat | 通過 | 0.006s |
| applyModifier_withUnit | 通過 | 0.004s |
| cvMeasurePopulation_skippedModifierSurfacesUserWarning | 通過 | 0.006s |
| collectDeclarations_emptyTree_shouldNotThrow | 通過 | 0.000s |
| getStr_missingKey_shouldReturnDefault | 通過 | 0.000s |
| applyModifier_checkExistence | 通過 | 0.004s |
| applyModifier_allTrue | 通過 | 0.004s |
| getStr_nullValue_shouldReturnDefault | 通過 | 0.001s |
| applyModifier_anyTrue | 通過 | 0.004s |
| applyModifier_afterDateTimePrecise | 通過 | 0.003s |
| escapeCqlString_null_shouldReturnEmpty | 通過 | 0.000s |
| formatParameterDefault_boolean_shouldFormat | 通過 | 0.006s |
| applyModifier_duringMeasurementPeriod_unknownIdWarns | 通過 | 0.003s |
| applyModifier_containsQuantity_noUnit | 通過 | 0.004s |
| withRenderMode_shouldNestAndRestoreInnerModeOnly | 通過 | 0.001s |
| getStr_presentKey_shouldReturnValue | 通過 | 0.000s |
| applyModifier_lookBackModifier | 通過 | 0.003s |
| buildExpression_genericObservation | 通過 | 0.006s |
| withRenderMode_shouldRestoreModeAfterBody | 通過 | 0.000s |
| applyModifier_duringMeasurementPeriod_encounter | 通過 | 0.005s |
| applyModifier_booleanExists | 通過 | 0.003s |
| buildExpression_gender | 通過 | 0.003s |
| buildContext_shouldAcceptBaseElements | 通過 | 0.001s |
| applyModifier_isNotFalse | 通過 | 0.003s |
| emitValueSets_shouldEmitDeclarations | 通過 | 0.000s |
| emitIncludes_shouldEmitDeclarations | 通過 | 0.000s |
| classifyListBehavior_explicitOverride_winsOverInference | 通過 | 0.000s |
| withRenderMode_shouldRestoreEvenIfBodyThrows | 通過 | 0.001s |
| applyModifier_afterInterval | 通過 | 0.002s |
| escapeCqlString_shouldEscapeSingleQuotes | 通過 | 0.001s |
| applyModifier_endsWithString | 通過 | 0.003s |
| applyModifier_convertUnits | 通過 | 0.003s |
| classifyListBehavior_missingReturnType_defaultsToPreservesList | 通過 | 0.000s |
| mapParameterType_unknownType_shouldReturnCapitalized | 通過 | 0.000s |
| applyModifier_isTrue | 通過 | 0.003s |
| mapParameterType_shouldMapKnownTypes | 通過 | 0.000s |
| applyModifier_containsInteger | 通過 | 0.004s |
| buildContext_shouldTrackWarnings | 通過 | 0.001s |
| applyModifier_beforeInterval | 通過 | 0.002s |
| applyModifier_baseModifier_fallback | 通過 | 0.003s |
| resolveHelpersVersion_r4_shouldReturn401 | 通過 | 0.000s |
| buildConjunctionExpression_andConjunction_shouldUseAnd | 通過 | 0.003s |




### com.cqlplatform.service.authoring.ExpressionTreeValidatorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 16 |
| 通過 | 16 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.040s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| validate_subpopulations_shouldValidateNodes | 通過 | 0.004s |
| validate_duplicateParameterNames_shouldThrow | 通過 | 0.002s |
| validate_conjunctionNode_shouldRecurseIntoChildren | 通過 | 0.003s |
| validate_unknownElementType_shouldThrowWithDetails | 通過 | 0.002s |
| validate_parameterReservedName_shouldThrow | 通過 | 0.002s |
| validate_reservedRecommendationNumbered_shouldThrow | 通過 | 0.002s |
| validate_validTree_shouldPass | 通過 | 0.001s |
| validate_multipleErrors_shouldCollectAll | 通過 | 0.002s |
| validate_uniqueNamesAcrossCategories_shouldPass | 通過 | 0.002s |
| validate_nullTrees_shouldPass | 通過 | 0.001s |
| validate_duplicateSubpopulationNames_shouldThrow | 通過 | 0.002s |
| validate_crossCategoryCollision_baseElementVsSubpopulation_shouldThrow | 通過 | 0.002s |
| validate_duplicateBaseElementNames_shouldThrow | 通過 | 0.002s |
| validate_reservedSystemName_shouldThrow | 通過 | 0.002s |
| validate_unknownModifierId_shouldThrowWithDetails | 通過 | 0.002s |
| validate_specialSubpopulations_shouldBeSkipped | 通過 | 0.002s |




### com.cqlplatform.service.authoring.ModifierServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.014s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getModifiersByInputType_unknownType_shouldReturnEmpty | 通過 | 0.003s |
| getAllModifiers_shouldReturnNonEmptyList | 通過 | 0.002s |
| getModifiersByInputType_knownType_shouldReturnResults | 通過 | 0.004s |
| init_shouldLoadModifiersFromClasspath | 通過 | 0.002s |
| getAllModifiers_eachShouldHaveNameAndId | 通過 | 0.003s |




### com.cqlplatform.service.authoring.QueryBuilderServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 8 |
| 通過 | 8 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.023s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getOperatorsForType_nonExistent_shouldReturnEmpty | 通過 | 0.005s |
| getOperators_shouldBeUnmodifiable | 通過 | 0.002s |
| loadConfig_shouldLoadOperators | 通過 | 0.002s |
| getResources_shouldBeUnmodifiable | 通過 | 0.003s |
| loadConfig_shouldLoadResources | 通過 | 0.003s |
| getResource_nonExistent_shouldReturnEmpty | 通過 | 0.003s |
| getResource_observation_shouldReturnResult | 通過 | 0.002s |
| getOperatorsForType_string_shouldReturnOperators | 通過 | 0.002s |




### com.cqlplatform.service.authoring.TemplateServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.012s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getAllCategories_shouldReturnCorrectStructure | 通過 | 0.003s |
| getAllCategories_entriesShouldHaveIds | 通過 | 0.003s |
| init_shouldLoadCategoriesFromClasspath | 通過 | 0.002s |
| getAllCategories_shouldHaveEntriesWithNames | 通過 | 0.003s |




### com.cqlplatform.service.cds.CdsAnalyticsServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.086s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getServiceAnalytics_noData_shouldReturnEmptyDTO | 通過 | 0.075s |
| recordInvocation_error_shouldIncrementErrorCount | 通過 | 0.004s |
| recordInvocation_existingService_shouldIncrementCounts | 通過 | 0.002s |
| recordInvocation_newService_shouldCreateAnalyticsRecord | 通過 | 0.002s |
| getServiceAnalytics_shouldReturnDTO | 通過 | 0.002s |




### com.cqlplatform.service.cds.CdsFeedbackTest

| 項目 | 數值 |
|------|------|
| 測試數 | 6 |
| 通過 | 6 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.086s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| processFeedback_withAccepted_shouldPersist | 通過 | 0.075s |
| processFeedback_emptyFeedback_shouldNotPersist | 通過 | 0.001s |
| processFeedback_withHtmlInDisplay_shouldEscapeBeforePersist | 通過 | 0.002s |
| processFeedback_withOverride_shouldPersistReason | 通過 | 0.001s |
| processFeedback_serviceNotFound_shouldThrow | 通過 | 0.002s |
| getFeedback_shouldReturnResults | 通過 | 0.002s |




### com.cqlplatform.service.cds.CdsHooksServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.032s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| invokeService_patientView_missingUserId_shouldThrowValidationException | 通過 | 0.006s |
| invokeService_orderSelect_missingDraftOrders_shouldThrowValidationException | 通過 | 0.002s |
| invokeService_nullContext_shouldHandleGracefully | 通過 | 0.003s |
| invokeService_unknownService_shouldReturnNotFoundCard | 通過 | 0.001s |
| registerService_shouldAddToConfigs | 通過 | 0.003s |
| invokeService_registeredService_shouldDelegateToInvocationService | 通過 | 0.002s |
| getServiceDefinitions_withRegistered_shouldReturnThem | 通過 | 0.002s |
| getServiceDefinitions_empty_shouldReturnEmptyList | 通過 | 0.001s |
| invokeService_hookMismatch_shouldThrow | 通過 | 0.001s |
| unregisterService_shouldRemoveFromConfigs | 通過 | 0.001s |
| invokeService_encounterStart_withAllRequired_shouldProceed | 通過 | 0.001s |
| createService_invalidHookType_shouldThrow | 通過 | 0.004s |




### com.cqlplatform.service.cds.CdsInvocationServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 10 |
| 通過 | 10 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.049s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| invoke_debugModeTrue_executionFails_shouldAttachErrorInfoWithPhase | 通過 | 0.015s |
| invoke_debugModeTrue_translatorErrorAsNestedCause_shouldClassifyAsTranslation | 通過 | 0.005s |
| invoke_planDefinitionMode_shouldUsePlanDefinitionStrategy | 通過 | 0.005s |
| invoke_debugModeTrue_success_shouldAttachDebugTraceAndContext | 通過 | 0.006s |
| invoke_debugModeFalse_shouldNotAttachDebug | 通過 | 0.002s |
| invoke_planDefinitionModeWithNullJson_shouldFallbackToTupleStrategy | 通過 | 0.002s |
| invoke_debugModeTrue_translatorErrorWrappedInExecutionException_shouldClassifyAsTranslation | 通過 | 0.003s |
| invoke_executionError_shouldReturnErrorCard | 通過 | 0.003s |
| invoke_nullMode_shouldDefaultToTupleStrategy | 通過 | 0.003s |
| invoke_cqlTupleMode_shouldUseTupleStrategy | 通過 | 0.002s |




### com.cqlplatform.service.cds.CdsResourceFormatterTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.025s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| formatReference_withId | 通過 | 0.003s |
| formatDetail_medicationRequest | 通過 | 0.007s |
| formatDetail_condition_withCodeAndStatus | 通過 | 0.002s |
| formatDetail_preservesRawContent | 通過 | 0.000s |
| formatReference_withoutId | 通過 | 0.001s |
| formatDetail_unknownResource | 通過 | 0.001s |
| formatDetail_procedure | 通過 | 0.005s |
| formatDetail_observation_withCodeAndValue | 通過 | 0.002s |
| formatDetail_allergyIntolerance | 通過 | 0.002s |




### com.cqlplatform.service.cds.CdsServiceVersioningTest

| 項目 | 數值 |
|------|------|
| 測試數 | 6 |
| 通過 | 6 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.016s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| createService_secondVersion_shouldIncrementVersion | 通過 | 0.004s |
| getServiceVersions_shouldReturnAllVersions | 通過 | 0.002s |
| rollbackService_nonexistentVersion_shouldThrow | 通過 | 0.002s |
| rollbackService_nonexistentService_shouldThrow | 通過 | 0.002s |
| rollbackService_shouldDisableAllAndEnableTarget | 通過 | 0.003s |
| createService_firstVersion_shouldUseVersion1 | 通過 | 0.002s |




### com.cqlplatform.service.cds.CdsTupleAccessorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 14 |
| 通過 | 14 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.012s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getString_missingField_shouldReturnNull | 通過 | 0.002s |
| getList_nonListValue_shouldReturnEmptyList | 通過 | 0.001s |
| getElements_shouldReturnMap | 通過 | 0.000s |
| isTuple_withTupleObject_shouldReturnTrue | 通過 | 0.001s |
| getBoolean_missingField_shouldReturnNull | 通過 | 0.000s |
| getList_missingField_shouldReturnEmptyList | 通過 | 0.001s |
| getList_shouldReturnListValue | 通過 | 0.000s |
| getBoolean_nonBooleanValue_shouldReturnNull | 通過 | 0.001s |
| isTuple_withNonTupleObject_shouldReturnFalse | 通過 | 0.000s |
| getObject_shouldReturnRawValue | 通過 | 0.000s |
| isTuple_withNull_shouldReturnFalse | 通過 | 0.001s |
| getString_shouldReturnStringValue | 通過 | 0.001s |
| getBoolean_shouldReturnBooleanValue | 通過 | 0.000s |
| getElements_withNonTuple_shouldReturnEmptyMap | 通過 | 0.000s |




### com.cqlplatform.service.cds.CdsValueFormatterTest

| 項目 | 數值 |
|------|------|
| 測試數 | 19 |
| 通過 | 19 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.015s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| formatValue_null_shouldReturnNull | 通過 | 0.001s |
| formatExpressionLine_coding | 通過 | 0.000s |
| formatExpressionLine_resource | 通過 | 0.001s |
| formatExpressionLine_emptyList_shouldReturnNull | 通過 | 0.000s |
| formatExpressionLine_number | 通過 | 0.000s |
| formatExpressionLine_string | 通過 | 0.001s |
| formatExpressionLine_codeableConcept | 通過 | 0.001s |
| formatValue_number | 通過 | 0.001s |
| formatValue_string | 通過 | 0.000s |
| formatExpressionLine_chineseText | 通過 | 0.000s |
| formatValue_boolean | 通過 | 0.001s |
| formatValue_quantity | 通過 | 0.000s |
| formatExpressionLine_string_preservesRawContent | 通過 | 0.000s |
| formatValue_string_preservesRawContent | 通過 | 0.000s |
| formatExpressionLine_booleanFalse_shouldReturnNull | 通過 | 0.000s |
| formatExpressionLine_booleanTrue_shouldReturnYes | 通過 | 0.000s |
| formatValue_resource | 通過 | 0.000s |
| formatExpressionLine_quantity | 通過 | 0.001s |
| formatExpressionLine_list_shouldFormatItems | 通過 | 0.000s |




### com.cqlplatform.service.cds.CqlTupleCardStrategyTest

| 項目 | 數值 |
|------|------|
| 測試數 | 6 |
| 通過 | 6 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.007s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| buildResponse_withBooleanTrue_shouldReturnConsolidatedCard | 通過 | 0.002s |
| createInfoCard_shouldSetIndicatorToInfo | 通過 | 0.001s |
| createErrorCard_shouldSetIndicatorToWarning | 通過 | 0.001s |
| buildResponse_noResults_shouldReturnNoRecommendationsCard | 通過 | 0.001s |
| buildResponse_withStringResult_shouldReturnConsolidatedCard | 通過 | 0.001s |
| buildResponse_withBooleanFalse_shouldReturnNoRecommendationsCard | 通過 | 0.001s |




### com.cqlplatform.service.cds.PlanDefinitionCardStrategyTest

| 項目 | 數值 |
|------|------|
| 測試數 | 8 |
| 通過 | 8 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 1.393s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| buildResponse_withApplicableAction_shouldGenerateCard | 通過 | 0.037s |
| buildResponse_nullPlanDefinitionJson_shouldReturnErrorCard | 通過 | 0.002s |
| buildResponse_urgentPriority_shouldMapToWarning | 通過 | 0.227s |
| buildResponse_actionWithNoCondition_shouldAlwaysApply | 通過 | 0.111s |
| buildResponse_invalidPlanDefinitionJson_shouldReturnErrorCard | 通過 | 0.005s |
| buildResponse_statPriority_shouldMapToCritical | 通過 | 0.554s |
| buildResponse_conditionFalse_shouldNotGenerateCard | 通過 | 0.439s |
| buildResponse_withDynamicValues_shouldIncludeInDetail | 通過 | 0.015s |




### com.cqlplatform.service.cds.PrefetchRetrieveProviderTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.393s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| retrieve_nonExistentType_shouldReturnEmpty | 通過 | 0.124s |
| retrieve_withCodeFilter_shouldFilterByCode | 通過 | 0.004s |
| retrieve_medicationRequestWithCodeFilter_shouldFilter | 通過 | 0.001s |
| retrieve_conditionWithCodeFilter_shouldFilter | 通過 | 0.002s |
| retrieve_emptyResourceList_shouldReturnEmpty | 通過 | 0.001s |
| retrieve_byDataType_shouldReturnMatchingResources | 通過 | 0.001s |
| retrieve_withoutCodePath_shouldReturnAllOfType | 通過 | 0.000s |
| retrieve_multipleResourceTypes_shouldOrganizeCorrectly | 通過 | 0.093s |
| retrieve_codeFilterNoMatch_shouldReturnEmpty | 通過 | 0.001s |




### com.cqlplatform.service.cql.CqlExecutionIntegrationTest

| 項目 | 數值 |
|------|------|
| 測試數 | 19 |
| 通過 | 19 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 9.989s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| legacySingleArg_shouldNotWarnOnSelfContainedLibrary | 通過 | 3.551s |
| crossLibraryRef_shouldDiscoverRetrievesInIncludedLib | 通過 | 1.316s |
| legacySingleArg_shouldWarnOnCrossLibraryReference | 通過 | 0.083s |
| freshCql_shouldOverrideSavedDbVersion | 通過 | 1.157s |
| expressionFilter_shouldLimitEvaluation | 通過 | 0.406s |
| invalidCql_shouldFailGracefully | 通過 | 0.069s |
| debugMode_shouldProduceTraces | 通過 | 0.231s |
| diabeticScreened_shouldBeInNumerator | 通過 | 0.821s |
| diabeticNotScreened_shouldNotBeInNumerator | 通過 | 0.348s |
| healthyPatient_shouldNotBeInPopulation | 通過 | 0.348s |
| healthyPatient_hasNoConditions | 通過 | 0.484s |
| observationRetrieve_findsHbA1c | 通過 | 0.271s |
| patientDemographics_fromDiabeticPatient | 通過 | 0.249s |
| conditionRetrieve_findsDiabetes | 通過 | 0.267s |
| dates_shouldProduceCorrectResults | 通過 | 0.089s |
| nullHandling_shouldWorkCorrectly | 通過 | 0.044s |
| intervals_shouldComputeContainment | 通過 | 0.074s |
| conditionals_shouldEvaluateCorrectly | 通過 | 0.047s |
| arithmetic_shouldProduceCorrectResults | 通過 | 0.050s |




### com.cqlplatform.service.cql.CqlFormatterServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.011s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| format_addBlankLineBeforeContext | 通過 | 0.001s |
| format_trimTrailingWhitespace | 通過 | 0.000s |
| format_preserveExistingIndent | 通過 | 0.000s |
| format_null_shouldReturnNull | 通過 | 0.001s |
| format_preservesCqlContent | 通過 | 0.000s |
| format_normalizeLineEndings | 通過 | 0.001s |
| format_blank_shouldReturnAsIs | 通過 | 0.000s |
| format_addBlankLineBeforeDefine | 通過 | 0.000s |
| format_indentAfterDefine | 通過 | 0.001s |
| format_collapseMultipleBlankLines | 通過 | 0.000s |
| format_ensureSingleTrailingNewline | 通過 | 0.001s |
| format_addBlankLineBeforeValueset | 通過 | 0.000s |




### com.cqlplatform.service.cql.CqlLibraryServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.039s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| saveLibrary_validCql_shouldStoreAndReturn | 通過 | 0.008s |
| getLatestLibrary_shouldReturnHighestVersion | 通過 | 0.006s |
| searchLibraries_nullSearch_shouldReturnAll | 通過 | 0.002s |
| getLibraryVersions_shouldReturnSortedDesc | 通過 | 0.003s |
| getLibrary_nonExisting_shouldReturnEmpty | 通過 | 0.002s |
| deleteLibrary_shouldRemoveFromStore | 通過 | 0.005s |
| updateLibrary_nonExisting_shouldThrow | 通過 | 0.003s |
| saveLibrary_invalidCql_shouldThrow | 通過 | 0.002s |
| searchLibraries_byName_shouldFilter | 通過 | 0.002s |
| getLibrary_existing_shouldReturnLibrary | 通過 | 0.001s |
| getLibraryByNameAndVersion_shouldFindCorrectLibrary | 通過 | 0.001s |
| getAllLibraries_shouldReturnAllStored | 通過 | 0.000s |




### com.cqlplatform.service.cql.CqlPipelineIntegrationTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 2.325s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| generatedCql_shouldExecuteSuccessfully | 通過 | 0.562s |
| translateThenExecute_shouldProduceCorrectResults | 通過 | 1.010s |
| generatedCql_shouldTranslateSuccessfully | 通過 | 0.494s |
| translation_shouldExtractMetadata | 通過 | 0.256s |




### com.cqlplatform.service.cql.CqlTranslationServiceHintTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.006s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| codingEquivalentString_shouldSuggestCodeField | 通過 | 0.000s |
| nullMessage_shouldReturnNullHint | 通過 | 0.000s |
| unrelatedTranslatorError_shouldReturnNullHint | 通過 | 0.000s |
| codeableConceptEquivalentString_shouldSuggestCodeExtraction | 通過 | 0.001s |




### com.cqlplatform.service.cql.CqlTranslationServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 1 |
| 通過 | 1 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 11.196s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| testTranslateWithFHIRHelpers | 通過 | 0.788s |




### com.cqlplatform.service.cql.CqlTranslationServiceUnitTest

| 項目 | 數值 |
|------|------|
| 測試數 | 10 |
| 通過 | 10 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.059s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| compile_validCql_shouldReturnCompiledLibrary | 通過 | 0.006s |
| translate_validCql_shouldSucceed | 通過 | 0.007s |
| validate_validCql_shouldSucceed | 通過 | 0.005s |
| translate_invalidCql_shouldReturnErrors | 通過 | 0.007s |
| translate_simpleCqlWithoutFhir_shouldSucceed | 通過 | 0.009s |
| translate_blankCql_shouldThrowIllegalArgument | 通過 | 0.001s |
| compile_invalidCql_shouldThrowTranslationException | 通過 | 0.007s |
| translate_nullCql_shouldThrowIllegalArgument | 通過 | 0.001s |
| validate_invalidCql_shouldReturnErrors | 通過 | 0.006s |
| translate_shouldExtractMetadata | 通過 | 0.007s |




### com.cqlplatform.service.cql.DatabaseLibrarySourceProviderTest

| 項目 | 數值 |
|------|------|
| 測試數 | 8 |
| 通過 | 8 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.012s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| excludedIdentifier_returnsNullAndDoesNotQueryRepo | 通過 | 0.003s |
| latestVersionResolution | 通過 | 0.002s |
| clearExclusion | 通過 | 0.001s |
| exclusionIsPerVersion | 通過 | 0.001s |
| returnsKnownLibrary | 通過 | 0.001s |
| versionLessLookupAgainstExcludedName | 通過 | 0.001s |
| exclusionIsPerIdentifier | 通過 | 0.001s |
| unknownLibrary_returnsNull | 通過 | 0.000s |




### com.cqlplatform.service.cql.DataRequirementExtractorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 22 |
| 通過 | 22 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.039s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| extract_deduplicatesQueriesWithSameCodeSystem | 通過 | 0.011s |
| extract_queryWithFunctionRefWrappedDateFilter_shouldExtractDateFilter | 通過 | 0.004s |
| extract_queryWithInlineCodesAndWhereDate_shouldNotOverrideInlineCodes | 通過 | 0.001s |
| extract_nullInput_shouldReturnEmptyList | 通過 | 0.000s |
| extract_retrieveWithDateProperty_shouldIncludeDateFilter | 通過 | 0.000s |
| extract_queryWithExistsCodeSystem_shouldExtractCodeSystemFromWhere | 通過 | 0.000s |
| extract_queryWithBothCodeSystemAndDateFilter_shouldExtractBoth | 通過 | 0.001s |
| extract_retrieveWithCodeRef_shouldCaptureDirectCode | 通過 | 0.003s |
| extract_duplicateRetrieves_shouldDeduplicate | 通過 | 0.000s |
| extract_nestedRetrieveInQuery_shouldBeFound | 通過 | 0.001s |
| extract_invalidJson_shouldReturnEmptyList | 通過 | 0.001s |
| extract_retrieveWithoutCodeProperty_shouldReturnWithoutCodeFilter | 通過 | 0.001s |
| extract_valueSetRefWithUnknownName_shouldUseNameAsValueSet | 通過 | 0.001s |
| extract_emptyString_shouldReturnEmptyList | 通過 | 0.001s |
| extract_queryWithNoWhereClause_shouldStillExtractBareRetrieve | 通過 | 0.001s |
| extract_mixedInlineAndWhereRetrieves_shouldExtractAll | 通過 | 0.001s |
| extract_noRetrieves_shouldReturnEmptyList | 通過 | 0.001s |
| extract_singleRetrieveWithValueSet_shouldExtractCorrectly | 通過 | 0.000s |
| extract_queryWithExistsAndCondition_shouldExtractCodeSystem | 通過 | 0.002s |
| extract_queryWithOverlapsDateFilter_shouldExtractDateFilter | 通過 | 0.000s |
| extract_multipleDistinctRetrieves_shouldReturnAll | 通過 | 0.001s |
| extract_blankString_shouldReturnEmptyList | 通過 | 0.000s |




### com.cqlplatform.service.cql.DependencyAnalysisServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 8 |
| 通過 | 8 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.042s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| analyze_transitiveDependencies_shouldResolve | 通過 | 0.009s |
| analyze_noDependencies_shouldReturnEmpty | 通過 | 0.007s |
| analyze_circularDependency_shouldNotInfiniteLoop | 通過 | 0.006s |
| analyze_libraryNotFound_shouldReturnEmpty | 通過 | 0.001s |
| analyze_versionMatch_shouldResolveCorrectly | 通過 | 0.001s |
| analyze_versionMismatch_shouldReportMismatch | 通過 | 0.005s |
| analyze_conflictingVersions_shouldDetectConflict | 通過 | 0.009s |
| analyze_invalidLibraryId_noDash_shouldReturnEmpty | 通過 | 0.001s |




### com.cqlplatform.service.cql.LibraryManagerFactoryTest

| 項目 | 數值 |
|------|------|
| 測試數 | 2 |
| 通過 | 2 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.002s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| buildOptions_mustSetSignatureLevelToOverloadsOrWider | 通過 | 0.001s |
| defaultOptions_mustSetSignatureLevelToOverloadsOrWider | 通過 | 0.000s |




### com.cqlplatform.service.cql.ModifierGeneratedCqlGoldenTest

| 項目 | 數值 |
|------|------|
| 測試數 | 15 |
| 通過 | 15 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 5.001s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| bug110_nullEffective_doesNotThrow | 通過 | 0.284s |
| onsetDateTimeInside | 通過 | 0.309s |
| recordedDateFallback | 通過 | 0.297s |
| noDateAtAll | 通過 | 0.230s |
| onsetPeriodInside | 通過 | 0.241s |
| periodOutside | 通過 | 0.315s |
| periodInside | 通過 | 0.359s |
| nullPeriod | 通過 | 0.535s |
| mixedList_returnsOnlyMatching | 通過 | 0.569s |
| nullEffective_noExceptionNoMatch | 通過 | 0.281s |
| periodInsideMp_matches | 通過 | 0.312s |
| periodOverlapsBoundary_matches | 通過 | 0.272s |
| dateTimeOutsideMp_noMatch | 通過 | 0.372s |
| periodOutsideMp_noMatch | 通過 | 0.289s |
| dateTimeInsideMp_matches | 通過 | 0.321s |




### com.cqlplatform.service.CustomUserDetailsServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.009s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| loadUserByUsername_admin_shouldHaveAdminRole | 通過 | 0.003s |
| loadUserByUsername_found_shouldReturnUserDetails | 通過 | 0.001s |
| loadUserByUsername_notFound_shouldThrowUsernameNotFoundException | 通過 | 0.001s |
| loadUserByUsername_disabledUser_shouldReturnDisabledDetails | 通過 | 0.001s |




### com.cqlplatform.service.ecqm.EcqmCqlBuilderTest

| 項目 | 數值 |
|------|------|
| 測試數 | 18 |
| 通過 | 18 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.228s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| buildEcqmCql_withStandardSde_shouldEmitSdeDefines | 通過 | 0.018s |
| buildEcqmCql_withTopLevelStratifiers_shouldEmitDefines | 通過 | 0.011s |
| buildEcqmCql_proportion_shouldNotContainCvOrRatioStructures | 通過 | 0.010s |
| buildEcqmCql_continuousVariable_shouldContainObservationFunction | 通過 | 0.012s |
| buildEcqmCql_missingRequiredPopulation_shouldWarn | 通過 | 0.014s |
| buildEcqmCql_emptyPopulationTree_shouldStillProduceDefine | 通過 | 0.008s |
| buildEcqmCql_proportion_shouldContainRequiredDefines | 通過 | 0.008s |
| buildEcqmCql_multiGroup_shouldAppendSuffix | 通過 | 0.010s |
| buildEcqmCql_specialCharsInName_shouldSanitize | 通過 | 0.016s |
| buildEcqmCql_cvMissingObservation_shouldWarn | 通過 | 0.020s |
| buildEcqmCql_withUserParameters_shouldEmitParams | 通過 | 0.018s |
| buildEcqmCql_noGroups_shouldWarn | 通過 | 0.006s |
| buildEcqmCql_ratio_dualIp_shouldContainDualIpDefines | 通過 | 0.011s |
| buildEcqmCql_ratio_singleIp_shouldContainStandardDefines | 通過 | 0.012s |
| buildEcqmCql_continuousVariable_episodeBased_shouldUseResourceParam | 通過 | 0.013s |
| buildEcqmCql_cohort_shouldContainOnlyIp | 通過 | 0.010s |
| buildEcqmCql_unknownScoringType_shouldWarn | 通過 | 0.008s |
| buildEcqmCql_ratio_dualIp_withStratifier_shouldWarn | 通過 | 0.011s |




### com.cqlplatform.service.ecqm.EcqmExpressionTreeValidatorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 33 |
| 通過 | 33 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.074s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| unknownAggregateMethods_shouldThrowValidationException(String)[1] | 通過 | 0.006s |
| unknownAggregateMethods_shouldThrowValidationException(String)[2] | 通過 | 0.002s |
| unknownAggregateMethods_shouldThrowValidationException(String)[3] | 通過 | 0.002s |
| unknownAggregateMethods_shouldThrowValidationException(String)[4] | 通過 | 0.002s |
| unknownAggregateMethods_shouldThrowValidationException(String)[5] | 通過 | 0.001s |
| unknownAggregateMethods_shouldThrowValidationException(String)[6] | 通過 | 0.001s |
| missingObservations_shouldBeAccepted | 通過 | 0.001s |
| nullAggregateMethod_shouldBeAccepted | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[1] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[2] | 通過 | 0.000s |
| validAggregateMethods_shouldNotThrow(String)[3] | 通過 | 0.000s |
| validAggregateMethods_shouldNotThrow(String)[4] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[5] | 通過 | 0.002s |
| validAggregateMethods_shouldNotThrow(String)[6] | 通過 | 0.000s |
| validAggregateMethods_shouldNotThrow(String)[7] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[8] | 通過 | 0.002s |
| validAggregateMethods_shouldNotThrow(String)[9] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[10] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[11] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[12] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[13] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[14] | 通過 | 0.002s |
| validAggregateMethods_shouldNotThrow(String)[15] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[16] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[17] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[18] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[19] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[20] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[21] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[22] | 通過 | 0.001s |
| validAggregateMethods_shouldNotThrow(String)[23] | 通過 | 0.000s |
| blankAggregateMethod_shouldBeAccepted | 通過 | 0.001s |
| multipleObservationsWithOneBadMethod_shouldIdentifyOffender | 通過 | 0.002s |




### com.cqlplatform.service.ecqm.EcqmPublishServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.174s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| publish_notFound_shouldThrow | 通過 | 0.156s |
| publish_existingMeasure_shouldUpdateMeasureDefinition | 通過 | 0.004s |
| publish_withCqlErrors_shouldThrow | 通過 | 0.004s |
| publish_asNonOwner_shouldThrow | 通過 | 0.002s |
| publish_newMeasure_shouldCreateMeasureDefinition | 通過 | 0.003s |




### com.cqlplatform.service.fhir.AsyncPatientImportServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 13 |
| 通過 | 13 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.081s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| cancelJob_runningJob_shouldSetCancelled | 通過 | 0.035s |
| submitBatchImport_shouldCreateJobWithPendingStatus | 通過 | 0.008s |
| getJob_notFound_shouldThrow | 通過 | 0.002s |
| submitBatchImport_withEmptyIds_shouldStillCreate | 通過 | 0.002s |
| executeBatchImport_withFailures_shouldRecordErrors | 通過 | 0.007s |
| getJob_shouldReturnJob | 通過 | 0.001s |
| executeBatchImport_jobNotFound_shouldThrow | 通過 | 0.003s |
| listJobs_withCreatedBy_shouldFilter | 通過 | 0.004s |
| listJobs_withoutCreatedBy_shouldReturnAll | 通過 | 0.002s |
| cancelJob_completedJob_shouldThrow | 通過 | 0.002s |
| executeBatchImport_allFailed_shouldSetStatusFailed | 通過 | 0.003s |
| executeBatchImport_cancelled_shouldStopEarly | 通過 | 0.002s |
| executeBatchImport_shouldProcessAllPatients | 通過 | 0.002s |




### com.cqlplatform.service.fhir.ConnectionHealthServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.305s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getCircuitBreakerStatuses_shouldReturnAllBreakers | 通過 | 0.176s |
| checkConnection_healthy_shouldRecordHealthy | 通過 | 0.103s |
| scheduledHealthCheck_shouldCheckAllConnections | 通過 | 0.004s |
| getHealthOverview_shouldReturnAllConnections | 通過 | 0.007s |
| checkConnection_down_shouldRecordDown | 通過 | 0.002s |
| checkConnection_slow_shouldRecordDegraded | 通過 | 0.003s |
| getHealthOverview_withNoChecks_shouldReturnUnknown | 通過 | 0.002s |
| getHistory_shouldReturnRecentChecks | 通過 | 0.002s |
| cleanupOldChecks_shouldDeleteExpiredRecords | 通過 | 0.002s |




### com.cqlplatform.service.fhir.FhirBulkExportServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 2 |
| 通過 | 2 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.151s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| pollExportStatus_serverError_shouldReturnInProgress | 通過 | 0.146s |
| kickOffExport_serverError_shouldThrow | 通過 | 0.004s |




### com.cqlplatform.service.fhir.FhirClientFactoryTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.492s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| createPlainClient_returnsClient | 通過 | 0.403s |
| createClient_returnsClient | 通過 | 0.042s |
| createAuthenticatedClient_basicAuth_returnsClient | 通過 | 0.020s |
| createAuthenticatedClient_smartBackend_usesTokenService | 通過 | 0.005s |
| createAuthenticatedClient_bearerAuth_returnsClient | 通過 | 0.003s |
| createAuthenticatedClient_tlsEnabled_callsTlsContextFactory | 通過 | 0.003s |
| createAuthenticatedClient_noAuth_returnsClient | 通過 | 0.003s |
| createAuthenticatedClient_tlsDisabled_doesNotCallTlsContextFactory | 通過 | 0.004s |
| createClient_cachesClient | 通過 | 0.005s |




### com.cqlplatform.service.fhir.FhirDataProviderServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.019s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| createClient_shouldUseDefaultUrlWhenNull | 通過 | 0.005s |
| parseSearchParams_shouldHandleEmptyParams | 通過 | 0.002s |
| getAndResetRetrieveCount_shouldResetAfterGet | 通過 | 0.004s |
| getAndResetRetrieveCount_shouldStartAtZero | 通過 | 0.002s |
| createClient_shouldUseProvidedUrl | 通過 | 0.004s |




### com.cqlplatform.service.fhir.FhirFallbackTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.184s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| validateCodeFallback_shouldThrowFhirServerUnavailableException | 通過 | 0.070s |
| searchPatientsByDemographicsFallback_shouldThrowFhirServerUnavailableException | 通過 | 0.048s |
| searchResourcesFallback_shouldThrowFhirServerUnavailableException | 通過 | 0.031s |
| searchValueSetsFallback_shouldThrowFhirServerUnavailableException | 通過 | 0.032s |




### com.cqlplatform.service.fhir.FhirImplementationGuideServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 7 |
| 通過 | 7 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.025s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| findProfileUrlForResourceType_nullType_shouldReturnNull | 通過 | 0.007s |
| getRecommendedProfiles_nullType_shouldReturnEmpty | 通過 | 0.001s |
| getRecommendedProfiles_shouldExcludeBaseDefinitions | 通過 | 0.004s |
| getRecommendedProfiles_knownType_shouldReturnProfiles | 通過 | 0.002s |
| getRecommendedProfiles_unknownType_shouldReturnEmpty | 通過 | 0.001s |
| findProfileUrlForResourceType_knownType_shouldReturnUrl | 通過 | 0.003s |
| findProfileUrlForResourceType_unknownType_shouldReturnNull | 通過 | 0.001s |




### com.cqlplatform.service.fhir.FhirSubscriptionServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 10 |
| 通過 | 10 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.198s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getSubscription_notFound_shouldThrow | 通過 | 0.051s |
| handleNotification_unknownSubscription_shouldLogWarning | 通過 | 0.001s |
| createSubscription_failure_shouldSetErrorStatus | 通過 | 0.041s |
| listSubscriptions_withoutConnectionId_shouldReturnAll | 通過 | 0.002s |
| syncStatus_shouldUpdateFromRemote | 通過 | 0.046s |
| createSubscription_success_shouldSetActiveStatus | 通過 | 0.003s |
| deleteSubscription_shouldDeleteLocalAndRemote | 通過 | 0.043s |
| listSubscriptions_withConnectionId_shouldFilter | 通過 | 0.002s |
| getSubscription_found_shouldReturn | 通過 | 0.002s |
| handleNotification_knownSubscription_shouldIncrementCount | 通過 | 0.002s |




### com.cqlplatform.service.fhir.FhirValidationServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 10.650s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| validateBundle_withResource_shouldPopulateEntries | 通過 | 8.078s |
| validateResource_withProfile_shouldValidateAgainstProfile | 通過 | 0.481s |
| validateResource_withoutProfile_shouldUseBaseValidation | 通過 | 0.430s |
| validateBundle_withValidBundle_shouldReturnResults | 通過 | 0.476s |
| validateResource_invalidJson_shouldReturnInvalid | 通過 | 0.487s |
| validateResource_malformedResource_shouldHaveIssues | 通過 | 0.385s |
| validateBundle_invalidJson_shouldReturnEmptyResult | 通過 | 0.003s |
| validateBundle_emptyBundle_shouldReturnEmpty | 通過 | 0.015s |
| validateResource_validPatient_shouldBeValid | 通過 | 0.292s |




### com.cqlplatform.service.fhir.ImportRetryServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 17 |
| 通過 | 17 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.059s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| recordFailure_withLongErrorMessage_shouldTruncate | 通過 | 0.028s |
| deleteFailedImport_notFound_shouldThrow | 通過 | 0.001s |
| retryImport_failure_shouldIncrementRetry | 通過 | 0.003s |
| retryImport_success_shouldResolve | 通過 | 0.002s |
| deleteFailedImport_exists_shouldDelete | 通過 | 0.001s |
| calculateNextRetry_shouldCapAtOneHour | 通過 | 0.002s |
| recordFailure_withNullCreatedBy_shouldDefaultToSystem | 通過 | 0.001s |
| recordFailure_shouldCreatePendingRecord | 通過 | 0.001s |
| retryImport_alreadyResolved_shouldThrow | 通過 | 0.002s |
| calculateNextRetry_shouldUseExponentialBackoff | 通過 | 0.003s |
| retryImport_notFound_shouldThrow | 通過 | 0.002s |
| retryImport_exhausted_shouldMarkExhausted | 通過 | 0.002s |
| processAutoRetries_noDueRetries_shouldDoNothing | 通過 | 0.001s |
| listFailedImports_withoutStatus_shouldReturnAll | 通過 | 0.001s |
| listFailedImports_withStatus_shouldFilter | 通過 | 0.001s |
| retryImport_exhaustedManual_shouldResetAndRetry | 通過 | 0.002s |
| processAutoRetries_withDueRetries_shouldProcess | 通過 | 0.002s |




### com.cqlplatform.service.fhir.PatientMatchServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 14 |
| 通過 | 14 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.116s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| deduplicateResults_samePatient_shouldKeepHighestScore | 通過 | 0.004s |
| deduplicateResults_noIdentifier_shouldUseConnectionAndPatientId | 通過 | 0.001s |
| calculateScore_identifierOnly_shouldReturn50 | 通過 | 0.004s |
| isValidTwNationalId_invalidId_shouldReturnFalse | 通過 | 0.001s |
| calculateScore_nameAndDob_shouldReturn35 | 通過 | 0.003s |
| matchPatients_byName_shouldReturnProbableMatch | 通過 | 0.082s |
| calculateScore_fullMatch_shouldReturn100 | 通過 | 0.001s |
| deduplicateResults_differentPatients_shouldKeepAll | 通過 | 0.001s |
| matchPatients_acrossConnections_shouldDedup | 通過 | 0.001s |
| isValidTwNationalId_null_shouldReturnFalse | 通過 | 0.001s |
| matchPatients_byNationalId_shouldReturnExactMatch | 通過 | 0.005s |
| matchPatients_connectionError_shouldContinue | 通過 | 0.005s |
| matchPatients_noResults_shouldReturnEmpty | 通過 | 0.002s |
| isValidTwNationalId_validId_shouldReturnTrue | 通過 | 0.001s |




### com.cqlplatform.service.fhir.TlsContextFactoryTest

| 項目 | 數值 |
|------|------|
| 測試數 | 11 |
| 通過 | 11 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.013s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| buildTrustManagers_noCerts_throwsException | 通過 | 0.001s |
| createHostnameVerifier_tlsDisabled_returnsNull | 通過 | 0.000s |
| createSslContext_tlsEnabledButNoCerts_returnsNull | 通過 | 0.000s |
| createSslContext_tlsEnabledBlankCerts_returnsNull | 通過 | 0.001s |
| createHostnameVerifier_hostnameVerificationDisabled_returnsPermissiveVerifier | 通過 | 0.001s |
| parseCertificates_emptyPem_returnsEmptyList | 通過 | 0.000s |
| createHostnameVerifier_hostnameVerificationEnabled_returnsNull | 通過 | 0.001s |
| parsePrivateKey_invalidKey_throwsException | 通過 | 0.001s |
| parseCertificates_invalidPem_returnsEmptyList | 通過 | 0.005s |
| createSslContext_withValidMinVersion_noCerts_returnsNull | 通過 | 0.000s |
| createSslContext_tlsDisabled_returnsNull | 通過 | 0.000s |




### com.cqlplatform.service.fhir.VsacServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 3 |
| 通過 | 3 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.006s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| expandValueSetByOid_serverError_shouldThrow | 通過 | 0.002s |
| searchValueSets_serverError_shouldThrow | 通過 | 0.001s |
| getValueSetByOid_serverError_shouldThrow | 通過 | 0.002s |




### com.cqlplatform.service.measure.DashboardServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 10 |
| 通過 | 10 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.105s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getEnhancedDashboard_withDepartmentFilter_shouldFilterMeasures | 通過 | 0.075s |
| getDepartmentDrilldown_shouldGroupByMeasure | 通過 | 0.003s |
| getTrends_withMeasureIdFilter_shouldFilterByMeasure | 通過 | 0.005s |
| getEnhancedDashboard_noMeasures_shouldReturnEmptyDashboard | 通過 | 0.002s |
| getTrends_shouldReturnChronologicalOrder | 通過 | 0.001s |
| getAlerts_shouldReturnThresholdViolations | 通過 | 0.005s |
| setThreshold_shouldSaveAndReturn | 通過 | 0.001s |
| getEnhancedDashboard_withMeasures_shouldAggregateCorrectly | 通過 | 0.001s |
| generateReport_shouldReturnReportWithScores | 通過 | 0.009s |
| getDepartmentDrilldown_shouldPickLatestScorePerMeasure | 通過 | 0.001s |




### com.cqlplatform.service.measure.DateShiftServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 4 |
| 通過 | 4 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.004s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| shiftDates_nullInput_shouldReturnNull | 通過 | 0.001s |
| shiftDates_zeroDays_shouldReturnOriginal | 通過 | 0.000s |
| shiftDates_shouldShiftFhirDateFields | 通過 | 0.002s |
| calculateAutoShift_shouldReturnDaysDifference | 通過 | 0.001s |




### com.cqlplatform.service.measure.MeasureComparisonServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 8 |
| 通過 | 8 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.053s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| extractPopulationCounts_shouldFallBackToJson_whenNormalizedRowsAbsent | 通過 | 0.034s |
| getTrend_shouldReturnOrderedDataPoints | 通過 | 0.006s |
| comparePeriods_largeDecline_shouldShowDeclining | 通過 | 0.003s |
| comparePeriods_bothPeriodsHaveData_shouldCalculateDelta | 通過 | 0.002s |
| getTrend_shouldLimitToPeriodCount | 通過 | 0.001s |
| extractPopulationCounts_shouldPreferNormalizedTable_whenAvailable | 通過 | 0.001s |
| comparePeriods_largeImprovement_shouldShowImproving | 通過 | 0.001s |
| comparePeriods_oneEmptyPeriod_shouldHaveNullDelta | 通過 | 0.001s |




### com.cqlplatform.service.measure.MeasureDefinitionServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 22 |
| 通過 | 22 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.074s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| retireMeasure_fromActive_shouldSucceed | 通過 | 0.029s |
| approveMeasure_fromInReview_shouldSucceed | 通過 | 0.002s |
| shareMeasure_shouldAddUser | 通過 | 0.003s |
| delete_shouldRecordAudit | 通過 | 0.001s |
| lockMeasure_unlocked_shouldSucceed | 通過 | 0.002s |
| getAuditTrail_shouldDelegateToRepository | 通過 | 0.001s |
| lockMeasure_expiredLock_shouldSucceed | 通過 | 0.002s |
| getById_notFound_shouldReturnEmpty | 通過 | 0.001s |
| lockMeasure_lockedByAnother_shouldThrow | 通過 | 0.001s |
| shareMeasure_nonOwner_shouldThrow | 通過 | 0.001s |
| createVersion_patch_shouldBump | 通過 | 0.003s |
| getById_found_shouldReturnMeasure | 通過 | 0.001s |
| create_duplicateMeasure_shouldThrow | 通過 | 0.001s |
| createVersion_major_shouldBump | 通過 | 0.002s |
| submitForReview_fromActive_shouldThrow | 通過 | 0.002s |
| unlockMeasure_byLockHolder_shouldSucceed | 通過 | 0.002s |
| create_newMeasure_shouldSucceed | 通過 | 0.001s |
| unlockMeasure_byWrongUser_shouldThrow | 通過 | 0.002s |
| createVersion_notFound_shouldThrow | 通過 | 0.002s |
| rejectMeasure_fromInReview_shouldRevertToDraft | 通過 | 0.002s |
| submitForReview_fromDraft_shouldSucceed | 通過 | 0.003s |
| createVersion_minor_shouldBump | 通過 | 0.002s |




### com.cqlplatform.service.measure.MeasureEvaluationIntegrationTest

| 項目 | 數值 |
|------|------|
| 測試數 | 7 |
| 通過 | 7 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 2.731s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| singleQualifyingPatient_scoreShouldBe100 | 通過 | 0.235s |
| denominatorOnlyPatient_scoreShouldBeZero | 通過 | 0.134s |
| eachPatient_shouldMatchExpectedPopulations | 通過 | 0.350s |
| ratioScore_shouldAlsoWork | 通過 | 0.572s |
| aggregation_shouldProduceCorrectCounts | 通過 | 0.796s |
| noQualifyingPatients_scoreShouldBeNull | 通過 | 0.157s |
| measureScore_shouldBeFiftyPercent | 通過 | 0.484s |




### com.cqlplatform.service.measure.MeasureEvaluationServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.030s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| evaluateMeasure_shouldSetReportType | 通過 | 0.007s |
| evaluateMeasure_zeroDenominator_shouldReturnNullScore | 通過 | 0.004s |
| evaluateMeasure_singlePatient_shouldReturnResult | 通過 | 0.002s |
| evaluateMeasure_multiplePatients_shouldAggregateResults | 通過 | 0.003s |
| evaluateMeasure_shouldUseProvidedPeriod | 通過 | 0.002s |
| evaluateMeasure_singlePatientFailure_shouldContinueOthers | 通過 | 0.003s |
| evaluateMeasure_scoringCalculation_shouldComputeCorrectly | 通過 | 0.003s |
| evaluateMeasure_shouldUseDefaultPeriodWhenNotProvided | 通過 | 0.002s |
| evaluateMeasure_executionError_shouldReturnErrorStatus | 通過 | 0.003s |




### com.cqlplatform.service.measure.MeasureReportNormalizerTest

| 項目 | 數值 |
|------|------|
| 測試數 | 7 |
| 通過 | 7 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.093s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| proportionMeasure_writesGroupAndPopulations | 通過 | 0.082s |
| populationIdFallback | 通過 | 0.001s |
| persist_isIdempotent | 通過 | 0.001s |
| cvMeasure_flattensObservationStatistics | 通過 | 0.001s |
| complexReport_writesFullGraph | 通過 | 0.003s |
| nullReportId_throws | 通過 | 0.001s |
| emptyResult_deletesExistingAndPersistsNothing | 通過 | 0.001s |




### com.cqlplatform.service.measure.MeasureReportServiceVersionTrackingTest

| 項目 | 數值 |
|------|------|
| 測試數 | 5 |
| 通過 | 5 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.030s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| whenMeasureDefinitionIdNull_noRepoCallMade | 通過 | 0.014s |
| whenDefinitionMissing_reportSavesWithNullProvenance | 通過 | 0.006s |
| whenCqlIsNullOnDefinition_cqlHashIsNull | 通過 | 0.004s |
| whenCqlChanges_hashDiffers | 通過 | 0.002s |
| whenDefinitionExists_savedReportCapturesVersionAndHashes | 通過 | 0.002s |




### com.cqlplatform.service.measure.MeasureScoreCalculatorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 47 |
| 通過 | 47 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.085s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| proportionScore_standard | 通過 | 0.001s |
| normalizeAggregateMethod_nullOrBlank_defaultsToAverage | 通過 | 0.000s |
| proportionScore_zeroDenom_null | 通過 | 0.001s |
| calculateScore_cohortDispatchesToNullFromThisEntryPoint | 通過 | 0.000s |
| cohortScore_nullIp_shouldReturnNull | 通過 | 0.001s |
| ratioScore_viaDispatcher | 通過 | 0.000s |
| normalizeAggregateMethod_unknown_returnsNull_notSilentFallthrough | 通過 | 0.000s |
| cohortScore_zeroIp_shouldReturnZero_notNull | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[1] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[2] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[3] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[4] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[5] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[6] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[7] | 通過 | 0.001s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[8] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[9] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[10] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[11] | 通過 | 0.001s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[12] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[13] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[14] | 通過 | 0.001s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[15] | 通過 | 0.001s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[16] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[17] | 通過 | 0.001s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[18] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[19] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[20] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[21] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[22] | 通過 | 0.000s |
| normalizeAggregateMethod_shouldAcceptCanonicalsAndAliases(String, String)[23] | 通過 | 0.001s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[1] | 通過 | 0.021s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[2] | 通過 | 0.001s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[3] | 通過 | 0.001s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[4] | 通過 | 0.001s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[5] | 通過 | 0.000s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[6] | 通過 | 0.000s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[7] | 通過 | 0.008s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[8] | 通過 | 0.001s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[9] | 通過 | 0.001s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[10] | 通過 | 0.001s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[11] | 通過 | 0.001s |
| cvScore_aliasesAndCanonicals_computeCorrectly(String, double)[12] | 通過 | 0.001s |
| computeObservationStats_shouldNormalizeDisplayedMethod | 通過 | 0.002s |
| cvScore_unknownMethod_shouldReturnNull_notFallThroughToAverage | 通過 | 0.001s |
| cohortScore_shouldReturnIpCountAsDouble | 通過 | 0.001s |
| cvScore_nullOrBlankMethod_stillAverages | 通過 | 0.001s |




### com.cqlplatform.service.measure.MeasureValidationServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 9 |
| 通過 | 9 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.044s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| validateFull_noCqlContent_shouldReportError | 通過 | 0.005s |
| validateFull_measureNotFound_shouldThrow | 通過 | 0.002s |
| validateFull_missingTitle_shouldReportWarning | 通過 | 0.002s |
| validateFull_noTestCases_shouldReportWarning | 通過 | 0.005s |
| validateQuick_validMeasure_shouldReturnReport | 通過 | 0.002s |
| validateFull_missingName_shouldReportError | 通過 | 0.005s |
| validateFull_proportionMissingRequiredPopulations_shouldReportErrors | 通過 | 0.003s |
| validateFull_expressionNotInCql_shouldReportError | 通過 | 0.005s |
| validateFull_missingVersion_shouldReportError | 通過 | 0.003s |




### com.cqlplatform.service.measure.NormalizedMeasureReportReaderTest

| 項目 | 數值 |
|------|------|
| 測試數 | 7 |
| 通過 | 7 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.031s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| nullReportId_returnsEmpty | 通過 | 0.005s |
| proportionMeasure_rebuilds | 通過 | 0.008s |
| subjectIds_decoded | 通過 | 0.002s |
| cvMeasure_rebuildsObservationStatistics | 通過 | 0.003s |
| noGroups_returnsEmpty | 通過 | 0.002s |
| stratifiedMeasure_rebuildsStratifiers | 通過 | 0.003s |
| malformedSubjectIds_doesNotBreak | 通過 | 0.003s |




### com.cqlplatform.service.measure.PopulationEvaluatorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.013s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| ratio_patientNotInIp_nothingCounts | 通過 | 0.002s |
| ratio_numerIndependent_patientNotInDenomStillCountsInNumer | 通過 | 0.001s |
| extractObservationValues_numericValue_unchangedByFix | 通過 | 0.002s |
| ratio_denomExclusion_reducesDenom_notNumer | 通過 | 0.000s |
| ratio_numerExclusion_reducesNumer_notDenom | 通過 | 0.000s |
| proportion_patientInDenomAndNumer_countsBoth | 通過 | 0.000s |
| proportion_numerGatedByDenom_patientNotInDenomDoesNotCountInNumer | 通過 | 0.001s |
| ratio_noDenomExceptionsConcept | 通過 | 0.000s |
| extractObservationValues_nullValue_shouldReturnEmpty | 通過 | 0.000s |
| ratio_patientInDenomNotNumer_countsDenomOnly | 通過 | 0.001s |
| extractObservationValues_booleanTrue_shouldReturnOne | 通過 | 0.000s |
| extractObservationValues_booleanFalse_shouldReturnEmpty | 通過 | 0.000s |




### com.cqlplatform.service.measure.TestCaseServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 18 |
| 通過 | 18 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.403s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getTestCasesForMeasure_shouldReturnSortedList | 通過 | 0.081s |
| batchImport_withDateShift_shouldCallDateShiftService | 通過 | 0.008s |
| runAllTestCases_shouldRunAllAndReturnResults | 通過 | 0.133s |
| create_shouldSaveAndReturnDto | 通過 | 0.003s |
| extractPatientIdFromBundle_validBundle_shouldReturnId | 通過 | 0.002s |
| getById_notFound_shouldReturnEmpty | 通過 | 0.007s |
| extractPatientIdFromBundle_emptyBundle_shouldReturnDefault | 通過 | 0.003s |
| delete_shouldCallRepository | 通過 | 0.005s |
| runTestCase_fail_shouldReturnFailStatus | 通過 | 0.078s |
| runTestCase_noCql_shouldReturnError | 通過 | 0.002s |
| create_measureNotFound_shouldThrow | 通過 | 0.002s |
| getById_found_shouldReturnTestCase | 通過 | 0.002s |
| buildComparisons_mismatching_shouldHaveMatchFalse | 通過 | 0.002s |
| buildComparisons_matching_shouldHaveMatchTrue | 通過 | 0.002s |
| extractPatientIdFromBundle_nullBundle_shouldReturnDefault | 通過 | 0.002s |
| batchImport_shouldImportSuccessfully | 通過 | 0.003s |
| runTestCase_pass_shouldReturnPassStatus | 通過 | 0.057s |
| update_shouldMapFieldsCorrectly | 通過 | 0.003s |




### com.cqlplatform.service.PasswordResetServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 11 |
| 通過 | 11 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.067s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| changePassword_wrongCurrent_shouldReturnFalse | 通過 | 0.039s |
| changePassword_userNotFound_shouldThrow | 通過 | 0.002s |
| requestPasswordReset_unknownEmail_shouldNotThrow | 通過 | 0.003s |
| adminResetPassword_notFound_shouldThrow | 通過 | 0.002s |
| changePassword_correctCurrent_shouldSucceed | 通過 | 0.002s |
| resetPassword_expiredToken_shouldReturnFalse | 通過 | 0.001s |
| adminResetPassword_shouldResetPasswordAndScheduleEmail | 通過 | 0.004s |
| resetPassword_usedToken_shouldReturnFalse | 通過 | 0.002s |
| requestPasswordReset_validEmail_shouldGenerateTokenAndSendEmail | 通過 | 0.004s |
| resetPassword_invalidToken_shouldReturnFalse | 通過 | 0.001s |
| resetPassword_validToken_shouldSucceed | 通過 | 0.002s |




### com.cqlplatform.service.RefreshTokenServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.053s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| refreshTokens_validToken_shouldRotate | 通過 | 0.030s |
| refreshTokens_expiredToken_shouldThrow | 通過 | 0.002s |
| refreshTokens_slidingWindowCappedAtAbsoluteExpiry | 通過 | 0.003s |
| refreshTokens_revokedToken_shouldRevokeEntireFamilyAndThrow | 通過 | 0.002s |
| revokeByToken_notFound_shouldNotThrow | 通過 | 0.002s |
| refreshTokens_notFound_shouldThrow | 通過 | 0.003s |
| cleanupExpiredTokens_shouldDelegateToRepository | 通過 | 0.001s |
| refreshTokens_absoluteExpiryExceeded_shouldThrow | 通過 | 0.002s |
| refreshTokens_disabledUser_shouldRevokeFamilyAndThrow | 通過 | 0.002s |
| createTokenPair_shouldReturnValidPairAndStoreHash | 通過 | 0.002s |
| revokeAllForUser_shouldDelegateToRepository | 通過 | 0.002s |
| revokeByToken_shouldRevokeFamilyIfFound | 通過 | 0.001s |




### com.cqlplatform.service.UserApiKeyServiceTest

| 項目 | 數值 |
|------|------|
| 測試數 | 12 |
| 通過 | 12 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.080s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| validateApiKey_invalidKey_shouldReturnEmpty | 通過 | 0.061s |
| deactivateAllKeys_noKeys_shouldReturnZero | 通過 | 0.001s |
| validateApiKey_validKey_deletedUser_shouldReturnEmpty | 通過 | 0.001s |
| revokeKey_nonOwner_shouldReturnFalse | 通過 | 0.003s |
| deactivateAllKeys_shouldDelegateToRepository | 通過 | 0.001s |
| generateApiKey_shouldReturnEntityWithCqlPrefix | 通過 | 0.001s |
| generateApiKey_shouldGenerateUniqueKeys | 通過 | 0.001s |
| validateApiKey_validKey_enabledUser_shouldReturnUsername | 通過 | 0.002s |
| revokeKey_notFound_shouldReturnFalse | 通過 | 0.001s |
| listKeys_shouldDelegateToRepository | 通過 | 0.001s |
| revokeKey_owner_shouldDeactivate | 通過 | 0.001s |
| validateApiKey_validKey_disabledUser_shouldReturnEmpty | 通過 | 0.001s |




### com.cqlplatform.util.ContentHashTest

| 項目 | 數值 |
|------|------|
| 測試數 | 6 |
| 通過 | 6 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.006s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| sameInput_sameOutput | 通過 | 0.001s |
| knownStringVector_shouldMatch | 通過 | 0.001s |
| utf8ChineseInput_shouldHashConsistently | 通過 | 0.000s |
| nullInput_shouldReturnNull | 通過 | 0.001s |
| emptyString_shouldReturnKnownSha256 | 通過 | 0.001s |
| byteLevelChange_producesDifferentHash | 通過 | 0.001s |




### com.cqlplatform.util.CsvUtilsTest

| 項目 | 數值 |
|------|------|
| 測試數 | 15 |
| 通過 | 15 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.021s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| formulaTrigger_withQuoteAndComma_shouldEscapeAll | 通過 | 0.002s |
| null_shouldReturnEmpty | 通過 | 0.000s |
| formulaTrigger_withComma_shouldBeQuotedAndPrefixed | 通過 | 0.000s |
| empty_shouldReturnEmpty | 通過 | 0.000s |
| plainValue_shouldPassThrough | 通過 | 0.000s |
| formulaTrigger_plain_shouldBePrefixed(String)[1] | 通過 | 0.001s |
| formulaTrigger_plain_shouldBePrefixed(String)[2] | 通過 | 0.001s |
| formulaTrigger_plain_shouldBePrefixed(String)[3] | 通過 | 0.001s |
| formulaTrigger_plain_shouldBePrefixed(String)[4] | 通過 | 0.000s |
| formulaTrigger_plain_shouldBePrefixed(String)[5] | 通過 | 0.001s |
| formulaTrigger_plain_shouldBePrefixed(String)[6] | 通過 | 0.001s |
| valueWithNewline_shouldBeQuoted | 通過 | 0.000s |
| valueWithComma_shouldBeQuoted | 通過 | 0.001s |
| negativeNumber_shouldBePrefixed | 通過 | 0.000s |
| valueWithDoubleQuote_shouldBeQuotedAndEscaped | 通過 | 0.001s |




### com.cqlplatform.util.ExecutionErrorClassifierTest

| 項目 | 數值 |
|------|------|
| 測試數 | 14 |
| 通過 | 14 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.013s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| buildErrorInfo_explicitPhase_overridesClassification | 通過 | 0.001s |
| buildErrorInfo_autoClassify_populatesAllFields | 通過 | 0.002s |
| classify_null_shouldReturnUnknown | 通過 | 0.000s |
| classify_nestedCause_withTranslationClass_shouldReturnCqlTranslation | 通過 | 0.000s |
| classify_wrappedTranslatorMessage_shouldReturnCqlTranslation | 通過 | 0.001s |
| classify_pureRuntimeException_shouldReturnCqlExecution | 通過 | 0.000s |
| buildErrorInfo_noOwnPackageFrames_stackTraceSummaryIsNull | 通過 | 0.000s |
| classify_classSimpleName_Compiler_shouldReturnCqlTranslation | 通過 | 0.001s |
| classify_causeLoop_shouldTerminate | 通過 | 0.001s |
| buildErrorInfo_nullThrowable_returnsPhaseOnly | 通過 | 0.000s |
| fromCdsPhase_null_returnsUnknown | 通過 | 0.000s |
| fromCdsPhase_eachLegacyValue_mapsCleanly | 通過 | 0.000s |
| classify_classSimpleName_Translation_shouldReturnCqlTranslation | 通過 | 0.000s |
| buildErrorInfo_ownPackageFrames_limitedToFive | 通過 | 0.001s |




### com.cqlplatform.validation.HookContextRequirementsTest

| 項目 | 數值 |
|------|------|
| 測試數 | 25 |
| 通過 | 25 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.039s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| getRequiredFields_encounterStart_shouldIncludeEncounterId | 通過 | 0.001s |
| getContextFieldDefinitions_orderSelect_shouldIncludeObjectFields | 通過 | 0.001s |
| validateContext_unknownHook_shouldNotThrow | 通過 | 0.000s |
| validateContext_nullContext_shouldThrow | 通過 | 0.001s |
| getRequiredFields_orderSign_shouldIncludeDraftOrders | 通過 | 0.001s |
| validateContext_appointmentBook_missingAppointments_shouldThrow | 通過 | 0.001s |
| validateContext_encounterStart_missingEncounterId_shouldThrow | 通過 | 0.001s |
| validateContext_patientView_missingUserId_shouldThrow | 通過 | 0.001s |
| getRequiredFields_allHookTypes_shouldReturnNonEmpty(String)[1] | 通過 | 0.001s |
| getRequiredFields_allHookTypes_shouldReturnNonEmpty(String)[2] | 通過 | 0.001s |
| getRequiredFields_allHookTypes_shouldReturnNonEmpty(String)[3] | 通過 | 0.000s |
| getRequiredFields_allHookTypes_shouldReturnNonEmpty(String)[4] | 通過 | 0.002s |
| getRequiredFields_allHookTypes_shouldReturnNonEmpty(String)[5] | 通過 | 0.000s |
| getRequiredFields_allHookTypes_shouldReturnNonEmpty(String)[6] | 通過 | 0.002s |
| validateContext_patientView_missingBothFields_shouldListBoth | 通過 | 0.002s |
| validateContext_encounterStart_withAllRequired_shouldNotThrow | 通過 | 0.001s |
| getRequiredFields_appointmentBook_shouldIncludeAppointments | 通過 | 0.001s |
| validateContext_orderSelect_missingSelectionsAndDraftOrders_shouldThrow | 通過 | 0.002s |
| validateContext_patientView_withAllRequired_shouldNotThrow | 通過 | 0.001s |
| getRequiredFields_orderSelect_shouldIncludeSelectionsAndDraftOrders | 通過 | 0.000s |
| getRequiredFields_unknownHook_shouldReturnEmpty | 通過 | 0.000s |
| validateContext_orderSelect_withAllRequired_shouldNotThrow | 通過 | 0.001s |
| getContextFieldDefinitions_patientView_shouldReturnCorrectFields | 通過 | 0.001s |
| validateContext_blankUserId_shouldCount_asMissing | 通過 | 0.002s |
| validateContext_appointmentBook_withAllRequired_shouldNotThrow | 通過 | 0.001s |




### com.cqlplatform.validation.HookTypeValidatorTest

| 項目 | 數值 |
|------|------|
| 測試數 | 6 |
| 通過 | 6 |
| 失敗 | 0 |
| 錯誤 | 0 |
| 略過 | 0 |
| 執行時間 | 0.007s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
| validate_nullHookType_shouldThrow | 通過 | 0.001s |
| validate_invalidHookType_shouldThrow | 通過 | 0.001s |
| validate_validHookTypes_shouldNotThrow | 通過 | 0.001s |
| validate_blankHookType_shouldThrow | 通過 | 0.000s |
| validate_deprecatedMedicationPrescribe_shouldSuggestReplacement | 通過 | 0.000s |
| validate_unknownHookType_shouldFallbackToGenericMessage | 通過 | 0.000s |





---

## 3. 前端測試結果 (Vitest)



---

## 4. 測試結論

大部分測試案例通過（通過率 99.9%），請審查失敗案例。


<!-- 由品質管理人員填寫最終結論 -->

---

*本文件由 CQL Platform 測試報告產生器自動產生*
