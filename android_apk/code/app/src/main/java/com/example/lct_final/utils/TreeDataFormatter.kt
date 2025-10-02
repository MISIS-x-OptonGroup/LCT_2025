package com.example.lct_final.utils

import com.example.lct_final.api.Condition
import com.example.lct_final.api.DiseaseOrPest
import com.example.lct_final.api.Risk

object TreeDataFormatter {
    
    fun formatDiseases(diseases: List<DiseaseOrPest>, minLikelihood: Int = 10): String {
        val filteredDiseases = diseases.filter { (it.likelihood ?: 0) >= minLikelihood }
        if (filteredDiseases.isEmpty()) return ""
        
        return filteredDiseases.joinToString("\n") { disease ->
            val name = disease.nameRu ?: "Неизвестно"
            val likelihood = disease.likelihood ?: 0
            val typeStr = if (!disease.type.isNullOrEmpty()) " [${disease.type}]" else ""
            val severityStr = when (disease.severity?.lowercase()) {
                "low" -> "легкая"
                "medium" -> "средняя"
                "high" -> "тяжелая"
                else -> disease.severity
            }
            val severityInfo = if (severityStr != null) " - $severityStr" else ""
            val evidence = if (!disease.evidence.isNullOrEmpty()) {
                "\n  Признаки: ${disease.evidence.joinToString(", ")}"
            } else ""
            "• $name$typeStr ($likelihood%)$severityInfo$evidence"
        }
    }
    
    fun formatPests(pests: List<DiseaseOrPest>, minLikelihood: Int = 10): String {
        val filteredPests = pests.filter { (it.likelihood ?: 0) >= minLikelihood }
        if (filteredPests.isEmpty()) return ""
        
        return filteredPests.joinToString("\n") { pest ->
            val name = pest.nameRu ?: "Неизвестно"
            val likelihood = pest.likelihood ?: 0
            val typeStr = if (!pest.type.isNullOrEmpty()) " [${pest.type}]" else ""
            val severityStr = when (pest.severity?.lowercase()) {
                "low" -> "легкая"
                "medium" -> "средняя"
                "high" -> "тяжелая"
                else -> pest.severity
            }
            val severityInfo = if (severityStr != null) " - $severityStr" else ""
            val evidence = if (!pest.evidence.isNullOrEmpty()) {
                "\n  Признаки: ${pest.evidence.joinToString(", ")}"
            } else ""
            "• $name$typeStr ($likelihood%)$severityInfo$evidence"
        }
    }
    
