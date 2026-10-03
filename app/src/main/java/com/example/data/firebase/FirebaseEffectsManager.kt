package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.domain.model.EffectType
import com.example.engine.effects.registry.EffectCategory
import com.example.engine.effects.registry.EffectsAssetRegistry
import com.example.engine.effects.registry.RegisteredEffect
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Firebase Firestore Real-Time Synchronizer for Effects.
 *
 * Listens to Firestore collections ('effects', 'effects_assets') in real time.
 * When effects are added, updated, or removed in Firebase, this manager automatically
 * syncs them with EffectsAssetRegistry so they immediately appear in the 4-column
 * card layout across Video Effects, Body Effects, Photo Effects, and AI Effects.
 */
object FirebaseEffectsManager {
  private const val TAG = "FirebaseEffectsManager"
  private const val COLLECTION_EFFECTS = "effects"
  private const val COLLECTION_EFFECTS_ASSETS = "effects_assets"

  private var firestore: FirebaseFirestore? = null
  private var effectsListener: ListenerRegistration? = null
  private var effectsAssetsListener: ListenerRegistration? = null
  private var isInitialized = false

  fun init(context: Context) {
    if (isInitialized) return
    isInitialized = true

    val appContext = context.applicationContext
    try {
      if (FirebaseApp.getApps(appContext).isNotEmpty()) {
        firestore = FirebaseFirestore.getInstance()
      } else {
        try {
          val app = FirebaseApp.initializeApp(appContext)
          if (app != null) {
            firestore = FirebaseFirestore.getInstance()
          }
        } catch (t: Throwable) {
          Log.w(TAG, "Firebase not configured or offline: ${t.message}")
        }
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to get Firestore instance: ${e.message}", e)
    }

    startSync()
  }

  fun startSync() {
    val db = firestore ?: run {
      Log.d(TAG, "Firestore not available, waiting for connection")
      return
    }

    // 1. Sync from 'effects' collection
    try {
      effectsListener?.remove()
      effectsListener = db.collection(COLLECTION_EFFECTS)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.w(TAG, "Firestore '$COLLECTION_EFFECTS' listener error: ${error.message}")
            return@addSnapshotListener
          }

          if (snapshot != null) {
            val list = snapshot.documents.mapNotNull { doc ->
              parseEffectDoc(doc.id, doc.data)
            }
            Log.d(TAG, "Synced ${list.size} effects from Firestore '$COLLECTION_EFFECTS'")
            updateRegistryWithRemoteEffects(list)
          }
        }
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to attach listener on '$COLLECTION_EFFECTS': ${t.message}")
    }

    // 2. Sync from 'effects_assets' collection (if used)
    try {
      effectsAssetsListener?.remove()
      effectsAssetsListener = db.collection(COLLECTION_EFFECTS_ASSETS)
        .addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.w(TAG, "Firestore '$COLLECTION_EFFECTS_ASSETS' listener error: ${error.message}")
            return@addSnapshotListener
          }

          if (snapshot != null) {
            val list = snapshot.documents.mapNotNull { doc ->
              parseEffectDoc(doc.id, doc.data)
            }
            Log.d(TAG, "Synced ${list.size} effect assets from Firestore '$COLLECTION_EFFECTS_ASSETS'")
            updateRegistryWithRemoteEffects(list)
          }
        }
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to attach listener on '$COLLECTION_EFFECTS_ASSETS': ${t.message}")
    }
  }

  private fun parseEffectDoc(docId: String, data: Map<String, Any>?): RegisteredEffect? {
    if (data == null) return null
    try {
      val name = data["name"] as? String ?: return null
      if (name.isBlank()) return null

      val isActive = data["isActive"] as? Boolean ?: true
      if (!isActive) return null

      val categoryStr = (data["category"] as? String ?: "").uppercase()
      val category = when {
        categoryStr.contains("BODY") -> EffectCategory.BODY_EFFECTS
        categoryStr.contains("PHOTO") || categoryStr.contains("IMAGE") -> EffectCategory.PHOTO_EFFECTS
        categoryStr.contains("AI") -> EffectCategory.AI_EFFECTS
        else -> EffectCategory.VIDEO_EFFECTS
      }

      val effectTypeStr = (data["effectType"] as? String ?: data["type"] as? String ?: "").uppercase()
      val effectType = EffectType.values().find { it.name.equals(effectTypeStr, ignoreCase = true) }

      val shaderKey = data["shaderKey"] as? String ?: data["shader"] as? String
      val intensity = when (val intVal = data["intensity"]) {
        is Number -> intVal.toFloat()
        is String -> intVal.toFloatOrNull() ?: 1.0f
        else -> 1.0f
      }
      val previewThumbnailUrl = data["previewThumbnailUrl"] as? String ?: data["thumbnail"] as? String

      return RegisteredEffect(
        id = docId,
        name = name,
        category = category,
        effectType = effectType,
        shaderKey = shaderKey,
        intensity = intensity,
        previewThumbnailUrl = previewThumbnailUrl,
        isCustom = true
      )
    } catch (e: Exception) {
      Log.w(TAG, "Error parsing effect document $docId: ${e.message}")
      return null
    }
  }

  private fun updateRegistryWithRemoteEffects(remoteEffects: List<RegisteredEffect>) {
    remoteEffects.forEach { effect ->
      EffectsAssetRegistry.registerEffect(effect)
    }
  }

  fun stopSync() {
    effectsListener?.remove()
    effectsListener = null
    effectsAssetsListener?.remove()
    effectsAssetsListener = null
  }
}
