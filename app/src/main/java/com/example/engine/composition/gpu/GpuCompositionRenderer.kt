package com.example.engine.composition.gpu

import android.content.Context
import android.graphics.*
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.example.domain.model.*
import com.example.engine.KeyframeInterpolator
import com.example.engine.composition.ComposedFrame
import com.example.engine.composition.ComposedOverlay
import com.example.engine.composition.ComposedSticker
import com.example.engine.composition.ComposedText
import com.example.engine.composition.StickerLayerRenderer
import com.example.engine.composition.VideoEffectRenderer
import com.example.engine.text.TextLayerRenderer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max

/**
 * Production-Grade GPU Composition Renderer powered by Native C++ OpenGL ES 3.0 Engine.
 * Supports 1, 3, 10, 20, and 25+ simultaneous layers (Base Video, PIP Videos, Image Stickers,
 * Text Layers, Visual Effects) with 60 FPS performance, deterministic Z-ordering,
 * texture recycling, and premultiplied alpha blending.
 */
class GpuCompositionRenderer(private val context: Context) {
  private var nativeHandle: Long = 0L

  companion object {
    private const val TAG = "GpuCompositionRenderer"

    fun isProceduralOverlayEffect(type: EffectType): Boolean {
      return when (type) {
        EffectType.FIRE_SPARK,
        EffectType.LASER_GRID, EffectType.BACKGROUND_NEON_GRID,
        EffectType.MANGA_LINE, EffectType.AI_MANGA_UNIVERSE,
        EffectType.BODY_AURA, EffectType.FIRE_AURA,
        EffectType.NEON_OUTLINE, EffectType.GLOW_EYES,
        EffectType.ANGEL_WINGS, EffectType.CYBER_WINGS,
        EffectType.LIGHTNING_BODY, EffectType.AI_SPEED_FORCE,
        EffectType.HEART_TRAIL, EffectType.FLORAL_CROWN,
        EffectType.CYBER_FACE, EffectType.CYBER_VISOR,
        EffectType.NEON_SPARKLE_CHEEKS, EffectType.DRAGON_FLAME,
        EffectType.MUSCLE_GLOW, EffectType.GHOST_CLONE,
        EffectType.FUNNY_BIG_EYES, EffectType.DARK_SHADOW_AURA,
        EffectType.DOUBLE_EXPOSURE,
        EffectType.CELEBRATE_CONFETTI, EffectType.CELEBRATE_FIREWORKS,
        EffectType.STAMP_ART,
        EffectType.SCREEN_SWAP_HOLO, EffectType.FACE_SWAP_AI,
        EffectType.AI_CYBERPUNK_CITY, EffectType.AI_BG_SWAP,
        EffectType.AI_PARTICLE_DISPERSE, EffectType.AI_NEON_TRAIL,
        EffectType.AI_SCI_FI_PORTAL, EffectType.AI_FREEZE_TIME,
        EffectType.AI_LIQUID_GOLD, EffectType.AI_GOLDEN_GOD,
        EffectType.AI_EXPANSION,
        EffectType.AI_FANTASY_KINGDOM,
        EffectType.AI_STYLE_MORPH, EffectType.AI_ANIME_WORLD,
        EffectType.ANIME_SILHOUETTE, EffectType.SKELETON_XRAY,
        EffectType.FACE_BEAUTY, EffectType.SLIM_SHAPE,
        EffectType.WATERCOLOR, EffectType.CHARCOAL_DRAW,
        EffectType.PASTEL_DREAM, EffectType.COLOR_POP_SPLASH,
        EffectType.Y2K_CHROME, EffectType.DIGICAM_2004,
        EffectType.OLD_PAPER_TEXTURE -> true
        else -> type.name.startsWith("VFX_BODY_") || type.name.startsWith("VFX_AI_") || type.name.startsWith("VFX_STICKER_")
      }
    }

    fun isPostProcessShaderEffect(type: EffectType): Boolean {
      return when (type) {
        EffectType.BLUR, EffectType.SOFT_FOCUS, EffectType.SHARPEN,
        EffectType.VFX_BLUR_1, EffectType.VFX_BLUR_3, EffectType.VFX_BLUR_8,
        EffectType.VFX_BLUR_9, EffectType.VFX_BLUR_10, EffectType.VFX_BLUR_11,
        EffectType.VFX_BLUR_12, EffectType.VFX_BLUR_13, EffectType.VFX_BLUR_15,
        EffectType.MOTION_BLUR, EffectType.VFX_VIRAL_1, EffectType.VFX_VIRAL_14,
        EffectType.VFX_BLUR_2, EffectType.VFX_BLUR_4, EffectType.VFX_BLUR_5,
        EffectType.VFX_BLUR_6, EffectType.VFX_BLUR_7, EffectType.VFX_BLUR_14,
        EffectType.GLOW, EffectType.HALO_GLOW,
        EffectType.VFX_LIGHT_4, EffectType.VFX_LIGHT_7, EffectType.VFX_LIGHT_14,
        EffectType.VFX_LIGHT_18, EffectType.VFX_LIGHT_19, EffectType.VFX_VIRAL_21,
        EffectType.VFX_VIRAL_22,
        EffectType.SHAKE, EffectType.CAMERA_MOVEMENT, EffectType.PARTY_CONFUSED,
        EffectType.VFX_VIRAL_6, EffectType.VFX_VIRAL_15, EffectType.VFX_GLITCH_14,
        EffectType.VFX_GLITCH_16, EffectType.VFX_GLITCH_19,
        EffectType.ZOOM, EffectType.SKATER_ZOOM, EffectType.VERTIGO_DOLLY, EffectType.WARP_SPEED,
        EffectType.VFX_VIRAL_12, EffectType.VFX_VIRAL_16, EffectType.VFX_VIRAL_17,
        EffectType.VFX_VIRAL_35, EffectType.VFX_3D_9,
        EffectType.SPIN, EffectType.VFX_VIRAL_20, EffectType.VFX_3D_8, EffectType.VFX_3D_15,
        EffectType.FLASH, EffectType.STROBE, EffectType.VFX_VIRAL_11, EffectType.VFX_VIRAL_36,
        EffectType.VFX_LIGHT_16, EffectType.VFX_VIRAL_23,
        EffectType.GLITCH, EffectType.CRT_TV, EffectType.VHS_VINTAGE, EffectType.AI_GLITCH_REALITY,
        EffectType.VFX_GLITCH_1, EffectType.VFX_GLITCH_2, EffectType.VFX_GLITCH_3,
        EffectType.VFX_GLITCH_6, EffectType.VFX_GLITCH_7, EffectType.VFX_GLITCH_8,
        EffectType.VFX_GLITCH_9, EffectType.VFX_GLITCH_10, EffectType.VFX_GLITCH_11,
        EffectType.VFX_GLITCH_13, EffectType.VFX_GLITCH_17, EffectType.VFX_GLITCH_18,
        EffectType.VFX_GLITCH_20, EffectType.VFX_RETRO_1, EffectType.VFX_RETRO_10,
        EffectType.VFX_VIRAL_4, EffectType.VFX_VIRAL_30, EffectType.VFX_VIRAL_31,
        EffectType.RGB_SPLIT, EffectType.VFX_VIRAL_5, EffectType.VFX_VIRAL_29,
        EffectType.VFX_GLITCH_12,
        EffectType.DISTORTION, EffectType.WAVE, EffectType.RIPPLE, EffectType.FISHEYE,
        EffectType.ACID_TRIP, EffectType.FUNNY_ALIEN_WARP, EffectType.VFX_GLITCH_4,
        EffectType.VFX_GLITCH_5, EffectType.VFX_GLITCH_15, EffectType.VFX_VIRAL_8,
        EffectType.VFX_VIRAL_9, EffectType.VFX_3D_2,
        EffectType.LENS_FLARE, EffectType.SOLAR_FLARE, EffectType.VFX_LIGHT_2,
        EffectType.LIGHT_LEAK, EffectType.GOLDEN_HOUR, EffectType.BOKEH, EffectType.PARTY_PRISM,
        EffectType.VFX_LIGHT_1, EffectType.VFX_LIGHT_3, EffectType.VFX_LIGHT_5,
        EffectType.VFX_LIGHT_10, EffectType.VFX_LIGHT_17, EffectType.VFX_RETRO_7,
        EffectType.VFX_VIRAL_24,
        EffectType.VIGNETTE, EffectType.NOISE, EffectType.THERMAL_CAMERA,
        EffectType.BLUEPRINT_CAD, EffectType.POP_ART_POSTER, EffectType.SEPIA_VINTAGE,
        EffectType.POLAROID_VINTAGE, EffectType.OIL_PAINTING, EffectType.HALFTONE_DOT,
        EffectType.COMIC_SKETCH, EffectType.MIRROR -> true
        else -> type.name.startsWith("VFX_BLUR_") || type.name.startsWith("VFX_GLITCH_") ||
                type.name.startsWith("VFX_LIGHT_") || type.name.startsWith("VFX_RETRO_") ||
                type.name.startsWith("VFX_DISTORT_") || type.name.startsWith("VFX_COLOR_")
      }
    }
    private const val FLOAT_SIZE_BYTES = 4
    private const val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 4 * FLOAT_SIZE_BYTES
    private const val POSITION_DATA_OFFSET = 0
    private const val TEXTURE_DATA_OFFSET = 2
    private const val MAX_UNTOUCHED_CACHE_FRAMES = 60
  }

