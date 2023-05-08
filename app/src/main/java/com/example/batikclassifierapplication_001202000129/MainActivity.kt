package com.example.batikclassifierapplication_001202000129

import android.content.Intent
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Process
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.batikclassifierapplication_001202000129.databinding.ActivityMainBinding
import com.example.batikclassifierapplication_001202000129.ml.BatikModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder


class MainActivity : AppCompatActivity() {

    // Declare variables to reference the ImageView, result TextView, and percentage TextView UI elements
    var imageView: ImageView? = null // ImageView to display the input image for classification
    var result: TextView? = null // TextView to display the result of the classification
    var percentage: TextView? = null // TextView to display the percentage of the classification result

    // Create a binding object to access views from the activity_main layout
    private lateinit var binding: ActivityMainBinding

    // Define the input image size required for EfficientNetB0
    private val imageSize = 224

    override fun onCreate(savedInstanceState: Bundle?) {
        // Called when the activity is starting, setting up the initial state
        super.onCreate(savedInstanceState)

        // Set the activity content from the specified layout resource (activity_main.xml)
        setContentView(R.layout.activity_main)

        // Hide the action bar to provide a full-screen user experience
        supportActionBar!!.hide()

        // Inflate the binding object and get the root view
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root

        // Assign the UI elements to the corresponding variables
        result = binding.result
        imageView = binding.imageView
        percentage = binding.percentage

        // Initialize the buttons for camera, gallery, and live classification
        val camera = binding.cameraBtn
        val gallery = binding.galleryBtn
        val live = binding.liveBtn

        // Set the content view to the root view of the binding object
        setContentView(view)


        // Set an OnClickListener for the camera button
        camera.setOnClickListener {
            // Check if the CAMERA permission is granted
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                // Create an Intent to launch the camera app
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                try {
                    // Start the camera app and wait for the captured image result
                    startActivityForResult(cameraIntent, 3)
                } catch (e: ActivityNotFoundException) {
                    // Display an error message to the user if the camera app is not found
                }
            } else {
                // Request the CAMERA permission if it has not been granted
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            }
        }

        // Set an OnClickListener for the gallery button
        gallery.setOnClickListener {
            // Create an Intent to launch the gallery app
            val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            // Start the gallery app and wait for the selected image result
            startActivityForResult(galleryIntent, 1)
        }

        // Set an OnClickListener for the live classification button
        live.setOnClickListener {
            // Create an Intent to launch the real-time classification activity
            val intent = Intent(this@MainActivity, realTime::class.java)
            // Start the real-time classification activity
            startActivity(intent)
        }

        // Set an OnClickListener for the result TextView
        result!!.setOnClickListener {
            // Create an Intent to launch a web browser with a Google search of the classification result
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=${result!!.text}"))
            // Start the web browser activity
            startActivity(intent)
        }
    }
    // Function to classify an input image using the BatikModel
    fun classifyImage(image: Bitmap?) {
        try {
            // Load the pre-trained BatikModel from the app's resources
            val model = BatikModel.newInstance(applicationContext)

            // Prepare the input tensor with the proper size and data type
            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)

            // Convert the input image into a ByteBuffer
            val byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3)
            byteBuffer.order(ByteOrder.nativeOrder())
            val intValues = IntArray(imageSize * imageSize)
            image!!.getPixels(intValues, 0, image.width, 0, 0, image.width, image.height)
            var pixel = 0
            for (i in 0 until imageSize) {
                for (j in 0 until imageSize) {
                    val `val` = intValues[pixel++] // RGB
                    byteBuffer.putFloat((`val` shr 16 and 0xFF) * (1f / 1))
                    byteBuffer.putFloat((`val` shr 8 and 0xFF) * (1f / 1))
                    byteBuffer.putFloat((`val` and 0xFF) * (1f / 1))
                }
            }

            // Load the ByteBuffer into the input tensor
            inputFeature0.loadBuffer(byteBuffer)

            // Run the model and get the output
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            val confidences = outputFeature0.floatArray

            // Find the class index with the highest confidence value
            var maxPos = 0
            var maxConfidence = 0f
            for (i in confidences.indices) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i]
                    maxPos = i
                }
            }

            // Define the class names
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

            // Set the classification result text
            result!!.text = classes[maxPos]
            println("Confidence: $maxConfidence")
            percentage!!.text = String.format("%.2f", maxConfidence * 100) + "%"

            // Close the model when it is no longer needed
            model.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            // Handle image capture from the camera
            if (requestCode == 3) {
                var image = data!!.extras!!["data"] as Bitmap?

                // Crop the image to be square
                val dimension = Math.min(image!!.width, image.height)
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension)

                // Display the image on the ImageView
                imageView!!.setImageBitmap(image)

                // Resize the image for classification
                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)

                // Classify the image
                classifyImage(image)

                // Handle image selection from the gallery
            } else {
                val dat = data!!.data
                var image: Bitmap? = null

                // Load the selected image
                try {
                    image = MediaStore.Images.Media.getBitmap(this.contentResolver, dat)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                // Display the image on the ImageView
                imageView!!.setImageBitmap(image)

                // Resize the image for classification
                image = Bitmap.createScaledBitmap(image!!, imageSize, imageSize, false)

                // Classify the image
                classifyImage(image)
            }
        }
    }
    override fun onBackPressed() {
        // Create an AlertDialog.Builder instance
        val alertDialogBuilder = AlertDialog.Builder(this)

        // Set AlertDialog properties
        alertDialogBuilder.setTitle("Exit Application?")
        alertDialogBuilder
            .setMessage("Click yes to exit!")
            .setCancelable(false)
            // Set positive button action: close the application
            .setPositiveButton(
                "Yes"
            ) { dialog, id ->
                moveTaskToBack(true)
                Process.killProcess(Process.myPid())
                System.exit(1)
            }
            // Set negative button action: cancel the dialog
            .setNegativeButton("No") { dialog, id -> dialog.cancel() }

        // Create and show the AlertDialog
        val alertDialog = alertDialogBuilder.create()
        alertDialog.show()
    }
}