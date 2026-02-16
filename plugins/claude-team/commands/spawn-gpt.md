---
description: "GPT-5.3 Codex 네이티브 팀메이트 스폰. cli-proxy-api를 통해 GPT 모델로 직접 실행되는 팀메이트를 생성합니다."
allowed-tools: Bash, Read, Write, Edit, AskUserQuestion, TeamCreate, SendMessage
namespace: team
argument-hint: [member-name] [--team team-name] [--prompt "initial task"]
---

# GPT Native Teammate Spawn

Spawn a GPT-5.3 Codex native teammate via tmux. This teammate runs directly on the GPT model through cli-proxy-api, with full Agent Teams tool access (Read, Write, Edit, Glob, Grep, etc.).

<task-context>
<plugin-path>./plugins/claude-team</plugin-path>
<component-name>spawn-gpt</component-name>
<mode>plugin</mode>
</task-context>

## Phase 1: Parse Arguments

**Goal**: Extract member-name, team name, and optional initial prompt from $ARGUMENTS.

**Actions**:

1. **Parse $ARGUMENTS**:

   Format: `[member-name] [--team team-name] [--prompt "initial task"]`

   - `member-name`: First positional argument (default: `"gpt"`)
   - `--team`: Team name (default: use active team from `~/.claude/teams/`)
   - `--prompt`: Initial task message to send after spawn

   Example parses:
   ```
   "gpt-worker --team my-team --prompt 'Review the auth module'"
   → name: "gpt-worker", team: "my-team", prompt: "Review the auth module"

   "gpt-coder"
   → name: "gpt-coder", team: (active team), prompt: (none)

   "" (empty)
   → name: "gpt", team: (active team), prompt: (none)
   ```

**Output**: `NAME`, `TEAM`, `PROMPT` variables

---

## Phase 2: Prerequisite Checks

**Goal**: Verify all requirements are met before spawning.

**Actions**:

1. **Check tmux availability**:
   ```bash
   which tmux
   ```
   If not found → Show error: "tmux가 설치되어 있지 않습니다. `sudo apt install tmux` 또는 `brew install tmux`로 설치하세요."

2. **Check CLAUDE_CODE_TMUX_SESSION**:
   ```bash
   echo "$CLAUDE_CODE_TMUX_SESSION"
   ```
   If empty → Show error: "CLAUDE_CODE_TMUX_SESSION 환경변수가 설정되지 않았습니다. tmux 세션 내에서 Claude Code를 실행하세요."

3. **Check cli-proxy-api is running**:
   ```bash
   curl -s --connect-timeout 3 http://localhost:8317/ > /dev/null 2>&1
   echo $?
   ```
   If exit code is not 0 → Show error:
   ```
   cli-proxy-api가 실행 중이지 않습니다.

   시작 방법:
   1. cli-proxy-api 서버를 시작하세요 (localhost:8317)
   2. 인증 토큰이 설정되어 있는지 확인하세요
   ```

4. **Check gpt-claude-code function exists**:
   ```bash
   zsh -c 'source ~/.zshrc && type gpt-claude-code' 2>&1
   ```
   If function not found → Show error:
   ```
   gpt-claude-code 함수를 찾을 수 없습니다.

   ~/.zshrc에 gpt-claude-code 함수가 정의되어 있는지 확인하세요.
   이 함수는 cli-proxy-api 환경변수를 설정하여 claude CLI를 GPT 모델로 실행합니다.
   ```

**If any check fails**: Stop execution and display the error with fix instructions.

**Output**: All prerequisites confirmed

---

## Phase 3: Team Setup

**Goal**: Ensure a team exists for the new teammate.

**Actions**:

1. **Determine team name**:

   Priority:
   1. `--team` argument value
   2. Active team: find most recent `~/.claude/teams/*/config.json`
   3. If no team exists → create one

