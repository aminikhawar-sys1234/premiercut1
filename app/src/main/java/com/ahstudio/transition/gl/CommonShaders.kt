package com.ahstudio.transition.gl

/** Engine-internal shader sources shared by the renderer (not part of any definition). */
object CommonShaders {
    const val FULLSCREEN_VERTEX = """
in vec2 aPosition;
in vec2 aUv;
out vec2 vUv;
void main() {
    vUv = aUv;
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
"""

    /** Source normalization: applies the host transform matrix once; canonical uv for all passes. */
    const val NORMALIZE_FRAG = """
#ifdef EXTERNAL_OES
#extension GL_OES_EGL_image_external_essl3 : require
#endif
precision highp float;
#ifdef EXTERNAL_OES
uniform samplerExternalOES uSrc;
#else
uniform sampler2D uSrc;
#endif
uniform mat4 uTransform;
in vec2 vUv;
out vec4 oColor;
void main() {
    vec2 uv = (uTransform * vec4(vUv, 0.0, 1.0)).xy;
    oColor = texture(uSrc, uv);
}
"""

    /** Safe fallback: plain passthrough of Clip B (§26). */
    const val PASSTHROUGH_FRAG = """
precision highp float;
uniform sampler2D uTextureB;
in vec2 vUv;
out vec4 oColor;
void main() { oColor = texture(uTextureB, vUv); }
"""
}
