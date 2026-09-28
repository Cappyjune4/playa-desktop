package com.cappyjune.playa.desktop.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.runtime.collectAsState
import com.cappyjune.playa.desktop.data.Settings
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.ktx.animateColorScheme

/** Playa's blue, the first of muSE's wordmark colours. */
val PlayaBlue = Color(0xFF4DA6F5)

/**
 * Light or dark follows GNOME (or the user's choice). Colours grow from Playa's blue, or from the
 * artwork of what's showing, as on the phone. Motion is Material 3 Expressive's springs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayaTheme(artwork: Color? = null, forceDark: Boolean = false, content: @Composable () -> Unit) {
    val s by Settings.state.collectAsState()
    val dark = forceDark || when (s.theme) {
        Settings.ThemeMode.SYSTEM -> systemDark
        Settings.ThemeMode.LIGHT -> false
        Settings.ThemeMode.DARK -> true
    }
    val seed = (if (s.colours == Settings.Colours.ARTWORK) artwork else null) ?: PlayaBlue
    val scheme = remember(seed, dark, s.pureBlack) {
        dynamicColorScheme(seedColor = seed, isDark = dark, isAmoled = dark && s.pureBlack, style = PaletteStyle.TonalSpot)
    }
    val type = remember(s.font) { typographyFor(s.font) }
    MaterialTheme(colorScheme = animateColorScheme(scheme), motionScheme = MotionScheme.expressive(), typography = type, content = content)
}

/** GNOME's own light/dark choice (Settings → Appearance), read once at start. */
val systemDark: Boolean by lazy {
    runCatching {
        val p = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().contains("dark")
    }.getOrDefault(true)
}

// --- fonts (all SIL Open Font License; the texts are in resources/fonts) ----------------

private fun bytes(path: String) = Thread.currentThread().contextClassLoader.getResourceAsStream(path)!!.use { it.readBytes() }

/** Google Sans Flex (Material 3 Expressive's typeface), roundness turned up, one Font per weight. */
private val googleRounded by lazy {
    val data = bytes("fonts/google_sans_flex.ttf")
    FontFamily(listOf(400, 500, 600, 700, 800, 900).map { w ->
        Font("google-sans-rounded-$w", data, FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.Setting("ROND", 100f)))
    })
}

private fun nevermind(kind: String) = FontFamily(
    listOf("regular" to FontWeight.Normal, "medium" to FontWeight.Medium, "semibold" to FontWeight.SemiBold, "bold" to FontWeight.Bold).map { (n, w) ->
        Font("nevermind-$kind-$n", bytes("fonts/nevermind_${kind}_$n.ttf"), w)
    }
)
private val nevermindRounded by lazy { nevermind("rounded") }
private val nevermindCompact by lazy { nevermind("compact") }

fun typographyFor(font: Settings.AppFont): Typography {
    val base = Typography()
    val f = when (font) {
        Settings.AppFont.GOOGLE_ROUNDED -> googleRounded
        Settings.AppFont.NEVERMIND_ROUNDED -> nevermindRounded
        Settings.AppFont.NEVERMIND -> nevermindCompact
        Settings.AppFont.SYSTEM -> return base
    }
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = f), displayMedium = base.displayMedium.copy(fontFamily = f),
        displaySmall = base.displaySmall.copy(fontFamily = f), headlineLarge = base.headlineLarge.copy(fontFamily = f),
        headlineMedium = base.headlineMedium.copy(fontFamily = f), headlineSmall = base.headlineSmall.copy(fontFamily = f),
        titleLarge = base.titleLarge.copy(fontFamily = f), titleMedium = base.titleMedium.copy(fontFamily = f),
        titleSmall = base.titleSmall.copy(fontFamily = f), bodyLarge = base.bodyLarge.copy(fontFamily = f),
        bodyMedium = base.bodyMedium.copy(fontFamily = f), bodySmall = base.bodySmall.copy(fontFamily = f),
        labelLarge = base.labelLarge.copy(fontFamily = f), labelMedium = base.labelMedium.copy(fontFamily = f),
        labelSmall = base.labelSmall.copy(fontFamily = f),
    )
}

