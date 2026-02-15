#!/bin/bash
# Generate a comprehensive team monitoring dashboard
# Outputs markdown-formatted dashboard with team status, member activity, and task progress

set -euo pipefail

CLAUDE_TEAMS_DIR="${HOME}/.claude/teams"
CLAUDE_TASKS_DIR="${HOME}/.claude/tasks"
TEAM_NAME=""
OUTPUT_FILE=""

usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS] TEAM_NAME

Generate a comprehensive monitoring dashboard for a Claude agent team.

ARGUMENTS:
    TEAM_NAME           Name of the team to monitor

OPTIONS:
    -h, --help          Show this help message
    -o, --output FILE   Write output to file instead of stdout
    -t, --teams-dir DIR Teams directory (default: ~/.claude/teams)
    -k, --tasks-dir DIR Tasks directory (default: ~/.claude/tasks)

EXAMPLES:
    # Generate dashboard for team-alpha
    $(basename "$0") team-alpha

    # Save to file
    $(basename "$0") team-alpha -o dashboard.md

EOF
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            usage
            exit 0
            ;;
        -o|--output)
            OUTPUT_FILE="$2"
            shift 2
            ;;
        -t|--teams-dir)
            CLAUDE_TEAMS_DIR="$2"
            shift 2
            ;;
        -k|--tasks-dir)
            CLAUDE_TASKS_DIR="$2"
            shift 2
            ;;
        -*)
            echo "Error: Unknown option: $1" >&2
            usage
            exit 1
            ;;
        *)
            TEAM_NAME="$1"
            shift
            ;;
    esac
done

if [[ -z "$TEAM_NAME" ]]; then
    echo "Error: TEAM_NAME required" >&2
    usage
    exit 1
fi

TEAM_DIR="$CLAUDE_TEAMS_DIR/$TEAM_NAME"
CONFIG_FILE="$TEAM_DIR/config.json"

# Verify team exists
if [[ ! -f "$CONFIG_FILE" ]]; then
    echo "Error: Team not found: $TEAM_NAME" >&2
    echo "Config file not found: $CONFIG_FILE" >&2
    exit 1
fi

# Helper functions
get_member_status() {
    local member_name=$1
    local is_active=$2
    local pane_id=$3
    local inbox_file="$TEAM_DIR/members/$member_name/inbox"

    # Check if pane exists
    if [[ -n "$pane_id" ]] && tmux list-panes -a -F "#{pane_id}" 2>/dev/null | grep -q "^$pane_id$"; then
        # Check inbox modification time
        if [[ -f "$inbox_file" ]]; then
            local mod_time=$(stat -c %Y "$inbox_file" 2>/dev/null || echo 0)
            local current_time=$(date +%s)
            local age=$((current_time - mod_time))

            if [[ $age -lt 300 ]]; then  # Less than 5 minutes
                echo "🟢 Active"
            elif [[ $age -lt 3600 ]]; then  # Less than 1 hour
                echo "🟡 Idle"
            else
                echo "🟠 Stale"
            fi
        else
            echo "🟡 Idle"
        fi
    elif [[ "$is_active" == "true" ]]; then
        echo "🔴 Offline"
    else
        echo "⚫ Inactive"
    fi
}

get_last_activity() {
    local member_name=$1
    local inbox_file="$TEAM_DIR/members/$member_name/inbox"

    if [[ ! -f "$inbox_file" ]]; then
        echo "-"
        return
    fi

    local mod_time=$(stat -c %Y "$inbox_file" 2>/dev/null || echo 0)
    if [[ $mod_time -eq 0 ]]; then
        echo "-"
        return
    fi

    local current_time=$(date +%s)
    local age=$((current_time - mod_time))

    if [[ $age -lt 60 ]]; then
        echo "${age}s ago"
    elif [[ $age -lt 3600 ]]; then
        echo "$((age / 60))m ago"
    elif [[ $age -lt 86400 ]]; then
        echo "$((age / 3600))h ago"
    else
        echo "$((age / 86400))d ago"
    fi
}

get_inbox_count() {
    local member_name=$1
    local inbox_file="$TEAM_DIR/members/$member_name/inbox"

    if [[ ! -f "$inbox_file" ]]; then
        echo "0"
        return
    fi

    wc -l < "$inbox_file"
}

# Generate dashboard
generate_dashboard() {
    local timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)

    cat << EOF
