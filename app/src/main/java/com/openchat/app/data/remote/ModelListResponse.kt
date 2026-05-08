package com.openchat.app.data.remote

data class ModelListResponse(
    val `data`: List<ModelData>? = null,
    val models: List<ModelData>? = null
)

data class ModelData(
    val id: String?,
    val name: String?
) {
    val modelId: String
        get() = id ?: name ?: "unknown"
}
