package com.coffeebreak.gps2rest

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConfigurationActivity : AppCompatActivity() {
    
    private lateinit var configManager: ConfigurationManager
    private lateinit var urlEditText: EditText
    private lateinit var startOnBootCheckBox: CheckBox
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration)
        
        configManager = ConfigurationManager(this)
        
        initViews()
        setupClickListeners()
        loadCurrentConfiguration()
    }
    
    private fun initViews() {
        urlEditText = findViewById(R.id.gpsUrlEditText)
        startOnBootCheckBox = findViewById(R.id.startOnBootCheckBox)
        saveButton = findViewById(R.id.saveButton)
        testButton = findViewById(R.id.testButton)
        
        // Set up action bar
        supportActionBar?.title = getString(R.string.configuration_activity_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }
    
    private fun setupClickListeners() {
        saveButton.setOnClickListener {
            saveConfiguration()
        }
        
        testButton.setOnClickListener {
            testConfiguration()
        }
    }
    
    private fun loadCurrentConfiguration() {
        val currentUrl = configManager.getGpsUrl()
        urlEditText.setText(currentUrl)
        
        val startOnBoot = configManager.shouldStartOnBoot()
        startOnBootCheckBox.isChecked = startOnBoot
        
        // Show current configuration status
        val savedUrl = configManager.getCurrentSavedUrl()
        if (savedUrl != "No URL saved (using default)") {
            Toast.makeText(this, "Current URL: $savedUrl", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveConfiguration() {
        val url = urlEditText.text.toString().trim()
        val startOnBoot = startOnBootCheckBox.isChecked

        if (url.isEmpty()) {
            Toast.makeText(this, getString(R.string.url_validation_error), Toast.LENGTH_SHORT).show()
            return
        }

        if (configManager.saveGpsUrl(url)) {
            configManager.setStartOnBoot(startOnBoot)
            Toast.makeText(this, getString(R.string.configuration_saved), Toast.LENGTH_SHORT).show()
            finish()
        } else {
            Toast.makeText(this, getString(R.string.url_validation_error), Toast.LENGTH_LONG).show()
        }
    }
    
    private fun testConfiguration() {
        val url = urlEditText.text.toString().trim()
        
        if (url.isEmpty()) {
            Toast.makeText(this, getString(R.string.url_validation_error), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Simple URL validation test
        try {
            java.net.URL(url)
            Toast.makeText(this, getString(R.string.test_successful), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.test_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
