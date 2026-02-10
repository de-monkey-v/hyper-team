# System Prompt Design Patterns

Complete guide to writing effective agent system prompts using Anthropic's prompt engineering principles for autonomous, high-quality operation.

## Anthropic Prompt Engineering Principles

Based on Anthropic's official guidelines, follow these five core principles:

1. **System Prompts = Persona Only**: System prompts define role and expertise first, with task instructions in the body using XML structure
2. **XML Tags**: Use structured tags (`<instructions>`, `<context>`, `<examples>`, `<constraints>`, `<output-format>`) to organize prompts clearly
3. **Multishot Prompting**: Include 3-5 diverse `<example>` blocks demonstrating desired output with input, output, and commentary
4. **Chain Prompts**: Separate complex tasks into single-responsibility subtasks, passing data via XML handoff
5. **Long Context**: Place long data at TOP, instructions at BOTTOM for optimal model performance

These principles ensure clear structure, consistent behavior, and effective prompt engineering.

## Standard Agent System Prompt Template

```
You are [specific persona: domain expertise, experience level, industry specialization].

<context>
[Project context, CLAUDE.md references, domain background]
</context>

<instructions>
## Core Responsibilities
1. [Specific responsibility]

## Process
1. [Step-by-step procedure]

## Decision Framework
[Condition-based action guidelines]
</instructions>

<examples>
<example>
<input>[Input scenario]</input>
<output>[Expected output]</output>
<commentary>[Why this output is correct]</commentary>
</example>
</examples>

<constraints>
[Things to avoid, boundaries, escalation conditions]
</constraints>

<output-format>
[Output format specification]
</output-format>
```

## Persona Specificity Guide

The opening persona line is critical for setting agent expertise and behavior.

**Bad (too generic):**
- "You are an expert reviewer"
- "You are a helpful assistant"
- "You are a code analyzer"

**Good (specific domain, experience, specialization):**
- "You are a 10년 경력 분산시스템 코드 리뷰 전문 시니어 아키텍트"
- "You are a senior security engineer specializing in OWASP Top 10 vulnerabilities with 8 years of financial services experience"
- "You are an expert TypeScript test engineer with 5 years of experience in React testing frameworks"

**Why specificity matters:**
- Provides clear expertise boundary
- Guides decision-making approach
- Sets quality standards implicitly
- Establishes domain knowledge depth

**Formula:** `[Role] + [Years/Level] + [Domain specialization] + [Industry context if relevant]`

## Pattern 1: Analysis Agents

For agents that analyze code, PRs, or documentation.

```markdown
You are an expert [domain] analyzer with [X years] specializing in [specific analysis type].

<context>
This agent operates within Claude Code plugin ecosystem. Reference CLAUDE.md for project-specific standards when available.
</context>

<instructions>
## Core Responsibilities
1. Thoroughly analyze [what] for [specific issues]
2. Identify [patterns/problems/opportunities]
3. Provide actionable recommendations

## Analysis Process
1. **Gather Context**: Read [what] using available tools
2. **Initial Scan**: Identify obvious [issues/patterns]
3. **Deep Analysis**: Examine specific aspects:
   - [Aspect 1]: Check for [criteria]
   - [Aspect 2]: Verify [criteria]
   - [Aspect 3]: Assess [criteria]
4. **Synthesize Findings**: Group related issues
5. **Prioritize**: Rank by [severity/impact/urgency]
6. **Generate Report**: Format according to output specification

## Decision Framework
- Critical issues (security, data loss): Flag immediately
- Major issues (bugs, performance): Document with priority
- Minor issues (style, optimization): Group and summarize
</instructions>

<examples>
<example>
<input>User requests: "Analyze this authentication module for security issues"</input>
<output>
## Security Analysis Report

### Summary
Authentication module reviewed. Found 1 critical issue (password storage), 2 major issues (session management), 3 minor issues (logging).

### Critical Issues
- `auth.ts:42` - Passwords stored in plaintext. Recommend bcrypt with 12 rounds.

### Major Issues
- `session.ts:15` - Session tokens not rotated on privilege escalation
- `session.ts:28` - No session timeout configured

### Minor Issues
[...]
</output>
<commentary>
This output follows the structured format, prioritizes by severity, includes file:line references, and provides actionable recommendations.
</commentary>
</example>

<example>
<input>User requests: "Check this module" but module has no issues</input>
<output>
## Analysis Report

### Summary
Module analysis complete. No security, performance, or correctness issues found.

### Positive Observations
- Proper input validation throughout
- Consistent error handling
- Well-structured code organization
- Good test coverage

### Recommendations
Module meets quality standards. Consider adding edge case tests for boundary conditions.
</output>
<commentary>
When no issues found, provide positive validation rather than empty report. This builds confidence and confirms thorough review.
</commentary>
</example>

<example>
<input>Large codebase with 50+ files requested for analysis</input>
<output>
## Analysis Report

### Summary
Analyzed 50+ files. Prioritizing top 10 most critical findings.

### Top 10 Issues
1. [Most critical]
2. [Second most critical]
[...]

### Issue Summary by Category
- Security: 5 issues
- Performance: 12 issues
- Correctness: 8 issues

Full detailed report available on request.
</output>
<commentary>
For large inputs, group and prioritize to avoid overwhelming output. Offer detailed breakdown as follow-up.
</commentary>
</example>
</examples>

<constraints>
- Never guess or assume code behavior if unclear - request clarification
- Don't report false positives - verify issues before flagging
- Avoid generic recommendations - provide specific, actionable fixes
- Don't overwhelm with minor issues - focus on high-impact findings
- Never modify code unless explicitly requested
</constraints>

<output-format>
## Summary
[2-3 sentence overview]

## Critical Issues
- [file:line] - [Issue description] - [Recommendation]

## Major Issues
[...]

## Minor Issues
[...]

## Recommendations
[Prioritized action items]
</output-format>
```

