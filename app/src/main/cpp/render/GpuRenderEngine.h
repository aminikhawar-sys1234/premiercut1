#ifndef GPU_RENDER_ENGINE_H
#define GPU_RENDER_ENGINE_H

#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <android/log.h>
#include <vector>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <cstdint>

#define LOG_TAG "GpuRenderEngine"

#ifdef NDEBUG
#define CHECK_GL_ERROR(op)
#else
#define CHECK_GL_ERROR(op) checkGlError(op)
#endif

namespace ah_engine {

enum class BlendMode {
    NORMAL = 0,
    ADDITIVE = 1,
    MULTIPLY = 2,
    SCREEN = 3,
    PREMULTIPLIED = 4
};

enum class LayerType {
    BASE_VIDEO = 0,
    VIDEO = 1,
    IMAGE_STICKER = 2,
    EFFECT_OVERLAY = 3,
    TEXT = 4
};

struct RenderLayer {
    uint32_t id{0};
    GLuint textureId{0};
    LayerType type{LayerType::BASE_VIDEO};
    bool isVisible{true};
    int zOrder{0};

    float posX{0.0f};      // Center X in normalized viewport [-1.0, 1.0]
    float posY{0.0f};      // Center Y in normalized viewport [-1.0, 1.0]
    float scaleX{1.0f};
    float scaleY{1.0f};
    float rotation{0.0f};  // Rotation in degrees
    float width{1.0f};     // Normalized width relative to canvas [0.0 - 1.0]
    float height{1.0f};    // Normalized height relative to canvas [0.0 - 1.0]
    float opacity{1.0f};

    // UV Coordinates / Mapping
    float uOffset{0.0f};
    float vOffset{0.0f};
    float uScale{1.0f};
    float vScale{1.0f};

    BlendMode blendMode{BlendMode::NORMAL};

    // Custom transform matrix (4x4 column-major). Used if useCustomMatrix == true
    float transformMatrix[16];
    bool useCustomMatrix{false};
};

struct FrameBufferObject {
    GLuint fboId{0};
    GLuint textureId{0};
    int width{0};
    int height{0};
    bool isValid{false};
};

class GpuRenderEngine {
public:
    GpuRenderEngine();
    ~GpuRenderEngine();

    // Initialization & Viewport Configuration
    bool init();
    void resize(int width, int height);

    // Main multi-layer render call (Supports 25+ simultaneous layers deterministically)
    void renderFrame(const std::vector<RenderLayer>& layers);

    // Off-screen rendering target methods
    void beginOffscreen();
    GLuint endOffscreen();

    // Resource Management & Lifecycle
    void onContextLost();
    void release();

    // Utility & State Query
    int getViewportWidth() const { return mViewportWidth; }
    int getViewportHeight() const { return mViewportHeight; }
    bool isInitialized() const { return mIsInitialized; }

private:
    void setupShaders();
    void setupQuadGeometry();
    void setupFbos(int width, int height);
    void releaseFbos();

    void applyBlendMode(BlendMode blendMode);
    void computeLayerMatrix(const RenderLayer& layer, float* outMatrix);
    void checkGlError(const char* op);

    // Matrix Math Helpers (Column-major 4x4)
    static void matrixIdentity(float* m);
    static void matrixOrtho(float* m, float left, float right, float bottom, float top, float nearVal, float farVal);
    static void matrixTranslate(float* m, float tx, float ty, float tz);
    static void matrixRotateZ(float* m, float angleDegrees);
    static void matrixScale(float* m, float sx, float sy, float sz);
    static void matrixMultiply(float* result, const float* a, const float* b);

    bool mIsInitialized{false};
    int mViewportWidth{0};
    int mViewportHeight{0};

    // Shader Program & Handles
    GLuint mProgram{0};
    GLint maPositionHandle{-1};
    GLint maTexCoordHandle{-1};
    GLint muMVPMatrixHandle{-1};
    GLint muSTMatrixHandle{-1};
    GLint muOpacityHandle{-1};
    GLint muPremultipliedHandle{-1};

    // VAO & VBO
    GLuint mVao{0};
    GLuint mVbo{0};

    // Ping-Pong FBO Cache for Multi-Pass Effects
    FrameBufferObject mPingPongFbo[2];
    int mActivePingPongIndex{0};
    bool mFbosInitialized{false};

    // Projection & Workspace Matrices
    float mProjectionMatrix[16];
};

} // namespace ah_engine

#endif // GPU_RENDER_ENGINE_H
