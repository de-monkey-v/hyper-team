---
description: plan.md 기반 코드 구현 (Agent Teams, 자동/대화형)
argument-hint: [spec-id] [--interactive] [--gpt] [--window]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, AskUserQuestion, Task, Skill, TaskCreate, TaskUpdate, TaskList, TeamCreate, TeamDelete, SendMessage
---

# Implement Command

spec.md와 plan.md를 기반으로 코드를 구현합니다.
Agent Teams 기반으로 팀을 구성하고, 팀메이트에게 구현/테스트를 위임합니다.

**핵심 원칙**:
- **리더(이 커맨드)는 사용자와 소통하고 팀을 조율** - 코드 직접 작성 금지
- **모든 코드 작성/수정은 팀메이트(developer, qa)가 수행**
- **Phase Group 단위로 구현 -> 검증 반복**
- **자동 모드(기본): 에러 시에만 중단 / 대화형 모드: Group별 확인**
- **높은 자율성**: 리더는 고수준 목표만 전달, 팀메이트가 세부사항 자율 결정
- **팀메이트 간 직접 소통**: developer와 qa가 SendMessage로 직접 협업

**Spec ID:** {{arguments}}

---

## 모드 선택

| 모드 | 옵션 | 설명 |
|------|------|------|
| **자동 모드** | (기본값) | 중단 없이 끝까지 자동 구현. 에러 시에만 중단. |
| **대화형 모드** | `--interactive` | 각 Phase Group 완료 후 사용자 확인. |

---

## 워크플로우 개요

```
Phase 0: 초기화 (Spec/Plan 로드, 모드 선택, 진행 상황 확인)
     ↓
Phase 1: 팀 구성 + 구현 계획 확인
     ↓
Phase 2: 구현 루프 (Group별: developer 코딩 + qa 검증 — 직접 소통)
     ↓
Phase 3: 통합 테스트
     ↓
Phase 4: 마무리 (완료 요약, 팀 해산)
```

---

## 필수 실행 체크리스트

| Phase | Step | 필수 액션 | Tool |
|-------|------|----------|------|
| 0 | 3 | 기존 태스크 정리 | TaskList, TaskUpdate |
| 0 | 4 | 태스크 등록 | TaskCreate |
| 1 | 1 | 팀 생성 | TeamCreate |
| 1 | 3 | 팀메이트 스폰 (developer, qa 등) | Skill (spawn-teammate) |
| 2 | 2 | 구현 + 검증 지시 | SendMessage to developer |
| 3 | 1 | 전체 테스트 위임 | SendMessage to qa |
| 4 | 2 | 팀 해산 | SendMessage (shutdown), TeamDelete |

**금지 사항:**
- 리더가 직접 코드 작성
- 리더가 직접 테스트 실행
- 리더가 직접 품질 검사 수행
- plan.md의 체크박스를 "verify에서 확인할 항목"으로 건너뛰기 - 모든 체크박스는 implement에서 100% 완료

---

## Phase 0: 초기화

### Step 1: 스킬 로드

```
Skill tool:
- skill: "oh-my-speckit:code-generation"
```

code-generation 스킬의 지식(기존 코드 우선 원칙, 패턴 준수)을 로드합니다.

### Step 2: Spec/Plan 로드

**Spec ID 파싱:**
- arguments에서 spec-id 추출 (`--interactive`, `--gpt` 옵션 제거)
- `--interactive` 포함 -> AUTO_MODE = false

**스폰 모드 설정:**

arguments에서 `--gpt` 옵션 확인:
- `--gpt` 포함 → GPT_MODE = true (spawn-teammate에 `--agent-type` 없이 호출)
- 기본값 → GPT_MODE = false (spawn-teammate에 `--agent-type` 지정)

arguments에서 `--window` 옵션 확인:
- `--window` 포함 → WINDOW_MODE = true (spawn-teammate에 --window 전달)
- 기본값 → WINDOW_MODE = false

**spec-id 미지정 시:**
```
Glob tool:
- pattern: "${PROJECT_ROOT}/.specify/specs/*/spec.md"
```

spec 목록을 표시하고 AskUserQuestion으로 선택 요청.

**문서 로드:**
```
Read tool: ${PROJECT_ROOT}/.specify/specs/{spec-id}/spec.md
Read tool: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md
```

