# Repository Guidelines

## Project Structure & Module Organization

- `templates/` contains the Velocity templates (`ubl-invoice-core.vm`, `ubl-invoice-full.vm`) plus template pattern and helper docs.
- `semantic-model/` documents the shared semantic invoice model (`*-stub.yaml`, `*-mapping.yaml`, `xrechnung.schema.json`) plus XRechnung structure notes.
- `examples/` contains filled example instances for `core` and `full`.
- `mapping/` holds the mapping template into the semantic invoice model.
- `prototypes/` groups the reference implementations and validation spikes.
- `prototypes/velocity-renderer/` is the local Java renderer and smoke-test harness for Velocity 1.6.4.
- `prototypes/kosit-embedded-verifier/` validates existing XML through the embedded KoSIT API.
- `prototypes/kosit-isolated-classloader-verifier/` validates existing XML through KoSIT loaded in an isolated same-VM ClassLoader.
- `prototypes/kosit-verification-mcp-service/` exposes embedded KoSIT validation as a local STDIO MCP server.
- `prototypes/kosit-via-mcp-verifier/` is a Java client/host prototype that starts the MCP service as a child process.
- `prototypes/saxon-output-verifier-spike/` explores a profile-driven output verifier with JAXP and Saxon-HE.
- `prototypes/haskell-verifier/` contains the Stack-based Haskell library and CLI.
- `bundle-docs/` contains curated public reference files. A local upstream bundle ZIP may be kept there for ad hoc extraction, but it stays ignored.
  The committed validator-configuration ZIP may be used directly by local validation runs.

## Build, Test, and Development Commands

- `mvn -f prototypes/velocity-renderer/pom.xml package`
  Builds the local Velocity 1.6.4 renderer and fat JAR.
- `java -jar prototypes/velocity-renderer/target/velocity-renderer.jar --template templates/ubl-invoice-full.vm --model examples/ubl-invoice-full-example.yaml --out /tmp/invoice-full.xml`
  Renders the full template with the example invoice model.
- `java -jar prototypes/velocity-renderer/target/velocity-renderer.jar --template templates/ubl-invoice-core.vm --model examples/ubl-invoice-core-example.yaml --out /tmp/invoice-core.xml`
  Renders the core template with the example invoice model.
- `java -jar prototypes/velocity-renderer/target/velocity-renderer.jar --template templates/ubl-invoice-full.vm --model examples/ubl-invoice-full-example.yaml --out /tmp/invoice-full.xml --validate`
  Renders and validates the full template with KoSIT validator `1.6.2` from the Maven build and the committed XRechnung configuration ZIP.
- `java -jar prototypes/velocity-renderer/target/velocity-renderer.jar --model examples/ubl-invoice-full-example.yaml --check-model`
  Validates the semantic-model YAML or JSON against `semantic-model/xrechnung.schema.json`.
- `mvn -f prototypes/kosit-embedded-verifier/pom.xml test`
  Runs the focused unit tests for the embedded KoSIT verifier.
- `mvn -f prototypes/kosit-isolated-classloader-verifier/pom.xml package`
  Builds the host JAR plus the isolated KoSIT adapter/runtime under `target/kosit-runtime/lib`.
- `mvn -f prototypes/kosit-verification-mcp-service/pom.xml test`
  Runs the focused unit tests for the local MCP service.
- `mvn -f prototypes/kosit-via-mcp-verifier/pom.xml test`
  Runs the focused unit tests for the Java verifier that calls the MCP service.
- `mvn -f prototypes/saxon-output-verifier-spike/pom.xml test`
  Runs the focused unit tests for the Saxon output verifier spike.
- `stack build --work-dir .stack-work`
  from `prototypes/haskell-verifier/`; builds the Haskell library and `xrechnung-haskell-verifier`.
- `stack run xrechnung-haskell-verifier -- to-xml ../../examples/ubl-invoice-full-example.yaml`
  from `prototypes/haskell-verifier/`; converts the example YAML into UBL XML.
- `git status --short`
  Quick check before committing; the repo should stay clean and intentional.

## Coding Style & Naming Conventions

- Java uses 2-space indentation, `final` utility classes, and short explanatory comments only where needed.
- Haskell modules stay small and explicit; prefer clear record types and straightforward functions over clever abstractions.
- Keep Velocity templates null-tolerant and self-contained; no runtime `#parse(...)` dependencies.
- Use descriptive file names with `core` and `full` to distinguish reduced vs. broader reference artifacts.
- In Markdown, use relative links only. Do not commit absolute local filesystem paths.

## Testing Guidelines

- Use the `velocity-renderer` render and validation flow as the baseline check for template changes.
- After template or helper changes, render both `core` and `full`; validate at least `full`, and validate `core` when header, totals, payment, tax, or line output changes.
- The example files under `examples/` are the canonical sample source for local render and verifier checks.
- After semantic-model changes, run the schema check on both example YAML files before rendering.
- Normal render runs already perform the schema check unless `--no-model-check` is used explicitly.
- Treat `templates/ubl-invoice-full.vm` as the macro reference when aligning shared behavior with `templates/ubl-invoice-core.vm`.
- After Haskell changes, run `stack build` in `prototypes/haskell-verifier/`; use the CLI for quick smoke tests like `to-xml`, `format`, or `verify`.
- For Java verifier changes, run the focused Maven tests in the touched prototype before relying on a smoke run.

## Commit & Pull Request Guidelines

- Keep commit messages short, imperative, and specific, e.g. `Switch project license to 0BSD`.
- Prefer one clean commit over many tiny fixup commits when preparing public-facing changes.
- PRs should explain the affected area (`templates`, `semantic-model`, `prototypes/*`, `bundle-docs`, renderer/verifier), list validation steps, and mention any doc or example updates.

## Repository Hygiene

- Do not commit `prototypes/velocity-renderer/target/`, `prototypes/haskell-verifier/.stack-work/`, `.codex/`, or other local artifacts.
- Treat `bundle-docs/` as curated reference material with external licensing; the repo’s `0BSD` license applies to project-authored content, not automatically to bundled third-party documents.
