# 실전 예시: AI/ML 통합 아키텍처

## 시나리오: 지능형 고객 지원 시스템

RAG 기반 고객 지원 챗봇 + 문서 검색 시스템 구현.

## 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────────┐
│                          Client Application                          │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────┐
│                            API Gateway                               │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
            ┌───────────────────┼───────────────────┐
            │                   │                   │
            ▼                   ▼                   ▼
    ┌───────────────┐  ┌───────────────┐  ┌───────────────┐
    │   Chat API    │  │  Search API   │  │  Admin API    │
    │  (RAG Pipeline)│  │(Vector Search)│  │(Doc Indexing) │
    └───────┬───────┘  └───────┬───────┘  └───────┬───────┘
            │                   │                   │
            └───────────────────┼───────────────────┘
                                │
                    ┌───────────┴───────────┐
                    │                       │
                    ▼                       ▼
            ┌───────────────┐      ┌───────────────┐
            │  AI Gateway   │      │  Vector DB    │
            │(OpenAI/Claude)│      │  (Pinecone)   │
            └───────────────┘      └───────────────┘
```

## 디렉토리 구조

```
src/
├── modules/
│   ├── chat/
│   │   ├── application/
│   │   │   ├── ChatUseCase.ts
│   │   │   └── IntentClassifier.ts
│   │   ├── domain/
│   │   │   ├── Conversation.ts
│   │   │   └── Message.ts
│   │   └── interfaces/
│   │       └── ChatController.ts
│   │
│   ├── search/
│   │   ├── application/
│   │   │   ├── SemanticSearchUseCase.ts
│   │   │   └── DocumentIndexer.ts
│   │   ├── domain/
│   │   │   ├── Document.ts
│   │   │   └── SearchResult.ts
│   │   └── interfaces/
│   │       └── SearchController.ts
│   │
│   └── knowledge/
│       ├── application/
│       │   ├── RAGPipeline.ts
│       │   └── ContextBuilder.ts
│       └── domain/
│           └── KnowledgeChunk.ts
│
├── infrastructure/
│   ├── ai/
│   │   ├── AIGateway.ts
│   │   ├── providers/
│   │   │   ├── OpenAIProvider.ts
│   │   │   └── AnthropicProvider.ts
│   │   └── embeddings/
│   │       └── EmbeddingService.ts
│   │
│   ├── vectordb/
│   │   ├── VectorStore.ts
│   │   └── PineconeVectorStore.ts
│   │
│   └── cache/
│       └── SemanticCache.ts
│
└── shared/
    ├── chunking/
    │   └── TextChunker.ts
    └── metrics/
        └── AIMetrics.ts
```

## 1. AI Gateway (다중 제공자 지원)

```typescript
// src/infrastructure/ai/AIGateway.ts
import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { OpenAIProvider } from './providers/OpenAIProvider';
import { AnthropicProvider } from './providers/AnthropicProvider';
import { AIMetrics } from '../../shared/metrics/AIMetrics';

export interface ChatMessage {
  role: 'system' | 'user' | 'assistant';
  content: string;
}

export interface ChatOptions {
  provider?: 'openai' | 'anthropic';
  model?: string;
  temperature?: number;
  maxTokens?: number;
}

export interface AIProvider {
  chat(messages: ChatMessage[], options?: ChatOptions): Promise<string>;
  embedding(text: string): Promise<number[]>;
  embeddingBatch(texts: string[]): Promise<number[][]>;
}

@Injectable()
export class AIGateway {
  private readonly logger = new Logger(AIGateway.name);
  private readonly providers: Map<string, AIProvider> = new Map();
  private readonly defaultProvider: string;

  constructor(
    private readonly config: ConfigService,
    private readonly openai: OpenAIProvider,
    private readonly anthropic: AnthropicProvider,
    private readonly metrics: AIMetrics,
  ) {
    this.providers.set('openai', openai);
    this.providers.set('anthropic', anthropic);
    this.defaultProvider = config.get('AI_DEFAULT_PROVIDER', 'openai');
  }