**plan.md가 없으면:** `/oh-my-speckit:specify {spec-id}` 안내 후 중단.

**파싱 항목:**
- FR 매핑 테이블
- 재사용 분석 섹션
- 변경 파일 목록
- 구현 단계 (Phase별 Task)
- 진행 상황 (이미 완료된 체크박스 `[x]`)

### Step 2.5: 진행 상황 확인 (재진입 지원)

plan.md에서 체크박스 진행률 확인:
- `[x]` 완료 개수 / 전체 체크박스 개수

**이미 진행 중인 경우:**

자동 모드:
```markdown
기존 진행 상황 감지 - 진행률: {N}%
완료된 Phase: Phase 1, 2
-> 미완료 Phase부터 자동 진행
```

대화형 모드:
```
AskUserQuestion:
- question: "기존 진행 상황이 있습니다 ({N}%). 어떻게 하시겠어요?"
- options:
  - "이어서 진행 (권장)"
  - "처음부터 다시"
```

### Step 2.7: 프레임워크 감지

프로젝트에서 사용 중인 프레임워크를 감지하여 전문 에이전트를 선택합니다.

**백엔드 프레임워크 감지:**

| 프레임워크 | 감지 파일 | 감지 키워드 | 결과 |
|-----------|----------|------------|------|
| Spring Boot | `pom.xml`, `build.gradle` | `spring-boot` | BACKEND_FRAMEWORK=spring |
| FastAPI | `requirements.txt`, `pyproject.toml` | `fastapi` | BACKEND_FRAMEWORK=fastapi |
| NestJS | `package.json` | `@nestjs/core` | BACKEND_FRAMEWORK=nestjs |

**프론트엔드 프레임워크 감지:**

| 프레임워크 | 감지 파일 | 감지 키워드 | 결과 |
|-----------|----------|------------|------|
| Next.js | `next.config.{js,ts,mjs}` | 파일 존재 | FRONTEND_FRAMEWORK=nextjs |
| Nuxt | `nuxt.config.{js,ts}` | 파일 존재 | FRONTEND_FRAMEWORK=nuxt |
| React | `package.json` | `"react"` (Next.js 미감지시) | FRONTEND_FRAMEWORK=react |
| Vue | `package.json` | `"vue"` (Nuxt 미감지시) | FRONTEND_FRAMEWORK=vue |

**감지 우선순위:** 메타프레임워크 우선 (Next.js > React, Nuxt > Vue)

```
Glob: pom.xml, build.gradle, build.gradle.kts
Glob: next.config.*, nuxt.config.*
Grep: package.json → "@nestjs/core", "react", "vue"
Grep: requirements.txt, pyproject.toml → "fastapi"
```

감지 결과는 Step 3의 역할-에이전트 매핑에 사용됩니다.

### Step 3: 기존 태스크 정리

```
TaskList tool -> 기존 태스크 확인
TaskUpdate tool (각 기존 태스크) -> status: "deleted"
```

### Step 4: 태스크 등록

```
TaskCreate (4회):

1. subject: "Phase 1: 팀 구성 + 구현 계획"
   description: "팀 생성, Phase Group 분류, 구현 준비"
   activeForm: "구현 준비 중"

2. subject: "Phase 2: 구현 루프"
   description: "Group별 코드 구현 + 즉시 검증 반복"
   activeForm: "코드 구현 중"

3. subject: "Phase 3: 통합 테스트"
   description: "전체 테스트 스위트 실행"
   activeForm: "통합 테스트 실행 중"

4. subject: "Phase 4: 마무리"
   description: "완료 요약, 팀 해산"
   activeForm: "마무리 중"
```

---

## Phase 1: 팀 구성 + 구현 계획 확인

### Step 1: 팀 생성

```
TeamCreate tool:
- team_name: "implement-{spec-id}"
- description: "Implement {spec-id}: 코드 구현"
```

### Step 2: role-templates 참조하여 팀 구성

```
Skill tool:
- skill: "oh-my-speckit:role-templates"
```

plan.md의 규모(변경 파일 수, Phase 수)를 기반으로 판단:

| 규모 | 기준 | 팀 구성 |
|------|------|--------|
| Small | 파일 5개 미만, Phase 3개 이하 | developer + qa |
| Medium | 파일 5-15개, Phase 4-6개 | developer x2 + qa |
| Large | 파일 15개+, Phase 7개+ | architect + developer x2 + qa |

