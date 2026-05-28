# CQL Platform — HIS 對接指南

> 本文件說明 CQL Platform 與外部 HIS / EHR 系統的整合方式、HIS 廠商需提供的 endpoint 規格、醫院 IT 在後台的設定步驟、以及常見故障排除。

---

## 0. TL;DR

| 你是誰 | 看哪幾章 |
|---|---|
| **HIS 廠商工程師**（要實作 endpoint 給我們連） | §1 → §2 → §3 → §9 附錄 |
| **醫院 IT / 系統管理員**（要在 CQL 平台後台設定連線） | §1 → §4 → §6 → §7 |
| **內部開發者**（要擴充對接支援） | §1 → §3 → §5 → §8 → §9 |

**設計重點：**

- **CQL Platform 不直接連 HIS 的資料庫。** 整合層統一走 **FHIR R4 REST API**。
- 平台已實作 4 種 FHIR 認證：`none` / `basic` / `bearer` / `smart_backend`（OAuth 2.0 Client Credentials + JWT assertion）。
- 平台已實作 **mTLS / 自家 CA 信任**：CA 憑證、Client 憑證、Client 私鑰皆 PEM 格式儲存（加密），TLS 最低版本可選。
- 平台已實作 **FHIR Subscription (REST-hook)**、**$export Bulk Export**、`$everything` patient compartment。
- 若 HIS 沒有 FHIR endpoint，需自建 **HL7v2 / CDA / 自家 DB → FHIR R4** 的中介層（§5）；中介層不在本平台範圍內，但平台支援的 endpoint 規格如 §3 所列。

---

## 1. 整合架構概覽

```
                ┌───────────────────────────────────────┐
                │           CQL Platform (我們)         │
                │                                       │
                │  Backend (Spring Boot 4 / Java 21)    │
                │  ┌─────────────────────────────────┐  │
                │  │ EhrConnectionService            │  │
                │  │ PatientSearchService            │  │
                │  │ PatientImportService            │  │
                │  │ AsyncPatientImportService       │  │
                │  │ FhirSubscriptionService         │  │
                │  │ FhirBulkExportService           │  │
                │  │ ConnectionHealthService         │  │
                │  └────────────┬────────────────────┘  │
                │               │ HAPI FHIR R4 Client   │
                │               │ (8.8.1)               │
                └───────────────┼───────────────────────┘
                                │
                                │  HTTPS (mTLS optional)
                                │  FHIR R4 REST
                                │  Basic / Bearer / SMART
                                ▼
                ┌───────────────────────────────────────┐
                │       HIS / EHR (對方)                │
                │                                       │
                │  情境 A: 已有 FHIR R4 endpoint        │
                │  情境 B: 需中介層 (HL7v2 → FHIR)      │
                └───────────────────────────────────────┘
```

**關鍵限制（請事先告知對接方）：**

- **FHIR R4 only**。內部 `FhirContext.forR4()` 寫死於 `CqlConfig.java` 與 `FhirClientFactory.java`。若 HIS 只提供 R5 / STU3 / DSTU2 endpoint，目前**無法**直接連，需要 FHIR 版本轉換層。
- 病人匯入優先呼叫 **`Patient/{id}/$everything`**（FHIR Patient Compartment operation），失敗才退回逐一搜尋 `Condition` / `Observation` / `MedicationRequest` / `Encounter` / `Procedure`。HIS 若能實作 `$everything` 可大幅減少 round-trip。

---

## 2. 對接路徑決策

回答以下三題，會自動落到對應方案：

1. **HIS 是否已有 FHIR R4 REST endpoint？**
   - 是 → 走 §3 **快通道**
   - 否 → 走 §5 **中介層方案**

2. **是否需即時推播（Push）？**
   - 是 → §3.6 FHIR Subscription（REST-hook）
   - 否 → §3.5 主動拉取（Search / Import / Bulk Export）

3. **是否需大量歷史資料初次匯入？**
   - 是 → §3.7 Bulk Export (`$export`)
   - 否 → §3.5 個案 import 或批次 import API

---

## 3. 快通道：HIS 已有 FHIR R4 Endpoint

> 本章節給 **HIS 廠商工程師** 看。實作完以下規格後，醫院 IT 即可在 CQL Platform 後台新增 EhrConnection 完成對接。

### 3.1 必須提供的 endpoint

