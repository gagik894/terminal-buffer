# Terminal Buffer Project: AI Assistant Directives

## 1. Project Identity & Scope [CRITICAL]
You are assisting in the development of a high-performance, in-memory terminal buffer written in Kotlin/JVM 21.
* **WE ONLY BUILD THE BUFFER.** Do not generate UI code, rendering code, Swing/Compose wrappers, or OS-level PTY/I/O integrations.
* **HEADLESS BY DESIGN.** The engine only understands coordinates, integers, and state transitions.
* **THE PRIME DIRECTIVE:** Zero-allocation on the hot path. CPU cache locality is our highest priority.

## 2. The Data-Oriented Mandate

Must be fast, performant, and cache-friendly. The design is intentionally low-level and flat.
Do NOT use Object-Oriented design patterns for memory storage.
* **Primitives Only:** The grid is stored as flat `IntArray`s (one for codepoints, one for packed attributes).

## 3. The Cell Physics & Invariants (The Grid Contract)
Every cell `i` in the `IntArray` must strictly adhere to one of these states. You must enforce these invariants in all mutation code:
* **`0` (EMPTY):** A blank cell. (We do not use NUL or space sentinels, 0 is the default).
* **`> 0` (CODEPOINT):** A standard 1-cell Unicode character.
* **`-1` (WIDE_SPACER):** A ghost cell. Must strictly follow a width-2 leader.

## 4. The Overwrite Protocol
When mutating the grid (inserting, overwriting, or clearing), you must NEVER leave orphaned spacers or corrupted leaders.
* **Rule 1:** Before writing to `col`, you must invoke the `findClusterStart(col)` logic to determine the canonical owner of the cell.
* **Rule 2 (Annihilation):** If writing to a cell that belongs to a multi-cell cluster or wide character, you must zero-out (`EMPTY`) the entire span of the previous cluster before writing the new data.

## 5. Memory Lifecycle (The Arena Model)
* **No Global State:** Complex strings are not interned globally. They are stored in an `Arena` (or local Pool) tied to a bounded lifecycle (e.g., a block of lines).
* **Garbage Collection:** When lines are evicted from the `HistoryRing`, their associated Arena data must die with them.
* **Explicit Widths:** When storing a complex cluster, its physical grid width must be explicitly stored and queried from the Arena, never inferred implicitly.

## 6. API Surface & Architecture
* **The Coordinator:** `TerminalBuffer` delegates input to `InputHandler`, reflow to `TerminalResizer`, and state to `TerminalState`.
* **The Firewall:** The Buffer API (`moveTo`, `printCodepoint`, `erase`) is a black box. The external Parser/FSM must NEVER directly touch the `IntArray`s.
* **Out-of-Bounds:** `getLine(row)` returns `VoidLine`. `getCodepointAt()` returns `0`. Do not throw IndexOutOfBounds exceptions for valid screen coordinate queries.

## 7. Developer Workflow & Testing
* **Validation First:** If modifying grid mutation logic, write or update tests in `src/test/kotlin/terminal/` first.
* **Gradle:** Use `./gradlew test`.
* **Test Focus:** Rely on `TerminalResizerTest.kt` and buffer behavior tests to catch regression in width calculations, cursor bounding, and history preservation.