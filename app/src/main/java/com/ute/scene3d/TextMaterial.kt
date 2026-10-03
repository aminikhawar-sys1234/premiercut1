package com.ute.scene3d

data class TextMaterial(
    val id: String,
    val baseColorTint: Int = 0xFFFFFFFF.toInt(),
    val metallic: Float = 0f,       // 0 = dielectric, 1 = metallic look
    val roughness: Float = 0.6f,    // drives specular size/intensity
    val emissiveStrength: Float = 0f,
    val opacity: Float = 1f,
    val rimStrength: Float = 0f,    // fresnel rim light term
    val glassiness: Float = 0f,     // adds environment-tinted specular boost
) {
    companion object {
        val PRESETS: Map<String, TextMaterial> = mapOf(
            "matte" to TextMaterial("matte", roughness = 0.9f),
            "glossy" to TextMaterial("glossy", roughness = 0.25f, rimStrength = 0.2f),
            "metallic" to TextMaterial("metallic", metallic = 0.95f, roughness = 0.35f),
            "chrome" to TextMaterial("chrome", metallic = 1f, roughness = 0.08f, glassiness = 0.4f),
            "glass" to TextMaterial("glass", roughness = 0.1f, opacity = 0.55f, rimStrength = 0.6f, glassiness = 0.8f),
            "plastic" to TextMaterial("plastic", roughness = 0.4f, rimStrength = 0.1f),
            "emissive" to TextMaterial("emissive", emissiveStrength = 1.5f, roughness = 0.8f),
            "gradient-front" to TextMaterial("gradient-front", roughness = 0.5f),
        )
    }
}
