# 실전 예시: Vertical Slice Architecture

## 시나리오: 상품 관리 API

상품 관리 기능을 Vertical Slice Architecture로 구현.

## 디렉토리 구조

```
src/
├── features/
│   └── products/
│       ├── create-product/
│       │   ├── CreateProductCommand.ts
│       │   ├── CreateProductHandler.ts
│       │   ├── CreateProductValidator.ts
│       │   ├── CreateProductEndpoint.ts
│       │   └── index.ts
│       │
│       ├── get-product/
│       │   ├── GetProductQuery.ts
│       │   ├── GetProductHandler.ts
│       │   ├── GetProductEndpoint.ts
│       │   └── index.ts
│       │
│       ├── list-products/
│       │   ├── ListProductsQuery.ts
│       │   ├── ListProductsHandler.ts
│       │   ├── ListProductsEndpoint.ts
│       │   └── index.ts
│       │
│       ├── update-product/
│       │   ├── UpdateProductCommand.ts
│       │   ├── UpdateProductHandler.ts
│       │   ├── UpdateProductValidator.ts
│       │   ├── UpdateProductEndpoint.ts
│       │   └── index.ts
│       │
│       ├── delete-product/
│       │   ├── DeleteProductCommand.ts
│       │   ├── DeleteProductHandler.ts
│       │   ├── DeleteProductEndpoint.ts
│       │   └── index.ts
│       │
│       └── shared/
│           ├── Product.ts           # 엔티티
│           ├── ProductRepository.ts # 인터페이스
│           └── ProductDto.ts        # 공유 DTO
│
├── infrastructure/
│   ├── database/
│   │   └── PrismaService.ts
│   └── repositories/
│       └── PrismaProductRepository.ts
│
└── shared/
    ├── mediator/
    │   └── Mediator.ts
    └── validation/
        └── ValidationPipe.ts
```

## 1. Create Product 슬라이스

### CreateProductCommand.ts

```typescript
// src/features/products/create-product/CreateProductCommand.ts
export class CreateProductCommand {
  constructor(
    public readonly name: string,
    public readonly description: string,
    public readonly price: number,
    public readonly stock: number,
    public readonly categoryId: string,
  ) {}
}
```

### CreateProductValidator.ts

```typescript
// src/features/products/create-product/CreateProductValidator.ts
import { z } from 'zod';
import { CreateProductCommand } from './CreateProductCommand';

export const createProductSchema = z.object({
  name: z.string().min(2).max(100),
  description: z.string().max(1000),
  price: z.number().positive(),
  stock: z.number().int().nonnegative(),
  categoryId: z.string().uuid(),
});

export type CreateProductInput = z.infer<typeof createProductSchema>;

export function validateCreateProduct(input: unknown): CreateProductCommand {
  const data = createProductSchema.parse(input);
  return new CreateProductCommand(
    data.name,
    data.description,
    data.price,
    data.stock,
    data.categoryId,
  );
}
```

### CreateProductHandler.ts

```typescript
// src/features/products/create-product/CreateProductHandler.ts
import { Injectable, ConflictException } from '@nestjs/common';
import { CreateProductCommand } from './CreateProductCommand';
import { Product } from '../shared/Product';
import { ProductRepository } from '../shared/ProductRepository';
import { ProductDto } from '../shared/ProductDto';

@Injectable()
export class CreateProductHandler {
  constructor(private readonly productRepository: ProductRepository) {}

  async execute(command: CreateProductCommand): Promise<ProductDto> {
    // 1. 비즈니스 규칙 검증
    const exists = await this.productRepository.existsByName(command.name);
    if (exists) {
      throw new ConflictException('Product with this name already exists');
    }

    // 2. 엔티티 생성
    const product = Product.create({
      name: command.name,
      description: command.description,
      price: command.price,
      stock: command.stock,
      categoryId: command.categoryId,
    });

    // 3. 저장
    const saved = await this.productRepository.save(product);

    // 4. DTO 반환
    return ProductDto.from(saved);
  }
}
```

### CreateProductEndpoint.ts

