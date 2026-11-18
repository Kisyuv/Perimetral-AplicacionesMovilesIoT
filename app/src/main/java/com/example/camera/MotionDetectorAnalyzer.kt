package com.example.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs

/**
 * Detector de movimiento por cámara.
 *
 * - Un bloque cuenta como movimiento solo si difiere del fondo aprendido Y además cambió respecto
 *   al fotograma anterior: algo distinto a la escena y que se está moviendo. Al retirar la mano
 *   deja de haber cambio entre fotogramas y la barra deja de subir.
 * - El fondo solo se actualiza (rápido) en las zonas sin movimiento; donde hay un objeto se adapta
 *   muy lento, así no "absorbe" la mano y no quedan fantasmas al retirarla.
 * - Compensa cambios globales de brillo (autoexposición) restando la diferencia de luminancia media.
 * - La confirmación es un acumulador: sube mientras hay movimiento y baja a la misma velocidad
 *   cuando falta, de modo que una pausa breve no reinicia la barra pero retirarse sí la vacía.
 * - Si cambia casi toda la imagen (teléfono movido o cambio brusco de luz) se reaprende el fondo.
 */
class MotionDetectorAnalyzer(
    private val onMotionDetected: () -> Unit,
    private val onMotionProgress: (Float) -> Unit, // 0.0f a 1.0f para feedback visual
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile var isEnabled: Boolean = true
    @Volatile var isAlarmActive: Boolean = false
    @Volatile var isDeviceMoving: Boolean = false // Actualizado por acelerómetro
    @Volatile var sensitivity: Long = 5L // 1 (baja) o 5 (normal)
    @Volatile var confirmationSeconds: Long = 2L // segundos requeridos

    private companion object {
        const val GRID_COLS = 8
        const val GRID_ROWS = 6
        const val SAMPLE_STEP = 4
        const val MIN_FRAME_INTERVAL_MS = 66L // ~15 fps de análisis
        const val BACKGROUND_ALPHA = 0.05f // adaptación del fondo donde no hay movimiento
        const val FOREGROUND_ALPHA = 0.002f // adaptación donde hay un objeto (muy lenta)
        const val GLOBAL_SHIFT_BLOCK_RATIO = 0.85f
        const val DECAY_FACTOR = 1.0f // la barra baja a la misma velocidad que sube
        const val COOLDOWN_MS = 4000L
    }

    private var background: FloatArray? = null
    private var previous: FloatArray? = null
    private var calibrationEndTime: Long = 0L
    private var lastAlarmDismissedTime: Long = 0L
    private var wasAlarmActive: Boolean = false
    private var lastFrameTime: Long = 0L
    private var accumulatedMs: Float = 0f
    private var cooldownUntil: Long = 0L

    fun startCalibration(seconds: Int = 3) {
        calibrationEndTime = System.currentTimeMillis() + (seconds * 1000L)
        background = null
        previous = null
        accumulatedMs = 0f
    }

    fun setAlarmState(alarm: Boolean) {
        if (wasAlarmActive && !alarm) {
            // Se silenció la alarma: pausar detección 5 segundos para que la luz se estabilice
            lastAlarmDismissedTime = System.currentTimeMillis() + 5000L
            background = null
        }
        wasAlarmActive = alarm
        isAlarmActive = alarm
    }

    private fun resetProgress() {
        accumulatedMs = 0f
        onMotionProgress(0f)
    }

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        try {
            // 1. Desactivado, durante alarma (el estroboscopio causa falsos positivos) o tras un disparo
            if (!isEnabled || isAlarmActive || now < lastAlarmDismissedTime || now < cooldownUntil) {
                resetProgress()
                return
            }

            // 2. El propio teléfono se está moviendo: descartar y reaprender el fondo
            if (isDeviceMoving) {
                background = null
                resetProgress()
                return
            }

            if (now - lastFrameTime < MIN_FRAME_INTERVAL_MS) return
            val dt = if (lastFrameTime == 0L) 0f else (now - lastFrameTime).coerceAtMost(500L).toFloat()
            lastFrameTime = now

            val current = sample(image)
            val bg = background
            val prev = previous
            previous = current

            // 3. Calibración inicial o fondo sin aprender
            if (bg == null || bg.size != current.size || prev == null || prev.size != current.size || now < calibrationEndTime) {
                background = if (bg == null || bg.size != current.size) current.copyOf() else blend(bg, current, 0.3f)
                resetProgress()
                return
            }

            val cols = (image.width / SAMPLE_STEP)
            val rows = current.size / cols
            val blockW = (cols / GRID_COLS).coerceAtLeast(1)
            val blockH = (rows / GRID_ROWS).coerceAtLeast(1)

            // Compensación de exposición: diferencia de brillo medio entre fotograma y fondo
            val meanShift = current.average().toFloat() - bg.average().toFloat()

            // Dos niveles: 1 = baja (solo cambios grandes), 5 = normal
            val low = sensitivity <= 1L
            val pixelThreshold = if (low) 34f else 26f
            val blockRatio = if (low) 0.22f else 0.14f
            val prevMeanShift = current.average().toFloat() - prev.average().toFloat()

            val foreground = BooleanArray(current.size)
            var foregroundBlocks = 0
            var activeBlocks = 0
            for (gy in 0 until GRID_ROWS) {
                for (gx in 0 until GRID_COLS) {
                    var changedVsBackground = 0
                    var changedVsPrevious = 0
                    var total = 0
                    for (y in gy * blockH until minOf((gy + 1) * blockH, rows)) {
                        val rowOffset = y * cols
                        for (x in gx * blockW until minOf((gx + 1) * blockW, cols)) {
                            val i = rowOffset + x
                            if (abs(current[i] - bg[i] - meanShift) > pixelThreshold) {
                                changedVsBackground++
                                foreground[i] = true
                            }
                            if (abs(current[i] - prev[i] - prevMeanShift) > pixelThreshold) changedVsPrevious++
                            total++
                        }
                    }
                    if (total == 0) continue
                    val isForeground = changedVsBackground.toFloat() / total >= blockRatio
                    if (isForeground) foregroundBlocks++
                    // Movimiento real: distinto al fondo y además cambiando entre fotogramas
                    if (isForeground && changedVsPrevious.toFloat() / total >= blockRatio / 2) activeBlocks++
                }
            }

            val totalBlocks = GRID_COLS * GRID_ROWS
            if (foregroundBlocks >= totalBlocks * GLOBAL_SHIFT_BLOCK_RATIO) {
                // Cambio de escena completo: no es un intruso, reaprender el fondo
                background = current.copyOf()
                resetProgress()
                return
            }

            // Actualización selectiva: rápida en la escena, casi nula donde hay un objeto
            background = FloatArray(bg.size) { i ->
                val alpha = if (foreground[i]) FOREGROUND_ALPHA else BACKGROUND_ALPHA
                bg[i] + (current[i] - bg[i]) * alpha
            }

            val targetMs = (confirmationSeconds.coerceAtLeast(1) * 1000L).toFloat()
            accumulatedMs = if (activeBlocks >= 1) {
                accumulatedMs + dt
            } else {
                (accumulatedMs - dt * DECAY_FACTOR).coerceAtLeast(0f)
            }
            onMotionProgress((accumulatedMs / targetMs).coerceIn(0f, 1f))

            if (accumulatedMs >= targetMs) {
                accumulatedMs = 0f
                cooldownUntil = now + COOLDOWN_MS
                background = null
                previous = null
                onMotionProgress(1f)
                onMotionDetected()
            }
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Error en análisis de fotogramas de cámara")
        } finally {
            image.close()
        }
    }

    /** Luminancia (plano Y) submuestreada cada SAMPLE_STEP píxeles. */
    private fun sample(image: ImageProxy): FloatArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val cols = image.width / SAMPLE_STEP
        val rows = image.height / SAMPLE_STEP
        val out = FloatArray(cols * rows)
        val limit = buffer.limit()
        for (r in 0 until rows) {
            val rowStart = r * SAMPLE_STEP * rowStride
            for (c in 0 until cols) {
                val pos = rowStart + c * SAMPLE_STEP * pixelStride
                out[r * cols + c] = if (pos < limit) (buffer.get(pos).toInt() and 0xFF).toFloat() else 0f
            }
        }
        return out
    }

    private fun blend(bg: FloatArray, current: FloatArray, alpha: Float): FloatArray {
        val out = FloatArray(bg.size)
        for (i in bg.indices) out[i] = bg[i] + (current[i] - bg[i]) * alpha
        return out
    }
}
