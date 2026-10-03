package com.example.data.repository

import android.util.Log
import com.example.domain.model.EditorToolItem
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class EditorToolsRepository {
  companion object {
    private const val TAG = "EditorToolsRepository"
    private const val COLLECTION_NAME = "editor_tools"
  }

  private val firestore: FirebaseFirestore? by lazy {
    try {
      val app = runCatching { com.example.StudioApplication.instance }.getOrNull()
      if (app != null && FirebaseApp.getApps(app).isNotEmpty()) {
        FirebaseFirestore.getInstance()
      } else {
        null
      }
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to get Firestore instance: ${t.message}")
      null
    }
  }

  /**
   * Loads editor tools directly from Firestore 'editor_tools' collection in real-time.
   * Emits live updates whenever documents are added, updated, or re-ordered.
   */
  fun getEditorToolsStream(): Flow<List<EditorToolItem>> {
    val db = firestore ?: return flowOf(getDefaultEditorTools())
    return callbackFlow {
      // Send initial fallback while connecting
      trySend(getDefaultEditorTools())

      val listener = try {
        db.collection(COLLECTION_NAME)
          .addSnapshotListener { snapshot, error ->
            if (error != null) {
              Log.w(TAG, "Firestore snapshot listener error: ${error.message}")
              trySend(getDefaultEditorTools())
              return@addSnapshotListener
            }

            if (snapshot != null && !snapshot.isEmpty) {
              val items = snapshot.documents.mapNotNull { doc ->
                try {
                  val id = doc.id
                  val name = doc.getString("name") ?: ""
                  val actionKey = doc.getString("actionKey") ?: ""
                  val category = doc.getString("category") ?: "general"
                  val orderNum = (doc.getLong("order") ?: doc.getString("order")?.toLongOrNull() ?: 0L).toInt()
                  val isActive = doc.getBoolean("isActive") ?: true

                  // Exclude Asset Packs, AI Tools, and Emojis from editor navigation tools
                  val isExcluded = id == "tool_asset_packs" || id == "tool_ai_tools" || id == "tool_emojis" ||
                    actionKey == "TOOL_ASSET_PACKS" || actionKey == "TOOL_AI_TOOLS" || actionKey == "TOOL_EMOJIS" ||
                    name.equals("Asset Packs", ignoreCase = true) ||
                    name.equals("Assets Packs", ignoreCase = true) ||
                    name.equals("Assets packs", ignoreCase = true) ||
                    name.equals("AI Tools", ignoreCase = true) ||
                    name.equals("Emojis", ignoreCase = true)

                  if (name.isNotBlank() && !isExcluded) {
                    EditorToolItem(
                      id = id,
                      name = name,
                      actionKey = actionKey,
                      category = category,
                      order = orderNum,
                      isActive = isActive
                    )
                  } else {
                    null
                  }
                } catch (e: Exception) {
                  Log.w(TAG, "Error parsing editor tool doc ${doc.id}: ${e.message}")
                  null
                }
              }.sortedBy { it.order }

              // Deduplicate Firestore tools to prevent repeated identical tabs
              val distinctItems = mutableListOf<EditorToolItem>()
              val seenActionKeys = mutableSetOf<String>()
              val seenNames = mutableSetOf<String>()
              for (item in items) {
                val actionNorm = item.actionKey.uppercase().trim()
                val nameNorm = item.name.lowercase().trim()
                if (actionNorm.isNotBlank() && !seenActionKeys.contains(actionNorm) && !seenNames.contains(nameNorm)) {
                  seenActionKeys.add(actionNorm)
                  seenNames.add(nameNorm)
                  distinctItems.add(item)
                }
              }

              if (distinctItems.isNotEmpty()) {
                Log.d(TAG, "Loaded ${distinctItems.size} distinct editor tools from Firestore '$COLLECTION_NAME'")
                trySend(distinctItems)
              } else {
                trySend(getDefaultEditorTools())
              }
            } else {
              Log.d(TAG, "Firestore '$COLLECTION_NAME' empty, using defaults")
              trySend(getDefaultEditorTools())
            }
          }
      } catch (t: Throwable) {
        Log.e(TAG, "Error attaching Firestore snapshot listener: ${t.message}", t)
        trySend(getDefaultEditorTools())
        null
      }

      awaitClose {
        listener?.remove()
      }
    }
  }

  /**
   * Primary Fallback providing a clean, distinct set of 1-per-category tools
   * with zero duplicates and instant UI rendering.
   */
  fun getDefaultEditorTools(): List<EditorToolItem> {
    return listOf(
      EditorToolItem("tool_audio", "Audio", "TOOL_AUDIO_MUSIC", "audio", 1, true),
      EditorToolItem("tool_text", "Text", "TOOL_TEXT", "text", 2, true),
      EditorToolItem("tool_overlays", "Overlay", "TOOL_OVERLAYS", "layers", 3, true),
      EditorToolItem("tool_effects", "Effects", "TOOL_EFFECTS", "effects", 4, true),
      EditorToolItem("tool_transitions", "Transitions", "TOOL_TRANSITIONS", "transitions", 5, true),
      EditorToolItem("tool_filters", "Filters", "TOOL_FILTERS", "filters", 6, true),
      EditorToolItem("tool_adjust", "Adjust", "TOOL_ADJUST", "adjust", 7, true),
      EditorToolItem("tool_stickers", "Stickers", "TOOL_STICKERS", "stickers", 8, true),
      EditorToolItem("tool_animations", "Animations", "TOOL_ANIMATIONS", "animation", 9, true),
      EditorToolItem("tool_motion_tracking", "Tracking", "TOOL_MOTION_TRACKING", "animation", 10, true),
      EditorToolItem("tool_canvas", "Canvas", "TOOL_CANVAS", "canvas", 11, true),
      EditorToolItem("tool_chroma", "Chroma Key", "TOOL_CHROMA", "cutout", 12, true),
      EditorToolItem("tool_masks", "Mask", "TOOL_MASKS", "layers", 13, true),
      EditorToolItem("tool_keyframe", "Keyframes", "TOOL_KEYFRAME", "animation", 14, true),
      EditorToolItem("tool_captions", "Captions", "TOOL_CAPTIONS", "text", 15, true),
      EditorToolItem("tool_ai", "AI Suite", "TOOL_AI_TOOLS", "ai", 16, true),
      EditorToolItem("tool_elements", "Elements", "TOOL_SHAPES", "graphics", 17, true),
      EditorToolItem("tool_asset_store", "Asset Store", "TOOL_TRENDING_PACKS", "packs", 18, true),
      EditorToolItem("tool_export", "Export", "TOOL_EXPORT_PRESETS", "export", 19, true)
    )
  }
}
