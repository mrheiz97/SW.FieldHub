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

/** Preview-only renderer. Does not write contact records or imply consent. */
@Composable
internal fun DynamicFormPreview(workspaceId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val schema = remember(context) {
        runCatching {
            val json = context.assets.open("fieldhub_form_example_v1.json").bufferedReader().use { it.readText() }
            parseFormSchema(json)
        }.getOrNull()
    }
    val values = remember(workspaceId) { mutableStateMapOf<String, String>() }
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
                            id = UUID.randomUUID().toString(),
                            workspaceId = workspaceId,
                            formId = form.id,
                            payloadJson = payload.toString(),
                            createdAt = System.currentTimeMillis()
                        ))
                        onDismiss()
                    } catch (error: Exception) {
                        saveError = "Speichern fehlgeschlagen. Bitte erneut versuchen."
                    } finally {
                        saving = false
                    }
                }
            }) { Text(if (saving) "Speichert…" else "Lokal speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
