# Team Diagnosis Workflow Examples

Step-by-step examples for diagnosing common team issues.

## Scenario 1: Member Not Responding

### Symptoms

User reports: "backend-dev isn't responding to messages for the past hour"

### Diagnosis Steps

#### Step 1: Check Member Configuration

```bash
# Verify member exists and is active
jq '.members[] | select(.name == "backend-dev")' \
  ~/.claude/teams/api-dev-team/config.json
```

**Expected output:**
```json
{
  "name": "backend-dev",
  "role": "implementer",
  "model": "sonnet",
  "isActive": true,
  "tmuxPaneId": "%42"
}
```

**Issues to check:**
- ❌ `isActive: false` → Member is deactivated
- ❌ Missing `tmuxPaneId` → Process was never started
- ✅ `isActive: true` with `tmuxPaneId` → Continue investigation

#### Step 2: Verify Tmux Process

```bash
# Check if tmux pane exists
tmux list-panes -a -F "#{pane_id} #{pane_current_command}" | grep "%42"
```

**Expected output:**
```
%42 cc
```

**Diagnosis:**
- ✅ Pane found running `cc` → Process is alive
- ❌ Pane not found → **Problem: Process crashed or was killed**
- ❌ Pane found but not running `cc` → **Problem: Wrong pane or process replaced**

**If pane not found:**

```bash
# Clean up config
jq '(.members[] | select(.name == "backend-dev") | .isActive) = false |
    (.members[] | select(.name == "backend-dev") | .tmuxPaneId) = null' \
  ~/.claude/teams/api-dev-team/config.json > /tmp/config.json
mv /tmp/config.json ~/.claude/teams/api-dev-team/config.json

# Restart member
cc team-member-start api-dev-team backend-dev
```

#### Step 3: Check Inbox Delivery

```bash
# Verify messages are being written to inbox
ls -lh ~/.claude/teams/api-dev-team/members/backend-dev/inbox
tail -n 5 ~/.claude/teams/api-dev-team/members/backend-dev/inbox
```

**Expected output:**
```
-rw-r--r-- 1 user user 12K Feb 15 16:30 inbox

{"type":"message","from":"coordinator","to":"backend-dev","timestamp":"2026-02-15T16:25:00Z",...}
{"type":"task_assignment","from":"coordinator","to":"backend-dev","timestamp":"2026-02-15T16:20:00Z",...}
...
```

**Diagnosis:**
- ✅ Recent messages (last 5-10 min) → Inbox receiving messages
- ❌ No recent messages → **Problem: Messages not being sent**
- ❌ File not found → **Problem: Inbox file missing**

**If inbox missing:**

```bash
mkdir -p ~/.claude/teams/api-dev-team/members/backend-dev
touch ~/.claude/teams/api-dev-team/members/backend-dev/inbox
```

#### Step 4: Check Process Output

```bash
# View tmux pane to see what member is doing
tmux select-pane -t %42
```

**Look for:**
- Stuck on a prompt (waiting for input)
- Error messages
- Infinite loop output
- Normal Claude Code interaction

**If stuck on input:**

```bash
# Send Ctrl+C to interrupt
tmux send-keys -t %42 C-c

# Send message to resume work
echo '{"type":"message","from":"coordinator","to":"backend-dev","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","content":{"text":"Resume work on current task"}}' >> \
  ~/.claude/teams/api-dev-team/members/backend-dev/inbox
```

#### Step 5: Check Member's State File

```bash
# Check if member is processing messages
cat ~/.claude/teams/api-dev-team/members/backend-dev/state.json
```

**Expected output:**
```json
{
  "lastProcessedLine": 42,
  "lastProcessedTimestamp": "2026-02-15T16:20:00Z",
  "currentTask": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Compare with inbox:**

```bash
# Count total inbox messages
wc -l < ~/.claude/teams/api-dev-team/members/backend-dev/inbox

# Compare with lastProcessedLine
TOTAL_LINES=$(wc -l < ~/.claude/teams/api-dev-team/members/backend-dev/inbox)
PROCESSED=$(jq -r '.lastProcessedLine' ~/.claude/teams/api-dev-team/members/backend-dev/state.json)
UNPROCESSED=$((TOTAL_LINES - PROCESSED))

echo "Unprocessed messages: $UNPROCESSED"
```

**Diagnosis:**
- ✅ 0-2 unprocessed → Member is keeping up
- ⚠️ 3-10 unprocessed → Member is slow but working
- ❌ 10+ unprocessed → **Problem: Member stopped processing**

### Resolution

**Root cause identified:** Process was running but stopped processing inbox messages (stuck on a long-running task).

**Solution:**
1. Interrupt current task
2. Clear or reduce inbox backlog
3. Restart member if necessary

```bash
# Option 1: Send interrupt message
echo '{"type":"control","from":"coordinator","to":"backend-dev","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","content":{"action":"interrupt","reason":"Unresponsive for 1 hour"}}' >> \
  ~/.claude/teams/api-dev-team/members/backend-dev/inbox

