package com.example.ui.components.ar

import androidx.compose.ui.graphics.Color
import com.ahstudio.face.overlay.FaceAnchor

enum class ArFilterCategory(val title: String, val iconEmoji: String) {
    TRENDING("Trending", "🌟"),
    EYEWEAR("Eyewear & Visors", "👓"),
    ANIMALS("Animals & Cute", "🐾"),
    MESH_CYBER("Mesh & Cyber", "🕸️"),
    AURA_GLOW("Aura & Glow", "✨"),
    MASKS("Masks & Warps", "🎭")
}

data class ArFilterItem(
    val id: String,
    val name: String,
    val category: ArFilterCategory,
    val iconEmoji: String,
    val description: String,
    val anchor: FaceAnchor,
    val defaultScale: Float = 1.0f,
    val defaultOffsetY: Float = 0.0f,
    val primaryColor: Color = Color(0xFF00E5FF),
    val secondaryColor: Color = Color(0xFF00D1B2),
    val isMeshOverlay: Boolean = false,
    val hasParticles: Boolean = false,
    val particleType: String = "SPARKLES"
)

object ArFilterCatalog {
    val filters = listOf(
        // TRENDING
        ArFilterItem(
            id = "ar_cyber_visor",
            name = "Cyber Visor 2077",
            category = ArFilterCategory.TRENDING,
            iconEmoji = "🥽",
            description = "Futuristic neon glowing HUD visor aligned to eyes",
            anchor = FaceAnchor.EYES_CENTER,
            defaultScale = 1.25f,
            defaultOffsetY = -0.02f,
            primaryColor = Color(0xFF00E5FF),
            secondaryColor = Color(0xFF7000FF),
            hasParticles = true
        ),
        ArFilterItem(
            id = "ar_neon_crown",
            name = "Neon Royal Crown",
            category = ArFilterCategory.TRENDING,
            iconEmoji = "👑",
            description = "Floating glowing neon crown anchored above forehead",
            anchor = FaceAnchor.FOREHEAD,
            defaultScale = 1.3f,
            defaultOffsetY = -0.18f,
            primaryColor = Color(0xFFFFD700),
            secondaryColor = Color(0xFFFF007F),
            hasParticles = true
        ),

        // EYEWEAR
        ArFilterItem(
            id = "ar_matrix_shades",
            name = "Matrix Sunglasses",
            category = ArFilterCategory.EYEWEAR,
            iconEmoji = "🕶️",
            description = "Sleek dark Matrix shades attached to eye landmarks",
            anchor = FaceAnchor.EYES_CENTER,
            defaultScale = 1.15f,
            defaultOffsetY = -0.01f,
            primaryColor = Color(0xFF101010),
            secondaryColor = Color(0xFF00FF66)
        ),
        ArFilterItem(
            id = "ar_hologram_goggles",
            name = "Holo Goggles",
            category = ArFilterCategory.EYEWEAR,
            iconEmoji = "🥽",
            description = "Semi-transparent holographic tech goggles",
            anchor = FaceAnchor.EYES_CENTER,
            defaultScale = 1.2f,
            defaultOffsetY = -0.01f,
            primaryColor = Color(0xFF00E5FF),
            secondaryColor = Color(0xFF0088FF)
        ),

        // ANIMALS
        ArFilterItem(
            id = "ar_cat_whiskers",
            name = "Cat Ears & Whiskers",
            category = ArFilterCategory.ANIMALS,
            iconEmoji = "🐱",
            description = "Cute kitty ears on forehead with whiskers on cheeks",
            anchor = FaceAnchor.FACE_CENTER,
            defaultScale = 1.35f,
            defaultOffsetY = -0.1f,
            primaryColor = Color(0xFFFF69B4),
            secondaryColor = Color(0xFFFFB6C1),
            hasParticles = true
        ),
        ArFilterItem(
            id = "ar_bunny_ears",
            name = "Fluffy Bunny",
            category = ArFilterCategory.ANIMALS,
            iconEmoji = "🐰",
            description = "Playful bunny ears and pink nose highlight",
            anchor = FaceAnchor.FOREHEAD,
            defaultScale = 1.4f,
            defaultOffsetY = -0.22f,
            primaryColor = Color(0xFFFFFFFF),
            secondaryColor = Color(0xFFFFB6C1)
        ),

        // MESH & CYBER
        ArFilterItem(
            id = "ar_face_mesh_wireframe",
            name = "3D Face Mesh Grid",
            category = ArFilterCategory.MESH_CYBER,
            iconEmoji = "🕸️",
            description = "Real-time ML Kit 3D face contour wireframe grid",
            anchor = FaceAnchor.FACE_CENTER,
            defaultScale = 1.0f,
            defaultOffsetY = 0.0f,
            primaryColor = Color(0xFF00FFCC),
            secondaryColor = Color(0xFF0088FF),
            isMeshOverlay = true
        ),
        ArFilterItem(
            id = "ar_tech_hud",
            name = "Biometric Tech HUD",
            category = ArFilterCategory.MESH_CYBER,
            iconEmoji = "🛰️",
            description = "Sci-fi tactical facial recognition crosshairs & stats",
            anchor = FaceAnchor.FACE_CENTER,
            defaultScale = 1.2f,
            defaultOffsetY = 0.0f,
            primaryColor = Color(0xFF00E5FF),
            secondaryColor = Color(0xFFFF3366),
            isMeshOverlay = true
        ),

        // AURA & GLOW
        ArFilterItem(
            id = "ar_angel_halo",
            name = "Angel Radiance Halo",
            category = ArFilterCategory.AURA_GLOW,
            iconEmoji = "😇",
            description = "Radiant golden halo with glowing particle trails",
            anchor = FaceAnchor.FOREHEAD,
            defaultScale = 1.35f,
            defaultOffsetY = -0.22f,
            primaryColor = Color(0xFFFFF700),
            secondaryColor = Color(0xFFFFFFFF),
            hasParticles = true
        ),
        ArFilterItem(
            id = "ar_devil_horns",
            name = "Neon Devil Horns",
            category = ArFilterCategory.AURA_GLOW,
            iconEmoji = "😈",
            description = "Glowing fiery crimson horns anchored to temples",
            anchor = FaceAnchor.FOREHEAD,
            defaultScale = 1.25f,
            defaultOffsetY = -0.15f,
            primaryColor = Color(0xFFFF0033),
            secondaryColor = Color(0xFFFF6600)
        ),

        // MASKS & WARPS
        ArFilterItem(
            id = "ar_venice_mask",
            name = "Golden Venetian Mask",
            category = ArFilterCategory.MASKS,
            iconEmoji = "🎭",
            description = "Ornate Venetian masquerade mask covering upper face",
            anchor = FaceAnchor.EYES_CENTER,
            defaultScale = 1.25f,
            defaultOffsetY = -0.03f,
            primaryColor = Color(0xFFFFD700),
            secondaryColor = Color(0xFF8B0000)
        ),
        ArFilterItem(
            id = "ar_privacy_mosaic",
            name = "Privacy Mosaic Mask",
            category = ArFilterCategory.MASKS,
            iconEmoji = "🧩",
            description = "Dynamic face privacy blur and pixelation mask",
            anchor = FaceAnchor.FACE_CENTER,
            defaultScale = 1.1f,
            defaultOffsetY = 0.0f,
            primaryColor = Color(0xFF00D1B2),
            secondaryColor = Color(0xFF0088FF)
        )
    )
}
