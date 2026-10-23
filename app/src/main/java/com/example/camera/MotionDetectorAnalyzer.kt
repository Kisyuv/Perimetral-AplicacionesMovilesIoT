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
    @Volatile var isDeviceMoving: Boolean = false // Actualizado por acelerómetro
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

        // 2. Si el dispositivo en sí se está moviendo físicamente (detectado por acelerómetro),
        // descartar la detección para evitar falsos positivos al manipular o mover el móvil
        if (isDeviceMoving) {
            motionStartTimestamp = 0L
            onMotionProgress(0f)
            image.close()
            return
        }

        // 3. Calibración inicial (3 segundos al armar)
        if (now < calibrationEndTime) {
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
            val sampleCols = width / step
            val sampleRows = height / step
            val totalSamples = sampleCols * sampleRows
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

            // Análisis espacial en cuadrícula 4x4 (16 bloques)
            // Esto permite diferenciar movimiento del teléfono (desplazamiento global uniforme)
            // de un objeto o mano moviéndose frente a la cámara (variación localizada)
            val gridCols = 4
            val gridRows = 4
            val blockWidth = sampleCols / gridCols
            val blockHeight = sampleRows / gridRows
            val pixelDiffThreshold = 25

            var totalChangedPixels = 0
            val activeBlocks = BooleanArray(gridCols * gridRows)

            for (gy in 0 until gridRows) {
                for (gx in 0 until gridCols) {
                    var blockChangedPixels = 0
                    val blockTotalPixels = blockWidth * blockHeight

                    for (by in 0 until blockHeight) {
                        val sy = gy * blockHeight + by
                        for (bx in 0 until blockWidth) {
                            val sx = gx * blockWidth + bx
                            val idx = sy * sampleCols + sx
                            if (idx < totalSamples) {
                                val diff = abs((currentSamples[idx].toInt() and 0xFF) - (prev[idx].toInt() and 0xFF))
                                if (diff > pixelDiffThreshold) {
                                    blockChangedPixels++
                                    totalChangedPixels++
                                }
                            }
                        }
                    }

                    // Si en este bloque más del 18% de píxeles cambiaron, marcar bloque activo
                    if ((blockChangedPixels.toFloat() / blockTotalPixels.toFloat()) >= 0.18f) {
                        activeBlocks[gy * gridCols + gx] = true
                    }
                }
            }

            previousFrameBuffer = currentSamples

            val activeBlocksCount = activeBlocks.count { it }
            val changePercentage = (totalChangedPixels.toFloat() / totalSamples.toFloat()) * 100f

            // FILTRO DE INMUNIDAD A MOVIMIENTO DEL MÓVIL:
            // - Si casi todos los bloques (>= 13 de 16) cambian a la vez, o el cambio global supera el 60%,
            //   significa que se movió todo el teléfono (paneo de escena o cambio brusco de luz ambiental).
            // - Un objeto real (mano, intruso) activa entre 2 y 10 bloques localizados.
            val isGlobalShift = activeBlocksCount >= 13 || changePercentage > 60f

            // Umbral de sensibilidad (1 a 10)
            val requiredThreshold = (11 - sensitivity.coerceIn(1, 10)) * 1.5f
            val hasValidLocalizedMotion = !isGlobalShift &&
                    activeBlocksCount in 2..10 &&
                    changePercentage >= requiredThreshold

            if (hasValidLocalizedMotion) {
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
                // Si decae o es movimiento global del teléfono, resetear confirmación
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
