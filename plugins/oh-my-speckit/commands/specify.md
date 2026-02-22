---
description: 기능 요청을 spec.md + plan.md로 통합 생성 (Agent Teams)
argument-hint: [기능 설명] [--gpt] [--no-gpt] [--window] [--no-window]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, AskUserQuestion, Task, Skill, TaskCreate, TaskUpdate, TaskList, TeamCreate, TeamDelete, SendMessage
---

# Specify Command

기능 요청을 분석하여 spec.md와 plan.md를 한 번에 생성합니다.
Agent Teams 기반으로 팀을 구성하고, 팀메이트에게 분석/조사를 위임합니다.

**핵심 원칙**:
- **리더(이 커맨드)는 사용자와 소통하고 팀을 조율** - 코드베이스 직접 분석 금지
- **높은 자율성**: 리더는 고수준 목표만 전달, 팀메이트가 세부사항 자율 결정
- **팀메이트 간 직접 소통**: 팀메이트끼리 SendMessage로 직접 협업
- **중요 결정만 질문** (기술 선택, Breaking Change 등)
- **세부사항은 AI가 결정**
- **요약 중심 출력** (전체 문서는 저장 시에만)

**사용자 요청:** {{arguments}}

---

## 워크플로우 개요

```
Phase 0: 초기화 (프로젝트 루트, constitution, spec-id)
     ↓
Phase 1: 팀 구성 + 컨텍스트 수집 (팀메이트 병렬)
     ↓
Phase 2: 방향성 합의 ← 중요 결정만 질문
     ↓
Phase 3: Spec + Plan 자동 작성
     ↓
Phase 4: 최종 확인 및 저장 ← 요약 표시 + 승인
     ↓
Phase 5: 팀 해산 + 완료 안내
```

---

## 필수 실행 체크리스트

| Phase | Step | 필수 액션 | Tool |
|-------|------|----------|------|
| 0 | 3 | 기존 태스크 정리 | TaskList, TaskUpdate |
| 0 | 4 | 태스크 등록 | TaskCreate |
| 1 | 1 | 팀 생성 | TeamCreate |
| 1 | 2 | role-templates 참조하여 팀 구성 판단 | Skill |
| 1 | 3 | 팀메이트 스폰 (pm, architect 등) | Skill (spawn-teammate) |
| 4 | 2 | 사용자 승인 후 파일 저장 | Write |
| 5 | 1 | 팀 해산 | SendMessage (shutdown), TeamDelete |

**금지 사항:**
- 리더가 직접 코드베이스 분석 수행
- 리더가 직접 WebSearch/WebFetch 사용
- 섹션별 확인 질문 (한 번에 작성 후 최종 승인만)
- 전체 문서 출력 (요약만 표시)
- 사용자 승인 없이 파일 저장

---

## Phase 0: 초기화

### Step 1: 스킬 로드

```
Skill tool:
- skill: "oh-my-speckit:spec-writing"
```

spec-writing 스킬의 지식(US/FR/NFR/EC 작성 가이드, spec 구조)을 로드합니다.

### Step 2: 프로젝트 루트 탐색

cwd부터 상위로 올라가며 탐색:
1. `.specify/` - 기존 폴더가 있으면 **최우선**
2. `package.json`, `pyproject.toml`, `Cargo.toml`, `go.mod`
3. `.git/` - 최후의 fallback

```
AskUserQuestion:
- question: "프로젝트 루트가 [탐색 경로]이 맞나요?"
- options:
  - "예, 맞습니다 (권장)"
  - "다른 경로 지정"
```

탐색된 경로를 `PROJECT_ROOT`로 저장.

### Step 2.5: Constitution 확인

```
Read tool:
- file_path: ${PROJECT_ROOT}/.specify/memory/constitution.md
```

- constitution.md 있으면 -> 원칙을 스펙/설계 작성에 반영
- 없으면 -> 선택적으로 생성 안내

