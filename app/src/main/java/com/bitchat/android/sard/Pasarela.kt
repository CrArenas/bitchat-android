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
}
