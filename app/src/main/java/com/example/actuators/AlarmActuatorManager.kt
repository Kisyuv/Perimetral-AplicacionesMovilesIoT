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

    private fun findCameraWithFlash() {
        try {
            for (id in cameraManager.cameraIdList) {
                val chars = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (flashAvailable && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    rearCameraIdWithFlash = id
                    break
                }
            }
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
        if (!enabled || rearCameraIdWithFlash == null) {
            stopTorchStrobe()
            return
        }

        if (torchJob?.isActive == true) return

        torchJob = scope.launch {
            var torchOn = false
            val cameraId = rearCameraIdWithFlash ?: return@launch
            try {
                // Máximo 3 Hz por fotosensibilidad (aprox 333ms período, 166ms encendido / 166ms apagado)
                while (isActive) {
                    torchOn = !torchOn
                    cameraManager.setTorchMode(cameraId, torchOn)
                    delay(166L)
                }
            } catch (_: Exception) {
            } finally {
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (_: Exception) {}
            }
        }
    }

    fun stopTorchStrobe() {
        torchJob?.cancel()
        torchJob = null
        val cameraId = rearCameraIdWithFlash ?: return
        try {
            cameraManager.setTorchMode(cameraId, false)
        } catch (_: Exception) {}
    }

    fun stopAll() {
        stopSiren()
        stopTorchStrobe()
    }
}
