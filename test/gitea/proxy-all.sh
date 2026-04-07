#!/usr/bin/env bash
# Orchestrator: runs all Gitea transparent proxy smoke tests.
set -euo pipefail

DIR="$(dirname "${BASH_SOURCE[0]}")"
source "${DIR}/common.sh"

print_header "GITEA PROXY SMOKE TESTS — ALL (8 suites)" "http://localhost:8080"

run_orchestrated "proxy: golden path"            "${DIR}/proxy-pass.sh"
run_orchestrated "proxy: commit message failures" "${DIR}/proxy-fail-message.sh"
run_orchestrated "proxy: author email failures"   "${DIR}/proxy-fail-author.sh"
run_orchestrated "proxy: identity (linked)"       "${DIR}/proxy-identity.sh"
run_orchestrated "proxy: identity (unlinked)"     "${DIR}/proxy-identity-unlinked.sh"
run_orchestrated "proxy: permission (literal)"    "${DIR}/proxy-permission-literal.sh"
run_orchestrated "proxy: permission (glob)"       "${DIR}/proxy-permission-glob.sh"
run_orchestrated "proxy: permission (regex)"      "${DIR}/proxy-permission-regex.sh"

print_results
[[ ${FAIL} -eq 0 ]]
