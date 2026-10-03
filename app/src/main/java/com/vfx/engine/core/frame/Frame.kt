package com.vfx.engine.core.frame

import com.vfx.engine.core.math.Mat4

enum class PixelFormat(val glInternalFormat: Int = 0x8058, val channels: Int = 4) {
    RGBA8(0x8058, 4),
    BGRA8(0x80E1, 4),
    RGB8(0x8051, 3),
    RGBA_FP16(0x881A, 4),
    EXTERNAL_OES(-1, 4),
    RGBA_8888(0x8058, 4),
    RGBA_F16(0x881A, 4),
    YUV_420_888(-1, 3),
    OES_EXTERNAL(-1, 4);

    val isFloat: Boolean get() = this == RGBA_FP16 || this == RGBA_F16
}

/** Working color space of pixels inside the render graph. */
enum class WorkingColorSpace { SRGB_GAMMA, LINEAR_SRGB, DISPLAY_P3 }

enum class AlphaMode { OPAQUE, STRAIGHT, PREMULTIPLIED }
enum class Orientation(val degrees: Int = 0, val isMirrored: Boolean = false) {
    DEG_0(0),
    DEG_90(90),
    DEG_180(180),
    DEG_270(270),
    UP(0),
    RIGHT(90),
    DOWN(180),
    LEFT(270),
    UP_MIRRORED(0, true),
    RIGHT_MIRRORED(90, true),
    DOWN_MIRRORED(180, true),
    LEFT_MIRRORED(270, true)
}

enum class TextureTarget { TEXTURE_2D, EXTERNAL_OES }

/** HDR static metadata (SMPTE ST 2086 subset). Null when SDR. */
data class HdrMetadata(
    val maxContentLuminanceNits: Float = 1000f,
    val maxFrameAverageLuminanceNits: Float = 400f,
    val minLuminanceNits: Float = 0.005f,
    val transfer: TransferCharacteristics = TransferCharacteristics.SDR_SRGB,
    val isHdr: Boolean = false
)
enum class TransferCharacteristics { SDR_SRGB, HLG, PQ }

/**
 * Immutable description of one video frame handed to the engine.
 * The texture is owned by the producer (decoder / compositor); the engine
 * never deletes it — only samples it.
 */
data class Frame(
    val textureId: Int,
    val width: Int,
    val height: Int,
    val timestampUs: Long,
    val durationUs: Long = 0L,
    val pixelFormat: PixelFormat = PixelFormat.EXTERNAL_OES,
    val colorSpace: WorkingColorSpace = WorkingColorSpace.SRGB_GAMMA,
    val alphaMode: AlphaMode = AlphaMode.OPAQUE,
    val transformMatrix: Mat4? = null,     // e.g. SurfaceTexture stMatrix
    val orientation: Orientation = Orientation.DEG_0,
    val hdrMetadata: HdrMetadata? = null,
    val textureTarget: TextureTarget = TextureTarget.EXTERNAL_OES,
    val isValid: Boolean = true,
    val isSeek: Boolean = false            // flush temporal buffers
) {
    val isHdr: Boolean get() = hdrMetadata != null && hdrMetadata.isHdr
    init { require(width > 0 && height > 0) { "Invalid frame dimensions ${width}x$height" } }
}