# Team Monitoring Dashboard: $TEAM_NAME

**Generated:** $timestamp

---

## Team Overview

EOF

    # Parse config
    local team_created=$(jq -r '.created // "N/A"' "$CONFIG_FILE")
    local total_members=$(jq '.members | length' "$CONFIG_FILE")
    local active_members=$(jq '[.members[] | select(.isActive == true)] | length' "$CONFIG_FILE")

    # Count tasks
    local total_tasks=0
    local completed_tasks=0
    local in_progress_tasks=0
    local pending_tasks=0

    if [[ -d "$CLAUDE_TASKS_DIR" ]]; then
        while IFS= read -r task_file; do
            local team=$(jq -r '.team // ""' "$task_file" 2>/dev/null || echo "")
            if [[ "$team" == "$TEAM_NAME" ]]; then
                ((total_tasks++))
                local status=$(jq -r '.status' "$task_file" 2>/dev/null || echo "")
                case "$status" in
                    completed) ((completed_tasks++)) ;;
                    in_progress) ((in_progress_tasks++)) ;;
                    pending) ((pending_tasks++)) ;;
                esac
            fi
        done < <(find "$CLAUDE_TASKS_DIR" -name "task.json" -type f 2>/dev/null)
    fi

    cat << EOF
| Metric | Value |
|--------|-------|
| **Team Name** | $TEAM_NAME |
| **Created** | $team_created |
| **Total Members** | $total_members |
| **Active Members** | $active_members |
| **Total Tasks** | $total_tasks |
| **Completed Tasks** | $completed_tasks ($(( total_tasks > 0 ? completed_tasks * 100 / total_tasks : 0 ))%) |
| **In Progress** | $in_progress_tasks ($(( total_tasks > 0 ? in_progress_tasks * 100 / total_tasks : 0 ))%) |
| **Pending** | $pending_tasks ($(( total_tasks > 0 ? pending_tasks * 100 / total_tasks : 0 ))%) |

---

## Member Status

| Name | Role | Model | Status | Last Activity | Inbox Messages |
|------|------|-------|--------|---------------|----------------|
EOF

    jq -r '.members[] | [.name, .role, .model, .isActive, .paneId // ""] | @tsv' "$CONFIG_FILE" | \
    while IFS=$'\t' read -r name role model is_active pane_id; do
        local status=$(get_member_status "$name" "$is_active" "$pane_id")
        local last_activity=$(get_last_activity "$name")
        local inbox_count=$(get_inbox_count "$name")

        echo "| $name | $role | $model | $status | $last_activity | $inbox_count |"
    done

    cat << EOF

### Status Legend
- 🟢 **Active**: Working, recently active (<5min)
- 🟡 **Idle**: Online but no recent activity (5min-1h)
- 🟠 **Stale**: No activity for >1 hour
- 🔴 **Offline**: Process not running
- ⚫ **Inactive**: Deactivated in config

---

## Task Progress

EOF

    if [[ $total_tasks -eq 0 ]]; then
        echo "_No tasks found for this team_"
    else
        cat << EOF
| ID | Subject | Owner | Status | Priority | Blocked By |
|----|---------|-------|--------|----------|-----------|
EOF

        find "$CLAUDE_TASKS_DIR" -name "task.json" -type f 2>/dev/null | while read -r task_file; do
            local team=$(jq -r '.team // ""' "$task_file" 2>/dev/null || echo "")
            if [[ "$team" == "$TEAM_NAME" ]]; then
                local id=$(jq -r '.id // ""' "$task_file")
                local subject=$(jq -r '.subject // "Untitled"' "$task_file")
                local owner=$(jq -r '.owner // "-"' "$task_file")
                local status=$(jq -r '.status // "unknown"' "$task_file")
                local priority=$(jq -r '.priority // "-"' "$task_file")
                local blocked_by=$(jq -r 'if .blockedBy and (.blockedBy | length) > 0 then (.blockedBy | length | tostring) + " tasks" else "-" end' "$task_file")

                # Format status with emoji
                local status_emoji
                case "$status" in
                    completed) status_emoji="✅ Completed" ;;
                    in_progress) status_emoji="🔄 In Progress" ;;
                    pending) status_emoji="⏸️ Pending" ;;
                    cancelled) status_emoji="❌ Cancelled" ;;
                    *) status_emoji="❓ $status" ;;
                esac

                # Truncate ID for display
                local short_id="${id:0:8}..."

                echo "| $short_id | $subject | $owner | $status_emoji | $priority | $blocked_by |"
            fi
        done
    fi

    cat << EOF

