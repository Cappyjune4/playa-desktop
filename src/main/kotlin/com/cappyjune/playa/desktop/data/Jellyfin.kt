package com.cappyjune.playa.desktop.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
data class JellyfinAccount(val server: String, val userId: String, val userName: String, val token: String)

/**
 * Jellyfin client. The account (with its token) lives only in the keyring and in the
 * Authorization header, which Http adds for requests to the signed-in server. It never goes in
 * a URL, so it can't leak into logs or mpv's stream metadata.
 */
object Jellyfin {
    private const val CLIENT = "Playa Desktop"
    private const val VERSION = "0.1.0"
    private val JSON = "application/json".toMediaType()

    private val _account = MutableStateFlow<JellyfinAccount?>(null)
    val account: StateFlow<JellyfinAccount?> = _account.asStateFlow()
    val deviceId: String get() = Settings.now.deviceId
    /** What Jellyfin's dashboard calls this device: the computer's name. */
    private val deviceName: String = runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrNull()?.ifBlank { null } ?: "Ubuntu"

    fun init() {
        _account.value = Keyring.get()?.let {
            runCatching { Http.json.decodeFromString(JellyfinAccount.serializer(), it) }.getOrNull()
        }
    }

    /** Header value; with no token it still identifies the client, as sign-in requires. */
    fun authHeader(token: String? = _account.value?.token): String =
        "MediaBrowser Client=\"$CLIENT\", Device=\"$deviceName\", DeviceId=\"$deviceId\", Version=\"$VERSION\"" +
            (token?.let { ", Token=\"$it\"" } ?: "")

    /** True when a request is going to the signed-in server, so it should carry the token. */
    fun owns(url: okhttp3.HttpUrl): Boolean {
        val s = _account.value?.server?.toHttpUrlOrNull() ?: return false
        return url.host == s.host && url.port == s.port
    }

    fun normalise(server: String): String {
        var s = server.trim().trimEnd('/')
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
        return s
    }

    // --- sign in ---------------------------------------------------------------------

    @Serializable private data class AuthResult(val AccessToken: String, val User: AuthUser)
    @Serializable private data class AuthUser(val Id: String, val Name: String)

    suspend fun signIn(server: String, user: String, password: String) {
        val base = normalise(server)
        val body = buildJsonObject { put("Username", user); put("Pw", password) }.toString()
        val res = post(base, "/Users/AuthenticateByName", body, token = null)
        save(base, Http.json.decodeFromString(AuthResult.serializer(), res))
    }

    @Serializable data class QuickConnect(val Code: String, val Secret: String, val Authenticated: Boolean = false)

    suspend fun quickConnectStart(server: String): QuickConnect =
        Http.json.decodeFromString(QuickConnect.serializer(), post(normalise(server), "/QuickConnect/Initiate", "", token = null))

    suspend fun quickConnectApproved(server: String, secret: String): Boolean =
        Http.json.decodeFromString(QuickConnect.serializer(), get(normalise(server), "/QuickConnect/Connect?secret=$secret", token = null)).Authenticated

    suspend fun quickConnectFinish(server: String, secret: String) {
        val base = normalise(server)
        val res = post(base, "/Users/AuthenticateWithQuickConnect", buildJsonObject { put("Secret", secret) }.toString(), token = null)
        save(base, Http.json.decodeFromString(AuthResult.serializer(), res))
    }

    private fun save(server: String, r: AuthResult) {
        val acct = JellyfinAccount(server, r.User.Id, r.User.Name, r.AccessToken)
        Keyring.put(Http.json.encodeToString(JellyfinAccount.serializer(), acct))
        _account.value = acct
    }

    suspend fun signOut() {
        val a = _account.value ?: return
        runCatching { post(a.server, "/Sessions/Logout", "", a.token) } // revokes the token server-side
        Keyring.put(null)
        _account.value = null
    }

    // --- library (video browsing arrives with the Jellyfin tabs) ------------------------

    internal fun acct() = _account.value ?: error("Not signed in")

    // --- http ------------------------------------------------------------------------

    internal suspend fun get(base: String, path: String, token: String?): String = call(
        Request.Builder().url(base + path).header("Authorization", authHeader(token)).build()
    )

    internal suspend fun post(base: String, path: String, json: String, token: String?): String = call(
        Request.Builder().url(base + path).header("Authorization", authHeader(token)).post(json.toRequestBody(JSON)).build()
    )

    /** DELETE (or any method) with an optional JSON body, for favourites, watched and admin actions. */
    internal suspend fun send(method: String, path: String, json: String? = null): String {
        val a = acct()
        val body = json?.toRequestBody(JSON) ?: if (method == "POST") "".toRequestBody(JSON) else null
        return call(Request.Builder().url(a.server + path).header("Authorization", authHeader(a.token)).method(method, body).build())
    }

    private suspend fun call(req: Request): String = withContext(Dispatchers.IO) {
        Http.client.newCall(req).execute().use { r ->
            when {
                r.code == 401 -> error("Wrong username or password, or the session has expired")
                !r.isSuccessful -> error("Server said ${r.code}")
                else -> r.body.string()
            }
        }
    }
}
