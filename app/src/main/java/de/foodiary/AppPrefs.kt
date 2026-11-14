package de.foodiary

import android.content.Context

/** Kleine Einstellungen der App selbst, z. B. ob der Willkommen-Bildschirm schon gesehen wurde. */
class AppPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("app", Context.MODE_PRIVATE)

    var welcomeDone: Boolean
        get() = prefs.getBoolean("welcome_done", false)
        set(value) {
            prefs.edit().putBoolean("welcome_done", value).apply()
        }
}