### Step 2.5: Fullstack 프로젝트 감지

Medium/Large에서 developer x2가 필요한 경우, fullstack 프로젝트 여부를 판단:

```
1. Glob: **/package.json → 여러 개면 monorepo 의심
2. Glob: src/{frontend,client,web}/** → FE 디렉토리 존재?
3. Glob: src/{backend,server,api}/** → BE 디렉토리 존재?
4. Glob: apps/{web,frontend,client}/** → monorepo FE?
5. Glob: apps/{api,backend,server}/** → monorepo BE?
```

**판단:**
- FE + BE 디렉토리 모두 존재 → fullstack → developer x2 대신 **frontend-dev + backend-dev**
- 그 외 → **developer + developer-2** (동일 역할 병렬화)

### Step 3: 팀메이트 스폰

모든 팀메이트는 spawn-teammate Skill로 생성합니다. GPT_MODE에 따라 `--agent-type` 포함 여부만 달라집니다.

**역할-에이전트 매핑:**

| 역할 | 기본 에이전트 | 프레임워크 오버라이드 |
|------|-------------|---------------------|
| developer | claude-team:implementer | BACKEND: spring→spring-expert, fastapi→fastapi-expert, nestjs→nestjs-expert |
| frontend-dev | claude-team:frontend | FRONTEND: nextjs→nextjs-expert, nuxt→nuxt-expert, react→react-expert, vue→vue-expert |
| backend-dev | claude-team:backend | BACKEND: spring→spring-expert, nestjs→nestjs-expert, fastapi→fastapi-expert |
| developer-2 | claude-team:implementer | (developer와 동일한 오버라이드) |
| qa | claude-team:tester | (오버라이드 없음) |
| architect | claude-team:architect | (오버라이드 없음) |

프레임워크 감지 결과(Step 2.7)에 따라 에이전트 타입을 동적으로 선택합니다.
예: BACKEND_FRAMEWORK=spring → developer의 에이전트 타입이 `claude-team:spring-expert`로 변경

---

**developer 생성 (필수 — non-fullstack):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "developer --team implement-{spec-id} --agent-type claude-team:{DETECTED_AGENT}"
  (DETECTED_AGENT: BACKEND_FRAMEWORK 감지시 해당 전문가, 미감지시 implementer)
  (GPT_MODE일 때: "developer --team implement-{spec-id}")
  (WINDOW_MODE일 때 끝에 --window 추가)

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "developer"
- content: |
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md

    plan.md의 체크리스트를 순서대로 구현해주세요.
    재사용 분석 섹션을 먼저 확인하고, 기존 코드 패턴을 따르세요.
    완료된 항목은 plan.md 체크박스를 업데이트해주세요.
    qa 팀메이트와 구현 완료 시 검증을 협의하세요.
    완료되면 리더에게 변경 파일 목록과 결과를 보고해주세요.
- summary: "developer 작업 지시"
```

**frontend-dev 생성 (fullstack 프로젝트, Medium 이상):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "frontend-dev --team implement-{spec-id} --agent-type claude-team:{DETECTED_AGENT}"
  (DETECTED_AGENT: FRONTEND_FRAMEWORK 감지시 해당 전문가, 미감지시 frontend)
  (GPT_MODE일 때: "frontend-dev --team implement-{spec-id}")
  (WINDOW_MODE일 때 끝에 --window 추가)

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "frontend-dev"
- content: |
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md

    plan.md의 체크리스트 중 프론트엔드 관련 항목을 순서대로 구현해주세요.
    재사용 분석 섹션을 먼저 확인하고, 기존 코드 패턴을 따르세요.
    담당 영역: UI 컴포넌트, 페이지, 클라이언트 상태 관리, API 호출 레이어
    백엔드 코드는 직접 수정하지 마세요 (backend-dev 담당).
    완료된 항목은 plan.md 체크박스를 업데이트해주세요.
    qa 팀메이트와 구현 완료 시 검증을 협의하세요.
    완료되면 리더에게 변경 파일 목록과 결과를 보고해주세요.
- summary: "frontend-dev 작업 지시"
```

