# 전략적 DDD (Strategic DDD)

프로젝트 규모가 커질수록 전략적 DDD가 중요해집니다.

## Bounded Context 정의

### Bounded Context란

- 특정 도메인 모델이 일관되게 적용되는 경계
- 각 Context 내에서 Ubiquitous Language(공통 언어) 사용
- 하나의 팀이 소유하고 관리

### 정의 방법

1. **비즈니스 능력 기반**: 조직이 제공하는 가치와 서비스 식별
2. **언어적 경계**: 같은 용어가 다른 의미로 사용되는 지점 발견
3. **조직 구조 반영**: 팀 경계와 Context 경계 일치

### 예시 (이커머스)

```
┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐
│  주문 Context   │  │  결제 Context   │  │  배송 Context   │
│  - Order        │  │  - Payment      │  │  - Shipment     │
│  - OrderItem    │  │  - Transaction  │  │  - Tracking     │
│  - Cart         │  │  - Refund       │  │  - Carrier      │
└─────────────────┘  └─────────────────┘  └─────────────────┘
        │                    │                    │
        └────────────────────┴────────────────────┘
                    Domain Events 통신
```

---

## Context Map 패턴

| 패턴 | 설명 | 사용 시나리오 |
|------|------|---------------|
| **Partnership** | 두 팀 긴밀 협력 | 공동 릴리즈, 공동 개발 |
| **Customer-Supplier** | 상류/하류 관계 | 우선순위 협상 필요 |
| **Conformist** | 하류가 상류 모델 수용 | 표준 모델 채택 |
| **Anticorruption Layer** | 변환 계층으로 보호 | 레거시/외부 시스템 연동 |
| **Open Host Service** | 표준화된 프로토콜 제공 | Public API |

### Partnership

두 팀이 긴밀하게 협력하며 공동 릴리즈:

```
┌─────────────┐    긴밀한 협력    ┌─────────────┐
│  주문 팀    │◄───────────────►│  결제 팀    │
│  (Order)    │   공동 릴리즈    │  (Payment)  │
└─────────────┘                  └─────────────┘
```

### Customer-Supplier

상류(Supplier)가 하류(Customer)의 요구사항 반영:

```
┌─────────────┐
│  상품 팀    │ (Supplier)
│  (Product)  │
└──────┬──────┘
       │ API 제공
       ▼
┌─────────────┐
│  주문 팀    │ (Customer)
│  (Order)    │
└─────────────┘
```

### Anticorruption Layer (ACL)

외부 시스템의 영향을 막는 변환 계층:

```typescript
// ACL 예시: 레거시 결제 시스템 연동
@Injectable()
export class PaymentACL {
  constructor(private readonly legacyPaymentClient: LegacyPaymentClient) {}

  async processPayment(order: Order): Promise<PaymentResult> {
    // 우리 도메인 모델 → 레거시 형식 변환
    const legacyRequest = {
      order_no: order.id,
      amount: order.totalAmount.value,
      currency_code: order.totalAmount.currency.code,
    };

    const legacyResponse = await this.legacyPaymentClient.pay(legacyRequest);

    // 레거시 응답 → 우리 도메인 모델 변환
    return PaymentResult.fromLegacy({
      success: legacyResponse.result_code === '0000',
      transactionId: legacyResponse.tx_id,
      errorMessage: legacyResponse.error_msg,
    });
  }
}
```

---

## Event Storming 워크숍

### 목적

도메인 이벤트 기반으로 비즈니스 프로세스 시각화

### 포스트잇 색상

- 🟠 **오렌지**: Domain Event (과거형, "주문됨")
- 🔵 **파랑**: Command (명령형, "주문하기")
- 🟡 **노랑**: Aggregate
- 🟣 **보라**: Policy (비즈니스 규칙)
- 🟢 **초록**: Read Model
- 🔴 **빨강**: 문제점/질문

### 8단계 프로세스

```
1. 도메인 이벤트 자유롭게 추가
   🟠 "주문 생성됨" 🟠 "결제 완료됨" 🟠 "배송 시작됨"

2. 시간순 정렬
   ────────────────────────────────────────────►
   주문생성 → 결제시작 → 결제완료 → 배송시작 → 배송완료

3. 문제점/질문 표시
   🔴 "재고 부족 시?"  🔴 "결제 실패 시?"

4. 중요 전환점(Pivotal Events) 식별
   ⭐ 결제 완료 = 주문 확정

5. Command 추가
   🔵 "주문하기" → 🟠 "주문 생성됨"

6. Policy 정의
   🟣 "결제 완료 시 재고 차감"

7. Read Model 추가
   🟢 "주문 목록" 🟢 "배송 현황"

8. 외부 시스템 통합점 식별
   📦 "결제 게이트웨이" 📦 "배송사 API"
```

### Event Storming 결과물 예시

```
┌─────────────────────────────────────────────────────────────────────┐
│                         주문 Context                                │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  🔵 주문하기  ──►  🟡 Order  ──►  🟠 주문 생성됨                   │
│                                         │                          │
│                                         ▼                          │
│                                   🟣 재고 확인 Policy               │
│                                         │                          │
│                                    ─────┴─────                     │
│                                   │           │                    │
│                                   ▼           ▼                    │
│                         🟠 재고 확보됨    🟠 재고 부족              │
│                              │                  │                  │
│                              ▼                  ▼                  │
│                      🔵 결제 요청        🟠 주문 취소됨             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 실전 적용 가이드

### Bounded Context 경계 결정 체크리스트

- [ ] 같은 용어가 다른 의미로 사용되는 곳이 있는가?
- [ ] 독립적으로 배포/확장 가능한 단위인가?
- [ ] 하나의 팀이 책임질 수 있는 범위인가?
- [ ] 데이터 일관성 경계가 명확한가?

### Context Map 선택 가이드

| 상황 | 권장 패턴 |
|------|----------|
| 같은 조직, 긴밀한 협업 | Partnership |
| 명확한 공급자/소비자 관계 | Customer-Supplier |
| 외부 API 연동 | Conformist 또는 ACL |
| 레거시 시스템 연동 | Anticorruption Layer |
| Public API 제공 | Open Host Service |