| FHIR Endpoint | 用途 | 必/選 |
|---|---|---|
| `GET /metadata` | 連線測試會抓 `CapabilityStatement`，回傳 server name + FHIR version | **必須** |
| `GET /Patient?identifier=...&family=...&given=...` | 病人搜尋 | **必須** |
| `GET /Patient/{id}` | 讀取單一 Patient 資源 | **必須** |
| `GET /Patient/{id}/$everything` | 一次拉病人 compartment 全部資料 | 強烈建議 |
| `GET /Condition?patient={id}` | $everything 缺席時 fallback | 必須 |
| `GET /Observation?patient={id}` | 同上 | 必須 |
| `GET /MedicationRequest?patient={id}` | 同上 | 必須 |
| `GET /Encounter?patient={id}` | 同上 | 必須 |
| `GET /Procedure?patient={id}` | 同上 | 必須 |
| `POST /Subscription` + REST-hook | 即時資料變更通知（如需 push 模式） | 選 |
| `POST /$export` (Group / System / Patient) | 大量資料匯出 | 選 |

### 3.2 Patient 搜尋必要參數

CQL Platform 的搜尋介面（`PatientSearchService`）會用以下 query：

```
Patient?identifier=<國民身分證或 MRN>
Patient?family=<姓>&given=<名>
```

- `identifier`：用於國民身分證、MRN 等任意識別碼；HIS 端必須將這些識別碼納入 `Patient.identifier[*].value` 索引。
- `family` / `given`：標準 FHIR string search modifier；HIS 端應支援 contains / starts-with 比對（HAPI 預設行為）。

### 3.3 Patient 資源必要欄位

| 欄位 | 為什麼需要 |
|---|---|
| `Patient.identifier[*].value` | 主要識別碼，醫院內部 ID / 健保 / 身分證等 |
| `Patient.identifier[*].use` | 用於 `extractPrimaryIdentifier()` 優先取 `official`，無則回退第一個有值的 |
| `Patient.name[0].family` / `given[]` | 顯示用 |
| `Patient.birthDate` | CQL 計算年齡常用 |
| `Patient.gender` | CQL 性別篩選常用 |

### 3.4 認證方式（4 選 1）

CQL Platform 會在新增 EhrConnection 時選擇下列其一。HIS 廠商需明確告知哪幾種可支援。

#### (a) `none` — 不認證

僅供測試環境。生產環境**請勿使用**。

#### (b) `basic` — HTTP Basic Auth

```http
Authorization: Basic <base64(username:password)>
```

平台儲存的 `credentials` 欄位 JSON 結構：

```json
{ "username": "string", "password": "string" }
```

#### (c) `bearer` — 靜態 Bearer Token

```http
Authorization: Bearer <token>
```

平台 `credentials` JSON：

```json
{ "token": "string" }
```

適用於 HIS 自訂的長效 API key。token 過期需手動更新 EhrConnection。

#### (d) `smart_backend` — SMART on FHIR Backend Services

OAuth 2.0 Client Credentials grant + RS384 簽章 JWT assertion（HL7 SMART Backend Services profile）。

**HIS 端需提供：**

1. Token endpoint URL（OAuth 2.0 `/token`）
2. Client registration（接受我們上傳的 public key）

**HIS 收到的 token 請求格式：**

```http
POST <token_endpoint> HTTP/1.1
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
&client_assertion=<RS384-signed JWT>
&scope=<requested scopes>
```

JWT assertion claims：

| Claim | 值 |
|---|---|
| `iss` | client_id |
| `sub` | client_id |
| `aud` | token endpoint URL |
| `jti` | UUID (each request unique) |
| `iat` | now |
| `exp` | now + 300s |
| (header) `alg` | `RS384` |
| (header) `typ` | `JWT` |

**HIS 回傳預期：**

```json
{
  "access_token": "<token>",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "..."
}
```

> 程式碼參考：`SmartBackendTokenService.java` — token 快取 TTL = `expires_in - 60s`，預設 lifetime 300s。

平台 `credentials` JSON：

```json
{
  "clientId": "string",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
  "scopes": "system/*.read"
}
```

### 3.5 主動拉取流程（Pull 模式）

