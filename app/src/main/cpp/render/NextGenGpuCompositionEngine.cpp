#include "NextGenGpuCompositionEngine.h"
#include <algorithm>
#include <chrono>
#include <cmath>
#include <cstring>
#include <iterator>

namespace ah_engine {

static const char* TAG = "NextGenGpu";

static const char* VS = R"glsl(#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
uniform mat4 uMVP;
uniform mat4 uST;
out vec2 vUv;
void main() {
    gl_Position = uMVP * vec4(aPosition, 1.0);
    vUv = (uST * vec4(aTexCoord, 0.0, 1.0)).xy;
}
)glsl";

static const char* FS2D = R"glsl(#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uTexture;
uniform float uOpacity;
out vec4 outColor;
void main() {
    vec4 c = texture(uTexture, vUv);
    outColor = vec4(c.rgb * uOpacity, c.a * uOpacity);
}
)glsl";

static const char* FSOES = R"glsl(#version 300 es
#extension GL_OES_EGL_image_external_essl3 : enable
#extension GL_OES_EGL_image_external : enable
precision mediump float;
in vec2 vUv;
uniform samplerExternalOES uTexture;
uniform float uOpacity;
out vec4 outColor;
void main() {
    vec4 c = texture(uTexture, vUv);
    outColor = vec4(c.rgb * uOpacity, c.a * uOpacity);
}
)glsl";

static const char* FSEFFECTS = R"glsl(#version 300 es
precision mediump float;
in vec2 vUv;
uniform sampler2D uTexture;
uniform int uEffectType;
uniform float uIntensity;
uniform float uParam1;
uniform float uParam2;
uniform float uTime;
uniform vec2 uTexSize;
out vec4 outColor;

float rand(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 uv = vUv;
    if (uEffectType == 1) { // COLOR_ADJUST (Param1: Brightness, Param2: Contrast, Intensity: Saturation)
        vec4 col = texture(uTexture, uv);
        vec3 rgb = col.rgb + vec3(uParam1);
        rgb = (rgb - 0.5) * uParam2 + 0.5;
        float gray = dot(rgb, vec3(0.2126, 0.7152, 0.0722));
        rgb = mix(vec3(gray), rgb, uIntensity);
        outColor = vec4(rgb, col.a);
    } else if (uEffectType == 2) { // VIGNETTE
        vec4 col = texture(uTexture, uv);
        float dist = distance(uv, vec2(0.5, 0.5));
        float vig = smoothstep(0.8, 0.8 - uIntensity * 0.45, dist);
        outColor = vec4(col.rgb * vig, col.a);
    } else if (uEffectType == 3 || uEffectType == 4) { // GLITCH / CHROMATIC ABERRATION
        if (uEffectType == 3 && uIntensity > 0.01) {
            float slice = step(0.96, rand(vec2(floor(uv.y * 25.0), floor(uTime * 0.02))));
            uv.x += slice * (rand(vec2(uTime, uv.y)) - 0.5) * 0.06 * uIntensity;
        }
        vec2 shift = vec2(0.007 * uIntensity, 0.0);
        float r = texture(uTexture, uv + shift).r;
        float g = texture(uTexture, uv).g;
        float b = texture(uTexture, uv - shift).b;
        outColor = vec4(r, g, b, texture(uTexture, uv).a);
    } else if (uEffectType == 5) { // SHARPEN
        vec2 step = 1.0 / max(uTexSize, vec2(1.0, 1.0));
        vec4 col = texture(uTexture, uv);
        vec4 n = texture(uTexture, uv + vec2(0.0, -step.y));
        vec4 s = texture(uTexture, uv + vec2(0.0, step.y));
        vec4 e = texture(uTexture, uv + vec2(step.x, 0.0));
        vec4 w = texture(uTexture, uv + vec2(-step.x, 0.0));
        vec4 sharp = col * 5.0 - (n + s + e + w);
        outColor = mix(col, sharp, clamp(uIntensity, 0.0, 1.0));
    } else {
        outColor = texture(uTexture, uv);
    }
}
)glsl";

NextGenGpuCompositionEngine::NextGenGpuCompositionEngine() {
    identity(projection_);
}

NextGenGpuCompositionEngine::~NextGenGpuCompositionEngine() {
    release();
}

