# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versioning follows [SemVer](https://semver.org/).

## [Unreleased]

### Planned — v0.2 (local-first)
- `StorageBackend` abstraction with a local file implementation (player memory, affinity, mood)
- Local knowledge-base search (`plugins/MirageFox/knowledge/*.md`)
- `/fox doctor` self-check: endpoint reachability, function-calling support, storage writability, permissions
- First-run configuration guide instead of an exception when the API key is missing
- Migration command for servers that used the optional cloud backend

### Planned — v0.3 (ecosystem)
- Public tool SPI with per-tool permission, cooldown and audit log
- Multi-provider layer with failover and token/cost accounting (`/fox usage`)
- Documented compatibility matrix across model families

### Planned — v1.0 (release)
- Modrinth / Hangar publication
- Bilingual documentation site
- Demo server

## [0.1.0] — 2026-10-19

First public snapshot of the plugin as it runs in production.

### Added
- OpenAI-compatible function-calling loop with up to 5 tool rounds per turn
- 22 server-side tools: weather, time, particles, fireworks, titles, sounds, healing, feeding,
  potion effects, launching, lightning, mob summoning, random teleport, recall, whisper,
  high-five, player info, player list, memory write, affinity, doc search, conversation search
- Per-player conversation context with cross-player isolation
- Private chat sessions with independent context and idle timeout
- `MirageFoxAPI` for third-party plugins (placeholder resolution, per-day rate limiting)
- External `persona.yml`: character name, display name, server name, currency name, system prompt
- Optional cloud backend for long-term memory and knowledge-base search (off by default)
- Vault economy integration with per-tool pricing and automatic refund on failure
- Input sanitisation and prompt-injection guards
- Config validation with safe fallbacks for every value

### Security
- Tool whitelist; unknown tool calls rejected
- No command layer — tools call Bukkit APIs directly
- Tools default to affecting only the caller; admin permission required for targeting others
- Credentials removed from the shipped config; CI checks the packaged jar for key patterns
