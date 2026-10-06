package com.example.deepfakevoicedetection

data class StoredSpeaker(
    val name: String,
    val embedding: FloatArray,
    val audio: FloatArray
)