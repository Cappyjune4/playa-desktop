package com.cappyjune.playa.desktop.spike

import com.cappyjune.playa.desktop.data.Jellyfin
import com.cappyjune.playa.desktop.data.JfApi
import kotlinx.coroutines.runBlocking

/** Developer check: which of Continue watching / Next up have media segments on the server. */
fun main() { runBlocking {
    Jellyfin.init()
    val a = Jellyfin.acct()
    val first = JfApi.resume().first()
    println("raw: " + runCatching { Jellyfin.get(a.server, "/MediaSegments/${first.Id}", a.token) }.fold({ it.take(300) }, { "ERROR ${it.message}" }))
    println("plugins: " + runCatching { Regex("\"Name\":\"([^\"]+)\"").findAll(Jellyfin.get(a.server, "/Plugins", a.token)).joinToString { it.groupValues[1] } }.getOrElse { "ERROR ${it.message}" })
    (JfApi.resume() + JfApi.nextUp()).forEach { i ->
        val s = JfApi.segments(i.Id)
        println("${i.SeriesName ?: i.Name} ${i.episodeCode ?: ""}: ${s.joinToString { "${it.Type} ${it.startMs / 1000}-${it.endMs / 1000}s" }.ifEmpty { "none" }}")
    }
    }; kotlin.system.exitProcess(0)
}
