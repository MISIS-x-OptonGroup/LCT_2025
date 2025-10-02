package com.example.lct_final

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.lct_final.databinding.ActivityGalleryBinding
import com.example.lct_final.api.ImageUploadManager
import com.example.lct_final.api.ImageDetailResponse
import com.example.lct_final.api.NetworkDiagnostics
import kotlinx.coroutines.*
import android.app.ProgressDialog
import android.app.AlertDialog
import android.content.Intent
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class PhotoItem(
    val uri: Uri,
    val metadata: String,
    val dateTaken: Long,
    var isSelected: Boolean = false,
    var isDeleted: Boolean = false
)

class GalleryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGalleryBinding
    private val photos = mutableListOf<PhotoItem>()
    private lateinit var adapter: PhotoAdapter
    private var isSelectionMode = false
    private var isShowingTrash = false
    private lateinit var imageUploadManager: ImageUploadManager
    private val uploadScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    
    // Кэш текущего местоположения (для оптимизации множественных запросов)
    private var cachedLocation: String? = null
    private var cacheTimestamp: Long = 0
    private val CACHE_DURATION_MS = 60000 // 1 минута

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadPhotos()
        } else {
            Toast.makeText(this, "Необходимо разрешение для чтения фотографий", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGalleryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUploadManager = ImageUploadManager(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        setupRecyclerView()

        binding.backButton.setOnClickListener {
            finish()
        }
        
        binding.uploadFromGalleryButton.setOnClickListener {
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
                    openLiveProcessing()
                }
                .start()
        }
        
        binding.backFromTrashButton.setOnClickListener {
            toggleTrashView()
        }

        binding.cancelButton.setOnClickListener {
            exitSelectionMode()
        }

        binding.deleteButton.setOnClickListener {
            deleteSelectedPhotos()
        }

        binding.selectAllButton.setOnClickListener {
            selectAllPhotos()
        }
        
        binding.trashButton.setOnClickListener {
            toggleTrashView()
        }
        
        binding.restoreAllButton.setOnClickListener {
            restoreAllPhotos()
        }
        
        binding.emptyTrashButton.setOnClickListener {
            emptyTrash()
        }
        
        binding.sendToBackendButton.setOnClickListener {
            sendSelectedPhotosToBackend()
        }

        // Проверка разрешений
        if (checkStoragePermission()) {
            loadPhotos()
        } else {
            requestStoragePermission()
        }
    }

    private fun setupRecyclerView() {
        adapter = PhotoAdapter(
            photos,
            onItemClick = { position ->
                if (isSelectionMode) {
                    toggleSelection(position)
                } else if (isShowingTrash) {
                    // В корзине при клике восстанавливаем
                    restorePhoto(photos[position])
                    loadPhotos()
                }
            },
            onItemLongClick = { position ->
                if (!isShowingTrash && !isSelectionMode) {
                    enterSelectionMode()
                    toggleSelection(position)
                } else if (isShowingTrash) {
                    // В корзине при долгом нажатии удаляем навсегда
                    permanentlyDeletePhoto(photos[position])
                    loadPhotos()
                }
                true
            }
        )
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@GalleryActivity, 2)
            adapter = this@GalleryActivity.adapter
            
            // Оптимизация RecyclerView
            setHasFixedSize(true)
            setItemViewCacheSize(20)
            isNestedScrollingEnabled = true
        }
    }

    private fun enterSelectionMode() {
        isSelectionMode = true
        binding.selectionToolbar.visibility = View.VISIBLE
        binding.headerLayout.visibility = View.GONE
        updateSelectionCount()
    }

    private fun exitSelectionMode() {
        isSelectionMode = false
        photos.forEach { it.isSelected = false }
        adapter.notifyDataSetChanged()
        binding.selectionToolbar.visibility = View.GONE
        binding.headerLayout.visibility = View.VISIBLE
        binding.sendToBackendButton.visibility = View.GONE
    }

    private fun toggleSelection(position: Int) {
        photos[position].isSelected = !photos[position].isSelected
        adapter.notifyItemChanged(position)
        updateSelectionCount()
        
        if (photos.none { it.isSelected }) {
            exitSelectionMode()
        }
    }

    private fun selectAllPhotos() {
        photos.forEach { it.isSelected = true }
        adapter.notifyDataSetChanged()
        updateSelectionCount()
    }

    private fun updateSelectionCount() {
        val count = photos.count { it.isSelected }
        binding.selectionCountText.text = "Выбрано: $count"
        
        // Показываем/скрываем кнопку отправки в зависимости от количества выбранных фото
        if (count > 0) {
            binding.sendToBackendButton.visibility = View.VISIBLE
        } else {
            binding.sendToBackendButton.visibility = View.GONE
        }
    }

    private fun deleteSelectedPhotos() {
        val selectedPhotos = photos.filter { it.isSelected }.toList()
        if (selectedPhotos.isEmpty()) return

        val prefs = getSharedPreferences("photo_metadata", MODE_PRIVATE)
        val deletedPrefs = getSharedPreferences("deleted_photos", MODE_PRIVATE)
        
        // Помечаем фото как удаленные (перемещаем в корзину)
        selectedPhotos.forEach { photo ->
            val deleteTime = System.currentTimeMillis()
            deletedPrefs.edit().putLong(photo.uri.toString(), deleteTime).apply()
        }

        val count = selectedPhotos.size
        Toast.makeText(this, "Перемещено в корзину: $count", Toast.LENGTH_SHORT).show()
        exitSelectionMode()
        loadPhotos()
    }
    
    private fun loadDeletedPhotos(): List<PhotoItem> {
        val deletedList = mutableListOf<PhotoItem>()
        val prefs = getSharedPreferences("photo_metadata", MODE_PRIVATE)
        val deletedPrefs = getSharedPreferences("deleted_photos", MODE_PRIVATE)
        
        val deletedUris = deletedPrefs.all.keys.toList()
        
        deletedUris.forEach { uriString ->
            val uri = Uri.parse(uriString)
            val metadata = prefs.getString(uriString, "Нет метаданных") ?: "Нет метаданных"
            val deleteTime = deletedPrefs.getLong(uriString, 0)
            
            deletedList.add(PhotoItem(uri, metadata, deleteTime, false, true))
        }
        
        return deletedList.sortedByDescending { it.dateTaken }
    }
    
    private fun restorePhoto(photo: PhotoItem) {
        val deletedPrefs = getSharedPreferences("deleted_photos", MODE_PRIVATE)
        deletedPrefs.edit().remove(photo.uri.toString()).apply()
        Toast.makeText(this, "Фото восстановлено", Toast.LENGTH_SHORT).show()
        if (isShowingTrash) {
            loadPhotos()
        }
    }
    
    private fun permanentlyDeletePhoto(photo: PhotoItem) {
        val prefs = getSharedPreferences("photo_metadata", MODE_PRIVATE)
        val deletedPrefs = getSharedPreferences("deleted_photos", MODE_PRIVATE)
        
        try {
            // Удаляем файл из MediaStore
            contentResolver.delete(photo.uri, null, null)
            // Удаляем метаданные
            prefs.edit().remove(photo.uri.toString()).apply()
            deletedPrefs.edit().remove(photo.uri.toString()).apply()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка удаления: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun emptyTrash() {
        val deletedPhotos = loadDeletedPhotos()
        deletedPhotos.forEach { photo ->
            permanentlyDeletePhoto(photo)
        }
        Toast.makeText(this, "Корзина очищена", Toast.LENGTH_SHORT).show()
        loadPhotos()
    }
    
    private fun sendSelectedPhotosToBackend() {
        val selectedPhotos = photos.filter { it.isSelected }.toList()
        if (selectedPhotos.isEmpty()) return
        
        // Сначала проверяем подключение
        uploadScope.launch {
            // Проверка интернета
            if (!NetworkDiagnostics.isNetworkAvailable(this@GalleryActivity)) {
                AlertDialog.Builder(this@GalleryActivity)
                    .setTitle("Нет подключения к интернету")
                    .setMessage("Проверьте подключение к WiFi или мобильным данным и попробуйте снова.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            
            // Создаем диалог прогресса
            val progressDialog = ProgressDialog(this@GalleryActivity).apply {
                setMessage("Проверка подключения к серверу...")
                setCancelable(false)
                setProgressStyle(ProgressDialog.STYLE_SPINNER)
                show()
            }
            
            // Проверка доступности сервера
            val serverReachable = NetworkDiagnostics.isServerReachable("36.34.82.242", 18087, 10000)
            
            if (!serverReachable) {
                progressDialog.dismiss()
                val diagnostics = NetworkDiagnostics.diagnoseConnection(this@GalleryActivity)
                
                AlertDialog.Builder(this@GalleryActivity)
                    .setTitle("Сервер недоступен")
                    .setMessage(diagnostics)
                    .setPositiveButton("OK", null)
                    .setNeutralButton("Попробовать снова") { _, _ ->
                        sendSelectedPhotosToBackend()
                    }
                    .show()
                return@launch
            }
            
            progressDialog.setMessage("Загрузка фотографий...\n0 из ${selectedPhotos.size}")
            
            startUploadingPhotos(selectedPhotos, progressDialog)
        }
    }
    
    private suspend fun startUploadingPhotos(selectedPhotos: List<PhotoItem>, progressDialog: ProgressDialog) {
        var successCount = 0
        var failedCount = 0
        val uploadedImageIds = mutableListOf<Int>()
        val errors = mutableListOf<String>()
            
            selectedPhotos.forEachIndexed { index, photo ->
                try {
                    progressDialog.setMessage("Загрузка фотографий...\n${index + 1} из ${selectedPhotos.size}")
                    
                    // Собираем метаданные для фотографии
                    val metadata = buildPhotoMetadata(photo)
                    
                    // Загружаем изображение с метаданными
                    val uploadResult = imageUploadManager.uploadImage(photo.uri, metadata)
                    
                    if (uploadResult.isSuccess) {
                        val response = uploadResult.getOrNull()!!
                        uploadedImageIds.add(response.id)
                        successCount++
                        
                        // Сохраняем информацию об отправленном фото
                        saveUploadedPhotoInfo(photo, response.id)
                        
                        // Запускаем мониторинг статуса обработки в фоне
                        uploadScope.launch {
                            monitorImageProcessing(response.id, photo.uri)
                        }
                    } else {
                        failedCount++
                        val errorMsg = uploadResult.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                        errors.add("Фото ${index + 1}: $errorMsg")
                        android.util.Log.e("GalleryActivity", "Ошибка загрузки ${photo.uri}: $errorMsg", uploadResult.exceptionOrNull())
                    }
                } catch (e: Exception) {
                    failedCount++
                    errors.add("Фото ${index + 1}: ${e.message}")
                    android.util.Log.e("GalleryActivity", "Исключение при загрузке ${photo.uri}", e)
                }
            }
            
        progressDialog.dismiss()
        
        // Показываем результат
        val message = if (successCount > 0 && failedCount == 0) {
            "✓ Успешно загружено: $successCount\n\nОбработка изображений начата..."
        } else if (successCount == 0 && failedCount > 0) {
            buildString {
                append("✗ Не удалось загрузить: $failedCount фото\n\n")
                append("Ошибки:\n")
                errors.take(3).forEach { error ->
                    append("• $error\n")
                }
                if (errors.size > 3) {
                    append("... и ещё ${errors.size - 3}\n")
                }
            }
        } else {
            buildString {
                append("✓ Успешно загружено: $successCount\n")
                append("✗ Не удалось загрузить: $failedCount\n")
                if (errors.isNotEmpty()) {
                    append("\nПервые ошибки:\n")
                    errors.take(2).forEach { error ->
                        append("• $error\n")
                    }
                }
                append("\nОбработка загруженных изображений начата...")
            }
        }
        
        val dialogBuilder = AlertDialog.Builder(this@GalleryActivity)
            .setTitle("Результат загрузки")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        
        // Добавляем кнопку диагностики если были ошибки
        if (failedCount > 0) {
            dialogBuilder.setNeutralButton("Диагностика") { _, _ ->
                showNetworkDiagnostics()
            }
        }
        
        dialogBuilder.show()
        
        exitSelectionMode()
        loadPhotos()
    }
    
    private fun showNetworkDiagnostics() {
        val progressDialog = ProgressDialog(this).apply {
            setMessage("Выполняется диагностика...")
            setCancelable(false)
            show()
        }
        
        uploadScope.launch {
            val diagnostics = NetworkDiagnostics.diagnoseConnection(this@GalleryActivity)
            progressDialog.dismiss()
            
            AlertDialog.Builder(this@GalleryActivity)
                .setTitle("Диагностика сети")
                .setMessage(diagnostics)
                .setPositiveButton("OK", null)
                .show()
        }
    }
    
    private fun readLocationFromExif(uri: Uri): String? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    "${latLong[0]},${latLong[1]}"
                } else null
            }
        } catch (e: Exception) {
            android.util.Log.w("GalleryActivity", "Не удалось прочитать GPS из EXIF: $uri", e)
            null
        }
    }
    
    /**
     * Получает текущее местоположение устройства через интернет (более точный метод)
     * Использует FusedLocationProviderClient для высокой точности
     * @return Строка вида "latitude,longitude" или null если не удалось получить
     */
    private suspend fun getCurrentLocationViaInternet(): String? = withContext(Dispatchers.IO) {
        try {
            // Проверяем кэш
            val currentTime = System.currentTimeMillis()
            if (cachedLocation != null && (currentTime - cacheTimestamp) < CACHE_DURATION_MS) {
                android.util.Log.d("GalleryActivity", "✓ Используем кэшированную геопозицию: $cachedLocation")
                return@withContext cachedLocation
            }
            
            // Проверяем доступность интернета
            if (!NetworkDiagnostics.isNetworkAvailable(this@GalleryActivity)) {
                android.util.Log.w("GalleryActivity", "⚠️ Интернет недоступен, пропускаем определение через интернет")
                return@withContext null
            }
            
            // Проверяем разрешения
            if (ContextCompat.checkSelfPermission(
                    this@GalleryActivity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this@GalleryActivity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                android.util.Log.w("GalleryActivity", "⚠️ Нет разрешения на геопозицию")
                return@withContext null
            }
            
            // Получаем текущее местоположение с высокой точностью через интернет
            val location = suspendCoroutine<android.location.Location?> { continuation ->
                try {
                    val cancellationTokenSource = CancellationTokenSource()
                    
                    // Используем HIGH_ACCURACY для максимальной точности через интернет и GPS
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).addOnSuccessListener { location ->
                        continuation.resume(location)
                    }.addOnFailureListener { exception ->
                        android.util.Log.w("GalleryActivity", "⚠️ Не удалось получить местоположение", exception)
                        continuation.resume(null)
                    }
                    
                    // Таймаут 10 секунд
                    uploadScope.launch {
                        delay(10000)
                        if (!continuation.context.isActive) return@launch
                        cancellationTokenSource.cancel()
                        continuation.resume(null)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GalleryActivity", "✗ Ошибка при запросе местоположения", e)
                    continuation.resumeWithException(e)
                }
            }
            
            if (location != null) {
                val locationString = "${location.latitude},${location.longitude}"
                
                // Кэшируем результат
                cachedLocation = locationString
                cacheTimestamp = currentTime
                
                val accuracy = location.accuracy
                android.util.Log.d("GalleryActivity", "✓ Получена геопозиция через интернет: $locationString (точность: ${accuracy}м)")
                
                return@withContext locationString
            } else {
                android.util.Log.w("GalleryActivity", "⚠️ Местоположение не получено")
                return@withContext null
            }
        } catch (e: Exception) {
            android.util.Log.e("GalleryActivity", "✗ Ошибка получения местоположения через интернет", e)
            return@withContext null
        }
    }

    private suspend fun buildPhotoMetadata(photo: PhotoItem): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        
        // Получаем координаты с улучшенным методом определения
        val locationPrefs = getSharedPreferences("photo_locations", MODE_PRIVATE)
        var location: String? = null
        var locationSource = "unknown"
        
        // 1. Приоритет: пробуем получить текущее местоположение через интернет (самый точный метод)
        try {
            val internetLocation = getCurrentLocationViaInternet()
            if (internetLocation != null) {
                location = internetLocation
                locationSource = "internet+gps"
                // Кэшируем для повторного использования
                locationPrefs.edit().putString(photo.uri.toString(), internetLocation).apply()
                android.util.Log.d("GalleryActivity", "✓ Используем геопозицию через интернет: $location")
            }
        } catch (e: Exception) {
            android.util.Log.w("GalleryActivity", "⚠️ Не удалось получить местоположение через интернет", e)
        }
        
        // 2. Если не удалось получить через интернет, проверяем кэш SharedPreferences
        if (location == null) {
            location = locationPrefs.getString(photo.uri.toString(), null)
            if (location != null) {
                locationSource = "cached"
                android.util.Log.d("GalleryActivity", "✓ Используем кэшированную геопозицию: $location")
            }
        }
        
        // 3. Резерв: пытаемся прочитать координаты из EXIF (если фото имеет встроенные GPS данные)
        if (location == null) {
            val exifLocation = readLocationFromExif(photo.uri)
            if (exifLocation != null) {
                location = exifLocation
                locationSource = "exif"
                // Кэшируем для повторного использования
                locationPrefs.edit().putString(photo.uri.toString(), exifLocation).apply()
                android.util.Log.d("GalleryActivity", "✓ Используем геопозицию из EXIF: $location")
            }
        }
        
        if (location != null) {
            // Бекенд ожидает location как строку "latitude,longitude"
            metadata["location"] = location
            android.util.Log.d("GalleryActivity", "✓ Финальная геопозиция (источник: $locationSource): $location")
        } else {
            android.util.Log.w("GalleryActivity", "⚠️ Не удалось определить геопозицию ни одним из методов")
        }
        
        // Добавляем время съемки в ISO 8601 формате
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault())
        dateFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
        metadata["taken_at"] = dateFormat.format(java.util.Date(photo.dateTaken))
        
        // Добавляем автора (имя устройства)
        metadata["author"] = android.os.Build.MODEL
        
        android.util.Log.d("GalleryActivity", "Метаданные фото для отправки: $metadata")
        
        return metadata
    }
    
    private fun saveUploadedPhotoInfo(photo: PhotoItem, imageId: Int) {
        val sentPrefs = getSharedPreferences("sent_photos", MODE_PRIVATE)
        val imageIdsPrefs = getSharedPreferences("uploaded_image_ids", MODE_PRIVATE)
        
        val sendTime = System.currentTimeMillis()
        sentPrefs.edit().putLong(photo.uri.toString(), sendTime).apply()
        
        // Сохраняем ID изображения на сервере
        imageIdsPrefs.edit().putInt(photo.uri.toString(), imageId).apply()
        
        // Координаты теперь загружаются с сервера через API, не сохраняем локально
    }
    
    private suspend fun monitorImageProcessing(imageId: Int, photoUri: Uri) {
        try {
            val result = imageUploadManager.monitorProcessingStatus(
                imageId = imageId,
                maxAttempts = 60, // 5 минут (60 * 5 секунд)
                delayMillis = 5000 // Проверка каждые 5 секунд
            ) { imageDetail ->
                // Обновляем статус в UI (опционально)
                android.util.Log.d("GalleryActivity", 
                    "Изображение $imageId: статус ${imageDetail.processingStatus}")
            }
            
            if (result.isSuccess) {
                val imageDetail = result.getOrNull()!!
                
                // Результаты обработки теперь загружаются с сервера через API, не сохраняем локально
                val resultsPrefs = getSharedPreferences("processing_results", MODE_PRIVATE)
                resultsPrefs.edit().apply {
                    putString("${photoUri}_description", imageDetail.descriptionText)
                    putString("${photoUri}_status", imageDetail.processingStatus)
                    apply()
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@GalleryActivity,
                        "✓ Обработка изображения $imageId завершена!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@GalleryActivity,
                        "✗ Ошибка обработки изображения $imageId",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GalleryActivity", "Ошибка мониторинга обработки $imageId", e)
        }
    }

    private fun loadPhotos() {
        photos.clear()

        if (isShowingTrash) {
            photos.addAll(loadDeletedPhotos())
            binding.titleText.text = "Корзина"
            binding.trashToolbar.visibility = View.VISIBLE
            binding.normalToolbar.visibility = View.GONE
        } else {
            val prefs = getSharedPreferences("photo_metadata", MODE_PRIVATE)
            val deletedPrefs = getSharedPreferences("deleted_photos", MODE_PRIVATE)
            val sentPrefs = getSharedPreferences("sent_photos", MODE_PRIVATE)
            val deletedUris = deletedPrefs.all.keys.toSet()
            val sentUris = sentPrefs.all.keys.toSet()

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN,
                MediaStore.Images.Media.RELATIVE_PATH
            )

            val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            } else {
                null
            }

            val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf("%Pictures/LCT_final%")
            } else {
                null
            }

            val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateTaken = cursor.getLong(dateTakenColumn)

                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )

                    // Пропускаем удаленные и отправленные фото
                    if (deletedUris.contains(contentUri.toString()) || 
                        sentUris.contains(contentUri.toString())) {
                        continue
                    }

                    // Загружаем метаданные из SharedPreferences
                    val metadata = prefs.getString(contentUri.toString(), null)
                        ?: "Нет метаданных"

                    photos.add(PhotoItem(contentUri, metadata, dateTaken))
                }
            }
            
            binding.titleText.text = "Моя галерея"
            binding.trashToolbar.visibility = View.GONE
            binding.normalToolbar.visibility = View.VISIBLE
        }

        adapter.notifyDataSetChanged()

        if (photos.isEmpty()) {
            binding.emptyTextView.visibility = View.VISIBLE
            binding.emptyTextView.text = if (isShowingTrash) "Корзина пуста" else "Пока нет фотографий\nСделайте первое фото!"
            binding.recyclerView.visibility = View.GONE
        } else {
            binding.emptyTextView.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }
    
    private fun toggleTrashView() {
        isShowingTrash = !isShowingTrash
        loadPhotos()
    }
    
    private fun restoreAllPhotos() {
        photos.forEach { photo ->
            if (photo.isDeleted) {
                restorePhoto(photo)
            }
        }
        loadPhotos()
    }

    private fun checkStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermissionLauncher.launch(permission)
    }

    private fun openLiveProcessing() {
        val intent = Intent(this, LiveProcessingActivity::class.java)
        startActivity(intent)
    }

    override fun onBackPressed() {
        if (isSelectionMode) {
            exitSelectionMode()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        uploadScope.cancel()
    }
}