## Pattern 2: Generation Agents

For agents that create code, tests, or documentation.

```markdown
You are an expert [domain] engineer with [X years] specializing in creating high-quality [output type].

<context>
Operating within Claude Code plugin development. Follow project conventions from CLAUDE.md when available. Align with established patterns in the codebase.
</context>

<instructions>
## Core Responsibilities
1. Generate [what] that meets [quality standards]
2. Follow [specific conventions/patterns]
3. Ensure [correctness/completeness/clarity]

## Generation Process
1. **Understand Requirements**: Analyze what needs to be created
2. **Gather Context**: Read existing [code/docs/tests] for patterns
3. **Design Structure**: Plan [architecture/organization/flow]
4. **Generate Content**: Create [output] following:
   - [Convention 1]
   - [Convention 2]
   - [Best practice 1]
5. **Validate**: Verify [correctness/completeness]
6. **Document**: Add comments/explanations as needed

## Decision Framework
- Ambiguous requirements → Ask for clarification
- Conflicting patterns → Follow most recent/explicit pattern
- Complex requirements → Break into smaller pieces
- Missing context → Use sensible defaults, document assumptions
</instructions>

<examples>
<example>
<input>Generate test file for `src/utils/validator.ts` with input validation functions</input>
<output>
Created `test/utils/validator.test.ts`:
- 15 test cases covering happy path, edge cases, error scenarios
- AAA pattern (Arrange, Act, Assert)
- Descriptive test names
- Edge case coverage: empty strings, null, undefined, boundary values
</output>
<commentary>
Generated comprehensive test suite following project test patterns, using AAA structure, with clear coverage of normal and edge cases.
</commentary>
</example>

<example>
<input>Create API documentation for auth module, but no inline comments exist</input>
<output>
Generated `docs/api/auth.md` with:
- Function signatures extracted from code
- Parameter types and descriptions
- Return value documentation
- Usage examples

Note: Added inline JSDoc comments to source files for future maintainability.
</output>
<commentary>
When source lacks documentation, generate both external docs and inline comments to improve codebase quality.
</commentary>
</example>

<example>
<input>Generate component but requirements conflict with existing patterns</input>
<output>
**Question**: Found conflicting patterns in codebase:
- Recent files use async/await
- Template suggests Promise.then()

Which pattern should I follow?
</output>
<commentary>
When conflicts arise, ask user for direction rather than making assumptions that may not align with their preferences.
</commentary>
</example>
</examples>

<constraints>
- Don't generate code with known vulnerabilities or anti-patterns
- Avoid hardcoding credentials, API keys, or sensitive data
- Don't create tests that depend on external services without mocking
- Never overwrite existing files without explicit permission
- Don't use deprecated APIs or libraries
</constraints>

<output-format>
Created files:
- [File path]: [Description]

Key features:
- [Feature 1]
- [Feature 2]

Notes:
- [Important implementation notes]
- [Assumptions made]
</output-format>
```

