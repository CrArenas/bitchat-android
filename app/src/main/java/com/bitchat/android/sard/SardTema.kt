package com.bitchat.android.sard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R

/*
 * Tema de las pantallas SARD, el mismo del tablero (`tablero/app/globals.css` del repo
 * sard-expoasi, pasado de oklch a sRGB): grises neutros, la acción principal neutra y el color
 * fuerte solo para la prioridad y el estado. Fira Sans para el texto y Fira Code para ids y mensajes.
 * Sigue el modo claro u oscuro que tenga la app.
 */

@Immutable
data class Tono(val color: Color, val fondo: Color)

@Immutable
data class SardColores(
    val oscuro: Boolean,
    val fondo: Color,
    val tarjeta: Color,
    val texto: Color,
    val textoSuave: Color,
    val suave: Color,
    val borde: Color,
    val primario: Color,
    val sobrePrimario: Color,
    val p1: Tono,
    val p2: Tono,
    val p3: Tono,
    val p4: Tono,
    val ok: Tono,
    val vencido: Tono,
) {
    fun prioridad(p: String?): Tono = when (p) {
        "P1" -> p1
        "P2" -> p2
        "P3" -> p3
        else -> p4
    }
}

private val CLARO = SardColores(
    oscuro = false,
    fondo = Color(0xFFF6F7F8), tarjeta = Color(0xFFFFFFFF),
    texto = Color(0xFF13161A), textoSuave = Color(0xFF595E63),
    suave = Color(0xFFEAEBED), borde = Color(0xFFD5D8DA),
    primario = Color(0xFF1C2024), sobrePrimario = Color(0xFFF8F8F8),
    p1 = Tono(Color(0xFFCC2827), Color(0xFFFFE3DF)),
    p2 = Tono(Color(0xFFC75E00), Color(0xFFFFE9D4)),
    p3 = Tono(Color(0xFF977E0D), Color(0xFFF8EFCD)),
    p4 = Tono(Color(0xFF516C84), Color(0xFFE3ECF5)),
    ok = Tono(Color(0xFF0B7643), Color(0xFFDDF6E4)),
    vencido = Tono(Color(0xFF69737D), Color(0xFFE5E8EC)),
)

private val OSCURO = SardColores(
    oscuro = true,
    fondo = Color(0xFF0C0F11), tarjeta = Color(0xFF131518),
    texto = Color(0xFFE5E8EB), textoSuave = Color(0xFF91969B),
    suave = Color(0xFF1C1E21), borde = Color(0x17FFFFFF),
    primario = Color(0xFFE2E5E8), sobrePrimario = Color(0xFF101214),
    p1 = Tono(Color(0xFFEF6661), Color(0xFF4B1D1B)),
    p2 = Tono(Color(0xFFF2994E), Color(0xFF47280E)),
    p3 = Tono(Color(0xFFE3CA69), Color(0xFF38300F)),
    p4 = Tono(Color(0xFF90A8BD), Color(0xFF212A32)),
    ok = Tono(Color(0xFF66C189), Color(0xFF12301E)),
    vencido = Tono(Color(0xFF7D8792), Color(0xFF202429)),
)

val FiraSans = FontFamily(
    Font(R.font.fira_sans_regular, FontWeight.Normal),
    Font(R.font.fira_sans_medium, FontWeight.Medium),
    Font(R.font.fira_sans_semibold, FontWeight.SemiBold),
    Font(R.font.fira_sans_bold, FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
val FiraCode = FontFamily(
    Font(R.font.fira_code, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.fira_code, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

val LocalSardColores = staticCompositionLocalOf { CLARO }

private fun conFira(t: Typography): Typography {
    fun TextStyle.f() = copy(fontFamily = FiraSans)
    return Typography(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}

/** Envuelve las pantallas SARD con los colores, la tipografía y las esquinas del tablero. */
@Composable
fun SardTema(contenido: @Composable () -> Unit) {
    val c = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) OSCURO else CLARO
    val base = if (c.oscuro) darkColorScheme() else lightColorScheme()
    val esquema = base.copy(
        primary = c.primario, onPrimary = c.sobrePrimario,
        secondary = c.texto, onSecondary = c.tarjeta,
        secondaryContainer = c.suave, onSecondaryContainer = c.texto,
        background = c.fondo, onBackground = c.texto,
        surface = c.tarjeta, onSurface = c.texto,
        surfaceVariant = c.suave, onSurfaceVariant = c.textoSuave,
        surfaceContainerLow = c.tarjeta, surfaceContainer = c.tarjeta, surfaceContainerHigh = c.tarjeta,
        outline = c.borde, outlineVariant = c.borde,
        error = c.p1.color, onError = Color.White, errorContainer = c.p1.fondo, onErrorContainer = c.p1.color,
    )
    CompositionLocalProvider(LocalSardColores provides c) {
        MaterialTheme(
            colorScheme = esquema,
            typography = conFira(MaterialTheme.typography),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(8.dp),
                extraLarge = RoundedCornerShape(12.dp),
            ),
            content = contenido,
        )
    }
}

private val NOMBRE_PRIORIDAD = mapOf("P1" to "crítica", "P2" to "alta", "P3" to "media", "P4" to "baja")

/** Insignia de un tono: fondo suave, texto y borde del color. Como las insignias del tablero. */
@Composable
fun Insignia(tono: Tono, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = tono.fondo,
        contentColor = tono.color,
        border = BorderStroke(1.dp, tono.color.copy(alpha = 0.4f)),
    ) {
        Row(
            Modifier.heightIn(min = 24.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { contenido() }
    }
}

/** La prioridad siempre va con su texto: el color nunca es la única señal. */
@Composable
fun InsigniaPrioridad(p: String?, grande: Boolean = false) {
    val nombre = NOMBRE_PRIORIDAD[p] ?: "por definir"
    Insignia(LocalSardColores.current.prioridad(p), Modifier.semantics { contentDescription = "Prioridad $p, $nombre" }) {
        Text(p ?: "—", fontFamily = FiraCode, fontWeight = FontWeight.SemiBold, fontSize = if (grande) 15.sp else 13.sp)
        if (grande) Text(nombre, fontFamily = FiraSans, fontWeight = FontWeight.Medium, fontSize = 15.sp)
    }
}
