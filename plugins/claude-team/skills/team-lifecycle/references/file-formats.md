# File Formats and Schemas

Detailed specifications for all file formats used in the Agent Teams API.

## config.json

Primary team configuration and member registry.

### Location
```
~/.claude/teams/{team-name}/config.json
```

### Schema

```typescript
interface TeamConfig {
  name: string;                    // Team identifier (kebab-case)
  description: string;             // Team purpose
  members: TeamMember[];           // Array of team members
  createdAt: string;               // ISO 8601 timestamp
  schemaVersion?: string;          // Config schema version
  metadata?: Record<string, any>;  // Custom team metadata
}

interface TeamMember {
  agentId: string;                 // "{name}@{team}"
  name: string;                    // Unique member name
  agentType: string;               // "general-purpose" | "specialized"
  model: string;                   // "haiku" | "sonnet" | "opus"
  prompt: string;                  // System prompt
  color: string;                   // Terminal color (blue|green|yellow|magenta|cyan|red)
  tmuxPaneId: string;              // Tmux pane identifier (e.g., "%88")
  backendType: string;             // "tmux" (future: "docker", "k8s")
  isActive: boolean;               // Active/shutdown status
  spawnedAt?: string;              // ISO 8601 timestamp
  shutdownAt?: string;             // ISO 8601 timestamp
  metadata?: Record<string, any>;  // Custom member metadata
}
```

### Example: Empty Team

```json
{
  "name": "research-team",
  "description": "Q4 sales analysis team",
  "members": [],
  "createdAt": "2026-02-16T10:30:00.000Z",
  "schemaVersion": "1.0.0"
}
```

### Example: Team with Members

```json
{
  "name": "research-team",
  "description": "Q4 sales analysis team",
  "members": [
    {
      "agentId": "analyst-1@research-team",
      "name": "analyst-1",
      "agentType": "general-purpose",
      "model": "haiku",
      "prompt": "You are a data analyst specializing in sales trends. Analyze data carefully and provide actionable insights with supporting evidence.",
      "color": "blue",
      "tmuxPaneId": "%88",
      "backendType": "tmux",
      "isActive": true,
      "spawnedAt": "2026-02-16T10:35:00.000Z"
    },
    {
      "agentId": "analyst-2@research-team",
      "name": "analyst-2",
      "agentType": "general-purpose",
      "model": "sonnet",
      "prompt": "You are a data analyst specializing in customer behavior patterns. Focus on qualitative insights and behavioral trends.",
      "color": "green",
      "tmuxPaneId": "%89",
      "backendType": "tmux",
      "isActive": true,
      "spawnedAt": "2026-02-16T10:36:00.000Z"
    }
  ],
  "createdAt": "2026-02-16T10:30:00.000Z",
  "schemaVersion": "1.0.0",
  "metadata": {
    "project": "Q4-analysis",
    "budget_tokens": 100000,
    "priority": "high",
    "deadline": "2026-02-20"
  }
}
```

### Example: Shutdown Member

```json
{
  "agentId": "analyst-1@research-team",
  "name": "analyst-1",
  "agentType": "general-purpose",
  "model": "haiku",
  "prompt": "...",
  "color": "blue",
  "tmuxPaneId": "%88",
  "backendType": "tmux",
  "isActive": false,
  "spawnedAt": "2026-02-16T10:35:00.000Z",
  "shutdownAt": "2026-02-16T11:45:00.000Z",
  "metadata": {
    "tasks_completed": 5,
    "total_tokens": 12500,
    "shutdown_reason": "tasks_complete"
  }
}
```

### Field Constraints

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `name` | string | Yes | Kebab-case, 3-64 chars |
| `description` | string | Yes | 1-500 chars |
| `members` | array | Yes | Can be empty |
| `createdAt` | string | Yes | ISO 8601 format |
| `agentId` | string | Yes | Format: "{name}@{team}" |
| `model` | string | Yes | haiku\|sonnet\|opus |
| `tmuxPaneId` | string | Yes | Format: "%{number}" |
| `isActive` | boolean | Yes | true\|false |

