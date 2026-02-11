# Design Workflow Detail

구현 계획 수립 워크플로우의 상세 절차. SKILL.md에서 참조.

## Phase별 상세 절차

### Phase 0: 프로젝트 루트 확인

`../common/project-root-detection.md` 참조

1. cwd부터 상위로 프로젝트 마커 탐색
2. AskUserQuestion으로 위치 확인
3. 확인된 경로를 `PROJECT_ROOT`로 저장

---

### Phase 1: 스펙 로드

```bash
ls ${PROJECT_ROOT}/.specify/specs/
cat ${PROJECT_ROOT}/.specify/specs/[spec-id]/spec.md
```

**파싱 항목:**
- 기능 요구사항 (FR) 목록
- 비기능 요구사항 (NFR) 목록
- 아키텍처 컨텍스트
- 기술 스택

---

### Phase 1.5: Constitution 확인

```bash
cat ${PROJECT_ROOT}/.specify/memory/constitution.md
```

| 항목 | plan.md 반영 내용 |
|------|------------------|
| 아키텍처 패턴 | 사용할 패턴 명시 |
| 금지된 패턴 | 피해야 할 접근법 기록 |
| 테스트 요구사항 | Phase별 테스트 요구사항 |
| API 버전 관리 | Breaking Change 처리 방식 |

---

### Phase 2: 코드베이스 분석 (에이전트)

**codebase-explorer 에이전트 호출:**

```
Task tool:
- subagent_type: "oh-my-speckit:codebase-explorer"
- prompt: |
    프로젝트 구조와 아키텍처 패턴 분석. [spec.md 요약] 구현을 위한 관련 코드 탐색.

    **중복 방지 분석 (CRITICAL):**
    1. spec.md의 각 FR에 대해 유사 기능 존재 여부 확인
    2. 재사용 가능한 코드 목록 작성 (필수)
    3. 새로 작성 필요한 코드와 재사용 코드 구분
    4. 기존 코드 수정 시 영향 범위 분석
```

**에이전트 수행 작업:**
- 디렉토리 구조 분석
- 기존 아키텍처 패턴 파악
- 사용 중인 라이브러리 확인
- 코딩 컨벤션/테스트 패턴 분석
- **각 FR별 유사 기능 검색**
- **재사용 가능 코드 목록** (최소 5개 탐색)
- 기존 코드 수정 시 호출자(caller) 분석

---

### Phase 2.5: Breaking Change 및 설계 방향 확인

#### 최소 변경 원칙 (CRITICAL)

기존 로직 수정 전 확인:
- [ ] 수정 없이 **확장**으로 해결 가능한가?
- [ ] 새 함수/클래스 **추가**로 해결 가능한가?
- [ ] 정말 기존 코드 **수정**이 필요한가?

| 상황 | 권장 접근법 |
|------|-----------|
| 기능 추가 | 새 함수/클래스 생성 |
| 기능 변경 | 래퍼 함수로 확장 |
| 버그 수정 | 해당 로직만 최소 수정 |
| 리팩토링 | 별도 PR로 분리 |

#### V2 필요 여부 판단

| 변경 유형 | V2 필요 |
|----------|---------|
| 응답/요청 필드 삭제/변경 | ✅ 예 |
| 필드 타입 변경 | ✅ 예 |
| DTO 구조 변경 | ✅ 예 |
| 필드 추가 (optional) | ❌ 아니오 |

#### 설계 방향 제안

```markdown
## 설계 방향 분석

**방향 A: 확장 중심** (권장)
- 기존 코드 수정 최소화, 새 파일 추가
- 장점: 안전, 롤백 용이
- 단점: 코드 중복 가능성

**방향 B: 리팩토링 포함**
- 기존 코드 개선과 함께 구현
- 장점: 코드 품질 개선
- 단점: 영향 범위 넓음

**방향 C: V2 분리** (Breaking Change)
- 새 버전으로 완전 분리
- 장점: 명확한 버전 관리
- 단점: 마이그레이션 필요
```

---

### Phase 3: 기술 조사 (필요시)

새 라이브러리/패턴 필요시 **smart-searcher** 에이전트 호출:

```
Task:
- subagent_type: "search:smart-searcher"
- description: "기술 조사"
- prompt: |
    [라이브러리/패턴명] 관련 조사:
    - 공식 문서 API
    - 최신 best practices
    - 상세 구현 사례
```

**자동 선택**: 상황에 따라 Context7/WebSearch/Brave/Tavily 사용

---

### Phase 4: 구현 계획 수립 (에이전트)

**architecture-planner 에이전트 호출:**

```
Task tool:
- subagent_type: "oh-my-speckit:architecture-planner"
- prompt: |
    spec.md 요구사항과 codebase-explorer 분석 결과 기반 plan.md 작성.
    파일 경로: .specify/specs/[spec-id]/plan.md
```

**에이전트 수행 작업:**
1. 변경 파일 목록 작성 (생성/수정/삭제)
2. Phase별 구현 단계 설계 (의존성 순서)
3. E2E 테스트 시나리오 정의
4. Breaking Change 분석
5. 기술 결정 문서화
6. plan.md 파일 생성

---

### Phase 5.5: Spec/Plan 정합성 검증

**spec-plan-validator 에이전트 호출:**

```
Task tool:
- subagent_type: "oh-my-speckit:spec-plan-validator"
- prompt: "spec.md: [경로], plan.md: [경로]. 정합성 검증."
```

| 결과 | 액션 |
|------|------|
| ✅ PASS | 완료 안내 |
| ⚠️ WARN | 경고 후 진행 |
| ❌ FAIL | plan.md 수정 후 재검증 |

---

## plan.md 필수 섹션

### FR 매핑 테이블

```markdown
## FR 매핑

| FR | Phase | 파일 | 설명 |
|-----|-------|------|------|
| FR-001 | 1 | src/auth/login.ts | 로그인 로직 |
| FR-002 | 2 | src/auth/token.ts | 토큰 발급 |
```

### 재사용 분석

```markdown
## 재사용 분석

| 기존 코드 | 위치 | 재사용 방법 |
|----------|------|------------|
| hashPassword() | src/utils/crypto.ts | import |
| validateEmail() | src/utils/validator.ts | import |
| BaseService | src/core/base.ts | 상속 |
```

### E2E 테스트 시나리오

```markdown
## E2E 테스트 시나리오

### 핵심 시나리오
| 시나리오 | 사전조건 | 액션 | 예상 결과 |
|---------|---------|------|----------|
| 로그인 성공 | 유효한 계정 | 로그인 | 대시보드 이동 |

### 엣지 케이스
| 시나리오 | 조건 | 예상 결과 |
|---------|------|----------|
| 연속 실패 | 5회 실패 | 계정 잠금 |
```

---

## 재진입 시나리오

**Verify에서 "요구사항 미충족 (설계)" 실패 시:**

1. 기존 spec.md, plan.md 로드
2. verify 실패 리포트에서 미충족 FR 확인
3. plan.md에 누락된 Phase/Task 추가
4. 사용자 승인 후 업데이트
5. implement로 재진입 안내
