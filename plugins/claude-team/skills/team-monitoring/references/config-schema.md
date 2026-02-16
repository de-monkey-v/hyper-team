# Team Configuration Schema

Complete specification for `~/.claude/teams/{team-name}/config.json`.

## Schema Definition

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["name", "created", "members"],
  "properties": {
    "name": {
      "type": "string",
      "description": "Unique team identifier",
      "pattern": "^[a-z0-9-]+$",
      "minLength": 1,
      "maxLength": 64
    },
    "created": {
      "type": "string",
      "format": "date-time",
      "description": "ISO 8601 timestamp of team creation"
    },
    "description": {
      "type": "string",
      "description": "Optional team purpose description"
    },
    "members": {
      "type": "array",
      "description": "List of team member configurations",
      "items": {
        "$ref": "#/definitions/member"
      }
    },
    "settings": {
      "type": "object",
      "description": "Optional team-level settings",
      "properties": {
        "maxConcurrentTasks": {
          "type": "integer",
          "minimum": 1,
          "default": 10
        },
        "defaultModel": {
          "type": "string",
          "enum": ["opus", "sonnet", "haiku"]
        },
        "timeoutMinutes": {
          "type": "integer",
          "minimum": 5,
          "default": 30
        }
      }
    }
  },
  "definitions": {
    "member": {
      "type": "object",
      "required": ["name", "role", "model", "isActive"],
      "properties": {
        "name": {
          "type": "string",
          "description": "Unique member identifier within team",
          "pattern": "^[a-z0-9-]+$",
          "minLength": 1,
          "maxLength": 32
        },
        "role": {
          "type": "string",
          "description": "Member's primary function",
          "examples": [
            "implementer",
            "tester",
            "reviewer",
            "coordinator",
            "specialist"
          ]
        },
        "model": {
          "type": "string",
          "description": "Claude model variant",
          "enum": ["opus", "sonnet", "haiku"]
        },
        "isActive": {
          "type": "boolean",
          "description": "Whether member is currently active"
        },
        "tmuxPaneId": {
          "type": "string",
          "description": "Tmux pane identifier (e.g., %123)",
          "pattern": "^%[0-9]+$"
        },
        "sessionInfo": {
          "type": "object",
          "description": "Tmux session details",
          "properties": {
            "sessionName": {
              "type": "string"
            },
            "windowIndex": {
              "type": "integer"
            },
            "paneIndex": {
              "type": "integer"
            }
          }
        },
        "capabilities": {
          "type": "array",
          "description": "Special capabilities or permissions",
          "items": {
            "type": "string"
          },
          "examples": [
            ["read", "write"],
            ["execute-scripts", "network-access"]
          ]
        },
        "context": {
          "type": "string",
          "description": "Custom context or system prompt modifications"
        }
      }
    }
  }
}
```

## Complete Example

```json
{
  "name": "team-alpha",
  "created": "2026-02-15T09:30:00Z",
  "description": "Full-stack development team for API project",
  "members": [
    {
      "name": "backend-dev",
      "role": "implementer",
      "model": "sonnet",
      "isActive": true,
      "tmuxPaneId": "%42",
      "sessionInfo": {
        "sessionName": "team-alpha",
        "windowIndex": 0,
        "paneIndex": 0
      },
      "capabilities": ["read", "write", "execute-scripts"],
      "context": "Focus on backend API implementation with FastAPI"
    },
    {
      "name": "frontend-dev",
      "role": "implementer",
      "model": "sonnet",
      "isActive": true,
      "tmuxPaneId": "%43",
      "sessionInfo": {
        "sessionName": "team-alpha",
        "windowIndex": 0,
        "paneIndex": 1
      },
      "capabilities": ["read", "write"],
      "context": "Focus on React frontend development"
    },
    {
      "name": "tester",
      "role": "tester",
      "model": "haiku",
      "isActive": true,
      "tmuxPaneId": "%44",
      "sessionInfo": {
        "sessionName": "team-alpha",
        "windowIndex": 1,
        "paneIndex": 0
      },
      "capabilities": ["read", "execute-scripts"],
      "context": "Write and execute unit and integration tests"
    },
    {
      "name": "reviewer",
      "role": "reviewer",
      "model": "opus",
      "isActive": false,
      "capabilities": ["read"]
    }
  ],
  "settings": {
    "maxConcurrentTasks": 5,
    "defaultModel": "sonnet",
    "timeoutMinutes": 45
  }
}
```

## Field Descriptions

### Root Level

#### name
- **Type:** string
- **Required:** Yes
- **Pattern:** `^[a-z0-9-]+$`
- **Purpose:** Unique identifier used in directory paths and references
- **Examples:** `team-alpha`, `api-dev-team`, `frontend-crew`

#### created
- **Type:** ISO 8601 datetime string
- **Required:** Yes
- **Purpose:** Track team lifecycle and age
- **Format:** `YYYY-MM-DDTHH:MM:SSZ`

#### description
- **Type:** string
- **Required:** No
- **Purpose:** Human-readable team purpose
- **Length:** Recommend <200 characters

#### members
- **Type:** array of member objects
- **Required:** Yes
- **Purpose:** Define all team members and their configurations
- **Minimum:** 1 member

#### settings
- **Type:** object
- **Required:** No
- **Purpose:** Team-wide configuration overrides

### Member Object

#### name
- **Type:** string
- **Required:** Yes
- **Pattern:** `^[a-z0-9-]+$`
- **Uniqueness:** Must be unique within team
- **Usage:** Used in inbox paths and message routing

#### role
- **Type:** string
- **Required:** Yes
- **Common Values:**
  - `implementer`: Writes code and implements features
  - `tester`: Creates and runs tests
  - `reviewer`: Reviews code for quality
  - `coordinator`: Manages task assignment and team coordination
  - `specialist`: Domain-specific expert (e.g., database, security)
- **Purpose:** Determines member responsibilities and task assignment

#### model
- **Type:** enum string
- **Required:** Yes
- **Values:**
  - `opus`: Highest capability, best for complex reasoning
  - `sonnet`: Balanced capability and speed
  - `haiku`: Fast, efficient for simple tasks
- **Purpose:** Model selection affects cost and performance

#### isActive
- **Type:** boolean
- **Required:** Yes
- **Purpose:** Controls whether member receives tasks and messages
- **Note:** Set to `false` to pause a member without removing them

#### tmuxPaneId
- **Type:** string
- **Required:** Only if active
- **Format:** `%{number}` (e.g., `%42`)
- **Purpose:** Links config entry to running tmux pane
- **Retrieval:** From `tmux list-panes -F "#{pane_id}"`

#### sessionInfo
- **Type:** object
- **Required:** No
- **Purpose:** Additional tmux session context
- **Fields:**
  - `sessionName`: Tmux session containing the pane
  - `windowIndex`: Window number within session
  - `paneIndex`: Pane number within window

#### capabilities
- **Type:** array of strings
- **Required:** No
- **Purpose:** Define permissions for security/sandboxing
- **Common Values:**
  - `read`: Can read files
  - `write`: Can modify files
  - `execute-scripts`: Can run shell commands
  - `network-access`: Can make network requests
- **Default:** If unspecified, assume full access

#### context
- **Type:** string
- **Required:** No
- **Purpose:** Custom instructions appended to member's system prompt
- **Use Cases:**
  - Specialize in specific technology
  - Focus on particular codebase area
  - Enforce coding standards
  - Add domain knowledge

## Settings Object

### maxConcurrentTasks
- **Type:** integer
- **Default:** 10
- **Purpose:** Limit total tasks team can work on simultaneously
- **Recommendation:** 1-3 tasks per active member

### defaultModel
- **Type:** enum string
- **Default:** Inherits from plugin settings
- **Purpose:** Model used when creating new members
- **Values:** `opus`, `sonnet`, `haiku`

### timeoutMinutes
- **Type:** integer
- **Default:** 30
- **Purpose:** How long to wait for member response before considering them stuck
- **Recommendation:** 15-60 minutes depending on task complexity

## Modification Operations

### Adding a Member

```bash
jq '.members += [{
  "name": "new-member",
  "role": "implementer",
  "model": "sonnet",
  "isActive": false
}]' ~/.claude/teams/{team}/config.json > /tmp/config.json
mv /tmp/config.json ~/.claude/teams/{team}/config.json
```

### Activating a Member

```bash
jq '(.members[] | select(.name == "member-name") | .isActive) = true' \
  ~/.claude/teams/{team}/config.json > /tmp/config.json
