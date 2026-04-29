package com.gagik.integration

/**
 * Host-facing safety limits for integration-owned metadata.
 *
 * These limits prevent untrusted terminal output from retaining unbounded host
 * metadata while still allowing normal shell and TUI OSC 8 hyperlink usage.
 *
 * @property maxHyperlinkEntries maximum distinct OSC 8 hyperlink keys retained
 * by the adapter before least-recently-used entries are evicted.
 * @property maxHyperlinkUriLength maximum accepted OSC 8 URI length in UTF-16
 * code units. Longer URIs are ignored and mapped to no active hyperlink.
 * @property maxHyperlinkIdLength maximum accepted OSC 8 `id=` parameter length
 * in UTF-16 code units. Longer IDs are ignored and mapped to no active
 * hyperlink.
 */
data class TerminalHostPolicy(
    val maxHyperlinkEntries: Int = 4096,
    val maxHyperlinkUriLength: Int = 4096,
    val maxHyperlinkIdLength: Int = 256,
) {
    init {
        require(maxHyperlinkEntries > 0) {
            "maxHyperlinkEntries must be positive, got $maxHyperlinkEntries"
        }
        require(maxHyperlinkUriLength >= 0) {
            "maxHyperlinkUriLength must be non-negative, got $maxHyperlinkUriLength"
        }
        require(maxHyperlinkIdLength >= 0) {
            "maxHyperlinkIdLength must be non-negative, got $maxHyperlinkIdLength"
        }
    }
}
