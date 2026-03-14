#!/usr/bin/env python3
"""
CQL Platform — 測試報告產生器

解析 Surefire XML (JUnit) 與 Vitest JSON 測試結果，
產出中文 Markdown + HTML 測試報告，符合 TFDA 格式要求。

使用方式:
    python generate_test_report.py \
        --backend-reports backend/target/surefire-reports \
        --output regulatory_docs/output

    python generate_test_report.py \
        --backend-reports backend/target/surefire-reports \
        --frontend-report frontend/test-results.json \
        --output regulatory_docs/output \
        --version 1.0.0
"""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
from datetime import datetime
from pathlib import Path

from jinja2 import Environment, BaseLoader

# ---------------------------------------------------------------------------
# Surefire XML parser
# ---------------------------------------------------------------------------


def parse_surefire_reports(reports_dir):
    """Parse all TEST-*.xml files in a Surefire reports directory."""
    reports_path = Path(reports_dir)
    if not reports_path.is_dir():
        print(f"警告：Surefire 報告目錄不存在: {reports_dir}", file=sys.stderr)
        return []

    suites = []
    for xml_file in sorted(reports_path.glob("TEST-*.xml")):
        try:
            tree = ET.parse(xml_file)
            root = tree.getroot()

            suite = {
                "name": root.attrib.get("name", xml_file.stem),
                "tests": int(root.attrib.get("tests", 0)),
                "failures": int(root.attrib.get("failures", 0)),
                "errors": int(root.attrib.get("errors", 0)),
                "skipped": int(root.attrib.get("skipped", 0)),
                "time": float(root.attrib.get("time", 0)),
                "test_cases": [],
            }

            for tc in root.findall("testcase"):
                case = {
                    "name": tc.attrib.get("name", ""),
                    "classname": tc.attrib.get("classname", ""),
                    "time": float(tc.attrib.get("time", 0)),
                    "status": "通過",
                    "message": "",
                }

                failure = tc.find("failure")
                error = tc.find("error")
                skipped_el = tc.find("skipped")

                if failure is not None:
                    case["status"] = "失敗"
                    case["message"] = failure.attrib.get("message", "")
                elif error is not None:
                    case["status"] = "錯誤"
                    case["message"] = error.attrib.get("message", "")
                elif skipped_el is not None:
                    case["status"] = "略過"
                    case["message"] = skipped_el.attrib.get("message", "")

                suite["test_cases"].append(case)

            suite["passed"] = suite["tests"] - suite["failures"] - suite["errors"] - suite["skipped"]
            suites.append(suite)
        except ET.ParseError as e:
            print(f"警告：無法解析 {xml_file}: {e}", file=sys.stderr)

    return suites


# ---------------------------------------------------------------------------
# Vitest JSON parser
# ---------------------------------------------------------------------------


def parse_vitest_report(report_path):
    """Parse a Vitest JSON reporter output file."""
    path = Path(report_path)
    if not path.is_file():
        print(f"警告：Vitest 報告檔案不存在: {report_path}", file=sys.stderr)
        return []

    with open(path, encoding="utf-8") as f:
        data = json.load(f)

    suites = []
    for test_file in data.get("testResults", []):
        suite = {
            "name": test_file.get("name", ""),
            "tests": 0,
            "passed": 0,
            "failures": 0,
            "errors": 0,
            "skipped": 0,
            "time": test_file.get("endTime", 0) - test_file.get("startTime", 0),
            "test_cases": [],
        }

        # Vitest nests results in assertionResults
        for result in test_file.get("assertionResults", []):
            status_map = {
                "passed": "通過",
                "failed": "失敗",
                "pending": "略過",
                "skipped": "略過",
            }
            status = status_map.get(result.get("status", ""), "未知")

            case = {
                "name": " > ".join(result.get("ancestorTitles", []) + [result.get("title", "")]),
                "classname": test_file.get("name", ""),
                "time": result.get("duration", 0) / 1000 if result.get("duration") else 0,
                "status": status,
                "message": "\n".join(result.get("failureMessages", [])),
            }
            suite["test_cases"].append(case)
            suite["tests"] += 1
            if status == "通過":
                suite["passed"] += 1
            elif status == "失敗":
                suite["failures"] += 1
            elif status == "略過":
                suite["skipped"] += 1

        suites.append(suite)

    return suites


