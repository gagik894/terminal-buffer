# Terminal Input Agent Guide

`terminal-input` owns host-to-terminal input encoding. It converts UI-level key,
paste, focus, and later mouse events into bytes written to the terminal host
input stream.

Keyboard, paste, and focus encoding are implemented. Mouse encoding and richer
keyboard protocols remain future milestones. Follow the staged plan in
`docs/terminal-input-implementation-plan.md` when extending behavior.

## Boundary

Input owns:

- keyboard event vocabulary and encoding.
- paste and focus report encoding.
- future mouse report encoding.
- allocation-conscious scratch buffers for generated input sequences.

Input must not:

- parse terminal output bytes or escape sequences.
- mutate terminal grid, cursor, scrollback, or pen state.
- depend on `terminal-parser` or `terminal-integration`.
- read renderer state, grid arrays, cursor internals, or parser state.
- invent terminal mode semantics outside core/protocol vocabulary.

The intended dependency shape is:

```text
UI adapter -> terminal-input -> TerminalHostOutput -> PTY stdin
```

For mode-dependent behavior, input should read packed mode bits once per event
from core's input-readable API and then encode from that stable value.

## Current Dependency Note

The target plan refers to a future `:terminal-core-api` module. This repository
currently exposes core API types from `:terminal-core`, so the scaffold depends
on `:terminal-core` until that API split exists.

## Implementation Rules

- Add `TerminalHostOutput` to `:terminal-protocol` before adding encoders.
- Keep `KeyboardEncoder` stateless with respect to modes; pass packed mode bits
  into each encode call.
- Do not add a `TerminalInputModeSnapshot` data class.
- Do not decode mode bit positions in `:terminal-input`; use core API helpers.
- Do not allocate arrays or strings for generated CSI/SS3 sequences on the hot
  path.
- Keep mouse deferred until keyboard, paste, and focus are implemented and green.

## Testing

Input tests should assert exact bytes. Cover validation errors, modifier
translation, printable UTF-8, Ctrl/Alt handling, special keys, keypad modes,
bracketed paste, focus reporting, and a real core mode-bit integration case.

Do not hide expected byte sequences behind broad helpers. Fixtures may record
bytes, but each test should make the terminal semantics obvious.
