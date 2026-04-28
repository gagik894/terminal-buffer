# Terminal Input Implementation Plan

This document is the staged plan for the future `:terminal-input` module. The
module is scaffolded now, but encoder implementation is intentionally deferred.

## Ground Rules

- `TerminalHostOutput` lives in `:terminal-protocol`.
- Core exposes zero-allocation input-readable packed mode bits.
- Input reads packed mode bits once per input event.
- Do not add a `TerminalInputModeSnapshot` data class.
- `KeyboardEncoder` is stateless with respect to modes.
- Mouse is deferred until keyboard, paste, and focus are green.

## Phase 1 - Confirm Module Dependencies

Target dependencies:

```text
:terminal-protocol
:terminal-core
```

Forbidden dependencies:

```text
:terminal-parser
:terminal-core implementation internals
:terminal-integration implementation
UI toolkit modules
```

Target byte-output shape:

```text
UI adapter
-> terminal-input
-> TerminalHostOutput
-> PTY stdin
```

Target mode-read shape:

```text
terminal-input
-> TerminalInputState
-> atomic packed mode bits in core
```

The input module must never read `ParserState`, terminal grid arrays, cursor
internals, renderer state, or integration adapter internals.

## Phase 2 - Shared Host-Output Boundary

Add `TerminalHostOutput` to package:

```text
com.gagik.terminal.protocol.host
```

Planned API:

```kotlin
public interface TerminalHostOutput {
    public fun writeByte(byte: Int)

    public fun writeBytes(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    )

    public fun writeAscii(text: String)

    public fun writeUtf8(text: String)
}
```

Hard rule: `writeBytes` must synchronously consume or copy the provided byte
range. Encoders may reuse scratch buffers immediately after `writeBytes`
returns.

Do not add only `writeBytes(bytes: ByteArray)`. Dynamic encoders need
`offset` and `length` without `sliceArray`.

## Phase 3 - Event Vocabulary

Package:

```text
com.gagik.terminal.input.event
```

Planned files:

- `TerminalModifiers.kt`
- `TerminalKey.kt`
- `TerminalKeyEvent.kt`
- `TerminalPasteEvent.kt`
- `TerminalFocusEvent.kt`

`TerminalModifiers` stores raw internal modifier bits:

```text
NONE = 0
SHIFT = 1 shl 0
ALT = 1 shl 1
CTRL = 1 shl 2
META = 1 shl 3
VALID_MASK = SHIFT or ALT or CTRL or META
```

Apply the `1 + modifiers` CSI parameter rule only when encoding CSI sequences.

`TerminalKey` should contain only non-printable or keypad keys:

```text
UP, DOWN, LEFT, RIGHT
HOME, END, PAGE_UP, PAGE_DOWN, INSERT, DELETE
BACKSPACE, ENTER, TAB, ESCAPE
F1..F12
NUMPAD_ENTER, NUMPAD_DIVIDE, NUMPAD_MULTIPLY, NUMPAD_SUBTRACT, NUMPAD_ADD
NUMPAD_DECIMAL, NUMPAD_0..NUMPAD_9
```

Do not put printable characters in `TerminalKey`. Printable input is a Unicode
scalar codepoint on `TerminalKeyEvent`.

`TerminalKeyEvent` should validate:

- modifier mask contains only valid bits.
- exactly one of `key` or `codepoint` is present.
- codepoints are Unicode scalar values from `0..0x10ffff`, excluding surrogate
  range `0xd800..0xdfff`.

Event objects may allocate at the UI boundary. Encoder internals should avoid
new arrays and strings for generated control sequences.

## Phase 4 - Input Encoder API

Package:

```text
com.gagik.terminal.input.api
```

Planned API:

```kotlin
public interface TerminalInputEncoder {
    public fun encodeKey(event: TerminalKeyEvent)

    public fun encodePaste(event: TerminalPasteEvent)

    public fun encodeFocus(event: TerminalFocusEvent)
}
```

Do not include mouse yet. Add mouse in the next milestone.

## Phase 5 - Static Terminal Sequences

Package:

```text
com.gagik.terminal.input.impl
```

Add `TerminalSequences` with ASCII byte arrays for:

