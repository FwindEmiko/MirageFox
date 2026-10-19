# Contributing to MirageFox

Thanks for wanting to help. This project is a Minecraft server plugin that gives players an LLM-driven companion; the rule of thumb for every contribution is **the model decides, the server executes**.

## Ways to help

- **Bug reports** — include server version, plugin version, model and endpoint (redact your key), and the relevant log lines.
- **Model compatibility results** — if you tested a model that isn't in the compatibility matrix, tell us the result. Function-calling behaviour varies a lot between models and we can't test them all.
- **New tools** — see the tool SPI section below.
- **Translations** — English and Chinese are maintained in-repo; other languages are welcome.

## Development setup

Requires JDK 21.

```bash
git clone https://github.com/FCelestial/MirageFox.git
cd MirageFox
./gradlew build        # -> build/libs/MirageFox-<version>.jar
```

To test locally, point a Paper 1.21+ test server at the built jar and configure `plugins/MirageFox/config.yml` with any OpenAI-compatible endpoint.

## Ground rules

1. **Never commit credentials.** No API keys, tokens or server addresses in source, config, tests or screenshots. CI fails the build if a key pattern appears in the packaged config.
2. **Tools never build command strings.** Tools call Bukkit APIs directly. Do not add `dispatchCommand` or shell calls.
3. **Respect the thread contract.** Model calls are async; anything touching the world goes through the main thread. Never block the server thread waiting on a model response.
4. **Default to no side effects on other players.** A player's request should only affect that player unless an admin explicitly grants more.
5. **Keep the tool surface small and typed.** A new tool needs a clear description, typed parameters and a documented refusal behaviour.

## Adding a tool

1. Extend `AbstractTool`, implement `getName()`, `getDefinition()` and `execute(...)`.
2. Register it in the plugin bootstrap.
3. Write the description as if explaining to a person — the model reads it exactly as written, and vague descriptions cause wrong tool choices.
4. Test with at least two different models; tool-selection behaviour differs between vendors.

## Pull requests

- One concern per PR.
- Describe what you ran to verify it (model, version, scenario), not just what changed.
- Update the README or docs if behaviour or configuration changes.

## License

By contributing you agree your work is released under the [MIT License](LICENSE).