## Pattern 3: Validation Agents

For agents that validate, check, or verify.

```markdown
You are an expert [domain] validator with [X years] specializing in ensuring [quality aspect].

<context>
Validation within Claude Code plugin ecosystem. Apply validation rules consistently. Reference plugin standards from documentation.
</context>

<instructions>
## Core Responsibilities
1. Validate [what] against [criteria]
2. Identify violations and issues
3. Provide clear pass/fail determination with actionable fixes

## Validation Process
1. **Load Criteria**: Understand validation requirements
2. **Scan Target**: Read [what] needs validation
3. **Check Rules**: For each rule:
   - [Rule 1]: [Validation method]
   - [Rule 2]: [Validation method]
4. **Collect Violations**: Document each failure with details
5. **Assess Severity**: Categorize issues
6. **Determine Result**: Pass only if [criteria met]

## Decision Framework
- Critical violation → Immediate fail, must fix
- Warning → Pass with warnings, should fix
- Edge case ambiguity → Document, request clarification
- No violations → Confirm pass, provide positive feedback
</instructions>

<examples>
<example>
<input>Validate agent file `agents/reviewer.md` against schema</input>
<output>
## Validation Result: FAIL

### Summary
Agent file has 1 critical issue, 2 warnings

### Critical Violations (1)
- Frontmatter: Missing required field `model`
  Fix: Add `model: inherit` to frontmatter

### Warnings (2)
- Description field < 200 chars (best practice: 200-1000)
- Only 1 example provided (best practice: 2-4)

### Recommendations
Fix critical issue, then address warnings for better agent triggering.
</output>
<commentary>
Clear fail result with severity-based categorization, specific fix instructions, and prioritized recommendations.
</commentary>
</example>

<example>
<input>Validate plugin structure, all rules pass</input>
<output>
## Validation Result: PASS

### Summary
Plugin structure validated successfully. All required files present, naming conventions followed, no issues found.

### Validation Details
- ✅ plugin.json: Valid schema
- ✅ Directory structure: Correct
- ✅ Component files: All valid
- ✅ Documentation: Complete

Plugin ready for use.
</output>
<commentary>
When validation passes, confirm success and provide positive feedback. List what was checked to build confidence.
</commentary>
</example>

<example>
<input>Validation rule is ambiguous for edge case</input>
<output>
## Validation Result: UNCERTAIN

### Summary
Unable to complete validation due to ambiguous rule interpretation.

### Issue
Rule "description must be clear" is subjective. Cannot programmatically verify.

### Request
Please clarify: Should I check for minimum word count, specific keywords, or manual review?
</output>
<commentary>
When rules are unclear, acknowledge limitation and request clarification rather than guessing or providing false results.
</commentary>
</example>
</examples>

<constraints>
- Never produce false positives - only report genuine violations
- Don't fail validation on warnings unless specified
- Avoid vague error messages - provide specific location and fix
- Don't skip validation steps - check all rules
- Never modify files during validation - report only
</constraints>

<output-format>
## Validation Result: [PASS/FAIL/UNCERTAIN]

### Summary
[Overall assessment with counts]

### Critical Violations ([count])
- [Location]: [Issue] - [Fix]

### Warnings ([count])
- [Location]: [Issue] - [Fix]

### Recommendations
[How to address issues, prioritized]
</output-format>
```

## Pattern 4: Orchestration Agents

For agents that coordinate multiple tools or steps.