```
1. 醫院 IT 在後台「測試連線」
   → CQL 後端 GET <fhir_server_url>/metadata
   → 解析 CapabilityStatement.software.name + fhirVersion

2. 病人搜尋
   → GET /Patient?identifier=A123456789

3. 預覽（看有多少 Condition/Observation/...）
   → GET /Condition?patient={id}&_summary=count
   → GET /Observation?patient={id}&_summary=count
   → ...

4. 匯入（建立測試案例或量測對象）
   → 先試 GET /Patient/{id}/$everything
   → 失敗則 fallback：逐一 GET /Patient/{id} + 5 種 resource search
   → 序列化為 Bundle，存入 patient_import.bundle_json
```

### 3.6 Push 模式 — FHIR Subscription (REST-hook)

CQL Platform 會在 HIS 端建立 `Subscription` 資源：

```json
{
  "resourceType": "Subscription",
  "status": "requested",
  "criteria": "Observation?code=http://loinc.org|XXXX",
  "reason": "CQL Platform data sync",
  "channel": {
    "type": "rest-hook",
    "endpoint": "https://<cql-platform>/api/ehr/subscriptions/callback",
    "payload": "application/fhir+json"
  }
}
```

**HIS 觸發通知時：**

```http
POST https://<cql-platform>/api/ehr/subscriptions/callback HTTP/1.1
X-Subscription-Id: <fhir subscription id>
Content-Type: application/fhir+json

{ ... resource JSON ... }
```

> 平台會用 `X-Subscription-Id` 反查本地 `fhir_subscription` 記錄。HIS 必須帶上這個 header（HAPI 預設行為），或自行於 endpoint URL 帶上 query 識別。callback base URL 由環境變數 `FHIR_SUBSCRIPTION_CALLBACK_URL` 設定。

### 3.7 Bulk Export (`$export`)

CQL Platform 會發 kick-off 請求：

```http
GET /$export?_outputFormat=application/fhir+ndjson&_since=2026-01-01T00:00:00Z&_type=Patient,Condition,Observation
Accept: application/fhir+json
Prefer: respond-async
```

HIS 應回應 `202 Accepted` 並在 `Content-Location` header 給 status URL，後續平台會 polling 該 URL。

> 程式碼參考：`FhirBulkExportService.java`。Resilience4j circuit breaker (`fhirDataProvider`) 包覆全部 export 操作。

### 3.8 TLS / mTLS 規格

- TLS 最低版本：預設 `TLSv1.2`（每連線可設 `TLSv1.3`）
- 平台支援上傳：
  - **CA 憑證**（PEM）— 用於信任 HIS 自簽 / 內部 PKI 的 server cert
  - **Client 憑證 + Client 私鑰**（PEM）— mTLS（雙向 TLS）
- Hostname verification 預設 **強制開啟**；要關閉需 sysadmin 在 `application.yml` 設 `security.tls.allow-disable-hostname-verification=true`（僅限非 prod 環境）

### 3.9 TW Core IG 對齊

平台內建 TW Core IG package：

| 項目 | 值 |
|---|---|
| Package id | `tw.gov.mohw.twcore` |
| 版本 | 0.3.2 |
| Canonical URL | `https://twcore.mohw.gov.tw/ig/twcore` |
| Base FHIR | 4.0.1 |
| 載入位置 | `classpath:fhir-packages/twcore/package.tgz` |

HIS 端 Resource 若有 `meta.profile` 宣告，建議使用對應的 TW Core profile URL，平台可進行 profile-aware 驗證。常用 code system：SNOMED CT (`http://snomed.info/sct`)、LOINC、健保署藥品碼。

### 3.10 範例：HIS 廠商驗收清單

完成後請以 curl 驗證：

```bash
# 1. metadata 必須回 R4
curl -k https://his.example.com/fhir/metadata \
  -H "Accept: application/fhir+json" \
  | jq '.fhirVersion, .software.name'
# 預期：4.0.1, "<your server name>"

# 2. Patient 搜尋必須支援 identifier
curl -k 'https://his.example.com/fhir/Patient?identifier=A123456789' \
  -H "Accept: application/fhir+json"

# 3. $everything 必須回 Bundle
curl -k 'https://his.example.com/fhir/Patient/123/$everything' \
  -H "Accept: application/fhir+json"

# 4. (若啟用 OAuth) Token endpoint 必須接受 JWT assertion
curl -X POST https://his.example.com/auth/token \
  -d 'grant_type=client_credentials' \
  -d 'client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer' \
  -d 'client_assertion=<signed_jwt>' \
  -d 'scope=system/*.read'
```

---

