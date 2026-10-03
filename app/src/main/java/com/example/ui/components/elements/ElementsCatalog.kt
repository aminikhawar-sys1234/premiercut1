package com.example.ui.components.elements

object ElementsCatalog {

  val allElements: List<ElementItem> by lazy {
    listOf(
      // 1. Shapes
      ElementItem(
        id = "shape_circle_solid",
        title = "Circle",
        subtitle = "Solid geometric circle",
        category = ElementCategory.SHAPES,
        tags = listOf("circle", "shape", "round", "solid"),
        iconSymbol = "⚪",
        vectorType = "circle_solid",
        primaryColor = 0xFF00E5FF,
        secondaryColor = 0xFF0091FF
      ),
      ElementItem(
        id = "shape_circle_outline",
        title = "Circle Ring",
        subtitle = "Clean outline ring",
        category = ElementCategory.SHAPES,
        tags = listOf("circle", "ring", "outline"),
        iconSymbol = "⭕",
        vectorType = "circle_outline",
        primaryColor = 0xFFFF0055,
        secondaryColor = 0xFFFF7700
      ),
      ElementItem(
        id = "shape_square_solid",
        title = "Square",
        subtitle = "Solid rounded box",
        category = ElementCategory.SHAPES,
        tags = listOf("square", "box", "solid"),
        iconSymbol = "⬛",
        vectorType = "square_solid",
        primaryColor = 0xFF00E5FF,
        secondaryColor = 0xFF0055FF
      ),
      ElementItem(
        id = "shape_star_5point",
        title = "Star",
        subtitle = "Golden 5-point star",
        category = ElementCategory.SHAPES,
        tags = listOf("star", "glow", "favorite"),
        iconSymbol = "⭐",
        vectorType = "star_5point",
        primaryColor = 0xFFFFD700,
        secondaryColor = 0xFFFF9100
      ),

      // 2. Arrows & Pointers (Icons)
      ElementItem(
        id = "arrow_straight_right",
        title = "Arrow Right",
        subtitle = "Horizontal pointing arrow",
        category = ElementCategory.ICONS,
        tags = listOf("arrow", "right", "next"),
        iconSymbol = "➡️",
        vectorType = "arrow_straight_right",
        primaryColor = 0xFF00E5FF,
        secondaryColor = 0xFF0088FF
      ),
      ElementItem(
        id = "arrow_curved_down",
        title = "Curved Down",
        subtitle = "Curved attention arrow",
        category = ElementCategory.ICONS,
        tags = listOf("arrow", "down", "curve"),
        iconSymbol = "⤵️",
        vectorType = "arrow_curved_down",
        primaryColor = 0xFFFF0055,
        secondaryColor = 0xFFFF5500
      ),

      // 3. Social / UI (Icons)
      ElementItem(
        id = "social_subscribe_pill",
        title = "Subscribe",
        subtitle = "Call to action button",
        category = ElementCategory.ICONS,
        tags = listOf("social", "youtube", "subscribe"),
        iconSymbol = "🔴",
        vectorType = "social_subscribe_pill",
        primaryColor = 0xFFFF0000,
        secondaryColor = 0xFFCC0000
      ),
      ElementItem(
        id = "social_like_thumb",
        title = "Like Button",
        subtitle = "Thumbs up animated card",
        category = ElementCategory.ICONS,
        tags = listOf("social", "like", "thumb"),
        iconSymbol = "👍",
        vectorType = "social_like_thumb",
        primaryColor = 0xFF00E5FF,
        secondaryColor = 0xFF0055FF
      ),

      // 4. Badges & Ribbons (Graphics)
      ElementItem(
        id = "badge_verified_shield",
        title = "Verified Shield",
        subtitle = "Official verification shield",
        category = ElementCategory.GRAPHICS,
        tags = listOf("badge", "verified", "official"),
        iconSymbol = "🛡️",
        vectorType = "badge_verified_shield",
        primaryColor = 0xFF00E5FF,
        secondaryColor = 0xFF0088FF
      ),
      ElementItem(
        id = "badge_sale_tag",
        title = "Sale Tag",
        subtitle = "Discount retail badge",
        category = ElementCategory.GRAPHICS,
        tags = listOf("badge", "sale", "discount"),
        iconSymbol = "🏷️",
        vectorType = "badge_sale_tag",
        primaryColor = 0xFFFF0055,
        secondaryColor = 0xFFFF7700
      ),

      // 5. Lines & Dividers (Graphics)
      ElementItem(
        id = "line_solid_glow",
        title = "Neon Glow Line",
        subtitle = "Horizontal gradient divider",
        category = ElementCategory.GRAPHICS,
        tags = listOf("line", "divider", "glow"),
        iconSymbol = "➖",
        vectorType = "line_solid_glow",
        primaryColor = 0xFF00E5FF,
        secondaryColor = 0xFF9D00FF
      ),

      // 6. Charts & Progress
      ElementItem(
        id = "chart_line",
        title = "Line Chart",
        subtitle = "Smooth metric trend line",
        category = ElementCategory.CHARTS,
        tags = listOf("chart", "line", "trend", "graph"),
        iconSymbol = "📈",
        vectorType = "chart_line",
        primaryColor = 0xFF00FF88,
        secondaryColor = 0xFF0088FF
      ),
      ElementItem(
        id = "chart_donut",
        title = "Donut Gauge",
        subtitle = "Circular percentage ring",
        category = ElementCategory.CHARTS,
        tags = listOf("chart", "donut", "ring", "percentage"),
        iconSymbol = "🍩",
        vectorType = "chart_donut",
        primaryColor = 0xFF76FF03,
        secondaryColor = 0xFF00E5FF
      )
    )
  }

  fun getByCategory(category: ElementCategory): List<ElementItem> {
    return allElements.filter { it.category == category }
  }

  fun search(query: String): List<ElementItem> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return allElements
    return allElements.filter { item ->
      item.title.lowercase().contains(q) ||
      item.subtitle.lowercase().contains(q) ||
      item.category.displayName.lowercase().contains(q) ||
      item.tags.any { it.lowercase().contains(q) }
    }
  }

  fun findById(id: String): ElementItem? {
    return allElements.find { it.id == id }
  }
}
