package com.example.engine.effects.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class HumanDeformationQualityTest {
  @Test fun capabilityPolicySelectsHighForModernDevice() {
    assertEquals(HumanDeformationQuality.HIGH, HumanDeformationQuality.fromCapability(true, 512))
  }
  @Test fun capabilityPolicySelectsLowForConstrainedDevice() {
    assertEquals(HumanDeformationQuality.LOW, HumanDeformationQuality.fromCapability(false, 96))
  }
}
