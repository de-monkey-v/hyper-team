---
name: spawn-teammate
description: "GPT-5.3 Codex 네이티브 팀메이트 스폰. cli-proxy-api를 통해 GPT 모델로 직접 실행되는 팀메이트를 생성합니다."
version: 1.0.0
---

# GPT Native Teammate Spawn Skill

GPT-5.3 Codex 네이티브 팀메이트를 tmux pane으로 스폰하는 절차를 제공합니다.
이 스킬을 로드한 커맨드(specify, implement, verify 등)가 절차에 따라 GPT 팀메이트를 스폰합니다.

## 인자 형식

```
{member-name} --team {team-name}
```

- `member-name`: 팀메이트 이름 (예: `pm`, `developer`, `qa`)
- `--team`: 팀 이름 (필수)

파싱 예시:
```
"pm --team specify-001"
→ NAME: "pm", TEAM: "specify-001"

"developer --team implement-003"
→ NAME: "developer", TEAM: "implement-003"
```

## 스폰 절차

### Step 1: Prerequisite Check

모든 전제조건을 확인합니다. **하나라도 실패하면 즉시 중단하고 에러를 표시합니다.**

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

**1-3. cli-proxy-api 확인:**
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

**1-4. gpt-claude-code 함수 확인:**
```bash
zsh -c 'source ~/.zshrc && type gpt-claude-code' 2>&1
```
함수를 찾을 수 없으면:
```
gpt-claude-code 함수를 찾을 수 없습니다.

~/.zshrc에 gpt-claude-code 함수가 정의되어 있는지 확인하세요.
이 함수는 cli-proxy-api 환경변수를 설정하여 claude CLI를 GPT 모델로 실행합니다.
```

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

**4-2. 사전 체크 및 Pane 스폰:**
```bash
# 터미널 너비 체크
TERM_WIDTH=$(tmux display-message -p '#{window_width}')
if [ "$TERM_WIDTH" -lt 120 ]; then
  echo "⚠️ 터미널 너비가 ${TERM_WIDTH}열입니다 (권장: 120열 이상). pane이 좁을 수 있습니다."
fi

# Pane 높이 변수화 (환경변수로 오버라이드 가능)
PANE_HEIGHT=${SPAWN_PANE_HEIGHT:-15}

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
```

**4-3. 레이아웃 재조정:**

팀메이트가 2개 이상일 때만 레이아웃을 재배치합니다 (1개일 때 불필요한 flickering 방지):
```bash
MEMBER_COUNT=$(jq '.members | length' "$CONFIG" 2>/dev/null || echo 0)
if [ "$MEMBER_COUNT" -ge 2 ]; then
  tmux select-layout -t "$TMUX_SESSION" main-vertical
fi
```

핵심 플래그 설명:
- `tmux display-message -p '#S'`: 현재 tmux 세션 이름을 동적으로 감지 (`CLAUDE_CODE_TMUX_SESSION` 환경변수 불필요)
- `-l $PANE_HEIGHT`: 팀메이트 pane 높이 (기본 15행, `SPAWN_PANE_HEIGHT` 환경변수로 조정 가능)
- `-d`: 새 pane으로 포커스 전환하지 않음 (리더 pane 유지)
- `-P -F '#{pane_id}'`: 생성된 pane ID 반환
- `source ~/.zshrc`: `gpt-claude-code` 함수 및 환경변수 로드
- `--model opus`: cli-proxy-api의 환경변수에 의해 `gpt-5.3-codex(high)`로 매핑됨
- `gpt-claude-code`: cli-proxy-api 환경변수를 설정하여 claude CLI를 GPT 모델로 직접 실행 (`~/.zshrc` 정의, agent-type 승급 불필요)
- `--parent-session-id`: 리더와의 메시지 라우팅 연결
- `--dangerously-skip-permissions`: 자율적 실행 허용

#### Pane 크기 전략

