---
name: spawn-teammate
description: "팀메이트 스폰. GPT 모드(cli-proxy-api) 또는 Claude 네이티브 모드(--agent-type)로 팀메이트를 생성합니다."
version: 2.0.0
---

# Teammate Spawn Skill

팀메이트를 tmux pane으로 스폰하는 절차를 제공합니다.
이 스킬을 로드한 커맨드(specify, implement, verify 등)가 절차에 따라 팀메이트를 스폰합니다.

**두 가지 모드를 지원합니다:**
- **GPT 모드** (기존): `--agent-type` 없이 호출 → cli-proxy-api를 통해 GPT-5.3 Codex로 실행
- **Claude 모드** (신규): `--agent-type {plugin}:{agent}` 지정 → Claude CLI 네이티브로 실행

## 인자 형식

```
{member-name} --team {team-name} [--agent-type {plugin}:{agent}] [--model {model}] [--color {color}]
```

- `member-name`: 팀메이트 이름 (예: `pm`, `developer`, `qa`) — **필수**
- `--team`: 팀 이름 — **필수**
- `--agent-type`: 에이전트 타입 (예: `claude-team:implementer`) — 지정 시 Claude 모드
- `--model`: 모델 오버라이드 (기본값: 에이전트별 기본 모델)
- `--color`: 색상 오버라이드 (기본값: 에이전트별 기본 색상)

**모드 감지**: `--agent-type` 있으면 **Claude 모드**, 없으면 **GPT 모드**

파싱 예시:
```
"pm --team specify-001"
→ GPT 모드: NAME="pm", TEAM="specify-001"

"developer --team impl-003 --agent-type claude-team:implementer"
→ Claude 모드: NAME="developer", TEAM="impl-003", AGENT_TYPE="claude-team:implementer"

"developer --team impl-003 --agent-type claude-team:implementer --model opus --color #0066CC"
→ Claude 모드 + 오버라이드: NAME="developer", TEAM="impl-003", AGENT_TYPE="claude-team:implementer", MODEL="opus", COLOR="#0066CC"
```

## 에이전트별 기본값 테이블

`--model` 또는 `--color` 미지정 시 에이전트 파일의 기본값을 사용합니다:

| Agent | Icon | Model | Color | 특성 |
|-------|------|-------|-------|------|
| Leader | 👑 | - | - | 팀 리더 |
| `planner` | 🔵 | opus | #FF6699 | 제품 기획/요구사항 분석 (읽기 전용 + 웹 검색) |
| `architect` | 🔵 | opus | #CC6600 | 아키텍처 분석/설계 (읽기 전용) |
| `implementer` | 🔵 | opus | #0066CC | 코드 구현 (읽기/쓰기) |
| `tester` | 🔵 | opus | #00AA44 | 테스트/검증 (읽기/쓰기) |
| `reviewer` | 🔵 | opus | #8800CC | 코드 리뷰 (읽기 전용) |
| `researcher` | 🔵 | opus | #00AACC | 기술 리서치 (읽기 전용 + 웹 검색) |
| `backend` | 🔵 | opus | #0066CC | 백엔드/API (읽기/쓰기) |
| `frontend` | 🔵 | opus | #FF6600 | 프론트엔드/UI (읽기/쓰기) |
| `coordinator` | 🔵 | opus | #FFAA00 | 태스크 조율 (읽기 전용 + 태스크 관리) |

GPT 모드 기본값: `model=opus` (→ gpt-5.3-codex(xhigh) 매핑), `color=#10A37F`, `icon=🤖`

## 스폰 절차

### Step 1: Prerequisite Check

모든 전제조건을 확인합니다. **하나라도 실패하면 즉시 중단하고 에러를 표시합니다.**

#### 공통 체크 (GPT/Claude 모드 모두)

**1-1. tmux 확인:**
```bash
which tmux
```
실패 시: "tmux가 설치되어 있지 않습니다. `sudo apt install tmux` 또는 `brew install tmux`로 설치하세요."

**1-2. tmux 세션 내 실행 확인:**
```bash
echo "$TMUX"
```
비어 있으면: "tmux 세션 내에서 Claude Code를 실행하세요."

> **참고**: `CLAUDE_CODE_TMUX_SESSION` 환경변수는 불필요합니다. `$TMUX` 환경변수로 tmux 세션 여부를 확인하고, 세션 이름은 `tmux display-message -p '#S'`로 동적 감지합니다.

#### GPT 모드 전용 체크 (`--agent-type` 없을 때)

