---
description: 구현 검증 및 대화형 수정 (Agent Teams, 병렬 검증)
argument-hint: [spec-id] [--quick|--full] [--gpt]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, AskUserQuestion, Task, Skill, TaskCreate, TaskUpdate, TaskList, TeamCreate, TeamDelete, SendMessage
---

# Verify Command

구현을 검증하고 문제를 대화형으로 수정합니다.
Agent Teams 기반으로 팀을 구성하고, 팀메이트에게 검증을 병렬로 위임합니다.

**핵심 원칙**:
- **리더(이 커맨드)는 사용자와 소통하고 팀을 조율** - 코드 직접 수정 금지
- **모든 검증은 팀메이트(qa, critic, architect)가 수행**
- **병렬 검증으로 속도 최적화**
- **문제 발견 시 수정 방법 선택 가능 (자동/가이드/스킵)**
- **높은 자율성 모델**: 리더는 고수준 목표만 전달, 팀메이트가 세부사항 자율 결정
- **팀메이트 간 직접 소통**: qa와 critic이 SendMessage로 직접 협업

**Spec ID:** {{arguments}}

---

## 워크플로우 개요

```
Phase 0: 초기화 (Spec/Plan 로드)
     ↓
Phase 1: 검증 범위 설정
     ↓
Phase 2: 팀 구성 + 병렬 검증 실행
     ↓
Phase 3: 대화형 수정 루프 (문제 있을 때)
     ↓
Phase 4: 최종 리포트 + 팀 해산
```

---

## 필수 실행 체크리스트

| Phase | Step | 필수 액션 | Tool |
|-------|------|----------|------|
| 0 | 3 | 기존 태스크 정리 | TaskList, TaskUpdate |
| 0 | 4 | 태스크 등록 | TaskCreate |
| 2 | 1 | 팀 생성 | TeamCreate |
| 2 | 2 | 스폰 모드 설정 | arguments 파싱 |
| 2 | 3 | 팀메이트 스폰 (qa, critic 등) | Skill (spawn-teammate) |
| 2 | 4 | 결과 수집 | SendMessage 수신 |
| 3 | 3 | 수정 시 developer 스폰 | Skill (spawn-teammate) |
| 4 | 2 | 팀 해산 | SendMessage (shutdown), TeamDelete |

**금지 사항:**
- 리더가 직접 테스트 실행
- 리더가 직접 요구사항 검증
- 리더가 직접 코드 수정

---

## Phase 0: 초기화

### Step 1: Spec/Plan 로드

**Spec ID 파싱:**
- arguments에서 spec-id 추출 (`--quick`, `--full`, `--gpt` 옵션 제거)
- `--quick` -> SCOPE = "quick"
- `--full` -> SCOPE = "full"
- 기본값 -> SCOPE = "standard"

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

**파싱 항목:**
- FR/NFR 목록 (검증 대상)
- 변경 파일 목록 (plan.md에서)
- Breaking Change 여부

### Step 2: Constitution 확인

```
Read tool: ${PROJECT_ROOT}/.specify/memory/constitution.md
```

constitution 규칙이 있으면 검증 기준에 포함.

### Step 3: 기존 태스크 정리

```
TaskList tool -> 기존 태스크 확인
TaskUpdate tool (각 기존 태스크) -> status: "deleted"
```

### Step 4: 태스크 등록

```
TaskCreate (4회):

1. subject: "Phase 1: 검증 범위 설정"
   description: "검증 항목 제시 및 범위 선택"
   activeForm: "검증 범위 설정 중"

2. subject: "Phase 2: 병렬 검증 실행"
   description: "팀 구성, 테스트/품질/요구사항 병렬 검증"
   activeForm: "검증 실행 중"

3. subject: "Phase 3: 수정 루프"
   description: "검증 실패 항목 분석 및 수정"
   activeForm: "수정 루프 진행 중"

4. subject: "Phase 4: 최종 리포트"
   description: "검증 결과 요약, 팀 해산"
   activeForm: "최종 리포트 작성 중"
```

---

## Phase 1: 검증 범위 설정

### Step 1: 검증 항목 제시

