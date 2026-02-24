---
name: role-templates
description: 팀메이트 역할 정의 가이드. Agent Teams에서 커맨드 리더가 팀을 구성할 때 참조합니다. 역할별 에이전트 매핑, 스폰 패턴, 자율성 모델 정보를 제공합니다.
version: 3.0.0
---

# Role Templates

Agent Teams의 팀메이트 역할 정의. 커맨드(리더)가 TeamCreate 후 팀메이트를 생성할 때 이 템플릿을 참조합니다.
- **Claude 네이티브 모드**: Task tool로 직접 스폰 (`subagent_type` 지정)
- **GPT 모드**: spawn-teammate Skill로 스폰

## 역할-에이전트 매핑 테이블

모든 역할은 `claude-team` 또는 `ai-cli-tools` 플러그인의 에이전트 타입에 매핑됩니다:

| 역할 | 에이전트 타입 | 특성 | 도구 |
|------|-------------|------|------|
| pm | `claude-team:planner` | 제품 기획/요구사항 분석 (읽기 전용 + 웹) | Read,Glob,Grep,Bash,WebSearch,WebFetch,SendMessage |
| architect | `claude-team:architect` | 아키텍처 분석/설계 (읽기 전용) | Read,Glob,Grep,Bash,SendMessage |
| developer | `claude-team:implementer` | 코드 구현 (읽기/쓰기) | Read,Write,Edit,Glob,Grep,Bash,SendMessage |
| frontend-dev | `claude-team:frontend` | 프론트엔드 구현 (읽기/쓰기) | Read,Write,Edit,Glob,Grep,Bash,SendMessage |
| backend-dev | `claude-team:backend` | 백엔드 구현 (읽기/쓰기) | Read,Write,Edit,Glob,Grep,Bash,SendMessage |
| qa | `claude-team:tester` | 테스트/검증 (읽기/쓰기) | Read,Write,Edit,Glob,Grep,Bash,SendMessage |
| critic | `claude-team:reviewer` | 비판적 리뷰 (읽기 전용) | Read,Glob,Grep,Bash,SendMessage |
| llms-analyst | `ai-cli-tools:llms` | 외부 LLM 분석 (specify용) | Bash,Read,Glob,Grep,WebSearch,WebFetch |
| llms-advisor | `ai-cli-tools:llms` | 외부 LLM 자문 (implement용) | Bash,Read,Glob,Grep,WebSearch,WebFetch |
| llms-reviewer | `ai-cli-tools:llms` | 외부 LLM 코드 리뷰 (verify용) | Bash,Read,Glob,Grep,WebSearch,WebFetch |

## 스폰 패턴 (통합)

**기본 모드** (Claude 네이티브 — Task tool 직접 스폰):
```
Task tool:
- subagent_type: "claude-team:{agent}" 또는 "ai-cli-tools:llms"
- team_name: "{team-name}"
- name: "{role-name}"
- description: "{role-name}: 역할 설명"
- run_in_background: true
- prompt: |
    [고수준 목표 + 컨텍스트]
```

> prompt에 작업 지시를 직접 포함하므로 별도 SendMessage가 불필요합니다.

**GPT 모드** (`--gpt` 플래그 — spawn-teammate Skill):
```
Skill tool:
- skill: "claude-team:spawn-teammate"
- args: "{role-name} --team {team-name}"

→ 스폰 완료 후:
SendMessage tool:
- type: "message"
- recipient: "{role-name}"
- content: |
    [고수준 목표 + 컨텍스트]
- summary: "{role-name} 작업 지시"
```

**차이점**: 기본 모드는 Task tool로 직접 스폰하여 에이전트의 내장 전문성 활용. GPT 모드는 spawn-teammate Skill + SendMessage로 역할 프롬프트를 전달.

## 자율성 모델

### 높은 자율성 (기본)

```
리더 → 고수준 목표 (SendMessage) → 팀메이트
팀메이트 ↔ 팀메이트 (직접 SendMessage 소통)
팀메이트 → 리더 (결과 보고 / 결정 필요시만)
리더 → 사용자 (승인, 선택 필요시)
```

**핵심 원칙:**
- 리더는 "무엇을 달성할지"만 전달, "어떻게"는 팀메이트가 결정
- 팀메이트는 서로 직접 소통하여 협업 (리더 중계 불필요)
- 리더는 사용자 대면 결정과 최종 조율만 담당
- 에이전트 타입의 내장 전문성이 상세 행동을 가이드

### SendMessage 지시 가이드

