#!/usr/bin/env python3
"""
CQL Platform — TFDA 法規文件產生器

從 GitHub Issues/PRs 自動彙整產出 6 份 TFDA 格式法規文件：
1. 軟體需求規格書 (SRS)
2. 軟體設計規格書 (SDS)
3. 風險管理報告 (RMR)
4. 軟體驗證報告 (SVR)
5. 追溯矩陣 (TM)
6. 變更管制紀錄 (CCR)

使用方式:
    python generate_regulatory_docs.py --repo owner/CQL --version 1.0.0
    python generate_regulatory_docs.py --repo owner/CQL --version 1.0.0 --output ./output
"""

import argparse
import os
import re
import sys
from datetime import datetime
from pathlib import Path

import requests
from jinja2 import Environment, FileSystemLoader

# ---------------------------------------------------------------------------
# GitHub API helpers
# ---------------------------------------------------------------------------

GITHUB_API = "https://api.github.com"


def github_headers():
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        print("錯誤：請設定 GITHUB_TOKEN 環境變數", file=sys.stderr)
        sys.exit(1)
    return {
        "Authorization": f"token {token}",
        "Accept": "application/vnd.github+json",
    }


def paginated_get(url, params=None):
    """Fetch all pages from a paginated GitHub API endpoint."""
    headers = github_headers()
    params = params or {}
    params.setdefault("per_page", 100)
    results = []
    while url:
        resp = requests.get(url, headers=headers, params=params, timeout=30)
        resp.raise_for_status()
        results.extend(resp.json())
        url = resp.links.get("next", {}).get("url")
        params = {}  # params already encoded in next URL
    return results


def fetch_issues_by_label(repo, label):
    """Fetch all issues (open + closed) with a given label."""
    url = f"{GITHUB_API}/repos/{repo}/issues"
    params = {"labels": label, "state": "all", "per_page": 100}
    return paginated_get(url, params)


def fetch_merged_prs(repo):
    """Fetch all merged pull requests."""
    url = f"{GITHUB_API}/repos/{repo}/pulls"
    params = {"state": "closed", "per_page": 100}
    all_prs = paginated_get(url, params)
    return [pr for pr in all_prs if pr.get("merged_at")]


def fetch_pr_reviews(repo, pr_number):
    """Fetch reviews for a specific PR."""
    url = f"{GITHUB_API}/repos/{repo}/pulls/{pr_number}/reviews"
    return paginated_get(url)


def fetch_tags(repo):
    """Fetch git tags with dates."""
    url = f"{GITHUB_API}/repos/{repo}/tags"
    tags_raw = paginated_get(url)
    headers = github_headers()
    tags = []
    for tag in tags_raw:
        commit_url = tag["commit"]["url"]
        commit_resp = requests.get(commit_url, headers=headers, timeout=30)
        commit_resp.raise_for_status()
        commit_data = commit_resp.json()
        date = commit_data.get("commit", {}).get("committer", {}).get("date", "")
        tags.append({
            "name": tag["name"],
            "date": date[:10] if date else "",
            "message": commit_data.get("commit", {}).get("message", "").split("\n")[0],
        })
    return tags


# ---------------------------------------------------------------------------
# Issue body parsing
# ---------------------------------------------------------------------------

def parse_issue_body(body):
    """Parse GitHub YAML-form issue body (### heading / value pairs)."""
    if not body:
        return {}
    fields = {}
    current_key = None
    current_lines = []
    for line in body.split("\n"):
        heading_match = re.match(r"^### (.+)$", line.strip())
        if heading_match:
            if current_key is not None:
                fields[current_key] = "\n".join(current_lines).strip()
            current_key = normalize_field_name(heading_match.group(1).strip())
            current_lines = []
        else:
            if current_key is not None:
                # Skip the _No response_ placeholder
                if line.strip() == "_No response_":
                    continue
                current_lines.append(line)
    if current_key is not None:
        fields[current_key] = "\n".join(current_lines).strip()
    return fields


# Map Chinese field labels to internal keys
FIELD_NAME_MAP = {
    "需求描述": "requirement_description",
    "臨床情境": "clinical_context",
    "驗收條件": "acceptance_criteria",
    "風險等級": "risk_level",
    "安全性等級": "safety_class",
    "關聯項目": "related_issues",
    "備註": "notes",
    "設計方案": "design_description",
    "架構影響": "architecture_impact",
    "關聯需求": "related_requirements",
    "安全考量": "safety_considerations",
    "介面描述": "interface_description",
    "危害情境": "hazard_situation",
    "嚴重度": "severity",
    "發生機率": "probability",
    "風險控制措施": "risk_control",
    "殘餘風險": "residual_risk",
    "測試目的": "test_purpose",
    "測試步驟": "test_steps",
    "預期結果": "expected_result",
    "實際結果": "actual_result",
    "測試結論": "test_result",
    "測試環境": "test_environment",
}


def normalize_field_name(label):
    return FIELD_NAME_MAP.get(label, label)


def enrich_issue(issue):
    """Parse issue body and merge fields into the issue dict."""
    fields = parse_issue_body(issue.get("body", ""))
    issue.update(fields)
    return issue


# ---------------------------------------------------------------------------
# Traceability matrix
# ---------------------------------------------------------------------------

def extract_issue_refs(text):
    """Extract #NNN issue references from text."""
    if not text:
        return []
    return [int(m) for m in re.findall(r"#(\d+)", text)]


