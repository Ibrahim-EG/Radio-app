package com.tacticom.app.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.LinkedList
import java.util.Queue

class PeerDiscovery(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_tacticom._udp."
    
    private val resolveQueue: Queue<NsdServiceInfo> = LinkedList()
    private var isResolving = false

    fun registerPeer(deviceName: String, port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = "TACTICOM-$deviceName"
            this.serviceType = this@PeerDiscovery.serviceType
            this.port = port
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(arg0: NsdServiceInfo, arg1: Int) {}
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {}
            override fun onUnregistrationFailed(arg0: NsdServiceInfo, arg1: Int) {}
        })
    }

    fun startDiscovery(onPeerDiscovered: (NsdServiceInfo) -> Unit) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains("tacticom")) {
                    synchronized(resolveQueue) {
                        resolveQueue.add(service)
                        resolveNext(onPeerDiscovered)
                    }
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun resolveNext(onPeerDiscovered: (NsdServiceInfo) -> Unit) {
        synchronized(resolveQueue) {
            if (isResolving || resolveQueue.isEmpty()) return
            isResolving = true
            val service = resolveQueue.poll() ?: run {
                isResolving = false
                return
            }

            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    synchronized(resolveQueue) {
                        isResolving = false
                        resolveNext(onPeerDiscovered)
                    }
                }
                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    if (serviceInfo.host != null) {
                        onPeerDiscovered(serviceInfo)
                    }
                    synchronized(resolveQueue) {
                        isResolving = false
                        resolveNext(onPeerDiscovered)
                    }
                }
            })
        }
    }
}
