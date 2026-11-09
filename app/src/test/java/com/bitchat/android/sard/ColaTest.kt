package com.bitchat.android.sard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColaTest {
    private fun g(id: String, prioridad: String, hora: Long, estado: Estado = Estado.PENDIENTE, proximoMs: Long = 0, inicioRondaMs: Long = 0) =
        Guardado(id, "SARD1|$id|$prioridad|OTRO|1|000||$hora|", propio = false, estado, 0, proximoMs = proximoMs, inicioRondaMs = inicioRondaMs)

    @Test
    fun `espera creciente con tope de 60 s`() {
        assertEquals(listOf(2000L, 4000, 8000, 16000, 32000, 60000, 60000), (1..7).map { Cola.esperaMs(it, 0.5) })
    }

    @Test
    fun `azar de mas o menos 20 por ciento`() {
        assertEquals(1600L, Cola.esperaMs(1, 0.0))
        assertEquals(2400L, Cola.esperaMs(1, 1.0))
    }

    @Test
    fun `unos 14 intentos antes de vencer`() {
        var t = 0L
        var intentos = 1
        while (t + Cola.esperaMs(intentos, 0.5) < Cola.VENCE_MS) { t += Cola.esperaMs(intentos, 0.5); intentos++ }
        assertEquals(14, intentos)
    }

    @Test
    fun `vence a los 600 s de la hora_unix, o de la reactivacion`() {
        val r = g("a-1", "P1", 1000)
        val rep = Sard1.leer(r.mensaje)!!
        assertFalse(Cola.vencido(r, rep, 1000_000 + 599_999))
        assertTrue(Cola.vencido(r, rep, 1000_000 + 600_000))
        val reactivado = r.copy(inicioRondaMs = 5_000_000)
        assertFalse(Cola.vencido(reactivado, rep, 5_000_000 + 599_999))
    }

    @Test
    fun `debidos por prioridad y hora, sin descartar P3 y P4`() {
        val lista = listOf(
            g("a-1", "P4", 100), g("a-2", "P1", 300), g("a-3", "P1", 200), g("a-4", "P3", 50),
            g("a-5", "P2", 10, proximoMs = 9_999), // todavía no le toca
            g("a-6", "P1", 1, estado = Estado.ENTREGADO),
            g("a-7", "P1", 1, estado = Estado.NO_ENTREGADO),
        )
        assertEquals(listOf("a-3", "a-2", "a-4", "a-1"), Cola.debidos(lista, 5_000).map { it.first.id })
    }
}
