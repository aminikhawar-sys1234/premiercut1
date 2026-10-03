package com.ahstudio.face.timeline

interface FaceTimelineBridge {
    fun sourceTimeUsOf(clipId: String, masterTimeUs: Long): Long?
    fun activeClipIdAt(masterTimeUs: Long): String?
    fun isMirroredSource(clipId: String): Boolean
    fun sourceDimensions(clipId: String): Pair<Int, Int>?
    fun transformHash(clipId: String): Int
    fun onClipStructureChanged(listener: () -> Unit)
}

interface FaceKeyframeSource {
    fun evaluate(effectId: String, property: FaceEffectProperty, clipLocalUs: Long): Float?
}

enum class FaceEffectProperty { OFFSET_X, OFFSET_Y, SCALE, ROTATION, OPACITY, INTENSITY }

interface FaceCommand { fun run(); fun undo() }
interface CommandBus { fun execute(cmd: FaceCommand) }

interface FaceProjectSink { fun persistFaceEffects(clipId: String, jsonList: List<String>) }
