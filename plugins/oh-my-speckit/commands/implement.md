---
description: plan.md 기반 코드 구현 (Agent Teams, 자동/대화형)
argument-hint: [spec-id] [--interactive]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, AskUserQuestion, Task, Skill, TaskCreate, TaskUpdate, TaskList, TeamCreate, TeamDelete, SendMessage
---

# Implement Command

spec.md와 plan.md를 기반으로 코드를 구현합니다.
Agent Teams 기반으로 팀을 구성하고, 팀메이트에게 구현/테스트를 위임합니다.

**핵심 원칙**:
- **리더(이 커맨드)는 사용자와 소통하고 팀을 조율** - 코드 직접 작성 금지
- **모든 코드 작성/수정은 팀메이트(implementer, tester)가 수행**
- **Phase Group 단위로 구현 -> 검증 반복**
- **자동 모드(기본): 에러 시에만 중단 / 대화형 모드: Group별 확인**

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
Phase 2: 구현 루프 (Group별: implementer 코딩 → tester 검증)
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
| 1 | 2 | 팀메이트 생성 (implementer, tester 등) | Task (team_name) |
| 2 | 2 | 코드 구현 위임 | SendMessage to implementer |
| 2 | 3 | 즉시 검증 위임 | SendMessage to tester |
| 3 | 1 | 전체 테스트 위임 | SendMessage to tester |
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
- arguments에서 spec-id 추출 (`--interactive` 등 옵션 제거)
- `--interactive` 포함 -> AUTO_MODE = false

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
| Small | 파일 5개 미만, Phase 3개 이하 | implementer + tester |
| Medium | 파일 5-15개, Phase 4-6개 | implementer x2 + tester |
| Large | 파일 15개+, Phase 7개+ | implementer x2 + tester + coordinator |

### Step 3: 팀메이트 생성

**implementer 생성 (필수):**
```
Task tool:
- subagent_type: "general-purpose"
- team_name: "implement-{spec-id}"
- name: "implementer"
- description: "코드 구현"
- prompt: |
    너는 코드 구현 전문가이다.

    **임무:**
    1. plan.md의 체크리스트 순서대로 구현
    2. "재사용 분석" 섹션을 먼저 확인 - 기존 코드 import 가능하면 새로 작성 금지
    3. 기존 코드 패턴과 컨벤션을 그대로 따라 작성
    4. 완료된 항목은 plan.md 체크박스 업데이트 ([ ] -> [x])

    **금지 사항:**
    - 기존 유틸 함수 재작성
    - 기존 타입과 동일한 타입 재정의
    - 기존 패턴과 다른 새 패턴 도입

    **plan.md 경로:** ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md

    리더의 지시에 따라 작업을 수행합니다.
    작업 완료 시 반드시 SendMessage로 리더에게 결과를 보고하세요.
```

**tester 생성 (필수):**
```
Task tool:
- subagent_type: "general-purpose"
- team_name: "implement-{spec-id}"
- name: "tester"
- description: "테스트 작성 및 실행"
- prompt: |
    너는 테스트 전문가이다.

    **임무:**
    1. 변경된 코드에 대한 테스트 작성
    2. Given-When-Then 패턴 적용
    3. 성공 케이스 + 실패 케이스 + 경계값 케이스 포함
    4. 테스트 실행 및 커버리지 확인 (목표: >= 80%)
    5. 기존 테스트 패턴과 컨벤션 따르기

    **필수 케이스:**
    - 성공 케이스 (Happy Path)
    - 실패 케이스 (유효성 검증, 비즈니스 규칙, 404/403)
    - 경계값 (null, empty, min/max)

    **통합 테스트 시:** flushAndClear() 후 Repository로 DB 검증

    리더의 지시에 따라 작업을 수행합니다.
    작업 완료 시 반드시 SendMessage로 리더에게 결과를 보고하세요.
```

**implementer-2 생성 (Medium 이상):**
- name: "implementer-2"
- 동일한 프롬프트, 별도 Phase Group 담당

**coordinator 생성 (Large만):**
```
Task tool:
- subagent_type: "general-purpose"
- team_name: "implement-{spec-id}"
- name: "coordinator"
- description: "구현 통합 조율"
- prompt: |
    너는 구현 통합 조율 전문가이다.

    **임무:**
    1. implementer들의 작업 간 충돌 방지
    2. 공통 인터페이스/타입 사전 정의
    3. 통합 시점에서 일관성 검증
    4. 아키텍처 패턴 준수 확인

    리더의 지시에 따라 작업을 수행합니다.
    작업 완료 시 반드시 SendMessage로 리더에게 결과를 보고하세요.
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
| Group 1 | Phase 1-2 | 기반 작업 (타입/인터페이스) | implementer |
| Group 2 | Phase 3-4 | 핵심 기능 (도메인/API) | implementer |
| Group 3 | Phase 5-6 | 통합 (UI 연결/테스트) | implementer |

### 재사용 분석
- 활용할 기존 코드: [목록]
- 새로 작성: [목록]
```

**Phase 1 완료 시:** TaskUpdate로 Phase 1 태스크를 `completed`로 변경