**1-3g. cli-proxy-api 확인:**
```bash
curl -s --connect-timeout 3 http://localhost:8317/ > /dev/null 2>&1
echo $?
```
exit code가 0이 아니면:
```
cli-proxy-api가 실행 중이지 않습니다.

시작 방법:
1. cli-proxy-api 서버를 시작하세요 (localhost:8317)
2. 인증 토큰이 설정되어 있는지 확인하세요
```

**1-4g. gpt-claude-code 함수 확인:**
```bash
zsh -c 'source ~/.zshrc && type gpt-claude-code' 2>&1
```
함수를 찾을 수 없으면:
```
gpt-claude-code 함수를 찾을 수 없습니다.

~/.zshrc에 gpt-claude-code 함수가 정의되어 있는지 확인하세요.
이 함수는 cli-proxy-api 환경변수를 설정하여 claude CLI를 GPT 모델로 실행합니다.
```

#### Claude 모드 전용 체크 (`--agent-type` 있을 때)

**1-3c. claude CLI 확인:**
```bash
which claude
```
실패 시: "claude CLI가 설치되어 있지 않습니다. `npm install -g @anthropic-ai/claude-code`로 설치하세요."

### Step 2: Inbox 생성

```bash
mkdir -p ~/.claude/teams/${TEAM}/inboxes && echo '[]' > ~/.claude/teams/${TEAM}/inboxes/${NAME}.json
```

### Step 3: Leader Session ID 추출

```bash
CONFIG="$HOME/.claude/teams/${TEAM}/config.json"
LEAD_SESSION_ID=$(jq -r '.leadSessionId' "$CONFIG")
```

### Step 4: tmux 세션 감지 및 Pane 스폰

**4-1. 현재 tmux 세션 이름을 동적으로 감지:**
```bash
TMUX_SESSION=$(tmux display-message -p '#S')
```

**4-2. 사전 체크:**
```bash
# 터미널 너비 체크
TERM_WIDTH=$(tmux display-message -p '#{window_width}')
if [ "$TERM_WIDTH" -lt 120 ]; then
  echo "터미널 너비가 ${TERM_WIDTH}열입니다 (권장: 120열 이상). pane이 좁을 수 있습니다."
fi

# Pane 높이 변수화 (환경변수로 오버라이드 가능)
PANE_HEIGHT=${SPAWN_PANE_HEIGHT:-15}
```

**4-3. 모드별 Pane 스폰:**

#### GPT 모드 (`--agent-type` 없을 때)

```bash
PANE_ID=$(tmux split-window -t "$TMUX_SESSION" -l $PANE_HEIGHT -c "$PWD" -dP -F '#{pane_id}' \
  "zsh -c 'source ~/.zshrc && gpt-claude-code \
    --agent-id ${NAME}@${TEAM} \
    --agent-name ${NAME} \
    --team-name ${TEAM} \
    --agent-color \"#10A37F\" \
    --parent-session-id ${LEAD_SESSION_ID} \
    --model opus \
    --dangerously-skip-permissions'")
echo "$PANE_ID"
tmux select-pane -t "$PANE_ID" -T "🤖 ${NAME}"
```

핵심 플래그 설명:
- `source ~/.zshrc`: `gpt-claude-code` 함수 및 환경변수 로드
- `--model opus`: cli-proxy-api의 환경변수에 의해 `gpt-5.3-codex(xhigh)`로 매핑됨
- `gpt-claude-code`: cli-proxy-api 환경변수를 설정하여 claude CLI를 GPT 모델로 직접 실행

#### Claude 모드 (`--agent-type` 있을 때)

에이전트별 기본값 룩업 (MODEL/COLOR 미지정 시):
```bash
# --model 미지정 시 에이전트 기본값 사용
if [ -z "$MODEL" ]; then
  MODEL="opus"  # 모든 에이전트의 기본 모델
fi

# --color 미지정 시 에이전트 기본값 사용
if [ -z "$COLOR" ]; then
  case "$AGENT_TYPE" in
    *:planner)     COLOR="#FF6699" ;;
    *:architect)   COLOR="#CC6600" ;;
    *:implementer) COLOR="#0066CC" ;;
    *:tester)      COLOR="#00AA44" ;;
    *:reviewer)    COLOR="#8800CC" ;;
    *:researcher)  COLOR="#00AACC" ;;
    *:backend)     COLOR="#0066CC" ;;
    *:frontend)    COLOR="#FF6600" ;;
    *:coordinator) COLOR="#FFAA00" ;;
    *)             COLOR="#0066CC" ;;  # fallback
  esac
fi
```

