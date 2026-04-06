#!/usr/bin/env bash
# Demo: Run key push tests with sleeps for readability
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  Git-Proxy Demo: Store-and-Forward Push Tests"
echo "════════════════════════════════════════════════════════════"
sleep 2

run() {
    local name="$1"
    local script="$2"
    echo ""
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "→ ${name}"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    sleep 1

    if bash "${script}"; then
        echo "✓ ${name} passed"
    else
        echo "✗ ${name} failed"
    fi
    sleep 2
}

run "Golden-path push"              "${SCRIPT_DIR}/demo-push-pass.sh"
run "Invalid commit message"        "${SCRIPT_DIR}/demo-push-fail-message.sh"

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  Demo complete"
echo "════════════════════════════════════════════════════════════"
