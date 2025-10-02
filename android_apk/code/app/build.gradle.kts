plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.lct_final"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.lct_final"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Библиотека для размытия изображений (работает на всех версиях Android)
    implementation("jp.wasabeef:blurry:4.0.1")
    
    // OpenStreetMap для отображения карт
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    
    // Preferences для конфигурации osmdroid
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // CameraX для работы с камерой
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    
    // Google Location Services для геолокации
    implementation("com.google.android.gms:play-services-location:21.1.0")
    
    // Glide для оптимизированной загрузки изображений
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // PhotoView для зума и просмотра изображений
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    
    // ExifInterface для чтения GPS-данных из EXIF
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    
    // Retrofit для работы с REST API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // OkHttp для HTTP запросов и логирования
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // Gson для сериализации JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Kotlin Coroutines для асинхронных операций
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}