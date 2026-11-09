package com.bitchat.android.sard

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** Estado de entrega de un reporte en este celular (ficha de comunicación). */
enum class Estado { PENDIENTE, ENTREGADO, NO_ENTREGADO }

data class Guardado(
    val id: String,
    val mensaje: String,
    val propio: Boolean, // true si lo creó este celular (reportante), false si llegó por la malla
    val estado: Estado,
    val recibidoMs: Long,
    val rechazado: Boolean = false, // la central lo rechazó por mal formado (regla 6): no se resube
)

/**
 * Reportes guardados en un archivo JSON, que sobrevive a un reinicio. Nunca borra: solo agrega
 * reportes y cambia su estado.
 */
class Almacen(private val archivo: File) {
    private val gson = Gson()
    private val _reportes = MutableStateFlow(cargar())
    val reportes: StateFlow<List<Guardado>> = _reportes

    /** Agrega el reporte si su id es nuevo. Devuelve true si lo agregó. */
    @Synchronized
    fun agregar(g: Guardado): Boolean {
        if (_reportes.value.any { it.id == g.id }) return false
        guardar(_reportes.value + g)
        return true
    }

    /** Aplica [cambio] al reporte [id]. Devuelve true si cambió algo. */
    @Synchronized
    fun actualizar(id: String, cambio: (Guardado) -> Guardado): Boolean {
        val actual = _reportes.value
        val nuevo = actual.map { if (it.id == id) cambio(it) else it }
        if (nuevo == actual) return false
        guardar(nuevo)
        return true
    }

    private fun guardar(lista: List<Guardado>) {
        archivo.parentFile?.mkdirs()
        val tmp = File(archivo.path + ".tmp")
        tmp.writeText(gson.toJson(lista))
        if (!tmp.renameTo(archivo)) {
            archivo.delete()
            tmp.renameTo(archivo)
        }
        _reportes.value = lista
    }

    private fun cargar(): List<Guardado> {
        if (!archivo.exists()) return emptyList()
        val tipo = object : TypeToken<List<Guardado>>() {}.type
        return gson.fromJson(archivo.readText(), tipo) ?: emptyList()
    }
}