class PhotoAdapter(
    private val photos: List<PhotoItem>,
    private val onItemClick: (Int) -> Unit,
    private val onItemLongClick: (Int) -> Boolean
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.photoImageView)
        val metadataTextView: TextView = view.findViewById(R.id.metadataTextView)
        val selectionOverlay: View = view.findViewById(R.id.selectionOverlay)
        val checkIcon: ImageView = view.findViewById(R.id.checkIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photo = photos[position]
        
        // Используем Glide для эффективной загрузки изображений
        Glide.with(holder.itemView.context)
            .load(photo.uri)
            .thumbnail(0.1f) // Загружаем миниатюру для быстрого превью
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL) // Кэшируем всё
            .into(holder.imageView)
        
        holder.metadataTextView.text = photo.metadata
        
        // Показываем/скрываем overlay и checkbox
        if (photo.isSelected) {
            holder.selectionOverlay.visibility = View.VISIBLE
            holder.checkIcon.visibility = View.VISIBLE
        } else {
            holder.selectionOverlay.visibility = View.GONE
            holder.checkIcon.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(position)
        }
        
        holder.itemView.setOnLongClickListener {
            onItemLongClick(position)
        }
    }
    
    override fun onViewRecycled(holder: PhotoViewHolder) {
        super.onViewRecycled(holder)
        // Освобождаем ресурсы при переработке view
        Glide.with(holder.itemView.context).clear(holder.imageView)
    }

    override fun getItemCount() = photos.size
}
