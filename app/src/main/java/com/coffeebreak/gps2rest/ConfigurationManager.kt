package com.coffeebreak.gps2rest

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

class ConfigurationManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    private val keystoreAlias = "gps2rest_aes_key"
    private val keyStore: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val ENCRYPTED_KEY_PREF = "encrypted_derived_key"
    private val ENCRYPTED_IV_PREF = "encrypted_derived_key_iv"

    companion object {
        private const val PREFS_NAME = "gps2rest_config"
        private const val KEY_GPS_URL = "gps_url"
        private const val KEY_FREQUENCY_SECONDS = "frequency_seconds"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_BATTERY_OPTIMIZATION_REQUESTED = "battery_optimization_requested"
        private const val KEY_PRIVACY_MODE = "privacy_mode"
        private const val KEY_TRUNCATION_PRECISION = "truncation_precision"
        private const val KEY_RANDOM_NOISE_LEVEL = "random_noise_level"
        private const val KEY_ENABLE_JWT = "enable_jwt"
        private const val DEFAULT_GPS_URL = "http://192.168.1.1:8080/api/v1/gps"
        private const val DEFAULT_FREQUENCY_SECONDS = 15 // 15 seconds default
        private const val DEFAULT_RANDOM_NOISE_LEVEL = 50 // Default value for the slider
        
        // Privacy modes
        const val PRIVACY_MODE_RANDOM_NOISE = "Add Random Noise"
        const val PRIVACY_MODE_TRUNCATE = "Truncate Coordinates"
        const val PRIVACY_MODE_ORIGINAL = "Precision Location"
        private const val DEFAULT_PRIVACY_MODE = PRIVACY_MODE_RANDOM_NOISE
        private const val DEFAULT_TRUNCATION_PRECISION = 3 // 3 decimal places (~110m)
    }
    
    fun saveGpsUrl(url: String): Boolean {
        val normalizedUrl = normalizeUrl(url)
        return if (isValidUrl(normalizedUrl)) {
            sharedPreferences.edit()
                .putString(KEY_GPS_URL, normalizedUrl)
                .apply()
            true
        } else {
            false
        }
    }
    
    private fun normalizeUrl(url: String): String {
        val trimmed = url.trim()
        
        // If URL doesn't start with http:// or https://, assume http://
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "http://$trimmed"
        } else {
            trimmed
        }
    }
    
    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = java.net.URL(url)
            urlObj.protocol in listOf("http", "https") && 
            !url.startsWith("/") &&
            urlObj.host.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }
    
    fun getGpsUrl(): String {
        return sharedPreferences.getString(KEY_GPS_URL, DEFAULT_GPS_URL) ?: DEFAULT_GPS_URL
    }
    
    fun getCurrentSavedUrl(): String {
        return sharedPreferences.getString(KEY_GPS_URL, null) ?: "No URL saved (using default)"
    }
    
    fun saveFrequencySeconds(frequencySeconds: Int) {
        sharedPreferences.edit()
            .putInt(KEY_FREQUENCY_SECONDS, frequencySeconds)
            .apply()
    }
    
    fun getFrequencySeconds(): Int {
        return sharedPreferences.getInt(KEY_FREQUENCY_SECONDS, DEFAULT_FREQUENCY_SECONDS)
    }
    
    fun setStartOnBoot(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_START_ON_BOOT, enabled)
            .apply()
    }
    
    fun shouldStartOnBoot(): Boolean {
        return sharedPreferences.getBoolean(KEY_START_ON_BOOT, false)
    }
    
    fun setServiceRunning(running: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_SERVICE_RUNNING, running)
            .apply()
    }
    
    fun isServiceRunning(): Boolean {
        return sharedPreferences.getBoolean(KEY_SERVICE_RUNNING, false)
    }
    
    fun setBatteryOptimizationRequested(requested: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_BATTERY_OPTIMIZATION_REQUESTED, requested)
            .apply()
    }
    
    fun isBatteryOptimizationRequested(): Boolean {
        return sharedPreferences.getBoolean(KEY_BATTERY_OPTIMIZATION_REQUESTED, false)
    }
    
    // Privacy mode configuration
    fun setPrivacyMode(mode: String) {
        sharedPreferences.edit()
            .putString(KEY_PRIVACY_MODE, mode)
            .apply()
    }
    
    fun getPrivacyMode(): String {
        return sharedPreferences.getString(KEY_PRIVACY_MODE, DEFAULT_PRIVACY_MODE) ?: DEFAULT_PRIVACY_MODE
    }
    
    fun setTruncationPrecision(precision: Int) {
        sharedPreferences.edit()
            .putInt(KEY_TRUNCATION_PRECISION, precision)
            .apply()
    }
    
    fun getTruncationPrecision(): Int {
        return sharedPreferences.getInt(KEY_TRUNCATION_PRECISION, DEFAULT_TRUNCATION_PRECISION)
    }
    
    fun getRandomNoiseLevel(): Int {
        return sharedPreferences.getInt(KEY_RANDOM_NOISE_LEVEL, DEFAULT_RANDOM_NOISE_LEVEL)
    }

    fun setRandomNoiseLevel(level: Int) {
        sharedPreferences.edit()
            .putInt(KEY_RANDOM_NOISE_LEVEL, level)
            .apply()
    }

    /**
     * Generates a cryptographically secure random noise value.
     * @param maxNoiseLevel The maximum noise level to generate.
     * @return A random noise value between 0 and maxNoiseLevel (inclusive).
     */
    fun generateSecureRandomNoise(maxNoiseLevel: Int): Int {
        return secureRandom.nextInt(maxNoiseLevel + 1)
    }

    fun setEnableJwt(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_ENABLE_JWT, enabled)
            .apply()
    }

    fun isJwtEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_ENABLE_JWT, false)
    }

    /**
     * Stores the derived key securely by encrypting it with an AES key in the Android Keystore.
     */
    fun setEncryptionKey(derivedKey: ByteArray) {
        val secretKey = getOrCreateAesKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(derivedKey)
        val encodedEncrypted = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT)
        val encodedIv = android.util.Base64.encodeToString(iv, android.util.Base64.DEFAULT)
        sharedPreferences.edit()
            .putString(ENCRYPTED_KEY_PREF, encodedEncrypted)
            .putString(ENCRYPTED_IV_PREF, encodedIv)
            .apply()
    }

    /**
     * Retrieves the derived key by decrypting it with the AES key from the Android Keystore.
     * Returns null if not set.
     */
    fun getEncryptionKey(): ByteArray? {
        val encodedEncrypted = sharedPreferences.getString(ENCRYPTED_KEY_PREF, null) ?: return null
        val encodedIv = sharedPreferences.getString(ENCRYPTED_IV_PREF, null) ?: return null
        val encrypted = android.util.Base64.decode(encodedEncrypted, android.util.Base64.DEFAULT)
        val iv = android.util.Base64.decode(encodedIv, android.util.Base64.DEFAULT)
        val secretKey = getOrCreateAesKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(encrypted)
    }

    /**
     * Gets or creates an AES key in the Android Keystore.
     */
    private fun getOrCreateAesKey(): javax.crypto.SecretKey {
        if (keyStore.containsAlias(keystoreAlias)) {
            val entry = keyStore.getEntry(keystoreAlias, null) as KeyStore.SecretKeyEntry
            return entry.secretKey
        }
        val keyGenerator = javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            keystoreAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }
}
