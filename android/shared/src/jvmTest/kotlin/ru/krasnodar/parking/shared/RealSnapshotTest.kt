package ru.krasnodar.parking.shared

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.krasnodar.parking.shared.catalog.CatalogCodec
import ru.krasnodar.parking.shared.catalog.CatalogSearch
import ru.krasnodar.parking.shared.format.ParkingFormat

/** Приложение читает тот же снимок, что пишет Go-импортёр. */
class RealSnapshotTest {
    private val file = File(System.getProperty("catalog.snapshot") ?: "../../data/snapshot/parkings.json")

    @Test
    fun decodesImporterOutput() {
        if (!file.exists()) return
        val snap = CatalogCodec.decode(file.readText())
        assertEquals(snap.counts.total, snap.parkings.size)
        assertTrue(snap.parkings.all { it.source == "parkingkrd" })
        assertTrue(snap.parkings.none { it.freePlaces.value != null }, "свободных мест в снимке быть не должно")
        val gogol = snap.parkings.first { it.portalId == 1102 }
        assertEquals("60 ₽/час", ParkingFormat.price(gogol))
        assertEquals("08:00–19:00", ParkingFormat.paidHours(gogol))
        assertEquals("Нет данных", ParkingFormat.tariffZone(gogol))
        assertEquals(listOf(1445, 1446), CatalogSearch.search(snap.parkings, "Дзержинского Дальней").map { it.portalId })
        assertEquals(listOf(1446), CatalogSearch.search(snap.parkings, "1446").map { it.portalId })
    }
}
