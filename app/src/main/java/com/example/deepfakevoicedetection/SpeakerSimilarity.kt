package com.example.deepfakevoicedetection

import kotlin.math.sqrt

object SpeakerSimilarity {

    fun cosineSimilarity(
        first: FloatArray,
        second: FloatArray
    ): Float {

        require(first.size == second.size) {
            "Embeddings must have the same size"
        }

        var dotProduct = 0.0
        var firstMagnitude = 0.0
        var secondMagnitude = 0.0

        for (i in first.indices) {

            val a = first[i].toDouble()
            val b = second[i].toDouble()

            dotProduct += a * b
            firstMagnitude += a * a
            secondMagnitude += b * b
        }

        val denominator =
            sqrt(firstMagnitude) *
                    sqrt(secondMagnitude)

        if (denominator == 0.0) {
            return 0f
        }

        return (dotProduct / denominator).toFloat()
    }
}