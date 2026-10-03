package com.example.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.EditorToolbarTab
import com.example.ui.components.navigation.NavItemColorTheme
import com.example.ui.components.navigation.NavItemThemes

data class EditorToolItem(
  val id: String,
  val name: String,
  val actionKey: String,
  val category: String,
  val order: Int,
  val isActive: Boolean = true
) {
  val mappedTab: EditorToolbarTab?
    get() = when (actionKey.uppercase()) {
      "TOOL_EFFECTS" -> EditorToolbarTab.EFFECTS
      "TOOL_FILTERS", "TOOL_LUT_PRESETS" -> EditorToolbarTab.FILTERS
      "TOOL_ANIMATIONS", "TOOL_MOTION_PRESETS" -> EditorToolbarTab.ANIMATIONS
      "TOOL_MOTION_TRACKING" -> EditorToolbarTab.MOTION_TRACKING
      "TOOL_TRANSITIONS" -> EditorToolbarTab.TRANSITIONS
      "TOOL_TEXT" -> EditorToolbarTab.TEXT
      "TOOL_AUDIO_MUSIC", "TOOL_SFX", "TOOL_VOICE_TTS" -> EditorToolbarTab.AUDIO
      "TOOL_STICKERS", "TOOL_EMOJIS" -> EditorToolbarTab.STICKERS
      "TOOL_OVERLAYS" -> null
      "TOOL_MASKS", "TOOL_BLEND_PRESETS" -> EditorToolbarTab.MASK
      "TOOL_FRAMES" -> EditorToolbarTab.CANVAS
      "TOOL_CANVAS" -> EditorToolbarTab.CANVAS
      "TOOL_BACKGROUNDS", "TOOL_BACKGROUND" -> EditorToolbarTab.BACKGROUND
      "TOOL_CHROMA_PRESETS", "TOOL_CHROMA" -> EditorToolbarTab.CHROMA
      "TOOL_SHAPES" -> EditorToolbarTab.ELEMENTS
      "TOOL_CAPTIONS" -> EditorToolbarTab.CAPTIONS
      "TOOL_AI_TOOLS" -> EditorToolbarTab.AI
      "TOOL_AI_EFFECTS" -> EditorToolbarTab.AI_MATTING
      "TOOL_FACE_EFFECTS" -> EditorToolbarTab.AI_AVATAR
      "TOOL_BEAUTY_RETOUCH", "TOOL_COLOR_PRESETS", "TOOL_ADJUST" -> EditorToolbarTab.ADJUST
      "TOOL_KEYFRAME_PRESETS", "TOOL_KEYFRAME" -> EditorToolbarTab.KEYFRAME
      "TOOL_SPEED" -> EditorToolbarTab.SPEED
      "TOOL_TRIM" -> EditorToolbarTab.TRIM
      "TOOL_EDIT" -> EditorToolbarTab.EDIT
      "TOOL_VOLUME" -> EditorToolbarTab.VOLUME
      "TOOL_MEDIA" -> EditorToolbarTab.MEDIA
      "TOOL_TRENDING_PACKS", "TOOL_EFFECT_PACKS", "TOOL_ASSET_PACKS", "TOOL_VIDEO_TEMPLATES" -> EditorToolbarTab.ASSET_STORE
      else -> when (category.lowercase()) {
        "effects" -> EditorToolbarTab.EFFECTS
        "filters", "color" -> EditorToolbarTab.FILTERS
        "animation" -> EditorToolbarTab.ANIMATIONS
        "transitions" -> EditorToolbarTab.TRANSITIONS
        "text" -> EditorToolbarTab.TEXT
        "audio" -> EditorToolbarTab.AUDIO
        "stickers" -> EditorToolbarTab.STICKERS
        "layers" -> null
        "canvas" -> EditorToolbarTab.CANVAS
        "cutout" -> EditorToolbarTab.CHROMA
        "graphics" -> EditorToolbarTab.ELEMENTS
        "ai" -> EditorToolbarTab.AI
        "adjust" -> EditorToolbarTab.ADJUST
        "packs", "templates" -> EditorToolbarTab.ASSET_STORE
        else -> null
      }
    }

  val icon: ImageVector
    get() = when (actionKey.uppercase()) {
      "TOOL_EFFECTS" -> Icons.Default.AutoAwesome
      "TOOL_FILTERS" -> Icons.Default.ColorLens
      "TOOL_ANIMATIONS" -> Icons.Default.Animation
      "TOOL_MOTION_TRACKING" -> Icons.Default.TrackChanges
      "TOOL_TRANSITIONS" -> Icons.Default.Transform
      "TOOL_TEXT" -> Icons.Default.Edit
      "TOOL_AUDIO_MUSIC" -> Icons.Default.MusicNote
      "TOOL_SFX" -> Icons.Default.GraphicEq
      "TOOL_VOICE_TTS" -> Icons.Default.Mic
      "TOOL_VIDEO_TEMPLATES" -> Icons.Default.DashboardCustomize
      "TOOL_STICKERS" -> Icons.Default.EmojiEmotions
      "TOOL_EMOJIS" -> Icons.Default.Mood
      "TOOL_OVERLAYS" -> Icons.Default.Layers
      "TOOL_MASKS" -> Icons.Default.LayersClear
      "TOOL_FRAMES" -> Icons.Default.CropSquare
      "TOOL_BACKGROUNDS" -> Icons.Default.Texture
      "TOOL_LUT_PRESETS" -> Icons.Default.Palette
      "TOOL_CHROMA_PRESETS", "TOOL_CHROMA" -> Icons.Default.FilterFrames
      "TOOL_BLEND_PRESETS" -> Icons.Default.Blender
      "TOOL_SHAPES" -> Icons.Default.Category
      "TOOL_CAPTIONS" -> Icons.Default.Subtitles
      "TOOL_AI_TOOLS" -> Icons.Default.AutoAwesome
      "TOOL_BEAUTY_RETOUCH" -> Icons.Default.FaceRetouchingNatural
      "TOOL_MOTION_PRESETS" -> Icons.Default.SlowMotionVideo
      "TOOL_KEYFRAME_PRESETS", "TOOL_KEYFRAME" -> Icons.Default.Diamond
      "TOOL_COLOR_PRESETS" -> Icons.Default.Tune
      "TOOL_EXPORT_PRESETS" -> Icons.Default.FileDownload
      "TOOL_TRENDING_PACKS" -> Icons.Default.LocalFireDepartment
      "TOOL_ASSET_PACKS" -> Icons.Default.Download
      "TOOL_EDIT" -> Icons.Default.Edit
      "TOOL_TRIM" -> Icons.Default.ContentCut
      "TOOL_SPEED" -> Icons.Default.Speed
      "TOOL_VOLUME" -> Icons.Default.VolumeUp
      "TOOL_MEDIA" -> Icons.Default.VideoLibrary
      else -> when (category.lowercase()) {
        "effects" -> Icons.Default.AutoAwesome
        "filters" -> Icons.Default.ColorLens
        "animation" -> Icons.Default.Animation
        "transitions" -> Icons.Default.Transform
        "text" -> Icons.Default.TextFields
        "audio" -> Icons.Default.MusicNote
        "stickers" -> Icons.Default.EmojiEmotions
        "layers" -> Icons.Default.Layers
        "canvas" -> Icons.Default.CropSquare
        "cutout" -> Icons.Default.FilterFrames
        "graphics" -> Icons.Default.Category
        "ai" -> Icons.Default.AutoAwesome
        "adjust", "color" -> Icons.Default.Tune
        "packs", "templates" -> Icons.Default.Download
        else -> Icons.Default.Build
      }
    }

  val theme: NavItemColorTheme
    get() = when (category.lowercase()) {
      "effects" -> NavItemThemes.Effects
      "filters", "color" -> NavItemThemes.Filters
      "animation" -> NavItemThemes.Animations
      "transitions" -> NavItemThemes.Transitions
      "text" -> if (actionKey.contains("TEMPLATE", ignoreCase = true)) NavItemThemes.TextTemplate else NavItemThemes.AddText
      "audio" -> NavItemThemes.Audio
      "stickers" -> NavItemThemes.Stickers
      "layers" -> NavItemThemes.Overlay
      "ai" -> NavItemThemes.AI
      "graphics" -> NavItemThemes.Elements
      else -> NavItemThemes.DefaultSlate
    }
}
