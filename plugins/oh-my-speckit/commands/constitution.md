---
description: 프로젝트 Constitution 초기화/업데이트 (Agent Teams)
argument-hint: [--update]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, AskUserQuestion, Task, Skill, TaskCreate, TaskUpdate, TaskList, TeamCreate, TeamDelete, SendMessage
---

# Constitution Command

프로젝트의 기술 원칙, 아키텍처 규칙, 코딩 컨벤션을 분석하여 constitution.md를 생성/업데이트합니다.
Agent Teams 기반으로 researcher 팀메이트에게 프로젝트 분석을 위임합니다.

**핵심 원칙**:
- **리더(이 커맨드)는 사용자와 소통하고 팀을 조율** - 코드베이스 직접 분석 금지
- **모든 분석은 팀메이트(researcher)가 수행**
- **높은 자율성**: 리더는 고수준 목표만 전달, 팀메이트가 세부사항 자율 결정
- **요약 중심 출력** (전체 문서는 저장 시에만)

**사용자 인자:** {{arguments}}

---

## 워크플로우 개요

```
Phase 0: 초기화 (프로젝트 루트, 기존 constitution 확인, 모드 결정)
     ↓
Phase 1: 팀 구성 + 프로젝트 분석 (팀메이트)
     ↓
Phase 2: 분석 결과 요약 + 사용자 확인/수정
     ↓
Phase 3: 저장 + 팀 해산 + 완료 안내
```

---

## 필수 실행 체크리스트

| Phase | Step | 필수 액션 | Tool |
|-------|------|----------|------|
| 0 | 3 | 이전 팀 정리 | Bash |
| 0 | 4 | 기존 태스크 정리 | TaskList, TaskUpdate |
| 0 | 5 | 태스크 등록 | TaskCreate |
| 1 | 1 | 팀 생성 | TeamCreate |
| 1 | 2 | role-templates 참조하여 팀 구성 | Skill |
| 1 | 3 | 팀메이트 스폰 + 분석 지시 (researcher) | Task tool |
| 1 | 4 | 결과 수집 | SendMessage 수신 |
| 2 | 2 | 사용자 확인 | AskUserQuestion |
| 3 | 1 | 사용자 승인 후 파일 저장 | Write |
| 3 | 3 | 팀 해산 | SendMessage (shutdown), TeamDelete |

**금지 사항:**
- 리더가 직접 코드베이스 분석 수행
- 리더가 직접 설정 파일 파싱
- 사용자 승인 없이 파일 저장
- 전체 문서 출력 (요약만 표시)
- spawn-teammate Skill 사용

---

## Phase 0: 초기화

### Step 1: 프로젝트 루트 탐색

cwd부터 상위로 올라가며 탐색:
1. `.specify/` - 기존 폴더가 있으면 **최우선**
2. `package.json`, `pyproject.toml`, `Cargo.toml`, `go.mod`, `build.gradle`
3. `.git/` - 최후의 fallback

```
AskUserQuestion:
- question: "프로젝트 루트가 [탐색 경로]이 맞나요?"
- options:
  - "예, 맞습니다 (권장)"
  - "다른 경로 지정"
```

탐색된 경로를 `PROJECT_ROOT`로 저장.

### Step 2: 모드 결정

**기존 constitution 확인:**

```
Read tool:
- file_path: ${PROJECT_ROOT}/.specify/memory/constitution.md
```

| 조건 | 모드 |
|------|------|
| 파일 없음 | **초기화 모드** |
| 파일 있음 + `--update` 인자 | **업데이트 모드** |
| 파일 있음 + 인자 없음 | AskUserQuestion으로 선택 |

**파일 있고 인자 없을 때:**

```
AskUserQuestion:
- question: "기존 Constitution이 있습니다. 어떻게 하시겠어요?"
- header: "모드 선택"
- options:
  - label: "업데이트 (권장)"
    description: "변경사항만 감지하여 업데이트"
  - label: "처음부터 다시"
    description: "기존 삭제 후 새로 분석"
  - label: "현재 내용 확인만"
    description: "기존 constitution 표시"
```

#### "현재 내용 확인만" 선택 시

기존 constitution.md 내용을 표시하고 종료.

### Step 3: Pre-Flight Team Cleanup

이전 실행에서 남은 팀이 있으면 자동 정리합니다.

**탐지:**
```bash
TEAM_NAME="constitution"
[ -d ~/.claude/teams/$TEAM_NAME ] && echo "exists" || echo "none"
```

**팀이 존재하면 자동 정리:**

