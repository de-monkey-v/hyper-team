---
description: Interactive team creation with AI-powered design
allowed-tools: Bash, Read, Write, Glob, Grep, AskUserQuestion, Task, TeamCreate, TeamDelete, SendMessage, TaskCreate, TaskUpdate, TaskList, TaskGet
namespace: team
---

# Team Creation Workflow

Create a new team through an interactive design process. This command orchestrates the entire team creation lifecycle by delegating design to the team-architect agent and then directly spawning teammates.

<task-context>
<plugin-path>./plugins/claude-team</plugin-path>
<component-name>create</component-name>
<mode>plugin</mode>
</task-context>

## Phase 1: Gather Requirements

**Goal**: Understand what team the user wants to create.

**Actions**:

1. **Parse User Input**:

   Check if $ARGUMENTS is provided:
   - If $ARGUMENTS is not empty → Use it as the team purpose/description
   - If $ARGUMENTS is empty → Use AskUserQuestion to gather requirements

2. **Interactive Requirements Gathering** (if $ARGUMENTS is empty):

   Use AskUserQuestion:

   **Question 1: Team Purpose**
   - header: "Purpose"
   - question: "What should this team accomplish?"
   - options:
     - Implement new feature (Build functionality from scratch)
     - Code review (Review and improve existing code)
     - Research & analysis (Investigate and analyze problem)
     - Testing & QA (Test and validate functionality)
     - Documentation (Write docs and guides)
     - Custom (Other purpose)

   **Question 2: Team Size**
   - header: "Size"
   - question: "How many team members do you need?"
   - options:
     - 2 members (Small focused team)
     - 3 members (Standard team)
     - 4 members (Larger team for complex work)

   Combine answers into a requirements description:
   ```
   Purpose: [answer from Q1]
   Team Size: [answer from Q2]
   Additional Context: [any details from $ARGUMENTS]
   ```

**Output**: Clear requirements description for team-architect agent

---

## Phase 2: Delegate Design to Team Architect

**Goal**: Get optimal team configuration designed by the team-architect agent.

**Actions**:

1. **Call team-architect agent via Task tool**:

   ```
   Task tool parameters:
   - subagent_type: "claude-team:team-architect"
   - prompt: [Pass requirements from Phase 1]
   ```

   The prompt should be:
   ```
   Design a team for the following requirements:

   {requirements from Phase 1}

   Please analyze the requirements, select an optimal team pattern, design the team members, create task breakdown, and return the final JSON configuration.
   ```

2. **Wait for Agent Response**:

   The team-architect agent will:
   - Analyze requirements
   - Select optimal team pattern (Small Focused, Uniform Workers, Specialized Workers, Full-Stack)
   - Design member roles, models, prompts, colors
   - Create task breakdown
   - Present design to user for approval via AskUserQuestion
   - Return final JSON configuration wrapped in ```json code block

3. **Extract JSON Configuration**:

   Parse the agent's response to extract the JSON configuration:
   - Look for ```json code block in agent output
   - Extract JSON between the code fences
   - Parse JSON into object

   Expected JSON structure:
   ```json
   {
     "team_name": "auto-generated-name",
     "description": "Team purpose",
     "members": [
       {
         "name": "member-1",
         "model": "sonnet",
         "prompt": "You are a...",
         "color": "blue"
       }
     ],
     "tasks": [
       {
         "subject": "Task title",
         "description": "Task details",
         "owner": "member-1"
       }
     ]
   }
   ```

**Output**: Parsed JSON team configuration

---

## Phase 3: Execute Team Creation

**Goal**: Create team structure and spawn all teammates.

**Actions**:

1. **Create Team Structure**:

   Use TeamCreate tool:
   ```
   TeamCreate(
     team_name: {from JSON.team_name},
     description: {from JSON.description}
   )
   ```

   This creates:
   - `~/.claude/teams/{team-name}/config.json`
   - `~/.claude/teams/{team-name}/inboxes/`

