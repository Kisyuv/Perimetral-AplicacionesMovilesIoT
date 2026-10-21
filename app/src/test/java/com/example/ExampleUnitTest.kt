package com.example

import com.example.security.PinSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun pinSecurity_hashAndVerify_succeeds() {
        val (hash, salt) = PinSecurity.createDefaultPinCredentials()
        // El PIN predeterminado es 2580
        assertTrue(PinSecurity.verifyPin("2580", salt, hash))
        assertFalse(PinSecurity.verifyPin("1234", salt, hash))
        assertFalse(PinSecurity.verifyPin("258", salt, hash))
    }

    @Test
    fun pinSecurity_differentSalts_produceDifferentHashes() {
        val salt1 = PinSecurity.generateSalt()
        val salt2 = PinSecurity.generateSalt()
        assertNotEquals(salt1, salt2)

        val hash1 = PinSecurity.hashPin("9999", salt1)
        val hash2 = PinSecurity.hashPin("9999", salt2)
        assertNotEquals(hash1, hash2)
    }
}
