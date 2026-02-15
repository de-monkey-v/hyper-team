---
name: llm-proxy
description: This skill should be used when the user asks about "external LLM teammate", "codex teammate", "gemini teammate", "proxy teammate", "multi-LLM team", "LLM routing", "LLM proxy", "codex proxy", "프록시 팀메이트", "외부 LLM", or wants to use non-Claude models within Agent Teams. Provides patterns for routing work to external CLI-based LLMs through thin proxy teammates.
version: 1.0.0
---

# Multi-LLM Proxy Teammate Framework

This skill provides comprehensive guidance on using external CLI-based LLMs (Codex, Gemini CLI) as backend engines for Agent Teams teammates through a thin proxy pattern.

## Overview

### What is an LLM Proxy Teammate?

An LLM proxy teammate is a lightweight Haiku-based agent that acts as a relay layer, forwarding user requests to external LLM CLI tools (like Codex or Gemini CLI) and returning their responses within the Agent Teams framework.

**Key concept**: The proxy teammate itself does minimal reasoning. It receives a message, constructs a CLI invocation, executes the external LLM, and sends back the result.

### Why Use External LLMs?

**1. Diversity of Perspective**
- Different models excel at different tasks
- Cross-model validation and code review
- Leverage specialized models (e.g., translation, creative writing)

**2. Cost Optimization**
- Route simple tasks to cheaper models
- Reserve Sonnet/Opus for complex reasoning
- Use cheaper models for simple tasks

**3. Specific Capabilities**
- Access to models with unique training (e.g., GPT-4 for code generation)
- Multilingual models optimized for specific languages
- Domain-specific fine-tuned models

### The Thin-Proxy Pattern

**Core principle**: Pure external LLM teammates are impossible because the Agent Teams protocol requires Claude CLI infrastructure. The solution is a minimal Haiku wrapper that acts as a protocol adapter.

```
Thin Proxy = Just enough Claude to bridge the protocol gap
```

**Benefits:**
- Minimal cost (Haiku is cheapest Claude model)
- Simple to implement and debug
- Clear separation of concerns
- Easy to add new external LLM providers

## Core Architecture

### The Proxy Pattern Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         User / Coordinator                      │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ SendMessage(team, proxy-member, prompt)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│              Haiku Proxy Teammate (Thin Wrapper)                │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 1. Receive message from inbox                           │   │
│  │ 2. Extract user prompt                                  │   │
│  │ 3. Construct CLI invocation (shell-escaped)             │   │
│  │ 4. Execute: bash -c "external-llm-cli 'prompt'"         │   │
│  │ 5. Capture stdout/stderr                                │   │
│  │ 6. Parse and format result                              │   │
│  │ 7. SendMessage(team, coordinator, result)               │   │
│  └──────────────────────────────────────────────────────────┘   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Bash execution
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                    External LLM (CLI Tool)                      │
│  • Codex CLI: codex --dangerously-bypass-approvals...          │
│  • Gemini CLI: gemini -p "prompt"                              │
│  • Any CLI that accepts prompt and returns text                │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ LLM API call (HTTP/gRPC)
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                External LLM Service (GPT-4, Gemini, etc.)       │
└─────────────────────────────────────────────────────────────────┘
```

### Why Pure External LLM Teammates Are Impossible

The Agent Teams protocol requires:
- Reading from `~/.claude/teams/{team}/inboxes/{member}.json`
- Writing to other members' inboxes
- Understanding the message envelope format
- TeamCreate, SendMessage tool availability

**Problem**: External LLMs (GPT-4, Gemini, etc.) are accessed via HTTP APIs or CLI tools. They cannot:
- Access local filesystem directly
- Use Claude CLI's internal tools (SendMessage, TeamCreate, etc.)
- Participate in the tmux-based process management

**Solution**: The Haiku proxy wraps the external LLM, providing the necessary protocol adapter layer while keeping the wrapper logic minimal.

### The Haiku Proxy Solution

**Why Haiku?**
- Cheapest Claude model (~$0.25/MTok input, ~$1.25/MTok output as of 2025)
- Fast response time for simple relay logic
- Sufficient for parsing messages and constructing CLI calls
- Full Agent Teams tool access

**Proxy responsibilities:**
1. Protocol compliance (inbox reading, SendMessage)
2. Message parsing and extraction
3. CLI invocation construction (shell escaping, quoting)
4. Timeout and error handling
5. Output formatting and delivery

**Not proxy responsibilities:**
- Complex reasoning (delegated to external LLM)
- Domain expertise (handled by external LLM)
- Multi-turn conversation state (stateless pattern)

## Provider Catalog

This section summarizes supported external LLM providers. For detailed provider documentation, API keys, installation, and advanced usage, see **`references/provider-catalog.md`**.

### Primary Providers (Tested)

**1. Codex CLI**
- **Command**: `codex --dangerously-bypass-approvals-and-sandbox "prompt"`
- **Environment**: `OPENAI_API_KEY`
- **Model**: GPT-4 (configurable)
- **Status**: Production-tested in multi-LLM routing scenarios
- **Use case**: Code generation, translation, general reasoning

**2. Gemini CLI**
- **Command**: `gemini -p "prompt"`
- **Environment**: `GEMINI_API_KEY`
- **Model**: Gemini Pro
- **Status**: Tested
- **Use case**: Research, analysis, multilingual tasks

### Generic Pattern for New Providers

To add a new external LLM provider:

```bash
# 1. Ensure CLI tool is installed and in PATH
which new-llm-cli

