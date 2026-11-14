package de.foodiary.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Zeigt die Benachrichtigung und plant den Auslöser für den nächsten Tag neu. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(ReminderScheduler.EXTRA_SLOT)
            ?.let { Slot.valueOf(it) } ?: return
        ReminderScheduler.showNotification(context, slot)
        ReminderScheduler.schedule(context, slot, ReminderPrefs(context).minutesOfDay(slot))
    }
}

/** Nach einem Neustart sind Alarme weg, deshalb neu planen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReminderScheduler.scheduleAll(context)
        }
    }
}
