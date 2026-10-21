package com.example.data.remote

import android.content.Context
import android.os.Build
import java.util.UUID

class DeviceManager(private val context: Context) {

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

    fun saveCachedRole(role: String?) {
        prefs.edit().putString("cached_role", role).apply()
    }

    fun getCachedRole(): String? {
        return prefs.getString("cached_role", null)
    }
}