```bash
CONFIG="$HOME/.claude/teams/$TEAM_NAME/config.json"
if [ -f "$CONFIG" ]; then
  # 활성 멤버 tmux pane 종료
  jq -r '.members[] | select(.isActive==true and .tmuxPaneId!=null and .tmuxPaneId!="") | .tmuxPaneId' "$CONFIG" 2>/dev/null | while read -r pane_id; do
    tmux kill-pane -t "$pane_id" 2>/dev/null || true
  done
  # window 모드 정리
  tmux list-windows -a -F "#{window_id} #{window_name}" 2>/dev/null | grep "${TEAM_NAME}-" | while read -r wid _; do
    tmux kill-window -t "$wid" 2>/dev/null || true
  done
fi
# 디렉토리 정리
rm -rf "$HOME/.claude/teams/$TEAM_NAME"
rm -rf "$HOME/.claude/tasks/$TEAM_NAME"
```

사용자에게 알림:
```markdown
> 이전 실행의 팀 `constitution`을 정리했습니다.
```

팀이 없으면 아무 것도 표시하지 않고 Step 4로 진행.

### Step 4: 기존 태스크 정리

```
TaskList tool -> 기존 태스크 확인
TaskUpdate tool (각 기존 태스크) -> status: "deleted"
```

### Step 5: 태스크 등록

```
TaskCreate (3회):

1. subject: "Phase 1: 팀 구성 + 프로젝트 분석"
   description: "팀 생성, researcher에게 프로젝트 분석 위임"
   activeForm: "프로젝트 분석 중"

2. subject: "Phase 2: 분석 결과 확인"
   description: "분석 결과 요약 표시, 사용자 확인/수정"
   activeForm: "분석 결과 확인 중"

3. subject: "Phase 3: 저장 + 완료"
   description: "constitution.md 저장, 팀 해산"
   activeForm: "저장 중"
```

---

## Phase 1: 팀 구성 + 프로젝트 분석

### Step 1: 팀 생성

```
TeamCreate tool:
- team_name: "constitution"
- description: "Constitution: 프로젝트 원칙 분석"
```

### Step 2: role-templates 참조

```
Skill tool:
- skill: "oh-my-speckit:role-templates"
```

### Step 3: researcher 스폰 + 분석 지시

Task tool로 researcher를 스폰합니다. prompt에 작업 지시를 직접 포함하므로 별도 SendMessage가 불필요합니다.

**역할-에이전트 매핑:**

| 역할 | subagent_type |
|------|--------------|
| researcher | claude-team:researcher |

#### 초기화 모드

```
Task tool:
- subagent_type: "claude-team:researcher"
- team_name: "constitution"
- name: "researcher"
- description: "researcher: 프로젝트 분석"
- run_in_background: true
- prompt: |
    프로젝트를 분석하여 Constitution(기술 원칙 문서)을 작성해주세요.

    프로젝트 루트: {PROJECT_ROOT}

    **분석 항목 (모두 필수):**

    1. **기술 스택**: 언어, 프레임워크, 빌드 도구, 패키지 매니저, 주요 라이브러리
       - 설정 파일: package.json, pyproject.toml, build.gradle, Cargo.toml, go.mod 등
       - 버전 정보 포함

    2. **아키텍처 패턴**: 채택된 패턴 (Layered, Clean, DDD, Vertical Slice 등)
       - 디렉토리 구조에서 패턴 유추
       - 주요 모듈/패키지 구조

    3. **금지된 패턴**: 린트 규칙, 프로젝트 규칙에서 금지된 사항
       - .eslintrc, .pylintrc, rustfmt.toml 등에서 추출

    4. **코딩 컨벤션**: 네이밍 규칙, 포맷팅, 타입 안전성 수준
       - .editorconfig, prettier, eslint 스타일 규칙 등
       - 기존 코드에서 실제 사용 패턴 관찰

    5. **테스트 요구사항**: 테스트 프레임워크, 커버리지 설정, 테스트 구조
       - jest.config, vitest.config, pytest.ini 등
       - 기존 테스트 파일 구조

    6. **API 전략**: API 버전 관리 방식, 라우팅 패턴
       - 기존 API 엔드포인트 구조에서 유추

    7. **보안/성능**: 환경변수 관리, 인증 방식, 성능 관련 설정

    **출력 형식** (이 형식 그대로 보고해주세요):

    ```
    SECTION: 기술 스택
    - 언어: [값]
    - 프레임워크: [값]
    - 빌드 도구: [값]
    - 패키지 매니저: [값]
    - 주요 라이브러리: [값]

    SECTION: 아키텍처 패턴
    - 채택된 패턴: [값]
    - 디렉토리 구조 규칙: [값]

    SECTION: 금지된 패턴
    - [항목]: [이유]

    SECTION: 코딩 컨벤션
    - 네이밍: [값]
    - 포맷: [값]
    - 타입 안전성: [값]

    SECTION: 테스트 요구사항
    - 프레임워크: [값]
    - 커버리지 목표: [값 또는 "미설정"]
    - 필수 테스트 유형: [값]

    SECTION: API 전략
    - 버전 관리 방식: [값 또는 "미확인"]
    - Breaking Change 처리: [값 또는 "미확인"]

    SECTION: 보안/성능
    - 환경변수 관리: [값]
    - 인증 방식: [값 또는 "미확인"]
    ```

    **규칙:**
    - 확인할 수 없는 항목은 "미확인" 으로 표시 (추측 금지)
    - 설정 파일 기반 사실만 기록
    - 기존 코드 패턴 관찰 결과도 포함 가능 (단, "코드 관찰" 명시)
    - 완료되면 리더에게 전체 결과를 보고해주세요
```

