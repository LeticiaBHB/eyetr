package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.camera.core.ExperimentalGetImage
import android.graphics.Rect
import android.graphics.PointF
import android.graphics.*
import android.util.DisplayMetrics

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setContent {
            MyAppTheme {
                CameraScreen()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

@Composable
fun MyAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val hasCameraPermission = remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission.value = isGranted
    }

    LaunchedEffect(Unit) {
        val permissionCheckResult = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        hasCameraPermission.value = permissionCheckResult == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission.value) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (hasCameraPermission.value) {
            CameraPreview()
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Permissão da câmera não concedida")
            }
        }
    }
}

@Composable
fun CameraPreview() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isBackCamera = remember { mutableStateOf(true) }
    val faces = remember { mutableStateOf<List<Face>>(emptyList()) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

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

    // Configuração do detector de faces
    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Modo mais preciso
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL) // Detectar todos os pontos faciais
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL) // Detectar todos os contornos
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL) // Classificar sorrisos, olhos abertos, etc.
            .build()

        FaceDetection.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(cameraSelector) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build()

        // Configuração da análise de imagem para detecção de faces
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

        Button(
            onClick = { isBackCamera.value = !isBackCamera.value },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Alternar")
        }
    }
}

class FaceGraphicOverlay(context: Context) : View(context) {
    private val lock = Any()
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0
    private var facing = CameraSelector.LENS_FACING_BACK
    private val faces = mutableListOf<Face>()
    private var faceRect: RectF? = null

    // Configurações de desenho
    private val facePaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val contourPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val landmarkPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 5f
        isAntiAlias = true
    }

    private val marginPx = 20f * (context.resources.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT)

    fun clear() {
        synchronized(lock) {
            faces.clear()
            faceRect = null
        }
        postInvalidate()
    }

    fun setFaces(faces: List<Face>) {
        synchronized(lock) {
            this.faces.clear()
            this.faces.addAll(faces)
        }
        postInvalidate()
    }

    fun setCameraInfo(previewWidth: Int, previewHeight: Int, facing: Boolean) {
        synchronized(lock) {
            this.previewWidth = previewWidth
            this.previewHeight = previewHeight
            this.facing = if (facing) CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        synchronized(lock) {
            if (faces.isNotEmpty()) {
                val face = faces[0] // Usa apenas o primeiro rosto detectado
                setupFaceRect(face)
                faceRect?.let { rect ->
                    drawFaceContainer(canvas, rect)
                    drawFaceContent(canvas, face, rect)
                }
            }
        }
    }

    private fun setupFaceRect(face: Face) {
        val boundingBox = RectF(face.boundingBox)
        val targetHeight = height / 3f // Altura do container
        val scale = targetHeight / boundingBox.height()
        val targetWidth = boundingBox.width() * scale

        faceRect = RectF(
            width - targetWidth - marginPx,
            height - targetHeight - marginPx,
            width - marginPx,
            height - marginPx
        )
    }

    private fun drawFaceContainer(canvas: Canvas, rect: RectF) {
        // Desenha o quadrado verde no canto inferior direito
        canvas.drawRect(rect, facePaint)
    }

    private fun drawFaceContent(canvas: Canvas, face: Face, containerRect: RectF) {
        val faceBox = RectF(face.boundingBox)
        val scaleX = containerRect.width() / faceBox.width()
        val scaleY = containerRect.height() / faceBox.height()

        canvas.save()
        // Posiciona no canto inferior direito
        canvas.translate(containerRect.left, containerRect.top)

        // Desenha contornos faciais (vertical)
        face.allContours.forEach { contour ->
            val path = Path().apply {
                contour.points.forEachIndexed { index, point ->
                    val x = (point.x - faceBox.left) * scaleX
                    val y = (point.y - faceBox.top) * scaleY
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            canvas.drawPath(path, contourPaint)
        }

        // Desenha landmarks faciais (vertical)
        face.allLandmarks.forEach { landmark ->
            val x = (landmark.position.x - faceBox.left) * scaleX
            val y = (landmark.position.y - faceBox.top) * scaleY
            canvas.drawCircle(x, y, 6f, landmarkPaint)
        }

        canvas.restore()
    }
}