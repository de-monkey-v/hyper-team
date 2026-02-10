---
name: command-development
description: Slash command creation guide. This skill should be used when the user asks to "create a command", "add a command", "커맨드 만들어줘", "슬래시 명령어 작성", or needs guidance on writing slash commands.
---

# Command Development for Claude Code

Slash commands are frequently-used prompts defined as Markdown files that Claude executes during interactive sessions.

## Critical Concept: Commands are FOR Claude

**Commands are written for agent consumption, not human consumption.**

When a user invokes `/command-name`, the command content becomes Claude's instructions.

**Correct (instructions for Claude):**
```markdown
Review this code for security vulnerabilities including:
- SQL injection
- XSS attacks
Provide specific line numbers and severity ratings.
```

**Incorrect (messages to user):**
```markdown
This command will review your code for security issues.
You'll receive a report with vulnerability details.
```

## Command Locations

| Location | Scope | Label |
|----------|-------|-------|
| `.claude/commands/` | Project-specific | (project) |
| `~/.claude/commands/` | All projects | (user) |
| `plugin-name/commands/` | When plugin installed | (plugin-name) |

## File Format

### Basic Command
```markdown
Review this code for security vulnerabilities including:
- SQL injection
- XSS attacks
- Authentication bypass
```

### With YAML Frontmatter
```markdown
---
description: Review code for security issues
allowed-tools: Read, Grep, Bash(git:*)
model: sonnet
---

Review this code for security vulnerabilities...
```

## YAML Frontmatter Fields

| Field | Purpose | Example |
|-------|---------|---------|
| description | Shown in `/help` | "Review PR for quality" |
| allowed-tools | Restrict tool access | `Read, Write, Bash(git:*)` |
| model | Specify model | `sonnet`, `opus`, `haiku` |
| argument-hint | Document args | `[pr-number] [priority]` |
| disable-model-invocation | Prevent programmatic calls | `true` |

## Dynamic Arguments

### Using $ARGUMENTS (all args)
```markdown
---
argument-hint: [issue-number]
---
Fix issue #$ARGUMENTS following our coding standards.
```

### Using Positional Args ($1, $2, $3)
```markdown
---
argument-hint: [pr-number] [priority] [assignee]
---
Review PR #$1 with priority $2. Assign to $3 for follow-up.
```

Usage: `/review-pr 123 high alice`

## File References

### Using @ Syntax
```markdown
---
argument-hint: [file-path]
---
Review @$1 for code quality and potential bugs.
```

### Multiple Files
```markdown
Compare @src/old.js with @src/new.js
Identify breaking changes and new features.
```

## Bash Execution

Commands can execute bash to gather context:

```markdown
---
allowed-tools: Read, Bash(git:*)
---
Files changed: !\`git diff --name-only\`

Review each file for code quality and test coverage.
```

**Note:** See `references/plugin-features-reference.md` for complete bash syntax.

## Command Organization

### Flat Structure
```
.claude/commands/
├── build.md
├── test.md
└── deploy.md
```

### Namespaced (15+ commands)
```
.claude/commands/
├── ci/
│   ├── build.md        # /build (project:ci)
│   └── test.md         # /test (project:ci)
└── git/
    └── commit.md       # /commit (project:git)
```

## Plugin-Specific Features

### ${CLAUDE_PLUGIN_ROOT}

Use for all plugin file references:

```markdown
---
allowed-tools: Bash(node:*)
---
Run analysis: !\`node ${CLAUDE_PLUGIN_ROOT}/scripts/analyze.js $1\`
```

**Common patterns:**
```text
# Execute script (use ! prefix for inline execution)
!\`bash ${CLAUDE_PLUGIN_ROOT}/scripts/script.sh\`

# Load configuration (use @ prefix for file inclusion)
@${CLAUDE_PLUGIN_ROOT}/config/settings.json

# Use template
@${CLAUDE_PLUGIN_ROOT}/templates/report.md
```

## Common Patterns

### Review Pattern
```markdown
---
description: Review code changes
allowed-tools: Read, Bash(git:*)
---
Files changed: !\`git diff --name-only\`

Review each file for:
1. Code quality and style
2. Potential bugs
3. Test coverage
```

### Workflow Pattern
```markdown
---
description: Complete PR workflow
argument-hint: [pr-number]
allowed-tools: Bash(gh:*), Read
---
PR #$1 Workflow:
1. Fetch PR: !\`gh pr view $1\`
2. Review changes
3. Run checks
4. Approve or request changes
```

### Documentation Pattern
```markdown
---
argument-hint: [source-file]
---
Generate documentation for @$1 including:
- Function descriptions
- Parameter documentation
- Usage examples
```

### Orchestration Pattern with XML Data Transfer
```markdown
---
description: Complete workflow with agents
argument-hint: [target]
allowed-tools: Read, Write, Bash, AskUserQuestion, Task, Skill
---

<task-context>
<target>$ARGUMENTS</target>
<mode>analysis</mode>
</task-context>

Phase 1: Load relevant skill for knowledge
Phase 2: Launch analyzer agent via Task tool with structured data:

Task tool prompt:
<task-context>
<target-path>{path}</target-path>
<analysis-type>{type}</analysis-type>
</task-context>

<instructions>
Analyze the target following the specification above.
</instructions>

Phase 3: Compile results
```

**Note:** `Task` enables agent launching, `Skill` enables skill loading.

## Best Practices

✅ **DO:**
- Write commands as instructions FOR Claude
- Use `argument-hint` to document expected args
- Specify `allowed-tools` when restricting access
- Use `${CLAUDE_PLUGIN_ROOT}` for plugin paths
- Use XML tags when passing data to agents via Task tool
- Place long context data at TOP, instructions at BOTTOM
- Keep commands focused on single tasks

❌ **DON'T:**
- Write commands as messages TO the user
- Omit argument documentation
- Use `Bash(*)` when narrower patterns work
- Hardcode absolute paths in plugins
- Create overly complex multi-purpose commands

## Troubleshooting

**Command not appearing:**
- Check file is in correct directory with `.md` extension
- Verify valid Markdown format
- Restart Claude Code

**Arguments not working:**
- Verify `$1`, `$2` syntax
- Check `argument-hint` matches usage

**Bash execution failing:**
- Check `allowed-tools` includes Bash
- Test command in terminal first

## Additional Resources

### Reference Files
- **`references/frontmatter-reference.md`** - Complete frontmatter field specs
- **`references/plugin-features-reference.md`** - Plugin-specific patterns
- **`references/validation-patterns.md`** - Input validation patterns
- **`references/component-integration.md`** - Agent/skill/hook integration
- **`references/interactive-commands.md`** - User interaction patterns

### Example Files
- **`examples/simple-commands.md`** - Basic command examples
- **`examples/plugin-commands.md`** - Plugin command patterns

## See Also

### Related Skills
- **[plugin-development](../plugin-development/SKILL.md)** - Plugin structure
- **[agent-development](../agent-development/SKILL.md)** - Agents commands can launch
- **[skill-development](../skill-development/SKILL.md)** - Skills commands can reference
- **[hook-development](../hook-development/SKILL.md)** - Hooks triggered by commands

### Related Agents
- **command-creator** - Create command files
- **plugin-validator** - Validate command files