# 2. Test CLI invocation manually
new-llm-cli "Test prompt"

# 3. Identify required environment variables
export NEW_LLM_API_KEY="..."

# 4. Create proxy teammate with appropriate system prompt (see section 4)
```

For detailed provider specifications, see **`references/provider-catalog.md`**.

## Proxy Teammate Prompt Engineering

The system prompt for the Haiku proxy teammate is critical. It must be precise, stateless, and robust to edge cases.

### System Prompt Structure

```markdown
# {Provider} Proxy Teammate

You are a thin proxy relay for the {Provider} CLI tool. Your ONLY job is:

1. **Receive message** from your inbox
2. **Extract the user prompt** from the message content
3. **Construct CLI invocation** with proper shell escaping
4. **Execute** the external LLM CLI
5. **Capture output** (stdout/stderr)
6. **Format result** as markdown with proper attribution
7. **Send result** back to the coordinator via SendMessage

## CLI Invocation Template

Use this exact command structure:

```bash
{cli-command} "{escaped-prompt}"
```

## Shell Escaping Rules

- Escape double quotes: " → \"
- Escape backslashes: \ → \\
- Escape dollar signs: $ → \$
- For multiline prompts, use heredoc:

```bash
{cli-command} <<'EOF'
{multiline-prompt}
EOF
```

## Timeout Handling

- Set timeout to 60 seconds (configurable)
- If CLI hangs, terminate and report timeout error

## Output Formatting

Format the response as:

```markdown
## {Provider} Response

{cli-output}

---
*Generated by {Provider} via proxy teammate*
```

## Error Handling

If CLI execution fails:
1. Capture exit code and stderr
2. Format error message:

```markdown
## {Provider} Error

**Exit code**: {code}
**Error**: {stderr}

Please check:
- API key is set ({ENV_VAR})
- CLI is installed and in PATH
- Prompt does not contain invalid characters
```

## Important Constraints

