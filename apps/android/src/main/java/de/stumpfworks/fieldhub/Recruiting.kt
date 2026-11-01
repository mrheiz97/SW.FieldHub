package de.stumpfworks.fieldhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
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

private val recruitingPanel = Color(0xFF1C2230)
private val statuses = listOf("Neu", "Kontaktiert", "Interessiert", "Probetraining", "Gewonnen", "Abgesagt")

@Composable
internal fun RecruitingScreen(workspaceId: String) {
    val context = LocalContext.current
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val contacts by remember(dao, workspaceId) { dao.contacts(workspaceId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var search by remember(workspaceId) { mutableStateOf("") }
    var selectedStatus by remember(workspaceId) { mutableStateOf("Alle") }
    var editing by remember(workspaceId) { mutableStateOf<RecruitingContact?>(null) }
    var creating by remember(workspaceId) { mutableStateOf(false) }
    val filtered = contacts.filter {
        (selectedStatus == "Alle" || it.status == selectedStatus) &&
            (search.isBlank() || listOf(it.name, it.phone, it.email, it.notes).any { value -> value.contains(search, ignoreCase = true) })
    }

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Recruiting", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            TextButton(onClick = { creating = true }) { Text("+ Kontakt") }
        }
        Text("${contacts.size} Kontakte · nur auf diesem Gerät", color = Color.LightGray)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = search, onValueChange = { search = it }, label = { Text("Name, Telefon, E-Mail suchen") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (listOf("Alle") + statuses).forEach { status ->
                FilterChip(selected = selectedStatus == status, onClick = { selectedStatus = status }, label = { Text(status) })
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.id }) { contact ->
                Card(onClick = { editing = contact }, colors = CardDefaults.cardColors(containerColor = recruitingPanel)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(contact.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Text(contact.status, color = MaterialTheme.colorScheme.primary)
                        if (contact.phone.isNotBlank()) Text(contact.phone, color = Color.LightGray)
                        if (contact.email.isNotBlank()) Text(contact.email, color = Color.LightGray)
                        if (contact.notes.isNotBlank()) Text(contact.notes, color = Color.LightGray, maxLines = 2)
                        if (!contact.consentToContact) Text("Keine dokumentierte Einwilligung", color = Color(0xFFFFCC80), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (filtered.isEmpty()) item { Text("Keine Kontakte gefunden. Lege deinen ersten Kontakt an.", color = Color.LightGray) }
        }
    }

    if (creating || editing != null) {
        val original = editing
        var name by remember(original?.id, creating) { mutableStateOf(original?.name.orEmpty()) }
        var phone by remember(original?.id, creating) { mutableStateOf(original?.phone.orEmpty()) }
        var email by remember(original?.id, creating) { mutableStateOf(original?.email.orEmpty()) }
        var notes by remember(original?.id, creating) { mutableStateOf(original?.notes.orEmpty()) }
        var status by remember(original?.id, creating) { mutableStateOf(original?.status ?: "Neu") }
        var consent by remember(original?.id, creating) { mutableStateOf(original?.consentToContact ?: false) }
        var confirmDelete by remember(original?.id, creating) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (original == null) "Kontakt aufnehmen" else "Kontakt bearbeiten") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name *") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it }, label = { Text("Telefon") }, singleLine = true)
                    OutlinedTextField(email, { email = it }, label = { Text("E-Mail") }, singleLine = true)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notizen") }, minLines = 3)
                    Text("Status", fontWeight = FontWeight.SemiBold)
                    statuses.forEach { option ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = status == option, onClick = { status = option })
                            Text(option, color = Color.White)
                        }
                    }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it })
                        Text("Einwilligung zur Kontaktaufnahme liegt vor", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Nur bestätigen, wenn die Person tatsächlich zugestimmt hat. Einwilligung kann widerrufen werden.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                    if (original != null) {
                        if (confirmDelete) {
                            Text("Kontakt wirklich dauerhaft löschen?")
                            TextButton(onClick = { scope.launch { dao.deleteContact(original) }; editing = null }) { Text("Ja, löschen") }
                            TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") }
                        } else {
                            TextButton(onClick = { confirmDelete = true }) { Text("Kontakt löschen") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && (phone.isNotBlank() || email.isNotBlank()),
                    onClick = {
                        val now = System.currentTimeMillis()
                        scope.launch {
                            dao.saveContact(
                                RecruitingContact(
                                    id = original?.id ?: UUID.randomUUID().toString(),
                                    workspaceId = workspaceId,
                                    name = name.trim(), phone = phone.trim(), email = email.trim(),
                                    notes = notes.trim(), status = status, consentToContact = consent,
                                    consentRecordedAt = if (consent) original?.consentRecordedAt ?: now else null,
                                    createdAt = original?.createdAt ?: now
                                )
                            )
                        }
                        creating = false; editing = null
                    }
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text("Schließen") } }
        )
    }
}
