package com.bitchat.android.sard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import com.bitchat.android.geohash.Geohash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.random.Random

/**
 * Une el formulario, el almacén, la malla y la pasarela. Los reportes viajan por la malla como
 * mensajes públicos de bitchat; la app los reconoce por la cabecera `SARD1|` o `SACK|`.
 */
object Sard {
    private const val TAG = "SARD"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val subida = Mutex()
    private val pasarela = Pasarela()

    private lateinit var app: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var almacen: Almacen
    private var enviar: (String) -> Unit = {}
    var palabrasCriticas: List<String> = emptyList()
        private set

    val reportes: StateFlow<List<Guardado>> get() = almacen.reportes

    private val _pasarelaActiva = MutableStateFlow(false)
    val pasarelaActiva: StateFlow<Boolean> = _pasarelaActiva
    private val _urlA = MutableStateFlow("")
    val urlA: StateFlow<String> = _urlA
    private val _urlB = MutableStateFlow("")
    val urlB: StateFlow<String> = _urlB
    private val _centralUsada = MutableStateFlow<String?>(null) // "A" o "B": a cuál subió la última vez
    val centralUsada: StateFlow<String?> = _centralUsada
    private val _ultimoError = MutableStateFlow<String?>(null)
    val ultimoError: StateFlow<String?> = _ultimoError

    /** Se llama una vez al crear el ChatViewModel. [enviar] emite un mensaje público por la malla. */
    @Synchronized
    fun iniciar(context: Context, enviar: (String) -> Unit) {
        this.enviar = enviar
        if (::app.isInitialized) return
        app = context.applicationContext
        prefs = app.getSharedPreferences("sard", Context.MODE_PRIVATE)
        almacen = Almacen(File(app.filesDir, "sard/reportes.json"))
        palabrasCriticas = Prioridad.leerLista(app.assets.open("palabras-criticas.txt").bufferedReader().readText())
        _pasarelaActiva.value = prefs.getBoolean("pasarela", false)
        _urlA.value = prefs.getString("url_a", "") ?: ""
        _urlB.value = prefs.getString("url_b", "") ?: ""
        scope.launch {
            while (true) {
                runCatching { revisarCola() }.onFailure { Log.w(TAG, "Fallo en la cola", it) }
                delay(1000)
            }
        }
    }

    private fun ahora() = System.currentTimeMillis()

    private fun siguienteEnvio(intentos: Int) = ahora() + Cola.esperaMs(intentos, Random.nextDouble())

    /**
     * Cada segundo: marca "no entregado" lo vencido y reenvía a la malla, en orden de prioridad,
     * los pendientes cuyo próximo intento ya llegó. La pasarela, además, reintenta la subida.
     */
    private fun revisarCola() {
        val t = ahora()
        almacen.actualizarTodos { g ->
            val r = Sard1.leer(g.mensaje)
            if (g.estado == Estado.PENDIENTE && r != null && Cola.vencido(g, r, t)) g.copy(estado = Estado.NO_ENTREGADO) else g
        }
        val debidos = Cola.debidos(almacen.reportes.value, t)
        for ((g, _) in debidos) {
            enviar(g.mensaje)
            almacen.actualizar(g.id) { it.copy(intentos = it.intentos + 1, proximoMs = siguienteEnvio(it.intentos + 1)) }
        }
        if (debidos.isNotEmpty() && _pasarelaActiva.value) subirPendientes()
    }

    private var vecinos: Set<String> = emptySet()

    /**
     * Lo llama bitchat cuando cambia la lista de vecinos. Si aparece uno nuevo, los reportes
     * vencidos empiezan una ronda nueva: se envían ya y la espera vuelve a 2 s (regla 5).
     */
    fun alCambiarVecinos(peers: List<String>) {
        if (!::almacen.isInitialized) return
        val nuevos = peers.toSet() - vecinos
        vecinos = peers.toSet()
        if (nuevos.isEmpty()) return
        val t = ahora()
        almacen.actualizarTodos { g ->
            if (g.estado == Estado.NO_ENTREGADO && !g.rechazado)
                g.copy(estado = Estado.PENDIENTE, intentos = 0, proximoMs = t, inicioRondaMs = t)
            else g
        }
    }

    fun configurarPasarela(activa: Boolean, urlA: String, urlB: String) {
        prefs.edit().putBoolean("pasarela", activa)
            .putString("url_a", urlA.trim()).putString("url_b", urlB.trim()).apply()
        _pasarelaActiva.value = activa
        _urlA.value = urlA.trim()
        _urlB.value = urlB.trim()
        if (activa) subirPendientes()
    }

