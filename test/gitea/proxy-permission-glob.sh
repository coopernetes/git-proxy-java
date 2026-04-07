#!/usr/bin/env bash
# GLOB permission smoke test via transparent proxy.
#
# user2 has a GLOB permission on /otherorg/* — matches any repo under otherorg.
#
# Expects:
#   PASS — push to /otherorg/other-foo   (matches glob)
#   PASS — push to /otherorg/other-bar   (matches glob)
#   FAIL — push to /test-owner/test-repo (outside glob scope)
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

TOKEN="${GITEA_USER2_TOKEN:-}"
if [ -z "${TOKEN}" ]; then
    echo "ERROR: GITEA_USER2_TOKEN not set. Run docker/gitea-setup.sh first." >&2
    exit 1
fi

REPO_FOO="gitea/otherorg/other-foo.git"
REPO_BAR="gitea/otherorg/other-bar.git"
REPO_DENIED="gitea/test-owner/test-repo.git"

print_header "PERMISSION: GLOB (proxy) — user2 (/otherorg/*)" "http://localhost:8080"

# --- allowed: /otherorg/other-foo ---
PUSH_URL="http://${GIT_USERNAME}:${TOKEN}@localhost:8080/proxy/${REPO_FOO}"
setup_repo "${PUSH_URL}" "proxy-perm-glob-foo"
git config user.email "user2@example.com"
echo "glob-foo - $(date)" >> test-file.txt
git add test-file.txt
git commit -m "test: glob permission — otherorg/other-foo"
run_proxy_test_expect_success "PASS: user2 → /otherorg/other-foo (glob match)"

# --- allowed: /otherorg/other-bar ---
PUSH_URL="http://${GIT_USERNAME}:${TOKEN}@localhost:8080/proxy/${REPO_BAR}"
setup_repo "${PUSH_URL}" "proxy-perm-glob-bar"
git config user.email "user2@example.com"
echo "glob-bar - $(date)" >> test-file.txt
git add test-file.txt
git commit -m "test: glob permission — otherorg/other-bar"
run_proxy_test_expect_success "PASS: user2 → /otherorg/other-bar (glob match)"

# --- denied: /test-owner/test-repo (outside glob scope) ---
PUSH_URL="http://${GIT_USERNAME}:${TOKEN}@localhost:8080/proxy/${REPO_DENIED}"
setup_repo "${PUSH_URL}" "proxy-perm-glob-deny"
git config user.email "user2@example.com"
echo "glob-deny - $(date)" >> test-file.txt
git add test-file.txt
git commit -m "test: glob permission — test-owner denied"
run_test_expect_failure "FAIL: user2 → /test-owner/test-repo (outside glob)"

print_results
[[ ${FAIL} -eq 0 ]]
