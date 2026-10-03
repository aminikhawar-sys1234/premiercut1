package com.ute.templates

import android.content.Context
import com.ute.core.Safe
import com.ute.core.TextEngineException
import com.ute.core.TimeRange
import com.ute.model.*
import com.ute.serialization.TextDocumentCodec
import org.json.JSONObject
import java.util.UUID

/**
 * Loads templates from assets/ute/templates (JSON files; data-driven; adding a
 * template is adding a file) and applies them into editable layers.
 */
class TemplateEngine(private val appContext: Context) {

    private val templates = LinkedHashMap<String, TextTemplate>()

    fun loadFromAssets(dir: String = "ute/templates") {
        Safe.critical("templates.load", Unit) {
            val list = appContext.assets.list(dir) ?: emptyArray()
            list.forEach { file ->
                appContext.assets.open("$dir/$file").bufferedReader().use { r ->
                    register(parse(r.readText()))
                }
            }
        }
    }

    fun register(template: TextTemplate) { templates[template.id] = template }
    fun byCategory(category: TextTemplate.Category): List<TextTemplate> =
        templates.values.filter { it.category == category }
    fun all(): List<TextTemplate> = templates.values.toList()
    fun find(id: String): TextTemplate = templates[id]
        ?: throw TextEngineException.InvalidInput("unknown template: $id")

    /** Apply a template with user overrides → a fully editable TextLayer. */
    fun apply(templateId: String, textOverride: String? = null,
              fillOverride: PaintSpec? = null, fontSizeOverride: Float? = null): TextLayer {
        val t = find(templateId)
        return TextLayer(
            id = "layer-${UUID.randomUUID()}",
            name = t.name,
            content = textOverride ?: t.defaultText,
            language = t.language,
            direction = t.direction,
            style = t.style.copy(sizePx = fontSizeOverride ?: t.style.sizePx),
            appearance = if (fillOverride != null) t.appearance.copy(fill = fillOverride) else t.appearance,
            layout = t.layout,
            transform = t.transform,
            motion = t.motion,
            timing = t.timing,
            text3d = t.threeD,
            effects = t.effects,
        )
    }

    fun parse(json: String): TextTemplate = Safe.critical("template.parse",
        TextTemplate("invalid", TextTemplate.Category.SOCIAL, "invalid", "")) {
        val o = JSONObject(json)
        TextTemplate(
            id = o.getString("id"),
            category = TextTemplate.Category.valueOf(o.optString("category", "SOCIAL")),
            name = o.getString("name"),
            defaultText = o.optString("defaultText", "Text"),
            language = o.optString("language", "en"),
            direction = Direction.valueOf(o.optString("direction", "AUTO")),
            style = TextDocumentCodec.decodeStyle(o.optJSONObject("style") ?: JSONObject()),
            appearance = TextDocumentCodec.decodeAppearance(o.optJSONObject("appearance") ?: JSONObject()),
            layout = TextDocumentCodec.decodeLayout(o.optJSONObject("layout") ?: JSONObject()),
            motion = o.optJSONObject("motion")?.let { TextDocumentCodec.decodeMotion(it) },
            timing = TimeRange(o.optDouble("startSec", 0.0), o.optDouble("durationSec", 5.0)),
        )
    }
}
