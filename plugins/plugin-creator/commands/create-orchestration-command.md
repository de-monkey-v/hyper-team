---
description: Guide creation of orchestration commands that coordinate skills and agents
argument-hint: [workflow-description]
allowed-tools: Read, Write, Grep, Glob, Bash, AskUserQuestion, Skill, Task
---

# Orchestration Command Creator

Create orchestration commands that coordinate multiple skills and agents.

## Core Principles

- **Phase-based structure**: Separate complex workflows into manageable stages
- **Skill loading**: Inject knowledge at the appropriate time
- **Agent delegation**: Delegate autonomous work to agents
- **User checkpoints**: Confirm at major decision points

**Initial request:** $ARGUMENTS

---

## Phase 1: Workflow Design

**Goal**: Understand workflow purpose and design structure

**Actions**:

### 1.1 Understand the Workflow

Analyze from $ARGUMENTS:
- What is the main goal?
- What steps are needed?
- What inputs/outputs are expected?

**If unclear, use AskUserQuestion:**
- header: "Workflow Purpose"
- question: "What task should this command automate?"
- options:
  - Creation workflow (file, component generation)
  - Validation workflow (inspection, audit)
  - Deployment workflow (build, test, deploy)
  - Analysis workflow (investigation, reporting)

### 1.2 Identify Components

Load `plugin-creator:command-development` skill.

Identify needed components:

| Component | Purpose | Example |
|-----------|---------|---------|
| **Skills** | Knowledge/guide reference | skill-development, agent-development |
| **Agents** | Autonomous task delegation | skill-creator, plugin-validator |
| **User Questions** | Decisions/confirmations | AskUserQuestion |
| **Tools** | Direct execution | Bash, Read, Write |

### 1.3 Design Phase Structure

Recommended number of phases: **3-5** (maximum)

Define each phase:
- Phase name (clear and action-oriented)
- Goal (one sentence)
- Required skills/agents
- Whether user checkpoint is needed

**Present structure to user and confirm.**

**Output**: Finalized phase structure

---

## Phase 2: Command Implementation

**Goal**: Write the command file

**Actions**:

### 2.1 Write Frontmatter

```yaml
---
description: [under 60 chars, start with verb]
argument-hint: [argument format]
allowed-tools: [required tools list]
---
```

**Tool selection guide:**
- Need skill loading → `Skill`
- Need agent invocation → `Task`
- Need file reading → `Read, Grep, Glob`
- Need file writing → `Write, Edit`
- Need shell commands → `Bash`
- Need user questions → `AskUserQuestion`

### 2.2 Write Command Body

**Header section:**
```markdown
# [Command Title]

[Brief description]

## Core Principles
- [Principle 1]
- [Principle 2]

**Initial request:** $ARGUMENTS

---
```

**Write each phase:**
```markdown
## Phase N: [Name]

**Goal**: [One-sentence goal]

**Actions**:

### N.1 [Sub-action]

[If skill loading needed]
Load `{plugin}:{skill}` skill.

[If agent delegation needed]
**Delegate to {agent-name} agent**:
- Information to pass: [what to provide]
- Expected output: [expected output]

[If user confirmation needed]
AskUserQuestion:
- header: "[label]"
- question: "[question]"
- options: [choices]

**Output**: [Phase deliverable]

---
```

**XML Data Transfer Pattern (for agent delegation):**
```markdown
## Phase N: [Agent Delegation Phase]

**Goal**: [One-sentence goal]

**Actions**:

### N.1 Delegate to {agent-name}

Task tool:
- subagent_type: "{agent-name}"
- description: "{brief description}"
- prompt: |
    <task-context>
    <target>{target from $ARGUMENTS}</target>
    <context>{relevant context}</context>
    </task-context>

    <specification>
    {detailed specification for the agent}
    </specification>

    <instructions>
    Execute the task according to the specification above.
    </instructions>

**Output**: [Expected output from agent]
```

### 2.3 Important Notes Section

```markdown
## Important Notes

### Agent Usage Guide
| Agent | Role | When to Use |
|-------|------|-------------|
| ... | ... | ... |

### User Checkpoints
1. After Phase N: [What to confirm]
2. After Phase M: [What to confirm]
```

**Output**: Completed command content

---

## Phase 3: File Creation & Validation

**Goal**: Create and validate the file

**Actions**:

### 3.1 Determine Location

AskUserQuestion:
- header: "Location"
- question: "Where should the command be created?"
- options:
  - Plugin commands/ (part of a plugin)
  - Project .claude/commands/ (project-specific)
  - User ~/.claude/commands/ (personal command)

### 3.2 Create File

**Create file with Write tool:**
- Combine frontmatter + body
- `{path}/{command-name}.md`

**Verify with Read tool.**

### 3.3 Validate Structure

Validation checklist:
- [ ] frontmatter has description
- [ ] All phase numbers and names present
- [ ] Skill load patterns are clear
- [ ] Agent delegation patterns are clear
- [ ] User checkpoints defined
- [ ] allowed-tools matches actual usage

### 3.4 Completion Summary

```markdown
## Command Created: /{command-name}

**File**: {path}
**Phases**: {count}
**Skills used**: {list}
**Agents used**: {list}
**User checkpoints**: {count}

### Usage Example
/{command-name} [example arguments]

### Testing Guide
1. Test basic flow
2. Test each phase individually
3. Test error cases
```

**Output**: Completed orchestration command

---

## Quick Reference: Orchestration Patterns

### Skill Load Pattern
```markdown
Load `plugin-creator:skill-development` skill.
```

### Agent Delegation Pattern
```markdown
**Delegate to skill-creator agent**:
Task tool:
- subagent_type: "plugin-creator:skill-creator"
- description: "Create skill with XML-structured data transfer"
- prompt: |
    <task-context>
    <plugin-path>{path}</plugin-path>
    <skill-name>{name}</skill-name>
    </task-context>

    <specification>
    Purpose: {purpose}
    Triggers: {triggers}
    </specification>

    <instructions>
    Create the skill files according to the specification above.
    Expected output: skills/{name}/SKILL.md
    </instructions>
```

### User Confirmation Pattern
```markdown
AskUserQuestion:
- header: "Confirm"
- question: "Proceed with this structure?"
- options:
  - Yes, proceed (Recommended)
  - Needs changes
  - Cancel
```

---

## Example: Simple Orchestration Command

```markdown
---
description: Review and improve code quality
argument-hint: [file-or-directory]
allowed-tools: Read, Grep, Glob, Skill, Task
---

# Code Quality Workflow

Analyze code and suggest quality improvements.

**Target:** $ARGUMENTS

---

## Phase 1: Analysis

**Goal**: Understand code structure

Load `code-analysis` skill.

**Actions**:
1. Scan target files
2. Identify code patterns
3. Write analysis summary

**Output**: Code structure summary

---

## Phase 2: Review

**Goal**: Identify quality issues

**Delegate to code-reviewer agent**:
- Information to pass: analysis results, target files
- Expected output: issue list

**Output**: Quality issue list

---

## Phase 3: Report

**Goal**: Present results

**Actions**:
1. Organize issues by severity
2. Write improvement suggestions
3. Report to user

**Output**: Quality report
```

---

**Begin with Phase 1: Workflow Design**