스폰 명령어:
```bash
PANE_ID=$(tmux split-window -t "$TMUX_SESSION" -l $PANE_HEIGHT -c "$PWD" -dP -F '#{pane_id}' \
  "env CLAUDECODE=1 CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1 \
    claude \
      --agent-id ${NAME}@${TEAM} \
      --agent-name ${NAME} \
      --team-name ${TEAM} \
      --agent-color '${COLOR}' \
      --parent-session-id ${LEAD_SESSION_ID} \
      --agent-type ${AGENT_TYPE} \
      --model ${MODEL} \
      --dangerously-skip-permissions")
echo "$PANE_ID"
tmux select-pane -t "$PANE_ID" -T "🔵 ${NAME}"
```

핵심 플래그 설명:
- `env CLAUDECODE=1`: Claude Code 환경 표시
- `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`: Agent Teams 기능 활성화
- `claude`: Claude CLI 직접 실행 (gpt-claude-code 함수 불필요)
- `--agent-type ${AGENT_TYPE}`: 에이전트 파일의 프롬프트/도구/모델 설정 적용
- `--model ${MODEL}`: 모델 지정 (opus → Claude Opus 4.6)
- `--parent-session-id`: 리더와의 메시지 라우팅 연결
- `--dangerously-skip-permissions`: 자율적 실행 허용

**4-4. Pane Border 활성화 및 레이아웃 재조정:**

```bash
MEMBER_COUNT=$(jq '.members | length' "$CONFIG" 2>/dev/null || echo 0)

# 첫 번째 팀메이트일 때: border 활성화 + 리더 pane 타이틀 설정
if [ "$MEMBER_COUNT" -eq 0 ]; then
  tmux set-option -w pane-border-status bottom
  tmux set-option -w pane-border-format " #{pane_title} "
  # 리더 pane에도 타이틀 설정
  LEADER_PANE=$(tmux display-message -p '#{pane_id}')
  tmux select-pane -t "$LEADER_PANE" -T "👑 LEADER"
fi

# 팀메이트가 2개 이상일 때만 레이아웃을 재배치합니다 (1개일 때 불필요한 flickering 방지)
if [ "$MEMBER_COUNT" -ge 2 ]; then
  tmux select-layout -t "$TMUX_SESSION" main-vertical
fi
```

#### Pane 크기 전략

| 시나리오 | 전략 |
|----------|------|
| 팀메이트 1개 | `-l 15`로 고정 크기 분할 |
| 팀메이트 2개+ | 스폰 후 `main-vertical`로 재배치 (리더=왼쪽 전체높이, 팀메이트=우측 row) |
| 터미널 너비 부족 (<120열) | 최소 너비 40열 보장, 부족 시 경고 |

### Step 5: Config 등록 (원자적 쓰기)

#### GPT 모드

```bash
CONFIG="$HOME/.claude/teams/${TEAM}/config.json"
LOCKFILE="$HOME/.claude/teams/${TEAM}/.config.lock"

(
  flock -w 10 200 || { echo "ERROR: Config lock 획득 실패"; exit 1; }
  jq --arg name "$NAME" --arg agentId "${NAME}@${TEAM}" --arg paneId "$PANE_ID" \
    '.members += [{
      "agentId": $agentId, "name": $name,
      "agentType": "claude-team:gpt", "model": "gpt-5.3-codex(xhigh)",
      "color": "#10A37F", "tmuxPaneId": $paneId,
      "backendType": "tmux", "isActive": true,
      "joinedAt": (now * 1000 | floor), "cwd": env.PWD, "subscriptions": []
    }]' "$CONFIG" > "${CONFIG}.tmp" && mv "${CONFIG}.tmp" "$CONFIG"
) 200>"$LOCKFILE"
```

#### Claude 모드

```bash
CONFIG="$HOME/.claude/teams/${TEAM}/config.json"
LOCKFILE="$HOME/.claude/teams/${TEAM}/.config.lock"

(
  flock -w 10 200 || { echo "ERROR: Config lock 획득 실패"; exit 1; }
  jq --arg name "$NAME" --arg agentId "${NAME}@${TEAM}" --arg paneId "$PANE_ID" \
    --arg agentType "$AGENT_TYPE" --arg model "$MODEL" --arg color "$COLOR" \
    '.members += [{
      "agentId": $agentId, "name": $name,
      "agentType": $agentType, "model": $model,
      "color": $color, "tmuxPaneId": $paneId,
      "backendType": "tmux", "isActive": true,
      "joinedAt": (now * 1000 | floor), "cwd": env.PWD, "subscriptions": []
    }]' "$CONFIG" > "${CONFIG}.tmp" && mv "${CONFIG}.tmp" "$CONFIG"
) 200>"$LOCKFILE"
```

