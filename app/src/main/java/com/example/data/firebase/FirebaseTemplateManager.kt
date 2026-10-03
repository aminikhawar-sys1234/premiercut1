package com.example.data.firebase

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.data.local.TimelineSerializer
import com.example.data.presets.MediaPlaceholder
import com.example.data.presets.PlaceholderType
import com.example.data.presets.TextPlaceholder
import com.example.data.presets.VideoTemplate
import com.example.domain.model.AspectRatio
import com.example.domain.model.FrameRate
import com.example.domain.model.Resolution
import com.example.domain.model.Timeline
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class CreatorProfile(
  val creatorId: String = "",
  val displayName: String = "",
  val handle: String = "",
  val bio: String = "",
  val avatarUrl: String? = null,
  val tikTokHandle: String? = null,
  val category: String = "Reels & TikTok",
  val isProfileComplete: Boolean = false,
  val templatesCount: Long = 0L,
  val totalViews: Long = 0L,
  val totalUses: Long = 0L,
  val updatedAt: Long = System.currentTimeMillis()
)

object FirebaseTemplateManager {
  private const val TAG = "FirebaseTemplateMgr"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var prefs: SharedPreferences? = null
  private var firestore: FirebaseFirestore? = null
  private var storage: FirebaseStorage? = null
  private var templatesListener: ListenerRegistration? = null

  private val _templates = MutableStateFlow<List<VideoTemplate>>(emptyList())
  val templates: StateFlow<List<VideoTemplate>> = _templates.asStateFlow()

  private val _creatorProfile = MutableStateFlow(CreatorProfile())
  val creatorProfile: StateFlow<CreatorProfile> = _creatorProfile.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  fun init(context: Context) {
    if (prefs != null) return
    val appContext = context.applicationContext
    prefs = appContext.getSharedPreferences("ah_creator_templates", Context.MODE_PRIVATE)

    // Load locally cached creator profile
    loadCachedProfile()

    // Initialize Firebase references
    try {
      if (FirebaseApp.getApps(appContext).isNotEmpty()) {
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
      } else {
        try {
          val app = FirebaseApp.initializeApp(appContext)
          if (app != null) {
            firestore = FirebaseFirestore.getInstance()
            storage = FirebaseStorage.getInstance()
          }
        } catch (t: Throwable) {
          Log.w(TAG, "Firebase offline fallback: ${t.message}")
        }
      }
      Log.d(TAG, "Firebase Firestore & Storage initialized for Templates")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to initialize Firebase: ${e.message}", e)
    }

    // Start real-time Firestore synchronization
    startTemplatesListener()
  }

  private fun loadCachedProfile() {
    val p = prefs ?: return
    val creatorId = p.getString("creator_id", null) ?: "creator_${UUID.randomUUID().toString().take(8)}"
    val displayName = p.getString("display_name", "") ?: ""
    val handle = p.getString("handle", "") ?: ""
    val bio = p.getString("bio", "") ?: ""
    val tikTokHandle = p.getString("tiktok_handle", "") ?: ""
    val category = p.getString("category", "Reels & TikTok") ?: "Reels & TikTok"
    val isComplete = p.getBoolean("is_profile_complete", false)
    val templatesCount = p.getLong("templates_count", 0L)
    val totalViews = p.getLong("total_views", 0L)
    val totalUses = p.getLong("total_uses", 0L)

    val profile = CreatorProfile(
      creatorId = creatorId,
      displayName = displayName,
      handle = if (handle.startsWith("@")) handle else if (handle.isNotBlank()) "@$handle" else "",
      bio = bio,
      tikTokHandle = tikTokHandle,
      category = category,
      isProfileComplete = isComplete,
      templatesCount = templatesCount,
      totalViews = totalViews,
      totalUses = totalUses
    )
    _creatorProfile.value = profile

    // Ensure creator ID is saved
    p.edit().putString("creator_id", creatorId).apply()
  }

