package com.vfx.engine.gpu.glsl

/**
 * Real GLSL bodies for built-in effects.
 * Every body defines `vec4 process(vec4 c, vec2 uv)`; distortion/warp bodies
 * additionally define `vec2 warpUV(vec2 uv)`. Effect uniforms are declared inline.
 */
object EffectGlsl {

    const val PASSTHROUGH = "vec4 process(vec4 c, vec2 uv){ return c; }"

    // ---------------- COLOR ----------------
    const val BRIGHTNESS = """
uniform float u_amount;
vec4 process(vec4 c, vec2 uv){ c.rgb += u_amount; return c; }"""

    const val CONTRAST = """
uniform float u_contrast;
vec4 process(vec4 c, vec2 uv){ c.rgb = (c.rgb - 0.5) * u_contrast + 0.5; return c; }"""

    const val EXPOSURE = """
uniform float u_stops;
vec4 process(vec4 c, vec2 uv){ c.rgb *= exp2(u_stops); return c; }"""

    const val GAMMA = """
uniform float u_gamma;
vec4 process(vec4 c, vec2 uv){ c.rgb = pow(max(c.rgb, vec3(0.0)), vec3(1.0 / max(u_gamma, 0.01))); return c; }"""

    const val SATURATION = """
uniform float u_sat;
vec4 process(vec4 c, vec2 uv){ float l = luma709(c.rgb); c.rgb = mix(vec3(l), c.rgb, u_sat); return c; }"""

    const val VIBRANCE = """
uniform float u_vibrance;
vec4 process(vec4 c, vec2 uv){
    float l = luma709(c.rgb);
    float sat = maxc(c.rgb) - minc(c.rgb);
    c.rgb = mix(vec3(l), c.rgb, 1.0 + sat * u_vibrance);
    return c;
}"""

    const val HUE = """
uniform float u_degrees;
vec4 process(vec4 c, vec2 uv){
    vec3 hsv = rgb2hsv(c.rgb);
    hsv.x = fract(hsv.x + u_degrees / 360.0);
    c.rgb = hsv2rgb(hsv);
    return c;
}"""

    const val TEMPERATURE = """
uniform float u_temp;
uniform float u_tint;
vec4 process(vec4 c, vec2 uv){
    c.r *= 1.0 + 0.12 * u_temp;
    c.b *= 1.0 - 0.12 * u_temp;
    c.g *= 1.0 + 0.08 * u_tint;
    return c;
}"""

    const val HIGHLIGHTS_SHADOWS = """
uniform float u_highlights;
uniform float u_shadows;
vec4 process(vec4 c, vec2 uv){
    float l = luma709(c.rgb);
    float hw = smoothstep(0.5, 1.0, l);
    float sw = 1.0 - smoothstep(0.0, 0.5, l);
    vec3 hDir = u_highlights >= 0.0 ? (1.0 - c.rgb) : c.rgb;
    vec3 sDir = u_shadows >= 0.0 ? (1.0 - c.rgb) : c.rgb;
    c.rgb += hw * u_highlights * hDir;
    c.rgb += sw * u_shadows * sDir;
    return c;
}"""

    const val WHITES_BLACKS = """
uniform float u_whites;
uniform float u_blacks;
vec4 process(vec4 c, vec2 uv){
    float l = luma709(c.rgb);
    float ww = smoothstep(0.6, 1.0, l);
    float bw = 1.0 - smoothstep(0.0, 0.4, l);
    vec3 wDir = u_whites >= 0.0 ? (1.0 - c.rgb) : c.rgb;
    vec3 bDir = u_blacks >= 0.0 ? (1.0 - c.rgb) : c.rgb;
    c.rgb += ww * u_whites * wDir;
    c.rgb += bw * u_blacks * bDir;
    return c;
}"""

    const val LEVELS = """
uniform float u_inBlack;
uniform float u_inWhite;
uniform float u_gamma;
uniform float u_outBlack;
uniform float u_outWhite;
vec4 process(vec4 c, vec2 uv){
    vec3 v = clamp((c.rgb - vec3(u_inBlack)) / max(u_inWhite - u_inBlack, 1.0e-4), vec3(0.0), vec3(1.0));
    v = pow(v, vec3(1.0 / max(u_gamma, 0.01)));
    c.rgb = v * (u_outWhite - u_outBlack) + u_outBlack;
    return c;
}"""