## 4. 醫院 IT 操作（CQL Platform 後台）

> 本章節給 **醫院 IT / 系統管理員**。前提：HIS 廠商已提供連線資訊。

### 4.1 權限需求

| 操作 | 角色 |
|---|---|
| 檢視連線列表 / 看健康監控 | 任何登入使用者 |
| 新增 / 修改 / 刪除連線、測試連線 | `ADMIN` 或 `DEPARTMENT_ADMIN` |
| 病人搜尋、import、批次 import、Subscription | `ADMIN` 或 `DEPARTMENT_ADMIN` |
| 重試失敗 import | `ADMIN` 或 `DEPARTMENT_ADMIN` |

### 4.2 新增 EhrConnection

```http
POST /api/ehr/connections
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "name": "台大醫院 EMR",
  "fhirServerUrl": "https://emr.ntuh.gov.tw/fhir",
  "authType": "smart_backend",
  "credentials": "{\"clientId\":\"...\",\"privateKey\":\"-----BEGIN PRIVATE KEY-----\\n...\",\"scopes\":\"system/*.read\"}",
  "tokenEndpoint": "https://emr.ntuh.gov.tw/auth/token",
  "tlsEnabled": true,
  "caCertPem": "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----",
  "clientCertPem": "-----BEGIN CERTIFICATE-----\n...",
  "clientKeyPem": "-----BEGIN PRIVATE KEY-----\n...",
  "tlsMinVersion": "TLSv1.2",
  "hostnameVerification": true,
  "department": "心臟內科"
}
```

**重點：**

- `credentials` 為 JSON 字串（注意要 escape）；DB 端透過 `EncryptionConverter` 自動加密儲存。
- `caCertPem` / `clientCertPem` / `clientKeyPem` 同樣自動加密，且 API 回應永遠不會回傳（`@JsonProperty(WRITE_ONLY)`）。
- `department` 可選，用於按部門隔離權限與列表過濾。

### 4.3 測試連線

```http
POST /api/ehr/connections/{id}/test
```

成功會把 status 標 `connected`，`lastTestMessage` 寫入 `"Connected successfully. Server: <name>, FHIR version: <version>"`。失敗則 status = `error`、訊息為例外短摘（最長 490 字元）。

### 4.4 病人搜尋 / 預覽 / 匯入

```http
# 搜尋
GET /api/ehr/connections/{id}/patients?nationalId=A123456789
GET /api/ehr/connections/{id}/patients?family=王&given=小明

# 預覽
GET /api/ehr/connections/{id}/patients/{patientId}/preview

# 匯入（可選 measureId，匯入後自動建立 test case）
POST /api/ehr/connections/{id}/patients/{patientId}/import?measureId=42
```

### 4.5 批次匯入

```http
POST /api/ehr/connections/{id}/patients/batch-import
{
  "patientIds": ["123", "456", "789"],
  "measureId": 42
}

# 查進度
GET /api/ehr/batch-imports/{jobId}

# 取消
POST /api/ehr/batch-imports/{jobId}/cancel
```

### 4.6 FHIR Subscription

```http
POST /api/ehr/connections/{id}/subscriptions
{
  "criteria": "Observation?code=http://loinc.org|2160-0",
  "reason": "監測 eGFR 變化"
}

# 列表 / 查單筆 / 同步狀態 / 刪除
GET    /api/ehr/subscriptions
GET    /api/ehr/subscriptions/{id}
POST   /api/ehr/subscriptions/{id}/sync
DELETE /api/ehr/subscriptions/{id}
```

### 4.7 健康監控

```http
GET  /api/ehr/health/overview                         # 全部連線
GET  /api/ehr/health/connections/{id}/history?hours=24
POST /api/ehr/health/connections/{id}/check          # 手動觸發
GET  /api/ehr/health/circuit-breakers                # 看 circuit breaker 狀態
```

`ConnectionHealthService` 排程定期檢查所有 active 連線。發生連續失敗時 Resilience4j `fhirDataProvider` circuit breaker 會 OPEN，後續呼叫直接 fail-fast，狀態可從 circuit-breakers endpoint 看。

### 4.8 失敗匯入處理

```http
GET    /api/ehr/failed-imports?status=PENDING_RETRY
POST   /api/ehr/failed-imports/{id}/retry
DELETE /api/ehr/failed-imports/{id}
```

