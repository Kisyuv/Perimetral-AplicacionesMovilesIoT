package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

fun Context.hasPermissions(permissions: Array<String>): Boolean = permissions.all {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/**
 * Solicitud de permisos bajo demanda. Si se niegan, deja un mensaje y el siguiente intento
 * vuelve a pedirlos; si Android ya no muestra el diálogo ("no volver a preguntar"),
 * el siguiente intento abre los ajustes de la app.
 */
class PermissionRequestState(
    private val context: Context,
    private val permissions: Array<String>,
    private val deniedMessage: String,
    private val launch: (Array<String>) -> Unit
) {
    var message by mutableStateOf<String?>(null)
        internal set
    internal var blocked by mutableStateOf(false)
    internal var onGranted: (() -> Unit)? = null
    // Fuerza la recomposición de quien lea isGranted tras cada respuesta del diálogo
    private var resultCount by mutableStateOf(0)

    val isGranted: Boolean
        get() {
            resultCount
            return context.hasPermissions(permissions)
        }

    fun request(onGranted: () -> Unit = {}) {
        if (isGranted) {
            message = null
            onGranted()
            return
        }
        if (blocked) {
            context.openAppSettings()
            return
        }
        this.onGranted = onGranted
        launch(permissions)
    }

    internal fun onResult(result: Map<String, Boolean>) {
        resultCount++
        if (result.values.all { it } && isGranted) {
            message = null
            blocked = false
            onGranted?.invoke()
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity != null && permissions.any {
                ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
            }
            blocked = !canAskAgain
            message = if (blocked) {
                "$deniedMessage Android ya no mostrará el diálogo: pulsa de nuevo para abrir los ajustes de la app."
            } else {
                "$deniedMessage Pulsa el botón de nuevo para volver a solicitarlo."
            }
        }
        onGranted = null
    }
}

@Composable
fun rememberPermissionRequest(permissions: Array<String>, deniedMessage: String): PermissionRequestState {
    val context = LocalContext.current
    var state: PermissionRequestState? = null
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        state?.onResult(result)
    }
    state = remember(permissions.toList()) {
        PermissionRequestState(context, permissions, deniedMessage) { launcher.launch(it) }
    }
    return state
}
