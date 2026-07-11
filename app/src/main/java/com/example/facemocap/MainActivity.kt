package com.example.facemocap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var statusText: TextView
    private lateinit var connectionIndicator: android.view.View

    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var tcpStreamer: TcpStreamer

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else statusText.text = "Camera permission denied"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        statusText = findViewById(R.id.statusText)
        connectionIndicator = findViewById(R.id.connectionIndicator)

        val versionText: TextView = findViewById(R.id.versionText)
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        versionText.text = "v${versionName ?: "?"}"

        val aboutLink: TextView = findViewById(R.id.aboutLink)
        aboutLink.paintFlags = aboutLink.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        aboutLink.setOnClickListener {
            val url = "https://github.com/w0wca7a/FaceMocapAndroid/blob/master/README.md"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusText.setPadding(statusText.paddingLeft, bars.top, statusText.paddingRight, statusText.paddingBottom)
            (connectionIndicator.layoutParams as android.widget.FrameLayout.LayoutParams).topMargin = bars.top + 12.dpToPx()
            connectionIndicator.requestLayout()

            (versionText.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin = bars.bottom + 12.dpToPx()
            versionText.requestLayout()
            (aboutLink.layoutParams as android.widget.FrameLayout.LayoutParams).bottomMargin = bars.bottom + 12.dpToPx()
            aboutLink.requestLayout()

            insets
        }

        setConnectionIndicator(connected = false)

        tcpStreamer = TcpStreamer(PORT)
        tcpStreamer.onConnectionStateChanged = { connected ->
            runOnUiThread { setConnectionIndicator(connected) }
        }
        tcpStreamer.start()

        faceLandmarkerHelper = FaceLandmarkerHelper(this) { result, imgW, imgH ->
            onFaceResult(result, imgW, imgH)
        }

        updateStatusText()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setConnectionIndicator(connected: Boolean) {
        val color = if (connected) android.graphics.Color.parseColor("#4CAF50") // зелёный
        else android.graphics.Color.parseColor("#F44336")           // красный
        connectionIndicator.background.setTint(color)
    }

    private fun updateStatusText() {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        statusText.text = "TCP $ip:$PORT"
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        faceLandmarkerHelper.detectAsync(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (e: Exception) {
                statusText.text = "Camera bind failed: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onFaceResult(result: FaceLandmarkerResult, imgW: Int, imgH: Int) {
        if (result.faceLandmarks().isEmpty()) {
            runOnUiThread {
                overlayView.setResults(emptyList(), imgW, imgH, mirrorHorizontally = true)
            }
            return
        }

        val landmarks = result.faceLandmarks()[0]

        // Overlay: MediaPipe's normalized image-space coordinates, used as-is for drawing.
        val normalizedPoints = landmarks.map { it.x() to it.y() }
        runOnUiThread {
            overlayView.setResults(normalizedPoints, imgW, imgH, mirrorHorizontally = true)
        }

        // Network: real MediaPipe blendshape scores (0..1), no geometric guesswork needed.
        val blendshapeList = result.faceBlendshapes().orElse(null)?.getOrNull(0)
        if (blendshapeList != null) {
            val blendshapes = blendshapeList.map { category ->
                category.categoryName() to category.score()
            }
            tcpStreamer.sendFrame(blendshapes)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tcpStreamer.stop()
        faceLandmarkerHelper.close()
        cameraExecutor.shutdown()
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val PORT = 9996
    }
}
