# Orchestration Patterns for Claude Code Commands

커맨드에서 에이전트와 스킬을 효과적으로 조율하는 패턴입니다.

## 핵심 원칙

### 역할 분담

| 컴포넌트 | 역할 | 예시 |
|---------|------|------|
| **커맨드** | 오케스트레이션, 사용자 상호작용 | create-plugin, modify-plugin |
| **스킬** | 지식/가이드 제공 | skill-development, agent-development |
| **에이전트** | 자율적 실행/생성 | skill-creator, agent-creator |

### 호출 흐름

```
사용자 → 커맨드 → (스킬 로드) → (에이전트 위임) → 결과
              ↓           ↓              ↓
         AskUserQuestion  Skill tool     Task tool
```

## 에이전트 호출 패턴

### 명시적 위임 패턴

커맨드에서 에이전트를 호출할 때는 **명시적으로** 작성하며, XML 태그로 데이터를 구조화하여 전달합니다:

```markdown
## Phase N: 컴포넌트 생성

**Goal**: 스킬 파일 생성

**Actions**:

1. **skill-creator 에이전트에게 위임**:

   Task tool prompt:
   ```
   <task-context>
   <skill-name>{skill-name}</skill-name>
   <trigger-phrases>{trigger phrases}</trigger-phrases>
   <resources>references/, examples/</resources>
   </task-context>

   <specification>
   {description}
   </specification>

   <instructions>
   위 명세에 따라 스킬을 생성하세요.
   </instructions>
   ```

   **기대 출력**:
   - `skills/{skill-name}/SKILL.md`
   - 지원 파일들

2. 에이전트 결과를 검토하고 품질 확인

**Output**: 완성된 스킬 파일
```

### 스킬 로드 후 에이전트 위임 패턴

에이전트가 스킬을 사전 로드하도록 설정되어 있으면, 커맨드에서는 에이전트만 호출합니다:

```markdown
## Phase N: 에이전트 생성

**Goal**: 에이전트 파일 생성

**Actions**:

1. **agent-creator 에이전트에게 위임**:

   에이전트는 `agent-development` 스킬을 사전 로드함.

   다음 정보를 전달:
   - 에이전트 목적: {purpose}
   - 트리거 시나리오: {when to use}
   - 필요한 도구: {tools}

   **기대 출력**: `agents/{agent-name}.md`

2. 생성된 에이전트 검증

**Output**: 완성된 에이전트 파일
```

## XML 핸드오프 패턴

커맨드에서 에이전트를 호출할 때 XML 태그로 구조화된 데이터를 전달합니다.

### 기본 XML 데이터 전달

```markdown
## Phase N: 컴포넌트 생성

**Goal**: 스킬 파일 생성

**Actions**:

1. **skill-creator 에이전트에게 위임**:

   Task tool prompt:
   ```
   <task-context>
   <plugin-path>{plugin-path}</plugin-path>
   <component-name>{name}</component-name>
   <mode>{project or plugin}</mode>
   </task-context>

   <specification>
   {plan 파일에서 추출한 상세 명세}
   </specification>

   <instructions>
   위 명세에 따라 컴포넌트를 생성하세요.
   XML 구조화된 프롬프트 패턴을 따르세요.
   </instructions>
   ```

   **기대 출력**:
   - `skills/{skill-name}/SKILL.md`
   - 지원 파일들
```

### Long Context 원칙

긴 데이터(plan, 현재 상태 분석 등)는 **TOP**에, 지시사항은 **BOTTOM**에 배치합니다:

```
<current-state>
{현재 플러그인 구조 분석 결과 - 긴 데이터}
</current-state>

<specification>
{plan에서 추출한 명세 - 긴 데이터}
</specification>

<instructions>
{짧은 지시사항은 마지막에}
</instructions>
```

---

## 스킬 로드 패턴

### 지식 참조용 스킬 로드

커맨드가 **직접** 스킬의 지식을 사용해야 할 때:

