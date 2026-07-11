package com.example.facemocap

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wraps MediaPipe's FaceLandmarker task (468 face landmarks, same canonical topology
 * MediaPipe/ARCore both derive from) running in LIVE_STREAM mode against the camera feed.
 *
 * Requires face_landmarker.task to be present in app/src/main/assets - see README.md.
 */
class FaceLandmarkerHelper(
    context: Context,
    private val listener: (FaceLandmarkerResult, imageWidth: Int, imageHeight: Int) -> Unit
) {
    private var faceLandmarker: FaceLandmarker

    // Guards against calling detectAsync() faster than results come back. Without this,
    // MediaPipe's internal LIVE_STREAM queue backs up over time (frames keep getting
    // submitted while previous ones are still processing), causing the overlay to drift
    // further and further behind actual face movement - worse when something on the
    // result-listener thread is slower (e.g. a connected TCP client), and especially
    // bad after the app has been backgrounded and frames piled up.
    private val isProcessing = AtomicBoolean(false)

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .setDelegate(Delegate.GPU)
            .build()

        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setOutputFacialTransformationMatrixes(true)
            .setOutputFaceBlendshapes(true)
            .setResultListener { result, input ->
                isProcessing.set(false)
                listener(result, input.width, input.height)
            }
            .setErrorListener { e ->
                isProcessing.set(false)
                Log.e("FaceLandmarkerHelper", "MediaPipe error", e)
            }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    /** Call from the ImageAnalysis analyzer. Closes imageProxy internally - don't close it yourself. */
    fun detectAsync(imageProxy: ImageProxy) {
        if (!isProcessing.compareAndSet(false, true)) {
            // Previous frame still being processed - drop this one instead of queuing it.
            imageProxy.close()
            return
        }

        try {
            // Requires ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888 to be set, see MainActivity.
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = System.currentTimeMillis()
            faceLandmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e("FaceLandmarkerHelper", "detectAsync failed", e)
            isProcessing.set(false)
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        faceLandmarker.close()
    }
}