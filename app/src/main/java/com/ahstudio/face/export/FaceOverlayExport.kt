package com.ahstudio.face.export

import android.content.Context
import android.opengl.GLES20
import androidx.media3.effect.GlEffect
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.effect.GlShaderProgram
import com.ahstudio.face.FaceEngineHost
import com.ahstudio.face.geometry.VideoTransform
import com.ahstudio.face.timeline.FaceTimelineBridge
import java.util.concurrent.Executor

class FaceOverlayEffect(
    private val host: FaceEngineHost,
    private val exportTransform: VideoTransform,
    private val forceBlockingTracking: Boolean = true,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHDR: Boolean): GlShaderProgram =
        FaceOverlayExportProgram(host, exportTransform, forceBlockingTracking)
}

class FaceOverlayExportProgram(
    private val host: FaceEngineHost,
    private val baseTransform: VideoTransform,
    private val forceBlocking: Boolean,
) : GlShaderProgram {

    override fun queueInputFrame(
        glObjectsProvider: GlObjectsProvider,
        inputTexture: GlTextureInfo,
        presentationTimeUs: Long
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, inputTexture.fboId)
        host.renderFrame(
            presentationTimeUs,
            baseTransform.copy(viewportWidth = inputTexture.width, viewportHeight = inputTexture.height),
            seekHint = forceBlocking,
        )
    }

    override fun setInputListener(inputListener: GlShaderProgram.InputListener) {}
    override fun setOutputListener(outputListener: GlShaderProgram.OutputListener) {}
    override fun setErrorListener(executor: Executor, errorListener: GlShaderProgram.ErrorListener) {}
    override fun releaseOutputFrame(outputTexture: GlTextureInfo) {}
    override fun signalEndOfCurrentInputStream() {}
    override fun flush() {}
    override fun release() {}
}

object TrackingPreWarmer {
    fun warm(
        host: FaceEngineHost,
        bridge: FaceTimelineBridge,
        ranges: List<Triple<String, Long, Long>>,
        stepUs: Long = 66_666,
    ) {
        for ((clipId, from, to) in ranges) {
            host.tracking.prewarm(clipId, from, to, stepUs, bridge.transformHash(clipId), bridge.isMirroredSource(clipId))
        }
    }
}
