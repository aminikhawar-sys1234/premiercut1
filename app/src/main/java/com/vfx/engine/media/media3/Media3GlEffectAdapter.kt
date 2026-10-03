package com.vfx.engine.media.media3

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.vfx.engine.core.effect.EffectInstance
import com.vfx.engine.core.stack.EffectStack

@OptIn(UnstableApi::class)
class Media3GlEffectAdapter(
  val effectInstance: EffectInstance
) : GlEffect {

  private val vfxEffect = VfxGlEffect(EffectStack(listOf(effectInstance)))

  override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
    return vfxEffect.toGlShaderProgram(context, useHdr)
  }
}

@OptIn(UnstableApi::class)
object Media3TransformerBridge {
  fun adaptEffectToMedia3(instance: EffectInstance): GlEffect {
    return Media3GlEffectAdapter(instance)
  }

  fun adaptStackToMedia3(stack: EffectStack): GlEffect {
    return VfxGlEffect(stack)
  }
}
