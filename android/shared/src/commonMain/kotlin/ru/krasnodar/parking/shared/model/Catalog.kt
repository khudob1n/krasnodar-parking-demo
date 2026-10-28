package ru.krasnodar.parking.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Снимок каталога в формате Go-сервера (schema_version = 1).
 * Поля, которых нет в источнике, приходят как null — показываем «Нет данных».
 */
@Serializable
data class CatalogSnapshot(
    @SerialName("schema_version") val schemaVersion: Int,
    val version: String,
    @SerialName("generated_at") val generatedAt: String,
    val sources: List<CatalogSource>,
    val counts: CatalogCounts,
    val parkings: List<Parking>,
) {
    fun source(id: String): CatalogSource? = sources.firstOrNull { it.id == id }

    companion object {
        const val SUPPORTED_SCHEMA = 1
    }
}

@Serializable
data class CatalogCounts(
    val total: Int,
    val paid: Int,
    val free: Int,
    @SerialName("with_geometry") val withGeometry: Int,
    @SerialName("paid_places") val paidPlaces: Int,
    val excluded: Int,
)

@Serializable
data class CatalogSource(
    val id: String,
    val publisher: String,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("endpoint_url") val endpointUrl: String? = null,
    @SerialName("fetched_at") val fetchedAt: String,
    /** exact | not_later_than | user_reported */
    @SerialName("fetched_at_precision") val fetchedAtPrecision: String,
    @SerialName("raw_sha256") val rawSha256: String = "",
    val provenance: String = "",
    @SerialName("verified_against_portal") val verifiedAgainstPortal: Boolean = false,
    val note: String = "",
)

@Serializable
enum class ParkingKind {
    @SerialName("street") STREET,
    @SerialName("lot") LOT,
    @SerialName("free") FREE,
}

@Serializable
enum class GeometryStatus {
    @SerialName("segments") SEGMENTS,
    @SerialName("point_only") POINT_ONLY,
    @SerialName("none") NONE,
}

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

@Serializable
data class Parking(
    val id: String,
    /** id из ответа портала. Совпадение с номером на табличке не подтверждено. */
    @SerialName("portal_id") val portalId: Int,
    val kind: ParkingKind,
    val paid: Boolean,
    val address: String,
    val description: String = "",
    val location: GeoPoint? = null,
    /** Каждый участок — две точки. Пустой список значит «геометрии нет». */
    val segments: List<List<GeoPoint>> = emptyList(),
    @SerialName("geometry_status") val geometryStatus: GeometryStatus,
    val tariff: Tariff,
    val capacity: Capacity,
    @SerialName("free_places") val freePlaces: FreePlaces,
    @SerialName("tariff_zone") val tariffZone: String? = null,
    val status: String? = null,
    @SerialName("in_building") val inBuilding: Boolean = false,
    @SerialName("photos_count") val photosCount: Int = 0,
    val issues: List<String> = emptyList(),
    val source: String,
)

@Serializable
data class Tariff(
    @SerialName("price_per_hour_rub") val pricePerHourRub: Double? = null,
    @SerialName("paid_from") val paidFrom: String? = null,
    @SerialName("paid_to") val paidTo: String? = null,
    val text: String = "",
)

@Serializable
data class Capacity(val total: Int? = null, val disabled: Int? = null)

@Serializable
data class FreePlaces(
    val value: Int? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** available | no_data */
    val status: String,
    val note: String = "",
)
