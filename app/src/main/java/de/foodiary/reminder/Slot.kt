package de.foodiary.reminder

import android.content.Context
import de.foodiary.data.MealType

/** Die täglichen Erinnerungen. defaultMinutes = Minuten seit Mitternacht. */
enum class Slot(
    val label: String,
    val defaultMinutes: Int,
    val mealType: MealType?,
    val question: String
) {
    FRUEHSTUECK("Frühstück", 10 * 60, MealType.FRUEHSTUECK,
        "Was hast du zum Frühstück gegessen, und wie geht es dir jetzt?"),
    MITTAG("Mittagessen", 14 * 60, MealType.MITTAG,
        "Was hast du zum Mittagessen gegessen, und wie geht es dir jetzt?"),
    ABEND("Abendessen", 20 * 60, MealType.ABEND,
        "Was hast du zum Abendessen gegessen, und wie geht es dir jetzt?"),
    SNACK("Snack-Check", 22 * 60, MealType.SNACK,
        "Hast du heute Snacks gegessen, die nicht zu den Mahlzeiten zählen?")
}

/** Speichert die Erinnerungszeiten als Minuten seit Mitternacht. */
class ReminderPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun minutesOfDay(slot: Slot): Int = prefs.getInt(slot.name, slot.defaultMinutes)

    fun save(slot: Slot, minutes: Int) {
        prefs.edit().putInt(slot.name, minutes).apply()
    }
}
