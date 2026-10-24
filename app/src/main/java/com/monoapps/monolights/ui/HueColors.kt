package com.monoapps.monolights.ui

import com.monoapps.monolights.api.LightState
import kotlin.math.abs

// The e-ink screen cannot show color, so colors are picked and displayed by name.

/** A named color mapped to a Hue hue value (0-65535 around the color wheel). */
data class NamedColor(val name: String, val hue: Int)

/** A white tone in mireds (153 = cold daylight, 454 = candlelight). */
data class WhitePreset(val name: String, val ct: Int)

val HUE_COLORS = listOf(
    NamedColor("Red", 65535),
    NamedColor("Orange", 6000),
    NamedColor("Yellow", 12750),
    NamedColor("Lime", 19000),
    NamedColor("Green", 25500),
    NamedColor("Teal", 36210),
    NamedColor("Blue", 46920),
    NamedColor("Purple", 52000),
    NamedColor("Magenta", 56100),
    NamedColor("Pink", 60700),
)

val WHITE_PRESETS = listOf(
    WhitePreset("Candle", 454),
    WhitePreset("Warm", 366),
    WhitePreset("Neutral", 250),
    WhitePreset("Cool", 200),
    WhitePreset("Daylight", 153),
)

private fun hueDistance(a: Int, b: Int): Int {
    val d = abs(a - b)
    return minOf(d, 65536 - d)
}

fun nearestColor(hue: Int): NamedColor = HUE_COLORS.minBy { hueDistance(it.hue, hue) }

fun nearestWhite(ct: Int): WhitePreset = WHITE_PRESETS.minBy { abs(it.ct - ct) }

/** Text label for the current color, or null for plain dimmable lights. */
fun colorLabel(state: LightState): String? = when {
    state.colormode == "ct" && state.ct != null -> nearestWhite(state.ct).name
    state.hue != null && (state.colormode == "hs" || state.colormode == "xy") ->
        if ((state.sat ?: 0) < 50) "White" else nearestColor(state.hue).name
    else -> null
}