### Step 2.6: Spec ID 결정

**1) 다음 번호 자동 계산 (Bash):**

```bash
NEXT_ID=$(ls -1d ${PROJECT_ROOT}/.specify/specs/[0-9][0-9][0-9]-* 2>/dev/null \
  | sed 's/.*\/\([0-9]\{3\}\)-.*/\1/' \
  | sort -n | tail -1 \
  | awk '{printf "%03d\n", $1 + 1}')
[ -z "$NEXT_ID" ] && NEXT_ID="001"
echo $NEXT_ID
```

Bash 출력(예: `010`)을 NEXT_ID로 사용. **AI가 직접 계산하지 않는다.**

**2) 기능명 결정:** 사용자 요청에서 핵심 기능명을 추출 → **kebab-case**

**3) 최종 ID 조합:** `{NEXT_ID}-{feature-name}` (예: `010-user-authentication`)

**4) 중복 방지 검증 (Bash):**

```bash
ls -1d ${PROJECT_ROOT}/.specify/specs/${NEXT_ID}-* 2>/dev/null
```

- 출력 없음 → 사용 가능
- 출력 있음 → NEXT_ID를 +1 증가 후 재검증

### Step 2.7: 스폰 모드 설정

**Config 읽기:**
```
Read tool: ${PROJECT_ROOT}/.specify/config.json
```
파일이 없거나 읽기 실패 시 `{}` 으로 간주.

**GPT_MODE 결정 (우선순위: CLI > config > default):**
1. arguments에 `--gpt` 포함 → GPT_MODE = true
2. arguments에 `--no-gpt` 포함 → GPT_MODE = false
3. 위 둘 다 없으면 → config의 `spawnGpt` 필드가 `true` → GPT_MODE = true
4. 기본값 → GPT_MODE = false

GPT_MODE일 때: spawn-teammate에 `--agent-type` 없이 호출
GPT_MODE 아닐 때: spawn-teammate에 `--agent-type` 지정

**WINDOW_MODE 결정 (우선순위: CLI > config > default):**
1. arguments에 `--window` 포함 → WINDOW_MODE = true
2. arguments에 `--no-window` 포함 → WINDOW_MODE = false
3. 위 둘 다 없으면 → config의 `spawnWindow` 필드가 `true` → WINDOW_MODE = true
4. 기본값 → WINDOW_MODE = false

WINDOW_MODE일 때: spawn-teammate에 `--window` 전달

### Step 3: 기존 태스크 정리

```
TaskList tool -> 기존 태스크 확인
TaskUpdate tool (각 기존 태스크) -> status: "deleted"
```

### Step 4: 태스크 등록

```
TaskCreate (5회):

1. subject: "Phase 1: 팀 구성 + 컨텍스트 수집"
   description: "팀 생성, 팀메이트 위임으로 요구사항 분석/코드베이스 분석/기술 조사"
   activeForm: "컨텍스트 수집 중"

2. subject: "Phase 2: 방향성 합의"
   description: "조사 결과 기반 중요 결정 확인"
   activeForm: "방향성 논의 중"

3. subject: "Phase 3: Spec + Plan 작성"
   description: "조사 결과 + 결정사항 기반 문서 자동 작성"
   activeForm: "문서 작성 중"

4. subject: "Phase 4: 최종 확인 및 저장"
   description: "요약 표시 후 사용자 승인, 파일 저장"
   activeForm: "최종 확인 중"

5. subject: "Phase 5: 완료 안내"
   description: "팀 해산, 완료 요약, 다음 단계 안내"
   activeForm: "완료 처리 중"
```

---

## Phase 1: 팀 구성 + 컨텍스트 수집

### Step 1: 팀 생성

```
TeamCreate tool:
- team_name: "specify-{spec-id}"
- description: "Specify {spec-id}: 요구사항 분석 + 설계"
```

### Step 2: 프로젝트 규모 판단 + 팀 구성

