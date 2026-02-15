---
name: team-templates
description: This skill should be used when the user asks to "create a team using a template", "use a team pattern", "select a role preset", "팀 템플릿", "팀 구성", "역할 프리셋", "role preset", "team template", or needs guidance on team composition patterns and predefined role configurations.
version: 0.1.0
---

# Team Templates and Role Presets

Accelerate team creation with predefined patterns and role configurations.

## Team Composition Patterns

Use these proven patterns to quickly structure teams based on project characteristics.

### Pattern Selection Guide

| Pattern | Members | Composition | Best For |
|---------|---------|-------------|----------|
| **Small Focused** | 2 | implementer + tester | Single domain, <5 files, straightforward tasks |
| **Uniform Workers** | 3-4 | coordinator + worker-N + tester | Repetitive similar tasks (e.g., multiple API endpoints) |
| **Specialized Workers** | 3-4 | domain-specific-N + tester | Different task types (e.g., DB + API + UI) |
| **Full-Stack** | 3-4 | backend + frontend + tester | Full-stack projects requiring frontend/backend split |

### Pattern Details

#### Small Focused Team

**When to use:**
- Simple feature implementation
- Single technology domain
- Quick iteration needed
- Small codebase changes (<5 files)

**Structure:**
```json
{
  "members": [
    {"name": "implementer", "role": "implementer", "model": "sonnet"},
    {"name": "tester", "role": "tester", "model": "sonnet"}
  ]
}
```

**Workflow:**
1. Implementer writes code following specifications
2. Tester validates and reports issues
3. Implementer fixes bugs

#### Uniform Workers Team

**When to use:**
- Multiple similar tasks
- Parallel execution beneficial
- Same skill set needed across tasks
- Examples: creating multiple API endpoints, processing multiple files

**Structure:**
```json
{
  "members": [
    {"name": "coordinator", "role": "coordinator", "model": "sonnet"},
    {"name": "worker-1", "role": "implementer", "model": "haiku"},
    {"name": "worker-2", "role": "implementer", "model": "haiku"},
    {"name": "tester", "role": "tester", "model": "sonnet"}
  ]
}
```

**Workflow:**
1. Coordinator splits tasks evenly among workers
2. Workers execute assigned tasks in parallel
3. Tester validates all outputs
4. Coordinator consolidates results

#### Specialized Workers Team

**When to use:**
- Tasks require different expertise
- Cross-domain features (DB + API + UI)
- Each member has distinct responsibilities
- Examples: database migration + API updates + UI changes

**Structure:**
```json
{
  "members": [
    {"name": "db-specialist", "role": "backend", "model": "sonnet"},
    {"name": "api-specialist", "role": "backend", "model": "sonnet"},
    {"name": "ui-specialist", "role": "frontend", "model": "sonnet"},
    {"name": "tester", "role": "tester", "model": "sonnet"}
  ]
}
```

**Workflow:**
1. Each specialist works on their domain
2. Specialists coordinate interfaces between domains
3. Tester validates integration
4. Team iterates on feedback

#### Full-Stack Team

**When to use:**
- Projects spanning frontend and backend
- Coordinated full-stack features
- API + UI implementation
- Examples: new user feature, dashboard creation

**Structure:**
```json
{
  "members": [
    {"name": "backend", "role": "backend", "model": "sonnet"},
    {"name": "frontend", "role": "frontend", "model": "sonnet"},
    {"name": "tester", "role": "tester", "model": "sonnet"}
  ]
}
```

**Workflow:**
1. Backend creates API endpoints and data logic
2. Frontend builds UI consuming the API
3. Team coordinates on API contracts
4. Tester validates end-to-end functionality

## Role Presets

Predefined roles with optimized configurations for common team functions.

### Available Presets

| Role | Model | Color | Primary Function |
|------|-------|-------|------------------|
| **implementer** | sonnet | blue | Code implementation specialist |
| **tester** | sonnet | green | Testing and validation specialist |
| **reviewer** | opus | purple | Code review and quality specialist |
| **coordinator** | sonnet | yellow | Task distribution and coordination |
| **researcher** | opus | cyan | Research and analysis specialist |
| **backend** | sonnet | blue | Backend implementation specialist |
| **frontend** | sonnet | orange | Frontend implementation specialist |

### Preset Quick Reference

**implementer**
- Focuses on writing clean, working code
- Follows existing code patterns
- Implements from specifications
- Reports completion to team leader

