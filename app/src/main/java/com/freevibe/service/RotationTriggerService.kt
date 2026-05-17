package com.freevibe.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * Foreground service that dynamically registers broadcast receivers for
 * [Intent.ACTION_USER_PRESENT] (unlock) and [Intent.ACTION_SCREEN_OFF].
 *
 * Required as a foreground service because both broadcasts are on the
 * background-restriction allow-list (Android 8+) only when the receiver is
 * runtime-registered from a long-lived process. A manifest-declared receiver
 * would not fire on phone unlock.
 *
 * ROADMAP NX-6 — first slice: per-unlock + screen-off pre-stage rotation
 * triggers. Off-by-default; the user opts in via two new Settings toggles
 * backed by `PreferencesManager.rotateOnUnlock` and `rotateOnScreenOff`.
 *
 * Each trigger enqueues a one-shot expedited `AutoWallpaperWorker` (same
 * worker the periodic rotation already uses) with the same constraints the
 * periodic schedule respects. WorkManager handles retries and battery
 * coalescing for us.
 *
 * Not in scope for this slice:
 * - Per-app rotation exclusion (needs `PACKAGE_USAGE_STATS`)
 * - Sub-15-minute periodic rotation (AlarmManager-backed)
 * - One-tap-shuffle Glance widget (bundled with NX-2 widget polish)
 */
class RotationTriggerService : Service() {

    private var receiver: BroadcastReceiver? = null
    @Volatile private var screenOffEnabled = false
    @Volatile private var unlockEnabled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        registerTriggers()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Allow Intent extras to update which triggers are active without restarting
        // the service. EXTRA_UNLOCK / EXTRA_SCREEN_OFF default to the current state
        // so the caller can selectively flip one without re-declaring both.
        screenOffEnabled = intent?.getBooleanExtra(EXTRA_SCREEN_OFF, screenOffEnabled) ?: screenOffEnabled
        unlockEnabled = intent?.getBooleanExtra(EXTRA_UNLOCK, unlockEnabled) ?: unlockEnabled
        if (!screenOffEnabled && !unlockEnabled) {
            stopSelf()
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Wallpaper triggers",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Listens for unlock/screen-off to rotate the wallpaper."
                    setShowBadge(false)
                },
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("Aura — wallpaper triggers")
            .setContentText("Rotating on unlock / screen off")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    private fun registerTriggers() {
        val rx = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> if (unlockEnabled) enqueueRotation(context)
                    Intent.ACTION_SCREEN_OFF -> if (screenOffEnabled) enqueueRotation(context)
                }
            }
        }
        receiver = rx
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        // ContextCompat is available but the receiver does not export any callbacks
        // back to other apps — RECEIVER_NOT_EXPORTED is the correct flag on API 33+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(rx, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(rx, filter)
        }
    }

    override fun onDestroy() {
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "aura_rotation_triggers"
        private const val NOTIFICATION_ID = 9241
        const val EXTRA_UNLOCK = "extra_unlock"
        const val EXTRA_SCREEN_OFF = "extra_screen_off"

        /**
         * Idempotent start: if any trigger is enabled, start (or update) the service;
         * if both are disabled, stop it. Safe to call from any context.
         */
        fun reconcile(context: Context, unlock: Boolean, screenOff: Boolean) {
            if (unlock || screenOff) {
                val intent = Intent(context, RotationTriggerService::class.java).apply {
                    putExtra(EXTRA_UNLOCK, unlock)
                    putExtra(EXTRA_SCREEN_OFF, screenOff)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } else {
                context.stopService(Intent(context, RotationTriggerService::class.java))
            }
        }

        /**
         * Enqueue a one-shot rotation. Reuses [AutoWallpaperWorker] (the periodic
         * worker already does the right thing — it reads source / target / shuffle
         * from prefs and applies). Expedited so the wallpaper is set in time for
         * the user to see it on their lock screen / next unlock.
         */
        internal fun enqueueRotation(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = OneTimeWorkRequestBuilder<AutoWallpaperWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "rotation_trigger_oneshot",
                ExistingWorkPolicy.KEEP, // Coalesce: don't queue 10 rotations on a chatty unlock
                request,
            )
        }
    }
}
