package com.roadmaster.app.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import com.google.android.gms.location.*

/**
 * Collects temporary GPS fixes while a user-started session is active.
 * This prototype broadcasts samples to the app; it does not upload tracks or award points.
 */
class TrackingService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private lateinit var callback: LocationCallback
    private var lastAcceptedLocationTime = 0L

    override fun onCreate() {
        super.onCreate()
        client = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(::publishIfUsable)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startForeground(NOTIFICATION_ID, notification())
            client.removeLocationUpdates(callback)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
                .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METERS)
                .setWaitForAccurateLocation(false)
                .build()
            client.requestLocationUpdates(request, callback, mainLooper)
        } catch (_: SecurityException) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun publishIfUsable(location: Location) {
        if (!location.hasAccuracy() || location.accuracy > MAX_ACCEPTED_ACCURACY_METERS) return
        if (!location.latitude.isFinite() || !location.longitude.isFinite()) return
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) return

        // Ignore cached/stale fixes and duplicate timestamps so the route does not jump backwards.
        val ageMillis = System.currentTimeMillis() - location.time
        if (ageMillis !in -5_000L..MAX_LOCATION_AGE_MILLIS) return
        if (location.time <= lastAcceptedLocationTime) return
        lastAcceptedLocationTime = location.time

        sendBroadcast(Intent(ACTION_LOCATION_UPDATE).apply {
            setPackage(packageName)
            putExtra(EXTRA_LATITUDE, location.latitude)
            putExtra(EXTRA_LONGITUDE, location.longitude)
            putExtra(EXTRA_ACCURACY, location.accuracy)
            putExtra(EXTRA_SPEED, if (location.hasSpeed()) location.speed else 0f)
            putExtra(EXTRA_BEARING, if (location.hasBearing()) location.bearing else -1f)
            putExtra(EXTRA_TIME, location.time)
        })
    }

    override fun onDestroy() {
        if (::client.isInitialized && ::callback.isInitialized) {
            client.removeLocationUpdates(callback)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "도로 기록",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "사용자가 시작한 이동 경로 기록 상태를 표시합니다."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("ROAD MASTER 기록 중")
            .setContentText("이동 경로를 기록하고 있습니다. 이동 중에는 화면을 조작하지 마세요.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_LOCATION_UPDATE = "com.roadmaster.app.LOCATION_UPDATE"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_BEARING = "bearing"
        const val EXTRA_TIME = "recordedAt"

        private const val CHANNEL_ID = "road_tracking"
        private const val NOTIFICATION_ID = 41
        private const val UPDATE_INTERVAL_MILLIS = 5_000L
        private const val MAX_LOCATION_AGE_MILLIS = 15_000L
        private const val MAX_ACCEPTED_ACCURACY_METERS = 50f
        private const val MIN_UPDATE_DISTANCE_METERS = 5f
    }
}
