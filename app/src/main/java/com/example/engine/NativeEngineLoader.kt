package com.example.engine

import android.util.Log

object NativeEngineLoader {
  private var isLoaded = false
  private var isInitialized = false

  fun loadLibrary(): Boolean {
    // Pure Kotlin + Media3 + GPU Shader composition pipeline is active.
    // Native external build is disabled, avoid throwing UnsatisfiedLinkError.
    isLoaded = false
    isInitialized = false
    return false
  }

  fun isEngineLoaded(): Boolean = isLoaded
  fun isEngineInitialized(): Boolean = isInitialized
}
