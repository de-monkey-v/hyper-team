# 품질 검사 가이드

각 Phase 완료 후 수행하는 품질 검사 절차.

## 검사 체크리스트

### 1. 타입 검사 (TypeScript)

```bash
# 전체 타입 체크
npx tsc --noEmit

# 특정 파일만
npx tsc --noEmit src/features/user/*.ts
```

**확인 사항:**
- [ ] `any` 타입 사용 최소화
- [ ] `as unknown as Type` 캐스팅 지양
- [ ] 모든 함수에 반환 타입 명시
- [ ] null/undefined 처리 (`?.`, `??` 활용)

### 2. 린트 검사

```bash
# ESLint 실행
npx eslint src/features/user --fix

# Prettier 포맷팅
npx prettier --write src/features/user
```

**확인 사항:**
- [ ] 미사용 import 제거
- [ ] 미사용 변수 제거
- [ ] console.log 제거 (디버깅용)
- [ ] 일관된 코드 스타일

### 3. 보안 검사

**하드코딩 금지 항목:**
- [ ] API 키, 시크릿
- [ ] 데이터베이스 URL
- [ ] 외부 서비스 URL (환경변수 사용)
- [ ] 사용자 크레덴셜

```typescript
// Bad
const API_KEY = 'sk-1234567890';

// Good
const API_KEY = process.env.API_KEY;
```

**입력 검증:**
- [ ] 사용자 입력 sanitization
- [ ] SQL Injection 방지 (ORM 사용)
- [ ] XSS 방지 (React 기본 제공)

### 4. 코드 스멜 탐지

#### Long Method (긴 메서드)
- 50줄 이상의 함수 → 분리 필요
- 들여쓰기 3단계 이상 → 추출 고려

```typescript
// Bad: 긴 함수
async function processOrder(order: Order) {
  // 100줄의 코드...
}

// Good: 분리된 함수
async function processOrder(order: Order) {
  await validateOrder(order);
  await calculateTotal(order);
  await applyDiscounts(order);
  await saveOrder(order);
  await sendNotification(order);
}
```

#### Large Class (큰 클래스)
- 300줄 이상 → 책임 분리 필요
- 10개 이상의 메서드 → 관심사 분리

#### Duplicate Code (중복 코드)
- 동일 로직 3회 이상 반복 → 추출
- 유사 패턴 → 제네릭/추상화

### 5. 복잡도 검사

#### Cyclomatic Complexity
- 10 이하 유지 권장
- if/else, switch, loop 각각 +1

```typescript
// Bad: 복잡도 높음
function calculate(type: string, value: number) {
  if (type === 'A') {
    if (value > 100) { /* ... */ }
    else if (value > 50) { /* ... */ }
    else { /* ... */ }
  } else if (type === 'B') {
    // ...
  }
}

// Good: 전략 패턴
const strategies = {
  A: (value: number) => calculateA(value),
  B: (value: number) => calculateB(value),
};

function calculate(type: keyof typeof strategies, value: number) {
  return strategies[type](value);
}
```

#### 중첩 깊이
- 3단계 이하 유지
- Early return 패턴 활용

```typescript
// Bad: 깊은 중첩
function process(user: User | null) {
  if (user) {
    if (user.isActive) {
      if (user.hasPermission) {
        // 실제 로직
      }
    }
  }
}

// Good: Early return
function process(user: User | null) {
  if (!user) return;
  if (!user.isActive) return;
  if (!user.hasPermission) return;

  // 실제 로직
}
```

## Phase 완료 체크리스트

각 Phase 완료 시 다음 확인:

```markdown
## Phase N 완료 체크리스트

### 필수
- [ ] 타입 에러 없음 (`tsc --noEmit`)
- [ ] 린트 에러 없음 (`eslint`)
- [ ] plan.md 체크박스 업데이트

### 권장
- [ ] 새 함수에 JSDoc 주석
- [ ] 복잡한 로직에 인라인 주석
- [ ] 에러 처리 확인

### 보안
- [ ] 하드코딩된 값 없음
- [ ] 입력 검증 추가
```

## 자동화 스크립트

### check_progress.py 활용

```bash
# 진행률 확인
python3 scripts/check_progress.py
```

출력 예시:
```
📊 구현 진행률: [████████░░░░░░░░░░░░] 40%
   완료: 4/10 tasks
   현재: Phase 2

📋 Phase별 현황:
   ✅ Phase 1: 3/3
   🔄 Phase 2: 1/4
   ⏳ Phase 3: 0/3
```

## 품질 기준

| 항목 | 기준 | 상태 |
|------|------|------|
| 타입 커버리지 | 100% | 필수 |
| 린트 에러 | 0개 | 필수 |
| 테스트 커버리지 | 80%+ | 권장 |
| 복잡도 | ≤10 | 권장 |
| 중첩 깊이 | ≤3 | 권장 |