- back-tab: `ESC [ Z`
- normal arrows: `ESC [ A/B/C/D`
- application arrows: `ESC O A/B/C/D`
- normal home/end: `ESC [ H`, `ESC [ F`
- application home/end: `ESC O H`, `ESC O F`
- insert/delete/page up/page down: `ESC [ 2~`, `ESC [ 3~`, `ESC [ 5~`,
  `ESC [ 6~`
- F1-F4: `ESC O P/Q/R/S`
- F5-F12: `ESC [ 15~`, `17~`, `18~`, `19~`, `20~`, `21~`, `23~`, `24~`
- bracketed paste wrappers: `ESC [ 200~`, `ESC [ 201~`
- focus reports: `ESC [ I`, `ESC [ O`

Include a private ASCII literal helper that rejects non-ASCII literals.

## Phase 6 - Scratch Buffer

Add `InputScratchBuffer` in `com.gagik.terminal.input.impl`.

Requirements:

- default capacity: 64 bytes.
- `clear()`.
- `appendByte(Int)` with `0..255` validation.
- `appendAscii(String)` with ASCII validation.
- `appendDecimal(Int)` without allocation.
- `writeTo(TerminalHostOutput)` using `writeBytes(bytes, 0, length)`.

This buffer exists to avoid `StringBuilder`, `String`, and `sliceArray` for
dynamic CSI sequences.

## Phase 7 - Top-Level Encoder Facade

Add `DefaultTerminalInputEncoder`.

Responsibilities:

- hold `TerminalInputState` and `TerminalHostOutput`.
- own one `InputScratchBuffer`.
- delegate to keyboard, paste, and focus encoders.
- read `inputState.getInputModeBits()` exactly once per input event.

That single mode read is the consistency boundary for each input event.

## Phase 8 - Keyboard Encoder

Add `KeyboardEncoder`.

Required behavior:

