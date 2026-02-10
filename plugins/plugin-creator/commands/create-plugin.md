---
description: Interactive plugin/component creation with conversational design and automated implementation
argument-hint: [description]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, AskUserQuestion, Skill, Task, TaskCreate, TaskUpdate, TaskList
---

# Interactive Plugin/Component Creation

Interactively design and automatically create plugins or project components.

**Supported Modes:**
- **Project Mode**: Add commands/agents/skills to the current project's `.claude/` folder
- **Plugin Mode**: Create independent plugins deployable to the marketplace

**Core Philosophy:**
- Iteratively collect and refine requirements like a Plan agent
- Only ask about direction; AI decides the details
- Validate against specifications via claude-code-guide, then request final approval
- Recommend Skill → Agent → Command structure

**Initial request:** $ARGUMENTS

---

## Workflow Overview

```
Phase 0: Initialization (includes mode selection)
     ↓
Phase 1: Interactive Plan Design
     └── plan-designer: iterative conversation → write plan file
     ↓
Phase 1.5: Plan Review and Approval
     ├── claude-code-guide: specification validation
     ├── Display plan + request approval
     └── Needs changes → re-run Phase 1
     ↓
Phase 2: Execution (after approval)
     └── Creator agents: create components based on plan
     ↓
Phase 3: Validation
     ├── claude-code-guide: format validation
     └── plugin-validator: structure validation (Plugin Mode only)
     ↓
Phase 4: Completion
```

---

## Phase 0: Initialization

### Step 1: Load Skill

```
Skill tool:
- skill: "plugin-creator:plugin-development"
```

### Step 2: Mode Selection

**MUST EXECUTE: Determine the creation mode first.**

```
AskUserQuestion:
- question: "What would you like to create?"
- header: "Type"
- options:
  - label: "Project components (Recommended)"
    description: "Add commands/agents/skills to the current project's .claude/ folder"
  - label: "Plugin"
    description: "Independent plugin deployable to the marketplace"
```

**Mode-specific characteristics:**

| Item | Project Mode | Plugin Mode |
|------|-------------|-------------|
| Location | `./.claude/` | `plugins/{name}/` or specified path |
| Manifest | None | `.claude-plugin/plugin.json` |
| Skills path | `.claude/skills/{name}/SKILL.md` | `{plugin}/skills/{name}/SKILL.md` |
| Agents path | `.claude/agents/{name}.md` | `{plugin}/agents/{name}.md` |
| Commands path | `.claude/commands/{name}.md` or `.claude/commands/{namespace}/{name}.md` | `{plugin}/commands/{name}.md` |
| Hooks path | `.claude/hooks.json` | `{plugin}/hooks/hooks.json` |

**Project Mode Commands Namespace:**
- With namespace: `.claude/commands/{namespace}/{name}.md` → `/{namespace}:{name}` invocation
- Without namespace: `.claude/commands/{name}.md` → `/{name}` invocation
- Example: `.claude/commands/mct/check.md` → `/mct:check`

### Step 3: Clean Up Tasks

```
TaskList tool → check existing tasks
TaskUpdate tool → delete existing tasks (status: "deleted")
```

### Step 4: Register Tasks

```
TaskCreate (4 times):

1. subject: "Phase 1: Plan Design"
   description: "Interactive plan design with plan-designer"
   activeForm: "Designing plan"

2. subject: "Phase 2: Component Creation"
   description: "Create components with Creator agents"
   activeForm: "Creating components"

3. subject: "Phase 3: Validation"
   description: "Format validation with claude-code-guide (+ plugin-validator for Plugin Mode)"
   activeForm: "Validating"

4. subject: "Phase 4: Completion"
   description: "Finalization"
   activeForm: "Finalizing"
```

---

## Phase 1: Plan Design

**MUST EXECUTE: Delegate interactive plan design to the plan-designer agent.**

### Step 1: Confirm Creation Location (branch by mode)

**Project Mode:**
- Skip location question
- Automatically use `./.claude/`

**Plugin Mode:**
```
AskUserQuestion:
- question: "Where would you like to create the plugin?"
- header: "Location"
- options:
  - label: "plugins/ directory (Recommended)"
    description: "./plugins/{plugin-name}/"
  - label: "Current directory"
    description: "./{plugin-name}/"
  - label: "Custom path"
    description: "Enter your desired path"
```

### Step 2: Call plan-designer