# ---------------------------------------------------------------------------
# Report generation
# ---------------------------------------------------------------------------

REPORT_TEMPLATE_MD = """\
# 軟體測試報告 (Software Test Report)

| 項目 | 內容 |
|------|------|
| 文件編號 | CQL-STR-{{ version }} |
| 版本 | {{ version }} |
| 日期 | {{ date }} |
| 產品名稱 | CQL Platform — 臨床品質語言視覺化編輯與執行平台 |
| 測試環境 | {{ test_env }} |
| 執行者 | {{ executor }} |
| 審核者 | _________________ |
| 核准者 | _________________ |

## 修訂歷史

| 版本 | 日期 | 變更說明 | 作者 |
|------|------|---------|------|
| {{ version }} | {{ date }} | 自動產生 | 系統 |

---

## 1. 測試摘要

| 項目 | 後端 (JUnit) | 前端 (Vitest) | 合計 |
|------|-------------|--------------|------|
| 測試套件數 | {{ backend_suites | length }} | {{ frontend_suites | length }} | {{ backend_suites | length + frontend_suites | length }} |
| 測試案例數 | {{ backend_total }} | {{ frontend_total }} | {{ backend_total + frontend_total }} |
| 通過 | {{ backend_passed }} | {{ frontend_passed }} | {{ backend_passed + frontend_passed }} |
| 失敗 | {{ backend_failed }} | {{ frontend_failed }} | {{ backend_failed + frontend_failed }} |
| 錯誤 | {{ backend_errors }} | {{ frontend_errors }} | {{ backend_errors + frontend_errors }} |
| 略過 | {{ backend_skipped }} | {{ frontend_skipped }} | {{ backend_skipped + frontend_skipped }} |
| 總執行時間 | {{ '%.2f' | format(backend_time) }}s | {{ '%.2f' | format(frontend_time) }}s | {{ '%.2f' | format(backend_time + frontend_time) }}s |

**通過率：{{ '%.1f' | format(pass_rate) }}%**

---

## 2. 後端測試結果 (JUnit / Surefire)

{% for suite in backend_suites %}
### {{ suite.name }}

| 項目 | 數值 |
|------|------|
| 測試數 | {{ suite.tests }} |
| 通過 | {{ suite.passed }} |
| 失敗 | {{ suite.failures }} |
| 錯誤 | {{ suite.errors }} |
| 略過 | {{ suite.skipped }} |
| 執行時間 | {{ '%.3f' | format(suite.time) }}s |

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
{% for tc in suite.test_cases -%}
| {{ tc.name }} | {{ tc.status }} | {{ '%.3f' | format(tc.time) }}s |
{% endfor %}
{% if suite.test_cases | selectattr('status', 'equalto', '失敗') | list %}
**失敗詳情：**
{% for tc in suite.test_cases if tc.status == '失敗' %}
- `{{ tc.name }}`: {{ tc.message }}
{% endfor %}
{% endif %}

{% endfor %}

---

## 3. 前端測試結果 (Vitest)

{% for suite in frontend_suites %}
### {{ suite.name }}

| 測試案例 | 狀態 | 時間 |
|---------|------|------|
{% for tc in suite.test_cases -%}
| {{ tc.name }} | {{ tc.status }} | {{ '%.3f' | format(tc.time) }}s |
{% endfor %}
{% if suite.test_cases | selectattr('status', 'equalto', '失敗') | list %}
**失敗詳情：**
{% for tc in suite.test_cases if tc.status == '失敗' %}
- `{{ tc.name }}`: {{ tc.message[:200] }}
{% endfor %}
{% endif %}

{% endfor %}

---

## 4. 測試結論

{% if pass_rate == 100.0 -%}
所有測試案例均通過，軟體品質符合預期。
{% elif pass_rate >= 95.0 -%}
大部分測試案例通過（通過率 {{ '%.1f' | format(pass_rate) }}%），請審查失敗案例。
{% else -%}
通過率低於 95%（{{ '%.1f' | format(pass_rate) }}%），需進一步調查失敗原因。
{% endif %}

<!-- 由品質管理人員填寫最終結論 -->

---

*本文件由 CQL Platform 測試報告產生器自動產生*
"""

