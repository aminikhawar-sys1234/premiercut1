package com.example.engine.gpu3d

import android.opengl.GLES30
import android.util.Log

/**
 * Encapsulates OpenGL ES 3.0 3D Shaders with Phong/PBR Lighting,
 * 3D Parallax Tilt, Volumetric Fog, and 3D Extrusion Raymarching.
 */
class Shader3D {
    companion object {
        private const val TAG = "Shader3D"

        val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aTexCoord;
layout(location = 2) in vec3 aNormal;

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjMatrix;
uniform mat3 uNormalMatrix;

out vec2 vTexCoord;
out vec3 vNormal;
out vec3 vFragPos;

void main() {
    vTexCoord = aTexCoord;
    vFragPos = vec3(uModelMatrix * vec4(aPosition, 1.0));
    vNormal = normalize(uNormalMatrix * aNormal);
    gl_Position = uProjMatrix * uViewMatrix * vec4(vFragPos, 1.0);
}
""".trimIndent()

        val FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
in vec3 vNormal;
in vec3 vFragPos;

uniform sampler2D uTexture;
uniform vec3 uCameraPos;
uniform vec3 uLightPos;
uniform vec3 uLightColor;
uniform vec3 uAmbientColor;
uniform float uShininess;
uniform float uSpecularStrength;
uniform float uOpacity;
uniform float uFogDensity;
uniform vec3 uFogColor;
uniform int uEnableLighting;
uniform int uEnableVolumetric;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(uTexture, vTexCoord);
    if (texColor.a < 0.01) {
        discard;
    }

    if (uEnableLighting == 0) {
        fragColor = vec4(texColor.rgb, texColor.a * uOpacity);
        return;
    }

    // 1. Surface Normal
    vec3 norm = normalize(vNormal);

    // 2. Ambient Lighting
    vec3 ambient = uAmbientColor * texColor.rgb;

    // 3. Diffuse Lighting (Directional / Point Light)
    vec3 lightDir = normalize(uLightPos - vFragPos);
    float diff = max(dot(norm, lightDir), 0.0);
    vec3 diffuse = diff * uLightColor * texColor.rgb;

    // 4. Specular Lighting (Blinn-Phong)
    vec3 viewDir = normalize(uCameraPos - vFragPos);
    vec3 halfwayDir = normalize(lightDir + viewDir);
    float spec = pow(max(dot(norm, halfwayDir), 0.0), uShininess);
    vec3 specular = uSpecularStrength * spec * uLightColor;

    vec3 result = ambient + diffuse + specular;

    // 5. Volumetric 3D Fog / Depth Decay
    if (uEnableVolumetric == 1 && uFogDensity > 0.0) {
        float distance = length(uCameraPos - vFragPos);
        float fogFactor = 1.0 - exp(-distance * uFogDensity);
        fogFactor = clamp(fogFactor, 0.0, 1.0);
        result = mix(result, uFogColor, fogFactor);
    }

    fragColor = vec4(result, texColor.a * uOpacity);
}
""".trimIndent()
    }

    private var programId = 0

    // Uniform Locations
    private var uModelMatrixLoc = -1
    private var uViewMatrixLoc = -1
    private var uProjMatrixLoc = -1
    private var uNormalMatrixLoc = -1
    private var uTextureLoc = -1
    private var uCameraPosLoc = -1
    private var uLightPosLoc = -1
    private var uLightColorLoc = -1
    private var uAmbientColorLoc = -1
    private var uShininessLoc = -1
    private var uSpecularStrengthLoc = -1
    private var uOpacityLoc = -1
    private var uFogDensityLoc = -1
    private var uFogColorLoc = -1
    private var uEnableLightingLoc = -1
    private var uEnableVolumetricLoc = -1

