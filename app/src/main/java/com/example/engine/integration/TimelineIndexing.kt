package com.example.engine.integration

import com.example.domain.model.*
import java.util.Arrays
import java.util.WeakHashMap
import kotlin.math.abs

data class TimelineIndexItem(
  val id: String,
  val track: String,
  val startMs: Long,
  val endMs: Long,
  val layer: Int = 0
) {
  init { require(endMs >= startMs) { "Timeline interval must not have a negative duration" } }
  fun contains(timeMs: Long): Boolean = timeMs >= startMs && timeMs < endMs
  fun intersects(startMs: Long, endMs: Long): Boolean = startMs < this.endMs && endMs > this.startMs
}

/**
 * Immutable balanced interval tree adapted from Autocut's temporal index.
 * Ah Studio's Timeline remains the source of truth.
 */
class IntervalTree private constructor(private val root: Node?) {
  private data class Node(
    val item: TimelineIndexItem,
    val maxEndMs: Long,
    val left: Node?,
    val right: Node?
  )

  companion object {
    fun build(items: Collection<TimelineIndexItem>): IntervalTree {
      if (items.isEmpty()) return IntervalTree(null)
      val sorted = items.sortedWith(compareBy({ it.startMs }, { it.endMs }, { it.id }))
      fun make(lo: Int, hi: Int): Node? {
        if (lo > hi) return null
        val mid = (lo + hi) ushr 1
        val left = make(lo, mid - 1)
        val right = make(mid + 1, hi)
        return Node(
          sorted[mid],
          maxOf(sorted[mid].endMs, left?.maxEndMs ?: Long.MIN_VALUE, right?.maxEndMs ?: Long.MIN_VALUE),
          left,
          right
        )
      }
      return IntervalTree(make(0, sorted.lastIndex))
    }
  }

  fun queryPoint(timeMs: Long): List<TimelineIndexItem> = buildList {
    queryPoint(root, timeMs, this)
  }

  private fun queryPoint(node: Node?, timeMs: Long, out: MutableList<TimelineIndexItem>) {
    if (node == null || node.maxEndMs <= timeMs) return
    if ((node.left?.maxEndMs ?: Long.MIN_VALUE) > timeMs) queryPoint(node.left, timeMs, out)
    if (node.item.contains(timeMs)) out += node.item
    if (node.item.startMs <= timeMs) queryPoint(node.right, timeMs, out)
  }

  fun queryRange(startMs: Long, endMs: Long): List<TimelineIndexItem> = buildList {
    queryRange(root, startMs, endMs, this)
  }

  private fun queryRange(node: Node?, startMs: Long, endMs: Long, out: MutableList<TimelineIndexItem>) {
    if (node == null || startMs >= endMs || node.maxEndMs <= startMs) return
    queryRange(node.left, startMs, endMs, out)
    if (node.item.intersects(startMs, endMs)) out += node.item
    if (node.item.startMs < endMs) queryRange(node.right, startMs, endMs, out)
  }
}

