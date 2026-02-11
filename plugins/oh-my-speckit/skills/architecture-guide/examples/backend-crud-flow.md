# 실전 예시: CRUD 기능 (Clean Architecture)

## 시나리오: 상품 관리 API

상품(Product)에 대한 CRUD 기능 구현.

## 디렉토리 구조

```
src/
├── domain/
│   └── product/
│       ├── Product.ts
│       ├── ProductRepository.ts
│       └── ProductId.ts          # Value Object
├── application/
│   └── product/
│       ├── CreateProductUseCase.ts
│       ├── GetProductUseCase.ts
│       ├── UpdateProductUseCase.ts
│       ├── DeleteProductUseCase.ts
│       ├── ListProductsUseCase.ts
│       └── dto/
│           ├── CreateProductDto.ts
│           ├── UpdateProductDto.ts
│           ├── ProductResponseDto.ts
│           └── ListProductsDto.ts
├── infrastructure/
│   └── repositories/
│       └── PrismaProductRepository.ts
└── interfaces/
    └── http/
        └── controllers/
            └── ProductController.ts
```

## 1. Domain Layer

### Product.ts (엔티티)

```typescript
// src/domain/product/Product.ts
import { ProductId } from './ProductId';

export class Product {
  private constructor(
    public readonly id: ProductId,
    public readonly name: string,
    public readonly description: string,
    public readonly price: number,
    public readonly stock: number,
    public readonly createdAt: Date,
    public readonly updatedAt: Date,
  ) {}

  static create(props: {
    name: string;
    description: string;
    price: number;
    stock: number;
  }): Product {
    if (props.price < 0) {
      throw new Error('Price cannot be negative');
    }
    if (props.stock < 0) {
      throw new Error('Stock cannot be negative');
    }

    const now = new Date();
    return new Product(
      ProductId.create(),
      props.name,
      props.description,
      props.price,
      props.stock,
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
    createdAt: Date;
    updatedAt: Date;
  }): Product {
    return new Product(
      ProductId.from(props.id),
      props.name,
      props.description,
      props.price,
      props.stock,
      props.createdAt,
      props.updatedAt,
    );
  }

  update(props: Partial<{
    name: string;
    description: string;
    price: number;
    stock: number;
  }>): Product {
    if (props.price !== undefined && props.price < 0) {
      throw new Error('Price cannot be negative');
    }
    if (props.stock !== undefined && props.stock < 0) {
      throw new Error('Stock cannot be negative');
    }

    return new Product(
      this.id,
      props.name ?? this.name,
      props.description ?? this.description,
      props.price ?? this.price,
      props.stock ?? this.stock,
      this.createdAt,
      new Date(),
    );
  }

  decreaseStock(quantity: number): Product {
    if (this.stock < quantity) {
      throw new Error('Insufficient stock');
    }
    return this.update({ stock: this.stock - quantity });
  }
}
```

### ProductId.ts (Value Object)

```typescript
// src/domain/product/ProductId.ts
export class ProductId {
  private constructor(public readonly value: string) {}

  static create(): ProductId {
    return new ProductId(crypto.randomUUID());
  }

  static from(value: string): ProductId {
    if (!value || value.trim() === '') {
      throw new Error('ProductId cannot be empty');
    }
    return new ProductId(value);
  }

  equals(other: ProductId): boolean {
    return this.value === other.value;
  }

  toString(): string {
    return this.value;
  }
}
```

### ProductRepository.ts (포트)

```typescript
// src/domain/product/ProductRepository.ts
import { Product } from './Product';
import { ProductId } from './ProductId';

export interface ProductRepository {
  findById(id: ProductId): Promise<Product | null>;
  findAll(options?: {
    page?: number;
    limit?: number;
    search?: string;
  }): Promise<{ items: Product[]; total: number }>;
  save(product: Product): Promise<Product>;
  delete(id: ProductId): Promise<void>;
  existsByName(name: string, excludeId?: ProductId): Promise<boolean>;
}
```

## 2. Application Layer

### CreateProductUseCase.ts

