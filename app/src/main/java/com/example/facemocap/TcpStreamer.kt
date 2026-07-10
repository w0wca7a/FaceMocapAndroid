package com.example.facemocap

import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Minimal TCP server streaming per-frame face blendshape scores.
 *
 *   ?FFFF eyeBlinkLeft_0.812|jawOpen_0.045|mouthSmileLeft_0.230|...|
 *
 * - '?'  frame start marker
 * - FFFF: 4-digit zero-padded rolling frame counter (0000..9999)
 * - entries separated by '|', each as name_score (score 0..1, 3 decimals)
 *
 * Only one client is served at a time (matches the original app's behaviour of a phone
 * acting as a TCP server that a single receiver connects to).
 */
class TcpStreamer(private val port: Int) {

    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val clientOut = AtomicReference<OutputStream?>(null)
    private var frameCounter = 0

    /** Called with true when a client connects, false when it disconnects. */
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null

    fun start() {
        if (running.get()) return
        running.set(true)

        thread(isDaemon = true) {
            try {
                serverSocket = ServerSocket(port)
                while (running.get()) {
                    val socket: Socket = serverSocket!!.accept()
                    socket.tcpNoDelay = true
                    clientOut.set(socket.getOutputStream())
                    onConnectionStateChanged?.invoke(true)
                }
            } catch (_: Exception) {
                // socket closed via stop(), or accept() failed - nothing to do
            }
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        clientOut.set(null)
        onConnectionStateChanged?.invoke(false)
    }

    /**
     * Sends one frame of blendshape scores.
     * Wire format: ?FFFF name1_score1|name2_score2|...|
     * (same '?' + 4-digit frame id framing as before, but each "point" is now a
     * blendshape name and its 0..1 score instead of an X_Y_Z landmark.)
     */
    fun sendFrame(blendshapes: List<Pair<String, Float>>) {
        val out = clientOut.get() ?: return

        val sb = StringBuilder()
        sb.append('?')
        sb.append(String.format("%04d", frameCounter % 10000))
        frameCounter++

        for ((name, score) in blendshapes) {
            sb.append(name).append('_')
            sb.append(String.format(java.util.Locale.US, "%.3f", score)).append('|')
        }

        try {
            out.write(sb.toString().toByteArray(Charsets.US_ASCII))
            out.flush()
        } catch (_: Exception) {
            // client disconnected mid-write - drop it, wait for a new connection
            clientOut.set(null)
            onConnectionStateChanged?.invoke(false)
        }
    }
}