  suspend fun saveCreatorProfile(profile: CreatorProfile): Result<Unit> {
    return try {
      val normalizedHandle = if (profile.handle.startsWith("@")) profile.handle else "@${profile.handle.trim()}"
      val updated = profile.copy(
        handle = normalizedHandle,
        isProfileComplete = true,
        updatedAt = System.currentTimeMillis()
      )
      _creatorProfile.value = updated

      // Save locally
      prefs?.edit()?.apply {
        putString("creator_id", updated.creatorId)
        putString("display_name", updated.displayName)
        putString("handle", updated.handle)
        putString("bio", updated.bio)
        putString("tiktok_handle", updated.tikTokHandle ?: "")
        putString("category", updated.category)
        putBoolean("is_profile_complete", true)
        putLong("templates_count", updated.templatesCount)
        putLong("total_views", updated.totalViews)
        putLong("total_uses", updated.totalUses)
        apply()
      }

      // Sync to Firestore
      val db = firestore
      if (db != null) {
        val map = mapOf(
          "creatorId" to updated.creatorId,
          "displayName" to updated.displayName,
          "handle" to updated.handle,
          "bio" to updated.bio,
          "tikTokHandle" to (updated.tikTokHandle ?: ""),
          "category" to updated.category,
          "isProfileComplete" to true,
          "templatesCount" to updated.templatesCount,
          "totalViews" to updated.totalViews,
          "totalUses" to updated.totalUses,
          "updatedAt" to updated.updatedAt
        )
        db.collection("creators").document(updated.creatorId).set(map).await()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Error saving creator profile: ${e.message}", e)
      // Profile is still cached locally
      Result.success(Unit)
    }
  }

  private fun startTemplatesListener() {
    val db = firestore ?: return
    templatesListener?.remove()

    try {
      templatesListener = db.collection("templates")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshots, error ->
          if (error != null) {
            Log.w(TAG, "Firestore templates snapshot error: ${error.message}")
            return@addSnapshotListener
          }

          if (snapshots != null) {
            val list = mutableListOf<VideoTemplate>()
            for (doc in snapshots.documents) {
              try {
                val tpl = parseDocumentToVideoTemplate(doc.id, doc.data ?: emptyMap())
                if (tpl != null) {
                  list.add(tpl)
                }
              } catch (e: Exception) {
                Log.e(TAG, "Failed to parse template ${doc.id}: ${e.message}")
              }
            }
            _templates.value = list
            updateCreatorStatsFromTemplates(list)
          }
        }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to attach Firestore snapshot listener: ${e.message}", e)
    }
  }

  private fun updateCreatorStatsFromTemplates(allTemplates: List<VideoTemplate>) {
    val currentCreatorId = _creatorProfile.value.creatorId
    if (currentCreatorId.isBlank()) return

    val myTemplates = allTemplates.filter { it.creatorId == currentCreatorId }
    val totalViews = myTemplates.sumOf { it.viewsCount }
    val totalUses = myTemplates.sumOf { it.usesCount }

    val updated = _creatorProfile.value.copy(
      templatesCount = myTemplates.size.toLong(),
      totalViews = totalViews,
      totalUses = totalUses
    )
    _creatorProfile.value = updated

    prefs?.edit()?.apply {
      putLong("templates_count", updated.templatesCount)
      putLong("total_views", totalViews)
      putLong("total_uses", totalUses)
      apply()
    }
  }