    const val CURVES = """
uniform sampler2D u_curveLut;
vec4 process(vec4 c, vec2 uv){
    c.r = texture(u_curveLut, vec2(clamp(c.r, 0.0, 1.0), 0.5)).r;
    c.g = texture(u_curveLut, vec2(clamp(c.g, 0.0, 1.0), 0.5)).g;
    c.b = texture(u_curveLut, vec2(clamp(c.b, 0.0, 1.0), 0.5)).b;
    return c;
}"""

    const val COLOR_BALANCE = """
uniform vec3 u_shadows;
uniform vec3 u_midtones;
uniform vec3 u_highlights;
uniform float u_preserveLuma;
vec4 process(vec4 c, vec2 uv){
    float l = luma709(c.rgb);
    float sw = pow(1.0 - l, 2.0);
    float hw = pow(l, 2.0);
    float mw = clamp(1.0 - sw - hw, 0.0, 1.0);
    vec3 adj = c.rgb + sw * u_shadows + mw * u_midtones + hw * u_highlights;
    if (u_preserveLuma > 0.5) {
        float l1 = luma709(adj);
        adj *= (l0Guard(l) + 1.0e-4) / (l1 + 1.0e-4);
    }
    c.rgb = adj;
    return c;
}
float l0Guard(float l){ return l; }"""

    const val CHANNEL_MIXER = """
uniform vec3 u_rowR;
uniform vec3 u_rowG;
uniform vec3 u_rowB;
vec4 process(vec4 c, vec2 uv){
    c.rgb = mat3(u_rowR, u_rowG, u_rowB) * c.rgb;
    return c;
}"""

    const val HSL_BANDS = """
uniform float u_hueShift[6];
uniform float u_satScale[6];
uniform float u_lumScale[6];
vec4 process(vec4 c, vec2 uv){
    vec3 hsv = rgb2hsv(c.rgb);
    float hDeg = hsv.x * 360.0;
    float hueOff = 0.0, satMul = 1.0, lumMul = 1.0;
    for (int i = 0; i < 6; i++) {
        float d = hueDist(hDeg, float(i) * 60.0);
        float w = 1.0 - smoothstep(30.0, 60.0, d);
        hueOff += u_hueShift[i] * w;
        satMul += (u_satScale[i] - 1.0) * w;
        lumMul += (u_lumScale[i] - 1.0) * w;
    }
    hsv.x = fract(hsv.x + hueOff / 360.0);
    hsv.y = clamp(hsv.y * satMul, 0.0, 1.0);
    hsv.z = clamp(hsv.z * lumMul, 0.0, 1.0);
    c.rgb = hsv2rgb(hsv);
    return c;
}"""

    const val SELECTIVE_COLOR = """
uniform float u_cmyDelta[27];
vec4 process(vec4 c, vec2 uv){
    vec3 hsv = rgb2hsv(c.rgb);
    float hDeg = hsv.x * 360.0;
    float l = luma709(c.rgb);
    vec3 cmy = vec3(1.0) - c.rgb;
    for (int i = 0; i < 9; i++) {
        float w;
        if (i < 6) {
            float d = hueDist(hDeg, float(i) * 60.0);
            w = 1.0 - smoothstep(30.0, 60.0, d);
        } else if (i == 6) { w = smoothstep(0.75, 0.95, l); }
        else if (i == 7) { w = 1.0 - smoothstep(0.0, 0.25, abs(l - 0.5)); }
        else { w = 1.0 - smoothstep(0.05, 0.25, l); }
        vec3 delta = vec3(u_cmyDelta[i * 3], u_cmyDelta[i * 3 + 1], u_cmyDelta[i * 3 + 2]);
        cmy = clamp(cmy + delta * w, vec3(0.0), vec3(1.0));
    }
    c.rgb = clamp(vec3(1.0) - cmy, vec3(0.0), vec3(1.0));
    return c;
}"""

