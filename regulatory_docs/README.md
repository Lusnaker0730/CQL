# TFDA 法規文件自動化

本目錄包含 CQL Platform 的 TFDA（衛生福利部食品藥物管理署）法規文件自動化工具，
依據 IEC 62304（醫療器材軟體生命週期流程）及 ISO 14971（醫療器材風險管理）標準。

## 目錄結構

```
regulatory_docs/
├── scripts/
│   ├── generate_regulatory_docs.py   ← 主腳本（GitHub API → 6 份法規文件）
│   ├── generate_test_report.py       ← 測試報告產生器（Surefire XML + Vitest JSON → 中文報告）
│   └── requirements.txt              ← Python 依賴
├── templates/                         ← 6 個 Jinja2 中文模板（含 TFDA 表頭）
│   ├── srs_template.md               ← 軟體需求規格書
│   ├── sds_template.md               ← 軟體設計規格書
│   ├── risk_report_template.md       ← 風險管理報告
│   ├── verification_report_template.md ← 軟體驗證報告
│   ├── traceability_matrix_template.md ← 追溯矩陣
│   └── change_control_template.md    ← 變更管制紀錄
├── output/                            ← 產出檔案
└── README.md
```

## 工作流程

### 日常開發

1. 使用 GitHub Issue Templates 記錄需求、設計、風險、驗證（中文）
2. PR 使用中文模板記錄變更說明、測試紀錄、風險評估
3. PR review comments 作為設計審查紀錄

### 產出法規文件

```bash
# 安裝依賴
pip install -r regulatory_docs/scripts/requirements.txt

# 產出 6 份法規文件（需要 GITHUB_TOKEN）
export GITHUB_TOKEN=your_token
python regulatory_docs/scripts/generate_regulatory_docs.py \
    --repo owner/CQL \
    --version 1.0.0

# 產出測試報告（需先執行測試）
mvn -f backend/pom.xml test
python regulatory_docs/scripts/generate_test_report.py \
    --backend-reports backend/target/surefire-reports \
    --output regulatory_docs/output \
    --version 1.0.0
```

### CI/CD 自動化

- `ci.yml`：上傳 Surefire XML 與 Vitest JSON 測試結果 artifact
- `regulatory-docs.yml`：手動觸發或 release 時自動產出完整法規文件包

## 產出文件

| 文件 | 來源 | TFDA 對應 |
|------|------|-----------|
| 軟體需求規格書_SRS.md | [需求] Issues | IEC 62304 §5.2 |
| 軟體設計規格書_SDS.md | [設計] Issues | IEC 62304 §5.3 |
| 風險管理報告_RMR.md | [風險] Issues | ISO 14971 |
| 軟體驗證報告_SVR.md | [驗證] Issues + 測試結果 | IEC 62304 §5.5 |
| 追溯矩陣_TM.md | 需求↔設計↔驗證↔風險 | IEC 62304 §5.6 |
| 變更管制紀錄_CCR.md | PRs + Tags | IEC 62304 §6 |
| 軟體測試報告_STR.md/html | Surefire XML + Vitest JSON | IEC 62304 §5.5 |

## GitHub Labels

執行 `.github/setup-labels.sh` 建立 8 個法規標籤：

```bash
GITHUB_REPO=owner/CQL bash .github/setup-labels.sh
```
