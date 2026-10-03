package com.ahstudio.transition.core

sealed class PassInput {
    object ClipA : PassInput()
    object ClipB : PassInput()
    data class Pass(val passId: String) : PassInput()   // sample upstream pass via uniform sampler2D u_<passId>
}

sealed class PassOutput {
    object Final : PassOutput()
    data class Intermediate(val id: String, val resolutionScale: Float = 1f) : PassOutput()
}

data class TransitionPassSpec(
    val id: String,
    val shaderId: String,
    val inputs: List<PassInput>,
    val output: PassOutput,
    val defines: Map<String, String> = emptyMap(),
    val blendEnabled: Boolean = false,   // premultiplied blend (ONE, ONE_MINUS_SRC_ALPHA)
)

data class TransitionRenderGraphSpec(val passes: List<TransitionPassSpec>) {
    init {
        require(passes.isNotEmpty()) { "Render graph must contain at least one pass" }
        require(passes.count { it.output is PassOutput.Final } == 1) {
            "Render graph must contain exactly one Final pass"
        }
        val seen = HashSet<String>()
        passes.forEach { p ->
            require(seen.add(p.id)) { "Duplicate pass id '${p.id}'" }
            p.inputs.forEach {
                if (it is PassInput.Pass) require(it.passId in seen) {
                    "Pass '${p.id}' references later/unknown pass '${it.passId}' (cycles and self-feedback are forbidden)"
                }
            }
        }
    }
    companion object {
        fun singlePass(mainShaderId: String) = TransitionRenderGraphSpec(
            listOf(TransitionPassSpec("main", mainShaderId,
                listOf(PassInput.ClipA, PassInput.ClipB), PassOutput.Final)))
    }
}