- printable codepoints write UTF-8.
- Alt prefixes one ESC before the encoded codepoint or Ctrl result.
- Ctrl maps ASCII letters and selected punctuation to C0/DEL:
  - `Ctrl+A..Z` / `Ctrl+a..z` -> `0x01..0x1a`
  - `Ctrl+@` and `Ctrl+Space` -> `0x00`
  - `Ctrl+[` -> `0x1b`
  - `Ctrl+\` -> `0x1c`
  - `Ctrl+]` -> `0x1d`
  - `Ctrl+^` -> `0x1e`
  - `Ctrl+_` -> `0x1f`
  - `Ctrl+?` -> `0x7f`
- Enter writes CR normally and CR LF in new-line mode.
- Tab writes HT; Shift+Tab writes `ESC [ Z`.
- Backspace writes DEL.
- Escape writes ESC.
- Application cursor mode switches unmodified arrows/home/end to SS3 forms.
- Modified arrows/home/end use CSI `1;modifier final`.
- Insert/delete/page keys use tilde forms with optional modifier parameters.
- F1-F4 use SS3 unmodified and CSI modified forms.
- F5-F12 use tilde forms.
- Application keypad mode uses SS3 keypad finals.

`TerminalInputState` must expose helper functions for:

```text
isApplicationCursorKeys(bits)
isApplicationKeypad(bits)
isBracketedPasteEnabled(bits)
isFocusReportingEnabled(bits)
isNewLineMode(bits)
```

If helpers are missing, add them to core API first. Do not decode bit positions
inside `:terminal-input`.

## Phase 9 - Paste Encoder

Add `PasteEncoder`.

Required behavior:

- plain paste writes UTF-8 text.
- bracketed paste writes `ESC [ 200~`, UTF-8 text, then `ESC [ 201~`.
- empty bracketed paste still emits both wrappers.

Paste sanitization is deferred.

## Phase 10 - Focus Encoder

Add `FocusEncoder`.

Required behavior:

- if focus reporting is disabled, write no bytes.
- focused writes `ESC [ I`.
- unfocused writes `ESC [ O`.

## Phase 11 - Tests

Add these before moving to mouse.

Event tests:

- invalid modifier bits rejected.
- `TerminalKeyEvent` rejects neither key nor codepoint.
- `TerminalKeyEvent` rejects both key and codepoint.
- `TerminalKeyEvent` rejects surrogate codepoint.
- `TerminalKeyEvent` rejects values above `U+10FFFF`.

Modifier tests:

- none -> CSI param 1.
- shift -> 2.
- alt -> 3.
- shift+alt -> 4.
- ctrl -> 5.
- meta -> 9.

Printable key tests:

- `a` -> `0x61`.
- `e acute` -> `C3 A9`.
- `U+1F600` -> `F0 9F 98 80`.
- Alt+a -> `ESC 0x61`.
- Ctrl+a -> `0x01`.
- Ctrl+z -> `0x1A`.
- Ctrl+[ -> `0x1B`.
- Ctrl+\ -> `0x1C`.
- Ctrl+] -> `0x1D`.
- Ctrl+^ -> `0x1E`.
- Ctrl+_ -> `0x1F`.
- Ctrl+? -> `0x7F`.
- Ctrl+Alt+a -> `ESC 0x01`.

Special key tests:

- Enter normal -> CR.
- Enter LNM -> CR LF.
- Tab -> HT.
- Shift+Tab -> `ESC [ Z`.
- Backspace -> DEL.
- Escape -> ESC.
- Up normal -> `ESC [ A`.
- Up application -> `ESC O A`.
- Ctrl+Up -> `ESC [ 1 ; 5 A`.
- Shift+Alt+Up -> `ESC [ 1 ; 4 A`.
- Home normal -> `ESC [ H`.
- End normal -> `ESC [ F`.
- Home application -> `ESC O H`.
- End application -> `ESC O F`.
- Insert -> `ESC [ 2 ~`.
- Delete -> `ESC [ 3 ~`.
- PageUp -> `ESC [ 5 ~`.
- PageDown -> `ESC [ 6 ~`.
- Ctrl+Delete -> `ESC [ 3 ; 5 ~`.
- F1 -> `ESC O P`.
- F2 -> `ESC O Q`.
- F3 -> `ESC O R`.
- F4 -> `ESC O S`.
- F5 -> `ESC [ 15 ~`.
- F12 -> `ESC [ 24 ~`.

Keypad tests:

- normal numpad `0..9` -> ASCII `0..9`.
- normal numpad decimal -> `.`.
- normal numpad divide/multiply/subtract/add -> `/`, `*`, `-`, `+`.
- application numpad `0..9` -> `ESC O p..y`.
- application decimal -> `ESC O n`.
- application divide -> `ESC O o`.
- application multiply -> `ESC O j`.
- application subtract -> `ESC O m`.
- application add -> `ESC O k`.
- application enter -> `ESC O M`.

Paste tests:

- plain paste writes UTF-8 text.
- bracketed paste writes `ESC [ 200~ text ESC [ 201~`.
- empty bracketed paste still emits wrappers.

Focus tests:

- focus disabled -> no bytes.
- focus enabled true -> `ESC [ I`.
- focus enabled false -> `ESC [ O`.

## Phase 12 - Deferred Work

Do not implement these in the first input milestone:

- mouse encoding.
- modifyOtherKeys.
- CSI u.
- Kitty Keyboard Protocol.
- configurable backspace policy.
- paste sanitization.
- clipboard policy.
- UI toolkit adapters.

## Correct Implementation Order

1. Add or verify `TerminalHostOutput` in `:terminal-protocol`.
2. Create `:terminal-input` module.
3. Add `TerminalModifiers` and tests.
4. Add `TerminalKey` and `TerminalKeyEvent` and tests.
5. Add `TerminalPasteEvent` and `TerminalFocusEvent`.
6. Add `TerminalInputEncoder`.
7. Add `TerminalSequences`.
8. Add `InputScratchBuffer` and tests.
9. Add `DefaultTerminalInputEncoder` skeleton.
10. Add `KeyboardEncoder` printable handling.
11. Add Ctrl/Alt printable handling.
12. Add unmodified special keys.
13. Add application cursor mode.
14. Add modified CSI special-key encoding.
15. Add application keypad mode.
16. Add `PasteEncoder`.
17. Add `FocusEncoder`.
18. Run the full `:terminal-input` test suite.
19. Add an integration test using real core `TerminalInputState` mode bits.
20. Start `MouseEncoder` in a separate milestone.
