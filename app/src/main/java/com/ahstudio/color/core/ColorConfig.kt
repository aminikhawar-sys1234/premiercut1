package com.ahstudio.color.core

import com.ahstudio.color.space.RgbColorSpace
import com.ahstudio.color.space.TransferFunctions
import com.ahstudio.color.space.TransferFunction

enum class ColorRange { FULL, LIMITED }

/** Input color metadata — from container/codec when available (§45). */
data class InputColorMetadata(
    val colorSpace: RgbColorSpace?,
    val transfer: TransferFunction?,
    val range: ColorRange?,
    val isMetadataReliable: Boolean
) {
    companion object {
        /** Documented fallback when metadata missing: Rec.709 primaries, 709/sRGB transfer, limited. */
        fun fallback(): InputColorMetadata =
            InputColorMetadata(
                com.ahstudio.color.space.ColorSpaceRegistry.REC709,
                TransferFunctions.REC709, ColorRange.LIMITED, isMetadataReliable = false
            )
    }
}

data class ColorConfig(
    val workingSpace: RgbColorSpace = com.ahstudio.color.space.ColorSpaceRegistry.REC709,
    val outputSpace: RgbColorSpace = com.ahstudio.color.space.ColorSpaceRegistry.REC709,
    val workingTransfer: TransferFunction = TransferFunctions.SRGB,
    val outputTransfer: TransferFunction = TransferFunctions.REC709,
    val inputMetadata: InputColorMetadata = InputColorMetadata.fallback(),
    val hdrOutput: Boolean = false,
    val halfFloatFbo: Boolean = true,
    val debugStage: Int = -1   // §68 pipeline debug taps, -1 = off
)
