package de.stumpfworks.fieldhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

@OptIn(ExperimentalMaterial3Api::class)
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
    var detailId by remember(workspaceId) { mutableStateOf<String?>(null) }
    val detail = contacts.firstOrNull { it.id == detailId }
    val filtered = contacts.filter {
        (selectedStatus == "Alle" || it.status == selectedStatus) &&
            (search.isBlank() || listOf(it.name, it.phone, it.email, it.notes).any { value -> value.contains(search, ignoreCase = true) })
    }

    if (detail != null) {
        RecruitingDetailScreen(detail, onBack = { detailId = null }, onEdit = { editing = detail })
    } else Column {
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
                Card(onClick = { detailId = contact.id }, colors = CardDefaults.cardColors(containerColor = recruitingPanel)) {
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
        var signature by remember(original?.id, creating) { mutableStateOf(original?.consentSignature.orEmpty()) }
        var confirmDelete by remember(original?.id, creating) { mutableStateOf(false) }
        var statusExpanded by remember(original?.id, creating) { mutableStateOf(false) }
        Dialog(
            onDismissRequest = { creating = false; editing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 540.dp).imePadding(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF1C2230),
                tonalElevation = 6.dp
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (original == null) "Neuer Kontakt" else "Kontakt bearbeiten",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text("Recruiting · Kontaktdaten", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
                        }
                        TextButton(onClick = { creating = false; editing = null }) { Text("Schließen") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("PERSON", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = name, onValueChange = { name = it },
                            label = { Text("Name *") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = phone, onValueChange = { phone = it },
                            label = { Text("Telefonnummer") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            label = { Text("E-Mail-Adresse") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                        Text("RECRUITING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        ExposedDropdownMenuBox(
                            expanded = statusExpanded,
                            onExpandedChange = { statusExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = status, onValueChange = {},
                                readOnly = true, label = { Text("Status") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = statusExpanded,
                                onDismissRequest = { statusExpanded = false }
                            ) {
                                statuses.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = { status = option; statusExpanded = false }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = notes, onValueChange = { notes = it },
                            label = { Text("Notizen") }, minLines = 2, maxLines = 4,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        )
                        HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = consent, onCheckedChange = { consent = it; if (!it) signature = "" })
                            Column(Modifier.weight(1f)) {
                                Text("Einwilligung zur Kontaktaufnahme", style = MaterialTheme.typography.bodyMedium)
                                Text("Nur aktivieren, wenn sie tatsächlich vorliegt.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (consent) {
                            Text(
                                "Ich bin damit einverstanden, dass die hier erfassten Kontaktdaten verwendet werden, um mich wegen meines Interesses an einer Teilnahme oder Mitgliedschaft zu kontaktieren. Die Einwilligung ist freiwillig und kann jederzeit widerrufen werden.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray
                            )
                            ConsentSignaturePad(value = signature, onChange = { signature = it })
                            if (signature.isBlank()) {
                                Text("Bitte vor dem Speichern unterschreiben lassen.", color = Color(0xFFFFCC80), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (original != null) {
                            if (confirmDelete) {
                                Text("Diesen Kontakt dauerhaft löschen?", color = Color(0xFFFFCC80))
                                Row {
                                    TextButton(onClick = {
                                        scope.launch { dao.deleteActivities(original.id); dao.deleteContact(original) }
                                        editing = null
                                    }) { Text("Endgültig löschen") }
                                    TextButton(onClick = { confirmDelete = false }) { Text("Behalten") }
                                }
                            } else {
                                TextButton(onClick = { confirmDelete = true }) { Text("Kontakt löschen") }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { creating = false; editing = null },
                            modifier = Modifier.weight(1f)
                        ) { Text("Abbrechen") }
                        Button(
                            enabled = name.isNotBlank() && (phone.isNotBlank() || email.isNotBlank()) && (!consent || signature.isNotBlank() || (original?.consentToContact == true && original.consentSignature.isBlank())),
                            modifier = Modifier.weight(1f),
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
                                            createdAt = original?.createdAt ?: now,
                                            consentSignature = if (consent) signature else ""
                                        )
                                    )
                                }
                                creating = false
                                editing = null
                            }
                        ) { Text("Speichern") }
                    }
                }
            }
        }
    }
}
