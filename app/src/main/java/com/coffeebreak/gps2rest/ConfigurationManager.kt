package com.coffeebreak.gps2rest

import android.content.Context
import android.content.SharedPreferences

class ConfigurationManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "gps2rest_config"
        private const val KEY_GPS_URL = "gps_url"
        private const val KEY_FREQUENCY_SECONDS = "frequency_seconds"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_SERVICE_RUNNING = "service_running"
        private const val KEY_BATTERY_OPTIMIZATION_REQUESTED = "battery_optimization_requested"
        private const val DEFAULT_GPS_URL = "http://192.168.1.1:8080/api/v1/gps"
        private const val DEFAULT_FREQUENCY_SECONDS = 15 // 15 seconds default
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
}
