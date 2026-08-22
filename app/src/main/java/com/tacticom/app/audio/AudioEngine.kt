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
import kotlin.concurrent.thread

class AudioEngine(private val port: Int = 50005) {
    private val sampleRate = 48000
    private val channelIn = AudioFormat.CHANNEL_IN_MONO
    private val channelOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    private val minRecBuffer = AudioRecord.getMinBufferSize(sampleRate, channelIn, audioFormat)
    private val minPlayBuffer = AudioTrack.getMinBufferSize(sampleRate, channelOut, audioFormat)

    @Volatile private var isTransmitting = false
    @Volatile private var isListening = false

    private var socket: DatagramSocket? = null

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

            track.play()
            
            if (socket == null || socket?.isClosed == true) {
                socket = DatagramSocket(port)
            }
            
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)

            while (isListening) {
                try {
                    socket?.receive(packet)
                    if (!isTransmitting) { // Avoid echoing local transmission
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
            
            val buffer = ByteArray(1024)
            recorder.startRecording()

            while (isTransmitting) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val packet = DatagramPacket(buffer, read, targetIp, port)
                    socket?.send(packet)

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
