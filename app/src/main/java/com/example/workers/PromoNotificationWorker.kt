package com.example.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class PromoNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = FirebaseDatabase.getInstance().getReference("tournaments")
            val snap = db.get().await()

            var latestTournamentTitle = ""
            var latestTimestamp = 0L

            for (child in snap.children) {
                val title = child.child("title").getValue(String::class.java) ?: continue
                val status = child.child("status").getValue(String::class.java) ?: "OPEN"
                val ts = child.child("scheduleTimeMillis").getValue(Long::class.java) ?: 0L

                if (status == "OPEN" && ts > latestTimestamp) {
                    latestTimestamp = ts
                    latestTournamentTitle = title
                }
            }

            val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val lastNotified = prefs.getLong("last_notified_ts", 0L)

            if (latestTimestamp > lastNotified && latestTournamentTitle.isNotBlank()) {
                prefs.edit().putLong("last_notified_ts", latestTimestamp).apply()
                sendNotification("New Tournament Alert!", "Registrations are OPEN for $latestTournamentTitle! Join now!")
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "admin_tourney_alerts"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Tournament Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_stat_trophy)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
