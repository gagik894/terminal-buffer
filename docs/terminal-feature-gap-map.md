# Terminal Feature Gap Map

This is the living backlog for turning the current terminal parser/core into a
professional terminal emulator pipeline.

The intent is to make gaps explicit. If a feature is missing, unsupported, or
only partially modeled, it should be listed here rather than hidden behind a
silent no-op.

The target is not literal full xterm parity. The target is a modern, secure,
xterm-compatible-enough terminal pipeline for contemporary shells and TUIs,
with obsolete or risky legacy protocols excluded unless they earn their place.

Status labels:

- `TODO(parser)`: byte/protocol recognition or semantic dispatch is missing.
- `TODO(core)`: terminal state, grid physics, pen storage, or public API is missing.
- `TODO(integration)`: parser and core both have enough shape, but the bridge is incomplete.
- `TODO(input)`: host-bound keyboard/mouse/paste encoding is missing.
- `TODO(policy)`: feature needs an explicit security or compatibility policy before implementation.

## Product Target

### Tier 1: Required

These are required for a modern terminal pipeline that can run contemporary
shells and TUIs well.

- UTF-8 decoding, Unicode grapheme segmentation, and core-owned width policy.
- CSI cursor movement, editing, erasing, scrolling, and mode toggles.
- SGR 16-color, 256-color, RGB/truecolor, and common text attributes.
- OSC title updates and OSC 8 hyperlinks.
- Bracketed paste mode.
- SGR mouse protocol.
- Primary/alternate screen switching.
- Terminal input encoding for keyboard, keypad, mouse, focus, and paste.

### Tier 2: Useful

These unlock richer app compatibility and better shell integration, but should
remain policy-gated where they can produce terminal-to-host responses.

- DSR, CPR, DA, DA2, and DA3 with a safe response policy.
- OSC palette queries and updates.
- DECRQSS and XTGETTCAP with an allowlist.
- Generated Unicode grapheme and width tables.
- Shell integration markers, including OSC 133 and common notification markers.
- Xterm title stack push/pop.
- Window/grid size queries.

### Tier 3: Optional

These are valuable for certain apps, but are not required for the first modern
pipeline milestone.

- Sixel or a modern graphics protocol.
- Richer hyperlink, title, palette, and notification host callbacks.
- Synchronized output mode.

## Intentional Non-Goals

These are not badges of compatibility for this project. They expand attack
surface or maintenance cost without meaningful modern terminal value.

- Tektronix 4014 emulation.
- Media Copy / printer passthrough (`CSI i`).
- X11-specific font loading protocols.
- Blind OSC 52 clipboard writes.
- Unbounded or unaudited DCS/OSC responses.
- Literal "everything xterm ever accepted" parity.

## Parser Gaps

### CSI Protocols

- `DONE(parser/integration)`: `DECSLRM` left/right margins, usually
  `CSI Pl ; Pr s`, route through parser and integration to core margins.
- `DONE(parser/integration)`: selective erase dispatch routes to core:
  - `DECSEL`, selective erase in line.
  - `DECSED`, selective erase in display.
- `DONE(parser/integration)`: `DECSCA` selective-erase protection routes to
  core protected-cell pen state.
- `DONE(parser/integration)`: `RIS`, `ESC c`, full terminal reset routes to
  `TerminalBufferApi.reset`.
- `TODO(parser)`: full DEC private mode vocabulary and tests beyond the current common set:
  - alternate-screen variants `47`, `1047`, `1048`, `1049`
  - cursor blink mode
  - mouse UTF-8 and URXVT encodings
  - synchronized output mode `?2026`
  - focus, paste, mouse, and application-mode interactions
- `TODO(parser/core)`: xterm title stack:
  - `CSI 22 t` push window/icon title
  - `CSI 23 t` pop window/icon title
  Shells use this when temporarily changing titles for foreground commands.
- `TODO(parser/core)`: window/grid size reports:
  - `CSI 14 t`, report window size in pixels
  - `CSI 18 t`, report terminal size in characters
  Requires terminal-to-host response plumbing and renderer/host size knowledge.