GLuint NextGenGpuCompositionEngine::compile(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        GLint n = 0;
        glGetShaderiv(s, GL_INFO_LOG_LENGTH, &n);
        std::vector<char> log(std::max(1, n));
        glGetShaderInfoLog(s, n, nullptr, log.data());
        __android_log_print(ANDROID_LOG_ERROR, TAG, "Shader compile error: %s", log.data());
        glDeleteShader(s);
        return 0;
    }
    return s;
}

bool NextGenGpuCompositionEngine::buildPrograms() {
    auto link = [&](const char* fs) {
        GLuint v = compile(GL_VERTEX_SHADER, VS);
        GLuint f = compile(GL_FRAGMENT_SHADER, fs);
        if (!v || !f) {
            if (v) glDeleteShader(v);
            if (f) glDeleteShader(f);
            return 0u;
        }
        GLuint p = glCreateProgram();
        glAttachShader(p, v);
        glAttachShader(p, f);
        glBindAttribLocation(p, 0, "aPosition");
        glBindAttribLocation(p, 1, "aTexCoord");
        glLinkProgram(p);
        glDeleteShader(v);
        glDeleteShader(f);
        GLint ok = 0;
        glGetProgramiv(p, GL_LINK_STATUS, &ok);
        if (!ok) {
            glDeleteProgram(p);
            return 0u;
        }
        return p;
    };

    program2d_ = link(FS2D);
    programOes_ = link(FSOES);
    programEffects_ = link(FSEFFECTS);

    if (!program2d_ || !programOes_ || !programEffects_) return false;

    mvp2d_ = glGetUniformLocation(program2d_, "uMVP");
    st2d_ = glGetUniformLocation(program2d_, "uST");
    opacity2d_ = glGetUniformLocation(program2d_, "uOpacity");
    sampler2d_ = glGetUniformLocation(program2d_, "uTexture");

    mvpOes_ = glGetUniformLocation(programOes_, "uMVP");
    stOes_ = glGetUniformLocation(programOes_, "uST");
    opacityOes_ = glGetUniformLocation(programOes_, "uOpacity");
    samplerOes_ = glGetUniformLocation(programOes_, "uTexture");

    fxTypeLoc_ = glGetUniformLocation(programEffects_, "uEffectType");
    fxIntensityLoc_ = glGetUniformLocation(programEffects_, "uIntensity");
    fxParam1Loc_ = glGetUniformLocation(programEffects_, "uParam1");
    fxParam2Loc_ = glGetUniformLocation(programEffects_, "uParam2");
    fxTimeLoc_ = glGetUniformLocation(programEffects_, "uTime");
    fxSamplerLoc_ = glGetUniformLocation(programEffects_, "uTexture");
    fxTexSizeLoc_ = glGetUniformLocation(programEffects_, "uTexSize");

    return true;
}

bool NextGenGpuCompositionEngine::init() {
    if (initialized_) return true;
    if (!buildPrograms()) return false;

    const float q[] = {
        -1.0f,  1.0f, 0.0f, 0.0f, 1.0f,
        -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
         1.0f,  1.0f, 0.0f, 1.0f, 1.0f,
         1.0f, -1.0f, 0.0f, 1.0f, 0.0f
    };

    glGenVertexArrays(1, &vao_);
    glGenBuffers(1, &vbo_);
    glBindVertexArray(vao_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferData(GL_ARRAY_BUFFER, sizeof(q), q, GL_STATIC_DRAW);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 5 * sizeof(float), (void*)(3 * sizeof(float)));
    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);

    initialized_ = (vao_ != 0 && vbo_ != 0);
    return initialized_;
}

void NextGenGpuCompositionEngine::resize(int w, int h) {
    if (w <= 0 || h <= 0 || !initialized_) return;
    width_ = w;
    height_ = h;
    glViewport(0, 0, w, h);
    identity(projection_);
    ensureTargets(w, h);
}

void NextGenGpuCompositionEngine::ensureTargets(int w, int h) {
    if (targetWidth_ == w && targetHeight_ == h && target_[0] && target_[1] && target_[2]) return;
    destroyTargets();
    targetWidth_ = w;
    targetHeight_ = h;
    glGenFramebuffers(3, fbo_);
    glGenTextures(3, target_);
    for (int i = 0; i < 3; i++) {
        glBindTexture(GL_TEXTURE_2D, target_[i]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo_[i]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target_[i], 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            __android_log_print(ANDROID_LOG_ERROR, TAG, "FBO %d incomplete", i);
        }
    }
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glBindTexture(GL_TEXTURE_2D, 0);
}

