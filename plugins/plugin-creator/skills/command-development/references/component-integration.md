# Component Integration Patterns

This reference shows how commands integrate with other plugin components.

## Agent Integration

Commands can launch plugin agents for complex tasks.

**IMPORTANT**: To use Task tool for launching agents, add `Task` to allowed-tools:

```yaml
---
description: Orchestration command
allowed-tools: Read, Write, Task
---
```

### Basic Agent Launch

```markdown
---
description: Deep code review
argument-hint: [file-path]
---

Initiate comprehensive review of @$1 using the code-reviewer agent.

The agent will analyze:
- Code structure
- Security issues
- Performance
- Best practices

Agent uses plugin resources:
- ${CLAUDE_PLUGIN_ROOT}/config/rules.json
- ${CLAUDE_PLUGIN_ROOT}/checklists/review.md
```

**Key points:**
- Agent must exist in `plugin/agents/` directory
- Claude uses Task tool to launch agent
- Document agent capabilities
- Reference plugin resources agent uses

### Multi-Agent Workflow

```markdown
---
description: Full review workflow
argument-hint: [file]
---

Phase 1: Launch code-reviewer agent for structural analysis
Phase 2: Launch security-analyzer agent for vulnerability scan
Phase 3: Launch performance-auditor agent for optimization check
Phase 4: Compile findings into unified report
```

## XML 구조화된 에이전트 연동

### XML 데이터 전달 패턴

커맨드에서 에이전트를 호출할 때 XML 태그로 구조화된 데이터를 전달합니다:

```markdown
---
description: Deep code review with structured data
argument-hint: [file-path]
allowed-tools: Read, Task
---

Task tool prompt for code-reviewer agent:

<task-context>
<target-file>@$1</target-file>
<review-scope>security, performance, best-practices</review-scope>
<project-standards>CLAUDE.md</project-standards>
</task-context>

<specification>
Agent uses plugin resources:
- ${CLAUDE_PLUGIN_ROOT}/config/rules.json
- ${CLAUDE_PLUGIN_ROOT}/checklists/review.md
</specification>

<instructions>
Perform comprehensive review of the target file.
Follow project standards and checklist.
Report findings in the standard output format.
</instructions>
```

### Multi-Agent XML 워크플로우

```markdown
---
description: Full review workflow with XML handoff
argument-hint: [file]
allowed-tools: Read, Task
---

Phase 1: Structural Analysis
Task tool:
<task-context>
<target>@$1</target>
<phase>structural-analysis</phase>
</task-context>
<instructions>Analyze code structure and architecture.</instructions>

Phase 2: Security Scan (receives Phase 1 context)
Task tool:
<task-context>
<target>@$1</target>
<phase>security-scan</phase>
<prior-analysis>{Phase 1 results}</prior-analysis>
</task-context>
<instructions>Scan for vulnerabilities, considering structural analysis.</instructions>

Phase 3: Compile findings into unified report
```

### Long Context 원칙

긴 데이터는 TOP에, 짧은 지시사항은 BOTTOM에 배치합니다:

```markdown
Task tool prompt:
<current-state>
{긴 현재 상태 분석 결과}
</current-state>

<specification>
{plan에서 추출한 상세 명세}
</specification>

<instructions>
{짧은 지시사항}
</instructions>
```

**Key points:**
- Use `<task-context>` for metadata (file paths, modes, scopes)
- Use `<specification>` for detailed requirements
- Use `<instructions>` for action directives (keep SHORT, place at BOTTOM)
- Use `<current-state>` or `<prior-analysis>` for context from previous phases

## Skill Integration

Commands can leverage plugin skills for specialized knowledge.

**IMPORTANT**: To use Skill tool for loading skills, add `Skill` to allowed-tools:

```yaml
---
description: Command using skills
allowed-tools: Read, Skill
---
```

### Basic Skill Reference

