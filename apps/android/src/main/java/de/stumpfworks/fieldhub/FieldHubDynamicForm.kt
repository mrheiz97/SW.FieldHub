package de.stumpfworks.fieldhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.text.DateFormat
import java.util.Date

/** Preview-only renderer. Does not write contact records or imply consent. */
@Composable
internal fun DynamicFormPreview(workspaceId: String, onDismiss: () -> Unit, existing: FormSubmission? = null) {
    val context = LocalContext.current
    val schema = remember(context) {
        runCatching {
            val json = context.assets.open("fieldhub_form_example_v1.json").bufferedReader().use { it.readText() }
            parseFormSchema(json)
        }.getOrNull()
    }
    val values = remember(workspaceId, existing?.id) {
        mutableStateMapOf<String, String>().apply {
            existing?.let { submission ->
                runCatching {
                    val payload = JSONObject(submission.payloadJson)
                    schema?.fields?.forEach { field ->
                        if (payload.has(field.id)) put(field.id, payload.optString(field.id))
                    }
                }
            }
        }
    }
    val errors = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val submissions by remember(dao, workspaceId, schema?.id) {
        dao.formSubmissions(workspaceId, schema?.id ?: "unknown")
    }.collectAsState(initial = emptyList())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(schema?.title ?: "Formular nicht verfügbar") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (schema == null) Text("Die Formular-Konfiguration konnte nicht gelesen werden.")
                schema?.fields?.forEach { field ->
                    when (field.type) {
                        "checkbox" -> Row {
                            Checkbox(checked = values[field.id] == "true", onCheckedChange = { values[field.id] = it.toString() })
                            Text(field.label)
                        }
                        "select" -> {
                            var expanded by remember(field.id) { mutableStateOf(false) }
                            Text(field.label + if (field.required) " *" else "")
                            Box {
                                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(values[field.id] ?: "Bitte auswählen")
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    field.options.forEach { option ->
                                        DropdownMenuItem(text = { Text(option) }, onClick = { values[field.id] = option; expanded = false })
                                    }
                                }
                            }
                        }
                        else -> OutlinedTextField(
                            value = values[field.id].orEmpty(),
                            onValueChange = { values[field.id] = it },
                            label = { Text(field.label + if (field.required) " *" else "") },
                            singleLine = field.type != "multiline",
                            minLines = if (field.type == "multiline") 3 else 1,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                errors.forEach { (id, message) ->
                    Text((schema?.fields?.firstOrNull { it.id == id }?.label ?: id) + ": " + message, color = MaterialTheme.colorScheme.error)
                }
                saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Gespeicherte Einträge: " + submissions.size, style = MaterialTheme.typography.bodySmall)
                Text("Separate lokale Speicherung – keine Kontaktanlage oder Einwilligung.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(enabled = schema != null && !saving, onClick = {
                val form = schema ?: return@TextButton
                errors.clear()
                errors.putAll(validateFormValues(form, values))
                if (errors.isNotEmpty()) return@TextButton
                saving = true
                saveError = null
                val payload = JSONObject()
                form.fields.forEach { field -> payload.put(field.id, values[field.id].orEmpty().trim()) }
                scope.launch {
                    try {
                        dao.saveFormSubmission(FormSubmission(
                            id = existing?.id ?: UUID.randomUUID().toString(),
                            workspaceId = workspaceId,
                            formId = form.id,
                            payloadJson = payload.toString(),
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        ))
                        onDismiss()
                    } catch (error: Exception) {
                        saveError = "Speichern fehlgeschlagen. Bitte erneut versuchen."
                    } finally {
                        saving = false
                    }
                }
            }) { Text(if (saving) "Speichert…" else if (existing != null) "Änderungen speichern" else "Lokal speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}

/** Workspace-scoped browser for generic JSON form submissions. */
@Composable
internal fun DynamicFormEntries(workspaceId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    val schema = remember(context) {
        runCatching {
            parseFormSchema(context.assets.open("fieldhub_form_example_v1.json").bufferedReader().use { it.readText() })
        }.getOrNull()
    }
    val submissions by remember(dao, workspaceId, schema?.id) {
        dao.formSubmissions(workspaceId, schema?.id ?: "unknown")
    }.collectAsState(initial = emptyList())
    var query by remember(workspaceId) { mutableStateOf("") }
    var selected by remember(workspaceId) { mutableStateOf<FormSubmission?>(null) }
    val filtered = remember(submissions, query) {
        submissions.filter { submission ->
            if (query.isBlank()) true
            else runCatching {
                val payload = JSONObject(submission.payloadJson)
                payload.keys().asSequence().any { key ->
                    payload.optString(key).contains(query, ignoreCase = true)
                }
            }.getOrDefault(false)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Formulareinträge") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Einträge durchsuchen") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("${filtered.size} von ${submissions.size} Einträgen", style = MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { entry ->
                        val payload = remember(entry.payloadJson) {
                            runCatching { JSONObject(entry.payloadJson) }.getOrNull()
                        }
                        Card(onClick = { selected = entry }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(payload?.optString("name")?.takeIf { it.isNotBlank() } ?: "Formulareintrag", style = MaterialTheme.typography.titleMedium)
                                Text(DateFormat.getDateTimeInstance().format(Date(entry.createdAt)), style = MaterialTheme.typography.bodySmall)
                                Text("Öffnen und bearbeiten", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) Text("Keine passenden Einträge.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
    selected?.let { entry ->
        DynamicFormPreview(workspaceId = workspaceId, existing = entry, onDismiss = { selected = null })
    }
}