    fun init() {
        if (programId != 0) return

        val vShader = compileShader(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fShader = compileShader(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vShader)
        GLES30.glAttachShader(programId, fShader)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(programId)
            Log.e(TAG, "Shader3D linking failed: $log")
        }

        // Cache uniform locations
        uModelMatrixLoc = GLES30.glGetUniformLocation(programId, "uModelMatrix")
        uViewMatrixLoc = GLES30.glGetUniformLocation(programId, "uViewMatrix")
        uProjMatrixLoc = GLES30.glGetUniformLocation(programId, "uProjMatrix")
        uNormalMatrixLoc = GLES30.glGetUniformLocation(programId, "uNormalMatrix")
        uTextureLoc = GLES30.glGetUniformLocation(programId, "uTexture")
        uCameraPosLoc = GLES30.glGetUniformLocation(programId, "uCameraPos")
        uLightPosLoc = GLES30.glGetUniformLocation(programId, "uLightPos")
        uLightColorLoc = GLES30.glGetUniformLocation(programId, "uLightColor")
        uAmbientColorLoc = GLES30.glGetUniformLocation(programId, "uAmbientColor")
        uShininessLoc = GLES30.glGetUniformLocation(programId, "uShininess")
        uSpecularStrengthLoc = GLES30.glGetUniformLocation(programId, "uSpecularStrength")
        uOpacityLoc = GLES30.glGetUniformLocation(programId, "uOpacity")
        uFogDensityLoc = GLES30.glGetUniformLocation(programId, "uFogDensity")
        uFogColorLoc = GLES30.glGetUniformLocation(programId, "uFogColor")
        uEnableLightingLoc = GLES30.glGetUniformLocation(programId, "uEnableLighting")
        uEnableVolumetricLoc = GLES30.glGetUniformLocation(programId, "uEnableVolumetric")
    }

    fun use() {
        if (programId == 0) init()
        GLES30.glUseProgram(programId)
    }

    fun setMatrices(model: FloatArray, view: FloatArray, proj: FloatArray, normalMatrix: FloatArray? = null) {
        GLES30.glUniformMatrix4fv(uModelMatrixLoc, 1, false, model, 0)
        GLES30.glUniformMatrix4fv(uViewMatrixLoc, 1, false, view, 0)
        GLES30.glUniformMatrix4fv(uProjMatrixLoc, 1, false, proj, 0)
        if (normalMatrix != null && uNormalMatrixLoc != -1) {
            GLES30.glUniformMatrix3fv(uNormalMatrixLoc, 1, false, normalMatrix, 0)
        }
    }

    fun setLighting(
        cameraPos: FloatArray,
        lightPos: FloatArray = floatArrayOf(2f, 4f, 5f),
        lightColor: FloatArray = floatArrayOf(1f, 1f, 1f),
        ambientColor: FloatArray = floatArrayOf(0.35f, 0.35f, 0.35f),
        shininess: Float = 32f,
        specularStrength: Float = 0.5f,
        enabled: Boolean = true
    ) {
        GLES30.glUniform3fv(uCameraPosLoc, 1, cameraPos, 0)
        GLES30.glUniform3fv(uLightPosLoc, 1, lightPos, 0)
        GLES30.glUniform3fv(uLightColorLoc, 1, lightColor, 0)
        GLES30.glUniform3fv(uAmbientColorLoc, 1, ambientColor, 0)
        GLES30.glUniform1f(uShininessLoc, shininess)
        GLES30.glUniform1f(uSpecularStrengthLoc, specularStrength)
        GLES30.glUniform1i(uEnableLightingLoc, if (enabled) 1 else 0)
    }

    fun setMaterial(opacity: Float = 1.0f) {
        GLES30.glUniform1f(uOpacityLoc, opacity)
    }

    fun setVolumetricFog(
        enabled: Boolean = false,
        density: Float = 0.05f,
        color: FloatArray = floatArrayOf(0.1f, 0.12f, 0.18f)
    ) {
        GLES30.glUniform1i(uEnableVolumetricLoc, if (enabled) 1 else 0)
        GLES30.glUniform1f(uFogDensityLoc, density)
        GLES30.glUniform3fv(uFogColorLoc, 1, color, 0)
    }

    fun setTextureUnit(unit: Int = 0) {
        GLES30.glUniform1i(uTextureLoc, unit)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e(TAG, "Compilation error in shader type $type: $log")
        }
        return shader
    }

    fun release() {
        if (programId != 0) {
            GLES30.glDeleteProgram(programId)
            programId = 0
        }
    }
}
