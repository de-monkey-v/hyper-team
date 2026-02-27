---
description: "LLM CLI (Gemini/Codex) 호출 래퍼. 모델 플래그를 스크립트로 강제하여 올바른 모델로 호출합니다."
---

# LLM CLI Invocation

외부 LLM CLI를 호출할 때 사용하는 스킬입니다.
`llm-invoke.sh` 스크립트가 모델 플래그를 강제하므로, 에이전트가 직접 `-m` 플래그를 지정할 필요가 없습니다.

## Script

`$SKILL_DIR/scripts/llm-invoke.sh`

## Enforced Models

| Provider | Model |
|----------|-------|
| Gemini | `gemini-3.1-pro-preview` |
| Codex | `gpt-5.3-codex` |

모델을 변경하려면 `scripts/llm-invoke.sh` 상단의 변수를 수정하세요.

## Usage Patterns

### Gemini

```bash
# 프롬프트만
$SCRIPT "gemini" "What is the best practice for X"

# 파일 pipe + 프롬프트
cat src/auth.py | $SCRIPT "gemini" "Review this code for security issues"

# Git diff pipe
git diff | $SCRIPT "gemini" "Analyze these changes"
```

### Codex

```bash
# exec 모드 (코드 분석, stdin pipe)
cat src/auth.py | $SCRIPT "codex" "exec" "Review this code"

# review 모드 (Git 변경사항)
$SCRIPT "codex" "review" "--uncommitted" "Review these changes"
$SCRIPT "codex" "review" "--base" "main"
```

## Error Handling

- CLI 미설치 시 `/ai-cli-tools:setup` 안내 메시지 출력
- provider/subcommand 오류 시 사용법 출력
- 모든 에러는 stderr로 출력, exit code 1 반환
