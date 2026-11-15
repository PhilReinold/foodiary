package de.foodiary.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.Context
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.foodiary.AppPrefs
import de.foodiary.data.CsvExport
import de.foodiary.data.Entry
import de.foodiary.data.EntryDao
import de.foodiary.data.MealType
import de.foodiary.data.label
import de.foodiary.data.question
import de.foodiary.reminder.ReminderPrefs
import de.foodiary.reminder.ReminderScheduler
import de.foodiary.reminder.Slot
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private sealed interface Screen {
    data object Welcome : Screen
    data object Overview : Screen
    data class Day(val date: LocalDate) : Screen
    data class Edit(val entry: Entry?, val slot: Slot?, val day: LocalDate?, val returnTo: Screen) : Screen
    data class Symptom(val entry: Entry?, val day: LocalDate?, val returnTo: Screen) : Screen
    data object Settings : Screen
    data object Data : Screen
}

private val dayHeaderFormat = DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN)
internal val dayShortFormat = DateTimeFormatter.ofPattern("EEEE, d. MMM", Locale.GERMAN)
private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM. HH:mm")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

internal fun Long.toTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(timeFormat)

private fun Long.toDateTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateTimeFormat)

/** Versionszeile, z. B. „Version 0.2.0 (Build 7)". Die Build-Nummer zählt GitHub Actions hoch. */
private fun appVersionLabel(context: Context): String {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    return "Version ${info.versionName} (Build ${PackageInfoCompat.getLongVersionCode(info)})"
}

private fun formatMinutes(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

internal fun defaultEatenAt(day: LocalDate?): Long {
    val target = day ?: LocalDate.now()
    return if (target == LocalDate.now()) System.currentTimeMillis()
    else target.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun pickMinutes(context: Context, current: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        current / 60,
        current % 60,
        true
    ).show()
}

internal fun pickDateTime(context: Context, base: Long, onPicked: (Long) -> Unit) {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = base }
    TimePickerDialog(
        context,
        { _, hour, minute ->
            cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
            cal.set(java.util.Calendar.MINUTE, minute)
            cal.set(java.util.Calendar.SECOND, 0)
            onPicked(cal.timeInMillis)
        },
        cal.get(java.util.Calendar.HOUR_OF_DAY),
        cal.get(java.util.Calendar.MINUTE),
        true
    ).show()
}

@Composable
fun FoodiaryApp(
    dao: EntryDao,
    prefs: ReminderPrefs,
    appPrefs: AppPrefs,
    incomingSlot: Slot?,
    onSlotConsumed: () -> Unit
) {
    val entries by dao.observeAll().collectAsState(initial = emptyList())
    var screen by remember {
        mutableStateOf<Screen>(if (appPrefs.welcomeDone) Screen.Overview else Screen.Welcome)
    }
    // Liegen hier, nicht im Overview, damit Ansicht und Monat beim Zurückkommen aus einem Tag erhalten bleiben.
    var calendarMode by rememberSaveable { mutableStateOf(false) }
    var calendarMonthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }

    LaunchedEffect(incomingSlot) {
        if (incomingSlot != null && appPrefs.welcomeDone) {
            // Gibt es für diese Mahlzeit heute schon einen Eintrag, wird er bearbeitet statt ein Duplikat anzulegen.
            val today = LocalDate.now()
            val existing = entries.firstOrNull {
                it.dayDate() == today && it.mealType == incomingSlot.mealType
            }
            screen = Screen.Edit(entry = existing, slot = incomingSlot, day = null, returnTo = Screen.Overview)
            onSlotConsumed()
        }
    }

    // Einträge ohne Mahlzeit sind Beschwerden und öffnen ihren eigenen Bildschirm.
    fun editorFor(entry: Entry, day: LocalDate?, back: Screen): Screen =
        if (entry.mealType == null) Screen.Symptom(entry, day, back) else Screen.Edit(entry, null, day, back)

    BackHandler(enabled = screen != Screen.Overview && screen != Screen.Welcome) {
        screen = when (val s = screen) {
            is Screen.Edit -> s.returnTo
            is Screen.Symptom -> s.returnTo
            else -> Screen.Overview
        }
    }

    // Android 15 zeichnet Apps randlos. Ohne diese Abstände rutscht Inhalt unter Status- und Navigationsleiste.
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .then(if (screen == Screen.Welcome) Modifier else Modifier.safeDrawingPadding()),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val current = screen) {
            Screen.Welcome -> WelcomeScreen(onDone = {
                appPrefs.welcomeDone = true
                screen = Screen.Overview
            })

            Screen.Overview -> OverviewScreen(
                entries = entries,
                calendarMode = calendarMode,
                onCalendarModeChange = { calendarMode = it },
                calendarMonth = YearMonth.parse(calendarMonthText),
                onCalendarMonthChange = { calendarMonthText = it.toString() },
                onAddMeal = { screen = Screen.Edit(null, null, null, Screen.Overview) },
                onAddSymptom = { screen = Screen.Symptom(null, null, Screen.Overview) },
                onOpenDay = { screen = Screen.Day(it) },
                onEdit = { screen = editorFor(it, null, Screen.Overview) },
                onData = { screen = Screen.Data },
                onSettings = { screen = Screen.Settings }
            )

            is Screen.Day -> DayScreen(
                date = current.date,
                entries = entries
                    .filter { it.dayDate() == current.date }
                    .sortedBy { it.eatenAt ?: it.recordedAt },
                onBack = { screen = Screen.Overview },
                onEdit = { screen = editorFor(it, current.date, current) },
                onAddMeal = { screen = Screen.Edit(null, null, current.date, current) },
                onAddSymptom = { screen = Screen.Symptom(null, current.date, current) }
            )

            is Screen.Edit -> EditScreen(
                dao = dao,
                existing = current.entry,
                slot = current.slot,
                day = current.day,
                onClose = { screen = current.returnTo }
            )

            is Screen.Symptom -> SymptomScreen(
                dao = dao,
                existing = current.entry,
                day = current.day,
                onClose = { screen = current.returnTo }
            )

            Screen.Settings -> SettingsScreen(prefs = prefs, onBack = { screen = Screen.Overview })

            Screen.Data -> DataScreen(dao = dao, onBack = { screen = Screen.Overview })
        }
    }
}

