package com.coffeebreak.gps2rest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MainActivity : AppCompatActivity(), GpsService.LocationProvider {
    
    private lateinit var configureButton: Button
    private lateinit var exitButton: Button
    private lateinit var locationText: TextView
    private lateinit var statusText: TextView
    private lateinit var frequencySeekBar: SeekBar
    private lateinit var frequencyText: TextView
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var gpsService: GpsService
    private lateinit var configManager: ConfigurationManager
    private lateinit var permissionManager: PermissionManager
    
    private var currentLocation: Location? = null
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        // REMOVED: BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE - no longer needed
        private const val BATTERY_OPTIMIZATION_REQUEST_CODE = 1003
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        permissionManager = PermissionManager(this)
        initViews()
        initBasicServices()
        setupClickListeners()
        checkAndRequestPermissions()
    }
    
    private fun initViews() {
        configureButton = findViewById(R.id.configureButton)
        exitButton = findViewById(R.id.exitButton)
        locationText = findViewById(R.id.locationText)
        statusText = findViewById(R.id.statusText)
        frequencySeekBar = findViewById(R.id.frequencySeekBar)
        frequencyText = findViewById(R.id.frequencyText)
    }
    
    private fun initBasicServices() {
        // Only initialize non-location services here
        configManager = ConfigurationManager(this)
        
        // Initialize frequency slider
        setupFrequencySlider()
    }
    
    private fun initLocationServices() {
        // Initialize location-related services only after permissions are granted
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        gpsService = GpsService(this)
        
        // Observe status messages
        gpsService.statusMessages.observe(this) { messages ->
            statusText.text = messages.joinToString("\n")
        }
    }
    
    private fun setupClickListeners() {
        configureButton.setOnClickListener {
            openConfiguration()
        }
        
        exitButton.setOnClickListener {
            showExitConfirmationDialog()
        }
    }
    
    private fun setupFrequencySlider() {
        val savedFrequency = configManager.getFrequencySeconds()
        
        // Convert seconds to slider position (0-100)
        // 10 seconds = 0, 30 minutes (1800 seconds) = 100
        val sliderPosition = when {
            savedFrequency <= 10 -> 0
            savedFrequency >= 1800 -> 100
            else -> {
                // Logarithmic scale for better UX
                val logMin = kotlin.math.ln(10.0)
                val logMax = kotlin.math.ln(1800.0)
                val logValue = kotlin.math.ln(savedFrequency.toDouble())
                ((logValue - logMin) / (logMax - logMin) * 100).toInt()
            }
        }
        
        frequencySeekBar.progress = sliderPosition
        updateFrequencyDisplay(savedFrequency)
        
        frequencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val frequency = progressToFrequency(progress)
                    updateFrequencyDisplay(frequency)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val frequency = progressToFrequency(seekBar?.progress ?: 0)
                configManager.saveFrequencySeconds(frequency)
                restartGpsService()
            }
        })
    }
    
    private fun progressToFrequency(progress: Int): Int {
        // Convert slider position (0-100) to seconds using logarithmic scale
        // 0 = 10 seconds, 100 = 1800 seconds (30 minutes)
        val logMin = kotlin.math.ln(10.0)
        val logMax = kotlin.math.ln(1800.0)
        val logValue = logMin + (progress / 100.0) * (logMax - logMin)
        return kotlin.math.exp(logValue).toInt()
    }
    
    private fun updateFrequencyDisplay(seconds: Int) {
        val displayText = when {
            seconds < 60 -> getString(R.string.frequency_seconds, seconds)
            seconds < 3600 -> {
                val minutes = seconds / 60
                val remainingSeconds = seconds % 60
                if (remainingSeconds == 0) {
                    getString(R.string.frequency_minutes, minutes)
                } else {
                    "${minutes}m ${remainingSeconds}s"
                }
            }
            else -> {
                val minutes = seconds / 60
                getString(R.string.frequency_minutes, minutes)
            }
        }
        frequencyText.text = displayText
    }
    
    private fun restartGpsService() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            gpsService.stopGpsUpdates()
            gpsService.startGpsUpdates(this)
        }
    }
    
    private fun checkAndRequestPermissions() {
        val basicLocationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        Log.d("MainActivity", "Checking basic location permissions")
        
        if (permissionManager.arePermissionsGranted(basicLocationPermissions)) {
            Log.d("MainActivity", "All basic permissions already granted")
            initLocationServices()
            checkBatteryOptimization() // Skip background location check
        } else {
            Log.d("MainActivity", "Showing permission explanation dialog")
            showPermissionExplanationDialog()
        }
    }
    
    // REMOVED: Background location permission no longer needed with foreground service
    // private fun checkBackgroundLocationPermission() { ... }
    // private fun showBackgroundLocationPermissionDialog() { ... }
    // private fun requestBackgroundLocationPermission() { ... }
    
    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            val packageName = packageName
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName) && 
                !configManager.isBatteryOptimizationRequested()) {
                Log.d("MainActivity", "Battery optimization not disabled")
                showBatteryOptimizationDialog()
            } else {
                Log.d("MainActivity", "Battery optimization already disabled or requested")
                startLocationUpdates()
            }
        } else {
            startLocationUpdates()
        }
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("🔋 Battery Optimization")
            .setMessage("For reliable background operation, GPS2REST needs to be excluded from battery optimization.\n\nThis prevents the system from killing the GPS tracking service to save battery.")
            .setPositiveButton("Disable Optimization") { _, _ ->
                Log.d("MainActivity", "User agreed to disable battery optimization")
                requestBatteryOptimizationExemption()
            }
            .setNegativeButton("Skip") { _, _ ->
                Log.d("MainActivity", "User skipped battery optimization exemption")
                startLocationUpdates()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, BATTERY_OPTIMIZATION_REQUEST_CODE)
                configManager.setBatteryOptimizationRequested(true)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error requesting battery optimization exemption", e)
                startLocationUpdates()
            }
        }
    }
    
    private fun showPermissionExplanationDialog() {
        AlertDialog.Builder(this)
            .setTitle("📍 Location Permission Required")
            .setMessage("GPS2REST needs access to your device's location to track and send GPS coordinates to your configured endpoint.\n\nThis permission is essential for the app to function.")
            .setPositiveButton("Grant Permission") { _, _ ->
                Log.d("MainActivity", "User agreed to grant permissions")
                requestBasicLocationPermissions()
            }
            .setNegativeButton("Exit") { _, _ ->
                Log.d("MainActivity", "User declined permissions")
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun requestBasicLocationPermissions() {
        val basicLocationPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        Log.d("MainActivity", "Requesting basic location permissions")
        permissionManager.requestMissingPermissions(basicLocationPermissions, LOCATION_PERMISSION_REQUEST_CODE)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        Log.d("MainActivity", "Permission result received for request code: $requestCode")
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                permissionManager.handlePermissionsResult(
                    requestCode,
                    permissions,
                    grantResults,
                    onPermissionsGranted = {
                        Log.d("MainActivity", "Location permissions granted")
                        initLocationServices()
                        checkBatteryOptimization() // Skip background location check
                        Toast.makeText(this, "✅ Location permission granted! App is ready.", Toast.LENGTH_SHORT).show()
                    },
                    onPermissionsDenied = {
                        Log.d("MainActivity", "Location permissions denied")
                        showPermissionDeniedDialog()
                    }
                )
            }
            // REMOVED: Background location permission case - no longer needed with foreground service
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            BATTERY_OPTIMIZATION_REQUEST_CODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                    val packageName = packageName
                    
                    if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                        Toast.makeText(this, "✅ Battery optimization disabled!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "⚠️ Battery optimization not disabled. App may not work reliably in background.", Toast.LENGTH_LONG).show()
                    }
                }
                startLocationUpdates()
            }
        }
    }
    
    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("❌ Permission Required")
            .setMessage("GPS2REST cannot function without location permission.\n\nPlease grant location permission in your device settings or restart the app to try again.")
            .setPositiveButton("Open Settings") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Please enable location permission in Settings", Toast.LENGTH_LONG).show()
                }
                finish()
            }
            .setNegativeButton("Exit App") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    private fun startLocationUpdates() {
        val frequencyMs = configManager.getFrequencySeconds() * 1000L
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            frequencyMs
        ).build()
        
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    updateLocationDisplay(location)
                }
            }
        }
        
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                mainLooper
            )
            
            // Start GPS service for REST API transmission
            gpsService.startGpsUpdates(this)
            
            // Start foreground service for background tracking
            LocationForegroundService.startService(this)
        }
    }
    
    private fun updateLocationDisplay(location: Location) {
        val locationString = "Lat: ${String.format(java.util.Locale.US, "%.6f", location.latitude)}, " +
                "Lon: ${String.format(java.util.Locale.US, "%.6f", location.longitude)}\n" +
                "Accuracy: ${String.format(java.util.Locale.US, "%.1f", location.accuracy)}m"
        locationText.text = locationString
    }
    
    override suspend fun getCurrentLocation(): Location? {
        return if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(location ?: currentLocation)
                    }
                    .addOnFailureListener {
                        continuation.resume(currentLocation)
                    }
            }
        } else {
            null
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_configure -> {
                openConfiguration()
                true
            }
            R.id.menu_about -> {
                showAboutDialog()
                true
            }
            R.id.menu_exit -> {
                showExitConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun openConfiguration() {
        val intent = Intent(this, ConfigurationActivity::class.java)
        startActivity(intent)
    }
    
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About GPS2REST-Android")
            .setMessage("GPS2REST-Android v1.0\n\nTracks GPS location at configurable intervals and sends coordinates to a REST endpoint.\n\nDefault endpoint: http://your-server.com:8080/api/v1/gps\n\nDeveloped for continuous GPS tracking with background operation support.")
            .setPositiveButton("OK") { _, _ -> }
            .show()
    }
    
    private fun exitApplication() {
        try {
            // Stop GPS service
            gpsService.stopGpsUpdates()
            
            // Stop foreground service
            LocationForegroundService.stopService(this)
            
            // Mark service as not running
            configManager.setServiceRunning(false)
            
            // Show exit message
            Toast.makeText(this, "GPS2REST stopped. Exiting...", Toast.LENGTH_SHORT).show()
            
            // Finish the activity
            finish()
            
            // Force exit the app (for older Android versions)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                finishAndRemoveTask()
            } else {
                @Suppress("DEPRECATION")
                finish()
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Error during exit", e)
            // Force finish even if there's an error
            finish()
        }
    }

    private fun showExitConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit GPS2REST")
            .setMessage("Are you sure you want to exit GPS2REST? All tracking services will be stopped.")
            .setPositiveButton("Yes, Exit") { _, _ ->
                exitApplication()
            }
            .setNegativeButton("Cancel") { _, _ -> }
            .setCancelable(false)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        gpsService.stopGpsUpdates()
        LocationForegroundService.stopService(this)
    }
    
    @Deprecated("Deprecated in API level 33")
    override fun onBackPressed() {
        showExitConfirmationDialog()
    }
}
