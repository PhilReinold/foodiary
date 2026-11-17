package de.foodiary.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.foodiary.data.Entry
import de.foodiary.data.EntryDao
import de.foodiary.data.MealType
import de.foodiary.reminder.Slot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Auswählbare Beschwerden. Alles Weitere geht über die Notiz. */
internal val SYMPTOMS = listOf("Bauch", "Kopf", "Müdigkeit", "Haut")

internal fun severityWord(severity: Int): String = when (severity) {
    1 -> "leicht"
    2 -> "eher leicht"
    3 -> "mittel"
    4 -> "stark"
    else -> "sehr stark"
}

internal fun Entry.symptomList(): List<String> =
    symptoms?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

/** Kurzform wie „Bauch, Kopf · stark" oder null, wenn keine Beschwerden erfasst sind. */
internal fun Entry.symptomSummary(): String? {
    val list = symptomList()
    if (list.isEmpty()) return null
    return list.joinToString(", ") + (severity?.let { " · " + severityWord(it) } ?: "")
}

/** Vorschlag für die Mahlzeit anhand der Uhrzeit: vor 11 Uhr Frühstück, vor 16 Uhr Mittag, danach Abend. */
private fun mealFor(time: Long): MealType {
    val hour = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).hour
    return when {
        hour < 11 -> MealType.FRUEHSTUECK
        hour < 16 -> MealType.MITTAG
        else -> MealType.ABEND
    }
}

private fun MealType?.tint(): Color = when (this) {
    MealType.FRUEHSTUECK -> Color(0xFFFFE8DA)
    MealType.MITTAG -> Color(0xFFE4ECF3)
    MealType.ABEND -> Color(0xFFFAD5D5)
    MealType.SNACK -> Color(0xFFEFE8FA)
    null -> Palette.Surface200
}

private val SunColor = Color(0xFFF6C24A)
private val SunRayColor = Color(0xFFF0A93B)

/** Kleine Auswahlpille. Bewusst ohne Material-Mindestgröße, damit mehrere in eine Zeile passen. */
@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
    horizontalPadding: Dp = 12.dp,
    selectedTextColor: Color = Palette.Ink,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(if (selected) selectedColor else Color.Transparent)
            .border(1.dp, if (selected) selectedColor else Palette.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize),
            color = if (selected) selectedTextColor else Palette.Ink,
            maxLines = 1
        )
    }
}

/** Schlichtes Textfeld ohne feste Mindesthöhe, damit es sich dem verfügbaren Platz anpasst. */
@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false
) {
    val shape = RoundedCornerShape(if (singleLine) 12.dp else 16.dp)
    // TextFieldValue statt String: so behält die Tastatur Auswahl und Textmarkierung,
    // und Löschen funktioniert zuverlässig (auch bei Vorschlägen und Autokorrektur).
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value)) }
    if (field.text != value) field = androidx.compose.ui.text.input.TextFieldValue(value, androidx.compose.ui.text.TextRange(value.length))
    BasicTextField(
        value = field,
        onValueChange = {
            field = it
            if (it.text != value) onValueChange(it.text)
        },
        singleLine = singleLine,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Ink),
        cursorBrush = SolidColor(Palette.Ink),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier
            .clip(shape)
            .background(Palette.Surface100)
            .border(1.dp, Palette.LavenderDeep, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        decorationBox = { inner ->
            Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                if (field.text.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.InkMuted
                    )
                }
                inner()
            }
        }
    )
}

/** Gesicht, dessen Mund sich mit dem Wert biegt: 1 = Lächeln, -1 = traurig. */
@Composable
private fun Face(level: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension
        drawCircle(Palette.Peach, s / 2f, center)
        drawCircle(Palette.Ink, s * 0.05f, Offset(s * 0.35f, s * 0.42f))
        drawCircle(Palette.Ink, s * 0.05f, Offset(s * 0.65f, s * 0.42f))
        val mouth = Path().apply {
            moveTo(s * 0.31f, s * 0.65f)
            quadraticBezierTo(s * 0.5f, s * (0.65f + 0.19f * level), s * 0.69f, s * 0.65f)
        }
        drawPath(mouth, Palette.Ink, style = Stroke(width = s * 0.05f, cap = StrokeCap.Round))
    }
}

