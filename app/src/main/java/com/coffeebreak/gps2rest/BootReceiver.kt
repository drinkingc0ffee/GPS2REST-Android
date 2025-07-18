package com.coffeebreak.gps2rest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.d("BootReceiver", "Device boot completed or app updated, checking if service should start")
                
                // Check if the app was configured to run on boot
                val configManager = ConfigurationManager(context)
                if (configManager.shouldStartOnBoot()) {
                    Log.d("BootReceiver", "Starting location service on boot")
                    
                    // Start the location service
                    LocationForegroundService.startService(context)
                } else {
                    Log.d("BootReceiver", "Service not configured to start on boot")
                }
            }
        }
    }
} 