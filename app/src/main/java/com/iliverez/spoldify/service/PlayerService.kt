package com.iliverez.spoldify.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import androidx.core.app.NotificationCompat
import com.iliverez.spoldify.MainActivity
import com.iliverez.spoldify.R
import com.iliverez.spoldify.SpoldifyApp

class PlayerService : android.app.Service() {

    companion object {
        const val CHANNEL_ID = "spoldify_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.iliverez.spoldify.PLAY"
        const val ACTION_PAUSE = "com.iliverez.spoldify.PAUSE"
        const val ACTION_SKIP_NEXT = "com.iliverez.spoldify.SKIP_NEXT"
        const val ACTION_SKIP_PREV = "com.iliverez.spoldify.SKIP_PREV"
        const val ACTION_STOP = "com.iliverez.spoldify.STOP"
    }

    private var notificationManager: PlayerNotificationManager? = null
    private var mediaSession: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = PlayerNotificationManager(this)
        setupMediaSession()
        startForegroundService()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "SpoldifyPlayer").apply {
            val callback = MediaSessionCallback().apply {
                onPlay = { SpoldifyApp.instance.playerWrapper.resume() }
                onPause = { SpoldifyApp.instance.playerWrapper.pause() }
                onSkipToNext = { SpoldifyApp.instance.playerWrapper.skipNext() }
                onSkipToPrevious = { SpoldifyApp.instance.playerWrapper.skipPrevious() }
                onStop = { stopSelf() }
            }
            setCallback(callback)
            isActive = true
        }
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun updateNotification(title: String, artist: String) {
        val notification = buildNotification(title, artist)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String? = null, text: String? = null): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title ?: getString(R.string.app_name))
            .setContentText(text ?: getString(R.string.notification_playing))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_skip_prev,
                    getString(R.string.action_skip_prev),
                    buildActionPendingIntent(ACTION_SKIP_PREV)
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_pause,
                    getString(R.string.action_pause),
                    buildActionPendingIntent(ACTION_PAUSE)
                )
            )
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_skip_next,
                    getString(R.string.action_skip_next),
                    buildActionPendingIntent(ACTION_SKIP_NEXT)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun buildActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> SpoldifyApp.instance.playerWrapper.resume()
            ACTION_PAUSE -> SpoldifyApp.instance.playerWrapper.pause()
            ACTION_SKIP_NEXT -> SpoldifyApp.instance.playerWrapper.skipNext()
            ACTION_SKIP_PREV -> SpoldifyApp.instance.playerWrapper.skipPrevious()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession?.isActive = false
        mediaSession?.release()
        mediaSession = null
        notificationManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null
}
