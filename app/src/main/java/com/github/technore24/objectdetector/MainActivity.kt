package com.github.technore24.objectdetector

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.technore24.objectdetector.databinding.ActivityMainBinding
import com.github.technore24.objectdetector.utils.ObjectDetector
import com.github.technore24.objectdetector.utils.ObjectDetectorInterface.Recognition
import com.github.technore24.objectdetector.views.PointImage.PointClickListener
import java.io.IOException
import java.util.Objects
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private var binding: ActivityMainBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(
            layoutInflater
        )
        setContentView(binding!!.root)

        ViewCompat.setOnApplyWindowInsetsListener(
            findViewById(R.id.main)
        ) { v: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding!!.selectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.setType("image/*")
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            startActivityForResult(
                Intent.createChooser(intent, "Open an image"),
                PICK_IMAGE_REQUEST
            )
        }
    }

    private fun loadImage(image: Bitmap) {
        var image = image
        image = resizeBitmap(image, 600)

        val image640 = Bitmap.createScaledBitmap(image, 640, 640, false)

        val results = processYOLO(image640)

        binding!!.image.reset()
        binding!!.image.setImage(image)
        binding!!.objects.text = ""
        binding!!.selectedObject.text = ""

        if (Objects.requireNonNull(results)!!.isEmpty()) {
            Toast.makeText(
                this,
                "Couldn't detect any objects. Please try another image",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        binding!!.image.setPointClickListener(object : PointClickListener {
            override fun onPointClick(id: Int, title: String?) {
                binding!!.selectedObject.text =
                    "Selected Object: " + title + " (" + results!![id].confidence + ")"
            }
        })

        val stringBuilder = StringBuilder("Objects: ")
        for (i in results!!.indices) {
            val res = results[i]
            binding!!.image.addPoint(
                i,
                res.title,
                scaleRectF(image640, image.width, image.height, res.location)
            )
            stringBuilder.append(res.title).append(" (").append(res.confidence).append(") ")
        }
        binding!!.objects.text = stringBuilder.toString()
    }

    private fun processYOLO(image: Bitmap): List<Recognition>? {
        try {
            val detector = ObjectDetector.create(assets, model_name, model_labels, false, 640)
            return detector.recognizeImage(image)
        } catch (e: IOException) {
            return null
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Calculate the scaling factor to maintain aspect ratio
        val scalingFactor = min(
            (maxSize.toFloat() / width).toDouble(),
            (maxSize.toFloat() / height).toDouble()
        ).toFloat()

        // Calculate the new dimensions
        val newWidth = Math.round(width * scalingFactor)
        val newHeight = Math.round(height * scalingFactor)

        // Resize the bitmap
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun scaleRectF(
        bitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        rect: RectF
    ): RectF {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val scaleX = targetWidth.toFloat() / originalWidth
        val scaleY = targetHeight.toFloat() / originalHeight

        return RectF(
            rect.left * scaleX,
            rect.top * scaleY,
            rect.right * scaleX,
            rect.bottom * scaleY
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data

            try {
                val image = MediaStore.Images.Media.getBitmap(this.contentResolver, imageUri)
                loadImage(image)
            } catch (e: IOException) {
                Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val PICK_IMAGE_REQUEST = 1

        private const val model_name = "model.tflite"
        private val model_labels = arrayOf("tshirt", "shirt", "trouser", "short")
    }
}