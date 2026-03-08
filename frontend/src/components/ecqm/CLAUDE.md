# eCQM 模組

電子臨床品質量測 (eCQM) 視覺化建構器 — 遵循 CMS 指引建立品質量測。

## 架構

```
EcqmArtifactList.tsx              — 列表頁
EcqmArtifactModal.tsx             — 新增 eCQM 對話框
EcqmArtifactWorkspace.tsx         — 主工作區（★ 核心，auto-save）
EcqmArtifactWorkspaceHeader.tsx   — Save / Publish 按鈕
EcqmSummaryTab.tsx                — 量測摘要（CMS ID、NQF、用途）
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
