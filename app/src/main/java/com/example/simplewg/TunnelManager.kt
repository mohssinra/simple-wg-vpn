package com.example.simplewg

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Thin wrapper around the official WireGuard-Android library.
 * It owns exactly one tunnel named "wg0", backed by a wg-quick-style
 * config file stored in the app's private storage.
 */
class TunnelManager(context: Context) {

    private val appContext = context.applicationContext
    private val configFile = File(appContext.filesDir, "wg0.conf")

    private val backend: Backend by lazy { GoBackend(appContext) }

    private var currentState: Tunnel.State = Tunnel.State.DOWN

    val tunnel: Tunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME
        override fun onStateChange(newState: Tunnel.State) {
            currentState = newState
        }
    }

    /** Returns the GoBackend VPN-consent intent, or null if permission is already granted. */
    fun prepareIntent() = GoBackend.VpnService.prepare(appContext)

    fun hasSavedConfig(): Boolean = configFile.exists()

    fun loadSavedConfigText(): String? =
        if (configFile.exists()) configFile.readText() else null

    /** Validates and persists a wg-quick style config (throws on invalid input). */
    fun saveConfig(rawText: String): Config {
        val parsed = Config.parse(ByteArrayInputStream(rawText.toByteArray(Charsets.UTF_8)))
        configFile.writeText(rawText)
        return parsed
    }

    fun readConfigFromStream(reader: BufferedReader): String =
        reader.readText()

    suspend fun connect(): Tunnel.State = withContext(Dispatchers.IO) {
        val text = loadSavedConfigText()
            ?: throw IllegalStateException("No config saved yet")
        val config = Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        backend.setState(tunnel, Tunnel.State.UP, config)
    }

    suspend fun disconnect(): Tunnel.State = withContext(Dispatchers.IO) {
        backend.setState(tunnel, Tunnel.State.DOWN, null)
    }

    fun state(): Tunnel.State = currentState

    companion object {
        private const val TUNNEL_NAME = "wg0"
    }
}