#### 업데이트 모드

```
Task tool:
- subagent_type: "claude-team:researcher"
- team_name: "constitution"
- name: "researcher"
- description: "researcher: 업데이트 분석"
- run_in_background: true
- prompt: |
    기존 Constitution과 현재 프로젝트 상태를 비교 분석해주세요.

    프로젝트 루트: {PROJECT_ROOT}
    기존 Constitution 경로: ${PROJECT_ROOT}/.specify/memory/constitution.md

    **작업:**
    1. 기존 constitution.md를 먼저 읽으세요
    2. 현재 프로젝트의 설정 파일과 코드 구조를 분석하세요
    3. 기존 내용과 달라진 부분을 찾아주세요

    **보고 형식:**

    ```
    UNCHANGED: [변경 없는 섹션 목록]

    CHANGED:
    - 섹션: [섹션명]
      기존: [이전 값]
      현재: [현재 값]
      이유: [변경 감지 근거]

    ADDED:
    - 섹션: [섹션명]
      내용: [새로 추가할 내용]
      이유: [추가 근거]

    REMOVED:
    - 섹션: [섹션명]
      이유: [제거 근거]
    ```

    변경이 없으면 "NO_CHANGES" 로 보고해주세요.
    완료되면 리더에게 결과를 보고해주세요.
```

### Step 4: 결과 수집

researcher의 SendMessage를 수신하여 결과를 정리합니다.

**Phase 1 완료 시:** TaskUpdate로 Phase 1 태스크를 `completed`로 변경

---

## Phase 2: 분석 결과 요약 + 사용자 확인

### Step 1: 요약 표시

#### 초기화 모드

```markdown
## Constitution 분석 결과

| 섹션 | 주요 내용 |
|------|----------|
| 기술 스택 | [언어], [프레임워크] |
| 아키텍처 | [패턴명] |
| 컨벤션 | [핵심 규칙 1-2개] |
| 테스트 | [프레임워크], 커버리지 [N%] |
| API | [전략] |
```

#### 업데이트 모드

**변경 없을 때:**
```markdown
## Constitution 업데이트 분석

변경사항이 없습니다. 현재 Constitution이 최신 상태입니다.
```
→ Phase 3으로 직행 (저장 불필요, 팀 해산만)

**변경 있을 때:**
```markdown
## Constitution 변경 감지

| 변경 유형 | 섹션 | 내용 |
|----------|------|------|
| 변경 | [섹션] | [기존] → [현재] |
| 추가 | [섹션] | [내용] |
| 삭제 | [섹션] | [이유] |
```

### Step 2: 사용자 확인

```
AskUserQuestion:
- question: "분석 결과를 확인해주세요. 수정할 부분이 있나요?"
- header: "Constitution 확인"
- options:
  - label: "이대로 저장 (권장)"
    description: "분석 결과 그대로 constitution.md 저장"
  - label: "전체 내용 확인 후 저장"
    description: "전체 문서를 먼저 확인"
  - label: "수정 필요"
    description: "특정 부분 수정 요청"
  - label: "저장 안함"
    description: "저장하지 않고 종료"
```

#### "전체 내용 확인 후 저장" 선택 시

메모리에 준비된 constitution.md 전체 내용을 표시한 후 저장 여부 재확인.

#### "수정 필요" 선택 시

사용자가 수정할 부분을 직접 말하도록 안내 → 수정 후 Step 2로 돌아감.

#### "저장 안함" 선택 시

```markdown
Constitution이 저장되지 않았습니다.
나중에 다시 시작하려면: /oh-my-speckit:constitution
```
→ Phase 3 (팀 해산만)

**Phase 2 완료 시:** TaskUpdate로 Phase 2 태스크를 `completed`로 변경

---

