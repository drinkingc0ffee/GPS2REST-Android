package com.rayhunter.gpssource

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
    }
    
    private fun saveConfiguration() {
        val url = urlEditText.text.toString().trim()

        if (url.isEmpty()) {
            Toast.makeText(this, "URL cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidUrl(url)) {
            Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
            return
        }

        configManager.saveGpsUrl(url)
        Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = java.net.URL(url)
            urlObj.protocol in listOf("http", "https") && !url.startsWith("/")
        } catch (e: Exception) {
            false
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