```markdown
---
description: Document API with standards
argument-hint: [api-file]
---

Document API in @$1 following plugin standards.

Use the api-docs-standards skill to ensure:
- Complete endpoint documentation
- Consistent formatting
- Example quality
- Error documentation

Generate production-ready API docs.
```

**Key points:**
- Skill must exist in `plugin/skills/` directory
- Mention skill name to trigger invocation
- Document skill purpose
- Explain what skill provides

### Multiple Skills

```markdown
---
description: Create feature
argument-hint: [feature-name]
---

Creating feature: $1

Apply these skills:
- Use coding-standards skill for style guidelines
- Use testing-patterns skill for test structure
- Use documentation skill for README updates

Follow all skill guidance throughout implementation.
```

## Hook Coordination

Commands can work with plugin hooks.

### Prepare State for Hooks

```markdown
---
description: Configure strict mode
---

Enable strict validation:
1. Create .claude/strict-mode.flag file
2. Hooks will detect this flag
3. All writes will be validated

Note: Hooks run automatically after this is set.
Disable with: /disable-strict-mode
```

### Document Hook Behavior

```markdown
---
description: Submit code
allowed-tools: Bash(git:*)
---

Submit changes for review.

Note: PreToolUse hooks will:
- Validate code style
- Check for secrets
- Enforce commit message format

If validation fails, hooks will provide feedback.
```

## Multi-Component Workflows

Combine multiple component types for complex workflows.

### Complete Review Workflow

```markdown
---
description: Comprehensive review workflow
argument-hint: [file]
allowed-tools: Bash(node:*), Read
---

Target: @$1

Phase 1 - Static Analysis:
!\`node ${CLAUDE_PLUGIN_ROOT}/scripts/lint.js $1\`

Phase 2 - Deep Review:
Launch code-reviewer agent for detailed analysis.

Phase 3 - Standards Check:
Use coding-standards skill for validation.

Phase 4 - Report:
Template: @${CLAUDE_PLUGIN_ROOT}/templates/review.md

Compile findings into report following template.
```

### Build and Deploy Pipeline

```markdown
---
description: Full deployment pipeline
argument-hint: [environment]
allowed-tools: Bash(*), Read
---

Deploy to: $1

Step 1 - Build:
!\`bash ${CLAUDE_PLUGIN_ROOT}/scripts/build.sh\`

Step 2 - Test:
Launch test-runner agent

Step 3 - Security:
Use security-checklist skill

Step 4 - Deploy:
!\`bash ${CLAUDE_PLUGIN_ROOT}/scripts/deploy.sh $1\`

Step 5 - Verify:
Launch deployment-verifier agent
```

## MCP Tool Integration

Commands can use MCP-provided tools.

### Pre-Allow MCP Tools

```markdown
---
description: Create Asana task
allowed-tools: mcp__plugin_asana_asana__asana_create_task
---

Create task in Asana with:
- Title from user request
- Description with requirements
- Appropriate project assignment
```

### Multiple MCP Services

```markdown
---
description: Sync issue
allowed-tools: mcp__plugin_github_github__*, mcp__plugin_jira_jira__*
---

Sync issue between GitHub and Jira:
1. Get issue details from GitHub
2. Create/update corresponding Jira ticket
3. Link references between systems
```

## Best Practices

### Component Discovery

Document which components a command uses:

```markdown
<!--
Components used:
- Agents: code-reviewer, test-runner
- Skills: coding-standards
- Scripts: lint.js, build.sh
- MCP: asana (create_task)
-->
```

### Error Handling Across Components

```markdown
If agent fails:
  Log error and continue with manual analysis

If script fails:
  Check script permissions
  Verify dependencies installed

If MCP tool unavailable:
  Suggest alternative approach
```

### Resource References

Always use ${CLAUDE_PLUGIN_ROOT} for plugin resources:

```markdown
# Good
@${CLAUDE_PLUGIN_ROOT}/templates/report.md
!\`bash ${CLAUDE_PLUGIN_ROOT}/scripts/build.sh\`

# Bad
@./templates/report.md
!\`bash ./scripts/build.sh\`
```
