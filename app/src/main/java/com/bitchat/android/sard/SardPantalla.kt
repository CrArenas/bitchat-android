package com.bitchat.android.sard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val NOMBRE_TIPO = mapOf(
    "ATRAP" to "Atrapados", "MEDIC" to "Médica", "FUEGO" to "Incendio",
    "VIA" to "Vía bloqueada", "DESAP" to "Desaparecido", "OTRO" to "Otro",
)

/** Botón SOS en la pantalla del chat; abre la hoja de SARD. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SardBoton(modifier: Modifier = Modifier) {
    var abierta by remember { mutableStateOf(false) }
    SardTema {
        val c = LocalSardColores.current
        Button(
            onClick = { abierta = true },
            modifier = modifier.heightIn(min = 44.dp).semantics { contentDescription = "SOS: reportar emergencia" },
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = c.p1.color, contentColor = Color.White),
        ) { Text("SOS", fontWeight = FontWeight.Bold) }

        if (abierta) {
            ModalBottomSheet(
                onDismissRequest = { abierta = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = c.fondo,
            ) { SardHoja() }
        }
    }
}

@Composable
private fun SardHoja() {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Tarjeta { Formulario() }
        Tarjeta { MisReportes() }
        Tarjeta { AjustePasarela() }
    }
}

/** Bloque con fondo de tarjeta y borde fino, como los paneles del tablero. */
@Composable
private fun Tarjeta(contenido: @Composable () -> Unit) {
    val c = LocalSardColores.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = c.tarjeta,
        border = BorderStroke(1.dp, c.borde),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { contenido() }
    }
}

@Composable
private fun Titulo(texto: String, detalle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(texto, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() })
        detalle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalSardColores.current.textoSuave) }
    }
}

