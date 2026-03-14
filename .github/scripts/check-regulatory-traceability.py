#!/usr/bin/env python3
"""
TFDA Regulatory Traceability Check for Pull Requests

Enforces IEC 62304 / ISO 14971 traceability rules at PR time:
1. PR must reference at least one Issue (#NNN)
2. Referenced requirement Issues (IEC62304:需求) with 安全性等級-B/C
   must have matching design, risk, and verification Issues
3. PR traceability table must be filled (not just template placeholders)

Exit code 0 = pass, 1 = fail
"""

import json
import os
import re
import subprocess
import sys


def gh_api(endpoint: str) -> dict | list:
    """Call GitHub API via gh CLI."""
    result = subprocess.run(
        ["gh", "api", endpoint, "--paginate"],
        capture_output=True, text=True, check=True,
    )
    return json.loads(result.stdout)


def get_pr_info(repo: str, pr_number: str) -> dict:
    """Fetch PR metadata."""
    return gh_api(f"repos/{repo}/pulls/{pr_number}")


def get_issue_info(repo: str, issue_number: int) -> dict:
    """Fetch issue metadata."""
    return gh_api(f"repos/{repo}/issues/{issue_number}")


def get_issues_by_label(repo: str, label: str) -> list[dict]:
    """Fetch all issues (open + closed) with a given label."""
    issues = []
    page = 1
    while True:
        result = subprocess.run(
            ["gh", "api", f"repos/{repo}/issues",
             "-f", f"labels={label}", "-f", "state=all",
             "-f", f"per_page=100", "-f", f"page={page}"],
            capture_output=True, text=True, check=True,
        )
        batch = json.loads(result.stdout)
        if not batch:
            break
        issues.extend(batch)
        page += 1
    return issues


def extract_issue_refs(text: str) -> list[int]:
    """Extract all #NNN issue references from text."""
    if not text:
        return []
    return sorted(set(int(m) for m in re.findall(r"#(\d+)", text)))


def get_issue_labels(issue: dict) -> set[str]:
    """Get label names from an issue."""
    return {label["name"] for label in issue.get("labels", [])}


def get_safety_class(labels: set[str]) -> str | None:
    """Determine safety class from labels."""
    for cls in ("A", "B", "C"):
        if f"安全性等級-{cls}" in labels:
            return cls
    return None


def check_traceability_table(pr_body: str) -> list[str]:
    """Check if the IEC 62304 / ISO 14971 traceability table is filled."""
    warnings = []

    # Find the traceability table section
    table_match = re.search(
        r"## IEC 62304 / ISO 14971 追溯\s*\n(.*?)(?:\n## |\Z)",
        pr_body, re.DOTALL,
    )
    if not table_match:
        warnings.append("PR 描述缺少「IEC 62304 / ISO 14971 追溯」區段")
        return warnings

    table_content = table_match.group(1)

    # Check each row for actual content (not just placeholders)
    rows = {
        "軟體需求": False,
        "軟體設計": False,
        "風險分析": False,
        "驗證紀錄": False,
    }
    for line in table_content.split("\n"):
        for row_name in rows:
            if row_name in line:
                # Check if there's an actual #NNN reference (not just comment placeholder)
                refs = re.findall(r"#(\d+)", line)
                # Also check it's not inside a comment <!-- -->
                uncommented = re.sub(r"<!--.*?-->", "", line)
                actual_refs = re.findall(r"#(\d+)", uncommented)
                if actual_refs:
                    rows[row_name] = True

    return warnings


def find_related_issues(
    repo: str,
    requirement_number: int,
    label_map: dict[str, list[dict]],
) -> dict[str, list[int]]:
    """
    Find design, risk, and verification Issues that reference a requirement.
    Returns dict mapping type to list of issue numbers.
    """
    result = {"設計": [], "風險": [], "驗證": []}
    ref_pattern = f"#{requirement_number}"

    type_label_map = {
        "設計": "IEC62304:設計",
        "風險": "ISO14971:風險",
        "驗證": "IEC62304:驗證",
    }

    for type_name, label in type_label_map.items():
        for issue in label_map.get(label, []):
            body = issue.get("body", "") or ""
            if ref_pattern in body:
                result[type_name].append(issue["number"])

    return result


