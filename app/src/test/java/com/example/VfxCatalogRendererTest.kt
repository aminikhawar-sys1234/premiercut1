package com.example

import com.example.domain.model.EffectType
import com.example.engine.composition.VfxCatalogRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VfxCatalogRendererTest {
  @Test
  fun everyVfxCatalogEntryHasConcreteRendererRouting() {
    val vfx = EffectType.values().filter { it.name.startsWith("VFX_") }
    assertEquals(200, vfx.size)
    assertTrue(vfx.all { VfxCatalogRenderer.supports(it) })
    assertTrue(vfx.all { !VfxCatalogRenderer.catalogGroup(it).isNullOrBlank() })
  }

  @Test
  fun allThirtyBodyCatalogEffectsUseMlBridge() {
    val body = EffectType.values().filter { it.name.startsWith("VFX_BODY_") }
    assertEquals(30, body.size)
    assertTrue(body.all(VfxCatalogRenderer::requiresMlDeformation))
  }
}
