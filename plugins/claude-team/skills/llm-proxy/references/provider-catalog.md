# LLM Provider Catalog

Comprehensive reference for CLI-based LLM tools that can be integrated with the llm-proxy skill. This catalog documents installation, configuration, and usage patterns for each supported provider.

## Table of Contents

- [Codex CLI (Primary)](#codex-cli-primary)
- [Gemini CLI](#gemini-cli)
- [Generic Provider Template](#generic-provider-template)

---

## Codex CLI (Primary)

**Status**: Primary provider, fully tested
**Version Tested**: 0.101.0
**Provider**: OpenAI
**Use Cases**: Code generation, code review, refactoring, technical tasks

### Installation

**Option 1: NPM (Direct)**
```bash
npm install -g @anthropic/codex
```

**Option 2: Via ai-cli-tools plugin**
```bash
cc ai-cli-tools:setup codex
```

**Verify Installation**
```bash
codex --version
# Expected: 0.101.0 or higher
```

### Configuration

**Required Environment Variables**
```bash
export OPENAI_API_KEY="sk-..."
```

**Optional Configuration**
```bash
# Set default model
export CODEX_DEFAULT_MODEL="o4-mini"

# Enable debug logging
export CODEX_DEBUG=1
```

### CLI Syntax

**Basic Command Structure**
```bash
codex [flags] "prompt"
```

**Essential Flags**

| Flag | Purpose | Required | Default |
|------|---------|----------|---------|
| `--dangerously-bypass-approvals-and-sandbox` | Non-interactive mode | Yes (for automation) | false |
| `--model <name>` | Specify model | No | o4-mini |
| `--temperature <float>` | Control randomness (0.0-2.0) | No | 1.0 |
| `--max-tokens <int>` | Maximum response length | No | 2048 |
| `--no-stream` | Disable streaming output | No | false |

### Available Models

| Model ID | Use Case | Speed | Cost | Context Window |
|----------|----------|-------|------|----------------|
| `o4-mini` | General code tasks | Fast | Low | 128k tokens |
| `o3` | Complex reasoning | Slow | High | 200k tokens |
| `o1-preview` | Advanced code generation | Medium | Medium | 128k tokens |
| `gpt-4-turbo` | High quality output | Medium | Medium | 128k tokens |
| `gpt-3.5-turbo` | Quick tasks | Very Fast | Very Low | 16k tokens |

**Model Selection Guide:**
- **Quick fixes, simple tasks**: `o4-mini` or `gpt-3.5-turbo`
- **Code review, refactoring**: `o4-mini` or `gpt-4-turbo`
- **Complex algorithms, reasoning**: `o3` or `gpt-4-turbo`
- **Cost-sensitive projects**: `gpt-3.5-turbo`

### Examples

#### Example 1: Simple Code Translation
```bash
codex --dangerously-bypass-approvals-and-sandbox \
  "Translate this Python code to TypeScript: def add(a, b): return a + b"
```

**Expected Output:**
```typescript
function add(a: number, b: number): number {
  return a + b;
}
```

#### Example 2: Code Review with File Context
```bash
# Review a specific file
codex --dangerously-bypass-approvals-and-sandbox \
  --model o4-mini \
  "Review this code for security issues: $(cat auth.js)"
```

#### Example 3: Complex Prompt with Model Selection
```bash
codex --dangerously-bypass-approvals-and-sandbox \
  --model o3 \
  --temperature 0.3 \
  "Design a distributed rate limiting algorithm with the following requirements:
- Support multiple nodes
- Redis as backing store
- Sliding window counter
- Include TypeScript implementation"
```

#### Example 4: Refactoring Legacy Code
```bash
codex --dangerously-bypass-approvals-and-sandbox \
  --model gpt-4-turbo \
  "Refactor this callback hell to async/await:
$(cat legacy-code.js)"
```

#### Example 5: Piped Input from File
```bash
cat requirements.txt | codex --dangerously-bypass-approvals-and-sandbox \
  "Generate a Docker multistage build that installs these dependencies"
```

### Known Quirks & Limitations

**1. Non-Interactive Requirement**
- ⚠️ **Must** use `--dangerously-bypass-approvals-and-sandbox` for automation
- Without this flag, codex will prompt for user approval
- This is by design for safety but breaks non-interactive workflows

**2. Exit Codes**
- Success: `0`
- API error: `1`
- Invalid arguments: `2`
- No API key: `3`

**3. Output Format**
- Streams to stdout by default
- Use `--no-stream` for buffered output (better for parsing)
- Errors go to stderr

**4. Rate Limiting**
- OpenAI API has rate limits per tier
- Codex CLI does **not** implement automatic retry
- Must handle rate limit errors (HTTP 429) externally

**5. Token Counting**
- Prompts over context window will fail
- Use `--max-tokens` to control response length
- No built-in prompt truncation

**6. Environment Variable Priority**
```
1. --model flag (highest)
2. CODEX_DEFAULT_MODEL
3. Hardcoded default (o4-mini)
```

### Error Handling

**Common Errors and Solutions**

| Error | Cause | Solution |
|-------|-------|----------|
| `Error: OPENAI_API_KEY not set` | Missing API key | Set `OPENAI_API_KEY` env var |
| `Error: Model not found` | Invalid model ID | Check available models list |
| `Error: Rate limit exceeded` | Too many requests | Implement exponential backoff |
| `Error: Context length exceeded` | Prompt too long | Reduce prompt size or use model with larger context |

**Retry Pattern (Bash)**
```bash
retry_codex() {
  local max_attempts=3
  local attempt=1
  local delay=2

  while [ $attempt -le $max_attempts ]; do
    if codex --dangerously-bypass-approvals-and-sandbox "$1"; then
      return 0
    fi

    echo "Attempt $attempt failed, retrying in ${delay}s..." >&2
    sleep $delay
    delay=$((delay * 2))
    attempt=$((attempt + 1))
  done

  return 1
}

retry_codex "Generate unit tests for auth.js"
```

---

## Gemini CLI

**Status**: Tested
**Provider**: Google
**Use Cases**: Research tasks, summarization, multimodal input, long context

### Installation

**Option 1: NPM (Direct)**
```bash
npm install -g @anthropic/gemini
```

**Option 2: Via ai-cli-tools plugin**
```bash
cc ai-cli-tools:setup gemini
```

**Verify Installation**
```bash
gemini --version
```

### Configuration

**Required Environment Variables**
```bash
export GEMINI_API_KEY="AIza..."
```

**Optional Configuration**
```bash
# Set default model
export GEMINI_DEFAULT_MODEL="gemini-2.0-flash-exp"
```

### CLI Syntax

**Basic Command Structure**
```bash
gemini [flags] -p "prompt"
```

**Essential Flags**

| Flag | Purpose | Required | Default |
|------|---------|----------|---------|
| `-p, --prompt <text>` | Prompt text | Yes | - |
| `-m, --model <name>` | Model selection | No | gemini-2.0-flash-exp |
| `-t, --temperature <float>` | Control randomness (0.0-2.0) | No | 1.0 |
| `--max-output-tokens <int>` | Maximum response length | No | 8192 |
| `--json` | Output in JSON format | No | false |
| `-f, --file <path>` | Include file in prompt | No | - |

### Available Models

| Model ID | Use Case | Speed | Cost | Context Window |
|----------|----------|-------|------|----------------|
| `gemini-2.0-flash-exp` | Fast general tasks | Very Fast | Low | 1M tokens |
| `gemini-1.5-pro` | High quality reasoning | Medium | Medium | 2M tokens |
| `gemini-1.5-flash` | Balanced performance | Fast | Low | 1M tokens |

**Model Selection Guide:**
- **Large documents, summarization**: `gemini-1.5-pro` (2M context)
- **Quick research tasks**: `gemini-2.0-flash-exp`
- **Cost-sensitive**: `gemini-1.5-flash`

### Examples

#### Example 1: Simple Research Query
```bash
gemini -p "What are the key differences between REST and GraphQL APIs?"
```

#### Example 2: Summarize Large Document
```bash
gemini -m gemini-1.5-pro \
  -p "Summarize this document in 3 key points: $(cat large-doc.txt)"
```

#### Example 3: Code Analysis with File Input
```bash
gemini -f src/api.js \
  -p "Analyze this API implementation for performance bottlenecks"
```

#### Example 4: JSON Output for Parsing
```bash
gemini --json \
  -p "List the top 5 programming languages in 2025 with popularity scores" \
  | jq '.candidates[0].content.parts[0].text'
```

#### Example 5: Multi-File Context
```bash
gemini -p "Compare these two implementations:
Implementation A: $(cat impl-a.js)
Implementation B: $(cat impl-b.js)

Which is more efficient and why?"
```

### Known Quirks & Limitations

**1. Non-Interactive by Default**
- ✅ No special flags needed for automation
- Always runs in non-interactive mode

**2. Output Format**
- Default: Plain text to stdout
- `--json`: Structured JSON response
- JSON structure:
  ```json
  {
    "candidates": [{
      "content": {
        "parts": [{"text": "response here"}]
      }
    }]
  }
  ```

**3. File Handling**
- `-f` flag can be used multiple times
- Supports text and image files
- Images: JPG, PNG, WEBP, HEIC, HEIF

**4. Context Window Advantages**
- Gemini 1.5 Pro: 2M tokens (largest available)
- Can process entire codebases or large documents
- No need for chunking strategies

**5. Rate Limiting**
- Google API has generous free tier
- Rate limits: 60 requests/minute (free tier)
- Automatic retry not implemented

### Error Handling

**Common Errors**

| Error | Cause | Solution |
|-------|-------|----------|
| `Error: API key not set` | Missing `GEMINI_API_KEY` | Set environment variable |
| `Error: File not found` | Invalid `-f` path | Check file path |
| `Error: Quota exceeded` | Rate limit hit | Wait or upgrade tier |
| `Error: Invalid model` | Unknown model ID | Use valid model from list |

---

## Generic Provider Template

Use this template to add any new CLI-based LLM provider.

### Prerequisites

For a CLI tool to be compatible with llm-proxy:
- ✅ **Non-interactive mode** (flag or stdin)
- ✅ **Prompt as argument or stdin** (not interactive only)
- ✅ **Stdout output** (not just to file)
- ✅ **Deterministic exit codes** (0 = success)

### Template Structure

```markdown
## Provider Name

**Status**: [Tested/Untested]
**Provider**: [Company/Project]
**Use Cases**: [Primary use cases]

### Installation

[Installation commands]

### Configuration

**Required Environment Variables**
```bash
export PROVIDER_API_KEY="..."
```

### CLI Syntax

**Basic Command Structure**
```bash
provider-cli [flags] "prompt"
```

**Essential Flags**

| Flag | Purpose | Required | Default |
|------|---------|----------|---------|
| `--flag` | Description | Yes/No | value |

### Available Models

| Model ID | Use Case | Speed | Cost |
|----------|----------|-------|------|
| `model-1` | Description | Fast | Low |

### Examples

#### Example 1: Basic Usage
```bash
provider-cli --flag "prompt"
```

### Known Quirks & Limitations

1. **Quirk 1**: Description
2. **Quirk 2**: Description

### Error Handling

**Common Errors**

| Error | Cause | Solution |
|-------|-------|----------|
| `Error message` | Reason | Fix |
```

### Adding a New Provider

**Step 1: Test CLI Manually**
```bash
# Verify non-interactive mode works
new-provider --non-interactive "test prompt"

# Check output goes to stdout
new-provider "test" | cat

# Verify exit codes
new-provider "test" && echo "Success"
```

**Step 2: Document Installation**
- Include all package managers (npm, pip, brew, etc.)
- Document system requirements
- List required environment variables

**Step 3: List All Relevant Flags**
- Focus on flags needed for automation
- Document model selection
- Include output format options

**Step 4: Create Working Examples**
- Start with simplest possible example
- Add complex examples with file input
- Show error handling patterns

**Step 5: Document Quirks**
- Non-obvious behavior
- Rate limiting specifics
- Output format oddities
- Known bugs or limitations

### Integration Checklist

- [ ] Non-interactive mode verified
- [ ] API key/auth documented
- [ ] Model options listed
- [ ] Basic example works
- [ ] Complex example works
- [ ] Error handling documented
- [ ] Performance characteristics noted
- [ ] Known limitations listed

---

## Comparison Matrix

Quick comparison to help choose the right provider:

| Feature | Codex | Gemini |
|---------|-------|--------|
| **Cost** | $$ (per token) | $ (per token) |
| **Speed** | Fast | Fast |
| **Context Window** | 128k-200k | 1M-2M |
| **Code Quality** | Excellent | Good |
| **Setup Complexity** | Low | Low |
| **Best For** | Code tasks | Research, long docs |

## Best Practices

### 1. Provider Selection
```bash
# Choose based on task requirements
task_type="code-review"

case $task_type in
  "code-review"|"refactor")
    provider="codex"
    model="o4-mini"
    ;;
  "research"|"summarize")
    provider="gemini"
    model="gemini-1.5-pro"
    ;;
esac
```

### 2. Error Handling Pattern
```bash
# Universal retry wrapper
llm_call() {
  local provider=$1
  local prompt=$2
  local max_retries=3
  local attempt=1

  while [ $attempt -le $max_retries ]; do
    case $provider in
      "codex")
        if codex --dangerously-bypass-approvals-and-sandbox "$prompt"; then
          return 0
        fi
        ;;
      "gemini")
        if gemini -p "$prompt"; then
          return 0
        fi
        ;;
    esac

    attempt=$((attempt + 1))
    sleep $((2 ** attempt))
  done

  return 1
}
```

### 3. Prompt Engineering for Consistency
```bash
# Standardize prompts across providers
generate_code_prompt() {
  local task=$1
  local context=$2

  cat <<EOF
You are a code generation assistant.

Task: $task

Context:
$context

Instructions:
1. Provide only the code implementation
2. Include inline comments
3. Use best practices for the language

Output format: Raw code only, no explanations.
EOF
}

# Use with any provider
prompt=$(generate_code_prompt "Create auth middleware" "$(cat requirements.txt)")
codex --dangerously-bypass-approvals-and-sandbox "$prompt"
```

### 4. Cost Optimization
```bash
# Route simple tasks to cheaper models
route_by_complexity() {
  local prompt=$1
  local prompt_length=${#prompt}

  if [ $prompt_length -lt 100 ]; then
    # Simple task -> cheap model
    codex --model gpt-3.5-turbo "$prompt"
  elif [ $prompt_length -lt 1000 ]; then
    # Medium task -> balanced model
    codex --model o4-mini "$prompt"
  else
    # Complex task -> powerful model
    codex --model o3 "$prompt"
  fi
}
```

---

## Troubleshooting

### General Debugging Steps

1. **Verify Provider Installation**
```bash
which codex gemini
```

2. **Check Environment Variables**
```bash
env | grep -E 'OPENAI|GEMINI'
```

3. **Test with Minimal Example**
```bash
codex --dangerously-bypass-approvals-and-sandbox "echo hello"
gemini -p "echo hello"
```

4. **Enable Debug Logging**
```bash
# Codex
CODEX_DEBUG=1 codex --dangerously-bypass-approvals-and-sandbox "test"

# Most CLIs support
--verbose or --debug flags
```

### Common Issues

**Issue: "Command not found"**
- Solution: Reinstall provider, check `$PATH`

**Issue: "API key invalid"**
- Solution: Verify key format, check expiration, regenerate key

**Issue: "Rate limit exceeded"**
- Solution: Implement exponential backoff, upgrade API tier

**Issue: "Response truncated"**
- Solution: Increase `--max-tokens` or `--max-output-tokens`

---

## See Also

### Related Documentation
- **`llm-proxy/SKILL.md`** - Main skill documentation
- **`llm-proxy/examples/`** - Working integration examples
- **`ai-cli-tools` plugin** - CLI tool installation helpers

### External Resources
- [OpenAI API Documentation](https://platform.openai.com/docs)
- [Google AI Studio](https://makersuite.google.com/)
