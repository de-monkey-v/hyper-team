# Task Schema Reference

Complete specification for task JSON files in `~/.claude/tasks/{uuid}/task.json`.

## Schema Definition

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "subject", "status", "created"],
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "Unique task identifier"
    },
    "subject": {
      "type": "string",
      "description": "Brief task summary",
      "minLength": 1,
      "maxLength": 200
    },
    "description": {
      "type": "string",
      "description": "Detailed task requirements and context"
    },
    "status": {
      "type": "string",
      "enum": ["pending", "in_progress", "completed", "cancelled"],
      "description": "Current task state"
    },
    "owner": {
      "type": "string",
      "description": "Team member assigned to this task"
    },
    "team": {
      "type": "string",
      "description": "Team name this task belongs to"
    },
    "created": {
      "type": "string",
      "format": "date-time",
      "description": "Task creation timestamp"
    },
    "updated": {
      "type": "string",
      "format": "date-time",
      "description": "Last modification timestamp"
    },
    "started": {
      "type": "string",
      "format": "date-time",
      "description": "When task moved to in_progress"
    },
    "completed": {
      "type": "string",
      "format": "date-time",
      "description": "When task moved to completed"
    },
    "blocks": {
      "type": "array",
      "description": "Task IDs that cannot start until this completes",
      "items": {
        "type": "string",
        "format": "uuid"
      }
    },
    "blockedBy": {
      "type": "array",
      "description": "Task IDs that must complete before this can start",
      "items": {
        "type": "string",
        "format": "uuid"
      }
    },
    "tags": {
      "type": "array",
      "description": "Categorization labels",
      "items": {
        "type": "string"
      }
    },
    "priority": {
      "type": "integer",
      "minimum": 1,
      "maximum": 5,
      "description": "Priority level (1=lowest, 5=highest)"
    },
    "estimatedHours": {
      "type": "number",
      "minimum": 0,
      "description": "Estimated effort in hours"
    },
    "actualHours": {
      "type": "number",
      "minimum": 0,
      "description": "Actual time spent"
    },
    "context": {
      "type": "object",
      "description": "Additional task-specific data",
      "properties": {
        "files": {
          "type": "array",
          "description": "Relevant file paths",
          "items": {"type": "string"}
        },
        "references": {
          "type": "array",
          "description": "Related documentation or tickets",
          "items": {"type": "string"}
        },
        "notes": {
          "type": "string",
          "description": "Free-form notes"
        }
      }
    }
  }
}
```

## Complete Example

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "subject": "Implement user authentication API",
  "description": "Create REST API endpoints for user registration, login, and token refresh. Use JWT for authentication. Include rate limiting and validation.",
  "status": "in_progress",
  "owner": "backend-dev",
  "team": "team-alpha",
  "created": "2026-02-15T09:00:00Z",
  "updated": "2026-02-15T14:30:00Z",
  "started": "2026-02-15T10:15:00Z",
  "blocks": [
    "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "c3d4e5f6-a7b8-9012-cdef-123456789012"
  ],
  "blockedBy": [],
  "tags": ["backend", "api", "security"],
  "priority": 4,
  "estimatedHours": 8,
  "actualHours": 5.5,
  "context": {
    "files": [
      "src/api/auth.py",
      "tests/test_auth.py",
      "docs/api-spec.md"
    ],
    "references": [
      "https://jwt.io/introduction/",
      "ticket-123"
    ],
    "notes": "Consider using bcrypt for password hashing. Rate limit: 5 requests/minute per IP."
  }
}
```

## Field Descriptions

### Core Fields

#### id
- **Type:** UUID v4 string
- **Required:** Yes
- **Immutable:** Yes
- **Purpose:** Unique identifier for task references
- **Generation:** `uuidgen` or equivalent
- **Example:** `a1b2c3d4-e5f6-7890-abcd-ef1234567890`

#### subject
- **Type:** string
- **Required:** Yes
- **Length:** 1-200 characters
- **Purpose:** One-line task summary
- **Best Practice:** Start with verb (e.g., "Implement...", "Fix...", "Add...")
- **Examples:**
  - "Implement user authentication API"
  - "Fix memory leak in data processor"
  - "Add unit tests for payment module"

#### description
- **Type:** string
- **Required:** No (but highly recommended)
- **Purpose:** Detailed requirements, acceptance criteria, context
- **Format:** Plain text or Markdown
- **Best Practice:** Include:
  - What needs to be done
  - Why it's needed
  - Acceptance criteria
  - Any constraints or special considerations

#### status
- **Type:** enum string
- **Required:** Yes
- **Values:**
  - `pending`: Task created but not started
  - `in_progress`: Task actively being worked on
  - `completed`: Task finished successfully
  - `cancelled`: Task abandoned or no longer needed
- **Transitions:**
  ```
  pending → in_progress → completed
  pending → cancelled
  in_progress → cancelled
  ```

#### owner
- **Type:** string
- **Required:** No (tasks can be unassigned)
- **Format:** Member name from team config
- **Purpose:** Identifies who is responsible for the task
- **Validation:** Should match a member name in team config