class TrackSpatialIndex private constructor(
  val trackId: String,
  private val byId: Map<String, TimelineIndexItem>,
  private val sortedItems: List<TimelineIndexItem>,
  private val intervalTree: IntervalTree
) {
  val itemCount: Int get() = sortedItems.size

  fun findItem(id: String): TimelineIndexItem? = byId[id]
  fun queryPoint(timeMs: Long): List<TimelineIndexItem> = intervalTree.queryPoint(timeMs)
  fun queryRange(startMs: Long, endMs: Long): List<TimelineIndexItem> = intervalTree.queryRange(startMs, endMs)

  fun findNext(timeMs: Long): TimelineIndexItem? {
    var low = 0
    var high = sortedItems.lastIndex
    var candidate: TimelineIndexItem? = null
    while (low <= high) {
      val mid = (low + high) ushr 1
      val item = sortedItems[mid]
      if (item.startMs > timeMs) {
        candidate = item
        high = mid - 1
      } else low = mid + 1
    }
    return candidate
  }

  fun findPrevious(timeMs: Long): TimelineIndexItem? {
    var low = 0
    var high = sortedItems.lastIndex
    var candidate: TimelineIndexItem? = null
    while (low <= high) {
      val mid = (low + high) ushr 1
      val item = sortedItems[mid]
      if (item.endMs <= timeMs) {
        candidate = item
        low = mid + 1
      } else high = mid - 1
    }
    return candidate
  }

  fun findClosest(timeMs: Long): TimelineIndexItem? {
    val containing = queryPoint(timeMs).firstOrNull()
    if (containing != null) return containing
    val next = findNext(timeMs)
    val previous = findPrevious(timeMs)
    val nextDistance = next?.let { abs(it.startMs - timeMs) } ?: Long.MAX_VALUE
    val previousDistance = previous?.let { abs(timeMs - it.endMs) } ?: Long.MAX_VALUE
    return if (nextDistance <= previousDistance) next else previous
  }

  fun findOverlaps(): List<Pair<TimelineIndexItem, TimelineIndexItem>> {
    val result = mutableListOf<Pair<TimelineIndexItem, TimelineIndexItem>>()
    for (i in sortedItems.indices) {
      val a = sortedItems[i]
      for (j in (i + 1)..sortedItems.lastIndex) {
        val b = sortedItems[j]
        if (b.startMs >= a.endMs) break
        if (a.intersects(b.startMs, b.endMs)) result += a to b
      }
    }
    return result
  }

  companion object {
    fun build(trackId: String, items: Collection<TimelineIndexItem>): TrackSpatialIndex {
      val sorted = items.sortedWith(compareBy({ it.startMs }, { it.endMs }, { it.id }))
      return TrackSpatialIndex(trackId, sorted.associateBy { it.id }, sorted, IntervalTree.build(sorted))
    }
  }
}

class TimelineSnapIndex private constructor(private val points: LongArray) {
  fun findClosest(candidateMs: Long, thresholdMs: Long, additionalPoints: LongArray = LongArray(0)): Long? {
    if (points.isEmpty() || thresholdMs < 0L) return null
    val insertion = Arrays.binarySearch(points, candidateMs).let { if (it >= 0) it else -it - 1 }
    var best: Long? = null
    var bestDistance = thresholdMs + 1
    val from = maxOf(0, insertion - 1)
    val to = minOf(points.lastIndex, insertion)
    if (from <= to) {
      for (i in from..to) {
        val distance = abs(points[i] - candidateMs)
        if (distance <= thresholdMs && distance < bestDistance) {
          bestDistance = distance
          best = points[i]
        }
      }
    }
    for (point in additionalPoints) {
      val distance = abs(point - candidateMs)
      if (distance <= thresholdMs && distance < bestDistance) {
        bestDistance = distance
        best = point
      }
    }
    return best
  }

  fun points(): LongArray = points.copyOf()

  companion object {
    fun build(points: Collection<Long>): TimelineSnapIndex =
      TimelineSnapIndex(points.distinct().sorted().toLongArray())
  }
}

/**
 * Global index over Ah Studio's legacy Timeline model.
 * The immutable Timeline is authoritative; the index is only a derived performance structure.
 */