## Inbox Files

Message queue for each team member.

### Location
```
~/.claude/teams/{team-name}/inboxes/{member-name}.json
```

### Schema

```typescript
type InboxMessages = InboxMessage[];

interface InboxMessage {
  from: string;           // Sender name or "system"
  text: string;           // Full message content
  summary: string;        // Brief summary for notifications
  timestamp: string;      // ISO 8601 timestamp
  color: string;          // Sender's color
  read: boolean;          // Read status
  messageId?: string;     // Unique message identifier
  type?: string;          // Message type (optional)
  metadata?: Record<string, any>;  // Custom message metadata
}
```

### Example: Initial System Message

```json
[
  {
    "from": "system",
    "text": "You are a data analyst specializing in sales trends. Analyze data carefully and provide actionable insights with supporting evidence.",
    "summary": "Initial system prompt",
    "timestamp": "2026-02-16T10:35:00.000Z",
    "color": "system",
    "read": false,
    "messageId": "msg-init-001",
    "type": "system_init"
  }
]
```

### Example: Regular Messages

```json
[
  {
    "from": "system",
    "text": "You are a data analyst...",
    "summary": "Initial system prompt",
    "timestamp": "2026-02-16T10:35:00.000Z",
    "color": "system",
    "read": true,
    "messageId": "msg-init-001"
  },
  {
    "from": "coordinator",
    "text": "Please analyze the sales data in /data/q4-sales.csv. Focus on:\n1. Top 3 revenue trends\n2. Customer segment performance\n3. Regional variations\n\nProvide a summary with key insights and recommendations.",
    "summary": "Q4 sales analysis request",
    "timestamp": "2026-02-16T10:40:00.000Z",
    "color": "yellow",
    "read": false,
    "messageId": "msg-task-001",
    "metadata": {
      "priority": "high",
      "estimated_time": "30min"
    }
  },
  {
    "from": "coordinator",
    "text": "Also cross-reference with customer satisfaction scores from /data/satisfaction.csv",
    "summary": "Additional analysis requirement",
    "timestamp": "2026-02-16T10:42:00.000Z",
    "color": "yellow",
    "read": false,
    "messageId": "msg-task-002",
    "metadata": {
      "related_to": "msg-task-001"
    }
  }
]
```

### Example: Shutdown Request Message

```json
[
  {
    "from": "coordinator",
    "text": "Analysis complete. Thank you for your excellent work. Please prepare for shutdown.",
    "summary": "Shutdown request",
    "timestamp": "2026-02-16T11:45:00.000Z",
    "color": "yellow",
    "read": false,
    "messageId": "msg-shutdown-001",
    "type": "shutdown_request",
    "metadata": {
      "request_id": "req-shutdown-abc123"
    }
  }
]
```

### Message Lifecycle

```
1. New message → read: false
2. Teammate polls inbox
3. Detects unread message
4. Processes content
5. Marks read: true
```

### Read Status Update

```bash
# Mark specific message as read
jq '(.[] | select(.messageId == "msg-task-001") | .read) = true' inbox.json
```

## Idle Notification Format

Special message type indicating teammate availability.

### Schema

```typescript
interface IdleNotification {
  type: "idle_notification";
  from: string;           // Teammate name
  idleReason: string;     // "available" | "waiting_response" | "task_complete"
  timestamp?: string;     // ISO 8601 timestamp
}
```

### Examples

**Available for Work:**
```json
{
  "type": "idle_notification",
  "from": "analyst-1",
  "idleReason": "available",
  "timestamp": "2026-02-16T10:45:00.000Z"
}
```

**Waiting for Response:**
```json
{
  "type": "idle_notification",
  "from": "analyst-1",
  "idleReason": "waiting_response",
  "timestamp": "2026-02-16T10:50:00.000Z"
}
```

**Task Complete:**
```json
{
  "type": "idle_notification",
  "from": "analyst-1",
  "idleReason": "task_complete",
  "timestamp": "2026-02-16T11:00:00.000Z"
}
```

