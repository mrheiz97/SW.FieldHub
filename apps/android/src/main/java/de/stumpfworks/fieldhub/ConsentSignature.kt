package de.stumpfworks.fieldhub

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

// Device-local normalized stroke coordinates, not a cryptographic digital signature.
private fun decodeStrokes(raw: String): List<List<Offset>> =
    raw.split("|").filter { it.isNotBlank() }.mapNotNull { stroke ->
        val points = stroke.split(";").mapNotNull { pair ->
            val coords = pair.split(",")
            val x = coords.getOrNull(0)?.toFloatOrNull()
            val y = coords.getOrNull(1)?.toFloatOrNull()
            if (x != null && y != null && x.isFinite() && y.isFinite()) Offset(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f)) else null
        }
        points.takeIf { it.isNotEmpty() }
    }

private fun encodeStrokes(strokes: List<List<Offset>>): String =
    strokes.filter { it.isNotEmpty() }.joinToString("|") { stroke ->
        stroke.joinToString(";") { point -> "${point.x},${point.y}" }
    }

@Composable
internal fun ConsentSignaturePad(value: String, onChange: (String) -> Unit) {
    val strokes = remember(value) { decodeStrokes(value) }
    var activeStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Unterschrift der kontaktierten Person", style = MaterialTheme.typography.labelMedium)
        Text("Bitte direkt mit Finger oder Stift im Feld unterschreiben.", style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
        Canvas(
            Modifier.fillMaxWidth().height(150.dp)
                .background(Color(0xFF111722), RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .pointerInput(value) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            activeStroke = listOf(Offset((offset.x / size.width).coerceIn(0f, 1f), (offset.y / size.height).coerceIn(0f, 1f)))
                        },
                        onDragEnd = {
                            if (activeStroke.isNotEmpty()) onChange(encodeStrokes(strokes + listOf(activeStroke)))
                            activeStroke = emptyList()
                        },
                        onDragCancel = { activeStroke = emptyList() },
                        onDrag = { change, _ ->
                            change.consume()
                            activeStroke = activeStroke + Offset(
                                (change.position.x / size.width).coerceIn(0f, 1f),
                                (change.position.y / size.height).coerceIn(0f, 1f)
                            )
                        }
                    )
                }
        ) {
            (strokes + listOf(activeStroke)).forEach { stroke ->
                val points = stroke.map { Offset(it.x * size.width, it.y * size.height) }
                if (points.size == 1) drawCircle(Color.White, radius = 2.dp.toPx(), center = points[0])
                points.zipWithNext().forEach { (a, b) ->
                    drawLine(Color.White, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChange(""); activeStroke = emptyList() }, enabled = value.isNotBlank()) {
                Text("Unterschrift löschen")
            }
            if (value.isNotBlank()) Text("Unterschrift erfasst", color = Color(0xFFB5DCC4), style = MaterialTheme.typography.bodySmall)
        }
    }
}
