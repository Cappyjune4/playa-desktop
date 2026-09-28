plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(compose.desktop.currentOs)
    // The alpha, as on the phone: Material 3 Expressive (its spring motion) is public from here.
    implementation("org.jetbrains.compose.material3:material3:1.12.0-alpha03")
    implementation(compose.materialIconsExtended)
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("com.materialkolor:material-kolor:5.0.1")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
}

// The version: 1.0 at the first packaged commit, then 1.0.N (N commits later), as the phone apps do.
val commits = providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }.standardOutput.asText.get().trim().toInt()
val firstRelease = 1
val appVersion = if (commits <= firstRelease) "1.0.0" else "1.0.${commits - firstRelease}"

compose.desktop {
    application {
        mainClass = "com.cappyjune.playa.desktop.MainKt"
        // For naming the window's class "playa", so the dock matches it to its launcher (see Main).
        jvmArgs += listOf("--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED", "-Dplaya.version=$appVersion")
        nativeDistributions {
            packageName = "Playa"
            packageVersion = appVersion
            description = "Jellyfin, live TV and your own videos"
            vendor = "cappy"
            // What the bundled Java needs beyond the basics (jdk.unsupported for Compose, the rest for OkHttp/JNA).
            modules("java.instrument", "java.management", "java.naming", "java.net.http", "java.sql", "jdk.unsupported", "jdk.crypto.ec", "jdk.security.auth")
        }
    }
}

// Developer tool: draw the UI off-screen to a PNG (see spike/Render.kt).
tasks.register<JavaExec>("render") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.cappyjune.playa.desktop.spike.RenderKt")
    args = (project.findProperty("out") as String? ?: "build/render.png").let { listOf(it) + ((project.findProperty("size") as String?)?.split("x") ?: listOf("1400", "900")) } +
        listOfNotNull(project.findProperty("video") as String?)
    // -Psection=LIBRARY, -Ppage=<item id>|search, -Psettle=<seconds to wait for the server>
    listOf("section", "page", "settle").forEach { k -> (project.findProperty(k) as String?)?.let { systemProperty("playa.$k", it) } }
}

// Developer tool: the playback measurement from the spike (see spike/Spike.kt).
tasks.register<JavaExec>("spike") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.cappyjune.playa.desktop.spike.SpikeKt")
    args = listOf(project.findProperty("video") as String? ?: "", "--seconds=10")
}

// Developer tool: which items have intro/credits segments (see spike/JfCheck.kt).
tasks.register<JavaExec>("jfcheck") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.cappyjune.playa.desktop.spike.JfCheckKt")
}

// Developer tool: the launcher icon PNGs for the package (see spike/Icon.kt).
tasks.register<JavaExec>("icon") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.cappyjune.playa.desktop.spike.IconKt")
    args = listOf("packaging/icons")
}

// For packaging/package.sh.
tasks.register("printVersion") { doLast { println(appVersion) } }
