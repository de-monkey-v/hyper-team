---
description: 기능 요청을 spec.md + plan.md로 통합 생성 (Agent Teams)
argument-hint: [기능 설명]
allowed-tools: Read, Write, Edit, Grep, Glob, Bash, AskUserQuestion, Task, Skill, TaskCreate, TaskUpdate, TaskList, TeamCreate, TeamDelete, SendMessage
---

# Specify Command

기능 요청을 분석하여 spec.md와 plan.md를 한 번에 생성합니다.
Agent Teams 기반으로 팀을 구성하고, 팀메이트에게 분석/조사를 위임합니다.

**핵심 원칙**:
- **리더(이 커맨드)는 사용자와 소통하고 팀을 조율** - 코드베이스 직접 분석 금지
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
| 1 | 3 | 팀메이트 생성 (researcher, explorer 등) | Task (team_name) |
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

기존 specs 디렉토리에서 다음 ID 결정:

```
Glob tool:
- pattern: "${PROJECT_ROOT}/.specify/specs/*/spec.md"
```

기존 spec 번호 파악 후 다음 번호 할당. 없으면 `001` 시작.
사용자 요청에서 기능명 추출하여 `{NNN}-{feature-name}` 형식으로 ID 결정.

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
| Small | 단일 기능, 파일 5개 미만 | researcher 1명 |
| Medium | 복합 기능, 파일 5-15개 | researcher + explorer |
| Large | 대규모 기능, 파일 15개+ | researcher + explorer + analyst |

### Step 3: 팀메이트 생성 (병렬)

role-templates 스킬의 프롬프트 템플릿을 사용하여 팀메이트 생성:

**researcher 생성 (필수):**
```
Task tool:
- subagent_type: "general-purpose"
- team_name: "specify-{spec-id}"
- name: "researcher"
- description: "요구사항 분석"
- prompt: |
    너는 요구사항 분석 전문가이다.

    **임무:**
    1. 사용자 요청을 분석하여 사용자 스토리(US), 기능 요구사항(FR), 비기능 요구사항(NFR), 엣지 케이스(EC)를 도출
    2. 필요시 Context7, WebSearch를 사용하여 관련 기술 문서를 조사
    3. 불명확한 요구사항을 식별하고 명확화 질문 목록 작성

    **사용자 요청:** {사용자 요청 원문}

    **constitution.md 규칙:** {constitution 내용 또는 "없음"}

    **출력 형식:**
    - US-NNN: As a [user], I want [goal] so that [benefit]
    - FR-NNN: [검증 가능한 기능 요구사항]
    - NFR-NNN: [측정 가능한 비기능 요구사항]
    - EC-NNN: [경계 조건/엣지 케이스]
    - 불명확 사항: [목록]

    작업 완료 시 반드시 SendMessage로 리더에게 결과를 보고하세요.
```

**explorer 생성 (Medium 이상):**
```
Task tool:
- subagent_type: "general-purpose"
- team_name: "specify-{spec-id}"
- name: "explorer"
- description: "코드베이스 분석"
- prompt: |
    너는 코드베이스 분석 전문가이다.

    **임무:**
    1. 프로젝트 디렉토리 구조 분석
    2. 기존 아키텍처 패턴 식별
    3. 재사용 가능한 유틸리티/컴포넌트/타입 목록 작성
    4. 유사 기능의 기존 구현 패턴 파악
    5. 코딩 컨벤션 파악

    **프로젝트 루트:** {PROJECT_ROOT}
    **분석 대상 기능:** {사용자 요청 요약}

    **출력 형식:**
    ## 코드베이스 분석 결과
    ### 디렉토리 구조
    ### 아키텍처 패턴
    ### 재사용 가능 코드
    | 코드 | 위치 | 재사용 방법 |
    ### 기존 패턴
    ### 코딩 컨벤션

    작업 완료 시 반드시 SendMessage로 리더에게 결과를 보고하세요.
```

**analyst 생성 (Large만):**
```
Task tool:
- subagent_type: "general-purpose"
- team_name: "specify-{spec-id}"
- name: "analyst"
- description: "설계 분석"
- prompt: |
    너는 설계 분석 전문가이다.

    **임무:**
    1. 기능 요구사항과 기존 아키텍처 간 정합성 분석
    2. Breaking Change 가능성 평가
    3. 구현 계획 초안 작성 (Phase 분류, FR 매핑)
    4. 재사용 분석의 적절성 검증

    **프로젝트 루트:** {PROJECT_ROOT}
    **분석 대상 기능:** {사용자 요청 요약}

    작업 완료 시 반드시 SendMessage로 리더에게 결과를 보고하세요.
```

### Step 4: 팀메이트 결과 수집

팀메이트들의 SendMessage를 수신하여 결과를 정리합니다.
모든 팀메이트의 보고가 완료될 때까지 대기합니다.

### Step 5: 불명확한 부분 사전 질문

researcher의 분석에서 불명확한 부분이 있으면 AskUserQuestion으로 해소:

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

Spec + explorer 분석결과를 기반으로 plan.md를 메모리에 작성:

- 설계 방향 및 개요
- FR 매핑 테이블
- 재사용 분석 (explorer 결과 기반)
- 변경 파일 목록
- 구현 단계 (Phase별 Task + 체크박스)
- E2E 테스트 시나리오
- Breaking Change 섹션 (해당시)

> plan 템플릿: skills/plan-writing/references/plan-template.md 참조

Large 규모에서 analyst 결과가 있으면 정합성 검증 결과를 반영합니다.

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
- recipient: "researcher"
- content: "Specify 완료, 팀을 해산합니다."

(explorer, analyst도 동일)
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

researcher 팀메이트가 직접 사용:
- Context7: 라이브러리/프레임워크 공식 문서
- WebSearch: 일반 웹 검색, 최신 정보

### Spec/Plan 템플릿

- `skills/spec-writing/references/spec-template.md`
- `skills/plan-writing/references/plan-template.md`

### 권장 옵션 제시 원칙

- 첫 번째 옵션에 "(권장)" 표시
- 검색 결과 기반 근거 제시
- 2-3개 대안과 장단점
