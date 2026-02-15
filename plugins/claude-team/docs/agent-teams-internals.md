# Agent Teams Internals

Claude Code Agent Teams의 내부 동작 방식을 문서화한다. 공식 CLI 레퍼런스에 노출되지 않는 비공개 플래그, 환경변수, 파일 구조를 포함한다.

## 팀메이트 스폰 메커니즘

### 사용자가 하는 것

```python
Task(
  team_name: "translators",
  name: "codex-en",
  model: "haiku",
  subagent_type: "ai-cli-tools:llms",
  run_in_background: true,
  prompt: "You are a translation proxy..."
)
```

### 내부적으로 실행되는 것

```bash
env CLAUDECODE=1 \
    CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1 \
  /path/to/claude \
    --agent-id codex-en@translators \
    --agent-name codex-en \
    --team-name translators \
    --agent-color blue \
    --parent-session-id <leader-session-uuid> \
    --agent-type ai-cli-tools:llms \
    --model haiku \
    --dangerously-skip-permissions
```

이 명령은 **독립된 Claude Code 프로세스**를 실행한다. tmux 백엔드를 사용하면 별도 tmux pane에서 실행된다.

---

## 비공개 CLI 플래그

`--help`에 노출되지 않으며, Agent Teams 시스템이 내부적으로 사용한다.

### 팀메이트 식별

| 플래그 | 형식 | 설명 |
|--------|------|------|
| `--agent-id <id>` | `name@team-name` | 팀 내 고유 식별자. inbox 파일명 등에 사용 |
| `--agent-name <name>` | 문자열 | 팀메이트 표시 이름. SendMessage의 recipient에 사용 |
| `--team-name <name>` | 문자열 | 소속 팀 이름. `~/.claude/teams/{name}/` 디렉토리에 매핑 |
| `--agent-color <color>` | CSS 색상 | UI 표시 색상. 이름 또는 hex (`blue`, `#4285F4`) |

### 팀 연결

| 플래그 | 형식 | 설명 |
|--------|------|------|
| `--parent-session-id <id>` | UUID | 팀 리더 세션 ID. 메시지 라우팅에 사용 |
| `--agent-type <type>` | `plugin:agent` | 에이전트 파일을 메인 에이전트로 로드. 예: `ai-cli-tools:llms` |

### 팀 설정

| 플래그 | 형식 | 설명 |
|--------|------|------|
| `--teammate-mode <mode>` | `auto`/`in-process`/`tmux` | 팀메이트 실행 방식 (공식 플래그) |
| `--team-mentions` | boolean | 팀 멘션 기능 활성화 |

---

## `--agent-type` 상세

**핵심 개념**: 플러그인에 정의된 서브에이전트를 독립 Claude 인스턴스의 메인 에이전트로 승격시킨다.

### 형식

```
--agent-type {plugin-name}:{agent-name}
```

### 예시

```bash
# ai-cli-tools 플러그인의 llms 에이전트를 메인으로 실행
--agent-type ai-cli-tools:llms

# 해당 에이전트 파일 위치
plugins/ai-cli-tools/agents/llms.md
```

### 동작

1. 지정된 플러그인의 에이전트 `.md` 파일을 찾는다
2. 에이전트의 frontmatter(tools, disallowedTools 등)를 세션 설정에 적용
3. 에이전트의 본문을 시스템 프롬프트로 로드
4. `--model`로 지정된 모델 위에서 실행

### 일반 서브에이전트 vs --agent-type

| 항목 | 서브에이전트 (Task) | --agent-type |
|------|-------------------|--------------|
| 실행 방식 | 부모 프로세스 내부 | 독립 프로세스 |
| 수명 | Task 완료 시 종료 | 명시적 shutdown까지 유지 |
| 통신 | 직접 반환 | inbox 파일 기반 메시지 |
| 팀 프로토콜 | 없음 | SendMessage, shutdown 지원 |
| 사용처 | 일회성 작업 | 장기 실행 팀메이트 |

---

## 환경변수

### 필수

| 변수 | 값 | 설명 |
|------|-----|------|
| `CLAUDECODE` | `1` | Claude Code 모드 활성화. 미설정 시 중첩 실행 차단 |
| `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS` | `1` | Agent Teams 기능 활성화 |

### 팀 관련

| 변수 | 설명 |
|------|------|
| `CLAUDE_CODE_TEAM_NAME` | 현재 팀 이름 |
| `CLAUDE_CODE_AGENT_NAME` | 현재 에이전트 이름 |
| `CLAUDE_CODE_TASK_LIST_ID` | 현재 태스크 리스트 ID |
| `CLAUDE_CODE_TEAMMATE_COMMAND` | 팀메이트 실행 명령 |
| `CLAUDE_CODE_TMUX_SESSION` | tmux 세션 이름 |
| `CLAUDE_CODE_TMUX_PREFIX` | tmux prefix 키 |

### 세션 관련

