package org.rtkcollector.app.ui

import android.R as AndroidResources
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.rtkcollector.app.testing.TestFiles
import org.w3c.dom.Element
import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.pow

class StopConfirmationPresentationTest {
    private val android = "http://schemas.android.com/apk/res/android"

    @Test fun `filled actions avoid bottom baseline clipping and retain stacked end alignment`() {
        val styles = document("values/styles.xml").getElementsByTagName("style")
        for (name in listOf("StopConfirmationPositiveButton", "StopConfirmationCancelButton")) {
            val style = (0 until styles.length).map { styles.item(it) as Element }
                .single { it.getAttribute("name") == name }
            val items = style.getElementsByTagName("item")
            val gravity = (0 until items.length).map { items.item(it) as Element }
                .singleOrNull { it.getAttribute("name") == "android:layout_gravity" }
                ?.textContent.orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            assertEquals(setOf("end", "center_vertical"), gravity,
                "$name must avoid the native bottom-baseline margin adjustment without losing stacked END alignment")
        }
    }

    @Test fun `filled action buttons reserve a visible gap in horizontal and stacked layouts`() {
        val styles = document("values/styles.xml").getElementsByTagName("style")
        for (name in listOf("StopConfirmationPositiveButton", "StopConfirmationCancelButton")) {
            val style = (0 until styles.length).map { styles.item(it) as Element }
                .single { it.getAttribute("name") == name }
            val items = style.getElementsByTagName("item")
            val margin = (0 until items.length).map { items.item(it) as Element }
                .firstOrNull { it.getAttribute("name") == "android:layout_margin" }
            assertNotNull(margin, "$name must not rely on platform spacing between filled backgrounds")
            assertEquals("6dp", requireNotNull(margin).textContent.trim())
        }
    }

    @Test fun `dialog styles only reference public framework attributes`() {
        val items = document("values/styles.xml").getElementsByTagName("item")
        val attributes = AndroidResources.attr::class.java.fields.map { it.name }.toSet()
        for (index in 0 until items.length) {
            val name = (items.item(index) as Element).getAttribute("name")
            if (name.startsWith("android:")) {
                assertTrue(name.removePrefix("android:") in attributes,
                    "Unsupported public Android style attribute: $name")
            }
        }
    }

    @Test fun `dialog theme binds filled action styles`() {
        val styles = document("values/styles.xml").getElementsByTagName("style")
        val theme = (0 until styles.length).map { styles.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == "StopConfirmationDialogTheme" }
        assertNotNull(theme, "Stop confirmation must have an explicit presentation theme")
        val items = requireNotNull(theme).getElementsByTagName("item")
        val values = (0 until items.length).associate {
            val item = items.item(it) as Element
            item.getAttribute("name") to item.textContent.trim()
        }
        assertEquals("@style/StopConfirmationPositiveButton", values["android:buttonBarPositiveButtonStyle"])
        assertEquals("@style/StopConfirmationCancelButton", values["android:buttonBarNegativeButtonStyle"])
    }

    @Test fun `confirmation has visible colored and disabled fills with readable text`() {
        val background = document("drawable/stop_confirmation_positive.xml")
        val states = background.getElementsByTagName("item")
        val disabled = (0 until states.length).map { states.item(it) as Element }
            .single { it.getAttributeNS(android, "state_enabled") == "false" }
        val enabled = states.item(states.length - 1) as Element
        val disabledColor = fill(disabled)
        val enabledColor = fill(enabled)
        assertNotEquals(disabledColor, enabledColor)
        assertTrue(enabledColor.toSet().size > 1, "Enabled confirmation must be colored, not neutral gray")
        val text = document("color/stop_confirmation_text.xml").getElementsByTagName("item")
        val disabledText = (0 until text.length).map { text.item(it) as Element }
            .single { it.getAttributeNS(android, "state_enabled") == "false" }
        assertTrue(contrast(disabledColor, color(disabledText.getAttributeNS(android, "color"))) >= 4.5)
        assertTrue(contrast(enabledColor, color((text.item(text.length - 1) as Element)
            .getAttributeNS(android, "color"))) >= 4.5)
    }

    @Test fun `cancel has a visible neutral gray background`() {
        val fill = fill(document("drawable/stop_confirmation_cancel.xml").documentElement)
        assertEquals(1, fill.toSet().size)
        assertTrue(fill[0] in 32..239, "Cancel must remain visibly filled, not transparent or white")
        assertTrue(contrast(fill, color("#202124")) >= 4.5)
    }

    private fun document(path: String): org.w3c.dom.Document {
        val file = TestFiles.locateProjectPath("src/main/res/values/styles.xml").parent.parent.resolve(path)
        assertTrue(Files.isRegularFile(file), "Missing Stop confirmation resource: $path")
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file.toFile())
    }

    private fun fill(element: Element) = color((element.getElementsByTagName("solid").item(0) as Element)
        .getAttributeNS(android, "color"))

    private fun color(hex: String): List<Int> {
        assertTrue(hex.matches(Regex("#[0-9A-Fa-f]{6}")), "Fill must be explicitly opaque: $hex")
        return hex.drop(1).chunked(2).map { it.toInt(16) }
    }

    private fun contrast(a: List<Int>, b: List<Int>): Double {
        fun luminance(rgb: List<Int>): Double = rgb.map { value ->
            val channel = value / 255.0
            if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        }.let { it[0] * 0.2126 + it[1] * 0.7152 + it[2] * 0.0722 }
        val first = luminance(a); val second = luminance(b)
        return (maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)
    }
}
