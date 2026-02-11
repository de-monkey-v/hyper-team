# Specify Workflow Detail

스펙 작성 워크플로우의 상세 절차. SKILL.md에서 참조.

## Phase별 상세 절차

### Phase 0: 프로젝트 루트 확인

**스마트 탐색 + 사용자 확인 방식으로 `.specify/` 폴더 위치를 결정.**

#### Step 1: 프로젝트 루트 자동 탐색

cwd부터 상위로 올라가며 다음 마커를 순서대로 탐색:
1. `.specify/` - 기존 폴더가 있으면 **최우선** 사용
2. `package.json` - Node.js/프론트엔드 프로젝트
3. `pyproject.toml` - Python 프로젝트
4. `Cargo.toml` - Rust 프로젝트
5. `go.mod` - Go 프로젝트
6. `.git/` - 최후의 fallback

```bash
# 탐색 예시
ls -d "$PWD"/.specify 2>/dev/null || ls -d "$PWD"/../.specify 2>/dev/null || ...
ls "$PWD"/package.json 2>/dev/null || ls "$PWD"/../package.json 2>/dev/null || ...
```

#### Step 2: 사용자 확인

AskUserQuestion으로 탐색된 위치 확인:

```
📍 .specify 폴더 위치를 확인합니다.

탐색된 프로젝트 루트: /path/to/project
(package.json 기준)

이 위치에 .specify/ 폴더를 생성/사용할까요?
```

**옵션:**
- "예, 이 위치 사용"
- "아니오, 다른 위치 지정"

확인된 경로를 `PROJECT_ROOT`로 저장.

---

### Phase 2: 조사 및 제안

**스펙을 바로 작성하지 않고, 먼저 조사 후 사용자에게 제안.**

#### Step 1: 관련 정보 조사

**smart-searcher** 에이전트 호출 (자동 도구 선택):

```
Task:
- subagent_type: "search:smart-searcher"
- description: "기술 조사"
- prompt: |
    [기능/라이브러리명] 관련 조사:
    - 구현 방법, API 사용법, 베스트 프랙티스
    - 구현 패턴, 주의사항, 일반적인 실수
```

**자동 선택**: Context7(라이브러리 문서) + WebSearch(일반 정보)

#### Step 2: 접근 방식 제안

조사 결과를 바탕으로 **제안 형식**으로 사용자에게 제시:

```markdown
## 스펙 제안: [기능명]

### 조사 결과
- [관련 기술/패턴 요약]
- [참고할 만한 사례]
- [주의사항]

### 제안하는 접근 방식

**1. 핵심 기능**
- [주요 기능 1]
- [주요 기능 2]

**2. 기술 스택**
- [추천 기술/라이브러리]
- [선택 이유]

**3. 아키텍처**
- [제안하는 구조]

### 예상 범위
- 사용자 스토리: ~N개
- 기능 요구사항: ~N개
- 엣지 케이스: ~N개
```

#### Step 3: 사용자 승인 요청

AskUserQuestion으로 승인:
- **승인** → Phase 3로 진행
- **수정 요청** → 피드백 반영 후 재제안
- **다른 방향** → 새로운 조사 후 재제안

---

### Phase 2.5: 기존 시스템 영향 분석

**승인된 접근 방식을 바탕으로 기존 시스템 영향을 분석.**

#### 분석 항목

| 분석 항목 | 확인 내용 |
|----------|----------|
| 기존 API 변경 | 기존 엔드포인트 수정 필요 여부 |
| 데이터 모델 변경 | DB 스키마/타입 변경 필요 여부 |
| 공유 컴포넌트 | 공통 UI/유틸 수정 필요 여부 |
| 기존 기능 동작 | 기존 기능 동작에 영향 여부 |

#### 호환성 옵션

**옵션 A: 하위 호환성 유지**
- 기존 API 유지, optional 필드만 추가

**옵션 B: V2로 새로 생성**
- 새 API 엔드포인트 분리

**옵션 C: 기존 대체 (Breaking Change)**
- 기존 API 직접 수정

---

### Phase 3: 요구사항 분석 (에이전트)

**requirements-analyzer 에이전트 호출:**

```
Task tool:
- subagent_type: "oh-my-speckit:requirements-analyzer"
- prompt: "[사용자 요구사항 + 승인된 접근 방식]. FR/NFR 정의, 사용자 스토리 도출, 엣지 케이스 식별"
```

에이전트 수행 작업:
1. **사용자 스토리 도출** - As a [user], I want [goal] so that [benefit]
2. **기능 요구사항 (FR) 정의** - 검증 가능한 요구사항
3. **비기능 요구사항 (NFR) 정의** - 성능, 보안, 확장성
4. **엣지 케이스 식별** - 경계 조건, 예외 상황

---

### Phase 5: 스펙 문서 작성

**spec.md 핵심 섹션:**

- 메타데이터 (ID, Status, Priority, Created)
- 요청 이력
- 개요
- 아키텍처 컨텍스트
- 사용자 스토리
- 기능 요구사항 (FR)
- 비기능 요구사항 (NFR)
- 엣지 케이스
- 기술 스택
- 제약 조건

**템플릿**: `spec-template.md` 참조

---

## 재진입 시나리오

**Verify에서 "스펙 불명확" 실패 시 재진입:**

1. 기존 spec.md 읽기
2. 불명확한 부분 목록 확인
3. AskUserQuestion으로 명확화
4. **수정 이력 기록**:
   ```markdown
   N. **YYYY-MM-DD HH:MM** - [수정 요약]
      > [수정 요청 내용]
   ```
5. spec.md 업데이트
6. design으로 재진입 안내
