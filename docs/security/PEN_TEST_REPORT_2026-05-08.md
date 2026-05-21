# 滲透測試報告 — CQL Platform

| 項目 | 內容 |
|------|------|
| 測試日期 | 2026-05-08 |
| 測試版本 | branch `fix/stratifier-per-group` (HEAD = 17c1df6) |
| 測試人員 | 自動化滲透測試 (內部審計) |
| 測試範圍 | (1) 靜態代碼安全審計 (2) 依賴漏洞掃描 (3) 本地動態測試 (docker compose 跑著的 dev stack) |
| 測試方法 | OWASP Top 10 (2021) + ASVS L2 + IEC 62304 安全考量 |
| 排除範圍 | 生產 VM (187.77.155.248) 未做動態測試 |

> **修補狀態 (2026-05-08 / PAT-157)**: 4 Critical 中 3 已修 (C2/C3/C4) + 1 暫保留 (C1 admin/admin per user request)；1 High 已修 (H2)、1 排到獨立 PR (H1)；6 Medium 中 3 已修 (M3/M4/M5)、3 排到獨立 PR (M1/M2/M6)；Low 1 已修 (L1/L2)、3 仍開 (L3/L4 + INFO)。詳見每個發現的「修補狀態」標記。

> **TFDA / IEC 62304 提醒**: 本報告中標 Critical / High 的項目應對應到 ISO 14971 的危害分析，建立風險控制紀錄與驗證 Issue。

---

## 摘要 (Executive Summary)

| 嚴重度 | 數量 | 重點議題 |
|--------|------|----------|
| **Critical** | 4 | 預設管理員憑證、JWT 密鑰可預測、PHI 加密金鑰可預測、SSRF 在 docker profile 下放行內網 |
| **High** | 2 | DNS rebinding TOCTOU、DB 密碼弱 |
| **Medium** | 6 | DEPT_ADMIN 角色無細部權限、JWT 存 localStorage、自助註冊政策、使用者枚舉、H2 driver 在 runtime scope、subscription callback 無來源驗證 |
| **Low / Info** | 5 | nginx CSP 比後端寬鬆、密碼複雜度只查長度、`/api/version` 行為非預期、依賴掃描未完整、其他防禦良好 |

**最關鍵的事實**：本機 docker stack 的 `admin / admin` 預設帳號生效；`docker/.env` 內的 `JWT_SECRET` 是公開字串 (`super_secret_jwt_key_must_be_at_least_256_bits_long_for_HS256_algorithm`)。**若 production VM 187.77.155.248 也帶這個 .env 部署**，任何能 reach API 的攻擊者都能以 ADMIN 身份完整接管平台 — 包含 PHI 解密 (PHI 用同一個 `.env` 的 `ENCRYPTION_KEY` 加密)。

---

## 良好實踐 (本次審計肯定的部分)

審計過程中發現多項已落實的安全措施，列於此處作為 baseline：

1. **JWT 密鑰強度檢查**: `JwtTokenProvider` ctor 強制 `JWT_SECRET ≥ 32 chars` 否則 fail-fast (`backend/src/main/java/com/cqlplatform/security/JwtTokenProvider.java:28`)
2. **Token version 即時撤銷**: `TokenVersionService.bumpVersion()` 配 30s Caffeine cache; 登出 / 改密碼 / 改角色 / 停用帳號都會 bump
3. **Refresh token 旋轉 + 重用偵測**: 重用偵測會撤銷整個 token family (`RefreshTokenService.refreshTokens` line 86-91)
4. **Refresh token cookie 屬性**: `HttpOnly` + `SameSite=Strict` + `Secure` (production) + `Path=/api/auth` (`RefreshTokenCookieUtil`)
5. **CSRF 設計合理**: stateless JWT API + 受保護 refresh cookie，文件已說明 (`SecurityConfig:140-150`)
6. **API Key SHA-256 hashed at rest**: `UserApiKeyService.hashApiKey` + 一次性回傳 plaintext
7. **密碼 BCrypt**: `SecurityConfig.passwordEncoder()`
8. **帳號鎖定**: `LoginAttemptListener` + 5 次失敗鎖 30 分鐘 (PAT-094)
9. **Email 枚舉防護**: `forgot-password` 一律回 success；password reset token SHA-256 hashed + 30 分鐘過期 + used flag
10. **Rate limiting**: 全局 60 rpm + 翻譯 / 執行 / 修補建議分層；per-IP + per-user; payload 加權
11. **Admin reset password 不回傳明文**: `adminResetPassword` 透過 email 寄送臨時密碼 (`PasswordResetService:140-177`)
12. **PAT-145 base-url fallback fail-loud**: production 拒絕從 `X-Forwarded-Host` 推導 base URL，避免密碼重設 email 被注入攻擊者網域 (`AuthController.getBaseUrl`)
13. **PAT-150 Prometheus auth fail-closed**: scrape credentials 沒設就拒絕所有請求
14. **InputValidator 多層**: URL / OID / FHIR resource type / cache name / search params / identifier 都有 regex / allow-list
15. **DOMPurify 包 `dangerouslySetInnerHTML`**: `CqlPreviewBox.tsx:63` + `CqlPreviewPanel.tsx:246`
16. **CqlArtifactBuilder identifier 過濾**: `name.replaceAll("[^a-zA-Z0-9_]", "_")` 杜絕 CQL identifier 注入 (`CqlArtifactBuilder.java:75`)
17. **CORS allow-list**: 拒絕通配符 origin，profile-aware (`WebConfig.getAllAllowedOrigins`)
18. **Multipart limit**: 1MB / 2MB total — 防大檔案 DoS

