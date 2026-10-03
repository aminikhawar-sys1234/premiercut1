package com.ute.export

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import com.ute.model.TextDocument
import com.ute.render.EglCore
import com.ute.render.RenderTarget
import com.ute.render.TextFrameRenderer

/**
 * Export glue for MediaCodec surface encoding. The SAME TextFrameRenderer used
 * for preview drives the encoder input surface — preview/export parity by construction.
 */
class MediaCodecExportAdapter(
    private val renderer: TextFrameRenderer,
    private val egl: EglCore,
) {

    fun export(
        document: TextDocument,
        width: Int, height: Int, fps: Int,
        bitRate: Int = 12_000_000,
        onFrameEncoded: (frameIndex: Int) -> Unit,
    ) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        encoder.start()
        val inputSurface: Surface = encoder.createInputSurface()
        val eglSurface = egl.createWindowSurface(inputSurface)
        egl.makeCurrent(eglSurface)

        val totalFrames = (document.durationSec * fps).toLong()
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            for (frame in 0 until totalFrames) {
                val timeUs = frame * 1_000_000L / fps
                renderer.renderFrame(document, timeUs, RenderTarget.Surface(width, height))
                egl.setPresentationTime(eglSurface, timeUs * 1000L)
                egl.swap(eglSurface)
                drain(encoder, bufferInfo, endOfStream = false)
                onFrameEncoded(frame.toInt())
            }
            encoder.signalEndOfInputStream()
            drain(encoder, bufferInfo, endOfStream = true)
        } finally {
            egl.destroySurface(eglSurface)
            inputSurface.release()
            encoder.stop()
            encoder.release()
        }
    }

    private fun drain(codec: MediaCodec, info: MediaCodec.BufferInfo, endOfStream: Boolean) {
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            if (idx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return
                continue
            }
            if (idx >= 0) {
                codec.releaseOutputBuffer(idx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            } else {
                return
            }
        }
    }
}
