#include "GpuRenderEngine.h"

namespace ah_engine {

static const char* VERTEX_SHADER_SOURCE = R"glsl(#version 300 es
layout(location = 0) in vec4 aPosition;
layout(location = 1) in vec2 aTexCoord;

uniform mat4 uMVPMatrix;
uniform mat4 uSTMatrix;

out vec2 vTexCoord;

void main() {
    gl_Position = uMVPMatrix * aPosition;
    vTexCoord = (uSTMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
}
)glsl";

static const char* FRAGMENT_SHADER_SOURCE = R"glsl(#version 300 es
precision mediump float;

in vec2 vTexCoord;
uniform sampler2D uTexture;
uniform float uOpacity;
uniform int uPremultiplied;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(uTexture, vTexCoord);
    if (uPremultiplied == 1) {
        fragColor = vec4(texColor.rgb * uOpacity, texColor.a * uOpacity);
    } else {
        fragColor = vec4(texColor.rgb, texColor.a * uOpacity);
    }
}
)glsl";

GpuRenderEngine::GpuRenderEngine() {
    matrixIdentity(mProjectionMatrix);
}

GpuRenderEngine::~GpuRenderEngine() {
    release();
}

bool GpuRenderEngine::init() {
    if (mIsInitialized) return true;

    setupShaders();
    setupQuadGeometry();

    mIsInitialized = (mProgram != 0 && mVao != 0);
    if (mIsInitialized) {
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "GpuRenderEngine initialized successfully with GLES 3.0 VAO/VBO");
    } else {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Failed to initialize GpuRenderEngine GLES 3.0 resources");
    }
    return mIsInitialized;
}

void GpuRenderEngine::setupShaders() {
    GLuint vertexShader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertexShader, 1, &VERTEX_SHADER_SOURCE, nullptr);
    glCompileShader(vertexShader);

    GLint compiled = 0;
    glGetShaderiv(vertexShader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(vertexShader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetShaderInfoLog(vertexShader, infoLen, nullptr, infoLog.data());
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error compiling vertex shader: %s", infoLog.data());
        }
        glDeleteShader(vertexShader);
        return;
    }

    GLuint fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fragmentShader, 1, &FRAGMENT_SHADER_SOURCE, nullptr);
    glCompileShader(fragmentShader);

    glGetShaderiv(fragmentShader, GL_COMPILE_STATUS, &compiled);
    if (!compiled) {
        GLint infoLen = 0;
        glGetShaderiv(fragmentShader, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetShaderInfoLog(fragmentShader, infoLen, nullptr, infoLog.data());
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error compiling fragment shader: %s", infoLog.data());
        }
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return;
    }

    mProgram = glCreateProgram();
    glAttachShader(mProgram, vertexShader);
    glAttachShader(mProgram, fragmentShader);
    glLinkProgram(mProgram);

    GLint linked = 0;
    glGetProgramiv(mProgram, GL_LINK_STATUS, &linked);
    if (!linked) {
        GLint infoLen = 0;
        glGetProgramiv(mProgram, GL_INFO_LOG_LENGTH, &infoLen);
        if (infoLen > 0) {
            std::vector<char> infoLog(infoLen);
            glGetProgramInfoLog(mProgram, infoLen, nullptr, infoLog.data());
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Error linking program: %s", infoLog.data());
        }
        glDeleteProgram(mProgram);
        mProgram = 0;
    }

    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);

    if (mProgram != 0) {
        maPositionHandle = glGetAttribLocation(mProgram, "aPosition");
        maTexCoordHandle = glGetAttribLocation(mProgram, "aTexCoord");
        muMVPMatrixHandle = glGetUniformLocation(mProgram, "uMVPMatrix");
        muSTMatrixHandle = glGetUniformLocation(mProgram, "uSTMatrix");
        muOpacityHandle = glGetUniformLocation(mProgram, "uOpacity");
        muPremultipliedHandle = glGetUniformLocation(mProgram, "uPremultiplied");
    }
}

void GpuRenderEngine::setupQuadGeometry() {
    // Full-screen Quad Geometry: Position (X, Y, Z), TexCoord (U, V)
    // Coords span [-1.0f, 1.0f] for full viewport coverage
    // FBO textures in OpenGL ES have V=1.0 at the top and V=0.0 at the bottom
    const float quadData[] = {
        // Position           // TexCoord
        -1.0f,  1.0f, 0.0f,   0.0f, 1.0f, // Top-Left
        -1.0f, -1.0f, 0.0f,   0.0f, 0.0f, // Bottom-Left
         1.0f,  1.0f, 0.0f,   1.0f, 1.0f, // Top-Right
         1.0f, -1.0f, 0.0f,   1.0f, 0.0f  // Bottom-Right
    };

    glGenVertexArrays(1, &mVao);
    glGenBuffers(1, &mVbo);

    glBindVertexArray(mVao);
    glBindBuffer(GL_ARRAY_BUFFER, mVbo);
    glBufferData(GL_ARRAY_BUFFER, sizeof(quadData), quadData, GL_STATIC_DRAW);

    // Position attribute
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)0);

    // TexCoord attribute
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)(3 * sizeof(float)));

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);

    CHECK_GL_ERROR("setupQuadGeometry");
}

