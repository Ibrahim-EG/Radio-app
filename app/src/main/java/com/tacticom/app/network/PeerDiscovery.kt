package com.tacticom.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

class PeerDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_tacticom._tcp."
    
    // FIX #10: Store discovery listener for cleanup
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun registerPeer(deviceName: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Tacticom_$deviceName"
            setServiceType(serviceType)
            setPort(port)
        }
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo?) {}
                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo?) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startDiscovery(
        onPeerFound: (NsdServiceInfo) -> Unit,
        onPeerLost: (() -> Unit)? = null
    ) {
        try {
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
                override fun onDiscoveryStarted(serviceType: String?) {}
                override fun onDiscoveryStopped(serviceType: String?) {}

                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    if (serviceInfo != null && serviceInfo.serviceType.contains("_tacticom")) {
                        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {}
                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                resolvedInfo?.let(onPeerFound)
                            }
                        })
                    }
                }

                // FIX #2 & #10: Properly handle peer loss
                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                    onPeerLost?.invoke()
                }
            }
            
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener!!)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // FIX #10: Add method to stop discovery and clean up resources
    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        discoveryListener = null
    }
}