/** Beschwerden wählen und ihre Stärke einstellen. Wird am Essen und im eigenen Eintrag verwendet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymptomBlock(
    selected: Set<String>,
    severity: Int?,
    onChange: (Set<String>, Int?) -> Unit
) {
    val any = selected.isNotEmpty()
    val value = severity ?: 2
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Pill("Keine", !any, Palette.Lavender, modifier = Modifier.weight(0.85f), height = 30.dp, horizontalPadding = 2.dp, fontSize = 12.sp) { onChange(emptySet(), null) }
        SYMPTOMS.forEach { symptom ->
            Pill(symptom, symptom in selected, Palette.SoftRed, modifier = Modifier.weight(if (symptom == "Müdigkeit") 1.4f else 0.85f), height = 30.dp, horizontalPadding = 2.dp, fontSize = 12.sp) {
                val next = if (symptom in selected) selected - symptom else selected + symptom
                onChange(next, if (next.isEmpty()) null else value)
            }
        }
    }
    Row(
        modifier = Modifier.alpha(if (any) 1f else 0.35f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Face(level = if (any) 0.1f - (value - 1) / 4f * 1.1f else 0.3f, modifier = Modifier.size(34.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(selected, it.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3,
            enabled = any,
            colors = SliderDefaults.colors(
                thumbColor = Palette.Outline,
                activeTrackColor = Palette.Outline,
                inactiveTrackColor = Palette.Lavender
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            if (any) severityWord(value) else "wie stark?",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(78.dp),
            textAlign = TextAlign.End
        )
    }
}

/** Zeitwahl: Jetzt, vor 30 Minuten, vor einer Stunde oder eine genaue Uhrzeit. */
@Composable
private fun TimeRow(
    time: Long,
    selection: String,
    relative: Boolean,
    onPick: (String, Long) -> Unit
) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill(
            label = "Jetzt",
            selected = selection == "now",
            selectedColor = Palette.Ink,
            modifier = Modifier.weight(0.8f),
            height = 40.dp,
            horizontalPadding = 2.dp,
            selectedTextColor = Palette.Surface200
        ) { onPick("now", System.currentTimeMillis()) }
        Pill(
            label = if (selection == "custom") time.toDayAndTime() else "Datum & Uhrzeit wählen",
            selected = selection == "custom",
            selectedColor = Palette.Ink,
            modifier = Modifier.weight(2f),
            height = 40.dp,
            horizontalPadding = 8.dp,
            selectedTextColor = Palette.Surface200
        ) { pickDateTime(context, time) { onPick("custom", it) } }
    }
}

/**
 * Sonnenbogen: Frühstück links unten, Mittag oben, Abend rechts unten. Die Sonne lässt sich ziehen
 * oder per Tipp setzen und rastet auf der nächsten Mahlzeit ein. Snack sitzt auf der Horizontlinie.
 */
@Composable
private fun SunArc(
    meal: MealType?,
    onSelect: (MealType) -> Unit,
    onSnack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mains = listOf(MealType.FRUEHSTUECK, MealType.MITTAG, MealType.ABEND)
    val index = mains.indexOf(meal)
    var dragT by remember { mutableStateOf<Float?>(null) }
    val restT by animateFloatAsState(if (index < 0) 0.5f else index / 2f, label = "sun")
    val t = dragT ?: restT
    val currentOnSelect by rememberUpdatedState(onSelect)

    Box(modifier = modifier.fillMaxWidth().height(108.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val pad = 24.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat()
                        fun tAt(x: Float): Float = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
                        dragT = tAt(down.position.x)
                        drag(down.id) { change ->
                            dragT = tAt(change.position.x)
                            change.consume()
                        }
                        val end = dragT ?: 0.5f
                        dragT = null
                        currentOnSelect(mains[(end * 2).roundToInt().coerceIn(0, 2)])
                    }
                }
        ) {
            val pad = 24.dp.toPx()
            val base = size.height - 26.dp.toPx()
            val peak = 20.dp.toPx()
            val p0 = Offset(pad, base)
            val p2 = Offset(size.width - pad, base)
            val control = Offset(size.width / 2f, 2 * peak - base)
            fun at(v: Float): Offset {
                val u = 1 - v
                return Offset(
                    u * u * p0.x + 2 * u * v * control.x + v * v * p2.x,
                    u * u * p0.y + 2 * u * v * control.y + v * v * p2.y
                )
            }

            drawLine(
                Palette.LavenderDeep,
                Offset(8.dp.toPx(), base),
                Offset(size.width - 8.dp.toPx(), base),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx()))
            )
            val arc = Path().apply {
                moveTo(p0.x, p0.y)
                quadraticBezierTo(control.x, control.y, p2.x, p2.y)
            }
            drawPath(arc, Palette.LavenderDeep, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            mains.forEachIndexed { i, m ->
                drawCircle(if (m == meal) Palette.Ink else Palette.LavenderDeep, 5.dp.toPx(), at(i / 2f))
            }

            val sun = at(t)
            val alpha = if (meal == MealType.SNACK && dragT == null) 0.25f else 1f
            for (i in 0 until 8) {
                val angle = i * PI / 4
                val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(
                    SunRayColor.copy(alpha = alpha),
                    sun + dir * 15.dp.toPx(),
                    sun + dir * 20.dp.toPx(),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(SunColor.copy(alpha = alpha), 11.dp.toPx(), sun)
        }

        @Composable
        fun label(text: String, on: Boolean, labelModifier: Modifier) {
            Text(
                text,
                modifier = labelModifier,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (on) Palette.Ink else Palette.InkMuted
            )
        }
        label("Frühstück", meal == MealType.FRUEHSTUECK, Modifier.align(Alignment.BottomStart).padding(start = 6.dp))
        label("Mittag", meal == MealType.MITTAG, Modifier.align(Alignment.TopCenter).padding(top = 42.dp))
        label("Abend", meal == MealType.ABEND, Modifier.align(Alignment.BottomEnd).padding(end = 10.dp))
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 13.dp).background(Palette.Surface200, RoundedCornerShape(50))) {
            Pill("Snack", meal == MealType.SNACK, Palette.LavenderDeep, height = 26.dp, onClick = onSnack)
        }
    }
}

