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
    private lateinit var enableJwtCheckBox: CheckBox
    private lateinit var saveButton: Button
    private lateinit var testButton: Button
    private lateinit var cancelButton: Button
    private lateinit var encryptionPinEditText: EditText
    private lateinit var savePinButton: Button
    private lateinit var pinStatusText: TextView
    // Privacy UI components
    private lateinit var privacyModeRadioGroup: RadioGroup
    private lateinit var randomNoiseRadio: RadioButton
    private lateinit var truncateRadio: RadioButton
    private lateinit var originalRadio: RadioButton
    private lateinit var truncationSpinner: Spinner
    private lateinit var privacyStatusText: TextView

    private var jwtConfigured: Boolean = false
    private var changePinButton: Button? = null

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
        enableJwtCheckBox = findViewById(R.id.enableJwtCheckBox)
        saveButton = findViewById(R.id.saveButton)
        testButton = findViewById(R.id.testButton)
        cancelButton = findViewById(R.id.cancelButton)
        encryptionPinEditText = findViewById(R.id.encryptionPinEditText)
        savePinButton = findViewById(R.id.savePinButton)
        pinStatusText = findViewById(R.id.pinStatusText)
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
        savePinButton.setOnClickListener {
            val pin = encryptionPinEditText.text.toString().trim()
            if (pin.length != 8 || !pin.all { it.isDigit() }) {
                pinStatusText.setTextColor(Color.RED)
                pinStatusText.text = "PIN must be exactly 8 digits."
                return@setOnClickListener
            }
            val derivedKey = KdfUtil.deriveKeyFromPin(pin)
            configManager.setEncryptionKey(derivedKey)
            pinStatusText.setTextColor(Color.GREEN)
            pinStatusText.text = "PIN set successfully. JWT crypto configured."
            jwtConfigured = true
            updatePinUiState()
        }
        saveButton.setOnClickListener {
            saveConfiguration()
        }
        
        testButton.setOnClickListener {
            testConfiguration()
        }
        
        cancelButton.setOnClickListener {
            loadCurrentConfiguration()
            Toast.makeText(this, "Changes discarded", Toast.LENGTH_SHORT).show()
        }
        
        enableJwtCheckBox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val key = configManager.getEncryptionKey()
                if (key != null && key.size == 32) {
                    // Do not show any message if key is set
                } else {
                    Toast.makeText(this, "No JWT key installed. Please enter an 8-digit PIN.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun setupPrivacyControls() {
        // Set up truncation spinner
        val truncationOptions = PrivacyManager.getTruncationOptions()
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
                R.id.originalRadio -> { // Update to reflect 'Precision Location'
                    truncationSpinner.isEnabled = false
                    updatePrivacyStatus()
                }
            }
        }

        // Set up spinner listener
        truncationSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View,
                position: Int,
                id: Long
            ) {
                val precision = truncationOptions[position].first
                configManager.setTruncationPrecision(precision)
                updatePrivacyStatus()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }
    
    private fun updatePrivacyStatus() {
        val currentMode = configManager.getPrivacyMode()
        val truncationPrecision = configManager.getTruncationPrecision()
        
        val description = PrivacyManager.getPrivacyModeDescription(currentMode)
        val privacyLevel = PrivacyManager.getPrivacyLevel(currentMode)
        val radiusText = PrivacyManager.getPrivacyRadius(currentMode, truncationPrecision)
        
        // Get numeric privacy level for color coding
        val numericPrivacyLevel = privacyManager.getPrivacyStrength()
        
        // Create detailed status text
        val statusText = buildString {
            append("Current: $description\n")
            append("Privacy Level: $privacyLevel\n")
            append("Protection Radius: $radiusText")
        }
        
        privacyStatusText.text = statusText
        
        // Color code based on privacy level
        val textColor = when {
            numericPrivacyLevel >= 70 -> Color.GREEN
            numericPrivacyLevel >= 40 -> Color.rgb(255, 165, 0) // Orange
            numericPrivacyLevel > 0 -> Color.rgb(255, 140, 0) // Dark orange
            else -> Color.RED
        }
        privacyStatusText.setTextColor(textColor)
    }
    
    private fun loadCurrentConfiguration() {
        // Show key status and update UI for JWT config
        val key = configManager.getEncryptionKey()
        jwtConfigured = key != null && key.size == 32
        updatePinUiState()
        val currentUrl = configManager.getGpsUrl()
        urlEditText.setText(currentUrl)
        
        val startOnBoot = configManager.shouldStartOnBoot()
        startOnBootCheckBox.isChecked = startOnBoot

        val enableJwt = configManager.isJwtEnabled()
        enableJwtCheckBox.isChecked = enableJwt
        
        // Set privacy mode
        when (configManager.getPrivacyMode()) {
            ConfigurationManager.PRIVACY_MODE_RANDOM_NOISE -> {
                privacyModeRadioGroup.check(R.id.randomNoiseRadio)
                truncationSpinner.isEnabled = false
            }
            ConfigurationManager.PRIVACY_MODE_TRUNCATE -> {
                privacyModeRadioGroup.check(R.id.truncateRadio)
                truncationSpinner.isEnabled = true
            }
            ConfigurationManager.PRIVACY_MODE_ORIGINAL -> {
                privacyModeRadioGroup.check(R.id.originalRadio)
                truncationSpinner.isEnabled = false
            }
        }

        // Update privacy status
        updatePrivacyStatus()

        // Show current configuration status
        val savedUrl = configManager.getCurrentSavedUrl()
        if (savedUrl != "No URL saved (using default)") {
            Toast.makeText(this, "Current URL: $savedUrl", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updatePinUiState() {
        val parent = pinStatusText.parent as? LinearLayout
        val key = configManager.getEncryptionKey()
        val keyInstalled = key != null && key.size == 32
        if (keyInstalled) {
            encryptionPinEditText.visibility = View.GONE
            savePinButton.visibility = View.GONE
            pinStatusText.setTextColor(Color.GREEN)
            pinStatusText.text = "Encryption key is installed. JWT crypto is configured."
            if (changePinButton == null && parent != null) {
                changePinButton = Button(this).apply {
                    id = View.generateViewId()
                    text = "Change PIN"
                    setOnClickListener {
                        encryptionPinEditText.text.clear()
                        encryptionPinEditText.visibility = View.VISIBLE
                        savePinButton.visibility = View.VISIBLE
                        this.visibility = View.GONE
                        pinStatusText.setTextColor(Color.RED)
                        pinStatusText.text = "Enter a new 8-digit PIN to update."
                    }
                }
                val index = parent.indexOfChild(pinStatusText)
                parent.addView(changePinButton, index + 1)
            }
            changePinButton?.visibility = View.VISIBLE
        } else {
            encryptionPinEditText.visibility = View.VISIBLE
            savePinButton.visibility = View.VISIBLE
            pinStatusText.setTextColor(Color.RED)
            pinStatusText.text = "Encryption key not set. Please enter an 8-digit PIN."
            changePinButton?.visibility = View.GONE
        }
    }
    
    private fun saveConfiguration() {
        val url = urlEditText.text.toString().trim()
        val startOnBoot = startOnBootCheckBox.isChecked
        val enableJwt = enableJwtCheckBox.isChecked

        if (url.isEmpty()) {
            Toast.makeText(this, getString(R.string.url_validation_error), Toast.LENGTH_SHORT).show()
            return
        }

        if (configManager.saveGpsUrl(url)) {
            configManager.setStartOnBoot(startOnBoot)
            configManager.setEnableJwt(enableJwt)

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
                val options = PrivacyManager.getTruncationOptions()
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
