@file:Suppress("DEPRECATION")

package com.programmazionemobile.progettoLucaBenzi

import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*


class MainActivity : AppCompatActivity() {

    private var lensFacing = CameraX.LensFacing.BACK
    private val tag = "MainActivity"
    private var isProcessing: Boolean = false
    private var PERMISSION_REQUEST_CODE = 101
    var REQUIRED_PERMISSIONS = arrayOf("android.permission.CAMERA")
    private var classifyActivity: ClassifyActivity = ClassifyActivity(this@MainActivity)
    var dbSq: DBHelper= DBHelper(this);


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (allPermissionsGranted()) {
            textureView.post { startCamera() }
            textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                transformationsMethod()
            }
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS,
                this.PERMISSION_REQUEST_CODE
            )
        }

        classifyActivity
            .initialize()
            .addOnSuccessListener { }
            .addOnFailureListener { e -> Log.e(tag, "Errore nel settaggio del classificatore.", e) }

    }


    private fun startCamera() {
        val displayMetrics = DisplayMetrics().also { textureView.display.getRealMetrics(it) }
        val screenAspectRatio = Rational(1, 1)
        val screenSize = Size(displayMetrics.widthPixels, displayMetrics.heightPixels)
        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(lensFacing)
            setTargetResolution(screenSize)
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(windowManager.defaultDisplay.rotation)
            setTargetRotation(textureView.display.rotation)
        }.build()

        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            textureView.surfaceTexture = it.surfaceTexture
            transformationsMethod()
        }
        val analyzerConfiguration = ImageAnalysisConfig.Builder().apply {
            val analyzerThread = HandlerThread("AnalysisThread").apply {
                start()
            }
            setCallbackHandler(Handler(analyzerThread.looper))
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
        }.build()
        val analyzer = ImageAnalysis(analyzerConfiguration)
        analyzer.analyzer =
            ImageAnalysis.Analyzer { imageProxy: ImageProxy, _: Int ->
                if (!isProcessing) {
                    isProcessing = true
                    val bitmap = imageProxy.toBitmap()
                    this.classifyActivity
                        .classifyAsync(bitmap)
                        .addOnSuccessListener { result ->
                            predictedTextView?.text = result
                            isProcessing = false
                        }
                        .addOnFailureListener {
                            isProcessing = false
                        }
                }
            }
        val captureConfig = ImageCaptureConfig.Builder()
            .setTargetAspectRatio(screenAspectRatio)
            .setTargetRotation(windowManager.defaultDisplay.rotation)
            .setTargetResolution(screenSize)
            .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
            .build()

        val capture = ImageCapture(captureConfig)
        btnDetectObject.setOnClickListener {

            val fileName = System.currentTimeMillis().toString()
            val fileFormat = ".jpg"
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            val imageFile = createTempFile(fileName, fileFormat)
            val date = Date()
            val output = File(dir, "CheckImage $date.jpeg")
            // Store captured image in the temporary file
            capture.takePicture(imageFile, object : ImageCapture.OnImageSavedListener {
                override fun onImageSaved(file: File) {
                    Log.d("funziona: ",dbSq.insertData(output!!.absolutePath).toString().plus(" "));
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    Log.d("CameraXApp", msg)

                }

                override fun onError(useCaseError: ImageCapture.UseCaseError, message: String, cause: Throwable?) {
                    val msg = "Photo capture failed: $message"
                    Log.e("CameraXApp", msg, cause)
                }
            })
        }
        CameraX.bindToLifecycle(this, preview, analyzer,capture)
    }
    private fun ImageProxy.toBitmap(): Bitmap {
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val arrayOutputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, arrayOutputStream)
        val imageBytes = arrayOutputStream.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun transformationsMethod() {
        val matrix = Matrix()
        val centerX = textureView.width / 2f
        val centerY = textureView.height / 2f
        val rotationDegrees = when (textureView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return

        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
        textureView.setTransform(matrix)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Bisogna dare il permesso all'app di utilizzare la videocamera.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {

        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    override fun onDestroy() {
        classifyActivity.close()
        super.onDestroy()
    }

}