**backend-dev 생성 (fullstack 프로젝트, Medium 이상):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "backend-dev --team implement-{spec-id} --agent-type claude-team:{DETECTED_AGENT}"
  (DETECTED_AGENT: BACKEND_FRAMEWORK 감지시 해당 전문가, 미감지시 backend)
  (GPT_MODE일 때: "backend-dev --team implement-{spec-id}")
  (WINDOW_MODE일 때 끝에 --window 추가)

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "backend-dev"
- content: |
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md

    plan.md의 체크리스트 중 백엔드 관련 항목을 순서대로 구현해주세요.
    재사용 분석 섹션을 먼저 확인하고, 기존 코드 패턴을 따르세요.
    담당 영역: API 엔드포인트, 비즈니스 로직, DB 스키마/쿼리, 인증/인가
    프론트엔드 코드는 직접 수정하지 마세요 (frontend-dev 담당).
    완료된 항목은 plan.md 체크박스를 업데이트해주세요.
    qa 팀메이트와 구현 완료 시 검증을 협의하세요.
    완료되면 리더에게 변경 파일 목록과 결과를 보고해주세요.
- summary: "backend-dev 작업 지시"
```

**developer-2 생성 (non-fullstack, Medium 이상):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "developer-2 --team implement-{spec-id} --agent-type claude-team:{DETECTED_AGENT}"
  (DETECTED_AGENT: developer와 동일한 오버라이드 적용)
  (GPT_MODE일 때: "developer-2 --team implement-{spec-id}")
  (WINDOW_MODE일 때 끝에 --window 추가)

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "developer-2"
- content: |
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md

    plan.md의 체크리스트를 순서대로 구현해주세요.
    재사용 분석 섹션을 먼저 확인하고, 기존 코드 패턴을 따르세요.
    완료된 항목은 plan.md 체크박스를 업데이트해주세요.
    qa 팀메이트와 구현 완료 시 검증을 협의하세요.
    완료되면 리더에게 변경 파일 목록과 결과를 보고해주세요.
- summary: "developer-2 작업 지시"
```

**qa 생성 (필수):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "qa --team implement-{spec-id} --agent-type claude-team:tester"
  (GPT_MODE일 때: "qa --team implement-{spec-id}")
  (WINDOW_MODE일 때 끝에 --window 추가)

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "qa"
- content: |
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md

    developer 팀메이트가 구현을 완료하면 검증해주세요.
    developer와 직접 소통하여 변경 파일과 검증 범위를 확인하세요.
    타입 체크, 린트, 테스트 작성/실행, 커버리지를 확인해주세요.
    완료되면 리더에게 검증 결과를 보고해주세요.
- summary: "qa 작업 지시"
```

**architect 생성 (Large만):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "architect --team implement-{spec-id} --agent-type claude-team:architect"
  (GPT_MODE일 때: "architect --team implement-{spec-id}")
  (WINDOW_MODE일 때 끝에 --window 추가)

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "architect"
- content: |
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md

    developer들의 작업 간 충돌을 방지하고 공통 인터페이스/타입을 사전 정의해주세요.
    통합 시점에서 일관성을 검증하고 아키텍처 패턴 준수를 확인해주세요.
    developer, qa 팀메이트와 직접 소통하여 협업하세요.
    완료되면 리더에게 결과를 보고해주세요.
- summary: "architect 작업 지시"
```

### Step 4: Plan에서 구현 계획 추출 및 Phase Group 분류

plan.md에서 구현 단계를 추출하고 논리적 그룹으로 분류:

```markdown
## 구현 계획 (Design에서 결정됨)

### 설계 방향
- 방향: [확장/리팩토링/V2 - plan.md에서 추출]
- 근거: [plan.md에서 추출]

### Phase Group 분류
| Group | Phase | 내용 | 담당 |
|-------|-------|------|------|
| Group 1 | Phase 1-2 | 기반 작업 (타입/인터페이스) | developer |
| Group 2 | Phase 3-4 | 핵심 기능 (도메인/API) | developer |
| Group 3 | Phase 5-6 | 통합 (UI 연결/테스트) | developer |

### 재사용 분석
- 활용할 기존 코드: [목록]
- 새로 작성: [목록]
```

**Phase 1 완료 시:** TaskUpdate로 Phase 1 태스크를 `completed`로 변경

---

## Phase 2: 구현 루프