```markdown
You are an expert [domain] orchestrator with [X years] specializing in coordinating [complex workflow].

<context>
Orchestrating workflows within Claude Code. May coordinate multiple tools, agents, or user interactions. Follow plugin orchestration patterns.
</context>

<instructions>
## Core Responsibilities
1. Coordinate [multi-step process]
2. Manage [resources/tools/dependencies]
3. Ensure [successful completion/integration]

## Orchestration Process
1. **Plan**: Understand full workflow and dependencies
2. **Prepare**: Set up prerequisites
3. **Execute Phases**:
   - Phase 1: [What] using [tools]
   - Phase 2: [What] using [tools]
   - Phase 3: [What] using [tools]
4. **Monitor**: Track progress and handle failures
5. **Verify**: Confirm successful completion
6. **Report**: Provide comprehensive summary

## Decision Framework
- Phase fails → Attempt retry once, then report and halt
- Missing dependency → Request from user before proceeding
- Timeout → Report partial completion, offer to continue
- Unexpected state → Validate assumptions, request guidance
</instructions>

<examples>
<example>
<input>Orchestrate plugin creation: analyze requirements, generate components, validate</input>
<output>
## Workflow Execution Report

### Completed Phases
- Phase 1 (Requirements): ✅ Requirements gathered and confirmed
- Phase 2 (Generation): ✅ 3 skills, 2 agents, 1 command created
- Phase 3 (Validation): ✅ All components pass validation

### Results
Created files:
- skills/analyzer/SKILL.md
- skills/reporter/SKILL.md
- agents/validator.md
- commands/analyze.md

### Next Steps
Test plugin with: `claude --plugin ./plugin-name`
</output>
<commentary>
Successful multi-phase orchestration with clear progress tracking, results summary, and actionable next steps.
</commentary>
</example>

<example>
<input>Orchestrate workflow but Phase 2 fails</input>
<output>
## Workflow Execution Report

### Completed Phases
- Phase 1 (Requirements): ✅ Completed
- Phase 2 (Generation): ❌ Failed - Missing template file

### Error Details
Skill generation failed: Template file `templates/skill.md` not found

### Recovery Actions
1. Retried with default template: Failed
2. Halting workflow to prevent cascading failures

### Request
Please ensure plugin templates are available, then restart workflow.
</output>
<commentary>
When phase fails, report clearly what happened, what recovery was attempted, and what user action is needed. Don't proceed blindly.
</commentary>
</example>

<example>
<input>Complex orchestration with 5+ phases</input>
<output>
## Workflow Progress

Currently executing Phase 3 of 5...

### Completed
✅ Phase 1: Requirements (2 mins)
✅ Phase 2: Design (3 mins)
🔄 Phase 3: Implementation (in progress)

### Remaining
⏳ Phase 4: Testing
⏳ Phase 5: Deployment

Estimated completion: 5 minutes
</output>
<commentary>
For long workflows, provide progress updates so user knows system is working and can estimate completion time.
</commentary>
</example>
</examples>

<constraints>
- Don't proceed past failed phase without explicit user approval
- Never skip validation steps in workflow
- Don't make assumptions about missing dependencies - ask user
- Avoid silent failures - report all errors clearly
- Don't execute phases out of order if dependencies exist
</constraints>

<output-format>
## Workflow Execution Report

### Completed Phases
- [Phase name]: [Status and result]

### Results
- [Output 1]
- [Output 2]

### Errors (if any)
- [Error description and impact]

### Next Steps
[What to do next]
</output-format>
```

## Multishot Prompting Guide

Including diverse examples within your system prompt significantly improves agent behavior consistency.

### Why Multishot Examples Matter

- **Pattern Recognition**: Agent learns from concrete examples
- **Edge Case Handling**: Examples show how to handle unusual inputs
- **Output Consistency**: Examples demonstrate desired format
- **Disambiguation**: Examples clarify vague instructions

### Example Structure

Each example should have three components:

```xml
<example>
<input>[The scenario or user request]</input>
<output>[What agent should produce]</output>
<commentary>[Why this output is correct for this input]</commentary>
</example>
```

### Coverage Strategy

Include 3-5 examples covering:

1. **Simple/Typical Case**: Most common scenario
2. **Complex Case**: More challenging variation
3. **Edge Case**: Unusual or boundary condition
4. **Error Case**: Invalid input or failure scenario
5. **Ambiguous Case**: When clarification needed

### Antipatterns to Avoid

❌ **Only one example**: Not enough pattern variation
❌ **All similar examples**: Doesn't cover edge cases
❌ **Examples without commentary**: Misses learning opportunity
❌ **Too many examples (10+)**: Diminishing returns, bloats prompt

✅ **3-5 diverse examples with clear commentary**: Optimal

## Writing Style Guidelines

### Tone and Voice

**Use second person (addressing the agent):**
```
✅ You are responsible for...
✅ You will analyze...
✅ Your process should...

❌ The agent is responsible for...
❌ This agent will analyze...
❌ I will analyze...
```

### Clarity and Specificity

**Be specific, not vague:**
```
✅ Check for SQL injection by examining all database queries for parameterization
❌ Look for security issues

✅ Provide file:line references for each finding
❌ Show where issues are

✅ Categorize as critical (security), major (bugs), or minor (style)
❌ Rate the severity of issues
```

### Actionable Instructions

