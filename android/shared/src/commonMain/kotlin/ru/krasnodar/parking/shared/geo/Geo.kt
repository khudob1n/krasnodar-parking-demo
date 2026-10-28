package ru.krasnodar.parking.shared.geo

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import ru.krasnodar.parking.shared.model.GeoPoint
import ru.krasnodar.parking.shared.model.Parking

object Geo {
    /** Центр Краснодара для стартовой камеры. */
    val KRASNODAR_CENTER = GeoPoint(45.0355, 38.9753)

    fun meters(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val la1 = a.lat.toRad()
        val la2 = b.lat.toRad()
        val dla = la2 - la1
        val dlo = (b.lon - a.lon).toRad()
        val h = sin(dla / 2) * sin(dla / 2) + cos(la1) * cos(la2) * sin(dlo / 2) * sin(dlo / 2)
        return 2 * r * asin(sqrt(h))
    }

    /** Расстояние до ближайшей известной точки парковки или null, если координат нет. */
    fun distanceTo(p: Parking, here: GeoPoint): Double? {
        val points = buildList {
            p.location?.let(::add)
            p.segments.forEach { addAll(it) }
        }
        return points.minOfOrNull { meters(it, here) }
    }

    /** Точка, на которую наводить камеру при выборе парковки. */
    fun anchor(p: Parking): GeoPoint? {
        val seg = p.segments.firstOrNull()
        if (seg != null && seg.size == 2) return GeoPoint((seg[0].lat + seg[1].lat) / 2, (seg[0].lon + seg[1].lon) / 2)
        return p.location
    }

    private fun Double.toRad() = this * kotlin.math.PI / 180
}
