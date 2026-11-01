package de.stumpfworks.fieldhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Preview-only renderer. Does not write contact records or imply consent. */
@Composable
internal fun DynamicFormPreview(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val schema = remember(context) {
        runCatching {
            val json = context.assets.open("fieldhub_form_example_v1.json").bufferedReader().use { it.readText() }
            parseFormSchema(json)
        }.getOrNull()
    }
    val values = remember { mutableStateMapOf<String, String>() }
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
                Text("Nur Vorschau: Keine Speicherung und keine Einwilligungserfassung.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}