def main():
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    pr_number = os.environ.get("PR_NUMBER", "")

    if not repo or not pr_number:
        print("::error::Missing GITHUB_REPOSITORY or PR_NUMBER environment variable")
        sys.exit(1)

    print(f"Checking regulatory traceability for PR #{pr_number} in {repo}")
    print("=" * 60)

    errors: list[str] = []
    warnings: list[str] = []

    # 1. Fetch PR info
    pr = get_pr_info(repo, pr_number)
    pr_body = pr.get("body", "") or ""
    pr_title = pr.get("title", "")

    # Skip docs-only PRs
    if pr_title.startswith("docs:"):
        print("docs-only PR — skipping regulatory checks")
        sys.exit(0)

    # Skip dependency update PRs (Dependabot, renovate, etc.)
    dep_prefixes = ("chore(deps):", "chore(deps-dev):", "Bump ", "bump ")
    pr_labels = [l["name"] for l in pr.get("labels", [])]
    if pr_title.startswith(dep_prefixes) or "dependencies" in pr_labels:
        print("dependency update PR — skipping regulatory checks")
        sys.exit(0)

    # 2. Extract issue references from PR body
    pr_refs = extract_issue_refs(pr_body)
    if not pr_refs:
        errors.append(
            "PR 描述未包含任何 Issue 引用（#NNN）。\n"
            "  請在「關聯 Issue」區段填寫相關的需求/設計/風險/驗證 Issue 編號。"
        )

    # 3. Check traceability table
    table_warnings = check_traceability_table(pr_body)
    warnings.extend(table_warnings)

    # 4. For each referenced issue, check regulatory labels
    requirement_issues: list[tuple[int, dict, str]] = []  # (number, issue, safety_class)

    for ref_num in pr_refs:
        try:
            issue = get_issue_info(repo, ref_num)
        except subprocess.CalledProcessError:
            warnings.append(f"無法取得 Issue #{ref_num}（可能已刪除或為 PR）")
            continue

        labels = get_issue_labels(issue)

        if "IEC62304:需求" in labels:
            safety_class = get_safety_class(labels)
            if safety_class:
                requirement_issues.append((ref_num, issue, safety_class))
            else:
                warnings.append(
                    f"需求 Issue #{ref_num} 缺少安全性等級標籤（安全性等級-A/B/C）"
                )

    # 5. For B/C requirements, check full traceability chain
    if requirement_issues:
        # Pre-fetch all design/risk/verification issues for efficiency
        label_map: dict[str, list[dict]] = {}
        for label in ("IEC62304:設計", "ISO14971:風險", "IEC62304:驗證"):
            try:
                label_map[label] = get_issues_by_label(repo, label)
            except subprocess.CalledProcessError:
                label_map[label] = []

        for req_num, req_issue, safety_class in requirement_issues:
            print(f"\n需求 #{req_num}: 安全性等級 {safety_class}")

            if safety_class in ("B", "C"):
                related = find_related_issues(repo, req_num, label_map)

                missing = []
                if not related["設計"]:
                    missing.append("[設計]")
                if not related["風險"]:
                    missing.append("[風險]")
                if not related["驗證"]:
                    missing.append("[驗證]")

                if missing:
                    errors.append(
                        f"安全性等級 {safety_class} 的需求 #{req_num} 缺少以下追溯文件：\n"
                        f"  缺少：{', '.join(missing)}\n"
                        f"  IEC 62304 要求安全性等級 B/C 的需求必須有完整的設計、風險分析、驗證紀錄。\n"
                        f"  請用 Issue Template 建立對應的 Issue，並在內容中引用 #{req_num}。"
                    )
                else:
                    print(f"  ✅ 追溯完整：設計 {related['設計']}, 風險 {related['風險']}, 驗證 {related['驗證']}")

            elif safety_class == "A":
                print(f"  ✅ 安全性等級 A — 不強制要求完整追溯")

    # 6. Check if any referenced issue body has broken YAML form format
    for ref_num in pr_refs:
        try:
            issue = get_issue_info(repo, ref_num)
        except subprocess.CalledProcessError:
            continue

        labels = get_issue_labels(issue)
        body = issue.get("body", "") or ""
        regulatory_labels = {"IEC62304:需求", "IEC62304:設計", "ISO14971:風險", "IEC62304:驗證"}

        if labels & regulatory_labels:
            # Check that the issue body has at least one ### heading with content
            headings = re.findall(r"^### (.+)$", body, re.MULTILINE)
            if not headings:
                warnings.append(
                    f"Issue #{ref_num} 的內容缺少 `### 標題` 格式。\n"
                    f"  法規文件產生腳本依賴此格式解析欄位。請使用 Issue Template 建立。"
                )

    # 7. Output results
    print("\n" + "=" * 60)

    if warnings:
        print(f"\n⚠️  警告 ({len(warnings)})：")
        for i, w in enumerate(warnings, 1):
            print(f"  {i}. {w}")
            # GitHub Actions annotation
            print(f"::warning::法規追溯警告：{w.split(chr(10))[0]}")

    if errors:
        print(f"\n❌ 錯誤 ({len(errors)})：")
        for i, e in enumerate(errors, 1):
            print(f"  {i}. {e}")
            # GitHub Actions annotation
            print(f"::error::法規追溯錯誤：{e.split(chr(10))[0]}")
        print("\n此 PR 不符合 TFDA 法規追溯要求，無法合併。")
        sys.exit(1)
    else:
        print("\n✅ 法規追溯檢查通過")
        sys.exit(0)


if __name__ == "__main__":
    main()