| 시나리오 | 전략 |
|----------|------|
| 팀메이트 1개 | `-l 15`로 고정 크기 분할 |
| 팀메이트 2개+ | 스폰 후 `main-vertical`로 재배치 (리더=왼쪽 전체높이, 팀메이트=우측 row) |
| 터미널 너비 부족 (<120열) | 최소 너비 40열 보장, 부족 시 경고 |

### Step 5: Config 등록 (원자적 쓰기)

```bash
CONFIG="$HOME/.claude/teams/${TEAM}/config.json"
LOCKFILE="$HOME/.claude/teams/${TEAM}/.config.lock"

(
  flock -w 10 200 || { echo "ERROR: Config lock 획득 실패"; exit 1; }
  jq --arg name "$NAME" --arg agentId "${NAME}@${TEAM}" --arg paneId "$PANE_ID" \
    '.members += [{
      "agentId": $agentId, "name": $name,
      "agentType": "claude-team:gpt", "model": "gpt-5.3-codex(high)",
      "color": "#10A37F", "tmuxPaneId": $paneId,
      "backendType": "tmux", "isActive": true,
      "joinedAt": (now * 1000 | floor), "cwd": env.PWD, "subscriptions": []
    }]' "$CONFIG" > "${CONFIG}.tmp" && mv "${CONFIG}.tmp" "$CONFIG"
) 200>"$LOCKFILE"

# 쓰기 후 검증
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
  echo "ERROR: GPT 팀메이트 pane이 즉시 종료되었습니다."
  _spawn_rollback "$CONFIG" "$NAME" "$TEAM"
  echo "Rollback 완료: config에서 ${NAME} 제거됨"
  echo ""
  echo "확인 사항:"
  echo "1. cli-proxy-api가 정상 동작하는지: curl http://localhost:8317/"
  echo "2. gpt-claude-code 함수의 인증 토큰이 유효한지"
  echo "3. tmux 세션에 여유 공간이 있는지"
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

**스폰 완료 메시지 표시:**
```markdown
GPT 팀메이트 스폰 완료: ${NAME} (Team: ${TEAM})
- Model: GPT-5.3 Codex (high) via cli-proxy-api
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

| 에러 | 원인 | 해결 |
|------|------|------|
| tmux not found | tmux 미설치 | `sudo apt install tmux` 또는 `brew install tmux` |
| `$TMUX` 비어있음 | tmux 밖에서 실행 | tmux 세션 내에서 Claude Code 실행 |
| cli-proxy-api 미응답 | 서버 미실행 | cli-proxy-api 서버 시작 (localhost:8317) |
| gpt-claude-code 미발견 | 함수 미정의 | `~/.zshrc`에 함수 정의 |
| Pane 즉시 종료 | 인증/연결 실패 | 토큰, 서버 상태, 함수 수동 테스트 |
| 리더 pane 너무 작음 | 반복 분할로 공간 부족 | `tmux select-layout main-vertical`로 재배치 |

## 트러블슈팅

### Pane 즉시 종료

| 원인 | 진단 방법 | 해결 |
|------|----------|------|
| cli-proxy-api 미실행 | `curl http://localhost:8317/` | 서버 시작 |
| 인증 토큰 만료 | `gpt-claude-code --help` 수동 실행 | 토큰 갱신 |
| `gpt-claude-code` 함수 오류 | `zsh -c 'source ~/.zshrc && type gpt-claude-code'` | 함수 재정의 |
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

### gpt-claude-code 보안 참고

`gpt-claude-code` 함수는 cli-proxy-api를 통해 GPT 모델에 접근합니다:
- cli-proxy-api의 인증 토큰은 환경변수로 관리됩니다
- 팀메이트 pane에서 토큰이 노출되지 않도록 `~/.zshrc`에서 환경변수로 주입하세요
- 프로덕션 환경에서는 credential을 별도 파일이나 시크릿 매니저로 분리하는 것을 권장합니다

## 호출 패턴 (커맨드에서 사용)

커맨드의 GPT 모드 (`--gpt`) 섹션에서:

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