## Shutdown Messages

### Shutdown Approved Format

```typescript
interface ShutdownApproved {
  type: "shutdown_approved";
  requestId: string;      // From shutdown_request
  paneId: string;         // Tmux pane to kill
  backendType: string;    // "tmux"
  timestamp?: string;
}
```

### Example

```json
{
  "type": "shutdown_approved",
  "requestId": "req-shutdown-abc123",
  "paneId": "%88",
  "backendType": "tmux",
  "timestamp": "2026-02-16T11:46:00.000Z"
}
```

## Plan Approval Format

### Plan Approval Request

```typescript
interface PlanApprovalRequest {
  type: "plan_approval_request";
  from: string;           // Requester name
  plan: string;           // Detailed plan description
  summary: string;        // Brief summary
  timestamp: string;
  requestId: string;
}
```

### Example

```json
{
  "type": "plan_approval_request",
  "from": "analyst-1",
  "plan": "## Analysis Plan\n\n1. Load Q4 sales data\n2. Calculate revenue trends\n3. Segment by customer type\n4. Generate visualization\n5. Write summary report\n\nEstimated time: 30 minutes",
  "summary": "Q4 analysis plan",
  "timestamp": "2026-02-16T10:41:00.000Z",
  "requestId": "plan-xyz789"
}
```

### Plan Approval Response

```typescript
interface PlanApprovalResponse {
  type: "plan_approval_response";
  requestId: string;      // From plan_approval_request
  approve: boolean;
  feedback?: string;      // Optional modifications/comments
  timestamp?: string;
}
```

### Example: Approved

```json
{
  "type": "plan_approval_response",
  "requestId": "plan-xyz789",
  "approve": true,
  "feedback": "Looks good. Also include comparison with Q3 data.",
  "timestamp": "2026-02-16T10:43:00.000Z"
}
```

### Example: Denied

```json
{
  "type": "plan_approval_response",
  "requestId": "plan-xyz789",
  "approve": false,
  "feedback": "Please focus on regional variations first, then customer segments. Skip visualization for now.",
  "timestamp": "2026-02-16T10:43:00.000Z"
}
```

## Task Artifacts

### Location
```
~/.claude/tasks/{team-name}/
```

### Structure

```
~/.claude/tasks/research-team/
├── analyst-1/
│   ├── output.log          # Stdout/stderr capture
│   ├── task-status.json    # Task execution status
│   └── artifacts/          # Generated files
│       ├── analysis.csv
│       └── report.md
└── analyst-2/
    ├── output.log
    ├── task-status.json
    └── artifacts/
```

### task-status.json Schema

```typescript
interface TaskStatus {
  agentId: string;
  status: "running" | "idle" | "complete" | "error";
  startedAt: string;
  lastActiveAt: string;
  completedAt?: string;
  errorMessage?: string;
  metrics?: {
    tokens_used: number;
    tasks_completed: number;
    avg_response_time: number;
  };
}
```

### Example

```json
{
  "agentId": "analyst-1@research-team",
  "status": "complete",
  "startedAt": "2026-02-16T10:35:00.000Z",
  "lastActiveAt": "2026-02-16T11:30:00.000Z",
  "completedAt": "2026-02-16T11:30:00.000Z",
  "metrics": {
    "tokens_used": 12500,
    "tasks_completed": 3,
    "avg_response_time": 120
  }
}
```

## Color Codes

### Terminal Color Mapping

