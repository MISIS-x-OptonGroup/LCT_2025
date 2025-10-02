package com.example.lct_final.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

object NetworkDiagnostics {
    private const val TAG = "NetworkDiagnostics"
    
    /**
     * Проверяет наличие интернет-соединения
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Проверяет доступность сервера по указанному хосту и порту
     */
    suspend fun isServerReachable(host: String, port: Int, timeoutMs: Int = 5000): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                Log.d(TAG, "Сервер $host:$port доступен")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Сервер $host:$port недоступен: ${e.message}")
            false
        }
    }
    
    /**
     * Выполняет полную диагностику подключения к API
     */
    suspend fun diagnoseConnection(context: Context): String {
        val result = StringBuilder()
        
        // 1. Проверка интернета
        val hasInternet = isNetworkAvailable(context)
        result.append("Интернет: ${if (hasInternet) "✓ Доступен" else "✗ Недоступен"}\n")
        
        if (!hasInternet) {
            result.append("\nПроверьте:\n")
            result.append("- WiFi или мобильные данные включены\n")
            result.append("- В настройках есть подключение к интернету\n")
            return result.toString()
        }
        
        // 2. Проверка доступности сервера
        val serverReachable = isServerReachable("36.34.82.242", 18087, 10000)
        result.append("Сервер 36.34.82.242:18087: ${if (serverReachable) "✓ Доступен" else "✗ Недоступен"}\n")
        
        if (!serverReachable) {
            result.append("\nВозможные причины:\n")
            result.append("- Сервер выключен или перезагружается\n")
            result.append("- Фаервол блокирует соединение\n")
            result.append("- Неверный IP-адрес или порт\n")
        }
        
        return result.toString()
    }
}



