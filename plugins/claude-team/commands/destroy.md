---
namespace: team
description: Gracefully shutdown team or specific member
argument-hint: [team-name] [--member=name]
allowed-tools: Bash, Read, Glob, AskUserQuestion, SendMessage, TeamDelete
---

# Team/Member Shutdown and Deletion

This command handles graceful shutdown of team members or complete team deletion.

## Step 1: Parse Arguments

Parse $ARGUMENTS to extract:
- Team name (first positional argument)
- --member option (if provided)

Expected formats:
- `/team:destroy` (no arguments - interactive mode)
- `/team:destroy team-name` (delete entire team)
- `/team:destroy team-name --member=member-name` (shutdown specific member only)

## Step 2: Interactive Team Selection (if no arguments)

If no team name provided:

1. Scan for active teams: `!\`ls -1 ~/.claude/teams/ 2>/dev/null || echo ""\``

2. If no teams found:
   - Report: "No active teams found. Nothing to destroy."
   - Exit

3. If teams found, use AskUserQuestion:
   - question: "Which team would you like to destroy?"
   - header: "Team"
   - options: [List each team directory name found]

4. Set selected team as target for destruction

## Step 3: Verify Team Exists

Read team configuration: `@~/.claude/teams/$TEAM_NAME/config.json`

If file doesn't exist:
- Report error: "Team '$TEAM_NAME' not found. Use /team:list to see active teams."
- Exit

## Step 4A: Single Member Shutdown (if --member specified)

Extract member name from `--member=name` option.

### Verify Member Exists

Check config.json for member:
```bash
!\`jq --arg name "$MEMBER_NAME" '.members[] | select(.name == $name) | .isActive' ~/.claude/teams/$TEAM_NAME/config.json\`
```

If member not found:
- Report error: "Member '$MEMBER_NAME' not found in team '$TEAM_NAME'"
- List available members from config.json
- Exit

If member is already inactive (isActive: false):
- Report: "Member '$MEMBER_NAME' is already inactive"
- Exit

### Request Shutdown

Send shutdown request using SendMessage tool:
```
{
  type: "shutdown_request",
  team_name: $TEAM_NAME,
  recipient: $MEMBER_NAME,
  content: "Graceful shutdown requested by coordinator. Please complete current work and confirm shutdown."
}
```

### Wait for Response

Poll inbox for shutdown response (check every 2 seconds, max 30 seconds):
```bash
!\`timeout 30 bash -c 'while true; do
  response=$(jq --arg from "$MEMBER_NAME" --arg type "shutdown_response" \
    "[.[] | select(.from == \$from and .type == \$type)] | length" \
    ~/.claude/teams/$TEAM_NAME/inboxes/coordinator.json 2>/dev/null || echo 0)
  if [ "$response" -gt 0 ]; then
    echo "approved"
    break
  fi
  sleep 2
done'\`
```

### Execute Shutdown

If shutdown approved:
1. Get tmux pane ID from config.json: `!\`jq --arg name "$MEMBER_NAME" '.members[] | select(.name == $name) | .tmuxPaneId' ~/.claude/teams/$TEAM_NAME/config.json\``
2. Kill pane: `!\`tmux kill-pane -t $PANE_ID 2>/dev/null\``
3. Update config.json to mark inactive (use jq to set `.members[] | select(.name == $MEMBER_NAME) | .isActive = false`)
4. Report success: "Member '$MEMBER_NAME' shutdown successfully"

If timeout or denial:
- Report warning: "Member '$MEMBER_NAME' did not respond or denied shutdown"
- Offer force option via AskUserQuestion:
  - question: "Force shutdown without confirmation?"
  - header: "Force"
  - options: ["Yes (Force kill pane)", "No (Cancel)"]
- If "Yes": Execute shutdown steps above
- If "No": Exit without changes

## Step 4B: Full Team Deletion (if --member not specified)

### Get All Active Members

Extract active members from config.json:
```bash
!\`jq '.members[] | select(.isActive == true) | .name' ~/.claude/teams/$TEAM_NAME/config.json\`
```

### Shutdown Each Member

For each active member found:

