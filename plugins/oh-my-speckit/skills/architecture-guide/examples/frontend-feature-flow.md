# 실전 예시: 프론트엔드 기능 (Feature-Sliced Design)

## 시나리오: 장바구니 기능

Next.js App Router + Feature-Sliced Design으로 장바구니 기능 구현.

## 디렉토리 구조

```
src/
├── app/
│   └── cart/
│       └── page.tsx              # 장바구니 페이지
├── widgets/
│   └── cart-summary/
│       ├── ui/
│       │   └── CartSummary.tsx   # 장바구니 요약 위젯
│       └── index.ts
├── features/
│   └── cart/
│       ├── add-to-cart/
│       │   ├── ui/
│       │   │   └── AddToCartButton.tsx
│       │   ├── model/
│       │   │   └── useAddToCart.ts
│       │   └── index.ts
│       ├── update-quantity/
│       │   ├── ui/
│       │   │   └── QuantitySelector.tsx
│       │   └── index.ts
│       └── remove-from-cart/
│           ├── ui/
│           │   └── RemoveButton.tsx
│           └── index.ts
├── entities/
│   └── cart/
│       ├── model/
│       │   ├── types.ts
│       │   └── store.ts          # Zustand store
│       ├── api/
│       │   └── cartApi.ts        # React Query
│       ├── ui/
│       │   └── CartItem.tsx
│       └── index.ts
└── shared/
    ├── ui/
    │   ├── Button.tsx
    │   └── Card.tsx
    ├── lib/
    │   └── formatPrice.ts
    └── api/
        └── client.ts
```

## 1. Shared Layer

### shared/ui/Button.tsx

```tsx
// src/shared/ui/Button.tsx
import { ButtonHTMLAttributes, forwardRef } from 'react';
import { cn } from '../lib/utils';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md' | 'lg';
  loading?: boolean;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', loading, disabled, children, ...props }, ref) => {
    return (
      <button
        ref={ref}
        disabled={disabled || loading}
        className={cn(
          'inline-flex items-center justify-center rounded-md font-medium transition-colors',
          'focus:outline-none focus:ring-2 focus:ring-offset-2',
          'disabled:opacity-50 disabled:cursor-not-allowed',
          {
            'bg-blue-600 text-white hover:bg-blue-700': variant === 'primary',
            'bg-gray-100 text-gray-900 hover:bg-gray-200': variant === 'secondary',
            'bg-red-600 text-white hover:bg-red-700': variant === 'danger',
            'hover:bg-gray-100': variant === 'ghost',
          },
          {
            'px-3 py-1.5 text-sm': size === 'sm',
            'px-4 py-2 text-sm': size === 'md',
            'px-6 py-3 text-base': size === 'lg',
          },
          className,
        )}
        {...props}
      >
        {loading ? (
          <span className="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent" />
        ) : null}
        {children}
      </button>
    );
  },
);

Button.displayName = 'Button';
```

### shared/lib/formatPrice.ts

```typescript
// src/shared/lib/formatPrice.ts
export function formatPrice(price: number, currency = 'KRW'): string {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency,
  }).format(price);
}
```

## 2. Entities Layer

### entities/cart/model/types.ts

```typescript
// src/entities/cart/model/types.ts
export interface CartItem {
  id: string;
  productId: string;
  productName: string;
  productImage: string;
  price: number;
  quantity: number;
}

export interface Cart {
  items: CartItem[];
  totalItems: number;
  totalPrice: number;
}
```

### entities/cart/model/store.ts (Zustand)

```typescript
// src/entities/cart/model/store.ts
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { CartItem } from './types';

interface CartState {
  items: CartItem[];

  // Actions
  addItem: (item: Omit<CartItem, 'quantity'>) => void;
  removeItem: (id: string) => void;
  updateQuantity: (id: string, quantity: number) => void;
  clearCart: () => void;

  // Computed
  getTotalItems: () => number;
  getTotalPrice: () => number;
}

export const useCartStore = create<CartState>()(
  persist(
    (set, get) => ({
      items: [],

      addItem: (newItem) => {
        set((state) => {
          const existingItem = state.items.find(
            (item) => item.productId === newItem.productId,
          );

          if (existingItem) {
            return {
              items: state.items.map((item) =>
                item.productId === newItem.productId
                  ? { ...item, quantity: item.quantity + 1 }
                  : item,
              ),
            };
          }

          return {
            items: [
              ...state.items,
              { ...newItem, id: crypto.randomUUID(), quantity: 1 },
            ],
          };
        });
      },

      removeItem: (id) => {
        set((state) => ({
          items: state.items.filter((item) => item.id !== id),
        }));
      },

      updateQuantity: (id, quantity) => {
        if (quantity <= 0) {
          get().removeItem(id);
          return;
        }

        set((state) => ({
          items: state.items.map((item) =>
            item.id === id ? { ...item, quantity } : item,
          ),
        }));
      },

      clearCart: () => {
        set({ items: [] });
      },

      getTotalItems: () => {
        return get().items.reduce((sum, item) => sum + item.quantity, 0);
      },

      getTotalPrice: () => {
        return get().items.reduce(
          (sum, item) => sum + item.price * item.quantity,
          0,
        );
      },
    }),
    {
      name: 'cart-storage',
    },
  ),
);
```