```markdown
## Phase N: 설계

**Goal**: 플러그인 구조 설계

**MUST load plugin-development skill** using Skill tool.

**Actions**:

1. `plugin-creator:plugin-development` 스킬 로드
2. 스킬의 컴포넌트 패턴을 참조하여 설계:
   - 필요한 스킬 목록
   - 필요한 에이전트 목록
   - 필요한 커맨드 목록
3. 설계 결과를 사용자에게 제시

**Output**: 플러그인 설계 문서
```

### 에이전트용 스킬 사전 로드

에이전트가 스킬을 사전 로드하도록 설정:

```yaml
# agents/skill-creator.md
---
name: skill-creator
skills: plugin-creator:skill-development
---
```

이 경우 커맨드에서 스킬을 별도로 로드할 필요 없습니다.

## 순차적 에이전트 체이닝

### 검증 체인 패턴

생성 → 검증 → 수정의 흐름:

```markdown
## Phase N: 생성 및 검증

**Goal**: 스킬 생성 및 품질 검증

**Actions**:

1. **skill-creator 에이전트에게 위임**:
   - 입력: 스킬 요구사항
   - 출력: 스킬 파일들

2. 에이전트 완료 후, **skill-reviewer 에이전트에게 위임**:
   - 입력: 생성된 스킬 경로
   - 출력: 품질 리뷰 보고서

3. 리뷰 결과 확인:
   - Critical 이슈 있음 → 수정 필요, 사용자 확인
   - 이슈 없음 → 다음 Phase 진행

**Output**: 검증된 스킬 파일
```

### 병렬 생성 패턴

독립적인 컴포넌트 동시 생성 (고급):

```markdown
## Phase N: 컴포넌트 생성

**Goal**: 여러 컴포넌트 동시 생성

**Actions**:

1. 다음 에이전트들에게 **병렬로** 위임:

   **skill-creator**: 분석 스킬 생성
   **command-creator**: 실행 커맨드 생성

   (각 에이전트는 독립적으로 실행)

2. 모든 에이전트 완료 후 결과 수집

3. 통합 검증

**Output**: 완성된 컴포넌트들
```

## 사용자 상호작용 패턴

### 결정 지점 패턴

```markdown
## Phase N: 구조 확정

**Goal**: 사용자 확인 후 구조 확정

**Actions**:

1. 설계 결과 제시:
   ```
   | 컴포넌트 | 수량 | 목록 |
   |---------|------|------|
   | Skills  | 2    | analyze, report |
   | Agents  | 1    | validator |
   ```

2. AskUserQuestion으로 확인:
   - header: "구조 확인"
   - question: "이 구조로 진행하시겠습니까?"
   - options:
     - 예, 진행 (Recommended)
     - 수정 필요
     - 취소

3. 응답에 따라 분기:
   - "예" → 다음 Phase
   - "수정" → 수정 사항 수집 후 재설계
   - "취소" → 종료

**Output**: 확정된 구조
```

### 점진적 정보 수집 패턴

```markdown
## Phase N: 요구사항 수집

**Goal**: 필요한 정보 점진적 수집

**Actions**:

1. 기본 정보 확인:
   - $ARGUMENTS에서 플러그인 이름/목적 파악
   - 불명확하면 AskUserQuestion으로 질문

2. 컴포넌트별 세부 정보:

   **스킬 정보** (필요시):
   - 어떤 지식을 제공해야 하나요?
   - 트리거 구문은 무엇인가요?

   **에이전트 정보** (필요시):
   - 어떤 작업을 자동화하나요?
   - 어떤 도구가 필요한가요?

3. 수집된 정보로 설계 진행

**Output**: 완전한 요구사항 문서
```

## 오류 처리 패턴

### 에이전트 실패 복구

```markdown
## Phase N: 컴포넌트 생성

**Actions**:

1. skill-creator 에이전트에게 위임

2. **실패 시 처리**:
   - 에이전트 오류 발생 → 오류 내용 확인
   - 입력 문제 → 사용자에게 추가 정보 요청
   - 시스템 문제 → 재시도 또는 수동 생성 안내

3. **성공 시**: 다음 단계 진행

**Output**: 생성된 파일 또는 오류 보고
```

