package com.ahstudio.color.gpu

/**
 * Fused color shader with conditional stages (#ifdef). Stage fusion chosen for mobile
 * bandwidth (one pass), with variant cache keyed on feature set (§32, §49).
 * CPU reference in ColorPipelineCpu mirrors this EXACTLY; shared constants injected
 * as #defines from PipelineConsts guarantee preview/export/CPU parity.
 */
object ColorShaderSource {

    const val VERT = """#version 300 es
in vec2 aPos;
out vec2 vUv;
uniform float uFlipY;
void main() {
    vec2 uv = aPos * 0.5 + 0.5;
    vUv = vec2(uv.x, mix(uv.y, 1.0 - uv.y, uFlipY));
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    fun fragment(defines: String): String = """#version 300 es
precision highp float;
$defines
${com.ahstudio.color.primary.PipelineConsts.glslDefines()}

#ifdef TEXTURE_EXTERNAL
#extension GL_OES_EGL_image_external_essl3 : require
uniform samplerExternalOES uInput;
#else
uniform sampler2D uInput;
#endif

in vec2 vUv;
out vec4 fragColor;

// ---- Uniforms (packed vec4s, uploaded only on change) ----
uniform vec4 uLuma;        // xyz = working luma coefs, w = limitedRangeExpand (0/1)
uniform vec4 uPrimary;     // x = exposureMul(exp2 stops), y = contrast, z = pivot, w = saturation
uniform vec4 uTone;        // highlights, shadows, whites, blacks
uniform vec4 uWb;          // wbGain.rgb, tintAppliedFlag
uniform vec4 uVib;         // vibrance, skinProtect, skinHue(0..1), unused
uniform vec4 uOut;         // x = gamutCompress, y = toneMapMode, z = inputTfId, w = outputTfId
uniform vec4 uMisc;        // x = workingTfId, y = splitStrength, z = bwEnabled, w = bwStrength
uniform mat3 uInToWork;
uniform mat3 uWorkToOut;
uniform mat3 uMixer;
uniform vec4 uSplit;       // shadowHue, shadowSat, highlightHue, highlightSat
uniform vec4 uSplitBal;    // balance, unused, unused, unused
uniform vec3 uBw;          // b&w weights

#if HAS_CURVES
uniform sampler2D uCurveTex;     // 256x1 RGBA: r,g,b, master
uniform sampler2D uLumaCurveTex; // 256x1 R
#endif

#if HAS_WHEELS
uniform vec4 uWheelLift;   // rgb + master
uniform vec4 uWheelGamma;
uniform vec4 uWheelGain;
uniform vec4 uWheelOffset;
#endif

#if HAS_HSL
uniform vec4 uHslBands[8]; // x = centerHue, y = hueShift, z = satDelta, w = lumDelta
#endif

#if HAS_LUT3D
uniform sampler3D uLut3d;
uniform vec3 uLutScale;    // (N-1)/N each
uniform vec3 uLutOffset;   // 0.5/N each
uniform float uLutIntensity;
#endif
#if HAS_LUT2D
uniform sampler2D uLut2d;
uniform float uLutSize;
uniform float uLutIntensity;
#endif

#if SEC_COUNT > 0
uniform vec4 uSecHue[2];   // center, halfWidth, softness, invert
uniform vec4 uSecSat[2];   // center, halfWidth, softness, -
uniform vec4 uSecLum[2];   // center, halfWidth, softness, -
uniform vec4 uSecCorr[2];  // gainR, gainG, gainB, intensity
#if SEC_HAS_MASK
uniform sampler2D uSecMask[2];
uniform vec4 uSecMaskCfg[2]; // x = hasMask, y = invertMask
#endif
#endif

uniform vec4 uDebug;       // x = enabled, y = stage(0..7), z = falseColor, w = unused

const float PI_ = 3.141592653589793;

// ================= transfer functions =================
vec3 srgbEncode(vec3 c) {
    vec3 lo = 12.92 * c;
    vec3 hi = 1.055 * pow(max(c, 0.0), vec3(1.0/2.4)) - 0.055;
    return mix(lo, hi, step(vec3(0.0031308), c));
}
vec3 srgbDecode(vec3 c) {
    vec3 lo = c / 12.92;
    vec3 hi = pow((c + 0.055) / 1.055, vec3(2.4));
    return mix(lo, hi, step(vec3(0.04045), c));
}
vec3 rec709Encode(vec3 c) {
    vec3 lo = 4.5 * c;
    vec3 hi = 1.099 * pow(max(c, 0.0), vec3(0.45)) - 0.099;
    return mix(lo, hi, step(vec3(0.018), c));
}
vec3 rec709Decode(vec3 c) {
    vec3 lo = c / 4.5;
    vec3 hi = pow((c + 0.099) / 1.099, vec3(1.0/0.45));
    return mix(lo, hi, step(vec3(0.081), c));
}
vec3 pqDecode(vec3 x) {
    vec3 xp = pow(max(x, vec3(0.0)), vec3(1.0/78.84375));
    vec3 num = max(xp - 0.8359375, vec3(0.0));
    return pow(max(num / (18.8515625 - 18.6875*xp), vec3(0.0)), vec3(1.0/0.1593017578125));
}
vec3 pqEncode(vec3 y) {
    y = clamp(y, 0.0, 1.0);
    vec3 ym = pow(y, vec3(0.1593017578125));
    return pow((0.8359375 + 18.8515625*ym) / (1.0 + 18.6875*ym), vec3(78.84375));
}
vec3 hlgDecode(vec3 e) {
    vec3 lo = e*e/3.0;
    vec3 hi = (exp((e - 0.55991073)/0.17883277) + 0.28466892)/12.0;
    return mix(lo, hi, step(vec3(0.5), e));
}
vec3 hlgEncode(vec3 t) {
    vec3 lo = sqrt(3.0*max(t, vec3(0.0)));
    vec3 hi = 0.17883277*log(12.0*max(t, vec3(0.0)) - 0.28466892) + 0.55991073;
    return mix(lo, hi, step(vec3(1.0/12.0), t));
}
vec3 gammaDecode(vec3 c, float g) { return pow(max(c, vec3(0.0)), vec3(g)); }
vec3 gammaEncode(vec3 c, float g) { return pow(max(c, vec3(0.0)), vec3(1.0/g)); }

vec3 decodeTf(vec3 c, int id) {
    if (id == 0) return c;
    if (id == 1) return srgbDecode(c);
    if (id == 2) return rec709Decode(c);
    if (id == 3) return pqDecode(c);
    if (id == 4) return hlgDecode(c);
    if (id == 10) return gammaDecode(c, 2.2);
    if (id == 11) return gammaDecode(c, 2.4);
    return srgbDecode(c);
}
vec3 encodeTf(vec3 c, int id) {
    if (id == 0) return c;
    if (id == 1) return srgbEncode(c);
    if (id == 2) return rec709Encode(c);
    if (id == 3) return pqEncode(c);
    if (id == 4) return hlgEncode(c);
    if (id == 10) return gammaEncode(c, 2.2);
    if (id == 11) return gammaEncode(c, 2.4);
    return srgbEncode(c);
}

// ================= helpers =================
float lumaW(vec3 c) { return dot(c, uLuma.xyz); }

vec3 rgb2hsl(vec3 c) {
    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float l = 0.5*(mx+mn); float h = 0.0; float s = 0.0;
    float d = mx - mn;
    if (d > 1e-6) {
        s = l > 0.5 ? d/(2.0-mx-mn) : d/(mx+mn);
        if (mx == c.r) h = (c.g-c.b)/d + (c.g < c.b ? 6.0 : 0.0);
        else if (mx == c.g) h = (c.b-c.r)/d + 2.0;
        else h = (c.r-c.g)/d + 4.0;
        h /= 6.0;
    }
    return vec3(h, s, l);
}
float hue2rgb(float p, float q, float t) {
    if (t < 0.0) t += 1.0;
    if (t > 1.0) t -= 1.0;
    if (t < 1.0/6.0) return p + (q-p)*6.0*t;
    if (t < 0.5) return q;
    if (t < 2.0/3.0) return p + (q-p)*(2.0/3.0-t)*6.0;
    return p;
}
vec3 hsl2rgb(vec3 hsl) {
    if (hsl.y <= 0.0) return vec3(hsl.z);
    float q = hsl.z < 0.5 ? hsl.z*(1.0+hsl.y) : hsl.z + hsl.y - hsl.z*hsl.y;
    float p = 2.0*hsl.z - q;
    return vec3(hue2rgb(p,q,hsl.x+1.0/3.0), hue2rgb(p,q,hsl.x), hue2rgb(p,q,hsl.x-1.0/3.0));
}

// ================= stages =================
vec3 toneDir(vec3 c, float amt, float mask, float upK, float downK) {
    if (amt >= 0.0) return c + amt * mask * upK * (vec3(1.0) - min(c, vec3(1.0)));
    return c * (1.0 + amt * mask * downK);
}

vec3 applyToneControls(vec3 c) {
    float ly = sqrt(max(lumaW(c), 0.0));
    float mHi = smoothstep(MASK_HI0, MASK_HI1, ly);
    float mSh = 1.0 - smoothstep(MASK_SH0, MASK_SH1, ly);
    float mWh = smoothstep(MASK_WH0, MASK_WH1, ly);
    float mBl = 1.0 - smoothstep(MASK_BL0, MASK_BL1, ly);
    c = toneDir(c, uTone.x, mHi, TONE_HI_UP, TONE_HI_DOWN);   // highlights
    c = toneDir(c, uTone.y, mSh, TONE_SH_UP, TONE_SH_DOWN);   // shadows
    c = toneDir(c, uTone.z, mWh, TONE_WH_UP, TONE_WH_DOWN);   // whites
    c = toneDir(c, uTone.w, mBl, TONE_BL_UP, TONE_BL_DOWN);   // blacks
    return c;
}

vec3 applyContrast(vec3 c) {
    float pivot = clamp(uPrimary.z, 1e-4, 1.0);
    float lp = log2(pivot);
    vec3 lc = log2(max(c, vec3(LOG_EPS)));
    lc = lp + (lc - lp) * (1.0 + uPrimary.y);
    return exp2(lc);
}

#if HAS_WHEELS
vec3 applyWheels(vec3 c) {
    float ly = sqrt(max(lumaW(clamp(c, 0.0, 1.0)), 0.0));
    float wLift = 1.0 - smoothstep(0.0, 0.5, ly);
    float wGain = smoothstep(0.4, 1.0, ly);
    float wGamma = clamp(1.0 - abs(ly - 0.5)*2.0, 0.0, 1.0);
    vec3 lift = uWheelLift.rgb * uWheelLift.a;
    vec3 gam  = uWheelGamma.rgb * uWheelGamma.a;
    vec3 gai  = uWheelGain.rgb * uWheelGain.a;
    vec3 off  = uWheelOffset.rgb * uWheelOffset.a;
    c += lift * (wLift * WHEEL_LIFT_K);
    vec3 gc = pow(max(c, vec3(0.0)), vec3(1.0) / (1.0 + gam * WHEEL_GAMMA_K));
    c = mix(c, gc, wGamma);
    c = mix(c, c * (1.0 + gai), wGain);
    c += off * WHEEL_OFFSET_K;
    return c;
}
#endif

#if HAS_CURVES
vec3 applyCurves(vec3 c) {
    vec3 cc = vec3(
        texture(uCurveTex, vec2(clamp(c.r,0.0,1.0), 0.5)).r,
        texture(uCurveTex, vec2(clamp(c.g,0.0,1.0), 0.5)).g,
        texture(uCurveTex, vec2(clamp(c.b,0.0,1.0), 0.5)).b);
    cc = vec3(
        texture(uCurveTex, vec2(clamp(cc.r,0.0,1.0), 0.5)).a,
        texture(uCurveTex, vec2(clamp(cc.g,0.0,1.0), 0.5)).a,
        texture(uCurveTex, vec2(clamp(cc.b,0.0,1.0), 0.5)).a);
    float y0 = lumaW(cc);
    float y1 = texture(uLumaCurveTex, vec2(clamp(y0,0.0,1.0), 0.5)).r;
    if (y0 > 1e-5) cc *= y1 / y0;
    return cc;
}
#endif

#if HAS_HSL
vec3 applyHsl(vec3 c) {
    vec3 hsl = rgb2hsl(clamp(c, 0.0, 1.0));
    vec3 acc = vec3(0.0); float accW = 0.0;
    for (int i = 0; i < 8; i++) {
        vec4 b = uHslBands[i];
        float d = abs(fract(hsl.x - b.x + 0.5) - 0.5);
        float w = 1.0 - smoothstep(HSL_CORE, HSL_CORE + HSL_FALLOFF, d);
        acc += b.yzw * w;
        accW += w;
    }
    if (accW < 1e-4) return c;
    vec3 adj = acc / accW;
    hsl.x = fract(hsl.x + adj.x);
    hsl.y = clamp(hsl.y * (1.0 + adj.y), 0.0, 1.0);
    hsl.z = clamp(hsl.z + adj.z * 0.5, 0.0, 1.0);
    return hsl2rgb(hsl);
}
#endif

vec3 applySatVib(vec3 c) {
    float y = lumaW(c);
    c = mix(vec3(y), c, 1.0 + uPrimary.w);
    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float sat = mx - mn;
    vec3 hsl = rgb2hsl(clamp(c, 0.0, 1.0));
    float hueDeg = hsl.x * 360.0;
    float dh = (hueDeg - uVib.z * 360.0);
    float skin = exp(-(dh*dh) / (2.0*SKIN_WIDTH*SKIN_WIDTH));
    float amt = uVib.x * (1.0 - sat) * mix(1.0, 1.0 - skin, uVib.y);
    return mix(vec3(y), c, 1.0 + amt);
}

#if HAS_SPLIT || HAS_BW
vec3 applySplitBw(vec3 c) {
    #if HAS_BW
    if (uMisc.z > 0.5) {
        float g = dot(c, uBw);
        c = mix(c, vec3(g), uMisc.w);
    }
    #endif
    #if HAS_SPLIT
    float y = lumaW(clamp(c, 0.0, 1.0));
    float bal = uSplitBal.x;
    float strength = uMisc.y;
    if (strength > 0.001) {
        float wSh = 1.0 - smoothstep(0.0, 0.5 + bal * SPLIT_SPREAD, y);
        float wHi = smoothstep(0.5 - bal * SPLIT_SPREAD, 1.0, y);
        vec3 hsl = rgb2hsl(clamp(c, 0.0, 1.0));
        vec3 sh = hsl2rgb(vec3(uSplit.x, uSplit.y, hsl.z));
        vec3 hi = hsl2rgb(vec3(uSplit.z, uSplit.w, hsl.z));
        c = mix(c, sh, wSh * strength);
        c = mix(c, hi, wHi * strength);
    }
    #endif
    return c;
}
#endif

#if HAS_LUT3D
vec3 applyLut(vec3 c) {
    vec3 d = clamp(c, 0.0, 1.0);
    vec3 s = texture(uLut3d, d * uLutScale + uLutOffset).rgb;
    return mix(c, s, uLutIntensity);
}
#endif
#if HAS_LUT2D
vec3 applyLut(vec3 c) {
    float n = uLutSize;
    vec3 d = clamp(c, 0.0, 1.0);
    float sx = d.r * (n - 1.0), sy = d.g * (n - 1.0), sz = d.b * (n - 1.0);
    float x = floor(sx), y = floor(sy), z = floor(sz);
    float fx = sx - x, fy = sy - y, fz = sz - z;
    vec2 ts = vec2(1.0/(n*n), 1.0/n);
    vec2 uv0 = vec2((z*n + x), y) * ts + ts*0.5;
    vec2 uv1 = vec2(((z+1.0)*n + x), y) * ts + ts*0.5;
    vec2 uvx = vec2(1.0/(n), 0.0);
    vec2 uvy = vec2(0.0, 1.0/n);
    vec3 c000 = texture(uLut2d, uv0).rgb;
    vec3 c100 = texture(uLut2d, uv0 + uvx).rgb;
    vec3 c010 = texture(uLut2d, uv0 + uvy).rgb;
    vec3 c110 = texture(uLut2d, uv0 + uvx + uvy).rgb;
    vec3 c001 = texture(uLut2d, uv1).rgb;
    vec3 c101 = texture(uLut2d, uv1 + uvx).rgb;
    vec3 c011 = texture(uLut2d, uv1 + uvy).rgb;
    vec3 c111 = texture(uLut2d, uv1 + uvx + uvy).rgb;
    vec3 c00 = mix(c000, c100, fx); vec3 c10 = mix(c010, c110, fx);
    vec3 c01 = mix(c001, c101, fx); vec3 c11 = mix(c011, c111, fx);
    vec3 c0 = mix(c00, c10, fy);    vec3 c1 = mix(c01, c11, fy);
    return mix(c, mix(c0, c1, fz), uLutIntensity);
}
#endif

#if SEC_COUNT > 0
vec3 applySecondary(vec3 c, int i) {
    vec3 hsl = rgb2hsl(clamp(c, 0.0, 1.0));
    vec4 H = uSecHue[i]; vec4 S = uSecSat[i]; vec4 L = uSecLum[i];
    float dh = abs(fract(hsl.x - H.x + 0.5) - 0.5);
    float m = 1.0 - smoothstep(H.y, H.y + max(H.z, 0.001), dh);
    m *= 1.0 - smoothstep(S.y, S.y + max(S.z, 0.001), abs(hsl.y - S.x));
    m *= 1.0 - smoothstep(L.y, L.y + max(L.z, 0.001), abs(hsl.z - L.x));
    if (H.w > 0.5) m = 1.0 - m;
    #if SEC_HAS_MASK
    if (uSecMaskCfg[i].x > 0.5) {
        float mk = texture(uSecMask[i], vUv).r;
        if (uSecMaskCfg[i].y > 0.5) mk = 1.0 - mk;
        m *= mk;
    }
    #endif
    vec4 C = uSecCorr[i];
    vec3 graded = c * vec3(C.x, C.y, C.z);
    return mix(c, graded, clamp(m, 0.0, 1.0) * C.w);
}
#endif

vec3 hableFn(vec3 x) {
    const float A = 0.15, B = 0.50, C = 0.10, D = 0.20, E = 0.02, F = 0.30;
    return ((x*(A*x+C*B)+D*E)/(x*(A*x+B)+D*F)) - E/F;
}
vec3 applyToneMap(vec3 c) {
    int mode = int(uOut.y);
    if (mode == 0) return c;
    vec3 x = max(c, vec3(0.0));
    if (mode == 1) {
        return clamp((x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14), 0.0, 1.0);
    }
    if (mode == 2) {
        return x*(1.0 + x/16.0)/(1.0 + x);
    }
    return hableFn(x*2.0) / hableFn(vec3(11.2));
}

vec3 gamutCompress(vec3 c) {
    float y = lumaW(c);
    vec3 d = c - y;
    bool oog = any(lessThan(c, vec3(-1e-4))) || any(greaterThan(c, vec3(1.0+1e-4)));
    if (!oog) return c;
    float lo = 0.0, hi = 1.0;
    for (int i = 0; i < 6; i++) {
        float m = (lo + hi) * 0.5;
        vec3 t = y + d * m;
        if (!any(lessThan(t, vec3(-1e-4))) && !any(greaterThan(t, vec3(1.0+1e-4)))) lo = m; else hi = m;
    }
    return y + d * lo;
}

bool debugTap(int stage, vec3 c, int tfId, int outTfId) {
    if (uDebug.x < 0.5) return false;
    if (int(uDebug.y) != stage) return false;
    vec3 shown = encodeTf(c, outTfId);
    if (uDebug.z > 0.5) {
        float l = lumaW(shown);
        vec3 fc = l < 0.05 ? vec3(0.0,0.0,1.0)
                : l < 0.2  ? vec3(0.0,1.0,1.0)
                : l < 0.5  ? vec3(0.0,1.0,0.0)
                : l < 0.8  ? vec3(1.0,1.0,0.0)
                : l < 0.95 ? vec3(1.0,0.5,0.0)
                : vec3(1.0,0.0,0.0);
        fragColor = vec4(fc, 1.0);
    } else {
        fragColor = vec4(clamp(shown, 0.0, 1.0), 1.0);
    }
    return true;
}

void main() {
    vec3 c = texture(uInput, vUv).rgb;

    // ---- Input decode ----
    #if RANGE_EXPAND
    c = (c - vec3(16.0/255.0)) * (255.0/219.0);
    #endif
    c = decodeTf(c, int(uOut.z));
    if (debugTap(0, c, int(uOut.z), int(uOut.w))) return;

    // ---- Input primaries -> working ----
    c = uInToWork * c;
    if (debugTap(1, c, int(uOut.z), int(uOut.w))) return;

    #if LUT_BEFORE && (HAS_LUT3D || HAS_LUT2D)
    c = applyLut(c);
    if (debugTap(15, c, int(uOut.z), int(uOut.w))) return;
    #endif

    // ---- PRIMARY (linear light) ----
    c *= uPrimary.x;                       // exposure
    c *= uWb.rgb;                          // white balance gains
    c = applyToneControls(c);              // highlights/shadows/whites/blacks
    c = applyContrast(c);                  // log-space contrast
    if (debugTap(2, c, int(uOut.z), int(uOut.w))) return;

    #if HAS_WHEELS
    c = applyWheels(c);
    #endif
    if (debugTap(3, c, int(uOut.z), int(uOut.w))) return;

    // ---- Perceptual domain (encode to working TF) ----
    c = encodeTf(max(c, vec3(0.0)), int(uMisc.x));

    #if HAS_CURVES
    c = applyCurves(c);
    #endif
    if (debugTap(4, c, int(uOut.z), int(uOut.w))) return;

    #if HAS_HSL
    c = applyHsl(c);
    #endif

    c = applySatVib(c);
    if (debugTap(5, c, int(uOut.z), int(uOut.w))) return;

    #if HAS_SPLIT || HAS_BW
    c = applySplitBw(c);
    #endif

    c = uMixer * c;

    #if LUT_AFTER && (HAS_LUT3D || HAS_LUT2D)
    c = applyLut(c);
    #endif
    if (debugTap(6, c, int(uOut.z), int(uOut.w))) return;

    #if SEC_COUNT > 0
    c = applySecondary(c, 0);
    #if SEC_COUNT > 1
    c = applySecondary(c, 1);
    #endif
    #endif

    // ---- Output transform ----
    c = decodeTf(c, int(uMisc.x));         // back to linear
    c = applyToneMap(c);                   // HDR -> target
    c = uWorkToOut * c;                    // working -> output primaries
    if (uOut.x > 0.5) c = gamutCompress(c);
    c = encodeTf(c, int(uOut.w));          // output transfer

    #ifdef HDR_OUTPUT
    fragColor = vec4(max(c, vec3(0.0)), 1.0);
    #else
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
    #endif
}
"""
}
