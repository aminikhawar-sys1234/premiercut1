# AH Studio — Next-Gen Hardware Export Engine

## Current architecture

The existing `VideoExporter` already has a real MediaCodec surface-input branch: it checks `COLOR_FormatSurface`, creates a MediaCodec input Surface, creates an `EglCore` recordable context and `WindowSurface`, renders with `GpuCompositionRenderer`, sets presentation time, and swaps the encoder surface. The CPU YUV path remains a fallback.

## New infrastructure

- `HardwareCodecCapabilities` performs device-aware AVC/HEVC capability selection and explicitly checks surface-input support, dimensions, frame rate and VBR support.
- `ExportPipelineMetrics` provides thread-safe counters for rendered/encoded/dropped/duplicated frames and GPU↔CPU copy counts.
- `HardwareExportMetricsTest` covers the zero-readback metric invariant.

## Important limitation

The current `VideoExporter` GPU branch still obtains source video frames through `fetchClipBitmap(...)` and uploads those bitmaps to OpenGL. Therefore the exporter is **GPU-surface encoded but is not yet a true decoder-SurfaceTexture/OES zero-copy decode→GPU path** for source video.

Do not label that path as zero-copy. The correct next integration step is to replace the per-frame `MediaMetadataRetriever`/Bitmap source path with a persistent MediaCodec decoder output Surface + SurfaceTexture/OES texture per active video source, while keeping the current EGL encoder surface and CPU fallback.

## Verification

GitHub source inspection confirms the encoder surface path exists in `VideoExporter`. Full Android/NDK Gradle execution was not available through the repository connector, so these changes are not claimed as build-verified in this environment.