mv /tmp/config.json ~/.claude/teams/{team}/config.json
```

### Updating tmuxPaneId

```bash
NEW_PANE_ID=$(tmux list-panes -F "#{pane_id}" | tail -n 1)
jq --arg pane "$NEW_PANE_ID" \
  '(.members[] | select(.name == "member-name") | .tmuxPaneId) = $pane' \
  ~/.claude/teams/{team}/config.json > /tmp/config.json
mv /tmp/config.json ~/.claude/teams/{team}/config.json
```

### Removing a Member

```bash
jq '.members = [.members[] | select(.name != "member-to-remove")]' \
  ~/.claude/teams/{team}/config.json > /tmp/config.json
mv /tmp/config.json ~/.claude/teams/{team}/config.json
```

## Validation

### Required Fields Check

```bash
jq -e '.name and .created and .members and (.members | length > 0)' \
  ~/.claude/teams/{team}/config.json
```

Exit code 0 = valid, non-zero = missing required fields.

### Member Uniqueness Check

```bash
jq '.members | map(.name) | group_by(.) | map(select(length > 1))' \
  ~/.claude/teams/{team}/config.json
```

Empty array = all unique, non-empty = duplicates exist.

### Active Member tmuxPaneId Check

```bash
jq -r '.members[] | select(.isActive == true and .tmuxPaneId == null) | .name' \
  ~/.claude/teams/{team}/config.json