    // ---------------- LUT ----------------
    const val LUT_SAMPLERS_3D = """
uniform sampler3D u_lut;
vec3 applyLut(vec3 c) {
    vec3 tc = (c * (u_lutSize - 1.0) + 0.5) / u_lutSize;
    return texture(u_lut, clamp(tc, vec3(0.0), vec3(1.0))).rgb;
}"""

    const val LUT_SAMPLERS_2D = """
uniform sampler2D u_lut;
vec3 applyLut(vec3 c) {
    float n = u_lutSize;
    float blue = clamp(c.b, 0.0, 1.0) * (n - 1.0);
    float b0 = floor(blue);
    float bf = blue - b0;
    float x0 = (b0 * n + c.r * (n - 1.0) + 0.5) / (n * n);
    float x1 = ((b0 + 1.0) * n + c.r * (n - 1.0) + 0.5) / (n * n);
    float y = (c.g * (n - 1.0) + 0.5) / n;
    vec3 s0 = texture(u_lut, vec2(clamp(x0, 0.0, 1.0), clamp(y, 0.0, 1.0))).rgb;
    vec3 s1 = texture(u_lut, vec2(clamp(x1, 0.0, 1.0), clamp(y, 0.0, 1.0))).rgb;
    return mix(s0, s1, bf);
}"""

    const val LUT_APPLY = """
uniform float u_lutSize;
uniform float u_lutIntensity;
vec4 process(vec4 c, vec2 uv){
    c.rgb = mix(c.rgb, applyLut(clamp(c.rgb, 0.0, 1.0)), clamp(u_lutIntensity, 0.0, 1.0));
    return c;
}"""

    const val LUT_1D = """
uniform sampler2D u_lut1d;
uniform float u_lutIntensity;
vec4 process(vec4 c, vec2 uv){
    vec3 g = vec3(
        texture(u_lut1d, vec2(clamp(c.r, 0.0, 1.0), 0.5)).r,
        texture(u_lut1d, vec2(clamp(c.g, 0.0, 1.0), 0.5)).g,
        texture(u_lut1d, vec2(clamp(c.b, 0.0, 1.0), 0.5)).b);
    c.rgb = mix(c.rgb, g, clamp(u_lutIntensity, 0.0, 1.0));
    return c;
}"""

    // ---------------- BLUR ----------------
    const val SEPARABLE_BLUR = """
uniform vec2 u_dir;
uniform float u_weights[33];
uniform int u_taps;
vec4 process(vec4 c, vec2 uv){
    vec4 acc = vec4(0.0);
    float wsum = 0.0;
    for (int i = 0; i < 33; i++) {
        if (i >= u_taps) break;
        float w = u_weights[i];
        vec2 off = u_dir * float(i - (u_taps - 1) / 2);
        acc += texture(u_tex, clampUv(uv + off)) * w;
        wsum += w;
    }
    c = acc / max(wsum, 1.0e-5);
    return c;
}"""

    const val DIRECTIONAL_BLUR = """
uniform vec2 u_dir;
uniform int u_samples;
uniform float u_amount;
vec4 process(vec4 c, vec2 uv){
    if (u_amount <= 0.001) return c;
    vec4 acc = vec4(0.0);
    float jitter = hash21(uv * u_resolution) - 0.5;
    for (int i = 0; i < 64; i++) {
        if (i >= u_samples) break;
        float t = (float(i) + jitter) / float(u_samples) - 0.5;
        acc += texture(u_tex, clampUv(uv + u_dir * t * u_amount));
    }
    c = acc / float(u_samples);
    return c;
}"""

    const val RADIAL_BLUR = """
uniform vec2 u_center;
uniform float u_strength;
uniform int u_samples;
vec4 process(vec4 c, vec2 uv){
    vec2 d = uv - u_center;
    vec4 acc = vec4(0.0);
    for (int i = 0; i < 64; i++) {
        if (i >= u_samples) break;
        float t = float(i) / float(u_samples) - 0.5;
        acc += texture(u_tex, clampUv(uv - d * t * u_strength));
    }
    c = acc / float(u_samples);
    return c;
}"""