```
Task tool:
- subagent_type: "plugin-creator:plan-designer"
- description: "Interactive plan design"
- prompt: |
    <task-context>
    <request>$ARGUMENTS</request>
    <mode>{project or plugin}</mode>
    <location>{Project Mode: ./.claude/ | Plugin Mode: selected path above}</location>
    <type>create</type>
    </task-context>

    <instructions>
    Design the plan interactively like a Plan agent:

    1. **Discovery Phase**: Ask only relevant questions via AskUserQuestion
       - Component purpose/domain
       - Interaction method (user-invoked vs automatic)
       - Key features (2-4)

    2. **Design Phase**: Automatically design the optimal structure
       - Recommend applying Skill → Agent → Command pattern

    3. **Review Phase**: Show design to user, let them choose to modify or approve
       - If "Needs changes" selected, redesign that section and repeat Review

    4. **Finalize Phase**: After approval, write the plan file
       - Write to `~/.claude/plans/{mode}-create-{name}-{timestamp}.md` using Write tool
       - Return the plan file path

    **Mode-specific path rules:**
    - Project Mode: `.claude/skills/`, `.claude/agents/`, `.claude/commands/`, `.claude/hooks.json`
    - Project Mode (namespaced commands): `.claude/commands/{namespace}/{name}.md` → `/{namespace}:{name}` invocation
    - Plugin Mode: `{plugin}/skills/`, `{plugin}/agents/`, `{plugin}/commands/`, `{plugin}/hooks/hooks.json`

    **For Project Mode command creation:**
    - If user wants a namespace, create as `.claude/commands/{namespace}/{name}.md`
    - Namespace examples: mct, dev, test → callable as `/mct:check`, `/dev:build`

    **CRITICAL**: Continue the conversation until the user explicitly says "save", "approve", or "looks good".
    **CRITICAL**: You MUST save the plan file using the Write tool and return the path.
    </instructions>
```

**Handling plan-designer results:**
- Extract the **plan file path** from the agent's returned result
- Path format: `~/.claude/plans/{mode}-create-{name}-{timestamp}.md`

**On Phase 1 completion:** Update "Phase 1" task to `completed` via TaskUpdate

---

## Phase 1.5: Plan Review and Approval

**MUST EXECUTE: Validate specifications via claude-code-guide, then request final approval from the user.**

### Step 1: Validate Specifications via claude-code-guide

```
Task tool:
- subagent_type: "claude-code-guide"
- description: "Plan specification validation"
- prompt: |
    <task-context>
    <plan-file>{path returned by plan-designer}</plan-file>
    <validation-type>specification</validation-type>
    </task-context>

    <instructions>
    Verify that the plan conforms to Claude Code official specifications:
    - Skill frontmatter format (description required)
    - Agent frontmatter required fields (name, description)
    - Agent does not include Task tool (agents cannot call other agents)
    - Command frontmatter format (description, allowed-tools)
    - Hook event type validity (PreToolUse, PostToolUse, SessionStart, Stop, etc.)

    If there are issues, provide the corrections needed.
    If no issues, respond with "Specification validation passed".
    </instructions>
```

### Step 2: Handle Validation Results

**If validation finds issues:**

```
AskUserQuestion:
- question: "The following issues were found in the plan. How would you like to proceed?"
- header: "Issue Found"
- options:
  - label: "Fix and re-validate (Recommended)"
    description: "Re-invoke plan-designer to fix the issues"
  - label: "Ignore and proceed"
    description: "Proceed ignoring the issues (not recommended)"
  - label: "Cancel"
    description: "Cancel plugin creation"
```

- **Fix and re-validate:** Return to Phase 1 (Step 2) and re-invoke plan-designer
- **Ignore and proceed:** Continue to Step 3
- **Cancel:** Mark all tasks as `completed` and exit

### Step 3: Read and Display Plan File

```
Read tool:
- file_path: {plan file path}
```

Display the plan file contents to the user.

### Step 4: Request Final Approval

```
AskUserQuestion:
- question: "Do you approve this plugin creation plan?"
- header: "Plan Approval"
- options:
  - label: "Approve - start creation (Recommended)"
    description: "Create the plugin according to the plan"
  - label: "Needs changes"
    description: "Discuss the design again (back to Phase 1)"
  - label: "Cancel"
    description: "Cancel plugin creation"
```

### Step 5: Handle Approval Results

