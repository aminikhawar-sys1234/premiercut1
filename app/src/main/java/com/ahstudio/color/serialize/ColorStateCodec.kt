package com.ahstudio.color.serialize

import com.ahstudio.color.core.ColorState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** §54: full persistence with versioning; project reopens visually identical. */
object ColorStateCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    fun encode(state: ColorState): String = json.encodeToString(state)

    fun decode(raw: String): ColorState {
        val parsed = json.decodeFromString<ColorState>(raw)
        return migrate(parsed)
    }

    /** Forward-only migrations keyed on ColorState.version. */
    fun migrate(s: ColorState): ColorState = when {
        s.version < ColorState.CURRENT_VERSION -> s.copy(version = ColorState.CURRENT_VERSION)
        else -> s
    }
}
