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
    private val _urlCentral = MutableStateFlow("")
    val urlCentral: StateFlow<String> = _urlCentral
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
        _urlCentral.value = prefs.getString("url_central", "") ?: ""
    }

    fun configurarPasarela(activa: Boolean, url: String) {
        prefs.edit().putBoolean("pasarela", activa).putString("url_central", url.trim()).apply()
        _pasarelaActiva.value = activa
        _urlCentral.value = url.trim()
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
        almacen.agregar(Guardado(r.id, r.mensaje(), propio = true, Estado.PENDIENTE, System.currentTimeMillis()))
        enviar(r.mensaje())
        if (_pasarelaActiva.value) subirPendientes()
        return r
    }

    /** Lo llama bitchat por cada mensaje público que llega de la malla. */
    fun alRecibir(contenido: String) {
        if (!::almacen.isInitialized) return
        Sard1.leerSack(contenido)?.let { id ->
            almacen.actualizar(id) { it.copy(estado = Estado.ENTREGADO) }
            return
        }
        val r = Sard1.leer(contenido) ?: return
        almacen.agregar(Guardado(r.id, contenido, propio = false, Estado.PENDIENTE, System.currentTimeMillis()))
        if (_pasarelaActiva.value) subirPendientes()
    }

    /** Pasarela: sube todo lo pendiente a la central y devuelve los SACK a la malla. */
    fun subirPendientes() {
        scope.launch {
            subida.withLock {
                val url = _urlCentral.value
                val pendientes = almacen.reportes.value.filter { it.estado != Estado.ENTREGADO && !it.rechazado }
                if (url.isBlank() || pendientes.isEmpty()) return@withLock
                try {
                    val resp = pasarela.subir(url, pendientes.map { it.mensaje }, pasarelaId = dispositivo())
                    for (id in resp.sack) {
                        almacen.actualizar(id) { it.copy(estado = Estado.ENTREGADO) }
                        enviar(Sard1.sack(id))
                    }
                    for (m in resp.rechazados) {
                        pendientes.find { it.mensaje == m }?.let { g -> almacen.actualizar(g.id) { it.copy(rechazado = true) } }
                    }
                    _ultimoError.value = null
                } catch (e: Exception) {
                    Log.w(TAG, "No se pudo subir a la central", e)
                    _ultimoError.value = "Sin conexión con la central: ${e.message}"
                }
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
