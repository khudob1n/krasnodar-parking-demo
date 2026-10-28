package ru.krasnodar.parking.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.krasnodar.parking.shared.format.ParkingFormat
import ru.krasnodar.parking.shared.model.Capacity
import ru.krasnodar.parking.shared.model.FreePlaces

class FormatTest {
    @Test
    fun missingValuesSayNoData() {
        val p = parking(1, "a", price = null, from = null, to = null).copy(capacity = Capacity(null, null))
        assertEquals("Нет данных", ParkingFormat.price(p))
        assertEquals("Нет данных", ParkingFormat.paidHours(p))
        assertEquals("Нет данных", ParkingFormat.capacity(p))
        assertEquals("Нет данных", ParkingFormat.freePlaces(p))
        assertEquals("Нет данных", ParkingFormat.tariffZone(p))
    }

    @Test
    fun freePlacesNeedTrustedStatus() {
        // Значение без статуса available не показываем: на портале это заглушка.
        val p = parking(1, "a").copy(freePlaces = FreePlaces(value = 37, status = "no_data"))
        assertEquals("Нет данных", ParkingFormat.freePlaces(p))
        val ok = parking(1, "a").copy(freePlaces = FreePlaces(value = 5, updatedAt = "2026-10-28T09:00:00Z", status = "available"))
        assertEquals("5 · на 28.10.2026 12:00", ParkingFormat.freePlaces(ok))
    }

    @Test
    fun priceAndHours() {
        assertEquals("60 ₽/час", ParkingFormat.price(parking(1, "a", price = 60.0)))
        assertEquals("12,5 ₽/час", ParkingFormat.price(parking(1, "a", price = 12.5)))
        assertEquals("08:00–19:00", ParkingFormat.paidHours(parking(1, "a")))
        assertEquals("Круглосуточно", ParkingFormat.paidHours(parking(1, "a", from = "00:00", to = "00:00")))
        assertEquals("37 мест, из них для инвалидов 4", ParkingFormat.capacity(parking(1, "a")))
    }

    @Test
    fun plural() {
        assertEquals(listOf("место", "места", "мест", "мест", "место", "мест"), listOf(1, 3, 5, 11, 21, 112).map(ParkingFormat::places))
    }

    @Test
    fun sourceLineShowsDateAndTrust() {
        assertEquals(
            "Источник: parkingkrd.ru · получено 28.10.2026, вручную, не сверено с порталом",
            ParkingFormat.sourceLine(SOURCE),
        )
        assertEquals(
            "Источник: parkingkrd.ru · получено 29.10.2026 01:30",
            ParkingFormat.sourceLine(SOURCE.copy(fetchedAt = "2026-10-28T22:30:00Z", fetchedAtPrecision = "exact", verifiedAgainstPortal = true)),
        )
        assertEquals("Источник: Нет данных", ParkingFormat.sourceLine(null))
    }

    @Test
    fun localTimeHandlesOffsetsAndYearRollover() {
        assertEquals("01.01.2027 02:59", ParkingFormat.dateTime("2026-12-31T23:59:00Z"))
        assertEquals("13.10.2026 10:15", ParkingFormat.dateTime("2026-10-13T10:15:51+03:00"))
        assertEquals("29.02.2028", ParkingFormat.date("2028-02-28T22:00:00Z"))
        assertEquals("not a date", ParkingFormat.date("not a date"))
    }
}
