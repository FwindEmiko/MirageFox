# Security Policy

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Use GitHub's private vulnerability reporting:

1. Go to the [Security tab](https://github.com/FwindEmiko/MirageFox/security) of this repository
2. Click **Report a vulnerability**
3. Describe the issue, how to reproduce it, and what an attacker could achieve

You can expect an initial response within 7 days. If the report is valid, a fix and a coordinated
disclosure timeline will be agreed with you before anything is public.

## Scope

This plugin runs inside a Minecraft server process and talks to a language model. The threat model
worth reporting includes, but is not limited to:

| Area | Example |
|---|---|
| **Prompt injection** | A player message makes the model call a tool it should not, or leak another player's data |
| **Cross-player leakage** | One player's conversation, memory or coordinates become visible to another |
| **Privilege escalation** | A non-admin player gets a tool to act on someone else |
| **Command injection** | Any path where model output reaches a shell or `dispatchCommand` |
| **Credential exposure** | API keys or tokens written to logs, chat, or the packaged jar |
| **Denial of service** | A crafted input that blocks the server thread or exhausts the thread pools |
| **Supply chain** | A dependency or build step compromised |

## Out of scope

- The model producing offensive, wrong or unhelpful *text* — that is a model-quality issue, not a plugin vulnerability. Report it as a normal issue.
- Server-side misconfiguration (for example, giving `miragefox.admin` to everyone).
- Vulnerabilities in Paper/Leaf, PlaceholderAPI or Vault themselves — report those upstream.
- Anything requiring an already-compromised server or console access.

## Built-in protections

For reference when auditing:

- Tools are whitelisted by registration; unknown tool names are rejected
- No command layer — tools call Bukkit APIs directly
- Non-admin callers can only target themselves
- Per-player context isolation; system prompt and player input are separated
- Player input sanitised (control characters and colour codes stripped, length capped)
- `ai.max-tokens` and tool-round limits bound the cost of a single turn
- Shipped config contains no credentials; CI inspects the packaged jar for key patterns

## Supported versions

The latest release receives security fixes. Older versions are supported on a best-effort basis —
please reproduce the issue on the latest release before reporting.