2. **If no team found and no --team specified**:

   Use AskUserQuestion:
   - header: "Team"
   - question: "활성 팀이 없습니다. 새 팀을 생성할까요?"
   - options:
     - "새 팀 생성 (Recommended)" / "자동으로 'gpt-team' 이름의 팀을 생성합니다"
     - "취소" / "스폰을 중단합니다"

   If create → `TeamCreate(team_name: "gpt-team", description: "GPT native teammate team")`
   If cancel → Stop execution

3. **Verify team config exists**:
   ```bash
   cat ~/.claude/teams/${TEAM}/config.json
   ```

4. **Extract leader session ID** from config.json:
   Read `leadSessionId` field from config.json.

**Output**: `TEAM` name confirmed, `CONFIG` path, `LEAD_SESSION_ID`

---

## Phase 4: Spawn GPT Teammate

**Goal**: Create tmux pane running gpt-claude-code with Agent Teams flags.

**Actions**:

1. **Create inbox file**:
   ```bash
   mkdir -p ~/.claude/teams/${TEAM}/inboxes && echo '[]' > ~/.claude/teams/${TEAM}/inboxes/${NAME}.json
   ```

2. **Spawn tmux pane**:
   ```bash
   PANE_ID=$(tmux split-window -t "$CLAUDE_CODE_TMUX_SESSION" -c "$PWD" -dP -F '#{pane_id}' \
     "zsh -c 'source ~/.zshrc && gpt-claude-code \
       --agent-id ${NAME}@${TEAM} \
       --agent-name ${NAME} \
       --team-name ${TEAM} \
       --agent-color \"#10A37F\" \
       --parent-session-id ${LEAD_SESSION_ID} \
       --agent-type claude-team:gpt \
       --model sonnet \
       --dangerously-skip-permissions'")
   echo "$PANE_ID"
   ```

   - `source ~/.zshrc` loads the `gpt-claude-code` function with env vars
   - `--model sonnet` is mapped to `gpt-5.3-codex(high)` by the env vars
   - `--agent-type claude-team:gpt` loads `agents/gpt.md`
   - `--parent-session-id` connects message routing to the leader
   - `--dangerously-skip-permissions` enables autonomous operation

3. **Register member in config.json**:
   ```bash
   CONFIG="$HOME/.claude/teams/${TEAM}/config.json"
   jq --arg name "$NAME" --arg agentId "${NAME}@${TEAM}" --arg paneId "$PANE_ID" \
     '.members += [{
       "agentId": $agentId, "name": $name,
       "agentType": "claude-team:gpt", "model": "gpt-5.3-codex(high)",
       "color": "#10A37F", "tmuxPaneId": $paneId,
       "backendType": "tmux", "isActive": true,
       "joinedAt": (now * 1000 | floor), "cwd": env.PWD, "subscriptions": []
     }]' "$CONFIG" > /tmp/config-tmp-${NAME}.json && mv /tmp/config-tmp-${NAME}.json "$CONFIG"
   ```

**Output**: Pane ID, member registered in config

---

## Phase 5: Verify and Activate

**Goal**: Confirm the teammate spawned successfully and send initial message.

**Actions**:

1. **Verify tmux pane is alive**:
   ```bash
   tmux list-panes -t "$CLAUDE_CODE_TMUX_SESSION" -F '#{pane_id} #{pane_alive}' | grep "$PANE_ID"
   ```
   If pane not found or dead → Show error:
   ```
   GPT 팀메이트 pane이 즉시 종료되었습니다.

   확인 사항:
   1. cli-proxy-api가 정상 동작하는지: curl http://localhost:8317/
   2. gpt-claude-code 함수의 인증 토큰이 유효한지
   3. tmux 세션에 여유 공간이 있는지
   ```

2. **Wait briefly for agent startup**:
   ```bash
   sleep 2
   ```

3. **Send initial prompt** (if `--prompt` provided):

   Use SendMessage:
   ```
   SendMessage(
     type: "message",
     recipient: "${NAME}",
     content: "${PROMPT}",
     summary: "GPT 팀메이트 초기 작업 할당"
   )
   ```

