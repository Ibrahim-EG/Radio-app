package com.tacticom.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

class AudioEngine(private val context: Context? = null) {
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioFormat).coerceAtLeast(1024)

    @Volatile private var isRecording = false
    @Volatile private var isListening = false
    private var rxSocket: DatagramSocket? = null
    private var recordingThread: Thread? = null

    fun startListening() {
        if (isListening) return
        isListening = true
        thread(name = "Tacticom-AudioRX") {
            var track: AudioTrack? = null
            try {
                // FIX #4: Assign socket before setting listening flag
                rxSocket = DatagramSocket(50005)
                val buffer = ByteArray(minBufferSize)
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(audioFormat)
                            .setSampleRate(sampleRate)
                            .setChannelMask(channelConfigOut)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize)
                    .build()

                track.play()

                while (isListening) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    rxSocket?.receive(packet)
                    track.write(packet.data, 0, packet.length)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // FIX #5: Ensure resources are always released
                try {
                    track?.stop()
                    track?.release()
                    rxSocket?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startTransmitting(targetIp: InetAddress, onAmplitude: (Float) -> Unit) {
        // FIX #6: Re-check permissions before recording
        if (context != null) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        if (isRecording) return
        isRecording = true
        recordingThread = thread(name = "Tacticom-AudioTX") {
            var recorder: AudioRecord? = null
            var socket: DatagramSocket? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfigIn,
                    audioFormat,
                    minBufferSize
                )
                socket = DatagramSocket()
                val buffer = ByteArray(minBufferSize)
                recorder.startRecording()

                while (isRecording) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        // FIX #3: Prevent array index out of bounds
                        for (i in 0 until (read - 1) step 2) {
                            val sample = (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8)).toShort()
                            sum += sample * sample
                        }
                        val amp = Math.sqrt(sum / (read / 2)) / 32768.0
                        onAmplitude(amp.toFloat().coerceIn(0f, 1f))

                        val packet = DatagramPacket(buffer, read, targetIp, 50005)
                        socket.send(packet)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                    socket?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stopTransmitting() {
        isRecording = false
        // FIX #7: Wait for recording thread to complete
        try {
            recordingThread?.join(5000)  // Wait up to 5 seconds
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun release() {
        isRecording = false
        isListening = false
        try {
            rxSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
