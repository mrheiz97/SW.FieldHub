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

internal enum class DemoRole(val label: String) { Player("Spieler"), Coach("Trainer"), Admin("Administrator") }
private val Panel = Color(0xFF1C2230)
private val Highlight = Color(0xFFBDA9FF)

@Composable
internal fun RoleProfileScreen(workspaceId: String, role: DemoRole, onRoleChange: (DemoRole) -> Unit) {
    Column {
        Text("Profil & Demo-Rolle", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Aktiver Workspace: $workspaceId", color = Color.LightGray)
        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Demo-Rolle auswählen", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(10.dp))
                DemoRole.entries.forEach { option ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = role == option, onClick = { onRoleChange(option) })
                        Text(option.label, color = Color.White)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Nur Vorschau. Diese Auswahl verleiht keine echten Zugriffsrechte.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private data class DemoTask(val id: String, val title: String, val detail: String)
private val demoTasks = mapOf(
    "ravens" to listOf(
        DemoTask("equipment", "Trainingsmaterial prüfen", "Bälle, Hütchen und Leibchen"),
        DemoTask("attendance", "Rückmeldungen prüfen", "Offene Zusagen im Blick behalten"),
        DemoTask("field", "Trainingsplatz vorbereiten", "Vor Trainingsbeginn")
    ),
    "stumpfworks" to listOf(
        DemoTask("review", "FieldHub-Prototyp testen", "Navigation und Darstellung"),
        DemoTask("docs", "Dokumentation ergänzen", "Nächste Entwicklungsschritte")
    ),
    "sales" to listOf(
        DemoTask("contacts", "Neue Kontakte nachfassen", "Demo-Kontaktliste prüfen"),
        DemoTask("planning", "Tagestour planen", "Gebiete und Termine")
    )
)

@Composable
internal fun DemoTasksScreen(workspaceId: String) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("fieldhub_demo_tasks", Context.MODE_PRIVATE) }
    val tasks = remember(workspaceId) { demoTasks[workspaceId].orEmpty() }
    val checked = remember(workspaceId) {
        mutableStateMapOf<String, Boolean>().apply {
            tasks.forEach { task -> put(task.id, prefs.getBoolean("${workspaceId}_${task.id}", false)) }
        }
    }
    Column {
        Text("Aufgaben", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("${tasks.count { checked[it.id] == true }} von ${tasks.size} erledigt", color = Color.LightGray)
        Spacer(Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tasks, key = { it.id }) { task ->
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked[task.id] == true,
                            onCheckedChange = { value ->
                                checked[task.id] = value
                                prefs.edit().putBoolean("${workspaceId}_${task.id}", value).apply()
                            }
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(task.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(task.detail, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CoachOverviewScreen() {
    val event = DemoEventRepository.events.first { it.id == "training-1" }
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("fieldhub_demo_events", Context.MODE_PRIVATE) }
    val ownStatus = prefs.getString("attendance_ravens_training-1", "unknown")
    val roster = listOf(
        "Du (Demo)" to when(ownStatus) { "yes" -> "Zugesagt"; "no" -> "Abgesagt"; else -> "Offen" },
        "Alex (Beispiel)" to "Zugesagt",
        "Sam (Beispiel)" to "Zugesagt",
        "Robin (Beispiel)" to "Offen",
        "Kim (Beispiel)" to "Abgesagt"
    )
    Column {
        Text("Trainerübersicht", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(event.title + " · " + event.scheduleLabel, color = Color.LightGray)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Zusagen" to roster.count { it.second == "Zugesagt" }, "Absagen" to roster.count { it.second == "Abgesagt" }, "Offen" to roster.count { it.second == "Offen" }).forEach { (name, count) ->
                Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(count.toString(), color = Highlight, style = MaterialTheme.typography.headlineSmall)
                        Text(name, color = Color.LightGray, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                roster.forEach { (name, status) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, color = Color.White)
                        Text(status, color = Highlight)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Beispielpersonen und lokale Demo-Werte. Keine echte Teamdatenbank.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
    }
}
