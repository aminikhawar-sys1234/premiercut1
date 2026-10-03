package com.ahstudio.color.space

object ColorSpaceRegistry {
    val SRGB = RgbColorSpace("sRGB", 0.640f, 0.330f, 0.300f, 0.600f, 0.150f, 0.060f, 0.3127f, 0.3290f, TransferFunctions.SRGB)
    val REC709 = RgbColorSpace("Rec.709", 0.640f, 0.330f, 0.300f, 0.600f, 0.150f, 0.060f, 0.3127f, 0.3290f, TransferFunctions.REC709)
    val DISPLAY_P3 = RgbColorSpace("Display P3", 0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f, 0.3127f, 0.3290f, TransferFunctions.SRGB)
    val DCI_P3 = RgbColorSpace("DCI-P3", 0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f, 0.314f, 0.351f, TransferFunctions.GAMMA24)
    val REC2020 = RgbColorSpace("Rec.2020", 0.708f, 0.292f, 0.170f, 0.797f, 0.131f, 0.046f, 0.3127f, 0.3290f, TransferFunctions.REC709)
    /** ACEScg — AP1 primaries, ACES white (D60-ish), LINEAR working space. */
    val ACESCG = RgbColorSpace("ACEScg", 0.713f, 0.293f, 0.165f, 0.830f, 0.128f, 0.044f, 0.32168f, 0.33767f, TransferFunctions.Linear)

    fun byName(n: String): RgbColorSpace? = when (n.lowercase()) {
        "srgb" -> SRGB; "rec709" -> REC709; "display-p3" -> DISPLAY_P3
        "dci-p3" -> DCI_P3; "rec2020" -> REC2020; "acescg" -> ACESCG
        else -> null
    }
}
