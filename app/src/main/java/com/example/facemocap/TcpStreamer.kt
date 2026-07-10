package com.example.facemocap

import android.annotation.SuppressLint
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/**
 * Minimal TCP server replicating the wire format reverse-engineered from a real capture
 * of the original Face Mocap app:
 *
 *   ?FFFF X1_Y1_Z1|X2_Y2_Z2|...|Xn_Yn_Zn|
 *
 * - '?'  frame start marker
 * - FFFF: 4-digit zero-padded rolling frame counter (0000..9999)
 * - points separated by '|', each point's X_Y_Z separated by '_'
 * - one decimal place, matching the sample capture
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

    /** points: (x, y, z) in whatever units you want on the wire (mm-like, to match the original). */
    @SuppressLint("DefaultLocale")
    fun sendFrame(points: List<Triple<Float, Float, Float>>) {
        val out = clientOut.get() ?: return

        val sb = StringBuilder()
        sb.append('?')
        sb.append(String.format("%04d", frameCounter % 10000))
        frameCounter++

        for ((x, y, z) in points) {
            sb.append(String.format("%.1f", x)).append('_')
            sb.append(String.format("%.1f", y)).append('_')
            sb.append(String.format("%.1f", z)).append('|')
        }

        try {
            out.write(sb.toString().toByteArray(Charsets.US_ASCII))
            out.flush()
        }
        catch (e: Exception) {
            e.message;
            clientOut.set(null)
            onConnectionStateChanged?.invoke(false)
        }
    }
}
