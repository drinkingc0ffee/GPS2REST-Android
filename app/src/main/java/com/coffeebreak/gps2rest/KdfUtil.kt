package com.coffeebreak.gps2rest

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object KdfUtil {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH = 256
    private val FIXED_SALT = "gps2rest-salt-2025".toByteArray(Charsets.UTF_8) // Fixed salt for both sides

    fun deriveKeyFromPin(pin: String): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), FIXED_SALT, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun generatePerMessageSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }
}
