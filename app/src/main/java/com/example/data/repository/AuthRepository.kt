package com.example.data.repository

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed class AuthResult<out T> {
    data class Success<out T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun validateEmail(email: String): String? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return "El correo electrónico es requerido."
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        if (!emailRegex.matches(trimmed)) return "Ingresa un formato de correo electrónico válido."
        return null
    }

    fun validatePassword(password: String): String? {
        if (password.length < 8) {
            return "La contraseña debe tener al menos 8 caracteres."
        }
        if (!password.any { it.isUpperCase() }) {
            return "La contraseña debe incluir al menos una letra mayúscula."
        }
        if (!password.any { it.isDigit() }) {
            return "La contraseña debe incluir al menos un número."
        }
        return null
    }

    suspend fun signInWithEmail(email: String, password: String): AuthResult<FirebaseUser> {
        return try {
            val emailError = validateEmail(email)
            if (emailError != null) return AuthResult.Error(emailError)

            if (password.isBlank()) return AuthResult.Error("Ingresa tu contraseña.")

            val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return AuthResult.Error("No se pudo iniciar sesión.")
            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(parseAuthError(e))
        }
    }

    suspend fun signUpWithEmail(email: String, password: String, nombre: String): AuthResult<FirebaseUser> {
        return try {
            val emailError = validateEmail(email)
            if (emailError != null) return AuthResult.Error(emailError)

            val passwordError = validatePassword(password)
            if (passwordError != null) return AuthResult.Error(passwordError)

            val trimmedNombre = nombre.trim()
            if (trimmedNombre.isEmpty()) return AuthResult.Error("Por favor ingresa tu nombre o alias de operador.")

            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val user = result.user ?: return AuthResult.Error("Error al crear cuenta.")

            // Crear documento users/{uid} = { email, nombre, creadoEn }
            val userDoc = mapOf(
                "email" to (user.email ?: email.trim()),
                "nombre" to trimmedNombre,
                "creadoEn" to FieldValue.serverTimestamp()
            )

            firestore.collection("users").document(user.uid).set(userDoc).await()

            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(parseAuthError(e))
        }
    }

    suspend fun signInWithGoogleCredential(credential: AuthCredential): AuthResult<FirebaseUser> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: return AuthResult.Error("Error en autenticación con Google.")

            // Verificar si el documento de usuario existe; si no, crearlo
            val docRef = firestore.collection("users").document(user.uid)
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                val userDoc = mapOf(
                    "email" to (user.email ?: ""),
                    "nombre" to (user.displayName ?: "Operador Bodega"),
                    "creadoEn" to FieldValue.serverTimestamp()
                )
                docRef.set(userDoc).await()
            }

            AuthResult.Success(user)
        } catch (e: Exception) {
            AuthResult.Error(parseAuthError(e))
        }
    }

    suspend fun sendPasswordReset(email: String): AuthResult<Unit> {
        return try {
            val emailError = validateEmail(email)
            if (emailError != null) return AuthResult.Error(emailError)

            auth.sendPasswordResetEmail(email.trim()).await()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            AuthResult.Error(parseAuthError(e))
        }
    }

    fun signOut() {
        auth.signOut()
    }

    private fun parseAuthError(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidUserException -> "No existe ninguna cuenta registrada con este correo electrónico."
            is FirebaseAuthInvalidCredentialsException -> "Credenciales inválidas. Revisa el correo y contraseña."
            is FirebaseAuthUserCollisionException -> "Este correo electrónico ya está registrado en el sistema."
            is FirebaseAuthWeakPasswordException -> "La contraseña proporcionada es demasiado débil."
            is FirebaseAuthException -> when (e.errorCode) {
                "ERROR_WRONG_PASSWORD" -> "Contraseña incorrecta."
                "ERROR_USER_NOT_FOUND" -> "Usuario no encontrado."
                "ERROR_EMAIL_ALREADY_IN_USE" -> "El correo ya está en uso por otra cuenta."
                "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos fallidos. Intenta más tarde."
                "ERROR_NETWORK_REQUEST_FAILED" -> "Error de conexión. Verifica tu acceso a internet."
                else -> e.localizedMessage ?: "Error de autenticación."
            }
            else -> e.localizedMessage ?: "Ocurrió un error inesperado. Intenta nuevamente."
        }
    }
}
