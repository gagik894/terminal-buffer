# Terminal Pipeline Agent Guide

This repository is building a modern, secure terminal pipeline in Kotlin/JVM 21.
It is not chasing literal full xterm parity. The goal is a clean, fast,
professional terminal architecture for contemporary shells and TUIs.

Read this file before making changes. Then read the module-level `AGENTS.md`
for the module you touch.

## Architecture

The project is split into strict layers:

- `terminal-parser`: byte stream to semantic terminal commands.
- `terminal-core`: headless terminal state, grid physics, modes, attributes,
  scrollback, width policy, and storage.
- `terminal-integration`: adapters that map parser semantic commands to core
  APIs.

Keep these boundaries intact:

- Parser parses. It owns UTF-8 decoding, ANSI state machines, CSI/OSC/DCS
  recognition, charset shifts, grapheme segmentation, and semantic dispatch.
- Core mutates and stores. It owns cursor physics, margins, wrapping, tab stops,
  scrollback, pen attributes, width calculation, and mode state.
- Integration maps. It must not parse protocols and must not reach into core
  internals.

Width calculation belongs in core. The parser may assemble grapheme clusters,
but it must not decide how many grid cells a cluster occupies because width
depends on terminal mode and policy.

## Non-Goals

Do not add these unless the product direction explicitly changes:

- Tektronix 4014 emulation.
- Media Copy / printer passthrough.
- X11-specific font protocols.
- Blind OSC 52 clipboard writes.
- Unbounded or unaudited OSC/DCS query responses.
- "Everything xterm ever accepted" compatibility.

Use `docs/terminal-feature-gap-map.md` as the living source for supported,
missing, intentionally deferred, and policy-gated features.

## Engineering Rules

- Preserve strong SRP. A feature belongs in exactly one responsible layer.
- Prefer the existing module structure and local helper APIs over new patterns.
- Keep hot paths allocation-free or allocation-minimal: primitive arrays,
  packed integers, generated-table-shaped lookups, and explicit buffers.
- Do not use regex, ICU, `BreakIterator`, or object-heavy parsing in parser/core
  hot paths.
- Do not fake unsupported behavior. Add a `TODO(parser-gap)`,
  `TODO(core-gap)`, `TODO(integration)`, or `TODO(policy)` and document it in
  the gap map.
- Keep behavior table-driven where appropriate, especially CSI/SGR/Unicode
  classification.
- Avoid broad refactors while adding protocol behavior. Tight changes are
  easier to verify and safer for terminal semantics.

## Testing Doctrine

Tests must assert real terminal semantics, not current implementation quirks.
If the implementation is wrong, the test should fail.

For every behavior change:

- Add or update focused unit tests for the responsible component.
- Add integration tests for real byte streams when parser behavior is involved.
- Cover normal cases, omitted/default parameters, malformed input, overflow,
  boundary values, recovery behavior, and chunking where relevant.
- Use recording fixtures to keep assertions explicit, but do not hide the
  semantic expectation inside helpers.
- Do not loosen assertions to make broken behavior pass.
- For new protocol files, write tests first where feasible.

## Definition of Done

A change is not done until:

- It is implemented in the correct layer.
- Relevant parser/core/integration tests exist and pass.
- Edge and hostile cases are covered, not only the happy path.
- Unsupported parts are explicit TODOs, not silent no-ops pretending to work.
- `docs/terminal-feature-gap-map.md` is updated when capability or scope
  changes.
- No unrelated formatting churn or architecture drift is introduced.

## Useful Commands

- Full test suite: `./gradlew test`
- Parser tests: `./gradlew :terminal-parser:test`
- Core tests: `./gradlew :terminal-core:test`
- Integration tests: `./gradlew :terminal-integration:test`

In sandboxed sessions, Gradle may need approval because wrapper/cache writes can
leave the workspace.

## Start Here

- Feature backlog: `docs/terminal-feature-gap-map.md`
- Core contract: `terminal-core/docs/terminal-core-contract.md`
- Project skills index: `docs/agent-skills.md`
- Repo-local Codex skills: `.codex/skills/*/SKILL.md`
