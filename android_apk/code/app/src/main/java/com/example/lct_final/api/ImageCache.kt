package com.example.lct_final.api

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Глобальный кэш для изображений
 * Загружает данные один раз и хранит в памяти
 */
object ImageCache {
    
    private val mutex = Mutex()
    private var cachedImages: List<ImageDetailResponse>? = null
    private var lastUpdateTime: Long = 0
    private const val CACHE_VALIDITY_MS = 5 * 60 * 1000 // 5 минут
    
    /**
     * Получить изображения из кэша или загрузить с сервера
     */
    suspend fun getImages(
        imageUploadManager: ImageUploadManager,
        forceRefresh: Boolean = false
    ): Result<List<ImageDetailResponse>> {
        mutex.withLock {
            // Проверяем, нужно ли обновить кэш
            val currentTime = System.currentTimeMillis()
            val isCacheValid = cachedImages != null && 
                              (currentTime - lastUpdateTime) < CACHE_VALIDITY_MS
            
            if (!forceRefresh && isCacheValid) {
                android.util.Log.d("ImageCache", "Используем кэшированные данные: ${cachedImages!!.size} изображений")
                return Result.success(cachedImages!!)
            }
            
            // Загружаем данные с сервера
            android.util.Log.d("ImageCache", "Загружаем данные с сервера...")
            val result = imageUploadManager.getImages(skip = 0, limit = 1000)
            
            if (result.isSuccess) {
                cachedImages = result.getOrNull()
                lastUpdateTime = currentTime
                android.util.Log.d("ImageCache", "✓ Кэш обновлен: ${cachedImages!!.size} изображений")
            }
            
            return result
        }
    }
    
    /**
     * Получить кэшированные изображения без загрузки
     */
    fun getCachedImages(): List<ImageDetailResponse>? {
        return cachedImages
    }
    
    /**
     * Очистить кэш
     */
    fun clearCache() {
        cachedImages = null
        lastUpdateTime = 0
        android.util.Log.d("ImageCache", "Кэш очищен")
    }
    
    /**
     * Обновить кэш принудительно
     */
    suspend fun refresh(imageUploadManager: ImageUploadManager): Result<List<ImageDetailResponse>> {
        return getImages(imageUploadManager, forceRefresh = true)
    }
}


