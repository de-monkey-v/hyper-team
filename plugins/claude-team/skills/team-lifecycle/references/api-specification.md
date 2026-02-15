# Agent Teams API Specification

Complete technical specification for all Agent Teams API tools and their parameters.

## TeamCreate Tool

### Purpose
Initialize a new team structure with configuration files and directories.

### Parameters

| Parameter | Type | Required | Description | Validation |
|-----------|------|----------|-------------|------------|
| `team_name` | string | Yes | Team identifier | Kebab-case, 3-64 chars, lowercase only |
| `description` | string | Yes | Team purpose | 1-500 chars |

### Returns

```typescript
{
  success: boolean;
  teamPath: string;  // "~/.claude/teams/{team-name}"
  configPath: string; // "~/.claude/teams/{team-name}/config.json"
}
```

### File System Changes

**Created Directories:**
```
~/.claude/teams/{team-name}/
~/.claude/teams/{team-name}/inboxes/
```

**Created Files:**
```json
// ~/.claude/teams/{team-name}/config.json
{
  "name": "team-name",
  "description": "Team purpose description",
  "members": [],
  "createdAt": "2026-02-16T10:30:00.000Z",
  "metadata": {}
}
```

### Error Conditions

| Error | Cause | Solution |
|-------|-------|----------|
| `TEAM_EXISTS` | Team name already in use | Choose different name or delete existing |
| `INVALID_NAME` | Name contains invalid chars | Use kebab-case, lowercase only |
| `PERMISSION_DENIED` | Cannot write to ~/.claude/ | Check file permissions |

### Examples

**Basic Team Creation:**
```javascript
TeamCreate({
  team_name: "research-team",
  description: "Q4 sales analysis team"
})
```

**Specialized Team:**
```javascript
TeamCreate({
  team_name: "code-review-team",
  description: "Automated code review and refactoring team"
})
```

## Task Tool (Teammate Spawning)

### Purpose
Spawn a new teammate process in an isolated tmux pane.

### Parameters

| Parameter | Type | Required | Description | Default |
|-----------|------|----------|-------------|---------|
| `team_name` | string | Yes | Target team name | - |
| `name` | string | Yes | Unique teammate identifier | - |
| `model` | string | Yes | Claude model ("haiku", "sonnet", "opus") | - |
| `prompt` | string | Yes | System prompt/instructions | - |
| `subagent_type` | string | Yes | Agent type (use "general-purpose") | - |
| `run_in_background` | boolean | No | Keep process running | false |

### Execution Command

The Task tool generates and executes this command:

```bash
claude \
  --agent-id {name}@{team_name} \
  --agent-name {name} \
  --team-name {team_name} \
  --agent-color {random_color} \
  --parent-session-id {session_id} \
  --agent-type general-purpose \
  --dangerously-skip-permissions \
  --model {model}
```

### Backend Execution

**Tmux Integration:**
```bash
# Split current pane
tmux split-window -h -p 50

# Execute claude command in new pane
tmux send-keys -t {pane_id} "{command}" Enter

# Store pane ID for later management
```

### File System Changes

**config.json Update:**
```json
{
  "members": [
    {
      "agentId": "worker-1@research-team",
      "name": "worker-1",
      "agentType": "general-purpose",
      "model": "haiku",
      "prompt": "You are a research assistant...",
      "color": "blue",
      "tmuxPaneId": "%88",
      "backendType": "tmux",
      "isActive": true,
      "spawnedAt": "2026-02-16T10:35:00.000Z"
    }
  ]
}
```

**Inbox Creation:**
```json
// ~/.claude/teams/{team}/inboxes/{name}.json
[
  {
    "from": "system",
    "text": "{prompt}",
    "summary": "Initial system prompt",
    "timestamp": "2026-02-16T10:35:00.000Z",
    "color": "system",
    "read": false
  }
]
```

### Color Assignment

Random color selection from pool:
- blue
- green
- yellow
- magenta
- cyan
- red

### Error Conditions

| Error | Cause | Solution |
|-------|-------|----------|
| `TEAM_NOT_FOUND` | Team doesn't exist | Create team first with TeamCreate |
| `DUPLICATE_NAME` | Teammate name exists | Use unique name |
| `TMUX_ERROR` | Cannot create pane | Check tmux availability |
| `INVALID_MODEL` | Unknown model name | Use haiku/sonnet/opus |

### Examples

**Basic Analyst:**
```javascript
Task({
  team_name: "research-team",
  name: "analyst-1",
  model: "haiku",
  prompt: "You are a data analyst specializing in sales trends. Analyze data and provide insights.",
  subagent_type: "general-purpose",
  run_in_background: true
})
```

**Code Reviewer:**
```javascript
Task({
  team_name: "review-team",
  name: "reviewer",
  model: "sonnet",
  prompt: "You are a senior code reviewer. Review code for bugs, performance, and best practices.",
  subagent_type: "general-purpose",
  run_in_background: true
})
```

## SendMessage Tool

### Purpose
Send messages between team members or broadcast to all members.