  // Full-screen quad geometry: (x, y, u, v)
  private val quadVertices = floatArrayOf(
    -1.0f, -1.0f,  0.0f, 0.0f,
     1.0f, -1.0f,  1.0f, 0.0f,
    -1.0f,  1.0f,  0.0f, 1.0f,
     1.0f,  1.0f,  1.0f, 1.0f
  )

  private val vertexBuffer: FloatBuffer = ByteBuffer
    .allocateDirect(quadVertices.size * FLOAT_SIZE_BYTES)
    .order(ByteOrder.nativeOrder())
    .asFloatBuffer()
    .apply {
      put(quadVertices)
      position(0)
    }

  // OpenGL Programs for OES conversion & post-process effects
  private var program2D = 0
  private var programOes = 0
  private var programTransition = 0
  private var programEffect = 0
  private var programBlit = 0

  // Framebuffers for OES conversion & multi-pass effect rendering
  private val fboMain2D = GlFramebuffer()
  private val fboOverlayMap = mutableMapOf<String, GlFramebuffer>()
  private val fboA = GlFramebuffer()
  private val fboB = GlFramebuffer()

  // Cached Text & Sticker textures with frame-access tracking
  internal data class CachedTexture(
    val texId: Int,
    val width: Int,
    val height: Int,
    val hash: Int,
    var lastFrameUsed: Long = 0L
  )

  private val textTextureCache = mutableMapOf<String, CachedTexture>()
  private val stickerTextureCache = mutableMapOf<String, CachedTexture>()
  private val imageTextureCache = mutableMapOf<String, CachedTexture>()
  private val proceduralEffectCache = mutableMapOf<String, CachedTexture>()
  private var proceduralBitmap: Bitmap? = null
  private var proceduralCanvas: Canvas? = null

  private var currentFrameCounter = 0L

  // Reusable Matrix buffers
  private val mvpMatrix = FloatArray(16)
  private val texMatrix = FloatArray(16)

  private var isInitialized = false
  private var currentViewportWidth = 0
  private var currentViewportHeight = 0

  fun initGl() {
    if (isInitialized) return

    program2D = GlShaderUtil.createProgram(GpuShaders.VERTEX_SHADER, GpuShaders.buildFragmentShader(isOes = false))
    programOes = GlShaderUtil.createProgram(GpuShaders.VERTEX_SHADER, GpuShaders.buildFragmentShader(isOes = true))
    programTransition = GlShaderUtil.createProgram(GpuShaders.VERTEX_SHADER, GpuShaders.TRANSITION_FRAGMENT_SHADER)
    programEffect = GlShaderUtil.createProgram(GpuShaders.VERTEX_SHADER, GpuShaders.EFFECT_FRAGMENT_SHADER)
    programBlit = GlShaderUtil.createProgram(GpuShaders.ENCODER_BLIT_VERTEX_SHADER, GpuShaders.BLIT_FRAGMENT_SHADER)

    isInitialized = true
  }

