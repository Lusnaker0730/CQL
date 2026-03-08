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

## 注意事項

- FHIR resource properties 定義在 `frontend/src/utils/cqlSyntax.ts` 的 `fhirResourceProperties`
- CQL 字面值正則: `CQL_LITERAL_RE` 在 `QueryBuilder.tsx`，用於自動引號判斷
- `escapeCqlString()` 目前各自定義在 `CdsCardBuilder.tsx` 和 `RecommendationBuilder.tsx`（待統一）
- Monaco Editor 整合: `useCqlEditor` hook 管理編輯器生命週期
- 前端 dev server proxy: `/api/*` → `localhost:8080`
