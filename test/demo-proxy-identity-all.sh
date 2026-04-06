#!/usr/bin/env bash
# Demo: Run all three proxy identity verification scenarios
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ""
echo "=========================================================="
echo "  Proxy Identity Verification (All Providers)"
echo "=========================================================="
sleep 2

run() {
    local name="$1"
    local script="$2"
    echo ""
    echo "============================================="
    echo "  ${name}"
    echo "============================================="
    sleep 1

    if bash "${script}"; then
        echo "✓ ${name}"
    else
        echo "✗ ${name}"
    fi
    sleep 2
}

run "GitHub (fully resolved)"          "${SCRIPT_DIR}/proxy-identity-github.sh"
run "GitLab (resolved, email warning)" "${SCRIPT_DIR}/proxy-identity-gitlab.sh"
run "Codeberg (unresolved, blocked)"   "${SCRIPT_DIR}/proxy-identity-codeberg.sh"

echo ""
echo "=========================================================="
echo "  Demo complete"
echo "=========================================================="
