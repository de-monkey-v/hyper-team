#!/usr/bin/env bash
set -euo pipefail

# Team cleanup on session end
# Finds teams whose leadSessionId matches the ending session and cleans them up

TEAMS_DIR="$HOME/.claude/teams"
TASKS_DIR="$HOME/.claude/tasks"

INPUT=$(cat)
SESSION_ID=$(echo "$INPUT" | jq -r '.session_id // empty')

[ -z "$SESSION_ID" ] && { echo '{"continue":true}'; exit 0; }
[ ! -d "$TEAMS_DIR" ] && { echo '{"continue":true}'; exit 0; }

for config in "$TEAMS_DIR"/*/config.json; do
  [ -f "$config" ] || continue
  TEAM_DIR=$(dirname "$config")
  TEAM_NAME=$(basename "$TEAM_DIR")
  LEAD_SESSION=$(jq -r '.leadSessionId // empty' "$config" 2>/dev/null)
  [ "$LEAD_SESSION" != "$SESSION_ID" ] && continue

  # Kill active member panes
  jq -r '.members[] | select(.isActive==true and .tmuxPaneId!=null and .tmuxPaneId!="") | .tmuxPaneId' "$config" 2>/dev/null | while read -r pid; do
    tmux kill-pane -t "$pid" 2>/dev/null || true
  done

  # Kill window-mode windows
  tmux list-windows -a -F "#{window_id} #{window_name}" 2>/dev/null | grep "${TEAM_NAME}-" | while read -r wid _; do
    tmux kill-window -t "$wid" 2>/dev/null || true
  done

  # Remove directories
  rm -rf "$TEAM_DIR"
  [ -d "$TASKS_DIR/$TEAM_NAME" ] && rm -rf "$TASKS_DIR/$TEAM_NAME"
done

echo '{"continue":true}'
