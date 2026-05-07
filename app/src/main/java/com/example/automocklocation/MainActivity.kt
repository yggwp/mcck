package com.example.automocklocation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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

    // Keep track of discovery listener to prevent leaks
    private var discoveryListener: NsdManager.DiscoveryListener? = null

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
        val etPairingCode = findViewById<EditText>(R.id.etPairingCode)
        val etLatitude = findViewById<EditText>(R.id.etLatitude)
        val etLongitude = findViewById<EditText>(R.id.etLongitude)

        val btnConnectAdb = findViewById<Button>(R.id.btnConnectAdb)
        val btnStopAdb = findViewById<Button>(R.id.btnStopAdb)
        val btnStartMock = findViewById<Button>(R.id.btnStartMock)
        val btnStopMock = findViewById<Button>(R.id.btnStopMock)

        btnConnectAdb.setOnClickListener {
            val pairingCode = etPairingCode.text.toString()
            discoverAndConnectAdb(pairingCode)
        }

        btnStopAdb.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                log("Memutuskan koneksi ADB...")
                adbManager.disconnect()
                adbManager.killServer()
                withContext(Dispatchers.Main) {
                    tvAdbStatus.text = "Status: ADB Diputus"
                    tvAdbStatus.setTextColor(android.graphics.Color.RED)
                    Toast.makeText(this@MainActivity, "ADB Server dimatikan", Toast.LENGTH_SHORT).show()
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

    private fun discoverAndConnectAdb(pairingCode: String) {
        tvAdbStatus.text = "Status: Mencari layanan ADB via mDNS..."
        tvAdbStatus.setTextColor(android.graphics.Color.BLUE)
        
        val nsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        
        if (pairingCode.isNotEmpty()) {
            log("Mencari layanan pairing...")
            discoverService(nsdManager, "_adb-tls-pairing._tcp.") { port ->
                CoroutineScope(Dispatchers.IO).launch {
                    log("Ditemukan port pairing: $port. Melakukan pairing...")
                    val pairOut = adbManager.pair(port.toString(), pairingCode)
                    log(pairOut)
                    
                    withContext(Dispatchers.Main) {
                        log("Mencari layanan connect...")
                        discoverService(nsdManager, "_adb-tls-connect._tcp.") { connectPort ->
                            connectToAdb(connectPort.toString())
                        }
                    }
                }
            }
        } else {
            log("Mencari layanan connect...")
            discoverService(nsdManager, "_adb-tls-connect._tcp.") { connectPort ->
                connectToAdb(connectPort.toString())
            }
        }
    }

    private fun discoverService(nsdManager: NsdManager, serviceType: String, onPortFound: (Int) -> Unit) {
        // Stop previous discovery if exists
        discoveryListener?.let { 
            try { nsdManager.stopServiceDiscovery(it) } catch (e: Exception) {} 
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                log("Pencarian $serviceType dimulai")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                log("Layanan ditemukan: ${service.serviceName}")
                try {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            log("Gagal me-resolve layanan: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            log("Berhasil me-resolve: ${serviceInfo.port}")
                            // We found it, stop discovery
                            try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (e: Exception) {}
                            
                            // Callback on Main Thread
                            CoroutineScope(Dispatchers.Main).launch {
                                onPortFound(serviceInfo.port)
                            }
                        }
                    })
                } catch (e: Exception) {
                    log("Resolve error: ${e.message}")
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                log("Layanan hilang: $service")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                log("Pencarian dihentikan")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                log("Gagal memulai pencarian: $errorCode")
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                log("Gagal menghentikan pencarian: $errorCode")
            }
        }
        
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun connectToAdb(connectPort: String) {
        CoroutineScope(Dispatchers.IO).launch {
            log("Connecting ke port $connectPort...")
            val connectOut = adbManager.connect(connectPort)
            log(connectOut)

            if (connectOut.contains("connected") || connectOut.contains("already connected") || connectOut.contains("failed to authenticate to")) {
                // Sometime "failed to authenticate" means it's connected but waiting for authorization, or already authenticated.
                // We will attempt to grant anyway.
                log("Memberikan izin AppOps mock_location...")
                val grantOut = adbManager.grantMockLocation(packageName)
                log(grantOut)
                
                withContext(Dispatchers.Main) {
                    tvAdbStatus.text = "Status: ADB Terhubung"
                    tvAdbStatus.setTextColor(android.graphics.Color.GREEN)
                    Toast.makeText(this@MainActivity, "Berhasil Menyambung!", Toast.LENGTH_SHORT).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    tvAdbStatus.text = "Status: Gagal Terhubung"
                    tvAdbStatus.setTextColor(android.graphics.Color.RED)
                }
            }
        }
    }

    private fun log(msg: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val currentText = tvLog.text.toString()
            tvLog.text = "$msg\n$currentText"
        }
    }
}