### entities/cart/api/cartApi.ts (React Query)

```typescript
// src/entities/cart/api/cartApi.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { Cart, CartItem } from '../model/types';

// 서버 장바구니 조회 (로그인 사용자)
export function useServerCart() {
  return useQuery({
    queryKey: ['cart'],
    queryFn: async (): Promise<Cart> => {
      const response = await apiClient.get('/api/cart');
      return response.data;
    },
  });
}

// 서버에 장바구니 동기화
export function useSyncCart() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (items: CartItem[]) => {
      const response = await apiClient.post('/api/cart/sync', { items });
      return response.data;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
    },
  });
}

// 장바구니 비우기 (서버)
export function useClearServerCart() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async () => {
      await apiClient.delete('/api/cart');
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cart'] });
    },
  });
}
```

### entities/cart/ui/CartItem.tsx

```tsx
// src/entities/cart/ui/CartItem.tsx
import Image from 'next/image';
import { CartItem as CartItemType } from '../model/types';
import { formatPrice } from '@/shared/lib/formatPrice';
import { Card } from '@/shared/ui/Card';

interface CartItemProps {
  item: CartItemType;
  actions?: React.ReactNode;  // Feature에서 주입
}

export function CartItem({ item, actions }: CartItemProps) {
  return (
    <Card className="flex gap-4 p-4">
      <div className="relative h-24 w-24 flex-shrink-0 overflow-hidden rounded-md">
        <Image
          src={item.productImage}
          alt={item.productName}
          fill
          className="object-cover"
        />
      </div>

      <div className="flex flex-1 flex-col">
        <h3 className="font-medium">{item.productName}</h3>
        <p className="text-lg font-semibold text-blue-600">
          {formatPrice(item.price)}
        </p>
        <p className="text-sm text-gray-500">
          소계: {formatPrice(item.price * item.quantity)}
        </p>
      </div>

      {/* Feature에서 주입된 액션 버튼들 */}
      <div className="flex items-center gap-2">{actions}</div>
    </Card>
  );
}
```

## 3. Features Layer

### features/cart/add-to-cart/ui/AddToCartButton.tsx

```tsx
// src/features/cart/add-to-cart/ui/AddToCartButton.tsx
'use client';

import { Button } from '@/shared/ui/Button';
import { useAddToCart } from '../model/useAddToCart';

interface AddToCartButtonProps {
  product: {
    id: string;
    name: string;
    image: string;
    price: number;
  };
}

export function AddToCartButton({ product }: AddToCartButtonProps) {
  const { addToCart, isAdding } = useAddToCart();

  const handleClick = () => {
    addToCart({
      productId: product.id,
      productName: product.name,
      productImage: product.image,
      price: product.price,
    });
  };

  return (
    <Button onClick={handleClick} loading={isAdding}>
      장바구니 담기
    </Button>
  );
}
```

### features/cart/add-to-cart/model/useAddToCart.ts

```typescript
// src/features/cart/add-to-cart/model/useAddToCart.ts
'use client';

import { useState } from 'react';
import { useCartStore } from '@/entities/cart';
import { toast } from 'sonner';

interface AddToCartParams {
  productId: string;
  productName: string;
  productImage: string;
  price: number;
}

export function useAddToCart() {
  const [isAdding, setIsAdding] = useState(false);
  const addItem = useCartStore((state) => state.addItem);

  const addToCart = async (params: AddToCartParams) => {
    setIsAdding(true);

    try {
      // 낙관적 업데이트
      addItem(params);
      toast.success('장바구니에 추가되었습니다');
    } catch (error) {
      toast.error('장바구니 추가에 실패했습니다');
    } finally {
      setIsAdding(false);
    }
  };

  return { addToCart, isAdding };
}
```

### features/cart/update-quantity/ui/QuantitySelector.tsx

