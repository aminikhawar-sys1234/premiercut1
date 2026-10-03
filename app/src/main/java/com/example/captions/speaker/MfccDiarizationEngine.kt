package com.ahstudio.captions.speaker

import com.ahstudio.captions.audio.AudioChunk
import com.ahstudio.captions.audio.EnergyVad
import com.ahstudio.captions.audio.SpeechRegion
import com.ahstudio.captions.core.model.CaptionSpeaker
import com.ahstudio.captions.core.model.CaptionWord
import com.ahstudio.captions.recognition.SpeechSource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

class MfccDiarizationEngine(
    private val maxSpeakers: Int = 4,
    private val seed: Int = 42,
) : SpeakerDiarizationEngine {

    override val id = "mfcc-kmeans"

    private val speakerColors = longArrayOf(
        0xFF4A90D9L, 0xFFE06666L, 0xFF6AA84FL, 0xFFB085F5L,
    )

    override suspend fun assignSpeakers(
        words: List<CaptionWord>,
        audio: SpeechSource?,
    ): Pair<List<CaptionWord>, List<CaptionSpeaker>> {
        val pcm = audio as? SpeechSource.Pcm
        if (pcm == null || words.isEmpty()) return SingleSpeakerDiarizationEngine().assignSpeakers(words, null)

        val vad = EnergyVad()
        val prep = com.ahstudio.captions.audio.AudioPreprocessor()
        val rate = pcm.sampleRateHz
        val merged = ArrayList<Float>()
        val regions = ArrayList<SpeechRegion>()
        pcm.chunks.collect { raw ->
            val c = prep.process(AudioChunk(raw.samples.copyOf(), raw.startMicros, raw.sampleRateHz))
            merged.addAll(c.samples.toList())
            for (r in vad.analyze(c.samples, c.sampleRateHz))
                regions += SpeechRegion(c.startMicros + r.startMicros, c.startMicros + r.endMicros)
        }
        val clean = EnergyVad.merge(regions)
        if (clean.isEmpty()) return SingleSpeakerDiarizationEngine().assignSpeakers(words, null)

        val embeddings = clean.map { r ->
            val s0 = (r.startMicros * rate / 1_000_000).toInt().coerceIn(0, max(merged.size - 1, 0))
            val s1 = (r.endMicros * rate / 1_000_000).toInt().coerceIn(s0, merged.size)
            if (s1 - s0 < rate / 4) FloatArray(Mfcc.MFCC_DIM)
            else Mfcc.mean(merged.toFloatArray(), s0, s1, rate)
        }

        var bestK = 1; var bestScore = -2f
        for (k in 1..min(maxSpeakers, embeddings.size)) {
            val score = if (k == 1) -1f else Silhouette.score(KMeans.run(embeddings, k, seed).assignments, embeddings)
            if (score > bestScore + 1e-4f) { bestScore = score; bestK = k }
        }
        val clustering = if (bestK == 1) KMeans.Run(assignments = IntArray(embeddings.size), centroids = emptyList())
                         else KMeans.run(embeddings, bestK, seed)

        val clusterOrder = ArrayList<Int>()
        for (a in clustering.assignments) if (a !in clusterOrder) clusterOrder += a
        val labelOf = clusterOrder.withIndex().associate { (i, c) -> c to i }

        fun clusterForTime(us: Long): Int {
            var bestIdx = -1; var bestDist = Double.MAX_VALUE
            for (i in clean.indices) {
                val mid = (clean[i].startMicros + clean[i].endMicros) / 2
                val d = kotlin.math.abs(mid - us).toDouble()
                if (d < bestDist) { bestDist = d; bestIdx = i }
            }
            return labelOf[clustering.assignments[bestIdx]] ?: 0
        }

        val speakers = (0 until clusterOrder.size).map { i ->
            CaptionSpeaker(id = "spk_${i + 1}", label = "Speaker ${i + 1}", colorArgb = speakerColors[i % speakerColors.size])
        }
        val tagged = words.map { w ->
            val c = clusterForTime((w.start.micros + w.end.micros) / 2)
            w.copy(speakerId = speakers[c].id)
        }
        return tagged to speakers
    }
}

