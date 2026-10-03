package com.vfx.engine.core.asset

interface AssetProvider {
  fun loadAssetText(assetPath: String): String
  fun loadAssetBytes(assetPath: String): ByteArray
}
