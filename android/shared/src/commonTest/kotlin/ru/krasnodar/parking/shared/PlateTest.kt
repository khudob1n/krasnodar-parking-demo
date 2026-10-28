package ru.krasnodar.parking.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.krasnodar.parking.shared.model.Plate

class PlateTest {
    @Test
    fun normalizesLatinLookalikesAndSpaces() {
        assertEquals("А123ВС93", Plate.normalize("a 123 bc 93"))
        assertEquals("А123ВС193", Plate.normalize("а123вс-193"))
        assertTrue(Plate.isStandard("А123ВС93"))
        assertTrue(Plate.isStandard("А123ВС193"))
        assertFalse(Plate.isStandard("Б123ВС93"))
        assertFalse(Plate.isStandard("А12ВС93"))
    }

    @Test
    fun displayAndMask() {
        assertEquals("А123ВС 93", Plate.display("А123ВС93"))
        assertEquals("А***ВС**", Plate.masked("А123ВС93"))
    }
}
