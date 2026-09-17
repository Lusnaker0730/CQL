# Frontend — React 19 / TypeScript 5.9 / Vite 7 / MUI 9

## 元件開發模式

```tsx
export default function MyComponent({ prop1, onAction }: MyComponentProps) {
  const { t } = useTranslation('namespace')  // 必用 i18n
  const [localState, setLocalState] = useState(initialValue)

  const derived = useMemo(() => expensiveComputation(localState), [localState])

  const handleAction = useCallback(() => {
    // ...
    onAction(result)
  }, [onAction])

  return (
    <Stack spacing={1}>
      <Typography variant="body2">{t('key')}</Typography>
      {/* MUI components with sx prop */}
    </Stack>
  )
}
```

### 必須遵守
- **純函數式元件 + Hooks**（無 class components）
- **所有 UI 文字用 i18n**：`t('namespace.key')`，禁止硬編碼
- **新增/修改文字時必須同步** `locales/en/*.json` 和 `locales/zh-TW/*.json`（CI 跑 `scripts/check-i18n-sync.py`）
- **效能**：大量計算用 `useMemo`，callback 用 `useCallback`，大列表用 `react-window`
- `npm run lint` 是 `--max-warnings 0`：一個 warning 就紅。常見：元件檔同時 export 非元件函式會觸發 react-refresh 警告，把函式搬到非元件模組
- MUI 9 / React 19：`TextField` 用 `slotProps={{ inputLabel: { shrink: true } }}`（不是 `InputLabelProps`）；`@mui/icons-material` 用 sub-path import（`@mui/icons-material/CheckCircle`）避免 vitest 收集時載整包 barrel

## 狀態管理

| 層 | 工具 | 用途 |
|----|------|------|
| 全局 | Redux Toolkit | editor content, execution, auth token, artifact JSON tree |
| 伺服器 | TanStack React Query | API 資料快取 + mutation |
| 功能 | React Context | preferences, notifications, terminology drawer, EHR outage banner, bundle builder, library history, resource type |
| 局部 | useState | 表單草稿、UI 開關 |

### React Query 慣例
```tsx
// 查詢
const { data } = useQuery({ queryKey: ['items'], queryFn: api.listItems })

// 變更
const mutation = useMutation({
  mutationFn: api.createItem,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['items'] })
})
```

## API 層

- `src/api/client.ts` — Axios instance（自動附 JWT、401 靜默刷新）
- `src/api/xxxApi.ts` — 按領域分模組（23 個模組）
- 基底 URL: `VITE_API_URL || '/api'`
- Dev proxy: `/api/*` → `localhost:8080`；`/cds-services/*` 亦代理
- 錯誤訊息用 `utils/errorUtils.extractApiError(err)`（讀 `GlobalExceptionHandler` 的 `message` / `error`）
- `src/types/index.ts` 是**手寫**型別（148 個 export），後端 DTO 改了不會自動反映；springdoc 的 `/v3/api-docs` 可用，尚未接 openapi-typescript

## i18n Namespaces（14 個）

| Namespace | 對應模組 |
|-----------|----------|
| `common` | 全域共用（含 `status.*` 生命週期標籤） |
| `builder` | CQL Builder 元件 |
| `authoring` | CDS Authoring |
| `ecqm` | eCQM 建構器 |
| `editor` | Monaco 編輯器 |
| `measures` | 品質量測 |
| `cds` | CDS Hooks |
| `fhir` | FHIR 瀏覽器 |
| `terminology` | 術語瀏覽 |
| `admin` | 管理後台（含租戶 / 診所申請） |
| `validation` | 驗證訊息 |
| `patientGenerator` | TW Core 假病人產生器 |
| `cqlLibraries` | CQL 程式庫管理 |
| `landing` | 公開頁（landing / status / docs / templates / terms / privacy / apply） |

## 路由（`App.tsx`，全部 lazy-loaded + Suspense + ErrorBoundary）

登入後：
- `/` — EditorPage（CQL 編輯器）
- `/authoring` — CDS 撰寫；`/ecqm` — eCQM；`/cds` — CDS Hooks
- `/measures` — 品質量測；`/cql-libraries` — CQL 程式庫
- `/fhir` — FHIR 瀏覽器；`/terminology` — 術語；`/patient-generator` — 假病人產生器
- `/tenant/users` — 診所 ADMIN 的租戶範圍員工管理（PAT-214）
- `/admin/users`, `/admin/tenants`, `/admin/clinic-applications`, `/admin/audit` — 平台操作員管理頁

公開：`/login`, `/learn`, `/templates`, `/status`, `/docs`, `/terms`, `/privacy`, `/apply`, `/forgot-password`, `/reset-password`, `/auth/okta/callback`

## 自訂 Hooks（`src/hooks/`，36 files）

核心 hooks：
- `useCql` — 翻譯 / 驗證 CQL
- `useCqlStructure` — 解析 CQL 結構（定義、參數、值集）
- `useAuthoring` — CDS artifact CRUD
- `useEcqm` — eCQM artifact 操作
- `useArtifactCql` — 產生 / 匯出 CQL
- `useMeasures` — 指標 CRUD + 審核流程 mutation（submit / approve / reject / retire / lock）
- `useModifiers` — 修飾器列表查詢
- `useCopyToClipboard` — 剪貼簿工具
- `usePreferences` — 使用者偏好設定
- `usePatientGenerator` — TW Core 假病人產生狀態管理

## 測試

- `src/test/test-utils.tsx` 的 `render` 包好 Redux store / QueryClient / MemoryRouter / Theme / Preferences / Notification providers
- 它**不**初始化 i18next：測試要 `vi.mock('react-i18next', ...)` 讓 `t` 回傳 key，斷言用 key 字串
- `src/test/setup.ts` 已全域 stub `monaco-editor` 與 `@monaco-editor/react`
- 大元件的子元件用 `vi.mock('../Child', () => ({ default: () => <div /> }))` 剪掉，測試聚焦在受測行為

## 常用指令

```bash
npm run dev           # Vite dev server (port 5173)
npm test              # Vitest 單次執行
npm run test:watch    # Vitest watch mode
npm run build         # tsc + Vite production build
npx tsc --noEmit      # 型別檢查（修改 tsx 後必跑）
npm run lint          # ESLint（--max-warnings 0）
python scripts/check-i18n-sync.py   # locale key 同步
```

本機 npm 若遇 `UNABLE_TO_VERIFY_LEAF_SIGNATURE`（防毒 TLS 攔截），設 `NODE_OPTIONS=--use-system-ca` 再跑。

## 關鍵工具模組（`src/utils/`）

- `cqlSyntax.ts` — Monaco CQL 語言定義 + `fhirResourceProperties`（48KB）
- `cqlNames.ts` — CQL 名稱解析（`extractCqlName`）
- `cqlString.ts` — `escapeCqlString()` / `formatFieldValue()`（唯一定義處）
- `modifierUtils.ts` — 修飾器型別判斷
- `conjunctionTreeUtils.ts` — 邏輯樹操作
- `validation.ts` — 表單驗證規則
- `errorUtils.ts` — API 錯誤訊息萃取
- `fhirPatientGenerator.ts` — TW Core IG FHIR 假病人產生引擎
- `twDemographics.ts` — 台灣人口資料產生（姓名/地址/身分證/電話）
- `random.ts` — 共用隨機工具函數
- `config/twcore/` — 假病人產生設定檔（conditions/observations/medications/allergies/scenarios JSON，新增/修改臨床項目只需改 JSON）