  private fun parseDocumentToVideoTemplate(docId: String, data: Map<String, Any>): VideoTemplate? {
    val title = data["title"] as? String ?: "Untitled Template"
    val category = data["category"] as? String ?: "Reels"
    val description = data["description"] as? String ?: ""
    val creatorId = data["creatorId"] as? String ?: ""
    val creatorName = data["creatorName"] as? String ?: "Creator"
    val creatorHandle = data["creatorHandle"] as? String ?: "@creator"
    val creatorAvatarUrl = data["creatorAvatarUrl"] as? String
    val aspectRatioStr = data["aspectRatio"] as? String ?: "9:16"
    val durationMs = (data["durationMs"] as? Number)?.toLong() ?: 5000L
    val viewsCount = (data["viewsCount"] as? Number)?.toLong() ?: 0L
    val usesCount = (data["usesCount"] as? Number)?.toLong() ?: 0L
    val previewVideoUrl = data["previewVideoUrl"] as? String
    val previewThumbnailUrl = data["previewThumbnailUrl"] as? String
    val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    val timelineJson = data["timelineJson"] as? String ?: ""
    val iconEmoji = data["iconEmoji"] as? String ?: "🎬"
    val isPro = (data["isPro"] as? Boolean) ?: false

    val aspectRatio = when (aspectRatioStr) {
      "9:16" -> AspectRatio.RATIO_9_16
      "16:9" -> AspectRatio.RATIO_16_9
      "1:1" -> AspectRatio.RATIO_1_1
      "4:5" -> AspectRatio.RATIO_4_5
      else -> AspectRatio.RATIO_9_16
    }

    val parsedTimeline = if (timelineJson.isNotBlank()) {
      TimelineSerializer.fromJson(timelineJson)
    } else {
      Timeline()
    }

    // Derive placeholders automatically from the timeline clips
    val mediaPlaceholders = mutableListOf<MediaPlaceholder>()
    val textPlaceholders = mutableListOf<TextPlaceholder>()

    parsedTimeline.videoClips.forEachIndexed { index, clip ->
      mediaPlaceholders.add(
        MediaPlaceholder(
          slotId = "slot_vid_${clip.id}",
          label = "Clip #${index + 1} (${clip.durationMs / 1000}s)",
          placeholderType = PlaceholderType.VIDEO,
          requiredDurationMs = clip.durationMs,
          targetClipId = clip.id,
          defaultName = clip.name
        )
      )
    }

    parsedTimeline.overlayClips.forEachIndexed { index, overlay ->
      mediaPlaceholders.add(
        MediaPlaceholder(
          slotId = "slot_overlay_${overlay.id}",
          label = "Overlay #${index + 1}",
          placeholderType = if (overlay.isVideo) PlaceholderType.VIDEO else PlaceholderType.IMAGE,
          requiredDurationMs = overlay.durationMs,
          targetClipId = overlay.id,
          defaultName = "Overlay Media",
          isOverlay = true
        )
      )
    }

    parsedTimeline.textClips.forEachIndexed { index, textClip ->
      textPlaceholders.add(
        TextPlaceholder(
          slotId = "slot_txt_${textClip.id}",
          label = "Text #${index + 1}",
          targetClipId = textClip.id,
          defaultText = textClip.text
        )
      )
    }

    val gradientStart = (data["thumbnailGradientStart"] as? Number)?.toLong() ?: 0xFF3B82F6
    val gradientEnd = (data["thumbnailGradientEnd"] as? Number)?.toLong() ?: 0xFF8B5CF6

    return VideoTemplate(
      id = docId,
      title = title,
      category = category,
      description = description,
      aspectRatio = aspectRatio,
      resolution = Resolution.RES_1080P,
      fps = FrameRate.FPS_30,
      durationMs = durationMs,
      thumbnailGradientStart = gradientStart,
      thumbnailGradientEnd = gradientEnd,
      iconEmoji = iconEmoji,
      mediaPlaceholders = mediaPlaceholders,
      textPlaceholders = textPlaceholders,
      audioTitle = (data["audioTitle"] as? String) ?: "Soundtrack",
      isPro = isPro,
      savedTimeline = parsedTimeline,
      creatorId = creatorId,
      creatorName = creatorName,
      creatorHandle = creatorHandle,
      creatorAvatarUrl = creatorAvatarUrl,
      previewVideoUrl = previewVideoUrl,
      previewThumbnailUrl = previewThumbnailUrl,
      viewsCount = viewsCount,
      usesCount = usesCount,
      createdAt = createdAt,
      createTimeline = { mediaMap, textMap ->
        // Apply replacements dynamically to the saved timeline
        var updatedTimeline = parsedTimeline.copy()
        if (mediaMap.isNotEmpty()) {
          updatedTimeline = updatedTimeline.copy(
            videoClips = updatedTimeline.videoClips.map { clip ->
              val rep = mediaMap["slot_vid_${clip.id}"] ?: mediaMap[clip.id]
              if (rep != null) clip.copy(uri = rep) else clip
            },
            overlayClips = updatedTimeline.overlayClips.map { overlay ->
              val rep = mediaMap["slot_overlay_${overlay.id}"] ?: mediaMap[overlay.id]
              if (rep != null) overlay.copy(uri = rep) else overlay
            }
          )
        }
        if (textMap.isNotEmpty()) {
          updatedTimeline = updatedTimeline.copy(
            textClips = updatedTimeline.textClips.map { textClip ->
              val rep = textMap["slot_txt_${textClip.id}"] ?: textMap[textClip.id]
              if (rep != null) textClip.copy(text = rep) else textClip
            }
          )
        }
        updatedTimeline
      }
    )
  }

