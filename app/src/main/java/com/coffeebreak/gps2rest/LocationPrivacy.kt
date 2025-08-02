package com.coffeebreak.gps2rest

import android.location.Location
import kotlin.math.pow
import kotlin.random.Random

/**
 * Utility class for applying privacy settings to GPS coordinates
 */
object LocationPrivacy {
    
    /**
     * Apply privacy settings to a location based on the configured privacy mode
     */
    fun applyPrivacySettings(
        location: Location,
        privacyMode: String,
        truncatePrecision: Int = 3
    ): Location {
        val newLocation = Location(location)
        when (privacyMode) {
            ConfigurationManager.PRIVACY_MODE_RANDOM_NOISE -> {
                addRandomNoise(newLocation)
            }
            ConfigurationManager.PRIVACY_MODE_TRUNCATE -> {
                truncateCoordinates(newLocation, truncatePrecision)
            }
            ConfigurationManager.PRIVACY_MODE_ORIGINAL -> {
                // No changes - use original coordinates
            }
        }
        return newLocation
    }
    
    /**
     * Add random noise to coordinates for geo-indistinguishability
     * Latitude: original + random(-0.001, +0.001)
     * Longitude: original + random(-0.001, +0.001)
     */
    private fun addRandomNoise(location: Location) {
        val noiseRange = 0.001 // ±0.001 degrees ≈ ±111 meters
        
        val latNoise = Random.nextDouble(-noiseRange, noiseRange)
        val lonNoise = Random.nextDouble(-noiseRange, noiseRange)
        
        location.latitude = location.latitude + latNoise
        location.longitude = location.longitude + lonNoise
    }
    
    /**
     * Truncate coordinates to specified decimal places
     * 5 decimal places ≈ 1.1 m
     * 3 decimal places ≈ 110 m (default)
     * 2 decimal places ≈ 1.1 km
     * 1 decimal place ≈ 11 km
     */
    private fun truncateCoordinates(location: Location, decimalPlaces: Int) {
        val multiplier = 10.0.pow(decimalPlaces.toDouble())
        
        location.latitude = kotlin.math.round(location.latitude * multiplier) / multiplier
        location.longitude = kotlin.math.round(location.longitude * multiplier) / multiplier
    }
    
    /**
     * Get human-readable description of privacy level
     */
    fun getPrivacyDescription(privacyMode: String, truncatePrecision: Int = 3): String {
        return when (privacyMode) {
            ConfigurationManager.PRIVACY_MODE_RANDOM_NOISE ->
                "Random Noise (±111m accuracy)"
            ConfigurationManager.PRIVACY_MODE_TRUNCATE -> {
                val accuracy = getAccuracyDescription(truncatePrecision)
                "Truncated ($accuracy accuracy)"
            }
            ConfigurationManager.PRIVACY_MODE_ORIGINAL ->
                "Full Precision (no privacy)"
            else -> "Unknown"
        }
    }
    
    /**
     * Get accuracy description for truncation precision
     */
    private fun getAccuracyDescription(decimalPlaces: Int): String {
        return when (decimalPlaces) {
            6 -> "±0.11m"
            5 -> "±1.1m"
            4 -> "±11m"
            3 -> "±110m"
            2 -> "±1.1km"
            1 -> "±11km"
            else -> "±${kotlin.math.round(111000.0 / (10.0.pow(decimalPlaces.toDouble())))}m"
        }
    }
}