# Option 2: Restart member
tmux send-keys -t %42 C-c
tmux kill-pane -t %42

# Update config
jq '(.members[] | select(.name == "backend-dev") | .isActive) = false' \
  ~/.claude/teams/api-dev-team/config.json > /tmp/config.json
mv /tmp/config.json ~/.claude/teams/api-dev-team/config.json

# Restart
cc team-member-start api-dev-team backend-dev
```

---

## Scenario 2: Task Stuck in Progress

### Symptoms

User reports: "Task #3 has been 'in progress' for 6 hours with no updates"

### Diagnosis Steps

#### Step 1: Get Task Details

```bash
# Find task file
TASK_ID="a1b2c3d4-e5f6-7890-abcd-ef1234567890"
cat ~/.claude/tasks/$TASK_ID/task.json
```

**Expected output:**
```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "subject": "Add pagination support",
  "status": "in_progress",
  "owner": "backend-dev",
  "started": "2026-02-15T10:00:00Z",
  "updated": "2026-02-15T10:30:00Z"
}
```

**Key observations:**
- Started: 10:00, Updated: 10:30 (6.5 hours ago)
- No updates for 6 hours → Potential stuck task

#### Step 2: Check Owner Status

```bash
# Is owner active?
jq '.members[] | select(.name == "backend-dev") | {name, isActive, tmuxPaneId}' \
  ~/.claude/teams/api-dev-team/config.json
```

**Diagnosis:**
- ✅ Active with tmuxPaneId → Owner should be working
- ❌ Not active → **Problem: Owner deactivated mid-task**

#### Step 3: Check Owner's Current Activity

```bash
# What messages has owner sent recently?
grep '"from":"backend-dev"' \
  ~/.claude/teams/api-dev-team/members/coordinator/inbox | \
  tail -n 5 | jq '.'
```

**Expected to see task updates:**
```json
{"type":"task_status_response","from":"backend-dev","content":{"taskId":"a1b2...","progress":70}}
```

**Diagnosis:**
- ✅ Recent task updates → Owner is working, task legitimately taking long
- ⚠️ No task updates but other messages → Owner distracted by other work
- ❌ No messages at all → Owner stuck or crashed

#### Step 4: Check Task Blockers

```bash
# Is task actually blocked?
jq -r '.blockedBy[]' ~/.claude/tasks/$TASK_ID/task.json
```

**If output shows blocking task IDs:**

```bash
# Check blocking task status
BLOCKING_ID="b2c3d4e5-f6a7-8901-bcde-f12345678901"
jq '{id, subject, status}' ~/.claude/tasks/$BLOCKING_ID/task.json
```

**Diagnosis:**
- ❌ Blocking task not completed → **Problem: Task shouldn't be in_progress**
- ✅ All blockers completed → Task validly in progress

#### Step 5: Check Task Complexity

```bash
# Review task description and estimated time
jq '{subject, description, estimatedHours, actualHours}' \
  ~/.claude/tasks/$TASK_ID/task.json
```

**Example:**
```json
{
  "subject": "Add pagination support",
  "description": "Implement offset and cursor-based pagination...",
  "estimatedHours": 8,
  "actualHours": null
}
```

**Diagnosis:**
- Estimated 8 hours, been 6 hours → Within normal range
- No actualHours tracking → Can't verify progress

#### Step 6: Request Status Update

```bash
# Send status request to owner
echo '{"type":"task_status_request","from":"coordinator","to":"backend-dev","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","requestId":"req-'$(uuidgen)'","content":{"taskId":"'$TASK_ID'"}}' >> \
  ~/.claude/teams/api-dev-team/members/backend-dev/inbox

# Wait 5 minutes, then check for response
sleep 300
grep "req-" ~/.claude/teams/api-dev-team/members/coordinator/inbox | tail -n 1
```

**Expected response:**
```json
{
  "type":"task_status_response",
  "from":"backend-dev",
  "requestId":"req-...",
  "content":{
    "taskId":"a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "progress":70,
    "notes":"Implementing cursor-based pagination, encountering edge cases"
  }
}
```

**Diagnosis:**
- ✅ Response received with progress → Task actively being worked on
- ❌ No response → Owner not processing messages (see Scenario 1)

### Resolution

**Root cause identified:** Task is legitimately complex and taking longer than estimated, but owner is actively working.

**Solution:**
1. Update task with progress notes
2. Adjust estimated hours if needed
3. No intervention required

```bash
# Update task with actual progress
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
jq --arg now "$NOW" \
  '.updated = $now | .actualHours = 6.5' \
  ~/.claude/tasks/$TASK_ID/task.json > /tmp/task.json