- **Stateless**: Do not maintain conversation history
- **Single-turn**: One prompt → one response
- **No reasoning**: Do not interpret or modify the prompt
- **Relay only**: Pass through the request unchanged
```

### Message Reception Protocol

The proxy reads messages from:
```
~/.claude/teams/{team-name}/inboxes/{proxy-member-name}.json
```

**Message structure:**
```json
{
  "messages": [
    {
      "from": "coordinator@team-name",
      "to": "codex-proxy@team-name",
      "content": "Translate this Python code to Rust:\n\ndef fibonacci(n):\n    ...",
      "timestamp": "2026-02-16T10:30:00Z"
    }
  ]
}
```

**Extract the content field** and use it as the prompt for the external LLM.

### CLI Invocation Construction

#### Simple Single-Line Prompt

```bash
codex --dangerously-bypass-approvals-and-sandbox "What is the capital of France?"
```

#### Multi-Line Prompt (Heredoc Pattern)

```bash
codex --dangerously-bypass-approvals-and-sandbox <<'EOF'
Translate this Python code to Rust:

def fibonacci(n):
    if n <= 1:
        return n
    return fibonacci(n-1) + fibonacci(n-2)

Provide idiomatic Rust with error handling.
EOF
```

**Why heredoc?**
- Avoids complex shell escaping
- Preserves newlines and formatting
- More readable for debugging
- Prevents quote-related errors

#### Shell Escaping Rules

When using quoted strings (not heredoc):

| Character | Escape As | Example |
|-----------|-----------|---------|
| `"` | `\"` | `"He said \"hello\""` |
| `\` | `\\` | `"Path: C:\\Users"` |
| `$` | `\$` | `"Price: \$100"` |
| `` ` `` | `` \` `` | ``"Command: \`ls\`"`` |
| `!` | `\!` (in `!!`) | `"Last command: \!\!"` |

**Recommendation**: Use heredoc for prompts >100 chars or containing newlines.

### Timeout Handling

Implement timeout to prevent hung CLI processes:

```bash
timeout 60s codex --dangerously-bypass-approvals-and-sandbox <<'EOF'
{prompt}
EOF
```

**Exit code handling:**
- `0` - Success
- `124` - Timeout (killed by timeout command)
- Other - CLI error

If timeout occurs, send error message:
```markdown
## Codex Timeout

The Codex CLI did not respond within 60 seconds.

Possible causes:
- Large prompt exceeding context window
- Network issues with OpenAI API
- Rate limiting

Please try:
- Simplifying the prompt
- Checking API key validity
- Retrying after a few minutes
```

### Output Parsing and Formatting

Capture both stdout and stderr:

```bash
output=$(codex ... 2>&1)
exit_code=$?
```

Format response with clear attribution:

```markdown
## Codex Response

{stdout content}

---
*Generated by OpenAI Codex (GPT-4) via proxy teammate*
*Model: gpt-4*
*Timestamp: 2026-02-16T10:30:45Z*
```

### Result Delivery via SendMessage

After capturing and formatting the output, send it back:

```
SendMessage(
  team_name: {team-name},
  to_member: {coordinator-name},
  message: {formatted-response}
)
```

**Important**: Always send a response, even on error. Silent failures break team workflows.

### Statelessness Considerations

**Proxy teammates are stateless** - they do not maintain conversation history.

**Implications:**
- Each message is independent
- No context from previous exchanges
- Coordinator must include all necessary context in each prompt

**Example - Bad (assumes state):**
```
Message 1: "Translate this code to Rust: def fib(n): ..."
Message 2: "Now optimize it"  # ❌ Proxy has no memory of "it"
```

**Example - Good (self-contained):**
```
Message 1: "Translate this code to Rust: def fib(n): ..."
Message 2: "Optimize this Rust code: fn fib(n: u32) -> u32 { ... }"  # ✅
```

## Use Case Patterns

### 1. Translation (Proven with Codex)

**Scenario**: Translate code between languages using GPT-4's superior code understanding.

**Team structure:**
- **Coordinator**: Opus (orchestrates workflow)
- **Codex Proxy**: Haiku + Codex CLI (translation engine)
- **Reviewer**: Sonnet (validates output)

**Workflow:**
```
1. User provides Python code to coordinator
2. Coordinator sends to codex-proxy: "Translate to Rust: {code}"
3. Codex proxy executes: codex "Translate to Rust: {code}"
4. Codex proxy returns Rust code
5. Coordinator sends to reviewer for validation
6. Reviewer provides feedback
7. Coordinator delivers final result to user
```

**Prompt template:**
```markdown
Translate this {source_lang} code to {target_lang}:

