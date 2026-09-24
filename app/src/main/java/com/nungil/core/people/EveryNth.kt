package com.nungil.core.people

/** true on the 1st, (n+1)th, (2n+1)th … call: "run face recognition every 3rd frame that has a person box". */
class EveryNth(private val n: Int) {
    private var count = 0

    init {
        require(n >= 1) { "n must be at least 1, was $n" }
    }

    fun take(): Boolean = (count++ % n) == 0
}
