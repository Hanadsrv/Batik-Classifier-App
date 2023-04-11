package com.example.batikclassifierapplication_001202000129

import android.content.Intent
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.ThumbnailUtils
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
import com.example.batikclassifierapplication_001202000129.ml.BatikModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    var imageView: ImageView? = null
    //var logout_btn: ImageView? = null
    var result: TextView? = null
    var percentage: TextView? = null

    var REQUEST_CODE = 123
    private val imageSize = 224

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar!!.hide()

        imageView = findViewById(R.id.imageView)
        //logout_btn = findViewById(R.id.logout_btn)
        result = findViewById(R.id.result)
        percentage = findViewById(R.id.percentage)

        val camera = findViewById<LinearLayout>(R.id.camera_btn)
        val gallery = findViewById<LinearLayout>(R.id.gallery_btn)
        val live = findViewById<LinearLayout>(R.id.live_btn)
        val logout_btn = findViewById<LinearLayout>(R.id.logout_btn)


        camera.setOnClickListener(View.OnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                try {
                    startActivityForResult(cameraIntent, 3)
                } catch (e: ActivityNotFoundException) {
                    // display error state to the user
                }
                //onActivityResult(3, RESULT_OK, cameraIntent)
            } else {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
            }
        })

        gallery.setOnClickListener{
            val cameraIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(cameraIntent, 1)
        }

        live.setOnClickListener{
            val intent = Intent(this@MainActivity, realTime::class.java)
            startActivity(intent)
        }
        logout_btn.setOnClickListener(View.OnClickListener {
            val alertDialogBuilder = AlertDialog.Builder(this@MainActivity)
            alertDialogBuilder.setTitle("Exit Application?")
            alertDialogBuilder
                .setMessage("Click yes to exit!")
                .setCancelable(false)
                .setPositiveButton(
                    "Yes"
                ) { dialog, id ->
                    moveTaskToBack(true)
                    Process.killProcess(Process.myPid())
                    System.exit(1)
                }
                .setNegativeButton("No") { dialog, id -> dialog.cancel() }
            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
        })

        fun onBackPressed() {
            val alertDialogBuilder = AlertDialog.Builder(this)
            alertDialogBuilder.setTitle("Exit Application?")
            alertDialogBuilder
                .setMessage("Click yes to exit!")
                .setCancelable(false)
                .setPositiveButton(
                    "Yes"
                ) { dialog, id ->
                    moveTaskToBack(true)
                    Process.killProcess(Process.myPid())
                    System.exit(1)
                }
                .setNegativeButton("No") { dialog, id -> dialog.cancel() }
            val alertDialog = alertDialogBuilder.create()
            alertDialog.show()
        }
    }
    fun classifyImage(image: Bitmap?) {
        try {
            val model = BatikModel.newInstance(applicationContext)
            val inputFeature0 =
                TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
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

            inputFeature0.loadBuffer(byteBuffer)

            // Menjalankan model dan mendapatkan hasil
            val outputs = model.process(inputFeature0)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer
            val confidences = outputFeature0.floatArray
            // Mencari indeks kelas dengan nilai confidence terbesar
            var maxPos = 0
            var maxConfidence = 0f
            for (i in confidences.indices) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i]
                    maxPos = i
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
            result!!.text = classes[maxPos]
            println("Confidence: $maxConfidence")
            percentage!!.text = String.format("%.2f", maxConfidence)

            // Close model jika tidak lagi digunakan
            model.close()
        } catch (e: IOException) {

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            // Pengambilan gambar untuk fitur kamera
            if (requestCode == 3) {
                var image = data!!.extras!!["data"] as Bitmap?
                val dimension = Math.min(image!!.width, image.height)
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension)
                imageView!!.setImageBitmap(image)
                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false)
                classifyImage(image)

                // Pengambilan gambar untuk fitur galeri
            } else {
                val dat = data!!.data
                var image: Bitmap? = null
                try {
                    image = MediaStore.Images.Media.getBitmap(this.contentResolver, dat)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                imageView!!.setImageBitmap(image)
                image = Bitmap.createScaledBitmap(image!!, imageSize, imageSize, false)
                classifyImage(image)
            }
        }

    }
}