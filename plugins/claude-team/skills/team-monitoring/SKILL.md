---
name: team-monitoring
description: This skill should be used when the user asks to "check team status", "show team dashboard", "monitor team progress", "view member status", "check task progress", "팀 상태 확인", "대시보드 보여줘", or needs to track agent team activity and task completion.
version: 0.1.0
---

# Team Monitoring

Monitor agent team status, member activity, and task progress through comprehensive dashboard views.

## Overview

This skill provides methods to track and visualize:
- Active teams and their configurations
- Member status and activity
- Task progress and dependencies
- Real-time team activity monitoring

## Core Concepts

### Team Directory Structure

All team data is stored in `~/.claude/teams/`:

```
~/.claude/teams/
├── team-alpha/
│   ├── config.json          # Team configuration and member list
│   └── members/
│       ├── worker-1/
│       │   └── inbox        # Message queue for member
│       └── tester/
│           └── inbox
└── team-beta/
    └── config.json
```

### Task Storage

Tasks are managed in `~/.claude/tasks/`:

```
~/.claude/tasks/
├── {uuid-1}/
│   ├── task.json           # Task metadata (subject, status, owner)
│   └── messages/           # Task-related messages
└── {uuid-2}/
    └── task.json
```

## Querying Team Status

### 1. List Active Teams

Scan the teams directory to identify all active teams:

```bash
ls -1 ~/.claude/teams/
```

For each team directory, read the configuration:

```bash
cat ~/.claude/teams/{team-name}/config.json
```

Extract from config.json:
- `name`: Team identifier
- `created`: Creation timestamp
- `members`: Array of member objects
- Member fields: `name`, `role`, `model`, `isActive`, `paneId`

### 2. Check Member Status

For each member, determine status using multiple signals:

**Active Status (from config.json):**
```bash
jq '.members[] | select(.name == "worker-1") | .isActive' ~/.claude/teams/{team-name}/config.json
```

**Process Status (tmux verification):**
```bash
tmux list-panes -a -F "#{pane_id} #{session_name}:#{window_index}.#{pane_index} cmd=#{pane_current_command}"
```

Match the `paneId` from config.json with tmux output to verify the process exists.

**Recent Activity (inbox analysis):**
```bash
stat -c '%Y' ~/.claude/teams/{team-name}/members/{member-name}/inbox
ls -lh ~/.claude/teams/{team-name}/members/{member-name}/inbox
```

Check file modification time and size to determine recent message activity.

### 3. Query Task Progress

Use Claude Code's task management tools:

**List all tasks:**
```
TaskList
```

**Get task details:**
```
TaskGet(taskId)
```

Task JSON structure:
```json
{
  "id": "uuid",
  "subject": "Implement API endpoint",
  "description": "Create REST API for user management",
  "status": "in_progress",
  "owner": "worker-1",
  "blocks": ["uuid-2"],
  "blockedBy": [],
  "created": "timestamp",
  "updated": "timestamp"
}
```

**Status values:**
- `pending`: Not yet started
- `in_progress`: Currently being worked on
- `completed`: Finished

## Dashboard Output Format

Generate structured dashboards using markdown tables:

### Team Overview

```markdown
## Team: {team-name}
Created: {creation-date} | Members: {active-count}/{total-count} | Tasks: {completed}/{total}

### Members
| Name | Role | Model | Status | Last Activity | Current Task |
|------|------|-------|--------|---------------|-------------|
| worker-1 | implementer | sonnet | Active | 2m ago | Task #1: Implement API |
| tester | tester | haiku | Idle | 1h ago | - |
| reviewer | reviewer | opus | Offline | 3h ago | - |

### Tasks
| # | Subject | Owner | Status | Progress | Blocked By |
|---|---------|-------|--------|----------|-----------|
| 1 | Implement API | worker-1 | in_progress | 60% | - |
| 2 | Write tests | tester | pending | 0% | Task #1 |
| 3 | Code review | reviewer | pending | 0% | Task #2 |
```

### Member Status Indicators

Determine status based on multiple signals:

| Status | Criteria |
|--------|----------|
| **Active** | isActive=true, tmux pane exists, inbox modified <5min |
| **Idle** | isActive=true, tmux pane exists, inbox modified >5min |
| **Offline** | isActive=false OR tmux pane not found |
| **Responding** | Inbox size growing, recent modifications |

### Task Progress Calculation

Calculate progress based on task dependency completion:

```
Progress = (completed_dependencies / total_dependencies) * 100
```

For tasks with no dependencies, estimate based on:
- Time since started
- Owner's last activity
- Inbox message count related to task

## Real-Time Monitoring Patterns

### Watch for Message Activity

Monitor inbox files for size and modification time changes:

```bash
watch -n 5 'ls -lh ~/.claude/teams/*/members/*/inbox'
```

Increasing file size indicates active message exchange.

### Track Configuration Changes

Monitor config.json for member activation/deactivation:

```bash
watch -n 10 'jq ".members[] | {name, isActive}" ~/.claude/teams/*/config.json'
```

### Detect Idle Members

Find members with no recent inbox activity:

```bash
find ~/.claude/teams/*/members/*/inbox -type f -mmin +30
```

This identifies inboxes not modified in the last 30 minutes.

### Monitor Task Updates

Track task.json file modifications:

```bash
find ~/.claude/tasks/*/task.json -type f -mmin -5
```

Recent modifications indicate active task work.

## Problem Diagnosis

### Member Not Responding

**Diagnosis steps:**

1. **Check tmux pane exists:**
   ```bash
   tmux list-panes -a | grep {paneId}
   ```

   If not found: Member process crashed or was killed.

