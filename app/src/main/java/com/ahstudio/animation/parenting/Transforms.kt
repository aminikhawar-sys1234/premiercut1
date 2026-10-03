package com.ahstudio.animation.parenting

import com.ahstudio.animation.math.Mat3
import com.ahstudio.animation.math.Vec2

/** Layer local transform -- anchor-aware, flip-aware. Opacity travels with composition blending. */
data class Transform2D(
    val anchor: Vec2 = Vec2.ZERO,
    val position: Vec2 = Vec2.ZERO,
    val scale: Vec2 = Vec2.ONE,
    val rotationDeg: Double = 0.0,
    val skew: Vec2 = Vec2.ZERO,
    val opacity: Double = 100.0,
    val flipX: Boolean = false,
    val flipY: Boolean = false
) {
    /** M = T(pos) * T(anchor) * R * Skew * S(flip) * T(-anchor) */
    fun toMatrix(): Mat3 =
        Mat3.translation(position.x, position.y) *
            Mat3.translation(anchor.x, anchor.y) *
            Mat3.rotationDeg(rotationDeg) *
            Mat3.skew(skew.x, skew.y) *
            Mat3.scaling(scale.x * (if (flipX) -1.0 else 1.0), scale.y * (if (flipY) -1.0 else 1.0)) *
            Mat3.translation(-anchor.x, -anchor.y)
}

/** Host implements over its existing composition hierarchy -- engine never owns layer storage. */
interface ParentResolver {
    fun parentIdOf(layerId: String): String?
    fun localTransformOf(layerId: String): Transform2D?
}

class MapParentResolver(
    private val parents: Map<String, String>,
    private val locals: Map<String, Transform2D>
) : ParentResolver {
    override fun parentIdOf(layerId: String) = parents[layerId]
    override fun localTransformOf(layerId: String) = locals[layerId]
}

object WorldTransformEvaluator {
    /**
     * world = PROD(parent...child) local matrices. Cycle-safe (visited set + maxDepth).
     */
    fun worldMatrix(layerId: String, resolver: ParentResolver, maxDepth: Int = 64): Mat3 {
        val chain = ArrayList<String>(8)
        var cur: String? = layerId
        val visited = HashSet<String>()
        while (cur != null && chain.size < maxDepth && visited.add(cur)) {
            chain.add(cur)
            cur = resolver.parentIdOf(cur)
        }
        var m = Mat3.IDENTITY
        for (i in chain.indices.reversed()) {
            val t = resolver.localTransformOf(chain[i]) ?: continue
            m = m * t.toMatrix()
        }
        return m
    }

    fun localToWorldPoint(layerId: String, p: Vec2, resolver: ParentResolver): Vec2 =
        worldMatrix(layerId, resolver).transform(p)

    fun worldToLocalPoint(layerId: String, p: Vec2, resolver: ParentResolver): Vec2 =
        worldMatrix(layerId, resolver).inverse().transform(p)
}
