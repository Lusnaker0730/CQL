# CQL Platform 部署與 AI 設定指南

> 本文件涵蓋 Docker Compose 部署、環境變數配置、AI 提供者設定、監控堆疊、以及常用維運操作。

---

## 目錄

1. [系統架構概覽](#1-系統架構概覽)
2. [前置需求](#2-前置需求)
3. [快速部署](#3-快速部署)
4. [環境變數說明](#4-環境變數說明)
5. [AI 提供者設定](#5-ai-提供者設定)
6. [各服務詳細說明](#6-各服務詳細說明)
7. [Nginx 反向代理](#7-nginx-反向代理)
8. [監控堆疊](#8-監控堆疊)
9. [資料庫備份與還原](#9-資料庫備份與還原)
10. [台灣 FHIR 病患資料產生器](#10-台灣-fhir-病患資料產生器)
11. [開發環境](#11-開發環境)
12. [Kubernetes 部署](#12-kubernetes-部署)
13. [常見問題排除](#13-常見問題排除)

---

## 1. 系統架構概覽

```
                        ┌─────────────────────────────────────────────┐
                        │              Docker Network                 │
  使用者 ──► :8888 ──►  │  ┌──────────┐      ┌──────────┐            │
                        │  │ Frontend │─API─►│ Backend  │            │
                        │  │ (Nginx)  │      │(Spring)  │            │
                        │  └──────────┘      └────┬─────┘            │
                        │       │                 │                   │
                        │  ┌────┴────┐    ┌───────┼───────┐          │
                        │  │Grafana  │    │       │       │          │
                        │  │Prometheus│   ▼       ▼       ▼          │
                        │  └─────────┘ Postgres HAPI-FHIR Ollama    │
                        │                                 (選用)     │
                        └─────────────────────────────────────────────┘
```

| 服務 | 技術 | 用途 |
|------|------|------|
| **Frontend** | React + TypeScript + Nginx | SPA 前端，透過 Nginx 反向代理 API |
| **Backend** | Spring Boot 4.0 + Java 21 | REST API、CQL 引擎、CDS Hooks、WebSocket 通知推送（PAT-167） |
| **PostgreSQL** | PostgreSQL 16 Alpine | 使用者、CQL 程式庫、指標定義等資料儲存 |
| **HAPI FHIR** | HAPI FHIR Server (R4) | FHIR 資料儲存與術語服務 |
| **Ollama** | Ollama + qwen2.5-coder:7b | 本地 GPU AI — CQL 錯誤修正建議（選用） |
| **Prometheus** | Prometheus v2.51 | 指標收集（scrape interval 10s） |
| **Grafana** | Grafana 10.4 | 儀表板視覺化 |
| **Taiwan FHIR Generator** | Python 3.11 + Flask | 台灣 TWCORE 病患資料批次產生（選用） |

---

## 2. 前置需求

| 需求 | 版本 |
|------|------|
| Docker Engine | 24+ |
| Docker Compose | v2.20+ |
| 可用記憶體 | 最低 4 GB（含 Ollama 需 12 GB+） |
| 可用磁碟 | 最低 10 GB |
| NVIDIA GPU（選用） | CUDA 驅動 + NVIDIA Container Toolkit（僅 Ollama 需要） |

---

## 3. 快速部署

### 3.1 基本部署（無 AI）

```bash
cd docker/

# 建立環境變數檔案
cp .env.example .env

# 編輯 .env（至少修改所有 CHANGE_ME 項目）
# 必須修改：POSTGRES_PASSWORD、DB_PASSWORD、JWT_SECRET、ENCRYPTION_KEY

# 啟動所有核心服務
docker compose up -d
```

啟動後：
- **前端**：`http://localhost:8888`
- **預設管理員帳號**：`admin` / `admin`（首次登入後立即修改密碼）

### 3.2 含本地 AI（Ollama）

```bash
# .env 中設定：
# AI_PROVIDER=ollama
# OLLAMA_URL=http://ollama:11434
# OLLAMA_MODEL=qwen2.5-coder:7b

docker compose --profile ollama up -d

# 首次啟動需手動下載模型（約 4.4 GB）
docker exec docker-ollama-1 ollama pull qwen2.5-coder:7b
```

### 3.3 含台灣 FHIR 病患資料產生器

```bash
docker compose --profile twcore up -d
```

### 3.4 全部功能

```bash
docker compose --profile ollama --profile twcore up -d
```

### 3.5 重新建置映像

```bash
# 單獨重建後端
docker compose build backend

# 單獨重建前端
docker compose build frontend

# 重建並重新啟動
docker compose up -d --build
```

---

## 4. 環境變數說明

### 4.1 必要變數

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `POSTGRES_DB` | 資料庫名稱 | `cqlplatform` |
| `POSTGRES_USER` | 資料庫使用者 | `cqlplatform` |
| `POSTGRES_PASSWORD` | 資料庫密碼 | **必須設定** |
| `DB_URL` | JDBC 連線字串 | `jdbc:postgresql://postgres:5432/cqlplatform` |
| `DB_USERNAME` | 同 `POSTGRES_USER` | `cqlplatform` |
| `DB_PASSWORD` | 同 `POSTGRES_PASSWORD` | **必須設定** |
| `JWT_SECRET` | JWT 簽章金鑰（HS256，至少 256 bits） | **必須設定** |
| `ENCRYPTION_KEY` | AES 加密金鑰（恰好 32 bytes） | **必須設定** |

### 4.2 FHIR 相關

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `FHIR_SERVER_URL` | FHIR R4 資料伺服器 | `http://hapi-fhir:8080/fhir` |
| `FHIR_TERMINOLOGY_URL` | 術語查詢伺服器 | `http://tx.fhir.org/r4` |
| `VSAC_API_KEY` | NLM VSAC API Key（ValueSet 查詢用） | 空 |

### 4.3 安全性

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `CORS_ALLOWED_ORIGINS` | 允許的跨域來源（逗號分隔） | 空（僅同源） |
| `APP_BASE_URL` | 應用程式公開 URL（用於密碼重設信件等） | — |
| `SMART_ISSUER` | SMART on FHIR issuer URL | — |
| `SMART_AUTH_ENDPOINT` | SMART 授權端點 | — |
| `SMART_TOKEN_ENDPOINT` | SMART Token 端點 | — |
| `AUTH_SELF_REGISTRATION_ENABLED` | 是否開放 `POST /api/auth/register` 自助註冊（PAT-157）。預設 `false` —— 醫療 / TFDA 受規範部署慣例要求由 admin 統一配發帳號（IEC 62304 access control）。設為 `true` 才會真的建立帳號；否則無論成功失敗都回傳統一的「待審核」訊息（同時也是「使用者名稱已被佔用」的回應 —— 避免 CWE-200 使用者列舉）。**VM-specific 部署建議透過 `docker-compose.override.yml` 設定**，避免污染 tracked 的 `docker-compose.yml`。 | `false` |

### 4.4 AI 相關（詳見第 5 節）

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `AI_PROVIDER` | AI 模式：`none` / `ollama` / `cloud` | `none` |
| `OLLAMA_URL` | Ollama 服務 URL | `http://ollama:11434` |
| `OLLAMA_MODEL` | Ollama 模型名稱 | `qwen2.5-coder:7b` |
| `OLLAMA_TIMEOUT` | Ollama 請求逾時（秒） | `120` |
| `AI_CLOUD_API_URL` | Cloud AI API 端點 | `https://api.openai.com/v1/chat/completions` |
| `AI_CLOUD_MODEL` | Cloud AI 模型名稱 | `gpt-4o-mini` |
| `AI_CLOUD_API_KEY` | Cloud AI API Key | 空 |
| `AI_CLOUD_TIMEOUT` | Cloud AI 請求逾時（秒） | `60` |

### 4.5 監控

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `GF_SECURITY_ADMIN_USER` | Grafana 管理員帳號 | `admin` |
| `GF_SECURITY_ADMIN_PASSWORD` | Grafana 管理員密碼 | **必須設定** |
| `METRICS_SCRAPE_USER` | Prometheus scrape HTTP Basic username | `prometheus` |
| `METRICS_SCRAPE_PASSWORD` | Prometheus scrape HTTP Basic password（PAT-113） | **必須設定** |

> **⚠️ PAT-113 之後**：`/actuator/prometheus` 預設要求 HTTP Basic 認證。`METRICS_SCRAPE_PASSWORD` 未設 → prometheus container 啟動時 FATAL exit，整個監控鏈斷掉（但 backend / frontend 不受影響）。
>
> 產生指令：
> ```bash
> openssl rand -base64 24
> ```
> 把輸出值填入 `.env` 的 `METRICS_SCRAPE_PASSWORD=`。`METRICS_SCRAPE_USER` 可留 `prometheus` 預設值。兩個變數的值必須在 backend 和 prometheus 兩個 container 之間一致（docker-compose 都從同一個 `.env` 讀）。

### 4.6 Thread pool 調校（PAT-109）

Backend 有兩個主要 thread pool：CQL 執行池（同步 API）與 bulk patient import 池（背景 EHR 匯入）。兩者刻意獨立以避免 bulk import 癱瘓 API。

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `RATE_LIMIT_TRANSLATE_RPM` | CQL 翻譯 per-IP 速率 | `20` |
| `RATE_LIMIT_EXECUTE_RPM` | CQL 執行 per-IP 速率 | `10` |
| `RATE_LIMIT_FIX_SUGGESTION_RPM` | AI 修正建議 per-IP 速率 | `5` |
| `RATE_LIMIT_CDS_DISCOVERY_RPM` | CDS discovery 端點 per-IP 速率（PAT-107） | `20` |
| `EHR_IMPORT_THREAD_POOL_SIZE` | Bulk import 核心 thread 數 | `4` |
| `EHR_IMPORT_MAX_POOL_SIZE` | Bulk import 最大 thread 數 | `8` |
| `EHR_IMPORT_QUEUE_CAPACITY` | Bulk import queue 容量 | `100` |

> **調校時機**：Grafana 的「Patient Import Pool」panel 顯示 queue depth 持續接近 80、或 HikariCP `pending` > 3 持續數分鐘，代表 pool 飽和，可上調對應變數。調大前先確認資料庫 `max_connections`（預設 100）還夠用。

---

## 5. AI 提供者設定

平台支援三種 AI 模式，用於提供 CQL 錯誤的自動修正建議。透過 `AI_PROVIDER` 環境變數切換。

### 5.1 `none`（預設）— 不使用 AI

```env
AI_PROVIDER=none
```

不會呼叫任何外部 AI 服務。CQL 編輯器不會顯示「AI Fix」按鈕。

### 5.2 `ollama` — 本地 GPU 推論

適合有 NVIDIA GPU 且需資料不離開內網的場景。

```env
AI_PROVIDER=ollama
OLLAMA_URL=http://ollama:11434
OLLAMA_MODEL=qwen2.5-coder:7b
OLLAMA_TIMEOUT=120
```

**啟動步驟：**

```bash
# 1. 使用 ollama profile 啟動
docker compose --profile ollama up -d

# 2. 首次需下載模型（一次性，約 4.4 GB）
docker exec docker-ollama-1 ollama pull qwen2.5-coder:7b

# 3. 驗證模型可用
docker exec docker-ollama-1 ollama list
```

**資源需求：**
- 容器限制：4 CPU / 8 GB RAM
- GPU：透過 NVIDIA Container Toolkit 保留所有可用 GPU
- 磁碟：模型檔約 4-5 GB

**可用模型建議：**

| 模型 | 大小 | 速度 | 品質 | 備註 |
|------|------|------|------|------|
| `qwen2.5-coder:7b` | 4.4 GB | 快 | 佳 | **預設推薦** |
| `qwen2.5-coder:14b` | 8.9 GB | 中 | 優 | 需 16 GB+ VRAM |
| `codellama:7b` | 3.8 GB | 快 | 普通 | 備選方案 |
| `deepseek-coder-v2:16b` | 8.9 GB | 中 | 優 | 需較大 VRAM |

更換模型：

```bash
# 下載新模型
docker exec docker-ollama-1 ollama pull qwen2.5-coder:14b

# 更新 .env
OLLAMA_MODEL=qwen2.5-coder:14b

# 重啟 backend
docker compose restart backend
```

### 5.3 `cloud` — 雲端 AI（OpenAI 相容）

適合無 GPU 但允許資料傳至外部 API 的場景。

```env
AI_PROVIDER=cloud
AI_CLOUD_API_URL=https://api.openai.com/v1/chat/completions
AI_CLOUD_MODEL=gpt-4o-mini
AI_CLOUD_API_KEY=sk-xxxxxxxxxxxxxxxx
AI_CLOUD_TIMEOUT=60
```

**相容的雲端服務：**

| 服務 | API URL | 模型範例 |
|------|---------|----------|
| OpenAI | `https://api.openai.com/v1/chat/completions` | `gpt-4o-mini` |
| Azure OpenAI | `https://{resource}.openai.azure.com/openai/deployments/{model}/chat/completions?api-version=2024-02-15-preview` | 依部署名稱 |
| DeepSeek | `https://api.deepseek.com/v1/chat/completions` | `deepseek-coder` |
| Groq | `https://api.groq.com/openai/v1/chat/completions` | `llama-3.1-70b-versatile` |

任何支援 OpenAI `/v1/chat/completions` 格式的端點皆可使用。

### 5.4 斷路器與重試機制

兩種 AI 提供者皆內建 Resilience4j 保護機制：

| 參數 | 值 | 說明 |
|------|------|------|
| 失敗率閾值 | 50% | 超過時斷路器開啟 |
| 斷路器開啟等待 | 60 秒 | 開啟後等待此時間才半開嘗試 |
| 最大重試次數 | 2 | 失敗後重試一次 |
| 重試等待 | 2 秒 | 兩次重試之間間隔 |

即使 AI 服務完全離線，也**不會**影響 CQL 執行、翻譯等核心功能。

---

## 6. 各服務詳細說明

### 6.1 Backend（Spring Boot 4.0）

**映像建置**：多階段 Docker Build
- 建置階段：`maven:3.9-eclipse-temurin-21`
- 執行階段：`eclipse-temurin:21-jre-alpine`（非 root 使用者 `appuser`）

**主要相依版本**：Spring Boot 4.0.6 / HAPI FHIR 8.8.1 / Spring Security 6.5.9 / Jackson 3.1.1 / Tomcat 10.1.54 / CQL Framework 4.5.0

**JVM 參數**：`-Xms256m -Xmx768m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError`

**資源限制**：2 CPU / 1.5 GB RAM

**健康檢查**：`/actuator/health`（30 秒間隔、5 次重試、30 秒啟動等待）

**關鍵應用程式設定**（`application.yml`）：

| 設定路徑 | 預設值 | 說明 |
|----------|--------|------|
| `server.port` | `8080` | 服務埠 |
| `spring.datasource.hikari.maximum-pool-size` | `20` | 資料庫連線池上限 |
| `spring.jpa.hibernate.ddl-auto` | `validate` | 僅驗證 schema（由 Flyway 管理遷移） |
| `jwt.expiration-ms` | `86400000`（24h） | JWT Token 有效期 |
| `rate-limit.requests-per-minute` | `60` | API 速率限制 |
| `cql.execution.timeout-seconds` | `120` | CQL 執行逾時 |
| `cql.execution.thread-pool-size` | `10` | CQL 執行緒池核心數 |
| `cql.execution.max-pool-size` | `20` | CQL 執行緒池上限 |
| `cql.execution.queue-capacity` | `50` | CQL 等待佇列容量 |
| `fhir.client.connect-timeout-ms` | `5000` | FHIR 連線逾時 |
| `fhir.client.socket-timeout-ms` | `15000` | FHIR 讀取逾時 |

### 6.2 Frontend（Nginx）

**映像建置**：多階段 Docker Build
- 建置階段：`node:26-alpine`（`npm ci && npm run build`，`NODE_OPTIONS=--max-old-space-size=4096` 避免 Vite + Monaco bundle OOM —— PR #543）
- 執行階段：`nginxinc/nginx-unprivileged:alpine`

**資源限制**：0.5 CPU / 256 MB RAM

**對外埠**：`8888:8080`

### 6.3 PostgreSQL

**映像**：`postgres:16-alpine`

**資源限制**：1 CPU / 512 MB RAM

**WAL 歸檔**：已啟用（`wal_level=replica`），備份至 `/backups/wal/`

**健康檢查**：`pg_isready`（10 秒間隔）

### 6.4 HAPI FHIR

**映像**：`hapiproject/hapi:latest`

**資源限制**：1 CPU / 2 GB RAM

**設定**：
- FHIR 版本：R4
- 預設編碼：JSON
- 允許外部參考：是
- 禁止批次刪除：是

---

## 7. Nginx 反向代理

Frontend 容器內嵌的 Nginx 負責所有路由：

| 路徑 | 轉發目標 | 說明 |
|------|----------|------|
| `/api/health` | `backend:8080/actuator/health` | 健康檢查 |
| `/actuator/` | 回傳 404 | 封鎖直接存取 actuator |
| `/api/ws/notifications` | `backend:8080`（含 `Upgrade` / `Connection: upgrade`） | **PAT-167** WebSocket 通知推送 — 取代舊 SSE `/api/notifications/subscribe`。`proxy_read_timeout 86400s`；backend 每 25 秒送 protocol-level ping frame，Cloudflare WS 300s idle 視為 activity 不會砍連線。 |
| `/api/` | `backend:8080` | 所有 REST API（逾時 180s） |
| `/cds-services` | `backend:8080` | CDS Hooks |
| `/grafana/` | `grafana:3000` | 監控儀表板 |
| `/prometheus/` | `prometheus:9090` | 指標查詢 |
| `/twcoredata/` | `taiwan-fhir-generator:5000` | 病患資料產生器 |
| `/` | 靜態檔案 + SPA fallback | React 前端 |

**安全 Headers**（自動套用於所有回應）：
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `Strict-Transport-Security: max-age=31536000`
- `Content-Security-Policy`（限制性原則）
- `Permissions-Policy`（禁用攝影機/麥克風/地理位置/付款）

---

## 8. 監控堆疊

### 8.1 Prometheus

- **抓取間隔**：10 秒（`/actuator/prometheus`）
- **資料保留**：30 天
- **告警規則**：自動載入 `prometheus-alerts.yml`

### 8.2 告警規則

| 告警名稱 | 觸發條件 | 嚴重程度 | 持續時間 |
|----------|----------|----------|----------|
| HighErrorRate | 5xx 錯誤率 > 5% | Critical | 2 分鐘 |
| HighLatency | P95 延遲 > 5 秒 | Warning | 5 分鐘 |
| ServiceDown | 服務無回應 | Critical | 1 分鐘 |
| HighHeapUsage | JVM Heap > 85% | Warning | 5 分鐘 |
| CqlExecutionFailures | CQL 錯誤率 > 0.1/s | Warning | 3 分鐘 |
| CqlQueueSaturation | 佇列深度 > 40（上限 50） | Warning | 2 分鐘 |
| FhirCircuitBreakerOpen | FHIR 斷路器開啟 | Critical | 1 分鐘 |

### 8.3 告警通知（BUG-120：已移除 Alertmanager）

原本的 Alertmanager 三個 receiver（default / pager / slack）全部指向不存在的
`localhost:9095` webhook（初版監控骨架留下的 placeholder，從未實作），任何告警
都送不出去且會在 Alertmanager log 持續累積 connection-refused 錯誤，因此已移除。

目前狀態：上述告警規則仍由 Prometheus 評估，可在 Prometheus UI（`:9090/alerts`）
直接檢視 firing 狀態，但**不會主動推播**到任何外部通道。若日後要恢復通知，於
`docker/prometheus.yml` 補回 `alerting:` 區塊並設定一個真實 receiver（Slack
`slack_configs` 或 Email `email_configs`）即可。

### 8.4 Grafana

- **存取路徑**：`http://localhost:8888/grafana/`
- **預設帳密**：由 `GF_SECURITY_ADMIN_USER` / `GF_SECURITY_ADMIN_PASSWORD` 控制
- **自動佈建**：啟動時自動載入 Prometheus 資料源 + CQL Platform 儀表板

---

## 9. 資料庫備份與還原

### 9.1 備份

```bash
cd docker/
./scripts/backup-db.sh                    # 使用預設容器名稱
./scripts/backup-db.sh my-postgres-1      # 指定容器名稱
```

- 輸出：`docker/postgres-backup/cqlplatform_YYYYMMDD_HHMMSS.sql.gz`
- 自動清理 30 天以前的備份

### 9.2 還原

```bash
cd docker/
./scripts/restore-db.sh ./postgres-backup/cqlplatform_20260303_120000.sql.gz
```

> 還原會先 DROP 再重建資料庫。執行前會要求確認。

---

## 10. 台灣 FHIR 病患資料產生器

透過 `twcore` profile 啟用。

```bash
docker compose --profile twcore up -d
```

- **Web 介面**：`http://localhost:8888/twcoredata/`（經 Nginx 代理）或 `http://localhost:5000`（直接存取）
- **功能**：
  - 批次產生台灣 FHIR R4 病患 Bundle
  - 支援台灣身分證字號、健保卡號
  - 包含 SNOMED CT（診斷）、LOINC（檢驗）、RxNorm（藥物）
  - 自動上傳至 HAPI FHIR Server

---

## 11. 開發環境

### 11.1 Docker 開發模式（曝露所有埠）

```bash
cd docker/
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```

| 服務 | 開發埠 |
|------|--------|
| PostgreSQL | `localhost:5432` |
| Backend | `localhost:8081` |
| Frontend | `localhost:5173` |
| HAPI FHIR | `localhost:8090` |
| Prometheus | `localhost:9090` |
| Grafana | `localhost:3000` |

### 11.2 本地 Backend 開發（不用 Docker）

```bash
cd backend/

# 使用 dev profile（H2 檔案資料庫，不需 PostgreSQL）
export JWT_SECRET="dev_secret_must_be_at_least_256_bits_long_for_HS256"
export ENCRYPTION_KEY="dev_key_must_be_exactly_32bytes!"

mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Dev profile 使用 H2 檔案資料庫（`./data/cqlplatform`），不需 PostgreSQL。H2 Console 可在 `http://localhost:8080/h2-console` 存取。

### 11.3 本地 Frontend 開發

```bash
cd frontend/
npm install
npm run dev    # Vite dev server at http://localhost:5173
```

---

## 12. Kubernetes 部署

專案在 `k8s/` 目錄下提供完整的 Kubernetes manifests：

| 資源 | 檔案 |
|------|------|
| Namespace | `namespace.yml`（`cql-platform`） |
| ConfigMap | `configmap.yml` |
| Secrets | `secrets.yml`（Bitnami Sealed Secrets） |
| Backend | Deployment + HPA + PDB |
| Frontend | Deployment + HPA + PDB |
| PostgreSQL | StatefulSet |
| HAPI FHIR | Deployment |
| Ingress | `ingress.yml` |
| Network Policies | `network-policies.yml` |

**密鑰管理**：使用 Bitnami Sealed Secrets，加密後可安全提交至 Git。

```bash
# 封裝密鑰
cd k8s/
./scripts/seal-secrets.sh
```

---

## 13. 常見問題排除

### Backend 無法啟動

```bash
# 檢查日誌
docker compose logs backend

# 常見原因：
# 1. PostgreSQL 尚未就緒 → 等待 health check 通過
# 2. Flyway 遷移失敗 → 檢查 DB schema 版本
# 3. JWT_SECRET 未設定或長度不足 256 bits
```

### Ollama 模型回應緩慢

```bash
# 確認 GPU 是否被正確掛載
docker exec docker-ollama-1 nvidia-smi

# 若無 GPU，回退為 CPU 推論（速度會顯著下降）
# 可考慮改用 cloud 模式
```

### CQL 執行逾時（504）

CQL 執行預設逾時為 120 秒。若經常逾時：

```bash
# 檢查佇列深度
curl -s http://localhost:8081/actuator/prometheus | grep cql.execution.queue

# 調整 .env 或 application.yml：
# cql.execution.timeout-seconds=180
# cql.execution.max-pool-size=30
```

### 資料庫連線池耗盡

```bash
# 檢查 HikariCP 指標
curl -s http://localhost:8081/actuator/prometheus | grep hikaricp

# 調整 application.yml：
# spring.datasource.hikari.maximum-pool-size=30
```

### 查看所有服務狀態

```bash
docker compose ps
docker compose logs --since 5m     # 最近 5 分鐘日誌
docker compose top                  # 各容器 process 列表
```

### 開啟自助註冊（PAT-157）

預設 `POST /api/auth/register` 不會真的建立帳號，僅回傳「待審核」訊息（避免 CWE-200 user enumeration）。
要開放使用者自助註冊，**不要**直接改動 git 追蹤的 `docker-compose.yml`（會跟未來的 `git pull` 衝突）；而是建立 untracked 的 override 檔：

```bash
cat > docker/docker-compose.override.yml <<'EOF'
# VM-specific overrides (not tracked in git).
services:
  backend:
    environment:
      - AUTH_SELF_REGISTRATION_ENABLED=true
EOF
docker compose up -d backend
```

驗證：

```bash
docker exec docker-backend-1 sh -c 'echo $AUTH_SELF_REGISTRATION_ENABLED'   # 應為 true
# 然後 POST /api/auth/register 會回傳真實 JWT + role: USER 而非「待審核」訊息
```

### WebSocket 通知失敗（PAT-167）

如果瀏覽器 console 出現 `wss://...` 連不上或 1006 abnormal closure：

```bash
# 1. 確認 backend WebSocket 端點在 nginx 內部直連可用
docker exec docker-backend-1 wget -q --header='Upgrade: websocket' --header='Connection: Upgrade' \
  --header='Sec-WebSocket-Version: 13' --header='Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==' \
  --header='Origin: http://localhost:8080' -S -O- 'http://localhost:8080/api/ws/notifications?ticket=...' 2>&1 | head -3
# 應該看到 HTTP/1.1 101

# 2. 確認 nginx 對該 location 有 Upgrade headers
grep -A8 'location /api/ws/notifications' docker/nginx.conf

# 3. 若連線只持續約 100 秒就被砍，那是 Cloudflare SSE 殘留問題 —
#    本端 backend 已改 WebSocket protocol-level ping（25s 一次），
#    Cloudflare 應該認為是 activity 而不會 idle-timeout。
#    若你還用舊版前端 bundle，會持續打 /api/notifications/subscribe（已不存在 → 401/500）—
#    請使用者 Ctrl+Shift+R 強制刷新。
```

---

## 密鑰輪換週期

| 密鑰 | 建議輪換週期 | 備註 |
|------|-------------|------|
| `DB_PASSWORD` | 90 天 | 需同步更新 PostgreSQL role |
| `JWT_SECRET` | 180 天 | 輪換會使所有 active session 失效 |
| `ENCRYPTION_KEY` | 365 天 | 需先執行雙金鑰重新加密遷移 |
| `GF_SECURITY_ADMIN_PASSWORD` | 90 天 | — |
| `VSAC_API_KEY` | 依 NLM 政策 | — |

詳細輪換步驟請參考 [`docs/secrets-rotation.md`](secrets-rotation.md)。
