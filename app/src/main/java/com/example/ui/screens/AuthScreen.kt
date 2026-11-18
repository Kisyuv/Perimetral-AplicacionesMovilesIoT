package com.example.ui.screens

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.repository.AuthRepository
import com.example.data.repository.AuthResult
import com.example.network.NodeMode
import com.example.ui.theme.DotoFamily
import com.example.ui.theme.AccentAlarm
import com.example.ui.theme.AccentDisarmed
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.LocalAlarmAccentColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

enum class AuthMode {
    LOGIN,
    REGISTER,
    FORGOT_PASSWORD
}

@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit,
    nodeMode: NodeMode,
    mqttChannel: String,
    onNodeConfigChange: (NodeMode, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accent = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    var authMode by remember { mutableStateOf(AuthMode.LOGIN) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nombre by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var canalTexto by remember { mutableStateOf(mqttChannel) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    // Validación reactiva de contraseña
    val hasMinLength = password.length >= 8
    val hasUppercase = password.any { it.isUpperCase() }
    val hasNumber = password.any { it.isDigit() }

    fun executeSignIn() {
        errorMessage = null
        successMessage = null
        isLoading = true
        coroutineScope.launch {
            when (val result = authRepository.signInWithEmail(email, password)) {
                is AuthResult.Success -> {
                    isLoading = false
                    onAuthSuccess()
                }
                is AuthResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    fun executeSignUp() {
        errorMessage = null
        successMessage = null
        isLoading = true
        coroutineScope.launch {
            when (val result = authRepository.signUpWithEmail(email, password, nombre)) {
                is AuthResult.Success -> {
                    isLoading = false
                    onAuthSuccess()
                }
                is AuthResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    fun executeResetPassword() {
        errorMessage = null
        successMessage = null
        isLoading = true
        coroutineScope.launch {
            when (val result = authRepository.sendPasswordReset(email)) {
                is AuthResult.Success -> {
                    isLoading = false
                    successMessage = "Se ha enviado un enlace de restablecimiento a $email. Revisa tu bandeja."
                }
                is AuthResult.Error -> {
                    isLoading = false
                    errorMessage = result.message
                }
            }
        }
    }

    fun executeGoogleSignIn() {
        errorMessage = null
        successMessage = null
        isLoading = true

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            isLoading = false
            errorMessage = "Configuración de Google Sign-In no disponible."
            return
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        coroutineScope.launch {
            try {
                val result = credentialManager.getCredential(context as Activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    when (val authRes = authRepository.signInWithGoogleCredential(authCredential)) {
                        is AuthResult.Success -> {
                            isLoading = false
                            onAuthSuccess()
                        }
                        is AuthResult.Error -> {
                            isLoading = false
                            errorMessage = authRes.message
                        }
                    }
                } else {
                    isLoading = false
                    errorMessage = "Tipo de credencial no esperado."
                }
            } catch (e: GetCredentialCancellationException) {
                isLoading = false
                // Cancelado por el usuario; no es un error fatal
            } catch (e: Exception) {
                isLoading = false
                errorMessage = e.localizedMessage ?: "Error al autenticar con Google."
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 24.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Emblema e Identidad Minimalista
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .border(1.dp, accent, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = "Perimetral Alarma",
                tint = accent,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "PERIMETRAL",
            color = TextPrimary,
            fontSize = 24.sp,
            fontFamily = DotoFamily,
            fontWeight = FontWeight.Medium,
            letterSpacing = 3.sp
        )

        Text(
            text = "SEGURIDAD DE BODEGA // CONTROL DE ACCESO",
            color = TextMuted,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Selector minimalista entre Login y Registro
        if (authMode != AuthMode.FORGOT_PASSWORD) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorderSubtle, RoundedCornerShape(6.dp))
                    .padding(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (authMode == AuthMode.LOGIN) DarkSurfaceVariant else Color.Transparent)
                        .clickable {
                            authMode = AuthMode.LOGIN
                            errorMessage = null
                            successMessage = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Iniciar Sesión",
                        color = if (authMode == AuthMode.LOGIN) TextPrimary else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (authMode == AuthMode.REGISTER) DarkSurfaceVariant else Color.Transparent)
                        .clickable {
                            authMode = AuthMode.REGISTER
                            errorMessage = null
                            successMessage = null
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Registrar Operador",
                        color = if (authMode == AuthMode.REGISTER) TextPrimary else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Mensaje de Error
        AnimatedVisibility(visible = errorMessage != null) {
            errorMessage?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurface)
                        .border(1.dp, TextSecondary, RoundedCornerShape(6.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = error,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Mensaje de Éxito (Recuperación)
        AnimatedVisibility(visible = successMessage != null) {
            successMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurface)
                        .border(1.dp, AccentDisarmed, RoundedCornerShape(6.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = msg,
                        color = AccentDisarmed,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Campos de Formulario
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (authMode == AuthMode.REGISTER) {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre o Alias de Operador", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = TextMuted)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nombre_input"),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = accent,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    )
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    errorMessage = null
                },
                label = { Text("Correo Electrónico", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(Icons.Outlined.Email, contentDescription = null, tint = TextMuted)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = DarkBorder,
                    focusedLabelColor = accent,
                    unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                )
            )

            if (authMode != AuthMode.FORGOT_PASSWORD) {
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text("Contraseña", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = TextMuted)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                                contentDescription = if (passwordVisible) "Ocultar" else "Mostrar",
                                tint = TextMuted
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("password_input"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (authMode == AuthMode.LOGIN) executeSignIn() else executeSignUp()
                    }),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        unfocusedBorderColor = DarkBorder,
                        focusedLabelColor = accent,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    )
                )

                // Indicadores de validación de contraseña en modo Registro
                if (authMode == AuthMode.REGISTER && password.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkSurfaceVariant)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ValidationRuleItem(text = "Mínimo 8 caracteres", isValid = hasMinLength)
                        ValidationRuleItem(text = "Al menos una letra mayúscula", isValid = hasUppercase)
                        ValidationRuleItem(text = "Al menos un número", isValid = hasNumber)
                    }
                }
            }
        }

        // Enlace de "¿Olvidaste tu contraseña?"
        if (authMode == AuthMode.LOGIN) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = "¿Olvidaste tu contraseña?",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable {
                            authMode = AuthMode.FORGOT_PASSWORD
                            errorMessage = null
                            successMessage = null
                        }
                        .padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Botón Principal de Acción
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(if (isLoading) DarkSurfaceVariant else accent)
                .clickable(enabled = !isLoading) {
                    when (authMode) {
                        AuthMode.LOGIN -> executeSignIn()
                        AuthMode.REGISTER -> executeSignUp()
                        AuthMode.FORGOT_PASSWORD -> executeResetPassword()
                    }
                }
                .padding(vertical = 14.dp)
                .testTag("auth_submit_button"),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = TextPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = when (authMode) {
                        AuthMode.LOGIN -> "INICIAR SESIÓN"
                        AuthMode.REGISTER -> "CREAR CUENTA DE OPERADOR"
                        AuthMode.FORGOT_PASSWORD -> "ENVIAR ENLACE DE RECUPERACIÓN"
                    },
                    color = DarkBackground,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }
        }

        if (authMode == AuthMode.FORGOT_PASSWORD) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "← Volver a inicio de sesión",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable {
                        authMode = AuthMode.LOGIN
                        errorMessage = null
                        successMessage = null
                    }
                    .padding(8.dp)
            )
        }

        // Separador e Inicio con Google
        if (authMode != AuthMode.FORGOT_PASSWORD) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(DarkBorder))
                Text(
                    text = "  O CONTINUAR CON  ",
                    color = TextMuted,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Box(modifier = Modifier.weight(1f).height(1.dp).background(DarkBorder))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón de Google Sign-In con Credential Manager
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurface)
                    .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
                    .clickable(enabled = !isLoading) { executeGoogleSignIn() }
                    .padding(vertical = 14.dp)
                    .testTag("google_signin_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "G",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continuar con Google",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        NodeModeSelector(
            nodeMode = nodeMode,
            canal = canalTexto,
            onModeChange = { onNodeConfigChange(it, canalTexto) },
            onCanalChange = {
                canalTexto = it
                onNodeConfigChange(nodeMode, it)
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

/** Quién hace de nodo de sensores. Se elige antes de iniciar sesión; con un ESP32 el teléfono es panel. */
@Composable
private fun NodeModeSelector(
    nodeMode: NodeMode,
    canal: String,
    onModeChange: (NodeMode) -> Unit,
    onCanalChange: (String) -> Unit
) {
    val accent = LocalAlarmAccentColor.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("NODO DE SENSORES", color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NodeMode.entries.forEach { mode ->
                val selected = mode == nodeMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurface)
                        .border(1.dp, if (selected) accent else DarkBorder, RoundedCornerShape(6.dp))
                        .clickable { onModeChange(mode) }
                        .padding(vertical = 10.dp)
                        .testTag("node_mode_${mode.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        color = if (selected) accent else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        Text(
            text = when (nodeMode) {
                NodeMode.TELEFONO -> "Un teléfono con la app hace de nodo: lee los sensores y activa la sirena."
                NodeMode.ESP32_WOKWI -> "Simula el ESP32 en wokwi.com (ver hardware/esp32-bodega). Este teléfono será panel."
                NodeMode.ESP32_FISICO -> "Un ESP32 con sensores y sirena conectado por MQTT. Este teléfono será panel."
            },
            color = TextSecondary,
            fontSize = 11.sp
        )
        if (nodeMode != NodeMode.TELEFONO) {
            OutlinedTextField(
                value = canal,
                onValueChange = onCanalChange,
                label = { Text("Canal MQTT", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth().testTag("mqtt_channel_input"),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    unfocusedBorderColor = DarkBorder,
                    focusedLabelColor = accent,
                    unfocusedLabelColor = TextMuted,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                )
            )
        }
    }
}

@Composable
private fun ValidationRuleItem(text: String, isValid: Boolean) {
    val color = if (isValid) AccentDisarmed else TextMuted
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isValid) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = color,
            fontSize = 11.sp
        )
    }
}
