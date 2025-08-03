package com.example.myapplication

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceLandmark
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class EyeMovementDetector(context: Context) {
    private var interpreter: Interpreter? = null

    sealed class EyeMovement {
        object LEFT : EyeMovement()
        object RIGHT : EyeMovement()
        object UP : EyeMovement()
        object DOWN : EyeMovement()
        object CENTER : EyeMovement()
        object BLINK : EyeMovement()
        object UNKNOWN : EyeMovement()
    }

    init {
        try {
            interpreter = Interpreter(loadModelFile(context))
            Log.d("EyeMovementDetector", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("EyeMovementDetector", "Error loading model", e)
        }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val assetManager = context.assets
        val fileDescriptor = assetManager.openFd("eye_model.tflite")
        return FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
            val fileChannel = inputStream.channel
            fileChannel.map(
                FileChannel.MapMode.READ_ONLY,
                fileDescriptor.startOffset,
                fileDescriptor.length
            )
        }
    }

    fun detectFromLandmarks(face: Face): EyeMovement {
        if (interpreter == null) return EyeMovement.UNKNOWN

        return try {
            val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position
            val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position
            val nose = face.getLandmark(FaceLandmark.NOSE_BASE)?.position

            if (leftEye == null || rightEye == null || nose == null) {
                return EyeMovement.UNKNOWN
            }

            // Cálculo simplificado com base nas posições médias
            val avgEyeX = (leftEye.x + rightEye.x) / 2
            val avgEyeY = (leftEye.y + rightEye.y) / 2

            val deltaX = avgEyeX - nose.x
            val deltaY = avgEyeY - nose.y

            return when {
                deltaX < -20 -> EyeMovement.LEFT
                deltaX > 20 -> EyeMovement.RIGHT
                deltaY < -20 -> EyeMovement.UP
                deltaY > 20 -> EyeMovement.DOWN
                else -> EyeMovement.CENTER
            }
        } catch (e: Exception) {
            Log.e("EyeMovementDetector", "Error detecting eye movement", e)
            EyeMovement.UNKNOWN
        }
    }

    fun close() {
        interpreter?.close()
    }
}