REPORT_TEMPLATE_HTML = """\
<!DOCTYPE html>
<html lang="zh-TW">
<head>
<meta charset="UTF-8">
<title>軟體測試報告 - CQL Platform {{ version }}</title>
<style>
  body { font-family: "Microsoft JhengHei", "Noto Sans TC", sans-serif; margin: 40px; color: #333; }
  h1 { color: #0D7377; border-bottom: 2px solid #0D7377; padding-bottom: 8px; }
  h2 { color: #1B3A5C; margin-top: 32px; }
  h3 { color: #555; }
  table { border-collapse: collapse; width: 100%; margin: 16px 0; }
  th, td { border: 1px solid #ddd; padding: 8px 12px; text-align: left; }
  th { background-color: #f5f5f5; font-weight: bold; }
  .pass { color: #2e7d32; font-weight: bold; }
  .fail { color: #c62828; font-weight: bold; }
  .skip { color: #f57f17; }
  .header-table { width: auto; }
  .header-table td:first-child { font-weight: bold; width: 120px; }
  .summary { background: #e8f5e9; padding: 16px; border-radius: 8px; margin: 16px 0; font-size: 1.2em; }
  .summary.warning { background: #fff3e0; }
  .summary.error { background: #ffebee; }
  .footer { margin-top: 40px; padding-top: 16px; border-top: 1px solid #ddd; color: #888; font-size: 0.9em; }
</style>
</head>
<body>

<h1>軟體測試報告</h1>

<table class="header-table">
<tr><td>文件編號</td><td>CQL-STR-{{ version }}</td></tr>
<tr><td>版本</td><td>{{ version }}</td></tr>
<tr><td>日期</td><td>{{ date }}</td></tr>
<tr><td>產品名稱</td><td>CQL Platform — 臨床品質語言視覺化編輯與執行平台</td></tr>
<tr><td>測試環境</td><td>{{ test_env }}</td></tr>
<tr><td>執行者</td><td>{{ executor }}</td></tr>
<tr><td>審核者</td><td></td></tr>
<tr><td>核准者</td><td></td></tr>
</table>

<h2>1. 測試摘要</h2>

<div class="summary {% if pass_rate < 95 %}error{% elif pass_rate < 100 %}warning{% endif %}">
通過率：{{ '%.1f' | format(pass_rate) }}%
（{{ backend_passed + frontend_passed }} / {{ backend_total + frontend_total }}）
</div>

<table>
<tr><th>項目</th><th>後端 (JUnit)</th><th>前端 (Vitest)</th><th>合計</th></tr>
<tr><td>測試套件數</td><td>{{ backend_suites | length }}</td><td>{{ frontend_suites | length }}</td><td>{{ backend_suites | length + frontend_suites | length }}</td></tr>
<tr><td>測試案例數</td><td>{{ backend_total }}</td><td>{{ frontend_total }}</td><td>{{ backend_total + frontend_total }}</td></tr>
<tr><td>通過</td><td class="pass">{{ backend_passed }}</td><td class="pass">{{ frontend_passed }}</td><td class="pass">{{ backend_passed + frontend_passed }}</td></tr>
<tr><td>失敗</td><td class="fail">{{ backend_failed }}</td><td class="fail">{{ frontend_failed }}</td><td class="fail">{{ backend_failed + frontend_failed }}</td></tr>
<tr><td>錯誤</td><td>{{ backend_errors }}</td><td>{{ frontend_errors }}</td><td>{{ backend_errors + frontend_errors }}</td></tr>
<tr><td>略過</td><td class="skip">{{ backend_skipped }}</td><td class="skip">{{ frontend_skipped }}</td><td class="skip">{{ backend_skipped + frontend_skipped }}</td></tr>
<tr><td>執行時間</td><td>{{ '%.2f' | format(backend_time) }}s</td><td>{{ '%.2f' | format(frontend_time) }}s</td><td>{{ '%.2f' | format(backend_time + frontend_time) }}s</td></tr>
</table>

<h2>2. 後端測試結果</h2>

{% for suite in backend_suites %}
<h3>{{ suite.name }}</h3>
<table>
<tr><th>測試案例</th><th>狀態</th><th>時間</th></tr>
{% for tc in suite.test_cases -%}
<tr>
  <td>{{ tc.name }}</td>
  <td class="{% if tc.status == '通過' %}pass{% elif tc.status == '失敗' %}fail{% elif tc.status == '略過' %}skip{% endif %}">{{ tc.status }}</td>
  <td>{{ '%.3f' | format(tc.time) }}s</td>
</tr>
{% endfor %}
</table>
{% endfor %}

<h2>3. 前端測試結果</h2>

{% for suite in frontend_suites %}
<h3>{{ suite.name }}</h3>
<table>
<tr><th>測試案例</th><th>狀態</th><th>時間</th></tr>
{% for tc in suite.test_cases -%}
<tr>
  <td>{{ tc.name }}</td>
  <td class="{% if tc.status == '通過' %}pass{% elif tc.status == '失敗' %}fail{% elif tc.status == '略過' %}skip{% endif %}">{{ tc.status }}</td>
  <td>{{ '%.3f' | format(tc.time) }}s</td>
</tr>
{% endfor %}
</table>
{% endfor %}

<div class="footer">
本文件由 CQL Platform 測試報告產生器自動產生 — {{ date }}
</div>

</body>
</html>
"""


