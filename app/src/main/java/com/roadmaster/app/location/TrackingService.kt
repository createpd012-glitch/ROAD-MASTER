package com.roadmaster.app.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.google.android.gms.location.*

class TrackingService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private lateinit var callback: LocationCallback
    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    if (location.hasAccuracy() && location.accuracy <= 50f) {
                        sendBroadcast(Intent(ACTION_LOCATION_UPDATE).apply {
                            setPackage(packageName)
                            putExtra(EXTRA_LATITUDE, location.latitude)
                            putExtra(EXTRA_LONGITUDE, location.longitude)
                            putExtra(EXTRA_ACCURACY, location.accuracy)
                            putExtra(EXTRA_TIME, location.time)
                        })
                    }
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification())
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).setMinUpdateDistanceMeters(3f).build()
        try { client.requestLocationUpdates(request, callback, mainLooper) } catch (_: SecurityException) { stopSelf() }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        if (::client.isInitialized && ::callback.isInitialized) client.removeLocationUpdates(callback)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "도로 기록", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setContentTitle("ROAD MASTER 기록 중")
            .setContentText("이동 경로를 기록하고 있습니다. 안전에 집중하세요.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).build()
    }
    companion object {
        const val ACTION_LOCATION_UPDATE = "com.roadmaster.app.LOCATION_UPDATE"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_TIME = "recordedAt"
        private const val CHANNEL_ID = "road_tracking"
        private const val NOTIFICATION_ID = 41
    }
}