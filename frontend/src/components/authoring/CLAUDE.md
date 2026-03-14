# CDS Authoring 模組

視覺化 CDS 規則撰寫工具 — 透過結構化 JSON 樹建立臨床決策規則，後端轉為 CQL。

## 架構

```
ArtifactList.tsx              — 列表頁（搜尋、篩選、建立）
ArtifactModal.tsx             — 新增 artifact 對話框
ArtifactWorkspace.tsx         — 主工作區（★ 核心，11 分頁，35KB）
ArtifactWorkspaceHeader.tsx   — 儲存、部署、CQL 預覽按鈕

子目錄：
  builder/                    — 表達式樹 UI（★ 核心）
    ConjunctionGroup.tsx      — 遞迴 AND/OR 群組
    ArtifactElement.tsx       — 單一條件元素
    ArtifactElementBody.tsx   — 元素內容（修飾器、值集）
    ModifierCard.tsx          — 修飾器卡片
    ExpressionPhrase.tsx      — 自然語言表達式顯示
    ConjunctionTypeSelect.tsx — AND/OR 切換
  base-elements/              — 可複用邏輯區塊
  cql-preview/                — CQL 產生 + 驗證面板
  element-select/             — 元素選擇器 UI
  error-statement/            — 錯誤條件處理
  external-cql/               — 外部 CQL 程式庫上傳
  fields/                     — 表單欄位元件
    NumberField, StringField, TextAreaField,
    UcumUnitField, ValueSetField, ChooseCodeDialog
  parameters/                 — 參數管理
  query-builder/              — 視覺化 FHIR 查詢建構
  recommendations/            — CDS 建議編輯器
  subpopulations/             — 子群體管理
  summary/                    — 摘要總覽
  testing/                    — FHIR 測試面板
  import/                     — CQL 匯入對話框
```

## 資料流

```
使用者操作 UI → Redux artifact slice (JSON tree) 更新
  → 儲存到後端 POST /api/authoring/artifacts
  → 後端 CqlGenerationService → CqlArtifactBuilder → FreeMarker → CQL
```

## 核心規則（必須遵守）

1. **禁止直接操作 CQL 字串** — 只操作 Redux 中的 JSON 結構樹
2. **表單 state 是局部的** — 編輯中的值存在 `useState`，確認後才更新 Redux
3. **CQL 三值邏輯** — `true / false / null`，設計 UI 時要考慮 null 情況
4. **元素名稱同步** — 改名時需呼叫 `syncReferenceNames()` 同步所有引用

## 表達式樹結構

```
ConjunctionGroup (AND/OR)
  ├── ArtifactElement (條件 1)
  │   ├── element type + return type
  │   └── modifiers[] (修飾器鏈)
  ├── ArtifactElement (條件 2)
  └── ConjunctionGroup (巢狀 AND/OR)
      └── ...
```

**treeName 對照**：
- `expTreeInclude` — 納入條件
- `expTreeExclude` — 排除條件
- `subpopulations[n].special_subpopulationLogic` — 子群體

## i18n

Namespace: `authoring`

Key 前綴：
- `page.*`, `list.*`, `modal.*` — 列表/建立
- `workspace.*` — 工作區分頁
- `header.*` — 頂部按鈕
- `conjunction.*` — AND/OR UI
- `element.*`, `elementBody.*` — 元素操作
- `modifier.*` — 修飾器表單
- `recommendations.*` — 建議編輯器
- `testing.*` — 測試面板
- `cqlPreview.*` — CQL 預覽

## ArtifactWorkspace 11 分頁

1. Summary（摘要）
2. Inclusions（納入條件）— ConjunctionGroup
3. Exclusions（排除條件）— ConjunctionGroup
4. Subpopulations（子群體）
5. Base Elements（基礎元素）
6. Recommendations（建議）
7. Parameters（參數）
8. Error Handling（錯誤處理）
9. External CQL（外部程式庫）
10. Review CQL（CQL 預覽）
11. Testing（測試）
