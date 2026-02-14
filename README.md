# Terminal Buffer

A high-performance, structured terminal buffer implementation in Kotlin. This project provides a core engine for managing terminal screen state, scrollback history, and character attributes.

## Solution Overview

The solution is built on a modular architecture that separates concerns between storage, cursor management, and visual representation.

### Core Components

1.  **TerminalBuffer**: The main coordinator. It implements `TerminalBufferApi` and orchestrates interactions between the Screen, Cursor, and Pen.
2.  **HistoryRing**: A fixed-capacity circular buffer that stores `Line` objects. It efficiently handles scrollback by recycling old lines once the capacity is reached.
3.  **Screen**: A lightweight "viewport" over the `HistoryRing`. It maps visual row coordinates (0 to height-1) to the actual lines at the end of the history ring.
4.  **Line**: Represents a single row of cells. It uses "packed" storage:
    -   `IntArray` for codepoints (0 = empty).
    -   `IntArray` for attributes (packed via `AttributeCodec`).
    -   `wrapped` flag to indicate soft-wrapping from the previous line.
5.  **Cursor**: Tracks current position and handles automatic wrapping and scrolling triggers.
6.  **Pen**: Maintains the current "active" style (foreground, background, bold, etc.) applied to new character writes.

## Design Decisions & Trade-offs

-   **Memory Efficiency via Primitive Arrays**: Instead of storing objects per cell (which would cause massive GC pressure), we use `IntArray` to store codepoints and packed attributes. This significantly reduces the memory footprint.
-   **Circular Buffer for History**: Using a circular buffer (HistoryRing) ensures that scrolling is an $O(1)$ operation (just updating the head index and recycling a line) rather than $O(N)$.
-   **Separation of Visual and Logical State**: The `Screen` doesn't own data; it only views the end of the `HistoryRing`. This makes operations like "scrolling" naturally integrated into the history management.
-   **Soft Wrap Tracking**: We track soft wraps at the `Line` level. While not fully utilized in the current simple "write" API, this is critical for future features like reflowing text on resize.

## Future Improvements

While the core functionality is robust, there are two major areas identified for future development:

### 1. Wide Character Support (CJK & Emojis)
Some characters occupy 2 cells in a terminal.
**How to implement:**
-   **Width Detection**: Use a utility or `java.text.BreakIterator` to determine if a codepoint is wide (e.g., East Asian Width property).
-   **Continuation Marker**: When writing a wide character, mark the subsequent cell with a special value (e.g., `-1`).
-   **Cursor Advancement**: Update `Cursor.advance()` to increment by 2 for wide characters.
-   **Character Retrieval**: Update `getLineAsString` and `getCharAt` to treat the continuation cell as part of the previous character.

### 2. Terminal Resizing (Reflow Strategy)
Changing the dimensions of the terminal requires reorganizing existing content.
**How to implement:**
-   **Capture Logical Stream**: Iterate through the entire history and screen. Use the `wrapped` flag on `Line` to treat consecutive wrapped lines as a single logical line.
-   **Re-wrap Logic**: Create a temporary buffer with the new width. Re-insert the captured stream of characters into this new buffer.
-   -   **Truncation/Expansion**: If the width increases, characters might move from the start of a next line to the end of the current one. If it decreases, more soft-wraps will be generated.
-   **Cursor Re-mapping**: Calculate the new cursor position based on its relative offset in the logical stream of the current screen.

## Getting Started

### Prerequisites
- JDK 11 or higher
- Gradle

### Running Tests
```bash
./gradlew test
```
