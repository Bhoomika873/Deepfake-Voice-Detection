package com.example.deepfakevoicedetection

import android.content.Context

class SpeakerRecognitionEngine(
    context: Context
) {

    private val fbankExtractor =
        FbankExtractor()

    private val embeddingExtractor =
        SpeakerEmbeddingExtractor(context)

    private val storage =
        SpeakerStorage(context)

    fun createEmbedding(
        audio: FloatArray
    ): FloatArray {

        val features =
            fbankExtractor.extract(audio)

        return embeddingExtractor.getEmbedding(
            features
        )
    }

    fun registerSpeaker(
        name: String,
        audio: FloatArray
    ) {
        require(name.isNotBlank()) {
            "Speaker name cannot be empty"
        }

        require(audio.isNotEmpty()) {
            "Audio cannot be empty"
        }

        val embedding = createEmbedding(audio)

        storage.saveSpeaker(
            name = name.trim(),
            embedding = embedding,
            audio = audio
        )
    }

    fun identifySpeaker(
        audio: FloatArray
    ): SpeakerMatch? {

        val storedSpeakers =
            storage.getSpeakers()

        if (storedSpeakers.isEmpty()) {
            return null
        }

        val newEmbedding =
            createEmbedding(audio)

        var bestMatch:
                SpeakerMatch? = null

        for (speaker in storedSpeakers) {

            val similarity =
                SpeakerSimilarity.cosineSimilarity(
                    newEmbedding,
                    speaker.embedding
                )

            if (
                bestMatch == null ||
                similarity > bestMatch.similarity
            ) {

                bestMatch =
                    SpeakerMatch(
                        name = speaker.name,
                        similarity = similarity
                    )
            }
        }

        return bestMatch
    }

    fun getStoredSpeakers():
            List<StoredSpeaker> {

        return storage.getSpeakers()
    }

    fun deleteSpeaker(
        name: String
    ) {

        storage.deleteSpeaker(name)
    }

    fun close() {
        embeddingExtractor.close()
    }
}

data class SpeakerMatch(
    val name: String,
    val similarity: Float
)