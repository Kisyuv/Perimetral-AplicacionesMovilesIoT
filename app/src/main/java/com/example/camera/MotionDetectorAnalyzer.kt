package com.example.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

class MotionDetectorAnalyzer(
    private val onMotionDetected: () -> Unit,
    private val onMotionProgress: (Float) -> Unit, // 0.0f a 1.0f para feedback visual
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile var isEnabled: Boolean = true
    @Volatile var isAlarmActive: Boolean = false
    @Volatile var sensitivity: Long = 5L // 1 a 10
    @Volatile var confirmationSeconds: Long = 2L // segundos requeridos

    private var previousFrameBuffer: ByteArray? = null
    private var calibrationEndTime: Long = 0L
    private var motionStartTimestamp: Long = 0L
    private var lastAlarmDismissedTime: Long = 0L
    private var wasAlarmActive: Boolean = false

    fun startCalibration(seconds: Int = 3) {
        calibrationEndTime = System.currentTimeMillis() + (seconds * 1000L)
        previousFrameBuffer = null
        motionStartTimestamp = 0L
    }

    fun setAlarmState(alarm: Boolean) {
        if (wasAlarmActive && !alarm) {
            // Se silenció la alarma: pausar detección 5 segundos para que la luz se estabilice
            lastAlarmDismissedTime = System.currentTimeMillis() + 5000L
        }
        wasAlarmActive = alarm
        isAlarmActive = alarm
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()

        // 1. Desactivado manualmente o durante alarma (el estroboscopio causa falsos positivos)
        if (!isEnabled || isAlarmActive || now < lastAlarmDismissedTime) {
            image.close()
            onMotionProgress(0f)
            return
        }

        // 2. Calibración inicial (3 segundos al armar)
        if (now < calibrationEndTime) {
            // Almacenar fondo durante calibración
            storeBackground(image)
            image.close()
            onMotionProgress(0f)
            return
        }

        try {
            val yPlane = image.planes[0]
            val buffer: ByteBuffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride

            // Submuestreo eficiente (paso de 4 píxeles)
            val step = 4
            val sampleCountX = width / step
            val sampleCountY = height / step
            val totalSamples = sampleCountX * sampleCountY
            val currentSamples = ByteArray(totalSamples)

            var sampleIdx = 0
            for (row in 0 until height step step) {
                val rowStart = row * rowStride
                for (col in 0 until width step step) {
                    val pos = rowStart + (col * pixelStride)
                    if (pos < buffer.limit() && sampleIdx < totalSamples) {
                        currentSamples[sampleIdx++] = buffer.get(pos)
                    }
                }
            }

            val prev = previousFrameBuffer
            if (prev == null || prev.size != totalSamples) {
                previousFrameBuffer = currentSamples
                image.close()
                return
            }

            // Calcular diferencia de fotogramas
            var changedPixels = 0
            val pixelDiffThreshold = 25 // Umbral de diferencia de luminosidad por píxel

            for (i in 0 until totalSamples) {
                val diff = abs((currentSamples[i].toInt() and 0xFF) - (prev[i].toInt() and 0xFF))
                if (diff > pixelDiffThreshold) {
                    changedPixels++
                }
            }

            previousFrameBuffer = currentSamples

            val changePercentage = (changedPixels.toFloat() / totalSamples.toFloat()) * 100f

            // Umbral según sensibilidad: 1 (poco sensible, necesita 15% cambio) a 10 (muy sensible, necesita 1.5% cambio)
            val requiredThreshold = (11 - sensitivity.coerceIn(1, 10)) * 1.5f

            if (changePercentage >= requiredThreshold) {
                if (motionStartTimestamp == 0L) {
                    motionStartTimestamp = now
                }
                val durationMs = now - motionStartTimestamp
                val targetDurationMs = confirmationSeconds * 1000L
                val progress = (durationMs.toFloat() / targetDurationMs.toFloat()).coerceIn(0f, 1f)
                onMotionProgress(progress)

                // Si supera el umbral durante el tiempo de confirmación continuo
                if (durationMs >= targetDurationMs) {
                    motionStartTimestamp = 0L
                    onMotionDetected()
                }
            } else {
                // Si decae el movimiento, resetear el tiempo de confirmación
                motionStartTimestamp = 0L
                onMotionProgress(0f)
            }
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Error en análisis de fotogramas de cámara")
        } finally {
            image.close()
        }
    }

    private fun storeBackground(image: ImageProxy) {
        try {
            val yPlane = image.planes[0]
            val buffer = yPlane.buffer
            val width = image.width
            val height = image.height
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            val step = 4
            val totalSamples = (width / step) * (height / step)
            val samples = ByteArray(totalSamples)
            var sampleIdx = 0

            for (row in 0 until height step step) {
                val rowStart = row * rowStride
                for (col in 0 until width step step) {
                    val pos = rowStart + (col * pixelStride)
                    if (pos < buffer.limit() && sampleIdx < totalSamples) {
                        samples[sampleIdx++] = buffer.get(pos)
                    }
                }
            }
            previousFrameBuffer = samples
        } catch (_: Exception) {}
    }
}
