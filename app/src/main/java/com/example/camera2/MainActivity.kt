package com.example.camera2

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.camera2.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.controls.Hdr
import com.otaliastudios.cameraview.controls.PictureFormat
import com.otaliastudios.cameraview.gesture.Gesture
import com.otaliastudios.cameraview.gesture.GestureAction
import com.otaliastudios.cameraview.markers.AutoFocusMarker
import com.otaliastudios.cameraview.markers.AutoFocusTrigger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var camera: CameraView
    private lateinit var overlayView: OverlayView
    private lateinit var textRes: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        camera = viewBinding.camera
        camera.setLifecycleOwner(this)
        camera.hdr = Hdr.ON
        camera.pictureFormat = PictureFormat.JPEG
        camera.setRequestPermissions(true)
        camera.mapGesture(Gesture.TAP, GestureAction.AUTO_FOCUS)

        overlayView = viewBinding.overlay
        textRes = viewBinding.resultText

        camera.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                result.toBitmap(1024, 768) { bitmap ->
                    // Handle the Bitmap here
                    if (bitmap != null) {
                        overlayView.rect.let { rect ->
                            val scaledRect = scaleRectToBitmap(rect, overlayView, bitmap)
                            val croppedBitmap = cropBitmap(bitmap, scaledRect)
                            val preProcessBitmap = getEnhancedBinaryBitmap(croppedBitmap)
                            saveBitmap(preProcessBitmap, "binarized")
                            recognizeText(preProcessBitmap)
                        }
                    } else {
                        // Handle the error case
                        Log.e("Camera", "Failed to convert PictureResult to Bitmap")
                    }
                }
            }
        })

        viewBinding.imageCaptureButton.setOnClickListener {
            camera.takePicture()
        }

    }

    private fun recognizeText(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                // Process the recognized text
                val txt = StringBuilder()
                for (block in visionText.textBlocks) {
                    val blockText = block.text
                    txt.append(blockText).append("\n")
                    Log.i("MLKit", "Detected text: $blockText")
                }
                textRes.text = txt.toString()
            }
            .addOnFailureListener { e ->
                Log.e("MLKit", "Text recognition failed", e)
            }

    }

    private fun scaleRectToBitmap(rect: Rect, view: View, bitmap: Bitmap): Rect {
        val xRatio = bitmap.width.toFloat() / view.width.toFloat()
        val yRatio = bitmap.height.toFloat() / view.height.toFloat()

        return Rect(
            (rect.left * xRatio).toInt(),
            (rect.top * yRatio).toInt(),
            (rect.right * xRatio).toInt(),
            (rect.bottom * yRatio).toInt()
        )
    }

    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val cropRect = Rect(
            rect.left.coerceAtLeast(0),
            rect.top.coerceAtLeast(0),
            rect.right.coerceAtMost(bitmap.width),
            rect.bottom.coerceAtMost(bitmap.height)
        )

        return Bitmap.createBitmap(
            bitmap,
            cropRect.left,
            cropRect.top,
            cropRect.width(),
            cropRect.height()
        )
    }

    private fun saveBitmap(bitmap: Bitmap, name: String) {
        // Directory in external storage to save images
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                .toString() + "/MyImages"
        )
        if (!directory.exists()) {
            directory.mkdirs()
        }

        // Create a unique filename based on the current time
        val filename = "$name.jpg"
        val file = File(directory, filename)

        // Save the bitmap to the file as a JPEG
        var out: FileOutputStream? = null
        try {
            out = FileOutputStream(file)
            bitmap.compress(
                Bitmap.CompressFormat.JPEG,
                100,
                out
            ) // Compress bitmap as JPEG, with 100% quality
            Log.i("Camera", "Image saved: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e("Camera", "Failed to save image", e)
        } finally {
            try {
                out?.flush()
                out?.close()
            } catch (e: IOException) {
                Log.e("Camera", "Failed to close output stream", e)
            }
        }
    }

    private fun getEnhancedBinaryBitmap(bitmapSrc: Bitmap): Bitmap {
        // Step 1: Convert to Grayscale
        val grayscaleBitmap = convertToGrayscaleUsingCanvas(bitmapSrc)
        saveBitmap(grayscaleBitmap, "grey")
        val contrastEnhancedBitmap = enhanceContrastUsingCanvas(grayscaleBitmap, 10f)
//        val applyMeanFilter = applyMeanFilter(contrastEnhancedBitmap, 10f)
        return grayscaleBitmap
//        saveBitmap(grayscaleBitmap)
        // Step 2: Enhance Contrast
        saveBitmap(contrastEnhancedBitmap, "contrast")


        // Step 3: Binarize the Bitmap
        return contrastEnhancedBitmap
    }

    private fun convertToGrayscaleUsingCanvas(bitmap: Bitmap): Bitmap {
        val grayscaleBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(grayscaleBitmap)

        val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }

        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return grayscaleBitmap
    }

    private fun enhanceContrastUsingCanvas(bitmap: Bitmap, contrast: Float): Bitmap {
        val contrastBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = Canvas(contrastBitmap)

        val contrastMatrix = ColorMatrix().apply {
            set(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, 128f * (1 - contrast), // Red channel
                    0f, contrast, 0f, 0f, 128f * (1 - contrast), // Green channel
                    0f, 0f, contrast, 0f, 128f * (1 - contrast), // Blue channel
                    0f, 0f, 0f, 1f, 0f                         // Alpha channel
                )
            )
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(contrastMatrix) }

        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return contrastBitmap
    }

    fun applyMeanFilter(bitmap: Bitmap, radius: Float): Bitmap {
        // Crear un bitmap mutable para la imagen de salida
        val resultBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)

        // Crear un Canvas para dibujar en el bitmap de salida
        val canvas = Canvas(resultBitmap)
        val paint = Paint()

        // Utilizar RenderScript para aplicar un filtro de desenfoque (mean filter)
        val renderScript = RenderScript.create(this)

        val input = Allocation.createFromBitmap(renderScript, bitmap)
        val output = Allocation.createTyped(renderScript, input.type)

        val script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        script.setRadius(radius)
        script.setInput(input)
        script.forEach(output)

        output.copyTo(resultBitmap)

        renderScript.destroy()

        return resultBitmap
    }

    private fun binarizeUsingCanvas(bitmap: Bitmap): Bitmap {
        val binaryBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(binaryBitmap)

        val threshold = 128f
        val binarizeMatrix = ColorMatrix(
            floatArrayOf(
                255f, 255f, 255f, 0f, -255 * threshold,  // Red channel
                255f, 255f, 255f, 0f, -255 * threshold,  // Green channel
                255f, 255f, 255f, 0f, -255 * threshold,  // Blue channel
                0f, 0f, 0f, 1f, 0f                       // Alpha channel
            )
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(binarizeMatrix) }

        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return binaryBitmap
    }

    override fun onPause() {
        super.onPause()
        camera.close()
    }

    override fun onStop() {
        super.onStop()
        camera.destroy()
    }

    override fun onResume() {
        super.onResume()
        camera.open()
    }

    override fun onDestroy() {
        super.onDestroy()
        camera.destroy()
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                camera.open()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

}