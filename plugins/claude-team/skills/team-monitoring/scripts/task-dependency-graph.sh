#!/bin/bash
# Generate visual task dependency graph
# Shows which tasks block which, and identifies circular dependencies

set -euo pipefail

CLAUDE_TASKS_DIR="${HOME}/.claude/tasks"
TEAM_NAME=""
OUTPUT_FORMAT="text"  # text or dot (graphviz)

usage() {
    cat << EOF
Usage: $(basename "$0") [OPTIONS] [TEAM_NAME]

Generate a visual task dependency graph showing blocking relationships.

ARGUMENTS:
    TEAM_NAME           Optional: Filter tasks by team name

OPTIONS:
    -h, --help          Show this help message
    -f, --format FMT    Output format: text (default) or dot (graphviz)
    -k, --tasks-dir DIR Tasks directory (default: ~/.claude/tasks)
    -c, --check-cycles  Check for circular dependencies only

EXAMPLES:
    # Generate text graph for all tasks
    $(basename "$0")

    # Generate for specific team
    $(basename "$0") team-alpha

    # Generate GraphViz dot format
    $(basename "$0") -f dot team-alpha | dot -Tpng > graph.png

    # Check for circular dependencies
    $(basename "$0") -c

EOF
}

CHECK_CYCLES_ONLY=false

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            usage
            exit 0
            ;;
        -f|--format)
            OUTPUT_FORMAT="$2"
            shift 2
            ;;
        -k|--tasks-dir)
            CLAUDE_TASKS_DIR="$2"
            shift 2
            ;;
        -c|--check-cycles)
            CHECK_CYCLES_ONLY=true
            shift
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

# Verify tasks directory exists
if [[ ! -d "$CLAUDE_TASKS_DIR" ]]; then
    echo "Error: Tasks directory not found: $CLAUDE_TASKS_DIR" >&2
    exit 1
fi

# Load all tasks into associative arrays
declare -A TASKS_ID
declare -A TASKS_SUBJECT
declare -A TASKS_STATUS
declare -A TASKS_OWNER
declare -A TASKS_TEAM
declare -A TASKS_BLOCKS
declare -A TASKS_BLOCKED_BY

load_tasks() {
    while IFS= read -r task_file; do
        local id=$(jq -r '.id // ""' "$task_file" 2>/dev/null || echo "")
        [[ -z "$id" ]] && continue

        # Filter by team if specified
        if [[ -n "$TEAM_NAME" ]]; then
            local team=$(jq -r '.team // ""' "$task_file" 2>/dev/null || echo "")
            [[ "$team" != "$TEAM_NAME" ]] && continue
        fi

        TASKS_ID["$id"]="$id"
        TASKS_SUBJECT["$id"]=$(jq -r '.subject // "Untitled"' "$task_file" 2>/dev/null || echo "Untitled")
        TASKS_STATUS["$id"]=$(jq -r '.status // "unknown"' "$task_file" 2>/dev/null || echo "unknown")
        TASKS_OWNER["$id"]=$(jq -r '.owner // ""' "$task_file" 2>/dev/null || echo "")
        TASKS_TEAM["$id"]=$(jq -r '.team // ""' "$task_file" 2>/dev/null || echo "")
        TASKS_BLOCKS["$id"]=$(jq -r '.blocks // [] | join(",")' "$task_file" 2>/dev/null || echo "")
        TASKS_BLOCKED_BY["$id"]=$(jq -r '.blockedBy // [] | join(",")' "$task_file" 2>/dev/null || echo "")
    done < <(find "$CLAUDE_TASKS_DIR" -name "task.json" -type f 2>/dev/null)
}

# Check for circular dependencies using DFS
check_circular_dependencies() {
    local task_id=$1
    local -n visited=$2
    local -n path=$3

    # If already in current path, we found a cycle
    for p in "${path[@]}"; do
        if [[ "$p" == "$task_id" ]]; then
            echo "CYCLE"
            return 1
        fi
    done

    # If already fully visited, no cycle from here
    for v in "${visited[@]}"; do
        if [[ "$v" == "$task_id" ]]; then
            return 0
        fi
    done

    # Add to current path
    path+=("$task_id")

    # Visit all tasks this task is blocked by
    local blocked_by="${TASKS_BLOCKED_BY[$task_id]}"
    if [[ -n "$blocked_by" ]]; then
        IFS=',' read -ra blockers <<< "$blocked_by"
        for blocker in "${blockers[@]}"; do
            [[ -z "$blocker" ]] && continue
            if ! check_circular_dependencies "$blocker" visited path; then
                echo "  $task_id (${TASKS_SUBJECT[$task_id]})"
                return 1
            fi
        done
    fi

    # Remove from current path, add to visited
    path=("${path[@]:0:${#path[@]}-1}")
    visited+=("$task_id")

    return 0
}

# Find all cycles
find_all_cycles() {
    local found_cycle=false

    for task_id in "${!TASKS_ID[@]}"; do
        local visited=()
        local path=()

        if ! check_circular_dependencies "$task_id" visited path 2>&1 | grep -q "CYCLE"; then
            continue
        fi

        if [[ "$found_cycle" == false ]]; then
            echo "⚠️ Circular dependencies detected:"
            echo
            found_cycle=true
        fi

        echo "Cycle involving task: $task_id (${TASKS_SUBJECT[$task_id]})"
        local visited=()
        local path=()
        check_circular_dependencies "$task_id" visited path | grep -v "CYCLE"
        echo
    done

    if [[ "$found_cycle" == false ]]; then
        echo "✅ No circular dependencies found"
    fi
}

