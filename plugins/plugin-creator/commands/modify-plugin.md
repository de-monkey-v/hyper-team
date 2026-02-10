---
description: Interactive plugin/component modification with conversational design and automated implementation
argument-hint: [plugin-name, plugin-path, or "project"]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, AskUserQuestion, Skill, Task, TaskCreate, TaskUpdate, TaskList
---

# Interactive Plugin/Component Modification

Interactively analyze and modify existing plugins or project components.

**Supported Modes:**
- **Project Mode**: Modify components in the current project's `.claude/` folder
- **Plugin Mode**: Modify existing plugins

**Core Philosophy:**
- Iteratively collect and refine requirements like a Plan agent
- Only ask about direction; AI decides the details
- Validate against specifications via claude-code-guide, then request final approval
- Recommend Skill → Agent → Command structure and suggest refactoring

**Target:** $ARGUMENTS

---

## Workflow Overview

```
Phase 0: Initialization (includes mode detection)
     ↓
Phase 1: Interactive Modification Plan Design
     └── plan-designer: analyze current structure → iterative conversation → write modification plan file
     ↓
Phase 1.5: Plan Review and Approval
     ├── claude-code-guide: specification validation
     ├── Display plan + request approval
     └── Needs changes → re-run Phase 1
     ↓
Phase 2: Execution (after approval)
     └── Creator agents: modify components based on plan
     ↓
Phase 3: Validation and Version Update
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

### Step 2: Mode Detection

**MUST EXECUTE: Analyze the arguments to determine the mode.**

**Mode detection logic:**

1. **Argument is "project" or ".claude"** → Project Mode
2. **Argument is a path and `.claude-plugin/plugin.json` exists** → Plugin Mode
3. **Argument is a name and search finds `.claude-plugin/`** → Plugin Mode
4. **Current project has `.claude/` and no argument** → Suggest Project Mode
5. **None of the above** → Ask the user

```
AskUserQuestion: (if needed)
- question: "What would you like to modify?"
- header: "Target"
- options:
  - label: "Project components (Recommended)"
    description: "Components in the current project's .claude/ folder"
  - label: "Plugin"
    description: "Independent plugin package"
```

**Mode-specific characteristics:**

| Item | Project Mode | Plugin Mode |
|------|-------------|-------------|
| Target path | `./.claude/` | `plugins/{name}/` or specified path |
| Manifest | None | `.claude-plugin/plugin.json` |
| Version management | None | Update plugin.json version |

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
   description: "Analysis and interactive modification plan with plan-designer"
   activeForm: "Designing modification plan"

2. subject: "Phase 2: Component Modification"
   description: "Modify components with Creator agents"
   activeForm: "Modifying components"

3. subject: "Phase 3: Validation"
   description: "Format validation with claude-code-guide (+ plugin-validator for Plugin Mode)"
   activeForm: "Validating"

4. subject: "Phase 4: Completion"
   description: "Finalization"
   activeForm: "Finalizing"
```

---

## Phase 1: Plan Design

### Step 1: Confirm Target Location (branch by mode)

**Project Mode:**
- Path: `./.claude/`
- Check: `ls -la .claude/` (check for skills, agents, commands, hooks.json)

**Plugin Mode:**
Find plugin from $ARGUMENTS:

1. If path is given → use directly
2. If only name is given → search in order:
   - `./plugins/{name}/`
   - `./.claude-plugin/` (current project)
   - `~/.claude/plugins/{name}/`

```bash
ls -la {path}/.claude-plugin/plugin.json
```

**If not found:**
```
Target not found.

Please verify:
- Project Mode: .claude/ directory exists
- Plugin Mode: full path to plugin directory or plugin name in standard locations
```

### Step 2: Call plan-designer

**MUST EXECUTE: Delegate analysis and modification plan design to the plan-designer agent.**