@Composable
private fun WelcomeScreen(onDone: () -> Unit) {
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.Lavender, Palette.Peach)))
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        FacetLogo(Modifier.size(120.dp))
        Spacer(Modifier.height(24.dp))
        Surface(color = Palette.Surface200, shape = RoundedCornerShape(50)) {
            Text(
                "Bekömmlich",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Palette.Surface200)
        ) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Willkommen bei Bekömmlich", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Notiere, was du isst und wie es dir danach geht. Mit der Zeit siehst du Muster, " +
                        "zum Beispiel bei vermuteten Unverträglichkeiten.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Alles bleibt auf deinem Gerät.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Damit dich die Erinnerungen zu deinen Mahlzeiten erreichen, brauchen wir die Erlaubnis für Benachrichtigungen.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(colors = peachButtonColors(), 
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Benachrichtigungen erlauben") }
                TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Später in den Einstellungen") }
            }
        }
    }
}

@Composable
private fun OverviewScreen(
    entries: List<Entry>,
    calendarMode: Boolean,
    onCalendarModeChange: (Boolean) -> Unit,
    calendarMonth: YearMonth,
    onCalendarMonthChange: (YearMonth) -> Unit,
    onAddMeal: () -> Unit,
    onAddSymptom: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEdit: (Entry) -> Unit,
    onData: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var addOpen by remember { mutableStateOf(false) }
    // Berechtigungen können in den Systemeinstellungen geändert werden. Beim Zurückkommen wird neu geprüft.
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationsOn = remember(resumeTick) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val exactOk = remember(resumeTick) {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    val grouped = remember(entries) {
        entries.groupBy { it.dayDate() }.toSortedMap(reverseOrder<LocalDate>())
    }

    Scaffold(
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { addOpen = true },
                    containerColor = Palette.Peach,
                    contentColor = Palette.Ink
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Eintrag hinzufügen")
                }
                DropdownMenu(expanded = addOpen, onDismissRequest = { addOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Mahlzeit eintragen") },
                        onClick = {
                            addOpen = false
                            onAddMeal()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Beschwerden eintragen") },
                        onClick = {
                            addOpen = false
                            onAddSymptom()
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Bekömmlich",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.Person, contentDescription = "Profil")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Meine Daten") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onData()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Erinnerungen") },
                            leadingIcon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onSettings()
                            }
                        )
                        HorizontalDivider()
                        Text(
                            appVersionLabel(context),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (!notificationsOn || !exactOk) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Palette.Peach)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Erinnerungen sind eingeschränkt", style = MaterialTheme.typography.titleSmall)
                        if (!notificationsOn) {
                            Text(
                                "Benachrichtigungen für Bekömmlich sind aus. Du bekommst keine Erinnerungen.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(colors = peachButtonColors(), onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }) { Text("Jetzt aktivieren") }
                        }
                        if (!exactOk) {
                            Text(
                                "Exakte Erinnerungen sind nicht erlaubt. Sie kommen dann eventuell etwas später.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(colors = peachButtonColors(), onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .setData(Uri.parse("package:${context.packageName}"))
                                )
                            }) { Text("Erlauben") }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !calendarMode,
                    onClick = { onCalendarModeChange(false) },
                    label = { Text("Liste") },
                    leadingIcon = { Icon(Icons.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                FilterChip(
                    selected = calendarMode,
                    onClick = { onCalendarModeChange(true) },
                    label = { Text("Kalender") },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            if (calendarMode) {
                CalendarView(
                    entries = entries,
                    month = calendarMonth,
                    onMonthChange = onCalendarMonthChange,
                    onDayClick = onOpenDay
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    if (entries.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("So startest du", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "Tippe unten auf +, um eine Mahlzeit oder Beschwerden zu notieren. " +
                                            "Zu deinen Erinnerungszeiten fragt Bekömmlich außerdem nach, was du gegessen hast.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                    grouped.forEach { (date, dayEntries) ->
                        item(key = "day-$date") {
                            Text(
                                date.format(dayHeaderFormat),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        items(
                            dayEntries.sortedBy { it.eatenAt ?: it.recordedAt },
                            key = { it.id }
                        ) { entry ->
                            EntryCard(entry) { onEdit(entry) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayScreen(
    date: LocalDate,
    entries: List<Entry>,
    onBack: () -> Unit,
    onEdit: (Entry) -> Unit,
    onAddMeal: () -> Unit,
    onAddSymptom: () -> Unit
) {
    val status = dayStatus(entries)
    val statusText = when (status) {
        DayStatus.COMPLETE -> "Vollständig: Frühstück, Mittag und Abendessen erfasst."
        DayStatus.PARTIAL -> "Teilweise erfasst."
        DayStatus.EMPTY -> "Noch nichts erfasst."
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Übersicht")
        }
        Text(date.format(dayHeaderFormat), style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(status)
            Text(statusText, style = MaterialTheme.typography.bodyMedium)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                EntryCard(entry) { onEdit(entry) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(colors = peachButtonColors(), onClick = onAddMeal, modifier = Modifier.weight(1f)) {
                Text("Mahlzeit")
            }
            OutlinedButton(onClick = onAddSymptom, modifier = Modifier.weight(1f)) {
                Text("Beschwerden")
            }
        }
    }
}

@Composable
private fun EntryCard(entry: Entry, onClick: () -> Unit) {
    val symptomEntry = entry.mealType == null
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = entry.mealType?.color() ?: Palette.LavenderDeep,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        entry.mealType?.label ?: "Beschwerden",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    (entry.eatenAt ?: entry.recordedAt).toTime(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val summary = entry.symptomSummary()
            if (symptomEntry) {
                Text(summary ?: "Ohne Angabe", style = MaterialTheme.typography.bodyLarge)
            } else {
                entry.mealText?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                val extras = listOfNotNull(
                    if (entry.kaffee) "Kaffee" else null,
                    if (entry.alkohol) "Alkohol" else null
                )
                if (extras.isNotEmpty()) {
                    Text("Dazu: ${extras.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium)
                }
                summary?.let { Text("Beschwerden: $it", style = MaterialTheme.typography.bodyMedium) }
            }
            entry.wellbeing?.let {
                Text("Befinden: $it/10", style = MaterialTheme.typography.bodyMedium)
            }
            entry.complaints?.let {
                Text("Notiz: $it", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SettingsScreen(prefs: ReminderPrefs, onBack: () -> Unit) {
    val context = LocalContext.current
    var times by remember { mutableStateOf(Slot.entries.associateWith { prefs.minutesOfDay(it) }) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Übersicht")
        }
        Text("Erinnerungen", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Zu diesen Zeiten erinnert dich Bekömmlich an deine Mahlzeiten.",
            style = MaterialTheme.typography.bodyMedium
        )
        Slot.entries.forEach { slot ->
            val minutes = times[slot] ?: slot.defaultMinutes
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(slot.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    pickMinutes(context, minutes) { picked ->
                        prefs.save(slot, picked)
                        ReminderScheduler.scheduleAll(context)
                        times = times + (slot to picked)
                    }
                }) { Text(formatMinutes(minutes)) }
            }
        }
    }
}

@Composable
private fun DataScreen(dao: EntryDao, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        val csv = CsvExport.build(dao.allAscending())
                        context.contentResolver.openOutputStream(uri)
                            ?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } != null
                    }.getOrDefault(false)
                }
                Toast.makeText(
                    context,
                    if (ok) "Export gespeichert." else "Export fehlgeschlagen.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Übersicht")
        }
        Text("Meine Daten", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Export", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Alle Einträge als CSV-Datei speichern, zum Beispiel für eine Auswertung.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(colors = peachButtonColors(), 
                    onClick = { exportLauncher.launch("bekoemmlich-export.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Daten exportieren (CSV)") }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Palette.Surface200)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dein Datenschutz", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Wir speichern die Daten nur auf deinem Gerät. Sie werden nicht in irgendwelche " +
                        "Clouds geladen oder an irgendwelche anderen Dritten weitergegeben.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Alle Daten löschen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Entfernt alle Einträge von diesem Gerät.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Alle Daten löschen")
                }
            }
        }
        Text(
            appVersionLabel(context),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Willst du wirklich löschen?") },
            text = { Text("Alle Einträge werden von diesem Gerät entfernt. Das lässt sich nicht rückgängig machen.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { dao.deleteAll() }
                    Toast.makeText(context, "Alle Daten gelöscht.", Toast.LENGTH_SHORT).show()
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
            }
        )
    }
}