## Phase 3: 저장 + 팀 해산 + 완료 안내

### Step 1: 파일 저장

**디렉토리 생성:**
```bash
mkdir -p ${PROJECT_ROOT}/.specify/memory
```

**Constitution 작성 및 저장:**

researcher 분석 결과 + 사용자 수정사항을 반영하여 constitution.md를 작성합니다.

```
Write tool:
- file_path: ${PROJECT_ROOT}/.specify/memory/constitution.md
- content: [완성된 constitution 내용]
```

**Constitution 파일 형식:**

```markdown
# Project Constitution

> 자동 생성: {오늘 날짜} | 마지막 업데이트: {오늘 날짜}

## 기술 스택
- 언어: {값}
- 프레임워크: {값}
- 빌드 도구: {값}
- 패키지 매니저: {값}
- 주요 라이브러리: {값}

## 아키텍처 패턴
- 채택된 패턴: {값}
- 디렉토리 구조 규칙: {값}

## 금지된 패턴
- {항목}: {이유}

## 코딩 컨벤션
- 네이밍: {값}
- 포맷: {값}
- 타입 안전성: {값}

## 테스트 요구사항
- 프레임워크: {값}
- 커버리지 목표: {값}
- 필수 테스트 유형: {값}

## API 전략
- 버전 관리 방식: {값}
- Breaking Change 처리: {값}

## 보안/성능
- 환경변수 관리: {값}
- 인증 방식: {값}
```

**업데이트 모드 시:** 기존 파일의 "자동 생성" 날짜는 유지하고 "마지막 업데이트" 날짜만 갱신.

### Step 2: 팀메이트 종료 + tmux 정리 + 팀 삭제

researcher에게 shutdown_request를 전송합니다:

```
SendMessage tool:
- type: "shutdown_request"
- recipient: "researcher"
- content: "Constitution 작성 완료, 팀을 해산합니다."
```

shutdown_request 전송 후 tmux pane/window를 정리하고 팀을 삭제합니다:

```bash
TEAM_NAME="constitution"
CONFIG="$HOME/.claude/teams/$TEAM_NAME/config.json"
if [ -f "$CONFIG" ]; then
  jq -r '.members[] | select(.isActive==true and .tmuxPaneId!=null and .tmuxPaneId!="") | .tmuxPaneId' "$CONFIG" 2>/dev/null | while read -r pane_id; do
    tmux kill-pane -t "$pane_id" 2>/dev/null || true
  done
  tmux list-windows -a -F "#{window_id} #{window_name}" 2>/dev/null | grep "${TEAM_NAME}-" | while read -r wid _; do
    tmux kill-window -t "$wid" 2>/dev/null || true
  done
fi
```

```
TeamDelete tool
```

### Step 4: 완료 안내

#### 초기화 모드

```markdown
## Constitution 생성 완료

**위치**: .specify/memory/constitution.md

### 요약
| 섹션 | 항목 수 |
|------|--------|
| 기술 스택 | N개 |
| 아키텍처 패턴 | N개 |
| 금지된 패턴 | N개 |
| 코딩 컨벤션 | N개 |
| 테스트 요구사항 | N개 |
| API 전략 | N개 |
| 보안/성능 | N개 |

### 활용
- specify, implement, verify 커맨드 실행 시 자동으로 참조됩니다
- 프로젝트 변경 시: /oh-my-speckit:constitution --update
```

#### 업데이트 모드

```markdown
## Constitution 업데이트 완료

**위치**: .specify/memory/constitution.md

### 변경 내역
| 유형 | 섹션 | 변경 내용 |
|------|------|----------|
| 변경 | [섹션] | [요약] |
| 추가 | [섹션] | [요약] |
```

### Step 5: 태스크 정리

```
TaskList -> 현재 태스크 확인
TaskUpdate (각 태스크) -> status: "deleted"
```

**Phase 3 완료 시:** TaskUpdate로 Phase 3 태스크를 `completed`로 변경

---

## 재진입 시

기존 constitution이 있고 `--update` 없이 실행한 경우:
- Phase 0 Step 2에서 모드 선택 질문으로 처리

---

## 참고: Constitution이 사용되는 곳

| 커맨드 | Phase | 용도 |
|--------|-------|------|
| specify | Phase 0.5 | pm에게 전달 → spec 작성 시 원칙 반영 |
| implement (plan-writing) | Phase 1.5 | plan.md에 아키텍처/금지 패턴/테스트 요구사항 반영 |
| implement (code-generation) | Phase 1.5 | 코드 구현 시 금지 패턴/컨벤션/타입 안전성 적용 |
| verify | Phase 0.2 | qa에게 전달 → 검증 기준에 포함 |
