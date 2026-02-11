# 코드 재사용 가이드라인

코드 작성 시 기존 코드를 최대한 활용하기 위한 지침.

## 핵심 원칙

1. **검색 우선**: 작성 전 항상 기존 코드 검색
2. **재사용 우선**: 새로 작성보다 기존 코드 활용
3. **확장 우선**: 수정보다 확장 선호
4. **최소 변경**: 꼭 필요한 부분만 변경

---

## 검색 명령어 치트시트

| 목적 | 명령어 |
|------|--------|
| 함수 찾기 | `grep -r "function 이름" src/` |
| 타입 찾기 | `grep -r "interface\|type 이름" src/` |
| 컴포넌트 찾기 | `ls src/components/ \| grep -i "이름"` |
| 훅 찾기 | `grep -r "export.*use" src/` |
| 유틸 찾기 | `ls src/utils/ && grep -r "export" src/utils/` |
| API 찾기 | `grep -r "async.*fetch\|axios" src/` |
| 상수 찾기 | `grep -r "const.*=" src/constants/` |

---

## 재사용 결정 트리

```
기능 구현 필요
    │
    ├─ 기존에 동일 기능 있음? ──Yes──► Import하여 사용
    │       │
    │       No
    │       │
    ├─ 유사 기능 있음? ──Yes──► 확장/래핑하여 사용
    │       │
    │       No
    │       │
    ├─ 유사 패턴 있음? ──Yes──► 패턴 복사 후 수정
    │       │
    │       No
    │       │
    └─ 모두 없음? ──► 새로 작성 (근거 명시)
```

---

## 활용 방법별 예제

### 1. 직접 Import (최우선)

```typescript
// ✅ 기존 유틸 그대로 사용
import { formatDate, parseDate } from '@/utils/date';
import { validateEmail, validatePhone } from '@/utils/validation';

// 사용
const formattedDate = formatDate(new Date());
```

### 2. 확장/래핑 (2순위)

```typescript
// ✅ 기존 함수를 확장
import { baseValidate } from '@/utils/validation';

export const validateUserInput = (input: UserInput) => {
  // 기존 검증 활용
  baseValidate(input.email, 'email');
  baseValidate(input.phone, 'phone');

  // 추가 검증만 새로 작성
  if (input.age < 0) throw new Error('Invalid age');
};
```

### 3. 패턴 복사 후 수정 (3순위)

```typescript
// ✅ 기존 패턴 참고
// 참고: src/features/auth/useAuth.ts

// 같은 패턴으로 새 훅 작성
export const useProfile = () => {
  // useAuth와 동일한 구조 사용
  const [data, setData] = useState<Profile | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  // ...
};
```

### 4. 새로 작성 (최후 수단)

```typescript
// ⚠️ 기존에 없어서 새로 작성
// 검색 결과: grep -r "calculateTax" src/ → 결과 없음

export const calculateTax = (amount: number, rate: number): number => {
  return amount * rate;
};
```

---

## 금지 사항

### ❌ 절대 하지 말 것

| 금지 | 이유 | 대안 |
|------|------|------|
| 이미 있는 유틸 재작성 | 중복, 불일치 | import |
| 기존 타입 재정의 | 타입 불일치 | import |
| 기존 패턴 무시 | 일관성 저해 | 패턴 따르기 |
| 불필요한 기존 코드 수정 | 사이드 이펙트 | 확장 |
| 기존과 다른 라이브러리 사용 | 의존성 증가 | 기존 라이브러리 사용 |

### 구체적인 금지 예시

```typescript
// ❌ 기존 formatDate가 있는데 새로 작성
export function formatDate(date: Date) { ... }
// src/utils/date.ts에 이미 있음!

// ❌ 기존 UserResponse 타입이 있는데 새로 정의
interface UserResponse { id: string; name: string; }
// src/types/user.ts에 이미 있음!

// ❌ 기존 프로젝트가 React Query 사용하는데 fetch 직접 호출
const data = await fetch('/api/users');
// 기존 패턴: useQuery로 호출해야 함!

// ❌ 기존 프로젝트가 Zustand 사용하는데 Redux 도입
import { createStore } from 'redux';
// 기존 패턴: Zustand 사용해야 함!
```

---

## Over-Engineering 방지

### YAGNI 원칙 (You Ain't Gonna Need It)

| 상황 | ❌ 과도함 | ✅ 충분함 |
|-----|---------|---------|
| 함수 1개 필요 | Factory 패턴 | 단순 함수 |
| 옵션 1개 필요 | Strategy 패턴 | if문 |
| 의존성 1개 | DI Container | 직접 import |
| 구현체 1개 | 인터페이스 + 구현 | 구현만 |
| 설정 1개 | 설정 파일 + 파서 | 상수로 선언 |

### 충분한 수준 예시

```typescript
// ❌ 과도한 추상화
interface IUserRepository {
  findById(id: string): Promise<User>;
}
class UserRepositoryImpl implements IUserRepository { ... }
const container = new DIContainer();
container.register<IUserRepository>('userRepo', UserRepositoryImpl);

// ✅ 충분한 수준 (구현체 1개일 때)
export const userRepository = {
  findById: async (id: string): Promise<User> => { ... }
};
```

---

## 체크리스트

### 코드 작성 전
- [ ] 비슷한 함수/타입이 있는지 검색했는가?
- [ ] plan.md의 "재사용 분석" 섹션을 확인했는가?
- [ ] 기존 패턴과 일관성이 있는가?

### 코드 작성 후
- [ ] 새로 작성한 코드가 기존 코드와 중복되지 않는가?
- [ ] 불필요한 추상화/패턴을 도입하지 않았는가?
- [ ] 기존 프로젝트의 라이브러리를 사용했는가?