---

## 漏洞清單

> 每項註記 OWASP / CWE / 估算 CVSS v3.1 (我自評，僅供決策參考；非正式評分)。

---

### **CRITICAL-1**: 預設管理員憑證 `admin / admin` 在 docker / dev profile 仍生效

> **修補狀態 (PAT-157)**: ⏸ 暫保留 — 使用者請求本批次先不動。`DataInitializer` 仍對 `{"dev","docker"}` profile 在空 DB 啟動時建 admin/admin。Production 風險：若 VM 用 docker profile 且 DB 首次啟動 → admin/admin 生效。後續 PR 規劃：`@Profile("dev")` only / 改用 `INITIAL_ADMIN_PASSWORD` env var / 加 forcePasswordChange flag 三選一。

| 屬性 | 值 |
|------|-----|
| OWASP | A07:2021 — Identification and Authentication Failures |
| CWE | [CWE-798](https://cwe.mitre.org/data/definitions/798.html) Use of Hard-coded Credentials |
| CVSS (我估) | 9.8 (Critical) — AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H |
| 檔案 | `backend/src/main/java/com/cqlplatform/config/DataInitializer.java:30-40` |

**描述**
`DataInitializer` 在 `@Profile({"dev", "docker"})` 下若 `userRepository.count() == 0` 自動建立 `admin / admin` (ADMIN 角色) 與 `demo / password`。Production 部署常用 `docker` profile，當 DB 是首次啟動 / 災後復原時會落入這個分支。

**PoC (在本機 docker stack)**
```bash
$ curl -s -X POST http://localhost:8888/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin"}'
# {"token":"eyJhbGciOi...", "role":"ADMIN", ...}

$ curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8888/api/admin/users
# 列出所有使用者 — 已驗證可用
```

**生產面影響**
若 187.77.155.248 部署時用了同一份 `docker` profile 且 DB 在初次啟動時走過這條 code path，admin/admin 會生效；即使後來改了密碼，code 仍會在新 DB 第一次啟動時再建一次。

**建議修補方向**
- `@Profile("dev")` only — `docker` 不包含
- Default password 改為從環境變數讀取 (e.g. `INITIAL_ADMIN_PASSWORD`)，啟動時若未設定就 fail-fast
- 建一個 TFDA `[需求]` Issue 追蹤「production 部署不允許 hardcoded credentials」(IEC 62304:5.2)

---

### **CRITICAL-2**: `docker/.env` 內 `JWT_SECRET` 為可預測字串

> **修補狀態 (PAT-157)**: ✅ 已修 — 本機 `docker/.env` 用 `secrets.token_bytes(64)` urlsafe-b64 重新產生。動態驗證：用舊 secret 偽造的 admin token 由 200 改為 401。生產 VM 須獨立輪替 + bump 全部使用者的 `token_version` 撤銷舊 token（`UPDATE app_user SET token_version = token_version + 1`）。

| 屬性 | 值 |
|------|-----|
| OWASP | A02:2021 — Cryptographic Failures |
| CWE | [CWE-321](https://cwe.mitre.org/data/definitions/321.html) Use of Hard-coded Cryptographic Key |
| CVSS (我估) | 9.8 (Critical) |
| 檔案 | `docker/.env:12` |

**描述**
`JWT_SECRET=super_secret_jwt_key_must_be_at_least_256_bits_long_for_HS256_algorithm` — 純文字、可預測、且這個字串本身就出現在 commit 的 `.env.example` 衍生模式裡 (`CHANGE_ME_must_be_at_least_256_bits_long_for_HS256`)。任何看過此 repo 的人都能猜中。

`.env` 已在 `.gitignore` 中 ✅，但本機 working tree 仍是這個值；生產 VM 若沿用同一份 `.env` 就直接漏。

**PoC (用洩漏的 secret 偽造 admin token)**
```python
import hmac, hashlib, base64, json, time
secret = "super_secret_jwt_key_must_be_at_least_256_bits_long_for_HS256_algorithm"
hdr = base64.urlsafe_b64encode(json.dumps({"alg":"HS512"},separators=(",",":")).encode()).decode().rstrip("=")
now = int(time.time())
pay = base64.urlsafe_b64encode(json.dumps({
    "iss":"cql-platform","aud":["cql-platform"],"sub":"admin",
    "role":"ADMIN","tv":0,"iat":now,"exp":now+900
},separators=(",",":")).encode()).decode().rstrip("=")
sig = base64.urlsafe_b64encode(hmac.new(secret.encode(), f"{hdr}.{pay}".encode(), hashlib.sha512).digest()).decode().rstrip("=")
print(f"{hdr}.{pay}.{sig}")
```
本機已驗證偽造的 admin JWT 能成功打 `/api/admin/users`。

**建議修補方向**
- `openssl rand -base64 64` 生成新 secret，更新 `.env`，重啟容器
- bump 所有現有使用者的 token version (DB 一次性 UPDATE) 撤銷舊 token
- 把 `.env.example` 留 placeholder (已是)，但加一段 README 警告：「不要直接 commit .env，並避免延用範例字串」
- 考慮接外部 secret manager (Vault / AWS Secrets Manager / Cloudflare Workers secrets)

---

### **CRITICAL-3**: `docker/.env` 內 `ENCRYPTION_KEY` 為可預測字串 — PHI 加密金鑰

> **修補狀態 (PAT-157)**: ✅ 已修（dev 環境）— 本機 `.env` 用 `secrets.token_bytes(32)` urlsafe-b64 重新產生 + dev DB volume 清空重建（demo 資料）。**Production 仍待處理**：rotate 需 envelope encryption migration（per-row data key + master key）以保留既存 PHI 解密能力，獨立 PR 設計。

| 屬性 | 值 |
|------|-----|
| OWASP | A02:2021 |
| CWE | [CWE-321](https://cwe.mitre.org/data/definitions/321.html) |
| CVSS (我估) | 9.1 (Critical) — AV:N/AC:L/PR:N/UI:N/C:H/I:N/A:N — 機密性影響大 |
| 檔案 | `docker/.env:13` |

**描述**
`ENCRYPTION_KEY=32byteslong_encryption_key_here!` 用於 PHI 欄位加密 (見 `EncryptionConverter.java`)。若 DB 一旦外洩 (備份遺失 / 容器漏 / 內部威脅) 而攻擊者也掌握此可預測金鑰，所有 PHI 直接解密。

**TFDA / 醫療資料保護**
這是受 TFDA 規管的醫療軟體；PHI 解密外洩屬重大資料事件，須走 ISO 14971 風險控制 + 通報。

**建議修補方向**
- 立即輪替 `ENCRYPTION_KEY` (注意：rotate 需要先用舊 key 解密重要欄位再用新 key 加密；或用 envelope encryption)
- 建立 key rotation 流程文件
- 評估改用 AES-GCM 的 key derivation (e.g. envelope: per-row key + master key in HSM)

---

### **CRITICAL-4**: SSRF 防護在 `docker` profile 下被旁路 — production 部署受影響

> **修補狀態 (PAT-157)**: ✅ 已修 — `InputValidator.computeIsLocalDevelopment()` 拿掉 `docker`，只認 `dev` / `test`。動態驗證：`?fhirServer=http://169.254.169.254/` 由 503（reachable）改為 400 `Invalid FHIR server URL`。Production VM 重啟後即生效。

| 屬性 | 值 |
|------|-----|
| OWASP | A10:2021 — Server-Side Request Forgery |
| CWE | [CWE-918](https://cwe.mitre.org/data/definitions/918.html) Server-Side Request Forgery |
| CVSS (我估) | 8.6 (High/Critical) — AV:N/AC:L/PR:L/UI:N/C:H/I:H/A:L |
| 檔案 | `backend/src/main/java/com/cqlplatform/security/InputValidator.java:64-73` |

**描述**
```java
private static boolean computeIsLocalDevelopment() {
    String profile = System.getProperty(...);
    return profile.contains("dev") || profile.contains("docker") || profile.contains("test");
}
```
`isLocalDevelopment()` 把 `docker` profile 視為「本地開發」，因此 `isValidUrl()` 對 loopback / link-local / site-local IP **放行**。但生產部署用的就是 `docker` profile (`SPRING_PROFILES_ACTIVE=docker`，見 `docker-compose.yml:42`)。

**結果**：在 production 環境下，攻擊者 (任一已認證使用者) 可透過 `?fhirServer=...` 等參數命中內網。

**PoC (本機 docker stack 已驗證)**
```bash
$ curl -s -o /dev/null -w "%{http_code}\n" \
    "http://localhost:8888/api/fhir/Patient?fhirServer=http://169.254.169.254/" \
    -H "Authorization: Bearer $TOKEN"
# 503 — 表示 backend 嘗試出站 (而非 400 「Invalid FHIR server URL」)
# 在 AWS / GCP / Azure / Cloudflare metadata endpoint 都會曝露 IAM credentials
```

**生產面影響**
- 命中雲端 metadata endpoint (169.254.169.254) → 取出 IAM role token / instance metadata
- 命中內網其他服務 (postgres:5432, hapi-fhir:8080, ollama:11434, prometheus:9090, alertmanager:9093, grafana:3000)
- 配合 AdminController 的功能可漏洞鏈上身分提升

**建議修補方向**
- `computeIsLocalDevelopment` 的判斷只認 `dev` 與 `test`；`docker` profile 改為視為 production 等級
- 或新增明確 `app.allow-private-targets` 旗標 (預設 false)，與 PAT-145 的 `app.allow-base-url-fallback` 同樣 fail-loud 模式
- 對所有 outbound URL 加防 DNS rebinding 的 hook：解析 IP 後在發送請求時鎖定 IP (見下面 HIGH-1)

---

### **HIGH-1**: DNS rebinding TOCTOU 旁路 SSRF 防護

> **修補狀態 (PAT-157)**: ⏸ 排到獨立 PR — 須改 HAPI client 出站 layer 加 IP-locking interceptor，需驗證 HAPI 8.6.6 相容性（自訂 DnsResolver / HostnameVerifier hook），風險中等不適合進綜合 security PR。獨立 follow-up 追蹤。

| 屬性 | 值 |
|------|-----|
| OWASP | A10:2021 |
| CWE | [CWE-367](https://cwe.mitre.org/data/definitions/367.html) Time-of-check Time-of-use Race Condition |
| CVSS (我估) | 7.4 (High) |
| 檔案 | `backend/src/main/java/com/cqlplatform/security/InputValidator.java:41-62` |

**描述**
`isValidUrl()` 用 `InetAddress.getByName(host)` 解析一次 DNS。後續 HAPI client 再做一次 DNS 查詢真的發送請求。攻擊者控制的 DNS server 可第一次回傳公網 IP (通過驗證)，第二次回傳 127.0.0.1 / 169.254.169.254 / 內網 IP。

**驗證難度**：要架自己的 authoritative DNS server，PoC 較複雜，但工具現成 (e.g. `singularity`, `dnschef`)。

**建議修補方向**
- 解析後鎖 IP：拿到 `addr` 後直接以 IP 發送 HTTP 請求，並設置 `Host:` header 為原 hostname (HAPI client 設定上要看支不支援)
- 或在 outbound HTTP layer 加 second check：每次 connect 前 re-verify 目標 IP 不在私有 / 元資料 IP 範圍

---

### **HIGH-2**: 弱 DB 密碼 `cqlplatform_secure_2024` 在 `docker/.env`

> **修補狀態 (PAT-157)**: ✅ 已修（dev）— 本機 `.env` 改用 `secrets.token_bytes(24)` urlsafe-b64；DB volume 清空重建。Production VM 須獨立輪替（推薦：先 `ALTER USER cqlplatform PASSWORD '...'` 再更新 `.env` 與重啟 backend）。

| 屬性 | 值 |
|------|-----|
| OWASP | A02:2021 |
| CWE | [CWE-521](https://cwe.mitre.org/data/definitions/521.html) Weak Password Requirements |
| CVSS (我估) | 7.0 (High) |
| 檔案 | `docker/.env:6, 11` |

**描述**
postgres 使用者密碼是 `cqlplatform_secure_2024` — 看似 strong 但是 dictionary + year suffix，是常見模式。組合 CRITICAL-3 (洩漏的 ENCRYPTION_KEY) 後若 postgres 暴露於外網就能 dump + decrypt PHI。

**緩解 (好的部分)**
- postgres container 沒對外曝 port (僅 internal docker network)
- 仍然要假設邊界會被穿透 (defense-in-depth)

**建議修補方向**
- 用 `openssl rand -base64 32` 生成新密碼
- 限制 pg_hba.conf 來源網段 (目前是 docker network)
- 啟用 SSL 連線 (URL 已含 `sslmode=prefer`，建議 `require`)

---

### **MEDIUM-1**: DEPARTMENT_ADMIN 角色擁有與 ADMIN 同等權限 (defense-in-depth)

> **修補狀態 (PAT-157)**: ⏸ 排到獨立 PR — 須產品決策 dept-scoped user mgmt 的範圍（DEPT_ADMIN 能管同部門使用者？能 reset password？能 unlock？），加 `department` 欄位驗證 + `OwnershipVerifier` 擴充。獨立 follow-up 追蹤。當前緩解：`AdminCreateUserRequest.role` 的 regex `"ADMIN|USER"` 阻擋從 API 建立 DEPT_ADMIN 帳號（已驗證）。

| CWE | [CWE-269](https://cwe.mitre.org/data/definitions/269.html) Improper Privilege Management |
| CVSS (我估) | 5.5 (Medium) |
| 檔案 | `backend/src/main/java/com/cqlplatform/controller/AdminController.java:35` |

**描述**
`AdminController` 的 `@PreAuthorize("hasAnyRole('ADMIN', 'DEPARTMENT_ADMIN')")` 與 `SecurityConfig:219` 的 path matcher 都把 DEPT_ADMIN 視同 ADMIN，可呼叫 `createUser` / `updateUserRole` / `resetUserPassword` / `unlock`。

**動態驗證 (本機)**
`AdminCreateUserRequest` 的 `role` 欄位 regex `"ADMIN|USER"` 不允許從 API 建立 DEPT_ADMIN ✅。但若 DEPT_ADMIN 由其他途徑建立 (DB seed, migration, future feature)，他能直接把任何人 (除了自己) 提升為 ADMIN。

**建議修補方向**
- 區分 endpoint：`updateUserRole` / `resetUserPassword` 限 ADMIN；DEPT_ADMIN 只能管自己 department 的使用者
- `@PreAuthorize` 改為 method-level 細粒度而非 class-level

---

### **MEDIUM-2**: JWT 存在 localStorage — XSS 即 token 失竊

> **修補狀態 (PAT-157)**: ⏸ 排到獨立 PR — 將 access token 也改 HttpOnly cookie 屬架構級 BFF 改造，risk/reward 須單獨評估（影響所有 17 個 API client 模組）。當前緩解（已就位）：DOMPurify 包 `dangerouslySetInnerHTML`、CSP `script-src 'self'`、JWT TTL 15 min、token version 即時撤銷、HttpOnly refresh cookie。

| CWE | [CWE-922](https://cwe.mitre.org/data/definitions/922.html) Insecure Storage of Sensitive Information |
| CVSS (我估) | 5.4 (Medium) |
| 檔案 | `frontend/src/api/client.ts:84` |

**描述**
Access token 存在 `localStorage`，任何 XSS (即使範圍很小) 都能 `localStorage.getItem('token')` 偷走 + 立刻發 `/api/admin/users`。

**目前緩解**
- DOMPurify 包 `dangerouslySetInnerHTML` ✅
- CSP `default-src 'self'; script-src 'self'`
- Token TTL 短 (15 min) + token version 即時撤銷
- HttpOnly refresh cookie

**建議**
- 維持目前架構 (SPA 常見) 但加 SubResource Integrity (SRI) 給第三方 CDN
- 評估「access token 也放 HttpOnly cookie + 用 BFF」(較大重構)

---

### **MEDIUM-3**: `/api/auth/register` 公開可註冊 USER 角色 + 自動登入

> **修補狀態 (PAT-157)**: ✅ 已修 — `auth.self-registration.enabled` feature flag 預設 false（`AUTH_SELF_REGISTRATION_ENABLED=true` 才會開）。flag false 時 `/api/auth/register` 一律回 200 + uniform pending message（與 username-taken 路徑外觀完全相同）。動態驗證：weak/strong password 兩種輸入都得到同一回應，無法用此 endpoint 探測啟用狀態或 username 是否被佔用。

| CWE | [CWE-284](https://cwe.mitre.org/data/definitions/284.html) Improper Access Control |
| CVSS (我估) | 5.0 (Medium) — 政策問題，視業務需求 |
| 檔案 | `backend/src/main/java/com/cqlplatform/controller/AuthController.java:112-144` |

**描述**
任何人可透過 POST `/api/auth/register` 自助註冊 USER 帳號並立即拿到 JWT。對醫療 / TFDA 監管軟體，註冊通常應由 admin 控管 (ISO 27001 / IEC 62304 access control)。

**動態驗證**
- 密碼長度有限制 (8-100) — 但無複雜度檢查
- 無 CAPTCHA / email 驗證 — 可批量註冊垃圾帳號

**建議修補方向**
- 加 feature flag `auth.self-registration.enabled` (預設 false)
- 啟用時要求 email 驗證 + CAPTCHA + admin approval

---

### **MEDIUM-4**: 註冊端點洩漏「Username already exists」— 使用者枚舉

> **修補狀態 (PAT-157)**: ✅ 已修 — duplicate-username 路徑改回 200 + uniform message（與 flag-off 路徑共用同一個 `REGISTRATION_PENDING_RESPONSE` 常數）。`AuthControllerTest.register_existingUser_returnsUniformResponse` + `AuthIntegrationTest.duplicateRegistration_returnsUniformPendingResponse` 鎖回正確行為。

| CWE | [CWE-200](https://cwe.mitre.org/data/definitions/200.html) Information Exposure |
| CVSS (我估) | 4.3 (Medium) |
| 檔案 | `AuthController.java:115-117` |

**描述**
`/api/auth/register` 對已存在的 username 回 `400 Username already exists`。攻擊者可枚舉系統內存在的 username (login + forgot-password 都已防枚舉，但這裡漏了)。

**建議**
- 統一回應「Account creation in progress, you'll receive confirmation by email」(類比 forgot-password 模式)
- 或要求 email 驗證後才確認註冊 (順便解 MEDIUM-3)

---

### **MEDIUM-5**: H2 driver 在 `<scope>runtime</scope>` — production JAR 含已知 CVE 史

> **修補狀態 (PAT-157)**: ✅ 已修 — `pom.xml` 改 `<scope>test</scope>`，production JAR 不再含 H2。Maven `clean compile` 通過、auth + admin 測試 31 個全綠。

| CWE | [CWE-1104](https://cwe.mitre.org/data/definitions/1104.html) Use of Unmaintained Third Party Components |
| CVSS (我估) | 4.0 (Medium) |
| 檔案 | `backend/pom.xml:144-149` |

**描述**
H2 driver 是 runtime scope，會被打進生產 JAR。雖然不會初始化連線，但 H2 console / SQL injection paths 在歷史上有過 CVE (CVE-2022-23221 RCE)。建議改為 `<scope>test</scope>`，僅在測試 profile 用。

**建議**
```xml
<scope>test</scope>
```

---

### **MEDIUM-6**: `/api/ehr/subscriptions/callback` permitAll — 無來源驗證

> **修補狀態 (PAT-157)**: ⏸ 排到獨立 PR — HMAC signature verification 須加 DB schema (per-subscription secret 欄位) + endpoint side 簽章驗證 + 對外通知端要設定 secret 並送 `X-Hub-Signature` header。FHIR Subscription standard 不強制此 header，須與接收端機構協調 — 屬產品/整合決策不適合進綜合 PR。

| CWE | [CWE-306](https://cwe.mitre.org/data/definitions/306.html) Missing Authentication for Critical Function |
| CVSS (我估) | 4.7 (Medium) |
| 檔案 | `SecurityConfig:193`, `EhrIntegrationController:214-223` |

**描述**
FHIR Subscription callback 設為 permitAll (合理 — 外部 FHIR server 無法 auth)，但實作只看 `X-Subscription-Id` header 就呼叫 `subscriptionService.handleNotification`。攻擊者可偽造任意 subscription notification，污染下游處理 (e.g. 觸發誤導性的 CDS card 或 import flow)。

**建議**
- 為每個 subscription 預先生成 HMAC secret，回呼時要求 `X-Hub-Signature` header
- 或限制來源 IP allow-list (產業常見做法)
- 或 mTLS (對接機構若支援)

---

### **LOW-1**: nginx CSP 比後端寬鬆 — `'unsafe-inline'` + `'unsafe-eval'` for scripts

> **修補狀態 (PAT-157)**: ✅ 已修 — `docker/nginx.conf` script-src 移除 `'unsafe-inline'` / `'unsafe-eval'` / `cdn.jsdelivr.net`（前端 grep 確認 SPA 完全不引用 jsdelivr）。保留 style-src `'unsafe-inline'`（MUI / Emotion runtime style tag 需要）+ worker-src `'self' blob:`（Monaco editor worker）。

| 檔案 | `docker/nginx.conf:17` |

**描述**
nginx 設的 CSP 對 script-src 加了 `'unsafe-inline' 'unsafe-eval'`。雖然有 fonts.cdn.jsdelivr.net 的需求，但 inline + eval 組合等於關掉大部分 CSP 防 XSS 能力。

後端 `SecurityConfig` 設的 CSP 較嚴 (`script-src 'self'`)，但 nginx 在 response 上覆蓋 — `add_header always` 在 SPA 路由 / 靜態檔案 location 都生效，且兩個 CSP header 同時存在時瀏覽器取交集 (對的方向，但 inline/eval 仍鬆綁)。

**建議**
- 評估是否能拿掉 `'unsafe-eval'` (只 Monaco editor 可能需要 — 確認用 `eval-mode` 而非 worker)
- 拿掉 `'unsafe-inline'`，把 inline script 改成 nonce-based 或外部檔案

---

### **LOW-2**: 密碼複雜度只查長度 (8–100)

> **修補狀態 (PAT-157)**: ✅ 已修 — 4 個 DTO（`RegisterRequest` / `ResetPasswordRequest` / `ChangePasswordRequest` / `AdminCreateUserRequest`）的 password 欄位加 `@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,100}$")`，要求至少一個小寫、一個大寫、一個數字。動態驗證：`alllowercase1` 從通過改為 400 + `must contain at least one lowercase letter, one uppercase letter, and one digit`。**非合規完整改善**：未加常見密碼字典撞庫檢查 (e.g. zxcvbn / hibp.org)；獨立 PR 評估是否引入。

| 檔案 | `RegisterRequest` (推測) |

**描述**
動態驗證顯示密碼只查 `size 8–100`。沒大小寫 / 數字 / 特殊符號要求；無 hibp.org 撞庫檢查 (e.g. `12345678`, `password`, `qwertyui` 都會通過)。

**建議**
- 至少加 zxcvbn 或 OWASP password guidelines (NIST 800-63B)：12+ chars + 不在常見密碼字典

---

### **LOW-3**: `/api/version` 回 401 — 與 SecurityConfig 宣告不一致

**描述**
SecurityConfig 第 205 行寫 `requestMatchers(GET, "/api/version").permitAll()`，但動態測試顯示 401。可能是 Spring Security 路由匹配優先序問題或某個 filter 提前 reject。低風險但會讓 SPA 的 cache-bust 偵測失效。

**建議**
- 跑 debug 確認哪個 matcher 接到，必要時用 `securityMatcher` 拆獨立 chain

---

### **LOW-4**: 依賴漏洞掃描未完整 (測試環境限制)

**描述**
本次審計嘗試 `npm audit` 失敗 (TLS 憑證鍊問題，疑公司網路 proxy)，未能取得 npm 端的 advisory data。Maven 端只做 manual review。

**已觀察**
- pom.xml 已有多項 CVE override (postgresql 42.7.11, jackson 2.21.1 / 3.1.1, tomcat 10.1.54, spring-security 6.5.9, hl7.fhir.* 6.9.0) — **積極維護中** ✅
- `jjwt-api 0.12.6` 與 `jjwt-impl 0.13.0` 版本不一致 — 不算安全問題但偶爾 runtime 怪事
- `axios ^1.15.0` (range) — 1.15.0 應該 OK，但建議釘版號避免日後升到含 CVE 的版本
- `vitest ^1.2.0` — 開發依賴，risk 很低，但 1.x 早期 browser mode 有 GHSA-9crc-q9x8-hgqq

**建議**
- 加 `mvn dependency-check` 或 OWASP Dependency-Check 到 CI pipeline (跑在 sonatype-public)
- npm 改用 Snyk / GitHub Dependabot (應該已開)

---

### **INFO-1**: CORS `allowCredentials=true` + `setAllowedOriginPatterns` — 注意設定

`WebConfig.corsFilter()` 對 allow-list origin 啟用 credentials。allow-list 已禁用通配符 ✅。但 `setAllowedOriginPatterns` 用 pattern 而非 exact origins — 確認 patterns 不含 `*` 或 prefix-match (如 `https://*.evil.com` 之類)。檢查 `getAllAllowedOrigins` 結果 — 看起來都是 exact origin string，**目前 OK**，但若日後加 pattern 要當心。

---

### **INFO-2**: 沒打到 production VM — 報告盲區

本次測試未對 187.77.155.248 動手 (依使用者選擇). 因此：
- 無法確認 production 是否仍有 admin/admin
- 無法確認 production 是否用同一份 `.env`
- 無法確認 production CSP / TLS / HSTS 行為

**建議下一步**
若要驗 production，可以 (1) 嘗試 `admin/admin` 登入 production (login API 即可，無破壞性) (2) 若失敗則 OK，若成功則立刻處理 CRITICAL-1 + rotate JWT_SECRET / ENCRYPTION_KEY。

---

## TFDA / IEC 62304 法規追溯建議

對應 CRITICAL / HIGH 層級的發現，建議按 CLAUDE.md 規範開以下 Issue：

| 發現 | TFDA Issue 模板 | 安全性等級 |
|------|-----------------|------------|
| CRITICAL-1 (預設憑證) | `[需求]` + `[設計]` + `[風險]` + `[驗證]` | C (可能影響患者資料) |
| CRITICAL-2 (JWT secret) | `[風險]` + `[驗證]` | C |
| CRITICAL-3 (PHI key) | `[風險]` + `[驗證]` | C — 直接影響 PHI 機密性 |
| CRITICAL-4 (SSRF profile) | `[需求]` + `[設計]` + `[風險]` + `[驗證]` | B-C |
| HIGH-1 (DNS rebinding) | `[風險]` + `[驗證]` | B |
| HIGH-2 (DB password) | `[風險]` | B |
| MEDIUM-1 (DEPT_ADMIN) | `[設計]` + `[驗證]` | B |
| MEDIUM-3 (self-register policy) | `[需求]` | A-B |

---

## 建議的修補優先序

> 2026-05-08 PAT-157 已交付的修補以 ✅ 標示。剩餘項目按嚴重度與實作風險排序。

1. **立刻 (今天)**:
   - ✅ rotate `JWT_SECRET` + `ENCRYPTION_KEY` + DB password (dev 環境完成；production VM 同步動作為運維責任)
   - ⏳ 確認 production 不存在 admin/admin (login API 試一次)
2. **本週**:
   - ✅ 改 `isLocalDevelopment` 不認 `docker`
   - ✅ H2 driver 改 test scope
   - ✅ register endpoint feature flag + uniform response
   - ✅ password complexity validator
   - ✅ nginx CSP 收緊
   - ⏳ 改 `DataInitializer` profile 限定 `dev` (CRITICAL-1，使用者請求暫保留)
3. **本月** (排到獨立 follow-up PR):
   - ⏳ MEDIUM-1 細粒度化 DEPT_ADMIN 權限
   - ⏳ MEDIUM-6 subscription callback HMAC 驗證
   - ⏳ 補 OWASP Dependency-Check / Dependabot 到 CI pipeline
4. **下季** (架構級改造):
   - ⏳ HIGH-1 DNS rebinding IP-locking outbound interceptor
   - ⏳ MEDIUM-2 JWT 儲存策略 (BFF 改造)
   - ⏳ CRITICAL-3 production ENCRYPTION_KEY 輪替 + envelope encryption migration

---

## 附錄 — 動態測試紀錄

| 測試項 | 結果 | 備註 |
|--------|------|------|
| `admin / admin` 登入 | ✅ 成功取得 ADMIN JWT | CRITICAL-1 |
| 用洩漏 secret 偽造 admin JWT 打 `/api/admin/users` | ✅ 成功 (200) | CRITICAL-2 |
| `?fhirServer=http://127.0.0.1:8080/fhir` | ✅ 503 (out-going attempted) | CRITICAL-4 |
| `?fhirServer=http://169.254.169.254/` | ✅ 503 (out-going attempted) | CRITICAL-4 |
| `?fhirServer=file:///etc/passwd` | ✅ 400 拒絕 | InputValidator OK |
| `?fhirServer=gopher://...` | ✅ 400 拒絕 | InputValidator OK |
| 註冊弱密碼 `a` | ✅ 400 拒絕 (size 8-100) | LOW-2 |
| 註冊既有 username | ⚠️ 400 + "Username already exists" | MEDIUM-4 |
| 登出後舊 token 打 `/api/admin/users` | ✅ 401 拒絕 | Token version 撤銷有效 |
| `demo / password` 登入 | ✅ 401 拒絕 | 已有人改過密碼 |
| 透過 admin 試建 DEPT_ADMIN 帳號 | ✅ 400 (regex 拒絕) | 但既有 DEPT_ADMIN 仍有問題 |

---

*報告產出日期 2026-05-08，分析人 Claude Code (自動化滲透測試)。本報告僅作內部安全評估與 TFDA 法規佐證，不代表 lawyer / 認證機構對 fair use 或合規性的法律意見。*