```{source_lang}
{code}
```

Requirements:
- Preserve functionality exactly
- Use idiomatic {target_lang} patterns
- Include error handling
- Add comments for complex logic
```

### 2. Cross-Model Code Review

**Scenario**: Get independent code review from multiple models.

**Team structure:**
- **Coordinator**: Sonnet
- **Claude Reviewer**: Opus (deep analysis)
- **GPT Reviewer**: Haiku + Codex (alternative perspective)
- **Gemini Reviewer**: Haiku + Gemini (third opinion)

**Workflow:**
```
1. Coordinator broadcasts code to all reviewers
2. Each reviewer analyzes independently
3. Coordinator aggregates feedback
4. Coordinator synthesizes consensus recommendations
```

**Benefits:**
- Catch issues one model might miss
- Validate consensus across model families
- Reduce model-specific bias

### 3. Research and Analysis

**Scenario**: Parallel research using different models' knowledge bases.

**Team structure:**
- **Coordinator**: Opus (synthesis)
- **Claude Research**: Sonnet (Anthropic knowledge)
- **GPT Research**: Haiku + Codex (OpenAI knowledge)
- **Gemini Research**: Haiku + Gemini (Google knowledge)

**Workflow:**
```
1. User asks research question
2. Coordinator formulates search queries
3. Broadcast to all research agents
4. Each agent provides findings from its knowledge base
5. Coordinator identifies consensus and contradictions
6. Deliver comprehensive report
```

### 4. Cost Optimization

**Scenario**: Route simple tasks to cheaper models, complex tasks to premium models.

**Decision tree:**
```
Task complexity analysis (by coordinator):

├─ Simple (formatting, basic queries)
│  └─> Gemini (cheap)
│
├─ Moderate (code review, translation)
│  └─> Codex/GPT-4 or Claude Haiku
│
└─ Complex (architecture design, deep reasoning)
   └─> Claude Opus or GPT-4 (no proxy)
```

**Cost comparison (approximate):**
- Gemini: ~$0.50/MTok
- Haiku: ~$0.25/MTok input, ~$1.25/MTok output
- GPT-4: ~$10/MTok input, ~$30/MTok output
- Opus: ~$15/MTok input, ~$75/MTok output

## Model Selection Guide

### When to Use Each Model

| Task Type | Recommended Model | Rationale |
|-----------|------------------|-----------|
| Code generation (Python, JS) | Codex/GPT-4 | Superior code completion training |
| Code generation (Rust, Go) | Claude Opus | Better understanding of systems languages |
| Translation (human languages) | Gemini | Multilingual optimization |
| Creative writing | Claude Opus | Nuanced, context-aware responses |
| Data formatting | Local (llama2) | Free, fast, simple task |
| Math/logic problems | Claude Opus | Strong reasoning |
| API documentation | Codex/GPT-4 | Better API knowledge base |
| Security review | Claude Opus | Careful, thorough analysis |
| Quick prototypes | Haiku or local | Speed and cost priority |

### Cost Considerations

**Per-request cost estimation:**

```python
# Example: 2K token input, 1K token output

# Haiku proxy (wrapper only, ~100 tokens)
haiku_cost = (0.1 * 0.25 + 0.05 * 1.25) / 1000 = $0.0000875

# Codex via proxy
codex_cost = (2 * 10 + 1 * 30) / 1000 = $0.05
total_cost = haiku_cost + codex_cost ≈ $0.05

