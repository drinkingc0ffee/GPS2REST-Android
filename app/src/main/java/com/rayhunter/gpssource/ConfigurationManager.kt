package com.rayhunter.gpssource

import android.content.Context
import android.content.SharedPreferences

class ConfigurationManager(context: Context) {
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "rayhunter_config"
        private const val KEY_GPS_URL = "gps_url"
        private const val KEY_FREQUENCY_SECONDS = "frequency_seconds"
        private const val DEFAULT_GPS_URL = "http://192.168.1.1:8080/api/v1/gps"
        private const val DEFAULT_FREQUENCY_SECONDS = 15 // 15 seconds default
    }
    
    fun saveGpsUrl(url: String) {
        if (isValidUrl(url)) {
            sharedPreferences.edit()
                .putString(KEY_GPS_URL, url)
                .apply()
        }
    }
    
    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = java.net.URL(url)
            urlObj.protocol in listOf("http", "https") && !url.startsWith("/")
        } catch (e: Exception) {
            false
        }
    }
    
    fun getGpsUrl(): String {
        return sharedPreferences.getString(KEY_GPS_URL, DEFAULT_GPS_URL) ?: DEFAULT_GPS_URL
    }
    
    fun saveFrequencySeconds(frequencySeconds: Int) {
        sharedPreferences.edit()
            .putInt(KEY_FREQUENCY_SECONDS, frequencySeconds)
            .apply()
    }
    
    fun getFrequencySeconds(): Int {
        return sharedPreferences.getInt(KEY_FREQUENCY_SECONDS, DEFAULT_FREQUENCY_SECONDS)
    }
}
