package com.bitchat.android.sard

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Failover de la pasarela entre la central A y la B, contra centrales falsas. */
@RunWith(RobolectricTestRunner::class)
class PasarelaTest {
    private lateinit var a: MockWebServer
    private lateinit var b: MockWebServer
    private val pasarela = Pasarela()
    private val mensajes = listOf("SARD1|ab12-0001|P1|ATRAP|2|100||1762481000|")

    private fun ok(cuerpo: String) = MockResponse.Builder().code(200).body(cuerpo).build()
    private val salud = ok("""{"estado":"ok","central":"A","jev":"ok"}""")
    private val sack = ok("""{"sack":["SACK|ab12-0001"],"rechazados":[]}""")
    private fun url(s: MockWebServer) = s.url("/").toString()

    @Before
    fun iniciar() {
        a = MockWebServer().apply { start() }
        b = MockWebServer().apply { start() }
    }

    @After
    fun cerrar() {
        a.close()
        b.close()
    }

    @Test
    fun `con la A sana sube a la A`() {
        a.enqueue(salud)
        a.enqueue(sack)
        val r = pasarela.subirConFailover(url(a), url(b), mensajes, "p")
        assertEquals("A", r.central)
        assertEquals(listOf("ab12-0001"), r.respuesta!!.sack)
        assertEquals("/salud", a.takeRequest().url.encodedPath)
        assertEquals("/reportes", a.takeRequest().url.encodedPath)
        assertEquals(0, b.requestCount)
    }

    @Test
    fun `con la A caida sube a la B`() {
        val urlA = url(a)
        a.close()
        b.enqueue(sack)
        val r = pasarela.subirConFailover(urlA, url(b), mensajes, "p")
        assertEquals("B", r.central)
        assertEquals(listOf("A: no responde a /salud"), r.fallos)
    }

    @Test
    fun `si la A responde a salud pero falla al recibir, prueba la B`() {
        a.enqueue(salud)
        a.enqueue(MockResponse.Builder().code(500).build())
        b.enqueue(sack)
        val r = pasarela.subirConFailover(url(a), url(b), mensajes, "p")
        assertEquals("B", r.central)
        assertEquals(listOf("A: central respondió 500"), r.fallos)
    }

    @Test
    fun `sin ninguna central conserva los reportes`() {
        val urlA = url(a)
        val urlB = url(b)
        a.close()
        b.close()
        val r = pasarela.subirConFailover(urlA, urlB, mensajes, "p")
        assertNull(r.central)
        assertNull(r.respuesta)
        assertEquals(2, r.fallos.size)
    }
}
