#!/usr/bin/env bash
# Author email validation failures via Gitea transparent proxy.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

PUSH_URL="http://${GIT_USERNAME}:${GIT_PASSWORD}@localhost:8080/proxy/${GIT_REPO}"

test_blocked_local_part() {
    git config user.email "noreply@example.com"
    echo "noreply - $(date)" >> test-file.txt
    git add test-file.txt
    git commit -m "chore: commit from noreply address"
}

test_blocked_domain() {
    git config user.email "user@invalid-corp.internal"
    echo "bad domain - $(date)" >> test-file.txt
    git add test-file.txt
    git commit -m "chore: commit from disallowed domain"
}

print_header "TRANSPARENT PROXY: AUTHOR EMAIL FAILURES" "${PUSH_URL}"

run_test() {
    local test_name="$1"; shift
    setup_repo "${PUSH_URL}" "proxy-author"
    "$@"
    run_test_expect_failure "${test_name}"
}

run_test "FAIL: noreply local-part"     test_blocked_local_part
run_test "FAIL: disallowed domain"      test_blocked_domain

print_results
[[ ${FAIL} -eq 0 ]]
