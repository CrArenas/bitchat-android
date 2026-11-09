package com.bitchat.android.sard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/** Los ejemplos de `contracts/ejemplos/` y la tabla de prioridad del contrato. */
class Sard1Test {
    private val contratos = File("../../../contracts")
    private fun ejemplo(n: String) = contratos.resolve("ejemplos").listFiles()!!.first { it.name.startsWith(n) }.readText().trim()
    private val palabras = Prioridad.leerLista(contratos.resolve("palabras-criticas.txt").readText())

    @Test
    fun `lee y vuelve a escribir los ejemplos validos sin cambios`() {
        for (n in listOf("01", "02", "03", "04", "07")) {
            val m = ejemplo(n)
            assertEquals(m, Sard1.leer(m)!!.mensaje())
        }
    }

    @Test
    fun `rechaza el truncado, los campos invalidos y el acuse`() {
        assertNull(Sard1.leer(ejemplo("06")))
        assertNull(Sard1.leer(ejemplo("09")))
        assertNull(Sard1.leer(ejemplo("08")))
    }

    @Test
    fun `el texto puede tener barras`() {
        assertEquals("a|b", Sard1.leer("SARD1|ab12-0001|P4|OTRO|0|000||1762481000|a|b")!!.texto)
    }

    @Test
    fun `lee el acuse`() {
        assertEquals("a3f9-0042", Sard1.leerSack(ejemplo("08")))
        assertNull(Sard1.leerSack("SACK|"))
        assertNull(Sard1.leerSack(ejemplo("01")))
    }

    @Test
    fun `la prioridad de las reglas coincide con la de los ejemplos`() {
        for (n in listOf("01", "02", "03", "04", "07")) {
            val r = Sard1.leer(ejemplo(n))!!
            val calculada = Prioridad.calcular(r.atrapados, r.heridos, r.vulnerables, r.personas, r.texto, palabras)
            assertEquals("ejemplo $n", r.prioridad, calculada)
        }
    }

    @Test
    fun `tabla de prioridad`() {
        fun p(a: Boolean = false, h: Boolean = false, v: Boolean = false, n: Int = 0, t: String = "") =
            Prioridad.calcular(a, h, v, n, t, palabras)
        assertEquals("P1", p(a = true))
        assertEquals("P1", p(t = "mi papá está ATRAPADO"))
        assertEquals("P1", p(t = "huele a gas"))
        assertEquals("P2", p(h = true, n = 1))
        assertEquals("P2", p(v = true))
        assertEquals("P3", p(n = 2))
        assertEquals("P4", p(t = "árbol caído en la vía"))
    }
}
