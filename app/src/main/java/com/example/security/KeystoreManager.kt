package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeystoreManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "perimetral_local_aes_master"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    init {
        ensureMasterKey()
    }

    private fun ensureMasterKey() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
    }

    private fun getMasterKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * Cifra un mensaje en texto plano usando AES-GCM (256-bit).
     * Devuelve Base64(IV + Ciphertext + Tag).
     */
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Descifra un mensaje cifrado con AES-GCM.
     * Si el mensaje fue manipulado o proviene de un dispositivo no emparejado,
     * el tag GCM fallará y lanzará una excepción de seguridad.
     */
    fun decrypt(encryptedBase64: String): String {
        val combined = Base64.getDecoder().decode(encryptedBase64)
        if (combined.size < GCM_IV_LENGTH) {
            throw SecurityException("Cifrado corrupto o tamaño de IV inválido.")
        }

        val iv = ByteArray(GCM_IV_LENGTH)
        val cipherText = ByteArray(combined.size - GCM_IV_LENGTH)

        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH)
        System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), spec)

        val decryptedBytes = cipher.doFinal(cipherText)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Almacenamiento seguro local para tokens de vinculación y secretos de sesión
     */
    fun saveSecureSecret(key: String, value: String) {
        val encrypted = encrypt(value)
        val prefs = context.getSharedPreferences("perimetral_encrypted_secrets", Context.MODE_PRIVATE)
        prefs.edit().putString(key, encrypted).apply()
    }

    fun getSecureSecret(key: String): String? {
        val prefs = context.getSharedPreferences("perimetral_encrypted_secrets", Context.MODE_PRIVATE)
        val encrypted = prefs.getString(key, null) ?: return null
        return try {
            decrypt(encrypted)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Clave AES-256 compartida entre panel y nodo de la misma cuenta.
     * La clave maestra del Keystore es única por dispositivo (no exportable), así que no sirve
     * para que otro equipo descifre; esta clave se distribuye vía Firestore y se guarda cifrada
     * localmente para funcionar sin conexión.
     */
    @Volatile
    private var peerKey: SecretKey? = null

    fun hasPeerKey(): Boolean = peerKey != null

    fun setPeerKey(rawBase64: String) {
        val bytes = Base64.getDecoder().decode(rawBase64)
        peerKey = javax.crypto.spec.SecretKeySpec(bytes, "AES")
        saveSecureSecret(PEER_KEY_PREF, rawBase64)
    }

    fun loadCachedPeerKey(): Boolean {
        val cached = getSecureSecret(PEER_KEY_PREF) ?: return false
        peerKey = javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(cached), "AES")
        return true
    }

    fun generatePeerKeyBase64(): String {
        val bytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun encryptForPeer(plainText: String): String {
        val key = peerKey ?: throw SecurityException("Clave de enlace no disponible")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + cipherText)
    }

    fun decryptFromPeer(encryptedBase64: String): String {
        val key = peerKey ?: throw SecurityException("Clave de enlace no disponible")
        val combined = Base64.getDecoder().decode(encryptedBase64)
        if (combined.size < GCM_IV_LENGTH) {
            throw SecurityException("Cifrado corrupto o tamaño de IV inválido.")
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, combined, 0, GCM_IV_LENGTH))
        val plain = cipher.doFinal(combined, GCM_IV_LENGTH, combined.size - GCM_IV_LENGTH)
        return String(plain, Charsets.UTF_8)
    }
}

private const val PEER_KEY_PREF = "peer_link_key"
