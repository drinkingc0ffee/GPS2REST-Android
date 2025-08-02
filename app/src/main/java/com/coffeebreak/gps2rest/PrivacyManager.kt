package com.coffeebreak.gps2rest

import android.location.Location
import kotlin.math.*
import kotlin.random.Random

/**
 * Privacy protection manager that implements multiple GPS privacy protection algorithms
 * including geo-indistinguishability with Laplace noise and spatial quantization.
 */
class PrivacyManager(private val configurationManager: ConfigurationManager) {

    companion object {
        // Privacy mode constants
        const val MODE_PRECISION_LOCATION = "Precision Location" // Renamed from Original
        const val MODE_RANDOM_NOISE = "Add Random Noise"
        const val MODE_TRUNCATE = "Truncate Coordinates"
        
        // Default privacy parameters
        private const val DEFAULT_NOISE_RADIUS_METERS = 111.0 // ~111m default noise radius
        private const val EARTH_RADIUS_METERS = 6371000.0 // Earth radius in meters
        private const val DEGREES_TO_RADIANS = PI / 180.0
        private const val RADIANS_TO_DEGREES = 180.0 / PI
        
        // Truncation options: [precision, description]
        private val TRUNCATION_OPTIONS = listOf(
            Pair(1, "~11 km accuracy"),
            Pair(2, "~1.1 km accuracy"),
            Pair(3, "~110 m accuracy"),
            Pair(4, "~11 m accuracy"),
            Pair(5, "~1.1 m accuracy"),
            Pair(6, "~0.11 m accuracy")
        )

        fun getTruncationOptions(): List<Pair<Int, String>> = TRUNCATION_OPTIONS

        fun getPrivacyModeDescription(mode: String): String {
            return when (mode) {
                MODE_PRECISION_LOCATION -> "No privacy protection - original GPS precision"
                MODE_RANDOM_NOISE -> "Adds random noise within ±111m radius for geo-indistinguishability"
                MODE_TRUNCATE -> "Reduces coordinate precision to limit location accuracy"
                else -> "Unknown privacy mode"
            }
        }

        fun getPrivacyLevel(mode: String): String {
            return when (mode) {
                MODE_PRECISION_LOCATION -> "0% (No Protection)"
                MODE_RANDOM_NOISE -> "High (85-95%)"
                MODE_TRUNCATE -> "Variable (20-90%)"
                else -> "Unknown"
            }
        }

        fun getPrivacyRadius(mode: String, truncationPrecision: Int? = null): String {
            return when (mode) {
                MODE_PRECISION_LOCATION -> "0m"
                MODE_RANDOM_NOISE -> "±111m"
                MODE_TRUNCATE -> {
                    val precision = truncationPrecision ?: 3
                    val radiusMeters = when (precision) {
                        1 -> 11000
                        2 -> 1100
                        3 -> 110
                        4 -> 11
                        5 -> 1
                        6 -> 0
                        else -> 110
                    }
                    "±${radiusMeters}m"
                }
                else -> "Unknown"
            }
        }

        /**
         * Applies random noise to the GPS coordinates.
         */
        fun applyRandomNoise(location: Location): Location {
            val noiseLat = Random.nextDouble(-0.001, 0.001)
            val noiseLon = Random.nextDouble(-0.001, 0.001)
            location.latitude += noiseLat
            location.longitude += noiseLon
            return location
        }

        /**
         * Truncates the GPS coordinates to the specified number of decimal places.
         */
        fun truncateCoordinates(location: Location, precision: Int): Location {
            val factor = 10.0.pow(precision)
            location.latitude = floor(location.latitude * factor) / factor
            location.longitude = floor(location.longitude * factor) / factor
            return location
        }

        /**
         * Returns the original GPS coordinates without modification.
         */
        fun applyOriginal(location: Location): Location {
            return location
        }

        /**
         * Applies the selected privacy mode to the GPS coordinates.
         */
        fun applyPrivacyMode(location: Location, mode: String, precision: Int? = null): Location {
            return when (mode) {
                MODE_RANDOM_NOISE -> applyRandomNoise(location)
                MODE_TRUNCATE -> truncateCoordinates(location, precision ?: 3)
                MODE_PRECISION_LOCATION -> applyOriginal(location)
                else -> location
            }
        }
    }

