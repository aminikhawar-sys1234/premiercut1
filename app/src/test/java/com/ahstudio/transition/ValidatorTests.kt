package com.ahstudio.transition

import com.ahstudio.transition.core.ShaderSource
import com.ahstudio.transition.core.TransitionResult
import com.ahstudio.transition.provider.DirectoryFileSource
import com.ahstudio.transition.transitions.BuiltinTransitions
import com.ahstudio.transition.validation.PackageValidator
import com.ahstudio.transition.validation.ShaderValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShaderValidatorTest {
    @Test fun `builtin shaders validate clean`() {
        BuiltinTransitions.allBuiltins().forEach { d ->
            val v = ShaderValidator.validateDefinition(d)
            assertTrue(d.id + ": " + v.errors, v.isValid)
        }
    }
    @Test fun `banned feature rejected`() {
        val bad = ShaderSource("main",
            "precision mediump float;\nvoid main(){ imageStore(u0, ivec2(0), vec4(0.0)); }")
        val v = ShaderValidator.validateSource(bad, emptyList())
        assertFalse(v.isValid)
        assertTrue(v.errors.any { it.contains("imageStore") })
    }
    @Test fun `version directive rejected`() {
        val bad = ShaderSource("main", "#version 300 es\nvoid main(){}")
        assertFalse(ShaderValidator.validateSource(bad, emptyList()).isValid)
    }
    @Test fun `oversized shader rejected`() {
        val big = ShaderSource("main", "float x = 1.0;\n".repeat(200_000), maxSourceBytes = 64_000)
        assertFalse(ShaderValidator.validateSource(big, emptyList()).isValid)
    }
    @Test fun `non whitelisted extension rejected`() {
        val bad = ShaderSource("main", "#extension GL_some_random_ext : require\nvoid main(){}")
        assertFalse(ShaderValidator.validateSource(bad, emptyList()).isValid)
    }
    @Test fun `external oes extension whitelisted`() {
        val ok = ShaderSource("main", "#extension GL_OES_EGL_image_external_essl3 : require\nvoid main(){}")
        assertTrue(ShaderValidator.validateSource(ok, emptyList()).isValid)
    }
    @Test fun `vertex override without aPosition rejected`() {
        val bad = ShaderSource("main", "void main(){}", vertexOverride = "void main(){}")
        assertFalse(ShaderValidator.validateSource(bad, emptyList()).isValid)
    }
}

class PackageValidatorTest {
    private fun tempPackage(shaderText: String): File {
        val tmp = File.createTempFile("pkg", "")
        tmp.delete()
        val dir = File(tmp.absolutePath + "_dir").apply { mkdirs() }
        File(dir, "main.frag").writeText(shaderText)
        val manifest = """
        {"manifestVersion":1,"packageId":"test.pkg","minEngineVersion":1,
         "transitions":[{"id":"test.pkg.dissolve","name":"D","family":"DISSOLVE","version":1,
           "entryShader":"main","shaders":[{"id":"main","file":"main.frag",
             "sha256":"${PackageValidator.sha256(shaderText.toByteArray())}"}]}]}
        """.trimIndent()
        File(dir, "manifest.json").writeText(manifest)
        return dir
    }

    private fun goodShader() = BuiltinTransitions.crossDissolve().shaders.getValue("main").fragment
    private fun badShader() = "void main(){ imageStore(u, ivec2(0), vec4(0)); }"

    @Test fun `valid package loads`() {
        val dir = tempPackage(goodShader())
        val r = PackageValidator.validateAndLoad(File(dir, "manifest.json").readBytes(),
            DirectoryFileSource(dir))
        val defs = (r as TransitionResult.Ok).value
        assertEquals(1, defs.size)
        assertEquals("test.pkg.dissolve", defs[0].id)
    }
    @Test fun `checksum mismatch rejected`() {
        val dir = tempPackage(goodShader())
        File(dir, "main.frag").writeText("// tampered\n" + goodShader())
        val r = PackageValidator.validateAndLoad(File(dir, "manifest.json").readBytes(),
            DirectoryFileSource(dir))
        assertTrue(r is TransitionResult.Err && (r as TransitionResult.Err).error.message.contains("Checksum"))
    }
    @Test fun `banned shader rejected`() {
        val dir = tempPackage(badShader())
        val r = PackageValidator.validateAndLoad(File(dir, "manifest.json").readBytes(),
            DirectoryFileSource(dir))
        assertTrue(r is TransitionResult.Err)
    }
    @Test fun `future manifest version rejected`() {
        val dir = tempPackage(goodShader())
        val m = File(dir, "manifest.json").readText()
            .replace("\"manifestVersion\":1", "\"manifestVersion\":99")
        val r = PackageValidator.validateAndLoad(m.toByteArray(), DirectoryFileSource(dir))
        assertTrue(r is TransitionResult.Err)
    }
}
