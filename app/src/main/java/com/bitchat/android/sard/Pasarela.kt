package com.bitchat.android.sard

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Cliente HTTP de la central: `POST /reportes` (`contracts/api-central.md`). */
class Pasarela(private val http: OkHttpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()) {
    private val gson = Gson()

    data class Respuesta(val sack: List<String>, val rechazados: List<String>)

    private val httpSalud = http.newBuilder().callTimeout(3, TimeUnit.SECONDS).build()

    /** true si la central responde `GET /salud` con `estado: ok` en menos de 3 s. */
    fun salud(urlCentral: String): Boolean = runCatching {
        val req = Request.Builder().url(urlCentral.trimEnd('/') + "/salud").get().build()
        httpSalud.newCall(req).execute().use { r ->
            r.isSuccessful && gson.fromJson(r.body.string(), JsonObject::class.java)?.get("estado")?.asString == "ok"
        }
    }.getOrDefault(false)

    /** Sube los mensajes y devuelve los ids confirmados y los mensajes rechazados. */
    @Throws(IOException::class)
    fun subir(urlCentral: String, mensajes: List<String>, pasarelaId: String): Respuesta {
        val cuerpo = gson.toJson(mapOf("mensajes" to mensajes, "pasarela_id" to pasarelaId))
        val req = Request.Builder()
            .url(urlCentral.trimEnd('/') + "/reportes")
            .post(cuerpo.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("central respondió ${r.code}")
            val json = gson.fromJson(r.body.string(), JsonObject::class.java)
            val sack = json.getAsJsonArray("sack")?.mapNotNull { Sard1.leerSack(it.asString) } ?: emptyList()
            val rechazados = json.getAsJsonArray("rechazados")
                ?.mapNotNull { it.asJsonObject.get("mensaje")?.asString } ?: emptyList()
            return Respuesta(sack, rechazados)
        }
    }

    /** A qué central se subió ("A" o "B"), su respuesta, y los fallos de las que no recibieron. */
    data class Resultado(val central: String?, val respuesta: Respuesta?, val fallos: List<String>)

    /**
     * Failover (contrato, "Sincronización"): consulta `GET /salud` de la A y, si no responde, sube
     * a la B. Si la A responde pero falla al recibir, también prueba la B.
     */
    fun subirConFailover(urlA: String, urlB: String, mensajes: List<String>, pasarelaId: String): Resultado {
        val fallos = mutableListOf<String>()
        val destinos = mutableListOf<Pair<String, String>>()
        if (urlA.isNotBlank()) {
            if (salud(urlA)) destinos += "A" to urlA else fallos += "A: no responde a /salud"
        }
        if (urlB.isNotBlank()) destinos += "B" to urlB
        for ((nombre, url) in destinos) {
            try {
                return Resultado(nombre, subir(url, mensajes, pasarelaId), fallos)
            } catch (e: Exception) { // red, HTTP o respuesta ilegible: se prueba la siguiente
                fallos += "$nombre: ${e.message}"
            }
        }
        return Resultado(null, null, fallos)
    }
}