    fun formatCondition(condition: Condition): String {
        val conditionItems = mutableListOf<String>()
        
        // Статус дерева
        condition.treeStatus?.let { status ->
            val statusRu = when (status.lowercase()) {
                "alive" -> "Живое"
                "dead" -> "Мертвое"
                "dying" -> "Усыхающее"
                else -> status
            }
            conditionItems.add("Статус: $statusRu")
        }
        
        // Наклон
        condition.leaning?.let { leaning ->
            if (leaning.present == true && (leaning.angle ?: 0) > 0) {
                val confidence = leaning.confidence?.let { " (уверенность: $it%)" } ?: ""
                conditionItems.add("Наклон: ${leaning.angle}°$confidence")
            }
        }
        
        condition.dryBranchesPct?.let { pct ->
            if (pct > 0) conditionItems.add("Сухие ветки: $pct%")
        }
        
        if (condition.trunkDecay?.present == true) {
            val confidence = condition.trunkDecay.confidence?.let { " ($it%)" } ?: ""
            val evidence = if (!condition.trunkDecay.evidence.isNullOrEmpty()) {
                " - ${condition.trunkDecay.evidence.joinToString(", ")}"
            } else ""
            conditionItems.add("Гниль ствола обнаружена$confidence$evidence")
        }
        if (condition.cavities?.present == true) {
            val confidence = condition.cavities.confidence?.let { " ($it%)" } ?: ""
            val locations = if (!condition.cavities.locations.isNullOrEmpty()) {
                " [${condition.cavities.locations.joinToString(", ")}]"
            } else ""
            conditionItems.add("Дупла обнаружены$locations$confidence")
        }
        if (condition.cracks?.present == true) {
            val confidence = condition.cracks.confidence?.let { " ($it%)" } ?: ""
            val locations = if (!condition.cracks.locations.isNullOrEmpty()) {
                " [${condition.cracks.locations.joinToString(", ")}]"
            } else ""
            conditionItems.add("Трещины обнаружены$locations$confidence")
        }
        if (condition.barkDetachment?.present == true) {
            val confidence = condition.barkDetachment.confidence?.let { " ($it%)" } ?: ""
            val locations = if (!condition.barkDetachment.locations.isNullOrEmpty()) {
                " [${condition.barkDetachment.locations.joinToString(", ")}]"
            } else ""
            conditionItems.add("Отслоение коры$locations$confidence")
        }
        if (condition.trunkDamage?.present == true) {
            val confidence = condition.trunkDamage.confidence?.let { " ($it%)" } ?: ""
            val evidence = if (!condition.trunkDamage.evidence.isNullOrEmpty()) {
                " - ${condition.trunkDamage.evidence.joinToString(", ")}"
            } else ""
            conditionItems.add("Повреждения ствола$confidence$evidence")
        }
        if (condition.crownDamage?.present == true) {
            val confidence = condition.crownDamage.confidence?.let { " ($it%)" } ?: ""
            val evidence = if (!condition.crownDamage.evidence.isNullOrEmpty()) {
                " - ${condition.crownDamage.evidence.joinToString(", ")}"
            } else ""
            conditionItems.add("Повреждения кроны$confidence$evidence")
        }
        if (condition.fruitingBodies?.present == true) {
            val confidence = condition.fruitingBodies.confidence?.let { " ($it%)" } ?: ""
            val evidence = if (!condition.fruitingBodies.evidence.isNullOrEmpty()) {
                " - ${condition.fruitingBodies.evidence.joinToString(", ")}"
            } else ""
            conditionItems.add("Плодовые тела грибов$confidence$evidence")
        }
        if (condition.rootDamage?.present == true) {
            val confidence = condition.rootDamage.confidence?.let { " ($it%)" } ?: ""
            val evidence = if (!condition.rootDamage.evidence.isNullOrEmpty()) {
                " - ${condition.rootDamage.evidence.joinToString(", ")}"
            } else ""
            conditionItems.add("Повреждения корней$confidence$evidence")
        }
        if (condition.rootCollarDecay?.present == true) {
            val confidence = condition.rootCollarDecay.confidence?.let { " ($it%)" } ?: ""
            val evidence = if (!condition.rootCollarDecay.evidence.isNullOrEmpty()) {
                " - ${condition.rootCollarDecay.evidence.joinToString(", ")}"
            } else ""
            conditionItems.add("Гниль корневой шейки$confidence$evidence")
        }
        
        // Другие проблемы
        if (!condition.other.isNullOrEmpty()) {
            conditionItems.add("Другое: ${condition.other.joinToString(", ")}")
        }
        
        return if (conditionItems.isNotEmpty()) {
            conditionItems.joinToString("\n• ", "• ")
        } else ""
    }
    
    fun formatRisk(risk: Risk): Pair<String, String> {
        val riskEmoji = when (risk.level?.lowercase()) {
            "low" -> "✅"
            "medium" -> "⚠️"
            "high" -> "🚨"
            else -> "ℹ️"
        }
        val riskLevel = when (risk.level?.lowercase()) {
            "low" -> "Низкий"
            "medium" -> "Средний"
            "high" -> "Высокий"
            else -> risk.level ?: ""
        }
        
        // Добавляем предупреждение о критическом риске
        val imminentWarning = if (risk.imminentFailureRisk == true) " 🚨 КРИТИЧНО!" else ""
        val riskText = "$riskEmoji Уровень риска: $riskLevel$imminentWarning"
        
        val driversText = buildString {
            if (risk.imminentFailureRisk == true) {
                append("⚠️ РИСК НЕМЕДЛЕННОГО ПАДЕНИЯ!\n")
            }
            if (!risk.drivers.isNullOrEmpty()) {
                append(risk.drivers.joinToString("\n• ", "• "))
            }
        }
        
        return Pair(riskText, driversText)
    }
    
    fun getRiskColor(riskLevel: String?): Int {
        return when (riskLevel?.lowercase()) {
            "low" -> android.graphics.Color.parseColor("#81C784")
            "medium" -> android.graphics.Color.parseColor("#FFB74D")
            "high" -> android.graphics.Color.parseColor("#E57373")
            else -> android.graphics.Color.parseColor("#A0B8A0")
        }
    }
}

