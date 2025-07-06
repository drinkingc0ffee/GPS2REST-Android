package com.coffeebreak.gps2rest

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(private val activity: Activity) {

    fun requestPermissions(permissions: Array<String>, requestCode: Int) {
        Log.d("PermissionManager", "Requesting permissions: ${permissions.joinToString(", ")}")
        ActivityCompat.requestPermissions(activity, permissions, requestCode)
    }

    fun arePermissionsGranted(permissions: Array<String>): Boolean {
        val result = permissions.all { permission ->
            val granted = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
            Log.d("PermissionManager", "Permission $permission: ${if (granted) "GRANTED" else "DENIED"}")
            granted
        }
        Log.d("PermissionManager", "All permissions granted: $result")
        return result
    }

    fun handlePermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onPermissionsGranted: () -> Unit,
        onPermissionsDenied: () -> Unit
    ) {
        Log.d("PermissionManager", "Handling permission result for request code: $requestCode")
        Log.d("PermissionManager", "Permissions: ${permissions.joinToString(", ")}")
        Log.d("PermissionManager", "Grant results: ${grantResults.joinToString(", ")}")
        
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            Log.d("PermissionManager", "All permissions granted")
            onPermissionsGranted()
        } else {
            Log.d("PermissionManager", "Some permissions denied")
            onPermissionsDenied()
        }
    }

    fun requestMissingPermissions(permissions: Array<String>, requestCode: Int) {
        val missingPermissions = permissions.filter {
            val granted = ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED
            Log.d("PermissionManager", "Checking permission $it: ${if (granted) "GRANTED" else "MISSING"}")
            !granted
        }

        Log.d("PermissionManager", "Missing permissions: ${missingPermissions.joinToString(", ")}")
        
        if (missingPermissions.isNotEmpty()) {
            Log.d("PermissionManager", "Requesting ${missingPermissions.size} missing permissions")
            ActivityCompat.requestPermissions(activity, missingPermissions.toTypedArray(), requestCode)
        } else {
            Log.d("PermissionManager", "No missing permissions")
        }
    }
} 