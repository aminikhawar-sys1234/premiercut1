package com.example

import com.example.domain.model.EffectType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfessionalEffectsCatalogTest {
  @Test fun declaredExtendedVfxCatalogContainsAll200VfxEntries() {
    assertEquals(200, EffectType.values().count { it.name.startsWith("VFX_") })
  }

  @Test fun aiCatalogContainsAll16AiEntries() {
    assertEquals(16, EffectType.values().count { it.name.startsWith("AI_") })
  }
}
