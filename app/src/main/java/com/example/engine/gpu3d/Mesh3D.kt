package com.example.engine.gpu3d

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Encapsulates a 3D Vertex Specification with VBO/VAO management for OpenGL ES 3.0.
 *
 * Vertex Format (8 Floats per vertex / 32-byte stride):
 *  - Location 0 (Position): vec3 (x, y, z)
 *  - Location 1 (TexCoord): vec2 (u, v)
 *  - Location 2 (Normal):   vec3 (nx, ny, nz)
 */
class Mesh3D(
    val vertexData: FloatArray,
    val indexData: ShortArray
) {
    companion object {
        const val FLOATS_PER_VERTEX = 8
        const val BYTES_PER_FLOAT = 4
        const val VERTEX_STRIDE_BYTES = FLOATS_PER_VERTEX * BYTES_PER_FLOAT // 32 bytes

        const val POSITION_OFFSET = 0
        const val TEXCOORD_OFFSET = 3 * BYTES_PER_FLOAT
        const val NORMAL_OFFSET = 5 * BYTES_PER_FLOAT

        /**
         * Creates a standard 3D rectangular plane quad (e.g. for video layers, images, overlays).
         */
        fun createPlaneQuad(width: Float = 2f, height: Float = 2f): Mesh3D {
            val halfW = width / 2f
            val halfH = height / 2f
            val z = 0f

            // Format: x, y, z, u, v, nx, ny, nz
            val vertices = floatArrayOf(
                // Top-Left
                -halfW,  halfH, z,   0f, 0f,   0f, 0f, 1f,
                // Bottom-Left
                -halfW, -halfH, z,   0f, 1f,   0f, 0f, 1f,
                // Bottom-Right
                 halfW, -halfH, z,   1f, 1f,   0f, 0f, 1f,
                // Top-Right
                 halfW,  halfH, z,   1f, 0f,   0f, 0f, 1f
            )

            val indices = shortArrayOf(
                0, 1, 2,
                0, 2, 3
            )

            return Mesh3D(vertices, indices)
        }

        /**
         * Creates a 3D extruded bevel cube / volume mesh.
         */
        fun createCube(size: Float = 1f): Mesh3D {
            val h = size / 2f
            val vertices = floatArrayOf(
                // Front face
                -h, -h,  h,  0f, 1f,  0f, 0f, 1f,
                 h, -h,  h,  1f, 1f,  0f, 0f, 1f,
                 h,  h,  h,  1f, 0f,  0f, 0f, 1f,
                -h,  h,  h,  0f, 0f,  0f, 0f, 1f,
                // Back face
                -h, -h, -h,  1f, 1f,  0f, 0f, -1f,
                -h,  h, -h,  1f, 0f,  0f, 0f, -1f,
                 h,  h, -h,  0f, 0f,  0f, 0f, -1f,
                 h, -h, -h,  0f, 1f,  0f, 0f, -1f,
                // Top face
                -h,  h, -h,  0f, 1f,  0f, 1f, 0f,
                -h,  h,  h,  0f, 0f,  0f, 1f, 0f,
                 h,  h,  h,  1f, 0f,  0f, 1f, 0f,
                 h,  h, -h,  1f, 1f,  0f, 1f, 0f,
                // Bottom face
                -h, -h, -h,  1f, 1f,  0f, -1f, 0f,
                 h, -h, -h,  0f, 1f,  0f, -1f, 0f,
                 h, -h,  h,  0f, 0f,  0f, -1f, 0f,
                -h, -h,  h,  1f, 0f,  0f, -1f, 0f,
                // Right face
                 h, -h, -h,  1f, 1f,  1f, 0f, 0f,
                 h,  h, -h,  1f, 0f,  1f, 0f, 0f,
                 h,  h,  h,  0f, 0f,  1f, 0f, 0f,
                 h, -h,  h,  0f, 1f,  1f, 0f, 0f,
                // Left face
                -h, -h, -h,  0f, 1f, -1f, 0f, 0f,
                -h, -h,  h,  1f, 1f, -1f, 0f, 0f,
                -h,  h,  h,  1f, 0f, -1f, 0f, 0f,
                -h,  h, -h,  0f, 0f, -1f, 0f, 0f
            )

            val indices = shortArrayOf(
                0, 1, 2, 0, 2, 3,       // front
                4, 5, 6, 4, 6, 7,       // back
                8, 9, 10, 8, 10, 11,    // top
                12, 13, 14, 12, 14, 15, // bottom
                16, 17, 18, 16, 18, 19, // right
                20, 21, 22, 20, 22, 23  // left
            )

            return Mesh3D(vertices, indices)
        }
    }

    private var vaoId = 0
    private var vboId = 0
    private var eboId = 0
    private var isInitialized = false

    fun initGl() {
        if (isInitialized) return

        val vaos = IntArray(1)
        val vbos = IntArray(1)
        val ebos = IntArray(1)

        GLES30.glGenVertexArrays(1, vaos, 0)
        GLES30.glGenBuffers(1, vbos, 0)
        GLES30.glGenBuffers(1, ebos, 0)

        vaoId = vaos[0]
        vboId = vbos[0]
        eboId = ebos[0]

        val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(vertexData.size * BYTES_PER_FLOAT)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexData)
                position(0)
            }

        val indexBuffer: ShortBuffer = ByteBuffer.allocateDirect(indexData.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indexData)
                position(0)
            }

        GLES30.glBindVertexArray(vaoId)

        // Bind and upload VBO
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexData.size * BYTES_PER_FLOAT,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Bind and upload EBO
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboId)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            indexData.size * 2,
            indexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        // Attribute 0: vec3 aPosition
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(
            0, 3, GLES30.GL_FLOAT, false,
            VERTEX_STRIDE_BYTES, POSITION_OFFSET
        )

        // Attribute 1: vec2 aTexCoord
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(
            1, 2, GLES30.GL_FLOAT, false,
            VERTEX_STRIDE_BYTES, TEXCOORD_OFFSET
        )

        // Attribute 2: vec3 aNormal
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(
            2, 3, GLES30.GL_FLOAT, false,
            VERTEX_STRIDE_BYTES, NORMAL_OFFSET
        )

        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

        isInitialized = true
    }

    fun draw() {
        if (!isInitialized) initGl()
        GLES30.glBindVertexArray(vaoId)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            indexData.size,
            GLES30.GL_UNSIGNED_SHORT,
            0
        )
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        if (isInitialized) {
            val vaos = intArrayOf(vaoId)
            val vbos = intArrayOf(vboId)
            val ebos = intArrayOf(eboId)
            GLES30.glDeleteVertexArrays(1, vaos, 0)
            GLES30.glDeleteBuffers(1, vbos, 0)
            GLES30.glDeleteBuffers(1, ebos, 0)
            isInitialized = false
        }
    }
}