```

Any output = active members missing tmuxPaneId.

### tmuxPaneId Existence Check

```bash
TMUX_PANES=$(tmux list-panes -a -F "#{pane_id}")
jq -r '.members[] | select(.isActive == true) | .tmuxPaneId' \
  ~/.claude/teams/{team}/config.json | \
  while read pane; do
    echo "$TMUX_PANES" | grep -q "$pane" || echo "Missing pane: $pane"
  done
```

## Migration Patterns

### V1 to V2 (Adding Settings)

If team config predates settings field:

```bash
jq '. + {settings: {maxConcurrentTasks: 10, timeoutMinutes: 30}}' \
  ~/.claude/teams/{team}/config.json > /tmp/config.json
mv /tmp/config.json ~/.claude/teams/{team}/config.json
```

### Adding sessionInfo

Populate from current tmux state:

```bash
jq --arg pane "%42" '
  (.members[] | select(.tmuxPaneId == $pane) | .sessionInfo) = {
    sessionName: "team-alpha",
    windowIndex: 0,
    paneIndex: 0
  }
' ~/.claude/teams/{team}/config.json > /tmp/config.json
```

## Best Practices

1. **Always backup before modification:**
   ```bash
   cp ~/.claude/teams/{team}/config.json{,.bak}
   ```

2. **Validate after changes:**
   ```bash
   jq empty ~/.claude/teams/{team}/config.json # Syntax check
   ```

3. **Use atomic writes:**
   ```bash
   jq '...' config.json > config.json.tmp && mv config.json.tmp config.json
   ```

4. **Keep member names descriptive:**
   - Good: `backend-api-dev`, `integration-tester`
   - Bad: `member1`, `worker`

5. **Document custom context:**
   - Include purpose and reasoning in context field
   - Keep context focused and concise

6. **Maintain tmuxPaneId accuracy:**
   - Update immediately after creating/recreating panes
   - Clean up when deactivating members
