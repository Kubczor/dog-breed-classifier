package com.example.dogsapp

import android.Manifest
import android.app.Dialog
import android.content.ContentValues
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.dogsapp.databinding.ActivityMainBinding
import com.example.dogsapp.databinding.ItemGalleryImageBinding
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.widget.ProgressBar
import android.graphics.ImageDecoder


class MainActivity : AppCompatActivity() {

    data class DogPhoto(val uri: String, val breed: String? = null)

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val capturedDogs = mutableListOf<DogPhoto>()
    private lateinit var sharedPref: SharedPreferences

    class GalleryAdapter(
        private val dogPhotos: MutableList<DogPhoto>,
        private val onItemClick: (Uri) -> Unit,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.Adapter<GalleryAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(private val binding: ItemGalleryImageBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(dogPhoto: DogPhoto, position: Int) {
                val uri = Uri.parse(dogPhoto.uri)

                Glide.with(binding.root.context)
                    .load(uri)
                    .centerCrop()
                    .into(binding.imageViewThumbnail)

                binding.imageViewCheckmark.visibility =
                    if (dogPhoto.breed != null) View.VISIBLE else View.GONE

                binding.root.setOnClickListener {
                    onItemClick(uri)
                }

                binding.buttonDelete.setOnClickListener {
                    onDeleteClick(position)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val binding = ItemGalleryImageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ImageViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(dogPhotos[position], position)
        }

        override fun getItemCount(): Int = dogPhotos.size
    }


    companion object {
        private const val PREFS_NAME = "CameraAppPrefs"
        private const val IMAGES_KEY = "saved_images_uris"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = mutableListOf<String>().apply {
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            add(Manifest.permission.INTERNET)
        }
    }

    private val permissionResultLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) startCamera() else Toast.makeText(
            this,
            "Permission request denied",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedPref = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        loadImagesList()
        binding.imageCapturedMedia.setOnClickListener { showImageGallery() }

        if (allPermissionsGranted()) startCamera() else requestPermissions()

        cameraExecutor = Executors.newSingleThreadExecutor()
        binding.buttonCapture.setOnClickListener { captureImage() }

        binding.buttonPickGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
    }

    private fun loadImagesList() {
        val json = sharedPref.getString(IMAGES_KEY, null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<DogPhoto>>() {}.type
                val loadedDogs = Gson().fromJson<List<DogPhoto>>(json, type)

                // Filter out any URIs that are no longer accessible
                val validDogs = loadedDogs.filter { dog ->
                    try {
                        val uri = Uri.parse(dog.uri)
                        contentResolver.openInputStream(uri)?.close()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }

                capturedDogs.clear()
                capturedDogs.addAll(validDogs)
                saveImagesList() // Update the saved list if any were removed

                if (capturedDogs.isNotEmpty()) {
                    val uri = Uri.parse(capturedDogs.last().uri)
                    try {
                        val source = ImageDecoder.createSource(contentResolver, uri)
                        val bitmap = ImageDecoder.decodeBitmap(source)
                        binding.imageCapturedMedia.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        binding.imageCapturedMedia.setImageResource(R.drawable.ic_default_image)
                    }
                } else {
                    binding.imageCapturedMedia.setImageResource(R.drawable.ic_default_image)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Błąd przy wczytywaniu danych: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveImagesList() {
        val json = Gson().toJson(capturedDogs)
        sharedPref.edit().putString(IMAGES_KEY, json).apply()
    }

    private fun showImageGallery() {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_gallery)

        val recycler = dialog.findViewById<RecyclerView>(R.id.recyclerGallery)
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.adapter = GalleryAdapter(capturedDogs,
            { uri ->
                dialog.dismiss()
                showFullScreenImage(uri)
            },
            { position ->
                deleteImage(position)
                recycler.adapter?.notifyItemRemoved(position)
            }
        )

        dialog.show()
    }

    private fun deleteImage(position: Int) {
        if (position in 0 until capturedDogs.size) {
            try {
                // Try to delete the file from storage
                val uri = Uri.parse(capturedDogs[position].uri)
                contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                // If deletion fails, just continue with removing from our list
                Toast.makeText(this, "Nie udało się usunąć pliku, ale usunięto z listy", Toast.LENGTH_SHORT).show()
            }

            // Remove from our list
            capturedDogs.removeAt(position)
            saveImagesList()
            Toast.makeText(this, "Zdjęcie usunięte", Toast.LENGTH_SHORT).show()

            // Update the last shown image
            if (capturedDogs.isNotEmpty()) {
                try {
                    val uri = Uri.parse(capturedDogs.last().uri)
                    val source = ImageDecoder.createSource(contentResolver, uri)
                    val bitmap = ImageDecoder.decodeBitmap(source)
                    binding.imageCapturedMedia.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    binding.imageCapturedMedia.setImageResource(R.drawable.ic_default_image)
                }
            } else {
                binding.imageCapturedMedia.setImageResource(R.drawable.ic_default_image)
            }
        }
    }

    private fun showFullScreenImage(uri: Uri) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_image_preview)

        val imageView = dialog.findViewById<ImageView>(R.id.dialogImageView)
        val textViewBreed = dialog.findViewById<TextView>(R.id.textViewBreed)
        val buttonRecognize = dialog.findViewById<Button>(R.id.buttonRecognize)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.progressBar)

        Glide.with(this).load(uri).into(imageView)

        val dogPhoto = capturedDogs.find { it.uri == uri.toString() }
        textViewBreed.text = dogPhoto?.breed?.let { "Rasa: $it" } ?: ""

        buttonRecognize.setOnClickListener {
            progressBar.visibility = View.VISIBLE
            buttonRecognize.isEnabled = false
            textViewBreed.text = "Rozpoznawanie..."

            recognizeBreed(uri) { breed ->
                progressBar.visibility = View.GONE
                buttonRecognize.isEnabled = true
                textViewBreed.text = breed?.let { "Rasa: $it" } ?: "Nie udało się rozpoznać rasy"

                val index = capturedDogs.indexOfFirst { it.uri == uri.toString() }
                if (index != -1) {
                    capturedDogs[index] = capturedDogs[index].copy(breed = breed)
                    saveImagesList()
                }
            }
        }

        dialog.show()
    }



    private fun recognizeBreed(imageUri: Uri, onFinished: (String?) -> Unit) {
        val inputStream = contentResolver.openInputStream(imageUri) ?: return onFinished(null)
        val imageBytes = inputStream.readBytes()
        val requestBody = imageBytes.toRequestBody("application/octet-stream".toMediaTypeOrNull())


        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "image.jpg", requestBody)
            .build()

        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://147.185.221.21:35978/predict")
            .post(multipartBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Błąd połączenia: ${e.message}", Toast.LENGTH_LONG).show()
                    onFinished(null)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string()?.trim()
                runOnUiThread {
                    if (response.isSuccessful) {
                        val index = capturedDogs.indexOfLast { it.uri == imageUri.toString() }
                        if (index != -1) {
                            capturedDogs[index] = capturedDogs[index].copy(breed = responseText)
                            saveImagesList()
                        }
                        Toast.makeText(this@MainActivity, "Rasa: $responseText", Toast.LENGTH_SHORT).show()
                        onFinished(responseText)
                    } else {
                        Toast.makeText(this@MainActivity, "Błąd serwera: ${response.code}", Toast.LENGTH_SHORT).show()
                        onFinished(null)
                    }
                }
            }
        })
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        permissionResultLauncher.launch(REQUIRED_PERMISSIONS.toTypedArray())
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { originalUri ->
            try {

                val fileName = "picked_${System.currentTimeMillis()}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DogsApp")
                    }
                }

                val imageUri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return@let


                contentResolver.openInputStream(originalUri)?.use { input ->
                    contentResolver.openOutputStream(imageUri)?.use { output ->
                        input.copyTo(output)
                    }
                }


                capturedDogs.add(DogPhoto(imageUri.toString()))
                saveImagesList()


                binding.imageCapturedMedia.setImageURI(imageUri)
                Toast.makeText(this, "Dodano zdjęcie z galerii", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Błąd przetwarzania obrazu: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().build()
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Camera setup failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val imageCapture = imageCapture ?: return
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault())
            .format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Images")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        capturedDogs.add(DogPhoto(uri.toString()))
                        saveImagesList()
                        binding.imageCapturedMedia.setImageURI(uri)
                        Toast.makeText(
                            this@MainActivity,
                            "Zdjęcie zapisane",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    Toast.makeText(
                        this@MainActivity,
                        "Photo capture failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    override fun onPause() {
        super.onPause()
        saveImagesList()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}