### Message Type: "message" (Direct Message)

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | "message" | Yes | Message type identifier |
| `team_name` | string | Yes | Target team |
| `recipient` | string | Yes | Teammate name |
| `content` | string | Yes | Message content |
| `summary` | string | Yes | Brief summary for notifications |

**Inbox Format:**
```json
{
  "from": "sender-name",
  "text": "Full message content here",
  "summary": "Brief summary",
  "timestamp": "2026-02-16T10:40:00.000Z",
  "color": "blue",
  "read": false
}
```

**Example:**
```javascript
SendMessage({
  type: "message",
  team_name: "research-team",
  recipient: "analyst-1",
  content: "Please analyze the sales data in /data/q4-sales.csv and identify top 3 trends",
  summary: "Q4 sales trend analysis request"
})
```

### Message Type: "broadcast"

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | "broadcast" | Yes | Message type identifier |
| `team_name` | string | Yes | Target team |
| `content` | string | Yes | Message content |
| `summary` | string | Yes | Brief summary for notifications |

**Behavior:**
- Sends message to ALL active team members
- Each member receives copy in their inbox
- High token cost - use sparingly

**Example:**
```javascript
SendMessage({
  type: "broadcast",
  team_name: "research-team",
  content: "Team meeting in 10 minutes. Please prepare status updates.",
  summary: "Team meeting notification"
})
```

### Message Type: "shutdown_request"

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | "shutdown_request" | Yes | Message type identifier |
| `team_name` | string | Yes | Target team |
| `recipient` | string | Yes | Teammate to shutdown |
| `content` | string | Yes | Shutdown reason/message |

**Workflow:**
1. Sender sends shutdown_request
2. Recipient receives in inbox
3. Recipient processes and responds with shutdown_response
4. System executes shutdown if approved

**Example:**
```javascript
SendMessage({
  type: "shutdown_request",
  team_name: "research-team",
  recipient: "analyst-1",
  content: "Analysis complete. Thank you for your work. Shutting down."
})
```

### Message Type: "shutdown_response"

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | "shutdown_response" | Yes | Message type identifier |
| `request_id` | string | Yes | ID from shutdown_request |
| `approve` | boolean | Yes | Approve or deny shutdown |

**Approval Flow:**
```json
// If approve: true
{
  "type": "shutdown_approved",
  "requestId": "req-abc123",
  "paneId": "%88",
  "backendType": "tmux"
}
```

**System Actions on Approval:**
1. Kill tmux pane: `tmux kill-pane -t {paneId}`
2. Update config.json: `isActive: false`
3. Archive inbox messages (optional)

**Example:**
```javascript
SendMessage({
  type: "shutdown_response",
  request_id: "req-abc123",
  approve: true
})
```

### Message Type: "plan_approval_response"

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | "plan_approval_response" | Yes | Message type identifier |
| `request_id` | string | Yes | ID from plan approval request |
| `approve` | boolean | Yes | Approve or deny plan |
| `feedback` | string | No | Optional feedback/modifications |

**Example:**
```javascript
SendMessage({
  type: "plan_approval_response",
  request_id: "plan-xyz789",
  approve: true,
  feedback: "Looks good, proceed with implementation"
})
```

### Error Conditions

| Error | Cause | Solution |
|-------|-------|----------|
| `TEAM_NOT_FOUND` | Team doesn't exist | Verify team name |
| `RECIPIENT_NOT_FOUND` | Teammate doesn't exist | Check teammate name in config.json |
| `RECIPIENT_INACTIVE` | Teammate shutdown | Cannot message inactive teammates |
| `INVALID_TYPE` | Unknown message type | Use valid type (message/broadcast/shutdown_request/etc) |

## TeamDelete Tool

### Purpose
Delete team structure and all associated files.

### Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `team_name` | string | Yes | Team to delete |

### Prerequisites

**CRITICAL:** All teammates must be shutdown before deletion.

**Verification Command:**
```bash
jq '.members[] | select(.isActive == true)' ~/.claude/teams/{team}/config.json
```

If output is not empty, shutdown required teammates first.

### File System Changes

**Deleted Directories:**
```
~/.claude/teams/{team-name}/       # Team config and inboxes
~/.claude/tasks/{team-name}/        # Task artifacts and logs
```

