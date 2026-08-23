package com.tacticom.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.tacticom.app.service.TacticomService
import com.tacticom.app.ui.IntercomScreen
import java.net.InetAddress

class MainActivity : ComponentActivity() {
    private var tacticomService: TacticomService? = null
    private var isBound by mutableStateOf(false)
    private var multicastLock: WifiManager.MulticastLock? = null

    private var discoveredTargetIp by mutableStateOf<InetAddress?>(null)
    private var activePeersCount by mutableStateOf(0)
    private var amplitude by mutableStateOf(0f)
    private var isTransmitting by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as TacticomService.LocalBinder
            tacticomService = binder.getService()
            isBound = true
            initNetworkDiscovery()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            tacticomService = null
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            startAndBindService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("TacticomMulticastLock").apply {
            setReferenceCounted(true)
            acquire()
        }

        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
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
                        tacticomService?.audioEngine?.startTransmitting(ip) { amp -> amplitude = amp }
                    }
                },
                onPttStop = {
                    isTransmitting = false
                    tacticomService?.audioEngine?.stopTransmitting()
                },
                onRingPeers = {
                    discoveredTargetIp?.let { ip ->
                        tacticomService?.sendRingSignal(ip)
                    }
                }
            )
        }
    }

    private fun startAndBindService() {
        val serviceIntent = Intent(this, TacticomService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun initNetworkDiscovery() {
        val service = tacticomService ?: return
        service.peerDiscovery.registerPeer(Build.MODEL, 50005)
        service.peerDiscovery.startDiscovery { serviceInfo ->
            if (serviceInfo.host != null) {
                discoveredTargetIp = serviceInfo.host
                activePeersCount++
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
    }
}
