#!/usr/bin/env python3
"""Back-fill commit hashes into docs/CHANGE_LOG.md.

Replaces the manual 2-commit pattern described in CLAUDE.md (commit the change
with an empty commit column, then later a 'docs: add commit hash #NNN' commit).
Instead:

  1. Developer commits the change once, leaving the row's final column as ` | |`.
  2. PR merges to main.
  3. This script runs (via GitHub Action or manually) and fills in the hash(es)
     of the commit(s) that referenced the ID in their message.

Called from:
  - .github/workflows/changelog-backfill.yml  (post-merge auto-commit)
  - scripts/changelog/fill-hash.sh            (local manual run)

Exit codes:
  0  — ran cleanly (may or may not have changed anything; check stdout)
  1  — argument / file error
  2  — git command failed

The script is IDEMPOTENT: running it twice in a row produces no further changes.
"""
from __future__ import annotations

import re
import subprocess
import sys
from pathlib import Path

CHANGELOG_PATH = Path("docs/CHANGE_LOG.md")

# Match a row whose commit column is truly empty (last ` | |` at end of line).
# The negative lookbehind prevents matching rows that already have content —
# we don't touch partially-filled rows.
EMPTY_ROW_RE = re.compile(
    r"^\| (?P<id>(?:PAT|BUG)-\d+) \|.*\| \|$"
)


def run_git(*args: str) -> str:
    """Run a git command and return stdout. Exits on failure."""
    try:
        result = subprocess.run(
            ["git", *args], capture_output=True, text=True, check=True, encoding="utf-8"
        )
        return result.stdout
    except subprocess.CalledProcessError as e:
        print(f"git {' '.join(args)} failed: {e.stderr}", file=sys.stderr)
        sys.exit(2)


def hashes_for_id(entry_id: str) -> list[str]:
    """Return list of commit hashes (on main) whose message contains `(#<id>)`.

    Paren delimiters filter out 'docs: add commit hash #NNN' and issue-only refs.
    Ordered oldest → newest because that's the readable chronology for a multi-
    commit PAT/BUG; older commits come first in the backfilled cell.
    """
    # --fixed-strings  literal matching (no regex escape needed)
    # --no-merges      exclude merge commits — they duplicate the feature
    #                  branch's hash reference via the PR title line
    # --reverse        oldest first → natural reading order for multi-commit IDs
    output = run_git(
        "log",
        "origin/main",
        f"--grep=(#{entry_id})",
        "--fixed-strings",
        "--no-merges",
        "--reverse",
        "--format=%H",
    )
    hashes = [h.strip() for h in output.splitlines() if h.strip()]
    # Short-form for readability in the markdown; matches existing convention
    # (see pre-existing rows in CHANGE_LOG.md that use 7-char short hashes).
    return [h[:7] for h in hashes]


def format_hash_cell(hashes: list[str]) -> str:
    """Format hashes as the CHANGE_LOG.md commit column content.

    Existing convention (sampled from BUG-107 / BUG-108 rows):
      [`<short>`](../../commit/<short>) [`<short2>`](../../commit/<short2>) ...
    """
    if not hashes:
        return ""
    return " ".join(f"[`{h}`](../../commit/{h})" for h in hashes)


def backfill(path: Path) -> int:
    """Read CHANGE_LOG, fill empty commit cells, write back. Return count changed."""
    if not path.exists():
        print(f"CHANGE_LOG not found at {path}", file=sys.stderr)
        sys.exit(1)

    content = path.read_text(encoding="utf-8")
    new_lines = []
    changed = 0

    for line in content.splitlines():
        m = EMPTY_ROW_RE.match(line)
        if not m:
            new_lines.append(line)
            continue

        entry_id = m.group("id")
        hashes = hashes_for_id(entry_id)
        if not hashes:
            # No commit references this ID yet (e.g. the change hasn't merged)
            # — leave the cell empty, try again next run.
            print(f"  {entry_id}: no matching commits found — leaving empty", file=sys.stderr)
            new_lines.append(line)
            continue

        # Replace the final ` | |` with ` |<hash-cell> |`
        new_line = re.sub(r"\| \|$", f"|{format_hash_cell(hashes)} |", line)
        new_lines.append(new_line)
        changed += 1
        print(f"  {entry_id}: filled with {len(hashes)} hash(es): {' '.join(hashes)}")

    if changed:
        path.write_text("\n".join(new_lines) + "\n", encoding="utf-8")

    return changed


def main() -> int:
    repo_root = Path(__file__).resolve().parents[2]
    changelog = repo_root / CHANGELOG_PATH
    changed = backfill(changelog)
    if changed == 0:
        print("No changes needed.")
    else:
        print(f"Backfilled {changed} row(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
