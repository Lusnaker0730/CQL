# 變更管制紀錄 (Change Control Record)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-CCR-{{ version }} |
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

本文件記錄 CQL Platform 的軟體變更管制紀錄，確保所有變更均經過適當審查與核准。

## 2. 版本發布紀錄

{% for tag in tags %}
### {{ tag.name }}

| 項目 | 內容 |
|------|------|
| 標籤 | {{ tag.name }} |
| 日期 | {{ tag.date }} |
| 說明 | {{ tag.message | default('—') }} |

{% endfor %}

## 3. 合併請求紀錄

| PR 編號 | 標題 | 合併日期 | 作者 | 審查者 | 關聯 Issue |
|---------|------|---------|------|--------|-----------|
{% for pr in pull_requests %}
| [#{{ pr.number }}]({{ pr.html_url }}) | {{ pr.title }} | {{ pr.merged_at[:10] if pr.merged_at else '—' }} | {{ pr.user.login }} | {{ pr.reviewers | join(', ') if pr.reviewers else '—' }} | {{ pr.related_issues | default('—') }} |
{% endfor %}

## 4. 變更統計

| 統計項目 | 數量 |
|---------|------|
| 版本發布數 | {{ tags | length }} |
| 合併 PR 數 | {{ pull_requests | length }} |
| 涉及需求變更 | {{ pull_requests | selectattr('has_requirement_change') | list | length }} |
| 涉及風險變更 | {{ pull_requests | selectattr('has_risk_change') | list | length }} |

---

*本文件由 CQL Platform 法規文件產生器自動產生*