```markdown
## 검증 항목

### 필수 (빠른 검증)
- 타입 체크
- 린트 체크
- 테스트 실행
- 커버리지 확인

### 표준 (기본)
- 필수 항목 전부
- 빌드 검증
- 요구사항 충족 검증
- Breaking Change 검증

### 완전
- 표준 항목 전부
- 코드 품질 심층 분석
- 보안 스캔
```

### Step 2: 범위 선택

`--quick` 또는 `--full` 옵션이 이미 지정된 경우 자동 설정.
지정되지 않은 경우:

```
AskUserQuestion:
- question: "검증 범위를 선택해주세요."
- header: "검증 범위"
- options:
  - label: "빠른 검증 (권장)"
    description: "타입, 린트, 테스트, 커버리지"
  - label: "표준 검증"
    description: "빠른 + 빌드, 요구사항, Breaking Change"
  - label: "완전 검증"
    description: "표준 + 품질 심층 분석, 보안"
  - label: "커스텀"
    description: "직접 선택"
```

**Phase 1 완료 시:** TaskUpdate로 Phase 1 태스크를 `completed`로 변경

---

## Phase 2: 팀 구성 + 병렬 검증 실행

### Step 1: 팀 생성

```
TeamCreate tool:
- team_name: "verify-{spec-id}"
- description: "Verify {spec-id}: 구현 검증"
```

### Step 2: 스폰 모드 설정

```
Skill tool:
- skill: "oh-my-speckit:role-templates"
```

검증 범위와 프로젝트 규모에 따라 팀 구성:

| 규모/범위 | 팀 구성 |
|----------|--------|
| Small / 빠른 | qa 1명 |
| Medium / 표준 | qa + critic |
| Large / 완전 | qa + architect + critic |

**스폰 모드 설정:**

arguments에서 `--gpt` 옵션 확인:
- `--gpt` 포함 -> GPT_MODE = true (spawn-teammate에 `--agent-type` 없이 호출)
- 기본값 -> GPT_MODE = false (spawn-teammate에 `--agent-type` 지정)

### Step 3: 팀메이트 스폰 + 검증 지시 (병렬)

모든 팀메이트는 spawn-teammate Skill로 스폰합니다.
GPT_MODE 여부에 따라 `--agent-type` 인자만 달라집니다.

**역할-에이전트 매핑:**

| 역할 | 에이전트 타입 |
|------|-------------|
| qa | claude-team:tester |
| critic | claude-team:reviewer |
| architect | claude-team:architect |

---

**qa 스폰 (필수):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "qa --team verify-{spec-id} --agent-type claude-team:tester"
  (GPT_MODE일 때: "qa --team verify-{spec-id}")

-> 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "qa"
- content: |
    spec.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/spec.md
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md
    프로젝트 루트: {PROJECT_ROOT}
    constitution 규칙: {constitution 내용 또는 "없음"}
    변경 파일 목록: [plan.md에서 추출]

    구현을 검증해주세요: 타입 체크, 린트, 테스트 실행, 커버리지, 코드 품질 분석.
    critic 팀메이트가 있으면 검증 결과를 공유하세요.
    완료되면 리더에게 결과를 보고해주세요.
- summary: "qa 검증 작업 지시"
```

---

**critic 스폰 (Medium 이상):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "critic --team verify-{spec-id} --agent-type claude-team:reviewer"
  (GPT_MODE일 때: "critic --team verify-{spec-id}")

-> 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "critic"
- content: |
    spec.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/spec.md
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md
    프로젝트 루트: {PROJECT_ROOT}

    구현이 요구사항을 충족하는지 비판적으로 검토해주세요.
    qa 팀메이트와 검증 결과를 공유하고 누락된 테스트 시나리오를 찾아주세요.
    완료되면 리더에게 Devil's Advocate Review를 보고해주세요.
- summary: "critic 검증 작업 지시"
```

---

**architect 스폰 (Large만):**

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "architect --team verify-{spec-id} --agent-type claude-team:architect"
  (GPT_MODE일 때: "architect --team verify-{spec-id}")

-> 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "architect"
- content: |
    spec.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/spec.md
    plan.md 경로: ${PROJECT_ROOT}/.specify/specs/{spec-id}/plan.md
    프로젝트 루트: {PROJECT_ROOT}

    아키텍처 정합성을 검증해주세요: FR 매핑, Breaking Change, 패턴 준수.
    qa, critic 팀메이트와 검증 결과를 공유하세요.
    완료되면 리더에게 결과를 보고해주세요.
