---
name: code-quality
description: This skill should be used when the user asks to "분석해줘", "코드 리뷰해줘", "코드 품질 확인", "analyze code", "code review", "check code quality", "코드 스멜 찾아줘", "SOLID 원칙 확인", "DRY 위반 찾기", "복잡도 분석", or mentions code quality, best practices, clean code, refactoring suggestions.
version: 1.0.0
user-invocable: true
---

# Code Quality Analysis

코드 품질을 분석하고 개선점을 제안하는 스킬.

## 분석 항목

### 1. 코드 스멜 (Code Smells)

| 스멜 유형 | 설명 | 심각도 |
|----------|------|--------|
| Long Method | 50줄 이상의 메서드 | Medium |
| Large Class | 300줄 이상의 클래스 | Medium |
| Duplicate Code | 동일/유사 코드 반복 | High |
| Dead Code | 사용되지 않는 코드 | Low |
| Magic Numbers | 의미 없는 상수 | Low |
| God Object | 너무 많은 책임을 가진 객체 | High |
| Feature Envy | 다른 클래스 데이터를 과도하게 사용 | Medium |

### 2. SOLID 원칙

| 원칙 | 확인 사항 |
|-----|----------|
| **S**ingle Responsibility | 클래스/함수가 하나의 책임만 갖는가 |
| **O**pen/Closed | 확장에는 열려있고 수정에는 닫혀있는가 |
| **L**iskov Substitution | 하위 타입이 상위 타입을 대체할 수 있는가 |
| **I**nterface Segregation | 인터페이스가 작고 구체적인가 |
| **D**ependency Inversion | 추상화에 의존하는가, 구체화에 의존하는가 |

### 3. DRY (Don't Repeat Yourself)

- 중복된 코드 블록 탐지
- 유사한 로직 패턴 식별
- 공통 추상화 기회 제안

### 4. 네이밍 & 가독성

| 항목 | 좋은 예 | 나쁜 예 |
|-----|--------|--------|
| 변수명 | `userAge`, `isValid` | `x`, `flag` |
| 함수명 | `calculateTotal()` | `calc()` |
| 클래스명 | `UserRepository` | `UR` |
| 상수명 | `MAX_RETRY_COUNT` | `MRC` |

### 5. 복잡도

| 메트릭 | 기준 | 상태 |
|-------|-----|------|
| Cyclomatic Complexity | ≤10 | 양호 |
| Cyclomatic Complexity | 11-20 | 주의 |
| Cyclomatic Complexity | >20 | 위험 |
| 함수 매개변수 | ≤4 | 양호 |
| 중첩 깊이 | ≤3 | 양호 |

## 분석 절차

1. **대상 파일 식별**: 분석할 파일/디렉토리 확인
2. **파일 읽기**: Read 도구로 코드 읽기
3. **항목별 분석**: 위 5개 항목 순차 분석
4. **결과 정리**: 마크다운 표로 결과 출력
5. **개선 제안**: 우선순위별 개선 방안 제시

## 출력 형식

분석 결과는 다음 형식으로 출력:

```markdown
## 코드 품질 분석 결과

### 요약

| 항목 | 상태 | 이슈 수 |
|-----|------|--------|
| 코드 스멜 | ⚠️ | 3 |
| SOLID | ✅ | 0 |
| DRY | ⚠️ | 2 |
| 네이밍 | ✅ | 0 |
| 복잡도 | ❌ | 1 |

### 상세 이슈

| 파일 | 라인 | 이슈 | 심각도 | 설명 |
|-----|-----|-----|--------|-----|
| src/user.ts | 45-120 | Long Method | Medium | processUser 함수가 75줄 |

### 개선 제안

1. **[High]** 중복 코드 제거: ...
2. **[Medium]** 메서드 분리: ...
```

## 언어별 고려사항

### TypeScript/JavaScript
- ESLint/TSLint 규칙 참고
- 타입 안전성 확인
- async/await 패턴 검토

### Python
- PEP 8 스타일 가이드 참고
- 타입 힌트 사용 여부
- Pythonic 코드 패턴

### Java
- Java Code Conventions 참고
- 접근 제한자 적절성
- 예외 처리 패턴

## 사용 예시

"src/services 폴더의 코드 품질을 분석해줘"
→ 해당 폴더의 모든 파일을 분석하고 결과 표 출력

"이 함수 리팩토링 제안해줘"
→ 해당 함수의 문제점과 개선된 코드 제안
