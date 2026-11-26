package com.priyabrat.medusa.data.library

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.coroutines.resume

/**
 * Copies the library from the Mac over the home Wi-Fi, once, when you press "Import library from Mac" in Settings.
 *
 * On the Mac, `python3 scripts/export_library.py --serve` writes the file and offers it on the network for a few
 * minutes, announced over Bonjour as `_medusa._tcp`. The TV finds it with NsdManager (no address to type), then
 * fetches it. The emulator can't see Bonjour on the Mac's network, so it also tries the Mac itself at 10.0.2.2.
 *
 * Plain HTTP over a socket on purpose: the file only crosses the home network, and Android's HTTP stack would need
 * cleartext switched on for the whole app.
 */
class LibraryImporter(private val context: Context) {

    sealed interface Result {
        class Ok(val text: String, val from: String) : Result
        class Failed(val reason: String) : Result
    }

    suspend fun fetch(): Result = withContext(Dispatchers.IO) {
        val found = withTimeoutOrNull(DISCOVER_MS) { discover() }
        val hosts = buildList {
            if (found != null) add(found)
            add(EMULATOR_HOST to PORT)
        }
        var last = "No Mac found. On the Mac, run: python3 scripts/export_library.py --serve"
        for ((host, port) in hosts) {
            try {
                return@withContext Result.Ok(get(host, port, PATH), host)
            } catch (e: Exception) {
                if (host != EMULATOR_HOST) last = "Found the Mac at $host but could not copy: ${e.message}"
            }
        }
        Result.Failed(last)
    }

    /** First `_medusa._tcp` service on the network, resolved to host and port. */
    private suspend fun discover(): Pair<String, Int>? = suspendCancellableCoroutine { cont ->
        val nsd = context.getSystemService(NsdManager::class.java)
        var done = false
        lateinit var listener: NsdManager.DiscoveryListener
        fun finish(v: Pair<String, Int>?) {
            if (done) return
            done = true
            runCatching { nsd.stopServiceDiscovery(listener) }
            if (cont.isActive) cont.resume(v)
        }
        listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { finish(null) }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        @Suppress("DEPRECATION")
                        val host = serviceInfo.host?.hostAddress
                        if (host != null) finish(host to serviceInfo.port)
                    }
                })
            }
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        cont.invokeOnCancellation { runCatching { nsd.stopServiceDiscovery(listener) } }
    }

    /** A minimal HTTP/1.0 GET: status line, headers, then the body until the Mac closes the connection. */
    private fun get(host: String, port: Int, path: String): String {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), CONNECT_MS)
            s.soTimeout = READ_MS
            s.getOutputStream().write("GET $path HTTP/1.0\r\nHost: $host\r\n\r\n".toByteArray())
            val bytes = ByteArrayOutputStream().also { s.getInputStream().copyTo(it) }.toByteArray()
            var split = -1
            for (i in 0 until bytes.size - 3) {
                if (bytes[i] == '\r'.code.toByte() && bytes[i + 1] == '\n'.code.toByte() && bytes[i + 2] == '\r'.code.toByte() && bytes[i + 3] == '\n'.code.toByte()) { split = i; break }
            }
            require(split > 0) { "bad reply" }
            val status = String(bytes, 0, split, Charsets.ISO_8859_1).lineSequence().first()
            require(" 200 " in "$status ") { status }
            return String(bytes, split + 4, bytes.size - split - 4, Charsets.UTF_8)
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_medusa._tcp."
        const val PATH = "/medusa-library.json"
        const val PORT = 4322
        const val EMULATOR_HOST = "10.0.2.2"
        const val DISCOVER_MS = 8_000L
        const val CONNECT_MS = 4_000
        const val READ_MS = 20_000
    }
}