role-templates 스킬을 참조하여 프로젝트 규모 판단:

```
Skill tool:
- skill: "oh-my-speckit:role-templates"
```

사용자 요청 내용을 기반으로 규모 판단:

| 규모 | 기준 | 팀 구성 |
|------|------|--------|
| Small | 단일 기능, 파일 5개 미만 | pm 1명 |
| Medium | 복합 기능, 파일 5-15개 | pm + architect |
| Large | 대규모 기능, 파일 15개+ | pm + architect + critic |

### Step 3: 팀메이트 스폰 (병렬)

spawn-teammate Skill로 팀메이트를 스폰한 뒤, SendMessage로 고수준 목표를 전달합니다.
팀메이트는 자율적으로 세부 분석을 수행하고, 필요시 팀메이트 간 직접 소통합니다.

**역할-에이전트 매핑:**

| 역할 | 에이전트 타입 (Claude 모드) |
|------|--------------------------|
| pm | claude-team:planner |
| architect | claude-team:architect |
| critic | claude-team:reviewer |

**pm 스폰 (필수):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "pm --team specify-{spec-id} --agent-type claude-team:planner"
  (GPT_MODE일 때: "pm --team specify-{spec-id}")
  (WINDOW_MODE일 때 끝에 --window 추가)

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "pm"
- content: |
    사용자가 다음 기능을 요청했습니다: {사용자 요청 원문}

    프로젝트 루트: {PROJECT_ROOT}
    constitution 규칙: {constitution 내용 또는 "없음"}

    이 기능에 대한 요구사항을 분석하고 US/FR/NFR/EC로 구조화해주세요.
    architect 팀메이트가 있으면 기술적 타당성을 협의하세요.
    완료되면 리더에게 결과를 보고해주세요.
- summary: "pm 작업 지시"
```

**architect 스폰 (Medium 이상):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "architect --team specify-{spec-id} --agent-type claude-team:architect"
  (GPT_MODE일 때: "architect --team specify-{spec-id}")
  (WINDOW_MODE일 때 끝에 --window 추가)

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "architect"
- content: |
    사용자가 다음 기능을 요청했습니다: {사용자 요청 요약}

    프로젝트 루트: {PROJECT_ROOT}

    코드베이스를 분석하고 아키텍처 관점에서 이 기능의 설계 방향을 제안해주세요.
    pm 팀메이트와 요구사항의 기술적 타당성을 협의하세요.
    완료되면 리더에게 결과를 보고해주세요.
- summary: "architect 작업 지시"
```

**critic 스폰 (Large만):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "critic --team specify-{spec-id} --agent-type claude-team:reviewer"
  (GPT_MODE일 때: "critic --team specify-{spec-id}")
  (WINDOW_MODE일 때 끝에 --window 추가)

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "critic"
- content: |
    사용자가 다음 기능을 요청했습니다: {사용자 요청 요약}

    프로젝트 루트: {PROJECT_ROOT}

    pm과 architect의 분석 결과를 비판적으로 검토해주세요.
    팀메이트들과 직접 소통하여 도전 질문, 리스크, 대안을 논의하세요.
    완료되면 리더에게 Devil's Advocate Review를 보고해주세요.
- summary: "critic 작업 지시"
```

### Step 4: 팀메이트 결과 수집

팀메이트들의 SendMessage를 수신하여 결과를 정리합니다.
모든 팀메이트의 보고가 완료될 때까지 대기합니다.

### Step 5: 불명확한 부분 사전 질문

pm의 분석에서 불명확한 부분이 있으면 AskUserQuestion으로 해소:

| 불명확한 부분 | 질문 예시 |
|-------------|----------|
| 기술 선택 | "인증 방식: JWT vs 세션 중 어떤 것을 선호하시나요?" |
| 범위 경계 | "관리자 기능도 이번 범위에 포함할까요?" |
| 우선순위 | "실시간 알림은 필수인가요, 선택인가요?" |

검색 결과 기반 권장 옵션 제시:
```
AskUserQuestion:
- question: "[결정 필요 사항]을 선택해주세요."
- options:
  - label: "[옵션 A] (권장)"
    description: "[근거 포함 한 줄 설명]"
  - label: "[옵션 B]"
    description: "[한 줄 설명]"
