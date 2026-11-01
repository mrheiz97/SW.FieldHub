package de.stumpfworks.fieldhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun LocalTrainingOverviewScreen(workspaceId: String) {
    val context = LocalContext.current
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    val events by remember(dao, workspaceId) { dao.events(workspaceId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val trainings = events.filter { it.type.equals("Training", ignoreCase = true) }
        .sortedBy { if (it.startAt > 0L) it.startAt else Long.MAX_VALUE }

    Column {
        Text("Trainingsübersicht", style = MaterialTheme.typography.headlineMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Lokale Trainings und Rückmeldungen", color = Color.LightGray)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(trainings, key = { it.id }) { event ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2230))) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(event.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(event.scheduleLabel, color = Color.LightGray)
                        Text(event.location, color = Color.LightGray)
                        Spacer(Modifier.height(8.dp))
                        Text("Rückmeldung auf diesem Gerät: " + when (event.attendance) {
                            "yes" -> "Zugesagt"
                            "no" -> "Abgesagt"
                            else -> "Offen"
                        }, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (trainings.isEmpty()) item {
                Text("Noch keine eigenen Trainings. Erstelle eines unter Termine.", color = Color.LightGray)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Hinweis: Andere Spielergeräte sind noch nicht verbunden. Teamweite Zahlen erfordern die FieldHub-API.", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
    }
}
