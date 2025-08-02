package com.example.myapplication

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.camera.core.CameraSelector
import com.google.mlkit.vision.face.Face

class FaceGraphicOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val lock = Any()
    private var previewWidth: Int = 0
    private var previewHeight: Int = 0
    private var facing = CameraSelector.LENS_FACING_BACK
    private val faces = ArrayList<Face>()
    private var faceRect: RectF? = null
    private val eyeMovementDetector = EyeMovementDetector(context)

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

    private val eyeMovementPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        isAntiAlias = true
    }

    private val marginPx = 20f * (context.resources.displayMetrics.density)

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
                val face = faces[0]
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
        val targetHeight = height / 3f
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
        canvas.drawRect(rect, facePaint)
    }

    private fun drawFaceContent(canvas: Canvas, face: Face, containerRect: RectF) {
        val faceBox = RectF(face.boundingBox)
        val scaleX = containerRect.width() / faceBox.width()
        val scaleY = containerRect.height() / faceBox.height()

        canvas.save()
        canvas.translate(containerRect.left, containerRect.top)

        // Desenhar contornos faciais
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

        // Desenhar landmarks faciais
        face.allLandmarks.forEach { landmark ->
            val x = (landmark.position.x - faceBox.left) * scaleX
            val y = (landmark.position.y - faceBox.top) * scaleY
            canvas.drawCircle(x, y, 6f, landmarkPaint)
        }

        // Detectar e exibir movimento ocular
        try {
            val eyeMovement = eyeMovementDetector.detectFromLandmarks(face)
            val eyeText = when (eyeMovement) {
                EyeMovementDetector.EyeMovement.LEFT -> "OLHO: ESQUERDA"
                EyeMovementDetector.EyeMovement.RIGHT -> "OLHO: DIREITA"
                EyeMovementDetector.EyeMovement.UP -> "OLHO: CIMA"
                EyeMovementDetector.EyeMovement.DOWN -> "OLHO: BAIXO"
                EyeMovementDetector.EyeMovement.CENTER -> "OLHO: CENTRO"
                EyeMovementDetector.EyeMovement.BLINK -> "OLHO: PISCAR"
                else -> "OLHO: -"
            }

            canvas.drawText(
                eyeText,
                20f,
                60f,
                eyeMovementPaint
            )
        } catch (e: Exception) {
            Log.e("FaceGraphicOverlay", "Error detecting eye movement", e)
        }

        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        eyeMovementDetector.close()
    }
}