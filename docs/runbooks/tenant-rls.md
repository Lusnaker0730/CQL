# Runbook — 租戶 Row-Level Security（PAT-223）

## 這是什麼

從 migration V70 起，PHI 相關表有 PostgreSQL Row-Level Security 政策 `tenant_isolation`：

| 表 | 租戶來源 |
|----|----------|
| `measure_report`、`patient_import`、`ehr_connection` | 自己的 `tenant_id` |
| `test_case` | 父 `measure_definition.tenant_id` |
| `measure_report_group` | 父 `measure_report.tenant_id` |
| `measure_report_population`、`measure_report_stratifier` | group → report |

backend 每次從 pool 取連線時執行
`SELECT set_config('app.tenant_id', <id>, false), set_config('app.rls_bypass', 'on'|'off', false)`
（`TenantAwareDataSource`），值來自 `TenantContext`：

- 有租戶 → 該租戶；無租戶 → `default` 租戶（與 service 的 `effectiveTenantId()` 一致）
- `default` 租戶不存在 → 空字串 → `app_current_tenant()` 為 NULL → **一列都看不到**（fail-closed）
- `TenantContext.runWithRlsBypass(...)` → `app.rls_bypass = on` → 全看（只給系統工作）

**superuser 或 BYPASSRLS 角色無條件繞過 RLS**。Docker 的 `POSTGRES_USER` 就是 superuser，所以 backend 必須以
`DB_APP_USERNAME`（NOSUPERUSER / NOBYPASSRLS）連線，見 `DEPLOYMENT_GUIDE_zh-TW.md` §3.6。

## 症狀 → 原因 → 處置

### 啟動 log 出現 `ERROR Tenant RLS is NOT enforced`（或 `tenant_rls_effective` = 0）

| 原因 | 處置 |
|------|------|
| `DB_APP_USERNAME` 未設，backend 以 superuser owner 連線 | 依 §3.6 建角色、設 env、重啟 backend |
| 角色存在但被改成 superuser / BYPASSRLS | `ALTER ROLE cqlplatform_app NOSUPERUSER NOBYPASSRLS` |
| `no tenant_isolation policies found` | V70 未套用：檢查 `flyway_schema_history`，或 rollback 被跑過 |

功能在此狀態下**照常運作**（隔離退回程式碼條件），只是第二層沒生效。

### backend 以 `TENANT_RLS_STRICT=true` 拒絕啟動

同上表；若要先恢復服務，把 `TENANT_RLS_STRICT` 設回 `false` 重啟，再修角色。

### 某些清單突然變空 / 報表消失（升級到 V70 之後）

- 檢查該請求的租戶：backend log 的 `requestId` 對應 `JwtAuthenticationFilter` 設的 tenant。
- 無租戶的舊帳號會落到 `default` 租戶；資料若掛在別的租戶就看不到 —— 這是**設計行為**，用 `/api/admin/tenants` 把使用者指派到正確租戶。
- 系統排程 / 非 request 執行緒：確認有走 `TenantContext.callWith(rowTenant, ...)`（既有模式），或真的需要跨租戶時用 `runWithRlsBypass`（極少數）。

### `new row violates row-level security policy for table "..."`

寫入的列 `tenant_id`（或父列的租戶）與連線的 `app.tenant_id` 不同。這幾乎一定是程式碼把別租戶的 id 帶進來，
不要用 bypass 蓋掉，去修來源。

### 快速診斷 SQL（以 owner 連線）

```sql
-- 應用角色屬性
SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname IN ('cqlplatform', 'cqlplatform_app');
-- 政策是否存在
SELECT tablename, policyname, permissive FROM pg_policies WHERE policyname = 'tenant_isolation' ORDER BY 1;
-- 模擬 backend 的視角（在 psql 以 app role 登入後）
SELECT set_config('app.tenant_id', '1', false), set_config('app.rls_bypass', 'off', false);
SELECT count(*) FROM measure_report;
```

## 寫資料 migration 時

這些表是 `FORCE ROW LEVEL SECURITY`。superuser owner 不受影響；若 owner 不是 superuser，資料 migration 要先：

```sql
SET LOCAL app.rls_bypass = 'on';
```

## 回滾

1. `backend/src/main/resources/db/rollback/rollback_V70__tenant_rls_phase1_phi_tables.sql`（以 owner 執行）
2. 移除 `docker/.env` 的 `DB_APP_*`（或留著也無害；backend 退回 owner 連線）
3. 重啟 backend；啟動 log 會改記 `no tenant_isolation policies found`

## 擴大範圍（phase 2）

其餘 11 張 tenant 表（`measure_definition`、`cds_service_config`、`cql_library`、`app_user`、`audit_log`…）尚未有政策。
每批：新 migration + rollback、確認該表的非 request 讀取路徑（啟動載入、排程）是否已有 `callWith` /
`runWithRlsBypass`、加進 `TenantRlsIntegrationTest`、跑全套 smoke。特別注意 `app_user`（登入時尚無租戶）與
`audit_log`（未認證請求也寫）需要不同的政策設計。
