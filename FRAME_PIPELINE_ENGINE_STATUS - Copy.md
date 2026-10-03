# AH Studio 6-8 — Frame Pipeline Engine

## Current implementation

The export stack now contains a real hardware video frame pipeline for timelines without active audio:

`MediaCodec decoder -> SurfaceTexture/OES -> GPU composition/effects -> EGL encoder Surface -> MediaCodec encoder -> MediaMuxer`

The pipeline uses persistent per-video-clip `MediaCodec` surface decoders. Video pixels remain GPU-resident as `GL_TEXTURE_EXTERNAL_OES`; the hardware path does not create a Bitmap or call `glReadPixels()` for video frames.

## Stages

- **Frame producer:** deterministic frame index/PTS generation from the AH Studio timeline.
- **Decode stage:** bounded executor feeding MediaCodec surface decoders.
- **GPU stage:** existing `VideoCompositionEngine` and `GpuCompositionRenderer` are reused; OES textures are supplied directly to composition.
- **Encode stage:** MediaCodec surface input with a dedicated output-drain thread.
- **Mux stage:** MediaMuxer is started after encoder format is available and encoded samples are drained continuously.

## Backpressure

The frame queue is bounded to three frame descriptors. Producers wait rather than growing memory without limit. Normal export never intentionally drops frames.

## Timing

Video PTS is deterministic:

`PTS(n) = n * 1_000_000 / outputFps`

Timeline position is derived from that PTS. Decoder arrival time is never used as the exported PTS.

## Zero-copy metrics

`AsyncFramePipelineMetrics` tracks decoded, GPU, encoded, dropped, duplicated, queue/backpressure, copy and zero-copy counters. Hardware video frames do not use CPU pixel copies.

## CPU/audio compatibility

Projects with active audio continue through the existing `VideoExporter` path so the established sample-accurate `AudioExportProcessor` mixer is preserved. This is intentional: the new asynchronous video path must not silently remove or desynchronize audio.

The existing CPU/YUV fallback remains available for devices that cannot satisfy the surface-decoder/encoder capability requirements.

## Cancellation and cleanup

Cancellation is propagated through the professional export engine into the active frame pipeline. Codec, Surface, SurfaceTexture, EGL, renderer, muxer and executor resources are released from the pipeline lifecycle.

## Tests

Added tests cover bounded queue behavior, frame ordering and zero-copy metrics. Full Android/NDK runtime verification must be performed by GitHub Actions/device testing; the connector session cannot execute the Android SDK/NDK build locally.

## Known limitations

1. The new asynchronous OES pipeline is currently selected for video-only timelines. Audio-bearing exports retain the existing audio-capable exporter.
2. SurfaceTexture decoder seeking is intentionally conservative and may be device-dependent for unusual variable-frame-rate sources.
3. Device-specific MediaCodec decoder/encoder behavior still requires physical-device validation, especially at 4K/60fps.
4. The fast pipeline's `GpuCompositionRenderer.render()` call only applies `timeline.adjustments`, `timeline.filter` and chroma key — it does not run `VideoEffectRenderer` (the 200+ VFX catalog, body-deformation ML bridge, or any per-clip `EffectClip`). `ProfessionalExportEngine` now excludes any timeline with non-empty `effectClips` from this path so effects are never silently dropped; those exports fall back to the full `VideoExporter` path instead. Giving the fast pipeline real per-clip effect compositing (likely via an OES→FBO pass that also runs the Canvas-based extended-effect renderer before re-upload) is the next integration step if fast-path export is wanted for effects-heavy timelines too).
5. `ProfessionalExportEngine` now polls `AsyncFramePipelineMetrics.encodedFrames` (fast path) or `VideoExporter.exportState` (fallback path) during rendering to report live progress instead of holding at "starting" until verification. This has not been build/run verified in this session (see Verification below).
