package com.coffeebreak.gps2rest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
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
    
    private var currentLocation: Location? = null
    
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 1002
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1003
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        initServices()
        setupClickListeners()
        checkLocationPermission()
    }
    
    private fun initViews() {
        configureButton = findViewById(R.id.configureButton)
        exitButton = findViewById(R.id.exitButton)
        locationText = findViewById(R.id.locationText)
        statusText = findViewById(R.id.statusText)
        frequencySeekBar = findViewById(R.id.frequencySeekBar)
        frequencyText = findViewById(R.id.frequencyText)
    }
    
    private fun initServices() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        gpsService = GpsService(this)
        configManager = ConfigurationManager(this)
        
        // Observe status messages
        gpsService.statusMessages.observe(this) { messages ->
            statusText.text = messages.joinToString("\n")
        }
        
        // Initialize frequency slider
        setupFrequencySlider()
    }
    
    private fun setupClickListeners() {
        configureButton.setOnClickListener {
            openConfiguration()
        }
        
        exitButton.setOnClickListener {
            exitApplication()
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
    
    private fun checkLocationPermission() {
        when {
            // Check if we have fine location permission
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED -> {
                requestLocationPermission()
            }
            
            // Check notification permission (Android 13+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED -> {
                requestNotificationPermission()
            }
            
            // Check background location permission (Android 10+)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED -> {
                requestBackgroundLocationPermission()
            }
            
            else -> {
                startLocationUpdates()
                promptBatteryOptimization()
            }
        }
    }
    
    private fun requestLocationPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            showUniformPermissionDialog(
                title = getString(R.string.location_permission_title),
                message = getString(R.string.location_permission_rationale),
                permissions = arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                requestCode = LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                showUniformPermissionDialog(
                    title = getString(R.string.notification_permission_title),
                    message = getString(R.string.notification_permission_rationale),
                    permissions = arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    requestCode = NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                showUniformPermissionDialog(
                    title = getString(R.string.background_location_title),
                    message = getString(R.string.background_location_rationale),
                    permissions = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    requestCode = BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                )
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    private fun showUniformPermissionDialog(
        title: String,
        message: String,
        permissions: Array<String>,
        requestCode: Int
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.grant_permission_button)) { _, _ ->
                ActivityCompat.requestPermissions(this, permissions, requestCode)
            }
            .setNegativeButton(getString(R.string.cancel_button)) { _, _ ->
                showUniformDenialToast(requestCode)
            }
            .setCancelable(false)
            .show()
    }
    
    private fun showUniformDenialToast(requestCode: Int) {
        val message = when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> getString(R.string.location_permission_denied_message)
            NOTIFICATION_PERMISSION_REQUEST_CODE -> getString(R.string.notification_permission_denied_message)
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> getString(R.string.background_location_denied_message)
            else -> getString(R.string.location_permission_denied)
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    private fun promptBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            if (intent.resolveActivity(packageManager) != null) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.battery_optimization_title))
                    .setMessage(getString(R.string.battery_optimization_message))
                    .setPositiveButton(getString(R.string.settings_button)) { _, _ ->
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this, getString(R.string.battery_settings_error), Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton(getString(R.string.skip_button)) { _, _ -> }
                    .show()
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        val isGranted = grantResults.isNotEmpty() && 
                       grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (isGranted) {
                    Toast.makeText(this, getString(R.string.location_permission_granted), Toast.LENGTH_SHORT).show()
                    checkLocationPermission() // Continue to next permission
                } else {
                    Toast.makeText(this, getString(R.string.location_permission_denied_message), Toast.LENGTH_LONG).show()
                    // App cannot function without location
                }
            }
            
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (isGranted) {
                    Toast.makeText(this, getString(R.string.notification_permission_granted), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.notification_permission_denied_message), Toast.LENGTH_SHORT).show()
                }
                checkLocationPermission() // Continue to next permission regardless
            }
            
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                if (isGranted) {
                    Toast.makeText(this, getString(R.string.background_location_granted), Toast.LENGTH_SHORT).show()
                    startLocationUpdates()
                    promptBatteryOptimization()
                    
                    // Final acknowledgment that everything is ready
                    Handler(mainLooper).postDelayed({
                        Toast.makeText(
                            this,
                            getString(R.string.all_permissions_granted),
                            Toast.LENGTH_LONG
                        ).show()
                    }, 1000)
                } else {
                    Toast.makeText(this, getString(R.string.background_location_denied_message), Toast.LENGTH_SHORT).show()
                    startLocationUpdates() // Start anyway but limited functionality
                }
            }
        }
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
            
            // Start GPS service
            gpsService.startGpsUpdates(this)
            
            // Start foreground service for background tracking
            LocationForegroundService.startService(this)
        }
    }
    
    private fun updateLocationDisplay(location: Location) {
        val locationString = "Lat: ${String.format("%.6f", location.latitude)}, " +
                "Lon: ${String.format("%.6f", location.longitude)}\n" +
                "Accuracy: ${String.format("%.1f", location.accuracy)}m"
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
                exitApplication()
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
        gpsService.stopGpsUpdates()
        LocationForegroundService.stopService(this)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        gpsService.stopGpsUpdates()
        LocationForegroundService.stopService(this)
    }
}