- summary: "architect 검증 작업 지시"
```

### Step 4: 결과 수집

모든 팀메이트의 SendMessage를 수신하여 결과를 통합합니다.

**Phase 2 완료 시:** TaskUpdate로 Phase 2 태스크를 `completed`로 변경

---

## Phase 3: 대화형 수정 루프

**조건**: 검증 실패 항목이 있을 때만 실행. 모든 항목 통과 시 Phase 4로 직행.

### Step 1: 결과 분석

```markdown
## 검증 결과 분석

### Critical (반드시 수정)
| # | 항목 | 문제 | 위치 |
|---|------|------|------|
| 1 | 테스트 실패 | Expected 200, got 500 | src/services/user.ts:42 |
| 2 | 타입 에러 | Property 'name' does not exist | src/types/user.ts:15 |

### Warning (수정 권장)
| # | 항목 | 문제 | 위치 |
|---|------|------|------|
| 3 | 린트 | 'any' type 사용 | src/utils/helper.ts:28 |
| 4 | 커버리지 | 75% (목표: 80%) | src/services/auth.ts |

### Info (선택)
| # | 항목 | 문제 |
|---|------|------|
| 5 | 미사용 import | 2개 파일에서 발견 |
```

### Step 2: 수정 대상 선택

```
AskUserQuestion:
- question: "검증 실패 항목이 있습니다. 어떻게 수정하시겠어요?"
- header: "수정 범위"
- options:
  - label: "Critical만 수정 (권장)"
    description: "필수 항목만 자동 수정"
  - label: "Warning까지 수정"
    description: "Critical + Warning 자동 수정"
  - label: "전부 수정"
    description: "Info 포함 모두 수정"
  - label: "하나씩 선택"
    description: "개별 확인 후 수정/스킵 결정"
```

### Step 3: 수정 실행

자동 수정이 필요한 경우 developer 팀메이트를 스폰:

```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "developer --team verify-{spec-id} --agent-type claude-team:implementer"
  (GPT_MODE일 때: "developer --team verify-{spec-id}")

-> 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "developer"
- content: |
    검증 실패 항목을 수정해주세요.
    기존 패턴 유지, 최소한의 수정으로 문제를 해결하세요.
    수정 완료 후 qa 팀메이트에게 재검증을 요청하세요.
    완료되면 리더에게 결과를 보고해주세요.
- summary: "developer 수정 작업 지시"
```

**수정 지시:**
```
SendMessage tool:
- type: "message"
- recipient: "developer"
- content: |
    **검증 실패 수정 요청**

    **수정 대상:**
    | # | 항목 | 문제 | 위치 |
    | 1 | [항목] | [문제] | [위치] |
    | 2 | [항목] | [문제] | [위치] |

    **수정 원칙:**
    - 원인 분석 후 최소한의 수정
    - 기존 패턴 유지
    - 다른 코드에 영향 최소화

    완료 후 변경 내역을 보고해주세요.
- summary: "검증 실패 수정 요청"
```

#### "하나씩 선택" 모드

각 이슈에 대해:
```
AskUserQuestion:
- question: "문제 #N: [이슈 설명] - 어떻게 하시겠어요?"
- header: "문제 #N"
- options:
  - label: "자동 수정 (권장)"
    description: "developer에게 수정 위임"
  - label: "가이드만"
    description: "수정 방법 안내 (직접 수정)"
  - label: "스킵"
    description: "나중에 처리"
```

**"가이드만" 선택 시:**
```markdown
## 문제 #N 수정 가이드

### 원인
[원인 분석]

### 수정 방법
1. [파일:라인] 열기
2. [구체적 수정 내용]
3. 테스트 재실행

### 관련 파일
- [파일 목록]
```

### Step 4: 재검증

수정 완료 후 qa에게 재검증 지시:

```
SendMessage tool:
- type: "message"
- recipient: "qa"
- content: |
    **수정 후 재검증 요청**

    수정된 항목:
    - [수정 내역]

    수정된 항목만 재검증해주세요.
    결과를 보고해주세요.
- summary: "재검증 요청"
```

**재검증 결과:**
```markdown
## 재검증 결과

