package com.example.ui.components.elements

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 11 Standard Elements Categories
 */
enum class ElementCategory(
  val id: String,
  val displayName: String,
  val icon: ImageVector,
  val description: String
) {
  SHAPES("shapes", "Shapes", Icons.Default.Category, "Circles, squares, lines, arrows & geometry"),
  GRAPHICS("graphics", "Graphics", Icons.Default.AutoAwesome, "Illustrations, decorative graphics & stickers"),
  FRAMES("frames", "Frames", Icons.Default.CropPortrait, "Image, video & device frames"),
  TABLES("tables", "Tables", Icons.Default.TableChart, "Grids, rows/cols & comparison tables"),
  THREE_D("3d", "3D", Icons.Default.ViewInAr, "3D objects, shapes & volume graphics"),
  ICONS("icons", "Icons", Icons.Default.Widgets, "Social, business & UI icons"),
  ANIMALS("animals", "Animals", Icons.Default.Pets, "Animal illustrations, shapes & graphics"),
  BIRDS("birds", "Birds", Icons.Default.Flight, "Flying birds, bird types & illustrations"),
  CHARACTERS("characters", "Characters", Icons.Default.Person, "Men, women, kids & elder avatars"),
  VEHICLES("vehicles", "Vehicles", Icons.Default.DirectionsCar, "Cars, bikes, trucks, jeeps & tractors"),
  CHARTS("charts", "Charts", Icons.Default.BarChart, "Bar, line, pie, graphs & data charts")
}

/**
 * Represents an individual Element available in the Elements Panel.
 */
data class ElementItem(
  val id: String,
  val title: String,
  val subtitle: String,
  val category: ElementCategory,
  val tags: List<String>,
  val iconSymbol: String,
  val vectorType: String,
  val defaultScale: Float = 1.0f,
  val primaryColor: Long = 0xFF00E5FF,
  val secondaryColor: Long = 0xFF7000FF,
  val isAnimated: Boolean = false,
  val renderData: String? = null
)
