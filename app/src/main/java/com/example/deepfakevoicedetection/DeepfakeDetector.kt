package com.example.deepfakevoicedetection

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.max

class DeepfakeDetector(context: Context) {

    companion object {
        private const val MODEL_FILE = "compare.onnx"
        private const val INPUT_LENGTH = 64600
    }

    private val environment =
        OrtEnvironment.getEnvironment()

    private val session: OrtSession

    init {

        val modelBytes =
            context.assets
                .open(MODEL_FILE)
                .use { it.readBytes() }

        session = environment.createSession(
            modelBytes,
            OrtSession.SessionOptions()
        )
    }

    fun detect(audio: ShortArray): DetectionResult {

        /*
         * AASIST-L expects:
         *
         * 16 kHz
         * Mono
         * Float32 waveform
         * 64600 samples
         */

        val input = FloatArray(INPUT_LENGTH)

        for (i in 0 until INPUT_LENGTH) {

            val sourceIndex =
                i % audio.size

            input[i] =
                audio[sourceIndex].toFloat() / 32768.0f
        }

        val inputName =
            session.inputNames
                .iterator()
                .next()

        val tensor =
            OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input),
                longArrayOf(
                    1,
                    INPUT_LENGTH.toLong()
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

                val rawOutput =
                    outputs[0].value

                val logits: FloatArray =
                    when (rawOutput) {

                        is Array<*> -> {

                            when (val first = rawOutput[0]) {

                                is FloatArray ->
                                    first

                                is DoubleArray ->
                                    first.map {
                                        it.toFloat()
                                    }.toFloatArray()

                                else ->
                                    throw IllegalStateException(
                                        "Unexpected output format"
                                    )
                            }
                        }

                        is FloatArray ->
                            rawOutput

                        is DoubleArray ->
                            rawOutput.map {
                                it.toFloat()
                            }.toFloatArray()

                        else ->
                            throw IllegalStateException(
                                "Unexpected output type"
                            )
                    }

                if (logits.size < 2) {
                    throw IllegalStateException(
                        "Invalid AASIST-L output"
                    )
                }

                /*
                 * AASIST-L:
                 *
                 * index 0 = spoof
                 * index 1 = bona fide
                 */

                val fakeLogit =
                    logits[0].toDouble()

                val realLogit =
                    logits[1].toDouble()

                val highest =
                    max(
                        fakeLogit,
                        realLogit
                    )

                val fakeExp =
                    exp(fakeLogit - highest)

                val realExp =
                    exp(realLogit - highest)

                val total =
                    fakeExp + realExp

                val fakeProbability =
                    fakeExp / total

                val realProbability =
                    realExp / total

                return if (
                    realProbability >= fakeProbability
                ) {

                    DetectionResult(
                        label = "REAL VOICE",
                        confidence = realProbability
                    )

                } else {

                    DetectionResult(
                        label = "DEEPFAKE VOICE",
                        confidence = fakeProbability
                    )
                }
            }
        }
    }

    fun close() {
        session.close()
    }
}