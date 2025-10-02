package com.yuyan.imemodule.libs.expression.tokenizer

import com.yuyan.imemodule.libs.expression.operator.Operator

class OperatorToken(op: Operator?) : Token(TOKEN_OPERATOR) {

    @JvmField
    val operator: Operator

    init {
        requireNotNull(op) { "Operator is unknown for token." }
        operator = op
    }
}
