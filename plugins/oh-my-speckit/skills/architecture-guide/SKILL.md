---
name: architecture-guide
description: This skill should be used when the user asks to "아키텍처 설계", "아키텍처 설계해줘", "architecture design", "백엔드 구조", "프론트엔드 구조", "구조 설계해줘", "Clean Architecture", "DDD", "Hexagonal", "Modular Monolith", "Vertical Slice", "VSA", "Event-Driven", "Serverless", "AI 통합", "REST vs GraphQL vs gRPC", or mentions designing system architecture. Provides comprehensive guidance for modern backend/frontend architecture patterns.
version: 1.0.0
user-invocable: true
---

# Architecture Guide

현대적인 백엔드/프론트엔드 아키텍처 패턴 가이드.

이 스킬은 specify/implement 커맨드의 explorer, analyst, implementer 팀메이트가 참조합니다.

## 아키텍처 선택 가이드

### 프로젝트 규모별 권장

| 규모 | 백엔드 | 프론트엔드 |
|------|--------|-----------|
| **소규모** (1-5명) | 단순 레이어드 또는 모듈러 모놀리스 (라이트) | 폴더 기반 구조 |
| **중규모** (5-10명) | Clean Architecture 라이트 또는 모듈러 모놀리스 | Feature-Sliced (간소화) |
| **대규모** (10명+) | Full Clean Architecture + DDD 또는 마이크로서비스 | Full Feature-Sliced |

### 아키텍처 비교

| 아키텍처 | 복잡성 | 적합 케이스 |
|---------|-------|------------|
| **Clean Architecture** | 중~높음 | 복잡한 도메인 로직, 장기 유지보수 |
| **Modular Monolith** | 중간 | 마이크로서비스 준비, 5-20명 팀 |
| **Vertical Slice** | 낮~중 | 빠른 기능 개발, CRUD 중심, MVP |
| **Microservices** | 높음 | 20명+ 팀, 독립적 배포 필요 |

---

## 백엔드 아키텍처

### Clean Architecture + DDD

**상세 가이드**: `references/ddd-strategic.md`, `references/ddd-tactical.md` 참조

권장 구조:
```
src/
├── domain/           # 핵심 비즈니스 로직 (순수 엔티티, 값 객체)
│   ├── entities/
│   ├── value-objects/
│   └── events/
├── application/      # 유스케이스, 애플리케이션 서비스
│   ├── use-cases/
│   ├── ports/
│   └── services/
├── infrastructure/   # 외부 시스템 연동 (DB, API, 메시징)
│   ├── repositories/
│   ├── adapters/
│   └── config/
└── interfaces/       # 진입점 (API, CLI, 이벤트 핸들러)
    ├── http/
    ├── graphql/
    └── events/
```

**핵심 원칙**:
| 원칙 | 설명 |
|------|------|
| **의존성 방향** | 외부 → 내부 (domain은 외부 의존 없음) |
| **포트/어댑터** | 인터페이스로 외부 시스템 추상화 |
| **유스케이스 중심** | 비즈니스 로직은 use-case 단위로 캡슐화 |

---

### Modular Monolith

**상세 가이드**: `references/modular-monolith.md` 참조

마이크로서비스의 복잡성 없이 모듈화의 이점을 얻는 아키텍처.

```
src/modules/
├── user/                    # User 모듈
│   ├── domain/
│   ├── application/
│   ├── infrastructure/
│   ├── interfaces/
│   └── index.ts             # Public API
├── order/                   # Order 모듈
└── shared/                  # 공유 코드
```

**모듈 간 통신 규칙**:
- ❌ 다른 모듈 내부 직접 접근
- ✅ Public API (index.ts) 통해서만 접근
- ✅ 이벤트 기반 통신

---

### Vertical Slice Architecture

**상세 가이드**: `references/vertical-slice.md` 참조

기능(Feature) 단위로 코드를 구성하는 아키텍처.

```
src/features/
├── orders/
│   ├── commands/
│   │   ├── create-order/
│   │   │   ├── CreateOrderCommand.ts
│   │   │   ├── CreateOrderHandler.ts
│   │   │   └── CreateOrderEndpoint.ts
│   │   └── cancel-order/
│   ├── queries/
│   │   └── get-order/
│   └── shared/
└── shared/
```

**Clean Architecture vs VSA**:
| 상황 | 권장 |
|------|------|
| 복잡한 도메인 로직 | Clean Architecture |
| 빠른 기능 개발, MVP | Vertical Slice |
| CRUD 중심 | Vertical Slice |
| 비즈니스 규칙 복잡 | Clean Architecture + DDD |

---

### API 설계

**상세 가이드**: `references/api-design.md` 참조

| 프로토콜 | 사용 케이스 |
|---------|------------|
| **REST** | Public API, 단순 CRUD |
| **GraphQL** | 복잡한 데이터 요구, 모바일 앱 |
| **gRPC** | 마이크로서비스 간 통신 |

---

### Event-Driven Architecture

**상세 가이드**: `references/event-driven.md` 참조

이벤트 기반 비동기 아키텍처.