  async chat(
    messages: ChatMessage[],
    options: ChatOptions = {},
  ): Promise<string> {
    const providerName = options.provider || this.defaultProvider;
    const provider = this.providers.get(providerName);

    if (!provider) {
      throw new Error(`AI provider '${providerName}' not found`);
    }

    const startTime = Date.now();

    try {
      const response = await provider.chat(messages, options);

      this.metrics.recordLatency(providerName, Date.now() - startTime);
      this.metrics.recordSuccess(providerName);

      return response;
    } catch (error) {
      this.metrics.recordError(providerName, error.message);
      this.logger.error(`AI chat failed: ${error.message}`);

      // Fallback to another provider
      if (providerName !== this.defaultProvider) {
        return this.chat(messages, {
          ...options,
          provider: this.defaultProvider as any,
        });
      }

      throw error;
    }
  }

  async embedding(text: string): Promise<number[]> {
    const provider = this.providers.get('openai')!;
    return provider.embedding(text);
  }

  async embeddingBatch(texts: string[]): Promise<number[][]> {
    const provider = this.providers.get('openai')!;
    return provider.embeddingBatch(texts);
  }
}

// src/infrastructure/ai/providers/OpenAIProvider.ts
import { Injectable } from '@nestjs/common';
import OpenAI from 'openai';
import { AIProvider, ChatMessage, ChatOptions } from '../AIGateway';

@Injectable()
export class OpenAIProvider implements AIProvider {
  private client: OpenAI;

  constructor(private readonly config: ConfigService) {
    this.client = new OpenAI({
      apiKey: config.get('OPENAI_API_KEY'),
    });
  }

  async chat(messages: ChatMessage[], options: ChatOptions = {}): Promise<string> {
    const response = await this.client.chat.completions.create({
      model: options.model || 'gpt-4-turbo',
      messages: messages.map(m => ({
        role: m.role,
        content: m.content,
      })),
      temperature: options.temperature ?? 0.7,
      max_tokens: options.maxTokens ?? 1000,
    });

    return response.choices[0].message.content || '';
  }

  async embedding(text: string): Promise<number[]> {
    const response = await this.client.embeddings.create({
      model: 'text-embedding-3-small',
      input: text,
    });

    return response.data[0].embedding;
  }

  async embeddingBatch(texts: string[]): Promise<number[][]> {
    const response = await this.client.embeddings.create({
      model: 'text-embedding-3-small',
      input: texts,
    });

    return response.data.map(d => d.embedding);
  }
}

// src/infrastructure/ai/providers/AnthropicProvider.ts
import { Injectable } from '@nestjs/common';
import Anthropic from '@anthropic-ai/sdk';
import { AIProvider, ChatMessage, ChatOptions } from '../AIGateway';

@Injectable()
export class AnthropicProvider implements AIProvider {
  private client: Anthropic;

  constructor(private readonly config: ConfigService) {
    this.client = new Anthropic({
      apiKey: config.get('ANTHROPIC_API_KEY'),
    });
  }

  async chat(messages: ChatMessage[], options: ChatOptions = {}): Promise<string> {
    const systemMessage = messages.find(m => m.role === 'system');
    const otherMessages = messages.filter(m => m.role !== 'system');

    const response = await this.client.messages.create({
      model: options.model || 'claude-3-5-sonnet-20241022',
      max_tokens: options.maxTokens || 1000,
      system: systemMessage?.content,
      messages: otherMessages.map(m => ({
        role: m.role as 'user' | 'assistant',
        content: m.content,
      })),
    });

    return response.content[0].type === 'text'
      ? response.content[0].text
      : '';
  }

  async embedding(text: string): Promise<number[]> {
    // Anthropic은 embedding API가 없으므로 OpenAI 사용
    throw new Error('Anthropic does not support embeddings');
  }

  async embeddingBatch(texts: string[]): Promise<number[][]> {
    throw new Error('Anthropic does not support embeddings');
  }
}
```

## 2. Vector Store (Pinecone)

```typescript
// src/infrastructure/vectordb/VectorStore.ts
export interface VectorDocument {
  id: string;
  embedding: number[];
  content: string;
  metadata: Record<string, unknown>;
}

export interface SearchOptions {
  topK: number;
  threshold?: number;
  filter?: Record<string, unknown>;
}

export interface SearchResult {
  id: string;
  content: string;
  score: number;
  metadata: Record<string, unknown>;
}

export interface VectorStore {
  upsert(documents: VectorDocument[]): Promise<void>;
  search(embedding: number[], options: SearchOptions): Promise<SearchResult[]>;
  delete(ids: string[]): Promise<void>;
}

