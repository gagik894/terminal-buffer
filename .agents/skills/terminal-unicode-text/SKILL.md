---
name: terminal-unicode-text
description: Unicode/text guidance for this terminal emulator. Use when changing UTF-8 decoding, PrintableProcessor, CharsetMapper, GraphemeAssembler, GraphemeSegmenter, UnicodeClass, generated Unicode tables, grapheme clustering, or core width policy.
---

# Terminal Unicode and Text

Use this skill when changing text ingestion, Unicode classification, grapheme
assembly, charset mapping, or width behavior.

## Ownership

- `TerminalParser` owns UTF-8 decoding and malformed-byte replay.
- Parser text code owns charset mapping and grapheme segmentation.
- Core owns width calculation and ambiguous-width policy.

Do not add a second UTF-8 decoder to `PrintableProcessor`. Do not calculate cell
width in parser.

## Unicode Rules

- Use generated-table-shaped APIs even when seed data is curated.
- Keep hot-path classification primitive and allocation-free.
- Do not use regex, ICU, or `BreakIterator` in hot paths.
- Keep grapheme context clearing complete after cluster flush.

## Test Checklist

Cover:

- ASCII fast path.
- valid 2-, 3-, and 4-byte UTF-8.
- malformed UTF-8 followed by ASCII and ESC.
- combining marks.
- variation selectors.
- ZWJ emoji sequences.
- regional indicator pairs and triples.
- Hangul Jamo sequences.
- DEC Special Graphics and active/inactive GL slots.
- SS2/SS3 single shifts for one character only.
- chunk boundaries.

The key hostile case: malformed UTF-8 followed by ESC must emit U+FFFD and then
route ESC structurally, not print ESC as text.
