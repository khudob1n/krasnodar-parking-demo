package ru.krasnodar.parking.shared.catalog

import ru.krasnodar.parking.shared.geo.Geo
import ru.krasnodar.parking.shared.model.GeoPoint
import ru.krasnodar.parking.shared.model.Parking

/**
 * Поиск по каталогу на устройстве. Работает без сети и повторяет правила
 * серверного поиска: номер из портала даёт точное совпадение, слова ищутся
 * в адресе и описании, все слова должны найтись.
 */
object CatalogSearch {
    fun normalize(s: String): String =
        s.lowercase()
            .replace('ё', 'е')
            .map { if (it in ".,№()") ' ' else it }
            .joinToString("")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    fun search(parkings: List<Parking>, query: String, paidOnly: Boolean = true, limit: Int = 50): List<Parking> {
        val tokens = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return parkings.asSequence()
            .filter { !paidOnly || it.paid }
            .mapNotNull { p ->
                val id = p.portalId.toString()
                val hay = normalize(p.address + " " + p.description)
                var score = 0
                for (t in tokens) {
                    score += when {
                        t == id -> 100
                        hay.contains(t) -> 10
                        else -> return@mapNotNull null
                    }
                }
                p to score
            }
            .sortedWith(compareByDescending<Pair<Parking, Int>> { it.second }.thenBy { it.first.portalId })
            .take(limit)
            .map { it.first }
            .toList()
    }

    /**
     * Ближайшие платные парковки — только варианты для выбора. Приложение не
     * выбирает зону само: при низкой точности или нескольких близких
     * вариантах пользователь выбирает вручную.
     */
    fun nearby(parkings: List<Parking>, here: GeoPoint, radiusMeters: Double = 300.0, limit: Int = 5): List<Pair<Parking, Double>> =
        parkings.asSequence()
            .filter { it.paid }
            .mapNotNull { p -> Geo.distanceTo(p, here)?.let { p to it } }
            .filter { it.second <= radiusMeters }
            .sortedBy { it.second }
            .take(limit)
            .toList()
}
