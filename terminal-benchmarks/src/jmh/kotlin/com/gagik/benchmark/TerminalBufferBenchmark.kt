package com.gagik.benchmark

import com.gagik.core.buffer.TerminalBuffer
import com.gagik.terminal.render.api.TerminalRenderClusterSink
import com.gagik.terminal.render.api.TerminalRenderFrameReader
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class TerminalBufferBenchmark {

    @Param("80", "120", "240")
    var width: Int = 0

    @Param("24", "40", "80")
    var height: Int = 0

    private lateinit var buffer: TerminalBuffer
    private lateinit var reader: TerminalRenderFrameReader
    private lateinit var codeWords: IntArray
    private lateinit var attrWords: LongArray
    private lateinit var flags: IntArray

    @Setup
    open fun setup() {
        buffer = TerminalBuffer(width, height)
        reader = buffer as TerminalRenderFrameReader
        codeWords = IntArray(width)
        attrWords = LongArray(width)
        flags = IntArray(width)
        
        // Fill with ASCII
        for (row in 0 until height) {
            buffer.positionCursor(0, row)
            for (col in 0 until width) {
                buffer.writeCodepoint('A'.code)
            }
        }
    }

    @Benchmark
    open fun copyFullFrameAscii() {
        reader.readRenderFrame { frame ->
            for (row in 0 until height) {
                frame.copyLine(row, codeWords, 0, attrWords, 0, flags, 0)
            }
        }
    }

    @Benchmark
    open fun copySingleRowAscii() {
        reader.readRenderFrame { frame ->
            frame.copyLine(0, codeWords, 0, attrWords, 0, flags, 0)
        }
    }
}

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class TerminalBufferClusterBenchmark {

    @Param("0", "1", "100")
    var clusterPercent: Int = 0

    private val width = 80
    private val height = 24
    private lateinit var buffer: TerminalBuffer
    private lateinit var reader: TerminalRenderFrameReader
    private lateinit var codeWords: IntArray
    private lateinit var attrWords: LongArray
    private lateinit var flags: IntArray
    private val clusterSink = TerminalRenderClusterSink { _, _ -> }

    @Setup
    open fun setup() {
        buffer = TerminalBuffer(width, height)
        reader = buffer as TerminalRenderFrameReader
        codeWords = IntArray(width)
        attrWords = LongArray(width)
        flags = IntArray(width)
        
        for (row in 0 until height) {
            buffer.positionCursor(0, row)
            for (col in 0 until width) {
                if (clusterPercent > 0 && (row * width + col) % (100 / clusterPercent.coerceAtLeast(1)) == 0) {
                    buffer.writeCluster(intArrayOf('e'.code, 0x0301))
                } else {
                    buffer.writeCodepoint('A'.code)
                }
            }
        }
    }

    @Benchmark
    open fun copyFullFrameWithClusters() {
        reader.readRenderFrame { frame ->
            for (row in 0 until height) {
                frame.copyLine(
                    row = row,
                    codeWords = codeWords,
                    codeOffset = 0,
                    attrWords = attrWords,
                    attrOffset = 0,
                    flags = flags,
                    flagOffset = 0,
                    clusterSink = clusterSink
                )
            }
        }
    }
}
