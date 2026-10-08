package com.example.aura.core.memory

/**
 * Universal interface for generating text embedding vectors in Aura.
 * Allows switching between on-device embedding models (e.g., MiniLM, MobileBERT)
 * and remote embedding providers without changing memory storage logic.
 */
interface EmbeddingProvider {
    /** Expected vector dimensionality (e.g. 384, 768, 1536) */
    val dimensions: Int

    /** Provider name or model tag */
    val modelName: String

    /**
     * Generates a dense embedding vector for the provided text.
     */
    suspend fun generateEmbedding(text: String): EmbeddingVector

    /**
     * Generates embeddings for multiple texts in batch.
     */
    suspend fun generateBatchEmbeddings(texts: List<String>): List<EmbeddingVector> =
        texts.map { generateEmbedding(it) }
}

/**
 * Interface for local-first vector memory indexing and retrieval.
 */
interface VectorMemoryStore {
    /**
     * Indexes or updates a long-term memory entry with its embedding vector.
     */
    suspend fun index(
        id: String,
        key: String,
        content: String,
        category: String,
        vector: EmbeddingVector
    )

    /**
     * Searches the local index for memories semantically nearest to the query vector.
     */
    suspend fun search(
        queryVector: EmbeddingVector,
        topK: Int = 5,
        minScore: Float = 0.5f
    ): List<ScoredMemoryMatch>

    /**
     * Removes an item from the vector index.
     */
    suspend fun remove(id: String)

    /**
     * Clears all indexed vectors.
     */
    suspend fun clear()

    /**
     * Number of items currently indexed.
     */
    val indexedCount: Int
}