```typescript
// src/features/products/create-product/CreateProductEndpoint.ts
import { Controller, Post, Body, HttpCode, HttpStatus } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { CreateProductHandler } from './CreateProductHandler';
import { validateCreateProduct, CreateProductInput } from './CreateProductValidator';
import { ProductDto } from '../shared/ProductDto';

@ApiTags('products')
@Controller('products')
export class CreateProductEndpoint {
  constructor(private readonly handler: CreateProductHandler) {}

  @Post()
  @HttpCode(HttpStatus.CREATED)
  @ApiOperation({ summary: '상품 생성' })
  @ApiResponse({ status: 201, type: ProductDto })
  @ApiResponse({ status: 400, description: 'Validation failed' })
  @ApiResponse({ status: 409, description: 'Product name already exists' })
  async handle(@Body() body: CreateProductInput): Promise<ProductDto> {
    const command = validateCreateProduct(body);
    return this.handler.execute(command);
  }
}
```

## 2. Get Product 슬라이스

### GetProductQuery.ts

```typescript
// src/features/products/get-product/GetProductQuery.ts
export class GetProductQuery {
  constructor(public readonly id: string) {}
}
```

### GetProductHandler.ts

```typescript
// src/features/products/get-product/GetProductHandler.ts
import { Injectable, NotFoundException } from '@nestjs/common';
import { GetProductQuery } from './GetProductQuery';
import { ProductRepository } from '../shared/ProductRepository';
import { ProductDto } from '../shared/ProductDto';

@Injectable()
export class GetProductHandler {
  constructor(private readonly productRepository: ProductRepository) {}

  async execute(query: GetProductQuery): Promise<ProductDto> {
    const product = await this.productRepository.findById(query.id);

    if (!product) {
      throw new NotFoundException('Product not found');
    }

    return ProductDto.from(product);
  }
}
```

### GetProductEndpoint.ts

```typescript
// src/features/products/get-product/GetProductEndpoint.ts
import { Controller, Get, Param, ParseUUIDPipe } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse } from '@nestjs/swagger';
import { GetProductHandler } from './GetProductHandler';
import { GetProductQuery } from './GetProductQuery';
import { ProductDto } from '../shared/ProductDto';

@ApiTags('products')
@Controller('products')
export class GetProductEndpoint {
  constructor(private readonly handler: GetProductHandler) {}

  @Get(':id')
  @ApiOperation({ summary: '상품 상세 조회' })
  @ApiResponse({ status: 200, type: ProductDto })
  @ApiResponse({ status: 404, description: 'Product not found' })
  async handle(@Param('id', ParseUUIDPipe) id: string): Promise<ProductDto> {
    const query = new GetProductQuery(id);
    return this.handler.execute(query);
  }
}
```

## 3. List Products 슬라이스

### ListProductsQuery.ts

```typescript
// src/features/products/list-products/ListProductsQuery.ts
export class ListProductsQuery {
  constructor(
    public readonly page: number = 1,
    public readonly limit: number = 10,
    public readonly search?: string,
    public readonly categoryId?: string,
    public readonly minPrice?: number,
    public readonly maxPrice?: number,
    public readonly sortBy: 'name' | 'price' | 'createdAt' = 'createdAt',
    public readonly sortOrder: 'asc' | 'desc' = 'desc',
  ) {}
}
```

### ListProductsHandler.ts

```typescript
// src/features/products/list-products/ListProductsHandler.ts
import { Injectable } from '@nestjs/common';
import { ListProductsQuery } from './ListProductsQuery';
import { ProductRepository } from '../shared/ProductRepository';
import { ProductDto } from '../shared/ProductDto';

export interface PaginatedResult<T> {
  data: T[];
  meta: {
    page: number;
    limit: number;
    total: number;
    totalPages: number;
  };
}

@Injectable()
export class ListProductsHandler {
  constructor(private readonly productRepository: ProductRepository) {}

  async execute(query: ListProductsQuery): Promise<PaginatedResult<ProductDto>> {
    const { items, total } = await this.productRepository.findAll({
      page: query.page,
      limit: query.limit,
      search: query.search,
      categoryId: query.categoryId,
      minPrice: query.minPrice,
      maxPrice: query.maxPrice,
      sortBy: query.sortBy,
      sortOrder: query.sortOrder,
    });

    return {
      data: items.map(ProductDto.from),
      meta: {
        page: query.page,
        limit: query.limit,
        total,
        totalPages: Math.ceil(total / query.limit),
      },
    };
  }
}
```

### ListProductsEndpoint.ts

