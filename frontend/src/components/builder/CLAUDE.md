# CQL Builder 元件

視覺化 CQL 建構器 — 讓使用者透過 UI 組裝 CQL 程式碼片段，插入 Monaco 編輯器。

## 架構

```
CqlBuilderPanel.tsx          — 主面板（手風琴 UI，整合所有 section）
  ├── IncludesSection.tsx     — library include 管理
  ├── ValueSetSection.tsx     — 值集定義
  ├── CodesSection.tsx        — 代碼定義（手動/VSAC/TWCORE）
  ├── ConceptsSection.tsx     — 概念定義
  ├── ParametersSection.tsx   — 參數定義
  ├── DefinitionsSection.tsx  — 定義區（切換 builder 模式）
  │   ├── RetrieveBuilder     — FHIR Retrieve 建構
  │   ├── QueryBuilder        — CQL Query 建構（where/with/let/sort/return）
  │   ├── ExpressionBuilder   — 表達式建構
  │   ├── CdsCardBuilder      — CDS Card Tuple
  │   ├── RecommendationBuilder — CDS 建議卡片
  │   ├── ConditionalBuilder  — if/else, case/when
  │   ├── ElementRefBuilder   — 元素引用
  │   └── OperatorPanel       — 運算子面板
  ├── FunctionsSection.tsx    — 函式定義
  ├── BaseElementsPanel.tsx   — 釘選基礎元素
  ├── ValidationPanel.tsx     — CQL 驗證結果
  └── ExpressionTreeView.tsx  — 依賴圖視覺化
```

## Builder 元件共通模式

每個 Builder 元件都遵循：

```tsx
interface XxxBuilderProps {
  valueSets: string[]           // 可用值集清單
  codes: string[]               // 可用代碼清單
  onInsert: (cql: string) => void  // 插入 CQL 到編輯器
  onCancel: () => void
}

export default function XxxBuilder({ valueSets, codes, onInsert, onCancel }: XxxBuilderProps) {
  // 1. 局部表單 state（不直接影響 Redux）
  // 2. useMemo 衍生選項（terminologyOptions, fieldOptions）
  // 3. generateCql() 純函數產生 CQL 字串
  // 4. CqlPreviewBox 即時預覽
  // 5. GradientButton 插入 + useCopyToClipboard 複製
}
```

### 資料流
```
使用者操作 UI → 局部 state 更新 → generateXxxCql() 即時產生 CQL
  → CqlPreviewBox 預覽 → 使用者點「插入」
  → onInsert(cql) → CqlBuilderPanel.handleInsertWithCheck()
  → 重複名稱檢查 → onInsertSnippet(cql) → Monaco 編輯器插入
```

## 共用元件

| 元件 | 用途 |
|------|------|
| `CqlPreviewBox` | Monaco colorize 語法高亮預覽（唯讀） |
| `SnippetPreview` | 簡短程式碼片段預覽 |
| `TypeChip` | 型別標籤（System.String, List<T>, etc.） |
| `ElementListItem` | 定義清單項目（名稱 + 型別 + 動作按鈕） |
| `ElementSelectField` | 選擇既有定義/參數/函式的下拉欄位 |
| `ModifierChainBuilder` | 修飾器鏈組合（值比較、布林、字串、回溯、單位轉換） |
| `TwcoreBrowser` | TWCORE 術語目錄瀏覽器 |

## i18n

Namespace: `builder`，翻譯檔：
- `locales/en/builder.json`
- `locales/zh-TW/builder.json`

Key 前綴對照：
- `query.*` — QueryBuilder
- `retrieve.*` — RetrieveBuilder
- `expression.*` — ExpressionBuilder
- `modifierChain.*` — ModifierChainBuilder
- `cdsCard.*` — CdsCardBuilder
- `recommendation.*` — RecommendationBuilder
- `conditional.*` — ConditionalBuilder
- `elementRef.*` — ElementRefBuilder
- `common.*` — 共用按鈕（insert, cancel, copy）

## 重要工具

- `fhirResourceProperties`（from `utils/cqlSyntax.ts`）— FHIR 資源屬性對照表
- `FHIR_RESOURCE_TYPES`（from `constants/fhirResources`）— 資源類型清單
- `extractCqlName`（from `utils/cqlNames`）— 解析 CQL 引號名稱
- `CQL_LITERAL_RE`（QueryBuilder 內）— CQL 字面值正則判斷

## 注意事項

- `escapeCqlString()` 在 CdsCardBuilder 和 RecommendationBuilder 各有一份（待統一）
- QueryBuilder 的 `generateQueryCql` 是純函數，CQL 子句順序：`let → with/without → where → return → sort`
- ModifierChainBuilder 支援算術運算（+−×÷）和巢狀修飾器
- 新增 Builder 時：建立元件 → 加到 DefinitionsSection 的模式切換 → 加 i18n keys (en + zh-TW)