- `TODO(policy)`: xterm window manipulation:
  - `CSI 3 t`, move window
  - `CSI 8 t`, resize window
  - minimize/maximize/raise/lower variants
  Many modern terminals ignore or gate these to prevent hostile scripts from
  controlling the user's window.
- `TODO(parser)`: DEC alignment test `DECALN`, `ESC # 8`.
- `DONE(parser/core/integration)`: terminal-to-host response channel and safe
  baseline responses for:
  - `DSR 5`, operating status, responding `CSI 0 n`
  - `CPR` / `DSR 6`, cursor position reports
  - primary `DA`, using a conservative VT100-compatible identity
  - secondary `DA2`, using a generic versionless identity
  - `DA3` is parsed but intentionally silent to avoid exposing a stable unit id
    without policy.
- `TODO(parser/core)`: broader DEC-specific status reports beyond the safe
  DSR/CPR/DA baseline.
- `TODO(parser)`: character attribute/protection commands not covered by SGR:
  - `DECSCUSR`, cursor style
  - `DECSACE`
- `TODO(parser)`: full tab-stop and margin variants beyond the current common set.
- `TODO(parser)`: rectangular area operations:
  - `DECCRA`
  - `DECERA`
  - `DECFRA`
  - `DECSERA`
  - `DECSACE`
- `TODO(parser)`: insert/delete/erase variants with selective protection and rectangular bounds.
- `TODO(parser)`: scroll variants and xterm extensions not yet routed:
  - `DECSTBM` is present
  - `SU` / `SD` are present
  - left/right-margin-aware variants need broader integration tests
- `DONE(parser/core/integration)`: erase saved lines / scrollback clear,
  `CSI 3 J`, is currently routed through ED mode 3 to core scrollback clearing.
  Keep this covered with regression tests because shells and clear-screen
  shortcuts rely on it.

### ESC Protocols

- `DONE(parser/integration)`: full reset `ESC c`.
- `TODO(parser)`: DEC alignment test `ESC # 8`.
- `TODO(parser)`: complete charset designation:
  - ISO 2022 G0-G3 single-byte sets beyond ASCII and DEC Special Graphics
  - UK, US, Dutch, Finnish, French, German, Italian, Norwegian/Danish, Spanish,
    Swedish, Swiss, Portuguese
  - line drawing aliases used by common terminal descriptions
- `TODO(parser)`: 8-bit C1 equivalents for ESC-prefixed controls if raw C1 mode
  is supported later.
- `TODO(parser)`: save/restore state parity between DEC and SCO cursor save forms,
  if compatibility requires it.

### OSC Protocols

Implemented low-risk baseline:

- OSC 0, 1, 2 title variants
- OSC 8 hyperlinks
- OSC 52 is intentionally ignored

Missing:

- `TODO(policy)`: OSC 52 clipboard support. This needs an explicit permission
  and security policy before implementation.
- `TODO(parser)`: OSC 4 / 10 / 11 / 12 color palette queries and updates.
- `TODO(parser)`: OSC 7 current directory.
- `TODO(parser)`: OSC 9 / 9;9 desktop notifications, if desired.
- `TODO(parser)`: OSC 777 desktop notifications. Common in shell integrations
  and long-running command completion hooks.
- `TODO(parser)`: OSC 133 shell integration markers.
- `TODO(parser)`: OSC 1337/iTerm2 extensions, if desired.
- `TODO(parser)`: OSC query responses. Requires terminal-to-host output.
- `TODO(parser)`: payload encoding policy for non-UTF-8 or invalid UTF-8 OSC data.

### DCS Protocols

Current state:

- DCS payload is bounded and terminated correctly.
- Milestone behavior discards payload.

Missing:

