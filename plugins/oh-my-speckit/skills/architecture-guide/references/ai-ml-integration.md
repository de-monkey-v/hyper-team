# AI/ML 통합 아키텍처

백엔드 시스템에 AI/ML 기능을 통합하는 아키텍처.

## AI 통합 레이어

```
┌─────────────────────────────────────────────────────────────────┐
│                        Application Layer                         │
│                              │                                   │
│                    ┌─────────┴─────────┐                        │
│                    ▼                   ▼                        │
│              [AI Service]        [Business Logic]               │
│                    │                   │                        │
├────────────────────┼───────────────────┼────────────────────────┤
│                    │    AI Gateway     │                        │
│              ┌─────┴─────┐       ┌─────┴─────┐                 │
│              ▼           ▼       ▼           ▼                 │
│         [OpenAI]    [Claude]  [Local    [Vector                │
│                              Model]      DB]                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## AI Gateway 패턴

여러 AI 제공자를 추상화하여 유연한 전환 지원:

```typescript
// AI Provider 인터페이스
export interface AIProvider {
  chat(messages: Message[], options?: ChatOptions): Promise<string>;
  embedding(text: string): Promise<number[]>;
  stream(messages: Message[]): AsyncIterable<string>;
}

// AI Gateway 구현
@Injectable()
export class AIGateway {
  private providers: Map<string, AIProvider> = new Map();

  constructor(
    private readonly openai: OpenAIProvider,
    private readonly anthropic: AnthropicProvider,
    private readonly config: ConfigService,
  ) {
    this.providers.set('openai', openai);
    this.providers.set('anthropic', anthropic);
  }

  async chat(
    messages: Message[],
    options?: { provider?: string; model?: string; temperature?: number },
  ): Promise<string> {
    const providerName = options?.provider || this.config.get('AI_DEFAULT_PROVIDER');
    const provider = this.providers.get(providerName);

    if (!provider) {
      throw new Error(`AI provider '${providerName}' not found`);
    }

    return provider.chat(messages, options);
  }

  async embedding(text: string, provider?: string): Promise<number[]> {
    const p = this.providers.get(provider || 'openai');
    return p!.embedding(text);
  }

  // Fallback 지원
  async chatWithFallback(messages: Message[]): Promise<string> {
    const providers = ['anthropic', 'openai'];  // 우선순위

    for (const providerName of providers) {
      try {
        return await this.chat(messages, { provider: providerName });
      } catch (error) {
        console.warn(`${providerName} failed, trying next...`);
      }
    }

    throw new Error('All AI providers failed');
  }
}
```

### Provider 구현

```typescript
// OpenAI Provider
@Injectable()
export class OpenAIProvider implements AIProvider {
  private client: OpenAI;

  constructor(private readonly config: ConfigService) {
    this.client = new OpenAI({
      apiKey: config.get('OPENAI_API_KEY'),
    });
  }

