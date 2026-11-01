package de.stumpfworks.fieldhub

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val DEFAULT_SERVER_URL = "https://api.fieldhub.stumpfworks.de"
private const val SERVER_KEY = "server_base_url"

internal fun savedServerUrl(context: Context): String =
    context.getSharedPreferences("fieldhub_local", Context.MODE_PRIVATE)
        .getString(SERVER_KEY, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL

private fun normalizeServerUrl(input: String): String? {
    val value = input.trim().trimEnd('/')
    val uri = Uri.parse(value)
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null ||
        uri.query != null || uri.fragment != null || (uri.path != null && uri.path != "")) return null
    return value
}

@Composable
internal fun ServerSettingsDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var url by remember { mutableStateOf(savedServerUrl(context)) }
    val normalized = remember(url) { normalizeServerUrl(url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Servereinstellungen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("FieldHub kann später mit deinem eigenen Server verbunden werden.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Server-URL") },
                    placeholder = { Text("https://api.example.org") },
                    singleLine = true,
                    isError = normalized == null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (normalized == null) {
                    Text("Bitte eine gültige HTTPS-Adresse ohne Pfad eingeben.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text("Die Adresse wird nur auf diesem Gerät gespeichert. Eine Serververbindung ist noch nicht implementiert.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { url = DEFAULT_SERVER_URL }) { Text("Standardadresse verwenden") }
            }
        },
        confirmButton = {
            TextButton(enabled = normalized != null, onClick = {
                context.getSharedPreferences("fieldhub_local", Context.MODE_PRIVATE)
                    .edit().putString(SERVER_KEY, normalized).apply()
                onDismiss()
            }) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } }
    )
}
