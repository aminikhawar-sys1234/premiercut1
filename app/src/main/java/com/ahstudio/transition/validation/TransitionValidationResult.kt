package com.ahstudio.transition.validation

data class TransitionValidationResult(
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val isValid: Boolean get() = errors.isEmpty()
    constructor(error: String) : this(listOf(error))
    operator fun plus(other: TransitionValidationResult) =
        TransitionValidationResult(errors + other.errors, warnings + other.warnings)
    companion object { val OK = TransitionValidationResult() }
}