@Composable
private fun Etiqueta(texto: String) {
    Text(texto, style = MaterialTheme.typography.labelLarge, color = LocalSardColores.current.textoSuave)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Formulario() {
    val c = LocalSardColores.current
    var tipo by remember { mutableStateOf("OTRO") }
    var personas by remember { mutableIntStateOf(1) }
    var atrapados by remember { mutableStateOf(false) }
    var heridos by remember { mutableStateOf(false) }
    var vulnerables by remember { mutableStateOf(false) }
    var texto by remember { mutableStateOf("") }
    var enviado by remember { mutableStateOf<String?>(null) }

    Text("Reportar emergencia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() })

    Etiqueta("Tipo")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Sard1.TIPOS.forEach { t ->
            FilterChip(
                selected = tipo == t,
                onClick = { tipo = t },
                label = { Text(NOMBRE_TIPO[t] ?: t) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.primario, selectedLabelColor = c.sobrePrimario,
                    labelColor = c.texto,
                ),
            )
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Personas afectadas", modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = { if (personas > 0) personas-- },
            modifier = Modifier.size(44.dp).semantics { contentDescription = "Una persona menos" },
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) { Text("−") }
        Text("$personas", style = MaterialTheme.typography.titleMedium, fontFamily = FiraCode)
        OutlinedButton(
            onClick = { personas++ },
            modifier = Modifier.size(44.dp).semantics { contentDescription = "Una persona más" },
            contentPadding = ButtonDefaults.TextButtonContentPadding,
        ) { Text("+") }
    }

    Column {
        Casilla("Hay personas atrapadas", atrapados) { atrapados = it }
        Casilla("Hay heridos", heridos) { heridos = it }
        Casilla("Hay niños o adultos mayores", vulnerables) { vulnerables = it }
    }

    OutlinedTextField(
        value = texto,
        onValueChange = { texto = it },
        label = { Text("Qué pasa (opcional)") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    )

    val prioridad = Sard.prioridad(atrapados, heridos, vulnerables, personas, texto)
    val palabra = Prioridad.palabraCritica(texto, Sard.palabrasCriticas)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Etiqueta("Prioridad")
        InsigniaPrioridad(prioridad, grande = true)
        palabra?.let {
            Text("palabra crítica «$it»", style = MaterialTheme.typography.bodySmall, color = c.textoSuave)
        }
    }

    Button(
        onClick = {
            val r = Sard.crear(tipo, personas, atrapados, heridos, vulnerables, texto)
            val m = r.mensaje()
            enviado = "Enviado ${r.id} (${r.prioridad}), ${m.toByteArray().size} bytes:\n$m\n" +
                "Queda pendiente y se reenvía por la malla hasta llegar a la central."
            texto = ""; atrapados = false; heridos = false; vulnerables = false; personas = 1; tipo = "OTRO"
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.small,
    ) { Text("Enviar reporte", fontWeight = FontWeight.SemiBold) }

    enviado?.let {
        Surface(color = c.suave, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
            Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FiraCode, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun Casilla(texto: String, marcada: Boolean, alCambiar: (Boolean) -> Unit) {
    val c = LocalSardColores.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
            .toggleable(value = marcada, role = Role.Checkbox, onValueChange = alCambiar),
    ) {
        Checkbox(
            checked = marcada, onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = c.primario, checkmarkColor = c.sobrePrimario),
        )
        Text(texto, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun EstadoInsignia(estado: Estado, intentos: Int) {
    val c = LocalSardColores.current
    when (estado) {
        Estado.ENTREGADO -> Insignia(c.ok) { Text("✓ entregado", style = MaterialTheme.typography.labelMedium) }
        Estado.PENDIENTE -> Insignia(Tono(c.texto, Color.Transparent)) {
            Text("pendiente · intento $intentos", style = MaterialTheme.typography.labelMedium)
        }
        Estado.NO_ENTREGADO -> Insignia(c.vencido) { Text("no entregado", style = MaterialTheme.typography.labelMedium) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MisReportes() {
    val c = LocalSardColores.current
    val reportes by Sard.reportes.collectAsStateWithLifecycle()
    val mios = reportes.filter { it.propio }.sortedByDescending { it.recibidoMs }
    Titulo("Mis reportes")
    if (mios.isEmpty()) Text("Todavía no has enviado reportes.", style = MaterialTheme.typography.bodyMedium, color = c.textoSuave)
    mios.forEachIndexed { i, g ->
        if (i > 0) HorizontalDivider(color = c.borde)
        val r = Sard1.leer(g.mensaje)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            InsigniaPrioridad(r?.prioridad)
            Text(NOMBRE_TIPO[r?.tipo] ?: r?.tipo ?: "", style = MaterialTheme.typography.bodyMedium)
            Text(g.id, style = MaterialTheme.typography.bodySmall, fontFamily = FiraCode, color = c.textoSuave)
            EstadoInsignia(g.estado, g.intentos)
        }
    }
}

@Composable
private fun AjustePasarela() {
    val c = LocalSardColores.current
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
            Titulo("Modo pasarela", "Solo en el celular con internet: sube los reportes a la central.")
        }
        Switch(
            checked = activa,
            onCheckedChange = { Sard.configurarPasarela(it, urlA, urlB) },
            colors = SwitchDefaults.colors(checkedTrackColor = c.primario, checkedThumbColor = c.sobrePrimario),
        )
    }
    CampoUrl("Central A (ej. http://192.168.1.10:8001)", urlA) { urlA = it }
    CampoUrl("Central B, respaldo (ej. http://192.168.1.10:8002)", urlB) { urlB = it }
    var guardado by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { Sard.configurarPasarela(activa, urlA, urlB); guardado = true },
            modifier = Modifier.heightIn(min = 44.dp),
            shape = MaterialTheme.shapes.small,
        ) { Text("Guardar") }
        if (guardado && urlA == urlGuardadaA && urlB == urlGuardadaB) {
            Text("Guardado ✓", style = MaterialTheme.typography.bodySmall, color = c.ok.color)
        }
    }
    centralUsada?.let { Text("Última subida: central $it", style = MaterialTheme.typography.bodySmall, color = c.textoSuave) }

    val ajenos = reportes.count { !it.propio }
    val entregados = reportes.count { it.estado == Estado.ENTREGADO }
    Text("Reportes en este celular: ${reportes.size} ($ajenos de otros) · entregados: $entregados",
        style = MaterialTheme.typography.bodySmall, color = c.textoSuave)
    error?.let {
        Insignia(c.p1, Modifier.fillMaxWidth()) {
            Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun CampoUrl(etiqueta: String, valor: String, alCambiar: (String) -> Unit) {
    OutlinedTextField(
        value = valor,
        onValueChange = alCambiar,
        label = { Text(etiqueta) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FiraCode),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
    )
}
