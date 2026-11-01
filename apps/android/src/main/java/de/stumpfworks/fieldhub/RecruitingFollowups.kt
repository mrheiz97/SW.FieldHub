package de.stumpfworks.fieldhub

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

internal fun followupLabel(dueAt: Long): String {
    val now = Calendar.getInstance()
    val start = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    return when {
        dueAt < System.currentTimeMillis() -> "Überfällig"
        dueAt < start + 86400000L -> "Heute"
        else -> "Demnächst"
    }
}

@Composable
internal fun FollowupEditor(contactId: String) {
    val context = LocalContext.current
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val entries by remember(dao, contactId) { dao.followups(contactId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var title by remember(contactId) { mutableStateOf("") }
    var dueAt by remember(contactId) { mutableLongStateOf(0L) }
    val format = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Wiedervorlagen", style = MaterialTheme.typography.titleLarge, color = Color.White)
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Rückruf, Probetraining oder Aufgabe") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = {
            val c = Calendar.getInstance()
            DatePickerDialog(context, { _, year, month, day ->
                val chosen = Calendar.getInstance().apply { set(year, month, day, 12, 0, 0); set(Calendar.MILLISECOND, 0) }
                TimePickerDialog(context, { _, hour, minute ->
                    chosen.set(Calendar.HOUR_OF_DAY, hour)
                    chosen.set(Calendar.MINUTE, minute)
                    dueAt = chosen.timeInMillis
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }) { Text(if (dueAt == 0L) "Datum und Uhrzeit wählen" else format.format(Date(dueAt))) }
        Button(enabled = title.isNotBlank() && dueAt > 0L, onClick = {
            val newEntry = RecruitingFollowup(UUID.randomUUID().toString(), contactId, title.trim(), dueAt)
            scope.launch { dao.saveFollowup(newEntry) }
            title = ""
            dueAt = 0L
        }) { Text("Wiedervorlage speichern") }
        entries.forEach { entry ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230))) {
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    Checkbox(checked = entry.done, onCheckedChange = { done ->
                        scope.launch {
                            dao.setFollowupDone(entry.id, done)
                            if (done) dao.addActivity(RecruitingActivity(UUID.randomUUID().toString(), contactId, "Wiedervorlage erledigt: ${entry.title}", System.currentTimeMillis()))
                        }
                    })
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, color = Color.White)
                        Text(format.format(Date(entry.dueAt)) + " · " + (if (entry.done) "Erledigt" else followupLabel(entry.dueAt)), color = Color.LightGray)
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecruitingFollowupSummary(contacts: List<RecruitingContact>, onOpenContact: (String) -> Unit) {
    val context = LocalContext.current
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    val followups by remember(dao) { dao.allFollowups() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val contactIds = contacts.map { it.id }.toSet()
    val active = followups.filter { !it.done && it.contactId in contactIds }
    val due = active.filter { it.dueAt <= System.currentTimeMillis() }
    if (active.isNotEmpty()) {
        Text("Wiedervorlagen · ${due.size} fällig · ${active.size} offen", style = MaterialTheme.typography.titleMedium, color = Color.White)
        active.take(5).forEach { entry ->
            val contact = contacts.firstOrNull { it.id == entry.contactId }
            if (contact != null) {
                OutlinedButton(onClick = { onOpenContact(contact.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("${followupLabel(entry.dueAt)} · ${contact.name}: ${entry.title}", maxLines = 2)
                }
            }
        }
    }
}