```

### Step 6: API 변경 전략 논의 (해당시)

기존 API/로직 변경이 감지되면:
```
AskUserQuestion:
- question: "기존 API/로직 변경이 감지되었습니다. 어떤 전략으로 진행할까요?"
- header: "변경 전략"
- options:
  - label: "V2 신규 생성 (권장)"
    description: "기존 API 유지 + V2 신규 생성. Breaking Change 방지"
  - label: "기존 로직 직접 수정"
    description: "기존 API 직접 변경. 모든 클라이언트 동시 수정 필요"
  - label: "하위 호환 유지"
    description: "optional 필드 추가 등으로 기존 호환성 유지"
```

**Phase 1 완료 시:** TaskUpdate로 Phase 1 태스크를 `completed`로 변경

---

## Phase 2: 방향성 합의

**핵심 원칙**: 중요 결정만 질문, 나머지는 AI가 자동 결정

### Step 1: 조사 결과 요약 표시

```markdown
## 조사 결과 요약

**코드베이스**: [기존 패턴 한 줄], [재사용 가능 여부]
**기술 권장**: [베스트 프랙티스 한 줄]
**제안 방향**: [A] vs [B] - [핵심 차이점]
```

### Step 2: 중요 결정만 질문

Phase 1에서 해소되지 않은 중요 결정이 있으면 AskUserQuestion으로 확인.
이미 Phase 1에서 모든 결정이 완료되었다면 바로 Phase 3로 진행.

### Step 3: 결정 기록

기술 결정을 기록:
```markdown
| ID | 결정 항목 | 선택 | 근거 | 결정일 |
```

**Phase 2 완료 시:** TaskUpdate로 Phase 2 태스크를 `completed`로 변경

---

## Phase 3: Spec + Plan 자동 작성

### Step 1: plan-writing 스킬 로드

```
Skill tool:
- skill: "oh-my-speckit:plan-writing"
```

plan-writing 스킬의 지식(plan.md 구조, FR 매핑, 재사용 분석)을 로드합니다.

### Step 2: Spec 작성

Phase 1 팀메이트 결과 + Phase 2 결정사항을 기반으로 spec.md를 메모리에 작성:

- 개요 및 메타데이터
- 사용자 스토리 (US)
- 기능 요구사항 (FR)
- 비기능 요구사항 (NFR)
- 엣지 케이스 (EC)
- 기술 결정 (Phase 2에서 결정된 사항)

> spec 템플릿: skills/spec-writing/references/spec-template.md 참조

### Step 3: Plan 작성

Spec + architect 분석결과를 기반으로 plan.md를 메모리에 작성:

- 설계 방향 및 개요
- FR 매핑 테이블
- 재사용 분석 (architect 결과 기반)
- 변경 파일 목록
- 구현 단계 (Phase별 Task + 체크박스)
- E2E 테스트 시나리오
- Breaking Change 섹션 (해당시)

> plan 템플릿: skills/plan-writing/references/plan-template.md 참조

Large 규모에서 critic 결과가 있으면 Devil's Advocate Review를 반영합니다:
- 도전 질문에 대한 응답을 설계에 반영
- 식별된 리스크에 대한 대응 방안을 plan에 포함
- 누락 항목을 spec에 추가

### Step 4: 작성 완료 알림

**전체 문서 출력 금지** - 요약만 표시:

```markdown
## Spec + Plan 작성 완료

**기능명**: [기능명]
**Spec ID**: {spec-id}

