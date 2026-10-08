package com.example.aura.core.memory

import java.util.concurrent.ConcurrentHashMap

/**
 * Pure local-first vector memory store using exact on-device cosine similarity.
 * Operates without external cloud vector databases, keeping all long-term memories private.
 */
class LocalCosineVectorStore : VectorMemoryStore {

    private data class IndexedVectorEntry(
        val id: String,
        val key: String,
        val content: String,
        val category: String,
        val vector: EmbeddingVector
    )

    private val index = ConcurrentHashMap<String, IndexedVectorEntry>()

    override val indexedCount: Int
        get() = index.size

    override suspend fun index(
        id: String,
        key: String,
        content: String,
        category: String,
        vector: EmbeddingVector
    ) {
        index[id] = IndexedVectorEntry(
            id = id,
            key = key,
            content = content,
            category = category,
            vector = vector
        )
    }

    override suspend fun search(
        queryVector: EmbeddingVector,
        topK: Int,
        minScore: Float
    ): List<ScoredMemoryMatch> {
        return index.values
            .map { entry ->
                val score = entry.vector.cosineSimilarity(queryVector)
                ScoredMemoryMatch(
                    memoryId = entry.id,
                    key = entry.key,
                    content = entry.content,
                    category = entry.category,
                    similarityScore = score
                )
            }
            .filter { it.similarityScore >= minScore }
            .sortedByDescending { it.similarityScore }
            .take(topK)
    }

    override suspend fun remove(id: String) {
        index.remove(id)
    }

    override suspend fun clear() {
        index.clear()
    }
}