1. Send shutdown_request via SendMessage tool (same as Step 4A)
2. Wait for response (2 second polls, max 20 seconds per member)
3. If approved, kill tmux pane
4. Update member's isActive to false in config.json
5. Track successes and failures

### Verify All Members Inactive

After shutdown attempts, verify no active members remain:
```bash
!\`jq '[.members[] | select(.isActive == true)] | length' ~/.claude/teams/$TEAM_NAME/config.json\`
```

If count > 0:
- List remaining active members
- Report warning: "Some members are still active. Team deletion requires all members to be shutdown."
- Use AskUserQuestion:
  - question: "Force shutdown remaining members and delete team?"
  - header: "Force delete"
  - options: ["Yes (Force delete)", "No (Cancel)"]
- If "No": Exit without deleting team
- If "Yes": Continue to deletion

### Cleanup Window Mode Windows

윈도우 모드(`--window`)로 스폰된 팀의 빈 윈도우를 정리합니다. tmux는 마지막 pane이 종료되면 윈도우를 자동 삭제하므로 대부분 불필요하지만, 방어적 안전장치입니다:

```bash
TMUX_SESSION=$(tmux display-message -p '#S')
tmux list-windows -t "${TMUX_SESSION}" -F '#{window_name} #{window_panes}' | while read name panes; do
  if echo "$name" | grep -q "^${TEAM_NAME}-" && [ "$panes" -le 1 ]; then
    # pane 수가 0이면 빈 윈도우, 1이면 기본 shell만 남은 윈도우
    PANE_CMD=$(tmux list-panes -t "${TMUX_SESSION}:${name}" -F '#{pane_current_command}' 2>/dev/null | head -1)
    if [ -z "$PANE_CMD" ] || echo "$PANE_CMD" | grep -qE '^(zsh|bash)$'; then
      tmux kill-window -t "${TMUX_SESSION}:${name}" 2>/dev/null
    fi
  fi
done
```

### Delete Team

Execute TeamDelete tool:
```
{
  team_name: $TEAM_NAME
}
```

### Cleanup Verification

Verify deletion:
```bash
!\`[ ! -d ~/.claude/teams/$TEAM_NAME ] && echo "deleted" || echo "exists"\`
```

If "deleted":
- Report success: "Team '$TEAM_NAME' successfully destroyed"
- Summary:
  - Members shutdown: [count]
  - Team directory removed: ~/.claude/teams/$TEAM_NAME
  - Task artifacts removed: ~/.claude/tasks/$TEAM_NAME (if exists)

If "exists":
- Report error: "Team deletion failed. Directory still exists."
- Suggest manual cleanup steps

## Error Handling

Throughout execution:

- **Permission errors**: Report clear message about file permissions
- **Tmux errors**: Check if tmux is available and accessible
- **JSON parsing errors**: Report malformed config.json and suggest recovery
- **Network/timeout**: Report timeouts clearly and offer retry

## Final Report Format

### Single Member Shutdown Success
```
Member Shutdown Complete
------------------------
Team: $TEAM_NAME
Member: $MEMBER_NAME
Status: Shutdown successfully
Tmux pane: Terminated
Config updated: Yes
```

### Full Team Deletion Success
```
Team Destruction Complete
-------------------------
Team: $TEAM_NAME
Members shutdown: [count] of [total]
Failed shutdowns: [count] (if any)
Team deleted: Yes
Directories removed:
  - ~/.claude/teams/$TEAM_NAME
  - ~/.claude/tasks/$TEAM_NAME
```

### Partial Success
```
Team Destruction Incomplete
---------------------------
Team: $TEAM_NAME
Members shutdown: [count] of [total]
Still active: [list names]
Team deleted: No

Next steps:
1. Investigate why members didn't shutdown
2. Force shutdown: /team:destroy $TEAM_NAME [with force option]
3. Manual cleanup: tmux kill-pane -t [pane-ids]
```

## Safety Checks

Before any destructive operation:
- Confirm team exists
- Verify member existence (if targeting specific member)
- Always attempt graceful shutdown first
- Require confirmation for force operations
- Preserve data integrity (don't partially delete)

Use SendMessage for all shutdown requests to allow members to clean up gracefully.
Use TeamDelete only after verifying all members are inactive.