**쓰기 후 검증 (공통):**
```bash
REGISTERED=$(jq --arg name "$NAME" '.members[] | select(.name == $name) | .name' "$CONFIG")
[ -z "$REGISTERED" ] && echo "ERROR: ${NAME} 등록 실패" && tmux kill-pane -t "$PANE_ID" 2>/dev/null
```

### Step 6: 스폰 확인 및 Rollback

Rollback 함수 정의:
```bash
_spawn_rollback() {
  local CONFIG="$1" NAME="$2" TEAM="$3"
  local LOCKFILE="$HOME/.claude/teams/${TEAM}/.config.lock"
  (
    flock -w 5 200
    jq --arg name "$NAME" '.members = [.members[] | select(.name != $name)]' \
      "$CONFIG" > "${CONFIG}.tmp" && mv "${CONFIG}.tmp" "$CONFIG"
  ) 200>"$LOCKFILE"
  rm -f "$HOME/.claude/teams/${TEAM}/inboxes/${NAME}.json"
}
```

**Phase 1 (0.5s): Pane 즉시 사망 감지:**
```bash
sleep 0.5
if ! tmux list-panes -a -F '#{pane_id}' | grep -q "$PANE_ID"; then
  echo "ERROR: 팀메이트 pane이 즉시 종료되었습니다."
  _spawn_rollback "$CONFIG" "$NAME" "$TEAM"
  echo "Rollback 완료: config에서 ${NAME} 제거됨"
  echo ""
  # 모드별 진단 가이드
  if [ -n "$AGENT_TYPE" ]; then
    echo "확인 사항 (Claude 모드):"
    echo "1. claude CLI가 정상 동작하는지: claude --version"
    echo "2. ANTHROPIC_API_KEY가 설정되어 있는지"
    echo "3. 에이전트 타입이 유효한지: ${AGENT_TYPE}"
    echo "4. tmux 세션에 여유 공간이 있는지"
  else
    echo "확인 사항 (GPT 모드):"
    echo "1. cli-proxy-api가 정상 동작하는지: curl http://localhost:8317/"
    echo "2. gpt-claude-code 함수의 인증 토큰이 유효한지"
    echo "3. tmux 세션에 여유 공간이 있는지"
  fi
  exit 1
fi
```

**Phase 2 (최대 5s): Agent 프로세스 기동 확인:**
```bash
AGENT_READY=false
for i in $(seq 1 10); do
  PANE_CMD=$(tmux list-panes -a -F '#{pane_id} #{pane_current_command}' | grep "$PANE_ID" | awk '{print $2}')
  if [ -z "$PANE_CMD" ]; then
    echo "ERROR: Pane이 Phase 2에서 종료됨"
    _spawn_rollback "$CONFIG" "$NAME" "$TEAM"
    exit 1
  fi
  if echo "$PANE_CMD" | grep -qE '^(claude|cc)$'; then
    AGENT_READY=true
    break
  fi
  sleep 0.5
done

if [ "$AGENT_READY" != "true" ]; then
  echo "WARN: Agent 프로세스(claude/cc)가 5초 내 감지되지 않음 (현재: ${PANE_CMD}). 계속 진행합니다."
fi
```

**스폰 완료 메시지 표시 (모드별 분기):**

GPT 모드:
```markdown
GPT 팀메이트 스폰 완료: ${NAME} (Team: ${TEAM})
- Model: GPT-5.3 Codex (xhigh) via cli-proxy-api
- Pane: ${PANE_ID}
```

Claude 모드:
```markdown
Claude 팀메이트 스폰 완료: ${NAME} (Team: ${TEAM})
- Agent Type: ${AGENT_TYPE}
- Model: ${MODEL}
- Pane: ${PANE_ID}
```

## 스폰 완료 후 작업

스폰 완료 후 호출한 커맨드가 **SendMessage로 초기 작업을 지시**합니다:

```
SendMessage tool:
- type: "message"
- recipient: "${NAME}"
- content: |
    [역할 템플릿 기반 프롬프트]
- summary: "${NAME} 초기 작업 지시"
```

## 에러 핸들링 요약

### 공통 에러

