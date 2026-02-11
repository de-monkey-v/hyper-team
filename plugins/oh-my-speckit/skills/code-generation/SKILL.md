---
name: code-generation
description: This skill should be used when the user asks to "구현해줘", "코드 작성", "개발 시작", "개발해줘", "만들어줘", "기능 구현", "implement", "build this", or mentions implementing features from plan.md. Provides knowledge for code implementation.
version: 1.0.0
---

# Implement

plan.md 기반으로 코드를 생성하는 지식 가이드.

**Note:** 이 스킬은 implement 커맨드의 implementer 팀메이트가 참조합니다.

## 워크플로우 위치

```
specify → implement → verify
          ↑ 현재
```

## 개요

| 항목 | 값 |
|------|-----|
| **입력** | `.specify/specs/{id}/plan.md` (체크리스트) |
| **출력** | 실제 코드 파일 (src/, tests/) |

## 핵심 원칙: 기존 코드 우선 (CRITICAL)

### 1. 기존 코드 검색 체크리스트

**유틸 함수 작성 전:**
```bash
# 이미 존재하는지 확인
grep -r "function 함수명" src/
grep -r "export.*함수명" src/
```

**타입 정의 전:**
```bash
# 이미 정의되어 있는지 확인
grep -r "interface 타입명" src/
grep -r "type 타입명" src/
```

**컴포넌트 작성 전:**
```bash
# 유사 컴포넌트 확인
ls src/components/ | grep -i "컴포넌트명"
```

### 2. 재사용 우선순위

1. **직접 Import** (최우선)
   ```typescript
   // 기존 유틸 그대로 사용
   import { validateEmail } from '@/utils/validation';
   ```

2. **확장/래핑** (2순위)
   ```typescript
   // 기존 함수를 확장
   import { baseValidate } from '@/utils/validation';
   export const validateUser = (user: User) => {
     baseValidate(user.email);
     // 추가 검증만
   };
   ```

3. **패턴 복사 후 수정** (3순위)
   ```typescript
   // 기존 패턴 참고하여 작성
   // 참고: src/features/auth/useAuth.ts
   ```

4. **새로 작성** (최후 수단)
   - 위 3가지 모두 불가능할 때만

### 3. 금지 패턴

**절대 하지 말 것:**

```typescript
// ❌ 이미 있는 유틸 재작성
export function formatDate(date: Date) { ... }
// 이미 src/utils/date.ts에 있음!

// ❌ 기존 타입과 중복
interface UserResponse { ... }
// 이미 src/types/user.ts에 있음!

// ❌ 기존 패턴 무시하고 새 패턴
// 기존: src/api/users.ts - React Query 사용
// 새로: fetch로 직접 호출 ← 금지!
```

## 생성 원칙

### 1. plan.md 체크리스트 순서 준수

plan.md의 Phase별 체크박스를 **순서대로** 구현:

```markdown
### Phase 1: 도메인 모델
- [ ] User 엔티티 생성        ← 1번째
- [ ] UserRepository 인터페이스 ← 2번째
- [ ] CreateUserUseCase       ← 3번째
```

### 2. 완료 시 체크박스 업데이트

각 항목 완료 후 plan.md 수정:
```markdown
- [x] User 엔티티 생성  ← 완료 표시
```

### 3. 점진적 구현

한 번에 모든 코드를 생성하지 않고, 파일 단위로 점진적 구현:
1. 파일 생성/수정
2. 관련 import 확인
3. 타입 체크 (필요시)
4. 다음 파일로 이동

## 결과 처리

### 성공 시

```markdown
✅ Phase N 완료
- [x] 항목1
- [x] 항목2

파일 변경:
- src/features/user/UserService.ts (생성)
- src/types/user.ts (수정)
```

### 실패 시

```markdown
❌ Phase N 실패: [오류 메시지]

시도한 내용:
- [시도한 작업]

오류 원인:
- [원인 분석]

권장 조치:
- [해결 방법 제안]
```

## 재진입

| 상황 | 재진입 지점 | 처리 방법 |
|------|------------|----------|
| **Phase 중간 중단** | 마지막 완료 항목 다음 | 1. plan.md에서 완료 항목 확인<br>2. 다음 미완료 항목부터 재개 |
| **타입 에러 발생** | 에러 발생 파일 | 1. 에러 메시지 분석<br>2. 타입 정의 수정<br>3. 재검증 |
| **Verify 실패** | 실패한 검증 항목 | 1. 실패 원인 분석<br>2. 관련 코드 수정<br>3. plan.md 재확인 |

## 다음 단계

```
✅ 구현 완료 → /oh-my-speckit:verify {spec-id}
```

## 참고 자료

### Reference Files
- **`references/code-generation.md`** - 코드 생성 상세 가이드 (네이밍, 패턴, 에러 처리)
- **`references/quality-checking.md`** - 품질 검사 체크리스트 (타입 검사, 린트, 보안)
- **`references/architecture-patterns.md`** - 아키텍처 패턴 (Clean Architecture, SOLID)
- **`references/api-versioning.md`** - API 버전 관리 (V2 생성, deprecated 처리)