---

## Recent Activity

### Inbox Summary (Last 5 Messages Per Member)

EOF

    jq -r '.members[] | select(.isActive == true) | .name' "$CONFIG_FILE" | while read -r member_name; do
        local inbox_file="$TEAM_DIR/members/$member_name/inbox"

        if [[ ! -f "$inbox_file" ]] || [[ ! -s "$inbox_file" ]]; then
            echo "#### $member_name"
            echo "_No messages_"
            echo
            continue
        fi

        echo "#### $member_name"
        echo '```'
        tail -n 5 "$inbox_file" | while IFS= read -r line; do
            local msg_type=$(echo "$line" | jq -r '.type // "unknown"' 2>/dev/null || echo "unknown")
            local msg_from=$(echo "$line" | jq -r '.from // "unknown"' 2>/dev/null || echo "unknown")
            local msg_timestamp=$(echo "$line" | jq -r '.timestamp // "unknown"' 2>/dev/null || echo "unknown")

            # Try to extract meaningful content
            local msg_preview=""
            case "$msg_type" in
                task_assignment)
                    local task_subject=$(echo "$line" | jq -r '.content.subject // ""' 2>/dev/null || echo "")
                    msg_preview="Task assigned: $task_subject"
                    ;;
                message)
                    msg_preview=$(echo "$line" | jq -r '.content.text // ""' 2>/dev/null || echo "")
                    ;;
                *)
                    msg_preview="$msg_type"
                    ;;
            esac

            # Truncate preview
            if [[ ${#msg_preview} -gt 60 ]]; then
                msg_preview="${msg_preview:0:57}..."
            fi

            echo "[$msg_timestamp] $msg_from → $msg_type: $msg_preview"
        done
        echo '```'
        echo
    done

    cat << EOF

---

## System Health

EOF

    local health_checks=()

    # Check for orphaned panes
    local config_panes=$(jq -r '.members[]? | select(.paneId != null) | .paneId' "$CONFIG_FILE" | sort)
    local orphan_count=0
    if command -v tmux &>/dev/null && tmux list-sessions &>/dev/null 2>&1; then
        local tmux_panes=$(tmux list-panes -a -F "#{pane_id}" 2>/dev/null | grep "^%[0-9]" || true)
        for pane in $tmux_panes; do
            if ! echo "$config_panes" | grep -q "$pane"; then
                ((orphan_count++))
            fi
        done
    fi

    if [[ $orphan_count -eq 0 ]]; then
        health_checks+=("✅ No orphaned tmux panes detected")
    else
        health_checks+=("⚠️ Found $orphan_count orphaned tmux pane(s)")
    fi

    # Check inbox sizes
    local large_inbox_count=0
    jq -r '.members[] | .name' "$CONFIG_FILE" | while read -r member_name; do
        local inbox_file="$TEAM_DIR/members/$member_name/inbox"
        if [[ -f "$inbox_file" ]]; then
            local size=$(stat -c %s "$inbox_file" 2>/dev/null || echo 0)
            if [[ $size -gt 10485760 ]]; then  # 10MB
                ((large_inbox_count++))
            fi
        fi
    done

    if [[ $large_inbox_count -eq 0 ]]; then
        health_checks+=("✅ All inbox files under 10MB")
    else
        health_checks+=("⚠️ Found $large_inbox_count inbox file(s) over 10MB")
    fi

    # Check active members have paneIds
    local missing_pane_count=$(jq '[.members[] | select(.isActive == true and .paneId == null)] | length' "$CONFIG_FILE")
    if [[ $missing_pane_count -eq 0 ]]; then
        health_checks+=("✅ All active members have paneId assigned")
    else
        health_checks+=("⚠️ $missing_pane_count active member(s) missing paneId")
    fi

    # Print health checks
    for check in "${health_checks[@]}"; do
        echo "$check"
    done

    cat << EOF

---

**Dashboard End**

*Auto-generated by team-monitoring skill*
*Team: $TEAM_NAME | Generated: $timestamp*
EOF
}

# Execute and output
if [[ -n "$OUTPUT_FILE" ]]; then
    generate_dashboard > "$OUTPUT_FILE"
    echo "Dashboard written to: $OUTPUT_FILE" >&2
else
    generate_dashboard
fi