// src/infrastructure/vectordb/PineconeVectorStore.ts
import { Injectable } from '@nestjs/common';
import { Pinecone, Index } from '@pinecone-database/pinecone';
import { VectorStore, VectorDocument, SearchOptions, SearchResult } from './VectorStore';

@Injectable()
export class PineconeVectorStore implements VectorStore {
  private index: Index;

  constructor(private readonly config: ConfigService) {
    const pinecone = new Pinecone({
      apiKey: config.get('PINECONE_API_KEY'),
    });
    this.index = pinecone.index(config.get('PINECONE_INDEX'));
  }

  async upsert(documents: VectorDocument[]): Promise<void> {
    const vectors = documents.map(doc => ({
      id: doc.id,
      values: doc.embedding,
      metadata: {
        ...doc.metadata,
        content: doc.content,
      },
    }));

    // Pinecone는 1000개씩 배치 처리
    const batchSize = 100;
    for (let i = 0; i < vectors.length; i += batchSize) {
      const batch = vectors.slice(i, i + batchSize);
      await this.index.upsert(batch);
    }
  }

  async search(
    embedding: number[],
    options: SearchOptions,
  ): Promise<SearchResult[]> {
    const results = await this.index.query({
      vector: embedding,
      topK: options.topK,
      includeMetadata: true,
      filter: options.filter,
    });

    return results.matches
      .filter(match => !options.threshold || (match.score || 0) >= options.threshold)
      .map(match => ({
        id: match.id,
        content: match.metadata?.content as string,
        score: match.score || 0,
        metadata: match.metadata || {},
      }));
  }

  async delete(ids: string[]): Promise<void> {
    await this.index.deleteMany(ids);
  }
}
```

## 3. RAG Pipeline

```typescript
// src/modules/knowledge/application/RAGPipeline.ts
import { Injectable, Logger } from '@nestjs/common';
import { AIGateway } from '../../../infrastructure/ai/AIGateway';
import { VectorStore } from '../../../infrastructure/vectordb/VectorStore';
import { SemanticCache } from '../../../infrastructure/cache/SemanticCache';

export interface RAGOptions {
  topK?: number;
  threshold?: number;
  includeMetadata?: boolean;
}

export interface RAGResult {
  answer: string;
  sources: Array<{
    content: string;
    score: number;
    metadata: Record<string, unknown>;
  }>;
  cached: boolean;
}

@Injectable()
export class RAGPipeline {
  private readonly logger = new Logger(RAGPipeline.name);

  constructor(
    private readonly aiGateway: AIGateway,
    private readonly vectorStore: VectorStore,
    private readonly semanticCache: SemanticCache,
  ) {}

  async query(question: string, options: RAGOptions = {}): Promise<RAGResult> {
    const { topK = 5, threshold = 0.7 } = options;

    // 1. 캐시 확인
    const cachedResult = await this.semanticCache.get(question);
    if (cachedResult) {
      this.logger.debug(`Cache hit for question: ${question.slice(0, 50)}...`);
      return { ...cachedResult, cached: true };
    }

    // 2. 질문 임베딩
    const questionEmbedding = await this.aiGateway.embedding(question);

    // 3. 유사 문서 검색
    const searchResults = await this.vectorStore.search(questionEmbedding, {
      topK,
      threshold,
    });

    if (searchResults.length === 0) {
      return {
        answer: '죄송합니다. 해당 질문에 대한 관련 정보를 찾을 수 없습니다.',
        sources: [],
        cached: false,
      };
    }

    // 4. 컨텍스트 구성
    const context = searchResults
      .map((result, index) => `[${index + 1}] ${result.content}`)
      .join('\n\n---\n\n');

    // 5. LLM 응답 생성
    const answer = await this.aiGateway.chat([
      {
        role: 'system',
        content: `당신은 도움이 되는 고객 지원 상담사입니다.
제공된 컨텍스트를 기반으로 사용자의 질문에 정확하고 친절하게 답변하세요.

규칙:
1. 컨텍스트에 있는 정보만 사용하세요
2. 컨텍스트에 없는 내용은 "해당 정보를 찾을 수 없습니다"라고 답변하세요
3. 답변에 사용한 출처 번호를 [1], [2] 형식으로 표시하세요
4. 한국어로 답변하세요

컨텍스트:
${context}`,
      },
      { role: 'user', content: question },
    ]);

    const result: RAGResult = {
      answer,
      sources: searchResults.map(r => ({
        content: r.content,
        score: r.score,
        metadata: r.metadata,
      })),
      cached: false,
    };

    // 6. 캐시 저장
    await this.semanticCache.set(question, result);

    return result;
  }
}
```

## 4. 문서 인덱싱

```typescript
// src/modules/search/application/DocumentIndexer.ts
import { Injectable, Logger } from '@nestjs/common';
import { AIGateway } from '../../../infrastructure/ai/AIGateway';
import { VectorStore } from '../../../infrastructure/vectordb/VectorStore';
import { TextChunker, ChunkOptions } from '../../../shared/chunking/TextChunker';

