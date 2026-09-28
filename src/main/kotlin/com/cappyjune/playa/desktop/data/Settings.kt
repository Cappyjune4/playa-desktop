package com.cappyjune.playa.desktop.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

/** Where Playa keeps its things: ~/.config/playa-desktop (or $XDG_CONFIG_HOME). */
object Paths {
    val config: File = File(System.getenv("XDG_CONFIG_HOME")?.ifBlank { null } ?: (System.getProperty("user.home") + "/.config"), "playa-desktop")
        .apply { mkdirs() }
    /** Downloads and the like: things that can't just be made again. */
    val data: File = File(System.getenv("XDG_DATA_HOME")?.ifBlank { null } ?: (System.getProperty("user.home") + "/.local/share"), "playa-desktop")
        .apply { mkdirs() }
    val cache: File = File(System.getenv("XDG_CACHE_HOME")?.ifBlank { null } ?: (System.getProperty("user.home") + "/.cache"), "playa-desktop")
        .apply { mkdirs() }

    /** Writes a file only this user can read (for anything with a token in it). */
    fun writePrivate(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        runCatching { Files.setPosixFilePermissions(tmp.toPath(), PosixFilePermissions.fromString("rw-------")) }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}

/** App-wide settings as flows for Compose, saved as JSON whenever one changes. */
object Settings {
    enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
    enum class Colours(val label: String) { ACCENT("Playa blue"), ARTWORK("Poster art") }
    enum class AppFont(val label: String) {
        GOOGLE_ROUNDED("Google Sans Rounded"), NEVERMIND_ROUNDED("NeverMind Rounded"), NEVERMIND("NeverMind (muSE)"), SYSTEM("System")
    }

    @Serializable
    data class Saved(
        val theme: ThemeMode = ThemeMode.SYSTEM,
        val colours: Colours = Colours.ARTWORK,
        val font: AppFont = AppFont.GOOGLE_ROUNDED,
        val pureBlack: Boolean = false,
        /** Jellyfin sees each install as its own device. */
        val deviceId: String = UUID.randomUUID().toString(),
        /** Where and how big the window was last time. */
        val window: WindowBounds? = null,
        val skipBack: Int = 10,
        val skipForward: Int = 10,
        /** Where the Local section looks for videos (and everything under them). */
        val videoFolders: List<String> = listOf(defaultVideos()),
    )
    @Serializable data class WindowBounds(val x: Int, val y: Int, val width: Int, val height: Int, val maximized: Boolean = false)

    /** The desktop's Videos folder (it's localised on some systems, so ask). */
    fun defaultVideos(): String = runCatching {
        ProcessBuilder("xdg-user-dir", "VIDEOS").start().inputStream.bufferedReader().readText().trim().takeIf { it.isNotBlank() && File(it).isDirectory }
    }.getOrNull() ?: (System.getProperty("user.home") + "/Videos")

    private val file = File(Paths.config, "settings.json")
    // Everything written out, defaults too (the device id is random, so it must be kept).
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val _s = MutableStateFlow(
        runCatching { json.decodeFromString(Saved.serializer(), file.readText()) }.getOrNull() ?: Saved().also { write(it) }
    )
    val state: StateFlow<Saved> = _s.asStateFlow()
    val now get() = _s.value

    fun update(f: (Saved) -> Saved) {
        val new = f(_s.value)
        if (new != _s.value) { _s.value = new; write(new) }
    }

    private fun write(s: Saved) = runCatching { Paths.writePrivate(file, json.encodeToString(Saved.serializer(), s)) }
}
