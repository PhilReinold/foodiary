package de.foodiary.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV im Langformat, eine Zeile pro Eintrag.
 * Der Freitext wird unverändert exportiert. art ist die Mahlzeit oder „Beschwerden".
 * zeitpunkt ist die Essenszeit bzw. der Beginn der Beschwerden, minuten_bis_eingabe der Abstand zur Eingabe.
 */
object CsvExport {

    private val timeFormat = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())

    fun build(entries: List<Entry>): String {
        val sb = StringBuilder()
        sb.append("erfasst_um,art,essen,zeitpunkt,minuten_bis_eingabe,kaffee,alkohol,beschwerden,staerke_1_5,notiz,befinden_1_10_alt,zuletzt_geaendert\n")
        entries.sortedBy { it.recordedAt }.forEach { e ->
            val minutes = e.eatenAt?.let { (e.recordedAt - it) / 60_000L }
            val row = listOf(
                formatTime(e.recordedAt),
                e.mealType?.label ?: "Beschwerden",
                e.mealText.orEmpty(),
                e.eatenAt?.let { formatTime(it) }.orEmpty(),
                minutes?.toString().orEmpty(),
                yesNo(e.kaffee),
                yesNo(e.alkohol),
                e.symptoms.orEmpty(),
                e.severity?.toString().orEmpty(),
                e.complaints.orEmpty(),
                e.wellbeing?.toString().orEmpty(),
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
