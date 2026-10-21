package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PinSecurity {

    fun generateSalt(): String {
        val random = SecureRandom()
        val saltBytes = ByteArray(16)
        random.nextBytes(saltBytes)
        return Base64.getEncoder().encodeToString(saltBytes)
    }

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val saltedPin = salt + pin
        val hashBytes = digest.digest(saltedPin.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    fun verifyPin(pinInput: String, salt: String, expectedHash: String): Boolean {
        if (pinInput.length != 4 || !pinInput.all { it.isDigit() }) return false
        val computedHash = hashPin(pinInput, salt)
        return computedHash == expectedHash
    }

    // PIN inicial predeterminado para nuevas bodegas: "2580"
    fun createDefaultPinCredentials(): Pair<String, String> {
        val salt = generateSalt()
        val hash = hashPin("2580", salt)
        return Pair(hash, salt)
    }
}