```typescript
// src/application/product/CreateProductUseCase.ts
import { Injectable, ConflictException } from '@nestjs/common';
import { Product } from '../../domain/product/Product';
import { ProductRepository } from '../../domain/product/ProductRepository';
import { CreateProductDto } from './dto/CreateProductDto';
import { ProductResponseDto } from './dto/ProductResponseDto';

@Injectable()
export class CreateProductUseCase {
  constructor(private readonly productRepository: ProductRepository) {}

  async execute(dto: CreateProductDto): Promise<ProductResponseDto> {
    // 1. 중복 이름 체크
    const exists = await this.productRepository.existsByName(dto.name);
    if (exists) {
      throw new ConflictException('Product with this name already exists');
    }

    // 2. 엔티티 생성
    const product = Product.create({
      name: dto.name,
      description: dto.description,
      price: dto.price,
      stock: dto.stock,
    });

    // 3. 저장
    const saved = await this.productRepository.save(product);

    // 4. DTO 변환
    return ProductResponseDto.from(saved);
  }
}
```

### GetProductUseCase.ts

```typescript
// src/application/product/GetProductUseCase.ts
import { Injectable, NotFoundException } from '@nestjs/common';
import { ProductId } from '../../domain/product/ProductId';
import { ProductRepository } from '../../domain/product/ProductRepository';
import { ProductResponseDto } from './dto/ProductResponseDto';

@Injectable()
export class GetProductUseCase {
  constructor(private readonly productRepository: ProductRepository) {}

  async execute(id: string): Promise<ProductResponseDto> {
    const productId = ProductId.from(id);
    const product = await this.productRepository.findById(productId);

    if (!product) {
      throw new NotFoundException('Product not found');
    }

    return ProductResponseDto.from(product);
  }
}
```

### ListProductsUseCase.ts

```typescript
// src/application/product/ListProductsUseCase.ts
import { Injectable } from '@nestjs/common';
import { ProductRepository } from '../../domain/product/ProductRepository';
import { ListProductsDto } from './dto/ListProductsDto';
import { ProductResponseDto } from './dto/ProductResponseDto';

interface PaginatedResponse<T> {
  data: T[];
  meta: {
    page: number;
    limit: number;
    total: number;
    totalPages: number;
  };
}

@Injectable()
export class ListProductsUseCase {
  constructor(private readonly productRepository: ProductRepository) {}

  async execute(
    dto: ListProductsDto,
  ): Promise<PaginatedResponse<ProductResponseDto>> {
    const { page = 1, limit = 10, search } = dto;

    const { items, total } = await this.productRepository.findAll({
      page,
      limit,
      search,
    });

    return {
      data: items.map(ProductResponseDto.from),
      meta: {
        page,
        limit,
        total,
        totalPages: Math.ceil(total / limit),
      },
    };
  }
}
```

### UpdateProductUseCase.ts

```typescript
// src/application/product/UpdateProductUseCase.ts
import {
  Injectable,
  NotFoundException,
  ConflictException,
} from '@nestjs/common';
import { ProductId } from '../../domain/product/ProductId';
import { ProductRepository } from '../../domain/product/ProductRepository';
import { UpdateProductDto } from './dto/UpdateProductDto';
import { ProductResponseDto } from './dto/ProductResponseDto';

@Injectable()
export class UpdateProductUseCase {
  constructor(private readonly productRepository: ProductRepository) {}

  async execute(id: string, dto: UpdateProductDto): Promise<ProductResponseDto> {
    const productId = ProductId.from(id);

    // 1. 기존 상품 조회
    const product = await this.productRepository.findById(productId);
    if (!product) {
      throw new NotFoundException('Product not found');
    }

    // 2. 이름 변경 시 중복 체크
    if (dto.name && dto.name !== product.name) {
      const exists = await this.productRepository.existsByName(
        dto.name,
        productId,
      );
      if (exists) {
        throw new ConflictException('Product with this name already exists');
      }
    }

    // 3. 엔티티 업데이트
    const updated = product.update(dto);

    // 4. 저장
    const saved = await this.productRepository.save(updated);

    return ProductResponseDto.from(saved);
  }
}
```

### DeleteProductUseCase.ts

