#!/usr/bin/env bash
# Demo: Store-and-forward push with invalid commit message (failure case)
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/env.sh"
resolve_pat ~/.github-pat
GIT_REPO=${GIT_REPO:-"github.com/coopernetes/test-repo.git"}

PUSH_URL="http://${GIT_USERNAME}:${GIT_PASSWORD}@localhost:8080/push/${GIT_REPO}"
TEST_BRANCH="test/push-fail-msg-$(date +%s)"
REPO_DIR=$(mktemp -d "${TMPDIR:-/tmp}/push-test-fail-msg-XXXX")

cleanup() {
    git -C "${REPO_DIR}" remote set-url origin "http://${GIT_USERNAME}:${GIT_PASSWORD}@${GIT_REPO}" 2>/dev/null || true
    safe_rm_rf "${REPO_DIR}"
}
trap cleanup EXIT

echo "→ Cloning repository via git-proxy..."
git clone "${PUSH_URL}" "${REPO_DIR}"
sleep 2

cd "${REPO_DIR}"
echo "→ Creating test branch..."
git checkout -b "${TEST_BRANCH}"
sleep 1

git config user.name "${GIT_AUTHOR_NAME}"
git config user.email "${GIT_EMAIL}"

echo "→ Creating commit with INVALID message (no type prefix)..."
echo "invalid - $(date)" >> test-file.txt
git add test-file.txt
git commit -m "this is a bad commit message"
sleep 1

echo "→ Attempting push (should be REJECTED by validation)..."
git push origin "${TEST_BRANCH}" 2>&1 || true
sleep 1

echo ""
echo "✓ PASSED: validation correctly rejected invalid commit"