# Pure Claude Opus
opus_cost = (2 * 15 + 1 * 75) / 1000 = $0.105

# Savings: 52% by using Codex proxy for this task
```

**When cost savings matter:**
- High-volume batch processing
- Non-critical tasks (acceptable quality tradeoff)

### Latency Implications

**Latency components:**
1. Proxy processing: ~0.5-1s (Haiku wrapper)
2. External LLM API call: 2-30s (depends on model and load)
3. SendMessage delivery: <0.1s

**Total latency:**
- Gemini: 3-8s
- GPT-4: 5-15s
- Claude Opus (direct): 3-10s
- Claude Opus (no proxy): 3-10s

**Latency vs quality tradeoff:**
- Use Gemini for faster responses when acceptable
- Use Codex/GPT-4 when quality matters more
- Consider async patterns for high-latency external LLMs

### Why Haiku Is Ideal for the Wrapper

**Characteristics needed for proxy:**
1. ✅ Low cost (frequent invocations)
2. ✅ Fast response (minimal relay delay)
3. ✅ Tool access (SendMessage, bash execution)
4. ✅ Reliable parsing (structured task)
5. ❌ Not needed: Deep reasoning, creativity

**Haiku fits perfectly:**
- Cheapest Claude model
- Fastest response time
- Full tool ecosystem
- Excellent at structured tasks (parsing, formatting)

**Why not Sonnet/Opus for proxy?**
- Unnecessary cost (3-10x more expensive)
- Overkill for simple relay logic
- Slower response time
- No quality benefit for this use case

## Error Handling

### CLI Not Found / API Key Missing

**Detection:**
```bash
which codex || echo "CLI not found"
echo $OPENAI_API_KEY || echo "API key not set"
```

**Error message template:**
```markdown
## {Provider} Configuration Error

**Issue**: {CLI tool} not found in PATH

**Required setup:**
1. Install {CLI tool}: {installation-command}
2. Verify installation: `which {cli-tool}`
3. Set API key: `export {ENV_VAR}="..."`
4. Test: `{cli-tool} "test prompt"`

**Current environment:**
- PATH: {$PATH}
- {ENV_VAR}: {set/unset}
```

### CLI Timeout

**Scenario**: External LLM takes >60s to respond.

**Handling:**
```bash
timeout 60s codex "prompt" || {
  exit_code=$?
  if [ $exit_code -eq 124 ]; then
    echo "Timeout after 60s"
  fi
}
```

**User-facing message:**
```markdown
## Request Timeout

The {Provider} CLI did not respond within 60 seconds.

**Possible causes:**
- Prompt too long (exceeds context window)
- API rate limiting
- Network connectivity issues
- Service outage

**Recommended actions:**
1. Check {Provider} status page
2. Simplify the prompt
3. Retry after 1-2 minutes
4. Contact {Provider} support if persistent
```

### Output Truncation

**Scenario**: External LLM response exceeds reasonable size.

**Detection:**
```bash
output=$(codex "prompt")
length=${#output}
if [ $length -gt 50000 ]; then
  echo "Warning: Large response ($length chars)"
fi
```

**Handling:**
- Warn user about large response
- Optionally truncate with summary
- Suggest breaking prompt into smaller chunks

### Non-Zero Exit Codes

**Common exit codes:**
- `1` - General error
- `2` - Misuse of shell command
- `126` - Command not executable
- `127` - Command not found
- `130` - Terminated by Ctrl+C
- `124` - Timeout

**Categorized handling:**
```bash
case $exit_code in
  0)   # Success
    ;;
  124) # Timeout
    report_timeout
    ;;
  127) # Command not found
    report_missing_cli
    ;;
  *)   # Other error
    report_generic_error $exit_code "$stderr"
    ;;
esac
```

### Fallback Strategies

**1. Retry with exponential backoff:**
```bash
max_retries=3
retry_count=0
while [ $retry_count -lt $max_retries ]; do
  output=$(codex "prompt" 2>&1)
  exit_code=$?
  if [ $exit_code -eq 0 ]; then
    break
  fi
  sleep $((2 ** retry_count))
  ((retry_count++))
