package com.coffeebreak.gps2rest

import android.content.Context
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.InetAddress
import java.util.*
import javax.net.SocketFactory

class GpsService(private val context: Context) {
    
    private val configManager = ConfigurationManager(context)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Default HTTP client for internet requests
    private val defaultHttpClient = OkHttpClient.Builder()
        .dns(Dns.SYSTEM)
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    // WiFi-specific HTTP client for local network requests
    private var wifiHttpClient: OkHttpClient? = null
    private var wifiNetwork: Network? = null
    
    private var gpsJob: Job? = null
    private var retryJob: Job? = null
    private val offlineQueue = mutableListOf<Location>()
    private val maxOfflineQueueSize = 100
    
    private val _statusMessages = MutableLiveData<List<String>>()
    val statusMessages: LiveData<List<String>> = _statusMessages
    
    private val statusBuffer = LinkedList<String>()
    private val maxStatusLines = 5
    
    fun startGpsUpdates(locationProvider: LocationProvider) {
        addStatusMessage("Starting GPS service...")
        initializeWifiClient()
        
        gpsJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val location = locationProvider.getCurrentLocation()
                    if (location != null) {
                        sendGpsData(location)
                    } else {
                        addStatusMessage("Location unavailable")
                    }
                } catch (e: Exception) {
                    addStatusMessage("Error: ${e.message}")
                }
                
