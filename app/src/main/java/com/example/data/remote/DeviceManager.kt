package com.example.data.remote

import android.content.Context
import android.os.Build
import com.example.network.DEFAULT_MQTT_CHANNEL
import com.example.network.NodeMode
import java.util.UUID

class DeviceManager(private val context: Context) {

    companion object {
        const val KEY_ALARM_NOTIFICATIONS = "alarm_notifications_enabled"
    }

    private val prefs = context.getSharedPreferences("perimetral_device_prefs", Context.MODE_PRIVATE)

    fun getDeviceId(): String {
        var deviceId = prefs.getString("device_unique_id", null)
        if (deviceId == null) {
            deviceId = "dev_" + UUID.randomUUID().toString().substring(0, 12)
            prefs.edit().putString("device_unique_id", deviceId).apply()
        }
        return deviceId
    }

    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model"
        }
    }

    fun getFcmToken(): String {
        // Token simulado / provisionado para Firebase Cloud Messaging
        return prefs.getString("fcm_token", "fcm_token_${getDeviceId()}") ?: "fcm_token_default"
    }

    /** Interruptor de avisos de alarma (pantalla Seguridad). Activado por defecto. */
    fun areAlarmNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_ALARM_NOTIFICATIONS, true)

    fun setAlarmNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALARM_NOTIFICATIONS, enabled).apply()
    }

    /** Quién hace de nodo de sensores: este teléfono o un ESP32 por MQTT. */
    fun getNodeMode(): NodeMode =
        NodeMode.entries.firstOrNull { it.name == prefs.getString("node_mode", null) } ?: NodeMode.TELEFONO

    fun setNodeMode(mode: NodeMode) {
        prefs.edit().putString("node_mode", mode.name).apply()
    }

    fun getMqttChannel(): String = prefs.getString("mqtt_channel", null) ?: DEFAULT_MQTT_CHANNEL

    fun setMqttChannel(channel: String) {
        prefs.edit().putString("mqtt_channel", channel).apply()
    }

    fun saveCachedRole(role: String?) {
        prefs.edit().putString("cached_role", role).apply()
    }

    fun getCachedRole(): String? {
        return prefs.getString("cached_role", null)
    }
}