| Color | ANSI Code | Use Case |
|-------|-----------|----------|
| blue | \033[34m | First teammate |
| green | \033[32m | Second teammate |
| yellow | \033[33m | Coordinators |
| magenta | \033[35m | Specialized roles |
| cyan | \033[36m | Reviewers |
| red | \033[31m | Error/warning agents |

### config.json Color Assignment

```json
{
  "name": "worker-1",
  "color": "blue"
}
```

### Message Color Usage

```json
{
  "from": "coordinator",
  "color": "yellow",
  "text": "..."
}
```

## File Operations

### Reading Config

```bash
# Get all members
jq '.members' ~/.claude/teams/research-team/config.json

# Get active members
jq '.members[] | select(.isActive == true)' config.json

# Get specific member
jq '.members[] | select(.name == "analyst-1")' config.json
```

### Updating Config

```bash
# Add new member
jq '.members += [{
  "agentId": "worker-2@research-team",
  "name": "worker-2",
  "agentType": "general-purpose",
  "model": "haiku",
  "prompt": "...",
  "color": "green",
  "tmuxPaneId": "%89",
  "backendType": "tmux",
  "isActive": true,
  "spawnedAt": "'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"
}]' config.json > config.json.tmp && mv config.json.tmp config.json

# Update member status
jq '(.members[] | select(.name == "analyst-1") | .isActive) = false' config.json

# Add shutdown timestamp
jq '(.members[] | select(.name == "analyst-1") | .shutdownAt) = "'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'"' config.json
```

### Reading Inbox

```bash
# Get all messages
jq '.' ~/.claude/teams/research-team/inboxes/analyst-1.json

# Get unread messages
jq '[.[] | select(.read == false)]' inbox.json

# Get messages from specific sender
jq '[.[] | select(.from == "coordinator")]' inbox.json

# Count unread
jq '[.[] | select(.read == false)] | length' inbox.json
```

### Appending to Inbox

```bash
# Add new message
jq '. += [{
  "from": "coordinator",
  "text": "New task: analyze customer churn",
  "summary": "Churn analysis request",
  "timestamp": "'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'",
  "color": "yellow",
  "read": false,
  "messageId": "msg-'$(uuidgen)'"
}]' inbox.json > inbox.json.tmp && mv inbox.json.tmp inbox.json
```

### Marking Messages Read

```bash
# Mark all as read
jq '(.[] | .read) = true' inbox.json

# Mark specific message
jq '(.[] | select(.messageId == "msg-task-001") | .read) = true' inbox.json
```

## Validation

### Config Validation

```bash
# Validate JSON syntax
jq empty config.json && echo "Valid JSON"

# Check required fields
jq 'if .name and .description and .members then "Valid" else "Missing required fields" end' config.json

# Validate member structure
jq '.members[] | select(.agentId and .name and .model and .isActive != null) | .name' config.json
```

### Inbox Validation

```bash
# Validate JSON array
jq 'if type == "array" then "Valid" else "Must be array" end' inbox.json

# Check message structure
jq '.[] | select(.from and .text and .timestamp and (.read != null)) | .from' inbox.json
```

## Backup and Recovery

### Backup Team Data

```bash
# Backup entire team
tar -czf research-team-$(date +%Y%m%d).tar.gz \
  ~/.claude/teams/research-team/ \
  ~/.claude/tasks/research-team/

# Backup config only
cp ~/.claude/teams/research-team/config.json \
  config-backup-$(date +%Y%m%d).json
```

### Restore Team Data

```bash
# Restore from backup
tar -xzf research-team-20260216.tar.gz -C ~/

# Restore config
cp config-backup-20260216.json \
  ~/.claude/teams/research-team/config.json
```

### Export Team State

```bash
# Export readable format
jq '{
  team: .name,
  created: .createdAt,
  active_members: [.members[] | select(.isActive == true) | .name],
  total_members: (.members | length)
}' config.json > team-state.json
```

## Schema Evolution

### Version Migration

```bash
# Check schema version
jq '.schemaVersion // "1.0.0"' config.json

# Add schema version to old config
jq '. + {schemaVersion: "1.0.0"}' config.json
```

### Future Compatibility

Reserved fields for future versions:
- `backendType`: Currently "tmux", future: "docker", "k8s"
- `agentType`: Currently "general-purpose", future: custom types
- `metadata`: Extensible for new features

## See Also

- **SKILL.md** - Core lifecycle workflows
- **api-specification.md** - Complete API reference
- **advanced-patterns.md** - Complex workflow examples
