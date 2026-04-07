#!/usr/bin/env bash
# Identity verification via Gitea transparent proxy (linked user — expect success).
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

GIT_REPO=${GIT_REPO:-"gitea/test-owner/test-repo.git"}
PUSH_URL="http://${GIT_USERNAME}:${GIT_PASSWORD}@localhost:8080/proxy/${GIT_REPO}"

print_header "TRANSPARENT PROXY: IDENTITY (linked)" "${PUSH_URL}"

setup_repo "${PUSH_URL}" "proxy-identity"
git config user.email "testuser@example.com"
echo "identity - $(date)" >> test-file.txt
git add test-file.txt
git commit -m "test: identity verification — Gitea token resolved"
run_proxy_test_expect_success "PASS: test-user → identity verified, push approved"

print_results
[[ ${FAIL} -eq 0 ]]
