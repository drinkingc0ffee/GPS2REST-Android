package com.coffeebreak.gps2rest

import android.location.Location
import kotlin.random.Random

class PrivacyManager(private val configManager: ConfigurationManager) {
    
    companion object {
        // Noise range for geo-indistinguishability (±0.001 degrees ≈ ±111 meters)
        private const val NOISE_RANGE_DEGREES = 0.001
    }
    
    /**
     * Applies privacy protection to GPS coordinates based on configured privacy mode
     */
    fun applyPrivacyProtection(location: Location): Location {
        val privacyMode = configManager.getPrivacyMode()
        
        return when (privacyMode) {
            ConfigurationManager.PRIVACY_MODE_RANDOM_NOISE -> {
                applyRandomNoise(location)
            }
            ConfigurationManager.PRIVACY_MODE_TRUNCATE -> {
                applyTruncation(location)
            }
            ConfigurationManager.PRIVACY_MODE_ORIGINAL -> {
                location // Return original location unchanged
            }
            else -> {
                // Default to random noise if unknown mode
                applyRandomNoise(location)
            }
        }
    }
    
    /**
     * Geo-Indistinguishability: Adds random noise to coordinates
     * Provides moderate privacy while maintaining reasonable accuracy
     */
    private fun applyRandomNoise(location: Location): Location {
        val noisyLocation = Location(location)
        
        // Generate random noise in range [-NOISE_RANGE_DEGREES, +NOISE_RANGE_DEGREES]
        val latNoise = (Random.nextDouble() - 0.5) * 2 * NOISE_RANGE_DEGREES
        val lonNoise = (Random.nextDouble() - 0.5) * 2 * NOISE_RANGE_DEGREES
        
        noisyLocation.latitude = location.latitude + latNoise
        noisyLocation.longitude = location.longitude + lonNoise
        
        return noisyLocation
    }
    
    /**
     * Spatial Rounding: Truncates coordinates to specified decimal places
     * Provides varying levels of privacy based on precision setting
     */
    private fun applyTruncation(location: Location): Location {
        val precision = configManager.getTruncationPrecision()
        val truncatedLocation = Location(location)
        
        // Use simple multiplier approach instead of pow()
        val multiplier = when (precision) {
            1 -> 10.0
            2 -> 100.0
            3 -> 1000.0
            4 -> 10000.0
            5 -> 100000.0
            6 -> 1000000.0
            else -> 1000.0 // default to 3 decimal places
        }
        
        // Truncate to specified decimal places using floor equivalent
        truncatedLocation.latitude = truncateToDecimal(location.latitude, multiplier)
        truncatedLocation.longitude = truncateToDecimal(location.longitude, multiplier)
        
        return truncatedLocation
    }
    
    /**
     * Helper function to truncate a value to specific decimal places
     */
    private fun truncateToDecimal(value: Double, multiplier: Double): Double {
        // Convert to string and back to avoid floating point precision issues
        val scaled = (value * multiplier).toLong()
        return scaled.toDouble() / multiplier
    }
    
    /**
     * Get human-readable description of current privacy mode
     */
    fun getPrivacyModeDescription(): String {
        return when (configManager.getPrivacyMode()) {
            ConfigurationManager.PRIVACY_MODE_RANDOM_NOISE -> {
                "Random Noise (±111m) - Moderate privacy protection"
            }
            ConfigurationManager.PRIVACY_MODE_TRUNCATE -> {
                val precision = configManager.getTruncationPrecision()
                val accuracy = getTruncationAccuracyDescription(precision)
                "Truncated to $precision decimal places ($accuracy)"
            }
            ConfigurationManager.PRIVACY_MODE_ORIGINAL -> {
                "Original coordinates - No privacy protection"
            }
            else -> "Unknown privacy mode"
        }
    }
    
    /**
     * Get accuracy description for truncation precision level
     */
    fun getTruncationAccuracyDescription(precision: Int): String {
        return when (precision) {
            6 -> "±0.11m precision"
            5 -> "±1.1m precision"
            4 -> "±11m precision"
            3 -> "±110m precision"
            2 -> "±1.1km precision"
            1 -> "±11km precision"
            else -> "±110m precision" // default
        }
    }
    
    /**
     * Get available truncation precision options for UI
     */
    fun getTruncationOptions(): List<Pair<Int, String>> {
        return listOf(
            6 to "6 decimal places (±0.11m) - Near full precision",
            5 to "5 decimal places (±1.1m) - Very high precision",
            4 to "4 decimal places (±11m) - High precision",
            3 to "3 decimal places (±110m) - Medium precision",
            2 to "2 decimal places (±1.1km) - Low precision",
            1 to "1 decimal place (±11km) - Very low precision"
        )
    }
}
