package com.programmazionemobile.progettoLucaBenzi
import kotlin.collections.ArrayList
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks.call
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class ClassifyActivity(private val context: Context) {
    private var _labelsArrayList = ArrayList<String>()
    private val executorService: ExecutorService = Executors.newCachedThreadPool()
    private var inputWidthImage = 0
    private var inputHeightImage = 0
    private var modelInputSize = 0
    private var gpuDelegate: GpuDelegate? = null
    private var interpreter: Interpreter? = null
    private var isInitialized = false

    @Throws(IOException::class)
    private fun initializeInterpreter() {
        val assetManager = context.assets
        val modelClassify = loadModel(assetManager, "mobilenet_v1_1.0_224.tflite")
        this._labelsArrayList = loadLines(context, "labels.txt")
        val interpreterOptions = Interpreter.Options()
        this.gpuDelegate = GpuDelegate()
        interpreterOptions.addDelegate(gpuDelegate)
        val interpreter = Interpreter(modelClassify, interpreterOptions)
        val inputShape = interpreter.getInputTensor(0).shape()
        this.inputWidthImage = inputShape[1]
        this.inputHeightImage = inputShape[2]
        this.modelInputSize = FLOAT_TYPE_SIZE * this.inputWidthImage * this.inputHeightImage * CHANNEL_SIZE
        this.interpreter = interpreter
        this.isInitialized = true
    }

    @Throws(IOException::class)
    private fun loadModel(assetManager: AssetManager, filename: String): ByteBuffer {
        val fileDescriptor = assetManager.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    @Throws(IOException::class)
    fun loadLines(context: Context, filename: String): ArrayList<String> {
        val s = Scanner(InputStreamReader(context.assets.open(filename)))
        val labelsString = ArrayList<String>()
        while (s.hasNextLine()) {
            labelsString.add(s.nextLine())
        }
        s.close()
        return labelsString
    }
    fun initialize(): Task<Void> {
        return call(
            executorService,
            Callable<Void> {
                initializeInterpreter()
                null
            }
        )
    }

    private fun getMaxResult(result: FloatArray): Int {
        var probabilityFloat = result[0]
        var index = 0
        for (i in result.indices) {
            if (probabilityFloat < result[i]) {
                probabilityFloat = result[i]
                index = i
            }
        }
        return index
    }
    private fun classify(bitmap: Bitmap): String {
        check(this.isInitialized) { "L'interprete non è ancora inizializzato" }
        val resizedImage =
            Bitmap.createScaledBitmap(bitmap, inputWidthImage, inputHeightImage, true)
        val byteBuffer = conversioneBitmapToByteBuffer(resizedImage)
        val output = Array(1) { FloatArray(_labelsArrayList.size) }
        interpreter?.run(byteBuffer, output)
        val index = getMaxResult(output[0])
        return "La predizione è: ${_labelsArrayList[index]}"
    }

    fun classifyAsync(bitmap: Bitmap): Task<String> {
        return call(executorService, Callable { classify(bitmap) })
    }

    fun close() {
        call(
            executorService,
            Callable<String> {
                interpreter?.close()
                if (gpuDelegate != null) {
                    gpuDelegate!!.close()
                    gpuDelegate = null
                }

                Log.d(TAG, "Chiusura interprete.")
                null
            }
        )
    }

    private fun conversioneBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(modelInputSize)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputWidthImage * inputHeightImage)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until inputWidthImage) {
            for (j in 0 until inputHeightImage) {
                val pixelVal = pixels[pixel++]

                byteBuffer.putFloat(((pixelVal shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((pixelVal shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((pixelVal and 0xFF) - IMAGE_MEAN) / IMAGE_STD)

            }
        }
        bitmap.recycle()

        return byteBuffer
    }

    companion object {
        private const val TAG = "Classificatore"
        private const val FLOAT_TYPE_SIZE = 4
        private const val CHANNEL_SIZE = 3
        private const val IMAGE_MEAN = 127.5f
        private const val IMAGE_STD = 127.5f
    }
}