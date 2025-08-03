package com.example.myapplication

import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors

@Composable
fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isBackCamera = remember { mutableStateOf(true) }
    val faces = remember { mutableStateOf<List<Face>>(emptyList()) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val eyePointerPosition = remember { mutableStateOf(Offset(500f, 800f)) }
    val eyeDetector = remember { EyeMovementDetector(context) }

    val cameraSelector = if (isBackCamera.value)
        CameraSelector.DEFAULT_BACK_CAMERA
    else
        CameraSelector.DEFAULT_FRONT_CAMERA

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val graphicOverlay = remember {
        FaceGraphicOverlay(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }

    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        FaceDetection.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            graphicOverlay.clear()
            eyeDetector.close()
        }
    }

    LaunchedEffect(cameraSelector) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analyzer ->
                analyzer.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    ImageAnalysis.Analyzer { imageProxy ->
                        @androidx.camera.core.ExperimentalGetImage
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            faceDetector.process(image)
                                .addOnSuccessListener { detectedFaces ->
                                    faces.value = detectedFaces
                                    graphicOverlay.setFaces(detectedFaces)

                                    if (detectedFaces.isNotEmpty()) {
                                        val movement =
                                            eyeDetector.detectFromLandmarks(detectedFaces[0])
                                        eyePointerPosition.value = when (movement) {
                                            EyeMovementDetector.EyeMovement.LEFT -> Offset(200f, eyePointerPosition.value.y)
                                            EyeMovementDetector.EyeMovement.RIGHT -> Offset(800f, eyePointerPosition.value.y)
                                            EyeMovementDetector.EyeMovement.UP -> Offset(eyePointerPosition.value.x, 300f)
                                            EyeMovementDetector.EyeMovement.DOWN -> Offset(eyePointerPosition.value.x, 1300f)
                                            EyeMovementDetector.EyeMovement.CENTER -> Offset(500f, 800f)
                                            else -> eyePointerPosition.value
                                        }
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("CameraPreview", "Face detection error", e)
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }
                    }
                )
            }

        preview.setSurfaceProvider(previewView.surfaceProvider)

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            previewView.post {
                graphicOverlay.setCameraInfo(
                    previewView.width,
                    previewView.height,
                    isBackCamera.value
                )
            }
        } catch (e: Exception) {
            Log.e("CameraPreview", "Error binding camera", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                FrameLayout(ctx).apply {
                    addView(previewView)
                    addView(graphicOverlay)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        EyePointerOverlay(
            position = eyePointerPosition.value,
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = { isBackCamera.value = !isBackCamera.value },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Alternar Câmera")
        }
    }
}
