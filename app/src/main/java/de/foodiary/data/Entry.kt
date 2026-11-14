package de.foodiary.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MealType { FRUEHSTUECK, MITTAG, ABEND, SNACK }

val MealType.label: String
    get() = when (this) {
        MealType.FRUEHSTUECK -> "Frühstück"
        MealType.MITTAG -> "Mittagessen"
        MealType.ABEND -> "Abendessen"
        MealType.SNACK -> "Snack"
    }

val MealType.question: String
    get() = when (this) {
        MealType.FRUEHSTUECK -> "Was hast du zum Frühstück gegessen?"
        MealType.MITTAG -> "Was hast du zum Mittagessen gegessen?"
        MealType.ABEND -> "Was hast du zum Abendessen gegessen?"
        MealType.SNACK -> "Was hast du als Snack gegessen?"
    }

/**
 * Ein Tagebucheintrag. Alle Zeitpunkte sind Epoch-Millisekunden.
 * mealType = null bedeutet: nur Befinden erfasst.
 * mealText bleibt unverändert, Auswertung erfolgt später extern.
 * updatedAt ist gesetzt, sobald der Eintrag nachträglich bearbeitet wurde.
 */
@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordedAt: Long,
    val mealType: MealType?,
    val mealText: String?,
    val eatenAt: Long?,
    val wellbeing: Int?,
    val complaints: String?,
    @ColumnInfo(defaultValue = "0") val kaffee: Boolean = false,
    @ColumnInfo(defaultValue = "0") val alkohol: Boolean = false,
    val updatedAt: Long? = null
)
