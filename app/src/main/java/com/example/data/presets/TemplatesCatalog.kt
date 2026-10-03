package com.example.data.presets

import com.example.domain.model.*

enum class PlaceholderType {
  VIDEO,
  IMAGE
}

data class MediaPlaceholder(
  val slotId: String,
  val label: String,
  val placeholderType: PlaceholderType,
  val requiredDurationMs: Long,
  val targetClipId: String,
  val defaultName: String = "Placeholder Media",
  val isOverlay: Boolean = false
)

data class TextPlaceholder(
  val slotId: String,
  val label: String,
  val targetClipId: String,
  val defaultText: String
)

data class VideoTemplate(
  val id: String,
  val title: String,
  val category: String,
  val description: String,
  val aspectRatio: AspectRatio,
  val resolution: Resolution = Resolution.RES_1080P,
  val fps: FrameRate = FrameRate.FPS_30,
  val durationMs: Long,
  val thumbnailGradientStart: Long = 0xFF1E293B,
  val thumbnailGradientEnd: Long = 0xFF0F172A,
  val iconEmoji: String = "🎬",
  val mediaPlaceholders: List<MediaPlaceholder> = emptyList(),
  val textPlaceholders: List<TextPlaceholder> = emptyList(),
  val audioTitle: String = "Soundtrack",
  val isPro: Boolean = false,
  val savedTimeline: Timeline? = null,
  val creatorId: String = "system_official",
  val creatorName: String = "Motion Studio",
  val creatorHandle: String = "@motionstudio",
  val creatorAvatarUrl: String? = null,
  val previewVideoUrl: String? = null,
  val previewThumbnailUrl: String? = null,
  val viewsCount: Long = 1240L,
  val usesCount: Long = 380L,
  val createdAt: Long = System.currentTimeMillis(),
  val createTimeline: (
    mediaReplacements: Map<String, String>,
    textReplacements: Map<String, String>
  ) -> Timeline = { _, _ -> savedTimeline ?: Timeline() }
) {
  fun createDefaultTimeline(): Timeline = savedTimeline ?: createTimeline(emptyMap(), emptyMap())
}

object TemplatesCatalog {
  val categories = listOf(
    "All",
    "Reels",
    "TikTok-style short videos",
    "YouTube",
    "YouTube Shorts",
    "Instagram",
    "Business",
    "Product Ads",
    "Birthday",
    "Wedding",
    "Travel",
    "Cinematic"
  )

