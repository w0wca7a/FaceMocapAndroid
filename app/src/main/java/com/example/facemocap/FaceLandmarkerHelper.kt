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
            .setResultListener { result, input ->
                listener(result, input.width, input.height)
            }
            .setErrorListener { e -> Log.e("FaceLandmarkerHelper", "MediaPipe error", e) }
            .build()

        faceLandmarker = FaceLandmarker.createFromOptions(context, options)
    }

    /** Call from the ImageAnalysis analyzer. Closes imageProxy internally - don't close it yourself. */
    fun detectAsync(imageProxy: ImageProxy) {
        try {
            // Requires ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888 to be set, see MainActivity.
            val bitmap = imageProxy.toBitmap()
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = System.currentTimeMillis()
            faceLandmarker.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e("FaceLandmarkerHelper", "detectAsync failed", e)
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        faceLandmarker.close()
    }
}
