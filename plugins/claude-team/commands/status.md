---
namespace: team
description: Display active team and member status dashboard
argument-hint: [team-name]
allowed-tools: Bash, Read, Glob
---

Generate a comprehensive team status dashboard for the specified team (or all active teams if no argument provided).

## Phase 1: Team Discovery

Check if team name provided in $ARGUMENTS:

- **If team name provided:** Validate team exists at `~/.claude/teams/$ARGUMENTS/config.json`
  - If exists: Process only this team
  - If not exists: Show error "Team '$ARGUMENTS' not found" and list available teams

- **If no team name:** Process all teams in `~/.claude/teams/`

Get team list: !\`ls -1 ~/.claude/teams/ 2>/dev/null || echo "NO_TEAMS"\`

If result is "NO_TEAMS":
  Display: "No teams configured. Create a team using /team:create"
  Stop here

## Phase 2: Team Configuration Analysis

For each team being processed:

1. **Load team config:**
   Read team config: !\`cat ~/.claude/teams/{team-name}/config.json\`

2. **Extract team metadata:**
   - Team name
   - Creation date
   - Description (if present)
   - Total member count
   - Active member count

3. **Parse member details:**
   Get members: !\`jq -r '.members[] | "\(.name)|\(.role)|\(.model)|\(.isActive)|\(.paneId // "none")"' ~/.claude/teams/{team-name}/config.json\`

## Phase 3: Member Status Verification

For each active member (isActive = true):

1. **Verify tmux pane:**
   Check pane exists: !\`tmux list-panes -a -F "#{pane_id}" 2>/dev/null | grep -q "{paneId}" && echo "ACTIVE" || echo "MISSING"\`

2. **Check inbox activity:**
   Inbox stats: !\`stat -c '%Y %s' ~/.claude/teams/{team-name}/members/{member-name}/inbox 2>/dev/null || echo "NO_INBOX"\`
   - Convert last modified timestamp to "X minutes/hours ago"
   - Get inbox size (message count)

3. **Determine status:**
   - 🟢 Active: Pane exists AND last activity < 10 minutes ago
   - 🟡 Idle: Pane exists AND last activity 10-60 minutes ago
   - 🔴 Offline: Pane missing OR last activity > 60 minutes ago

## Phase 4: Task Progress Collection

Query tasks for this team:

1. **Find all team tasks:**
   Tasks: !\`find ~/.claude/tasks -type f -name "task.json" -exec grep -l '"team": "{team-name}"' {} \; 2>/dev/null\`

2. **For each task, extract:**
   Task data: !\`jq -r '"\(.id[0:8])|\(.subject[0:50])|\(.owner // "unassigned")|\(.status)|\(.priority // 3)"' {task-path}\`

3. **Calculate task statistics:**
   - Total tasks
   - By status: pending, in_progress, completed, cancelled
   - Completion rate: (completed / total) * 100

4. **Identify blocking relationships:**
   Blocked tasks: !\`jq -r 'select((.blockedBy | length) > 0) | .id[0:8]' {task-path}\`

## Phase 5: Dashboard Output

Generate formatted dashboard:

```markdown
# Team Status: {team-name}

**Generated:** {current-timestamp}

---

## Overview

| Metric | Value |
|--------|-------|
| **Team Name** | {team-name} |
| **Created** | {creation-date} ({days-ago} days ago) |
| **Total Members** | {total-members} |
| **Active Members** | {active-members} ({percentage}%) |
| **Total Tasks** | {total-tasks} |
| **Completed** | {completed-count} ({completion-rate}%) |
| **In Progress** | {in-progress-count} |
| **Pending** | {pending-count} |

---

## Members

| Name | Role | Model | Status | Last Activity | Pane | Inbox |
|------|------|-------|--------|---------------|------|-------|
| {member-name} | {role} | {model} | {status-icon} {status} | {last-activity} | {pane-id} | {inbox-size} msgs |
...

**Status Legend:**
- 🟢 Active: Working recently (< 10 min)
- 🟡 Idle: Online but inactive (10-60 min)
- 🔴 Offline: Not responsive (> 60 min) or pane missing

---

## Tasks

| ID | Subject | Owner | Status | Priority |
|----|---------|-------|--------|----------|
| {task-id} | {subject} | {owner} | {status-emoji} {status} | {priority}/5 |
...

**Status:**
- ✅ Completed
- 🔄 In Progress
- ⏸️ Pending
- ❌ Cancelled

{If any blocking relationships exist:}

### Blocking Chains

```
Task {id} ({status})
  ├─> Task {blocked-id} ({status})
  └─> Task {blocked-id} ({status})
```

---

## Alerts

{Check for issues and display warnings:}

⚠️ **Offline Members:** {count} member(s) offline - {list-names}

⚠️ **Blocked Tasks:** {count} task(s) waiting on dependencies

ℹ️ **Idle Members:** {count} member(s) idle - consider assigning work

✅ **System Health:** All active members running, no orphaned panes detected

---

**Dashboard End**
*Run `/team:status` to refresh or `/team:status {team-name}` for specific team*
```

## Multi-Team Output

If processing multiple teams, show compact summary for each:

```markdown
# All Active Teams

---

## {team-name-1}

**Members:** {active}/{total} active  |  **Tasks:** {in-progress} in progress, {completed} completed

| Member | Status | Current Task |
|--------|--------|-------------|
| {name} | {status-icon} | {task-subject} |
...

---

## {team-name-2}

...

---

**Total:** {total-teams} teams, {total-active-members} active members, {total-active-tasks} tasks in progress

*Run `/team:status {team-name}` for detailed view*
```

## Error Handling

- **Team not found:** List available teams with creation dates
- **Invalid config:** Show JSON parsing error and suggest validation
- **No active members:** Display "All members offline" with last activity times
- **No tasks:** Display "No tasks assigned" with suggestion to create tasks
- **Missing tmux:** Warn "tmux not available - cannot verify pane status"

## Implementation Notes

- Use bash commands for filesystem queries (faster than multiple Read calls)
- Parse JSON with jq for structured data extraction
- Calculate relative timestamps ("2m ago", "3h ago", "5d ago")
- Highlight critical issues (all members offline, high blocking rate)
- Keep output concise for multi-team view, detailed for single team
- Validate all file paths exist before reading
- Handle missing optional fields gracefully (description, priority, etc.)
