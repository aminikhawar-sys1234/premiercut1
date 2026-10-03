package com.ahstudio.face.detection

import android.media.MediaMetadataRetriever
import android.os.Build

/** Reliable default provider — detection frames downscaled to <=640px. */
class RetrieverFrameProvider(
    private val retrieverFor: (String) -> MediaMetadataRetriever,
) : FrameProvider {

    private val dims = HashMap<String, Pair<Int, Int>>()
    private val retrievers = HashMap<String, MediaMetadataRetriever>()

    override suspend fun frame(clipId: String, sourceTimeUs: Long): DetectionFrame? {
        val r = retrievers.getOrPut(clipId) { retrieverFor(clipId) }
        val bmp = if (Build.VERSION.SDK_INT >= 27) {
            r.getScaledFrameAtTime(sourceTimeUs, MediaMetadataRetriever.OPTION_CLOSEST, 640, 640)
        } else {
            r.getFrameAtTime(sourceTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        }
        return bmp?.let { DetectionFrame.BitmapFrame(it, sourceTimeUs) }
    }

    override fun dimensionsOf(clipId: String): Pair<Int, Int> = dims.getOrPut(clipId) {
        val r = retrievers.getOrPut(clipId) { retrieverFor(clipId) }
        val (rw, rh) = rawDims(r)
        val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rot == 90 || rot == 270) Pair(rh, rw) else Pair(rw, rh)
    }

    private fun rawDims(r: MediaMetadataRetriever) = Pair(
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
    )

    override fun close() {
        retrievers.values.forEach { runCatching { it.release() } }
        retrievers.clear(); dims.clear()
    }
}