@Composable
private fun EntryHeader(showDelete: Boolean, onClose: () -> Unit, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClose) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Zurück")
        }
        Spacer(Modifier.weight(1f))
        if (showDelete) {
            TextButton(onClick = onDelete) { Text("Löschen") }
        }
    }
}

@Composable
private fun DeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eintrag löschen?") },
        text = { Text("Der Eintrag wird dauerhaft entfernt.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

@Composable
private fun SaveButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        colors = peachButtonColors(),
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(52.dp)
    ) { Text("Speichern") }
}

/** Mahlzeit erfassen: Was, Wann und Beschwerden danach. */
@Composable
internal fun EditScreen(
    dao: EntryDao,
    existing: Entry?,
    slot: Slot?,
    day: LocalDate?,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val relative = (existing?.dayDate() ?: day ?: today) == today

    var text by remember { mutableStateOf(existing?.mealText.orEmpty()) }
    var kaffee by remember { mutableStateOf(existing?.kaffee ?: false) }
    var alkohol by remember { mutableStateOf(existing?.alkohol ?: false) }
    var eatenAt by remember {
        mutableStateOf(existing?.eatenAt ?: existing?.recordedAt ?: defaultEatenAt(day))
    }
    var timeSelection by remember { mutableStateOf(if (existing == null && relative) "now" else "custom") }
    var meal by remember { mutableStateOf(existing?.mealType ?: slot?.mealType ?: mealFor(eatenAt)) }
    // Solange die Mahlzeit nicht von Hand gesetzt wurde, folgt sie der gewählten Uhrzeit.
    var mealTouched by remember { mutableStateOf(existing != null || slot != null) }
    var symptoms by remember { mutableStateOf(existing?.symptomList()?.toSet() ?: emptySet()) }
    var severity by remember { mutableStateOf(existing?.severity) }
    var note by remember { mutableStateOf(existing?.complaints.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val hasContent = text.isNotBlank() || kaffee || alkohol || symptoms.isNotEmpty() || note.isNotBlank()
    val whenColor by animateColorAsState(meal.tint(), label = "mealTint")

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        EntryHeader(showDelete = existing != null, onClose = onClose, onDelete = { confirmDelete = true })

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            // Passt alles auf den Bildschirm, füllt die erste Karte den Rest. Sonst wird gescrollt.
            val fits = maxHeight >= 610.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState())),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = whenColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (existing == null) "Neuer Eintrag" else "Eintrag bearbeiten", style = MaterialTheme.typography.titleMedium)
                        SunArc(
                            meal = meal,
                            onSelect = {
                                meal = it
                                mealTouched = true
                            },
                            onSnack = {
                                meal = if (meal == MealType.SNACK) mealFor(eatenAt) else MealType.SNACK
                                mealTouched = true
                            }
                        )
                        TimeRow(time = eatenAt, selection = timeSelection, relative = relative) { key, picked ->
                            timeSelection = key
                            eatenAt = picked
                            if (!mealTouched) meal = mealFor(picked)
                        }
                    }
                }


                Card(
                    modifier = Modifier.fillMaxWidth().then(if (fits) Modifier.weight(1f) else Modifier),
                    colors = CardDefaults.cardColors(containerColor = Palette.Surface200)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (fits) Modifier.fillMaxHeight() else Modifier)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            when (meal) {
                                MealType.FRUEHSTUECK -> "Was gab es zum Frühstück?"
                                MealType.MITTAG -> "Was gab es zum Mittagessen?"
                                MealType.ABEND -> "Was gab es zum Abendessen?"
                                MealType.SNACK -> "Was gab es als Snack?"
                            },
                            style = MaterialTheme.typography.titleMedium)
                        PlainField(
                            value = text,
                            onValueChange = { text = it },
                            placeholder = "z. B. Spaghetti Carbonara, kleine Portion",
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (fits) Modifier.weight(1f) else Modifier.height(96.dp))
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Dazu",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Pill("Kaffee", kaffee, Palette.Lavender, height = 30.dp) { kaffee = !kaffee }
                            Pill("Alkohol", alkohol, Palette.Lavender, height = 30.dp) { alkohol = !alkohol }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Palette.Surface200)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Beschwerden danach?", style = MaterialTheme.typography.titleMedium)
                        SymptomBlock(symptoms, severity) { nextSymptoms, nextSeverity ->
                            symptoms = nextSymptoms
                            severity = nextSeverity
                        }
                        PlainField(
                            value = note,
                            onValueChange = { note = it },
                            placeholder = "Notiz (optional)",
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            singleLine = true
                        )
                    }
                }
            }
        }

        SaveButton(enabled = hasContent) {
            val now = System.currentTimeMillis()
            val entry = Entry(
                id = existing?.id ?: 0L,
                recordedAt = existing?.recordedAt ?: now,
                mealType = meal,
                mealText = text.trim().ifBlank { null },
                eatenAt = eatenAt,
                wellbeing = existing?.wellbeing,
                complaints = note.trim().ifBlank { null },
                kaffee = kaffee,
                alkohol = alkohol,
                updatedAt = if (existing != null) now else null,
                symptoms = symptoms.joinToString(",").ifBlank { null },
                severity = if (symptoms.isEmpty()) null else severity
            )
            scope.launch {
                if (existing == null) dao.insert(entry) else dao.update(entry)
                onClose()
            }
        }
    }

    if (confirmDelete && existing != null) {
        DeleteDialog(
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    dao.delete(existing)
                    onClose()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** Beschwerden ohne Mahlzeit erfassen, zum Beispiel wenn sie erst Stunden später auftreten. */
@Composable
internal fun SymptomScreen(
    dao: EntryDao,
    existing: Entry?,
    day: LocalDate?,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val relative = (existing?.dayDate() ?: day ?: today) == today

    var symptoms by remember { mutableStateOf(existing?.symptomList()?.toSet() ?: emptySet()) }
    var severity by remember { mutableStateOf(existing?.severity) }
    var time by remember {
        mutableStateOf(existing?.eatenAt ?: existing?.recordedAt ?: defaultEatenAt(day))
    }
    var timeSelection by remember { mutableStateOf(if (existing == null && relative) "now" else "custom") }
    var note by remember { mutableStateOf(existing?.complaints.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val hasContent = symptoms.isNotEmpty() || note.isNotBlank()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        EntryHeader(showDelete = existing != null, onClose = onClose, onDelete = { confirmDelete = true })

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Palette.Surface200)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Was spürst du?", style = MaterialTheme.typography.titleMedium)
                    SymptomBlock(symptoms, severity) { nextSymptoms, nextSeverity ->
                        symptoms = nextSymptoms
                        severity = nextSeverity
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Palette.Surface200)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Seit wann?", style = MaterialTheme.typography.titleMedium)
                    TimeRow(time = time, selection = timeSelection, relative = relative) { key, picked ->
                        timeSelection = key
                        time = picked
                    }
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Palette.Surface200)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Notiz", style = MaterialTheme.typography.titleMedium)
                    PlainField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = "z. B. Krämpfe nach dem Mittagessen",
                        modifier = Modifier.fillMaxWidth().height(96.dp)
                    )
                }
            }
            Text(
                "Im Export stehen die Beschwerden mit Uhrzeit neben deinen Mahlzeiten.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        SaveButton(enabled = hasContent) {
            val now = System.currentTimeMillis()
            val entry = Entry(
                id = existing?.id ?: 0L,
                recordedAt = existing?.recordedAt ?: now,
                mealType = null,
                mealText = null,
                eatenAt = time,
                wellbeing = existing?.wellbeing,
                complaints = note.trim().ifBlank { null },
                updatedAt = if (existing != null) now else null,
                symptoms = symptoms.joinToString(",").ifBlank { null },
                severity = if (symptoms.isEmpty()) null else severity
            )
            scope.launch {
                if (existing == null) dao.insert(entry) else dao.update(entry)
                onClose()
            }
        }
    }

    if (confirmDelete && existing != null) {
        DeleteDialog(
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    dao.delete(existing)
                    onClose()
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
