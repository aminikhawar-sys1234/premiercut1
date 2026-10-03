package com.ute.gpu

import android.opengl.GLES30.*
import com.ute.core.Mat4
import com.ute.scene3d.LightRig
import com.ute.scene3d.TextMaterial
import com.ute.text3d.ExtrusionBuilder

class GlMeshRenderer {

    private var program = -1

    fun ensure() {
        if (program > 0) return
        program = GlUtil.compileProgram(Shaders.MESH_VERT, Shaders.MESH_FRAG, "text3d")
    }

    fun drawMesh(
        mesh: ExtrusionBuilder.Mesh, model: Mat4, viewProj: Mat4,
        material: TextMaterial, rig: LightRig, camPos: FloatArray, gradientEnabled: Boolean,
    ) {
        if (mesh.triangleCount == 0) return
        ensure()
        val vao = run { val ids = IntArray(1); glGenVertexArrays(1, ids, 0); ids[0] }
        val vbo = run { val ids = IntArray(1); glGenBuffers(1, ids, 0); ids[0] }
        try {
            glBindVertexArray(vao)
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            val stride = mesh.positions.size / 3
            val data = FloatArray(stride * 8)
            for (v in 0 until stride) {
                data[v*8] = mesh.positions[v*3]; data[v*8+1] = mesh.positions[v*3+1]; data[v*8+2] = mesh.positions[v*3+2]
                data[v*8+3] = mesh.normals[v*3]; data[v*8+4] = mesh.normals[v*3+1]; data[v*8+5] = mesh.normals[v*3+2]
                data[v*8+6] = (mesh.positions[v*3] / 200f + 0.5f).coerceIn(0f, 1f); data[v*8+7] = 0.5f
            }
            glBufferData(GL_ARRAY_BUFFER, data.size * 4, GlUtil.floatBuffer(data), GL_STATIC_DRAW)
            glEnableVertexAttribArray(0); glVertexAttribPointer(0, 3, GL_FLOAT, false, 32, 0)
            glEnableVertexAttribArray(1); glVertexAttribPointer(1, 3, GL_FLOAT, false, 32, 12)
            glEnableVertexAttribArray(2); glVertexAttribPointer(2, 2, GL_FLOAT, false, 32, 24)

            glUseProgram(program)
            glUniformMatrix4fv(glGetUniformLocation(program, "uViewProj"), 1, false, viewProj.m, 0)
            glUniformMatrix4fv(glGetUniformLocation(program, "uModel"), 1, false, model.m, 0)
            val c = material.baseColorTint
            glUniform3f(glGetUniformLocation(program, "uBaseColor"),
                ((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
            glUniform1f(glGetUniformLocation(program, "uMetallic"), material.metallic)
            glUniform1f(glGetUniformLocation(program, "uRoughness"), material.roughness)
            glUniform1f(glGetUniformLocation(program, "uEmissive"), material.emissiveStrength)
            glUniform1f(glGetUniformLocation(program, "uOpacity"), material.opacity)
            glUniform1f(glGetUniformLocation(program, "uRim"), material.rimStrength)
            glUniform1f(glGetUniformLocation(program, "uGlassy"), material.glassiness)
            glUniform3f(glGetUniformLocation(program, "uCamPos"), camPos[0], camPos[1], camPos[2])
            glUniform4f(glGetUniformLocation(program, "uGradient"), 1f, 0.84f, 0.4f, if (gradientEnabled) 1f else 0f)

            var ambientSet = false
            var li = 0
            val posArr = FloatArray(16); val dirArr = FloatArray(16); val colArr = FloatArray(16)
            for (light in rig.sanitized()) {
                when (light) {
                    is com.ute.scene3d.Light.Ambient -> {
                        glUniform3f(glGetUniformLocation(program, "uAmbient"),
                            ((light.color shr 16) and 0xFF) / 255f * light.intensity,
                            ((light.color shr 8) and 0xFF) / 255f * light.intensity,
                            (light.color and 0xFF) / 255f * light.intensity)
                        ambientSet = true
                    }
                    is com.ute.scene3d.Light.Directional -> if (li < 4) {
                        posArr[li*4+3] = 1f
                        dirArr[li*4] = light.dirX; dirArr[li*4+1] = light.dirY; dirArr[li*4+2] = light.dirZ
                        colArr[li*4] = ((light.color shr 16) and 0xFF) / 255f
                        colArr[li*4+1] = ((light.color shr 8) and 0xFF) / 255f
                        colArr[li*4+2] = (light.color and 0xFF) / 255f
                        colArr[li*4+3] = light.intensity; li++
                    }
                    is com.ute.scene3d.Light.Point -> if (li < 4) {
                        posArr[li*4] = light.x; posArr[li*4+1] = light.y; posArr[li*4+2] = light.z; posArr[li*4+3] = 0f
                        colArr[li*4] = ((light.color shr 16) and 0xFF) / 255f
                        colArr[li*4+1] = ((light.color shr 8) and 0xFF) / 255f
                        colArr[li*4+2] = (light.color and 0xFF) / 255f
                        colArr[li*4+3] = light.intensity; li++
                    }
                    is com.ute.scene3d.Light.Spot -> if (li < 4) {
                        posArr[li*4] = light.x; posArr[li*4+1] = light.y; posArr[li*4+2] = light.z; posArr[li*4+3] = 2f
                        dirArr[li*4] = light.dirX; dirArr[li*4+1] = light.dirY; dirArr[li*4+2] = light.dirZ
                        dirArr[li*4+3] = light.coneDeg
                        colArr[li*4] = ((light.color shr 16) and 0xFF) / 255f
                        colArr[li*4+1] = ((light.color shr 8) and 0xFF) / 255f
                        colArr[li*4+2] = (light.color and 0xFF) / 255f
                        colArr[li*4+3] = light.intensity; li++
                    }
                    is com.ute.scene3d.Light.Rim -> { }
                }
            }
            if (!ambientSet) glUniform3f(glGetUniformLocation(program, "uAmbient"), 0.28f, 0.28f, 0.3f)
            glUniform4fv(glGetUniformLocation(program, "uLightPos"), 4, posArr, 0)
            glUniform4fv(glGetUniformLocation(program, "uLightDir"), 4, dirArr, 0)
            glUniform4fv(glGetUniformLocation(program, "uLightColor"), 4, colArr, 0)

            glEnable(GL_DEPTH_TEST)
            glEnable(GL_BLEND)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            glDrawArrays(GL_TRIANGLES, 0, stride)
            glBindVertexArray(0)
        } finally {
            glDeleteVertexArrays(1, intArrayOf(vao), 0)
            glDeleteBuffers(1, intArrayOf(vbo), 0)
        }
    }

    object Shaders {
        const val MESH_VERT = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec2 aUv;
uniform mat4 uViewProj;
uniform mat4 uModel;
out vec3 vNormal; out vec3 vWorldPos; out vec2 vUvFront;
void main() {
    vec4 wp = uModel * vec4(aPos, 1.0);
    vWorldPos = wp.xyz;
    vNormal = mat3(uModel) * aNormal;
    vUvFront = aUv;
    gl_Position = uViewProj * wp;
}
"""

        const val MESH_FRAG = """#version 300 es
precision highp float;
in vec3 vNormal;
in vec3 vWorldPos;
in vec2 vUvFront;
out vec4 fragColor;

uniform vec3  uBaseColor;
uniform float uMetallic, uRoughness, uEmissive, uOpacity, uRim, uGlassy;
uniform vec3  uCamPos;
uniform vec4  uGradient;
uniform vec4  uLightPos[4];
uniform vec4  uLightDir[4];
uniform vec4  uLightColor[4];
uniform vec3  uAmbient;

void main() {
    vec3 N = normalize(vNormal);
    vec3 V = normalize(uCamPos - vWorldPos);
    if (!gl_FrontFacing) N = -N;

    vec3 base = mix(uBaseColor, uGradient.rgb, uGradient.w * vUvFront.x * step(0.5, N.z));
    vec3 albedo = mix(base, base * 0.4, uMetallic);
    vec3 specularTint = mix(vec3(1.0), base, uMetallic);

    vec3 color = albedo * uAmbient;
    float shininess = mix(8.0, 256.0, 1.0 - uRoughness);

    for (int i = 0; i < 4; i++) {
        if (uLightColor[i].a <= 0.0) continue;
        vec3 L;
        float atten = 1.0;
        if (uLightPos[i].w < 0.5) {
            vec3 d = uLightPos[i].xyz - vWorldPos;
            float dist = length(d);
            L = d / max(dist, 1e-4);
            atten = 1.0 / (1.0 + 0.002 * dist * dist);
        } else if (uLightPos[i].w < 1.5) {
            L = normalize(-uLightDir[i].xyz);
        } else {
            vec3 d = uLightPos[i].xyz - vWorldPos;
            float dist = length(d);
            L = d / max(dist, 1e-4);
            float cosA = dot(-L, normalize(uLightDir[i].xyz));
            float cone = smoothstep(cos(radians(uLightDir[i].w)), cos(radians(uLightDir[i].w)) * 0.7, cosA);
            atten = cone / (1.0 + 0.002 * dist * dist);
        }
        float ndl = max(dot(N, L), 0.0);
        vec3 H = normalize(L + V);
        float spec = pow(max(dot(N, H), 0.0), shininess) * (1.0 - uRoughness * 0.8);
        color += (albedo * ndl + specularTint * spec * (0.3 + uGlassy)) * uLightColor[i].rgb * uLightColor[i].a * atten;
    }

    float fres = pow(1.0 - max(dot(N, V), 0.0), 3.0);
    color += fres * uRim * vec3(0.75, 0.91, 1.0);
    color += albedo * uEmissive;

    fragColor = vec4(color, uOpacity);
}
"""
    }
}