mv /tmp/task.json ~/.claude/tasks/$TASK_ID/task.json
```

**If intervention needed (task actually stuck):**

```bash
# Reassign task
NEW_OWNER="backend-dev-2"

jq --arg owner "$NEW_OWNER" --arg now "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  '.owner = $owner | .updated = $now' \
  ~/.claude/tasks/$TASK_ID/task.json > /tmp/task.json
mv /tmp/task.json ~/.claude/tasks/$TASK_ID/task.json

# Notify new owner
echo '{"type":"task_assignment","from":"coordinator","to":"'$NEW_OWNER'","timestamp":"'$(date -u +%Y-%m-%dT%H:%M:%SZ)'","content":{"taskId":"'$TASK_ID'","subject":"Add pagination support (reassigned)"}}' >> \
  ~/.claude/teams/api-dev-team/members/$NEW_OWNER/inbox
```

---

## Scenario 3: Circular Task Dependencies

### Symptoms

Dashboard shows tasks blocked but nothing progressing.

### Diagnosis Steps

#### Step 1: List All Task Dependencies

```bash
# Extract all blocking relationships
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -s '[.[] | {id, subject, blocks, blockedBy}]'
```

**Example output:**
```json
[
  {"id":"task-a","subject":"A","blocks":["task-b"],"blockedBy":["task-c"]},
  {"id":"task-b","subject":"B","blocks":["task-c"],"blockedBy":["task-a"]},
  {"id":"task-c","subject":"C","blocks":["task-a"],"blockedBy":["task-b"]}
]
```

**Visual representation:**
```
Task A blocks Task B
Task B blocks Task C
Task C blocks Task A
→ Circular dependency!
```

#### Step 2: Detect Cycles Programmatically

```bash
# Python script to detect cycles
python3 << 'EOF'
import json
import sys
from pathlib import Path

def find_cycles(tasks):
    def dfs(task_id, path, visited):
        if task_id in path:
            cycle_start = path.index(task_id)
            return path[cycle_start:]
        if task_id in visited:
            return None

        path.append(task_id)
        visited.add(task_id)

        task = next((t for t in tasks if t['id'] == task_id), None)
        if task and task.get('blockedBy'):
            for blocker in task['blockedBy']:
                result = dfs(blocker, path[:], visited)
                if result:
                    return result

        return None

    visited = set()
    for task in tasks:
        cycle = dfs(task['id'], [], visited)
        if cycle:
            return cycle

    return None

# Load all tasks
tasks = []
for task_file in Path.home().glob('.claude/tasks/*/task.json'):
    with open(task_file) as f:
        tasks.append(json.load(f))

cycle = find_cycles(tasks)
if cycle:
    print("Circular dependency detected:")
    for task_id in cycle:
        task = next(t for t in tasks if t['id'] == task_id)
        print(f"  {task_id[:8]}... ({task['subject']})")
else:
    print("No circular dependencies found")
EOF
```

#### Step 3: Analyze Blocking Chain

```bash
# For each pending task, trace why it can't start
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -r 'select(.status == "pending") |
    .id + " blocked by: " + (.blockedBy | join(", "))'
```

#### Step 4: Identify Root Cause

Review blocking reasons:
- Are dependencies legitimate?
- Can any be removed?
- Should tasks be reordered?

### Resolution

**Root cause identified:** Task A and Task B have mutual blocking relationship (both block each other).

**Solution:** Break the cycle by removing one blocking relationship.

```bash
TASK_A="task-a-uuid"
TASK_B="task-b-uuid"

# Remove "Task B blocks Task A" relationship
jq --arg taskB "$TASK_B" \
  '.blockedBy = [.blockedBy[] | select(. != $taskB)]' \
  ~/.claude/tasks/$TASK_A/task.json > /tmp/task.json
mv /tmp/task.json ~/.claude/tasks/$TASK_A/task.json

jq --arg taskA "$TASK_A" \
  '.blocks = [.blocks[] | select(. != $taskA)]' \
  ~/.claude/tasks/$TASK_B/task.json > /tmp/task.json
mv /tmp/task.json ~/.claude/tasks/$TASK_B/task.json

echo "Circular dependency broken: Task A no longer blocked by Task B"
```

---

## Scenario 4: Orphaned Tmux Panes

### Symptoms

`tmux list-panes` shows more panes than team members.

### Diagnosis Steps

#### Step 1: List All Configured Pane IDs

```bash
# Extract tmuxPaneIds from all team configs
find ~/.claude/teams -name "config.json" -exec cat {} \; | \
  jq -r '.members[]? | select(.tmuxPaneId != null) | .tmuxPaneId' | \
  sort > /tmp/config-panes.txt
