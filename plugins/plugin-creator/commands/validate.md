---
description: Validate plugin structure, manifest, and components
argument-hint: [plugin path]
allowed-tools: Read, Glob, Grep, Bash, Task
---

# Plugin Validation

Validate a Claude Code plugin for correctness, best practices, and quality standards.

## Context

**Plugin path:** $ARGUMENTS

If no path provided, look for plugin in current directory or `.claude-plugin/`.

## Process

### Step 1: Locate Plugin

Find the plugin root by looking for `.claude-plugin/plugin.json`:
- Check provided path
- Check current directory
- Check `.claude-plugin/` subdirectory

### Step 2: Run Plugin Validator Agent

Use the plugin-validator agent for comprehensive validation:

```
Task tool:
- subagent_type: "plugin-creator:plugin-validator"
- description: "Validate plugin structure and components"
- prompt: |
    <task-context>
    <plugin-path>$ARGUMENTS (or detected path)</plugin-path>
    <validation-scope>full</validation-scope>
    </task-context>

    <instructions>
    Perform comprehensive plugin validation:
    - Manifest validity (plugin.json)
    - Directory structure
    - Each component format (skills, agents, commands, hooks)
    - Naming conventions
    - Security checks
    Provide detailed report with fix suggestions for each issue.
    </instructions>
```

### Step 3: Review Results

The validator will check:

**Manifest (plugin.json):**
- ✓ Valid JSON syntax
- ✓ Required fields (name, version, description)
- ✓ Name follows kebab-case convention
- ✓ Version is valid semver

**Structure:**
- ✓ `.claude-plugin/plugin.json` exists
- ✓ Component directories are valid (skills/, agents/, commands/, hooks/)
- ✓ No unexpected files in wrong locations

**Skills:**
- ✓ Each skill has SKILL.md
- ✓ Descriptions use third person
- ✓ Trigger phrases are specific
- ✓ Word count is appropriate (1,500-3,000)

**Agents:**
- ✓ Frontmatter has required fields
- ✓ Identifier follows naming rules
- ✓ Description is single-line (not multiline YAML)
- ✓ Example blocks are present

**Commands:**
- ✓ Frontmatter has description
- ✓ Content is instructions FOR Claude
- ✓ allowed-tools is minimal

**Hooks:**
- ✓ hooks.json is valid JSON
- ✓ Uses plugin wrapper format: `{"hooks": {...}}`
- ✓ Event names are valid
- ✓ ${CLAUDE_PLUGIN_ROOT} used for paths

**MCP:**
- ✓ .mcp.json is valid JSON
- ✓ Server configurations are complete
- ✓ Uses HTTPS/WSS (not HTTP/WS)
- ✓ ${CLAUDE_PLUGIN_ROOT} used for paths

**Security:**
- ✓ No hardcoded credentials
- ✓ No absolute paths (use ${CLAUDE_PLUGIN_ROOT})
- ✓ .gitignore excludes .local.md files

### Step 4: Present Findings

Summarize validation results with:
- Critical issues (must fix)
- Warnings (should fix)
- Recommendations (nice to have)
- Positive findings (what's good)

### Step 5: Offer Fixes

Ask user:
"Validation found [X] critical issues, [Y] warnings. Would you like me to fix the critical issues now?"

If yes, fix each critical issue and re-validate.

## Output Format

```markdown
## Plugin Validation Report: [name]

### Summary
- **Status:** PASS / NEEDS ATTENTION / FAIL
- **Critical Issues:** X
- **Warnings:** Y
- **Components:** X skills, Y agents, Z commands, W hooks

### Critical Issues
1. [Issue description]
   - **Location:** [file path]
   - **Fix:** [how to fix]

### Warnings
1. [Warning description]
   - **Location:** [file path]
   - **Recommendation:** [what to do]

### Positive Findings
- ✅ [What's good about the plugin]

### Component Summary

| Component | Count | Status |
|-----------|-------|--------|
| Skills | X | ✅ Valid |
| Agents | Y | ⚠️ 1 warning |
| Commands | Z | ✅ Valid |
| Hooks | W | ❌ 1 critical |

### Next Steps
1. [Priority fix 1]
2. [Priority fix 2]
```

## Quick Validation Checklist

For manual quick check:

```bash
# Check plugin.json exists and is valid
cat .claude-plugin/plugin.json | jq .

# Check skill descriptions
grep -r "^description:" skills/*/SKILL.md

# Check agent descriptions (should be single-line)
grep "^description:" agents/*.md

# Check hooks.json format
cat hooks/hooks.json | jq .

# Check for hardcoded paths
grep -r "/home/" . --include="*.md" --include="*.json"
grep -r "/Users/" . --include="*.md" --include="*.json"
```
