package com.tacticom.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.concurrent.thread

class AudioEngine(private val port: Int = 50005) {
    private val sampleRate = 48000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    private val minRecBuffer = maxOf(AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat), 2048)
    private val minPlayBuffer = maxOf(AudioTrack.getMinBufferSize(sampleRate, channelOut, audioFormat), 2048)

    @Volatile private var isTransmitting = false
    @Volatile private var isListening = false

    private var socket: DatagramSocket? = null

    @Synchronized
    private fun getOrCreateSocket(): DatagramSocket {
        val currentSocket = socket
        if (currentSocket != null && !currentSocket.isClosed) {
            return currentSocket
        }
        val newSocket = DatagramSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(port))
        }
        socket = newSocket
        return newSocket
    }

    fun startListening() {
        if (isListening) return
        isListening = true
        
        thread(name = "Tacticom-RxThread") {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelOut)
                        .build()
                )
                .setBufferSizeInBytes(minPlayBuffer)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) return@thread

            track.play()
            val activeSocket = getOrCreateSocket()
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isListening && !activeSocket.isClosed) {
                try {
                    activeSocket.receive(packet)
                    if (!isTransmitting) {
                        track.write(packet.data, 0, packet.length)
                    }
                } catch (e: Exception) {
                    break
                }
            }
            track.stop()
            track.release()
        }
    }

    @SuppressLint("MissingPermission")
    fun startTransmitting(targetIp: InetAddress, onAmplitude: (Float) -> Unit) {
        if (isTransmitting) return
        isTransmitting = true

        thread(name = "Tacticom-TxThread") {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelIn,
                audioFormat,
                minRecBuffer
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                isTransmitting = false
                return@thread
            }
            
            val activeSocket = getOrCreateSocket()
            val buffer = ByteArray(1024)
            recorder.startRecording()

            while (isTransmitting && !activeSocket.isClosed) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    try {
                        val packet = DatagramPacket(buffer, read, targetIp, port)
                        activeSocket.send(packet)
                    } catch (e: Exception) {
                        break
                    }

                    var maxPeak = 0
                    for (i in 0 until read step 2) {
                        val sample = (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8)).toShort()
                        val abs = Math.abs(sample.toInt())
                        if (abs > maxPeak) maxPeak = abs
                    }
                    onAmplitude(maxPeak / 32768f)
                }
            }
            recorder.stop()
            recorder.release()
            onAmplitude(0f)
        }
    }

    fun stopTransmitting() {
        isTransmitting = false
    }

    fun release() {
        isTransmitting = false
        isListening = false
        socket?.close()
        socket = null
    }
}
