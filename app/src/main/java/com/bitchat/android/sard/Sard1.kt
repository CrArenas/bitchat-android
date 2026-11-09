package com.bitchat.android.sard

/**
 * Mensajes del protocolo SARD1 (`contracts/protocolo-sard1.md` del repo sard-expoasi).
 *
 * `SARD1|<id>|<prioridad>|<tipo>|<personas>|<banderas>|<geohash>|<hora_unix>|<texto>`
 * `SACK|<id>`
 */
object Sard1 {
    val TIPOS = listOf("ATRAP", "MEDIC", "FUEGO", "VIA", "DESAP", "OTRO")
    private val PRIORIDADES = setOf("P1", "P2", "P3", "P4")
    private val RE_ID = Regex("^[0-9A-Za-z]+-[0-9]+$")
    private val RE_BANDERAS = Regex("^[01]{3}$")
    private val RE_ENTERO = Regex("^[0-9]+$")
    private const val N_CAMPOS = 9

    data class Reporte(
        val id: String,
        val prioridad: String,
        val tipo: String,
        val personas: Int,
        val atrapados: Boolean,
        val heridos: Boolean,
        val vulnerables: Boolean, // niños o adultos mayores
        val geohash: String, // vacío si no hay GPS
        val horaUnix: Long,
        val texto: String,
    ) {
        fun mensaje(): String {
            val banderas = listOf(atrapados, heridos, vulnerables).joinToString("") { if (it) "1" else "0" }
            return "SARD1|$id|$prioridad|$tipo|$personas|$banderas|$geohash|$horaUnix|$texto"
        }
    }

    /** El reporte si el mensaje es un SARD1 válido; null en cualquier otro caso. */
    fun leer(mensaje: String): Reporte? {
        if (!mensaje.startsWith("SARD1|")) return null
        // Todo lo que sigue al separador del campo 7 es texto, aunque tenga '|'.
        val p = mensaje.split("|", limit = N_CAMPOS)
        if (p.size < N_CAMPOS) return null
        val id = p[1]; val prioridad = p[2]; val tipo = p[3]; val personas = p[4]
        val banderas = p[5]; val geohash = p[6]; val hora = p[7]
        if (!RE_ID.matches(id) || prioridad !in PRIORIDADES || tipo !in TIPOS) return null
        if (!RE_ENTERO.matches(personas) || !RE_BANDERAS.matches(banderas) || !RE_ENTERO.matches(hora)) return null
        return Reporte(
            id = id, prioridad = prioridad, tipo = tipo, personas = personas.toInt(),
            atrapados = banderas[0] == '1', heridos = banderas[1] == '1', vulnerables = banderas[2] == '1',
            geohash = geohash, horaUnix = hora.toLong(), texto = p[8],
        )
    }

    fun sack(id: String) = "SACK|$id"

    /** true si es un mensaje del protocolo SARD (reporte o acuse), válido o no. */
    fun esMensajeSard(mensaje: String) = mensaje.startsWith("SARD1|") || mensaje.startsWith("SACK|")

    /** El id confirmado si el mensaje es un `SACK|<id>`; null en otro caso. */
    fun leerSack(mensaje: String): String? =
        mensaje.removePrefix("SACK|").takeIf { mensaje.startsWith("SACK|") && RE_ID.matches(it) }
}

