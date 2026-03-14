#!/usr/bin/env bash
# 建立 TFDA 法規相關 GitHub Labels
# 使用方式: GITHUB_REPO=owner/repo bash .github/setup-labels.sh

set -euo pipefail

REPO="${GITHUB_REPO:?請設定 GITHUB_REPO 環境變數，例如: owner/CQL}"

echo "正在為 ${REPO} 建立法規標籤..."

gh label create "IEC62304:需求"   --repo "$REPO" --color "0E8A16" --description "IEC 62304 Software Requirement"   --force
gh label create "IEC62304:設計"   --repo "$REPO" --color "1D76DB" --description "IEC 62304 Design Specification"   --force
gh label create "IEC62304:驗證"   --repo "$REPO" --color "5319E7" --description "IEC 62304 Verification Record"    --force
gh label create "ISO14971:風險"   --repo "$REPO" --color "D93F0B" --description "ISO 14971 Risk Analysis"          --force
gh label create "變更管制"         --repo "$REPO" --color "FBCA04" --description "Change Control"                   --force
gh label create "安全性等級-A"     --repo "$REPO" --color "C2E0C6" --description "Safety Class A - No injury"       --force
gh label create "安全性等級-B"     --repo "$REPO" --color "FEF2C0" --description "Safety Class B - Non-serious"     --force
gh label create "安全性等級-C"     --repo "$REPO" --color "F9D0C4" --description "Safety Class C - Death/Serious"   --force

echo "完成！已建立 8 個法規標籤。"
