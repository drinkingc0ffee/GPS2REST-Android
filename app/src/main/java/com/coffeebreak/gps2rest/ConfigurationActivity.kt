package com.coffeebreak.gps2rest

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConfigurationActivity : AppCompatActivity() {
    
    private lateinit var configManager: ConfigurationManager
    private lateinit var urlEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration)
        
        configManager = ConfigurationManager(this)
        
        initViews()
        setupClickListeners()
        loadCurrentConfiguration()
    }
    
    private fun initViews() {
        urlEditText = findViewById(R.id.urlEditText)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        
        // Set up action bar
        supportActionBar?.title = getString(R.string.configuration_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupClickListeners() {
        saveButton.setOnClickListener {
            saveConfiguration()
        }
        
        cancelButton.setOnClickListener {
            finish()
        }
    }
    
    private fun loadCurrentConfiguration() {
        val currentUrl = configManager.getGpsUrl()
        urlEditText.setText(currentUrl)
        
        // Show current configuration status
        val savedUrl = configManager.getCurrentSavedUrl()
        if (savedUrl != "No URL saved (using default)") {
            Toast.makeText(this, "Current URL: $savedUrl", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveConfiguration() {
        val url = urlEditText.text.toString().trim()

        if (url.isEmpty()) {
            Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (configManager.saveGpsUrl(url)) {
            Toast.makeText(this, "Configuration saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, "Invalid URL. Please enter a valid URL like: http://192.168.1.1:8080/api/v1/gps", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