done
```

**2. Fallback to alternative model:**
```
If Codex fails → Try Gemini
If all fail → Report error to coordinator
```

**3. Graceful degradation:**
```markdown
## Partial Response

{Provider} encountered an error but provided partial output:

{partial-output}

**Error details:**
{error-message}

Please review the partial result and re-submit if needed.
```

## Integration with Team Templates

### Team Composition Patterns

**1. Hybrid Team (Claude + External LLMs)**

```json
{
  "name": "translation-team",
  "description": "Multi-model translation team",
  "members": [
    {
      "name": "coordinator",
      "model": "opus",
      "role": "Orchestrate translation workflow, validate quality"
    },
    {
      "name": "codex-translator",
      "model": "haiku",
      "role": "Proxy for Codex CLI - handle code translation"
    },
    {
      "name": "gemini-reviewer",
      "model": "haiku",
      "role": "Proxy for Gemini CLI - provide alternative translation"
    },
    {
      "name": "quality-checker",
      "model": "sonnet",
      "role": "Validate translation correctness and idiomatic usage"
    }
  ]
}
```

**2. Cost-Optimized Team**

```json
{
  "name": "research-team",
  "description": "Cost-optimized research team with routing",
  "members": [
    {
      "name": "coordinator",
      "model": "sonnet",
      "role": "Route queries by complexity: simple→gemini, complex→opus"
    },
    {
      "name": "deep-researcher",
      "model": "opus",
      "role": "Handle complex research requiring deep reasoning"
    }
  ]
}
```

**3. Cross-Model Validation Team**

```json
{
  "name": "code-review-team",
  "description": "Multi-model code review for comprehensive coverage",
  "members": [
    {
      "name": "coordinator",
      "model": "sonnet",
      "role": "Aggregate feedback from all reviewers"
    },
    {
      "name": "claude-reviewer",
      "model": "opus",
      "role": "Claude perspective on code quality"
    },
    {
      "name": "gpt-reviewer",
      "model": "haiku",
      "role": "Proxy for GPT-4 - OpenAI perspective"
    },
    {
      "name": "gemini-reviewer",
      "model": "haiku",
      "role": "Proxy for Gemini - Google perspective"
    }
  ]
}
```

### How team-architect Should Compose Teams

**Considerations when adding proxy teammates:**

1. **Identify external LLM use case**
   - What task requires non-Claude model?
   - Why is this model better suited?
   - What's the cost/latency tradeoff?

2. **Design coordinator logic**
   - Route messages to appropriate proxy
   - Handle proxy errors gracefully
   - Aggregate results from multiple proxies

3. **Configure proxy member**
   - Use Haiku for wrapper
   - Set appropriate timeout
   - Include provider-specific system prompt
   - Document required environment variables

4. **Plan error handling**
   - What if proxy fails?
   - Fallback to Claude?
   - Retry strategy?

5. **Document setup requirements**
   - List required CLI tools
   - Environment variables
   - Installation instructions

**Example team creation workflow:**

```markdown
User: "Create a team that uses GPT-4 for code generation and Claude for review"

team-architect:
1. TeamCreate(name: "hybrid-dev-team", description: "...")
2. Spawn coordinator (Sonnet)
3. Spawn codex-proxy (Haiku + Codex CLI proxy prompt)
4. Spawn claude-reviewer (Opus)
5. Document required setup:
   - Install Codex: npm install -g @openai/codex-cli
   - Set OPENAI_API_KEY
   - Test: codex "hello world"
