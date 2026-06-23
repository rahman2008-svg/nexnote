package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("NOTE_TITLE") ?: "NexNote Reminder"
        val content = intent.getStringExtra("NOTE_CONTENT") ?: "You have a scheduled note study session!"
        showNotification(context, title, content)
    }

    private fun showNotification(context: Context, title: String, content: String) {
        val channelId = "nexnote_reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "NexNote Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Offline notifications for tasks & notebooks"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Generate dynamic ID to support multiple concurrent alerts
        val notificationId = System.currentTimeMillis().toInt()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(notificationId, builder.build())
    }
}
