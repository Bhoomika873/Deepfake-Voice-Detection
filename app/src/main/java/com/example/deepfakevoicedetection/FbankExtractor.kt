package com.example.deepfakevoicedetection

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

class FbankExtractor {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val NUM_MEL_BINS = 80

        private const val FRAME_LENGTH_MS = 25
        private const val FRAME_SHIFT_MS = 10

        private const val FRAME_LENGTH =
            SAMPLE_RATE * FRAME_LENGTH_MS / 1000

        private const val FRAME_SHIFT =
            SAMPLE_RATE * FRAME_SHIFT_MS / 1000

        private const val NUM_FFT_BINS = 512

        private const val LOW_FREQ = 20.0
        private const val HIGH_FREQ = 8000.0
    }

    fun extract(samples: FloatArray): Array<FloatArray> {

        require(samples.isNotEmpty()) {
            "Audio samples are empty"
        }

        val numFrames =
            if (samples.size <= FRAME_LENGTH) {
                1
            } else {
                1 + (samples.size - FRAME_LENGTH) / FRAME_SHIFT
            }

        val melFilters = createMelFilterBank()

        val output = Array(numFrames) {
            FloatArray(NUM_MEL_BINS)
        }

        for (frameIndex in 0 until numFrames) {

            val start = frameIndex * FRAME_SHIFT

            val frame = FloatArray(FRAME_LENGTH)

            for (i in 0 until FRAME_LENGTH) {

                val index = start + i

                val sample =
                    if (index < samples.size) {
                        samples[index]
                    } else {
                        0f
                    }

                // Hamming window
                val window =
                    0.54 -
                            0.46 *
                            cos(
                                2.0 * PI * i /
                                        (FRAME_LENGTH - 1)
                            )

                frame[i] =
                    (sample * window).toFloat()
            }

            val powerSpectrum =
                computePowerSpectrum(frame)

            for (melIndex in 0 until NUM_MEL_BINS) {

                var energy = 0.0

                val filter =
                    melFilters[melIndex]

                for (bin in filter.indices) {

                    val filterValue =
                        filter[bin]

                    energy +=
                        powerSpectrum[bin] *
                                filterValue
                }

                // Kaldi-style log energy floor
                energy = max(
                    energy,
                    1.0e-10
                )

                output[frameIndex][melIndex] =
                    ln(energy).toFloat()
            }
        }

        return output
    }

    private fun computePowerSpectrum(
        frame: FloatArray
    ): DoubleArray {

        val padded =
            DoubleArray(NUM_FFT_BINS)

        for (i in frame.indices) {
            padded[i] = frame[i].toDouble()
        }

        val power =
            DoubleArray(NUM_FFT_BINS / 2 + 1)

        for (k in power.indices) {

            var real = 0.0
            var imag = 0.0

            for (n in frame.indices) {

                val angle =
                    -2.0 *
                            PI *
                            k *
                            n /
                            NUM_FFT_BINS

                val value =
                    padded[n]

                real +=
                    value *
                            cos(angle)

                imag +=
                    value *
                            sin(angle)
            }

            power[k] =
                (
                        real * real +
                                imag * imag
                        ) / NUM_FFT_BINS
        }

        return power
    }

    private fun createMelFilterBank():
            Array<DoubleArray> {

        val numBins =
            NUM_FFT_BINS / 2 + 1

        val filters =
            Array(NUM_MEL_BINS) {
                DoubleArray(numBins)
            }

        val lowMel =
            hzToMel(LOW_FREQ)

        val highMel =
            hzToMel(HIGH_FREQ)

        val melPoints =
            DoubleArray(NUM_MEL_BINS + 2)

        for (i in melPoints.indices) {

            melPoints[i] =
                lowMel +
                        (highMel - lowMel) *
                        i /
                        (NUM_MEL_BINS + 1)
        }

        val hzPoints =
            melPoints.map {
                melToHz(it)
            }

        val binPoints =
            hzPoints.map { frequency ->

                (
                        (NUM_FFT_BINS + 1) *
                                frequency /
                                SAMPLE_RATE
                        ).toInt()
            }

        for (m in 1..NUM_MEL_BINS) {

            val left =
                binPoints[m - 1]

            val center =
                binPoints[m]

            val right =
                binPoints[m + 1]

            for (k in left until center) {

                if (k in 0 until numBins &&
                    center > left
                ) {

                    filters[m - 1][k] =
                        (k - left).toDouble() /
                                (center - left)
                }
            }

            for (k in center until right) {

                if (k in 0 until numBins &&
                    right > center
                ) {

                    filters[m - 1][k] =
                        (right - k).toDouble() /
                                (right - center)
                }
            }
        }

        return filters
    }

    private fun hzToMel(
        hz: Double
    ): Double {

        return 1127.0 *
                ln(1.0 + hz / 700.0)
    }

    private fun melToHz(
        mel: Double
    ): Double {

        return 700.0 *
                (
                        kotlin.math.exp(
                            mel / 1127.0
                        ) - 1.0
                        )
    }
}