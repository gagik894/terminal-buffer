# Terminal PTY Agent Guide

`terminal-pty` owns local pseudo-terminal process lifecycle and stream wiring.
It connects PTY stdout to `terminal-parser` and connects host-bound input bytes
from `terminal-input` plus core response bytes to PTY stdin.

## Boundary

PTY owns:

- spawning and closing PTY-backed terminal processes.
- pumping process output bytes into `TerminalOutputParser`.
- serializing UI input bytes and parser/core response bytes to process stdin.
- resizing the PTY and the public terminal buffer together.
- reporting PTY lifecycle, reader failures, BEL, and title metadata through
  `TerminalPtyEventListener`.

PTY must not:

- parse escape sequences or inspect parser state.
- mutate grid/cursor state except through public `TerminalBufferApi` methods.
- encode keyboard, paste, focus, or mouse bytes itself.
- duplicate `terminal-integration` command mapping.
- expose concurrent access to `DefaultTerminalInputEncoder`.

## Testing

Unit tests should use fake process streams for lifecycle and wiring behavior.
Native PTY smoke tests are opt-in because PTY4J startup is platform-sensitive:

```text
./gradlew :terminal-pty:test --tests "com.gagik.terminal.pty.TerminalPtyRealProcessTest" "-Dterminal.pty.integration=true"
```
