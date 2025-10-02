package com.example.lct_final.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

/**
 * Утилита для работы с изображениями:
 * - Загрузка изображения по URL
 * - Вырезание фрагментов по bbox координатам
 */
object ImageCropHelper {
    
    /**
     * Загружает изображение по URL с учетом EXIF-ориентации
     */
    suspend fun loadBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("ImageCropHelper", "Загружаем изображение: $url")
            
            // Загружаем изображение
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            
            val inputStream: InputStream = connection.inputStream
            var bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) {
                android.util.Log.e("ImageCropHelper", "✗ Не удалось декодировать изображение")
                return@withContext null
            }
            
            android.util.Log.d("ImageCropHelper", "✓ Изображение загружено: ${bitmap.width}x${bitmap.height}")
            
            // Загружаем EXIF для проверки ориентации
            try {
                val exifConnection = URL(url).openConnection() as HttpURLConnection
                exifConnection.doInput = true
                exifConnection.connect()
                
                val exifInputStream = exifConnection.inputStream
                val exif = ExifInterface(exifInputStream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                exifInputStream.close()
                
                android.util.Log.d("ImageCropHelper", "EXIF ориентация: $orientation")
                
                // Применяем поворот, если необходимо
                bitmap = rotateBitmapByExif(bitmap, orientation)
                
                android.util.Log.d("ImageCropHelper", "✓ После коррекции ориентации: ${bitmap.width}x${bitmap.height}")
            } catch (e: Exception) {
                android.util.Log.w("ImageCropHelper", "Не удалось прочитать EXIF, используем изображение как есть", e)
            }
            
            bitmap
        } catch (e: Exception) {
            android.util.Log.e("ImageCropHelper", "✗ Ошибка загрузки изображения", e)
            null
        }
    }
    
    /**
     * Поворачивает Bitmap в соответствии с EXIF-ориентацией
     */
    private fun rotateBitmapByExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                android.util.Log.d("ImageCropHelper", "Поворачиваем на 90° по часовой стрелке")
                matrix.postRotate(90f)
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                android.util.Log.d("ImageCropHelper", "Поворачиваем на 180°")
                matrix.postRotate(180f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                android.util.Log.d("ImageCropHelper", "Поворачиваем на 270° по часовой стрелке")
                matrix.postRotate(270f)
            }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                android.util.Log.d("ImageCropHelper", "Отражаем горизонтально")
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                android.util.Log.d("ImageCropHelper", "Отражаем вертикально")
                matrix.postScale(1f, -1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                android.util.Log.d("ImageCropHelper", "Transpose (поворот + отражение)")
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                android.util.Log.d("ImageCropHelper", "Transverse (поворот + отражение)")
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> {
                // ORIENTATION_NORMAL или неизвестное значение - не нужно ничего делать
                return bitmap
            }
        }
        
        return try {
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0,
                bitmap.width, bitmap.height,
                matrix, true
            )
            
            // Освобождаем старый bitmap, если это новый объект
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            
            rotatedBitmap
        } catch (e: Exception) {
            android.util.Log.e("ImageCropHelper", "✗ Ошибка поворота изображения", e)
            bitmap
        }
    }
    
    /**
     * Вырезает фрагмент из изображения по bbox координатам
     * @param sourceBitmap Исходное изображение
     * @param bbox Координаты bbox [x1, y1, x2, y2]
     * @return Вырезанный фрагмент или null в случае ошибки
     */
    fun cropBitmapByBbox(sourceBitmap: Bitmap, bbox: List<Double>): Bitmap? {
        if (bbox.size != 4) {
            android.util.Log.e("ImageCropHelper", "Неверный формат bbox: ${bbox.size} элементов")
            return null
        }
        
        try {
            val width = sourceBitmap.width
            val height = sourceBitmap.height
            
            // Извлекаем координаты bbox
            val x1 = bbox[0].toInt()
            val y1 = bbox[1].toInt()
            val x2 = bbox[2].toInt()
            val y2 = bbox[3].toInt()
            
            // Вычисляем координаты и размеры для вырезания
            val cropX = max(0, min(x1, width - 1))
            val cropY = max(0, min(y1, height - 1))
            val cropWidth = max(1, min(x2 - x1, width - cropX))
            val cropHeight = max(1, min(y2 - y1, height - cropY))
            
            android.util.Log.d(
                "ImageCropHelper",
                "Вырезаем фрагмент: bbox[$x1,$y1,$x2,$y2] -> crop[$cropX,$cropY,$cropWidth,$cropHeight] из [$width,$height]"
            )
            
            // Проверяем корректность размеров
            if (cropWidth <= 0 || cropHeight <= 0) {
                android.util.Log.e("ImageCropHelper", "Некорректные размеры фрагмента")
                return null
            }
            
            // Вырезаем фрагмент
            return Bitmap.createBitmap(sourceBitmap, cropX, cropY, cropWidth, cropHeight)
        } catch (e: Exception) {
            android.util.Log.e("ImageCropHelper", "✗ Ошибка вырезания фрагмента", e)
            return null
        }
    }
    
    /**
     * Кэш загруженных изображений для избежания повторной загрузки
     */
    private val imageCache = mutableMapOf<Int, Bitmap?>()
    
    /**
     * Получает главное изображение (с кэшированием)
     */
    suspend fun getMainImage(imageId: Int, downloadUrl: String): Bitmap? {
        // Проверяем кэш
        if (imageCache.containsKey(imageId)) {
            android.util.Log.d("ImageCropHelper", "Используем закэшированное изображение #$imageId")
            return imageCache[imageId]
        }
        
        // Загружаем и кэшируем
        val bitmap = loadBitmapFromUrl(downloadUrl)
        imageCache[imageId] = bitmap
        return bitmap
    }
    
    /**
     * Очищает кэш изображений
     */
    fun clearCache() {
        imageCache.values.forEach { it?.recycle() }
        imageCache.clear()
        android.util.Log.d("ImageCropHelper", "Кэш изображений очищен")
    }
    
    /**
     * Преобразует внутренний URL MinIO в публичный
     */
    fun convertMinioUrlToPublic(s3Url: String): String {
        return s3Url.replace("http://minio:9000", "http://36.34.82.242:17897")
    }
}