```typescript
// src/features/products/list-products/ListProductsEndpoint.ts
import { Controller, Get, Query } from '@nestjs/common';
import { ApiTags, ApiOperation, ApiQuery } from '@nestjs/swagger';
import { ListProductsHandler, PaginatedResult } from './ListProductsHandler';
import { ListProductsQuery } from './ListProductsQuery';
import { ProductDto } from '../shared/ProductDto';

class ListProductsQueryDto {
  page?: number;
  limit?: number;
  search?: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  sortBy?: 'name' | 'price' | 'createdAt';
  sortOrder?: 'asc' | 'desc';
}

@ApiTags('products')
@Controller('products')
export class ListProductsEndpoint {
  constructor(private readonly handler: ListProductsHandler) {}

  @Get()
  @ApiOperation({ summary: '상품 목록 조회' })
  @ApiQuery({ name: 'page', required: false, type: Number })
  @ApiQuery({ name: 'limit', required: false, type: Number })
  @ApiQuery({ name: 'search', required: false, type: String })
  @ApiQuery({ name: 'categoryId', required: false, type: String })
  @ApiQuery({ name: 'minPrice', required: false, type: Number })
  @ApiQuery({ name: 'maxPrice', required: false, type: Number })
  @ApiQuery({ name: 'sortBy', required: false, enum: ['name', 'price', 'createdAt'] })
  @ApiQuery({ name: 'sortOrder', required: false, enum: ['asc', 'desc'] })
  async handle(
    @Query() queryDto: ListProductsQueryDto,
  ): Promise<PaginatedResult<ProductDto>> {
    const query = new ListProductsQuery(
      queryDto.page,
      queryDto.limit,
      queryDto.search,
      queryDto.categoryId,
      queryDto.minPrice,
      queryDto.maxPrice,
      queryDto.sortBy,
      queryDto.sortOrder,
    );
    return this.handler.execute(query);
  }
}
```

## 4. 공유 코드 (Shared)

### Product.ts (엔티티)

```typescript
// src/features/products/shared/Product.ts
export class Product {
  private constructor(
    public readonly id: string,
    public readonly name: string,
    public readonly description: string,
    public readonly price: number,
    public readonly stock: number,
    public readonly categoryId: string,
    public readonly createdAt: Date,
    public readonly updatedAt: Date,
  ) {}

  static create(props: {
    name: string;
    description: string;
    price: number;
    stock: number;
    categoryId: string;
  }): Product {
    const now = new Date();
    return new Product(
      crypto.randomUUID(),
      props.name,
      props.description,
      props.price,
      props.stock,
      props.categoryId,
      now,
      now,
    );
  }

  static reconstitute(props: {
    id: string;
    name: string;
    description: string;
    price: number;
    stock: number;
    categoryId: string;
    createdAt: Date;
    updatedAt: Date;
  }): Product {
    return new Product(
      props.id,
      props.name,
      props.description,
      props.price,
      props.stock,
      props.categoryId,
      props.createdAt,
      props.updatedAt,
    );
  }

  update(props: Partial<{
    name: string;
    description: string;
    price: number;
    stock: number;
    categoryId: string;
  }>): Product {
    return new Product(
      this.id,
      props.name ?? this.name,
      props.description ?? this.description,
      props.price ?? this.price,
      props.stock ?? this.stock,
      props.categoryId ?? this.categoryId,
      this.createdAt,
      new Date(),
    );
  }
}
```

### ProductRepository.ts (인터페이스)

```typescript
// src/features/products/shared/ProductRepository.ts
import { Product } from './Product';

export interface FindAllOptions {
  page: number;
  limit: number;
  search?: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  sortBy: string;
  sortOrder: 'asc' | 'desc';
}

export interface ProductRepository {
  findById(id: string): Promise<Product | null>;
  findAll(options: FindAllOptions): Promise<{ items: Product[]; total: number }>;
  save(product: Product): Promise<Product>;
  delete(id: string): Promise<void>;
  existsByName(name: string, excludeId?: string): Promise<boolean>;
}

export const ProductRepository = Symbol('ProductRepository');
```

### ProductDto.ts

```typescript
// src/features/products/shared/ProductDto.ts
import { ApiProperty } from '@nestjs/swagger';
import { Product } from './Product';

export class ProductDto {
  @ApiProperty()
  id: string;

  @ApiProperty()
  name: string;

  @ApiProperty()
  description: string;

  @ApiProperty()
  price: number;

  @ApiProperty()
  stock: number;

  @ApiProperty()
  categoryId: string;

  @ApiProperty()
  createdAt: Date;

  @ApiProperty()
  updatedAt: Date;

  static from(product: Product): ProductDto {
    return {
      id: product.id,
      name: product.name,
      description: product.description,
      price: product.price,
      stock: product.stock,
      categoryId: product.categoryId,
      createdAt: product.createdAt,
      updatedAt: product.updatedAt,
    };
  }
}
```

