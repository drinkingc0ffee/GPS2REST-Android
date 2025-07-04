package com.rayhunter.gpssource

import android.content.Context
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import okhttp3.*
import java.io.IOException
import java.util.*
import javax.net.SocketFactory

class GpsService(private val context: Context) {
    
    private val configManager = ConfigurationManager(context)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiNetwork = connectivityManager.allNetworks.firstOrNull {
        connectivityManager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private val httpClient = OkHttpClient.Builder()
        .socketFactory(wifiNetwork?.socketFactory ?: SocketFactory.getDefault())
        .dns(Dns.SYSTEM) // Use system DNS
        .build()
    private var gpsJob: Job? = null
    
    private val _statusMessages = MutableLiveData<List<String>>()
    val statusMessages: LiveData<List<String>> = _statusMessages
    
    private val statusBuffer = LinkedList<String>()
    private val maxStatusLines = 5
    
    fun startGpsUpdates(locationProvider: LocationProvider) {
        addStatusMessage("Starting GPS service...")
        
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
    }
    
    fun stopGpsUpdates() {
        gpsJob?.cancel()
        addStatusMessage("GPS service stopped")
    }
    
    private suspend fun sendGpsData(location: Location) {
        val baseUrl = configManager.getGpsUrl()

        // Validate and sanitize the base URL
        val sanitizedBaseUrl = baseUrl.trimEnd('/')
        val requestUrl = "$sanitizedBaseUrl/${location.latitude},${location.longitude}"

        addStatusMessage("Sending to: $requestUrl")

        val request = Request.Builder()
            .url(requestUrl)
            .post(RequestBody.create(null, "")) // Ensure POST request
            .build()

        try {
            val response = httpClient.newCall(request).execute()
            val sourceIp = response.networkResponse?.request?.url?.host ?: "Unknown"
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: "No response body"
                addStatusMessage("✓ GPS data sent successfully from IP: $sourceIp. Server response: $responseBody")
            } else {
                val responseBody = response.body?.string() ?: "No response body"
                addStatusMessage("✗ Server error: ${response.code} from IP: $sourceIp. Server response: $responseBody")
            }
            response.close()
        } catch (e: IOException) {
            addStatusMessage("✗ Network error: ${e.message}")
        } catch (e: Exception) {
            addStatusMessage("✗ Error: ${e.message}")
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