export interface IndexDocumentInput {
  id: string;
  title: string;
  content: string;
  category?: string;
  tags?: string[];
}

@Injectable()
export class DocumentIndexer {
  private readonly logger = new Logger(DocumentIndexer.name);

  constructor(
    private readonly aiGateway: AIGateway,
    private readonly vectorStore: VectorStore,
    private readonly textChunker: TextChunker,
  ) {}

  async indexDocument(input: IndexDocumentInput): Promise<void> {
    this.logger.log(`Indexing document: ${input.id}`);

    // 1. 문서 청킹
    const chunks = this.textChunker.chunk(input.content, {
      chunkSize: 1000,
      overlap: 200,
      separator: '\n\n',
    });

    this.logger.debug(`Document split into ${chunks.length} chunks`);

    // 2. 청크별 임베딩 생성 (배치)
    const embeddings = await this.aiGateway.embeddingBatch(
      chunks.map(c => c.text),
    );

    // 3. Vector Store에 저장
    const documents = chunks.map((chunk, index) => ({
      id: `${input.id}-chunk-${index}`,
      embedding: embeddings[index],
      content: chunk.text,
      metadata: {
        documentId: input.id,
        title: input.title,
        category: input.category,
        tags: input.tags,
        chunkIndex: index,
        totalChunks: chunks.length,
        startOffset: chunk.startOffset,
        endOffset: chunk.endOffset,
      },
    }));

    await this.vectorStore.upsert(documents);

    this.logger.log(`Indexed ${documents.length} chunks for document ${input.id}`);
  }

  async deleteDocument(documentId: string): Promise<void> {
    // 문서의 모든 청크 삭제
    // Pinecone filter로 조회 후 삭제 또는 prefix 기반 삭제
    const searchResults = await this.vectorStore.search(
      new Array(1536).fill(0),  // dummy embedding
      {
        topK: 1000,
        filter: { documentId },
      },
    );

    const ids = searchResults.map(r => r.id);
    if (ids.length > 0) {
      await this.vectorStore.delete(ids);
    }
  }

  async reindexAll(documents: IndexDocumentInput[]): Promise<void> {
    this.logger.log(`Reindexing ${documents.length} documents`);

    for (const doc of documents) {
      await this.indexDocument(doc);
    }
  }
}

// src/shared/chunking/TextChunker.ts
export interface ChunkOptions {
  chunkSize: number;
  overlap: number;
  separator?: string;
}

export interface TextChunk {
  text: string;
  startOffset: number;
  endOffset: number;
}

@Injectable()
export class TextChunker {
  chunk(text: string, options: ChunkOptions): TextChunk[] {
    const { chunkSize, overlap, separator = '\n\n' } = options;
    const chunks: TextChunk[] = [];

    // 단락 단위로 먼저 분리
    const paragraphs = text.split(separator);
    let currentChunk = '';
    let currentOffset = 0;
    let chunkStartOffset = 0;

    for (const paragraph of paragraphs) {
      if (currentChunk.length + paragraph.length > chunkSize && currentChunk.length > 0) {
        // 현재 청크 저장
        chunks.push({
          text: currentChunk.trim(),
          startOffset: chunkStartOffset,
          endOffset: currentOffset,
        });

        // 오버랩 적용
        const overlapText = currentChunk.slice(-overlap);
        currentChunk = overlapText + separator + paragraph;
        chunkStartOffset = currentOffset - overlapText.length;
      } else {
        currentChunk += (currentChunk ? separator : '') + paragraph;
      }

      currentOffset += paragraph.length + separator.length;
    }

    // 마지막 청크
    if (currentChunk.trim()) {
      chunks.push({
        text: currentChunk.trim(),
        startOffset: chunkStartOffset,
        endOffset: currentOffset,
      });
    }

    return chunks;
  }
}
```

## 5. 의도 분류 및 채팅

```typescript
// src/modules/chat/application/IntentClassifier.ts
import { Injectable } from '@nestjs/common';
import { AIGateway } from '../../../infrastructure/ai/AIGateway';

