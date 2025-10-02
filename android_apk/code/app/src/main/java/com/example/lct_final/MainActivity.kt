package com.example.lct_final

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.example.lct_final.databinding.ActivityMainBinding
import com.example.lct_final.api.ImageUploadManager
import com.example.lct_final.api.ImageUrlHelper
import com.example.lct_final.api.ImageCropHelper
import com.example.lct_final.utils.TreeDataFormatter
import com.google.android.material.button.MaterialButton
import android.graphics.Bitmap
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var imageUploadManager: ImageUploadManager
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Инициализация конфигурации osmdroid
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUploadManager = ImageUploadManager(this)

        // Инициализация карты на главном экране
        setupMainMap()

        // Обработчик клика по прозрачной View поверх карты - открытие полноэкранной карты
        binding.mapClickOverlay.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        }
        
        // Применяем анимации
        applyAnimations()
    }
    
    private fun applyAnimations() {
        // Анимация кнопки помощи
        binding.helpButton.apply {
            alpha = 0f
            scaleX = 0.5f
            scaleY = 0.5f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(600)
                .start()
        }
        
        // Анимация карты
        binding.mapCard.apply {
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(700)
                .setStartDelay(500)
                .start()
        }
        
        // Анимация кнопок снизу
        binding.iconButton.apply {
            alpha = 0f
            translationY = 100f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(700)
                .start()
        }
        
        binding.getStartedButton.apply {
            alpha = 0f
            translationY = 100f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(750)
                .start()
        }
        
        // Анимация клика для кнопок
        binding.getStartedButton.setOnClickListener {
            it.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    val intent = Intent(this, CameraActivity::class.java)
                    startActivity(intent)
                }
                .start()
        }
        
        binding.iconButton.setOnClickListener {
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
                    val intent = Intent(this, GalleryActivity::class.java)
                    startActivity(intent)
                }
                .start()
        }
        
        binding.recentPhotosButton.apply {
            alpha = 0f
            translationY = 100f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(700)
                .setStartDelay(800)
                .start()
        }
        
        binding.recentPhotosButton.setOnClickListener {
            it.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                    val intent = Intent(this, RecentPhotosActivity::class.java)
                    startActivity(intent)
                }
                .start()
        }
        
        binding.helpButton.setOnClickListener {
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
                    showInstructionDialog()
                }
                .start()
        }
    }
    
    private fun showInstructionDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_instruction)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        val closeButton = dialog.findViewById<MaterialButton>(R.id.instructionCloseButton)
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun setupMainMap() {
        binding.mainMapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false) // Отключаем мультитач
            
            // Устанавливаем начальную позицию (Москва)
            controller.setZoom(12.0)
            controller.setCenter(GeoPoint(55.751244, 37.618423))
            
            // Делаем карту статической (без навигации)
            // Убираем isClickable и isEnabled, чтобы клики проходили к CardView
            
            // Запрещаем прокрутку
            setScrollableAreaLimitDouble(null)
            isHorizontalMapRepetitionEnabled = false
            isVerticalMapRepetitionEnabled = false
            
            // Отключаем возможность прокрутки и масштабирования
            minZoomLevel = 12.0
            maxZoomLevel = 12.0
        }
        
        // Загружаем и отображаем маркеры отправленных фотографий
        loadSentPhotoMarkers()
    }
    
    private fun loadSentPhotoMarkers() {
        // Загружаем изображения с сервера (используем кэш)
        mainScope.launch {
            try {
                android.util.Log.d("MainActivity", "Начинаем загрузку изображений...")
                val result = com.example.lct_final.api.ImageCache.refresh(imageUploadManager)
                
                if (result.isSuccess) {
                    val images = result.getOrNull() ?: emptyList()
                    android.util.Log.d("MainActivity", "✓ Загружено изображений с сервера: ${images.size}")
                    
                    if (images.isEmpty()) {
                        Toast.makeText(this@MainActivity, "На сервере пока нет изображений", Toast.LENGTH_SHORT).show()
                    }
                    
                    var firstMarkerLocation: GeoPoint? = null
                    var markerCount = 0
                    
                    // Отображаем только обработанные изображения с координатами
                    // Сортируем по времени убывания и берем последние 100
                    images
                        .filter { it.processingStatus == "completed" && !it.location.isNullOrEmpty() }
                        .sortedByDescending { it.createdAt }  // Сортировка по времени в убывающем порядке (новые сверху)
                        .take(100)  // Берем только последние 100
                        .forEach { image ->
                        try {
                            val coordinates = image.location!!.split(",")
                            if (coordinates.size == 2) {
                                val latitude = coordinates[0].trim().toDouble()
                                val longitude = coordinates[1].trim().toDouble()
                                val location = GeoPoint(latitude, longitude)
                                markerCount++
                                
                                // Сохраняем первый маркер для центрирования карты
                                if (firstMarkerLocation == null) {
                                    firstMarkerLocation = location
                                }
                                
                                // Добавляем маркер
                                val marker = Marker(binding.mainMapView).apply {
                                    position = location
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    
                                    // Формируем заголовок на основе найденных объектов
                                    val objectsCount = image.detectedObjects?.size ?: 0
                                    title = if (objectsCount > 0) {
                                        "Найдено объектов: $objectsCount"
                                    } else {
                                        "Изображение #${image.id}"
                                    }
                                    
                                    // Формируем краткое описание
                                    snippet = image.detectedObjects?.firstOrNull()?.let { obj ->
                                        "${obj.label} (${(obj.confidence * 100).toInt()}%)"
                                    } ?: "Обработано"
                                    
                                    // Обработчик клика на маркер
                                    setOnMarkerClickListener { clickedMarker, _ ->
                                        showServerPhotoInfoDialog(image, location)
                                        true
                                    }
                                }
                                binding.mainMapView.overlays.add(marker)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Ошибка парсинга координат для изображения ${image.id}", e)
                        }
                    }
                    
                    // Центрируем карту на первом маркере, если есть маркеры
                    firstMarkerLocation?.let {
                        binding.mainMapView.controller.setCenter(it)
                    }
                    
                    binding.mainMapView.invalidate()
                    
                    if (markerCount > 0) {
                        Toast.makeText(this@MainActivity, "Загружено $markerCount изображений на карту", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    android.util.Log.e("MainActivity", "✗ Ошибка загрузки изображений: $errorMsg")
                    Toast.makeText(this@MainActivity, "Ошибка загрузки: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "✗ Исключение при загрузке изображений", e)
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showServerPhotoInfoDialog(image: com.example.lct_final.api.ImageDetailResponse, location: GeoPoint) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.bottom_sheet_photo_info)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Получаем элементы интерфейса
        val photoImageView = dialog.findViewById<ImageView>(R.id.photoImageView)
        val photoTitleText = dialog.findViewById<TextView>(R.id.photoTitleText)
        val photoSubtitleText = dialog.findViewById<TextView>(R.id.photoSubtitleText)
        val dateValueText = dialog.findViewById<TextView>(R.id.dateValueText)
        val timeValueText = dialog.findViewById<TextView>(R.id.timeValueText)
        val coordinatesText = dialog.findViewById<TextView>(R.id.coordinatesText)
        val statusValueText = dialog.findViewById<TextView>(R.id.statusValueText)
        val backendDataText = dialog.findViewById<TextView>(R.id.backendDataText)
        val descriptionText = dialog.findViewById<TextView>(R.id.descriptionText)
        val backendDataSection = dialog.findViewById<android.view.ViewGroup>(R.id.backendDataSection)
        val backendResultText = dialog.findViewById<TextView>(R.id.backendResultText)
        val closeButton = dialog.findViewById<MaterialButton>(R.id.closeButton)
        val shareButton = dialog.findViewById<MaterialButton>(R.id.shareButton)
        
        // Загружаем фотографию через download API
        android.util.Log.d("MainActivity", "📸 Загружаем изображение через download API:")
        android.util.Log.d("MainActivity", "  ID изображения: ${image.id}")
        
        // Загружаем download URL через API
        mainScope.launch {
            try {
                val apiService = com.example.lct_final.api.RetrofitClient.imageApiService
                val downloadResponse = apiService.getImageDownloadUrl(image.id, 3600)
                
                if (downloadResponse.isSuccessful && downloadResponse.body() != null) {
                    val downloadData = downloadResponse.body()!!
                    val publicUrl = ImageCropHelper.convertMinioUrlToPublic(downloadData.downloadUrl)
                    
                    android.util.Log.d("MainActivity", "✓ Получен download URL: $publicUrl")
                    
                    // Загружаем главное изображение через Glide
                    Glide.with(this@MainActivity)
                        .load(publicUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(photoImageView)
                    
                    // Добавляем обработчик клика для полноэкранного просмотра
                    photoImageView.setOnClickListener {
                        ImageViewerHelper.openImageFullscreen(this@MainActivity, publicUrl)
                    }
                    
                    // Загружаем главное изображение для вырезания фрагментов
                    val mainBitmap = ImageCropHelper.getMainImage(image.id, publicUrl)
                    if (mainBitmap != null) {
                        android.util.Log.d("MainActivity", "✓ Главное изображение загружено: ${mainBitmap.width}x${mainBitmap.height}")
                        
                        // Отображаем фрагменты после загрузки главного изображения
                        displayFragments(dialog, image, mainBitmap)
                    } else {
                        android.util.Log.e("MainActivity", "✗ Не удалось загрузить главное изображение для вырезания фрагментов")
                    }
                } else {
                    android.util.Log.e("MainActivity", "✗ Ошибка получения download URL: ${downloadResponse.code()}")
                    Toast.makeText(this@MainActivity, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "✗ Ошибка загрузки изображения", e)
                Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Форматируем дату и время
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        val createdDate = try {
            val parsedDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(image.createdAt.take(19))
            // Прибавляем 3 часа (3 * 60 * 60 * 1000 = 10800000 мс)
            Date(parsedDate.time + 10800000L)
        } catch (e: Exception) {
            Date()
        }
        
        // Заполняем данные
        val objectsCount = image.detectedObjects?.size ?: 0
        photoTitleText.text = if (objectsCount > 0) {
            "Найдено объектов: $objectsCount"
        } else {
            "Изображение #${image.id}"
        }
        photoSubtitleText.text = image.originalFilename ?: "Без имени"
        dateValueText.text = dateFormat.format(createdDate)
        timeValueText.text = timeFormat.format(createdDate)
        coordinatesText.text = String.format(
            "Широта: %.6f, Долгота: %.6f",
            location.latitude,
            location.longitude
        )
        
        // Статус обработки
        statusValueText.text = when (image.processingStatus) {
            "completed" -> "Завершено"
            "processing" -> "Обработка"
            "uploaded" -> "Загружено"
            else -> image.processingStatus
        }
        
        // Информация о данных
        backendDataText.text = if (objectsCount > 0) "✓" else "—"
        
        // Описание
        descriptionText.text = image.descriptionText?.takeIf { it.isNotEmpty() } 
            ?: "Изображение успешно обработано"
        
        // Отображаем детальную информацию об объектах
        if (objectsCount > 0) {
            backendDataSection.visibility = android.view.View.VISIBLE
            backendResultText.text = formatDetectedObjects(image.detectedObjects!!)
        } else {
            backendDataSection.visibility = android.view.View.GONE
        }
        
        // Примечание: Фрагменты будут отображены после загрузки главного изображения
        // в методе displayFragments()
        
        // Обработчики кнопок
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        shareButton.setOnClickListener {
            Toast.makeText(this, "Изображение: ${image.s3Url}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    /**
     * Отображает фрагменты объектов, вырезанные из главного изображения
     */
    private fun displayFragments(dialog: Dialog, image: com.example.lct_final.api.ImageDetailResponse, mainBitmap: Bitmap) {
        val fragmentsSection = dialog.findViewById<android.view.ViewGroup>(R.id.fragmentsSection)
        val fragmentsContainer = dialog.findViewById<android.view.ViewGroup>(R.id.fragmentsContainer)
        
        val objectsCount = image.detectedObjects?.size ?: 0
        
        if (objectsCount > 0) {
            fragmentsSection.visibility = android.view.View.VISIBLE
            fragmentsContainer.removeAllViews()
            
            image.detectedObjects!!.forEach { obj ->
                // Вырезаем фрагмент из главного изображения по bbox
                val fragmentBitmap = ImageCropHelper.cropBitmapByBbox(mainBitmap, obj.bbox)
                
                if (fragmentBitmap != null) {
                    addFragmentView(fragmentsContainer, obj, fragmentBitmap)
                } else {
                    android.util.Log.e("MainActivity", "Не удалось вырезать фрагмент для объекта: ${obj.label}")
                }
            }
        } else {
            fragmentsSection.visibility = android.view.View.GONE
        }
    }
    
    private fun addFragmentView(
        container: android.view.ViewGroup, 
        obj: com.example.lct_final.api.DetectedObject,
        fragmentBitmap: Bitmap
    ) {
        val fragmentView = layoutInflater.inflate(R.layout.item_fragment, container, false)
        val fragmentImageView = fragmentView.findViewById<ImageView>(R.id.fragmentImageView)
        
        // Отображаем вырезанный фрагмент
        android.util.Log.d("MainActivity", "Отображаем фрагмент: ${obj.label} (${fragmentBitmap.width}x${fragmentBitmap.height})")
        
        Glide.with(this)
            .load(fragmentBitmap)
            .centerCrop()
            .placeholder(R.drawable.ic_launcher_foreground)
            .error(R.drawable.ic_launcher_foreground)
            .into(fragmentImageView)
        
        // Клик на фрагмент - открываем детали
        fragmentView.setOnClickListener {
            showFragmentDetailDialog(obj, fragmentBitmap)
        }
        
        container.addView(fragmentView)
    }
    
    /**
     * Показывает детальную информацию о фрагменте
     */
    private fun showFragmentDetailDialog(obj: com.example.lct_final.api.DetectedObject, fragmentBitmap: Bitmap) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_fragment_detail)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Получаем элементы интерфейса
        val detailFragmentImageView = dialog.findViewById<ImageView>(R.id.detailFragmentImageView)
        val detailObjectTypeText = dialog.findViewById<TextView>(R.id.detailObjectTypeText)
        val detailObjectSubtypeText = dialog.findViewById<TextView>(R.id.detailObjectSubtypeText)
        val detailConfidenceBadge = dialog.findViewById<TextView>(R.id.detailConfidenceBadge)
        val detailSpeciesSection = dialog.findViewById<android.view.ViewGroup>(R.id.detailSpeciesSection)
        val detailSpeciesText = dialog.findViewById<TextView>(R.id.detailSpeciesText)
        val detailSeasonSection = dialog.findViewById<android.view.ViewGroup>(R.id.detailSeasonSection)
        val detailSeasonText = dialog.findViewById<TextView>(R.id.detailSeasonText)
        val detailSeasonNoteText = dialog.findViewById<TextView>(R.id.detailSeasonNoteText)
        val detailDiseasesSection = dialog.findViewById<android.view.ViewGroup>(R.id.detailDiseasesSection)
        val detailDiseasesText = dialog.findViewById<TextView>(R.id.detailDiseasesText)
        val detailPestsSection = dialog.findViewById<android.view.ViewGroup>(R.id.detailPestsSection)
        val detailPestsText = dialog.findViewById<TextView>(R.id.detailPestsText)
        val detailConditionSection = dialog.findViewById<android.view.ViewGroup>(R.id.detailConditionSection)
        val detailConditionText = dialog.findViewById<TextView>(R.id.detailConditionText)
        val detailRiskSection = dialog.findViewById<android.view.ViewGroup>(R.id.detailRiskSection)
        val detailRiskLevelText = dialog.findViewById<TextView>(R.id.detailRiskLevelText)
        val detailRiskDriversText = dialog.findViewById<TextView>(R.id.detailRiskDriversText)
        val detailDataQualitySection = dialog.findViewById<android.view.ViewGroup>(R.id.detailDataQualitySection)
        val detailDataQualityText = dialog.findViewById<TextView>(R.id.detailDataQualityText)
        val detailCloseButton = dialog.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.detailCloseButton)
        
        // Отображаем изображение фрагмента
        Glide.with(this)
            .load(fragmentBitmap)
            .centerCrop()
            .into(detailFragmentImageView)
        
        // Добавляем обработчик клика для полноэкранного просмотра
        detailFragmentImageView.setOnClickListener {
            ImageViewerHelper.openBitmapFullscreen(this, fragmentBitmap)
        }
        
        // Логируем данные объекта для отладки
        android.util.Log.d("MainActivity", "=== Детали фрагмента ===")
        android.util.Log.d("MainActivity", "Label: ${obj.label}")
        android.util.Log.d("MainActivity", "Confidence: ${obj.confidence}")
        android.util.Log.d("MainActivity", "Description: ${obj.description}")
        android.util.Log.d("MainActivity", "Scene: ${obj.description?.scene}")
        android.util.Log.d("MainActivity", "ObjectInfo: ${obj.description?.objectInfo}")
        android.util.Log.d("MainActivity", "Species: ${obj.description?.objectInfo?.species}")
        android.util.Log.d("MainActivity", "Condition: ${obj.description?.objectInfo?.condition}")
        android.util.Log.d("MainActivity", "Risk: ${obj.description?.objectInfo?.risk}")
        android.util.Log.d("MainActivity", "DataQuality: ${obj.description?.dataQuality}")
        
        // Тип объекта
        val objType = obj.description?.objectInfo?.type ?: obj.label
        detailObjectTypeText.text = "Тип: ${objType.replaceFirstChar { it.uppercase() }}"
        
        // Подтип объекта
        detailObjectSubtypeText.text = obj.description?.objectInfo?.type ?: "Неопределено"
        
        // Confidence badge
        obj.confidence?.let { confidence ->
            detailConfidenceBadge.text = "${(confidence * 100).toInt()}%"
            detailConfidenceBadge.visibility = android.view.View.VISIBLE
        } ?: run {
            detailConfidenceBadge.visibility = android.view.View.GONE
        }
        
        // Вид
        obj.description?.objectInfo?.species?.let { species ->
            if (!species.labelRu.isNullOrEmpty()) {
                detailSpeciesSection.visibility = android.view.View.VISIBLE
                val speciesName = if (species.labelRu == "неопределено") {
                    "Не удалось определить вид"
                } else {
                    species.labelRu!!
                }
                detailSpeciesText.text = if (species.confidence != null && species.confidence > 0) {
                    "$speciesName (уверенность: ${species.confidence}%)"
                } else {
                    speciesName
                }
            }
        }
        
        // Сезон
        obj.description?.scene?.let { scene ->
            if (!scene.seasonInferred.isNullOrEmpty()) {
                detailSeasonSection.visibility = android.view.View.VISIBLE
                val seasonRu = when (scene.seasonInferred.lowercase()) {
                    "spring" -> "Весна"
                    "summer" -> "Лето"
                    "autumn", "fall" -> "Осень"
                    "winter" -> "Зима"
                    else -> scene.seasonInferred
                }
                detailSeasonText.text = seasonRu
                if (!scene.note.isNullOrEmpty()) {
                    detailSeasonNoteText.text = scene.note
                    detailSeasonNoteText.visibility = android.view.View.VISIBLE
                } else {
                    detailSeasonNoteText.visibility = android.view.View.GONE
                }
            }
        }
        
        // Болезни
        obj.description?.objectInfo?.condition?.diseases?.let { diseases ->
            val diseasesText = TreeDataFormatter.formatDiseases(diseases)
            if (diseasesText.isNotEmpty()) {
                detailDiseasesSection.visibility = android.view.View.VISIBLE
                detailDiseasesText.text = diseasesText
            }
        }
        
        // Вредители
        obj.description?.objectInfo?.condition?.pests?.let { pests ->
            val pestsText = TreeDataFormatter.formatPests(pests)
            if (pestsText.isNotEmpty()) {
                detailPestsSection.visibility = android.view.View.VISIBLE
                detailPestsText.text = pestsText
            }
        }
        
        // Состояние
        obj.description?.objectInfo?.condition?.let { condition ->
            val conditionText = TreeDataFormatter.formatCondition(condition)
            if (conditionText.isNotEmpty()) {
                detailConditionSection.visibility = android.view.View.VISIBLE
                detailConditionText.text = conditionText
            }
        }
        
        // Риск
        obj.description?.objectInfo?.risk?.let { risk ->
            if (!risk.level.isNullOrEmpty()) {
                detailRiskSection.visibility = android.view.View.VISIBLE
                
                val (riskText, driversText) = TreeDataFormatter.formatRisk(risk)
                detailRiskLevelText.text = riskText
                detailRiskLevelText.setTextColor(TreeDataFormatter.getRiskColor(risk.level))
                
                if (driversText.isNotEmpty()) {
                    detailRiskDriversText.visibility = android.view.View.VISIBLE
                    detailRiskDriversText.text = driversText
                } else {
                    detailRiskDriversText.visibility = android.view.View.GONE
                }
            }
        }
        
        // Качество данных
        obj.description?.dataQuality?.let { quality ->
            detailDataQualitySection.visibility = android.view.View.VISIBLE
            val qualityInfo = buildString {
                quality.overallConfidence?.let {
                    append("Общая уверенность: $it%")
                }
                if (!quality.issues.isNullOrEmpty()) {
                    if (isNotEmpty()) append("\n\n")
                    append("Замечания:\n")
                    append(quality.issues.joinToString("\n• ", "• "))
                }
            }
            detailDataQualityText.text = qualityInfo.ifEmpty { "Нет дополнительной информации" }
        }
        
        // Если description == null, показываем сообщение
        if (obj.description == null) {
            android.util.Log.w("MainActivity", "⚠️ Description == null для объекта ${obj.label}")
            detailDataQualitySection.visibility = android.view.View.VISIBLE
            detailDataQualityText.text = "Детальная информация об объекте недоступна.\n\nОбъект был обнаружен, но детальный анализ не был выполнен."
        }
        
        // Кнопка закрытия
        detailCloseButton.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
    }
    
    private fun formatDetectedObjects(objects: List<com.example.lct_final.api.DetectedObject>): String {
        return objects.mapIndexed { index, obj ->
            buildString {
                append("${index + 1}. ${obj.label.replaceFirstChar { it.uppercase() }} ")
                append("(уверенность: ${(obj.confidence * 100).toInt()}%)\n")
                
                try {
                    obj.description?.objectInfo?.let { info ->
                        info.species?.labelRu?.let { species ->
                            if (species.isNotEmpty() && species != "неопределено") {
                                append("   Вид: $species")
                                info.species.confidence?.let { conf ->
                                    if (conf > 50) append(" ($conf%)")
                                }
                                append("\n")
                            }
                        }
                        
                        info.condition?.let { condition ->
                            val issues = mutableListOf<String>()
                            
                            condition.diseases?.filter { (it.likelihood ?: 0) > 30 }?.forEach { disease ->
                                disease.nameRu?.let { name ->
                                    issues.add("Болезнь: $name (${disease.likelihood}%)")
                                }
                            }
                            
                            condition.pests?.filter { (it.likelihood ?: 0) > 30 }?.forEach { pest ->
                                pest.nameRu?.let { name ->
                                    issues.add("Вредитель: $name (${pest.likelihood}%)")
                                }
                            }
                            
                            condition.dryBranchesPct?.let { pct ->
                                if (pct > 0) {
                                    issues.add("Сухие ветки: $pct%")
                                }
                            }
                            
                            if (issues.isNotEmpty()) {
                                append("   Проблемы:\n")
                                issues.forEach { append("   • $it\n") }
                            }
                        }
                        
                        info.risk?.level?.let { level ->
                            val riskLevel = when (level.lowercase()) {
                                "low" -> "Низкий"
                                "medium" -> "Средний"
                                "high" -> "Высокий"
                                else -> level
                            }
                            append("   Уровень риска: $riskLevel\n")
                        }
                    }
                    
                    obj.description?.dataQuality?.let { quality ->
                        quality.overallConfidence?.let { conf ->
                            append("   Качество анализа: $conf%\n")
                        }
                        quality.issues?.takeIf { it.isNotEmpty() }?.let { issues ->
                            append("   Замечания: ${issues.joinToString(", ")}\n")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Ошибка форматирования объекта ${obj.label}", e)
                }
            }
        }.joinToString("\n")
    }
    
    private fun showPhotoInfoDialog(photoUri: Uri, location: GeoPoint, photoNumber: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.bottom_sheet_photo_info)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // Получаем элементы интерфейса
        val photoImageView = dialog.findViewById<ImageView>(R.id.photoImageView)
        val photoTitleText = dialog.findViewById<TextView>(R.id.photoTitleText)
        val photoSubtitleText = dialog.findViewById<TextView>(R.id.photoSubtitleText)
        val dateValueText = dialog.findViewById<TextView>(R.id.dateValueText)
        val timeValueText = dialog.findViewById<TextView>(R.id.timeValueText)
        val coordinatesText = dialog.findViewById<TextView>(R.id.coordinatesText)
        val closeButton = dialog.findViewById<MaterialButton>(R.id.closeButton)
        val shareButton = dialog.findViewById<MaterialButton>(R.id.shareButton)
        
        // Загружаем фотографию
        Glide.with(this)
            .load(photoUri)
            .centerCrop()
            .into(photoImageView)
        
        // Получаем метаданные
        val prefs = getSharedPreferences("photo_metadata", MODE_PRIVATE)
        val metadata = prefs.getString(photoUri.toString(), null)
        
        // Получаем время отправки
        val sentPrefs = getSharedPreferences("sent_photos", MODE_PRIVATE)
        val sendTime = sentPrefs.getLong(photoUri.toString(), System.currentTimeMillis())
        
        // Форматируем дату и время
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val date = Date(sendTime)
        
        // Заполняем данные
        photoTitleText.text = "Отправленное фото"
        photoSubtitleText.text = "Фото #$photoNumber"
        dateValueText.text = dateFormat.format(date)
        timeValueText.text = timeFormat.format(date)
        coordinatesText.text = String.format(
            "Широта: %.6f, Долгота: %.6f",
            location.latitude,
            location.longitude
        )
        
        // Обработчики кнопок
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        shareButton.setOnClickListener {
            // TODO: Реализовать функцию "Поделиться"
            dialog.dismiss()
        }
        
        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        binding.mainMapView.onResume()
        
        // Обновляем маркеры при возвращении на экран (из кэша быстро)
        binding.mainMapView.overlays.clear()
        loadSentPhotoMarkers()
        binding.mainMapView.invalidate()
    }
    
    // Метод для принудительного обновления данных
    private fun refreshData() {
        mainScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Обновляем данные...", Toast.LENGTH_SHORT).show()
                val result = com.example.lct_final.api.ImageCache.refresh(imageUploadManager)
                
                if (result.isSuccess) {
                    binding.mainMapView.overlays.clear()
                    loadSentPhotoMarkers()
                    binding.mainMapView.invalidate()
                    Toast.makeText(this@MainActivity, "✓ Данные обновлены", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "✗ Ошибка обновления", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "✗ Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.mainMapView.onPause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        ImageCropHelper.clearCache()
        ImageViewerHelper.clearTempImages(this)
    }
}
        