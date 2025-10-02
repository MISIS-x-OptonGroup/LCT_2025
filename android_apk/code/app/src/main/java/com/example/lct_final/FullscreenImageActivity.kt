package com.example.lct_final

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.lct_final.databinding.ActivityFullscreenImageBinding
import java.io.File

class FullscreenImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenImageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Обработчики закрытия
        binding.closeButton.setOnClickListener {
            finish()
        }
        binding.closeButtonCard.setOnClickListener {
            finish()
        }

        // Получаем данные для отображения
        val imageUrl = intent.getStringExtra("IMAGE_URL")
        val imagePath = intent.getStringExtra("IMAGE_PATH")

        if (imageUrl != null) {
            loadImageFromUrl(imageUrl)
        } else if (imagePath != null) {
            loadImageFromPath(imagePath)
        } else {
            Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadImageFromUrl(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        Glide.with(this)
            .load(url)
            .fitCenter()
            .error(R.drawable.ic_launcher_foreground)
            .into(binding.fullscreenImageView)
        
        binding.progressBar.visibility = View.GONE
    }

    private fun loadImageFromPath(path: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        val file = File(path)
        if (file.exists()) {
            Glide.with(this)
                .load(file)
                .fitCenter()
                .error(R.drawable.ic_launcher_foreground)
                .into(binding.fullscreenImageView)
        } else {
            Toast.makeText(this, "Файл не найден", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        binding.progressBar.visibility = View.GONE
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Удаляем временный файл если он был создан
        intent.getStringExtra("IMAGE_PATH")?.let { path ->
            try {
                val file = java.io.File(path)
                if (file.exists() && file.name.startsWith("temp_fullscreen_")) {
                    file.delete()
                }
            } catch (e: Exception) {
                android.util.Log.e("FullscreenImageActivity", "Ошибка удаления временного файла", e)
            }
        }
    }
}

