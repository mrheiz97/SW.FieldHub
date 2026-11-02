package de.stumpfworks.fieldhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val dashboardPanel = Color(0xFF1C2230)
private val phases = listOf("Neu", "Kontaktiert", "Interessiert", "Probetraining", "Gewonnen", "Abgesagt")

@Composable
internal fun RecruitingDashboard(
    contacts: List<RecruitingContact>,
    workspaceId: String,
    onFilterStatus: (String) -> Unit,
    onOpenContact: (String) -> Unit
) {
    val context = LocalContext.current
    val dashboard = remember(context, workspaceId) { runCatching { loadLocalUiConfig(context).dashboardFor(workspaceId) }.getOrNull() }
    val dao = remember(context) { FieldHubDatabase.get(context).dao() }
    val followups by remember(dao) { dao.allFollowups() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val contactIds = remember(contacts) { contacts.map { it.id }.toSet() }
    val now = System.currentTimeMillis()
    val overdue = followups.filter { !it.done && it.contactId in contactIds && it.dueAt < now }
    val defaultMetrics = listOf(
        UiMetric("total", "Kontakte", "contacts.total", "filter.all"),
        UiMetric("interested", "Interessiert", "contacts.interested", "filter.interested"),
        UiMetric("trial", "Probetraining", "contacts.trial", "filter.trial"),
        UiMetric("due", "Fällige Rückrufe", "followups.due", "open.due")
    )
    val metrics = dashboard?.metrics ?: defaultMetrics
    fun valueFor(source: String): Int = when (source) {
        "contacts.total" -> contacts.size
        "contacts.interested" -> contacts.count { it.status == "Interessiert" }
        "contacts.trial" -> contacts.count { it.status == "Probetraining" }
        "followups.due" -> overdue.size
        else -> 0
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(dashboard?.title ?: "Überblick", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = when { maxWidth >= 840.dp -> 4; maxWidth >= 600.dp -> 3; else -> 2 }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { metric ->
                            Card(
                                onClick = {
                                    when (metric.action) {
                                        "filter.all" -> onFilterStatus("Alle")
                                        "filter.interested" -> onFilterStatus("Interessiert")
                                        "filter.trial" -> onFilterStatus("Probetraining")
                                        "open.due" -> overdue.minByOrNull { it.dueAt }?.let { onOpenContact(it.contactId) }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = dashboardPanel),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(Modifier.fillMaxWidth().heightIn(min = 92.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(valueFor(metric.dataSource).toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(metric.label, color = Color.LightGray, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (dashboard?.showPhases != false) {
        Text("Recruiting-Phasen", style = MaterialTheme.typography.titleMedium, color = Color.White)
        phases.forEach { phase ->
            val count = contacts.count { it.status == phase }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(phase, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(104.dp))
                LinearProgressIndicator(
                    progress = { if (contacts.isEmpty()) 0f else count.toFloat() / contacts.size },
                    modifier = Modifier.weight(1f).height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
                Text(count.toString(), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
        }
        Text("Kennzahlen aus lokalen Kontakten und Wiedervorlagen", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
    }
}