  /**
   * Main GPU composition entry point:
   * Converts OES video frames, prepares text/sticker/overlay textures, converts layer models into
   * native C++ layer representations, and renders the composed frame via NativeRenderBridge.
   */
  fun render(
    frame: ComposedFrame,
    mainTextureId: Int,
    isMainOes: Boolean,
    mainTexMatrix: FloatArray? = null,
    overlayTextures: Map<String, Int> = emptyMap(),
    overlayTexMatrices: Map<String, FloatArray> = emptyMap(),
    viewportWidth: Int,
    viewportHeight: Int,
    timelineAdjustments: VideoAdjustments = VideoAdjustments(),
    timelineFilter: FilterSettings = FilterSettings(),
    chromaKey: ChromaKeySettings = ChromaKeySettings(),
    flipYForEncoder: Boolean = false,
    flipXForEncoder: Boolean = false
  ) {
    if (viewportWidth <= 0 || viewportHeight <= 0) return

    if (!isInitialized) {
      initGl()
    }

    currentFrameCounter++

    // Initialize or resize native C++ OpenGL ES 3.0 renderer
    if (viewportWidth != currentViewportWidth || viewportHeight != currentViewportHeight || nativeHandle == 0L) {
      currentViewportWidth = viewportWidth
      currentViewportHeight = viewportHeight
      if (nativeHandle == 0L && NativeRenderBridge.isLoaded) {
        nativeHandle = NativeRenderBridge.init(viewportWidth, viewportHeight)
      } else if (nativeHandle != 0L) {
        NativeRenderBridge.resize(nativeHandle, viewportWidth, viewportHeight)
      }
    }

    val nativeLayers = mutableListOf<NativeLayer>()

    val fxColorMatrix = if (frame.activeEffects.isNotEmpty()) {
      VideoEffectRenderer.calculateEffectColorMatrix(
        frame.activeEffects.map { it.clip },
        frame.timelinePosMs
      )
    } else null

    // 1. Process Main Base Video Clip
    if (mainTextureId > 0) {
      val main2dTexId = processMainVideoTo2D(
        frame = frame,
        textureId = mainTextureId,
        isOes = isMainOes,
        customTexMatrix = mainTexMatrix,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        adjustments = timelineAdjustments,
        filter = timelineFilter,
        chromaKey = chromaKey,
        effectColorMatrix = fxColorMatrix
      )

      if (main2dTexId > 0) {
        val baseLayer = NativeLayer(
          id = frame.activeClip?.id?.hashCode()?.toLong() ?: 1L,
          textureId = main2dTexId,
          type = NativeLayerType.BASE_VIDEO,
          isVisible = true,
          zOrder = 0,
          opacity = 1.0f,
          blendMode = NativeBlendMode.NORMAL,
          useCustomMatrix = false
        )
        nativeLayers.add(baseLayer)
      }
    }

    // 2. Process PIP Overlays (Deterministically ordered)
    for (i in frame.activeOverlays.indices) {
      val overlay = frame.activeOverlays[i]
      val overlayTexId = overlayTextures[overlay.clip.id]
      if (overlayTexId != null && overlayTexId > 0) {
        val isOvOes = overlay.clip.isVideo
        val ov2dTexId = processOverlayVideoTo2D(
          overlay = overlay,
          textureId = overlayTexId,
          isOes = isOvOes,
          customTexMatrix = overlayTexMatrices[overlay.clip.id],
          viewportWidth = viewportWidth,
          viewportHeight = viewportHeight,
          chromaKey = chromaKey,
          effectColorMatrix = fxColorMatrix
        )

        if (ov2dTexId > 0) {
          val ovNaturalRot = if (isOvOes) overlay.clip.naturalRotation else 0
          val isRawTransposed = (ovNaturalRot == 90 || ovNaturalRot == 270)
          val rawW = if (overlay.clip.width > 0) overlay.clip.width else viewportWidth
          val rawH = if (overlay.clip.height > 0) overlay.clip.height else viewportHeight
          val ovDisplayW = if (isRawTransposed) rawH else rawW
          val ovDisplayH = if (isRawTransposed) rawW else rawH
          val ovDisplayAspect = ovDisplayW.toFloat() / max(1, ovDisplayH)
          val vpAspect = viewportWidth.toFloat() / max(1, viewportHeight)

          val ovScreenFitX: Float
          val ovScreenFitY: Float
          if (ovDisplayAspect > vpAspect) {
            ovScreenFitX = 1.0f
            ovScreenFitY = vpAspect / ovDisplayAspect
          } else {
            ovScreenFitX = ovDisplayAspect / vpAspect
            ovScreenFitY = 1.0f
          }

          val flipX = if (overlay.clip.flipHorizontal) -overlay.clip.cropScale else overlay.clip.cropScale
          val flipY = if (overlay.clip.flipVertical) -overlay.clip.cropScale else overlay.clip.cropScale

          val totalRot = ((ovNaturalRot.toFloat() + overlay.clip.rotationDegrees.toFloat() + overlay.rotation) % 360f + 360f) % 360f
          val isTransposed = (totalRot == 90f || totalRot == 270f)

          val baseScaleX = ovScreenFitX * overlay.scaleX * 0.5f * flipX
          val baseScaleY = ovScreenFitY * overlay.scaleY * 0.5f * flipY

          val localScaleX = if (isTransposed) baseScaleY else baseScaleX
          val localScaleY = if (isTransposed) baseScaleX else baseScaleY

          val ovMatrix = FloatArray(16)
          Matrix.setIdentityM(ovMatrix, 0)
          Matrix.translateM(ovMatrix, 0, overlay.clip.cropOffsetX + overlay.posX, -(overlay.clip.cropOffsetY + overlay.posY), 0f)
          Matrix.rotateM(ovMatrix, 0, -totalRot, 0f, 0f, 1f)
          Matrix.scaleM(ovMatrix, 0, localScaleX, localScaleY, 1f)

          val calculatedZ = 100 + (i * 10)
          val overlayLayer = NativeLayer(
            id = overlay.clip.id.hashCode().toLong(),
            textureId = ov2dTexId,
            type = NativeLayerType.VIDEO,
            isVisible = true,
            zOrder = calculatedZ,
            opacity = overlay.opacity.coerceIn(0f, 1f),
            blendMode = mapBlendMode(overlay.blendMode),
            useCustomMatrix = true,
            transformMatrix = ovMatrix
          )
          nativeLayers.add(overlayLayer)
        }
      }
    }

    // 2.5. Process Procedural Visual Effects Overlay (Canvas drawing, Particles, Wings, Confetti, Sparks, Grids)
    val proceduralOverlayEffects = frame.activeEffects.filter { isProceduralOverlayEffect(it.effectType) }
    if (proceduralOverlayEffects.isNotEmpty()) {
      val fxCached = getOrCreateProceduralEffectTexture(frame, viewportWidth, viewportHeight)
      if (fxCached != null && fxCached.texId > 0) {
        fxCached.lastFrameUsed = currentFrameCounter
        val fxMatrix = FloatArray(16)
        Matrix.setIdentityM(fxMatrix, 0)
        val fxLayer = NativeLayer(
          id = 99998888L,
          textureId = fxCached.texId,
          type = NativeLayerType.VIDEO,
          isVisible = true,
          zOrder = 450,
          opacity = 1.0f,
          vScale = -1.0f,
          vOffset = 1.0f,
          blendMode = NativeBlendMode.PREMULTIPLIED,
          useCustomMatrix = true,
          transformMatrix = fxMatrix
        )
        nativeLayers.add(fxLayer)
      }
    }

    // 3. Process Sticker Layers (Deterministically ordered)
    for (i in frame.activeStickers.indices) {
      val sticker = frame.activeStickers[i]
      val cached = getOrCreateStickerTexture(sticker.clip, viewportWidth, viewportHeight)
      if (cached != null && cached.texId > 0) {
        cached.lastFrameUsed = currentFrameCounter
        val aspect = viewportWidth.toFloat() / max(1, viewportHeight)
        val stickerAspect = cached.width.toFloat() / max(1, cached.height)
        val scaleY = ((cached.height.toFloat() / viewportHeight) * 2f * sticker.scale).coerceAtLeast(0.01f)
        val scaleX = (scaleY * stickerAspect / aspect).coerceAtLeast(0.01f)

        val stkMatrix = FloatArray(16)
        Matrix.setIdentityM(stkMatrix, 0)
        Matrix.translateM(stkMatrix, 0, sticker.posX, -sticker.posY, 0f)
        Matrix.rotateM(stkMatrix, 0, -sticker.rotation, 0f, 0f, 1f)
        Matrix.scaleM(stkMatrix, 0, scaleX, scaleY, 1f)

        val calculatedZ = 500 + (i * 10)
        val stickerLayer = NativeLayer(
          id = sticker.clip.id.hashCode().toLong(),
          textureId = cached.texId,
          type = NativeLayerType.IMAGE_STICKER,
          isVisible = true,
          zOrder = calculatedZ,
          opacity = sticker.opacity.coerceIn(0f, 1f),
          vScale = -1.0f,
          vOffset = 1.0f,
          blendMode = NativeBlendMode.PREMULTIPLIED,
          useCustomMatrix = true,
          transformMatrix = stkMatrix
        )
        nativeLayers.add(stickerLayer)
      }
    }

    // 4. Process Text Layers (Deterministically ordered with highest Z-Order to guarantee visibility)
    for (i in frame.activeTexts.indices) {
      val text = frame.activeTexts[i]
      val cached = getOrCreateTextTexture(text.clip, text.currentPosMs, viewportWidth, viewportHeight)
      if (cached != null && cached.texId > 0) {
        cached.lastFrameUsed = currentFrameCounter

        // TextLayerRenderer draws text onto a full viewport bitmap at exact coordinates and scale.
        // Identity matrix maps the full-viewport texture 1:1 onto the GPU framebuffer.
        val txtMatrix = FloatArray(16)
        Matrix.setIdentityM(txtMatrix, 0)

        val calculatedZ = 1000 + (text.clip.trackIndex * 10) + i
        val textLayer = NativeLayer(
          id = text.clip.id.hashCode().toLong(),
          textureId = cached.texId,
          type = NativeLayerType.TEXT,
          isVisible = true,
          zOrder = calculatedZ,
          opacity = 1.0f,
          vScale = -1.0f,
          vOffset = 1.0f,
          blendMode = NativeBlendMode.PREMULTIPLIED,
          useCustomMatrix = true,
          transformMatrix = txtMatrix
        )
        nativeLayers.add(textLayer)
      }
    }

    // 5. Clean up stale textures periodically
    if (currentFrameCounter % 30L == 0L) {
      cleanStaleTextureCaches()
    }

    // 6. Render via Native C++ OpenGL ES 3.0 Engine or Kotlin OpenGL ES Fallback Compositor
    val postProcessEffects = frame.activeEffects.filter { isPostProcessShaderEffect(it.effectType) }
    val hasPostProcess = postProcessEffects.isNotEmpty()
    val isNativeActive = (nativeHandle != 0L)

    fboA.setup(viewportWidth, viewportHeight)
    fboA.bind()
    GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    if (chromaKey.enabled && chromaKey.backgroundType == "Transparent") {
      GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
    } else {
      GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    }
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

    if (isNativeActive) {
      NativeRenderBridge.renderFrame(nativeHandle, nativeLayers)
    } else {
      renderNativeLayersKotlin(nativeLayers, viewportWidth, viewportHeight)
    }
    fboA.unbind()

    // 7. Apply Active Visual Effects (Multi-pass ping-ponging)
    var currentInputTex = if (isNativeActive) NativeRenderBridge.endOffscreen(nativeHandle) else fboA.getTextureId()
    if (currentInputTex <= 0) currentInputTex = fboA.getTextureId()

    if (hasPostProcess && currentInputTex > 0) {
      GLES20.glDisable(GLES20.GL_BLEND)
      fboB.setup(viewportWidth, viewportHeight)
      var currentOutputFbo = fboB

      for (i in postProcessEffects.indices) {
        val effect = postProcessEffects[i]
        currentOutputFbo.bind()
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_BLEND)
        applyEffect(
          effectType = effect.effectType,
          intensity = effect.intensity,
          timeSec = effect.timeInEffectMs / 1000f,
          inputTexId = currentInputTex,
          viewportWidth = viewportWidth,
          viewportHeight = viewportHeight
        )
        currentOutputFbo.unbind()
        currentInputTex = currentOutputFbo.getTextureId()
        currentOutputFbo = if (currentOutputFbo == fboB) fboA else fboB
      }
    }