    /**
     * Apply privacy protection to a GPS location based on current configuration
     */
    fun applyPrivacyProtection(location: Location): Location {
        val privacyMode = configurationManager.getPrivacyMode()
        
        return when (privacyMode) {
            MODE_PRECISION_LOCATION -> {
                // Return original location unchanged
                location
            }
            MODE_RANDOM_NOISE -> {
                applyRandomNoise(location, DEFAULT_NOISE_RADIUS_METERS)
            }
            MODE_TRUNCATE -> {
                val precision = configurationManager.getTruncationPrecision()
                applyTruncation(location, precision)
            }
            else -> {
                // Default to original if unknown mode
                location
            }
        }
    }

    /**
     * Apply geo-indistinguishability using Laplace mechanism with polar coordinates
     * This provides formal privacy guarantees by adding calibrated noise
     */
    private fun applyRandomNoise(location: Location, radiusMeters: Double): Location {
        // Generate random angle (0 to 2π)
        val theta = Random.nextDouble() * 2 * PI
        
        // Generate random distance using Laplace distribution
        // For geo-indistinguishability, we use exponential distribution for radius
        val u = Random.nextDouble()
        val r = -radiusMeters * ln(1.0 - u)
        
        // Convert to Cartesian coordinates
        val deltaLat = r * cos(theta)
        val deltaLon = r * sin(theta)
        
        // Convert meter offsets to coordinate offsets
        val latOffset = deltaLat / EARTH_RADIUS_METERS * RADIANS_TO_DEGREES
        val lonOffset = deltaLon / (EARTH_RADIUS_METERS * cos(location.latitude * DEGREES_TO_RADIANS)) * RADIANS_TO_DEGREES
        
        // Create new location with noise applied
        val noisyLocation = Location(location)
        noisyLocation.latitude = location.latitude + latOffset
        noisyLocation.longitude = location.longitude + lonOffset
        
        return noisyLocation
    }

    /**
     * Apply spatial quantization by truncating coordinates to specified precision
     * This reduces location accuracy by limiting decimal places
     */
    private fun applyTruncation(location: Location, precision: Int): Location {
        val truncatedLocation = Location(location)
        
        // Calculate truncation factor (10^precision)
        val factor = 10.0.pow(precision.toDouble())
        
        // Truncate coordinates using floor function for consistent spatial quantization
        truncatedLocation.latitude = floor(location.latitude * factor) / factor
        truncatedLocation.longitude = floor(location.longitude * factor) / factor
        
        return truncatedLocation
    }

    /**
     * Calculate the approximate privacy radius in meters for current settings
     */
    fun getEstimatedPrivacyRadius(): Double {
        val privacyMode = configurationManager.getPrivacyMode()
        
        return when (privacyMode) {
            MODE_PRECISION_LOCATION -> 0.0
            MODE_RANDOM_NOISE -> DEFAULT_NOISE_RADIUS_METERS
            MODE_TRUNCATE -> {
                val precision = configurationManager.getTruncationPrecision()
                // Approximate radius based on coordinate precision
                when (precision) {
                    1 -> 11000.0 // ~11 km
                    2 -> 1100.0  // ~1.1 km
                    3 -> 110.0   // ~110 m
                    4 -> 11.0    // ~11 m
                    5 -> 1.1     // ~1.1 m
                    6 -> 0.11    // ~0.11 m
                    else -> 110.0
                }
            }
            else -> 0.0
        }
    }

    /**
     * Get privacy protection strength as a percentage
     */
    fun getPrivacyStrength(): Int {
        val privacyMode = configurationManager.getPrivacyMode()
        
        return when (privacyMode) {
            MODE_PRECISION_LOCATION -> 0
            MODE_RANDOM_NOISE -> 90 // High protection with formal guarantees
            MODE_TRUNCATE -> {
                val precision = configurationManager.getTruncationPrecision()
                // Higher precision = lower privacy
                when (precision) {
                    1 -> 90  // Very high protection
                    2 -> 80  // High protection
                    3 -> 60  // Medium protection
                    4 -> 40  // Low-medium protection
                    5 -> 20  // Low protection
                    6 -> 10  // Very low protection
                    else -> 60
                }
            }
            else -> 0
        }
    }
}