class AdvancedTimelineIndex private constructor(
  private val items: List<TimelineIndexItem>,
  private val byId: Map<String, TimelineIndexItem>,
  private val tracks: Map<String, TrackSpatialIndex>,
  private val intervalTree: IntervalTree,
  val snapIndex: TimelineSnapIndex
) {
  val totalItems: Int get() = items.size
  val trackIds: Set<String> get() = tracks.keys

  fun findClip(id: String): TimelineIndexItem? = byId[id]
  fun getClipsAt(timeMs: Long): List<TimelineIndexItem> = intervalTree.queryPoint(timeMs)
  fun getVisibleRange(startMs: Long, endMs: Long): List<TimelineIndexItem> = intervalTree.queryRange(startMs, endMs)
  fun getTrackIndex(track: String): TrackSpatialIndex? = tracks[track]
  fun getTrackClips(track: String, startMs: Long, endMs: Long): List<TimelineIndexItem> =
    tracks[track]?.queryRange(startMs, endMs).orEmpty()
  fun findOverlaps(track: String): List<Pair<TimelineIndexItem, TimelineIndexItem>> =
    tracks[track]?.findOverlaps().orEmpty()

  companion object {
    private val cache = WeakHashMap<Timeline, AdvancedTimelineIndex>()

    fun from(timeline: Timeline): AdvancedTimelineIndex =
      synchronized(cache) { cache[timeline] ?: build(timeline).also { cache[timeline] = it } }

    fun build(timeline: Timeline, ignoreClipIds: Set<String> = emptySet()): AdvancedTimelineIndex {
      val all = mutableListOf<TimelineIndexItem>()
      val snapPoints = mutableSetOf(0L, timeline.totalDurationMs)

      fun addItem(id: String, track: String, startMs: Long, durationMs: Long, layer: Int, keyframes: List<ClipKeyframe>) {
        if (id in ignoreClipIds) return
        val safeDuration = durationMs.coerceAtLeast(0L)
        val endMs = startMs + safeDuration
        all += TimelineIndexItem(id, track, startMs, endMs, layer)
        snapPoints += startMs
        snapPoints += endMs
        keyframes.forEach { snapPoints += startMs + it.timeMs }
      }

      timeline.videoClips.forEachIndexed { layer, clip ->
        addItem(clip.id, "video", clip.timelineStartMs, clip.durationMs, layer, clip.keyframes)
      }
      timeline.overlayClips.forEachIndexed { layer, clip ->
        addItem(clip.id, "overlay", clip.timelineStartMs, clip.durationMs, layer, clip.keyframes)
      }
      timeline.audioClips.forEachIndexed { layer, clip ->
        addItem(clip.id, "audio", clip.timelineStartMs, clip.durationMs, layer, clip.keyframes)
      }
      timeline.textClips.forEachIndexed { layer, clip ->
        addItem(clip.id, "text", clip.timelineStartMs, clip.durationMs, layer, clip.keyframes)
      }
      timeline.stickerClips.forEachIndexed { layer, clip ->
        addItem(clip.id, "sticker", clip.timelineStartMs, clip.durationMs, layer, clip.keyframes)
      }
      timeline.effectClips.forEachIndexed { layer, clip ->
        addItem(clip.id, "effect", clip.timelineStartMs, clip.durationMs, layer, clip.keyframes)
      }
      timeline.shapeClips.forEachIndexed { layer, clip ->
        addItem(clip.id, "shape", clip.timelineStartMs, clip.durationMs, layer, clip.keyframes)
      }

      timeline.transitions.forEach { transition ->
        timeline.videoClips.getOrNull(transition.clipIndexBefore)?.let { clip ->
          val cutMs = clip.timelineStartMs + clip.durationMs
          snapPoints += cutMs
          snapPoints += (cutMs - transition.durationMs / 2).coerceAtLeast(0L)
          snapPoints += cutMs + transition.durationMs / 2
        }
      }

      timeline.audioClips.forEach { clip ->
        if (clip.isMuted || clip.waveformData.isEmpty()) return@forEach
        val step = (clip.waveformData.size / 20).coerceAtLeast(1)
        for (i in clip.waveformData.indices step step) {
          if (clip.waveformData[i] > 0.8f) {
            snapPoints += clip.timelineStartMs +
              (i.toDouble() / clip.waveformData.size * clip.durationMs).toLong()
          }
        }
      }

      val grouped = all.groupBy { it.track }
      val trackIndexes = grouped.mapValues { (track, values) -> TrackSpatialIndex.build(track, values) }
      return AdvancedTimelineIndex(
        items = all,
        byId = all.associateBy { it.id },
        tracks = trackIndexes,
        intervalTree = IntervalTree.build(all),
        snapIndex = TimelineSnapIndex.build(snapPoints)
      )
    }
  }
}