void NextGenGpuCompositionEngine::destroyTargets() {
    if (fbo_[0]) {
        glDeleteFramebuffers(3, fbo_);
        glDeleteTextures(3, target_);
        std::fill(std::begin(fbo_), std::end(fbo_), 0);
        std::fill(std::begin(target_), std::end(target_), 0);
    }
    targetWidth_ = targetHeight_ = 0;
}

void NextGenGpuCompositionEngine::destroyPrograms() {
    if (program2d_) glDeleteProgram(program2d_);
    if (programOes_) glDeleteProgram(programOes_);
    if (programEffects_) glDeleteProgram(programEffects_);
    program2d_ = programOes_ = programEffects_ = 0;
}

void NextGenGpuCompositionEngine::bindTarget(GLuint fbo, int w, int h) {
    glBindFramebuffer(GL_FRAMEBUFFER, fbo);
    glViewport(0, 0, w, h);
    stats_.fboSwitches++;
}

void NextGenGpuCompositionEngine::blend(BlendMode mode) {
    glEnable(GL_BLEND);
    switch (mode) {
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
        default:
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            break;
    }
}

void NextGenGpuCompositionEngine::identity(float* m) const {
    std::memset(m, 0, 16 * sizeof(float));
    m[0] = m[5] = m[10] = m[15] = 1.0f;
}

void NextGenGpuCompositionEngine::multiply(float* o, const float* a, const float* b) const {
    float r[16];
    for (int c = 0; c < 4; c++) {
        for (int row = 0; row < 4; row++) {
            r[c * 4 + row] = a[row] * b[c * 4] +
                             a[4 + row] * b[c * 4 + 1] +
                             a[8 + row] * b[c * 4 + 2] +
                             a[12 + row] * b[c * 4 + 3];
        }
    }
    std::memcpy(o, r, sizeof(r));
}

void NextGenGpuCompositionEngine::layerMatrix(const RenderLayer& l, float* o) const {
    if (l.useCustomMatrix) {
        std::memcpy(o, l.transformMatrix, sizeof(float) * 16);
        return;
    }
    identity(o);
    float r = l.rotation * 3.14159265359f / 180.0f;
    float c = std::cos(r);
    float s = std::sin(r);
    o[0] = c * l.width * l.scaleX;
    o[1] = s * l.width * l.scaleX;
    o[4] = -s * l.height * l.scaleY;
    o[5] = c * l.height * l.scaleY;
    o[12] = l.posX;
    o[13] = l.posY;
}

void NextGenGpuCompositionEngine::drawTexture(GLuint tex, const float* mvp, const float* st, float opacity, BlendMode mode, bool external) {
    GLuint p = external ? programOes_ : program2d_;
    if (lastProgram_ != p) {
        glUseProgram(p);
        stats_.shaderSwitches++;
        lastProgram_ = p;
    }
    if (lastTexture_ != tex) {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(external ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D, tex);
        stats_.textureBinds++;
        lastTexture_ = tex;
    }
    GLint hm = external ? mvpOes_ : mvp2d_;
    GLint hs = external ? stOes_ : st2d_;
    GLint ho = external ? opacityOes_ : opacity2d_;
    GLint hsam = external ? samplerOes_ : sampler2d_;

    glUniformMatrix4fv(hm, 1, GL_FALSE, mvp);
    glUniformMatrix4fv(hs, 1, GL_FALSE, st);
    glUniform1f(ho, opacity);
    glUniform1i(hsam, 0);

    blend(mode);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    stats_.drawCalls++;
}

