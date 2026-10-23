package com.example.actuators

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class AlarmActuatorManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var sirenJob: Job? = null
    private var torchJob: Job? = null
    private var vibrationJob: Job? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val cameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var rearCameraIdWithFlash: String? = null

    init {
        findCameraWithFlash()
    }

    private var cameraControl: androidx.camera.core.CameraControl? = null

    fun setCameraControl(control: androidx.camera.core.CameraControl?) {
        cameraControl = control
    }

    @Volatile private var isStrobeRunning: Boolean = false

    private fun findCameraWithFlash() {
        try {
            var fallbackId: String? = null
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                if (flashAvailable) {
                    if (fallbackId == null) fallbackId = id
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        rearCameraIdWithFlash = id
                        return
                    }
                }
            }
            rearCameraIdWithFlash = fallbackId
        } catch (_: Exception) {}
    }

    fun startSiren() {
        if (sirenJob?.isActive == true) return

        sirenJob = scope.launch {
            val sampleRate = 22050
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()

            // Generador de tono modulado de sirena perimetral (700Hz a 1400Hz en barrido)
            val buffer = ShortArray(1024)
            var phase = 0.0
            var sweepPhase = 0.0

            try {
                while (isActive) {
                    val currentFreq = 700.0 + 700.0 * (0.5 + 0.5 * sin(sweepPhase))
                    sweepPhase += 2.0 * Math.PI * 2.0 / sampleRate // Modulación a 2 Hz

                    for (i in buffer.indices) {
                        buffer[i] = (sin(phase) * 30000.0).toInt().toShort()
                        phase += 2.0 * Math.PI * currentFreq / sampleRate
                        if (phase > 2.0 * Math.PI) phase -= 2.0 * Math.PI
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                }
            } finally {
                audioTrack.stop()
                audioTrack.release()
            }
        }

        startVibration()
    }

    /**
     * Emite un tono breve de prueba (600ms) para demostrar el funcionamiento de la sirena acústica
     */
    suspend fun playTestChirp(): Boolean {
        return try {
            val sampleRate = 22050
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack.play()

            val totalSamples = (sampleRate * 0.6).toInt() // 600 ms
            val buffer = ShortArray(512)
            var phase = 0.0
            var written = 0

            while (written < totalSamples) {
                val freq = 880.0 + (written.toDouble() / totalSamples) * 600.0 // 880Hz a 1480Hz
                for (i in buffer.indices) {
                    buffer[i] = (sin(phase) * 26000.0).toInt().toShort()
                    phase += 2.0 * Math.PI * freq / sampleRate
                }
                audioTrack.write(buffer, 0, buffer.size)
                written += buffer.size
            }

            audioTrack.stop()
            audioTrack.release()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopSiren() {
        sirenJob?.cancel()
        sirenJob = null
        stopVibration()
    }

    private fun startVibration() {
        if (vibrationJob?.isActive == true) return
        vibrationJob = scope.launch {
            val pattern = longArrayOf(0, 400, 200, 400)
            while (isActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, 0)
                }
                delay(2000L)
            }
        }
    }

    private fun stopVibration() {
        vibrationJob?.cancel()
        vibrationJob = null
        try {
            vibrator.cancel()
        } catch (_: Exception) {}
    }

    fun startTorchStrobe(enabled: Boolean = true) {
        if (!enabled) {
            stopTorchStrobe()
            return
        }

        if (isStrobeRunning && torchJob?.isActive == true) return

        if (rearCameraIdWithFlash == null) {
            findCameraWithFlash()
        }

        isStrobeRunning = true
        torchJob?.cancel()
        torchJob = scope.launch {
            var torchOn = false
            try {
                // Frecuencia segura a 3 Hz (166ms encendido / 166ms apagado)
                while (isActive && isStrobeRunning) {
                    torchOn = !torchOn
                    setTorch(torchOn)
                    delay(166L)
                }
            } catch (_: Exception) {
            } finally {
                isStrobeRunning = false
                setTorch(false)
            }
        }
    }

    /**
     * Enciende o apaga la linterna física usando CameraControl (si la cámara está activa)
     * o CameraManager (si la cámara está libre)
     */
    private fun setTorch(on: Boolean) {
        val ctrl = cameraControl
        if (ctrl != null) {
            try {
                ctrl.enableTorch(on)
                return
            } catch (_: Exception) {}
        }

        val cameraId = rearCameraIdWithFlash
        if (cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId, on)
            } catch (_: Exception) {}
        }
    }

    /**
     * Prueba interactiva que emite destellos en la linterna física (3 pulsos)
     */
    suspend fun demonstrateTorchPulse(pulses: Int = 3): Boolean {
        return try {
            for (i in 0 until pulses) {
                setTorch(true)
                delay(200L)
                setTorch(false)
                delay(200L)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stopTorchStrobe() {
        isStrobeRunning = false
        torchJob?.cancel()
        torchJob = null
        setTorch(false)
        // Asegurar que cualquier otra cámara con flash esté apagada
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                    try {
                        cameraManager.setTorchMode(id, false)
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    fun stopAll() {
        stopSiren()
        stopTorchStrobe()
    }
}
