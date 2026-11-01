package de.stumpfworks.fieldhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Composable
internal fun RecruitingDetailScreen(contact: RecruitingContact, onBack: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val activities by remember(dao, contact.id) { dao.activities(contact.id) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var note by remember(contact.id) { mutableStateOf("") }
    val format = remember { SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("‹ Kontakte") }
            TextButton(onClick = onEdit) { Text("Bearbeiten") }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230)), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(contact.name, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(contact.status, color = MaterialTheme.colorScheme.primary)
                        if (contact.phone.isNotBlank()) Text(contact.phone, color = Color.LightGray)
                        if (contact.email.isNotBlank()) Text(contact.email, color = Color.LightGray)
                        if (contact.notes.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(contact.notes, color = Color.White)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230)), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text("Einwilligung", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        val signed = contact.consentToContact && contact.consentSignature.isNotBlank()
                        Text(
                            when {
                                signed -> "Einwilligung mit Unterschrift erfasst"
                                contact.consentToContact -> "Einwilligung dokumentiert (ohne gespeicherte Unterschrift)"
                                else -> "Keine Einwilligung dokumentiert"
                            },
                            color = if (contact.consentToContact) Color(0xFFB5DCC4) else Color(0xFFFFCC80)
                        )
                        contact.consentRecordedAt?.let { Text("Erfasst: " + format.format(Date(it)), color = Color.LightGray) }
                        if (signed) {
                            Spacer(Modifier.height(8.dp))
                            Text("Gespeicherte Unterschrift:", color = Color.LightGray)
                            ConsentSignaturePreview(contact.consentSignature)
                        }
                        Text("Nur lokal auf diesem Gerät gespeichert.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Text("Kontakthistorie", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("Gespräch oder Aktivität notieren") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = note.isNotBlank(),
                    onClick = {
                        val text = note.trim()
                        scope.launch {
                            dao.addActivity(RecruitingActivity(UUID.randomUUID().toString(), contact.id, text, System.currentTimeMillis()))
                        }
                        note = ""
                    }
                ) { Text("Eintrag hinzufügen") }
            }
            if (activities.isEmpty()) item { Text("Noch keine Gesprächseinträge.", color = Color.LightGray) }
            items(activities, key = { it.id }) { activity ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(format.format(Date(activity.createdAt)), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(activity.text, color = Color.White)
                    }
                }
            }
        }
    }
}