4. **Display spawn dashboard**:

   ```markdown
   ## GPT Native Teammate Spawned

   | Field | Value |
   |-------|-------|
   | **Name** | ${NAME} |
   | **Team** | ${TEAM} |
   | **Model** | GPT-5.3 Codex (high) via cli-proxy-api |
   | **Agent Type** | claude-team:gpt |
   | **Tmux Pane** | ${PANE_ID} |
   | **Config** | ~/.claude/teams/${TEAM}/config.json |
   | **Inbox** | ~/.claude/teams/${TEAM}/inboxes/${NAME}.json |

   ### Capabilities
   - Full file access (Read, Write, Edit, Glob, Grep)
   - Code execution (Bash)
   - Task management (TaskList, TaskGet, TaskUpdate, TaskCreate)
   - Web access (WebSearch, WebFetch)
   - Direct Agent Teams communication (SendMessage)

   ### How to Interact

   **Send a message:**
   ```
   SendMessage(recipient: "${NAME}", content: "Your task here...")
   ```

   **Check status:**
   ```
   /claude-team:status
   ```

   **Shutdown:**
   ```
   SendMessage(type: "shutdown_request", recipient: "${NAME}", content: "Task complete")
   ```
   ```

**Output**: Spawn confirmation with usage guide

---

## Error Handling

### cli-proxy-api Not Running

```
cli-proxy-api가 실행 중이지 않습니다 (localhost:8317).

GPT 네이티브 팀메이트는 cli-proxy-api를 통해 GPT 모델에 접근합니다.

시작 방법:
1. cli-proxy-api 서버를 시작하세요
2. 서버가 localhost:8317에서 응답하는지 확인: curl http://localhost:8317/
3. 다시 시도: /claude-team:spawn-gpt ${NAME}
```

### gpt-claude-code Function Not Found

```
gpt-claude-code 함수를 찾을 수 없습니다.

이 함수는 ~/.zshrc에 정의되어야 하며, 다음 환경변수를 설정합니다:
- ANTHROPIC_BASE_URL: cli-proxy-api 엔드포인트
- ANTHROPIC_AUTH_TOKEN: 인증 토큰
- ANTHROPIC_MODEL: GPT 모델 매핑

~/.zshrc를 확인하고 함수를 정의하세요.
```

### tmux Session Not Available

```
tmux 세션을 찾을 수 없습니다.

CLAUDE_CODE_TMUX_SESSION 환경변수: ${CLAUDE_CODE_TMUX_SESSION:-"(미설정)"}

해결 방법:
1. tmux 세션 내에서 Claude Code를 실행하세요
2. 또는 CLAUDE_CODE_TMUX_SESSION 환경변수를 설정하세요
```

### Pane Immediately Exits

```
GPT 팀메이트가 즉시 종료되었습니다.

일반적인 원인:
1. cli-proxy-api 인증 실패 → 토큰 확인
2. cli-proxy-api 연결 실패 → 서버 상태 확인
3. gpt-claude-code 함수 내부 오류 → 함수 수동 실행 테스트

디버깅:
  zsh -c 'source ~/.zshrc && gpt-claude-code --help'
```

### Team Not Found

If `--team` specifies a non-existent team:
1. Use AskUserQuestion to ask whether to create the team
2. If yes → TeamCreate with the specified name
3. If no → Abort

---

## Example Usage

**Basic spawn:**
```
/claude-team:spawn-gpt
```
→ Spawns "gpt" teammate in active team

**Named spawn:**
```
/claude-team:spawn-gpt gpt-reviewer
```
→ Spawns "gpt-reviewer" teammate

**With team and initial task:**
```
/claude-team:spawn-gpt gpt-worker --team my-project --prompt "Review src/auth/ for security issues"
```
→ Creates "gpt-worker" in "my-project" team with initial task

**Multiple GPT teammates:**
```
/claude-team:spawn-gpt gpt-frontend --team fullstack
/claude-team:spawn-gpt gpt-backend --team fullstack
```
→ Two GPT teammates in the same team