def compute_stats(suites):
    total = sum(s["tests"] for s in suites)
    passed = sum(s["passed"] for s in suites)
    failed = sum(s["failures"] for s in suites)
    errors = sum(s["errors"] for s in suites)
    skipped = sum(s["skipped"] for s in suites)
    time_total = sum(s["time"] for s in suites)
    return total, passed, failed, errors, skipped, time_total


def main():
    parser = argparse.ArgumentParser(description="CQL Platform 測試報告產生器")
    parser.add_argument("--backend-reports", default=None, help="Surefire XML 報告目錄")
    parser.add_argument("--frontend-report", default=None, help="Vitest JSON 報告檔案")
    parser.add_argument("--output", required=True, help="輸出目錄")
    parser.add_argument("--version", default="0.0.0", help="文件版本號")
    parser.add_argument("--executor", default="CI/CD 自動化", help="測試執行者")
    parser.add_argument("--test-env", default="GitHub Actions / Ubuntu Latest / Java 21 / Node 20", help="測試環境描述")
    args = parser.parse_args()

    output_dir = Path(args.output)
    output_dir.mkdir(parents=True, exist_ok=True)

    # Parse reports
    backend_suites = parse_surefire_reports(args.backend_reports) if args.backend_reports else []
    frontend_suites = parse_vitest_report(args.frontend_report) if args.frontend_report else []

    bt, bp, bf, be, bs, btime = compute_stats(backend_suites)
    ft, fp, ff, fe, fs, ftime = compute_stats(frontend_suites)

    total = bt + ft
    passed = bp + fp
    pass_rate = (passed / total * 100) if total > 0 else 0.0

    context = {
        "version": args.version,
        "date": datetime.now().strftime("%Y-%m-%d"),
        "test_env": args.test_env,
        "executor": args.executor,
        "backend_suites": backend_suites,
        "frontend_suites": frontend_suites,
        "backend_total": bt,
        "backend_passed": bp,
        "backend_failed": bf,
        "backend_errors": be,
        "backend_skipped": bs,
        "backend_time": btime,
        "frontend_total": ft,
        "frontend_passed": fp,
        "frontend_failed": ff,
        "frontend_errors": fe,
        "frontend_skipped": fs,
        "frontend_time": ftime,
        "pass_rate": pass_rate,
    }

    env = Environment(loader=BaseLoader(), keep_trailing_newline=True)

    # Generate Markdown report
    md_template = env.from_string(REPORT_TEMPLATE_MD)
    md_output = md_template.render(**context)
    md_path = output_dir / "軟體測試報告_STR.md"
    md_path.write_text(md_output, encoding="utf-8")
    print(f"已產生 Markdown 報告: {md_path}")

    # Generate HTML report
    html_template = env.from_string(REPORT_TEMPLATE_HTML)
    html_output = html_template.render(**context)
    html_path = output_dir / "軟體測試報告_STR.html"
    html_path.write_text(html_output, encoding="utf-8")
    print(f"已產生 HTML 報告: {html_path}")

    # Print summary
    print(f"\n測試摘要：")
    print(f"  後端: {bp}/{bt} 通過")
    print(f"  前端: {fp}/{ft} 通過")
    print(f"  通過率: {pass_rate:.1f}%")

    # Return non-zero if there are failures
    if bf + be + ff + fe > 0:
        print(f"\n注意：有 {bf + be + ff + fe} 個測試失敗或錯誤")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