| Selection | Action |
|-----------|--------|
| **Approve** | Proceed to Phase 2 |
| **Needs changes** | Return to Phase 1 (Step 2) and re-invoke plan-designer |
| **Cancel** | Mark all tasks as `completed` and exit |

---

## Phase 2: Component Creation

**MUST EXECUTE: Execute the Delegation Instructions from the plan file.**

### Step 1: Extract Delegation Information from Plan File

Parse the `## Delegation Instructions` section of the plan file to extract component information

### Step 2: Create Base Structure (branch by mode)

**Project Mode:**
```
1. Create directories
Bash: mkdir -p .claude/skills .claude/agents .claude/commands

(No plugin.json creation)
```

**Plugin Mode:**
```
1. Create directories
Bash: mkdir -p {plugin-path}/.claude-plugin

2. Create plugin.json
Write tool:
- file_path: {plugin-path}/.claude-plugin/plugin.json
- content: {based on plan file metadata}
```

### Step 3: Call Creator Agents Sequentially

```
1. Create Skills (first)
Task tool:
- subagent_type: "plugin-creator:skill-creator"
- description: "Create {skill-name} skill"
- prompt: |
    <task-context>
    <plugin-path>{plugin-path}</plugin-path>
    <component-name>{skill-name}</component-name>
    <mode>{project or plugin}</mode>
    </task-context>

    <specification>
    {skill specification extracted from plan file}
    </specification>

    <instructions>
    Create the skill files according to the specification above.
    Follow XML-structured prompt patterns for any generated content.
    </instructions>

2. Create Agents (after skills)
Task tool:
- subagent_type: "plugin-creator:agent-creator"
- description: "Create {agent-name} agent"
- prompt: |
    <task-context>
    <plugin-path>{plugin-path}</plugin-path>
    <component-name>{agent-name}</component-name>
    <mode>{project or plugin}</mode>
    </task-context>

    <specification>
    {agent specification extracted from plan file}
    </specification>

    <instructions>
    Create the agent file according to the specification above.
    Follow XML-structured prompt patterns for any generated content.
    </instructions>

3. Create Commands (after agents)
Task tool:
- subagent_type: "plugin-creator:command-creator"
- description: "Create {command-name} command"
- prompt: |
    <task-context>
    <plugin-path>{plugin-path}</plugin-path>
    <component-name>{command-name}</component-name>
    <mode>{project or plugin}</mode>
    </task-context>

    <specification>
    {command specification extracted from plan file}
    </specification>

    <instructions>
    Create the command file according to the specification above.
    Follow XML-structured prompt patterns for any generated content.
    </instructions>

4. Create Hooks (if needed)
Task tool:
- subagent_type: "plugin-creator:hook-creator"
- description: "Create hooks"
- prompt: |
    <task-context>
    <plugin-path>{plugin-path}</plugin-path>
    <mode>{project or plugin}</mode>
    </task-context>

    <specification>
    {hook specification extracted from plan file}
    </specification>

    <instructions>
    Create the hooks.json file according to the specification above.
    </instructions>
```

**Parallel invocation possible:**
- Same type components (multiple skills, multiple agents) can be invoked in parallel
- Different types should be sequential (Skills → Agents → Commands → Hooks)

### Step 4: Update Plan File Status

After delegation completion, update the plan file Status:

```
Edit tool:
- file_path: {plan file path}
- old_string: "**Status**: PENDING_APPROVAL"
- new_string: "**Status**: COMPLETED"
```

**On Phase 2 completion:** Update "Phase 2" task to `completed` via TaskUpdate

---

## Phase 3: Validation

### Step 1: Format Validation via claude-code-guide

**MUST EXECUTE: Verify that created components conform to official specifications.**

```
Task tool:
- subagent_type: "claude-code-guide"
- description: "Validate created component formats"
- prompt: |
    <task-context>
    <mode>{project or plugin}</mode>
    <path>{.claude/ or plugin-path}</path>
    <validation-type>format</validation-type>
    </task-context>

    <instructions>
    Validate created components against official documentation:
    - Is skill SKILL.md frontmatter format correct?
    - Do agent .md frontmatter have required fields? (name, description)
    - Is command .md frontmatter format correct? (description, allowed-tools)
    - Are hooks.json event types valid?
    - Do agents NOT contain Task tool?

    If there are issues, provide fix instructions.
    </instructions>
```

### Step 2: Structure Validation via plugin-validator (Plugin Mode only)