void GpuRenderEngine::resize(int width, int height) {
    if (width <= 0 || height <= 0) return;
    mViewportWidth = width;
    mViewportHeight = height;

    glViewport(0, 0, width, height);

    // Setup Orthographic Projection Matrix for Viewport [-1, 1]
    matrixOrtho(mProjectionMatrix, -1.0f, 1.0f, -1.0f, 1.0f, -1.0f, 1.0f);

    setupFbos(width, height);
}

void GpuRenderEngine::setupFbos(int width, int height) {
    releaseFbos();

    for (int i = 0; i < 2; ++i) {
        glGenFramebuffers(1, &mPingPongFbo[i].fboId);
        glGenTextures(1, &mPingPongFbo[i].textureId);

        glBindTexture(GL_TEXTURE_2D, mPingPongFbo[i].textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        glBindFramebuffer(GL_FRAMEBUFFER, mPingPongFbo[i].fboId);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mPingPongFbo[i].textureId, 0);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Ping-Pong Framebuffer %d incomplete", i);
        }

        mPingPongFbo[i].width = width;
        mPingPongFbo[i].height = height;
        mPingPongFbo[i].isValid = true;
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
    mFbosInitialized = true;

    CHECK_GL_ERROR("setupFbos");
}

void GpuRenderEngine::releaseFbos() {
    for (int i = 0; i < 2; ++i) {
        if (mPingPongFbo[i].fboId != 0) {
            glDeleteFramebuffers(1, &mPingPongFbo[i].fboId);
            mPingPongFbo[i].fboId = 0;
        }
        if (mPingPongFbo[i].textureId != 0) {
            glDeleteTextures(1, &mPingPongFbo[i].textureId);
            mPingPongFbo[i].textureId = 0;
        }
        mPingPongFbo[i].isValid = false;
    }
    mFbosInitialized = false;
}

void GpuRenderEngine::renderFrame(const std::vector<RenderLayer>& inputLayers) {
    if (!mIsInitialized || mProgram == 0) return;

    // Filter invisible layers & sort deterministically by zOrder & LayerType priority
    std::vector<RenderLayer> layers;
    layers.reserve(inputLayers.size());

    for (const auto& layer : inputLayers) {
        if (layer.isVisible && layer.textureId != 0 && layer.opacity > 0.001f) {
            layers.push_back(layer);
        }
    }

    if (layers.empty()) return;

    // Deterministic sorting: Primary by zOrder, Secondary by LayerType priority
    std::stable_sort(layers.begin(), layers.end(), [](const RenderLayer& a, const RenderLayer& b) {
        if (a.zOrder != b.zOrder) {
            return a.zOrder < b.zOrder;
        }
        return static_cast<int>(a.type) < static_cast<int>(b.type);
    });

    // Configure GL Global State for Multi-Layer Rendering
    glUseProgram(mProgram);
    glBindVertexArray(mVao);
    glDisable(GL_DEPTH_TEST);
    glEnable(GL_BLEND);
    glDisable(GL_CULL_FACE);

    // Render each layer in sorted deterministic order
    for (const auto& layer : layers) {
        applyBlendMode(layer.blendMode);

        // Compute Layer MVP Matrix
        float mvpMatrix[16];
        if (layer.useCustomMatrix) {
            matrixMultiply(mvpMatrix, mProjectionMatrix, layer.transformMatrix);
        } else {
            float modelMatrix[16];
            computeLayerMatrix(layer, modelMatrix);
            matrixMultiply(mvpMatrix, mProjectionMatrix, modelMatrix);
        }

        // Compute UV ST Matrix
        float stMatrix[16];
        matrixIdentity(stMatrix);
        stMatrix[0] = layer.uScale;
        stMatrix[5] = layer.vScale;
        stMatrix[12] = layer.uOffset;
        stMatrix[13] = layer.vOffset;

        // Bind Uniforms
        glUniformMatrix4fv(muMVPMatrixHandle, 1, GL_FALSE, mvpMatrix);
        glUniformMatrix4fv(muSTMatrixHandle, 1, GL_FALSE, stMatrix);
        glUniform1f(muOpacityHandle, layer.opacity);
        glUniform1i(muPremultipliedHandle, (layer.blendMode == BlendMode::PREMULTIPLIED) ? 1 : 0);

        // Bind Texture
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, layer.textureId);
        glUniform1i(glGetUniformLocation(mProgram, "uTexture"), 0);

        // Draw Quad
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }

    glBindVertexArray(0);
    glUseProgram(0);
    glDisable(GL_BLEND);

    CHECK_GL_ERROR("renderFrame");
}