object Mfcc {
    const val MFCC_DIM = 13
    private const val FRAME_MS = 25
    private const val HOP_MS = 10
    private const val MEL_FILTERS = 40
    private const val PREEMPH = 0.97f
    private const val MEL_LOW_HZ = 50f

    private fun hzToMel(hz: Float) = 1127f * ln(1f + hz / 700f)
    private fun melToHz(m: Float) = 700f * (kotlin.math.exp(m / 1127f) - 1f)

    fun mean(samples: FloatArray, from: Int, until: Int, rate: Int): FloatArray {
        val frameLen = FRAME_MS * rate / 1000
        val hop = HOP_MS * rate / 1000
        if (until - from < frameLen) return FloatArray(MFCC_DIM)

        val bank = filterbank(rate, frameLen)
        val window = FloatArray(frameLen) { i ->
            (0.54 - 0.46 * cos(2 * PI * i / (frameLen - 1))).toFloat()
        }

        val acc = DoubleArray(MFCC_DIM)
        var frames = 0
        var i = from
        while (i + frameLen <= until) {
            val frame = FloatArray(frameLen)
            for (j in 0 until frameLen) {
                val cur = samples[i + j]
                val prev = if (j == 0) samples[i] else samples[i + j - 1]
                frame[j] = (cur - PREEMPH * prev) * window[j]
            }
            val spec = powerSpectrum(frame)
            val melE = DoubleArray(MEL_FILTERS)
            for (m in 0 until MEL_FILTERS) {
                var e = 0.0
                for (k in bank.mStart[m]..bank.mEnd[m]) e += bank.weights[m][k - bank.mStart[m]] * spec[k]
                melE[m] = ln(max(e, 1e-10))
            }
            for (c in 0 until MFCC_DIM) {
                var s = 0.0
                for (m in 0 until MEL_FILTERS) s += melE[m] * cos(PI * c * (m + 0.5) / MEL_FILTERS)
                acc[c] += s
            }
            frames++
            i += hop
        }
        if (frames == 0) return FloatArray(MFCC_DIM)
        return FloatArray(MFCC_DIM) { (acc[it] / frames).toFloat() }
    }

    private fun powerSpectrum(frame: FloatArray): DoubleArray {
        val n = frame.size
        val bins = n / 2 + 1
        val out = DoubleArray(bins)
        for (k in 0 until bins) {
            var re = 0.0; var im = 0.0
            for (t in 0 until n) {
                val ang = -2.0 * PI * k * t / n
                re += frame[t] * cos(ang); im += frame[t] * kotlin.math.sin(ang)
            }
            out[k] = (re * re + im * im) / n
        }
        return out
    }

    private class Bank(val mStart: IntArray, val mEnd: IntArray, val weights: Array<DoubleArray>)

    private var cachedRate = -1
    private var cachedLen = -1
    private lateinit var cachedBank: Bank

    @Synchronized
    private fun filterbank(rate: Int, frameLen: Int): Bank {
        if (rate == cachedRate && frameLen == cachedLen) return cachedBank
        val bins = frameLen / 2 + 1
        val melLow = hzToMel(MEL_LOW_HZ); val melHigh = hzToMel(rate / 2f)
        val centers = FloatArray(MEL_FILTERS + 2) { i -> melToHz(melLow + (melHigh - melLow) * i / (MEL_FILTERS + 1)) }
        val binHz = rate.toFloat() / frameLen
        val mStart = IntArray(MEL_FILTERS); val mEnd = IntArray(MEL_FILTERS)
        val weights = Array(MEL_FILTERS) { DoubleArray(1) }
        for (m in 0 until MEL_FILTERS) {
            val lo = centers[m]; val mid = centers[m + 1]; val hi = centers[m + 2]
            val k0 = max(0, (lo / binHz).toInt()); val k1 = min(bins - 1, (hi / binHz).toInt() + 1)
            mStart[m] = k0; mEnd[m] = k1
            val w = DoubleArray(max(1, k1 - k0 + 1))
            for (k in k0..k1) {
                val f = k * binHz
                val v = when {
                    f < lo || f > hi -> 0.0
                    f <= mid -> (f - lo).toDouble() / max(mid - lo, 1e-6f).toDouble()
                    else -> (hi - f).toDouble() / max(hi - mid, 1e-6f).toDouble()
                }
                w[k - k0] = v
            }
            weights[m] = w
        }
        cachedRate = rate; cachedLen = frameLen; cachedBank = Bank(mStart, mEnd, weights)
        return cachedBank
    }
}

