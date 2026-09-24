# eCQM 模組

電子臨床品質量測 (eCQM) 視覺化建構器 — 遵循 CMS 指引建立品質量測。

## 架構

```
EcqmArtifactList.tsx              — 列表頁
EcqmArtifactModal.tsx             — 新增 eCQM 對話框
EcqmArtifactWorkspace.tsx         — 主工作區（★ 核心，auto-save）
EcqmArtifactWorkspaceHeader.tsx   — Save / Publish 按鈕
EcqmSummaryTab.tsx                — 量測摘要（CMS ID、NQF、用途；PAT-236 標準 metadata 段落共用 `measure/MeasureStandardMetadataFields`）
EcqmPopulationGroupsTab.tsx       — 母群體群組管理
EcqmPopulationGroupEditor.tsx     — 單一群組編輯
EcqmPopulationTreeEditor.tsx      — 表達式樹建構（★ 複用 ConjunctionGroup）
EcqmObservationEditor.tsx         — 觀察值設定
EcqmSdeTab.tsx                    — 補充資料元素 (SDE)
EcqmStratifiersTab.tsx            — 分層規則
EcqmCqlPreviewTab.tsx             — CQL 產生 / 驗證 / 發佈
EcqmExternalCql.tsx               — 外部 CQL 程式庫
```

## 與 CDS Authoring 的共用

eCQM 模組**複用** CDS Authoring 的以下元件：
- `ConjunctionGroup` — AND/OR 表達式樹
- `BaseElements` — 基礎元素面板
- `Parameters` — 參數管理
- Field 元件（ValueSetField, NumberField 等）

## 計分類型 (Scoring)

| 類型 | 母群體結構 |
|------|-----------|
| Proportion | IP → Denominator → Numerator (± Exclusion/Exception) |
| Ratio | IP₁ → Denom, IP₂ → Numer (可用雙 IP) |
| Continuous Variable | IP → Measure Population → Observation |
| Cohort | IP only |

## 核心規則

1. **Ratio 雙 IP** — Ratio 量測可啟用 `useSeparateIPs`，此時 Denominator 和 Numerator 各有獨立 Initial Population
2. **Ratio + 雙 IP → 停用分層** — CMS 規則，啟用雙 IP 時 Stratifiers 不可用
3. **多群組名稱** — 多群組量測自動加後綴（" 1", " 2"）避免 CQL 命名衝突
4. **Observation 聚合方法** — Continuous Variable 必須選擇聚合方法（Count, Sum, Average, etc.）
5. **分層兩種（PAT-233）** — `kind: 'criteria'`（預設，布林條件樹 → `true` / `false` 兩層）或 `kind: 'value'`（運算式的值就是分層，每個不同的值一層）。值型只有結構化來源 `gender`（`Patient.gender.value`）與 `ageBands`（測量期間結束時足歲、上下界含、標籤限 ASCII，`utils/ageBands.ts` 與後端同一套檢查）——**不要**加自由 CQL 文字欄位，artifact JSON 是 client 送來的，那是 CQL injection 面。此分頁編輯的是 artifact 層級 `stratifiers`，publish 時會套到每個 group（以前只映 group 層級，UI 又不編那層，做了等於沒做）
7. **多元件分層（PAT-235）** — 分層第三種模式：`components[]`（每個元件有 `code` + 自己的條件式 / 值型編輯器，共用 `ValueSourceEditor`），病人的分層是各元件值的組合。`code` 會成為 define 名的一部分（`Stratifier <id> <code>`），`utils/stratifierComponents.ts` 與後端同一套檢查（1–50 字英數 / 空格 / `_.-`、不重複、2–10 個）；切回單一模式會清掉 `components`。指標編輯頁對多元件分層只顯示、不編輯
6. **SDE 的用途與類型（PAT-234）** — 自訂 SDE 列多 `usage`（`supplemental-data` 預設 / `risk-adjustment-factor`）與 `kind`（`criteria` / `value`，值型共用 `ValueSourceEditor`）。切成風險校正因子時，還是預設名稱的列會自動改成 `RAF …`，作者自己取的名字不動、只提示（後端 publish 警告）。publish 依 `usage` 映進 `MeasureDefinition.supplementalData` / `riskAdjustments`——以前一個都沒映，所以評估與交換封裝都不知道指標有 SDE

8. **標準 metadata（PAT-236）** — 摘要分頁最後一段「標準 Metadata（FHIR Measure）」：指標類型（複選）、實驗性、生效 / 核准 / 審閱日期、臨床建議聲明、名詞定義，與指標編輯頁共用 `components/measure/MeasureStandardMetadataFields`（`measures` namespace）。artifact 的 PUT 是部分更新，清掉的日期要送 `''` 才會清（`utils/measureMetadata.clearedDatesAsEmpty` 把元件送出的 `null` 換成 `''`）；publish 時只有 artifact 有值的欄位才覆蓋指標

## 狀態管理

```
Server artifact ←→ localOverrides (optimistic) ←→ UI
                    ↓
              auto-save timer (pendingRef + timerRef)
                    ↓
              useMutation → PUT /api/ecqm/artifacts/:id
```

- `SaveStatus`: `idle | dirty | saving | saved | error`
- 編輯時先更新 `localOverrides`，自動定時同步到伺服器
- `useUnsavedChangesGuard` hook 防止離開未存檔頁面

## CQL 產生路徑

```
EcqmPopulationTreeEditor (前端 JSON 樹)
  → PUT /api/ecqm/artifacts/:id (儲存)
  → POST /api/ecqm/artifacts/:id/generate-cql
  → EcqmCqlBuilder.buildEcqmCql() (後端)
  → ecqm-artifact.ftl (FreeMarker)
  → CQL 字串
```

## i18n

Namespace: `ecqm`

Key 前綴：
- `list.*`, `modal.*` — 列表/建立
- `workspace.*` — 分頁名稱 + 存檔狀態
- `header.*` — Save / Publish
- `summary.*` — 量測摘要欄位
- `populationGroups.*` — 群組管理
- `observation.*` — 觀察值設定
- `scoring.*` — 計分類型標籤
- `populationLabels.*` — CMS 母群體名稱
- `sde.*`, `stratifiers.*` — SDE / 分層
- `cqlPreview.*` — CQL 預覽面板
