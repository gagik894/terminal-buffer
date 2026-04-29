# Terminal Buffer

A high-performance terminal pipeline in Kotlin. The core remains a headless
screen-state engine, while parser, integration, input, session, transport, and
PTY modules are split so rendering and UI can sit on top without touching grid
internals.

## Architecture

- **terminal-protocol** holds dependency-free control-code constants, ANSI/DEC
  mode ids, and shared mode vocabulary used by parser, core, integration, and
  input code.
- **terminal-parser** converts byte streams into semantic terminal commands.
- **terminal-integration** maps parser commands onto public core APIs and
  host metadata callbacks.
- **terminal-input** encodes keyboard, paste, focus, and mouse events to
  host-bound bytes.
- **terminal-transport-api** defines the connector contract for PTY/SSH/test
  transports.
- **terminal-session** serializes parser/core mutation and host-bound writes.
- **terminal-pty** exposes PTY4J-backed local processes as transport connectors.
- **terminal-testkit** provides connector fakes for cross-module tests.
- **TerminalBuffer** is the facade that coordinates the state, mutation, cursor,
  mode, and reader/inspector surfaces.
- **ScreenBuffer** owns one complete screen arena: `HistoryRing`, `ClusterStore`,
  cursor, saved cursor, and scroll margins.
- **Line** stores cells in flat primitive arrays:
  - `IntArray` codepoints / sentinels / cluster handles
  - `IntArray` packed attributes
  - `wrapped` soft-wrap flag
- **MutationEngine** owns spatial cell physics: overwrite, wrap, scroll, erase,
  wide-cell annihilation, and line editing.
- **CursorEngine** owns cursor movement, save/restore, and tabbing.
- **TerminalResizer** reflows the primary screen and deep-copies surviving
  cluster payloads into a fresh arena.

## Unicode boundary

The core is cluster-capable but not a grapheme segmenter.

- `writeCodepoint` / `writeText` are scalar convenience entrypoints.
- `writeCluster` is the parser-facing entrypoint for pre-segmented grapheme
  clusters.
- A future parser module should own grapheme segmentation, buffering, and
  dispatch into the core.

## Pipeline Handoff

The detailed public contract lives in
[`docs/terminal-core-contract.md`](terminal-core/docs/terminal-core-contract.md).
Known parser/core/integration gaps are tracked in
[`docs/terminal-feature-gap-map.md`](docs/terminal-feature-gap-map.md).
Agent/session guidance lives in [`AGENTS.md`](AGENTS.md), with reusable
playbooks in [`docs/agent-skills.md`](docs/agent-skills.md).

- Parser-facing durable mode control lives on `TerminalModeController`.
- Input/UI-facing durable mode reads live on `TerminalModeReader` via immutable
  `TerminalModeSnapshot` values.
- Shared protocol vocabulary lives in `:terminal-protocol`; input code should
  consume that module and core snapshots, not parser internals.
- The core stores host-controlled input and presentation flags, but it does not
  encode input events or render frames.
- Transport connectors own byte-stream I/O threads. `TerminalSession` owns
  parser/core synchronization, response draining, and ordering between UI input
  bytes and terminal response bytes.

## Behavioral notes

- Wide characters and grapheme clusters are stored explicitly in the grid.
- Resize reflows the primary screen, wipes the alternate screen, and resets both
  buffers' scroll regions to the full viewport.
- ED 3 follows xterm/VTE semantics here: it clears scrollback history while
  preserving the visible viewport.

## Development

- JDK 21
- Run tests with `./gradlew test`