    fun prioridad(atrapados: Boolean, heridos: Boolean, vulnerables: Boolean, personas: Int, texto: String) =
        Prioridad.calcular(atrapados, heridos, vulnerables, personas, texto, palabrasCriticas)

    /** Crea un reporte desde el formulario, lo guarda y lo emite por la malla. */
    fun crear(tipo: String, personas: Int, atrapados: Boolean, heridos: Boolean, vulnerables: Boolean, texto: String): Sard1.Reporte {
        val limpio = texto.replace('\n', ' ').trim()
        val r = Sard1.Reporte(
            id = nuevoId(), prioridad = prioridad(atrapados, heridos, vulnerables, personas, limpio),
            tipo = tipo, personas = personas, atrapados = atrapados, heridos = heridos,
            vulnerables = vulnerables, geohash = geohash(), horaUnix = System.currentTimeMillis() / 1000,
            texto = limpio,
        )
        almacen.agregar(Guardado(r.id, r.mensaje(), propio = true, Estado.PENDIENTE, ahora(), intentos = 1, proximoMs = siguienteEnvio(1)))
        enviar(r.mensaje())
        if (_pasarelaActiva.value) subirPendientes()
        return r
    }

    /**
     * Lo llama bitchat por cada mensaje público que llega de la malla. Devuelve true si era un
     * mensaje SARD (`SARD1` o `SACK`), que no se muestra en el chat.
     */
    fun alRecibir(contenido: String): Boolean {
        if (!::almacen.isInitialized) return false
        Sard1.leerSack(contenido)?.let { id ->
            almacen.actualizar(id) { it.copy(estado = Estado.ENTREGADO) }
            return true
        }
        val r = Sard1.leer(contenido) ?: return contenido.startsWith("SARD1|")
        val nuevo = almacen.agregar(
            Guardado(r.id, contenido, propio = false, Estado.PENDIENTE, ahora(), intentos = 1, proximoMs = siguienteEnvio(1))
        )
        if (!nuevo && almacen.reportes.value.any { it.id == r.id && it.estado == Estado.ENTREGADO }) {
            // Quien lo reenvía no supo del SACK: se le repite.
            enviar(Sard1.sack(r.id))
        } else if (nuevo && _pasarelaActiva.value) {
            subirPendientes()
        }
        return true
    }

    /** Pasarela: sube todo lo pendiente a la central y devuelve los SACK a la malla. */
    fun subirPendientes() {
        scope.launch {
            subida.withLock {
                val a = _urlA.value
                val b = _urlB.value
                val pendientes = almacen.reportes.value.filter { it.estado != Estado.ENTREGADO && !it.rechazado }
                if (a.isBlank() && b.isBlank()) {
                    _ultimoError.value = "Falta la URL de la central: escríbela y toca Guardar."
                    return@withLock
                }
                if (pendientes.isEmpty()) return@withLock
                val (nombre, resp, fallos) = pasarela.subirConFailover(a, b, pendientes.map { it.mensaje }, dispositivo())
                if (resp == null) {
                    _ultimoError.value = "Ninguna central recibió los reportes (${fallos.joinToString("; ")}). Se reintenta."
                    return@withLock
                }
                for (id in resp.sack) {
                    almacen.actualizar(id) { it.copy(estado = Estado.ENTREGADO) }
                    enviar(Sard1.sack(id))
                }
                for (m in resp.rechazados) {
                    pendientes.find { it.mensaje == m }?.let { g -> almacen.actualizar(g.id) { it.copy(rechazado = true) } }
                }
                _centralUsada.value = nombre
                _ultimoError.value = null
            }
        }
    }

    /** `<disp>-<contador>`: 4 caracteres hex fijos por celular y un contador local. */
    @Synchronized
    private fun nuevoId(): String {
        val n = prefs.getInt("contador", 0) + 1
        prefs.edit().putInt("contador", n).apply()
        return "${dispositivo()}-${n.toString().padStart(4, '0')}"
    }

    private fun dispositivo(): String = prefs.getString("disp", null) ?: "%04x".format(Random.nextInt(0x10000)).also {
        prefs.edit().putString("disp", it).apply()
    }

    /** Geohash de 6 caracteres de la última ubicación conocida, o vacío si no hay. */
    @SuppressLint("MissingPermission")
    private fun geohash(): String {
        val permiso = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permiso) return ""
        val lm = app.getSystemService(LocationManager::class.java) ?: return ""
        val ultima = lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time } ?: return ""
        return Geohash.encode(ultima.latitude, ultima.longitude, 6)
    }
}
