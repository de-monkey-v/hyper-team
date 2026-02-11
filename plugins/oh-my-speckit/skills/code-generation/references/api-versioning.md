# API Versioning Guide

API 변경 시 버전 관리 상세 가이드.

## 핵심 원칙

**기존 API 직접 수정/삭제 금지** → V2 신규 생성 + 기존 deprecated

기존 API를 바로 수정하면 해당 API를 사용하는 다른 기능이 망가질 수 있음.

## V2 생성이 필요한 경우

| 변경 유형 | V2 필요 | 이유 |
|----------|---------|------|
| 응답 필드 추가 (optional) | ❌ | 하위 호환 유지됨 |
| 응답 필드 삭제/이름 변경 | ✅ | Breaking Change |
| 응답 필드 타입 변경 | ✅ | Breaking Change |
| 요청 파라미터 추가 (optional) | ❌ | 하위 호환 유지됨 |
| 요청 파라미터 삭제 | ✅ | Breaking Change |
| 요청 파라미터 필수화 | ✅ | Breaking Change |
| DTO 구조 변경 | ✅ | Breaking Change |
| 에러 코드/메시지 변경 | ✅ | 클라이언트 에러 핸들링 영향 |

## V2 생성 패턴

### 엔드포인트

```
GET  /api/users       → GET  /api/v2/users
POST /api/auth/login  → POST /api/v2/auth/login
```

### DTO/타입

```typescript
// 기존 (유지, deprecated 처리)
interface UserDto { ... }

// 신규
interface UserDtoV2 { ... }
```

### 서비스/함수

```typescript
// 기존 (유지, deprecated 처리)
function getUsers() { ... }

// 신규
function getUsersV2() { ... }
```

## Deprecated 처리 방법

```typescript
/**
 * @deprecated Use getUsersV2() instead. Will be removed in v3.0.0
 * @see getUsersV2
 */
export async function getUsers(): Promise<UserDto[]> {
  // 기존 로직 유지 (수정하지 않음)
}

/**
 * 새로운 사용자 목록 조회 API
 */
export async function getUsersV2(): Promise<UserDtoV2[]> {
  // 새로운 로직
}
```

## 디렉토리 구조 예시

```
src/
├── interfaces/
│   └── http/
│       ├── v1/           # 기존 API (deprecated)
│       │   └── users.controller.ts
│       └── v2/           # 새 API
│           └── users.controller.ts
├── application/
│   └── use-cases/
│       ├── get-users.use-case.ts      # 기존
│       └── get-users-v2.use-case.ts   # 신규
└── domain/
    └── dto/
        ├── user.dto.ts      # 기존
        └── user-v2.dto.ts   # 신규
```

## 마이그레이션 가이드 작성

V2 생성 시 마이그레이션 가이드 문서화:

```markdown
## Migration: getUsers → getUsersV2

### 변경 사항
- `name` 필드가 `firstName`, `lastName`으로 분리

### Before
```json
{ "name": "John Doe" }
```

### After
```json
{ "firstName": "John", "lastName": "Doe" }
```

### 마이그레이션 방법
1. getUsersV2() 호출로 변경
2. 응답 처리 로직 수정
```

## 체크리스트

구현 시 확인:

- [ ] V2 함수/엔드포인트 생성됨
- [ ] 기존 API 로직 변경 없음
- [ ] @deprecated JSDoc 추가됨
- [ ] V2 DTO 생성됨 (필요시)
- [ ] 라우트 등록됨 (API 엔드포인트인 경우)
- [ ] 마이그레이션 가이드 작성됨
