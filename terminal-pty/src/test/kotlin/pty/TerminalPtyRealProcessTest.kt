package com.gagik.terminal.pty

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class TerminalPtyRealProcessTest {
    @Test
    fun `real PTY process output reaches terminal core`() {
        assumeTrue(
            System.getProperty("terminal.pty.integration") == "true",
            "Set -Dterminal.pty.integration=true to run native PTY smoke tests",
        )

        val session = TerminalPtySessions.start(
            TerminalPtyOptions(
                command = smokeCommand(),
                workingDirectory = Path.of(System.getProperty("user.home")),
                columns = 40,
                rows = 5,
                maxHistory = 10,
            ),
        )

        val exitCode = session.waitFor()
        session.joinReader(2000)
        session.close()

        assertTrue(exitCode == 0, "expected smoke process to exit successfully")
        assertTrue(
            session.terminal.getAllAsString().contains("PTY_READY"),
            "expected PTY_READY to be parsed into terminal core",
        )
    }

    private fun smokeCommand(): List<String> {
        val osName = System.getProperty("os.name").lowercase()
        return if (osName.contains("windows")) {
            listOf("cmd.exe", "/c", "echo PTY_READY")
        } else {
            listOf("/bin/sh", "-lc", "printf PTY_READY")
        }
    }
}
