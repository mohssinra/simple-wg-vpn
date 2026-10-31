package com.example.simplewg

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Minimal WireGuard VPN client.
 *
 * The user pastes (or imports) a standard wg-quick ".conf" file for a
 * WireGuard server they control or a VPN provider that issues WireGuard
 * configs, then taps Connect. All tunnelling is handled by the official
 * WireGuard-Android library (com.wireguard.android:tunnel).
 */
class MainActivity : ComponentActivity() {

    private lateinit var manager: TunnelManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = TunnelManager(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VpnScreen(manager = manager)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VpnScreen(manager: TunnelManager) {
    val scope = rememberCoroutineScope()

    var configText by remember { mutableStateOf(manager.loadSavedConfigText() ?: "") }
    var status by remember { mutableStateOf(manager.state()) }
    var statusMessage by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            scope.launch {
                isBusy = true
                runCatching { manager.connect() }
                    .onSuccess { status = it; statusMessage = "" }
                    .onFailure { statusMessage = "خطأ: ${it.message}" }
                isBusy = false
            }
        } else {
            statusMessage = "خاصك تسمح بـ VPN باش يخدم التطبيق"
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }
            }.onSuccess { text ->
                if (text != null) {
                    configText = text
                    statusMessage = "تقرا الملف، كبس \"حفظ\" باش تسجله"
                }
            }.onFailure {
                statusMessage = "ما قدرتش نقرا الملف: ${it.message}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Simple WG VPN",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (status) {
                Tunnel.State.UP -> "متصل"
                Tunnel.State.DOWN -> "غير متصل"
                else -> "..."
            },
            fontSize = 16.sp
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    isBusy = true
                    statusMessage = ""
                    if (status == Tunnel.State.UP) {
                        runCatching { manager.disconnect() }
                            .onSuccess { status = it }
                            .onFailure { statusMessage = "خطأ: ${it.message}" }
                    } else {
                        // Validate + save config first.
                        val saved = runCatching { manager.saveConfig(configText) }
                        if (saved.isFailure) {
                            statusMessage = "الكونفيغ ماشي صحيح: ${saved.exceptionOrNull()?.message}"
                            isBusy = false
                            return@launch
                        }
                        val intent: Intent? = manager.prepareIntent()
                        if (intent != null) {
                            vpnPermissionLauncher.launch(intent)
                        } else {
                            runCatching { manager.connect() }
                                .onSuccess { status = it }
                                .onFailure { statusMessage = "خطأ: ${it.message}" }
                        }
                    }
                    isBusy = false
                }
            },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text(if (status == Tunnel.State.UP) "قطع الاتصال" else "اتصل")
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(statusMessage, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(28.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text(
            text = "كونفيغ WireGuard (ملف .conf)",
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = configText,
            onValueChange = { configText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            placeholder = { Text("[Interface]\nPrivateKey = ...\nAddress = ...\n\n[Peer]\nPublicKey = ...\nEndpoint = ...\nAllowedIPs = 0.0.0.0/0") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii)
        )

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text("استيراد ملف .conf")
            }
            Button(
                onClick = {
                    val result = runCatching { manager.saveConfig(configText) }
                    statusMessage = if (result.isSuccess) "تسجل الكونفيغ"
                    else "خطأ: ${result.exceptionOrNull()?.message}"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("حفظ")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "خاصك سيرفر WireGuard ديالك (VPS) ولا كونفيغ من مزود VPN كيدعم WireGuard. هاد التطبيق ما كيعطيش سيرفرات، غير كيتصل بالي تعطيه.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
    }
}

