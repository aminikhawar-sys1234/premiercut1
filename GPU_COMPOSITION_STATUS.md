# AH Studio — Next-Gen GPU Composition Status

## Implemented

- Added `NextGenGpuCompositionEngine` as the active native composition backend.
- Reused the existing `RenderLayer`/JNI contract; no UI or timeline model rewrite.
- Persistent GLES 3 shader programs and VAO/VBO resources.
- Cached uniform handles; no per-layer `glGetUniformLocation` calls.
- Deterministic z-order rendering with stable layer IDs as the tie-breaker.
- Reusable triple offscreen FBO/texture targets with resolution-aware allocation.
- Offscreen state is preserved while `NativeRenderBridge.beginOffscreen()` → `renderFrame()` → `endOffscreen()` is active.
- Direct `samplerExternalOES` rendering entry point for decoder `SurfaceTexture` textures.
- GPU-side blend modes, transforms, UV mapping and opacity.
- Render instrumentation for frames, draw calls, shader switches, texture binds, FBO switches, skipped layers and frame time.
- Native bridge now routes preview/export composition calls through the next-generation backend.

## Existing architecture preserved

`GpuRenderEngine` remains in the repository for compatibility. The JNI bridge uses the new backend, while existing Kotlin composition/export APIs remain unchanged.

## Export path

The existing `VideoExporter` already creates the EGL recordable surface and `GpuCompositionRenderer`, so the native composition backend is now used by that composition path without introducing a second export UI or timeline system.

## Important limitation

The direct OES method is available in the native bridge, but the current `GpuCompositionRenderer` still performs its existing OES-to-2D preprocessing when adjustments/chroma/effect processing are required. This preserves current visual behavior. A future optimization can select the direct OES fast path only when no preprocessing is needed.

## Verification

The GitHub connector can modify and inspect repository sources, but it does not provide a local Android/NDK build environment in this session. Therefore `./gradlew testDebugUnitTest assembleDebug --stacktrace` could not be executed here. The next validation step is to run the repository's Android CI/build and fix any device/NDK-specific issues reported by that build.
