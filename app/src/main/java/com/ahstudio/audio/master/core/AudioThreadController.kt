package com.ahstudio.audio.master.core

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AudioThreadController {
    val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun assertNotMainThread(where: String) {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Audio heavy work on main thread at $where" }
    }
    fun release() { bgScope.cancel(); ioScope.cancel() }
}
