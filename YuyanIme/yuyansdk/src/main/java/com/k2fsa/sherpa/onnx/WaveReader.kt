package com.k2fsa.sherpa.onnx

import android.content.res.AssetManager

data class WaveData(
    val samples: FloatArray,
    val sampleRate: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WaveData

        if (!samples.contentEquals(other.samples)) return false
        if (sampleRate != other.sampleRate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        return result
    }
}

class WaveReader {
    companion object {

        fun readWave(
            assetManager: AssetManager,
            filename: String,
        ): WaveData {
            return readWaveFromAsset(assetManager, filename).let {
                WaveData(it[0] as FloatArray, it[1] as Int)
            }
        }

        fun readWave(
            filename: String,
        ): WaveData {
            return readWaveFromFile(filename).let {
                WaveData(it[0] as FloatArray, it[1] as Int)
            }
        }

        external fun readWaveFromAsset(
            assetManager: AssetManager,
            filename: String,
        ): Array<Any>

        external fun readWaveFromFile(
            filename: String,
        ): Array<Any>

        init {
            System.loadLibrary("sherpa-onnx-jni")
        }
    }
}