#!/usr/bin/env bash
# Environment defaults and credential resolution for test scripts

export GIT_USERNAME=${GIT_USERNAME:-"me"}
export GITPROXY_API_KEY=${GITPROXY_API_KEY:-"change-me-in-production"}

# resolve_pat() — set GIT_PASSWORD from env var or a PAT file
# Args: $1 = path to PAT file (e.g. ~/.github-pat)
resolve_pat() {
    local pat_file="$1"
    GIT_PASSWORD="${GIT_PASSWORD:-}"
    if [ -z "${GIT_PASSWORD}" ] && [ -f "${pat_file}" ]; then
        GIT_PASSWORD="$(cat "${pat_file}")"
    fi
    if [ -z "${GIT_PASSWORD}" ]; then
        echo "ERROR: GIT_PASSWORD not set and ${pat_file} not found" >&2
        exit 1
    fi
    export GIT_PASSWORD
}
