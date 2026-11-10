package com.flowfolio.core.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Quiet Library tokens (ADR-0052) against WCAG 2.1: body text (ink, ink 2) and the accent's ink on the accent at
 * AA for normal text (4.5:1) in every theme, light and dark; the third ink, the accent as a control colour and the
 * danger colour at the 3:1 that AA asks of large text and interface parts; page text at AAA (7:1).
 */
class TokenContrastTest {
    private val themes = mapOf(
        "FlowFolio light" to LightTokens,
        "FlowFolio dark" to DarkTokens,
        "Rustic light" to RusticLightTokens,
        "Rustic dark" to RusticDarkTokens,
        "AMOLED" to AmoledTokens,
        "Material light (no wallpaper colours)" to MaterialLightTokens,
        "Material dark (no wallpaper colours)" to MaterialDarkTokens
    )

    @Test
    fun bodyTextIsAaOnEveryBackground() = check(AA) { t ->
        listOf(
            "ink on bg" to (t.ink to t.bg),
            "ink on surface" to (t.ink to t.surface),
            "ink on surface 2" to (t.ink to t.surface2),
            "ink 2 on bg" to (t.ink2 to t.bg),
            "ink 2 on surface" to (t.ink2 to t.surface)
        )
    }

    @Test
    fun primaryButtonTextIsAa() = check(AA) { t -> listOf("accent ink on accent" to (t.accentInk to t.accent)) }

    @Test
    fun snackbarTextIsAa() = check(AA) { t -> listOf("bg on ink (snackbar)" to (t.bg to t.ink)) }

    @Test
    fun interfacePartsAndMetaTextReachThree() = check(LARGE) { t ->
        listOf(
            "ink 3 on bg" to (t.ink3 to t.bg),
            "ink 3 on surface" to (t.ink3 to t.surface),
            "accent on surface" to (t.accent to t.surface),
            "accent on bg" to (t.accent to t.bg),
            "accent on soft accent" to (t.accent to t.accentSoft),
            "danger on surface" to (t.danger to t.surface),
            "danger on soft danger" to (t.danger to t.dangerSoft),
            "ok on surface" to (t.ok to t.surface)
        )
    }

    @Test
    fun pageTextIsAaa() = check(AAA) { t -> listOf("page ink on paper" to (t.paperInk to t.paper)) }

    @Test
    fun everyThemeHasOpaqueTokens() {
        themes.forEach { (name, t) ->
            listOf(t.bg, t.surface, t.ink, t.accent, t.paper).forEach {
                assertTrue("$name has a translucent token", it.alpha == 1f)
            }
        }
    }

    private fun check(minimum: Double, pairs: (Tokens) -> List<Pair<String, Pair<Color, Color>>>) {
        val failures = themes.flatMap { (name, t) ->
            pairs(t).mapNotNull { (what, colours) ->
                val ratio = contrast(colours.first, colours.second)
                if (ratio < minimum) "$name: $what is ${"%.2f".format(ratio)}:1" else null
            }
        }
        assertTrue("Below ${"%.1f".format(minimum)}:1 -\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private companion object {
        const val AA = 4.5
        const val LARGE = 3.0
        const val AAA = 7.0
    }
}
