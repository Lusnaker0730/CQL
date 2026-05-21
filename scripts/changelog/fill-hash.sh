#!/usr/bin/env bash
# Local fallback for the CHANGE_LOG hash backfill — same logic as the GitHub
# Action at .github/workflows/changelog-backfill.yml, invocable by hand.
#
# Usage:
#   scripts/changelog/fill-hash.sh             # fill hashes, show changes but don't commit
#   scripts/changelog/fill-hash.sh --commit    # also git-commit the changes
#
# When to run locally:
#   - Your PR merged but the Action failed or is disabled
#   - You want to preview what the Action would do before push
#   - You're manually backfilling older rows
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

COMMIT=0
for arg in "$@"; do
    case "$arg" in
        --commit) COMMIT=1 ;;
        *) echo "unknown arg: $arg" >&2; exit 2 ;;
    esac
done

if ! command -v python >/dev/null 2>&1 && ! command -v python3 >/dev/null 2>&1; then
    echo "ERROR: python or python3 required" >&2
    exit 2
fi
PYTHON=$(command -v python3 || command -v python)

"$PYTHON" .github/scripts/changelog-backfill.py

if git diff --quiet docs/CHANGE_LOG.md; then
    echo "No changes."
    exit 0
fi

if [ "$COMMIT" -eq 1 ]; then
    git add docs/CHANGE_LOG.md
    git commit -m "docs: backfill commit hashes into CHANGE_LOG"
    echo "Committed."
else
    echo ""
    echo "Changes staged in working tree. Review with 'git diff docs/CHANGE_LOG.md'."
    echo "Re-run with --commit to create the commit automatically."
fi