**tester**
- Writes and executes tests
- Checks code coverage
- Identifies bugs and edge cases
- Reports quality issues

**reviewer**
- Evaluates code quality
- Checks architecture compliance
- Identifies security vulnerabilities
- Assesses performance implications

**coordinator**
- Distributes work among team members
- Monitors task progress
- Resolves bottlenecks
- Consolidates deliverables

**researcher**
- Investigates technologies and patterns
- Analyzes best practices
- Recommends solutions
- Documents findings

**backend**
- Implements APIs and server logic
- Manages database operations
- Coordinates with frontend on contracts
- Ensures data integrity

**frontend**
- Builds UI components and pages
- Integrates with backend APIs
- Implements responsive design
- Ensures user experience quality

### Using Presets

Reference presets when creating team members:

```bash
# Use preset directly
cc team create my-team --member implementer:sonnet --member tester:sonnet

# Customize preset
cc team create my-team --member implementer:opus:purple --member tester:haiku:green
```

See `references/role-presets.md` for detailed prompt templates for each role.

## Model Selection Guide

Choose the appropriate model based on task complexity and budget.

| Model | Speed | Cost | Best For |
|-------|-------|------|----------|
| **haiku** | Fastest | Lowest | Simple repetitive tasks, cost optimization |
| **sonnet** | Balanced | Medium | General implementation, balanced choice |
| **opus** | Slowest | Highest | Complex analysis, critical quality needs |

### Model Selection Examples

**Use haiku for:**
- Formatting code
- Running simple tests
- File operations
- Template-based generation

**Use sonnet for:**
- Feature implementation
- API development
- Test writing
- Standard coordination

**Use opus for:**
- Architecture decisions
- Complex debugging
- Security reviews
- Research and analysis

## Custom Templates

Save frequently used team configurations as custom templates.

### Template Format

Store templates in `.claude-team/templates/{name}.json`:

```json
{
  "name": "api-development",
  "description": "Team for REST API development",
  "members": [
    {
      "name": "api-implementer",
      "role": "backend",
      "model": "sonnet",
      "color": "blue",
      "prompt_template": "Implement REST API endpoints..."
    },
    {
      "name": "api-tester",
      "role": "tester",
      "model": "sonnet",
      "color": "green",
      "prompt_template": "Test API endpoints..."
    }
  ]
}
```

### Using Custom Templates

```bash
# Create from custom template
cc team create my-api --template api-development

# List available templates
cc team templates list

# View template details
cc team templates show api-development
```

## Best Practices

### Team Size
- Keep teams small (2-4 members) for focus
- Larger teams increase coordination overhead
- Add members only when parallelization benefits outweigh coordination costs

### Role Assignment
- Give each member a clear, distinct responsibility
- Avoid overlapping roles within one team
- Use coordinator for teams >3 members

### Model Budget
- Start with sonnet for all roles
- Upgrade to opus only for critical analysis/decision roles
- Downgrade to haiku for simple, repetitive tasks

### Pattern Selection
- Match pattern to project structure
- Start small, expand if needed
- Prefer specialized over uniform when tasks differ significantly

## Additional Resources

### Reference Files
- **`references/role-presets.md`** - Detailed prompt templates for each role preset

### Related Commands
- `cc team create` - Create teams using templates
- `cc team add` - Add members with role presets
- `cc team list` - View active teams

## Examples

### Example 1: Quick Feature Implementation

```bash
# Small focused team for simple feature
cc team create feature-login \
  --member implementer:sonnet:blue \
  --member tester:sonnet:green
```

### Example 2: Multi-Endpoint API

```bash
# Uniform workers for parallel API development
cc team create api-v2 \
  --member coordinator:sonnet:yellow \
  --member worker-1:haiku:blue \
  --member worker-2:haiku:blue \
  --member worker-3:haiku:blue \
  --member tester:sonnet:green
```

### Example 3: Full-Stack Dashboard

```bash
# Full-stack team
cc team create dashboard \
  --member backend:sonnet:blue \
  --member frontend:sonnet:orange \
  --member tester:sonnet:green
```

### Example 4: Complex Migration

```bash
# Specialized workers with reviewer
cc team create migration \
  --member db-specialist:opus:purple \
  --member api-updater:sonnet:blue \
  --member tester:sonnet:green \
  --member reviewer:opus:purple
```
