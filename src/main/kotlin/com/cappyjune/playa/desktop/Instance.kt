package com.cappyjune.playa.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import kotlin.concurrent.thread

/**
 * One Playa at a time: "Open with Playa" (or the launcher) while it's already open hands the
 * files to the open window, which plays them and comes to the front, instead of opening another.
 */
object Instance {
    private val path: Path = Path.of(System.getenv("XDG_RUNTIME_DIR")?.ifBlank { null } ?: System.getProperty("java.io.tmpdir"), "playa-desktop.sock")

    /** Bumped when another launch asks for the window; Main brings it to the front. */
    var raise by mutableIntStateOf(0)

    /** Gives [files] to a Playa that's already running; false if there isn't one. */
    fun handOff(files: List<String>): Boolean = runCatching {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { ch ->
            ch.connect(UnixDomainSocketAddress.of(path))
            ch.write(ByteBuffer.wrap(files.joinToString("\n", postfix = "\n").toByteArray()))
        }
        true
    }.getOrDefault(false)

    /** Becomes the running Playa: files from later launches arrive at [onFiles] (on the UI thread). */
    fun listen(onFiles: (List<String>) -> Unit) {
        runCatching {
            Files.deleteIfExists(path)  // left behind by a Playa that didn't close cleanly
            val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).apply { bind(UnixDomainSocketAddress.of(path)) }
            path.toFile().deleteOnExit()
            thread(isDaemon = true, name = "playa-instance") {
                while (true) {
                    val files = runCatching { server.accept().use { ch -> Channels.newReader(ch, Charsets.UTF_8).readLines().filter { it.isNotBlank() } } }.getOrNull() ?: continue
                    javax.swing.SwingUtilities.invokeLater { raise++; onFiles(files) }
                }
            }
        }
    }
}
