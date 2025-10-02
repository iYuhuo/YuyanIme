package com.yuyan.imemodule.libs.expression.operator

abstract class Operator ( val symbol: String, val numOperands: Int, val isLeftAssociative: Boolean, val precedence: Int) {

    
    abstract fun apply(vararg args: Double): Double

    companion object {
        
        const val PRECEDENCE_ADDITION = 500

        
        const val PRECEDENCE_MULTIPLICATION = 1000

        
        const val PRECEDENCE_DIVISION = PRECEDENCE_MULTIPLICATION

        
        const val PRECEDENCE_MODULO = PRECEDENCE_DIVISION

        
        const val PRECEDENCE_POWER = 10000

        
        const val PRECEDENCE_UNARY_MINUS = 5000

        
        const val PRECEDENCE_UNARY_PLUS = PRECEDENCE_UNARY_MINUS

        
        val ALLOWED_OPERATOR_CHARS = charArrayOf(
            '+', '-', '*', '/', '%', '^', '!', '#', '§',
            '$', '&', ';', ':', '~', '<', '>', '|', '=', '÷', '√', '∛', '⌈', '⌊'
        )

        
        fun isAllowedOperatorChar(ch: Char): Boolean {
            for (allowed in ALLOWED_OPERATOR_CHARS) {
                if (ch == allowed) {
                    return true
                }
            }
            return false
        }
    }
}
