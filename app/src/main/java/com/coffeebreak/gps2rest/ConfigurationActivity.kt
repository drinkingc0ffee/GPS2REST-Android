package com.coffeebreak.gps2rest

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class ConfigurationActivity : AppCompatActivity() {
    
    private lateinit var configManager: ConfigurationManager
    private lateinit var privacyManager: PrivacyManager
    private lateinit var urlEditText: EditText
    private lateinit var startOnBootCheckBox: CheckBox
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    
    // Privacy UI components
    private lateinit var privacyModeRadioGroup: RadioGroup
    private lateinit var randomNoiseRadio: RadioButton
    private lateinit var truncateRadio: RadioButton
    private lateinit var originalRadio: RadioButton
    private lateinit var truncationSpinner: Spinner
    private lateinit var privacyStatusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuration)
        
        configManager = ConfigurationManager(this)
        privacyManager = PrivacyManager(configManager)
        
        initViews()
        setupClickListeners()
        setupPrivacyControls()
        loadCurrentConfiguration()
    }
    
    private fun initViews() {
        urlEditText = findViewById(R.id.gpsUrlEditText)
        startOnBootCheckBox = findViewById(R.id.startOnBootCheckBox)
        saveButton = findViewById(R.id.saveButton)
        testButton = findViewById(R.id.testButton)
        
        // Privacy UI components
        privacyModeRadioGroup = findViewById(R.id.privacyModeRadioGroup)
        randomNoiseRadio = findViewById(R.id.randomNoiseRadio)
        truncateRadio = findViewById(R.id.truncateRadio)
        originalRadio = findViewById(R.id.originalRadio)
        truncationSpinner = findViewById(R.id.truncationSpinner)
        privacyStatusText = findViewById(R.id.privacyStatusText)
        
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
    
    private fun setupPrivacyControls() {
        // Set up truncation spinner
        val truncationOptions = privacyManager.getTruncationOptions()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            truncationOptions.map { it.second }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        truncationSpinner.adapter = adapter
        
        // Set up radio group listener
        privacyModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.randomNoiseRadio -> {
                    truncationSpinner.isEnabled = false
                    updatePrivacyStatus()
                }
                R.id.truncateRadio -> {
                    truncationSpinner.isEnabled = true
                    updatePrivacyStatus()
                }
                R.id.originalRadio -> {
                    truncationSpinner.isEnabled = false
                    updatePrivacyStatus()
                }
            }
        }
        
        // Set up spinner listener
        truncationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePrivacyStatus()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun updatePrivacyStatus() {
        val currentMode = when {
            randomNoiseRadio.isChecked -> "Random Noise (±111m)"
            truncateRadio.isChecked -> {
                val options = privacyManager.getTruncationOptions()
                val selectedPosition = truncationSpinner.selectedItemPosition
                if (selectedPosition >= 0 && selectedPosition < options.size) {
                    val precision = options[selectedPosition].first
                    privacyManager.getTruncationAccuracyDescription(precision)
                } else "Truncated coordinates"
            }
            originalRadio.isChecked -> "Original coordinates - No privacy protection"
            else -> "Unknown"
        }
        privacyStatusText.text = "Current: $currentMode"
    }
    
    private fun loadCurrentConfiguration() {
        val currentUrl = configManager.getGpsUrl()
        urlEditText.setText(currentUrl)
        
        val startOnBoot = configManager.shouldStartOnBoot()
        startOnBootCheckBox.isChecked = startOnBoot
        
        // Load privacy settings
        val currentPrivacyMode = configManager.getPrivacyMode()
        when (currentPrivacyMode) {
            ConfigurationManager.PRIVACY_MODE_RANDOM_NOISE -> {
                randomNoiseRadio.isChecked = true
                truncationSpinner.isEnabled = false
            }
            ConfigurationManager.PRIVACY_MODE_TRUNCATE -> {
                truncateRadio.isChecked = true
                truncationSpinner.isEnabled = true
                // Set spinner to current precision
                val currentPrecision = configManager.getTruncationPrecision()
                val options = privacyManager.getTruncationOptions()
                val position = options.indexOfFirst { it.first == currentPrecision }
                if (position >= 0) {
                    truncationSpinner.setSelection(position)
                }
            }
            ConfigurationManager.PRIVACY_MODE_ORIGINAL -> {
                originalRadio.isChecked = true
                truncationSpinner.isEnabled = false
            }
        }
        
        updatePrivacyStatus()
        
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
            
            // Save privacy settings
            val selectedPrivacyMode = when {
                randomNoiseRadio.isChecked -> ConfigurationManager.PRIVACY_MODE_RANDOM_NOISE
                truncateRadio.isChecked -> ConfigurationManager.PRIVACY_MODE_TRUNCATE
                originalRadio.isChecked -> ConfigurationManager.PRIVACY_MODE_ORIGINAL
                else -> ConfigurationManager.PRIVACY_MODE_RANDOM_NOISE // Default
            }
            configManager.setPrivacyMode(selectedPrivacyMode)
            
            // Save truncation precision if applicable
            if (truncateRadio.isChecked) {
                val options = privacyManager.getTruncationOptions()
                val selectedPosition = truncationSpinner.selectedItemPosition
                if (selectedPosition >= 0 && selectedPosition < options.size) {
                    val precision = options[selectedPosition].first
                    configManager.setTruncationPrecision(precision)
                }
            }
            
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
