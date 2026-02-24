---
name: plan-writing
description: This skill should be used when the user asks to "설계해줘", "설계", "구현 계획", "plan 작성", "plan.md 작성", "plan 만들어", "아키텍처 설계", "아키텍처 계획", "design plan", "create plan", or mentions creating implementation plans from spec. Provides knowledge for analyzing codebase and creating plan.md from spec.
version: 1.0.0
---

# Design

spec.md를 기반으로 코드베이스를 분석하고 구현 계획(plan.md)을 작성하는 지식 가이드.

**Note:** 이 스킬은 specify 커맨드에서 plan 작성 시 리더와 explorer/analyst 팀메이트가 참조합니다.

## 워크플로우 위치

```
specify → implement → verify
↑ 현재 (plan 작성)
```

## 개요

| 항목 | 값 |
|------|-----|
| **입력** | `spec.md` |
| **출력** | `.specify/specs/{id}/plan.md` |
| **핵심 팀메이트** | explorer, analyst |

## 핵심 Phase

| Phase | 내용 | 사용자 승인 |
|-------|------|------------|
| 1 | 스펙 로드 및 파싱 | - |
| **1.5** | **기술 결정 파싱** | - |
| 2 | Constitution 확인 | - |
| 3 | 코드베이스 분석 (팀메이트) | - |
| 3.5 | Breaking Change 분석 | ✅ 설계 방향 (기술 결정에 없을 때만) |
| 4 | Plan 작성 (팀메이트) | ✅ Plan 초안 |
| 5.5 | Spec/Plan 정합성 검증 | - |

## 기술 결정 파싱 (Phase 1.5)

**목적**: specify 단계에서 결정된 사항을 파싱하여 중복 질문 방지

**파싱 대상:**
```markdown
## 기술 결정 (Technical Decisions)

| ID | 결정 항목 | 선택 | 근거 | 결정일 |
|----|----------|------|------|--------|
| TD-1 | API 버전 전략 | V2 신규 생성 | ... | ... |
```

**조건부 질문 스킵:**
- `API 버전 전략` 결정 있음 → Breaking Change 질문 건너뛰기
- `아키텍처 패턴` 결정 있음 → DDD 질문 건너뛰기
- `인증 방식` 결정 있음 → 인증 방식 질문 건너뛰기

## plan.md 핵심 구조

```markdown
# [기능명] Plan

## 메타데이터
- Spec ID: {spec-id}
- Status: Draft | Approved
- Created: YYYY-MM-DD

## FR 매핑

| FR | AC (합격 기준) | Phase | 파일 | 검증 방법 |
|-----|---------------|-------|------|----------|
| FR-001 | [spec.md AC 인용] | 1 | src/... | 단위 테스트 / 통합 테스트 |

## 재사용 분석

| 기존 코드 | 위치 | 재사용 방법 |
|----------|------|------------|
| helper() | src/utils/... | import |

## 변경 파일

### 생성
- src/features/.../new.ts

### 수정
- src/services/existing.ts

## 구현 단계

### Phase 1: [제목]
- [ ] Task 1.1
- [ ] Task 1.2

## E2E 테스트 시나리오

| 시나리오 | 사전조건 | 액션 | 예상 결과 |
|---------|---------|------|----------|
| ... | ... | ... | ... |

## Breaking Change (해당시)
- V2 API: [목록]
- Deprecated: [목록]
```

## 설계 방향 (Phase 2.5)

### 최소 변경 원칙

| 상황 | 권장 접근법 |
|------|-----------|
| 기능 추가 | 새 함수/클래스 생성 |
| 기능 변경 | 래퍼 함수로 확장 |
| 버그 수정 | 최소 범위 수정 |

### 옵션

| 방향 | 설명 |
|------|------|
| A: 확장 중심 | 기존 코드 수정 최소화 (권장) |
| B: 리팩토링 포함 | 기존 코드 개선 함께 |
| C: V2 분리 | Breaking Change 시 |

## 검증 기준 설계 원칙

plan.md의 검증 기준은 implement에서 developer/qa가 직접 사용하는 판단 기준입니다.

| 원칙 | 설명 |
|------|------|
| FR별 구조화 | 각 FR의 AC를 plan에 인용하여 추적 가능 |
| 검증 방법 명시 | 단위/통합/E2E/수동 중 구체적 방법 기재 |
| Phase 연결 | 체크박스에 FR 번호 주석으로 추적성 확보 |

이를 통해 implement 단계에서:
- developer가 체크박스 완료 시 해당 FR/AC 충족 여부를 자가 확인
- qa가 FR 매핑 기반으로 요구사항 충족을 체계적으로 검증

## 재진입

**Verify에서 "plan.md 누락" 실패 시:**

```
verify 실패 → specify 재진입 (plan 작성)
  1. 기존 spec.md, plan.md 로드
  2. 미충족 FR 식별
  3. plan.md 보완
  4. implement로 안내
```

## 디렉토리 구조

```
.specify/specs/{id}/
├── spec.md   ← 입력
└── plan.md   ← 산출물
```

## 다음 단계

```
✅ Design 완료 → /oh-my-speckit:implement {spec-id}
```

## 참고 자료

| 파일 | 설명 |
|------|------|
| `references/plan-template.md` | Plan 문서 전체 템플릿 |
| `references/workflow-detail.md` | Phase별 상세 절차 |
