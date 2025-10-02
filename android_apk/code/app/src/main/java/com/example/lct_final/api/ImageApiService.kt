package com.example.lct_final.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ImageApiService {
    
    @Multipart
    @POST("/api/v1/images/upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: RequestBody? = null
    ): Response<ImageUploadResponse>
    
    @GET("/api/v1/images/{image_id}")
    suspend fun getImageDetails(
        @Path("image_id") imageId: Int
    ): Response<ImageDetailResponse>
    
    @GET("/api/v1/images/")
    suspend fun getImages(
        @Query("skip") skip: Int = 0,
        @Query("limit") limit: Int = 100
    ): Response<List<ImageDetailResponse>>
    
    @GET("/api/v1/images/{image_id}/download")
    suspend fun getImageDownloadUrl(
        @Path("image_id") imageId: Int,
        @Query("expires_in") expiresIn: Int = 3600
    ): Response<ImageDownloadResponse>
}