**좋은 예 (고수준 목표):**
```
사용자가 "{기능}"을 요청했습니다.
프로젝트 루트: {PROJECT_ROOT}
constitution 규칙: {내용 또는 "없음"}

이 기능에 대한 요구사항을 분석하고 구조화해주세요.
architect 팀메이트와 기술적 타당성을 협의하세요.
완료되면 리더에게 보고해주세요.
```

**나쁜 예 (과도한 상세 지시):**
```
1단계: 디렉토리 구조를 분석하세요
2단계: package.json을 읽으세요
3단계: FR-001을 다음 형식으로 작성하세요...
(에이전트 타입이 이미 이런 전문성을 내장하고 있음)
```

## 프로젝트 규모 판단 기준

| 규모 | 기준 | 팀 규모 |
|------|------|--------|
| Small | 단일 기능, 파일 5개 미만 변경 | 1-2명 |
| Medium | 복합 기능, 파일 5-15개 변경 | 2-3명 |
| Large | 대규모 기능, 파일 15개+ 변경 또는 여러 모듈 | 3-5명 |

## 커맨드별 추천 팀 구성

### /oh-my-speckit:specify
| 규모 | 팀원 | 에이전트 타입 |
|------|------|-------------|
| Small | pm + llms-analyst | planner + llms |
| Medium | pm + architect + llms-analyst | planner + architect + llms |
| Large | pm + architect + critic + llms-analyst | planner + architect + reviewer + llms |

> llms-analyst는 모든 규모에서 필수 스폰.

### /oh-my-speckit:implement
| 규모 | 팀원 | 에이전트 타입 |
|------|------|-------------|
| Medium (기본) | developer + qa + llms-advisor | implementer + tester + llms |
| Large | architect + developer-1 + developer-2 + qa + llms-advisor | architect + implementer x2 + tester + llms |

> Small 카테고리 없음 — Medium이 최소 팀 구성. llms-advisor는 모든 규모에서 필수.

*Medium/Large fullstack 프로젝트: developer → frontend-dev + backend-dev*

### /oh-my-speckit:verify
| 규모 | 팀원 | 에이전트 타입 |
|------|------|-------------|
| Small (빠른) | qa + llms-reviewer | tester + llms |
| Medium (표준) | qa + critic + llms-reviewer | tester + reviewer + llms |
| Large (완전) | qa + architect + critic + llms-reviewer | tester + architect + reviewer + llms |

> llms-reviewer는 모든 규모에서 필수 스폰.

## 복수 인스턴스 네이밍 규칙

같은 역할을 여러 명 스폰할 때의 이름 규칙:

| 인스턴스 수 | 네이밍 패턴 | 예시 |
|-----------|-----------|------|
| 1명 | 번호 없음 | `developer`, `qa` |
| 2명 이상 | `-N` 접미사 (1부터) | `developer-1`, `developer-2` |

**적용 예시:**
- Small implement: `developer` + `qa` (각 1명이므로 번호 없음)
- Medium implement (non-fullstack): `developer-1` + `developer-2` + `qa`
- Medium implement (fullstack): `frontend-dev` + `backend-dev` + `qa` (다른 역할이므로 번호 없음)
- Large implement: `architect` + `developer-1` + `developer-2` + `qa`

**SendMessage 라우팅**: 번호가 붙은 이름 그대로 recipient에 사용
```
SendMessage: recipient: "developer-1"
SendMessage: recipient: "developer-2"
```

## Fullstack 프로젝트 자동 감지

developer를 frontend-dev + backend-dev로 분리할지 판단하는 기준:

**감지 로직:**
1. `src/{frontend,client,web}/**` 디렉토리 존재 여부
2. `src/{backend,server,api}/**` 디렉토리 존재 여부
3. monorepo 구조: `apps/web` + `apps/api` 등
4. 여러 `package.json` 존재 (루트 외 하위 디렉토리)

**판단:**
- FE + BE 디렉토리 모두 존재 → fullstack → frontend-dev + backend-dev
- 그 외 → developer로 통합

## 프레임워크 기반 에이전트 오버라이드

프로젝트의 프레임워크를 감지하여 기본 에이전트를 전문 에이전트로 오버라이드합니다.

### 프레임워크 감지 로직

| 프레임워크 | 감지 파일 | 감지 키워드 |
|-----------|----------|------------|
| Spring Boot | `pom.xml`, `build.gradle` | `spring-boot` |
| FastAPI | `requirements.txt`, `pyproject.toml` | `fastapi` |
| NestJS | `package.json` | `@nestjs/core` |
| Next.js | `next.config.{js,ts,mjs}` | 파일 존재 |
| Nuxt | `nuxt.config.{js,ts}` | 파일 존재 |
| React | `package.json` | `"react"` (Next.js 미감지시) |
| Vue | `package.json` | `"vue"` (Nuxt 미감지시) |

