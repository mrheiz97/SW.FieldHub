package de.stumpfworks.fieldhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Background = Color(0xFF10131C)
private val Surface = Color(0xFF1C2230)
private val Accent = Color(0xFFBDA9FF)

data class Workspace(val id: String, val name: String, val role: String, val headline: String, val action: String)
private val workspaces = listOf(
    Workspace("ravens", "Ravens Fürstenwalde", "Spieler", "Bereit fürs Training?", "Training ansehen"),
    Workspace("stumpfworks", "stumpfWORKS", "Administrator", "Was steht heute an?", "Aufgaben öffnen"),
    Workspace("sales", "Field Sales Demo", "Recruiter", "Neue Kontakte erfassen", "Kontakt hinzufügen")
)
private enum class Section(val title: String) { Today("Heute"), Tasks("Aufgaben"), Profile("Profil") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FieldHubApp() }
    }
}

@Composable
private fun FieldHubApp() {
    var selectedId by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf(Section.Today) }
    var showWorkspaces by remember { mutableStateOf(false) }
    val selected = workspaces.firstOrNull { it.id == selectedId }
    val colors = darkColorScheme(primary = Accent, background = Background, surface = Surface, onSurface = Color.White)
    MaterialTheme(colorScheme = colors) {
        if (selected == null) {
            WorkspacePicker { selectedId = it.id; section = Section.Today }
        } else {
            Scaffold(
                containerColor = Background,
                bottomBar = {
                    NavigationBar(containerColor = Surface) {
                        Section.entries.forEach { item ->
                            NavigationBarItem(
                                selected = section == item,
                                onClick = { section = item },
                                icon = {
                                    when (item) {
                                        Section.Today -> Icon(Icons.Default.Home, contentDescription = null)
                                        Section.Tasks -> Icon(Icons.Default.CheckCircle, contentDescription = null)
                                        Section.Profile -> Icon(Icons.Default.Person, contentDescription = null)
                                    }
                                },
                                label = { Text(item.title) }
                            )
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                    Spacer(Modifier.height(20.dp))
                    TextButton(onClick = { showWorkspaces = true }) {
                        Icon(Icons.Default.Business, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(selected.name)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Workspace wechseln")
                    }
                    Spacer(Modifier.height(16.dp))
                    when (section) {
                        Section.Today -> TodayScreen(selected)
                        Section.Tasks -> PlaceholderScreen("Deine Aufgaben", "Hier erscheinen später die Aufgaben dieses Workspaces.")
                        Section.Profile -> PlaceholderScreen("Dein Profil", "Deine Mitgliedschaften und Einstellungen folgen.")
                    }
                }
            }
            if (showWorkspaces) {
                AlertDialog(
                    onDismissRequest = { showWorkspaces = false },
                    title = { Text("Workspace wechseln") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            workspaces.forEach { workspace ->
                                TextButton(
                                    onClick = { selectedId = workspace.id; section = Section.Today; showWorkspaces = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(workspace.name, modifier = Modifier.weight(1f))
                                    if (workspace.id == selectedId) Icon(Icons.Default.Check, contentDescription = "Ausgewählt")
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { showWorkspaces = false }) { Text("Schließen") } }
                )
            }
        }
    }
}

@Composable
private fun WorkspacePicker(onSelect: (Workspace) -> Unit) {
    Column(Modifier.fillMaxSize().background(Background).padding(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("FieldHub", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Wähle deinen Workspace", color = Color.LightGray)
        Spacer(Modifier.height(28.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(workspaces, key = { it.id }) { workspace ->
                Card(
                    onClick = { onSelect(workspace) },
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Business, contentDescription = null, tint = Accent)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(workspace.name, fontWeight = FontWeight.SemiBold)
                            Text(workspace.role, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Workspace öffnen")
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(workspace: Workspace) {
    var actionOpened by remember(workspace.id) { mutableStateOf(false) }
    Text(workspace.role.uppercase(), color = Accent, style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(8.dp))
    Text(workspace.headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Deine persönliche Übersicht", color = Color.LightGray)
    Spacer(Modifier.height(28.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Accent)
            Spacer(Modifier.height(14.dp))
            Text("Für dich wichtig", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Dein nächster Schritt", color = Color.LightGray)
            Spacer(Modifier.height(18.dp))
            Button(onClick = { actionOpened = true }, modifier = Modifier.fillMaxWidth()) { Text(workspace.action) }
        }
    }
    Spacer(Modifier.height(20.dp))
    Text("Demo-Modus · Noch keine Serververbindung", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
    if (actionOpened) {
        AlertDialog(
            onDismissRequest = { actionOpened = false },
            title = { Text(workspace.action) },
            text = { Text("Dieser Ablauf wird im nächsten Entwicklungsschritt implementiert.") },
            confirmButton = { TextButton(onClick = { actionOpened = false }) { Text("Verstanden") } }
        )
    }
}

@Composable
private fun PlaceholderScreen(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(20.dp)) {
        Text(description, modifier = Modifier.fillMaxWidth().padding(24.dp), color = Color.LightGray)
    }
}