#### team
- **Type:** string
- **Required:** No (for team-specific tasks)
- **Format:** Team name from ~/.claude/teams/
- **Purpose:** Associates task with a specific team

### Timestamp Fields

All timestamps use ISO 8601 format: `YYYY-MM-DDTHH:MM:SSZ`

#### created
- **Required:** Yes
- **Set:** When task is created
- **Immutable:** Yes
- **Purpose:** Track task age

#### updated
- **Required:** No
- **Set:** Whenever task.json is modified
- **Purpose:** Track recent activity

#### started
- **Required:** No
- **Set:** When status changes to `in_progress`
- **Purpose:** Calculate time-to-start metric

#### completed
- **Required:** No
- **Set:** When status changes to `completed`
- **Purpose:** Calculate completion time and throughput

### Dependency Fields

#### blocks
- **Type:** array of UUID strings
- **Purpose:** Tasks that cannot start until this task completes
- **Direction:** Forward dependency (this task → future tasks)
- **Example:** Task A blocks Tasks B and C
  ```json
  {
    "id": "task-a-uuid",
    "blocks": ["task-b-uuid", "task-c-uuid"]
  }
  ```

#### blockedBy
- **Type:** array of UUID strings
- **Purpose:** Tasks that must complete before this task can start
- **Direction:** Backward dependency (past tasks → this task)
- **Example:** Task D blocked by Tasks A and B
  ```json
  {
    "id": "task-d-uuid",
    "blockedBy": ["task-a-uuid", "task-b-uuid"]
  }
  ```

**Consistency Rule:** If Task A blocks Task B, then Task B should be blocked by Task A:
```json
// Task A
{"id": "a", "blocks": ["b"]}

// Task B
{"id": "b", "blockedBy": ["a"]}
```

### Metadata Fields

#### tags
- **Type:** array of strings
- **Purpose:** Categorization and filtering
- **Common Tags:**
  - Technology: `backend`, `frontend`, `database`
  - Type: `feature`, `bugfix`, `refactor`, `test`
  - Area: `api`, `ui`, `auth`, `payment`
- **Best Practice:** Use lowercase, hyphen-separated

#### priority
- **Type:** integer 1-5
- **Purpose:** Relative importance
- **Scale:**
  - `1`: Low priority, nice-to-have
  - `2`: Normal priority
  - `3`: Medium priority, should do soon
  - `4`: High priority, important
  - `5`: Critical, urgent

#### estimatedHours
- **Type:** number (float)
- **Purpose:** Planned effort
- **Use:** Capacity planning, workload balancing
- **Best Practice:** Include buffer for unknowns

#### actualHours
- **Type:** number (float)
- **Purpose:** Track actual time spent
- **Use:** Improve future estimates, track efficiency
- **Update:** Periodically or on completion

### Context Object

Extensible container for additional task data:

#### context.files
- **Type:** array of strings
- **Purpose:** File paths relevant to task
- **Format:** Absolute or project-relative paths
- **Example:**
  ```json
  "files": [
    "src/api/users.py",
    "tests/test_users.py",
    "migrations/002_add_users_table.sql"
  ]
  ```

#### context.references
- **Type:** array of strings
- **Purpose:** Links to docs, tickets, specs
- **Format:** URLs or ticket IDs
- **Example:**
  ```json
  "references": [
    "https://docs.example.com/api-spec",
    "JIRA-123",
    "RFC-456"
  ]
  ```

#### context.notes
- **Type:** string
- **Purpose:** Free-form notes, decisions, findings
- **Format:** Plain text or Markdown
- **Use Cases:**
  - Document decisions made
  - Record blockers encountered
  - Link to discussions

## Task Lifecycle

### 1. Creation

```bash
TASK_ID=$(uuidgen)
mkdir -p ~/.claude/tasks/$TASK_ID

cat > ~/.claude/tasks/$TASK_ID/task.json <<EOF
{
  "id": "$TASK_ID",
  "subject": "Implement feature X",
  "description": "Detailed requirements...",
  "status": "pending",
  "created": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "tags": ["feature"],
  "priority": 3
}
EOF
```

### 2. Assignment

```bash
jq '.owner = "backend-dev" | .updated = "'$(date -u +%Y-%m-%dT%H:%M:%SZ)'"' \
  ~/.claude/tasks/$TASK_ID/task.json > /tmp/task.json
mv /tmp/task.json ~/.claude/tasks/$TASK_ID/task.json
```

### 3. Starting Work

```bash
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
jq --arg now "$NOW" \
  '.status = "in_progress" | .started = $now | .updated = $now' \
  ~/.claude/tasks/$TASK_ID/task.json > /tmp/task.json
mv /tmp/task.json ~/.claude/tasks/$TASK_ID/task.json
```

### 4. Completion

```bash
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
jq --arg now "$NOW" \
  '.status = "completed" | .completed = $now | .updated = $now' \
  ~/.claude/tasks/$TASK_ID/task.json > /tmp/task.json
mv /tmp/task.json ~/.claude/tasks/$TASK_ID/task.json
```

## Querying Tasks

### List All Tasks

```bash
find ~/.claude/tasks -name "task.json" -exec cat {} \; | jq -s '.'
```