```
Task tool:
- subagent_type: "plugin-creator:plan-designer"
- description: "Interactive modification plan design"
- prompt: |
    <current-state>
    <mode>{project or plugin}</mode>
    <path>{Project Mode: ./.claude/ | Plugin Mode: confirmed plugin path}</path>
    <user-request>$ARGUMENTS</user-request>
    <type>modify</type>
    </current-state>

    <instructions>
    Design the modification plan interactively:

    0. **Analysis Phase**: Analyze current structure
       - Project Mode: scan components in .claude/
       - Plugin Mode: read plugin.json + scan components
       - Identify current architecture patterns
       - Check Skill → Agent → Command pattern compliance

    1. **Discovery Phase**: Ask 2-3 questions via AskUserQuestion about modification direction
       - Modification type (add/modify/delete)
       - Components to modify
       - Key changes

    2. **Design Phase**: Automatically design modification plan
       - Decide which components to change
       - Design new components to add
       - Check dependencies for components to delete
       - **Suggest refactoring if recommended patterns aren't followed**

    3. **Review Phase**: Show modification plan to user, let them choose to modify or approve
       - Compare current structure vs post-modification structure
       - If "Needs changes" selected, redesign that section and repeat Review

    4. **Finalize Phase**: After approval, write the plan file
       - Write to `~/.claude/plans/{mode}-modify-{name}-{timestamp}.md` using Write tool
       - Return the plan file path

    **Mode-specific path rules:**
    - Project Mode: `.claude/skills/`, `.claude/agents/`, `.claude/commands/`, `.claude/hooks.json`
    - Project Mode (namespaced commands): `.claude/commands/{namespace}/{name}.md` → `/{namespace}:{name}` invocation
    - Plugin Mode: `{plugin}/skills/`, `{plugin}/agents/`, `{plugin}/commands/`, `{plugin}/hooks/hooks.json`

    **For Project Mode command modification/addition:**
    - If user wants a namespace, create as `.claude/commands/{namespace}/{name}.md`
    - Namespace examples: mct, dev, test → callable as `/mct:check`, `/dev:build`

    **Refactoring suggestion conditions:**
    - Skills exist but no agents → suggest adding agents
    - Commands perform all logic directly → suggest agent separation
    - Agents operate without skills → suggest skill extraction

    **CRITICAL**: Continue the conversation until the user explicitly says "save", "approve", or "looks good".
    **CRITICAL**: You MUST save the plan file using the Write tool and return the path.
    </instructions>
```

**Handling plan-designer results:**
- Extract the **plan file path** from the agent's returned result
- Path format: `~/.claude/plans/{mode}-modify-{name}-{timestamp}.md`

**On Phase 1 completion:** Update "Phase 1" task to `completed` via TaskUpdate

---

## Phase 1.5: Plan Review and Approval

**MUST EXECUTE: Validate specifications via claude-code-guide, then request final approval from the user.**

### Step 1: Validate Specifications via claude-code-guide

```
Task tool:
- subagent_type: "claude-code-guide"
- description: "Modification plan specification validation"
- prompt: |
    <task-context>
    <plan-file>{path returned by plan-designer}</plan-file>
    <validation-type>specification</validation-type>
    </task-context>

    <instructions>
    Verify that the modification plan conforms to Claude Code official specifications:
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
- question: "The following issues were found in the modification plan. How would you like to proceed?"
- header: "Issue Found"
- options:
  - label: "Fix and re-validate (Recommended)"
    description: "Re-invoke plan-designer to fix the issues"
  - label: "Ignore and proceed"
    description: "Proceed ignoring the issues (not recommended)"
  - label: "Cancel"
    description: "Cancel plugin modification"
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
- question: "Do you approve this plugin modification plan?"
- header: "Plan Approval"
- options:
  - label: "Approve - start modification (Recommended)"
    description: "Modify the plugin according to the plan"
  - label: "Needs changes"
    description: "Discuss the design again (back to Phase 1)"
  - label: "Cancel"
    description: "Cancel plugin modification"
```

### Step 5: Handle Approval Results

| Selection | Action |
|-----------|--------|
| **Approve** | Proceed to Phase 2 |
| **Needs changes** | Return to Phase 1 (Step 2) and re-invoke plan-designer |
| **Cancel** | Mark all tasks as `completed` and exit |

---

## Phase 2: Component Modification

**MUST EXECUTE: Execute the Delegation Instructions from the plan file.**

### Step 1: Extract Delegation Information from Plan File

Parse the `## Delegation Instructions` section of the plan file to extract task information

### Step 2: Execute Tasks Sequentially

```
1. Delete components (first, if needed)
- Check dependencies before deletion
- Bash tool: rm -rf {path}

2. Modify components (direct Edit or agent invocation)
- Partial modification: use Edit tool
- Full rewrite: invoke Creator agent

3. Add components (agent invocation)
Task tool:
- subagent_type: "plugin-creator:{component}-creator"
- description: "Create {component-name}"
- prompt: |
    <task-context>
    <plugin-path>{plugin-path}</plugin-path>
    <component-name>{component-name}</component-name>
    <mode>{project or plugin}</mode>
    </task-context>

    <specification>
    {component specification extracted from plan file}
    </specification>

    <instructions>
    Create the component file according to the specification above.
    Follow XML-structured prompt patterns for any generated content.
    </instructions>
```

**Task order:**
1. Delete (clean up dependencies)
2. Modify (existing files)
3. Add (new files)
4. Update references (README, etc.)

### Step 3: Update Plan File Status

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

**MUST EXECUTE: Verify that modified components conform to official specifications.**

```
Task tool:
- subagent_type: "claude-code-guide"
- description: "Validate modified component formats"
- prompt: |
    <task-context>
    <mode>{project or plugin}</mode>
    <path>{.claude/ or plugin-path}</path>
    <modified-components>{modified-components}</modified-components>
    <validation-type>format</validation-type>
    </task-context>

    <instructions>
    Validate modified components against official documentation:
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
    <plugin-path>{plugin path}</plugin-path>
    <validation-scope>modified-components</validation-scope>
    </task-context>

    <instructions>
    Validate the following:
    - Modified component formats
    - Impact on existing components (regression)
    - Reference integrity
    - Skill → Agent → Command pattern compliance

    Provide detailed report with fix suggestions for each issue.
    </instructions>
```

