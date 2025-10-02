package com.example.lct_final

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.lct_final.api.ImageCropHelper
import com.example.lct_final.api.ImageDetailResponse
import com.example.lct_final.api.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class RecentPhotosAdapter(
    private val photos: List<ImageDetailResponse>,
    private val onPhotoClick: (ImageDetailResponse) -> Unit
) : RecyclerView.Adapter<RecentPhotosAdapter.PhotoViewHolder>() {

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val photoImageView: ImageView = itemView.findViewById(R.id.photoImageView)
        val photoTitleText: TextView = itemView.findViewById(R.id.photoTitleText)
        val photoDateText: TextView = itemView.findViewById(R.id.photoDateText)
        val photoObjectsText: TextView = itemView.findViewById(R.id.photoObjectsText)
        val photoLocationText: TextView = itemView.findViewById(R.id.photoLocationText)

        fun bind(image: ImageDetailResponse) {
            // Заголовок
            val objectsCount = image.detectedObjects?.size ?: 0
            photoTitleText.text = if (objectsCount > 0) {
                "Найдено объектов: $objectsCount"
            } else {
                "Изображение #${image.id}"
            }

            // Дата
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val createdDate = try {
                val parsedDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).parse(image.createdAt.take(19))
                // Прибавляем 3 часа (3 * 60 * 60 * 1000 = 10800000 мс)
                java.util.Date(parsedDate.time + 10800000L)
            } catch (e: Exception) {
                java.util.Date()
            }
            photoDateText.text = dateFormat.format(createdDate)

            // Количество объектов
            photoObjectsText.text = if (objectsCount > 0) {
                "Найдено объектов: $objectsCount"
            } else {
                "Объекты не обнаружены"
            }

            // Координаты
            photoLocationText.text = if (!image.location.isNullOrEmpty()) {
                try {
                    val coordinates = image.location.split(",")
                    if (coordinates.size == 2) {
                        val lat = coordinates[0].trim().toDouble()
                        val lon = coordinates[1].trim().toDouble()
                        String.format("%.4f, %.4f", lat, lon)
                    } else {
                        image.location
                    }
                } catch (e: Exception) {
                    image.location
                }
            } else {
                "Координаты отсутствуют"
            }

            // Загружаем изображение
            loadImageThumbnail(image, photoImageView)

            // Обработчик клика
            itemView.setOnClickListener {
                onPhotoClick(image)
            }
        }

        private fun loadImageThumbnail(image: ImageDetailResponse, imageView: ImageView) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val apiService = RetrofitClient.imageApiService
                    val downloadResponse = apiService.getImageDownloadUrl(image.id, 3600)

                    if (downloadResponse.isSuccessful && downloadResponse.body() != null) {
                        val downloadData = downloadResponse.body()!!
                        val publicUrl = ImageCropHelper.convertMinioUrlToPublic(downloadData.downloadUrl)

                        Glide.with(itemView.context)
                            .load(publicUrl)
                            .centerCrop()
                            .placeholder(R.drawable.ic_launcher_foreground)
                            .error(R.drawable.ic_launcher_foreground)
                            .into(imageView)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RecentPhotosAdapter", "Ошибка загрузки изображения", e)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size
}

