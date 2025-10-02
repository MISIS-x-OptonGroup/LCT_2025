package com.example.lct_final.api

import java.net.URLEncoder

object ImageUrlHelper {
    
    private const val MINIO_PUBLIC_URL = "http://36.34.82.242:17904/api/v1/buckets"
    
    /**
     * Преобразует внутренний S3 URL в публичный MinIO URL
     * Из: http://minio:9000/lct-backend1-images/images/filename.jpg
     * В: http://36.34.82.242:17904/api/v1/buckets/lct-backend1-images/objects/download?prefix=images%2Ffilename.jpg
     */
    fun convertS3UrlToPublic(s3Url: String?): String? {
        if (s3Url.isNullOrEmpty()) return null
        
        return try {
            android.util.Log.d("ImageUrlHelper", "Преобразуем URL: $s3Url")
            
            // Парсим URL вида: http://minio:9000/bucket-name/path/to/file.jpg
            val regex = Regex("http://minio:9000/([^/]+)/(.+)")
            val matchResult = regex.find(s3Url)
            
            if (matchResult != null) {
                val bucketName = matchResult.groupValues[1]
                val objectPath = matchResult.groupValues[2]
                
                // Кодируем путь для URL: заменяем / на %2F
                val encodedPath = URLEncoder.encode(objectPath, "UTF-8")
                
                // Формируем публичный URL (БЕЗ preview и version_id)
                val publicUrl = "$MINIO_PUBLIC_URL/$bucketName/objects/download?prefix=$encodedPath"
                android.util.Log.d("ImageUrlHelper", "✓ Преобразовано в: $publicUrl")
                publicUrl
            } else {
                android.util.Log.w("ImageUrlHelper", "URL не соответствует паттерну MinIO, возвращаем как есть")
                s3Url
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageUrlHelper", "✗ Ошибка преобразования URL: $s3Url", e)
            s3Url
        }
    }
    
    /**
     * Преобразует fragment_url из внутреннего в публичный
     */
    fun convertFragmentUrlToPublic(fragmentUrl: String?): String? {
        return convertS3UrlToPublic(fragmentUrl)
    }
}

