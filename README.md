# hyper-team

A collection of Claude Code plugins for power users. Install individually or use them all together.

**Documentation**: https://de-monkey-v.github.io/hyper-team/

## Plugins

| Plugin | Description | Setup |
|--------|-------------|-------|
| [search](plugins/search) | Unified web & documentation search with Tavily/Brave load balancing | `/search:setup` |
| [notification](plugins/notification) | Multi-channel notifications — Slack, Discord, Desktop | `/notification:setup` |
| [ai-cli-tools](plugins/ai-cli-tools) | Gemini CLI & Codex CLI integration for code review | `/ai-cli-tools:setup` |
| [git-utils](plugins/git-utils) | Git workflow automation — commit, branch, diff, log, status | `/git-utils:setup` |
| [hyper-team](plugins/hyper-team) | Agent Teams workflow helper — setup, prompt generation, verification | `/hyper-team:setup` |
| [plugin-creator](plugins/plugin-creator) | Plugin scaffolding & validation toolkit | `/plugin-creator:setup` |

## Installation

### Install a single plugin

```bash
claude plugin install <plugin-name>@de-monkey-v/hyper-team
```

For example:

```bash
claude plugin install search@de-monkey-v/hyper-team
claude plugin install notification@de-monkey-v/hyper-team
```

### First-time setup

After installing a plugin, run its setup command:

```
/search:setup
/notification:setup
/ai-cli-tools:setup
```

The setup wizard will:
- Check prerequisites
- Install dependencies
- Configure environment variables (detects bash/zsh automatically)
- Guide you through API key registration with direct links

All setup commands support `--language=eng` (default) and `--language=kor`.

## Plugin Details

### search

Parallel search across multiple sources with automatic load balancing.

- **Free**: WebSearch (built-in) + Context7 MCP (library docs)
- **Optional**: Tavily API (deep search, extraction) + Brave Search API (web, news)
- Load balancing alternates between Tavily/Brave to distribute API credits evenly

**Commands**: `/search:search`, `/search:deep-research`, `/search:status`, `/search:setup`

### notification

Automatic notifications on Claude Code events (task complete, waiting, session end).

- **Slack**: Incoming Webhooks
- **Discord**: Webhooks with rich embeds
- **Desktop**: OS-native notifications (Linux/macOS/Windows)
- Includes work summary, experience extraction, and next-step suggestions

**Commands**: `/notification:send`, `/notification:setup`

### ai-cli-tools

Use other AI CLIs from within Claude Code.

- **Gemini CLI**: `npm install -g @google/gemini-cli`
- **Codex CLI**: `npm install -g @openai/codex`
- Delegate code review and analysis via the `@llms` agent

**Commands**: `/ai-cli-tools:setup`

### git-utils

Git workflow automation with smart commit message generation.

- Follows Conventional Commits (`<feat>`, `<fix>`, `<refactor>`, etc.)
- Learns commit style from project history
- Detects secrets before committing

**Commands**: `/git-utils:commit`, `/git-utils:setup`
**Skills**: commit, branch, log, diff, status

### hyper-team

Streamline Agent Teams workflows.

- Generate detailed todo specs and team prompts from natural language
- Verify implementations with scoring (tests, code quality, integration)
- Requires tmux for split-pane teammate display

**Commands**: `/hyper-team:setup`, `/hyper-team:make-prompt`, `/hyper-team:verify`

### plugin-creator

Create Claude Code plugins interactively.

- 7 agents + 7 skills + 4 commands
- Supports full lifecycle: create, modify, validate
- Skill -> Agent -> Command architecture pattern

**Commands**: `/plugin-creator:create-plugin`, `/plugin-creator:modify-plugin`, `/plugin-creator:validate`, `/plugin-creator:setup`

## Environment Variables

| Variable | Plugin | Required | Source |
|----------|--------|----------|--------|
| `TAVILY_API_KEY` | search | Optional | https://app.tavily.com |
| `BRAVE_API_KEY` | search | Optional | https://brave.com/search/api/ |
| `SLACK_WEBHOOK_URL` | notification | Optional | https://api.slack.com/messaging/webhooks |
| `DISCORD_WEBHOOK_URL` | notification | Optional | Discord Server Settings > Integrations > Webhooks |
| `ENABLE_DESKTOP_NOTIFICATION` | notification | Optional | Set to `true` |

## License

MIT