### Step 3: Handle Results

| Result | Action |
|--------|--------|
| PASS | Proceed to Step 4 |
| WARN | Display warnings, proceed to Step 4 |
| FAIL | Fix issues, re-validate |

### Step 4: Version Update (Plugin Mode only)

**Plugin Mode only. Project Mode proceeds to Phase 4.**

Recommended version bump by modification type:

| Modification Type | Version Bump | Example |
|-------------------|-------------|---------|
| Metadata only | patch | 1.0.0 → 1.0.1 |
| Existing modification | patch | 1.0.0 → 1.0.1 |
| Component addition | minor | 1.0.0 → 1.1.0 |
| Component deletion | minor | 1.0.0 → 1.1.0 |
| Breaking Change | major | 1.0.0 → 2.0.0 |

```
AskUserQuestion:
- question: "Update version from {current} → {suggested}?"
- header: "Version"
- options:
  - label: "Yes, use recommended version"
    description: "Update to {suggested}"
  - label: "Specify different version"
    description: "Enter your preferred version"
  - label: "Keep current version"
    description: "Keep current version"
```

**On Phase 3 completion:** Update "Phase 3" task to `completed` via TaskUpdate

---

## Phase 4: Completion

### Step 1: Completion Summary

**Project Mode:**
```markdown
## Modification Summary

**Location**: .claude/
**Mode**: Project Components

### Changes
- Added: {added components}
- Modified: {modified components}
- Deleted: {removed components}

### Architecture Changes
- Before: {previous pattern}
- After: {new pattern}

### File Changes
- Files added: {count}
- Files modified: {count}
- Files deleted: {count}

### Testing
Restart Claude Code to automatically load the components.
\`\`\`bash
claude
# or verify with /agents, /skills commands
\`\`\`

### Next Steps
1. Test modified components
2. Run /plugin-creator:modify-plugin project again for additional modifications
```

**Plugin Mode:**
```markdown
## Modification Summary

**Plugin**: {name}
**Previous version**: {old_version}
**New version**: {new_version}

### Changes
- Added: {added components}
- Modified: {modified components}
- Deleted: {removed components}

### Architecture Changes
- Before: {previous pattern}
- After: {new pattern}

### File Changes
- Files added: {count}
- Files modified: {count}
- Files deleted: {count}

### Testing
\`\`\`bash
claude --plugin-dir {path}
\`\`\`

### Next Steps
1. Test modified components
2. Update README (if needed)
3. Run /plugin-creator:modify-plugin again for additional modifications
```

### Step 2: Check for Additional Work

```
AskUserQuestion:
- question: "Is there anything else you'd like to modify?"
- header: "Additional Work"
- options:
  - label: "Done"
    description: "Finalize modifications"
  - label: "Additional modifications"
    description: "Modify other components as well"
  - label: "Update README"
    description: "Reflect changes in README"
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

### What plan-designer Does (Modification Mode)
- Analyzes current structure
- Iteratively identifies modification direction through conversation
- Automatically designs modification plan
- Suggests refactoring (when recommended patterns aren't followed)
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

**Refactoring suggestion conditions:**
- Skills exist but no agents → add agents
- Commands perform all logic → separate into agents
- Agents operate without skills → extract skills

---

## Component Dependencies

| Component | Potential Dependencies |
|-----------|----------------------|
| Skills | Referenced by commands, preloaded by agents |
| Agents | Called via Task tool from commands |
| Hooks | Depend on script files |
| Commands | May reference agents/skills |

**Before deletion:**
- Check references in other components via Grep
- Request deletion confirmation from user

---

## Error Handling

### Target Not Found

**Project Mode:**
```
".claude/" directory not found.

Options:
- Create new with /plugin-creator:create-plugin?
- Specify a different path?
```

**Plugin Mode:**
```
Plugin "{name}" not found.

Please verify:
- Full path to plugin directory
- Or plugin name in standard locations
```

### Invalid Structure

**Project Mode:**
```
No modifiable components in ".claude/".

At least one of skills/, agents/, commands/ is needed.

Options:
- Create new with /plugin-creator:create-plugin project?
```

**Plugin Mode:**
```
No valid plugin at "{path}".

Required file:
- .claude-plugin/plugin.json

Options:
- Initialize as new plugin?
- Specify a different path?
```

### Agent Call Failure
```
{agent-name} agent call failed.

Options:
- Retry
- Discuss alternative approach
```

### Specification Validation Failure
Pass issues found by claude-code-guide to plan-designer for correction

---

**Begin with Phase 0: Initialization**
