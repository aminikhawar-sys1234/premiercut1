package com.ahstudio.animation.expression

import com.ahstudio.animation.keyframes.EvaluatedValue
import com.ahstudio.animation.properties.BindingKey

/**
 * Expression-READY architecture. No arbitrary scripting or reflective exec calls.
 * Host supplies a safe evaluator if needed.
 */
interface ExpressionContext {
    fun timeMs(): Long
    fun propertyValue(key: BindingKey): Double
    fun markerTime(name: String): Long?
    fun seededRandom(seed: Long, index: Long): Double
}

fun interface ExpressionEvaluator {
    fun evaluate(expressionId: String, ctx: ExpressionContext): Double
}

class ExpressionSource(
    private val expressionId: String,
    private val evaluator: ExpressionEvaluator,
    private val ctx: ExpressionContext
) : () -> EvaluatedValue? {
    override fun invoke(): EvaluatedValue? =
        EvaluatedValue.FloatV(evaluator.evaluate(expressionId, ctx), 0.0)
}