    const val ZOOM_BLUR = """
uniform vec2 u_center;
uniform float u_strength;
uniform int u_samples;
vec4 process(vec4 c, vec2 uv){
    vec4 acc = vec4(0.0);
    for (int i = 0; i < 64; i++) {
        if (i >= u_samples) break;
        float t = float(i) / float(u_samples);
        vec2 su = mix(uv, u_center + (uv - u_center) * (1.0 - u_strength), t);
        acc += texture(u_tex, clampUv(su));
    }
    c = acc / float(u_samples);
    return c;
}"""

    // ---------------- SHARPEN ----------------
    const val SHARPEN_3X3 = """
uniform float u_amount;
vec4 process(vec4 c, vec2 uv){
    vec3 acc = vec3(0.0);
    for (int dy = -1; dy <= 1; dy++)
    for (int dx = -1; dx <= 1; dx++) {
        vec3 s = texture(u_tex, clampUv(uv + vec2(float(dx), float(dy)) * u_texel)).rgb;
        float w = (dx == 0 && dy == 0) ? (1.0 + 4.0 * u_amount) : -u_amount;
        acc += s * w;
    }
    c.rgb = acc;
    return c;
}"""

    const val UNSHARP_COMBINE = """
uniform sampler2D u_blurred;
uniform float u_amount;
vec4 process(vec4 c, vec2 uv){
    vec3 blur = texture(u_blurred, uv).rgb;
    c.rgb = c.rgb + (c.rgb - blur) * u_amount;
    return c;
}"""

    // ---------------- LIGHT ----------------
    const val BLOOM_THRESHOLD = """
uniform float u_threshold;
uniform float u_knee;
vec4 process(vec4 c, vec2 uv){
    float l = luma709(c.rgb);
    float soft = u_threshold + u_knee;
    float w = clamp((l - u_threshold) / max(soft - u_threshold, 1.0e-4), 0.0, 1.0);
    c.rgb *= w;
    return c;
}"""

    const val BLOOM_COMBINE = """
uniform sampler2D u_bloom;
uniform float u_bloomIntensity;
vec4 process(vec4 c, vec2 uv){
    vec3 b = texture(u_bloom, uv).rgb;
    c.rgb = 1.0 - (1.0 - c.rgb) * (1.0 - min(b * u_bloomIntensity, vec3(1.0)));
    return c;
}"""

    const val GLOW_COMBINE = """
uniform sampler2D u_blurred;
uniform float u_glowStrength;
vec4 process(vec4 c, vec2 uv){
    vec3 b = texture(u_blurred, uv).rgb;
    c.rgb = blendScreen(c.rgb, b * u_glowStrength);
    return c;
}"""

    const val LIGHT_RAYS = """
uniform vec2 u_lightPos;
uniform float u_rayLength;
uniform float u_decay;
uniform float u_rayStrength;
uniform int u_raySamples;
vec4 process(vec4 c, vec2 uv){
    vec2 dir = u_lightPos - uv;
    vec3 rays = vec3(0.0);
    float w = 1.0;
    for (int i = 0; i < 64; i++) {
        if (i >= u_raySamples) break;
        float t = float(i) / float(u_raySamples);
        rays += texture(u_tex, clampUv(uv + dir * t * u_rayLength)).rgb * w;
        w *= u_decay;
    }
    c.rgb += rays * u_rayStrength;
    return c;
}"""

    const val VIGNETTE = """
uniform vec2 u_center;
uniform float u_start;
uniform float u_end;
uniform vec4 u_vigColor;
uniform float u_vigAmount;
vec4 process(vec4 c, vec2 uv){
    vec2 d = uv - u_center;
    d.x *= u_resolution.x / max(u_resolution.y, 1.0);
    float r = length(d);
    float m = 1.0 - smoothstep(u_start, u_end, r);
    vec3 target = mix(u_vigColor.rgb, c.rgb, m);
    c.rgb = mix(c.rgb, target, u_vigAmount);
    return c;
}"""

