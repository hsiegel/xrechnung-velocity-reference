# Repository Guidelines

## Project Structure & Module Organization

- `templates/` contains the uploadable Velocity templates: `ubl-invoice-core.vm` and `ubl-invoice-full.vm`.
- `public-model/` documents the neutral `$xr` model (`*-stub.yaml`, `*-mapping.yaml`).
- `examples/` contains filled example instances for `core` and `full`.
- `mapping-matrix/` holds the DTO-to-`$xr` mapping template.
- `notes/` contains design notes and helper contracts.
- `velocity-runner/` is the local Java smoke-test harness for Velocity 1.6.4.
- `bundle-docs/` contains curated public reference files. A local upstream bundle ZIP may be kept there for ad hoc extraction, but it stays ignored.

## Build, Test, and Development Commands

- `mvn -f velocity-runner/pom.xml package`
  Builds the local Velocity 1.6.4 runner and fat JAR.
- `java -jar velocity-runner/target/velocity-runner.jar --template templates/ubl-invoice-full.vm --out /tmp/invoice-full.xml`
  Renders the full template with the sample `$xr` model.
- `java -jar velocity-runner/target/velocity-runner.jar --template templates/ubl-invoice-core.vm --out /tmp/invoice-core.xml`
  Renders the core template.
- `java -jar velocity-runner/target/velocity-runner.jar --template templates/ubl-invoice-full.vm --out /tmp/invoice-full.xml --validate`
  Renders and validates the full template with KoSIT validator `1.6.0` from the local bundle ZIP.
- `git status --short`
  Quick check before committing; the repo should stay clean and intentional.

## Coding Style & Naming Conventions

- Java uses 2-space indentation, `final` utility classes, and short explanatory comments only where needed.
- Keep Velocity templates null-tolerant and self-contained; no runtime `#parse(...)` dependencies.
- Use descriptive file names with `core` and `full` to distinguish reduced vs. broader reference artifacts.
- In Markdown, use relative links only. Do not commit absolute local filesystem paths.

## Testing Guidelines

- There is no formal unit test suite yet; use the `velocity-runner` render and validation flow as the baseline check.
- After template or helper changes, render both `core` and `full`; validate at least `full`, and validate `core` when header, totals, payment, tax, or line output changes.
- Treat `templates/ubl-invoice-full.vm` as the macro reference when aligning shared behavior with `templates/ubl-invoice-core.vm`.

## Commit & Pull Request Guidelines

- Keep commit messages short, imperative, and specific, e.g. `Switch project license to 0BSD`.
- Prefer one clean commit over many tiny fixup commits when preparing public-facing changes.
- PRs should explain the affected area (`templates`, `public-model`, `bundle-docs`, runner), list validation steps, and mention any doc or example updates.

## Repository Hygiene

- Do not commit `velocity-runner/target/`, `.codex/`, or other local artifacts.
- Treat `bundle-docs/` as curated reference material with external licensing; the repo’s `0BSD` license applies to project-authored content, not automatically to bundled third-party documents.
