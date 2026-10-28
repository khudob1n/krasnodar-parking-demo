package ru.krasnodar.parking.shared.format

import ru.krasnodar.parking.shared.model.CatalogSource
import ru.krasnodar.parking.shared.model.Parking
import ru.krasnodar.parking.shared.model.ParkingKind

/**
 * Строки для интерфейса. Правило одно: если значения нет в источнике,
 * пишем «Нет данных», а не придумываем.
 */
object ParkingFormat {
    const val NO_DATA = "Нет данных"

    fun kind(p: Parking): String = when (p.kind) {
        ParkingKind.STREET -> "Уличная парковка"
        ParkingKind.LOT -> "Парковочная площадка"
        ParkingKind.FREE -> "Бесплатная парковка"
    }

    /** Номер так, как его отдаёт портал, с явной оговоркой в карточке. */
    fun number(p: Parking): String = "№ ${p.portalId}"

    fun price(p: Parking): String {
        val v = p.tariff.pricePerHourRub ?: return NO_DATA
        val rub = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString().replace('.', ',')
        return "$rub ₽/час"
    }

    /** «08:00–19:00», «Круглосуточно» или «Нет данных». */
    fun paidHours(p: Parking): String {
        val from = p.tariff.paidFrom
        val to = p.tariff.paidTo
        return when {
            from == null || to == null -> NO_DATA
            from == "00:00" && to == "00:00" -> "Круглосуточно"
            else -> "$from–$to"
        }
    }

    fun capacity(p: Parking): String {
        val total = p.capacity.total ?: return NO_DATA
        val disabled = p.capacity.disabled
        return if (disabled != null && disabled > 0) "$total ${places(total)}, из них для инвалидов $disabled" else "$total ${places(total)}"
    }

    fun freePlaces(p: Parking): String {
        val v = p.freePlaces.value
        if (p.freePlaces.status != "available" || v == null) return NO_DATA
        val at = p.freePlaces.updatedAt?.let { " · на ${dateTime(it)}" } ?: ""
        return "$v$at"
    }

    fun tariffZone(p: Parking): String = p.tariffZone ?: NO_DATA

    /** Короткая строка для списка: тариф и часы. */
    fun summary(p: Parking): String = if (!p.paid) kind(p) else "${price(p)} · ${paidHours(p)}"

    /** «parkingkrd.ru · получено 28.10.2026, вручную, не сверено». */
    fun sourceLine(s: CatalogSource?): String {
        if (s == null) return "Источник: $NO_DATA"
        val host = s.sourceUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val date = date(s.fetchedAt)
        val whenText = when (s.fetchedAtPrecision) {
            "exact" -> "получено ${dateTime(s.fetchedAt)}"
            "not_later_than" -> "получено не позже $date"
            "user_reported" -> "получено $date, вручную"
            else -> "дата получения: $date"
        }
        val check = if (s.verifiedAgainstPortal) "" else ", не сверено с порталом"
        return "Источник: $host · $whenText$check"
    }

    fun date(iso: String): String = LocalTime.parse(iso)?.let { "${pad(it.day)}.${pad(it.month)}.${it.year}" } ?: iso

    fun dateTime(iso: String): String =
        LocalTime.parse(iso)?.let { "${pad(it.day)}.${pad(it.month)}.${it.year} ${pad(it.hour)}:${pad(it.minute)}" } ?: iso

    fun places(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            m10 == 1 && m100 != 11 -> "место"
            m10 in 2..4 && m100 !in 12..14 -> "места"
            else -> "мест"
        }
    }

    private fun pad(n: Int) = n.toString().padStart(2, '0')
}

/**
 * Перевод ISO-времени в местное время Краснодара (UTC+3, без перехода на
 * летнее время). Без зависимостей, чтобы модуль собирался и под iOS.
 */
internal data class LocalTime(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int) {
    companion object {
        private const val KRASNODAR_OFFSET_MIN = 180
        private val re = Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::\d{2}(?:\.\d+)?)?(Z|[+-]\d{2}:\d{2})$""")

        fun parse(iso: String): LocalTime? {
            val m = re.matchEntire(iso.trim()) ?: return null
            val (y, mo, d, h, mi, zone) = m.destructured
            val offset = if (zone == "Z") 0 else {
                val sign = if (zone[0] == '-') -1 else 1
                sign * (zone.substring(1, 3).toInt() * 60 + zone.substring(4, 6).toInt())
            }
            var minutes = daysFromCivil(y.toInt(), mo.toInt(), d.toInt()) * 1440L + h.toInt() * 60 + mi.toInt()
            minutes += KRASNODAR_OFFSET_MIN - offset
            val days = minutes.floorDiv(1440L)
            val rest = (minutes - days * 1440).toInt()
            val (yy, mm, dd) = civilFromDays(days)
            return LocalTime(yy, mm, dd, rest / 60, rest % 60)
        }

        // Алгоритмы Говарда Хиннанта для григорианского календаря.
        private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
            val y = if (m <= 2) y0 - 1 else y0
            val era = y.floorDiv(400)
            val yoe = y - era * 400
            val doy = (153 * (if (m > 2) m - 3 else m + 9) + 2) / 5 + d - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146097L + doe - 719468
        }

        private fun civilFromDays(z0: Long): Triple<Int, Int, Int> {
            val z = z0 + 719468
            val era = z.floorDiv(146097L)
            val doe = (z - era * 146097).toInt()
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = if (mp < 10) mp + 3 else mp - 9
            val y = yoe + era.toInt() * 400 + if (m <= 2) 1 else 0
            return Triple(y, m, d)
        }
    }
}