```tsx
// src/features/cart/update-quantity/ui/QuantitySelector.tsx
'use client';

import { Button } from '@/shared/ui/Button';
import { useCartStore } from '@/entities/cart';

interface QuantitySelectorProps {
  itemId: string;
  quantity: number;
}

export function QuantitySelector({ itemId, quantity }: QuantitySelectorProps) {
  const updateQuantity = useCartStore((state) => state.updateQuantity);

  return (
    <div className="flex items-center gap-2">
      <Button
        variant="secondary"
        size="sm"
        onClick={() => updateQuantity(itemId, quantity - 1)}
        disabled={quantity <= 1}
      >
        -
      </Button>
      <span className="w-8 text-center">{quantity}</span>
      <Button
        variant="secondary"
        size="sm"
        onClick={() => updateQuantity(itemId, quantity + 1)}
      >
        +
      </Button>
    </div>
  );
}
```

### features/cart/remove-from-cart/ui/RemoveButton.tsx

```tsx
// src/features/cart/remove-from-cart/ui/RemoveButton.tsx
'use client';

import { Button } from '@/shared/ui/Button';
import { useCartStore } from '@/entities/cart';
import { Trash2 } from 'lucide-react';

interface RemoveButtonProps {
  itemId: string;
}

export function RemoveButton({ itemId }: RemoveButtonProps) {
  const removeItem = useCartStore((state) => state.removeItem);

  return (
    <Button
      variant="ghost"
      size="sm"
      onClick={() => removeItem(itemId)}
      aria-label="삭제"
    >
      <Trash2 className="h-4 w-4 text-red-500" />
    </Button>
  );
}
```

## 4. Widgets Layer

### widgets/cart-summary/ui/CartSummary.tsx

```tsx
// src/widgets/cart-summary/ui/CartSummary.tsx
'use client';

import { useCartStore } from '@/entities/cart';
import { formatPrice } from '@/shared/lib/formatPrice';
import { Button } from '@/shared/ui/Button';
import { Card } from '@/shared/ui/Card';

export function CartSummary() {
  const items = useCartStore((state) => state.items);
  const getTotalItems = useCartStore((state) => state.getTotalItems);
  const getTotalPrice = useCartStore((state) => state.getTotalPrice);

  if (items.length === 0) {
    return null;
  }

  return (
    <Card className="sticky top-4 p-6">
      <h2 className="mb-4 text-lg font-semibold">주문 요약</h2>

      <div className="space-y-2 text-sm">
        <div className="flex justify-between">
          <span>상품 수</span>
          <span>{getTotalItems()}개</span>
        </div>
        <div className="flex justify-between">
          <span>상품 금액</span>
          <span>{formatPrice(getTotalPrice())}</span>
        </div>
        <div className="flex justify-between">
          <span>배송비</span>
          <span>무료</span>
        </div>
      </div>

      <hr className="my-4" />

      <div className="flex justify-between text-lg font-semibold">
        <span>총 결제금액</span>
        <span className="text-blue-600">{formatPrice(getTotalPrice())}</span>
      </div>

      <Button className="mt-6 w-full" size="lg">
        주문하기
      </Button>
    </Card>
  );
}
```

## 5. App Layer (페이지)

### app/cart/page.tsx

```tsx
// src/app/cart/page.tsx
'use client';

import { useCartStore } from '@/entities/cart';
import { CartItem } from '@/entities/cart';
import { QuantitySelector } from '@/features/cart/update-quantity';
import { RemoveButton } from '@/features/cart/remove-from-cart';
import { CartSummary } from '@/widgets/cart-summary';

export default function CartPage() {
  const items = useCartStore((state) => state.items);

  if (items.length === 0) {
    return (
      <main className="container mx-auto py-8">
        <h1 className="mb-8 text-2xl font-bold">장바구니</h1>
        <div className="text-center py-12 text-gray-500">
          장바구니가 비어있습니다.
        </div>
      </main>
    );
  }

  return (
    <main className="container mx-auto py-8">
      <h1 className="mb-8 text-2xl font-bold">장바구니</h1>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
        {/* 장바구니 아이템 목록 */}
        <div className="space-y-4 lg:col-span-2">
          {items.map((item) => (
            <CartItem
              key={item.id}
              item={item}
              actions={
                <>
                  <QuantitySelector itemId={item.id} quantity={item.quantity} />
                  <RemoveButton itemId={item.id} />
                </>
              }
            />
          ))}
        </div>

        {/* 주문 요약 */}
        <div>
          <CartSummary />
        </div>
      </div>
    </main>
  );
}
```

## 핵심 포인트

| 레이어 | 역할 | 의존성 |
|--------|------|--------|
| **shared** | 공용 UI, 유틸 | 없음 |
| **entities** | 비즈니스 데이터/UI | shared만 |
| **features** | 사용자 액션 | shared, entities |
| **widgets** | 조합된 UI 블록 | shared, entities, features |
| **app** | 페이지 라우팅 | 모든 레이어 |

**의존성 방향**: app → widgets → features → entities → shared