    // 8. Final Blit Pass to Framebuffer 0 (MediaCodec encoder surface or display surface)
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
    GLES20.glDisable(GLES20.GL_BLEND)
    blitToSurface(currentInputTex, flipY = flipYForEncoder, flipX = flipXForEncoder)
  }

  private fun renderNativeLayersKotlin(
    layers: List<NativeLayer>,
    viewportWidth: Int,
    viewportHeight: Int
  ) {
    if (layers.isEmpty() || program2D == 0) return
    GLES20.glUseProgram(program2D)

    val uMVPMatrixHandle = GLES20.glGetUniformLocation(program2D, "uMVPMatrix")
    val uTexMatrixHandle = GLES20.glGetUniformLocation(program2D, "uTexMatrix")
    val uTextureHandle = GLES20.glGetUniformLocation(program2D, "uTexture")
    val uOpacityHandle = GLES20.glGetUniformLocation(program2D, "uOpacity")
    val uBrightnessHandle = GLES20.glGetUniformLocation(program2D, "uBrightness")
    val uContrastHandle = GLES20.glGetUniformLocation(program2D, "uContrast")
    val uSaturationHandle = GLES20.glGetUniformLocation(program2D, "uSaturation")
    val uExposureHandle = GLES20.glGetUniformLocation(program2D, "uExposure")
    val uTemperatureHandle = GLES20.glGetUniformLocation(program2D, "uTemperature")
    val uTintHandle = GLES20.glGetUniformLocation(program2D, "uTint")
    val uHighlightsHandle = GLES20.glGetUniformLocation(program2D, "uHighlights")
    val uShadowsHandle = GLES20.glGetUniformLocation(program2D, "uShadows")
    val uVignetteHandle = GLES20.glGetUniformLocation(program2D, "uVignette")
    val uGrainHandle = GLES20.glGetUniformLocation(program2D, "uGrain")
    val uSharpnessHandle = GLES20.glGetUniformLocation(program2D, "uSharpness")
    val uTexelSizeHandle = GLES20.glGetUniformLocation(program2D, "uTexelSize")
    val uChromaEnabledHandle = GLES20.glGetUniformLocation(program2D, "uChromaEnabled")
    val uBlurHandle = GLES20.glGetUniformLocation(program2D, "uBlur")
    val uEffectParamHandle = GLES20.glGetUniformLocation(program2D, "uEffectParam")

    if (uBrightnessHandle >= 0) GLES20.glUniform1f(uBrightnessHandle, 0f)
    if (uContrastHandle >= 0) GLES20.glUniform1f(uContrastHandle, 1f)
    if (uSaturationHandle >= 0) GLES20.glUniform1f(uSaturationHandle, 1f)
    if (uExposureHandle >= 0) GLES20.glUniform1f(uExposureHandle, 0f)
    if (uTemperatureHandle >= 0) GLES20.glUniform1f(uTemperatureHandle, 0f)
    if (uTintHandle >= 0) GLES20.glUniform1f(uTintHandle, 0f)
    if (uHighlightsHandle >= 0) GLES20.glUniform1f(uHighlightsHandle, 0f)
    if (uShadowsHandle >= 0) GLES20.glUniform1f(uShadowsHandle, 0f)
    if (uVignetteHandle >= 0) GLES20.glUniform1f(uVignetteHandle, 0f)
    if (uGrainHandle >= 0) GLES20.glUniform1f(uGrainHandle, 0f)
    if (uSharpnessHandle >= 0) GLES20.glUniform1f(uSharpnessHandle, 0f)
    if (uTexelSizeHandle >= 0) GLES20.glUniform2f(uTexelSizeHandle, 1.0f / max(1, viewportWidth), 1.0f / max(1, viewportHeight))
    if (uChromaEnabledHandle >= 0) GLES20.glUniform1i(uChromaEnabledHandle, 0)
    if (uBlurHandle >= 0) GLES20.glUniform1f(uBlurHandle, 0f)
    if (uEffectParamHandle >= 0) GLES20.glUniform1f(uEffectParamHandle, 0f)

    val sortedLayers = layers.filter { it.isVisible && it.textureId > 0 }.sortedBy { it.zOrder }

    for ((idx, layer) in sortedLayers.withIndex()) {
      if (idx == 0 && (layer.type == NativeLayerType.BASE_VIDEO || layer.type == NativeLayerType.VIDEO)) {
        GLES20.glDisable(GLES20.GL_BLEND)
      } else {
        GLES20.glEnable(GLES20.GL_BLEND)
        when (layer.blendMode) {
          NativeBlendMode.ADDITIVE -> GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
          NativeBlendMode.MULTIPLY -> GLES20.glBlendFunc(GLES20.GL_DST_COLOR, GLES20.GL_ONE_MINUS_SRC_ALPHA)
          NativeBlendMode.SCREEN -> GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_COLOR)
          NativeBlendMode.PREMULTIPLIED -> GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
          else -> {
            if (layer.type == NativeLayerType.TEXT || layer.type == NativeLayerType.IMAGE_STICKER) {
              GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            } else {
              GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            }
          }
        }
      }

      val mMatrix = FloatArray(16)
      if (layer.useCustomMatrix && layer.transformMatrix != null) {
        System.arraycopy(layer.transformMatrix, 0, mMatrix, 0, 16)
      } else {
        Matrix.setIdentityM(mMatrix, 0)
        Matrix.translateM(mMatrix, 0, layer.posX, -layer.posY, 0f)
        Matrix.rotateM(mMatrix, 0, -layer.rotation, 0f, 0f, 1f)
        Matrix.scaleM(mMatrix, 0, layer.scaleX, layer.scaleY, 1f)
      }
      GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mMatrix, 0)

      val tMatrix = FloatArray(16)
      Matrix.setIdentityM(tMatrix, 0)
      if (layer.uOffset != 0f || layer.vOffset != 0f || layer.uScale != 1f || layer.vScale != 1f) {
        Matrix.translateM(tMatrix, 0, layer.uOffset, layer.vOffset, 0f)
        Matrix.scaleM(tMatrix, 0, layer.uScale, layer.vScale, 1f)
      }
      GLES20.glUniformMatrix4fv(uTexMatrixHandle, 1, false, tMatrix, 0)

      GLES20.glUniform1f(uOpacityHandle, layer.opacity.coerceIn(0f, 1f))

      GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
      GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, layer.textureId)
      GLES20.glUniform1i(uTextureHandle, 0)

      drawQuad(program2D)
    }

    GLES20.glDisable(GLES20.GL_BLEND)
  }

  private fun processMainVideoTo2D(
    frame: ComposedFrame,
    textureId: Int,
    isOes: Boolean,
    customTexMatrix: FloatArray?,
    viewportWidth: Int,
    viewportHeight: Int,
    adjustments: VideoAdjustments,
    filter: FilterSettings,
    chromaKey: ChromaKeySettings,
    effectColorMatrix: android.graphics.ColorMatrix? = null
  ): Int {
    fboMain2D.setup(viewportWidth, viewportHeight)
    fboMain2D.bind()

    GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

    val program = if (isOes) programOes else program2D
    GLES20.glUseProgram(program)

    Matrix.setIdentityM(mvpMatrix, 0)
    val clip = frame.activeClip
    var keyframeBlur = 0f
    var keyframeEffectParam = 0f
    var finalAdjustments = adjustments
    var finalOpacity = 1.0f

    if (clip != null) {
      val kf = frame.activeClipTransform ?: KeyframeInterpolator.interpolate(clip, frame.timelinePosMs - clip.timelineStartMs)

      val naturalRot = if (isOes) clip.naturalRotation else 0
      val isRawTransposed = (naturalRot == 90 || naturalRot == 270)
      val rawW = if (clip.width > 0) clip.width else viewportWidth
      val rawH = if (clip.height > 0) clip.height else viewportHeight
      val effW = if (isRawTransposed) rawH else rawW
      val effH = if (isRawTransposed) rawW else rawH

      val cachedMain = imageTextureCache.values.find { it.texId == textureId }
      val displayW = cachedMain?.width ?: effW
      val displayH = cachedMain?.height ?: effH
      val displayAspect = displayW.toFloat() / max(1, displayH)
      val vpAspect = viewportWidth.toFloat() / max(1, viewportHeight)

      val screenFitX: Float
      val screenFitY: Float
      if (displayAspect > vpAspect) {
        screenFitX = 1.0f
        screenFitY = vpAspect / displayAspect
      } else {
        screenFitX = displayAspect / vpAspect
        screenFitY = 1.0f
      }

      val userScaleX = (if (clip.flipHorizontal) -clip.cropScale else clip.cropScale) * kf.scaleX
      val userScaleY = (if (clip.flipVertical) -clip.cropScale else clip.cropScale) * kf.scaleY

      val totalRot = ((naturalRot.toFloat() + clip.rotationDegrees.toFloat() + kf.rotation) % 360f + 360f) % 360f
      val isTransposed = (totalRot == 90f || totalRot == 270f)

      val localScaleX = (if (isTransposed) screenFitY else screenFitX) * userScaleX
      val localScaleY = (if (isTransposed) screenFitX else screenFitY) * userScaleY

      Matrix.translateM(mvpMatrix, 0, clip.cropOffsetX + kf.posX, -(clip.cropOffsetY + kf.posY), 0f)
      Matrix.rotateM(mvpMatrix, 0, -totalRot, 0f, 0f, 1f)
      Matrix.scaleM(mvpMatrix, 0, localScaleX, localScaleY, 1f)

      finalOpacity *= kf.opacity
      keyframeBlur = kf.blur
      keyframeEffectParam = kf.effectParam
      finalAdjustments = adjustments.copy(
        brightness = (adjustments.brightness + kf.brightness).coerceIn(-1f, 1f),
        contrast = (adjustments.contrast * kf.contrast).coerceAtLeast(0f),
        saturation = (adjustments.saturation * kf.saturation).coerceAtLeast(0f)
      )
    }

    if (frame.activeEffects.isNotEmpty()) {
      val motion = VideoEffectRenderer.calculateMotionTransform(frame.activeEffects.map { it.clip }, frame.timelinePosMs)
      Matrix.scaleM(mvpMatrix, 0, motion.scaleX, motion.scaleY, 1f)
      Matrix.rotateM(mvpMatrix, 0, -motion.rotation, 0f, 0f, 1f)
      Matrix.translateM(mvpMatrix, 0, motion.translationX * 2f, -motion.translationY * 2f, 0f)
      finalOpacity *= motion.alpha
    }

    if (customTexMatrix != null) {
      System.arraycopy(customTexMatrix, 0, texMatrix, 0, 16)
    } else {
      Matrix.setIdentityM(texMatrix, 0)
      if (!isOes) {
        Matrix.translateM(texMatrix, 0, 0f, 1f, 0f)
        Matrix.scaleM(texMatrix, 0, 1f, -1f, 1f)
      }
    }

    if (frame.activeTransition != null) {
      val tr = frame.activeTransition
      val p = tr.progress.coerceIn(0f, 1f)
      when (tr.type) {
        TransitionType.FADE, TransitionType.DISSOLVE -> {
          finalOpacity = (finalOpacity * (1.0f - p)).coerceIn(0f, 1f)
        }
        TransitionType.SLIDE_LEFT -> {
          Matrix.translateM(mvpMatrix, 0, -p * 2.0f, 0f, 0f)
        }
        TransitionType.SLIDE_RIGHT -> {
          Matrix.translateM(mvpMatrix, 0, p * 2.0f, 0f, 0f)
        }
        TransitionType.PUSH_UP -> {
          Matrix.translateM(mvpMatrix, 0, 0f, p * 2.0f, 0f)
        }
        TransitionType.ZOOM_IN -> {
          val zoom = 1.0f + p * 0.6f
          Matrix.scaleM(mvpMatrix, 0, zoom, zoom, 1f)
          finalOpacity = (finalOpacity * (1.0f - p * 0.4f)).coerceIn(0f, 1f)
        }
        TransitionType.ZOOM_OUT -> {
          val zoom = (1.0f - p * 0.4f).coerceAtLeast(0.1f)
          Matrix.scaleM(mvpMatrix, 0, zoom, zoom, 1f)
          finalOpacity = (finalOpacity * (1.0f - p * 0.4f)).coerceIn(0f, 1f)
        }
        TransitionType.SPIN -> {
          Matrix.rotateM(mvpMatrix, 0, p * 360f, 0f, 0f, 1f)
          val zoom = (1.0f - p * 0.5f).coerceAtLeast(0.1f)
          Matrix.scaleM(mvpMatrix, 0, zoom, zoom, 1f)
          finalOpacity = (finalOpacity * (1.0f - p)).coerceIn(0f, 1f)
        }
        TransitionType.BLUR, TransitionType.ZOOM_BLUR -> {
          keyframeBlur = (keyframeBlur + (1.0f - kotlin.math.abs(p - 0.5f) * 2.0f) * 0.04f).coerceIn(0f, 1f)
          finalOpacity = (finalOpacity * (1.0f - p)).coerceIn(0f, 1f)
        }
        TransitionType.FLASH -> {
          val flashIntensity = (1.0f - kotlin.math.abs(p - 0.5f) * 2.0f).coerceIn(0f, 1f)
          finalAdjustments = finalAdjustments.copy(
            brightness = (finalAdjustments.brightness + flashIntensity * 0.8f).coerceIn(-1f, 1f),
            exposure = (finalAdjustments.exposure + flashIntensity * 0.6f).coerceIn(-1f, 1f)
          )
          if (p >= 0.5f) finalOpacity = (finalOpacity * (1.0f - (p - 0.5f) * 2f)).coerceIn(0f, 1f)
        }
        TransitionType.GLITCH, TransitionType.GLITCH_WIPE -> {
          if (p in 0.15f..0.85f) {
            val jitterX = ((Math.random() - 0.5) * 0.1).toFloat()
            val jitterY = ((Math.random() - 0.5) * 0.05).toFloat()
            Matrix.translateM(mvpMatrix, 0, jitterX, jitterY, 0f)
          }
          finalOpacity = (finalOpacity * (1.0f - p)).coerceIn(0f, 1f)
        }
        TransitionType.WHIP_PAN -> {
          Matrix.translateM(mvpMatrix, 0, -p * 2.5f, 0f, 0f)
          keyframeBlur = (keyframeBlur + kotlin.math.sin(p * 3.14159f) * 0.03f).coerceIn(0f, 1f)
        }
        TransitionType.WIPE -> {
          Matrix.translateM(mvpMatrix, 0, -p * 1.5f, 0f, 0f)
          finalOpacity = (finalOpacity * (1.0f - p * 0.5f)).coerceIn(0f, 1f)
        }
        TransitionType.LIGHT_LEAK -> {
          val flare = kotlin.math.sin(p * 3.14159f).coerceIn(0f, 1f)
          finalAdjustments = finalAdjustments.copy(
            brightness = (finalAdjustments.brightness + flare * 0.4f).coerceIn(-1f, 1f),
            temperature = (finalAdjustments.temperature + flare * 0.5f).coerceIn(-1f, 1f)
          )
          finalOpacity = (finalOpacity * (1.0f - p)).coerceIn(0f, 1f)
        }
        else -> {}
      }
    }

    val effectiveFilter = clip?.filter ?: FilterSettings()
    bindCommonUniforms(
      program = program,
      textureId = textureId,
      isOes = isOes,
      opacity = finalOpacity,
      adjustments = finalAdjustments,
      filter = effectiveFilter,
      chromaKey = chromaKey,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight,
      blur = keyframeBlur,
      effectParam = keyframeEffectParam,
      effectColorMatrix = effectColorMatrix
    )

    drawQuad(program)
    fboMain2D.unbind()

    return fboMain2D.getTextureId()
  }

  private fun processOverlayVideoTo2D(
    overlay: ComposedOverlay,
    textureId: Int,
    isOes: Boolean,
    customTexMatrix: FloatArray? = null,
    viewportWidth: Int,
    viewportHeight: Int,
    chromaKey: ChromaKeySettings,
    effectColorMatrix: android.graphics.ColorMatrix? = null
  ): Int {
    val fbo = fboOverlayMap.getOrPut(overlay.clip.id) { GlFramebuffer() }
    fbo.setup(viewportWidth, viewportHeight)
    fbo.bind()

    GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

    val program = if (isOes) programOes else program2D
    GLES20.glUseProgram(program)

    Matrix.setIdentityM(mvpMatrix, 0)
    if (customTexMatrix != null) {
      System.arraycopy(customTexMatrix, 0, texMatrix, 0, 16)
    } else {
      Matrix.setIdentityM(texMatrix, 0)
      if (!isOes) {
        Matrix.translateM(texMatrix, 0, 0f, 1f, 0f)
        Matrix.scaleM(texMatrix, 0, 1f, -1f, 1f)
      }
    }

    val overlayAdj = VideoAdjustments(
      brightness = overlay.brightness,
      contrast = overlay.contrast,
      saturation = overlay.saturation
    )

    bindCommonUniforms(
      program = program,
      textureId = textureId,
      isOes = isOes,
      opacity = 1.0f,
      adjustments = overlayAdj,
      filter = overlay.clip.filter ?: FilterSettings(),
      chromaKey = chromaKey,
      viewportWidth = viewportWidth,
      viewportHeight = viewportHeight,
      blur = overlay.blur,
      effectParam = overlay.effectParam,
      effectColorMatrix = effectColorMatrix
    )

    drawQuad(program)
    fbo.unbind()

    return fbo.getTextureId()
  }

  private fun getOrCreateTextTexture(
    clip: TextClip,
    currentPosMs: Long,
    viewportWidth: Int,
    viewportHeight: Int
  ): CachedTexture? {
    val hasAnim = clip.animationType != "None" || clip.animation3D != "None" || clip.keyframes.isNotEmpty()
    val animTimeStep = if (hasAnim) (currentPosMs / 33L).toInt() else 0

    val hash = clip.text.hashCode() xor
        clip.textColor.toInt() xor
        clip.fontSizeSp.toInt() xor
        clip.fontWeight.hashCode() xor
        clip.backgroundColor.toInt() xor
        clip.strokeColor.toInt() xor
        clip.strokeWidth.toInt() xor
        clip.fontFamily.hashCode() xor
        (clip.customFontPath?.hashCode() ?: 0) xor
        clip.animationType.hashCode() xor
        clip.animation3D.hashCode() xor
        clip.effectStyle.hashCode() xor
        clip.depth3D.toInt() xor
        clip.bevelAngle3D.toInt() xor
        clip.is3D.hashCode() xor
        clip.hasGradient.hashCode() xor
        clip.gradientColorStart.toInt() xor
        clip.gradientColorEnd.toInt() xor
        clip.hasGlow.hashCode() xor
        clip.hasShadow.hashCode() xor
        clip.opacity.hashCode() xor
        clip.scale.hashCode() xor
        clip.rotation.hashCode() xor
        clip.posX.hashCode() xor
        clip.posY.hashCode() xor
        clip.alignment.hashCode() xor
        animTimeStep xor
        viewportWidth xor
        (viewportHeight shl 16)

    val cached = textTextureCache[clip.id]
    if (cached != null && cached.hash == hash && cached.texId > 0) {
      return cached
    }

    val bitmap = TextLayerRenderer.renderToBitmap(
      clip = clip,
      currentPosMs = currentPosMs,
      width = viewportWidth,
      height = viewportHeight,
      context = context
    ) ?: return null

    val oldTexId = if (cached != null && cached.hash != hash) cached.texId else 0
    val texId = GlShaderUtil.uploadBitmapToTexture(bitmap, oldTexId)
    bitmap.recycle()

    if (texId == 0) return null
    val entry = CachedTexture(texId, bitmap.width, bitmap.height, hash, currentFrameCounter)
    textTextureCache[clip.id] = entry
    return entry
  }

  private fun getOrCreateStickerTexture(
    clip: StickerClip,
    viewportWidth: Int,
    viewportHeight: Int
  ): CachedTexture? {
    val hash = clip.emojiOrAsset.hashCode() xor clip.badgeType.hashCode() xor viewportWidth
    val cached = stickerTextureCache[clip.id]
    if (cached != null && cached.hash == hash && cached.texId > 0) {
      return cached
    }

    val isBadge = clip.badgeType != null
    val targetWidth = if (isBadge) {
      (160f * (viewportWidth.toFloat() / 600f)).toInt().coerceIn(128, 384)
    } else {
      (80f * (viewportWidth.toFloat() / 600f)).toInt().coerceIn(64, 256)
    }
    val targetHeight = if (isBadge) {
      (targetWidth * 0.42f).toInt().coerceIn(54, 160)
    } else {
      targetWidth
    }

    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    StickerLayerRenderer.draw(
      canvas = canvas,
      clip = clip.copy(posX = 0f, posY = 0f, scale = 1f, rotation = 0f, opacity = 1f),
      currentPosMs = clip.timelineStartMs,
      width = targetWidth,
      height = targetHeight
    )

    val oldTexId = cached?.texId ?: 0
    val texId = GlShaderUtil.uploadBitmapToTexture(bitmap, oldTexId)
    bitmap.recycle()

    if (texId == 0) return null
    val entry = CachedTexture(texId, targetWidth, targetHeight, hash, currentFrameCounter)
    stickerTextureCache[clip.id] = entry
    return entry
  }

  fun uploadImageTexture(id: String, bitmap: Bitmap): Int {
    val existing = imageTextureCache[id]
    if (existing != null && existing.hash == bitmap.generationId && existing.width == bitmap.width && existing.height == bitmap.height) {
      existing.lastFrameUsed = currentFrameCounter
      return existing.texId
    }
    val texId = GlShaderUtil.uploadBitmapToTexture(bitmap, existing?.texId ?: 0)
    val entry = CachedTexture(texId, bitmap.width, bitmap.height, bitmap.generationId, currentFrameCounter)
    imageTextureCache[id] = entry
    return texId
  }

  internal fun getOrCreateProceduralEffectTexture(
    frame: ComposedFrame,
    viewportWidth: Int,
    viewportHeight: Int
  ): CachedTexture? {
    val overlayEffects = frame.activeEffects.filter { isProceduralOverlayEffect(it.effectType) }
    if (overlayEffects.isEmpty() || viewportWidth <= 0 || viewportHeight <= 0) return null

    val timeStep = (frame.timelinePosMs / 33L).toInt()
    var hash = timeStep xor viewportWidth xor (viewportHeight shl 16)
    for (eff in overlayEffects) {
      hash = hash xor eff.clip.id.hashCode() xor eff.effectType.hashCode() xor (eff.intensity * 1000f).toInt()
    }

    val key = "procedural_overlay_fx"
    val cached = proceduralEffectCache[key]
    if (cached != null && cached.hash == hash && cached.width == viewportWidth && cached.height == viewportHeight) {
      cached.lastFrameUsed = currentFrameCounter
      return cached
    }

    val currentBmp = proceduralBitmap
    val bmp = if (currentBmp != null && !currentBmp.isRecycled && currentBmp.width == viewportWidth && currentBmp.height == viewportHeight) {
      currentBmp.eraseColor(0)
      currentBmp
    } else {
      currentBmp?.recycle()
      val newBmp = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
      proceduralBitmap = newBmp
      proceduralCanvas = Canvas(newBmp)
      newBmp
    }
    val canvas = proceduralCanvas ?: Canvas(bmp)

    VideoEffectRenderer.renderEffectsOnCanvas(
      canvas = canvas,
      activeEffects = overlayEffects.map { it.clip },
      currentPosMs = frame.timelinePosMs,
      width = viewportWidth,
      height = viewportHeight
    )

    val oldTexId = cached?.texId ?: 0
    val texId = GlShaderUtil.uploadBitmapToTexture(bmp, oldTexId)

    if (texId == 0) return null
    val entry = CachedTexture(texId, viewportWidth, viewportHeight, hash, currentFrameCounter)
    proceduralEffectCache[key] = entry
    return entry
  }

  private fun cleanStaleTextureCaches() {
    val textToDel = textTextureCache.filter { currentFrameCounter - it.value.lastFrameUsed > MAX_UNTOUCHED_CACHE_FRAMES }
    for ((id, tex) in textToDel) {
      if (tex.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(tex.texId), 0)
      }
      textTextureCache.remove(id)
    }

    val stickerToDel = stickerTextureCache.filter { currentFrameCounter - it.value.lastFrameUsed > MAX_UNTOUCHED_CACHE_FRAMES }
    for ((id, tex) in stickerToDel) {
      if (tex.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(tex.texId), 0)
      }
      stickerTextureCache.remove(id)
    }

    val fxToDel = proceduralEffectCache.filter { currentFrameCounter - it.value.lastFrameUsed > MAX_UNTOUCHED_CACHE_FRAMES }
    for ((id, tex) in fxToDel) {
      if (tex.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(tex.texId), 0)
      }
      proceduralEffectCache.remove(id)
    }
  }

  private fun mapBlendMode(modeStr: String): NativeBlendMode {
    return when (modeStr.lowercase().trim()) {
      "screen" -> NativeBlendMode.SCREEN
      "multiply" -> NativeBlendMode.MULTIPLY
      "add", "additive" -> NativeBlendMode.ADDITIVE
      "premultiplied" -> NativeBlendMode.PREMULTIPLIED
      else -> NativeBlendMode.NORMAL
    }
  }

  private fun bindCommonUniforms(
    program: Int,
    textureId: Int,
    isOes: Boolean,
    opacity: Float,
    adjustments: VideoAdjustments,
    filter: FilterSettings,
    chromaKey: ChromaKeySettings,
    viewportWidth: Int,
    viewportHeight: Int,
    blur: Float = 0f,
    effectParam: Float = 0f,
    effectColorMatrix: android.graphics.ColorMatrix? = null
  ) {
    val uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
    val uTexMatrixHandle = GLES20.glGetUniformLocation(program, "uTexMatrix")
    val uTextureHandle = GLES20.glGetUniformLocation(program, "uTexture")
    val uOpacityHandle = GLES20.glGetUniformLocation(program, "uOpacity")
    val uBlurHandle = GLES20.glGetUniformLocation(program, "uBlur")
    val uEffectParamHandle = GLES20.glGetUniformLocation(program, "uEffectParam")

    GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
    GLES20.glUniformMatrix4fv(uTexMatrixHandle, 1, false, texMatrix, 0)
    GLES20.glUniform1f(uOpacityHandle, opacity)
    if (uBlurHandle >= 0) GLES20.glUniform1f(uBlurHandle, blur)
    if (uEffectParamHandle >= 0) GLES20.glUniform1f(uEffectParamHandle, effectParam)

    val target = if (isOes) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(target, textureId)
    GLES20.glUniform1i(uTextureHandle, 0)

    val uBrightnessHandle = GLES20.glGetUniformLocation(program, "uBrightness")
    val uContrastHandle = GLES20.glGetUniformLocation(program, "uContrast")
    val uSaturationHandle = GLES20.glGetUniformLocation(program, "uSaturation")
    val uExposureHandle = GLES20.glGetUniformLocation(program, "uExposure")
    val uTemperatureHandle = GLES20.glGetUniformLocation(program, "uTemperature")
    val uTintHandle = GLES20.glGetUniformLocation(program, "uTint")
    val uHighlightsHandle = GLES20.glGetUniformLocation(program, "uHighlights")
    val uShadowsHandle = GLES20.glGetUniformLocation(program, "uShadows")
    val uVignetteHandle = GLES20.glGetUniformLocation(program, "uVignette")
    val uGrainHandle = GLES20.glGetUniformLocation(program, "uGrain")
    val uSharpnessHandle = GLES20.glGetUniformLocation(program, "uSharpness")
    val uTexelSizeHandle = GLES20.glGetUniformLocation(program, "uTexelSize")

    if (uBrightnessHandle >= 0) GLES20.glUniform1f(uBrightnessHandle, adjustments.brightness)
    if (uContrastHandle >= 0) GLES20.glUniform1f(uContrastHandle, adjustments.contrast)
    if (uSaturationHandle >= 0) GLES20.glUniform1f(uSaturationHandle, adjustments.saturation)
    if (uExposureHandle >= 0) GLES20.glUniform1f(uExposureHandle, adjustments.exposure)
    if (uTemperatureHandle >= 0) GLES20.glUniform1f(uTemperatureHandle, adjustments.temperature)
    if (uTintHandle >= 0) GLES20.glUniform1f(uTintHandle, adjustments.tint)
    if (uHighlightsHandle >= 0) GLES20.glUniform1f(uHighlightsHandle, adjustments.highlights)
    if (uShadowsHandle >= 0) GLES20.glUniform1f(uShadowsHandle, adjustments.shadows)
    if (uVignetteHandle >= 0) GLES20.glUniform1f(uVignetteHandle, adjustments.vignette)
    if (uGrainHandle >= 0) GLES20.glUniform1f(uGrainHandle, adjustments.grain)
    if (uSharpnessHandle >= 0) GLES20.glUniform1f(uSharpnessHandle, adjustments.sharpness)
    if (uTexelSizeHandle >= 0) GLES20.glUniform2f(uTexelSizeHandle, 1.0f / max(1, viewportWidth), 1.0f / max(1, viewportHeight))

    val uChromaEnabledHandle = GLES20.glGetUniformLocation(program, "uChromaEnabled")
    if (uChromaEnabledHandle >= 0) {
      if (chromaKey.enabled) {
        val color = chromaKey.targetColor.toInt()
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f

        GLES20.glUniform1i(uChromaEnabledHandle, 1)
        val keyLoc = GLES20.glGetUniformLocation(program, "uChromaKeyColor").let { if (it >= 0) it else GLES20.glGetUniformLocation(program, "uKeyColor") }
        if (keyLoc >= 0) {
          GLES20.glUniform3f(keyLoc, r, g, b)
        }
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChromaSimilarity"), chromaKey.similarity)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChromaSmoothness"), max(0.001f, chromaKey.smoothness))
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChromaSpill"), chromaKey.spillSuppression)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uChromaEdge"), chromaKey.edgeControl)

        val bgType = if (chromaKey.backgroundType == "SolidColor") 1 else 0
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uChromaBgType"), bgType)
        val bgColor = chromaKey.backgroundColor.toInt()
        val bgR = Color.red(bgColor) / 255f
        val bgG = Color.green(bgColor) / 255f
        val bgB = Color.blue(bgColor) / 255f
        val bgA = Color.alpha(bgColor) / 255f
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "uChromaBgColor"), bgR, bgG, bgB, bgA)
      } else {
        GLES20.glUniform1i(uChromaEnabledHandle, 0)
      }
    }

    val uColorMatrixHandle = GLES20.glGetUniformLocation(program, "uColorMatrix")
    val uColorOffsetHandle = GLES20.glGetUniformLocation(program, "uColorOffset")
    val uUseColorMatrixHandle = GLES20.glGetUniformLocation(program, "uUseColorMatrix")

    val filterMatrix = com.example.engine.composition.ColorFilterGenerator.getFilterMatrix(filter.type, filter.intensity)
    val finalMatrix = when {
      filterMatrix != null && effectColorMatrix != null -> {
        val combined = android.graphics.ColorMatrix(filterMatrix)
        combined.postConcat(effectColorMatrix)
        combined
      }
      filterMatrix != null -> filterMatrix
      effectColorMatrix != null -> effectColorMatrix
      else -> null
    }

    if (finalMatrix != null && uUseColorMatrixHandle >= 0) {
      val arr = finalMatrix.array
      val glMat = floatArrayOf(
        arr[0], arr[5], arr[10], arr[15],
        arr[1], arr[6], arr[11], arr[16],
        arr[2], arr[7], arr[12], arr[17],
        arr[3], arr[8], arr[13], arr[18]
      )
      val glOffset = floatArrayOf(
        arr[4] / 255.0f,
        arr[9] / 255.0f,
        arr[14] / 255.0f,
        arr[19] / 255.0f
      )
      if (uColorMatrixHandle >= 0) GLES20.glUniformMatrix4fv(uColorMatrixHandle, 1, false, glMat, 0)
      if (uColorOffsetHandle >= 0) GLES20.glUniform4fv(uColorOffsetHandle, 1, glOffset, 0)
      GLES20.glUniform1i(uUseColorMatrixHandle, 1)
    } else if (uUseColorMatrixHandle >= 0) {
      GLES20.glUniform1i(uUseColorMatrixHandle, 0)
    }
  }

  private fun drawQuad(program: Int) {
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    val aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
    val aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")

    if (aPositionHandle >= 0) {
      vertexBuffer.position(POSITION_DATA_OFFSET)
      GLES20.glVertexAttribPointer(
        aPositionHandle, 2, GLES20.GL_FLOAT, false,
        TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vertexBuffer
      )
      GLES20.glEnableVertexAttribArray(aPositionHandle)
    }

    if (aTextureCoordHandle >= 0) {
      vertexBuffer.position(TEXTURE_DATA_OFFSET)
      GLES20.glVertexAttribPointer(
        aTextureCoordHandle, 2, GLES20.GL_FLOAT, false,
        TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vertexBuffer
      )
      GLES20.glEnableVertexAttribArray(aTextureCoordHandle)
    }

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

    if (aPositionHandle >= 0) GLES20.glDisableVertexAttribArray(aPositionHandle)
    if (aTextureCoordHandle >= 0) GLES20.glDisableVertexAttribArray(aTextureCoordHandle)
  }

  fun onContextLost() {
    isInitialized = false
    textTextureCache.clear()
    stickerTextureCache.clear()
    imageTextureCache.clear()
    proceduralEffectCache.clear()
    proceduralBitmap?.recycle()
    proceduralBitmap = null
    proceduralCanvas = null
    fboOverlayMap.clear()
    if (nativeHandle != 0L) {
      NativeRenderBridge.onContextLost(nativeHandle)
    }
  }

  fun release() {
    fboMain2D.release()
    fboA.release()
    fboB.release()

    for (fbo in fboOverlayMap.values) {
      fbo.release()
    }
    fboOverlayMap.clear()

    val texturesToDelete = mutableListOf<Int>()
    for (t in textTextureCache.values) texturesToDelete.add(t.texId)
    for (s in stickerTextureCache.values) texturesToDelete.add(s.texId)
    for (i in imageTextureCache.values) texturesToDelete.add(i.texId)
    for (fx in proceduralEffectCache.values) texturesToDelete.add(fx.texId)

    if (texturesToDelete.isNotEmpty()) {
      GLES20.glDeleteTextures(texturesToDelete.size, texturesToDelete.toIntArray(), 0)
    }
    textTextureCache.clear()
    stickerTextureCache.clear()
    imageTextureCache.clear()
    proceduralEffectCache.clear()
    proceduralBitmap?.recycle()
    proceduralBitmap = null
    proceduralCanvas = null

    if (program2D != 0) {
      GLES20.glDeleteProgram(program2D)
      program2D = 0
    }
    if (programOes != 0) {
      GLES20.glDeleteProgram(programOes)
      programOes = 0
    }
    if (programTransition != 0) {
      GLES20.glDeleteProgram(programTransition)
      programTransition = 0
    }
    if (programEffect != 0) {
      GLES20.glDeleteProgram(programEffect)
      programEffect = 0
    }
    if (programBlit != 0) {
      GLES20.glDeleteProgram(programBlit)
      programBlit = 0
    }

    isInitialized = false
    if (nativeHandle != 0L) {
      NativeRenderBridge.release(nativeHandle)
      nativeHandle = 0L
    }
    Log.d(TAG, "GpuCompositionRenderer & Native Engine cleanly released")
  }

  private fun blitToSurface(textureId: Int, flipY: Boolean, flipX: Boolean = false) {
    if (programBlit == 0 || textureId <= 0) return
    GLES20.glUseProgram(programBlit)

    val uMVPMatrixHandle = GLES20.glGetUniformLocation(programBlit, "uMVPMatrix")
    val uTexMatrixHandle = GLES20.glGetUniformLocation(programBlit, "uTexMatrix")
    val uFlipYHandle = GLES20.glGetUniformLocation(programBlit, "uFlipY")
    val uFlipXHandle = GLES20.glGetUniformLocation(programBlit, "uFlipX")
    val uTextureHandle = GLES20.glGetUniformLocation(programBlit, "uTexture")

    val identity = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    if (uMVPMatrixHandle >= 0) GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, identity, 0)
    if (uTexMatrixHandle >= 0) GLES20.glUniformMatrix4fv(uTexMatrixHandle, 1, false, identity, 0)
    if (uFlipYHandle >= 0) GLES20.glUniform1i(uFlipYHandle, if (flipY) 1 else 0)
    if (uFlipXHandle >= 0) GLES20.glUniform1i(uFlipXHandle, if (flipX) 1 else 0)

    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
    if (uTextureHandle >= 0) GLES20.glUniform1i(uTextureHandle, 0)

    drawQuad(programBlit)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
  }

  private fun applyEffect(
    effectType: EffectType,
    intensity: Float,
    timeSec: Float,
    inputTexId: Int,
    viewportWidth: Int,
    viewportHeight: Int
  ) {
    if (programEffect == 0) return
    GLES20.glUseProgram(programEffect)

    val uMVPMatrixHandle = GLES20.glGetUniformLocation(programEffect, "uMVPMatrix")
    val uTexMatrixHandle = GLES20.glGetUniformLocation(programEffect, "uTexMatrix")
    val uTextureHandle = GLES20.glGetUniformLocation(programEffect, "uTexture")
    val uEffectTypeHandle = GLES20.glGetUniformLocation(programEffect, "uEffectType")
    val uIntensityHandle = GLES20.glGetUniformLocation(programEffect, "uIntensity")
    val uTimeHandle = GLES20.glGetUniformLocation(programEffect, "uTime")
    val uTexelSizeHandle = GLES20.glGetUniformLocation(programEffect, "uTexelSize")

    val identity = FloatArray(16)
    Matrix.setIdentityM(identity, 0)
    if (uMVPMatrixHandle >= 0) GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, identity, 0)
    if (uTexMatrixHandle >= 0) GLES20.glUniformMatrix4fv(uTexMatrixHandle, 1, false, identity, 0)

    val glEffectType = when (effectType) {
      // Blur & Soft Focus
      EffectType.BLUR, EffectType.SOFT_FOCUS, EffectType.SHARPEN,
      EffectType.VFX_BLUR_1, EffectType.VFX_BLUR_3, EffectType.VFX_BLUR_8,
      EffectType.VFX_BLUR_9, EffectType.VFX_BLUR_10, EffectType.VFX_BLUR_11,
      EffectType.VFX_BLUR_12, EffectType.VFX_BLUR_13, EffectType.VFX_BLUR_15 -> GpuShaders.EFFECT_BLUR

      // Motion Blur
      EffectType.MOTION_BLUR, EffectType.VFX_VIRAL_1, EffectType.VFX_VIRAL_14,
      EffectType.VFX_BLUR_2, EffectType.VFX_BLUR_4, EffectType.VFX_BLUR_5,
      EffectType.VFX_BLUR_6, EffectType.VFX_BLUR_7, EffectType.VFX_BLUR_14 -> GpuShaders.EFFECT_MOTION_BLUR

      // Glow, Halo
      EffectType.GLOW, EffectType.HALO_GLOW,
      EffectType.VFX_LIGHT_4, EffectType.VFX_LIGHT_7, EffectType.VFX_LIGHT_14,
      EffectType.VFX_LIGHT_18, EffectType.VFX_LIGHT_19, EffectType.VFX_VIRAL_21,
      EffectType.VFX_VIRAL_22 -> GpuShaders.EFFECT_GLOW

      // Shake & Camera Shake
      EffectType.SHAKE, EffectType.CAMERA_MOVEMENT, EffectType.PARTY_CONFUSED,
      EffectType.VFX_VIRAL_6, EffectType.VFX_VIRAL_15, EffectType.VFX_GLITCH_14,
      EffectType.VFX_GLITCH_16, EffectType.VFX_GLITCH_19 -> GpuShaders.EFFECT_SHAKE

      // Zoom & Pulse
      EffectType.ZOOM, EffectType.SKATER_ZOOM, EffectType.VERTIGO_DOLLY, EffectType.WARP_SPEED,
      EffectType.VFX_VIRAL_12, EffectType.VFX_VIRAL_16, EffectType.VFX_VIRAL_17,
      EffectType.VFX_VIRAL_35, EffectType.VFX_3D_9 -> GpuShaders.EFFECT_ZOOM

      // Spin
      EffectType.SPIN, EffectType.VFX_VIRAL_20, EffectType.VFX_3D_8, EffectType.VFX_3D_15 -> GpuShaders.EFFECT_SPIN

      // Flash & Strobe
      EffectType.FLASH, EffectType.STROBE, EffectType.VFX_VIRAL_11, EffectType.VFX_VIRAL_36,
      EffectType.VFX_LIGHT_16, EffectType.VFX_VIRAL_23 -> GpuShaders.EFFECT_FLASH

      // Glitch, CRT, VHS
      EffectType.GLITCH, EffectType.AI_GLITCH_REALITY,
      EffectType.VFX_GLITCH_1, EffectType.VFX_GLITCH_2, EffectType.VFX_GLITCH_3,
      EffectType.VFX_GLITCH_6, EffectType.VFX_GLITCH_7, EffectType.VFX_GLITCH_8,
      EffectType.VFX_GLITCH_9, EffectType.VFX_GLITCH_10, EffectType.VFX_GLITCH_11,
      EffectType.VFX_GLITCH_13, EffectType.VFX_GLITCH_17, EffectType.VFX_GLITCH_18,
      EffectType.VFX_GLITCH_20, EffectType.VFX_RETRO_10,
      EffectType.VFX_VIRAL_4, EffectType.VFX_VIRAL_30, EffectType.VFX_VIRAL_31 -> GpuShaders.EFFECT_GLITCH

      EffectType.CRT_TV, EffectType.VFX_RETRO_1 -> GpuShaders.EFFECT_CRT
      EffectType.VHS_VINTAGE -> GpuShaders.EFFECT_VHS

      // RGB Split
      EffectType.RGB_SPLIT, EffectType.VFX_VIRAL_5, EffectType.VFX_VIRAL_29,
      EffectType.VFX_GLITCH_12 -> GpuShaders.EFFECT_RGB_SPLIT

      // Distortion, Wave, Fisheye
      EffectType.DISTORTION, EffectType.WAVE, EffectType.FISHEYE,
      EffectType.FUNNY_ALIEN_WARP, EffectType.VFX_GLITCH_4,
      EffectType.VFX_GLITCH_5, EffectType.VFX_GLITCH_15, EffectType.VFX_VIRAL_8,
      EffectType.VFX_VIRAL_9, EffectType.VFX_3D_2 -> GpuShaders.EFFECT_DISTORTION

      EffectType.RIPPLE -> GpuShaders.EFFECT_RIPPLE

      // Lens Flare & Solar Flare
      EffectType.LENS_FLARE, EffectType.VFX_LIGHT_2 -> GpuShaders.EFFECT_LENS_FLARE
      EffectType.SOLAR_FLARE -> GpuShaders.EFFECT_SOLAR_FLARE

      // Light Leak, Golden Hour, Bokeh, Prism
      EffectType.LIGHT_LEAK, EffectType.GOLDEN_HOUR,
      EffectType.VFX_LIGHT_1, EffectType.VFX_LIGHT_3, EffectType.VFX_LIGHT_5,
      EffectType.VFX_LIGHT_10, EffectType.VFX_LIGHT_17, EffectType.VFX_RETRO_7,
      EffectType.VFX_VIRAL_24 -> GpuShaders.EFFECT_LIGHT_LEAK

      EffectType.BOKEH -> GpuShaders.EFFECT_BOKEH
      EffectType.PARTY_PRISM -> GpuShaders.EFFECT_PRISM
      EffectType.VIGNETTE -> GpuShaders.EFFECT_VIGNETTE
      EffectType.NOISE -> GpuShaders.EFFECT_NOISE
      EffectType.THERMAL_CAMERA -> GpuShaders.EFFECT_THERMAL
      EffectType.BLUEPRINT_CAD -> GpuShaders.EFFECT_BLUEPRINT
      EffectType.ACID_TRIP -> GpuShaders.EFFECT_ACID_TRIP
      EffectType.POP_ART_POSTER -> GpuShaders.EFFECT_POP_ART
      EffectType.SEPIA_VINTAGE -> GpuShaders.EFFECT_SEPIA
      EffectType.POLAROID_VINTAGE -> GpuShaders.EFFECT_POLAROID
      EffectType.OIL_PAINTING -> GpuShaders.EFFECT_OIL_PAINTING
      EffectType.HALFTONE_DOT -> GpuShaders.EFFECT_HALFTONE
      EffectType.COMIC_SKETCH -> GpuShaders.EFFECT_COMIC
      EffectType.MIRROR -> GpuShaders.EFFECT_MIRROR

      else -> -1
    }

    if (uEffectTypeHandle >= 0) GLES20.glUniform1i(uEffectTypeHandle, glEffectType)
    if (uIntensityHandle >= 0) GLES20.glUniform1f(uIntensityHandle, intensity)
    if (uTimeHandle >= 0) GLES20.glUniform1f(uTimeHandle, timeSec)
    if (uTexelSizeHandle >= 0) GLES20.glUniform2f(uTexelSizeHandle, 1.0f / max(1, viewportWidth), 1.0f / max(1, viewportHeight))

    GLES20.glDisable(GLES20.GL_BLEND)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexId)
    if (uTextureHandle >= 0) GLES20.glUniform1i(uTextureHandle, 0)

    drawQuad(programEffect)
  }

  fun invalidateClip(clipId: String) {
    textTextureCache.remove(clipId)?.let {
      if (it.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(it.texId), 0)
      }
    }
    stickerTextureCache.remove(clipId)?.let {
      if (it.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(it.texId), 0)
      }
    }
    imageTextureCache.remove(clipId)?.let {
      if (it.texId > 0) {
        GLES20.glDeleteTextures(1, intArrayOf(it.texId), 0)
      }
    }
  }

  fun invalidateAll() {
    for ((_, item) in textTextureCache) {
      if (item.texId > 0) GLES20.glDeleteTextures(1, intArrayOf(item.texId), 0)
    }
    textTextureCache.clear()
    for ((_, item) in stickerTextureCache) {
      if (item.texId > 0) GLES20.glDeleteTextures(1, intArrayOf(item.texId), 0)
    }
    stickerTextureCache.clear()
    for ((_, item) in imageTextureCache) {
      if (item.texId > 0) GLES20.glDeleteTextures(1, intArrayOf(item.texId), 0)
    }
    imageTextureCache.clear()
  }
}
