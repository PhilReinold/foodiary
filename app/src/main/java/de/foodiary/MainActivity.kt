package de.foodiary

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.foodiary.data.AppDatabase
import de.foodiary.reminder.ReminderPrefs
import de.foodiary.reminder.ReminderScheduler
import de.foodiary.reminder.Slot
import de.foodiary.ui.FoodiaryApp
import de.foodiary.ui.FoodiaryTheme

class MainActivity : ComponentActivity() {

    /** Gesetzt, wenn die App über eine Erinnerung geöffnet wurde. */
    var incomingSlot by mutableStateOf<Slot?>(null)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.createChannel(this)
        ReminderScheduler.scheduleAll(this)
        handleIntent(intent)

        val dao = AppDatabase.get(this).entryDao()
        val prefs = ReminderPrefs(this)
        val appPrefs = AppPrefs(this)

        setContent {
            FoodiaryTheme {
                FoodiaryApp(
                    dao = dao,
                    prefs = prefs,
                    appPrefs = appPrefs,
                    incomingSlot = incomingSlot,
                    onSlotConsumed = { incomingSlot = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        incomingSlot = intent?.getStringExtra(ReminderScheduler.EXTRA_SLOT)
            ?.let { runCatching { Slot.valueOf(it) }.getOrNull() }
    }
}