**핵심 패턴**:
| 패턴 | 설명 |
|------|------|
| **Outbox Pattern** | DB 트랜잭션 + 이벤트 발행 원자성 보장 |
| **Saga Pattern** | 분산 트랜잭션 관리 (보상 트랜잭션) |
| **Event Sourcing** | 상태를 이벤트 시퀀스로 저장 |

---

### Serverless Architecture

**상세 가이드**: `references/serverless.md` 참조

서버 관리 없이 코드 실행에 집중.

**적합 케이스**:
- ✅ API 백엔드, 이벤트 처리, Cron 작업
- ❌ 장시간 실행, WebSocket, 고빈도 실시간 처리

---

### AI/ML 통합

**상세 가이드**: `references/ai-ml-integration.md` 참조

**핵심 패턴**:
| 패턴 | 설명 |
|------|------|
| **AI Gateway** | 여러 AI 제공자 추상화 |
| **RAG Pipeline** | 문서 검색 + LLM 응답 생성 |
| **Vector Store** | 임베딩 기반 유사도 검색 |

---

## 프론트엔드 아키텍처

### Feature-Sliced Design

```
src/
├── app/              # 앱 설정, 프로바이더, 라우팅
├── pages/            # 페이지 컴포넌트 (라우트 매핑)
├── widgets/          # 독립적인 UI 블록 (헤더, 사이드바)
├── features/         # 사용자 시나리오 (로그인, 장바구니)
├── entities/         # 비즈니스 엔티티 (User, Product)
└── shared/           # 공용 유틸, UI 컴포넌트, 훅
    ├── ui/
    ├── lib/
    └── api/
```

**핵심 원칙**:
| 원칙 | 설명 |
|------|------|
| **단방향 의존** | 상위 레이어만 하위 레이어 참조 |
| **Feature 격리** | 각 feature는 독립적으로 동작 |
| **Colocation** | 관련 코드는 가까이 배치 |
| **Server Components** | 가능한 서버에서 렌더링 |

### 기술 스택 권장

- **프레임워크**: Next.js 15+ (App Router)
- **상태 관리**: Zustand (클라이언트), React Query (서버 상태)
- **스타일**: Tailwind CSS, CSS Modules
- **폼**: React Hook Form + Zod
- **테스트**: Vitest, Testing Library, Playwright

---

## API 버전 정책

### 핵심 원칙

**기존 API 직접 수정/삭제 금지** → V2 신규 생성 + 기존 deprecated

### V2 생성이 필요한 경우

| 변경 유형 | V2 필요 |
|----------|---------|
| 응답 필드 추가 (optional) | ❌ |
| 응답 필드 삭제/이름 변경 | ✅ |
| 요청 파라미터 필수화 | ✅ |
| DTO 구조 변경 | ✅ |

### V2 생성 패턴

```typescript
/**
 * @deprecated Use getUsersV2() instead. Will be removed in v3.0.0
 */
export async function getUsers(): Promise<UserDto[]> {
  // 기존 로직 유지
}

export async function getUsersV2(): Promise<UserDtoV2[]> {
  // 새로운 로직
}
```

---

## Anti-Patterns 피하기

**백엔드**:
- ❌ 컨트롤러에 비즈니스 로직
- ❌ 엔티티에서 외부 서비스 직접 호출
- ❌ Repository에서 비즈니스 로직 처리

**프론트엔드**:
- ❌ 컴포넌트에서 직접 API 호출 (React Query 사용)
- ❌ Props Drilling (Context 또는 상태 관리 사용)
- ❌ 거대한 컴포넌트 (합성 패턴 사용)

---

## 실전 예시

상세 코드 예시는 `examples/` 디렉토리 참조:

### 기본 아키텍처
| 예시 | 설명 |
|------|------|
| `examples/backend-auth-flow.md` | 로그인 기능 (Clean Architecture) |
| `examples/backend-crud-flow.md` | CRUD 기능 (Product 예시) |
| `examples/frontend-feature-flow.md` | 장바구니 (Feature-Sliced) |
| `examples/api-v2-migration.md` | V2 API 마이그레이션 |

### 고급 아키텍처
| 예시 | 설명 |
|------|------|
| `examples/modular-monolith-example.md` | 이커머스 모듈러 모놀리스 |
| `examples/vertical-slice-example.md` | 상품 관리 (VSA + CQRS) |
| `examples/event-driven-example.md` | 주문 처리 EDA |
| `examples/ai-integration-example.md` | 지능형 고객 지원 (RAG) |

---

## 상세 레퍼런스

| 주제 | 파일 |
|------|------|
| 전략적 DDD | `references/ddd-strategic.md` |
| 전술적 DDD | `references/ddd-tactical.md` |
| 모듈러 모놀리스 | `references/modular-monolith.md` |
| Vertical Slice | `references/vertical-slice.md` |
| API 설계 | `references/api-design.md` |
| Event-Driven | `references/event-driven.md` |
| Serverless | `references/serverless.md` |
| AI/ML 통합 | `references/ai-ml-integration.md` |

---

## 관련 명령어

| 명령어 | 설명 |
|-------|------|
| `/oh-my-speckit:specify` | 아키텍처 인식 스펙 작성 |
| `/oh-my-speckit:implement` | 아키텍처 기반 구현 |
| `/oh-my-speckit:verify` | 구현 검증 |
