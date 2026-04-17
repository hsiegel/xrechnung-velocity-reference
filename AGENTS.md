# Repository Guidelines

## Project Structure & Module Organization

- `templates/` contains the Velocity templates: `ubl-invoice-core.vm` and `ubl-invoice-full.vm`.
- `semantic-model/` documents the shared semantic invoice model (`*-stub.yaml`, `*-mapping.yaml`, `xrechnung.schema.json`).
- `examples/` contains filled example instances for `core` and `full`.
- `mapping-matrix/` holds the mapping template into the semantic invoice model.
- `notes/` contains design notes and helper contracts.
- `velocity-runner/` is the local Java smoke-test harness for Velocity 1.6.4.
- `haskell-tools/` contains the Stack-based Haskell library and CLI.
- `bundle-docs/` contains curated public reference files. A local upstream bundle ZIP may be kept there for ad hoc extraction, but it stays ignored.
  The committed validator-configuration ZIP may be used directly by local validation runs.

## Build, Test, and Development Commands

- `mvn -f velocity-runner/pom.xml package`
  Builds the local Velocity 1.6.4 runner and fat JAR.
- `java -jar velocity-runner/target/velocity-runner.jar --template templates/ubl-invoice-full.vm --model examples/ubl-invoice-full-example.yaml --out /tmp/invoice-full.xml`
  Renders the full template with the example invoice model.
- `java -jar velocity-runner/target/velocity-runner.jar --template templates/ubl-invoice-core.vm --model examples/ubl-invoice-core-example.yaml --out /tmp/invoice-core.xml`
  Renders the core template with the example invoice model.
- `java -jar velocity-runner/target/velocity-runner.jar --template templates/ubl-invoice-full.vm --model examples/ubl-invoice-full-example.yaml --out /tmp/invoice-full.xml --validate`
  Renders and validates the full template with KoSIT validator `1.6.0` from the Maven build and the committed XRechnung configuration ZIP.
- `java -jar velocity-runner/target/velocity-runner.jar --model examples/ubl-invoice-full-example.yaml --check-model`
  Validates the semantic-model YAML or JSON against `semantic-model/xrechnung.schema.json`.
- `stack build --work-dir .stack-work`
  from `haskell-tools/`; builds the Haskell library and `xrechnung-cli`.
- `stack run -- to-xml ../examples/ubl-invoice-full-example.yaml`
  from `haskell-tools/`; converts the example YAML into UBL XML.
- `git status --short`
  Quick check before committing; the repo should stay clean and intentional.

## Coding Style & Naming Conventions

- Java uses 2-space indentation, `final` utility classes, and short explanatory comments only where needed.
- Haskell modules stay small and explicit; prefer clear record types and straightforward functions over clever abstractions.
- Keep Velocity templates null-tolerant and self-contained; no runtime `#parse(...)` dependencies.
- Use descriptive file names with `core` and `full` to distinguish reduced vs. broader reference artifacts.
- In Markdown, use relative links only. Do not commit absolute local filesystem paths.

## Testing Guidelines

- There is no formal unit test suite yet; use the `velocity-runner` render and validation flow as the baseline check.
- After template or helper changes, render both `core` and `full`; validate at least `full`, and validate `core` when header, totals, payment, tax, or line output changes.
- The example files under `examples/` are the canonical sample source for local runner checks.
- After semantic-model changes, run the schema check on both example YAML files before rendering.
- Normal render runs already perform the schema check unless `--no-model-check` is used explicitly.
- Treat `templates/ubl-invoice-full.vm` as the macro reference when aligning shared behavior with `templates/ubl-invoice-core.vm`.
- After Haskell changes, run `stack build` in `haskell-tools/`; use the CLI for quick smoke tests like `to-xml`, `format`, or `verify`.

## Commit & Pull Request Guidelines

- Keep commit messages short, imperative, and specific, e.g. `Switch project license to 0BSD`.
- Prefer one clean commit over many tiny fixup commits when preparing public-facing changes.
- PRs should explain the affected area (`templates`, `semantic-model`, `haskell-tools`, `bundle-docs`, runner), list validation steps, and mention any doc or example updates.

## Repository Hygiene

- Do not commit `velocity-runner/target/`, `haskell-tools/.stack-work/`, `.codex/`, or other local artifacts.
- Treat `bundle-docs/` as curated reference material with external licensing; the repo’s `0BSD` license applies to project-authored content, not automatically to bundled third-party documents.
