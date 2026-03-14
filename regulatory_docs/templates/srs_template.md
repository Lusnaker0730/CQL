# 軟體需求規格書 (Software Requirements Specification)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-SRS-{{ version }} |
| 版本 | {{ version }} |
| 日期 | {{ date }} |
| 產品名稱 | CQL Platform — 臨床品質語言視覺化編輯與執行平台 |
| 審核者 | _________________ |
| 核准者 | _________________ |

## 修訂歷史

| 版本 | 日期 | 變更說明 | 作者 |
|------|------|---------|------|
| {{ version }} | {{ date }} | 自動產生 | 系統 |

---

## 1. 目的

本文件定義 CQL Platform 的軟體需求規格，依據 IEC 62304 醫療器材軟體生命週期流程標準。

## 2. 範圍

本文件涵蓋所有標記為 `IEC62304:需求` 的 GitHub Issues，版本 {{ version }}。

## 3. 軟體需求列表

{% for req in requirements %}
### SRS-{{ '%03d' | format(loop.index) }} {{ req.title }}

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#{{ req.number }}]({{ req.html_url }}) |
| 建立日期 | {{ req.created_at[:10] }} |
| 狀態 | {{ req.state }} |
| 風險等級 | {{ req.risk_level | default('未指定') }} |
| 安全性等級 | {{ req.safety_class | default('未指定') }} |

**需求描述：**

{{ req.requirement_description | default('（未填寫）') }}

**臨床情境：**

{{ req.clinical_context | default('（未填寫）') }}

**驗收條件：**

{{ req.acceptance_criteria | default('（未填寫）') }}

{% if req.related_issues %}
**關聯項目：** {{ req.related_issues }}
{% endif %}

---

{% endfor %}

## 4. 需求統計

| 統計項目 | 數量 |
|---------|------|
| 總需求數 | {{ requirements | length }} |
| 開放中 | {{ requirements | selectattr('state', 'equalto', 'open') | list | length }} |
| 已關閉 | {{ requirements | selectattr('state', 'equalto', 'closed') | list | length }} |

---

*本文件由 CQL Platform 法規文件產生器自動產生*