平台會記錄失敗的 import（含原因、HTTP 狀態），並透過 `ImportRetryService` 提供手動或自動重試管道。

---

## 5. 中介層方案（HIS 沒有 FHIR Endpoint）

> 本章節提供建議；**中介層本身不是 CQL Platform 的一部分**，需要 HIS 廠商或第三方整合商建置。中介層完成後，從 CQL Platform 看起來與「快通道」HIS 無異。

### 5.1 建議架構

```
┌─────────┐   HL7v2 / CDA / DB    ┌──────────────────┐   FHIR R4    ┌────────┐
│   HIS   │ ────────────────────► │  中介層 (Bridge)  │ ───────────► │ CQL    │
│         │                       │                  │              │Platform│
└─────────┘                       │  ‧ 解析 HL7v2    │              └────────┘
                                  │  ‧ Mapping → FHIR│
                                  │  ‧ Caching       │
                                  │  ‧ 認證          │
                                  └──────────────────┘
```

選項：

| 方案 | 適合場景 |
|---|---|
| **HAPI FHIR Server** + 自寫 mapping interceptor | 已有 HL7v2 介接，想最小成本起步 |
| **Microsoft FHIR Server / Azure API for FHIR** | 雲端、已採用 Azure |
| **MITRE Synthea / IBM LinuxForHealth** | 開源、可客製 |
| **自寫 Spring Boot + HAPI client lib** | 已有 Java 技術棧、需深度客製 |

### 5.2 常見資料對應