  val templates: List<VideoTemplate> by lazy {
    listOf(
      // 1. Reels
      VideoTemplate(
        id = "reels_trending_vibe",
        title = "Dynamic Fast-Cut Reel",
        category = "Reels",
        description = "High-energy beat sync cuts with pop-in titles and vibrant pacing.",
        aspectRatio = AspectRatio.RATIO_9_16,
        durationMs = 9000L,
        thumbnailGradientStart = 0xFF833AB4,
        thumbnailGradientEnd = 0xFFFD1D1D,
        iconEmoji = "⚡",
        audioTitle = "Energetic Beat Drop",
        mediaPlaceholders = listOf(
          MediaPlaceholder("v1", "Opening Hook Clip", PlaceholderType.VIDEO, 2000L, "reels_vid_1"),
          MediaPlaceholder("v2", "Action Motion Clip", PlaceholderType.VIDEO, 2500L, "reels_vid_2"),
          MediaPlaceholder("i1", "Featured Photo", PlaceholderType.IMAGE, 2000L, "reels_img_1"),
          MediaPlaceholder("v3", "Climax Outro Clip", PlaceholderType.VIDEO, 2500L, "reels_vid_3")
        ),
        textPlaceholders = listOf(
          TextPlaceholder("t1", "Main Hook Title", "reels_txt_1", "TRENDING NOW"),
          TextPlaceholder("t2", "Call to Action", "reels_txt_2", "LINK IN BIO")
        ),
        createTimeline = { media, texts ->
          val t1Text = texts["t1"] ?: "TRENDING NOW"
          val t2Text = texts["t2"] ?: "LINK IN BIO"
          Timeline(
            videoClips = listOf(
              VideoClip(id = "reels_vid_1", name = "Hook", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 2000L),
              VideoClip(id = "reels_vid_2", name = "Action", uri = media["v2"] ?: "", timelineStartMs = 2000L, durationMs = 2500L),
              VideoClip(id = "reels_img_1", name = "Photo", uri = media["i1"] ?: "", timelineStartMs = 4500L, durationMs = 2000L, isVideo = false, keyframes = listOf(ClipKeyframe(id = "k1", timeMs = 0L, scale = 1.0f), ClipKeyframe(id = "k2", timeMs = 2000L, scale = 1.15f))),
              VideoClip(id = "reels_vid_3", name = "Outro", uri = media["v3"] ?: "", timelineStartMs = 6500L, durationMs = 2500L)
            ),
            textClips = listOf(
              TextClip(id = "reels_txt_1", text = t1Text, timelineStartMs = 500L, durationMs = 2500L, animationType = "Pop"),
              TextClip(id = "reels_txt_2", text = t2Text, timelineStartMs = 5000L, durationMs = 2000L, animationType = "Fade")
            ),
            audioClips = listOf(
              AudioClip(id = "reels_aud_1", title = "Energetic Beat Drop", uri = "internal://beat", timelineStartMs = 0L, durationMs = 9000L)
            ),
            transitions = listOf(
              Transition(id = "trans_1", clipIndexBefore = 0, durationMs = 500L, type = TransitionType.DISSOLVE)
            ),
            effectClips = listOf(
              EffectClip(id = "eff_1", effectType = EffectType.GLOW, timelineStartMs = 0L, durationMs = 9000L)
            )
          )
        }
      ),

      // 2. Cinematic
      VideoTemplate(
        id = "cinematic_letterbox_film",
        title = "Cinematic Story",
        category = "Cinematic",
        description = "Widescreen anamorphic cinematic visual with smooth transitions.",
        aspectRatio = AspectRatio.RATIO_16_9,
        durationMs = 10000L,
        thumbnailGradientStart = 0xFF000000,
        thumbnailGradientEnd = 0xFF434343,
        iconEmoji = "🎞️",
        audioTitle = "Cinematic Drone",
        mediaPlaceholders = listOf(
          MediaPlaceholder("v1", "Establishing Shot", PlaceholderType.VIDEO, 5000L, "cine_vid_1"),
          MediaPlaceholder("v2", "Character Close-up", PlaceholderType.VIDEO, 5000L, "cine_vid_2")
        ),
        textPlaceholders = listOf(
          TextPlaceholder("t1", "Film Title", "cine_txt_1", "THE HORIZON")
        ),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(
              VideoClip(id = "cine_vid_1", name = "Establishing", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 5000L),
              VideoClip(id = "cine_vid_2", name = "Character", uri = media["v2"] ?: "", timelineStartMs = 5000L, durationMs = 5000L)
            ),
            textClips = listOf(
              TextClip(id = "cine_txt_1", text = texts["t1"] ?: "THE HORIZON", timelineStartMs = 1000L, durationMs = 4000L)
            ),
            audioClips = listOf(
              AudioClip(id = "cine_aud_1", title = "Cinematic Drone", uri = "internal://drone", timelineStartMs = 0L, durationMs = 10000L)
            )
          )
        }
      ),

