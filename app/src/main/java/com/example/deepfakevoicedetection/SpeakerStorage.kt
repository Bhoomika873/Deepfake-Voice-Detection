package com.example.deepfakevoicedetection

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SpeakerStorage(context: Context) {

    private val preferences =
        context.getSharedPreferences(
            "speaker_storage",
            Context.MODE_PRIVATE
        )

    companion object {
        private const val KEY_SPEAKERS = "speakers"
    }

    fun saveSpeaker(
        name: String,
        embedding: FloatArray,
        audio: FloatArray
    ) {
        val speakers = getSpeakers().toMutableList()

        speakers.removeAll {
            it.name.equals(name, ignoreCase = true)
        }

        speakers.add(
            StoredSpeaker(
                name = name,
                embedding = embedding,
                audio = audio
            )
        )

        saveAll(speakers)
    }

    fun getSpeakers(): List<StoredSpeaker> {

        val jsonString =
            preferences.getString(
                KEY_SPEAKERS,
                null
            ) ?: return emptyList()

        return try {

            val jsonArray =
                JSONArray(jsonString)

            val speakers = mutableListOf<StoredSpeaker>()

            for (i in 0 until jsonArray.length()) {

                val speakerObject =
                    jsonArray.getJSONObject(i)

                val name =
                    speakerObject.getString("name")

                val embeddingJson =
                    speakerObject.getJSONArray("embedding")

                val embedding =
                    FloatArray(embeddingJson.length())

                for (j in embedding.indices) {
                    embedding[j] =
                        embeddingJson
                            .getDouble(j)
                            .toFloat()
                }

                val audioJson =
                    speakerObject.optJSONArray("audio")

                val audio =
                    if (audioJson != null) {

                        FloatArray(audioJson.length()) {
                                index ->
                            audioJson
                                .getDouble(index)
                                .toFloat()
                        }

                    } else {
                        FloatArray(0)
                    }

                speakers.add(
                    StoredSpeaker(
                        name = name,
                        embedding = embedding,
                        audio = audio
                    )
                )
            }

            speakers

        } catch (e: Exception) {

            emptyList()
        }
    }

    fun deleteSpeaker(name: String) {

        val speakers =
            getSpeakers()
                .filterNot {
                    it.name.equals(
                        name,
                        ignoreCase = true
                    )
                }

        saveAll(speakers)
    }

    private fun saveAll(
        speakers: List<StoredSpeaker>
    ) {

        val jsonArray = JSONArray()

        for (speaker in speakers) {

            val speakerObject =
                JSONObject()

            speakerObject.put(
                "name",
                speaker.name
            )

            val embeddingJson =
                JSONArray()

            for (value in speaker.embedding) {
                embeddingJson.put(value.toDouble())
            }

            speakerObject.put(
                "embedding",
                embeddingJson
            )

            val audioJson =
                JSONArray()

            for (value in speaker.audio) {
                audioJson.put(value.toDouble())
            }

            speakerObject.put(
                "audio",
                audioJson
            )

            jsonArray.put(
                speakerObject
            )
        }

        preferences
            .edit()
            .putString(
                KEY_SPEAKERS,
                jsonArray.toString()
            )
            .apply()
    }
}