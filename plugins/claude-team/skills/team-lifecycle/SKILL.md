---
name: team-lifecycle
description: This skill should be used when the user asks to "create a team", "create teammate", "spawn teammate", "send message to teammate", "shutdown team", "delete team", "broadcast message", "team communication", or mentions Agent Teams API lifecycle operations like TeamCreate, Task spawn, SendMessage, TeamDelete, or teammate management.
version: 1.0.0
---

# Agent Teams API Lifecycle Guide

This skill provides comprehensive guidance on the Agent Teams API lifecycle: creating teams, spawning teammates, communicating between agents, and managing team shutdown/deletion.

## Overview

The Agent Teams API lifecycle consists of four primary phases:

1. **Creation** - Initialize team and spawn teammate processes
2. **Communication** - Exchange messages between teammates
3. **Idle Management** - Handle agent availability and wake-up
4. **Termination** - Shutdown teammates and delete team resources

Each phase uses specific tools and file system operations documented below.

## Phase 1: Team Creation

### TeamCreate Tool

Create a new team structure with the TeamCreate tool:

**Parameters:**
- `team_name` (required) - Kebab-case team identifier
- `description` (required) - Team purpose description

**Result:**
```
~/.claude/teams/{team-name}/
├── config.json       # Team configuration and member registry
└── inboxes/          # Message queue directory
```

**config.json Structure:**
```json
{
  "name": "team-name",
  "description": "Team purpose",
  "members": [],
  "createdAt": "2026-02-16T10:30:00Z"
}
```

### Spawning Teammates with Task Tool

After creating the team, spawn individual teammates using the Task tool:

**Required Parameters:**
- `team_name` - Must match the TeamCreate name
- `name` - Unique teammate identifier
- `model` - Model to use (e.g., "haiku", "sonnet", "opus")
- `prompt` - Initial system prompt/instructions
- `subagent_type` - Set to "general-purpose"

**Optional Parameters:**
- `run_in_background` - Set to `true` for persistent processes

**Execution Mechanism:**

The Task tool executes this command in a new tmux pane:
```bash
claude --agent-id {name}@{team} \
  --agent-name {name} \
  --team-name {team} \
  --agent-color {color} \
  --parent-session-id {session-id} \
  --agent-type general-purpose \
  --dangerously-skip-permissions \
  --model {model}
```

**Member Registration:**

After spawning, the teammate is registered in config.json:
```json
{
  "agentId": "name@team-name",
  "name": "worker-1",
  "agentType": "general-purpose",
  "model": "haiku",
  "prompt": "You are a worker agent...",
  "color": "blue",
  "tmuxPaneId": "%88",
  "backendType": "tmux",
  "isActive": true
}
```

**Initial Message Delivery:**

The spawn prompt is delivered via inbox file:
```
~/.claude/teams/{team-name}/inboxes/{member}.json
```

## Phase 2: Team Communication

### SendMessage Tool - Message Types

The SendMessage tool supports four message types, each with different parameters and behaviors.

#### Type 1: Direct Message ("message")

Send a private message to a specific teammate.

**Required Parameters:**
- `type`: "message"
- `team_name`: Target team
- `recipient`: Teammate name
- `content`: Message content
- `summary`: Brief summary (for inbox notifications)

**Example:**
```json
{
  "type": "message",
  "team_name": "research-team",
  "recipient": "analyst",
  "content": "Analyze the Q4 sales data in reports/sales.csv",
  "summary": "Sales analysis request"
}
```

**Inbox Format:**
```json
[
  {
    "from": "coordinator",
    "text": "Analyze the Q4 sales data in reports/sales.csv",
    "summary": "Sales analysis request",
    "timestamp": "2026-02-16T10:35:00Z",
    "color": "green",
    "read": false
  }
]
```

#### Type 2: Broadcast ("broadcast")

Send a message to all active team members.

**Required Parameters:**
- `type`: "broadcast"
- `team_name`: Target team
- `content`: Message content
- `summary`: Brief summary

**Cost Warning:** Broadcasts consume tokens for all recipients. Use direct messages when possible.

