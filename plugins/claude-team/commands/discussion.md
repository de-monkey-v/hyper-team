---
description: Multi-LLM 토론 팀 생성. Gemini와 GPT teammate를 스폰하여 다양한 LLM 관점으로 주제를 토론합니다.
allowed-tools: Bash, Read, Write, AskUserQuestion, Task, TeamCreate, TeamDelete, SendMessage, TaskCreate, TaskUpdate, TaskList
namespace: team
argument-hint: [topic]
---

# Multi-LLM Discussion Workflow

Create a discussion team with Gemini and GPT teammates to analyze a topic from multiple AI perspectives. Gemini is spawned via haiku proxy (Task), GPT is spawned natively via tmux (gpt-claude-code). This command orchestrates team creation, teammate spawning, and facilitates multi-viewpoint discussion.

<task-context>
<plugin-path>./plugins/claude-team</plugin-path>
<component-name>discussion</component-name>
<mode>plugin</mode>
</task-context>

## Phase 1: Parse Topic

**Goal**: Determine the discussion topic.

**Actions**:

1. **Parse User Input**:

   Check if $ARGUMENTS is provided:
   - If $ARGUMENTS is not empty → Use it as the discussion topic
   - If $ARGUMENTS is empty → Use AskUserQuestion to gather topic

2. **Interactive Topic Gathering** (if $ARGUMENTS is empty):

   Use AskUserQuestion:

   **Question 1: Topic Category**
   - header: "Topic"
   - question: "어떤 주제로 토론하시겠습니까?"
   - options:
     - 기술 아키텍처 (Architecture decisions, trade-offs)
     - 코드 리뷰 (Code quality, patterns, best practices)
     - 기술 비교 (Compare technologies, frameworks, approaches)

   **Question 2: Specific Topic** (follow-up):
   - header: "Specific Topic"
   - question: "구체적인 토론 주제를 입력해주세요."
   - (free text input based on selected category)

   Combine answers into final topic:
   ```
   Category: [answer from Q1]
   Topic: [answer from Q2]
   ```

**Output**: Clear discussion topic string

---

## Phase 2: Create Discussion Team

**Goal**: Create team structure for discussion.

**Actions**:

1. **Create Team Structure**:

   Use TeamCreate tool:
   ```
   TeamCreate(
     team_name: "discussion",
     description: "Multi-LLM discussion: {topic}"
   )
   ```

   This creates:
   - `~/.claude/teams/discussion/config.json`
   - `~/.claude/teams/discussion/inboxes/`

2. **Verify Creation**:

   - Confirm team directory exists
   - Verify config.json is initialized

**Output**: Initialized discussion team structure

---

## Phase 2.5: GPT Prerequisites Check

**Goal**: Verify GPT native spawn prerequisites before proceeding.

**Actions**:

1. **Check tmux session availability**:

   Verify `$CLAUDE_CODE_TMUX_SESSION` environment variable exists:
   ```bash
   if [ -z "$CLAUDE_CODE_TMUX_SESSION" ]; then
     echo "ERROR: CLAUDE_CODE_TMUX_SESSION is not set"
     exit 1
   fi
   ```

2. **Check cli-proxy-api is running**:

   ```bash
   curl -s --connect-timeout 3 http://localhost:8317/
   ```

   If this fails, cli-proxy-api is not running.

3. **Check gpt-claude-code function exists**:

   ```bash
   zsh -c 'source ~/.zshrc && type gpt-claude-code'
   ```

   If this fails, the function is not defined.

4. **On any failure**:

   - Display which prerequisite(s) failed with specific guidance
   - Clean up team: `TeamDelete("discussion")`
   - Abort execution

**Output**: All GPT prerequisites verified

---

## Phase 3: Spawn Teammates

**Goal**: Spawn Gemini and GPT teammates in parallel.

**Actions**:

1. **Spawn Both Teammates IN PARALLEL**:

   **Gemini Teammate** (haiku proxy via Task):
   ```
   Task(
     subagent_type: "claude-team:gemini",
     team_name: "discussion",
     name: "gemini",
     model: "haiku",
     run_in_background: true,
     prompt: "You are a discussion teammate. Analyze the following topic and
             provide your perspective via Gemini CLI.

             Discussion topic: {topic}

             Please analyze and include:
             - Key points and insights
             - Pros and cons if applicable
             - Your recommendations
             - Any concerns or considerations"
   )
   ```

   **GPT Teammate** (native tmux spawn via Bash):

   Execute the following steps sequentially in a single Bash call:

   ```bash
   # 1. Create inbox file
   echo '[]' > ~/.claude/teams/discussion/inboxes/gpt.json

   # 2. Extract leadSessionId from config
   LEAD_SESSION_ID=$(jq -r '.leadSessionId' ~/.claude/teams/discussion/config.json)

   # 3. Spawn tmux pane
   PANE_ID=$(tmux split-window -t "$CLAUDE_CODE_TMUX_SESSION" -c "$PWD" -dP -F '#{pane_id}' \
     "zsh -c 'source ~/.zshrc && gpt-claude-code \
       --agent-id gpt@discussion \
       --agent-name gpt \
       --team-name discussion \
       --agent-color \"#10A37F\" \
       --parent-session-id ${LEAD_SESSION_ID} \
       --agent-type claude-team:gpt \
       --model sonnet \
       --dangerously-skip-permissions'")

   # 4. Register member in config.json
   jq --arg paneId "$PANE_ID" \
     '.members += [{
       "agentId": "gpt@discussion", "name": "gpt",
       "agentType": "claude-team:gpt", "model": "gpt-5.3-codex(high)",
       "color": "#10A37F", "tmuxPaneId": $paneId,
       "backendType": "tmux", "isActive": true,
       "joinedAt": (now * 1000 | floor), "cwd": env.PWD, "subscriptions": []
     }]' ~/.claude/teams/discussion/config.json > /tmp/config-tmp-gpt.json \
     && mv /tmp/config-tmp-gpt.json ~/.claude/teams/discussion/config.json
   ```

   GPT is spawned as a tmux pane, so `run_in_background: true` is NOT needed — tmux handles background execution.

   After spawning, send the discussion topic to GPT:
   ```
   SendMessage(
     type: "message",
     recipient: "gpt",
     content: "You are a discussion teammate. Analyze the following topic and
               provide your perspective.

               Discussion topic: {topic}

               Please analyze and include:
               - Key points and insights
               - Pros and cons if applicable
               - Your recommendations
               - Any concerns or considerations",
     summary: "토론 주제 전달"
   )
   ```

2. **Verify Spawns**:

   - Read `~/.claude/teams/discussion/config.json`
   - Verify both members are registered with `isActive: true`

**Output**: Two active teammates ready for discussion

---

## Phase 4: Wait for Responses

**Goal**: Collect perspectives from teammates.

**Actions**:

1. **Wait for Responses**:
   - Topic is already included in spawn prompts (Phase 3)
   - No separate broadcast needed
   - Expected response time: Gemini ~10-30s, GPT ~10-60s
   - Maximum wait: 3 minutes per teammate
   - If only one teammate responds within 3 minutes, present that
     response and note the other teammate timed out
   - Do NOT resend the topic — teammates already have it

**Output**: Collected responses from teammates

---

## Phase 5: Present Results

**Goal**: Display discussion summary with both perspectives.

**Actions**:

1. **Generate Discussion Summary**:

   Display in this format:
   ```markdown
   ## Discussion: {topic}

   ### 🔮 Gemini's Perspective

   {gemini response summary or full response}

   ---

   ### ⚡ GPT's Perspective

   {gpt response summary or full response}

   ---

   ### How to Continue

   The discussion team is active. You can:

   - **Continue discussion with both**:
     ```
     SendMessage(type: "broadcast", content: "Follow-up question...")
     ```

   - **Ask Gemini specifically**:
     ```
     SendMessage(recipient: "gemini", content: "Question for Gemini...")
     ```

   - **Ask GPT specifically**:
     ```
     SendMessage(recipient: "gpt", content: "Question for GPT...")
     ```

   - **End discussion**:
     ```
     /team:destroy discussion
     ```

   ### Team Status

   - **Team**: discussion
   - **Members**: gemini (haiku), gpt (gpt-5.3-codex)
   - **Config**: `~/.claude/teams/discussion/config.json`
   - **Inboxes**: `~/.claude/teams/discussion/inboxes/`
   ```

2. **Success Confirmation**:

   ```
   ✅ Discussion team created
   ✅ Gemini and GPT teammates spawned
   ✅ Responses collected
   ```

**Output**: Complete discussion summary with next steps guide

---

## Error Handling

### Gemini CLI Not Found

