# 風險管理報告 (Risk Management Report)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-RMR-{{ version }} |
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

本文件彙整 CQL Platform 的風險分析紀錄，依據 ISO 14971 醫療器材風險管理標準。

## 2. 風險評估矩陣

| | 1-可忽略 | 2-輕微 | 3-嚴重 | 4-危急 | 5-災難性 |
|---|---------|--------|--------|--------|---------|
| **5-經常** | 中 | 高 | 高 | 極高 | 極高 |
| **4-可能** | 低 | 中 | 高 | 高 | 極高 |
| **3-偶爾** | 低 | 中 | 中 | 高 | 高 |
| **2-很少** | 低 | 低 | 中 | 中 | 高 |
| **1-極不可能** | 低 | 低 | 低 | 中 | 中 |

## 3. 風險分析紀錄

{% for risk in risks %}
### RISK-{{ '%03d' | format(loop.index) }} {{ risk.title }}

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#{{ risk.number }}]({{ risk.html_url }}) |
| 建立日期 | {{ risk.created_at[:10] }} |
| 狀態 | {{ risk.state }} |
| 嚴重度 | {{ risk.severity | default('未評估') }} |
| 發生機率 | {{ risk.probability | default('未評估') }} |

**危害情境：**

{{ risk.hazard_situation | default('（未填寫）') }}

**風險控制措施：**

{{ risk.risk_control | default('（未填寫）') }}

**殘餘風險：**

{{ risk.residual_risk | default('（未填寫）') }}

{% if risk.related_issues %}
**關聯項目：** {{ risk.related_issues }}
{% endif %}

---

{% endfor %}

## 4. 風險統計

| 統計項目 | 數量 |
|---------|------|
| 總風險項目 | {{ risks | length }} |
| 開放中 | {{ risks | selectattr('state', 'equalto', 'open') | list | length }} |
| 已關閉 | {{ risks | selectattr('state', 'equalto', 'closed') | list | length }} |

## 5. 整體殘餘風險評估

<!-- 由品質管理人員填寫 -->

---

*本文件由 CQL Platform 法規文件產生器自動產生*