**Example:**
```json
{
  "type": "broadcast",
  "team_name": "research-team",
  "content": "Meeting in 5 minutes - status updates required",
  "summary": "Team meeting notification"
}
```

#### Type 3: Shutdown Request ("shutdown_request")

Request a teammate to terminate gracefully.

**Required Parameters:**
- `type`: "shutdown_request"
- `team_name`: Target team
- `recipient`: Teammate to shutdown
- `content`: Shutdown reason/message

**Example:**
```json
{
  "type": "shutdown_request",
  "team_name": "research-team",
  "recipient": "analyst",
  "content": "Analysis complete, shutting down"
}
```

**Response Flow:**
1. Recipient receives shutdown_request in inbox
2. Recipient sends shutdown_response
3. System processes approval and terminates process

#### Type 4: Shutdown Response ("shutdown_response")

Approve or deny a shutdown request.

**Required Parameters:**
- `type`: "shutdown_response"
- `request_id`: ID from shutdown_request
- `approve`: true/false

**Example:**
```json
{
  "type": "shutdown_response",
  "request_id": "req-123",
  "approve": true
}
```

**Approval Result:**
```json
{
  "type": "shutdown_approved",
  "requestId": "req-123",
  "paneId": "%88",
  "backendType": "tmux"
}
```

### Message Polling and Delivery

Teammates poll their inbox files for new messages:

1. Check `~/.claude/teams/{team}/inboxes/{name}.json`
2. Read unread messages (where `read: false`)
3. Process message content
4. Mark messages as read

**Polling Frequency:** Typically triggered by idle_notification or explicit checks.

## Phase 3: Idle Management

### Idle Notification System

When a teammate completes a turn and has no pending work, it sends an idle notification:

**Format:**
```json
{
  "type": "idle_notification",
  "from": "worker-1",
  "idleReason": "available"
}
```

**Wake-up Mechanism:**

1. Teammate enters idle state
2. New message arrives in inbox
3. Polling detects unread message
4. Teammate wakes and processes message

### Availability States

| State | Description | Inbox Polling |
|-------|-------------|---------------|
| **active** | Processing work | No |
| **idle** | Waiting for messages | Yes (periodic) |
| **shutdown** | Terminating | No |

## Phase 4: Termination

### Teammate Shutdown Process

**Step 1: Send Shutdown Request**

Use SendMessage with type "shutdown_request":
```json
{
  "type": "shutdown_request",
  "team_name": "research-team",
  "recipient": "analyst",
  "content": "Task complete, shutting down"
}
```

**Step 2: Receive Approval**

Teammate responds with shutdown_response. System sends shutdown_approved:
```json
{
  "type": "shutdown_approved",
  "requestId": "req-123",
  "paneId": "%88",
  "backendType": "tmux"
}
```

**Step 3: Process Termination**

1. Kill tmux pane (`tmux kill-pane -t %88`)
2. Update config.json - set `isActive: false`
3. Remove member from active registry

### Team Deletion with TeamDelete

Delete the entire team structure using TeamDelete tool:

**Required Parameter:**
- `team_name` - Team to delete

**Prerequisites:**
- All teammates must be shutdown first
- Active members will cause deletion to fail

**Deletion Process:**

1. Verify no active members in config.json
2. Delete `~/.claude/teams/{team-name}/`
3. Delete `~/.claude/tasks/{team-name}/`
4. Remove all related files

**Example:**
```bash
# Check for active members first
cat ~/.claude/teams/research-team/config.json | jq '.members[] | select(.isActive == true)'

# If empty, safe to delete
TeamDelete(team_name="research-team")
```

## File System Reference

### Directory Structure

```
~/.claude/
├── teams/
│   └── {team-name}/
│       ├── config.json         # Team configuration
│       └── inboxes/
│           ├── {member-1}.json # Message queue
│           └── {member-2}.json
└── tasks/
    └── {team-name}/            # Task artifacts
```

### config.json Member Structure

```json
{
  "agentId": "name@team-name",
  "name": "worker-1",
  "agentType": "general-purpose",
  "model": "haiku",
  "prompt": "System prompt text",
  "color": "blue",
  "tmuxPaneId": "%88",
  "backendType": "tmux",
  "isActive": true
}
```

