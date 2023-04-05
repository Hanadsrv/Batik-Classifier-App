package com.example.batikclassifierapplication_001202000129

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.example.batikclassifierapplication_001202000129.ml.BatikModel
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutionException

class realTime : AppCompatActivity() {
    var img_preview: PreviewView? = null
    var results: TextView? = null

    var IMG_SIZE = 224
    var model: BatikModel? = null

    private val REQUEST_CODE_PERMISSIONS = 123
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.real_time_detection)
        supportActionBar!!.hide()
        img_preview = findViewById(R.id.viewRealTime)
        results = findViewById(R.id.result)

        try {
            model = BatikModel.newInstance(
                applicationContext
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }

        if (allPermissionGranted()) {
            openCamera()
        } else {
            ActivityCompat.requestPermissions(this,REQUIRED_PERMISSIONS,REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun openCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    bindPreview(cameraProvider)
                }
                catch (e:ExecutionException) {}
                catch (e:InterruptedException) {}
                catch (e:IOException) {
                    e.printStackTrace()
                }
            }, ActivityCompat.getMainExecutor(this)
        )
    }

    fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val builder = ImageCapture.Builder()
        val imageCapture = builder.build()
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(img_preview!!.surfaceProvider)
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

        imageAnalysis.setAnalyzer(ActivityCompat.getMainExecutor(this)) {
            image -> var result: String? = null
            @SuppressLint("UnsafeOptInUsageError") val img = image.image

            try {
                result = classifyImage(img, model)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            results!!.text = result
            image.close()
        }

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture)
    }

    private fun classifyImage(image: Image?, model: BatikModel?): String? {
        var img = toBitmap(image!!)
        img = Bitmap.createScaledBitmap(img, 224, 224, false)

        try {
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            val byteBuffer = ByteBuffer.allocateDirect(4*IMG_SIZE*IMG_SIZE*3)
            byteBuffer.order(ByteOrder.nativeOrder())
            val intValues = IntArray(IMG_SIZE*IMG_SIZE)
            img.getPixels(intValues, 0, img.width, 0, 0, img.width, img.height)
            var pixel = 0

            for (i in 0 until IMG_SIZE) {
                for (j in 0 until IMG_SIZE) {
                    val `val` = intValues[pixel++]
                    byteBuffer.putFloat((`val` shr 16 and 0xff)*(1f/1))
                    byteBuffer.putFloat((`val` shr 8 and 0xff)*(1f/1))
                    byteBuffer.putFloat((`val` and 0xff)*(1f/1))
                }
            }

            inputFeature0.loadBuffer(byteBuffer)

            val outputs = model!!.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            val percentages = outputFeature0.floatArray

            var maxPos = 0
            var maxPercetage = 0f

            for (i in percentages.indices) {
                if (percentages[i] > maxPercetage) {
                    maxPercetage = percentages[i]
                    maxPos = 1
                }
            }

            val classes = arrayOf(
                "Batik Bali",
                "Batik Betawi",
                "Batik Celup",
                "Batik Cendrawasih",
                "Batik Dayak",
                "Batik Geblek Renteng",
                "Batik Insang",
                "Batik Kawung",
                "Batik Lasem",
                "Batik Megamendung",
                "Batik Pala",
                "Batik Parang",
                "Batik Poleng",
                "Batik Sekar Jagad",
                "Batik Tambal"
            )

            Log.d("classify", classes[maxPos])
            return classes[maxPos] + " " + String.format("%.1f", maxPercetage*100) + "%"
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun allPermissionGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                return false
            }
        }
        return true
    }

    fun toBitmap(image: Image): Bitmap {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        //U and V are swapped
        yBuffer[nv21, 0, ySize]
        vBuffer[nv21, ySize, vSize]
        uBuffer[nv21, ySize + vSize, uSize]
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera access denied", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onBackPressed() {
        model!!.close()
        finish()
    }
}