| 변수 | 설명 |
|------|------|
| `CLAUDE_CODE_SESSION_ID` | 현재 세션 UUID |
| `CLAUDE_CODE_SUBAGENT_MODEL` | 서브에이전트 기본 모델 |
| `CLAUDE_CODE_PLAN_MODE_REQUIRED` | plan 모드 강제 여부 |

---

## 파일 구조

### 팀 디렉토리

```
~/.claude/teams/{team-name}/
├── config.json          # 팀 설정 및 멤버 목록
└── inboxes/
    ├── team-lead.json   # 팀 리더 수신함
    ├── codex-en.json    # 팀메이트 수신함
    └── gemini-ja.json   # 팀메이트 수신함
```

### 태스크 디렉토리

```
~/.claude/tasks/{team-name}/
└── {task-id}.json       # 개별 태스크 파일
```

### config.json 구조

```json
{
  "name": "translators",
  "description": "Translation team",
  "createdAt": 1771180724196,
  "leadAgentId": "team-lead@translators",
  "leadSessionId": "0c6f5738-...",
  "members": [
    {
      "agentId": "team-lead@translators",
      "name": "team-lead",
      "agentType": "team-lead",
      "model": "claude-opus-4-6",
      "joinedAt": 1771180724196,
      "tmuxPaneId": "",
      "cwd": "/path/to/project",
      "subscriptions": []
    },
    {
      "agentId": "codex-en@translators",
      "name": "codex-en",
      "agentType": "ai-cli-tools:llms",
      "model": "haiku",
      "prompt": "You are a translation proxy...",
      "color": "blue",
      "planModeRequired": false,
      "joinedAt": 1771180731459,
      "tmuxPaneId": "%98",
      "cwd": "/path/to/project",
      "subscriptions": [],
      "backendType": "tmux",
      "isActive": false
    }
  ]
}
```

### member 필드 설명

| 필드 | 타입 | 설명 |
|------|------|------|
| `agentId` | string | `name@team-name` 형식 고유 ID |
| `name` | string | 표시 이름 (SendMessage recipient) |
| `agentType` | string | 에이전트 타입. `team-lead` 또는 `plugin:agent` |
| `model` | string | 사용 모델 (full name 또는 alias) |
| `prompt` | string | 시스템 프롬프트 (팀메이트만) |
| `color` | string | UI 색상 |
| `planModeRequired` | boolean | plan 모드 강제 여부 |
| `tmuxPaneId` | string | tmux pane ID (`%98` 등) |
| `backendType` | string | 실행 백엔드 (`tmux` 또는 `in-process`) |
| `isActive` | boolean | 현재 활성 상태 |
| `subscriptions` | array | 메시지 구독 설정 |

### inbox 메시지 형식

```json
[
  {
    "from": "team-lead",
    "text": "안녕하세요, 번역해주세요.",
    "summary": "번역 요청",
    "timestamp": "2026-02-16T17:42:27.554Z",
    "read": true
  }
]
```

| 필드 | 설명 |
|------|------|
| `from` | 발신자 이름 |
| `text` | 메시지 본문 |
| `summary` | 요약 (UI 미리보기) |
| `timestamp` | ISO 8601 타임스탬프 |
| `read` | 읽음 여부 |
| `color` | 발신자 색상 (선택) |

---

## 팀메이트 생명주기

```
1. TeamCreate
   └─ ~/.claude/teams/{name}/config.json 생성
   └─ 리더를 members에 등록

2. Task(team_name, name, ...)
   └─ CLI 명령 조립 (비공개 플래그 포함)
   └─ tmux pane 또는 in-process로 실행
   └─ config.json members에 추가
   └─ inbox 파일 생성

3. SendMessage
   └─ 대상의 inbox JSON에 메시지 추가
   └─ 대상 프로세스가 폴링하여 수신

4. idle_notification
   └─ 팀메이트 턴 종료 시 자동 발송
   └─ 리더 inbox에 JSON 메시지로 도착

5. shutdown_request → shutdown_response
   └─ 리더가 종료 요청
   └─ 팀메이트가 승인/거부
   └─ 승인 시 프로세스 종료, config에서 isActive=false

6. TeamDelete
   └─ teams/{name}/ 디렉토리 삭제
   └─ tasks/{name}/ 디렉토리 삭제
```

---

## 실행 백엔드

### tmux (기본)

- 각 팀메이트가 별도 tmux pane에서 실행
- `tmuxPaneId`로 식별 (`%98`, `%99` 등)
- `--teammate-mode tmux`로 활성화
- 장점: 프로세스 격리, 독립 실행
- 단점: tmux 설치 필요

### in-process

- 메인 프로세스 내에서 실행
- `--teammate-mode in-process`로 활성화
- 장점: 설치 불필요, 빠른 시작
- 단점: 메인 프로세스와 자원 공유

### auto (기본값)

- tmux가 설치되어 있으면 tmux, 아니면 in-process

---

## 참고

- 비공개 플래그는 CLI 버전에 따라 변경될 수 있다
- 이 문서는 Claude Code v2.1.42 기준이다
- 공식 문서: https://docs.anthropic.com/en/docs/claude-code
