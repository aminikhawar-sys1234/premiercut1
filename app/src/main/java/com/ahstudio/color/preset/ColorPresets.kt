package com.ahstudio.color.preset

import com.ahstudio.color.core.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** §22: presets are PARAMETER CONFIGURATIONS, never baked frames. */
object ColorPresets {

    fun cinematic() = ColorState(
        contrast = 0.18f, contrastPivot = 0.42f,
        highlights = -0.30f, shadows = 0.18f, blacks = 0.10f,
        saturation = -0.08f, vibrance = 0.22f,
        curves = CurveSet(master = listOf(
            CurvePoint(0f, 0.03f), CurvePoint(0.5f, 0.5f), CurvePoint(1f, 0.96f))),
        splitTone = SplitToneState(
            shadowHue = 210f / 360f, shadowSat = 0.16f,
            highlightHue = 40f / 360f, highlightSat = 0.08f, strength = 0.6f)
    )

    fun tealOrange() = ColorState(
        contrast = 0.15f, shadows = 0.10f, highlights = -0.15f, vibrance = 0.15f,
        splitTone = SplitToneState(
            shadowHue = 195f / 360f, shadowSat = 0.30f,
            highlightHue = 35f / 360f, highlightSat = 0.22f,
            balance = 0.1f, strength = 0.8f),
        hsl = HslBands(
            oranges = HslAdjust(sat = 0.12f, lum = 0.05f),
            aquas = HslAdjust(sat = 0.15f))
    )

    fun warm() = ColorState(temperature = 0.30f, vibrance = 0.08f, highlights = -0.05f)
    fun cool() = ColorState(temperature = -0.30f, shadows = 0.05f)

    fun vintage() = ColorState(
        contrast = -0.10f, saturation = -0.25f, vibrance = 0.10f,
        blacks = 0.18f, highlights = -0.10f,
        curves = CurveSet(master = listOf(
            CurvePoint(0f, 0.12f), CurvePoint(0.35f, 0.38f),
            CurvePoint(0.75f, 0.74f), CurvePoint(1f, 0.92f))),
        splitTone = SplitToneState(
            shadowHue = 45f / 360f, shadowSat = 0.14f,
            highlightHue = 200f / 360f, highlightSat = 0.10f, strength = 0.5f)
    )

    fun film() = ColorState(
        contrast = 0.10f, contrastPivot = 0.40f,
        highlights = -0.25f, shadows = 0.22f, whites = -0.10f, blacks = 0.08f,
        saturation = -0.12f, vibrance = 0.18f, skinProtect = 0.7f,
        hsl = HslBands(
            greens = HslAdjust(hueShift = 0.02f, sat = -0.15f, lum = 0.05f),
            blues = HslAdjust(hueShift = -0.015f, sat = -0.08f)),
        curves = CurveSet(
            master = listOf(CurvePoint(0f, 0.02f), CurvePoint(0.25f, 0.24f),
                CurvePoint(0.75f, 0.78f), CurvePoint(1f, 0.97f)),
            blue = listOf(CurvePoint(0f, 0.06f), CurvePoint(1f, 0.94f)))
    )

    fun highContrast() = ColorState(contrast = 0.45f, blacks = -0.15f, whites = 0.15f)
    fun matte() = ColorState(contrast = -0.18f, blacks = 0.22f, whites = -0.18f, saturation = -0.06f)

    fun blackAndWhite() = ColorState(
        bw = BlackWhiteState(enabled = true, weightR = 0.30f, weightG = 0.59f,
            weightB = 0.11f, strength = 1f)
    )

    fun pastel() = ColorState(
        contrast = -0.12f, saturation = -0.10f, vibrance = 0.25f,
        highlights = 0.10f, shadows = 0.12f,
        splitTone = SplitToneState(
            shadowHue = 320f / 360f, shadowSat = 0.10f,
            highlightHue = 50f / 360f, highlightSat = 0.12f, strength = 0.45f)
    )

    fun builtIn(): Map<String, () -> ColorState> = linkedMapOf(
        "Cinematic" to ::cinematic, "Teal & Orange" to ::tealOrange,
        "Warm" to ::warm, "Cool" to ::cool, "Vintage" to ::vintage,
        "Film" to ::film, "High Contrast" to ::highContrast, "Matte" to ::matte,
        "Black & White" to ::blackAndWhite, "Pastel" to ::pastel
    )
}

/** §56: user preset = named ColorState only. No media references. */
@Serializable
data class UserColorPreset(val name: String, val state: ColorState)

class UserPresetStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    init { dir.mkdirs() }

    fun save(preset: UserColorPreset): Boolean = try {
        fileFor(preset.name).writeText(json.encodeToString(preset)); true
    } catch (t: Throwable) { false }

    fun loadAll(): List<UserColorPreset> =
        dir.listFiles { f -> f.extension == "ahpreset" }
            ?.mapNotNull { f -> runCatching {
                json.decodeFromString<UserColorPreset>(f.readText())
            }.getOrNull() } ?: emptyList()

    fun delete(name: String) { fileFor(name).delete() }

    private fun fileFor(name: String) =
        File(dir, sanitize(name) + ".ahpreset")

    private fun sanitize(n: String) =
        n.map { c -> if (c.isLetterOrDigit() || c in "_- ") c else '_' }.joinToString("")
            .trim().ifEmpty { "preset" }.take(64)
}