internal object KMeans {
    class Run(val assignments: IntArray, val centroids: List<FloatArray>)

    fun run(points: List<FloatArray>, k: Int, seed: Int, iters: Int = 25): Run {
        if (points.isEmpty() || k <= 0) return Run(IntArray(points.size), emptyList())
        val rng = Random(seed)
        val centroids = ArrayList<FloatArray>(k)
        centroids += points[rng.nextInt(points.size)].copyOf()
        while (centroids.size < k) {
            val d2 = points.map { p -> centroids.minOf { c -> dist2(p, c) } }
            val total = d2.sum()
            val pick = if (total <= 1e-12) rng.nextInt(points.size)
                       else { var r = rng.nextDouble() * total; var i = 0
                              while (r > d2[i]) { r -= d2[i]; i++ }; i.coerceAtMost(points.size - 1) }
            centroids += points[pick].copyOf()
        }
        val assign = IntArray(points.size)
        var iter = 0
        while (iter < iters) {
            iter++
            var moved = false
            for (p in points.indices) {
                var best = 0; var bd = Double.MAX_VALUE
                for (c in centroids.indices) {
                    val d = dist2(points[p], centroids[c])
                    if (d < bd) { bd = d; best = c }
                }
                if (assign[p] != best) { assign[p] = best; moved = true }
            }
            val dim = points[0].size
            val sums = Array(k) { DoubleArray(dim) }; val counts = IntArray(k)
            for (p in points.indices) {
                counts[assign[p]]++
                for (d in 0 until dim) sums[assign[p]][d] += points[p][d]
            }
            var emptyClusterFound = false
            for (c in centroids.indices) {
                if (counts[c] == 0) { centroids[c] = points[rng.nextInt(points.size)].copyOf(); emptyClusterFound = true; break }
                for (d in 0 until dim) centroids[c][d] = (sums[c][d] / counts[c]).toFloat()
            }
            if (emptyClusterFound) continue
            if (!moved) break
        }
        return Run(assign, centroids)
    }

    fun dist2(a: FloatArray, b: FloatArray): Double {
        var s = 0.0
        for (i in a.indices) { val d = (a[i] - b[i]).toDouble(); s += d * d }
        return s
    }
}

internal object Silhouette {
    fun score(assignments: IntArray, points: List<FloatArray>, sample: Int = 200): Float {
        val n = points.size
        if (n < 2 || assignments.size != n) return -2f
        val idx = if (n <= sample) (0 until n).toList() else (0 until sample).map { (it * n) / sample }
        var total = 0.0; var counted = 0
        for (i in idx) {
            val own = assignments[i]
            var aSum = 0.0; var aCount = 0
            val bSums = HashMap<Int, Double>(); val bCounts = HashMap<Int, Int>()
            for (j in idx) {
                if (i == j) continue
                val d = cosDist(points[i], points[j])
                if (assignments[j] == own) { aSum += d; aCount++ }
                else { bSums[assignments[j]] = (bSums[assignments[j]] ?: 0.0) + d
                       bCounts[assignments[j]] = (bCounts[assignments[j]] ?: 0) + 1 }
            }
            val a = if (aCount == 0) 0.0 else aSum / aCount
            val b = bSums.entries.minOf { entry -> entry.value / bCounts[entry.key]!! }
            val s = if (aCount == 0) 0.0 else (b - a) / max(a, b)
            total += s; counted++
        }
        return if (counted == 0) -2f else (total / counted).toFloat()
    }

    private fun cosDist(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom < 1e-12) 1.0 else 1.0 - dot / denom
    }
}
