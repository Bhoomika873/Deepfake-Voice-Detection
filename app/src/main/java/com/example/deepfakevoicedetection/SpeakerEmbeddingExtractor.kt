package com.example.deepfakevoicedetection

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

class SpeakerEmbeddingExtractor(context: Context) {

    companion object {
        private const val MODEL_FILE = "model.onnx"
        private const val FEATURE_DIM = 80
    }

    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets
            .open(MODEL_FILE)
            .use { it.readBytes() }

        session = environment.createSession(
            modelBytes,
            OrtSession.SessionOptions()
        )
    }

    fun getEmbedding(
        features: Array<FloatArray>
    ): FloatArray {

        require(features.isNotEmpty()) {
            "Features are empty"
        }

        require(
            features.all { it.size == FEATURE_DIM }
        ) {
            "Expected 80-dimensional fbank features"
        }

        val numFrames = features.size

        val flattened =
            FloatArray(numFrames * FEATURE_DIM)

        var index = 0

        for (frame in features) {
            for (value in frame) {
                flattened[index++] = value
            }
        }

        val inputName =
            session.inputNames.iterator().next()

        val tensor = OnnxTensor.createTensor(
            environment,
            FloatBuffer.wrap(flattened),
            longArrayOf(
                1,
                numFrames.toLong(),
                FEATURE_DIM.toLong()
            )
        )

        tensor.use {

            val outputs =
                session.run(
                    mapOf(
                        inputName to tensor
                    )
                )

            outputs.use {

                val output =
                    outputs[0].value

                val embedding =
                    when (output) {

                        is Array<*> -> {

                            val first =
                                output[0]

                            when (first) {

                                is FloatArray ->
                                    first

                                else ->
                                    throw IllegalStateException(
                                        "Unexpected embedding output"
                                    )
                            }
                        }

                        is FloatArray ->
                            output

                        else ->
                            throw IllegalStateException(
                                "Unexpected ONNX output type"
                            )
                    }

                require(embedding.size == 192) {
                    "Expected 192-D embedding, got ${embedding.size}"
                }

                return embedding
            }
        }
    }

    fun close() {
        session.close()
    }
}