| # | 항목 | 이전 | 현재 |
|---|------|------|------|
| 1 | 테스트 실패 | FAIL | PASS |
| 2 | 타입 에러 | FAIL | PASS |
| 3 | 린트 | WARN | 스킵됨 |
```

```
AskUserQuestion:
- question: "재검증 완료. 어떻게 하시겠어요?"
- header: "재검증"
- options:
  - label: "완료 - 리포트로 (권장)"
    description: "현재 상태로 마무리"
  - label: "계속 - 남은 문제 수정"
    description: "남은 Warning/Info 수정"
  - label: "전체 재검증"
    description: "처음부터 다시 검증"
```

**Phase 3 완료 시:** TaskUpdate로 Phase 3 태스크를 `completed`로 변경

---

## Phase 4: 최종 리포트 + 팀 해산

### Step 1: 최종 리포트

#### 성공 시 (모든 항목 통과)

```markdown
## Verification Complete

| 항목 | 상태 |
|------|------|
| 타입 체크 | PASS |
| 린트 | PASS |
| 테스트 | PASS (N/N) |
| 커버리지 | N% |
| 빌드 | PASS |
| 요구사항 | 충족 (N/N) |

### 수정 히스토리
| 라운드 | 수정 항목 | 결과 |
|--------|----------|------|
| 1 | 테스트 실패 (#1) | ok |
| 1 | 타입 에러 (#2) | ok |

### 다음 단계
-> 커밋: /commit
-> PR 생성: gh pr create
```

#### 부분 성공 시 (Warning 남음)

```markdown
## Verification Passed with Warnings

| 항목 | 상태 |
|------|------|
| 타입 체크 | PASS |
| 린트 | WARN (2 warnings) |
| 테스트 | PASS |
| 커버리지 | 75% (목표: 80%) |

### 남은 Warning
| # | 항목 | 상태 |
|---|------|------|
| 3 | 린트 | 스킵됨 |
| 4 | 커버리지 | 스킵됨 |
```

```
AskUserQuestion:
- question: "Warning이 남아 있습니다. 어떻게 하시겠어요?"
- header: "Warning"
- options:
  - label: "이대로 완료 (권장)"
    description: "Warning 무시하고 마무리"
  - label: "Warning 수정"
    description: "수정 루프로 돌아가기"
```

#### 실패 시 (Critical 남음)

```markdown
## Verification Failed

### 미해결 Critical
| # | 항목 | 문제 |
|---|------|------|
| 1 | 빌드 실패 | Module not found |

### 원인 분석
- 무엇이: 빌드 실패
- 왜: 누락된 의존성
- 어떻게: npm install missing-package

### 재진입 안내
-> 코드 수정 후: /oh-my-speckit:implement {spec-id}
-> 검증 재실행: /oh-my-speckit:verify {spec-id}
```

### Step 2: 팀메이트 종료 + 팀 삭제

```
SendMessage tool:
- type: "shutdown_request"
- recipient: "qa"
- content: "Verify 완료, 팀을 해산합니다."

(critic, architect, developer도 동일 -- 생성된 팀메이트만)

TeamDelete tool
```

### Step 3: 태스크 정리

```
TaskList -> 현재 태스크 확인
TaskUpdate (각 태스크) -> status: "deleted"
```

**Phase 4 완료 시:** TaskUpdate로 Phase 4 태스크를 `completed`로 변경

---

## 재진입 시

1. 이전 검증 결과 파일 확인
2. AskUserQuestion: "이전 검증 상태가 있습니다. 어떻게 하시겠어요?"
   - "이어서 - 미해결 문제부터"
   - "처음부터 다시"
   - "특정 항목만 재검증"
3. 선택에 따라 진행

---

## 검증 원칙

1. **모든 테스트 실패는 문제다** - "스펙 무관"은 면책 사유가 아님
2. **WARN은 통과가 아니다** - 해결 방법을 명확히 제시
3. **실패 분석 3요소 필수** - 무엇이, 왜, 어떻게

---

## 실패 시 재진입 안내

| 실패 유형 | 재진입 커맨드 |
|----------|------------|
| 타입/린트/테스트/빌드 에러 | /oh-my-speckit:implement {spec-id} |
| 요구사항 미충족 (구현) | /oh-my-speckit:implement {spec-id} |
| 요구사항 미충족 (설계) | /oh-my-speckit:specify {spec-id} |
| 스펙 불명확 | /oh-my-speckit:specify {spec-id} |