- `TODO(parser)`: DCS dispatch policy and command router.
- `TODO(parser)`: XTGETTCAP / XTSETTCAP terminal capability query/response.
- `TODO(parser)`: DECRQSS request status string.
- `TODO(parser)`: Sixel graphics, if the emulator will support inline graphics.
- `TODO(parser)`: Kitty graphics protocol, commonly sent as `ESC _ G ... ESC \`.
  This is more relevant to modern TUIs than many legacy graphics protocols.
- `TODO(parser)`: ReGIS / DEC vector graphics, likely out of scope unless a DEC
  compatibility mode is a goal.
- `TODO(policy)`: any DCS that can exfiltrate host capabilities needs a response
  policy and terminal-to-host channel.

### Text and Unicode

- `TODO(parser)`: replace curated seed grapheme tables with generated Unicode
  data from UAX #29.
- `TODO(parser)`: full Grapheme_Cluster_Break table coverage.
- `TODO(parser)`: full Extended_Pictographic table coverage.
- `TODO(parser)`: versioned Unicode table generation and tests.
- `TODO(parser)`: malformed UTF-8 policy tests across all structural boundary
  bytes, not only the current representative hostile cases.
- `TODO(parser)`: configurable replacement policy if needed by host applications.
- `TODO(parser)`: broader ISO 2022 charset mapping.

## Core Gaps

### Pen and Attributes

Current core attributes store default/indexed/RGB foreground/background, bold,
italic, underline, inverse/reverse-video, and selective-erase protection.

Missing:

- `DONE(core)`: 256-color indexed foreground/background storage.
- `DONE(core)`: RGB/truecolor foreground/background storage.
- `DONE(core)`: inverse/reverse-video cell attribute.
- `TODO(core)`: faint/dim intensity.
- `TODO(core)`: blink attribute.
- `TODO(core)`: conceal/hidden attribute.
- `TODO(core)`: strikethrough attribute.
- `TODO(core)`: underline style beyond boolean underline:
  - none
  - single
  - double
  - curly
  - dotted
  - dashed
  - underline color, if supported
- `TODO(core)`: SGR overline.
- `TODO(core)`: palette model for default, ANSI 16, 256-color, and RGB colors.
- `TODO(core)`: renderer-facing effective color calculation with reverse video,
  bold-as-bright policy, and default color policy.

### Reset and Mode Semantics

- `TODO(core)`: DECSTR soft reset API.
  Do not fake this with full `reset`, because DECSTR is less destructive than RIS.
- `TODO(core)`: distinguish alternate-screen variants:
  - `47`
  - `1047`
  - `1048`
  - `1049`
  Current core exposes only 1049-style enter/exit behavior.
- `TODO(core)`: cursor style state for `DECSCUSR`.
- `TODO(core)`: cursor blink is currently durable mode state; renderer contract
  may need a richer cursor presentation snapshot.
- `TODO(core)`: synchronized output mode state (`?2026`) if renderer integration
  needs batching semantics.
- `TODO(core)`: bell hook or event channel.
- `TODO(core)`: title/icon/hyperlink storage policy if those should belong to
  core rather than the integration adapter.

### Grid Operations

- `TODO(core)`: rectangular area operations if parser support is added:
  - copy rectangle
  - erase rectangle
  - fill rectangle
  - selective erase rectangle
- `TODO(core)`: left/right margin interactions need continued property testing
  across all edit/erase/scroll operations.
- `TODO(core)`: full DEC protection behavior across all rectangular operations.
- `TODO(core)`: scrollback policy under alternate-screen and private-mode
  combinations beyond current tested cases.
- `TODO(core)`: soft-wrap metadata compatibility with copy/paste/export.

### Unicode Width

- `TODO(core)`: generated width tables from current Unicode data:
  - EastAsianWidth
  - emoji presentation
  - zero-width and combining ranges
  - ambiguous-width policy
- `TODO(core)`: width policy for emoji ZWJ clusters and variation-selector
  presentation should be explicit and versioned.
- `TODO(core)`: configurable ambiguous-width policy is present, but table coverage
  should be generated and audited.
- `TODO(core)`: invalid/unassigned codepoint width policy.

### Query and Response Channel

- `DONE(core)`: terminal-to-host response queue and safe response generation for:
  - DSR/CPR
  - primary DA and secondary DA2
- `TODO(policy)`: DA3 terminal unit id behavior.
- `TODO(core)`: terminal-to-host response/event API for:
  - XTGETTCAP
  - OSC queries
  - mouse reports
  - focus reports
  - bracketed paste boundaries
- `TODO(core)`: event API for bell, title changes, hyperlinks, palette updates,
  and terminal notifications if these move out of integration.

## Integration Gaps

- `DONE(integration)`: parser SGR inverse, 256-color indexed, and RGB/truecolor
  attributes are mapped to core pen attributes without clamping.
- `TODO(integration)`: map faint/blink/conceal/strikethrough only after core
  exposes those attributes.
- `TODO(integration)`: map DECSTR only after core exposes a soft-reset API.
- `TODO(integration)`: map alternate-screen `47` and `1047` only after core
  exposes their exact semantics.
- `DONE(integration)`: parser RIS maps to core `reset`.
- `DONE(integration)`: parser DECSLRM maps to core left/right margins.
- `DONE(integration)`: parser DECSEL/DECSED/DECSCA map to core selective erase
  and protection commands.
- `TODO(integration)`: decide whether OSC title/hyperlink state belongs in core,
  integration metadata, or a host callback interface.
- `TODO(integration)`: add a host callback/event sink for bell, title, hyperlink,
  palette, mouse reports, and clipboard policy. Device responses currently use
  the core response queue.

## Input Module Gaps

There is no production `:terminal-input` module yet.

Missing:

- `TODO(input)`: keyboard encoding for normal and application cursor-key modes.
- `TODO(input)`: keypad encoding for numeric and application keypad modes.
- `TODO(input)`: modifier encoding:
  - xterm modifyOtherKeys
  - CSI u
  - legacy modifier encodings
- `TODO(input)`: Kitty Keyboard Protocol. This is becoming a modern standard for
  disambiguating keys that legacy encodings collapse, such as Shift+Enter versus
  Enter or Ctrl+I versus Tab.
- `TODO(input)`: mouse report encoding:
  - X10
  - normal tracking
  - button-event tracking
  - any-event tracking
  - SGR encoding
  - UTF-8 and URXVT encodings if supported
- `TODO(input)`: focus in/out reports.
- `TODO(input)`: bracketed paste wrapping.
- `TODO(input)`: paste sanitization policy.
- `TODO(input)`: terminal-to-host response queue integration for keyboard,
  mouse, focus, and paste reports, shared with parser/core query responses.

## Rendering and Host Integration Gaps

Rendering is intentionally outside the current core/parser modules, but a
professional emulator needs explicit contracts for it.

- `TODO(host)`: renderer API for cell attributes, cursor shape, cursor blink,
  reverse video, selection, hyperlinks, and dirty regions.
- `TODO(host)`: font measurement policy and fallback fonts.
- `TODO(host)`: double-width glyph display, emoji presentation, and ambiguous
  width presentation must match core width decisions.
- `TODO(host)`: text selection and clipboard integration.
- `TODO(host)`: scrollback viewport separate from active cursor viewport.
- `TODO(host)`: accessibility/export APIs.
- `TODO(host)`: performance benchmarks for large scrollback, resize, and dense
  SGR streams.

## Security and Policy Gaps

- `TODO(policy)`: OSC 52 clipboard permission model.
- `TODO(policy)`: DCS/OSC query response allowlist.
- `TODO(policy)`: hyperlink validation and display policy.
- `TODO(policy)`: maximum payload sizes per protocol family.
- `TODO(policy)`: whether title/icon updates are always accepted or host-gated.
- `TODO(policy)`: paste sanitization and bracketed paste defaults.
- `TODO(policy)`: terminal capability identity. Claiming xterm compatibility
  requires implementing enough behavior to make that claim true.
- `TODO(policy)`: window manipulation allow/deny behavior for xterm window ops.
- `TODO(policy)`: desktop notification allow/deny behavior for OSC 777 and
  related notification protocols.

## Recommended Next Order

3. `TODO(parser/core)`: add xterm title stack and safe window/grid size reports.
4. `TODO(parser)`: add DCS router with a strict response/security policy.
