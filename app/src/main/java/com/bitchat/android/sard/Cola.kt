package com.bitchat.android.sard

/**
 * Reglas de la cola de reenvío (contrato, reglas 1, 2, 4 y 5). Sin Android, para probarlas.
 */
object Cola {
    /** Un reporte vence a los 600 s del inicio de su ronda de reintentos. */
    const val VENCE_MS = 600_000L
    private val ESPERAS_S = longArrayOf(2, 4, 8, 16, 32, 60)

    /**
     * Espera antes del siguiente intento, después de [intentos] envíos: 2, 4, 8, 16, 32 s y luego
     * 60 s fijo, con ±20 % según [azar] (entre 0 y 1).
     */
    fun esperaMs(intentos: Int, azar: Double): Long {
        val base = ESPERAS_S[(intentos - 1).coerceIn(0, ESPERAS_S.lastIndex)] * 1000
        return (base * (0.8 + 0.4 * azar)).toLong()
    }

    /** La primera ronda empieza en la `hora_unix` del reporte; una reactivada, al reactivarse. */
    fun inicioRonda(g: Guardado, r: Sard1.Reporte): Long =
        if (g.inicioRondaMs > 0) g.inicioRondaMs else r.horaUnix * 1000

    fun vencido(g: Guardado, r: Sard1.Reporte, ahoraMs: Long): Boolean =
        ahoraMs - inicioRonda(g, r) >= VENCE_MS

    /**
     * Los pendientes cuyo próximo intento ya llegó, en orden de envío: por prioridad (P1 primero)
     * y, dentro de cada una, por `hora_unix` más antigua. Ninguno se descarta.
     */
    fun debidos(lista: List<Guardado>, ahoraMs: Long): List<Pair<Guardado, Sard1.Reporte>> =
        lista.asSequence()
            .filter { it.estado == Estado.PENDIENTE && !it.rechazado && it.proximoMs <= ahoraMs }
            .mapNotNull { g -> Sard1.leer(g.mensaje)?.let { g to it } }
            .sortedWith(compareBy({ it.second.prioridad }, { it.second.horaUnix }))
            .toList()
}