    // ---------------- DISTORTION ----------------
    const val LENS_DISTORTION = """
uniform float u_k1;
uniform float u_k2;
vec2 warpUV(vec2 uv){
    vec2 p = (uv - 0.5) * 2.0;
    float r2 = dot(p, p);
    float f = 1.0 + u_k1 * r2 + u_k2 * r2 * r2;
    return clampUv(p * f * 0.5 + 0.5);
}
vec4 process(vec4 c, vec2 uv){ return c; }"""

    const val FISHEYE = """
uniform float u_fishStrength;
vec2 warpUV(vec2 uv){
    vec2 p = (uv - 0.5) * 2.0;
    float r = length(p);
    if (r < 1.0e-5) return uv;
    float rr = mix(r, asin(clamp(r, 0.0, 1.0)) * 1.2732, u_fishStrength);
    return clampUv(p / r * rr * 0.5 + 0.5);
}
vec4 process(vec4 c, vec2 uv){ return c; }"""

    const val RIPPLE = """
uniform vec2 u_center;
uniform float u_rippleFreq;
uniform float u_rippleSpeed;
uniform float u_rippleAmp;
uniform float u_rippleFalloff;
vec2 warpUV(vec2 uv){
    vec2 d = uv - u_center;
    float r = length(d);
    float a = sin(r * u_rippleFreq - u_time * u_rippleSpeed) * u_rippleAmp * exp(-r * u_rippleFalloff);
    return clampUv(uv + (r > 1.0e-6 ? d / r : vec2(0.0)) * a);
}
vec4 process(vec4 c, vec2 uv){ return c; }"""

    const val WAVE = """
uniform vec2 u_waveDir;
uniform float u_waveFreq;
uniform float u_waveSpeed;
uniform float u_waveAmp;
vec2 warpUV(vec2 uv){
    vec2 dir = normalize(u_waveDir + vec2(1.0e-5, 0.0));
    float s = dot(uv, vec2(-dir.y, dir.x));
    float off = sin(s * u_waveFreq + u_time * u_waveSpeed) * u_waveAmp;
    return clampUv(uv + dir * off);
}
vec4 process(vec4 c, vec2 uv){ return c; }"""

    const val DISPLACEMENT = """
uniform sampler2D u_dispMap;
uniform float u_dispScale;
vec2 warpUV(vec2 uv){
    vec2 d = texture(u_dispMap, clampUv(uv)).rg - 0.5;
    return clampUv(uv + d * u_dispScale);
}
vec4 process(vec4 c, vec2 uv){ return c; }"""

    const val TURBULENCE = """
uniform float u_turbFreq;
uniform float u_turbAmp;
uniform float u_turbSpeed;
vec2 warpUV(vec2 uv){
    float t = u_time * u_turbSpeed;
    vec2 n = vec2(fbm(uv * u_turbFreq + t), fbm(uv * u_turbFreq + vec2(37.7, 11.3) - t)) - 0.5;
    return clampUv(uv + n * u_turbAmp);
}
vec4 process(vec4 c, vec2 uv){ return c; }"""

    // ---------------- STYLIZE ----------------
    const val POSTERIZE = """
uniform float u_levels;
vec4 process(vec4 c, vec2 uv){ c.rgb = floor(c.rgb * u_levels + 0.5) / u_levels; return c; }"""

    const val THRESHOLD = """
uniform float u_threshold;
uniform float u_keepColor;
vec4 process(vec4 c, vec2 uv){
    float l = luma709(c.rgb);
    vec3 col = u_keepColor > 0.5 ? c.rgb : vec3(1.0);
    c.rgb = l >= u_threshold ? col : vec3(0.0);
    return c;
}"""

    const val PIXELATE = """
uniform float u_blocksX;
uniform float u_blocksY;
vec2 warpUV(vec2 uv){
    vec2 g = vec2(max(u_blocksX, 2.0), max(u_blocksY, 2.0));
    return clampUv((floor(uv * g) + 0.5) / g);
}
vec4 process(vec4 c, vec2 uv){ return c; }"""