### Filter by Status

```bash
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -s '[.[] | select(.status == "in_progress")]'
```

### Find Blocked Tasks

```bash
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -s '[.[] | select((.blockedBy | length) > 0)]'
```

### Find Tasks by Owner

```bash
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -s '[.[] | select(.owner == "backend-dev")]'
```

### Find Tasks by Tag

```bash
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -s '[.[] | select(.tags | contains(["backend"]))]'
```

### Find Overdue Tasks

```bash
NOW=$(date -u +%s)
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -s --arg now "$NOW" \
  '[.[] | select(.status == "in_progress" and (.started | fromdateiso8601) < ($now | tonumber) - 86400)]'
```

## Dependency Management

### Add Blocking Relationship

```bash
# Task A blocks Task B
jq '.blocks += ["task-b-uuid"]' \
  ~/.claude/tasks/task-a-uuid/task.json > /tmp/task.json
mv /tmp/task.json ~/.claude/tasks/task-a-uuid/task.json

jq '.blockedBy += ["task-a-uuid"]' \
  ~/.claude/tasks/task-b-uuid/task.json > /tmp/task.json
mv /tmp/task.json ~/.claude/tasks/task-b-uuid/task.json
```

### Find Dependency Chain

```bash
# Recursive function to find all blockers
function find_blockers() {
  local task_id=$1
  local blockers=$(jq -r '.blockedBy[]' ~/.claude/tasks/$task_id/task.json 2>/dev/null)

  if [ -n "$blockers" ]; then
    echo "$blockers"
    for blocker in $blockers; do
      find_blockers $blocker
    done
  fi
}

find_blockers "task-uuid"
```

### Detect Circular Dependencies

```bash
# Check if task appears in its own dependency chain
function has_circular_dependency() {
  local task_id=$1
  local visited=()
  local queue=("$task_id")

  while [ ${#queue[@]} -gt 0 ]; do
    local current=${queue[0]}
    queue=("${queue[@]:1}")

    if [[ " ${visited[@]} " =~ " ${current} " ]]; then
      echo "Circular dependency detected: $current"
      return 1
    fi

    visited+=("$current")
    local blockers=$(jq -r '.blockedBy[]' ~/.claude/tasks/$current/task.json 2>/dev/null)
    queue+=($blockers)
  done

  return 0
}
```

## Task Metrics

### Calculate Completion Time

```bash
jq -r 'select(.status == "completed") |
  (.completed | fromdateiso8601) - (.started | fromdateiso8601) |
  . / 3600' \
  ~/.claude/tasks/*/task.json
```

Output: hours from start to completion

### Calculate Time to Start

```bash
jq -r 'select(.started != null) |
  (.started | fromdateiso8601) - (.created | fromdateiso8601) |
  . / 3600' \
  ~/.claude/tasks/*/task.json
```

Output: hours from creation to start

### Estimate vs Actual Accuracy

```bash
jq -r 'select(.estimatedHours != null and .actualHours != null) |
  {
    subject,
    estimated: .estimatedHours,
    actual: .actualHours,
    variance: ((.actualHours - .estimatedHours) / .estimatedHours * 100)
  }' \
  ~/.claude/tasks/*/task.json
```

## Validation Rules

### Required Fields Present

```bash
jq -e '.id and .subject and .status and .created' \
  ~/.claude/tasks/$TASK_ID/task.json
```

### Valid Status Value

```bash
jq -e '.status | IN("pending", "in_progress", "completed", "cancelled")' \
  ~/.claude/tasks/$TASK_ID/task.json
```

### Dependency Consistency

```bash
# For each task in blocks array, verify reverse relationship exists
jq -r '.blocks[]' ~/.claude/tasks/$TASK_ID/task.json | while read blocked_id; do
  jq -e --arg id "$TASK_ID" \
    '.blockedBy | contains([$id])' \
    ~/.claude/tasks/$blocked_id/task.json || \
    echo "Inconsistency: $blocked_id not blocked by $TASK_ID"
done
```

### Owner Exists in Team

```bash
OWNER=$(jq -r '.owner' ~/.claude/tasks/$TASK_ID/task.json)
TEAM=$(jq -r '.team' ~/.claude/tasks/$TASK_ID/task.json)

jq -e --arg owner "$OWNER" \
  '.members[] | select(.name == $owner)' \
  ~/.claude/teams/$TEAM/config.json || \
  echo "Owner $OWNER not found in team $TEAM"
```

## Best Practices

1. **Keep subjects concise**: Under 100 characters when possible
2. **Always set description**: Provide context for future reference
3. **Update timestamps**: Set `updated` on any modification
4. **Maintain dependency consistency**: Update both sides of blocking relationships
5. **Use tags consistently**: Establish team-wide tagging conventions
6. **Track time accurately**: Update `actualHours` regularly for better estimates
7. **Clean up completed tasks**: Archive or remove tasks older than retention policy
8. **Validate before save**: Check JSON syntax and required fields
9. **Use meaningful priorities**: Align team on priority scale meaning
10. **Document in context**: Record decisions and blockers in `context.notes`
