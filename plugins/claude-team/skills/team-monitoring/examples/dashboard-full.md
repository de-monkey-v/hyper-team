# Complete Team Dashboard Example

This example demonstrates a full team monitoring dashboard output for an active development team.

## Scenario

Team "api-dev-team" working on a REST API project:
- 4 members with different roles
- 6 tasks in various states
- Some tasks blocked by dependencies
- Mix of active and idle members

## Dashboard Output

```markdown
# Team Monitoring Dashboard: api-dev-team

**Generated:** 2026-02-15T16:30:00Z

---

## Team Overview

| Metric | Value |
|--------|-------|
| **Team Name** | api-dev-team |
| **Created** | 2026-02-10T09:00:00Z (5 days ago) |
| **Total Members** | 4 |
| **Active Members** | 3 |
| **Total Tasks** | 6 |
| **Completed Tasks** | 2 (33%) |
| **In Progress** | 3 (50%) |
| **Pending** | 1 (17%) |

---

## Member Status

| Name | Role | Model | Status | Last Activity | Current Task | Inbox |
|------|------|-------|--------|---------------|-------------|-------|
| backend-api | implementer | sonnet | 🟢 Active | 2m ago | #3: Add pagination | 15 msgs |
| auth-specialist | implementer | sonnet | 🟢 Active | 5m ago | #4: Implement OAuth | 23 msgs |
| integration-tester | tester | haiku | 🟡 Idle | 45m ago | - | 8 msgs |
| code-reviewer | reviewer | opus | 🔴 Offline | 3h ago | - | 2 msgs |

### Status Legend
- 🟢 **Active**: Working on tasks, recently active
- 🟡 **Idle**: Online but no assigned tasks
- 🔴 **Offline**: Not responsive or deactivated

---

## Task Progress

| ID | Subject | Owner | Status | Progress | Priority | Blocked By | Blocks |
|----|---------|-------|--------|----------|----------|-----------|--------|
| 1 | Setup database schema | backend-api | ✅ Completed | 100% | 5 | - | #3, #6 |
| 2 | Create auth endpoints | auth-specialist | ✅ Completed | 100% | 5 | - | #4 |
| 3 | Add pagination support | backend-api | 🔄 In Progress | 70% | 4 | #1 | #5 |
| 4 | Implement OAuth flow | auth-specialist | 🔄 In Progress | 40% | 4 | #2 | - |
| 5 | Write integration tests | integration-tester | ⏸️ Pending | 0% | 3 | #3 | - |
| 6 | Add DB indexes | backend-api | 🔄 In Progress | 20% | 2 | #1 | - |

### Status Legend
- ✅ Completed
- 🔄 In Progress
- ⏸️ Pending
- ❌ Blocked

### Blocking Chains

```
Task #1 (Completed)
  ├─> Task #3 (In Progress, 70%)
  │     └─> Task #5 (Pending)
  └─> Task #6 (In Progress, 20%)

Task #2 (Completed)
  └─> Task #4 (In Progress, 40%)
