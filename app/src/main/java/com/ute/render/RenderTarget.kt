package com.ute.render

/** Any surface the engine draws to: preview texture, bitmap, or encoder surface. */
sealed class RenderTarget {
    abstract val width: Int
    abstract val height: Int
    data class Offscreen(override val width: Int, override val height: Int) : RenderTarget()
    data class Surface(override val width: Int, override val height: Int) : RenderTarget()
}

data class EGLSurfaceProxy(val width: Int, val height: Int)
