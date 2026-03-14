#!/usr/bin/env python3
"""
CQL Platform — GitHub Issue 法規快照工具

將所有帶法規標籤的 Issues 匯出為 JSON，作為不可變的時間點備份。
即使日後 Issue 被編輯或刪除，此快照仍可用來重建該時間點的法規文件。

使用方式:
    GITHUB_TOKEN=xxx python snapshot_issues.py --repo owner/CQL --output ./snapshots
"""

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

import requests

GITHUB_API = "https://api.github.com"

REGULATORY_LABELS = [
    "IEC62304:需求",
    "IEC62304:設計",
    "IEC62304:驗證",
    "ISO14971:風險",
]


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
    headers = github_headers()
    params = params or {}
    params.setdefault("per_page", 100)
    results = []
    while url:
        resp = requests.get(url, headers=headers, params=params, timeout=30)
        resp.raise_for_status()
        results.extend(resp.json())
        url = resp.links.get("next", {}).get("url")
        params = {}
    return results


def fetch_all_regulatory_issues(repo):
    """Fetch all issues (open + closed) with any regulatory label."""
    all_issues = {}
    for label in REGULATORY_LABELS:
        url = f"{GITHUB_API}/repos/{repo}/issues"
        params = {"labels": label, "state": "all", "per_page": 100}
        issues = paginated_get(url, params)
        for issue in issues:
            # De-duplicate across labels (one issue may have multiple labels)
            all_issues[issue["number"]] = issue
    return sorted(all_issues.values(), key=lambda i: i["number"])


def strip_volatile_fields(issue):
    """Keep only the fields needed for regulatory reconstruction."""
    labels = [l["name"] for l in issue.get("labels", [])]
    return {
        "number": issue["number"],
        "title": issue["title"],
        "state": issue["state"],
        "body": issue.get("body"),
        "labels": labels,
        "created_at": issue.get("created_at"),
        "updated_at": issue.get("updated_at"),
        "closed_at": issue.get("closed_at"),
        "user": issue.get("user", {}).get("login"),
        "html_url": issue.get("html_url"),
        "milestone": issue.get("milestone", {}).get("title") if issue.get("milestone") else None,
    }


def sha256_of_content(content: str) -> str:
    return hashlib.sha256(content.encode("utf-8")).hexdigest()


def main():
    parser = argparse.ArgumentParser(description="CQL Platform 法規 Issue 快照工具")
    parser.add_argument("--repo", required=True, help="GitHub repo (owner/name)")
    parser.add_argument("--output", default=None, help="輸出目錄")
    parser.add_argument("--version", default=None, help="版本號（加到快照 metadata）")
    args = parser.parse_args()

    output_dir = Path(args.output) if args.output else Path(__file__).resolve().parent.parent / "snapshots"
    output_dir.mkdir(parents=True, exist_ok=True)

    now = datetime.now(timezone.utc)
    timestamp = now.strftime("%Y%m%dT%H%M%SZ")

    print(f"正在從 GitHub 擷取法規 Issues ({args.repo})...")
    issues = fetch_all_regulatory_issues(args.repo)
    print(f"  共 {len(issues)} 筆法規 Issues")

    stripped = [strip_volatile_fields(i) for i in issues]

    # Count by label category
    counts = {}
    for issue in stripped:
        for label in issue["labels"]:
            if label in REGULATORY_LABELS:
                counts[label] = counts.get(label, 0) + 1

    # Build snapshot payload
    commit_sha = os.environ.get("GITHUB_SHA", os.popen("git rev-parse HEAD 2>/dev/null").read().strip() or "unknown")

    snapshot = {
        "metadata": {
            "repo": args.repo,
            "version": args.version or "snapshot",
            "timestamp": now.isoformat(),
            "commit_sha": commit_sha,
            "total_issues": len(stripped),
            "counts": counts,
        },
        "issues": stripped,
    }

    # Serialize deterministically (sorted keys, no ASCII escape for Chinese)
    content = json.dumps(snapshot, ensure_ascii=False, indent=2, sort_keys=True)
    content_hash = sha256_of_content(content)
    snapshot["metadata"]["sha256"] = content_hash

    # Re-serialize with hash included
    content = json.dumps(snapshot, ensure_ascii=False, indent=2, sort_keys=True)

    # Write snapshot
    filename = f"issue-snapshot-{timestamp}.json"
    out_path = output_dir / filename
    out_path.write_text(content, encoding="utf-8")
    print(f"  快照已儲存: {out_path}")
    print(f"  SHA-256: {content_hash}")

    # Also write a "latest" symlink-like file
    latest_path = output_dir / "latest-snapshot.json"
    latest_path.write_text(content, encoding="utf-8")

    print(f"\n完成！{len(stripped)} 筆 Issues 已快照至 {output_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
