package de.stumpfworks.fieldhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.util.UUID

private val panel = Color(0xFF1C2230)
private val muted = Color.LightGray

@Composable
internal fun LocalEventsScreen(workspaceId: String, canManage: Boolean) {
    val context = LocalContext.current
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val events by remember(dao, workspaceId) { dao.events(workspaceId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<LocalEvent?>(null) }
    var creating by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Termine", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            if (canManage) TextButton(onClick = { creating = true }) { Text("+ Neu") }
        }
        Text("Deine Termine · lokal gespeichert", color = muted)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(events, key = { it.id }) { event ->
                Card(onClick = { editing = event }, colors = CardDefaults.cardColors(containerColor = panel)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(event.type, color = MaterialTheme.colorScheme.primary)
                        Text(event.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(event.scheduleLabel, color = muted)
                        Text(event.location, color = muted)
                    }
                }
            }
            if (events.isEmpty()) item { Text("Noch keine eigenen Termine. Die bisherigen Beispieltermine sind weiterhin auf der Startseite.", color = muted) }
        }
    }
    if (creating || editing != null) {
        val current = editing
        var title by remember(current?.id, creating) { mutableStateOf(current?.title.orEmpty()) }
        var whenText by remember(current?.id, creating) { mutableStateOf(current?.scheduleLabel.orEmpty()) }
        var location by remember(current?.id, creating) { mutableStateOf(current?.location.orEmpty()) }
        var description by remember(current?.id, creating) { mutableStateOf(current?.description.orEmpty()) }
        var type by remember(current?.id, creating) { mutableStateOf(current?.type ?: "Training") }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (current == null) "Termin erstellen" else "Termindetails") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true, readOnly = !canManage)
                    OutlinedTextField(whenText, { whenText = it }, label = { Text("Datum / Uhrzeit") }, singleLine = true, readOnly = !canManage)
                    OutlinedTextField(location, { location = it }, label = { Text("Ort") }, singleLine = true, readOnly = !canManage)
                    OutlinedTextField(type, { type = it }, label = { Text("Art") }, singleLine = true, readOnly = !canManage)
                    OutlinedTextField(description, { description = it }, label = { Text("Beschreibung") }, readOnly = !canManage)
                    if (current != null && canManage) TextButton(onClick = {
                        scope.launch { dao.deleteEvent(current) }; editing = null
                    }) { Text("Termin löschen") }
                }
            },
            confirmButton = {
                if (canManage) TextButton(
                    enabled = title.isNotBlank() && whenText.isNotBlank(),
                    onClick = {
                        scope.launch {
                            dao.saveEvent(LocalEvent(current?.id ?: UUID.randomUUID().toString(), workspaceId, title.trim(), whenText.trim(), location.trim(), description.trim(), type.trim()))
                        }
                        creating = false; editing = null
                    }
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text("Schließen") } }
        )
    }
}

@Composable
internal fun LocalTasksScreen(workspaceId: String) {
    val context = LocalContext.current
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val tasks by remember(dao, workspaceId) { dao.tasks(workspaceId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<LocalTask?>(null) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Aufgaben", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            TextButton(onClick = { creating = true }) { Text("+ Neu") }
        }
        Text("${tasks.count { it.done }} von ${tasks.size} erledigt · lokal gespeichert", color = muted)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tasks, key = { it.id }) { task ->
                Card(colors = CardDefaults.cardColors(containerColor = panel)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = task.done, onCheckedChange = { scope.launch { dao.saveTask(task.copy(done = it)) } })
                        Column(Modifier.weight(1f)) {
                            Text(task.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("${task.priority} · ${task.description}", color = muted)
                        }
                        TextButton(onClick = { editing = task }) { Text("Öffnen") }
                    }
                }
            }
            if (tasks.isEmpty()) item { Text("Noch keine eigenen Aufgaben. Erstelle deine erste Aufgabe.", color = muted) }
        }
    }
    if (creating || editing != null) {
        val current = editing
        var title by remember(current?.id, creating) { mutableStateOf(current?.title.orEmpty()) }
        var description by remember(current?.id, creating) { mutableStateOf(current?.description.orEmpty()) }
        var priority by remember(current?.id, creating) { mutableStateOf(current?.priority ?: "Normal") }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (current == null) "Aufgabe erstellen" else "Aufgabe bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Titel") }, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("Beschreibung") })
                    Text("Priorität")
                    Row {
                        listOf("Niedrig", "Normal", "Hoch").forEach { value ->
                            FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text(value) })
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    if (current != null) TextButton(onClick = { scope.launch { dao.deleteTask(current) }; editing = null }) { Text("Aufgabe löschen") }
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = {
                    scope.launch { dao.saveTask(LocalTask(current?.id ?: UUID.randomUUID().toString(), workspaceId, title.trim(), description.trim(), priority, current?.done ?: false)) }
                    creating = false; editing = null
                }) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text("Abbrechen") } }
        )
    }
}

}
