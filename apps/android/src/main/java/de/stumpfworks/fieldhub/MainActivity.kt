package de.stumpfworks.fieldhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val Purple = Color(0xFF7657D8)
private val Background = Color(0xFF10131C)
private val Surface = Color(0xFF1C2230)

data class Workspace(val id: String, val name: String, val role: String, val headline: String, val action: String)

private val demoWorkspaces = listOf(
    Workspace("ravens", "Ravens Fürstenwalde", "Spieler", "Bereit fürs Training?", "Training ansehen"),
    Workspace("stumpfworks", "stumpfWORKS", "Administrator", "Was steht heute an?", "Aufgaben öffnen"),
    Workspace("sales", "Field Sales Demo", "Recruiter", "Neue Kontakte erfassen", "Kontakt hinzufügen")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FieldHubApp() }
    }
}

@Composable
private fun FieldHubApp() {
    var selected by remember { mutableStateOf<Workspace?>(null) }
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFBDA9FF),
            background = Background,
            surface = Surface,
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(containerColor = Background) { insets ->
            Column(Modifier.fillMaxSize().padding(insets).padding(20.dp)) {
                if (selected == null) {
                    Spacer(Modifier.height(24.dp))
                    Text("FieldHub", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Wähle deinen Workspace", color = Color.LightGray)
                    Spacer(Modifier.height(28.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(demoWorkspaces, key = { it.id }) { workspace ->
                            Card(
                                onClick = { selected = workspace },
                                colors = CardDefaults.cardColors(containerColor = Surface),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFFBDA9FF))
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
                } else {
                    val workspace = selected!!
                    TextButton(onClick = { selected = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Workspaces")
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(workspace.name, color = Color(0xFFBDA9FF))
                    Spacer(Modifier.height(8.dp))
                    Text(workspace.headline, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Deine persönliche Übersicht · ${workspace.role}", color = Color.LightGray)
                    Spacer(Modifier.height(28.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFFBDA9FF))
                            Spacer(Modifier.height(14.dp))
                            Text("Für dich wichtig", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            Text("Dein nächster Schritt", color = Color.LightGray)
                            Spacer(Modifier.height(18.dp))
                            Button(onClick = { /* Navigation folgt im nächsten Meilenstein */ }, modifier = Modifier.fillMaxWidth()) {
                                Text(workspace.action)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.People, contentDescription = null, tint = Purple)
                        Spacer(Modifier.width(8.dp))
                        Text("Demo-Modus · Keine Serververbindung", color = Color.LightGray)
                    }
                }
            }
        }
    }
}
