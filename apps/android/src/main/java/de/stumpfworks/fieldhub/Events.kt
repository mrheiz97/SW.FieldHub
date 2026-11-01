package de.stumpfworks.fieldhub

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Generic event model. Demo repository can later be replaced with an API-backed repository. */
internal data class FieldEvent(
    val id: String,
    val workspaceId: String,
    val title: String,
    val scheduleLabel: String,
    val location: String,
    val description: String,
    val type: String
)

internal object DemoEventRepository {
    val events = listOf(
        FieldEvent("training-1", "ravens", "Teamtraining", "Dienstag · 18:30 Uhr", "Trainingsplatz · Demo", "Gemeinsames Training der Ravens.", "Training"),
        FieldEvent("training-2", "ravens", "Teamtraining", "Samstag · 10:00 Uhr", "Trainingsplatz · Demo", "Technik, Spielformen und Fitness.", "Training"),
        FieldEvent("club-1", "ravens", "Vereinsaktion", "Sonntag · 11:00 Uhr", "Vereinsgelände · Demo", "Gemeinsame Vereinsaktion.", "Verein"),
        FieldEvent("meeting-1", "stumpfworks", "Projektbesprechung", "Mittwoch · 17:00 Uhr", "Online · Demo", "Abstimmung der nächsten Entwicklungsschritte.", "Meeting")
    )
    fun forWorkspace(workspaceId: String) = events.filter { it.workspaceId == workspaceId }
}

internal enum class EventAttendance { Unknown, Yes, No }

@Composable
internal fun EventListScreen(workspaceId: String) {
    var opened by remember(workspaceId) { mutableStateOf<FieldEvent?>(null) }
    val events = remember(workspaceId) { DemoEventRepository.forWorkspace(workspaceId) }
    Column {
        Text("Termine", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Alle Termine deines Workspaces", color = Color.LightGray)
        Spacer(Modifier.height(20.dp))
        if (events.isEmpty()) {
            Text("Noch keine Termine vorhanden.", color = Color.LightGray)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(events, key = { it.id }) { event ->
                    Card(
                        onClick = { opened = event },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text(event.type.uppercase(), color = Color(0xFFBDA9FF), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.height(6.dp))
                            Text(event.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(event.scheduleLabel, color = Color.LightGray)
                            Text(event.location, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    opened?.let { event ->
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(event.title) },
            text = {
                Column {
                    Text(event.scheduleLabel)
                    Text(event.location)
                    Spacer(Modifier.height(12.dp))
                    Text(event.description)
                    Spacer(Modifier.height(16.dp))
                    EventAttendanceControls(event)
                }
            },
            confirmButton = { TextButton(onClick = { opened = null }) { Text("Schließen") } }
        )
    }
}

@Composable
internal fun EventAttendanceControls(event: FieldEvent) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("fieldhub_demo_events", Context.MODE_PRIVATE) }
    val key = "attendance_${event.workspaceId}_${event.id}"
    var state by remember(key) {
        mutableStateOf(when (prefs.getString(key, "unknown")) {
            "yes" -> EventAttendance.Yes
            "no" -> EventAttendance.No
            else -> EventAttendance.Unknown
        })
    }
    fun update(value: EventAttendance) {
        state = value
        prefs.edit().putString(key, when(value) {
            EventAttendance.Yes -> "yes"
            EventAttendance.No -> "no"
            EventAttendance.Unknown -> "unknown"
        }).apply()
    }
    Text(when(state) {
        EventAttendance.Yes -> "Du hast zugesagt"
        EventAttendance.No -> "Du hast abgesagt"
        EventAttendance.Unknown -> "Deine Teilnahme ist offen"
    })
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { update(EventAttendance.Yes) }, enabled = state != EventAttendance.Yes, modifier = Modifier.weight(1f)) {
            Text("Zusagen")
        }
        OutlinedButton(onClick = { update(EventAttendance.No) }, enabled = state != EventAttendance.No, modifier = Modifier.weight(1f)) {
            Text("Absagen")
        }
    }
    Spacer(Modifier.height(8.dp))
    Text("Nur lokal gespeichert · Demo", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
}