```

### Mixed Teams: Claude-Native + Proxy Members

**Best practices:**

**✅ DO:**
- Use coordinator to route work appropriately
- Let Claude members handle complex reasoning
- Use proxy members for specialized tasks
- Aggregate results at coordinator level
- Handle proxy errors gracefully

**❌ DON'T:**
- Have proxy members communicate directly with each other
- Rely on proxy members for multi-turn conversations
- Use proxy members for tasks requiring tool access
- Assume proxy members have conversation state

**Example workflow:**

```
User: "Review this code and suggest optimizations"

1. Coordinator receives request
2. Coordinator → claude-reviewer: "Review this code for correctness"
3. Coordinator → gpt-proxy: "Review this code for optimization opportunities"
4. Both reviewers respond to coordinator
5. Coordinator synthesizes feedback
6. Coordinator → User: "Comprehensive review from Claude + GPT-4"
```

## Security and Best Practices

### --dangerously-bypass-approvals-and-sandbox Implications

**What this flag does:**
- Bypasses user approval for tool use
- Disables sandboxing for bash execution
- Allows automated, unattended execution

**Security risks:**
1. **Code execution without review** - External LLM output executed directly
2. **Prompt injection** - User input passed to external LLM could be malicious
3. **Data exfiltration** - External LLM sees all prompt content
4. **API abuse** - Malicious prompts could trigger expensive API calls

**Mitigation strategies:**

```bash
# 1. Input validation (before sending to proxy)
if echo "$prompt" | grep -E "(rm -rf|sudo|eval)" > /dev/null; then
  echo "Error: Potentially dangerous command in prompt"
  exit 1
fi

# 2. Output sanitization (after receiving from external LLM)
# Review external LLM response before execution

# 3. Rate limiting
# Track API calls, enforce daily/hourly limits

# 4. Audit logging
echo "$(date): Codex proxy called with prompt: $prompt" >> /var/log/llm-proxy.log
```

### API Key Management

**Best practices:**

**✅ DO:**
- Store API keys in environment variables
- Use `.env` files (not committed to git)
- Rotate keys regularly
- Use separate keys for dev/prod
- Monitor API usage for anomalies

**❌ DON'T:**
- Hard-code API keys in prompts or scripts
- Commit API keys to version control
- Share API keys across team members
- Use production keys for testing

**Secure setup:**

```bash
# .env file (add to .gitignore)
OPENAI_API_KEY="sk-..."
GEMINI_API_KEY="..."

# Load in shell
source .env

# Verify (should show key)
echo $OPENAI_API_KEY | head -c 10

# Proxy teammate reads from environment
# No keys in config.json or prompts
```

### Input Sanitization

**Threat model**: User provides malicious input that exploits:
1. Shell command injection
2. Prompt injection in external LLM
3. Resource exhaustion

**Sanitization strategies:**

**1. Shell command injection prevention:**
```bash
# BAD - vulnerable to injection
codex "$user_input"

# GOOD - use heredoc to avoid shell interpretation
codex <<'EOF'
$user_input
EOF
```

**2. Prompt injection detection:**
```python
dangerous_patterns = [
    r"ignore previous instructions",
    r"system prompt",
    r"you are now",
    r"disregard.*above",
]

for pattern in dangerous_patterns:
    if re.search(pattern, user_input, re.IGNORECASE):
        warn("Potential prompt injection detected")
