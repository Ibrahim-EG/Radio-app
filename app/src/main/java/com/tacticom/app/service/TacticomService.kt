package com.tacticom.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.tacticom.app.audio.AudioEngine
import com.tacticom.app.network.PeerDiscovery
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class TacticomService : Service() {
    private val binder = LocalBinder()
    val audioEngine = AudioEngine()
    lateinit var peerDiscovery: PeerDiscovery
    
    private var controlSocket: DatagramSocket? = null
    @Volatile private var isListeningControl = false

    inner class LocalBinder : Binder() {
        fun getService(): TacticomService = this@TacticomService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        peerDiscovery = PeerDiscovery(this)
        startForegroundServiceNotification()
        startControlListener()
        audioEngine.startListening()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "tacticom_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Tacticom Background Intercom",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("TACTICOM Active")
            .setContentText("Listening for incoming LAN calls...")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    private fun startControlListener() {
        isListeningControl = true
        thread(name = "Tacticom-ControlThread") {
            try {
                controlSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(50006))
                }
                val buffer = ByteArray(256)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isListeningControl) {
                    controlSocket?.receive(packet)
                    val message = String(packet.data, 0, packet.length).trim()
                    if (message == "ACTION_RING") {
                        triggerRingtoneAndVibration()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun sendRingSignal(targetIp: InetAddress) {
        thread {
            try {
                val socket = DatagramSocket()
                val data = "ACTION_RING".toByteArray()
                val packet = DatagramPacket(data, data.size, targetIp, 50006)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun triggerRingtoneAndVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }

            val notificationUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(applicationContext, notificationUri)
            ringtone.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isListeningControl = false
        controlSocket?.close()
        audioEngine.release()
    }
}
