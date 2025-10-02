package com.example.lct_final

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object ImageViewerHelper {
    
    /**
     * Открывает изображение в полноэкранном режиме по URL
     */
    fun openImageFullscreen(context: Context, imageUrl: String) {
        val intent = Intent(context, FullscreenImageActivity::class.java)
        intent.putExtra("IMAGE_URL", imageUrl)
        context.startActivity(intent)
    }
    
    /**
     * Открывает Bitmap в полноэкранном режиме (сохраняет во временный файл)
     */
    fun openBitmapFullscreen(context: Context, bitmap: Bitmap) {
        try {
            // Сохраняем bitmap во временный файл
            val tempFile = File(context.cacheDir, "temp_fullscreen_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            
            val intent = Intent(context, FullscreenImageActivity::class.java)
            intent.putExtra("IMAGE_PATH", tempFile.absolutePath)
            context.startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ImageViewerHelper", "Ошибка сохранения bitmap", e)
        }
    }
    
    /**
     * Очищает временные файлы изображений
     */
    fun clearTempImages(context: Context) {
        try {
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_fullscreen_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageViewerHelper", "Ошибка очистки временных файлов", e)
        }
    }
}