### 오버라이드 매핑

| 역할 | 기본 에이전트 | 프레임워크 → 전문 에이전트 |
|------|-------------|--------------------------|
| developer | implementer | spring→spring-expert, fastapi→fastapi-expert, nestjs→nestjs-expert |
| frontend-dev | frontend | nextjs→nextjs-expert, nuxt→nuxt-expert, react→react-expert, vue→vue-expert |
| backend-dev | backend | spring→spring-expert, nestjs→nestjs-expert, fastapi→fastapi-expert |

### implement 커맨드 적용 예시

```
# Spring Boot 프로젝트 감지 시
Skill: spawn-teammate
args: "developer --team implement-{id} --agent-type claude-team:spring-expert"

# Next.js + NestJS fullstack 프로젝트 감지 시
Skill: spawn-teammate
args: "frontend-dev --team implement-{id} --agent-type claude-team:nextjs-expert"
args: "backend-dev --team implement-{id} --agent-type claude-team:nestjs-expert"
```

---

## LLM 모드 옵션

| 모드 | 플래그 | 스폰 방식 |
|------|--------|---------|
| Claude (기본) | 없음 | `spawn-teammate --agent-type claude-team:{agent}` |
| GPT | `--gpt` | `spawn-teammate` (--agent-type 없이) |

**기본 모드 (Claude)**: 에이전트 파일의 내장 프롬프트/도구/모델 설정이 자동 적용됩니다.
**GPT 모드**: cli-proxy-api를 통해 GPT-5.3 Codex로 실행됩니다. 역할 전문성은 SendMessage로 전달합니다.

---

## 역할별 초기 지시 컨텍스트

각 역할에게 SendMessage로 전달할 컨텍스트 항목:

### pm (planner)
- 사용자 요청 원문
- constitution.md 규칙 (있으면)
- 팀 내 다른 팀메이트 목록 (소통 대상)

### architect
- 분석 대상 기능 요약
- 프로젝트 루트 경로
- spec.md/plan.md 경로 (있으면)
- 팀 내 다른 팀메이트 목록

### developer / frontend-dev / backend-dev (implementer / frontend / backend)
- plan.md 경로
- 담당 Phase/Group 범위
- 재사용 분석 핵심 항목 (있으면)
- 팀 내 다른 팀메이트 목록 (특히 qa)

### qa (tester)
- 검증 대상 파일 목록 또는 전체 범위
- spec.md 경로 (요구사항 검증시)
- constitution.md 규칙 (있으면)
- 팀 내 다른 팀메이트 목록 (특히 developer)

### critic (reviewer)
- 검토 대상 (spec, plan, 또는 코드)
- spec.md/plan.md 경로
- 프로젝트 루트 경로
- 팀 내 다른 팀메이트 목록

## 팀메이트 공통 규칙

모든 팀메이트는 다음을 준수:
1. **SendMessage**: 작업 결과를 리더에게 반드시 보고
2. **팀메이트 간 직접 소통**: 다른 팀메이트에게 SendMessage로 직접 협업
3. **자율적 결정**: 세부 구현/분석 방법은 스스로 판단
4. **리더 에스컬레이션**: 사용자 결정이 필요한 사항만 리더에게 보고
5. **shutdown_request**: 수신 시 진행 중 작업 마무리 후 즉시 승인

## 서브에이전트 활용 가이드

팀메이트(implementer, frontend, backend, tester 등)는 Task 도구를 통해 전문가 서브에이전트를 활용할 수 있습니다.

### 활용 원칙

1. **전문 분석이 필요할 때만 스폰** — 단순 작업에는 사용 금지
2. **구체적인 질문 전달** — 전체 태스크가 아닌 특정 분석 요청
3. **결과는 참고용** — 서브에이전트 결과를 기반으로 팀메이트가 최종 구현

### 역할별 추천 서브에이전트

| 팀메이트 역할 | 추천 서브에이전트 |
|-------------|-----------------|
| developer (implementer) | db-architect, api-designer, security-architect, domain-modeler |
| frontend-dev (frontend, react-expert, vue-expert) | css-architect, a11y-auditor, state-designer, fe-performance, fe-tester |
| frontend-dev (nextjs-expert, nuxt-expert) | css-architect, a11y-auditor, state-designer, fe-performance, api-designer |
| backend-dev (backend, spring-expert, nestjs-expert, fastapi-expert) | db-architect, api-designer, security-architect, integration-tester |
| qa (tester) | test-strategist, integration-tester, fe-tester, side-effect-analyzer |