  /**
   * Automatically saves to Firebase Storage + Firestore and updates local state.
   */
  suspend fun uploadAndPublishTemplate(
    title: String,
    category: String,
    description: String,
    timeline: Timeline,
    aspectRatio: AspectRatio,
    exportedVideoFile: File?,
    thumbnailBitmap: Bitmap? = null
  ): Result<VideoTemplate> {
    _isLoading.value = true
    return try {
      val templateId = "tpl_fb_${UUID.randomUUID().toString().take(10)}"
      val creator = _creatorProfile.value
      val durationMs = timeline.totalDurationMs.coerceAtLeast(1000L)

      var previewVideoUrl: String? = null
      var previewThumbnailUrl: String? = null

      val storageRef = storage?.reference

      // 1. Upload video preview to Firebase Storage if file exists
      if (exportedVideoFile != null && exportedVideoFile.exists() && storageRef != null) {
        try {
          val videoRef = storageRef.child("templates/$templateId/preview.mp4")
          videoRef.putFile(Uri.fromFile(exportedVideoFile)).await()
          previewVideoUrl = videoRef.downloadUrl.await().toString()
          Log.d(TAG, "Uploaded preview video to Firebase Storage: $previewVideoUrl")
        } catch (e: Exception) {
          Log.w(TAG, "Firebase Storage video upload skipped/failed: ${e.message}")
          previewVideoUrl = exportedVideoFile.absolutePath
        }
      } else if (exportedVideoFile != null) {
        previewVideoUrl = exportedVideoFile.absolutePath
      }

      // 2. Upload thumbnail to Firebase Storage if bitmap exists
      if (thumbnailBitmap != null && storageRef != null) {
        try {
          val thumbFile = File.createTempFile("thumb_$templateId", ".jpg")
          FileOutputStream(thumbFile).use { out ->
            thumbnailBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
          }
          val thumbRef = storageRef.child("templates/$templateId/thumbnail.jpg")
          thumbRef.putFile(Uri.fromFile(thumbFile)).await()
          previewThumbnailUrl = thumbRef.downloadUrl.await().toString()
          thumbFile.delete()
          Log.d(TAG, "Uploaded thumbnail to Firebase Storage: $previewThumbnailUrl")
        } catch (e: Exception) {
          Log.w(TAG, "Firebase Storage thumbnail upload failed: ${e.message}")
        }
      }

      // 3. Serialize Timeline to JSON
      val timelineJson = TimelineSerializer.serializeTimeline(timeline)

      // 4. Build Firestore Document Map
      val gradientStart = when (category) {
        "TikTok-style short videos", "TikTok" -> 0xFFFE2C55
        "Reels", "Instagram" -> 0xFFE1306C
        "YouTube", "YouTube Shorts" -> 0xFFFF0000
        "Cinematic" -> 0xFF0F172A
        "Business", "Product Ads" -> 0xFF2563EB
        else -> 0xFF7C3AED
      }
      val gradientEnd = when (category) {
        "TikTok-style short videos", "TikTok" -> 0xFF25F4EE
        "Reels", "Instagram" -> 0xFFF77737
        "YouTube", "YouTube Shorts" -> 0xFF991B1B
        "Cinematic" -> 0xFF1E293B
        "Business", "Product Ads" -> 0xFF38BDF8
        else -> 0xFFEC4899
      }

      val iconEmoji = when (category) {
        "TikTok-style short videos", "TikTok" -> "⚡"
        "Reels", "Instagram" -> "✨"
        "YouTube", "YouTube Shorts" -> "▶️"
        "Cinematic" -> "🎥"
        "Business", "Product Ads" -> "💼"
        "Travel" -> "✈️"
        "Birthday" -> "🎂"
        "Wedding" -> "💍"
        else -> "🎬"
      }

      val docData = hashMapOf(
        "id" to templateId,
        "title" to title.ifBlank { "Viral Template" },
        "category" to category.ifBlank { "Reels" },
        "description" to description.ifBlank { "Professional editing template by ${creator.displayName}" },
        "creatorId" to creator.creatorId,
        "creatorName" to (creator.displayName.ifBlank { "Studio Creator" }),
        "creatorHandle" to (creator.handle.ifBlank { "@creator" }),
        "creatorAvatarUrl" to (creator.avatarUrl ?: ""),
        "creatorTikTok" to (creator.tikTokHandle ?: ""),
        "aspectRatio" to aspectRatio.label,
        "durationMs" to durationMs,
        "previewVideoUrl" to (previewVideoUrl ?: ""),
        "previewThumbnailUrl" to (previewThumbnailUrl ?: ""),
        "viewsCount" to 0L,
        "usesCount" to 0L,
        "createdAt" to System.currentTimeMillis(),
        "timelineJson" to timelineJson,
        "audioTitle" to (timeline.audioClips.firstOrNull()?.title ?: "Original Audio"),
        "thumbnailGradientStart" to gradientStart,
        "thumbnailGradientEnd" to gradientEnd,
        "iconEmoji" to iconEmoji,
        "isPro" to false
      )

      // 5. Save to Firestore
      val db = firestore
      if (db != null) {
        try {
          db.collection("templates").document(templateId).set(docData).await()
          // Update creator templatesCount
          db.collection("creators").document(creator.creatorId).update(
            "templatesCount", FieldValue.increment(1)
          )
        } catch (e: Exception) {
          Log.w(TAG, "Firestore set failed: ${e.message}")
        }
      }

      // 6. Create local VideoTemplate object and inject into StateFlow immediately
      val newTemplate = parseDocumentToVideoTemplate(templateId, docData)!!
      _templates.value = listOf(newTemplate) + _templates.value.filterNot { it.id == templateId }

      // Update creator profile templates count
      val updatedProfile = _creatorProfile.value.copy(
        templatesCount = _creatorProfile.value.templatesCount + 1
      )
      _creatorProfile.value = updatedProfile
      prefs?.edit()?.putLong("templates_count", updatedProfile.templatesCount)?.apply()

      Log.d(TAG, "Successfully published template: ${newTemplate.id} (${newTemplate.title})")
      Result.success(newTemplate)
    } catch (e: Exception) {
      Log.e(TAG, "Error publishing template: ${e.message}", e)
      Result.failure(e)
    } finally {
      _isLoading.value = false
    }
  }

