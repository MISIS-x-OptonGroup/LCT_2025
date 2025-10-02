package com.example.lct_final.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class ImageUploadManager(private val context: Context) {
    
    private val apiService = RetrofitClient.imageApiService
    private val TAG = "ImageUploadManager"
    
    /**
     * Загружает изображение на сервер
     * @param uri URI изображения
     * @param metadata Дополнительные метаданные (координаты, автор, время)
     */
    suspend fun uploadImage(uri: Uri, metadata: Map<String, String>? = null): Result<ImageUploadResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Начинаем загрузку изображения с URI: $uri")
            
            // Конвертируем URI в файл
            val file = uriToFile(uri) ?: return@withContext Result.failure(
                Exception("Не удалось преобразовать URI в файл")
            )
            
            Log.d(TAG, "Файл создан: ${file.absolutePath}, существует: ${file.exists()}, размер: ${file.length()} байт")
            
            // Получаем MIME тип
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            Log.d(TAG, "MIME тип: $mimeType")
            
            // Создаем RequestBody для файла
            val requestFile = file.asRequestBody(mimeType.toMediaTypeOrNull())
            
            // Создаем MultipartBody.Part
            val body = MultipartBody.Part.createFormData(
                "file",
                file.name,
                requestFile
            )
            
            Log.d(TAG, "Отправляем запрос на сервер для файла: ${file.name}")
            
            // Создаем RequestBody для метаданных в формате JSON как text/plain (бекенд ожидает именно так)
            val metadataBody = metadata?.let {
                val gson = Gson()
                val jsonString = gson.toJson(it)
                Log.d(TAG, "Передаём метаданные JSON: $jsonString")
                jsonString.toRequestBody("text/plain".toMediaTypeOrNull())
            }
            
            // Отправляем запрос
            val response = apiService.uploadImage(body, metadataBody)
            
            // Удаляем временный файл
            file.delete()
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Изображение успешно загружено. ID: ${response.body()!!.id}")
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Нет детальной информации"
                val errorMsg = "Ошибка загрузки: код ${response.code()}, сообщение: ${response.message()}, детали: $errorBody"
                Log.e(TAG, errorMsg)
                Result.failure(Exception("HTTP ${response.code()}: ${response.message()}"))
            }
        } catch (e: Exception) {
            val detailedMessage = "Исключение при загрузке: ${e.javaClass.simpleName} - ${e.message}"
            Log.e(TAG, detailedMessage, e)
            Result.failure(Exception("$detailedMessage (Проверьте подключение к интернету и доступность сервера)"))
        }
    }
    
    /**
     * Получает список всех изображений с сервера
     */
    suspend fun getImages(skip: Int = 0, limit: Int = 100): Result<List<ImageDetailResponse>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Получаем список изображений с сервера (skip: $skip, limit: $limit)")
            
            val response = apiService.getImages(skip, limit)
            
            if (response.isSuccessful && response.body() != null) {
                val images = response.body()!!
                Log.d(TAG, "Получено изображений: ${images.size}")
                Result.success(images)
            } else {
                val errorMsg = "Ошибка получения списка: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при получении списка изображений", e)
            Result.failure(e)
        }
    }
    
    /**
     * Получает детальную информацию об изображении
     */
    suspend fun getImageDetails(imageId: Int): Result<ImageDetailResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Получаем информацию об изображении ID: $imageId")
            
            val response = apiService.getImageDetails(imageId)
            
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Информация получена. Статус: ${response.body()!!.processingStatus}")
                Result.success(response.body()!!)
            } else {
                val errorMsg = "Ошибка получения информации: ${response.code()} - ${response.message()}"
                Log.e(TAG, errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при получении информации об изображении", e)
            Result.failure(e)
        }
    }
    
    /**
     * Мониторит статус обработки изображения до завершения
     * @param imageId ID изображения
     * @param maxAttempts Максимальное количество попыток (по умолчанию 60)
     * @param delayMillis Задержка между попытками в миллисекундах (по умолчанию 5000 = 5 секунд)
     * @param onStatusUpdate Callback для обновления статуса
     */
    suspend fun monitorProcessingStatus(
        imageId: Int,
        maxAttempts: Int = 60,
        delayMillis: Long = 5000,
        onStatusUpdate: (ImageDetailResponse) -> Unit
    ): Result<ImageDetailResponse> = withContext(Dispatchers.IO) {
        try {
            var attempts = 0
            
            while (attempts < maxAttempts) {
                val result = getImageDetails(imageId)
                
                if (result.isSuccess) {
                    val imageDetail = result.getOrNull()!!
                    
                    // Уведомляем об обновлении статуса
                    withContext(Dispatchers.Main) {
                        onStatusUpdate(imageDetail)
                    }
                    
                    // Проверяем статус обработки
                    when (imageDetail.processingStatus) {
                        "completed" -> {
                            Log.d(TAG, "Обработка завершена для изображения ID: $imageId")
                            return@withContext Result.success(imageDetail)
                        }
                        "failed", "error" -> {
                            Log.e(TAG, "Ошибка обработки изображения ID: $imageId")
                            return@withContext Result.failure(
                                Exception("Обработка изображения завершилась с ошибкой")
                            )
                        }
                        else -> {
                            // Статусы: "uploaded", "processing" и другие
                            Log.d(TAG, "Изображение ID: $imageId в статусе: ${imageDetail.processingStatus}. Попытка ${attempts + 1}/$maxAttempts")
                        }
                    }
                } else {
                    Log.e(TAG, "Не удалось получить статус изображения ID: $imageId")
                }
                
                attempts++
                delay(delayMillis)
            }
            
            Result.failure(Exception("Превышено время ожидания обработки изображения"))
        } catch (e: Exception) {
            Log.e(TAG, "Исключение при мониторинге статуса обработки", e)
            Result.failure(e)
        }
    }
    
    /**
     * Конвертирует URI в File с автоматическим сжатием до 2K разрешения
     */
    private fun uriToFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            
            // Получаем имя файла
            val fileName = getFileName(uri) ?: "temp_image_${System.currentTimeMillis()}.jpg"
            
            // Создаем временный файл для оригинала
            val tempOriginalFile = File(context.cacheDir, "original_$fileName")
            
            // Копируем данные оригинала
            FileOutputStream(tempOriginalFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            
            Log.d(TAG, "Оригинальный файл: ${tempOriginalFile.length()} байт")
            
            // Сжимаем изображение до 2K разрешения
            val compressedFile = compressImage(tempOriginalFile, fileName)
            
            // Удаляем временный оригинальный файл
            tempOriginalFile.delete()
            
            if (compressedFile != null) {
                Log.d(TAG, "Сжатый файл: ${compressedFile.length()} байт (экономия: ${((1 - compressedFile.length().toFloat() / tempOriginalFile.length()) * 100).toInt()}%)")
            }
            
            compressedFile
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка конвертации URI в File", e)
            null
        }
    }
    
    /**
     * Сжимает изображение до максимального разрешения 2000 пикселей
     * @param originalFile Оригинальный файл изображения
     * @param fileName Имя файла для сохранения
     * @return Сжатый файл или null в случае ошибки
     */
    private fun compressImage(originalFile: File, fileName: String): File? {
        return try {
            // Максимальное разрешение (по большей стороне)
            val maxDimension = 2000
            
            // Читаем EXIF для правильной ориентации
            val exif = ExifInterface(originalFile.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            // Получаем размеры оригинального изображения без полной загрузки в память
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(originalFile.absolutePath, options)
            
            val originalWidth = options.outWidth
            val originalHeight = options.outHeight
            
            Log.d(TAG, "Оригинальное разрешение: ${originalWidth}x${originalHeight}")
            
            // Вычисляем коэффициент сжатия
            val maxOriginalDimension = max(originalWidth, originalHeight)
            val scaleFactor = if (maxOriginalDimension > maxDimension) {
                maxOriginalDimension.toFloat() / maxDimension
            } else {
                1f // Не сжимаем, если изображение уже меньше 2K
            }
            
            // Если изображение уже меньше 2K, просто копируем
            if (scaleFactor == 1f) {
                Log.d(TAG, "Изображение не требует сжатия")
                val compressedFile = File(context.cacheDir, "compressed_$fileName")
                originalFile.copyTo(compressedFile, overwrite = true)
                return compressedFile
            }
            
            // Настройки для загрузки с уменьшением
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(originalWidth, originalHeight, maxDimension)
                inJustDecodeBounds = false
            }
            
            // Загружаем изображение
            var bitmap = BitmapFactory.decodeFile(originalFile.absolutePath, loadOptions)
            
            // Применяем правильную ориентацию
            bitmap = rotateImageIfRequired(bitmap, orientation)
            
            // Финальное масштабирование до точного размера
            val currentMaxDimension = max(bitmap.width, bitmap.height)
            if (currentMaxDimension > maxDimension) {
                val scale = maxDimension.toFloat() / currentMaxDimension
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                bitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            }
            
            Log.d(TAG, "Новое разрешение: ${bitmap.width}x${bitmap.height}")
            
            // Сохраняем сжатое изображение
            val compressedFile = File(context.cacheDir, "compressed_$fileName")
            FileOutputStream(compressedFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            
            // Освобождаем память
            bitmap.recycle()
            
            compressedFile
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка сжатия изображения", e)
            null
        }
    }
    
    /**
     * Вычисляет оптимальный inSampleSize для загрузки изображения
     */
    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        val maxOriginalDimension = max(width, height)
        
        if (maxOriginalDimension > maxDimension) {
            val halfWidth = width / 2
            val halfHeight = height / 2
            
            while ((halfWidth / inSampleSize) >= maxDimension && (halfHeight / inSampleSize) >= maxDimension) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Поворачивает изображение согласно EXIF ориентации
     */
    private fun rotateImageIfRequired(bitmap: Bitmap, orientation: Int): Bitmap {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                rotateImage(bitmap, 90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                rotateImage(bitmap, 180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                rotateImage(bitmap, 270f)
            }
            else -> bitmap
        }
    }
    
    /**
     * Поворачивает изображение на указанный угол
     */
    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        bitmap.recycle()
        return rotatedBitmap
    }
    
    /**
     * Получает имя файла из URI
     */
    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }
            }
        }
        
        if (fileName == null) {
            fileName = uri.path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
        }
        
        return fileName
    }
}


