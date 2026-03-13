# CQL Platform — AI 開發指引

> 臨床品質語言 (CQL) 視覺化編輯 + 執行平台

## 技術棧

| 層 | 技術 | 版本 |
|----|------|------|
| Backend | Spring Boot / Java / Maven | 3.2.0 / 21 |
| Frontend | React / TypeScript / Vite | 18 / 5.3 / 5.0 |
| UI | Material-UI (MUI) | 5.14 |
| Editor | Monaco Editor | 4.6 |
| State | Redux Toolkit + TanStack React Query | 2.0 / 5.8 |
| i18n | i18next + react-i18next | 25 / 16 |
| DB | PostgreSQL (prod) / H2 (dev) | — |
| CQL Engine | CQL Framework + HAPI FHIR | 3.29 / 7.0 |
| Templates | FreeMarker (.ftl) | — |

## 目錄結構

```
backend/src/main/java/com/cqlplatform/
  config/          — Spring 配置 (Security, CORS, Cache, Async)
  controller/      — REST API (18 controllers)
  entity/          — JPA 實體 (28 entities)
  exception/       — 自訂例外 + GlobalExceptionHandler
  model/           — DTO / Request / Response
  repository/      — Spring Data JPA
  security/        — JWT 認證、API Key
  service/
    authoring/     — CQL 產生引擎 (★ 核心)
    cds/           — CDS Hooks
    cql/           — CQL 翻譯 / 執行 / 程式庫
    ecqm/          — eCQM 邏輯
    fhir/          — FHIR 伺服器互動
    measure/       — 品質量測
  util/            — 工具類
  validation/      — 輸入驗證

frontend/src/
  api/             — Axios API 模組 (12 modules)
  components/
    auth/          — 登入 / 密碼重設
    authoring/     — CDS Authoring 視覺化
    builder/       — CQL Builder 元件 (★ 核心)
    cds/           — CDS Hooks UI
    common/        — 共用元件
    ecqm/          — eCQM 建構器
    editor/        — Monaco CQL 編輯器
    execution/     — CQL 執行面板
    fhir/          — FHIR 瀏覽器
    measure/       — 品質量測
    terminology/   — 術語瀏覽器
  contexts/        — React Context (6 providers)
  hooks/           — 自訂 Hooks (10+)
  locales/{en,zh-TW}/ — i18n JSON (11 namespaces)
  pages/           — 路由頁面 (14 routes, lazy-loaded)
  store/           — Redux slices (editor, execution, auth, artifact)
  utils/           — 共用工具 (13 modules)

backend/src/main/resources/
  templates/cql/   — FreeMarker 模板 (30 files)
    artifact.ftl, ecqm-artifact.ftl    — 主模板
    modifiers/     — 19 modifier templates
    elements/      — 3 element templates
    fragments/     — cds-card, error-statement
  db/migration/    — Flyway SQL (V1~V19)
  application.yml  — 主配置
```

## 開發指令

```bash
# Backend
/c/Users/alumi/apache-maven-3.9.12/bin/mvn -f backend/pom.xml test     # 執行測試
/c/Users/alumi/apache-maven-3.9.12/bin/mvn -f backend/pom.xml compile  # 編譯

# Frontend
cd frontend && npm run dev          # 開發伺服器 (port 5173)
cd frontend && npm test             # Vitest 測試
cd frontend && npm run build        # 產出建置
cd frontend && npx tsc --noEmit     # 型別檢查
```

## 開發慣例

### Commit 與 Changelog
- Commit 格式: `feat|fix|docs|refactor: 描述 (#NNN)`
- 每次 commit 後更新 `docs/CHANGE_LOG.md`（表格格式，繁體中文）
- Changelog 更新完後追加一個 `docs: add commit hash to CHANGE_LOG #NNN` commit
- ID 格式: `PAT-###`（功能/修補）、`BUG-###`（修復）

### i18n（必須遵守）
- 所有 UI 文字使用 `useTranslation('namespace')`，**禁止硬編碼字串**
- 新增/修改文字時，**必須同時更新** `locales/en/*.json` 和 `locales/zh-TW/*.json`
- 11 個 namespace: common, validation, editor, builder, measures, cds, fhir, terminology, authoring, admin, ecqm

### Backend 模式
- Controller → Service → Repository 分層架構
- Service 層**禁止使用** HTTP 概念 (`HttpServletRequest`, `@ResponseStatus`)
- 使用 `@RequiredArgsConstructor` + `final` 欄位做依賴注入，不用 `@Autowired`
- 多步驟變更必須加 `@Transactional`
- 拋出領域例外（`ResourceNotFoundException`, `ValidationException` 等），GlobalExceptionHandler 統一處理
- CQL 產生：`CqlArtifactBuilder` 組裝 context Map → 呼叫 FreeMarker 模板

### Frontend 模式
- 純函數式元件 + Hooks（無 class components）
- MUI + `sx` prop 做樣式，遵循專案 theme
- API 呼叫用 `src/api/` 模組 + TanStack React Query hooks
- Redux 用於全局狀態（editor content, auth token）
- Context 用於功能性狀態（preferences, notifications, terminology drawer）
- 效能：善用 `useMemo` / `useCallback`，大列表用 `react-window`

### 測試
- Backend: JUnit 5 + Mockito，79 個測試檔案
- Frontend: Vitest + React Testing Library
- 型別安全: 修改 tsx 後執行 `npx tsc --noEmit` 驗證

## 關鍵檔案速查