  /**
   * Automatically increments template views in Firebase Firestore.
   */
  fun recordTemplateView(templateId: String, creatorId: String) {
    scope.launch {
      // 1. Update in-memory state immediately
      _templates.value = _templates.value.map {
        if (it.id == templateId) it.copy(viewsCount = it.viewsCount + 1) else it
      }
      if (creatorId == _creatorProfile.value.creatorId) {
        val updated = _creatorProfile.value.copy(totalViews = _creatorProfile.value.totalViews + 1)
        _creatorProfile.value = updated
        prefs?.edit()?.putLong("total_views", updated.totalViews)?.apply()
      }

      // 2. Update Firestore
      try {
        val db = firestore ?: return@launch
        db.collection("templates").document(templateId).update("viewsCount", FieldValue.increment(1))
        if (creatorId.isNotBlank()) {
          db.collection("creators").document(creatorId).update("totalViews", FieldValue.increment(1))
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to increment viewsCount in Firestore: ${e.message}")
      }
    }
  }

  /**
   * Automatically increments template uses / cuts in Firebase Firestore.
   */
  fun recordTemplateUse(templateId: String, creatorId: String) {
    scope.launch {
      // 1. Update in-memory state immediately
      _templates.value = _templates.value.map {
        if (it.id == templateId) it.copy(usesCount = it.usesCount + 1) else it
      }
      if (creatorId == _creatorProfile.value.creatorId) {
        val updated = _creatorProfile.value.copy(totalUses = _creatorProfile.value.totalUses + 1)
        _creatorProfile.value = updated
        prefs?.edit()?.putLong("total_uses", updated.totalUses)?.apply()
      }

      // 2. Update Firestore
      try {
        val db = firestore ?: return@launch
        db.collection("templates").document(templateId).update("usesCount", FieldValue.increment(1))
        if (creatorId.isNotBlank()) {
          db.collection("creators").document(creatorId).update("totalUses", FieldValue.increment(1))
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to increment usesCount in Firestore: ${e.message}")
      }
    }
  }

  suspend fun deleteTemplate(templateId: String): Result<Unit> {
    return try {
      _templates.value = _templates.value.filterNot { it.id == templateId }
      val db = firestore
      if (db != null) {
        db.collection("templates").document(templateId).delete().await()
      }
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to delete template: ${e.message}", e)
      Result.failure(e)
    }
  }
}
