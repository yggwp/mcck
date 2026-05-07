package com.example.automocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LocationMockService : Service() {

    private val CHANNEL_ID = "MockLocationServiceChannel"
    private var isMocking = false
    private var mockJob: Job? = null
    
    private var lat = 0.0
    private var lon = 0.0
    private lateinit var adbManager: AdbManager

    // Configuration from v4.bash
    private val injectInterval = 100L // 0.1 detik
    private val burstSize = 3
    private val restartDuration = 100L // 0.1 detik

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        adbManager = AdbManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lat = intent?.getDoubleExtra("lat", 0.0) ?: 0.0
        lon = intent?.getDoubleExtra("lon", 0.0) ?: 0.0

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock Location Active")
            .setContentText("Spoofing location via ADB to: $lat, $lon")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()

        startForeground(1, notification)
        
        startMocking()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMocking()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startMocking() {
        isMocking = true

        mockJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // [1] Setup mock location permission
                Log.d("MockService", "Setting AppOps permission...")
                adbManager.shellCommand(listOf("appops", "set", "com.android.shell", "android:mock_location", "allow"))

                // [2] Add test providers
                Log.d("MockService", "Adding test providers...")
                adbManager.shellCommand(listOf("cmd", "location", "providers", "add-test-provider", "gps"))
                adbManager.shellCommand(listOf("cmd", "location", "providers", "add-test-provider", "network"))

                // [3] Enable test providers
                Log.d("MockService", "Enabling test providers...")
                adbManager.shellCommand(listOf("cmd", "location", "providers", "set-test-provider-enabled", "gps", "true"))
                adbManager.shellCommand(listOf("cmd", "location", "providers", "set-test-provider-enabled", "network", "true"))

                // [4] Ensure location service is on
                Log.d("MockService", "Enabling location service...")
                adbManager.shellCommand(listOf("cmd", "location", "set-location-enabled", "true"))

                // [5] Start loop
                Log.d("MockService", "Starting burst loop...")
                while (isMocking) {
                    // Burst inject
                    for (i in 1..burstSize) {
                        adbManager.shellCommand(listOf("cmd", "location", "providers", "set-test-provider-location", "gps", "--location", "$lat,$lon"))
                        adbManager.shellCommand(listOf("cmd", "location", "providers", "set-test-provider-location", "network", "--location", "$lat,$lon"))
                        delay(injectInterval)
                    }

                    // Restart location service
                    adbManager.shellCommand(listOf("cmd", "location", "set-location-enabled", "false"))
                    delay(restartDuration)
                    adbManager.shellCommand(listOf("cmd", "location", "set-location-enabled", "true"))
                }
            } catch (e: Exception) {
                Log.e("LocationMockService", "Error during ADB mock loop", e)
            }
        }
    }

    private fun stopMocking() {
        isMocking = false
        mockJob?.cancel()

        // Cleanup
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d("MockService", "Cleaning up mock providers...")
                adbManager.shellCommand(listOf("cmd", "location", "providers", "set-test-provider-enabled", "gps", "false"))
                adbManager.shellCommand(listOf("cmd", "location", "providers", "set-test-provider-enabled", "network", "false"))
                adbManager.shellCommand(listOf("cmd", "location", "providers", "remove-test-provider", "gps"))
                adbManager.shellCommand(listOf("cmd", "location", "providers", "remove-test-provider", "network"))
                adbManager.shellCommand(listOf("cmd", "location", "set-location-enabled", "true"))
                Log.d("MockService", "Cleanup done.")
            } catch (e: Exception) {
                Log.e("LocationMockService", "Error during cleanup", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Mock Location Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
