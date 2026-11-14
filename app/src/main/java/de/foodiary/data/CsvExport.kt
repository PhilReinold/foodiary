package de.foodiary.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV im Langformat, eine Zeile pro Eintrag.
 * Der Freitext wird unverändert exportiert. minuten_seit_essen ist der Abstand
 * zwischen Eingabe und Essenszeitpunkt.
 */
object CsvExport {

    private val timeFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())

    fun build(entries: List<Entry>): String {
        val sb = StringBuilder()
        sb.append("erfasst_um,mahlzeit,essen,gegessen_um,minuten_seit_essen,kaffee,alkohol,befinden_1_10,beschwerden,zuletzt_geaendert\n")
        entries.sortedBy { it.recordedAt }.forEach { e ->
            val minutes = e.eatenAt?.let { (e.recordedAt - it) / 60_000L }
            val row = listOf(
                formatTime(e.recordedAt),
                e.mealType?.label.orEmpty(),
                e.mealText.orEmpty(),
                e.eatenAt?.let { formatTime(it) }.orEmpty(),
                minutes?.toString().orEmpty(),
                yesNo(e.kaffee),
                yesNo(e.alkohol),
                e.wellbeing?.toString().orEmpty(),
                e.complaints.orEmpty(),
                e.updatedAt?.let { formatTime(it) }.orEmpty()
            )
            sb.append(row.joinToString(",") { escape(it) }).append('\n')
        }
        return sb.toString()
    }

    private fun formatTime(millis: Long): String = timeFormat.format(Instant.ofEpochMilli(millis))

    private fun yesNo(value: Boolean): String = if (value) "ja" else "nein"

    private fun escape(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