export enum Intent {
  ORDER_STATUS = 'ORDER_STATUS',
  PRODUCT_QUESTION = 'PRODUCT_QUESTION',
  RETURN_REQUEST = 'RETURN_REQUEST',
  GENERAL_INQUIRY = 'GENERAL_INQUIRY',
  COMPLAINT = 'COMPLAINT',
}

@Injectable()
export class IntentClassifier {
  constructor(private readonly aiGateway: AIGateway) {}

  async classify(message: string): Promise<Intent> {
    const response = await this.aiGateway.chat(
      [
        {
          role: 'system',
          content: `사용자 메시지의 의도를 분류하세요.

가능한 의도:
- ORDER_STATUS: 주문 상태, 배송 조회
- PRODUCT_QUESTION: 상품 정보, 재고, 가격 문의
- RETURN_REQUEST: 반품, 환불 요청
- COMPLAINT: 불만, 클레임
- GENERAL_INQUIRY: 기타 일반 문의

의도만 출력하세요. 다른 텍스트 없이 의도 이름만 출력합니다.`,
        },
        { role: 'user', content: message },
      ],
      { temperature: 0, maxTokens: 20 },
    );

    const intentName = response.trim().toUpperCase();
    return (Intent[intentName as keyof typeof Intent] || Intent.GENERAL_INQUIRY);
  }
}

// src/modules/chat/application/ChatUseCase.ts
import { Injectable, Logger } from '@nestjs/common';
import { IntentClassifier, Intent } from './IntentClassifier';
import { RAGPipeline } from '../../knowledge/application/RAGPipeline';
import { OrderService } from '../../order/OrderService';
import { AIGateway } from '../../../infrastructure/ai/AIGateway';

export interface ChatInput {
  sessionId: string;
  userId?: string;
  message: string;
}

export interface ChatOutput {
  response: string;
  intent: Intent;
  sources?: Array<{ title: string; score: number }>;
  suggestedActions?: string[];
}

@Injectable()
export class ChatUseCase {
  private readonly logger = new Logger(ChatUseCase.name);

  constructor(
    private readonly intentClassifier: IntentClassifier,
    private readonly ragPipeline: RAGPipeline,
    private readonly orderService: OrderService,
    private readonly aiGateway: AIGateway,
  ) {}

  async execute(input: ChatInput): Promise<ChatOutput> {
    // 1. 의도 분류
    const intent = await this.intentClassifier.classify(input.message);
    this.logger.debug(`Classified intent: ${intent}`);

    // 2. 의도별 처리
    switch (intent) {
      case Intent.ORDER_STATUS:
        return this.handleOrderStatus(input);

      case Intent.PRODUCT_QUESTION:
        return this.handleProductQuestion(input);

      case Intent.RETURN_REQUEST:
        return this.handleReturnRequest(input);

      case Intent.COMPLAINT:
        return this.handleComplaint(input);

      default:
        return this.handleGeneralInquiry(input);
    }
  }

  private async handleOrderStatus(input: ChatInput): Promise<ChatOutput> {
    if (!input.userId) {
      return {
        response: '주문 조회를 위해 로그인이 필요합니다.',
        intent: Intent.ORDER_STATUS,
        suggestedActions: ['로그인하기', '주문번호로 조회하기'],
      };
    }

    // 최근 주문 조회
    const orders = await this.orderService.getRecentOrders(input.userId, 3);

    if (orders.length === 0) {
      return {
        response: '최근 주문 내역이 없습니다.',
        intent: Intent.ORDER_STATUS,
      };
    }

    // LLM으로 자연스러운 응답 생성
    const response = await this.aiGateway.chat([
      {
        role: 'system',
        content: `고객의 주문 상태를 친절하게 안내하세요.

주문 정보:
${JSON.stringify(orders, null, 2)}

규칙:
1. 가장 최근 주문부터 안내
2. 배송 상태를 명확히 전달
3. 예상 도착일이 있으면 함께 안내`,
      },
      { role: 'user', content: input.message },
    ]);

    return {
      response,
      intent: Intent.ORDER_STATUS,
    };
  }

