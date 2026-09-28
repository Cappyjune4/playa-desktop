package com.cappyjune.playa.desktop.playback

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/** The parts of libmpv's C API Playa uses (client.h and render.h). */
@Suppress("FunctionName")
internal interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_error_string(error: Int): String
    fun mpv_render_context_create(res: PointerByReference, ctx: Pointer, params: Pointer): Int
    fun mpv_render_context_set_update_callback(rc: Pointer, callback: UpdateCallback?, data: Pointer?)
    fun mpv_render_context_update(rc: Pointer): Long
    fun mpv_render_context_render(rc: Pointer, params: Pointer): Int
    fun mpv_render_context_free(rc: Pointer)

    fun interface UpdateCallback : Callback { fun invoke(data: Pointer?) }

    /** libc's setlocale: mpv refuses to start unless numbers are formatted the "C" way. */
    interface LibC : Library { fun setlocale(category: Int, locale: String): String? }

    companion object {
        val lib: LibMpv by lazy { Native.load("mpv", LibMpv::class.java) }
    }
}

/**
 * One mpv player whose frames land in a Skia bitmap that Compose draws, so Material controls sit
 * on top of the video like any other layer. mpv renders into the bitmap's own memory (its
 * software render API); decoding still uses the GPU where it can (hwdec auto-copy).
 */
class MpvPlayer {
    private val mpv = LibMpv.lib
    private val ctx: Pointer = run {
        Native.load("c", LibMpv.LibC::class.java).setlocale(1 /* LC_NUMERIC */, "C")
        mpv.mpv_create() ?: error("libmpv couldn't start")
    }
    private var render: Pointer? = null
    private val wake = Semaphore(0)
    // Kept in a field so the JVM doesn't collect it while mpv still calls it.
    private val onUpdate = LibMpv.UpdateCallback { wake.release() }
    @Volatile private var running = true

    /** Two bitmaps: mpv draws into one while Compose shows the other. */
    private val bitmaps = arrayOf(Bitmap(), Bitmap())
    private var back = 0
    @Volatile var targetWidth = 1280
    @Volatile var targetHeight = 720

    /** Called on mpv's render thread with each new frame. */
    var onFrame: (Bitmap) -> Unit = {}

    // For measuring: frames drawn and time spent drawing them.
    @Volatile var framesRendered = 0L; private set
    @Volatile var renderNanos = 0L; private set

    init {
        opt("vo", "libmpv")
        opt("hwdec", "auto-copy")  // decode on the GPU, frames copied back for the software renderer
        opt("keep-open", "yes")
        opt("terminal", "no")
        opt("sub-auto", "fuzzy")  // subtitle files beside a video, "Film.en.srt" included
        check(mpv.mpv_initialize(ctx) >= 0) { "mpv_initialize failed" }
        val params = params(1 to Memory(3).apply { setString(0, "sw") })  // MPV_RENDER_PARAM_API_TYPE
        val rc = PointerByReference()
        val err = mpv.mpv_render_context_create(rc, ctx, params)
        check(err >= 0) { "mpv render context: ${mpv.mpv_error_string(err)}" }
        render = rc.value
        mpv.mpv_render_context_set_update_callback(render!!, onUpdate, null)
        Thread(::renderLoop, "mpv-render").apply { isDaemon = true }.start()
    }

    private fun opt(name: String, value: String) { mpv.mpv_set_option_string(ctx, name, value) }

    fun load(path: String) { command("loadfile", path) }
    fun command(vararg args: String) { mpv.mpv_command(ctx, arrayOf(*args, null)) }
    fun set(name: String, value: String) { mpv.mpv_set_property_string(ctx, name, value) }
    fun get(name: String): String? = mpv.mpv_get_property_string(ctx, name)?.let { p -> p.getString(0).also { mpv.mpv_free(p) } }

    private fun renderLoop() {
        // "bgra", not "bgr0": bgr0 leaves the fourth byte 0, which Skia takes as see-through when it composites.
        val size = Memory(8); val format = Memory(5).apply { setString(0, "bgra") }; val stride = Memory(8)
        while (running) {
            if (!wake.tryAcquire(100, TimeUnit.MILLISECONDS)) continue
            val rc = render ?: break
            // Bit 0 (MPV_RENDER_UPDATE_FRAME): there's a new frame to draw.
            if (mpv.mpv_render_context_update(rc) and 1L == 0L) continue
            val w = targetWidth.coerceAtLeast(16); val h = targetHeight.coerceAtLeast(16)
            val bmp = bitmaps[back]
            if (bmp.width != w || bmp.height != h) bmp.allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE))
            val pixels = bmp.peekPixels() ?: continue
            size.setInt(0, w); size.setInt(4, h); stride.setLong(0, bmp.rowBytes.toLong())
            val params = params(17 to size, 18 to format, 19 to stride, 20 to Pointer(pixels.addr))  // SW_SIZE, SW_FORMAT, SW_STRIDE, SW_POINTER
            val t0 = System.nanoTime()
            mpv.mpv_render_context_render(rc, params)
            renderNanos += System.nanoTime() - t0; framesRendered++
            bmp.notifyPixelsChanged()
            back = 1 - back
            onFrame(bmp)
        }
    }

    /** A NULL-terminated mpv_render_param array: { int type; void *data; } per entry, 16 bytes on 64-bit. */
    private fun params(vararg entries: Pair<Int, Pointer>): Memory {
        val m = Memory(16L * (entries.size + 1)); m.clear()
        entries.forEachIndexed { i, (type, data) -> m.setInt(16L * i, type); m.setPointer(16L * i + 8, data) }
        return m
    }

    fun close() {
        running = false
        render?.let { mpv.mpv_render_context_set_update_callback(it, null, null); mpv.mpv_render_context_free(it) }
        render = null
        mpv.mpv_terminate_destroy(ctx)
    }
}