void NextGenGpuCompositionEngine::renderFrame(const std::vector<RenderLayer>& in) {
    if (!initialized_ || !width_ || !height_) return;
    auto start = std::chrono::steady_clock::now();
    stats_.frames++;

    std::vector<RenderLayer> layers;
    layers.reserve(in.size());
    for (const auto& l : in) {
        if (l.isVisible && l.textureId && l.opacity > 0.001f) {
            layers.push_back(l);
        } else {
            stats_.skippedLayers++;
        }
    }

    std::stable_sort(layers.begin(), layers.end(), [](const RenderLayer& a, const RenderLayer& b) {
        if (a.zOrder != b.zOrder) return a.zOrder < b.zOrder;
        return a.id < b.id;
    });

    GLuint targetFbo = offscreenActive_ ? fbo_[activeTarget_] : 0;
    bindTarget(targetFbo, width_, height_);
    glDisable(GL_DEPTH_TEST);
    glDisable(GL_CULL_FACE);

    if (!offscreenActive_) {
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);
    }

    glBindVertexArray(vao_);
    lastTexture_ = 0;

    for (const auto& l : layers) {
        float model[16], mvp[16], st[16];
        layerMatrix(l, model);
        multiply(mvp, projection_, model);
        identity(st);
        st[0] = l.uScale;
        st[5] = l.vScale;
        st[12] = l.uOffset;
        st[13] = l.vOffset;

        // Dynamic detection: Video / Base video layers use samplerExternalOES for zero-copy SurfaceTexture
        bool isExternal = (l.type == LayerType::BASE_VIDEO || l.type == LayerType::VIDEO);
        drawTexture(l.textureId, mvp, st, l.opacity, l.blendMode, isExternal);
    }

    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glDisable(GL_BLEND);
    glUseProgram(0);
    lastProgram_ = 0;

    auto end = std::chrono::steady_clock::now();
    stats_.lastFrameMs = std::chrono::duration<float, std::milli>(end - start).count();
}

void NextGenGpuCompositionEngine::applyEffectPass(const NativeEffectPass& pass) {
    if (!initialized_ || !offscreenActive_ || pass.type == NativeEffectType::NONE) return;
    int nextTarget = (activeTarget_ == 0) ? 1 : 0;

    bindTarget(fbo_[nextTarget], width_, height_);
    glDisable(GL_BLEND);
    glDisable(GL_DEPTH_TEST);

    glUseProgram(programEffects_);
    glUniform1i(fxTypeLoc_, static_cast<int>(pass.type));
    glUniform1f(fxIntensityLoc_, pass.intensity);
    glUniform1f(fxParam1Loc_, pass.param1);
    glUniform1f(fxParam2Loc_, pass.param2);
    glUniform1f(fxTimeLoc_, pass.timeMs);
    glUniform2f(fxTexSizeLoc_, static_cast<float>(width_), static_cast<float>(height_));

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, target_[activeTarget_]);
    glUniform1i(fxSamplerLoc_, 0);

    glBindVertexArray(vao_);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    glBindVertexArray(0);

    activeTarget_ = nextTarget;
}

void NextGenGpuCompositionEngine::renderExternalTexture(GLuint tex, const float* texMatrix) {
    if (!initialized_ || !tex) return;
    float mvp[16], st[16];
    std::memcpy(mvp, projection_, sizeof(mvp));
    if (texMatrix) std::memcpy(st, texMatrix, sizeof(st));
    else identity(st);

    bindTarget(0, width_, height_);
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glBindVertexArray(vao_);
    lastTexture_ = 0;
    drawTexture(tex, mvp, st, 1.0f, BlendMode::NORMAL, true);
    glBindVertexArray(0);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glDisable(GL_BLEND);
    glUseProgram(0);
    lastProgram_ = 0;
}

void NextGenGpuCompositionEngine::beginOffscreen() {
    if (!initialized_) return;
    ensureTargets(width_, height_);
    activeTarget_ = 0;
    offscreenActive_ = true;
    bindTarget(fbo_[activeTarget_], width_, height_);
    glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
    glClear(GL_COLOR_BUFFER_BIT);
}

GLuint NextGenGpuCompositionEngine::endOffscreen() {
    if (!initialized_) return 0;
    offscreenActive_ = false;
    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    glViewport(0, 0, width_, height_);
    return target_[activeTarget_];
}

void NextGenGpuCompositionEngine::onContextLost() {
    initialized_ = false;
    vao_ = vbo_ = program2d_ = programOes_ = programEffects_ = 0;
    std::fill(std::begin(fbo_), std::end(fbo_), 0);
    std::fill(std::begin(target_), std::end(target_), 0);
    offscreenActive_ = false;
    lastProgram_ = lastTexture_ = 0;
}

void NextGenGpuCompositionEngine::release() {
    if (!initialized_ && !vao_ && !vbo_ && !program2d_ && !programOes_ && !programEffects_) return;
    if (initialized_) destroyTargets();
    destroyPrograms();
    if (vbo_) glDeleteBuffers(1, &vbo_);
    if (vao_) glDeleteVertexArrays(1, &vao_);
    vao_ = vbo_ = 0;
    initialized_ = false;
    offscreenActive_ = false;
}

} // namespace ah_engine
