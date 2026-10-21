package com.example.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SecurityCodeManager {

    private var masterPin: String = "2580" // PIN predeterminado de seguridad para bodega
    private var failedAttempts = 0
    private var lockoutUntilTimestamp: Long = 0L

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    fun validatePin(pin: String): Boolean {
        if (System.currentTimeMillis() < lockoutUntilTimestamp) {
            _isLocked.value = true
            return false
        }
        _isLocked.value = false

        return if (pin == masterPin) {
            failedAttempts = 0
            true
        } else {
            failedAttempts++
            if (failedAttempts >= 5) {
                // Bloqueo de seguridad temporal de 60 segundos
                lockoutUntilTimestamp = System.currentTimeMillis() + 60_000L
                _isLocked.value = true
            }
            false
        }
    }

    fun updatePin(currentPin: String, newPin: String): Boolean {
        if (validatePin(currentPin) && newPin.length == 4 && newPin.all { it.isDigit() }) {
            masterPin = newPin
            return true
        }
        return false
    }

    fun getFailedAttempts(): Int = failedAttempts
}
