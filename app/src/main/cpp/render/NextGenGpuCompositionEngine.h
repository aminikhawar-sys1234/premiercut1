#ifndef NEXT_GEN_GPU_COMPOSITION_ENGINE_H
#define NEXT_GEN_GPU_COMPOSITION_ENGINE_H

#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#include <cstdint>
#include <vector>
#include "GpuRenderEngine.h"

namespace ah_engine {

struct GpuRenderStats {
    uint64_t frames = 0;
    uint64_t drawCalls = 0;
    uint64_t shaderSwitches = 0;
    uint64_t textureBinds = 0;
    uint64_t fboSwitches = 0;
    uint64_t skippedLayers = 0;
    float lastFrameMs = 0.0f;
};

enum class NativeEffectType {
    NONE = 0,
    COLOR_ADJUST = 1,
    VIGNETTE = 2,
    GLITCH = 3,
    CHROMATIC_ABERRATION = 4,
    SHARPEN = 5
};

struct NativeEffectPass {
    NativeEffectType type{NativeEffectType::NONE};
    float intensity{1.0f};
    float param1{0.0f};
    float param2{0.0f};
    float timeMs{0.0f};
};

class NextGenGpuCompositionEngine {
public:
    NextGenGpuCompositionEngine();
    ~NextGenGpuCompositionEngine();

    bool init();
    void resize(int width, int height);
    void renderFrame(const std::vector<RenderLayer>& layers);
    void renderExternalTexture(GLuint textureId, const float* texMatrix);
    
    // Multi-pass native GPU effect chain on active target
    void applyEffectPass(const NativeEffectPass& pass);

    void beginOffscreen();
    GLuint endOffscreen();
    void onContextLost();
    void release();

    bool isInitialized() const { return initialized_; }
    const GpuRenderStats& stats() const { return stats_; }

private:
    bool buildPrograms();
    GLuint compile(GLenum type, const char* source);
    void destroyPrograms();
    void ensureTargets(int width, int height);
    void destroyTargets();

    void drawTexture(GLuint texture, const float* mvp, const float* st, float opacity, BlendMode mode, bool external);
    void identity(float* m) const;
    void multiply(float* out, const float* a, const float* b) const;
    void layerMatrix(const RenderLayer& layer, float* out) const;
    void blend(BlendMode mode);
    void bindTarget(GLuint fbo, int width, int height);

    bool initialized_ = false;
    bool offscreenActive_ = false;
    int width_ = 0, height_ = 0;
    GLuint vao_ = 0, vbo_ = 0;
    GLuint program2d_ = 0, programOes_ = 0, programEffects_ = 0;

    GLint mvp2d_ = -1, st2d_ = -1, opacity2d_ = -1, sampler2d_ = -1;
    GLint mvpOes_ = -1, stOes_ = -1, opacityOes_ = -1, samplerOes_ = -1;

    // Effect Shader Uniforms
    GLint fxTypeLoc_ = -1, fxIntensityLoc_ = -1, fxParam1Loc_ = -1, fxParam2Loc_ = -1, fxTimeLoc_ = -1, fxSamplerLoc_ = -1, fxTexSizeLoc_ = -1;

    GLuint fbo_[3] = {0, 0, 0};
    GLuint target_[3] = {0, 0, 0};
    int targetWidth_ = 0, targetHeight_ = 0;
    int activeTarget_ = 0;

    GpuRenderStats stats_;
    float projection_[16]{};
    GLuint lastProgram_ = 0, lastTexture_ = 0;
};

} // namespace ah_engine
#endif
