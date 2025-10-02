package com.example.lct_final.api

import com.google.gson.annotations.SerializedName

data class ImageUploadResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("filename") val filename: String,
    @SerializedName("original_filename") val originalFilename: String,
    @SerializedName("content_type") val contentType: String,
    @SerializedName("file_size") val fileSize: Int,
    @SerializedName("s3_key") val s3Key: String,
    @SerializedName("s3_url") val s3Url: String,
    @SerializedName("s3_bucket") val s3Bucket: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("taken_at") val takenAt: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("author") val author: String?,
    @SerializedName("processing_status") val processingStatus: String,
    @SerializedName("description_text") val descriptionText: String?,
    @SerializedName("detected_objects") val detectedObjects: List<DetectedObject>?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("fragments") val fragments: List<Any>
)

data class ImageDetailResponse(
    @SerializedName("id") val id: Int,
    @SerializedName("filename") val filename: String,
    @SerializedName("original_filename") val originalFilename: String,
    @SerializedName("content_type") val contentType: String,
    @SerializedName("file_size") val fileSize: Int,
    @SerializedName("s3_key") val s3Key: String,
    @SerializedName("s3_url") val s3Url: String,
    @SerializedName("s3_bucket") val s3Bucket: String,
    @SerializedName("width") val width: Int,
    @SerializedName("height") val height: Int,
    @SerializedName("taken_at") val takenAt: String?,
    @SerializedName("location") val location: String?,
    @SerializedName("author") val author: String?,
    @SerializedName("processing_status") val processingStatus: String,
    @SerializedName("description_text") val descriptionText: String?,
    @SerializedName("detected_objects") val detectedObjects: List<DetectedObject>?,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String,
    @SerializedName("fragments") val fragments: List<Any>
)

// Модели для detected_objects
data class DetectedObject(
    @SerializedName("bbox") val bbox: List<Double>,
    @SerializedName("label") val label: String,
    @SerializedName("confidence") val confidence: Double,
    @SerializedName("fragment_url") val fragmentUrl: String?,
    @SerializedName("description") val description: ObjectDescription?
)

data class ObjectDescription(
    @SerializedName("scene") val scene: Scene?,
    @SerializedName("object") val objectInfo: ObjectInfo?,
    @SerializedName("data_quality") val dataQuality: DataQuality?
)

data class Scene(
    @SerializedName("season_inferred") val seasonInferred: String?,
    @SerializedName("note") val note: String?
)

data class ObjectInfo(
    @SerializedName("type") val type: String?,
    @SerializedName("species") val species: Species?,
    @SerializedName("condition") val condition: Condition?,
    @SerializedName("risk") val risk: Risk?
)

data class Species(
    @SerializedName("label_ru") val labelRu: String?,
    @SerializedName("confidence") val confidence: Int?
)

data class Condition(
    @SerializedName("trunk_decay") val trunkDecay: ConditionDetail?,
    @SerializedName("cavities") val cavities: ConditionDetail?,
    @SerializedName("cracks") val cracks: ConditionDetail?,
    @SerializedName("bark_detachment") val barkDetachment: ConditionDetail?,
    @SerializedName("trunk_damage") val trunkDamage: ConditionDetail?,
    @SerializedName("crown_damage") val crownDamage: ConditionDetail?,
    @SerializedName("fruiting_bodies") val fruitingBodies: ConditionDetail?,
    @SerializedName("root_damage") val rootDamage: ConditionDetail?,
    @SerializedName("root_collar_decay") val rootCollarDecay: ConditionDetail?,
    @SerializedName("tree_status") val treeStatus: String?,
    @SerializedName("leaning") val leaning: LeaningDetail?,
    @SerializedName("diseases") val diseases: List<DiseaseOrPest>?,
    @SerializedName("pests") val pests: List<DiseaseOrPest>?,
    @SerializedName("dry_branches_pct") val dryBranchesPct: Int?,
    @SerializedName("other") val other: List<String>?
)

data class ConditionDetail(
    @SerializedName("present") val present: Boolean?,
    @SerializedName("evidence") val evidence: List<String>?,
    @SerializedName("locations") val locations: List<String>?,
    @SerializedName("confidence") val confidence: Int?
)

data class LeaningDetail(
    @SerializedName("present") val present: Boolean?,
    @SerializedName("angle") val angle: Int?,
    @SerializedName("confidence") val confidence: Int?
)

data class DiseaseOrPest(
    @SerializedName("name_ru") val nameRu: String?,
    @SerializedName("type") val type: String?,
    @SerializedName("likelihood") val likelihood: Int?,
    @SerializedName("evidence") val evidence: List<String>?,
    @SerializedName("severity") val severity: String?
)

data class Risk(
    @SerializedName("level") val level: String?,
    @SerializedName("drivers") val drivers: List<String>?,
    @SerializedName("imminent_failure_risk") val imminentFailureRisk: Boolean?
)

data class DataQuality(
    @SerializedName("issues") val issues: List<String>?,
    @SerializedName("overall_confidence") val overallConfidence: Int?
)

// Модель для получения download URL
data class ImageDownloadResponse(
    @SerializedName("download_url") val downloadUrl: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("filename") val filename: String
)