**Plugin Mode only. Project Mode skips to Step 3.**

```
Task tool:
- subagent_type: "plugin-creator:plugin-validator"
- description: "Plugin validation"
- prompt: |
    <task-context>
    <plugin-path>{created plugin path}</plugin-path>
    <validation-scope>full</validation-scope>
    </task-context>

    <instructions>
    Validate the following:
    - Manifest validity (plugin.json)
    - Directory structure
    - Each component format
    - Naming conventions
    - Skill → Agent → Command pattern compliance

    Provide detailed report with fix suggestions for each issue.
    </instructions>
```

### Step 3: Handle Results

| Result | Action |
|--------|--------|
| PASS | Proceed to Phase 4 |
| WARN | Display warnings, proceed to Phase 4 |
| FAIL | Fix issues, re-validate |

**On Phase 3 completion:** Update "Phase 3" task to `completed` via TaskUpdate

---

## Phase 4: Completion

### Step 1: Marketplace Registration (Plugin Mode only)

**Plugin Mode only. Project Mode skips to Step 2.**

```
AskUserQuestion:
- question: "Would you like to register the plugin in the marketplace?"
- header: "Marketplace"
- options:
  - label: "Yes, register (Recommended)"
    description: "Register the plugin in marketplace.json"
  - label: "Later"
    description: "Skip registration for now"
```

### Step 2: Completion Summary

**Project Mode:**
```markdown
## Components Created

**Location**: .claude/
**Mode**: Project Components

### Created Components
- Skills: {count} ({names}) → .claude/skills/
- Agents: {count} ({names}) → .claude/agents/
- Commands: {count} ({names}) → .claude/commands/
- Hooks: {present/absent} → .claude/hooks.json

### Architecture Pattern
{Description of patterns used}

### Testing
Restart Claude Code to automatically load the components.
\`\`\`bash
claude
# or verify with /agents, /skills commands
\`\`\`

### Next Steps
1. Test each component
2. Modify if needed (use /plugin-creator:modify-plugin)
```

**Plugin Mode:**
```markdown
## Plugin Created: {name}

**Location**: {path}
**Version**: 0.1.0
**Marketplace**: {registered/not registered}

### Created Components
- Skills: {count} ({names})
- Agents: {count} ({names})
- Commands: {count} ({names})
- Hooks: {present/absent}

### Architecture Pattern
{Description of patterns used}

### Testing
\`\`\`bash
claude --plugin-dir {path}
\`\`\`

### Next Steps
1. Test each component
2. Modify if needed (use /plugin-creator:modify-plugin)
3. Flesh out README
```

**On Phase 4 completion:** Update "Phase 4" task to `completed` via TaskUpdate

---

## Quick Reference

### Mode-specific Path Rules

| Component | Project Mode | Plugin Mode |
|-----------|-------------|-------------|
| Skills | `.claude/skills/{name}/SKILL.md` | `{plugin}/skills/{name}/SKILL.md` |
| Agents | `.claude/agents/{name}.md` | `{plugin}/agents/{name}.md` |
| Commands | `.claude/commands/{name}.md` or `.claude/commands/{ns}/{name}.md` | `{plugin}/commands/{name}.md` |
| Hooks | `.claude/hooks.json` | `{plugin}/hooks/hooks.json` |
| Manifest | None | `{plugin}/.claude-plugin/plugin.json` |

**Project Mode Namespaced Commands:**
- `.claude/commands/{namespace}/{name}.md` → `/{namespace}:{name}` invocation
- Example: `.claude/commands/mct/check.md` → `/mct:check`

### What plan-designer Does
- Iteratively collects requirements through conversation
- Automatically designs structure
- Allows modifications until user approval
- Writes plan file after approval

### Recommended Architecture: Skill → Agent → Command

```
Skills (Knowledge/Guides)
    ↓ referenced by
Agents (Automated Execution)
    ↓ called by
Commands (User Entry Points)
```

**When to apply:**
- When knowledge + automation is needed
- Complex workflows
- NOT needed for simple utilities (Skills alone suffice)

---

## Error Handling

### Unclear request
plan-designer clarifies with strategic questions

### Agent call failure
```
{agent-name} agent call failed.

Options:
- Retry
- Discuss alternative approach
```

### Specification validation failure
Pass issues found by claude-code-guide to plan-designer for correction

---

**Begin with Phase 0: Initialization**
