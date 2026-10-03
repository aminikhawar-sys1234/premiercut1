package com.example.engine.composition.gpu

import android.opengl.GLES20
import android.opengl.GLES30
import com.example.domain.model.ClipKeyframe
import com.example.domain.model.VideoClip
import com.example.engine.InterpolatedClipTransform
import com.example.engine.KeyframeInterpolator
import com.example.engine.gpu3d.Camera3D
import com.example.engine.gpu3d.Transform3D

/**
 * Keyframe-Driven OpenGL ES Uniform Automation Engine.
 *
 * Interpolates bezier/ease curves at exact hardware timestamps and directly
 * uploads matrices, vectors, and scalars into active GPU shader uniforms.
 */
object KeyframeUniformAutomator {

    /**
     * Binds 2D transform, opacity, and color parameters directly to shader uniforms.
     */
    fun bind2DTransformUniforms(
        programId: Int,
        transform: InterpolatedClipTransform,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        val uScaleLoc = GLES20.glGetUniformLocation(programId, "u_Scale")
        val uRotationLoc = GLES20.glGetUniformLocation(programId, "u_Rotation")
        val uOffsetLoc = GLES20.glGetUniformLocation(programId, "u_Offset")
        val uOpacityLoc = GLES20.glGetUniformLocation(programId, "u_Opacity")
        val uBlurLoc = GLES20.glGetUniformLocation(programId, "u_Blur")
        val uBrightnessLoc = GLES20.glGetUniformLocation(programId, "u_Brightness")
        val uContrastLoc = GLES20.glGetUniformLocation(programId, "u_Contrast")
        val uSaturationLoc = GLES20.glGetUniformLocation(programId, "u_Saturation")

        if (uScaleLoc >= 0) GLES20.glUniform2f(uScaleLoc, transform.scaleX, transform.scaleY)
        if (uRotationLoc >= 0) GLES20.glUniform1f(uRotationLoc, Math.toRadians(transform.rotation.toDouble()).toFloat())
        if (uOffsetLoc >= 0) GLES20.glUniform2f(uOffsetLoc, transform.posX, transform.posY)
        if (uOpacityLoc >= 0) GLES20.glUniform1f(uOpacityLoc, transform.opacity)
        if (uBlurLoc >= 0) GLES20.glUniform1f(uBlurLoc, transform.blur)
        if (uBrightnessLoc >= 0) GLES20.glUniform1f(uBrightnessLoc, transform.brightness)
        if (uContrastLoc >= 0) GLES20.glUniform1f(uContrastLoc, transform.contrast)
        if (uSaturationLoc >= 0) GLES20.glUniform1f(uSaturationLoc, transform.saturation)
    }

    /**
     * Evaluates keyframes for a clip at [relTimeMs] and injects computed uniforms into shader.
     */
    fun bindClipKeyframes(
        programId: Int,
        clip: VideoClip,
        relTimeMs: Long,
        canvasWidth: Float,
        canvasHeight: Float
    ) {
        val transform = KeyframeInterpolator.interpolate(clip, relTimeMs)
        bind2DTransformUniforms(programId, transform, canvasWidth, canvasHeight)
    }

    /**
     * Binds 3D MVP Matrices, 3D Rotations (Yaw/Pitch/Roll), and Camera Uniforms.
     */
    fun bind3DTransformUniforms(
        programId: Int,
        transform3D: Transform3D,
        camera3D: Camera3D,
        aspectRatio: Float
    ) {
        val model = transform3D.getModelMatrix()
        val view = camera3D.getViewMatrix()
        val proj = camera3D.getProjectionMatrix(aspectRatio)

        val uModelLoc = GLES30.glGetUniformLocation(programId, "uModelMatrix")
        val uViewLoc = GLES30.glGetUniformLocation(programId, "uViewMatrix")
        val uProjLoc = GLES30.glGetUniformLocation(programId, "uProjMatrix")

        if (uModelLoc >= 0) GLES30.glUniformMatrix4fv(uModelLoc, 1, false, model, 0)
        if (uViewLoc >= 0) GLES30.glUniformMatrix4fv(uViewLoc, 1, false, view, 0)
        if (uProjLoc >= 0) GLES30.glUniformMatrix4fv(uProjLoc, 1, false, proj, 0)
    }
}
