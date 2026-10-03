package com.ahstudio.composition.mask

import com.ahstudio.composition.graph.MaskInstance
import com.ahstudio.composition.graph.PathData

object PathAnim {
    data class PathKf(val timeUs: Long, val path: PathData)
    fun samplePath(m: MaskInstance, @Suppress("UNUSED_PARAMETER") timeUs: Long): PathData = m.path
}