      // 3. TikTok
      VideoTemplate(
        id = "shorts_flash_cut",
        title = "Fast Viral Shorts",
        category = "TikTok-style short videos",
        description = "Quick punchy visual beats and high impact text placement.",
        aspectRatio = AspectRatio.RATIO_9_16,
        durationMs = 6000L,
        thumbnailGradientStart = 0xFF00E5FF,
        thumbnailGradientEnd = 0xFF7000FF,
        iconEmoji = "🔥",
        audioTitle = "Viral Beat",
        mediaPlaceholders = listOf(
          MediaPlaceholder("v1", "Punch In Clip", PlaceholderType.VIDEO, 3000L, "short_vid_1"),
          MediaPlaceholder("v2", "Reaction Clip", PlaceholderType.VIDEO, 3000L, "short_vid_2")
        ),
        textPlaceholders = listOf(
          TextPlaceholder("t1", "Reaction Text", "short_txt_1", "WAIT FOR IT...")
        ),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(
              VideoClip(id = "short_vid_1", name = "Punch", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 3000L),
              VideoClip(id = "short_vid_2", name = "Reaction", uri = media["v2"] ?: "", timelineStartMs = 3000L, durationMs = 3000L)
            ),
            textClips = listOf(
              TextClip(id = "short_txt_1", text = texts["t1"] ?: "WAIT FOR IT...", timelineStartMs = 500L, durationMs = 3000L, animationType = "Pop")
            ),
            audioClips = listOf(
              AudioClip(id = "short_aud_1", title = "Viral Beat", uri = "internal://viral", timelineStartMs = 0L, durationMs = 6000L)
            )
          )
        }
      ),

      // 4. YouTube
      VideoTemplate(
        id = "yt_channel_vlog_showcase",
        title = "YouTube Video Showcase",
        category = "YouTube",
        description = "Widescreen 16:9 dynamic layout for long-form YouTube creators and vloggers.",
        aspectRatio = AspectRatio.RATIO_16_9,
        durationMs = 15000L,
        thumbnailGradientStart = 0xFFFF0000,
        thumbnailGradientEnd = 0xFF282828,
        iconEmoji = "▶️",
        audioTitle = "Upbeat YouTube Background",
        mediaPlaceholders = listOf(
          MediaPlaceholder("v1", "Main Vlog Footage", PlaceholderType.VIDEO, 10000L, "yt_long_vid_1"),
          MediaPlaceholder("v2", "B-Roll Cutaway", PlaceholderType.VIDEO, 5000L, "yt_long_vid_2")
        ),
        textPlaceholders = listOf(
          TextPlaceholder("t1", "Video Headline", "yt_long_txt_1", "WELCOME TO MY CHANNEL"),
          TextPlaceholder("t2", "Outro Call to Action", "yt_long_txt_2", "LIKE & SUBSCRIBE")
        ),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(
              VideoClip(id = "yt_long_vid_1", name = "Vlog", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 10000L),
              VideoClip(id = "yt_long_vid_2", name = "B-Roll", uri = media["v2"] ?: "", timelineStartMs = 10000L, durationMs = 5000L)
            ),
            textClips = listOf(
              TextClip(id = "yt_long_txt_1", text = texts["t1"] ?: "WELCOME TO MY CHANNEL", timelineStartMs = 500L, durationMs = 4500L, animationType = "Pop"),
              TextClip(id = "yt_long_txt_2", text = texts["t2"] ?: "LIKE & SUBSCRIBE", timelineStartMs = 10500L, durationMs = 4000L, animationType = "Fade")
            ),
            audioClips = listOf(
              AudioClip(id = "yt_long_aud_1", title = "Upbeat YouTube Background", uri = "internal://youtube_bg", timelineStartMs = 0L, durationMs = 15000L)
            )
          )
        }
      ),

      // 5. YouTube Shorts
      VideoTemplate(
        id = "yt_shorts_intro",
        title = "YouTube Shorts Intro",
        category = "YouTube Shorts",
        description = "Engaging intro template for short form creators.",
        aspectRatio = AspectRatio.RATIO_9_16,
        durationMs = 8000L,
        mediaPlaceholders = listOf(MediaPlaceholder("v1", "Main Video", PlaceholderType.VIDEO, 8000L, "yt_vid_1")),
        textPlaceholders = listOf(TextPlaceholder("t1", "Title", "yt_txt_1", "SUBSCRIBE")),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(VideoClip(id = "yt_vid_1", name = "Main", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 8000L)),
            textClips = listOf(TextClip(id = "yt_txt_1", text = texts["t1"] ?: "SUBSCRIBE", timelineStartMs = 0L, durationMs = 3000L)),
            audioClips = listOf(AudioClip(id = "yt_aud_1", title = "Intro Track", uri = "internal://intro", timelineStartMs = 0L, durationMs = 8000L))
          )
        }
      ),

      // 5. Instagram
      VideoTemplate(
        id = "insta_story_vibe",
        title = "Instagram Story Promo",
        category = "Instagram",
        description = "Clean aesthetic story layout for modern brands.",
        aspectRatio = AspectRatio.RATIO_9_16,
        durationMs = 7000L,
        mediaPlaceholders = listOf(MediaPlaceholder("v1", "Story Media", PlaceholderType.VIDEO, 7000L, "insta_vid_1")),
        textPlaceholders = listOf(TextPlaceholder("t1", "Header", "insta_txt_1", "NEW COLLECTION")),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(VideoClip(id = "insta_vid_1", name = "Media", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 7000L)),
            textClips = listOf(TextClip(id = "insta_txt_1", text = texts["t1"] ?: "NEW COLLECTION", timelineStartMs = 0L, durationMs = 4000L)),
            audioClips = listOf(AudioClip(id = "insta_aud_1", title = "Chill Lofi", uri = "internal://chill", timelineStartMs = 0L, durationMs = 7000L))
          )
        }
      ),

      // 6. Business
      VideoTemplate(
        id = "biz_promo_clean",
        title = "Corporate Showcase",
        category = "Business",
        description = "Professional presentation for business launches and services.",
        aspectRatio = AspectRatio.RATIO_16_9,
        durationMs = 12000L,
        mediaPlaceholders = listOf(MediaPlaceholder("v1", "Company Demo", PlaceholderType.VIDEO, 12000L, "biz_vid_1")),
        textPlaceholders = listOf(TextPlaceholder("t1", "Company Name", "biz_txt_1", "ACME CORP")),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(VideoClip(id = "biz_vid_1", name = "Corporate", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 12000L)),
            textClips = listOf(TextClip(id = "biz_txt_1", text = texts["t1"] ?: "ACME CORP", timelineStartMs = 0L, durationMs = 5000L)),
            audioClips = listOf(AudioClip(id = "biz_aud_1", title = "Corporate Tech", uri = "internal://corp", timelineStartMs = 0L, durationMs = 12000L))
          )
        }
      ),

      // 7. Product Ads
      VideoTemplate(
        id = "product_ad_flash",
        title = "Product Launch Commercial",
        category = "Product Ads",
        description = "High impact product promo layout.",
        aspectRatio = AspectRatio.RATIO_1_1,
        durationMs = 10000L,
        mediaPlaceholders = listOf(MediaPlaceholder("v1", "Product Shot", PlaceholderType.VIDEO, 10000L, "prod_vid_1")),
        textPlaceholders = listOf(TextPlaceholder("t1", "Product Title", "prod_txt_1", "50% OFF TODAY")),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(VideoClip(id = "prod_vid_1", name = "Product", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 10000L)),
            textClips = listOf(TextClip(id = "prod_txt_1", text = texts["t1"] ?: "50% OFF TODAY", timelineStartMs = 0L, durationMs = 4000L)),
            audioClips = listOf(AudioClip(id = "prod_aud_1", title = "Upbeat Pop", uri = "internal://pop", timelineStartMs = 0L, durationMs = 10000L))
          )
        }
      ),

      // 8. Birthday
      VideoTemplate(
        id = "birthday_memories",
        title = "Happy Birthday Slideshow",
        category = "Birthday",
        description = "Festive slideshow with glitter and celebration titles.",
        aspectRatio = AspectRatio.RATIO_16_9,
        durationMs = 15000L,
        mediaPlaceholders = listOf(MediaPlaceholder("v1", "Party Clip", PlaceholderType.VIDEO, 15000L, "bday_vid_1")),
        textPlaceholders = listOf(TextPlaceholder("t1", "Greeting", "bday_txt_1", "HAPPY BIRTHDAY!")),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(VideoClip(id = "bday_vid_1", name = "Celebration", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 15000L)),
            textClips = listOf(TextClip(id = "bday_txt_1", text = texts["t1"] ?: "HAPPY BIRTHDAY!", timelineStartMs = 0L, durationMs = 5000L)),
            audioClips = listOf(AudioClip(id = "bday_aud_1", title = "Celebration Tune", uri = "internal://party", timelineStartMs = 0L, durationMs = 15000L))
          )
        }
      ),

      // 9. Wedding
      VideoTemplate(
        id = "wedding_romantic_story",
        title = "Romantic Wedding Highlights",
        category = "Wedding",
        description = "Soft romantic color grading and elegant typography.",
        aspectRatio = AspectRatio.RATIO_16_9,
        durationMs = 12000L,
        mediaPlaceholders = listOf(MediaPlaceholder("v1", "Couple Dance", PlaceholderType.VIDEO, 12000L, "wed_vid_1")),
        textPlaceholders = listOf(
          TextPlaceholder("t1", "Couple Names", "wed_txt_1", "Emma & Oliver"),
          TextPlaceholder("t2", "Wedding Date", "wed_txt_2", "JUNE 20, 2026")
        ),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(
              VideoClip(id = "wed_vid_1", name = "Dance", uri = media["v1"] ?: "file:///couple_dance.mp4", timelineStartMs = 0L, durationMs = 6000L),
              VideoClip(id = "wed_vid_2", name = "Ceremony", uri = media["v2"] ?: "file:///ceremony.mp4", timelineStartMs = 6000L, durationMs = 6000L)
            ),
            textClips = listOf(
              TextClip(id = "wed_txt_1", text = texts["t1"] ?: "Emma & Oliver", timelineStartMs = 0L, durationMs = 6000L),
              TextClip(id = "wed_txt_2", text = texts["t2"] ?: "JUNE 20, 2026", timelineStartMs = 6000L, durationMs = 6000L)
            ),
            audioClips = listOf(
              AudioClip(id = "wed_aud_1", title = "Emotional Piano & Orchestra", uri = "internal://wedding", timelineStartMs = 0L, durationMs = 12000L)
            )
          )
        }
      ),

      // 10. Travel
      VideoTemplate(
        id = "travel_vlog_intro",
        title = "Wanderlust Travel Vlog",
        category = "Travel",
        description = "Scenic landscape layout with fast transitions.",
        aspectRatio = AspectRatio.RATIO_16_9,
        durationMs = 10000L,
        mediaPlaceholders = listOf(MediaPlaceholder("v1", "Scenery Shot", PlaceholderType.VIDEO, 10000L, "travel_vid_1")),
        textPlaceholders = listOf(TextPlaceholder("t1", "Destination", "travel_txt_1", "EXPLORE BALI")),
        createTimeline = { media, texts ->
          Timeline(
            videoClips = listOf(VideoClip(id = "travel_vid_1", name = "Scenery", uri = media["v1"] ?: "", timelineStartMs = 0L, durationMs = 10000L)),
            textClips = listOf(TextClip(id = "travel_txt_1", text = texts["t1"] ?: "EXPLORE BALI", timelineStartMs = 0L, durationMs = 4000L)),
            audioClips = listOf(AudioClip(id = "travel_aud_1", title = "Tropical House", uri = "internal://tropical", timelineStartMs = 0L, durationMs = 10000L))
          )
        }
      )
    )
  }
}
