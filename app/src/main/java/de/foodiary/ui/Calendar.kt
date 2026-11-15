package de.foodiary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foodiary.data.Entry
import de.foodiary.data.MealType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import java.util.Locale

enum class DayStatus { COMPLETE, PARTIAL, EMPTY }

/** Ein neuer Tag beginnt um diese Uhrzeit. Späte Snacks vor 4 Uhr gehören noch zum Vortag. */
const val DAY_START_HOUR = 4L

/** Tag, zu dem ein Zeitpunkt gehört (mit Tagesgrenze). */
fun Long.logicalDay(): LocalDate =
    Instant.ofEpochMilli(this).minus(DAY_START_HOUR, ChronoUnit.HOURS)
        .atZone(ZoneId.systemDefault()).toLocalDate()

/** Tag eines Eintrags: die Essenszeit, sonst die Erfassungszeit. */
fun Entry.dayDate(): LocalDate = (eatenAt ?: recordedAt).logicalDay()

/**
 * Vollständig = Frühstück, Mittag und Abendessen sind erfasst.
 * Teilweise = irgendetwas ist erfasst. Leer = nichts.
 */
fun dayStatus(entries: List<Entry>): DayStatus {
    if (entries.isEmpty()) return DayStatus.EMPTY
    val mains = listOf(MealType.FRUEHSTUECK, MealType.MITTAG, MealType.ABEND)
    val complete = mains.all { main -> entries.any { it.mealType == main } }
    return if (complete) DayStatus.COMPLETE else DayStatus.PARTIAL
}

private val GreenDot = Color(0xFF6DBF8A)
private val YellowDot = Color(0xFFF2C94C)
private val RedDot = Color(0xFFE57373)

/** Farbe und Symbol zusammen, damit der Status auch ohne Farbunterscheidung lesbar ist. */
fun DayStatus.color(): Color = when (this) {
    DayStatus.COMPLETE -> GreenDot
    DayStatus.PARTIAL -> YellowDot
    DayStatus.EMPTY -> RedDot
}

fun DayStatus.symbol(): String = when (this) {
    DayStatus.COMPLETE -> "✓"
    DayStatus.PARTIAL -> "½"
    DayStatus.EMPTY -> "×"
}

@Composable
fun StatusDot(status: DayStatus, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(16.dp).clip(CircleShape).background(status.color()),
        contentAlignment = Alignment.Center
    ) {
        Text(status.symbol(), fontSize = 10.sp, lineHeight = 10.sp, color = Palette.Ink)
    }
}

@Composable
fun CalendarView(
    entries: List<Entry>,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    onDayClick: (LocalDate) -> Unit
) {
    val byDay = remember(entries) { entries.groupBy { it.dayDate() } }
    // Vor dem ersten erfassten Tag gibt es keinen Status. Sonst würde der Kalender alles rot färben.
    val firstTracked: LocalDate? = remember(entries) { byDay.keys.minOrNull() }
    val today = LocalDate.now()
    val daysInMonth = month.lengthOfMonth()
    val offset = month.atDay(1).dayOfWeek.value - 1 // Montag = 0
    val rows = (offset + daysInMonth + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Text("‹") }
            Text(
                text = "${month.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)} ${month.year}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            TextButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Text("›") }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNumber = row * 7 + col - offset + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        DayCell(
                            date = date,
                            entries = byDay[date].orEmpty(),
                            isToday = date == today,
                            firstTracked = firstTracked,
                            onClick = { onDayClick(date) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(Modifier.weight(1f).height(56.dp))
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LegendItem(DayStatus.COMPLETE, "vollständig")
            LegendItem(DayStatus.PARTIAL, "teilweise")
            LegendItem(DayStatus.EMPTY, "nichts")
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    entries: List<Entry>,
    isToday: Boolean,
    firstTracked: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val future = date.isAfter(LocalDate.now())
    // Kein Status: Zukunft, Tage vor der ersten Erfassung und der heutige Tag, solange noch nichts da ist.
    val showStatus = !future &&
        firstTracked != null &&
        !date.isBefore(firstTracked) &&
        !(isToday && entries.isEmpty())

    val cellModifier = modifier
        .height(56.dp)
        .clip(MaterialTheme.shapes.small)
        .then(
            if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
            else Modifier
        )
        .clickable(enabled = !future, onClick = onClick)

    Column(
        modifier = cellModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (future) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        if (showStatus) StatusDot(dayStatus(entries))
    }
}

@Composable
private fun LegendItem(status: DayStatus, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StatusDot(status)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
