package ru.krasnodar.parking.shared

import ru.krasnodar.parking.shared.model.Capacity
import ru.krasnodar.parking.shared.model.CatalogCounts
import ru.krasnodar.parking.shared.model.CatalogSnapshot
import ru.krasnodar.parking.shared.model.CatalogSource
import ru.krasnodar.parking.shared.model.FreePlaces
import ru.krasnodar.parking.shared.model.GeoPoint
import ru.krasnodar.parking.shared.model.GeometryStatus
import ru.krasnodar.parking.shared.model.Parking
import ru.krasnodar.parking.shared.model.ParkingKind
import ru.krasnodar.parking.shared.model.Tariff

fun parking(
    id: Int,
    address: String,
    price: Double? = 60.0,
    kind: ParkingKind = ParkingKind.STREET,
    location: GeoPoint? = GeoPoint(45.0266, 38.9721),
    from: String? = "08:00",
    to: String? = "19:00",
) = Parking(
    id = "parkingkrd:$id", portalId = id, kind = kind, paid = kind != ParkingKind.FREE, address = address,
    location = location, geometryStatus = if (location == null) GeometryStatus.NONE else GeometryStatus.POINT_ONLY,
    tariff = Tariff(price, from, to, ""), capacity = Capacity(37, 4), freePlaces = FreePlaces(status = "no_data"),
    source = "parkingkrd",
)

val SOURCE = CatalogSource(
    id = "parkingkrd", publisher = "parkingkrd.ru", sourceUrl = "https://parkingkrd.ru/",
    fetchedAt = "2026-10-28T18:56:00Z", fetchedAtPrecision = "user_reported",
)

fun snapshotJson(version: String, generatedAt: String = "2026-10-28T19:00:00Z", schema: Int = 1): String = """
{"schema_version":$schema,"version":"$version","generated_at":"$generatedAt",
 "sources":[{"id":"parkingkrd","publisher":"parkingkrd.ru","source_url":"https://parkingkrd.ru/","endpoint_url":null,
   "fetched_at":"2026-10-28T18:56:00Z","fetched_at_precision":"user_reported","raw_sha256":"x","verified_against_portal":false,"future_field":1}],
 "counts":{"total":1,"paid":1,"free":0,"with_geometry":1,"paid_places":37,"excluded":0},
 "parkings":[{"id":"parkingkrd:1102","portal_id":1102,"kind":"street","paid":true,"address":"ул. Гоголя",
   "location":{"lat":45.02,"lon":38.97},"segments":[[{"lat":45.026684,"lon":38.972087},{"lat":45.026543,"lon":38.972943}]],
   "geometry_status":"segments","tariff":{"price_per_hour_rub":60,"paid_from":"08:00","paid_to":"19:00","text":"60.0 руб/час"},
   "capacity":{"total":37,"disabled":4},"free_places":{"value":null,"updated_at":null,"status":"no_data"},
   "tariff_zone":null,"status":null,"in_building":false,"photos_count":4,"issues":[],"source":"parkingkrd"}]}
"""

@Suppress("unused")
val EMPTY_COUNTS = CatalogCounts(0, 0, 0, 0, 0, 0)

@Suppress("unused")
fun snapshotOf(vararg ps: Parking) = CatalogSnapshot(1, "v", "2026-10-28T19:00:00Z", listOf(SOURCE), EMPTY_COUNTS, ps.toList())
