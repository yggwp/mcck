package com.example.automocklocation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var adbManager: AdbManager
    private val PERMISSION_REQUEST_CODE = 100

    private lateinit var tvLog: TextView
    private lateinit var tvAdbStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        adbManager = AdbManager(this)
        
        tvLog = findViewById(R.id.tvLog)
        tvAdbStatus = findViewById(R.id.tvAdbStatus)

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST_CODE)
            }
        }

        // Initialize ADB binary
        CoroutineScope(Dispatchers.IO).launch {
            log("Extracting ADB binary...")
            val success = adbManager.extractAdb()
            withContext(Dispatchers.Main) {
                if (success) {
                    log("ADB extracted successfully.")
                } else {
                    log("Failed to extract ADB.")
                }
            }
        }

        setupButtons()
    }

    private fun setupButtons() {
        val etPairingPort = findViewById<EditText>(R.id.etPairingPort)
        val etPairingCode = findViewById<EditText>(R.id.etPairingCode)
        val etConnectPort = findViewById<EditText>(R.id.etConnectPort)
        val etLatitude = findViewById<EditText>(R.id.etLatitude)
        val etLongitude = findViewById<EditText>(R.id.etLongitude)

        val btnConnectAdb = findViewById<Button>(R.id.btnConnectAdb)
        val btnStartMock = findViewById<Button>(R.id.btnStartMock)
        val btnStopMock = findViewById<Button>(R.id.btnStopMock)

        btnConnectAdb.setOnClickListener {
            val pairingPort = etPairingPort.text.toString()
            val pairingCode = etPairingCode.text.toString()
            val connectPort = etConnectPort.text.toString()

            if (pairingPort.isEmpty() || pairingCode.isEmpty() || connectPort.isEmpty()) {
                Toast.makeText(this, "Mohon isi Port Pairing, Code, dan Port Koneksi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            tvAdbStatus.text = "Status: Menghubungkan..."
            tvAdbStatus.setTextColor(android.graphics.Color.BLUE)

            CoroutineScope(Dispatchers.IO).launch {
                log("Pairing dengan port $pairingPort...")
                val pairOut = adbManager.pair(pairingPort, pairingCode)
                log(pairOut)

                log("Connecting ke port $connectPort...")
                val connectOut = adbManager.connect(connectPort)
                log(connectOut)

                if (connectOut.contains("connected") || connectOut.contains("already connected")) {
                    log("Memberikan izin AppOps mock_location...")
                    val grantOut = adbManager.grantMockLocation(packageName)
                    log(grantOut)
                    
                    withContext(Dispatchers.Main) {
                        tvAdbStatus.text = "Status: ADB Terhubung & Izin Diberikan"
                        tvAdbStatus.setTextColor(android.graphics.Color.GREEN)
                        Toast.makeText(this@MainActivity, "Berhasil!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        tvAdbStatus.text = "Status: Gagal Terhubung"
                        tvAdbStatus.setTextColor(android.graphics.Color.RED)
                    }
                }
            }
        }

        btnStartMock.setOnClickListener {
            val latStr = etLatitude.text.toString()
            val lonStr = etLongitude.text.toString()

            if (latStr.isEmpty() || lonStr.isEmpty()) {
                Toast.makeText(this, "Isi Latitude & Longitude", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, LocationMockService::class.java).apply {
                putExtra("lat", latStr.toDoubleOrNull() ?: 0.0)
                putExtra("lon", lonStr.toDoubleOrNull() ?: 0.0)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Toast.makeText(this, "Mock Location Dimulai", Toast.LENGTH_SHORT).show()
            log("Mocking started at $latStr, $lonStr")
        }

        btnStopMock.setOnClickListener {
            val intent = Intent(this, LocationMockService::class.java)
            stopService(intent)
            Toast.makeText(this, "Mock Location Dihentikan", Toast.LENGTH_SHORT).show()
            log("Mocking stopped.")
        }
    }

    private fun log(msg: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val currentText = tvLog.text.toString()
            tvLog.text = "$msg\n$currentText"
        }
    }
}
