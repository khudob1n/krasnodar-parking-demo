package ru.krasnodar.parking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.krasnodar.parking.shared.catalog.CatalogCodec
import ru.krasnodar.parking.shared.catalog.CatalogOrigin
import ru.krasnodar.parking.shared.catalog.CatalogState
import ru.krasnodar.parking.shared.catalog.SyncStatus
import ru.krasnodar.parking.ui.components.statusText
import java.io.File

class StatusTextTest {
    private val snapshot = CatalogCodec.decode(File("../../data/snapshot/parkings.json").readText())

    @Test
    fun offlineSaysWhereDataComesFrom() {
        val s = CatalogState(snapshot = snapshot, origin = CatalogOrigin.SEED, sync = SyncStatus.Offline("2026-10-28T20:00:00Z", "x"), loading = false)
        assertEquals("Нет сети. Показан встроенный каталог от 28.10.2026.", statusText(s, online = false))
        assertEquals("Сервер каталога недоступен. Показан встроенный каталог от 28.10.2026.", statusText(s, online = true))
    }

    @Test
    fun freshDataHasNoBanner() {
        val s = CatalogState(snapshot = snapshot, origin = CatalogOrigin.NETWORK, savedAt = "2026-10-28T20:00:00Z", sync = SyncStatus.UpToDate("2026-10-28T20:00:00Z"), loading = false)
        assertNull(statusText(s, online = true))
    }
}
