# 軟體設計規格書 (Software Design Specification)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-SDS-{{ version }} |
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

本文件定義 CQL Platform 的軟體設計規格，依據 IEC 62304 醫療器材軟體生命週期流程標準。

## 2. 系統架構概述

CQL Platform 採用前後端分離架構：

- **後端**：Spring Boot 3.2 / Java 21 / PostgreSQL
- **前端**：React 18 / TypeScript / Vite / MUI 5
- **CQL 引擎**：CQL Framework + HAPI FHIR

## 3. 軟體設計項目

{% for design in designs %}
### SDS-{{ '%03d' | format(loop.index) }} {{ design.title }}

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#{{ design.number }}]({{ design.html_url }}) |
| 建立日期 | {{ design.created_at[:10] }} |
| 狀態 | {{ design.state }} |

**設計方案：**

{{ design.design_description | default('（未填寫）') }}

**架構影響：**

{{ design.architecture_impact | default('（未填寫）') }}

**關聯需求：** {{ design.related_requirements | default('（未指定）') }}

**安全考量：**

{{ design.safety_considerations | default('（未填寫）') }}

{% if design.interface_description %}
**介面描述：**

{{ design.interface_description }}
{% endif %}

---

{% endfor %}

## 4. 設計統計

| 統計項目 | 數量 |
|---------|------|
| 總設計項目 | {{ designs | length }} |
| 開放中 | {{ designs | selectattr('state', 'equalto', 'open') | list | length }} |
| 已關閉 | {{ designs | selectattr('state', 'equalto', 'closed') | list | length }} |

---

*本文件由 CQL Platform 法規文件產生器自動產生*