```

---

## Activity Metrics (Last 24 Hours)

### Message Activity

| Member | 00-06 | 06-12 | 12-18 | 18-24 | Total |
|--------|-------|-------|-------|-------|-------|
| backend-api | ░░░░ | ████ | ████ | ██░░ | 47 |
| auth-specialist | ░░░░ | ░░██ | ████ | ░░░░ | 31 |
| integration-tester | ░░░░ | ░░░░ | ░░██ | ░░░░ | 12 |
| code-reviewer | ░░░░ | ░░░░ | ░░░░ | ░░░░ | 3 |

**Legend:** █ High activity (>10 msgs/period), ░ Low activity (<5 msgs/period)

### Task Completion Velocity

| Period | Completed | Started | Avg Time to Complete |
|--------|-----------|---------|---------------------|
| Today | 1 | 2 | 6.5 hours |
| Yesterday | 1 | 1 | 8.2 hours |
| This Week | 2 | 6 | 7.3 hours (avg) |

---

## Performance Insights

### Team Efficiency

| Metric | Value | Status |
|--------|-------|--------|
| **Average Task Completion Time** | 7.3 hours | ✅ Good |
| **Member Utilization Rate** | 75% | ✅ Good |
| **Task Blocking Rate** | 50% | ⚠️ High |
| **Idle Member Time** | 12% | ✅ Low |

### Recommendations

1. **High Blocking Rate (50%)**: Consider breaking down Task #1 dependencies or parallelizing work
2. **Code Reviewer Offline**: `code-reviewer` has been offline for 3 hours - reassign or activate backup
3. **Integration Tester Idle**: `integration-tester` waiting for Task #3 - consider assigning documentation or planning tasks
4. **Good Velocity**: Team completed 2 tasks this week with 7.3h avg - on track with estimates

---

## Recent Activity Log

### Last 10 Messages (Most Recent First)

```
[16:28] coordinator → backend-api: How's progress on Task #3 pagination?
[16:25] backend-api → coordinator: Completed API endpoint refactor, starting pagination logic
[16:20] coordinator → auth-specialist: OAuth implementation looking good, any blockers?
[16:15] auth-specialist → coordinator: Need clarification on token refresh strategy
[16:10] coordinator → all: Daily standup recap - focus on unblocking Task #5
[15:45] integration-tester → coordinator: idle_notification (reason: waiting for Task #3)
[15:30] backend-api → coordinator: Task #6 index creation in progress
[15:15] coordinator → backend-api: Prioritize Task #3 over Task #6
[15:00] auth-specialist → coordinator: OAuth endpoints 40% complete, testing locally
[14:45] coordinator → code-reviewer: Please review completed Task #1
```

---

## Alerts & Issues

### Active Alerts

⚠️ **Member Offline**: `code-reviewer` has not responded for 3 hours
- **Impact**: Code reviews delayed
- **Action**: Contact member or activate backup reviewer

⚠️ **Task Blocked**: Task #5 (integration tests) blocked by Task #3
- **Impact**: Testing phase delayed
- **Action**: Expedite Task #3 or assign other work to tester

ℹ️ **Member Idle**: `integration-tester` idle for 45 minutes
- **Impact**: Low - member available when Task #3 completes
- **Action**: None required, normal workflow

### System Health

✅ All active member processes running (verified via tmux)
✅ All inbox files accessible and under 5MB
✅ No orphaned tmux panes detected
✅ Task dependency graph has no circular dependencies

---

## Task Details (Expanded)

### Task #3: Add pagination support
- **Owner:** backend-api
- **Status:** In Progress (70%)
- **Priority:** 4 (High)
- **Created:** 2026-02-14T10:00:00Z (1.3 days ago)
- **Started:** 2026-02-14T11:30:00Z
- **Estimated:** 8 hours
- **Actual:** 5.5 hours (so far)
- **Blocked By:** Task #1 (✅ Completed)
- **Blocks:** Task #5 (Pending integration tests)
- **Progress Notes:**
  - [x] Design pagination API structure
  - [x] Implement offset-based pagination
  - [x] Add limit parameter validation
  - [ ] Implement cursor-based pagination
  - [ ] Add pagination metadata to responses
  - [ ] Update API documentation
- **Recent Activity:**
  - 16:25 - Completed API endpoint refactor
  - 15:30 - Started pagination logic implementation

### Task #4: Implement OAuth flow
- **Owner:** auth-specialist
- **Status:** In Progress (40%)
- **Priority:** 4 (High)
- **Created:** 2026-02-13T14:00:00Z (2.1 days ago)
- **Started:** 2026-02-14T09:00:00Z
- **Estimated:** 12 hours
- **Actual:** 8 hours (so far)
- **Blocked By:** Task #2 (✅ Completed)
- **Blocks:** None
- **Progress Notes:**
  - [x] Research OAuth 2.0 providers
  - [x] Setup OAuth client configuration
  - [x] Implement authorization endpoint
  - [ ] Implement token endpoint
  - [ ] Add token refresh mechanism
  - [ ] Implement token validation
- **Recent Activity:**
  - 16:15 - Requested clarification on token refresh
  - 15:00 - OAuth endpoints 40% complete, testing locally

---

## Next Steps

1. **Immediate Actions:**
   - Contact `code-reviewer` to check availability
   - Provide token refresh strategy guidance to `auth-specialist`
   - Monitor Task #3 progress for Task #5 unblocking

2. **Short-term (Next 4 Hours):**
   - Complete Task #3 pagination implementation
   - Advance Task #4 OAuth to 60%+ completion
   - Assign alternate work to `integration-tester` if Task #3 delays

3. **Medium-term (This Week):**
   - Complete all in-progress tasks (#3, #4, #6)
   - Start Task #5 integration testing
   - Review team blocking patterns and optimize task dependencies

---

**Dashboard End**
*Auto-generated by team-monitoring skill*
*Refresh: Run `cc team-status api-dev-team` or `/team-monitoring`*
```

## How This Dashboard Was Generated

### 1. Team Configuration Query

```bash
# Read team config
jq '.' ~/.claude/teams/api-dev-team/config.json

# Extract member information
jq '.members[] | {name, role, model, isActive, tmuxPaneId}' \
  ~/.claude/teams/api-dev-team/config.json
```

### 2. Member Status Determination

```bash
# Check tmux pane existence
tmux list-panes -a -F "#{pane_id} #{session_name}:#{window_index}.#{pane_index}"

# Check inbox activity
for member in backend-api auth-specialist integration-tester code-reviewer; do
  stat -c '%Y %s' ~/.claude/teams/api-dev-team/members/$member/inbox
done
```

### 3. Task Data Collection

```bash
# Get all tasks for this team
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -s '[.[] | select(.team == "api-dev-team")]'

# Calculate progress based on dependencies and status
jq -r 'if .status == "completed" then 100
       elif .status == "in_progress" then 50
       else 0 end' task.json
```

### 4. Activity Metrics Calculation

```bash
# Count messages by time period
PERIODS=("00:00-06:00" "06:00-12:00" "12:00-18:00" "18:00-24:00")

for period in "${PERIODS[@]}"; do
  START="${period%-*}"
  END="${period#*-}"

  jq -r --arg start "$START" --arg end "$END" \
    'select(.timestamp | split("T")[1] >= $start and split("T")[1] < $end)' \
    inbox | wc -l
done
```

### 5. Blocking Chain Analysis

```bash
# Recursively build dependency tree
function build_chain() {
  local task_id=$1
  local blocks=$(jq -r '.blocks[]' ~/.claude/tasks/$task_id/task.json)

  echo "Task $task_id"
  for blocked in $blocks; do
    echo "  ├─> $(build_chain $blocked)"
  done
}
```

### 6. Performance Calculation

```bash
# Average completion time
find ~/.claude/tasks -name "task.json" -exec cat {} \; | \
  jq -r 'select(.status == "completed" and .team == "api-dev-team") |
    ((.completed | fromdateiso8601) - (.started | fromdateiso8601)) / 3600' | \
  awk '{sum+=$1; count++} END {print sum/count}'
```

## Usage Examples

### Generate this dashboard

```bash
cc team-dashboard api-dev-team
```

### Monitor in real-time

```bash
watch -n 30 'cc team-dashboard api-dev-team'
```

### Export to file

```bash
cc team-dashboard api-dev-team > team-status-$(date +%Y%m%d-%H%M).md
```

### Filter for specific section

```bash
cc "Show me just the task progress for api-dev-team"
```