If Gemini CLI is not installed:
1. Show warning: "Gemini CLI not found. Please install it first."
2. Suggest: `/ai-cli-tools:setup` to install Gemini CLI
3. Abort discussion creation
4. Clean up team if partially created: `TeamDelete("discussion")`

### cli-proxy-api Not Running

If cli-proxy-api is not running (curl to localhost:8317 fails):
1. Show warning: "cli-proxy-api is not running. GPT teammate requires it."
2. Suggest: Start cli-proxy-api with `cli-proxy-api` or check if the process is running
3. Abort discussion creation
4. Clean up team if partially created: `TeamDelete("discussion")`

### gpt-claude-code Function Not Found

If `gpt-claude-code` function is not defined:
1. Show warning: "gpt-claude-code function not found."
2. Suggest: Check `~/.zshrc` for the function definition
3. Abort discussion creation
4. Clean up team if partially created: `TeamDelete("discussion")`

### tmux Session Not Available

If `$CLAUDE_CODE_TMUX_SESSION` is not set:
1. Show warning: "CLAUDE_CODE_TMUX_SESSION is not set. GPT teammate requires tmux."
2. Suggest: Run `/hyper-team:setup` to configure tmux session, or set the env variable manually
3. Abort discussion creation
4. Clean up team if partially created: `TeamDelete("discussion")`

### Team Already Exists

If discussion team already exists:
1. Use AskUserQuestion:
   - header: "Team exists"
   - question: "Discussion team already exists. What would you like to do?"
   - options:
     - Delete existing and create new
     - Use existing team
     - Cancel
2. Handle choice:
   - Delete existing: Use `TeamDelete("discussion")` then proceed
   - Use existing: Skip to Phase 4 (wait for responses)
   - Cancel: Stop execution

### Teammate Spawn Failure

If any teammate fails to spawn:
1. Note which teammate failed (gemini or gpt)
2. Show error with details
3. Ask user:
   - header: "Spawn failed"
   - question: "{teammate} failed to spawn. Continue with available teammate?"
   - options:
     - Yes (continue with one teammate)
     - No (abort and clean up)
4. If continuing with one, adjust Phase 4 to send to available teammate only

### GPT tmux Pane Immediately Exits

If the GPT tmux pane exits immediately after spawning:
1. Verify cli-proxy-api authentication is working: `curl -s http://localhost:8317/`
2. Test `gpt-claude-code` function manually in a terminal
3. Check tmux pane output for error messages: `tmux capture-pane -p -t {pane_id}`
4. Suggest: Run `gpt-claude-code` manually to diagnose the issue

### No Response from Teammates

If teammates don't respond within 3 minutes:
1. Show warning: "No response received from {teammate} within 3 minutes."
2. Send ONE follow-up message only (if teammate isActive: true):
   ```
   SendMessage(
     type: "message",
     recipient: "{teammate}",
     content: "Follow-up: Please provide your analysis on: {topic}",
     summary: "토론 주제 재전송"
   )
   ```
3. If still no response after follow-up, proceed with available responses

### Duplicate Response Prevention

- Do NOT resend messages to teammates that have already responded
- If a teammate sends multiple responses, use the FIRST response only
- If no response within 3 minutes and teammate isActive: true,
  send ONE follow-up message only

---

## Best Practices

**DO**:
- Include topic directly in spawn prompts (no separate broadcast needed)
- Show full perspective summaries for clarity
- Provide clear next-step commands
- Verify cli-proxy-api and gpt-claude-code availability before spawning
- Use follow-up broadcasts only for NEW questions after initial discussion

**DON'T**:
- Don't spawn teammates sequentially (use parallel spawning)
- Don't proceed if prerequisites are missing
- Don't ignore spawn failures
- Don't timeout too quickly on responses (allow 3 minutes per teammate)
- Don't leave orphaned team on failure (clean up)
- Don't resend the initial topic via broadcast (it's already in spawn prompts)
- Don't send duplicate messages to teammates that already responded

---

## Example Usage

**Simple Usage**:
```
/team:discussion "Should we use microservices or monolith for our new product?"
```

**Interactive Usage**:
```
/team:discussion
→ Prompts for category
→ Prompts for specific topic
→ Creates discussion team
→ Shows both perspectives
```

**With Technical Topic**:
```
/team:discussion "Compare React vs Vue for dashboard applications - performance, ecosystem, learning curve"
```

**Follow-up Example**:
After initial discussion:
```
SendMessage(type: "broadcast", content: "What about TypeScript support in both frameworks?")
```