| 에러 | 원인 | 해결 |
|------|------|------|
| tmux not found | tmux 미설치 | `sudo apt install tmux` 또는 `brew install tmux` |
| `$TMUX` 비어있음 | tmux 밖에서 실행 | tmux 세션 내에서 Claude Code 실행 |
| Pane 즉시 종료 | 인증/연결 실패 | 모드별 진단 가이드 참조 |
| 리더 pane 너무 작음 | 반복 분할로 공간 부족 | `tmux select-layout main-vertical`로 재배치 |

### GPT 모드 전용 에러

| 에러 | 원인 | 해결 |
|------|------|------|
| cli-proxy-api 미응답 | 서버 미실행 | cli-proxy-api 서버 시작 (localhost:8317) |
| gpt-claude-code 미발견 | 함수 미정의 | `~/.zshrc`에 함수 정의 |

### Claude 모드 전용 에러

| 에러 | 원인 | 해결 |
|------|------|------|
| claude CLI 미발견 | CLI 미설치 | `npm install -g @anthropic-ai/claude-code` |
| 에이전트 타입 미인식 | 잘못된 에이전트명 | 에이전트별 기본값 테이블 참조 |

## 트러블슈팅

### Pane 즉시 종료

| 원인 | 진단 방법 | 해결 |
|------|----------|------|
| cli-proxy-api 미실행 (GPT) | `curl http://localhost:8317/` | 서버 시작 |
| 인증 토큰 만료 (GPT) | `gpt-claude-code --help` 수동 실행 | 토큰 갱신 |
| `gpt-claude-code` 함수 오류 (GPT) | `zsh -c 'source ~/.zshrc && type gpt-claude-code'` | 함수 재정의 |
| claude CLI 미설치 (Claude) | `which claude` | `npm i -g @anthropic-ai/claude-code` |
| ANTHROPIC_API_KEY 미설정 (Claude) | `echo $ANTHROPIC_API_KEY` | 환경변수 설정 |
| 에이전트 타입 오류 (Claude) | 에이전트 파일 존재 여부 확인 | `plugins/claude-team/agents/` 확인 |
| tmux 공간 부족 | `tmux list-panes` 확인 | 불필요한 pane 정리 또는 터미널 확대 |
| 환경변수 미로드 | `source ~/.zshrc` 후 재시도 | `.zshrc` 내 함수/변수 확인 |

### Config 죽은 멤버 수동 정리

config에 `isActive: true`이지만 pane이 없는 멤버가 남아있을 때:

```bash
# 죽은 멤버 확인
CONFIG="$HOME/.claude/teams/${TEAM}/config.json"
jq -r '.members[] | select(.isActive == true) | .tmuxPaneId' "$CONFIG" | while read pane; do
  tmux list-panes -a -F '#{pane_id}' | grep -q "$pane" || echo "Dead member pane: $pane"
done

# 특정 멤버 제거
jq --arg name "dead-member" '.members = [.members[] | select(.name != $name)]' \
  "$CONFIG" > "${CONFIG}.tmp" && mv "${CONFIG}.tmp" "$CONFIG"
```

### 동시 스폰 시 멤버 누락 진단

```bash
# config에 등록된 멤버 수 확인
jq '.members | length' "$HOME/.claude/teams/${TEAM}/config.json"

# 실제 팀메이트 pane 수 확인
jq -r '.members[] | .tmuxPaneId' "$HOME/.claude/teams/${TEAM}/config.json" | while read pane; do
  tmux list-panes -a -F '#{pane_id}' | grep -q "$pane" && echo "OK: $pane" || echo "MISSING: $pane"
done
```

### 보안 참고

**GPT 모드**: `gpt-claude-code` 함수는 cli-proxy-api를 통해 GPT 모델에 접근합니다:
- cli-proxy-api의 인증 토큰은 환경변수로 관리됩니다
- 팀메이트 pane에서 토큰이 노출되지 않도록 `~/.zshrc`에서 환경변수로 주입하세요

**Claude 모드**: Claude CLI는 ANTHROPIC_API_KEY를 사용합니다:
- API 키는 환경변수로 관리됩니다
- 프로덕션 환경에서는 credential을 별도 파일이나 시크릿 매니저로 분리하는 것을 권장합니다

## 호출 패턴 (커맨드에서 사용)

### GPT 모드 (`--gpt`)

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "{role-name} --team {team-name}"

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "{role-name}"
- content: |
    [역할 템플릿 기반 프롬프트]
- summary: "{role-name} 초기 작업 지시"
```

### Claude 모드 (`--agent-type`)

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "{role-name} --team {team-name} --agent-type claude-team:{agent}"

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "{role-name}"
- content: |
    [역할 템플릿 기반 프롬프트]
- summary: "{role-name} 초기 작업 지시"
```
