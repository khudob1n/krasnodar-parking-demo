package ru.krasnodar.parking.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.krasnodar.parking.shared.catalog.CatalogSearch
import ru.krasnodar.parking.shared.model.GeoPoint
import ru.krasnodar.parking.shared.model.ParkingKind

class SearchTest {
    private val all = listOf(
        parking(1102, "Чётная сторона ул. им. Гоголя, от ул. Красноармейской до ул. Красной"),
        parking(1103, "Чётная сторона ул.Комсомольская, от ул.Красной до ул.Красноармейской", location = GeoPoint(45.019142, 38.969612)),
        parking(12001, "г.Краснодар, ул. Московская", kind = ParkingKind.FREE),
    )

    private fun ids(q: String, paidOnly: Boolean = true) = CatalogSearch.search(all, q, paidOnly).map { it.portalId }

    @Test
    fun byWordsAndNumber() {
        assertEquals(listOf(1102), ids("гоголя"))
        assertEquals(listOf(1102), ids("Чeтная гоголя".replace('e', 'ё')))
        assertEquals(listOf(1102, 1103), ids("красной"))
        assertEquals(listOf(1103), ids("1103"))
        assertEquals(listOf(1103), ids("комсомольская красной"))
        assertEquals(emptyList(), ids("   "))
    }

    @Test
    fun freeParkingsHiddenByDefault() {
        assertEquals(emptyList(), ids("московская"))
        assertEquals(listOf(12001), ids("московская", paidOnly = false))
    }

    @Test
    fun nearbyOnlySuggestsWithinRadius() {
        val near = CatalogSearch.nearby(all, GeoPoint(45.0266, 38.9722), radiusMeters = 300.0)
        assertEquals(listOf(1102), near.map { it.first.portalId })
        assertTrue(near.first().second < 20)
        assertEquals(emptyList(), CatalogSearch.nearby(all, GeoPoint(45.2, 39.2)))
    }
}