void GpuRenderEngine::beginOffscreen() {
    if (!mFbosInitialized) return;
    mActivePingPongIndex = 0;
    glBindFramebuffer(GL_FRAMEBUFFER, mPingPongFbo[mActivePingPongIndex].fboId);
    glViewport(0, 0, mPingPongFbo[mActivePingPongIndex].width, mPingPongFbo[mActivePingPongIndex].height);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
}

GLuint GpuRenderEngine::endOffscreen() {
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, mViewportWidth, mViewportHeight);
    if (!mFbosInitialized) return 0;
    return mPingPongFbo[mActivePingPongIndex].textureId;
}

void GpuRenderEngine::applyBlendMode(BlendMode blendMode) {
    switch (blendMode) {
        case BlendMode::NORMAL:
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            break;
        case BlendMode::ADDITIVE:
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ONE, GL_ONE);
            break;
        case BlendMode::MULTIPLY:
            glBlendFuncSeparate(GL_DST_COLOR, GL_ZERO, GL_DST_ALPHA, GL_ZERO);
            break;
        case BlendMode::SCREEN:
            glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_COLOR, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            break;
        case BlendMode::PREMULTIPLIED:
            glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            break;
    }
}

void GpuRenderEngine::computeLayerMatrix(const RenderLayer& layer, float* outMatrix) {
    // Model Matrix = Translate * Rotate * Scale
    float tMat[16], rMat[16], sMat[16], trMat[16];

    matrixTranslate(tMat, layer.posX, layer.posY, 0.0f);
    matrixRotateZ(rMat, layer.rotation);
    matrixScale(sMat, layer.width * layer.scaleX, layer.height * layer.scaleY, 1.0f);

    matrixMultiply(trMat, tMat, rMat);
    matrixMultiply(outMatrix, trMat, sMat);
}

void GpuRenderEngine::onContextLost() {
    mProgram = 0;
    mVao = 0;
    mVbo = 0;
    mIsInitialized = false;
    for (int i = 0; i < 2; ++i) {
        mPingPongFbo[i].fboId = 0;
        mPingPongFbo[i].textureId = 0;
        mPingPongFbo[i].isValid = false;
    }
    mFbosInitialized = false;
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "GpuRenderEngine context lost state invalidated");
}

void GpuRenderEngine::release() {
    releaseFbos();
    if (mVbo != 0) {
        glDeleteBuffers(1, &mVbo);
        mVbo = 0;
    }
    if (mVao != 0) {
        glDeleteVertexArrays(1, &mVao);
        mVao = 0;
    }
    if (mProgram != 0) {
        glDeleteProgram(mProgram);
        mProgram = 0;
    }
    mIsInitialized = false;
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "GpuRenderEngine resources released");
}

void GpuRenderEngine::checkGlError(const char* op) {
    for (GLint error = glGetError(); error; error = glGetError()) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "after %s() glError (0x%x)", op, error);
    }
}

// ---------------------------------------------------------------------------
// Matrix 4x4 Math Helpers (Column-Major)
// ---------------------------------------------------------------------------

void GpuRenderEngine::matrixIdentity(float* m) {
    std::memset(m, 0, 16 * sizeof(float));
    m[0] = 1.0f;
    m[5] = 1.0f;
    m[10] = 1.0f;
    m[15] = 1.0f;
}

void GpuRenderEngine::matrixOrtho(float* m, float left, float right, float bottom, float top, float nearVal, float farVal) {
    matrixIdentity(m);
    m[0] = 2.0f / (right - left);
    m[5] = 2.0f / (top - bottom);
    m[10] = -2.0f / (farVal - nearVal);
    m[12] = -(right + left) / (right - left);
    m[13] = -(top + bottom) / (top - bottom);
    m[14] = -(farVal + nearVal) / (farVal - nearVal);
}

void GpuRenderEngine::matrixTranslate(float* m, float tx, float ty, float tz) {
    matrixIdentity(m);
    m[12] = tx;
    m[13] = ty;
    m[14] = tz;
}

void GpuRenderEngine::matrixRotateZ(float* m, float angleDegrees) {
    matrixIdentity(m);
    float rad = angleDegrees * (3.14159265358979323846f / 180.0f);
    float c = std::cos(rad);
    float s = std::sin(rad);

    m[0] = c;
    m[1] = s;
    m[4] = -s;
    m[5] = c;
}

void GpuRenderEngine::matrixScale(float* m, float sx, float sy, float sz) {
    matrixIdentity(m);
    m[0] = sx;
    m[5] = sy;
    m[10] = sz;
}

void GpuRenderEngine::matrixMultiply(float* result, const float* a, const float* b) {
    for (int i = 0; i < 4; ++i) { // col of b
        for (int j = 0; j < 4; ++j) { // row of a
            result[i * 4 + j] = a[0 * 4 + j] * b[i * 4 + 0] +
                                a[1 * 4 + j] * b[i * 4 + 1] +
                                a[2 * 4 + j] * b[i * 4 + 2] +
                                a[3 * 4 + j] * b[i * 4 + 3];
        }
    }
}

} // namespace ah_engine
