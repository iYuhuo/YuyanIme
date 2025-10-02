package com.yuyan.imemodule.libs.expression.function

abstract class Function @JvmOverloads constructor(name: String, numArguments: Int = 1) {
    
    val name: String

    
    val numArguments: Int

    init {
        require(numArguments >= 0) { "The number of function arguments can not be less than 0 for '$name'" }
        if (!isValidFunctionName(name)) {
            throw IllegalArgumentException("The function name '$name' is invalid")
        }
        this.name = name
        this.numArguments = numArguments
    }

    
    abstract fun apply(vararg args: Double): Double

    companion object {

        fun isValidFunctionName(name: String?): Boolean {
            if (name == null) {
                return false
            }
            val size = name.length
            if (size == 0) {
                return false
            }
            for (i in 0 until size) {
                val c = name[i]
                if (Character.isLetter(c) || c == '_') {
                    continue
                } else if (Character.isDigit(c) && i > 0) {
                    continue
                }
                return false
            }
            return true
        }
    }
}
