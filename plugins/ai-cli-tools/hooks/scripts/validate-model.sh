#!/usr/bin/env bash
# validate-model.sh
# PreToolUse hook: gemini/codex CLI 인보케이션의 모델 플래그를 검증한다.
# - gemini → -m gemini-3.1-pro-preview 필수
# - codex  → -m gpt-5.3-codex 필수
#
# "command -v gemini", "which gemini" 등 CLI를 직접 실행하지 않는
# 명령어는 차단하지 않는다.

set -euo pipefail

INPUT=$(cat)

# jq가 있으면 jq, 없으면 python3으로 command 추출
if command -v jq &>/dev/null; then
  COMMAND=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
else
  COMMAND=$(echo "$INPUT" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('tool_input',{}).get('command',''))" 2>/dev/null || true)
fi

# command가 비어있으면 통과
[[ -z "$COMMAND" ]] && exit 0

# is_cli_invocation: 주어진 도구명이 커맨드 위치에서 실행되는지 확인
# 커맨드 위치 = 줄 시작, | 뒤, ; 뒤, && 뒤, || 뒤
is_cli_invocation() {
  local tool="$1"
  echo "$COMMAND" | grep -qE "(^|[|;&]\s*)${tool}(\s|$)"
}

# gemini CLI 인보케이션 검증
if is_cli_invocation "gemini"; then
  if ! echo "$COMMAND" | grep -q -- "-m gemini-3.1-pro-preview"; then
    echo "BLOCKED: gemini CLI 호출 시 '-m gemini-3.1-pro-preview' 플래그가 필요합니다. 명령어를 수정하세요." >&2
    exit 2
  fi
fi

# codex CLI 인보케이션 검증
if is_cli_invocation "codex"; then
  if ! echo "$COMMAND" | grep -q -- "-m gpt-5.3-codex"; then
    echo "BLOCKED: codex CLI 호출 시 '-m gpt-5.3-codex' 플래그가 필요합니다. 명령어를 수정하세요." >&2
    exit 2
  fi
fi

exit 0
