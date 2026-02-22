---
description: Git 변경사항을 분석하여 어떤 플러그인에 변경이 생겼는지 보고합니다
argument-hint: "[--last-commit | --pull]"
allowed-tools: Bash, Glob, Read, Grep
---

Analyze git changes and report which plugins have been modified.

Arguments provided by user: `{{arguments}}`

## Step 1: Parse Arguments and Determine Mode

Check the argument: `{{arguments}}`

- If argument is `--last-commit`: mode = "last-commit"
- If argument is `--pull`: mode = "pull"
- If no argument (empty): mode = "working-tree"

## Step 2: Verify Git Repository

Run the following to confirm we are inside a git repository:

`git rev-parse --is-inside-work-tree`

If this command fails or returns an error, stop and output:

```
Error: This directory is not a git repository. Please run this command from within a git project.
```

## Step 3: Gather Changed Files Based on Mode

Execute the appropriate git command for the determined mode:

**working-tree mode** — collect both staged and unstaged changes plus untracked files:
```
git status --porcelain
```
Parse the two-character status code at the start of each line. Map codes as:
- First char (index/staged): A=Added, M=Modified, D=Deleted, R=Renamed
- Second char (working tree): M=Modified, D=Deleted, ?=Untracked (`??` prefix means untracked)

Collect file paths from all output lines.

**last-commit mode:**
```
git diff HEAD~1 --name-status
```
Parse tab-separated `STATUS\tFILE` lines.

**pull mode:**

First check if ORIG_HEAD exists:
```
git rev-parse ORIG_HEAD
```
If this fails, stop and output:
```
Error: pull 이력이 없습니다. --last-commit을 사용해주세요.
```

If ORIG_HEAD exists, run:
```
git diff ORIG_HEAD..HEAD --name-status
```
Parse tab-separated `STATUS\tFILE` lines.

## Step 4: Filter and Extract Plugin Names

From the collected file list, filter only paths that start with `plugins/`.

For each such path:
- Extract the plugin name: the segment immediately after `plugins/` (i.e., `plugins/{plugin-name}/...` → `{plugin-name}`)
- Keep track of the relative path within the plugin (the part after `plugins/{plugin-name}/`)
- Retain the status code (Added, Modified, Deleted, Renamed, Untracked)

If no files under `plugins/` are found after filtering, output:
```
No plugin changes detected.
```
and stop.

Also collect files that do NOT start with `plugins/` — count them for the "Other changes" summary.

## Step 5: Group Changes by Plugin

Group the filtered entries by plugin name. For each plugin, build a list of `(status, relative-path)` pairs.

Determine the component type for each changed file by examining the second path segment within the plugin:
- `commands/` → Commands
- `agents/` → Agents
- `skills/` → Skills
- `hooks/` → Hooks
- Other (e.g., root files, `plugin.json`, `README.md`) → Config/Other

## Step 6: Read Plugin Versions

For each plugin that has changes, attempt to read its `plugin.json`:

Use the Read tool on `plugins/{plugin-name}/plugin.json`.

Extract the `version` field. If the file does not exist or has no version field, use `N/A`.

## Step 7: Output the Report

Print the report as markdown using the following structure:

```
## Plugin Changes Report
**Mode**: {mode}
**Date**: {today's date}

---

### {plugin-name} v{version} ({N} files changed)

**Changed component types**: {comma-separated list, e.g. "Agents, Skills"}

| Status | File |
|--------|------|
| {status} | {relative-path-within-plugin} |
...

**Summary**: {N} modified, {N} added, {N} deleted, {N} renamed, {N} untracked

---

### {next-plugin-name} v{version} ({N} files changed)
...

---

## Other Changes
{N} files changed outside `plugins/` directory.

(List up to 10 file paths, one per line, prefixed with their status. If more than 10, show first 10 and append "... and {M} more".)
```

Status display labels:
- `A` or `??` → Added
- `M` → Modified
- `D` → Deleted
- `R` → Renamed

## Step 8: Error Handling Summary

Handle these cases explicitly before proceeding with the report:

| Condition | Output |
|-----------|--------|
| Not a git repo | `Error: This directory is not a git repository.` |
| `--pull` with no ORIG_HEAD | `Error: pull 이력이 없습니다. --last-commit을 사용해주세요.` |
| No changes under `plugins/` | `No plugin changes detected.` |
| `plugin.json` missing | Show version as `N/A` and continue |
| Unknown argument provided | `Error: Unknown argument '{arg}'. Usage: /plugin-creator:plugin-changes [--last-commit | --pull]` |

## Important Notes

- When parsing `git status --porcelain` for working-tree mode, each line has a 2-char status prefix followed by a space and then the filename. Handle rename lines which use ` -> ` separator.
- For `--name-status` output from `git diff`, lines may have `R100` or similar codes for renames — treat any status starting with `R` as Renamed.
- Sort plugins alphabetically in the report.
- Sort files within each plugin alphabetically by relative path.
- The report should be clean, readable markdown — do not add extra explanation outside the report structure.