    const val EDGE_DETECT = """
uniform float u_edgeStrength;
uniform vec4 u_edgeFg;
uniform vec4 u_edgeBg;
vec4 process(vec4 c, vec2 uv){
    float tl = luma709(texture(u_tex, clampUv(uv + vec2(-1.0, -1.0) * u_texel)).rgb);
    float t  = luma709(texture(u_tex, clampUv(uv + vec2( 0.0, -1.0) * u_texel)).rgb);
    float tr = luma709(texture(u_tex, clampUv(uv + vec2( 1.0, -1.0) * u_texel)).rgb);
    float l  = luma709(texture(u_tex, clampUv(uv + vec2(-1.0,  0.0) * u_texel)).rgb);
    float r  = luma709(texture(u_tex, clampUv(uv + vec2( 1.0,  0.0) * u_texel)).rgb);
    float bl = luma709(texture(u_tex, clampUv(uv + vec2(-1.0,  1.0) * u_texel)).rgb);
    float b  = luma709(texture(u_tex, clampUv(uv + vec2( 0.0,  1.0) * u_texel)).rgb);
    float br = luma709(texture(u_tex, clampUv(uv + vec2( 1.0,  1.0) * u_texel)).rgb);
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
    float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
    float e = clamp(length(vec2(gx, gy)) * u_edgeStrength, 0.0, 1.0);
    c.rgb = mix(u_edgeBg.rgb, u_edgeFg.rgb, e);
    return c;
}"""

    const val SKETCH = """
uniform float u_sketchStrength;
vec4 process(vec4 c, vec2 uv){
    float tl = luma709(texture(u_tex, clampUv(uv + vec2(-1.0, -1.0) * u_texel)).rgb);
    float t  = luma709(texture(u_tex, clampUv(uv + vec2( 0.0, -1.0) * u_texel)).rgb);
    float tr = luma709(texture(u_tex, clampUv(uv + vec2( 1.0, -1.0) * u_texel)).rgb);
    float l  = luma709(texture(u_tex, clampUv(uv + vec2(-1.0,  0.0) * u_texel)).rgb);
    float r  = luma709(texture(u_tex, clampUv(uv + vec2( 1.0,  0.0) * u_texel)).rgb);
    float bl = luma709(texture(u_tex, clampUv(uv + vec2(-1.0,  1.0) * u_texel)).rgb);
    float b  = luma709(texture(u_tex, clampUv(uv + vec2( 0.0,  1.0) * u_texel)).rgb);
    float br = luma709(texture(u_tex, clampUv(uv + vec2( 1.0,  1.0) * u_texel)).rgb);
    float gx = -tl - 2.0 * l - bl + tr + 2.0 * r + br;
    float gy = -tl - 2.0 * t - tr + bl + 2.0 * b + br;
    float edge = clamp(length(vec2(gx, gy)) * u_sketchStrength, 0.0, 1.0);
    float paper = 0.92 + 0.08 * hash21(floor(uv * u_resolution * 0.5));
    c.rgb = vec3(paper) * (1.0 - edge);
    return c;
}"""

    const val EMBOSS = """
uniform float u_embossAmount;
vec4 process(vec4 c, vec2 uv){
    float acc = 0.0;
    float k[9];
    k[0] = -2.0; k[1] = -1.0; k[2] = 0.0;
    k[3] = -1.0; k[4] =  1.0; k[5] = 1.0;
    k[6] =  0.0; k[7] =  1.0; k[8] = 2.0;
    for (int dy = -1; dy <= 1; dy++)
    for (int dx = -1; dx <= 1; dx++) {
        int i = (dy + 1) * 3 + (dx + 1);
        float s = luma709(texture(u_tex, clampUv(uv + vec2(float(dx), float(dy)) * u_texel)).rgb);
        acc += s * k[i];
    }
    c.rgb = vec3(acc * u_embossAmount + 0.5);
    return c;
}"""

