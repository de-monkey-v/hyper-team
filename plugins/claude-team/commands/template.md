---
namespace: team
description: Manage team templates (list, save, show)
argument-hint: [list|save <name>|show <name>]
allowed-tools: Bash, Read, Write, Glob, AskUserQuestion
---

Load team-templates skill for reference on built-in patterns and role presets.

Parse $ARGUMENTS to determine action:
- No arguments or "list" → List all templates
- "save <name>" → Save current active team as template
- "show <name>" → Display template details

## Action 1: List Templates

Display built-in templates table:

| Pattern | Members | Use Case |
|---------|---------|----------|
| small-focused | 2 (implementer+tester) | Single domain, <5 files, straightforward tasks |
| uniform-workers | 3-4 (coordinator+workers+tester) | Repetitive similar tasks (multiple API endpoints) |
| specialized-workers | 3-4 (specialists+tester) | Different task types (DB+API+UI) |
| full-stack | 3-4 (backend+frontend+tester) | Full-stack projects with frontend/backend split |

Then check for custom templates:
1. Check if `.claude-team/templates/` directory exists: `!\`ls -1 .claude-team/templates/*.json 2>/dev/null\``
2. If custom templates exist, list them:
   - Extract template names from filenames
   - Display as additional table section

Output format:
```
**Built-in Templates:**
[table above]

**Custom Templates:**
- template-name-1
- template-name-2
```

## Action 2: Save Current Team as Template

**Prerequisites:**
1. Verify active team exists: `!\`ls -1 .claude-team/active/*/config.json 2>/dev/null | head -n1\``
2. If no active team, show error: "No active team found. Create a team first with cc team create."

**Save Process:**
1. Extract team name from $ARGUMENTS (word after "save")
2. Read active team config: `!\`cat $(ls -1 .claude-team/active/*/config.json | head -n1)\``
3. Extract members array from config.json
4. Create template structure:
```json
{
  "name": "{template-name}",
  "description": "Custom template saved from {original-team-name}",
  "members": [
    {
      "name": "{member.name}",
      "role": "{member.metadata.role or 'custom'}",
      "model": "{member.model}",
      "color": "{member.color}",
      "prompt_template": "{member.prompt}"
    }
  ]
}
```
5. Ensure `.claude-team/templates/` directory exists: `!\`mkdir -p .claude-team/templates\``
6. Write template to `.claude-team/templates/{template-name}.json`
7. Display confirmation: "Template '{template-name}' saved with {count} members"

## Action 3: Show Template Details

**Template Resolution:**
1. Extract template name from $ARGUMENTS (word after "show")
2. Check if it's a built-in template (small-focused, uniform-workers, specialized-workers, full-stack)
3. If built-in, use team-templates skill knowledge to display:
   - Pattern name and description
   - When to use criteria
   - Member structure with roles and models
   - Typical workflow
4. If not built-in, check for custom template:
   - Read `.claude-team/templates/{name}.json`
   - If file doesn't exist, show error: "Template '{name}' not found"

**Display Format:**

For built-in templates:
```
## {Pattern Name} Template

**When to use:**
- [criteria from team-templates skill]

**Structure:**
| Member | Role | Model |
|--------|------|-------|
| {name} | {role} | {model} |

**Workflow:**
[workflow steps from team-templates skill]
```

For custom templates:
```
## {Template Name} (Custom)

**Description:** {template.description}

**Members:**
| Name | Role | Model | Color |
|------|------|-------|-------|
| {member.name} | {member.role} | {member.model} | {member.color} |

**Usage:**
cc team create my-team --template {template-name}
```

## Error Handling

- Invalid action: "Usage: /team:template [list|save <name>|show <name>]"
- Missing template name for save/show: "Please provide a template name"
- Template not found: "Template '{name}' not found. Use /team:template list to see available templates"
- No active team for save: "No active team found. Create a team first."
