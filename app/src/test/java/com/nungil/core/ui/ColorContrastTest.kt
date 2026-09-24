package com.nungil.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads the real colour resources (unit tests run with the app module as working directory) and checks
 * every token pair the design system promises. Changing a colour so that it fails WCAG fails the build.
 */
class ColorContrastTest {

    private fun colours(path: String): Map<String, Int> {
        val file = File(path)
        assertTrue("missing ${file.absolutePath}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("color")
        return (0 until nodes.length).associate { i ->
            val e = nodes.item(i) as Element
            e.getAttribute("name") to Contrast.parseHex(e.textContent.trim())
        }
    }

    private val light = colours("src/main/res-i/values/colors.xml")
    private val dark = colours("src/main/res-i/values-night/colors.xml")

    /** Palette with the "ng_" (or "ng_hc_") prefix removed, e.g. "text", "bg". */
    private fun palette(all: Map<String, Int>, prefix: String): Map<String, Int> =
        all.filterKeys { it.startsWith(prefix) && (prefix == "ng_hc_" || !it.startsWith("ng_hc_")) }
            .mapKeys { it.key.removePrefix(prefix) }

    private fun check(name: String, p: Map<String, Int>) {
        val failures = mutableListOf<String>()
        fun need(fg: String, bg: String, min: Double) {
            val a = p[fg] ?: error("$name palette has no $fg")
            val b = p[bg] ?: error("$name palette has no $bg")
            val r = Contrast.ratio(a, b)
            if (r < min) failures += "$name: $fg on $bg is ${"%.2f".format(r)} (needs $min)"
        }
        for (bg in listOf("bg", "card")) {
            need("text", bg, Contrast.TEXT_MIN)
            need("text_sub", bg, Contrast.TEXT_MIN)
            need("accent_text", bg, Contrast.TEXT_MIN)
            need("success", bg, Contrast.TEXT_MIN)
        }
        need("text", "primary_soft", Contrast.TEXT_MIN)
        need("on_primary", "primary", Contrast.TEXT_MIN)
        need("on_danger", "danger", Contrast.TEXT_MIN)
        need("primary", "bg", Contrast.NON_TEXT_MIN)
        need("danger", "bg", Contrast.NON_TEXT_MIN)
        need("focus", "bg", Contrast.NON_TEXT_MIN)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun lightPalettePasses() = check("light", palette(light, "ng_"))

    @Test fun darkPalettePasses() = check("dark", palette(dark, "ng_"))

    @Test fun highContrastPalettePasses() = check("high contrast", palette(light, "ng_hc_"))

    @Test fun highContrastTextIsAtLeastSeven() {
        val hc = palette(light, "ng_hc_")
        assertTrue(Contrast.ratio(hc.getValue("text"), hc.getValue("bg")) >= 7.0)
        assertTrue(Contrast.ratio(hc.getValue("on_primary"), hc.getValue("primary")) >= 7.0)
    }
}
