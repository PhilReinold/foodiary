package de.foodiary.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.foodiary.MainActivity
import java.time.LocalDateTime
import java.time.ZoneId

object ReminderScheduler {

    const val CHANNEL_ID = "foodiary_reminders"
    const val EXTRA_SLOT = "slot"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Erinnerungen", NotificationManager.IMPORTANCE_DEFAULT)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun scheduleAll(context: Context) {
        val prefs = ReminderPrefs(context)
        Slot.entries.forEach { schedule(context, it, prefs.minutesOfDay(it)) }
    }

    /** Plant den nächsten Auslöser für einen Slot. Ein gleicher PendingIntent ersetzt den alten Alarm. */
    fun schedule(context: Context, slot: Slot, minutesOfDay: Int) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(minutesOfDay / 60, minutesOfDay % 60)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = pendingReminder(context, slot)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms()
        if (exactAllowed) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            // Ohne Erlaubnis für exakte Alarme ungefähr, Zeit kann einige Minuten abweichen.
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    @SuppressLint("MissingPermission")
    fun showNotification(context: Context, slot: Slot) {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_SLOT, slot.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context, 100 + slot.ordinal, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("FooDiary · ${slot.label}")
            .setContentText(slot.question)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(slot.ordinal, notification)
    }

    private fun pendingReminder(context: Context, slot: Slot): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_SLOT, slot.name)
        return PendingIntent.getBroadcast(
            context, slot.ordinal, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
