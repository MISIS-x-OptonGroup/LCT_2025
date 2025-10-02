package com.example.lct_final

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.lct_final.databinding.ActivityLiveProcessingBinding
import com.example.lct_final.api.ImageUploadManager
import com.example.lct_final.api.ImageDetailResponse
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class LiveProcessingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLiveProcessingBinding
    private lateinit var imageUploadManager: ImageUploadManager
    private val processingScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentImageDetail: ImageDetailResponse? = null
    private var currentPhotoUri: Uri? = null

    // Launcher для выбора фото из галереи
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                processPhoto(uri)
            } else {
                Toast.makeText(this, "Не удалось выбрать фото", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveProcessingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUploadManager = ImageUploadManager(this)

        setupUI()

        // Если Activity запущена с URI, обрабатываем его
        val photoUriString = intent.getStringExtra("photo_uri")
        if (photoUriString != null) {
            val uri = Uri.parse(photoUriString)
            processPhoto(uri)
        } else {
            // Иначе предлагаем выбрать фото
            openGalleryPicker()
        }
    }

    private fun setupUI() {
        binding.closeButton.setOnClickListener {
            // При закрытии возвращаемся к списку недавних фото, если обработка завершена
            if (currentImageDetail != null) {
                val intent = Intent(this, RecentPhotosActivity::class.java)
                startActivity(intent)
            }
            finish()
        }

        binding.nextPhotoButton.setOnClickListener {
            // Анимация кнопки
            it.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    openGalleryPicker()
                }
                .start()
        }

        binding.viewDetailsButton.setOnClickListener {
            // Анимация кнопки
            it.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    currentImageDetail?.let { image ->
                        showDetailedResults(image)
                    }
                }
                .start()
        }
    }

    private fun openGalleryPicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    private fun processPhoto(uri: Uri) {
        currentPhotoUri = uri
        
        // Отображаем фото
        Glide.with(this)
            .load(uri)
            .centerCrop()
            .into(binding.photoImageView)

        // Сбрасываем UI
        resetUI()

        // Запускаем процесс обработки
        processingScope.launch {
            try {
                // Шаг 1: Подготовка
                updateStatus("Подготовка к загрузке...")
                delay(500)

                // Шаг 2: Сбор метаданных
                updateStatus("Сбор метаданных...")
                val metadata = buildPhotoMetadata(uri)
                delay(500)

                // Шаг 3: Загрузка на сервер
                updateStatus("Загрузка на сервер...")
                val uploadResult = imageUploadManager.uploadImage(uri, metadata)

                if (uploadResult.isFailure) {
                    val errorMsg = uploadResult.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    showError("Ошибка загрузки: $errorMsg")
                    return@launch
                }

                val uploadResponse = uploadResult.getOrNull()!!
                updateStatus("✓ Фото загружено (ID: ${uploadResponse.id})")
                delay(1000)

                // Шаг 4: Мониторинг обработки
                updateStatus("Обработка изображения...")
                val processingResult = imageUploadManager.monitorProcessingStatus(
                    imageId = uploadResponse.id,
                    maxAttempts = 60, // 5 минут
                    delayMillis = 5000 // Проверка каждые 5 секунд
                ) { imageDetail ->
                    // Обновляем статус в UI
                    val status = when (imageDetail.processingStatus) {
                        "uploaded" -> "Ожидание обработки..."
                        "processing" -> "Обрабатывается..."
                        "completed" -> "✓ Обработка завершена!"
                        else -> imageDetail.processingStatus
                    }
                    updateStatus(status)
                }

                if (processingResult.isFailure) {
                    showError("Ошибка обработки: таймаут или ошибка сервера")
                    return@launch
                }

                val imageDetail = processingResult.getOrNull()!!
                currentImageDetail = imageDetail

                // Шаг 5: Отображение результатов
                showResults(imageDetail)

            } catch (e: Exception) {
                android.util.Log.e("LiveProcessingActivity", "Ошибка обработки", e)
                showError("Ошибка: ${e.message}")
            }
        }
    }

    private fun resetUI() {
        binding.statusText.text = "Подготовка к загрузке..."
        binding.progressBar.isIndeterminate = true
        binding.progressBar.visibility = View.VISIBLE
        binding.resultsSection.visibility = View.GONE
        currentImageDetail = null
    }

    private fun updateStatus(status: String) {
        binding.statusText.text = status
    }

    private fun showResults(imageDetail: ImageDetailResponse) {
        binding.progressBar.visibility = View.GONE
        
        // Анимация появления результатов
        binding.resultsSection.apply {
            alpha = 0f
            visibility = View.VISIBLE
            animate()
                .alpha(1f)
                .setDuration(500)
                .start()
        }

        val objectsCount = imageDetail.detectedObjects?.size ?: 0
        binding.objectsCountText.text = objectsCount.toString()

        if (objectsCount > 0) {
            val description = buildString {
                imageDetail.detectedObjects?.take(3)?.forEach { obj ->
                    append("• ${obj.label.replaceFirstChar { it.uppercase() }}")
                    append(" (${(obj.confidence * 100).toInt()}%)\n")
                }
                if (objectsCount > 3) {
                    append("... и еще ${objectsCount - 3}")
                }
            }
            binding.descriptionText.text = description
        } else {
            binding.descriptionText.text = "Объекты не обнаружены"
        }

        updateStatus("✓ Обработка завершена успешно!")
        
        // Очищаем кэш изображений для получения свежих данных
        com.example.lct_final.api.ImageCache.clearCache()
        android.util.Log.d("LiveProcessingActivity", "🧹 Кэш изображений очищен после успешной обработки")
        
        // Показываем Toast с уведомлением
        Toast.makeText(
            this, 
            "✓ Найдено объектов: $objectsCount", 
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showError(errorMessage: String) {
        binding.progressBar.visibility = View.GONE
        binding.statusText.text = "✗ $errorMessage"
        
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        
        // Через 3 секунды предлагаем выбрать другое фото
        processingScope.launch {
            delay(3000)
            binding.nextPhotoButton.performClick()
        }
    }

    private suspend fun buildPhotoMetadata(uri: Uri): Map<String, String> {
        val metadata = mutableMapOf<String, String>()

        // Получаем время создания из MediaStore
        val projection = arrayOf(MediaStore.Images.Media.DATE_TAKEN)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val dateTaken = cursor.getLong(dateTakenColumn)

                // Добавляем время съемки в ISO 8601 формате
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                metadata["taken_at"] = dateFormat.format(Date(dateTaken))
            }
        }

        // Добавляем автора (имя устройства)
        metadata["author"] = android.os.Build.MODEL

        // Пытаемся получить координаты из EXIF
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = androidx.exifinterface.media.ExifInterface(input)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    metadata["location"] = "${latLong[0]},${latLong[1]}"
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("LiveProcessingActivity", "Не удалось прочитать GPS из EXIF", e)
        }

        return metadata
    }

    private fun showDetailedResults(image: ImageDetailResponse) {
        // Открываем RecentPhotosActivity с детальной информацией
        val intent = Intent(this, RecentPhotosActivity::class.java)
        intent.putExtra("scroll_to_id", image.id)
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Применяем анимацию появления при возврате
        binding.photoCard.alpha = 0f
        binding.photoCard.scaleX = 0.8f
        binding.photoCard.scaleY = 0.8f
        binding.photoCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()
    }

    override fun onDestroy() {
        super.onDestroy()
        processingScope.cancel()
    }
}

