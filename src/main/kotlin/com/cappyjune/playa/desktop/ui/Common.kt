package com.cappyjune.playa.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.xml.sax.InputSource

/** muSE's four wordmark colours (blue, green, amber, red), text-safe for the current surface. */
@Composable
fun playaColours(): List<Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return (if (dark) listOf(0xFF4DA6F5, 0xFF66BB6A, 0xFFFFB74D, 0xFFEF7061)
    else listOf(0xFF315ABE, 0xFF2E7D32, 0xFFE08E0B, 0xFFC0392B)).map { Color(it) }
}

/** The "Playa" wordmark, muSE's colours letter by letter (the fifth wraps round to blue). */
@Composable
fun PlayaLogo(big: Boolean = false) {
    val colours = playaColours()
    Text(
        buildAnnotatedString { "Playa".forEachIndexed { i, ch -> withStyle(SpanStyle(color = colours[i % colours.size])) { append(ch) } } },
        style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
    )
}

/** An Android vector drawable from resources/drawable, as the phone app uses them. */
fun vector(name: String): ImageVector =
    Thread.currentThread().contextClassLoader.getResourceAsStream("drawable/$name.xml")!!.use { loadXmlImageVector(InputSource(it), Density(1f)) }

private val jellyfin by lazy { vector("ic_jellyfin_color") }
val playaIcon by lazy { vector("ic_launcher_fg") }

/** Jellyfin's own logo in full colour, for the Jellyfin sections and sign-in. */
@Composable
fun JellyfinLogo(size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Image(rememberVectorPainter(remember { jellyfin }), "Jellyfin", modifier.size(size))
}

/** "Jellyfin" with its logo, "Live TV" with its icon...: what a section's heading says. */
@Composable
fun SectionTitle(s: Section) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (s.jellyfin) JellyfinLogo(32.dp) else Icon(s.icon, null, Modifier.size(30.dp), tint = sectionColour(s))
        Spacer(Modifier.width(12.dp))
        Text(s.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

/** Each section's colour, from the wordmark palette, so the menu and the headings match. */
@Composable
fun sectionColour(s: Section) = playaColours()[s.ordinal % 4]