# Generate text format graph
generate_text_graph() {
    # Find root tasks (not blocked by anything)
    local roots=()
    for task_id in "${!TASKS_ID[@]}"; do
        local blocked_by="${TASKS_BLOCKED_BY[$task_id]}"
        if [[ -z "$blocked_by" ]]; then
            roots+=("$task_id")
        fi
    done

    if [[ ${#roots[@]} -eq 0 ]]; then
        echo "No root tasks found (all tasks are blocked by something)"
        return
    fi

    # Print each root and its tree
    for root in "${roots[@]}"; do
        print_task_tree "$root" ""
        echo
    done
}

# Recursive function to print task tree
print_task_tree() {
    local task_id=$1
    local indent=$2
    local is_last=${3:-true}

    # Prevent infinite recursion (simple visited tracking)
    [[ "$indent" =~ "    " ]] && [[ "${indent//    /}" == "${indent//│   /}" ]] && return

    # Get task info
    local subject="${TASKS_SUBJECT[$task_id]}"
    local status="${TASKS_STATUS[$task_id]}"
    local owner="${TASKS_OWNER[$task_id]}"

    # Format status
    local status_symbol
    case "$status" in
        completed) status_symbol="✅" ;;
        in_progress) status_symbol="🔄" ;;
        pending) status_symbol="⏸️" ;;
        cancelled) status_symbol="❌" ;;
        *) status_symbol="❓" ;;
    esac

    # Print current task
    local short_id="${task_id:0:8}"
    local tree_char="├──"
    [[ "$is_last" == true ]] && tree_char="└──"

    if [[ -z "$indent" ]]; then
        echo "$status_symbol Task $short_id: $subject"
        [[ -n "$owner" ]] && echo "    Owner: $owner | Status: $status"
    else
        echo "${indent}${tree_char} $status_symbol Task $short_id: $subject"
    fi

    # Get tasks this blocks
    local blocks="${TASKS_BLOCKS[$task_id]}"
    if [[ -z "$blocks" ]]; then
        return
    fi

    # Parse blocked tasks
    IFS=',' read -ra blocked_tasks <<< "$blocks"
    local count=${#blocked_tasks[@]}

    for i in "${!blocked_tasks[@]}"; do
        local blocked="${blocked_tasks[$i]}"
        [[ -z "$blocked" ]] && continue

        # Skip if task doesn't exist (might be from different team)
        [[ -z "${TASKS_ID[$blocked]:-}" ]] && continue

        local new_indent
        if [[ -z "$indent" ]]; then
            new_indent="    "
        else
            if [[ "$is_last" == true ]]; then
                new_indent="${indent}    "
            else
                new_indent="${indent}│   "
            fi
        fi

        local is_last_child=false
        [[ $((i + 1)) -eq $count ]] && is_last_child=true

        print_task_tree "$blocked" "$new_indent" "$is_last_child"
    done
}

# Generate GraphViz DOT format
generate_dot_graph() {
    cat << EOF
digraph TaskDependencies {
    rankdir=TB;
    node [shape=box, style=rounded];

EOF

    # Define nodes
    for task_id in "${!TASKS_ID[@]}"; do
        local short_id="${task_id:0:8}"
        local subject="${TASKS_SUBJECT[$task_id]}"
        local status="${TASKS_STATUS[$task_id]}"
        local owner="${TASKS_OWNER[$task_id]}"

        # Color by status
        local color
        case "$status" in
            completed) color="lightgreen" ;;
            in_progress) color="lightblue" ;;
            pending) color="lightyellow" ;;
            cancelled) color="lightgray" ;;
            *) color="white" ;;
        esac

        local label="$short_id\\n$subject"
        [[ -n "$owner" ]] && label="$label\\n($owner)"

        echo "    \"$task_id\" [label=\"$label\", fillcolor=\"$color\", style=\"filled,rounded\"];"
    done

    echo

    # Define edges (blocking relationships)
    for task_id in "${!TASKS_ID[@]}"; do
        local blocks="${TASKS_BLOCKS[$task_id]}"
        [[ -z "$blocks" ]] && continue

        IFS=',' read -ra blocked_tasks <<< "$blocks"
        for blocked in "${blocked_tasks[@]}"; do
            [[ -z "$blocked" ]] && continue
            [[ -z "${TASKS_ID[$blocked]:-}" ]] && continue

            echo "    \"$task_id\" -> \"$blocked\" [label=\"blocks\"];"
        done
    done

    cat << EOF

    labelloc="t";
    label="Task Dependency Graph";
}
EOF
}

# Main execution
load_tasks

if [[ ${#TASKS_ID[@]} -eq 0 ]]; then
    echo "No tasks found"
    if [[ -n "$TEAM_NAME" ]]; then
        echo "Team filter: $TEAM_NAME"
    fi
    exit 0
fi

if [[ "$CHECK_CYCLES_ONLY" == true ]]; then
    find_all_cycles
    exit 0
fi

case "$OUTPUT_FORMAT" in
    text)
        echo "# Task Dependency Graph"
        [[ -n "$TEAM_NAME" ]] && echo "Team: $TEAM_NAME"
        echo
        echo "Total tasks: ${#TASKS_ID[@]}"
        echo
        generate_text_graph
        ;;
    dot)
        generate_dot_graph
        ;;
    *)
        echo "Error: Unknown format: $OUTPUT_FORMAT" >&2
        echo "Supported formats: text, dot" >&2
        exit 1
        ;;
esac