```typescript
// src/application/product/DeleteProductUseCase.ts
import { Injectable, NotFoundException } from '@nestjs/common';
import { ProductId } from '../../domain/product/ProductId';
import { ProductRepository } from '../../domain/product/ProductRepository';

@Injectable()
export class DeleteProductUseCase {
  constructor(private readonly productRepository: ProductRepository) {}

  async execute(id: string): Promise<void> {
    const productId = ProductId.from(id);

    const product = await this.productRepository.findById(productId);
    if (!product) {
      throw new NotFoundException('Product not found');
    }

    await this.productRepository.delete(productId);
  }
}
```

### DTO 정의

```typescript
// src/application/product/dto/CreateProductDto.ts
import { IsString, IsNumber, Min, MinLength } from 'class-validator';

export class CreateProductDto {
  @IsString()
  @MinLength(2)
  name: string;

  @IsString()
  description: string;

  @IsNumber()
  @Min(0)
  price: number;

  @IsNumber()
  @Min(0)
  stock: number;
}

// src/application/product/dto/UpdateProductDto.ts
import { IsString, IsNumber, Min, MinLength, IsOptional } from 'class-validator';

export class UpdateProductDto {
  @IsOptional()
  @IsString()
  @MinLength(2)
  name?: string;

  @IsOptional()
  @IsString()
  description?: string;

  @IsOptional()
  @IsNumber()
  @Min(0)
  price?: number;

  @IsOptional()
  @IsNumber()
  @Min(0)
  stock?: number;
}

// src/application/product/dto/ListProductsDto.ts
import { IsNumber, IsString, IsOptional, Min } from 'class-validator';
import { Type } from 'class-transformer';

export class ListProductsDto {
  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  page?: number;

  @IsOptional()
  @Type(() => Number)
  @IsNumber()
  @Min(1)
  limit?: number;

  @IsOptional()
  @IsString()
  search?: string;
}

// src/application/product/dto/ProductResponseDto.ts
import { Product } from '../../../domain/product/Product';

export class ProductResponseDto {
  id: string;
  name: string;
  description: string;
  price: number;
  stock: number;
  createdAt: Date;
  updatedAt: Date;

  static from(product: Product): ProductResponseDto {
    return {
      id: product.id.value,
      name: product.name,
      description: product.description,
      price: product.price,
      stock: product.stock,
      createdAt: product.createdAt,
      updatedAt: product.updatedAt,
    };
  }
}
```

## 3. Infrastructure Layer

### PrismaProductRepository.ts

```typescript
// src/infrastructure/repositories/PrismaProductRepository.ts
import { Injectable } from '@nestjs/common';
import { PrismaService } from '../database/PrismaService';
import { Product } from '../../domain/product/Product';
import { ProductId } from '../../domain/product/ProductId';
import { ProductRepository } from '../../domain/product/ProductRepository';

@Injectable()
export class PrismaProductRepository implements ProductRepository {
  constructor(private readonly prisma: PrismaService) {}

  async findById(id: ProductId): Promise<Product | null> {
    const data = await this.prisma.product.findUnique({
      where: { id: id.value },
    });
    return data ? Product.reconstitute(data) : null;
  }

  async findAll(options?: {
    page?: number;
    limit?: number;
    search?: string;
  }): Promise<{ items: Product[]; total: number }> {
    const { page = 1, limit = 10, search } = options ?? {};

    const where = search
      ? {
          OR: [
            { name: { contains: search, mode: 'insensitive' as const } },
            { description: { contains: search, mode: 'insensitive' as const } },
          ],
        }
      : {};

    const [items, total] = await Promise.all([
      this.prisma.product.findMany({
        where,
        skip: (page - 1) * limit,
        take: limit,
        orderBy: { createdAt: 'desc' },
      }),
      this.prisma.product.count({ where }),
    ]);

    return {
      items: items.map(Product.reconstitute),
      total,
    };
  }

  async save(product: Product): Promise<Product> {
    const data = await this.prisma.product.upsert({
      where: { id: product.id.value },
      update: {
        name: product.name,
        description: product.description,
        price: product.price,
        stock: product.stock,
        updatedAt: product.updatedAt,
      },
      create: {
        id: product.id.value,
        name: product.name,
        description: product.description,
        price: product.price,
        stock: product.stock,
        createdAt: product.createdAt,
        updatedAt: product.updatedAt,
      },
    });
    return Product.reconstitute(data);
  }

  async delete(id: ProductId): Promise<void> {
    await this.prisma.product.delete({
      where: { id: id.value },
    });
  }

  async existsByName(name: string, excludeId?: ProductId): Promise<boolean> {
    const count = await this.prisma.product.count({
      where: {
        name,
        ...(excludeId && { id: { not: excludeId.value } }),
      },
    });
    return count > 0;
  }
}
```

