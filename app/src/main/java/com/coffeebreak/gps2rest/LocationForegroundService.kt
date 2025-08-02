package com.coffeebreak.gps2rest

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import okhttp3.*
import java.io.IOException

class LocationForegroundService : Service(), GpsService.LocationProvider {
    
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var gpsService: GpsService
    private lateinit var configManager: ConfigurationManager
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var connectivityManager: ConnectivityManager
    private var currentLocation: Location? = null
    private var locationCallback: LocationCallback? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "GPS_TRACKING_CHANNEL"
        private const val CHANNEL_NAME = "GPS Tracking"
        
        fun startService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java)
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        gpsService = GpsService(this)
        configManager = ConfigurationManager(this)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Acquire wake lock to prevent system from sleeping
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GPS2REST::LocationWakeLock"
        )
        wakeLock.acquire(10*60*1000L /*10 minutes*/)
        
        // Mark service as running
        configManager.setServiceRunning(true)
        
        createNotificationChannel()
        setupNetworkMonitoring()
        startLocationUpdates()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        // Note: GPS data transmission is handled by MainActivity's GpsService
        // This service only handles background location tracking for notifications
        
        return START_STICKY // Restart service if killed
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Clean up location tracking only
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        
        // Mark service as not running
        configManager.setServiceRunning(false)
        
        serviceScope.cancel()
    }
    
    private fun setupNetworkMonitoring() {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                updateNotification("Network available")
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                updateNotification("Network lost")
            }
            
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val hasValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                updateNotification("Network: ${if (hasInternet && hasValidated) "Connected" else "Limited"}")
            }
        }
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for GPS tracking service"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS2REST Tracking")
            .setContentText("Sending GPS data every ${configManager.getFrequencySeconds()} seconds")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            updateNotification("Location permission not granted")
            return
        }
        
        val frequencyMs = configManager.getFrequencySeconds() * 1000L
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            frequencyMs
        ).apply {
            setMinUpdateDistanceMeters(0f) // Get updates even if not moving
            setMaxUpdateDelayMillis(frequencyMs * 2) // Maximum delay
            setWaitForAccurateLocation(false) // Don't wait for accurate location
        }.build()
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    updateNotification(location)
                }
            }
        }
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            mainLooper
        )
    }
    
    private fun updateNotification(location: Location) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS2REST Tracking")
            .setContentText("Lat: ${String.format(java.util.Locale.US, "%.6f", location.latitude)}, Lon: ${String.format(java.util.Locale.US, "%.6f", location.longitude)}")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun updateNotification(message: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPS2REST Tracking")
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
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
                
                continuation.invokeOnCancellation {
                    // Handle cancellation if needed
                }
            }
        } else {
            null
        }
    }
}
