# Terminal Buffer

A high-performance terminal model/core in Kotlin. This repository builds the
headless screen state engine only: grid memory, scrollback, wrap semantics,
resize reflow, attributes, alternate-screen state, and cluster-aware cell
storage.

## Architecture

- **TerminalBuffer** is the façade that coordinates the state, mutation, cursor,
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

## Behavioral notes

- Wide characters and grapheme clusters are stored explicitly in the grid.
- Resize reflows the primary screen, wipes the alternate screen, and resets both
  buffers' scroll regions to the full viewport.
- ED 3 follows xterm/VTE semantics here: it clears scrollback history while
  preserving the visible viewport.

## Development

- JDK 21
- Run tests with `./gradlew test`
