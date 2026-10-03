package com.example.engine.gpu3d

import android.opengl.GLES30

/**
 * Controller and orchestrator for 3D OpenGL ES 3.0 layer rendering.
 *
 * Ensures 100% parity between preview viewport and VideoExporter export pipeline.
 */
class Engine3DController {
    val camera = Camera3D()
    private val shader = Shader3D()
    private val planeMesh = Mesh3D.createPlaneQuad(2f, 2f)
    private val cubeMesh = Mesh3D.createCube(1.5f)
    val fbo3D = Gl3DFramebuffer()

    private var isInitialized = false

    fun init(width: Int, height: Int) {
        shader.init()
        planeMesh.initGl()
        cubeMesh.initGl()
        fbo3D.setup(width, height)
        isInitialized = true
    }

    /**
     * Renders a 3D layer (video texture, overlay, text) using the 3D MVP pipeline.
     */
    fun renderLayer3D(
        textureId: Int,
        transform: Transform3D,
        aspectRatio: Float,
        opacity: Float = 1.0f,
        is3DCube: Boolean = false,
        enableLighting: Boolean = true,
        enableFog: Boolean = false
    ) {
        if (!isInitialized) return

        shader.use()

        val modelMatrix = transform.getModelMatrix()
        val normalMatrix = transform.getNormalMatrix(modelMatrix)
        val viewMatrix = camera.getViewMatrix()
        val projMatrix = camera.getProjectionMatrix(aspectRatio)

        shader.setMatrices(modelMatrix, viewMatrix, projMatrix, normalMatrix)
        shader.setLighting(camera.position, enabled = enableLighting)
        shader.setMaterial(opacity)
        shader.setVolumetricFog(enabled = enableFog)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        shader.setTextureUnit(0)

        if (is3DCube) {
            cubeMesh.draw()
        } else {
            planeMesh.draw()
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    fun release() {
        if (isInitialized) {
            shader.release()
            planeMesh.release()
            cubeMesh.release()
            fbo3D.release()
            isInitialized = false
        }
    }
}
