---
name: setup
description: Interactive setup wizard for the plugin-creator plugin. Verifies Claude Code environment and plugin directory structure.
argument-hint: "[--language=eng]"
allowed-tools: Bash, Read, Write, Edit, AskUserQuestion, Glob
---

# /plugin-creator:setup - Setup Wizard

Interactive setup wizard for the plugin-creator plugin.

## Language Resolution

Determine the output language using the following priority:

1. **Argument override**: If `$ARGUMENTS` contains `--language=eng` or `--language=kor`, use that.
2. **Metadata file**: Read `.hyper-team/metadata.json` and use the `language` field (`"eng"` or `"kor"`).
3. **Default**: If neither is available, default to English (`eng`).

```
Read .hyper-team/metadata.json (if exists)
→ Extract language field
→ Override with $ARGUMENTS --language flag if present
→ Default to "eng" if neither source available
```

---

## Step 1: Detect Shell Environment

```
Bash: echo $SHELL
```

Determine if the user uses bash or zsh:
- If contains "zsh": RC_FILE=~/.zshrc
- If contains "bash": RC_FILE=~/.bashrc
- Otherwise: Ask the user which shell config file they use.

---

## Step 2: Verify Claude Code Environment

Since this command is running inside Claude Code, the environment is already functional. Confirm this to the user:

- English: "Claude Code environment is active and functional."
- Korean: "Claude Code 환경이 활성화되어 정상 동작 중입니다."

Optionally check the Claude Code version if available:

```
Bash: claude --version 2>/dev/null || echo "Version check not available"
```

---

## Step 3: Check Plugin Directory Structure

Verify the plugin-creator plugin's directory structure is intact by checking for required directories.

Determine the plugin base path. Use the path where this command file resides:

```
PLUGIN_DIR: the parent directory of the commands/ folder this file is in
(typically: plugins/plugin-creator or the directory specified by --plugin-dir)
```

Check for required directories:

```
Bash: ls -d plugins/plugin-creator/agents/ plugins/plugin-creator/skills/ plugins/plugin-creator/commands/ plugins/plugin-creator/.claude-plugin/ 2>&1
```

**Expected directories:**
- `agents/` - Agent definition files
- `skills/` - Skill files with SKILL.md and references
- `commands/` - Slash command files
- `.claude-plugin/` - Plugin manifest

**If any directory is missing:**
- Display a warning with the missing directory name
- English: "Warning: Directory `[dir]` is missing. The plugin may not function correctly."
- Korean: "경고: `[dir]` 디렉토리가 없습니다. 플러그인이 정상 동작하지 않을 수 있습니다."

**If all directories exist:**
- English: "All required directories are present."
- Korean: "필요한 디렉토리가 모두 존재합니다."

---

## Step 4: Show Available Components

List all available components in the plugin-creator plugin.

### Agents

```
Bash: ls plugins/plugin-creator/agents/*.md 2>/dev/null | xargs -I{} basename {} .md
```

### Skills

```
Bash: ls -d plugins/plugin-creator/skills/*/ 2>/dev/null | xargs -I{} basename {}
```

### Commands

```
Bash: ls plugins/plugin-creator/commands/*.md 2>/dev/null | xargs -I{} basename {} .md
```

Display the results in a structured table:

| Component | Name | Description |
|-----------|------|-------------|
| Agent | skill-creator | Creates skill files |
| Agent | agent-creator | Creates agent files |
| Agent | command-creator | Creates command files |
| Agent | hook-creator | Creates hook configurations |
| Agent | plugin-validator | Validates plugin structure |
| Agent | skill-reviewer | Reviews skill quality |
| Agent | plan-designer | Interactive plan design |
| Agent | settings-creator | Creates plugin settings |
| Command | create-plugin | Interactive plugin creation |
| Command | modify-plugin | Interactive plugin modification |
| Command | validate | Validate plugin structure |
| Command | create-orchestration-command | Create orchestration commands |
| Command | setup | This setup wizard |

(Populate actual names from the directory listing above)

---

## Step 5: Quick Start Guide

Display a brief getting started guide based on the resolved language:

### English:

```
## Getting Started with Plugin Creator

### Creating a New Plugin
Use `/plugin-creator:create-plugin` to start an interactive plugin creation wizard.
It will guide you through designing and generating all components.

### Modifying an Existing Plugin
Use `/plugin-creator:modify-plugin` to modify components of an existing plugin.

### Validating a Plugin
Use `/plugin-creator:validate` to check if your plugin structure and configuration are correct.

### Recommended Architecture
Follow the **Skill → Agent → Command** pattern:
1. **Skills** define knowledge and guidelines (SKILL.md files)
2. **Agents** automate tasks using skills (agent .md files)
3. **Commands** provide user entry points that orchestrate agents

### Prompt Engineering
Plugin-creator follows Anthropic's prompt engineering guidelines:
- **XML Tags**: Agent prompts use `<instructions>`, `<examples>`, `<constraints>` tags
- **Structured Data Transfer**: Commands pass data to agents via XML `<task-context>` tags
- **Multishot Examples**: Agent system prompts include 3-5 example scenarios

### Plugin Structure
```
my-plugin/
├── .claude-plugin/
│   └── plugin.json          # Plugin manifest
├── agents/                   # Agent definitions
├── skills/                   # Skill definitions
│   └── my-skill/
│       └── SKILL.md
├── commands/                 # Slash commands
└── hooks/                    # Event hooks (optional)
    └── hooks.json
```

### Useful Tips
- Use `claude --plugin-dir ./my-plugin` to test a plugin locally
- Run `/plugin-creator:validate` after making changes
- Check the skills/ directory for reference documentation
```

### Korean:

```
## Plugin Creator 시작 가이드

### 새 플러그인 만들기
`/plugin-creator:create-plugin`으로 대화형 플러그인 생성 마법사를 시작하세요.
모든 컴포넌트의 설계와 생성을 안내합니다.

### 기존 플러그인 수정
`/plugin-creator:modify-plugin`으로 기존 플러그인의 컴포넌트를 수정할 수 있습니다.

### 플러그인 검증
`/plugin-creator:validate`로 플러그인 구조와 설정이 올바른지 확인하세요.

### 권장 아키텍처
**Skill → Agent → Command** 패턴을 따르세요:
1. **Skills** - 지식과 가이드라인 정의 (SKILL.md 파일)
2. **Agents** - 스킬을 활용한 작업 자동화 (에이전트 .md 파일)
3. **Commands** - 에이전트를 조율하는 사용자 진입점

### 프롬프트 엔지니어링
Plugin-creator는 Anthropic 공식 프롬프트 엔지니어링 가이드라인을 따릅니다:
- **XML 태그**: 에이전트 프롬프트에 `<instructions>`, `<examples>`, `<constraints>` 태그 사용
- **구조화된 데이터 전달**: 커맨드에서 에이전트로 XML `<task-context>` 태그로 데이터 전달
- **멀티샷 예제**: 에이전트 시스템 프롬프트에 3-5개 예제 시나리오 포함

### 플러그인 구조
```
my-plugin/
├── .claude-plugin/
│   └── plugin.json          # 플러그인 매니페스트
├── agents/                   # 에이전트 정의
├── skills/                   # 스킬 정의
│   └── my-skill/
│       └── SKILL.md
├── commands/                 # 슬래시 커맨드
└── hooks/                    # 이벤트 훅 (선택사항)
    └── hooks.json
```

### 유용한 팁
- `claude --plugin-dir ./my-plugin`으로 플러그인을 로컬에서 테스트하세요
- 변경 후 `/plugin-creator:validate`를 실행하세요
- skills/ 디렉토리에서 참고 문서를 확인하세요
```

---

## Step 6: Summary

Display a final summary based on the resolved language:

### English Summary:

```
## plugin-creator Setup Complete

| Item                    | Status |
|-------------------------|--------|
| Claude Code Environment | Active |
| Plugin Directory        | Intact |
| Agents                  | [count] available |
| Skills                  | [count] available |
| Commands                | [count] available |

No external API keys or dependencies required.
The plugin-creator plugin is ready to use!

### Available Commands
- `/plugin-creator:create-plugin` - Create a new plugin interactively
- `/plugin-creator:modify-plugin` - Modify an existing plugin
- `/plugin-creator:validate` - Validate plugin structure
- `/plugin-creator:create-orchestration-command` - Create orchestration commands
```

### Korean Summary:

```
## plugin-creator 설정 완료

| 항목                    | 상태   |
|-------------------------|--------|
| Claude Code 환경        | 활성   |
| 플러그인 디렉토리       | 정상   |
| 에이전트                | [count]개 사용 가능 |
| 스킬                    | [count]개 사용 가능 |
| 커맨드                  | [count]개 사용 가능 |

외부 API 키나 의존성이 필요하지 않습니다.
plugin-creator 플러그인을 사용할 준비가 되었습니다!

### 사용 가능한 명령어
- `/plugin-creator:create-plugin` - 대화형으로 새 플러그인 생성
- `/plugin-creator:modify-plugin` - 기존 플러그인 수정
- `/plugin-creator:validate` - 플러그인 구조 검증
- `/plugin-creator:create-orchestration-command` - 오케스트레이션 커맨드 생성
```
