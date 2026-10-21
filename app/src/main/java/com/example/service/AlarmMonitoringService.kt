package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.PerimetralApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AlarmMonitoringService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var isMonitoring = false
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val MONITORING_CHANNEL_ID = "perimetral_monitoring_channel"
        const val CRITICAL_ALARM_CHANNEL_ID = "perimetral_critical_alarms"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_MONITORING = "com.example.service.ACTION_START"
        const val ACTION_STOP_MONITORING = "com.example.service.ACTION_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "Perimetral:AlarmMonitoringWakeLock"
            ).apply {
                acquire(10 * 60 * 1000L) // 10 minutos con renovación
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_START_MONITORING -> startMonitoring()
                ACTION_STOP_MONITORING -> stopMonitoring()
                else -> startMonitoring()
            }
        } catch (_: Exception) {}
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        val notification = createForegroundNotification("Vigilancia perimetral en curso para bodega")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        } catch (_: Exception) {}

        val app = applicationContext as? PerimetralApplication ?: return
        val siteRepo = app.container.siteAlarmRepository
        val actuators = app.container.alarmActuatorManager
        val deviceRole = app.container.deviceManager.getCachedRole()

        serviceScope.launch {
            siteRepo.observeSiteState("bodega_central").collectLatest { state ->
                if (state.estado == "alarma") {
                    showCriticalAlarmNotification("¡INTRUSIÓN EN BODEGA!", "Se ha activado una alarma perimetral.")
                    if (deviceRole == "nodo" && state.sirenaActiva) {
                        actuators.startSiren()
                    }
                } else {
                    if (deviceRole == "nodo") {
                        actuators.stopAll()
                    }
                }

                // Evaluar auto-armado fuera de horario
                if (state.armadoAutomatico && state.estado == "desarmada") {
                    val inicio = state.horario["inicio"] as? String ?: "08:00"
                    val fin = state.horario["fin"] as? String ?: "19:00"
                    @Suppress("UNCHECKED_CAST")
                    val dias = state.horario["dias"] as? List<Long> ?: listOf(1L, 2L, 3L, 4L, 5L)

                    if (siteRepo.isOffHours(inicio, fin, dias)) {
                        siteRepo.armSystem("bodega_central", "auto_horario")
                    }
                }
            }
        }

        // Tarea periódica de comprobación de horario cada 60 segundos
        serviceScope.launch {
            while (isActive) {
                delay(60_000L)
                val state = siteRepo.observeSiteState("bodega_central")
                // Mantenimiento de wake lock activo
                wakeLock?.let {
                    if (!it.isHeld) it.acquire(10 * 60 * 1000L)
                }
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        val app = applicationContext as? PerimetralApplication
        app?.container?.alarmActuatorManager?.stopAll()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createForegroundNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, MONITORING_CHANNEL_ID)
            .setContentTitle("Perimetral - Central Bodega")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showCriticalAlarmNotification(title: String, body: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CRITICAL_ALARM_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(999, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitorChannel = NotificationChannel(
                MONITORING_CHANNEL_ID,
                "Servicio de Monitoreo Perimetral",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoreo en segundo plano de sensores y central de bodega"
            }
            manager.createNotificationChannel(monitorChannel)

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val criticalChannel = NotificationChannel(
                CRITICAL_ALARM_CHANNEL_ID,
                "Alarmas Críticas de Bodega",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Disparo inmediato de alarma con sirena y prioridad máxima"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(alarmSound, audioAttrs)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(criticalChannel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }
}
