# Plan: DiffCheck refactor

## Goal

Eliminate the duplicated `scanDiff()` / `extractFileName()` logic that exists in
both `DiffScanningHook` (S&F) and `ScanDiffFilter` (proxy), and wire both adapters
through the `DiffCheck` interface — matching the pattern already used by `CommitCheck`.

## Background

`DiffCheck` is a mode-independent interface in `org.finos.gitproxy.validation`:

```java
Optional<List<Violation>> check(String diff);
```

`SecretScanCheck` already implements it (uses `GitleaksRunner.scan()` --pipe mode).
**Do not delete `SecretScanCheck` or `DiffCheck`** — the abstraction is intentionally
kept for future scanner extensibility, even though nothing wires it today.

The blocked-content scan logic is currently copy-pasted:
- `DiffScanningHook#scanDiff()` + `extractFileName()` (S&F hook)
- `ScanDiffFilter#scanDiff()` + `extractFileName()` (proxy filter)

## Steps

### 1. Create `BlockedContentDiffCheck implements DiffCheck`

New file: `jgit-proxy-core/src/main/java/org/finos/gitproxy/validation/BlockedContentDiffCheck.java`

- Constructor takes `CommitConfig.BlockConfig block`
- Move the shared `scanDiff()` + `extractFileName()` logic here
- Return `Optional.of(List<Violation>)` — this check never fails-open (no external
  process), so `Optional.empty()` is not needed; always return `Optional.of(...)`

### 2. Update `DiffScanningHook`

- Remove inline `scanDiff()` and `extractFileName()`
- Construct a `BlockedContentDiffCheck` from `commitConfig.getDiff().getBlock()`
- Delegate: `check.check(diff)` → map `List<Violation>` to the existing
  `validationContext.addIssue()` calls

### 3. Update `ScanDiffFilter`

- Remove inline `scanDiff()` and `extractFileName()`
- Same delegation pattern as above

### 4. (Separate step, not urgent) Remove dead `--pipe` path

Once there's appetite for it:
- Delete `SecretScanCheck.java`
- Delete `DiffCheck.java` (or keep the interface if we expect a future git-mode
  `DiffCheck` wrapper around `GitleaksRunner.scanGit()`)
- Remove `GitleaksRunner.scan()`, `buildCommand()`, `enrichFindings()`,
  `parseHunkNewStart()`

This step was started but reverted because the abstraction is worth keeping.
Revisit when the team decides on the multi-scanner direction.

## Files to touch

| File | Change |
|------|--------|
| `validation/BlockedContentDiffCheck.java` | **new** |
| `git/DiffScanningHook.java` | remove inline scan logic, delegate to check |
| `servlet/filter/ScanDiffFilter.java` | remove inline scan logic, delegate to check |
| `validation/DiffCheck.java` | no change |
| `validation/SecretScanCheck.java` | no change |

## Tests

Existing unit tests for `ScanDiffFilter#scanDiff()` should be moved/adapted to test
`BlockedContentDiffCheck#check()` directly. The filter/hook tests become thin
integration tests that verify wiring.