```

#### Step 2: List All Tmux Panes

```bash
tmux list-panes -a -F "#{pane_id}" | sort > /tmp/tmux-panes.txt
```

#### Step 3: Find Orphans

```bash
# Panes in tmux but not in any config
comm -13 /tmp/config-panes.txt /tmp/tmux-panes.txt
```

**Example output:**
```
%99
%100
%101
```

#### Step 4: Inspect Orphaned Panes

```bash
# Check what each orphan is running
for pane in $(comm -13 /tmp/config-panes.txt /tmp/tmux-panes.txt); do
  echo "Pane: $pane"
  tmux list-panes -a -F "#{pane_id} #{session_name}:#{window_index}.#{pane_index} cmd=#{pane_current_command}" | \
    grep "$pane"
done
```

**Example output:**
```
Pane: %99
%99 old-session:0.0 cmd=cc

Pane: %100
%100 test-session:1.2 cmd=bash

Pane: %101
%101 team-alpha:2.0 cmd=zsh
```

**Diagnosis:**
- `%99` running `cc` → Likely old team member
- `%100` running `bash` → Not a Claude process
- `%101` running `zsh` → Probably interactive shell, not team member

### Resolution

**Root cause identified:** Panes %99 is from deactivated team member, others are unrelated.

**Solution:** Clean up orphaned Claude Code panes.

```bash
# Kill orphaned cc panes
for pane in %99; do
  echo "Killing pane: $pane"
  tmux kill-pane -t $pane
done

# Leave non-cc panes alone (they might be in use for other purposes)
```

**Create cleanup script for future use:**

```bash
cat > ~/cleanup-orphaned-panes.sh << 'EOF'
#!/bin/bash
# Find and clean up orphaned Claude Code team member panes

CONFIG_PANES=$(find ~/.claude/teams -name "config.json" -exec cat {} \; | \
  jq -r '.members[]? | select(.tmuxPaneId != null) | .tmuxPaneId' | sort)

TMUX_PANES=$(tmux list-panes -a -F "#{pane_id} #{pane_current_command}" | \
  grep " cc$" | cut -d' ' -f1 | sort)

for pane in $TMUX_PANES; do
  if ! echo "$CONFIG_PANES" | grep -q "$pane"; then
    echo "Orphaned pane found: $pane"
    read -p "Kill this pane? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
      tmux kill-pane -t $pane
      echo "Pane $pane killed"
    fi
  fi
done
EOF

chmod +x ~/cleanup-orphaned-panes.sh
```

---

## Diagnostic Checklist

Use this checklist when encountering team issues:

### Member Issues

- [ ] Member exists in config.json
- [ ] Member isActive is true
- [ ] Member has tmuxPaneId assigned
- [ ] Tmux pane with that ID exists
- [ ] Pane is running `cc` command
- [ ] Inbox file exists and is writable
- [ ] Recent messages in inbox
- [ ] State file shows recent activity

### Task Issues

- [ ] Task file exists in ~/.claude/tasks/
- [ ] Task has valid status (pending/in_progress/completed)
- [ ] If in_progress, owner is active
- [ ] All blockedBy tasks are completed
- [ ] No circular dependencies
- [ ] Estimated vs actual time reasonable
- [ ] Recent update timestamp

### Team Health

- [ ] No orphaned tmux panes
- [ ] All active members have valid tmuxPaneIds
- [ ] Inbox files under size threshold (< 10MB)
- [ ] No task dependency cycles
- [ ] Member utilization balanced
- [ ] Blocking rate acceptable (< 30%)

## Quick Diagnosis Commands

```bash
# Team overview
jq '{team: .name, members: .members | length, active: [.members[] | select(.isActive)] | length}' \
  ~/.claude/teams/*/config.json

# Find stuck tasks (in_progress > 24h)
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -r 'select(.status == "in_progress" and
    (now - (.started | fromdateiso8601)) > 86400) |
    {id, subject, owner, hours: ((now - (.started | fromdateiso8601)) / 3600)}'

# List all blockers
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -s 'map(select((.blockedBy | length) > 0)) |
    .[] | {id: .id[:8], subject, blockedBy: .blockedBy | map(.[:8])}'

# Check orphaned panes
comm -13 \
  <(find ~/.claude/teams -name "config.json" -exec cat {} \; | jq -r '.members[]? | .tmuxPaneId' | sort) \
  <(tmux list-panes -a -F "#{pane_id}" | sort)
```
