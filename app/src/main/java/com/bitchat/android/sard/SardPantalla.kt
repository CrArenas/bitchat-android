package com.bitchat.android.sard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val ROJO = Color(0xFFC62828)

private val NOMBRE_TIPO = mapOf(
    "ATRAP" to "Atrapados", "MEDIC" to "Médica", "FUEGO" to "Incendio",
    "VIA" to "Vía bloqueada", "DESAP" to "Desaparecido", "OTRO" to "Otro",
)

/** Botón SOS en la pantalla del chat; abre la hoja de SARD. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SardBoton(modifier: Modifier = Modifier) {
    var abierta by remember { mutableStateOf(false) }
    Button(
        onClick = { abierta = true },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = ROJO, contentColor = Color.White),
    ) { Text("SOS", fontWeight = FontWeight.Bold) }

    if (abierta) {
        ModalBottomSheet(
            onDismissRequest = { abierta = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) { SardHoja() }
    }
}

@Composable
private fun SardHoja() {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Formulario()
        HorizontalDivider()
        MisReportes()
        HorizontalDivider()
        AjustePasarela()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Formulario() {
    var tipo by remember { mutableStateOf("OTRO") }
    var personas by remember { mutableIntStateOf(1) }
    var atrapados by remember { mutableStateOf(false) }
    var heridos by remember { mutableStateOf(false) }
    var vulnerables by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf("") }
    var enviado by remember { mutableStateOf<String?>(null) }

    Text("Reportar emergencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

    Text("Tipo")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sard1.TIPOS.forEach { t ->
            FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(NOMBRE_TIPO[t] ?: t) })
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Personas afectadas")
        OutlinedButton(onClick = { if (personas > 0) personas-- }) { Text("−") }
        Text("$personas", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { personas++ }) { Text("+") }
    }

    Casilla("Hay personas atrapadas", atrapados) { atrapados = it }
    Casilla("Hay heridos", heridos) { heridos = it }
    Casilla("Hay niños o adultos mayores", vulnerables) { vulnerables = it }

    OutlinedTextField(
        value = texto,
        onValueChange = { texto = it },
        label = { Text("Qué pasa (opcional)") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    )

    val prioridad = Sard.prioridad(atrapados, heridos, vulnerables, personas, texto)
    val palabra = Prioridad.palabraCritica(texto, Sard.palabrasCriticas)
    Text(
        "Prioridad: $prioridad" + (palabra?.let { " (palabra crítica «$it»)" } ?: ""),
        fontWeight = FontWeight.Bold,
        color = if (prioridad == "P1" || prioridad == "P2") ROJO else MaterialTheme.colorScheme.onSurface,
    )

    Button(
        onClick = {
            val r = Sard.crear(tipo, personas, atrapados, heridos, vulnerables, texto)
            enviado = "Enviado ${r.id} (${r.prioridad}). Se reenvía por la malla hasta llegar a la central."
            texto = ""; atrapados = false; heridos = false; vulnerables = false; personas = 1; tipo = "OTRO"
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = ROJO, contentColor = Color.White),
    ) { Text("Enviar reporte") }

    enviado?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun Casilla(texto: String, marcada: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = marcada, onCheckedChange = alCambiar)
        Text(texto)
    }
}

@Composable
private fun MisReportes() {
    val reportes by Sard.reportes.collectAsStateWithLifecycle()
    val mios = reportes.filter { it.propio }.sortedByDescending { it.recibidoMs }
    Text("Mis reportes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    if (mios.isEmpty()) Text("Todavía no has enviado reportes.", style = MaterialTheme.typography.bodySmall)
    mios.forEach { g ->
        val r = Sard1.leer(g.mensaje)
        val estado = when (g.estado) {
            Estado.ENTREGADO -> "✓ entregado"
            Estado.PENDIENTE -> "pendiente · intento ${g.intentos}"
            Estado.NO_ENTREGADO -> "no entregado"
        }
        Text("${g.id} · ${r?.prioridad} · ${NOMBRE_TIPO[r?.tipo] ?: r?.tipo} · $estado")
    }
}

@Composable
private fun AjustePasarela() {
    val activa by Sard.pasarelaActiva.collectAsStateWithLifecycle()
    val urlGuardadaA by Sard.urlA.collectAsStateWithLifecycle()
    val urlGuardadaB by Sard.urlB.collectAsStateWithLifecycle()
    val centralUsada by Sard.centralUsada.collectAsStateWithLifecycle()
    val error by Sard.ultimoError.collectAsStateWithLifecycle()
    val reportes by Sard.reportes.collectAsStateWithLifecycle()
    var urlA by remember(urlGuardadaA) { mutableStateOf(urlGuardadaA) }
    var urlB by remember(urlGuardadaB) { mutableStateOf(urlGuardadaB) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Modo pasarela", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Solo en el celular con internet: sube los reportes a la central.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = activa, onCheckedChange = { Sard.configurarPasarela(it, urlA, urlB) })
    }
    CampoUrl("Central A (ej. http://192.168.1.10:8001)", urlA) { urlA = it }
    CampoUrl("Central B, respaldo (ej. http://192.168.1.10:8002)", urlB) { urlB = it }
    OutlinedButton(onClick = { Sard.configurarPasarela(activa, urlA, urlB) }) { Text("Guardar") }
    centralUsada?.let { Text("Última subida: central $it", style = MaterialTheme.typography.bodySmall) }

    val ajenos = reportes.count { !it.propio }
    val entregados = reportes.count { it.estado == Estado.ENTREGADO }
    Text("Reportes en este celular: ${reportes.size} ($ajenos de otros) · entregados: $entregados", style = MaterialTheme.typography.bodySmall)
    error?.let { Text(it, color = ROJO, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun CampoUrl(etiqueta: String, valor: String, alCambiar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
}
