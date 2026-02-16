#!/bin/bash
# Check for and optionally clean up orphaned tmux panes
# Orphaned panes are tmux panes running 'cc' that are not referenced in any team config

set -euo pipefail

CLAUDE_TEAMS_DIR="${HOME}/.claude/teams"
VERBOSE=false
AUTO_KILL=false

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS]

Check for orphaned Claude Code team member tmux panes.

Orphaned panes are tmux panes running 'cc' that are not referenced
in any team configuration file.

OPTIONS:
    -h, --help          Show this help message
    -v, --verbose       Show detailed output
    -k, --kill          Automatically kill orphaned panes (use with caution)
    -d, --dir DIR       Teams directory (default: ~/.claude/teams)

EXAMPLES:
    # Check for orphans
    $(basename "$0")

    # Verbose output
    $(basename "$0") -v

    # Auto-kill orphans
    $(basename "$0") -k

EOF
}

log_info() {
    echo -e "${GREEN}[INFO]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*"
}

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            usage
            exit 0
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -k|--kill)
            AUTO_KILL=true
            shift
            ;;
        -d|--dir)
            CLAUDE_TEAMS_DIR="$2"
            shift 2
            ;;
        *)
            log_error "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Verify teams directory exists
if [[ ! -d "$CLAUDE_TEAMS_DIR" ]]; then
    log_error "Teams directory not found: $CLAUDE_TEAMS_DIR"
    exit 1
fi

# Check if tmux is running
if ! tmux list-sessions &>/dev/null; then
    log_info "No tmux sessions running"
    exit 0
fi

# Create temp files
CONFIG_PANES=$(mktemp)
TMUX_PANES=$(mktemp)
trap 'rm -f "$CONFIG_PANES" "$TMUX_PANES"' EXIT

# Extract all paneIds from team configs
if [[ "$VERBOSE" == true ]]; then
    log_info "Scanning team configurations in $CLAUDE_TEAMS_DIR"
fi

find "$CLAUDE_TEAMS_DIR" -name "config.json" -type f 2>/dev/null | while read -r config; do
    if [[ "$VERBOSE" == true ]]; then
        team_name=$(jq -r '.name // "unknown"' "$config")
        log_info "  Processing team: $team_name"
    fi

    jq -r '.members[]? | select(.tmuxPaneId != null) | .tmuxPaneId' "$config" 2>/dev/null || true
done | sort -u > "$CONFIG_PANES"

config_count=$(wc -l < "$CONFIG_PANES")
if [[ "$VERBOSE" == true ]]; then
    log_info "Found $config_count pane IDs in team configurations"
fi

# Get all tmux panes running 'cc'
if [[ "$VERBOSE" == true ]]; then
    log_info "Scanning tmux panes"
fi

tmux list-panes -a -F "#{pane_id} #{pane_current_command}" 2>/dev/null | \
    grep " cc$" | \
    cut -d' ' -f1 | \
    sort -u > "$TMUX_PANES"

tmux_count=$(wc -l < "$TMUX_PANES")
if [[ "$VERBOSE" == true ]]; then
    log_info "Found $tmux_count tmux panes running 'cc'"
fi

# Find orphans (in tmux but not in config)
orphans=$(comm -13 "$CONFIG_PANES" "$TMUX_PANES")

if [[ -z "$orphans" ]]; then
    log_info "No orphaned panes found ✓"
    exit 0
fi

# Report orphans
orphan_count=$(echo "$orphans" | wc -l)
log_warn "Found $orphan_count orphaned pane(s):"
echo

for pane in $orphans; do
    # Get pane details
    pane_info=$(tmux list-panes -a -F "#{pane_id} #{session_name}:#{window_index}.#{pane_index} #{pane_current_command}" | \
        grep "^$pane ")

    echo "  Pane: $pane"
    echo "    Location: $(echo "$pane_info" | cut -d' ' -f2)"
    echo "    Command:  $(echo "$pane_info" | cut -d' ' -f3)"

    if [[ "$VERBOSE" == true ]]; then
        # Get pane title if available
        pane_title=$(tmux display-message -p -t "$pane" '#{pane_title}' 2>/dev/null || echo "N/A")
        echo "    Title:    $pane_title"

        # Check pane age
        pane_start=$(tmux display-message -p -t "$pane" '#{pane_start_time}' 2>/dev/null || echo "0")
        if [[ "$pane_start" != "0" ]]; then
            current_time=$(date +%s)
            age_seconds=$((current_time - pane_start))
            age_hours=$((age_seconds / 3600))
            age_minutes=$(( (age_seconds % 3600) / 60 ))
            echo "    Age:      ${age_hours}h ${age_minutes}m"
        fi
    fi
    echo
done

# Handle cleanup
if [[ "$AUTO_KILL" == true ]]; then
    log_warn "Auto-kill enabled, terminating orphaned panes..."
    for pane in $orphans; do
        if tmux kill-pane -t "$pane" 2>/dev/null; then
            log_info "Killed pane: $pane"
        else
            log_error "Failed to kill pane: $pane"
        fi
    done
else
    echo "To kill these panes, run:"
    for pane in $orphans; do
        echo "  tmux kill-pane -t $pane"
    done
    echo
    echo "Or run this script with --kill flag"
fi

exit 1  # Exit with error code to indicate orphans were found