                val frequencyMs = configManager.getFrequencySeconds() * 1000L
                delay(frequencyMs)
            }
        }
        
        // Start retry job for offline data
        startRetryJob()
    }
    
    fun stopGpsUpdates() {
        gpsJob?.cancel()
        retryJob?.cancel()
        addStatusMessage("GPS service stopped")
    }
    
    private fun startRetryJob() {
        retryJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    if (offlineQueue.isNotEmpty() && isNetworkAvailable()) {
                        addStatusMessage("Retrying ${offlineQueue.size} offline locations...")
                        retryOfflineData()
                    }
                } catch (e: Exception) {
                    addStatusMessage("Retry error: ${e.message}")
                }
                
                delay(60000) // Retry every minute
            }
        }
    }
    
    private suspend fun retryOfflineData() {
        val locationsToRetry = offlineQueue.toList()
        offlineQueue.clear()
        
        locationsToRetry.forEach { location ->
            try {
                sendGpsData(location, isRetry = true)
                delay(1000) // Small delay between retries
            } catch (e: Exception) {
                // If retry fails, add back to queue
                if (offlineQueue.size < maxOfflineQueueSize) {
                    offlineQueue.add(location)
                }
            }
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = activeNetwork?.let { 
            connectivityManager.getNetworkCapabilities(it) 
        }
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
               networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    private fun initializeWifiClient() {
        try {
            // Find active WiFi network
            val activeNetwork = connectivityManager.activeNetwork
            val activeNetworkCapabilities = activeNetwork?.let { 
                connectivityManager.getNetworkCapabilities(it) 
            }
            
            if (activeNetwork != null && activeNetworkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                wifiNetwork = activeNetwork
                wifiHttpClient = OkHttpClient.Builder()
                    .socketFactory(activeNetwork.socketFactory)
                    .dns(Dns.SYSTEM)
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
                addStatusMessage("WiFi network found for local requests")
            } else {
                // If active network is not WiFi, look for any WiFi network
                @Suppress("DEPRECATION")
                val networks = connectivityManager.allNetworks
                for (network in networks) {
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                    if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                        wifiNetwork = network
                        wifiHttpClient = OkHttpClient.Builder()
                            .socketFactory(network.socketFactory)
                            .dns(Dns.SYSTEM)
                            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build()
                        addStatusMessage("WiFi network found for local requests")
                        break
                    }
                }
            }
            
            if (wifiNetwork == null) {
                addStatusMessage("No WiFi network found - using default routing")
            }
        } catch (e: Exception) {
            addStatusMessage("Error initializing WiFi client: ${e.message}")
        }
    }
    
    private fun isPrivateIpAddress(host: String): Boolean {
        return try {
            val address = InetAddress.getByName(host)
            val ip = address.hostAddress ?: return false
            
            // Check for private IP ranges
            ip.startsWith("192.168.") ||
            ip.startsWith("10.") ||
            ip.startsWith("172.16.") ||
            ip.startsWith("172.17.") ||
            ip.startsWith("172.18.") ||
            ip.startsWith("172.19.") ||
            ip.startsWith("172.20.") ||
            ip.startsWith("172.21.") ||
            ip.startsWith("172.22.") ||
            ip.startsWith("172.23.") ||
            ip.startsWith("172.24.") ||
            ip.startsWith("172.25.") ||
            ip.startsWith("172.26.") ||
            ip.startsWith("172.27.") ||
            ip.startsWith("172.28.") ||
            ip.startsWith("172.29.") ||
            ip.startsWith("172.30.") ||
            ip.startsWith("172.31.") ||
            ip.startsWith("127.") ||
            ip == "localhost"
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getHttpClient(url: String): OkHttpClient {
        return try {
            val urlObj = java.net.URL(url)
            val host = urlObj.host
            
            if (isPrivateIpAddress(host) && wifiHttpClient != null) {
                addStatusMessage("Using WiFi network for local address: $host")
                wifiHttpClient!!
            } else {
                addStatusMessage("Using default network for address: $host")
                defaultHttpClient
            }
        } catch (e: Exception) {
            addStatusMessage("Error parsing URL, using default client: ${e.message}")
            defaultHttpClient
        }
    }
    
    private suspend fun sendGpsData(location: Location, isRetry: Boolean = false) {
        val baseUrl = configManager.getGpsUrl()

        // Validate and construct the request URL
        val requestUrl = try {
            val sanitizedBaseUrl = baseUrl.trimEnd('/')
            val coordinates = "${location.latitude},${location.longitude}"
            val fullUrl = "$sanitizedBaseUrl/$coordinates"
            
            // Validate the URL before using it
            java.net.URL(fullUrl)
            fullUrl
        } catch (e: Exception) {
            addStatusMessage("✗ Invalid URL configuration: $baseUrl")
            return
        }

        if (!isRetry) {
            addStatusMessage("Sending to: $requestUrl")
        }

        // Check network availability
        if (!isNetworkAvailable()) {
            if (offlineQueue.size < maxOfflineQueueSize) {
                offlineQueue.add(location)
                addStatusMessage("⚠️ Network unavailable, queued location (${offlineQueue.size}/${maxOfflineQueueSize})")
            } else {
                addStatusMessage("⚠️ Network unavailable, queue full, dropping location")
            }
            return
        }

        // Get the appropriate HTTP client based on the target address
        val httpClient = getHttpClient(requestUrl)

        val request = Request.Builder()
            .url(requestUrl)
            .post("".toRequestBody()) // Ensure POST request
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val serverHost = response.request.url.host
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "No response body"
                val prefix = if (isRetry) "✓ Retry" else "✓"
                addStatusMessage("$prefix GPS data sent successfully to $serverHost. Server response: $responseBody")
            } else {
                val responseBody = response.body?.string() ?: "No response body"
                addStatusMessage("✗ Server error: ${response.code} from $serverHost. Server response: $responseBody")
                
                // Queue for retry on server errors
                if (!isRetry && offlineQueue.size < maxOfflineQueueSize) {
                    offlineQueue.add(location)
                    addStatusMessage("⚠️ Queued for retry due to server error")
                }
            }
            response.close()
        } catch (e: IOException) {
            addStatusMessage("✗ Network error: ${e.message}")
            
            // Queue for retry on network errors
            if (!isRetry && offlineQueue.size < maxOfflineQueueSize) {
                offlineQueue.add(location)
                addStatusMessage("⚠️ Queued for retry due to network error")
            }
        } catch (e: Exception) {
            addStatusMessage("✗ Error: ${e.message}")
            
            // Queue for retry on other errors
            if (!isRetry && offlineQueue.size < maxOfflineQueueSize) {
                offlineQueue.add(location)
                addStatusMessage("⚠️ Queued for retry due to error")
            }
        }
    }
    
    private fun addStatusMessage(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            .format(Date())
        val timestampedMessage = "[$timestamp] $message"

        synchronized(statusBuffer) {
            if (statusBuffer.size >= maxStatusLines) {
                statusBuffer.removeFirst()
            }
            statusBuffer.addLast(timestampedMessage)

            // Update LiveData on main thread
            CoroutineScope(Dispatchers.Main).launch {
                _statusMessages.value = ArrayList(statusBuffer)
            }
        }

        // Log the message for debugging purposes
        println("Status Update: $timestampedMessage")
    }
    
    interface LocationProvider {
        suspend fun getCurrentLocation(): Location?
    }
}
