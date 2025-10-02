package com.example.lct_final

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.location.LocationListener
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.lct_final.databinding.ActivityCameraBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.app.Activity
import android.content.IntentSender
import android.os.Looper
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.SettingsClient
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import android.os.Handler
import kotlinx.coroutines.suspendCancellableCoroutine
import android.view.animation.AnimationUtils

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    
    private var currentLocation: Location? = null
    private lateinit var locationManager: LocationManager
    private var gpsLocationListener: LocationListener? = null
    private var locationJob: Job? = null
    private var bestLocation: Location? = null
    private val locationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val startResolutionLauncher = registerForActivityResult(StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startLocationAcquisition()
        } else {
            Toast.makeText(this, "Геолокация не включена", Toast.LENGTH_LONG).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            startCamera()
            startLocationAcquisition()
        } else {
            Toast.makeText(this, "Необходимы разрешения для работы камеры", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        
        // Настройка запроса локации в реальном времени
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000 // Обновление каждую секунду
        ).apply {
            setMinUpdateIntervalMillis(500) // Минимальный интервал 0.5 секунды
            setMaxUpdateDelayMillis(2000)
        }.build()
        
        // Callback для получения обновлений локации
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location
                    updateLocationDisplay()
                }
            }
        }

        // Инициализация отображения GPS
        updateLocationDisplay()

        // Проверка и запрос разрешений
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Кнопка для съемки фото с анимацией
        binding.captureButton.setOnClickListener {
            // Анимация нажатия
            it.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(100)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()
            
            takePhoto()
        }

        // Кнопка возврата
        binding.backButton.setOnClickListener {
            finish()
        }

        // Клик на GPS блок для повторного определения координат
        binding.gpsContainer.setOnClickListener {
            // Анимация нажатия
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
                }
                .start()
            
            if (allPermissionsGranted()) {
                Toast.makeText(this, "🔄 Обновление координат...", Toast.LENGTH_SHORT).show()
                startLocationAcquisition()
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        
        // Применяем анимации к элементам интерфейса
        applyUIAnimations()
    }
    
    private fun applyUIAnimations() {
        // Плавное появление GPS контейнера
        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
        binding.gpsContainer.startAnimation(fadeIn)
        binding.backButtonCard.startAnimation(fadeIn)
        
        // Пульсирующая анимация для подсказки обновления
        val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        binding.gpsHint.startAnimation(pulseAnimation)
        
        // Анимация появления кнопки захвата снизу
        binding.captureButton.apply {
            alpha = 0f
            translationY = 200f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(600)
                .setStartDelay(200)
                .start()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Ошибка запуска камеры", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // Получение текущего времени
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentDateTime = dateFormat.format(Date())
        
        // Получение имени пользователя устройства
        val author = Build.MODEL // Можно заменить на имя пользователя из настроек

        // Создание имени файла с временной меткой
        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.getDefault())
            .format(System.currentTimeMillis())
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LCT_final")
            }
        }

        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            .build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(
                        baseContext,
                        "Ошибка сохранения фото: ${exc.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Сохраняем метаданные в SharedPreferences
                    output.savedUri?.let { uri ->
                        val prefs = getSharedPreferences("photo_metadata", MODE_PRIVATE)
                        val metadata = buildMetadata(author, currentDateTime)
                        prefs.edit().putString(uri.toString(), metadata).apply()
                        
                        // Сохраняем координаты для отправки на бэкенд (используются в GalleryActivity)
                        currentLocation?.let { location ->
                            val locationPrefs = getSharedPreferences("photo_locations", MODE_PRIVATE)
                            val locationString = "${location.latitude},${location.longitude}"
                            locationPrefs.edit().putString(uri.toString(), locationString).apply()
                        }
                    }
                    
                    val msg = "Фото сохранено!\n" +
                            "Автор: $author\n" +
                            "Время: $currentDateTime\n" +
                            "Координаты: ${getLocationString()}"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun buildMetadata(author: String, dateTime: String): String {
        return "Автор: $author | Дата и время: $dateTime | Координаты: ${getLocationString()}"
    }

    private fun getLocationString(): String {
        return currentLocation?.let {
            "Широта: ${it.latitude}, Долгота: ${it.longitude}"
        } ?: "Геоданные недоступны"
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun allPermissionsGranted() = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startLocationAcquisition() {
        locationJob?.cancel()
        bestLocation = null
        // Проверка разрешений
        if (!allPermissionsGranted()) {
            requestPermissions()
            return
        }
        // Проверка настроек геолокации
        val settingsClient: SettingsClient = LocationServices.getSettingsClient(this)
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                // Всё ок, запускаем сбор локации
                locationJob = locationScope.launch {
                    binding.locationTextView.text = "GPS: Определяю..."
                    Toast.makeText(this@CameraActivity, "Определяю местоположение...", Toast.LENGTH_SHORT).show()
                    val location = getBestLocationCoroutine()
                    if (location != null) {
                        currentLocation = location
                        updateLocationDisplay()
                    } else {
                        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                        
                        val msg = if (!gpsEnabled && !networkEnabled) {
                            "✗ GPS и сеть выключены. Включите геолокацию в настройках!"
                        } else if (!gpsEnabled) {
                            "✗ GPS выключен. Для точных координат включите GPS в настройках!"
                        } else {
                            "✗ Не удалось получить координаты. Попробуйте выйти на улицу или к окну."
                        }
                        
                        binding.locationTextView.text = "GPS: ✗ Недоступен"
                        Toast.makeText(this@CameraActivity, msg, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .addOnFailureListener { e ->
                if (e is com.google.android.gms.common.api.ResolvableApiException) {
                    val intentSenderRequest = IntentSenderRequest.Builder(e.resolution).build()
                    startResolutionLauncher.launch(intentSenderRequest)
                } else {
                    Toast.makeText(this, "Геолокация выключена", Toast.LENGTH_LONG).show()
                }
            }
    }

    private suspend fun getBestLocationCoroutine(): Location? = suspendCancellableCoroutine { cont ->
        var best: Location? = null
        var finished = false
        val handler = Handler(Looper.getMainLooper())
        // Объявляем переменные заранее, чтобы были доступны для removeLocationUpdates
        lateinit var fusedCallback: LocationCallback
        lateinit var gpsListener: android.location.LocationListener
        lateinit var timeoutRunnable: Runnable
        
        fun finish(reason: String) {
            if (finished) return
            finished = true
            fusedLocationClient.removeLocationUpdates(fusedCallback)
            try { locationManager.removeUpdates(gpsListener) } catch (_: Exception) {}
            handler.removeCallbacks(timeoutRunnable)
            
            // Показываем сообщение о качестве координат
            val accuracyMsg = best?.let { 
                "✓ Координаты получены (точность: ${it.accuracy.toInt()}м)"
            } ?: "✗ Координаты не получены"
            runOnUiThread {
                Toast.makeText(this@CameraActivity, accuracyMsg, Toast.LENGTH_SHORT).show()
            }
            
            cont.resume(best)
        }
        
        // 0) Сначала пробуем получить последнее известное местоположение
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                    if (lastLocation != null && !finished) {
                        // Проверяем, не слишком ли старое местоположение (не старше 5 минут)
                        val locationAge = System.currentTimeMillis() - lastLocation.time
                        if (locationAge < 5 * 60 * 1000) { // 5 минут
                            best = lastLocation
                            // Если точность приемлемая, сразу возвращаем
                            if (lastLocation.accuracy <= 50f) {
                                finish("last-known-good")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибки получения последнего местоположения
            }
        }
        
        // 1) Подписка Fused (локальный колбэк!)
        fusedCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (loc in result.locations) {
                    if (loc.accuracy > 0 && (best == null || loc.accuracy < best!!.accuracy)) {
                        best = loc
                        // Принимаем координаты с точностью до 50 метров
                        if (loc.accuracy <= 50f) finish("fused-good")
                    }
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(
            locationRequest, fusedCallback, Looper.getMainLooper()
        )
        
        // 2) Подписка GPS (только здесь)
        gpsListener = object : android.location.LocationListener {
            override fun onLocationChanged(loc: Location) {
                if (loc.accuracy > 0 && (best == null || loc.accuracy < best!!.accuracy)) {
                    best = loc
                    // Принимаем координаты с точностью до 50 метров
                    if (loc.accuracy <= 50f) finish("gps-good")
                }
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 0f, gpsListener, Looper.getMainLooper()
                )
            } else {
                // GPS выключен
                runOnUiThread {
                    Toast.makeText(this@CameraActivity, "⚠ GPS выключен, используются менее точные координаты", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        // 3) Таймаут - уменьшили до 30 секунд
        timeoutRunnable = Runnable { finish("timeout") }
        handler.postDelayed(timeoutRunnable, 30_000L) // 30 секунд вместо 120
        
        // 4) Отмена корутины: чисто снимаем слушателей
        cont.invokeOnCancellation {
            try { fusedLocationClient.removeLocationUpdates(fusedCallback) } catch (_: Exception) {}
            try { locationManager.removeUpdates(gpsListener) } catch (_: Exception) {}
            handler.removeCallbacks(timeoutRunnable)
        }
    }

    private fun updateLocationDisplay() {
        runOnUiThread {
            binding.locationTextView.text = currentLocation?.let { location ->
                val accuracy = location.accuracy.toInt()
                val lat = String.format("%.5f", location.latitude)
                val lon = String.format("%.5f", location.longitude)
                "GPS: $lat, $lon\n(±${accuracy}м)"
            } ?: "GPS: Определение..."
        }
    }

    override fun onPause() {
        super.onPause()
        locationJob?.cancel()
    }
    
    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
            startLocationAcquisition()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        locationJob?.cancel()
        cameraExecutor.shutdown()
    }
}