2. **Verify inbox delivery:**
   ```bash
   tail -n 20 ~/.claude/teams/{team}/members/{member}/inbox
   ```

   Check if messages are being written to inbox.

3. **Check process command:**
   ```bash
   tmux list-panes -a -F "#{pane_id} cmd=#{pane_current_command}"
   ```

   Verify the pane is running `cc` command.

**Solutions:**
- Restart member if pane missing
- Clear inbox if corrupted
- Check for error messages in tmux pane output

### Task Blocked

**Diagnosis steps:**

1. **Trace blocking chain:**
   ```bash
   TaskGet(taskId) | jq '.blockedBy'
   ```

   Follow the chain: Task A blocked by Task B blocked by Task C...

2. **Identify blocking owner:**
   ```bash
   TaskGet(blockingTaskId) | jq '{owner, status, subject}'
   ```

3. **Check owner activity:**
   - Verify owner is active
   - Check owner's inbox for task-related messages
   - Look for idle_notification messages

**Solutions:**
- Send message to blocking task owner
- Reassign blocking task if owner offline
- Remove blocking relationship if outdated

### Orphaned Tmux Panes

**Detection:**

Find panes not in any team config:

```bash
# Get all pane IDs from configs
jq -r '.members[].paneId' ~/.claude/teams/*/config.json | sort > /tmp/config-panes

# Get all tmux pane IDs
tmux list-panes -a -F "#{pane_id}" | sort > /tmp/tmux-panes

# Find orphans (in tmux but not in config)
comm -13 /tmp/config-panes /tmp/tmux-panes
```

**Cleanup:**

```bash
tmux kill-pane -t {orphaned-pane-id}
```

### Idle Notification Handling

When a member sends `idle_notification`:

1. **Extract from inbox:**
   ```bash
   grep "idle_notification" ~/.claude/teams/{team}/members/{member}/inbox
   ```

2. **Determine reason:**
   - No assigned tasks
   - Waiting for blocking tasks
   - Completed all work

3. **Take action:**
   - Assign new tasks if available
   - Send waiting status update
   - Deactivate if no work available

## Advanced Monitoring

### Activity Heatmap

Generate a visual representation of team activity:

```markdown
## Activity (last 24h)

Member      | 00-06 | 06-12 | 12-18 | 18-24 | Total Msgs
------------|-------|-------|-------|-------|------------
worker-1    | ░░░░  | ████  | ████  | ██░░  | 47
tester      | ░░░░  | ░░██  | ████  | ░░░░  | 23
reviewer    | ░░░░  | ░░░░  | ░░██  | ░░░░  | 8
```

Calculate from inbox file modification times and size changes.

### Task Dependency Graph

Visualize task blocking relationships:

```markdown
## Task Dependencies

Task #1 (in_progress)
  │
  ├─> Task #2 (pending)
  │     │
  │     └─> Task #5 (pending)
  │
  └─> Task #3 (pending)
        │
        └─> Task #4 (pending)
```

Build from `blocks` and `blockedBy` fields in task.json files.

### Performance Metrics

Track team efficiency:

```markdown
## Performance Metrics

- **Throughput**: 12 tasks completed / week
- **Average Task Time**: 2.3 hours
- **Blocking Rate**: 18% tasks blocked
- **Member Utilization**:
  - worker-1: 85% (active 6.8h/day)
  - tester: 62% (active 5.0h/day)
  - reviewer: 41% (active 3.3h/day)
```

Calculate from:
- Task completion timestamps
- Member activity durations (inbox modification spans)
- Blocking relationship frequency

## Command Examples

### Show complete team dashboard

```
Show me the full dashboard for team-alpha
```

Output includes: team overview, member table, task table, recent activity.

### Check specific member status

```
What's the status of worker-1 in team-alpha?
```

Output includes: active status, current task, recent activity, inbox summary.

### Monitor task progress

```
Show me all blocked tasks and their blocking chains
```

Trace all `blockedBy` relationships and identify resolution owners.

### Diagnose team issues

```
Team-beta seems stuck, help me diagnose
```

Check for: offline members, circular blocking, orphaned panes, stale tasks.

## Best Practices

### Regular Monitoring

- Check team dashboard at project start/end
- Monitor task progress hourly during active work
- Review member activity when delays occur

### Proactive Diagnosis

- Identify blocking chains before they accumulate
- Detect idle members early
- Clean up orphaned panes regularly

### Performance Optimization

- Balance task assignments across members
- Minimize blocking relationships
- Deactivate unused members to reduce overhead

### Data Hygiene

- Archive completed tasks periodically
- Rotate or truncate large inbox files
- Remove obsolete team configurations

## Troubleshooting

### Dashboard shows incorrect member count

**Cause:** config.json out of sync with actual tmux panes.

**Fix:** Reconcile config with `tmux list-panes` output and update config.json.

### Tasks show as "in_progress" but no activity

**Cause:** Owner crashed or status not updated.

**Fix:** Check owner status, restart if needed, or reassign task.

### "File not found" errors

**Cause:** Team or member directory missing.

**Fix:** Verify team exists with `ls ~/.claude/teams/`, recreate if needed.

## Additional Resources

### Reference Files
- **`references/config-schema.md`** - Complete config.json schema
- **`references/task-schema.md`** - Task JSON structure and fields
- **`references/inbox-protocol.md`** - Message format and delivery mechanism

### Example Files
- **`examples/dashboard-full.md`** - Complete dashboard example
- **`examples/diagnosis-workflow.md`** - Step-by-step diagnosis examples

### Scripts
- **`scripts/check-orphans.sh`** - Find and clean orphaned tmux panes
- **`scripts/generate-dashboard.sh`** - Automated dashboard generation
- **`scripts/task-dependency-graph.sh`** - Generate visual dependency graphs
