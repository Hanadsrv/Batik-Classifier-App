package com.example.batikclassifierapplication_001202000129

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    var imageView: ImageView? = null
    var logout_btn: ImageView? = null
    var result: TextView? = null
    var percentage: TextView? = null
    private val imageSize = 224

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}