### Spec 요약
- 사용자 스토리: N개
- 기능 요구사항: N개 (P1: X개, P2: Y개)
- 비기능 요구사항: N개
- 엣지 케이스: N개
- 기술 결정: N개

### Plan 요약
- 구현 단계: N개 Phase
- 총 Task: N개
- 재사용 코드: N건
- 변경 파일: N개
- Breaking Change: 있음/없음

-> Phase 4에서 저장 여부를 확인합니다
```

**Phase 3 완료 시:** TaskUpdate로 Phase 3 태스크를 `completed`로 변경

---

## Phase 4: 최종 확인 및 저장

### Step 1: 최종 승인 질문

```
AskUserQuestion:
- question: "Spec과 Plan을 저장할까요?"
- header: "최종 승인"
- options:
  - label: "저장 (권장)"
    description: ".specify/specs/{id}/에 spec.md + plan.md 저장"
  - label: "전체 내용 확인 후 저장"
    description: "전체 문서를 먼저 확인"
  - label: "수정 필요"
    description: "특정 부분 수정 요청"
  - label: "저장 안함"
    description: "저장하지 않고 종료"
```

#### "저장" 선택 시

```
Bash: mkdir -p ${PROJECT_ROOT}/.specify/specs/{spec-id}

Write tool:
- file_path: ${PROJECT_ROOT}/.specify/specs/{spec-id}/spec.md
- content: [완성된 spec 내용]

Write tool:
- file_path: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md
- content: [완성된 plan 내용]
```

#### "전체 내용 확인 후 저장" 선택 시

전체 spec.md, plan.md 내용을 표시한 후 저장 여부 재확인.

#### "수정 필요" 선택 시

사용자가 수정할 부분을 직접 말하도록 안내 -> 수정 후 Step 1로 돌아감.

#### "저장 안함" 선택 시

```markdown
Spec/Plan이 저장되지 않았습니다.
나중에 다시 시작하려면: /oh-my-speckit:specify [기능 설명]
```

**Phase 4 완료 시:** TaskUpdate로 Phase 4 태스크를 `completed`로 변경

---

## Phase 5: 팀 해산 + 완료 안내

### Step 1: 팀메이트 종료

각 팀메이트에게 shutdown_request 전송:

```
SendMessage tool:
- type: "shutdown_request"
- recipient: "pm"
- content: "Specify 완료, 팀을 해산합니다."

(architect, critic도 동일 — 생성된 팀메이트만)
```

### Step 2: 팀 삭제

```
TeamDelete tool
```

### Step 3: 완료 안내

```markdown
## Specify 완료

**위치**: .specify/specs/{spec-id}/
**Spec ID**: {spec-id}

### 요약
- 사용자 스토리: N개
- 기능 요구사항: N개
- 구현 Phase: N개
- 총 Task: N개

### 다음 단계
-> 코드 구현: /oh-my-speckit:implement {spec-id}
```

### Step 4: 태스크 정리

```
TaskList -> 현재 태스크 확인
TaskUpdate (각 태스크) -> status: "deleted"
```

---

## 재진입 시 (spec-id 지정됨)

기존 spec 수정 요청인 경우:

1. 기존 spec.md, plan.md 로드
2. AskUserQuestion: "기존 문서가 있습니다. 어떻게 하시겠어요?"
   - "처음부터 다시"
   - "기존 spec 수정"
   - "기존 plan만 수정"
   - "기존 spec 검토만"
3. 선택에 따라 진행

---

## 참고 사항

### 검색 도구 활용

pm 팀메이트가 직접 사용:
- Context7: 라이브러리/프레임워크 공식 문서
- WebSearch: 일반 웹 검색, 최신 정보

### Spec/Plan 템플릿

- `skills/spec-writing/references/spec-template.md`
- `skills/plan-writing/references/plan-template.md`

### 권장 옵션 제시 원칙

- 첫 번째 옵션에 "(권장)" 표시
- 검색 결과 기반 근거 제시
- 2-3개 대안과 장단점
