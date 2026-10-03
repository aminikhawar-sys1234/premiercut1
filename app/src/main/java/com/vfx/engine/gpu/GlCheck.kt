package com.vfx.engine.gpu

import android.opengl.GLES30
import com.vfx.engine.core.EffectEngineException

object GlCheck {
    fun glError(where: String) {
        val err = GLES30.glGetError()
        if (err != GLES30.GL_NO_ERROR)
            throw EffectEngineException.EglFailure("GL $where", err)
    }
}
