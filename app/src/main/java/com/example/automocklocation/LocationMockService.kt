package com.example.automocklocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat

class LocationMockService : Service() {

    private val CHANNEL_ID = "MockLocationServiceChannel"
    private var isMocking = false
    private var mockThread: Thread? = null
    
    private var lat = 0.0
    private var lon = 0.0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lat = intent?.getDoubleExtra("lat", 0.0) ?: 0.0
        lon = intent?.getDoubleExtra("lon", 0.0) ?: 0.0

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock Location Active")
            .setContentText("Spoofing location to: $lat, $lon")
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
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        
        val providerName = LocationManager.GPS_PROVIDER
        
        try {
            locationManager.addTestProvider(
                providerName, false, false, false, false, false, 
                false, false, 0, 1
            )
            locationManager.setTestProviderEnabled(providerName, true)
        } catch (e: Exception) {
            Log.e("LocationMockService", "Failed to add test provider. AppOps permission might be missing.", e)
            return
        }

        mockThread = Thread {
            while (isMocking) {
                try {
                    val mockLocation = Location(providerName).apply {
                        latitude = lat
                        longitude = lon
                        altitude = 0.0
                        time = System.currentTimeMillis()
                        accuracy = 1.0f
                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    }
                    
                    locationManager.setTestProviderLocation(providerName, mockLocation)
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e("LocationMockService", "Error setting mock location", e)
                }
            }
        }
        mockThread?.start()
    }

    private fun stopMocking() {
        isMocking = false
        mockThread?.interrupt()
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            Log.e("LocationMockService", "Error removing test provider", e)
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