def build_traceability_matrix(requirements, designs, verifications, risks):
    """Build requirement-centric traceability rows."""
    # Index designs/verifications/risks by referenced requirement numbers
    design_by_req = {}
    for d in designs:
        for ref in extract_issue_refs(d.get("related_requirements", "")):
            design_by_req.setdefault(ref, []).append(d)

    verify_by_req = {}
    for v in verifications:
        for ref in extract_issue_refs(v.get("related_requirements", "")):
            verify_by_req.setdefault(ref, []).append(v)

    risk_by_req = {}
    for r in risks:
        for ref in extract_issue_refs(r.get("related_issues", "")):
            risk_by_req.setdefault(ref, []).append(r)

    matrix = []
    gaps = []
    for req in requirements:
        num = req["number"]
        row_designs = design_by_req.get(num, [])
        row_verifies = verify_by_req.get(num, [])
        row_risks = risk_by_req.get(num, [])

        missing = []
        if not row_designs:
            missing.append("設計")
        if not row_verifies:
            missing.append("驗證")
        if not row_risks:
            missing.append("風險")

        status = "完整" if not missing else f"缺少：{'、'.join(missing)}"

        matrix.append({
            "requirement": req,
            "designs": row_designs,
            "verifications": row_verifies,
            "risks": row_risks,
            "status": status,
        })

        if missing:
            gaps.append({"number": num, "title": req["title"], "missing": missing})

    return matrix, gaps


# ---------------------------------------------------------------------------
# PR enrichment
# ---------------------------------------------------------------------------

def enrich_pr(repo, pr):
    """Add reviewer names and related issues to a PR."""
    reviews = fetch_pr_reviews(repo, pr["number"])
    reviewers = list({r["user"]["login"] for r in reviews if r.get("state") == "APPROVED"})
    pr["reviewers"] = reviewers

    body = pr.get("body", "") or ""
    related = re.findall(r"(?:Closes|Fixes|Relates to|關聯)\s*#(\d+)", body, re.IGNORECASE)
    pr["related_issues"] = ", ".join(f"#{n}" for n in related) if related else ""

    # Check if PR touches requirements or risks
    pr["has_requirement_change"] = bool(re.search(r"需求|requirement", body, re.IGNORECASE))
    pr["has_risk_change"] = bool(re.search(r"風險|risk", body, re.IGNORECASE))

    return pr


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="CQL Platform TFDA 法規文件產生器")
    parser.add_argument("--repo", required=True, help="GitHub repo (owner/name)")
    parser.add_argument("--version", required=True, help="文件版本號，例如 1.0.0")
    parser.add_argument("--output", default=None, help="輸出目錄 (預設: regulatory_docs/output)")
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    template_dir = script_dir.parent / "templates"
    output_dir = Path(args.output) if args.output else script_dir.parent / "output"
    output_dir.mkdir(parents=True, exist_ok=True)

    env = Environment(loader=FileSystemLoader(str(template_dir)), keep_trailing_newline=True)
    today = datetime.now().strftime("%Y-%m-%d")

    print(f"正在從 GitHub 擷取資料 ({args.repo})...")

    # Fetch issues by label
    requirements = [enrich_issue(i) for i in fetch_issues_by_label(args.repo, "IEC62304:需求")]
    designs = [enrich_issue(i) for i in fetch_issues_by_label(args.repo, "IEC62304:設計")]
    verifications = [enrich_issue(i) for i in fetch_issues_by_label(args.repo, "IEC62304:驗證")]
    risks = [enrich_issue(i) for i in fetch_issues_by_label(args.repo, "ISO14971:風險")]

    print(f"  需求: {len(requirements)}, 設計: {len(designs)}, 驗證: {len(verifications)}, 風險: {len(risks)}")

    # Fetch PRs and tags
    print("正在擷取合併請求與版本標籤...")
    merged_prs = fetch_merged_prs(args.repo)
    for pr in merged_prs:
        enrich_pr(args.repo, pr)
    tags = fetch_tags(args.repo)

    print(f"  合併 PR: {len(merged_prs)}, 版本標籤: {len(tags)}")

    # Build traceability matrix
    matrix, gaps = build_traceability_matrix(requirements, designs, verifications, risks)

    # Common template context
    common = {"version": args.version, "date": today}

    # Render documents
    documents = [
        ("srs_template.md", "軟體需求規格書_SRS.md", {**common, "requirements": requirements}),
        ("sds_template.md", "軟體設計規格書_SDS.md", {**common, "designs": designs}),
        ("risk_report_template.md", "風險管理報告_RMR.md", {**common, "risks": risks}),
        ("verification_report_template.md", "軟體驗證報告_SVR.md", {**common, "verifications": verifications, "test_summary": None}),
        ("traceability_matrix_template.md", "追溯矩陣_TM.md", {**common, "matrix": matrix, "gaps": gaps}),
        ("change_control_template.md", "變更管制紀錄_CCR.md", {**common, "pull_requests": merged_prs, "tags": tags}),
    ]

    print(f"\n正在產生 {len(documents)} 份法規文件...")
    for template_name, output_name, context in documents:
        template = env.get_template(template_name)
        rendered = template.render(**context)
        out_path = output_dir / output_name
        out_path.write_text(rendered, encoding="utf-8")
        print(f"  已產生: {out_path}")

    print(f"\n完成！所有文件已產出至 {output_dir}")

    # Print gap summary
    if gaps:
        print(f"\n追溯缺口提醒（{len(gaps)} 項需求缺少對應）：")
        for gap in gaps:
            print(f"  #{gap['number']} {gap['title']}：缺少 {'、'.join(gap['missing'])}")


if __name__ == "__main__":
    main()