2. **Create Tasks**:

   For each task in JSON.tasks array:
   ```
   TaskCreate(
     team_name: {JSON.team_name},
     subject: {task.subject},
     description: {task.description},
     owner: {task.owner}
   )
   ```

3. **Spawn Team Members**:

   For each member in JSON.members array:
   ```
   Task(
     subagent_type: "general-purpose",
     team_name: {JSON.team_name},
     name: {member.name},
     model: {member.model},
     prompt: {member.prompt},
     run_in_background: true
   )
   ```

   **Color Assignment**: Colors are automatically assigned by the Task tool from the pool (blue, green, yellow, magenta, cyan, red)

4. **Verify Creation**:

   After all spawning completes:
   - Read `~/.claude/teams/{team-name}/config.json`
   - Verify all members are registered
   - Check that all members have `isActive: true`

**Output**: Fully spawned and operational team

---

## Phase 4: Display Team Dashboard

**Goal**: Show user the created team status.

**Actions**:

1. **Generate Team Summary**:

   Display in this format:
   ```markdown
   ## Team Created: {team_name}

   **Description**: {description}
   **Pattern**: {pattern selected by team-architect}
   **Members**: {count}
   **Tasks**: {count}

   ### Members

   | Name | Role | Model | Status | Color |
   |------|------|-------|--------|-------|
   | member-1 | implementer | sonnet | Active | 🔵 blue |
   | member-2 | tester | haiku | Active | 🟢 green |

   ### Tasks

   | # | Task | Owner | Status |
   |---|------|-------|--------|
   | 1 | Implement feature X | member-1 | Pending |
   | 2 | Test feature X | member-2 | Pending |

   ### Next Steps

   1. Monitor team progress: `/team:status {team_name}`
   2. Send work to members: Use SendMessage tool
   3. View task list: Use TaskList tool
   4. Check team config: `~/.claude/teams/{team_name}/config.json`

   ### File Locations

   - Config: `~/.claude/teams/{team_name}/config.json`
   - Inboxes: `~/.claude/teams/{team_name}/inboxes/`
   - Tasks: Check with TaskList tool
   ```

2. **Success Confirmation**:

   Confirm that team is ready:
   ```
   ✅ Team successfully created and all members spawned
   ✅ Task queue initialized
   ✅ Team is ready to receive work
   ```

**Output**: Complete team creation summary and next steps guide

---

## Error Handling

### Team-Architect Agent Failure

If team-architect agent fails or returns invalid JSON:
1. Show error message with details
2. Ask user if they want to retry or provide manual configuration
3. If retry → Return to Phase 2
4. If manual → Ask for team_name, member count, and create simple template

### TeamCreate Failure

If TeamCreate fails (e.g., team name already exists):
1. Show error: "Team '{name}' already exists"
2. Use AskUserQuestion:
   - header: "Team exists"
   - question: "What would you like to do?"
   - options:
     - Delete existing and create new
     - Choose different name
     - Cancel
3. Handle choice accordingly

### Task Spawn Failure

If any Task spawn fails:
1. Note which member failed to spawn
2. Continue spawning remaining members
3. Show warning in final dashboard
4. Provide command to retry failed spawns

---

## Best Practices

**DO**:
- Let team-architect agent design the optimal team configuration
- Parse JSON carefully with error handling
- Verify all spawns completed successfully
- Show clear dashboard with next steps

**DON'T**:
- Don't hardcode team configurations - let team-architect decide
- Don't skip user approval (team-architect handles this)
- Don't proceed if JSON parsing fails
- Don't ignore spawn failures

---

## Example Usage

**Simple Usage**:
```
/team:create "Build authentication API with React frontend"
```

**Interactive Usage**:
```
/team:create
→ Prompts for purpose and size
→ Delegates to team-architect
→ Creates team with designed configuration
```

**With Context**:
```
/team:create "Refactor database layer - PostgreSQL to Prisma migration, need backend specialist and tester"
```
