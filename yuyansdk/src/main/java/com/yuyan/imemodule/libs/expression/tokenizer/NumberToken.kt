package com.yuyan.imemodule.libs.expression.tokenizer

class NumberToken ( @JvmField val value: Double) : Token(TOKEN_NUMBER) {
    internal constructor(expression: CharArray?, offset: Int, len: Int) : this(String(expression!!, offset, len).toDouble())
}