각 Phase Group에 대해 반복. 리더는 Group 시작만 알리고, developer와 qa가 직접 소통합니다.

```
┌──────────────────────────────────────────────────────┐
│  Step 1: 현재 Group 표시                               │
│  Step 2: 구현 + 검증 지시 (developer에게 SendMessage)  │
│  Step 3: 결과 확인 + 피드백                             │
│  (모든 Group 완료 시 Phase 3로)                         │
└──────────────────────────────────────────────────────┘
```

### Step 1: 현재 Phase Group 표시

```markdown
## Phase Group N/M 시작

### 현재 Group: [Group 이름]
- Phase X: [제목] - [설명]
- Phase Y: [제목] - [설명]

### 진행률
[████████░░░░░░░░░░░░] 40% (2/5 Groups)

### 이번 Group에서 생성/수정할 파일
- src/types/xxx.ts (생성)
- src/utils/xxx.ts (생성)
```

### Step 2: 구현 + 검증 지시

리더는 developer에게만 지시합니다. developer가 구현 완료 후 qa에게 직접 검증을 요청합니다.

```
SendMessage tool:
- type: "message"
- recipient: "developer"  (또는 "frontend-dev"/"backend-dev")
- content: |
    **Phase Group N 구현 시작**

    대상 Phase: Phase X, Y
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md

    구현 완료 후 qa 팀메이트에게 검증을 요청하세요.
    검증 통과 후 리더에게 결과를 보고해주세요.
- summary: "Group N 구현 시작"
```

**developer와 qa의 결과 보고 대기.**

> 리더는 developer와 qa 사이의 중계 역할을 하지 않습니다. 에러 발생 시에만 개입합니다.

### Step 3: 결과 확인 및 피드백

#### 자동 모드

```markdown
## Phase Group N/M 완료 (자동 진행)

### 변경 내역
| 파일 | 작업 | 상태 |
|------|------|------|
| src/types/xxx.ts | 생성 | ok |
| src/utils/xxx.ts | 생성 | ok |

### 검증 결과
- 타입 체크: PASS
- 린트: PASS
- 테스트: 5/5 통과

-> 다음 Group으로 자동 진행
```

**자동 모드 에러 처리:**

| 상황 | 자동 처리 |
|------|----------|
| 타입/린트 에러 | developer에게 SendMessage로 수정 요청 (최대 2회) |
| 테스트 실패 | developer에게 수정 요청 또는 qa에게 테스트 수정 요청 (최대 2회) |
| 3회 실패 | 중단 후 사용자에게 보고 |

**에러 수정 요청:**
```
SendMessage tool:
- type: "message"
- recipient: "developer"
- content: |
    **에러 수정 요청**

    에러 내용: [에러 메시지]
    대상 파일: [에러 발생 파일]

    에러를 수정하고 qa에게 재검증을 요청한 뒤 결과를 보고해주세요.
- summary: "에러 수정 요청"
```

#### 대화형 모드

결과 표시 후:
```
AskUserQuestion:
- question: "Phase Group N 완료. 어떻게 하시겠어요?"
- options:
  - "승인 - 다음 Group으로 (권장)"
  - "수정 필요 - 특정 파일 재작성"
  - "테스트 보완 - 케이스 추가"
  - "중단 - 여기까지만"
```

#### "수정 필요" 선택 시 (대화형)

사용자의 수정 요청을 developer에게 SendMessage로 전달 -> developer가 수정 후 qa에게 직접 재검증 요청.

#### "테스트 보완" 선택 시 (대화형)

사용자의 추가 테스트 요청을 qa에게 SendMessage로 전달.

#### "중단" 선택 시 (대화형)

```markdown
구현 중단 - 진행 상황 저장됨
- 완료된 Group: N/M
- 재진입: /oh-my-speckit:implement {spec-id}
  (완료된 Phase는 스킵됨)
```

### Step 4: 전 Group 완료 후 체크박스 확인

모든 Group 완료 후 plan.md 체크박스 진행률 확인:

```
Read tool: plan.md -> [ ] vs [x] 개수 파악
```

| 진행률 | 액션 |
|-------|------|
| 100% | Phase 3로 진행 |
| < 100% | 미완료 항목 파악 -> developer에게 추가 구현 지시 |

**Phase 2 완료 시:** TaskUpdate로 Phase 2 태스크를 `completed`로 변경