| 用途 | 檔案 |
|------|------|
| CQL 產生引擎 | `service/authoring/CqlArtifactBuilder.java` |
| 表達式引擎 | `service/authoring/ExpressionCqlEngine.java` |
| FreeMarker 引擎 | `service/authoring/CqlTemplateEngine.java` |
| CQL 翻譯 | `service/cql/CqlTranslationService.java` |
| 例外處理 | `exception/GlobalExceptionHandler.java` |
| CQL Builder UI | `components/builder/` (28 元件) |
| CDS Authoring UI | `components/authoring/` |
| eCQM UI | `components/ecqm/` |
| Monaco CQL 語法 | `utils/cqlSyntax.ts` (48KB) |
| API 客戶端 | `api/client.ts` (Axios instance) |
| 路由 | `App.tsx` (14 routes, lazy-loaded) |
| 主配置 | `backend/src/main/resources/application.yml` |
| Flyway 遷移 | `backend/src/main/resources/db/migration/` |
| Docker | `docker/docker-compose.yml` |

## TFDA 法規文件工作流（必須遵守）

本專案受 TFDA 法規管理，開發過程中的需求、設計、風險、驗證紀錄直接用中文寫在 GitHub Issues/PRs，最後自動彙整成 IEC 62304 / ISO 14971 格式的法規文件。

### 觸發時機與對應動作

| 開發情境 | 你必須做的事 |
|---------|------------|
| 新增功能或使用者需求 | 用 `gh issue create` 建立 **[需求]** Issue（模板：`software_requirement.yml`），填寫需求描述、臨床情境、驗收條件、風險等級、安全性等級 |
| 設計技術方案 | 用 `gh issue create` 建立 **[設計]** Issue（模板：`design_specification.yml`），填寫設計方案、架構影響、安全考量，**必須在「關聯需求」填入對應的需求 Issue 編號** |
| 識別到潛在風險 | 用 `gh issue create` 建立 **[風險]** Issue（模板：`risk_analysis.yml`），填寫危害情境、嚴重度、發生機率、控制措施、殘餘風險，**必須在「關聯項目」填入對應的需求/設計 Issue 編號** |
| 完成測試驗證 | 用 `gh issue create` 建立 **[驗證]** Issue（模板：`verification_record.yml`），填寫測試目的、步驟、預期結果，**必須在「關聯需求」填入對應的需求 Issue 編號** |
| 安全性等級 B/C 的功能 | **必須同時建立**需求 + 設計 + 風險 + 驗證四個 Issue，確保追溯完整 |

### Issue 建立規則

1. **標題前綴**：`[需求]`、`[設計]`、`[風險]`、`[驗證]` — 必須使用
2. **Labels**：自動套用（`IEC62304:需求`、`IEC62304:設計`、`ISO14971:風險`、`IEC62304:驗證`）
3. **追溯連結**：設計/風險/驗證 Issue 中**必須用 `#編號` 引用對應的需求 Issue**，這是追溯矩陣自動建構的依據
4. **內容語言**：中文（直接匯出為 TFDA 文件，不需翻譯）
5. **安全性等級標籤**：需求 Issue 需額外加上 `安全性等級-A`、`安全性等級-B` 或 `安全性等級-C`

### Issue Body 格式（YAML 表單解析用）

腳本透過 `### 標題` + 換行後的內容來解析欄位，建立 Issue 時使用以下格式：

```
### 需求描述

（內容）

### 臨床情境

（內容）
```

使用 `gh issue create` 搭配 `--body` 參數時，須遵守此 `### heading\n\nvalue` 格式。

### PR 法規追溯（CI 強制檢查）

PR 描述自動載入中文模板（`.github/pull_request_template.md`），**必須填寫**：
- 變更說明、關聯 Issue、測試紀錄、風險評估、IEC 62304 / ISO 14971 追溯表

**CI 自動檢查（`.github/workflows/regulatory-check.yml`）：**
- ❌ Block：PR 描述未包含任何 `#NNN` Issue 引用
- ❌ Block：安全性等級 B/C 的需求 Issue 缺少對應的設計/風險/驗證 Issue
- ⚠️ Warn：需求 Issue 缺少安全性等級標籤
- ⚠️ Warn：法規 Issue 內容缺少 `### 標題` 格式（腳本無法解析）
- ✅ Skip：`docs:` 開頭的 PR 標題不觸發檢查

### 法規文件產生

```bash
# 產出 6 份 TFDA 法規文件（需要 GITHUB_TOKEN）
GITHUB_TOKEN=$(gh auth token) python regulatory_docs/scripts/generate_regulatory_docs.py \
    --repo Lusnaker0730/CQL --version 1.0.0

# 產出測試報告（需先執行後端測試）
python regulatory_docs/scripts/generate_test_report.py \
    --backend-reports backend/target/surefire-reports \
    --output regulatory_docs/output --version 1.0.0
```

產出文件放在 `regulatory_docs/output/`：SRS、SDS、風險報告、驗證報告、追溯矩陣、變更管制紀錄。

### 法規文件目錄

```
regulatory_docs/
├── scripts/          — generate_regulatory_docs.py, generate_test_report.py
├── templates/        — 6 個 Jinja2 中文模板（含 TFDA 表頭）
└── output/           — 產出檔案
```

## 注意事項

- FHIR resource properties 定義在 `frontend/src/utils/cqlSyntax.ts` 的 `fhirResourceProperties`
- CQL 字面值正則: `CQL_LITERAL_RE` 在 `QueryBuilder.tsx`，用於自動引號判斷
- `escapeCqlString()` 目前各自定義在 `CdsCardBuilder.tsx` 和 `RecommendationBuilder.tsx`（待統一）
- Monaco Editor 整合: `useCqlEditor` hook 管理編輯器生命週期
- 前端 dev server proxy: `/api/*` → `localhost:8080`
