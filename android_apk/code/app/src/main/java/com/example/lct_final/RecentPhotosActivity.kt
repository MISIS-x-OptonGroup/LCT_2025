package com.example.lct_final

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.example.lct_final.api.ImageCropHelper
import com.example.lct_final.api.ImageDetailResponse
import com.example.lct_final.api.ImageUploadManager
import com.example.lct_final.databinding.ActivityRecentPhotosBinding
import com.example.lct_final.utils.TreeDataFormatter
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentPhotosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecentPhotosBinding
    private lateinit var imageUploadManager: ImageUploadManager
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecentPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUploadManager = ImageUploadManager(this)

        // Настройка кнопки закрытия
        binding.closeButton.setOnClickListener {
            finish()
        }

        // Настройка кнопки обновления
        binding.refreshButton.setOnClickListener {
            // Анимация кнопки
            it.animate()
                .rotation(360f)
                .setDuration(500)
                .start()
            
            loadRecentPhotos()
        }

        // Настройка RecyclerView
        binding.photosRecyclerView.layoutManager = LinearLayoutManager(this)

        // Загрузка данных
        loadRecentPhotos()
    }

    override fun onResume() {
        super.onResume()
        // Обновляем данные при каждом заходе на экран
        loadRecentPhotos()
    }

    private fun loadRecentPhotos() {
        android.util.Log.d("RecentPhotosActivity", "🔄 Начинаем загрузку последних фото...")
        binding.progressBar.visibility = View.VISIBLE
        binding.emptyStateLayout.visibility = View.GONE

        mainScope.launch {
            try {
                android.util.Log.d("RecentPhotosActivity", "📡 Запрашиваем данные с бекенда (принудительное обновление)...")
                // Принудительно обновляем кэш для получения свежих данных
                val result = com.example.lct_final.api.ImageCache.refresh(imageUploadManager)

                if (result.isSuccess) {
                    val images = result.getOrNull() ?: emptyList()
                    android.util.Log.d("RecentPhotosActivity", "✓ Загружено изображений с бекенда: ${images.size}")

                    // Фильтруем только обработанные изображения, сортируем по времени убывания и берем последние 100
                    val processedImages = images
                        .filter { it.processingStatus == "completed" }
                        .sortedByDescending { it.createdAt }  // Сортировка по времени в убывающем порядке (новые сверху)
                        .take(100)  // Берем только последние 100

                    android.util.Log.d("RecentPhotosActivity", "✓ Отфильтровано обработанных изображений: ${processedImages.size}")

                    binding.progressBar.visibility = View.GONE

                    if (processedImages.isEmpty()) {
                        android.util.Log.d("RecentPhotosActivity", "⚠️ Нет обработанных изображений для отображения")
                        binding.emptyStateLayout.visibility = View.VISIBLE
                    } else {
                        android.util.Log.d("RecentPhotosActivity", "✅ Отображаем ${processedImages.size} обработанных изображений")
                        binding.photosRecyclerView.adapter = RecentPhotosAdapter(processedImages) { photo ->
                            showPhotoDetails(photo)
                        }
                    }
                } else {
                    binding.progressBar.visibility = View.GONE
                    binding.emptyStateLayout.visibility = View.VISIBLE
                    val errorMsg = result.exceptionOrNull()?.message ?: "Неизвестная ошибка"
                    Toast.makeText(this@RecentPhotosActivity, "Ошибка: $errorMsg", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.emptyStateLayout.visibility = View.VISIBLE
                android.util.Log.e("RecentPhotosActivity", "✗ Ошибка загрузки", e)
                Toast.makeText(this@RecentPhotosActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPhotoDetails(image: ImageDetailResponse) {
        // Получаем координаты (если есть)
        val location = try {
            if (!image.location.isNullOrEmpty()) {
                val coordinates = image.location.split(",")
                if (coordinates.size == 2) {
                    val latitude = coordinates[0].trim().toDouble()
                    val longitude = coordinates[1].trim().toDouble()
                    GeoPoint(latitude, longitude)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        showServerPhotoInfoDialog(image, location)
    }

    private fun showServerPhotoInfoDialog(image: ImageDetailResponse, location: GeoPoint?) {
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
        android.util.Log.d("RecentPhotosActivity", "📸 Загружаем изображение через download API:")
        android.util.Log.d("RecentPhotosActivity", "  ID изображения: ${image.id}")

        // Загружаем download URL через API
        mainScope.launch {
            try {
                val apiService = com.example.lct_final.api.RetrofitClient.imageApiService
                val downloadResponse = apiService.getImageDownloadUrl(image.id, 3600)

                if (downloadResponse.isSuccessful && downloadResponse.body() != null) {
                    val downloadData = downloadResponse.body()!!
                    val publicUrl = ImageCropHelper.convertMinioUrlToPublic(downloadData.downloadUrl)

                    android.util.Log.d("RecentPhotosActivity", "✓ Получен download URL: $publicUrl")

                    // Загружаем главное изображение через Glide
                    Glide.with(this@RecentPhotosActivity)
                        .load(publicUrl)
                        .centerCrop()
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .error(R.drawable.ic_launcher_foreground)
                        .into(photoImageView)
                    
                    // Добавляем обработчик клика для полноэкранного просмотра
                    photoImageView.setOnClickListener {
                        ImageViewerHelper.openImageFullscreen(this@RecentPhotosActivity, publicUrl)
                    }

                    // Загружаем главное изображение для вырезания фрагментов
                    val mainBitmap = ImageCropHelper.getMainImage(image.id, publicUrl)
                    if (mainBitmap != null) {
                        android.util.Log.d("RecentPhotosActivity", "✓ Главное изображение загружено: ${mainBitmap.width}x${mainBitmap.height}")

                        // Отображаем фрагменты после загрузки главного изображения
                        displayFragments(dialog, image, mainBitmap)
                    } else {
                        android.util.Log.e("RecentPhotosActivity", "✗ Не удалось загрузить главное изображение для вырезания фрагментов")
                    }
                } else {
                    android.util.Log.e("RecentPhotosActivity", "✗ Ошибка получения download URL: ${downloadResponse.code()}")
                    Toast.makeText(this@RecentPhotosActivity, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("RecentPhotosActivity", "✗ Ошибка загрузки изображения", e)
                Toast.makeText(this@RecentPhotosActivity, "Ошибка: ${e.message}", Toast.LENGTH_SHORT).show()
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
        coordinatesText.text = if (location != null) {
            String.format(
                "Широта: %.6f, Долгота: %.6f",
                location.latitude,
                location.longitude
            )
        } else {
            "Координаты отсутствуют"
        }

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
    private fun displayFragments(dialog: Dialog, image: ImageDetailResponse, mainBitmap: Bitmap) {
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
                    android.util.Log.e("RecentPhotosActivity", "Не удалось вырезать фрагмент для объекта: ${obj.label}")
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
        android.util.Log.d("RecentPhotosActivity", "Отображаем фрагмент: ${obj.label} (${fragmentBitmap.width}x${fragmentBitmap.height})")

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
            val filteredDiseases = diseases.filter { (it.likelihood ?: 0) >= 10 }
            if (filteredDiseases.isNotEmpty()) {
                detailDiseasesSection.visibility = android.view.View.VISIBLE
                val diseasesText = filteredDiseases.joinToString("\n") { disease ->
                    val name = disease.nameRu ?: "Неизвестно"
                    val likelihood = disease.likelihood ?: 0
                    val evidence = if (!disease.evidence.isNullOrEmpty()) {
                        "\n  Признаки: ${disease.evidence.joinToString(", ")}"
                    } else ""
                    "• $name ($likelihood%)$evidence"
                }
                detailDiseasesText.text = diseasesText
            }
        }

        // Вредители
        obj.description?.objectInfo?.condition?.pests?.let { pests ->
            val filteredPests = pests.filter { (it.likelihood ?: 0) >= 10 }
            if (filteredPests.isNotEmpty()) {
                detailPestsSection.visibility = android.view.View.VISIBLE
                val pestsText = filteredPests.joinToString("\n") { pest ->
                    val name = pest.nameRu ?: "Неизвестно"
                    val likelihood = pest.likelihood ?: 0
                    val evidence = if (!pest.evidence.isNullOrEmpty()) {
                        "\n  Признаки: ${pest.evidence.joinToString(", ")}"
                    } else ""
                    "• $name ($likelihood%)$evidence"
                }
                detailPestsText.text = pestsText
            }
        }

        // Состояние
        obj.description?.objectInfo?.condition?.let { condition ->
            val conditionItems = mutableListOf<String>()

            condition.dryBranchesPct?.let { pct ->
                if (pct > 0) conditionItems.add("Сухие ветки: $pct%")
            }

            if (condition.trunkDecay?.present == true) {
                conditionItems.add("Гниль ствола обнаружена")
            }
            if (condition.cavities?.present == true) {
                conditionItems.add("Дупла обнаружены")
            }
            if (condition.cracks?.present == true) {
                conditionItems.add("Трещины обнаружены")
            }
            if (condition.barkDetachment?.present == true) {
                conditionItems.add("Отслоение коры")
            }
            if (condition.trunkDamage?.present == true) {
                conditionItems.add("Повреждения ствола")
            }
            if (condition.crownDamage?.present == true) {
                conditionItems.add("Повреждения кроны")
            }
            if (condition.fruitingBodies?.present == true) {
                conditionItems.add("Плодовые тела грибов")
            }

            if (conditionItems.isNotEmpty()) {
                detailConditionSection.visibility = android.view.View.VISIBLE
                detailConditionText.text = conditionItems.joinToString("\n• ", "• ")
            }
        }

        // Риск
        obj.description?.objectInfo?.risk?.let { risk ->
            if (!risk.level.isNullOrEmpty()) {
                detailRiskSection.visibility = android.view.View.VISIBLE

                val riskEmoji = when (risk.level.lowercase()) {
                    "low" -> "✅"
                    "medium" -> "⚠️"
                    "high" -> "🚨"
                    else -> "ℹ️"
                }
                val riskLevel = when (risk.level.lowercase()) {
                    "low" -> "Низкий"
                    "medium" -> "Средний"
                    "high" -> "Высокий"
                    else -> risk.level
                }
                detailRiskLevelText.text = "$riskEmoji Уровень риска: $riskLevel"

                val color = when (risk.level.lowercase()) {
                    "low" -> android.graphics.Color.parseColor("#81C784")
                    "medium" -> android.graphics.Color.parseColor("#FFB74D")
                    "high" -> android.graphics.Color.parseColor("#E57373")
                    else -> android.graphics.Color.parseColor("#A0B8A0")
                }
                detailRiskLevelText.setTextColor(color)

                if (!risk.drivers.isNullOrEmpty()) {
                    detailRiskDriversText.visibility = android.view.View.VISIBLE
                    detailRiskDriversText.text = risk.drivers.joinToString("\n• ", "• ")
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
            android.util.Log.w("RecentPhotosActivity", "⚠️ Description == null для объекта ${obj.label}")
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
                    android.util.Log.w("RecentPhotosActivity", "Ошибка форматирования объекта ${obj.label}", e)
                }
            }
        }.joinToString("\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
        ImageViewerHelper.clearTempImages(this)
    }
}


