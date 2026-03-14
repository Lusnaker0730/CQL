# 追溯矩陣 (Traceability Matrix)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-TM-{{ version }} |
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

本文件建立需求、設計、驗證、風險之間的雙向追溯關係，確保每項需求均已被設計、驗證並進行風險分析。

## 2. 需求 → 設計 → 驗證 → 風險 追溯表

| 需求 | 設計 | 驗證 | 風險 | 狀態 |
|------|------|------|------|------|
{% for row in matrix %}
| [#{{ row.requirement.number }}]({{ row.requirement.html_url }}) {{ row.requirement.title }} | {% for d in row.designs %}[#{{ d.number }}]({{ d.html_url }}) {% endfor %} | {% for v in row.verifications %}[#{{ v.number }}]({{ v.html_url }}) {% endfor %} | {% for r in row.risks %}[#{{ r.number }}]({{ r.html_url }}) {% endfor %} | {{ row.status }} |
{% endfor %}

## 3. 覆蓋率統計

| 項目 | 數量 | 百分比 |
|------|------|--------|
| 總需求數 | {{ matrix | length }} | 100% |
| 有設計對應 | {{ matrix | selectattr('designs') | list | length }} | {{ '%.0f' | format(matrix | selectattr('designs') | list | length / (matrix | length or 1) * 100) }}% |
| 有驗證對應 | {{ matrix | selectattr('verifications') | list | length }} | {{ '%.0f' | format(matrix | selectattr('verifications') | list | length / (matrix | length or 1) * 100) }}% |
| 有風險對應 | {{ matrix | selectattr('risks') | list | length }} | {{ '%.0f' | format(matrix | selectattr('risks') | list | length / (matrix | length or 1) * 100) }}% |

## 4. 缺口分析

{% if gaps %}
以下需求尚缺追溯對應：

{% for gap in gaps %}
- **#{{ gap.number }} {{ gap.title }}**：缺少 {{ gap.missing | join('、') }}
{% endfor %}
{% else %}
所有需求均已完成追溯對應。
{% endif %}

---

*本文件由 CQL Platform 法規文件產生器自動產生*