## 4. Interface Layer

### ProductController.ts

```typescript
// src/interfaces/http/controllers/ProductController.ts
import {
  Controller,
  Get,
  Post,
  Put,
  Delete,
  Body,
  Param,
  Query,
  HttpCode,
  HttpStatus,
  ParseUUIDPipe,
} from '@nestjs/common';
import { ApiTags, ApiOperation, ApiResponse, ApiQuery } from '@nestjs/swagger';
import { CreateProductUseCase } from '../../../application/product/CreateProductUseCase';
import { GetProductUseCase } from '../../../application/product/GetProductUseCase';
import { UpdateProductUseCase } from '../../../application/product/UpdateProductUseCase';
import { DeleteProductUseCase } from '../../../application/product/DeleteProductUseCase';
import { ListProductsUseCase } from '../../../application/product/ListProductsUseCase';
import { CreateProductDto } from '../../../application/product/dto/CreateProductDto';
import { UpdateProductDto } from '../../../application/product/dto/UpdateProductDto';
import { ListProductsDto } from '../../../application/product/dto/ListProductsDto';
import { ProductResponseDto } from '../../../application/product/dto/ProductResponseDto';

@ApiTags('products')
@Controller('products')
export class ProductController {
  constructor(
    private readonly createProduct: CreateProductUseCase,
    private readonly getProduct: GetProductUseCase,
    private readonly updateProduct: UpdateProductUseCase,
    private readonly deleteProduct: DeleteProductUseCase,
    private readonly listProducts: ListProductsUseCase,
  ) {}

  @Get()
  @ApiOperation({ summary: '상품 목록 조회' })
  @ApiQuery({ name: 'page', required: false })
  @ApiQuery({ name: 'limit', required: false })
  @ApiQuery({ name: 'search', required: false })
  async list(@Query() query: ListProductsDto) {
    return this.listProducts.execute(query);
  }

  @Get(':id')
  @ApiOperation({ summary: '상품 상세 조회' })
  @ApiResponse({ status: 200, type: ProductResponseDto })
  @ApiResponse({ status: 404, description: 'Product not found' })
  async findOne(
    @Param('id', ParseUUIDPipe) id: string,
  ): Promise<ProductResponseDto> {
    return this.getProduct.execute(id);
  }

  @Post()
  @ApiOperation({ summary: '상품 생성' })
  @ApiResponse({ status: 201, type: ProductResponseDto })
  @ApiResponse({ status: 409, description: 'Product name already exists' })
  async create(@Body() dto: CreateProductDto): Promise<ProductResponseDto> {
    return this.createProduct.execute(dto);
  }

  @Put(':id')
  @ApiOperation({ summary: '상품 수정' })
  @ApiResponse({ status: 200, type: ProductResponseDto })
  @ApiResponse({ status: 404, description: 'Product not found' })
  async update(
    @Param('id', ParseUUIDPipe) id: string,
    @Body() dto: UpdateProductDto,
  ): Promise<ProductResponseDto> {
    return this.updateProduct.execute(id, dto);
  }

  @Delete(':id')
  @HttpCode(HttpStatus.NO_CONTENT)
  @ApiOperation({ summary: '상품 삭제' })
  @ApiResponse({ status: 204, description: 'Product deleted' })
  @ApiResponse({ status: 404, description: 'Product not found' })
  async remove(@Param('id', ParseUUIDPipe) id: string): Promise<void> {
    return this.deleteProduct.execute(id);
  }
}
```

## 핵심 포인트

1. **UseCase 분리**: 각 CRUD 작업이 독립적인 UseCase
2. **Value Object**: ProductId로 식별자 타입 안전성 확보
3. **불변 엔티티**: update() 메서드가 새 인스턴스 반환
4. **도메인 검증**: 가격/재고 음수 검증은 엔티티에서
5. **Repository 추상화**: 인터페이스로 정의, Infrastructure에서 구현
