package com.example.lct_final.api

import com.google.gson.*
import java.lang.reflect.Type

/**
 * Кастомный десериализатор для DetectedObject
 * Обрабатывает случаи, когда description приходит как строка вместо объекта
 */
class DetectedObjectDeserializer : JsonDeserializer<DetectedObject> {
    
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): DetectedObject {
        val jsonObject = json.asJsonObject
        
        // Парсим основные поля
        val bbox = context.deserialize<List<Double>>(
            jsonObject.get("bbox"),
            object : com.google.gson.reflect.TypeToken<List<Double>>() {}.type
        )
        
        val label = jsonObject.get("label")?.asString ?: ""
        val confidence = jsonObject.get("confidence")?.asDouble ?: 0.0
        val fragmentUrl = jsonObject.get("fragment_url")?.takeIf { !it.isJsonNull }?.asString
        
        // Пытаемся парсить description как объект, если не получается - игнорируем
        val description = try {
            val descElement = jsonObject.get("description")
            android.util.Log.d("DetectedObjectDeserializer", "Парсим description для объекта '$label'")
            android.util.Log.d("DetectedObjectDeserializer", "descElement: $descElement")
            android.util.Log.d("DetectedObjectDeserializer", "descElement == null: ${descElement == null}")
            android.util.Log.d("DetectedObjectDeserializer", "descElement.isJsonNull: ${descElement?.isJsonNull}")
            android.util.Log.d("DetectedObjectDeserializer", "descElement.isJsonObject: ${descElement?.isJsonObject}")
            
            when {
                descElement == null || descElement.isJsonNull -> {
                    android.util.Log.w("DetectedObjectDeserializer", "description is null or JsonNull")
                    null
                }
                descElement.isJsonObject -> {
                    android.util.Log.d("DetectedObjectDeserializer", "Deserializing description as ObjectDescription")
                    val result = context.deserialize<ObjectDescription>(descElement, ObjectDescription::class.java)
                    android.util.Log.d("DetectedObjectDeserializer", "✓ Description parsed successfully: $result")
                    result
                }
                else -> {
                    // Если это строка или что-то другое - игнорируем
                    android.util.Log.w("DetectedObjectDeserializer", "description is not an object (type: ${descElement.javaClass.simpleName}), content: $descElement")
                    null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DetectedObjectDeserializer", "✗ Failed to parse description: ${e.message}", e)
            e.printStackTrace()
            null
        }
        
        return DetectedObject(
            bbox = bbox,
            label = label,
            confidence = confidence,
            fragmentUrl = fragmentUrl,
            description = description
        )
    }
}