**Deleted Files:**
- config.json
- All inbox files (inboxes/*.json)
- Task execution logs
- Any team-specific metadata

### Error Conditions

| Error | Cause | Solution |
|-------|-------|----------|
| `TEAM_NOT_FOUND` | Team doesn't exist | Verify team name |
| `ACTIVE_MEMBERS` | Teammates still active | Shutdown all teammates first |
| `PERMISSION_DENIED` | Cannot delete files | Check file permissions |

### Examples

**Safe Deletion Flow:**
```javascript
// 1. Verify no active members
const config = readJSON('~/.claude/teams/research-team/config.json');
const activeMembers = config.members.filter(m => m.isActive);

if (activeMembers.length > 0) {
  // 2. Shutdown each active member
  activeMembers.forEach(member => {
    SendMessage({
      type: "shutdown_request",
      team_name: "research-team",
      recipient: member.name,
      content: "Preparing team deletion"
    });
  });

  // 3. Wait for shutdowns to complete
  // ...
}

// 4. Delete team
TeamDelete({ team_name: "research-team" });
```

## Idle Notification System

### Purpose
Signal teammate availability and enable polling-based wake-up.

### Idle Notification Format

```json
{
  "type": "idle_notification",
  "from": "teammate-name",
  "idleReason": "available",
  "timestamp": "2026-02-16T10:45:00.000Z"
}
```

### Idle Reasons

| Reason | Description | Next Action |
|--------|-------------|-------------|
| `available` | Ready for work | Wait for messages |
| `waiting_response` | Waiting for reply | Poll for response |
| `task_complete` | Finished work | Idle until new task |

### Polling Mechanism

**Inbox Polling Loop:**
```bash
while is_idle; do
  messages=$(jq '[.[] | select(.read == false)]' inbox.json)
  if [ "$messages" != "[]" ]; then
    process_messages
    break
  fi
  sleep 1
done
```

### Wake-up Flow

1. Teammate enters idle state
2. Sends idle_notification to coordinator
3. Begins inbox polling (every 1 second)
4. New message arrives in inbox
5. Polling detects unread message
6. Teammate wakes and processes message

## Advanced Features

### Custom Metadata

Teams and teammates can store custom metadata:

**Team Metadata:**
```json
{
  "name": "research-team",
  "description": "...",
  "metadata": {
    "project": "Q4-analysis",
    "budget": 1000,
    "priority": "high"
  }
}
```

**Teammate Metadata:**
```json
{
  "agentId": "analyst@research-team",
  "name": "analyst",
  "metadata": {
    "specialization": "sales-trends",
    "completed_tasks": 5,
    "avg_response_time": 30
  }
}
```

### Message Filtering

Filter inbox messages by criteria:

```bash
# Unread messages only
jq '[.[] | select(.read == false)]' inbox.json

# From specific sender
jq '[.[] | select(.from == "coordinator")]' inbox.json

# Recent messages (last hour)
jq --arg cutoff "$(date -u -d '1 hour ago' +%Y-%m-%dT%H:%M:%S)" \
  '[.[] | select(.timestamp > $cutoff)]' inbox.json
```

### Tmux Pane Management

**List All Team Panes:**
```bash
tmux list-panes -a -F "#{pane_id} #{pane_current_command}" | grep claude
```

**Focus Specific Pane:**
```bash
tmux select-pane -t {paneId}
```

**Monitor Pane Output:**
```bash
tmux capture-pane -t {paneId} -p
```

## Performance Considerations

### Message Volume

**Direct Message Cost:**
- Single recipient processes message
- Token cost: 1x message content

**Broadcast Cost:**
- All N members process message
- Token cost: Nx message content
- Use direct messages when possible

### Polling Frequency

**Default: 1 second intervals**

**Trade-offs:**
- Higher frequency → Faster response, more CPU
- Lower frequency → Slower response, less CPU

**Optimization:**
```bash
# Exponential backoff polling
sleep_time=1
max_sleep=60

while is_idle; do
  if has_new_messages; then
    process_messages
    break
  fi
  sleep $sleep_time
  sleep_time=$((sleep_time * 2))
  [ $sleep_time -gt $max_sleep ] && sleep_time=$max_sleep
done
```

### Config.json Locking

**Concurrent Access:**

Multiple processes may access config.json simultaneously. Use file locking:

```bash
# Acquire lock
exec 200>/tmp/team-config.lock
flock -x 200

# Modify config.json
jq '.members += [{...}]' config.json > config.json.tmp
mv config.json.tmp config.json

# Release lock
flock -u 200
```

## Migration and Compatibility

### Version Compatibility

**Config Schema Versions:**
- v1.0.0: Initial release
- Future versions: Backward compatible

**Migration Strategy:**
```bash
# Check schema version
jq '.schemaVersion' config.json

# Migrate if needed
migrate_config_v1_to_v2 config.json
```

### Backup Recommendations

**Before Deletion:**
```bash
# Backup team data
tar -czf research-team-backup.tar.gz ~/.claude/teams/research-team/

# Verify backup
tar -tzf research-team-backup.tar.gz
```

## Security Considerations

### Permission Model

**File Permissions:**
```bash
chmod 700 ~/.claude/teams/        # Owner only
chmod 600 ~/.claude/teams/*/config.json  # Owner read/write
chmod 600 ~/.claude/teams/*/inboxes/*.json
```

### Sensitive Data

**Avoid in Messages:**
- API keys
- Passwords
- Personal identifiable information

**Use Environment Variables:**
```bash
# Pass secrets via environment
export API_KEY="secret"
claude --agent-id worker@team ...
```

### Tmux Session Isolation

**Security Boundary:**
- Each teammate runs in isolated tmux pane
- Shared file system access
- No network isolation by default

**Enhanced Isolation:**
```bash
# Run in separate tmux session per team
tmux new-session -d -s team-{name}
tmux split-window -t team-{name}
```

## See Also

- **SKILL.md** - Core lifecycle workflows
- **file-formats.md** - Detailed file schemas
- **advanced-patterns.md** - Complex workflow examples
