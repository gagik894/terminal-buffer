---
name: terminal-feature-work
description: Terminal emulator feature implementation workflow for this Kotlin repository. Use when adding or changing terminal protocols, parser/core/integration behavior, modes, SGR, OSC, CSI, DCS, Unicode, input, rendering contracts, or feature-gap documentation.
---

# Terminal Feature Work

Use this skill for any new terminal protocol or behavior in this repository.

## Workflow

1. Identify the owning layer:
   - `terminal-parser` recognizes byte/protocol syntax and emits semantic commands.
   - `terminal-core` owns grid physics, modes, width, attributes, storage, and state.
   - `terminal-integration` maps existing parser semantics to existing core APIs.
2. Read the relevant module `AGENTS.md`.
3. Add tests that describe real terminal behavior before or alongside code.
4. Implement the smallest layer-correct behavior.
5. Add full-path tests when bytes should flow through the parser.
6. Update `docs/terminal-feature-gap-map.md` when support or scope changes.

## Rules

- Do not fake unsupported behavior.
- Do not move width, cursor bounds, grid mutation, or mode persistence into the parser.
- Do not parse ANSI/OSC/SGR in integration.
- Keep hot paths primitive, table-shaped, and allocation-conscious.
- Prefer existing module helpers and fixture style.

## Done

The work is done only when normal, omitted/default, malformed, hostile, and
boundary cases are tested; unsupported parts are explicit TODOs; and the
parser/core/integration boundary remains clean.
