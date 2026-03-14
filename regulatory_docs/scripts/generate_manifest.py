#!/usr/bin/env python3
"""
CQL Platform — 法規文件 SHA-256 完整性清單產生器

對 regulatory_docs/output/ 下所有產出檔計算 SHA-256，
產出 MANIFEST.sha256 供日後驗證文件完整性。

用途：
  - TFDA 查驗登記送件時，附上此清單作為「文件未竄改」的證明
  - 日後可用 `sha256sum -c MANIFEST.sha256` 驗證

使用方式:
    python generate_manifest.py --dir regulatory_docs/output --version 1.0.0
"""

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path


def sha256_file(filepath: Path) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    parser = argparse.ArgumentParser(description="法規文件 SHA-256 清單產生器")
    parser.add_argument("--dir", required=True, help="文件目錄")
    parser.add_argument("--version", default="unknown", help="版本號")
    parser.add_argument("--commit", default=None, help="Git commit SHA")
    args = parser.parse_args()

    doc_dir = Path(args.dir)
    if not doc_dir.is_dir():
        print(f"錯誤：目錄不存在: {doc_dir}", file=sys.stderr)
        sys.exit(1)

    commit_sha = args.commit or os.environ.get(
        "GITHUB_SHA",
        os.popen("git rev-parse HEAD 2>/dev/null").read().strip() or "unknown",
    )

    now = datetime.now(timezone.utc)

    # Collect all files (exclude the manifest itself)
    files = sorted(
        p for p in doc_dir.rglob("*")
        if p.is_file() and p.name not in ("MANIFEST.sha256", "MANIFEST.json")
    )

    if not files:
        print("警告：目錄中沒有任何檔案", file=sys.stderr)
        sys.exit(1)

    entries = []
    sha256_lines = []
    for filepath in files:
        rel = filepath.relative_to(doc_dir)
        digest = sha256_file(filepath)
        entries.append({
            "file": str(rel),
            "sha256": digest,
            "size_bytes": filepath.stat().st_size,
        })
        sha256_lines.append(f"{digest}  {rel}")

    # Write sha256sum-compatible file
    manifest_path = doc_dir / "MANIFEST.sha256"
    manifest_path.write_text("\n".join(sha256_lines) + "\n", encoding="utf-8")

    # Write JSON manifest with metadata
    json_manifest = {
        "version": args.version,
        "commit_sha": commit_sha,
        "generated_at": now.isoformat(),
        "generator": "generate_manifest.py",
        "file_count": len(entries),
        "files": entries,
    }
    json_path = doc_dir / "MANIFEST.json"
    json_path.write_text(
        json.dumps(json_manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"完成！{len(entries)} 個檔案已計算 SHA-256")
    print(f"  MANIFEST.sha256: {manifest_path}")
    print(f"  MANIFEST.json:   {json_path}")
    print(f"  版本: {args.version}")
    print(f"  Commit: {commit_sha}")
    print(f"  時間: {now.isoformat()}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