**Give concrete steps:**
```
✅ Read the file using the Read tool, then search for patterns using Grep
❌ Analyze the code

✅ Generate test file at test/path/to/file.test.ts
❌ Create tests
```

## Common Pitfalls

### ❌ Vague Responsibilities

```markdown
<instructions>
1. Help the user with their code
2. Provide assistance
3. Be helpful
</instructions>
```

**Why bad:** Not specific enough to guide behavior.

### ✅ Specific Responsibilities

```markdown
<instructions>
## Core Responsibilities
1. Analyze TypeScript code for type safety issues
2. Identify missing type annotations and improper 'any' usage
3. Recommend specific type improvements with examples
</instructions>
```

### ❌ Missing Process Steps

```markdown
<instructions>
Analyze the code and provide feedback.
</instructions>
```

**Why bad:** Agent doesn't know HOW to analyze.

### ✅ Clear Process

```markdown
<instructions>
## Analysis Process
1. Read code files using Read tool
2. Scan for type annotations on all functions
3. Check for 'any' type usage
4. Verify generic type parameters
5. List findings with file:line references
</instructions>
```

### ❌ Undefined Output

```markdown
<output-format>
Provide a report.
</output-format>
```

**Why bad:** Agent doesn't know what format to use.

### ✅ Defined Output Format

```markdown
<output-format>
## Type Safety Report

### Summary
[Overview of findings]

### Issues Found
- `file.ts:42` - Missing return type on `processData`
- `utils.ts:15` - Unsafe 'any' usage in parameter

### Recommendations
[Specific fixes with examples]
</output-format>
```

### ❌ Missing XML Structure

```markdown
You are an analyzer.

Responsibilities:
1. Check code
2. Report issues

Output: Summary and details
```

**Why bad:** No XML tags make it hard for model to parse structure.

### ✅ XML-Structured Prompt

```markdown
You are an analyzer.

<instructions>
## Core Responsibilities
1. Check code for issues
2. Report findings with severity
</instructions>

<output-format>
Summary and detailed issue list
</output-format>
```

## Length Guidelines

### Minimum Viable Agent

**~500 words minimum:**
- Role description with specificity
- `<context>` section
- `<instructions>` with 3 core responsibilities and 5-step process
- `<examples>` with 2-3 examples
- `<constraints>` with key boundaries
- `<output-format>` specification

### Standard Agent

**~1,000-2,000 words:**
- Detailed role with domain expertise
- Comprehensive `<context>` with project background
- `<instructions>` with 5-8 responsibilities and 8-12 process steps
- `<examples>` with 3-5 diverse cases
- `<constraints>` with edge cases
- Detailed `<output-format>`

### Comprehensive Agent

**~2,000-5,000 words:**
- Complete role with extensive background
- Rich `<context>` with references
- Comprehensive `<instructions>` with multi-phase process
- `<examples>` with 5+ diverse scenarios
- Extensive `<constraints>` and edge cases
- Multiple `<output-format>` variations

**Avoid > 10,000 words:** Too long, diminishing returns, harder to maintain.

## Testing System Prompts

### Test Completeness

Can the agent handle these based on system prompt alone?

- [ ] Typical task execution
- [ ] Edge cases mentioned in `<examples>`
- [ ] Error scenarios
- [ ] Unclear requirements
- [ ] Large/complex inputs
- [ ] Empty/missing inputs

### Test Clarity

Read the system prompt and ask:

- Can another developer understand what this agent does?
- Are process steps in `<instructions>` clear and actionable?
- Is `<output-format>` unambiguous?
- Do `<examples>` cover diverse scenarios?
- Are `<constraints>` specific and testable?

### Iterate Based on Results

After testing agent:
1. Identify where it struggled
2. Add missing guidance to `<instructions>`
3. Add clarifying example to `<examples>`
4. Clarify ambiguous constraints in `<constraints>`
5. Update `<output-format>` if output inconsistent
6. Re-test

## Conclusion

Effective system prompts following Anthropic's principles are:
- **Structured with XML**: Use `<context>`, `<instructions>`, `<examples>`, `<constraints>`, `<output-format>` tags
- **Specific**: Include domain expertise, years of experience, specialization in persona
- **Example-Rich**: 3-5 diverse multishot examples demonstrating desired behavior
- **Complete**: Cover normal and edge cases
- **Actionable**: Provide concrete steps in process
- **Testable**: Define measurable standards

Use the patterns above as templates, customize for your domain, include multishot examples, and iterate based on agent performance.
