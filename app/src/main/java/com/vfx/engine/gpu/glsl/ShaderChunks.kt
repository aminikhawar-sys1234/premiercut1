package com.vfx.engine.gpu.glsl

/** Reusable GLSL libraries injected into every composed effect shader. Zero duplication. */
object ShaderChunks {

    val HEADER_ES3 = """
#version 300 es
precision highp float;
out vec4 fragColor;
""".trimIndent()

    val HEADER_ES2 = """
precision highp float;
#define texture texture2D
#define fragColor gl_FragColor
#define isnan(v) ((v) != (v))
""".trimIndent()

    fun vertex(es3: Boolean): String = if (es3) """
#version 300 es
precision highp float;
layout(location=0) in vec2 a_pos;
layout(location=1) in vec2 a_texCoord;
uniform mat3 u_uvMat;
out vec2 v_uv;
void main() {
    v_uv = (u_uvMat * vec3(a_texCoord, 1.0)).xy;
    gl_Position = vec4(a_pos, 0.0, 1.0);
}
""".trimIndent() else """
precision highp float;
attribute vec2 a_pos;
attribute vec2 a_texCoord;
uniform mat3 u_uvMat;
varying vec2 v_uv;
void main() {
    v_uv = (u_uvMat * vec3(a_texCoord, 1.0)).xy;
    gl_Position = vec4(a_pos, 0.0, 1.0);
}
""".trimIndent()

    /** Vertex pass for external OES (SurfaceTexture mat4 matrix). */
    fun vertexExternal(es3: Boolean): String = if (es3) """
#version 300 es
precision highp float;
layout(location=0) in vec2 a_pos;
layout(location=1) in vec2 a_texCoord;
uniform mat4 u_texMatrix;
out vec2 v_uv;
void main() {
    v_uv = (u_texMatrix * vec4(a_texCoord, 0.0, 1.0)).xy;
    gl_Position = vec4(a_pos, 0.0, 1.0);
}
""".trimIndent() else """
precision highp float;
attribute vec2 a_pos;
attribute vec2 a_texCoord;
uniform mat4 u_texMatrix;
varying vec2 v_uv;
void main() {
    v_uv = (u_texMatrix * vec4(a_texCoord, 0.0, 1.0)).xy;
    gl_Position = vec4(a_pos, 0.0, 1.0);
}
""".trimIndent()

    val COLOR = """
float luma709(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }
float luma601(vec3 c)  { return dot(c, vec3(0.299, 0.587, 0.114)); }
float maxc(vec3 c) { return max(c.r, max(c.g, c.b)); }
float minc(vec3 c) { return min(c.r, min(c.g, c.b)); }
vec3 safe3(vec3 c) {
    if (isnan(c.r)) c.r = 0.0;
    if (isnan(c.g)) c.g = 0.0;
    if (isnan(c.b)) c.b = 0.0;
    return clamp(c, vec3(-1.0e6), vec3(1.0e6));
}
vec3 srgbToLinear(vec3 c) {
    bvec3 lo = lessThanEqual(c, vec3(0.04045));
    vec3 lin = c / 12.92;
    vec3 hi = pow((c + 0.055) / 1.055, vec3(2.4));
    return mix(hi, lin, vec3(lo));
}
vec3 linearToSrgb(vec3 c) {
    bvec3 lo = lessThanEqual(c, vec3(0.0031308));
    vec3 lin = c * 12.92;
    vec3 hi = 1.055 * pow(max(c, vec3(0.0)), vec3(1.0 / 2.4)) - 0.055;
    return mix(hi, lin, vec3(lo));
}
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}
float hueDist(float a, float b) {
    float d = abs(mod(a - b, 360.0));
    return min(d, 360.0 - d);
}
""".trimIndent()

    val NOISE = """
float hash11(float p) { p = fract(p * 0.1031); p *= p + 33.33; p *= p + p; return fract(p); }
float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float valueNoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) { v += a * valueNoise(p); p = p * 2.03 + 17.31; a *= 0.5; }
    return v;
}
mat2 rot2(float a) { float c = cos(a), s = sin(a); return mat2(c, -s, s, c); }
""".trimIndent()

    val BLEND = """
vec3 blendMultiply(vec3 b, vec3 s) { return b * s; }
vec3 blendScreen(vec3 b, vec3 s) { return b + s - b * s; }
vec3 blendOverlay(vec3 b, vec3 s) {
    return mix(2.0 * b * s, 1.0 - 2.0 * (1.0 - b) * (1.0 - s), step(0.5, b));
}
vec3 blendHardLight(vec3 b, vec3 s) { return blendOverlay(s, b); }
vec3 blendSoftLight(vec3 b, vec3 s) { return (1.0 - 2.0 * s) * b * b + 2.0 * s * b; }
vec3 blendDarken(vec3 b, vec3 s) { return min(b, s); }
vec3 blendLighten(vec3 b, vec3 s) { return max(b, s); }
vec3 blendDifference(vec3 b, vec3 s) { return abs(b - s); }
vec3 blendExclusion(vec3 b, vec3 s) { return b + s - 2.0 * b * s; }
vec3 blendAdd(vec3 b, vec3 s) { return min(b + s, vec3(1.0)); }
vec3 blendSubtract(vec3 b, vec3 s) { return max(b - s, vec3(0.0)); }
vec3 blendColorDodge(vec3 b, vec3 s) { return min(vec3(1.0), b / max(1.0 - s, vec3(1.0e-4))); }
vec3 blendColorBurn(vec3 b, vec3 s) { return max(vec3(0.0), 1.0 - (1.0 - b) / max(s, vec3(1.0e-4))); }
vec3 blendC(int mode, vec3 b, vec3 s) {
    if (mode == 1) return blendMultiply(b, s);
    if (mode == 2) return blendScreen(b, s);
    if (mode == 3) return blendOverlay(b, s);
    if (mode == 4) return blendSoftLight(b, s);
    if (mode == 5) return blendHardLight(b, s);
    if (mode == 6) return blendDarken(b, s);
    if (mode == 7) return blendLighten(b, s);
    if (mode == 8) return blendDifference(b, s);
    if (mode == 9) return blendExclusion(b, s);
    if (mode == 10) return blendAdd(b, s);
    if (mode == 11) return blendSubtract(b, s);
    if (mode == 12) return blendColorDodge(b, s);
    if (mode == 13) return blendColorBurn(b, s);
    return s;
}
/* Straight-alpha composite; codes match BlendMode.glslCode on the Kotlin side. */
vec4 blendComposite(vec4 base, vec4 src, int mode, float opacity) {
    float sa = clamp(src.a, 0.0, 1.0) * opacity;
    if (sa <= 0.0) return base;
    vec3 blended = blendC(mode, base.rgb, src.rgb);
    float outA = sa + base.a * (1.0 - sa);
    vec3 rgb = (blended * sa + base.rgb * base.a * (1.0 - sa)) / max(outA, 1.0e-6);
    return vec4(rgb, outA);
}
""".trimIndent()

    val MASK_SDF = """
float sdBox(vec2 p, vec2 b) {
    vec2 d = abs(p) - b;
    return length(max(d, vec2(0.0))) + min(max(d.x, d.y), 0.0);
}
float sdRoundBox(vec2 p, vec2 b, float r) {
    return sdBox(p, b - vec2(clamp(r, 0.0, min(b.x, b.y)))) - clamp(r, 0.0, min(b.x, b.y));
}
""".trimIndent()

    val UV = """
vec2 clampUv(vec2 uv) { return clamp(uv, vec2(0.0), vec2(1.0)); }
""".trimIndent()
}
