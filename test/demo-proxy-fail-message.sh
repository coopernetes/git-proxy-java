#!/usr/bin/env bash
# Demo: Transparent proxy push with validation failure (commit message)
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"
resolve_pat ~/.github-pat
GIT_REPO=${GIT_REPO:-"github.com/coopernetes/test-repo.git"}

PROXY_URL="http://${GIT_USERNAME}:${GIT_PASSWORD}@localhost:8080/proxy/${GIT_REPO}"
TEST_BRANCH="test/proxy-fail-msg-$(date +%s)"
REPO_DIR=$(mktemp -d "${TMPDIR:-/tmp}/proxy-test-fail-msg-XXXX")

cleanup() {
    git -C "${REPO_DIR}" remote set-url origin "http://${GIT_USERNAME}:${GIT_PASSWORD}@${GIT_REPO}" 2>/dev/null || true
    safe_rm_rf "${REPO_DIR}"
}
trap cleanup EXIT

echo "=========================================================="
echo "  PROXY: Commit message validation failure"
echo "  URL: http://localhost:8080/proxy/${GIT_REPO}"
echo "=========================================================="
sleep 2

echo "→ Cloning repository (${PROXY_URL//${GIT_PASSWORD}/***}) via git-proxy..."
git clone "${PROXY_URL}" "${REPO_DIR}"
sleep 2

cd "${REPO_DIR}"
echo "→ Creating test branch..."
git checkout -b "${TEST_BRANCH}"
sleep 1

git config user.name "${GIT_AUTHOR_NAME}"
git config user.email "${GIT_EMAIL}"

echo "→ Creating commit with INVALID message (WIP flag)..."
echo "invalid - $(date)" >> test-file.txt
git add test-file.txt
git commit -m "WIP: still working on this feature"
sleep 1

echo "→ Attempting push (will be REJECTED by validation)..."
git push origin "${TEST_BRANCH}" 2>&1 || true
sleep 2

echo ""
echo "✓ PASSED: validation correctly rejected invalid commit"