### Inbox Message Structure

```json
[
  {
    "from": "sender-name",
    "text": "Message content",
    "summary": "Brief summary",
    "timestamp": "2026-02-16T10:35:00Z",
    "color": "green",
    "read": false
  }
]
```

## Complete Lifecycle Example

### Scenario: Create Research Team with Two Analysts

**1. Create Team:**
```
TeamCreate(
  team_name="research-team",
  description="Q4 analysis team"
)
```

**2. Spawn First Analyst:**
```
Task(
  team_name="research-team",
  name="analyst-1",
  model="haiku",
  prompt="You are a data analyst specializing in sales trends",
  subagent_type="general-purpose",
  run_in_background=true
)
```

**3. Spawn Second Analyst:**
```
Task(
  team_name="research-team",
  name="analyst-2",
  model="haiku",
  prompt="You are a data analyst specializing in customer behavior",
  subagent_type="general-purpose",
  run_in_background=true
)
```

**4. Assign Work:**
```
SendMessage(
  type="message",
  team_name="research-team",
  recipient="analyst-1",
  content="Analyze sales trends in Q4 data",
  summary="Sales trend analysis"
)

SendMessage(
  type="message",
  team_name="research-team",
  recipient="analyst-2",
  content="Analyze customer behavior patterns",
  summary="Customer analysis"
)
```

**5. Collect Results:**

Wait for analysts to complete work and send results back via SendMessage.

**6. Shutdown Analysts:**
```
SendMessage(
  type="shutdown_request",
  team_name="research-team",
  recipient="analyst-1",
  content="Analysis complete, thank you"
)

SendMessage(
  type="shutdown_request",
  team_name="research-team",
  recipient="analyst-2",
  content="Analysis complete, thank you"
)
```

**7. Delete Team:**
```
TeamDelete(team_name="research-team")
```

## Best Practices

### Team Creation
- Use descriptive team names (kebab-case)
- Provide clear descriptions for team purpose
- Plan teammate roles before spawning

### Teammate Spawning
- Choose appropriate models for task complexity
- Write clear, focused system prompts
- Use `run_in_background=true` for persistent agents

### Communication
- Prefer direct messages over broadcasts (cost efficiency)
- Include meaningful summaries for inbox notifications
- Keep message content concise and actionable

### Shutdown
- Always shutdown teammates before team deletion
- Allow teammates to finish current work before shutdown
- Verify shutdown completion before deletion

### Error Handling
- Check config.json for active members before deletion
- Monitor inbox files for message delivery
- Verify tmux pane existence for active teammates

## Troubleshooting

### Teammate Not Responding

1. Check if teammate is active: `cat ~/.claude/teams/{team}/config.json | jq '.members[] | select(.name == "{name}")'`
2. Verify inbox file exists: `ls ~/.claude/teams/{team}/inboxes/`
3. Check tmux pane: `tmux list-panes -a | grep {paneId}`

### TeamDelete Fails

Error: "Cannot delete team with active members"

**Solution:**
1. List active members: `jq '.members[] | select(.isActive == true)' ~/.claude/teams/{team}/config.json`
2. Shutdown each active member
3. Retry deletion

### Message Not Delivered

1. Verify recipient name matches config.json
2. Check inbox file permissions
3. Ensure recipient is active (not shutdown)

## Additional Resources

### Reference Files
- **`references/api-specification.md`** - Complete API parameter specifications
- **`references/file-formats.md`** - Detailed file format schemas
- **`references/advanced-patterns.md`** - Complex workflow patterns

### Examples
- **`examples/config-complete.json`** - Complete team configuration example
- **`examples/inbox-messages.json`** - Message format examples
- **`examples/workflow-parallel.md`** - Parallel task workflow

## See Also

### Related Tools
- **TeamCreate** - Initialize team structure
- **Task** - Spawn teammate processes
- **SendMessage** - Inter-agent communication
- **TeamDelete** - Remove team resources

### Related Concepts
- **Agent Types** - general-purpose, specialized roles
- **Message Polling** - Inbox monitoring mechanisms
- **Tmux Backend** - Process isolation and management
