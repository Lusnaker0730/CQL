# 軟體驗證報告 (Software Verification Report)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-SVR-{{ version }} |
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

本文件彙整 CQL Platform 的軟體驗證活動紀錄，依據 IEC 62304 醫療器材軟體生命週期流程標準。

## 2. 測試環境

| 項目 | 版本 |
|------|------|
| 後端框架 | Spring Boot 3.2.0 / Java 21 |
| 前端框架 | React 18 / TypeScript 5.3 / Vite 5.0 |
| 測試框架（後端）| JUnit 5 / Mockito |
| 測試框架（前端）| Vitest / React Testing Library |
| CI 環境 | GitHub Actions / Ubuntu Latest |

## 3. 驗證紀錄

{% for verify in verifications %}
### VER-{{ '%03d' | format(loop.index) }} {{ verify.title }}

| 項目 | 內容 |
|------|------|
| Issue 編號 | [#{{ verify.number }}]({{ verify.html_url }}) |
| 建立日期 | {{ verify.created_at[:10] }} |
| 狀態 | {{ verify.state }} |
| 測試結論 | {{ verify.test_result | default('待執行') }} |

**測試目的：**

{{ verify.test_purpose | default('（未填寫）') }}

**測試步驟：**

{{ verify.test_steps | default('（未填寫）') }}

**預期結果：**

{{ verify.expected_result | default('（未填寫）') }}

**實際結果：**

{{ verify.actual_result | default('（未填寫）') }}

**關聯需求：** {{ verify.related_requirements | default('（未指定）') }}

{% if verify.test_environment %}
**測試環境：** {{ verify.test_environment }}
{% endif %}

---

{% endfor %}

## 4. 自動化測試摘要

{% if test_summary %}
| 項目 | 數值 |
|------|------|
| 後端測試數 | {{ test_summary.backend_total | default('N/A') }} |
| 後端通過數 | {{ test_summary.backend_passed | default('N/A') }} |
| 後端失敗數 | {{ test_summary.backend_failed | default('N/A') }} |
| 前端測試數 | {{ test_summary.frontend_total | default('N/A') }} |
| 前端通過數 | {{ test_summary.frontend_passed | default('N/A') }} |
| 前端失敗數 | {{ test_summary.frontend_failed | default('N/A') }} |
{% else %}
*自動化測試摘要將由測試報告產生器補充*
{% endif %}

## 5. 驗證統計

| 統計項目 | 數量 |
|---------|------|
| 總驗證項目 | {{ verifications | length }} |
| 通過 | {{ verifications | selectattr('test_result', 'defined') | selectattr('test_result', 'in', ['通過 (Pass)', '有條件通過 (Conditional Pass)']) | list | length }} |
| 失敗 | {{ verifications | selectattr('test_result', 'defined') | selectattr('test_result', 'equalto', '失敗 (Fail)') | list | length }} |
| 待執行 | {{ verifications | rejectattr('test_result', 'defined') | list | length }} |

## 6. 驗證結論

<!-- 由品質管理人員填寫 -->

---

*本文件由 CQL Platform 法規文件產生器自動產生*