    const val HALFTONE = """
uniform float u_dotSize;
uniform float u_gridAngle;
uniform vec4 u_dark;
uniform vec4 u_light;
vec4 process(vec4 c, vec2 uv){
    vec2 p = rot2(radians(u_gridAngle)) * uv * u_resolution / max(u_dotSize, 1.0);
    vec2 f = fract(p) - 0.5;
    float d = length(f) * 2.0;
    float l = luma709(c.rgb);
    float radius = sqrt(clamp(1.0 - l, 0.0, 1.0));
    float m = smoothstep(radius, radius - 0.08, d);
    return mix(u_dark, u_light, 1.0 - m);
}"""

    const val DUOTONE = """
uniform vec4 u_duoDark;
uniform vec4 u_duoLight;
uniform float u_duoDetail;
vec4 process(vec4 c, vec2 uv){
    float l = luma709(c.rgb);
    vec3 duo = mix(u_duoDark.rgb, u_duoLight.rgb, clamp(l, 0.0, 1.0));
    c.rgb = mix(duo, c.rgb, u_duoDetail);
    return c;
}"""

    // ---------------- NOISE ----------------
    const val FILM_GRAIN = """
uniform float u_grainAmount;
uniform float u_grainSize;
uniform float u_seed;
uniform float u_lumBias;
vec4 process(vec4 c, vec2 uv){
    vec2 gp = uv * u_resolution / max(u_grainSize, 0.25)
            + vec2(u_seed + u_time * 37.7, u_seed * 1.7 + u_time * 53.3);
    float g = hash21(gp) - 0.5;
    float l = luma709(c.rgb);
    c.rgb += g * u_grainAmount * mix(1.0, 1.0 - l, u_lumBias);
    return c;
}"""

    const val DIGITAL_NOISE = """
uniform float u_noiseAmount;
uniform float u_noiseSeed;
vec4 process(vec4 c, vec2 uv){
    float n = hash31(vec3(uv * u_resolution, u_noiseSeed + floor(u_time * 60.0)));
    c.rgb += (n - 0.5) * u_noiseAmount;
    return c;
}"""

    // ---------------- CHROMATIC ----------------
    const val CHROMATIC_ABERRATION = """
uniform float u_caAmount;
uniform vec2 u_caCenter;
vec4 process(vec4 c, vec2 uv){
    vec2 d = uv - u_caCenter;
    c.r = texture(u_tex, clampUv(uv + d * u_caAmount)).r;
    c.g = texture(u_tex, uv).g;
    c.b = texture(u_tex, clampUv(uv - d * u_caAmount)).b;
    return c;
}"""

    const val RGB_SPLIT = """
uniform vec2 u_splitDir;
uniform float u_splitAmount;
vec4 process(vec4 c, vec2 uv){
    vec2 off = u_splitDir * u_splitAmount;
    c.r = texture(u_tex, clampUv(uv + off)).r;
    c.g = texture(u_tex, uv).g;
    c.b = texture(u_tex, clampUv(uv - off)).b;
    return c;
}"""

    // ---------------- LUT ----------------
    const val LUT_3D = """
uniform mediump sampler3D u_lut;
uniform float u_lutSize;
vec4 process(vec4 c, vec2 uv){
    vec3 scale = (vec3(u_lutSize) - 1.0) / vec3(u_lutSize);
    vec3 offset = 1.0 / (2.0 * vec3(u_lutSize));
    vec3 lutCoord = clamp(c.rgb, 0.0, 1.0) * scale + offset;
    c.rgb = texture(u_lut, lutCoord).rgb;
    return c;
}"""

    const val LUT_2D_ATLAS = """
uniform sampler2D u_lut;
uniform float u_lutSize;
vec4 process(vec4 c, vec2 uv){
    vec3 col = clamp(c.rgb, 0.0, 1.0);
    float blueCell = col.b * (u_lutSize - 1.0);
    float cellLow = floor(blueCell);
    float cellHigh = ceil(blueCell);
    float fracBlue = fract(blueCell);
    
    float cellsPerRow = 8.0;
    float atlasW = u_lutSize * cellsPerRow;
    float atlasH = u_lutSize * ceil(u_lutSize / cellsPerRow);
    
    vec2 uvLow = vec2(
        (mod(cellLow, cellsPerRow) * u_lutSize + col.r * (u_lutSize - 1.0) + 0.5) / atlasW,
        (floor(cellLow / cellsPerRow) * u_lutSize + col.g * (u_lutSize - 1.0) + 0.5) / atlasH
    );
    vec2 uvHigh = vec2(
        (mod(cellHigh, cellsPerRow) * u_lutSize + col.r * (u_lutSize - 1.0) + 0.5) / atlasW,
        (floor(cellHigh / cellsPerRow) * u_lutSize + col.g * (u_lutSize - 1.0) + 0.5) / atlasH
    );
    
    vec3 colLow = texture(u_lut, uvLow).rgb;
    vec3 colHigh = texture(u_lut, uvHigh).rgb;
    c.rgb = mix(colLow, colHigh, fracBlue);
    return c;
}"""

