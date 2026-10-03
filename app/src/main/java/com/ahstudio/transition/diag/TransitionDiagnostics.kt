package com.ahstudio.transition.diag

import com.ahstudio.transition.core.TransitionError
import java.util.concurrent.atomic.AtomicLong

/** Debug info (§34). Disabled by default; production builds keep enabled=false. */
class TransitionDiagnostics(var enabled: Boolean = false) {
    var logger: (String) -> Unit = { android.util.Log.d("TransitionEngine", it) }

    val framesRendered = AtomicLong()
    val errorCount = AtomicLong()
    var renderPassCount = 0

    fun log(msg: String) { if (enabled) logger(msg) }
    fun onError(e: TransitionError) {
        errorCount.incrementAndGet()
        log("ERROR: ${e::class.simpleName}: ${e.message}")
    }
    fun countFrame() { framesRendered.incrementAndGet() }

    fun dump(shaderHits: Long, shaderMisses: Long, fboIdle: Int, fboLive: Int): String =
        "frames=$framesRendered passCount=$renderPassCount shaderHits=$shaderHits " +
        "shaderMisses=$shaderMisses fboIdle=$fboIdle fboLive=$fboLive errors=$errorCount"
}
