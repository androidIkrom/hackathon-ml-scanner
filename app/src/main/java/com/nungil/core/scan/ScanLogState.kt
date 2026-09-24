package com.nungil.core.scan

/** The on-screen text log of everything the scan said, so judges and helpers can read it. */
class ScanLogState(private val maxLines: Int = MAX_LINES) {
    private val lines = ArrayDeque<String>()

    fun add(line: String) {
        if (line.isBlank()) return
        lines.addLast(line)
        while (lines.size > maxLines) lines.removeFirst()
    }

    fun lines(): List<String> = lines.toList()

    fun text(): String = lines.joinToString("\n")

    fun clear() = lines.clear()

    companion object {
        const val MAX_LINES = 200
    }
}