## 5. 모듈 구성

```typescript
// src/features/products/products.module.ts
import { Module } from '@nestjs/common';

// Create Product
import { CreateProductEndpoint } from './create-product/CreateProductEndpoint';
import { CreateProductHandler } from './create-product/CreateProductHandler';

// Get Product
import { GetProductEndpoint } from './get-product/GetProductEndpoint';
import { GetProductHandler } from './get-product/GetProductHandler';

// List Products
import { ListProductsEndpoint } from './list-products/ListProductsEndpoint';
import { ListProductsHandler } from './list-products/ListProductsHandler';

// Update Product
import { UpdateProductEndpoint } from './update-product/UpdateProductEndpoint';
import { UpdateProductHandler } from './update-product/UpdateProductHandler';

// Delete Product
import { DeleteProductEndpoint } from './delete-product/DeleteProductEndpoint';
import { DeleteProductHandler } from './delete-product/DeleteProductHandler';

// Infrastructure
import { ProductRepository } from './shared/ProductRepository';
import { PrismaProductRepository } from '../../infrastructure/repositories/PrismaProductRepository';
import { DatabaseModule } from '../../infrastructure/database/database.module';

@Module({
  imports: [DatabaseModule],
  controllers: [
    CreateProductEndpoint,
    GetProductEndpoint,
    ListProductsEndpoint,
    UpdateProductEndpoint,
    DeleteProductEndpoint,
  ],
  providers: [
    // Handlers
    CreateProductHandler,
    GetProductHandler,
    ListProductsHandler,
    UpdateProductHandler,
    DeleteProductHandler,

    // Repository
    {
      provide: ProductRepository,
      useClass: PrismaProductRepository,
    },
  ],
})
export class ProductsModule {}
```

## 6. MediatR 패턴 (선택적)

더 큰 규모에서는 MediatR 패턴으로 핸들러를 자동 연결:

```typescript
// src/shared/mediator/Mediator.ts
import { Injectable, Type } from '@nestjs/common';
import { ModuleRef } from '@nestjs/core';

export interface IRequest<TResponse> {
  __responseType?: TResponse;
}

export interface IRequestHandler<TRequest, TResponse> {
  execute(request: TRequest): Promise<TResponse>;
}

@Injectable()
export class Mediator {
  private handlers = new Map<Type<IRequest<any>>, Type<IRequestHandler<any, any>>>();

  constructor(private moduleRef: ModuleRef) {}

  register<TRequest extends IRequest<TResponse>, TResponse>(
    requestType: Type<TRequest>,
    handlerType: Type<IRequestHandler<TRequest, TResponse>>,
  ): void {
    this.handlers.set(requestType, handlerType);
  }

  async send<TResponse>(request: IRequest<TResponse>): Promise<TResponse> {
    const handlerType = this.handlers.get(request.constructor as Type<IRequest<TResponse>>);

    if (!handlerType) {
      throw new Error(`No handler registered for ${request.constructor.name}`);
    }

    const handler = this.moduleRef.get(handlerType, { strict: false });
    return handler.execute(request);
  }
}

// 사용 예시
@Controller('products')
export class ProductsController {
  constructor(private readonly mediator: Mediator) {}

  @Post()
  async create(@Body() body: CreateProductInput) {
    const command = new CreateProductCommand(body);
    return this.mediator.send(command);
  }

  @Get(':id')
  async get(@Param('id') id: string) {
    const query = new GetProductQuery(id);
    return this.mediator.send(query);
  }
}
```

## 핵심 포인트

| 원칙 | 설명 |
|------|------|
| **기능 단위 구성** | 각 슬라이스가 Command/Query + Handler + Endpoint 포함 |
| **독립적 변경** | 한 슬라이스 변경이 다른 슬라이스에 영향 없음 |
| **공유 코드 최소화** | 엔티티, Repository 인터페이스만 공유 |
| **CQRS 자연 적용** | Command(쓰기)와 Query(읽기) 슬라이스 분리 |
| **테스트 용이성** | 각 슬라이스 독립적으로 테스트 가능 |
