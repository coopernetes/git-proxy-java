---
description: Update GitHub Actions uses: pins to latest commit SHAs, respecting the version constraint in ratchet-style comments
allowed-tools: Bash, Read, Edit, Glob, Grep
---

## Task

Update all GitHub Actions `uses:` pins in `.github/workflows/` to the latest commit SHA that satisfies the version constraint declared in the trailing comment.

## Comment format (preserve exactly)

```
uses: actions/checkout@de0fac2e4500dabe0009e67214ff5f5447ce83dd # ratchet:actions/checkout@v6
```

The `# ratchet:<action>@<version>` comment is the version constraint. Never change or remove it.

Version constraint semantics:
- `@v6` — major pin: upgrade to latest `v6.x.y` tag
- `@v7.4` — minor pin: upgrade to latest `v7.4.x` tag
- `@v7.4.0` — patch pin: upgrade to latest `v7.4.x` tag (still tracks patches)

## Steps

### 1. Collect all pinned actions

Grep all `.github/workflows/*.yml` files for lines matching the ratchet pattern:

```bash
grep -rn 'uses: .\+@[0-9a-f]\{40\} # ratchet:' .github/workflows/
```

For each matching line, parse out:
- `file` — the workflow file path
- `action` — `owner/repo` portion (e.g. `actions/checkout`)
- `current_sha` — the 40-char hex SHA currently pinned
- `constraint` — the version string after `@` in the ratchet comment (e.g. `v6`, `v7.4.0`)

### 2. For each unique `action@constraint`, find the latest matching tag

Use `gh api` (never curl) to list tags:

```bash
gh api repos/{owner}/{repo}/tags --paginate --jq '.[].name'
```

Filter the returned tag names client-side:
- `@v6` → keep tags matching `^v6\.\d+\.\d+$`, sort semver descending, take first
- `@v7.4` → keep tags matching `^v7\.4\.\d+$`, sort semver descending, take first
- `@v7.4.0` → keep tags matching `^v7\.4\.\d+$`, sort semver descending, take first

Use Python or bash for semver sorting — do not rely on lexicographic order.

### 3. Resolve tag name to commit SHA

Tags may be annotated (type `tag`) or lightweight (type `commit`). Always resolve to the underlying commit SHA:

```bash
# Step 1: get the ref object
gh api repos/{owner}/{repo}/git/ref/tags/{tag} --jq '.object | {type, sha}'

# Step 2: if type == "tag" (annotated), dereference to commit
gh api repos/{owner}/{repo}/git/tags/{tag_object_sha} --jq '.object.sha'
```

The final SHA must be the commit SHA (40 hex chars), not the tag object SHA.

### 4. Update files

For each pin where the resolved commit SHA differs from `current_sha`:
- Use Edit to replace `@{current_sha}` with `@{new_sha}` on that exact line
- Leave the `# ratchet:` comment completely unchanged
- If the latest tag itself changed (e.g. `v6.1.0` → `v6.2.0`), update the tag name in the comment too

### 5. Report results

After all updates, print a summary table:

```
ACTION                              CONSTRAINT   OLD TAG    NEW TAG    SHA (first 12)
actions/checkout                    @v6          v6.1.0  →  v6.2.0    abc123def456
docker/setup-buildx-action          @v3          v3.9.0  →  v3.9.0    (already current)
anchore/scan-action                 @v7.4.0      v7.4.0  →  v7.4.1    fed987654321
```

Mark already-current pins as "(no change)" rather than omitting them — useful to confirm they were checked.
