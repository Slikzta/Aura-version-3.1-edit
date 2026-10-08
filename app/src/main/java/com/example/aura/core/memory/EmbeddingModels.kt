package com.example.aura.core.memory

import kotlin.math.sqrt

/**
 * Metric used to evaluate similarity between two high-dimensional embedding vectors.
 */
enum class SimilarityMetric {
    COSINE,
    DOT_PRODUCT,
    EUCLIDEAN
}

/**
 * High-dimensional dense vector representing semantic text embeddings.
 */
data class EmbeddingVector(
    val values: FloatArray
) {
    val dimensions: Int get() = values.size

    fun cosineSimilarity(other: EmbeddingVector): Float {
        if (values.size != other.values.size || values.isEmpty()) return 0f

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in values.indices) {
            val a = values[i].toDouble()
            val b = other.values[i].toDouble()
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0.0) {
            (dotProduct / denominator).toFloat()
        } else {
            0f
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingVector
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()
}

/**
 * Result of a semantic nearest-neighbor memory search.
 */
data class ScoredMemoryMatch(
    val memoryId: String,
    val key: String,
    val content: String,
    val category: String,
    val similarityScore: Float
)
