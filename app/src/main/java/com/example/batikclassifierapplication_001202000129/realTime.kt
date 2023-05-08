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
    // Declare UI elements for displaying the camera preview and results
    var img_preview: PreviewView? = null
    var results: TextView? = null

    // Define the image size for the input to the classification model
    var IMG_SIZE = 224

    // Declare a variable to hold the BatikModel object for image classification
    var model: BatikModel? = null

    // Define constants for permission request code and required permissions
    private val REQUEST_CODE_PERMISSIONS = 123
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    // onCreate method is called when the activity is created
    override fun onCreate(savedInstanceState: Bundle?) {
        // Call the superclass onCreate method to complete the creation of the activity
        super.onCreate(savedInstanceState)
        // Set the content view to the real_time_detection layout
        setContentView(R.layout.real_time_detection)
        // Hide the action bar at the top of the screen
        supportActionBar!!.hide()
        // Initialize UI elements
        img_preview = findViewById(R.id.viewRealTime)
        results = findViewById(R.id.result)

        // Try to create a new instance of the BatikModel
        try {
            model = BatikModel.newInstance(applicationContext)
        } catch (e: IOException) {
            // Print the stack trace if an exception occurs
            e.printStackTrace()
        }

        // Check if all required permissions are granted
        if (allPermissionGranted()) {
            // If permissions are granted, open the camera
            openCamera()
        } else {
            // If not, request the necessary permissions
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    // Function to open the camera and set up camera provider
    private fun openCamera() {
        // Get an instance of the ProcessCameraProvider
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                try {
                    // Get the camera provider and bind the preview
                    val cameraProvider = cameraProviderFuture.get()
                    bindPreview(cameraProvider)
                } catch (e: ExecutionException) {
                    // Handle ExecutionException
                } catch (e: InterruptedException) {
                    // Handle InterruptedException
                } catch (e: IOException) {
                    // Print stack trace if an exception occurs
                    e.printStackTrace()
                }
            }, ActivityCompat.getMainExecutor(this)
        )
    }

    // Function to bind the camera preview to the PreviewView
    fun bindPreview(cameraProvider: ProcessCameraProvider) {
        // Create an ImageCapture instance with the builder
        val builder = ImageCapture.Builder()
        val imageCapture = builder.build()
        // Create a Preview instance with the builder
        val preview = Preview.Builder().build()
        // Set the surface provider for the preview
        preview.setSurfaceProvider(img_preview!!.surfaceProvider)
        // Create a camera selector for the back camera
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
        // Create an ImageAnalysis instance and set the backpressure strategy
        val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

        // Set the analyzer for image analysis
        imageAnalysis.setAnalyzer(ActivityCompat.getMainExecutor(this)) {
                image -> var result: String? = null
            // Get the image from the ImageProxy
            @SuppressLint("UnsafeOptInUsageError") val img = image.image

            // Try to classify the image using the model
            try {
                result = classifyImage(img, model)
            } catch (e: IOException) {
                // Print stack trace if an exception occurs
                e.printStackTrace()
            }

            // Update the results TextView with the classification result
            results!!.text = result
            // Close the ImageProxy
            image.close()
        }

        // Bind the camera provider to the lifecycle with the camera selector, preview, and image analysis
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis, imageCapture)
    }

    // Function to classify the input image using the trained model
    private fun classifyImage(image: Image?, model: BatikModel?): String? {
        // Convert the input image to a bitmap
        var img = toBitmap(image!!)
        // Resize the bitmap to the required input size for the model (224x224)
        img = Bitmap.createScaledBitmap(img, 224, 224, false)

        try {
            // Create a TensorBuffer for the input image
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            // Allocate a ByteBuffer to store the image data
            val byteBuffer = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3)
            byteBuffer.order(ByteOrder.nativeOrder())
            val intValues = IntArray(IMG_SIZE * IMG_SIZE)
            img.getPixels(intValues, 0, img.width, 0, 0, img.width, img.height)
            var pixel = 0

            // Convert the pixel values to a ByteBuffer
            for (i in 0 until IMG_SIZE) {
                for (j in 0 until IMG_SIZE) {
                    val `val` = intValues[pixel++]
                    byteBuffer.putFloat((`val` shr 16 and 0xff) * (1f / 1))
                    byteBuffer.putFloat((`val` shr 8 and 0xff) * (1f / 1))
                    byteBuffer.putFloat((`val` and 0xff) * (1f / 1))
                }
            }

            // Load the ByteBuffer into the input TensorBuffer
            inputFeature0.loadBuffer(byteBuffer)

            // Process the input TensorBuffer using the model and obtain the output
            val outputs = model!!.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            val percentages = outputFeature0.floatArray

            // Find the index with the highest percentage
            var maxPos = 0
            var maxPercetage = 0f
            for (i in percentages.indices) {
                if (percentages[i] > maxPercetage) {
                    maxPercetage = percentages[i]
                    maxPos = i
                }
            }

            // Define the array of class names
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

            // Log the classification result
            Log.d("classify", classes[maxPos])
            // Return the class name and percentage
            return classes[maxPos] + " " + String.format("%.1f", maxPercetage * 100) + "%"
        } catch (e: Exception) {
            // Print stack trace if an exception occurs
            e.printStackTrace()
        }
        return null
    }

    // Function to check if all required permissions are granted
    private fun allPermissionGranted(): Boolean {
        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    // Function to convert the input Image to a Bitmap
    fun toBitmap(image: Image): Bitmap {
        val planes = image.planes
        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer
        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)

        // Copy Y, U, and V buffers into the NV21 byte array
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        // Create a YuvImage object from the NV21 byte array
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()

        // Compress the YuvImage to a JPEG format
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()

        // Decode the compressed image bytes into a Bitmap object
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    // Function to handle the back button press
    override fun onBackPressed() {
        // Close the model and finish the activity
        model!!.close()
        finish()
    }
}