### 검증 실패 처리

```markdown
## Phase N: 검증

**Actions**:

1. plugin-validator 에이전트에게 위임

2. 검증 결과 처리:

   **Critical 이슈**:
   - 즉시 수정 필요
   - 수정 가이드 제공
   - 다음 Phase 진행 불가

   **Warning**:
   - 사용자에게 알림
   - 선택적 수정
   - 다음 Phase 진행 가능

   **Pass**:
   - 다음 Phase 진행

**Output**: 검증 보고서 및 다음 액션
```

## Phase 설계 가이드라인

### 간결한 Phase 구조

**권장 Phase 수:**
- 단순 워크플로우: 2-3 Phase
- 표준 워크플로우: 3-4 Phase
- 복잡한 워크플로우: 4-5 Phase (최대)

### Phase 템플릿

```markdown
## Phase N: [명확한 이름]

**Goal**: [한 문장으로 목표]

**Prerequisites**: (선택사항)
- [필요한 스킬/조건]

**Actions**:
1. [구체적 행동]
2. [구체적 행동]
3. [구체적 행동]

**Output**: [이 Phase의 산출물]

---
```

### Phase 간 의존성

```
Phase 1 (Discovery)
    ↓ 사용자 확인
Phase 2 (Implementation)
    ↓
Phase 3 (Validation)
    ↓ 실패 시 Phase 2로 복귀
Phase 4 (Completion)
```

## Best Practices

### DO ✅

1. **명시적 위임**: 에이전트에게 무엇을 전달하고 무엇을 기대하는지 명확히
2. **XML 데이터 전달**: 에이전트에게 데이터를 전달할 때 XML 태그로 구조화
3. **Long Context 원칙**: 긴 데이터는 TOP에, 지시사항은 BOTTOM에
4. **결정 지점**: 중요한 단계마다 사용자 확인
5. **오류 처리**: 실패 시나리오 고려
6. **간결함**: 필요한 최소한의 Phase만 유지

### DON'T ❌

1. **암시적 호출**: "스킬을 로드하세요"만 쓰고 어떻게 하는지 안 씀
2. **과도한 Phase**: 7개 이상의 Phase
3. **에이전트 체이닝 무시**: 에이전트 내에서 다른 에이전트 호출 시도
4. **사용자 무시**: 결정 없이 진행

## 예시: 최적화된 플러그인 생성 커맨드

```markdown
---
description: Guided plugin creation with component design
argument-hint: [plugin-description]
allowed-tools: Read, Write, Grep, Glob, Bash, AskUserQuestion, Skill, Task
---

# Create Plugin

## Phase 1: Discovery & Planning

**Goal**: 플러그인 목적 파악 및 컴포넌트 설계

**Actions**:
1. $ARGUMENTS에서 플러그인 목적 파악
2. 불명확하면 AskUserQuestion으로 질문
3. `plugin-development` 스킬 로드하여 컴포넌트 유형 참조
4. 컴포넌트 계획 수립 및 사용자 확인

**Output**: 확정된 컴포넌트 계획

---

## Phase 2: Implementation

**Goal**: 컴포넌트 생성

**Actions**:
1. 플러그인 구조 생성 (디렉토리, plugin.json)
2. 각 컴포넌트 타입별 에이전트 위임:
   - Skills → skill-creator
   - Agents → agent-creator
   - Commands → command-creator
3. 생성 결과 수집

**Output**: 완성된 플러그인 파일들

---

## Phase 3: Validation & Completion

**Goal**: 검증 및 완료

**Actions**:
1. plugin-validator 에이전트에게 검증 위임
2. 이슈 처리 (있으면)
3. 완료 요약 및 테스트 가이드 제공

**Output**: 검증된 플러그인
```

이 패턴으로 8 Phase → 3 Phase로 간소화하면서도 모든 기능을 유지합니다.