```

**3. Size limits:**
```bash
max_prompt_size=50000  # characters
if [ ${#prompt} -gt $max_prompt_size ]; then
  echo "Error: Prompt exceeds size limit"
  exit 1
fi
```

### When NOT to Use Proxy Pattern

**Avoid proxy pattern when:**

1. **Task requires tool use** - External LLMs cannot use Agent Teams tools
   ```
   ❌ "Use the Grep tool to search for patterns"
   ✅ Have Claude member use tools, send results to external LLM for analysis
   ```

2. **Multi-turn conversation needed** - Proxy teammates are stateless
   ```
   ❌ User: "Review this code" → GPT: "Found issues" → User: "Fix them"
   ✅ Include all context in each message
   ```

3. **Real-time collaboration** - Latency of external API calls
   ```
   ❌ Interactive pair programming
   ✅ Batch code review
   ```

4. **Sensitive data** - External LLM APIs see all prompt content
   ```
   ❌ Private customer data, API keys, credentials
   ✅ Public code, general knowledge queries
   ```

5. **Cost-insensitive, quality-critical** - Claude Opus may be superior
   ```
   ❌ Production system architecture design
   ✅ Quick code snippet generation
   ```

## Limitations

### Stateless, No Streaming, Single-Turn

**Stateless:**
- Each message is independent
- No conversation history
- Coordinator must include all context in each prompt

**No streaming:**
- External LLMs typically return complete response
- Cannot stream partial results to user
- Latency until first token is full API call time

**Single-turn:**
- One prompt → one response
- No back-and-forth refinement
- Multi-turn conversations must be orchestrated by coordinator

**Workaround patterns:**

```markdown
# Multi-turn simulation (coordinator manages state)

User: "Translate this code to Rust"
Coordinator → Codex proxy: "Translate: {code}"
Codex proxy → Coordinator: {rust_code}

User: "Now optimize it"
Coordinator → Codex proxy: "Optimize this Rust code: {rust_code}"
# Coordinator included previous result in new prompt
```

### External LLMs Cannot Use Agent Teams Tools

**Tools unavailable to external LLMs:**
- SendMessage (must go through proxy)
- TeamCreate (not applicable)
- Grep, Read, Write (filesystem access)
- LSP (code intelligence)
- Custom plugin tools

**Impact:**
- External LLMs can only process text
- Cannot read files, search codebases, etc.
- Coordinator must provide all context in prompt

**Workaround:**
```markdown
# Instead of:
User → GPT: "Read main.py and suggest improvements"
# (GPT cannot use Read tool)

# Do this:
User → Coordinator: "Review main.py"
Coordinator: Read(main.py) → {content}
Coordinator → GPT proxy: "Review this code: {content}"
GPT proxy → Coordinator: {feedback}
Coordinator → User: {feedback}
```

### Context Window Limits

**Limits by model:**
- GPT-4: 8K, 32K, 128K (varies by version)
- Gemini Pro: 32K
- Claude: 100K (Haiku/Sonnet), 200K (Opus)

**Implications:**
- Large codebases may exceed external LLM limits
- Cannot pass entire project context
- Coordinator must chunk or summarize

**Mitigation:**
```markdown
# Chunking strategy
If file >30K tokens:
  1. Split into logical sections
  2. Send to external LLM separately
  3. Coordinator aggregates results

# Summarization strategy
If codebase too large:
  1. Claude Sonnet reads and summarizes
  2. Send summary to external LLM
  3. External LLM provides high-level feedback
```

### Performance Characteristics

**Latency:**
- Proxy overhead: ~0.5-1s (Haiku wrapper)
- External LLM API: 2-30s (depends on model)
- Total: 3-31s per request

**Cost:**
- Each proxy request incurs both Haiku and external LLM costs
- Haiku wrapper: ~$0.0001 per request (negligible)
- External LLM: varies ($0.001-$0.10+ per request)

**Reliability:**
- Depends on external API uptime
- Network connectivity required
- Rate limiting may apply

## See Also

### Reference Files
- **`references/provider-catalog.md`** - Detailed provider specifications, API keys, installation
- **`references/prompt-templates.md`** - Ready-to-use system prompts for common providers

### Example Files
- **`examples/codex-translator.md`** - Proven Korean→English translation via Codex proxy
- **`examples/codex-code-reviewer.md`** - Cross-model code review via Codex proxy

### Related Skills
- **`team-lifecycle`** - Creating and managing teams with proxy members
- **`team-templates`** - Team composition patterns
- **`team-monitoring`** - Debugging proxy teammate issues

### Related Agents
- **`team-architect`** - Designs teams with appropriate proxy members
