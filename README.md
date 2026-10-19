# MirageFox

**Put an AI companion in your Minecraft server that can actually do things.**

MirageFox is a Paper plugin that gives your server a persistent character. Players talk to it in chat; a language model answers and calls **21 server-side tools** through OpenAI-style function calling — change the weather, spawn fireworks, heal a player, summon a mob. Every action runs inside your server process, under your permission model, with your own API key and any OpenAI-compatible endpoint.

No bot account. No gateway. No third-party memory service.

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B-green.svg)]()
[![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Leaf-blue.svg)]()
[![Build](https://github.com/FwindEmiko/MirageFox/actions/workflows/build.yml/badge.svg)](https://github.com/FwindEmiko/MirageFox/actions/workflows/build.yml)

[中文说明](#中文说明) · [配置](#configuration) · [开发者接口](#developer-api) · [路线图](#roadmap)

---

## Why another AI bot?

Most "AI in Minecraft" setups fall into one of three traps:

| Approach | Problem |
|---|---|
| External bot (Mineflayer / headless client) | Needs a real player account, can't touch server state, lags behind the tick loop |
| Scripted NPC | Keyword matching dressed up as conversation; players see through it in two lines |
| Cloud AI service | Your players' chat leaves your server, and the plugin dies when the service does |

MirageFox takes a fourth path: **the model thinks remotely, the actions run locally.** The LLM only decides *what* to do. Every tool calls the Bukkit API directly — no command strings, nothing to inject into.

## Features

- **Function calling, not text parsing.** The model picks from a registry of typed tools; unknown calls are rejected.
- **21 built-in tools** across world, player, entity, info and memory categories.
- **Per-player context isolation** — one player's chat cannot bleed into another's session.
- **Private chat sessions** with independent context and idle timeout.
- **Any OpenAI-compatible endpoint** — OpenAI, self-hosted vLLM, or a relay that speaks the same protocol.
- **Editable persona** — character name, tone and system prompt live in `persona.yml`, not in the code.
- **Plugin API** — `MirageFoxAPI` lets quest, event or welcome plugins make the companion speak.
- **Guard rails on by default** — tools affect only the caller unless an admin allows more; lightning deals no damage; launches come with slow-fall.
- **Optional economy** — Vault integration with per-tool pricing, cooldowns and automatic refund on failure.

## Requirements

| | |
|---|---|
| Server | Paper or Leaf **1.21+** (built against 1.21.4 API) |
| Java | **21** |
| Optional | PlaceholderAPI (placeholder resolution), Vault (tool pricing) |
| Model | Any endpoint supporting OpenAI-style `tools` / `tool_calls` |

## Quick start

1. Drop `MirageFox-<version>.jar` into `plugins/`.
2. Start the server once, then edit `plugins/MirageFox/config.yml`:
   ```yaml
   ai:
     base-url: "https://api.openai.com/v1"
     api-key: "sk-your-key"
     model: "gpt-4o-mini"
   ```
3. Edit `plugins/MirageFox/persona.yml` — at minimum `name` and `server-name`.
4. `/fox reload`, then mention the character's name in chat.

Both `MIRAGEFOX_API_KEY` and `MIRAGEFOX_CLOUD_TOKEN` environment variables override the config file, which is handy for containerised deployments.

## Configuration

Two files, two concerns:

| File | Controls |
|---|---|
| `config.yml` | Behaviour — chat mode, visibility, cooldown, tool switches, AI endpoint |
| `persona.yml` | Identity — character name, display name, server name, currency name, system prompt |

```yaml
# config.yml (excerpt)
chat:
  context-size: 12            # turns kept per player
  mode: "mention_only"        # mention_only | always
  mention-prefixes: []        # empty -> uses persona name
  visibility: "broadcast"     # broadcast | whisper | nearby
  cooldown-seconds: 3
  private-chat:
    timeout-minutes: 10

tools:
  enabled: true
  lightning-damage: false     # keep false: harmless by design
  launch-max-power: 5
```

```yaml
# persona.yml (excerpt)
name: "Star"
display-name: "§d§lStar§r"
server-name: "Example Server"
currency-name: "coins"
system-prompt: |
  You are {name}, a friendly guardian spirit on the Minecraft server "{server}".
  ...
```

Placeholders `{name}`, `{server}` and `{currency}` are substituted at load time. Full reference: [`docs/configuration.md`](docs/configuration.md).

## Built-in tools

| Category | Tools |
|---|---|
| World | `set_time`, `set_weather`, `spawn_particles`, `spawn_firework`, `play_sound`, `send_title` |
| Player | `heal_player`, `feed_player`, `give_effect`, `launch_player`, `random_teleport`, `recall_player`, `whisper_player`, `high_five` |
| Entity | `strike_lightning`, `summon_mob` |
| Info | `player_info`, `list_players` |
| Memory | `remember_player`, `adjust_favorability`, `search_docs`, `search_conversations` |

Every tool declares a description and typed parameters; the model sees exactly what is written there, so tool descriptions are treated as part of the API surface.

## Commands & permissions

| Command | Permission | Description |
|---|---|---|
| `/fox reload` | `miragefox.admin` | Reload config and persona |
| `/fox toggle` | `miragefox.admin` | Pause / resume the companion |
| `/fox reset` | `miragefox.admin` | Clear all conversation contexts |
| `/fox status` | `miragefox.admin` | Show endpoint, model and tool count |
| `/fox admin pluginchat <prompt> <player> [public\|private\|nearby]` | `miragefox.pluginchat` | Make the companion speak to a player |
| `/fox admin broadcast <prompt>` | `miragefox.pluginchat` | Broadcast a companion message |

`miragefox.talk` (default: everyone) gates chatting with the companion.

## Architecture

```
Player chat
    │
    ▼
ChatListener ──► ChatManager ──► AIClient ──► OpenAI-compatible endpoint
                    │                              │
                    │◄──── tool_calls ─────────────┘
                    ▼
              ToolRegistry ──► Tool (Bukkit API, main thread)
                    │
                    ▼
              MessageService ──► chat / whisper / nearby
```

- `ChatManager` owns the conversation loop, the tool-call cycle and per-player turn serialisation.
- `ToolRegistry` is the extension point: register a `Tool`, get an LLM-visible schema for free.
- `ToolExecutor` guarantees world mutation happens on the main thread, with timeouts.
- `MessageService` handles formatting and visibility so tools never deal with chat output.
- `StorageBackend` (v0.2) will abstract persistence: local by default, remote optional.

Details: [`docs/architecture.md`](docs/architecture.md).

## Security model

- **Tool whitelist** — the model can only invoke registered tools; unknown names are rejected.
- **No command layer** — tools call Bukkit APIs directly, so there is no command string to inject into.
- **Target authorization** — a request affects only the caller unless the caller holds `miragefox.admin`.
- **Context isolation** — conversation state is keyed per player, limiting cross-player prompt injection.
- **Input sanitisation** — control characters and colour codes stripped, length capped.
- **Prompt delimiting** — system prompt and player input are separated, with explicit instructions to ignore embedded "system" text.
- **Permission split** — chatting is open to everyone; administration requires OP.
- **No credentials in the repo** — the shipped config ships with placeholders, and CI fails the build if a key pattern appears in the packaged jar.

Found a security issue? Please read [`SECURITY.md`](SECURITY.md) before opening a public issue.

## Cost

Rough figures from production use, per conversation turn (one player message + one reply):

| Model class | Tokens / turn | Notes |
|---|---|---|
| Small/flash models | ~1.5k–3k | Usually one tool round; cheapest and fine for chat |
| Mid-tier | ~2k–4k | Better tool selection, fewer retries |
| Frontier | ~3k–6k | Best at multi-step tool use, ~10× the cost |

At 100 turns/hour on a flash-class model you are typically looking at cents per hour, not dollars. The plugin sets `ai.max-tokens: 500` by default to keep replies short — long replies cost more and read worse in chat.

## Developer API

```java
MirageFoxAPI api = MirageFoxAPI.getInstance();

// Congratulate a player when your quest plugin completes a stage
api.sendPluginMessage(player, "The player just finished the tutorial. Congratulate them.",
                      MirageFoxAPI.Visibility.PRIVATE)
   .thenAccept(reply -> getLogger().info("companion said: " + reply));
```

The returned `CompletableFuture` completes off the main thread — hop back with `Bukkit.getScheduler().runTask` before touching the world.

## Roadmap

- [x] **v0.1** — function-calling core, 21 tools, per-player context, private chat, editable persona *(in production)*
- [ ] **v0.2** — local-first storage, `/fox doctor` self-check, first-run guide, cloud migration
- [ ] **v0.3** — public tool SPI with per-tool permissions, multi-provider failover, usage accounting
- [ ] **v1.0** — bilingual docs site, Modrinth / Hangar release, demo server

See [`CHANGELOG.md`](CHANGELOG.md) for details.

## Building

Requires JDK 21.

```bash
./gradlew build
# -> build/libs/MirageFox-<version>.jar
```

## Contributing

Issues and pull requests are welcome — see [`CONTRIBUTING.md`](CONTRIBUTING.md). Model compatibility reports are especially useful: function-calling behaviour differs a lot between vendors and we cannot test them all.

## License

MIT — see [`LICENSE`](LICENSE).

---

## 中文说明

**给 Minecraft 服务器一个真的会做事、不只是会聊天的 AI 伙伴。**

MirageFox 是一个 Paper 插件。玩家在聊天里叫它的名字，它用大模型回话，并通过 OpenAI 风格的 function calling 调用 **21 个服务端工具**——改天气、放烟花、给玩家治疗、召唤生物。所有动作都在你的服务器进程内执行，走你的权限系统，用你自己配置的 API Key 和任意 OpenAI 兼容端点。

不需要机器人账号，不需要网关，玩家聊天不经过第三方记忆服务。

### 和常见方案的区别

| 常见做法 | 问题 |
|---|---|
| Mineflayer / 假人客户端 | 要占一个真实玩家账号；动不了服务端状态；跟 tick 循环脱节 |
| 固定脚本 NPC | 本质是关键词匹配，玩家两句话就出戏 |
| 云端 AI 服务 | 玩家聊天离开你的服务器；服务方一停，插件报废 |

MirageFox 走第四条路：**模型在远端思考，动作在本地执行。** 模型只决定「做什么」，每个工具直接调用 Bukkit API，不走命令系统，天然没有命令注入面。

### 功能

- **function calling，不是文本解析**：模型从类型化工具注册表里挑工具，未注册的调用直接拒绝
- **21 个内置工具**：世界、玩家、实体、信息、记忆五类
- **按玩家隔离上下文**：A 玩家的聊天不会污染 B 玩家的会话
- **私聊模式**：独立上下文 + 空闲超时
- **任意 OpenAI 兼容端点**：官方、自建 vLLM、各种中转都可以
- **人设可编辑**：角色名、语气、系统提示词都在 `persona.yml`，不在代码里
- **插件接口**：`MirageFoxAPI` 让任务/活动/欢迎插件让 AI 主动说话
- **默认安全**：工具默认只作用于发起者本人；闪电不伤人；击飞自带缓降
- **可选经济系统**：Vault 集成，按工具定价、冷却、失败自动退款

### 环境要求

- 服务端：Paper 或 Leaf **1.21+**（按 1.21.4 API 构建）
- Java：**21**
- 可选：PlaceholderAPI（变量解析）、Vault（工具收费）
- 模型：任何支持 OpenAI `tools` / `tool_calls` 的端点

### 快速开始

1. 把 `MirageFox-<版本>.jar` 放进 `plugins/`
2. 启动一次服务器，编辑 `plugins/MirageFox/config.yml`，填 `ai.base-url` / `ai.api-key` / `ai.model`
3. 编辑 `plugins/MirageFox/persona.yml`，至少改 `name` 和 `server-name`
4. `/fox reload`，然后在聊天里叫它的名字

环境变量 `MIRAGEFOX_API_KEY` / `MIRAGEFOX_CLOUD_TOKEN` 优先于配置文件，方便容器化部署。

### 两个配置文件

| 文件 | 管什么 |
|---|---|
| `config.yml` | 行为：对话模式、可见性、冷却、工具开关、AI 端点 |
| `persona.yml` | 身份：角色名、显示名、服务器名、货币名、系统提示词 |

`persona.yml` 里支持 `{name}` / `{server}` / `{currency}` 三个占位符，加载时自动替换。完整说明见 [`docs/configuration.md`](docs/configuration.md)。

### 安全设计

工具白名单、不走命令系统（无注入面）、默认只作用于自己、上下文按玩家隔离、输入清洗、提示词分隔、权限分离、仓库内不含任何真实凭据（CI 会检查打包产物）。

发现安全问题请先读 [`SECURITY.md`](SECURITY.md)，不要在公开 issue 里贴细节。

### 成本

生产环境实测，一次对话（玩家一句 + 回复一句）大致消耗：

- flash 级模型：约 1.5k–3k token，通常一轮工具调用
- 中档模型：约 2k–4k token，工具选择更准、重试更少
- 旗舰模型：约 3k–6k token，多步工具调用最强，成本约为前者的十倍

默认 `ai.max-tokens: 500`，回复短、读起来更像聊天，也更省钱。

### 开发者接口

```java
MirageFoxAPI api = MirageFoxAPI.getInstance();
api.sendPluginMessage(player, "玩家刚完成新手引导，恭喜他一下。", MirageFoxAPI.Visibility.PRIVATE);
```

返回的 `CompletableFuture` 在非主线程完成，碰世界之前记得 `runTask` 回主线程。

### 路线图

- [x] **v0.1** — function calling 核心、21 个工具、按玩家隔离上下文、私聊、可编辑人设（已在生产运行）
- [ ] **v0.2** — 本地优先存储、`/fox doctor` 自检、首次启动向导、云端数据迁移
- [ ] **v0.3** — 公开工具 SPI（工具级权限/冷却/审计）、多端点故障切换、用量统计
- [ ] **v1.0** — 中英双语文档站、Modrinth / Hangar 发布、公开演示服

### 许可证

MIT，见 [`LICENSE`](LICENSE)。欢迎 issue 与 PR。
