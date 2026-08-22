package com.tacticom.app

import android.Manifest
import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.tacticom.app.audio.AudioEngine
import com.tacticom.app.network.PeerDiscovery
import com.tacticom.app.ui.IntercomScreen
import java.net.InetAddress

class MainActivity : ComponentActivity() {
    private val audioEngine = AudioEngine()
    private lateinit var peerDiscovery: PeerDiscovery
    private var multicastLock: WifiManager.MulticastLock? = null

    private var discoveredTargetIp by mutableStateOf<InetAddress?>(null)
    private var activePeersCount by mutableStateOf(0)
    private var amplitude by mutableStateOf(0f)
    private var isTransmitting by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (micGranted) {
            initNetworkAndAudio()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("TacticomMulticastLock").apply {
            setReferenceCounted(true)
            acquire()
        }

        peerDiscovery = PeerDiscovery(this)

        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())

        setContent {
            IntercomScreen(
                activePeersCount = activePeersCount,
                amplitude = amplitude,
                isTransmitting = isTransmitting,
                onPttStart = {
                    discoveredTargetIp?.let { ip ->
                        isTransmitting = true
                        audioEngine.startTransmitting(ip) { amp -> amplitude = amp }
                    }
                },
                onPttStop = {
                    isTransmitting = false
                    audioEngine.stopTransmitting()
                }
            )
        }
    }

    private fun initNetworkAndAudio() {
        audioEngine.startListening()
        peerDiscovery.registerPeer(Build.MODEL, 50005)
        peerDiscovery.startDiscovery { serviceInfo ->
            if (serviceInfo.host != null) {
                discoveredTargetIp = serviceInfo.host
                activePeersCount++
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.release()
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
    }
}