HL7v2 / 自家 DB 欄位 → FHIR R4 Resource 對應只列高頻項目，完整對應請參考 [HL7 FHIR mapping documentation](https://hl7.org/fhir/R4/hl7v2-mappings.html)：

| HIS 來源 | FHIR R4 |
|---|---|
| PID-3 (Patient Identifier List) | `Patient.identifier[*]` |
| PID-5 (Patient Name) | `Patient.name[0].family` + `given[]` |
| PID-7 (Date of Birth) | `Patient.birthDate` |
| PID-8 (Sex) | `Patient.gender` |
| OBX (Observation) | `Observation` resource |
| DG1 (Diagnosis) | `Condition` resource |
| RXE / RXO (Pharmacy Order) | `MedicationRequest` resource |
| PV1 (Visit) | `Encounter` resource |

**對應到 TW Core**：建議在中介層輸出時加上 `Resource.meta.profile`，指向 TW Core 對應 profile，方便我們做 profile-aware validation。

### 5.3 中介層必須暴露的對外行為

對齊 §3 規格即可。最低限度：

1. `GET /metadata` 回 FHIR 4.0.1 CapabilityStatement
2. Patient search by `identifier` / `family` / `given`
3. `GET /Patient/{id}/$everything`（或實作 5 種 fallback search）
4. HTTPS + 認證（建議 `smart_backend`）

---

## 6. 安全與合規

### 6.1 機密欄位儲存

下列欄位皆透過 `EncryptionConverter` 在 JPA 層自動 AES 加密；API 回應永遠不會回傳這些欄位（`@JsonProperty(WRITE_ONLY)`）：

- `credentials`（auth credentials JSON）
- `ca_cert_pem` / `client_cert_pem` / `client_key_pem`

> 加密金鑰由 `application.yml` 的 `app.encryption.secret` 提供。Production 必須以環境變數 `APP_ENCRYPTION_SECRET` 注入，**勿** commit 至 repo。

### 6.2 TLS 預設值

| 項目 | 預設 | 可調 |
|---|---|---|
| `tlsMinVersion` | `TLSv1.2` | `TLSv1.3` |
| `hostnameVerification` | `true` | `false`（但需 sysadmin 開全域 kill-switch） |
| Cipher suite | JDK 預設 | 不開放 per-connection 客製 |

### 6.3 個資與審計

- 所有 EhrConnection CRUD、connection test、patient import 操作會寫入 `log.info`，含使用者名稱、connection name、影響的 patient id。
- `patient_import.imported_by` 欄位記錄匯入者，retention 由量測模組決定（預設不刪）。
- 病人 Bundle JSON (`patient_import.bundle_json`) 含 PHI；DB 備份時必須加密。

### 6.4 SAML / OAuth 信任邊界

- **CQL Platform → HIS**：見 §3.4 認證方式。
- **CQL Platform 內部**：使用者一律 JWT；JWT token version 機制使停用帳號 30 秒內所有舊 token 失效。
- **HIS 通知回 callback**：目前實作 `/api/ehr/subscriptions/callback` **未強制驗證來源**（依賴 `X-Subscription-Id` 對映本地紀錄）；若 HIS 可外連此 endpoint，建議在反向代理層用 IP 白名單或加入 HMAC 驗證。

### 6.5 法遵 / 合規

- 符合 IEC 62304 軟體生命週期：對接相關 PR 須建立 [需求] / [設計] / [風險] / [驗證] Issue（見專案 CLAUDE.md）。
- 風險評估點：認證失敗、TLS 降版、callback 偽造、Bundle 含 PHI 落地。

---

## 7. 故障排除

### 7.1 連線測試失敗

| `lastTestMessage` 開頭 | 可能原因 | 處理 |
|---|---|---|
| `Connection failed: PKIX path building failed` | CA 憑證沒上傳 / 信任鏈不完整 | 上傳完整 CA 鏈到 `caCertPem` |
| `Connection failed: No subject alternative names matching IP` | hostname 不一致 | 確認 URL 用對方 cert 上的 hostname；非 prod 可暫關 hostname verification |
| `Connection failed: HTTP 401` | 認證失敗 | 重新檢查 credentials JSON 欄位拼字（`username` 不是 `user`） |
| `Connection failed: HTTP 403` | scope 不足（SMART） | 通知 HIS 端在 client registration 加上 `system/*.read` |
| `Connection failed: Read timed out` | 對方響應 > 10s | 檢查網路 RTT、看 HIS 端是否需 warm-up |
| `Connection failed: Failed to obtain SMART Backend Services access token` | JWT 簽章失敗 / private key 格式錯 | 確認 `privateKey` 為 RSA PKCS#8 PEM；公鑰已上傳至 HIS |

### 7.2 病人匯入 `$everything` 失敗

正常行為：fallback 到 5 個 resource type 個別 search。log 會出現：

```
$everything operation failed for patient X, falling back to individual searches
```

若 fallback 也失敗，會記錄到 `failed_import` 表，可從 `/api/ehr/failed-imports` 找回。

### 7.3 Subscription 沒收到通知

逐項檢查：

1. `/api/ehr/subscriptions/{id}/sync` 看 HIS 端 status 是否為 `active`（不是 `requested` / `error`）
2. 後端環境變數 `FHIR_SUBSCRIPTION_CALLBACK_URL` 是否設為對外可達 URL
3. HIS 防火牆是否允許出向到 callback URL
4. callback URL 是 HTTPS，憑證是否被 HIS 端信任

### 7.4 Circuit Breaker OPEN

連續失敗會觸發 Resilience4j `fhirDataProvider` 開斷。狀態：

```http
GET /api/ehr/health/circuit-breakers
```

排除根因後（例如 HIS 服務恢復），circuit breaker 會自動進入 HALF_OPEN 試探，成功則 CLOSE。手動 force close 需重啟後端。

### 7.5 病人 Bundle 太大

`$everything` 對長照住院病人可能回 5+ MB。目前 `patient_import.bundle_json` 為 PostgreSQL `TEXT`，理論無上限但前端載入會慢。建議：

- 用 Subscription + 增量 sync 取代全量 import
- 或在中介層做 `_type` filter（如只取最近 3 年）

---

## 8. 開發者參考

### 8.1 關鍵類別索引

| 類別 | 職責 | 檔案 |
|---|---|---|
| `EhrIntegrationController` | REST API entry | `controller/EhrIntegrationController.java` |
| `EhrConnectionService` | Connection CRUD + 測試 | `service/fhir/EhrConnectionService.java` |
| `FhirClientFactory` | 建立 HAPI client、加 interceptor、TLS 配置 | `service/fhir/FhirClientFactory.java` |
| `SmartBackendTokenService` | OAuth + JWT assertion + token 快取 | `service/fhir/SmartBackendTokenService.java` |
| `TlsContextFactory` | 從 PEM 建 SSLContext | `service/fhir/TlsContextFactory.java` |
| `PatientSearchService` | Patient 搜尋 + preview | `service/fhir/PatientSearchService.java` |
| `PatientImportService` | 個案 import (`$everything` + fallback) | `service/fhir/PatientImportService.java` |
| `AsyncPatientImportService` | 批次 import job | `service/fhir/AsyncPatientImportService.java` |
| `FhirSubscriptionService` | Subscription CRUD + callback handler | `service/fhir/FhirSubscriptionService.java` |
| `FhirBulkExportService` | `$export` kickoff + status polling | `service/fhir/FhirBulkExportService.java` |
| `ConnectionHealthService` | 排程健康檢查 + circuit breaker 監控 | `service/fhir/ConnectionHealthService.java` |
| `ImportRetryService` | 失敗 import 記錄與重試 | `service/fhir/ImportRetryService.java` |
| `EncryptionConverter` | JPA AttributeConverter for credentials/PEM 欄位 | `security/EncryptionConverter.java` |

### 8.2 擴充新 auth 類型

1. 在 `EhrConnectionRequest.authType` 規格新增 enum 值
2. `FhirClientFactory.createAuthenticatedClient()` 加 `else if ("xxx".equals(...))` 分支
3. 若需 token 交換，仿 `SmartBackendTokenService` 寫一個快取服務
4. 在 frontend `EhrConnectionForm` 加對應 UI 欄位
5. i18n 同步更新 `locales/{en,zh-TW}/admin.json`
6. 寫 integration test 覆蓋新 auth path

### 8.3 擴充支援 FHIR R5

目前限制：`FhirContext.forR4()` 寫死於 `CqlConfig.java:30` 與 `FhirClientFactory.java:34`。要支援 R5：

1. 將 `FhirContext` 改成依 EhrConnection 的目標版本 lazy 建立並快取
2. cql-engine 4.5.0 有 R5 model resolver + type converter，但**無** R5 query generator 與完整 terminology provider
3. 影響範圍：所有 `org.hl7.fhir.r4.model.*` import 都需抽象化
4. **建議**先以 FHIR 版本轉換中介層解決，避免大規模改動

### 8.4 測試

```bash
# Backend
mvn=/c/Users/alumi/apache-maven-3.9.12/bin/mvn
$mvn -f backend/pom.xml test -Dtest='com.cqlplatform.service.fhir.*Test'

# Smoke test（含對 dev FHIR server 的端對端）
scripts/smoke/run.sh
```

---

## 9. 附錄

### 9.1 完整 API endpoint 表

| Method | Path | 描述 | 權限 |
|---|---|---|---|
| GET | `/api/ehr/connections` | 列出連線（可 `?department=`） | 任意 |
| GET | `/api/ehr/connections/{id}` | 取得單一連線 | 任意 |
| POST | `/api/ehr/connections` | 新增 | ADMIN/DEPT_ADMIN |
| PUT | `/api/ehr/connections/{id}` | 更新 | ADMIN/DEPT_ADMIN |
| DELETE | `/api/ehr/connections/{id}` | 軟刪除 | ADMIN/DEPT_ADMIN |
| POST | `/api/ehr/connections/{id}/test` | 測試連線 | ADMIN/DEPT_ADMIN |
| GET | `/api/ehr/connections/{id}/patients` | 搜尋病人 | ADMIN/DEPT_ADMIN |
| GET | `/api/ehr/connections/{id}/patients/{patientId}/preview` | 預覽病人資料量 | ADMIN/DEPT_ADMIN |
| POST | `/api/ehr/connections/{id}/patients/{patientId}/import` | 匯入病人 | ADMIN/DEPT_ADMIN |
| POST | `/api/ehr/connections/{id}/patients/batch-import` | 批次匯入 | ADMIN/DEPT_ADMIN |
| GET | `/api/ehr/batch-imports/{jobId}` | 查批次 job | 任意 |
| GET | `/api/ehr/batch-imports` | 列批次 job | 任意 |
| POST | `/api/ehr/batch-imports/{jobId}/cancel` | 取消批次 | ADMIN/DEPT_ADMIN |
| POST | `/api/ehr/connections/{id}/subscriptions` | 建立 Subscription | ADMIN/DEPT_ADMIN |
| GET | `/api/ehr/subscriptions` | 列出 Subscription | 任意 |
| GET | `/api/ehr/subscriptions/{id}` | 取得 Subscription | 任意 |
| POST | `/api/ehr/subscriptions/{id}/sync` | 同步遠端狀態 | ADMIN/DEPT_ADMIN |
| DELETE | `/api/ehr/subscriptions/{id}` | 刪除 Subscription | ADMIN/DEPT_ADMIN |
| POST | `/api/ehr/subscriptions/callback` | REST-hook callback（HIS 呼叫） | 公開 |
| GET | `/api/ehr/imports` | 匯入歷史 | 任意 |
| GET | `/api/ehr/failed-imports` | 失敗 import 列表 | 任意 |
| GET | `/api/ehr/failed-imports/{id}` | 失敗 import 詳情 | 任意 |
| POST | `/api/ehr/failed-imports/{id}/retry` | 手動重試 | ADMIN/DEPT_ADMIN |
| DELETE | `/api/ehr/failed-imports/{id}` | 移除失敗紀錄 | ADMIN/DEPT_ADMIN |
| GET | `/api/ehr/health/overview` | 健康總覽 | 任意 |
| GET | `/api/ehr/health/connections/{id}/history` | 健康歷史 | 任意 |
| POST | `/api/ehr/health/connections/{id}/check` | 手動健康檢查 | ADMIN/DEPT_ADMIN |
| GET | `/api/ehr/health/circuit-breakers` | Circuit breaker 狀態 | 任意 |

### 9.2 EhrConnectionRequest schema

```json
{
  "name": "string (必填, ≤255)",
  "fhirServerUrl": "string (必填, ≤2048, 必須合法 URL)",
  "authType": "none | basic | bearer | smart_backend",
  "credentials": "JSON string (≤10000, 加密儲存)",
  "tokenEndpoint": "string (≤500, smart_backend 必填)",
  "tlsEnabled": "boolean",
  "caCertPem": "PEM string (加密儲存)",
  "clientCertPem": "PEM string (加密儲存)",
  "clientKeyPem": "PEM string (加密儲存)",
  "tlsMinVersion": "TLSv1.2 | TLSv1.3",
  "hostnameVerification": "boolean (預設 true)",
  "department": "string (≤100)"
}
```

### 9.3 credentials JSON schema（依 authType）

```jsonc
// authType = "basic"
{ "username": "string", "password": "string" }

// authType = "bearer"
{ "token": "string" }

// authType = "smart_backend"
{
  "clientId": "string",
  "privateKey": "RSA PKCS#8 PEM",
  "scopes": "space-separated scope string"
}
```

### 9.4 資料表（V28 migration）

```
ehr_connection
  id, name, fhir_server_url, auth_type, credentials,
  token_endpoint, tls_enabled, ca_cert_pem, client_cert_pem, client_key_pem,
  tls_min_version, hostname_verification, department,
  status, last_tested_at, last_test_message,
  active, created_at, updated_at

patient_import
  id, connection_id (FK), patient_fhir_id, patient_identifier, patient_name,
  resource_count, bundle_json, target_measure_id (FK), target_test_case_id (FK),
  imported_by, created_at
```

後續 migration 增加：`fhir_subscription`、`batch_import_job`、`failed_import`、`connection_health_check`。

### 9.5 相關環境變數

| 變數 | 預設 | 用途 |
|---|---|---|
| `FHIR_SERVER_URL` | `http://hapi-fhir:8080/fhir` | 本平台預設 FHIR server（非對接 HIS） |
| `FHIR_TERMINOLOGY_URL` | `https://tx.fhir.org/r4` | Terminology 服務 |
| `FHIR_SUBSCRIPTION_CALLBACK_URL` | （空） | Subscription callback base URL，需設為對外可達 |
| `FHIR_IG_ENABLED` | `true` | TW Core IG 載入開關 |
| `APP_ENCRYPTION_SECRET` | （prod 必設） | 加密 credentials / PEM 欄位的金鑰 |
| `security.tls.allow-disable-hostname-verification` | `false` | 全域允許關閉 per-connection hostname 驗證 |

### 9.6 不在本次範圍

下列項目目前**未實作**，需另案規劃：

- 直接 HIS DB ↔ CQL DB 同步（JDBC / CDC / Kafka）
- HL7v2 / CDA 內建解析器
- FHIR R5 / STU3 / DSTU2 client
- Subscription callback HMAC 簽章驗證
- 自動 token rotation（除 SMART Backend 外）

如需上述支援，請以 PAT-NNN 開需求 issue 並依專案 TFDA 法規工作流（CLAUDE.md §TFDA）建立對應的設計/風險/驗證 Issue。

---

**文件版本：** v1.0
**最後更新：** 2026-05-28
**對應後端版本：** Spring Boot 4.0.6 / HAPI FHIR 8.8.1 / cql-engine 4.5.0
