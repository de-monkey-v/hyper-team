# Implement Workflow Detail

구현 워크플로우의 상세 절차. SKILL.md에서 참조.

## 핵심 원칙

### 기존 코드 우선 (CRITICAL)

1. plan.md의 "재사용 분석" 섹션 **먼저** 확인
2. 기존 코드 import 가능하면 **새로 작성 금지**
3. 유사 패턴 있으면 **그대로 따라서** 작성
4. 새 유틸 함수 작성 전 **기존 유틸 검색 필수**

**금지 사항:**
- ❌ 이미 존재하는 유틸 함수 재작성
- ❌ 기존 타입과 동일한 타입 재정의
- ❌ 기존 패턴과 다른 새로운 패턴 도입

---

## Phase별 상세 절차

### Phase 0: 프로젝트 루트 확인

`../common/project-root-detection.md` 참조

### Phase 0.5: Spec/Plan 검증

**spec-plan-validator 에이전트 호출:**

```
Task:
- subagent_type: "oh-my-speckit:spec-plan-validator"
- prompt: "spec.md: [경로], plan.md: [경로] 정합성 검증"
```

| 결과 | 액션 |
|------|------|
| ✅ PASS | Phase 1로 |
| ⚠️ WARN | AskUserQuestion으로 진행 여부 확인 |
| ❌ FAIL | 구현 중단, 수정 안내 |

### Phase 1: 문서 로드

```bash
ls ${PROJECT_ROOT}/.specify/specs/
cat ${PROJECT_ROOT}/.specify/specs/[spec-id]/spec.md
cat ${PROJECT_ROOT}/.specify/specs/[spec-id]/plan.md
```

plan.md가 없으면 `/oh-my-speckit:design [spec-id]` 안내

### Phase 1.5: Constitution 확인

```bash
cat ${PROJECT_ROOT}/.specify/memory/constitution.md
```

**code-generator에 전달할 제약사항:**

| Constitution 항목 | 구현 시 적용 |
|------------------|-------------|
| 금지된 패턴 | 해당 패턴 사용 금지 |
| 필수 테스트 | 코드와 함께 테스트 작성 |
| 코딩 컨벤션 | 네이밍/스타일 준수 |
| 타입 안전성 | any 타입 금지 등 |

### Phase 2: 구현 준비

1. plan.md의 구현 단계 파싱
2. 각 Phase의 태스크 목록 추출
3. 진행 상황 확인 (이미 완료된 항목)
4. TaskCreate로 plan.md의 각 Phase를 태스크로 등록

### Phase 3: 코드 구현 (에이전트)

**code-generator 에이전트 호출:**

```
Task:
- subagent_type: "oh-my-speckit:code-generator"
- prompt: |
    plan.md: .specify/specs/[spec-id]/plan.md

    **필수 확인:**
    - plan.md의 "재사용 분석" 섹션 먼저 확인
    - 기존 코드 import 가능하면 새로 작성 금지

    **구현:**
    - plan.md 체크리스트 순서대로 구현
    - 파일 생성/수정 후 체크박스 업데이트 ([ ] → [x])
    - 기존 코드 스타일 따르기

    **완료 조건:**
    - plan.md 모든 구현 체크박스 [x] 완료
```

**결과 처리:**
- ✅ 성공 + 파일 목록 → Phase 4로
- ❌ 에러 → 분석 후 재호출

### Phase 4: 품질 검사 (에이전트)

**quality-checker 에이전트 호출:**

```
Task:
- subagent_type: "oh-my-speckit:quality-checker"
- prompt: |
    검사 항목: 타입 체크, 린트 체크, 코드 스멜, SOLID 원칙
    대상 파일: [Phase 3 결과 파일 목록]
    완료 조건: Critical 이슈 없음
```

**결과 처리:**
- ✅ PASS → Phase 4.5로
- ⚠️ WARN (Minor만) → 경고 기록 후 Phase 4.5로
- ❌ FAIL (Critical/High) → Phase 3 재호출

### Phase 4.5: 테스트 작성 (에이전트)

**test-creator 에이전트 호출:**

```
Task:
- subagent_type: "oh-my-speckit:test-creator"
- prompt: |
    spec.md: [경로] (요구사항 참조)
    plan.md: [경로] (E2E 시나리오 참조)
    대상 파일: [Phase 3 결과 파일 목록]
    완료 조건: 핵심 기능 테스트 코드 작성
```

**test-creator 수행 작업:**
- 프로젝트 테스트 스택 감지 (JUnit, Jest, Playwright 등)
- 기존 테스트 패턴 분석
- 테스트 유형 선택 (단위/통합/E2E)
- 테스트 코드 작성

### Phase 5: 테스트 실행 (에이전트)

**test-runner 에이전트 호출:**

```
Task:
- subagent_type: "oh-my-speckit:test-runner"
- prompt: |
    테스트 범위: 변경된 파일 관련 단위/통합 테스트
    완료 조건: 모든 테스트 통과, 커버리지 ≥ 80%
```

**결과 처리:**
- ✅ PASS → Phase 6으로
- ❌ FAIL:
  - 코드 버그 → Phase 3 재호출
  - 테스트 오류 → Phase 4.5 재호출

### Phase 6: 마무리

```markdown
✅ Implementation Complete

## Summary
| Item | Value |
|------|-------|
| Spec | [spec-id] |
| Files Created | N개 |
| Files Modified | N개 |
| Tests | PASSED |
| Quality | PASSED |

## 다음 단계
- 코드 리뷰 (선택): /oh-my-speckit:review
- 검증: /oh-my-speckit:verify [spec-id]
- PR 생성: gh pr create
```

---

## 에러 처리

### Phase 3 실패 (코드 구현)

```
❌ 코드 구현 실패

에러: [에러 메시지]

→ 에러 분석 후 code-generator 재호출
```

### Phase 4 실패 (품질 검사)

```
❌ 품질 검사 실패

이슈:
- [이슈 목록]

→ Phase 3으로 돌아가 코드 수정 후 재검사
```

### Phase 5 실패 (테스트 실행)

```
❌ 테스트 실패

실패한 테스트:
- [테스트 목록]

원인 분석:
- [test-runner 분석 결과]

→ 코드 또는 테스트 수정 후 재실행
```

---

## 재진입 시나리오

**Verify에서 코드 관련 실패 시:**

```
verify 실패 (타입/린트/테스트/빌드/실행)
    │
    └─ /oh-my-speckit:implement [spec-id]
        ├─ 실패 원인 분석
        ├─ 해당 코드 수정
        └─ verify 재실행 안내
```

### 재진입 유형별 처리

| 실패 유형 | 시작 Phase | 이유 |
|----------|-----------|------|
| 타입/린트 에러 | Phase 3 | 코드 수정 필요 |
| 테스트 실패 | Phase 3 | 코드/테스트 수정 |
| 커버리지 부족 | Phase 4.5 | 테스트 추가 |
| 빌드/실행 실패 | Phase 3 | 코드 수정 |

> **Tip**: 재진입 시 Phase 1~2(문서 로드)는 스킵 가능
