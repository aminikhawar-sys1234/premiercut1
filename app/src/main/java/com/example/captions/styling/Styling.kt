package com.ahstudio.captions.styling

import com.ahstudio.captions.core.model.*

object CaptionStyleEngine {
    fun resolve(project: CaptionProject, styleId: String): CaptionStyle = project.styleFor(styleId)
    fun scaled(style: CaptionStyle, factor: Float): CaptionStyle =
        if (factor == 1f) style else style.copy(fontSizeSp = style.fontSizeSp * factor)
}

interface TemplateRepository { fun templates(): List<CaptionTemplate> }

class CaptionTemplateEngine(vararg repositories: TemplateRepository) {
    private val repos = repositories.toList()
    fun templates(): List<CaptionTemplate> = repos.flatMap { it.templates() }
    fun byId(id: String): CaptionTemplate? = templates().firstOrNull { it.id == id }
}

object BuiltInTemplates : TemplateRepository {
    private const val K = 0xFF000000L
    private const val W = 0xFFFFFFFFL

    private val list = listOf(
        CaptionTemplate("tpl.minimal", "Minimal", TemplateCategory.MINIMAL,
            CaptionStyle("style.minimal", "Minimal", fontSizeSp = 17f, shadowRadiusDp = 4f)),
        CaptionTemplate("tpl.bold", "Bold", TemplateCategory.BOLD,
            CaptionStyle("style.bold", "Bold", fontSizeSp = 22f, bold = true,
                strokeWidthDp = 3f, strokeColorArgb = K)),
        CaptionTemplate("tpl.karaoke", "Karaoke", TemplateCategory.KARAOKE,
            CaptionStyle("style.karaoke", "Karaoke", fontSizeSp = 20f, bold = true,
                strokeWidthDp = 2f),
            CaptionAnimationSpec(CaptionAnimationType.KARAOKE_FILL, 0)),
        CaptionTemplate("tpl.podcast", "Podcast", TemplateCategory.PODCAST,
            CaptionStyle("style.podcast", "Podcast", fontSizeSp = 18f,
                background = CaptionBackgroundSpec(K, 0.7f),
                cornerRadiusDp = 10f)),
        CaptionTemplate("tpl.social", "Social", TemplateCategory.SOCIAL,
            CaptionStyle("style.social", "Social", fontSizeSp = 24f, bold = true,
                colorArgb = 0xFFFFD400L, strokeWidthDp = 4f, strokeColorArgb = K),
            CaptionAnimationSpec(CaptionAnimationType.POP, 260)),
        CaptionTemplate("tpl.news", "News", TemplateCategory.NEWS,
            CaptionStyle("style.news", "News", fontSizeSp = 16f,
                background = CaptionBackgroundSpec(0xCC0B1B33L, 0.85f),
                cornerRadiusDp = 2f, alignment = TextAlignment.LEFT)),
        CaptionTemplate("tpl.gaming", "Gaming", TemplateCategory.GAMING,
            CaptionStyle("style.gaming", "Gaming", fontSizeSp = 22f, bold = true,
                colorArgb = 0xFF00FFA3L, strokeWidthDp = 3f, letterSpacingEm = 0.04f),
            CaptionAnimationSpec(CaptionAnimationType.PULSE, 300)),
        CaptionTemplate("tpl.cinematic", "Cinematic", TemplateCategory.CINEMATIC,
            CaptionStyle("style.cinematic", "Cinematic", fontSizeSp = 19f,
                shadowRadiusDp = 8f, shadowDyDp = 3f),
            CaptionAnimationSpec(CaptionAnimationType.FADE, 500)),
        CaptionTemplate("tpl.educational", "Educational", TemplateCategory.EDUCATIONAL,
            CaptionStyle("style.educational", "Educational", fontSizeSp = 17f,
                colorArgb = W, strokeWidthDp = 2f, lineSpacingMultiplier = 1.3f)),
    )

    override fun templates(): List<CaptionTemplate> = list
}