  private async handleProductQuestion(input: ChatInput): Promise<ChatOutput> {
    // RAG로 상품 정보 검색
    const ragResult = await this.ragPipeline.query(input.message, {
      topK: 5,
      threshold: 0.75,
    });

    return {
      response: ragResult.answer,
      intent: Intent.PRODUCT_QUESTION,
      sources: ragResult.sources.map(s => ({
        title: s.metadata.title as string,
        score: s.score,
      })),
    };
  }

  private async handleReturnRequest(input: ChatInput): Promise<ChatOutput> {
    // 반품 정책 RAG 검색 + 안내
    const ragResult = await this.ragPipeline.query(
      '반품 및 환불 정책 ' + input.message,
    );

    return {
      response: ragResult.answer + '\n\n반품을 진행하시겠습니까?',
      intent: Intent.RETURN_REQUEST,
      sources: ragResult.sources.map(s => ({
        title: s.metadata.title as string,
        score: s.score,
      })),
      suggestedActions: ['반품 신청하기', '상담원 연결'],
    };
  }

  private async handleComplaint(input: ChatInput): Promise<ChatOutput> {
    return {
      response: '불편을 드려 죄송합니다. 자세한 상담을 위해 상담원을 연결해 드릴까요?',
      intent: Intent.COMPLAINT,
      suggestedActions: ['상담원 연결', '이메일로 접수'],
    };
  }

  private async handleGeneralInquiry(input: ChatInput): Promise<ChatOutput> {
    const ragResult = await this.ragPipeline.query(input.message);

    return {
      response: ragResult.answer,
      intent: Intent.GENERAL_INQUIRY,
      sources: ragResult.sources.map(s => ({
        title: s.metadata.title as string,
        score: s.score,
      })),
    };
  }
}
```

## 6. Semantic Cache

```typescript
// src/infrastructure/cache/SemanticCache.ts
import { Injectable } from '@nestjs/common';
import { AIGateway } from '../ai/AIGateway';
import { VectorStore } from '../vectordb/VectorStore';

interface CachedResult {
  answer: string;
  sources: any[];
}

@Injectable()
export class SemanticCache {
  private readonly SIMILARITY_THRESHOLD = 0.95;
  private readonly CACHE_NAMESPACE = 'cache';

  constructor(
    private readonly aiGateway: AIGateway,
    private readonly vectorStore: VectorStore,
  ) {}

  async get(question: string): Promise<CachedResult | null> {
    const embedding = await this.aiGateway.embedding(question);

    const results = await this.vectorStore.search(embedding, {
      topK: 1,
      threshold: this.SIMILARITY_THRESHOLD,
      filter: { namespace: this.CACHE_NAMESPACE },
    });

    if (results.length === 0) {
      return null;
    }

    const cached = results[0];
    return JSON.parse(cached.content);
  }

  async set(question: string, result: CachedResult): Promise<void> {
    const embedding = await this.aiGateway.embedding(question);

    await this.vectorStore.upsert([
      {
        id: `cache-${crypto.randomUUID()}`,
        embedding,
        content: JSON.stringify(result),
        metadata: {
          namespace: this.CACHE_NAMESPACE,
          question,
          createdAt: new Date().toISOString(),
        },
      },
    ]);
  }
}
```

## 핵심 포인트

| 패턴 | 목적 |
|------|------|
| **AI Gateway** | 다중 AI 제공자 추상화, fallback 지원 |
| **RAG Pipeline** | 문서 기반 정확한 응답 생성 |
| **Intent Classification** | 사용자 의도 파악 후 적절한 처리 |
| **Semantic Cache** | 유사 질문에 대한 캐시로 비용/지연 절감 |
| **Document Chunking** | 긴 문서를 검색 가능한 단위로 분할 |
| **Batch Embedding** | 대량 문서 인덱싱 시 API 호출 최적화 |
