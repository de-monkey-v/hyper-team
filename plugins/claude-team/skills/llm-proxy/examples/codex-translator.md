# Codex CLI Translation Backend Example

This is a **proven, tested example** of using Codex CLI as a translation backend via a Haiku proxy teammate.

## Overview

This workflow demonstrates how to create a lightweight translation service using:
- **Haiku** as the proxy agent (low-cost, fast)
- **Codex CLI** as the backend LLM (OpenAI GPT models)
- **Agent Teams API** for orchestration

**Use case**: Korean → English translation with Codex's language quality, Haiku's coordination cost.

## Prerequisites

### Required Tools
```bash
# 1. Codex CLI v0.101.0 or later
which codex
codex --version

# 2. OpenAI API key configured
echo $OPENAI_API_KEY
```

### Installation
```bash
# Install Codex CLI if not present
npm install -g codex-cli

# Or via pip
pip install codex-cli

# Set API key
export OPENAI_API_KEY="sk-..."
```

## Complete Workflow

### Step 1: Create Translation Team

Create a dedicated team for translation tasks:

```
TeamCreate(
  team_name: "codex-translator",
  description: "Codex CLI 백엔드 한→영 번역팀"
)
```

**Response**: Team created with ID.

### Step 2: Spawn Haiku Proxy Teammate

Create the translator proxy agent with full system prompt:

```
Task(
  team_name: "codex-translator",
  name: "en-translator",
  model: "haiku",
  subagent_type: "general-purpose",
  run_in_background: true,
  prompt: "You are a translation proxy agent. Your ONLY job is to translate Korean text to English using the Codex CLI.

WORKFLOW:
1. When you receive Korean text, run this Bash command:
   codex --dangerously-bypass-approvals-and-sandbox \"Translate the following Korean text to natural, fluent English. Return ONLY the English translation:\n\n{korean_text}\"
2. Send the Codex output back via SendMessage to whoever sent you the message.
3. If codex fails, send error message back. Do NOT translate yourself.

RULES:
- ALWAYS use codex command. NEVER translate yourself.
- No commentary, no formatting, just the translation.
- Approve shutdown_request immediately."
)
```

**Key points:**
- `run_in_background: true` - Agent stays alive for multiple requests
- `--dangerously-bypass-approvals-and-sandbox` - Auto-approve Codex commands
- Haiku follows instructions precisely, won't hallucinate translations

**Response**: Teammate spawned and running in background.

### Step 3: Send Translation Request

Send Korean text for translation:

```
SendMessage(
  type: "message",
  recipient: "en-translator",
  content: "안녕하세요, 오늘 날씨가 좋네요.",
  summary: "번역 테스트"
)
```

**Expected flow:**
1. Haiku receives message
2. Haiku runs: `codex --dangerously-bypass-approvals-and-sandbox "Translate the following Korean text to natural, fluent English. Return ONLY the English translation:\n\n안녕하세요, 오늘 날씨가 좋네요."`
3. Codex (OpenAI) translates
4. Haiku sends back via SendMessage

**Expected response:**
```
"Hello, the weather is nice today."
```

### Step 4: Additional Translations (Optional)

Send more requests to the same agent:

```
SendMessage(
  type: "message",
  recipient: "en-translator",
  content: "이 문서를 검토해 주시겠어요?",
  summary: "번역 요청 2"
)
```

**Response:**
```
"Could you please review this document?"
```

### Step 5: Shutdown

Clean up resources:

```
# 1. Stop the translator agent
SendMessage(
  type: "shutdown_request",
  recipient: "en-translator"
)

# 2. Delete the team
TeamDelete(team_name: "codex-translator")
```

**Response**: Agent stopped, team deleted.

## Performance Characteristics

### Latency
- **First message**: ~10-15 seconds (agent startup + Codex round-trip)
- **Subsequent messages**: ~5-10 seconds (Codex round-trip only)
- **Idle timeout**: Agent may need re-initialization if inactive >5 minutes

### Cost
- **Haiku proxy**: ~250 tokens per request
  - System prompt: ~150 tokens
  - Message coordination: ~100 tokens
- **Codex/OpenAI**: Variable by model (GPT-4, GPT-3.5, etc.)
  - Translation task: ~100-200 tokens per request

**Total cost example** (GPT-4):
- Haiku: $0.0001/request
- GPT-4: $0.003/request
- **Combined**: ~$0.0031/request

### Reliability
- **First message retry**: Sometimes needed due to idle timing
- **Error handling**: Codex CLI failures propagate back via SendMessage
- **Robustness**: Haiku strictly follows "use Codex only" rule (no hallucinated translations)

## Troubleshooting

### Issue: "codex: command not found"
```bash
# Verify installation
which codex

# Add to PATH if needed
export PATH="$PATH:/path/to/codex"
```

### Issue: "OPENAI_API_KEY not set"
```bash
# Set in environment
export OPENAI_API_KEY="sk-..."

# Or in ~/.bashrc / ~/.zshrc
echo 'export OPENAI_API_KEY="sk-..."' >> ~/.zshrc
```

### Issue: First message gets no response
**Solution**: Wait 30 seconds, then re-send the message. Background agents may need time to initialize.

### Issue: Agent translates without using Codex
**Solution**: System prompt is too weak. Ensure the prompt explicitly says:
```
ALWAYS use codex command. NEVER translate yourself.
```

## Variations

### Multi-Language Support

Create specialized translators:

```
# Korean → English
Task(name: "ko-en-translator", prompt: "... translate Korean to English ...")

# Japanese → English
Task(name: "ja-en-translator", prompt: "... translate Japanese to English ...")

# English → Korean
Task(name: "en-ko-translator", prompt: "... translate English to Korean ...")
```

### Use Different Codex Models

Modify the Bash command in the system prompt:

```bash
# GPT-4 (high quality)
codex --model gpt-4 --dangerously-bypass-approvals-and-sandbox "..."

# GPT-3.5 (faster, cheaper)
codex --model gpt-3.5-turbo --dangerously-bypass-approvals-and-sandbox "..."
```

### Batch Translation

Send multiple texts in one message:

```
SendMessage(
  content: "1. 안녕하세요\n2. 감사합니다\n3. 죄송합니다",
  summary: "배치 번역"
)
```

Adjust system prompt to handle numbered lists.

## Comparison: Direct Codex vs. Proxy Pattern

| Aspect | Direct Codex | Haiku Proxy + Codex |
|--------|-------------|---------------------|
| Cost per request | $0.003 | $0.0031 (+3%) |
| Latency | ~3s | ~5-10s (+2-7s) |
| Orchestration | Manual | Automated via Teams |
| Background availability | No | Yes (run_in_background) |
| Multi-agent coordination | Difficult | Natural (SendMessage) |

**When to use proxy pattern:**
- Need background service
- Coordinating multiple LLMs
- Automating multi-step workflows
- Acceptable latency trade-off

**When to use direct Codex:**
- One-off translations
- Latency-critical applications
- Simple scripts without orchestration

## Real-World Usage Example

```
User: "다음 한국어 문장들을 영어로 번역해줘:
1. 프로젝트 진행 상황을 공유드립니다.
2. 다음 주 월요일까지 완료 예정입니다.
3. 추가 질문 있으시면 연락 주세요."