package com.bitchat.android.sard

import java.text.Normalizer

/**
 * Prioridad en el celular, solo con reglas (contrato, sección "Prioridad en el celular").
 * Gana la primera regla que se cumple.
 */
object Prioridad {
    fun calcular(
        atrapados: Boolean,
        heridos: Boolean,
        vulnerables: Boolean,
        personas: Int,
        texto: String,
        palabrasCriticas: List<String>,
    ): String = when {
        atrapados || palabraCritica(texto, palabrasCriticas) != null -> "P1"
        heridos || vulnerables -> "P2"
        personas >= 1 -> "P3"
        else -> "P4"
    }

    /** La primera palabra crítica que aparece en el texto, o null. */
    fun palabraCritica(texto: String, palabrasCriticas: List<String>): String? {
        val t = normalizar(texto)
        return palabrasCriticas.firstOrNull { t.contains(it) }
    }

    /** Minúsculas y sin tildes, como en `contracts/palabras-criticas.txt`. */
    fun normalizar(texto: String): String =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFKD).replace(Regex("\\p{M}"), "")

    /** Lee el formato de `palabras-criticas.txt`: una por línea, `#` es comentario. */
    fun leerLista(contenido: String): List<String> =
        contenido.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map(::normalizar)
}
