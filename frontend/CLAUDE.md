# Frontend — React 18 / TypeScript / Vite / MUI

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
- **新增/修改文字時必須同步** `locales/en/*.json` 和 `locales/zh-TW/*.json`
- **效能**：大量計算用 `useMemo`，callback 用 `useCallback`，大列表用 `react-window`

## 狀態管理

| 層 | 工具 | 用途 |
|----|------|------|
| 全局 | Redux Toolkit | editor content, auth token, artifact JSON tree |
| 伺服器 | TanStack React Query | API 資料快取 + mutation |
| 功能 | React Context | preferences, notifications, terminology drawer |
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
- `src/api/xxxApi.ts` — 按領域分模組（17 個模組）
- 基底 URL: `VITE_API_URL || '/api'`
- Dev proxy: `/api/*` → `localhost:8080`

## i18n Namespaces（11 個）

| Namespace | 對應模組 |
|-----------|----------|
| `common` | 全域共用 |
| `builder` | CQL Builder 元件 |
| `authoring` | CDS Authoring |
| `ecqm` | eCQM 建構器 |
| `editor` | Monaco 編輯器 |
| `measures` | 品質量測 |
| `cds` | CDS Hooks |
| `fhir` | FHIR 瀏覽器 |
| `terminology` | 術語瀏覽 |
| `admin` | 管理後台 |
| `validation` | 驗證訊息 |

## 路由

所有頁面 lazy-loaded + Suspense + ErrorBoundary：
- `/` — EditorPage（CQL 編輯器）
- `/authoring` — AuthoringPage（CDS 撰寫）
- `/ecqm` — EcqmPage（eCQM）
- `/cds` — CdsPage
- `/measures` — MeasuresPage
- `/fhir` — FhirPage
- `/terminology` — TerminologyPage
- `/admin/users`, `/admin/audit` — 管理頁面

## 自訂 Hooks（`src/hooks/`，31 files）

核心 hooks：
- `useCql` — 翻譯 / 驗證 CQL
- `useCqlStructure` — 解析 CQL 結構（定義、參數、值集）
- `useAuthoring` — CDS artifact CRUD
- `useEcqm` — eCQM artifact 操作
- `useArtifactCql` — 產生 / 匯出 CQL
- `useModifiers` — 修飾器列表查詢
- `useCopyToClipboard` — 剪貼簿工具
- `usePreferences` — 使用者偏好設定

## 常用指令

```bash
npm run dev           # Vite dev server (port 5173)
npm test              # Vitest 單次執行
npm run test:watch    # Vitest watch mode
npm run build         # tsc + Vite production build
npx tsc --noEmit      # 型別檢查（修改 tsx 後必跑）
npm run lint          # ESLint
```

## 關鍵工具模組（`src/utils/`）

- `cqlSyntax.ts` — Monaco CQL 語言定義 + `fhirResourceProperties`（48KB）
- `cqlNames.ts` — CQL 名稱解析（`extractCqlName`）
- `modifierUtils.ts` — 修飾器型別判斷
- `conjunctionTreeUtils.ts` — 邏輯樹操作
- `validation.ts` — 表單驗證規則
