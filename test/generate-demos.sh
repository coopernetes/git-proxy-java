#!/usr/bin/env bash
# Generate all demo GIFs using asciinema and agg
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$(mkdir -p "${OUTPUT_DIR:-.}" && cd "${OUTPUT_DIR:-.}" && pwd)"

# Check dependencies
if ! command -v asciinema &> /dev/null; then
    echo "ERROR: asciinema not found. Install with: npm install -g asciinema"
    exit 1
fi

if ! command -v agg &> /dev/null; then
    echo "ERROR: agg not found. Install with: cargo install agg"
    exit 1
fi

echo "=========================================================="
echo "  Generating demo GIFs"
echo "=========================================================="
echo ""

demos=(
    "demo-proxy-pass:Proxy golden-path with auto-approval"
    "demo-proxy-fail-message:Proxy validation failure"
    "demo-proxy-identity-all:Proxy identity verification (all providers)"
    "demo-push-simple:Push golden-path"
    "demo-push-fix-message:Push failure and fix"
)

for demo in "${demos[@]}"; do
    name="${demo%:*}"
    desc="${demo#*:}"

    cast_file="${OUTPUT_DIR}/${name}.cast"
    gif_file="${OUTPUT_DIR}/${name}.gif"

    echo "→ ${desc}"
    asciinema rec --overwrite "${cast_file}" -c "bash ${SCRIPT_DIR}/${name}.sh"
    echo "  Converting to GIF..."
    agg --speed 0.75 "${cast_file}" "${gif_file}"
    echo "  ✓ ${gif_file}"
    echo ""
done

echo "=========================================================="
echo "  Done! GIFs generated in ${OUTPUT_DIR}"
echo "=========================================================="