    // ---------------- TEMPORAL ----------------
    const val TRAILS = """
uniform sampler2D u_prev;
uniform float u_decay;
vec4 process(vec4 c, vec2 uv){
    vec3 prev = texture(u_prev, uv).rgb * u_decay;
    c.rgb = max(c.rgb, prev);
    return c;
}"""

    const val ACCUMULATE = """
uniform sampler2D u_prev;
uniform float u_alpha;
vec4 process(vec4 c, vec2 uv){
    vec3 prev = texture(u_prev, uv).rgb;
    c.rgb = mix(prev, c.rgb, clamp(u_alpha, 0.0, 1.0));
    return c;
}"""

    const val MOTION_TRAIL = TRAILS
    const val FRAME_ACCUMULATE = ACCUMULATE

    // ---------------- MASK COMBINE (single shared program; masks are UNIFORMS, never baked source) ----------------
    const val MASK_COMBINE = """
uniform sampler2D u_effect;
uniform float u_opacity;
uniform int u_blendMode;
uniform sampler2D u_maskTex;
uniform int u_maskType;        // 1 rect, 2 circle, 3 ellipse, 4 linear, 5 radial, 6 texture
uniform vec4 u_maskA;
uniform vec4 u_maskB;
uniform float u_maskInvert;
float maskFactor(vec2 uv) {
    float m = 1.0;
    if (u_maskType == 1) {
        vec2 p = uv - u_maskA.xy;
        float ca = cos(radians(u_maskB.x)), sa = sin(radians(u_maskB.x));
        p = mat2(ca, -sa, sa, ca) * p;
        float sd = sdRoundBox(p, u_maskA.zw * 0.5, u_maskB.y);
        float f = max(u_maskB.z, 0.0001);
        m = 1.0 - smoothstep(-f, f, sd);
    } else if (u_maskType == 2) {
        float d = length(uv - u_maskA.xy) - u_maskA.z;
        float f = max(u_maskB.z, 0.0001);
        m = 1.0 - smoothstep(-f, f, d);
    } else if (u_maskType == 3) {
        vec2 p = uv - u_maskA.xy;
        float ca = cos(radians(u_maskB.x)), sa = sin(radians(u_maskB.x));
        p = mat2(ca, -sa, sa, ca) * p / max(u_maskA.zw, vec2(1.0e-4));
        float d = (length(p) - 1.0) * min(u_maskA.z, u_maskA.w);
        float f = max(u_maskB.z, 0.0001);
        m = 1.0 - smoothstep(-f, f, d);
    } else if (u_maskType == 4) {
        float t = dot(uv, u_maskA.xy);
        m = clamp((t - u_maskB.z) / max(u_maskB.w - u_maskB.z, 1.0e-4), 0.0, 1.0);
    } else if (u_maskType == 5) {
        float d = length(uv - u_maskA.xy);
        m = 1.0 - smoothstep(u_maskA.z, u_maskA.w, d);
    } else if (u_maskType == 6) {
        m = texture(u_maskTex, clampUv(uv)).r;
    }
    return mix(m, 1.0 - m, u_maskInvert);
}
vec4 process(vec4 c, vec2 uv){
    float m = clamp(maskFactor(uv), 0.0, 1.0);
    vec4 fx = texture(u_effect, uv);
    return blendComposite(c, fx, u_blendMode, m * u_opacity);
}"""
}