---

## Phase 3: 통합 테스트

### Step 1: qa에게 전체 테스트 실행 지시

```
SendMessage tool:
- type: "message"
- recipient: "qa"
- content: |
    **전체 통합 테스트 실행 요청**

    **테스트 범위:** 전체 테스트 스위트

    **검증 항목:**
    1. 전체 테스트 실행
    2. 커버리지 분석 (목표: >= 80%)
    3. 기존 테스트 사이드 이펙트 확인

    **결과 형식:**
    - 총 테스트: N개
    - 통과: N개
    - 실패: N개 (상세)
    - 커버리지: N%

    완료 후 SendMessage로 결과를 보고해주세요.
- summary: "통합 테스트 실행 요청"
```

### Step 2: 결과 확인

```markdown
## 통합 테스트 결과

| 항목 | 결과 |
|------|------|
| 단위 테스트 | N/N 통과 |
| 통합 테스트 | N/N 통과 |
| 커버리지 | N% |
```

#### 자동 모드

| 결과 | 자동 처리 |
|------|----------|
| 모든 테스트 통과 | Phase 4로 자동 진행 |
| 커버리지 낮음 (<80%) | qa에게 보완 테스트 요청 후 재실행 |
| 테스트 실패 | developer에게 수정 요청 (최대 2회). 3회 실패 시 중단 |

#### 대화형 모드

```
AskUserQuestion:
- question: "통합 테스트 결과입니다. 어떻게 하시겠어요?"
- options:
  - "승인 - 마무리로 (권장)"
  - "테스트 추가 - 커버리지 보완"
  - "코드 수정 - 특정 부분 개선"
```

**Phase 3 완료 시:** TaskUpdate로 Phase 3 태스크를 `completed`로 변경

---

## Phase 4: 마무리

### Step 1: plan.md 100% 완료 최종 검증

```
Read tool: plan.md -> 모든 체크박스 [x] 여부 확인
```

**100% 미만이면:**
- 미완료 항목 파악
- developer에게 추가 구현 지시
- 100% 완료 후 진행

**절대 금지:**
- "verify에서 확인할 항목"이라고 건너뛰기
- 진행률 100% 미만 상태에서 완료 처리

### Step 2: 팀메이트 종료 + 팀 삭제

```
SendMessage tool:
- type: "shutdown_request"
- recipient: "developer"
- content: "Implement 완료, 팀을 해산합니다."

(qa, developer-2/frontend-dev/backend-dev, architect도 동일 — 생성된 팀메이트만)

TeamDelete tool
```

### Step 3: 구현 완료 요약

```markdown
## Implementation Complete

| Item | Value |
|------|-------|
| Spec | {spec-id} |
| Mode | 자동/대화형 |
| Files Created | N개 |
| Files Modified | N개 |
| Tests | PASSED (N/N) |
| Coverage | N% |

### 구현 히스토리
| Group | 내용 | 결과 |
|-------|------|------|
| Group 1 | 기반 작업 | ok |
| Group 2 | 핵심 기능 | ok (자동 수정 1회) |
| Group 3 | 통합 | ok |

### 다음 단계
-> 검증: /oh-my-speckit:verify {spec-id}
-> PR 생성: gh pr create
```

### Step 4: 태스크 정리

```
TaskList -> 현재 태스크 확인
TaskUpdate (각 태스크) -> status: "deleted"
```

---

## 대화 가이드

| 질문 유형 | 대응 방법 |
|----------|----------|
| "이거 왜 이렇게 만들었어?" | plan.md + 재사용 분석 근거 설명 |
| "다른 방식 없어?" | 대안 제시 + 장단점 비교 |
| "더 간단하게" | 범위 축소 옵션 제시 |
| "테스트 더 추가해" | qa에게 추가 테스트 지시 |
| "품질 더 높여" | qa에게 품질 검사 지시 |

---

## Verify 실패 후 재진입

| 실패 유형 | 시작 위치 |
|----------|-----------|
| 타입/린트 에러 | Phase 2 (해당 Group) |
| 테스트 실패 | Phase 2 (해당 Group) |
| 커버리지 부족 | Phase 3 |
| 빌드/실행 실패 | Phase 2 (해당 Group) |

> 재진입 시 완료된 Group은 자동 스킵