---

## Phase 2: 구현 루프

각 Phase Group에 대해 반복:

```
┌──────────────────────────────────────────────────────┐
│  Step 1: 현재 Group 표시                               │
│  Step 2: implementer에게 구현 지시 (SendMessage)       │
│  Step 3: tester에게 검증 지시 (SendMessage)            │
│  Step 4: 결과 확인 + 피드백                             │
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

### Step 2: implementer에게 구현 지시

```
SendMessage tool:
- type: "message"
- recipient: "implementer"
- content: |
    **Phase Group N 구현 요청**

    **plan.md 경로:** ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md

    **설계 방향:** [plan.md에서 추출]
    **재사용 분석:** [plan.md에서 추출]

    **대상 Phase:**
    - Phase X: [제목]
    - Phase Y: [제목]

    **필수 확인:**
    - plan.md의 "재사용 분석" 섹션 먼저 확인
    - 기존 코드 import 가능하면 새로 작성 금지
    - 기존 패턴 따르기
    - 완료 항목은 plan.md 체크박스 업데이트 ([ ] -> [x])

    완료 후 SendMessage로 변경 파일 목록과 결과를 보고해주세요.
- summary: "Group N 구현 요청"
```

**implementer 결과 수신 대기.**

### Step 3: tester에게 검증 지시

implementer 완료 후:

```
SendMessage tool:
- type: "message"
- recipient: "tester"
- content: |
    **Phase Group N 검증 요청**

    **변경 파일:** [implementer가 보고한 파일 목록]

    **검증 항목:**
    1. 변경된 코드의 타입 체크
    2. 린트 체크
    3. 단위 테스트 작성 (성공/실패/경계값 케이스)
    4. 테스트 실행

    **테스트 케이스 필수:**
    - 성공 케이스 (Happy Path)
    - 실패 케이스 (유효성 검증, 비즈니스 규칙)
    - 경계값 (null, empty, min/max)

    완료 후 SendMessage로 결과를 보고해주세요.
- summary: "Group N 테스트 요청"
```

**tester 결과 수신 대기.**

### Step 4: 결과 확인 및 피드백

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
| 타입/린트 에러 | implementer에게 SendMessage로 수정 요청 (최대 2회) |
| 테스트 실패 | implementer에게 수정 요청 또는 tester에게 테스트 수정 요청 (최대 2회) |
| 3회 실패 | 중단 후 사용자에게 보고 |

**에러 수정 요청:**
```
SendMessage tool:
- type: "message"
- recipient: "implementer"
- content: |
    **에러 수정 요청**

    에러 내용: [에러 메시지]
    대상 파일: [에러 발생 파일]

    에러를 수정하고 결과를 보고해주세요.
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

사용자의 수정 요청을 implementer에게 SendMessage로 전달 -> 수정 후 tester 재검증.

#### "테스트 보완" 선택 시 (대화형)

사용자의 추가 테스트 요청을 tester에게 SendMessage로 전달.

#### "중단" 선택 시 (대화형)

```markdown
구현 중단 - 진행 상황 저장됨
- 완료된 Group: N/M
- 재진입: /oh-my-speckit:implement {spec-id}
  (완료된 Phase는 스킵됨)
```

### Step 5: 전 Group 완료 후 체크박스 확인

모든 Group 완료 후 plan.md 체크박스 진행률 확인:

```
Read tool: plan.md -> [ ] vs [x] 개수 파악
```

| 진행률 | 액션 |
|-------|------|
| 100% | Phase 3로 진행 |
| < 100% | 미완료 항목 파악 -> implementer에게 추가 구현 지시 |

**Phase 2 완료 시:** TaskUpdate로 Phase 2 태스크를 `completed`로 변경

---

## Phase 3: 통합 테스트

### Step 1: tester에게 전체 테스트 실행 지시

```
SendMessage tool:
- type: "message"
- recipient: "tester"
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
| 커버리지 낮음 (<80%) | tester에게 보완 테스트 요청 후 재실행 |
| 테스트 실패 | implementer에게 수정 요청 (최대 2회). 3회 실패 시 중단 |

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
- implementer에게 추가 구현 지시
- 100% 완료 후 진행

**절대 금지:**
- "verify에서 확인할 항목"이라고 건너뛰기
- 진행률 100% 미만 상태에서 완료 처리

### Step 2: 팀메이트 종료 + 팀 삭제

```
SendMessage tool:
- type: "shutdown_request"
- recipient: "implementer"
- content: "Implement 완료, 팀을 해산합니다."

(tester, implementer-2, coordinator도 동일)

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
| "테스트 더 추가해" | tester에게 추가 테스트 지시 |
| "품질 더 높여" | tester에게 품질 검사 지시 |

---

## Verify 실패 후 재진입

| 실패 유형 | 시작 위치 |
|----------|-----------|
| 타입/린트 에러 | Phase 2 (해당 Group) |
| 테스트 실패 | Phase 2 (해당 Group) |
| 커버리지 부족 | Phase 3 |
| 빌드/실행 실패 | Phase 2 (해당 Group) |

> 재진입 시 완료된 Group은 자동 스킵