  async chat(messages: Message[], options?: ChatOptions): Promise<string> {
    const response = await this.client.chat.completions.create({
      model: options?.model || 'gpt-4-turbo',
      messages: messages.map(m => ({
        role: m.role as 'user' | 'assistant' | 'system',
        content: m.content,
      })),
      temperature: options?.temperature ?? 0.7,
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

  async *stream(messages: Message[]): AsyncIterable<string> {
    const stream = await this.client.chat.completions.create({
      model: 'gpt-4-turbo',
      messages: messages.map(m => ({
        role: m.role as 'user' | 'assistant' | 'system',
        content: m.content,
      })),
      stream: true,
    });

    for await (const chunk of stream) {
      const content = chunk.choices[0]?.delta?.content;
      if (content) yield content;
    }
  }
}

// Anthropic Provider
@Injectable()
export class AnthropicProvider implements AIProvider {
  private client: Anthropic;

  constructor(private readonly config: ConfigService) {
    this.client = new Anthropic({
      apiKey: config.get('ANTHROPIC_API_KEY'),
    });
  }

  async chat(messages: Message[], options?: ChatOptions): Promise<string> {
    const response = await this.client.messages.create({
      model: options?.model || 'claude-3-5-sonnet-20241022',
      max_tokens: 4096,
      messages: messages.filter(m => m.role !== 'system').map(m => ({
        role: m.role as 'user' | 'assistant',
        content: m.content,
      })),
      system: messages.find(m => m.role === 'system')?.content,
    });

    return response.content[0].type === 'text' ? response.content[0].text : '';
  }
}
```

---

## RAG (Retrieval-Augmented Generation)

### RAG Pipeline 구조

```
┌────────────┐     ┌────────────┐     ┌────────────┐
│   질문     │────►│  Embedding │────►│ 유사 문서  │
│            │     │            │     │  검색      │
└────────────┘     └────────────┘     └─────┬──────┘
                                            │
                                            ▼
┌────────────┐     ┌────────────┐     ┌────────────┐
│   응답     │◄────│    LLM     │◄────│ 컨텍스트   │
│            │     │            │     │ 구성       │
└────────────┘     └────────────┘     └────────────┘
```

### RAG Service 구현

```typescript
@Injectable()
export class RAGService {
  constructor(
    private readonly vectorStore: VectorStore,
    private readonly aiGateway: AIGateway,
  ) {}

  async query(question: string): Promise<RAGResponse> {
    // 1. 질문 임베딩
    const questionEmbedding = await this.aiGateway.embedding(question);

    // 2. 유사 문서 검색
    const relevantDocs = await this.vectorStore.similaritySearch(
      questionEmbedding,
      { topK: 5, threshold: 0.7 },
    );

    // 3. 컨텍스트 구성
    const context = relevantDocs
      .map(doc => `[${doc.metadata.title}]\n${doc.content}`)
      .join('\n\n---\n\n');

    // 4. LLM 응답 생성
    const response = await this.aiGateway.chat([
      {
        role: 'system',
        content: `다음 컨텍스트를 기반으로 질문에 답변하세요.
컨텍스트에 없는 내용은 "제공된 정보에서 찾을 수 없습니다"라고 답변하세요.
답변에 사용한 출처를 명시하세요.

컨텍스트:
${context}`,
      },
      { role: 'user', content: question },
    ]);

    return {
      answer: response,
      sources: relevantDocs.map(d => ({
        title: d.metadata.title,
        snippet: d.content.substring(0, 200),
        score: d.score,
      })),
    };
  }
}
```

### 문서 인덱싱

```typescript
@Injectable()
export class DocumentIndexer {
  constructor(
    private readonly vectorStore: VectorStore,
    private readonly aiGateway: AIGateway,
  ) {}

  async indexDocument(document: Document): Promise<void> {
    // 1. 문서 청킹
    const chunks = this.chunkDocument(document.content, {
      chunkSize: 1000,
      overlap: 200,
    });

    // 2. 각 청크 임베딩 및 저장
    for (const chunk of chunks) {
      const embedding = await this.aiGateway.embedding(chunk.text);

      await this.vectorStore.upsert({
        id: `${document.id}-${chunk.index}`,
        embedding,
        metadata: {
          documentId: document.id,
          title: document.title,
          chunkIndex: chunk.index,
          source: document.source,
        },
        content: chunk.text,
      });
    }
  }

  private chunkDocument(
    content: string,
    options: { chunkSize: number; overlap: number },
  ): Array<{ text: string; index: number }> {
    const chunks: Array<{ text: string; index: number }> = [];
    const sentences = content.split(/(?<=[.!?])\s+/);

    let currentChunk = '';
    let index = 0;

    for (const sentence of sentences) {
      if ((currentChunk + sentence).length > options.chunkSize && currentChunk) {
        chunks.push({ text: currentChunk.trim(), index: index++ });

        // Overlap: 마지막 몇 문장 유지
        const words = currentChunk.split(' ');
        currentChunk = words.slice(-Math.floor(options.overlap / 10)).join(' ') + ' ';
      }
      currentChunk += sentence + ' ';
    }

    if (currentChunk.trim()) {
      chunks.push({ text: currentChunk.trim(), index });
    }

    return chunks;
  }
}
```

---

## Vector Database 통합

### Pinecone 구현

```typescript
@Injectable()
export class PineconeVectorStore implements VectorStore {
  private index: Index;

  constructor(private readonly config: ConfigService) {
    const pinecone = new Pinecone({
      apiKey: config.get('PINECONE_API_KEY'),
    });
    this.index = pinecone.index(config.get('PINECONE_INDEX'));
  }

  async upsert(doc: VectorDocument): Promise<void> {
    await this.index.upsert([{
      id: doc.id,
      values: doc.embedding,
      metadata: {
        ...doc.metadata,
        content: doc.content,
      },
    }]);
  }

  async similaritySearch(
    embedding: number[],
    options: { topK: number; threshold: number },
  ): Promise<VectorDocument[]> {
    const results = await this.index.query({
      vector: embedding,
      topK: options.topK,
      includeMetadata: true,
    });

    return results.matches
      .filter(match => (match.score || 0) >= options.threshold)
      .map(match => ({
        id: match.id,
        content: match.metadata?.content as string,
        metadata: match.metadata as Record<string, unknown>,
        score: match.score,
      }));
  }

  async delete(ids: string[]): Promise<void> {
    await this.index.deleteMany(ids);
  }
}
```

### pgvector (PostgreSQL) 구현

```typescript
@Injectable()
export class PgVectorStore implements VectorStore {
  constructor(private readonly prisma: PrismaClient) {}

  async upsert(doc: VectorDocument): Promise<void> {
    await this.prisma.$executeRaw`
      INSERT INTO documents (id, embedding, content, metadata)
      VALUES (
        ${doc.id},
        ${doc.embedding}::vector,
        ${doc.content},
        ${JSON.stringify(doc.metadata)}::jsonb
      )
      ON CONFLICT (id) DO UPDATE SET
        embedding = EXCLUDED.embedding,
        content = EXCLUDED.content,
        metadata = EXCLUDED.metadata
    `;
  }

  async similaritySearch(
    embedding: number[],
    options: { topK: number; threshold: number },
  ): Promise<VectorDocument[]> {
    const results = await this.prisma.$queryRaw<VectorDocument[]>`
      SELECT
        id,
        content,
        metadata,
        1 - (embedding <=> ${embedding}::vector) as score
      FROM documents
      WHERE 1 - (embedding <=> ${embedding}::vector) >= ${options.threshold}
      ORDER BY embedding <=> ${embedding}::vector
      LIMIT ${options.topK}
    `;

    return results;
  }
}
```

---

## AI 기능 Use Case

### 의도 분류 + 기능 라우팅

```typescript
@Injectable()
export class CustomerSupportUseCase {
  constructor(
    private readonly ragService: RAGService,
    private readonly orderService: OrderService,
    private readonly aiGateway: AIGateway,
  ) {}

  async handleQuery(
    customerId: string,
    query: string,
  ): Promise<ChatResponse> {
    // 1. 의도 분류
    const intent = await this.classifyIntent(query);

    // 2. 의도에 따른 처리
    switch (intent) {
      case 'ORDER_STATUS':
        return this.handleOrderStatus(customerId, query);
      case 'PRODUCT_QUESTION':
        return this.handleProductQuestion(query);
      case 'COMPLAINT':
        return this.handleComplaint(customerId, query);
      default:
        return this.handleGeneralQuestion(query);
    }
  }

  private async classifyIntent(query: string): Promise<string> {
    const response = await this.aiGateway.chat([
      {
        role: 'system',
        content: `사용자 질문의 의도를 분류하세요.
가능한 의도: ORDER_STATUS, PRODUCT_QUESTION, COMPLAINT, GENERAL
의도만 출력하세요.`,
      },
      { role: 'user', content: query },
    ]);

    return response.trim().toUpperCase();
  }

  private async handleOrderStatus(
    customerId: string,
    query: string,
  ): Promise<ChatResponse> {
    const orders = await this.orderService.getRecentOrders(customerId, 5);

    const response = await this.aiGateway.chat([
      {
        role: 'system',
        content: `고객의 최근 주문 정보를 바탕으로 질문에 친절하게 답변하세요.

주문 정보:
${JSON.stringify(orders, null, 2)}`,
      },
      { role: 'user', content: query },
    ]);

    return {
      message: response,
      data: { orders },
    };
  }

  private async handleProductQuestion(query: string): Promise<ChatResponse> {
    const result = await this.ragService.query(query);
    return {
      message: result.answer,
      sources: result.sources,
    };
  }
}
```

---

## AI 서비스 모니터링

```typescript
@Injectable()
export class AIMetricsService {
  constructor(
    private readonly metrics: MetricsService,
    private readonly logger: Logger,
  ) {}

  async trackAICall<T>(
    provider: string,
    model: string,
    operation: () => Promise<T>,
    options?: { inputTokens?: number },
  ): Promise<T> {
    const startTime = Date.now();
    const requestId = crypto.randomUUID();

    try {
      const result = await operation();
      const duration = Date.now() - startTime;

      // 메트릭 기록
      this.metrics.recordHistogram('ai_call_duration_ms', duration, {
        provider,
        model,
        status: 'success',
      });

      this.metrics.incrementCounter('ai_calls_total', {
        provider,
        model,
        status: 'success',
      });

      // 비용 추적 (토큰 기반)
      if (options?.inputTokens) {
        this.metrics.incrementCounter('ai_tokens_used', {
          provider,
          model,
        }, options.inputTokens);
      }

      return result;
    } catch (error) {
      const duration = Date.now() - startTime;

      this.metrics.recordHistogram('ai_call_duration_ms', duration, {
        provider,
        model,
        status: 'error',
      });

      this.metrics.incrementCounter('ai_calls_total', {
        provider,
        model,
        status: 'error',
        error_type: error.constructor.name,
      });

      this.logger.error('AI call failed', {
        requestId,
        provider,
        model,
        duration,
        error: error.message,
      });

      throw error;
    }
  }
}
```

---

## 실전 예시

상세 구현 예시: `examples/ai-integration-example.md` 참조
