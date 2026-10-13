package com.nungil.core.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class LaptopCheckTest {
    @Test fun theClassifierSayingLaptopMakesItALaptop() =
        assertEquals("laptop", LaptopCheck.label(listOf("laptop" to 0.4f, "computer keyboard" to 0.3f)))

    @Test fun notebookIsALaptopToo() =
        assertEquals("laptop", LaptopCheck.label(listOf("computer keyboard" to 0.2f, "notebook" to 0.35f)))

    @Test fun aKeyboardStaysAKeyboard() {
        assertEquals("keyboard", LaptopCheck.label(listOf("computer keyboard" to 0.6f, "laptop" to 0.2f)))
        assertEquals("keyboard", LaptopCheck.label(listOf("laptop" to 0.05f)))
        assertEquals("keyboard", LaptopCheck.label(emptyList()))
    }
}
