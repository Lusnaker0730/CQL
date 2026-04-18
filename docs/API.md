# CQL 平台 API 參考手冊

> **版本**：1.0.0 ｜ **最後更新**：2026-02-22

---

## 目錄

1. [概述](#1-概述)
2. [認證 (Auth)](#2-認證-auth)
3. [CQL 操作](#3-cql-操作)
4. [品質指標 (Measures)](#4-品質指標-measures)
5. [FHIR 資源](#5-fhir-資源)
6. [CDS 決策支援](#6-cds-決策支援)
7. [CDS 撰寫工具](#7-cds-撰寫工具)
8. [EHR 整合](#8-ehr-整合)
9. [管理功能](#9-管理功能)
10. [指標目錄](#10-指標目錄)
11. [通知](#11-通知)
12. [使用者設定](#12-使用者設定)
13. [附錄](#13-附錄)

---

## 1. 概述

### 基本資訊

| 項目 | 說明 |
|------|------|
| Base URL | `http://localhost:8080` |
| 協定 | HTTP / HTTPS |
| 資料格式 | JSON（`application/json`） |
| 字元編碼 | UTF-8 |

### 認證方式

本平台使用 **JWT (JSON Web Token)** Bearer Token 認證。登入成功後取得 token，後續請求需於 HTTP Header 附帶：

```
Authorization: Bearer <token>
```

少數端點不需認證：`POST /api/auth/login`、`POST /api/auth/register`、`POST /api/auth/forgot-password`、`POST /api/auth/reset-password`、`GET /api/auth/okta/config`、`GET /cds-services`、`GET /.well-known/smart-configuration`。

### 回應格式

所有成功回應回傳 HTTP 2xx 狀態碼，回應 body 為 JSON 物件或陣列。

### 錯誤處理

錯誤回應格式：

```json
{
  "error": "錯誤訊息描述"
}
```

### 角色權限

| 角色 | 說明 |
|------|------|
| `USER` | 一般使用者 |
| `DEPARTMENT_ADMIN` | 部門管理員 |
| `ADMIN` | 系統管理員（可存取所有 `/api/admin` 端點） |

---

## 2. 認證 (Auth)

基礎路徑：`/api/auth`

### 2.1 使用者登入

**POST** `/api/auth/login`

使用帳號密碼登入，取得 JWT Token。

**請求參數：**

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| username | body | string | 是 | 使用者帳號 |
| password | body | string | 是 | 密碼 |

**請求範例：**

```json
{
  "username": "admin",
  "password": "password123"
}
```

**回應範例（200）：**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "role": "ADMIN",
  "expiresIn": 86400000,
  "forcePasswordChange": false
}
```

**錯誤回應（401）：**

```json
{
  "error": "Invalid username or password"
}
```

---

### 2.2 使用者註冊

**POST** `/api/auth/register`

註冊新帳號。

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| username | body | string | 是 | 帳號（3–50 字元） |
| password | body | string | 是 | 密碼（8–100 字元） |
| email | body | string | 否 | 電子信箱 |

**請求範例：**

```json
{
  "username": "newuser",
  "password": "securePass1",
  "email": "user@example.com"
}
```

**回應範例（200）：** 同 AuthResponse 格式。

---

### 2.3 取得目前使用者

**GET** `/api/auth/me`

回傳已認證使用者的個人資訊。

**回應範例（200）：**

```json
{
  "username": "admin",
  "email": "admin@example.com",
  "role": "ADMIN",
  "forcePasswordChange": false,
  "authProvider": "LOCAL",
  "displayName": "管理員"
}
```

---

### 2.4 忘記密碼

**POST** `/api/auth/forgot-password`

發送密碼重設信件。為防止帳號列舉，無論 email 是否存在都回傳成功。

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| email | body | string | 是 | 註冊時使用的 email |

**回應範例（200）：**

```json
{
  "message": "If an account with that email exists, a password reset link has been sent."
}
```

---

### 2.5 重設密碼

**POST** `/api/auth/reset-password`

透過重設 token 設定新密碼。

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| token | body | string | 是 | 重設令牌 |
| newPassword | body | string | 是 | 新密碼 |

---

### 2.6 變更密碼

**POST** `/api/auth/change-password`

已登入使用者變更自己的密碼。

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| currentPassword | body | string | 是 | 目前密碼 |
| newPassword | body | string | 是 | 新密碼 |

---

### 2.7 取得 Okta SSO 設定

**GET** `/api/auth/okta/config`

查詢 Okta SSO 是否啟用及設定資訊。無需認證。

**回應範例（200）：**

```json
{
  "enabled": true,
  "authorizationEndpoint": "https://dev-xxx.okta.com/oauth2/v1/authorize",
  "clientId": "0oa...",
  "scopes": "openid profile email"
}
```

---

### 2.8 Okta SSO 回呼

**POST** `/api/auth/okta/callback`

Okta OIDC 授權碼交換，自動 JIT 建立使用者。

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| code | body | string | 是 | 授權碼 |
| redirectUri | body | string | 是 | 重導向 URI |
| nonce | body | string | 是 | OIDC nonce |

**回應範例（200）：** 同 AuthResponse 格式。

---

## 3. CQL 操作

基礎路徑：`/api/cql`

### 3.1 翻譯 CQL

**POST** `/api/cql/translate`

將 CQL 程式碼翻譯為 ELM（Expression Logical Model）格式。

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| cql | body | string | 是 | CQL 原始碼 |
| enableAnnotations | body | boolean | 否 | 啟用註解（預設 true） |
| enableLocators | body | boolean | 否 | 啟用位置標記（預設 true） |
| enableResultTypes | body | boolean | 否 | 啟用結果類型（預設 true） |
| validateUnits | body | boolean | 否 | 驗證單位（預設 true） |

**請求範例：**

```json
{
  "cql": "library Test version '1.0'\nusing FHIR version '4.0.1'\ndefine InInitialPopulation: true"
}
```

**回應範例（200）：**

```json
{
  "success": true,
  "elm": "<xml>...</xml>",
  "elmJson": "{...}",
  "errors": [],
  "warnings": [],
  "metadata": {
    "libraryId": "Test",
    "libraryVersion": "1.0",
    "usings": ["FHIR 4.0.1"],
    "includes": [],
    "parameters": [],
    "valueSets": [],
    "codes": [],
    "concepts": [],
    "expressions": [
      {
        "name": "InInitialPopulation",
        "context": "Patient",
        "accessLevel": "Public",
        "resultType": "System.Boolean"
      }
    ]
  }
}
```

---

### 3.2 驗證 CQL

**POST** `/api/cql/validate`

驗證 CQL 語法與語義正確性。

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| cql | body | string | 是 | CQL 原始碼 |

**回應格式：** 同 CqlTranslationResponse。

---

### 3.3 執行 CQL

**POST** `/api/cql/execute`

對 FHIR 伺服器執行 CQL 查詢。

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| cql | body | string | 是 | CQL 原始碼 |
| libraryId | body | string | 否 | 函式庫 ID |
| patientId | body | string | 否 | 病患 ID |
| contextType | body | string | 否 | 上下文類型（預設 `Patient`） |
| parameters | body | object | 否 | 執行參數 |
| fhirServerUrl | body | string | 否 | FHIR 伺服器 URL |
| debugMode | body | boolean | 否 | 除錯模式（預設 false） |
| expressionNames | body | string[] | 否 | 指定執行的表達式名稱 |

**回應範例（200）：**

```json
{
  "success": true,
  "patientId": "patient-1",
  "results": {
    "InInitialPopulation": {
      "name": "InInitialPopulation",
      "value": true,
      "valueType": "System.Boolean",
      "displayValue": "true"
    }
  },
  "errors": ["Active Medications: <runtime error message>"],
  "warnings": ["FHIRHelpers.ToString(FHIR.code) resolves to multiple overloads"],
  "metadata": {
    "executionTimeMs": 150,
    "libraryId": "Test",
    "libraryVersion": "1.0",
    "fhirServerUrl": "http://hapi-fhir:8080/fhir",
    "resourcesRetrieved": 5
  }
}
```

**回應欄位**：

| 欄位 | 類型 | 說明 |
|------|------|------|
| `success` | boolean | 翻譯成功且引擎無頂層例外時為 true；per-define runtime error 仍可能出現在 `errors[]` |
| `results` | `Map<String, ExpressionResult>` | 每個 `define` 的結果，key 為 define 名稱 |
| `results[name].value` | any | 實際值；失敗的 define 為 null，`valueType` 為 `"Error"` |
| `results[name].displayValue` | string | 人類可讀字串；失敗時為 `Error at <loc>: <msg>` |
| `errors` | `string[]` \| null | per-define runtime error 摘要（PAT-066 新增）。batch 引擎失敗 + 後備 per-expression 再失敗時填入此欄位 |
| `warnings` | `string[]` \| null | CQL 翻譯期 warning 列表（PAT-066 新增），例如 deprecated function、choice-type ambiguity |
| `metadata.resourcesRetrieved` | int | 實際從 FHIR 撈取的資源筆數 |
| `debugTrace` | object \| null | 僅在 `debugMode=true` 時回傳，含 per-expression 時間、retrieve 追蹤、ELM JSON |

**執行語意保證（BUG-107 後）**：請求送出的 `cql` 文字**保證**就是引擎實際翻譯與執行的內容；即使 `cql_library` 表中有同名同版本舊版紀錄，引擎不會回退去執行舊版。驗證紀錄：`CqlExecutionIntegrationTest.LibraryResolutionRegressionTest`。

---

### 3.4 列出 CQL 函式庫

**GET** `/api/cql/libraries`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| search | query | string | 否 | 搜尋關鍵字 |

**回應範例（200）：**

```json
[
  {
    "id": "Test-1.0",
    "name": "Test",
    "version": "1.0",
    "cqlContent": "library Test version '1.0'...",
    "elmJson": "{...}",
    "description": "測試函式庫",
    "status": "active",
    "dependencies": [],
    "ownerUsername": "admin",
    "sharedWith": [],
    "accessLevel": "private",
    "createdAt": "2026-01-15T10:00:00",
    "updatedAt": "2026-01-15T10:00:00"
  }
]
```

---

### 3.5 取得函式庫

**GET** `/api/cql/libraries/{id}`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| id | path | string | 是 | 函式庫 ID |

---

### 3.6 建立函式庫

**POST** `/api/cql/libraries`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| cql | body | string | 是 | CQL 原始碼 |
| description | body | string | 否 | 說明 |

**回應：** 201 Created，回傳 CqlLibrary。

---

### 3.7 更新函式庫

**PUT** `/api/cql/libraries/{id}`

需為擁有者或管理員。參數同建立。

---

### 3.8 刪除函式庫

**DELETE** `/api/cql/libraries/{id}`

需為擁有者或管理員。回傳 204 No Content。

---

### 3.9 取得最新版本

**GET** `/api/cql/libraries/latest/{name}`

依函式庫名稱取得最新版本。

---

### 3.10 列出所有版本

**GET** `/api/cql/libraries/versions/{name}`

回傳指定函式庫的所有版本（由新至舊）。

---

### 3.11 取得函式庫中繼資料

**GET** `/api/cql/libraries/metadata`

回傳所有函式庫的輕量中繼資料（不含完整 CQL / ELM 內容）。

---

### 3.12 匯出為 FHIR Library

**GET** `/api/cql/libraries/{id}/fhir`

將 CQL 函式庫匯出為 FHIR R4 Library 資源。

---

### 3.13 匯入 FHIR Library

**POST** `/api/cql/libraries/import/fhir`

匯入 FHIR R4 Library 資源（body 為 FHIR Library JSON）。

---

### 3.14 列出範本庫

**GET** `/api/cql/libraries/repository`

列出可供匯入的預建 CQL 函式庫範本。

---

### 3.15 匯入範本庫

**POST** `/api/cql/libraries/repository/{name}/import`

將預建範本匯入為使用者的函式庫。

---

### 3.16 建立新版本

**POST** `/api/cql/libraries/{name}/version`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| name | path | string | 是 | 函式庫名稱 |
| type | query | string | 否 | 版本類型：`major`、`minor`（預設）、`patch` |

---

### 3.17 版本歷史

**GET** `/api/cql/libraries/{name}/history`

回傳函式庫的所有版本歷史紀錄。

---

### 3.18 比較版本

**GET** `/api/cql/libraries/compare`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| oldId | query | string | 是 | 舊版本 ID |
| newId | query | string | 是 | 新版本 ID |

**回應範例（200）：**

```json
{
  "oldCql": "library Test version '1.0'...",
  "newCql": "library Test version '1.1'..."
}
```

---

### 3.19 分享函式庫

**POST** `/api/cql/libraries/{id}/share`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| targetUsername | body | string | 是 | 分享對象使用者名稱 |

---

### 3.20 取消分享

**POST** `/api/cql/libraries/{id}/unshare`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| targetUsername | body | string | 是 | 取消分享的使用者名稱 |

---

### 3.21 轉移擁有權

**POST** `/api/cql/libraries/{id}/transfer`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| newOwner | body | string | 是 | 新擁有者使用者名稱 |

---

### 3.22 設定存取層級

**PUT** `/api/cql/libraries/{id}/access`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| accessLevel | body | string | 是 | `private`、`shared`、`public` |

---

### 3.23 依擁有者列出

**GET** `/api/cql/libraries/owner/{username}`

回傳指定使用者擁有的所有函式庫。

---

### 3.24 列出分享函式庫

**GET** `/api/cql/libraries/shared/{username}`

回傳分享給指定使用者或公開的函式庫。

---

### 3.25 取得相依性

**GET** `/api/cql/libraries/{id}/dependencies`

回傳函式庫的完整相依性樹。

---

### 3.26 取得被依賴者

**GET** `/api/cql/libraries/dependents/{name}`

回傳所有相依於指定函式庫的函式庫。

---

### 3.27 相依性分析

**GET** `/api/cql/libraries/{id}/dependency-analysis`

分析函式庫的相依性衝突與版本不一致問題。

---

## 4. 品質指標 (Measures)

基礎路徑：`/api/measures`

### 4.1 指標定義 CRUD

#### 列出所有指標

**GET** `/api/measures`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| search | query | string | 否 | 搜尋關鍵字 |
| department | query | string | 否 | 部門代碼篩選 |

#### 取得指標

**GET** `/api/measures/{id}`

#### 建立指標

**POST** `/api/measures`

**請求範例：**

```json
{
  "name": "DiabetesHbA1c",
  "version": "1.0.0",
  "title": "糖尿病 HbA1c 控制率",
  "description": "衡量糖尿病患者 HbA1c < 7% 的比例",
  "status": "draft",
  "scoringType": "proportion",
  "department": "INTERNAL"
}
```

**回應：** 201 Created，回傳 MeasureDefinition。

#### 更新指標

**PUT** `/api/measures/{id}`

需為擁有者或管理員。Body 同建立。

#### 刪除指標

**DELETE** `/api/measures/{id}`

需為擁有者或管理員。回傳 204。

---

### 4.2 FHIR 匯入 / 匯出

| 端點 | 說明 |
|------|------|
| `POST /api/measures/import/fhir` | 匯入 FHIR Measure 資源 |
| `GET /api/measures/{id}/fhir` | 匯出為 FHIR Measure 資源 |
| `GET /api/measures/{id}/export/bundle?format=json\|xml` | 匯出完整 FHIR Bundle（Measure + Library + ValueSet） |
| `GET /api/measures/{id}/export/cql` | 匯出 CQL 原始碼檔案 |
| `GET /api/measures/{id}/export/elm` | 匯出 ELM JSON |
| `POST /api/measures/import/bundle` | 匯入 FHIR Bundle |
| `GET /api/measures/{id}/export/hqmf` | 匯出 HQMF R2.1 XML（CMS 申報） |
| `GET /api/measures/{id}/export/human-readable` | 匯出人類可讀 HTML 敘述文件 |

---

### 4.3 CQL 表達式 / 資料需求

| 端點 | 說明 |
|------|------|
| `GET /api/measures/{id}/cql-expressions` | 解析 CQL 並回傳可用表達式名稱（用於母群體對應） |
| `GET /api/measures/{id}/data-requirements` | 從 CQL/ELM 擷取 FHIR DataRequirement 資源 |

---

### 4.4 指標評估

#### 評估已儲存指標

**POST** `/api/measures/{measureId}/$evaluate-measure`

| 名稱 | 位置 | 類型 | 必填 | 說明 |
|------|------|------|------|------|
| measureId | path | string | 是 | 指標 ID |
| subject | query | string | 否 | 病患 ID |
| periodStart | query | date | 否 | 評估期間起始 |
| periodEnd | query | date | 否 | 評估期間結束 |
| reportType | query | string | 否 | `individual`（預設）、`subject-list`、`summary`、`data-collection` |

Body 可選提供 MeasureEvaluationRequest 覆蓋查詢參數。

**回應範例（200）：**

```json
{
  "measureId": "1",
  "measureName": "DiabetesHbA1c",
  "status": "complete",
  "periodStart": "2025-01-01",
  "periodEnd": "2025-12-31",
  "reportType": "summary",
  "groups": [
    {
      "groupId": "group-1",
      "populations": [
        { "populationType": "initial-population", "count": 100 },
        { "populationType": "denominator", "count": 95 },
        { "populationType": "numerator", "count": 72 }
      ],
      "measureScore": 0.7579,
      "totalPatients": 100
    }
  ]
}
```

#### 評估自訂 CQL

**POST** `/api/measures/evaluate`

Body: MeasureEvaluationRequest（含 measureCql）。

---

### 4.5 報告

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/reports` | GET | 列出近期報告 |
| `/api/measures/{measureId}/reports` | GET | 列出指標報告 |
| `/api/measures/reports/{reportId}` | GET | 取得報告 |
| `/api/measures/reports/{reportId}` | DELETE | 刪除報告 |
| `/api/measures/reports/{reportId}/export?format=fhir\|csv\|excel` | GET | 匯出報告 |

---

### 4.6 排程

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/{measureId}/schedules` | GET | 列出排程 |
| `/api/measures/{measureId}/schedules` | POST | 建立排程 |
| `/api/measures/schedules/{scheduleId}` | PUT | 更新排程 |
| `/api/measures/schedules/{scheduleId}` | DELETE | 刪除排程 |
| `/api/measures/schedules/{scheduleId}/trigger` | POST | 手動觸發排程 |

---

### 4.7 期間比較與趨勢

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/compare?measureName=&p1Start=&p1End=&p2Start=&p2End=` | GET | 兩期間比較 |
| `/api/measures/trend?measureName=&periods=4` | GET | 趨勢分析 |

---

### 4.8 測試案例

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/{measureId}/test-cases` | GET | 列出測試案例 |
| `/api/measures/{measureId}/test-cases/{testCaseId}` | GET | 取得測試案例 |
| `/api/measures/{measureId}/test-cases` | POST | 建立測試案例 |
| `/api/measures/{measureId}/test-cases/{testCaseId}` | PUT | 更新測試案例 |
| `/api/measures/{measureId}/test-cases/{testCaseId}` | DELETE | 刪除測試案例 |
| `/api/measures/{measureId}/test-cases/batch-import` | POST | 批次匯入測試案例 |
| `/api/measures/{measureId}/test-cases/{testCaseId}/run` | POST | 執行單一測試案例 |
| `/api/measures/{measureId}/test-cases/run` | POST | 執行所有測試案例 |
| `/api/measures/{measureId}/test-cases/{testCaseId}/run-with-coverage` | POST | 執行含覆蓋率測試 |

**TestCase 範例：**

```json
{
  "title": "糖尿病患者-符合",
  "description": "HbA1c < 7% 的測試病患",
  "patientBundleJson": "{\"resourceType\":\"Bundle\",...}",
  "expectedPopulations": {
    "initial-population": true,
    "denominator": true,
    "numerator": true
  },
  "series": "基本測試",
  "sortOrder": 1
}
```

---

### 4.9 版本管理

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/{id}/version?type=minor` | POST | 建立新版本 |
| `/api/measures/{id}/history` | GET | 版本歷史 |
| `/api/measures/version-compare?oldId=&newId=` | GET | 版本比較 |

---

### 4.10 分享與權限

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/{id}/share` | POST | 分享指標（body: `{targetUsername}`） |
| `/api/measures/{id}/unshare` | POST | 取消分享 |
| `/api/measures/{id}/transfer` | POST | 轉移擁有權（body: `{newOwner}`） |
| `/api/measures/{id}/access` | PUT | 設定存取層級（body: `{accessLevel}`） |
| `/api/measures/owner/{username}` | GET | 依擁有者列出 |
| `/api/measures/shared/{username}` | GET | 列出分享指標 |

---

### 4.11 工作流程

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/{id}/submit-for-review` | POST | 提交審查 |
| `/api/measures/{id}/approve` | POST | 核准指標 |
| `/api/measures/{id}/reject` | POST | 退回指標（body: `{reason}`） |
| `/api/measures/{id}/retire` | POST | 退役指標 |
| `/api/measures/{id}/lock` | POST | 鎖定編輯 |
| `/api/measures/{id}/unlock` | POST | 解鎖編輯 |

工作流程動作的 body 為 WorkflowActionRequest：`{ "reason": "..." }`

---

### 4.12 驗證

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/{id}/validate` | POST | 完整驗證（CQL、母群體、中繼資料、測試案例、QI-Core） |
| `/api/measures/{id}/validate/quick` | POST | 快速驗證（僅 CQL + 母群體） |

---

### 4.13 儀表板

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/dashboard` | GET | 基本儀表板摘要 |
| `/api/measures/dashboard/enhanced?department=` | GET | 增強儀表板（含趨勢、警示、部門分數） |
| `/api/measures/dashboard/trends?measureId=&periodType=&count=` | GET | 分數趨勢 |
| `/api/measures/dashboard/department/{code}` | GET | 部門深入分析 |
| `/api/measures/dashboard/alerts?department=` | GET | 閾值警示 |
| `/api/measures/dashboard/report?type=&department=` | GET | 品質報告 |

---

### 4.14 閾值管理

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/measures/{id}/thresholds` | POST | 設定閾值（201） |
| `/api/measures/{id}/thresholds` | GET | 取得閾值 |

---

### 4.15 批次評估

**POST** `/api/measures/batch-evaluate`

同時評估多個指標，回傳各指標結果。

---

### 4.16 稽核紀錄

**GET** `/api/measures/{id}/audit`

回傳指標的操作稽核軌跡。

---

## 5. FHIR 資源

基礎路徑：`/api/fhir`

### 5.1 Implementation Guide 瀏覽

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/fhir/ig/packages` | GET | 列出已載入的 IG 套件 |
| `/api/fhir/ig/profiles?resourceType=&search=` | GET | 瀏覽 StructureDefinition |
| `/api/fhir/ig/profiles/{url}` | GET | 取得指定 Profile |
| `/api/fhir/ig/valuesets?search=` | GET | 瀏覽 ValueSet |
| `/api/fhir/ig/valuesets/{url}` | GET | 取得指定 ValueSet |
| `/api/fhir/ig/codesystems?search=` | GET | 瀏覽 CodeSystem |
| `/api/fhir/ig/codesystems/{url}` | GET | 取得指定 CodeSystem |

---

### 5.2 結構定義

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/fhir/structure-definitions/resource-types` | GET | 列出支援的資源類型 |
| `/api/fhir/structure-definitions/{resourceType}` | GET | 取得資源元素中繼資料 |

---

### 5.3 FHIR 資源 CRUD

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/fhir/{resourceType}?fhirServer=&params=` | GET | 搜尋 FHIR 資源 |
| `/api/fhir/{resourceType}/{id}?fhirServer=` | GET | 讀取資源 |
| `/api/fhir/{resourceType}?fhirServer=` | POST | 建立資源 |
| `/api/fhir/{resourceType}/{id}?fhirServer=` | PUT | 更新資源 |
| `/api/fhir/{resourceType}/{id}?fhirServer=` | DELETE | 刪除資源 |

所有 CRUD 操作需提供 `fhirServer` 查詢參數指定目標 FHIR 伺服器。

---

### 5.4 術語服務

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/fhir/ValueSet?title=` | GET | 搜尋 ValueSet |
| `/api/fhir/ValueSet/$expand?url=&filter=` | GET | 展開 ValueSet |
| `/api/fhir/CodeSystem/$validate-code?system=&code=&valueSet=` | GET | 驗證代碼 |
| `/api/fhir/CodeSystem/$lookup?system=&code=` | GET | 查詢代碼資訊 |
| `/api/fhir/CodeSystem/$search-codes?system=&text=&maxResults=20` | GET | 搜尋代碼 |

---

### 5.5 VSAC 整合

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/fhir/vsac/ValueSet?title=` | GET | 搜尋 VSAC ValueSet |
| `/api/fhir/vsac/ValueSet/{oid}` | GET | 取得 VSAC ValueSet |
| `/api/fhir/vsac/ValueSet/{oid}/$expand` | GET | 展開 VSAC ValueSet |

---

### 5.6 批次匯出

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/fhir/$export?fhirServer=&exportType=&_outputFormat=&_since=&_type=` | POST | 啟動批次匯出（回傳 202） |
| `/api/fhir/$export-status?statusUrl=` | GET | 輪詢匯出狀態 |

---

### 5.7 其他

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/fhir/Bundle/$transaction?fhirServer=` | POST | 執行 FHIR Transaction Bundle |
| `/api/fhir/$validate?profile=` | POST | 驗證 FHIR 資源 |
| `/api/fhir/Patient/$search-by-demographics?family=&given=&birthdate=&identifier=&fhirServer=` | GET | 依人口統計資料搜尋病患 |
| `/api/fhir/cache/stats` | GET | 快取統計 |
| `/api/fhir/cache/{cacheName}` | DELETE | 清除快取（僅管理員） |

---

## 6. CDS 決策支援

### 6.1 CDS 服務管理

基礎路徑：`/api/cds/services`

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/cds/services` | GET | 列出 CDS 服務（使用者自有 + 共享） |
| `/api/cds/services/{id}` | GET | 取得服務 |
| `/api/cds/services` | POST | 建立服務 |
| `/api/cds/services/{id}` | PUT | 更新服務 |
| `/api/cds/services/{id}` | DELETE | 刪除服務 |
| `/api/cds/services/{id}/enable` | PATCH | 啟用服務 |
| `/api/cds/services/{id}/disable` | PATCH | 停用服務 |
| `/api/cds/services/{id}/share?shared=true` | PATCH | 切換共享（僅管理員） |
| `/api/cds/services/{serviceName}/versions` | GET | 列出服務版本 |
| `/api/cds/services/{serviceName}/rollback/{version}` | POST | 回滾版本 |
| `/api/cds/services/analytics` | GET | 所有服務分析 |
| `/api/cds/services/{id}/analytics` | GET | 服務分析 |
| `/api/cds/services/{id}/feedback` | GET | 服務回饋 |

**CdsServiceConfigRequest 範例：**

```json
{
  "id": "diabetes-alert",
  "hook": "patient-view",
  "title": "糖尿病警示",
  "description": "當病患 HbA1c > 9% 時發出警示",
  "cqlContent": "library DiabetesAlert version '1.0'...",
  "defaultIndicator": "warning",
  "enabled": true,
  "prefetch": {
    "patient": "Patient/{{context.patientId}}"
  }
}
```

---

### 6.2 CDS Hooks 執行

基礎路徑：`/cds-services`

| 端點 | 方法 | 說明 |
|------|------|------|
| `/cds-services` | GET | 服務發現（回傳共享服務） |
| `/cds-services/{serviceId}` | POST | 呼叫 CDS 服務 |
| `/cds-services/{serviceId}/feedback` | POST | 提交回饋 |
| `/cds-services/{serviceId}/sandbox` | POST | 沙箱模式測試 |
| `/cds-services/u/{username}` | GET | 使用者專屬服務發現（API Key 認證） |
| `/cds-services/u/{username}/{serviceId}` | POST | 使用者專屬服務呼叫 |

**CDS 請求範例：**

```json
{
  "hook": "patient-view",
  "hookInstance": "d1577c69-dfbe-44ad-ba6d-3e05e953b2ea",
  "fhirServer": "http://hapi-fhir:8080/fhir",
  "context": {
    "userId": "Practitioner/123",
    "patientId": "Patient/456"
  }
}
```

**CDS 回應範例：**

```json
{
  "cards": [
    {
      "uuid": "card-1",
      "summary": "HbA1c 過高警示",
      "detail": "病患最近一次 HbA1c 為 10.2%，建議加強血糖控制",
      "indicator": "warning",
      "source": { "label": "CQL Platform" }
    }
  ]
}
```

---

## 7. CDS 撰寫工具

基礎路徑：`/api/authoring`

### 7.1 工件管理

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/authoring/artifacts` | GET | 列出工件 |
| `/api/authoring/artifacts/{id}` | GET | 取得工件 |
| `/api/authoring/artifacts` | POST | 建立工件 |
| `/api/authoring/artifacts/{id}` | PUT | 更新工件 |
| `/api/authoring/artifacts/{id}` | DELETE | 刪除工件 |
| `/api/authoring/artifacts/{id}/duplicate` | POST | 複製工件 |
| `/api/authoring/artifacts/{id}/summary` | GET | 取得工件摘要 |

**ArtifactRequest 範例：**

```json
{
  "name": "糖尿病照護建議",
  "version": "1.0",
  "description": "針對糖尿病患者的照護建議",
  "status": "draft",
  "fhirVersion": "4.0.1",
  "expTreeInclude": { "id": "root", "type": "And", "children": [] },
  "expTreeExclude": { "id": "root", "type": "And", "children": [] },
  "recommendations": [],
  "subpopulations": [],
  "parameters": []
}
```

---

### 7.2 範本與修飾器

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/authoring/templates` | GET | 取得元素範本分類 |
| `/api/authoring/modifiers?inputType=` | GET | 取得修飾器定義 |

---

### 7.3 CQL 生成

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/authoring/artifacts/{id}/cql?fhirVersion=` | POST | 從表達式樹生成 CQL |
| `/api/authoring/artifacts/{id}/elm` | POST | 生成 CQL 並翻譯為 ELM |
| `/api/authoring/artifacts/{id}/validate` | POST | 驗證工件 CQL |

---

### 7.4 外部 CQL 函式庫

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/authoring/artifacts/{id}/external-cql` | GET | 列出外部 CQL |
| `/api/authoring/artifacts/{artifactId}/external-cql/{libId}` | GET | 取得外部 CQL |
| `/api/authoring/artifacts/{id}/external-cql/upload` | POST | 上傳 CQL 檔案（multipart） |
| `/api/authoring/artifacts/{id}/external-cql/content` | POST | 上傳 CQL 內容（body: `{cqlContent}`） |
| `/api/authoring/artifacts/{artifactId}/external-cql/{libId}` | DELETE | 刪除外部 CQL |

---

### 7.5 測試與部署

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/authoring/artifacts/{id}/test` | POST | 測試工件（body: `{patientIds[], fhirServerUrl}`） |
| `/api/authoring/artifacts/{id}/deploy-cds` | POST | 部署為 CDS 服務（body: `{serviceId?, hook?}`） |
| `/api/authoring/artifacts/{id}/save-library` | POST | 儲存為 CQL 函式庫 |

---

### 7.6 CQL 匯入與查詢建構器

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/authoring/import-cql` | POST | 解析 CQL 為工件結構（body: `{cql}`） |
| `/api/authoring/query-builder/resources` | GET | 取得 FHIR R4 資源屬性 |
| `/api/authoring/query-builder/operators?type=` | GET | 取得查詢運算子 |

---

### 7.7 TWCORE 目錄

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/authoring/twcore-catalog?resourceType=` | GET | 取得 TWCORE 值集與代碼 |
| `/api/authoring/twcore-catalog/code-systems` | GET | 取得 TWCORE 代碼系統 |

---

## 8. EHR 整合

基礎路徑：`/api/ehr`

### 8.1 連線管理

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/ehr/connections?department=` | GET | 列出 EHR 連線 |
| `/api/ehr/connections/{id}` | GET | 取得連線 |
| `/api/ehr/connections` | POST | 建立連線（201） |
| `/api/ehr/connections/{id}` | PUT | 更新連線 |
| `/api/ehr/connections/{id}` | DELETE | 刪除連線（軟刪除） |
| `/api/ehr/connections/{id}/test` | POST | 測試連線 |

---

### 8.2 病患搜尋與匯入

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/ehr/connections/{id}/patients?nationalId=&mrn=&family=&given=` | GET | 搜尋病患 |
| `/api/ehr/connections/{id}/patients/{patientId}/preview` | GET | 匯入預覽 |
| `/api/ehr/connections/{id}/patients/{patientId}/import?measureId=` | POST | 匯入病患資料為測試案例（201） |
| `/api/ehr/imports?importedBy=` | GET | 匯入歷史 |

---

## 9. 管理功能

### 9.1 使用者管理

基礎路徑：`/api/admin`（需 ADMIN 角色）

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/admin/users` | GET | 列出所有使用者 |
| `/api/admin/users` | POST | 建立使用者 |
| `/api/admin/users/{userId}/role` | PUT | 更新角色（body: `{role}`） |
| `/api/admin/users/{userId}/enabled` | PUT | 啟用/停用帳號（body: `{enabled}`） |
| `/api/admin/users/{userId}/reset-password` | POST | 重設密碼（回傳臨時密碼） |

**AdminCreateUserRequest 範例：**

```json
{
  "username": "doctor01",
  "password": "tempPass123",
  "role": "USER",
  "email": "doctor@hospital.com"
}
```

**AdminResetPasswordResponse 範例：**

```json
{
  "temporaryPassword": "Tmp_a8f3k2",
  "username": "doctor01",
  "message": "Temporary password generated. User will be required to change it on next login."
}
```

---

### 9.2 稽核日誌

基礎路徑：`/api/admin/audit`（需 ADMIN 角色）

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/admin/audit/logs?username=&action=&resourceType=&startDate=&endDate=&statusCode=&page=0&size=20` | GET | 搜尋稽核日誌 |
| `/api/admin/audit/logs/export?...` | GET | 匯出 CSV |
| `/api/admin/audit/stats` | GET | 稽核統計 |
| `/api/admin/audit/phi-access?page=0&size=20&startDate=` | GET | PHI 存取紀錄 |
| `/api/admin/audit/login-activity?page=0&size=20&startDate=` | GET | 登入活動 |
| `/api/admin/audit/security-events?page=0&size=20&startDate=` | GET | 安全事件 |

---

### 9.3 部門管理

基礎路徑：`/api/departments`

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/departments` | GET | 列出所有部門 |
| `/api/departments/{code}` | GET | 取得部門 |
| `/api/departments/{code}/children` | GET | 取得子部門 |
| `/api/departments` | POST | 建立部門（201） |
| `/api/departments/{code}` | PUT | 更新部門 |

---

## 10. 指標目錄

基礎路徑：`/api/indicators`

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/indicators?source=&category=&search=` | GET | 搜尋指標代碼 |
| `/api/indicators/{code}?source=MOH` | GET | 取得指標 |
| `/api/indicators` | POST | 建立指標（201） |
| `/api/indicators/{code}?source=MOH` | PUT | 更新指標 |
| `/api/indicators/import` | POST | 批次匯入 |

**批次匯入回應範例：**

```json
{
  "imported": 15,
  "skipped": 2,
  "errors": ["第 3 筆缺少必要欄位 code"]
}
```

---

## 11. 通知

基礎路徑：`/api/notifications`

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/notifications` | GET | 取得最近 50 則通知 |
| `/api/notifications/unread-count` | GET | 未讀數量 |
| `/api/notifications/{id}/read` | POST | 標記已讀 |
| `/api/notifications/read-all` | POST | 全部標記已讀 |
| `/api/notifications/{id}` | DELETE | 刪除通知 |
| `/api/notifications/subscribe` | GET | SSE 訂閱（`text/event-stream`） |

**SSE 訂閱：** 使用 Server-Sent Events 即時推送通知。連線後伺服器會推送 `notification` 事件。

---

## 12. 使用者設定

### 12.1 VSAC 設定

基礎路徑：`/api/settings`

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/settings/vsac-status` | GET | 查詢 VSAC 設定狀態 |
| `/api/settings/vsac-api-key` | PUT | 更新 VSAC API 金鑰（body: `{apiKey}`） |

---

### 12.2 API 金鑰

基礎路徑：`/api/user/api-keys`

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/user/api-keys` | GET | 列出 API 金鑰（遮罩顯示） |
| `/api/user/api-keys` | POST | 產生新金鑰（body: `{name?}`，201，僅建立時回傳完整金鑰） |
| `/api/user/api-keys/{id}` | DELETE | 撤銷金鑰 |

---

### 12.3 使用者偏好

基礎路徑：`/api/cql/user-prefs`

| 端點 | 方法 | 說明 |
|------|------|------|
| `/api/cql/user-prefs/favorites` | GET | 列出我的最愛 |
| `/api/cql/user-prefs/favorites/{libraryId}` | POST | 加入最愛（201） |
| `/api/cql/user-prefs/favorites/{libraryId}` | DELETE | 移除最愛 |
| `/api/cql/user-prefs/recent` | GET | 列出最近存取 |
| `/api/cql/user-prefs/recent/{libraryId}` | POST | 記錄最近存取（201） |
| `/api/cql/user-prefs/recent` | DELETE | 清除最近存取 |

---

### 12.4 SMART on FHIR 設定

**GET** `/.well-known/smart-configuration`

回傳 SMART App Launch Framework 設定。無需認證。

**回應範例：**

```json
{
  "issuer": "https://cql-platform.example.com",
  "authorization_endpoint": "https://auth.example.com/authorize",
  "token_endpoint": "https://auth.example.com/token",
  "capabilities": ["launch-ehr", "launch-standalone", "client-public", "sso-openid-connect"],
  "grant_types_supported": ["authorization_code", "client_credentials"],
  "code_challenge_methods_supported": ["S256"],
  "scopes_supported": ["openid", "fhirUser", "launch", "patient/*.read"],
  "response_types_supported": ["code"],
  "token_endpoint_auth_methods_supported": ["client_secret_basic", "private_key_jwt"]
}
```

---

## 13. 附錄

### 13.1 HTTP 狀態碼

| 狀態碼 | 說明 |
|--------|------|
| 200 | 成功 |
| 201 | 資源已建立 |
| 202 | 已接受（非同步操作，如批次匯出） |
| 204 | 成功（無內容，如 DELETE） |
| 400 | 請求格式錯誤或參數驗證失敗 |
| 401 | 未認證（JWT 缺失或過期） |
| 403 | 權限不足 |
| 404 | 資源不存在 |
| 409 | 衝突（如使用者名稱已存在） |
| 500 | 伺服器內部錯誤 |
| 502 | 上游服務錯誤（如 FHIR 伺服器無回應） |

### 13.2 錯誤碼對照表

| 錯誤訊息 | 說明 |
|----------|------|
| `Invalid username or password` | 帳號或密碼錯誤 |
| `Username already exists` | 使用者名稱已被使用 |
| `User not found` | 找不到使用者 |
| `User account is disabled` | 帳號已停用 |
| `SSO authentication failed` | Okta SSO 認證失敗 |
| `Okta SSO is not enabled` | Okta SSO 未啟用 |
| `Invalid or expired reset token` | 密碼重設令牌無效或過期 |
| `Current password is incorrect` | 目前密碼不正確 |
| `CQL code is required` | CQL 程式碼為必填 |
| `Measure not found` | 找不到品質指標 |
| `Service ID is required` | 服務 ID 為必填 |
| `Hook type is required` | Hook 類型為必填 |
| `Only .cql files are accepted` | 僅接受 .cql 檔案 |
| `File size exceeds 1MB limit` | 檔案大小超過 1MB 限制 |
| `Cannot reset password for SSO users` | 無法為 SSO 使用者重設密碼 |
| `You can only modify your own services` | 只能修改自己的服務 |
| `Only admins can share/unshare services` | 僅管理員可分享/取消分享服務 |
| `Invalid FHIR server URL` | 無效的 FHIR 伺服器 URL |
| `Invalid FHIR resource type` | 無效的 FHIR 資源類型 |

### 13.3 資料模型參考

#### AuthResponse

```json
{
  "token": "string (JWT)",
  "username": "string",
  "role": "USER | DEPARTMENT_ADMIN | ADMIN",
  "expiresIn": "number (毫秒)",
  "forcePasswordChange": "boolean"
}
```

#### CqlLibrary

```json
{
  "id": "string (Name-Version)",
  "name": "string",
  "version": "string (semver)",
  "cqlContent": "string",
  "elmJson": "string",
  "description": "string",
  "status": "string",
  "dependencies": ["string"],
  "ownerUsername": "string",
  "sharedWith": ["string"],
  "accessLevel": "private | shared | public",
  "createdAt": "ISO 8601",
  "updatedAt": "ISO 8601"
}
```

#### MeasureDefinition

```json
{
  "id": "number",
  "name": "string",
  "version": "string",
  "title": "string",
  "description": "string",
  "status": "draft | active | retired | in-review",
  "scoringType": "proportion | ratio | continuous-variable | cohort | composite",
  "cqlContent": "string",
  "groupDefinitions": [
    {
      "populations": [
        { "type": "initial-population", "expressionName": "string" }
      ],
      "stratifiers": [
        { "expressionName": "string", "description": "string" }
      ]
    }
  ],
  "ownerUsername": "string",
  "department": "string",
  "mohIndicatorCode": "string",
  "createdAt": "ISO 8601",
  "updatedAt": "ISO 8601"
}
```

#### MeasureEvaluationResult

```json
{
  "measureId": "string",
  "measureName": "string",
  "status": "complete | error",
  "periodStart": "date",
  "periodEnd": "date",
  "reportType": "string",
  "groups": [
    {
      "groupId": "string",
      "populations": [
        { "populationType": "string", "count": "number" }
      ],
      "measureScore": "number",
      "totalPatients": "number"
    }
  ],
  "errorMessage": "string (null if success)"
}
```

#### TestCase

```json
{
  "id": "number",
  "measureDefinitionId": "number",
  "title": "string",
  "description": "string",
  "patientBundleJson": "string (FHIR Bundle JSON)",
  "expectedPopulations": { "population-type": "boolean" },
  "status": "pass | fail | error | pending",
  "series": "string",
  "sortOrder": "number"
}
```

#### CdsResponse

```json
{
  "cards": [
    {
      "uuid": "string",
      "summary": "string",
      "detail": "string",
      "indicator": "info | warning | critical",
      "source": { "label": "string", "url": "string" },
      "suggestions": [
        {
          "label": "string",
          "actions": [
            { "type": "create | update | delete", "description": "string", "resource": {} }
          ]
        }
      ],
      "links": [{ "label": "string", "url": "string", "type": "absolute | smart" }]
    }
  ]
}
```

---

> **端點總計：222 個** ｜ 涵蓋 17 個控制器 ｜ CQL 平台 v1.0
